package com.karapp.drawguidance.content.graph

import com.karapp.drawguidance.content.vector.LineStyle
import com.karapp.drawguidance.content.vector.P
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Sayı doğrusu, birim çember ve üç boyutlu eksenler. */
internal object Special {

    // ---------------------------------------------------------------- NUMBER_LINE

    /** ticks → birim başına alt bölme (≤5: yok). */
    private val SUBDIV = intArrayOf(1, 1, 1, 1, 1, 2, 3, 4, 5, 6, 8, 10)

    fun subdivisions(ticks: Int) = SUBDIV[ticks.coerceIn(1, 12) - 1]

    /** Sayı doğrusu çentik anahtarı: tamsayıda "x:3", ara çentikte sadeleşmiş kesir "x:-1/2". */
    fun numberLineKey(k: Int, sub: Int): String {
        if (k % sub == 0) return "x:${k / sub}"
        val g = Fmt.gcd(k, sub)
        return "x:${k / g}/${sub / g}"
    }

    fun numberLine(c: Canvas) {
        val p = c.p
        val rep = p.repeat.coerceIn(1, 20)
        val sub = subdivisions(p.ticks)
        val unit = if (p.tickStep > 0) p.tickStep.toDouble() else 1.0
        val lineY = c.H * 0.5f - 0.01f
        val x0 = Style.PAD
        val x1 = c.W - Style.PAD
        val inner = Style.ARROW_LEN + 0.03f
        val spacing = (x1 - x0 - 2 * inner) / (2 * rep)
        val cx = c.W / 2

        c.layer = 3
        c.line(x0 + Style.ARROW_LEN * 0.6f, lineY, x1 - Style.ARROW_LEN * 0.6f, lineY, width = 1.1f)
        c.arrowHead(x1, lineY, 1f, 0f)
        c.arrowHead(x0, lineY, -1f, 0f)

        val ks = -rep * sub..rep * sub
        val dec = Fmt.decimals(unit)
        val texts = ks.associateWith { k ->
            val def = if (k % sub == 0) Fmt.num((k / sub) * unit, dec) else ""
            c.tickText(numberLineKey(k, sub), def)
        }
        val majors = ks.filter { it % sub == 0 }.mapNotNull { texts.getValue(it).first }
        var size = 0.05f
        val unitW = majors.maxOfOrNull { c.measure(it, 1f) } ?: 1f
        size = min(size, (spacing * 0.92f / unitW).coerceAtLeast(0.022f))
        val every = max(1, ceil((unitW * size + 0.012f) / spacing).toInt())
        val minusHalf = c.measure(Fmt.MINUS, size) / 2
        for (k in ks) {
            val x = cx + k * spacing / sub
            val major = k % sub == 0
            val h = if (major) 0.024f else 0.014f
            val key = numberLineKey(k, sub)
            c.layer = 3
            c.line(x, lineY - h, x, lineY + h, width = if (major) 1f else 0.8f, tag = "tick:$key")
            val (s, forced) = texts.getValue(k)
            if (s == null) continue
            if (!forced && (!p.showNumbers || (k / sub) % every != 0)) continue
            val ts = if (major) size else size * 0.8f
            val w = c.measure(s, ts)
            val shift = if (s.startsWith(Fmt.MINUS)) minusHalf else 0f
            c.layer = 6
            c.text(x - w / 2 - shift, lineY + 0.024f + Style.GAP, s, ts, tag = "tick:$key")
        }
        // v45: formülün çözüm kümesi (kalın renkli aralıklar, dolu/boş uç noktaları) ve formül yazısı
        if (p.formula.isNotBlank()) solutionSet(c, cx, lineY, spacing, unit, x0 + inner * 0.5f, x1 - inner * 0.5f)
        // İşaretli noktalar
        p.values.forEachIndexed { i, v ->
            if (!v.isFinite()) return@forEachIndexed
            val x = cx + (v / unit).toFloat() * spacing
            if (x < x0 + inner * 0.5f || x > x1 - inner * 0.5f) return@forEachIndexed
            c.layer = 5
            c.dot(x, lineY, Style.DOT * 1.3f, tag = "value:$i")
            val name = p.valueLabels[i] ?: ""
            if (name.isNotEmpty()) {
                c.layer = 6
                val ts = 0.045f
                c.text(x - c.measure(name, ts) / 2, lineY - 0.03f - ts, name, ts, color = c.color, tag = "vlabel:$i")
            }
        }
    }

