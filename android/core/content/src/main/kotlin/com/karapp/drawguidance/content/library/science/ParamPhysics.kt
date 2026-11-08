package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * v45 (9) formülle çizilen fizik şekilleri. Kimlikler eski sabit çizimlerle aynıdır ("sci.phys.projectile"…): eski
 * kayıtlar varsayılan değerlerle aynı görünür, Düzenle'de değerleri ve formülü açılır.
 */

private fun rad(d: Double) = d * PI / 180.0
private fun degOf(r: Double) = r * 180.0 / PI
private fun n(v: Double, d: Int = 2) = ParamModel.num(v, d)

/** Eğik atış: y = x·tanθ − g·x²/(2v²·cos²θ). Tutamak: hız vektörünün ucu (yön θ, boy v). */
internal object ProjectileModel : ParamModel(
    "sci.phys.projectile", "Eğik atış", 1.8f, 1f,
    tags = listOf("atış", "yörünge", "menzil", "maksimum yükseklik", "hız", "mekanik", "formül"),
) {
    override val defs = listOf(
        ParamDef("v", "v", "İlk hız", default = 20.0, min = 1.0, max = 100.0, step = 1.0, unit = "m/s"),
        ParamDef("theta", "θ", "Atış açısı", ParamKind.ANGLE, default = 55.0, min = 5.0, max = 85.0, step = 5.0, unit = "°"),
        ParamDef("g", "g", "Yer çekimi ivmesi", default = 9.8, min = 0.5, max = 30.0, step = 0.1, unit = "m/s²", advanced = true),
    )
    private fun range(v: ParamValues) = v["v"] * v["v"] * sin(2 * rad(v["theta"])) / v["g"]
    private fun hmax(v: ParamValues) = v["v"] * v["v"] * sin(rad(v["theta"])).let { it * it } / (2 * v["g"])
    override val derived = listOf(
        Derived("R", "Menzil", "m", "v") { range(it) },
        Derived("h", "En büyük yükseklik", "m", "v") { hmax(it) },
        Derived("t", "Uçuş süresi", "s", "v") { 2 * it["v"] * sin(rad(it["theta"])) / it["g"] },
    )
    override val curveVar = "y"
    override fun curve(v: ParamValues): (Double) -> Double {
        val th = rad(v["theta"]); val sp = v["v"]; val g = v["g"]
        return { x -> x * tan(th) - g * x * x / (2 * sp * sp * cos(th) * cos(th)) }
    }

    override fun fit(f: (Double) -> Double, v: ParamValues): Map<String, Double>? {
        // y = p·x − q·x² (orijinden geçen parabol)
        val s = max(1.0, range(v) / 4)
        if (abs(f(0.0)) > 1e-6) return null
        val p = (4 * f(s) - f(2 * s)) / (2 * s)
        val q = (2 * f(s) - f(2 * s)) / (2 * s * s)
        for (x in listOf(0.5 * s, 3 * s)) if (abs(f(x) - (p * x - q * x * x)) > 1e-6 * (1 + abs(f(x)))) return null
        if (q <= 0 || p <= 0) return null
        val th = atan(p)
        val sp = sqrt(v["g"] / (2 * q * cos(th) * cos(th)))
        return mapOf("theta" to degOf(th), "v" to sp)
    }

    override fun curveText(v: ParamValues): String {
        val p = tan(rad(v["theta"])); val q = v["g"] / (2 * v["v"] * v["v"] * cos(rad(v["theta"])).let { it * it })
        return "y = ${n(p, 3)}x − ${n(q, 4)}x²"
    }

    override fun label(v: ParamValues) = listOf(
        "y = x·tanθ − g·x²/(2v²·cos²θ)",
        "v = ${n(v["v"])} m/s; θ = ${n(v["theta"], 1)}°",
        "R = ${n(range(v), 1)} m; h = ${n(hmax(v), 1)} m",
    )

    private const val GY = 0.86f
    private const val OX = 0.14f
    private const val VEC0 = 0.12f
    private const val VEC_PER = 0.01f

    /** Dünya → sanal ölçek (menzil ve yükseklik kutuya sığar). */
    private fun scale(v: ParamValues): Double = min(1.5 / max(range(v), 1e-6), 0.5 / max(hmax(v), 1e-6))

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val s = scale(v)
        val f = curve(v)
        val r = range(v)
        val th = rad(v["theta"])
        line(0.04f, GY, 1.76f, GY).width(1.5f)
        curve(samples = 140) { t -> val x = r * t; P(OX + (x * s).toFloat(), GY - (f(x) * s).toFloat()) }.dashed().width(1f).tag("curve")
        val len = VEC0 + VEC_PER * v["v"].toFloat()
        val tip = P(OX + len * cos(th).toFloat(), GY - len * sin(th).toFloat())
        vec(OX, GY, tip.x, tip.y, 0.05f)
        lb.c(tip.x + 0.06f, tip.y - 0.02f, "v₀", 0.07f)
        angleArc(OX, GY, 0.1f, -degOf(th).toFloat(), degOf(th).toFloat())
        val pa = onCircle(OX, GY, 0.16f, -degOf(th).toFloat() / 2f)
        lb.c(pa.x + 0.012f, pa.y, "θ", 0.065f)
        dot(OX, GY, 0.02f)
        val px = OX + (r / 2 * s).toFloat(); val py = GY - (hmax(v) * s).toFloat()
        line(px, py, px, GY).dashed().width(0.6f)
        dot(px, py, 0.018f)
        lb.c(px + 0.07f, (py + GY) / 2f, "h", 0.07f)
        val end = OX + (r * s).toFloat()
        dim(OX, 0.93f, end, 0.93f)
        lb.c((OX + end) / 2f, 0.97f, "R", 0.065f)
    }

    override fun handles(v: ParamValues): List<ParamHandle> {
        val th = rad(v["theta"])
        val len = VEC0 + VEC_PER * v["v"].toFloat()
        return listOf(ParamHandle("v0", OX + len * cos(th).toFloat(), GY - len * sin(th).toFloat()))
    }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        if (key != "v0") return null
        val dx = (x - OX).toDouble(); val dy = (GY - y).toDouble()
        if (hypot(dx, dy) < 0.02) return null
        val th = degOf(atan2(dy, dx))
        val sp = (hypot(dx, dy) - VEC0) / VEC_PER
        return mapOf("theta" to th, "v" to sp)
    }
}

