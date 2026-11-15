package com.karapp.drawguidance.content.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v52 foto düzenleme hattı: geometri, kırpma, ters eşleme (fırça), ışık ve fırçalar. */
class PhotoEditTest {
    private val w = 40
    private val h = 20
    private val src = IntArray(w * h) { i -> val x = i % w; val y = i / w; (0xFF shl 24) or ((x * 6) shl 16) or ((y * 12) shl 8) or 0x40 }

    @Test fun identityKeepsPixels() {
        val (px, ow, oh) = PhotoOps.render(src, w, h, PhotoEdit())
        assertEquals(w, ow); assertEquals(h, oh)
        assertTrue(px.contentEquals(src))
    }

    @Test fun rotationSwapsSizeAndCropShrinks() {
        val (_, rw, rh) = PhotoOps.render(src, w, h, PhotoEdit(rotation = 1))
        assertEquals(h, rw); assertEquals(w, rh)
        val (_, cw, ch) = PhotoOps.render(src, w, h, PhotoEdit(crop = PhotoCrop(0.25f, 0f, 0.75f, 0.5f)))
        assertEquals(20, cw); assertEquals(10, ch)
        val sq = PhotoOps.cropForAspect(w, h, PhotoEdit(), 1f)
        assertEquals(0.5f, sq.right - sq.left, 1e-4f)
    }

    @Test fun toSourceInvertsGeometry() {
        for (q in 0..3) for (fh in listOf(false, true)) {
            val e = PhotoEdit(rotation = q, flipH = fh)
            val (out, ow, oh) = PhotoOps.render(src, w, h, e)
            // Son resimdeki bir pikselin rengi, kaynaktaki karşılığıyla aynı
            val ox = 7; val oy = 3
            val (u, v) = PhotoOps.toSource((ox + 0.5f) / ow, (oy + 0.5f) / oh, w, h, e)
            val sx = (u * w).toInt().coerceIn(0, w - 1); val sy = (v * h).toInt().coerceIn(0, h - 1)
            assertEquals("q=$q flip=$fh", src[sy * w + sx], out[oy * ow + ox])
        }
    }

    @Test fun lightAndBrushChangePixels() {
        val (lit, _, _) = PhotoOps.render(src, w, h, PhotoEdit(light = PhotoLight(exposure = 0.5f)))
        assertTrue(lit.zip(src.toList()).count { (a, b) -> (a shr 16 and 0xFF) > (b shr 16 and 0xFF) } > src.size / 2)
        val stroke = PhotoStroke(PhotoBrush.PAINT, listOf(0.5f, 0.5f, 0.6f, 0.5f), 0.3f, 1f, 0xFFFF0000.toInt())
        val (painted, _, _) = PhotoOps.render(src, w, h, PhotoEdit(strokes = listOf(stroke)))
        assertNotEquals(src[10 * w + 20], painted[10 * w + 20])
        assertEquals(src[0], painted[0])
    }
}