    /** v45: sayı doğrusunda formülün çözüm kümesi. [xa]..[xb] çizilebilir yatay aralık. */
    private fun solutionSet(c: Canvas, cx: Float, lineY: Float, spacing: Float, unit: Double, xa: Float, xb: Float) {
        val p = c.p
        val lo = ((xa - cx) / spacing).toDouble() * unit
        val hi = ((xb - cx) / spacing).toDouble() * unit
        fun X(v: Double) = (cx + (v / unit) * spacing).toFloat()
        val parts = GraphFormula.parts(p.formula).take(GraphFormula.MAX_CURVES)
        parts.forEachIndexed { k, text ->
            val set = GraphFormula.solve(text, lo, hi, p.angleDeg) ?: return@forEachIndexed
            val color = if (k == 0) c.color else Cartesian.EXTRA_COLORS[(k - 1) % Cartesian.EXTRA_COLORS.size]
            // birden çok formülde kümeler biraz yukarı kaydırılır (üst üste binmesin)
            val y = lineY - k * 0.035f
            c.layer = 4
            for (iv in set.intervals) {
                val a = X(iv.a); val b = X(iv.b)
                c.line(a, y, b, y, stroke = color, width = 3.2f, tag = "curve")
                if (iv.aInf) c.arrowHead(a - 0.004f, y, -1f, 0f, color = color) else endDot(c, a, y, iv.aClosed, color)
                if (iv.bInf) c.arrowHead(b + 0.004f, y, 1f, 0f, color = color) else endDot(c, b, y, iv.bClosed, color)
            }
            for (v in set.points) {
                val x = X(v)
                if (x < xa || x > xb) continue
                c.layer = 5
                c.dot(x, y, Style.DOT * 1.3f, fill = color, tag = "key")
            }
        }
        val label = p.tickLabels["formula"] ?: Graphs.formulaLabel(p)
        c.defaults["formula"] = Graphs.formulaLabel(p)
        if (label.isNotBlank()) {
            c.layer = 6
            val ts = 0.05f
            c.text(Style.PAD, Style.PAD * 0.4f, label, ts, color = c.color, tag = "formula")
        }
    }

    private fun endDot(c: Canvas, x: Float, y: Float, closed: Boolean, color: String) {
        c.layer = 5
        if (closed) c.dot(x, y, Style.DOT * 1.3f, fill = color, tag = "key") else c.openDot(x, y, Style.DOT * 1.3f, stroke = color, tag = "key")
    }

    // ---------------------------------------------------------------- UNIT_CIRCLE

    private fun exact(v: Double): String {
        val a = abs(v)
        val s = when {
            a < 1e-9 -> return "0"
            abs(a - 1) < 1e-9 -> "1"
            abs(a - 0.5) < 1e-9 -> "1/2"
            abs(a - sqrt(2.0) / 2) < 1e-9 -> "√2/2"
            abs(a - sqrt(3.0) / 2) < 1e-9 -> "√3/2"
            else -> Fmt.auto(a, 3)
        }
        return if (v < 0) Fmt.MINUS + s else s
    }

    /** repeat = çeyrek sayısı, ticks = açı kümesi. */
    fun angles(p: GraphParams): List<Int> {
        val quadrants = p.repeat.coerceIn(1, 4)
        val set = p.ticks.coerceIn(1, 12)
        val out = ArrayList<Int>()
        for (deg in 15 until 360 step 15) {
            if (deg % 90 == 0 || deg / 90 >= quadrants) continue
            val r = deg % 90
            val ok = when {
                set <= 2 -> false
                set == 3 -> r == 45
                set == 4 -> r == 30 || r == 60
                else -> r == 30 || r == 45 || r == 60
            }
            if (ok) out += deg
        }
        return out
    }