/** Enine dalga: y = A·sin(kx); λ = 2π/k. Tutamak: ilk tepe (yatay k, dikey A). */
internal object WaveModel : ParamModel(
    "sci.phys.transverse_wave", "Enine dalga", 2f, 1f,
    tags = listOf("dalga", "dalga boyu", "genlik", "tepe", "çukur", "lambda", "sinüs", "formül"),
) {
    override val defs = listOf(
        ParamDef("A", "A", "Genlik", default = 3.0, min = 0.2, max = 4.5, step = 0.5),
        ParamDef("k", "k", "Dalga sayısı", default = 1.0, min = 0.25, max = 6.0, step = 0.25),
    )
    override val derived = listOf(Derived("λ", "Dalga boyu", "", "k") { 2 * PI / it["k"] })
    override val curveVar = "y"
    override fun curve(v: ParamValues): (Double) -> Double { val a = v["A"]; val k = v["k"]; return { x -> a * sin(k * x) } }

    private const val X_RANGE = 12.0
    override fun fit(f: (Double) -> Double, v: ParamValues): Map<String, Double>? {
        if (abs(f(0.0)) > 1e-6) return null
        // ilk pozitif kök → k = π/x₀; genlik = f(π/(2k)) (negatifse işaret çevrilmez: uymaz)
        val n = 4000
        var prev = f(1e-6)
        var x0 = Double.NaN
        for (i in 1..n) {
            val x = X_RANGE * 2 * i / n
            val y = f(x)
            if (prev.isFinite() && y.isFinite() && (prev < 0) != (y < 0)) {
                var a = x - X_RANGE * 2 / n; var b = x
                repeat(60) { val m = (a + b) / 2; if ((f(m) < 0) == (f(a) < 0)) a = m else b = m }
                x0 = (a + b) / 2
                break
            }
            prev = y
        }
        if (x0.isNaN()) return null
        val k = PI / x0
        val a = f(PI / (2 * k))
        if (a <= 0) return null
        for (x in listOf(0.37, 1.9, 4.4, 7.3)) if (abs(f(x) - a * sin(k * x)) > 1e-6 * (1 + a)) return null
        return mapOf("A" to a, "k" to k)
    }

    override fun curveText(v: ParamValues) = "y = ${n(v["A"])}·sin(${n(v["k"], 3)}x)"

    override fun label(v: ParamValues) = listOf("y = A·sin(kx)", "A = ${n(v["A"])}; k = ${n(v["k"], 2)}; λ = ${n(2 * PI / v["k"])}")

    private const val AX = 0.56f
    private const val X0 = 0.1f
    private const val XS = 1.8 / X_RANGE
    private const val YS = 0.08

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val f = curve(v)
        val a = v["A"]; val k = v["k"]
        line(0.04f, AX, 1.96f, AX).dashed().width(0.7f)
        curve(samples = 240) { t -> val x = X_RANGE * t; P(X0 + (x * XS).toFloat(), AX - (f(x) * YS).toFloat()) }.width(1.6f).tag("curve")
        val lam = 2 * PI / k
        val c1 = PI / (2 * k)
        val c2 = c1 + lam
        val top = AX - (a * YS).toFloat()
        if (c2 <= X_RANGE) {
            val xa = X0 + (c1 * XS).toFloat(); val xb = X0 + (c2 * XS).toFloat()
            line(xa, top - 0.08f, xa, top - 0.01f).dashed().width(0.6f)
            line(xb, top - 0.08f, xb, top - 0.01f).dashed().width(0.6f)
            dim(xa, top - 0.05f, xb, top - 0.05f)
            lb.c((xa + xb) / 2f, top - 0.1f, "λ", 0.07f)
        }
        val ax = 1.9f
        dim(ax, AX, ax, AX + (a * YS).toFloat())
        lb.c(ax - 0.06f, AX + (a * YS / 2).toFloat(), "A", 0.07f)
    }

    override fun handles(v: ParamValues): List<ParamHandle> {
        val c1 = PI / (2 * v["k"])
        return listOf(ParamHandle("crest", X0 + (c1 * XS).toFloat(), AX - (v["A"] * YS).toFloat()))
    }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        if (key != "crest") return null
        val xx = ((x - X0) / XS).coerceAtLeast(0.05)
        return mapOf("k" to PI / (2 * xx), "A" to ((AX - y) / YS))
    }
}

