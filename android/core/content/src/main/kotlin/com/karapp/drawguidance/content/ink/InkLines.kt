package com.karapp.drawguidance.content.ink

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * v38 çizgi önerisinin çoklu çizgi adımı (şekil ve yazı önerisinden ayrı algoritma; sinir ağı yok, klasik makine
 * öğrenmesi ve geometri): her çizgi önce kendi başına düzeltilir ([InkShapes]: en küçük kareler/kovaryansla çember ve
 * elips, Ramer–Douglas–Peucker köşe bulmayla doğru, kırık çizgi, üçgen, dörtgen, çokgen; tanınmayan çizgi Gauss
 * yumuşatmayla). Sonra birden çok çizgi birlikte ele alınır: açık çizgilerin birbirine yakın uç noktaları **tek
 * bağlantılı (single-linkage) hiyerarşik kümeleme** ile kümelenir ve her küme tek noktaya yapıştırılır — iki düz
 * kenarın ucu doğruların kesişim noktasına, diğerleri kümenin ağırlık merkezine. Böylece ayrı çizgilerle çizilen
 * köşeler (ör. dört kenarla çizilen kutu, ok, yıldız) birleşir.
 */
object InkLines {

    /** Bir çizginin düzeltmesi: [points] yeni noktalar, [kind] tanınan geometri (yumuşatılan serbest çizgide null). */
    class Item(val kind: InkShapeKind?, val points: List<InkPoint>) {
        val closed: Boolean
            get() = kind == InkShapeKind.CIRCLE || kind == InkShapeKind.ELLIPSE || kind == InkShapeKind.TRIANGLE ||
                kind == InkShapeKind.RECTANGLE || kind == InkShapeKind.SQUARE || kind == InkShapeKind.POLYGON ||
                (points.size > 2 && points.first() == points.last())
    }

    /** Yapıştırma sonucu: düzeltmeler (aynı sırada) ve birleştirilen uç kümesi sayısı. */
    class Result(val items: List<Item>, val joints: Int)

    /**
     * [items] çizgilerin tek tek düzeltmeleri; açık çizgilerin birbirine [eps] (null: çizgilerin ortanca boyunun
     * %[EPS_SHARE]'i, [EPS_MIN]..[EPS_MAX] px) kadar yakın uçları birleştirilir.
     */
    fun snapEndpoints(items: List<Item>, eps: Float? = null): Result {
        if (items.size < 2) return Result(items, 0)
        val e = eps ?: run {
            val ext = items.map { extent(it.points) }.sorted()
            (ext[ext.size / 2] * EPS_SHARE).coerceIn(EPS_MIN, EPS_MAX)
        }
        // Uç noktaları: (çizgi, 0 = baş / 1 = son)
        val ends = ArrayList<Pair<Int, Int>>()
        items.forEachIndexed { i, it -> if (!it.closed && it.points.size >= 2) { ends += i to 0; ends += i to 1 } }
        if (ends.size < 2) return Result(items, 0)
        fun pt(end: Pair<Int, Int>): InkPoint = items[end.first].points.let { if (end.second == 0) it.first() else it.last() }
        // Tek bağlantılı kümeleme: aralarındaki uzaklık ≤ eps olan farklı çizgilerin uçları aynı kümede (birleşim-bul)
        val parent = IntArray(ends.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; var y = x; while (parent[y] != r) { val n = parent[y]; parent[y] = r; y = n }; return r }
        for (a in ends.indices) for (b in a + 1 until ends.size) {
            if (ends[a].first == ends[b].first) continue
            val pa = pt(ends[a]); val pb = pt(ends[b])
            if (hypot(pa.x - pb.x, pa.y - pb.y) <= e) parent[find(a)] = find(b)
        }
        val clusters = ends.indices.groupBy { find(it) }.values.filter { c -> c.map { ends[it].first }.distinct().size >= 2 }
        if (clusters.isEmpty()) return Result(items, 0)
        val out = items.map { it.points.toMutableList() }
        var joints = 0
        for (c in clusters) {
            val members = c.map { ends[it] }
            val target = target(members.map { m -> items[m.first] to m.second }, members.map(::pt), e) ?: continue
            for (m in members) move(out[m.first], items[m.first].kind, m.second, target)
            joints++
        }
        return Result(items.mapIndexed { i, it -> Item(it.kind, out[i]) }, joints)
    }

    /**
     * Kümenin yapışacağı nokta: iki düz uç (doğrular arası açı > 20°) varsa doğruların kesişimi (kümenin yakınındaysa),
     * yoksa ağırlık merkezi.
     */
    private fun target(ends: List<Pair<Item, Int>>, pts: List<InkPoint>, eps: Float): InkPoint? {
        val cx = pts.map { it.x }.average().toFloat(); val cy = pts.map { it.y }.average().toFloat()
        if (ends.size == 2) {
            val d1 = direction(ends[0].first.points, ends[0].second)
            val d2 = direction(ends[1].first.points, ends[1].second)
            if (d1 != null && d2 != null) {
                val cross = d1.second.x * d2.second.y - d1.second.y * d2.second.x
                if (abs(cross) > SIN_MIN_ANGLE) {
                    val p = d1.first; val q = d2.first
                    val t = ((q.x - p.x) * d2.second.y - (q.y - p.y) * d2.second.x) / cross
                    val ix = p.x + d1.second.x * t; val iy = p.y + d1.second.y * t
                    if (hypot(ix - cx, iy - cy) <= 2f * eps) return InkPoint(ix, iy)
                }
            }
        }
        return InkPoint(cx, cy)
    }

