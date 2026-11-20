package com.karapp.drawguidance

import com.karapp.drawguidance.data.clipboard.ElementClipboard
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.ClipMask
import com.karapp.drawguidance.data.model.ElementClip
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextBox
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.data.model.V56Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v56 aşama 3A (2C-3): sayfalar arası kes, kopyala, yapıştır. */
class V56ClipboardTest {
    private fun line(id: String, x: Float = 10f, group: String? = null, layer: String? = null, mask: ClipMask? = null) = Stroke(
        id, listOf(StrokePoint(x, 20f), StrokePoint(x + 40f, 20f)), "#FF0000", BrushType.INK_PEN, 6f, 1f, 0,
        groupId = group, layerId = layer, mask = mask,
    )

    private var n = 0
    private fun nid() = "n${n++}"

    @Test
    fun copyPasteGivesNewIdsAndKeepsGroups() {
        val page = listOf(line("a", group = "g"), line("b", group = "g"), line("c"))
        val clip = ElementClip.collect(page, setOf("a", "b"))
        assertEquals(listOf("a", "b"), clip.map { it.id })
        val pasted = ElementClip.paste(clip, ::nid, 3)
        assertTrue(pasted.none { it.id in setOf("a", "b") })
        assertEquals(1, pasted.map { it.groupId }.toSet().size)
        assertNotEquals("g", pasted[0].groupId)
        assertEquals(listOf(3, 4), pasted.map { it.order })
        assertEquals(page[0].points, pasted[0].points)
    }

    @Test
    fun clipMaskSourceTravelsAndIsRemapped() {
        val mask = ClipMask(V56Selection.rect(0f, 0f, 50f, 50f), source = "s")
        val src = Stroke("s", V56Selection.rect(0f, 0f, 50f, 50f), "#000000", BrushType.LASSO_FILL, 1f, 1f, 1, groupId = "g", hidden = true)
        val page = listOf(line("a", group = "g", mask = mask), src)
        val clip = ElementClip.collect(page, setOf("a"))
        assertEquals(listOf("a", "s"), clip.map { it.id })
        val pasted = ElementClip.paste(clip, ::nid, 0, dx = 5f)
        assertEquals(pasted[1].id, pasted[0].mask?.source)
        assertEquals(5f, pasted[0].mask!!.points[0].x, 0.001f)
    }

    @Test
    fun pasteGoesToActiveLayerAndRemapsAssets() {
        val img = Stroke("i", V56Selection.rect(0f, 0f, 10f, 10f), "#000000", BrushType.IMAGE, 1f, 1f, 0, assetId = "A", layerId = "L2")
        val text = Stroke(
            "t", V56Selection.rect(0f, 0f, 10f, 10f), "#000000", BrushType.TEXT, 1f, 1f, 1,
            text = TextSpec("x", box = TextBox(imageId = BackgroundSpec.ASSET + "B")),
        )
        assertEquals(setOf("A", "B"), ElementClip.assetIds(listOf(img, text)))
        val pasted = ElementClip.paste(listOf(img, text), ::nid, 0, assets = mapOf("A" to "A2", "B" to "B2"))
        assertEquals(null, pasted[0].layerId)
        assertEquals("A2", pasted[0].assetId)
        assertEquals(BackgroundSpec.ASSET + "B2", pasted[1].text?.box?.imageId)
    }

    @Test
    fun placementKeepsPositionCentersWhenOffPageAndShiftsOnRepeat() {
        val b = floatArrayOf(10f, 10f, 50f, 30f)
        assertEquals(0f to 0f, ElementClip.offset(b, 100f, 100f))
        assertEquals(16f to 16f, ElementClip.offset(b, 100f, 100f, repeat = 1))
        // Hedef sayfa küçük: sayfanın ortasına
        val small = ElementClip.offset(floatArrayOf(100f, 100f, 140f, 120f), 100f, 100f)
        assertEquals(50f - 120f to 50f - 110f, small)
        // Görünür alanın dışında: görünür alanın ortasına
        assertEquals(250f - 30f to 50f - 20f, ElementClip.offset(b, 400f, 100f, visible = floatArrayOf(200f, 0f, 300f, 100f)))
        // Buraya yapıştır
        assertEquals(70f to 80f, ElementClip.offset(b, 100f, 100f, anchor = 100f to 100f))
    }

    @Test
    fun clipboardRepeatRule() {
        ElementClipboard.set(listOf(line("a")), "c1", 0, cut = false)
        assertEquals("kaynak sayfada ilk yapıştırma kaydırılır", 1, ElementClipboard.nextRepeat("c1", 0, ""))
        assertEquals(2, ElementClipboard.nextRepeat("c1", 0, ""))
        assertEquals("başka sayfada aynı yere", 0, ElementClipboard.nextRepeat("c1", 1, ""))
        ElementClipboard.set(listOf(line("a")), "c1", 0, cut = true)
        assertEquals("kesilmişse aynı yere", 0, ElementClipboard.nextRepeat("c1", 0, ""))
        ElementClipboard.clear()
    }
}
