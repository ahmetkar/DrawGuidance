package com.karapp.drawguidance.content.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * v30 resmin genel ayarları. Renk ayarları −1..1 (0: değişmez), diğerleri 0..1 (0: yok).
 */
data class ImageAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    /** Sıcaklık: + sarı/turuncu, − mavi. */
    val warmth: Float = 0f,
    /** Ton: + macenta, − yeşil. */
    val tint: Float = 0f,
    val blur: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    /** Solukluk: siyahlar griye açılır (film görünümü). */
    val fade: Float = 0f,
) {
    val isIdentity: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f && tint == 0f &&
            blur == 0f && sharpen == 0f && vignette == 0f && grain == 0f && fade == 0f
}

/** v30 hazır görünümler (efektler). */
enum class ImageLook {
    NONE, GRAYSCALE, SEPIA, INVERT, VINTAGE, COOL, WARM, NOIR, VIVID, DUOTONE,
    POSTERIZE, PIXELATE, SKETCH, EMBOSS, OIL, GLOW,
}

/**
 * v30 resim efektleri (saf Kotlin, ARGB piksel dizileri; saydamlık korunur). Önce hazır görünüm, sonra renk
 * ayarları (tek geçişli 4×5 renk matrisi), sonra bulanıklık/keskinlik (ayrılabilir kutu bulanıklığı, O(n)), en son
 * vinyet, gren ve solukluk uygulanır. 1–2 megapikselde telefonda birkaç yüz milisaniye; arka planda çalıştırılır.
 */
object ImageEffects {

    fun apply(src: IntArray, w: Int, h: Int, look: ImageLook = ImageLook.NONE, adjust: ImageAdjust = ImageAdjust(), cancelled: () -> Boolean = { false }): IntArray {
        require(src.size >= w * h && w > 0 && h > 0)
        var px = applyLook(src, w, h, look, cancelled)
        if (cancelled()) return src.copyOf()
        val matrix = adjustMatrix(adjust)
        if (matrix != null) px = colorMatrix(if (px === src) px.copyOf() else px, matrix)
        if (adjust.blur > 0f) {
            val r = max(1, (adjust.blur * min(w, h) * 0.025f).roundToInt())
            px = blur(px, w, h, r)
        }
        if (adjust.sharpen > 0f) px = sharpen(px, w, h, adjust.sharpen)
        if (adjust.vignette > 0f || adjust.grain > 0f || adjust.fade > 0f) px = finish(if (px === src) px.copyOf() else px, w, h, adjust)
        return if (px === src) src.copyOf() else px
    }

    // --- Renk matrisi ---

    /** 4×5 matris (r, g, b satırları; son sütun 0..255 öteleme). Değişiklik yoksa null. */
    internal fun adjustMatrix(a: ImageAdjust): FloatArray? {
        if (a.brightness == 0f && a.contrast == 0f && a.saturation == 0f && a.warmth == 0f && a.tint == 0f) return null
        var m = identity()
        if (a.saturation != 0f) m = mul(saturationMatrix(1f + a.saturation.coerceIn(-1f, 1f)), m)
        if (a.contrast != 0f) {
            // −1: düz gri, +1: 3 kat karşıtlık (yumuşak eğri)
            val k = if (a.contrast >= 0f) 1f + a.contrast * 2f else 1f + a.contrast
            val t = 128f * (1f - k)
            m = mul(floatArrayOf(k, 0f, 0f, t, 0f, k, 0f, t, 0f, 0f, k, t), m)
        }
        if (a.brightness != 0f) {
            val b = a.brightness * 90f
            m = mul(floatArrayOf(1f, 0f, 0f, b, 0f, 1f, 0f, b, 0f, 0f, 1f, b), m)
        }
        if (a.warmth != 0f || a.tint != 0f) {
            val wv = a.warmth * 28f; val tv = a.tint * 22f
            m = mul(floatArrayOf(1f, 0f, 0f, wv + tv * 0.5f, 0f, 1f, 0f, -tv + wv * 0.15f, 0f, 0f, 1f, -wv + tv * 0.5f), m)
        }
        return m
    }

