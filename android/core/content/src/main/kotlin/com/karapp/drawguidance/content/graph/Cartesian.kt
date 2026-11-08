package com.karapp.drawguidance.content.graph

import com.karapp.drawguidance.content.vector.LineStyle
import com.karapp.drawguidance.content.vector.P
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Veri koordinatlarını (x sağa, y yukarı) fiziksel çizim alanına eşler. */
internal class Frame(
    val x0: Double, val x1: Double, val y0: Double, val y1: Double,
    val L: Float, val T: Float, val R: Float, val B: Float,
) {
    val sx: Double = (R - L) / (x1 - x0)
    val sy: Double = (B - T) / (y1 - y0)
    fun px(x: Double): Double = L + (x - x0) * sx
    fun py(y: Double): Double = B - (y - y0) * sy
    val hasX0 get() = x0 <= 0.0 && x1 >= 0.0
    val hasY0 get() = y0 <= 0.0 && y1 >= 0.0
}

/** Eksen çentikleri: k·step; π kesirli ise etiket "π/2", "3π/2"… */
internal class Ticks(val step: Double, val piN: Int = 0, val piD: Int = 0) {
    val isPi: Boolean get() = piD > 0
    fun value(k: Int) = k * step
    fun label(k: Int): String = if (isPi) Fmt.piLabel(k * piN, piD) else Fmt.auto(k * step, Fmt.decimals(step))
    fun indices(lo: Double, hi: Double): IntRange {
        val a = ceil(lo / step - 1e-7)
        val b = floor(hi / step + 1e-7)
        if (!a.isFinite() || !b.isFinite() || b - a > 400) return IntRange.EMPTY
        return a.toInt()..b.toInt()
    }

    companion object {
        fun pi(n: Int, d: Int) = Ticks(PI * n / d, n, d)

        /** Kullanıcı adımı; [allowPi] ise π'nin basit kesri π ile yazılır. */
        fun user(step: Double, allowPi: Boolean): Ticks {
            if (allowPi) Fmt.piFraction(step)?.let { (n, d) -> if (n in 1..12 && d <= 12) return pi(n, d) }
            return Ticks(step)
        }

        /** Periyodik eğri: yarım eksende en fazla [ticks] çentik olacak en küçük π adımı. */
        fun piFor(half: Double, ticks: Int): Ticks {
            val cands = listOf(1 to 4, 1 to 2, 1 to 1, 2 to 1, 4 to 1, 8 to 1)
            for ((n, d) in cands) if (half / (PI * n / d) <= ticks + 0.01) return pi(n, d)
            return pi(16, 1)
        }
    }
}

/** Çizilecek eğrinin kaynağı: ön ayar ya da formül; açık (y = f(x)) ya da kapalı (F(x, y) = 0). */
internal class CurveSource(
    val text: String,
    val explicit: Expression.Expr?,
    val implicit: Expression.Expr?,
    val periodic: Boolean,
    val period: Double,
    val hint: PresetHint,
) {
    fun f(x: Double): Double = explicit?.eval(x, 0.0) ?: Double.NaN

    companion object {
        fun resolve(p: GraphParams): CurveSource {
            val preset = if (p.kind == GraphKind.FUNCTION || p.kind == GraphKind.AXES || p.kind == GraphKind.COORD_PLANE)
                CurvePresets.find(p.preset) else null
            // v45: ';' ile birden çok eğri; plan ve önemli noktalar ilk eğriden (ötekiler [Cartesian.extraCurves])
            val text = preset?.formula ?: GraphFormula.parts(p.formula).firstOrNull().orEmpty()
            val mode = if (p.angleDeg) com.karapp.drawguidance.content.math.AngleMode.DEG else com.karapp.drawguidance.content.math.AngleMode.RAD
            val hint = preset?.let { CurvePresets.hint(it.id) } ?: PresetHint(unit = 2.0)
            if (text.isBlank()) return CurveSource("", null, null, false, 0.0, hint)
            // v18 desen (FUNCTION): y = f(k·x); eksen aralığı k = 1'deki gibi kalır, eğri sıklaşır
            val k = if (p.kind == GraphKind.FUNCTION) p.freq.coerceIn(1, Graphs.MAX_FREQ).toDouble() else 1.0
            fun scaled(e: Expression.Expr?): Expression.Expr? =
                if (e == null || k == 1.0) e else Expression.Expr { x, y -> e.eval(k * x, y) }
            if (Expression.isImplicit(text)) {
                return CurveSource(text, null, scaled(Expression.parseImplicit(text, mode)), false, 0.0, hint.copy(equal = true))
            }
            val raw = Expression.parse(text, mode)
            var periodic = preset?.periodic ?: false
            // Periyot (ve buna bağlı x aralığı) k = 1'deki eğriden: desen artınca eksenler değişmez
            var period = preset?.period ?: 0.0
            if (preset == null && raw != null) detectPeriod(text, raw)?.let { periodic = true; period = it }
            return CurveSource(text, scaled(raw), null, periodic, period, hint)
        }

        /** Trigonometrik formüllerde π, 2π ya da 4π periyodu (ör. "3sin(2x)" → π). */
        private fun detectPeriod(text: String, e: Expression.Expr): Double? {
            val t = text.lowercase()
            if (listOf("sin", "cos", "tan", "tg", "cot", "sec", "csc").none { t.contains(it) }) return null
            val probes = doubleArrayOf(-2.7, -1.9, -1.1, -0.35, 0.2, 0.9, 1.6, 2.45, 3.1)
            val vs = probes.map { e.eval(it, 0.0) }.filter { it.isFinite() }
            if (vs.size < 4 || vs.max() - vs.min() < 1e-9) return null
            for (period in doubleArrayOf(PI, 2 * PI, 4 * PI)) {
                var ok = 0
                var bad = false
                for (x in probes) {
                    val a = e.eval(x, 0.0)
                    val b = e.eval(x + period, 0.0)
                    if (!a.isFinite() || !b.isFinite()) continue
                    if (abs(a - b) > 1e-6 * (1 + abs(a))) { bad = true; break }
                    ok++
                }
                if (!bad && ok >= 4) return period
            }
            return null
        }
    }
}

/** AXES, COORD_PLANE ve FUNCTION: eksenler, ızgara, çentikler, eğri, önemli noktalar, formül kutusu. */
internal object Cartesian {

    private class Plan(
        val fr: Frame, val xt: Ticks, val yt: Ticks,
        val domA: Double, val domB: Double,
        val minorX: Int, val minorY: Int,
    )

    private class DP(val x: Double, val y: Double)
    private class Jump(val x: Double, val left: Double, val right: Double)
    private class Sampled(
        val pieces: List<List<DP>>, val xs: DoubleArray, val ys: DoubleArray, val cont: BooleanArray,
        val jumps: List<Jump>, val edges: List<DP>,
    )

    private enum class Grow { CENTER, HIGH, LOW }

    fun build(c: Canvas) {
        val src = CurveSource.resolve(c.p)
        val plan = if (c.p.kind == GraphKind.FUNCTION) planFunction(c, src) else planPlane(c, src)
        render(c, src, plan)
    }

    private fun finiteRange(a: Float, b: Float) = a.isFinite() && b.isFinite() && b > a

    private fun grow(a: Double, b: Double, span: Double, g: Grow): Pair<Double, Double> {
        val extra = span - (b - a)
        if (extra <= 0) return Pair(a, b)
        return when (g) {
            Grow.CENTER -> Pair(a - extra / 2, b + extra / 2)
            Grow.HIGH -> Pair(a - extra * 0.08, b + extra * 0.92)
            Grow.LOW -> Pair(a - extra * 0.92, b + extra * 0.08)
        }
    }

