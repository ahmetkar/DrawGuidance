package com.karapp.drawguidance.content.graph

import com.karapp.drawguidance.content.vector.P
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/** Veri grafikleri: sütun (BAR), çizgi (LINE), daire (PIE), serpilme (SCATTER), histogram (HISTOGRAM). */
internal object Charts {

    private val BASE_BAR = listOf(6f, 9f, 5f, 8f, 4f, 7f, 10f, 6f, 8f, 5f, 9f, 7f, 6f, 8f, 5f)
    private val BASE_PIE = listOf(35f, 25f, 20f, 12f, 8f, 15f, 10f, 18f, 6f, 9f, 11f, 7f, 5f, 13f, 4f)
    private val NOISE = listOf(0.8f, -0.6f, 1.1f, -0.9f, 0.3f, -0.4f, 0.9f, -1.0f, 0.5f, -0.2f, 0.7f, -0.8f, 0.2f, -0.5f, 1.0f)

    fun isChart(kind: GraphKind) = kind == GraphKind.BAR || kind == GraphKind.LINE || kind == GraphKind.PIE ||
        kind == GraphKind.SCATTER || kind == GraphKind.HISTOGRAM

    /** [GraphParams.values] boşken öğe sayısı. */
    fun defaultCount(p: GraphParams): Int = (p.repeat + 3).coerceIn(2, 15)

    /** [kind] için [n] öğelik hoş varsayılan veri (SCATTER'da n nokta = 2n değer). */
    fun defaults(kind: GraphKind, n: Int): List<Float> = when (kind) {
        GraphKind.PIE -> List(n) { BASE_PIE[it % BASE_PIE.size] }
        GraphKind.HISTOGRAM -> List(n) { i ->
            val t = (i - (n - 1) / 2.0) / (n / 3.4)
            round(2 + 10 * exp(-t * t)).toFloat()
        }
        GraphKind.SCATTER -> {
            val out = ArrayList<Float>(2 * n)
            for (i in 0 until n) {
                val x = if (n <= 1) 5.0 else 1.0 + 8.0 * i / (n - 1)
                val y = 1.5 + 0.7 * x + NOISE[i % NOISE.size]
                out += (round(x * 10) / 10).toFloat()
                out += (round(y * 10) / 10).toFloat()
            }
            out
        }
        else -> List(n) { BASE_BAR[it % BASE_BAR.size] }
    }

    /** Çizimde kullanılan veri: kullanıcınınki ya da varsayılan. */
    fun effective(p: GraphParams): List<Float> {
        if (p.values.isNotEmpty() || !isChart(p.kind)) return p.values
        // v45: formül yazılmışsa öğeler x = 1..n'de f(x)'ten örneklenir
        GraphFormula.sampled(p, defaultCount(p))?.let { return it }
        val n = if (p.kind == GraphKind.SCATTER) defaultCount(p) * 2 else defaultCount(p)
        return defaults(p.kind, n)
    }

    fun itemCount(p: GraphParams): Int = effective(p).size.let { if (p.kind == GraphKind.SCATTER) it / 2 else it }

    fun build(c: Canvas) {
        when (c.p.kind) {
            GraphKind.BAR, GraphKind.LINE, GraphKind.HISTOGRAM -> valueChart(c)
            GraphKind.PIE -> pie(c)
            GraphKind.SCATTER -> scatter(c)
            else -> {}
        }
        // v45: formülden örneklenen grafikte formül sağ üst köşede
        val p = c.p
        if (p.formula.isNotBlank() && p.values.isEmpty() && GraphFormula.sampled(p, defaultCount(p)) != null) {
            val label = p.tickLabels["formula"] ?: Graphs.formulaLabel(p)
            c.defaults["formula"] = Graphs.formulaLabel(p)
            if (label.isNotBlank()) {
                val ts = Style.FORMULA_SIZE * 0.9f
                c.layer = 6
                c.text(c.W - Style.PAD - c.measure(label, ts), Style.PAD * 0.3f, label, ts, color = c.color, tag = "formula", reserve = false)
            }
        }
    }

