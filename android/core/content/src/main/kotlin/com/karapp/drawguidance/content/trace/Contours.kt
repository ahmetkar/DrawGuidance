package com.karapp.drawguidance.content.trace

/**
 * Bölge sınırlarının izlenmesi (piksel kenarı takibi). Sınırlar, üç ya da daha çok bölgenin buluştuğu köşelerde
 * (kavşak) "yay"lara bölünür; iki komşu bölge ortak yayı paylaşır ve yay yalnız bir kez sadeleştirilir — böylece
 * komşular arasında boşluk ya da taşma olmaz.
 *
 * Her bölge yalnız dış sınırıyla (delikleri dahil) doldurulur; deliklerdeki bölgeler daha küçük olduğundan sonra çizilip
 * üstte kalır (ressam sırası). Yalnız saydam alan içeren delikler şekilden gerçekten oyulur (köprüyle tek yola eklenir).
 */
internal class Tracer(private val map: RegionMap) {

    /** Yay: ham noktalar (x,y çiftleri, piksel), [loop]: kavşaksız kapalı sınır; açık yayın uçları kavşak köşeleridir. */
    class Arc(val raw: FloatArray, val loop: Boolean) {
        var fitted: FloatArray = FloatArray(0)
    }

    /** Sınır: yay başvuruları (yay*2 + ters?1:0) ve piksel alanı (dış sınırda pozitif). */
    class Contour(val refs: IntArray, val area: Long)

    private val w = map.w
    private val h = map.h
    private val rid = map.rid
    private val hCount = w * (h + 1)
    private val arcOf = IntArray(hCount + (w + 1) * h) { -1 }
    val arcs = ArrayList<Arc>()

    /** Yatay kenar, üstündeki bölgenin dış sınırında mı. */
    private val outerAbove = BooleanArray(hCount)
    private val holeDone = BooleanArray(hCount)

    /** Çizilen bölgelerin dış sınırları (bölge sırasıyla; çizilmeyenler null). */
    val outer = arrayOfNulls<Contour>(map.count)
    /** Oyulacak (saydam içeren) delik sınırları. */
    val holes = arrayOfNulls<ArrayList<Contour>>(map.count)

    private val stepX = IntList(1024)
    private val stepY = IntList(1024)
    private val stepD = IntList(1024)

    private fun at(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) OUT else rid[y * w + x]

    fun traceAll(cancel: () -> Boolean) {
        val n = w * h
        val first = IntArray(map.count) { -1 }
        for (p in 0 until n) if (first[rid[p]] < 0) first[rid[p]] = p
        for (r in 0 until map.count) {
            if (map.label[r] < 0) continue
            if ((r and 255) == 0) checkCancel(cancel)
            val p = first[r]
            outer[r] = trace(r, p % w, p / w, 0, markOuter = true, markHole = false)
        }
        // Kenara değmeyen saydam bölgeler: onları çevreleyen her bölgede deliği oy
        val border = BooleanArray(map.count)
        for (x in 0 until w) { border[rid[x]] = true; border[rid[(h - 1) * w + x]] = true }
        for (y in 0 until h) { border[rid[y * w]] = true; border[rid[y * w + w - 1]] = true }
        val seen = IntArray(map.count) { -1 }
        for (t in 0 until map.count) {
            if (map.label[t] >= 0 || border[t]) continue
            checkCancel(cancel)
            val p = first[t]
            val tx = p % w; val ty = p / w
            var prev = t
            for (y in ty - 1 downTo 0) {
                val cur = rid[y * w + tx]
                if (cur == prev) continue
                prev = cur
                if (map.label[cur] < 0 || seen[cur] == t) continue
                seen[cur] = t
                val e = (y + 1) * w + tx
                if (outerAbove[e] || holeDone[e]) continue
                val c = trace(cur, tx + 1, y + 1, 2, markOuter = false, markHole = true)
                (holes[cur] ?: ArrayList<Contour>().also { holes[cur] = it }).add(c)
            }
        }
    }