    /**
     * Ucun düz kısmının yönü (birim vektör) ve ucun kendisi: uçtan geriye, kirişe en çok 1,5 px uzak kalan noktalar
     * düz sayılır; düz kısım çizginin %25'inden kısaysa null (eğri uç).
     */
    private fun direction(p: List<InkPoint>, end: Int): Pair<InkPoint, InkPoint>? {
        val run = straightRun(p, end)
        val total = length(p)
        if (run.second < max(0.25f * total, 8f)) return null
        val a = p[run.first]; val b = if (end == 0) p.first() else p.last()
        val dx = b.x - a.x; val dy = b.y - a.y
        val l = hypot(dx, dy).takeIf { it > 1e-3f } ?: return null
        return b to InkPoint(dx / l, dy / l)
    }

    /** Uçtan geriye düz kalan kısmın başladığı nokta indisi ve uzunluğu. */
    private fun straightRun(p: List<InkPoint>, end: Int): Pair<Int, Float> {
        val n = p.size
        val idx = if (end == 0) (0 until n).toList() else (n - 1 downTo 0).toList()
        val e = p[idx[0]]
        var last = idx[0]
        for (k in 2 until idx.size) {
            val a = p[idx[k]]
            var ok = true
            for (m in 1 until k) if (segDist(p[idx[m]], e, a) > STRAIGHT_TOL) { ok = false; break }
            if (!ok) break
            last = idx[k]
        }
        if (last == idx[0] && n >= 2) last = idx[1]
        return last to hypot(p[last].x - e.x, p[last].y - e.y)
    }

    /** Ucu [target]'a taşır: doğruda doğru yeniden kurulur; düz uç kısmı düz kalır; eğri uçta yumuşak geçişle. */
    private fun move(p: MutableList<InkPoint>, kind: InkShapeKind?, end: Int, target: InkPoint) {
        if (p.size < 2) return
        if (kind == InkShapeKind.LINE) {
            val a = if (end == 0) target else p.first()
            val b = if (end == 0) p.last() else target
            val line = InkShapes.densify(listOf(a, b), false)
            p.clear(); p.addAll(line)
            return
        }
        val n = p.size
        val endIdx = if (end == 0) 0 else n - 1
        val (anchor, runLen) = straightRun(p, end)
        val total = length(p)
        if (kind == InkShapeKind.POLYLINE || runLen >= 0.3f * total) {
            // Düz son kenar: çapa ile yeni uç arasında doğrusal yeniden dağıtım (kenar düz kalır)
            val a = p[anchor]
            val range = if (end == 0) (0 until anchor) else (anchor + 1 until n)
            val count = range.count()
            range.forEachIndexed { k, i ->
                val t = if (end == 0) 1f - (k.toFloat() / count) else (k + 1f) / count
                p[i] = InkPoint(a.x + (target.x - a.x) * t, a.y + (target.y - a.y) * t)
            }
            return
        }
        // Eğri uç: yay boyunca azalan ağırlıkla kaydırma (çizginin en çok %35'i)
        val dx = target.x - p[endIdx].x; val dy = target.y - p[endIdx].y
        val span = max(1f, min(0.35f * total, 4f * hypot(dx, dy) + 12f))
        var acc = 0f
        val order = if (end == 0) (0 until n) else (n - 1 downTo 0)
        var prev = p[endIdx]
        for (i in order) {
            acc += hypot(p[i].x - prev.x, p[i].y - prev.y)
            prev = p[i]
            if (acc > span) break
            val w = 1f - acc / span
            p[i] = InkPoint(p[i].x + dx * w, p[i].y + dy * w)
        }
    }

    private fun extent(p: List<InkPoint>): Float {
        if (p.isEmpty()) return 0f
        return max(p.maxOf { it.x } - p.minOf { it.x }, p.maxOf { it.y } - p.minOf { it.y })
    }

