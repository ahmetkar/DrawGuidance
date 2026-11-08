package com.karapp.drawguidance.content.trace

import com.karapp.drawguidance.content.vector.P
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Çizgi bulma türü: [AUTO] görüntüye göre seçer; [EDGES] renk/ışık değişimlerinin kenarı; [CENTERLINE] koyu çizgilerin ortası. */
enum class EdgeMode { AUTO, EDGES, CENTERLINE }

/**
 * Kenar vektörleştirme ayarları (0..1).
 * - [detail]: 0 yalnız belirgin kenarlar, az çizgi … 1 ince ayrıntılar.
 * - [smooth]: çizgilerin yumuşatılması (titreşim ve piksel basamakları kalkar).
 * - [minLength]: kısa çizgileri atma eşiği (0: hepsi kalır … 1: yalnız uzun çizgiler).
 * - [colored]: çizgi rengi görüntüden alınır; kapalıysa koyu gri.
 */
data class EdgeOptions(
    val detail: Float = 0.5f,
    val smooth: Float = 0.5f,
    val minLength: Float = 0.4f,
    val colored: Boolean = false,
    val mode: EdgeMode = EdgeMode.AUTO,
    val maxSide: Int = 640,
)

/**
 * Bulunan çizgi: noktalar görüntünün birim kutusunda (0..1), [nodes] vektör noktalarının sıraları (köşeler ve eğrinin
 * dönüm yerleri; aradaki noktalar yumuşak eğri), [color] "#RRGGBB".
 */
data class EdgePath(val points: List<P>, val closed: Boolean, val nodes: List<Int>, val color: String)

/** Sonuç; [mode] kullanılan tür (otomatikte seçilen), [width]×[height] işlenen çözünürlük. */
data class EdgeResult(val paths: List<EdgePath>, val mode: EdgeMode, val width: Int, val height: Int)

/**
 * Bitmap → düzenlenebilir çizgiler (dolgu değil kenar çizgileri; tamamen Kotlin, Android'den bağımsız).
 *
 * - **Kenarlar** (fotoğraf, renkli resim): gri ton → Gauss yumuşatma → Sobel eğimi → en büyük olmayanı bastırma →
 *   görüntüye uyarlanan çift eşikle histerezis (Canny) → incelme.
 * - **Orta çizgi** (kalem/mürekkep çizimi): Otsu ile koyu çizgiler ayrılır → incelme (Zhang–Suen) ile çizginin ortası.
 * - Ortak: pikseller zincirlere bağlanır, uçları yakın zincirler birleşir, kısa zincirler atılır; yumuşatma,
 *   Ramer–Douglas–Peucker ile vektör noktaları ve noktalar arasında Catmull-Rom eğrisi.
 * Durumsuzdur; arka planda çağrılmalıdır. [cancel] true dönerse [java.util.concurrent.CancellationException].
 */
object EdgeTracer {

    const val MAX_PATHS = 2500
    const val MAX_POINTS = 80_000

    fun trace(argb: IntArray, width: Int, height: Int, options: EdgeOptions = EdgeOptions(), cancel: () -> Boolean = { false }): EdgeResult {
        require(width > 0 && height > 0) { "Geçersiz boyut: ${width}x$height" }
        require(argb.size >= width.toLong() * height) { "Piksel dizisi kısa" }
        val img = downscale(argb, width, height, options.maxSide.coerceIn(64, 2048))
        check(cancel)
        val w = img.w
        val h = img.h
        val gray = FloatArray(w * h) { 0.299f * img.r[it] + 0.587f * img.g[it] + 0.114f * img.b[it] }
        val detail = options.detail.coerceIn(0f, 1f)
        val mode = when (options.mode) {
            EdgeMode.AUTO -> if (looksLikeLineArt(gray, w, h)) EdgeMode.CENTERLINE else EdgeMode.EDGES
            else -> options.mode
        }
        val mask = if (mode == EdgeMode.CENTERLINE) centerline(gray, w, h, detail) else canny(gray, w, h, detail, cancel)
        check(cancel)
        thin(mask, w, h)
        check(cancel)
        var chains = chains(mask, w, h)
        chains = join(chains, 2.3f)
        val minPx = (3f + 40f * options.minLength.coerceIn(0f, 1f)) * (max(w, h) / 640f).coerceAtLeast(0.4f)
        chains = chains.filter { length(it) >= minPx || (isClosed(it) && length(it) >= minPx * 0.5f) }
            .sortedByDescending { length(it) }
            .take(MAX_PATHS)
        check(cancel)
        val smooth = options.smooth.coerceIn(0f, 1f)
        val eps = 0.5f + 1.6f * (1f - detail) + 0.8f * smooth
        val out = ArrayList<EdgePath>(chains.size)
        var total = 0
        for (c in chains) {
            val closed = isClosed(c) && c.size >= 8
            val pts = if (closed && c.first().dist(c.last()) < 0.01f) c.dropLast(1) else c
            val sm = smoothed(pts, closed, (1 + (smooth * 4f).roundToInt()))
            val keys = rdp(sm, closed, eps)
            val (curve, nodes) = spline(sm, keys, closed)
            if (curve.size < 2) continue
            if (total + curve.size > MAX_POINTS) break
            total += curve.size
            val color = if (options.colored) chainColor(img, pts) else DARK
            out += EdgePath(curve.map { P(it.x / w, it.y / h) }, closed, nodes, color)
        }
        return EdgeResult(out, mode, w, h)
    }

