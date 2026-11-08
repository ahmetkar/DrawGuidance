package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.privacy.PersonalDataCipher;
import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.SlackStub;
import com.karapp.drawguidance.server.support.TestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v40 web sitesi uçları: girişsiz hesap silme talebi ({@code POST /api/v1/account/deletion-requests}) ve destek
 * mesajı ({@code POST /api/v1/support/messages}); doğrulama, yineleme, IP başına sınır, Slack bildirimi ve CORS.
 * Test bağlamında {@code behind-proxy=true}: her test kendi {@code X-Forwarded-For} adresiyle ayrı kova kullanır.
 */
class WebFormsApiTest extends ApiContextTest {
    static final String DELETION = "/api/v1/account/deletion-requests";
    static final String SUPPORT = "/api/v1/support/messages";
    static final String ALLOWED_ORIGIN = "https://drawguidance.ahmetkar.com";
    private static final AtomicInteger IPS = new AtomicInteger(1);

    @Autowired
    MongoTemplate mongo;

    @Autowired
    PersonalDataCipher cipher;

    private final ObjectMapper json = new ObjectMapper();

    /** Her çağrıda yeni bir belgesel IP (198.18.0.0/15, kıyaslama ağı). */
    static String newIp() {
        int n = IPS.getAndIncrement();
        return "198.18." + (n / 250) + "." + (n % 250 + 1);
    }

    /** Benzersiz alan adlı e-posta: gizlenmiş hali de ({@code u***@<alan>}) benzersiz olur. */
    static String newEmail() {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return "User." + id + "@T" + id + ".example";
    }

