package com.karapp.drawguidance.server.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * v40 Slack bildirimleri: gelen web kancasına ({@code drawguidance.slack.webhook-url}, ortam değişkeni
 * {@code SLACK_WEBHOOK_URL}) kısa bir ileti gönderir. Bütün uçlar ortak kullanır (şimdilik hesap silme talepleri ve
 * destek mesajları; ileride hata raporları ve geri bildirimler).
 *
 * <ul>
 *   <li><b>Arka planda ve en iyi çaba:</b> {@link #send} hemen döner, istek sanal iş parçacığında gider. Gönderilemezse
 *       yalnız günlüğe uyarı yazılır; çağıran işlem (kayıt, 202 yanıtı) hiç etkilenmez.</li>
 *   <li><b>Adres bir sırdır:</b> günlüğe, hata iletisine ya da yanıta yazılmaz (hata günlüğünde yalnız HTTP durumu ya da
 *       istisna türü görünür).</li>
 *   <li>Adres boşsa hiçbir şey yapılmaz ({@link #enabled()} {@code false}).</li>
 *   <li>Kullanıcıdan gelen yazılar {@link #escape} ile kaçışlanır ({@code <!channel>} gibi bahsetmeler ve bağlantılar
 *       çalışmasın); e-postalar {@link #maskEmail} ile kısmen gizlenir.</li>
 * </ul>
 */
@Service
public class SlackNotifier {
    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** Slack tek iletide 40 000 karakteri keser; bildirimler kısa tutulur. */
    static final int MAX_TEXT = 3_000;
    static final int MAX_FIELD = 300;

    private final URI webhook;
    private final ObjectMapper json;
    private final HttpClient http;

    public SlackNotifier(DrawGuidanceProperties props, ObjectMapper json) {
        this.webhook = parse(props.slack().webhookUrl());
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        if (webhook != null) log.info("Slack bildirimleri açık");
    }

    /** Adres yalnız http(s) olabilir; geçersizse bildirim kapalı (adresin kendisi günlüğe yazılmaz). */
    private static URI parse(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if ((scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null) return uri;
        } catch (IllegalArgumentException ignored) {
            // aşağıda günlüğe yazılır (adres olmadan)
        }
        log.warn("SLACK_WEBHOOK_URL geçersiz (http(s) adresi değil); Slack bildirimleri kapalı");
        return null;
    }

    public boolean enabled() { return webhook != null; }

    /**
     * Başlık ve alanlardan ileti: {@code *başlık*} ve her alan için {@code • ad: değer} satırı. Değerler kaçışlanır ve
     * {@link #MAX_FIELD} karakterde kesilir; boş değerli alanlar atlanır.
     */
    public CompletableFuture<Boolean> send(String title, Map<String, String> fields) {
        StringBuilder text = new StringBuilder("*").append(escape(title)).append('*');
        if (fields != null) {
            fields.forEach((k, v) -> {
                if (v == null || v.isBlank()) return;
                text.append("\n• ").append(escape(k)).append(": ").append(escape(cut(oneLine(v), MAX_FIELD)));
            });
        }
        return send(text.toString());
    }

    /**
     * Hazır (kaçışlanmış) metni gönderir. Dönen sonuç yalnız testler ve isteyenler içindir: {@code true} = Slack 2xx
     * döndü; {@code false} = kapalı ya da gönderilemedi. Asla istisna fırlatmaz.
     */
    public CompletableFuture<Boolean> send(String text) {
        if (webhook == null || text == null || text.isBlank()) return CompletableFuture.completedFuture(false);
        byte[] body;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("text", cut(text, MAX_TEXT));
            payload.put("unfurl_links", false);
            payload.put("unfurl_media", false);
            body = json.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            log.warn("Slack bildirimi hazırlanamadı: {}", e.getClass().getSimpleName());
            return CompletableFuture.completedFuture(false);
        }
        HttpRequest request = HttpRequest.newBuilder(webhook)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            return http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .handle((res, err) -> {
                        if (err != null) {
                            Throwable cause = err.getCause() != null ? err.getCause() : err;
                            // İstisna iletisi adresi içerebilir: yalnız türü yazılır
                            log.warn("Slack bildirimi gönderilemedi: {}", cause.getClass().getSimpleName());
                            return false;
                        }
                        if (res.statusCode() / 100 != 2) {
                            log.warn("Slack bildirimi gönderilemedi: HTTP {}", res.statusCode());
                            return false;
                        }
                        return true;
                    });
        } catch (RuntimeException e) {
            log.warn("Slack bildirimi gönderilemedi: {}", e.getClass().getSimpleName());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * E-postayı kısmen gizler: {@code ahmet@gmail.com} → {@code a***@gmail.com}. Biçimsizse {@code ***}.
     */
    public static String maskEmail(String email) {
        if (email == null) return "***";
        String e = email.trim();
        int at = e.lastIndexOf('@');
        if (at <= 0 || at == e.length() - 1) return "***";
        return e.codePoints().limit(1).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                + "***" + e.substring(at);
    }

    /** Slack biçim karakterleri ({@code & < >}) kaçışlanır: kullanıcı yazısı bahsetme ya da bağlantı olamaz. */
    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String oneLine(String s) { return s.replaceAll("[\\r\\n\\t]+", " ").trim(); }

    private static String cut(String s, int max) {
        if (s.length() <= max) return s;
        int end = max - 1;
        // Vekil çifti bölünmez
        if (Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end) + "…";
    }
}
