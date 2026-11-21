package com.karapp.drawguidance.data.model

import com.karapp.drawguidance.data.model.V56Bool.P
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * v56 aşama 9C (2E, yalnız Resim): yol işlemleri (birleştir, önden çıkar, kesişim, dışla, böl, kırp, birleştir, kırp,
 * anahat, şekil oluşturucu), konturu yola çevir, bıçak, yol ayır/kapat/ters çevir, efektler (öteleme, zarf, kıvırma,
 * pürüz, zikzak, dalga). iOS `V56PathOps.swift` ile aynı hesap.
 */

/** Çokgen boole işlemleri (kenar sınıflandırma: kesişimlerde bölünen kenarlar öteki şeklin içinde mi dışında mı). */
object V56Bool {
    data class P(val x: Double, val y: Double)
    enum class Op { UNION, INTERSECT, SUBTRACT, EXCLUDE }

    private const val EPS = 1e-7

    fun area(r: List<P>): Double {
        var a = 0.0
        for (i in r.indices) { val p = r[i]; val q = r[(i + 1) % r.size]; a += p.x * q.y - q.x * p.y }
        return a / 2.0
    }

    /** Çift-tek kuralıyla [p] halkaların içinde mi. */
    fun inside(p: P, rings: List<List<P>>): Boolean {
        var c = false
        for (r in rings) {
            var j = r.size - 1
            for (i in r.indices) {
                val a = r[i]; val b = r[j]
                if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) c = !c
                j = i
            }
        }
        return c
    }

    /** Tekrarlanan noktaları atar, 3'ten az noktalı halkaları atar, yönleri iç içeliğe göre düzeltir (dış: pozitif alan). */
    fun normalize(rings: List<List<P>>): List<List<P>> {
        val clean = rings.map { r ->
            val out = ArrayList<P>()
            for (p in r) if (out.isEmpty() || out.last() != p) out += p
            while (out.size > 1 && out.first() == out.last()) out.removeAt(out.size - 1)
            out
        }.filter { it.size >= 3 && abs(area(it)) > 1e-9 }
        return clean.mapIndexed { i, r ->
            val probe = sample(r)
            val depth = clean.indices.count { j -> j != i && inside(probe, listOf(clean[j])) }
            val positive = area(r) > 0
            if ((depth % 2 == 0) == positive) r else r.reversed()
        }
    }

    /** Halkanın içinde, kenara çok yakın bir nokta (ilk kenarın ortasından içe doğru). */
    private fun sample(r: List<P>): P {
        val a = r[0]; val b = r[1]
        val mx = (a.x + b.x) / 2; val my = (a.y + b.y) / 2
        val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1e-9)
        val nx = -(b.y - a.y) / len; val ny = (b.x - a.x) / len
        val s = if (area(r) > 0) 1e-4 else -1e-4
        return P(mx + nx * s, my + ny * s)
    }

    private class Seg(val a: P, val b: P, val src: Int) {
        val cuts = ArrayList<Pair<Double, P>>()
    }

    private fun segsOf(rings: List<List<P>>, src: Int): List<Seg> =
        rings.flatMap { r -> r.indices.map { i -> Seg(r[i], r[(i + 1) % r.size], src) } }

    private fun cross(ax: Double, ay: Double, bx: Double, by: Double) = ax * by - ay * bx

    private fun intersect(s: Seg, t: Seg) {
        val rx = s.b.x - s.a.x; val ry = s.b.y - s.a.y
        val qx = t.b.x - t.a.x; val qy = t.b.y - t.a.y
        val den = cross(rx, ry, qx, qy)
        val wx = t.a.x - s.a.x; val wy = t.a.y - s.a.y
        val lr = rx * rx + ry * ry; val lq = qx * qx + qy * qy
        if (lr < EPS * EPS || lq < EPS * EPS) return
        if (abs(den) <= 1e-12 * sqrt(lr * lq)) {
            // Paralel: aynı doğrudaysa uç noktaları birbirine böler
            if (abs(cross(wx, wy, rx, ry)) > 1e-9 * sqrt(lr) * 1e3) return
            fun proj(p: P, o: Seg): Double { val dx = o.b.x - o.a.x; val dy = o.b.y - o.a.y; return ((p.x - o.a.x) * dx + (p.y - o.a.y) * dy) / (dx * dx + dy * dy) }
            for (p in listOf(t.a, t.b)) { val u = proj(p, s); if (u > 1e-9 && u < 1 - 1e-9) s.cuts += u to p }
            for (p in listOf(s.a, s.b)) { val u = proj(p, t); if (u > 1e-9 && u < 1 - 1e-9) t.cuts += u to p }
            return
        }
        val u = cross(wx, wy, qx, qy) / den
        val v = cross(wx, wy, rx, ry) / den
        if (u < -1e-9 || u > 1 + 1e-9 || v < -1e-9 || v > 1 + 1e-9) return
        val p = when {
            u <= 1e-9 -> s.a; u >= 1 - 1e-9 -> s.b; v <= 1e-9 -> t.a; v >= 1 - 1e-9 -> t.b
            else -> P(s.a.x + rx * u, s.a.y + ry * u)
        }
        if (u > 1e-9 && u < 1 - 1e-9) s.cuts += u to p
        if (v > 1e-9 && v < 1 - 1e-9) t.cuts += v to p
    }

    private class Edge(val a: P, val b: P) { var used = false }

    private fun pieces(s: Seg): List<Pair<P, P>> {
        val pts = ArrayList<P>()
        pts += s.a
        s.cuts.sortedBy { it.first }.forEach { (_, p) -> if (pts.last() != p) pts += p }
        if (pts.last() != s.b) pts += s.b
        return (0 until pts.size - 1).map { pts[it] to pts[it + 1] }
    }

    /** Kenarın öteki şekle göre konumu: 0 dışarıda, 1 içeride, 2 ortak aynı yön, 3 ortak ters yön. */
    private fun classify(a: P, b: P, other: List<Seg>, otherRings: List<List<P>>): Int {
        val m = P((a.x + b.x) / 2, (a.y + b.y) / 2)
        for (o in other) {
            val dx = o.b.x - o.a.x; val dy = o.b.y - o.a.y
            val l2 = dx * dx + dy * dy
            if (l2 < 1e-18) continue
            val t = ((m.x - o.a.x) * dx + (m.y - o.a.y) * dy) / l2
            if (t < -1e-9 || t > 1 + 1e-9) continue
            val px = o.a.x + dx * t; val py = o.a.y + dy * t
            if (hypot(m.x - px, m.y - py) < 1e-6) {
                val dot = (b.x - a.x) * dx + (b.y - a.y) * dy
                return if (dot > 0) 2 else 3
            }
        }
        return if (inside(m, otherRings)) 1 else 0
    }

    /** İki şekil (halka listeleri, çift-tek dolgu) arasında işlem. Sonuç yönleri düzeltilmiş halkalar. */
    fun op(a0: List<List<P>>, b0: List<List<P>>, op: Op): List<List<P>> {
        val a = normalize(a0); val b = normalize(b0)
        if (a.isEmpty()) return if (op == Op.UNION || op == Op.EXCLUDE) b else emptyList()
        if (b.isEmpty()) return if (op == Op.INTERSECT) emptyList() else a
        val sa = segsOf(a, 0); val sb = segsOf(b, 1)
        for (s in sa) for (t in sb) intersect(s, t)
        val edges = ArrayList<Edge>()
        for (s in sa) for ((p, q) in pieces(s)) {
            val c = classify(p, q, sb, b)
            val keep = when (op) {
                Op.UNION -> c == 0 || c == 2
                Op.INTERSECT -> c == 1 || c == 2
                Op.SUBTRACT -> c == 0 || c == 3
                Op.EXCLUDE -> c == 0 || c == 1
            }
            if (keep) edges += if (op == Op.EXCLUDE && c == 1) Edge(q, p) else Edge(p, q)
        }
        for (s in sb) for ((p, q) in pieces(s)) {
            val c = classify(p, q, sa, a)
            if (c >= 2) continue
            when (op) {
                Op.UNION -> if (c == 0) edges += Edge(p, q)
                Op.INTERSECT -> if (c == 1) edges += Edge(p, q)
                Op.SUBTRACT -> if (c == 1) edges += Edge(q, p)
                Op.EXCLUDE -> edges += if (c == 1) Edge(q, p) else Edge(p, q)
            }
        }
        return chain(edges)
    }

    private fun chain(edges: List<Edge>): List<List<P>> {
        val out = HashMap<P, MutableList<Edge>>()
        edges.forEach { out.getOrPut(it.a) { ArrayList() } += it }
        val rings = ArrayList<List<P>>()
        for (e0 in edges) {
            if (e0.used) continue
            val ring = ArrayList<P>()
            var e = e0
            var ok = false
            var guard = 0
            while (guard++ < edges.size + 2) {
                e.used = true
                ring += e.a
                if (e.b == e0.a) { ok = true; break }
                val cands = out[e.b]?.filter { !it.used } ?: break
                if (cands.isEmpty()) break
                // En sağa dönen kenar (dokunan halkalar ayrı kalır)
                val inAng = atan2(e.b.y - e.a.y, e.b.x - e.a.x)
                e = cands.minBy { c ->
                    var d = atan2(c.b.y - c.a.y, c.b.x - c.a.x) - inAng
                    while (d <= -PI) d += 2 * PI
                    while (d > PI) d -= 2 * PI
                    d
                }
            }
            if (ok && ring.size >= 3) {
                val simple = simplify(ring)
                if (simple.size >= 3 && abs(area(simple)) > 0.01) rings += simple
            }
        }
        return rings
    }

    /** Aynı doğru üzerindeki ara noktaları atar. */
    fun simplify(r: List<P>): List<P> {
        if (r.size < 4) return r
        val out = ArrayList<P>()
        val n = r.size
        for (i in 0 until n) {
            val p = r[(i + n - 1) % n]; val q = r[i]; val s = r[(i + 1) % n]
            val c = cross(q.x - p.x, q.y - p.y, s.x - q.x, s.y - q.y)
            val l = hypot(q.x - p.x, q.y - p.y) * hypot(s.x - q.x, s.y - q.y)
            if (abs(c) > 1e-9 * max(l, 1e-12) || (q.x - p.x) * (s.x - q.x) + (q.y - p.y) * (s.y - q.y) < 0) out += q
        }
        return out
    }
}

