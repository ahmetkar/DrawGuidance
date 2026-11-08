package com.karapp.drawguidance.data.print

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * v40 siyah üretimi (GCR): CMY'nin ortak gri payının ne kadarının siyah (K) mürekkeple basılacağı. [start]: grinin
 * bu koyuluğundan sonra siyah devreye girer (0: tam GCR). Saf siyah her seviyede yalnız K ile basılır.
 */
enum class BlackGeneration(val start: Float) { LIGHT(0.5f), MEDIUM(0.25f), HEAVY(0f) }

/** v40 baskıya hazır (CMYK) kayıt ayarları. */
data class CmykOptions(
    val blackGeneration: BlackGeneration = BlackGeneration.MEDIUM,
    /** Toplam mürekkep sınırı (yüzde, 0: sınırsız / %400). Kuşe kağıtta tipik %300, gazete kağıdında %260. */
    val inkLimit: Int = 300,
) {
    companion object {
        /** Pencerede sunulan mürekkep sınırları. */
        val InkLimits = listOf(0, 340, 300, 260)
    }
}

/**
 * v40 RGB → CMYK dönüşümü (ICC profili olmadan, saf Kotlin): C' = 1 − R … ; ortak gri payından [BlackGeneration] ile
 * siyah üretilir (UCR: C = (C' − K) / (1 − K)); toplam mürekkep [CmykOptions.inkLimit]'i aşarsa siyah korunup CMY
 * orantılı azaltılır. Saydam pikseller kağıda (beyaza) karıştırılır.
 */
class CmykConverter(val options: CmykOptions = CmykOptions()) {

    /** Bir ARGB pikselin CMYK değerleri (0..255, 0 = mürekkep yok) [out]'a [offset]'ten yazılır. */
    fun convert(argb: Int, out: ByteArray, offset: Int) {
        val a = (argb ushr 24) and 0xFF
        var r = (argb shr 16) and 0xFF
        var g = (argb shr 8) and 0xFF
        var b = argb and 0xFF
        if (a < 255) {
            // Kağıt beyaz: saydamlık beyaza karışır
            r = (r * a + 255 * (255 - a)) / 255
            g = (g * a + 255 * (255 - a)) / 255
            b = (b * a + 255 * (255 - a)) / 255
        }
        val cmyk = cmyk(r / 255f, g / 255f, b / 255f)
        out[offset] = (cmyk[0] * 255f).roundToInt().coerceIn(0, 255).toByte()
        out[offset + 1] = (cmyk[1] * 255f).roundToInt().coerceIn(0, 255).toByte()
        out[offset + 2] = (cmyk[2] * 255f).roundToInt().coerceIn(0, 255).toByte()
        out[offset + 3] = (cmyk[3] * 255f).roundToInt().coerceIn(0, 255).toByte()
    }

    private val tmp = FloatArray(4)

    /** 0..1 aralığında RGB → CMYK (0..1). Dönen dizi yeniden kullanılır. */
    fun cmyk(r: Float, g: Float, b: Float): FloatArray {
        val c0 = 1f - r
        val m0 = 1f - g
        val y0 = 1f - b
        val k0 = min(c0, min(m0, y0))
        val s = options.blackGeneration.start
        val k = if (k0 <= s) 0f else ((k0 - s) / (1f - s)).coerceAtMost(k0)
        var c: Float; var m: Float; var y: Float
        if (k >= 0.9999f) { c = 0f; m = 0f; y = 0f } else {
            c = ((c0 - k) / (1f - k)).coerceIn(0f, 1f)
            m = ((m0 - k) / (1f - k)).coerceIn(0f, 1f)
            y = ((y0 - k) / (1f - k)).coerceIn(0f, 1f)
        }
        val limit = options.inkLimit / 100f
        if (options.inkLimit in 1..399) {
            val cmy = c + m + y
            if (cmy + k > limit && cmy > 0f) {
                val f = ((limit - k) / cmy).coerceIn(0f, 1f)
                c *= f; m *= f; y *= f
            }
        }
        tmp[0] = c; tmp[1] = m; tmp[2] = y; tmp[3] = k
        return tmp
    }

    /** Bir satır ARGB pikseli CMYK baytlarına çevirir (piksel başına 4 bayt). */
    fun convertRow(pixels: IntArray, from: Int, count: Int, out: ByteArray, outOffset: Int) {
        for (i in 0 until count) convert(pixels[from + i], out, outOffset + i * 4)
    }
}

/**
 * v40 basit ekran prova (soft proof): CMYK'nin baskıdaki görünüşü, ofset baskı mürekkeplerinin (FOGRA39 benzeri
 * kuşe kağıt; nokta kazancıyla) ışık geçirgenlikleri çarpılarak tahmin edilir. Önizlemede asıl rengin açıklığı ve tonu
 * korunur, doygunluğu basılabilen kadarına indirilir ("ana renk grubu": dört süreç mürekkebiyle basılabilen renkler).
 * Doygunluğu belirgin düşen pikseller gamut dışı sayılır.
 */
object SoftProof {
    /** Kâğıt ve tam ton mürekkeplerin sRGB yaklaşıkları: kağıt, C, M, Y, K. */
    private val paper = floatArrayOf(lin(244), lin(244), lin(242))
    private val inks = arrayOf(
        floatArrayOf(lin(0), lin(158), lin(224)),
        floatArrayOf(lin(229), lin(0), lin(126)),
        floatArrayOf(lin(255), lin(237), lin(0)),
        floatArrayOf(lin(38), lin(36), lin(37)),
    )