    fun unitCircle(c: Canvas) {
        val p = c.p
        val angles = angles(p)
        val showCoords = p.showNumbers && angles.isNotEmpty()
        val ls = 0.031f
        val lrx = if (showCoords) 0.2f else 0.08f
        val lry = if (showCoords) 0.085f else 0.07f
        val r = (min(c.W - 2 * (Style.PAD + lrx), c.H - 2 * (Style.PAD + lry)) / 2).coerceAtLeast(0.1f)
        val cx = c.W / 2
        val cy = c.H / 2
        val ex = min(r * 1.3f, c.W / 2 - Style.PAD)
        val ey = min(r * 1.3f, c.H / 2 - Style.PAD)

        if (p.grid) {
            c.layer = 0
            c.add(c.circlePoints(cx, cy, r, 48), closed = true, fill = Colors.lighten(c.color, 0.93f), width = 0f)
        }
        c.layer = 3
        c.line(cx - ex, cy, cx + ex - Style.ARROW_LEN * 0.6f, cy, width = Style.W_AXIS)
        c.arrowHead(cx + ex, cy, 1f, 0f)
        c.line(cx, cy + ey, cx, cy - ey + Style.ARROW_LEN * 0.6f, width = Style.W_AXIS)
        c.arrowHead(cx, cy - ey, 0f, -1f)
        for ((key, pt) in listOf("x:1" to P(cx + r, cy), "x:-1" to P(cx - r, cy))) c.line(pt.x, cy - Style.TICK, pt.x, cy + Style.TICK, width = Style.W_TICK, tag = "tick:$key")
        for ((key, pt) in listOf("y:1" to P(cx, cy - r), "y:-1" to P(cx, cy + r))) c.line(cx - Style.TICK, pt.y, cx + Style.TICK, pt.y, width = Style.W_TICK, tag = "tick:$key")

        c.layer = 4
        c.ring(cx, cy, r, stroke = c.color, width = 1.6f, tag = "curve")

        for (deg in angles) {
            val th = Math.toRadians(deg.toDouble())
            val cs = cos(th).toFloat()
            val sn = sin(th).toFloat()
            val px = cx + r * cs
            val py = cy - r * sn
            c.layer = 2
            c.line(cx, cy, px, py, stroke = c.color, width = 0.8f, line = LineStyle.DASHED, alpha = 0.8f)
            if (p.ticks >= 6) {
                c.line(px, py, px, cy, stroke = c.pal.guide, width = 0.7f, line = LineStyle.DOTTED)
                c.line(px, py, cx, py, stroke = c.pal.guide, width = 0.7f, line = LineStyle.DOTTED)
            }
            c.layer = 5
            c.dot(px, py, Style.DOT, tag = "angle:$deg")
        }

        // Yazılar
        c.layer = 6
        val ns = Style.NAME_SIZE
        if (p.xLabel.isNotEmpty()) c.text(cx + ex - c.measure(p.xLabel, ns), cy - Style.GAP * 1.2f - ns, p.xLabel, ns, tag = "axis:x")
        if (p.yLabel.isNotEmpty()) c.text(cx + Style.GAP * 1.6f, cy - ey - ns * 0.1f, p.yLabel, ns, tag = "axis:y")
        val ts = Style.TICK_SIZE
        fun tick(key: String, def: String, place: (Float, Float) -> P) {
            val (s, forced) = c.tickText(key, def)
            if (s == null || !(p.showNumbers || forced)) return
            val w = c.measure(s, ts)
            val at = place(w, ts)
            c.text(at.x, at.y, s, ts, tag = "tick:$key")
        }
        tick("x:1", "1") { _, _ -> P(cx + r + 0.006f, cy + 0.006f) }
        tick("x:-1", "${Fmt.MINUS}1") { w, _ -> P(cx - r - 0.006f - w, cy + 0.006f) }
        tick("y:1", "1") { _, h -> P(cx + 0.008f, cy - r - 0.006f - h) }
        tick("y:-1", "${Fmt.MINUS}1") { _, _ -> P(cx + 0.008f, cy + r + 0.006f) }
        tick("x:0", "O") { w, _ -> P(cx - 0.008f - w, cy + 0.006f) }

        for (deg in angles) {
            val th = Math.toRadians(deg.toDouble())
            val cs = cos(th)
            val sn = sin(th)
            val px = cx + r * cs.toFloat()
            val py = cy - r * sn.toFloat()
            // Koordinat (cos, sin)
            val (coord, forcedC) = c.tickText("c:$deg", "(${exact(cs)}, ${exact(sn)})")
            if (coord != null && (p.showNumbers || forcedC)) {
                val w = c.measure(coord, ls)
                var placed = false
                for (off in floatArrayOf(0.016f, 0.03f, 0.046f)) {
                    val axp = px + off * cs.toFloat()
                    val ayp = py - off * sn.toFloat()
                    val bx = when { cs > 0.1 -> axp; cs < -0.1 -> axp - w; else -> axp - w / 2 }
                    val by = when { sn > 0.1 -> ayp - ls; sn < -0.1 -> ayp; else -> ayp - ls / 2 }
                    val box = c.textBox(bx, by, coord, ls)
                    if (c.fits(box, 0.002f) || (forcedC && off > 0.04f)) {
                        c.text(bx, by, coord, ls, tag = "tick:c:$deg")
                        placed = true
                        break
                    }
                }
                if (!placed && forcedC) c.text(px, py, coord, ls, tag = "tick:c:$deg")
            }
            // Açı (derece)
            val (angle, forcedA) = c.tickText("a:$deg", "$deg°")
            if (angle != null && (p.showNumbers || forcedA)) {
                val als = ls * 0.88f
                val w = c.measure(angle, als)
                val rr = r * 0.8f
                val shiftA = Math.toRadians(deg.toDouble() - 6.0)
                val ax = cx + rr * cos(shiftA).toFloat()
                val ay = cy - rr * sin(shiftA).toFloat()
                val box = c.textBox(ax - w / 2, ay - als / 2, angle, als)
                if (forcedA || c.fits(box, 0.002f)) c.text(box.l, box.t, angle, als, color = c.color, tag = "tick:a:$deg")
            }
        }
    }

