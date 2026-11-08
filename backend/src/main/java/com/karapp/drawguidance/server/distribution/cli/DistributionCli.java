package com.karapp.drawguidance.server.distribution.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.distribution.ContentService;
import com.karapp.drawguidance.server.distribution.ContentService.ItemInput;
import com.karapp.drawguidance.server.distribution.DeliveryService;
import com.karapp.drawguidance.server.distribution.DeviceService;
import com.karapp.drawguidance.server.distribution.Dist;
import com.karapp.drawguidance.server.distribution.DistributionWorker;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * v43 yönetim komut satırı: paketler, bildirimler, duyurular, uygulama sürümleri ve gönderimler. Sunucunun
 * jar'ıyla çalışır ({@code java -jar drawguidance-server.jar cli <komut>}); aynı veritabanına bağlanır, web sunucusu
 * ve arka plan işçileri açılmaz. Gönderim ayarlanınca asıl gönderimi çalışan sunucunun işçileri yapar.
 *
 * <p>Komutların Türkçe ve İngilizce adları vardır ({@code paket ekle} = {@code package add}). {@code yardim}
 * bütün komutları yazar.
 */
@Component
public class DistributionCli {
    private static final Set<String> FLAGS = Set.of("herkes", "all", "yukle", "upload", "sadece-yukle", "upload-only", "kuru", "dry-run", "gonderme", "no-notify", "yardim", "help", "json");
    private static final Pattern SEP = Pattern.compile("\\|");

    private final ContentService content;
    private final DeliveryService deliveries;
    private final DeviceService devices;
    private final DistributionWorker worker;
    private final BaseSeeder seeder;
    private final MongoTemplate mongo;
    private final ObjectMapper json;
    private final InboxCli inbox;

    public DistributionCli(ContentService content, DeliveryService deliveries, DeviceService devices, DistributionWorker worker,
                           BaseSeeder seeder, MongoTemplate mongo, ObjectMapper json, InboxCli inbox) {
        this.inbox = inbox;
        this.content = content;
        this.deliveries = deliveries;
        this.devices = devices;
        this.worker = worker;
        this.seeder = seeder;
        this.mongo = mongo;
        this.json = json;
    }

