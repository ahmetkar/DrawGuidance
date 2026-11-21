package com.karapp.drawguidance

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SymmetryKind
import com.karapp.drawguidance.data.model.SymmetrySpec
import com.karapp.drawguidance.data.model.V56Symmetry
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import kotlin.math.abs

/** v56 aşama 8 saf kurallar: simetri dönüşümleri, kopyalar, eksenler, desen. */
class V56SymmetryRulesTest {
    private fun eq(a: Pair<Float, Float>, x: Float, y: Float) = abs(a.first - x) < 0.01f && abs(a.second - y) < 0.01f

    @Test fun transforms() {
        val v = SymmetrySpec(SymmetryKind.VERTICAL, 2, 100f, 100f)
        val t = V56Symmetry.transforms(v)
        assertEquals(1, t.size)
        assertTrue(eq(t[0].map(100f, 100f, 130f, 50f), 70f, 50f))
        val h = V56Symmetry.transforms(v.copy(kind = SymmetryKind.HORIZONTAL))[0]
        assertTrue(eq(h.map(100f, 100f, 130f, 50f), 130f, 150f))
        val q = V56Symmetry.transforms(v.copy(kind = SymmetryKind.QUAD))
        assertEquals(3, q.size)
        assertTrue(eq(q[2].map(100f, 100f, 130f, 50f), 70f, 150f))
        val r = V56Symmetry.transforms(v.copy(kind = SymmetryKind.RADIAL, arms = 4))
        assertEquals(3, r.size)
        assertTrue(eq(r[0].map(100f, 100f, 110f, 100f), 100f, 110f))
        assertEquals(15, V56Symmetry.transforms(v.copy(kind = SymmetryKind.RADIAL, arms = 40)).size)
    }

    @Test fun copiesAndAxes() {
        val s = Stroke("s", listOf(StrokePoint(110f, 100f), StrokePoint(120f, 90f)), "#000000", BrushType.INK_PEN, 4f, 1f, 3)
        var n = 0
        val c = V56Symmetry.copies(s, SymmetrySpec(SymmetryKind.RADIAL, 6, 100f, 100f)) { "c${n++}" }
        assertEquals(5, c.size)
        assertEquals(listOf("c0", "c1", "c2", "c3", "c4"), c.map { it.id })
        assertTrue(c.all { it.points.size == 2 && it.colorHex == s.colorHex })
        assertEquals(1, V56Symmetry.axes(SymmetrySpec(SymmetryKind.VERTICAL, 2, 50f, 50f), 100f).size)
        assertEquals(2, V56Symmetry.axes(SymmetrySpec(SymmetryKind.QUAD, 2, 50f, 50f), 100f).size)
        val a = V56Symmetry.axes(SymmetrySpec(SymmetryKind.RADIAL, 6, 50f, 50f), 100f)
        assertEquals(6, a.size); assertEquals(50f, a[0][0], 0.01f)
        val st = V56Symmetry.start(SymmetryKind.RADIAL, 800, 600)
        assertEquals(6, st.arms); assertEquals(400f, st.cx); assertEquals(300f, st.cy)
    }

    @Test fun pattern() {
        val s = Stroke("s", listOf(StrokePoint(0f, 0f), StrokePoint(80f, 80f)), "#000000", BrushType.INK_PEN, 4f, 1f, 0)
        val b = floatArrayOf(0f, 0f, 80f, 80f)
        assertEquals(35, V56Symmetry.patternCount(b, 500, 500, 20f))
        var n = 0
        val out = V56Symmetry.pattern(listOf(s), b, 500, 500, 20f) { "p${n++}" }
        assertEquals(35, out.size)
        assertEquals(1, out.map { it.groupId }.toSet().size)
        assertEquals(10, V56Symmetry.pattern(listOf(s), b, 5000, 5000, 0f, maxCopies = 10) { "x" }.size)
        assertTrue(V56Symmetry.pattern(emptyList(), b, 500, 500, 0f) { "x" }.isEmpty())
    }
}

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56SymmetryUiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, strokes: List<Stroke> = emptyList()) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking {
            val c = repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.ART)
            if (strokes.isNotEmpty()) repo().saveStrokes(c.localId, listOf(strokes))
        }
        waitForText(name)
        compose.onAllNodes(hasText(name))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    @Test
    fun drawingWithSymmetryAddsCopies() {
        open("Simetri")
        compose.runOnIdle { vm().setSymmetry(SymmetryKind.QUAD) }
        waitFor { vm().canvas.value?.guide?.symmetry?.kind == SymmetryKind.QUAD }
        assertEquals(500f, vm().canvas.value!!.guide.symmetry!!.cx)
        compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        waitFor { exists("v56_sym_bar") }
        compose.runOnIdle { vm().onStrokeFinished(listOf(StrokePoint(600f, 400f), StrokePoint(700f, 300f))) }
        waitFor { vm().canvas.value!!.strokes.size == 4 }
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value!!.strokes.isEmpty() }
        compose.runOnIdle { vm().setSymmetry(SymmetryKind.RADIAL) }
        waitFor { vm().canvas.value?.guide?.symmetry?.arms == 6 }
        compose.runOnIdle { vm().setSymmetry(null) }
        waitFor { vm().canvas.value?.guide?.symmetry == null }
        assertNull(vm().canvas.value!!.guide.symmetry)
    }

    @Test
    fun patternTilesSelection() {
        open("Desen", listOf(Stroke("a", listOf(StrokePoint(10f, 10f), StrokePoint(90f, 90f)), "#000000", BrushType.INK_PEN, 4f, 1f, 0)))
        waitFor { vm().canvas.value?.strokes?.size == 1 }
        compose.runOnIdle { vm().setMode(EditMode.SELECT); vm().selectAll() }
        waitFor { vm().selectedIds.value.isNotEmpty() }
        val count = vm().patternCount(100f)
        assertTrue(count > 0)
        compose.runOnIdle { vm().makePattern(100f) }
        waitFor { vm().canvas.value!!.strokes.size == count + 1 }
        assertEquals(1, vm().canvas.value!!.strokes.map { it.groupId }.toSet().size)
    }
}
