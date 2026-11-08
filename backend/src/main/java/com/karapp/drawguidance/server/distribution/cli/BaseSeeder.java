package com.karapp.drawguidance.server.distribution.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karapp.drawguidance.server.distribution.ContentService;
import com.karapp.drawguidance.server.distribution.ContentService.ItemInput;
import com.karapp.drawguidance.server.distribution.Dist;
import com.karapp.drawguidance.server.distribution.DistributionConfig;
import com.karapp.drawguidance.server.distribution.GcsClient;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v43 taban paketleri: uygulamanın içinde gelen pencere resimleri (çizgi roman nesneleri, efektleri, mekânları,
 * karakterleri, diyalog kutuları ve şekilleri; not şekilleri ve diyagramları; resim şekilleri) Cloud Storage'a
 * yüklenir ve modun taban paketi olarak kaydedilir.
 *
 * <p>Girdi klasörü ({@code tools/base_packages} üretir): {@code manifest.json} = {@code {"packages": [{"id",
 * "name", "mode", "items": [{"id", "window", "title", "category"?, "file"}]}]}} ve {@code file} yolundaki resimler.
 * Nesne adı {@code base/<paket>/<dosya>}; aynı içerik (MD5) zaten varsa yeniden yüklenmez. Kayıtta adres
 * {@code gs://<bucket>/base/<paket>/<dosya>}. Paket zaten varsa öğeleri değiştirilir (yayımlanmışsa sürümü artar);
 * yoksa eklenir ve yayımlanır. {@code --kuru}: hiçbir şey yazılmaz, yalnız sayılar gösterilir.
 */
@Component
public class BaseSeeder {
    private final ContentService content;
    private final DistributionConfig.Storage storage;
    private final MongoTemplate mongo;
    private final ObjectMapper json;

    public BaseSeeder(ContentService content, DistributionConfig.Storage storage, MongoTemplate mongo, ObjectMapper json) {
        this.content = content;
        this.storage = storage;
        this.mongo = mongo;
        this.json = json;
    }

    public void run(Path dir, boolean upload, boolean dryRun, PrintStream out) throws IOException {
        run(dir, upload, dryRun, false, out);
    }

    /**
     * @param uploadOnly yalnız Cloud Storage'a yükle ve {@code bundled-manifest.json} üret; Mongo'ya yazma
     *                   (sunucu açılışta {@link BasePackagesStartup} ile kaydeder). Mongo dışarı açılmadığında
     *                   bilgisayardan eşitleme için. {@code true} ise yükleme kendiliğinden açıktır.
     */
    public void run(Path dir, boolean upload, boolean dryRun, boolean uploadOnly, PrintStream out) throws IOException {
        if (uploadOnly) upload = true;
        Path manifest = dir.resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) throw new IllegalArgumentException("manifest.json bulunamadı: " + manifest);
        if (storage.bucket() == null) throw new IllegalStateException("GCS_BUCKET ayarlı değil");
        if (upload && storage.client() == null) throw new IllegalStateException("Yüklemek için GCS_SERVICE_ACCOUNT_JSON gerekli");
        JsonNode root = json.readTree(manifest.toFile());
        Path base = dir.toRealPath();
        for (JsonNode p : root.path("packages")) {
            String id = ContentServiceIds.id(p.path("id").asText());
            String mode = Dist.mode(p.path("mode").asText());
            List<ItemInput> items = new ArrayList<>();
            List<Path> files = new ArrayList<>();
            List<String> objects = new ArrayList<>();
            for (JsonNode i : p.path("items")) {
                String rel = i.path("file").asText();
                Path f = base.resolve(rel).normalize();
                if (!f.startsWith(base) || !Files.isRegularFile(f)) throw new IllegalArgumentException("Dosya yok ya da klasör dışında: " + rel);
                String object = "base/" + id + "/" + rel.replace('\\', '/');
                files.add(f);
                objects.add(object);
                items.add(new ItemInput(i.path("window").asText(), i.path("title").asText(), i.path("category").asText(null),
                        "gs://" + storage.bucket() + "/" + object, i.path("id").asText(null)));
            }
            out.printf("%s (%s): %d öğe%n", id, mode, items.size());
            if (dryRun) continue;
            if (upload) uploadAll(files, objects, out);
            if (uploadOnly) out.println("  Mongo atlandı (sunucu açılışta bundled-manifest.json'dan kaydeder)");
            else out.println("  " + save(id, p.path("name").asText(), mode, items));
        }
        if (!dryRun && upload) {
            // Sunucuyla birlikte gelen kopya: açılışta kayıtları kendiliğinden oluşturur (BasePackagesStartup)
            Path bundled = dir.resolve("bundled-manifest.json");
            json.writerWithDefaultPrettyPrinter().writeValue(bundled.toFile(), stampBundled(root, storage.bucket(), Instant.now()));
            out.println("Sunucu kaynağı için: " + bundled);
        }
    }

    /** Sunucuyla gelen listede üretim anının alanı (ISO-8601, UTC). */
    public static final String GENERATED_AT = "generatedAt";

    /**
     * v43 sunucuyla birlikte gelen taban paket listesini (dosyalar zaten Cloud Storage'da) veritabanına işler:
     * eksik paket eklenir. Liste başka bir kovaya yüklenmişse (kovada dosyalar yok) atlanır.
     *
     * <p>v49.1: var olan bir paket yalnız liste ondan YENİYSE değiştirilir: listenin {@code generatedAt} anı paketin
     * veritabanındaki {@code updatedAt} anından sonra olmalı. Böylece CLI ile ({@code paket guncelle},
     * {@code taban-paket --yukle}) yapılan bir değişikliği, eski listeyi taşıyan bir sunucu kopyası yeniden
     * başlayınca geri almaz. {@code generatedAt} olmayan (eski) listeler var olan paketlere hiç dokunmaz.
     *
     * @return işlenen paket sayısı
     */
    public int registerBundled(JsonNode root, PrintStream out) {
        String bucket = root.path("bucket").asText("");
        if (storage.bucket() == null || !storage.bucket().equals(bucket)) {
            out.println("Taban paketler atlandı: kova " + (storage.bucket() == null ? "ayarlı değil" : storage.bucket() + " (liste " + bucket + " için)"));
            return 0;
        }
        Instant generatedAt = generatedAt(root);
        int n = 0;
        for (JsonNode p : root.path("packages")) {
            String id = ContentServiceIds.id(p.path("id").asText());
            String mode = Dist.mode(p.path("mode").asText());
            List<ItemInput> items = new ArrayList<>();
            for (JsonNode i : p.path("items")) {
                String rel = i.path("file").asText().replace('\\', '/');
                if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) throw new IllegalArgumentException("Geçersiz dosya yolu: " + rel);
                items.add(new ItemInput(i.path("window").asText(), i.path("title").asText(), i.path("category").asText(null),
                        "gs://" + bucket + "/base/" + id + "/" + rel, i.path("id").asText(null)));
            }
            Document existing = mongo.findById(id, Document.class, Dist.PACKAGES);
            String r;
            if (existing != null && !bundledMayReplace(existing.getDate("updatedAt"), generatedAt)) {
                r = generatedAt == null
                        ? "dokunulmadı (listede generatedAt yok; veritabanındaki " + existing.getList("items", Document.class).size() + " öğe korunur)"
                        : "dokunulmadı (veritabanındaki paket listeden yeni; " + existing.getList("items", Document.class).size() + " öğe korunur)";
            } else {
                r = save(id, p.path("name").asText(), mode, items);
            }
            out.printf("%s (%s): %d öğe, %s%n", id, mode, items.size(), r);
            n++;
        }
        return n;
    }

    /**
     * Açılıştaki liste var olan paketi değiştirebilir mi: listenin üretim anı paketin son değişikliğinden sonra
     * olmalı. Üretim anı yoksa (eski liste) asla; paketin değişiklik anı yoksa (çok eski kayıt) evet.
     */
    static boolean bundledMayReplace(Date dbUpdatedAt, Instant generatedAt) {
        if (generatedAt == null) return false;
        if (dbUpdatedAt == null) return true;
        return generatedAt.isAfter(dbUpdatedAt.toInstant());
    }

    /**
     * Sunucuyla gelecek listeyi işaretler: kova ve v49.1'den beri üretim anı ({@code generatedAt}). Sunucu açılışta
     * yalnız bu andan sonra değişmemiş paketleri günceller.
     */
    static com.fasterxml.jackson.databind.node.ObjectNode stampBundled(JsonNode root, String bucket, Instant now) {
        com.fasterxml.jackson.databind.node.ObjectNode o = (com.fasterxml.jackson.databind.node.ObjectNode) root;
        o.put("bucket", bucket);
        o.put(GENERATED_AT, now.toString());
        return o;
    }

    /** Listenin {@code generatedAt} alanı; yoksa ya da okunamazsa {@code null}. */
    static Instant generatedAt(JsonNode root) {
        String s = root.path(GENERATED_AT).asText("");
        if (s.isBlank()) return null;
        try {
            return Instant.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Paketi ekler ya da günceller ve yayımlar; ne yapıldığını döndürür. */
    private String save(String id, String name, String mode, List<ItemInput> items) {
        Document existing = mongo.findById(id, Document.class, Dist.PACKAGES);
        if (existing == null) {
            content.addPackage(name, mode, items, true, id);
            content.publishPackage(id);
            return "kaydedildi ve yayımlandı";
        }
        if (same(existing.getList("items", Document.class), items)) {
            if (!"PUBLISHED".equals(existing.getString("status"))) content.publishPackage(id);
            return "değişiklik yok (sürüm " + existing.getInteger("version", 1) + ")";
        }
        content.updatePackage(id, name, null, null, items);
        content.publishPackage(id);
        return "güncellendi (sürüm " + content.requirePackage(id).getInteger("version", 1) + ")";
    }

    private void uploadAll(List<Path> files, List<String> objects, PrintStream out) throws IOException {
        GcsClient gcs = storage.client();
        AtomicInteger sent = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                Path f = files.get(i);
                String object = objects.get(i);
                jobs.add(pool.submit(() -> {
                    byte[] data = Files.readAllBytes(f);
                    String md5 = GcsClient.md5Base64(data);
                    if (md5.equals(gcs.md5(storage.bucket(), object))) {
                        skipped.incrementAndGet();
                    } else {
                        gcs.upload(storage.bucket(), object, data, contentType(f));
                        sent.incrementAndGet();
                    }
                    return null;
                }));
            }
            for (Future<?> j : jobs) {
                try {
                    j.get();
                } catch (Exception e) {
                    Throwable c = e.getCause() == null ? e : e.getCause();
                    throw new IOException(c.getMessage(), c);
                }
            }
        }
        out.printf("  yüklendi: %d, zaten vardı: %d%n", sent.get(), skipped.get());
    }

    /** Kayıtlı öğeler yenileriyle aynı mı (kimlik, pencere, başlık, kategori, adres). */
    static boolean same(List<Document> have, List<ItemInput> next) {
        if (have.size() != next.size()) return false;
        for (int i = 0; i < have.size(); i++) {
            Document h = have.get(i);
            ItemInput n = next.get(i);
            if (!java.util.Objects.equals(h.getString("id"), n.id()) || !java.util.Objects.equals(h.getString("window"), n.window())
                    || !java.util.Objects.equals(h.getString("title"), n.title()) || !java.util.Objects.equals(h.getString("category"), n.category())
                    || !java.util.Objects.equals(h.getString("url"), n.url())) return false;
        }
        return true;
    }

    static String contentType(Path f) {
        String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    /** Kimlik denetimi ContentService'in kuralıyla. */
    static final class ContentServiceIds {
        static String id(String s) {
            if (s == null || s.isBlank()) throw new IllegalArgumentException("Paket kimliği boş");
            String v = s.trim().toLowerCase(Locale.ROOT);
            if (!v.matches("^[a-z0-9][a-z0-9_\\-]{0,79}$")) throw new IllegalArgumentException("Geçersiz paket kimliği: " + s);
            return v;
        }
    }
}
