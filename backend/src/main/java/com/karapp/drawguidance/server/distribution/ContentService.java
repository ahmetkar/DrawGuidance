package com.karapp.drawguidance.server.distribution;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * v43 dağıtılan içerikler (CLI yönetir):
 *
 * <ul>
 *   <li><b>Paket</b> ({@link Dist#PACKAGES}): {@code {_id, name, mode, version, status: DRAFT|PUBLISHED, base, items:
 *       [{id, window, title, category?, url}], createdAt, updatedAt, publishedAt?}}. Öğeler modun pencerelerine aittir
 *       (çizgi roman: nesneler, efektler, mekânlar, diyalog kutuları, şekiller, karakterler; not: şekiller,
 *       diyagramlar; resim: şekiller). Yayımlanmış bir paket değişince sürümü artar ve yeniden gönderilebilir.</li>
 *   <li><b>Bildirim</b> ({@link Dist#NOTIFICATIONS}): {@code {_id, title, body, createdAt, updatedAt}}.</li>
 *   <li><b>Duyuru</b> ({@link Dist#ANNOUNCEMENTS}): {@code {_id, title, body, imageUrl, createdAt, updatedAt}}.</li>
 *   <li><b>Uygulama sürümü</b> ({@link Dist#RELEASES}): {@code {_id: versionCode, versionName, message,
 *       minSupportedVersionCode, createdAt}}.</li>
 * </ul>
 *
 * Bütün yazılar tek satır/çok satır denetiminden geçer, denetim karakterleri atılır; adresler yalnız {@code https://}
 * ya da {@code gs://bucket/nesne} olabilir.
 */
@Service
public class ContentService {
    public static final int MAX_TITLE = 120;
    public static final int MAX_BODY = 1000;
    public static final int MAX_ANNOUNCEMENT_BODY = 4000;
    public static final int MAX_ITEMS = 5000;
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9_\\-]{0,79}$");
    private static final Pattern BUCKET = Pattern.compile("^[a-z0-9][a-z0-9._\\-]{1,220}[a-z0-9]$");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Paket öğesi girdisi. */
    public record ItemInput(String window, String title, String category, String url, String id) {}

    private final MongoTemplate mongo;

    public ContentService(MongoTemplate mongo) { this.mongo = mongo; }

    // ------------------------------------------------------------------ paketler

    public Document addPackage(String name, String mode, List<ItemInput> items, boolean base, String id) {
        String m = Dist.mode(mode);
        String pid = id == null ? newId("pkg") : checkId(id);
        if (mongo.findById(pid, Document.class, Dist.PACKAGES) != null) throw new IllegalArgumentException("Bu kimlikte paket zaten var: " + pid);
        Date now = now();
        Document d = new Document("_id", pid).append("name", line("ad", name, MAX_TITLE, true)).append("mode", m)
                .append("version", 1).append("status", "DRAFT").append("base", base)
                .append("items", items(m, items, new ArrayList<>())).append("createdAt", now).append("updatedAt", now);
        mongo.insert(d, Dist.PACKAGES);
        return d;
    }

    /**
     * Paketi değiştirir: ad, öğe ekleme/çıkarma ya da bütün öğeleri değiştirme. Yayımlanmış paket değişirse sürüm
     * artar (cihazlara yeniden gönderilebilir).
     */
    public Document updatePackage(String id, String name, List<ItemInput> add, Set<String> remove, List<ItemInput> replace) {
        Document d = requirePackage(id);
        String mode = d.getString("mode");
        List<Document> items = new ArrayList<>(d.getList("items", Document.class));
        boolean changed = false;
        if (replace != null) {
            items = items(mode, replace, new ArrayList<>());
            changed = true;
        }
        if (remove != null && !remove.isEmpty()) {
            int before = items.size();
            items.removeIf(i -> remove.contains(i.getString("id")));
            if (items.size() == before) throw new IllegalArgumentException("Çıkarılacak öğe bulunamadı: " + String.join(", ", remove));
            changed = true;
        }
        if (add != null && !add.isEmpty()) {
            items = items(mode, add, items);
            changed = true;
        }
        if (name != null) {
            d.put("name", line("ad", name, MAX_TITLE, true));
            changed = true;
        }
        if (!changed) throw new IllegalArgumentException("Değişiklik yok");
        d.put("items", items);
        if ("PUBLISHED".equals(d.getString("status"))) d.put("version", d.getInteger("version", 1) + 1);
        d.put("updatedAt", now());
        mongo.save(d, Dist.PACKAGES);
        return d;
    }

    public Document publishPackage(String id) {
        Document d = requirePackage(id);
        if (d.getList("items", Document.class).isEmpty()) throw new IllegalArgumentException("Boş paket yayımlanamaz");
        if (!"PUBLISHED".equals(d.getString("status"))) {
            d.put("status", "PUBLISHED");
            d.put("publishedAt", now());
            d.put("updatedAt", now());
            mongo.save(d, Dist.PACKAGES);
        }
        return d;
    }

    public Document requirePackage(String id) {
        Document d = mongo.findById(id, Document.class, Dist.PACKAGES);
        if (d == null) throw new IllegalArgumentException("Paket bulunamadı: " + id);
        return d;
    }

    public List<Document> packages(String mode) {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (mode != null) q.addCriteria(Criteria.where("mode").is(Dist.mode(mode)));
        q.fields().exclude("items");
        return mongo.find(q, Document.class, Dist.PACKAGES);
    }

    private List<Document> items(String mode, List<ItemInput> input, List<Document> into) {
        Set<String> ids = new HashSet<>();
        for (Document i : into) ids.add(i.getString("id"));
        for (ItemInput in : input) {
            String window = Dist.window(mode, in.window());
            String itemId = in.id() == null ? newId("i") : checkId(in.id());
            if (!ids.add(itemId)) throw new IllegalArgumentException("Öğe kimliği yineleniyor: " + itemId);
            Document item = new Document("id", itemId).append("window", window).append("title", line("başlık", in.title(), MAX_TITLE, true))
                    .append("url", url(in.url()));
            String category = line("kategori", in.category(), 60, false);
            if (category != null) item.append("category", category);
            into.add(item);
        }
        if (into.size() > MAX_ITEMS) throw new IllegalArgumentException("Bir pakette en çok " + MAX_ITEMS + " öğe olabilir");
        return into;
    }

    // ------------------------------------------------------------------ bildirim ve duyuru

    public Document addNotification(String title, String body) {
        Date now = now();
        Document d = new Document("_id", newId("ntf")).append("title", line("başlık", title, MAX_TITLE, true))
                .append("body", text("açıklama", body, MAX_BODY, true)).append("createdAt", now).append("updatedAt", now);
        mongo.insert(d, Dist.NOTIFICATIONS);
        return d;
    }

    public Document updateNotification(String id, String title, String body) {
        Document d = require(Dist.NOTIFICATIONS, id, "Bildirim");
        if (title == null && body == null) throw new IllegalArgumentException("Değişiklik yok");
        if (title != null) d.put("title", line("başlık", title, MAX_TITLE, true));
        if (body != null) d.put("body", text("açıklama", body, MAX_BODY, true));
        d.put("updatedAt", now());
        mongo.save(d, Dist.NOTIFICATIONS);
        return d;
    }

    public Document addAnnouncement(String title, String body, String imageUrl) {
        Date now = now();
        Document d = new Document("_id", newId("ann")).append("title", line("başlık", title, MAX_TITLE, true))
                .append("body", text("açıklama", body, MAX_ANNOUNCEMENT_BODY, true)).append("imageUrl", url(imageUrl))
                .append("createdAt", now).append("updatedAt", now);
        mongo.insert(d, Dist.ANNOUNCEMENTS);
        return d;
    }

    public Document updateAnnouncement(String id, String title, String body, String imageUrl) {
        Document d = require(Dist.ANNOUNCEMENTS, id, "Duyuru");
        if (title == null && body == null && imageUrl == null) throw new IllegalArgumentException("Değişiklik yok");
        if (title != null) d.put("title", line("başlık", title, MAX_TITLE, true));
        if (body != null) d.put("body", text("açıklama", body, MAX_ANNOUNCEMENT_BODY, true));
        if (imageUrl != null) d.put("imageUrl", url(imageUrl));
        d.put("updatedAt", now());
        mongo.save(d, Dist.ANNOUNCEMENTS);
        return d;
    }

    // ------------------------------------------------------------------ uygulama sürümleri

    public Document addRelease(long versionCode, String versionName, String message, Long minSupported) {
        if (versionCode <= 0) throw new IllegalArgumentException("versionCode pozitif olmalı");
        if (mongo.findById(versionCode, Document.class, Dist.RELEASES) != null) throw new IllegalArgumentException("Bu sürüm zaten kayıtlı: " + versionCode);
        long min = minSupported == null ? 0 : minSupported;
        if (min < 0 || min > versionCode) throw new IllegalArgumentException("En eski desteklenen sürüm 0 ile " + versionCode + " arasında olmalı");
        Document d = new Document("_id", versionCode).append("versionName", line("sürüm adı", versionName, 40, true))
                .append("message", text("ileti", message, 500, false)).append("minSupportedVersionCode", min).append("createdAt", now());
        mongo.insert(d, Dist.RELEASES);
        return d;
    }

    /** En yeni kayıtlı sürüm (yoksa null). */
    public Document latestRelease() {
        return mongo.findOne(new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(1), Document.class, Dist.RELEASES);
    }

    public List<Document> list(String collection, int limit) {
        return mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit), Document.class, collection);
    }

    public Document require(String collection, Object id, String what) {
        Document d = mongo.findById(id, Document.class, collection);
        if (d == null) throw new IllegalArgumentException(what + " bulunamadı: " + id);
        return d;
    }

    /** Teslimat türünün kaynağı (gönderilebilir mi?). */
    public Document source(Dist.Kind kind, String refId) {
        return switch (kind) {
            case PACKAGE -> {
                Document p = requirePackage(refId);
                if (!"PUBLISHED".equals(p.getString("status"))) throw new IllegalArgumentException("Paket yayımlanmamış (önce: package publish " + refId + ")");
                yield p;
            }
            case NOTIFICATION -> require(Dist.NOTIFICATIONS, refId, "Bildirim");
            case ANNOUNCEMENT -> require(Dist.ANNOUNCEMENTS, refId, "Duyuru");
            case APP_UPDATE -> require(Dist.RELEASES, parseLong(refId), "Sürüm");
        };
    }

    static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Sayı bekleniyordu: " + s);
        }
    }

    // ------------------------------------------------------------------ denetimler

    static String newId(String prefix) {
        byte[] b = new byte[6];
        RANDOM.nextBytes(b);
        return prefix + "_" + HexFormat.of().formatHex(b);
    }

    static String checkId(String id) {
        String v = id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(v).matches()) throw new IllegalArgumentException("Kimlik yalnız küçük harf, rakam, _ ve - içerebilir (en çok 80): " + id);
        return v;
    }

    /** Tek satır yazı (denetim karakterleri atılır). */
    static String line(String field, String value, int max, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new IllegalArgumentException(field + " boş olamaz");
            return null;
        }
        String v = value.replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]", " ").replaceAll("\\s+", " ").trim();
        if (v.length() > max) throw new IllegalArgumentException(field + " en çok " + max + " karakter olabilir");
        return v;
    }

    /** Çok satırlı yazı (satır sonları kalır). */
    static String text(String field, String value, int max, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new IllegalArgumentException(field + " boş olamaz");
            return null;
        }
        String v = value.replace("\r\n", "\n").replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").replaceAll("[\\u202A-\\u202E\\u2066-\\u2069]", "").trim();
        if (v.length() > max) throw new IllegalArgumentException(field + " en çok " + max + " karakter olabilir");
        return v;
    }

    /** Yalnız https:// ya da gs://bucket/nesne. */
    static String url(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Resim adresi boş olamaz");
        String v = value.trim();
        if (v.length() > 2048) throw new IllegalArgumentException("Adres çok uzun");
        if (v.startsWith("gs://")) {
            String[] gs = GcsClient.parse(v);
            if (gs == null || !BUCKET.matcher(gs[0]).matches() || gs[1].contains("..") || gs[1].startsWith("/")) {
                throw new IllegalArgumentException("gs:// adresi gs://<bucket>/<nesne> biçiminde olmalı: " + v);
            }
            return v;
        }
        try {
            URI u = new URI(v);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null) throw new IllegalArgumentException();
            return u.toASCIIString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Adres https:// ya da gs:// ile başlamalı: " + v);
        }
    }

    static Date now() { return Date.from(Instant.now().truncatedTo(ChronoUnit.MILLIS)); }
}
