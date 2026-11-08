package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.math.MNode
import com.karapp.drawguidance.content.math.MathException
import com.karapp.drawguidance.content.math.MathExpr
import com.karapp.drawguidance.content.math.Rel
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * v45 (6) formülle ve tutamakla düzenlenen geometri/matematik/biyoloji şekilleri: üçgen (açı–kenar), çember
 * (yarıçap), düzgün çokgen (kenar sayısı), parabol (y = ax² + bx + c) ve lojistik nüfus büyümesi.
 */

private fun rad(d: Double) = d * PI / 180.0
private fun degOf(r: Double) = r * 180.0 / PI
private fun n(v: Double, d: Int = 2) = ParamModel.num(v, d)
private const val FILL = "#E3F2FD"
private const val EDGE = "#1E88E5"
private const val GRID = "#CFD8DC"

/** "1, 2, 5 × 10ⁿ" dizisinde [v]'den büyük ya da eşit ilk değer. */
internal fun niceCeil(v: Double): Double {
    if (v <= 0 || !v.isFinite()) return 1.0
    val p = 10.0.pow(floor(log10(v)))
    for (m in listOf(1.0, 2.0, 5.0, 10.0)) if (m * p >= v - 1e-12) return m * p
    return 10 * p
}

/** a·x² + b·x + c'nin okunur yazımı ("x² − 2x − 3"). */
internal fun polyText(a: Double, b: Double, c: Double, v: String = "x"): String {
    val sb = StringBuilder()
    fun term(k: Double, body: String) {
        if (abs(k) < 1e-12) return
        val neg = k < 0
        val mag = abs(k)
        val coef = if (body.isNotEmpty() && abs(mag - 1) < 1e-12) "" else n(mag, 3)
        if (sb.isEmpty()) sb.append(if (neg) "−" else "") else sb.append(if (neg) " − " else " + ")
        sb.append(coef).append(body)
    }
    term(a, "$v²"); term(b, v); term(c, "")
    return if (sb.isEmpty()) "0" else sb.toString()
}

// ------------------------------------------------------------------ Üçgen

/**
 * Üçgen: iki açı (A, B) ve aradaki kenar (c) verilir; C = 180° − A − B, a ve b sinüs teoremiyle. Tutamak: C köşesi
 * (sürüklenince A ve B değişir, c sabit).
 */
