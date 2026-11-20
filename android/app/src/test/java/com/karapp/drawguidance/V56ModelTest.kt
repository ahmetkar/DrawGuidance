package com.karapp.drawguidance

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.local.V11Flatten
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ClipMask
import com.karapp.drawguidance.data.model.DocExtras
import com.karapp.drawguidance.data.model.GrainSpec
import com.karapp.drawguidance.data.model.ImageWarp
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.LayerBlend
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SymmetryKind
import com.karapp.drawguidance.data.model.SymmetrySpec
import com.karapp.drawguidance.data.model.V56Flags
import com.karapp.drawguidance.data.model.WaveSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v56 aşama 1: V11 içerik biçimi, düzleştirme ve mod kuralı. */
class V56ModelTest {
    private fun pts(vararg xy: Float) = xy.toList().chunked(2).map { StrokePoint(it[0], it[1]) }
    private fun stroke(id: String, type: BrushType = BrushType.PENCIL, layer: String? = null) =
        Stroke(id, pts(1f, 2f, 30f, 40f, 50f, 10f), "#FF0000", type, 6f, 0.8f, 0, layerId = layer)

    @Test
    fun plainDocumentStaysOldVersion() {
        val bytes = ContentCodec.encodePages(listOf(listOf(stroke("a"))))
        assertTrue("V11 gerekmiyorsa eski sürüm yazılır", bytes[2] < 11)
    }

    @Test
    fun v11RoundTripKeepsEverything() {
        val layers = listOf(Layer.Default, Layer("l2", "Gölge", opacity = 0.5f, blend = LayerBlend.MULTIPLY, alphaLock = true, clipToBelow = true))
        val extras = DocExtras(SymmetrySpec(SymmetryKind.RADIAL, arms = 6, cx = 100f, cy = 120f, angle = 15f))
        val masked = stroke("m", layer = "l2").copy(mask = ClipMask(pts(0f, 0f, 10f, 0f, 10f, 10f), feather = 3f, inverted = true))
        val image = Stroke("i", pts(0f, 0f, 100f, 0f, 100f, 50f, 0f, 50f), "#000000", BrushType.IMAGE, 1f, 1f, 1, assetId = "asset-1",
            warp = ImageWarp(quad = pts(5f, 5f, 95f, 0f, 100f, 55f, 0f, 45f), wave = WaveSpec(4f, 20f, ripple = true, fadeTo = 0.2f)))
        val lasso = stroke("f", BrushType.LASSO_FILL).copy(params = BrushParams(grain = GrainSpec("paper", scale = 2f, strength = 0.5f, moving = false)))
        val bytes = ContentCodec.encodePages(listOf(listOf(masked, image), listOf(lasso)), layers = layers, extras = extras)
        assertEquals(11, bytes[2].toInt())
        val doc = ContentCodec.decodeDocument(bytes)
        assertEquals(layers, doc.layers)
        assertEquals(extras, doc.extras)
        assertEquals(masked, doc.pages[0][0])
        assertEquals(image, doc.pages[0][1])
        assertEquals(lasso, doc.pages[1][0])
        assertTrue(ContentCodec.requiresV11(doc))
    }

    @Test
    fun encodeCacheMatchesUncached() {
        val cache = ContentCodec.EncodeCache()
        val page = listOf(stroke("a"), stroke("b").copy(mask = ClipMask(pts(0f, 0f, 1f, 1f, 2f, 0f))))
        ContentCodec.encodePages(listOf(page.take(1)), cache)
        val cached = ContentCodec.encodePages(listOf(page), cache)
        assertTrue(cached.contentEquals(ContentCodec.encodePages(listOf(page))))
    }

    @Test
    fun flattenProducesOldReadableDocument() {
        val layers = listOf(Layer.Default, Layer("l2", "Yarı saydam", opacity = 0.5f, blend = LayerBlend.SCREEN))
        val image = Stroke("i", pts(0f, 0f, 100f, 0f, 100f, 50f, 0f, 50f), "#000000", BrushType.IMAGE, 1f, 1f, 1,
            warp = ImageWarp(quad = pts(5f, 5f, 95f, 0f, 100f, 55f, 0f, 45f)))
        val content = ContentCodec.encodePages(
            listOf(listOf(stroke("a", layer = "l2"), image, stroke("f", BrushType.LASSO_FILL))), layers = layers,
            extras = DocExtras(SymmetrySpec(SymmetryKind.VERTICAL, cx = 1f, cy = 1f)),
        )
        val (same, notFlat) = V11Flatten.forPeer(content, 11)
        assertFalse(notFlat); assertTrue(same.contentEquals(content))
        val (flat, flattened) = V11Flatten.forPeer(content, 10)
        assertTrue(flattened)
        assertTrue("düzleştirilen içerik V10 ya da eski", flat[2] <= 10)
        val doc = ContentCodec.decodeDocument(flat)
        assertEquals(0.8f * 0.5f, doc.pages[0][0].opacity, 1e-6f)
        assertEquals(pts(5f, 5f, 95f, 0f, 100f, 55f, 0f, 45f), doc.pages[0][1].points)
        assertNull(doc.pages[0][1].warp)
        assertEquals(BrushType.SHAPE, doc.pages[0][2].brushType)
        assertEquals("#FF0000", doc.pages[0][2].shape?.style?.fillColorHex)
        assertTrue(doc.extras.isEmpty)
    }

    @Test
    fun modeRulesMatchPlan() {
        assertTrue(V56Flags.enabled(V56Flags.Feature.QUICK_BRUSH, CanvasKind.ART))
        assertTrue(V56Flags.enabled(V56Flags.Feature.QUICK_BRUSH, CanvasKind.SIMPLE_ART))
        assertFalse(V56Flags.enabled(V56Flags.Feature.QUICK_BRUSH, CanvasKind.COMIC))
        assertFalse(V56Flags.enabled(V56Flags.Feature.QUICK_BRUSH, CanvasKind.NOTES))
        assertEquals(setOf(V56Flags.Feature.SYMMETRY), V56Flags.features(CanvasKind.KIDS))
        assertTrue(V56Flags.enabled(V56Flags.Feature.EXPORT_SVG_PSD_DXF, CanvasKind.NOTES))
        assertFalse(V56Flags.enabled(V56Flags.Feature.EXPORT_SVG_PSD_DXF, CanvasKind.KIDS))
        assertFalse(V56Flags.enabled(V56Flags.Feature.LAYER_BLEND, CanvasKind.NOTES))
        // 2E yalnız gelişmiş çizimde
        assertTrue(V56Flags.enabled(V56Flags.Feature.ADV_PATHFINDER, CanvasKind.ART))
        assertFalse(V56Flags.enabled(V56Flags.Feature.ADV_PATHFINDER, CanvasKind.SIMPLE_ART))
        assertFalse(V56Flags.enabled(V56Flags.Feature.ADV_PATHFINDER, CanvasKind.COMIC))
    }
}
