package com.karapp.drawguidance.server.web;

import com.karapp.drawguidance.server.common.ApiError;
import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * v40 web sitesinin herkese açık form uçlarının ortak işleri (hesap silme talebi, destek mesajı):
 *
 * <ul>
 *   <li><b>İstemci adresi</b> ({@link #clientIp}): öntanımlı olarak bağlantının adresi. {@code X-Forwarded-For} yalnız
 *       {@code drawguidance.web.behind-proxy=true} ise okunur ve sağdan {@code proxy-hops}. adres alınır (vekilin
 *       eklediği; istemcinin uydurduğu soldaki değerler sayılmaz).</li>
 *   <li><b>İstek sınırı</b> ({@link #limit}): IP ve uç başına jeton kovası (öntanımlı saatte 5), aşılırsa 429
 *       {@code rate_limited} ve {@code Retry-After}.</li>
 *   <li><b>IP özeti</b> ({@link #ipHash}): ham IP saklanmaz; HMAC-SHA256(tuz, IP) ilk 16 bayt.</li>
 *   <li><b>Alan denetimi</b>: e-posta, uzunluk sınırları, dil kodu.</li>
 * </ul>
 */
@Component
public class PublicForms {
    private static final Logger log = LoggerFactory.getLogger(PublicForms.class);

    public static final int MAX_EMAIL = 254;
    public static final int MAX_USER_AGENT = 256;
    /** Basit ve tutucu e-posta biçimi: yerel kısım @ alan adı . uzantı (boşluk ve denetim karakteri yok). */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@\\p{Cntrl}]{1,64}@[^\\s@\\p{Cntrl}]+\\.[^\\s@\\p{Cntrl}.]{2,}$");
    private static final Pattern LOCALE = Pattern.compile("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8}){0,2}$");
    /** Satır sonu ve sekme dışındaki denetim karakterleri. */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    /** Form gövdesinin üst sınırı (bayt); en uzun alanlar (5000 karakterlik mesaj) rahatça sığar. */
    public static final int MAX_BODY = 32 * 1024;

    private final DrawGuidanceProperties.Web web;
    private final TokenBucketLimiter limiter;
    private final byte[] salt;
    private final ObjectMapper json;

    public PublicForms(DrawGuidanceProperties props, ObjectMapper json) {
        this.json = json;
        this.web = props.web();
        this.limiter = new TokenBucketLimiter(web.formLimit(), web.formWindow(), Clock.systemUTC());
        if (web.ipHashSalt() != null) {
            this.salt = web.ipHashSalt().getBytes(StandardCharsets.UTF_8);
        } else {
            this.salt = new byte[32];
            new SecureRandom().nextBytes(salt);
            log.info("IP_HASH_SALT verilmedi: IP özetleri için açılışta rastgele tuz üretildi");
        }
    }

    // ---------------------------------------------------------------- istemci ve sınır

    /** İstemci adresi (bkz. sınıf açıklaması). */
    public String clientIp(HttpServletRequest request) {
        return clientIp(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"), web.behindProxy(), web.proxyHops());
    }

    static String clientIp(String remote, String forwardedFor, boolean behindProxy, int hops) {
        if (!behindProxy || forwardedFor == null || forwardedFor.isBlank()) return remote;
        String[] parts = forwardedFor.split(",");
        int i = parts.length - hops;
        if (i < 0) i = 0;
        String ip = parts[i].trim();
        return ip.isEmpty() || ip.length() > 64 ? remote : ip;
    }

    /** IP ve uç başına sınır; izin varsa {@code null}, yoksa 429 yanıtı. */
    public ResponseEntity<Object> limit(String endpoint, String ip) {
        TokenBucketLimiter.Decision d = limiter.acquire(endpoint + ":" + ip);
        if (d.allowed()) return null;
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(d.retryAfterSeconds()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("rate_limited", "Çok fazla istek gönderildi; biraz sonra yeniden deneyin"));
    }

    /** {@code 202 {"status": "received"}}: talep alındı (yinelenen talepte de aynısı; e-posta varlığı sızmasın). */
    public static ResponseEntity<Object> received() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(Map.of("status", "received"));
    }

    /**
     * Gövdeyi okur ve JSON nesnesine çevirir: {@link #MAX_BODY} baytı aşarsa okunmadan 413 {@code too_large}; JSON
     * değilse ya da alan türü yanlışsa 400 {@code bad_request}. Bilinmeyen alanlar yok sayılır.
     */
    public <T> T readJson(HttpServletRequest request, Class<T> type) {
        if (request.getContentLengthLong() > MAX_BODY) throw tooLarge();
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readNBytes(MAX_BODY + 1);
        } catch (IOException e) {
            throw ApiException.badRequest("bad_request", "İstek gövdesi okunamadı");
        }
        if (body.length > MAX_BODY) throw tooLarge();
        try {
            T value = json.readValue(body, type);
            if (value == null) throw ApiException.badRequest("bad_request", "Gövde bir JSON nesnesi olmalı");
            return value;
        } catch (IOException e) {
            throw ApiException.badRequest("bad_request", "İstek gövdesi okunamadı (geçersiz JSON ya da alan türü)");
        }
    }

    private static ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "too_large", "İstek gövdesi çok büyük (en çok " + (MAX_BODY / 1024) + " KB)");
    }

    // ---------------------------------------------------------------- saklanan istemci bilgisi

    /** HMAC-SHA256(tuz, IP) ilk 16 baytı (32 onaltılık karakter). */
    public String ipHash(String ip) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(String.valueOf(ip).getBytes(StandardCharsets.UTF_8)), 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code User-Agent} tek satır, en çok {@link #MAX_USER_AGENT} karakter; yoksa {@code null}. */
    public static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader(HttpHeaders.USER_AGENT);
        if (ua == null) return null;
        ua = ua.replaceAll("\\p{Cntrl}", "").trim();
        if (ua.isEmpty()) return null;
        return ua.length() > MAX_USER_AGENT ? ua.substring(0, MAX_USER_AGENT) : ua;
    }

    // ---------------------------------------------------------------- alan denetimi

    /** Zorunlu e-posta: kırpılır, küçük harfe çevrilir; biçimsizse 400 {@code invalid_email}. */
    public static String email(String raw) {
        String e = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (e.isEmpty() || e.length() > MAX_EMAIL || !EMAIL.matcher(e).matches()) {
            throw ApiException.badRequest("invalid_email", "Geçerli bir e-posta adresi girin");
        }
        return e;
    }

    /**
     * Serbest yazı: denetim karakterleri silinir, kırpılır. Zorunluysa ve boşsa 400 {@code validation}; {@code max}
     * karakteri aşarsa 400 {@code too_long} (kesilmez: kullanıcı ne gönderdiğini bilsin).
     */
    public static String text(String field, String raw, int max, boolean required) {
        String t = raw == null ? "" : CONTROL.matcher(raw).replaceAll("").strip();
        if (t.isEmpty()) {
            if (required) throw ApiException.badRequest("validation", field + ": boş olamaz");
            return null;
        }
        if (t.codePointCount(0, t.length()) > max) {
            throw ApiException.badRequest("too_long", field + ": en çok " + max + " karakter olabilir");
        }
        return t;
    }

    /** İsteğe bağlı dil kodu (BCP-47, ör. {@code tr}, {@code en-US}); geçersizse yok sayılır. */
    public static String locale(String raw) {
        if (raw == null) return null;
        String l = raw.trim();
        return LOCALE.matcher(l).matches() ? l : null;
    }
}
