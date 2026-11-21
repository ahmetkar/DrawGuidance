package com.karapp.drawguidance

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Homography
import com.karapp.drawguidance.data.model.Reflection
import com.karapp.drawguidance.data.model.Snapping
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.data.model.TransformKind
import com.karapp.drawguidance.data.model.TransformOps
import com.karapp.drawguidance.data.model.TransformState
import com.karapp.drawguidance.data.model.WarpMesh
import com.karapp.drawguidance.data.model.WaveSpec
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.state.SnapPrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import kotlin.math.hypot

/** v56 aşama 6 saf kurallar: homografi, ağ, yaslama, çevirme/döndürme, sığdırma, uygulama, yansıma, sıvılaştır. */
class V56TransformRulesTest {
    private fun line(id: String, vararg xy: Float) = Stroke(id, xy.toList().chunked(2).map { StrokePoint(it[0], it[1]) }, "#000000", BrushType.INK_PEN, 10f, 1f, 0)

    private fun near(a: List<StrokePoint>, b: List<StrokePoint>, tol: Float = 1e-3f) =
        a.size == b.size && a.zip(b).all { (p, q) -> hypot(p.x - q.x, p.y - q.y) <= tol }

    @Test fun homographyMapsCornersWithinHalfPixel() {
        val src = floatArrayOf(0f, 0f, 200f, 0f, 200f, 100f, 0f, 100f)
        val dst = floatArrayOf(30f, 12f, 260f, 40f, 220f, 190f, 10f, 140f)
        val h = Homography.solve(src, dst)
        assertNotNull(h)
        for (i in 0 until 4) {
            val (x, y) = Homography.map(h!!, src[2 * i], src[2 * i + 1])
            assertTrue("köşe $i", hypot(x - dst[2 * i], y - dst[2 * i + 1]) <= 0.5f)
        }
        // Dışbükey olmayan dörtgen reddedilir
        assertFalse(Homography.convex(listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f), StrokePoint(10f, 10f), StrokePoint(0f, 100f))))
    }

    @Test fun flatMeshIsIdentityAndCornerMoveIsLocal() {
        val m = WarpMesh.identity(0f, 0f, 300f, 300f, WarpMesh.BASIC)
        listOf(0.1f to 0.2f, 0.5f to 0.5f, 0.93f to 0.4f).forEach { (u, v) ->
            val (x, y) = WarpMesh.eval(m, WarpMesh.BASIC, u, v)
            assertEquals(300f * u, x, 1e-2f); assertEquals(300f * v, y, 1e-2f)
        }
        val moved = WarpMesh.movePoint(m, 0, -40f, -30f)
        val far = WarpMesh.eval(moved, WarpMesh.BASIC, 0.95f, 0.95f)
        assertEquals(285f, far.first, 1e-2f); assertEquals(285f, far.second, 1e-2f)
        val nearCorner = WarpMesh.eval(moved, WarpMesh.BASIC, 0.05f, 0.05f)
        assertTrue(nearCorner.first < 15f - 1f)
        // Örnekleme düz ağı düz bırakır
        assertTrue(near(WarpMesh.resample(m, WarpMesh.BASIC, WarpMesh.ADVANCED), WarpMesh.identity(0f, 0f, 300f, 300f, WarpMesh.ADVANCED), 1e-2f))
    }

    @Test fun snappingStepsDistanceAndSpeed() {
        assertEquals(15f, Snapping.angle(13f), 0f)
        assertEquals(45f, Snapping.angle(47f, tolerance = 3f), 0f)
        assertEquals(40f, Snapping.angle(40f, tolerance = 3f), 0f)
        assertEquals(100f, Snapping.nearest(104f, listOf(0f, 100f, 500f), 5f)!!, 0f)
        assertNull(Snapping.nearest(110f, listOf(0f, 100f), 5f))
        assertTrue(Snapping.active(800f, 1500f))
        assertFalse("hızlı sürüklemede yaslama yok", Snapping.active(3000f, 1500f))
        assertTrue("eşik 0: her zaman", Snapping.active(9000f, 0f))
        val r = Snapping.snapBox(97f, 10f, 147f, 60f, listOf(100f), listOf(500f), 5f)
        assertEquals(3f, r.dx, 1e-4f); assertEquals(0f, r.dy, 0f); assertEquals(100f, r.guideX!!, 0f); assertNull(r.guideY)
        // Ayar metni geri okunur
        val p = SnapPrefs(magnets = false, snap = true, distance = 22f, speed = 900f)
        assertEquals(p, SnapPrefs.decode(p.encode()))
        assertEquals(SnapPrefs(), SnapPrefs.decode("bozuk"))
    }

    @Test fun twoFlipsAndEightEighthTurnsAreIdentity() {
        val s = TransformOps.rotate(TransformState.start(10f, 20f, 210f, 120f), 30f)
        assertTrue(near(TransformOps.flip(TransformOps.flip(s, true), true).quad, s.quad))
        assertTrue(near(TransformOps.flip(TransformOps.flip(s, false), false).quad, s.quad))
        var r = s
        repeat(8) { r = TransformOps.rotate(r, 45f) }
        assertTrue(near(r.quad, s.quad, 1e-2f))
        val h = TransformOps.flip(TransformState.start(0f, 0f, 100f, 50f), true)
        assertEquals(100f, h.quad[0].x, 1e-3f)
    }

    @Test fun fitKeepsRatioAndCenters() {
        val s = TransformState.start(10f, 10f, 110f, 60f)
        val f = TransformOps.fit(TransformOps.rotate(s, 20f), 0f, 0f, 1000f, 1000f)
        assertEquals(1000f, TransformOps.widthOf(f.quad), 1e-2f)
        assertEquals(500f, TransformOps.heightOf(f.quad), 1e-2f)
        assertEquals(0f, TransformOps.angleOf(f.quad), 1e-3f)
        assertEquals(250f, f.quad[0].y, 1e-2f)
        assertTrue(TransformOps.reset(f).isIdentity)
    }

    @Test fun uniformCornerKeepsRatioAndDistortSkipsText() {
        val s = TransformState.start(0f, 0f, 100f, 50f)
        val u = TransformOps.dragCorner(s, 2, 300f, 60f)
        assertEquals(2f, TransformOps.widthOf(u.quad) / TransformOps.heightOf(u.quad), 1e-3f)
        val strokes = listOf(
            line("a", 0f, 0f, 100f, 50f),
            Stroke("t", listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f), StrokePoint(100f, 50f), StrokePoint(0f, 50f)), "#000000", BrushType.TEXT, 12f, 1f, 1, text = TextSpec("x")),
        )
        val d = TransformOps.dragCorner(TransformOps.setKind(s, TransformKind.DISTORT), 2, 140f, 90f)
        val out = TransformOps.apply(strokes, setOf("a", "t"), d)
        assertEquals(setOf("t"), out.skipped)
        assertEquals(strokes[1], out.strokes[1])
        assertEquals(140f, out.strokes[0].points[1].x, 0.5f); assertEquals(90f, out.strokes[0].points[1].y, 0.5f)
        // Taşıma: noktalar kayar, kalınlık aynı
        val moved = TransformOps.apply(strokes, setOf("a"), TransformOps.translate(s, 5f, 7f)).strokes[0]
        assertEquals(5f, moved.points[0].x, 1e-3f); assertEquals(7f, moved.points[0].y, 1e-3f); assertEquals(10f, moved.sizePx, 1e-3f)
    }

    @Test fun reflectionMirrorsBelowWithWaveAndFade() {
        val strokes = listOf(line("a", 0f, 0f, 100f, 100f))
        var k = 0
        val out = Reflection.create(strokes, setOf("a"), 0f, 0f, 100f, 100f, 4f, { "r${k++}" })
        assertEquals(2, out.size)
        val r = out[1]
        assertEquals("r0", r.id)
        assertEquals(204f, r.points[0].y, 1e-3f)
        assertEquals(104f, r.points[1].y, 1e-3f)
        val w = r.warp!!.wave!!
        assertEquals(104f, w.top, 0f); assertEquals(204f, w.bottom, 0f)
        assertEquals(0f, Reflection.offset(w, 50f, w.top).first, 1e-4f)
        assertEquals(w.fadeFrom, Reflection.fade(w, w.top), 1e-4f)
        assertEquals(w.fadeTo, Reflection.fade(w, w.bottom), 1e-4f)
        assertEquals(0f to 0f, Reflection.offset(WaveSpec(amplitude = 0f, wavelength = 10f), 3f, 4f))
        assertEquals(strokes, Reflection.create(strokes, emptySet(), 0f, 0f, 1f, 1f, 4f, { "x" }))
    }

    @Test fun liquifyIsLocal() {
        val m = WarpMesh.identity(0f, 0f, 320f, 320f, WarpMesh.LIQUIFY)
        val pushed = WarpMesh.liquify(m, WarpMesh.Liquify.PUSH, 160f, 160f, 30f, 0f, 60f, 1f)
        val center = pushed[8 * WarpMesh.LIQUIFY + 8]
        assertEquals(190f, center.x, 1e-3f)
        assertEquals(m[0], pushed[0])
        val bloat = WarpMesh.liquify(m, WarpMesh.Liquify.BLOAT, 160f, 160f, 0f, 0f, 60f, 1f)
        val pinch = WarpMesh.liquify(m, WarpMesh.Liquify.PINCH, 160f, 160f, 0f, 0f, 60f, 1f)
        val i = 8 * WarpMesh.LIQUIFY + 9 // merkezin sağındaki nokta
        assertTrue(bloat[i].x > m[i].x && pinch[i].x < m[i].x)
        val twirl = WarpMesh.liquify(m, WarpMesh.Liquify.TWIRL, 160f, 160f, 0f, 0f, 60f, 1f)
        assertNotEquals(m[i].y, twirl[i].y)
    }
}

