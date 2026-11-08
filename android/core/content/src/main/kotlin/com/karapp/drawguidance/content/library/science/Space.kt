package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.vector.INK_COLOR
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Uzay: Güneş sistemi, Dünya ve Ay olayları, gökcisimleri ve uzay araçları. */
internal object Space {

    val category: LibCategory by lazy {
        LibCategory(
            id = "sci_space",
            title = "Uzay",
            audience = Audience.NOTES,
            section = "Fen",
            shapes = listOf(
                solarSystem, ringedPlanet, sunLayers, earthLayers, globe, dayNight,
                seasons, moonPhases, solarEclipse, lunarEclipse,
                comet, galaxy, bigDipper, rocket, satellite, telescope,
            ),
        )
    }

    private const val SUN = "#FDB813"
    private const val EARTH = "#42A5F5"
    private const val LAND = "#66BB6A"
    private const val MOON = "#B0BEC5"
    private const val NIGHT = "#1A237E"

    /** Dairenin [y1]..[y2] arasındaki yatay şeridi (gezegen bantları). */
    private fun circleBand(cx: Float, cy: Float, r: Float, y1: Float, y2: Float, n: Int = 16): List<P> {
        val left = ArrayList<P>(); val right = ArrayList<P>()
        for (i in 0..n) {
            val y = y1 + (y2 - y1) * i / n
            val w = sqrt((r * r - (y - cy) * (y - cy)).coerceAtLeast(0f))
            left += P(cx - w, y); right += P(cx + w, y)
        }
        return left + right.reversed()
    }

    /** Dünya: okyanus, kıta lekeleri, istenirse gece yarısı (güneşten uzak taraf, [sunAngle] güneşin yönü). */
    private fun VectorBuilder.earth(cx: Float, cy: Float, r: Float, sunAngle: Float? = null) {
        circle(cx, cy, r).fill(EARTH).width(1.1f)
        smooth(
            cx - 0.5f * r, cy - 0.45f * r, cx - 0.1f * r, cy - 0.6f * r, cx + 0.15f * r, cy - 0.3f * r, cx - 0.05f * r, cy + 0.05f * r,
            cx - 0.35f * r, cy + 0.1f * r, cx - 0.6f * r, cy - 0.1f * r, closed = true,
        ).fill(LAND).noStroke()
        smooth(
            cx + 0.25f * r, cy + 0.15f * r, cx + 0.6f * r, cy + 0.1f * r, cx + 0.55f * r, cy + 0.45f * r, cx + 0.3f * r, cy + 0.65f * r,
            cx + 0.1f * r, cy + 0.4f * r, closed = true,
        ).fill(LAND).noStroke()
        if (sunAngle != null) arc(cx, cy, r, r, sunAngle + 90f, 180f, closed = true).fill(NIGHT).noStroke().alpha(0.45f)
        circle(cx, cy, r).width(1.1f)
    }

    /** Güneş (dolu daire, isteğe bağlı ışınlar). */
    private fun VectorBuilder.sun(cx: Float, cy: Float, r: Float, rays: Boolean = true) {
        if (rays) star(cx, cy, r * 1.35f, r * 1.02f, 14).fill("#FFE082").noStroke()
        circle(cx, cy, r).fill(SUN).width(1.1f)
    }

    // ------------------------------------------------------------------ Güneş sistemi

    private val solarSystem = sci(
        "sci.space.solar_system", "Güneş sistemi", 1.8f, 1f,
        tags = listOf("gezegenler", "yörünge", "Güneş", "Merkür", "Venüs", "Dünya", "Mars", "Jüpiter", "Satürn", "Uranüs", "Neptün"),
    ) { _ ->
        val scx = -0.2f; val cy = 0.5f; val sr = 0.36f
        data class Pl(val x: Float, val r: Float, val col: String)
        val planets = listOf(
            Pl(0.25f, 0.016f, "#9E9E9E"), Pl(0.34f, 0.028f, "#E8C27A"), Pl(0.44f, 0.03f, EARTH), Pl(0.54f, 0.022f, "#D35400"),
            Pl(0.76f, 0.095f, "#D9A066"), Pl(1.09f, 0.075f, "#E3C07B"), Pl(1.4f, 0.052f, "#8FD3E0"), Pl(1.64f, 0.05f, "#3F6FD8"),
        )
        // yörüngeler (kutu içinde kalan yaylar)
        for (p in planets) {
            val rr = p.x - scx
            val a = deg(minOf(acos((0.02f - scx).toDouble() / rr), asin(minOf(1.0, 0.46 / rr))))
            arc(scx, cy, rr, rr, -a, 2 * a).width(0.6f)
        }
        // asteroit kuşağı
        val rng = java.util.Random(4)
        val rb = 0.645f - scx
        val ab = deg(asin(0.44 / rb))
        repeat(46) {
            val ang = -ab + rng.nextFloat() * 2 * ab
            val p = onCircle(scx, cy, rb + (rng.nextFloat() - 0.5f) * 0.05f, ang)
            dot(p.x, p.y, 0.004f + rng.nextFloat() * 0.004f, "#8D8D8D")
        }
        // Güneş (kenardan taşan büyük disk)
        val hy = sqrt(sr * sr - scx * scx)
        val a0 = angleOf(scx, cy, 0f, cy - hy)
        path(closed = true) { arcTo(scx, cy, sr, sr, a0, -2 * a0) }.fill(SUN).width(1.2f)
        for (p in planets) {
            when (p.col) {
                "#D9A066" -> {
                    circle(p.x, cy, p.r).fill(p.col).width(1f)
                    for ((y1, y2, c) in listOf(Triple(-0.5f, -0.3f, "#B97A45"), Triple(0.0f, 0.15f, "#B97A45"), Triple(0.35f, 0.5f, "#E9C79B"))) {
                        points(circleBand(p.x, cy, p.r, cy + y1 * p.r, cy + y2 * p.r), closed = true).fill(c).noStroke()
                    }
                    ellipse(p.x + 0.3f * p.r, cy + 0.25f * p.r, 0.18f * p.r, 0.1f * p.r).fill("#C0504D").noStroke()
                    circle(p.x, cy, p.r).width(1f)
                }
                "#E3C07B" -> {
                    group(rotate = -18f, px = p.x, py = cy) {
                        arc(p.x, cy, p.r * 2.1f, p.r * 0.55f, 180f, 180f).stroke("#B08D4A").width(2.2f)
                    }
                    circle(p.x, cy, p.r).fill(p.col).width(1f)
                    group(rotate = -18f, px = p.x, py = cy) {
                        arc(p.x, cy, p.r * 2.1f, p.r * 0.55f, 0f, 180f).stroke("#B08D4A").width(2.2f)
                    }
                }
                EARTH -> earth(p.x, cy, p.r)
                else -> circle(p.x, cy, p.r).fill(p.col).width(1f)
            }
        }
    }