    fun axisX(fr: Frame): Float = if (fr.hasX0) fr.px(0.0).toFloat() else fr.L
    fun axisY(fr: Frame): Float = if (fr.hasY0) fr.py(0.0).toFloat() else fr.B

    /** Çentik yazılarının sığması için sol / alt kenar payı eksikleri. */
    private fun deficits(c: Canvas, fr: Frame, yt: Ticks): Pair<Float, Float> {
        if (!c.p.showNumbers) return Pair(0f, 0f)
        val ax = axisX(fr)
        val ay = axisY(fr)
        var yw = 0f
        for (k in yt.indices(fr.y0, fr.y1)) if (k != 0) yw = max(yw, c.measure(yt.label(k), Style.TICK_SIZE))
        val dL = Style.PAD * 0.6f + yw + Style.GAP + Style.TICK - ax
        val dB = ay + Style.TICK + Style.GAP + Style.TICK_SIZE + Style.PAD * 0.5f - c.H
        return Pair(dL, dB)
    }

    private fun minorFor(spacing: Double, preferred: Int, minGap: Double): Int {
        var m = preferred.coerceAtLeast(1)
        while (m > 1 && spacing / m < minGap) m = if (m % 2 == 0 && m > 2) m / 2 else m - 1
        return m
    }

    // ---------------------------------------------------------------- düzlem (AXES, COORD_PLANE)

    private fun planPlane(c: Canvas, src: CurveSource): Plan {
        val p = c.p
        val n = p.ticks.coerceIn(1, 12)
        val hasX = finiteRange(p.xMin, p.xMax)
        val hasY = finiteRange(p.yMin, p.yMax)
        val user = p.tickStep.toDouble()
        val ext = max(
            if (hasX) max(abs(p.xMin), abs(p.xMax)).toDouble() else 0.0,
            if (hasY) max(abs(p.yMin), abs(p.yMax)).toDouble() else 0.0,
        )
        val t = when {
            user > 0 -> Ticks.user(user, true)
            hasX || hasY -> Ticks(Fmt.niceCeil(ext / n))
            else -> Ticks(1.0)
        }
        val m = Style.PAD + Style.AXIS_EXT
        var mL = m
        var mB = m
        val mR = m
        val mT = m
        var fr = Frame(-1.0, 1.0, -1.0, 1.0, mL, mT, c.W - mR, c.H - mB)
        for (iter in 0 until 4) {
            val pw = (c.W - mL - mR).toDouble()
            val ph = (c.H - mT - mB).toDouble()
            var xa: Double; var xb: Double; var ya: Double; var yb: Double
            if (!hasX && !hasY) {
                val half = (n + 0.5) * t.step
                xa = -half; xb = half; ya = -half; yb = half
            } else {
                xa = if (hasX) p.xMin.toDouble() else -1e-9
                xb = if (hasX) p.xMax.toDouble() else 1e-9
                ya = if (hasY) p.yMin.toDouble() else -1e-9
                yb = if (hasY) p.yMax.toDouble() else 1e-9
            }
            val s = min(pw / (xb - xa), ph / (yb - ya))
            grow(xa, xb, pw / s, Grow.CENTER).let { xa = it.first; xb = it.second }
            grow(ya, yb, ph / s, Grow.CENTER).let { ya = it.first; yb = it.second }
            fr = Frame(xa, xb, ya, yb, mL, mT, c.W - mR, c.H - mB)
            val (dL, dB) = deficits(c, fr, t)
            if (dL <= 0.001f && dB <= 0.001f) break
            mL += max(0f, dL)
            mB += max(0f, dB)
        }
        val minGap = if (p.kind == GraphKind.AXES) 0.012 else 0.016
        val minor = minorFor(t.step * fr.sx, p.repeat.coerceIn(1, 10), minGap)
        return Plan(fr, t, t, fr.x0, fr.x1, minor, minor)
    }

    // ---------------------------------------------------------------- FUNCTION

    private fun planFunction(c: Canvas, src: CurveSource): Plan {
        val p = c.p
        val hint = src.hint
        val rep = p.repeat.coerceIn(1, 24)
        val n = p.ticks.coerceIn(1, 12)
        val hasX = finiteRange(p.xMin, p.xMax)
        val hasY = finiteRange(p.yMin, p.yMax)
        val user = p.tickStep.toDouble()

        val a: Double
        val b: Double
        if (hasX) {
            a = p.xMin.toDouble(); b = p.xMax.toDouble()
        } else if (src.periodic && src.period > 0) {
            val per = src.period
            when {
                hint.start == XStart.ZERO -> { a = 0.0; b = rep * per }
                hint.start == XStart.ONE_PERIOD && rep == 1 -> { a = 0.0; b = per }
                else -> { a = -rep * per / 2; b = rep * per / 2 }
            }
        } else {
            val u = hint.unit
            if (hint.start == XStart.FROM_ZERO) { a = hint.center - 0.15 * rep * u; b = hint.center + rep * u }
            else { a = hint.center - rep * u; b = hint.center + rep * u }
        }
        val halfX = max(abs(a), abs(b)).coerceAtLeast(1e-9)
        val xt = when {
            user > 0 -> Ticks.user(user, src.periodic)
            src.periodic -> Ticks.piFor(halfX, n)
            else -> Ticks(Fmt.niceCeil(halfX / n))
        }
        val padX = when {
            hasX -> 0.0
            src.periodic -> max(0.3 * xt.step, 0.03 * (b - a))
            else -> 0.05 * (b - a)
        }
        val xv0 = a - padX
        val xv1 = b + padX
        val yr = if (hasY) {
            doubleArrayOf(p.yMin.toDouble(), p.yMax.toDouble(), p.yMin.toDouble(), p.yMax.toDouble())
        } else {
            yRange(src, if (src.periodic) a else xv0, if (src.periodic) b else xv1, hint, xv0, xv1)
        }
        val coreLo = yr[2]
        val coreHi = yr[3]
        var mL = Style.PAD
        var mB = Style.PAD
        val mT = Style.PAD + Style.AXIS_EXT
        val mR = Style.PAD + Style.AXIS_EXT
        var equal = false
        var decided = false
        var fr = Frame(xv0, xv1, yr[0], yr[1], mL, mT, c.W - mR, c.H - mB)
        var yt = Ticks(1.0)
        for (iter in 0 until 5) {
            val pw = (c.W - mL - mR).toDouble()
            val ph = (c.H - mT - mB).toDouble()
            var x0 = xv0; var x1 = xv1; var y0 = yr[0]; var y1 = yr[1]
            if (!decided) {
                val sx = pw / (x1 - x0)
                val sy = ph / (y1 - y0)
                equal = src.implicit != null || hint.equal || (!(hasX && hasY) && min(sx, sy) / max(sx, sy) >= 0.55)
                decided = true
            }
            if (equal) {
                val s = min(pw / (x1 - x0), ph / (y1 - y0))
                val gx = if (hasX) Grow.CENTER else if (hint.start == XStart.FROM_ZERO || hint.start == XStart.ZERO) Grow.HIGH else Grow.CENTER
                val cs = coreHi - coreLo
                val gy = when {
                    hasY -> Grow.CENTER
                    coreLo >= -0.05 * cs && coreHi > 0 -> Grow.HIGH
                    coreHi <= 0.05 * cs && coreLo < 0 -> Grow.LOW
                    else -> Grow.CENTER
                }
                grow(x0, x1, pw / s, gx).let { x0 = it.first; x1 = it.second }
                grow(y0, y1, ph / s, gy).let { y0 = it.first; y1 = it.second }
            }
            fr = Frame(x0, x1, y0, y1, mL, mT, c.W - mR, c.H - mB)
            yt = yTicks(fr, xt, coreLo, coreHi, user, equal, src.periodic, n)
            val (dL, dB) = deficits(c, fr, yt)
            if (dL <= 0.001f && dB <= 0.001f) break
            mL += max(0f, dL)
            mB += max(0f, dB)
        }
        val periodicDomain = src.periodic && !hasX
        val domA = if (periodicDomain) a else fr.x0
        val domB = if (periodicDomain) b else fr.x1
        val minorX = minorFor(xt.step * fr.sx, 2, 0.028)
        val minorY = minorFor(yt.step * fr.sy, 2, 0.028)
        return Plan(fr, xt, yt, domA, domB, minorX, minorY)
    }

