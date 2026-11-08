package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.distribution.DeviceCrypto;
import com.karapp.drawguidance.server.distribution.cli.DistributionCli;
import com.karapp.drawguidance.server.feedback.FeedbackService;
import com.karapp.drawguidance.server.privacy.DeviceDataMigration;
import com.karapp.drawguidance.server.privacy.PersonalDataCipher;
import com.karapp.drawguidance.server.report.ErrorReportRequest;
import com.karapp.drawguidance.server.report.ErrorReportService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v49.2 telefon modeli ve tarayıcı bilgisi veritabanında şifreli: yeni kayıtlar şifreli yazılır, eski düz metin kayıtlar
 * açılışta şifreli alana taşınır (yalnız kalıcı anahtarla), CLI şifreyi çözerek gösterir.
 */
class V49DeviceDataEncryptionTest extends ApiContextTest {
    static final String MODEL = "samsung SM-A515F";
    static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36";

    @Autowired MongoTemplate mongo;
    @Autowired PersonalDataCipher cipher;
    @Autowired DeviceDataMigration migration;
    @Autowired FeedbackService feedback;
    @Autowired ErrorReportService reports;
    @Autowired DistributionCli cli;

    static String id() { return UUID.randomUUID().toString(); }

    record Out(int code, String text) {}

