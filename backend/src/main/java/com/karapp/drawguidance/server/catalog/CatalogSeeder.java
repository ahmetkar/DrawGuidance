package com.karapp.drawguidance.server.catalog;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.karapp.drawguidance.server.config.StartupTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Açılışta katalog eşitlemesi: jar içindeki tohum dosyası ({@code drawguidance.catalog.seed-resource}, öntanımlı
 * {@code classpath:catalog/catalog.json.gz}) ile {@code catalog_*} koleksiyonları.
 *
 * <p><b>Ne zaman:</b> veritabanında hiç tamamlanmış eşitleme yoksa, dosya sürümü veritabanındakinden büyükse ya da
 * dosyada öğe varken katalog koleksiyonları boşsa. Dosyadaki sürüm daha küçükse (eski jar) dokunulmaz. Dosya yoksa
 * katalog boş sayılır (sürüm 0): ilk açılışta {@code catalog_meta} sürüm 0 ile yazılır, veritabanında daha yeni bir
 * katalog varsa korunur. Dosya bozuksa hata yazılır, veritabanındaki katalog değiştirilmez.
 *
 * <p><b>Nasıl:</b> her bölümde dosyadaki öğeler {@code _id} = anahtar ile yerine yazılır (yoksa eklenir, değişen
 * güncellenir, aynısı değişmez), dosyada olmayan anahtarlar silinir; en son {@code catalog_meta} sürümü, sayılar ve
 * zaman yazılır. Okuyucular sürümü bu son adımla görür.
 *
 * <p><b>Birden çok sunucu aynı anda açılırsa:</b> eşitlemeyi {@code catalog_meta.lock} kirası alan tek sunucu
 * yapar (kira koşullu tek komutla alınır; belge yoksa eşzamanlı eklemelerden biri yinelenen anahtar hatası alır).
 * Kira her toplu yazmadan sonra uzatılır; sahibi çökerse süresi dolunca ({@link #LEASE}) başkası devralır, kirasını
 * kaybeden sunucu sürümü yazamaz. Kirayı alamayanlar bekler, sonra durumu yeniden değerlendirir (çoğunlukla
 * "güncel"). Kirayı alan da işe başlamadan önce sürümü yeniden okur. Yazmalar tekrar çalıştırılabilir (yerine yazma).
 */
@Component
public class CatalogSeeder extends StartupTask {

    public enum Outcome { SEEDED, UP_TO_DATE, BUSY }

    /** Eşitleme sonucu ({@code version}: sonrasında veritabanındaki sürüm). */
    public record Result(Outcome outcome, long version, int inserted, int updated, int deleted) {}

    static final Duration LEASE = Duration.ofSeconds(60);
    static final Duration WAIT = Duration.ofSeconds(150);
    static final Duration POLL = Duration.ofMillis(500);
    static final int BATCH = 200;

    private final MongoTemplate mongo;
    private final ResourceLoader resources;
    private final ObjectMapper json;
    private final String seedResource;
    private final String instanceId = System.getenv().getOrDefault("HOSTNAME", "sunucu") + "/" + UUID.randomUUID();

    public CatalogSeeder(MongoTemplate mongo, ResourceLoader resources, ObjectMapper json, DrawGuidanceProperties props) {
        super("katalog eşitlemesi", Duration.ofSeconds(15));
        this.mongo = mongo;
        this.resources = resources;
        this.json = json;
        this.seedResource = props.catalog().seedResource();
    }

    @Override
    protected void runTask() {
        CatalogFile file = load(resources.getResource(seedResource));
        if (file != null) sync(file);
    }

    /** Dosyayı okuyup eşitler (dosya bozuksa {@code null}). */
    public Result sync(Resource resource) {
        CatalogFile file = load(resource);
        return file == null ? null : sync(file);
    }

    CatalogFile load(Resource resource) {
        try {
            CatalogFile file = CatalogFile.read(resource, json);
            if (file.missing()) log.warn("Katalog tohum dosyası bulunamadı ({}); katalog boş (sürüm 0) sayılır", resource);
            return file;
        } catch (IOException | RuntimeException e) {
            log.error("Katalog tohum dosyası okunamadı ({}): {}; veritabanındaki katalog değiştirilmedi", resource, e.getMessage());
            return null;
        }
    }

    public Result sync(CatalogFile file) {
        Instant deadline = Instant.now().plus(WAIT);
        while (true) {
            CatalogMeta meta = readMeta();
            String reason = reseedReason(meta, file);
            if (reason == null) return upToDate(meta, file);
            if (tryLock(file.version())) {
                try {
                    // Kira alınırken başka bir sunucu eşitlemeyi bitirmiş olabilir
                    meta = readMeta();
                    reason = reseedReason(meta, file);
                    if (reason == null) return upToDate(meta, file);
                    return apply(meta, file, reason);
                } finally {
                    unlock();
                }
            }
            if (Instant.now().isAfter(deadline)) {
                log.warn("Katalog: başka bir sunucu eşitlemeyi {} sn içinde bitirmedi; beklemeden devam ediliyor", WAIT.toSeconds());
                return new Result(Outcome.BUSY, meta.versionOrZero(), 0, 0, 0);
            }
            try {
                Thread.sleep(POLL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Result(Outcome.BUSY, meta.versionOrZero(), 0, 0, 0);
            }
        }
    }

    /** Eşitleme gerekiyorsa nedeni, gerekmiyorsa {@code null}. */
    private String reseedReason(CatalogMeta meta, CatalogFile file) {
        if (meta.version() == null) return "ilk eşitleme";
        if (file.version() > meta.version()) return "yeni sürüm";
        if (file.totalCount() > 0 && catalogEmpty()) return "veritabanında katalog boş";
        return null;
    }

    private Result upToDate(CatalogMeta meta, CatalogFile file) {
        long dbVersion = meta.versionOrZero();
        if (file.missing() && dbVersion > 0) log.info("Katalog dosyası yok; veritabanındaki katalog kullanılıyor (sürüm {})", dbVersion);
        else if (file.version() < dbVersion) log.info("Katalog: veritabanındaki sürüm ({}) dosyadakinden ({}) yeni; değiştirilmedi", dbVersion, file.version());
        else log.info("Katalog güncel (sürüm {})", dbVersion);
        return new Result(Outcome.UP_TO_DATE, dbVersion, 0, 0, 0);
    }

    private Result apply(CatalogMeta before, CatalogFile file, String reason) {
        int inserted = 0, updated = 0, deleted = 0;
        Document counts = new Document();
        for (CatalogKind kind : CatalogKind.values()) {
            MongoCollection<Document> coll = mongo.getCollection(kind.collection());
            List<Document> items = file.items(kind);
            for (int i = 0; i < items.size(); i += BATCH) {
                List<WriteModel<Document>> ops = new ArrayList<>();
                for (Document d : items.subList(i, Math.min(items.size(), i + BATCH))) {
                    ops.add(new ReplaceOneModel<>(Filters.eq("_id", d.get("_id")), d, new ReplaceOptions().upsert(true)));
                }
                BulkWriteResult r = coll.bulkWrite(ops, new BulkWriteOptions().ordered(false));
                inserted += r.getUpserts().size();
                updated += r.getModifiedCount();
                renewLock();
            }
            List<Object> keys = items.stream().map(d -> d.get("_id")).toList();
            deleted += (int) coll.deleteMany(Filters.nin("_id", keys)).getDeletedCount();
            counts.append(kind.fileName(), items.size());
            // v34 türe göre süzme ({@code ?kind=}) için dizin
            if (kind != CatalogKind.MODE_NETS) coll.createIndex(com.mongodb.client.model.Indexes.ascending(CatalogMeta.KINDS_FIELD));
            renewLock();
        }
        UpdateResult done = meta().updateOne(mine(), Updates.combine(
                Updates.set(CatalogMeta.VERSION, file.version()),
                Updates.set(CatalogMeta.GENERATED, file.generatedAt()),
                Updates.set(CatalogMeta.UPDATED, new Date()),
                Updates.set(CatalogMeta.COUNTS, counts),
                Updates.unset(CatalogMeta.LOCK)));
        if (done.getMatchedCount() == 0) throw new IllegalStateException("Katalog eşitleme kirası kaybedildi; sürüm yazılmadı");
        log.info("Katalog eşitlendi ({}): sürüm {} → {}; şablon {}, vektör {}, ağ {}; eklenen {}, değişen {}, silinen {}",
                reason, before.versionOrZero(), file.version(), file.count(CatalogKind.TEMPLATES), file.count(CatalogKind.VECTORS),
                file.count(CatalogKind.MODE_NETS), inserted, updated, deleted);
        return new Result(Outcome.SEEDED, file.version(), inserted, updated, deleted);
    }

    private MongoCollection<Document> meta() { return mongo.getCollection(Stores.CATALOG_META); }

    private CatalogMeta readMeta() { return CatalogMeta.from(meta().find(Filters.eq("_id", CatalogMeta.ID)).first()); }

    private boolean catalogEmpty() {
        for (CatalogKind kind : CatalogKind.values()) {
            if (mongo.getCollection(kind.collection()).find().projection(Projections.include("_id")).limit(1).first() != null) return false;
        }
        return true;
    }

    private Bson mine() { return Filters.and(Filters.eq("_id", CatalogMeta.ID), Filters.eq(CatalogMeta.LOCK + ".owner", instanceId)); }

    /** Kira: kilit yoksa ya da süresi dolmuşsa tek koşullu komutla alınır. */
    private boolean tryLock(long target) {
        Instant now = Instant.now();
        Document lock = new Document("owner", instanceId).append("target", target)
                .append("since", Date.from(now)).append("until", Date.from(now.plus(LEASE)));
        try {
            meta().updateOne(
                    Filters.and(Filters.eq("_id", CatalogMeta.ID),
                            Filters.or(Filters.eq(CatalogMeta.LOCK, null), Filters.lt(CatalogMeta.LOCK + ".until", Date.from(now)))),
                    Updates.set(CatalogMeta.LOCK, lock), new UpdateOptions().upsert(true));
            return true;
        } catch (MongoWriteException e) {
            // Kilit başkasında: koşul tutmadı, ekleme de aynı _id'ye çarptı
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) return false;
            throw e;
        }
    }

    private void renewLock() {
        UpdateResult r = meta().updateOne(mine(), Updates.set(CatalogMeta.LOCK + ".until", Date.from(Instant.now().plus(LEASE))));
        if (r.getMatchedCount() == 0) throw new IllegalStateException("Katalog eşitleme kirası kaybedildi (başka bir sunucu devraldı)");
    }

    private void unlock() {
        try {
            meta().updateOne(mine(), Updates.unset(CatalogMeta.LOCK));
        } catch (RuntimeException e) {
            log.warn("Katalog eşitleme kirası bırakılamadı (süresi dolunca kendiliğinden düşer): {}", e.getMessage());
        }
    }
}