internal object TriangleModel : ParamModel(
    "sci.geo.triangle", "Üçgen (açılar ve kenarlar)", 1.4f, 1f,
    tags = listOf("üçgen", "açı", "kenar", "iç açılar toplamı", "sinüs teoremi", "geometri", "formül"),
) {
    override val defs = listOf(
        ParamDef("A", "A", "A açısı", ParamKind.ANGLE, default = 60.0, min = 1.0, max = 178.0, step = 5.0, unit = "°"),
        ParamDef("B", "B", "B açısı", ParamKind.ANGLE, default = 50.0, min = 1.0, max = 178.0, step = 5.0, unit = "°"),
        ParamDef("c", "c", "c kenarı", default = 6.0, min = 0.1, max = 1000.0, step = 0.5),
    )
    private fun cAng(v: ParamValues) = 180.0 - v["A"] - v["B"]
    private fun sideA(v: ParamValues) = cAng(v).let { if (it <= 0) Double.NaN else v["c"] * sin(rad(v["A"])) / sin(rad(it)) }
    private fun sideB(v: ParamValues) = cAng(v).let { if (it <= 0) Double.NaN else v["c"] * sin(rad(v["B"])) / sin(rad(it)) }
    override val derived = listOf(
        Derived("C", "C açısı", "°", "B") { cAng(it) },
        Derived("a", "a kenarı", "", "A") { sideA(it) },
        Derived("b", "b kenarı", "", "B") { sideB(it) },
        Derived("S", "Alan", "", "c") { 0.5 * sideB(it) * it["c"] * sin(rad(it["A"])) },
    )
    override fun invalid(v: ParamValues): String? = if (cAng(v) < 1.0 - 1e-9) "C" else null

    override fun editText(raw: Map<String, String>?): String {
        val v = values(raw)
        return "A = ${n(v["A"], 2)}°; B = ${n(v["B"], 2)}°; c = ${n(v["c"], 3)}"
    }

    override fun label(v: ParamValues) = listOf(
        "A + B + C = 180°",
        "A = ${n(v["A"], 1)}°; B = ${n(v["B"], 1)}°; C = ${n(cAng(v), 1)}°",
        "a = ${n(sideA(v))}; b = ${n(sideB(v))}; c = ${n(v["c"])}",
    )

    /** Dünya (A = orijin, B = (c, 0), y yukarı) → sanal dönüşüm. */
    private class Frame(val s: Double, val ox: Double, val xmin: Double) {
        fun map(x: Double, y: Double) = P((ox + (x - xmin) * s).toFloat(), (BASE - y * s).toFloat())
        fun unmap(p: P) = Pair((p.x - ox) / s + xmin, (BASE - p.y) / s)
    }
    private const val BASE = 0.9
    private const val LEFT = 0.12
    private const val WIDTH = 1.16
    private const val HEIGHT = 0.52

    private fun apex(v: ParamValues): Pair<Double, Double> {
        val b = sideB(v)
        return Pair(b * cos(rad(v["A"])), b * sin(rad(v["A"])))
    }

    private fun frame(v: ParamValues): Frame {
        val c = v["c"]
        val (cx, cy) = apex(v)
        val xmin = min(0.0, cx); val xmax = max(c, cx)
        val s = min(WIDTH / (xmax - xmin), HEIGHT / max(cy, 1e-9))
        return Frame(s, LEFT + (WIDTH - (xmax - xmin) * s) / 2, xmin)
    }

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        if (invalid(v) != null) return@with
        val fr = frame(v)
        val (cx, cy) = apex(v)
        val pa = fr.map(0.0, 0.0); val pb = fr.map(v["c"], 0.0); val pc = fr.map(cx, cy)
        poly(pa.x, pa.y, pb.x, pb.y, pc.x, pc.y, closed = true).fill(FILL).width(1.4f).tag("curve")
        val g = P((pa.x + pb.x + pc.x) / 3f, (pa.y + pb.y + pc.y) / 3f)
        fun corner(p: P, q1: P, q2: P, name: String, ang: Double) {
            val a1 = angleOf(p.x, p.y, q1.x, q1.y)
            var sw = angleOf(p.x, p.y, q2.x, q2.y) - a1
            while (sw > 180f) sw -= 360f
            while (sw < -180f) sw += 360f
            arc(p.x, p.y, 0.07f, 0.07f, a1, sw).width(0.8f).stroke(EDGE)
            val mid = onCircle(p.x, p.y, 0.13f, a1 + sw / 2f)
            lb.c(mid.x, mid.y, "${n(ang, 0)}°", 0.05f, EDGE)
            val u = unit(p.x - g.x, p.y - g.y)
            lb.c(p.x + u.x * 0.05f, p.y + u.y * 0.05f, name, 0.07f)
        }
        corner(pa, pb, pc, "A", v["A"]); corner(pb, pc, pa, "B", v["B"]); corner(pc, pa, pb, "C", cAng(v))
        fun side(p: P, q: P, name: String) {
            val m = lerp(p, q, 0.5f)
            val u = unit(m.x - g.x, m.y - g.y)
            lb.c(m.x + u.x * 0.06f, m.y + u.y * 0.06f, name, 0.06f)
        }
        side(pb, pc, "a"); side(pc, pa, "b"); side(pa, pb, "c")
        dot(pc.x, pc.y, 0.016f, EDGE)
    }

    override fun handles(v: ParamValues): List<ParamHandle> {
        if (invalid(v) != null) return emptyList()
        val (cx, cy) = apex(v)
        val p = frame(v).map(cx, cy)
        return listOf(ParamHandle("C", p.x, p.y))
    }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        if (key != "C") return null
        val (wx, wy) = frame(v).unmap(P(x, y))
        if (wy <= v["c"] * 0.01) return null
        val a = degOf(atan2(wy, wx)); val bb = degOf(atan2(wy, v["c"] - wx))
        return mapOf("A" to round(a * 10) / 10, "B" to round(bb * 10) / 10)
    }
}

