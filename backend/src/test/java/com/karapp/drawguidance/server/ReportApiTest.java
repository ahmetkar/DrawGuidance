package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.privacy.PersonalDataCipher;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v37 hata/çökme raporu ucu ({@code POST /api/v1/reports}): kimlikli kayıt, alan denetimi (400/413/415), kısaltma,
 * kurulum başına sınır (test bağlamında 5), parmak izi ve sayaç. v40 giriş zorunlu: jetonsuz ya da geçersiz jetonlu
 * rapor 401 ile reddedilir; yeni rapor Slack'e kısmen gizlenmiş e-postayla bildirilir.
 */
class ReportApiTest extends ApiContextTest {
    static final String REPORTS = "/api/v1/reports";
    static final String STACK = """
            java.lang.IllegalStateException: Tuval yüklenemedi: 42
            \tat com.karapp.drawguidance.canvas.CanvasLoader.load(CanvasLoader.kt:120)
            \tat com.karapp.drawguidance.canvas.CanvasViewModel$open$1.invokeSuspend(CanvasViewModel.kt:88)
            \tat kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
            \tat kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)
            \tat android.os.Handler.handleCallback(Handler.java:958)
            \tat android.os.Looper.loop(Looper.java:294)
            Caused by: java.io.IOException: disk dolu
            \tat java.io.FileOutputStream.write(FileOutputStream.java:1)
            """;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    PersonalDataCipher cipher;

    private final ObjectMapper json = new ObjectMapper();

    static String installId() { return UUID.randomUUID().toString(); }