object V56PathOps {

    enum class PathOp { UNITE, MINUS_FRONT, INTERSECT, EXCLUDE, DIVIDE, TRIM, MERGE, CROP, OUTLINE }

    // ------------------------------------------------------------------ çizim <-> halka

    fun pt(p: StrokePoint) = P(p.x.toDouble(), p.y.toDouble())
    fun sp(p: P) = StrokePoint(p.x.toFloat(), p.y.toFloat())

    /** Alt yollar: noktalar ve kapalı mı. */
    fun paths(s: Stroke): List<Pair<List<StrokePoint>, Boolean>> {
        val spec = s.shape ?: return listOf(s.points to (s.brushType == BrushType.LASSO_FILL))
        val out = ArrayList<Pair<List<StrokePoint>, Boolean>>()
        var start = 0
        for (sub in spec.subpaths) {
            val end = min(s.points.size, start + sub.count)
            if (end > start && sub.label == null) out += s.points.subList(start, end) to (sub.closed || sub.fillColorHex != null)
            start = end
        }
        return out
    }

    /** Boole işlemlerine girebilen çizim mi (şekil, kement dolgu, düz kalem). */
    fun operable(s: Stroke): Boolean = !s.hidden && when (s.brushType) {
        BrushType.SHAPE -> s.shape != null && s.shape.graph == null && s.points.size >= 2
        BrushType.LASSO_FILL -> s.points.size >= 3
        BrushType.TEXT, BrushType.IMAGE, BrushType.ERASER -> false
        else -> !s.brushType.isRaster && s.points.size >= 2
    }

