package com.karapp.drawguidance

import androidx.compose.ui.test.performClick
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.EffectKind
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import com.karapp.drawguidance.data.model.V56Bool
import com.karapp.drawguidance.data.model.V56PathOps
import com.karapp.drawguidance.data.model.V56PathOps.PathOp
import com.karapp.drawguidance.data.model.VectorEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** v56 aşama 9C: yol işlemleri, bıçak, konturu yola çevir, efektler (iOS V56PathOpsTests ile aynı). */
class V56PathOpsTest {
    private var n = 0
    private fun id() = "n${n++}"
    private fun sq(id: String, x: Float, y: Float, s: Float, fill: String = "#FF0000") = Stroke(
        id, listOf(StrokePoint(x, y), StrokePoint(x + s, y), StrokePoint(x + s, y + s), StrokePoint(x, y + s)), fill, BrushType.SHAPE, 2f, 1f, 0,
        shape = ShapeSpec("rect", listOf(SubPath(4, closed = true)), ShapeStyle("#000000", 2f, fill)),
    )
    private fun ring(x: Double, y: Double, s: Double) = listOf(V56Bool.P(x, y), V56Bool.P(x + s, y), V56Bool.P(x + s, y + s), V56Bool.P(x, y + s))
    private fun absArea(r: List<List<V56Bool.P>>) = abs(r.sumOf { V56Bool.area(it) })

    @Test fun booleanAreas() {
        val a = listOf(ring(0.0, 0.0, 10.0)); val b = listOf(ring(5.0, 5.0, 10.0))
        assertEquals(175.0, absArea(V56Bool.op(a, b, V56Bool.Op.UNION)), 1e-6)
        assertEquals(25.0, absArea(V56Bool.op(a, b, V56Bool.Op.INTERSECT)), 1e-6)
        assertEquals(75.0, absArea(V56Bool.op(a, b, V56Bool.Op.SUBTRACT)), 1e-6)
        assertEquals(150.0, absArea(V56Bool.op(a, b, V56Bool.Op.EXCLUDE)), 1e-6)
        // Kenar paylaşan kareler tek dikdörtgen olur
        val u = V56Bool.op(a, listOf(ring(10.0, 0.0, 10.0)), V56Bool.Op.UNION)
        assertEquals(1, u.size); assertEquals(4, u[0].size); assertEquals(200.0, absArea(u), 1e-6)
        // Delik: 400 - 100
        val hole = V56Bool.op(listOf(ring(0.0, 0.0, 20.0)), listOf(ring(5.0, 5.0, 10.0)), V56Bool.Op.SUBTRACT)
        assertEquals(2, hole.size); assertEquals(300.0, absArea(hole), 1e-6)
        // Ayrık şekillerin kesişimi boş
        assertTrue(V56Bool.op(a, listOf(ring(50.0, 50.0, 5.0)), V56Bool.Op.INTERSECT).isEmpty())
    }