    // ---------------------------------------------------------------- AXES_3D

    private class V3(val x: Double, val y: Double, val z: Double)

    fun axes3d(c: Canvas) {
        val p = c.p
        val az = Math.toRadians(32.0)
        val el = Math.toRadians(22.0)
        val rx = doubleArrayOf(-sin(az), cos(az), 0.0)
        val ux = doubleArrayOf(-sin(el) * cos(az), -sin(el) * sin(az), cos(el))
        val dv = doubleArrayOf(cos(el) * cos(az), cos(el) * sin(az), sin(el))
        fun sx(v: V3) = v.x * rx[0] + v.y * rx[1] + v.z * rx[2]
        fun sy(v: V3) = v.x * ux[0] + v.y * ux[1] + v.z * ux[2]
        fun depth(v: V3) = v.x * dv[0] + v.y * dv[1] + v.z * dv[2]

        val expr = if (p.formula.isNotBlank()) Expression.parse(
            GraphFormula.parts(p.formula).firstOrNull().orEmpty(),
            if (p.angleDeg) com.karapp.drawguidance.content.math.AngleMode.DEG else com.karapp.drawguidance.content.math.AngleMode.RAD,
        ) else null
        val n = p.ticks.coerceIn(1, 12)
        var kz = 1.0
        val stepXY: Double
        val stepZ: Double
        val lx: Double
        val lz: Double
        val negXY: Double
        val negZ: Double
        var surface: Array<Array<V3?>>? = null
        var zmin = 0.0
        var zmax = 0.0
        if (expr == null) {
            stepXY = if (p.tickStep > 0) p.tickStep.toDouble() else 1.0
            stepZ = stepXY
            lx = (n + 0.8) * stepXY
            lz = lx
            negXY = 0.35 * n * stepXY
            negZ = negXY
        } else {
            val rr = p.repeat.coerceIn(1, 8).toDouble()
            val g = (c.res / 26f).toInt().coerceIn(12, 30)
            val zs = ArrayList<Double>()
            val raw = Array(g + 1) { j -> DoubleArray(g + 1) { i -> expr.eval(-rr + 2 * rr * i / g, -rr + 2 * rr * j / g).also { if (it.isFinite()) zs += it } } }
            if (zs.isNotEmpty()) {
                zs.sort()
                zmin = zs[(zs.size * 0.02).toInt().coerceIn(0, zs.size - 1)]
                zmax = zs[(zs.size * 0.98).toInt().coerceIn(0, zs.size - 1)]
                if (zs.first() >= zmin - 0.3 * (zmax - zmin)) zmin = zs.first()
                if (zs.last() <= zmax + 0.3 * (zmax - zmin)) zmax = zs.last()
            }
            val span = zmax - zmin
            kz = if (span < 1e-9) 1.0 else if (span in 0.5 * rr..2.2 * rr) 1.0 else 1.2 * rr / span
            surface = Array(g + 1) { j ->
                Array(g + 1) { i ->
                    val z = raw[j][i]
                    if (!z.isFinite()) null else V3(-rr + 2 * rr * i / g, -rr + 2 * rr * j / g, z.coerceIn(zmin - 0.05 * span, zmax + 0.05 * span) * kz)
                }
            }
            stepXY = if (p.tickStep > 0) p.tickStep.toDouble() else Fmt.niceCeil(rr * 1.4 / n)
            val zExt = max(abs(zmax), abs(zmin)).coerceAtLeast(1e-9)
            stepZ = Fmt.niceCeil(zExt / n)
            lx = rr * 1.45
            lz = max(max(zmax, 0.0) * kz * 1.25, 0.6 * rr)
            negXY = rr * 1.1
            negZ = if (zmin < 0) -zmin * kz * 1.1 else 0.35 * rr
        }

        // Sığdırma
        val probe = ArrayList<V3>()
        probe += V3(lx, 0.0, 0.0); probe += V3(-negXY, 0.0, 0.0)
        probe += V3(0.0, lx, 0.0); probe += V3(0.0, -negXY, 0.0)
        probe += V3(0.0, 0.0, lz); probe += V3(0.0, 0.0, -negZ)
        surface?.forEach { row -> row.forEach { v -> if (v != null) probe += v } }
        p.values.chunked(3).forEach { t -> if (t.size == 3) probe += V3(t[0].toDouble(), t[1].toDouble(), t[2].toDouble() * kz) }
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (v in probe) { minX = min(minX, sx(v)); maxX = max(maxX, sx(v)); minY = min(minY, sy(v)); maxY = max(maxY, sy(v)) }
        val m = 0.075f
        val formulaRoom = if (expr != null) Style.FORMULA_SIZE * 1.7f else 0f
        val availW = c.W - 2 * (Style.PAD + m)
        val availH = c.H - 2 * (Style.PAD + m) - formulaRoom
        val s = min(availW / (maxX - minX), availH / (maxY - minY)).coerceAtLeast(1e-6)
        val ox = c.W / 2 - s * (minX + maxX) / 2
        val oy = (c.H + formulaRoom) / 2 + s * (minY + maxY) / 2
        fun X(v: V3) = (ox + s * sx(v)).toFloat()
        fun Y(v: V3) = (oy - s * sy(v)).toFloat()
        fun pt(v: V3) = P(X(v), Y(v))

        // Taban ızgarası
        if (p.grid && expr == null) {
            c.layer = 0
            for (k in 1..n) {
                val t = k * stepXY
                val e = n * stepXY
                c.add(listOf(pt(V3(t, 0.0, 0.0)), pt(V3(t, e, 0.0))), stroke = c.pal.grid, width = Style.W_GRID)
                c.add(listOf(pt(V3(0.0, t, 0.0)), pt(V3(e, t, 0.0))), stroke = c.pal.grid, width = Style.W_GRID)
            }
        }

        // Yüzey (ressam algoritması: uzaktan yakına)
        if (surface != null) {
            c.layer = 1
            class Quad(val pts: List<V3>, val d: Double, val zAvg: Double)
            val quads = ArrayList<Quad>()
            val g = surface.size - 1
            for (j in 0 until g) for (i in 0 until g) {
                val a = surface[j][i] ?: continue
                val b = surface[j][i + 1] ?: continue
                val cc = surface[j + 1][i + 1] ?: continue
                val d = surface[j + 1][i] ?: continue
                val list = listOf(a, b, cc, d)
                quads += Quad(list, list.sumOf { depth(it) } / 4, list.sumOf { it.z } / 4 / kz)
            }
            quads.sortBy { it.d }
            val light = doubleArrayOf(0.35, -0.45, 0.82).let { v -> val l = sqrt(v.sumOf { it * it }); DoubleArray(3) { v[it] / l } }
            val span = (zmax - zmin).coerceAtLeast(1e-9)
            val low = Colors.lighten(c.color, 0.8f)
            for (q in quads) {
                val a = q.pts[0]; val b = q.pts[1]; val d = q.pts[3]
                val e1 = doubleArrayOf(b.x - a.x, b.y - a.y, b.z - a.z)
                val e2 = doubleArrayOf(d.x - a.x, d.y - a.y, d.z - a.z)
                var nx = e1[1] * e2[2] - e1[2] * e2[1]
                var ny = e1[2] * e2[0] - e1[0] * e2[2]
                var nz = e1[0] * e2[1] - e1[1] * e2[0]
                val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-12)
                nx /= nl; ny /= nl; nz /= nl
                if (nx * dv[0] + ny * dv[1] + nz * dv[2] < 0) { nx = -nx; ny = -ny; nz = -nz }
                val lam = max(0.0, nx * light[0] + ny * light[1] + nz * light[2])
                val t = ((q.zAvg - zmin) / span).coerceIn(0.0, 1.0).toFloat()
                val base = Colors.mix(low, c.color, t * 0.9f)
                val fill = Colors.mix(base, "#000000", ((1 - lam) * 0.3).toFloat())
                val edge = Colors.mix(fill, Colors.darken(c.color, 0.45f), 0.45f)
                c.add(q.pts.map { pt(it) }, closed = true, fill = fill, stroke = edge, width = 0.3f, tag = "curve")
            }
        }