    private val ringedPlanet = sci(
        "sci.space.ringed_planet", "Halkalı gezegen (Satürn)", 1.6f, 1f,
        tags = listOf("Satürn", "halka", "gezegen", "Güneş sistemi"),
    ) { _ ->
        val cx = 0.8f; val cy = 0.5f; val r = 0.28f; val tilt = -16f
        fun halfBand(front: Boolean) {
            val s = if (front) 0f else 180f
            group(rotate = tilt, px = cx, py = cy) {
                path(closed = true) {
                    arcTo(cx, cy, 0.7f, 0.17f, s, 180f)
                    arcTo(cx, cy, 0.42f, 0.1f, s + 180f, -180f)
                }.fill("#D8C08E").width(1.1f)
                arc(cx, cy, 0.58f, 0.14f, s, 180f).stroke("#9C7F45").width(1.4f)
                arc(cx, cy, 0.5f, 0.12f, s, 180f).stroke("#E9D7AE").width(1.2f)
            }
        }
        halfBand(front = false)
        circle(cx, cy, r).fill("#E6C27A").width(1.3f)
        group(rotate = tilt, px = cx, py = cy) {
            for ((y1, y2, c) in listOf(
                Triple(-0.85f, -0.6f, "#D4A95F"), Triple(-0.35f, -0.18f, "#D4A95F"), Triple(0.1f, 0.3f, "#F0D59A"), Triple(0.55f, 0.8f, "#D4A95F"),
            )) points(circleBand(cx, cy, r, cy + y1 * r, cy + y2 * r), closed = true).fill(c).noStroke()
        }
        circle(cx, cy, r).width(1.3f)
        halfBand(front = true)
    }

    private val sunLayers = sci(
        "sci.space.sun_layers", "Güneş'in katmanları", 1f, 1f, Fit.SQUARE,
        tags = listOf("Güneş", "çekirdek", "ışınım bölgesi", "konveksiyon", "fotosfer", "güneş lekesi", "yıldız"),
    ) { _ ->
        val c = 0.5f; val r = 0.4f
        // püskürme (kenardan yükselen yay)
        path(closed = true) {
            val a0 = onCircle(c, c, r * 0.97f, 128f); moveTo(a0.x, a0.y)
            val o1 = onCircle(c, c, r + 0.2f, 132f); val o2 = onCircle(c, c, r + 0.2f, 168f); val b0 = onCircle(c, c, r * 0.97f, 172f)
            cubicTo(o1.x, o1.y, o2.x, o2.y, b0.x, b0.y)
            val b1 = onCircle(c, c, r * 0.97f, 162f); lineTo(b1.x, b1.y)
            val i2 = onCircle(c, c, r + 0.1f, 160f); val i1 = onCircle(c, c, r + 0.1f, 140f); val a1 = onCircle(c, c, r * 0.97f, 138f)
            cubicTo(i2.x, i2.y, i1.x, i1.y, a1.x, a1.y)
        }.fill("#FF7043").width(0.8f)
        circle(c, c, r).fill("#FFB300").width(1.3f)
        // yüzey granülleri ve lekeler
        val rng = java.util.Random(8)
        repeat(40) {
            val a = rng.nextFloat() * 270f + 90f
            val d = sqrt(rng.nextFloat()) * r * 0.92f
            val p = onCircle(c, c, d, a)
            circle(p.x, p.y, 0.012f).stroke("#F57F17").width(0.4f).alpha(0.5f)
        }
        for ((x, y, rr) in listOf(Triple(0.3f, 0.62f, 0.028f), Triple(0.36f, 0.7f, 0.018f), Triple(0.62f, 0.76f, 0.022f))) {
            circle(x, y, rr * 1.6f).fill("#E65100").noStroke().alpha(0.6f)
            circle(x, y, rr).fill("#4E342E").noStroke()
        }
        // kesit (sağ üst çeyrek)
        arc(c, c, r, r, -90f, 90f, pie = true).fill("#FF8F00").width(1f)
        arc(c, c, r * 0.72f, r * 0.72f, -90f, 90f, pie = true).fill("#FFCC80").width(0.9f)
        arc(c, c, r * 0.28f, r * 0.28f, -90f, 90f, pie = true).fill("#FFF59D").width(0.9f)
        // konveksiyon döngüleri
        for (k in 0..4) {
            val a = -80f + k * 17.5f
            val p = onCircle(c, c, r * 0.86f, a)
            arc(p.x, p.y, 0.035f, 0.035f, a - 150f, 300f).stroke("#BF360C").width(0.7f)
        }
        // ışınım bölgesi: zikzak foton yolu
        val zz = ArrayList<P>()
        for (k in 0..9) zz += onCircle(c, c, r * (0.32f + 0.036f * k), -45f + if (k % 2 == 0) -9f else 9f)
        points(zz).stroke("#E65100").width(0.7f)
    }