    /** Komutu çalıştırır; çıkış kodu (0 başarılı, 1 hata, 2 kullanım hatası). */
    public int run(String[] argv, PrintStream out) {
        if (argv.length == 0) {
            help(out);
            return 2;
        }
        String group = norm(argv[0]);
        String action = argv.length > 1 ? norm(argv[1]) : "";
        Args a = new Args(Arrays.copyOfRange(argv, Math.min(2, argv.length), argv.length), FLAGS);
        try {
            switch (group) {
                case "paket", "package" -> pkg(action, a, out);
                case "bildirim", "notification" -> notification(action, a, out);
                case "duyuru", "announcement" -> announcement(action, a, out);
                case "surum", "release" -> release(action, a, out);
                case "gonder", "send" -> {
                    // gonder <tür> <kimlik>: eylem yok, ilk iki değer tür ve kimlik
                    Args s = new Args(Arrays.copyOfRange(argv, 1, argv.length), FLAGS);
                    send(s, out);
                    a = s;
                }
                case "gonderim", "dispatch" -> dispatch(action, a, out);
                case "teslimat", "delivery" -> delivery(a, out);
                case "kullanici", "user" -> users(a, out);
                case "cihaz", "device" -> deviceList(a, out);
                case "geri-bildirim", "geribildirim", "feedback" -> inbox.feedback(action, a, out);
                case "hata", "report", "reports" -> inbox.report(action, a, out);
                case "destek", "support" -> inbox.support(action, a, out);
                case "silme-talebi", "silme", "deletion" -> inbox.deletion(action, a, out);
                case "isci", "worker" -> out.println("Tur: " + worker.tick());
                case "taban-paket", "base-seed" -> {
                    Args s = new Args(Arrays.copyOfRange(argv, 1, argv.length), FLAGS);
                    seeder.run(Path.of(s.need("klasor", "dir")), s.flag("yukle", "upload"), s.flag("kuru", "dry-run"),
                            s.flag("sadece-yukle", "upload-only"), out);
                    a = s;
                }
                case "yardim", "help", "-h", "--help" -> help(out);
                default -> {
                    out.println("Bilinmeyen komut: " + argv[0]);
                    help(out);
                    return 2;
                }
            }
            List<String> unknown = a.unknown();
            if (!unknown.isEmpty()) out.println("Uyarı: tanınmayan seçenekler yok sayıldı: " + String.join(", ", unknown));
            return 0;
        } catch (IllegalArgumentException | IllegalStateException e) {
            out.println("HATA: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            out.println("HATA (dosya/ağ): " + e.getMessage());
            return 1;
        }
    }

    // ------------------------------------------------------------------ paket

    private void pkg(String action, Args a, PrintStream out) throws IOException {
        switch (action) {
            case "listele", "list" -> {
                List<Document> list = content.packages(a.opt("mod", "mode"));
                if (list.isEmpty()) out.println("Paket yok.");
                for (Document p : list) {
                    out.printf("%-24s %-8s s%-3d %-10s %s%s%n", p.getString("_id"), p.getString("mode"), p.getInteger("version", 1),
                            p.getString("status"), p.getString("name"), Boolean.TRUE.equals(p.getBoolean("base")) ? "  [taban]" : "");
                }
            }
            case "goster", "show" -> {
                Document p = content.requirePackage(a.need(0, "paket kimliği"));
                out.printf("%s  (%s, sürüm %d, %s%s)%n", p.getString("name"), p.getString("mode"), p.getInteger("version", 1), p.getString("status"),
                        Boolean.TRUE.equals(p.getBoolean("base")) ? ", taban paket" : "");
                out.println("Kimlik: " + p.getString("_id") + "   Güncellendi: " + date(p.getDate("updatedAt")));
                List<Document> items = p.getList("items", Document.class);
                Map<String, String> windows = Dist.MODE_WINDOWS.get(p.getString("mode"));
                for (String w : windows.keySet()) {
                    List<Document> in = items.stream().filter(i -> w.equals(i.getString("window"))).toList();
                    if (in.isEmpty()) continue;
                    out.println();
                    out.println("== " + windows.get(w) + " (" + w + "): " + in.size());
                    for (Document i : in) {
                        out.printf("  %-20s %-32s %-16s %s%n", i.getString("id"), i.getString("title"),
                                i.getString("category") == null ? "" : i.getString("category"), i.getString("url"));
                    }
                }
            }
            case "ekle", "add" -> {
                List<ItemInput> items = new ArrayList<>(readItems(a.opt("dosya", "file")));
                items.addAll(parseItems(a.all("oge", "item")));
                Document p = content.addPackage(a.need("ad", "name"), a.need("mod", "mode"), items, false, a.opt("kimlik", "id"));
                out.println("Paket eklendi: " + p.getString("_id") + " (" + p.getList("items", Document.class).size() + " öğe, taslak). Yayımlamak için: paket yayinla " + p.getString("_id"));
            }
            case "guncelle", "update" -> {
                String id = a.need(0, "paket kimliği");
                String file = a.opt("dosya", "file");
                List<ItemInput> replace = file == null ? null : readItems(file);
                Set<String> remove = new LinkedHashSet<>();
                for (String r : a.all("cikar", "remove")) for (String x : r.split(",")) if (!x.isBlank()) remove.add(x.trim());
                Document p = content.updatePackage(id, a.opt("ad", "name"), parseItems(a.all("oge", "item")), remove, replace);
                out.println("Paket güncellendi: " + id + " (sürüm " + p.getInteger("version", 1) + ", " + p.getList("items", Document.class).size() + " öğe)");
                if ("PUBLISHED".equals(p.getString("status"))) out.println("Yeni sürümü göndermek için: gonder paket " + id + " --herkes");
            }
            case "yayinla", "publish" -> {
                Document p = content.publishPackage(a.need(0, "paket kimliği"));
                out.println("Yayımlandı: " + p.getString("_id") + " (sürüm " + p.getInteger("version", 1) + "). Göndermek için: gonder paket " + p.getString("_id") + " --herkes");
            }
            case "pencereler", "windows" -> Dist.MODE_WINDOWS.forEach((m, w) -> out.println(m + ": " + w));
            default -> throw new IllegalArgumentException("paket listele | goster <kimlik> | ekle | guncelle <kimlik> | yayinla <kimlik> | pencereler");
        }
    }

    /** "pencere|başlık|adres[|kategori]" biçimli öğeler. */
    static List<ItemInput> parseItems(List<String> specs) {
        List<ItemInput> out = new ArrayList<>();
        for (String s : specs) {
            String[] p = SEP.split(s, -1);
            if (p.length < 3) throw new IllegalArgumentException("Öğe \"pencere|başlık|adres[|kategori]\" biçiminde olmalı: " + s);
            out.add(new ItemInput(p[0].trim(), p[1].trim(), p.length > 3 ? p[3].trim() : null, p[2].trim(), null));
        }
        return out;
    }

    /** JSON ([{window,title,url,category?,id?}]) ya da CSV (başlık satırı: window,title,url[,category][,id]). */
    List<ItemInput> readItems(String file) throws IOException {
        if (file == null) return List.of();
        String text = Files.readString(Path.of(file), StandardCharsets.UTF_8).replace("﻿", "");
        List<ItemInput> out = new ArrayList<>();
        if (text.trim().startsWith("[")) {
            for (JsonNode n : json.readTree(text)) {
                out.add(new ItemInput(n.path("window").asText(null), n.path("title").asText(null), n.path("category").asText(null),
                        n.path("url").asText(null), n.path("id").asText(null)));
            }
            return out;
        }
        List<String> lines = text.lines().filter(l -> !l.isBlank()).toList();
        if (lines.isEmpty()) return out;
        List<String> head = csv(lines.get(0)).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        int w = head.indexOf("window"), t = head.indexOf("title"), u = head.indexOf("url"), c = head.indexOf("category"), id = head.indexOf("id");
        if (w < 0 || t < 0 || u < 0) throw new IllegalArgumentException("CSV başlığında window, title ve url olmalı");
        for (String line : lines.subList(1, lines.size())) {
            List<String> f = csv(line);
            out.add(new ItemInput(get(f, w), get(f, t), get(f, c), get(f, u), get(f, id)));
        }
        return out;
    }

    private static String get(List<String> f, int i) { return i < 0 || i >= f.size() || f.get(i).isBlank() ? null : f.get(i).trim(); }

    /** Basit CSV (çift tırnak ve "" kaçışı). */
    static List<String> csv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (q) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { b.append('"'); i++; }
                else if (ch == '"') q = false;
                else b.append(ch);
            } else if (ch == '"') q = true;
            else if (ch == ',') { out.add(b.toString()); b.setLength(0); }
            else b.append(ch);
        }
        out.add(b.toString());
        return out;
    }

    // ------------------------------------------------------------------ bildirim, duyuru, sürüm

    private void notification(String action, Args a, PrintStream out) {
        switch (action) {
            case "listele", "list" -> {
                List<Document> list = content.list(Dist.NOTIFICATIONS, 100);
                if (list.isEmpty()) out.println("Bildirim yok.");
                for (Document d : list) out.printf("%-18s %s  %s%n", d.getString("_id"), date(d.getDate("createdAt")), d.getString("title"));
            }
            case "goster", "show" -> {
                Document d = content.require(Dist.NOTIFICATIONS, a.need(0, "bildirim kimliği"), "Bildirim");
                out.println(d.getString("title"));
                out.println(d.getString("body"));
                out.println("Kimlik: " + d.getString("_id") + "   Oluşturuldu: " + date(d.getDate("createdAt")));
            }
            case "ekle", "add" -> {
                Document d = content.addNotification(a.need("baslik", "title"), a.need("aciklama", "body"));
                out.println("Bildirim eklendi: " + d.getString("_id") + ". Göndermek için: gonder bildirim " + d.getString("_id") + " --herkes");
            }
            case "guncelle", "update" -> {
                Document d = content.updateNotification(a.need(0, "bildirim kimliği"), a.opt("baslik", "title"), a.opt("aciklama", "body"));
                deliveries.evict(Dist.Kind.NOTIFICATION, d.getString("_id"));
                out.println("Bildirim güncellendi: " + d.getString("_id") + " (henüz almamış cihazlar yeni halini alır)");
            }
            default -> throw new IllegalArgumentException("bildirim listele | goster <kimlik> | ekle --baslik .. --aciklama .. | guncelle <kimlik>");
        }
    }

    private void announcement(String action, Args a, PrintStream out) {
        switch (action) {
            case "listele", "list" -> {
                List<Document> list = content.list(Dist.ANNOUNCEMENTS, 100);
                if (list.isEmpty()) out.println("Duyuru yok.");
                for (Document d : list) out.printf("%-18s %s  %s%n", d.getString("_id"), date(d.getDate("createdAt")), d.getString("title"));
            }
            case "goster", "show" -> {
                Document d = content.require(Dist.ANNOUNCEMENTS, a.need(0, "duyuru kimliği"), "Duyuru");
                out.println(d.getString("title"));
                out.println(d.getString("body"));
                out.println("Küçük resim: " + d.getString("imageUrl"));
                out.println("Kimlik: " + d.getString("_id") + "   Oluşturuldu: " + date(d.getDate("createdAt")));
            }
            case "ekle", "add" -> {
                Document d = content.addAnnouncement(a.need("baslik", "title"), a.need("aciklama", "body"), a.need("resim", "image"));
                out.println("Duyuru eklendi: " + d.getString("_id") + ". Göndermek için: gonder duyuru " + d.getString("_id") + " --herkes");
            }
            case "guncelle", "update" -> {
                Document d = content.updateAnnouncement(a.need(0, "duyuru kimliği"), a.opt("baslik", "title"), a.opt("aciklama", "body"), a.opt("resim", "image"));
                deliveries.evict(Dist.Kind.ANNOUNCEMENT, d.getString("_id"));
                out.println("Duyuru güncellendi: " + d.getString("_id") + " (henüz almamış cihazlar yeni halini alır)");
            }
            default -> throw new IllegalArgumentException("duyuru listele | goster <kimlik> | ekle --baslik .. --aciklama .. --resim URL | guncelle <kimlik>");
        }
    }

    private void release(String action, Args a, PrintStream out) {
        switch (action) {
            case "listele", "list" -> {
                List<Document> list = mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(50), Document.class, Dist.RELEASES);
                if (list.isEmpty()) out.println("Kayıtlı sürüm yok.");
                for (Document d : list) {
                    out.printf("%-8s %-10s en eski: %-8s %s  %s%n", d.get("_id"), d.getString("versionName"), d.get("minSupportedVersionCode"),
                            date(d.getDate("createdAt")), d.getString("message") == null ? "" : d.getString("message"));
                }
            }
            case "ekle", "add" -> {
                long code = Long.parseLong(a.need("kod", "code"));
                String min = a.opt("en-eski", "min");
                Document d = content.addRelease(code, a.need("ad", "name"), a.opt("ileti", "message"), min == null ? null : Long.parseLong(min));
                out.println("Sürüm kaydedildi: " + d.get("_id") + " (" + d.getString("versionName") + ")");
                if (!a.flag("gonderme", "no-notify")) {
                    Document dsp = deliveries.dispatch(Dist.Kind.APP_UPDATE, String.valueOf(code), null);
                    out.println("Eski sürümdeki bütün cihazlara güncelleme haberi gönderilecek (gönderim " + dsp.getString("_id") + ").");
                }
            }
            default -> throw new IllegalArgumentException("surum listele | ekle --kod 44 --ad 1.0.1 [--ileti ..] [--en-eski 40] [--gonderme]");
        }
    }

    // ------------------------------------------------------------------ gönderim ve teslimat

    private void send(Args a, PrintStream out) {
        Dist.Kind kind = Dist.Kind.parse(a.need(0, "tür (paket, bildirim, duyuru, surum)"));
        String id = a.need(1, "kimlik");
        boolean all = a.flag("herkes", "all");
        List<String> uids = new ArrayList<>();
        for (String s : a.all("kullanicilar", "users")) uids.addAll(Arrays.asList(s.split(",")));
        if (all == !uids.isEmpty()) throw new IllegalArgumentException("Ya --herkes ya da --kullanicilar uid1,uid2 verilmeli");
        Document d = deliveries.dispatch(kind, id, all ? null : uids);
        out.println("Gönderim ayarlandı: " + d.getString("_id") + " (" + kind + " " + d.getString("refId")
                + (all ? ", bütün kullanıcılar" : ", " + uids.size() + " kullanıcı") + "). Sunucunun işçileri arka planda gönderir.");
        out.println("Durumu görmek için: gonderim goster " + d.getString("_id"));
    }

    private void dispatch(String action, Args a, PrintStream out) {
        switch (action) {
            case "listele", "list" -> {
                List<Document> list = deliveries.dispatches(50);
                if (list.isEmpty()) out.println("Gönderim yok.");
                for (Document d : list) {
                    Document t = d.get("target", Document.class);
                    out.printf("%-18s %-13s %-22s %-10s %-12s %s %s%n", d.getString("_id"), d.getString("kind"), d.getString("refId"), d.getString("status"),
                            Boolean.TRUE.equals(t.getBoolean("all")) ? "herkes" : t.getList("uids", String.class).size() + " kullanıcı",
                            date(d.getDate("createdAt")), deliveries.counts(d.getString("_id")));
                }
            }
            case "goster", "show" -> {
                String id = a.need(0, "gönderim kimliği");
                Document d = content.require(Dist.DISPATCHES, id, "Gönderim");
                out.println(d.getString("kind") + " " + d.getString("refId") + " (sürüm " + d.getInteger("version", 1) + "), durum " + d.getString("status"));
                out.println("Aşamalar: " + deliveries.counts(id));
                for (Document x : deliveries.deliveries(id, null, 50)) printDelivery(x, out);
            }
            case "iptal", "cancel" -> {
                deliveries.cancelDispatch(a.need(0, "gönderim kimliği"));
                out.println("Gönderim iptal edildi (henüz cihaza ulaşmamış teslimatlar iptal).");
            }
            default -> throw new IllegalArgumentException("gonderim listele | goster <kimlik> | iptal <kimlik>");
        }
    }

    private void delivery(Args a, PrintStream out) {
        List<Document> list = deliveries.deliveries(a.opt("gonderim", "dispatch"), a.opt("kullanici", "user"), 100);
        if (list.isEmpty()) out.println("Teslimat yok.");
        for (Document x : list) printDelivery(x, out);
    }

    private void printDelivery(Document x, PrintStream out) {
        out.printf("  %-36s %-13s %-20s %-13s %-28s kanal:%-5s %s%s%n", x.getString("_id"), x.getString("kind"), x.getString("refId"), x.getString("stage"),
                x.getString("uid"), x.getString("channel") == null ? "-" : x.getString("channel"), date(x.getDate("updatedAt")),
                x.getString("lastError") == null ? "" : "  (" + x.getString("lastError") + ")");
    }

    private void users(Args a, PrintStream out) {
        String q = a.opt("ara", "search");
        Criteria c = Criteria.where("firebaseUid").exists(true);
        if (q != null) c = c.orOperator(Criteria.where("email").regex(Pattern.quote(q.toLowerCase(Locale.ROOT))), Criteria.where("displayName").regex(Pattern.quote(q), "i"));
        List<Document> list = mongo.find(new Query(c).with(Sort.by(Sort.Direction.DESC, "updatedAt")).limit(100), Document.class, Stores.USERS);
        if (list.isEmpty()) out.println("Kullanıcı yok.");
        for (Document u : list) {
            String uid = u.getString("firebaseUid");
            long n = mongo.count(Query.query(Criteria.where("uid").is(uid)), Dist.DEVICES);
            out.printf("%-30s %-32s %-24s cihaz: %d%n", uid, u.getString("email") == null ? "-" : u.getString("email"),
                    u.getString("displayName") == null ? "-" : u.getString("displayName"), n);
        }
    }

    private void deviceList(Args a, PrintStream out) {
        List<Document> list = devices.list(a.opt("kullanici", "user"), 100);
        if (list.isEmpty()) out.println("Kayıtlı cihaz yok.");
        for (Document d : list) {
            Map<String, Object> info = devices.info(d);
            out.printf("%s…  %-28s sürüm %-8s %-26s gelen kutusu v%-5s son görülme %s  paketler %s%n", d.getString("_id").substring(0, 12), d.getString("uid"),
                    d.get("appVersionCode"), info.getOrDefault("manufacturer", "") + " " + info.getOrDefault("model", ""),
                    d.get("inboxVersion"), date(d.getDate("lastSeenAt")),
                    d.get("packages") == null ? "{}" : d.get("packages", Document.class).toJson());
        }
    }

    // ------------------------------------------------------------------ yardım

    public static void help(PrintStream out) {
        out.println("""
                DrawGuidance yönetim komutları (v43)
                Kullanım: java -jar drawguidance-server.jar cli <komut>   (yerel sunucuda: Yonetim.bat <komut>)

                PAKETLER (mod: COMIC, NOTES, ART)
                  paket pencereler                         modlar ve pencereleri
                  paket listele [--mod COMIC]
                  paket goster <kimlik>
                  paket ekle --ad "Uzay paketi" --mod COMIC [--dosya ogeler.json|ogeler.csv]
                             [--oge "objects|Roket|https://.../roket.webp|Uzay"]... [--kimlik uzay-paketi]
                      öğe: pencere|başlık|resim adresi[|kategori]  (adres: https://... ya da gs://bucket/nesne)
                  paket guncelle <kimlik> [--ad ..] [--oge ..]... [--cikar oge1,oge2] [--dosya ogeler.json (tümünü değiştirir)]
                  paket yayinla <kimlik>

                BİLDİRİMLER VE DUYURULAR
                  bildirim listele | goster <kimlik> | ekle --baslik ".." --aciklama ".." | guncelle <kimlik> [--baslik ..] [--aciklama ..]
                  duyuru   listele | goster <kimlik> | ekle --baslik ".." --aciklama ".." --resim URL | guncelle <kimlik> [...]

                UYGULAMA GÜNCELLEMESİ
                  surum listele
                  surum ekle --kod 44 --ad 1.0.1 [--ileti ".."] [--en-eski 40] [--gonderme]
                      (öntanımlı: eski sürümdeki bütün cihazlara güncelleme haberi gönderilir)

                GÖNDERME (arka plan işçileri yapar: önce "yeni içerik var" haberi, sonra veri)
                  gonder <paket|bildirim|duyuru|surum> <kimlik> --herkes
                  gonder <paket|bildirim|duyuru|surum> <kimlik> --kullanicilar uid1,uid2
                  gonderim listele | goster <kimlik> | iptal <kimlik>
                  teslimat listele [--gonderim <kimlik>] [--kullanici <uid>]

                KULLANICILAR VE CİHAZLAR
                  kullanici listele [--ara metin]
                  cihaz listele [--kullanici <uid>]

                GELEN KUTUSU (telefon modeli ve tarayıcı bilgisi veritabanında şifreli; burada çözülerek gösterilir)
                  geri-bildirim listele [--mod ART] [--sayi 20] | goster <kimlik>
                  hata listele [--tur CRASH|ERROR|USER] [--sayi 20] | goster <kimlik> | gruplar [--sayi 20]
                  destek listele [--sayi 20] | goster <kimlik>
                  silme-talebi listele [--sayi 20] | goster <kimlik>

                DİĞER
                  isci tur                                 işçinin bir turunu hemen çalıştırır (sunucu zaten 10 sn'de bir çalıştırır)
                  taban-paket --klasor <dizin> [--yukle] [--sadece-yukle] [--kuru]
                      uygulamadaki pencere resimlerini taban paket olarak Cloud Storage'a yükler ve kaydeder
                      --sadece-yukle: yalnız Cloud Storage'a yükler + bundled-manifest.json üretir, Mongo'ya YAZMAZ
                                      (sunucu açılışta kaydeder; Mongo dışarı açık değilken bilgisayardan eşitleme)
                """);
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
    }

    private static String date(Date d) {
        if (d == null) return "-";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        f.setTimeZone(TimeZone.getTimeZone("Europe/Istanbul"));
        return f.format(d);
    }
}
