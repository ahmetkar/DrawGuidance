package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.INK_COLOR
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Fizik: optik, mekanik, elektrik, dalgalar ve ölçüm araçları. */
internal object Physics {

    val category: LibCategory by lazy {
        LibCategory(
            id = "sci_physics",
            title = "Fizik",
            audience = Audience.NOTES,
            section = "Fen",
            shapes = listOf(
                // v45 optik sırası: ayna, mercekler, prizma (ilk paketli şekil sunucunun değiştirebildiği sabit çizimdir;
                // parametreli şekiller sunucudaki sabit kopyayla değişmez)
                planeMirror, ConvexLensModel.shape, ConcaveLensModel.shape, prism,
                InclineModel.shape, FreeBodyModel.shape, PendulumModel.shape, SpringModel.shape,
                FixedPulleyModel.shape, MovablePulleyModel.shape, LeverModel.shape, barMagnet,
                circuit, symCell, symResistor, symRheostat, symLamp, symSwitch, symAmmeter, symVoltmeter, symCapacitor,
                WaveModel.shape, longitudinalWave, ProjectileModel.shape, OhmModel.shape, thermometer, manometer,
            ),
        )
    }

    // ------------------------------------------------------------------ Optik

    /** Çift dışbükey mercek gövdesi: yarı yükseklik [h], merkez yarı kalınlığı [t]. */
    private fun VectorBuilder.convexLensShape(cx: Float, cy: Float, h: Float, t: Float) {
        val r = (h * h + t * t) / (2f * t)
        val a = deg(asin((h / r).toDouble()))
        path(closed = true) {
            arcTo(cx + t - r, cy, r, r, -a, 2f * a)
            arcTo(cx - t + r, cy, r, r, 180f - a, 2f * a)
        }.fill(Sc.LENS).width(1.2f)
    }

    /** Çift içbükey mercek: kenar yarı kalınlığı [e], merkez yarı kalınlığı [t]. */
    private fun VectorBuilder.concaveLensShape(cx: Float, cy: Float, h: Float, e: Float, t: Float) {
        val s = e - t
        val r = (h * h + s * s) / (2f * s)
        val a = deg(asin((h / r).toDouble()))
        path(closed = true) {
            moveTo(cx - e, cy - h)
            lineTo(cx + e, cy - h)
            arcTo(cx + t + r, cy, r, r, 180f + a, -2f * a)
            lineTo(cx - e, cy + h)
            arcTo(cx - t - r, cy, r, r, a, -2f * a)
        }.fill(Sc.LENS).width(1.2f)
    }

    // v45: convexLens → ParamPhysics.kt (formülle ve tutamakla düzenlenen model)
    // v45: concaveLens → ParamPhysics.kt (formülle ve tutamakla düzenlenen model)
    private val planeMirror = sci(
        "sci.phys.plane_mirror", "Düz aynada yansıma", 1.4f, 1f,
        tags = listOf("ayna", "yansıma", "gelme açısı", "yansıma açısı", "normal", "optik"),
    ) { lb ->
        val ox = 0.7f; val oy = 0.78f; val i = 40f; val l = 0.6f
        rect(0.12f, oy, 1.16f, 0.035f).fill(Sc.LENS).width(0.8f)
        hatch(0.12f, oy + 0.035f, 1.28f, oy + 0.035f, side = 1f, gap = 0.06f, len = 0.05f)
        line(0.12f, oy, 1.28f, oy).width(1.8f)
        line(ox, oy, ox, 0.14f).dashed().width(0.8f)
        lb.c(ox, 0.1f, "N", 0.07f)
        val sx = ox - l * sin(Math.toRadians(i.toDouble())).toFloat()
        val sy = oy - l * cos(Math.toRadians(i.toDouble())).toFloat()
        ray(sx, sy, ox, oy)
        ray(ox, oy, 2 * ox - sx, sy)
        angleArc(ox, oy, 0.17f, -90f - i, i)
        angleArc(ox, oy, 0.17f, -90f, i)
        val pi = onCircle(ox, oy, 0.24f, -90f - i / 2f)
        val pr = onCircle(ox, oy, 0.24f, -90f + i / 2f)
        lb.c(pi.x, pi.y, "i", 0.075f)
        lb.c(pr.x, pr.y, "r", 0.075f)
    }

