package com.karapp.drawguidance.content.image

import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * v30 arka plan kaldırma ayarları. Dokunma noktaları resme göre oranlıdır (0..1), böylece önizleme ve tam boyut aynı
 * sonucu verir.
 */
data class BackgroundOptions(
    /** 0..1: arka plan sayılan renk aralığının genişliği. */
    val tolerance: Float = 0.5f,
    /** 0..1: kenar yumuşaklığı. */
    val feather: Float = 0.4f,
    /** Kenara değmeyen ama arka plan renginde olan iç boşluklar da (harf içleri, kol arası) kaldırılır. */
    val removeHoles: Boolean = true,
    /** Kenardan otomatik bulma (kapalıyken yalnız dokunulan yerler kaldırılır). */
    val auto: Boolean = true,
    /** Dokunarak kaldırılan bölgeler (o noktaya bağlı, benzer renkli alan). */
    val removeAt: List<Pair<Float, Float>> = emptyList(),
    /** Dokunarak geri getirilen bölgeler. */
    val keepAt: List<Pair<Float, Float>> = emptyList(),
)

/** Maskenin özeti (arayüzde gösterilir). */
data class BackgroundMask(val w: Int, val h: Int, val alpha: FloatArray, val removedRatio: Float, val background: IntArray)

/**
 * v30 arka plan kaldırma (saf Kotlin, cihazda; ağ ve model gerekmez):
 *
 * 1. Kenar şeridindeki renkler Lab uzayında k-ortalamalarla 3 kümeye ayrılır; payı küçük kümeler (kenara değen
 *    konunun renkleri) atılır, kalanlar arka plan renkleridir. Alt kenar konular sık değdiği için yarı ağırlıklıdır.
 * 2. Arka plan rengine yakın kenar pikselleri tohum olur; bölge büyütme komşuya, hem arka plan rengine uzaklık hem de
 *    yerel renk adımı eşiğin altındaysa geçer (yumuşak gradyanlı arka planlar da izlenir, keskin kenarda durur).
 * 3. İsteğe bağlı: kenara değmeyen, arka plan renginde büyükçe alanlar (iç boşluklar) da kaldırılır.
 * 4. Dokunma noktaları: o noktaya bağlı benzer renkli alan kaldırılır ya da geri getirilir.
 * 5. Temizlik: çok küçük kalıntı adacıklar silinir, küçük delikler doldurulur.
 * 6. Kenar yumuşatma (maske bulanıklığı) ve kenar renginden arka plan renginin ayrıştırılması (renk sızması olmaz).
 *
 * Maske en fazla [WORK_SIDE] kenarlı çalışma boyutunda hesaplanır ve tam boyuta çift doğrusal büyütülür.
 */
object BackgroundRemover {
    const val WORK_SIDE = 900

