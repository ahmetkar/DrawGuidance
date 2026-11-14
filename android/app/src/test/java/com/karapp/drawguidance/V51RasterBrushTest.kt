package com.karapp.drawguidance

import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.FxParams
import com.karapp.drawguidance.data.model.PenDynamics
import com.karapp.drawguidance.data.model.TipParams
import com.karapp.drawguidance.ui.drawing.PenModel
import com.karapp.drawguidance.ui.drawing.RasterBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v51 piksel motoru: boya fırçaları boya bırakır, yardımcı fırçalar alttaki içeriği işler. */
class V51RasterBrushTest {
    /** 80×40 bölge: sol yarı kırmızı, sağ yarı mavi (ön çarpımlı). */
    private fun base(): RasterBrush.Patch {
        val p = RasterBrush.Patch(0f, 0f, 1f, 80, 40)
        for (y in 0 until 40) for (x in 0 until 80) {
            val o = (y * 80 + x) * 4
            if (x < 40) p.rgba[o] = 1f else p.rgba[o + 2] = 1f
            p.rgba[o + 3] = 1f
        }
        return p
    }

    private fun line() = floatArrayOf(10f, 70f) to floatArrayOf(20f, 20f)

    private fun render(type: BrushType, params: BrushParams? = null, patch: RasterBrush.Patch = base()): RasterBrush.Result {
        val (xs, ys) = line()
        return RasterBrush.render(patch, type, 0xFF00FF00.toInt(), 16f, 1f, 1f, 1f, 7, xs, ys, 2, null, null, null, null, null, params)
    }

    @Test fun paintBrushesLeavePaint() {
        for (t in listOf(BrushType.ACRYLIC, BrushType.OIL_PAINT, BrushType.CLOUD_SMOKE)) {
            val r = render(t, patch = RasterBrush.Patch(0f, 0f, 1f, 80, 40))
            val row = (20 until 60).map { 20 * 80 + it }
            assertTrue("$t maske", row.any { r.mask[it] > 0f })
            assertTrue("$t yeşil", row.maxOf { r.color[it * 4 + 1] } > 0.1f)
        }
    }

    @Test fun blurMixesTheEdge() {
        val r = render(BrushType.FX_BLUR)
        val i = (20 * 80 + 40) * 4
        assertTrue(r.mask[20 * 80 + 40] > 0f)
        assertTrue("kırmızı ve mavi karışır", r.color[i] > 0.05f && r.color[i + 2] > 0.05f)
    }

    @Test fun dodgeBrightensBurnDarkens() {
        val gray = RasterBrush.Patch(0f, 0f, 1f, 80, 40).apply { for (i in 0 until 80 * 40) { rgba[i * 4] = 0.5f; rgba[i * 4 + 1] = 0.5f; rgba[i * 4 + 2] = 0.5f; rgba[i * 4 + 3] = 1f } }
        val i = (20 * 80 + 40) * 4
        assertTrue(render(BrushType.FX_DODGE, patch = gray).color[i] > 0.55f)
        assertTrue(render(BrushType.FX_BURN, patch = gray).color[i] < 0.45f)
    }

    @Test fun spongeDesaturates() {
        val r = render(BrushType.FX_SPONGE, BrushParams(fx = FxParams(mode = 0)))
        val i = (20 * 80 + 20) * 4
        assertTrue("kırmızı griye yaklaşır", r.color[i + 1] > 0.1f)
    }

    @Test fun cloneCopiesFromSourceOffset() {
        val r = render(BrushType.FX_CLONE, BrushParams(fx = FxParams(srcDx = 30f, srcDy = 0f)))
        val i = (20 * 80 + 20) * 4
        assertEquals("x=20 noktasına x=50'deki mavi gelir", 1f, r.color[i + 2], 0.01f)
    }

    @Test fun effectiveTipFallsBackToDefaults() {
        val t = RasterBrush.effectiveTip(BrushType.OIL_PAINT, BrushParams(tip = TipParams(hardness = 0.1f)))
        assertEquals(0.1f, t.hardness!!, 0f)
        assertEquals(RasterBrush.defaults(BrushType.OIL_PAINT).wetness, t.wetness)
    }

    @Test fun penModelOnlyWhenConfigured() {
        assertNull(PenModel.of(null))
        assertNull(PenModel.of(BrushParams(tip = TipParams(hardness = 0.5f))))
        val pm = assertNotNullAnd(PenModel.of(BrushParams(tip = TipParams(minSize = 0.5f, maxSize = 2f))))
        val d = PenModel.PointData(floatArrayOf(0f, 10f), floatArrayOf(0f, 0f), 2, null, null, null, null, null, 10f)
        assertEquals(2f, pm.size(1f, d, 0), 1e-4f)
        assertEquals(1f, pm.size(0f, d, 0), 1e-4f)
        val barrel = assertNotNullAnd(PenModel.of(BrushParams(pen = PenDynamics(barrel = 1f))))
        val b = PenModel.PointData(floatArrayOf(0f), floatArrayOf(0f), 1, null, null, null, null, floatArrayOf(1f), 10f)
        assertEquals(2f, barrel.size(1f, b, 0), 1e-4f)
    }

    private fun <T> assertNotNullAnd(v: T?): T { assertNotNull(v); return v!! }
}