    private val prism = sci(
        "sci.phys.prism", "Prizmada ışığın ayrışması", 1.6f, 1f,
        tags = listOf("prizma", "tayf", "renkler", "kırılma", "beyaz ışık", "optik"),
    ) { _ ->
        val side = 0.72f
        val a = P(0.78f, 0.14f)
        val hgt = side * sqrt(3f) / 2f
        val b = P(a.x - side / 2f, a.y + hgt)
        val c = P(a.x + side / 2f, a.y + hgt)
        // ekran
        rect(1.5f, 0.3f, 0.045f, 0.6f).fill("#ECEFF1").width(0.9f)
        val pin = lerp(b, a, 0.5f)
        // gelen beyaz ışık
        ray(0.04f, pin.y + 0.148f, pin.x, pin.y, color = null, width = 1.6f)
        val n = Sc.SPECTRUM.size
        val exits = List(n) { k -> lerp(a, c, 0.47f + 0.11f * k / (n - 1)) }
        // prizma gövdesi (ışınların altında)
        points(listOf(a, b, c), closed = true).fill("#E6F3FB").width(1.3f)
        for (k in 0 until n) {
            val col = Sc.SPECTRUM[k]
            val e = exits[k]
            line(pin.x, pin.y, e.x, e.y).stroke(col).width(1.1f)
            val ang = Math.toRadians((10.0 + 14.0 * k / (n - 1)))
            val yEnd = e.y + (1.5f - e.x) * tan(ang).toFloat()
            line(e.x, e.y, 1.5f, yEnd).stroke(col).width(1.4f)
            rect(1.5f, yEnd - 0.017f, 0.045f, 0.034f).fill(col).noStroke()
        }
        points(listOf(a, b, c), closed = true).width(1.3f)
    }

    // ------------------------------------------------------------------ Mekanik

    // v45: inclinedPlane → ParamPhysics.kt (formülle ve tutamakla düzenlenen model)
    // v46: freeBody, fixedPulley, movablePulley, lever → ParamMechanics.kt (formülle yeniden çizilir)
    // v45: pendulum → ParamPhysics.kt (formülle ve tutamakla düzenlenen model)
    // v45: springMass → ParamPhysics.kt (formülle ve tutamakla düzenlenen model)
    // ------------------------------------------------------------------ Manyetizma

    /** Çubuk mıknatısın alan çizgileri: iki kutuplu alan boyunca sayısal izleme. */
    private fun fieldLines(n: P, s: P, w: Float, h: Float): List<List<P>> {
        fun field(p: P): P {
            val ax = p.x - n.x; val ay = p.y - n.y; val a3 = (ax * ax + ay * ay).let { it * sqrt(it) }
            val bx = p.x - s.x; val by = p.y - s.y; val b3 = (bx * bx + by * by).let { it * sqrt(it) }
            return P(ax / a3 - bx / b3, ay / a3 - by / b3)
        }
        fun trace(from: P, angle: Float, sign: Float, target: P): Pair<List<P>, Boolean> {
            val pts = ArrayList<P>()
            var p = onCircle(from.x, from.y, 0.03f, angle)
            pts += p
            val step = 0.01f
            repeat(900) {
                val e = field(p)
                val u = unit(e.x * sign, e.y * sign)
                p = P(p.x + u.x * step, p.y + u.y * step)
                if (p.x < 0.02f || p.x > w - 0.02f || p.y < 0.02f || p.y > h - 0.02f) return pts to false
                pts += p
                if (dist(p, target) < 0.03f) return pts to true
            }
            return pts to false
        }
        val lines = ArrayList<List<P>>()
        for (alpha in listOf(0f, 26f, 52f, 80f, 112f, 148f)) {
            for (sgn in if (alpha == 0f) listOf(1f) else listOf(1f, -1f)) {
                val (fromN, closed) = trace(n, 180f + sgn * alpha, 1f, s)
                lines += fromN
                if (!closed) {
                    // kutudan çıkan çizginin S tarafındaki simetriği (S'ye giren)
                    val (fromS, _) = trace(s, -sgn * alpha, -1f, n)
                    lines += fromS.reversed()
                }
            }
        }
        return lines
    }

