package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.billing.Plans;
import com.karapp.drawguidance.server.billing.apple.AppStoreApi.AppleNotification;
import com.karapp.drawguidance.server.billing.apple.AppStoreApi.AppleRenewal;
import com.karapp.drawguidance.server.billing.apple.AppStoreApi.AppleStatus;
import com.karapp.drawguidance.server.billing.apple.AppStoreApi.AppleTransaction;
import com.karapp.drawguidance.server.billing.apple.AppleStates;
import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.support.FakeAppStoreApi;
import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v50 App Store abonelikleri: doğrulama, hesap bağlama, Server Notifications V2 ve {@code /me} tazelemesi (sahte App Store ile). */
class AppleBillingApiTest extends ApiContextTest {
    static final String PRODUCT = "com.karapp.drawguidance.premium.monthly", BUNDLE = "com.karapp.drawguidance";
    static final String ME = "/api/v1/subscriptions/me", VERIFY = "/api/v1/subscriptions/verify-apple",
            NOTIFY = "/api/v1/billing/apple-notifications";

    @Autowired
    MongoTemplate mongo;
    @Autowired
    FakeAppStoreApi apple;

    static String newUid() { return "uid" + UUID.randomUUID().toString().replace("-", "").substring(0, 25); }

    static String newOtid() { return String.valueOf(2_000_000_000_000_000L + (long) (Math.random() * 1e12)); }

    static AppleTransaction tx(String otid, String txId, String product, UUID account, Instant expires) {
        Instant now = Instant.now();
        return new AppleTransaction(txId, otid, BUNDLE, product, "21500000", account, now.minus(Duration.ofDays(1)),
                now.minus(Duration.ofDays(1)), expires, null, null, false, true, "TUR", "Auto-Renewable Subscription", "PURCHASE");
    }

    static AppleStatus status(int code, AppleTransaction t, Boolean autoRenew) {
        return new AppleStatus(code, t, new AppleRenewal(t.productId(), autoRenew, null, false, null, t.expiresDate()));
    }

    /** İmzalı işlem (sahtede anahtar) ve App Store'daki etkin durumu hazırlar. */
    String purchase(String otid, String txId, UUID account, boolean autoRenew) {
        AppleTransaction t = tx(otid, txId, PRODUCT, account, Instant.now().plus(Duration.ofDays(29)));
        String jws = "jws-" + UUID.randomUUID();
        apple.signed.put(jws, t);
        apple.statuses.put(otid, status(1, t, autoRenew));
        return jws;
    }

    Res me(String uid) { return http.call("GET", ME, null, "Authorization", "Bearer " + TestTokens.valid(uid)); }

    Res verify(String uid, String jws) {
        return http.call("POST", VERIFY, "{\"signedTransaction\":\"" + jws + "\"}", "Authorization", "Bearer " + TestTokens.valid(uid));
    }

    Res notify(String payloadKey) { return http.call("POST", NOTIFY, "{\"signedPayload\":\"" + payloadKey + "\"}"); }

    String notification(String type, String subtype, AppleStatus s) {
        String key = "n-" + UUID.randomUUID();
        apple.notifications.put(key, new AppleNotification(UUID.randomUUID().toString(), type, subtype, true, Instant.now(), s));
        return key;
    }

    Document sub(String uid) { return mongo.getCollection(Stores.SUBSCRIPTIONS).find(Filters.eq("_id", uid)).first(); }

    List<Document> txs(String token) {
        return mongo.getCollection(Stores.SUBSCRIPTION_TRANSACTIONS).find(Filters.eq("purchaseToken", token))
                .sort(new Document("at", 1)).into(new ArrayList<>());
    }

    // ------------------------------------------------------------------ hesap jetonu

    @Test
    void appAccountTokenMatchesTheIosFormula() {
        // iOS AppAccountToken.forUid ile aynı test vektörü (SHA-256 ilk 16 bayt, sürüm 8, RFC varyantı)
        assertEquals(UUID.fromString("a8c418aa-055f-8fdb-9801-967606c84f12"), AppleStates.appAccountToken("test-uid-123"));
        Res r = me(newUid());
        assertEquals(200, r.status(), r.body());
        assertTrue(r.json().get("appAccountToken").asText().matches("[0-9a-f-]{36}"));
    }

