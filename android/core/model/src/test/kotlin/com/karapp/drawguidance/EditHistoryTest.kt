package com.karapp.drawguidance

import com.karapp.drawguidance.data.history.EditHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geri al / yinele geçmişi: kayıt, birleştirme, sınır ve yineleme listesinin temizlenmesi. */
class EditHistoryTest {

    @Test fun undoAndRedoWalkThroughTheRecordedStates() {
        val h = EditHistory<String>()
        h.record("a", "b")
        h.record("b", "c")
        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
        assertEquals("b", h.undo())
        assertEquals("a", h.undo())
        assertNull(h.undo())
        assertEquals("b", h.redo())
        assertEquals("c", h.redo())
        assertNull(h.redo())
    }

    @Test fun newChangeClearsRedo() {
        val h = EditHistory<String>()
        h.record("a", "b")
        h.undo()
        assertTrue(h.canRedo)
        h.record("a", "x")
        assertFalse(h.canRedo)
        assertEquals("a", h.undo())
    }

    @Test fun sameKeyWithinWindowCoalescesAndOtherKeysOrPausesDoNot() {
        val h = EditHistory<Int>(coalesceWindowMs = 1000)
        h.record(0, 1, key = "slider", now = 0)
        h.record(1, 2, key = "slider", now = 500)
        h.record(2, 3, key = "slider", now = 900)
        assertEquals(1, h.undoSize)
        h.record(3, 4, key = "slider", now = 5000)   // ara verildi: yeni adım
        h.record(4, 5, key = "other", now = 5100)    // başka anahtar: yeni adım
        assertEquals(3, h.undoSize)
        assertEquals(4, h.undo())
        assertEquals(3, h.undo())
        assertEquals(0, h.undo())
        // Geri alma sonrası aynı anahtarla gelen değişiklik yeni adımdır
        h.record(0, 7, key = "slider", now = 5200)
        h.record(7, 8, key = "slider", now = 5300)
        assertEquals(1, h.undoSize)
        h.breakCoalescing()
        h.record(8, 9, key = "slider", now = 5400)
        assertEquals(2, h.undoSize)
    }

    @Test fun oldestEntriesAreDroppedBeyondTheLimit() {
        val h = EditHistory<Int>(maxSize = 3)
        for (i in 0 until 10) h.record(i, i + 1)
        assertEquals(3, h.undoSize)
        assertEquals(9, h.undo())
        assertEquals(8, h.undo())
        assertEquals(7, h.undo())
        assertNull(h.undo())
    }
}