    /** Sağlam y aralığı: [görünüm alt, üst, çekirdek alt, üst]. */
    private fun yRange(src: CurveSource, a: Double, b: Double, hint: PresetHint, xv0: Double, xv1: Double): DoubleArray {
        if (src.implicit != null) {
            val h = max(abs(xv0), abs(xv1)) * 0.75
            return doubleArrayOf(-h, h, -h * 0.8, h * 0.8)
        }
        if (!hint.yLimit.isNaN()) {
            val l = hint.yLimit
            return doubleArrayOf(-l, l, -l * 0.8, l * 0.8)
        }
        if (src.explicit == null) {
            val h = max(abs(xv0), abs(xv1))
            return doubleArrayOf(-h, h, -h, h)
        }
        val n = 700
        val vals = ArrayList<Double>(n + 1)
        for (i in 0..n) {
            val y = src.f(a + (b - a) * i / n)
            if (y.isFinite()) vals += y
        }
        if (vals.isEmpty()) return doubleArrayOf(-1.2, 1.2, -1.0, 1.0)
        vals.sort()
        fun q(t: Double) = vals[((vals.size - 1) * t).roundToInt()]
        var lo = q(0.05)
        var hi = q(0.95)
        var span = max(hi - lo, 1e-12)
        if (vals.first() >= lo - 0.6 * span) lo = vals.first()
        if (vals.last() <= hi + 0.6 * span) hi = vals.last()
        span = hi - lo
        // Önemli noktalar (kökler, yerel uç değerler, y kesişimi) aralığın küçük bir kısmındaysa onlara odaklan
        run {
            val ks = ArrayList<Double>()
            var turning = false
            var prevY = Double.NaN
            var prevD = Double.NaN
            for (i in 0..n) {
                val y = src.f(a + (b - a) * i / n)
                if (!y.isFinite()) { prevY = Double.NaN; prevD = Double.NaN; continue }
                if (prevY.isFinite()) {
                    if (prevY * y <= 0 && abs(y - prevY) < 0.5 * span) ks += 0.0
                    val d = y - prevY
                    if (prevD.isFinite() && prevD * d < 0 && abs(d) < 0.5 * span) { ks += prevY; turning = true }
                    prevD = d
                }
                prevY = y
            }
            if (a < 0 && b > 0) src.f(0.0).let { if (it.isFinite()) ks += it }
            if (turning && ks.size >= 2) {
                val kLo = ks.min()
                val kHi = ks.max()
                val ksp = kHi - kLo
                if (ksp > 1e-9 && span > 6.5 * ksp) {
                    lo = max(lo, kLo - 1.6 * ksp)
                    hi = min(hi, kHi + 1.6 * ksp)
                }
            }
        }
        span = hi - lo
        if (a < 0 && b > 0) {
            val y0 = src.f(0.0)
            if (y0.isFinite() && y0 >= lo - span && y0 <= hi + span) { lo = min(lo, y0); hi = max(hi, y0) }
        }
        span = hi - lo
        if (span < 1e-9 * (1 + abs(hi))) {
            val v = hi
            lo = min(0.0, v - 1)
            hi = max(0.0, v + 1)
            if (v > 0) lo = min(lo, -0.2 * v) else if (v < 0) hi = max(hi, -0.2 * v)
        }
        span = hi - lo
        if (lo > 0 && lo <= 0.6 * span) lo = 0.0
        if (hi < 0 && -hi <= 0.6 * span) hi = 0.0
        span = hi - lo
        return doubleArrayOf(lo - 0.1 * span, hi + 0.1 * span, lo, hi)
    }

    private fun yTicks(fr: Frame, xt: Ticks, coreLo: Double, coreHi: Double, user: Double, equal: Boolean, periodic: Boolean, ticks: Int): Ticks {
        if (user > 0 && !periodic) return Ticks(user)
        var st = if (equal && !xt.isPi) xt.step else Fmt.niceRound(xt.step * fr.sx / fr.sy)
        if (!equal) {
            val ext = max(abs(coreLo), abs(coreHi))
            var guard = 0
            while (st > ext && ext > 0 && guard++ < 40) st = Fmt.smaller(st)
        }
        var guard = 0
        if (!(equal && !xt.isPi)) {
            val core = max(abs(coreLo), abs(coreHi))
            val limit = if (xt.isPi) max(1.0, ticks - 1.0) else ticks + 0.5
            while (core / st > limit && guard++ < 40) st = Fmt.larger(st)
        }
        val half = max(abs(fr.y0), abs(fr.y1))
        guard = 0
        while (half / st > 14 && guard++ < 40) st = Fmt.larger(st)
        return Ticks(st)
    }

    // ---------------------------------------------------------------- örnekleme

    private fun sample(f: (Double) -> Double, a: Double, b: Double, fr: Frame, px: Float): Sampled {
        val widthPx = abs((b - a) * fr.sx) / px
        val n = (widthPx / 2.5).toInt().coerceIn(120, 2400)
        val xs = DoubleArray(n + 1) { a + (b - a) * it / n }
        val ys = DoubleArray(n + 1) { f(xs[it]) }
        val cont = BooleanArray(n) { true }
        val tol = 2.5 * px
        val top = fr.y1
        val bot = fr.y0
        val pieces = ArrayList<ArrayList<DP>>()
        val jumps = ArrayList<Jump>()
        val edges = ArrayList<DP>()
        var cur: ArrayList<DP>? = null
        var broken = false
        fun emit(x: Double, y: Double) {
            val cc = cur ?: ArrayList<DP>().also { cur = it; pieces += it }
            cc += DP(x, y)
        }
        fun seg(x0: Double, y0: Double, x1: Double, y1: Double, depth: Int) {
            val f0 = y0.isFinite()
            val f1 = y1.isFinite()
            if (!f0 && !f1) { cur = null; broken = true; return }
            if (f0 != f1) {
                broken = true
                if (depth >= 46 || x1 - x0 <= 1e-13 * (1 + abs(x0))) {
                    if (f1) { cur = null; emit(x1, y1); edges += DP(x1, y1) } else { edges += DP(x0, y0); cur = null }
                    return
                }
                val xm = 0.5 * (x0 + x1)
                val ym = f(xm)
                seg(x0, y0, xm, ym, depth + 1)
                seg(xm, ym, x1, y1, depth + 1)
                return
            }
            val dyp = abs(y1 - y0) * fr.sy
            if (dyp <= tol) { emit(x1, y1); return }
            if ((y0 > top && y1 > top) || (y0 < bot && y1 < bot)) { emit(x1, y1); return }
            if (depth >= 22) {
                jumps += Jump(0.5 * (x0 + x1), y0, y1)
                broken = true
                cur = null
                emit(x1, y1)
                return
            }
            val xm = 0.5 * (x0 + x1)
            val ym = f(xm)
            seg(x0, y0, xm, ym, depth + 1)
            seg(xm, ym, x1, y1, depth + 1)
        }
        if (ys[0].isFinite()) { emit(xs[0], ys[0]); edges += DP(xs[0], ys[0]) }
        for (i in 0 until n) {
            broken = false
            seg(xs[i], ys[i], xs[i + 1], ys[i + 1], 0)
            cont[i] = !broken
        }
        if (ys[n].isFinite()) edges += DP(xs[n], ys[n])
        return Sampled(pieces.filter { it.size >= 2 }, xs, ys, cont, jumps, edges)
    }

