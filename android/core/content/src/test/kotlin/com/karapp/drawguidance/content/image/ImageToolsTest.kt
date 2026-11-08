package com.karapp.drawguidance.content.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * v30 resim araçları: arka plan kaldırma (düz, gradyanlı ve kenara değen konulu resimlerde konu korunur, arka plan
 * saydam olur; iç boşluklar; dokunarak kaldırma ve geri getirme) ve genel efektler (saydamlık korunur, ayarlar
 * beklenen yönde değişir, süreler).
 */
class ImageToolsTest {
    private val W = 400
    private val H = 300

    /** Arka plan (renk ya da gradyan) üstünde kırmızı daire; [ring] verilirse ortası arka plan renginde halka. */
    private fun scene(bg: (Int, Int) -> Int, ring: Boolean = false, touchBottom: Boolean = false): IntArray = IntArray(W * H) { i ->
        val x = i % W; val y = i / W
        val cy = if (touchBottom) H - 60f else H / 2f
        val d = hypot(x - W / 2f, y - cy)
        when {
            ring && d < 40f -> bg(x, y)
            d < 90f -> 0xFFD32F2F.toInt()
            touchBottom && y > H - 60 && kotlin.math.abs(x - W / 2) < 50 -> 0xFF1565C0.toInt()
            else -> bg(x, y)
        }
    }

    private fun alphaAt(px: IntArray, x: Int, y: Int) = px[y * W + x] ushr 24

    @Test fun plainBackgroundIsRemovedSubjectKept() {
        val src = scene({ _, _ -> 0xFFF4F1EA.toInt() })
        val t0 = System.nanoTime()
        val out = BackgroundRemover.remove(src, W, H)
        println("düz arka plan: %.1f ms".format((System.nanoTime() - t0) / 1e6))
        assertEquals(0, alphaAt(out, 5, 5))
        assertEquals(0, alphaAt(out, W - 10, H - 10))
        assertEquals(255, alphaAt(out, W / 2, H / 2))
        assertEquals(255, alphaAt(out, W / 2 + 70, H / 2))
        // Konu rengi kenarda bozulmaz (arka plan rengi ayrıştırılır)
        val edge = out[(H / 2) * W + W / 2 + 89]
        assertTrue((edge shr 16 and 0xFF) > 150 && (edge and 0xFF) < 120)
    }

    @Test fun gradientBackgroundIsFollowed() {
        val src = scene({ x, y -> val v = 170 + (60 * (x + y) / (W + H)); (0xFF shl 24) or (v shl 16) or (v shl 8) or 255 })
        val out = BackgroundRemover.remove(src, W, H)
        val removed = out.count { it ushr 24 == 0 } / out.size.toFloat()
        println("gradyan: kaldırılan %.0f%%".format(removed * 100))
        assertTrue(removed > 0.7f)
        assertEquals(255, alphaAt(out, W / 2, H / 2))
    }

    @Test fun holesAreOptionalAndTapsWork() {
        val src = scene({ _, _ -> 0xFFFFFFFF.toInt() }, ring = true)
        val withHoles = BackgroundRemover.remove(src, W, H, BackgroundOptions(removeHoles = true))
        val noHoles = BackgroundRemover.remove(src, W, H, BackgroundOptions(removeHoles = false))
        assertEquals("iç boşluk kaldırıldı", 0, alphaAt(withHoles, W / 2, H / 2))
        assertEquals("iç boşluk korundu", 255, alphaAt(noHoles, W / 2, H / 2))
        // Dokunarak geri getirme: ortadaki boşluk yeniden görünür
        val kept = BackgroundRemover.remove(src, W, H, BackgroundOptions(keepAt = listOf(0.5f to 0.5f)))
        assertEquals(255, alphaAt(kept, W / 2, H / 2))
        // Otomatik kapalı: yalnız dokunulan kırmızı daire kaldırılır
        val tapped = BackgroundRemover.remove(src, W, H, BackgroundOptions(auto = false, removeAt = listOf((W / 2 + 60f) / W to 0.5f)))
        assertEquals(0, alphaAt(tapped, W / 2 + 60, H / 2))
        assertEquals(255, alphaAt(tapped, 5, 5))
    }

    @Test fun subjectTouchingBottomIsKept() {
        val src = scene({ _, _ -> 0xFFE8F5E9.toInt() }, touchBottom = true)
        val out = BackgroundRemover.remove(src, W, H)
        assertEquals(255, alphaAt(out, W / 2, H - 3))
        assertEquals(0, alphaAt(out, 10, 10))
    }

    @Test fun effectsKeepAlphaAndMoveTheRightWay() {
        val src = IntArray(W * H) { i -> val x = i % W; val y = i / W; if (x < 20) 0 else (0xFF shl 24) or ((x * 255 / W) shl 16) or ((y * 255 / H) shl 8) or (if ((x / 3 + y / 3) % 2 == 0) 40 else 220) }
        fun mean(px: IntArray, shift: Int) = px.filter { it ushr 24 != 0 }.map { it shr shift and 0xFF }.average()
        for (look in ImageLook.entries) {
            val t0 = System.nanoTime()
            val out = ImageEffects.apply(src, W, H, look)
            val ms = (System.nanoTime() - t0) / 1e6
            assertEquals(src.size, out.size)
            assertTrue("$look saydamlık korunur", out.indices.all { (out[it] ushr 24) == (src[it] ushr 24) || look == ImageLook.PIXELATE || look == ImageLook.OIL || look == ImageLook.GLOW || look == ImageLook.SKETCH })
            if (look != ImageLook.NONE) assertTrue("$look değiştirir", ImageEffects.meanDiff(src, out) > if (look == ImageLook.PIXELATE) 0.2f else 1f)
            println("$look: %.0f ms".format(ms))
        }
        val bright = ImageEffects.apply(src, W, H, adjust = ImageAdjust(brightness = 0.5f))
        assertTrue(mean(bright, 8) > mean(src, 8) + 20)
        val gray = ImageEffects.apply(src, W, H, adjust = ImageAdjust(saturation = -1f))
        assertTrue(gray.filter { it ushr 24 != 0 }.all { kotlin.math.abs((it shr 16 and 0xFF) - (it and 0xFF)) <= 1 })
        val warm = ImageEffects.apply(src, W, H, adjust = ImageAdjust(warmth = 1f))
        assertTrue(mean(warm, 16) > mean(src, 16) && mean(warm, 0) < mean(src, 0))
        val vignette = ImageEffects.apply(src, W, H, adjust = ImageAdjust(vignette = 1f))
        assertTrue((vignette[W - 1] shr 16 and 0xFF) < (src[W - 1] shr 16 and 0xFF))
        val blurred = ImageEffects.apply(src, W, H, adjust = ImageAdjust(blur = 0.5f))
        assertTrue(blurred[150 * W + 20] ushr 24 in 1..254)
        assertEquals(src.toList(), ImageEffects.apply(src, W, H).toList())
    }
}