    private fun length(p: List<InkPoint>): Float { var l = 0f; for (i in 1 until p.size) l += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y); return l }

    private fun segDist(p: InkPoint, a: InkPoint, b: InkPoint): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val l2 = dx * dx + dy * dy
        if (l2 < 1e-6f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** Çember uydurması: merkez, yarıçap ve yarıçapa göre ortalama karesel hata. */
    class Circle(val cx: Float, val cy: Float, val r: Float, val rms: Float)

    /**
     * v38 cebirsel çember uydurması (Kåsa en küçük kareler): x² + y² + Dx + Ey + F = 0. Nokta azsa ya da noktalar
     * neredeyse doğrusalsa null.
     */
    fun fitCircle(points: List<InkPoint>): Circle? {
        if (points.size < 5) return null
        val mx = points.sumOf { it.x.toDouble() } / points.size; val my = points.sumOf { it.y.toDouble() } / points.size
        var suu = 0.0; var svv = 0.0; var suv = 0.0; var suuu = 0.0; var svvv = 0.0; var suvv = 0.0; var svuu = 0.0
        for (p in points) {
            val u = p.x - mx; val v = p.y - my
            suu += u * u; svv += v * v; suv += u * v
            suuu += u * u * u; svvv += v * v * v; suvv += u * v * v; svuu += v * u * u
        }
        val det = suu * svv - suv * suv
        if (abs(det) < 1e-9) return null
        val bu = 0.5 * (suuu + suvv); val bv = 0.5 * (svvv + svuu)
        val uc = (bu * svv - bv * suv) / det; val vc = (bv * suu - bu * suv) / det
        val cx = (uc + mx).toFloat(); val cy = (vc + my).toFloat()
        val r = points.map { hypot(it.x - cx, it.y - cy) }.average().toFloat()
        if (!r.isFinite() || r <= 0f) return null
        val rms = kotlin.math.sqrt(points.map { val d = hypot(it.x - cx, it.y - cy) - r; (d * d).toDouble() }.average()).toFloat() / r
        return Circle(cx, cy, r, rms)
    }

    /** Çizginin [c] çemberi üstünde kapladığı açı (radyan, işaretli; çizim yönünde). */
    fun sweep(points: List<InkPoint>, c: Circle): Double {
        var total = 0.0
        var prev = kotlin.math.atan2((points[0].y - c.cy).toDouble(), (points[0].x - c.cx).toDouble())
        for (i in 1 until points.size) {
            val a = kotlin.math.atan2((points[i].y - c.cy).toDouble(), (points[i].x - c.cx).toDouble())
            var d = a - prev
            while (d > Math.PI) d -= 2 * Math.PI
            while (d < -Math.PI) d += 2 * Math.PI
            total += d; prev = a
        }
        return total
    }

    /** Çizginin [c] çemberindeki düzgün yayı (aynı başlangıç açısı ve kaplama, sık örnekli). */
    fun arcOn(points: List<InkPoint>, c: Circle): List<InkPoint> {
        val a0 = kotlin.math.atan2((points[0].y - c.cy).toDouble(), (points[0].x - c.cx).toDouble())
        val sw = sweep(points, c)
        val n = max(8, (abs(sw) * c.r / 3.0).toInt().coerceAtMost(400))
        return (0..n).map { i -> val a = a0 + sw * i / n; InkPoint((c.cx + c.r * kotlin.math.cos(a)).toFloat(), (c.cy + c.r * kotlin.math.sin(a)).toFloat()) }
    }

    /**
     * v38 yaylar: birden çok açık çizgi birlikte tek çembere oturuyorsa (ör. çemberi iki yayla çizmek) her çizgi o
     * ortak çemberin yayına; oturmuyorsa tek tek yay gibi duran (≥[ARC_MIN_SWEEP], hata ≤[ARC_MAX_RMS]) çizgiler
     * kendi yaylarına döner. Yay olmayanlar için null.
     */
    fun arcs(strokes: List<List<InkPoint>>): List<List<InkPoint>?> {
        val usable = strokes.map { it.size >= 5 }
        if (strokes.count { it.size >= 5 } >= 2) {
            val common = fitCircle(strokes.filterIndexed { i, _ -> usable[i] }.flatten())
            if (common != null && common.rms <= ARC_MAX_RMS &&
                strokes.withIndex().all { (i, s) -> !usable[i] || abs(sweep(s, common)) >= ARC_MIN_SWEEP }) {
                return strokes.mapIndexed { i, s -> if (usable[i]) arcOn(s, common) else null }
            }
        }
        return strokes.map { s ->
            if (s.size < 5) null
            else fitCircle(s)?.takeIf { c -> c.rms <= ARC_MAX_RMS && abs(sweep(s, c)) >= ARC_MIN_SWEEP }?.let { c -> arcOn(s, c) }
        }
    }

    /** Yay sayılmak için en küçük kaplama (~40°) ve en büyük göreli hata. */
    const val ARC_MIN_SWEEP = 0.7
    const val ARC_MAX_RMS = 0.035f

    /** Yapıştırma uzaklığı: çizgilerin ortanca boyunun bu payı (px sınırlarıyla). */
    const val EPS_SHARE = 0.12f
    const val EPS_MIN = 8f
    const val EPS_MAX = 48f
    /** Düz kısım toleransı (px). */
    private const val STRAIGHT_TOL = 1.5f
    /** Kesişim için doğrular arasındaki en küçük açının sinüsü (~20°). */
    private const val SIN_MIN_ANGLE = 0.34f
}
