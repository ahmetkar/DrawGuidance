package com.karapp.drawguidance.server.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Katalog tohum dosyası (gzip'li JSON; gzip'siz JSON da okunur):
 * {@code {"version": 33000, "generatedAt": "...", "templates": [...], "vectors": [...], "modeNets": [...]}}.
 * Her öğe olduğu gibi BSON belgesine çevrilir, anahtarı {@code _id} olur. Anahtarı olmayan öğeler atlanır, aynı
 * anahtar iki kez geçerse sonuncusu kalır.
 *
 * @param version     katalog sürümü (dosya yoksa 0)
 * @param generatedAt dosyanın üretildiği an (yorumlanmaz)
 * @param items       bölüm → {@code _id}'li belgeler (dosyadaki sırayla)
 * @param missing     dosya bulunamadı (boş katalog)
 */
public record CatalogFile(long version, String generatedAt, Map<CatalogKind, List<Document>> items, boolean missing) {
    private static final Logger log = LoggerFactory.getLogger(CatalogFile.class);

    public static CatalogFile empty(boolean missing) {
        Map<CatalogKind, List<Document>> items = new EnumMap<>(CatalogKind.class);
        for (CatalogKind k : CatalogKind.values()) items.put(k, List.of());
        return new CatalogFile(0, null, items, missing);
    }

    public List<Document> items(CatalogKind kind) { return items.getOrDefault(kind, List.of()); }

    public int count(CatalogKind kind) { return items(kind).size(); }

    public int totalCount() {
        int n = 0;
        for (CatalogKind k : CatalogKind.values()) n += count(k);
        return n;
    }

    /** Dosya yoksa boş katalog (sürüm 0); bozuksa {@link IllegalArgumentException}. */
    public static CatalogFile read(Resource resource, ObjectMapper json) throws IOException {
        if (resource == null || !resource.exists()) return empty(true);
        JsonNode root;
        try (InputStream in = maybeGunzip(new BufferedInputStream(resource.getInputStream()))) {
            root = json.readTree(in);
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("kök JSON nesnesi değil");
        JsonNode v = root.get("version");
        if (v == null || !v.canConvertToExactIntegral() || !v.canConvertToLong() || v.asLong() < 0) {
            throw new IllegalArgumentException("\"version\" sıfır ya da pozitif bir tam sayı olmalı");
        }
        JsonNode gen = root.get("generatedAt");
        Map<CatalogKind, List<Document>> items = new EnumMap<>(CatalogKind.class);
        for (CatalogKind kind : CatalogKind.values()) items.put(kind, readItems(kind, root.get(kind.fileName())));
        return new CatalogFile(v.asLong(), gen == null || gen.isNull() ? null : gen.asText(), items, false);
    }

    private static InputStream maybeGunzip(BufferedInputStream in) throws IOException {
        in.mark(2);
        int b1 = in.read(), b2 = in.read();
        in.reset();
        return b1 == 0x1f && b2 == 0x8b ? new GZIPInputStream(in, 64 * 1024) : in;
    }

    private static List<Document> readItems(CatalogKind kind, JsonNode array) {
        if (array == null || array.isNull()) return List.of();
        if (!array.isArray()) throw new IllegalArgumentException("\"" + kind.fileName() + "\" bir dizi olmalı");
        Map<String, Document> byKey = new LinkedHashMap<>();
        int skipped = 0;
        for (JsonNode item : array) {
            JsonNode key = item.isObject() ? item.get(kind.keyField()) : null;
            if (key == null || !key.isTextual() || key.asText().isBlank()) {
                skipped++;
                continue;
            }
            Document doc = new Document("_id", key.asText());
            Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                if (!"_id".equals(f.getKey())) doc.append(f.getKey(), toBson(f.getValue()));
            }
            if (byKey.put(key.asText(), doc) != null) log.warn("Katalog: {} içinde aynı anahtar iki kez: {} (sonuncusu kullanıldı)", kind.fileName(), key.asText());
        }
        if (skipped > 0) log.warn("Katalog: {} içinde anahtarı ({}) olmayan {} öğe atlandı", kind.fileName(), kind.keyField(), skipped);
        return new ArrayList<>(byKey.values());
    }

    /** JSON değeri → BSON değeri (içerik yorumlanmaz; sayı türleri korunur). */
    static Object toBson(JsonNode n) {
        return switch (n.getNodeType()) {
            case OBJECT -> {
                Document d = new Document();
                n.fields().forEachRemaining(f -> d.append(f.getKey(), toBson(f.getValue())));
                yield d;
            }
            case ARRAY -> {
                List<Object> list = new ArrayList<>(n.size());
                n.forEach(e -> list.add(toBson(e)));
                yield list;
            }
            case STRING -> n.textValue();
            case BOOLEAN -> n.booleanValue();
            case NUMBER -> {
                if (n.isInt()) yield n.intValue();
                if (n.isLong()) yield n.longValue();
                if (n.isBigInteger() && n.canConvertToLong()) yield n.longValue();
                // Kesirli sayılar (ve int64'e sığmayan çok büyük tam sayılar) çift duyarlıklı
                yield n.doubleValue();
            }
            case BINARY -> n.asText();
            default -> null;
        };
    }
}
