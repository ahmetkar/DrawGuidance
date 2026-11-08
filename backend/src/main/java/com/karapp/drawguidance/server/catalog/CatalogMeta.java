package com.karapp.drawguidance.server.catalog;

import org.bson.Document;

import java.time.Instant;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

/**
 * {@code catalog_meta} belgesi ({@code _id} = "catalog"): yüklü katalog sürümü, eşitleme anı, bölüm sayıları ve
 * (eşitleme sürerken) kilit. Hiç eşitlenmemişse sürüm 0, sayılar 0.
 *
 * @param version   yüklü sürüm ({@code null}: hiç tamamlanmış eşitleme yok)
 * @param updatedAt son eşitlemenin bittiği an
 * @param counts    bölüm → öğe sayısı
 * @param lockUntil eşitleme kilidinin bitişi (kilit yoksa {@code null})
 */
public record CatalogMeta(Long version, Instant updatedAt, Map<CatalogKind, Integer> counts, Instant lockUntil) {
    /** v34 öğenin kullanıldığı tuval türleri (şablon ve vektörlerde). */
    public static final String KINDS_FIELD = "kinds";

    public static final String ID = "catalog";
    public static final String VERSION = "version", UPDATED = "updatedAt", GENERATED = "generatedAt", COUNTS = "counts", LOCK = "lock";

    public static final CatalogMeta NONE = from(null);

    public long versionOrZero() { return version == null ? 0L : version; }

    public int count(CatalogKind kind) { return counts.getOrDefault(kind, 0); }

    /** Başka bir sunucu şu an eşitliyor mu (süresi geçmiş kilit sayılmaz). */
    public boolean seeding(Instant now) { return lockUntil != null && lockUntil.isAfter(now); }

    public static CatalogMeta from(Document d) {
        Map<CatalogKind, Integer> counts = new EnumMap<>(CatalogKind.class);
        for (CatalogKind k : CatalogKind.values()) counts.put(k, 0);
        if (d == null) return new CatalogMeta(null, null, counts, null);
        Long version = d.get(VERSION) instanceof Number n ? n.longValue() : null;
        Instant updated = d.get(UPDATED) instanceof Date t ? t.toInstant() : null;
        if (d.get(COUNTS) instanceof Document c) {
            for (CatalogKind k : CatalogKind.values()) if (c.get(k.fileName()) instanceof Number n) counts.put(k, n.intValue());
        }
        Instant lockUntil = d.get(LOCK) instanceof Document l && l.get("until") instanceof Date u ? u.toInstant() : null;
        return new CatalogMeta(version, updated, counts, lockUntil);
    }
}