    /** Liang–Barsky ile çoklu çizgiyi dikdörtgene kırpar (dışarı çıkan yerlerde böler). */
    private fun clip(pts: List<DP>, L: Float, T: Float, R: Float, B: Float): List<List<P>> {
        val out = ArrayList<ArrayList<P>>()
        var cur: ArrayList<P>? = null
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            var t0 = 0.0
            var t1 = 1.0
            var ok = true
            fun edge(pp: Double, q: Double) {
                if (!ok) return
                if (pp == 0.0) { if (q < 0) ok = false; return }
                val r = q / pp
                if (pp < 0) { if (r > t1) ok = false else if (r > t0) t0 = r } else { if (r < t0) ok = false else if (r < t1) t1 = r }
            }
            edge(-dx, a.x - L); edge(dx, R - a.x); edge(-dy, a.y - T); edge(dy, B - a.y)
            if (!ok) { cur = null; continue }
            val s = P((a.x + t0 * dx).toFloat(), (a.y + t0 * dy).toFloat())
            val e = P((a.x + t1 * dx).toFloat(), (a.y + t1 * dy).toFloat())
            var cc = cur
            if (cc == null || t0 > 1e-9) { cc = arrayListOf(s); out += cc }
            cc.add(e)
            cur = if (t1 < 1 - 1e-9) null else cc
        }
        return out.filter { it.size >= 2 }
    }

    private fun segDist(p: P, a: P, b: P): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l2 = dx * dx + dy * dy
        if (l2 < 1e-12f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** Douglas–Peucker sadeleştirme. */
    fun simplify(pts: List<P>, tol: Float): List<P> {
        if (pts.size <= 2) return pts
        val keep = BooleanArray(pts.size)
        keep[0] = true
        keep[pts.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to pts.size - 1)
        while (stack.isNotEmpty()) {
            val (i, j) = stack.removeLast()
            var maxD = -1f
            var idx = -1
            for (k in i + 1 until j) {
                val d = segDist(pts[k], pts[i], pts[j])
                if (d > maxD) { maxD = d; idx = k }
            }
            if (idx >= 0 && maxD > tol) { keep[idx] = true; stack.add(i to idx); stack.add(idx to j) }
        }
        return pts.filterIndexed { k, _ -> keep[k] }
    }

    // ---------------------------------------------------------------- kapalı eğri (marching squares)

    private fun contour(fn: Expression.Expr, fr: Frame, c: Canvas): List<List<DP>> {
        val nx = ((fr.R - fr.L) / c.px / 3.5f).toInt().coerceIn(40, 260)
        val ny = ((fr.B - fr.T) / c.px / 3.5f).toInt().coerceIn(40, 260)
        val xs = DoubleArray(nx + 1) { fr.x0 + (fr.x1 - fr.x0) * it / nx }
        val ys = DoubleArray(ny + 1) { fr.y0 + (fr.y1 - fr.y0) * it / ny }
        val v = Array(ny + 1) { j -> DoubleArray(nx + 1) { i -> fn.eval(xs[i], ys[j]) } }
        val stride = nx + 1
        val vBase = (ny + 1) * stride
        val cache = HashMap<Int, DP?>()
        fun point(key: Int): DP? = cache.getOrPut(key) {
            val i: Int; val j: Int; val i2: Int; val j2: Int
            if (key < vBase) { j = key / stride; i = key % stride; i2 = i + 1; j2 = j } else {
                val k = key - vBase; j = k / stride; i = k % stride; i2 = i; j2 = j + 1
            }
            val va = v[j][i]
            val vb = v[j2][i2]
            val t = (va / (va - vb)).coerceIn(0.0, 1.0)
            val x = xs[i] + (xs[i2] - xs[i]) * t
            val y = ys[j] + (ys[j2] - ys[j]) * t
            val fv = fn.eval(x, y)
            if (!fv.isFinite() || abs(fv) > 0.5 * max(abs(va), abs(vb)) + 1e-12) null else DP(x, y)
        }
        val segs = ArrayList<IntArray>()
        fun seg(ka: Int, kb: Int) { if (point(ka) != null && point(kb) != null) segs += intArrayOf(ka, kb) }
        for (j in 0 until ny) for (i in 0 until nx) {
            val a = v[j][i]; val b = v[j][i + 1]; val cc = v[j + 1][i + 1]; val d = v[j + 1][i]
            if (!a.isFinite() || !b.isFinite() || !cc.isFinite() || !d.isFinite()) continue
            val idx = (if (a > 0) 1 else 0) or (if (b > 0) 2 else 0) or (if (cc > 0) 4 else 0) or (if (d > 0) 8 else 0)
            if (idx == 0 || idx == 15) continue
            val e0 = j * stride + i
            val e1 = vBase + j * stride + i + 1
            val e2 = (j + 1) * stride + i
            val e3 = vBase + j * stride + i
            when (idx) {
                1, 14 -> seg(e3, e0)
                2, 13 -> seg(e0, e1)
                3, 12 -> seg(e3, e1)
                4, 11 -> seg(e1, e2)
                6, 9 -> seg(e0, e2)
                7, 8 -> seg(e2, e3)
                5, 10 -> {
                    val center = fn.eval((xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2)
                    val joined = center > 0
                    if ((idx == 5) == joined) { seg(e0, e1); seg(e2, e3) } else { seg(e3, e0); seg(e1, e2) }
                }
            }
        }
        val adj = HashMap<Int, MutableList<Int>>()
        segs.forEachIndexed { s, e -> adj.getOrPut(e[0]) { ArrayList(2) }.add(s); adj.getOrPut(e[1]) { ArrayList(2) }.add(s) }
        val used = BooleanArray(segs.size)
        fun walk(startSeg: Int, startKey: Int): List<DP> {
            val keys = arrayListOf(startKey)
            var s = startSeg
            var k = startKey
            while (true) {
                used[s] = true
                val e = segs[s]
                val nk = if (e[0] == k) e[1] else e[0]
                keys += nk
                k = nk
                s = adj[k]?.firstOrNull { !used[it] } ?: break
            }
            return keys.mapNotNull { point(it) }
        }
        val chains = ArrayList<List<DP>>()
        for ((k, list) in adj) if (list.size == 1 && !used[list[0]]) chains += walk(list[0], k)
        for (s in segs.indices) if (!used[s]) chains += walk(s, segs[s][0])
        return chains.filter { it.size >= 2 }
    }

    // ---------------------------------------------------------------- çizim

    private class KeyPt(val x: Double, val y: Double)

    private fun render(c: Canvas, src: CurveSource, plan: Plan) {
        val p = c.p
        val fr = plan.fr
        val ax = axisX(fr)
        val ay = axisY(fr)

        if (p.grid && p.kind != GraphKind.AXES) grid(c, plan)

        // Eğri geometrisi
        val curve = ArrayList<List<P>>()
        var sampled: Sampled? = null
        var closedLoops: List<List<P>> = emptyList()
        if (src.explicit != null) {
            val s = sample(src::f, plan.domA, plan.domB, fr, c.px)
            sampled = s
            for (piece in s.pieces) {
                val phys = piece.map { DP(fr.px(it.x), fr.py(it.y)) }
                clip(phys, fr.L, fr.T, fr.R, fr.B).forEach { curve += simplify(it, c.px * 0.35f) }
            }
        } else if (src.implicit != null) {
            val loops = ArrayList<List<P>>()
            for (chain in contour(src.implicit, fr, c)) {
                val phys = chain.map { DP(fr.px(it.x), fr.py(it.y)) }
                val parts = clip(phys, fr.L, fr.T, fr.R, fr.B).map { simplify(it, c.px * 0.35f) }
                curve += parts
                val first = chain.first()
                val last = chain.last()
                if (parts.size == 1 && abs(first.x - last.x) < 1e-9 && abs(first.y - last.y) < 1e-9) loops += parts[0]
            }
            closedLoops = loops
        }

        // Alan dolgusu
        if (p.fillUnder) {
            c.layer = 1
            val base = fr.py(0.0).toFloat().coerceIn(fr.T, fr.B)
            sampled?.pieces?.forEach { piece ->
                val pts = simplify(piece.map {
                    P(fr.px(it.x).toFloat().coerceIn(fr.L, fr.R), fr.py(it.y).coerceIn(fr.T.toDouble(), fr.B.toDouble()).toFloat())
                }, c.px * 0.5f)
                if (pts.size >= 2) {
                    c.add(listOf(P(pts.first().x, base)) + pts + P(pts.last().x, base), closed = true, fill = c.color, width = 0f, alpha = 0.16f, tag = "fill")
                }
            }
            closedLoops.forEach { c.add(it, closed = true, fill = c.color, width = 0f, alpha = 0.16f, tag = "fill") }
        }

        // Asimptotlar ve sıçramalar
        val finiteJumps = if (sampled != null) asymptotes(c, fr, src, sampled) else emptyList()

        axes(c, plan, ax, ay)

        // Eğri
        c.layer = 4
        curve.forEach { c.add(it, stroke = c.color, width = Style.W_CURVE, tag = "curve") }
        // v45: formüldeki öteki eğriler (aynı eksenlerde, ayrı renklerle)
        extraCurves(c, plan)

        // Süsleme (çember: merkez ve yarıçap)
        decorate(c, fr, src)

        // Önemli noktalar
        val keys = when {
            !p.keyPoints -> emptyList()
            sampled != null -> keyPointsExplicit(src, sampled, fr, finiteJumps)
            src.implicit != null -> keyPointsImplicit(src.implicit, fr)
            else -> emptyList()
        }
        c.layer = 5
        if (p.keyPoints) {
            for (j in finiteJumps) {
                val xs = round(j.x * 1e6) / 1e6
                val fv = src.f(xs)
                val X = fr.px(xs).toFloat()
                val closedLeft = fv.isFinite() && abs(fv - j.left) < abs(fv - j.right)
                val closedRight = fv.isFinite() && !closedLeft
                val yl = fr.py(j.left).toFloat()
                val yr = fr.py(j.right).toFloat()
                if (X < fr.L || X > fr.R) continue
                if (yl in fr.T..fr.B) { if (closedLeft) c.dot(X, yl, tag = "key") else c.openDot(X, yl, tag = "key") }
                if (yr in fr.T..fr.B) { if (closedRight) c.dot(X, yr, tag = "key") else c.openDot(X, yr, tag = "key") }
            }
        }
        keys.forEachIndexed { i, k -> c.dot(fr.px(k.x).toFloat(), fr.py(k.y).toFloat(), tag = "key:$i") }

        // Yazılar: eksen adları → çentikler → noktalar → formül kutusu → önemli nokta koordinatları
        c.layer = 6
        axisNames(c, fr, ax, ay)
        tickLabels(c, plan, ax, ay)
        valuePoints(c, fr, ax, ay, curve)
        val formula = p.tickLabels["formula"] ?: if (src.text.isNotBlank()) Graphs.formulaLabel(p) else ""
        c.defaults["formula"] = if (src.text.isNotBlank()) Graphs.formulaLabel(p) else ""
        if (formula.isNotBlank()) formulaBox(c, fr, formula, curve, ax, ay)
        if (p.showNumbers && keys.size in 1..5) keyLabels(c, fr, keys, src.periodic, curve)
    }

    /** v45: ikinci ve sonraki eğrilerin renkleri. */
    internal val EXTRA_COLORS = listOf("#E53935", "#43A047", "#8E24AA", "#FB8C00")

    /** v45: ';' ile yazılan ek eğriler (en çok [GraphFormula.MAX_CURVES]); kesiklik ve asimptotlarda bölünür. */
    private fun extraCurves(c: Canvas, plan: Plan) {
        val p = c.p
        if (CurvePresets.find(p.preset) != null && (p.kind == GraphKind.FUNCTION || p.kind == GraphKind.AXES || p.kind == GraphKind.COORD_PLANE)) return
        val parts = GraphFormula.parts(p.formula)
        if (parts.size < 2) return
        val fr = plan.fr
        val mode = if (p.angleDeg) com.karapp.drawguidance.content.math.AngleMode.DEG else com.karapp.drawguidance.content.math.AngleMode.RAD
        parts.drop(1).take(GraphFormula.MAX_CURVES - 1).forEachIndexed { k, text ->
            val color = EXTRA_COLORS[k % EXTRA_COLORS.size]
            val lines = ArrayList<List<P>>()
            if (Expression.isImplicit(text)) {
                val fn = Expression.parseImplicit(text, mode) ?: return@forEachIndexed
                for (chain in contour(fn, fr, c)) {
                    clip(chain.map { DP(fr.px(it.x), fr.py(it.y)) }, fr.L, fr.T, fr.R, fr.B).forEach { lines += simplify(it, c.px * 0.35f) }
                }
            } else {
                val e = Expression.parse(text, mode) ?: return@forEachIndexed
                val s = sample({ x -> e.eval(x, 0.0) }, fr.x0, fr.x1, fr, c.px)
                for (piece in s.pieces) {
                    clip(piece.map { DP(fr.px(it.x), fr.py(it.y)) }, fr.L, fr.T, fr.R, fr.B).forEach { lines += simplify(it, c.px * 0.35f) }
                }
            }
            lines.forEach { c.add(it, stroke = color, width = Style.W_CURVE, tag = "curve:${k + 1}") }
        }
    }

    private fun grid(c: Canvas, plan: Plan) {
        val fr = plan.fr
        c.layer = 0
        fun positions(step: Double, lo: Double, hi: Double, map: (Double) -> Double, skipMod: Int): List<Float> {
            val out = ArrayList<Float>()
            val a = ceil(lo / step - 1e-7).toLong()
            val b = floor(hi / step + 1e-7).toLong()
            if (b - a > 2000) return out
            for (k in a..b) if (skipMod <= 1 || k % skipMod != 0L) out += map(k * step).toFloat()
            return out
        }
        fun vLines(xs: List<Float>, color: String, width: Float) {
            if (xs.isEmpty()) return
            val pts = ArrayList<P>()
            xs.forEachIndexed { i, x -> if (i % 2 == 0) { pts += P(x, fr.B); pts += P(x, fr.T) } else { pts += P(x, fr.T); pts += P(x, fr.B) } }
            if (pts.size >= 2) c.add(pts, stroke = color, width = width)
        }
        fun hLines(ys: List<Float>, color: String, width: Float) {
            if (ys.isEmpty()) return
            val pts = ArrayList<P>()
            ys.forEachIndexed { i, y -> if (i % 2 == 0) { pts += P(fr.L, y); pts += P(fr.R, y) } else { pts += P(fr.R, y); pts += P(fr.L, y) } }
            if (pts.size >= 2) c.add(pts, stroke = color, width = width)
        }
        if (plan.minorX > 1) vLines(positions(plan.xt.step / plan.minorX, fr.x0, fr.x1, fr::px, plan.minorX), c.pal.gridMinor, Style.W_MINOR)
        if (plan.minorY > 1) hLines(positions(plan.yt.step / plan.minorY, fr.y0, fr.y1, fr::py, plan.minorY), c.pal.gridMinor, Style.W_MINOR)
        vLines(positions(plan.xt.step, fr.x0, fr.x1, fr::px, 1), c.pal.grid, Style.W_GRID)
        hLines(positions(plan.yt.step, fr.y0, fr.y1, fr::py, 1), c.pal.grid, Style.W_GRID)
        c.add(listOf(P(fr.L, fr.T), P(fr.R, fr.T), P(fr.R, fr.B), P(fr.L, fr.B), P(fr.L, fr.T)), stroke = c.pal.grid, width = Style.W_GRID)
    }

    private fun axes(c: Canvas, plan: Plan, ax: Float, ay: Float) {
        val fr = plan.fr
        c.layer = 3
        val neg = if (c.p.kind == GraphKind.FUNCTION) 0f else 0.02f
        val tipX = fr.R + Style.AXIS_EXT
        val tipY = fr.T - Style.AXIS_EXT
        c.line(fr.L - neg, ay, tipX - Style.ARROW_LEN * 0.6f, ay, width = Style.W_AXIS)
        c.arrowHead(tipX, ay, 1f, 0f)
        c.line(ax, fr.B + neg, ax, tipY + Style.ARROW_LEN * 0.6f, width = Style.W_AXIS)
        c.arrowHead(ax, tipY, 0f, -1f)
        val eps = 1e-3f
        if (c.p.kind == GraphKind.AXES && plan.minorX > 1) {
            val ms = plan.xt.step / plan.minorX
            for (k in Ticks(ms).indices(fr.x0, fr.x1)) {
                if (k % plan.minorX == 0) continue
                val x = fr.px(k * ms).toFloat()
                c.line(x, ay - Style.TICK * 0.55f, x, ay + Style.TICK * 0.55f, width = Style.W_TICK * 0.7f)
            }
            for (k in Ticks(ms).indices(fr.y0, fr.y1)) {
                if (k % plan.minorY == 0) continue
                val y = fr.py(k * ms).toFloat()
                c.line(ax - Style.TICK * 0.55f, y, ax + Style.TICK * 0.55f, y, width = Style.W_TICK * 0.7f)
            }
        }
        for (k in plan.xt.indices(fr.x0, fr.x1)) {
            if (k == 0 && fr.hasX0) continue
            val x = fr.px(plan.xt.value(k)).toFloat()
            if (x < fr.L - eps || x > fr.R + eps) continue
            c.line(x, ay - Style.TICK, x, ay + Style.TICK, width = Style.W_TICK, tag = "tick:x:$k")
        }
        for (k in plan.yt.indices(fr.y0, fr.y1)) {
            if (k == 0 && fr.hasY0) continue
            val y = fr.py(plan.yt.value(k)).toFloat()
            if (y < fr.T - eps || y > fr.B + eps) continue
            c.line(ax - Style.TICK, y, ax + Style.TICK, y, width = Style.W_TICK, tag = "tick:y:$k")
        }
    }

    private fun axisNames(c: Canvas, fr: Frame, ax: Float, ay: Float) {
        val p = c.p
        val s = Style.NAME_SIZE
        if (p.xLabel.isNotEmpty()) {
            val w = c.measure(p.xLabel, s)
            c.text(fr.R + Style.AXIS_EXT - w, ay - Style.GAP * 1.2f - s, p.xLabel, s, tag = "axis:x")
        }
        if (p.yLabel.isNotEmpty()) {
            val w = c.measure(p.yLabel, s)
            var x = ax + Style.GAP * 1.6f
            if (x + w > c.W - 0.004f) x = ax - Style.GAP * 1.6f - w
            c.text(x, fr.T - Style.AXIS_EXT - s * 0.1f, p.yLabel, s, tag = "axis:y")
        }
    }

    private fun tickLabels(c: Canvas, plan: Plan, ax: Float, ay: Float) {
        val p = c.p
        val fr = plan.fr
        val size = Style.TICK_SIZE
        val eps = 1e-3f
        val minusHalf = c.measure(Fmt.MINUS, size) / 2
        // x
        val xks = plan.xt.indices(fr.x0, fr.x1).filter {
            it != 0 && fr.px(plan.xt.value(it)).toFloat().let { x -> x >= fr.L - eps && x <= fr.R + eps }
        }
        val xTexts = xks.associateWith { c.tickText("x:$it", plan.xt.label(it)) }
        val maxW = xTexts.values.maxOfOrNull { t -> t.first?.let { c.measure(it, size) } ?: 0f } ?: 0f
        val everyX = max(1, ceil((maxW + 0.016f) / (plan.xt.step * fr.sx)).toInt())
        for (k in xks) {
            val (s, forced) = xTexts.getValue(k)
            if (s == null || (!forced && (!p.showNumbers || k % everyX != 0))) continue
            val x = fr.px(plan.xt.value(k)).toFloat()
            val w = c.measure(s, size)
            val shift = if (s.startsWith(Fmt.MINUS)) minusHalf else 0f
            val bx = x - w / 2 - shift
            val by = ay + Style.TICK + Style.GAP * 0.5f
            if (!forced && !c.fits(c.textBox(bx, by, s, size))) continue
            c.text(bx, by, s, size, tag = "tick:x:$k")
        }
        // y
        val yks = plan.yt.indices(fr.y0, fr.y1).filter {
            it != 0 && fr.py(plan.yt.value(it)).toFloat().let { y -> y >= fr.T - eps && y <= fr.B + eps }
        }
        val everyY = max(1, ceil((size + 0.01f) / (plan.yt.step * fr.sy)).toInt())
        for (k in yks) {
            val (s, forced) = c.tickText("y:$k", plan.yt.label(k))
            if (s == null || (!forced && (!p.showNumbers || k % everyY != 0))) continue
            val y = fr.py(plan.yt.value(k)).toFloat()
            val w = c.measure(s, size)
            val bx = ax - Style.TICK - Style.GAP * 0.6f - w
            val by = y - size * 0.52f
            if (!forced && !c.fits(c.textBox(bx, by, s, size))) continue
            c.text(bx, by, s, size, tag = "tick:y:$k")
        }
        // başlangıç noktası
        if (fr.hasX0 && fr.hasY0) {
            val (s, forced) = c.tickText("x:0", "0")
            if (s != null && (p.showNumbers || forced)) {
                val w = c.measure(s, size)
                c.text(ax - Style.GAP * 0.8f - w, ay + Style.GAP * 0.5f, s, size, tag = "tick:x:0")
            }
        }
    }

    private fun segHitsBox(poly: List<P>, b: Box): Int {
        var hits = 0
        for (i in 0 until poly.size - 1) {
            val a = poly[i]
            val e = poly[i + 1]
            if (max(a.x, e.x) < b.l || min(a.x, e.x) > b.r || max(a.y, e.y) < b.t || min(a.y, e.y) > b.b) continue
            if (b.contains(a.x, a.y) || b.contains(e.x, e.y)) { hits++; continue }
            // kenarlarla kesişim (örnekleyerek)
            val steps = 8
            for (s in 1 until steps) {
                val t = s / steps.toFloat()
                if (b.contains(a.x + (e.x - a.x) * t, a.y + (e.y - a.y) * t)) { hits++; break }
            }
        }
        return hits
    }

    private fun curveHits(curve: List<List<P>>, b: Box) = curve.sumOf { segHitsBox(it, b) }

    private fun formulaBox(c: Canvas, fr: Frame, text: String, curve: List<List<P>>, ax: Float, ay: Float) {
        val inset = 0.018f
        var size = Style.FORMULA_SIZE
        val avail = (fr.R - fr.L) - 2 * inset
        val unit = c.measure(text, 1f) + 0.9f
        if (unit * size > avail) size = max(0.026f, avail / unit)
        val tw = c.measure(text, size)
        val padX = size * 0.45f
        val padY = size * 0.28f
        val bw = tw + 2 * padX
        val bh = size * 1.1f + 2 * padY
        val cands = listOf(
            P(fr.L + inset, fr.T + inset), P(fr.R - inset - bw, fr.T + inset),
            P(fr.L + inset, fr.B - inset - bh), P(fr.R - inset - bw, fr.B - inset - bh),
        )
        var best = cands[0]
        var bestScore = Double.MAX_VALUE
        for ((i, cand) in cands.withIndex()) {
            val box = Box(cand.x, cand.y, cand.x + bw, cand.y + bh)
            var score = i * 0.6
            score += c.boxes.count { it.overlaps(box, 0.004f) } * 100.0
            if (ax > box.l - 0.006f && ax < box.r + 0.006f) score += 25.0
            if (ay > box.t - 0.006f && ay < box.b + 0.006f) score += 25.0
            score += curveHits(curve, box) * 3.0
            if (score < bestScore) { bestScore = score; best = cand }
        }
        c.layer = 7
        c.add(c.roundRectPoints(best.x, best.y, bw, bh, size * 0.32f), closed = true, fill = c.pal.paper, stroke = c.pal.boxBorder, width = 0.5f, alpha = 0.93f, tag = "formula")
        c.text(best.x + padX, best.y + padY + size * 0.05f, text, size, color = c.color, tag = "formula")
        c.layer = 6
    }

    /** Nokta yazısını çakışmayan (tercihen eğriye değmeyen) bir yere koyar. */
    private fun placeNear(c: Canvas, x: Float, y: Float, s: String, size: Float, curve: List<List<P>>, tag: String, color: String? = null): Boolean {
        val w = c.measure(s, size)
        val h = size
        val g = 0.012f
        val cands = listOf(
            P(x + g, y - g - h), P(x - g - w, y - g - h), P(x + g, y + g * 0.7f), P(x - g - w, y + g * 0.7f),
            P(x - w / 2, y - g * 1.5f - h), P(x - w / 2, y + g * 1.5f),
        )
        var best: P? = null
        var bestHits = Int.MAX_VALUE
        for (cand in cands) {
            val box = c.textBox(cand.x, cand.y, s, size)
            if (!c.fits(box, slack = 0.004f)) continue
            val hits = curveHits(curve, box)
            if (hits < bestHits) { bestHits = hits; best = cand }
            if (hits == 0) break
        }
        val b = best ?: return false
        c.text(b.x, b.y, s, size, color = color, tag = tag)
        return true
    }

    private fun keyLabels(c: Canvas, fr: Frame, keys: List<KeyPt>, periodic: Boolean, curve: List<List<P>>) {
        keys.forEachIndexed { i, k ->
            val s = Fmt.point(Fmt.coord(k.x, periodic), Fmt.coord(k.y, false))
            placeNear(c, fr.px(k.x).toFloat(), fr.py(k.y).toFloat(), s, Style.KEY_SIZE, curve, "key:$i")
        }
    }

    /** values = x0, y0, x1, y1, …: adlandırılmış noktalar (A(2, 3)) ve eksenlere kesikli izdüşümler. */
    private fun valuePoints(c: Canvas, fr: Frame, ax: Float, ay: Float, curve: List<List<P>>) {
        val v = c.p.values
        if (v.size < 2) return
        for (i in 0 until v.size / 2) {
            val x = v[2 * i].toDouble()
            val y = v[2 * i + 1].toDouble()
            if (!x.isFinite() || !y.isFinite()) continue
            val X = fr.px(x).toFloat()
            val Y = fr.py(y).toFloat()
            if (X < fr.L - 1e-3f || X > fr.R + 1e-3f || Y < fr.T - 1e-3f || Y > fr.B + 1e-3f) continue
            c.layer = 2
            if (abs(Y - ay) > 0.004f) c.line(X, Y, X, ay, stroke = c.pal.guide, width = 0.7f, line = LineStyle.DASHED)
            if (abs(X - ax) > 0.004f) c.line(X, Y, ax, Y, stroke = c.pal.guide, width = 0.7f, line = LineStyle.DASHED)
            c.layer = 5
            c.dot(X, Y, Style.DOT * 1.15f, tag = "value:$i")
            c.layer = 6
            val name = c.p.valueLabels[i] ?: letter(i)
            val s = if (c.p.showNumbers) name + Fmt.point(Fmt.auto(x), Fmt.auto(y)) else name
            if (s.isNotEmpty()) placeNear(c, X, Y, s, Style.KEY_SIZE * 1.05f, curve, "vlabel:$i")
        }
    }

    fun letter(i: Int): String = if (i < 26) ('A' + i).toString() else "P${i + 1}"

    private fun decorate(c: Canvas, fr: Frame, src: CurveSource) {
        val d = src.hint.decorate
        if (!d.startsWith("circle:")) return
        val r = d.removePrefix("circle:").toDoubleOrNull() ?: return
        val cx = fr.px(0.0).toFloat()
        val cy = fr.py(0.0).toFloat()
        val k = sqrt(0.5)
        val ex = fr.px(r * k).toFloat()
        val ey = fr.py(r * k).toFloat()
        c.layer = 2
        c.line(cx, cy, ex, ey, stroke = c.color, width = 1f, line = LineStyle.DASHED)
        c.layer = 5
        c.dot(cx, cy, Style.DOT * 0.9f, fill = c.color)
        if (c.p.showNumbers) {
            c.layer = 6
            val s = "r = ${Fmt.auto(r)}"
            val size = Style.KEY_SIZE
            val mx = (cx + ex) / 2
            val my = (cy + ey) / 2
            val w = c.measure(s, size)
            c.text(mx - w - 0.006f, my - size * 0.2f, s, size, color = c.color)
        }
    }

    // ---------------------------------------------------------------- asimptotlar, önemli noktalar

    private fun asymptotes(c: Canvas, fr: Frame, src: CurveSource, s: Sampled): List<Jump> {
        val span = fr.y1 - fr.y0
        val lo = fr.y0 - 0.5 * span
        val hi = fr.y1 + 0.5 * span
        val vx = ArrayList<Double>()
        val finite = ArrayList<Jump>()
        for (j in s.jumps) {
            if (j.left in lo..hi && j.right in lo..hi) finite += j
            else if (vx.none { abs(it - j.x) < 1e-6 * (1 + abs(j.x)) }) vx += j.x
        }
        c.layer = 2
        for (x in vx) {
            val X = fr.px(x).toFloat()
            if (X > fr.L + 0.003f && X < fr.R - 0.003f) {
                c.line(X, fr.T, X, fr.B, stroke = c.pal.guide, width = 0.9f, line = LineStyle.DASHED, tag = "asymptote")
            }
        }
        if (!src.periodic) {
            val hys = ArrayList<Double>()
            for (sign in doubleArrayOf(-1.0, 1.0)) {
                val a1 = src.f(sign * 1e6)
                val a2 = src.f(sign * 1e8)
                if (!a1.isFinite() || !a2.isFinite()) continue
                if (abs(a1 - a2) > 1e-4 * (1 + abs(a2))) continue
                val l = round(a2 * 1e6) / 1e6
                if (abs(l) < 1e-6 || l <= fr.y0 || l >= fr.y1) continue
                val dev = s.ys.filter { it.isFinite() }.maxOfOrNull { abs(it - l) } ?: 0.0
                if (dev < 0.05 * span) continue
                if (hys.none { abs(it - l) < 1e-6 }) hys += l
            }
            for (l in hys) {
                val Y = fr.py(l).toFloat()
                c.line(fr.L, Y, fr.R, Y, stroke = c.pal.guide, width = 0.9f, line = LineStyle.DASHED, tag = "asymptote")
            }
        }
        return finite
    }

    private fun keyPointsExplicit(src: CurveSource, s: Sampled, fr: Frame, jumps: List<Jump>): List<KeyPt> {
        val xs = s.xs
        val ys = s.ys
        val n = xs.size - 1
        val out = ArrayList<KeyPt>()
        val scale = max(abs(fr.y1), abs(fr.y0)).coerceAtLeast(1e-9)
        val zero = 1e-10 * scale
        // Kökler
        for (i in 0 until n) {
            if (!s.cont[i]) continue
            val a = ys[i]
            val b = ys[i + 1]
            if (!a.isFinite() || !b.isFinite()) continue
            if (abs(a) <= zero) {
                val prev = if (i > 0) ys[i - 1] else Double.NaN
                if ((i == 0 || abs(prev) > zero) && abs(b) > zero) out += KeyPt(xs[i], 0.0)
                continue
            }
            if (a * b < 0 && abs(b) > zero) {
                var lo = xs[i]
                var hi = xs[i + 1]
                var flo = a
                for (it in 0 until 70) {
                    val m = 0.5 * (lo + hi)
                    val fm = src.f(m)
                    if (!fm.isFinite()) break
                    if (fm == 0.0) { lo = m; hi = m; break }
                    if (flo * fm < 0) hi = m else { lo = m; flo = fm }
                }
                out += KeyPt(0.5 * (lo + hi), 0.0)
            }
        }
        if (abs(ys[n]) <= zero && n > 0 && abs(ys[n - 1]) > zero) out += KeyPt(xs[n], 0.0)
        // Yerel en büyük / en küçük
        for (i in 1 until n) {
            if (!s.cont[i - 1] || !s.cont[i]) continue
            val d1 = ys[i] - ys[i - 1]
            val d2 = ys[i + 1] - ys[i]
            if (!(d1 * d2 < 0)) continue
            val isMax = d1 > 0
            var lo = xs[i - 1]
            var hi = xs[i + 1]
            for (it in 0 until 90) {
                val m1 = lo + (hi - lo) * 0.381966
                val m2 = lo + (hi - lo) * 0.618034
                val f1 = src.f(m1)
                val f2 = src.f(m2)
                if ((f1 > f2) == isMax) hi = m2 else lo = m1
            }
            val xm = 0.5 * (lo + hi)
            val ym = src.f(xm)
            if (ym.isFinite()) out += KeyPt(xm, ym)
        }
        // y eksenini kesme noktası
        if (0.0 >= xs[0] && 0.0 <= xs[n]) {
            val y0 = src.f(0.0)
            if (y0.isFinite()) out += KeyPt(0.0, y0)
        }
        // Tanım aralığının kenarları (√x'in başlangıcı gibi)
        for (e in s.edges) {
            val interior = e.x > xs[0] + 1e-9 * (xs[n] - xs[0]) && e.x < xs[n] - 1e-9 * (xs[n] - xs[0])
            if (interior) out += KeyPt(e.x, e.y)
        }
        // Temizle: küçük değerleri sıfırla, yuvarla, çift kayıtları ve sıçrama noktalarını at
        val snapped = out.map { k ->
            var x = if (abs(k.x) < 1e-9 * (1 + abs(xs[n] - xs[0]))) 0.0 else k.x
            var y = if (abs(k.y) <= zero * 10) 0.0 else k.y
            val rx = round(x * 1e6) / 1e6
            if (abs(rx - x) < 1e-8) x = rx
            val ry = round(y * 1e6) / 1e6
            if (abs(ry - y) < 1e-8) y = ry
            KeyPt(x, y)
        }
        val result = ArrayList<KeyPt>()
        for (k in snapped) {
            val X = fr.px(k.x)
            val Y = fr.py(k.y)
            if (X < fr.L + 0.004 || X > fr.R - 0.004 || Y < fr.T + 0.004 || Y > fr.B - 0.004) continue
            if (jumps.any { abs(fr.px(it.x) - X) < 0.01 }) continue
            if (result.any { hypot(fr.px(it.x) - X, fr.py(it.y) - Y) < 0.012 }) continue
            result += k
        }
        return if (result.size > 16) emptyList() else result
    }

    private fun keyPointsImplicit(fn: Expression.Expr, fr: Frame): List<KeyPt> {
        val out = ArrayList<KeyPt>()
        fun scan(a: Double, b: Double, g: (Double) -> Double, make: (Double) -> KeyPt) {
            val n = 400
            var pv = g(a)
            var px = a
            for (i in 1..n) {
                val x = a + (b - a) * i / n
                val v = g(x)
                if (pv.isFinite() && v.isFinite() && pv * v < 0) {
                    var lo = px
                    var hi = x
                    var flo = pv
                    for (it in 0 until 60) {
                        val m = 0.5 * (lo + hi)
                        val fm = g(m)
                        if (flo * fm <= 0) hi = m else { lo = m; flo = fm }
                    }
                    val r = 0.5 * (lo + hi)
                    val scale = max(abs(pv), abs(v))
                    if (abs(g(r)) < 0.1 * scale) out += make(if (abs(r - round(r * 1e6) / 1e6) < 1e-8) round(r * 1e6) / 1e6 else r)
                }
                pv = v
                px = x
            }
        }
        if (fr.hasY0) scan(fr.x0, fr.x1, { fn.eval(it, 0.0) }) { KeyPt(it, 0.0) }
        if (fr.hasX0) scan(fr.y0, fr.y1, { fn.eval(0.0, it) }) { KeyPt(0.0, it) }
        val result = ArrayList<KeyPt>()
        for (k in out) {
            val X = fr.px(k.x)
            val Y = fr.py(k.y)
            if (X < fr.L + 0.004 || X > fr.R - 0.004 || Y < fr.T + 0.004 || Y > fr.B - 0.004) continue
            if (result.any { hypot(fr.px(it.x) - X, fr.py(it.y) - Y) < 0.012 }) continue
            result += k
        }
        return if (result.size > 16) emptyList() else result
    }
}
