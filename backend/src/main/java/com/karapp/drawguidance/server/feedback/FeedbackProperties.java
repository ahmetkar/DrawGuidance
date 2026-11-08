package com.karapp.drawguidance.server.feedback;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * v40 geri bildirim ucunun sınırı ({@code drawguidance.feedback.*}): kullanıcı başına jeton kovası.
 *
 * @param limit   pencere başına en çok geri bildirim (öntanımlı 10)
 * @param window  kovanın tamamen dolma süresi (öntanımlı 1 saat)
 */
@ConfigurationProperties(prefix = "drawguidance.feedback")
public record FeedbackProperties(Integer limit, Duration window) {
    public FeedbackProperties {
        if (limit == null || limit <= 0) limit = 10;
        if (window == null || window.isNegative() || window.isZero()) window = Duration.ofHours(1);
    }
}