    @Test fun pathOpsOnStrokes() {
        val strokes = listOf(sq("a", 0f, 0f, 10f, "#FF0000"), sq("b", 5f, 5f, 10f, "#0000FF"))
        val ids = setOf("a", "b")
        val unite = V56PathOps.apply(strokes, ids, PathOp.UNITE, ::id)!!
        assertEquals(1, unite.size); assertEquals("#0000FF", unite[0].shape!!.style.fillColorHex)
        assertEquals(175.0, absArea(V56PathOps.rings(unite[0])), 1e-3)
        val minus = V56PathOps.apply(strokes, ids, PathOp.MINUS_FRONT, ::id)!!
        assertEquals(75.0, absArea(V56PathOps.rings(minus[0])), 1e-3)
        assertEquals(3, V56PathOps.apply(strokes, ids, PathOp.DIVIDE, ::id)!!.size)
        val trim = V56PathOps.apply(strokes, ids, PathOp.TRIM, ::id)!!
        assertEquals(2, trim.size)
        assertEquals(75.0, absArea(V56PathOps.rings(trim[0])), 1e-3)
        val crop = V56PathOps.apply(strokes, ids, PathOp.CROP, ::id)!!
        assertEquals(1, crop.size); assertEquals(25.0, absArea(V56PathOps.rings(crop[0])), 1e-3)
        val merge = V56PathOps.apply(listOf(sq("a", 0f, 0f, 10f), sq("b", 5f, 5f, 10f)), ids, PathOp.MERGE, ::id)!!
        assertEquals(1, merge.size)
        val outline = V56PathOps.apply(strokes, ids, PathOp.OUTLINE, ::id)!!
        assertEquals(3, outline.size); assertTrue(outline.all { it.shape!!.style.fillColorHex == null })
        // Şekil oluşturucu: kesişimi solla birleştir, sağdakini sil
        val regions = V56PathOps.builderRegions(strokes)
        val inter = V56PathOps.regionAt(regions, 7f, 7f); val left = V56PathOps.regionAt(regions, 2f, 2f); val right = V56PathOps.regionAt(regions, 13f, 13f)
        val built = V56PathOps.build(strokes, ids, listOf(left, inter), setOf(right), ::id)!!
        assertEquals(1, built.size); assertEquals(100.0, absArea(V56PathOps.rings(built[0])), 1e-3)
    }