    String body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        try {
            return json.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Res post(String path, String ip, String body, String... extra) {
        String[] headers = new String[extra.length + 2];
        headers[0] = "X-Forwarded-For";
        headers[1] = "6.6.6.6, " + ip;
        System.arraycopy(extra, 0, headers, 2, extra.length);
        return http.call("POST", path, body, headers);
    }

    List<Document> requestsFor(String email) {
        return mongo.find(Query.query(Criteria.where("email").is(email.toLowerCase())), Document.class, Stores.DELETION_REQUESTS);
    }

    /** Slack sahtesine gelen, verilen parçayı içeren ileti (başka testlerin gecikmiş iletileri atlanır). */
    static String slackMessageContaining(String part) throws InterruptedException {
        for (String b; (b = SlackStub.shared().next()) != null; ) {
            if (b.contains(part)) return b;
        }
        return null;
    }

    // ---------------------------------------------------------------- silme talebi

    @Test
    void deletionRequestIsStoredWithoutRawIpAndNotifiesSlack() throws Exception {
        String ip = newIp();
        String email = newEmail();
        String ua = "Mozilla/5.0 " + "x".repeat(400);
        Res r = post(DELETION, ip, body("email", "  " + email + " ", "reason", "Artık kullanmıyorum", "locale", "tr"), "User-Agent", ua);
        assertEquals(202, r.status(), r.body());
        assertEquals("received", r.json().path("status").asText());
        assertEquals(1, r.json().size(), "yanıtta yalnız durum var");

        List<Document> docs = requestsFor(email);
        assertEquals(1, docs.size());
        Document d = docs.get(0);
        assertEquals(email.toLowerCase(), d.getString("email"), "küçük harfe çevrilir");
        assertEquals("Artık kullanmıyorum", d.getString("reason"));
        assertEquals("tr", d.getString("locale"));
        assertEquals("PENDING", d.getString("status"));
        assertEquals("web", d.getString("source"));
        assertNotNull(d.getDate("createdAt"));
        assertTrue(d.getString("ipHash").matches("[0-9a-f]{32}"), d.getString("ipHash"));
        assertNull(d.get("userAgent"), "v49.2 tarayıcı bilgisi düz metin saklanmaz");
        String storedUa = cipher.open(PersonalDataCipher.DELETION, d.getString("_id"), d.getString("userAgentEnc"));
        assertEquals(256, storedUa.length(), "User-Agent kısaltılır");
        assertTrue(storedUa.startsWith("Mozilla/5.0 "));
        assertFalse(d.toJson().contains("Mozilla"));
        assertFalse(d.toJson().contains(ip), "ham IP saklanmaz");
        assertFalse(d.toJson().contains("6.6.6.6"));

        String masked = "u***@" + email.substring(email.indexOf('@') + 1).toLowerCase();
        String slack = slackMessageContaining(masked);
        assertNotNull(slack, "Slack bildirimi gelmeli");
        assertTrue(slack.contains("hesap silme talebi"), slack);
        assertFalse(slack.contains(email.toLowerCase()), "tam e-posta Slack'e gitmez");
        assertFalse(slack.contains("Artık kullanmıyorum"), "gerekçe Slack'e gitmez");
    }

    @Test
    void deletionRequestValidation() {
        String ip = newIp();
        assertEquals("invalid_email", post(DELETION, ip, body("email", "gecersiz")).error());
        assertEquals("invalid_email", post(DELETION, ip, body("email", "a b@example.com")).error());
        assertEquals("invalid_email", post(DELETION, ip, body("reason", "e-posta yok")).error());
        assertEquals(400, post(DELETION, ip, body("email", "x".repeat(250) + "@example.com")).status());

        String ip2 = newIp();
        Res tooLong = post(DELETION, ip2, body("email", newEmail(), "reason", "ç".repeat(1001)));
        assertEquals(400, tooLong.status());
        assertEquals("too_long", tooLong.error());
        Res badJson = post(DELETION, ip2, "{bozuk");
        assertEquals(400, badJson.status());
        assertEquals("bad_request", badJson.error());
        assertEquals(400, post(DELETION, ip2, "[]").status());

        String ip3 = newIp();
        Res huge = post(DELETION, ip3, body("email", newEmail(), "reason", "x".repeat(40_000)));
        assertEquals(413, huge.status());
        assertEquals("too_large", huge.error());
        assertEquals(415, post(DELETION, ip3, "email=a@b.co", "Content-Type", "application/x-www-form-urlencoded").status());
        // Bilinmeyen alanlar ve geçersiz dil yok sayılır
        String email = newEmail();
        assertEquals(202, post(DELETION, ip3, body("email", email, "locale", "<script>", "extra", 1)).status());
        assertNull(requestsFor(email).get(0).getString("locale"));
    }

    @Test
    void duplicatePendingRequestWithin24HoursReturnsSame202() throws Exception {
        String email = newEmail();
        Res first = post(DELETION, newIp(), body("email", email));
        Res second = post(DELETION, newIp(), body("email", email.toUpperCase(), "reason", "tekrar"));
        assertEquals(202, first.status());
        assertEquals(202, second.status());
        assertEquals(first.body(), second.body(), "yanıt aynı: e-posta varlığı anlaşılmaz");
        assertEquals(1, requestsFor(email).size(), "24 saatte tek bekleyen talep");

        // 24 saatten eski talep varsa yenisi yazılır
        mongo.getCollection(Stores.DELETION_REQUESTS).updateMany(new Document("email", email.toLowerCase()),
                new Document("$set", new Document("createdAt", new java.util.Date(System.currentTimeMillis() - 25L * 3600_000))));
        assertEquals(202, post(DELETION, newIp(), body("email", email)).status());
        assertEquals(2, requestsFor(email).size());
    }

    @Test
    void rateLimitedPerIpAndEndpoint() {
        String ip = newIp();
        for (int i = 0; i < 5; i++) assertEquals(202, post(DELETION, ip, body("email", newEmail())).status(), "istek " + (i + 1));
        Res limited = post(DELETION, ip, body("email", newEmail()));
        assertEquals(429, limited.status());
        assertEquals("rate_limited", limited.error());
        assertNotNull(limited.json().path("message").asText(null));
        assertTrue(Long.parseLong(limited.header("Retry-After")) > 0);
        // Geçersiz istekler de sayılır (sınır gövdeden önce)
        assertEquals(429, post(DELETION, ip, "{bozuk").status());

        assertEquals(202, post(DELETION, newIp(), body("email", newEmail())).status(), "başka IP etkilenmez");
        assertEquals(202, post(SUPPORT, ip, body("email", newEmail(), "subject", "Soru", "message", "Merhaba")).status(),
                "destek formunun kovası ayrı");
    }

    // ---------------------------------------------------------------- destek mesajı

    @Test
    void supportMessageIsStoredAndNotifiesSlack() throws Exception {
        String ip = newIp();
        String email = newEmail();
        String phoneUa = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36";
        Res r = post(SUPPORT, ip, body("email", email, "subject", "  Giriş\nsorunu ", "message", "Google ile giriş yapamıyorum.\nYardım?", "locale", "en"), "User-Agent", phoneUa);
        assertEquals(202, r.status(), r.body());
        assertEquals("received", r.json().path("status").asText());

        List<Document> docs = mongo.find(Query.query(Criteria.where("email").is(email.toLowerCase())), Document.class, Stores.SUPPORT_MESSAGES);
        assertEquals(1, docs.size());
        Document d = docs.get(0);
        assertEquals("Giriş sorunu", d.getString("subject"), "konu tek satır");
        assertEquals("Google ile giriş yapamıyorum.\nYardım?", d.getString("message"), "mesajın satırları korunur");
        assertEquals("en", d.getString("locale"));
        assertEquals("NEW", d.getString("status"));
        assertTrue(d.getString("ipHash").matches("[0-9a-f]{32}"));
        assertFalse(d.toJson().contains(ip));
        assertNull(d.get("userAgent"), "v49.2 tarayıcı bilgisi (telefon modeli) düz metin saklanmaz");
        assertFalse(d.toJson().contains("SM-S918B"));
        assertEquals(phoneUa, cipher.open(PersonalDataCipher.SUPPORT, d.getString("_id"), d.getString("userAgentEnc")));

        String masked = "u***@" + email.substring(email.indexOf('@') + 1).toLowerCase();
        String slack = slackMessageContaining(masked);
        assertNotNull(slack);
        assertTrue(slack.contains("Giriş sorunu"), slack);
        assertFalse(slack.contains("Yardım?"), "mesajın kendisi Slack'e gitmez");
    }

    @Test
    void supportMessageValidation() {
        String ip = newIp();
        assertEquals("invalid_email", post(SUPPORT, ip, body("email", "yok", "subject", "a", "message", "b")).error());
        assertEquals("validation", post(SUPPORT, ip, body("email", newEmail(), "subject", " ", "message", "b")).error());
        assertEquals("validation", post(SUPPORT, ip, body("email", newEmail(), "subject", "a")).error());
        String ip2 = newIp();
        assertEquals("too_long", post(SUPPORT, ip2, body("email", newEmail(), "subject", "k".repeat(151), "message", "b")).error());
        assertEquals("too_long", post(SUPPORT, ip2, body("email", newEmail(), "subject", "a", "message", "m".repeat(5001))).error());
    }

    // ---------------------------------------------------------------- CORS

    Res preflight(String path, String origin, String method, String headers) {
        return http.call("OPTIONS", path, null, "Origin", origin, "Access-Control-Request-Method", method,
                "Access-Control-Request-Headers", headers);
    }

    @Test
    void corsPreflightFromAllowedOrigin() {
        Res r = preflight("/api/v1/users/me", ALLOWED_ORIGIN, "DELETE", "authorization");
        assertEquals(200, r.status(), r.body());
        assertEquals(ALLOWED_ORIGIN, r.header("Access-Control-Allow-Origin"));
        assertTrue(r.header("Access-Control-Allow-Methods").contains("DELETE"), r.header("Access-Control-Allow-Methods"));
        assertTrue(r.header("Access-Control-Allow-Headers").toLowerCase().contains("authorization"));
        assertNull(r.header("Access-Control-Allow-Credentials"), "çerez yok");

        Res form = preflight(DELETION, "http://localhost:3000", "POST", "content-type");
        assertEquals(200, form.status());
        assertEquals("http://localhost:3000", form.header("Access-Control-Allow-Origin"));
    }

    @Test
    void corsPreflightFromConfiguredPreviewPattern() {
        // Test bağlamında kalıp: https://drawguidance-*.vercel.app
        String preview = "https://drawguidance-preview-ahmet.vercel.app";
        Res r = preflight(DELETION, preview, "POST", "content-type");
        assertEquals(200, r.status(), r.body());
        assertEquals(preview, r.header("Access-Control-Allow-Origin"));
        assertEquals(403, preflight(DELETION, "https://baskasi.vercel.app", "POST", "content-type").status(),
                "kalıba uymayan vercel.app adresi reddedilir");
        assertEquals(403, preflight(DELETION, "https://drawguidance.vercel.app", "POST", "content-type").status(),
                "eski öntanımlı adres artık izinli değil");
    }

    @Test
    void corsPreflightFromDisallowedOriginIsRejected() {
        Res r = preflight("/api/v1/users/me", "https://kotu.example", "DELETE", "authorization");
        assertEquals(403, r.status());
        assertNull(r.header("Access-Control-Allow-Origin"));
        Res put = preflight("/api/v1/users/me", ALLOWED_ORIGIN, "PUT", "authorization");
        assertEquals(403, put.status(), "yalnız GET, POST, DELETE");
        Res header = preflight(DELETION, ALLOWED_ORIGIN, "POST", "x-custom");
        assertEquals(403, header.status(), "yalnız Authorization ve Content-Type");
    }

    @Test
    void browserDeleteMeWithFirebaseTokenGetsCorsHeaders() {
        String uid = "web" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Res r = http.call("DELETE", "/api/v1/users/me", null, "Origin", ALLOWED_ORIGIN, "Authorization", "Bearer " + TestTokens.valid(uid));
        assertEquals(204, r.status(), r.body());
        assertEquals(ALLOWED_ORIGIN, r.header("Access-Control-Allow-Origin"));

        Res evil = http.call("DELETE", "/api/v1/users/me", null, "Origin", "https://kotu.example", "Authorization", "Bearer " + TestTokens.valid(uid));
        assertEquals(403, evil.status(), "izinsiz kökenden gelen tarayıcı isteği reddedilir");

        Res app = http.call("DELETE", "/api/v1/users/me", null, "Authorization", "Bearer " + TestTokens.valid(uid));
        assertEquals(204, app.status(), "Origin'siz (uygulama) istek etkilenmez");
        assertNull(app.header("Access-Control-Allow-Origin"));
    }
}
