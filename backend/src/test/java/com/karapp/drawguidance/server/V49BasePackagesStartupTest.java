package com.karapp.drawguidance.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karapp.drawguidance.server.distribution.ContentService;
import com.karapp.drawguidance.server.distribution.ContentService.ItemInput;
import com.karapp.drawguidance.server.distribution.Dist;
import com.karapp.drawguidance.server.distribution.cli.BaseSeeder;
import com.karapp.drawguidance.server.distribution.cli.DistributionCliAccess;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v49.1 açılıştaki taban paket kaydı, CLI ile yapılan değişiklikleri geri almaz: var olan paket yalnız sunucuyla gelen
 * liste ondan yeniyse ({@code generatedAt} &gt; {@code updatedAt}) değişir. Gerçek MongoDB ile (Testcontainers).
 */
class V49BasePackagesStartupTest extends ApiContextTest {
    static final String BUCKET = "dg-test-bucket"; // ApiContextTest'teki kova

    @Autowired BaseSeeder seeder;
    @Autowired ContentService content;
    @Autowired MongoTemplate mongo;
    @Autowired ObjectMapper json;

    static String pkg() { return "base-t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }

    /** Sunucuyla gelen liste: tek paket, {@code n} nesne öğesi; {@code generatedAt} null ise alan yok (eski liste). */
    ObjectNode list(String id, int n, Instant generatedAt) {
        ObjectNode root = json.createObjectNode();
        root.put("bucket", BUCKET);
        if (generatedAt != null) root.put(BaseSeeder.GENERATED_AT, generatedAt.toString());
        ObjectNode p = root.putArray("packages").addObject();
        p.put("id", id).put("name", "Taban test").put("mode", "COMIC");
        ArrayNode items = p.putArray("items");
        for (int i = 0; i < n; i++) {
            items.addObject().put("id", "o" + i).put("window", "objects").put("title", "Nesne " + i).put("file", "comic/objects/o" + i + ".webp");
        }
        return root;
    }

    String register(JsonNode root) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            assertEquals(1, seeder.registerBundled(root, out));
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    Document doc(String id) { return mongo.findById(id, Document.class, Dist.PACKAGES); }

    int count(String id) { return doc(id).getList("items", Document.class).size(); }

    /** CLI'nin "paket guncelle <id> --dosya" yaptığı gibi bütün öğeleri değiştirir. */
    void cliReplace(String id, int n) {
        List<ItemInput> items = new ArrayList<>();
        for (int i = 0; i < n; i++) items.add(new ItemInput("objects", "Yeni " + i, null, "gs://" + BUCKET + "/base/" + id + "/comic/objects/n" + i + ".webp", "n" + i));
        content.updatePackage(id, null, null, null, items);
    }

    @Test
    void missingPackageIsAddedAndPublishedEvenFromAnOldList() {
        String id = pkg();
        String out = register(list(id, 3, null));
        Document d = doc(id);
        assertNotNull(d, "eksik paket eklenir");
        assertEquals(3, count(id));
        assertEquals("PUBLISHED", d.getString("status"));
        assertTrue(Boolean.TRUE.equals(d.getBoolean("base")));
        assertTrue(out.contains("kaydedildi ve yayımlandı"), out);
    }

    @Test
    void restartWithAnOlderListDoesNotRevertTheCliUpdate() {
        // Ahmet'in durumu: sunucu eski listeyle (1723) kurulu, CLI ile yeni öğeler (1821) yazıldı, sunucu yeniden başladı.
        String id = pkg();
        Instant oldListTime = Instant.now().minusSeconds(3600);
        register(list(id, 5, oldListTime));
        cliReplace(id, 7);
        int version = doc(id).getInteger("version");
        Date updatedAt = doc(id).getDate("updatedAt");

        String out = register(list(id, 5, oldListTime)); // yeniden başlama
        assertEquals(7, count(id), "CLI ile yazılan öğeler korunur");
        assertEquals(version, doc(id).getInteger("version"), "sürüm artmaz");
        assertEquals(updatedAt, doc(id).getDate("updatedAt"));
        assertTrue(out.contains("dokunulmadı (veritabanındaki paket listeden yeni; 7 öğe korunur)"), out);

        for (int i = 0; i < 3; i++) register(list(id, 5, oldListTime)); // her yeniden başlamada aynı
        assertEquals(7, count(id));
        assertEquals(version, doc(id).getInteger("version"));
    }

    @Test
    void listWithoutGeneratedAtNeverTouchesAnExistingPackage() {
        String id = pkg();
        register(list(id, 2, null));
        cliReplace(id, 4);
        int version = doc(id).getInteger("version");
        String out = register(list(id, 2, null));
        assertEquals(4, count(id));
        assertEquals(version, doc(id).getInteger("version"));
        assertTrue(out.contains("dokunulmadı (listede generatedAt yok; veritabanındaki 4 öğe korunur)"), out);
    }

    @Test
    void newerListReplacesOnceAndRestartsAreIdempotent() {
        String id = pkg();
        register(list(id, 2, null));
        int v1 = doc(id).getInteger("version");
        Instant newer = Instant.now().plusSeconds(5);
        String out = register(list(id, 6, newer)); // yeni sürüm dağıtıldı: liste paketten yeni
        assertEquals(6, count(id), "yeni liste uygulanır");
        assertEquals(v1 + 1, doc(id).getInteger("version"), "yayımlanmış paketin sürümü bir artar");
        assertTrue(out.contains("güncellendi"), out);

        int v2 = doc(id).getInteger("version");
        String again = register(list(id, 6, newer)); // aynı imaj yeniden başladı
        assertEquals(6, count(id));
        assertEquals(v2, doc(id).getInteger("version"), "aynı listeyle sürüm artmaz");
        assertFalse(again.contains("güncellendi"), again);
    }

    @Test
    void cliUpdateAfterANewerListIsAlsoKept() throws InterruptedException {
        String id = pkg();
        register(list(id, 2, null));
        Instant listTime = Instant.now().plusMillis(50);
        register(list(id, 6, listTime)); // yeni imaj listesi uygulandı
        assertEquals(6, count(id));
        Thread.sleep(120);
        cliReplace(id, 9); // sonra CLI ile değiştirildi
        assertTrue(doc(id).getDate("updatedAt").toInstant().isAfter(listTime));
        int version = doc(id).getInteger("version");
        String out = register(list(id, 6, listTime)); // aynı imaj yeniden başladı
        assertEquals(9, count(id), "CLI değişikliği korunur");
        assertEquals(version, doc(id).getInteger("version"));
        assertTrue(out.contains("dokunulmadı"), out);
    }

    @Test
    void otherBucketIsSkipped() {
        String id = pkg();
        ObjectNode root = list(id, 2, Instant.now());
        root.put("bucket", "baska-kova");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            assertEquals(0, seeder.registerBundled(root, out));
        }
        assertNull(doc(id));
    }