    /** Çizimin dolu alanı: kapalı alt yollar; açık yollar ve kalem izleri konturuyla. */
    fun rings(s: Stroke): List<List<P>> {
        val out = ArrayList<List<P>>()
        val width = strokeWidth(s)
        for ((pts, closed) in paths(s)) {
            if (closed && pts.size >= 3) out += dedupe(pts.map(::pt))
            else if (pts.size >= 2 && width > 0f) out += outlineRings(pts.map(::pt), false, width / 2.0, s.style2?.cap ?: CapStyle.ROUND, s.style2?.join ?: JoinStyle.ROUND)
        }
        return out
    }

    private fun dedupe(r: List<P>): List<P> {
        val out = ArrayList<P>()
        for (p in r) if (out.isEmpty() || out.last() != p) out += p
        while (out.size > 1 && out.first() == out.last()) out.removeAt(out.size - 1)
        return out
    }

    fun strokeWidth(s: Stroke): Float = if (s.brushType == BrushType.SHAPE) s.shape?.style?.strokeWidth ?: 0f else s.sizePx

    /** Çizimin dolgu rengi (şekilde dolgu, yoksa kontur; kalemde renk). */
    fun fillOf(s: Stroke): String = when {
        s.brushType == BrushType.SHAPE && s.shape?.style?.fillColorHex != null && paths(s).any { it.second } -> s.shape.style.fillColorHex!!
        s.brushType == BrushType.SHAPE -> s.shape?.style?.strokeColorHex ?: s.colorHex
        else -> s.colorHex
    }

    /** Halkalardan dolu şekil (kaynağın katmanı, grubu ve opaklığıyla). */
    fun shapeOf(rings: List<List<P>>, like: Stroke, id: String, fill: String, stroke: ShapeStyle? = null): Stroke? {
        val rs = rings.filter { it.size >= 3 }
        if (rs.isEmpty()) return null
        val pts = rs.flatMap { r -> r.map(::sp) }
        val style = stroke ?: ShapeStyle(strokeColorHex = fill, strokeWidth = 0f, fillColorHex = fill)
        return Stroke(
            id, pts, fill, BrushType.SHAPE, like.sizePx, like.opacity, like.order, groupId = like.groupId, layerId = like.layerId,
            shape = ShapeSpec("path", rs.map { SubPath(it.size, closed = true) }, style), clip = like.clip, mask = like.mask,
        )
    }

    // ------------------------------------------------------------------ yol işlemleri

    /**
     * Seçili elemanlara yol işlemi uygular (sıra: listedeki sıra, sonuncusu en üstte). Dönen: yeni çizim listesi ya da
     * işlem uygulanamazsa null.
     */
    fun apply(strokes: List<Stroke>, ids: Set<String>, op: PathOp, newId: () -> String): List<Stroke>? {
        val sel = strokes.filter { it.id in ids && operable(it) }
        if (sel.isEmpty() || (sel.size < 2 && op != PathOp.OUTLINE && op != PathOp.DIVIDE)) return null
        val bottom = sel.first(); val top = sel.last()
        val produced: List<Stroke> = when (op) {
            PathOp.UNITE -> listOfNotNull(shapeOf(sel.drop(1).fold(rings(bottom)) { acc, s -> V56Bool.op(acc, rings(s), V56Bool.Op.UNION) }, top, newId(), fillOf(top), styleOf(top)))
            PathOp.INTERSECT -> listOfNotNull(shapeOf(sel.drop(1).fold(rings(bottom)) { acc, s -> V56Bool.op(acc, rings(s), V56Bool.Op.INTERSECT) }, top, newId(), fillOf(top), styleOf(top)))
            PathOp.EXCLUDE -> listOfNotNull(shapeOf(sel.drop(1).fold(rings(bottom)) { acc, s -> V56Bool.op(acc, rings(s), V56Bool.Op.EXCLUDE) }, top, newId(), fillOf(top), styleOf(top)))
            PathOp.MINUS_FRONT -> {
                val front = sel.drop(1).fold(emptyList<List<P>>()) { acc, s -> V56Bool.op(acc, rings(s), V56Bool.Op.UNION) }
                listOfNotNull(shapeOf(V56Bool.op(rings(bottom), front, V56Bool.Op.SUBTRACT), bottom, newId(), fillOf(bottom), styleOf(bottom)))
            }
            PathOp.DIVIDE -> divide(sel).mapNotNull { (rs, src) -> shapeOf(rs, src, newId(), fillOf(src), styleOf(src)) }
            PathOp.TRIM -> trim(sel).mapNotNull { (rs, src) -> shapeOf(rs, src, newId(), fillOf(src), noStroke(src)) }
            PathOp.MERGE -> {
                val trimmed = trim(sel)
                val groups = LinkedHashMap<String, Pair<List<List<P>>, Stroke>>()
                trimmed.forEach { (rs, src) ->
                    val k = fillOf(src).uppercase()
                    val prev = groups[k]
                    groups[k] = if (prev == null) rs to src else V56Bool.op(prev.first, rs, V56Bool.Op.UNION) to src
                }
                groups.values.mapNotNull { (rs, src) -> shapeOf(rs, src, newId(), fillOf(src), noStroke(src)) }
            }
            PathOp.CROP -> {
                val clip = rings(top)
                sel.dropLast(1).mapNotNull { s -> shapeOf(V56Bool.op(rings(s), clip, V56Bool.Op.INTERSECT), s, newId(), fillOf(s), noStroke(s)) }
            }
            PathOp.OUTLINE -> divide(sel).flatMap { (rs, src) -> outlinePieces(rs, src, newId) }
        }
        if (produced.isEmpty()) return null
        val firstIndex = strokes.indexOfFirst { it.id in sel.map(Stroke::id).toSet() }
        val removed = sel.map { it.id }.toSet()
        val rest = strokes.filter { it.id !in removed }
        val at = strokes.subList(0, firstIndex).count { it.id !in removed }
        return rest.subList(0, at) + produced + rest.subList(at, rest.size)
    }