    private fun fitSize(c: Canvas, texts: List<String>, room: Float, max: Float, min: Float): Float {
        val unit = texts.maxOfOrNull { c.measure(it, 1f) }?.coerceAtLeast(0.3f) ?: 1f
        return (room / unit).coerceIn(min, max)
    }

    // ---------------------------------------------------------------- BAR / LINE / HISTOGRAM

    private fun valueChart(c: Canvas) {
        val p = c.p
        val kind = p.kind
        val vals = effective(p).map { if (it.isFinite()) it.toDouble() else 0.0 }
        val n = vals.size
        if (n == 0) return
        val vmax = max(0.0, vals.max())
        val vmin = min(0.0, vals.min())
        val ext = max(vmax, -vmin).let { if (it <= 0) 1.0 else it }
        val tk = p.ticks.coerceIn(1, 12)
        var step = if (p.tickStep > 0) p.tickStep.toDouble() else Fmt.niceCeil(ext / tk)
        while ((vmax - vmin) / step > 60) step = Fmt.larger(step)
        val kTop = max(if (vmax > 0) ceil(vmax / step - 1e-9).toInt() else 0, if (vmin < 0) 0 else 1)
        val kBot = if (vmin < 0) floor(vmin / step + 1e-9).toInt() else 0
        val yt = Ticks(step)
        val tsize = Style.TICK_SIZE
        val yTexts = (kBot..kTop).associateWith { c.tickText("y:$it", yt.label(it)) }
        val yw = yTexts.values.maxOf { (s, forced) -> if (s != null && (p.showNumbers || forced)) c.measure(s, tsize) else 0f }

        val names = List(n) { p.valueLabels[it] ?: Cartesian.letter(it) }
        val nameS = Style.NAME_SIZE
        val xName = p.xLabel
        val xNameW = if (xName.isEmpty()) 0f else c.measure(xName, nameS)
        val xNameRight = xName.isNotEmpty() && xNameW <= 0.22f * c.W
        val headroom = if (p.showNumbers) Style.KEY_SIZE + Style.GAP else 0f
        val mT = Style.PAD + Style.AXIS_EXT + headroom
        val mL = Style.PAD + yw + Style.GAP + Style.TICK + 0.004f
        val mR = Style.PAD + Style.AXIS_EXT + (if (xNameRight) xNameW + Style.GAP * 1.2f else 0f)
        val L = mL
        val R = c.W - mR
        val slot = (R - L) / n
        val catSize = if (kind == GraphKind.HISTOGRAM) tsize else fitSize(c, names, slot * 0.94f, 0.042f, 0.022f)
        val labelRow = if (kind == GraphKind.HISTOGRAM) Style.TICK + Style.GAP + tsize else catSize + Style.GAP * 1.4f
        val mB = Style.PAD + labelRow + (if (!xNameRight && xName.isNotEmpty()) nameS + Style.GAP else 0f)
        val T = mT
        val B = c.H - mB
        val fr = Frame(0.0, n.toDouble(), kBot * step, kTop * step, L, T, R, B)
        val ay = fr.py(0.0).toFloat()
        val tipY = Style.PAD

        // Izgara (yatay)
        if (p.grid) {
            c.layer = 0
            for (k in kBot..kTop) {
                if (k == 0) continue
                val y = fr.py(yt.value(k)).toFloat()
                c.line(L, y, R, y, stroke = c.pal.grid, width = Style.W_GRID)
            }
        }

        // Sütunlar / çizgi
        val valSize = fitSize(c, vals.map { Fmt.auto(it) }, slot * 0.92f, Style.KEY_SIZE, 0.02f)
        when (kind) {
            GraphKind.BAR, GraphKind.HISTOGRAM -> {
                c.layer = 1
                for (i in 0 until n) {
                    val v = vals[i]
                    val bw = if (kind == GraphKind.BAR) slot * 0.62f else slot
                    val x = if (kind == GraphKind.BAR) L + slot * (i + 0.5f) - bw / 2 else L + slot * i
                    val yv = fr.py(v).toFloat()
                    val fill = if (kind == GraphKind.BAR) Style.PALETTE[i % Style.PALETTE.size] else Colors.lighten(c.color, 0.3f)
                    val stroke = if (kind == GraphKind.BAR) Colors.darken(fill, 0.22f) else Colors.darken(c.color, 0.2f)
                    val radius = if (kind == GraphKind.BAR) min(bw * 0.14f, 0.01f) else 0f
                    c.add(barPoints(x, bw, ay, yv, radius), closed = true, fill = fill, stroke = stroke, width = if (kind == GraphKind.BAR) 0.6f else 0.8f, tag = "value:$i")
                }
            }
            else -> {
                val pts = List(n) { P(L + slot * (it + 0.5f), fr.py(vals[it]).toFloat()) }
                if (p.fillUnder && n >= 2) {
                    c.layer = 1
                    c.add(listOf(P(pts.first().x, ay)) + pts + P(pts.last().x, ay), closed = true, fill = c.color, width = 0f, alpha = 0.16f, tag = "fill")
                }
                c.layer = 4
                if (n >= 2) c.add(pts, stroke = c.color, width = Style.W_CURVE, tag = "curve")
                c.layer = 5
                pts.forEachIndexed { i, pt ->
                    c.add(c.circlePoints(pt.x, pt.y, Style.DOT * 1.05f), closed = true, fill = c.pal.paper, stroke = c.color, width = 1.3f, tag = "value:$i")
                }
            }
        }

        // Eksenler
        c.layer = 3
        c.line(L, ay, R + Style.AXIS_EXT - Style.ARROW_LEN * 0.6f, ay, width = Style.W_AXIS)
        c.arrowHead(R + Style.AXIS_EXT, ay, 1f, 0f)
        c.line(L, B, L, tipY + Style.ARROW_LEN * 0.6f, width = Style.W_AXIS)
        c.arrowHead(L, tipY, 0f, -1f)
        for (k in kBot..kTop) {
            if (k == 0 && kBot == 0) continue
            val y = fr.py(yt.value(k)).toFloat()
            c.line(L - Style.TICK, y, L, y, width = Style.W_TICK, tag = "tick:y:$k")
        }
        if (kind == GraphKind.HISTOGRAM) {
            for (i in 0..n) {
                val x = L + slot * i
                c.line(x, ay, x, ay + Style.TICK, width = Style.W_TICK, tag = "tick:x:$i")
            }
        }

        // Yazılar
        c.layer = 6
        axisNamesChart(c, L, R, ay, B + labelRow, xNameRight)
        val everyY = max(1, ceil((tsize + 0.008f) / (step * fr.sy)).toInt())
        for (k in kBot..kTop) {
            val (s, forced) = yTexts.getValue(k)
            if (s == null || (!forced && (!p.showNumbers || k % everyY != 0))) continue
            val y = fr.py(yt.value(k)).toFloat()
            val w = c.measure(s, tsize)
            val box = c.textBox(L - Style.TICK - Style.GAP * 0.6f - w, y - tsize * 0.52f, s, tsize)
            if (!forced && !c.fits(box)) continue
            c.text(box.l, box.t, s, tsize, tag = "tick:y:$k")
        }
        if (kind == GraphKind.HISTOGRAM) {
            val texts = (0..n).map { c.tickText("x:$it", Fmt.auto(it * 10.0)) }
            val maxW = texts.maxOf { (s, _) -> s?.let { c.measure(it, tsize) } ?: 0f }
            val every = max(1, ceil((maxW + 0.012f) / slot).toInt())
            for (i in 0..n) {
                val (s, forced) = texts[i]
                if (s == null || (!forced && (!p.showNumbers || i % every != 0))) continue
                val w = c.measure(s, tsize)
                c.text(L + slot * i - w / 2, B + Style.TICK + Style.GAP * 0.5f, s, tsize, tag = "tick:x:$i")
            }
        } else {
            for (i in 0 until n) {
                val s = names[i]
                if (s.isEmpty()) continue
                val w = c.measure(s, catSize)
                c.text(L + slot * (i + 0.5f) - w / 2, B + Style.GAP * 0.9f, s, catSize, tag = "vlabel:$i")
            }
        }
        if (p.showNumbers) {
            for (i in 0 until n) {
                val v = vals[i]
                val s = Fmt.auto(v)
                val w = c.measure(s, valSize)
                val cx = L + slot * (i + 0.5f)
                val yv = fr.py(v).toFloat()
                val y = when {
                    kind == GraphKind.LINE -> yv - Style.DOT * 1.1f - Style.GAP * 0.8f - valSize
                    v >= 0 -> yv - Style.GAP * 0.7f - valSize
                    else -> yv + Style.GAP * 0.7f
                }
                c.text(cx - w / 2, y, s, valSize, color = if (kind == GraphKind.LINE) c.color else null, tag = "value:$i")
            }
        }
    }