    /** [src] (w×h ARGB) için maskeyi hesaplar (çalışma boyutunda). */
    fun mask(src: IntArray, w: Int, h: Int, opt: BackgroundOptions, cancelled: () -> Boolean = { false }): BackgroundMask {
        require(src.size >= w * h && w > 0 && h > 0)
        val scale = min(1f, WORK_SIDE.toFloat() / max(w, h))
        val ww = max(1, (w * scale).roundToInt()); val wh = max(1, (h * scale).roundToInt())
        val small = if (scale < 1f) resample(src, w, h, ww, wh) else src
        val n = ww * wh
        val lab = FloatArray(n * 3)
        for (i in 0 until n) toLab(small[i], lab, i * 3)
        val srcAlpha = IntArray(n) { small[it] ushr 24 }
        val tol = opt.tolerance.coerceIn(0f, 1f)
        val tGlobal = 7f + tol * 38f
        val tLocal = 2.5f + tol * 9f

        // 1. Arka plan renkleri
        val centers = if (opt.auto) borderCenters(lab, srcAlpha, ww, wh) else emptyList()
        // 0: bilinmiyor/ön plan, 1: arka plan
        val bg = ByteArray(n)
        // Zaten saydam pikseller arka plandır
        for (i in 0 until n) if (srcAlpha[i] < 8) bg[i] = 1

        fun distBg(i: Int): Float {
            var best = Float.MAX_VALUE
            for (c in centers) {
                val d = dist(lab, i, c)
                if (d < best) best = d
            }
            return best
        }

        val queue = IntArray(n)
        // 2. Kenardan bölge büyütme
        if (centers.isNotEmpty()) {
            var head = 0; var tail = 0
            fun seed(i: Int) { if (bg[i].toInt() == 0 && distBg(i) < tGlobal) { bg[i] = 1; queue[tail++] = i } }
            for (x in 0 until ww) { seed(x); seed((wh - 1) * ww + x) }
            for (y in 0 until wh) { seed(y * ww); seed(y * ww + ww - 1) }
            // Saydam kenar pikselleri de büyütmeyi başlatır
            for (i in 0 until n) if (srcAlpha[i] < 8 && tail < n) { queue[tail++] = i }
            while (head < tail) {
                if (head % 65536 == 0 && cancelled()) break
                val p = queue[head++]
                val x = p % ww; val y = p / ww
                for (k in 0 until 4) {
                    val nx = x + DX[k]; val ny = y + DY[k]
                    if (nx < 0 || ny < 0 || nx >= ww || ny >= wh) continue
                    val q = ny * ww + nx
                    if (bg[q].toInt() != 0) continue
                    val step = dist2(lab, p, q)
                    val d = distBg(q)
                    if ((d < tGlobal && step < tLocal * 3f) || (step < tLocal && d < tGlobal * 2.2f)) { bg[q] = 1; queue[tail++] = q }
                }
            }
            // 3. İç boşluklar
            if (opt.removeHoles) removeHoles(lab, bg, ww, wh, centers, tGlobal * 0.75f, max(12, n / 2500), queue)
        }

        // 4. Dokunma noktaları (önce kaldırma, sonra geri getirme)
        for ((u, v) in opt.removeAt) flood(lab, bg, ww, wh, u, v, target = 1, tGlobal = tGlobal * 1.1f, tLocal = tLocal * 1.2f, queue)
        for ((u, v) in opt.keepAt) flood(lab, bg, ww, wh, u, v, target = 0, tGlobal = tGlobal * 1.1f, tLocal = tLocal * 1.2f, queue)

        // 5. Temizlik: kalıntı adacıklar ve küçük delikler
        val minArea = max(6, n / 1500)
        cleanComponents(bg, ww, wh, value = 0, maxArea = minArea, queue)
        cleanComponents(bg, ww, wh, value = 1, maxArea = max(4, n / 6000), queue, borderSafe = true)
        // Saydam kaynak pikseller her durumda arka plan
        for (i in 0 until n) if (srcAlpha[i] < 8) bg[i] = 1

        // 6. Yumuşak kenar
        val alpha = FloatArray(n) { if (bg[it].toInt() == 1) 0f else 1f }
        val radius = (0.6f + opt.feather.coerceIn(0f, 1f) * 2.4f) * max(1f, max(ww, wh) / 900f)
        val soft = softEdges(alpha, ww, wh, radius)
        var removed = 0
        for (i in 0 until n) if (soft[i] < 0.5f) removed++
        val bgColors = IntArray(centers.size) { labToRgb(centers[it]) }
        return BackgroundMask(ww, wh, soft, removed / n.toFloat(), bgColors)
    }

