package com.karapp.drawguidance.content.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** İç kullanım: tek çizginin koordinatları (y aşağı) ve sınır kutusu. */
internal class Poly(val x: FloatArray, val y: FloatArray) {
    val n: Int get() = x.size
    val minX: Float
    val maxX: Float
    val minY: Float
    val maxY: Float

    init {
        var a = Float.POSITIVE_INFINITY; var b = Float.NEGATIVE_INFINITY
        var c = Float.POSITIVE_INFINITY; var d = Float.NEGATIVE_INFINITY
        for (i in x.indices) {
            a = min(a, x[i]); b = max(b, x[i]); c = min(c, y[i]); d = max(d, y[i])
        }
        minX = a; maxX = b; minY = c; maxY = d
    }

    val w: Float get() = maxX - minX
    val h: Float get() = maxY - minY
    val cx: Float get() = (minX + maxX) * 0.5f
    val cy: Float get() = (minY + maxY) * 0.5f
    val extent: Float get() = max(w, h)
    val length: Float by lazy {
        var s = 0f
        for (i in 1 until n) s += hypot(x[i] - x[i - 1], y[i] - y[i - 1])
        s
    }

    /** x' = a·x + b·y + c, y' = d·x + e·y + f */
    fun affine(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): Poly {
        val nx = FloatArray(n); val ny = FloatArray(n)
        for (i in 0 until n) {
            nx[i] = a * x[i] + b * y[i] + c
            ny[i] = d * x[i] + e * y[i] + f
        }
        return Poly(nx, ny)
    }
}

internal class Box(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val w get() = maxX - minX
    val h get() = maxY - minY
    val cx get() = (minX + maxX) * 0.5f
    val cy get() = (minY + maxY) * 0.5f

    companion object {
        fun of(polys: List<Poly>): Box {
            var a = Float.POSITIVE_INFINITY; var b = Float.POSITIVE_INFINITY
            var c = Float.NEGATIVE_INFINITY; var d = Float.NEGATIVE_INFINITY
            for (p in polys) {
                a = min(a, p.minX); b = min(b, p.minY); c = max(c, p.maxX); d = max(d, p.maxY)
            }
            return Box(a, b, c, d)
        }
    }
}

internal object Geo {

    /** Yol boyunca eşit aralıklı yeniden örnekleme (uçlar korunur). */
    fun resample(p: Poly, step: Float): Poly {
        if (p.n < 2 || step <= 0f) return p
        val len = p.length
        if (len < 1e-6f) return Poly(floatArrayOf(p.x[0]), floatArrayOf(p.y[0]))
        val count = max(2, ceil(len / step).toInt() + 1)
        val real = len / (count - 1)
        val ox = FloatArray(count); val oy = FloatArray(count)
        ox[0] = p.x[0]; oy[0] = p.y[0]
        var k = 1
        var acc = 0f
        var px = p.x[0]; var py = p.y[0]
        var i = 1
        while (i < p.n && k < count - 1) {
            val qx = p.x[i]; val qy = p.y[i]
            val d = hypot(qx - px, qy - py)
            if (acc + d >= real && d > 0f) {
                val t = (real - acc) / d
                val nx = px + t * (qx - px); val ny = py + t * (qy - py)
                ox[k] = nx; oy[k] = ny; k++
                px = nx; py = ny; acc = 0f
            } else {
                acc += d; px = qx; py = qy; i++
            }
        }
        while (k < count) { ox[k] = p.x[p.n - 1]; oy[k] = p.y[p.n - 1]; k++ }
        return Poly(ox, oy)
    }

    /** [1,2,1] yumuşatma (uçlar sabit). */
    fun smooth(p: Poly, passes: Int = 1): Poly {
        if (p.n < 3) return p
        var x = p.x; var y = p.y
        repeat(passes) {
            val nx = x.copyOf(); val ny = y.copyOf()
            for (i in 1 until x.size - 1) {
                nx[i] = (x[i - 1] + 2 * x[i] + x[i + 1]) * 0.25f
                ny[i] = (y[i - 1] + 2 * y[i] + y[i + 1]) * 0.25f
            }
            x = nx; y = ny
        }
        return Poly(x, y)
    }

    /** İki çizgi arasındaki en kısa nokta uzaklığı (yaklaşık; noktalar arası). */
    fun minDistance(a: Poly, b: Poly): Float {
        var best = Float.POSITIVE_INFINITY
        for (i in 0 until a.n) for (j in 0 until b.n) {
            val dx = a.x[i] - b.x[j]; val dy = a.y[i] - b.y[j]
            val d = dx * dx + dy * dy
            if (d < best) best = d
        }
        return sqrt(best)
    }

    /** [absTurns] toplam mutlak dönüş, [netTurns] işaretli toplam (sarmalda büyük, 8 gibi ters döngülerde küçük). */
    class Turning(val absTurns: Float, val sharpTurns: Int, val reversals: Int, val netTurns: Float = 0f)

