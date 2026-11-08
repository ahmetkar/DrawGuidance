package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.support.Http;
import com.karapp.drawguidance.server.support.SlackStub;
import com.karapp.drawguidance.server.support.TestBillingConfig;
import com.karapp.drawguidance.server.support.TestJwtConfig;
import com.karapp.drawguidance.server.support.TestMongo;
import com.karapp.drawguidance.server.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Uçtan uca testlerin ortak bağlamı: gerçek MongoDB 7 (kopya kümesi, {@code dg_api} veritabanı), rastgele kapıda
 * gerçek HTTP, test kataloğu ({@code test-catalog.json.gz}, sürüm 1000; v37: ikili biçim için tam alanlı "star"
 * vektörü eklendi) ve yerel RSA anahtarlı jeton çözücü. Bu
 * sınıftan türeyen test sınıfları aynı Spring bağlamını paylaşır.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// v39 Google Play API yerine sahtesi
@Import({TestJwtConfig.class, TestBillingConfig.class})
abstract class ApiContextTest {
    @LocalServerPort
    int port;

    Http http;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> TestMongo.url("dg_api"));
        r.add("drawguidance.catalog.seed-resource", () -> "classpath:catalog/test-catalog.json.gz");
        r.add("drawguidance.firebase.project-id", () -> TestTokens.PROJECT);
        r.add("drawguidance.mongo.min-pool-size", () -> "1");
        // v37 rapor ucu: kurulum sınırı küçük (sınır testi), IP sınırı büyük (bütün testler 127.0.0.1'den)
        r.add("drawguidance.reports.per-install-limit", () -> "5");
        r.add("drawguidance.reports.per-ip-limit", () -> "100000");
        // v40 form uçları: testler 127.0.0.1'den gelir; her test kendi X-Forwarded-For adresiyle ayrı kova kullanır
        r.add("drawguidance.web.behind-proxy", () -> "true");
        // v40 isteğe bağlı CORS kalıbı (Vercel önizlemeleri); izinli kökenler öntanımlı kalır
        r.add("drawguidance.web.allowed-origin-patterns", () -> "https://drawguidance-*.vercel.app");
        // v40 Slack bildirimleri yerel sahte sunucuya gider
        r.add("drawguidance.slack.webhook-url", SlackStub::url);
        // v40 API istek sınırları bu bağlamda pratikte kapalı (bütün testler 127.0.0.1'den); sınırlar HardeningApiTest'te
        r.add("drawguidance.limits.ip-limit", () -> "1000000");
        r.add("drawguidance.limits.write-limit", () -> "1000000");
        r.add("drawguidance.limits.verify-limit", () -> "1000000");
        // v43 dağıtım işçisi testlerde elle çalıştırılır (tick); bucket ayarlı ama hizmet hesabı yok (imza verilemez)
        r.add("drawguidance.distribution.workers-enabled", () -> "false");
        r.add("drawguidance.distribution.gcs-bucket", () -> "dg-test-bucket");
        r.add("drawguidance.distribution.long-poll-max", () -> "3s");
    }

    @BeforeEach
    void http() { http = new Http(port); }
}
