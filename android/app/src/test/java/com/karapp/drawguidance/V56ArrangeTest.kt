package com.karapp.drawguidance

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import com.karapp.drawguidance.data.model.TransformKind
import com.karapp.drawguidance.data.model.TransformOps
import com.karapp.drawguidance.data.model.TransformState
import com.karapp.drawguidance.data.model.V56Arrange
import com.karapp.drawguidance.data.model.V56Flags
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import kotlin.math.hypot

/** v56 aşama 6A saf kurallar: kaydır/eğ, referans noktası, her birini dönüştür, tekrarla, hizala/dağıt, efektler. */
class V56ArrangeRulesTest {
    private fun box(id: String, l: Float, t: Float, r: Float, b: Float, group: String? = null) =
        Stroke(id, listOf(StrokePoint(l, t), StrokePoint(r, t), StrokePoint(r, b), StrokePoint(l, b)), "#000000", BrushType.INK_PEN, 4f, 1f, 0, groupId = group)

    @Test fun shearKeepsParallelogramAndIsAffine() {
        val s = TransformOps.setKind(TransformState.start(0f, 0f, 100f, 50f), TransformKind.SHEAR)
        val sh = TransformOps.dragEdge(s, 0, 30f, 0f)
        assertEquals(30f, sh.quad[0].x, 1e-3f); assertEquals(0f, sh.quad[3].x, 1e-3f)
        val rel = TransformOps.relative(sh)
        assertNotNull(rel)
        assertEquals(-0.6f, rel!!.b, 1e-3f)
        // Köşe: paralelkenar kendi eksenlerinde ölçeklenir
        val sc = TransformOps.dragCorner(sh, 2, 200f, 100f)
        assertNotNull(TransformOps.relative(sc))
        // Bozulmuş (perspektif) dörtgenin afin karşılığı yok
        assertNull(TransformOps.relative(TransformOps.dragCorner(TransformOps.setKind(s, TransformKind.DISTORT), 2, 150f, 90f)))
    }

    @Test fun pivotRotationKeepsPivotFixed() {
        val s = TransformState.start(0f, 0f, 100f, 100f)
        val tl = TransformOps.anchorPoint(s.quad, 0)
        assertEquals(0f to 0f, tl)
        assertEquals(50f to 50f, TransformOps.anchorPoint(s.quad, 4))
        val r = TransformOps.rotate(s, 90f, tl)
        assertEquals(0f, r.quad[0].x, 1e-3f); assertEquals(0f, r.quad[0].y, 1e-3f)
        val f = TransformOps.flip(s, true, 100f to 0f)
        assertEquals(200f, f.quad[0].x, 1e-3f)
    }

    @Test fun transformEachScalesAroundOwnCenters() {
        val strokes = listOf(box("a", 0f, 0f, 10f, 10f), box("b", 100f, 0f, 110f, 10f))
        val s = TransformState.start(0f, 0f, 110f, 10f, TransformKind.FREE)
        val twice = s.copy(quad = s.box.map { StrokePoint((it.x - 55f) * 2f + 55f, (it.y - 5f) * 2f + 5f) })
        val out = TransformOps.applyEach(strokes, setOf("a", "b"), twice).strokes
        // Her kutu kendi merkezinde iki kat
        assertEquals(-5f, out[0].points[0].x, 1e-3f); assertEquals(15f, out[0].points[1].x, 1e-3f)
        assertEquals(95f, out[1].points[0].x, 1e-3f); assertEquals(115f, out[1].points[1].x, 1e-3f)
        // Tekrar: aynı göreli dönüşüm
        val rel = TransformOps.relative(twice)!!
        val again = TransformOps.applyRelative(strokes, setOf("a"), rel, each = false)
        assertEquals(-5f, again[0].points[0].x, 1e-3f)
        assertEquals(strokes[1], again[1])
    }

    @Test fun alignAndDistributeCoordinates() {
        val strokes = listOf(box("a", 0f, 0f, 10f, 10f), box("b", 30f, 20f, 50f, 30f), box("c", 90f, 5f, 100f, 15f, group = "g"), box("d", 95f, 40f, 100f, 50f, group = "g"))
        val units = V56Arrange.units(strokes, setOf("a", "b", "c", "d"))
        assertEquals(3, units.size)
        val left = V56Arrange.applyOffsets(strokes, units, V56Arrange.align(units, V56Arrange.Align.LEFT, V56Arrange.Ref.SELECTION, 500f, 500f))
        assertTrue(left.all { s -> s.points.minOf { it.x } >= 0f })
        assertEquals(0f, left[1].points.minOf { it.x }, 1e-3f)
        // Grup birlikte: c ve d aynı kayma
        assertEquals(left[2].points[0].x - strokes[2].points[0].x, left[3].points[0].x - strokes[3].points[0].x, 1e-3f)
        val bottomPaper = V56Arrange.align(units, V56Arrange.Align.BOTTOM, V56Arrange.Ref.PAPER, 500f, 500f)
        assertEquals(490f, bottomPaper[0].second, 1e-3f)
        val key = V56Arrange.align(units, V56Arrange.Align.TOP, V56Arrange.Ref.KEY, 500f, 500f, keyId = "b")
        assertEquals(0f to 0f, key[1]); assertEquals(20f, key[0].second, 1e-3f)
        // Dağıt: merkezler eşit aralık (5, ?, 95) → orta 50
        val dist = V56Arrange.distribute(units, V56Arrange.Distribute.HORIZONTAL)
        assertEquals(50f - 40f, dist[1].first, 1e-3f)
        assertEquals(0f to 0f, dist[0])
        // Boşlukları eşitle: genişlikler 10, 20, 10 → toplam boşluk 60 → 30
        val sp = V56Arrange.distribute(units, V56Arrange.Distribute.SPACE_H)
        assertEquals(40f - 30f, sp[1].first, 1e-3f)
        assertTrue(V56Arrange.distribute(units.take(2), V56Arrange.Distribute.VERTICAL).all { it == 0f to 0f })
    }

