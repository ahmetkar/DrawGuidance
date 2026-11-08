package com.karapp.drawguidance.server.sync;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v40 eşitleme dilimleri: grup, dilim, pencere hesabı (belirlenimci) ve ayarlanabilir uzunluklar. */
class SyncSlotsTest {
    static final SyncPermitProperties DEFAULT = new SyncPermitProperties(null, null, null);
    static final Instant DAY = Instant.parse("2026-10-24T00:00:00Z");

    @Test
    void defaultsAre1000Users144SlotsOf10Minutes() {
        assertEquals(1000, DEFAULT.groupSize());
        assertEquals(Duration.ofMinutes(10), DEFAULT.slotLength());
        assertEquals(144, DEFAULT.slots());
    }

    @Test
    void groupsOf1000AndSlotsWrapAfter144() {
        assertEquals(0, SyncSlots.group(0, 1000));
        assertEquals(0, SyncSlots.group(999, 1000));
        assertEquals(1, SyncSlots.group(1000, 1000));
        assertEquals(143, SyncSlots.slotOfGroup(143, 144));
        // 144'ten fazla grup: aynı dilimi paylaşırlar
        assertEquals(0, SyncSlots.slotOfGroup(144, 144));
        assertEquals(5, SyncSlots.slotOfGroup(149, 144));
        assertEquals(SyncSlots.slotOfGroup(SyncSlots.group(150_000, 1000), 144), SyncSlots.slotOfGroup(6, 144));
    }

    @Test
    void insideOwnSlotIsAllowed() {
        // Grup 3: 00:30–00:40 UTC
        SyncSlots.Window w = SyncSlots.window(3, DAY.plus(Duration.ofMinutes(35)), DEFAULT);
        assertTrue(w.allowed());
        assertEquals(DAY.plus(Duration.ofMinutes(30)), w.slotStart());
        assertEquals(DAY.plus(Duration.ofMinutes(40)), w.slotEnd());
        assertEquals(DAY.plus(Duration.ofHours(24)).plus(Duration.ofMinutes(30)), w.nextSlotStart());
        // Başı dahil, sonu hariç
        assertTrue(SyncSlots.window(3, DAY.plus(Duration.ofMinutes(30)), DEFAULT).allowed());
        assertFalse(SyncSlots.window(3, DAY.plus(Duration.ofMinutes(40)), DEFAULT).allowed());
    }

    @Test
    void beforeAndAfterSlotPointToNextOccurrence() {
        SyncSlots.Window before = SyncSlots.window(100, DAY.plus(Duration.ofHours(1)), DEFAULT);
        assertFalse(before.allowed());
        assertEquals(DAY.plus(Duration.ofMinutes(1000)), before.nextSlotStart());
        assertEquals(before.nextSlotStart(), before.slotStart());
        assertEquals(DAY.plus(Duration.ofMinutes(1010)), before.slotEnd());
        SyncSlots.Window after = SyncSlots.window(1, DAY.plus(Duration.ofHours(5)), DEFAULT);
        assertFalse(after.allowed());
        assertEquals(DAY.plus(Duration.ofDays(1)).plus(Duration.ofMinutes(10)), after.nextSlotStart());
    }

    @Test
    void everyMomentBelongsToExactlyOneSlot() {
        for (int m = 0; m < 24 * 60; m += 7) {
            Instant t = DAY.plus(Duration.ofMinutes(m));
            int allowed = 0;
            for (int s = 0; s < 144; s++) if (SyncSlots.window(s, t, DEFAULT).allowed()) allowed++;
            assertEquals(1, allowed, "dakika " + m);
        }
    }

    @Test
    void configurableLengths() {
        SyncPermitProperties p = new SyncPermitProperties(500, Duration.ofMinutes(30), Duration.ofHours(12));
        assertEquals(24, p.slots());
        assertEquals(2, SyncSlots.group(1000, p.groupSize()));
        SyncSlots.Window w = SyncSlots.window(2, DAY.plus(Duration.ofMinutes(70)), p);
        assertTrue(w.allowed());
        assertEquals(DAY.plus(Duration.ofHours(13)), w.nextSlotStart());
    }

    @Test
    void deviceSlotIsDeterministicAndSpread() {
        assertEquals(SyncSlots.slotOfDevice("device-abc-123", 144), SyncSlots.slotOfDevice("device-abc-123", 144));
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            int s = SyncSlots.slotOfDevice("device-" + i, 144);
            assertTrue(s >= 0 && s < 144);
            seen.add(s);
        }
        assertTrue(seen.size() > 130, "dilimlere yayılır: " + seen.size());
    }
}