    /** Gamut uyarısı eşiği (Lab doygunluk farkı). */
    const val CHROMA_LOSS = 14f

    private fun lin(v: Int): Float = srgbToLinear(v / 255f)

    fun srgbToLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    fun linearToSrgb(c: Float): Float = if (c <= 0.0031308f) c * 12.92f else (1.055f * c.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f)

    /** CMYK (0..1) → baskıdaki sRGB (0..1); nokta kazancı %50'de ~%15. */
    fun printed(c: Float, m: Float, y: Float, k: Float, out: FloatArray = FloatArray(3)): FloatArray {
        val cov = floatArrayOf(gain(c), gain(m), gain(y), gain(k))
        for (ch in 0..2) {
            var refl = paper[ch]
            for (i in 0..3) {
                val t = inks[i][ch] / paper[ch]
                refl *= 1f - cov[i] * (1f - t.coerceIn(0f, 1f))
            }
            out[ch] = linearToSrgb(refl.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        }
        return out
    }

    private fun gain(v: Float): Float = (v + 0.6f * v * (1f - v)).coerceIn(0f, 1f)

    /** sRGB (0..1) → CIE Lab (D65). */
    fun lab(r: Float, g: Float, b: Float, out: FloatArray = FloatArray(3)): FloatArray {
        val rl = srgbToLinear(r); val gl = srgbToLinear(g); val bl = srgbToLinear(b)
        val x = (0.4124f * rl + 0.3576f * gl + 0.1805f * bl) / 0.95047f
        val yv = 0.2126f * rl + 0.7152f * gl + 0.0722f * bl
        val z = (0.0193f * rl + 0.1192f * gl + 0.9505f * bl) / 1.08883f
        fun f(t: Float) = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
        val fx = f(x); val fy = f(yv); val fz = f(z)
        out[0] = 116f * fy - 16f; out[1] = 500f * (fx - fy); out[2] = 200f * (fy - fz)
        return out
    }

    /** Lab → sRGB (0..1, kırpılmış). */
    fun rgb(l: Float, a: Float, bb: Float, out: FloatArray = FloatArray(3)): FloatArray {
        val fy = (l + 16f) / 116f
        val fx = fy + a / 500f
        val fz = fy - bb / 200f
        fun inv(t: Float) = if (t * t * t > 0.008856f) t * t * t else (t - 16f / 116f) / 7.787f
        val x = inv(fx) * 0.95047f; val y = inv(fy); val z = inv(fz) * 1.08883f
        val rl = 3.2406f * x - 1.5372f * y - 0.4986f * z
        val gl = -0.9689f * x + 1.8758f * y + 0.0415f * z
        val bl = 0.0557f * x - 0.2040f * y + 1.0570f * z
        out[0] = linearToSrgb(rl.coerceIn(0f, 1f)); out[1] = linearToSrgb(gl.coerceIn(0f, 1f)); out[2] = linearToSrgb(bl.coerceIn(0f, 1f))
        return out
    }

    /** Prova sonucu: önizleme pikselleri ve gamut dışı oranı. */
    class Result(val pixels: IntArray, val outOfGamut: Float, val warning: IntArray)

    /**
     * [pixels] (ARGB) için prova: her pikselin açıklığı ve tonu korunur, doygunluğu baskıdaki doygunluğa indirilir.
     * [warning] gamut dışı pikselleri parlak yeşille işaretlenmiş kopyadır (Photoshop'taki gamut uyarısı gibi).
     */
    fun proof(pixels: IntArray, converter: CmykConverter): Result {
        val out = IntArray(pixels.size)
        val warn = IntArray(pixels.size)
        val labO = FloatArray(3); val labP = FloatArray(3); val pr = FloatArray(3); val rgb = FloatArray(3)
        var flagged = 0; var counted = 0
        for (i in pixels.indices) {
            val px = pixels[i]
            val a = px ushr 24
            if (a == 0) { out[i] = px; warn[i] = px; continue }
            val r = ((px shr 16) and 0xFF) / 255f; val g = ((px shr 8) and 0xFF) / 255f; val b = (px and 0xFF) / 255f
            val cmyk = converter.cmyk(r, g, b)
            printed(cmyk[0], cmyk[1], cmyk[2], cmyk[3], pr)
            lab(r, g, b, labO)
            lab(pr[0], pr[1], pr[2], labP)
            val chromaO = hypot(labO[1], labO[2])
            val chromaP = hypot(labP[1], labP[2])
            counted++
            val lost = chromaO - chromaP > CHROMA_LOSS
            if (lost) flagged++
            val chroma = min(chromaO, chromaP)
            val hue = atan2(labO[2], labO[1])
            rgb(labO[0], chroma * cos(hue), chroma * sin(hue), rgb)
            val shown = (a shl 24) or (to8(rgb[0]) shl 16) or (to8(rgb[1]) shl 8) or to8(rgb[2])
            out[i] = shown
            warn[i] = if (lost) (a shl 24) or 0x00E040 else shown
        }
        return Result(out, if (counted == 0) 0f else flagged.toFloat() / counted, warn)
    }

    private fun to8(v: Float): Int = (max(0f, min(1f, v)) * 255f).roundToInt()
}