    // ------------------------------------------------------------------ doğrulama

    @Test
    void verifiedPurchaseGrantsPremiumAndIsVisibleFromMe() {
        String uid = newUid(), otid = newOtid();
        String jws = purchase(otid, otid, AppleStates.appAccountToken(uid), true);
        Res r = verify(uid, jws);
        assertEquals(200, r.status(), r.body());
        JsonNode v = r.json();
        assertTrue(v.get("active").asBoolean());
        assertEquals(Plans.PREMIUM, v.get("planId").asText());
        assertEquals("ACTIVE", v.get("status").asText());
        assertEquals("APP_STORE", v.get("store").asText());
        assertEquals(PRODUCT, v.get("productId").asText());
        assertTrue(v.get("autoRenewing").asBoolean());
        assertEquals(Plans.FEATURE_KEYS.size(), v.get("features").size());

        Document d = sub(uid);
        assertEquals("apple:" + otid, d.getString("purchaseToken"));
        assertEquals("APP_STORE", d.getString("store"));
        assertTrue(d.getBoolean("testPurchase"));
        assertEquals(List.of("PURCHASE"), txs("apple:" + otid).stream().map(x -> x.getString("type")).toList());

        // Aynı hesap başka platformdan (/me) Premium görür
        JsonNode m = me(uid).json();
        assertTrue(m.get("active").asBoolean());
        assertEquals("APP_STORE", m.get("store").asText());
    }

    @Test
    void autoRenewOffIsCanceledButStillEntitled() {
        String uid = newUid(), otid = newOtid();
        Res r = verify(uid, purchase(otid, otid, AppleStates.appAccountToken(uid), false));
        assertEquals(200, r.status(), r.body());
        assertEquals("CANCELED", r.json().get("status").asText());
        assertTrue(r.json().get("active").asBoolean());
        assertEquals("USER", r.json().get("cancelReason").asText());
    }

    @Test
    void purchaseOfAnotherAccountIsRejected() {
        String uid = newUid(), otid = newOtid();
        Res r = verify(uid, purchase(otid, otid, AppleStates.appAccountToken(newUid()), true));
        assertEquals(403, r.status(), r.body());
        assertEquals("account_mismatch", r.error());
        assertNull(sub(uid));
    }

    @Test
    void purchaseWithoutAccountTokenBindsToFirstAccountOnly() {
        String first = newUid(), second = newUid(), otid = newOtid();
        String jws = purchase(otid, otid, null, true);
        assertEquals(200, verify(first, jws).status());
        Res r = verify(second, jws);
        assertEquals(409, r.status(), r.body());
        assertEquals("purchase_token_in_use", r.error());
        // İlk hesap yeniden doğrulayabilir (geri yükleme)
        assertEquals(200, verify(first, jws).status());
    }

    @Test
    void invalidSignatureUnknownProductAndMissingKeyAreRejected() {
        String uid = newUid();
        Res bad = verify(uid, "imzasiz");
        assertEquals(400, bad.status(), bad.body());
        assertEquals("invalid_transaction", bad.error());

        String otid = newOtid();
        AppleTransaction other = tx(otid, otid, "com.example.other", AppleStates.appAccountToken(uid), Instant.now().plus(Duration.ofDays(3)));
        apple.signed.put("jws-other-" + otid, other);
        Res unknown = verify(uid, "jws-other-" + otid);
        assertEquals(400, unknown.status(), unknown.body());
        assertEquals("unknown_product", unknown.error());

        apple.configured = false;
        try {
            Res r = verify(uid, purchase(newOtid(), "1", AppleStates.appAccountToken(uid), true));
            assertEquals(503, r.status(), r.body());
            assertEquals("billing_not_configured", r.error());
        } finally {
            apple.configured = true;
        }
    }

    @Test
    void appStoreOutageIsBadGateway() {
        String uid = newUid(), otid = newOtid();
        String jws = purchase(otid, otid, AppleStates.appAccountToken(uid), true);
        apple.failing.add(otid);
        Res r = verify(uid, jws);
        assertEquals(502, r.status(), r.body());
        assertEquals("appstore_unavailable", r.error());
    }

