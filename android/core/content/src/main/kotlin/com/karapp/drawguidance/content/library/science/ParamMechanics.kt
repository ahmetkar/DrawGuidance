package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * v46 (4) formülle yeniden çizilen basit makineler ve serbest cisim diyagramı. Kimlikler eski sabit çizimlerle aynıdır
 * ("sci.phys.free_body"…); kutu oranları değişmedi, eski kayıtlar varsayılan değerlerle açılır. Kuvvet oklarının boyu
 * kuvvetlerle orantılıdır (şekildeki en büyük kuvvet en uzun ok); değerler değişince şekil yeniden çizilir.
 */

private fun rad(d: Double) = d * PI / 180.0
private fun degOf(r: Double) = r * 180.0 / PI
private fun n(v: Double, d: Int = 2) = ParamModel.num(v, d)

/** Serbest cisim diyagramı: yatay zeminde blok; F (açılı), ağırlık G, normal N, sürtünme Fs; F·cosα − Fs = m·a. */
internal object FreeBodyModel : ParamModel(
    "sci.phys.free_body", "Serbest cisim diyagramı", 1.3f, 1f,
    tags = listOf("kuvvet", "normal kuvvet", "ağırlık", "sürtünme", "net kuvvet", "ivme", "mekanik", "formül"),
) {
    override val defs = listOf(
        ParamDef("m", "m", "Kütle", default = 2.0, min = 0.1, max = 500.0, step = 0.5, unit = "kg"),
        ParamDef("F", "F", "Uygulanan kuvvet", default = 15.0, min = 0.0, max = 5000.0, step = 1.0, unit = "N"),
        ParamDef("alpha", "α", "Kuvvetin açısı", ParamKind.ANGLE, default = 0.0, min = -80.0, max = 80.0, step = 5.0, unit = "°"),
        ParamDef("mu", "μ", "Sürtünme katsayısı", default = 0.3, min = 0.0, max = 1.5, step = 0.05),
        ParamDef("g", "g", "Yer çekimi ivmesi", default = 9.8, min = 0.5, max = 30.0, step = 0.1, unit = "m/s²", advanced = true),
    )

    /** Kuvvetler: ağırlık, normal, sürtünme (statikte uygulanan yatay kuvvete eşit), ivme. */
    data class Forces(val g: Double, val n: Double, val fx: Double, val fy: Double, val fs: Double, val a: Double, val moving: Boolean)

    fun forces(v: ParamValues): Forces {
        val g = v["m"] * v["g"]
        val fx = v["F"] * cos(rad(v["alpha"]))
        val fy = v["F"] * sin(rad(v["alpha"]))
        val nf = max(0.0, g - fy)
        val fsMax = v["mu"] * nf
        val moving = fx > fsMax + 1e-9
        val fs = if (moving) fsMax else fx
        val a = if (moving) (fx - fs) / v["m"] else 0.0
        return Forces(g, nf, fx, fy, fs, a, moving)
    }

    override val derived = listOf(
        Derived("G", "Ağırlık", "N", "m") { it["m"] * it["g"] },
        Derived("N", "Normal kuvvet", "N", "m") { forces(it).n },
        Derived("Fs", "Sürtünme kuvveti", "N", "mu") { forces(it).fs },
        Derived("a", "İvme", "m/s²", "F") { forces(it).a },
    )

    override fun label(v: ParamValues): List<String> {
        val f = forces(v)
        return listOf(
            "F·cosα − Fs = m·a",
            "G = ${n(f.g)} N; N = ${n(f.n)} N",
            "Fs = ${n(f.fs)} N; a = ${n(f.a)} m/s²",
        )
    }
    override val labelSize = 0.05f

    private const val CX = 0.7f
    private const val CY = 0.6f
    private const val GROUND = 0.72f
    private const val MAXLEN = 0.3f

    private fun scale(f: Forces, v: ParamValues): Float {
        val big = maxOf(f.g, f.n, f.fs, v["F"], 1e-6)
        return (MAXLEN / big).toFloat()
    }

    private fun fTip(v: ParamValues): P {
        val f = forces(v); val s = scale(f, v)
        val len = (v["F"] * s).toFloat()
        val a = rad(v["alpha"])
        return P(CX + 0.2f + len * cos(a).toFloat(), CY - len * sin(a).toFloat())
    }

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val f = forces(v); val s = scale(f, v)
        hatch(0.08f, GROUND, 1.22f, GROUND, side = 1f, gap = 0.06f, len = 0.045f)
        rect(CX - 0.2f, CY - 0.12f, 0.4f, 0.24f).fill(Sc.BLOCK).width(1.2f)
        lb.c(CX - 0.12f, CY, "m", 0.07f)
        fun arrow(x1: Float, y1: Float, len: Float, ux: Float, uy: Float, label: String, color: String? = null) {
            if (len < 0.02f) return
            vec(x1, y1, x1 + ux * len, y1 + uy * len, 0.04f, color = color, width = 1.2f)
            lb.c(x1 + ux * (len + 0.05f) + (if (ux == 0f) 0.07f else 0f), y1 + uy * (len + 0.05f), label, 0.06f, color)
        }
        // ağırlık ve normal kuvvet bloğun ortasından
        arrow(CX - 0.05f, CY, (f.g * s).toFloat(), 0f, 1f, "G")
        arrow(CX + 0.05f, CY - 0.12f, (f.n * s).toFloat(), 0f, -1f, "N", Sc.BLUE)
        // uygulanan kuvvet sağ yüzden, sürtünme zemin hizasında sola
        if (v["F"] > 1e-9) {
            val tip = fTip(v)
            vec(CX + 0.2f, CY, tip.x, tip.y, 0.04f, color = Sc.RED, width = 1.3f)
            lb.c(tip.x + 0.04f, tip.y - 0.05f, "F", 0.06f, Sc.RED)
            if (kotlin.math.abs(v["alpha"]) > 2) {
                line(CX + 0.2f, CY, CX + 0.36f, CY).dashed().width(0.6f)
                angleArc(CX + 0.2f, CY, 0.09f, if (v["alpha"] > 0) -v.f("alpha") else 0f, kotlin.math.abs(v.f("alpha")))
                lb.c(CX + 0.36f, CY + (if (v["alpha"] > 0) -0.04f else 0.05f), "α", 0.05f)
            }
        }
        arrow(CX - 0.2f, GROUND - 0.02f, (f.fs * s).toFloat(), -1f, 0f, "Fs", Sc.GREEN)
        dot(CX, CY, 0.012f)
        // ivme yönü (hareket varsa)
        if (f.moving) {
            vec(CX - 0.2f, CY - 0.2f, CX + 0.02f, CY - 0.2f, 0.035f, width = 0.9f)
            lb.c(CX - 0.09f, CY - 0.26f, "a", 0.055f)
        }
    }

    override fun handles(v: ParamValues): List<ParamHandle> = listOf(fTip(v).let { ParamHandle("force", it.x, it.y) })

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? {
        if (key != "force") return null
        val dx = (x - CX - 0.2f).toDouble(); val dy = (CY - y).toDouble()
        val len = hypot(dx, dy)
        if (len < 0.01 || dx <= 0) return null
        val s = scale(forces(v), v)
        return mapOf("F" to len / s, "alpha" to degOf(atan2(dy, dx)).coerceIn(-80.0, 80.0))
    }
}