/** Yay–kütle: F = k·x. Tutamak: kütle (yatay sürükleme uzamayı değiştirir). */
internal object SpringModel : ParamModel(
    "sci.phys.spring_mass", "Yay–kütle sistemi", 1.8f, 0.9f,
    tags = listOf("yay", "esneklik", "hooke", "salınım", "kütle", "mekanik", "formül"),
) {
    override val defs = listOf(
        ParamDef("k", "k", "Yay sabiti", default = 50.0, min = 1.0, max = 500.0, step = 5.0, unit = "N/m"),
        ParamDef("x", "x", "Uzama", default = 0.2, min = 0.0, max = 0.5, step = 0.05, unit = "m"),
    )
    override val derived = listOf(Derived("F", "Yay kuvveti", "N", "x") { it["k"] * it["x"] })
    override val curveVar = "F"
    override fun curve(v: ParamValues): (Double) -> Double { val k = v["k"]; return { x -> k * x } }
    override fun fit(f: (Double) -> Double, v: ParamValues): Map<String, Double>? {
        val k = f(1.0)
        if (abs(f(0.0)) > 1e-9 || abs(f(0.3) - 0.3 * k) > 1e-9 * (1 + abs(k))) return null
        return mapOf("k" to k)
    }
    override fun curveText(v: ParamValues) = "F = ${n(v["k"])}x"
    override fun editText(raw: Map<String, String>?): String { val v = values(raw); return "k = ${n(v["k"])}; x = ${n(v["x"])}" }
    override fun label(v: ParamValues) = listOf("F = k·x", "k = ${n(v["k"])} N/m; x = ${n(v["x"])} m; F = ${n(v["k"] * v["x"])} N")
    override val labelRight = true

    private const val FLOOR = 0.72f
    private const val REST = 0.82f
    private const val XS = 0.9f

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val bx = REST + XS * v.f("x")
        hatch(0.1f, FLOOR, 0.1f, 0.1f, side = -1f, gap = 0.06f, len = 0.045f)
        hatch(0.1f, FLOOR, 1.72f, FLOOR, side = 1f, gap = 0.06f, len = 0.045f)
        coil(0.1f, 0.55f, bx, 0.55f, turns = 11, r = 0.075f, lead = 0.07f, tilt = 0.4f)
        rect(bx, 0.38f, 0.34f, FLOOR - 0.38f).fill(Sc.BLOCK).width(1.2f)
        lb.c(bx + 0.17f, 0.55f, "m", 0.09f)
        lb.c((0.1f + bx) / 2f, 0.4f, "k", 0.075f)
        line(REST, 0.22f, REST, FLOOR).dashed().width(0.7f)
        if (v["x"] > 0.005) {
            vec(REST, 0.29f, bx, 0.29f, 0.03f, width = 0.9f)
            lb.c((REST + bx) / 2f, 0.23f, "x", 0.07f)
            // geri getirici kuvvet: boyu F ile orantılı (en çok 0,3)
            val fl = (0.02f + 0.0012f * (v["k"] * v["x"]).toFloat()).coerceAtMost(0.3f)
            vec(bx + 0.34f, 0.47f, bx + 0.34f - fl - 0.04f, 0.47f, 0.035f, color = Sc.RED, width = 1f)
            lb.c(bx + 0.34f - fl / 2f - 0.02f, 0.43f, "F", 0.065f, Sc.RED)
        }
    }

    override fun handles(v: ParamValues) = listOf(ParamHandle("mass", REST + XS * v.f("x") + 0.17f, 0.55f))

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? =
        if (key == "mass") mapOf("x" to ((x - 0.17f - REST) / XS).toDouble()) else null
}