    // ------------------------------------------------------------------ bildirimler

    @Test
    void notificationsUpdateTheSubscriptionAndAreDeduplicated() {
        String uid = newUid(), otid = newOtid();
        UUID account = AppleStates.appAccountToken(uid);
        assertEquals(200, verify(uid, purchase(otid, otid, account, true)).status());

        // Yenileme: yeni işlem kimliği, yeni bitiş
        AppleTransaction renewed = tx(otid, otid + "1", PRODUCT, account, Instant.now().plus(Duration.ofDays(59)));
        apple.statuses.put(otid, status(1, renewed, true));
        String key = notification("DID_RENEW", null, status(1, renewed, true));
        Res r = notify(key);
        assertEquals(200, r.status(), r.body());
        assertEquals("processed", r.json().get("status").asText());
        assertEquals("APPLIED", r.json().get("result").asText());
        assertEquals(otid + "1", sub(uid).getString("orderId"));
        assertEquals("duplicate", notify(key).json().get("status").asText());

        // İade: hak hemen biter
        AppleTransaction refunded = new AppleTransaction(renewed.transactionId(), otid, BUNDLE, PRODUCT, "21500000", account,
                renewed.purchaseDate(), renewed.originalPurchaseDate(), renewed.expiresDate(), Instant.now(), 0, false, true, "TUR",
                renewed.type(), "PURCHASE");
        apple.statuses.put(otid, status(5, refunded, false));
        assertEquals(200, notify(notification("REFUND", null, status(5, refunded, false))).status());
        JsonNode m = me(uid).json();
        assertFalse(m.get("active").asBoolean());
        assertEquals("EXPIRED", m.get("status").asText());
        List<String> types = txs("apple:" + otid).stream().map(x -> x.getString("type")).toList();
        assertEquals(List.of("PURCHASE", "RENEWAL", "REVOKE"), types);
    }

    @Test
    void notificationBeforeVerifyIsLinkedLater() {
        String uid = newUid(), otid = newOtid();
        String jws = purchase(otid, otid, AppleStates.appAccountToken(uid), true);
        Res r = notify(notification("SUBSCRIBED", "INITIAL_BUY", apple.statuses.get(otid)));
        assertEquals(200, r.status(), r.body());
        assertEquals("UNKNOWN_USER", r.json().get("result").asText());
        assertNull(txs("apple:" + otid).get(0).getString("userId"));

        assertEquals(200, verify(uid, jws).status());
        assertTrue(txs("apple:" + otid).stream().allMatch(x -> uid.equals(x.getString("userId"))));
    }

    @Test
    void testAndUnsignedNotifications() {
        Res t = notify(notification("TEST", null, null));
        assertEquals(200, t.status(), t.body());
        assertEquals("ignored", t.json().get("status").asText());

        Res bad = notify("sahte");
        assertEquals(400, bad.status(), bad.body());
        assertEquals("invalid_notification", bad.error());

        assertEquals(400, http.call("POST", NOTIFY, "{}").status());
    }

    // ------------------------------------------------------------------ /me tazelemesi

    @Test
    void staleAppStoreRecordIsRefreshedOnMe() {
        String uid = newUid(), otid = newOtid();
        UUID account = AppleStates.appAccountToken(uid);
        assertEquals(200, verify(uid, purchase(otid, otid, account, true)).status());
        // Bildirim kaçtı: App Store'da süresi doldu
        AppleTransaction t = tx(otid, otid, PRODUCT, account, Instant.now().minus(Duration.ofHours(1)));
        apple.statuses.put(otid, status(2, t, false));
        assertTrue(me(uid).json().get("active").asBoolean(), "taze kayıt yeniden okunmaz");

        mongo.getCollection(Stores.SUBSCRIPTIONS).updateOne(Filters.eq("_id", uid),
                Updates.set("fetchedAt", Date.from(Instant.now().minus(Duration.ofHours(13)))));
        JsonNode m = me(uid).json();
        assertFalse(m.get("active").asBoolean());
        assertEquals("EXPIRED", m.get("status").asText());
    }
}