    @Test fun knifeOutlineSeparateReverse() {
        val cut = V56PathOps.knife(listOf(sq("a", 0f, 0f, 10f)), emptySet(), listOf(StrokePoint(5f, -5f), StrokePoint(5f, 15f)), ::id)!!
        assertEquals(2, cut.size)
        cut.forEach { assertEquals(50.0, absArea(V56PathOps.rings(it)), 1.0) }
        val line = Stroke("l", listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f)), "#00FF00", BrushType.INK_PEN, 10f, 1f, 0)
        val out = V56PathOps.outlineStroke(listOf(line), setOf("l"), ::id)!!
        assertEquals(1, out.size)
        assertEquals("#00FF00", out[0].shape!!.style.fillColorHex)
        assertTrue(absArea(V56PathOps.rings(out[0])) > 1000.0)
        val two = sq("t", 0f, 0f, 10f).let { s -> s.copy(points = s.points + sq("x", 20f, 0f, 5f).points, shape = s.shape!!.copy(subpaths = listOf(SubPath(4, closed = true), SubPath(4, closed = true)))) }
        assertEquals(2, V56PathOps.separate(listOf(two), setOf("t"), ::id)!!.size)
        val rev = V56PathOps.reversePaths(listOf(line), setOf("l"))!!
        assertEquals(100f, rev[0].points[0].x)
    }

    @Test fun effectsAreEditable() {
        val s = sq("a", 0f, 0f, 100f)
        val rough = V56PathOps.withEffects(s, listOf(VectorEffect(EffectKind.ROUGHEN, size = 5f, detail = 10f)))
        assertTrue(rough.points.size > 4)
        assertNotNull(rough.style2?.effectBase)
        assertEquals(rough.points.size, rough.shape!!.subpaths.sumOf { it.count })
        val off = V56PathOps.withEffects(s, listOf(VectorEffect(EffectKind.OFFSET, amount = 10f)))
        assertEquals(120.0 * 120.0, absArea(V56PathOps.rings(off)), 1.0)
        val back = V56PathOps.withEffects(rough, emptyList())
        assertEquals(s.points, back.points)
        assertEquals(null, back.style2)
        val bent = V56PathOps.withEffects(s, listOf(VectorEffect(EffectKind.BEND, amount = 0.5f)))
        assertTrue(bent.points.size > 4)
        assertEquals(V56PathOps.noise(1, 5), V56PathOps.noise(1, 5), 0.0)
        val env = V56PathOps.envelope(listOf(sq("a", 0f, 0f, 10f), sq("e", 0f, 0f, 20f)), setOf("a", "e"))!!
        assertEquals(1, env.size)
        assertEquals(20f, env[0].points.maxOf { it.x }, 0.01f)
    }
}

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56PathOpsUiTest : V33EditorBase() {
    private fun repo() = org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.repository.CanvasRepository>()
    private fun sq(id: String, x: Float, fill: String) = Stroke(
        id, listOf(StrokePoint(x, 100f), StrokePoint(x + 100f, 100f), StrokePoint(x + 100f, 200f), StrokePoint(x, 200f)), fill, BrushType.SHAPE, 2f, 1f, 0,
        shape = ShapeSpec("rect", listOf(SubPath(4, closed = true)), ShapeStyle("#000000", 2f, fill)),
    )

    @Test
    fun pathOpsBuilderKnifeAndEffectsThroughViewModel() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        kotlinx.coroutines.runBlocking {
            val c = repo().createCanvas("Yollar", 1000, 1000, "#FFFFFF", aiEnabled = false, kind = com.karapp.drawguidance.data.model.CanvasKind.ART)
            repo().saveStrokes(c.localId, listOf(listOf(sq("a", 100f, "#FF0000"), sq("b", 150f, "#0000FF"))))
        }
        waitForText("Yollar")
        compose.onAllNodes(androidx.compose.ui.test.hasText("Yollar"))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.strokes?.size == 2 }
        fun selectAll() = compose.runOnIdle { vm().setMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.SELECT); vm().clearSelection(); vm().canvas.value!!.strokes.forEach { vm().toggleElement(it.id) } }
        selectAll()
        // Şekil oluşturucu: soldaki ve ortadaki bölge birleşir, sağdaki silinir
        compose.runOnIdle { assertTrue(vm().startShapeBuilder()) }
        waitFor { exists("v56_builder_bar") }
        compose.runOnIdle { vm().selectAt(120f, 150f, 10f); vm().selectAt(175f, 150f, 10f); vm().deselectAt(230f, 150f, 10f) }
        assertEquals(2, vm().shapeBuilder.value!!.merge.size)
        compose.runOnIdle { assertTrue(vm().applyShapeBuilder()) }
        waitFor { vm().canvas.value!!.strokes.size == 1 }
        assertEquals(10000.0, abs(V56PathOps.rings(vm().canvas.value!!.strokes[0]).sumOf { V56Bool.area(it) }), 1.0)
        // Geri al, birleştir
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value!!.strokes.size == 2 }
        selectAll()
        compose.runOnIdle { assertTrue(vm().pathOp(PathOp.UNITE)) }
        waitFor { vm().canvas.value!!.strokes.size == 1 }
        // Efekt listesi: ötele ve geri kaldır
        val id = vm().canvas.value!!.strokes[0].id
        compose.runOnIdle { vm().clearSelection(); vm().toggleElement(id) }
        val before = vm().canvas.value!!.strokes[0].points
        compose.runOnIdle { vm().applyEffects(listOf(VectorEffect(EffectKind.OFFSET, amount = 10f))) }
        waitFor { vm().canvas.value!!.strokes[0].style2?.effects?.size == 1 }
        compose.runOnIdle { vm().applyEffects(emptyList()) }
        waitFor { vm().canvas.value!!.strokes[0].style2 == null }
        assertEquals(before, vm().canvas.value!!.strokes[0].points)
        // Bıçak: dikey çizgi ikiye böler; çizgi eklenmez
        compose.runOnIdle { vm().startKnife() }
        assertTrue(vm().knife.value)
        compose.runOnIdle { vm().onStrokeFinished(listOf(StrokePoint(170f, 50f), StrokePoint(170f, 300f))) }
        waitFor { vm().canvas.value!!.strokes.size == 2 }
        assertTrue(vm().canvas.value!!.strokes.all { it.brushType == BrushType.SHAPE })
        compose.runOnIdle { vm().stopKnife() }
    }
}