    /** Maskeyi [w]×[h] tam boyutlu resme uygular; kenar renklerinden arka plan rengi ayrıştırılır. */
    fun apply(src: IntArray, w: Int, h: Int, mask: BackgroundMask): IntArray {
        val out = IntArray(w * h)
        val sx = mask.w / w.toFloat(); val sy = mask.h / h.toFloat()
        val bgs = mask.background
        for (y in 0 until h) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (mask.h - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(mask.h - 1, y0 + 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (mask.w - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(mask.w - 1, x0 + 1); val tx = fx - x0
                val a00 = mask.alpha[y0 * mask.w + x0]; val a10 = mask.alpha[y0 * mask.w + x1]
                val a01 = mask.alpha[y1 * mask.w + x0]; val a11 = mask.alpha[y1 * mask.w + x1]
                val m = (a00 * (1 - tx) + a10 * tx) * (1 - ty) + (a01 * (1 - tx) + a11 * tx) * ty
                val i = y * w + x
                val c = src[i]
                if (m <= 0.004f) { out[i] = 0; continue }
                val a = ((c ushr 24) * m).roundToInt().coerceIn(0, 255)
                if (m >= 0.996f || bgs.isEmpty()) { out[i] = (a shl 24) or (c and 0xFFFFFF); continue }
                // Kenar pikseli: c = m·ön + (1−m)·arka → ön = (c − (1−m)·arka) / m (en yakın arka plan rengiyle)
                val bgc = nearestRgb(bgs, c)
                fun un(v: Int, b: Int): Int = ((v - (1 - m) * b) / m).roundToInt().coerceIn(0, 255)
                out[i] = (a shl 24) or (un(c shr 16 and 0xFF, bgc shr 16 and 0xFF) shl 16) or
                    (un(c shr 8 and 0xFF, bgc shr 8 and 0xFF) shl 8) or un(c and 0xFF, bgc and 0xFF)
            }
        }
        return out
    }

    /** Tek adımda: maske + uygulama. */
    fun remove(src: IntArray, w: Int, h: Int, opt: BackgroundOptions = BackgroundOptions(), cancelled: () -> Boolean = { false }): IntArray =
        apply(src, w, h, mask(src, w, h, opt, cancelled))

    // --- Yardımcılar ---

    private val DX = intArrayOf(1, -1, 0, 0)
    private val DY = intArrayOf(0, 0, 1, -1)

    private fun nearestRgb(bgs: IntArray, c: Int): Int {
        var best = bgs[0]; var bd = Int.MAX_VALUE
        for (b in bgs) {
            val dr = (b shr 16 and 0xFF) - (c shr 16 and 0xFF); val dg = (b shr 8 and 0xFF) - (c shr 8 and 0xFF); val db = (b and 0xFF) - (c and 0xFF)
            val d = dr * dr + dg * dg + db * db
            if (d < bd) { bd = d; best = b }
        }
        return best
    }

    private fun dist(lab: FloatArray, i: Int, c: FloatArray): Float {
        val dl = lab[i * 3] - c[0]; val da = lab[i * 3 + 1] - c[1]; val db = lab[i * 3 + 2] - c[2]
        return sqrt(dl * dl + da * da + db * db)
    }

    private fun dist2(lab: FloatArray, i: Int, j: Int): Float {
        val dl = lab[i * 3] - lab[j * 3]; val da = lab[i * 3 + 1] - lab[j * 3 + 1]; val db = lab[i * 3 + 2] - lab[j * 3 + 2]
        return sqrt(dl * dl + da * da + db * db)
    }

    /** Kenar şeridi renklerinden arka plan renk merkezleri (Lab). */
    internal fun borderCenters(lab: FloatArray, alpha: IntArray, w: Int, h: Int): List<FloatArray> {
        val band = max(1, min(w, h) / 50)
        val samples = ArrayList<Int>()
        val weights = ArrayList<Float>()
        val stride = max(1, (2 * (w + h) * band) / 6000)
        var k = 0
        for (y in 0 until h) for (x in 0 until w) {
            val edge = x < band || y < band || x >= w - band || y >= h - band
            if (!edge) continue
            if (k++ % stride != 0) continue
            val i = y * w + x
            if (alpha[i] < 8) continue
            samples += i
            weights += if (y >= h - band && x >= band && x < w - band) 0.5f else 1f
        }
        if (samples.isEmpty()) return emptyList()
        // k-ortalamalar (k=3), en parlak, en koyu ve ortanca örnekten başlanır
        val kk = min(3, samples.size)
        val sorted = samples.sortedBy { lab[it * 3] }
        val centers = Array(kk) { j -> val s = sorted[(j * (sorted.size - 1)) / max(1, kk - 1)]; floatArrayOf(lab[s * 3], lab[s * 3 + 1], lab[s * 3 + 2]) }
        val assign = IntArray(samples.size)
        repeat(8) {
            for ((si, s) in samples.withIndex()) {
                var best = 0; var bd = Float.MAX_VALUE
                for (c in 0 until kk) { val d = dist(lab, s, centers[c]); if (d < bd) { bd = d; best = c } }
                assign[si] = best
            }
            for (c in 0 until kk) {
                var l = 0f; var a = 0f; var b = 0f; var wsum = 0f
                for ((si, s) in samples.withIndex()) if (assign[si] == c) {
                    val wt = weights[si]; l += lab[s * 3] * wt; a += lab[s * 3 + 1] * wt; b += lab[s * 3 + 2] * wt; wsum += wt
                }
                if (wsum > 0f) { centers[c][0] = l / wsum; centers[c][1] = a / wsum; centers[c][2] = b / wsum }
            }
        }
        val share = FloatArray(kk)
        var total = 0f
        for ((si, c) in assign.withIndex()) { share[c] += weights[si]; total += weights[si] }
        // Payı küçük kümeler (kenara değen konu) arka plan sayılmaz; birbirine çok yakın merkezler birleşir
        val kept = (0 until kk).filter { share[it] / total >= 0.18f }.sortedByDescending { share[it] }.map { centers[it] }
        val merged = ArrayList<FloatArray>()
        for (c in kept) if (merged.none { m -> val dl = m[0] - c[0]; val da = m[1] - c[1]; val db = m[2] - c[2]; sqrt(dl * dl + da * da + db * db) < 6f }) merged += c
        return merged
    }

    private fun removeHoles(lab: FloatArray, bg: ByteArray, w: Int, h: Int, centers: List<FloatArray>, t: Float, minArea: Int, queue: IntArray) {
        val n = w * h
        val seen = BooleanArray(n)
        fun near(i: Int): Boolean { for (c in centers) if (dist(lab, i, c) < t) return true; return false }
        for (s in 0 until n) {
            if (seen[s] || bg[s].toInt() != 0 || !near(s)) continue
            var head = 0; var tail = 0
            queue[tail++] = s; seen[s] = true
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                for (k in 0 until 4) {
                    val nx = x + DX[k]; val ny = y + DY[k]
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (seen[q] || bg[q].toInt() != 0 || !near(q)) continue
                    seen[q] = true; queue[tail++] = q
                }
            }
            if (tail >= minArea) for (j in 0 until tail) bg[queue[j]] = 1
        }
    }

