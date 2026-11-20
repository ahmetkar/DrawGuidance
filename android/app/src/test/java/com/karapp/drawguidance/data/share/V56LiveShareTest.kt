package com.karapp.drawguidance.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v56 canlı yayın düzeltmeleri (2C-1, 2C-2): ara-ver sahipleri, şifre kapısı, onay kuralları, canlı mürekkep. */
class V56LiveShareTest {

    @Test
    fun reopenedEditorIsNotPausedByOldEditorsLateDispose() {
        val o = LivePauseOwners()
        o.attach("c", "A")
        assertFalse(o.shouldPause("c"))
        // Yayın kabul edildi: yeni düzenleyici B açılır, sonra eski A'nın onDispose'u gelir
        o.attach("c", "B")
        o.detach("c", "A")
        assertFalse("eski düzenleyicinin kapanışı yayını durdurmamalı", o.shouldPause("c"))
    }

    @Test
    fun pauseResumeDisposeSequenceKeepsBroadcastRunning() {
        // Plan testi: pause(A), resume(B), dispose(A) sonrasında yayın sürüyor
        val o = LivePauseOwners()
        o.attach("c", "A")
        o.pause("c", "A")
        assertTrue(o.shouldPause("c"))
        o.attach("c", "B")
        o.resume("c", "B")
        assertTrue("B, A'nın arasını kaldıramaz", o.shouldPause("c"))
        o.detach("c", "A")
        assertFalse(o.shouldPause("c"))
    }

    @Test
    fun noEditorMeansPausedAndReasonOwnersAreScoped() {
        val o = LivePauseOwners()
        assertTrue(o.shouldPause("c"))
        o.attach("c", "A")
        o.pause("c", "A:photo")
        assertTrue(o.shouldPause("c"))
        o.resume("c", "A:photo")
        assertFalse(o.shouldPause("c"))
        o.pause("c", "A:photo")
        o.detach("c", "A")
        o.attach("c", "B")
        assertFalse("ayrılan düzenleyicinin nedenli araları da silinir", o.shouldPause("c"))
        assertTrue(o.isOpen("c"))
        assertFalse(o.isOpen("x"))
    }

    @Test
    fun pairingCodeIsNotGeneratedBeforeApproval() {
        var generated = 0
        val gate = PairingCodeGate(known = false) { generated++; "123456" }
        assertNull(gate.code)
        assertEquals("onaydan önce şifre üretilmemeli", 0, generated)
        assertEquals("123456", gate.approve())
        assertEquals(1, generated)
        gate.approve()
        assertEquals(1, generated)
    }

    @Test
    fun knownDeviceNeedsApprovalButNoCode() {
        val gate = PairingCodeGate(known = true) { error("eşleşmiş cihazda şifre üretilmemeli") }
        assertNull(gate.approve())
        assertTrue(gate.approved)
    }

    @Test
    fun threeRejectionsInAMinuteBlockForFiveMinutes() {
        var now = 0L
        val p = LiveApprovalPolicy { now }
        p.onRejected("k"); now += 10_000
        p.onRejected("k"); now += 10_000
        assertFalse(p.isBlocked("k"))
        p.onRejected("k")
        assertTrue(p.isBlocked("k"))
        now += LiveApprovalPolicy.BLOCK_MS - 1
        assertTrue(p.isBlocked("k"))
        now += 2
        assertFalse(p.isBlocked("k"))
    }

    @Test
    fun spreadOutRejectionsDoNotBlock() {
        var now = 0L
        val p = LiveApprovalPolicy { now }
        repeat(5) { p.onRejected("k"); now += 40_000 }
        assertFalse(p.isBlocked("k"))
    }

    @Test
    fun approvalIsUsedOnceAndExpires() {
        var now = 0L
        val p = LiveApprovalPolicy { now }
        p.onApproved("k")
        assertTrue(p.consumeApproval("k"))
        assertFalse("onay bir kez kullanılır", p.consumeApproval("k"))
        p.onApproved("k")
        now += LiveApprovalPolicy.APPROVAL_VALID_MS + 1
        assertFalse(p.consumeApproval("k"))
    }

    @Test
    fun liveInkRoundTripAndMerge() {
        val first = ShareInk("s1", "PENCIL", "#FF0000", 6f, 1f, 0, listOf(1f, 2f, 1f, 3f, 4f, 0.5f))
        val bytes = ShareWire.encode(ShareWire.LIVE_INK, ShareWire.json.encodeToString(ShareInk.serializer(), first))
        val m = ShareWire.decode(bytes)
        assertEquals(ShareWire.LIVE_INK, m.type)
        val back = ShareWire.json.decodeFromString(ShareInk.serializer(), m.json)
        assertEquals(first, back)
        val a = LiveInk.merge(null, back)
        assertEquals(6, a.pts.size)
        // Tekrar eden nokta (from=1) atlanır, yalnız yenisi eklenir
        val b = LiveInk.merge(a, ShareInk("s1", "PENCIL", "#FF0000", 6f, 1f, 1, listOf(3f, 4f, 0.5f, 5f, 6f, 1f)))
        assertEquals(listOf(1f, 2f, 1f, 3f, 4f, 0.5f, 5f, 6f, 1f), b.pts)
        // Kayıp parça: from ileride, noktalar yine eklenir
        val c = LiveInk.merge(b, ShareInk("s1", "PENCIL", "#FF0000", 6f, 1f, 9, listOf(9f, 9f, 1f)))
        assertEquals(12, c.pts.size)
        assertNotNull(c)
    }
}