    private val earthLayers = sci(
        "sci.space.earth_layers", "Dünya'nın katmanları", 1f, 1f, Fit.SQUARE,
        tags = listOf("yer kabuğu", "manto", "dış çekirdek", "iç çekirdek", "Dünya", "yer bilimi"),
    ) { _ ->
        val c = 0.5f; val r = 0.44f
        circle(c, c, r).fill(EARTH).width(1.3f)
        for (b in listOf(
            floatArrayOf(0.2f, 0.32f, 0.3f, 0.22f, 0.44f, 0.24f, 0.42f, 0.38f, 0.32f, 0.46f, 0.18f, 0.44f),
            floatArrayOf(0.26f, 0.6f, 0.38f, 0.58f, 0.44f, 0.7f, 0.36f, 0.86f, 0.26f, 0.76f),
            floatArrayOf(0.6f, 0.62f, 0.78f, 0.58f, 0.86f, 0.66f, 0.76f, 0.78f, 0.62f, 0.74f),
        )) smooth(*b, closed = true).fill(LAND).noStroke()
        circle(c, c, r).width(1.3f)
        // kesit: yer kabuğu, manto, dış çekirdek, iç çekirdek
        arc(c, c, r, r, -90f, 90f, pie = true).fill("#8D6E63").width(1f)
        arc(c, c, r * 0.94f, r * 0.94f, -90f, 90f, pie = true).fill("#E8743B").width(0.9f)
        arc(c, c, r * 0.55f, r * 0.55f, -90f, 90f, pie = true).fill("#F5A623").width(0.9f)
        arc(c, c, r * 0.24f, r * 0.24f, -90f, 90f, pie = true).fill("#FFE066").width(0.9f)
    }

    private val globe = sci(
        "sci.space.globe", "Paralel ve meridyenler", 1f, 1f, Fit.SQUARE,
        tags = listOf("paralel", "meridyen", "ekvator", "kutup", "enlem", "boylam", "coğrafya"),
    ) { lb ->
        val c = 0.5f; val r = 0.38f; val tiltView = 0.28f
        circle(c, c, r).fill("#E3F2FD").width(1.3f)
        // paraleller: enlem φ → y = c − r·sinφ·cos(eğim), yarı eksenler r·cosφ ve r·cosφ·sin(eğim)
        for (lat in listOf(-60, -30, 0, 30, 60)) {
            val phi = Math.toRadians(lat.toDouble())
            val yy = c - r * sin(phi).toFloat() * cos(tiltView)
            val rx = r * cos(phi).toFloat(); val ry = rx * sin(tiltView)
            val eq = lat == 0
            arc(c, yy, rx, ry, 0f, 180f).width(if (eq) 1.5f else 0.8f).stroke(if (eq) Sc.RED else null)
            arc(c, yy, rx, ry, 180f, 180f).width(0.6f).dashed().stroke(if (eq) Sc.RED else null)
        }
        // meridyenler: kutuptan kutba, ön yüzdeki yarılar
        for (lon in listOf(-60, -30, 0, 30, 60)) {
            val lam = Math.toRadians(lon.toDouble())
            curve(samples = 60) { t ->
                val phi = Math.toRadians(-90.0 + 180.0 * t)
                P(
                    c + (r * cos(phi) * sin(lam)).toFloat(),
                    c - (r * sin(phi) * cos(tiltView) - r * cos(phi) * cos(lam) * sin(tiltView)).toFloat(),
                )
            }.width(if (lon == 0) 1.2f else 0.8f)
        }
        val py = r * cos(tiltView)
        line(c, c - py - 0.08f, c, c + py + 0.08f).width(1f).dashed()
        dot(c, c - py, 0.012f); dot(c, c + py, 0.012f)
        lb.c(c + 0.05f, c - py - 0.07f, "K", 0.07f)
        lb.c(c + 0.05f, c + py + 0.07f, "G", 0.07f)
    }

