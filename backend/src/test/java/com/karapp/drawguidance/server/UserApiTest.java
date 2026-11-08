package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kullanıcı kaydı ve Firebase jetonu doğrulaması (imza, iss, aud, süre, sub). */
class UserApiTest extends ApiContextTest {
    static final Set<String> RESPONSE_FIELDS = Set.of("id", "firebaseUid", "displayName", "givenName", "familyName", "email",
            "emailVerified", "photoUrl", "accountCreatedAt", "birthDate", "createdAt", "updatedAt", "version",
            "termsAcceptedAt", "termsVersion");
    static final String ME = "/api/v1/users/me";

    @Autowired
    MongoTemplate mongo;

    private final ObjectMapper json = new ObjectMapper();

    static String newUid() { return "uid" + UUID.randomUUID().toString().replace("-", "").substring(0, 25); }

    String body(String id, String uid, String displayName, String birthDate) {
        return body(id, uid, displayName, birthDate, null, null);
    }

    String body(String id, String uid, String displayName, String birthDate, Long termsAcceptedAt, String termsVersion) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("id", id);
        b.put("firebaseUid", uid);
        b.put("displayName", displayName);
        b.put("givenName", "Ahmet");
        b.put("familyName", "Kar");
        b.put("email", "ahmet@example.com");
        b.put("emailVerified", true);
        b.put("photoUrl", "https://example.com/a.png");
        b.put("accountCreatedAt", 1700000000000L);
        b.put("birthDate", birthDate);
        b.put("clientUpdatedAt", 1760000000000L);
        if (termsAcceptedAt != null) b.put("termsAcceptedAt", termsAcceptedAt);
        if (termsVersion != null) b.put("termsVersion", termsVersion);
        try {
            return json.writeValueAsString(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Res put(String token, String body) { return http.call("PUT", ME, body, "Authorization", "Bearer " + token); }

    Res get(String token) { return http.call("GET", ME, null, "Authorization", "Bearer " + token); }

    Res delete(String token) { return http.call("DELETE", ME, null, "Authorization", "Bearer " + token); }

    long docsFor(String uid) { return mongo.count(Query.query(Criteria.where("firebaseUid").is(uid)), Stores.USERS); }

    @Test
    void createUpdateGetDelete() {
        String uid = newUid(), token = TestTokens.valid(uid), id1 = UUID.randomUUID().toString();

        Res created = put(token, body(id1, uid, "Ahmet Kar", "1990-05-17"));
        assertEquals(200, created.status(), created.body());
        JsonNode c = created.json();
        assertEquals(RESPONSE_FIELDS, fieldNames(c));
        assertEquals(id1, c.get("id").asText());
        assertEquals(uid, c.get("firebaseUid").asText());
        assertEquals("Ahmet Kar", c.get("displayName").asText());
        assertEquals("Ahmet", c.get("givenName").asText());
        assertEquals("Kar", c.get("familyName").asText());
        assertEquals("ahmet@example.com", c.get("email").asText());
        assertTrue(c.get("emailVerified").asBoolean());
        assertEquals("https://example.com/a.png", c.get("photoUrl").asText());
        assertEquals(1700000000000L, c.get("accountCreatedAt").asLong());
        assertEquals("1990-05-17", c.get("birthDate").asText());
        assertEquals(1, c.get("version").asLong());
        Instant createdAt = Instant.parse(c.get("createdAt").asText());
        assertEquals(createdAt, Instant.parse(c.get("updatedAt").asText()));

        // Kayıt varken gövdedeki farklı id yok sayılır, _id ve createdAt korunur
        Res updated = put(token, body(UUID.randomUUID().toString(), uid, "Ahmet K.", null));
        assertEquals(200, updated.status(), updated.body());
        JsonNode u = updated.json();
        assertEquals(id1, u.get("id").asText());
        assertEquals("Ahmet K.", u.get("displayName").asText());
        assertTrue(u.get("birthDate").isNull());
        assertEquals(2, u.get("version").asLong());
        assertEquals(createdAt, Instant.parse(u.get("createdAt").asText()));
        assertFalse(Instant.parse(u.get("updatedAt").asText()).isBefore(createdAt));

        Res got = get(token);
        assertEquals(200, got.status());
        assertEquals(u, got.json());

        // Veritabanında tek belge, _id = ilk id, clientUpdatedAt saklanır ama yanıtta yok
        assertEquals(1, docsFor(uid));
        Document doc = mongo.findOne(Query.query(Criteria.where("firebaseUid").is(uid)), Document.class, Stores.USERS);
        assertEquals(id1, doc.get("_id"));
        assertEquals(1760000000000L, ((Number) doc.get("clientUpdatedAt")).longValue());

        assertEquals(204, delete(token).status());
        Res gone = get(token);
        assertEquals(404, gone.status());
        assertEquals("user_not_found", gone.error());
        assertEquals(0, docsFor(uid));
        assertEquals(204, delete(token).status(), "silme tekrarlanabilir");
    }

    /**
     * v40 hesap silme bütün koleksiyonlardan kullanıcının verisini kaldırır: profil, Çizim Görevi ilerlemesi, kimlikli hata
     * raporları silinir; abonelik ve hareketler saklanır ama uid bağı koparılır; aynı e-postanın bekleyen web silme
     * talebi tamamlanır. Son olarak veritabanındaki hiçbir koleksiyonda uid kalmadığı denetlenir (yeni bir kullanıcı
     * koleksiyonu eklenip silmeye eklenmezse bu test bozulur).
     */
    @Test
    void deleteRemovesUserDataEverywhereAndAnonymizesBilling() {
        String uid = newUid(), token = TestTokens.valid(uid), id = UUID.randomUUID().toString();
        assertEquals(200, put(token, body(id, uid, "Ahmet Kar", "1990-05-17")).status());
        java.util.Date now = new java.util.Date();
        String purchaseToken = "tok-" + uid;
        mongo.getCollection(Stores.CHALLENGE_PROGRESS).insertOne(new Document("_id", uid).append("firebaseUid", uid).append("levels", new Document()));
        mongo.getCollection(Stores.ERROR_REPORTS).insertOne(new Document("_id", UUID.randomUUID().toString()).append("uid", uid)
                .append("kind", "ERROR").append("receivedAt", now));
        String anonymousReport = UUID.randomUUID().toString();
        mongo.getCollection(Stores.ERROR_REPORTS).insertOne(new Document("_id", anonymousReport).append("kind", "ERROR").append("receivedAt", now));
        mongo.getCollection(Stores.SUBSCRIPTIONS).insertOne(new Document("_id", uid).append("planId", "premium").append("status", "ACTIVE")
                .append("purchaseToken", purchaseToken).append("orderId", "GPA.1234").append("expiryTime", now));
        String txId = UUID.randomUUID().toString();
        mongo.getCollection(Stores.SUBSCRIPTION_TRANSACTIONS).insertOne(new Document("_id", txId).append("userId", uid)
                .append("purchaseToken", purchaseToken).append("orderId", "GPA.1234-" + uid).append("type", "PURCHASE").append("at", now));
        // TestTokens jetonlarının e-postası ahmet@example.com
        String requestId = UUID.randomUUID().toString();
        mongo.getCollection(Stores.DELETION_REQUESTS).insertOne(new Document("_id", requestId).append("email", "ahmet@example.com")
                .append("status", "PENDING").append("createdAt", now));

        assertEquals(204, delete(token).status());

        assertEquals(0, docsFor(uid));
        assertEquals(0, mongo.getCollection(Stores.CHALLENGE_PROGRESS).countDocuments(new Document("_id", uid)));
        assertEquals(0, mongo.getCollection(Stores.ERROR_REPORTS).countDocuments(new Document("uid", uid)));
        assertEquals(1, mongo.getCollection(Stores.ERROR_REPORTS).countDocuments(new Document("_id", anonymousReport)), "kimliksiz raporlar kalır");

        Document kept = mongo.getCollection(Stores.SUBSCRIPTIONS).find(new Document("retainedPurchaseToken", purchaseToken)).first();
        assertNotNull(kept, "abonelik kaydı saklanır");
        assertTrue(kept.getString("_id").startsWith("deleted:"), kept.getString("_id"));
        assertNull(kept.get("purchaseToken"), "jeton yeni doğrulamayı engellemesin");
        assertEquals("GPA.1234", kept.getString("orderId"));
        assertEquals(Boolean.TRUE, kept.getBoolean("anonymized"));
        Document tx = mongo.getCollection(Stores.SUBSCRIPTION_TRANSACTIONS).find(new Document("_id", txId)).first();
        assertNotNull(tx, "hareket saklanır");
        assertNull(tx.get("userId"));
        assertNotNull(tx.get("anonymizedAt"));
        assertEquals("COMPLETED", mongo.getCollection(Stores.DELETION_REQUESTS).find(new Document("_id", requestId)).first().getString("status"));

        // Hiçbir koleksiyonda uid kalmaz
        for (String coll : mongo.getCollectionNames()) {
            long left = mongo.getCollection(coll).countDocuments(new Document("$or", List.of(new Document("_id", uid),
                    new Document("firebaseUid", uid), new Document("uid", uid), new Document("userId", uid))));
            assertEquals(0, left, coll + " koleksiyonunda kullanıcının verisi kaldı");
        }
        assertEquals(204, delete(token).status(), "silme tekrarlanabilir");
    }

    /** v36: hesap açılırken onaylanan kullanım şartları ve KVKK metni saklanır; onayı göndermeyen istek onu silmez. */
    @Test
    void termsConsentIsStoredAndKeptByOlderClients() {
        String uid = newUid(), token = TestTokens.valid(uid), id = UUID.randomUUID().toString();
        Res created = put(token, body(id, uid, "Ahmet Kar", "1990-05-17", 1760000000123L, "2026-10"));
        assertEquals(200, created.status(), created.body());
        assertEquals(1760000000123L, created.json().get("termsAcceptedAt").asLong());
        assertEquals("2026-10", created.json().get("termsVersion").asText());

        // Onay alanı olmayan (eski sürüm) güncelleme var olan onayı korur
        Res updated = put(token, body(id, uid, "Ahmet K.", null));
        assertEquals(200, updated.status(), updated.body());
        assertEquals(1760000000123L, updated.json().get("termsAcceptedAt").asLong());
        assertEquals("2026-10", updated.json().get("termsVersion").asText());
        Document doc = mongo.findOne(Query.query(Criteria.where("firebaseUid").is(uid)), Document.class, Stores.USERS);
        assertEquals("2026-10", doc.get("termsVersion"));

        // Onaysız ilk kayıtta alanlar boş döner
        String other = newUid();
        Res plain = put(TestTokens.valid(other), body(UUID.randomUUID().toString(), other, "x", null));
        assertEquals(200, plain.status(), plain.body());
        assertTrue(plain.json().get("termsAcceptedAt").isNull());
        assertTrue(plain.json().get("termsVersion").isNull());
    }

    @Test
    void getWithoutRecordIs404() {
        Res r = get(TestTokens.valid(newUid()));
        assertEquals(404, r.status());
        assertEquals("user_not_found", r.error());
    }

    @Test
    void missingTokenIs401() {
        for (String method : new String[]{"GET", "PUT", "DELETE"}) {
            Res r = http.call(method, ME, "PUT".equals(method) ? body(UUID.randomUUID().toString(), newUid(), "x", null) : null);
            assertEquals(401, r.status(), method);
            assertEquals("unauthorized", r.error());
            assertNotNull(r.json().get("message").asText(null));
            assertTrue(r.header("WWW-Authenticate").startsWith("Bearer"));
        }
        // Bearer olmayan şema da jetonsuz sayılır
        assertEquals(401, http.get(ME, "Authorization", "Basic Zm9vOmJhcg==").status());
        assertEquals(401, http.get(ME, "X-User-Id", "default-user").status());
    }

    @Test
    void garbageTokenIs401() {
        for (String token : new String[]{"garbage", "a.b.c", "eyJhbGciOiJSUzI1NiJ9.e30.c2ln", ""}) {
            Res r = http.get(ME, "Authorization", "Bearer " + token);
            assertEquals(401, r.status(), token);
            assertEquals("invalid_token", r.error(), token);
            assertTrue(r.header("WWW-Authenticate").contains("invalid_token"), token);
        }
    }

    @Test
    void tokenRulesAreEnforced() {
        String uid = newUid();
        Instant now = Instant.now();
        Instant iat = now.minusSeconds(30), exp = now.plus(Duration.ofHours(1));
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("yanlış aud", TestTokens.mint(TestTokens.KEY, uid, "baska-proje", TestTokens.ISSUER, iat, exp));
        bad.put("yanlış iss", TestTokens.mint(TestTokens.KEY, uid, TestTokens.PROJECT, "https://securetoken.google.com/baska-proje", iat, exp));
        bad.put("süresi dolmuş", TestTokens.mint(TestTokens.KEY, uid, TestTokens.PROJECT, TestTokens.ISSUER, now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1))));
        bad.put("exp yok", TestTokens.mint(TestTokens.KEY, uid, TestTokens.PROJECT, TestTokens.ISSUER, iat, null));
        bad.put("iat yok", TestTokens.mint(TestTokens.KEY, uid, TestTokens.PROJECT, TestTokens.ISSUER, null, exp));
        bad.put("iat gelecekte", TestTokens.mint(TestTokens.KEY, uid, TestTokens.PROJECT, TestTokens.ISSUER, now.plus(Duration.ofMinutes(10)), exp.plus(Duration.ofMinutes(10))));
        bad.put("sub yok", TestTokens.mint(TestTokens.KEY, null, TestTokens.PROJECT, TestTokens.ISSUER, iat, exp));
        bad.put("sub boş", TestTokens.mint(TestTokens.KEY, "", TestTokens.PROJECT, TestTokens.ISSUER, iat, exp));
        bad.put("başka anahtar", TestTokens.mint(TestTokens.OTHER, uid, TestTokens.PROJECT, TestTokens.ISSUER, iat, exp));
        bad.forEach((why, token) -> {
            Res r = get(token);
            assertEquals(401, r.status(), why + ": " + r.body());
            assertEquals("invalid_token", r.error(), why);
            Res p = put(token, body(UUID.randomUUID().toString(), uid, "x", null));
            assertEquals(401, p.status(), why);
        });
        assertEquals(0, docsFor(uid));
        // Aynı alanlarla doğru jeton geçer
        assertEquals(404, get(TestTokens.mint(TestTokens.KEY, uid, TestTokens.PROJECT, TestTokens.ISSUER, iat, exp)).status());
    }

