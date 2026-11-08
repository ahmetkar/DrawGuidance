package com.karapp.drawguidance.server.report;

import com.karapp.drawguidance.server.common.ApiError;
import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.karapp.drawguidance.server.web.PublicForms;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/**
 * v37 hata/çökme raporu ucu ({@code POST /api/v1/reports}): uygulamanın merkezi hata yönetimi kullanıcı onayıyla
 * raporu buraya gönderir. v40 giriş zorunlu: geçerli bir Firebase ID jetonu ({@code Authorization: Bearer ...})
 * gerekir (yoksa 401, Google anahtarları alınamazsa 503; denetimi güvenlik süzgeci yapar). Uygulama girişsiz
 * kullanıcıya "önce giriş yapın" uyarısı gösterir; bekleyen raporlar olay tablosunda girişi bekler. Yeni rapor
 * Slack'e kısmen gizlenmiş e-postayla bildirilir.
 *
 * <p>Sıra: IP başına sınır (429) → gövde boyutu (413) → JSON ve alan denetimi (400) → kurulum başına sınır (429) →
 * kayıt → 202 {@code {"id": "..."}}.
 */
@RestController
@RequestMapping(ErrorReportController.PATH)
public class ErrorReportController {
    private static final Logger log = LoggerFactory.getLogger(ErrorReportController.class);
    public static final String PATH = "/api/v1/reports";

    /** Jeton durumu: yok, geçerli, geçersiz, doğrulanamadı (Google anahtarları alınamadı). */
    public static final String AUTH_NONE = "none", AUTH_VALID = "valid", AUTH_INVALID = "invalid", AUTH_UNVERIFIED = "unverified";

    private final ErrorReportService reports;
    private final RateLimiter limiter;
    private final JwtDecoder jwt;
    private final ObjectMapper json;
    private final DrawGuidanceProperties.Reports limits;
    private final PublicForms forms;

    public ErrorReportController(ErrorReportService reports, RateLimiter limiter, JwtDecoder jwt, ObjectMapper json, DrawGuidanceProperties props,
                                 PublicForms forms) {
        this.forms = forms;
        this.reports = reports;
        this.limiter = limiter;
        this.jwt = jwt;
        this.json = json;
        this.limits = props.reports();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> post(HttpServletRequest request) throws IOException {
        // v40 istemci adresi vekil arkasında da doğru (önceden vekilin adresi: bütün raporlar tek IP kovasındaydı)
        RateLimiter.Decision ip = limiter.acquire("ip:" + forms.clientIp(request), limits.perIpLimit(), limits.window());
        if (!ip.allowed()) return tooMany(ip);
        ErrorReportService.Normalized report = ErrorReportService.normalize(parse(read(request)));
        RateLimiter.Decision install = limiter.acquire("install:" + report.installId(), limits.perInstallLimit(), limits.window());
        if (!install.allowed()) return tooMany(install);
        Identity who = identify(request.getHeader(HttpHeaders.AUTHORIZATION));
        ErrorReportService.Stored stored = reports.store(report, who.uid(), who.auth(), who.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(new Accepted(stored.id()));
    }

    /** {@code 202} yanıtı: {@code {"id": "<rapor kimliği>"}}. */
    public record Accepted(String id) {}

    /** Gövde en çok {@code maxBodyBytes} bayt; fazlası okunmadan 413. */
    private byte[] read(HttpServletRequest request) throws IOException {
        int max = limits.maxBodyBytes();
        if (request.getContentLengthLong() > max) throw tooLarge(max);
        try (InputStream in = request.getInputStream()) {
            byte[] body = in.readNBytes(max + 1);
            if (body.length > max) throw tooLarge(max);
            return body;
        }
    }

    private static ApiException tooLarge(int max) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "too_large", "Rapor çok büyük (en çok " + (max / 1024) + " KB)");
    }

    private ErrorReportRequest parse(byte[] body) {
        try {
            ErrorReportRequest r = json.readValue(body, ErrorReportRequest.class);
            if (r == null) throw ApiException.badRequest("bad_request", "Rapor gövdesi bir JSON nesnesi olmalı");
            return r;
        } catch (JacksonException e) {
            throw ApiException.badRequest("bad_request", "Rapor gövdesi okunamadı (geçersiz JSON ya da alan türü)");
        } catch (IOException e) {
            throw ApiException.badRequest("bad_request", "Rapor gövdesi okunamadı");
        }
    }

    private static ResponseEntity<Object> tooMany(RateLimiter.Decision d) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, String.valueOf(d.retryAfterSeconds()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("rate_limited", "Çok fazla rapor gönderildi; biraz sonra yeniden deneyin"));
    }

    record Identity(String uid, String auth, String email) {
        Identity(String uid, String auth) { this(uid, auth, null); }
    }

    /**
     * v40 kimlik: güvenlik süzgecinin doğruladığı jeton (uç artık giriş ister). Süzgeç dışında çağrılırsa (ör. eski
     * yapılandırma) Bearer jetonu yine kendisi çözer: geçerliyse {@code sub}; değilse kimliksiz.
     */
    Identity identify(String header) {
        var current = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (current instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken t) {
            return new Identity(t.getToken().getSubject(), AUTH_VALID, t.getToken().getClaimAsString("email"));
        }
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return new Identity(null, AUTH_NONE);
        String token = header.substring(7).trim();
        if (token.isEmpty()) return new Identity(null, AUTH_INVALID);
        try {
            Jwt decoded = jwt.decode(token);
            return new Identity(decoded.getSubject(), AUTH_VALID, decoded.getClaimAsString("email"));
        } catch (BadJwtException e) {
            return new Identity(null, AUTH_INVALID);
        } catch (JwtException e) {
            log.warn("Rapordaki Firebase jetonu doğrulanamadı (Google anahtarları alınamadı mı?): {}", e.getMessage());
            return new Identity(null, AUTH_UNVERIFIED);
        }
    }
}