// ------------------------------------------------------------------ Çember

/**
 * Çember: x² + y² = r². Ç = 2πr, A = πr². Koordinat ızgarası yarıçapla birlikte ölçeklenir (1–2–5 adımlı). Tutamak:
 * yarıçapın ucu.
 */
internal object CircleModel : ParamModel(
    "sci.geo.circle", "Çember (yarıçap)", 1f, 1f, Fit.SQUARE,
    tags = listOf("çember", "daire", "yarıçap", "çevre", "alan", "pi", "geometri", "formül"),
) {
    override val defs = listOf(ParamDef("r", "r", "Yarıçap", default = 3.0, min = 0.1, max = 1000.0, step = 0.5))
    override val derived = listOf(
        Derived("Ç", "Çevre", "", "r") { 2 * PI * it["r"] },
        Derived("A", "Alan", "", "r") { PI * it["r"] * it["r"] },
        Derived("d", "Çap", "", "r") { 2 * it["r"] },
    )

    override fun label(v: ParamValues): List<String> {
        val r = v["r"]
        return listOf("x² + y² = r²", "r = ${n(r)}; x² + y² = ${n(r * r)}", "Ç = 2πr ≈ ${n(2 * PI * r)}; A = πr² ≈ ${n(PI * r * r)}")
    }

    override fun editText(raw: Map<String, String>?): String = "x² + y² = ${n(values(raw)["r"].let { it * it }, 4)}"

    private const val CX = 0.5f
    private const val CY = 0.6f
    private const val HALF = 0.36f
    private const val RAY_DEG = -40f

    private fun extent(r: Double) = niceCeil(r * 1.15)
    private fun scale(v: ParamValues) = HALF / extent(v["r"]).toFloat()

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val e = extent(v["r"])
        val s = scale(v)
        val lead = (e / 10.0.pow(floor(log10(e)))).let { round(it).toInt() }
        val ticks = if (lead == 2) 4 else 5
        val step = HALF / ticks
        for (i in -ticks..ticks) {
            val d = i * step
            line(CX + d, CY - HALF, CX + d, CY + HALF).stroke(GRID).width(0.5f)
            line(CX - HALF, CY + d, CX + HALF, CY + d).stroke(GRID).width(0.5f)
        }
        arrow(CX - HALF - 0.04f, CY, CX + HALF + 0.05f, CY, head = 0.03f).width(0.9f)
        arrow(CX, CY + HALF + 0.04f, CX, CY - HALF - 0.05f, head = 0.03f).width(0.9f)
        lb.c(CX + HALF + 0.05f, CY + 0.045f, "x", 0.05f)
        lb.c(CX - 0.04f, CY - HALF - 0.04f, "y", 0.05f)
        lb.c(CX + HALF, CY + 0.05f, n(e), 0.045f)
        val r = (v["r"] * s).toFloat()
        circle(CX, CY, r).fill(FILL).stroke(EDGE).width(1.6f).tag("curve")
        val tip = onCircle(CX, CY, r, RAY_DEG)
        line(CX, CY, tip.x, tip.y).width(1.1f)
        dot(CX, CY, 0.014f)
        lb.c(CX - 0.035f, CY + 0.035f, "O", 0.05f)
        val m = onCircle(CX, CY, r / 2f, RAY_DEG - 12f)
        lb.c(m.x, m.y, "r", 0.06f)
        dot(tip.x, tip.y, 0.016f, EDGE)
    }

    override fun handles(v: ParamValues): List<ParamHandle> {
        val tip = onCircle(CX, CY, (v["r"] * scale(v)).toFloat(), RAY_DEG)
        return listOf(ParamHandle("r", tip.x, tip.y))
    }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        if (key != "r") return null
        val d = hypot((x - CX).toDouble(), (y - CY).toDouble()) / scale(v)
        if (d <= 0) return null
        // 2 anlamlı basamağa yuvarlanır (sürüklemede okunur değerler)
        val p = 10.0.pow(floor(log10(d)) - 1)
        return mapOf("r" to round(d / p) * p)
    }

    /** "x² + y² = 16" gibi çember denklemi: merkezi orijinde çemberse r çıkarılır; değilse genel uygulama. */
    override fun apply(raw: Map<String, String>?, text: String): ParamApply {
        if (text.contains(';') || text.contains('\n')) return super.apply(raw, text)
        val rel = try { MathExpr.parseRelation(text) } catch (e: MathException) { return super.apply(raw, text) }
        val vars = rel.sides.flatMap { it.variables() }.toSet()
        if (!vars.contains("x") || !vars.contains("y")) return super.apply(raw, text)
        if (rel.ops != listOf(Rel.EQ) || vars.any { it != "x" && it != "y" }) return ParamApply.Failed(ParamError(ParamError.Code.NO_MATCH))
        val lhs = rel.sides[0]; val rhs = rel.sides[1]
        fun g(x: Double, y: Double) = MathExpr.eval(MNode.Bin('-', lhs, rhs), mapOf("x" to x, "y" to y))
        val g0 = g(0.0, 0.0)
        if (!g0.isFinite() || g0 == 0.0) return ParamApply.Failed(ParamError(ParamError.Code.NO_MATCH))
        var hi = 0.0
        var t = 1e-3
        while (t <= 2000.0) { val gt = g(t, 0.0); if (gt.isFinite() && (gt < 0) != (g0 < 0)) { hi = t; break }; t *= 1.5 }
        if (hi == 0.0) return ParamApply.Failed(ParamError(ParamError.Code.NO_MATCH))
        var lo = hi / 1.5
        if (lo < 1e-3) lo = 0.0
        repeat(80) { val m = (lo + hi) / 2; if ((g(m, 0.0) < 0) == (g0 < 0)) lo = m else hi = m }
        val r = (lo + hi) / 2
        val tol = 1e-6 * (1 + abs(g0))
        for (deg in listOf(90.0, 180.0, 37.0, 233.0, 300.0)) if (abs(g(r * cos(rad(deg)), r * sin(rad(deg)))) > tol) return ParamApply.Failed(ParamError(ParamError.Code.NO_MATCH))
        val d = def("r")!!
        if (r < d.min || r > d.max) return ParamApply.Failed(ParamError(ParamError.Code.OUT_OF_RANGE, "r"))
        return ParamApply.Ok(withValue(raw, "r", r))
    }
}

