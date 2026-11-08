package com.karapp.drawguidance.server.catalog;

import com.karapp.drawguidance.server.support.CatalogBinaryReader;
import com.karapp.drawguidance.server.support.CatalogBinaryReader.Path;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v37 ikili katalog kodlayıcısı (veritabanı gerekmez): uygulamayla ortak örnek dosya ({@code catalog-binary-fixture.json})
 * uygulamanın ürettiği baytların aynısını verir (beklenen baytlar ve SHA-256 özetleri uygulamadaki
 * {@code CatalogBinaryTest.kt} ile aynı), yollar uygulamanın JSON çözücüsünün okuduğu gibi okunur, uygulamanın
 * çözemeyeceği öğeler atlanır.
 */
class CatalogBinaryTest {
    /** Uygulama testindeki sabitlerle aynı olmalı. */
    static final String VECTORS_SHA256 = "7c3c993e9f7fe2f96eeb426eda9b1971fb8ffa5f59fb343aa71030614cf6ce85";
    static final String MODE_NETS_SHA256 = "4d89bdc3d9156f1673703b39b6bda8d7338f37d99891a95594471f0cf16398c3";
    static final long FIXTURE_VERSION = 37000L;

    static final ObjectMapper JSON = new ObjectMapper();
    static JsonNode fixture;

    @BeforeAll
    static void readFixture() throws IOException {
        fixture = JSON.readTree(resource("catalog-binary-fixture.json"));
    }

    static byte[] resource(String name) throws IOException {
        try (InputStream in = CatalogBinaryTest.class.getResourceAsStream("/catalog/" + name)) {
            assertNotNull(in, name);
            return in.readAllBytes();
        }
    }

    static byte[] hex(String name) throws IOException {
        String text = new String(resource(name), StandardCharsets.US_ASCII).replaceAll("\\s", "");
        return HexFormat.of().parseHex(text);
    }

