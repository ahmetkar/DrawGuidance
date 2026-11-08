package com.karapp.drawguidance.ui.drawing.scene

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.model.HarmonySpec
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v43 cihazda sahne çözümlemesi (büyük model, bulut ya da LLM yok; yalnız küçültülmüş piksellerle istatistik):
 *
 * - **Uyum** ([analyze]): nesnenin çevresindeki arka plan halkasının ortalama rengi, parlaklığı, karşıtlığı, doygunluğu
 *   ve keskinliği (Laplace varyansı); ışık yönü (parlaklığın ağırlık merkezinin halkanın ortasına göre yönü) ve nesnenin
 *   altındaki zemin çizgisi (yatay kenar gücünün en büyük olduğu satır). [suggest] bunlardan [HarmonySpec] üretir.
 * - **Mekân** ([perspective]): Sobel kenarları + Hough dönüşümüyle çizgiler; eğik çizgilerin kesişimlerinin
 *   ortancası kaçış noktası, onun yüksekliği ufuk. Eğik çizgi yetmezse en güçlü uzun yatay çizgi, o da yoksa
 *   varsayılan göz hizası (%40) — düşük güvenle.
 */
object SceneAnalyzer {

    class Analysis(
        val bgMean: FloatArray,
        val bgLum: Float,
        val bgContrast: Float,
        val bgSat: Float,
        val bgSharpness: Float,
        val objMean: FloatArray,
        val objLum: Float,
        val objSat: Float,
        val objSharpness: Float,
        /** Işığın geldiği yön (birim vektör, görüntü koordinatı) ve gücü (0–1). */
        val light: Offset,
        val lightStrength: Float,
        /** Nesnenin altındaki zemin çizgisi (tuval y), bulunamazsa null. */
        val groundY: Float?,
    )