    @Test fun vectorEffectsDensifyAndMove() {
        val sq = Stroke(
            "s", listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f), StrokePoint(100f, 100f), StrokePoint(0f, 100f)), "#000000", BrushType.SHAPE, 2f, 1f, 0,
            shape = ShapeSpec("rect", listOf(SubPath(4, closed = true))),
        )
        val pucker = V56Arrange.effect(listOf(sq), setOf("s"), V56Arrange.Effect.PUCKER, 0.3f)[0]
        assertTrue(pucker.points.size > 4)
        assertEquals(pucker.points.size, pucker.shape!!.subpaths[0].count)
        // Köşe dışa (merkezden uzaklaşır), kenar ortası içe
        assertTrue(hypot(pucker.points[0].x - 50f, pucker.points[0].y - 50f) > hypot(50f, 50f))
        val mid = pucker.points.minByOrNull { hypot(it.x - 50f, it.y - 0f) }!!
        assertTrue(mid.y > 0f)
        val bloat = V56Arrange.effect(listOf(sq), setOf("s"), V56Arrange.Effect.BLOAT, 0.3f)[0]
        assertTrue(hypot(bloat.points[0].x - 50f, bloat.points[0].y - 50f) < hypot(50f, 50f))
        val tw = V56Arrange.effect(listOf(sq), setOf("s"), V56Arrange.Effect.TWIRL, 90f)[0]
        assertTrue(tw.points != sq.points)
        // Resim ve yazıya efekt yok
        val img = sq.copy(id = "i", brushType = BrushType.IMAGE, shape = null)
        assertEquals(img, V56Arrange.effect(listOf(img), setOf("i"), V56Arrange.Effect.TWIRL, 90f)[0])
        assertFalse(V56Flags.enabled(V56Flags.Feature.ADV_EFFECTS, CanvasKind.SIMPLE_ART))
    }
}

/** v56 aşama 6A arayüz: Nesne menüsü hizalama, efekt önizleme/iptal, dönüşümü tekrarla. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56ArrangeUiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openWith(name: String, strokes: List<Stroke>) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking {
            val c = repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.ART)
            repo().saveStrokes(c.localId, listOf(strokes))
        }
        waitForText(name)
        compose.onAllNodes(hasText(name))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name && vm().canvas.value?.strokes?.size == strokes.size }
    }

    private fun tap(tag: String) {
        waitFor { exists(tag) }
        runCatching { compose.onNodeWithTag(tag).performScrollTo() }
        compose.onNodeWithTag(tag).performClick()
    }

    private fun line(id: String, x: Float, y: Float) =
        Stroke(id, listOf(StrokePoint(x, y), StrokePoint(x + 40f, y + 20f)), "#000000", BrushType.INK_PEN, 6f, 1f, 0)

    @Test
    fun objectMenuAlignsAndEffectCancelLeavesDocument() {
        openWith("Nesne", listOf(line("a", 100f, 100f), line("b", 300f, 200f)))
        compose.runOnIdle { vm().setMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.SELECT); vm().selectAll() }
        waitFor { exists("tool_row_context") }
        val nesne = androidx.compose.ui.test.hasContentDescription("Nesne") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("tool_row_context"))
        waitFor { compose.onAllNodes(nesne).fetchSemanticsNodes().isNotEmpty() }
        runCatching { compose.onNode(nesne).performScrollTo() }
        compose.onNode(nesne).performClick()
        waitFor { exists("v56_object_menu") }
        tap("v56_align_left")
        waitFor { vm().canvas.value!!.strokes.all { s -> s.points.minOf { it.x } == 100f } }
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value!!.strokes[1].points[0].x == 300f }
        val before = vm().canvas.value!!.strokes
        tap("v56_fx_twirl")
        waitFor { exists("v56_fx_dialog") }
        compose.runOnIdle { vm().cancelEffect() }
        waitFor { vm().canvas.value!!.strokes == before }
    }

    @Test
    fun repeatTransformAppliesLastAffine() {
        openWith("Tekrar", listOf(line("a", 100f, 100f)))
        compose.runOnIdle {
            vm().selectAll(); vm().openTransform(TransformKind.FREE)
            vm().transform.translate(50f, 0f)
            vm().transform.done()
        }
        waitFor { vm().canvas.value!!.strokes[0].points[0].x == 150f }
        compose.runOnIdle { assertTrue(vm().repeatTransform(false)) }
        waitFor { vm().canvas.value!!.strokes[0].points[0].x == 200f }
        compose.runOnIdle { assertTrue(vm().repeatTransform(true)) }
        waitFor { vm().canvas.value!!.strokes.size == 2 && vm().canvas.value!!.strokes[1].points[0].x == 250f }
    }
}