// ------------------------------------------------------------------ Düzgün çokgen

/** Düzgün çokgen: iç açı α = (n − 2)·180°/n. Tutamak: ikinci köşe (çember üzerinde kaydırınca n değişir). */
internal object PolygonModel : ParamModel(
    "sci.geo.polygon", "Düzgün çokgen", 1f, 1f, Fit.SQUARE,
    tags = listOf("çokgen", "düzgün çokgen", "beşgen", "altıgen", "iç açı", "kenar", "geometri", "formül"),
) {
    override val defs = listOf(
        ParamDef("n", "n", "Kenar sayısı", ParamKind.INT, default = 6.0, min = 3.0, max = 12.0, step = 1.0),
        ParamDef("a", "a", "Kenar uzunluğu", default = 4.0, min = 0.1, max = 1000.0, step = 0.5),
    )
    private fun alpha(v: ParamValues) = (v["n"] - 2) * 180.0 / v["n"]
    private fun area(v: ParamValues) = v["n"] * v["a"] * v["a"] / (4 * tan(PI / v["n"]))
    override val derived = listOf(
        Derived("α", "İç açı", "°", "n") { alpha(it) },
        Derived("Ç", "Çevre", "", "a") { it["n"] * it["a"] },
        Derived("A", "Alan", "", "a") { area(it) },
    )
    override fun editText(raw: Map<String, String>?): String { val v = values(raw); return "n = ${v["n"].toInt()}; a = ${n(v["a"], 3)}" }
    override fun label(v: ParamValues) = listOf(
        "α = (n − 2)·180°/n",
        "n = ${v["n"].toInt()}; a = ${n(v["a"])}",
        "α = ${n(alpha(v))}°; Ç = ${n(v["n"] * v["a"])}; A ≈ ${n(area(v))}",
    )

    private const val CX = 0.5f
    private const val CY = 0.6f
    private const val R = 0.33f

    private fun theta(v: ParamValues, k: Int): Float { val nn = v["n"].toFloat(); return 90f + 180f / nn + k * 360f / nn }
    private fun vertex(v: ParamValues, k: Int) = onCircle(CX, CY, R, theta(v, k))

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val nn = v["n"].toInt()
        val pts = (0 until nn).map { vertex(v, it) }
        points(pts, closed = true).fill(FILL).stroke(EDGE).width(1.5f).tag("curve")
        circle(CX, CY, R).dashed().width(0.5f)
        pts.forEach { dot(it.x, it.y, 0.012f) }
        // iç açı: 0. köşede (sol alt)
        val p0 = pts[0]; val p1 = pts[1]; val pl = pts[nn - 1]
        val a1 = angleOf(p0.x, p0.y, pl.x, pl.y)
        var sw = angleOf(p0.x, p0.y, p1.x, p1.y) - a1
        while (sw > 180f) sw -= 360f
        while (sw < -180f) sw += 360f
        arc(p0.x, p0.y, 0.06f, 0.06f, a1, sw).width(0.8f).stroke(EDGE)
        val mid = onCircle(p0.x, p0.y, 0.11f, a1 + sw / 2f)
        lb.c(mid.x, mid.y, "α", 0.06f, EDGE)
        val m = lerp(pl, p0, 0.5f)
        lb.c(m.x, m.y + 0.05f, "a", 0.065f)
        dot(p1.x, p1.y, 0.016f, EDGE)
    }

    override fun handles(v: ParamValues): List<ParamHandle> { val p = vertex(v, 1); return listOf(ParamHandle("corner", p.x, p.y)) }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        if (key != "corner") return null
        if (hypot(x - CX, y - CY) < R * 0.2f) return null
        var d = angleOf(CX, CY, x, y) - theta(v, 0)
        while (d <= 0f) d += 360f
        while (d > 360f) d -= 360f
        if (d > 180f) return null
        return mapOf("n" to round(360.0 / d).coerceIn(3.0, 12.0))
    }
}