/** v56 aşama 6 arayüz: panel kipleri, Bitti tek geri alma adımı, Sıfırla/İptal belgeyi değiştirmez, yansıma. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56TransformUiTest : V33EditorBase() {
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

    private val base = listOf(Stroke("a", listOf(StrokePoint(100f, 100f), StrokePoint(300f, 200f)), "#000000", BrushType.INK_PEN, 8f, 1f, 0))

    @Test
    fun panelModesDoneIsOneUndoAndCancelLeavesDocument() {
        openWith("Dönüşüm", base)
        val before = vm().canvas.value!!.strokes
        compose.runOnIdle { vm().selectAll(); assertTrue(vm().openTransform()) }
        waitFor { exists("v56_transform_panel") }
        listOf("v56_tf_free", "v56_tf_distort", "v56_tf_warp").forEach { tap(it) }
        waitFor { exists("v56_tf_advanced_mesh") }
        tap("v56_tf_uniform")
        // Sıfırla ve İptal: belge aynı
        tap("v56_tf_flip_h")
        tap("v56_tf_reset")
        compose.runOnIdle { assertTrue(vm().transform.state.value!!.isIdentity) }
        tap("v56_tf_flip_v")
        tap("v56_tf_cancel")
        waitFor { !exists("v56_transform_panel") }
        assertEquals(before, vm().canvas.value!!.strokes)
        // Bitti: tek geri alma adımı
        compose.runOnIdle { vm().selectAll(); vm().openTransform(); vm().transform.flip(true) }
        waitFor { exists("v56_transform_panel") }
        tap("v56_tf_done")
        waitFor { vm().canvas.value!!.strokes[0].points[0].x == 300f }
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value!!.strokes == before }
        // Yaslama ayarları penceresi
        compose.runOnIdle { vm().selectAll(); vm().openTransform() }
        waitFor { exists("v56_tf_snapping") }
        tap("v56_tf_snapping")
        waitFor { exists("v56_tf_snap_dialog") }
    }

    @Test
    fun reflectionAddsMirroredCopyInOneStep() {
        openWith("Yansıma", base)
        compose.runOnIdle { vm().selectAll(); assertTrue(vm().reflectSelection()) }
        waitFor { vm().canvas.value!!.strokes.size == 2 }
        val r = vm().canvas.value!!.strokes[1]
        assertNotNull(r.warp?.wave)
        assertTrue(r.points.all { it.y > 200f })
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value!!.strokes.size == 1 }
    }
}