    /** Üst köşeleri yuvarlatılmış sütun (negatifte düz dikdörtgen). */
    private fun barPoints(x: Float, w: Float, base: Float, top: Float, radius: Float): List<P> {
        val h = abs(base - top)
        val r = min(radius, h * 0.5f)
        if (top > base || r < 1e-4f) return listOf(P(x, base), P(x, top), P(x + w, top), P(x + w, base))
        val pts = ArrayList<P>()
        pts += P(x, base)
        for (i in 0..5) {
            val a = PI + PI / 2 * i / 5
            pts += P(x + r + r * cos(a).toFloat(), top + r + r * sin(a).toFloat())
        }
        for (i in 0..5) {
            val a = 1.5 * PI + PI / 2 * i / 5
            pts += P(x + w - r + r * cos(a).toFloat(), top + r + r * sin(a).toFloat())
        }
        pts += P(x + w, base)
        return pts
    }

    private fun axisNamesChart(c: Canvas, L: Float, R: Float, ay: Float, belowY: Float, xRight: Boolean) {
        val p = c.p
        val s = Style.NAME_SIZE
        if (p.xLabel.isNotEmpty()) {
            val w = c.measure(p.xLabel, s)
            if (xRight) c.text(R + Style.AXIS_EXT + Style.GAP * 1.2f, ay - s * 0.55f, p.xLabel, s, tag = "axis:x")
            else c.text((L + R) / 2 - w / 2, belowY + Style.GAP * 0.3f, p.xLabel, s, tag = "axis:x")
        }
        if (p.yLabel.isNotEmpty()) {
            val w = c.measure(p.yLabel, s)
            var x = L + Style.GAP * 1.6f
            if (x + w > c.W - 0.004f) x = c.W - 0.004f - w
            c.text(x, Style.PAD - s * 0.1f, p.yLabel, s, tag = "axis:y")
        }
    }

