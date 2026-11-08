package com.karapp.drawguidance.server.sync;

import com.karapp.drawguidance.server.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * v40 {@code GET /api/v1/sync/permit} (herkese açık, jeton isteğe bağlı): uygulama katalog içeriğini (şekiller,
 * vektörler, yapay zekâ ağları) yalnız {@code allowed=true} iken indirir; değilse {@code nextSlotStart} zamanına
 * kendini planlar. Hesap verileri (profil, abonelik, ilerleme, raporlar, geri bildirim) bu izni beklemez.
 *
 * <ul>
 *   <li>Geçerli Firebase jetonu: kullanıcının kalıcı grubu ve dilimi.</li>
 *   <li>Jeton yoksa (ya da geçersizse) cihaz kimliği gerekir: {@code ?deviceId=} ya da {@code X-Device-Id}
 *       (8–128 karakter; harf, rakam, {@code - _ .}). İkisi de yoksa geçersiz jetonda 401, jetonsuzda 400.</li>
 * </ul>
 */
@RestController
@RequestMapping(SyncPermitController.PATH)
public class SyncPermitController {
    public static final String PATH = "/api/v1/sync/permit";
    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,128}$");

    private final SyncPermitService permits;
    private final JwtDecoder jwt;

    public SyncPermitController(SyncPermitService permits, JwtDecoder jwt) {
        this.permits = permits;
        this.jwt = jwt;
    }

    @GetMapping
    public SyncPermitService.Permit permit(HttpServletRequest request, @RequestParam(value = "deviceId", required = false) String deviceId) {
        String device = deviceId != null ? deviceId.trim() : trimOrNull(request.getHeader("X-Device-Id"));
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean badToken = false;
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            try {
                if (!token.isEmpty()) return permits.forUser(jwt.decode(token).getSubject());
                badToken = true;
            } catch (BadJwtException e) {
                badToken = true;
            } catch (JwtException e) {
                // Google anahtarları alınamadı: cihaz kimliği varsa onunla devam edilir
                if (device == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "auth_unavailable", "Kimlik doğrulama şu an yapılamıyor; biraz sonra yeniden deneyin");
            }
        }
        if (device == null || !DEVICE_ID.matcher(device).matches()) {
            if (badToken) throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_token", "Kimlik jetonu geçersiz ya da süresi dolmuş");
            throw ApiException.badRequest("invalid_device_id", "Jeton yoksa deviceId (8–128 karakter: harf, rakam, - _ .) gerekli");
        }
        return permits.forDevice(device);
    }

    private static String trimOrNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
