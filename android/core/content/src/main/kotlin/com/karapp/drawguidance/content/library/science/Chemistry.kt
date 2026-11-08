package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Kimya: laboratuvar malzemeleri, düzenekler, atom ve molekül modelleri. */
internal object Chemistry {

    val category: LibCategory by lazy {
        LibCategory(
            id = "sci_chemistry",
            title = "Kimya",
            audience = Audience.NOTES,
            section = "Fen",
            shapes = listOf(
                beaker, erlenmeyerFlask, testTubes, volumetricFlask, graduatedCylinder, titration,
                bunsen, spiritLamp, filtration, separatoryFunnel, distillation,
                BohrAtomModel.shape, Molecules.water.shape, Molecules.methane.shape, Molecules.benzene.shape, nacl, PeriodicCellModel.shape, phScale,
                Molecules.any2d.shape, Molecules.any3d.shape,
                Molecules.propane.shape, Molecules.ethylene.shape, Molecules.ethanol.shape, Molecules.aceticAcid.shape,
                Molecules.phenol.shape, Molecules.toluene.shape, Molecules.glycine.shape, Molecules.alanine.shape, Molecules.glucose.shape,
            ),
        )
    }

    private const val PINK = "#F8BBD0"
    private const val OIL = "#FBE38E"
    private const val STAND = "#B0BEC5"

    // ------------------------------------------------------------------ Cam kap yardımcıları

    /**
     * Dönel cam kap profili: (yarı genişlik, y) kontrol noktaları yukarıdan aşağı, yumuşatılır.
     * Dış çizgi, sıvı bandı gibi parçalar bu profilden üretilir.
     */
    private class Vessel(val cx: Float, ctrl: List<P>) {
        val prof: List<P> = VectorBuilder.catmullRom(ctrl, closed = false, steps = 14)

        fun w(y: Float): Float {
            for (i in 1 until prof.size) {
                val a = prof[i - 1]; val b = prof[i]
                if ((y - a.y) * (y - b.y) <= 0f && a.y != b.y) return a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y)
            }
            return if (y < prof.first().y) prof.first().x else prof.last().x
        }

        /** Açık ağızlı dış çizgi (sol üstten aşağı, sağ üste). */
        fun outline(): List<P> = prof.map { P(cx - it.x, it.y) } + prof.reversed().map { P(cx + it.x, it.y) }

