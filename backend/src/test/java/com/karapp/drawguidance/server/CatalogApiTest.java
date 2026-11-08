package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.catalog.CatalogBinary;
import com.karapp.drawguidance.server.support.CatalogBinaryReader;
import com.karapp.drawguidance.server.support.CatalogBinaryReader.Path;
import com.karapp.drawguidance.server.support.Http.Res;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Katalog uçları: test tohum dosyasından (sürüm 1000) gelen içerik, ETag / 304, gzip, kimliksiz erişim; v37 ikili
 * biçim (içerik anlaşması, JSON'la aynı öğeler, ayrı ETag).
 */
class CatalogApiTest extends ApiContextTest {
    static final String BIN = "application/vnd.drawguidance.catalog+bin";

    static final String[][] SECTIONS = {
            {"/api/v1/catalog/templates", "templates", "key"},
            {"/api/v1/catalog/vectors", "vectors", "id"},
            {"/api/v1/catalog/mode-nets", "modeNets", "mode"}};
    static final String[] ALL_PATHS = {"/api/v1/catalog/manifest", SECTIONS[0][0], SECTIONS[1][0], SECTIONS[2][0]};

    static JsonNode fixture;

    @BeforeAll
    static void readFixture() throws Exception {
        try (InputStream in = new GZIPInputStream(CatalogApiTest.class.getResourceAsStream("/catalog/test-catalog.json.gz"))) {
            fixture = new ObjectMapper().readTree(in);
        }
    }

    /** Dosyadaki geçerli (anahtarı olan) öğeler, anahtara göre. */
    static Map<String, JsonNode> expected(String array, String keyField) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        for (JsonNode item : fixture.get(array)) if (item.hasNonNull(keyField)) out.put(item.get(keyField).asText(), item);
        return out;
    }

    @Test
    void manifestReportsVersionCountsAndEtag() {
        Res r = http.get("/api/v1/catalog/manifest");
        assertEquals(200, r.status(), r.body());
        JsonNode m = r.json();
        assertEquals(1000, m.get("version").asLong());
        assertNotNull(Instant.parse(m.get("updatedAt").asText()));
        assertEquals(3, m.get("counts").get("templates").asInt());
        assertEquals(3, m.get("counts").get("vectors").asInt());
        assertEquals(2, m.get("counts").get("modeNets").asInt());
        // v37 ikili biçim bilgisi (eski uygulamalar bilinmeyen alanı yok sayar)
        JsonNode binary = m.get("binary");
        assertEquals(BIN, binary.get("mediaType").asText());
        assertEquals(1, binary.get("formatVersion").asInt());
        assertEquals(List.of("vectors", "modeNets"), List.of(binary.get("sections").get(0).asText(), binary.get("sections").get(1).asText()));
        assertEquals(2, binary.get("sections").size());
        assertEquals(4, m.size());
        assertEquals("\"cat-1000\"", r.etag());
        assertTrue(r.header("Cache-Control").contains("no-cache"));
    }

    @Test
    void sectionsReturnSeedItemsUnchanged() {
        for (String[] s : SECTIONS) {
            Res r = http.get(s[0]);
            assertEquals(200, r.status(), s[0] + " " + r.body());
            assertEquals("\"cat-1000\"", r.etag());
            assertTrue(r.header("Content-Type").startsWith("application/json"), s[0]);
            JsonNode body = r.json();
            assertEquals(1000, body.get("version").asLong());
            assertEquals(2, body.size());
            Map<String, JsonNode> want = expected(s[1], s[2]);
            List<String> keys = new ArrayList<>();
            for (JsonNode item : body.get("items")) {
                String key = item.get(s[2]).asText();
                keys.add(key);
                assertNull(item.get("_id"), "iç alan sızmamalı: " + key);
                assertEquals(want.get(key), item, "öğe olduğu gibi dönmeli: " + key);
            }
            assertEquals(want.keySet().stream().sorted().toList(), keys, "anahtara göre sıralı, anahtarsız öğe atlanmış");
        }
    }

    /** v34 {@code ?kind=}: yalnız o tuval türünün öğeleri; ETag türü içerir; bilinmeyen tür 400. */
    @Test
    void filtersByCanvasKind() {
        Res kids = http.get("/api/v1/catalog/templates?kind=KIDS");
        assertEquals(200, kids.status(), kids.body());
        assertEquals("\"cat-1000-KIDS\"", kids.etag());
        List<String> keys = new ArrayList<>();
        for (JsonNode item : kids.json().get("items")) keys.add(item.get("key").asText());
        assertEquals(List.of("yuz.goz.badem"), keys);
        Res notes = http.get("/api/v1/catalog/templates?kind=simple_notes");
        assertEquals(1, notes.json().get("items").size());
        assertEquals("yuz.agiz.gulumseme", notes.json().get("items").get(0).get("key").asText());
        Res vectors = http.get("/api/v1/catalog/vectors?kind=KIDS");
        assertEquals(1, vectors.json().get("items").size());
        assertEquals("cat", vectors.json().get("items").get(0).get("id").asText());
        assertEquals(304, http.get("/api/v1/catalog/templates?kind=KIDS", "If-None-Match", "\"cat-1000-KIDS\"").status());
        assertEquals(200, http.get("/api/v1/catalog/templates?kind=KIDS", "If-None-Match", "\"cat-1000\"").status());
        assertEquals(400, http.get("/api/v1/catalog/templates?kind=ROBOT").status());
        // Süzmesiz yanıt değişmedi (tüm öğeler)
        assertEquals(expected("templates", "key").size(), http.get("/api/v1/catalog/templates").json().get("items").size());
    }

    @Test
    void templatesKeepRawFieldsAndNumberTypes() {
        JsonNode items = http.get("/api/v1/catalog/templates").json().get("items");
        JsonNode smile = null;
        for (JsonNode i : items) if ("yuz.agiz.gulumseme".equals(i.get("key").asText())) smile = i;
        assertNotNull(smile);
        assertEquals(4294967296L, smile.get("lshKey").asLong());
        assertTrue(smile.get("solid").isNull());
        assertEquals(2.5, smile.get("extra").get("weights").get(1).asDouble());
        assertTrue(smile.get("extra").get("weights").get(4).isNull());
    }

    @Test
    void etagAndIfNoneMatch() {
        for (String path : ALL_PATHS) {
            for (String inm : new String[]{"\"cat-1000\"", "W/\"cat-1000\"", "\"x\", \"cat-1000\"", "*"}) {
                Res r = http.get(path, "If-None-Match", inm);
                assertEquals(304, r.status(), path + " " + inm);
                assertEquals(0, r.bytes().length);
                assertEquals("\"cat-1000\"", r.etag());
            }
            Res stale = http.get(path, "If-None-Match", "\"cat-999\"");
            assertEquals(200, stale.status(), path);
            assertEquals("\"cat-1000\"", stale.etag());
        }
    }

    @Test
    void sectionsAreGzippedWhenAccepted() {
        for (String[] s : SECTIONS) {
            Res plain = http.get(s[0]);
            assertNull(plain.header("Content-Encoding"));
            Res gz = http.get(s[0], "Accept-Encoding", "gzip");
            assertEquals(200, gz.status());
            assertEquals("gzip", gz.header("Content-Encoding"));
            assertTrue(String.join(",", gz.headers().allValues("Vary")).contains("Accept-Encoding"), gz.headers().toString());
            assertEquals("\"cat-1000\"", gz.etag());
            assertEquals(plain.json(), gz.gunzipJson());
            Res refused = http.get(s[0], "Accept-Encoding", "gzip;q=0, identity");
            assertNull(refused.header("Content-Encoding"));
            assertEquals(plain.json(), refused.json());
        }
    }

    @Test
    void catalogNeedsNoTokenAndIgnoresABadOne() {
        for (String path : ALL_PATHS) {
            assertEquals(200, http.get(path).status(), path);
            assertEquals(200, http.get(path, "Authorization", "Bearer garbage").status(), path);
            assertEquals(200, http.get(path, "Authorization", "Basic Zm9vOmJhcg==").status(), path);
        }
        assertEquals(200, http.get("/api/health", "Authorization", "Bearer garbage").status());
    }

    // ---------------------------------------------------------------- v37 ikili biçim

    static String vary(Res r) { return String.join(",", r.headers().allValues("Vary")); }

    static byte[] gunzip(byte[] b) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(b))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static List<String> ids(Res bin) {
        List<String> out = new ArrayList<>();
        for (byte[] item : CatalogBinaryReader.section(bin.bytes()).items()) out.add(CatalogBinaryReader.vector(item).id());
        return out;
    }

    /** Öntanımlı yanıt JSON kalır (eski uygulamalar): Accept yok, JSON, joker ya da ikili JSON'dan düşük öncelikli. */
    @Test
    void jsonStaysTheDefault() {
        for (String path : new String[]{SECTIONS[1][0], SECTIONS[2][0]}) {
            Res plain = http.get(path);
            assertTrue(vary(plain).contains("Accept"), vary(plain));
            for (String accept : new String[]{"application/json", "*/*", "application/json, " + BIN + ";q=0.5", BIN + ";q=0"}) {
                Res r = http.get(path, "Accept", accept);
                assertEquals(200, r.status(), path + " " + accept);
                assertTrue(r.header("Content-Type").startsWith("application/json"), accept);
                assertEquals("\"cat-1000\"", r.etag(), accept);
                assertArrayEquals(plain.bytes(), r.bytes(), accept);
            }
        }
    }

    /** İkili vektörler JSON'daki öğelerin kodlanmışıdır; uygulamanın çözemeyeceği öğe ("cat": yolunda "p" yok) yoktur. */
    @Test
    void binaryVectorsAreTheJsonItemsEncoded() {
        Res json = http.get("/api/v1/catalog/vectors");
        Res bin = http.get("/api/v1/catalog/vectors", "Accept", BIN);
        assertEquals(200, bin.status(), bin.body());
        assertTrue(bin.header("Content-Type").startsWith(BIN), bin.header("Content-Type"));
        assertEquals("\"cat-1000-bin\"", bin.etag());
        assertTrue(vary(bin).contains("Accept") && vary(bin).contains("Accept-Encoding"), vary(bin));
        assertTrue(bin.header("Cache-Control").contains("no-cache"));

        List<Document> docs = new ArrayList<>();
        for (JsonNode item : json.json().get("items")) docs.add(Document.parse(item.toString()));
        assertArrayEquals(CatalogBinary.vectors(1000, docs), bin.bytes());

        CatalogBinaryReader.Section s = CatalogBinaryReader.section(bin.bytes());
        assertEquals(1, s.format());
        assertEquals(CatalogBinary.SECTION_VECTORS, s.section());
        assertEquals(1000, s.version());
        assertEquals(List.of("house", "star"), ids(bin));

        CatalogBinaryReader.Vector house = CatalogBinaryReader.vector(s.items().get(0));
        assertEquals("ALL", house.audience(), "değer olduğu gibi (uygulama tanımadığını ART sayar)");
        assertEquals("WIDTH", house.fit());
        assertEquals(1f, house.aspect());
        assertEquals(List.of(), house.tags());
        assertNull(house.kinds(), "JSON'da kinds yok");
        assertTrue(CatalogBinaryReader.paths(house.paths()).isEmpty());

        CatalogBinaryReader.Vector star = CatalogBinaryReader.vector(s.items().get(1));
        assertEquals(511, star.mask());
        assertEquals("Yıldız", star.title());
        assertEquals("shapes", star.categoryId());
        assertEquals("Şekiller", star.categoryTitle());
        assertEquals("Temel", star.section());
        assertEquals("ART", star.audience());
        assertEquals("SQUARE", star.fit());
        assertEquals(List.of("yıldız", "şekil"), star.tags());
        assertEquals(List.of("ART", "COMIC"), star.kinds());
        List<Path> paths = CatalogBinaryReader.paths(star.paths());
        assertEquals(3, paths.size());
        Path body = paths.get(0);
        assertTrue(body.closed());
        assertEquals(20, body.xy().length);
        assertArrayEquals(new int[]{5000, 0, 6123, 3455}, java.util.Arrays.copyOf(body.xy(), 4));
        assertEquals("#FFD700", body.fill());
        assertEquals("#8A6D00", body.stroke());
        assertEquals(15000, body.width());
        assertEquals("★ Yıldız", paths.get(1).label());
        assertEquals(1000, paths.get(1).labelSize());
        assertEquals("DASHED", paths.get(2).line());
        assertEquals(5000, paths.get(2).alpha());
        assertEquals("base", paths.get(2).tag());
    }

    @Test
    void binaryModeNetsCarryRawWeights() {
        JsonNode items = http.get("/api/v1/catalog/mode-nets").json().get("items");
        Res bin = http.get("/api/v1/catalog/mode-nets", "Accept", BIN);
        assertEquals(200, bin.status());
        assertEquals("\"cat-1000-bin\"", bin.etag());
        CatalogBinaryReader.Section s = CatalogBinaryReader.section(bin.bytes());
        assertEquals(CatalogBinary.SECTION_MODE_NETS, s.section());
        assertEquals(items.size(), s.items().size());
        for (int i = 0; i < items.size(); i++) {
            JsonNode j = items.get(i);
            CatalogBinaryReader.ModeNet n = CatalogBinaryReader.modeNet(s.items().get(i));
            assertEquals(j.get("mode").asText(), n.mode());
            assertEquals(j.get("version").asInt(), n.version());
            assertEquals(j.get("format").asText(), n.format());
            assertArrayEquals(Base64.getDecoder().decode(j.get("weights").asText()), n.weights());
        }
    }

    /** İkili gövde gzip'lenir; ETag temsile özgüdür; {@code ?format=} başlıktan önce gelir. */
    @Test
    void binaryGzipEtagAndFormatParameter() {
        for (String path : new String[]{"/api/v1/catalog/vectors", "/api/v1/catalog/mode-nets"}) {
            Res bin = http.get(path, "Accept", BIN);
            Res gz = http.get(path, "Accept", BIN, "Accept-Encoding", "gzip");
            assertEquals("gzip", gz.header("Content-Encoding"), path);
            assertEquals("\"cat-1000-bin\"", gz.etag());
            assertArrayEquals(bin.bytes(), gunzip(gz.bytes()), path);

            Res same = http.get(path, "Accept", BIN, "If-None-Match", "\"cat-1000-bin\"");
            assertEquals(304, same.status(), path);
            assertEquals("\"cat-1000-bin\"", same.etag());
            assertEquals(0, same.bytes().length);
            assertEquals(200, http.get(path, "Accept", BIN, "If-None-Match", "\"cat-1000\"").status(), "JSON ETag'i ikili gövdeyi tutmaz");
            assertEquals(200, http.get(path, "If-None-Match", "\"cat-1000-bin\"").status(), "ikili ETag JSON gövdeyi tutmaz");

            Res param = http.get(path + "?format=bin");
            assertEquals(200, param.status());
            assertEquals("\"cat-1000-bin\"", param.etag());
            assertArrayEquals(bin.bytes(), param.bytes());
            Res forced = http.get(path + "?format=json", "Accept", BIN);
            assertTrue(forced.header("Content-Type").startsWith("application/json"));
            assertEquals("\"cat-1000\"", forced.etag());
        }
    }

    /** Şablonlar yalnız JSON; bilinmeyen biçim 400; tuval türü süzmesi ikili biçimde de çalışır. */
    @Test
    void binaryFormatErrorsAndKindFilter() {
        Res tpl = http.get("/api/v1/catalog/templates", "Accept", BIN);
        assertEquals(200, tpl.status());
        assertTrue(tpl.header("Content-Type").startsWith("application/json"));
        assertEquals("\"cat-1000\"", tpl.etag());
        Res unsupported = http.get("/api/v1/catalog/templates?format=bin");
        assertEquals(400, unsupported.status());
        assertEquals("unsupported_format", unsupported.error());
        Res unknown = http.get("/api/v1/catalog/vectors?format=xml");
        assertEquals(400, unknown.status());
        assertEquals("unknown_format", unknown.error());

        Res art = http.get("/api/v1/catalog/vectors?kind=art", "Accept", BIN);
        assertEquals(200, art.status());
        assertEquals("\"cat-1000-ART-bin\"", art.etag());
        assertEquals(List.of("star"), ids(art));
        assertEquals(304, http.get("/api/v1/catalog/vectors?kind=ART", "Accept", BIN, "If-None-Match", "\"cat-1000-ART-bin\"").status());
        assertEquals(200, http.get("/api/v1/catalog/vectors?kind=ART", "If-None-Match", "\"cat-1000-ART-bin\"").status());
    }
}
