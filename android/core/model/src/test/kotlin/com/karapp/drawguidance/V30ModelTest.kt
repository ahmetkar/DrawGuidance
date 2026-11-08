package com.karapp.drawguidance

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** v30 gizlenen elemanlar: kayıtta korunur (v8 ek bayrağı), çizilmez ve düzenlenemez. */
class V30ModelTest {
    private fun line(id: String, hidden: Boolean = false, layer: String? = null) =
        Stroke(id, listOf(StrokePoint(0f, 0f), StrokePoint(50f, 10f)), "#112233", BrushType.PENCIL, 4f, 1f, 0, layerId = layer, hidden = hidden)

    @Test
    fun hiddenRoundTripsAndOldContentStaysOld() {
        val strokes = listOf(line("a"), line("b", hidden = true), line("c"))
        val bytes = ContentCodec.encode(strokes)
        assertEquals("gizli eleman varsa v8", 8, bytes[2].toInt())
        assertEquals(strokes, ContentCodec.decode(bytes))
        // Gizli eleman yoksa eski biçim yazılır (önceki sürümler açabilir)
        assertTrue(ContentCodec.encode(listOf(line("a"))).let { it[2].toInt() < 8 })
    }

    @Test
    fun hiddenIsNotVisibleNorEditable() {
        val layers = listOf(Layer.Default)
        val open = listOf(line("a"), line("c"))
        assertSame("gizli yoksa aynı liste", open, Layers.visible(open, layers))
        val strokes = listOf(line("a"), line("b", hidden = true), line("c"))
        assertEquals(listOf("a", "c"), Layers.visible(strokes, layers).map { it.id })
        assertEquals(setOf("b"), Layers.lockedIds(strokes, layers))
        assertFalse(Layers.editable(strokes[1], layers))
        // Gizli katmanla birlikte
        val two = listOf(Layer.Default, Layer("l2", "Üst", visible = false))
        val mixed = strokes + line("d", layer = "l2")
        assertEquals(listOf("a", "c"), Layers.visible(mixed, two).map { it.id })
        assertEquals(setOf("b", "d"), Layers.lockedIds(mixed, two))
    }
}
