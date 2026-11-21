package com.karapp.drawguidance

import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.data.billing.FeatureKeys
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Model3DLink
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TransformKind
import com.karapp.drawguidance.data.model.TransformOps
import com.karapp.drawguidance.data.model.TransformState
import com.karapp.drawguidance.ui.screens.canvasedit.state.Model3DController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v56 aşama 10: 3B şeklin açısı, Bozma/Çarpıtma'nın 3B şekle uygulanmaması, emekli Premium anahtarları. */
class V56Model3DTest {
    private fun model(): Model3D = Model3D("m", "Küp", emptyList())

    @Test fun angleIsNormalisedAndClamped() {
        val m = Model3DController.withAngle(model(), -30f, 120f)
        assertEquals(330f, m.camera.yaw, 1e-3f)
        assertEquals(80f, m.camera.pitch, 1e-3f)
        val n = Model3DController.withAngle(model(), 725f, -95f)
        assertEquals(5f, n.camera.yaw, 1e-3f)
        assertEquals(-80f, n.camera.pitch, 1e-3f)
        // Yalnız kamera değişir
        assertEquals(model().copy(camera = n.camera), n)
    }

    @Test fun distortAndWarpSkip3dShapes() {
        val quad = listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f), StrokePoint(100f, 100f), StrokePoint(0f, 100f))
        val shape = Stroke("s", quad, "#000000", BrushType.IMAGE, 1f, 1f, 0, assetId = "a", model = Model3DLink("catalog:cube", "d"))
        val image = Stroke("i", quad, "#000000", BrushType.IMAGE, 1f, 1f, 1, assetId = "b")
        val start = TransformState.start(0f, 0f, 100f, 100f, TransformKind.DISTORT)
        val moved = start.copy(quad = start.quad.mapIndexed { i, p -> if (i == 2) StrokePoint(130f, 140f) else p })
        val r = TransformOps.apply(listOf(shape, image), setOf("s", "i"), moved)
        assertEquals(setOf("s"), r.skipped)
        assertEquals(quad, r.strokes[0].points)
        assertTrue(r.strokes[1].points != quad)
        // Serbest dönüşüm (afin) 3B şekle de uygulanır
        val free = TransformState.start(0f, 0f, 100f, 100f, TransformKind.FREE).let { st -> st.copy(quad = st.quad.map { StrokePoint(it.x + 10f, it.y) }) }
        val f = TransformOps.apply(listOf(shape), setOf("s"), free)
        assertTrue(f.skipped.isEmpty())
        assertEquals(10f, f.strokes[0].points[0].x, 1e-3f)
    }

    @Test fun retired3dKeysAreNotPremium() {
        assertTrue(FeatureKeys.RETIRED.none { it in FeatureKeys.PREMIUM })
        assertTrue(Catalog3D.all.isNotEmpty())
    }
}
