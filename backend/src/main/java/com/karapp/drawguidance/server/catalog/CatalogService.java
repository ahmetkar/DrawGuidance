package com.karapp.drawguidance.server.catalog;

import com.karapp.drawguidance.server.common.Stores;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * Kataloğun okunması. Her istekte yalnız küçük {@code catalog_meta} belgesi okunur (sürüm → ETag); bölüm gövdeleri
 * sürüm başına bir kez veritabanından okunup JSON'a (v37: istenirse ikili biçime, {@link CatalogBinary}) ve gzip'e
 * çevrilir, bellekte tutulur. İkili gövde yalnız istenince (o sürümdeki ilk ikili istekte) hazırlanır.
 *
 * <p>Önbelleğe yalnız tutarlı okuma alınır: o sırada başka bir sunucu eşitlemiyorsa, okuma boyunca sürüm
 * değişmediyse ve öğe sayısı {@code catalog_meta}'dakiyle aynıysa. (Aksi halde yanıt yine verilir, ama bir sonraki
 * istek yeniden okur.)
 */
@Service
public class CatalogService {

    /** Bölüm yanıtı: {@code {"version": n, "items": [...]}}. */
    public record Items(long version, List<Document> items) {}

    /**
     * Manifest: {@code {"version", "updatedAt", "counts": {"templates", "vectors", "modeNets"}, "binary": {...}}}.
     * v37 {@code binary}: ikili biçimin ortam türü, sürümü ve ikili verilebilen bölümler (eski uygulamalar yok sayar).
     */
    public record Manifest(long version, Instant updatedAt, Counts counts, Binary binary) {}

    public record Counts(int templates, int vectors, int modeNets) {}

    /** v37 ikili biçim bilgisi ({@code sections}: manifestteki {@code counts} anahtarlarıyla aynı adlar). */
    public record Binary(String mediaType, int formatVersion, List<String> sections) {}

    public static final Binary BINARY = new Binary(CatalogBinary.MEDIA_TYPE, CatalogBinary.FORMAT_VERSION,
            Arrays.stream(CatalogKind.values()).filter(CatalogBinary::supports).map(CatalogKind::fileName).toList());

    /** v37 bölüm gövdesinin biçimi. */
    public enum Format { JSON, BINARY }

    /** Hazır gövde ({@code format} biçiminde ve gzip'li hali). */
    public record Body(long version, Format format, byte[] bytes, byte[] gzip) {}

    private final MongoTemplate mongo;
    private final ObjectMapper json;
    private final Map<String, Body> cache = new ConcurrentHashMap<>();
    private final Map<CatalogKind, ReentrantLock> loading = new EnumMap<>(CatalogKind.class);

    public CatalogService(MongoTemplate mongo, ObjectMapper json) {
        this.mongo = mongo;
        this.json = json;
        for (CatalogKind k : CatalogKind.values()) loading.put(k, new ReentrantLock());
    }

    public CatalogMeta meta() {
        return CatalogMeta.from(mongo.getCollection(Stores.CATALOG_META).find(Filters.eq("_id", CatalogMeta.ID)).first());
    }

    public static Manifest manifest(CatalogMeta meta) {
        return new Manifest(meta.versionOrZero(), meta.updatedAt() == null ? Instant.EPOCH : meta.updatedAt(),
                new Counts(meta.count(CatalogKind.TEMPLATES), meta.count(CatalogKind.VECTORS), meta.count(CatalogKind.MODE_NETS)),
                BINARY);
    }

    /** Bölümün o sürümdeki JSON gövdesi (önbellekten ya da veritabanından). */
    public Body items(CatalogKind kind, CatalogMeta meta) { return items(kind, meta, null); }

    /** v34 [canvasKind] verilirse yalnız {@code kinds} alanında o tür olan öğeler (bölüm + tür başına önbellek). */
    public Body items(CatalogKind kind, CatalogMeta meta, String canvasKind) { return items(kind, meta, canvasKind, Format.JSON); }

    /**
     * v37 {@code format} ikiliyse bölüm {@link CatalogBinary} biçiminde (yalnız {@link CatalogBinary#supports}
     * bölümleri). Önbellek bölüm + tür + biçim başına.
     */
    public Body items(CatalogKind kind, CatalogMeta meta, String canvasKind, Format format) {
        if (format == Format.BINARY && !CatalogBinary.supports(kind)) throw new IllegalArgumentException("Bu bölüm ikili biçimde verilmiyor: " + kind.fileName());
        long version = meta.versionOrZero();
        String cacheKey = (canvasKind == null ? kind.name() : kind.name() + ":" + canvasKind) + (format == Format.BINARY ? ":bin" : "");
        Body cached = cache.get(cacheKey);
        if (cached != null && cached.version() == version) return cached;
        // Aynı anda gelen istekler bölümü bir kez okusun (sanal iş parçacıkları için kilit, synchronized değil)
        ReentrantLock lock = loading.get(kind);
        lock.lock();
        try {
            cached = cache.get(cacheKey);
            if (cached != null && cached.version() == version) return cached;
            List<Document> items = load(kind, canvasKind);
            byte[] raw = format == Format.BINARY ? CatalogBinary.encode(kind, version, items) : toJson(new Items(version, items));
            Body body = new Body(version, format, raw, gzip(raw));
            Instant now = Instant.now();
            CatalogMeta after = meta();
            boolean complete = canvasKind != null || items.size() == meta.count(kind);
            if (!meta.seeding(now) && !after.seeding(now) && after.versionOrZero() == version && complete) {
                cache.put(cacheKey, body);
            }
            return body;
        } finally {
            lock.unlock();
        }
    }

    private List<Document> load(CatalogKind kind, String canvasKind) {
        List<Document> out = new ArrayList<>();
        var coll = mongo.getCollection(kind.collection());
        var found = canvasKind == null ? coll.find() : coll.find(Filters.eq(CatalogMeta.KINDS_FIELD, canvasKind));
        for (Document d : found.sort(Sorts.ascending("_id"))) {
            d.remove("_id");
            out.add(d);
        }
        return out;
    }

    private byte[] toJson(Object value) {
        try {
            return json.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] gzip(byte[] raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(512, raw.length / 3));
        try (GZIPOutputStream gz = new GZIPOutputStream(out, 64 * 1024) {{ def.setLevel(Deflater.BEST_COMPRESSION); }}) {
            gz.write(raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