    /** Yönelim değişimleri: toplam mutlak dönüş (tur), keskin (>110°) köşe ve geri dönüş (>150°) sayısı. */
    fun turning(p: Poly, step: Float): Turning {
        val r = smooth(resample(p, step), 1)
        if (r.n < 3) return Turning(0f, 0, 0)
        var total = 0.0
        var sharp = 0
        var rev = 0
        var prevA = Double.NaN
        // köşeleri birkaç adımlık pencereyle ölç (gürültüye dayanıklı)
        val angles = ArrayList<Double>()
        for (i in 1 until r.n) {
            val dx = (r.x[i] - r.x[i - 1]).toDouble(); val dy = (r.y[i] - r.y[i - 1]).toDouble()
            if (dx * dx + dy * dy < 1e-12) continue
            angles += atan2(dy, dx)
        }
        var net = 0.0
        for (a in angles) {
            if (!prevA.isNaN()) { val d = wrap(a - prevA); total += abs(d); net += d }
            prevA = a
        }
        val win = 2
        var i = win
        while (i < angles.size - win) {
            val d = abs(wrap(angles[i + win - 1] - angles[i - win]))
            if (d > 110.0 * PI / 180.0) {
                sharp++
                if (d > 150.0 * PI / 180.0) rev++
                i += 2 * win
            } else i++
        }
        return Turning((total / (2 * PI)).toFloat(), sharp, rev, (abs(net) / (2 * PI)).toFloat())
    }

    /** Çizginin kutu merkezi etrafında dolanma sayısı (sarmalda büyük; o/0 için ~1, m/8 için ~0). */
    fun winding(p: Poly): Float {
        if (p.n < 3) return 0f
        val cx = p.cx; val cy = p.cy
        val r0 = 0.08f * p.extent
        var total = 0.0
        var prev = Double.NaN
        for (i in 0 until p.n) {
            val dx = (p.x[i] - cx).toDouble(); val dy = (p.y[i] - cy).toDouble()
            if (dx * dx + dy * dy < (r0 * r0).toDouble()) continue
            val a = atan2(dy, dx)
            if (!prev.isNaN()) total += wrap(a - prev)
            prev = a
        }
        return (abs(total) / (2 * PI)).toFloat()
    }

    /** Çizginin kendini kesme sayısı (komşu olmayan parçalar arasında). */
    fun selfIntersections(p: Poly, step: Float): Int {
        val r = resample(p, step)
        var count = 0
        for (i in 0 until r.n - 1) for (j in i + 2 until r.n - 1) {
            if (i == 0 && j == r.n - 2) continue
            if (!segmentsCross(r.x[i], r.y[i], r.x[i + 1], r.y[i + 1], r.x[j], r.y[j], r.x[j + 1], r.y[j + 1])) continue
            // geri dönüşte (m, n gibi) üst üste binen parçaların gürültü kesişmeleri sayılmaz
            val ux = r.x[i + 1] - r.x[i]; val uy = r.y[i + 1] - r.y[i]
            val vx = r.x[j + 1] - r.x[j]; val vy = r.y[j + 1] - r.y[j]
            val sin = abs(ux * vy - uy * vx) / max(hypot(ux, uy) * hypot(vx, vy), 1e-9f)
            if (sin > 0.5f) count++
        }
        return count
    }

    private fun segmentsCross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
        fun cross(ox: Float, oy: Float, px: Float, py: Float, qx: Float, qy: Float) = (px - ox) * (qy - oy) - (py - oy) * (qx - ox)
        val d1 = cross(cx, cy, dx, dy, ax, ay); val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy); val d4 = cross(ax, ay, bx, by, dx, dy)
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
    }

    fun wrap(a: Double): Double {
        var v = a
        while (v > PI) v -= 2 * PI
        while (v < -PI) v += 2 * PI
        return v
    }
}

/** Taslak kontrol noktası: [corner] ise spline burada kırılır (sivri köşe). */
internal class Ctrl(val x: Float, val y: Float, val corner: Boolean)

/**
 * Glif taslak dili: çizgiler `|` ile ayrılır; her çizgi boşlukla ayrılmış noktalardır: `x,y` yumuşak nokta,
 * `*x,y` köşe, `@cx,cy,rx,ry,a0,a1` elips yayı (derece; 0 = sağ, 90 = aşağı; azalan açı ekranda saat yönünün
 * tersi). Noktalar Catmull-Rom (merkezcil) eğrisiyle birleştirilir; iki köşe arasındaki iki nokta düz çizgidir.
 * Koordinatlar glif kutusunda: y 0 (üst) .. 100 (alt), x doğal genişlik.
 */
internal object Sketch {

    fun parse(src: String): List<List<Ctrl>> =
        src.split('|').map { parseStroke(it.trim()) }.filter { it.isNotEmpty() }