    private fun styleOf(s: Stroke): ShapeStyle? = if (s.brushType == BrushType.SHAPE && s.shape?.style?.fillColorHex != null && paths(s).any { it.second }) s.shape.style else null
    private fun noStroke(s: Stroke): ShapeStyle? = styleOf(s)?.copy(strokeWidth = 0f)

    /** Bölgelere böler: her bölge (halkalar) ve rengini veren en üstteki kaynak. */
    fun divide(sel: List<Stroke>): List<Pair<List<List<P>>, Stroke>> {
        var regions = ArrayList<Pair<List<List<P>>, Stroke>>()
        var covered = emptyList<List<P>>()
        for (s in sel) {
            val r = rings(s)
            val next = ArrayList<Pair<List<List<P>>, Stroke>>()
            for ((rs, src) in regions) {
                val inter = V56Bool.op(rs, r, V56Bool.Op.INTERSECT)
                val rest = V56Bool.op(rs, r, V56Bool.Op.SUBTRACT)
                splitRegions(inter).forEach { next += it to s }
                splitRegions(rest).forEach { next += it to src }
            }
            splitRegions(V56Bool.op(r, covered, V56Bool.Op.SUBTRACT)).forEach { next += it to s }
            covered = V56Bool.op(covered, r, V56Bool.Op.UNION)
            regions = next
        }
        return regions
    }

    /** Her nesneden üstündekilerin kapladığı alan çıkarılır. */
    fun trim(sel: List<Stroke>): List<Pair<List<List<P>>, Stroke>> {
        val out = ArrayList<Pair<List<List<P>>, Stroke>>()
        var above = emptyList<List<P>>()
        for (s in sel.reversed()) {
            val r = rings(s)
            val visible = V56Bool.op(r, above, V56Bool.Op.SUBTRACT)
            if (visible.isNotEmpty()) out += visible to s
            above = V56Bool.op(above, r, V56Bool.Op.UNION)
        }
        return out.reversed()
    }

    /** Halka listesini ayrık bölgelere ayırır (her dış halka ve içindeki delikler). */
    fun splitRegions(rings: List<List<P>>): List<List<List<P>>> {
        if (rings.isEmpty()) return emptyList()
        val n = V56Bool.normalize(rings)
        val outers = n.filter { V56Bool.area(it) > 0 }
        val holes = n.filter { V56Bool.area(it) <= 0 }
        if (outers.size <= 1) return listOf(n)
        val groups = outers.map { arrayListOf(it) }
        for (h in holes) {
            val probe = h[0]
            // en küçük kapsayan dış halka
            val owner = outers.indices.filter { V56Bool.inside(P(probe.x, probe.y), listOf(outers[it])) }.minByOrNull { abs(V56Bool.area(outers[it])) }
            (if (owner != null) groups[owner] else groups[0]) += h
        }
        return groups
    }

    /** Anahat: bölgelerin kenarları dolgusuz, kontur rengi bölgenin rengi. */
    private fun outlinePieces(rings: List<List<P>>, src: Stroke, newId: () -> String): List<Stroke> {
        if (rings.isEmpty()) return emptyList()
        val color = fillOf(src)
        val pts = rings.flatMap { r -> (r + r.first()).map(::sp) }
        return listOf(
            Stroke(
                newId(), pts, color, BrushType.SHAPE, 1f, src.opacity, src.order, groupId = src.groupId, layerId = src.layerId,
                shape = ShapeSpec("path", rings.map { SubPath(it.size + 1, closed = false) }, ShapeStyle(strokeColorHex = color, strokeWidth = 1f)),
            ),
        )
    }

    // ------------------------------------------------------------------ şekil oluşturucu

    /** Şekil oluşturucu bölgeleri (bölme sonucu). */
    fun builderRegions(sel: List<Stroke>): List<Pair<List<List<P>>, Stroke>> = divide(sel.filter(::operable))

    /** Noktayı içeren bölge. */
    fun regionAt(regions: List<Pair<List<List<P>>, Stroke>>, x: Float, y: Float): Int =
        regions.indexOfLast { V56Bool.inside(P(x.toDouble(), y.toDouble()), it.first) }

    /**
     * Şekil oluşturucu sonucu: [merge] bölgeleri birleşip tek şekil olur (rengi ilk dokunulan bölgenin), [remove] bölgeleri
     * atılır, öteki bölgeler ayrı şekiller olarak kalır.
     */
    fun build(strokes: List<Stroke>, ids: Set<String>, merge: List<Int>, remove: Set<Int>, newId: () -> String): List<Stroke>? {
        val sel = strokes.filter { it.id in ids && operable(it) }
        if (sel.isEmpty()) return null
        val regions = builderRegions(sel)
        val produced = ArrayList<Stroke>()
        val mergeSet = merge.filter { it in regions.indices && it !in remove }
        regions.forEachIndexed { i, (rs, src) ->
            if (i in remove || i in mergeSet) return@forEachIndexed
            shapeOf(rs, src, newId(), fillOf(src), noStroke(src))?.let(produced::add)
        }
        if (mergeSet.isNotEmpty()) {
            val first = regions[mergeSet.first()].second
            val union = mergeSet.drop(1).fold(regions[mergeSet.first()].first) { acc, i -> V56Bool.op(acc, regions[i].first, V56Bool.Op.UNION) }
            shapeOf(union, first, newId(), fillOf(first), noStroke(first))?.let(produced::add)
        }
        val removed = sel.map { it.id }.toSet()
        val firstIndex = strokes.indexOfFirst { it.id in removed }
        val rest = strokes.filter { it.id !in removed }
        val at = strokes.subList(0, firstIndex).count { it.id !in removed }
        return rest.subList(0, at) + produced + rest.subList(at, rest.size)
    }

    // ------------------------------------------------------------------ konturu yola çevir