/** Makara tekerleği: gövde, oluk ve mil. */
private fun VectorBuilder.pulleyWheel(cx: Float, cy: Float, r: Float) {
    circle(cx, cy, r).fill("#E3E7EC").width(1.2f)
    circle(cx, cy, r * 0.78f).width(0.6f)
    circle(cx, cy, r * 0.2f).fill(Sc.METAL_DARK).width(0.8f)
}

/** Yükün çizim boyu: ağırlıkla (karekökle) büyür, kutuya sığar. */
private fun blockSize(p: Double): Float = (0.14f + 0.05f * sqrt(p / 100.0).toFloat()).coerceIn(0.12f, 0.3f)

/** Sabit makara: kuvvetten kazanç yok, yön değişir: F = P; ip h kadar çekilince yük h kadar yükselir. */
internal object FixedPulleyModel : ParamModel(
    "sci.phys.fixed_pulley", "Sabit makara", 1f, 1.15f,
    tags = listOf("makara", "basit makine", "ip", "kuvvet", "yük", "formül"),
) {
    override val defs = listOf(
        ParamDef("P", "P", "Yükün ağırlığı", default = 100.0, min = 1.0, max = 10000.0, step = 10.0, unit = "N"),
        ParamDef("h", "h", "Yükselme", default = 1.0, min = 0.0, max = 100.0, step = 0.5, unit = "m"),
    )
    override val derived = listOf(
        Derived("F", "Kuvvet", "N", "P") { it["P"] },
        Derived("s", "Çekilen ip", "m", "h") { it["h"] },
    )
    override fun label(v: ParamValues) = listOf("F = P", "F = ${n(v["P"])} N; ip = ${n(v["h"])} m")
    override val labelSize = 0.045f

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val cx = 0.5f; val top = 0.16f; val cy = 0.38f; val r = 0.15f
        hatch(0.3f, top, 0.7f, top, side = -1f, gap = 0.05f, len = 0.04f)
        bar(cx, top, cx, cy, 0.03f).fill(Sc.METAL).width(1f)
        pulleyWheel(cx, cy, r)
        arc(cx, cy, r, r, 180f, 180f).width(1.5f)
        val bs = blockSize(v["P"])
        val by = 0.82f
        line(cx - r, cy, cx - r, by).width(1.5f)
        line(cx + r, cy, cx + r, 0.84f).width(1.5f)
        // F oku: sabit makarada F = P, ok boyu yükün ağırlık okuyla aynı
        val len = 0.2f
        vec(cx + r, 0.84f, cx + r, 0.84f + len, 0.05f, color = Sc.RED)
        lb.c(cx + r + 0.08f, 0.96f, "F", 0.08f, Sc.RED)
        rect(cx - r - bs / 2f, by, bs, bs).fill(Sc.BLOCK).width(1.2f)
        lb.c(cx - r, by + bs / 2f, "P", 0.07f)
        // yükselme h: kesik çizgiyle yükün varacağı yer
        if (v["h"] > 0.01) {
            val rise = bs + 0.04f
            rect(cx - r - bs / 2f, by - rise, bs, bs).dashed().width(0.6f)
            dim(cx - r - bs / 2f - 0.06f, by + bs, cx - r - bs / 2f - 0.06f, by + bs - rise)
            lb.c(cx - r - bs / 2f - 0.12f, by + bs - rise / 2f, "h", 0.06f)
        }
    }
}

