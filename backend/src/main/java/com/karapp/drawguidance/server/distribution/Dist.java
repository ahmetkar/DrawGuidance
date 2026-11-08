package com.karapp.drawguidance.server.distribution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v43 içerik dağıtımının ortak adları: koleksiyonlar, teslimat türleri ve aşamaları, modlar ve pencereleri.
 *
 * <p><b>Akış:</b> CLI ile bir paket/bildirim/duyuru/sürüm "gönderilmek üzere" ayarlanır ({@link #DISPATCHES}). İşçi
 * bunu hedef kullanıcıların cihazlarına teslimatlara ({@link #DELIVERIES}) açar. Her teslimat sırayla:
 * <ol>
 *   <li>{@link Stage#QUEUED}: cihaza "yeni bir şey var" bilgisi henüz gitmedi.</li>
 *   <li>{@link Stage#INFO_SENT}: bilgi gönderildi (cihaz gelen kutusunu yoklayınca; uygulama açıksa uzun yoklama hemen uyanır).</li>
 *   <li>{@link Stage#INFO_RECEIVED}: cihaz bilgiyi olay tablosuna yazdı ve onayladı.</li>
 *   <li>{@link Stage#DATA_SENT}: sunucu veriyi hazırladı ve cihaza haber verdi (cihaz veriyi indirir).</li>
 *   <li>{@link Stage#DELIVERED}: cihaz verinin tamamını aldı ve onayladı.</li>
 * </ol>
 * {@link Stage#FAILED}: cihaz veriyi işleyemedi (ör. bozuk paket); {@link Stage#CANCELLED}: artık gerekmiyor (ör.
 * güncelleme zaten yüklendi, gönderim iptal edildi).
 */
public final class Dist {
    private Dist() {}

    public static final String PACKAGES = "content_packages";
    public static final String NOTIFICATIONS = "app_notifications";
    public static final String ANNOUNCEMENTS = "announcements";
    public static final String RELEASES = "app_releases";
    public static final String DEVICES = "devices";
    public static final String DISPATCHES = "dispatches";
    public static final String DELIVERIES = "deliveries";

    /** Teslimat türleri. */
    public enum Kind {
        PACKAGE, NOTIFICATION, ANNOUNCEMENT, APP_UPDATE;

        public static Kind parse(String s) {
            if (s == null) throw new IllegalArgumentException("tür boş");
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "package", "paket" -> PACKAGE;
                case "notification", "bildirim" -> NOTIFICATION;
                case "announcement", "duyuru" -> ANNOUNCEMENT;
                case "app_update", "release", "surum", "sürüm", "guncelleme", "güncelleme" -> APP_UPDATE;
                default -> throw new IllegalArgumentException("Bilinmeyen tür: " + s + " (package, notification, announcement, release)");
            };
        }
    }

    /** Teslimat aşamaları (sıra önemli: geri gidilmez). */
    public enum Stage {
        QUEUED, INFO_SENT, INFO_RECEIVED, DATA_SENT, DELIVERED, FAILED, CANCELLED;

        public boolean finished() { return this == DELIVERED || this == FAILED || this == CANCELLED; }
    }

    /** Paket alabilen modlar ve her modun pencereleri (pencere kimliği → Türkçe adı). */
    public static final Map<String, Map<String, String>> MODE_WINDOWS;

    static {
        Map<String, Map<String, String>> m = new LinkedHashMap<>();
        Map<String, String> comic = new LinkedHashMap<>();
        comic.put("objects", "Nesneler");
        comic.put("effects", "Efektler");
        comic.put("backgrounds", "Mekân arka planları");
        comic.put("balloons", "Diyalog kutuları");
        comic.put("shapes", "Şekiller");
        comic.put("characters", "Karakterler");
        m.put("COMIC", comic);
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("shapes", "Şekiller");
        notes.put("diagrams", "Diyagramlar");
        m.put("NOTES", notes);
        Map<String, String> art = new LinkedHashMap<>();
        art.put("shapes", "Şekiller");
        m.put("ART", art);
        MODE_WINDOWS = java.util.Collections.unmodifiableMap(m);
    }

    public static List<String> modes() { return List.copyOf(MODE_WINDOWS.keySet()); }

    /** Mod adı (büyük harf; "cizgi-roman", "comic" gibi yazımlar da kabul edilir). */
    public static String mode(String s) {
        if (s == null) throw new IllegalArgumentException("mod boş");
        String v = s.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace("İ", "I");
        v = switch (v) {
            case "CIZGI_ROMAN", "ÇIZGI_ROMAN", "COMICS" -> "COMIC";
            case "NOT", "NOT_ALMA", "NOTE" -> "NOTES";
            case "RESIM", "RESİM", "DRAWING", "PAINT" -> "ART";
            default -> v;
        };
        if (!MODE_WINDOWS.containsKey(v)) throw new IllegalArgumentException("Bilinmeyen mod: " + s + " (" + String.join(", ", modes()) + ")");
        return v;
    }

    /** Pencere kimliği bu mod için geçerli mi (küçük harfe çevrilir). */
    public static String window(String mode, String window) {
        if (window == null) throw new IllegalArgumentException("pencere boş");
        String w = window.trim().toLowerCase(Locale.ROOT);
        Map<String, String> windows = MODE_WINDOWS.get(mode);
        if (windows == null || !windows.containsKey(w)) {
            throw new IllegalArgumentException("'" + mode + "' modunda '" + window + "' penceresi yok (" + String.join(", ", windows == null ? List.of() : windows.keySet()) + ")");
        }
        return w;
    }
}