    /**
     * [bg]: [region] (tuval) alanının nesne olmadan çizimi; [objBounds]: nesnenin kutusu (tuval); [obj]: nesnenin resmi
     * (alfalı).
     */
    fun analyze(bg: Bitmap, region: Rect, objBounds: Rect, obj: Bitmap): Analysis {
        val w = bg.width; val h = bg.height
        val px = IntArray(w * h).also { bg.getPixels(it, 0, w, 0, 0, w, h) }
        val sx = w / region.width; val sy = h / region.height
        val ox0 = ((objBounds.left - region.left) * sx).toInt(); val ox1 = ((objBounds.right - region.left) * sx).toInt()
        val oy0 = ((objBounds.top - region.top) * sy).toInt(); val oy1 = ((objBounds.bottom - region.top) * sy).toInt()
        fun inObj(x: Int, y: Int) = x in ox0..ox1 && y in oy0..oy1
        val lum = FloatArray(w * h)
        var r = 0.0; var g = 0.0; var b = 0.0; var n = 0; var lsum = 0.0; var satSum = 0.0
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            val cr = ((c shr 16) and 0xFF) / 255f; val cg = ((c shr 8) and 0xFF) / 255f; val cb = (c and 0xFF) / 255f
            val l = 0.299f * cr + 0.587f * cg + 0.114f * cb
            lum[y * w + x] = l
            if (inObj(x, y)) continue
            r += cr; g += cg; b += cb; lsum += l; satSum += sat(cr, cg, cb); n++
        }
        n = max(n, 1)
        val bgMean = floatArrayOf((r / n).toFloat(), (g / n).toFloat(), (b / n).toFloat())
        val bgLum = (lsum / n).toFloat()
        var varSum = 0.0
        for (y in 0 until h) for (x in 0 until w) if (!inObj(x, y)) { val d = lum[y * w + x] - bgLum; varSum += d * d }
        val bgContrast = sqrt(varSum / n).toFloat()
        // Işık: parlaklığın ortalamadan sapmasıyla ağırlıklı yön (halkanın ortasına göre)
        val cx = (ox0 + ox1) / 2f; val cy = (oy0 + oy1) / 2f
        var vx = 0.0; var vy = 0.0; var wsum = 0.0
        for (y in 0 until h) for (x in 0 until w) {
            if (inObj(x, y)) continue
            val d = lum[y * w + x] - bgLum
            val dx = x - cx; val dy = y - cy
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            vx += d * dx / dist; vy += d * dy / dist; wsum += abs(d)
        }
        var light = Offset(-0.6f, -0.8f)
        var strength = 0f
        if (wsum > 1e-6) {
            val lx = (vx / wsum).toFloat(); val ly = (vy / wsum).toFloat()
            val m = hypot(lx, ly)
            if (m > 0.05f) { light = Offset(lx / m, ly / m); strength = (m * 2f).coerceIn(0f, 1f) }
        }
        val bgSharp = laplaceVariance(lum, w, h) { x, y -> !inObj(x, y) }
        // Zemin: nesnenin alt kenarı çevresinde (±%25 boy) yatay kenar gücü en büyük satır
        val objH = (oy1 - oy0).coerceAtLeast(1)
        var bestY = -1; var best = 0f
        val yFrom = (oy1 - objH / 4).coerceIn(1, h - 2); val yTo = (oy1 + objH / 4).coerceIn(1, h - 2)
        for (y in yFrom..yTo) {
            var e = 0f
            for (x in max(0, ox0 - objH / 2) until min(w, ox1 + objH / 2)) e += abs(lum[(y + 1) * w + x] - lum[(y - 1) * w + x])
            if (e > best) { best = e; bestY = y }
        }
        val strongEdge = best > 0.08f * (ox1 - ox0 + objH).coerceAtLeast(1)
        val groundY = if (bestY >= 0 && strongEdge) region.top + bestY / sy else null
        // Nesne: alfayla ağırlıklı ortalama
        val ow = obj.width; val oh = obj.height
        val step = max(1, max(ow, oh) / 256)
        var or = 0.0; var og = 0.0; var ob = 0.0; var aw = 0.0; var osat = 0.0
        val small = Bitmap.createScaledBitmap(obj, max(1, ow / step), max(1, oh / step), true)
        val sw = small.width; val sh = small.height
        val opx = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
        if (small !== obj) small.recycle()
        val olum = FloatArray(sw * sh)
        for (i in opx.indices) {
            val c = opx[i]
            val a = (c ushr 24) / 255f
            val cr = ((c shr 16) and 0xFF) / 255f; val cg = ((c shr 8) and 0xFF) / 255f; val cb = (c and 0xFF) / 255f
            olum[i] = 0.299f * cr + 0.587f * cg + 0.114f * cb
            if (a < 0.1f) continue
            or += cr * a; og += cg * a; ob += cb * a; osat += sat(cr, cg, cb) * a; aw += a
        }
        aw = max(aw, 1e-6)
        val objMean = floatArrayOf((or / aw).toFloat(), (og / aw).toFloat(), (ob / aw).toFloat())
        val objSharp = laplaceVariance(olum, sw, sh) { x, y -> (opx[y * sw + x] ushr 24) > 200 }
        return Analysis(
            bgMean, bgLum, bgContrast, (satSum / n).toFloat(), bgSharp, objMean,
            0.299f * objMean[0] + 0.587f * objMean[1] + 0.114f * objMean[2], (osat / aw).toFloat(), objSharp,
            light, strength, groundY,
        )
    }

    /** Uyum seçenekleri: hangi etkiler ve yoğunluk. */
    data class Options(val shadow: Boolean = true, val color: Boolean = true, val edge: Boolean = true, val position: Boolean = false, val intensity: Float = 0.8f)

    /** Çözümlemeden ayarlar. */
    fun suggest(a: Analysis, o: Options): HarmonySpec {
        fun gain(i: Int): Float {
            val bgC = a.bgMean[i] / a.bgLum.coerceAtLeast(0.02f)
            val obC = a.objMean[i] / a.objLum.coerceAtLeast(0.02f)
            val ratio = if (obC < 1e-3f) 1f else bgC / obC
            return (1f + (ratio - 1f) * 0.35f).coerceIn(0.75f, 1.3f)
        }
        val lift = ((a.bgLum - a.objLum) * 0.35f).coerceIn(-0.2f, 0.2f)
        val satRatio = if (a.objSat < 0.02f) 1f else a.bgSat / a.objSat
        val saturation = (1f + (satRatio - 1f) * 0.4f).coerceIn(0.7f, 1.15f)
        val lx = a.light.x; val ly = a.light.y
        val opacity = ((0.2f + a.bgContrast * 1.2f).coerceIn(0.2f, 0.55f) * (0.6f + 0.4f * a.lightStrength)).coerceIn(0.15f, 0.55f)
        val shadowColor = hex(a.bgMean[0] * 0.22f, a.bgMean[1] * 0.22f, a.bgMean[2] * 0.25f)
        val blurrier = a.bgSharpness < a.objSharpness * 0.6f
        return HarmonySpec(
            intensity = o.intensity.coerceIn(0f, 1f),
            color = o.color,
            gainR = gain(0), gainG = gain(1), gainB = gain(2), lift = lift, saturation = saturation,
            shadow = o.shadow,
            shadowColorHex = shadowColor, shadowOpacity = opacity,
            shadowDx = -lx * 0.02f, shadowDy = 0.01f,
            shadowBlur = if (blurrier) 0.045f else 0.03f,
            // Işık soldan (lx < 0) gelince gölge sağa yatar; ışık tepeden gelince gölge kısalır
            castSkew = (lx * 0.9f).coerceIn(-1.2f, 1.2f),
            castScaleY = (0.15f + 0.35f * (1f - abs(ly))).coerceIn(0.12f, 0.6f),
            contact = true,
            edge = o.edge,
            feather = if (blurrier) 0.012f else 0.005f,
            wrap = (0.25f + 0.2f * a.lightStrength).coerceIn(0f, 0.6f),
            wrapColorHex = hex(a.bgMean[0], a.bgMean[1], a.bgMean[2]),
            lightAngle = Math.toDegrees(atan2(ly.toDouble(), lx.toDouble())).toFloat(),
        )
    }

    // ------------------------------------------------------------------ perspektif

    class Perspective(
        /** Ufuk (tuval y). */
        val horizonY: Float,
        /** Kaçış noktası (tuval), bulunamadıysa null. */
        val vanishing: Offset?,
        /** 0–1: 1 kaçış noktası tutarlı, 0.5 yatay çizgi, 0.2 varsayılan. */
        val confidence: Float,
        val lines: Int,
    )

    /** [bg]: tuvalin tamamının (nesne olmadan) küçültülmüş çizimi; [canvasW]/[canvasH] tuval boyu. */
    fun perspective(bg: Bitmap, canvasW: Float, canvasH: Float): Perspective {
        val w = bg.width; val h = bg.height
        val px = IntArray(w * h).also { bg.getPixels(it, 0, w, 0, 0, w, h) }
        val lum = FloatArray(w * h) { val c = px[it]; (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f }
        val mag = FloatArray(w * h); val ang = FloatArray(w * h)
        var maxMag = 0f
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = (lum[i - w + 1] + 2 * lum[i + 1] + lum[i + w + 1]) - (lum[i - w - 1] + 2 * lum[i - 1] + lum[i + w - 1])
            val gy = (lum[i + w - 1] + 2 * lum[i + w] + lum[i + w + 1]) - (lum[i - w - 1] + 2 * lum[i - w] + lum[i - w + 1])
            mag[i] = hypot(gx, gy); ang[i] = atan2(gy, gx)
            if (mag[i] > maxMag) maxMag = mag[i]
        }
        val sx = canvasW / w; val sy = canvasH / h
        val fallback = Perspective(canvasH * 0.4f, null, 0.2f, 0)
        if (maxMag < 1e-3f) return fallback
        val thr = maxMag * 0.25f
        // Hough: θ 0..180 (1°), ρ ±diag
        val thetas = 180
        val diag = hypot(w.toFloat(), h.toFloat()).toInt() + 1
        val acc = IntArray(thetas * (2 * diag + 1))
        val cosT = FloatArray(thetas) { cos(Math.toRadians(it.toDouble())).toFloat() }
        val sinT = FloatArray(thetas) { sin(Math.toRadians(it.toDouble())).toFloat() }
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (mag[i] < thr) continue
            // Kenarın normali yönünde ±10° (gürültüyü azaltır)
            var nd = Math.toDegrees(ang[i].toDouble()).toInt()
            if (nd < 0) nd += 180
            for (d in -10..10) {
                val t = ((nd + d) % 180 + 180) % 180
                val rho = (x * cosT[t] + y * sinT[t]).toInt() + diag
                acc[t * (2 * diag + 1) + rho]++
            }
        }
        // En güçlü çizgiler (yerel en büyükler)
        data class Line(val theta: Int, val rho: Float, val votes: Int)
        val minVotes = (min(w, h) * 0.35f).toInt()
        val lines = ArrayList<Line>()
        val cols = 2 * diag + 1
        val order = acc.indices.filter { acc[it] >= minVotes }.sortedByDescending { acc[it] }
        for (k in order) {
            val t = k / cols; val r = k % cols - diag
            if (lines.any { abs(it.theta - t).let { d -> min(d, 180 - d) } < 6 && abs(it.rho - r) < diag * 0.04f }) continue
            lines += Line(t, r.toFloat(), acc[k])
            if (lines.size >= 24) break
        }
        if (lines.isEmpty()) return fallback
        // Eğik çizgiler (yataydan ve dikeyden en az 12° uzak): kesişimleri
        val oblique = lines.filter { val t = it.theta; val fromVertical = min(t, 180 - t); val fromHorizontal = abs(t - 90); fromVertical > 12 && fromHorizontal > 12 }
        val points = ArrayList<Offset>()
        for (i in oblique.indices) for (j in i + 1 until oblique.size) {
            val a = oblique[i]; val b = oblique[j]
            if (abs(a.theta - b.theta) < 8) continue
            val det = cosT[a.theta] * sinT[b.theta] - sinT[a.theta] * cosT[b.theta]
            if (abs(det) < 1e-3f) continue
            val x = (a.rho * sinT[b.theta] - b.rho * sinT[a.theta]) / det
            val y = (cosT[a.theta] * b.rho - cosT[b.theta] * a.rho) / det
            if (x < -w * 0.5f || x > w * 1.5f || y < -h * 0.3f || y > h * 1.0f) continue
            points += Offset(x, y)
        }
        if (points.size >= 3) {
            val mx = points.map { it.x }.sorted()[points.size / 2]
            val my = points.map { it.y }.sorted()[points.size / 2]
            // Tutarlılık: kesişimlerin yarısı ortancaya yakın mı
            val near = points.count { hypot(it.x - mx, it.y - my) < max(w, h) * 0.15f }
            if (near * 2 >= points.size) {
                return Perspective((my * sy).coerceIn(0f, canvasH), Offset(mx * sx, my * sy), (0.5f + 0.5f * near / points.size).coerceAtMost(1f), lines.size)
            }
        }
        // Uzun yatay çizgi (üst %70'te): ufuk/zemin sınırı
        val horizontal = lines.filter { abs(it.theta - 90) <= 4 }.filter { it.rho in h * 0.1f..h * 0.7f }
        horizontal.maxByOrNull { it.votes }?.let { return Perspective(it.rho * sy, null, 0.5f, lines.size) }
        return Perspective(fallback.horizonY, null, 0.2f, lines.size)
    }

    /** Laplace varyansı (keskinlik); [use] dışındaki pikseller sayılmaz. */
    private inline fun laplaceVariance(l: FloatArray, w: Int, h: Int, use: (Int, Int) -> Boolean): Float {
        var s = 0.0; var s2 = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            if (!use(x, y)) continue
            val i = y * w + x
            val v = 4 * l[i] - l[i - 1] - l[i + 1] - l[i - w] - l[i + w]
            s += v; s2 += v * v; n++
        }
        if (n == 0) return 0f
        val m = s / n
        return (s2 / n - m * m).toFloat()
    }

    private fun sat(r: Float, g: Float, b: Float): Float {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        return if (mx <= 1e-4f) 0f else (mx - mn) / mx
    }

    fun hex(r: Float, g: Float, b: Float): String =
        "#%02X%02X%02X".format((r * 255).toInt().coerceIn(0, 255), (g * 255).toInt().coerceIn(0, 255), (b * 255).toInt().coerceIn(0, 255))
}

