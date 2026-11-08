package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.account.DeletionRequestService;
import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.security.TokenRevocations;
import com.karapp.drawguidance.server.support.Http;
import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.SlackStub;
import com.karapp.drawguidance.server.support.TestBillingConfig;
import com.karapp.drawguidance.server.support.TestJwtConfig;
import com.karapp.drawguidance.server.support.TestMongo;
import com.karapp.drawguidance.server.support.TestTokens;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v40 güvenlik sertleştirmesi uçtan uca: güvenlik başlıkları, genel gövde sınırı (413), Actuator'ın yalnız ayrıntısız
 * sağlık ucu, hata iletilerinin iç ayrıntı sızdırmaması, IP/kullanıcı/doğrulama istek sınırları (429), hesap
 * silindikten sonra eski jetonun iptali, profil e-postasının jetondan yazılması. Küçük sınırlarla ayrı bağlam.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestJwtConfig.class, TestBillingConfig.class})
class HardeningApiTest {
    static final int IP_LIMIT = 30, WRITE_LIMIT = 5, VERIFY_LIMIT = 2, MAX_BODY = 2048;
    private static final AtomicInteger IPS = new AtomicInteger(1);

    @LocalServerPort
    int port;
    Http http;

    @Autowired
    MongoTemplate mongo;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> TestMongo.url("dg_hardening"));
        r.add("drawguidance.catalog.seed-resource", () -> "classpath:catalog/test-catalog.json.gz");
        r.add("drawguidance.firebase.project-id", () -> TestTokens.PROJECT);
        r.add("drawguidance.mongo.min-pool-size", () -> "1");
        r.add("drawguidance.web.behind-proxy", () -> "true");
        r.add("drawguidance.slack.webhook-url", SlackStub::url);
        r.add("drawguidance.limits.ip-limit", () -> String.valueOf(IP_LIMIT));
        r.add("drawguidance.limits.write-limit", () -> String.valueOf(WRITE_LIMIT));
        r.add("drawguidance.limits.verify-limit", () -> String.valueOf(VERIFY_LIMIT));
        r.add("drawguidance.limits.max-body-bytes", () -> String.valueOf(MAX_BODY));
    }

    @BeforeEach
    void http() { http = new Http(port); }

    /** Her test kendi IP kovasını kullansın (198.18.0.0/15 kıyaslama ağı). */
    static String newIp() {
        int n = IPS.getAndIncrement();
        return "198.19." + (n / 250) + "." + (n % 250 + 1);
    }

    static String uid() { return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20); }

    Res call(String method, String path, String body, String ip, String token) {
        return token == null ? http.call(method, path, body, "X-Forwarded-For", ip)
                : http.call(method, path, body, "X-Forwarded-For", ip, "Authorization", "Bearer " + token);
    }

    static String userBody(String uid, String email) {
        return "{\"id\":\"" + UUID.randomUUID() + "\",\"firebaseUid\":\"" + uid + "\",\"displayName\":\"Ahmet\",\"email\":\"" + email
                + "\",\"emailVerified\":true}";
    }

    // ---------------------------------------------------------------- başlıklar, Actuator, hata iletileri

    @Test
    void securityHeadersOnEveryResponse() {
        for (Res r : new Res[]{http.get("/api/health"), http.get("/api/v1/users/me"), http.get("/api/v1/nope")}) {
            assertEquals("default-src 'none'; frame-ancestors 'none'", r.header("Content-Security-Policy"));
            assertEquals("nosniff", r.header("X-Content-Type-Options"));
            assertEquals("DENY", r.header("X-Frame-Options"));
            assertEquals("no-referrer", r.header("Referrer-Policy"));
        }
    }

    @Test
    void actuatorExposesOnlyDetaillessHealth() {
        Res health = http.get("/actuator/health");
        assertEquals(200, health.status(), health.body());
        assertEquals("UP", health.json().path("status").asText());
        assertFalse(health.json().has("components"), health.body());
        assertFalse(health.json().has("details"), health.body());
        assertEquals(200, http.get("/actuator/health/liveness").status());
        for (String p : new String[]{"/actuator/env", "/actuator/info", "/actuator/beans", "/actuator/heapdump", "/actuator"}) {
            assertEquals(404, http.get(p).status(), p);
        }
    }

    @Test
    void errorMessagesDoNotLeakInternals() {
        Res r = call("GET", "/api/v1/subscriptions/me/history?limit=abc", null, newIp(), TestTokens.valid(uid()));
        assertEquals(400, r.status(), r.body());
        assertEquals("limit: geçersiz değer", r.json().path("message").asText());
        assertFalse(r.body().contains("java."), r.body());
        Res notJson = call("PUT", "/api/v1/users/me", "{bozuk", newIp(), TestTokens.valid(uid()));
        assertEquals(400, notJson.status());
        assertFalse(notJson.body().contains("Exception"), notJson.body());
    }

    // ---------------------------------------------------------------- gövde sınırı

    @Test
    void oversizedBodyIsRejectedBeforeAuthAndWhenChunked() throws Exception {
        String big = "{\"x\":\"" + "a".repeat(MAX_BODY) + "\"}";
        // Content-Length ile: kimlik denetiminden önce 413
        Res r = call("PUT", "/api/v1/users/me", big, newIp(), null);
        assertEquals(413, r.status(), r.body());
        assertEquals("too_large", r.error());
        // Uzunluksuz (chunked) gövde: herkese açık RTDN ucunda okunurken 413
        HttpRequest chunked = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/billing/rtdn"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(big.getBytes(StandardCharsets.UTF_8))))
                .build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(chunked, HttpResponse.BodyHandlers.ofString());
        assertEquals(413, res.statusCode(), res.body());
        assertTrue(res.body().contains("too_large"), res.body());
    }

    // ---------------------------------------------------------------- istek sınırları

    @Test
    void perIpLimitReturns429WithRetryAfter() {
        String ip = newIp();
        for (int i = 0; i < IP_LIMIT; i++) assertEquals(200, call("GET", "/api/v1/plans", null, ip, null).status(), "istek " + i);
        Res limited = call("GET", "/api/v1/plans", null, ip, null);
        assertEquals(429, limited.status());
        assertEquals("rate_limited", limited.error());
        assertNotNull(limited.header("Retry-After"));
        // Başka IP etkilenmez
        assertEquals(200, call("GET", "/api/v1/plans", null, newIp(), null).status());
    }

    @Test
    void perUserWriteLimit() {
        String uid = uid();
        String body = "{\"levels\":[{\"level\":0,\"bestScore\":70}]}";
        for (int i = 0; i < WRITE_LIMIT; i++) {
            assertEquals(200, call("PUT", "/api/v1/challenge/progress", body, newIp(), TestTokens.valid(uid)).status());
        }
        // IP değişse de aynı kullanıcı sınırda
        Res limited = call("PUT", "/api/v1/challenge/progress", body, newIp(), TestTokens.valid(uid));
        assertEquals(429, limited.status());
        // Okumalar yazma sınırına girmez
        assertEquals(200, call("GET", "/api/v1/challenge/progress", null, newIp(), TestTokens.valid(uid)).status());
        assertEquals(200, call("PUT", "/api/v1/challenge/progress", body, newIp(), TestTokens.valid(uid())).status());
    }

    @Test
    void perUserVerifyLimit() {
        String uid = uid();
        String body = "{\"packageName\":\"com.karapp.drawguidance\",\"productId\":\"drawguidance_premium\",\"purchaseToken\":\"yok-" + uid + "\"}";
        for (int i = 0; i < VERIFY_LIMIT; i++) {
            assertEquals(400, call("POST", "/api/v1/subscriptions/verify", body, newIp(), TestTokens.valid(uid)).status());
        }
        assertEquals(429, call("POST", "/api/v1/subscriptions/verify", body, newIp(), TestTokens.valid(uid)).status());
    }

    // ---------------------------------------------------------------- jeton iptali, e-posta

    @Test
    void tokenIssuedBeforeAccountDeletionIsRevoked() {
        String uid = uid();
        Instant now = Instant.now();
        String old = TestTokens.mint(TestTokens.KEY, uid, TestTokens.PROJECT, TestTokens.ISSUER, now.minusSeconds(30), now.plusSeconds(3600));
        assertEquals(200, call("PUT", "/api/v1/users/me", userBody(uid, "ahmet@example.com"), newIp(), old).status());
        assertEquals(204, call("DELETE", "/api/v1/users/me", null, newIp(), old).status());
        Res reused = call("PUT", "/api/v1/users/me", userBody(uid, "ahmet@example.com"), newIp(), old);
        assertEquals(401, reused.status(), reused.body());
        assertEquals("token_revoked", reused.error());
        assertEquals(401, call("PUT", "/api/v1/challenge/progress", "{\"levels\":[{\"level\":0,\"bestScore\":90}]}", newIp(), old).status());
        assertEquals(0, mongo.getCollection(Stores.USERS).countDocuments(Filters.eq("firebaseUid", uid)), "profil yeniden yazılmadı");
        assertEquals(0, mongo.getCollection(Stores.CHALLENGE_PROGRESS).countDocuments(Filters.eq("_id", uid)), "ilerleme yeniden yazılmadı");
        // Okuma ve silmenin yinelenmesi etkilenmez (veri zaten yok)
        assertEquals(404, call("GET", "/api/v1/users/me", null, newIp(), old).status());
        assertEquals(204, call("DELETE", "/api/v1/users/me", null, newIp(), old).status());
        // Kayıt uid'yi açık tutmaz (SHA-256)
        assertEquals(0, mongo.getCollection(Stores.TOKEN_REVOCATIONS).countDocuments(Filters.eq("_id", uid)));
        Document rev = mongo.getCollection(Stores.TOKEN_REVOCATIONS).find(Filters.eq("_id", TokenRevocations.key(uid))).first();
        assertNotNull(rev);
        assertNotNull(rev.getDate("expireAt"));
        // Silmeden sonra verilen yeni jeton (aynı Firebase hesabıyla yeniden giriş) kabul edilir
        String fresh = TestTokens.mint(TestTokens.KEY, uid, TestTokens.PROJECT, TestTokens.ISSUER, now.plusSeconds(5), now.plusSeconds(3600));
        assertEquals(200, call("PUT", "/api/v1/users/me", userBody(uid, "ahmet@example.com"), newIp(), fresh).status());
        // TTL dizini: kayıt expireAt anında silinir
        boolean ttl = mongo.getCollection(Stores.TOKEN_REVOCATIONS).listIndexes().into(new java.util.ArrayList<>()).stream()
                .anyMatch(i -> "expireAt_ttl".equals(i.getString("name")) && Integer.valueOf(0).equals(i.get("expireAfterSeconds")));
        assertTrue(ttl, "expireAt_ttl dizini");
    }

    @Test
    void profileEmailComesFromTokenNotBody() {
        String uid = uid();
        String victim = "kurban-" + uid + "@example.com";
        mongo.getCollection(Stores.DELETION_REQUESTS).insertOne(new Document("_id", UUID.randomUUID().toString()).append("email", victim)
                .append("status", DeletionRequestService.PENDING).append("createdAt", new java.util.Date()));
        Res put = call("PUT", "/api/v1/users/me", userBody(uid, victim), newIp(), TestTokens.valid(uid));
        assertEquals(200, put.status(), put.body());
        // Jetondaki doğrulanmış e-posta yazıldı; gövdedeki yok sayıldı
        assertEquals("ahmet@example.com", put.json().path("email").asText());
        assertTrue(put.json().path("emailVerified").asBoolean());
        assertEquals(204, call("DELETE", "/api/v1/users/me", null, newIp(), TestTokens.valid(uid)).status());
        Document req = mongo.getCollection(Stores.DELETION_REQUESTS).find(Filters.eq("email", victim)).first();
        assertEquals(DeletionRequestService.PENDING, req.getString("status"), "başkasının talebi kapatılamaz");
    }
}
