package com.karapp.drawguidance.server.distribution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * v43 giriş yapmış kullanıcıların cihazları ({@link Dist#DEVICES}): güncelleme ve teslimat için.
 *
 * <p>Belge: {@code {_id: HMAC(deviceId), uid, appVersionCode, packages: {paketKimliği: sürüm}, info: şifreli(model,
 * üretici, Android, dil, uygulama sürüm adı), inboxVersion, inboxChangedAt, createdAt, lastSeenAt}}. Cihaz kimliği düz
 * metin saklanmaz, bilgiler şifrelidir ({@link DeviceCrypto}). Sürüm numarası ve kurulu paketler sorgu için düz tutulur
 * (kişisel veri değildir). {@code inboxVersion} cihazın gelen kutusu her değiştiğinde artar: yoklama değişiklik yoksa
 * tek bir dizinli okumayla 304 döner.
 *
 * <p>Aynı cihazda başka hesapla giriş yapılırsa belge yeni kullanıcıya geçer; eski kullanıcının o cihazdaki bitmemiş
 * teslimatları iptal edilir.
 */
@Service
public class DeviceService {
    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9_\\-]{16,64}$");
    static final int MAX_FIELD = 80;
    static final int MAX_PACKAGES = 500;

    /** Cihazın gönderdiği kayıt gövdesi. */
    public record Registration(String deviceId, Long appVersionCode, String appVersionName, String model,
                               String manufacturer, Integer sdkInt, String locale, List<InstalledPackage> packages) {}

    public record InstalledPackage(String id, Integer version) {}

    private final MongoTemplate mongo;
    private final DeviceCrypto crypto;
    private final ObjectMapper json;

    public DeviceService(MongoTemplate mongo, DeviceCrypto crypto, ObjectMapper json) {
        this.mongo = mongo;
        this.crypto = crypto;
        this.json = json;
    }

    /** Cihaz kimliği biçimi doğruysa belge kimliği. */
    public String key(String deviceId) {
        if (deviceId == null || !DEVICE_ID.matcher(deviceId).matches()) throw new IllegalArgumentException("Geçersiz cihaz kimliği");
        return crypto.deviceKey(deviceId);
    }

    /** Kaydeder ya da günceller; belge kimliği. */
    public String register(String uid, Registration r) {
        if (r == null) throw new IllegalArgumentException("Gövde boş");
        String key = key(r.deviceId());
        Document existing = mongo.findById(key, Document.class, Dist.DEVICES);
        Date now = ContentService.now();
        Map<String, Object> info = new LinkedHashMap<>();
        put(info, "model", r.model());
        put(info, "manufacturer", r.manufacturer());
        if (r.sdkInt() != null && r.sdkInt() > 0 && r.sdkInt() < 1000) info.put("sdkInt", r.sdkInt());
        put(info, "locale", r.locale());
        put(info, "appVersionName", r.appVersionName());
        Update u = new Update().set("uid", uid).set("lastSeenAt", now).setOnInsert("createdAt", now)
                .set("info", crypto.encrypt(toJson(info), key));
        if (r.appVersionCode() != null && r.appVersionCode() > 0) u.set("appVersionCode", r.appVersionCode());
        u.setOnInsert("inboxVersion", 0L);
        if (r.packages() != null) {
            if (r.packages().size() > MAX_PACKAGES) throw new IllegalArgumentException("Çok fazla paket");
            Document pk = new Document();
            for (InstalledPackage p : r.packages()) {
                if (p == null || p.id() == null || p.version() == null || p.version() < 1) continue;
                pk.put(ContentService.checkId(p.id()), p.version());
            }
            u.set("packages", pk);
        }
        mongo.upsert(Query.query(Criteria.where("_id").is(key)), u, Dist.DEVICES);
        if (existing != null && existing.getString("uid") != null && !existing.getString("uid").equals(uid)) {
            // Cihaz başka hesaba geçti: eski hesabın bu cihazdaki bitmemiş teslimatları iptal
            mongo.updateMulti(Query.query(Criteria.where("deviceKey").is(key).and("uid").is(existing.getString("uid"))
                            .and("stage").nin(Dist.Stage.DELIVERED.name(), Dist.Stage.FAILED.name(), Dist.Stage.CANCELLED.name())),
                    new Update().set("stage", Dist.Stage.CANCELLED.name()).set("updatedAt", now).set("lastError", "cihaz başka hesaba geçti"),
                    Dist.DELIVERIES);
        }
        return key;
    }

    /** Cihazdan çıkış: kullanıcı bağı kaldırılır (teslimat yapılmaz), bitmemiş teslimatlar iptal. */
    public void unregister(String uid, String deviceId) {
        String key = key(deviceId);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(key).and("uid").is(uid)),
                new Update().unset("uid"), Dist.DEVICES);
        mongo.updateMulti(Query.query(Criteria.where("deviceKey").is(key).and("uid").is(uid)
                        .and("stage").nin(Dist.Stage.DELIVERED.name(), Dist.Stage.FAILED.name(), Dist.Stage.CANCELLED.name())),
                new Update().set("stage", Dist.Stage.CANCELLED.name()).set("updatedAt", ContentService.now()).set("lastError", "çıkış yapıldı"),
                Dist.DELIVERIES);
    }

    /** Çözülmüş cihaz bilgisi (CLI gösterimi için; çözülemezse boş). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> info(Document device) {
        String plain = crypto.decrypt(device.getString("info"), device.getString("_id"));
        if (plain == null) return Map.of();
        try {
            return json.readValue(plain, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    /** Gelen kutusu değişti: sürüm artar (bekleyen uzun yoklamalar uyanır). */
    public void bump(java.util.Collection<String> keys) {
        if (keys.isEmpty()) return;
        mongo.updateMulti(Query.query(Criteria.where("_id").in(keys)),
                new Update().inc("inboxVersion", 1L).set("inboxChangedAt", ContentService.now()), Dist.DEVICES);
    }

    /** Gelen kutusunun sürümü (cihaz yoksa -1). Yalnız bu alan okunur. */
    public long inboxVersion(String key) {
        Query q = Query.query(Criteria.where("_id").is(key));
        q.fields().include("inboxVersion");
        Document d = mongo.findOne(q, Document.class, Dist.DEVICES);
        if (d == null) return -1;
        Object v = d.get("inboxVersion");
        return v instanceof Number n ? n.longValue() : 0;
    }

    /** [since] sonrası gelen kutusu değişen cihazlar (en çok [limit]). */
    public List<Document> changedSince(Date since, int limit) {
        Query q = Query.query(Criteria.where("inboxChangedAt").gt(since)).limit(limit);
        q.fields().include("_id").include("inboxChangedAt");
        return mongo.find(q, Document.class, Dist.DEVICES);
    }

    public Document get(String key) { return mongo.findById(key, Document.class, Dist.DEVICES); }

    public List<Document> forUsers(List<String> uids) {
        Query q = new Query(Criteria.where("uid").in(uids)).with(Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        return mongo.find(q, Document.class, Dist.DEVICES);
    }

    public List<Document> list(String uid, int limit) {
        Query q = (uid == null ? new Query(Criteria.where("uid").exists(true)) : new Query(Criteria.where("uid").is(uid)))
                .with(Sort.by(Sort.Direction.DESC, "lastSeenAt")).limit(limit);
        return mongo.find(q, Document.class, Dist.DEVICES);
    }

    /** Paket teslim edildi: cihazın kurulu paketleri güncellenir. */
    public void markPackage(String key, String packageId, int version) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(key)), new Update().set("packages." + packageId, version), Dist.DEVICES);
    }

    private static void put(Map<String, Object> m, String k, String v) {
        if (v == null) return;
        String s = v.replaceAll("\\p{Cntrl}", "").trim();
        if (s.isEmpty()) return;
        m.put(k, s.length() > MAX_FIELD ? s.substring(0, MAX_FIELD) : s);
    }

    private String toJson(Map<String, Object> m) {
        try {
            return json.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Hedef kullanıcıların listesi boş değil ve kimlikler düzgün. */
    static List<String> uids(List<String> uids) {
        List<String> out = new ArrayList<>();
        for (String u : uids) {
            String v = u.trim();
            if (v.isEmpty()) continue;
            if (v.length() > 128 || v.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Geçersiz kullanıcı kimliği: " + v);
            out.add(v);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("En az bir kullanıcı gerekli");
        return out;
    }
}