    /**
     * [q] bölgesinin sınırını ([x0],[y0]) köşesinden [d0] yönünde, bölge sağda kalacak şekilde izler.
     * Yönler: 0 sağ, 1 aşağı, 2 sol, 3 yukarı. Çapraz komşular bağlı sayılmaz (4-bağlılık).
     */
    private fun trace(q: Int, x0: Int, y0: Int, d0: Int, markOuter: Boolean, markHole: Boolean): Contour {
        stepX.clear(); stepY.clear(); stepD.clear()
        var vx = x0; var vy = y0; var d = d0
        var area2 = 0L
        do {
            stepX.add(vx); stepY.add(vy); stepD.add(d)
            val nx = vx + DX[d]; val ny = vy + DY[d]
            area2 += vx.toLong() * ny - nx.toLong() * vy
            if (d == 2) {
                val e = vy * w + vx - 1
                if (markOuter) outerAbove[e] = true
                if (markHole) holeDone[e] = true
            }
            vx = nx; vy = ny
            val right: Int; val left: Int
            when (d) {
                0 -> { right = at(vx, vy); left = at(vx, vy - 1) }
                1 -> { right = at(vx - 1, vy); left = at(vx, vy) }
                2 -> { right = at(vx - 1, vy - 1); left = at(vx - 1, vy) }
                else -> { right = at(vx, vy - 1); left = at(vx - 1, vy - 1) }
            }
            d = if (right != q) (d + 1) and 3 else if (left == q) (d + 3) and 3 else d
        } while (vx != x0 || vy != y0 || d != d0)

        val m = stepX.size
        val junctions = IntList()
        for (i in 0 until m) if (isJunction(stepX[i], stepY[i])) junctions.add(i)
        val refs = IntList()
        if (junctions.size == 0) {
            val a = arcOf[edgeId(0)]
            if (a >= 0) refs.add(a * 2 + 1) else refs.add(newArc(0, m, loop = true) * 2)
        } else {
            for (k in 0 until junctions.size) {
                val i = junctions[k]
                val j = junctions[(k + 1) % junctions.size]
                var len = (j - i + m) % m
                if (len == 0) len = m
                val a = arcOf[edgeId(i)]
                if (a >= 0) refs.add(a * 2 + 1) else refs.add(newArc(i, len, loop = false) * 2)
            }
        }
        return Contour(IntArray(refs.size) { refs[it] }, area2 / 2)
    }

    private fun edgeId(step: Int): Int {
        val vx = stepX[step]; val vy = stepY[step]
        return when (stepD[step]) {
            0 -> vy * w + vx
            2 -> vy * w + vx - 1
            1 -> hCount + vy * (w + 1) + vx
            else -> hCount + (vy - 1) * (w + 1) + vx
        }
    }

    /** [start]'tan [len] adımlık yay; açık yayda uç köşeler de eklenir. */
    private fun newArc(start: Int, len: Int, loop: Boolean): Int {
        val m = stepX.size
        val id = arcs.size
        val pts = FloatArray((len + if (loop) 0 else 2) * 2)
        var o = 0
        if (!loop) { pts[o++] = stepX[start].toFloat(); pts[o++] = stepY[start].toFloat() }
        for (k in 0 until len) {
            val s = (start + k) % m
            val d = stepD[s]
            pts[o++] = stepX[s] + DX[d] * 0.5f
            pts[o++] = stepY[s] + DY[d] * 0.5f
            arcOf[edgeId(s)] = id
        }
        if (!loop) {
            val e = (start + len) % m
            pts[o++] = stepX[e].toFloat(); pts[o] = stepY[e].toFloat()
        }
        arcs += Arc(pts, loop)
        return id
    }

    /** Üç ya da daha çok bölgenin buluştuğu, çapraz (satranç) dizilimli ya da görüntü köşesi olan nokta. */
    private fun isJunction(vx: Int, vy: Int): Boolean {
        if ((vx == 0 || vx == w) && (vy == 0 || vy == h)) return true
        val a = at(vx - 1, vy - 1); val b = at(vx, vy - 1); val c = at(vx - 1, vy); val d = at(vx, vy)
        var distinct = 1
        if (b != a) distinct++
        if (c != a && c != b) distinct++
        if (d != a && d != b && d != c) distinct++
        if (distinct >= 3) return true
        return distinct == 2 && a == d && b == c
    }

    /** Tüm yayları sadeleştirir/eğri uydurur. */
    fun fit(p: FitParams, cancel: () -> Boolean) {
        val out = FloatList(4096)
        for ((i, arc) in arcs.withIndex()) {
            if ((i and 255) == 0) checkCancel(cancel)
            out.clear()
            val n = arc.raw.size / 2
            if (arc.loop) CurveFit.loop(arc.raw, n, p, out) else CurveFit.open(arc.raw, n, p, out)
            arc.fitted = out.toArray()
        }
    }

