package com.karapp.drawguidance.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.karapp.drawguidance.server.distribution.ContentService;
import com.karapp.drawguidance.server.distribution.ContentService.ItemInput;
import com.karapp.drawguidance.server.distribution.DeliveryService;
import com.karapp.drawguidance.server.distribution.Dist;
import com.karapp.drawguidance.server.distribution.DistributionWorker;
import com.karapp.drawguidance.server.distribution.cli.DistributionCli;
import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.TestTokens;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v43 içerik dağıtımı uçtan uca (Firebase yok): CLI ile içerik ve gönderim → işçi teslimatları açar → cihaz yoklar
 * (ETag/304, uzun yoklama) → bilgi alındı onayı → veri → teslim onayı. Paket sürümleri, güncelleme, yetki ve
 * şifreli cihaz bilgisi.
 */
class DistributionApiTest extends ApiContextTest {
    @Autowired MongoTemplate mongo;
    @Autowired DistributionWorker worker;
    @Autowired DistributionCli cli;
    @Autowired ContentService content;
    @Autowired DeliveryService deliveries;

    static String uid() { return "uid-" + UUID.randomUUID(); }

    static String deviceId() { return "dev-" + UUID.randomUUID().toString().replace("-", ""); }

    String[] auth(String uid, String device) {
        return new String[]{"Authorization", "Bearer " + TestTokens.valid(uid), "X-Device-Id", device};
    }

    Res register(String uid, String device, String extra) {
        String body = "{\"deviceId\":\"" + device + "\",\"appVersionCode\":42,\"appVersionName\":\"1.0.0\",\"model\":\"Pixel 8\","
                + "\"manufacturer\":\"Google\",\"sdkInt\":34,\"locale\":\"tr-TR\"" + (extra == null ? "" : "," + extra) + "}";
        return http.call("PUT", "/api/v1/devices/me", body, "Authorization", "Bearer " + TestTokens.valid(uid));
    }

    Res inbox(String uid, String device, String etag, Integer wait) {
        String path = "/api/v1/inbox" + (wait == null ? "" : "?wait=" + wait);
        String[] h = auth(uid, device);
        if (etag == null) return http.get(path, h);
        return http.get(path, h[0], h[1], h[2], h[3], "If-None-Match", etag);
    }

    Res ack(String uid, String device, String deliveryId, String stage) {
        return http.call("POST", "/api/v1/inbox/ack", "{\"items\":[{\"deliveryId\":\"" + deliveryId + "\",\"stage\":\"" + stage + "\"}]}", auth(uid, device));
    }

    /** Gelen kutusunda bu içeriğin öğeleri (başka testlerin "herkese" gönderimleri de gelebilir). */
    static java.util.List<JsonNode> of(Res inbox, String refId) {
        java.util.List<JsonNode> out = new java.util.ArrayList<>();
        for (JsonNode i : inbox.json().get("items")) if (i.get("refId").asText().equals(refId)) out.add(i);
        return out;
    }

    String runCli(String... argv) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int code = cli.run(argv, new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertEquals(0, code, out);
        return out;
    }

    static String id(String out, String prefix) {
        Matcher m = Pattern.compile("(" + prefix + "_[0-9a-f]{12})").matcher(out);
        assertTrue(m.find(), out);
        return m.group(1);
    }

