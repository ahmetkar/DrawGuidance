package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.catalog.CatalogMeta;
import com.karapp.drawguidance.server.catalog.CatalogSeeder;
import com.karapp.drawguidance.server.catalog.CatalogSeeder.Outcome;
import com.karapp.drawguidance.server.catalog.CatalogSeeder.Result;
import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.karapp.drawguidance.server.support.Http;
import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.TestMongo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Katalog tohumlamasının yaşam döngüsü (ayrı veritabanı {@code dg_seed}, sırayla): sunucu dosyasız açılır (boş
 * katalog, sürüm 0) → dosya gelir (1000) → sürüm artar (1001: değişen güncellenir, eklenen eklenir, dosyada olmayan
 * silinir) → eski dosya geri alınmaz → dosya silinince katalog korunur → boşaltılan koleksiyonlar yeniden dolar →
 * dört "sunucu" aynı anda eşitler (tek eşitleme) → çökmüş sunucunun kirası devralınır → bozuk dosya yok sayılır.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogSeedingTest {
    static final Path DIR;
    static final Path SEED;

    static {
        try {
            DIR = Files.createTempDirectory("dg-catalog");
            SEED = DIR.resolve("catalog.json.gz");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> TestMongo.url("dg_seed"));
        r.add("drawguidance.catalog.seed-resource", () -> "file:" + SEED.toAbsolutePath());
        r.add("drawguidance.mongo.min-pool-size", () -> "1");
    }

    @LocalServerPort
    int port;
    @Autowired
    CatalogSeeder seeder;
    @Autowired
    MongoTemplate mongo;
    @Autowired
    ObjectMapper json;
    @Autowired
    ResourceLoader resources;
    @Autowired
    DrawGuidanceProperties props;

    Http http;

    @BeforeEach
    void http() { http = new Http(port); }

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(SEED);
        Files.deleteIfExists(DIR);
    }

    ObjectNode fixture() throws IOException {
        try (InputStream in = new GZIPInputStream(getClass().getResourceAsStream("/catalog/test-catalog.json.gz"))) {
            return (ObjectNode) json.readTree(in);
        }
    }

    void writeSeed(JsonNode catalog) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(SEED))) {
            json.writeValue(out, catalog);
        }
    }

    Result sync() { return seeder.sync(new FileSystemResource(SEED)); }

    JsonNode manifest() {
        Res r = http.get("/api/v1/catalog/manifest");
        assertEquals(200, r.status(), r.body());
        return r.json();
    }

    List<String> keys(String path, String keyField) {
        List<String> out = new ArrayList<>();
        for (JsonNode i : http.get(path).json().get("items")) out.add(i.get(keyField).asText());
        return out;
    }

    static ObjectNode find(JsonNode array, String keyField, String key) {
        for (JsonNode i : array) if (i.path(keyField).asText().equals(key)) return (ObjectNode) i;
        throw new AssertionError(key);
    }

    /** Sürüm 1001: badem göz değişti, daire silindi, kare eklendi; ev silindi; ART ağı yenilendi. */
    ObjectNode version1001() throws IOException {
        ObjectNode c = fixture();
        c.put("version", 1001);
        ArrayNode templates = (ArrayNode) c.get("templates");
        find(templates, "key", "yuz.goz.badem").put("name", "Badem göz (yeni)");
        for (int i = templates.size() - 1; i >= 0; i--) if ("geometri.daire".equals(templates.get(i).path("key").asText())) templates.remove(i);
        templates.addObject().put("key", "geometri.kare").put("templateKey", "geometri.kare").put("name", "Kare").put("mode", "ALL");
        ArrayNode vectors = (ArrayNode) c.get("vectors");
        for (int i = vectors.size() - 1; i >= 0; i--) if ("house".equals(vectors.get(i).path("id").asText())) vectors.remove(i);
        find(c.get("modeNets"), "mode", "ART").put("version", 4).put("weights", "AAAAAAAA");
        return c;
    }

    @Test
    @Order(1)
    void emptyCatalogWhenNoSeedFile() {
        JsonNode m = manifest();
        assertEquals(0, m.get("version").asLong());
        assertEquals(0, m.get("counts").get("templates").asInt());
        assertEquals(0, m.get("counts").get("vectors").asInt());
        assertEquals(0, m.get("counts").get("modeNets").asInt());
        for (String path : new String[]{"/api/v1/catalog/templates", "/api/v1/catalog/vectors", "/api/v1/catalog/mode-nets"}) {
            Res r = http.get(path);
            assertEquals(200, r.status());
            assertEquals("\"cat-0\"", r.etag());
            assertEquals(0, r.json().get("version").asLong());
            assertEquals(0, r.json().get("items").size());
            assertEquals(304, http.get(path, "If-None-Match", "\"cat-0\"").status());
        }
        // v37 boş katalog ikili biçimde de boş
        for (String path : new String[]{"/api/v1/catalog/vectors", "/api/v1/catalog/mode-nets"}) {
            Res bin = http.get(path + "?format=bin");
            assertEquals(200, bin.status());
            assertEquals("\"cat-0-bin\"", bin.etag());
            var section = com.karapp.drawguidance.server.support.CatalogBinaryReader.section(bin.bytes());
            assertEquals(0, section.version());
            assertEquals(0, section.items().size());
        }
        Document meta = mongo.getCollection(Stores.CATALOG_META).find(new Document("_id", CatalogMeta.ID)).first();
        assertEquals(0L, ((Number) meta.get("version")).longValue(), "ilk açılışta sürüm 0 yazılır");
        assertNull(meta.get("lock"));
    }

    @Test
    @Order(2)
    void seedsWhenFileAppears() throws IOException {
        writeSeed(fixture());
        Result r = sync();
        assertEquals(Outcome.SEEDED, r.outcome());
        assertEquals(1000, r.version());
        assertEquals(8, r.inserted(), "3 şablon, 3 vektör (v37: star), 2 ağ");
        assertEquals(0, r.deleted());
        assertEquals(1000, manifest().get("version").asLong());
        assertEquals(List.of("geometri.daire", "yuz.agiz.gulumseme", "yuz.goz.badem"), keys("/api/v1/catalog/templates", "key"));
        assertEquals(Outcome.UP_TO_DATE, sync().outcome(), "aynı sürüm yeniden yazılmaz");
    }

    @Test
    @Order(3)
    void reseedsWhenVersionIncreases() throws IOException {
        Res before = http.get("/api/v1/catalog/templates");
        assertEquals("\"cat-1000\"", before.etag());

        writeSeed(version1001());
        Result r = sync();
        assertEquals(Outcome.SEEDED, r.outcome());
        assertEquals(1001, r.version());
        assertEquals(1, r.inserted(), "geometri.kare");
        assertEquals(2, r.updated(), "badem göz ve ART ağı");
        assertEquals(2, r.deleted(), "geometri.daire ve house");

        JsonNode m = manifest();
        assertEquals(1001, m.get("version").asLong());
        assertEquals(3, m.get("counts").get("templates").asInt());
        assertEquals(2, m.get("counts").get("vectors").asInt());
        assertEquals(2, m.get("counts").get("modeNets").asInt());

        Res templates = http.get("/api/v1/catalog/templates", "If-None-Match", "\"cat-1000\"");
        assertEquals(200, templates.status(), "eski ETag artık tutmaz");
        assertEquals("\"cat-1001\"", templates.etag());
        assertEquals(List.of("geometri.kare", "yuz.agiz.gulumseme", "yuz.goz.badem"), keys("/api/v1/catalog/templates", "key"));
        assertEquals("Badem göz (yeni)", find(templates.json().get("items"), "key", "yuz.goz.badem").get("name").asText());
        assertEquals(List.of("cat", "star"), keys("/api/v1/catalog/vectors", "id"));
        // v37 ikili gövde de yeni sürümden (önceki sürümün önbelleği kullanılmaz)
        Res bin = http.get("/api/v1/catalog/vectors", "Accept", "application/vnd.drawguidance.catalog+bin");
        assertEquals("\"cat-1001-bin\"", bin.etag());
        assertEquals(1001, com.karapp.drawguidance.server.support.CatalogBinaryReader.section(bin.bytes()).version());
        assertEquals(4, find(http.get("/api/v1/catalog/mode-nets").json().get("items"), "mode", "ART").get("version").asInt());
        assertEquals(0, mongo.getCollection(Stores.CATALOG_TEMPLATES).countDocuments(new Document("_id", "geometri.daire")));
        assertEquals(0, mongo.getCollection(Stores.CATALOG_VECTORS).countDocuments(new Document("_id", "house")));
    }

    @Test
    @Order(4)
    void olderFileDoesNotDowngrade() throws IOException {
        writeSeed(fixture());
        Result r = sync();
        assertEquals(Outcome.UP_TO_DATE, r.outcome());
        assertEquals(1001, r.version());
        assertEquals(1001, manifest().get("version").asLong());
        assertTrue(keys("/api/v1/catalog/templates", "key").contains("geometri.kare"));
    }

    @Test
    @Order(5)
    void missingFileKeepsExistingCatalog() throws IOException {
        Files.delete(SEED);
        assertEquals(Outcome.UP_TO_DATE, sync().outcome());
        assertEquals(1001, manifest().get("version").asLong());
        assertEquals(3, keys("/api/v1/catalog/templates", "key").size());
    }

    @Test
    @Order(6)
    void emptiedCollectionsAreRefilled() throws IOException {
        for (String c : new String[]{Stores.CATALOG_TEMPLATES, Stores.CATALOG_VECTORS, Stores.CATALOG_MODENETS}) mongo.dropCollection(c);
        writeSeed(version1001());
        Result r = sync();
        assertEquals(Outcome.SEEDED, r.outcome());
        assertEquals(7, r.inserted());
        assertEquals(List.of("geometri.kare", "yuz.agiz.gulumseme", "yuz.goz.badem"), keys("/api/v1/catalog/templates", "key"));
    }

    @Test
    @Order(7)
    void concurrentInstancesSeedOnce() throws Exception {
        ObjectNode c = version1001();
        c.put("version", 1002);
        writeSeed(c);
        int n = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<Result>> results = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                // Her biri ayrı sunucu gibi: kendi kimliğiyle
                CatalogSeeder instance = new CatalogSeeder(mongo, resources, json, props);
                results.add(pool.submit(() -> {
                    start.await();
                    return instance.sync(new FileSystemResource(SEED));
                }));
            }
            start.countDown();
            int seeded = 0;
            for (Future<Result> f : results) {
                Result r = f.get();
                assertEquals(1002, r.version(), r.toString());
                if (r.outcome() == Outcome.SEEDED) seeded++;
                else assertEquals(Outcome.UP_TO_DATE, r.outcome());
            }
            assertEquals(1, seeded, "eşitlemeyi tek sunucu yapmalı");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1002, manifest().get("version").asLong());
        assertEquals(3, keys("/api/v1/catalog/templates", "key").size());
        Document meta = mongo.getCollection(Stores.CATALOG_META).find(new Document("_id", CatalogMeta.ID)).first();
        assertNull(meta.get("lock"), "kira bırakılmalı");
    }

    @Test
    @Order(8)
    void expiredLeaseOfCrashedInstanceIsTakenOver() throws IOException {
        mongo.getCollection(Stores.CATALOG_META).updateOne(new Document("_id", CatalogMeta.ID),
                new Document("$set", new Document("lock", new Document("owner", "cokmus-sunucu").append("target", 1003L)
                        .append("since", new Date(System.currentTimeMillis() - 120_000)).append("until", new Date(System.currentTimeMillis() - 1000)))));
        ObjectNode c = version1001();
        c.put("version", 1003);
        writeSeed(c);
        Result r = sync();
        assertEquals(Outcome.SEEDED, r.outcome());
        assertEquals(1003, manifest().get("version").asLong());
    }

    @Test
    @Order(9)
    void corruptFileIsIgnored() throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(SEED))) {
            out.write("{\"version\": 5000, \"templates\": [".getBytes());
        }
        assertNull(sync(), "bozuk dosya eşitlenmez");
        Files.writeString(SEED, "{\"version\": \"yeni\"}");
        assertNull(sync());
        assertEquals(1003, manifest().get("version").asLong());
        assertFalse(keys("/api/v1/catalog/templates", "key").isEmpty());
    }
}
