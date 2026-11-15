package com.karapp.drawguidance.content.image

import com.karapp.drawguidance.content.comic.ComicFilter
import com.karapp.drawguidance.content.comic.ComicFilters
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * v52 Foto düzenleme modu: resmin bütün raster düzenlemeleri tek, yıkıcı olmayan bir durumda ([PhotoEdit]) toplanır ve
 * özgün piksellerden her seferinde baştan uygulanır (geri al/yinele yalnız durum geçmişidir). iOS `PhotoEdit` ile aynı
 * sıra ve aynı hesap:
 *  1. rötuş fırçaları (kaynak resmin koordinatlarında: boya, geri getir, bulanıklaştır, aç, koyulaştır, yumuşat),
 *  2. arka plan kaldırma,
 *  3. geometri: 90° döndürme, yatay/dikey çevirme, eğim düzeltme (köşeler boş kalmasın diye büyütülür), kırpma,
 *  4. ışık: pozlama, vurgular, gölgeler, beyazlar, siyahlar, netlik,
 *  5. çizgi roman filtresi, hazır görünüm (filtre) ve genel ayarlar (ImageEffects: parlaklık, karşıtlık, doygunluk,
 *     sıcaklık, ton, bulanıklık, keskinlik, vinyet, gren, solukluk),
 *  6. canlılık.
 */

/** Rötuş fırçası türleri. */
enum class PhotoBrush { PAINT, RESTORE, BLUR, LIGHTEN, DARKEN, SMOOTH }

/**
 * Rötuş fırça izi: noktalar kaynak resmin 0..1 koordinatlarında (x, y çiftleri), [size] kaynağın kısa kenarına oranla
 * çap, [strength] 0..1, [color] boyada ARGB.
 */
data class PhotoStroke(val brush: PhotoBrush, val points: List<Float>, val size: Float, val strength: Float = 0.6f, val color: Int = 0xFFE53935.toInt())

/** Kırpma çerçevesi (döndürülmüş/düzeltilmiş resmin 0..1 koordinatlarında). */
data class PhotoCrop(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val isFull: Boolean get() = left <= 0.0005f && top <= 0.0005f && right >= 0.9995f && bottom >= 0.9995f
}

/** Işık ayarları (−1..1). */
data class PhotoLight(
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val clarity: Float = 0f,
    val vibrance: Float = 0f,
) {
    val isIdentity: Boolean get() = exposure == 0f && highlights == 0f && shadows == 0f && whites == 0f && blacks == 0f && clarity == 0f && vibrance == 0f
}

/**
 * v53 HSL ayarı: 8 renk aralığı (kırmızı, turuncu, sarı, yeşil, camgöbeği, mavi, mor, macenta) için ton kaydırma,
 * doygunluk ve açıklık (her biri −1..1; ton ±30°).
 */
data class PhotoHsl(
    val hue: List<Float> = ZERO8,
    val saturation: List<Float> = ZERO8,
    val lightness: List<Float> = ZERO8,
) {
    val isIdentity: Boolean get() = hue.all { it == 0f } && saturation.all { it == 0f } && lightness.all { it == 0f }

    fun with(range: Int, h: Float? = null, s: Float? = null, l: Float? = null): PhotoHsl = copy(
        hue = if (h == null) hue else hue.toMutableList().also { it[range] = h },
        saturation = if (s == null) saturation else saturation.toMutableList().also { it[range] = s },
        lightness = if (l == null) lightness else lightness.toMutableList().also { it[range] = l },
    )

    companion object {
        val ZERO8: List<Float> = List(8) { 0f }
        /** Aralıkların merkez tonları (derece). */
        val CENTERS = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 275f, 315f)
        val SWATCHES = longArrayOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFFD81B60)
    }
}

/**
 * v53 renk dengesi: gölgeler, orta tonlar ve vurgular için camgöbeği–kırmızı, macenta–yeşil, sarı–mavi (−1..1);
 * [preserve] parlaklığı korur.
 */
