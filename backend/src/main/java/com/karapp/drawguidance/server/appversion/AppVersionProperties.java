package com.karapp.drawguidance.server.appversion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * v40 uygulama sürümü bilgisi ({@code drawguidance.app.*}; ortam değişkenleri {@code APP_LATEST_VERSION_CODE},
 * {@code APP_MIN_SUPPORTED_VERSION_CODE}, {@code APP_UPDATE_MESSAGE}).
 *
 * @param latestVersionCode        Play'deki en yeni sürüm kodu (0: bilinmiyor; güncelleme bildirimi gönderilmez)
 * @param minSupportedVersionCode  desteklenen en eski sürüm kodu (altındaki uygulamalar açılışta güncellemeye zorlanır)
 * @param message                  isteğe bağlı kısa ileti (bildirimde ve zorunlu güncelleme penceresinde gösterilir)
 */
@ConfigurationProperties(prefix = "drawguidance.app")
public record AppVersionProperties(Long latestVersionCode, Long minSupportedVersionCode, String message) {
    public AppVersionProperties {
        if (latestVersionCode == null || latestVersionCode < 0) latestVersionCode = 0L;
        if (minSupportedVersionCode == null || minSupportedVersionCode < 0) minSupportedVersionCode = 0L;
        if (message != null && message.isBlank()) message = null;
        if (message != null && message.length() > 500) message = message.substring(0, 500);
    }
}