    Out cli(String... argv) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int code = cli.run(argv, new PrintStream(buf, true, StandardCharsets.UTF_8));
        return new Out(code, buf.toString(StandardCharsets.UTF_8));
    }

    String report(String device) {
        ErrorReportRequest b = new ErrorReportRequest("CRASH", "Tuval yüklenemedi", "java.lang.IllegalStateException: x\n\tat a.B.c(B.kt:1)",
                "canvas.editor", "49.2.0", 49L, 34, device, "tr", UUID.randomUUID().toString(), null, "Kaydet'e basınca", List.of("Galeri açıldı"), null);
        return reports.store(ErrorReportService.normalize(b), "uid-v49-2", "valid").id();
    }

    String feedback(String model) {
        return feedback.submit("uid-v49-2", "ahmet@example.com", new FeedbackService.Body("ART", "art.layers", 5, "Katmanlar güzel", true,
                new FeedbackService.Device(model, "14", "49.2.0", 49L, "tr-TR")));
    }

    // ---------------------------------------------------------------- yeni kayıtlar

    @Test
    void newReportStoresModelOnlyEncrypted() {
        String id = report(MODEL);
        Document d = mongo.findById(id, Document.class, Stores.ERROR_REPORTS);
        assertNull(d.get("device"));
        assertFalse(d.toJson().contains("SM-A515F"));
        String sealed = d.getString("deviceEnc");
        assertEquals(MODEL, cipher.open(PersonalDataCipher.REPORT, id, sealed));
        assertNull(cipher.open(PersonalDataCipher.REPORT, id(), sealed), "başka belgeye kopyalanırsa çözülmez");
        assertEquals("49.2.0", d.getString("appVersion"), "sürüm sorgu için düz kalır");
        assertEquals(34, d.getInteger("sdk"));

        // Aynı model iki kez şifrelenince farklı çıkar (rastgele IV): şifreli değerden model tahmin edilemez
        String other = mongo.findById(report(MODEL), Document.class, Stores.ERROR_REPORTS).getString("deviceEnc");
        assertFalse(sealed.equals(other));

        Document none = mongo.findById(report("   "), Document.class, Stores.ERROR_REPORTS);
        assertFalse(none.containsKey("deviceEnc"), "cihaz yoksa şifreli alan da yok");
        assertFalse(none.containsKey("device"));
    }

    @Test
    void newFeedbackStoresModelOnlyEncryptedKeepsOtherDeviceFields() {
        String id = feedback(MODEL);
        Document d = mongo.findById(id, Document.class, Stores.FEEDBACK);
        Document dev = d.get("device", Document.class);
        assertNull(dev.get("model"));
        assertFalse(d.toJson().contains("SM-A515F"));
        assertEquals(MODEL, cipher.open(PersonalDataCipher.FEEDBACK, id, dev.getString("modelEnc")));
        assertNull(cipher.open(PersonalDataCipher.REPORT, id, dev.getString("modelEnc")), "başka kayıt türüyle çözülmez");
        assertEquals("14", dev.getString("androidVersion"));
        assertEquals("49.2.0", dev.getString("appVersion"));
        assertEquals("tr-TR", dev.getString("locale"));

        Document blank = mongo.findById(feedback(" \u0007 "), Document.class, Stores.FEEDBACK).get("device", Document.class);
        assertFalse(blank.containsKey("modelEnc"), "boş model saklanmaz");
        assertEquals("14", blank.getString("androidVersion"));
    }

    // ---------------------------------------------------------------- eski kayıtların taşınması

    @Test
    void keyStatus() {
        assertFalse(DeviceCrypto.ephemeral().persistent(), "geçici anahtar");
        assertTrue(DeviceCrypto.fromBase64(DeviceCrypto.newKey()).persistent(), "ayarlardan gelen anahtar");
        assertFalse(cipher.persistent(), "test bağlamında DEVICE_DATA_KEY yok");
    }

    @Test
    void oldPlainRecordsAreEncryptedOnceAndOnlyWithPersistentKey() {
        String rep = id(), fb = id(), sup = id(), del = id(), noDevice = id();
        Date now = new Date();
        mongo.insert(new Document("_id", rep).append("kind", "CRASH").append("device", MODEL).append("receivedAt", now), Stores.ERROR_REPORTS);
        mongo.insert(new Document("_id", noDevice).append("kind", "USER").append("receivedAt", now), Stores.ERROR_REPORTS);
        mongo.insert(new Document("_id", fb).append("mode", "ART").append("rating", 4).append("createdAt", now)
                .append("device", new Document("model", MODEL).append("androidVersion", "13").append("locale", "tr")), Stores.FEEDBACK);
        mongo.insert(new Document("_id", sup).append("email", "a@example.com").append("userAgent", UA).append("createdAt", now), Stores.SUPPORT_MESSAGES);
        mongo.insert(new Document("_id", del).append("email", "b@example.com").append("userAgent", UA).append("createdAt", now), Stores.DELETION_REQUESTS);

        // Geçici anahtarla açılış: hiçbir şey değişmez (yoksa yeniden başlatınca okunamazdı)
        migration.run(null);
        assertEquals(MODEL, mongo.findById(rep, Document.class, Stores.ERROR_REPORTS).getString("device"));
        assertEquals(UA, mongo.findById(sup, Document.class, Stores.SUPPORT_MESSAGES).getString("userAgent"));

        // CLI şifreleme öncesi kaydı da gösterir
        assertTrue(cli("hata", "goster", rep).text().contains("Telefon: " + MODEL));

        Map<String, Integer> done = migration.migrate();
        assertTrue(done.get(Stores.ERROR_REPORTS + ".device") >= 1, done.toString());
        assertTrue(done.get(Stores.FEEDBACK + ".device.model") >= 1, done.toString());
        assertTrue(done.get(Stores.SUPPORT_MESSAGES + ".userAgent") >= 1, done.toString());
        assertTrue(done.get(Stores.DELETION_REQUESTS + ".userAgent") >= 1, done.toString());

        Document r = mongo.findById(rep, Document.class, Stores.ERROR_REPORTS);
        assertNull(r.get("device"));
        assertEquals(MODEL, cipher.open(PersonalDataCipher.REPORT, rep, r.getString("deviceEnc")));
        assertEquals("CRASH", r.getString("kind"), "diğer alanlar korunur");

        Document f = mongo.findById(fb, Document.class, Stores.FEEDBACK);
        Document dev = f.get("device", Document.class);
        assertNull(dev.get("model"));
        assertEquals(MODEL, cipher.open(PersonalDataCipher.FEEDBACK, fb, dev.getString("modelEnc")));
        assertEquals("13", dev.getString("androidVersion"), "cihazın diğer alanları korunur");
        assertEquals(4, f.getInteger("rating"));

        for (String[] c : new String[][]{{sup, Stores.SUPPORT_MESSAGES, PersonalDataCipher.SUPPORT}, {del, Stores.DELETION_REQUESTS, PersonalDataCipher.DELETION}}) {
            Document m = mongo.findById(c[0], Document.class, c[1]);
            assertNull(m.get("userAgent"), c[1]);
            assertFalse(m.toJson().contains("SM-S918B"), c[1]);
            assertEquals(UA, cipher.open(c[2], c[0], m.getString("userAgentEnc")), c[1]);
        }
        Document untouched = mongo.findById(noDevice, Document.class, Stores.ERROR_REPORTS);
        assertFalse(untouched.containsKey("deviceEnc"), "cihazı olmayan kayda alan eklenmez");

        // İkinci kez: yapacak iş yok, şifreli değer değişmez
        String sealed = r.getString("deviceEnc");
        assertTrue(migration.migrate().values().stream().allMatch(n -> n == 0));
        assertEquals(sealed, mongo.findById(rep, Document.class, Stores.ERROR_REPORTS).getString("deviceEnc"));
    }

    // ---------------------------------------------------------------- CLI

    @Test
    void cliShowsDecryptedDeviceData() {
        String fb = feedback(MODEL);
        String rep = report(MODEL);

        Out list = cli("geri-bildirim", "listele", "--mod", "art", "--sayi", "500");
        assertEquals(0, list.code(), list.text());
        assertTrue(list.text().contains(fb) && list.text().contains(MODEL), list.text());
        Out show = cli("geri-bildirim", "goster", fb);
        assertEquals(0, show.code(), show.text());
        assertTrue(show.text().contains("Telefon: " + MODEL) && show.text().contains("Android: 14") && show.text().contains("Katmanlar güzel"), show.text());
        assertTrue(show.text().contains("ahmet@example.com"));

        Out reps = cli("hata", "listele", "--tur", "crash", "--sayi", "500");
        assertEquals(0, reps.code(), reps.text());
        assertTrue(reps.text().contains(rep) && reps.text().contains(MODEL), reps.text());
        Out rshow = cli("hata", "göster", rep);
        assertEquals(0, rshow.code(), rshow.text());
        assertTrue(rshow.text().contains("Telefon: " + MODEL) && rshow.text().contains("Kaydet'e basınca") && rshow.text().contains("Galeri açıldı")
                && rshow.text().contains("at a.B.c"), rshow.text());
        Out groups = cli("hata", "gruplar", "--sayi", "500");
        assertEquals(0, groups.code(), groups.text());
        assertTrue(groups.text().contains("kez"), groups.text());

        // Şifreli değer başka belgeye kopyalanırsa çözülmez
        String copy = id();
        String sealed = mongo.findById(rep, Document.class, Stores.ERROR_REPORTS).getString("deviceEnc");
        mongo.insert(new Document("_id", copy).append("kind", "ERROR").append("deviceEnc", sealed).append("receivedAt", new Date()), Stores.ERROR_REPORTS);
        assertTrue(cli("hata", "goster", copy).text().contains("Telefon: (çözülemedi)"));
    }

    @Test
    void cliShowsWebFormsWithDecryptedBrowser() {
        String sup = id(), del = id();
        mongo.insert(new Document("_id", sup).append("email", "destek@example.com").append("subject", "Giriş sorunu").append("message", "Giremiyorum")
                .append("status", "NEW").append("createdAt", new Date()).append("userAgentEnc", cipher.seal(PersonalDataCipher.SUPPORT, sup, UA)), Stores.SUPPORT_MESSAGES);
        mongo.insert(new Document("_id", del).append("email", "silme@example.com").append("reason", "Kullanmıyorum")
                .append("status", "PENDING").append("createdAt", new Date()).append("userAgentEnc", cipher.seal(PersonalDataCipher.DELETION, del, UA)), Stores.DELETION_REQUESTS);

        Out s = cli("destek", "goster", sup);
        assertEquals(0, s.code(), s.text());
        assertTrue(s.text().contains("Tarayıcı: " + UA) && s.text().contains("Giremiyorum"), s.text());
        assertTrue(cli("destek", "listele", "--sayi", "500").text().contains("destek@example.com"));
        Out d = cli("silme-talebi", "goster", del);
        assertTrue(d.text().contains("Tarayıcı: " + UA) && d.text().contains("Kullanmıyorum"), d.text());
        assertTrue(cli("silme-talebi", "listele", "--sayi", "500").text().contains("silme@example.com"));
    }

    @Test
    void cliErrors() {
        Out bad = cli("hata", "listele", "--sayi", "abc");
        assertEquals(1, bad.code());
        assertTrue(bad.text().contains("--sayi"), bad.text());
        assertEquals(1, cli("geri-bildirim", "listele", "--sayi", "0").code());
        assertEquals(1, cli("hata", "goster", id()).code(), "olmayan kayıt");
        Out usage = cli("destek", "sil");
        assertEquals(1, usage.code());
        assertTrue(usage.text().contains("destek listele"), usage.text());
        assertTrue(cli("yardim").text().contains("geri-bildirim listele"));
    }
}