        // Eksenler
        val axes = listOf(
            Triple("x", V3(1.0, 0.0, 0.0), Pair(lx, negXY)),
            Triple("y", V3(0.0, 1.0, 0.0), Pair(lx, negXY)),
            Triple("z", V3(0.0, 0.0, 1.0), Pair(lz, negZ)),
        )
        val o = V3(0.0, 0.0, 0.0)
        for ((_, d, len) in axes) {
            c.layer = 2
            val neg = V3(-d.x * len.second, -d.y * len.second, -d.z * len.second)
            c.add(listOf(pt(o), pt(neg)), width = 0.8f, line = LineStyle.DASHED, alpha = 0.65f)
        }
        c.layer = 3
        for ((name, d, len) in axes) {
            val tip = V3(d.x * len.first, d.y * len.first, d.z * len.first)
            val tx = X(tip); val ty = Y(tip)
            val dx = tx - X(o); val dy = ty - Y(o)
            val l = hypot(dx, dy).coerceAtLeast(1e-6f)
            c.line(X(o), Y(o), tx - dx / l * Style.ARROW_LEN * 0.6f, ty - dy / l * Style.ARROW_LEN * 0.6f, width = Style.W_AXIS)
            c.arrowHead(tx, ty, dx, dy)
            val step = if (name == "z") stepZ else stepXY
            val kzAxis = if (name == "z") kz else 1.0
            var k = 1
            while (k * step * kzAxis < len.first - 0.35 * step * kzAxis && k <= 40) {
                val v = k * step * kzAxis
                val at = V3(d.x * v, d.y * v, d.z * v)
                val px = X(at); val py = Y(at)
                val nx = -dy / l; val ny = dx / l
                c.line(px - nx * Style.TICK, py - ny * Style.TICK, px + nx * Style.TICK, py + ny * Style.TICK, width = Style.W_TICK, tag = "tick:$name:$k")
                k++
            }
        }