    private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)

    /** a·b (3×4 afin matrisler). */
    private fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(12)
        for (r in 0 until 3) {
            for (c in 0 until 3) o[r * 4 + c] = a[r * 4] * b[c] + a[r * 4 + 1] * b[4 + c] + a[r * 4 + 2] * b[8 + c]
            o[r * 4 + 3] = a[r * 4] * b[3] + a[r * 4 + 1] * b[7] + a[r * 4 + 2] * b[11] + a[r * 4 + 3]
        }
        return o
    }

    private fun saturationMatrix(s: Float): FloatArray {
        val lr = 0.2126f * (1 - s); val lg = 0.7152f * (1 - s); val lb = 0.0722f * (1 - s)
        return floatArrayOf(lr + s, lg, lb, 0f, lr, lg + s, lb, 0f, lr, lg, lb + s, 0f)
    }

    private fun colorMatrix(px: IntArray, m: FloatArray): IntArray {
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = (c shr 16 and 0xFF).toFloat(); val g = (c shr 8 and 0xFF).toFloat(); val b = (c and 0xFF).toFloat()
            px[i] = (a shl 24) or (clamp(m[0] * r + m[1] * g + m[2] * b + m[3]) shl 16) or
                (clamp(m[4] * r + m[5] * g + m[6] * b + m[7]) shl 8) or clamp(m[8] * r + m[9] * g + m[10] * b + m[11])
        }
        return px
    }

    private fun clamp(v: Float): Int = if (v <= 0f) 0 else if (v >= 255f) 255 else (v + 0.5f).toInt()

    // --- Hazır görünümler ---

    private fun applyLook(src: IntArray, w: Int, h: Int, look: ImageLook, cancelled: () -> Boolean): IntArray = when (look) {
        ImageLook.NONE -> src
        ImageLook.GRAYSCALE -> colorMatrix(src.copyOf(), saturationMatrix(0f))
        ImageLook.SEPIA -> colorMatrix(src.copyOf(), floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0.272f, 0.534f, 0.131f, 0f))
        ImageLook.INVERT -> colorMatrix(src.copyOf(), floatArrayOf(-1f, 0f, 0f, 255f, 0f, -1f, 0f, 255f, 0f, 0f, -1f, 255f))
        ImageLook.VINTAGE -> finish(
            colorMatrix(src.copyOf(), mul(floatArrayOf(0.9f, 0.1f, 0.05f, 18f, 0.07f, 0.85f, 0.08f, 10f, 0.05f, 0.1f, 0.7f, 6f), saturationMatrix(0.7f))),
            w, h, ImageAdjust(vignette = 0.45f, fade = 0.25f),
        )
        ImageLook.COOL -> colorMatrix(src.copyOf(), floatArrayOf(0.92f, 0f, 0f, -6f, 0f, 1f, 0f, 4f, 0f, 0f, 1.08f, 18f))
        ImageLook.WARM -> colorMatrix(src.copyOf(), floatArrayOf(1.08f, 0f, 0f, 16f, 0f, 1.02f, 0f, 6f, 0f, 0f, 0.9f, -10f))
        ImageLook.NOIR -> colorMatrix(src.copyOf(), mul(floatArrayOf(1.6f, 0f, 0f, -60f, 0f, 1.6f, 0f, -60f, 0f, 0f, 1.6f, -60f), saturationMatrix(0f)))
        ImageLook.VIVID -> colorMatrix(src.copyOf(), mul(floatArrayOf(1.12f, 0f, 0f, -8f, 0f, 1.12f, 0f, -8f, 0f, 0f, 1.12f, -8f), saturationMatrix(1.55f)))
        ImageLook.DUOTONE -> duotone(src, 0x1B2A6B, 0xFFB86B)
        ImageLook.POSTERIZE -> posterize(src, 4)
        ImageLook.PIXELATE -> pixelate(src, w, h, max(2, min(w, h) / 56))
        ImageLook.SKETCH -> sketch(src, w, h)
        ImageLook.EMBOSS -> emboss(src, w, h)
        ImageLook.OIL -> kuwahara(src, w, h, if (w * h > 1_500_000) 2 else 3, cancelled)
        ImageLook.GLOW -> glow(src, w, h)
    }

    private fun lum(c: Int): Float = 0.2126f * (c shr 16 and 0xFF) + 0.7152f * (c shr 8 and 0xFF) + 0.0722f * (c and 0xFF)

    private fun duotone(src: IntArray, dark: Int, light: Int): IntArray {
        val out = IntArray(src.size)
        val dr = dark shr 16 and 0xFF; val dg = dark shr 8 and 0xFF; val db = dark and 0xFF
        val lr = light shr 16 and 0xFF; val lg = light shr 8 and 0xFF; val lb = light and 0xFF
        for (i in src.indices) {
            val c = src[i]
            val t = lum(c) / 255f
            out[i] = (c and 0xFF000000.toInt()) or (clamp(dr + (lr - dr) * t) shl 16) or (clamp(dg + (lg - dg) * t) shl 8) or clamp(db + (lb - db) * t)
        }
        return out
    }

    private fun posterize(src: IntArray, levels: Int): IntArray {
        val step = 255f / (levels - 1)
        val lut = IntArray(256) { ((it / step).roundToInt() * step).roundToInt().coerceIn(0, 255) }
        return IntArray(src.size) { i ->
            val c = src[i]
            (c and 0xFF000000.toInt()) or (lut[c shr 16 and 0xFF] shl 16) or (lut[c shr 8 and 0xFF] shl 8) or lut[c and 0xFF]
        }
    }

    private fun pixelate(src: IntArray, w: Int, h: Int, cell: Int): IntArray {
        val out = IntArray(src.size)
        var y0 = 0
        while (y0 < h) {
            val y1 = min(h, y0 + cell)
            var x0 = 0
            while (x0 < w) {
                val x1 = min(w, x0 + cell)
                var a = 0L; var r = 0L; var g = 0L; var b = 0L; var n = 0
                for (y in y0 until y1) for (x in x0 until x1) {
                    val c = src[y * w + x]; val ca = c ushr 24
                    a += ca; r += (c shr 16 and 0xFF) * ca; g += (c shr 8 and 0xFF) * ca; b += (c and 0xFF) * ca; n++
                }
                val avg = if (a == 0L) 0 else ((a / n).toInt() shl 24) or ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (b / a).toInt()
                for (y in y0 until y1) for (x in x0 until x1) out[y * w + x] = avg
                x0 = x1
            }
            y0 = y1
        }
        return out
    }

    /** Kurşun kalem çizimi: gri → ters → bulanık → renk soldurma (color dodge). */
    private fun sketch(src: IntArray, w: Int, h: Int): IntArray {
        val n = w * h
        val gray = IntArray(n) { val l = lum(src[it]).roundToInt().coerceIn(0, 255); (src[it] and 0xFF000000.toInt()) or (l shl 16) or (l shl 8) or l }
        val inv = IntArray(n) { (gray[it] and 0xFF000000.toInt()) or (0xFFFFFF - (gray[it] and 0xFFFFFF)) }
        val blurred = blur(inv, w, h, max(2, min(w, h) / 90))
        return IntArray(n) { i ->
            val base = gray[i] and 0xFF
            val top = blurred[i] and 0xFF
            val v = if (top >= 255) 255 else min(255, base * 255 / (255 - top))
            (src[i] and 0xFF000000.toInt()) or (v shl 16) or (v shl 8) or v
        }
    }

    private fun emboss(src: IntArray, w: Int, h: Int): IntArray {
        val l = FloatArray(w * h) { lum(src[it]) }
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            fun L(dx: Int, dy: Int) = l[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
            val v = clamp(128f + 1.5f * (-2 * L(-1, -1) - L(0, -1) - L(-1, 0) + L(1, 0) + L(0, 1) + 2 * L(1, 1)) / 2f)
            (src[i] and 0xFF000000.toInt()) or (v shl 16) or (v shl 8) or v
        }
    }

    /** Yumuşak parıltı: bulanık kopya "screen" karışımıyla eklenir. */
    private fun glow(src: IntArray, w: Int, h: Int): IntArray {
        val b = blur(src, w, h, max(2, min(w, h) / 40))
        return IntArray(src.size) { i ->
            val c = src[i]; val d = b[i]
            fun screen(x: Int, y: Int): Int = 255 - (255 - x) * (255 - (y * 0.6f).toInt()) / 255
            (c and 0xFF000000.toInt()) or (screen(c shr 16 and 0xFF, d shr 16 and 0xFF) shl 16) or
                (screen(c shr 8 and 0xFF, d shr 8 and 0xFF) shl 8) or screen(c and 0xFF, d and 0xFF)
        }
    }

    /**
     * Yağlı boya (Kuwahara): her piksel, çevresindeki dört çeyrekten parlaklık varyansı en küçük olanın ortalama
     * rengini alır; kenarlar korunur, düz alanlar fırça darbesi gibi düzleşir.
     */
    private fun kuwahara(src: IntArray, w: Int, h: Int, r: Int, cancelled: () -> Boolean): IntArray {
        val out = IntArray(w * h)
        val l = FloatArray(w * h) { lum(src[it]) }
        val qx = intArrayOf(-r, 0, -r, 0); val qy = intArrayOf(-r, -r, 0, 0)
        for (y in 0 until h) {
            if (y % 32 == 0 && cancelled()) return src.copyOf()
            for (x in 0 until w) {
                var best = Float.MAX_VALUE; var br = 0f; var bg = 0f; var bb = 0f
                for (q in 0 until 4) {
                    var sum = 0f; var sq = 0f; var rr = 0f; var gg = 0f; var bbq = 0f; var n = 0
                    for (dy in 0..r) {
                        val yy = (y + qy[q] + dy).coerceIn(0, h - 1)
                        for (dx in 0..r) {
                            val i = yy * w + (x + qx[q] + dx).coerceIn(0, w - 1)
                            val v = l[i]; sum += v; sq += v * v
                            val c = src[i]; rr += c shr 16 and 0xFF; gg += c shr 8 and 0xFF; bbq += c and 0xFF; n++
                        }
                    }
                    val mean = sum / n
                    val variance = sq / n - mean * mean
                    if (variance < best) { best = variance; br = rr / n; bg = gg / n; bb = bbq / n }
                }
                out[y * w + x] = (src[y * w + x] and 0xFF000000.toInt()) or (clamp(br) shl 16) or (clamp(bg) shl 8) or clamp(bb)
            }
        }
        return out
    }

    // --- Bulanıklık ve keskinlik ---

    /**
     * Saydamlığa duyarlı (ön-çarpımlı) bulanıklık: üç geçişli ayrılabilir kutu bulanıklığı (Gauss'a yakın), yarıçaptan
     * bağımsız O(n). Saydam kenarlarda koyu hale oluşmaz.
     */
    fun blur(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return src.copyOf()
        val n = w * h
        val a = IntArray(n); val r = IntArray(n); val g = IntArray(n); val b = IntArray(n)
        for (i in 0 until n) {
            val c = src[i]; val ca = c ushr 24
            a[i] = ca; r[i] = (c shr 16 and 0xFF) * ca / 255; g[i] = (c shr 8 and 0xFF) * ca / 255; b[i] = (c and 0xFF) * ca / 255
        }
        val tmp = IntArray(n)
        val k = max(1, (radius / 1.7f).roundToInt())
        for (ch in arrayOf(a, r, g, b)) repeat(3) { boxH(ch, tmp, w, h, k); boxV(tmp, ch, w, h, k) }
        return IntArray(n) { i ->
            val ca = a[i]
            if (ca == 0) 0 else (ca shl 24) or (min(255, r[i] * 255 / ca) shl 16) or (min(255, g[i] * 255 / ca) shl 8) or min(255, b[i] * 255 / ca)
        }
    }

    private fun boxH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (i in -r..r) sum += src[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / div
                sum += src[row + min(w - 1, x + r + 1)] - src[row + max(0, x - r)]
            }
        }
    }

    private fun boxV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (i in -r..r) sum += src[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / div
                sum += src[min(h - 1, y + r + 1) * w + x] - src[max(0, y - r) * w + x]
            }
        }
    }

    /** Keskinleştirme maskesi: c + k·(c − bulanık c). */
    private fun sharpen(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val soft = blur(src, w, h, max(1, min(w, h) / 500 + 1))
        val k = amount * 1.6f
        return IntArray(src.size) { i ->
            val c = src[i]; val s = soft[i]
            fun ch(shift: Int): Int { val v = c shr shift and 0xFF; return clamp(v + k * (v - (s shr shift and 0xFF))) }
            (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }

    /** Vinyet, gren ve solukluk (yerinde). */
    private fun finish(px: IntArray, w: Int, h: Int, a: ImageAdjust): IntArray {
        val cx = (w - 1) / 2f; val cy = (h - 1) / 2f
        val maxD = sqrt(cx * cx + cy * cy).coerceAtLeast(1f)
        val fadeLift = a.fade * 70f
        val fadeK = 1f - a.fade * 0.28f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                if (c ushr 24 == 0) continue
                var m = 1f
                if (a.vignette > 0f) {
                    val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / maxD
                    val t = ((d - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    m = 1f - a.vignette * 0.85f * t * t * (3 - 2 * t)
                }
                var noise = 0f
                if (a.grain > 0f) {
                    var hsh = x * 374761393 + y * 668265263
                    hsh = (hsh xor (hsh ushr 13)) * 1274126177
                    noise = ((hsh ushr 8 and 0xFFFF) / 65535f - 0.5f) * a.grain * 70f
                }
                fun ch(v: Int): Int = clamp((v * m + noise) * fadeK + fadeLift)
                px[i] = (c and 0xFF000000.toInt()) or (ch(c shr 16 and 0xFF) shl 16) or (ch(c shr 8 and 0xFF) shl 8) or ch(c and 0xFF)
            }
        }
        return px
    }

    /** İki ARGB dizisinin ortalama kanal farkı (testler ve önizleme karşılaştırması için). */
    fun meanDiff(a: IntArray, b: IntArray): Float {
        var s = 0L
        for (i in a.indices) {
            val x = a[i]; val y = b[i]
            s += abs((x shr 16 and 0xFF) - (y shr 16 and 0xFF)) + abs((x shr 8 and 0xFF) - (y shr 8 and 0xFF)) + abs((x and 0xFF) - (y and 0xFF))
        }
        return s / (3f * a.size)
    }
}
