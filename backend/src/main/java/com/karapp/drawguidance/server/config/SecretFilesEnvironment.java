package com.karapp.drawguidance.server.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v40 Docker sırları ({@code *_FILE} kuralı): {@code X_FILE=/run/secrets/x} verilirse dosyanın içeriği {@code X} ortam
 * değişkeni gibi kullanılır (ör. {@code SLACK_WEBHOOK_URL_FILE} → {@code SLACK_WEBHOOK_URL}). Böylece sırlar
 * {@code docker inspect} ile görünen ortam değişkenlerine yazılmaz.
 *
 * <ul>
 *   <li>Yalnız {@link #SECRETS} listesindeki adlar okunur (yanlışlıkla başka bir {@code *_FILE} değişkeni ayar olmasın).</li>
 *   <li>{@code GOOGLE_PLAY_SERVICE_ACCOUNT_JSON} zaten bir dosya yoludur: {@code GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_FILE}
 *       verilirse içerik değil yol aktarılır.</li>
 *   <li>Dosyanın sonundaki satır sonları atılır ({@code printf}/{@code echo} farkı önemsiz).</li>
 *   <li>Aynı ad hem doğrudan hem {@code _FILE} ile verilirse ya da dosya okunamazsa açılış durur (yanlış/eksik sırla
 *       çalışmaktansa). Değerler hiçbir zaman günlüğe ya da hata iletisine yazılmaz; iletide yalnız ad ve yol olur.</li>
 * </ul>
 */
public class SecretFilesEnvironment implements EnvironmentPostProcessor, Ordered {
    public static final String SOURCE = "dockerSecretFiles";
    /** İçeriği okunan sırlar (ortam değişkeni adları). */
    public static final List<String> SECRETS = List.of(
            "MONGODB_URI", "MONGO_APP_PASSWORD", "SLACK_WEBHOOK_URL", "IP_HASH_SALT",
            "PUBSUB_PUSH_AUDIENCE", "PUBSUB_PUSH_SERVICE_ACCOUNT", "FIREBASE_PROJECT_ID",
            // v43 cihaz bilgilerinin şifreleme anahtarı
            "DEVICE_DATA_KEY");
    /** Dosya yolu olarak aktarılanlar. */
    public static final List<String> PATHS = List.of("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON",
            // v43 Cloud Storage hizmet hesabı (paket resimleri: yükleme ve imzalı adres)
            "GCS_SERVICE_ACCOUNT_JSON");
    /** Sır dosyasının üst sınırı (yanlış dosya verilirse belleğe büyük dosya okunmasın). */
    static final long MAX_BYTES = 64 * 1024;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> values = resolve(System.getenv());
        if (!values.isEmpty()) environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, values));
    }

    /** Ortamdan {@code *_FILE} değişkenlerini çözer (testler için ayrı). */
    static Map<String, Object> resolve(Map<String, String> env) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String name : SECRETS) {
            String file = env.get(name + "_FILE");
            if (file == null || file.isBlank()) continue;
            conflict(env, name);
            out.put(name, read(name, Path.of(file.trim())));
        }
        for (String name : PATHS) {
            String file = env.get(name + "_FILE");
            if (file == null || file.isBlank()) continue;
            conflict(env, name);
            Path path = Path.of(file.trim());
            if (!Files.isReadable(path)) throw new IllegalStateException(name + "_FILE okunamıyor: " + path);
            out.put(name, path.toString());
        }
        return out;
    }

    private static void conflict(Map<String, String> env, String name) {
        String direct = env.get(name);
        if (direct != null && !direct.isEmpty()) {
            throw new IllegalStateException(name + " ve " + name + "_FILE birlikte verilmiş; yalnız birini kullanın");
        }
    }

    static String read(String name, Path path) {
        try {
            if (Files.size(path) > MAX_BYTES) throw new IllegalStateException(name + "_FILE çok büyük: " + path);
            String value = Files.readString(path, StandardCharsets.UTF_8);
            int end = value.length();
            while (end > 0 && (value.charAt(end - 1) == '\n' || value.charAt(end - 1) == '\r')) end--;
            return value.substring(0, end);
        } catch (IOException e) {
            // İstisnanın iletisi yalnız yolu içerir; içerik değil
            throw new IllegalStateException(name + "_FILE okunamadı: " + path + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 20; }
}