        // Yazılar
        c.layer = 6
        val ts = Style.TICK_SIZE
        for ((name, d, len) in axes) {
            val tip = V3(d.x * len.first, d.y * len.first, d.z * len.first)
            val tx = X(tip); val ty = Y(tip)
            val dx = tx - X(o); val dy = ty - Y(o)
            val l = hypot(dx, dy).coerceAtLeast(1e-6f)
            val ux1 = dx / l; val uy1 = dy / l
            // Eksene dik, yazının gideceği yön: z için sol, diğerleri için aşağı
            var nx = -uy1; var ny = ux1
            if (name == "z") { if (nx > 0) { nx = -nx; ny = -ny } } else if (ny < 0) { nx = -nx; ny = -ny }
            val step = if (name == "z") stepZ else stepXY
            val kzAxis = if (name == "z") kz else 1.0
            var k = 1
            val spacing = step * kzAxis * s * hypot(sx(d), sy(d))
            val labels = ArrayList<Pair<Int, String>>()
            while (k * step * kzAxis < len.first - 0.35 * step * kzAxis && k <= 40) {
                c.tickText("$name:$k", Fmt.num(k * step, Fmt.decimals(step))).first?.let { t -> if (p.showNumbers || p.tickLabels.containsKey("$name:$k")) labels += k to t }
                k++
            }
            val maxW = labels.maxOfOrNull { c.measure(it.second, ts) } ?: 0f
            val need = (abs(ux1) * (maxW + 0.01f) + abs(uy1) * (ts + 0.006f))
            val every = max(1, ceil(need / spacing).toInt())
            for ((kk, t) in labels) {
                if (kk % every != 0 && !p.tickLabels.containsKey("$name:$kk")) continue
                val v = kk * step * kzAxis
                val at = V3(d.x * v, d.y * v, d.z * v)
                val w = c.measure(t, ts)
                val off = Style.TICK + Style.GAP * 0.6f + abs(nx) * w / 2 + abs(ny) * ts / 2
                val ccx = X(at) + nx * off
                val ccy = Y(at) + ny * off
                c.text(ccx - w / 2, ccy - ts / 2, t, ts, tag = "tick:$name:$kk")
            }
            val label = when (name) { "x" -> p.xLabel; "y" -> p.yLabel; else -> p.zLabel }
            if (label.isNotEmpty()) {
                val ns = Style.NAME_SIZE
                val w = c.measure(label, ns)
                val off = 0.012f + abs(ux1) * w / 2 + abs(uy1) * ns / 2
                val lcx = tx + ux1 * off
                val lcy = ty + uy1 * off
                c.text(lcx - w / 2, lcy - ns / 2, label, ns, tag = "axis:$name")
            }
        }
        run {
            val (t, forced) = c.tickText("x:0", "O")
            if (t != null && (p.showNumbers || forced)) {
                val w = c.measure(t, ts)
                c.text(X(o) - w / 2, Y(o) + 0.014f, t, ts, tag = "tick:x:0")
            }
        }

