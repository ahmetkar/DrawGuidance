package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.privacy.PersonalDataCipher;
import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.SlackStub;
import com.karapp.drawguidance.server.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v40 geri bildirim ({@code POST /api/v1/feedback}: giriş, izinle cihaz bilgisi/e-posta, Slack, sınır), uygulama
 * sürümü ({@code GET /api/v1/app/version}) ve içerik eşitleme izni ({@code GET /api/v1/sync/permit}).
 */
class AccountSyncApiTest extends ApiContextTest {
    static final String FEEDBACK = "/api/v1/feedback";

    @DynamicPropertySource
    static void appVersion(DynamicPropertyRegistry r) {
        r.add("drawguidance.app.latest-version-code", () -> "40100");
        r.add("drawguidance.app.min-supported-version-code", () -> "39000");
        r.add("drawguidance.app.message", () -> "Yeni sürüm hazır");
        r.add("drawguidance.feedback.limit", () -> "3");
    }

    @Autowired
    MongoTemplate mongo;

    @Autowired
    PersonalDataCipher cipher;

    static String uid() { return "uid-" + UUID.randomUUID(); }

    Res feedback(String uid, String body) {
        return uid == null ? http.call("POST", FEEDBACK, body) : http.call("POST", FEEDBACK, body, "Authorization", "Bearer " + TestTokens.valid(uid));
    }

    static final String DEVICE = "\"device\":{\"model\":\"samsung SM-A515F\",\"androidVersion\":\"14\",\"appVersion\":\"40.0.0\",\"versionCode\":40000,\"locale\":\"tr-TR\"}";

    @Test
    void feedbackNeedsSignIn() {
        Res r = feedback(null, "{\"mode\":\"ART\",\"rating\":5}");
        assertEquals(401, r.status(), r.body());
        Res bad = http.call("POST", FEEDBACK, "{\"mode\":\"ART\",\"rating\":5}", "Authorization", "Bearer garbage");
        assertEquals(401, bad.status());
    }

    @Test
    void feedbackWithConsentStoresDeviceAndEmail() throws Exception {
        SlackStub slack = SlackStub.shared();
        String uid = uid();
        Res r = feedback(uid, "{\"mode\":\"comic\",\"feature\":\"comic.balloons\",\"rating\":4,\"text\":\"Balonlar harika\",\"consent\":true," + DEVICE + "}");
        assertEquals(202, r.status(), r.body());
        String id = r.json().get("id").asText();
        Document d = mongo.findById(id, Document.class, Stores.FEEDBACK);
        assertNotNull(d);
        assertEquals(uid, d.get("uid"));
        assertEquals("COMIC", d.get("mode"));
        assertEquals("comic.balloons", d.get("feature"));
        assertEquals(4, d.get("rating"));
        assertEquals("Balonlar harika", d.get("text"));
        assertEquals(true, d.get("consent"));
        assertEquals("ahmet@example.com", d.get("email"));
        Document device = (Document) d.get("device");
        assertNull(device.get("model"), "v49.2 telefon modeli düz metin saklanmaz");
        assertFalse(d.toJson().contains("SM-A515F"));
        assertEquals("samsung SM-A515F", cipher.open(PersonalDataCipher.FEEDBACK, id, device.getString("modelEnc")));
        assertEquals("14", device.get("androidVersion"));
        assertEquals("40.0.0", device.get("appVersion"));
        assertEquals("tr-TR", device.get("locale"));
        String body = null;
        for (String m; (m = slack.next()) != null; ) if (m.contains(id)) { body = m; break; }
        assertNotNull(body, "Slack bildirimi gelmedi");
        assertTrue(body.contains("COMIC") && body.contains("a***@example.com") && body.contains("4/5"), body);
        assertFalse(body.contains("Balonlar harika"), "yazının kendisi Slack'e gitmez");
        assertFalse(body.contains("ahmet@example.com"));
    }

    @Test
    void feedbackWithoutConsentDropsDeviceAndEmail() {
        String uid = uid();
        Res r = feedback(uid, "{\"mode\":\"KIDS\",\"rating\":2,\"consent\":false," + DEVICE + "}");
        assertEquals(202, r.status(), r.body());
        Document d = mongo.findById(r.json().get("id").asText(), Document.class, Stores.FEEDBACK);
        assertEquals("KIDS", d.get("mode"));
        assertEquals(false, d.get("consent"));
        assertFalse(d.containsKey("device"), "izin yoksa cihaz bilgisi saklanmaz");
        assertFalse(d.containsKey("email"), "izin yoksa e-posta saklanmaz");
        assertNull(d.get("text"));
    }