    private val dayNight = sci(
        "sci.space.day_night", "Gece ve gündüz", 1.6f, 1f,
        tags = listOf("Dünya'nın dönmesi", "eksen", "gündüz", "gece", "Güneş ışınları"),
    ) { lb ->
        for (i in 0..4) {
            val y = 0.2f + i * 0.15f
            vec(0.04f, y, 0.5f, y, 0.035f, "#F9A825", 1.2f)
        }
        val cx = 1.0f; val cy = 0.52f; val r = 0.34f; val tilt = 23.5f
        earth(cx, cy, r)
        arc(cx, cy, r, r, -90f, 180f, closed = true).fill(NIGHT).noStroke().alpha(0.5f)
        circle(cx, cy, r).width(1.3f)
        group(rotate = tilt, px = cx, py = cy) {
            arc(cx, cy, r, r * 0.25f, 0f, 180f).width(0.9f).stroke(Sc.RED)
            line(cx, cy - r - 0.12f, cx, cy + r + 0.1f).width(1.2f)
            arc(cx, cy - r - 0.05f, 0.08f, 0.025f, 20f, 300f).width(1f)
            arrowHead(cx + 0.075f, cy - r - 0.041f, 1f, -0.87f, 0.03f, filled = true).fill(INK_COLOR)
        }
        val top = onCircle(cx, cy, r + 0.16f, -90f + tilt)
        lb.c(top.x + 0.04f, top.y, "K", 0.07f)
        lb.c(0.6f, 0.92f, "gündüz", 0.065f)
        lb.c(1.38f, 0.92f, "gece", 0.065f)
    }

    private val seasons = sci(
        "sci.space.seasons", "Mevsimlerin oluşumu", 2f, 1f,
        tags = listOf("mevsim", "eksen eğikliği", "yörünge", "gündönümü", "ekinoks", "Dünya", "Güneş"),
    ) { lb ->
        val cx = 1f; val cy = 0.5f; val ox = 0.76f; val oy = 0.3f; val er = 0.085f; val tilt = 23.5f
        ellipse(cx, cy, ox, oy).dashed().width(0.8f)
        arrowHead(cx - ox * 0.707f, cy + oy * 0.707f, 0.55f, 0.21f, 0.035f, filled = true).fill(INK_COLOR)
        sun(cx, cy, 0.1f)
        data class E(val x: Float, val y: Float, val name: String, val ly: Float)
        val es = listOf(
            E(cx - ox, cy, "21 Haziran", cy + 0.19f), E(cx, cy + oy, "23 Eylül", cy + oy + 0.16f),
            E(cx + ox, cy, "21 Aralık", cy + 0.19f), E(cx, cy - oy, "21 Mart", cy - oy - 0.16f),
        )
        for (e in es) {
            val sunAng = angleOf(e.x, e.y, cx, cy)
            earth(e.x, e.y, er, sunAng)
            group(rotate = tilt, px = e.x, py = e.y) {
                line(e.x, e.y - er - 0.035f, e.x, e.y + er + 0.035f).width(1.1f)
                line(e.x - er, e.y, e.x + er, e.y).width(0.6f).stroke(Sc.RED)
            }
            lb.c(e.x, e.ly, e.name, 0.065f)
        }
    }

    // ------------------------------------------------------------------ Ay