    private const val DARK = "#1E1E1E"

    private fun check(cancel: () -> Boolean) {
        if (cancel()) throw java.util.concurrent.CancellationException("iptal")
    }

    // --- Görüntü ---

    private class Img(val w: Int, val h: Int, val r: FloatArray, val g: FloatArray, val b: FloatArray)

    /** Alan ortalamalı küçültme; saydam pikseller beyaz zemine bindirilir. */
    private fun downscale(argb: IntArray, width: Int, height: Int, maxSide: Int): Img {
        val k = min(1f, maxSide.toFloat() / max(width, height))
        val w = max(1, (width * k).roundToInt())
        val h = max(1, (height * k).roundToInt())
        val r = FloatArray(w * h); val g = FloatArray(w * h); val b = FloatArray(w * h)
        val n = FloatArray(w * h)
        for (y in 0 until height) {
            val ty = min(h - 1, (y * k).toInt())
            for (x in 0 until width) {
                val tx = min(w - 1, (x * k).toInt())
                val c = argb[y * width + x]
                val a = (c ushr 24 and 0xFF) / 255f
                val i = ty * w + tx
                r[i] += (c shr 16 and 0xFF) * a + 255f * (1 - a)
                g[i] += (c shr 8 and 0xFF) * a + 255f * (1 - a)
                b[i] += (c and 0xFF) * a + 255f * (1 - a)
                n[i] += 1f
            }
        }
        for (i in 0 until w * h) if (n[i] > 0f) { r[i] /= n[i]; g[i] /= n[i]; b[i] /= n[i] } else { r[i] = 255f; g[i] = 255f; b[i] = 255f }
        return Img(w, h, r, g, b)
    }