    static String sha256(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    /** Tohum dosyasındaki gibi: JSON öğesi → BSON belgesi ({@link CatalogFile#toBson}). */
    static List<Document> docs(String array) {
        List<Document> out = new ArrayList<>();
        for (JsonNode item : fixture.get(array)) out.add((Document) CatalogFile.toBson(item));
        return out;
    }

    static Document doc(String json) { return (Document) CatalogFile.toBson(read(json)); }

    static JsonNode read(String json) {
        try {
            return JSON.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void fixtureVectorsMatchTheAppBytes() throws Exception {
        byte[] expected = hex("catalog-binary-fixture.vectors.hex");
        assertEquals(VECTORS_SHA256, sha256(expected));
        assertArrayEquals(expected, CatalogBinary.vectors(FIXTURE_VERSION, docs("vectors")));
        assertArrayEquals(expected, CatalogBinary.encode(CatalogKind.VECTORS, FIXTURE_VERSION, docs("vectors")));
    }

    @Test
    void fixtureModeNetsMatchTheAppBytes() throws Exception {
        byte[] expected = hex("catalog-binary-fixture.modenets.hex");
        assertEquals(MODE_NETS_SHA256, sha256(expected));
        assertArrayEquals(expected, CatalogBinary.modeNets(FIXTURE_VERSION, docs("modeNets")));
    }

    @Test
    void fixtureDecodesToTheJsonFields() throws Exception {
        CatalogBinaryReader.Section s = CatalogBinaryReader.section(hex("catalog-binary-fixture.vectors.hex"));
        assertEquals(1, s.format());
        assertEquals(CatalogBinary.SECTION_VECTORS, s.section());
        assertEquals(FIXTURE_VERSION, s.version());
        JsonNode items = fixture.get("vectors");
        assertEquals(items.size(), s.items().size());
        for (int i = 0; i < items.size(); i++) {
            JsonNode j = items.get(i);
            CatalogBinaryReader.Vector v = CatalogBinaryReader.vector(s.items().get(i));
            assertEquals(511, v.mask());
            assertEquals(j.get("id").asText(), v.id());
            assertEquals(j.get("title").asText(), v.title());
            assertEquals(j.get("categoryTitle").asText(), v.categoryTitle());
            assertEquals(j.get("audience").asText(), v.audience());
            assertEquals(j.get("fit").asText(), v.fit());
            assertEquals(Float.parseFloat(j.get("aspect").asText()), v.aspect());
            assertEquals(strings(j.get("tags")), v.tags());
            assertEquals(strings(j.get("kinds")), v.kinds());
            assertPathsEqual(j.get("paths").asText(), CatalogBinaryReader.paths(v.paths()));
        }
        CatalogBinaryReader.Vector first = CatalogBinaryReader.vector(s.items().get(0));
        assertEquals("Çizim örneği ğüşöçİı 漢字 😀", first.title());
        assertEquals(4f / 3f, first.aspect());
        List<Path> paths = CatalogBinaryReader.paths(first.paths());
        assertEquals(4, paths.size());
        assertTrue(paths.get(0).closed());
        assertEquals("DASHED", paths.get(0).line());
        assertEquals(27500, paths.get(0).width());
        assertEquals("Etiket ğ", paths.get(0).label());
        assertEquals(0, paths.get(2).width(), "kalınlık 0: çizgisiz");
        // Büyük koordinat: uygulamadaki gibi float aritmetiğiyle (123456.8f * 10000f float duyarlığında)
        assertEquals(1234567936, paths.get(3).xy()[3]);
        assertEquals(CatalogBinary.q4(123456.8f), paths.get(3).xy()[3]);

        CatalogBinaryReader.Section nets = CatalogBinaryReader.section(hex("catalog-binary-fixture.modenets.hex"));
        assertEquals(CatalogBinary.SECTION_MODE_NETS, nets.section());
        for (int i = 0; i < nets.items().size(); i++) {
            JsonNode j = fixture.get("modeNets").get(i);
            CatalogBinaryReader.ModeNet n = CatalogBinaryReader.modeNet(nets.items().get(i));
            assertEquals(j.get("mode").asText(), n.mode());
            assertEquals(j.get("version").asInt(), n.version());
            assertEquals(j.get("format").asText(), n.format());
            assertArrayEquals(Base64.getDecoder().decode(j.get("weights").asText()), n.weights());
        }
    }

    /** JSON'daki yollar (uygulamanın okuduğu gibi) ile paketli yollar aynı. */
    static void assertPathsEqual(String json, List<Path> packed) {
        JsonNode arr = read(json);
        assertEquals(arr.size(), packed.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonNode o = arr.get(i);
            Path p = packed.get(i);
            JsonNode xy = o.get("p");
            assertEquals(xy.size() / 2 * 2, p.xy().length);
            for (int k = 0; k < p.xy().length; k++) assertEquals(CatalogBinary.q4(Float.parseFloat(xy.get(k).asText())), p.xy()[k]);
            assertEquals(o.path("c").asInt() == 1, p.closed());
            assertEquals(o.path("f").asText(null), p.fill());
            assertEquals(o.path("s").asText(null), p.stroke());
            assertEquals(o.path("l").asText(null), p.line());
            assertEquals(o.path("t").asText(null), p.tag());
            assertEquals(o.path("lb").asText(null), p.label());
            assertEquals(o.has("w") ? CatalogBinary.q4(Float.parseFloat(o.get("w").asText())) : null, p.width());
            assertEquals(o.has("a") ? CatalogBinary.q4(Float.parseFloat(o.get("a").asText())) : null, p.alpha());
            assertEquals(o.has("ls") ? CatalogBinary.q4(Float.parseFloat(o.get("ls").asText())) : null, p.labelSize());
        }
    }

    static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(e -> out.add(e.asText()));
        return out;
    }

    /** Eksik ya da yazı olmayan alanların bayrağı konmaz (uygulama öntanımlıyı kullanır); ilkel diziler metin olur. */
    @Test
    void missingFieldsAreLeftOutAndPrimitivesBecomeText() {
        CatalogBinaryReader.Vector min = CatalogBinaryReader.vector(CatalogBinary.vectorItem(doc("{\"id\":\"x\",\"paths\":\"[]\"}")));
        assertEquals(0, min.mask());
        assertEquals("x", min.id());
        assertTrue(CatalogBinaryReader.paths(min.paths()).isEmpty());

        CatalogBinaryReader.Vector odd = CatalogBinaryReader.vector(CatalogBinary.vectorItem(doc(
                "{\"id\":\"y\",\"title\":5,\"audience\":\"TEENS\",\"aspect\":\"0.5\",\"tags\":[1,\"a\",null,true,{\"x\":1},2.5],"
                        + "\"kinds\":\"ART\",\"paths\":\"[]\"}")));
        assertEquals(CatalogBinary.V_AUDIENCE | CatalogBinary.V_ASPECT | CatalogBinary.V_TAGS, odd.mask());
        assertNull(odd.title());
        assertEquals("TEENS", odd.audience(), "bilinmeyen kitle olduğu gibi (uygulama ART sayar)");
        assertEquals(0.5f, odd.aspect());
        assertEquals(List.of("1", "a", "null", "true", "2.5"), odd.tags());
        assertNull(odd.kinds());

        // Tam sayı ve uzun tam sayı aspect
        assertEquals(2f, CatalogBinaryReader.vector(CatalogBinary.vectorItem(doc("{\"id\":\"z\",\"aspect\":2,\"paths\":\"[]\"}"))).aspect());
        // Boolean aspect sayı değildir (uygulamada 1)
        assertEquals(0, CatalogBinaryReader.vector(CatalogBinary.vectorItem(doc("{\"id\":\"z\",\"aspect\":true,\"paths\":\"[]\"}"))).mask());
    }

    /** Uygulamanın JSON çözücüsünün atlayacağı öğeler ikili yanıta konmaz; geri kalanlar sırasıyla gelir. */
    @Test
    void itemsTheAppCannotDecodeAreSkipped() {
        String[] bad = {
                "{\"title\":\"id yok\",\"paths\":\"[]\"}",
                "{\"id\":7,\"paths\":\"[]\"}",
                "{\"id\":\"a\"}",
                "{\"id\":\"a\",\"paths\":[]}",
                "{\"id\":\"a\",\"paths\":\"{}\"}",
                "{\"id\":\"a\",\"paths\":\"[1]\"}",
                "{\"id\":\"a\",\"paths\":\"[{\\\"d\\\":\\\"M0 0\\\"}]\"}",
                "{\"id\":\"a\",\"paths\":\"[{\\\"p\\\":5}]\"}",
                "{\"id\":\"a\",\"paths\":\"[{\\\"p\\\":[0,[1]]}]\"}",
                "{\"id\":\"a\",\"paths\":\"[{\\\"p\\\":[0,1],\\\"c\\\":{}}]\"}",
                "{\"id\":\"a\",\"paths\":\"[{\\\"p\\\":[0,1],\\\"w\\\":[2]}]\"}",
                "{\"id\":\"a\",\"paths\":\"[{\\\"p\\\":[0,1]\"}",
                "{\"id\":\"a\",\"paths\":\"[] []\"}",
                "{\"id\":\"a\",\"aspect\":{\"w\":1},\"paths\":\"[]\"}",
        };
        List<Document> docs = new ArrayList<>();
        docs.add(doc("{\"id\":\"ilk\",\"paths\":\"[]\"}"));
        for (String b : bad) {
            Document d = doc(b);
            assertThrowsOrNull(d);
            docs.add(d);
        }
        docs.add(doc("{\"id\":\"son\",\"paths\":\"[]\"}"));
        CatalogBinaryReader.Section s = CatalogBinaryReader.section(CatalogBinary.vectors(5, docs));
        assertEquals(List.of("ilk", "son"), s.items().stream().map(i -> CatalogBinaryReader.vector(i).id()).toList());

        List<Document> nets = List.of(
                doc("{\"mode\":\"ART\",\"weights\":\"AQID\"}"),
                doc("{\"mode\":\"NOTES\",\"weights\":\"base64 değil!\"}"),
                doc("{\"weights\":\"AQID\"}"),
                doc("{\"mode\":\"KIDS\",\"weights\":7}"));
        CatalogBinaryReader.Section n = CatalogBinaryReader.section(CatalogBinary.modeNets(5, nets));
        assertEquals(1, n.items().size());
        CatalogBinaryReader.ModeNet art = CatalogBinaryReader.modeNet(n.items().get(0));
        assertEquals(0, art.mask(), "sürüm ve biçim yoksa bayrak yok (uygulama 0 ve modenet-int8-v1 kullanır)");
        assertArrayEquals(new byte[]{1, 2, 3}, art.weights());
    }

    static void assertThrowsOrNull(Document d) {
        try {
            assertNull(CatalogBinary.vectorItem(d), d.toJson());
        } catch (IllegalArgumentException e) {
            // beklenen (container atlar)
        }
    }

    /** Yollar uygulamadaki {@code decodePaths} gibi okunur. */
    @Test
    void pathsAreReadLikeTheAppReadsThem() {
        String json = "[{\"p\":[0.1,null,\"0.5\",true,0.25],\"c\":true,\"f\":5,\"s\":\"#000\",\"w\":\"2\",\"l\":\"SOLID\",\"a\":1,\"ls\":0},"
                + "{\"p\":[1,1],\"c\":\"1\",\"l\":\"WAVY\",\"a\":0.5,\"t\":null,\"lb\":\"x\",\"lb\":\"son\"},"
                + "{\"p\":[1E-4,-1.0E-4],\"c\":2},"
                + "{\"p\":[],\"c\":\"TRUE\",\"w\":1.0}]";
        List<Path> p = CatalogBinaryReader.paths(CatalogBinary.packPaths(json));
        assertEquals(4, p.size());
        // null ve sayı olmayan noktalar 0, tek kalan koordinat atılır
        assertArrayEquals(new int[]{1000, 0, 5000, 0}, p.get(0).xy());
        assertTrue(p.get(0).closed());
        assertNull(p.get(0).fill(), "sayı renk değildir");
        assertEquals("#000", p.get(0).stroke());
        assertEquals(20000, p.get(0).width(), "yazı olarak sayı");
        assertNull(p.get(0).line(), "SOLID yazılmaz");
        assertNull(p.get(0).alpha(), "1 öntanımlı");
        assertNull(p.get(0).labelSize(), "0 öntanımlı");
        assertTrue(p.get(1).closed(), "\"1\" tam sayı olarak okunur");
        assertEquals("WAVY", p.get(1).line(), "çizgi biçimi yorumlanmaz");
        assertEquals(5000, p.get(1).alpha());
        assertNull(p.get(1).tag(), "null yazı değildir");
        assertEquals("son", p.get(1).label(), "aynı anahtarın sonuncusu");
        assertArrayEquals(new int[]{1, -1}, p.get(2).xy());
        assertFalse(p.get(2).closed());
        assertTrue(p.get(3).closed(), "büyük/küçük harf duyarsız true");
        assertNull(p.get(3).width());
        // Noktalar yollar arasında farkla devam eder: ikinci yolun (1,1) noktası mutlak olarak çözülür
        assertArrayEquals(new int[]{10000, 10000}, p.get(1).xy());
        assertThrows(IllegalArgumentException.class, () -> CatalogBinary.packPaths("[{\"p\":[1,2]}"));
    }

    /**
     * Jar'daki gerçek tohum dosyasının her vektörü ve ağı ikili biçime girer (tohum yenilenip yeni öğeler eklenince de:
     * uygulamanın çözemeyeceği öğe olmamalı); yollar bağımsız okuyucuyla baştan sona çözülür.
     */
    @Test
    void bundledSeedEncodesEveryItem() throws IOException {
        CatalogFile seed = CatalogFile.read(new org.springframework.core.io.ClassPathResource("catalog/catalog.json.gz"), JSON);
        for (CatalogKind kind : List.of(CatalogKind.VECTORS, CatalogKind.MODE_NETS)) {
            CatalogBinaryReader.Section s = CatalogBinaryReader.section(CatalogBinary.encode(kind, seed.version(), seed.items(kind)));
            assertEquals(seed.version(), s.version());
            assertEquals(seed.count(kind), s.items().size(), kind.name());
            if (kind == CatalogKind.VECTORS) {
                for (byte[] item : s.items()) {
                    CatalogBinaryReader.Vector v = CatalogBinaryReader.vector(item);
                    assertEquals(511, v.mask(), v.id());
                    assertNotNull(CatalogBinaryReader.paths(v.paths()), v.id());
                }
            }
        }
    }

    @Test
    void manifestAdvertisesBinarySections() {
        assertEquals(List.of("vectors", "modeNets"), CatalogService.BINARY.sections());
        assertEquals("application/vnd.drawguidance.catalog+bin", CatalogService.BINARY.mediaType());
        assertTrue(CatalogBinary.supports(CatalogKind.VECTORS) && CatalogBinary.supports(CatalogKind.MODE_NETS));
        assertFalse(CatalogBinary.supports(CatalogKind.TEMPLATES));
    }

    /** {@code Accept} ve {@code ?format=} çözümlemesi. */
    @Test
    void contentNegotiation() {
        CatalogService.Format json = CatalogService.Format.JSON, bin = CatalogService.Format.BINARY;
        String type = CatalogBinary.MEDIA_TYPE;
        Map<String, CatalogService.Format> accept = new java.util.LinkedHashMap<>();
        accept.put(null, json);
        accept.put("", json);
        accept.put("*/*", json);
        accept.put("application/json", json);
        accept.put(type, bin);
        accept.put(type + ", application/json;q=0.5", bin);
        accept.put("application/json, " + type, bin);
        accept.put("application/json, " + type + ";q=0.9", json);
        accept.put(type + ";q=0", json);
        accept.put(type.toUpperCase(), bin);
        accept.forEach((header, want) -> assertEquals(want, CatalogController.format(CatalogKind.VECTORS, header, null), String.valueOf(header)));
        assertEquals(json, CatalogController.format(CatalogKind.TEMPLATES, type, null), "şablonlar yalnız JSON");
        assertEquals(bin, CatalogController.format(CatalogKind.MODE_NETS, null, "bin"));
        assertEquals(bin, CatalogController.format(CatalogKind.MODE_NETS, null, " BINARY "));
        assertEquals(json, CatalogController.format(CatalogKind.VECTORS, type, "json"), "parametre başlıktan önce gelir");
        assertEquals("unknown_format", assertThrows(com.karapp.drawguidance.server.common.ApiException.class,
                () -> CatalogController.format(CatalogKind.VECTORS, null, "xml")).code());
        assertEquals("unsupported_format", assertThrows(com.karapp.drawguidance.server.common.ApiException.class,
                () -> CatalogController.format(CatalogKind.TEMPLATES, null, "bin")).code());
        assertEquals("\"cat-7-bin\"", CatalogController.etag(7, null, bin));
        assertEquals("\"cat-7-KIDS-bin\"", CatalogController.etag(7, "KIDS", bin));
        assertEquals("\"cat-7-KIDS\"", CatalogController.etag(7, "KIDS", json));
        assertEquals(CatalogController.etag(7), CatalogController.etag(7, null, json));
    }
}