    private val barMagnet = sci(
        "sci.phys.bar_magnet", "Çubuk mıknatıs ve alan çizgileri", 1.6f, 1f,
        tags = listOf("mıknatıs", "manyetik alan", "kutup", "N", "S", "manyetizma"),
    ) { lb ->
        val cy = 0.5f
        val nPole = P(0.56f, cy); val sPole = P(1.04f, cy)
        for (pts in fieldLines(nPole, sPole, 1.6f, 1f)) {
            if (pts.size < 2) continue
            points(pts).width(0.9f)
            // ok ucu: çizginin mıknatısın ortasına en yakın x'inde (kapalı çizgi) ya da ortasında
            val idx = pts.indices.minByOrNull { abs(pts[it].x - 0.8f) + if (abs(pts[it].y - cy) < 0.12f) 5f else 0f } ?: continue
            val i = if (abs(pts[idx].x - 0.8f) < 0.05f) idx else pts.size / 2
            if (i < 1 || i >= pts.size) continue
            val d = pts[i] - pts[i - 1]
            arrowHead(pts[i].x, pts[i].y, d.x, d.y, 0.035f, filled = true).fill(INK_COLOR).width(0.6f)
        }
        rect(0.5f, cy - 0.07f, 0.3f, 0.14f).fill("#E53935").width(1.3f)
        rect(0.8f, cy - 0.07f, 0.3f, 0.14f).fill("#1E88E5").width(1.3f)
        lb.c(0.65f, cy, "N", 0.09f, "#FFFFFF")
        lb.c(0.95f, cy, "S", 0.09f, "#FFFFFF")
    }

    // ------------------------------------------------------------------ Elektrik

    private val circuit = sci(
        "sci.phys.circuit", "Basit elektrik devresi", 1.4f, 1f,
        tags = listOf("devre", "pil", "ampul", "anahtar", "akım", "elektrik"),
    ) { lb ->
        val l = 0.18f; val r = 1.22f; val t = 0.22f; val b = 0.8f
        // teller
        poly(0.66f, t, l, t, l, b, 0.56f, b).width(1.3f)
        poly(0.74f, t, r, t, r, 0.42f).width(1.3f)
        poly(r, 0.6f, r, b, 0.84f, b).width(1.3f)
        // pil
        line(0.66f, t - 0.11f, 0.66f, t + 0.11f).width(1.4f)
        line(0.74f, t - 0.055f, 0.74f, t + 0.055f).width(3f)
        lb.c(0.6f, t - 0.12f, "+", 0.08f)
        lb.c(0.8f, t - 0.12f, "−", 0.08f)
        // ampul
        circle(r, 0.51f, 0.09f).fill("#FFF6C2").width(1.3f)
        val d = 0.09f * 0.7071f
        line(r - d, 0.51f - d, r + d, 0.51f + d).width(1.1f)
        line(r - d, 0.51f + d, r + d, 0.51f - d).width(1.1f)
        // açık anahtar
        line(0.56f, b, 0.8f, b - 0.12f).width(1.4f)
        circle(0.56f, b, 0.016f).fill("#FFFFFF").width(1f)
        circle(0.84f, b, 0.016f).fill("#FFFFFF").width(1f)
        // akım yönü (+ uçtan dış devreye)
        arrowHead(l, 0.53f, 0f, 1f, 0.05f, filled = true).fill(INK_COLOR)
        lb.c(l - 0.07f, 0.5f, "I", 0.08f)
    }

    /** Devre elemanı (iki nokta arası): uçlar x=0 ve x=3, y=0.5. */
    private fun symbol(id: String, title: String, tags: List<String>, draw: VectorBuilder.(Lb) -> Unit): LibShape =
        sci(id, title, 3f, 1f, Fit.LINE, tags + listOf("devre elemanı", "sembol", "elektrik"), draw)

    private val symCell = symbol("sci.phys.sym_cell", "Pil (devre sembolü)", listOf("pil", "üreteç", "batarya")) { lb ->
        line(0f, 0.5f, 1.38f, 0.5f)
        line(1.38f, 0.1f, 1.38f, 0.9f).width(1.3f)
        line(1.62f, 0.3f, 1.62f, 0.7f).width(3f)
        line(1.62f, 0.5f, 3f, 0.5f)
        lb.c(1.16f, 0.2f, "+", 0.26f)
        lb.c(1.86f, 0.2f, "−", 0.26f)
    }

    private val symResistor = symbol("sci.phys.sym_resistor", "Direnç (devre sembolü)", listOf("direnç", "ohm")) { _ ->
        val pts = ArrayList<Float>()
        pts += listOf(0f, 0.5f, 0.9f, 0.5f)
        var x = 1.0f; var up = true
        while (x < 2.05f) { pts += listOf(x, if (up) 0.22f else 0.78f); up = !up; x += 0.2f }
        pts += listOf(2.1f, 0.5f, 3f, 0.5f)
        poly(*pts.toFloatArray()).width(1.2f)
    }