// ------------------------------------------------------------------ Parabol

/**
 * Parabol y = ax² + bx + c. Pencere tepe noktasına göre seçilir (10 × 10 birim). Tutamaklar: tepe (b ve c; a sabit) ve
 * tepeden bir birim sağdaki nokta (a; tepe sabit).
 */
internal object ParabolaModel : ParamModel(
    "sci.math.parabola", "Parabol (y = ax² + bx + c)", 1.2f, 1f,
    tags = listOf("parabol", "ikinci dereceden", "fonksiyon", "tepe noktası", "diskriminant", "grafik", "matematik", "formül"),
) {
    override val defs = listOf(
        ParamDef("a", "a", "a katsayısı", default = 1.0, min = -20.0, max = 20.0, step = 0.5),
        ParamDef("b", "b", "b katsayısı", default = -2.0, min = -100.0, max = 100.0, step = 1.0),
        ParamDef("c", "c", "c katsayısı", default = -3.0, min = -1000.0, max = 1000.0, step = 1.0),
    )
    private fun xv(v: ParamValues) = -v["b"] / (2 * v["a"])
    private fun yv(v: ParamValues) = v["c"] - v["b"] * v["b"] / (4 * v["a"])
    override val derived = listOf(Derived("Δ", "Diskriminant", "", "c") { it["b"] * it["b"] - 4 * it["a"] * it["c"] })
    override fun invalid(v: ParamValues): String? = if (abs(v["a"]) < 1e-9) "a" else null

    override val curveVar = "y"
    override fun curve(v: ParamValues): (Double) -> Double { val a = v["a"]; val b = v["b"]; val c = v["c"]; return { x -> a * x * x + b * x + c } }

    override fun fit(f: (Double) -> Double, v: ParamValues): Map<String, Double>? {
        val c = f(0.0); val p = f(1.0); val m = f(-1.0)
        if (!c.isFinite() || !p.isFinite() || !m.isFinite()) return null
        val a = (p + m - 2 * c) / 2; val b = (p - m) / 2
        for (x in listOf(2.0, -3.0, 0.5, 7.0)) if (abs(f(x) - (a * x * x + b * x + c)) > 1e-6 * (1 + abs(f(x)))) return null
        if (abs(a) < 1e-9) return null
        return mapOf("a" to a, "b" to b, "c" to c)
    }

    override fun curveText(v: ParamValues) = "y = " + polyText(v["a"], v["b"], v["c"])

    override fun label(v: ParamValues): List<String> {
        val d = v["b"] * v["b"] - 4 * v["a"] * v["c"]
        return listOf("y = ax² + bx + c", curveText(v), "T(${n(xv(v))}; ${n(yv(v))}); Δ = ${n(d)}")
    }

    private const val X0 = 0.1f
    private const val X1 = 1.14f
    private const val Y0 = 0.28f
    private const val Y1 = 0.96f
    private const val SPAN = 10.0

    private class Win(val x0: Double, val y0: Double) {
        fun map(x: Double, y: Double) = P(X0 + ((x - x0) / SPAN * (X1 - X0)).toFloat(), Y1 - ((y - y0) / SPAN * (Y1 - Y0)).toFloat())
        fun unmap(p: P) = Pair(x0 + (p.x - X0) / (X1 - X0) * SPAN, y0 + (Y1 - p.y) / (Y1 - Y0) * SPAN)
    }

    private fun win(v: ParamValues): Win {
        val cx = round(xv(v)); val cy = round(yv(v))
        return Win(cx - SPAN / 2, if (v["a"] > 0) cy - 2 else cy - SPAN + 2)
    }

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        if (invalid(v) != null) return@with
        val w = win(v)
        for (i in 0..SPAN.toInt()) {
            val gx = w.map(w.x0 + i, w.y0); val gy = w.map(w.x0, w.y0 + i)
            line(gx.x, Y0, gx.x, Y1).stroke(GRID).width(0.4f)
            line(X0, gy.y, X1, gy.y).stroke(GRID).width(0.4f)
        }
        if (0.0 in w.x0..(w.x0 + SPAN)) { val p = w.map(0.0, w.y0); line(p.x, Y0, p.x, Y1).width(0.9f); lb.c(p.x + 0.03f, Y0 + 0.02f, "y", 0.045f) }
        if (0.0 in w.y0..(w.y0 + SPAN)) { val p = w.map(w.x0, 0.0); line(X0, p.y, X1, p.y).width(0.9f); lb.c(X1 - 0.02f, p.y - 0.035f, "x", 0.045f) }
        val f = curve(v)
        var run = ArrayList<P>()
        val n = 240
        for (i in 0..n) {
            val x = w.x0 + SPAN * i / n
            val y = f(x)
            if (y in (w.y0 - 0.5)..(w.y0 + SPAN + 0.5)) run += w.map(x, y.coerceIn(w.y0, w.y0 + SPAN))
            else if (run.isNotEmpty()) { if (run.size > 1) points(run).stroke(EDGE).width(1.7f).tag("curve"); run = ArrayList() }
        }
        if (run.size > 1) points(run).stroke(EDGE).width(1.7f).tag("curve")
        val t = w.map(xv(v), yv(v))
        dot(t.x, t.y, 0.016f, EDGE)
        lb.c(t.x + 0.045f, t.y + (if (v["a"] > 0) 0.04f else -0.04f), "T", 0.055f, EDGE)
        val q = pointHandle(v)
        dot(q.x, q.y, 0.012f)
    }

    private fun pointHandle(v: ParamValues): P {
        val dx = min(1.0, sqrt(6.0 / abs(v["a"])))
        val x = xv(v) + dx
        return win(v).map(x, curve(v)(x))
    }

    override fun handles(v: ParamValues): List<ParamHandle> {
        if (invalid(v) != null) return emptyList()
        val t = win(v).map(xv(v), yv(v)); val q = pointHandle(v)
        return listOf(ParamHandle("vertex", t.x, t.y), ParamHandle("point", q.x, q.y))
    }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        val w = win(v)
        val (wx, wy) = w.unmap(P(x, y))
        val a = v["a"]
        return when (key) {
            "vertex" -> {
                val h = round(wx * 10) / 10; val k = round(wy * 10) / 10
                mapOf("b" to -2 * a * h, "c" to a * h * h + k)
            }
            "point" -> {
                val h = xv(v); val k = yv(v)
                val dx = wx - h
                if (abs(dx) < 0.05) return null
                val na = round((wy - k) / (dx * dx) * 10) / 10
                if (abs(na) < 1e-9) return null
                mapOf("a" to na, "b" to -2 * na * h, "c" to na * h * h + k)
            }
            else -> null
        }
    }
}

