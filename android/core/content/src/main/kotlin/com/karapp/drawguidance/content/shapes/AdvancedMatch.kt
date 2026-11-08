package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.ink.Geo
import com.karapp.drawguidance.content.ink.Poly
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * v27 çok elemanlı şekil tanımada ek yeniden sıralayıcılar (kullanıcının seçtiği dört yöntemden üçü; dördüncüsü küçük
 * sinir ağı [ShapeNet]). Hepsi saf Kotlin, yalnız en iyi birkaç aday için çalışır (arama süresi milisaniyenin altında
 * kalır):
 *
 * 1. $Q nokta bulutu tanıyıcısı (Vatavu, Anthony, Wobbrock 2018): çizgi sayısından, sırasından ve yönünden bağımsız
 *    eşleme; açgözlü bulut eşlemesi, arama tablosundan alt sınır ve erken bırakma ($P'den ~142 kat hızlı).
 * 2. Parça ilişki grafiği: her eleman temel parça olarak tanınır (nokta, doğru, yay, yuvarlak/köşeli kapalı, açık eğri),
 *    parçalar Macar yöntemiyle eşlenir, aralarındaki ilişkiler (göreli konum, içinde olma, boy oranı) karşılaştırılır.
 *    Kısmi eşleme (az eleman seçiliyken) eksik parçaların önerisini besler.
 * 3. Görüntü deformasyon modeli (IDM, Ouyang ve Davis 2009): yön ızgarası görüntülerinde her hücre komşu hücrelere
 *    kayabilir (3×3); küçük yer kaymalarına dayanıklı görüntü uzaklığı.
 */

/** $Q bulutu: [N] nokta (tamsayı ızgaraya ölçeklenmiş) ve en yakın nokta arama tablosu. */
internal class QCloud(val xs: FloatArray, val ys: FloatArray, val lut: IntArray)

internal object QDollar {
    const val N = 32
    private const val M = 64

    /** Çizgilerin ortak bulutu: toplam uzunluğa göre [N] nokta, ağırlık merkezi ortada, uzun kenar M-1. */
    fun cloud(polys: List<Poly>): QCloud? {
        val strokes = polys.filter { it.n > 0 }
        if (strokes.isEmpty()) return null
        val total = strokes.sumOf { it.length.toDouble() }.toFloat()
        val xs = FloatArray(N); val ys = FloatArray(N)
        var k = 0
        if (total < 1e-6f) {
            for (i in 0 until N) { val p = strokes[i % strokes.size]; xs[i] = p.x[0]; ys[i] = p.y[0] }
            k = N
        } else {
            // Her çizgiye uzunluğu oranında nokta (noktalara en az bir)
            val alloc = IntArray(strokes.size) { i -> max(1, Math.round((N - strokes.size) * strokes[i].length / total)) }
            var sum = alloc.sum()
            var guard = 0
            while (sum != N && guard++ < 4 * N) {
                val i = if (sum > N) alloc.indices.maxBy { alloc[it] } else alloc.indices.maxBy { strokes[it].length / alloc[it] }
                if (sum > N && alloc[i] > 1) { alloc[i]--; sum-- } else if (sum < N) { alloc[i]++; sum++ } else break
            }
            for ((si, st) in strokes.withIndex()) {
                val c = alloc[si]
                if (c <= 1 || st.length < 1e-6f || st.n < 2) {
                    repeat(c) { if (k < N) { xs[k] = st.x[0]; ys[k] = st.y[0]; k++ } }
                    continue
                }
                val r = Geo.resample(st, st.length / (c - 1))
                for (i in 0 until c) if (k < N) { val j = min(i, r.n - 1); xs[k] = r.x[j]; ys[k] = r.y[j]; k++ }
            }
            while (k < N) { xs[k] = xs[k - 1]; ys[k] = ys[k - 1]; k++ }
        }
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (i in 0 until N) { minX = min(minX, xs[i]); maxX = max(maxX, xs[i]); minY = min(minY, ys[i]); maxY = max(maxY, ys[i]) }
        val size = max(max(maxX - minX, maxY - minY), 1e-6f)
        var cx = 0f; var cy = 0f
        for (i in 0 until N) { xs[i] = (xs[i] - minX) / size * (M - 1); ys[i] = (ys[i] - minY) / size * (M - 1); cx += xs[i]; cy += ys[i] }
        cx /= N; cy /= N
        // Ağırlık merkezi ızgaranın ortasına
        val sx = (M - 1) / 2f - cx; val sy = (M - 1) / 2f - cy
        for (i in 0 until N) { xs[i] = (xs[i] + sx).coerceIn(0f, M - 1f); ys[i] = (ys[i] + sy).coerceIn(0f, M - 1f) }
        val lut = IntArray(M * M)
        for (gy in 0 until M) for (gx in 0 until M) {
            var best = 0; var bd = Float.MAX_VALUE
            for (i in 0 until N) {
                val dx = xs[i] - gx; val dy = ys[i] - gy
                val d = dx * dx + dy * dy
                if (d < bd) { bd = d; best = i }
            }
            lut[gy * M + gx] = best
        }
        return QCloud(xs, ys, lut)
    }

    /** İki bulut arası $Q uzaklığı (açgözlü eşleme, alt sınırla erken bırakma); kutu boyuna göre ortalama uzaklık. */
    fun distance(a: QCloud, b: QCloud): Float {
        val step = floor(sqrt(N.toDouble())).toInt().coerceAtLeast(1)
        var minSoFar = Float.MAX_VALUE
        val lb1 = lowerBound(a, b, step)
        val lb2 = lowerBound(b, a, step)
        var i = 0
        var idx = 0
        while (i < N) {
            if (lb1[idx] < minSoFar) minSoFar = min(minSoFar, cloudDistance(a, b, i, minSoFar))
            if (lb2[idx] < minSoFar) minSoFar = min(minSoFar, cloudDistance(b, a, i, minSoFar))
            i += step; idx++
        }
        val weights = N * (N + 1) / 2f
        return sqrt(minSoFar / weights) / (M - 1)
    }

    private fun cloudDistance(p: QCloud, t: QCloud, start: Int, minSoFar: Float): Float {
        val unmatched = BooleanArray(N) { true }
        var i = start
        var weight = N.toFloat()
        var sum = 0f
        do {
            var best = -1; var bd = Float.MAX_VALUE
            for (j in 0 until N) if (unmatched[j]) {
                val dx = p.xs[i] - t.xs[j]; val dy = p.ys[i] - t.ys[j]
                val d = dx * dx + dy * dy
                if (d < bd) { bd = d; best = j }
            }
            unmatched[best] = false
            sum += weight * bd
            if (sum >= minSoFar) return sum
            weight -= 1f
            i = (i + 1) % N
        } while (i != start)
        return sum
    }

    private fun lowerBound(p: QCloud, t: QCloud, step: Int): FloatArray {
        val count = (N + step - 1) / step
        val lb = FloatArray(count)
        val sat = FloatArray(N)
        for (i in 0 until N) {
            val gx = p.xs[i].toInt().coerceIn(0, M - 1); val gy = p.ys[i].toInt().coerceIn(0, M - 1)
            val j = t.lut[gy * M + gx]
            val dx = p.xs[i] - t.xs[j]; val dy = p.ys[i] - t.ys[j]
            val d = dx * dx + dy * dy
            sat[i] = if (i == 0) d else sat[i - 1] + d
            lb[0] += (N - i) * d
        }
        var k = 1
        var i = step
        while (i < N && k < count) {
            lb[k] = lb[0] + i * sat[N - 1] - N * sat[i - 1]
            i += step; k++
        }
        return lb
    }
}

/** Grafiğin bir parçası: tür, birim kutudaki merkez ve boy, ağırlık (uzunluk payı) ve kendi biçim özellikleri. */
internal class GPart(
    val type: Int,
    val cx: Float, val cy: Float, val w: Float, val h: Float,
    val weight: Float,
    /** kapalı, düzlük, yuvarlaklık, köşe/6, en-boy (atan oranı), dönüş/tur. */
    val desc: FloatArray,
) {
    val size: Float get() = max(w, h)
}

/** Parça ilişki grafiği: parçalar (ilişkiler eşleme sırasında çiftlerden hesaplanır). */
internal class PGraph(val parts: Array<GPart>)

/** Kısmi eşleme: sorgu parçası → şablon parçası ve maliyet (az eleman seçiliyken eksik parça önerisi için). */
internal class PartialMatch(val mapping: IntArray, val cost: Float)

internal object PartGraph {
    const val DOT = 0
    const val LINE = 1
    const val ARC = 2
    const val ROUND = 3
    const val POLY = 4
    const val OPEN = 5

    /** Parçalardan grafik ([polys] birim kutuya getirilmiş; küçük parçalar nokta). */
    fun of(polys: List<Poly>): PGraph {
        val total = polys.sumOf { max(it.length, max(it.extent, 0.02f)).toDouble() }.toFloat().coerceAtLeast(1e-6f)
        return PGraph(Array(polys.size) { i -> part(polys[i], max(polys[i].length, max(polys[i].extent, 0.02f)) / total) })
    }

    fun part(p: Poly, weight: Float): GPart {
        if (p.n < 2 || p.extent < 0.03f) return GPart(DOT, p.cx, p.cy, p.w, p.h, weight, floatArrayOf(1f, 0f, 1f, 0f, 0.5f, 0f))
        val closed = ShapeSignatures.isClosed(p)
        val chord = hypot(p.x[p.n - 1] - p.x[0], p.y[p.n - 1] - p.y[0])
        val straight = if (p.length > 1e-6f) (chord / p.length).coerceIn(0f, 1f) else 0f
        val corners = ShapeSignatures.cornersOf(p)
        val turn = Geo.turning(p, 0.02f)
        var area = 0f
        for (i in 0 until p.n) { val j = (i + 1) % p.n; area += p.x[i] * p.y[j] - p.x[j] * p.y[i] }
        area = abs(area) / 2f
        val per = p.length + chord
        val round = if (per > 1e-6f) (4f * Math.PI.toFloat() * area / (per * per)).coerceIn(0f, 1f) else 0f
        val aspect = (atan2(p.w.toDouble(), p.h.toDouble()) / (Math.PI / 2)).toFloat()
        val type = when {
            closed && round > 0.62f && corners <= 2 -> ROUND
            closed -> POLY
            straight > 0.93f && corners <= 1 -> LINE
            turn.absTurns < 0.7f && turn.reversals == 0 -> ARC
            else -> OPEN
        }
        return GPart(type, p.cx, p.cy, p.w, p.h, weight, floatArrayOf(if (closed) 1f else 0f, straight, round, min(corners, 6) / 6f, aspect, min(turn.absTurns, 3f) / 3f))
    }

    private fun typeCost(a: Int, b: Int): Float = when {
        a == b -> 0f
        (a == ROUND && b == POLY) || (a == POLY && b == ROUND) -> 0.35f
        (a == ARC && b == OPEN) || (a == OPEN && b == ARC) || (a == LINE && b == ARC) || (a == ARC && b == LINE) -> 0.3f
        a == DOT || b == DOT -> 0.8f
        else -> 0.65f
    }

    /** Parçanın kendi biçiminin farkı (yerinden bağımsız). */
    fun intrinsic(a: GPart, b: GPart): Float {
        var d = typeCost(a.type, b.type)
        if (a.type != DOT && b.type != DOT) {
            var s = 0f
            for (k in 0 until 6) s += abs(a.desc[k] - b.desc[k])
            d += 0.35f * s
        }
        return d
    }

    /** Konum ve boy dahil parça farkı (iki grafik aynı birim kutudayken). */
    private fun placed(a: GPart, b: GPart): Float =
        intrinsic(a, b) * 0.6f + 1.4f * hypot(a.cx - b.cx, a.cy - b.cy) + 0.8f * abs(a.size - b.size)

    /**
     * Grafik uzaklığı: parçalar Macar yöntemiyle eşlenir (eşlenmeyen parça ağırlığıyla cezalanır), eşlenen çiftlerin
     * göreli konumları ve içerme ilişkileri karşılaştırılır. Yaklaşık 0 (aynı) … 1+ (farklı).
     */
    fun distance(q: PGraph, t: PGraph): Float {
        val n = q.parts.size; val m = t.parts.size
        if (n == 0 || m == 0) return 1f
        val size = max(n, m)
        val miss = 0.9f
        val cost = Array(size) { i -> FloatArray(size) { j ->
            when {
                i < n && j < m -> placed(q.parts[i], t.parts[j]) * (q.parts[i].weight + t.parts[j].weight) * 0.5f
                i < n -> miss * q.parts[i].weight
                j < m -> miss * t.parts[j].weight
                else -> 0f
            }
        } }
        val assign = Hungarian.solve(cost)
        var total = 0f
        for (i in 0 until size) total += cost[i][assign[i]]
        // İlişkiler: eşlenen çiftlerin aralarındaki konum ve içerme
        var rel = 0f; var pairs = 0
        val pairsMax = 40
        loop@ for (i1 in 0 until n) {
            val j1 = assign[i1]; if (j1 >= m) continue
            for (i2 in i1 + 1 until n) {
                val j2 = assign[i2]; if (j2 >= m) continue
                val a1 = q.parts[i1]; val a2 = q.parts[i2]; val b1 = t.parts[j1]; val b2 = t.parts[j2]
                val dq = hypot((a2.cx - a1.cx) - (b2.cx - b1.cx), (a2.cy - a1.cy) - (b2.cy - b1.cy))
                val inQ = inside(a1, a2) != inside(b1, b2)
                rel += (dq + if (inQ) 0.3f else 0f) * min(a1.weight + a2.weight, 0.5f)
                if (++pairs >= pairsMax) break@loop
            }
        }
        return total + 0.8f * rel
    }

    /** -1: a b'nin içinde, 1: b a'nın içinde, 0: hiçbiri (kutulara göre). */
    private fun inside(a: GPart, b: GPart): Int {
        fun within(x: GPart, y: GPart) = x.cx - x.w / 2 >= y.cx - y.w / 2 - 0.01f && x.cx + x.w / 2 <= y.cx + y.w / 2 + 0.01f &&
            x.cy - x.h / 2 >= y.cy - y.h / 2 - 0.01f && x.cy + x.h / 2 <= y.cy + y.h / 2 + 0.01f
        return when {
            within(a, b) && b.size > a.size * 1.2f -> -1
            within(b, a) && a.size > b.size * 1.2f -> 1
            else -> 0
        }
    }

    /**
     * Kısmi eşleme: sorgunun bütün parçaları (1–3) şablonun parçalarına; tek parçada yalnız biçim, çok parçada ayrıca
     * göreli düzen (sorgu ve şablon kendi kutularına göre) karşılaştırılır. Eşlenen şablon parçaları şablonun önemli
     * bir kısmı olmalı (çok küçük bir parçaya oturtulmaz). Maliyet ~0 (çok benzer) … 1+.
     */
    fun partial(q: PGraph, t: PGraph, maxCost: Float = PARTIAL_MAX): PartialMatch? {
        val n = q.parts.size; val m = t.parts.size
        if (n == 0 || n > 3 || m <= n) return null
        // Biçim farkları bir kez (n × m); her sorgu parçasının en iyi değeri alt sınır olur
        val intr = Array(n) { k -> FloatArray(m) { j -> intrinsic(q.parts[k], t.parts[j]) } }
        val rowMin = FloatArray(n) { k -> intr[k].min() }
        var lower = 0f
        for (k in 0 until n) lower += rowMin[k]
        if (lower / n > maxCost) return null
        // Sorgunun kendi kutusu ve göreli konumları da bir kez
        val qb = boxOf(q.parts.toList())
        val qx = FloatArray(n) { (q.parts[it].cx - qb[0]) / qb[2] }
        val qy = FloatArray(n) { (q.parts[it].cy - qb[1]) / qb[3] }
        val qs = FloatArray(n) { q.parts[it].size / qb[4] }
        var bestCost = maxCost
        var bestMap: IntArray? = null
        val map = IntArray(n)
        val used = BooleanArray(m)
        fun rec(i: Int, acc: Float) {
            // Dal ve sınır: kalan parçaların en iyi biçim farkı eklense bile en iyiyi geçemiyorsa dur
            var rest = 0f
            for (k in i until n) rest += rowMin[k]
            if ((acc + rest) / n >= bestCost) return
            if (i == n) {
                var c = acc / n
                // Önemli parça: eşlenen şablon parçalarının toplam boyu
                var share = 0f
                for (k in 0 until n) share += t.parts[map[k]].size
                if (share < 0.28f) return
                if (n >= 2) {
                    var l = Float.MAX_VALUE; var tp = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
                    for (k in 0 until n) { val p = t.parts[map[k]]; l = min(l, p.cx - p.w / 2); tp = min(tp, p.cy - p.h / 2); r = max(r, p.cx + p.w / 2); b = max(b, p.cy + p.h / 2) }
                    val tw = max(r - l, 1e-3f); val th = max(b - tp, 1e-3f); val tl = max(tw, th)
                    for (k in 0 until n) {
                        val p = t.parts[map[k]]
                        val bx = (p.cx - l) / tw; val by = (p.cy - tp) / th
                        c += 0.5f * hypot(qx[k] - bx, qy[k] - by) / n
                        c += 0.3f * abs(qs[k] - p.size / tl) / n
                        if (c >= bestCost) return
                    }
                }
                if (c < bestCost) { bestCost = c; bestMap = map.copyOf() }
                return
            }
            for (j in 0 until m) if (!used[j]) {
                used[j] = true; map[i] = j
                rec(i + 1, acc + intr[i][j])
                used[j] = false
            }
        }
        rec(0, 0f)
        return bestMap?.let { PartialMatch(it, bestCost) }
    }

    /** Kısmi eşlemede bundan kötüsü aranmaz (güven ~%4). */
    const val PARTIAL_MAX = 1.0f

    /** Parçaların kutusu: sol, üst, en, boy, uzun kenar. */
    private fun boxOf(parts: List<GPart>): FloatArray {
        var l = Float.MAX_VALUE; var tp = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in parts) { l = min(l, p.cx - p.w / 2); tp = min(tp, p.cy - p.h / 2); r = max(r, p.cx + p.w / 2); b = max(b, p.cy + p.h / 2) }
        val w = max(r - l, 1e-3f); val h = max(b - tp, 1e-3f)
        return floatArrayOf(l, tp, w, h, max(w, h))
    }

    fun confidence(cost: Float): Float = exp(-cost / 0.32f)
}

