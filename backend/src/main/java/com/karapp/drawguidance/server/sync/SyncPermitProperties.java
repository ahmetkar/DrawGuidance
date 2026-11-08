package com.karapp.drawguidance.server.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * v40 içerik eşitleme izni ({@code drawguidance.sync.*}): kullanıcılar {@code groupSize} kişilik gruplara bölünür;
 * {@code cycle} (24 saat) {@code slotLength} (10 dakika) uzunluğunda dilimlere ayrılır ve her grup sırayla kendi
 * diliminde katalog (şekil, vektör, yapay zekâ ağları) indirme izni alır.
 *
 * @param groupSize   grup başına kullanıcı (öntanımlı 1000)
 * @param slotLength  dilim uzunluğu (öntanımlı 10 dakika)
 * @param cycle       bütün dilimlerin döngüsü (öntanımlı 24 saat; dilim sayısı = cycle / slotLength, 144)
 */
@ConfigurationProperties(prefix = "drawguidance.sync")
public record SyncPermitProperties(Integer groupSize, Duration slotLength, Duration cycle) {
    public SyncPermitProperties {
        if (groupSize == null || groupSize <= 0) groupSize = 1000;
        if (slotLength == null || slotLength.isNegative() || slotLength.isZero()) slotLength = Duration.ofMinutes(10);
        if (cycle == null || cycle.isNegative() || cycle.isZero()) cycle = Duration.ofHours(24);
        if (cycle.compareTo(slotLength) < 0) cycle = slotLength;
    }

    /** Döngüdeki dilim sayısı (en az 1). */
    public int slots() { return (int) Math.max(1, cycle.toMillis() / slotLength.toMillis()); }
}