    @Test
    void bodyUidMustMatchTokenSub() {
        String uid = newUid(), other = newUid();
        Res r = put(TestTokens.valid(uid), body(UUID.randomUUID().toString(), other, "x", null));
        assertEquals(403, r.status(), r.body());
        assertEquals("uid_mismatch", r.error());
        assertEquals(0, docsFor(uid));
        assertEquals(0, docsFor(other));
    }

    @Test
    void newRecordNeedsUuidId() {
        String uid = newUid(), token = TestTokens.valid(uid);
        for (String id : new String[]{"not-a-uuid", "1-1-1-1-1", "", "0f8fad5bd9cb469fa16570867728950e"}) {
            Res r = put(token, body(id, uid, "x", null));
            assertEquals(400, r.status(), id + " " + r.body());
            assertEquals("invalid_id", r.error(), id);
        }
        Res noId = put(token, body(null, uid, "x", null));
        assertEquals(400, noId.status());
        assertEquals("invalid_id", noId.error());
        assertEquals(0, docsFor(uid));

        // Büyük harfli UUID kabul edilir, küçük harfe çevrilir
        String id = UUID.randomUUID().toString();
        Res ok = put(token, body(id.toUpperCase(), uid, "x", null));
        assertEquals(200, ok.status(), ok.body());
        assertEquals(id, ok.json().get("id").asText());
        // Kayıt varken geçersiz id yok sayılır
        Res again = put(token, body("not-a-uuid", uid, "y", null));
        assertEquals(200, again.status(), again.body());
        assertEquals(id, again.json().get("id").asText());
        assertEquals("y", again.json().get("displayName").asText());
    }