    /** Açık zemin üstünde ince koyu çizgiler mi (kalem çizimi): koyu piksel azsa ve çizgiler inceyse. */
    private fun looksLikeLineArt(gray: FloatArray, w: Int, h: Int): Boolean {
        val t = otsu(gray)
        var dark = 0
        var light = 0
        for (v in gray) if (v < t) dark++ else if (v > 200f) light++
        val n = gray.size
        if (dark == 0 || dark > n * 0.28f || light < n * 0.5f) return false
        // Koyu piksellerin ortalama kalınlığı: koyu alan / koyu sınır uzunluğu
        var border = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (gray[i] >= t) continue
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1 || gray[i - 1] >= t || gray[i + 1] >= t || gray[i - w] >= t || gray[i + w] >= t) border++
        }
        val thickness = 2f * dark / max(1, border)
        return thickness <= 7f
    }

    private fun otsu(gray: FloatArray): Float {
        val hist = IntArray(256)
        for (v in gray) hist[v.roundToInt().coerceIn(0, 255)]++
        val total = gray.size.toDouble()
        var sum = 0.0
        for (i in 0 until 256) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0.0; var best = 0.0; var t = 128
        for (i in 0 until 256) {
            wB += hist[i]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; t = i }
        }
        return t + 0.5f
    }

    /** Koyu çizgilerin maskesi (incelme ile ortası bulunur); ayrıntı azsa küçük lekeler atılır. */
    private fun centerline(gray: FloatArray, w: Int, h: Int, detail: Float): BooleanArray {
        val blurred = blur(gray, w, h, 0.6f + 0.6f * (1f - detail))
        val t = otsu(blurred)
        return BooleanArray(w * h) { blurred[it] < t }
    }

    private fun blur(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        val rad = max(1, ceil(sigma * 2.5f).toInt())
        val k = FloatArray(rad * 2 + 1) { val d = (it - rad).toFloat(); exp(-(d * d) / (2 * sigma * sigma)) }
        val ks = k.sum()
        for (i in k.indices) k[i] /= ks
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (j in -rad..rad) s += src[y * w + (x + j).coerceIn(0, w - 1)] * k[j + rad]
            tmp[y * w + x] = s
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (j in -rad..rad) s += tmp[(y + j).coerceIn(0, h - 1) * w + x] * k[j + rad]
            out[y * w + x] = s
        }
        return out
    }

    /** Canny: yumuşatma, Sobel, en büyük olmayanı bastırma ve uyarlanan çift eşikle histerezis. */
    private fun canny(gray: FloatArray, w: Int, h: Int, detail: Float, cancel: () -> Boolean): BooleanArray {
        val g = blur(gray, w, h, 1.9f - 0.9f * detail)
        check(cancel)
        val mag = FloatArray(w * h)
        val dir = ByteArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = (g[i - w + 1] + 2 * g[i + 1] + g[i + w + 1]) - (g[i - w - 1] + 2 * g[i - 1] + g[i + w - 1])
            val gy = (g[i + w - 1] + 2 * g[i + w] + g[i + w + 1]) - (g[i - w - 1] + 2 * g[i - w] + g[i - w + 1])
            mag[i] = hypot(gx, gy)
            val ax = abs(gx); val ay = abs(gy)
            dir[i] = when {
                ay <= ax * 0.4142f -> 0 // yatay eğim: kenar dikey
                ay >= ax * 2.4142f -> 2
                (gx > 0) == (gy > 0) -> 1
                else -> 3
            }
        }
        val nms = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val m = mag[i]
            if (m <= 0f) continue
            val (a, b) = when (dir[i].toInt()) {
                0 -> mag[i - 1] to mag[i + 1]
                2 -> mag[i - w] to mag[i + w]
                1 -> mag[i - w - 1] to mag[i + w + 1]
                else -> mag[i - w + 1] to mag[i + w - 1]
            }
            if (m >= a && m >= b) nms[i] = m
        }
        check(cancel)
        // Eşikler: bastırmadan kalan eğimlerin dağılımına göre (ayrıntı arttıkça daha çok kenar)
        val values = nms.filter { it > 4f }.sorted()
        if (values.isEmpty()) return BooleanArray(w * h)
        val pct = (0.86f - 0.36f * detail).coerceIn(0.3f, 0.95f)
        val high = max(values[((values.size - 1) * pct).toInt()], 28f + 40f * (1f - detail))
        val low = high * 0.42f
        val mask = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        for (i in 0 until w * h) if (nms[i] >= high) { mask[i] = true; stack[sp++] = i }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (!mask[j] && nms[j] >= low) { mask[j] = true; stack[sp++] = j }
            }
        }
        return mask
    }

    /** Zhang–Suen incelme: çizgiler tek piksel kalınlığa iner. */
    private fun thin(m: BooleanArray, w: Int, h: Int) {
        val remove = ArrayList<Int>()
        var changed = true
        var guard = 0
        fun at(x: Int, y: Int) = x in 0 until w && y in 0 until h && m[y * w + x]
        while (changed && guard++ < 60) {
            changed = false
            for (step in 0..1) {
                remove.clear()
                for (y in 0 until h) for (x in 0 until w) {
                    if (!m[y * w + x]) continue
                    val p2 = at(x, y - 1); val p3 = at(x + 1, y - 1); val p4 = at(x + 1, y); val p5 = at(x + 1, y + 1)
                    val p6 = at(x, y + 1); val p7 = at(x - 1, y + 1); val p8 = at(x - 1, y); val p9 = at(x - 1, y - 1)
                    val nb = listOf(p2, p3, p4, p5, p6, p7, p8, p9)
                    val b = nb.count { it }
                    if (b < 2 || b > 6) continue
                    var a = 0
                    for (k in 0 until 8) if (!nb[k] && nb[(k + 1) % 8]) a++
                    if (a != 1) continue
                    if (step == 0) {
                        if (p2 && p4 && p6) continue
                        if (p4 && p6 && p8) continue
                    } else {
                        if (p2 && p4 && p8) continue
                        if (p2 && p6 && p8) continue
                    }
                    remove += y * w + x
                }
                if (remove.isNotEmpty()) { changed = true; remove.forEach { m[it] = false } }
            }
        }
    }

    // --- Zincirler ---

    private data class Pt(val x: Float, val y: Float) {
        fun dist(o: Pt) = hypot(x - o.x, y - o.y)
    }

    /**
     * Pikselleri zincirlere bağlar: önce uçlardan, sonra kalan halkalar. Her adımda en düz devam eden komşuya gidilir;
     * kavşakta (çizgilerin kesiştiği yer) çizgi düz devam ediyorsa kavşak pikselinin üstünden geçilir — kesişen çizgi ve
     * daire kopmaz, daire kapalı kalır.
     */
    private fun chains(m: BooleanArray, w: Int, h: Int): List<List<Pt>> {
        val visited = BooleanArray(w * h)
        fun on(x: Int, y: Int) = x in 0 until w && y in 0 until h && m[y * w + x]
        fun degree(x: Int, y: Int): Int {
            var d = 0
            for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0) && on(x + dx, y + dy)) d++
            return d
        }
        val order = (0 until w * h).filter { m[it] }.sortedBy { i -> if (degree(i % w, i / w) == 1) 0 else 1 }
        val out = ArrayList<List<Pt>>()
        val nbr = intArrayOf(0, -1, 1, 0, 0, 1, -1, 0, 1, -1, 1, 1, -1, 1, -1, -1) // önce 4-komşular
        for (start in order) {
            if (visited[start]) continue
            val chain = ArrayList<Pt>()
            var cx = start % w; var cy = start / w
            visited[start] = true
            chain += Pt(cx + 0.5f, cy + 0.5f)
            while (true) {
                // Gidiş yönü (son birkaç pikselden)
                val n = chain.size
                val back = chain[maxOf(0, n - 4)]
                var dirX = chain[n - 1].x - back.x; var dirY = chain[n - 1].y - back.y
                val dl = hypot(dirX, dirY)
                val hasDir = dl > 0.9f
                if (hasDir) { dirX /= dl; dirY /= dl }
                fun turn(dx: Int, dy: Int): Float {
                    if (!hasDir) return 0f
                    val l = hypot(dx.toFloat(), dy.toFloat())
                    return 1f - (dx * dirX + dy * dirY) / l
                }
                var bestX = -1; var bestY = -1; var bestScore = Float.MAX_VALUE; var via = -1
                var k = 0
                while (k < nbr.size) {
                    val dx = nbr[k]; val dy = nbr[k + 1]
                    val nx = cx + dx; val ny = cy + dy
                    k += 2
                    if (!on(nx, ny)) continue
                    val j = ny * w + nx
                    if (!visited[j]) {
                        val score = turn(dx, dy) + (if (k > 8) 0.02f else 0f)
                        if (score < bestScore) { bestScore = score; bestX = nx; bestY = ny; via = -1 }
                    } else if (hasDir && degree(nx, ny) >= 3) {
                        // Ziyaret edilmiş kavşak pikselinin ötesine düz geçiş
                        var q = 0
                        while (q < nbr.size) {
                            val ex = nx + nbr[q]; val ey = ny + nbr[q + 1]
                            q += 2
                            if (!on(ex, ey) || visited[ey * w + ex]) continue
                            if (abs(ex - cx) <= 1 && abs(ey - cy) <= 1) continue
                            val score = turn(ex - cx, ey - cy) + 0.15f
                            if (score < bestScore && score < 0.45f) { bestScore = score; bestX = ex; bestY = ey; via = j }
                        }
                    }
                }
                if (bestX < 0 || (hasDir && bestScore > 1.2f)) break
                if (via >= 0) chain += Pt(via % w + 0.5f, via / w + 0.5f)
                cx = bestX; cy = bestY
                visited[cy * w + cx] = true
                chain += Pt(cx + 0.5f, cy + 0.5f)
            }
            // Halka: son piksel ilk pikselin komşusuysa kapanır
            if (chain.size > 3 && abs(chain.last().x - chain.first().x) <= 1.01f && abs(chain.last().y - chain.first().y) <= 1.01f) chain += chain.first()
            if (chain.size >= 2) out += chain
        }
        // Düz gidişte atlanan basamak/çift piksel şeritleri kısa artık zincirler bırakır: daha uzun bir zincirin hemen
        // bitişiğindeyse (piksellerinin çoğu ona komşu) atılır
        val owned = BooleanArray(w * h)
        val kept = ArrayList<List<Pt>>(out.size)
        for (c in out.sortedByDescending { it.size }) {
            if (c.size < 30) {
                val near = c.count { p ->
                    val x = p.x.toInt(); val y = p.y.toInt()
                    var hit = false
                    for (dy in -1..1) for (dx in -1..1) {
                        val xx = x + dx; val yy = y + dy
                        if (xx in 0 until w && yy in 0 until h && owned[yy * w + xx]) hit = true
                    }
                    hit
                }
                if (near >= c.size * 0.7f) continue
            }
            kept += c
            c.forEach { p -> owned[p.y.toInt() * w + p.x.toInt()] = true }
        }
        return kept
    }

    private fun length(c: List<Pt>): Float = (1 until c.size).sumOf { c[it].dist(c[it - 1]).toDouble() }.toFloat()

    private fun isClosed(c: List<Pt>) = c.size > 3 && c.first().dist(c.last()) < 1.6f

    /** Uçları [gap] pikselden yakın zincirler birleştirilir (kenar kopuklukları kapanır). */
    private fun join(src: List<List<Pt>>, gap: Float): List<List<Pt>> {
        val list = src.map { ArrayList(it) }.toMutableList()
        var merged = true
        var guard = 0
        while (merged && guard++ < 6) {
            merged = false
            var i = 0
            while (i < list.size) {
                val a = list[i]
                if (isClosed(a)) { i++; continue }
                var bestJ = -1; var bestD = gap; var mode = 0
                for (j in list.indices) {
                    if (j == i) continue
                    val b = list[j]
                    if (isClosed(b)) continue
                    val d1 = a.last().dist(b.first()); val d2 = a.last().dist(b.last())
                    val d3 = a.first().dist(b.last()); val d4 = a.first().dist(b.first())
                    if (d1 < bestD) { bestD = d1; bestJ = j; mode = 1 }
                    if (d2 < bestD) { bestD = d2; bestJ = j; mode = 2 }
                    if (d3 < bestD) { bestD = d3; bestJ = j; mode = 3 }
                    if (d4 < bestD) { bestD = d4; bestJ = j; mode = 4 }
                }
                if (bestJ < 0) { i++; continue }
                val b = list[bestJ]
                val joined = ArrayList<Pt>(a.size + b.size)
                when (mode) {
                    1 -> { joined += a; joined += b }
                    2 -> { joined += a; joined += b.asReversed() }
                    3 -> { joined += b; joined += a }
                    else -> { joined += b.asReversed(); joined += a }
                }
                val hi = max(i, bestJ); val lo = min(i, bestJ)
                list.removeAt(hi); list.removeAt(lo)
                list.add(lo, joined)
                merged = true
                i = lo
            }
            // Kendi uçları birbirine yakın zincir halka olur
            for (c in list) if (!isClosed(c) && c.size > 8 && c.first().dist(c.last()) < gap) c += c.first()
        }
        return list
    }

    private fun smoothed(pts: List<Pt>, closed: Boolean, window: Int): List<Pt> {
        if (pts.size < 5 || window <= 1) return pts
        val n = pts.size
        val out = ArrayList<Pt>(n)
        for (i in 0 until n) {
            if (!closed && (i == 0 || i == n - 1)) { out += pts[i]; continue }
            var sx = 0f; var sy = 0f; var c = 0
            for (k in -window..window) {
                val j = if (closed) ((i + k) % n + n) % n else (i + k).coerceIn(0, n - 1)
                sx += pts[j].x; sy += pts[j].y; c++
            }
            out += Pt(sx / c, sy / c)
        }
        return out
    }

    /** Vektör noktaları (sıralar): Ramer–Douglas–Peucker. */
    private fun rdp(pts: List<Pt>, closed: Boolean, eps: Float): List<Int> {
        val n = pts.size
        if (n <= 2) return pts.indices.toList()
        val keep = BooleanArray(n)
        fun simplify(a: Int, b: Int) {
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.addLast(a to b)
            while (stack.isNotEmpty()) {
                val (s, e) = stack.removeLast()
                if (e <= s + 1) continue
                var best = -1; var bestD = 0f
                for (i in s + 1 until e) {
                    val d = segDist(pts[i], pts[s], pts[e])
                    if (d > bestD) { bestD = d; best = i }
                }
                if (best >= 0 && bestD > eps) { keep[best] = true; stack.addLast(s to best); stack.addLast(best to e) }
            }
        }
        keep[0] = true
        if (closed) {
            val far = (1 until n).maxByOrNull { pts[it].dist(pts[0]) } ?: (n / 2)
            keep[far] = true
            simplify(0, far)
            // Kapanış kısmı: son noktadan başa
            val ext = pts + pts[0]
            val keepExt = BooleanArray(n + 1)
            fun simplifyExt(a: Int, b: Int) {
                val stack = ArrayDeque<Pair<Int, Int>>()
                stack.addLast(a to b)
                while (stack.isNotEmpty()) {
                    val (s, e) = stack.removeLast()
                    if (e <= s + 1) continue
                    var best = -1; var bestD = 0f
                    for (i in s + 1 until e) {
                        val d = segDist(ext[i], ext[s], ext[e])
                        if (d > bestD) { bestD = d; best = i }
                    }
                    if (best >= 0 && bestD > eps) { keepExt[best] = true; stack.addLast(s to best); stack.addLast(best to e) }
                }
            }
            simplifyExt(far, n)
            for (i in far + 1 until n) if (keepExt[i]) keep[i] = true
        } else {
            keep[n - 1] = true
            simplify(0, n - 1)
        }
        return keep.indices.filter { keep[it] }
    }

    private fun segDist(p: Pt, a: Pt, b: Pt): Float {
        val vx = b.x - a.x; val vy = b.y - a.y
        val len2 = vx * vx + vy * vy
        if (len2 < 1e-6f) return p.dist(a)
        val t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / len2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
    }

    /**
     * Vektör noktaları arasında (yumuşatılmış) kaynaktan seyrek ara noktalar: eğri kenarı izler, köşelerde taşma olmaz.
     * Dönüş: noktalar ve vektör noktalarının sıraları.
     */
    private fun spline(src: List<Pt>, keys: List<Int>, closed: Boolean): Pair<List<Pt>, List<Int>> {
        val n = keys.size
        if (n < 2) return keys.map { src[it] } to keys.indices.toList()
        val out = ArrayList<Pt>()
        val nodes = ArrayList<Int>()
        val m = src.size
        val segs = if (closed) n else n - 1
        for (s in 0 until segs) {
            val a = keys[s]
            val b = if (s + 1 < n) keys[s + 1] else keys[0] + m
            nodes += out.size
            out += src[a % m]
            val inner = b - a - 1
            if (inner <= 0) continue
            val len = src[a % m].dist(src[b % m])
            val steps = min(inner, min(10, (len / 5f).toInt()))
            for (t in 1..steps) {
                val idx = a + ((b - a) * t / (steps + 1f)).roundToInt()
                if (idx in (a + 1) until b) out += src[idx % m]
            }
        }
        if (!closed) { nodes += out.size; out += src[keys.last()] }
        return out to nodes
    }

    private fun chainColor(img: Img, pts: List<Pt>): String {
        var r = 0f; var g = 0f; var b = 0f; var c = 0
        val step = max(1, pts.size / 64)
        var i = 0
        while (i < pts.size) {
            val p = pts[i]
            // Kenarın iki yanından koyu olanı
            val x = p.x.toInt().coerceIn(0, img.w - 1); val y = p.y.toInt().coerceIn(0, img.h - 1)
            var best = -1; var bestL = Float.MAX_VALUE
            for (dy in -1..1) for (dx in -1..1) {
                val nx = (x + dx).coerceIn(0, img.w - 1); val ny = (y + dy).coerceIn(0, img.h - 1)
                val j = ny * img.w + nx
                val l = 0.299f * img.r[j] + 0.587f * img.g[j] + 0.114f * img.b[j]
                if (l < bestL) { bestL = l; best = j }
            }
            r += img.r[best]; g += img.g[best]; b += img.b[best]; c++
            i += step
        }
        if (c == 0) return DARK
        // Biraz koyulaştırılır: çizgi olarak okunaklı kalsın
        val k = 0.8f
        return String.format(Locale.ROOT, "#%02X%02X%02X", (r / c * k).toInt().coerceIn(0, 255), (g / c * k).toInt().coerceIn(0, 255), (b / c * k).toInt().coerceIn(0, 255))
    }
}