        /** [ya]..[yb] arasındaki sıvı bölgesi (kapalı). */
        fun band(ya: Float, yb: Float = prof.last().y): List<P> {
            val inner = prof.filter { it.y > ya && it.y < yb }
            val left = listOf(P(cx - w(ya), ya)) + inner.map { P(cx - it.x, it.y) } + P(cx - w(yb), yb)
            val right = listOf(P(cx + w(yb), yb)) + inner.reversed().map { P(cx + it.x, it.y) } + P(cx + w(ya), ya)
            return left + right
        }
    }

    /** Erlenmeyer: boyun [nh] yarı genişlik, taban [bh] yarı genişlik, [liquid] sıvı seviyesi (null: boş). */
    private fun VectorBuilder.erlenmeyer(
        cx: Float, top: Float, shoulder: Float, bottom: Float, nh: Float, bh: Float,
        liquid: Float?, color: String = Sc.LIQUID, cr: Float = 0.04f,
    ) {
        fun wallX(y: Float, s: Float) = cx + s * (nh + (bh - nh) * (y - shoulder) / (bottom - shoulder))
        val len = sqrt((bh - nh) * (bh - nh) + (bottom - shoulder) * (bottom - shoulder))
        val ux = (bh - nh) / len; val uy = (bottom - shoulder) / len
        fun cornerA(s: Float) = P(cx + s * bh - s * ux * cr, bottom - uy * cr)
        fun cornerB(s: Float) = P(cx + s * (bh - cr), bottom)
        val body: com.karapp.drawguidance.content.vector.PathBuilder.(Float) -> Unit = { y0 ->
            moveTo(wallX(y0, -1f), y0)
            val a = cornerA(-1f); val b = cornerB(-1f)
            lineTo(a.x, a.y); quadTo(cx - bh, bottom, b.x, b.y)
            val b2 = cornerB(1f); val a2 = cornerA(1f)
            lineTo(b2.x, b2.y); quadTo(cx + bh, bottom, a2.x, a2.y)
            lineTo(wallX(y0, 1f), y0)
        }
        path(closed = true) { body(shoulder); lineTo(cx + nh, top); lineTo(cx - nh, top) }.fill(Sc.GLASS).noStroke()
        if (liquid != null) {
            path(closed = true) { body(liquid) }.fill(color).noStroke()
            line(wallX(liquid, -1f), liquid, wallX(liquid, 1f), liquid).stroke("#6FA8C8").width(0.6f)
        }
        path {
            moveTo(cx - nh, top); lineTo(cx - nh, shoulder)
            body(shoulder)
            lineTo(cx + nh, top)
        }.width(1.3f)
        // ağız kenarı
        line(cx - nh - 0.015f, top, cx + nh + 0.015f, top).width(1.3f)
    }

    /** Deney tüpü (yuvarlak dipli). */
    private fun VectorBuilder.testTube(x: Float, top: Float, bottom: Float, hw: Float, liquid: Float?, color: String) {
        path(closed = true) {
            moveTo(x - hw, top); lineTo(x - hw, bottom - hw); arcTo(x, bottom - hw, hw, hw, 180f, -180f); lineTo(x + hw, top)
        }.fill(Sc.GLASS).noStroke()
        if (liquid != null) {
            path(closed = true) {
                moveTo(x - hw, liquid); lineTo(x - hw, bottom - hw); arcTo(x, bottom - hw, hw, hw, 180f, -180f); lineTo(x + hw, liquid)
            }.fill(color).noStroke()
            line(x - hw, liquid, x + hw, liquid).stroke("#6FA8C8").width(0.6f)
        }
        path {
            moveTo(x - hw, top); lineTo(x - hw, bottom - hw); arcTo(x, bottom - hw, hw, hw, 180f, -180f); lineTo(x + hw, top)
        }.width(1.2f)
        ellipse(x, top, hw + 0.012f, 0.012f).width(1f)
    }

    /** Laboratuvar sporu (altlık + çubuk). */
    private fun VectorBuilder.stand(x: Float, top: Float, baseY: Float, baseL: Float, baseR: Float) {
        roundRect(baseL, baseY, baseR - baseL, 0.04f, 0.01f).fill(STAND).width(1f)
        rect(x - 0.013f, top, 0.026f, baseY - top).fill(Sc.METAL).width(0.9f)
    }

    /** Alev (dış turuncu, iç sarı): taban merkezi (x, y), yükseklik [h]. */
    private fun VectorBuilder.flame(x: Float, y: Float, h: Float, w: Float, outer: String = Sc.FLAME_OUT, inner: String = Sc.FLAME_IN) {
        path(closed = true) {
            moveTo(x - w, y)
            cubicTo(x - w * 1.6f, y - h * 0.35f, x - w * 0.2f, y - h * 0.6f, x, y - h)
            cubicTo(x + w * 0.2f, y - h * 0.6f, x + w * 1.6f, y - h * 0.35f, x + w, y)
            quadTo(x, y + w * 0.5f, x - w, y)
        }.fill(outer).width(0.8f)
        path(closed = true) {
            moveTo(x - w * 0.5f, y)
            cubicTo(x - w * 0.8f, y - h * 0.25f, x - w * 0.1f, y - h * 0.4f, x, y - h * 0.62f)
            cubicTo(x + w * 0.1f, y - h * 0.4f, x + w * 0.8f, y - h * 0.25f, x + w * 0.5f, y)
            quadTo(x, y + w * 0.25f, x - w * 0.5f, y)
        }.fill(inner).noStroke()
    }

    // ------------------------------------------------------------------ Cam malzemeler

    private val beaker = sci(
        "sci.chem.beaker", "Beherglas", 0.9f, 1f,
        tags = listOf("behar", "cam malzeme", "laboratuvar", "sıvı"),
    ) { _ ->
        val l = 0.16f; val r = 0.76f; val t = 0.1f; val b = 0.92f; val cr = 0.05f; val lvl = 0.46f
        fun PB(y0: Float): com.karapp.drawguidance.content.vector.PathBuilder.() -> Unit = {
            moveTo(l, y0); lineTo(l, b - cr); arcTo(l + cr, b - cr, cr, cr, 180f, -90f)
            lineTo(r - cr, b); arcTo(r - cr, b - cr, cr, cr, 90f, -90f); lineTo(r, y0)
        }
        path(closed = true, block = PB(t)).fill(Sc.GLASS).noStroke()
        path(closed = true, block = PB(lvl)).fill(Sc.LIQUID).noStroke()
        line(l, lvl, r, lvl).stroke("#6FA8C8").width(0.7f)
        // ölçek çizgileri
        for (i in 0..8) {
            val y = 0.26f + i * 0.07f
            line(l + 0.07f, y, l + if (i % 2 == 0) 0.22f else 0.14f, y).width(0.7f)
        }
        path {
            moveTo(l - 0.08f, t - 0.035f)
            quadTo(l - 0.01f, t - 0.02f, l, t + 0.05f)
            lineTo(l, b - cr); arcTo(l + cr, b - cr, cr, cr, 180f, -90f)
            lineTo(r - cr, b); arcTo(r - cr, b - cr, cr, cr, 90f, -90f)
            lineTo(r, t + 0.01f); quadTo(r, t - 0.015f, r + 0.025f, t - 0.025f)
        }.width(1.4f)
    }

    private val erlenmeyerFlask = sci(
        "sci.chem.erlenmeyer", "Erlenmeyer", 0.8f, 1f,
        tags = listOf("erlen", "cam malzeme", "laboratuvar", "sıvı"),
    ) { _ ->
        erlenmeyer(0.4f, 0.07f, 0.34f, 0.93f, 0.075f, 0.33f, liquid = 0.66f)
        for (i in 0..3) {
            val y = 0.5f + i * 0.09f
            line(0.34f, y, 0.46f, y).width(0.6f)
        }
    }

    private val testTubes = sci(
        "sci.chem.test_tubes", "Deney tüpü ve tüplük", 1.3f, 1f,
        tags = listOf("deney tüpü", "tüplük", "cam malzeme", "laboratuvar"),
    ) { _ ->
        val xs = listOf(0.33f, 0.54f, 0.75f, 0.96f)
        val lv = listOf(0.5f, 0.38f, 0.6f, null)
        val cols = listOf(Sc.LIQUID, "#B9E4A8", PINK, Sc.LIQUID)
        // arka ayaklar ve alt tabla
        roundRect(0.04f, 0.87f, 1.22f, 0.07f, 0.015f).fill(Sc.WOOD).width(1.1f)
        rect(0.09f, 0.34f, 0.06f, 0.53f).fill(Sc.WOOD).width(1.1f)
        rect(1.15f, 0.34f, 0.06f, 0.53f).fill(Sc.WOOD).width(1.1f)
        for (i in xs.indices) testTube(xs[i], 0.12f, 0.84f, 0.06f, lv[i], cols[i])
        // üst tabla (tüplerin önünde)
        rect(0.06f, 0.34f, 1.18f, 0.07f).fill(Sc.WOOD).width(1.1f)
        for (x in xs) ellipse(x, 0.345f, 0.07f, 0.012f).fill("#C9A87A").width(0.6f)
        rect(0.09f, 0.66f, 1.12f, 0.035f).fill(Sc.WOOD).width(1f)
    }

    private val volumetricFlask = sci(
        "sci.chem.volumetric_flask", "Balon joje", 0.7f, 1f,
        tags = listOf("ölçü balonu", "cam malzeme", "çözelti", "laboratuvar"),
    ) { _ ->
        val v = Vessel(
            0.35f,
            listOf(
                P(0.045f, 0.1f), P(0.045f, 0.4f), P(0.08f, 0.48f), P(0.19f, 0.54f), P(0.255f, 0.63f),
                P(0.27f, 0.74f), P(0.24f, 0.86f), P(0.16f, 0.94f),
            ),
        )
        points(v.outline(), closed = true).fill(Sc.GLASS).noStroke()
        points(v.band(0.24f), closed = true).fill(Sc.LIQUID).noStroke()
        points(v.outline(), closed = true).width(1.3f)
        // kalibrasyon çizgisi
        ellipse(0.35f, 0.24f, 0.045f, 0.01f).width(0.9f)
        // tıpa
        roundRect(0.29f, 0.03f, 0.12f, 0.09f, 0.02f).fill("#D7CCC8").width(1.1f)
    }

    private val graduatedCylinder = sci(
        "sci.chem.graduated_cylinder", "Dereceli silindir", 0.45f, 1f,
        tags = listOf("mezür", "dereceli kap", "hacim", "cam malzeme"),
    ) { _ ->
        val l = 0.13f; val r = 0.33f; val t = 0.07f; val b = 0.92f; val lvl = 0.45f
        rect(l, t, r - l, b - t).fill(Sc.GLASS).noStroke()
        path(closed = true) {
            moveTo(l, lvl); quadTo((l + r) / 2f, lvl + 0.05f, r, lvl); lineTo(r, b); lineTo(l, b)
        }.fill(Sc.LIQUID).noStroke()
        path { moveTo(l, lvl); quadTo((l + r) / 2f, lvl + 0.05f, r, lvl) }.stroke("#6FA8C8").width(0.8f)
        for (i in 0..24) {
            val y = 0.14f + i * 0.03f
            val major = i % 5 == 0
            line(l + 0.02f, y, l + if (major) 0.11f else 0.06f, y).width(if (major) 0.8f else 0.5f)
        }
        path {
            moveTo(l - 0.05f, t - 0.035f); quadTo(l - 0.005f, t - 0.02f, l, t + 0.035f)
            lineTo(l, b); moveTo(r, b); lineTo(r, t)
        }.width(1.3f)
        line(l, b, l, t + 0.035f).width(1.3f)
        line(r, t, r, b).width(1.3f)
        poly(0.05f, 0.975f, 0.41f, 0.975f, 0.36f, b, 0.1f, b, closed = true).fill("#DDE7EE").width(1.2f)
    }

    private val titration = sci(
        "sci.chem.titration", "Büret ve titrasyon düzeneği", 0.8f, 1.1f,
        tags = listOf("büret", "titrasyon", "asit baz", "erlenmeyer", "laboratuvar"),
    ) { _ ->
        val bx = 0.47f; val bw = 0.026f
        stand(0.13f, 0.03f, 1.02f, 0.04f, 0.72f)
        // kıskaç
        bar(0.143f, 0.3f, bx - bw, 0.3f, 0.011f).fill(Sc.METAL).width(0.8f)
        // büret
        rect(bx - bw, 0.03f, bw * 2, 0.64f).fill(Sc.GLASS).noStroke()
        rect(bx - bw, 0.12f, bw * 2, 0.55f).fill(Sc.LIQUID).noStroke()
        for (i in 0..13) {
            val y = 0.08f + i * 0.04f
            line(bx - bw, y, bx - bw + if (i % 5 == 0) 0.03f else 0.016f, y).width(0.5f)
        }
        line(bx - bw, 0.03f, bx - bw, 0.67f).width(1.2f)
        line(bx + bw, 0.03f, bx + bw, 0.67f).width(1.2f)
        roundRect(bx - bw - 0.012f, 0.285f, (bw + 0.012f) * 2, 0.03f, 0.008f).fill(Sc.METAL_DARK).width(0.8f)
        // musluk ve uç
        path(closed = true) {
            moveTo(bx - bw, 0.67f); lineTo(bx + bw, 0.67f); lineTo(bx + 0.009f, 0.8f); lineTo(bx - 0.009f, 0.8f)
        }.fill(Sc.GLASS).width(1.1f)
        rect(bx - 0.035f, 0.69f, 0.07f, 0.04f).fill("#90A4AE").width(1f)
        roundRect(bx - 0.12f, 0.7f, 0.24f, 0.018f, 0.008f).fill("#607D8B").width(0.8f)
        // damla
        path(closed = true) {
            moveTo(bx, 0.825f); quadTo(bx + 0.014f, 0.85f, bx, 0.858f); quadTo(bx - 0.014f, 0.85f, bx, 0.825f)
        }.fill(Sc.LIQUID).width(0.6f)
        erlenmeyer(bx, 0.78f, 0.84f, 1.02f, 0.04f, 0.17f, liquid = 0.94f, color = PINK, cr = 0.03f)
    }

    private val bunsen = sci(
        "sci.chem.bunsen", "Bunsen beki", 0.55f, 1f,
        tags = listOf("bunsen", "ısıtıcı", "alev", "laboratuvar"),
    ) { _ ->
        val cx = 0.26f
        // alev (mavi)
        path(closed = true) {
            moveTo(cx - 0.05f, 0.42f)
            cubicTo(cx - 0.1f, 0.3f, cx - 0.02f, 0.14f, cx, 0.04f)
            cubicTo(cx + 0.02f, 0.14f, cx + 0.1f, 0.3f, cx + 0.05f, 0.42f)
        }.fill("#BBD6FA").stroke("#5C8FE0").width(0.8f)
        path(closed = true) {
            moveTo(cx - 0.035f, 0.42f); quadTo(cx - 0.03f, 0.3f, cx, 0.24f); quadTo(cx + 0.03f, 0.3f, cx + 0.035f, 0.42f)
        }.fill("#3D7BE0").noStroke()
        // gaz girişi
        rect(cx + 0.1f, 0.855f, 0.16f, 0.032f).fill(Sc.METAL).width(0.9f)
        // taban
        path(closed = true) {
            moveTo(0.05f, 0.965f); lineTo(0.05f, 0.93f); quadTo(0.06f, 0.86f, cx, 0.855f); quadTo(0.46f, 0.86f, 0.47f, 0.93f); lineTo(0.47f, 0.965f)
        }.fill(Sc.METAL_DARK).width(1.1f)
        // gövde borusu
        rect(cx - 0.055f, 0.42f, 0.11f, 0.45f).fill(Sc.METAL).width(1.1f)
        line(cx - 0.03f, 0.44f, cx - 0.03f, 0.7f).stroke("#FFFFFF").width(1.6f).alpha(0.7f)
        // hava ayar bileziği
        rect(cx - 0.07f, 0.71f, 0.14f, 0.08f).fill(Sc.METAL_DARK).width(1f)
        roundRect(cx - 0.03f, 0.735f, 0.06f, 0.032f, 0.01f).fill("#37474F").noStroke()
    }

    private val spiritLamp = sci(
        "sci.chem.spirit_lamp", "İspirto ocağı", 0.7f, 1f,
        tags = listOf("ispirto", "ısıtıcı", "alev", "laboratuvar"),
    ) { _ ->
        val v = Vessel(
            0.35f,
            listOf(P(0.075f, 0.46f), P(0.1f, 0.5f), P(0.22f, 0.57f), P(0.29f, 0.7f), P(0.28f, 0.87f), P(0.24f, 0.95f)),
        )
        points(v.outline(), closed = true).fill(Sc.GLASS).noStroke()
        points(v.band(0.7f), closed = true).fill("#D9ECF7").noStroke()
        line(0.35f, 0.44f, 0.35f, 0.9f).stroke("#BDBDBD").width(2.4f)
        points(v.outline(), closed = true).width(1.3f)
        // fitil tutucu kapak
        roundRect(0.26f, 0.39f, 0.18f, 0.08f, 0.015f).fill(Sc.METAL).width(1.1f)
        rect(0.33f, 0.32f, 0.04f, 0.07f).fill("#F5F0E6").width(0.9f)
        flame(0.35f, 0.33f, 0.28f, 0.05f)
    }

    // ------------------------------------------------------------------ Düzenekler

    private val filtration = sci(
        "sci.chem.filtration", "Süzme (huni ve süzgeç kâğıdı)", 0.8f, 1.2f,
        tags = listOf("süzme", "huni", "süzgeç kağıdı", "karışım", "ayırma", "laboratuvar"),
    ) { _ ->
        val cx = 0.5f
        stand(0.1f, 0.04f, 1.14f, 0.02f, 0.3f)
        bar(0.113f, 0.24f, cx - 0.1f, 0.24f, 0.011f).fill(Sc.METAL).width(0.8f)
        arc(cx, 0.24f, 0.135f, 0.022f, 180f, 180f).width(1.4f)
        // beherglas
        val bl = 0.3f; val br = 0.74f; val bt = 0.7f; val bb = 1.14f
        rect(bl, bt, br - bl, bb - bt).fill(Sc.GLASS).noStroke()
        rect(bl, 1.0f, br - bl, bb - 1.0f).fill(Sc.LIQUID).noStroke()
        line(bl, 1.0f, br, 1.0f).stroke("#6FA8C8").width(0.7f)
        poly(bl - 0.03f, bt - 0.02f, bl, bt + 0.02f, bl, bb, br, bb, br, bt).width(1.3f)
        // huni
        path(closed = true) {
            moveTo(cx - 0.2f, 0.1f); lineTo(cx + 0.2f, 0.1f); lineTo(cx + 0.028f, 0.44f); lineTo(cx - 0.028f, 0.44f)
        }.fill(Sc.GLASS).width(1.3f)
        // süzgeç kâğıdı, karışım ve tortu
        poly(cx - 0.17f, 0.115f, cx, 0.42f, cx + 0.17f, 0.115f, closed = true).fill("#FFFFFF").width(0.8f)
        poly(cx - 0.11f, 0.22f, cx, 0.42f, cx + 0.11f, 0.22f, closed = true).fill("#D7C4A3").noStroke()
        poly(cx - 0.05f, 0.33f, cx, 0.42f, cx + 0.05f, 0.33f, closed = true).fill("#8D6E63").noStroke()
        line(cx, 0.42f, cx - 0.07f, 0.115f).width(0.6f)
        poly(cx - 0.17f, 0.115f, cx, 0.42f, cx + 0.17f, 0.115f, closed = true).width(0.8f)
        // sap
        path(closed = true) {
            moveTo(cx - 0.024f, 0.44f); lineTo(cx + 0.024f, 0.44f); lineTo(cx + 0.024f, 0.8f); lineTo(cx - 0.024f, 0.77f)
        }.fill(Sc.GLASS).width(1.2f)
        ellipse(cx, 0.1f, 0.2f, 0.022f).fill(Sc.GLASS).width(1.2f)
        // damla
        path(closed = true) {
            moveTo(cx + 0.02f, 0.83f); quadTo(cx + 0.034f, 0.855f, cx + 0.02f, 0.865f); quadTo(cx + 0.006f, 0.855f, cx + 0.02f, 0.83f)
        }.fill(Sc.LIQUID).width(0.6f)
    }

    private val separatoryFunnel = sci(
        "sci.chem.separatory_funnel", "Ayırma hunisi", 0.75f, 1.1f,
        tags = listOf("ayırma hunisi", "karışmayan sıvılar", "yoğunluk", "ayırma", "laboratuvar"),
    ) { _ ->
        val cx = 0.45f
        stand(0.1f, 0.04f, 1.04f, 0.02f, 0.3f)
        val v = Vessel(
            cx,
            listOf(
                P(0.035f, 0.1f), P(0.04f, 0.16f), P(0.15f, 0.24f), P(0.21f, 0.34f), P(0.19f, 0.45f),
                P(0.11f, 0.56f), P(0.045f, 0.64f), P(0.028f, 0.7f),
            ),
        )
        points(v.outline(), closed = true).fill(Sc.GLASS).noStroke()
        points(v.band(0.31f, 0.45f), closed = true).fill(OIL).noStroke()
        points(v.band(0.45f), closed = true).fill(Sc.LIQUID).noStroke()
        line(cx - v.w(0.31f), 0.31f, cx + v.w(0.31f), 0.31f).stroke("#C9A227").width(0.7f)
        line(cx - v.w(0.45f), 0.45f, cx + v.w(0.45f), 0.45f).stroke("#6FA8C8").width(0.7f)
        points(v.outline(), closed = true).width(1.3f)
        // kıskaç
        bar(0.113f, 0.4f, cx - v.w(0.4f), 0.4f, 0.011f).fill(Sc.METAL).width(0.8f)
        // tıpa
        roundRect(cx - 0.05f, 0.04f, 0.1f, 0.07f, 0.02f).fill("#D7CCC8").width(1.1f)
        // musluk ve sap
        rect(cx - 0.014f, 0.7f, 0.028f, 0.24f).fill(Sc.GLASS).width(1.1f)
        rect(cx - 0.035f, 0.72f, 0.07f, 0.045f).fill("#90A4AE").width(1f)
        roundRect(cx - 0.12f, 0.733f, 0.24f, 0.018f, 0.008f).fill("#607D8B").width(0.8f)
    }

    private val distillation = sci(
        "sci.chem.distillation", "Damıtma düzeneği", 1.8f, 1f,
        tags = listOf("damıtma", "soğutucu", "kaynama noktası", "ayırma", "laboratuvar"),
    ) { _ ->
        val fx = 0.3f; val fy = 0.55f; val fr = 0.15f; val nh = 0.026f
        stand(0.08f, 0.06f, 0.95f, 0.03f, 0.56f)
        // ısıtıcı
        roundRect(fx - 0.08f, 0.925f, 0.16f, 0.025f, 0.008f).fill(Sc.METAL_DARK).width(0.9f)
        rect(fx - 0.018f, 0.81f, 0.036f, 0.115f).fill(Sc.METAL).width(0.9f)
        path(closed = true) {
            moveTo(fx - 0.03f, 0.81f); cubicTo(fx - 0.05f, 0.77f, fx - 0.01f, 0.74f, fx, 0.715f)
            cubicTo(fx + 0.01f, 0.74f, fx + 0.05f, 0.77f, fx + 0.03f, 0.81f)
        }.fill("#BBD6FA").stroke("#5C8FE0").width(0.7f)
        // balon
        val neckY = fy - sqrt(fr * fr - nh * nh)
        val la0 = angleOf(fx, fy, fx - nh, neckY); val ra0 = angleOf(fx, fy, fx + nh, neckY)
        val sweep = -(360f - (ra0 - la0))
        path(closed = true) {
            moveTo(fx - nh, 0.14f); lineTo(fx - nh, neckY); arcTo(fx, fy, fr, fr, la0, sweep); lineTo(fx + nh, 0.14f)
        }.fill(Sc.GLASS).noStroke()
        val ly = 0.58f
        val hw = sqrt(fr * fr - (ly - fy) * (ly - fy))
        val la = angleOf(fx, fy, fx - hw, ly); val ra = angleOf(fx, fy, fx + hw, ly)
        path(closed = true) { moveTo(fx - hw, ly); arcTo(fx, fy, fr, fr, la, -(la - ra)) }.fill(Sc.LIQUID).noStroke()
        line(fx - hw, ly, fx + hw, ly).stroke("#6FA8C8").width(0.6f)
        for ((bx, by, br) in listOf(Triple(fx - 0.05f, 0.64f, 0.012f), Triple(fx + 0.03f, 0.62f, 0.01f), Triple(fx + 0.07f, 0.66f, 0.008f), Triple(fx - 0.01f, 0.67f, 0.009f))) {
            circle(bx, by, br).width(0.6f)
        }
        // yan kol
        val a1 = P(fx + nh, 0.215f); val p1 = P(0.56f, 0.31f)
        bar(a1.x - 0.005f, a1.y + 0.013f, p1.x, p1.y, 0.012f).fill(Sc.GLASS).width(1.1f)
        path { moveTo(fx - nh, 0.14f); lineTo(fx - nh, neckY); arcTo(fx, fy, fr, fr, la0, sweep); lineTo(fx + nh, 0.14f) }.width(1.3f)
        // termometre ve tıpa
        rect(fx - 0.036f, 0.1f, 0.072f, 0.06f).fill("#8D6E63").width(1f)
        rect(fx - 0.008f, 0.02f, 0.016f, 0.2f).fill("#FFFFFF").width(0.8f)
        line(fx, 0.05f, fx, 0.21f).stroke(Sc.RED).width(1.2f)
        circle(fx, 0.225f, 0.013f).fill(Sc.RED).width(0.8f)
        // balon kıskacı
        bar(0.093f, 0.13f, fx - 0.036f, 0.13f, 0.01f).fill(Sc.METAL).width(0.8f)
        // soğutucu (Liebig)
        val p2 = P(1.44f, 0.7f)
        val j1 = lerp(p1, p2, 0.1f); val j2 = lerp(p1, p2, 0.86f)
        val u = unit(p2.x - p1.x, p2.y - p1.y)
        // su giriş/çıkış boruları
        val inP = lerp(p1, p2, 0.78f); val outP = lerp(p1, p2, 0.2f)
        bar(inP.x, inP.y, inP.x, inP.y + 0.16f, 0.015f).fill("#D6EEFA").width(1f)
        bar(outP.x, outP.y, outP.x, outP.y - 0.16f, 0.015f).fill("#D6EEFA").width(1f)
        vec(inP.x + 0.06f, inP.y + 0.24f, inP.x + 0.06f, inP.y + 0.12f, 0.03f, Sc.BLUE, 1f)
        vec(outP.x + 0.06f, outP.y - 0.1f, outP.x + 0.06f, outP.y - 0.22f, 0.03f, Sc.BLUE, 1f)
        bar(j1.x, j1.y, j2.x, j2.y, 0.06f).fill("#D6EEFA").width(1.2f)
        bar(p1.x, p1.y, p2.x, p2.y, 0.017f).fill("#FFFFFF").width(1f)
        // alıcı erlenmeyer
        erlenmeyer(1.47f, 0.66f, 0.72f, 0.95f, 0.045f, 0.17f, liquid = 0.88f, cr = 0.03f)
        bar(p2.x - u.x * 0.02f, p2.y - u.y * 0.02f, p2.x, p2.y, 0.017f).fill("#FFFFFF").width(1f)
    }

    // ------------------------------------------------------------------ Atom ve moleküller

    // v46: bohrAtom, periodicCell → ParamElements.kt (yazılan elemente göre çizilir)
    // v45: su, metan ve benzen ParamChemistry.kt'de (formülden çizilen molekül; kimlikler aynı)
    private val nacl = sci(
        "sci.chem.nacl_lattice", "NaCl kristal örgüsü", 1f, 1f, Fit.SQUARE,
        tags = listOf("sodyum klorür", "iyonik bağ", "kristal", "örgü", "tuz"),
    ) { _ ->
        val yaw = Math.toRadians(32.0); val pitch = Math.toRadians(22.0)
        val s = 0.19
        fun proj(i: Int, j: Int, k: Int): Pair<P, Double> {
            val x = (i - 1) * s; val y = (1 - j) * s; val z = (k - 1) * s
            val x1 = x * cos(yaw) + z * sin(yaw); val z1 = -x * sin(yaw) + z * cos(yaw)
            val y2 = y * cos(pitch) - z1 * sin(pitch); val z2 = y * sin(pitch) + z1 * cos(pitch)
            return P(0.5f + x1.toFloat(), 0.5f - y2.toFloat()) to z2
        }
        // çizim öğeleri derinliğe göre (uzaktan yakına)
        data class Item(val z: Double, val draw: VectorBuilder.() -> Unit)
        val items = ArrayList<Item>()
        for (i in 0..2) for (j in 0..2) for (k in 0..2) {
            val (p, z) = proj(i, j, k)
            val na = (i + j + k) % 2 == 0
            items += Item(z) {
                val r = if (na) 0.042f else 0.068f
                circle(p.x, p.y, r).fill(if (na) "#9575CD" else "#81C784").width(0.8f)
                shine(p.x, p.y, r)
            }
            for ((di, dj, dk) in listOf(Triple(1, 0, 0), Triple(0, 1, 0), Triple(0, 0, 1))) {
                if (i + di > 2 || j + dj > 2 || k + dk > 2) continue
                val (q, zq) = proj(i + di, j + dj, k + dk)
                items += Item((z + zq) / 2 - 0.001) { line(p.x, p.y, q.x, q.y).stroke("#78909C").width(0.8f) }
            }
        }
        items.sortedBy { it.z }.forEach { it.draw(this) }
    }

    private val phScale = sci(
        "sci.chem.ph_scale", "pH çizelgesi", 2.4f, 0.75f,
        tags = listOf("ph", "asit", "baz", "nötr", "indikatör", "turnusol"),
    ) { lb ->
        val cols = listOf(
            "#EE1C25", "#F26724", "#F8C611", "#F5ED1C", "#B5D333", "#84C341", "#4DB749", "#33A94B",
            "#22B46B", "#0AB8B6", "#4690CD", "#3853A4", "#5A51A2", "#63459D", "#462C83",
        )
        val x0 = 0.075f; val cw = 0.15f; val t = 0.1f; val h = 0.26f
        for (i in cols.indices) {
            rect(x0 + i * cw, t, cw, h).fill(cols[i]).width(0.8f)
            lb.c(x0 + (i + 0.5f) * cw, t + h + 0.07f, "$i", 0.075f)
        }
        rect(x0, t, cw * 15, h).width(1.3f)
        val ay = t + h + 0.17f
        vec(x0 + 6.3f * cw, ay, x0 + 0.1f * cw, ay, 0.04f, width = 1f)
        vec(x0 + 8.7f * cw, ay, x0 + 14.9f * cw, ay, 0.04f, width = 1f)
        lb.c(x0 + 3.2f * cw, ay + 0.09f, "asidik", 0.08f)
        lb.c(x0 + 7.5f * cw, ay, "nötr", 0.08f)
        lb.c(x0 + 11.8f * cw, ay + 0.09f, "bazik", 0.08f)
    }
}
