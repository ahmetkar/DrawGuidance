package com.karapp.drawguidance.server.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoCompressor;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB istemcisinin yüksek trafik ayarları: bağlantı havuzu (en çok/en az, aynı anda açılan), kısa zaman aşımları
 * (sunucu seçimi, bağlantı), yeniden denenen okuma ve yazmalar, kablo sıkıştırması (zstd → snappy → zlib), okuma
 * tercihi ve yazma onayı. Bu ayarlar bağlantı adresindeki (URI) karşılıklarının üstüne yazılır; değiştirmek için
 * {@code drawguidance.mongo.*} ayarları ya da ortam değişkenleri ({@code DRAWGUIDANCE_MONGO_MAXPOOLSIZE} …) kullanılır.
 *
 * <p>v40 kimlik: {@code drawguidance.mongo.password} ({@code MONGO_APP_PASSWORD} ya da Docker sırrı
 * {@code MONGO_APP_PASSWORD_FILE}) verilirse kullanıcı adı ({@code MONGO_APP_USER}) ve parola URI'dekinin yerine
 * kullanılır (SCRAM, sunucuyla anlaşılır). Böylece parola bağlantı adresine ve ortam değişkenine yazılmaz.
 */
@Configuration
public class MongoClientConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer drawGuidanceMongoSettings(DrawGuidanceProperties props, Environment env) {
        DrawGuidanceProperties.Mongo m = props.mongo();
        MongoCredential credential = credential(m, env.getProperty("spring.data.mongodb.uri"));
        return builder -> {
            if (credential != null) builder.credential(credential);
            builder.applyToConnectionPoolSettings(pool -> pool
                    .maxSize(m.maxPoolSize())
                    .minSize(m.minPoolSize())
                    .maxConnecting(m.maxConnecting())
                    .maxConnectionIdleTime(60, TimeUnit.SECONDS)
                    .maxWaitTime(m.timeoutsMs(), TimeUnit.MILLISECONDS));
            builder.applyToSocketSettings(s -> s.connectTimeout(m.timeoutsMs(), TimeUnit.MILLISECONDS).readTimeout(30, TimeUnit.SECONDS));
            builder.applyToClusterSettings(c -> c.serverSelectionTimeout(m.timeoutsMs(), TimeUnit.MILLISECONDS));
            builder.retryWrites(true).retryReads(true);
            builder.compressorList(compressors(m.compressors()));
            builder.readPreference(ReadPreference.valueOf(m.readPreference()));
            builder.writeConcern(writeConcern(m.writeConcern()));
            builder.applicationName("drawguidance-server");
        };
    }

    /** v40 ayrı verilen kullanıcı/parola; parola yoksa {@code null} (URI'deki kimlik geçerli). */
    static MongoCredential credential(DrawGuidanceProperties.Mongo m, String uri) {
        if (m.password() == null) return null;
        ConnectionString cs = uri == null || uri.isBlank() ? null : new ConnectionString(uri);
        String user = m.username() != null ? m.username() : cs != null && cs.getUsername() != null ? cs.getUsername() : null;
        if (user == null) throw new IllegalStateException("MONGO_APP_PASSWORD verildi ama kullanıcı adı yok (MONGO_APP_USER)");
        String source = m.authSource();
        if (source == null && cs != null && cs.getCredential() != null) source = cs.getCredential().getSource();
        if (source == null && cs != null && uri.contains("authSource=")) source = authSourceOf(uri);
        if (source == null && cs != null) source = cs.getDatabase();
        if (source == null) source = "admin";
        return MongoCredential.createCredential(user, source, m.password().toCharArray());
    }

    private static String authSourceOf(String uri) {
        for (String part : uri.substring(uri.indexOf('?') + 1).split("&")) {
            if (part.startsWith("authSource=")) return part.substring("authSource=".length());
        }
        return null;
    }

    static List<MongoCompressor> compressors(String list) {
        List<MongoCompressor> out = new ArrayList<>();
        for (String name : list.split(",")) {
            switch (name.trim().toLowerCase()) {
                case "zstd" -> out.add(MongoCompressor.createZstdCompressor());
                case "snappy" -> out.add(MongoCompressor.createSnappyCompressor());
                case "zlib" -> out.add(MongoCompressor.createZlibCompressor().withProperty(MongoCompressor.LEVEL, 3));
                default -> { }
            }
        }
        return out;
    }

    static WriteConcern writeConcern(String value) {
        return switch (value.trim().toLowerCase()) {
            case "w1", "1" -> WriteConcern.W1.withJournal(true);
            case "unacknowledged", "0" -> WriteConcern.UNACKNOWLEDGED;
            default -> WriteConcern.MAJORITY.withWTimeout(10, TimeUnit.SECONDS);
        };
    }
}
