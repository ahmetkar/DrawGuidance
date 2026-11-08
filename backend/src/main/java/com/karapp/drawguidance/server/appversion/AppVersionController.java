package com.karapp.drawguidance.server.appversion;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * v40 {@code GET /api/v1/app/version} (herkese açık): {@code {latestVersionCode, minSupportedVersionCode, message}}.
 * Uygulamanın günlük işi güncelleme bildirimi ve zorunlu güncelleme için okur. Değerler ayardan gelir; yanıt 5 dakika
 * önbelleğe alınabilir. v43: CLI ile eklenen sürümler ({@code app_releases}) ayardakinden yeniyse onlar kullanılır.
 */
@RestController
@RequestMapping(AppVersionController.PATH)
public class AppVersionController {
    public static final String PATH = "/api/v1/app/version";

    public record Version(long latestVersionCode, long minSupportedVersionCode, String message) {}

    private final AppVersionProperties props;
    private final com.karapp.drawguidance.server.distribution.ContentService content;

    public AppVersionController(AppVersionProperties props, com.karapp.drawguidance.server.distribution.ContentService content) {
        this.props = props;
        this.content = content;
    }

    @GetMapping
    public ResponseEntity<Version> version() {
        // v43 CLI ile kaydedilen en yeni sürüm (app_releases) ayardakinden yeniyse o kullanılır
        long latestCfg = props.latestVersionCode();
        long min = props.minSupportedVersionCode();
        String message = props.message();
        org.bson.Document r = null;
        try {
            r = content.latestRelease();
        } catch (RuntimeException ignored) {
            // veritabanı yoksa ayardaki değerler
        }
        if (r != null && ((Number) r.get("_id")).longValue() >= latestCfg) {
            latestCfg = ((Number) r.get("_id")).longValue();
            min = Math.max(min, ((Number) r.get("minSupportedVersionCode")).longValue());
            if (r.getString("message") != null) message = r.getString("message");
        }
        // En yeni sürüm, desteklenen en eskiden küçük yazılmışsa en eski kabul edilir
        long latest = Math.max(latestCfg, min);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new Version(latest, min, message));
    }
}