    private fun norm(dx: Double, dy: Double): Pair<Double, Double> { val l = hypot(dx, dy); return if (l < 1e-12) 0.0 to 0.0 else dx / l to dy / l }

    /** Bir yanın ötelenmiş çizgisi ([h] > 0 sol). Köşeler: sivri (sınırla), yuvarlak ya da kesik. */
    fun offsetLine(pts: List<P>, closed: Boolean, h: Double, join: JoinStyle, miterLimit: Double = 4.0): List<P> {
        val n = pts.size
        if (n < 2) return pts
        val out = ArrayList<P>()
        fun normalAt(i: Int): Pair<Double, Double> { val a = pts[i]; val b = pts[(i + 1) % n]; val (ux, uy) = norm(b.x - a.x, b.y - a.y); return -uy to ux }
        val segs = if (closed) n else n - 1
        for (i in 0 until n) {
            val p = pts[i]
            val hasPrev = closed || i > 0
            val hasNext = closed || i < n - 1
            if (!hasPrev) { val (nx, ny) = normalAt(0); out += P(p.x + nx * h, p.y + ny * h); continue }
            if (!hasNext) { val (nx, ny) = normalAt(segs - 1); out += P(p.x + nx * h, p.y + ny * h); continue }
            val (n1x, n1y) = normalAt((i - 1 + n) % n)
            val (n2x, n2y) = normalAt(i % n)
            val turn = n1x * n2y - n1y * n2x
            val dot = n1x * n2x + n1y * n2y
            if (dot > 0.9999) { out += P(p.x + n2x * h, p.y + n2y * h); continue }
            val outer = turn * h < 0
            if (!outer || join == JoinStyle.MITER) {
                val (mx, my) = norm(n1x + n2x, n1y + n2y)
                val cosHalf = (mx * n2x + my * n2y).coerceAtLeast(0.05)
                val len = h / cosHalf
                if (!outer || abs(len / h) <= miterLimit) { out += P(p.x + mx * len, p.y + my * len); continue }
            }
            if (join == JoinStyle.ROUND) {
                val a1 = atan2(n1y, n1x); var a2 = atan2(n2y, n2x)
                var d = a2 - a1
                while (d > PI) d -= 2 * PI
                while (d < -PI) d += 2 * PI
                val steps = max(2, ceil(abs(d) / (PI / 8)).toInt())
                for (k in 0..steps) { val a = a1 + d * k / steps; out += P(p.x + cos(a) * h, p.y + sin(a) * h) }
                a2 += 0.0
            } else {
                out += P(p.x + n1x * h, p.y + n1y * h); out += P(p.x + n2x * h, p.y + n2y * h)
            }
        }
        return out
    }

    /** Yolun konturunun halkaları ([h] yarı kalınlık). */
    fun outlineRings(pts0: List<P>, closed: Boolean, h: Double, cap: CapStyle, join: JoinStyle): List<List<P>> {
        val pts = dedupe(pts0).let { if (it.size == 1) listOf(it[0], P(it[0].x + 0.01, it[0].y)) else it }
        if (pts.size < 2) return emptyList()
        if (closed && pts.size >= 3) {
            val left = offsetLine(pts, true, h, join); val right = offsetLine(pts, true, -h, join)
            return listOf(left, right.reversed())
        }
        val left = offsetLine(pts, false, h, join)
        val right = offsetLine(pts, false, -h, join)
        val ring = ArrayList<P>()
        ring += left
        capPoints(pts[pts.size - 2], pts.last(), h, cap, ring)
        ring += right.reversed()
        capPoints(pts[1], pts[0], h, cap, ring)
        return listOf(ring)
    }

    private fun capPoints(from: P, end: P, h: Double, cap: CapStyle, out: MutableList<P>) {
        val (ux, uy) = norm(end.x - from.x, end.y - from.y)
        val nx = -uy; val ny = ux
        when (cap) {
            CapStyle.BUTT -> Unit
            CapStyle.SQUARE -> { out += P(end.x + nx * h + ux * h, end.y + ny * h + uy * h); out += P(end.x - nx * h + ux * h, end.y - ny * h + uy * h) }
            CapStyle.ROUND -> {
                val a0 = atan2(ny, nx)
                for (k in 1 until 8) { val a = a0 - PI * k / 8; out += P(end.x + cos(a) * h, end.y + sin(a) * h) }
            }
        }
    }

    /** Konturu yola çevirir: kontur dolu şekil olur; dolgu varsa ayrı şekil olarak altında kalır. */
    fun outlineStroke(strokes: List<Stroke>, ids: Set<String>, newId: () -> String): List<Stroke>? {
        var changed = false
        val out = strokes.flatMap { s ->
            if (s.id !in ids || !operable(s) || s.brushType == BrushType.LASSO_FILL) return@flatMap listOf(s)
            val w = strokeWidth(s)
            if (w <= 0f) return@flatMap listOf(s)
            val color = if (s.brushType == BrushType.SHAPE) s.shape!!.style.strokeColorHex else s.colorHex
            val rings = paths(s).flatMap { (pts, closed) -> outlineRings(pts.map(::pt), closed, w / 2.0, s.style2?.cap ?: CapStyle.ROUND, s.style2?.join ?: JoinStyle.ROUND) }
            val outline = shapeOf(rings, s, newId(), color) ?: return@flatMap listOf(s)
            changed = true
            val fill = if (s.brushType == BrushType.SHAPE && s.shape!!.style.fillColorHex != null)
                s.copy(shape = s.shape.copy(style = s.shape.style.copy(strokeWidth = 0f))) else null
            listOfNotNull(fill, outline)
        }
        return if (changed) out else null
    }

    // ------------------------------------------------------------------ bıçak, ayır, kapat, ters çevir

