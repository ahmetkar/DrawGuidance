package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.ClipMask
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import com.karapp.drawguidance.data.model.V56Selection
import com.karapp.drawguidance.ui.drawing.StrokeRenderer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v56 aşama 3: seçim alanı (maske), genişlet/daralt, kement dolgu, kırpma maskesi, sihirli değnek. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56SelectionTest {
    @After fun tearDown() = stopKoin()

    private fun line(id: String, color: String = "#FF0000", size: Float = 20f, type: BrushType = BrushType.INK_PEN, mask: ClipMask? = null) = Stroke(
        id, listOf(StrokePoint(10f, 50f), StrokePoint(100f, 50f), StrokePoint(190f, 50f)), color, type, size, 1f, 0, mask = mask,
    )

    private fun alpha(strokes: List<Stroke>, x: Int, y: Int): Int {
        val bmp = StrokeRenderer.renderViewportCache(strokes, 200, 100, 1f, Offset.Zero, Rect(0f, 0f, 200f, 100f))
        return android.graphics.Color.alpha(bmp.asAndroidBitmap().getPixel(x, y))
    }

    private val left = V56Selection.rect(0f, 0f, 100f, 100f)

    @Test
    fun maskClipsInsideOutsideAndInverted() {
        assertTrue(alpha(listOf(line("a", mask = ClipMask(left))), 50, 50) > 200)
        assertEquals(0, alpha(listOf(line("a", mask = ClipMask(left))), 150, 50))
        assertEquals(0, alpha(listOf(line("a", mask = ClipMask(left, inverted = true))), 50, 50))
        assertTrue(alpha(listOf(line("a", mask = ClipMask(left, inverted = true))), 150, 50) > 200)
        // Yumuşatılmış kenar: sınırda yarı saydam, içeride tam
        val soft = listOf(line("a", mask = ClipMask(left, feather = 16f)))
        assertTrue(alpha(soft, 40, 50) > 200)
        val edge = alpha(soft, 100, 50)
        val out = alpha(soft, 130, 50)
        assertTrue("kenar yarı saydam: $edge / ${alpha(soft, 108, 50)} / $out", edge in 40..215 && out < 20)
    }

    @Test
    fun lassoFillPaintsInside() {
        val fill = V56Selection.lassoFill("f", V56Selection.rect(20f, 20f, 80f, 80f), "#00FF00", 1f, 0)
        assertTrue(alpha(listOf(fill), 50, 50) > 250)
        assertEquals(0, alpha(listOf(fill), 150, 50))
    }

    @Test
    fun offsetGrowsAndShrinks() {
        val sq = V56Selection.rect(10f, 10f, 50f, 50f)
        val grown = V56Selection.offset(sq, 5f)!!
        assertEquals(50f * 50f, kotlin.math.abs(V56Selection.signedArea(grown)), 1f)
        val shrunk = V56Selection.offset(sq, -5f)!!
        assertEquals(30f * 30f, kotlin.math.abs(V56Selection.signedArea(shrunk)), 1f)
        // Ters yönlü çokgende de dışa doğru
        assertEquals(50f * 50f, kotlin.math.abs(V56Selection.signedArea(V56Selection.offset(sq.reversed(), 5f)!!)), 1f)
        assertNull("çok daralan alan", V56Selection.offset(sq, -30f))
    }

    @Test
    fun clipMaskMakeAndRelease() {
        val shape = Stroke(
            "s", V56Selection.rect(0f, 0f, 100f, 100f), "#000000", BrushType.SHAPE, 4f, 1f, 2,
            shape = ShapeSpec("rect", listOf(SubPath(4, closed = true))),
        )
        val a = line("a"); val b = line("b")
        assertTrue(V56Selection.canClip(listOf(a, b, shape)))
        assertFalse(V56Selection.canClip(listOf(shape, a)))
        val changed = V56Selection.clip(listOf(a, b, shape), "g")
        val byId = changed.associateBy { it.id }
        assertTrue(byId.getValue("s").hidden)
        assertEquals("s", byId.getValue("a").mask?.source)
        assertEquals("g", byId.getValue("b").groupId)
        val page = listOf(byId.getValue("a"), byId.getValue("b"), byId.getValue("s"))
        val released = V56Selection.release(page, setOf("a"))
        assertTrue(released.none { it.mask != null || it.hidden || it.groupId != null })
        // Kaynağı maskeyle kırpılan çizgi yalnız sınırın içinde görünür
        assertEquals(0, alpha(listOf(byId.getValue("a")), 150, 50))
    }

    @Test
    fun magicWandCriteria() {
        val t = line("t", "#FF0000", 20f)
        val near = line("n", "#F01010", 22f)
        val far = line("f", "#0000FF", 20f)
        val marker = line("m", "#FF0000", 20f, BrushType.MARKER)
        val all = listOf(t, near, far, marker)
        assertEquals(setOf("t", "n", "m"), V56Selection.similar(t, all, V56Selection.WandCriterion.COLOR, 0.1f).map { it.id }.toSet())
        assertEquals(setOf("t", "n", "f"), V56Selection.similar(t, all, V56Selection.WandCriterion.STROKE, 0.15f).map { it.id }.toSet())
        assertEquals(setOf("t", "n"), V56Selection.similar(t, all, V56Selection.WandCriterion.APPEARANCE, 0.15f).map { it.id }.toSet())
        assertEquals(setOf("t"), V56Selection.similar(t, all, V56Selection.WandCriterion.APPEARANCE, 0f).map { it.id }.toSet())
    }

    @Test
    fun areaAppliesOnlyToNewInk() {
        val old = line("old")
        val ink = line("new")
        val shape = Stroke("s", left, "#000000", BrushType.SHAPE, 4f, 1f, 2, shape = ShapeSpec("rect", listOf(SubPath(4, closed = true))))
        val out = V56Selection.applyArea(setOf("old"), listOf(old, ink, shape), ClipMask(left))
        assertNull(out[0].mask)
        assertNotNull(out[1].mask)
        assertNull("nesneler kırpılmaz", out[2].mask)
    }
}