    /** Uygulamanın gönderdiği alanların hepsiyle bir rapor. */
    Map<String, Object> report(String installId) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("kind", "CRASH");
        b.put("message", "Tuval yüklenemedi: 42");
        b.put("stackTrace", STACK);
        b.put("feature", "canvas.editor");
        b.put("appVersion", "37.0.0");
        b.put("versionCode", 37000);
        b.put("sdk", 34);
        b.put("device", "samsung SM-A515F");
        b.put("language", "tr");
        b.put("installId", installId);
        b.put("deviceTime", 1760000000123L);
        b.put("note", "Kaydet'e basınca kapandı");
        b.put("breadcrumbs", List.of("12:00:01 Galeri açıldı", "12:00:05 Tuval açıldı (ART)", "12:00:09 Kaydet"));
        return b;
    }

    String text(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** v40 rapor ucu giriş ister: başlık verilmezse geçerli bir jeton eklenir. */
    Res post(Object body, String... headers) {
        String[] h = headers.length > 0 ? headers : new String[]{"Authorization", "Bearer " + TestTokens.valid("uid-reporter")};
        return http.call("POST", REPORTS, body instanceof String s ? s : text(body), h);
    }

    Res postAnonymous(Object body) { return http.call("POST", REPORTS, text(body)); }

    Document stored(String id) { return mongo.findById(id, Document.class, Stores.ERROR_REPORTS); }

    long count(String installId) { return mongo.count(Query.query(Criteria.where("installId").is(installId)), Stores.ERROR_REPORTS); }

    @Test
    void signedInReportIsStoredWithServerTime() {
        String install = installId();
        Instant before = Instant.now().minusSeconds(1);
        Res r = post(report(install.toUpperCase()));
        assertEquals(202, r.status(), r.body());
        JsonNode body = r.json();
        assertEquals(1, body.size(), "yanıt yalnız id taşır");
        String id = body.get("id").asText();
        assertEquals(id, UUID.fromString(id).toString());

        Document d = stored(id);
        assertNotNull(d);
        assertEquals("CRASH", d.get("kind"));
        assertEquals("Tuval yüklenemedi: 42", d.get("message"));
        assertEquals(STACK, d.get("stackTrace"));
        assertEquals("java.lang.IllegalStateException", d.get("exceptionType"), "yığın dökümünün ilk satırından");
        assertEquals("canvas.editor", d.get("feature"));
        assertEquals("37.0.0", d.get("appVersion"));
        assertEquals(37000L, ((Number) d.get("versionCode")).longValue());
        assertEquals(34, ((Number) d.get("sdk")).intValue());
        assertNull(d.get("device"), "v49.2 telefon modeli düz metin saklanmaz");
        assertTrue(d.getString("deviceEnc").startsWith("v1."), d.getString("deviceEnc"));
        assertFalse(d.toJson().contains("SM-A515F"), "modelin hiçbir parçası düz metin değil");
        assertEquals("samsung SM-A515F", cipher.open(PersonalDataCipher.REPORT, id, d.getString("deviceEnc")));
        assertNull(cipher.open(PersonalDataCipher.FEEDBACK, id, d.getString("deviceEnc")), "başka kayıt türüyle çözülmez");
        assertEquals("tr", d.get("language"));
        assertEquals(install, d.get("installId"), "küçük harfe çevrilir");
        assertEquals(new Date(1760000000123L), d.get("deviceTime"));
        assertEquals("Kaydet'e basınca kapandı", d.get("note"));
        assertEquals(List.of("12:00:01 Galeri açıldı", "12:00:05 Tuval açıldı (ART)", "12:00:09 Kaydet"), d.get("breadcrumbs"));
        assertEquals("valid", d.get("auth"));
        assertEquals("uid-reporter", d.get("uid"));
        assertNull(d.get("truncated"));
        assertEquals(32, d.getString("fingerprint").length());
        Instant received = ((Date) d.get("receivedAt")).toInstant();
        assertFalse(received.isBefore(before));
        assertFalse(received.isAfter(Instant.now().plusSeconds(1)));
        assertFalse(d.containsKey("ip"), "IP adresi saklanmaz");

        // Yalnız zorunlu alanlar ve not (USER)
        Map<String, Object> minimal = Map.of("kind", "user", "installId", install, "note", "Bir öneri");
        Res m = post(minimal);
        assertEquals(202, m.status(), m.body());
        Document md = stored(m.json().get("id").asText());
        assertEquals("USER", md.get("kind"));
        assertEquals("Bir öneri", md.get("note"));
        assertNull(md.get("stackTrace"));
        assertNull(md.get("deviceTime"));
        assertFalse(md.containsKey("breadcrumbs"));
    }

    @Test
    void validTokenAttachesTheUser() {
        String uid = "uid-" + UUID.randomUUID();
        Res r = post(report(installId()), "Authorization", "Bearer " + TestTokens.valid(uid));
        assertEquals(202, r.status(), r.body());
        Document d = stored(r.json().get("id").asText());
        assertEquals(uid, d.get("uid"));
        assertEquals("valid", d.get("auth"));
    }

    /** v40 giriş zorunlu: jetonsuz, süresi dolmuş/bozuk jetonlu ya da Basic başlıklı rapor 401 ile reddedilir, yazılmaz. */
    @Test
    void anonymousAndInvalidTokensAreRejected() {
        Instant now = Instant.now();
        String expired = TestTokens.mint(TestTokens.KEY, "uid-x", TestTokens.PROJECT, TestTokens.ISSUER,
                now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)));
        String install = installId();
        Res none = postAnonymous(report(install));
        assertEquals(401, none.status(), none.body());
        assertEquals("unauthorized", none.error());
        for (String header : new String[]{"Bearer garbage", "Bearer " + expired, "bearer a.b.c"}) {
            Res r = post(report(install), "Authorization", header);
            assertEquals(401, r.status(), header + " " + r.body());
            assertEquals("invalid_token", r.error(), header);
        }
        Res basic = post(report(install), "Authorization", "Basic Zm9vOmJhcg==");
        assertEquals(401, basic.status());
        assertEquals(0, count(install), "kimliksiz rapor yazılmaz");
    }

    /** v40 yeni rapor Slack'e: tür, istisna, kısmen gizlenmiş e-posta; ileti, yığın ve not gitmez; aynı hata saatte bir. */
    @Test
    void newReportIsSentToSlackWithoutPii() throws Exception {
        com.karapp.drawguidance.server.support.SlackStub slack = com.karapp.drawguidance.server.support.SlackStub.shared();
        slack.clear();
        Map<String, Object> b = report(installId());
        String unique = "com.karapp.drawguidance.slack.T" + UUID.randomUUID().toString().replace("-", "");
        b.put("stackTrace", STACK.replace("CanvasLoader", unique));
        Res r = post(b);
        assertEquals(202, r.status(), r.body());
        String id = r.json().get("id").asText();
        String body = null;
        // Önceki testlerin (arka planda giden) bildirimleri atlanır
        for (String m; (m = slack.next()) != null; ) if (m.contains(id)) { body = m; break; }
        assertNotNull(body, "Slack bildirimi gelmedi");
        assertTrue(body.contains("Yeni hata raporu (CRASH)"), body);
        assertTrue(body.contains("java.lang.IllegalStateException"), body);
        assertTrue(body.contains("a***@example.com"), body);
        assertFalse(body.contains("ahmet@example.com"), "e-posta açık yazılmaz");
        assertFalse(body.contains("Kaydet'e basınca kapandı"), "notun kendisi gitmez");
        assertFalse(body.contains("Tuval yüklenemedi"), "ileti gitmez");
        // Aynı hata (parmak izi) bir saat içinde yeniden bildirilmez
        Map<String, Object> again = new LinkedHashMap<>(b);
        again.put("installId", installId());
        Res second = post(again);
        assertEquals(202, second.status());
        String secondId = second.json().get("id").asText();
        for (String m; (m = slack.poll(700)) != null; ) assertFalse(m.contains(secondId), "aynı hata Slack'e ikinci kez gitmez");
    }

    @Test
    void invalidReportsAreRejected() {
        String install = installId();
        Map<String, String> codes = new LinkedHashMap<>();
        List<Object> bodies = new ArrayList<>();
        Map<String, Object> noKind = report(install);
        noKind.remove("kind");
        bodies.add(noKind);
        codes.put("tür yok", "invalid_kind");
        Map<String, Object> badKind = report(install);
        badKind.put("kind", "PANIC");
        bodies.add(badKind);
        codes.put("bilinmeyen tür", "invalid_kind");
        Map<String, Object> noInstall = report(install);
        noInstall.remove("installId");
        bodies.add(noInstall);
        codes.put("kurulum yok", "invalid_install_id");
        Map<String, Object> badInstall = report(install);
        badInstall.put("installId", "kurulum-1");
        bodies.add(badInstall);
        codes.put("kurulum UUID değil", "invalid_install_id");
        Map<String, Object> empty = new LinkedHashMap<>(Map.of("kind", "ERROR", "installId", install, "message", " ", "note", ""));
        bodies.add(empty);
        codes.put("boş rapor", "empty_report");
        Map<String, Object> badSdk = report(install);
        badSdk.put("sdk", 0);
        bodies.add(badSdk);
        codes.put("sdk 0", "validation");
        Map<String, Object> badVersion = report(install);
        badVersion.put("versionCode", -1);
        bodies.add(badVersion);
        codes.put("negatif sürüm kodu", "validation");
        Map<String, Object> badTime = report(install);
        badTime.put("deviceTime", -5);
        bodies.add(badTime);
        codes.put("negatif zaman", "validation");
        Map<String, Object> wrongType = report(install);
        wrongType.put("sdk", "otuz dört");
        bodies.add(wrongType);
        codes.put("sayı değil", "bad_request");
        Map<String, Object> crumbObjects = report(install);
        crumbObjects.put("breadcrumbs", List.of(Map.of("a", 1)));
        bodies.add(crumbObjects);
        codes.put("eylemler yazı değil", "bad_request");
        bodies.add("{\"kind\":");
        codes.put("bozuk JSON", "bad_request");
        bodies.add("[]");
        codes.put("dizi", "bad_request");
        bodies.add("null");
        codes.put("null", "bad_request");
        bodies.add("");
        codes.put("boş gövde", "bad_request");

        List<String> whys = new ArrayList<>(codes.keySet());
        for (int i = 0; i < bodies.size(); i++) {
            Res r = post(bodies.get(i));
            assertEquals(400, r.status(), whys.get(i) + ": " + r.body());
            assertEquals(codes.get(whys.get(i)), r.error(), whys.get(i));
            assertEquals(2, r.json().size(), "ortak hata gövdesi");
        }
        // JSON olmayan gövde türü
        Res plain = http.call("POST", REPORTS, text(report(install)), "Content-Type", "text/plain", "Authorization", "Bearer " + TestTokens.valid("uid-reporter"));
        assertEquals(415, plain.status());
        assertEquals("unsupported_media_type", plain.error());
        assertEquals(0, count(install), "geçersiz rapor yazılmaz");
        // Yalnız POST açık (v40 yol kimlik ister: jetonsuz 401, jetonla 403)
        for (String method : new String[]{"GET", "PUT", "DELETE"}) {
            Res r = http.call(method, REPORTS, null);
            assertEquals(401, r.status(), method);
            Res withToken = http.call(method, REPORTS, null, "Authorization", "Bearer " + TestTokens.valid("uid-reporter"));
            assertEquals(403, withToken.status(), method);
        }
    }

    @Test
    void tooLargeBodyIs413() {
        String install = installId();
        Map<String, Object> big = report(install);
        big.put("stackTrace", "x".repeat(300 * 1024));
        Res r = post(big);
        assertEquals(413, r.status(), r.body());
        assertEquals("too_large", r.error());
        assertEquals(0, count(install));
    }

    @Test
    void longFieldsAreTruncated() {
        String install = installId();
        Map<String, Object> b = report(install);
        String head = "java.lang.OutOfMemoryError: büyük\n";
        String tail = "\nCaused by: java.lang.RuntimeException: kök neden";
        b.put("stackTrace", head + "\tat a.b.C.d(C.kt:1)\n".repeat(6_000) + tail);
        b.put("message", "m".repeat(10_000));
        b.put("note", "n".repeat(5_000));
        b.put("feature", "f".repeat(500));
        List<String> crumbs = new ArrayList<>();
        for (int i = 0; i < 80; i++) crumbs.add("eylem " + i + (i == 79 ? " " + "u".repeat(1_000) : ""));
        b.put("breadcrumbs", crumbs);
        Res r = post(b);
        assertEquals(202, r.status(), r.body());
        Document d = stored(r.json().get("id").asText());
        String stack = d.getString("stackTrace");
        assertTrue(stack.length() <= 64_000, String.valueOf(stack.length()));
        assertTrue(stack.startsWith(head), "baş kalır");
        assertTrue(stack.endsWith(tail), "son (Caused by) kalır");
        assertTrue(stack.contains("karakter kesildi"));
        assertEquals(4_000, d.getString("message").length());
        assertTrue(d.getString("message").endsWith("…"));
        assertEquals(4_000, d.getString("note").length());
        assertEquals(100, d.getString("feature").length());
        @SuppressWarnings("unchecked")
        List<String> kept = (List<String>) d.get("breadcrumbs");
        assertEquals(50, kept.size(), "son 50 eylem");
        assertEquals("eylem 30", kept.get(0));
        assertEquals(300, kept.get(49).length());
        assertEquals(List.of("message", "stackTrace", "note", "feature", "breadcrumbs"), d.get("truncated"));
        assertEquals("java.lang.OutOfMemoryError", d.get("exceptionType"));
    }

    @Test
    void perInstallRateLimit() {
        String install = installId();
        for (int i = 0; i < 5; i++) assertEquals(202, post(report(install)).status(), "rapor " + i);
        Res limited = post(report(install));
        assertEquals(429, limited.status(), limited.body());
        assertEquals("rate_limited", limited.error());
        long retry = Long.parseLong(limited.header("Retry-After"));
        assertTrue(retry >= 1 && retry <= 3600, String.valueOf(retry));
        assertEquals(5, count(install));
        // Başka kurulum etkilenmez
        assertEquals(202, post(report(installId())).status());
    }

    /** Aynı hata (satır numarası ve ileti farklı) tek parmak izinde sayılır; farklı hata ayrı. */
    @Test
    void sameCrashIsGroupedWithACount() {
        Map<String, Object> first = report(installId());
        first.put("feature", "test." + UUID.randomUUID());
        String unique = "com.karapp.drawguidance.test.T" + UUID.randomUUID().toString().replace("-", "");
        first.put("stackTrace", STACK.replace("CanvasLoader", unique));
        Map<String, Object> second = new LinkedHashMap<>(first);
        second.put("installId", installId());
        second.put("message", "Tuval yüklenemedi: 7");
        // Satır numarası, derleyici adı ($1 → $2) ve iletideki sayı farklı
        second.put("stackTrace", STACK.replace("CanvasLoader.kt:120", "CanvasLoader.kt:131").replace("$open$1", "$open$2")
                .replace("42", "7").replace("CanvasLoader", unique));
        Map<String, Object> other = new LinkedHashMap<>(first);
        other.put("stackTrace", STACK.replace("CanvasLoader", unique).replace("IllegalStateException", "IllegalArgumentException"));

        Res a = post(first), b = post(second), c = post(other);
        assertEquals(202, a.status());
        assertEquals(202, b.status());
        assertEquals(202, c.status());
        String fa = stored(a.json().get("id").asText()).getString("fingerprint");
        String fb = stored(b.json().get("id").asText()).getString("fingerprint");
        String fc = stored(c.json().get("id").asText()).getString("fingerprint");
        assertEquals(fa, fb);
        assertNotEquals(fa, fc);
        Document group = mongo.findById(fa, Document.class, Stores.ERROR_REPORT_GROUPS);
        assertNotNull(group);
        assertEquals(2, ((Number) group.get("count")).intValue());
        assertEquals(b.json().get("id").asText(), group.get("lastReportId"));
        assertEquals("CRASH", group.get("kind"));
        assertEquals("java.lang.IllegalStateException", group.get("exceptionType"));
        assertTrue(!((Date) group.get("lastSeen")).before((Date) group.get("firstSeen")));
        assertEquals(1, ((Number) mongo.findById(fc, Document.class, Stores.ERROR_REPORT_GROUPS).get("count")).intValue());
    }
}
