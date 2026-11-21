package com.karapp.drawguidance

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.local.V11Flatten
import com.karapp.drawguidance.data.model.ArrowKind
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CapStyle
import com.karapp.drawguidance.data.model.CloneLink
import com.karapp.drawguidance.data.model.LayerBlend
import com.karapp.drawguidance.data.model.PaintLayer
import com.karapp.drawguidance.data.model.RepeatKind
import com.karapp.drawguidance.data.model.RepeatSpec
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import com.karapp.drawguidance.data.model.V56Arrange
import com.karapp.drawguidance.data.model.V56Vector
import com.karapp.drawguidance.data.model.VectorStyle
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import kotlin.math.abs

/** v56 aşama 8A saf kurallar: bağlı kopya, tekrar, opaklık maskesi, görünüm kaydı. */
class V56VectorRulesTest {
    private fun line(id: String, x: Float = 0f, y: Float = 0f) =
        Stroke(id, listOf(StrokePoint(x, y), StrokePoint(x + 10f, y + 10f)), "#000000", BrushType.INK_PEN, 4f, 1f, 0)
    private var n = 0
    private fun id() = "n${n++}"

    @Test fun cloneFollowsSource() {
        val src = line("a")
        val withClone = V56Vector.clone(listOf(src), setOf("a"), 50f, 0f, ::id)
        assertEquals(2, withClone.size)
        val clone = withClone[1]
        assertEquals("a", clone.cloneOf?.source)
        assertEquals(50f, clone.points[0].x, 1e-3f)
        // Kaynak değişince kopya güncellenir
        val moved = withClone.map { if (it.id == "a") V56Arrange.shift(it, 0f, 20f).copy(colorHex = "#FF0000") else it }
        val synced = V56Vector.sync(withClone, moved)
        assertEquals(20f, synced[1].points[0].y, 1e-3f)
        assertEquals("#FF0000", synced[1].colorHex)
        assertEquals(clone.id, synced[1].id)
        // Kopya kendi taşındıysa kayma korunur
        val cloneMoved = synced.map { if (it.id == clone.id) V56Arrange.shift(it, 100f, 0f) else it }
        val again = V56Vector.sync(cloneMoved, cloneMoved.map { if (it.id == "a") it.copy(sizePx = 9f) else it })
        assertEquals(150f, again[1].points[0].x, 1e-3f)
        assertEquals(9f, again[1].sizePx, 1e-3f)
        // Birlikte taşınırsa dokunulmaz; bağı koparılınca güncellenmez
        assertNull(V56Vector.unlink(again, setOf(clone.id))[1].cloneOf)
    }

    @Test fun repeatKinds() {
        val src = line("a", 100f, 100f)
        val (lin, c1) = V56Vector.repeat(listOf(src), setOf("a"), RepeatSpec(RepeatKind.LINEAR, count = 3, dx = 30f), ::id)
        assertEquals(3, c1)
        assertEquals(listOf(130f, 160f, 190f), lin.drop(1).map { it.points[0].x })
        assertNotNull(lin[0].repeat)
        assertTrue(lin.drop(1).all { it.cloneOf?.repeat == true && it.groupId == lin[0].groupId })
        // Yeniden uygulama eski kopyaları siler
        val (grid, c2) = V56Vector.repeat(lin, setOf("a"), RepeatSpec(RepeatKind.GRID, cols = 2, rows = 2, gapX = 0f, gapY = 0f), ::id)
        assertEquals(3, c2); assertEquals(4, grid.size)
        val (rad, c3) = V56Vector.repeat(listOf(src), setOf("a"), RepeatSpec(RepeatKind.RADIAL, count = 4, radius = 0f), ::id)
        assertEquals(3, c3)
        val p = rad[2].points // 180° dönmüş: (110,110) → (100,100)
        assertEquals(100f, p[1].x, 0.01f); assertEquals(100f, p[1].y, 0.01f)
        val (mir, _) = V56Vector.repeat(listOf(src), setOf("a"), RepeatSpec(RepeatKind.MIRROR, gapX = 0f), ::id)
        assertEquals(120f, mir[1].points[0].x, 0.01f)
        val (step, c5) = V56Vector.repeat(listOf(src), setOf("a"), RepeatSpec(RepeatKind.STEP, count = 2, dx = 10f, dy = 0f, angle = 0f, scale = 2f), ::id)
        assertEquals(2, c5)
        assertEquals(16f, step[2].sizePx, 0.01f)
        assertEquals(RepeatKind.STEP, V56Vector.repeatOf(step, setOf(step[1].id))?.kind)
        val removed = V56Vector.removeRepeat(step, setOf(step[2].id))
        assertEquals(1, removed.size); assertNull(removed[0].repeat)
        // Kaynak değişince tekrar kopyaları güncellenir
        val changed = lin.map { if (it.id == "a") it.copy(colorHex = "#00FF00") else it }
        assertTrue(V56Vector.sync(lin, changed).all { it.colorHex == "#00FF00" })
    }

