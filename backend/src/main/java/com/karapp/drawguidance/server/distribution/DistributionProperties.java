package com.karapp.drawguidance.server.distribution;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * v43 içerik dağıtımı ({@code drawguidance.distribution.*}): paketler, bildirimler, duyurular ve uygulama
 * güncellemelerinin cihazlara gönderilmesi. Firebase/FCM kullanılmaz: cihazlar gelen kutusunu yoklar; uygulama açıkken
 * uzun yoklamayla (long polling) beklerler.
 *
 * @param workersEnabled        arka plan işçileri çalışsın mı (CLI kipinde ve bazı testlerde kapalı)
 * @param workerInterval        işçi turları arası bekleme
 * @param batchSize             gönderim açarken bir toplu yazmadaki cihaz sayısı
 * @param signedUrlTtl          Cloud Storage imzalı indirme adreslerinin geçerlilik süresi (en çok 7 gün)
 * @param gcsBucket             paket resimlerinin bulunduğu bucket ({@code gs://<bucket>/...} adresleri bu bucket'a
 *                              ait olmalı; boşsa {@code gs://} adresi verilemez)
 * @param gcsServiceAccountFile imzalı adres ve yükleme için hizmet hesabı JSON dosyası (sırdır)
 * @param deviceKey             cihaz bilgilerinin şifreleme anahtarı: base64, 32 bayt (AES-256-GCM ve HMAC-SHA256
 *                              için ayrı alt anahtarlar türetilir). Boşsa açılışta rastgele üretilir ve uyarı
 *                              yazılır (yeniden başlatınca eski kayıtlar okunamaz; üretimde zorunlu)
 * @param pollInterval          cihazların arka planda yoklama aralığı önerisi (yanıtta {@code X-Poll-After})
 * @param busyPollInterval      sunucu yoğunken önerilen daha uzun aralık
 * @param longPollMax           uygulama açıkken bir yoklamanın en uzun bekleme süresi
 * @param maxWaiters            bu sunucu kopyasında aynı anda bekleyen uzun yoklama sınırı (aşılınca beklemeden yanıt
 *                              ve daha uzun aralık önerisi: yük atma)
 * @param dataCacheTtl          teslimat verisinin (imzalı adreslerle) bellekte tutulma süresi
 */
@ConfigurationProperties(prefix = "drawguidance.distribution")
public record DistributionProperties(Boolean workersEnabled, Duration workerInterval, Integer batchSize, Duration signedUrlTtl,
                                     String gcsBucket, String gcsServiceAccountFile, String deviceKey, Duration pollInterval,
                                     Duration busyPollInterval, Duration longPollMax, Integer maxWaiters, Duration dataCacheTtl) {
    public DistributionProperties {
        if (workersEnabled == null) workersEnabled = true;
        workerInterval = positive(workerInterval, Duration.ofSeconds(10));
        if (batchSize == null || batchSize <= 0) batchSize = 1000;
        signedUrlTtl = positive(signedUrlTtl, Duration.ofHours(24));
        if (signedUrlTtl.compareTo(Duration.ofDays(7)) > 0) signedUrlTtl = Duration.ofDays(7);
        gcsBucket = blank(gcsBucket);
        gcsServiceAccountFile = blank(gcsServiceAccountFile);
        deviceKey = blank(deviceKey);
        pollInterval = positive(pollInterval, Duration.ofMinutes(15));
        busyPollInterval = positive(busyPollInterval, Duration.ofMinutes(45));
        longPollMax = positive(longPollMax, Duration.ofSeconds(25));
        if (longPollMax.compareTo(Duration.ofSeconds(55)) > 0) longPollMax = Duration.ofSeconds(55);
        if (maxWaiters == null || maxWaiters < 0) maxWaiters = 5000;
        dataCacheTtl = positive(dataCacheTtl, Duration.ofMinutes(30));
        // İmzalı adres önbellekteyken süresi dolmasın
        if (dataCacheTtl.compareTo(signedUrlTtl.dividedBy(2)) > 0) dataCacheTtl = signedUrlTtl.dividedBy(2);
    }

    private static Duration positive(Duration d, Duration fallback) { return d == null || d.isNegative() || d.isZero() ? fallback : d; }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