    private fun flood(lab: FloatArray, bg: ByteArray, w: Int, h: Int, u: Float, v: Float, target: Int, tGlobal: Float, tLocal: Float, queue: IntArray) {
        val sx = (u * w).toInt().coerceIn(0, w - 1); val sy = (v * h).toInt().coerceIn(0, h - 1)
        val s = sy * w + sx
        val ref = floatArrayOf(lab[s * 3], lab[s * 3 + 1], lab[s * 3 + 2])
        val seen = BooleanArray(w * h)
        var head = 0; var tail = 0
        queue[tail++] = s; seen[s] = true
        while (head < tail) {
            val p = queue[head++]
            bg[p] = target.toByte()
            val x = p % w; val y = p / w
            for (k in 0 until 4) {
                val nx = x + DX[k]; val ny = y + DY[k]
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val q = ny * w + nx
                if (seen[q]) continue
                // Geri getirmede yalnız kaldırılmış alan; kaldırmada yalnız kalan alan genişler
                if (bg[q].toInt() == target) continue
                val d = dist(lab, q, ref)
                if (d < tGlobal && dist2(lab, p, q) < tLocal * 2f) { seen[q] = true; queue[tail++] = q }
            }
        }
    }

    /** [value] değerli, [maxArea]'dan küçük bağlı bölgeleri ters değere çevirir. */
    private fun cleanComponents(bg: ByteArray, w: Int, h: Int, value: Int, maxArea: Int, queue: IntArray, borderSafe: Boolean = false) {
        val n = w * h
        val seen = BooleanArray(n)
        for (s in 0 until n) {
            if (seen[s] || bg[s].toInt() != value) continue
            var head = 0; var tail = 0; var touchesBorder = false
            queue[tail++] = s; seen[s] = true
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touchesBorder = true
                for (k in 0 until 4) {
                    val nx = x + DX[k]; val ny = y + DY[k]
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (seen[q] || bg[q].toInt() != value) continue
                    seen[q] = true; queue[tail++] = q
                }
            }
            if (tail <= maxArea && !(borderSafe && touchesBorder)) for (j in 0 until tail) bg[queue[j]] = (1 - value).toByte()
        }
    }

    /** Maske kenarlarını [radius] piksel yumuşatır (iki geçişli kutu bulanıklığı; iç ve dış tam kalır). */
    private fun softEdges(a: FloatArray, w: Int, h: Int, radius: Float): FloatArray {
        val r = max(1, radius.roundToInt())
        val tmp = FloatArray(a.size)
        var cur = a.copyOf()
        repeat(2) {
            boxH(cur, tmp, w, h, r); boxV(tmp, cur, w, h, r)
        }
        // Yumuşak geçiş biraz sıkılaştırılır (kenar bulanık görünmesin): 0.15..0.85 → 0..1
        for (i in cur.indices) cur[i] = ((cur[i] - 0.15f) / 0.7f).coerceIn(0f, 1f)
        return cur
    }

    private fun boxH(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val div = 2f * r + 1f
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (i in -r..r) sum += src[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / div
                sum += src[row + min(w - 1, x + r + 1)] - src[row + max(0, x - r)]
            }
        }
    }

    private fun boxV(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val div = 2f * r + 1f
        for (x in 0 until w) {
            var sum = 0f
            for (i in -r..r) sum += src[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / div
                sum += src[min(h - 1, y + r + 1) * w + x] - src[max(0, y - r) * w + x]
            }
        }
    }

    /** Alan ortalamalı küçültme (ARGB, saydamlık ağırlıklı). */
    fun resample(src: IntArray, w: Int, h: Int, nw: Int, nh: Int): IntArray {
        val out = IntArray(nw * nh)
        for (y in 0 until nh) {
            val y0 = y * h / nh; val y1 = max(y0 + 1, (y + 1) * h / nh)
            for (x in 0 until nw) {
                val x0 = x * w / nw; val x1 = max(x0 + 1, (x + 1) * w / nw)
                var a = 0L; var r = 0L; var g = 0L; var b = 0L; var k = 0
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val c = src[yy * w + xx]; val ca = (c ushr 24).toLong()
                    a += ca; r += (c shr 16 and 0xFF) * ca; g += (c shr 8 and 0xFF) * ca; b += (c and 0xFF) * ca; k++
                }
                out[y * nw + x] = if (a == 0L) 0 else ((a / k).toInt() shl 24) or ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (b / a).toInt()
            }
        }
        return out
    }

    // --- sRGB ↔ Lab (D65) ---

    private val LIN = FloatArray(256) { val c = it / 255f; if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat() }

    private fun f(t: Float): Float = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f

    internal fun toLab(c: Int, out: FloatArray, at: Int) {
        val r = LIN[c shr 16 and 0xFF]; val g = LIN[c shr 8 and 0xFF]; val b = LIN[c and 0xFF]
        val x = (0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val z = (0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f
        val fx = f(x); val fy = f(y); val fz = f(z)
        out[at] = 116f * fy - 16f; out[at + 1] = 500f * (fx - fy); out[at + 2] = 200f * (fy - fz)
    }

    private fun labToRgb(lab: FloatArray): Int {
        val fy = (lab[0] + 16f) / 116f; val fx = fy + lab[1] / 500f; val fz = fy - lab[2] / 200f
        fun inv(t: Float) = if (t * t * t > 0.008856f) t * t * t else (t - 16f / 116f) / 7.787f
        val x = inv(fx) * 0.95047f; val y = inv(fy); val z = inv(fz) * 1.08883f
        val r = 3.2406f * x - 1.5372f * y - 0.4986f * z
        val g = -0.9689f * x + 1.8758f * y + 0.0415f * z
        val b = 0.0557f * x - 0.2040f * y + 1.0570f * z
        fun gam(v: Float): Int { val c = if (v <= 0.0031308f) 12.92f * v else 1.055f * Math.pow(v.toDouble(), 1 / 2.4).toFloat() - 0.055f; return (c * 255f).roundToInt().coerceIn(0, 255) }
        return (0xFF shl 24) or (gam(r) shl 16) or (gam(g) shl 8) or gam(b)
    }
}