    @Test
    void decisionRules() {
        Instant t = Instant.parse("2026-11-03T18:23:44.684Z");
        Date at = Date.from(t);
        assertFalse(DistributionCliAccess.bundledMayReplace(at, null), "eski liste (generatedAt yok) asla");
        assertFalse(DistributionCliAccess.bundledMayReplace(at, t.minusSeconds(1)), "eski liste");
        assertFalse(DistributionCliAccess.bundledMayReplace(at, t), "aynı an: değiştirmez");
        assertTrue(DistributionCliAccess.bundledMayReplace(at, t.plusMillis(1)), "yeni liste");
        assertTrue(DistributionCliAccess.bundledMayReplace(null, t), "değişiklik anı olmayan çok eski kayıt");
        assertFalse(DistributionCliAccess.bundledMayReplace(null, null));
    }

    @Test
    void generatedAtParsingAndStamping() {
        ObjectNode root = json.createObjectNode();
        assertNull(DistributionCliAccess.generatedAt(root));
        root.put(BaseSeeder.GENERATED_AT, "dün");
        assertNull(DistributionCliAccess.generatedAt(root), "okunamayan değer yok sayılır");
        Instant now = Instant.parse("2026-11-03T19:30:00Z");
        JsonNode stamped = DistributionCliAccess.stampBundled(root, "dgpackets", now);
        assertEquals("dgpackets", stamped.path("bucket").asText());
        assertEquals("2026-11-03T19:30:00Z", stamped.path(BaseSeeder.GENERATED_AT).asText());
        assertEquals(now, DistributionCliAccess.generatedAt(stamped));
    }

    @Test
    void bundledResourceManifestCarriesGeneratedAt() throws Exception {
        // Sunucuyla gelen liste yeni kurala göre işaretli olmalı; yoksa var olan paketler hiç güncellenmez.
        try (var in = getClass().getClassLoader().getResourceAsStream("base-packages/manifest.json")) {
            assertNotNull(in);
            JsonNode root = json.readTree(in);
            assertNotNull(DistributionCliAccess.generatedAt(root), "base-packages/manifest.json içinde generatedAt olmalı");
            for (JsonNode p : root.path("packages")) {
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (JsonNode i : p.path("items")) assertTrue(ids.add(i.path("id").asText()), "yinelenen öğe kimliği: " + i.path("id").asText());
            }
        }
    }
}