    /** Ay evresi simgesi: [phi] 0 yeni ay, 90 ilk dördün, 180 dolunay, 270 son dördün (Kuzey yarımküreden görünüş). */
    private fun VectorBuilder.phaseIcon(cx: Float, cy: Float, r: Float, phi: Float) {
        circle(cx, cy, r).fill("#546E7A").width(0.9f)
        val waxing = phi <= 180f
        val k = cos(Math.toRadians((if (waxing) phi else 360f - phi).toDouble())).toFloat()
        val s = if (waxing) 1f else -1f
        val pts = ArrayList<P>()
        for (i in 0..24) { // aydınlık yarım çember (tepe → dip)
            val a = Math.toRadians(-90.0 + 180.0 * i / 24)
            pts += P(cx + s * r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        for (i in 0..24) { // gölge sınırı (dip → tepe)
            val a = Math.toRadians(90.0 - 180.0 * i / 24)
            pts += P(cx + s * k * r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        points(pts, closed = true).fill("#FFF59D").noStroke()
        circle(cx, cy, r).width(0.9f)
    }

    private val moonPhases = sci(
        "sci.space.moon_phases", "Ay'ın evreleri", 1.25f, 1f,
        tags = listOf("Ay", "evre", "yeni ay", "ilk dördün", "dolunay", "son dördün", "hilal"),
    ) { _ ->
        val ex = 0.72f; val ey = 0.5f; val ro = 0.22f; val rOut = 0.4f
        for (i in 0..4) {
            val y = 0.14f + i * 0.18f
            vec(0.02f, y, 0.2f, y, 0.03f, "#F9A825", 1.1f)
        }
        circle(ex, ey, ro).dashed().width(0.7f)
        arrowHead(ex + ro * cos(Math.toRadians(157.5)).toFloat(), ey + ro * sin(Math.toRadians(157.5)).toFloat(), 0.38f, 0.92f, 0.03f, filled = true).fill(INK_COLOR)
        earth(ex, ey, 0.065f, 180f)
        for (k in 0 until 8) {
            val th = 180f - 45f * k
            val m = onCircle(ex, ey, ro, th)
            // Ay: Güneş'e bakan (sol) yarısı aydınlık
            circle(m.x, m.y, 0.04f).fill("#546E7A").width(0.8f)
            arc(m.x, m.y, 0.04f, 0.04f, 90f, 180f, closed = true).fill("#FFF59D").noStroke()
            circle(m.x, m.y, 0.04f).width(0.8f)
            // Dünya'dan görünüşü (dış halka)
            val o = onCircle(ex, ey, rOut, th)
            phaseIcon(o.x, o.y, 0.045f, 45f * k)
            val a = onCircle(ex, ey, ro + 0.05f, th); val b = onCircle(ex, ey, rOut - 0.055f, th)
            line(a.x, a.y, b.x, b.y).dotted().width(0.7f)
        }
    }

    /** Işık kaynağı (Güneş) kenarlarından geçen doğru ile daire kesişimi (en küçük x). */
    private fun hitCircle(p: P, q: P, cx: Float, cy: Float, r: Float): P {
        val d = unit(q.x - p.x, q.y - p.y)
        val fx = p.x - cx; val fy = p.y - cy
        val b = fx * d.x + fy * d.y; val cc = fx * fx + fy * fy - r * r
        val disc = b * b - cc
        if (disc < 0) return P(cx - r, p.y + d.y * (cx - r - p.x) / d.x)
        val t = -b - sqrt(disc)
        return P(p.x + d.x * t, p.y + d.y * t)
    }

    private val solarEclipse = sci(
        "sci.space.solar_eclipse", "Güneş tutulması", 2f, 1f,
        tags = listOf("tutulma", "tam gölge", "yarı gölge", "Ay", "Güneş", "Dünya"),
    ) { lb ->
        val s = P(0.2f, 0.5f); val sr = 0.17f
        val m = P(1.25f, 0.5f); val mr = 0.05f
        val e = P(1.72f, 0.5f); val er = 0.21f
        earth(e.x, e.y, er)
        val sTop = P(s.x, s.y - sr); val sBot = P(s.x, s.y + sr)
        val mTop = P(m.x, m.y - mr); val mBot = P(m.x, m.y + mr)
        // yarı gölge (iç teğetler) ve tam gölge (dış teğetler) Dünya yüzeyine kadar
        val pT = hitCircle(sBot, mTop, e.x, e.y, er); val pB = hitCircle(sTop, mBot, e.x, e.y, er)
        val uT = hitCircle(sTop, mTop, e.x, e.y, er); val uB = hitCircle(sBot, mBot, e.x, e.y, er)
        fun arcPts(a: P, b: P): List<P> {
            val a0 = angleOf(e.x, e.y, a.x, a.y); val a1 = angleOf(e.x, e.y, b.x, b.y)
            val aa0 = if (a0 < 0) a0 + 360f else a0; val aa1 = if (a1 < 0) a1 + 360f else a1
            return List(9) { onCircle(e.x, e.y, er, aa0 + (aa1 - aa0) * it / 8) }
        }
        points(listOf(mTop) + arcPts(pT, pB) + listOf(mBot), closed = true).fill("#90A4AE").noStroke().alpha(0.45f)
        points(listOf(mTop) + arcPts(uT, uB) + listOf(mBot), closed = true).fill("#263238").noStroke().alpha(0.85f)
        for ((a, b) in listOf(sTop to pB, sBot to pT)) line(a.x, a.y, b.x, b.y).dashed().width(0.6f)
        for ((a, b) in listOf(sTop to uT, sBot to uB)) line(a.x, a.y, b.x, b.y).width(0.7f)
        sun(s.x, s.y, sr, rays = false)
        circle(m.x, m.y, mr).fill(MOON).width(1f)
        lb.c(s.x, 0.8f, "Güneş", 0.07f)
        lb.c(m.x, 0.63f, "Ay", 0.07f)
        lb.c(e.x, 0.84f, "Dünya", 0.07f)
    }

    private val lunarEclipse = sci(
        "sci.space.lunar_eclipse", "Ay tutulması", 2f, 1f,
        tags = listOf("tutulma", "tam gölge", "yarı gölge", "Ay", "Güneş", "Dünya", "kanlı ay"),
    ) { lb ->
        val s = P(0.2f, 0.5f); val sr = 0.17f
        val e = P(1.0f, 0.5f); val er = 0.11f
        val m = P(1.72f, 0.5f); val mr = 0.045f
        val x2 = 1.96f
        fun at(a: P, b: P, x: Float) = P(x, a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x))
        val sTop = P(s.x, s.y - sr); val sBot = P(s.x, s.y + sr)
        val eTop = P(e.x, e.y - er); val eBot = P(e.x, e.y + er)
        // yarı gölge
        points(listOf(eTop, at(sBot, eTop, x2), at(sTop, eBot, x2), eBot), closed = true).fill("#CFD8DC").noStroke().alpha(0.6f)
        // tam gölge
        points(listOf(eTop, at(sTop, eTop, x2), at(sBot, eBot, x2), eBot), closed = true).fill("#546E7A").noStroke().alpha(0.55f)
        for ((a, b) in listOf(sTop to eTop, sBot to eBot)) { val c = at(a, b, x2); line(a.x, a.y, c.x, c.y).width(0.7f) }
        for ((a, b) in listOf(sBot to eTop, sTop to eBot)) { val c = at(a, b, x2); line(a.x, a.y, c.x, c.y).dashed().width(0.6f) }
        sun(s.x, s.y, sr, rays = false)
        earth(e.x, e.y, er, 180f)
        circle(m.x, m.y, mr).fill("#B5553C").width(1f)
        lb.c(s.x, 0.8f, "Güneş", 0.07f)
        lb.c(e.x, 0.7f, "Dünya", 0.07f)
        lb.c(m.x, 0.62f, "Ay", 0.07f)
    }

    // ------------------------------------------------------------------ Gökcisimleri

    private val comet = sci(
        "sci.space.comet", "Kuyruklu yıldız", 1.8f, 1f,
        tags = listOf("kuyrukluyıldız", "koma", "çekirdek", "kuyruk", "gökcismi"),
    ) { _ ->
        val n = P(1.36f, 0.64f)
        sun(1.7f, 0.9f, 0.07f)
        // toz kuyruğu (geniş, kavisli) ve iyon kuyruğu (dar, düz): ikisi de Güneş'in tersine
        path(closed = true) {
            moveTo(n.x + 0.02f, n.y - 0.07f)
            cubicTo(1.0f, 0.36f, 0.5f, 0.14f, 0.05f, 0.14f)
            lineTo(0.04f, 0.36f)
            cubicTo(0.5f, 0.44f, 0.95f, 0.64f, n.x - 0.03f, n.y + 0.08f)
        }.fill("#FFE9A8").width(0.8f).alpha(0.9f)
        for (k in 0..3) {
            val y0 = 0.19f + k * 0.045f
            smooth(n.x - 0.1f, n.y - 0.01f + k * 0.02f, 0.8f, 0.36f + k * 0.04f, 0.4f, y0 + 0.03f, 0.1f, y0).stroke("#F9A825").width(0.5f).alpha(0.7f)
        }
        path(closed = true) {
            moveTo(n.x - 0.01f, n.y - 0.05f)
            lineTo(0.56f, 0.02f); lineTo(0.7f, 0.02f)
            lineTo(n.x + 0.03f, n.y - 0.02f)
        }.fill("#B3D9F7").width(0.7f).alpha(0.9f)
        for (k in 0..2) line(n.x - 0.05f, n.y - 0.05f, 0.6f + k * 0.04f, 0.04f).stroke("#64B5F6").width(0.5f)
        circle(n.x, n.y, 0.095f).fill("#E3F2FD").noStroke().alpha(0.7f)
        circle(n.x, n.y, 0.06f).fill("#F5FAFF").width(0.7f)
        circle(n.x, n.y, 0.022f).fill("#90A4AE").width(1f)
        for ((x, y, r) in listOf(Triple(1.2f, 0.18f, 0.02f), Triple(0.6f, 0.75f, 0.018f), Triple(1.55f, 0.35f, 0.015f), Triple(0.25f, 0.55f, 0.016f), Triple(0.95f, 0.88f, 0.014f))) {
            star(x, y, r, r * 0.4f, 4).fill("#FDD835").width(0.6f)
        }
    }

    private val galaxy = sci(
        "sci.space.spiral_galaxy", "Sarmal gökada", 1f, 1f, Fit.SQUARE,
        tags = listOf("gökada", "galaksi", "Samanyolu", "sarmal kol", "yıldız"),
    ) { _ ->
        val c = 0.5f
        group(sy = 0.7f, rotate = -20f, px = c, py = c) {
            for (arm in 0..1) {
                val off = PI * arm
                val pts = List(80) { i ->
                    val th = 2.7 * PI * i / 79
                    val r = 0.045 * exp(0.235 * th)
                    P(c + (r * cos(th + off)).toFloat(), c + (r * sin(th + off)).toFloat())
                }
                tube(pts, 0.03f, 0.05f).fill("#B39DDB").noStroke().alpha(0.6f)
                tube(pts, 0.012f, 0.022f).fill("#7E57C2").noStroke().alpha(0.5f)
                points(pts).stroke("#5E35B1").width(1f)
            }
            circle(c, c, 0.13f).fill("#FFE082").noStroke().alpha(0.55f)
            circle(c, c, 0.075f).fill("#FFF59D").width(0.6f)
            val rng = java.util.Random(12)
            repeat(70) {
                val th = rng.nextDouble() * 2.7 * PI
                val r = 0.045 * exp(0.235 * th) + (rng.nextDouble() - 0.5) * 0.07
                val off = if (rng.nextBoolean()) 0.0 else PI
                dot(c + (r * cos(th + off)).toFloat(), c + (r * sin(th + off)).toFloat(), 0.004f + rng.nextFloat() * 0.005f, "#5E35B1")
            }
        }
    }

    private val bigDipper = sci(
        "sci.space.big_dipper", "Büyükayı takımyıldızı", 1.2f, 1f,
        tags = listOf("takımyıldız", "Büyükayı", "Kutup Yıldızı", "yön bulma", "yıldız"),
    ) { lb ->
        // yaklaşık gökyüzü konumları (derece): Dubhe, Merak, Phecda, Megrez, Alioth, Mizar, Alkaid
        val raw = listOf(P(11.5f, -6.75f), P(11.7f, -1.4f), P(4.3f, 1.3f), P(1.2f, -2.0f), P(-4.3f, -0.96f), P(-8.6f, 0.1f), P(-12f, 5.7f))
        val dir = unit(raw[0].x - raw[1].x, raw[0].y - raw[1].y)
        val pol = P(raw[0].x + dir.x * 12f, raw[0].y + dir.y * 12f)
        val ang = Math.toRadians(0.0)
        fun rot(p: P) = P((p.x * cos(ang) - p.y * sin(ang)).toFloat(), (p.x * sin(ang) + p.y * cos(ang)).toFloat())
        val all = (raw + pol).map(::rot)
        val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }; val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
        val sc = minOf(1.04f / (maxX - minX), 0.84f / (maxY - minY))
        val ox = (1.2f - (maxX - minX) * sc) / 2f; val oy = (1f - (maxY - minY) * sc) / 2f
        val p = all.map { P(ox + (it.x - minX) * sc, oy + (it.y - minY) * sc) }
        val order = listOf(6, 5, 4, 3, 0, 1, 2, 3)
        poly(*order.flatMap { listOf(p[it].x, p[it].y) }.toFloatArray()).width(1f)
        line(p[1].x, p[1].y, p[7].x, p[7].y).dashed().width(0.7f)
        val sizes = listOf(0.042f, 0.038f, 0.036f, 0.028f, 0.042f, 0.038f, 0.038f)
        for (i in 0..6) star(p[i].x, p[i].y, sizes[i], sizes[i] * 0.42f, 5).fill("#FDD835").width(0.8f)
        star(p[7].x, p[7].y, 0.055f, 0.022f, 5).fill("#FFF176").width(1f)
        lb.r(p[7].x - 0.07f, p[7].y, "Kutup Yıldızı", 0.065f)
    }

    // ------------------------------------------------------------------ Uzay araçları

    private val rocket = sci(
        "sci.space.rocket", "Roket", 0.55f, 1f,
        tags = listOf("roket", "uzay aracı", "fırlatma", "uzay"),
    ) { _ ->
        val cx = 0.275f
        group(rotate = 180f, px = cx, py = 0.905f) {
            path(closed = true) {
                moveTo(cx - 0.07f, 0.905f)
                cubicTo(cx - 0.1f, 0.96f, cx - 0.04f, 1.0f, cx, 1.06f)
                cubicTo(cx + 0.04f, 1.0f, cx + 0.1f, 0.96f, cx + 0.07f, 0.905f)
            }.fill(Sc.FLAME_OUT).width(0.8f)
        }
        path(closed = true) {
            moveTo(cx - 0.045f, 0.83f); lineTo(cx + 0.045f, 0.83f)
            cubicTo(cx + 0.06f, 0.9f, cx + 0.02f, 0.93f, cx, 0.98f)
            cubicTo(cx - 0.02f, 0.93f, cx - 0.06f, 0.9f, cx - 0.045f, 0.83f)
        }.fill(Sc.FLAME_OUT).width(0.8f)
        path(closed = true) {
            moveTo(cx - 0.025f, 0.83f); lineTo(cx + 0.025f, 0.83f); quadTo(cx + 0.02f, 0.88f, cx, 0.92f); quadTo(cx - 0.02f, 0.88f, cx - 0.025f, 0.83f)
        }.fill(Sc.FLAME_IN).noStroke()
        // kanatçıklar
        for (s in listOf(-1f, 1f)) {
            poly(cx + s * 0.1f, 0.56f, cx + s * 0.22f, 0.74f, cx + s * 0.22f, 0.84f, cx + s * 0.1f, 0.76f, closed = true).fill(Sc.RED).width(1.1f)
        }
        // lüle
        poly(cx - 0.06f, 0.76f, cx + 0.06f, 0.76f, cx + 0.08f, 0.83f, cx - 0.08f, 0.83f, closed = true).fill("#607D8B").width(1f)
        // gövde
        path(closed = true) {
            moveTo(cx - 0.1f, 0.77f); lineTo(cx - 0.1f, 0.3f)
            cubicTo(cx - 0.1f, 0.16f, cx - 0.04f, 0.07f, cx, 0.02f)
            cubicTo(cx + 0.04f, 0.07f, cx + 0.1f, 0.16f, cx + 0.1f, 0.3f)
            lineTo(cx + 0.1f, 0.77f)
        }.fill("#ECEFF1").width(1.3f)
        path(closed = true) {
            moveTo(cx - 0.083f, 0.2f); cubicTo(cx - 0.06f, 0.12f, cx - 0.03f, 0.06f, cx, 0.02f)
            cubicTo(cx + 0.03f, 0.06f, cx + 0.06f, 0.12f, cx + 0.083f, 0.2f)
        }.fill(Sc.RED).width(1.1f)
        rect(cx - 0.1f, 0.62f, 0.2f, 0.035f).fill(Sc.RED).width(0.9f)
        rect(cx - 0.012f, 0.66f, 0.024f, 0.17f).fill("#C62828").width(0.9f)
        circle(cx, 0.38f, 0.058f).fill("#B0BEC5").width(1.1f)
        circle(cx, 0.38f, 0.042f).fill("#81D4FA").width(0.9f)
        shine(cx, 0.38f, 0.042f)
    }

    private val satellite = sci(
        "sci.space.satellite", "Yapay uydu", 1.6f, 1f,
        tags = listOf("uydu", "güneş paneli", "anten", "iletişim", "uzay"),
    ) { _ ->
        val cy = 0.55f
        fun panel(x: Float) {
            rect(x, cy - 0.12f, 0.46f, 0.24f).fill("#1E4E9A").width(1.2f)
            for (i in 1..4) line(x + i * 0.092f, cy - 0.12f, x + i * 0.092f, cy + 0.12f).stroke("#90CAF9").width(0.7f)
            line(x, cy, x + 0.46f, cy).stroke("#90CAF9").width(0.7f)
        }
        line(0.5f, cy, 1.1f, cy).width(1.6f)
        panel(0.06f); panel(1.08f)
        // gövde (kutu, üç boyut izlenimi)
        poly(0.66f, 0.4f, 0.7f, 0.35f, 0.98f, 0.35f, 0.94f, 0.4f, closed = true).fill("#F7D774").width(1f)
        poly(0.94f, 0.4f, 0.98f, 0.35f, 0.98f, 0.66f, 0.94f, 0.71f, closed = true).fill("#D9A520").width(1f)
        rect(0.66f, 0.4f, 0.28f, 0.31f).fill("#F4B942").width(1.2f)
        for (i in 1..2) line(0.66f, 0.4f + i * 0.103f, 0.94f, 0.4f + i * 0.103f).stroke("#C98F1C").width(0.6f)
        // çanak anten
        line(0.8f, 0.35f, 0.84f, 0.25f).width(1.2f)
        group(rotate = -30f, px = 0.86f, py = 0.2f) {
            path(closed = true) { moveTo(0.76f, 0.2f); quadTo(0.86f, 0.3f, 0.96f, 0.2f) }.fill("#ECEFF1").width(1.1f)
            line(0.86f, 0.25f, 0.86f, 0.12f).width(0.9f)
            dot(0.86f, 0.12f, 0.012f)
        }
        line(0.74f, 0.71f, 0.72f, 0.84f).width(1f)
        dot(0.72f, 0.84f, 0.01f)
    }

    private val telescope = sci(
        "sci.space.telescope", "Teleskop", 1.2f, 1f,
        tags = listOf("teleskop", "mercekli teleskop", "gözlem", "gökyüzü", "optik"),
    ) { _ ->
        val m = P(0.6f, 0.47f)
        // üç ayak ve kafa
        for (x in listOf(0.34f, 0.6f, 0.86f)) line(m.x, m.y + 0.04f, x, 0.97f).width(1.6f)
        line(0.44f, 0.76f, 0.76f, 0.76f).width(1f)
        roundRect(m.x - 0.05f, m.y - 0.03f, 0.1f, 0.08f, 0.015f).fill("#607D8B").width(1.1f)
        // ana tüp
        val a = P(0.2f, 0.6f); val b = P(1.08f, 0.2f)
        bar(a.x, a.y, b.x, b.y, 0.055f).fill("#ECEFF1").width(1.2f)
        val u = unit(b.x - a.x, b.y - a.y)
        val n = P(u.y, -u.x)
        val o1 = P(b.x - u.x * 0.12f, b.y - u.y * 0.12f)
        bar(o1.x, o1.y, b.x + u.x * 0.02f, b.y + u.y * 0.02f, 0.07f).fill("#90A4AE").width(1.1f)
        // oküler ve arayıcı dürbün
        val e1 = P(a.x + u.x * 0.03f, a.y + u.y * 0.03f)
        bar(e1.x, e1.y, e1.x - u.x * 0.1f, e1.y - u.y * 0.1f, 0.025f).fill("#607D8B").width(1f)
        val f0 = lerp(a, b, 0.42f)
        val f1 = P(f0.x + n.x * 0.085f, f0.y + n.y * 0.085f)
        line(f0.x + n.x * 0.05f, f0.y + n.y * 0.05f, f1.x, f1.y).width(1f)
        bar(f1.x - u.x * 0.06f, f1.y - u.y * 0.06f, f1.x + u.x * 0.2f, f1.y + u.y * 0.2f, 0.018f).fill("#CFD8DC").width(0.9f)
    }
}