    @Test
    void invalidFeedbackIsRejected() {
        String[][] cases = {
                {"{\"mode\":\"PAINT\",\"rating\":3}", "invalid_mode"},
                {"{\"rating\":3}", "invalid_mode"},
                {"{\"mode\":\"ART\",\"rating\":0}", "invalid_rating"},
                {"{\"mode\":\"ART\",\"rating\":6}", "invalid_rating"},
                {"{\"mode\":\"ART\"}", "invalid_rating"},
                {"{\"mode\":\"ART\",\"rating\":3,\"feature\":\"<script>\"}", "invalid_feature"},
                {"{\"mode\":\"ART\",\"rating\":3,\"text\":\"" + "a".repeat(5001) + "\"}", "too_long"},
                {"{bozuk", "bad_request"},
        };
        for (String[] c : cases) {
            // Her istek kullanıcının kovasından düşer (sınır 3): her durum ayrı kullanıcı
            Res r = feedback(uid(), c[0]);
            assertEquals(400, r.status(), c[1] + " " + r.body());
            assertEquals(c[1], r.error());
        }
    }

    @Test
    void feedbackIsRateLimitedPerUser() {
        String uid = uid();
        for (int i = 0; i < 3; i++) assertEquals(202, feedback(uid, "{\"mode\":\"NOTES\",\"rating\":5}").status(), "geri bildirim " + i);
        Res limited = feedback(uid, "{\"mode\":\"NOTES\",\"rating\":5}");
        assertEquals(429, limited.status());
        assertEquals("rate_limited", limited.error());
        assertNotNull(limited.header("Retry-After"));
        // Başka kullanıcı etkilenmez
        assertEquals(202, feedback(uid(), "{\"mode\":\"NOTES\",\"rating\":5}").status());
    }

    @Test
    void appVersionIsPublic() {
        Res r = http.get("/api/v1/app/version");
        assertEquals(200, r.status(), r.body());
        JsonNode j = r.json();
        assertEquals(40100, j.get("latestVersionCode").asLong());
        assertEquals(39000, j.get("minSupportedVersionCode").asLong());
        assertEquals("Yeni sürüm hazır", j.get("message").asText());
        assertFalse(http.call("POST", "/api/v1/app/version", "{}").status() < 400, "yalnız GET");
    }

    @Test
    void permitForSignedInUserIsStable() {
        String uid = uid();
        Res a = http.get("/api/v1/sync/permit", "Authorization", "Bearer " + TestTokens.valid(uid));
        assertEquals(200, a.status(), a.body());
        JsonNode j = a.json();
        Instant start = Instant.parse(j.get("slotStart").asText());
        Instant end = Instant.parse(j.get("slotEnd").asText());
        Instant next = Instant.parse(j.get("nextSlotStart").asText());
        assertEquals(Duration.ofMinutes(10), Duration.between(start, end));
        assertEquals(600, j.get("slotLengthSeconds").asLong());
        assertTrue(j.get("slot").asInt() >= 0 && j.get("slot").asInt() < 144);
        assertTrue(j.has("allowed"));
        assertTrue(next.isAfter(Instant.now().minusSeconds(5)));
        assertEquals(1000, j.get("contentVersion").asLong(), "test kataloğunun sürümü");
        // Grup kalıcı: ikinci istek aynı grubu ve dilimi verir
        Res b = http.get("/api/v1/sync/permit", "Authorization", "Bearer " + TestTokens.valid(uid));
        assertEquals(j.get("group").asLong(), b.json().get("group").asLong());
        assertEquals(j.get("slot").asInt(), b.json().get("slot").asInt());
        Document g = mongo.findById(uid, Document.class, Stores.SYNC_GROUPS);
        assertNotNull(g);
        assertEquals(j.get("group").asLong(), ((Number) g.get("seq")).longValue() / 1000);
    }

    @Test
    void permitForAnonymousDeviceUsesDeviceId() {
        Res a = http.get("/api/v1/sync/permit?deviceId=0f8fad5b-d9cb-469f-a165-70867728950e");
        assertEquals(200, a.status(), a.body());
        assertTrue(a.json().get("group").isNull());
        Res h = http.get("/api/v1/sync/permit", "X-Device-Id", "0f8fad5b-d9cb-469f-a165-70867728950e");
        assertEquals(a.json().get("slot").asInt(), h.json().get("slot").asInt(), "aynı cihaz aynı dilim");
        Res none = http.get("/api/v1/sync/permit");
        assertEquals(400, none.status());
        assertEquals("invalid_device_id", none.error());
        assertEquals(400, http.get("/api/v1/sync/permit?deviceId=kisa").status());
        Res badToken = http.get("/api/v1/sync/permit", "Authorization", "Bearer garbage");
        assertEquals(401, badToken.status());
        // Geçersiz jeton + cihaz kimliği: cihazın dilimi
        Res fallback = http.get("/api/v1/sync/permit?deviceId=0f8fad5b-d9cb-469f-a165-70867728950e", "Authorization", "Bearer garbage");
        assertEquals(200, fallback.status());
        assertEquals(a.json().get("slot").asInt(), fallback.json().get("slot").asInt());
    }
}