/**
 * Hareketli makara (palanga): yük iki iple taşınır, F = (P + Pm)/2 (Pm makaranın ağırlığı); yükü h kadar kaldırmak
 * için ip 2h çekilir. Sabit makara yalnız yönü değiştirir.
 */
internal object MovablePulleyModel : ParamModel(
    "sci.phys.movable_pulley", "Hareketli makara (palanga)", 1f, 1.15f,
    tags = listOf("makara", "palanga", "basit makine", "ip", "kuvvet", "yük", "formül"),
) {
    override val defs = listOf(
        ParamDef("P", "P", "Yükün ağırlığı", default = 100.0, min = 1.0, max = 10000.0, step = 10.0, unit = "N"),
        ParamDef("Pm", "Pm", "Makaranın ağırlığı", default = 0.0, min = 0.0, max = 1000.0, step = 5.0, unit = "N"),
        ParamDef("h", "h", "Yükselme", default = 1.0, min = 0.0, max = 100.0, step = 0.5, unit = "m"),
    )
    fun force(v: ParamValues) = (v["P"] + v["Pm"]) / 2.0
    override val derived = listOf(
        Derived("F", "Kuvvet", "N", "P") { force(it) },
        Derived("s", "Çekilen ip", "m", "h") { 2 * it["h"] },
    )
    override fun label(v: ParamValues) = listOf(
        "F = (P + Pm)/2",
        "F = ${n(force(v))} N; ip = ${n(2 * v["h"])} m",
    )
    override val labelSize = 0.045f

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val r = 0.11f
        val top = 0.16f
        val fx = 0.64f; val fy = 0.32f
        val mx = 0.4f; val my = 0.66f
        hatch(0.24f, top, 0.9f, top, side = -1f, gap = 0.05f, len = 0.04f)
        bar(fx, top, fx, fy, 0.025f).fill(Sc.METAL).width(1f)
        bar(mx, my, mx, 0.83f, 0.022f).fill(Sc.METAL).width(1f)
        pulleyWheel(fx, fy, r)
        pulleyWheel(mx, my, r)
        line(mx - r, top, mx - r, my).width(1.5f)
        arc(mx, my, r, r, 180f, -180f).width(1.5f)
        line(mx + r, my, fx - r, fy).width(1.5f)
        arc(fx, fy, r, r, 180f, 180f).width(1.5f)
        line(fx + r, fy, fx + r, 0.86f).width(1.5f)
        dot(mx - r, top, 0.014f)
        // F ve P okları orantılı: P oku yükten aşağı, F oku ip ucunda
        val p = v["P"] + v["Pm"]
        val s = 0.22f / max(p, 1e-6).toFloat()
        val fl = max(0.05f, (force(v) * s).toFloat())
        vec(fx + r, 0.86f, fx + r, 0.86f + fl, 0.045f, color = Sc.RED)
        lb.c(fx + r + 0.08f, 0.86f + fl / 2f, "F", 0.08f, Sc.RED)
        val bs = min(blockSize(v["P"]), 0.22f)
        rect(mx - bs / 2f, 0.83f, bs, bs).fill(Sc.BLOCK).width(1.2f)
        lb.c(mx, 0.83f + bs / 2f, "P", 0.07f)
        // iki ip yükü taşır: 2F = P + Pm
        lb.c(mx - r - 0.06f, (top + my) / 2f, "F", 0.055f, Sc.RED)
        lb.c((mx + fx) / 2f - 0.04f, (my + fy) / 2f + 0.04f, "F", 0.055f, Sc.RED)
    }
}