    private fun parseStroke(s: String): List<Ctrl> {
        val out = ArrayList<Ctrl>()
        for (tok0 in s.split(' ', '\n', '\t')) {
            if (tok0.isEmpty()) continue
            var tok = tok0
            var corner = false
            if (tok.startsWith("*")) { corner = true; tok = tok.substring(1) }
            if (tok.startsWith("@")) {
                val v = tok.substring(1).split(',').map { it.toFloat() }
                require(v.size == 6) { "yay: $tok0" }
                val a0 = v[4]; val a1 = v[5]
                val steps = max(2, ceil(abs(a1 - a0) / 30f).toInt())
                for (k in 0..steps) {
                    val a = Math.toRadians((a0 + (a1 - a0) * k / steps).toDouble())
                    out += Ctrl(v[0] + v[2] * cos(a).toFloat(), v[1] + v[3] * sin(a).toFloat(), corner && k == 0)
                }
            } else {
                val v = tok.split(',').map { it.toFloat() }
                require(v.size == 2) { "nokta: $tok0" }
                val last = out.lastOrNull()
                if (last != null && last.x == v[0] && last.y == v[1]) continue
                out += Ctrl(v[0], v[1], corner)
            }
        }
        return out
    }

    /** Kontrol noktalarından yoğun çoklu çizgi. */
    fun render(ctrls: List<Ctrl>, perSeg: Int = 8): Poly {
        if (ctrls.size == 1) return Poly(floatArrayOf(ctrls[0].x), floatArrayOf(ctrls[0].y))
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        var start = 0
        while (start < ctrls.size - 1) {
            var end = start + 1
            while (end < ctrls.size - 1 && !ctrls[end].corner) end++
            val run = ctrls.subList(start, end + 1)
            appendRun(run, perSeg, xs, ys, first = start == 0)
            start = end
        }
        return Poly(xs.toFloatArray(), ys.toFloatArray())
    }

    private fun appendRun(run: List<Ctrl>, perSeg: Int, xs: MutableList<Float>, ys: MutableList<Float>, first: Boolean) {
        if (first) { xs += run[0].x; ys += run[0].y }
        if (run.size == 2) {
            val steps = perSeg
            for (k in 1..steps) {
                val t = k / steps.toFloat()
                xs += run[0].x + (run[1].x - run[0].x) * t
                ys += run[0].y + (run[1].y - run[0].y) * t
            }
            return
        }
        val n = run.size
        for (i in 0 until n - 1) {
            val p1 = run[i]; val p2 = run[i + 1]
            val p0x = if (i > 0) run[i - 1].x else 2 * p1.x - p2.x
            val p0y = if (i > 0) run[i - 1].y else 2 * p1.y - p2.y
            val p3x = if (i + 2 < n) run[i + 2].x else 2 * p2.x - p1.x
            val p3y = if (i + 2 < n) run[i + 2].y else 2 * p2.y - p1.y
            for (k in 1..perSeg) {
                val u = k / perSeg.toFloat()
                val (qx, qy) = centripetal(p0x, p0y, p1.x, p1.y, p2.x, p2.y, p3x, p3y, u)
                xs += qx; ys += qy
            }
        }
    }

    private fun centripetal(
        p0x: Float, p0y: Float, p1x: Float, p1y: Float, p2x: Float, p2y: Float, p3x: Float, p3y: Float, u: Float,
    ): Pair<Float, Float> {
        fun knot(ti: Float, ax: Float, ay: Float, bx: Float, by: Float): Float =
            ti + max(1e-3f, hypot(bx - ax, by - ay).toDouble().pow(0.5).toFloat())
        val t0 = 0f
        val t1 = knot(t0, p0x, p0y, p1x, p1y)
        val t2 = knot(t1, p1x, p1y, p2x, p2y)
        val t3 = knot(t2, p2x, p2y, p3x, p3y)
        val t = t1 + (t2 - t1) * u
        fun lerp(ax: Float, bx: Float, ta: Float, tb: Float) = (tb - t) / (tb - ta) * ax + (t - ta) / (tb - ta) * bx
        val a1x = lerp(p0x, p1x, t0, t1); val a1y = lerp(p0y, p1y, t0, t1)
        val a2x = lerp(p1x, p2x, t1, t2); val a2y = lerp(p1y, p2y, t1, t2)
        val a3x = lerp(p2x, p3x, t2, t3); val a3y = lerp(p2y, p3y, t2, t3)
        val b1x = lerp(a1x, a2x, t0, t2); val b1y = lerp(a1y, a2y, t0, t2)
        val b2x = lerp(a2x, a3x, t1, t3); val b2y = lerp(a2y, a3y, t1, t3)
        return lerp(b1x, b2x, t1, t2) to lerp(b1y, b2y, t1, t2)
    }

    fun renderAll(src: String, perSeg: Int = 8): List<Poly> = parse(src).map { render(it, perSeg) }
}
