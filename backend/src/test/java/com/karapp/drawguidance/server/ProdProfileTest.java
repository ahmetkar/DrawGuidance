package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.support.Http;
import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.TestBillingConfig;
import com.karapp.drawguidance.server.support.TestJwtConfig;
import com.karapp.drawguidance.server.support.TestMongo;
import com.karapp.drawguidance.server.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * v40 üretim profili ({@code prod}): zorunlu ayarlarla açılır, RTDN OIDC ayarı yoksa 503, sağlık ayrıntısı yok ve
 * {@code X-Forwarded-For} yalnız güvenilen vekilden (burada {@code TRUSTED_PROXIES=127\.0\.0\.1}) ve sağdan okunur:
 * istemcinin başa eklediği sahte adres istek sınırını atlatamaz.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Import({TestJwtConfig.class, TestBillingConfig.class})
class ProdProfileTest {
    @LocalServerPort
    int port;

    @Autowired
    Environment env;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // application-prod.yml'deki zorunlu yer tutucular (üretimde ortamdan / Docker sırlarından gelir)
        r.add("MONGODB_URI", () -> TestMongo.url("dg_prod"));
        r.add("IP_HASH_SALT", () -> "test-tuzu-" + UUID.randomUUID());
        // v43 üretimde zorunlu cihaz şifreleme anahtarı
        r.add("DEVICE_DATA_KEY", com.karapp.drawguidance.server.distribution.DeviceCrypto::newKey);
        r.add("TRUSTED_PROXIES", () -> "127\\.0\\.0\\.1");
        r.add("drawguidance.catalog.seed-resource", () -> "classpath:catalog/test-catalog.json.gz");
        r.add("drawguidance.firebase.project-id", () -> TestTokens.PROJECT);
        r.add("drawguidance.mongo.min-pool-size", () -> "1");
        r.add("drawguidance.slack.webhook-url", () -> "");
    }

    @Test
    void prodDefaults() {
        Http http = new Http(port);
        assertEquals("true", env.getProperty("drawguidance.billing.require-account-id"));
        assertEquals("native", env.getProperty("server.forward-headers-strategy"));
        Res rtdn = http.call("POST", "/api/v1/billing/rtdn", "{\"message\":{\"messageId\":\"1\"}}");
        assertEquals(503, rtdn.status(), rtdn.body());
        assertEquals("rtdn_not_configured", rtdn.error());
        Res health = http.get("/actuator/health");
        assertEquals("UP", health.json().path("status").asText());
        assertEquals(false, health.json().has("components") || health.json().has("details"), health.body());
    }

    @Test
    void forwardedForIsReadFromTheRightOnlyBehindTrustedProxy() {
        Http http = new Http(port);
        String body = "{\"email\":\"a@example.com\",\"subject\":\"Merhaba\",\"message\":\"Deneme\"}";
        // Form sınırı öntanımlı saatte 5: istemci her istekte başka sahte adres ekler, vekilin eklediği adres aynı
        for (int i = 0; i < 5; i++) {
            Res r = http.call("POST", "/api/v1/support/messages", body, "X-Forwarded-For", "6.6.6." + i + ", 203.0.113.77");
            assertEquals(202, r.status(), r.body());
        }
        assertEquals(429, http.call("POST", "/api/v1/support/messages", body, "X-Forwarded-For", "6.6.6.99, 203.0.113.77").status());
        // Başka gerçek istemci etkilenmez
        assertEquals(202, http.call("POST", "/api/v1/support/messages", body, "X-Forwarded-For", "203.0.113.78").status());
    }

    /** Öntanımlı güvenilen vekil ifadesi (yığındaki edge ağı 10.231.0.0/24) yer tutucu çözümünde bozulmuyor. */
    @Test
    void defaultTrustedProxyRegexSurvivesPlaceholderResolution() throws Exception {
        StandardEnvironment e = new StandardEnvironment();
        for (PropertySource<?> s : new YamlPropertySourceLoader().load("prod", new ClassPathResource("application-prod.yml"))) {
            e.getPropertySources().addLast(s);
        }
        String regex = e.getProperty("server.tomcat.remoteip.internal-proxies");
        assertEquals("10\\.231\\.0\\.\\d{1,3}", regex);
        assertEquals(true, "10.231.0.7".matches(regex));
        assertEquals(false, "10.0.0.7".matches(regex));
    }
}