    @Test
    void notificationFlowInfoThenData() throws Exception {
        String uid = uid();
        String dev = deviceId();
        assertEquals(200, register(uid, dev, null).status());
        Res first = inbox(uid, dev, null, null);
        assertEquals(200, first.status(), first.body());
        assertEquals(0, of(first, "yok").size());
        String etag = first.etag();
        assertNotNull(etag);
        assertEquals("900", first.header("X-Poll-After"));
        // Değişiklik yoksa 304 (gövdesiz)
        assertEquals(304, inbox(uid, dev, etag, null).status());

        String ntf = id(runCli("bildirim", "ekle", "--baslik", "Yeni fırçalar", "--aciklama", "Sulu boya fırçası eklendi"), "ntf");
        String dsp = id(runCli("gonder", "bildirim", ntf, "--kullanicilar", uid), "dsp");
        // Uygulama açık: uzun yoklama işçi teslimatı açınca uyanır
        CompletableFuture<Res> waiting = CompletableFuture.supplyAsync(() -> inbox(uid, dev, etag, 3));
        Thread.sleep(300);
        assertTrue(worker.tick().get("acilan") >= 1);
        Res woke = waiting.get(10, TimeUnit.SECONDS);
        assertEquals(200, woke.status(), woke.body());
        JsonNode item = of(woke, ntf).get(0);
        assertEquals("NOTIFICATION", item.get("kind").asText());
        assertEquals(ntf, item.get("refId").asText());
        assertEquals("INFO_SENT", item.get("stage").asText());
        assertFalse(item.get("dataReady").asBoolean());
        String delivery = item.get("deliveryId").asText();

        // Veri, bilgi alındı onayından önce verilmez
        assertEquals(409, http.get("/api/v1/inbox/" + delivery + "/data", auth(uid, dev)).status());
        Res ack = ack(uid, dev, delivery, "INFO_RECEIVED");
        assertEquals(200, ack.status(), ack.body());
        // Veri hazırlanabildiği için onayla birlikte gönderildi
        assertEquals("DATA_SENT", ack.json().get("results").get(delivery).asText());
        Res data = http.get("/api/v1/inbox/" + delivery + "/data", auth(uid, dev));
        assertEquals(200, data.status(), data.body());
        assertEquals("Yeni fırçalar", data.json().get("title").asText());
        assertEquals("Sulu boya fırçası eklendi", data.json().get("body").asText());
        assertEquals("DELIVERED", ack(uid, dev, delivery, "DELIVERED").json().get("results").get(delivery).asText());
        assertEquals(Map.of("DELIVERED", 1), deliveries.counts(dsp));
        // Teslim edilen artık gelen kutusunda yok
        assertEquals(0, of(inbox(uid, dev, null, null), ntf).size());
        String show = runCli("gonderim", "goster", dsp);
        assertTrue(show.contains("DELIVERED"), show);
    }

    @Test
    void longPollTimesOutWith304() {
        String uid = uid();
        String dev = deviceId();
        register(uid, dev, null);
        String etag = inbox(uid, dev, null, null).etag();
        long t0 = System.nanoTime();
        Res r = inbox(uid, dev, etag, 60);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(304, r.status());
        assertTrue(ms >= 2500 && ms < 8000, "en çok long-poll-max (3 sn) beklenir: " + ms);
    }

