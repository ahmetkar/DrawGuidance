package com.karapp.drawguidance.content.ink

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Giriş temizliği, kaba kümeler (karakter yüksekliği tahmini) ve "yazı mı?" sezgileri. */
internal object Prep {

    fun polys(strokes: List<InkStroke>): List<Pair<Int, Poly>> {
        val out = ArrayList<Pair<Int, Poly>>(strokes.size)
        strokes.forEachIndexed { i, s ->
            val xs = ArrayList<Float>(s.points.size); val ys = ArrayList<Float>(s.points.size)
            for (p in s.points) {
                if (!p.x.isFinite() || !p.y.isFinite()) continue
                if (xs.isNotEmpty() && xs.last() == p.x && ys.last() == p.y) continue
                xs += p.x; ys += p.y
            }
            if (xs.isNotEmpty()) out += i to Poly(xs.toFloatArray(), ys.toFloatArray())
        }
        return out
    }

    class Cluster(val members: List<Int>, val box: Box)

    /** Yatayda belirgin örtüşen (küçük olmayan) çizgilerin birleşimi — kabaca karakterler. */
    fun clusters(ps: List<Poly>, tinyFrac: Float = 0.2f): List<Cluster> {
        if (ps.isEmpty()) return emptyList()
        val mx = max(ps.maxOf { it.extent }, 1e-4f)
        var big = ps.indices.filter { ps[it].extent >= tinyFrac * mx }
        if (big.isEmpty()) big = ps.indices.toList()
        val parent = IntArray(ps.size) { it }
        fun find(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; parent[i] = r; return r }
        val floor = 0.12f * mx
        for (a in big.indices) for (b in a + 1 until big.size) {
            val p = ps[big[a]]; val q = ps[big[b]]
            val wp = max(p.w, floor); val wq = max(q.w, floor)
            val ovl = min(p.cx + wp / 2, q.cx + wq / 2) - max(p.cx - wp / 2, q.cx - wq / 2)
            if (ovl >= 0.3f * min(wp, wq)) parent[find(big[a])] = find(big[b])
        }
        return big.groupBy { find(it) }.values.map { m -> Cluster(m, Box.of(m.map { ps[it] })) }
            .sortedBy { it.box.cx }
    }

    /** Tipik karakter yüksekliği (büyük harf/rakam boyu civarı). */
    fun capHeight(ps: List<Poly>): Float {
        val cl = clusters(ps)
        if (cl.isEmpty()) return 1f
        val sizes = cl.map { max(it.box.h, 0.7f * it.box.w) }.sorted()
        val mx = sizes.last()
        val tall = sizes.filter { it >= 0.6f * mx }
        return max(tall[tall.size / 2], 1e-3f)
    }

    fun isFrame(ps: List<Poly>, b: Box): Boolean {
        val size = max(b.w, b.h)
        if (b.w < 0.25f * size || b.h < 0.25f * size) return false
        val tol = 0.07f * size
        val bins = 20
        val top = BooleanArray(bins); val bot = BooleanArray(bins); val lef = BooleanArray(bins); val rig = BooleanArray(bins)
        for (p in ps) {
            val r = Geo.resample(p, size / 80f)
            for (i in 0 until r.n) {
                val x = r.x[i]; val y = r.y[i]
                val bx = ((x - b.minX) / max(b.w, 1e-4f) * bins).toInt().coerceIn(0, bins - 1)
                val by = ((y - b.minY) / max(b.h, 1e-4f) * bins).toInt().coerceIn(0, bins - 1)
                if (y - b.minY < tol) top[bx] = true
                if (b.maxY - y < tol) bot[bx] = true
                if (x - b.minX < tol) lef[by] = true
                if (b.maxX - x < tol) rig[by] = true
            }
        }
        fun cov(a: BooleanArray) = a.count { it } / bins.toFloat()
        return cov(top) >= 0.75f && cov(bot) >= 0.75f && cov(lef) >= 0.75f && cov(rig) >= 0.75f
    }

    /**
     * [script] v37: Japonca/Korece için çizgi sınırı [Handwriting.MAX_STROKES_CJK], kümede çok çizgiye izin verilir ve
     * çerçeve kuralı yalnız tek çizgide uygulanır (口, 日, 田 gibi kutu biçimli karakterler).
     */
    /** v37: çizgilerin en az %80'i aynı yönde (45° dilimleri) düz çizgi: tarama. */
    internal fun isHatching(ps: List<Poly>): Boolean {
        if (ps.size < 8) return false
        val bins = IntArray(4)
        var straight = 0
        for (p in ps) {
            val dx = p.x[p.n - 1] - p.x[0]
            val dy = p.y[p.n - 1] - p.y[0]
            val chord = hypot(dx, dy)
            if (chord <= 0f || p.length > 1.15f * chord) continue
            straight++
            var a = kotlin.math.atan2(dy, dx)
            if (a < 0f) a += kotlin.math.PI.toFloat()
            bins[(a / (kotlin.math.PI.toFloat() / 4f) + 0.5f).toInt() % 4]++
        }
        return straight >= 0.8f * ps.size && bins.max() >= 0.8f * ps.size
    }

    fun looksLikeWriting(ps: List<Poly>, script: InkScript = InkScript.LATIN): Boolean {
        val cjk = script == InkScript.JAPANESE || script == InkScript.KOREAN
        if (ps.isEmpty() || ps.size > (if (cjk) Handwriting.MAX_STROKES_CJK else Handwriting.MAX_STROKES)) return false
        val all = Box.of(ps)
        if (max(all.w, all.h) <= 0f) return false
        val hc = capHeight(ps)
        for (p in ps) {
            if (p.extent < 0.25f * hc) continue
            val t = Geo.turning(p, max(p.extent, 0.5f * hc) / 28f)
            if (t.absTurns > 4.6f || (t.absTurns > 1.8f && Geo.winding(p) > 1.8f)) return false // sarmal / karalama
            if (t.absTurns > 2.6f && Geo.selfIntersections(p, max(p.extent, 0.5f * hc) / 20f) >= 3) return false // karalama
            if (t.sharpTurns >= 6 || t.reversals >= 5) return false // zikzak / tarama
        }
        if (ps.size == 1) {
            val p = ps[0]
            val chord = hypot(p.x[p.n - 1] - p.x[0], p.y[p.n - 1] - p.y[0])
            // tek düz yatay çizgi: çizim çizgisi (tek başına "−" önerilmez)
            if (p.w > 4f * p.h && p.length < 1.15f * chord) return false
        }
        // çerçeve: mürekkep sınır kutusunun dört kenarı boyunca uzanıyor (dikdörtgen/kutu çizimi; metinde olmaz)
        if ((!cjk || ps.size == 1) && isFrame(ps, all)) return false
        // tek yerde üst üste çok çizgi: tarama / boyama
        for (c in clusters(ps)) if (c.members.size > (if (cjk) 24 else 7)) return false
        // v37 Japonca/Korece çok çizgi: çizgilerin çoğu aynı yönde düz çizgiyse tarama (gerçek satırda yönler karışık)
        if (cjk && ps.size > Handwriting.MAX_STROKES && isHatching(ps)) return false
        // çok satırlı / sütun gibi dağınık mürekkep
        if (all.h > 3.2f * hc && ps.size > 1) return false
        return true
    }
}