        // Noktalar: values = x, y, z üçlüleri
        p.values.chunked(3).forEachIndexed { i, t ->
            if (t.size < 3 || t.any { !it.isFinite() }) return@forEachIndexed
            val x = t[0].toDouble(); val y = t[1].toDouble(); val z = t[2].toDouble()
            val P0 = V3(x, y, z * kz)
            val base = V3(x, y, 0.0)
            c.layer = 2
            c.add(listOf(pt(V3(x, 0.0, 0.0)), pt(base), pt(V3(0.0, y, 0.0))), stroke = c.pal.guide, width = 0.7f, line = LineStyle.DASHED)
            c.add(listOf(pt(base), pt(P0)), stroke = c.pal.guide, width = 0.7f, line = LineStyle.DASHED)
            c.add(listOf(pt(P0), pt(V3(0.0, 0.0, z * kz))), stroke = c.pal.guide, width = 0.7f, line = LineStyle.DASHED)
            c.layer = 5
            c.dot(X(P0), Y(P0), Style.DOT * 1.15f, tag = "value:$i")
            c.layer = 6
            val name = p.valueLabels[i] ?: Cartesian.letter(i)
            val label = if (p.showNumbers) "$name(${Fmt.auto(x)}, ${Fmt.auto(y)}, ${Fmt.auto(z)})" else name
            if (label.isNotEmpty()) c.text(X(P0) + 0.014f, Y(P0) - 0.012f - Style.KEY_SIZE, label, Style.KEY_SIZE, tag = "vlabel:$i")
        }

        // Formül kutusu
        val formula = p.tickLabels["formula"] ?: if (expr != null) Graphs.formulaLabel(p) else ""
        c.defaults["formula"] = if (p.formula.isNotBlank()) Graphs.formulaLabel(p) else ""
        if (formula.isNotBlank()) {
            val size = min(Style.FORMULA_SIZE, (c.W - 2 * Style.PAD) / (c.measure(formula, 1f) + 0.9f))
            val tw = c.measure(formula, size)
            val bx = Style.PAD
            val by = Style.PAD
            c.layer = 7
            c.add(c.roundRectPoints(bx, by, tw + size * 0.9f, size * 1.66f, size * 0.32f), closed = true, fill = c.pal.paper, stroke = c.pal.boxBorder, width = 0.5f, alpha = 0.93f, tag = "formula")
            c.text(bx + size * 0.45f, by + size * 0.33f, formula, size, color = c.color, tag = "formula")
        }
    }
}