    @Test fun opacityMaskAndCodec() {
        val a = line("a"); val m = line("m").copy(order = 1, colorHex = "#FFFFFF")
        val masked = V56Vector.makeOpacityMask(listOf(a, m), setOf("a", "m"))
        assertEquals(1, masked.size)
        assertEquals("m", masked[0].style2?.opacityMask?.single()?.id)
        val shifted = V56Arrange.shift(masked[0], 5f, 0f)
        assertEquals(5f, shifted.style2!!.opacityMask!![0].points[0].x, 1e-3f)
        val released = V56Vector.releaseOpacityMask(masked, setOf("a"))
        assertEquals(listOf("a", "m"), released.map { it.id })
        assertNull(released[0].style2)

        val shape = Stroke("s", listOf(StrokePoint(0f, 0f), StrokePoint(50f, 0f)), "#000000", BrushType.SHAPE, 2f, 1f, 0,
            shape = ShapeSpec("line", listOf(SubPath(2))),
            style2 = VectorStyle(cap = CapStyle.BUTT, arrowEnd = ArrowKind.TRIANGLE, dash = listOf(6f, 3f), blend = LayerBlend.MULTIPLY,
                strokes = listOf(PaintLayer("#FFFFFF", 8f)), opacityMask = listOf(m)),
            cloneOf = CloneLink("x", tx = 3f), repeat = RepeatSpec(RepeatKind.GRID))
        val back = ContentCodec.decodePages(ContentCodec.encode(listOf(shape)))[0][0]
        assertEquals(shape.style2, back.style2)
        assertEquals(shape.cloneOf, back.cloneOf)
        assertEquals(shape.repeat, back.repeat)
        val flat = V11Flatten.downgrade(ContentCodec.decodeDocument(ContentCodec.encode(listOf(shape))))
        assertNull(flat.pages[0][0].style2)
        // Ok ucu ve serbest gradyan
        val head = V56Vector.arrowHead(ArrowKind.TRIANGLE, 10f, 0f, 1f, 0f, 4f)
        assertEquals(10f, head[1].first, 1e-3f)
        val c = V56Vector.freeGradientColor(listOf(com.karapp.drawguidance.data.model.GradientPoint(0f, 0f, "#FF0000"), com.karapp.drawguidance.data.model.GradientPoint(1f, 1f, "#0000FF")), 0f, 0f) { 0xFF000000.toInt() or it.drop(1).toInt(16) }
        assertEquals(0xFFFF0000.toInt(), c)
        assertTrue(abs(V56Vector.about(0f, 0f, 90f, 1f, 0f, 0f).map(1f, 0f).second - 1f) < 1e-4f)
    }
}

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56VectorUiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, strokes: List<Stroke>) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking {
            val c = repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.ART)
            repo().saveStrokes(c.localId, listOf(strokes))
        }
        waitForText(name)
        compose.onAllNodes(hasText(name))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name && vm().canvas.value?.strokes?.size == strokes.size }
    }

    private fun shape(id: String, x: Float) = Stroke(id, listOf(StrokePoint(x, 100f), StrokePoint(x + 80f, 100f), StrokePoint(x + 80f, 180f), StrokePoint(x, 180f)),
        "#000000", BrushType.SHAPE, 4f, 1f, 0, shape = ShapeSpec("rect", listOf(SubPath(4, closed = true))))

    @Test
    fun cloneRepeatMaskAndStyleThroughViewModel() {
        open("Vektör", listOf(shape("a", 100f), shape("b", 400f)))
        compose.runOnIdle { vm().setMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.SELECT); vm().clearSelection(); vm().toggleElement("a") }
        waitFor { vm().selectedIds.value == setOf("a") }
        compose.runOnIdle { vm().cloneSelection() }
        waitFor { vm().canvas.value!!.strokes.size == 3 }
        val cloneId = vm().canvas.value!!.strokes[2].id
        // Kaynağın görünümü değişince kopya da değişir
        compose.runOnIdle { vm().clearSelection(); vm().toggleElement("a") }
        compose.runOnIdle { vm().updateVectorStyle { it.copy(cap = CapStyle.SQUARE) } }
        waitFor { vm().canvas.value!!.strokes.first { it.id == cloneId }.style2?.cap == CapStyle.SQUARE }
        compose.runOnIdle { vm().applyRepeat(RepeatSpec(RepeatKind.LINEAR, count = 2, dx = 10f)) }
        waitFor { vm().canvas.value!!.strokes.size == 5 }
        compose.runOnIdle { vm().removeRepeat() }
        waitFor { vm().canvas.value!!.strokes.size == 3 }
        compose.runOnIdle { vm().clearSelection(); vm().toggleElement("a"); vm().toggleElement("b") }
        waitFor { vm().selectedIds.value.size == 2 }
        compose.runOnIdle { vm().makeOpacityMask() }
        waitFor { vm().canvas.value!!.strokes.size == 2 }
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value!!.strokes.size == 3 }
    }
}