data class PhotoBalance(
    val shadows: List<Float> = ZERO3,
    val midtones: List<Float> = ZERO3,
    val highlights: List<Float> = ZERO3,
    val preserve: Boolean = true,
) {
    val isIdentity: Boolean get() = shadows.all { it == 0f } && midtones.all { it == 0f } && highlights.all { it == 0f }

    fun range(i: Int): List<Float> = when (i) { 0 -> shadows; 1 -> midtones; else -> highlights }
    fun with(i: Int, channel: Int, v: Float): PhotoBalance {
        val r = range(i).toMutableList().also { it[channel] = v }
        return when (i) { 0 -> copy(shadows = r); 1 -> copy(midtones = r); else -> copy(highlights = r) }
    }

    companion object { val ZERO3: List<Float> = List(3) { 0f } }
}

/** v53 kırpma maskesinin dolgusu ve karışım kipi. */
enum class ClipFill { COLOR, GRADIENT }
enum class ClipBlend { NORMAL, MULTIPLY, SCREEN, OVERLAY, SOFT_LIGHT, COLOR }

/**
 * v53 kırpma maskesi: renk ya da gradyan dolgusu yalnız resmin görünen (saydam olmayan) piksellerine kırpılarak
 * karıştırılır (arka plan kaldırılınca nesnenin şekli dolar). [angle] gradyan açısı (derece), [opacity] 0..1.
 */
data class PhotoClip(
    val fill: ClipFill = ClipFill.COLOR,
    val color1: Int = 0xFFE53935.toInt(),
    val color2: Int = 0xFF1E88E5.toInt(),
    val angle: Float = 90f,
    val blend: ClipBlend = ClipBlend.OVERLAY,
    val opacity: Float = 0.7f,
)

data class PhotoEdit(
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    /** Eğim düzeltme (derece, −45..45). */
    val straighten: Float = 0f,
    val crop: PhotoCrop = PhotoCrop(),
    val light: PhotoLight = PhotoLight(),
    val adjust: ImageAdjust = ImageAdjust(),
    val look: ImageLook = ImageLook.NONE,
    val comic: ComicFilter? = null,
    val background: BackgroundOptions? = null,
    val strokes: List<PhotoStroke> = emptyList(),
    /** v53 renk aralıklarına göre ton, doygunluk, açıklık. */
    val hsl: PhotoHsl = PhotoHsl(),
    /** v53 renk dengesi. */
    val balance: PhotoBalance = PhotoBalance(),
    /** v53 kırpma maskesi (null: yok). */
    val clip: PhotoClip? = null,
) {
    val isIdentity: Boolean get() = this == PhotoEdit()
    val quarter: Int get() = ((rotation % 4) + 4) % 4
}

object PhotoOps {

    /** [edit] uygulanmış resim (yeni dizi) ve boyutu. [skipCrop]: kırpma ekranının önizlemesi (çerçeve üstte çizilir). */
    fun render(src: IntArray, w: Int, h: Int, edit: PhotoEdit, skipCrop: Boolean = false, cancelled: () -> Boolean = { false }): Triple<IntArray, Int, Int> {
        var px = src.copyOf()
        if (edit.strokes.isNotEmpty()) px = applyStrokes(px, src, w, h, edit.strokes)
        if (cancelled()) return Triple(px, w, h)
        edit.background?.let { opt ->
            val mask = BackgroundRemover.mask(px, w, h, opt, cancelled)
            px = BackgroundRemover.apply(px, w, h, mask)
        }
        if (cancelled()) return Triple(px, w, h)
        var cw = w; var ch = h
        if (edit.quarter != 0 || edit.flipH || edit.flipV) {
            val r = orient(px, cw, ch, edit.quarter, edit.flipH, edit.flipV); px = r.first; cw = r.second; ch = r.third
        }
        if (edit.straighten != 0f) px = straighten(px, cw, ch, edit.straighten)
        if (!skipCrop && !edit.crop.isFull) {
            val r = crop(px, cw, ch, edit.crop); px = r.first; cw = r.second; ch = r.third
        }
        if (cancelled()) return Triple(px, cw, ch)
        if (!edit.light.isIdentity) px = light(px, cw, ch, edit.light)
        if (!edit.hsl.isIdentity) px = hsl(px, edit.hsl)
        if (!edit.balance.isIdentity) px = balance(px, edit.balance)
        if (cancelled()) return Triple(px, cw, ch)
        edit.comic?.let { px = ComicFilters.apply(it, px, cw, ch, cancelled) }
        if (edit.look != ImageLook.NONE || !edit.adjust.isIdentity) px = ImageEffects.apply(px, cw, ch, edit.look, edit.adjust, cancelled)
        edit.clip?.let { px = clip(px, cw, ch, it) }
        return Triple(px, cw, ch)
    }