    private val symRheostat = symbol("sci.phys.sym_rheostat", "Reosta (devre sembolü)", listOf("reosta", "ayarlı direnç")) { _ ->
        val pts = ArrayList<Float>()
        pts += listOf(0f, 0.5f, 0.9f, 0.5f)
        var x = 1.0f; var up = true
        while (x < 2.05f) { pts += listOf(x, if (up) 0.3f else 0.7f); up = !up; x += 0.2f }
        pts += listOf(2.1f, 0.5f, 3f, 0.5f)
        poly(*pts.toFloatArray()).width(1.2f)
        vec(1.05f, 0.95f, 2.0f, 0.05f, 0.14f, width = 1f)
    }

    private val symLamp = symbol("sci.phys.sym_lamp", "Ampul (devre sembolü)", listOf("ampul", "lamba")) { _ ->
        val r = 0.38f; val d = r * 0.7071f
        line(0f, 0.5f, 1.5f - r, 0.5f)
        line(1.5f + r, 0.5f, 3f, 0.5f)
        circle(1.5f, 0.5f, r).width(1.2f)
        line(1.5f - d, 0.5f - d, 1.5f + d, 0.5f + d)
        line(1.5f - d, 0.5f + d, 1.5f + d, 0.5f - d)
    }

    private val symSwitch = symbol("sci.phys.sym_switch", "Anahtar (devre sembolü)", listOf("anahtar", "açık devre")) { _ ->
        line(0f, 0.5f, 1.04f, 0.5f)
        line(1.12f, 0.46f, 1.9f, 0.1f).width(1.3f)
        line(2.06f, 0.5f, 3f, 0.5f)
        circle(1.08f, 0.5f, 0.05f).fill("#FFFFFF")
        circle(2.02f, 0.5f, 0.05f).fill("#FFFFFF")
    }

    private fun VectorBuilder.meter(lb: Lb, letter: String) {
        val r = 0.38f
        line(0f, 0.5f, 1.5f - r, 0.5f)
        line(1.5f + r, 0.5f, 3f, 0.5f)
        circle(1.5f, 0.5f, r).width(1.2f)
        lb.c(1.5f, 0.52f, letter, 0.42f)
    }

    private val symAmmeter = symbol("sci.phys.sym_ammeter", "Ampermetre (devre sembolü)", listOf("ampermetre", "akım ölçer")) { lb -> meter(lb, "A") }

    private val symVoltmeter = symbol("sci.phys.sym_voltmeter", "Voltmetre (devre sembolü)", listOf("voltmetre", "gerilim ölçer")) { lb -> meter(lb, "V") }

    private val symCapacitor = symbol("sci.phys.sym_capacitor", "Kondansatör (devre sembolü)", listOf("kondansatör", "sığa")) { _ ->
        line(0f, 0.5f, 1.4f, 0.5f)
        line(1.6f, 0.5f, 3f, 0.5f)
        line(1.4f, 0.12f, 1.4f, 0.88f).width(1.6f)
        line(1.6f, 0.12f, 1.6f, 0.88f).width(1.6f)
    }

    // ------------------------------------------------------------------ Dalgalar

    // v45: transverseWave → ParamPhysics.kt (formülle ve tutamakla düzenlenen model)
    private val longitudinalWave = sci(
        "sci.phys.longitudinal_wave", "Boyuna dalga", 2.2f, 0.8f,
        tags = listOf("dalga", "yay", "sıkışma", "genleşme", "ses", "dalga boyu"),
    ) { lb ->
        val y = 0.4f; val x0 = 0.12f; val x1 = 2.12f; val k = 3; val a = 0.66f
        hatch(x0, 0.62f, x0, 0.18f, side = -1f, gap = 0.05f, len = 0.04f)
        val lead = 0.03f
        coil(x0, y, x1, y, turns = 30, r = 0.13f, lead = lead, tilt = 0.12f) { s ->
            s + a / (2f * PI.toFloat() * k) * sin(2.0 * PI * k * s).toFloat()
        }
        // sıkışma merkezleri: s = 1/6, 1/2, 5/6; genleşmeler: s = 1/3, 2/3
        val lc = x1 - x0 - 2 * lead
        fun xs(s: Float) = x0 + lead + s * lc
        val c1 = xs(1f / 6f); val c2 = xs(0.5f)
        line(c1, 0.09f, c1, 0.25f).dashed().width(0.6f)
        line(c2, 0.09f, c2, 0.25f).dashed().width(0.6f)
        dim(c1, 0.13f, c2, 0.13f)
        lb.c((c1 + c2) / 2f, 0.07f, "λ", 0.08f)
        line(c1, 0.56f, c1, 0.61f).width(0.7f)
        lb.c(c1, 0.66f, "sıkışma", 0.07f)
        val r2 = xs(2f / 3f)
        line(r2, 0.56f, r2, 0.61f).width(0.7f)
        lb.c(r2, 0.66f, "genleşme", 0.07f)
        vec(1.72f, 0.1f, 2.06f, 0.1f, 0.04f, width = 1f)
    }

