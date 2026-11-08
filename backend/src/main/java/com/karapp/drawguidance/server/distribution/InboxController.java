package com.karapp.drawguidance.server.distribution;

import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.web.PublicForms;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * v43 cihaz kaydı ve gelen kutusu (Firebase jetonu zorunlu; cihaz kimliği {@code X-Device-Id} başlığında). Bildirim
 * altyapısı Firebase/FCM kullanmaz: cihaz yoklar.
 *
 * <ul>
 *   <li>{@code PUT /api/v1/devices/me}: cihazı kaydeder/günceller ({@link DeviceService.Registration}).</li>
 *   <li>{@code DELETE /api/v1/devices/me}: çıkışta cihazın hesapla bağı kaldırılır.</li>
 *   <li>{@code GET /api/v1/inbox[?wait=25]}: bekleyen teslimatlar {@code {items: [{deliveryId, kind, refId, version,
 *       stage, dataReady}]}}. Yanıtta {@code ETag} (gelen kutusu sürümü) ve {@code X-Poll-After} (saniye: bir sonraki
 *       arka plan yoklaması). {@code If-None-Match} aynıysa ve değişiklik yoksa <b>304</b> (tek dizinli okuma).
 *       {@code wait} verilirse (uygulama açıkken) değişiklik olana ya da süre dolana kadar beklenir (uzun yoklama);
 *       sunucu yoğunsa beklemeden 304 ve daha uzun {@code X-Poll-After}.</li>
 *   <li>{@code POST /api/v1/inbox/ack} {@code {"items": [{"deliveryId", "stage": INFO_RECEIVED|DELIVERED|FAILED,
 *       "error"?}]}} (en çok 200): öğe başına yeni aşama.</li>
 *   <li>{@code GET /api/v1/inbox/{id}/data}: teslimatın verisi (paket öğeleri imzalı adreslerle).</li>
 * </ul>
 */
@RestController
public class InboxController {
    public static final String DEVICES = "/api/v1/devices";
    public static final String INBOX = "/api/v1/inbox";
    public static final String DEVICE_HEADER = "X-Device-Id";
    public static final String POLL_AFTER = "X-Poll-After";

    public record AckBody(List<DeliveryService.AckItem> items) {}

    private final DeviceService devices;
    private final DeliveryService deliveries;
    private final InboxSignals signals;
    private final DistributionProperties props;
    private final PublicForms forms;

    public InboxController(DeviceService devices, DeliveryService deliveries, InboxSignals signals, DistributionProperties props, PublicForms forms) {
        this.devices = devices;
        this.deliveries = deliveries;
        this.signals = signals;
        this.props = props;
        this.forms = forms;
    }

    @PutMapping(DEVICES + "/me")
    public ResponseEntity<Object> register(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        DeviceService.Registration body = forms.readJson(request, DeviceService.Registration.class);
        try {
            devices.register(jwt.getSubject(), body);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_device", e.getMessage());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(POLL_AFTER, pollAfter()).body(Map.of("ok", true));
    }

    @DeleteMapping(DEVICES + "/me")
    public ResponseEntity<Void> unregister(@AuthenticationPrincipal Jwt jwt, @RequestHeader(value = DEVICE_HEADER, required = false) String deviceId) {
        key(deviceId);
        devices.unregister(jwt.getSubject(), deviceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(INBOX)
    public ResponseEntity<Object> inbox(@AuthenticationPrincipal Jwt jwt, @RequestHeader(value = DEVICE_HEADER, required = false) String deviceId,
                                        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
                                        @RequestParam(value = "wait", required = false) Integer waitSeconds) {
        String key = key(deviceId);
        long version = devices.inboxVersion(key);
        if (version < 0) throw ApiException.notFound("device_not_registered", "Önce cihaz kaydı gerekli (PUT /api/v1/devices/me)");
        String etag = etag(version);
        if (etag.equals(ifNoneMatch)) {
            if (waitSeconds == null || waitSeconds <= 0) return notModified(etag);
            long wait = Math.min(waitSeconds, props.longPollMax().toSeconds());
            CompletableFuture<Boolean> f = signals.register(key);
            if (f == null) return notModified(etag); // yük atma: bekleyen sınırı dolu
            try {
                // Kayıttan önce değişmiş olabilir
                long now = devices.inboxVersion(key);
                if (now == version) f.get(wait, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                return notModified(etag);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return notModified(etag);
            } catch (ExecutionException e) {
                return notModified(etag);
            } finally {
                signals.unregister(key, f);
            }
            version = devices.inboxVersion(key);
            etag = etag(version);
            if (etag.equals(ifNoneMatch)) return notModified(etag);
        }
        List<Map<String, Object>> items = deliveries.inbox(jwt.getSubject(), key);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(etag).header(POLL_AFTER, pollAfter()).body(Map.of("items", items));
    }

    @PostMapping(INBOX + "/ack")
    public ResponseEntity<Object> ack(@AuthenticationPrincipal Jwt jwt, @RequestHeader(value = DEVICE_HEADER, required = false) String deviceId,
                                      HttpServletRequest request) {
        String key = key(deviceId);
        AckBody body = forms.readJson(request, AckBody.class);
        try {
            Map<String, String> result = deliveries.ack(jwt.getSubject(), key, body == null ? null : body.items(), Instant.now());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("results", result));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_ack", e.getMessage());
        }
    }

    @GetMapping(INBOX + "/{id}/data")
    public ResponseEntity<Object> data(@AuthenticationPrincipal Jwt jwt, @RequestHeader(value = DEVICE_HEADER, required = false) String deviceId,
                                       @PathVariable("id") String id) {
        String key = key(deviceId);
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(deliveries.data(jwt.getSubject(), key, id, Instant.now()));
        } catch (DeliveryService.StorageUnavailableException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "storage_unavailable", "İçerik şu an hazırlanamıyor; biraz sonra yeniden deneyin");
        } catch (IllegalStateException e) {
            throw ApiException.conflict("not_ready", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("not_found", "Teslimat bulunamadı");
        }
    }

    private ResponseEntity<Object> notModified(String etag) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED).cacheControl(CacheControl.noStore()).eTag(etag).header(POLL_AFTER, pollAfter()).build();
    }

    /** Önerilen arka plan yoklama aralığı (saniye); yoğunlukta uzar. Cihazlar üzerine rastgele sapma ekler. */
    private String pollAfter() {
        Duration d = signals.busy() ? props.busyPollInterval() : props.pollInterval();
        return String.valueOf(d.toSeconds());
    }

    static String etag(long version) { return "\"v" + version + "\""; }

    private String key(String deviceId) {
        try {
            return devices.key(deviceId);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_device", "X-Device-Id başlığı gerekli (16–64 karakter: harf, rakam, _ -)");
        }
    }
}
