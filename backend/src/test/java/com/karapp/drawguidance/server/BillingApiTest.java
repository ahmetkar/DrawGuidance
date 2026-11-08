package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.billing.PlanSeeder;
import com.karapp.drawguidance.server.billing.Plans;
import com.karapp.drawguidance.server.billing.SubscriptionService;
import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.support.FakeGooglePlayApi;
import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v39 planlar, abonelik doğrulaması ve Google Play gerçek zamanlı bildirimleri (sahte Play API ile). */
class BillingApiTest extends ApiContextTest {
    static final String PKG = "com.karapp.drawguidance", PRODUCT = "drawguidance_premium", BASE = "premium-monthly";
    static final String ME = "/api/v1/subscriptions/me", VERIFY = "/api/v1/subscriptions/verify", RTDN = "/api/v1/billing/rtdn";

    @Autowired
    MongoTemplate mongo;
    @Autowired
    FakeGooglePlayApi play;
    @Autowired
    PlanSeeder seeder;

    private final ObjectMapper json = new ObjectMapper();

    static String newUid() { return "uid" + UUID.randomUUID().toString().replace("-", "").substring(0, 25); }

    static String newToken() { return "tok-" + UUID.randomUUID(); }

    static String hash(String uid) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8)));
    }

    static ObjectNode active(String uid, String order, boolean ack) throws Exception {
        Instant now = Instant.now();
        return FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_ACTIVE", PRODUCT, BASE, order, now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(29)), true, ack, uid == null ? null : hash(uid), null);
    }

    Res me(String uid) { return http.call("GET", ME, null, "Authorization", "Bearer " + TestTokens.valid(uid)); }

    Res verify(String uid, String token) { return verify(uid, PKG, PRODUCT, token); }

    Res verify(String uid, String pkg, String product, String token) {
        String body = "{\"packageName\":\"" + pkg + "\",\"productId\":\"" + product + "\",\"purchaseToken\":\"" + token + "\"}";
        return http.call("POST", VERIFY, body, "Authorization", "Bearer " + TestTokens.valid(uid));
    }

    Res rtdn(String messageId, String notificationJson) {
        String data = Base64.getEncoder().encodeToString(notificationJson.getBytes(StandardCharsets.UTF_8));
        String body = "{\"message\":{\"data\":\"" + data + "\",\"messageId\":\"" + messageId + "\",\"publishTime\":\"2026-10-20T10:00:00Z\","
                + "\"attributes\":{}},\"subscription\":\"projects/dg/subscriptions/play-rtdn\"}";
        return http.call("POST", RTDN, body);
    }

    Res notify(String messageId, int type, String token) {
        return rtdn(messageId, "{\"version\":\"1.0\",\"packageName\":\"" + PKG + "\",\"eventTimeMillis\":\"1760954400000\","
                + "\"subscriptionNotification\":{\"version\":\"1.0\",\"notificationType\":" + type + ",\"purchaseToken\":\"" + token
                + "\",\"subscriptionId\":\"" + PRODUCT + "\"}}");
    }

    Document sub(String uid) { return mongo.getCollection(Stores.SUBSCRIPTIONS).find(Filters.eq("_id", uid)).first(); }

    Document event(String messageId) { return mongo.getCollection(Stores.BILLING_WEBHOOK_EVENTS).find(Filters.eq("_id", messageId)).first(); }

    List<Document> txs(String token) {
        return mongo.getCollection(Stores.SUBSCRIPTION_TRANSACTIONS).find(Filters.eq("purchaseToken", token))
                .sort(new Document("at", 1)).into(new ArrayList<>());
    }

    List<String> types(String token) { return txs(token).stream().map(d -> d.getString("type")).toList(); }

    static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    // ------------------------------------------------------------------ planlar

    @Test
    void plansAreSeededIdempotentlyAndListed() {
        Res r = http.get("/api/v1/plans");
        assertEquals(200, r.status(), r.body());
        JsonNode plans = r.json().get("plans");
        assertEquals(2, plans.size());
        JsonNode free = plans.get(0), premium = plans.get(1);
        assertEquals(Plans.FREE, free.get("id").asText());
        assertEquals("Ücretsiz", free.get("name").asText());
        assertTrue(free.get("productId").isNull());
        assertEquals(0, free.get("features").size());
        assertEquals(Plans.PREMIUM, premium.get("id").asText());
        assertEquals(PRODUCT, premium.get("productId").asText());
        assertEquals(BASE, premium.get("basePlanId").asText());
        assertEquals(100_000_000L, premium.get("price").get("amountMicros").asLong());
        assertEquals("TRY", premium.get("price").get("currency").asText());
        assertEquals("100 TL", premium.get("price").get("display").asText());
        assertEquals("P1M", premium.get("period").asText());
        assertEquals(Plans.FEATURE_KEYS, texts(premium.get("features")));
        assertEquals(21, premium.get("features").size());
        assertTrue(premium.get("features").toString().contains("ads.none"));
        // v40 çocuk modu çizim görevinin son 15 seviyesi
        assertTrue(premium.get("features").toString().contains("kids.challenge_advanced"));
        assertEquals(Plans.FEATURE_KEYS, texts(r.json().get("featureKeys")));

        // ETag: aynı içerik → 304
        assertNotNull(r.etag());
        assertEquals(304, http.get("/api/v1/plans", "If-None-Match", r.etag()).status());

        // Tohumlama tekrar çalıştırılabilir: sayılar değişmez, listede olmayan özellik silinir
        mongo.getCollection(Stores.PLAN_FEATURES).insertOne(new Document("_id", "premium:old.feature").append("planId", "premium")
                .append("feature", "old.feature").append("enabled", true));
        seeder.seed();
        seeder.seed();
        assertEquals(2, mongo.getCollection(Stores.PLANS).countDocuments());
        assertEquals(21, mongo.getCollection(Stores.PLAN_FEATURES).countDocuments(Filters.eq("planId", "premium")));
        assertEquals(0, mongo.getCollection(Stores.PLAN_FEATURES).countDocuments(Filters.eq("planId", "free")));
        assertEquals(0, mongo.getCollection(Stores.PLAN_FEATURES).countDocuments(Filters.eq("feature", "old.feature")));
        assertEquals(r.etag(), http.get("/api/v1/plans").etag());
    }

    // ------------------------------------------------------------------ /me

    @Test
    void meDefaultsToFreeAndRequiresToken() throws Exception {
        String uid = newUid();
        Res r = me(uid);
        assertEquals(200, r.status(), r.body());
        JsonNode b = r.json();
        assertEquals("free", b.get("planId").asText());
        assertTrue(b.get("status").isNull());
        assertFalse(b.get("active").asBoolean());
        assertEquals(0, b.get("features").size());
        assertEquals(hash(uid), b.get("obfuscatedAccountId").asText());
        assertEquals(SubscriptionService.accountHash(uid), b.get("obfuscatedAccountId").asText());
        assertEquals(64, b.get("obfuscatedAccountId").asText().length());

        assertEquals(401, http.get(ME).status());
        assertEquals("unauthorized", http.get(ME).error());
        assertEquals(401, http.call("POST", VERIFY, "{}").status());
        assertEquals(401, http.get(ME, "Authorization", "Bearer bozuk").status());
        assertEquals(401, http.get(ME + "/history").status());
    }

    // ------------------------------------------------------------------ doğrulama

    @Test
    void verifyHappyPathAcknowledgesStoresAndRecordsPurchase() throws Exception {
        String uid = newUid(), token = newToken();
        play.subscriptions.put(token, active(uid, "GPA.1111-2222-3333-44444", false));

        Res r = verify(uid, token);
        assertEquals(200, r.status(), r.body());
        JsonNode b = r.json();
        assertEquals("premium", b.get("planId").asText());
        assertEquals("ACTIVE", b.get("status").asText());
        assertTrue(b.get("active").asBoolean());
        assertEquals(PRODUCT, b.get("productId").asText());
        assertEquals(BASE, b.get("basePlanId").asText());
        assertTrue(b.get("autoRenewing").asBoolean());
        assertTrue(b.get("acknowledged").asBoolean());
        assertEquals("GPA.1111-2222-3333-44444", b.get("orderId").asText());
        assertEquals(Plans.FEATURE_KEYS, texts(b.get("features")));
        assertTrue(Instant.parse(b.get("expiryTime").asText()).isAfter(Instant.now()));
        assertTrue(play.acknowledged.contains(token), "onaylanmamış satın alma onaylanır");

        Document s = sub(uid);
        assertEquals(token, s.getString("purchaseToken"));
        assertEquals("premium", s.getString("planId"));
        assertEquals("ACTIVE", s.getString("status"));
        assertEquals("VERIFY", s.getString("source"));
        assertEquals(true, s.getBoolean("acknowledged"));
        assertEquals(List.of("PURCHASE"), types(token));
        Document tx = txs(token).get(0);
        assertEquals(uid, tx.getString("userId"));
        assertEquals("GPA.1111-2222-3333-44444", tx.getString("orderId"));
        assertEquals("SUBSCRIPTION_STATE_ACTIVE", tx.get("snapshot", Document.class).getString("subscriptionState"));

        // Aynı durumu yeniden doğrulamak hareket eklemez, onay tekrar istenmez
        Res again = verify(uid, token);
        assertEquals(200, again.status());
        assertEquals(List.of("PURCHASE"), types(token));
        assertEquals(1, play.acknowledged.stream().filter(token::equals).count());
        assertEquals(again.json(), me(uid).json());
    }

    @Test
    void verifyRejectsAnotherAccountsPurchase() throws Exception {
        String owner = newUid(), other = newUid(), token = newToken();
        play.subscriptions.put(token, active(owner, "GPA.2000-0000-0000-00001", true));
        Res r = verify(other, token);
        assertEquals(403, r.status(), r.body());
        assertEquals("account_mismatch", r.error());
        assertNull(sub(other));
        assertTrue(txs(token).isEmpty());

        // Hesap kimliği olmayan (uygulama dışı) satın alma ilk doğrulayana bağlanır, ikincisi 409
        String loose = newToken();
        play.subscriptions.put(loose, active(null, "GPA.2000-0000-0000-00002", true));
        assertEquals(200, verify(owner, loose).status());
        Res taken = verify(other, loose);
        assertEquals(409, taken.status(), taken.body());
        assertEquals("purchase_token_in_use", taken.error());
    }

    @Test
    void verifyWithoutPlayCredentialsIs503() {
        String uid = newUid(), token = newToken();
        play.subscriptions.put(token, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_ACTIVE", PRODUCT, BASE, "GPA.3", Instant.now(),
                Instant.now().plus(Duration.ofDays(30)), true, true, SubscriptionService.accountHash(uid), null));
        play.configured = false;
        try {
            Res r = verify(uid, token);
            assertEquals(503, r.status(), r.body());
            assertEquals("billing_not_configured", r.error());
            assertNull(sub(uid), "doğrulanamayan satın alma asla kaydedilmez");
            assertEquals("free", me(uid).json().get("planId").asText());
        } finally {
            play.configured = true;
        }
    }

    @Test
    void verifyExpiredSubscriptionGivesFree() throws Exception {
        String uid = newUid(), token = newToken();
        Instant now = Instant.now();
        play.subscriptions.put(token, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_EXPIRED", PRODUCT, BASE, "GPA.4000-0000-0000-00001",
                now.minus(Duration.ofDays(60)), now.minus(Duration.ofDays(30)), false, true, hash(uid), null));
        Res r = verify(uid, token);
        assertEquals(200, r.status(), r.body());
        assertEquals("free", r.json().get("planId").asText());
        assertEquals("EXPIRED", r.json().get("status").asText());
        assertFalse(r.json().get("active").asBoolean());
        assertEquals(0, r.json().get("features").size());

        // Bildirim kaçsa da süresi geçmiş ACTIVE kayıt hak vermez
        String uid2 = newUid(), token2 = newToken();
        play.subscriptions.put(token2, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_ACTIVE", PRODUCT, BASE, "GPA.4000-0000-0000-00002",
                now.minus(Duration.ofDays(40)), now.minus(Duration.ofMinutes(1)), true, true, hash(uid2), null));
        Res r2 = verify(uid2, token2);
        assertEquals("free", r2.json().get("planId").asText());
        assertEquals("EXPIRED", r2.json().get("status").asText());
        assertEquals("ACTIVE", sub(uid2).getString("status"));
    }

    @Test
    void verifyValidatesRequest() throws Exception {
        String uid = newUid(), token = newToken();
        play.subscriptions.put(token, active(uid, "GPA.5000-0000-0000-00001", true));
        assertEquals("invalid_purchase_token", verify(uid, newToken()).error());
        assertEquals(400, verify(uid, newToken()).status());
        assertEquals("package_mismatch", verify(uid, "com.other.app", PRODUCT, token).error());
        assertEquals("product_mismatch", verify(uid, PKG, "other_product", token).error());
        Res blank = http.call("POST", VERIFY, "{\"packageName\":\"" + PKG + "\"}", "Authorization", "Bearer " + TestTokens.valid(uid));
        assertEquals(400, blank.status());
        assertEquals("validation", blank.error());

        String unknown = newToken();
        play.subscriptions.put(unknown, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_ACTIVE", "coins_pack", "x", "GPA.5", Instant.now(),
                Instant.now().plus(Duration.ofDays(3)), true, true, hash(uid), null));
        Res r = verify(uid, PKG, "coins_pack", unknown);
        assertEquals(400, r.status());
        assertEquals("unknown_product", r.error());

        play.failing.add(token);
        try {
            Res down = verify(uid, token);
            assertEquals(502, down.status());
            assertEquals("play_unavailable", down.error());
        } finally {
            play.failing.remove(token);
        }
        assertNull(sub(uid));
    }

    // ------------------------------------------------------------------ gerçek zamanlı bildirimler

    @Test
    void rtdnRenewalCancelExpireWithDedupeAndHistory() throws Exception {
        String uid = newUid(), token = newToken();
        Instant now = Instant.now(), start = now.minus(Duration.ofDays(30));
        play.subscriptions.put(token, active(uid, "GPA.6000-0000-0000-00001", true));
        assertEquals(200, verify(uid, token).status());

        // Yenileme: Play'deki durum değişti, bildirim yalnız jetonu söyler
        play.subscriptions.put(token, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_ACTIVE", PRODUCT, BASE, "GPA.6000-0000-0000-00001..0",
                start, now.plus(Duration.ofDays(60)), true, true, hash(uid), null));
        String renewId = "msg-" + UUID.randomUUID();
        Res renewed = notify(renewId, 2, token);
        assertEquals(200, renewed.status(), renewed.body());
        assertEquals("processed", renewed.json().get("status").asText());
        assertEquals("GPA.6000-0000-0000-00001..0", sub(uid).getString("orderId"));
        assertEquals("RTDN", sub(uid).getString("source"));
        assertEquals(List.of("PURCHASE", "RENEWAL"), types(token));
        Document ev = event(renewId);
        assertEquals("PROCESSED", ev.getString("status"));
        assertEquals("SUBSCRIPTION_RENEWED", ev.getString("notificationName"));
        assertEquals(2, ev.getInteger("notificationType"));
        assertEquals(token, ev.getString("purchaseToken"));
        assertEquals(1, ev.getInteger("attempts"));
        assertNotNull(ev.get("processedAt"));

        // Aynı ileti yeniden gelir: 2xx, ikinci kez işlenmez
        Res dup = notify(renewId, 2, token);
        assertEquals(200, dup.status());
        assertEquals("duplicate", dup.json().get("status").asText());
        assertEquals(List.of("PURCHASE", "RENEWAL"), types(token));
        assertEquals(1, event(renewId).getInteger("attempts"));
        assertEquals(1, event(renewId).getInteger("duplicates"));

        // Başka iletiyle aynı yenileme (aynı orderId + tür) de bir kez yazılır
        assertEquals(200, notify("msg-" + UUID.randomUUID(), 2, token).status());
        assertEquals(List.of("PURCHASE", "RENEWAL"), types(token));

        // İptal: dönem sonuna kadar hak sürer
        com.fasterxml.jackson.databind.node.ObjectNode canceledNode = FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_CANCELED", PRODUCT, BASE,
                "GPA.6000-0000-0000-00001..0", start, now.plus(Duration.ofDays(60)), false, true, hash(uid), null);
        // v40 Play'in iptal bilgisi (kullanıcı iptali ve zamanı) saklanır ve gösterilir
        Instant cancelAt = now.minus(Duration.ofMinutes(3)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        canceledNode.putObject("canceledStateContext").putObject("userInitiatedCancellation").put("cancelTime", cancelAt.toString());
        play.subscriptions.put(token, canceledNode);
        assertEquals(200, notify("msg-" + UUID.randomUUID(), 3, token).status());
        JsonNode canceled = me(uid).json();
        assertEquals(cancelAt, Instant.parse(canceled.get("cancelTime").asText()));
        assertEquals("USER", canceled.get("cancelReason").asText());
        assertTrue(canceled.get("autoResumeTime").isNull());
        assertEquals("CANCELED", canceled.get("status").asText());
        assertTrue(canceled.get("active").asBoolean());
        assertEquals("premium", canceled.get("planId").asText());
        assertFalse(canceled.get("autoRenewing").asBoolean());

        // Süre doldu
        play.subscriptions.put(token, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_EXPIRED", PRODUCT, BASE, "GPA.6000-0000-0000-00001..0",
                start, now.minus(Duration.ofMinutes(5)), false, true, hash(uid), null));
        assertEquals(200, notify("msg-" + UUID.randomUUID(), 13, token).status());
        JsonNode expired = me(uid).json();
        assertEquals("EXPIRED", expired.get("status").asText());
        assertEquals("free", expired.get("planId").asText());
        assertFalse(expired.get("active").asBoolean());
        assertEquals(List.of("PURCHASE", "RENEWAL", "CANCEL", "EXPIRE"), types(token));

        // Geçmiş ucu: yeniden eskiye, sipariş numaralarıyla
        Res history = http.call("GET", ME + "/history", null, "Authorization", "Bearer " + TestTokens.valid(uid));
        assertEquals(200, history.status(), history.body());
        JsonNode items = history.json().get("items");
        assertEquals(4, items.size());
        assertEquals("EXPIRE", items.get(0).get("type").asText());
        assertEquals("PURCHASE", items.get(3).get("type").asText());
        assertEquals("GPA.6000-0000-0000-00001", items.get(3).get("orderId").asText());
        assertEquals("RTDN", items.get(0).get("source").asText());
        assertNull(items.get(0).get("snapshot"));
        // Başka kullanıcı bu geçmişi görmez
        assertEquals(0, http.call("GET", ME + "/history", null, "Authorization", "Bearer " + TestTokens.valid(newUid())).json().get("items").size());
    }

    @Test
    void rtdnGracePeriodOnHoldRecoverAndRevoke() throws Exception {
        String uid = newUid(), token = newToken();
        Instant now = Instant.now(), start = now.minus(Duration.ofDays(31));
        play.subscriptions.put(token, active(uid, "GPA.7000-0000-0000-00001", true));
        assertEquals(200, verify(uid, token).status());

        play.subscriptions.put(token, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", PRODUCT, BASE, "GPA.7000-0000-0000-00001",
                start, now.plus(Duration.ofDays(3)), true, true, hash(uid), null));
        assertEquals(200, notify("msg-" + UUID.randomUUID(), 6, token).status());
        JsonNode grace = me(uid).json();
        assertEquals("IN_GRACE_PERIOD", grace.get("status").asText());
        assertTrue(grace.get("active").asBoolean(), "ek sürede hak sürer");

        play.subscriptions.put(token, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_ON_HOLD", PRODUCT, BASE, "GPA.7000-0000-0000-00001",
                start, now.minus(Duration.ofDays(1)), true, true, hash(uid), null));
        assertEquals(200, notify("msg-" + UUID.randomUUID(), 5, token).status());
        JsonNode hold = me(uid).json();
        assertEquals("ON_HOLD", hold.get("status").asText());
        assertFalse(hold.get("active").asBoolean());
        assertEquals("free", hold.get("planId").asText());

        play.subscriptions.put(token, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_ACTIVE", PRODUCT, BASE, "GPA.7000-0000-0000-00001..0",
                start, now.plus(Duration.ofDays(30)), true, true, hash(uid), null));
        assertEquals(200, notify("msg-" + UUID.randomUUID(), 1, token).status());
        assertTrue(me(uid).json().get("active").asBoolean());

        play.subscriptions.put(token, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_EXPIRED", PRODUCT, BASE, "GPA.7000-0000-0000-00001..0",
                start, now.minus(Duration.ofSeconds(1)), false, true, hash(uid), null));
        assertEquals(200, notify("msg-" + UUID.randomUUID(), 12, token).status());
        JsonNode revoked = me(uid).json();
        assertEquals("EXPIRED", revoked.get("status").asText());
        assertFalse(revoked.get("active").asBoolean());
        assertEquals(List.of("PURCHASE", "GRACE_PERIOD", "ON_HOLD", "RECOVER", "REVOKE"), types(token));
    }

    @Test
    void rtdnRetriesWhenNotConfiguredOrPlayDown() throws Exception {
        String uid = newUid(), token = newToken();
        play.subscriptions.put(token, active(uid, "GPA.8000-0000-0000-00001", true));
        assertEquals(200, verify(uid, token).status());
        play.subscriptions.put(token, FakeGooglePlayApi.sub("SUBSCRIPTION_STATE_CANCELED", PRODUCT, BASE, "GPA.8000-0000-0000-00001",
                Instant.now(), Instant.now().plus(Duration.ofDays(20)), false, true, hash(uid), null));
        String id = "msg-" + UUID.randomUUID();

        play.configured = false;
        try {
            Res r = notify(id, 3, token);
            assertEquals(503, r.status(), r.body());
            assertEquals("billing_not_configured", r.error());
        } finally {
            play.configured = true;
        }
        assertEquals("FAILED", event(id).getString("status"));
        assertTrue(event(id).getString("error").startsWith("billing_not_configured"));
        assertEquals("ACTIVE", sub(uid).getString("status"));

        play.failing.add(token);
        try {
            assertEquals(503, notify(id, 3, token).status());
        } finally {
            play.failing.remove(token);
        }
        assertEquals(2, event(id).getInteger("attempts"));

        Res ok = notify(id, 3, token);
        assertEquals(200, ok.status(), ok.body());
        assertEquals("processed", ok.json().get("status").asText());
        assertEquals("PROCESSED", event(id).getString("status"));
        assertEquals(3, event(id).getInteger("attempts"));
        assertEquals("CANCELED", sub(uid).getString("status"));
    }

    @Test
    void rtdnForUnknownTokenIsLinkedOnVerify() throws Exception {
        String uid = newUid(), token = newToken();
        play.subscriptions.put(token, active(uid, "GPA.9000-0000-0000-00001", false));
        Res r = notify("msg-" + UUID.randomUUID(), 4, token);
        assertEquals(200, r.status(), r.body());
        assertEquals("unknown_user", r.json().get("result").asText());
        assertEquals(1, txs(token).size());
        assertNull(txs(token).get(0).getString("userId"));
        assertFalse(play.acknowledged.contains(token), "kullanıcısı bilinmeyen satın alma onaylanmaz");

        assertEquals(200, verify(uid, token).status());
        assertEquals(List.of("PURCHASE"), types(token), "aynı sipariş bir kez yazılır");
        assertEquals(uid, txs(token).get(0).getString("userId"));
        assertTrue(play.acknowledged.contains(token));
    }

    @Test
    void rtdnTestMalformedAndForeignMessages() {
        String id = "msg-" + UUID.randomUUID();
        Res test = rtdn(id, "{\"version\":\"1.0\",\"packageName\":\"" + PKG + "\",\"eventTimeMillis\":\"1\",\"testNotification\":{\"version\":\"1.0\"}}");
        assertEquals(200, test.status(), test.body());
        assertEquals("ignored", test.json().get("status").asText());
        assertEquals("IGNORED", event(id).getString("status"));
        assertEquals("TEST", event(id).getString("notificationName"));

        String foreign = "msg-" + UUID.randomUUID();
        Res other = rtdn(foreign, "{\"packageName\":\"com.other\",\"subscriptionNotification\":{\"notificationType\":4,\"purchaseToken\":\"x\"}}");
        assertEquals(200, other.status());
        assertEquals("package_mismatch", other.json().get("result").asText());

        Res oneTime = rtdn("msg-" + UUID.randomUUID(), "{\"packageName\":\"" + PKG + "\",\"voidedPurchaseNotification\":{\"purchaseToken\":\"x\",\"orderId\":\"GPA.1\",\"productType\":2}}");
        assertEquals(200, oneTime.status());
        assertEquals("not_subscription", oneTime.json().get("result").asText());

        Res invalid = notify("msg-" + UUID.randomUUID(), 4, "never-seen-" + UUID.randomUUID());
        assertEquals(200, invalid.status());
        assertEquals("invalid_purchase_token", invalid.json().get("result").asText());

        assertEquals(400, http.call("POST", RTDN, "{\"message\":{}}").status());
        assertEquals(400, http.call("POST", RTDN, "[]").status());
        assertEquals(400, http.call("POST", RTDN, "bozuk").status());
        assertEquals(404, http.get(RTDN).status());
    }

    @Test
    void indexesExist() {
        assertTrue(indexNames(Stores.SUBSCRIPTIONS).contains("purchaseToken_1"));
        assertTrue(indexNames(Stores.SUBSCRIPTION_TRANSACTIONS).contains("orderId_1_type_1"));
        assertTrue(indexNames(Stores.BILLING_WEBHOOK_EVENTS).contains("receivedAt_ttl"));
        assertTrue(indexNames(Stores.PLAN_FEATURES).contains("planId_1_feature_1"));
        assertTrue(indexNames(Stores.CHALLENGE_PROGRESS).contains("firebaseUid_1"));
    }

    List<String> indexNames(String coll) {
        return mongo.getCollection(coll).listIndexes().into(new ArrayList<>()).stream().map(d -> d.getString("name")).toList();
    }
}
