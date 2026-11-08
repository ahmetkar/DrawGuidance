package com.karapp.drawguidance.server.distribution.cli;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.privacy.PersonalDataCipher;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * v49.2 gelen kutusu komutları: geri bildirimler, hata raporları, destek mesajları ve hesap silme talepleri. Şifreli
 * cihaz bilgisi (telefon modeli, tarayıcı bilgisi) burada çözülerek gösterilir ({@link PersonalDataCipher}); çözülemezse
 * "(çözülemedi)" yazar. Şifreleme öncesinden kalan düz metin alanlar da okunur.
 */
@Component
public class InboxCli {
    static final int DEFAULT_LIMIT = 20, MAX_LIMIT = 500;

    private final MongoTemplate mongo;
    private final PersonalDataCipher cipher;

    public InboxCli(MongoTemplate mongo, PersonalDataCipher cipher) {
        this.mongo = mongo;
        this.cipher = cipher;
    }

    public void feedback(String action, Args a, PrintStream out) {
        switch (action) {
            case "listele", "list", "" -> {
                Criteria c = new Criteria();
                String mode = a.opt("mod", "mode");
                if (mode != null) c = Criteria.where("mode").is(mode.toUpperCase(Locale.ROOT));
                List<Document> list = latest(Stores.FEEDBACK, c, "createdAt", a);
                if (list.isEmpty()) out.println("Geri bildirim yok.");
                for (Document d : list) {
                    out.printf("%s  %-36s %-12s %d/5  %-20s %-24s %s%n", date(d.getDate("createdAt")), d.getString("_id"), d.getString("mode"),
                            d.getInteger("rating", 0), or(d.getString("feature")), or(feedbackModel(d)), shorten(d.getString("text"), 60));
                }
            }
            case "goster", "show" -> {
                Document d = require(Stores.FEEDBACK, a.need(0, "geri bildirim kimliği"));
                out.println("Geri bildirim " + d.getString("_id") + "  (" + date(d.getDate("createdAt")) + ")");
                out.println("Kullanıcı: " + or(d.getString("uid")) + "   E-posta: " + or(d.getString("email")) + "   İzin: " + d.get("consent"));
                out.println("Mod: " + d.getString("mode") + "   Özellik: " + or(d.getString("feature")) + "   Puan: " + d.getInteger("rating", 0) + "/5");
                Document dev = d.get("device", Document.class);
                if (dev != null) {
                    out.println("Telefon: " + or(feedbackModel(d)) + "   Android: " + or(dev.getString("androidVersion"))
                            + "   Uygulama: " + or(dev.getString("appVersion")) + " (" + dev.get("versionCode") + ")   Dil: " + or(dev.getString("locale")));
                }
                out.println("Yazı:");
                out.println(d.getString("text") == null ? "  (yok)" : d.getString("text"));
            }
            default -> throw new IllegalArgumentException("geri-bildirim listele [--mod ART] [--sayi 20] | goster <kimlik>");
        }
    }

    public void report(String action, Args a, PrintStream out) {
        switch (action) {
            case "listele", "list", "" -> {
                Criteria c = new Criteria();
                String kind = a.opt("tur", "kind");
                if (kind != null) c = Criteria.where("kind").is(kind.toUpperCase(Locale.ROOT));
                List<Document> list = latest(Stores.ERROR_REPORTS, c, "receivedAt", a);
                if (list.isEmpty()) out.println("Hata raporu yok.");
                for (Document d : list) {
                    out.printf("%s  %-36s %-5s %-10s %-24s %-40s %s%n", date(d.getDate("receivedAt")), d.getString("_id"), d.getString("kind"),
                            or(d.getString("appVersion")), or(reportDevice(d)), shorten(or(d.getString("exceptionType")), 40), or(d.getString("feature")));
                }
            }
            case "goster", "show" -> {
                Document d = require(Stores.ERROR_REPORTS, a.need(0, "rapor kimliği"));
                out.println("Hata raporu " + d.getString("_id") + "  (" + d.getString("kind") + ", " + date(d.getDate("receivedAt")) + ")");
                out.println("Kullanıcı: " + or(d.getString("uid")) + "   Kurulum: " + d.getString("installId") + "   Parmak izi: " + d.getString("fingerprint"));
                out.println("Telefon: " + or(reportDevice(d)) + "   Android API: " + or(d.get("sdk")) + "   Uygulama: " + or(d.getString("appVersion"))
                        + " (" + d.get("versionCode") + ")   Dil: " + or(d.getString("language")));
                out.println("Özellik: " + or(d.getString("feature")) + "   İstisna: " + or(d.getString("exceptionType")));
                out.println("İleti: " + or(d.getString("message")));
                if (d.getString("note") != null) out.println("Not: " + d.getString("note"));
                List<String> crumbs = d.getList("breadcrumbs", String.class);
                if (crumbs != null && !crumbs.isEmpty()) {
                    out.println("Son eylemler:");
                    crumbs.forEach(c -> out.println("  " + c));
                }
                if (d.getString("stackTrace") != null) {
                    out.println("Yığın dökümü:");
                    out.println(d.getString("stackTrace"));
                }
            }
            case "gruplar", "groups" -> {
                Query q = new Query().with(Sort.by(Sort.Direction.DESC, "lastSeen")).limit(limit(a));
                List<Document> list = mongo.find(q, Document.class, Stores.ERROR_REPORT_GROUPS);
                if (list.isEmpty()) out.println("Hata grubu yok.");
                for (Document g : list) {
                    out.printf("%s  %-32s %5s kez  %-5s %-10s %-40s %s%n", date(g.getDate("lastSeen")), g.getString("_id"), g.get("count"),
                            g.getString("kind"), or(g.getString("lastAppVersion")), shorten(or(g.getString("exceptionType")), 40), or(g.getString("feature")));
                }
            }
            default -> throw new IllegalArgumentException("hata listele [--tur CRASH|ERROR|USER] [--sayi 20] | goster <kimlik> | gruplar");
        }
    }

