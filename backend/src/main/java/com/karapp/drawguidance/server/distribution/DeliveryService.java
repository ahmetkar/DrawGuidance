package com.karapp.drawguidance.server.distribution;

import com.karapp.drawguidance.server.distribution.Dist.Kind;
import com.karapp.drawguidance.server.distribution.Dist.Stage;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.bulk.BulkWriteUpsert;
import org.bson.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v43 gönderimler ({@link Dist#DISPATCHES}) ve cihaz başına teslimatlar ({@link Dist#DELIVERIES}).
 *
 * <p><b>Gönderim</b>: {@code {_id, kind, refId, version, target: {all, uids[]}, status: ACTIVE|CANCELLED|DONE,
 * createdAt, expandedAt?, activeUntil, leaseUntil?}}. Etkin gönderim her işçi turunda hedefteki (yeni kaydolanlar
 * dahil) cihazlara açılır; 30 gün sonra kapanır.
 *
 * <p><b>Teslimat</b>: {@code {_id, dispatchId, kind, refId, version, uid, deviceKey, stage, attempts, nextAttemptAt,
 * lastError, createdAt, updatedAt, infoSentAt, infoReceivedAt, dataSentAt, deliveredAt}}. {@code (deviceKey, kind,
 * refId, version)} benzersizdir. Aşamalar yalnız ileri gider; her geçiş koşullu güncellemedir.
 *
 * <p><b>Yüksek trafik için:</b>
 * <ul>
 *   <li>Gönderimler cihazlara toplu ({@code bulk}, sırasız) yazılır; bir gönderimi aynı anda tek kopya açar (kira).</li>
 *   <li>Yoklama değişiklik yoksa yalnız cihazın {@code inboxVersion} alanını okur (304).</li>
 *   <li>Onaylar toplu gelir ({@link #ack}); bilgi alındı onayıyla veri hemen hazırlanır (ek tur yok).</li>
 *   <li>Teslimat verisi (imzalı adreslerle) içerik ve sürüm başına bellekte tutulur: binlerce cihaz aynı paketi
 *       isteyince imza ve veritabanı okuması bir kez yapılır.</li>
 *   <li>Veri hazırlanamayanlar (ör. depolama geçici olarak yok) işçi tarafından içerik başına toplu yeniden denenir.</li>
 * </ul>
 */
@Service
public class DeliveryService {
    static final Duration ACTIVE_FOR = Duration.ofDays(30);
    static final Duration LEASE = Duration.ofMinutes(5);
    static final int MAX_CACHE = 2000;
    private static final List<String> OPEN = List.of(Stage.QUEUED.name(), Stage.INFO_SENT.name(), Stage.INFO_RECEIVED.name(), Stage.DATA_SENT.name());

    /** Toplu onay girdisi. */
    public record AckItem(String deliveryId, String stage, String error) {}

    private record Cached(Map<String, Object> data, Instant until) {}

    private final MongoTemplate mongo;
    private final ContentService content;
    private final DeviceService devices;
    private final DistributionConfig.Storage storage;
    private final DistributionProperties props;
    private final InboxSignals signals;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public DeliveryService(MongoTemplate mongo, ContentService content, DeviceService devices, DistributionConfig.Storage storage,
                           DistributionProperties props, InboxSignals signals) {
        this.mongo = mongo;
        this.content = content;
        this.devices = devices;
        this.storage = storage;
        this.props = props;
        this.signals = signals;
    }

    // ------------------------------------------------------------------ gönderim (CLI)

    /** Gönderim oluşturur: [uids] boşsa bütün kullanıcılara. */
    public Document dispatch(Kind kind, String refId, List<String> uids) {
        Document src = content.source(kind, refId);
        int version = kind == Kind.PACKAGE ? src.getInteger("version", 1) : 1;
        String ref = kind == Kind.APP_UPDATE ? String.valueOf(((Number) src.get("_id")).longValue()) : src.getString("_id");
        boolean all = uids == null || uids.isEmpty();
        Date now = ContentService.now();
        Document target = new Document("all", all);
        if (!all) target.append("uids", DeviceService.uids(uids));
        Document d = new Document("_id", ContentService.newId("dsp")).append("kind", kind.name()).append("refId", ref).append("version", version)
                .append("target", target).append("status", "ACTIVE").append("createdAt", now)
                .append("activeUntil", Date.from(now.toInstant().plus(ACTIVE_FOR)));
        mongo.insert(d, Dist.DISPATCHES);
        // İçerik değişmiş olabilir: eski önbellek kaydı kullanılmasın
        cache.remove(cacheKey(kind, ref, version));
        return d;
    }

    public void cancelDispatch(String id) {
        Document d = content.require(Dist.DISPATCHES, id, "Gönderim");
        if (!"ACTIVE".equals(d.getString("status"))) throw new IllegalArgumentException("Gönderim zaten kapalı: " + d.getString("status"));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("status", "CANCELLED"), Dist.DISPATCHES);
        mongo.updateMulti(Query.query(Criteria.where("dispatchId").is(id).and("stage").in(Stage.QUEUED.name(), Stage.INFO_SENT.name())),
                new Update().set("stage", Stage.CANCELLED.name()).set("updatedAt", ContentService.now()).set("lastError", "gönderim iptal edildi"),
                Dist.DELIVERIES);
    }

    public List<Document> dispatches(int limit) {
        return mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit), Document.class, Dist.DISPATCHES);
    }

    /** Aşama başına teslimat sayıları (tek toplama sorgusu). */
    public Map<String, Integer> counts(String dispatchId) {
        Aggregation agg = Aggregation.newAggregation(Aggregation.match(Criteria.where("dispatchId").is(dispatchId)),
                Aggregation.group("stage").count().as("n"));
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (Document d : mongo.aggregate(agg, Dist.DELIVERIES, Document.class)) raw.put(d.getString("_id"), ((Number) d.get("n")).intValue());
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Stage s : Stage.values()) if (raw.containsKey(s.name())) out.put(s.name(), raw.get(s.name()));
        return out;
    }

    public List<Document> deliveries(String dispatchId, String uid, int limit) {
        Criteria c = new Criteria();
        if (dispatchId != null) c = c.and("dispatchId").is(dispatchId);
        if (uid != null) c = c.and("uid").is(uid);
        return mongo.find(new Query(c).with(Sort.by(Sort.Direction.DESC, "updatedAt")).limit(limit), Document.class, Dist.DELIVERIES);
    }

    // ------------------------------------------------------------------ işçi adımları

    /** Etkin gönderimleri hedef cihazlara açar; açılan yeni teslimat sayısı. */
    public int expand(Instant now) {
        int created = 0;
        Date nowDate = Date.from(now);
        mongo.updateMulti(Query.query(Criteria.where("status").is("ACTIVE").and("activeUntil").lt(nowDate)), new Update().set("status", "DONE"), Dist.DISPATCHES);
        while (true) {
            // Bir gönderimi aynı anda tek kopya açar (kira); bu turda açılmış olanlar atlanır
            Query q = Query.query(Criteria.where("status").is("ACTIVE")
                    .andOperator(new Criteria().orOperator(Criteria.where("leaseUntil").exists(false), Criteria.where("leaseUntil").lte(nowDate)),
                            new Criteria().orOperator(Criteria.where("expandedAt").exists(false), Criteria.where("expandedAt").lt(nowDate))));
            Document d = mongo.findAndModify(q, new Update().set("leaseUntil", Date.from(now.plus(LEASE))),
                    FindAndModifyOptions.options().returnNew(true), Document.class, Dist.DISPATCHES);
            if (d == null) break;
            try {
                created += expandOne(d, nowDate);
            } finally {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(d.getString("_id"))),
                        new Update().set("expandedAt", nowDate).unset("leaseUntil"), Dist.DISPATCHES);
            }
        }
        return created;
    }

    private int expandOne(Document d, Date nowDate) {
        Document target = d.get("target", Document.class);
        Criteria c = Criteria.where("uid").exists(true);
        if (!Boolean.TRUE.equals(target.getBoolean("all"))) c = Criteria.where("uid").in(target.getList("uids", String.class));
        Date since = d.getDate("expandedAt");
        // Son açılıştan beri görülen cihazlar (yeni kaydolanlar dahil); ilk açılışta hepsi
        if (since != null) c = c.and("lastSeenAt").gte(Date.from(since.toInstant().minusSeconds(120)));
        Kind kind = Kind.valueOf(d.getString("kind"));
        String ref = d.getString("refId");
        int version = d.getInteger("version", 1);
        Query devQ = new Query(c).with(Sort.by(Sort.Direction.ASC, "_id"));
        devQ.fields().include("_id").include("uid").include("packages").include("appVersionCode");
        int created = 0;
        List<Document> batch = new ArrayList<>(props.batchSize());
        try (var stream = mongo.stream(devQ, Document.class, Dist.DEVICES)) {
            var it = stream.iterator();
            while (it.hasNext()) {
                Document dev = it.next();
                if (!needs(kind, ref, version, dev)) continue;
                batch.add(dev);
                if (batch.size() >= props.batchSize()) {
                    created += upsertBatch(batch, d.getString("_id"), kind, ref, version, nowDate);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) created += upsertBatch(batch, d.getString("_id"), kind, ref, version, nowDate);
        return created;
    }

    /** Toplu upsert; yeni açılan teslimatların cihazlarının gelen kutusu sürümü artar ve bekleyenleri uyanır. */
    private int upsertBatch(List<Document> devs, String dispatchId, Kind kind, String ref, int version, Date now) {
        BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, Dist.DELIVERIES);
        for (Document dev : devs) {
            Query q = Query.query(Criteria.where("deviceKey").is(dev.getString("_id")).and("kind").is(kind.name()).and("refId").is(ref).and("version").is(version));
            Update u = new Update().setOnInsert("_id", UUID.randomUUID().toString()).setOnInsert("dispatchId", dispatchId)
                    .setOnInsert("uid", dev.getString("uid")).setOnInsert("stage", Stage.QUEUED.name()).setOnInsert("attempts", 0)
                    .setOnInsert("nextAttemptAt", now).setOnInsert("createdAt", now).setOnInsert("updatedAt", now);
            bulk.upsert(q, u);
        }
        List<BulkWriteUpsert> upserts;
        try {
            BulkWriteResult r = bulk.execute();
            upserts = r.getUpserts();
        } catch (DataAccessException e) {
            // Yarışta aynı teslimatı başka kopya eklediyse (benzersiz dizin): eklenenler yine sayılır
            upserts = e.getCause() instanceof MongoBulkWriteException mb ? mb.getWriteResult().getUpserts() : List.of();
        }
        if (upserts.isEmpty()) return 0;
        List<String> keys = new ArrayList<>(upserts.size());
        for (BulkWriteUpsert up : upserts) keys.add(devs.get(up.getIndex()).getString("_id"));
        devices.bump(keys);
        signals.signal(keys);
        return upserts.size();
    }

    /** Cihazın buna ihtiyacı var mı (paket zaten kurulu / sürüm zaten yüklü değilse). */
    static boolean needs(Kind kind, String ref, int version, Document device) {
        return switch (kind) {
            case PACKAGE -> {
                Document pk = device.get("packages", Document.class);
                Object have = pk == null ? null : pk.get(ref);
                yield !(have instanceof Number n) || n.intValue() < version;
            }
            case APP_UPDATE -> {
                Object v = device.get("appVersionCode");
                yield !(v instanceof Number n) || n.longValue() < Long.parseLong(ref);
            }
            default -> true;
        };
    }

    /**
     * Bilgisi alınmış ama verisi hazırlanamamış teslimatları içerik başına toplu ilerletir (veri hazırlanabiliyorsa
     * {@code DATA_SENT}; içerik silinmişse {@code CANCELLED}); ilerletilen sayı.
     */
    public int prepareData(Instant now) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("stage").is(Stage.INFO_RECEIVED.name()).and("nextAttemptAt").lte(Date.from(now))),
                Aggregation.group("kind", "refId", "version"), Aggregation.limit(500));
        int moved = 0;
        for (Document g : mongo.aggregate(agg, Dist.DELIVERIES, Document.class)) {
            Document id = g.get("_id", Document.class);
            Kind kind = Kind.valueOf(id.getString("kind"));
            String ref = id.getString("refId");
            int version = id.getInteger("version", 1);
            Criteria group = Criteria.where("stage").is(Stage.INFO_RECEIVED.name()).and("kind").is(kind.name()).and("refId").is(ref).and("version").is(version);
            try {
                build(kind, ref, version, now);
            } catch (StorageUnavailableException e) {
                mongo.updateMulti(new Query(group), new Update().set("nextAttemptAt", Date.from(now.plus(Duration.ofMinutes(10)))).set("lastError", e.getMessage()), Dist.DELIVERIES);
                continue;
            } catch (RuntimeException e) {
                mongo.updateMulti(new Query(group), new Update().set("stage", Stage.CANCELLED.name()).set("updatedAt", ContentService.now())
                        .set("lastError", String.valueOf(e.getMessage())), Dist.DELIVERIES);
                continue;
            }
            while (true) {
                Query page = new Query(group).limit(props.batchSize());
                page.fields().include("_id").include("deviceKey");
                List<Document> rows = mongo.find(page, Document.class, Dist.DELIVERIES);
                if (rows.isEmpty()) break;
                List<String> ids = new ArrayList<>(rows.size());
                Set<String> keys = new HashSet<>();
                for (Document r : rows) {
                    ids.add(r.getString("_id"));
                    keys.add(r.getString("deviceKey"));
                }
                Date at = ContentService.now();
                moved += (int) mongo.updateMulti(Query.query(Criteria.where("_id").in(ids).and("stage").is(Stage.INFO_RECEIVED.name())),
                        new Update().set("stage", Stage.DATA_SENT.name()).set("dataSentAt", at).set("updatedAt", at), Dist.DELIVERIES).getModifiedCount();
                devices.bump(keys);
                signals.signal(keys);
            }
        }
        return moved;
    }

    // ------------------------------------------------------------------ cihaz tarafı (gelen kutusu)

    /**
     * Cihazın bekleyen teslimatları. Bilgisi henüz gitmemiş olanlar bu yanıtla gitmiş sayılır ({@code QUEUED →
     * INFO_SENT}, tek toplu güncelleme). Gerekmeyen güncellemeler (sürüm zaten yüklü) iptal edilir.
     */
    public List<Map<String, Object>> inbox(String uid, String deviceKey) {
        Document device = devices.get(deviceKey);
        List<Map<String, Object>> out = new ArrayList<>();
        Query q = new Query(Criteria.where("uid").is(uid).and("deviceKey").is(deviceKey).and("stage").in(OPEN))
                .with(Sort.by(Sort.Direction.ASC, "createdAt")).limit(200);
        List<String> queued = new ArrayList<>();
        List<String> obsolete = new ArrayList<>();
        for (Document d : mongo.find(q, Document.class, Dist.DELIVERIES)) {
            Kind kind = Kind.valueOf(d.getString("kind"));
            String id = d.getString("_id");
            if (device != null && !needs(kind, d.getString("refId"), d.getInteger("version", 1), device)) {
                obsolete.add(id);
                continue;
            }
            Stage stage = Stage.valueOf(d.getString("stage"));
            if (stage == Stage.QUEUED) {
                queued.add(id);
                stage = Stage.INFO_SENT;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deliveryId", id);
            item.put("kind", kind.name());
            item.put("refId", d.getString("refId"));
            item.put("version", d.getInteger("version", 1));
            item.put("stage", stage.name());
            item.put("dataReady", stage == Stage.DATA_SENT);
            out.add(item);
        }
        Date now = ContentService.now();
        if (!queued.isEmpty()) {
            mongo.updateMulti(Query.query(Criteria.where("_id").in(queued).and("stage").is(Stage.QUEUED.name())),
                    new Update().set("stage", Stage.INFO_SENT.name()).set("infoSentAt", now).set("updatedAt", now), Dist.DELIVERIES);
        }
        if (!obsolete.isEmpty()) {
            mongo.updateMulti(Query.query(Criteria.where("_id").in(obsolete).and("stage").in(OPEN)),
                    new Update().set("stage", Stage.CANCELLED.name()).set("updatedAt", now).set("lastError", "zaten kurulu"), Dist.DELIVERIES);
        }
        return out;
    }

    /**
     * Cihazın toplu onayı: her öğe için {@code INFO_RECEIVED}, {@code DELIVERED} ya da {@code FAILED}. Bilgi alındı
     * onayında verisi hazırlanabilen teslimat hemen {@code DATA_SENT} olur. Öğe başına sonuç aşaması (başka cihazın
     * teslimatı ya da bilinmeyen kimlik: {@code NOT_FOUND}; sırası gelmemiş: {@code NOT_READY}).
     */
    public Map<String, String> ack(String uid, String deviceKey, List<AckItem> items, Instant now) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("items boş");
        if (items.size() > 200) throw new IllegalArgumentException("Bir istekte en çok 200 onay");
        List<String> ids = items.stream().map(AckItem::deliveryId).filter(java.util.Objects::nonNull).toList();
        Map<String, Document> own = new LinkedHashMap<>();
        for (Document d : mongo.find(Query.query(Criteria.where("_id").in(ids).and("uid").is(uid).and("deviceKey").is(deviceKey)), Document.class, Dist.DELIVERIES)) {
            own.put(d.getString("_id"), d);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (AckItem a : items) {
            Document d = a.deliveryId() == null ? null : own.get(a.deliveryId());
            if (d == null) {
                out.put(String.valueOf(a.deliveryId()), "NOT_FOUND");
                continue;
            }
            out.put(a.deliveryId(), ackOne(d, deviceKey, a, now));
        }
        return out;
    }

    private String ackOne(Document d, String deviceKey, AckItem a, Instant now) {
        String id = d.getString("_id");
        Stage current = Stage.valueOf(d.getString("stage"));
        String s = a.stage() == null ? "" : a.stage().trim().toUpperCase(java.util.Locale.ROOT);
        Kind kind = Kind.valueOf(d.getString("kind"));
        switch (s) {
            case "INFO_RECEIVED" -> {
                if (current == Stage.QUEUED || current == Stage.INFO_SENT) {
                    advance(id, List.of(Stage.QUEUED, Stage.INFO_SENT), Stage.INFO_RECEIVED, null);
                    current = Stage.INFO_RECEIVED;
                }
                if (current == Stage.INFO_RECEIVED) {
                    // Veri hazırsa hemen gönderilmiş sayılır (cihaz aynı turda indirebilir)
                    try {
                        build(kind, d.getString("refId"), d.getInteger("version", 1), now);
                        if (advance(id, List.of(Stage.INFO_RECEIVED), Stage.DATA_SENT, null)) current = Stage.DATA_SENT;
                    } catch (RuntimeException e) {
                        // işçi yeniden dener
                    }
                }
                return current.name();
            }
            case "DELIVERED" -> {
                if (current == Stage.DELIVERED) return current.name();
                if (current != Stage.DATA_SENT) return "NOT_READY";
                if (advance(id, List.of(Stage.DATA_SENT), Stage.DELIVERED, null) && kind == Kind.PACKAGE) {
                    devices.markPackage(deviceKey, d.getString("refId"), d.getInteger("version", 1));
                }
                return Stage.DELIVERED.name();
            }
            case "FAILED" -> {
                if (current.finished()) return current.name();
                String e = ContentService.line("hata", a.error(), 300, false);
                advance(id, List.of(Stage.INFO_SENT, Stage.INFO_RECEIVED, Stage.DATA_SENT), Stage.FAILED, new Update().set("lastError", e == null ? "cihaz işleyemedi" : e));
                return Stage.FAILED.name();
            }
            default -> {
                return "INVALID_STAGE";
            }
        }
    }

    /** Aşama geçişi (yalnız [from] aşamasındaysa). */
    boolean advance(String id, List<Stage> from, Stage to, Update extra) {
        Update u = extra == null ? new Update() : extra;
        Date now = ContentService.now();
        u.set("stage", to.name()).set("updatedAt", now);
        switch (to) {
            case INFO_SENT -> u.set("infoSentAt", now);
            case INFO_RECEIVED -> u.set("infoReceivedAt", now).set("nextAttemptAt", now);
            case DATA_SENT -> u.set("dataSentAt", now);
            case DELIVERED -> u.set("deliveredAt", now);
            default -> { }
        }
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("stage").in(from.stream().map(Stage::name).toList())), u, Dist.DELIVERIES)
                .getModifiedCount() > 0;
    }

    /** Teslimatın verisi (yalnız veri gönderildikten sonra). */
    public Map<String, Object> data(String uid, String deviceKey, String deliveryId, Instant now) {
        Document d = mongo.findOne(Query.query(Criteria.where("_id").is(deliveryId).and("uid").is(uid).and("deviceKey").is(deviceKey)), Document.class, Dist.DELIVERIES);
        if (d == null) throw new IllegalArgumentException("Teslimat bulunamadı");
        Stage stage = Stage.valueOf(d.getString("stage"));
        if (stage != Stage.DATA_SENT && stage != Stage.DELIVERED) throw new IllegalStateException("Veri henüz hazır değil");
        return build(Kind.valueOf(d.getString("kind")), d.getString("refId"), d.getInteger("version", 1), now);
    }

    /** Verinin kendisi (imzalı adreslerle); içerik ve sürüm başına önbellekte. */
    public Map<String, Object> build(Kind kind, String ref, int version, Instant now) {
        String key = cacheKey(kind, ref, version);
        Cached c = cache.get(key);
        if (c != null && now.isBefore(c.until())) return c.data();
        Map<String, Object> data = java.util.Collections.unmodifiableMap(buildFresh(kind, ref, version, now));
        if (cache.size() >= MAX_CACHE) cache.entrySet().removeIf(e -> !now.isBefore(e.getValue().until()));
        if (cache.size() >= MAX_CACHE) cache.clear();
        cache.put(key, new Cached(data, now.plus(props.dataCacheTtl())));
        return data;
    }

    /** İçerik değişti (CLI): önbellekteki eski verisi atılır. */
    public void evict(Kind kind, String ref) {
        String prefix = kind.name() + ":" + ref + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String cacheKey(Kind kind, String ref, int version) { return kind.name() + ":" + ref + ":" + version; }

    private Map<String, Object> buildFresh(Kind kind, String ref, int version, Instant now) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind.name());
        switch (kind) {
            case NOTIFICATION -> {
                Document n = content.require(Dist.NOTIFICATIONS, ref, "Bildirim");
                out.put("id", ref);
                out.put("title", n.getString("title"));
                out.put("body", n.getString("body"));
            }
            case ANNOUNCEMENT -> {
                Document a = content.require(Dist.ANNOUNCEMENTS, ref, "Duyuru");
                out.put("id", ref);
                out.put("title", a.getString("title"));
                out.put("body", a.getString("body"));
                out.put("imageUrl", resolve(a.getString("imageUrl"), now));
            }
            case PACKAGE -> {
                Document p = content.requirePackage(ref);
                if (p.getInteger("version", 1) != version) throw new IllegalStateException("Paketin bu sürümü artık yok (yeni sürüm gönderilecek)");
                out.put("id", ref);
                out.put("name", p.getString("name"));
                out.put("mode", p.getString("mode"));
                out.put("version", version);
                List<Map<String, Object>> items = new ArrayList<>();
                for (Document i : p.getList("items", Document.class)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", i.getString("id"));
                    m.put("window", i.getString("window"));
                    m.put("title", i.getString("title"));
                    if (i.getString("category") != null) m.put("category", i.getString("category"));
                    m.put("url", resolve(i.getString("url"), now));
                    items.add(m);
                }
                out.put("items", items);
            }
            case APP_UPDATE -> {
                Document r = content.require(Dist.RELEASES, Long.parseLong(ref), "Sürüm");
                out.put("versionCode", ((Number) r.get("_id")).longValue());
                out.put("versionName", r.getString("versionName"));
                out.put("message", r.getString("message"));
                out.put("minSupportedVersionCode", ((Number) r.get("minSupportedVersionCode")).longValue());
            }
        }
        return out;
    }

    private String resolve(String url, Instant now) {
        String r = storage.resolve(url, now);
        if (r == null) throw new StorageUnavailableException("Resim adresi imzalanamadı (Cloud Storage ayarlı değil ya da bucket farklı): " + url);
        return r;
    }

    /** Cloud Storage ayarlı değil: veri şu an verilemez (503). */
    public static class StorageUnavailableException extends RuntimeException {
        public StorageUnavailableException(String m) { super(m); }
    }
}