/** Macar yöntemi (kare maliyet matrisi, en küçük toplam); n ≤ ~16 için yeterince hızlı (O(n³)). */
internal object Hungarian {
    fun solve(cost: Array<FloatArray>): IntArray {
        val n = cost.size
        val u = DoubleArray(n + 1); val v = DoubleArray(n + 1)
        val p = IntArray(n + 1); val way = IntArray(n + 1)
        for (i in 1..n) {
            p[0] = i
            var j0 = 0
            val minv = DoubleArray(n + 1) { Double.MAX_VALUE }
            val used = BooleanArray(n + 1)
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = Double.MAX_VALUE
                var j1 = 0
                for (j in 1..n) if (!used[j]) {
                    val cur = cost[i0 - 1][j - 1] - u[i0] - v[j]
                    if (cur < minv[j]) { minv[j] = cur; way[j] = j0 }
                    if (minv[j] < delta) { delta = minv[j]; j1 = j }
                }
                for (j in 0..n) if (used[j]) { u[p[j]] += delta; v[j] -= delta } else minv[j] -= delta
                j0 = j1
            } while (p[j0] != 0)
            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }
        val ans = IntArray(n)
        for (j in 1..n) if (p[j] > 0) ans[p[j] - 1] = j - 1
        return ans
    }
}

/** Görüntü deformasyon modeli (5 kanal × 8×8 yön ızgarası, kanal öncelikli düzen). */
internal object Idm {
    private const val G = 8
    private const val CH = 5

    /** Her hücre için 3×3 komşulukta en iyi eşleşen hücreyle kare fark toplamı (kaymaya küçük ceza). */
    fun distance(q: FloatArray, qo: Int, t: FloatArray, to: Int): Float {
        var total = 0f
        for (y in 0 until G) for (x in 0 until G) {
            var best = Float.MAX_VALUE
            for (dy in -1..1) {
                val yy = y + dy
                if (yy < 0 || yy >= G) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx < 0 || xx >= G) continue
                    var s = if (dx != 0 || dy != 0) SHIFT else 0f
                    for (c in 0 until CH) {
                        val a = q[qo + c * G * G + y * G + x]; val b = t[to + c * G * G + yy * G + xx]
                        s += (a - b) * (a - b)
                    }
                    if (s < best) best = s
                }
            }
            total += best
        }
        return total
    }

    private const val SHIFT = 0.0008f
}