    // ---------------------------------------------------------------- PIE

    /** En büyük kalan yöntemiyle toplamı 100 olan tamsayı yüzdeler. */
    private fun percents(vals: List<Double>): IntArray {
        val sum = vals.sum()
        if (sum <= 0) return IntArray(vals.size)
        val raw = vals.map { it / sum * 100 }
        val out = IntArray(vals.size) { floor(raw[it]).toInt() }
        var rest = 100 - out.sum()
        val order = raw.indices.sortedByDescending { raw[it] - floor(raw[it]) }
        for (i in order) { if (rest <= 0) break; if (vals[i] > 0) { out[i]++; rest-- } }
        return out
    }

    private fun pie(c: Canvas) {
        val p = c.p
        val vals = effective(p).map { if (it.isFinite() && it > 0) it.toDouble() else 0.0 }
        val n = vals.size
        if (n == 0) return
        val sum = vals.sum()
        val names = List(n) { p.valueLabels[it] ?: Cartesian.letter(it) }
        val ls = 0.042f
        val sw = ls * 0.85f
        val rowH = ls * 1.5f
        val legendW = sw + Style.GAP * 1.3f + (names.maxOfOrNull { c.measure(it, ls) } ?: 0f)
        val right = c.W >= c.H * 1.15f
        val outer = if (p.showNumbers) 0.07f else 0.02f
        val gapL = 0.05f
        val cx: Float
        val cy: Float
        val r: Float
        val legend = ArrayList<P>()
        if (right) {
            val availW = c.W - 2 * Style.PAD - legendW - gapL
            r = (min(availW, c.H - 2 * Style.PAD) / 2 - outer).coerceAtLeast(0.08f)
            val block = 2 * (r + outer) + gapL + legendW
            val x0 = (c.W - block) / 2
            cx = x0 + outer + r
            cy = c.H / 2
            val lx = x0 + 2 * (r + outer) + gapL
            val ly0 = cy - n * rowH / 2
            for (i in 0 until n) legend += P(lx, ly0 + i * rowH)
        } else {
            val colW = legendW + 0.04f
            val cols = max(1, floor((c.W - 2 * Style.PAD + 0.04f) / colW).toInt()).coerceAtMost(n)
            val rows = (n + cols - 1) / cols
            val legendH = rows * rowH
            r = (min(c.W - 2 * Style.PAD, c.H - 2 * Style.PAD - legendH - 0.04f) / 2 - outer).coerceAtLeast(0.08f)
            val block = 2 * (r + outer) + 0.04f + legendH
            val y0 = (c.H - block) / 2
            cx = c.W / 2
            cy = y0 + outer + r
            val gridW = cols * colW - 0.04f
            val lx0 = (c.W - gridW) / 2
            val ly0 = y0 + 2 * (r + outer) + 0.04f
            for (i in 0 until n) legend += P(lx0 + (i % cols) * colW, ly0 + (i / cols) * rowH)
        }

        c.layer = 1
        if (sum <= 0) {
            c.ring(cx, cy, r, stroke = c.pal.guide, width = 1f, tag = "curve")
        } else {
            var start = -90.0
            for (i in 0 until n) {
                val sweep = 360.0 * vals[i] / sum
                if (sweep <= 0) continue
                val m = max(2, (abs(sweep) / 360.0 * 2 * PI * r * c.res / 4).toInt()).coerceAtMost(240)
                val pts = ArrayList<P>(m + 2)
                if (sweep < 359.99) pts += P(cx, cy)
                for (k in 0..m) {
                    val a = Math.toRadians(start + sweep * k / m)
                    pts += P(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
                }
                c.add(pts, closed = true, fill = Style.PALETTE[i % Style.PALETTE.size], stroke = c.pal.paper, width = 1.4f, tag = "value:$i")
                start += sweep
            }
        }

        // Yüzdeler
        if (p.showNumbers && sum > 0) {
            val pc = percents(vals)
            var start = -90.0
            for (i in 0 until n) {
                val sweep = 360.0 * vals[i] / sum
                if (sweep <= 0) continue
                val mid = Math.toRadians(start + sweep / 2)
                start += sweep
                val s = "%${pc[i]}"
                val cs = cos(mid).toFloat()
                val sn = sin(mid).toFloat()
                val inside = sweep >= 26.0
                if (inside) {
                    val size = min(0.042f, r * 0.2f)
                    val w = c.measure(s, size)
                    val fill = Style.PALETTE[i % Style.PALETTE.size]
                    val col = if (Colors.luminance(fill) < 0.55f) Style.WHITE else Style.TEXT_DARK
                    c.layer = 6
                    c.text(cx + 0.63f * r * cs - w / 2, cy + 0.63f * r * sn - size * 0.52f, s, size, color = col, tag = "value:$i")
                } else {
                    val size = 0.034f
                    val w = c.measure(s, size)
                    c.layer = 3
                    c.line(cx + r * cs, cy + r * sn, cx + (r + 0.022f) * cs, cy + (r + 0.022f) * sn, width = 0.6f)
                    val ax = cx + (r + 0.03f) * cs
                    val ay = cy + (r + 0.03f) * sn
                    val bx = if (cs >= 0) ax else ax - w
                    c.layer = 6
                    c.text(bx, ay - size * 0.52f, s, size, tag = "value:$i")
                }
            }
        }

        // Açıklama
        for (i in 0 until n) {
            val at = legend[i]
            c.layer = 5
            c.add(c.roundRectPoints(at.x, at.y + (ls - sw) / 2, sw, sw, sw * 0.22f), closed = true, fill = Style.PALETTE[i % Style.PALETTE.size], width = 0f, tag = "value:$i")
            c.layer = 6
            c.text(at.x + sw + Style.GAP * 1.3f, at.y, names[i], ls, tag = "vlabel:$i")
        }
    }

    // ---------------------------------------------------------------- SCATTER

    private fun scatter(c: Canvas) {
        val p = c.p
        val v = effective(p)
        val m = v.size / 2
        val xs = DoubleArray(m) { v[2 * it].toDouble() }
        val ys = DoubleArray(m) { v[2 * it + 1].toDouble() }
        val tk = p.ticks.coerceIn(1, 12)
        fun range(a: DoubleArray): Triple<Double, Double, Ticks> {
            val f = a.filter { it.isFinite() }
            val lo0 = min(0.0, f.minOrNull() ?: 0.0)
            val hi0 = max(0.0, f.maxOrNull() ?: 1.0)
            val span = (hi0 - lo0).let { if (it <= 0) 1.0 else it }
            var st = if (p.tickStep > 0) p.tickStep.toDouble() else Fmt.niceCeil(span / tk)
            while (span / st > 60) st = Fmt.larger(st)
            var lo = floor(lo0 / st + 1e-9) * st
            var hi = ceil(hi0 / st - 1e-9) * st
            if (hi <= lo) hi = lo + st
            if (f.isNotEmpty() && hi - (f.maxOrNull() ?: 0.0) < st * 0.12) hi += st * 0.5
            if (lo < 0 && (f.minOrNull() ?: 0.0) - lo < st * 0.12) lo -= st * 0.5
            return Triple(lo, hi, Ticks(st))
        }
        val (x0, x1, xt) = range(xs)
        val (y0, y1, yt) = range(ys)
        val tsize = Style.TICK_SIZE
        var yw = 0f
        for (k in yt.indices(y0, y1)) if (k != 0) yw = max(yw, c.measure(yt.label(k), tsize))
        val L = Style.PAD + (if (p.showNumbers) yw + Style.GAP else 0f) + Style.TICK + 0.004f
        val T = Style.PAD + Style.AXIS_EXT
        val R = c.W - Style.PAD - Style.AXIS_EXT
        val B = c.H - Style.PAD - (if (p.showNumbers) tsize + Style.GAP else 0f) - Style.TICK
        val fr = Frame(x0, x1, y0, y1, L, T, R, B)
        val ax = Cartesian.axisX(fr)
        val ay = Cartesian.axisY(fr)

        if (p.grid) {
            c.layer = 0
            for (k in xt.indices(x0, x1)) {
                val x = fr.px(xt.value(k)).toFloat()
                if (abs(x - ax) > 1e-3f) c.line(x, T, x, B, stroke = c.pal.grid, width = Style.W_GRID)
            }
            for (k in yt.indices(y0, y1)) {
                val y = fr.py(yt.value(k)).toFloat()
                if (abs(y - ay) > 1e-3f) c.line(L, y, R, y, stroke = c.pal.grid, width = Style.W_GRID)
            }
        }
        c.layer = 3
        c.line(L, ay, R + Style.AXIS_EXT - Style.ARROW_LEN * 0.6f, ay, width = Style.W_AXIS)
        c.arrowHead(R + Style.AXIS_EXT, ay, 1f, 0f)
        c.line(ax, B, ax, T - Style.AXIS_EXT + Style.ARROW_LEN * 0.6f, width = Style.W_AXIS)
        c.arrowHead(ax, T - Style.AXIS_EXT, 0f, -1f)
        for (k in xt.indices(x0, x1)) {
            if (k == 0 && fr.hasX0) continue
            val x = fr.px(xt.value(k)).toFloat()
            c.line(x, ay - Style.TICK, x, ay + Style.TICK, width = Style.W_TICK, tag = "tick:x:$k")
        }
        for (k in yt.indices(y0, y1)) {
            if (k == 0 && fr.hasY0) continue
            val y = fr.py(yt.value(k)).toFloat()
            c.line(ax - Style.TICK, y, ax + Style.TICK, y, width = Style.W_TICK, tag = "tick:y:$k")
        }

        c.layer = 5
        for (i in 0 until m) {
            if (!xs[i].isFinite() || !ys[i].isFinite()) continue
            c.dot(fr.px(xs[i]).toFloat(), fr.py(ys[i]).toFloat(), Style.DOT * 1.15f, tag = "value:$i")
        }

        c.layer = 6
        val s = Style.NAME_SIZE
        if (p.xLabel.isNotEmpty()) c.text(R + Style.AXIS_EXT - c.measure(p.xLabel, s), ay - Style.GAP * 1.2f - s, p.xLabel, s, tag = "axis:x")
        if (p.yLabel.isNotEmpty()) c.text(ax + Style.GAP * 1.6f, T - Style.AXIS_EXT - s * 0.1f, p.yLabel, s, tag = "axis:y")
        val maxW = xt.indices(x0, x1).maxOfOrNull { c.measure(xt.label(it), tsize) } ?: 0f
        val everyX = max(1, ceil((maxW + 0.014f) / (xt.step * fr.sx)).toInt())
        for (k in xt.indices(x0, x1)) {
            if (k == 0) continue
            val (t, forced) = c.tickText("x:$k", xt.label(k))
            if (t == null || (!forced && (!p.showNumbers || k % everyX != 0))) continue
            val w = c.measure(t, tsize)
            val box = c.textBox(fr.px(xt.value(k)).toFloat() - w / 2, ay + Style.TICK + Style.GAP * 0.5f, t, tsize)
            if (!forced && !c.fits(box)) continue
            c.text(box.l, box.t, t, tsize, tag = "tick:x:$k")
        }
        val everyY = max(1, ceil((tsize + 0.008f) / (yt.step * fr.sy)).toInt())
        for (k in yt.indices(y0, y1)) {
            if (k == 0) continue
            val (t, forced) = c.tickText("y:$k", yt.label(k))
            if (t == null || (!forced && (!p.showNumbers || k % everyY != 0))) continue
            val w = c.measure(t, tsize)
            val box = c.textBox(ax - Style.TICK - Style.GAP * 0.6f - w, fr.py(yt.value(k)).toFloat() - tsize * 0.52f, t, tsize)
            if (!forced && !c.fits(box)) continue
            c.text(box.l, box.t, t, tsize, tag = "tick:y:$k")
        }
        if (fr.hasX0 && fr.hasY0) {
            val (t, forced) = c.tickText("x:0", "0")
            if (t != null && (p.showNumbers || forced)) c.text(ax - Style.GAP * 0.8f - c.measure(t, tsize), ay + Style.GAP * 0.5f, t, tsize, tag = "tick:x:0")
        }
        for (i in 0 until m) {
            val name = p.valueLabels[i] ?: continue
            if (name.isEmpty() || !xs[i].isFinite() || !ys[i].isFinite()) continue
            val X = fr.px(xs[i]).toFloat()
            val Y = fr.py(ys[i]).toFloat()
            c.text(X + Style.DOT * 1.4f, Y - Style.DOT * 1.4f - Style.KEY_SIZE, name, Style.KEY_SIZE, tag = "vlabel:$i")
        }
    }
}