    // ------------------------------------------------------------------ v53 HSL, renk dengesi, kırpma maskesi

    private fun rgbToHsl(r: Float, g: Float, b: Float, out: FloatArray) {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        if (mx == mn) { out[0] = 0f; out[1] = 0f; out[2] = l; return }
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        var h = when (mx) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        h *= 60f
        out[0] = h; out[1] = s; out[2] = l
    }

    private fun hue2rgb(p: Float, q: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 0.5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    private fun hslToRgb(h0: Float, s: Float, l: Float, out: FloatArray) {
        if (s <= 0f) { out[0] = l; out[1] = l; out[2] = l; return }
        val h = (((h0 % 360f) + 360f) % 360f) / 360f
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        out[0] = hue2rgb(p, q, h + 1f / 3f); out[1] = hue2rgb(p, q, h); out[2] = hue2rgb(p, q, h - 1f / 3f)
    }

    /** Tonun aralığa yakınlığı (0..1): merkezden komşu merkeze doğru doğrusal azalır. */
    fun rangeWeight(h: Float, range: Int): Float {
        val c = PhotoHsl.CENTERS
        val center = c[range]
        val prev = c[(range + 7) % 8]; val next = c[(range + 1) % 8]
        var d = h - center
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        val span = if (d >= 0f) ((next - center + 360f) % 360f) else ((center - prev + 360f) % 360f)
        return (1f - abs(d) / span.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    fun hsl(src: IntArray, adj: PhotoHsl): IntArray {
        val out = IntArray(src.size)
        val hsl = FloatArray(3); val rgb = FloatArray(3)
        for (i in src.indices) {
            val c = src[i]
            val a = c ushr 24
            if (a == 0) { out[i] = c; continue }
            rgbToHsl(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f, hsl)
            val gray = (hsl[1] * 2f).coerceIn(0f, 1f)
            var dh = 0f; var ds = 0f; var dl = 0f
            for (r in 0 until 8) {
                val w = rangeWeight(hsl[0], r) * gray
                if (w == 0f) continue
                dh += w * adj.hue[r]; ds += w * adj.saturation[r]; dl += w * adj.lightness[r]
            }
            if (dh == 0f && ds == 0f && dl == 0f) { out[i] = c; continue }
            val h = hsl[0] + dh * 30f
            val s = (if (ds >= 0f) hsl[1] + (1f - hsl[1]) * ds else hsl[1] * (1f + ds)).coerceIn(0f, 1f)
            val l = (if (dl >= 0f) hsl[2] + (1f - hsl[2]) * dl * 0.6f else hsl[2] * (1f + dl * 0.6f)).coerceIn(0f, 1f)
            hslToRgb(h, s, l, rgb)
            out[i] = (a shl 24) or ((rgb[0] * 255f).roundToInt().coerceIn(0, 255) shl 16) or
                ((rgb[1] * 255f).roundToInt().coerceIn(0, 255) shl 8) or (rgb[2] * 255f).roundToInt().coerceIn(0, 255)
        }
        return out
    }

    fun balance(src: IntArray, b: PhotoBalance): IntArray {
        val out = IntArray(src.size)
        for (i in src.indices) {
            val c = src[i]
            val a = c ushr 24
            if (a == 0) { out[i] = c; continue }
            val r0 = ((c shr 16) and 0xFF) / 255f; val g0 = ((c shr 8) and 0xFF) / 255f; val b0 = (c and 0xFF) / 255f
            val lum = 0.299f * r0 + 0.587f * g0 + 0.114f * b0
            val ws = ((0.5f - lum) * 2f).coerceIn(0f, 1f)
            val wh = ((lum - 0.5f) * 2f).coerceIn(0f, 1f)
            val wm = (1f - abs(lum - 0.5f) * 2f).coerceIn(0f, 1f)
            val k = 0.32f
            var r = r0 + (b.shadows[0] * ws + b.midtones[0] * wm + b.highlights[0] * wh) * k
            var g = g0 + (b.shadows[1] * ws + b.midtones[1] * wm + b.highlights[1] * wh) * k
            var bl = b0 + (b.shadows[2] * ws + b.midtones[2] * wm + b.highlights[2] * wh) * k
            if (b.preserve) {
                val d = lum - (0.299f * r + 0.587f * g + 0.114f * bl)
                r += d; g += d; bl += d
            }
            out[i] = (a shl 24) or ((r * 255f).roundToInt().coerceIn(0, 255) shl 16) or
                ((g * 255f).roundToInt().coerceIn(0, 255) shl 8) or (bl * 255f).roundToInt().coerceIn(0, 255)
        }
        return out
    }

    private fun blendCh(mode: ClipBlend, a: Float, b: Float): Float = when (mode) {
        ClipBlend.NORMAL, ClipBlend.COLOR -> b
        ClipBlend.MULTIPLY -> a * b
        ClipBlend.SCREEN -> 1f - (1f - a) * (1f - b)
        ClipBlend.OVERLAY -> if (a < 0.5f) 2f * a * b else 1f - 2f * (1f - a) * (1f - b)
        ClipBlend.SOFT_LIGHT -> (1f - 2f * b) * a * a + 2f * b * a
    }

    fun clip(src: IntArray, w: Int, h: Int, clip: PhotoClip): IntArray {
        val out = IntArray(src.size)
        val rad = Math.toRadians(clip.angle.toDouble())
        val dx = cos(rad).toFloat(); val dy = sin(rad).toFloat()
        // Gradyan izdüşümünün uçları (resmin köşelerinden)
        val corners = listOf(0f to 0f, w.toFloat() to 0f, 0f to h.toFloat(), w.toFloat() to h.toFloat()).map { (x, y) -> x * dx + y * dy }
        val p0 = corners.min(); val p1 = corners.max()
        val span = (p1 - p0).coerceAtLeast(1f)
        val c1 = clip.color1; val c2 = clip.color2
        val op = clip.opacity.coerceIn(0f, 1f)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = src[i]
            val a = c ushr 24
            if (a == 0) { out[i] = c; continue }
            val t = if (clip.fill == ClipFill.GRADIENT) ((x * dx + y * dy - p0) / span).coerceIn(0f, 1f) else 0f
            val fr = (((c1 shr 16) and 0xFF) * (1 - t) + ((c2 shr 16) and 0xFF) * t) / 255f
            val fg = (((c1 shr 8) and 0xFF) * (1 - t) + ((c2 shr 8) and 0xFF) * t) / 255f
            val fb = ((c1 and 0xFF) * (1 - t) + (c2 and 0xFF) * t) / 255f
            val r0 = ((c shr 16) and 0xFF) / 255f; val g0 = ((c shr 8) and 0xFF) / 255f; val b0 = (c and 0xFF) / 255f
            var r = blendCh(clip.blend, r0, fr); var g = blendCh(clip.blend, g0, fg); var b = blendCh(clip.blend, b0, fb)
            if (clip.blend == ClipBlend.COLOR) {
                // Renk kipi: dolgunun rengi, resmin parlaklığı
                val d = (0.299f * r0 + 0.587f * g0 + 0.114f * b0) - (0.299f * r + 0.587f * g + 0.114f * b)
                r += d; g += d; b += d
            }
            r = r0 + (r - r0) * op; g = g0 + (g - g0) * op; b = b0 + (b - b0) * op
            out[i] = (a shl 24) or ((r * 255f).roundToInt().coerceIn(0, 255) shl 16) or
                ((g * 255f).roundToInt().coerceIn(0, 255) shl 8) or (b * 255f).roundToInt().coerceIn(0, 255)
        }
        return out
    }

    // ------------------------------------------------------------------ geometri

    /** Döndürülmüş (çeyrek) resmin boyutu. */
    fun orientedSize(w: Int, h: Int, quarter: Int): Pair<Int, Int> = if (quarter % 2 == 1) h to w else w to h

    /** Saat yönünde [quarter]×90° döndürme, ardından çevirmeler. */
    fun orient(src: IntArray, w: Int, h: Int, quarter: Int, flipH: Boolean, flipV: Boolean): Triple<IntArray, Int, Int> {
        val (ow, oh) = orientedSize(w, h, quarter)
        val out = IntArray(ow * oh)
        for (y in 0 until oh) for (x in 0 until ow) {
            val ax = if (flipH) ow - 1 - x else x
            val ay = if (flipV) oh - 1 - y else y
            val (sx, sy) = when (quarter) {
                1 -> ay to (h - 1 - ax)
                2 -> (w - 1 - ax) to (h - 1 - ay)
                3 -> (w - 1 - ay) to ax
                else -> ax to ay
            }
            out[y * ow + x] = src[sy * w + sx]
        }
        return Triple(out, ow, oh)
    }

    /** Eğim düzeltmede köşeler boş kalmasın diye büyütme. */
    fun coverScale(w: Int, h: Int, degrees: Float): Float {
        val a = Math.toRadians(abs(degrees).toDouble())
        val ratio = max(w, h).toDouble() / max(1, min(w, h))
        return (cos(a) + sin(a) * ratio).toFloat()
    }

    fun straighten(src: IntArray, w: Int, h: Int, degrees: Float): IntArray {
        val a = Math.toRadians(degrees.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val s = coverScale(w, h, degrees)
        val cx = w / 2f; val cy = h / 2f
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x + 0.5f - cx) / s; val dy = (y + 0.5f - cy) / s
            // Görüntü [degrees] kadar döner: hedef noktası kaynağa ters döndürmeyle eşlenir
            val sx = cx + dx * ca + dy * sa - 0.5f
            val sy = cy - dx * sa + dy * ca - 0.5f
            out[y * w + x] = bilinear(src, w, h, sx, sy)
        }
        return out
    }

    fun crop(src: IntArray, w: Int, h: Int, c: PhotoCrop): Triple<IntArray, Int, Int> {
        val l = (c.left.coerceIn(0f, 1f) * w).roundToInt().coerceIn(0, w - 1)
        val t = (c.top.coerceIn(0f, 1f) * h).roundToInt().coerceIn(0, h - 1)
        val r = (c.right.coerceIn(0f, 1f) * w).roundToInt().coerceIn(l + 1, w)
        val b = (c.bottom.coerceIn(0f, 1f) * h).roundToInt().coerceIn(t + 1, h)
        val cw = r - l; val ch = b - t
        val out = IntArray(cw * ch)
        for (y in 0 until ch) System.arraycopy(src, (t + y) * w + l, out, y * cw, cw)
        return Triple(out, cw, ch)
    }

    /**
     * Son resmin 0..1 noktasının kaynak resimdeki 0..1 karşılığı (rötuş fırçası son resmin üstünde çizilir, kaynağa
     * işlenir). [w]×[h] kaynak boyutu.
     */
    fun toSource(u: Float, v: Float, w: Int, h: Int, e: PhotoEdit, skipCrop: Boolean = false): Pair<Float, Float> {
        val (ow, oh) = orientedSize(w, h, e.quarter)
        // kırpma
        var bx = if (skipCrop) u else e.crop.left + u * (e.crop.right - e.crop.left)
        var by = if (skipCrop) v else e.crop.top + v * (e.crop.bottom - e.crop.top)
        // eğim düzeltme (piksel)
        var px = bx * ow; var py = by * oh
        if (e.straighten != 0f) {
            val a = Math.toRadians(e.straighten.toDouble())
            val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
            val s = coverScale(ow, oh, e.straighten)
            val cx = ow / 2f; val cy = oh / 2f
            val dx = (px - cx) / s; val dy = (py - cy) / s
            px = cx + dx * ca + dy * sa; py = cy - dx * sa + dy * ca
        }
        // çevirme
        if (e.flipH) px = ow - px
        if (e.flipV) py = oh - py
        // çeyrek döndürme
        val (sx, sy) = when (e.quarter) {
            1 -> py to (h - px)
            2 -> (w - px) to (h - py)
            3 -> (w - py) to px
            else -> px to py
        }
        bx = sx / w; by = sy / h
        return bx to by
    }

    private fun bilinear(src: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val xf = x.coerceIn(0f, (w - 1).toFloat()); val yf = y.coerceIn(0f, (h - 1).toFloat())
        val x0 = floor(xf).toInt(); val y0 = floor(yf).toInt()
        val x1 = min(w - 1, x0 + 1); val y1 = min(h - 1, y0 + 1)
        val fx = xf - x0; val fy = yf - y0
        val c00 = src[y0 * w + x0]; val c10 = src[y0 * w + x1]; val c01 = src[y1 * w + x0]; val c11 = src[y1 * w + x1]
        var out = 0
        for (sh in intArrayOf(24, 16, 8, 0)) {
            val a = (c00 ushr sh and 0xFF) * (1 - fx) + (c10 ushr sh and 0xFF) * fx
            val b = (c01 ushr sh and 0xFF) * (1 - fx) + (c11 ushr sh and 0xFF) * fx
            out = out or (((a * (1 - fy) + b * fy) + 0.5f).toInt().coerceIn(0, 255) shl sh)
        }
        return out
    }

    // ------------------------------------------------------------------ ışık

    fun light(px: IntArray, w: Int, h: Int, l: PhotoLight): IntArray {
        val out = px.copyOf()
        val ev = Math.pow(2.0, l.exposure * 1.5).toFloat()
        // Netlik: yerel karşıtlık (büyük yarıçaplı bulanıklığa göre)
        val base = if (l.clarity != 0f) ImageEffects.blur(px, w, h, max(2, min(w, h) / 40)) else null
        for (i in out.indices) {
            val c = out[i]
            val a = c ushr 24
            if (a == 0) continue
            var r = (c shr 16 and 0xFF) / 255f; var g = (c shr 8 and 0xFF) / 255f; var b = (c and 0xFF) / 255f
            if (base != null) {
                val bc = base[i]
                val k = l.clarity * 0.8f
                r += (r - (bc shr 16 and 0xFF) / 255f) * k
                g += (g - (bc shr 8 and 0xFF) / 255f) * k
                b += (b - (bc and 0xFF) / 255f) * k
            }
            r *= ev; g *= ev; b *= ev
            val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
            // Gölgeler koyu bölgeyi, vurgular açık bölgeyi etkiler (yumuşak ağırlık)
            val sw = (1f - lum).let { it * it }
            val hw = lum * lum
            val delta = l.shadows * 0.45f * sw + l.highlights * 0.45f * hw +
                l.whites * 0.25f * smooth(0.6f, 1f, lum) + l.blacks * 0.25f * (1f - smooth(0f, 0.4f, lum))
            r += delta; g += delta; b += delta
            if (l.vibrance != 0f) {
                val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                val sat = mx - mn
                val k = l.vibrance * (1f - sat) * 1.2f
                val avg = (r + g + b) / 3f
                r += (r - avg) * k; g += (g - avg) * k; b += (b - avg) * k
            }
            out[i] = (a shl 24) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
        }
        return out
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
    private fun to8(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

    // ------------------------------------------------------------------ rötuş fırçaları

    /** Fırça izlerini [px] üstüne uygular ([orig] geri getir fırçasının kaynağı). */
    fun applyStrokes(px: IntArray, orig: IntArray, w: Int, h: Int, strokes: List<PhotoStroke>): IntArray {
        var out = px
        val side = min(w, h).toFloat()
        for (s in strokes) {
            val radius = max(1f, s.size * side / 2f)
            val mask = FloatArray(w * h)
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            val pts = s.points
            val n = pts.size / 2
            fun stamp(cx: Float, cy: Float) {
                val x0 = max(0, (cx - radius).toInt()); val x1 = min(w - 1, (cx + radius).toInt() + 1)
                val y0 = max(0, (cy - radius).toInt()); val y1 = min(h - 1, (cy + radius).toInt() + 1)
                if (x0 > x1 || y0 > y1) return
                minX = min(minX, x0); minY = min(minY, y0); maxX = max(maxX, x1); maxY = max(maxY, y1)
                for (y in y0..y1) for (x in x0..x1) {
                    val d = sqrt((x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy)) / radius
                    if (d >= 1f) continue
                    // yumuşak kenar
                    val v = if (d < 0.6f) 1f else 1f - (d - 0.6f) / 0.4f
                    val i = y * w + x
                    if (v > mask[i]) mask[i] = v
                }
            }
            for (k in 0 until n) {
                val x = pts[2 * k] * w; val y = pts[2 * k + 1] * h
                if (k == 0) { stamp(x, y); continue }
                val px0 = pts[2 * k - 2] * w; val py0 = pts[2 * k - 1] * h
                val dist = sqrt((x - px0) * (x - px0) + (y - py0) * (y - py0))
                val steps = max(1, (dist / max(1f, radius * 0.3f)).toInt())
                for (j in 1..steps) stamp(px0 + (x - px0) * j / steps, py0 + (y - py0) * j / steps)
            }
            if (maxX < 0) continue
            out = when (s.brush) {
                PhotoBrush.BLUR, PhotoBrush.SMOOTH -> {
                    val r = max(1, (radius * if (s.brush == PhotoBrush.BLUR) 0.5f else 0.25f).toInt())
                    val blurred = ImageEffects.blur(out, w, h, r)
                    blend(out, w, minX, minY, maxX, maxY, mask, s.strength) { i, _ -> blurred[i] }
                }
                PhotoBrush.RESTORE -> blend(out, w, minX, minY, maxX, maxY, mask, s.strength) { i, _ -> orig[i] }
                PhotoBrush.PAINT -> blend(out, w, minX, minY, maxX, maxY, mask, s.strength) { _, c -> (c and 0xFF000000.toInt()) or (s.color and 0xFFFFFF) or if (c ushr 24 == 0) 0xFF000000.toInt() else 0 }
                PhotoBrush.LIGHTEN -> blend(out, w, minX, minY, maxX, maxY, mask, s.strength) { _, c -> scale(c, 1.45f, 18f) }
                PhotoBrush.DARKEN -> blend(out, w, minX, minY, maxX, maxY, mask, s.strength) { _, c -> scale(c, 0.6f, 0f) }
            }
        }
        return out
    }

    private fun scale(c: Int, k: Float, add: Float): Int {
        val r = ((c shr 16 and 0xFF) * k + add).roundToInt().coerceIn(0, 255)
        val g = ((c shr 8 and 0xFF) * k + add).roundToInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * k + add).roundToInt().coerceIn(0, 255)
        return (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    private inline fun blend(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, mask: FloatArray, strength: Float, target: (Int, Int) -> Int): IntArray {
        val out = px.copyOf()
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * w + x
            val m = mask[i] * strength.coerceIn(0f, 1f)
            if (m <= 0f) continue
            val c = px[i]
            val t = target(i, c)
            var o = 0
            for (sh in intArrayOf(24, 16, 8, 0)) {
                val a = c ushr sh and 0xFF; val b = t ushr sh and 0xFF
                o = o or (((a + (b - a) * m) + 0.5f).toInt().coerceIn(0, 255) shl sh)
            }
            out[i] = o
        }
        return out
    }

    /** Durum geçmişi için kısa yardımcı: [e]'de kırpma yeni orana göre (en/boy, ör. 1:1) ortalanır. */
    fun cropForAspect(w: Int, h: Int, e: PhotoEdit, aspect: Float?): PhotoCrop {
        if (aspect == null) return PhotoCrop()
        val (ow, oh) = orientedSize(w, h, e.quarter)
        val imgAspect = ow.toFloat() / oh
        return if (aspect > imgAspect) {
            val hh = imgAspect / aspect
            PhotoCrop(0f, (1 - hh) / 2f, 1f, (1 + hh) / 2f)
        } else {
            val ww = aspect / imgAspect
            PhotoCrop((1 - ww) / 2f, 0f, (1 + ww) / 2f, 1f)
        }
    }

    @Suppress("unused") private fun gauss(x: Float) = exp(-x * x)
}
