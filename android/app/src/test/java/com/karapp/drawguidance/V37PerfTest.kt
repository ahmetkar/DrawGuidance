package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.isQuad
import com.karapp.drawguidance.ui.drawing.ShapeKind
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.screens.canvasedit.editor.Element
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementIndex
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * v37 (6) veri yapısı hızlandırmaları sonucu değiştirmez: dokunma sınaması üstten aşağı ve kutu elemesiyle eskisiyle
 * aynı elemanı bulur; şekil adı tablodan aranır; eleman kimlik kümesi bir kez kurulur.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class V37PerfTest {
    @org.junit.After fun stop() = org.koin.core.context.stopKoin()

    private fun scribble(id: String, rnd: Random, size: Float, type: BrushType = BrushType.INK_PEN): Stroke {
        var x = rnd.nextFloat() * 2000f
        var y = rnd.nextFloat() * 2000f
        val pts = (0 until 40).map { StrokePoint(x, y).also { x += rnd.nextFloat() * 30f - 15f; y += rnd.nextFloat() * 30f - 15f } }
        return Stroke(id, pts, "#112233", type, size, 1f, 0, null)
    }

    /** Eski yöntem: bütün çizimlere uzaklık, en üstteki değen. */
    private fun reference(index: ElementIndex, p: Offset, tolerance: Float, skip: Set<String> = emptySet()): Element? {
        var best: Stroke? = null
        index.strokes.forEach { s ->
            if (s.brushType == BrushType.ERASER || s.points.isEmpty() || s.id in skip || !ElementOps.insideClip(s, p)) return@forEach
            val half = if (s.brushType.isQuad) 0f else ElementOps.halfWidth(s)
            if (ElementOps.distanceToShape(p, s) <= half + tolerance) best = s
        }
        return best?.let { index.elementOf(it.id) }
    }

    @Test
    fun hitTestMatchesFullScan() {
        val rnd = Random(37)
        val strokes = (0 until 600).map { i -> scribble("s$i", rnd, 2f + rnd.nextFloat() * 30f, if (i % 50 == 7) BrushType.ERASER else BrushType.INK_PEN) }
        val index = ElementOps.index(strokes)
        var hits = 0
        repeat(3000) {
            val p = Offset(rnd.nextFloat() * 2100f - 50f, rnd.nextFloat() * 2100f - 50f)
            val tol = rnd.nextFloat() * 24f
            val want = reference(index, p, tol)
            val got = ElementOps.hitTest(index, p, tol)
            assertEquals("$p", want?.strokeIds, got?.strokeIds)
            if (got != null) hits++
        }
        assert(hits > 100) { "çok az değme: $hits" }
        // Atlanan çizim seçilmez
        val top = strokes.last()
        val p = Offset(top.points[10].x, top.points[10].y)
        assertEquals(reference(index, p, 2f, setOf(top.id))?.strokeIds, ElementOps.hitTest(index, p, 2f, skip = setOf(top.id))?.strokeIds)
        assertNull(ElementOps.hitTest(index, Offset(-500f, -500f), 4f))
    }

    @Test
    fun builtinShapeLookupAndElementIds() {
        ShapeKind.entries.forEach { assertSame(it, ShapeRegistry.builtin(it.name)) }
        assertNull(ShapeRegistry.builtin("lib.yok"))
        val e = ElementOps.index(listOf(scribble("a", Random(1), 4f))).elements.single()
        assertSame("kimlik kümesi bir kez kurulur", e.allIds, e.allIds)
        assertEquals(setOf("a"), e.allIds)
    }
}