    @Test
    void birthDateMustBeValidAndNotInFuture() {
        String uid = newUid(), token = TestTokens.valid(uid);
        String future = LocalDate.now(ZoneOffset.UTC).plusDays(2).toString();
        for (String date : new String[]{future, "17.05.1990", "1990-02-30", "1990-5-17", "yarın"}) {
            Res r = put(token, body(UUID.randomUUID().toString(), uid, "x", date));
            assertEquals(400, r.status(), date + " " + r.body());
            assertEquals("invalid_birth_date", r.error(), date);
        }
        assertEquals(0, docsFor(uid));
        Res leap = put(token, body(UUID.randomUUID().toString(), uid, "x", "2000-02-29"));
        assertEquals(200, leap.status(), leap.body());
        assertEquals("2000-02-29", leap.json().get("birthDate").asText());
        Res today = put(token, body(UUID.randomUUID().toString(), uid, "x", LocalDate.now(ZoneOffset.UTC).toString()));
        assertEquals(200, today.status(), today.body());
    }

    @Test
    void invalidBodiesAre400() {
        String uid = newUid(), token = TestTokens.valid(uid);
        Res noUid = put(token, "{\"id\":\"" + UUID.randomUUID() + "\",\"displayName\":\"x\"}");
        assertEquals(400, noUid.status());
        assertEquals("validation", noUid.error());
        Res badEmail = put(token, body(UUID.randomUUID().toString(), uid, "x", null).replace("ahmet@example.com", "eposta-degil"));
        assertEquals(400, badEmail.status());
        assertEquals("validation", badEmail.error());
        Res broken = put(token, "{\"id\":");
        assertEquals(400, broken.status());
        assertEquals("bad_request", broken.error());
        Res wrongType = put(token, body(UUID.randomUUID().toString(), uid, "x", null).replace("1700000000000", "\"dün\""));
        assertEquals(400, wrongType.status());
        assertEquals(0, docsFor(uid));
    }

    @Test
    void concurrentPutsForOneUidKeepOneDocument() throws Exception {
        String uid = newUid(), token = TestTokens.valid(uid);
        int n = 16;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<Res>> results = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                String id = UUID.randomUUID().toString();
                String name = "eşzamanlı " + i;
                results.add(pool.submit(() -> {
                    start.await();
                    return put(token, body(id, uid, name, null));
                }));
            }
            start.countDown();
            Set<String> ids = new HashSet<>();
            for (Future<Res> f : results) {
                Res r = f.get();
                assertEquals(200, r.status(), r.body());
                ids.add(r.json().get("id").asText());
            }
            assertEquals(1, ids.size(), "hepsi aynı kaydı görmeli: " + ids);
            assertEquals(1, docsFor(uid));
            Document doc = mongo.findOne(Query.query(Criteria.where("firebaseUid").is(uid)), Document.class, Stores.USERS);
            assertEquals(ids.iterator().next(), doc.get("_id"));
            assertEquals(n, ((Number) doc.get("version")).longValue(), "her istek tek bir yazma");
        } finally {
            pool.shutdownNow();
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> out = new HashSet<>();
        node.fieldNames().forEachRemaining(out::add);
        return out;
    }
}
