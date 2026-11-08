package com.karapp.drawguidance.server.sync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * v40 eşitleme dilimlerinin hesabı (saf ve belirlenimci; birim testli):
 *
 * <ul>
 *   <li><b>Grup</b>: kullanıcının sıra numarası ({@code seq}, ilk izin isteğinde atanır, değişmez) /
 *       {@code groupSize}.</li>
 *   <li><b>Dilim</b>: grup mod dilim sayısı (144). 144'ten fazla grup varsa birden çok grup aynı dilimi paylaşır.</li>
 *   <li><b>Kimliksiz cihaz</b>: cihaz kimliğinin SHA-256 özeti mod dilim sayısı.</li>
 *   <li>Döngü Unix zamanının başından ({@code 1970-01-01T00:00Z}) sayılır: 24 saatlik döngüde gün UTC gece yarısı
 *       başlar; dilim {@code s} her gün {@code s × slotLength} sonra açılır, {@code slotLength} sürer.</li>
 * </ul>
 */
public final class SyncSlots {
    private SyncSlots() {}

    /** İzin yanıtının zaman bilgisi: şu an izinli mi, ilgili dilimin başı/sonu ve sonraki dilimin başı. */
    public record Window(boolean allowed, Instant slotStart, Instant slotEnd, Instant nextSlotStart) {}

    /** Sıra numarasından grup (0'dan). */
    public static long group(long seq, int groupSize) { return Math.max(0, seq) / Math.max(1, groupSize); }

    /** Gruptan dilim (0..slots-1). */
    public static int slotOfGroup(long group, int slots) { return (int) Math.floorMod(group, (long) Math.max(1, slots)); }

    /** Kimliksiz cihaz: kimliğin SHA-256 özetinin ilk 8 baytı mod dilim sayısı. */
    public static int slotOfDevice(String deviceId, int slots) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(deviceId.getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) v = (v << 8) | (d[i] & 0xff);
            return (int) Math.floorMod(v, (long) Math.max(1, slots));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * [now] anında [slot] diliminin durumu. İzinliyse {@code slotStart..slotEnd} şimdiki dilim, {@code nextSlotStart}
     * bir sonraki döngüdeki aynı dilim; değilse {@code slotStart..slotEnd} gelecek dilim ve {@code nextSlotStart} onun
     * başı.
     */
    public static Window window(int slot, Instant now, SyncPermitProperties p) {
        long len = p.slotLength().toMillis();
        long cycle = (long) p.slots() * len;
        long t = now.toEpochMilli();
        long cycleStart = Math.floorDiv(t, cycle) * cycle;
        long start = cycleStart + (long) slot * len;
        if (t >= start && t < start + len) {
            return new Window(true, Instant.ofEpochMilli(start), Instant.ofEpochMilli(start + len), Instant.ofEpochMilli(start + cycle));
        }
        long next = t < start ? start : start + cycle;
        return new Window(false, Instant.ofEpochMilli(next), Instant.ofEpochMilli(next + len), Instant.ofEpochMilli(next));
    }
}