    // v45: projectile → ParamPhysics.kt (formülle ve tutamakla düzenlenen model)
    // ------------------------------------------------------------------ Ölçüm araçları

    private val thermometer = sci(
        "sci.phys.thermometer", "Termometre", 0.5f, 1f,
        tags = listOf("sıcaklık", "derece", "ısı", "ölçüm"),
    ) { lb ->
        val cx = 0.21f
        roundRect(cx - 0.07f, 0.04f, 0.14f, 0.8f, 0.07f).fill("#F7FBFE").width(1.2f)
        line(cx - 0.015f, 0.1f, cx - 0.015f, 0.8f).stroke("#B0BEC5").width(0.5f)
        line(cx + 0.015f, 0.1f, cx + 0.015f, 0.8f).stroke("#B0BEC5").width(0.5f)
        rect(cx - 0.015f, 0.36f, 0.03f, 0.48f).fill(Sc.RED).noStroke()
        circle(cx, 0.88f, 0.085f).fill(Sc.RED).width(1.2f)
        shine(cx, 0.88f, 0.085f)
        // ölçek: 0 °C (y=0.74) → 100 °C (y=0.14)
        for (i in 0..20) {
            val y = 0.74f - i * 0.03f
            val major = i % 2 == 0
            line(cx + 0.03f, y, cx + if (major) 0.07f else 0.052f, y).width(if (major) 0.8f else 0.5f)
            if (i % 4 == 0) lb.t(cx + 0.09f, y - 0.018f, "${i * 5}", 0.04f)
        }
        lb.t(cx + 0.09f, 0.025f, "°C", 0.05f)
    }

    private val manometer = sci(
        "sci.phys.manometer", "U borulu manometre", 1.3f, 1f,
        tags = listOf("basınç", "gaz basıncı", "u borusu", "sıvı", "ölçüm"),
    ) { lb ->
        val cx = 0.8f; val cy = 0.74f; val ro = 0.22f; val ri = 0.14f
        val lvlL = 0.58f; val lvlR = 0.34f
        // sıvı
        path(closed = true) {
            moveTo(cx - ro, lvlL)
            lineTo(cx - ro, cy)
            arcTo(cx, cy, ro, ro, 180f, -180f)
            lineTo(cx + ro, lvlR)
            lineTo(cx + ri, lvlR)
            lineTo(cx + ri, cy)
            arcTo(cx, cy, ri, ri, 0f, 180f)
            lineTo(cx - ri, lvlL)
        }.fill(Sc.LIQUID).noStroke()
        // gaz haznesi
        circle(0.27f, 0.3f, 0.2f).fill("#FFF3E0").width(1.2f)
        rect(0.45f, 0.26f, 0.21f, 0.08f).fill("#FFF3E0").noStroke()
        rect(cx - ro, 0.26f, ro - ri, lvlL - 0.26f).fill("#FFF3E0").noStroke()
        for ((x, y) in listOf(0.19f to 0.19f, 0.33f to 0.17f, 0.18f to 0.41f, 0.36f to 0.4f, 0.4f to 0.27f, 0.13f to 0.3f)) dot(x, y, 0.01f)
        // cam boru
        path {
            moveTo(0.46f, 0.26f); lineTo(cx - ri, 0.26f); lineTo(cx - ri, cy)
            arcTo(cx, cy, ri, ri, 180f, -180f); lineTo(cx + ri, 0.1f)
        }.width(1.3f)
        path {
            moveTo(0.46f, 0.34f); lineTo(cx - ro, 0.34f); lineTo(cx - ro, cy)
            arcTo(cx, cy, ro, ro, 180f, -180f); lineTo(cx + ro, 0.1f)
        }.width(1.3f)
        line(cx - ro, lvlL, cx - ri, lvlL).width(0.8f)
        line(cx + ri, lvlR, cx + ro, lvlR).width(0.8f)
        // yükseklik farkı
        line(cx - ri, lvlL, 1.18f, lvlL).dashed().width(0.6f)
        line(cx + ro, lvlR, 1.18f, lvlR).dashed().width(0.6f)
        dim(1.13f, lvlR, 1.13f, lvlL)
        lb.c(1.2f, (lvlL + lvlR) / 2f, "h", 0.08f)
        lb.c(0.27f, 0.3f, "Gaz", 0.08f)
    }
}