/** Basit sarkaç: T = 2π·√(L/g). Tutamak: top (uzaklık L, yön θ). */
internal object PendulumModel : ParamModel(
    "sci.phys.pendulum", "Basit sarkaç", 1f, 1f,
    tags = listOf("sarkaç", "salınım", "periyot", "genlik", "mekanik", "formül"),
) {
    override val defs = listOf(
        ParamDef("L", "L", "İp uzunluğu", default = 2.0, min = 0.2, max = 2.2, step = 0.1, unit = "m"),
        ParamDef("theta", "θ", "Genlik açısı", ParamKind.ANGLE, default = 32.0, min = 0.0, max = 70.0, step = 2.0, unit = "°"),
        ParamDef("g", "g", "Yer çekimi ivmesi", default = 9.8, min = 0.5, max = 30.0, step = 0.1, unit = "m/s²", advanced = true),
    )
    override val derived = listOf(Derived("T", "Periyot", "s", "L") { 2 * PI * sqrt(it["L"] / it["g"]) })
    override fun label(v: ParamValues) = listOf("T = 2π·√(L/g)", "L = ${n(v["L"])} m; θ = ${n(v["theta"], 0)}°", "T = ${n(2 * PI * sqrt(v["L"] / v["g"]))} s")
    override val labelRight = true
    override val labelSize = 0.055f

    private const val PX = 0.5f
    private const val PY = 0.1f
    private const val LS = 0.34f
    private const val BR = 0.055f

    /** Çizim ölçeği: uzun ip ve büyük genlikte salınım kutuya sığsın diye küçültülür (sürükleme başlangıç ölçeğiyle). */
    private fun scale(v: ParamValues): Float {
        val l = LS * v.f("L")
        val sx = (PX - BR - 0.02f) / (l * sin(Math.toRadians(v["theta"])).toFloat()).coerceAtLeast(1e-4f)
        val sy = (1f - BR - 0.03f - PY) / l
        return minOf(1f, sx, sy)
    }

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val l = LS * v.f("L") * scale(v); val amp = v.f("theta")
        hatch(PX - 0.15f, PY, PX + 0.15f, PY, side = -1f, gap = 0.05f, len = 0.04f)
        if (amp > 0.5f) arc(PX, PY, l, l, 90f - amp, 2 * amp).dotted().width(0.9f)
        line(PX, PY, PX, PY + l + BR + 0.03f).dashed().width(0.7f)
        fun bob(phi: Float): P = P(PX + l * sin(Math.toRadians(phi.toDouble())).toFloat(), PY + l * cos(Math.toRadians(phi.toDouble())).toFloat())
        val left = bob(-amp)
        line(PX, PY, left.x, left.y).dashed().width(0.8f)
        circle(left.x, left.y, BR).dashed().width(0.8f)
        val right = bob(amp)
        line(PX, PY, right.x, right.y).width(1.1f)
        circle(right.x, right.y, BR).fill("#90A4AE").width(1.2f)
        shine(right.x, right.y, BR)
        dot(PX, PY, 0.014f)
        if (amp > 3f) {
            angleArc(PX, PY, 0.14f, 90f - amp, amp)
            val pt = onCircle(PX, PY, 0.2f, 90f - amp / 2f)
            lb.c(pt.x, pt.y, "θ", 0.065f)
        }
        val mid = P((PX + right.x) / 2f + 0.05f, (PY + right.y) / 2f - 0.03f)
        lb.c(mid.x, mid.y, "L", 0.065f)
    }

    override fun handles(v: ParamValues): List<ParamHandle> {
        val l = LS * v.f("L") * scale(v); val a = Math.toRadians(v["theta"])
        return listOf(ParamHandle("bob", PX + l * sin(a).toFloat(), PY + l * cos(a).toFloat()))
    }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        if (key != "bob") return null
        val dx = (x - PX).toDouble(); val dy = (y - PY).toDouble()
        if (dy <= 0.01) return null
        return mapOf("L" to hypot(dx, dy) / (LS * scale(v)), "theta" to degOf(atan2(dx, dy)))
    }
}

