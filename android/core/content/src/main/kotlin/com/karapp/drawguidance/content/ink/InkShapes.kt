package com.karapp.drawguidance.content.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Elle çizilen tek çizgiden tanınan şeklin türü. */
enum class InkShapeKind { LINE, POLYLINE, CIRCLE, ELLIPSE, TRIANGLE, RECTANGLE, SQUARE, POLYGON }

/**
 * Tanınan şekil: [points] düzeltilmiş (ideal) geometri — kapalı şekillerde son nokta ilk noktanın tekrarı, eğriler
 * sık örneklenmiş; [score] 0..1 ne kadar emin (yüksek = çizim bu şekle çok yakın).
 */
data class InkShape(val kind: InkShapeKind, val points: List<InkPoint>, val closed: Boolean, val score: Float)

/**
 * v17 çizim düzeltme: elle çizilen tek çizgi doğru, kırık çizgi, çember/elips, üçgen, dikdörtgen/kare ya da çokgen mi —
 * tanınırsa ideal geometrisi verilir (açılar 15°'ye, neredeyse yatay/dikey kenarlar tam yatay/dikeye yapışır).
 * Tanınmayan (serbest) çizimde null. Tamamen Kotlin, durumsuz.
 */
object InkShapes {

    private const val N = 96

    fun recognize(raw: List<InkPoint>): InkShape? {
        val pts = dedupe(raw)
        if (pts.size < 2) return null
        val length = pathLength(pts)
        val (minX, minY, maxX, maxY) = bounds(pts)
        val diag = hypot(maxX - minX, maxY - minY)
        if (length < 12f || diag < 10f) return null
        val r = resample(pts, N)
        val gap = dist(r.first(), r.last())
        val closed = gap < max(0.16f * diag, 0.08f * length) && length > diag * 1.6f
        return if (closed) closedShape(r, diag) else openShape(r, length, diag)
    }

    // --- Açık çizgiler: doğru, kırık çizgi ---

    private fun openShape(r: List<InkPoint>, length: Float, diag: Float): InkShape? {
        val a = r.first(); val b = r.last()
        val chord = dist(a, b)
        val dev = r.maxOf { segDist(it, a, b) }
        if (chord > 0.8f * length && dev < max(0.07f * chord, 2.5f)) {
            val (p, q) = snapLine(a, b)
            return InkShape(InkShapeKind.LINE, densify(listOf(p, q), false), false, (1f - dev / (0.07f * chord + 1e-3f)).coerceIn(0.3f, 1f))
        }
        // Kırık çizgi: az köşeli ve kenarları düz
        val corners = rdp(r, 0.06f * diag)
        if (corners.size in 3..5) {
            val poly = corners.map { r[it] }
            val err = r.map { p -> polyDist(p, poly, false) }.average().toFloat()
            if (err < 0.022f * diag && poly.zipWithNext().all { (p, q) -> dist(p, q) > 0.12f * diag }) {
                val snapped = snapPolyline(poly)
                return InkShape(InkShapeKind.POLYLINE, densify(snapped, false), false, (1f - err / (0.022f * diag)).coerceIn(0.3f, 1f))
            }
        }
        return null
    }

    // --- Kapalı çizgiler: çember/elips ve çokgenler ---

    private fun closedShape(r: List<InkPoint>, diag: Float): InkShape? {
        val ring = r.dropLast(1)
        val ellipse = fitEllipse(ring)
        val corners = rdpClosed(ring, 0.07f * diag)
        var polygon: InkShape? = null
        var polyErr = Float.MAX_VALUE
        if (corners.size in 3..8) {
            val poly = corners.map { ring[it] }
            polyErr = ring.map { p -> polyDist(p, poly, true) }.average().toFloat() / diag
            if (polyErr < 0.03f) polygon = makePolygon(poly, polyErr, diag)
        }
        val ellipseErr = ellipse?.second ?: Float.MAX_VALUE
        // Kenarları düz (köşeli) çizim çokgen; yuvarlak çizim çember/elips — hangisine daha iyi uyuyorsa
        return when {
            polygon != null && polyErr < 0.018f && polyErr * 2.2f < ellipseErr -> polygon
            ellipse != null && ellipseErr < 0.07f -> ellipse.first
            else -> polygon
        }
    }