/** Kaldıraç (destek ortada): F·b = P·a (kuvvet × kuvvet kolu = yük × yük kolu). */
internal object LeverModel : ParamModel(
    "sci.phys.lever", "Kaldıraç", 1.8f, 1f,
    tags = listOf("basit makine", "destek", "yük", "kuvvet kolu", "yük kolu", "moment", "formül"),
) {
    override val defs = listOf(
        ParamDef("P", "P", "Yükün ağırlığı", default = 60.0, min = 1.0, max = 10000.0, step = 5.0, unit = "N"),
        ParamDef("a", "a", "Yük kolu", default = 0.4, min = 0.05, max = 50.0, step = 0.05, unit = "m"),
        ParamDef("b", "b", "Kuvvet kolu", default = 1.0, min = 0.05, max = 50.0, step = 0.05, unit = "m"),
    )
    fun force(v: ParamValues) = v["P"] * v["a"] / v["b"]
    override val derived = listOf(Derived("F", "Kuvvet", "N", "b") { force(it) })
    override fun label(v: ParamValues) = listOf("F·b = P·a", "F = ${n(force(v))} N")
    override val labelSize = 0.055f

    private const val LOAD = 0.26f
    private const val EFFORT = 1.62f
    private const val TOP = 0.5f
    private const val BOT = 0.56f
    private const val GY = 0.8f

    private fun pivotX(v: ParamValues) = (LOAD + (EFFORT - LOAD) * v["a"] / (v["a"] + v["b"])).toFloat()

    private fun scale(v: ParamValues) = 0.34f / max(v["P"], force(v)).toFloat()

    override fun draw(b: VectorBuilder, lb: Lb, v: ParamValues): Unit = with(b) {
        val px = pivotX(v)
        hatch(px - 0.24f, GY, px + 0.24f, GY, side = 1f, gap = 0.05f, len = 0.035f)
        poly(px, BOT, px - 0.12f, GY, px + 0.12f, GY, closed = true).fill(Sc.METAL).width(1.2f)
        rect(0.1f, TOP, 1.6f, BOT - TOP).fill(Sc.WOOD).width(1.2f)
        // yük: kutu, ağırlık oku kaldıracın altından aşağı
        val bs = (0.14f + 0.05f * sqrt(v["P"] / 60.0).toFloat()).coerceIn(0.12f, 0.3f)
        rect(LOAD - bs / 2f, TOP - bs, bs, bs).fill(Sc.BLOCK).width(1.2f)
        lb.c(LOAD, TOP - bs / 2f, "P", 0.08f)
        val s = scale(v)
        val pl = max(0.05f, v.f("P") * s)
        val fl = max(0.05f, force(v).toFloat() * s)
        vec(EFFORT, TOP - fl, EFFORT, TOP - 0.005f, 0.045f, color = Sc.RED, width = 1.3f)
        lb.c(EFFORT + 0.08f, TOP - fl / 2f, "F", 0.08f, Sc.RED)
        // kollar
        for (x in listOf(LOAD, px, EFFORT)) line(x, BOT, x, 0.9f).dashed().width(0.6f)
        dim(LOAD, 0.88f, px, 0.88f)
        dim(px, 0.88f, EFFORT, 0.88f)
        lb.c((LOAD + px) / 2f, 0.95f, "a = ${n(v["a"])} m", 0.05f)
        lb.c((px + EFFORT) / 2f, 0.95f, "b = ${n(v["b"])} m", 0.05f)
        // yükün ağırlık oku (kutunun yanından) — F ile karşılaştırılabilir boyda
        vec(LOAD - bs / 2f - 0.05f, TOP - pl, LOAD - bs / 2f - 0.05f, TOP, 0.04f, width = 1f)
    }

    override fun handles(v: ParamValues): List<ParamHandle> = listOf(
        ParamHandle("pivot", pivotX(v), (BOT + GY) / 2f),
        ParamHandle("force", EFFORT, TOP - max(0.05f, force(v).toFloat() * scale(v))),
    )

    override fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? = when (key) {
        "pivot" -> {
            val t = ((x - LOAD) / (EFFORT - LOAD)).toDouble().coerceIn(0.03, 0.97)
            val total = v["a"] + v["b"]
            mapOf("a" to total * t, "b" to total * (1 - t))
        }
        "force" -> {
            val len = (TOP - y).toDouble()
            if (len <= 0.01) null else {
                val f = len / scale(v)
                mapOf("P" to f * v["b"] / v["a"])
            }
        }
        else -> null
    }
}