    /**
     * Bıçak: [cut] çizgisinin geçtiği dolu şekiller iki (ya da daha çok) parçaya, açık yollar kesişim noktalarından
     * ayrılır. [ids] boşsa kesilen her vektör eleman, doluysa yalnız onlar.
     */
    fun knife(strokes: List<Stroke>, ids: Set<String>, cut: List<StrokePoint>, newId: () -> String, locked: Set<String> = emptySet()): List<Stroke>? {
        if (cut.size < 2) return null
        val blade = outlineRings(cut.map(::pt), false, 0.02, CapStyle.BUTT, JoinStyle.MITER)
        var changed = false
        val out = strokes.flatMap { s ->
            if ((ids.isNotEmpty() && s.id !in ids) || !operable(s) || s.layerId in locked || s.brushType != BrushType.SHAPE && s.brushType != BrushType.LASSO_FILL) return@flatMap listOf(s)
            val ps = paths(s)
            val closedAll = ps.isNotEmpty() && ps.all { it.second }
            if (closedAll) {
                val parts = splitRegions(V56Bool.op(ps.map { dedupe(it.first.map(::pt)) }, blade, V56Bool.Op.SUBTRACT))
                if (parts.size < 2) return@flatMap listOf(s)
                changed = true
                parts.mapNotNull { r -> shapeOf(r, s, newId(), fillOf(s), styleOf(s) ?: s.shape?.style) }
            } else {
                val pieces = ps.flatMap { (pts, closed) -> cutOpen(pts, closed, cut) }
                if (pieces.size <= ps.size) return@flatMap listOf(s)
                changed = true
                val style = s.shape?.style ?: ShapeStyle(strokeColorHex = s.colorHex, strokeWidth = s.sizePx)
                pieces.map { pts ->
                    Stroke(newId(), pts, s.colorHex, BrushType.SHAPE, s.sizePx, s.opacity, s.order, groupId = s.groupId, layerId = s.layerId,
                        shape = ShapeSpec("path", listOf(SubPath(pts.size)), style.copy(fillColorHex = null)), style2 = s.style2?.copy(effects = emptyList(), effectBase = null))
                }
            }
        }
        return if (changed) out else null
    }

