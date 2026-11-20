package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.LayerBlend
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.LayerCompositor
import com.karapp.drawguidance.ui.drawing.StrokeRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.koin.core.context.stopKoin
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v56 aşama 2: katman opaklığı, alttakine kırpma, alfa kilidi ve önbelleğe ekleme kuralı. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56LayerCompositeTest {
    @After fun tearDown() = stopKoin()

    private val base = Layer.Default
    private fun line(id: String, x0: Float, x1: Float, y: Float, layer: String? = null, locked: Boolean = false) = Stroke(
        id, listOf(StrokePoint(x0, y), StrokePoint((x0 + x1) / 2, y), StrokePoint(x1, y)), "#FF0000", BrushType.INK_PEN, 20f, 1f, 0,
        layerId = layer, params = if (locked) BrushParams(alphaLocked = true) else null,
    )

    private fun alphaAt(strokes: List<Stroke>, layers: List<Layer>, x: Int, y: Int): Int {
        val bmp = StrokeRenderer.renderViewportCache(strokes, 200, 100, 1f, Offset.Zero, Rect(0f, 0f, 200f, 100f), layers = layers)
        return android.graphics.Color.alpha(bmp.asAndroidBitmap().getPixel(x, y))
    }

    @Test
    fun layerOpacityHalvesAlpha() {
        val top = Layer("t", "Üst")
        val strokes = listOf(line("a", 20f, 180f, 50f, "t"))
        val full = alphaAt(strokes, listOf(base, top), 100, 50)
        val half = alphaAt(strokes, listOf(base, top.copy(opacity = 0.5f)), 100, 50)
        assertTrue("çizgi görünmeli: $full", full > 200)
        assertEquals(full / 2.0, half.toDouble(), 6.0)
    }

    @Test
    fun clipToBelowShowsOnlyWhereBaseIsPainted() {
        val top = Layer("t", "Gölge", clipToBelow = true)
        // Taban yalnız solda; üstteki çizgi boydan boya
        val strokes = listOf(line("b", 10f, 90f, 50f), line("c", 10f, 190f, 50f, "t").copy(colorHex = "#0000FF"))
        val layers = listOf(base, top)
        assertTrue(alphaAt(strokes, layers, 50, 50) > 200)
        assertEquals("taban boş yerde kırpılan katman görünmez", 0, alphaAt(strokes, layers, 150, 50))
        assertTrue(alphaAt(strokes, listOf(base, top.copy(clipToBelow = false)), 150, 50) > 200)
    }

    @Test
    fun alphaLockedStrokePaintsOnlyOverExistingInk() {
        val strokes = listOf(line("b", 10f, 90f, 50f), line("l", 10f, 190f, 50f, locked = true).copy(colorHex = "#00FF00"))
        assertTrue(alphaAt(strokes, listOf(base), 50, 50) > 200)
        assertEquals(0, alphaAt(strokes, listOf(base), 150, 50))
    }

    @Test
    fun appendRuleRefusesCompositedLayers() {
        val plain = listOf(base, Layer("t", "Üst"))
        assertTrue(LayerCompositor.appendSafe(listOf(line("a", 0f, 1f, 1f, "t")), plain))
        val blended = listOf(base, Layer("t", "Üst", blend = LayerBlend.MULTIPLY))
        assertFalse(LayerCompositor.appendSafe(listOf(line("a", 0f, 1f, 1f, "t")), blended))
        assertTrue("birleştirilmeyen alttaki katmana ekleme", LayerCompositor.appendSafe(listOf(line("a", 0f, 1f, 1f)), blended))
        val clipped = listOf(base, Layer("t", "Gölge", clipToBelow = true))
        assertFalse("kırpılan katmanın tabanı", LayerCompositor.appendSafe(listOf(line("a", 0f, 1f, 1f)), clipped))
        assertFalse(LayerCompositor.appendSafe(listOf(line("a", 0f, 1f, 1f, locked = true)), plain))
    }
}