/**
 * İnce mercek: 1/f = 1/do + 1/di (yakınsak f > 0, ıraksak f < 0). Tutamaklar: cisim (do) ve odak (f).
 */
internal class LensModel(id: String, title: String, private val converging: Boolean, w: Float, kind: String) : ParamModel(
    id, title, w, 1f,
    tags = listOf(kind, "mercek", "odak", "ışın", "ışık", "kırılma", "görüntü", "optik", "formül"),
) {
    override val defs = listOf(
        ParamDef("f", "f", "Odak uzaklığı", default = 10.0, min = 2.0, max = 40.0, step = 1.0, unit = "cm"),
        ParamDef("do", "do", "Cismin uzaklığı", default = 25.0, min = 1.0, max = 80.0, step = 1.0, unit = "cm"),
    )
    private fun fs(v: ParamValues) = if (converging) v["f"] else -v["f"]
    /** Görüntü uzaklığı (sanal görüntüde negatif; cisim odakta sonsuz). */
    fun di(v: ParamValues): Double {
        val inv = 1 / fs(v) - 1 / v["do"]
        return if (abs(inv) < 1e-9) Double.POSITIVE_INFINITY else 1 / inv
    }
    override val derived = listOf(
        Derived("di", "Görüntünün uzaklığı", "cm", "do") { di(it) },
        Derived("m", "Büyütme", "", "do") { -di(it) / it["do"] },
    )
    override fun label(v: ParamValues): List<String> {
        val d = di(v)
        return listOf(
            "1/f = 1/do + 1/di",
            "f = ${n(fs(v), 1)} cm; do = ${n(v["do"], 1)} cm",
            if (d.isInfinite()) "di = ∞" else "di = ${n(d, 1)} cm; m = ${n(-d / v["do"])}",
        )
    }
    override val labelSize = 0.055f
    private val cx get() = w / 2f
    private val ay = 0.62f
    private val ho = 0.2f

    private fun scale(v: ParamValues): Float {
        val d = di(v)
        val extent = max(max(v["do"], v["f"] * 1.25), if (d.isFinite()) min(abs(d), 3 * v["do"]) else 0.0)
        return ((w / 2f - 0.1f) / extent).toFloat()
    }

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val s = scale(v)
        val f = v.f("f"); val dob = v.f("do")
        val d = di(v)
        line(0.04f, ay, w - 0.04f, ay).width(0.8f)
        // mercek gövdesi
        if (converging) {
            path(closed = true) { moveTo(cx, ay - 0.36f); quadTo(cx + 0.09f, ay, cx, ay + 0.36f); quadTo(cx - 0.09f, ay, cx, ay - 0.36f) }.fill(Sc.LENS).width(1.2f)
        } else {
            path(closed = true) {
                moveTo(cx - 0.06f, ay - 0.36f); lineTo(cx + 0.06f, ay - 0.36f); quadTo(cx + 0.01f, ay, cx + 0.06f, ay + 0.36f)
                lineTo(cx - 0.06f, ay + 0.36f); quadTo(cx - 0.01f, ay, cx - 0.06f, ay - 0.36f)
            }.fill(Sc.LENS).width(1.2f)
        }
        for (sg in listOf(-1f, 1f)) {
            val fx = cx + sg * f * s
            if (fx > 0.02f && fx < w - 0.02f) { dot(fx, ay, 0.013f); lb.c(fx, ay + 0.05f, if (sg < 0) "F" else "F′", 0.06f) }
        }
        // cisim
        val ox = cx - dob * s
        vec(ox, ay, ox, ay - ho, 0.035f, color = Sc.BLUE, width = 1.4f)
        lb.c(ox, ay + 0.05f, "do", 0.055f)
        val top = P(ox, ay - ho)
        if (d.isFinite()) {
            val m = (-d / v["do"]).toFloat()
            val ix = (cx + d * s).toFloat()
            val ih = (ho * m).coerceIn(-0.55f, 0.55f)
            val real = d > 0
            val img = vec(ix, ay, ix, ay - ih, 0.035f, color = "#8E24AA", width = 1.4f)
            if (!real) img.dashed()
            // ışınlar: paralel → odaktan, merkezden düz
            val lensTop = P(cx, top.y)
            line(top.x, top.y, lensTop.x, lensTop.y).stroke(Sc.RAY).width(1f)
            val toImg = P(ix, ay - ih)
            if (real) {
                line(lensTop.x, lensTop.y, toImg.x, toImg.y).stroke(Sc.RAY).width(1f)
                line(top.x, top.y, toImg.x, toImg.y).stroke(Sc.RAY).width(0.8f)
            } else {
                // sanal görüntü: ışınların uzantıları kesikli
                val dir = unit(lensTop.x - toImg.x, lensTop.y - toImg.y)
                line(lensTop.x, lensTop.y, lensTop.x + dir.x * 0.5f, lensTop.y + dir.y * 0.5f).stroke(Sc.RAY).width(1f)
                line(lensTop.x, lensTop.y, toImg.x, toImg.y).stroke(Sc.RAY).width(0.7f).dashed()
                val dir2 = unit(cx - top.x, ay - top.y)
                line(top.x, top.y, cx + dir2.x * 0.5f, ay + dir2.y * 0.5f).stroke(Sc.RAY).width(0.8f)
                line(cx, ay, toImg.x, toImg.y).stroke(Sc.RAY).width(0.7f).dashed()
            }
            lb.c(ix, ay + 0.05f, "di", 0.055f)
        } else {
            // cisim odakta: ışınlar paralel çıkar
            line(top.x, top.y, cx, top.y).stroke(Sc.RAY).width(1f)
            line(cx, top.y, w - 0.05f, top.y + (w / 2f - 0.05f) * ho / max(1e-3f, f * s)).stroke(Sc.RAY).width(1f)
        }
    }

    override fun handles(v: ParamValues): List<ParamHandle> {
        val s = scale(v)
        return listOf(
            ParamHandle("object", cx - v.f("do") * s, ay - ho),
            ParamHandle("focus", cx + v.f("f") * s, ay),
        )
    }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        val s = scale(v)
        return when (key) {
            "object" -> mapOf("do" to ((cx - x) / s).toDouble())
            "focus" -> mapOf("f" to (abs(x - cx) / s).toDouble())
            else -> null
        }
    }
}

