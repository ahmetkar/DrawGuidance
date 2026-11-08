package com.karapp.drawguidance.ui.drawing.scene

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import com.karapp.drawguidance.data.model.HarmonySpec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v43 "Arka planla uyumla"nın çizimi (yıkıcı olmayan): resmin özgün dosyası değişmez; [HarmonySpec] çizim anında
 * uygulanır.
 *
 * - **Renk/ışık** ve **kenar** (yumuşatma, ışık sarması) piksel işlemiyle bir kez hesaplanır ve önbellekte tutulur
 *   (en çok 1024 piksel; anahtar: resim + ayarlar). Bulanıklaştırma iki geçişli kutu bulanıklığıdır (O(n), yarıçaptan
 *   bağımsız).
 * - **Gölge** her çizimde: işlenmiş resmin alfa maskesi ([Bitmap.extractAlpha]) ayak çizgisinden yatırılıp basılır,
 *   [BlurMaskFilter] ile yumuşatılır; ayağın altına radyal temas gölgesi.
 */
object HarmonyRenderer {
    const val MAX_SIDE = 1024

    class Processed(val bitmap: Bitmap, val alpha: Bitmap, val foot: Float)

    private val cache = object : LruCache<String, Processed>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Processed): Int = value.bitmap.byteCount + value.alpha.byteCount
    }

    fun clear() = cache.evictAll()

    /** İşlenmiş resim (önbellekten). */
    fun processed(src: Bitmap, key: String?, spec: HarmonySpec): Processed {
        val k = "${key ?: System.identityHashCode(src)}:${src.width}x${src.height}:${spec.hashCode()}"
        cache.get(k)?.let { if (!it.bitmap.isRecycled) return it }
        val bmp = process(src, spec)
        val p = Processed(bmp, bmp.extractAlpha(), footY(bmp))
        cache.put(k, p)
        return p
    }

    /** Renk/ışık ve kenar ayarları uygulanmış kopya (en çok [MAX_SIDE]). */
    fun process(src: Bitmap, spec: HarmonySpec): Bitmap {
        val scale = min(1f, MAX_SIDE.toFloat() / max(src.width, src.height))
        val w = max(1, (src.width * scale).roundToInt())
        val h = max(1, (src.height * scale).roundToInt())
        val base = if (scale < 1f) Bitmap.createScaledBitmap(src, w, h, true) else src.copy(Bitmap.Config.ARGB_8888, false)
        val px = IntArray(w * h)
        base.getPixels(px, 0, w, 0, 0, w, h)
        if (base !== src) base.recycle()
        val t = spec.intensity.coerceIn(0f, 1f)
        if (spec.color && t > 0f) applyColor(px, spec, t)
        if (spec.edge && t > 0f && (spec.feather > 0f || spec.wrap > 0f)) applyEdge(px, w, h, spec, t)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Kanal kazancı, parlaklık kayması ve doygunluk ([t] ile özgünle karışık). */
    fun applyColor(px: IntArray, spec: HarmonySpec, t: Float) {
        val gr = 1f + (spec.gainR - 1f) * t
        val gg = 1f + (spec.gainG - 1f) * t
        val gb = 1f + (spec.gainB - 1f) * t
        val lift = spec.lift * 255f * t
        val sat = 1f + (spec.saturation - 1f) * t
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 0) continue
            var r = ((c shr 16) and 0xFF) * gr + lift
            var g = ((c shr 8) and 0xFF) * gg + lift
            var b = (c and 0xFF) * gb + lift
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            r = l + (r - l) * sat; g = l + (g - l) * sat; b = l + (b - l) * sat
            px[i] = (a shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
        }
    }

    /** Kenar yumuşatma (alfa, bulanık alfayla çarpılır) ve ışık sarması (kenar bandına arka plan rengi). */
    fun applyEdge(px: IntArray, w: Int, h: Int, spec: HarmonySpec, t: Float) {
        val alpha = IntArray(px.size) { px[it] ushr 24 }
        val side = min(w, h).toFloat()
        val featherR = (spec.feather * side).roundToInt()
        val soft = if (featherR >= 1) blur(alpha, w, h, featherR) else null
        val wrapR = max(2, (0.02f * side).roundToInt())
        val band = if (spec.wrap > 0f) blur(alpha, w, h, wrapR) else null
        val wc = runCatching { android.graphics.Color.parseColor(spec.wrapColorHex) }.getOrDefault(0xFFFFFFFF.toInt())
        val wr = (wc shr 16) and 0xFF; val wg = (wc shr 8) and 0xFF; val wb = wc and 0xFF
        for (i in px.indices) {
            val a = alpha[i]
            if (a == 0) continue
            val c = px[i]
            var r = (c shr 16) and 0xFF; var g = (c shr 8) and 0xFF; var b = c and 0xFF
            var na = a
            if (band != null) {
                // Kenarın içindeki bant: kendi alfası bulanık alfasından büyük olan yerler
                val k = ((a - band[i]).coerceAtLeast(0) / 255f * 2f).coerceAtMost(1f) * spec.wrap * t
                if (k > 0f) {
                    r = (r + (wr - r) * k).roundToInt(); g = (g + (wg - g) * k).roundToInt(); b = (b + (wb - b) * k).roundToInt()
                }
            }
            if (soft != null) na = (a + (a * soft[i] / 255f - a) * t).roundToInt().coerceIn(0, 255)
            px[i] = (na shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }
    }

    /** İki geçişli kutu bulanıklığı (yatay + dikey, iki kez: yaklaşık Gauss). */
    fun blur(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        var a = src.copyOf()
        val tmp = IntArray(a.size)
        repeat(2) {
            boxH(a, tmp, w, h, radius)
            boxV(tmp, a, w, h, radius)
        }
        return a
    }

    private fun boxH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (x in -r..r) sum += src[row + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / div
                sum += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
    }

    private fun boxV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (y in -r..r) sum += src[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / div
                sum += src[(y + r + 1).coerceAtMost(h - 1) * w + x] - src[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }

    private fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, 255)

    /**
     * Uyumlu resmi çizer: önce gölgeler, sonra işlenmiş resim. [matrix] özgün resmin (src) piksellerinden tuvale;
     * [alpha] elemanın saydamlığı (0–255).
     */
    fun draw(native: Canvas, src: Bitmap, key: String?, spec: HarmonySpec, matrix: Matrix, alpha: Int) {
        val p = processed(src, key, spec)
        val m = Matrix(matrix).apply { preScale(src.width.toFloat() / p.bitmap.width, src.height.toFloat() / p.bitmap.height) }
        if (spec.shadow && spec.intensity > 0f) drawShadows(native, p, spec, m, alpha)
        native.drawBitmap(p.bitmap, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha })
    }

    private fun drawShadows(native: Canvas, p: Processed, spec: HarmonySpec, m: Matrix, alpha: Int) {
        val w = p.bitmap.width.toFloat(); val h = p.bitmap.height.toFloat()
        val t = spec.intensity.coerceIn(0f, 1f)
        val color = runCatching { android.graphics.Color.parseColor(spec.shadowColorHex) }.getOrDefault(0xFF000000.toInt()) and 0x00FFFFFF
        val a = (spec.shadowOpacity.coerceIn(0f, 1f) * t * alpha).roundToInt().coerceIn(0, 255)
        if (a == 0) return
        val foot = p.foot
        native.save()
        native.concat(m)
        // Temas gölgesi: ayağın altında yassı radyal leke
        if (spec.contact) {
            val rx = w * 0.38f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(0f, 0f, 1f, intArrayOf((min(255, a * 2) shl 24) or color, color), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            }
            native.save()
            native.translate(w / 2f, foot)
            native.scale(rx, max(2f, h * 0.035f))
            native.drawCircle(0f, 0f, 1f, paint)
            native.restore()
        }
        // Düşen gölge: siluet ayak çizgisinden yatırılır ve basılır
        native.save()
        native.translate(spec.shadowDx * w, spec.shadowDy * h)
        native.translate(w / 2f, foot)
        native.skew(spec.castSkew, 0f)
        native.scale(1f, spec.castScaleY.coerceIn(0.05f, 1f))
        native.translate(-w / 2f, -foot)
        val blurPx = max(0.5f, spec.shadowBlur * max(w, h))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.color = (a shl 24) or color
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        native.drawBitmap(p.alpha, 0f, 0f, paint)
        native.restore()
        native.restore()
    }

    /** Siluetin en alt dolu satırı (ayak çizgisi); resmin alt boşluğu gölgeyi havada bırakmasın. */
    fun footY(bmp: Bitmap): Float {
        val w = bmp.width; val h = bmp.height
        val buf = IntArray(w)
        for (y in h - 1 downTo 0) {
            bmp.getPixels(buf, 0, w, 0, y, w, 1)
            var n = 0
            for (x in 0 until w) if ((buf[x] ushr 24) > 40) n++
            if (n >= max(1, w / 100)) return (y + 1).toFloat()
        }
        return h.toFloat()
    }
}