    @Test
    void packageVersionsAndInstalledSkip() {
        String uid = uid();
        String dev = deviceId();
        String other = uid();
        String otherDev = deviceId();
        register(uid, dev, null);
        // Diğer cihazda paketin 1. sürümü zaten kurulu
        String pkg = "uzay-" + UUID.randomUUID().toString().substring(0, 8);
        register(other, otherDev, "\"packages\":[{\"id\":\"" + pkg + "\",\"version\":1}]");
        content.addPackage("Uzay paketi", "COMIC", List.of(
                new ItemInput("objects", "Roket", "Uzay", "https://cdn.example.com/roket.webp", "roket"),
                new ItemInput("effects", "Yıldız tozu", null, "https://cdn.example.com/toz.webp", "toz"),
                new ItemInput("backgrounds", "Ay yüzeyi", "Uzay", "https://cdn.example.com/ay.webp", "ay")), false, pkg);
        // Yayımlanmamış paket gönderilemez
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(1, cli.run(new String[]{"gonder", "paket", pkg, "--herkes"}, new PrintStream(err, true, StandardCharsets.UTF_8)));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("yayımlanmamış"));
        runCli("paket", "yayinla", pkg);
        runCli("gonder", "paket", pkg, "--kullanicilar", uid + "," + other);
        worker.tick();
        assertEquals(0, of(inbox(other, otherDev, null, null), pkg).size(), "zaten kurulu olana gitmez");
        JsonNode item = of(inbox(uid, dev, null, null), pkg).get(0);
        assertEquals(1, item.get("version").asInt());
        String d1 = item.get("deliveryId").asText();
        ack(uid, dev, d1, "INFO_RECEIVED");
        JsonNode data = http.get("/api/v1/inbox/" + d1 + "/data", auth(uid, dev)).json();
        assertEquals("COMIC", data.get("mode").asText());
        assertEquals(3, data.get("items").size());
        assertEquals("objects", data.get("items").get(0).get("window").asText());
        assertEquals("https://cdn.example.com/roket.webp", data.get("items").get(0).get("url").asText());
        ack(uid, dev, d1, "DELIVERED");
        Document device = mongo.findOne(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("uid").is(uid)), Document.class, Dist.DEVICES);
        assertEquals(1, device.get("packages", Document.class).getInteger(pkg));

        // Paket güncellenince sürüm 2: yeniden gönderilir ve iki cihaza da gider
        String upd = runCli("paket", "guncelle", pkg, "--oge", "objects|Uydu|https://cdn.example.com/uydu.webp|Uzay");
        assertTrue(upd.contains("sürüm 2"), upd);
        runCli("gonder", "paket", pkg, "--herkes");
        worker.tick();
        java.util.List<JsonNode> again = of(inbox(uid, dev, null, null), pkg);
        assertEquals(1, again.size());
        assertEquals(2, again.get(0).get("version").asInt());
        assertEquals(1, of(inbox(other, otherDev, null, null), pkg).size());
        String show = runCli("paket", "goster", pkg);
        assertTrue(show.contains("Uydu") && show.contains("Nesneler"), show);
    }

    @Test
    void announcementWithGsUrlWaitsForStorage() {
        String uid = uid();
        String dev = deviceId();
        register(uid, dev, null);
        String ann = id(runCli("duyuru", "ekle", "--baslik", "Bahar kampanyası", "--aciklama", "Premium %50 indirimli",
                "--resim", "gs://dg-test-bucket/duyurular/bahar.webp"), "ann");
        runCli("gonder", "duyuru", ann, "--kullanicilar", uid);
        worker.tick();
        String d = of(inbox(uid, dev, null, null), ann).get(0).get("deliveryId").asText();
        // Hizmet hesabı yok: imzalı adres üretilemez → veri gönderilmez, işçi sonra yeniden dener
        assertEquals("INFO_RECEIVED", ack(uid, dev, d, "INFO_RECEIVED").json().get("results").get(d).asText());
        assertEquals(409, http.get("/api/v1/inbox/" + d + "/data", auth(uid, dev)).status());
        assertEquals(0, (int) worker.tick().get("veriHazir"));
        Document del = mongo.findById(d, Document.class, Dist.DELIVERIES);
        assertEquals("INFO_RECEIVED", del.getString("stage"));
        assertTrue(del.getString("lastError").contains("imzalanamadı"), del.toJson());
    }

    @Test
    void releaseNotifiesOnlyOlderDevicesAndVersionEndpoint() {
        String uid = uid();
        String oldDev = deviceId();
        String newDev = deviceId();
        register(uid, oldDev, null); // 42
        http.call("PUT", "/api/v1/devices/me", "{\"deviceId\":\"" + newDev + "\",\"appVersionCode\":999999}", "Authorization", "Bearer " + TestTokens.valid(uid));
        long code = 900_000 + (long) (Math.random() * 90_000);
        runCli("surum", "ekle", "--kod", String.valueOf(code), "--ad", "1.0." + code, "--ileti", "Yeni özellikler", "--en-eski", "40");
        worker.tick();
        JsonNode items = inbox(uid, oldDev, null, null).json().get("items");
        assertTrue(items.toString().contains("APP_UPDATE"), items.toString());
        assertFalse(inbox(uid, newDev, null, null).json().get("items").toString().contains(String.valueOf(code)), "yeni sürümdeki cihaza gitmez");
        String d = null;
        for (JsonNode i : items) if (i.get("refId").asText().equals(String.valueOf(code))) d = i.get("deliveryId").asText();
        assertNotNull(d);
        ack(uid, oldDev, d, "INFO_RECEIVED");
        JsonNode data = http.get("/api/v1/inbox/" + d + "/data", auth(uid, oldDev)).json();
        assertEquals(code, data.get("versionCode").asLong());
        assertEquals("Yeni özellikler", data.get("message").asText());
        JsonNode v = http.get("/api/v1/app/version").json();
        assertTrue(v.get("latestVersionCode").asLong() >= code);
        // Cihaz güncellenip yeniden kaydolunca bekleyen güncelleme iptal edilir
        http.call("PUT", "/api/v1/devices/me", "{\"deviceId\":\"" + oldDev + "\",\"appVersionCode\":" + code + "}", "Authorization", "Bearer " + TestTokens.valid(uid));
        assertFalse(inbox(uid, oldDev, null, null).json().get("items").toString().contains(String.valueOf(code)));
        // Paylaşılan veritabanı: sürüm kaydı ve gönderimi kaldırılır (AccountSyncApiTest ayardaki sürümü bekler)
        mongo.remove(org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is(code)), Dist.RELEASES);
        mongo.updateMulti(org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("refId").is(String.valueOf(code))),
                new org.springframework.data.mongodb.core.query.Update().set("status", "DONE"), Dist.DISPATCHES);
    }

    @Test
    void securityAndPrivacy() {
        String uid = uid();
        String dev = deviceId();
        register(uid, dev, null);
        String ntf = id(runCli("bildirim", "ekle", "--baslik", "Gizli", "--aciklama", "Yalnız sana"), "ntf");
        runCli("gonder", "bildirim", ntf, "--kullanicilar", uid);
        worker.tick();
        String d = of(inbox(uid, dev, null, null), ntf).get(0).get("deliveryId").asText();
        // Başka kullanıcı ya da başka cihaz bu teslimatı göremez/onaylayamaz
        String intruder = uid();
        String intruderDev = deviceId();
        register(intruder, intruderDev, null);
        assertEquals("NOT_FOUND", ack(intruder, intruderDev, d, "INFO_RECEIVED").json().get("results").get(d).asText());
        assertEquals(404, http.get("/api/v1/inbox/" + d + "/data", auth(intruder, intruderDev)).status());
        assertEquals("NOT_FOUND", ack(uid, deviceId(), d, "INFO_RECEIVED").json().get("results").get(d).asText());
        // Jetonsuz 401, cihaz kimliksiz 400, kayıtsız cihaz 404
        assertEquals(401, http.get("/api/v1/inbox", "X-Device-Id", dev).status());
        assertEquals(400, http.get("/api/v1/inbox", "Authorization", "Bearer " + TestTokens.valid(uid)).status());
        assertEquals(404, inbox(uid, deviceId(), null, null).status());
        assertEquals("INVALID_STAGE", ack(uid, dev, d, "X").json().get("results").get(d).asText());
        assertEquals(400, http.call("POST", "/api/v1/inbox/ack", "{\"items\":[]}", auth(uid, dev)).status());
        // Cihaz bilgileri şifreli: veritabanında model ve cihaz kimliği düz metin yok
        String raw = mongo.getCollection(Dist.DEVICES).find(new Document("uid", uid)).first().toJson();
        assertFalse(raw.contains("Pixel"), raw);
        assertFalse(raw.contains(dev), raw);
        String devices = runCli("cihaz", "listele", "--kullanici", uid);
        assertTrue(devices.contains("Google Pixel 8"), "CLI çözülmüş bilgiyi gösterir: " + devices);
        // Cihaz başka hesaba geçince eski hesabın bitmemiş teslimatı iptal
        register(intruder, dev, null);
        assertEquals("CANCELLED", mongo.findById(d, Document.class, Dist.DELIVERIES).getString("stage"));
    }

    @Test
    void cancelDispatchAndValidation() {
        String uid = uid();
        String dev = deviceId();
        register(uid, dev, null);
        String ntf = id(runCli("bildirim", "ekle", "--baslik", "İptal", "--aciklama", "Gönderilmeyecek"), "ntf");
        String dsp = id(runCli("gonder", "bildirim", ntf, "--herkes"), "dsp");
        worker.tick();
        runCli("gonderim", "iptal", dsp);
        assertFalse(inbox(uid, dev, null, null).json().get("items").toString().contains(ntf));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        assertEquals(1, cli.run(new String[]{"gonder", "bildirim", ntf}, out), "hedef verilmeli");
        assertEquals(1, cli.run(new String[]{"paket", "ekle", "--ad", "Kötü", "--mod", "ART", "--oge", "objects|x|https://a/b.png"}, out));
        assertEquals(1, cli.run(new String[]{"duyuru", "ekle", "--baslik", "x", "--aciklama", "y", "--resim", "http://duz.http/a.png"}, out));
        assertEquals(2, cli.run(new String[]{"bilinmeyen"}, out));
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("'ART' modunda 'objects' penceresi yok"));
    }

    @Test
    void cliItemsFromFileAndBaseSeederDryRun(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("ogeler.csv");
        Files.writeString(csv, "window,title,url,category\nshapes,\"Ok, kalın\",https://cdn.example.com/ok.png,Oklar\ndiagrams,Akış,https://cdn.example.com/akis.png,\n");
        String out = runCli("paket", "ekle", "--ad", "Not şekilleri", "--mod", "not", "--dosya", csv.toString());
        String pkg = id(out, "pkg");
        Document p = content.requirePackage(pkg);
        assertEquals("NOTES", p.getString("mode"));
        assertEquals("Ok, kalın", p.getList("items", Document.class).get(0).getString("title"));

        Path dir = tmp.resolve("taban");
        Files.createDirectories(dir.resolve("art/shapes"));
        Files.write(dir.resolve("art/shapes/kare.png"), new byte[]{1, 2, 3});
        Files.writeString(dir.resolve("manifest.json"), "{\"packages\":[{\"id\":\"base-art-test\",\"name\":\"Resim temel\",\"mode\":\"ART\","
                + "\"items\":[{\"id\":\"kare\",\"window\":\"shapes\",\"title\":\"Kare\",\"category\":\"Temel\",\"file\":\"art/shapes/kare.png\"}]}]}");
        String dry = runCli("taban-paket", "--klasor", dir.toString(), "--kuru");
        assertTrue(dry.contains("base-art-test (ART): 1 öğe"), dry);
        assertNull(mongo.findById("base-art-test", Document.class, Dist.PACKAGES), "kuru çalışma yazmaz");
        runCli("taban-paket", "--klasor", dir.toString());
        Document base = content.requirePackage("base-art-test");
        assertEquals(true, base.getBoolean("base"));
        assertEquals("PUBLISHED", base.getString("status"));
        assertEquals("gs://dg-test-bucket/base/base-art-test/art/shapes/kare.png", base.getList("items", Document.class).get(0).getString("url"));
        assertTrue(runCli("taban-paket", "--klasor", dir.toString()).contains("değişiklik yok"));
        // Klasör dışına çıkan dosya reddedilir
        Files.writeString(dir.resolve("manifest.json"), "{\"packages\":[{\"id\":\"base-x\",\"name\":\"x\",\"mode\":\"ART\",\"items\":[{\"window\":\"shapes\",\"title\":\"x\",\"file\":\"../ogeler.csv\"}]}]}");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(1, cli.run(new String[]{"taban-paket", "--klasor", dir.toString()}, new PrintStream(buf, true, StandardCharsets.UTF_8)));
    }

    @Autowired com.karapp.drawguidance.server.distribution.cli.BaseSeeder baseSeeder;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** v43 sunucuyla gelen taban paket listesi: geçerli, üç mod, pencereler modun pencereleri. */
    @Test
    void bundledBasePackagesAreValid() throws Exception {
        JsonNode root;
        try (var in = new org.springframework.core.io.ClassPathResource("base-packages/manifest.json").getInputStream()) {
            root = objectMapper.readTree(in);
        }
        assertEquals("dgpackets", root.path("bucket").asText());
        Map<String, String> modes = new java.util.HashMap<>();
        for (JsonNode p : root.path("packages")) {
            String mode = Dist.mode(p.path("mode").asText());
            modes.put(p.path("id").asText(), mode);
            java.util.Set<String> ids = new java.util.HashSet<>();
            assertTrue(p.path("items").size() > 50, "paket dolu: " + p.path("id").asText());
            for (JsonNode i : p.path("items")) {
                assertEquals(i.path("window").asText(), Dist.window(mode, i.path("window").asText()));
                assertTrue(ids.add(i.path("window").asText() + "/" + i.path("id").asText()), "yinelenen öğe " + i);
                assertFalse(i.path("title").asText().isBlank());
                assertFalse(i.path("file").asText().contains(".."));
            }
        }
        assertEquals(Map.of("base-comic", "COMIC", "base-notes", "NOTES", "base-art", "ART"), modes);
    }

    /** v43 açılış kaydı: eksik paket eklenir, aynısı tekrar işlenince sürüm değişmez, başka kovanın listesi atlanır. */
    @Test
    void bundledBasePackagesRegisterIdempotently() throws Exception {
        String id = "base-t" + UUID.randomUUID().toString().substring(0, 8);
        String manifest = "{\"bucket\":\"dg-test-bucket\",\"packages\":[{\"id\":\"" + id + "\",\"name\":\"Deneme\",\"mode\":\"NOTES\","
                + "\"items\":[{\"id\":\"a\",\"window\":\"shapes\",\"title\":\"Daire\",\"category\":\"Temel\",\"file\":\"notes/shapes/a.png\"},"
                + "{\"id\":\"b\",\"window\":\"diagrams\",\"title\":\"Akış\",\"file\":\"notes/diagrams/b.png\"}]}]}";
        PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        assertEquals(1, baseSeeder.registerBundled(objectMapper.readTree(manifest), out));
        Document d = mongo.findById(id, Document.class, Dist.PACKAGES);
        assertNotNull(d);
        assertEquals("PUBLISHED", d.getString("status"));
        assertEquals(Boolean.TRUE, d.getBoolean("base"));
        assertEquals(1, d.getInteger("version"));
        assertEquals("gs://dg-test-bucket/base/" + id + "/notes/shapes/a.png", d.getList("items", Document.class).get(0).getString("url"));
        // Aynı liste: dokunulmaz
        baseSeeder.registerBundled(objectMapper.readTree(manifest), out);
        assertEquals(1, mongo.findById(id, Document.class, Dist.PACKAGES).getInteger("version"));
        // v49.1: değişen başlık ama listede generatedAt yok (eski liste): var olan pakete dokunulmaz
        baseSeeder.registerBundled(objectMapper.readTree(manifest.replace("Daire", "Çember")), out);
        assertEquals(1, mongo.findById(id, Document.class, Dist.PACKAGES).getInteger("version"));
        // Değişen başlık ve paketten yeni liste: sürüm artar
        String newer = manifest.replace("Daire", "Çember").replace("{\"bucket\"", "{\"generatedAt\":\"" + java.time.Instant.now().plusSeconds(5) + "\",\"bucket\"");
        baseSeeder.registerBundled(objectMapper.readTree(newer), out);
        assertEquals(2, mongo.findById(id, Document.class, Dist.PACKAGES).getInteger("version"));
        assertEquals("Çember", mongo.findById(id, Document.class, Dist.PACKAGES).getList("items", Document.class).get(0).getString("title"));
        // Başka kova: atlanır
        assertEquals(0, baseSeeder.registerBundled(objectMapper.readTree(manifest.replace("dg-test-bucket", "baska-kova")), out));
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is(id)), Dist.PACKAGES);
    }
}