    public void support(String action, Args a, PrintStream out) {
        switch (action) {
            case "listele", "list", "" -> {
                List<Document> list = latest(Stores.SUPPORT_MESSAGES, new Criteria(), "createdAt", a);
                if (list.isEmpty()) out.println("Destek mesajı yok.");
                for (Document d : list) {
                    out.printf("%s  %-36s %-6s %-32s %s%n", date(d.getDate("createdAt")), d.getString("_id"), or(d.getString("status")),
                            or(d.getString("email")), shorten(d.getString("subject"), 60));
                }
            }
            case "goster", "show" -> {
                Document d = require(Stores.SUPPORT_MESSAGES, a.need(0, "mesaj kimliği"));
                out.println("Destek mesajı " + d.getString("_id") + "  (" + date(d.getDate("createdAt")) + ", " + or(d.getString("status")) + ")");
                out.println("E-posta: " + or(d.getString("email")) + "   Dil: " + or(d.getString("locale")));
                out.println("Tarayıcı: " + or(userAgent(d, PersonalDataCipher.SUPPORT)));
                out.println("Konu: " + or(d.getString("subject")));
                out.println(or(d.getString("message")));
            }
            default -> throw new IllegalArgumentException("destek listele [--sayi 20] | goster <kimlik>");
        }
    }

    public void deletion(String action, Args a, PrintStream out) {
        switch (action) {
            case "listele", "list", "" -> {
                List<Document> list = latest(Stores.DELETION_REQUESTS, new Criteria(), "createdAt", a);
                if (list.isEmpty()) out.println("Hesap silme talebi yok.");
                for (Document d : list) {
                    out.printf("%s  %-36s %-9s %-32s %s%n", date(d.getDate("createdAt")), d.getString("_id"), or(d.getString("status")),
                            or(d.getString("email")), shorten(d.getString("reason"), 60));
                }
            }
            case "goster", "show" -> {
                Document d = require(Stores.DELETION_REQUESTS, a.need(0, "talep kimliği"));
                out.println("Hesap silme talebi " + d.getString("_id") + "  (" + date(d.getDate("createdAt")) + ", " + or(d.getString("status")) + ")");
                out.println("E-posta: " + or(d.getString("email")) + "   Dil: " + or(d.getString("locale")));
                out.println("Tarayıcı: " + or(userAgent(d, PersonalDataCipher.DELETION)));
                out.println("Gerekçe: " + or(d.getString("reason")));
            }
            default -> throw new IllegalArgumentException("silme-talebi listele [--sayi 20] | goster <kimlik>");
        }
    }

    // ------------------------------------------------------------------ çözme

    /** Geri bildirimdeki telefon modeli (şifreliyse çözülür; şifreleme öncesi kayıtta düz alan). */
    String feedbackModel(Document d) {
        Document dev = d.get("device", Document.class);
        if (dev == null) return null;
        return open(PersonalDataCipher.FEEDBACK, d, dev.getString("modelEnc"), dev.getString("model"));
    }

    String reportDevice(Document d) {
        return open(PersonalDataCipher.REPORT, d, d.getString("deviceEnc"), d.getString("device"));
    }

    String userAgent(Document d, String kind) {
        return open(kind, d, d.getString("userAgentEnc"), d.getString("userAgent"));
    }

    private String open(String kind, Document d, String sealed, String plain) {
        if (sealed == null) return plain;
        String v = cipher.open(kind, String.valueOf(d.get("_id")), sealed);
        return v == null ? "(çözülemedi)" : v;
    }

    // ------------------------------------------------------------------ yardımcılar

    /** Son kayıtlar; listede gösterilmeyen büyük alanlar (yığın dökümü, son eylemler) okunmaz (CLI az bellekle çalışır). */
    private List<Document> latest(String collection, Criteria c, String dateField, Args a) {
        Query q = new Query(c).with(Sort.by(Sort.Direction.DESC, dateField)).limit(limit(a));
        q.fields().exclude("stackTrace").exclude("breadcrumbs");
        return mongo.find(q, Document.class, collection);
    }

    private Document require(String collection, String id) {
        Document d = mongo.findById(id, Document.class, collection);
        if (d == null) throw new IllegalArgumentException("Bulunamadı: " + id);
        return d;
    }

    static int limit(Args a) {
        String v = a.opt("sayi", "limit");
        if (v == null) return DEFAULT_LIMIT;
        try {
            int n = Integer.parseInt(v.trim());
            if (n < 1 || n > MAX_LIMIT) throw new IllegalArgumentException("--sayi 1 ile " + MAX_LIMIT + " arasında olmalı");
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--sayi bir sayı olmalı");
        }
    }

    private static String or(Object v) { return v == null ? "-" : v.toString(); }

    /** Tek satıra indirilmiş, en çok [max] karakterlik özet. */
    static String shorten(String s, int max) {
        if (s == null) return "-";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static String date(Date d) {
        if (d == null) return "-";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        f.setTimeZone(TimeZone.getTimeZone("Europe/Istanbul"));
        return f.format(d);
    }
}
