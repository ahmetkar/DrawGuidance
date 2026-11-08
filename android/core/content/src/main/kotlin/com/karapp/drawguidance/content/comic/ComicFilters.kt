package com.karapp.drawguidance.content.comic

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** v27 resim filtreleri (çizgi roman görünümü). */
enum class ComicFilter { COMIC, HALFTONE, MANGA, POP }

/**
 * v27 çizgi roman resim filtreleri (saf Kotlin, ARGB piksel dizileri): tek geçişli, bellek dostu (giriş dizisine ek
 * olarak iki dizi). 1–2 megapikselde telefonda yüzlerce milisaniye; arka planda çalıştırılır.
 *
 * - [ComicFilter.COMIC]: hafif bulanıklık + renk basamaklama (posterize) + Sobel kenarlarından mürekkep çizgileri.
 * - [ComicFilter.HALFTONE]: ton noktaları (gazete baskısı): hücre başına koyuluk kadar siyah nokta.
 * - [ComicFilter.MANGA]: siyah-beyaz; koyu yerler siyah, açık yerler beyaz, ara tonlar ton noktası (screentone).
 * - [ComicFilter.POP]: doygun, az basamaklı renkler ve kalın kenarlar (pop art).
 */
object ComicFilters {
    fun apply(kind: ComicFilter, src: IntArray, w: Int, h: Int, cancelled: () -> Boolean = { false }): IntArray {
        require(src.size >= w * h)
        return when (kind) {
            ComicFilter.COMIC -> posterEdges(src, w, h, levels = 5, saturation = 1.1f, edge = 0.22f, thick = false, cancelled)
            ComicFilter.POP -> posterEdges(src, w, h, levels = 3, saturation = 1.6f, edge = 0.18f, thick = true, cancelled)
            ComicFilter.HALFTONE -> halftone(src, w, h, manga = false)
            ComicFilter.MANGA -> halftone(src, w, h, manga = true)
        }
    }

    private fun lum(c: Int): Float = (0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)) / 255f

    private fun posterEdges(src: IntArray, w: Int, h: Int, levels: Int, saturation: Float, edge: Float, thick: Boolean, cancelled: () -> Boolean): IntArray {
        val n = w * h
        val l = FloatArray(n) { lum(src[it]) }
        val out = IntArray(n)
        val step = 255f / (levels - 1)
        for (y in 0 until h) {
            if (y % 64 == 0 && cancelled()) return src.copyOf()
            for (x in 0 until w) {
                val i = y * w + x
                // 3×3 kutu bulanıklığı (gürültü basamaklarda leke yapmasın)
                var r = 0; var g = 0; var b = 0; var a = 0; var k = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, h - 1)
                    for (dx in -1..1) {
                        val c = src[yy * w + (x + dx).coerceIn(0, w - 1)]
                        r += c shr 16 and 0xFF; g += c shr 8 and 0xFF; b += c and 0xFF; k++
                    }
                }
                a = src[i] ushr 24
                var rf = r / k.toFloat(); var gf = g / k.toFloat(); var bf = b / k.toFloat()
                // Doygunluk
                val m = (rf + gf + bf) / 3f
                rf = m + (rf - m) * saturation; gf = m + (gf - m) * saturation; bf = m + (bf - m) * saturation
                val rq = (Math.round(rf.coerceIn(0f, 255f) / step) * step).toInt()
                val gq = (Math.round(gf.coerceIn(0f, 255f) / step) * step).toInt()
                val bq = (Math.round(bf.coerceIn(0f, 255f) / step) * step).toInt()
                // Sobel kenarı
                fun L(xx: Int, yy: Int) = l[yy.coerceIn(0, h - 1) * w + xx.coerceIn(0, w - 1)]
                val gx = -L(x - 1, y - 1) - 2 * L(x - 1, y) - L(x - 1, y + 1) + L(x + 1, y - 1) + 2 * L(x + 1, y) + L(x + 1, y + 1)
                val gy = -L(x - 1, y - 1) - 2 * L(x, y - 1) - L(x + 1, y - 1) + L(x - 1, y + 1) + 2 * L(x, y + 1) + L(x + 1, y + 1)
                val mag = sqrt(gx * gx + gy * gy)
                out[i] = if (mag > edge) (a shl 24) or 0x111111 else (a shl 24) or (rq shl 16) or (gq shl 8) or bq
            }
        }
        if (thick) {
            // Kenarlar bir piksel kalınlaştırılır
            val copy = out.copyOf()
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                if (copy[i] and 0xFFFFFF == 0x111111) continue
                if (copy[i - 1] and 0xFFFFFF == 0x111111 && copy[i + 1] and 0xFFFFFF == 0x111111) out[i] = (copy[i] and 0xFF000000.toInt()) or 0x111111
            }
        }
        return out
    }

    private fun halftone(src: IntArray, w: Int, h: Int, manga: Boolean): IntArray {
        val out = IntArray(w * h)
        val cell = max(4, min(w, h) / 110)
        val half = cell / 2f
        for (cy in 0 until h step cell) for (cx in 0 until w step cell) {
            // Hücrenin ortalama koyuluğu
            var sum = 0f; var cnt = 0; var alpha = 0
            for (y in cy until min(h, cy + cell)) for (x in cx until min(w, cx + cell)) { sum += lum(src[y * w + x]); alpha = max(alpha, src[y * w + x] ushr 24); cnt++ }
            val dark = 1f - sum / max(cnt, 1)
            // Manga: çok koyu siyah, çok açık beyaz, ara tonlar nokta
            val mode = when {
                !manga -> 0
                dark > 0.78f -> 1
                dark < 0.18f -> 2
                else -> 0
            }
            val radius = half * 1.25f * sqrt(dark.coerceIn(0f, 1f))
            for (y in cy until min(h, cy + cell)) for (x in cx until min(w, cx + cell)) {
                val i = y * w + x
                val a = src[i] ushr 24
                val black = when (mode) {
                    1 -> true
                    2 -> false
                    else -> {
                        val dx = x - cx - half + 0.5f; val dy = y - cy - half + 0.5f
                        dx * dx + dy * dy <= radius * radius
                    }
                }
                out[i] = (a shl 24) or (if (black) 0x101010 else 0xFFFFFF)
            }
            if (alpha == 0) Unit
        }
        return out
    }
}