// ------------------------------------------------------------------ Biyoloji: lojistik büyüme

/**
 * Lojistik nüfus büyümesi N(t) = K / (1 + ((K − N₀)/N₀)·e^(−rt)). Tutamaklar: taşıma kapasitesi K, başlangıç N₀ ve
 * büküm noktası (yatay: r).
 */
internal object PopulationModel : ParamModel(
    "sci.bio.population_growth", "Nüfus büyümesi (lojistik)", 1.6f, 1f,
    tags = listOf("nüfus", "popülasyon", "büyüme eğrisi", "taşıma kapasitesi", "lojistik", "ekoloji", "biyoloji", "formül"),
) {
    override val defs = listOf(
        ParamDef("N0", "N_0", "Başlangıç nüfusu", default = 10.0, min = 0.01, max = 1e9, step = 5.0),
        ParamDef("r", "r", "Büyüme hızı", default = 0.5, min = 0.01, max = 5.0, step = 0.1),
        ParamDef("K", "K", "Taşıma kapasitesi", default = 100.0, min = 1.0, max = 1e9, step = 10.0),
    )
    override fun invalid(v: ParamValues): String? = if (v["N0"] >= v["K"]) "N_0" else null
    override val curveVar = "N"
    override val curveArg = "t"
    override fun curve(v: ParamValues): (Double) -> Double {
        val k = v["K"]; val n0 = v["N0"]; val r = v["r"]
        return { t -> k / (1 + (k - n0) / n0 * exp(-r * t)) }
    }
    private fun tHalf(v: ParamValues) = ln((v["K"] - v["N0"]) / v["N0"]) / v["r"]
    override val derived = listOf(Derived("t_m", "Büküm zamanı", "", "r") { tHalf(it) })

    override fun fit(f: (Double) -> Double, v: ParamValues): Map<String, Double>? {
        val n0 = f(0.0); val k = f(4000.0)
        if (!n0.isFinite() || !k.isFinite() || n0 <= 0 || k <= n0 || abs(f(8000.0) - k) > 1e-6 * k) return null
        val q = (k - n0) / n0
        val r = -ln((k / f(1.0) - 1) / q)
        if (!r.isFinite() || r <= 0) return null
        for (t in listOf(0.5, 2.0, 5.0, 11.0)) if (abs(f(t) - k / (1 + q * exp(-r * t))) > 1e-6 * k) return null
        return mapOf("N0" to n0, "K" to k, "r" to r)
    }

    override fun curveText(v: ParamValues): String {
        val q = (v["K"] - v["N0"]) / v["N0"]
        return "N = ${n(v["K"])}/(1 + ${n(q, 3)}·e^(−${n(v["r"], 3)}t))"
    }
    override fun editText(raw: Map<String, String>?): String { val v = values(raw); return "N₀ = ${n(v["N0"], 3)}; r = ${n(v["r"], 3)}; K = ${n(v["K"], 3)}" }

    override fun label(v: ParamValues) = listOf(
        "N = K / (1 + ((K − N₀)/N₀)·e^(−rt))",
        "N₀ = ${n(v["N0"])}; r = ${n(v["r"], 3)}; K = ${n(v["K"])}",
    )

    private const val OX = 0.14f
    private const val OY = 0.92f
    private const val PW = 1.36f
    private const val PH = 0.6f

    private fun tMax(v: ParamValues) = (max(ln((v["K"] - v["N0"]) / v["N0"]), 0.0) + 5) / v["r"]
    private fun mapT(v: ParamValues, t: Double) = OX + (t / tMax(v) * PW).toFloat()
    private fun mapN(v: ParamValues, nn: Double) = OY - (nn / (1.1 * v["K"]) * PH).toFloat()

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        if (invalid(v) != null) return@with
        arrow(OX, OY, OX + PW + 0.05f, OY, head = 0.03f).width(1f)
        arrow(OX, OY, OX, OY - PH - 0.06f, head = 0.03f).width(1f)
        lb.c(OX + PW + 0.05f, OY + 0.045f, "t", 0.055f)
        lb.c(OX - 0.05f, OY - PH - 0.05f, "N", 0.055f)
        val f = curve(v)
        val tm = tMax(v)
        val yk = mapN(v, v["K"])
        line(OX, yk, OX + PW, yk).dashed().width(0.7f).stroke(Sc.RED)
        lb.c(OX - 0.04f, yk, "K", 0.055f, Sc.RED)
        curve(samples = 160) { s -> val t = tm * s; P(mapT(v, t), mapN(v, f(t))) }.stroke(Sc.GREEN).width(1.8f).tag("curve")
        val y0 = mapN(v, v["N0"])
        dot(OX, y0, 0.014f, Sc.GREEN)
        lb.c(OX - 0.06f, y0, "N₀", 0.05f)
        val th = tHalf(v)
        if (th > 0) {
            val p = P(mapT(v, th), mapN(v, v["K"] / 2))
            line(p.x, p.y, p.x, OY).dotted().width(0.6f)
            dot(p.x, p.y, 0.012f)
        }
    }

    override fun handles(v: ParamValues): List<ParamHandle> {
        if (invalid(v) != null) return emptyList()
        val out = arrayListOf(ParamHandle("K", OX + PW * 0.9f, mapN(v, v["K"])), ParamHandle("N0", OX, mapN(v, v["N0"])))
        val th = tHalf(v)
        if (th > 0) out += ParamHandle("mid", mapT(v, th), mapN(v, v["K"] / 2))
        return out
    }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        val scaleN = 1.1 * v["K"] / PH
        return when (key) {
            "K" -> ((OY - y) * scaleN).takeIf { it > v["N0"] }?.let { mapOf("K" to round(it * 10) / 10) }
            "N0" -> ((OY - y) * scaleN).takeIf { it > 0 && it < v["K"] }?.let { mapOf("N0" to round(it * 10) / 10) }
            "mid" -> {
                val t = (x - OX) / PW * tMax(v)
                val l = ln((v["K"] - v["N0"]) / v["N0"])
                if (t <= 0.01 || l <= 0) null else mapOf("r" to round(l / t * 1000) / 1000)
            }
            else -> null
        }
    }
}