    /** Çember/elips uydurma: ağırlık merkezi + ana eksenler (kovaryans). Hata: normalleştirilmiş yarıçapın 1'den sapması. */
    private fun fitEllipse(ring: List<InkPoint>): Pair<InkShape, Float>? {
        val n = ring.size
        val cx = ring.sumOf { it.x.toDouble() } / n
        val cy = ring.sumOf { it.y.toDouble() } / n
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        ring.forEach { val dx = it.x - cx; val dy = it.y - cy; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        sxx /= n; syy /= n; sxy /= n
        val tr = sxx + syy
        val det = sxx * syy - sxy * sxy
        val disc = sqrt(max(0.0, tr * tr / 4 - det))
        val l1 = tr / 2 + disc
        val l2 = tr / 2 - disc
        if (l2 <= 1e-6) return null
        var angle = 0.5 * atan2(2 * sxy, sxx - syy)
        // Yarı eksenler ana eksenler boyunca uzanımdan (örnekleme yoğunluğundan etkilenmez); merkez uzanımın ortası
        val c0 = cos(angle); val s0 = sin(angle)
        var minU = Double.MAX_VALUE; var maxU = -Double.MAX_VALUE; var minV = Double.MAX_VALUE; var maxV = -Double.MAX_VALUE
        ring.forEach { p ->
            val dx = p.x - cx; val dy = p.y - cy
            val u = dx * c0 + dy * s0; val v = -dx * s0 + dy * c0
            minU = min(minU, u); maxU = max(maxU, u); minV = min(minV, v); maxV = max(maxV, v)
        }
        val du = (minU + maxU) / 2; val dv = (minV + maxV) / 2
        val ecx = cx + du * c0 - dv * s0
        val ecy = cy + du * s0 + dv * c0
        var a = (maxU - minU) / 2
        var b = (maxV - minV) / 2
        if (a < 1e-3 || b < 1e-3) return null
        fun err(a0: Double, b0: Double, ang: Double): Double {
            val c = cos(ang); val s = sin(ang)
            return ring.sumOf { p ->
                val dx = p.x - ecx; val dy = p.y - ecy
                val u = dx * c + dy * s; val v = -dx * s + dy * c
                abs(sqrt((u / a0) * (u / a0) + (v / b0) * (v / b0)) - 1.0)
            } / n
        }
        val e = err(a, b, angle).toFloat()
        val ratio = (b / a).toFloat()
        val circle = ratio > 0.86f
        if (circle) { val m = (a + b) / 2; a = m; b = m; angle = 0.0 }
        else {
            // Neredeyse yatay/dikey eksen tam yatay/dikey olur
            val deg = Math.toDegrees(angle)
            val snapped = (deg / 90.0).roundToInt() * 90.0
            if (abs(deg - snapped) < 8.0) angle = Math.toRadians(snapped)
        }
        val pts = (0..72).map { i ->
            val t = i / 72.0 * 2 * PI
            val u = a * cos(t); val v = b * sin(t)
            InkPoint((ecx + u * cos(angle) - v * sin(angle)).toFloat(), (ecy + u * sin(angle) + v * cos(angle)).toFloat())
        }
        val score = (1f - e / 0.085f).coerceIn(0f, 1f)
        return InkShape(if (circle) InkShapeKind.CIRCLE else InkShapeKind.ELLIPSE, pts, true, score) to e
    }

    private fun makePolygon(poly: List<InkPoint>, err: Float, diag: Float): InkShape {
        val score = (1f - err / 0.03f).coerceIn(0f, 1f)
        if (poly.size == 4 && poly.indices.all { i -> abs(cornerAngle(poly, i) - 90f) < 22f }) {
            // Dikdörtgen: en uzun kenarın yönünde, köşelerin kapsadığı kutu
            val longest = poly.indices.maxBy { dist(poly[it], poly[(it + 1) % 4]) }
            var ang = atan2((poly[(longest + 1) % 4].y - poly[longest].y).toDouble(), (poly[(longest + 1) % 4].x - poly[longest].x).toDouble())
            val deg = Math.toDegrees(ang)
            val snapped = (deg / 90.0).roundToInt() * 90.0
            if (abs(deg - snapped) < 10.0) ang = Math.toRadians(snapped)
            val c = cos(ang); val s = sin(ang)
            val cx = poly.sumOf { it.x.toDouble() } / 4; val cy = poly.sumOf { it.y.toDouble() } / 4
            var hu = 0.0; var hv = 0.0
            poly.forEach { p -> val dx = p.x - cx; val dy = p.y - cy; hu += abs(dx * c + dy * s); hv += abs(-dx * s + dy * c) }
            hu /= 4; hv /= 4
            val square = min(hu, hv) / max(hu, hv) > 0.88
            if (square) { val m = (hu + hv) / 2; hu = m; hv = m }
            fun at(u: Double, v: Double) = InkPoint((cx + u * c - v * s).toFloat(), (cy + u * s + v * c).toFloat())
            val rect = listOf(at(-hu, -hv), at(hu, -hv), at(hu, hv), at(-hu, hv))
            return InkShape(if (square) InkShapeKind.SQUARE else InkShapeKind.RECTANGLE, densify(rect + rect.first(), true), true, score)
        }
        val snapped = snapPolyline(poly + poly.first()).dropLast(1)
        val kind = if (poly.size == 3) InkShapeKind.TRIANGLE else InkShapeKind.POLYGON
        return InkShape(kind, densify(snapped + snapped.first(), true), true, score)
    }

    // --- Yardımcılar ---

    private fun cornerAngle(poly: List<InkPoint>, i: Int): Float {
        val p = poly[i]; val a = poly[(i - 1 + poly.size) % poly.size]; val b = poly[(i + 1) % poly.size]
        val v1x = a.x - p.x; val v1y = a.y - p.y; val v2x = b.x - p.x; val v2y = b.y - p.y
        val cos = (v1x * v2x + v1y * v2y) / (hypot(v1x, v1y) * hypot(v2x, v2y) + 1e-6f)
        return Math.toDegrees(kotlin.math.acos(cos.coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    /** Doğrunun açısı 15°'nin katına yakınsa ona yapışır (orta noktası yerinde). */
    private fun snapLine(a: InkPoint, b: InkPoint): Pair<InkPoint, InkPoint> {
        val len = dist(a, b)
        val ang = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()))
        val snapped = (ang / 15.0).roundToInt() * 15.0
        if (abs(ang - snapped) > 5.0) return a to b
        val mx = (a.x + b.x) / 2; val my = (a.y + b.y) / 2
        val rad = Math.toRadians(snapped)
        val dx = (cos(rad) * len / 2).toFloat(); val dy = (sin(rad) * len / 2).toFloat()
        return InkPoint(mx - dx, my - dy) to InkPoint(mx + dx, my + dy)
    }

    /** Kırık çizginin neredeyse yatay/dikey kenarları tam yatay/dikey olur. */
    private fun snapPolyline(poly: List<InkPoint>): List<InkPoint> {
        val out = poly.toMutableList()
        for (i in 0 until out.size - 1) {
            val p = out[i]; val q = out[i + 1]
            val dx = q.x - p.x; val dy = q.y - p.y
            val len = hypot(dx, dy)
            if (len < 1e-3f) continue
            if (abs(dy) / len < 0.09f) { val y = (p.y + q.y) / 2; out[i] = p.copy(y = y); out[i + 1] = q.copy(y = y) }
            else if (abs(dx) / len < 0.09f) { val x = (p.x + q.x) / 2; out[i] = p.copy(x = x); out[i + 1] = q.copy(x = x) }
        }
        if (poly.size > 2 && poly.first() == poly.last()) out[out.size - 1] = out[0]
        return out
    }

    /** Köşe noktaları arasına sık nokta koyar (her fırça motoru düzgün çizsin). */
    fun densify(corners: List<InkPoint>, closed: Boolean, step: Float = 3f): List<InkPoint> {
        val out = ArrayList<InkPoint>()
        for (i in 0 until corners.size - 1) {
            val a = corners[i]; val b = corners[i + 1]
            val n = max(1, (dist(a, b) / step).toInt())
            for (k in 0 until n) out += InkPoint(a.x + (b.x - a.x) * k / n, a.y + (b.y - a.y) * k / n)
        }
        out += corners.last()
        if (closed && out.first() != out.last()) out += out.first()
        return out
    }

    private fun dedupe(p: List<InkPoint>): List<InkPoint> {
        val out = ArrayList<InkPoint>(p.size)
        p.forEach { if (out.isEmpty() || dist(out.last(), it) > 0.01f) out += it }
        return out
    }

    private fun pathLength(p: List<InkPoint>): Float { var l = 0f; for (i in 1 until p.size) l += dist(p[i - 1], p[i]); return l }

    private data class B(val l: Float, val t: Float, val r: Float, val b: Float)
    private fun bounds(p: List<InkPoint>) = B(p.minOf { it.x }, p.minOf { it.y }, p.maxOf { it.x }, p.maxOf { it.y })

    private fun resample(p: List<InkPoint>, n: Int): List<InkPoint> {
        val total = pathLength(p)
        val step = total / (n - 1)
        val out = ArrayList<InkPoint>(n)
        out += p.first()
        var acc = 0f
        var i = 1
        var prev = p.first()
        while (i < p.size && out.size < n - 1) {
            val cur = p[i]
            val d = dist(prev, cur)
            if (acc + d >= step && d > 0f) {
                val t = (step - acc) / d
                val q = InkPoint(prev.x + (cur.x - prev.x) * t, prev.y + (cur.y - prev.y) * t)
                out += q
                prev = q
                acc = 0f
            } else {
                acc += d
                prev = cur
                i++
            }
        }
        while (out.size < n) out += p.last()
        return out
    }

    private fun dist(a: InkPoint, b: InkPoint) = hypot(a.x - b.x, a.y - b.y)

    private fun segDist(p: InkPoint, a: InkPoint, b: InkPoint): Float {
        val vx = b.x - a.x; val vy = b.y - a.y
        val l2 = vx * vx + vy * vy
        if (l2 < 1e-9f) return dist(p, a)
        val t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / l2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
    }

    private fun polyDist(p: InkPoint, poly: List<InkPoint>, closed: Boolean): Float {
        var best = Float.MAX_VALUE
        val n = if (closed) poly.size else poly.size - 1
        for (i in 0 until n) best = min(best, segDist(p, poly[i], poly[(i + 1) % poly.size]))
        return best
    }

    private fun rdp(p: List<InkPoint>, eps: Float): List<Int> {
        val keep = BooleanArray(p.size)
        keep[0] = true; keep[p.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to p.size - 1)
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            var best = -1; var bd = 0f
            for (i in s + 1 until e) { val d = segDist(p[i], p[s], p[e]); if (d > bd) { bd = d; best = i } }
            if (best >= 0 && bd > eps) { keep[best] = true; stack.addLast(s to best); stack.addLast(best to e) }
        }
        return p.indices.filter { keep[it] }
    }

    /** Kapalı çizginin köşeleri: en uzak iki noktadan bölünüp iki yarıda RDP; birleşen yakın köşeler tek köşe. */
    private fun rdpClosed(ring: List<InkPoint>, eps: Float): List<Int> {
        val n = ring.size
        var i0 = 0; var i1 = 0; var bd = 0f
        for (i in 0 until n) for (j in i + 1 until n) { val d = dist(ring[i], ring[j]); if (d > bd) { bd = d; i0 = i; i1 = j } }
        val rot = ring.drop(i0) + ring.take(i0)
        val k = (i1 - i0 + n) % n
        val first = rdp(rot.subList(0, k + 1), eps)
        val second = rdp(rot.subList(k, n) + rot[0], eps).map { it + k }.filter { it < n }
        val idx = (first + second).distinct().sorted()
        // Neredeyse düz köşeler (170°+) atılır
        val corners = idx.map { rot[it] }
        val kept = corners.indices.filter { c ->
            corners.size <= 3 || cornerAngle(corners, c) < 165f
        }.map { idx[it] }
        return kept.map { (it + i0) % n }.sorted()
    }
}