    /** Açık (ya da kapalı) yolu bıçak çizgisiyle kesişim noktalarından böler. */
    private fun cutOpen(pts0: List<StrokePoint>, closed: Boolean, cut: List<StrokePoint>): List<List<StrokePoint>> {
        val pts = if (closed && pts0.size >= 3) pts0 + pts0.first() else pts0
        val pieces = ArrayList<List<StrokePoint>>()
        var cur = arrayListOf(pts[0])
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            val hits = ArrayList<Pair<Float, StrokePoint>>()
            for (j in 0 until cut.size - 1) {
                segHit(a, b, cut[j], cut[j + 1])?.let { hits += it }
            }
            hits.sortedBy { it.first }.forEach { (_, p) -> cur += p; pieces += cur; cur = arrayListOf(p) }
            cur += b
        }
        pieces += cur
        val valid = pieces.filter { it.size >= 2 }
        // Kapalı yolda ilk ve son parça birleşir
        if (closed && valid.size >= 3) return listOf(valid.last() + valid.first().drop(1)) + valid.subList(1, valid.size - 1)
        return valid
    }

    private fun segHit(a: StrokePoint, b: StrokePoint, c: StrokePoint, d: StrokePoint): Pair<Float, StrokePoint>? {
        val rx = b.x - a.x; val ry = b.y - a.y; val qx = d.x - c.x; val qy = d.y - c.y
        val den = rx * qy - ry * qx
        if (abs(den) < 1e-9f) return null
        val wx = c.x - a.x; val wy = c.y - a.y
        val u = (wx * qy - wy * qx) / den; val v = (wx * ry - wy * rx) / den
        if (u <= 1e-4f || u >= 1f - 1e-4f || v < 0f || v > 1f) return null
        return u to a.at(a.x + rx * u, a.y + ry * u)
    }

    /** Yolu ayır: alt yollar ayrı şekiller olur. */
    fun separate(strokes: List<Stroke>, ids: Set<String>, newId: () -> String): List<Stroke>? {
        var changed = false
        val out = strokes.flatMap { s ->
            val spec = s.shape
            if (s.id !in ids || spec == null || spec.subpaths.size < 2) return@flatMap listOf(s)
            changed = true
            var start = 0
            spec.subpaths.mapNotNull { sub ->
                val end = min(s.points.size, start + sub.count)
                val pts = s.points.subList(start, end)
                start = end
                if (pts.isEmpty()) null else s.copy(id = newId(), points = pts, shape = spec.copy(subpaths = listOf(sub)), cloneOf = null, repeat = null,
                    style2 = s.style2?.copy(effects = emptyList(), effectBase = null))
            }
        }
        return if (changed) out else null
    }

    /** Açık alt yolları kapatır. */
    fun closePaths(strokes: List<Stroke>, ids: Set<String>): List<Stroke>? {
        var changed = false
        val out = strokes.map { s ->
            val spec = s.shape
            if (s.id !in ids || spec == null || spec.subpaths.none { !it.closed && it.count >= 3 }) return@map s
            changed = true
            s.copy(shape = spec.copy(subpaths = spec.subpaths.map { if (it.count >= 3) it.copy(closed = true) else it }))
        }
        return if (changed) out else null
    }

    /** Yolun yönünü ters çevirir (ok uçları yer değiştirir). */
    fun reversePaths(strokes: List<Stroke>, ids: Set<String>): List<Stroke>? {
        var changed = false
        val out = strokes.map { s ->
            if (s.id !in ids || s.points.size < 2 || !operable(s)) return@map s
            changed = true
            val spec = s.shape
            if (spec == null) s.copy(points = s.points.reversed())
            else {
                val pts = ArrayList<StrokePoint>()
                var start = 0
                spec.subpaths.forEach { sub -> val end = min(s.points.size, start + sub.count); pts += s.points.subList(start, end).reversed(); start = end }
                if (start < s.points.size) pts += s.points.subList(start, s.points.size)
                s.copy(points = pts)
            }
        }
        return if (changed) out else null
    }

    // ------------------------------------------------------------------ efektler

    /** Yolu [spacing] aralıkla yeniden örnekler (köşeler korunur). */
    fun resample(pts: List<P>, closed: Boolean, spacing: Double): List<P> {
        if (pts.size < 2 || spacing <= 0) return pts
        val src = if (closed) pts + pts.first() else pts
        val out = ArrayList<P>()
        out += src[0]
        for (i in 0 until src.size - 1) {
            val a = src[i]; val b = src[i + 1]
            val len = hypot(b.x - a.x, b.y - a.y)
            val k = max(1, ceil(len / spacing).toInt())
            for (j in 1..k) { val t = j.toDouble() / k; out += P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) }
        }
        if (closed) out.removeAt(out.size - 1)
        return out
    }

    private fun normals(pts: List<P>, closed: Boolean): List<Pair<Double, Double>> = pts.indices.map { i ->
        val prev = if (i > 0) pts[i - 1] else if (closed) pts.last() else pts[i]
        val next = if (i < pts.size - 1) pts[i + 1] else if (closed) pts.first() else pts[i]
        val (ux, uy) = norm(next.x - prev.x, next.y - prev.y)
        -uy to ux
    }

    /** Deterministik sözde rastgele (-1..1); iOS ile aynı. */
    fun noise(seed: Int, i: Int): Double {
        var x = (seed.toLong() * 374761393L + i.toLong() * 668265263L) and 0xFFFFFFFFL
        x = ((x xor (x ushr 13)) * 1274126177L) and 0xFFFFFFFFL
        x = x xor (x ushr 16)
        return (x % 20001L).toDouble() / 10000.0 - 1.0
    }

    /** Tek efekti yollara uygular (kutu: tüm yolların sınırları). */
    fun effect(paths: List<Pair<List<P>, Boolean>>, e: VectorEffect): List<Pair<List<P>, Boolean>> {
        if (paths.isEmpty()) return paths
        val all = paths.flatMap { it.first }
        val l = all.minOf { it.x }; val r = all.maxOf { it.x }; val t = all.minOf { it.y }; val b = all.maxOf { it.y }
        return when (e.kind) {
            EffectKind.OFFSET -> paths.map { (pts, closed) ->
                if (closed && pts.size >= 3) {
                    val outward = if (V56Bool.area(pts) > 0) -1.0 else 1.0
                    offsetLine(pts, true, e.amount.toDouble() * outward, JoinStyle.MITER) to true
                } else offsetLine(pts, false, e.amount.toDouble(), JoinStyle.MITER) to false
            }
            EffectKind.BEND -> {
                val w = (r - l).coerceAtLeast(1e-6); val cx = (l + r) / 2
                val k = e.amount.toDouble().coerceIn(-1.0, 1.0)
                if (abs(k) < 1e-4) paths else {
                    val theta = k * PI // toplam yay açısı
                    val radius = w / theta
                    paths.map { (pts, closed) ->
                        resample(pts, closed, max(w, b - t) / 80.0).map { p ->
                            val a = (p.x - cx) / radius
                            val rr = radius - (p.y - t)
                            P(cx + rr * sin(a), t + radius - rr * cos(a))
                        } to closed
                    }
                }
            }
            EffectKind.ROUGHEN -> {
                val spacing = 100.0 / e.detail.coerceIn(1f, 100f)
                var idx = 0
                paths.map { (pts, closed) ->
                    resample(pts, closed, spacing).map { p ->
                        val dx = noise(e.seed, idx * 2) * e.size; val dy = noise(e.seed, idx * 2 + 1) * e.size
                        idx++
                        P(p.x + dx, p.y + dy)
                    } to closed
                }
            }
            EffectKind.ZIGZAG, EffectKind.WAVE -> {
                val spacing = 100.0 / e.detail.coerceIn(1f, 100f) / 2.0
                paths.map { (pts, closed) ->
                    val rs = resample(pts, closed, spacing / (if (e.kind == EffectKind.WAVE) 4 else 1))
                    val ns = normals(rs, closed)
                    val step = if (e.kind == EffectKind.WAVE) 4 else 1
                    rs.mapIndexed { i, p ->
                        val (nx, ny) = ns[i]
                        val amp = if (e.kind == EffectKind.ZIGZAG) (if (i % 2 == 0) 1.0 else -1.0) * e.size
                        else sin(i * PI / (2.0 * step) * 2.0) * e.size
                        if (!closed && (i == 0 || i == rs.size - 1)) p else P(p.x + nx * amp, p.y + ny * amp)
                    } to closed
                }
            }
        }
    }

    /** Efektli elemanın geometrisini tabandan yeniden üretir. */
    fun withEffects(s: Stroke, effects: List<VectorEffect>): Stroke {
        val style = s.style2 ?: VectorStyle()
        val base = style.effectBase ?: EffectBase(s.points, s.shape?.subpaths?.map { it.count })
        if (effects.isEmpty()) {
            val restored = restore(s, base)
            return restored.copy(style2 = style.copy(effects = emptyList(), effectBase = null).takeUnless { it.isDefault })
        }
        val baseStroke = restore(s, base)
        var ps = paths(baseStroke).map { (pts, closed) -> pts.map(::pt) to closed }
        effects.forEach { ps = effect(ps, it) }
        val pts = ps.flatMap { (pp, _) -> pp.map(::sp) }
        val spec = baseStroke.shape
        val shape = spec?.let { sp0 ->
            val visible = sp0.subpaths.filter { it.label == null }
            sp0.copy(subpaths = ps.mapIndexed { i, (pp, closed) -> (visible.getOrNull(i) ?: SubPath(pp.size)).copy(count = pp.size, closed = closed) })
        }
        return baseStroke.copy(points = pts, shape = shape, style2 = style.copy(effects = effects, effectBase = base))
    }

    private fun restore(s: Stroke, base: EffectBase): Stroke {
        val spec = s.shape
        val counts = base.counts
        val shape = if (spec != null && counts != null && counts.size == spec.subpaths.size) spec.copy(subpaths = spec.subpaths.mapIndexed { i, sub -> sub.copy(count = counts[i]) })
        else if (spec != null && counts != null) spec.copy(subpaths = counts.map { SubPath(it) }) else spec
        return s.copy(points = base.points, shape = shape)
    }

    /** Efekt uygulanabilir mi (vektör şekil ya da düz kalem). */
    fun effectable(s: Stroke): Boolean = operable(s) && s.brushType != BrushType.LASSO_FILL

    // ------------------------------------------------------------------ zarf bozma

    /**
     * Üstteki nesneyle zarf: seçimin en üstündeki kapalı şekil zarf olur, öteki nesneler kutularından zarfın içine
     * eşlenir (Coons yaması: zarfın kutu köşelerine en yakın dört noktası köşe kabul edilir). Zarf nesnesi silinir.
     */
    fun envelope(strokes: List<Stroke>, ids: Set<String>): List<Stroke>? {
        val sel = strokes.filter { it.id in ids && operable(it) }
        if (sel.size < 2) return null
        val env = sel.last()
        val ring = paths(env).firstOrNull { it.second }?.first?.map(::pt)?.let(::dedupe) ?: return null
        if (ring.size < 4) return null
        val targets = sel.dropLast(1)
        val box = V56Vector.pointBounds(targets) ?: return null
        val bw = (box[2] - box[0]).coerceAtLeast(1e-3f); val bh = (box[3] - box[1]).coerceAtLeast(1e-3f)
        val (top, right, bottom, left) = sides(ring)
        fun at(side: List<P>, u: Double): P {
            val lens = DoubleArray(side.size)
            for (i in 1 until side.size) lens[i] = lens[i - 1] + hypot(side[i].x - side[i - 1].x, side[i].y - side[i - 1].y)
            val total = lens.last().coerceAtLeast(1e-9)
            val target = u.coerceIn(0.0, 1.0) * total
            var i = 1
            while (i < side.size - 1 && lens[i] < target) i++
            val seg = (lens[i] - lens[i - 1]).coerceAtLeast(1e-9)
            val t = ((target - lens[i - 1]) / seg).coerceIn(0.0, 1.0)
            return P(side[i - 1].x + (side[i].x - side[i - 1].x) * t, side[i - 1].y + (side[i].y - side[i - 1].y) * t)
        }
        val c00 = top.first(); val c10 = top.last(); val c11 = bottom.last(); val c01 = bottom.first()
        fun map(p: StrokePoint): StrokePoint {
            val u = ((p.x - box[0]) / bw).toDouble(); val v = ((p.y - box[1]) / bh).toDouble()
            val tp = at(top, u); val bp = at(bottom, u); val lp = at(left, v); val rp = at(right, v)
            val x = (1 - v) * tp.x + v * bp.x + (1 - u) * lp.x + u * rp.x - ((1 - u) * (1 - v) * c00.x + u * (1 - v) * c10.x + u * v * c11.x + (1 - u) * v * c01.x)
            val y = (1 - v) * tp.y + v * bp.y + (1 - u) * lp.y + u * rp.y - ((1 - u) * (1 - v) * c00.y + u * (1 - v) * c10.y + u * v * c11.y + (1 - u) * v * c01.y)
            return p.at(x.toFloat(), y.toFloat())
        }
        val ids2 = targets.map { it.id }.toSet()
        return strokes.filter { it.id != env.id }.map { s ->
            if (s.id !in ids2) return@map s
            // Kenarlar yumuşak eğilsin diye seyrek yollar sıklaştırılır
            val dense = densify(s, max(bw, bh) / 60f)
            dense.copy(points = dense.points.map(::map), style2 = V56Vector.mapStyle(dense.style2, ::map))
        }
    }

    /** Zarfın dört kenarı (üst soldan sağa, sağ yukarıdan aşağı, alt soldan sağa, sol yukarıdan aşağı). */
    private fun sides(ring0: List<P>): List<List<P>> {
        val ring = if (V56Bool.area(ring0) < 0) ring0.reversed() else ring0 // ekranda (y aşağı) saat yönü
        val l = ring.minOf { it.x }; val r = ring.maxOf { it.x }; val t = ring.minOf { it.y }; val b = ring.maxOf { it.y }
        fun nearest(x: Double, y: Double) = ring.indices.minBy { hypot(ring[it].x - x, ring[it].y - y) }
        val iTL = nearest(l, t); val iTR = nearest(r, t); val iBR = nearest(r, b); val iBL = nearest(l, b)
        fun walk(from: Int, to: Int): List<P> {
            val out = ArrayList<P>(); var i = from
            out += ring[i]
            var guard = 0
            while (i != to && guard++ < ring.size) { i = (i + 1) % ring.size; out += ring[i] }
            return out
        }
        val top = walk(iTL, iTR); val right = walk(iTR, iBR); val bottom = walk(iBR, iBL).reversed(); val left = walk(iBL, iTL).reversed()
        return listOf(top, right, bottom, left)
    }

    /** Uzun kenarları [step] aralıkla böler (zarf ve kıvırma için). */
    fun densify(s: Stroke, step: Float): Stroke {
        if (step <= 0f) return s
        val spec = s.shape
        val ps = paths(s)
        if (spec == null) return s.copy(points = resample(s.points.map(::pt), false, step.toDouble()).map(::sp))
        val visible = spec.subpaths.filter { it.label == null }
        if (visible.size != spec.subpaths.size) return s
        val newPaths = ps.map { (pts, closed) -> resample(pts.map(::pt), closed, step.toDouble()).map(::sp) }
        return s.copy(points = newPaths.flatten(), shape = spec.copy(subpaths = newPaths.mapIndexed { i, pp -> spec.subpaths[i].copy(count = pp.size) }))
    }

    /** Halka çevresi (testler). */
    fun ringArea(rings: List<List<P>>): Double = rings.sumOf { V56Bool.area(it) }

    internal fun dist(a: P, b: P) = hypot(a.x - b.x, a.y - b.y)
    @Suppress("unused") private fun sq(v: Double) = sqrt(v)
}