/**
 * v43 "Nesneyi Mekâna Yerleştir": ufka göre perspektif ölçeği. Zemindeki bir nesnenin ekrandaki boyu ayağının ufka
 * uzaklığıyla orantılıdır (aynı gerçek boy için: boy = k × (ayak − ufuk)). k, sahnedeki öteki nesnelerden (referans;
 * ortanca) ya da nesnenin şimdiki hâlinden alınır; ayak ufkun üstündeyse nesne zemine indirilir. Oran korunur; ölçek
 * sınırlıdır.
 */
object ScenePlacement {
    data class Reference(val footY: Float, val height: Float)

    data class Result(val footX: Float, val footY: Float, val scale: Float, val k: Float, val references: Int)

    const val MIN_SCALE = 0.25f
    const val MAX_SCALE = 4f

    /**
     * [obj]: nesnenin kutusu (tuval); [horizonY]: ufuk; [refs]: zemindeki öteki nesneler; [footOverride]: elle
     * verilen ayak yüksekliği; [scaleMul]: elle ek ölçek.
     */
    fun place(obj: Rect, horizonY: Float, canvasH: Float, refs: List<Reference>, footOverride: Float? = null, scaleMul: Float = 1f): Result {
        val minGap = canvasH * 0.03f
        val foot0 = obj.bottom
        val footY = (footOverride ?: if (foot0 > horizonY + minGap) foot0 else horizonY + 0.35f * (canvasH - horizonY))
            .coerceIn(horizonY + minGap, canvasH)
        val valid = refs.filter { it.footY > horizonY + minGap && it.height > 1f }.map { it.height / (it.footY - horizonY) }.sorted()
        val k = when {
            valid.isNotEmpty() -> valid[valid.size / 2]
            foot0 > horizonY + minGap -> obj.height / (foot0 - horizonY)
            else -> 0.9f
        }
        val targetH = (k * (footY - horizonY) * scaleMul).coerceIn(canvasH * 0.02f, canvasH * 0.95f)
        val scale = (targetH / obj.height.coerceAtLeast(1f)).coerceIn(MIN_SCALE, MAX_SCALE)
        return Result(obj.center.x, footY, scale, k, valid.size)
    }
}
