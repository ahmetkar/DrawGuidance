package com.karapp.drawguidance.server.distribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karapp.drawguidance.server.billing.play.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * v43 dağıtım bileşenleri: cihaz şifrelemesi ve Cloud Storage (imzalı adres/yükleme). Hizmet hesabı dosyası yoksa
 * sunucu yine çalışır; yalnız {@code gs://} adresli içerikler verilemez.
 */
@Configuration
public class DistributionConfig {
    private static final Logger log = LoggerFactory.getLogger(DistributionConfig.class);

    @Bean
    DeviceCrypto deviceCrypto(DistributionProperties props) {
        if (props.deviceKey() == null) {
            log.warn("DEVICE_DATA_KEY ayarlı değil: cihaz bilgileri için geçici rastgele anahtar üretildi (yeniden başlatınca eski cihaz kayıtları okunamaz)");
            return DeviceCrypto.ephemeral();
        }
        return DeviceCrypto.fromBase64(props.deviceKey());
    }

    @Bean
    Storage storage(DistributionProperties props, ObjectMapper json, @Value("${drawguidance.distribution.gcs-api-base:}") String apiBase) {
        ServiceAccountCredentials creds = null;
        if (props.gcsServiceAccountFile() != null) {
            try {
                creds = ServiceAccountCredentials.load(Path.of(props.gcsServiceAccountFile()), json);
                log.info("Cloud Storage hizmet hesabı yüklendi: {}", creds.clientEmail());
            } catch (Exception e) {
                log.error("Cloud Storage hizmet hesabı dosyası okunamadı: {}", e.getMessage());
            }
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        GcsClient client = creds == null ? null : new GcsClient(creds, json, http, apiBase.isBlank() ? null : apiBase);
        if (props.gcsBucket() == null) log.warn("GCS_BUCKET ayarlı değil: paketlerde gs:// adresleri kullanılamaz");
        return new Storage(client, props.gcsBucket(), props.signedUrlTtl());
    }

    /** Cloud Storage erişimi: istemci yoksa imzalı adres verilemez. */
    public record Storage(GcsClient client, String bucket, Duration ttl) {
        public boolean canSign() { return client != null && bucket != null; }

        /**
         * İçeriğin cihaza verilecek adresi: {@code gs://} ise imzalı adres (bucket ayarlı olanla aynı olmalı),
         * {@code https://} ise olduğu gibi. İmzalanamıyorsa null.
         */
        public String resolve(String url, Instant now) {
            if (url == null) return null;
            if (url.startsWith("https://")) return url;
            String[] gs = GcsClient.parse(url);
            if (gs == null || !canSign() || !gs[0].equals(bucket)) return null;
            return client.signedGetUrl(gs[0], gs[1], ttl, now);
        }
    }
}
