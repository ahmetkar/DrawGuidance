package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.billing.play.HttpGooglePlayApi;
import com.karapp.drawguidance.server.billing.play.PlayApiException;
import com.karapp.drawguidance.server.billing.play.PlaySubscription;
import com.karapp.drawguidance.server.billing.play.ServiceAccountCredentials;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v39 gerçek Play istemcisi ({@link HttpGooglePlayApi}) yerel bir HTTP sunucusuna karşı: hizmet hesabı JWT'si
 * (RS256, JDK ile) imzası açık anahtarla doğrulanır, erişim jetonu önbelleğe alınır, {@code subscriptionsv2.get}
 * yanıtı ayrıştırılır, {@code acknowledge} doğru adrese gider, hatalar ayrılır. Dosya yoksa "ayarlı değil".
 */
class HttpGooglePlayApiTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private KeyPair keys;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private String base;

    @BeforeEach
    void start() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        keys = g.generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/token", this::token);
        server.createContext("/androidpublisher", this::api);
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private void token(HttpExchange ex) throws IOException {
        tokenCalls.incrementAndGet();
        String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String assertion = null;
        for (String kv : form.split("&")) {
            String[] p = kv.split("=", 2);
            if (p[0].equals("assertion")) assertion = URLDecoder.decode(p[1], StandardCharsets.UTF_8);
        }
        boolean ok = form.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer") && assertion != null && verify(assertion);
        respond(ex, ok ? 200 : 400, ok ? "{\"access_token\":\"ya29.test\",\"expires_in\":3600,\"token_type\":\"Bearer\"}" : "{\"error\":\"invalid_grant\"}");
    }

    /** İmza ve alanlar: iss = hizmet hesabı, aud = token_uri, scope = androidpublisher. */
    private boolean verify(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(keys.getPublic());
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(Base64.getUrlDecoder().decode(parts[2]))) return false;
            JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            return header.get("alg").asText().equals("RS256") && header.get("kid").asText().equals("key-1")
                    && claims.get("iss").asText().equals("play@dg.iam.gserviceaccount.com")
                    && claims.get("aud").asText().equals(base + "/token")
                    && claims.get("scope").asText().equals(ServiceAccountCredentials.SCOPE)
                    && claims.get("exp").asLong() - claims.get("iat").asLong() == 3600;
        } catch (Exception e) {
            return false;
        }
    }

    private void api(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getRawPath();
        requests.add(ex.getRequestMethod() + " " + path);
        if (!"Bearer ya29.test".equals(ex.getRequestHeaders().getFirst("Authorization"))) {
            respond(ex, 401, "{\"error\":{\"message\":\"yetkisiz\"}}");
            return;
        }
        if (path.endsWith("/tokens/good") && ex.getRequestMethod().equals("GET")) {
            respond(ex, 200, """
                    {"kind":"androidpublisher#subscriptionPurchaseV2","regionCode":"TR","startTime":"2026-10-01T10:00:00.123Z",
                     "subscriptionState":"SUBSCRIPTION_STATE_ACTIVE","latestOrderId":"GPA.1-old",
                     "acknowledgementState":"ACKNOWLEDGEMENT_STATE_PENDING","linkedPurchaseToken":"older",
                     "externalAccountIdentifiers":{"obfuscatedExternalAccountId":"abc"},
                     "lineItems":[
                       {"productId":"drawguidance_premium","expiryTime":"2026-10-15T10:00:00Z","latestSuccessfulOrderId":"GPA.1..0",
                        "offerDetails":{"basePlanId":"old-plan"}},
                       {"productId":"drawguidance_premium","expiryTime":"2026-11-01T10:00:00Z","latestSuccessfulOrderId":"GPA.1..1",
                        "autoRenewingPlan":{"autoRenewEnabled":true},"offerDetails":{"basePlanId":"premium-monthly","offerId":"intro"}}]}
                    """);
        } else if (path.endsWith(":acknowledge") && ex.getRequestMethod().equals("POST")) {
            respond(ex, 204, "");
        } else if (path.endsWith("/tokens/down")) {
            respond(ex, 503, "{\"error\":{\"message\":\"backend\"}}");
        } else {
            respond(ex, 404, "{\"error\":{\"message\":\"not found\"}}");
        }
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, status == 204 ? -1 : b.length);
        if (status != 204) ex.getResponseBody().write(b);
        ex.close();
    }

    private Path serviceAccountFile() throws IOException {
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        ObjectNode n = json.createObjectNode().put("type", "service_account").put("client_email", "play@dg.iam.gserviceaccount.com")
                .put("private_key_id", "key-1").put("private_key", pem).put("token_uri", base + "/token");
        Path file = Files.createTempFile("dg-sa", ".json");
        json.writeValue(file.toFile(), n);
        return file;
    }

    @Test
    void callsPlayWithServiceAccountToken() throws Exception {
        Path file = serviceAccountFile();
        try {
            HttpGooglePlayApi api = HttpGooglePlayApi.fromProperties(
                    new DrawGuidanceProperties.Billing(null, file.toString(), base, null, null, null), json);
            assertTrue(api.configured());

            PlaySubscription s = api.getSubscription("com.karapp.drawguidance", "good");
            assertEquals(SubscriptionStatus.ACTIVE, s.status());
            assertEquals("drawguidance_premium", s.productId());
            assertEquals("premium-monthly", s.basePlanId(), "bitişi en geç olan kalem");
            assertEquals("intro", s.offerId());
            assertEquals("GPA.1..1", s.latestOrderId());
            assertEquals(Instant.parse("2026-11-01T10:00:00Z"), s.expiryTime());
            assertEquals(Instant.parse("2026-10-01T10:00:00.123Z"), s.startTime());
            assertTrue(s.autoRenewing());
            assertFalse(s.acknowledged());
            assertTrue(s.needsAcknowledge());
            assertEquals("older", s.linkedPurchaseToken());
            assertEquals("abc", s.obfuscatedAccountId());
            assertEquals("TR", s.regionCode());

            api.acknowledge("com.karapp.drawguidance", "drawguidance_premium", "good");
            assertTrue(requests.contains("POST /androidpublisher/v3/applications/com.karapp.drawguidance/purchases/subscriptions/drawguidance_premium/tokens/good:acknowledge"));
            assertTrue(requests.contains("GET /androidpublisher/v3/applications/com.karapp.drawguidance/purchases/subscriptionsv2/tokens/good"));
            assertEquals(1, tokenCalls.get(), "erişim jetonu yeniden kullanılır");

            PlayApiException missing = assertThrows(PlayApiException.class, () -> api.getSubscription("com.karapp.drawguidance", "nope"));
            assertTrue(missing.invalidToken());
            PlayApiException down = assertThrows(PlayApiException.class, () -> api.getSubscription("com.karapp.drawguidance", "down"));
            assertFalse(down.invalidToken());
            assertEquals(503, down.status());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void notConfiguredWithoutOrWithBrokenFile() throws Exception {
        HttpGooglePlayApi none = HttpGooglePlayApi.fromProperties(new DrawGuidanceProperties.Billing(null, "", base, null, null, null), json);
        assertFalse(none.configured());
        assertEquals(503, assertThrows(PlayApiException.class, () -> none.getSubscription("p", "good")).status());
        assertEquals(0, tokenCalls.get());
        assertTrue(requests.isEmpty(), "ayarsızken Play'e istek gitmez");

        HttpGooglePlayApi missing = HttpGooglePlayApi.fromProperties(
                new DrawGuidanceProperties.Billing(null, "/yok/boyle/bir/dosya.json", base, null, null, null), json);
        assertFalse(missing.configured());

        Path broken = Files.createTempFile("dg-sa", ".json");
        try {
            Files.writeString(broken, "{\"client_email\":\"x@y\",\"private_key\":\"bozuk\"}");
            assertFalse(HttpGooglePlayApi.fromProperties(new DrawGuidanceProperties.Billing(null, broken.toString(), base, null, null, null), json).configured());
        } finally {
            Files.deleteIfExists(broken);
        }
        DrawGuidanceProperties.Billing defaults = new DrawGuidanceProperties.Billing(null, null, null, null, null, null);
        assertEquals("com.karapp.drawguidance", defaults.packageName());
        assertNull(defaults.serviceAccountFile());
        assertEquals("drawguidance_premium", defaults.premium().productId());
    }

    @Test
    void statusMappingAndTransactionDerivation() {
        assertEquals(SubscriptionStatus.EXPIRED, SubscriptionStatus.fromPlay("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED"));
        assertEquals(SubscriptionStatus.PENDING, SubscriptionStatus.fromPlay("SUBSCRIPTION_STATE_UNSPECIFIED"));
        assertEquals(SubscriptionStatus.ON_HOLD, SubscriptionStatus.fromPlay("SUBSCRIPTION_STATE_ON_HOLD"));
        assertEquals(TransactionType.RENEWAL, TransactionType.fromNotification(2));
        assertEquals(TransactionType.REVOKE, TransactionType.fromNotification(12));
        assertEquals(TransactionType.UPDATE, TransactionType.fromNotification(99));
        assertEquals(TransactionType.PURCHASE, TransactionType.derive(null, null, SubscriptionStatus.ACTIVE, "a"));
        assertEquals(TransactionType.RENEWAL, TransactionType.derive(SubscriptionStatus.ACTIVE, "a", SubscriptionStatus.ACTIVE, "a..0"));
        assertEquals(TransactionType.RECOVER, TransactionType.derive(SubscriptionStatus.ON_HOLD, "a", SubscriptionStatus.ACTIVE, "a..0"));
        assertEquals(TransactionType.RESTART, TransactionType.derive(SubscriptionStatus.CANCELED, "a", SubscriptionStatus.ACTIVE, "a"));
        assertEquals(TransactionType.CANCEL, TransactionType.derive(SubscriptionStatus.ACTIVE, "a", SubscriptionStatus.CANCELED, "a"));
        assertNull(TransactionType.derive(SubscriptionStatus.ACTIVE, "a", SubscriptionStatus.ACTIVE, "a"));
        Instant now = Instant.now();
        assertTrue(SubscriptionService.entitled(SubscriptionStatus.IN_GRACE_PERIOD, now.minusSeconds(3600), now));
        assertFalse(SubscriptionService.entitled(SubscriptionStatus.IN_GRACE_PERIOD, now.minus(java.time.Duration.ofDays(31)), now));
        assertFalse(SubscriptionService.entitled(SubscriptionStatus.CANCELED, now.minusSeconds(1), now));
        assertFalse(SubscriptionService.entitled(SubscriptionStatus.PAUSED, now.plusSeconds(3600), now));
    }
}