    /** Sınırın (sadeleştirilmiş) çokgeni, piksel biriminde; ardışık tekrarlar atılır. */
    fun polygon(c: Contour): FloatArray {
        val out = FloatList(256)
        fun add(x: Float, y: Float) {
            val s = out.size
            if (s >= 2 && kotlin.math.abs(out[s - 2] - x) < 1e-4f && kotlin.math.abs(out[s - 1] - y) < 1e-4f) return
            out.add(x, y)
        }
        for (ref in c.refs) {
            val arc = arcs[ref shr 1]
            val f = arc.fitted
            val n = f.size / 2
            val count = if (arc.loop) n else n - 1
            if ((ref and 1) == 0) {
                for (i in 0 until count) add(f[i * 2], f[i * 2 + 1])
            } else {
                for (k in 0 until count) { val i = n - 1 - k; add(f[i * 2], f[i * 2 + 1]) }
            }
        }
        // Kapanışta ilk noktanın tekrarı
        while (out.size >= 4 && kotlin.math.abs(out[0] - out[out.size - 2]) < 1e-4f &&
            kotlin.math.abs(out[1] - out[out.size - 1]) < 1e-4f) { out.pop(); out.pop() }
        return out.toArray()
    }

    companion object {
        private const val OUT = -2
        private val DX = intArrayOf(1, 0, -1, 0)
        private val DY = intArrayOf(0, 1, 0, -1)
    }
}

/**
 * Delikleri dış çokgene sıfır genişlikli köprülerle ekler (anahtar deliği): deliğin en üst noktasından yukarı dikey ışının
 * ilk kestiği kenara bağlanır. Delikler ters yönde dolaşıldığından hem sıfırdan-farklı hem çift-tek dolgu kuralında boş kalır.
 */
internal fun bridgeHoles(outer: FloatArray, holes: List<FloatArray>): FloatArray {
    var poly = outer
    val sorted = holes.filter { it.size >= 6 }.sortedBy { h -> topIndex(h).let { h[it * 2 + 1] } }
    for (hole in sorted) {
        val hn = hole.size / 2
        val ti = topIndex(hole)
        val hx = hole[ti * 2]; val hy = hole[ti * 2 + 1]
        val n = poly.size / 2
        var bestSeg = -1; var bestY = -Float.MAX_VALUE
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ax = poly[i * 2]; val ay = poly[i * 2 + 1]; val bx = poly[j * 2]; val by = poly[j * 2 + 1]
            if ((ax - hx) * (bx - hx) > 0f || ax == bx) continue
            val t = (hx - ax) / (bx - ax)
            val y = ay + t * (by - ay)
            if (y < hy && y > bestY) { bestY = y; bestSeg = i }
        }
        val ix: Float; val iy: Float
        if (bestSeg < 0) {
            // Yedek: en yakın köşe
            var bi = 0; var bd = Float.MAX_VALUE
            for (i in 0 until n) {
                val dd = (poly[i * 2] - hx).let { it * it } + (poly[i * 2 + 1] - hy).let { it * it }
                if (dd < bd) { bd = dd; bi = i }
            }
            bestSeg = bi; ix = poly[bi * 2]; iy = poly[bi * 2 + 1]
        } else { ix = hx; iy = bestY }
        val res = FloatList(poly.size + hole.size + 8)
        for (i in 0..bestSeg) res.add(poly[i * 2], poly[i * 2 + 1])
        res.add(ix, iy)
        for (k in 0..hn) { val i = (ti + k) % hn; res.add(hole[i * 2], hole[i * 2 + 1]) }
        res.add(ix, iy)
        for (i in bestSeg + 1 until n) res.add(poly[i * 2], poly[i * 2 + 1])
        poly = res.toArray()
    }
    return poly
}

private fun topIndex(h: FloatArray): Int {
    var best = 0
    for (i in 1 until h.size / 2) {
        val y = h[i * 2 + 1]; val by = h[best * 2 + 1]
        if (y < by || (y == by && h[i * 2] < h[best * 2])) best = i
    }
    return best
}

/** Kapalı çokgenin işaretli alanı (y aşağı: saat yönü pozitif). */
internal fun signedArea(p: FloatArray): Double {
    val n = p.size / 2
    var s = 0.0
    for (i in 0 until n) {
        val j = (i + 1) % n
        s += p[i * 2].toDouble() * p[j * 2 + 1] - p[j * 2].toDouble() * p[i * 2 + 1]
    }
    return s / 2
}