internal val ConvexLensModel = LensModel("sci.phys.convex_lens", "Yakınsak mercek", true, 1.8f, "ince kenarlı mercek")
internal val ConcaveLensModel = LensModel("sci.phys.concave_lens", "Iraksak mercek", false, 1.6f, "kalın kenarlı mercek")

/** Eğik düzlem: a = g·(sinθ − μ·cosθ). Tutamak: düzlemin tepesi (θ). */
internal object InclineModel : ParamModel(
    "sci.phys.inclined_plane", "Eğik düzlem", 1.6f, 1f,
    tags = listOf("kuvvet", "sürtünme", "ağırlık", "normal kuvvet", "blok", "mekanik", "formül"),
) {
    override val defs = listOf(
        ParamDef("theta", "θ", "Eğim açısı", ParamKind.ANGLE, default = 30.0, min = 5.0, max = 60.0, step = 5.0, unit = "°"),
        ParamDef("mu", "μ", "Sürtünme katsayısı", default = 0.0, min = 0.0, max = 1.0, step = 0.05),
        ParamDef("m", "m", "Kütle", default = 2.0, min = 0.1, max = 100.0, step = 0.5, unit = "kg"),
        ParamDef("g", "g", "Yer çekimi ivmesi", default = 9.8, min = 0.5, max = 30.0, step = 0.1, unit = "m/s²", advanced = true),
    )
    private fun acc(v: ParamValues) = max(0.0, v["g"] * (sin(rad(v["theta"])) - v["mu"] * cos(rad(v["theta"]))))
    override val derived = listOf(
        Derived("a", "İvme", "m/s²", "theta") { acc(it) },
        Derived("N", "Normal kuvvet", "N", "theta") { it["m"] * it["g"] * cos(rad(it["theta"])) },
    )
    override fun label(v: ParamValues) = listOf(
        "a = g·(sinθ − μ·cosθ)",
        "θ = ${n(v["theta"], 0)}°; μ = ${n(v["mu"])}; a = ${n(acc(v))} m/s²",
    )
    override val labelSize = 0.055f

    private val p0 = P(0.1f, 0.9f)
    private const val BASE = 1.35f

    private fun topY(th: Double) = (0.9 - BASE * tan(rad(th))).toFloat()

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val th = v["theta"]
        // çok dik açıda düzlem kutuya sığsın diye taban kısalır
        val base = min(BASE.toDouble(), 0.72 / tan(rad(th))).toFloat()
        val top = P(p0.x + base, (0.9 - base * tan(rad(th))).toFloat())
        hatch(0.02f, 0.9f, 1.58f, 0.9f, side = 1f, gap = 0.06f, len = 0.045f)
        poly(p0.x, p0.y, top.x, 0.9f, top.x, top.y, closed = true).fill(Sc.GROUND).width(1.3f)
        val u = P(cos(rad(th)).toFloat(), -sin(rad(th)).toFloat())
        val nv = P(u.y, -u.x)
        val along = min(0.72f, hypot(top.x - p0.x, top.y - p0.y) * 0.55f)
        val c = P(p0.x + u.x * along + nv.x * 0.1f, p0.y + u.y * along + nv.y * 0.1f)
        group(rotate = -th.toFloat(), px = c.x, py = c.y) { rect(c.x - 0.1f, c.y - 0.1f, 0.2f, 0.2f).fill(Sc.BLOCK).width(1.2f) }
        val g = 0.3f
        vec(c.x, c.y, c.x, c.y + g, 0.045f)
        val nlen = 0.3f * cos(rad(th)).toFloat()
        vec(c.x, c.y, c.x + nv.x * nlen, c.y + nv.y * nlen, 0.045f)
        val fpar = 0.3f * sin(rad(th)).toFloat()
        vec(c.x, c.y, c.x - u.x * fpar, c.y - u.y * fpar, 0.04f, color = Sc.RED, width = 1f)
        dot(c.x, c.y, 0.012f)
        lb.c(c.x + 0.05f, c.y + g + 0.01f, "G", 0.065f)
        lb.c(c.x + nv.x * nlen - 0.04f, c.y + nv.y * nlen + 0.01f, "N", 0.065f)
        angleArc(p0.x, p0.y, 0.2f, -th.toFloat(), th.toFloat())
        val pt = onCircle(p0.x, p0.y, 0.28f, -th.toFloat() / 2.4f)
        lb.c(pt.x, pt.y, "θ", 0.065f)
    }

    override fun handles(v: ParamValues): List<ParamHandle> {
        val th = v["theta"]
        val base = min(BASE.toDouble(), 0.72 / tan(rad(th))).toFloat()
        return listOf(ParamHandle("top", p0.x + base, (0.9 - base * tan(rad(th))).toFloat()))
    }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        if (key != "top") return null
        val dx = (x - p0.x).toDouble().coerceAtLeast(0.05); val dy = (p0.y - y).toDouble().coerceAtLeast(0.0)
        return mapOf("theta" to degOf(atan2(dy, dx)))
    }
}

/** Ohm yasası grafiği: V = I·R (doğru). Tutamak: doğrunun ucu (eğim R). */
internal object OhmModel : ParamModel(
    "sci.phys.ohm_graph", "Ohm yasası grafiği (V = I·R)", 1.3f, 1f,
    tags = listOf("ohm", "direnç", "gerilim", "akım", "elektrik", "grafik", "formül"),
) {
    override val defs = listOf(
        ParamDef("R", "R", "Direnç", default = 10.0, min = 0.5, max = 100.0, step = 1.0, unit = "Ω"),
        ParamDef("Imax", "I_max", "En büyük akım", default = 2.0, min = 0.5, max = 10.0, step = 0.5, unit = "A"),
    )
    override val derived = listOf(Derived("V", "Gerilim (I_max'ta)", "V", "R") { it["R"] * it["Imax"] })
    override val curveVar = "V"
    override val curveArg = "I"
    override fun curve(v: ParamValues): (Double) -> Double { val r = v["R"]; return { i -> r * i } }
    override fun fit(f: (Double) -> Double, v: ParamValues): Map<String, Double>? {
        val r = f(1.0)
        if (abs(f(0.0)) > 1e-9 || abs(f(2.5) - 2.5 * r) > 1e-9 * (1 + abs(r))) return null
        return mapOf("R" to r)
    }
    override fun curveText(v: ParamValues) = "V = ${n(v["R"])}·I"
    override fun label(v: ParamValues) = listOf("V = I·R", "R = ${n(v["R"])} Ω")
    override val labelRight = true

    private const val L = 0.18f
    private const val B = 0.86f
    private const val R_ = 1.2f
    private const val T = 0.1f

    private fun vmax(v: ParamValues) = Fmt2.nice(v["R"] * v["Imax"] * 1.1)

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val imax = v["Imax"]; val vm = vmax(v)
        vec(L, B, R_ + 0.06f, B, 0.035f, width = 1f)
        vec(L, B, L, T - 0.04f, 0.035f, width = 1f)
        lb.c(R_ + 0.05f, B + 0.05f, "I (A)", 0.055f)
        lb.c(L - 0.02f, T - 0.08f, "V (V)", 0.055f)
        fun X(i: Double) = (L + (i / imax) * (R_ - L - 0.08f)).toFloat()
        fun Y(u: Double) = (B - (u / vm) * (B - T)).toFloat()
        for (k in 1..4) {
            val i = imax * k / 4
            line(X(i), B - 0.012f, X(i), B + 0.012f).width(0.8f)
            lb.c(X(i), B + 0.045f, n(i), 0.045f)
            val u = vm * k / 4
            line(L - 0.012f, Y(u), L + 0.012f, Y(u)).width(0.8f)
            lb.r(L - 0.02f, Y(u), n(u, 1), 0.045f)
        }
        line(X(0.0), Y(0.0), X(imax), Y(v["R"] * imax)).stroke(Sc.BLUE).width(1.8f).tag("curve")
        dot(X(imax), Y(v["R"] * imax), 0.014f, Sc.BLUE)
    }

    override fun handles(v: ParamValues): List<ParamHandle> {
        val imax = v["Imax"]; val vm = vmax(v)
        return listOf(ParamHandle("end", (L + (R_ - L - 0.08f)).toFloat(), (B - (v["R"] * imax / vm) * (B - T)).toFloat()))
    }

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        if (key != "end") return null
        val vm = vmax(v)
        val u = (B - y) / (B - T) * vm
        return mapOf("R" to u / v["Imax"])
    }
}

/** Eksen ölçeği için yuvarlak sayılar. */
internal object Fmt2 {
    fun nice(x: Double): Double {
        if (x <= 0) return 1.0
        val e = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(x)))
        val m = x / e
        val k = when { m <= 1 -> 1.0; m <= 2 -> 2.0; m <= 2.5 -> 2.5; m <= 4 -> 4.0; m <= 5 -> 5.0; else -> 10.0 }
        return k * e
    }
}
