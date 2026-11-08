package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Biyoloji ve tıp: hücreler, organeller, organlar, sistemler, mikroskop ve çiçek. */
internal object Biology {

    val category: LibCategory by lazy {
        LibCategory(
            id = "sci_biology",
            title = "Biyoloji & Tıp",
            audience = Audience.NOTES,
            section = "Fen",
            shapes = listOf(
                animalCell, plantCell, mitochondrion, bacterium, virus, bloodCells,
                neuron, dna, heart, lungs, eye, tooth,
                kidney, brain, digestive, skeleton, microscope, flower,
                PopulationModel.shape,
            ),
        )
    }

    private const val CYTO = "#FDEBD8"
    private const val NUCLEUS = "#D7C9EF"
    private const val NUCLEOLUS = "#8E6CC9"
    private const val MITO = "#FFB74D"
    private const val CHLORO = "#66BB6A"
    private const val BONE = "#FAF4E4"

    /** Belirlenimci sözde rastgele sayılar (her çizimde aynı sonuç). */
    private class Rng(private var s: Long) {
        fun next(): Float {
            s = s * 6364136223846793005L + 1442695040888963407L
            return ((s ushr 33) % 100000L).toFloat() / 100000f
        }
    }

    /** Kapalı yumuşak şekil (Catmull-Rom). */
    private fun VectorBuilder.blob(vararg xy: Float) = smooth(*xy, closed = true)

    /** Mitokondri (küçük): dış zar + zikzak kristalar. */
    private fun VectorBuilder.mito(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float) {
        group(rotate = rot, px = cx, py = cy) {
            ellipse(cx, cy, rx, ry).fill(MITO).width(1f)
            val n = 7
            val pts = FloatArray((n + 1) * 2)
            for (i in 0..n) {
                pts[i * 2] = cx - rx * 0.72f + rx * 1.44f * i / n
                pts[i * 2 + 1] = cy + (if (i % 2 == 0) -1f else 1f) * ry * 0.55f
            }
            poly(*pts).width(0.7f)
        }
    }

    /** Kloroplast: yeşil elips + tilakoit yığınları (granum). */
    private fun VectorBuilder.chloroplast(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float) {
        group(rotate = rot, px = cx, py = cy) {
            ellipse(cx, cy, rx, ry).fill(CHLORO).width(1f)
            for (k in -1..1) rect(cx + k * rx * 0.5f - 0.007f, cy - ry * 0.55f, 0.014f, ry * 1.1f).fill("#2E7D32").noStroke()
        }
    }

    // ------------------------------------------------------------------ Hücreler

    private val animalCell = sci(
        "sci.bio.animal_cell", "Hayvan hücresi", 1.3f, 1f,
        tags = listOf("hücre", "organel", "çekirdek", "mitokondri", "golgi", "endoplazmik retikulum", "ribozom"),
    ) { _ ->
        blob(1.2f, 0.48f, 1.12f, 0.78f, 0.86f, 0.92f, 0.52f, 0.93f, 0.22f, 0.84f, 0.08f, 0.6f, 0.1f, 0.32f, 0.3f, 0.1f, 0.62f, 0.07f, 0.95f, 0.13f, 1.16f, 0.28f)
            .fill(CYTO).width(1.7f)
        val nx = 0.58f; val ny = 0.47f
        // granüllü endoplazmik retikulum (çekirdeğin sağında)
        val rng = Rng(7)
        for (k in 0..2) {
            val r0 = 0.215f + k * 0.035f
            val pts = List(40) { i ->
                val a = Math.toRadians(-55.0 + 110.0 * i / 39)
                val rr = r0 + 0.008f * sin(a * 14).toFloat()
                P(nx + rr * cos(a).toFloat(), ny + rr * sin(a).toFloat())
            }
            points(pts).width(0.9f)
            for (i in 2 until 40 step 4) dot(pts[i].x, pts[i].y + 0.006f * (if (i % 8 == 2) 1 else -1), 0.006f)
        }
        // golgi aygıtı
        for (i in 0..3) {
            val cx = 0.33f; val cy = 0.7f + i * 0.042f; val rx = 0.12f - i * 0.012f
            path(closed = true) {
                arcTo(cx, cy, rx, 0.06f, 200f, 140f)
                arcTo(cx, cy + 0.02f, rx * 0.95f, 0.05f, 340f, -140f)
            }.fill("#F48FB1").width(0.8f)
        }
        circle(0.2f, 0.72f, 0.014f).fill("#F48FB1").width(0.7f)
        circle(0.47f, 0.78f, 0.012f).fill("#F48FB1").width(0.7f)
        // çekirdek
        circle(nx, ny, 0.17f).fill(NUCLEUS).width(1.3f)
        circle(nx, ny, 0.155f).dashed().width(0.5f)
        circle(nx + 0.03f, ny - 0.03f, 0.055f).fill(NUCLEOLUS).width(0.8f)
        smooth(nx - 0.1f, ny + 0.02f, nx - 0.06f, ny + 0.07f, nx - 0.02f, ny + 0.05f, nx + 0.03f, ny + 0.1f).width(0.6f)
        smooth(nx - 0.08f, ny - 0.07f, nx - 0.04f, ny - 0.1f, nx - 0.05f, ny - 0.03f).width(0.6f)
        // mitokondriler
        mito(0.97f, 0.74f, 0.085f, 0.04f, 20f)
        mito(0.28f, 0.3f, 0.08f, 0.038f, -30f)
        mito(0.9f, 0.2f, 0.075f, 0.036f, 10f)
        // lizozomlar, koful, sentrioller
        for ((x, y) in listOf(0.76f to 0.84f, 0.17f to 0.5f)) {
            circle(x, y, 0.03f).fill("#A5D6A7").width(0.9f)
            dot(x - 0.008f, y - 0.006f, 0.005f); dot(x + 0.01f, y + 0.004f, 0.005f)
        }
        circle(1.05f, 0.47f, 0.045f).fill("#D6EEFA").width(0.9f)
        group(rotate = 25f, px = 0.62f, py = 0.79f) {
            rect(0.59f, 0.78f, 0.06f, 0.022f).fill("#90A4AE").width(0.8f)
            rect(0.64f, 0.755f, 0.022f, 0.06f).fill("#90A4AE").width(0.8f)
        }
        // serbest ribozomlar
        var placed = 0
        while (placed < 26) {
            val x = 0.15f + rng.next() * 1.0f; val y = 0.14f + rng.next() * 0.74f
            val ex = (x - 0.65f) / 0.5f; val ey = (y - 0.5f) / 0.36f
            if (ex * ex + ey * ey > 1f) continue
            val dx = x - nx; val dy = y - ny
            if (dx * dx + dy * dy < 0.3f * 0.3f) continue
            if (x < 0.48f && y > 0.62f) continue
            dot(x, y, 0.006f); placed++
        }
    }

    private val plantCell = sci(
        "sci.bio.plant_cell", "Bitki hücresi", 1.3f, 1f,
        tags = listOf("hücre", "hücre duvarı", "kloroplast", "koful", "organel", "bitki"),
    ) { _ ->
        roundRect(0.05f, 0.06f, 1.2f, 0.88f, 0.07f).fill("#AED581").width(1.6f)
        roundRect(0.085f, 0.095f, 1.13f, 0.81f, 0.05f).fill("#F4F9E5").width(1f)
        blob(0.5f, 0.2f, 0.9f, 0.18f, 1.1f, 0.3f, 1.12f, 0.6f, 1.0f, 0.8f, 0.65f, 0.82f, 0.47f, 0.7f, 0.44f, 0.42f)
            .fill("#D3ECFA").width(1.1f)
        // çekirdek ve ER
        val nx = 0.26f; val ny = 0.32f
        for (k in 0..1) arc(nx, ny, 0.14f + k * 0.03f, 0.14f + k * 0.03f, 10f, 100f).width(0.8f)
        circle(nx, ny, 0.11f).fill(NUCLEUS).width(1.2f)
        circle(nx + 0.02f, ny - 0.02f, 0.035f).fill(NUCLEOLUS).width(0.8f)
        // kloroplastlar ve mitokondriler
        chloroplast(0.15f, 0.6f, 0.07f, 0.032f, 80f)
        chloroplast(0.27f, 0.62f, 0.065f, 0.03f, 30f)
        chloroplast(0.22f, 0.82f, 0.07f, 0.032f, -20f)
        chloroplast(0.42f, 0.86f, 0.07f, 0.032f, 0f)
        chloroplast(1.17f, 0.45f, 0.07f, 0.03f, 90f)
        chloroplast(0.72f, 0.13f, 0.07f, 0.028f, 0f)
        chloroplast(0.86f, 0.87f, 0.065f, 0.028f, -5f)
        mito(0.4f, 0.6f, 0.055f, 0.026f, -50f)
        mito(0.64f, 0.87f, 0.055f, 0.026f, 0f)
        mito(0.44f, 0.14f, 0.05f, 0.024f, 10f)
        val rng = Rng(11)
        repeat(14) {
            val x = 0.12f + rng.next() * 0.3f; val y = 0.46f + rng.next() * 0.42f
            dot(x, y, 0.005f)
        }
    }

    private val mitochondrion = sci(
        "sci.bio.mitochondrion", "Mitokondri", 1.6f, 1f,
        tags = listOf("organel", "kristalar", "matriks", "enerji", "ATP", "hücresel solunum"),
    ) { _ ->
        val cx = 0.8f; val cy = 0.5f
        ellipse(cx, cy, 0.74f, 0.4f).fill("#FFB74D").width(1.6f)
        // iç zar: kıvrımlı (kristalar), matriks rengiyle dolu
        val rx = 0.67f; val ry = 0.33f; val fw = 0.028f
        val topFolds = listOf(0.3f, 0.56f, 0.82f, 1.08f, 1.3f)
        val botFolds = listOf(0.43f, 0.69f, 0.95f, 1.2f)
        fun yTop(x: Float) = cy - ry * kotlin.math.sqrt((1f - ((x - cx) / rx).let { it * it }).coerceAtLeast(0f))
        fun yBot(x: Float) = cy + ry * kotlin.math.sqrt((1f - ((x - cx) / rx).let { it * it }).coerceAtLeast(0f))
        val pts = ArrayList<P>()
        val steps = 120
        // üst kenar soldan sağa; kıvrım aralığındaki örnekler atlanır, kıvrım girişte eklenir
        var prev = -10f
        for (i in 0..steps) {
            val x = cx - rx + 2 * rx * i / steps
            for (f in topFolds) if (prev < f - fw && x >= f - fw) {
                val depth = cy + 0.13f
                pts += P(f - fw, yTop(f - fw)); pts += P(f - fw, depth - fw)
                for (k in 0..8) { val a = PI - PI * k / 8; pts += P(f + fw * cos(a).toFloat(), depth - fw + fw * sin(a).toFloat()) }
                pts += P(f + fw, yTop(f + fw))
            }
            if (topFolds.none { abs(x - it) <= fw }) pts += P(x, yTop(x))
            prev = x
        }
        // alt kenar sağdan sola
        prev = 10f
        for (i in 0..steps) {
            val x = cx + rx - 2 * rx * i / steps
            for (f in botFolds) if (prev > f + fw && x <= f + fw) {
                val height = cy - 0.13f
                pts += P(f + fw, yBot(f + fw)); pts += P(f + fw, height + fw)
                for (k in 0..8) { val a = -PI * k / 8; pts += P(f + fw * cos(a).toFloat(), height + fw + fw * sin(a).toFloat()) }
                pts += P(f - fw, yBot(f - fw))
            }
            if (botFolds.none { abs(x - it) <= fw }) pts += P(x, yBot(x))
            prev = x
        }
        points(pts, closed = true).fill("#FFF3E0").width(1.2f)
        val rng = Rng(3)
        repeat(22) {
            val x = cx - 0.55f + rng.next() * 1.1f; val y = cy - 0.2f + rng.next() * 0.4f
            if (topFolds.none { abs(it - x) < 0.05f } && botFolds.none { abs(it - x) < 0.05f }) dot(x, y, 0.007f)
        }
        circle(0.52f, 0.52f, 0.03f).width(0.8f)
        circle(1.12f, 0.46f, 0.026f).width(0.8f)
    }

    private val bacterium = sci(
        "sci.bio.bacterium", "Bakteri", 1.6f, 1f,
        tags = listOf("prokaryot", "kamçı", "kapsül", "hücre duvarı", "plazmit", "mikroorganizma"),
    ) { _ ->
        // kamçı
        curve(samples = 80) { t ->
            val x = 1.22f + 0.36f * t
            P(x, 0.5f + 0.06f * sin(2.0 * PI * 2.2 * t).toFloat() * t.coerceAtLeast(0.2f))
        }.width(1.3f)
        // pililer
        val cx = 0.72f; val cy = 0.5f
        for (i in 0 until 22) {
            val a = 360f * i / 22
            val p = rodPoint(cx, cy, 0.5f, 0.24f, a)
            val q = rodPoint(cx, cy, 0.56f, 0.3f, a)
            if (abs(a) < 12f || abs(a - 360f) < 12f) continue
            line(p.x, p.y, q.x, q.y).width(0.7f)
        }
        roundRect(0.2f, 0.25f, 1.04f, 0.5f, 0.25f).fill("#F1F8E9").width(0.9f).dashed()
        roundRect(0.24f, 0.29f, 0.96f, 0.42f, 0.21f).fill("#C5E1A5").width(1.3f)
        roundRect(0.275f, 0.325f, 0.89f, 0.35f, 0.175f).fill("#EAF5DC").width(0.9f)
        // nükleoid (DNA)
        smooth(
            0.52f, 0.46f, 0.6f, 0.4f, 0.7f, 0.45f, 0.78f, 0.39f, 0.88f, 0.44f, 0.9f, 0.52f, 0.82f, 0.58f,
            0.72f, 0.53f, 0.64f, 0.6f, 0.55f, 0.56f, 0.6f, 0.5f, 0.7f, 0.5f, 0.66f, 0.44f, 0.56f, 0.52f,
            closed = true,
        ).stroke("#7B1FA2").width(1.2f)
        circle(1.0f, 0.42f, 0.03f).stroke("#7B1FA2").width(1f)
        circle(0.42f, 0.58f, 0.025f).stroke("#7B1FA2").width(1f)
        val rng = Rng(5)
        var n = 0
        while (n < 24) {
            val x = 0.34f + rng.next() * 0.76f; val y = 0.36f + rng.next() * 0.28f
            if (x > 0.5f && x < 0.93f && y > 0.37f && y < 0.61f) continue
            dot(x, y, 0.008f); n++
        }
    }

    /** Çubuk (kapsül) şeklin sınırında açıya göre nokta. */
    private fun rodPoint(cx: Float, cy: Float, halfLen: Float, r: Float, a: Float): P {
        val d = unit(cos(Math.toRadians(a.toDouble())).toFloat(), sin(Math.toRadians(a.toDouble())).toFloat())
        // yarım daireli uçlar: merkez çizgisi (cx±(halfLen−r))
        val core = halfLen - r
        var t = 0f; var p = P(cx, cy)
        while (t < 2f) {
            p = P(cx + d.x * t, cy + d.y * t)
            val qx = (abs(p.x - cx) - core).coerceAtLeast(0f)
            if (qx * qx + (p.y - cy) * (p.y - cy) >= r * r) break
            t += 0.004f
        }
        return p
    }

    private val virus = sci(
        "sci.bio.virus", "Virüs (bakteriyofaj)", 0.7f, 1f,
        tags = listOf("virüs", "bakteriyofaj", "kapsid", "DNA", "kuyruk"),
    ) { _ ->
        val cx = 0.35f; val hy = 0.2f; val hr = 0.16f
        // kuyruk lifleri
        for (s in listOf(-1f, 1f)) {
            poly(cx + s * 0.05f, 0.7f, cx + s * 0.2f, 0.76f, cx + s * 0.22f, 0.95f).width(1.3f)
            poly(cx + s * 0.08f, 0.705f, cx + s * 0.29f, 0.72f, cx + s * 0.31f, 0.9f).width(1.3f)
            poly(cx + s * 0.03f, 0.715f, cx + s * 0.1f, 0.82f, cx + s * 0.1f, 0.97f).width(1.3f)
        }
        // kuyruk kını
        rect(cx - 0.045f, 0.37f, 0.09f, 0.31f).fill("#D1C4E9").width(1.2f)
        for (i in 1..11) line(cx - 0.045f, 0.37f + i * 0.026f, cx + 0.045f, 0.37f + i * 0.026f).width(0.6f)
        polygon(cx, 0.695f, 0.11f, 6, rotate = 30f, ry = 0.025f).fill("#9575CD").width(1.1f)
        rect(cx - 0.06f, 0.355f, 0.12f, 0.02f).fill("#9575CD").width(1f)
        // baş (ikozahedral kapsid)
        val v = List(6) { onCircle(cx, hy, hr, -90f + 60f * it) }
        points(v, closed = true).fill("#B39DDB").width(1.3f)
        val inner = List(3) { onCircle(cx, hy, hr * 0.5f, 90f + 120f * it) }
        points(inner, closed = true).width(0.7f)
        for (i in 0 until 3) {
            val a = inner[i]
            line(a.x, a.y, v[(i * 2 + 3) % 6].x, v[(i * 2 + 3) % 6].y).width(0.7f)
        }
        line(inner[0].x, inner[0].y, v[2].x, v[2].y).width(0.7f)
        line(inner[0].x, inner[0].y, v[4].x, v[4].y).width(0.7f)
        line(inner[1].x, inner[1].y, v[0].x, v[0].y).width(0.7f)
        line(inner[2].x, inner[2].y, v[0].x, v[0].y).width(0.7f)
        line(inner[1].x, inner[1].y, v[4].x, v[4].y).width(0.7f)
        line(inner[2].x, inner[2].y, v[2].x, v[2].y).width(0.7f)
        smooth(cx - 0.07f, hy + 0.02f, cx - 0.03f, hy - 0.06f, cx + 0.04f, hy - 0.02f, cx - 0.01f, hy + 0.05f, cx + 0.07f, hy + 0.06f)
            .stroke("#7B1FA2").width(1.1f)
    }

    private val bloodCells = sci(
        "sci.bio.blood_cells", "Kan hücreleri", 1.4f, 1f,
        tags = listOf("alyuvar", "akyuvar", "kan pulcuğu", "trombosit", "kan", "lökosit", "eritrosit"),
    ) { _ ->
        fun rbc(x: Float, y: Float, r: Float) {
            circle(x, y, r).fill("#E53935").width(1f)
            circle(x, y, r * 0.5f).fill("#EF8784").noStroke()
            circle(x, y, r * 0.72f).stroke("#C62828").width(0.5f).alpha(0.6f)
        }
        rbc(0.22f, 0.27f, 0.13f)
        rbc(0.47f, 0.7f, 0.13f)
        rbc(1.2f, 0.28f, 0.12f)
        rbc(1.14f, 0.72f, 0.12f)
        // yandan görünen alyuvar (iki yüzü çukur disk)
        val sx = 0.8f; val sy = 0.84f; val r = 0.14f
        val rel = floatArrayOf(-1f, 0f, -0.75f, -0.34f, -0.35f, -0.26f, 0f, -0.13f, 0.35f, -0.26f, 0.75f, -0.34f, 1f, 0f, 0.75f, 0.34f, 0.35f, 0.26f, 0f, 0.13f, -0.35f, 0.26f, -0.75f, 0.34f)
        val pts = FloatArray(rel.size) { i -> if (i % 2 == 0) sx + rel[i] * r else sy + rel[i] * r }
        smooth(*pts, closed = true).fill("#E53935").width(1f)
        // akyuvar (nötrofil)
        val wx = 0.78f; val wy = 0.38f
        circle(wx, wy, 0.2f).fill("#EDE7F6").width(1.1f)
        val rng = Rng(9)
        repeat(30) {
            val a = rng.next() * 2 * PI; val d = 0.04f + rng.next() * 0.14f
            dot(wx + d * cos(a).toFloat(), wy + d * sin(a).toFloat(), 0.006f, "#B39DDB")
        }
        bar(wx - 0.07f, wy - 0.01f, wx + 0.01f, wy - 0.06f, 0.012f).fill("#7E57C2").noStroke()
        bar(wx + 0.01f, wy - 0.06f, wx + 0.07f, wy + 0.03f, 0.012f).fill("#7E57C2").noStroke()
        circle(wx - 0.07f, wy - 0.01f, 0.05f).fill("#7E57C2").width(0.8f)
        circle(wx + 0.01f, wy - 0.07f, 0.048f).fill("#7E57C2").width(0.8f)
        circle(wx + 0.075f, wy + 0.04f, 0.05f).fill("#7E57C2").width(0.8f)
        // lenfosit
        circle(0.2f, 0.72f, 0.11f).fill("#EDE7F6").width(1.1f)
        circle(0.21f, 0.72f, 0.083f).fill("#5E35B1").width(0.8f)
        // kan pulcukları
        for ((x, y) in listOf(0.46f to 0.36f, 0.53f to 0.44f, 1.3f to 0.52f, 1.0f to 0.1f, 0.38f to 0.94f, 1.02f to 0.52f)) {
            blob(x - 0.025f, y, x - 0.005f, y - 0.018f, x + 0.022f, y - 0.01f, x + 0.02f, y + 0.014f, x - 0.004f, y + 0.018f)
                .fill("#CE93D8").width(0.8f)
        }
    }

    // ------------------------------------------------------------------ Sinir ve kalıtım

    /** Dallanan çizgi (dendrit, bronşçuk): özyinelemeli. */
    private fun VectorBuilder.branch(x: Float, y: Float, angle: Float, len: Float, w: Float, depth: Int, spread: Float = 28f, color: String? = null) {
        val e = onCircle(x, y, len, angle)
        line(x, y, e.x, e.y).width(w).stroke(color)
        if (depth <= 0) return
        branch(e.x, e.y, angle - spread, len * 0.66f, w * 0.7f, depth - 1, spread, color)
        branch(e.x, e.y, angle + spread * 0.9f, len * 0.6f, w * 0.7f, depth - 1, spread, color)
    }

    private val neuron = sci(
        "sci.bio.neuron", "Nöron (sinir hücresi)", 2f, 0.9f,
        tags = listOf("sinir hücresi", "akson", "dendrit", "miyelin kılıf", "sinaps", "sinir sistemi"),
    ) { _ ->
        val sx = 0.36f; val sy = 0.45f
        // dendritler (gövdeyle birlikte biraz büyütülmüş)
        group(sx = 1.25f, px = sx, py = sy) {
        branch(0.3f, 0.35f, -135f, 0.11f, 2.2f, 2)
        branch(0.26f, 0.46f, 178f, 0.12f, 2.2f, 2)
        branch(0.3f, 0.56f, 138f, 0.11f, 2.2f, 2)
        branch(0.4f, 0.32f, -80f, 0.1f, 2f, 2)
        branch(0.4f, 0.58f, 85f, 0.1f, 2f, 2)
        }
        // akson
        val axon = spline(0.5f, 0.45f, 0.8f, 0.43f, 1.1f, 0.47f, 1.4f, 0.44f, 1.72f, 0.46f)
        points(axon).width(1.6f)
        fun yAt(x: Float): Float = axon.minByOrNull { abs(it.x - x) }!!.y
        var x = 0.6f
        while (x + 0.17f < 1.66f) {
            val y = yAt(x + 0.085f)
            roundRect(x, y - 0.04f, 0.17f, 0.08f, 0.04f).fill("#FFE9B0").width(1f)
            x += 0.205f
        }
        // akson uçları
        for ((ex, ey) in listOf(1.88f to 0.3f, 1.93f to 0.43f, 1.9f to 0.57f, 1.8f to 0.66f)) {
            smooth(1.72f, 0.46f, (1.72f + ex) / 2f, (0.46f + ey) / 2f + 0.01f, ex, ey).width(1.1f)
            circle(ex, ey, 0.022f).fill("#F5C6D6").width(0.9f)
        }
        // hücre gövdesi
        group(sx = 1.25f, px = sx, py = sy) {
            blob(0.52f, 0.45f, 0.44f, 0.56f, 0.36f, 0.6f, 0.27f, 0.55f, 0.25f, 0.44f, 0.3f, 0.33f, 0.4f, 0.31f, 0.47f, 0.37f)
                .fill("#F5C6D6").width(1.3f)
            circle(sx, sy, 0.05f).fill("#9C7BD1").width(1f)
            dot(sx + 0.01f, sy - 0.01f, 0.012f)
        }
    }

    private val dna = sci(
        "sci.bio.dna", "DNA çift sarmalı", 0.62f, 1f,
        tags = listOf("DNA", "kalıtım", "gen", "nükleotit", "baz eşleşmesi", "sarmal"),
    ) { _ ->
        val cx = 0.31f; val amp = 0.2f; val period = 0.46f; val y0 = 0.04f; val y1 = 0.96f
        fun phase(y: Float) = 2.0 * PI * (y - y0) / period
        fun xs(y: Float, strand: Int) = cx + (if (strand == 0) 1f else -1f) * amp * sin(phase(y)).toFloat()
        fun front(y: Float, strand: Int) = (if (strand == 0) 1.0 else -1.0) * cos(phase(y)) > 0
        fun runs(strand: Int, wantFront: Boolean): List<List<P>> {
            val out = ArrayList<List<P>>(); var cur = ArrayList<P>()
            val n = 240
            for (i in 0..n) {
                val y = y0 + (y1 - y0) * i / n
                if (front(y, strand) == wantFront) cur += P(xs(y, strand), y)
                else if (cur.isNotEmpty()) { out += cur; cur = ArrayList() }
            }
            if (cur.size > 1) out += cur
            return out.filter { it.size > 1 }
        }
        for (s in 0..1) for (r in runs(s, false)) tube(r, 0.018f).fill("#C5CAE9").width(0.6f)
        val baseCols = listOf("#EF5350" to "#FFCA28", "#66BB6A" to "#42A5F5", "#FFCA28" to "#EF5350", "#42A5F5" to "#66BB6A")
        var y = y0 + 0.02f; var k = 0
        while (y < y1 - 0.01f) {
            val a = xs(y, 0); val b = xs(y, 1)
            if (abs(a - b) > 0.05f) {
                val m = (a + b) / 2f
                val (c1, c2) = baseCols[k % baseCols.size]
                line(a, y, m, y).stroke(c1).width(2.6f)
                line(m, y, b, y).stroke(c2).width(2.6f)
            }
            y += 0.037f; k++
        }
        for (s in 0..1) for (r in runs(s, true)) tube(r, 0.02f).fill("#5C6BC0").width(1f)
    }

    // ------------------------------------------------------------------ Organlar

    private val heart = sci(
        "sci.bio.heart", "Kalp (kesit)", 1f, 1.1f,
        tags = listOf("kalp", "kulakçık", "karıncık", "aort", "dolaşım sistemi", "anatomi", "damar"),
    ) { _ ->
        val blue = "#7FA8D8"; val red = "#E57373"
        // arkadaki damarlar: sağ akciğer atardamarı, alt ana toplardamar
        tube(spline(0.58f, 0.31f, 0.4f, 0.3f, 0.06f, 0.33f), 0.03f).fill(blue).width(1f)
        tube(spline(0.3f, 0.62f, 0.3f, 1.03f), 0.042f).fill(blue).width(1f)
        // aort ve dalları
        for ((a, b) in listOf(P(0.58f, 0.15f) to P(0.54f, 0.02f), P(0.66f, 0.12f) to P(0.66f, 0.0f), P(0.74f, 0.13f) to P(0.78f, 0.02f))) {
            tube(listOf(a, b), 0.018f).fill(red).width(0.9f)
        }
        tube(spline(0.5f, 0.64f, 0.49f, 0.4f, 0.52f, 0.22f, 0.61f, 0.12f, 0.73f, 0.12f, 0.81f, 0.2f, 0.83f, 0.42f), 0.05f).fill(red).width(1.1f)
        // üst ana toplardamar
        tube(spline(0.3f, 0.03f, 0.3f, 0.47f), 0.05f).fill(blue).width(1.1f)
        // akciğer toplardamarları
        tube(spline(0.8f, 0.47f, 0.98f, 0.44f), 0.022f).fill(red).width(0.9f)
        tube(spline(0.8f, 0.55f, 0.98f, 0.57f), 0.022f).fill(red).width(0.9f)
        // kalp kası
        blob(0.3f, 0.38f, 0.18f, 0.46f, 0.14f, 0.6f, 0.2f, 0.76f, 0.34f, 0.9f, 0.5f, 0.99f, 0.62f, 1.04f, 0.74f, 0.97f, 0.86f, 0.8f, 0.9f, 0.62f, 0.87f, 0.45f, 0.76f, 0.37f, 0.6f, 0.36f, 0.45f, 0.36f)
            .fill("#E27B72").width(1.5f)
        // odacıklar
        val rBlue = "#9CC3EB"; val rRed = "#F6B3AC"
        blob(0.24f, 0.47f, 0.2f, 0.57f, 0.24f, 0.66f, 0.34f, 0.67f, 0.41f, 0.6f, 0.4f, 0.49f, 0.32f, 0.44f).fill(rBlue).width(1f)
        blob(0.26f, 0.74f, 0.33f, 0.84f, 0.46f, 0.92f, 0.52f, 0.86f, 0.51f, 0.74f, 0.45f, 0.7f, 0.34f, 0.7f).fill(rBlue).width(1f)
        blob(0.61f, 0.47f, 0.61f, 0.57f, 0.68f, 0.62f, 0.8f, 0.6f, 0.84f, 0.51f, 0.77f, 0.44f, 0.67f, 0.44f).fill(rRed).width(1f)
        blob(0.6f, 0.7f, 0.57f, 0.82f, 0.61f, 0.95f, 0.7f, 0.92f, 0.8f, 0.8f, 0.82f, 0.68f, 0.72f, 0.66f).fill(rRed).width(1f)
        // kapakçıklar
        for ((a, b) in listOf(P(0.29f, 0.675f) to P(0.32f, 0.735f), P(0.4f, 0.665f) to P(0.37f, 0.725f), P(0.65f, 0.64f) to P(0.68f, 0.7f), P(0.78f, 0.625f) to P(0.75f, 0.69f))) {
            line(a.x, a.y, b.x, b.y).stroke("#FFFFFF").width(1.8f)
        }
        // akciğer atardamarı (önde)
        tube(spline(0.5f, 0.45f, 0.54f, 0.37f, 0.58f, 0.31f), 0.045f).fill(blue).width(1.1f)
        tube(spline(0.58f, 0.31f, 0.75f, 0.285f, 0.96f, 0.3f), 0.03f).fill(blue).width(1f)
        circle(0.58f, 0.31f, 0.04f).fill(blue).noStroke()
        // kan akışı okları
        vec(0.3f, 0.5f, 0.3f, 0.62f, 0.03f, "#FFFFFF", 1.2f)
        vec(0.72f, 0.5f, 0.72f, 0.58f, 0.03f, "#FFFFFF", 1.2f)
        vec(0.38f, 0.85f, 0.46f, 0.76f, 0.03f, "#FFFFFF", 1.2f)
        vec(0.72f, 0.86f, 0.67f, 0.74f, 0.03f, "#FFFFFF", 1.2f)
    }

    private val lungs = sci(
        "sci.bio.lungs", "Akciğerler ve soluk borusu", 1.1f, 1f,
        tags = listOf("akciğer", "soluk borusu", "bronş", "bronşçuk", "solunum sistemi", "anatomi"),
    ) { _ ->
        val pink = "#F4A6B1"
        blob(0.43f, 0.1f, 0.33f, 0.17f, 0.22f, 0.36f, 0.15f, 0.6f, 0.12f, 0.85f, 0.2f, 0.93f, 0.34f, 0.88f, 0.47f, 0.9f, 0.49f, 0.62f, 0.49f, 0.36f, 0.47f, 0.2f)
            .fill(pink).width(1.4f)
        blob(0.67f, 0.1f, 0.77f, 0.17f, 0.88f, 0.36f, 0.95f, 0.6f, 0.98f, 0.85f, 0.9f, 0.93f, 0.77f, 0.9f, 0.68f, 0.84f, 0.68f, 0.72f, 0.62f, 0.62f, 0.61f, 0.36f, 0.63f, 0.2f)
            .fill(pink).width(1.4f)
        // lob yarıkları
        smooth(0.48f, 0.52f, 0.34f, 0.48f, 0.17f, 0.5f).width(0.8f)
        smooth(0.16f, 0.64f, 0.26f, 0.76f, 0.34f, 0.88f).width(0.8f)
        smooth(0.63f, 0.3f, 0.8f, 0.5f, 0.95f, 0.68f).width(0.8f)
        // bronş ağacı
        val airway = "#9C3D50"
        for (s in listOf(-1f, 1f)) {
            val bx = 0.55f + s * 0.15f; val by = 0.48f
            val base = if (s < 0) 180f else 0f
            for (d in listOf(-60f, -25f, 15f, 55f)) {
                val a = if (s < 0) base - d else base + d
                branch(bx, by, a, 0.1f, 1.3f, 2, 26f, airway)
            }
        }
        // bronşlar ve soluk borusu
        tube(spline(0.55f, 0.33f, 0.47f, 0.4f, 0.4f, 0.48f), 0.028f, 0.02f).fill("#F3D9DC").width(1.1f)
        tube(spline(0.55f, 0.33f, 0.63f, 0.4f, 0.7f, 0.48f), 0.028f, 0.02f).fill("#F3D9DC").width(1.1f)
        rect(0.51f, 0.02f, 0.08f, 0.33f).fill("#F3D9DC").width(1.1f)
        var y = 0.05f
        while (y < 0.33f) { arc(0.55f, y, 0.04f, 0.012f, 0f, 180f).width(0.8f); y += 0.035f }
    }

    private val eye = sci(
        "sci.bio.eye", "Göz (kesit)", 1.3f, 1f,
        tags = listOf("göz", "retina", "mercek", "kornea", "iris", "görme siniri", "duyu organı"),
    ) { _ ->
        val cx = 0.72f; val cy = 0.5f; val r = 0.36f
        // görme siniri
        tube(spline(0.98f, 0.56f, 1.14f, 0.6f, 1.28f, 0.62f), 0.05f).fill("#FFE082").width(1.1f)
        // göz küresi dış çizgisi: sklera (arka) + kornea (ön)
        val kc = 0.47f; val kr = 0.17f
        val ix = (r * r - kr * kr - cx * cx + kc * kc) / (2f * (kc - cx))
        val iy = kotlin.math.sqrt(kr * kr - (ix - kc) * (ix - kc))
        val a0 = abs(angleOf(kc, cy, ix, cy - iy))
        val sb = angleOf(cx, cy, ix, cy + iy)
        path(closed = true) {
            arcTo(cx, cy, r, r, -sb, 2f * sb)
            arcTo(kc, cy, kr, kr, a0, 360f - 2f * a0)
        }.fill("#FFFFFF").width(1.4f)
        fun band(r0: Float, r1: Float, color: String) {
            path(closed = true) {
                arcTo(cx, cy, r0, r0, -140f, 280f)
                arcTo(cx, cy, r1, r1, 140f, -280f)
            }.fill(color).width(0.6f)
        }
        band(0.345f, 0.325f, "#8D6E63")
        band(0.325f, 0.305f, "#EF9A9A")
        circle(cx, cy, 0.305f).fill("#F2F8FC").noStroke()
        // ön oda (aköz sıvı)
        path(closed = true) {
            arcTo(kc, cy, kr, kr, -a0, -(360f - 2f * a0))
            arcTo(cx, cy, r, r, sb, 2f * (180f - sb))
        }.fill("#E3F2FD").noStroke()
        arc(kc, cy, kr, kr, a0, 360f - 2f * a0).width(1.4f)
        // iris, siliyer cisim, mercek ve askı bağları
        for (s in listOf(-1f, 1f)) {
            val y0 = cy + s * 0.06f; val y1 = cy + s * 0.15f
            rect(0.405f, minOf(y0, y1), 0.022f, abs(y1 - y0)).fill("#6D8FB3").width(0.8f)
            poly(0.43f, cy + s * 0.15f, 0.47f, cy + s * 0.2f, 0.45f, cy + s * 0.145f, closed = true).fill("#6D4C41").width(0.7f)
            line(0.46f, cy + s * 0.165f, 0.475f, cy + s * 0.11f).width(0.6f)
        }
        ellipse(0.475f, cy, 0.045f, 0.12f).fill("#FFF3C4").width(1.1f)
        // sarı benek
        dot(cx + 0.3f, cy - 0.01f, 0.012f, "#C62828")
    }

    private val tooth = sci(
        "sci.bio.tooth", "Diş (kesit)", 0.75f, 1.1f,
        tags = listOf("diş", "mine", "dentin", "pulpa", "diş eti", "kök", "sindirim"),
    ) { _ ->
        // kemik ve diş eti
        rect(0f, 0.6f, 0.75f, 0.5f).fill("#EFE3CF").noStroke()
        val rng = Rng(21)
        repeat(26) {
            val x = 0.03f + rng.next() * 0.69f; val y = 0.66f + rng.next() * 0.4f
            if (x < 0.14f || x > 0.61f) circle(x, y, 0.012f + rng.next() * 0.01f).stroke("#BCA88A").width(0.6f)
        }
        path(closed = true) {
            moveTo(0f, 0.5f); quadTo(0.1f, 0.44f, 0.2f, 0.47f); lineTo(0.55f, 0.47f); quadTo(0.65f, 0.44f, 0.75f, 0.5f)
            lineTo(0.75f, 0.64f); lineTo(0f, 0.64f)
        }.fill("#F8BBD0").width(1f)
        // diş: dentin gövde
        val outline = floatArrayOf(
            0.17f, 0.46f, 0.1f, 0.3f, 0.12f, 0.15f, 0.21f, 0.07f, 0.3f, 0.12f, 0.375f, 0.14f, 0.45f, 0.12f, 0.54f, 0.07f,
            0.63f, 0.15f, 0.65f, 0.3f, 0.58f, 0.46f, 0.56f, 0.7f, 0.5f, 0.98f, 0.44f, 0.97f, 0.42f, 0.72f, 0.375f, 0.62f,
            0.33f, 0.72f, 0.31f, 0.97f, 0.25f, 0.98f, 0.19f, 0.7f,
        )
        blob(*outline).fill("#FFF1D0").width(1.3f)
        // mine (taç)
        blob(
            0.17f, 0.46f, 0.1f, 0.3f, 0.12f, 0.15f, 0.21f, 0.07f, 0.3f, 0.12f, 0.375f, 0.14f, 0.45f, 0.12f, 0.54f, 0.07f,
            0.63f, 0.15f, 0.65f, 0.3f, 0.58f, 0.46f, 0.555f, 0.44f, 0.575f, 0.24f, 0.52f, 0.15f, 0.375f, 0.2f, 0.23f, 0.15f,
            0.175f, 0.24f, 0.195f, 0.44f,
        ).fill("#FBFBFB").width(1.1f)
        // pulpa ve kök kanalları
        blob(
            0.27f, 0.3f, 0.32f, 0.36f, 0.375f, 0.34f, 0.43f, 0.36f, 0.48f, 0.3f, 0.5f, 0.42f, 0.485f, 0.6f, 0.475f, 0.8f,
            0.47f, 0.94f, 0.465f, 0.8f, 0.45f, 0.6f, 0.375f, 0.5f, 0.3f, 0.6f, 0.285f, 0.8f, 0.28f, 0.94f, 0.275f, 0.8f,
            0.265f, 0.6f, 0.25f, 0.42f,
        ).fill("#F06292").width(1f)
    }

    private val kidney = sci(
        "sci.bio.kidney", "Böbrek (kesit)", 0.9f, 1.1f,
        tags = listOf("böbrek", "boşaltım sistemi", "üreter", "nefron", "anatomi"),
    ) { _ ->
        val pelvis = "#F7DDB0"
        tube(spline(0.02f, 0.44f, 0.2f, 0.46f, 0.38f, 0.5f), 0.026f).fill("#E53935").width(1f)
        tube(spline(0.02f, 0.6f, 0.2f, 0.6f, 0.37f, 0.58f), 0.032f).fill("#5C8FD6").width(1f)
        val ureter = spline(0.4f, 0.56f, 0.32f, 0.7f, 0.27f, 0.9f, 0.26f, 1.08f)
        tube(ureter, 0.03f, 0.024f).fill(pelvis).width(1f)
        blob(0.5f, 0.06f, 0.7f, 0.1f, 0.83f, 0.28f, 0.86f, 0.55f, 0.8f, 0.8f, 0.64f, 0.97f, 0.45f, 0.98f, 0.32f, 0.88f, 0.28f, 0.72f, 0.36f, 0.62f, 0.37f, 0.48f, 0.28f, 0.4f, 0.27f, 0.24f, 0.36f, 0.1f)
            .fill("#E9A49B").width(1.5f)
        val pc = P(0.43f, 0.54f)
        for (a in listOf(-95f, -56f, -18f, 20f, 57f, 95f)) {
            val apex = onCircle(pc.x, pc.y, 0.13f, a)
            val pts = ArrayList<P>()
            pts += apex
            for (k in 0..6) pts += onCircle(pc.x, pc.y, 0.31f, a - 14f + 28f * k / 6)
            points(pts, closed = true).fill("#C96B61").width(0.9f)
            for (k in 1..3) {
                val s = onCircle(pc.x, pc.y, 0.16f, a - 6f + 4f * k)
                val e = onCircle(pc.x, pc.y, 0.29f, a - 10f + 5f * k)
                line(s.x, s.y, e.x, e.y).stroke("#A94E45").width(0.5f)
            }
        }
        for (a in listOf(-95f, -56f, -18f, 20f, 57f, 95f)) {
            val apex = onCircle(pc.x, pc.y, 0.13f, a)
            tube(listOf(pc, apex), 0.03f).fill(pelvis).noStroke()
            circle(apex.x, apex.y, 0.036f).fill(pelvis).noStroke()
        }
        circle(0.41f, 0.55f, 0.085f).fill(pelvis).noStroke()
        tube(ureter.filter { it.y < 0.8f }, 0.03f, 0.027f).fill(pelvis).noStroke()
    }

    private val brain = sci(
        "sci.bio.brain", "Beyin (loblar)", 1.2f, 1f,
        tags = listOf("beyin", "lob", "beyincik", "omurilik soğanı", "sinir sistemi", "anatomi"),
    ) { _ ->
        // beyin sapı ve beyincik (arkada)
        tube(spline(0.72f, 0.62f, 0.71f, 0.8f, 0.68f, 0.99f), 0.05f, 0.04f).fill("#BCAAA4").width(1.1f)
        blob(0.74f, 0.7f, 0.86f, 0.64f, 1.02f, 0.66f, 1.08f, 0.76f, 1.0f, 0.87f, 0.86f, 0.89f, 0.76f, 0.82f).fill("#CE93D8").width(1.2f)
        for (k in 0..3) arc(0.92f, 0.95f, 0.11f + k * 0.04f, 0.1f + k * 0.03f, 200f, 110f).width(0.6f)
        // büyük beyin: dış çizgi kontrol noktaları (0: yan yarık başı, 5: T1, 7: T2, 10: B2)
        val ctrl = listOf(
            P(0.3f, 0.6f), P(0.14f, 0.52f), P(0.1f, 0.38f), P(0.2f, 0.18f), P(0.42f, 0.08f), P(0.62f, 0.07f),
            P(0.82f, 0.12f), P(0.98f, 0.26f), P(1.06f, 0.4f), P(1.08f, 0.52f), P(1.0f, 0.64f), P(0.82f, 0.7f),
            P(0.6f, 0.73f), P(0.42f, 0.71f), P(0.32f, 0.66f),
        )
        val steps = 12
        val out = VectorBuilder.catmullRom(ctrl, closed = true, steps = steps)
        fun seg(i: Int, j: Int): List<P> { // i..j kontrol noktaları arası (dairesel)
            val n = out.size; val res = ArrayList<P>()
            var k = i * steps
            val end = (j * steps) % n
            while (true) { res += out[k % n]; if (k % n == end) break; k++ }
            return res
        }
        val s = P(0.5f, 0.47f); val e = P(0.8f, 0.46f); val jn = P(0.9f, 0.48f)
        val central = spline(0.62f, 0.07f, 0.6f, 0.2f, 0.54f, 0.34f, s.x, s.y, steps = 8)
        val fissure1 = spline(0.3f, 0.6f, 0.4f, 0.53f, s.x, s.y, steps = 8)
        val fissure2 = spline(s.x, s.y, 0.65f, 0.44f, e.x, e.y, steps = 8)
        val po = spline(0.98f, 0.26f, 0.94f, 0.38f, jn.x, jn.y, steps = 8)
        val jb = spline(jn.x, jn.y, 0.96f, 0.56f, 1.0f, 0.64f, steps = 8)
        val je = listOf(jn, e)
        val frontal = seg(0, 5) + central + fissure1.reversed()
        val parietal = seg(5, 7) + po + je + fissure2.reversed() + central.reversed()
        val occipital = seg(7, 10) + jb.reversed() + po.reversed()
        val temporal = seg(10, 15) + fissure1 + fissure2 + je.reversed() + jb
        points(frontal, closed = true).fill("#90CAF9").noStroke()
        points(parietal, closed = true).fill("#FFE082").noStroke()
        points(occipital, closed = true).fill("#EF9A9A").noStroke()
        points(temporal, closed = true).fill("#A5D6A7").noStroke()
        // kıvrımlar
        for (g in listOf(
            floatArrayOf(0.2f, 0.3f, 0.3f, 0.26f, 0.36f, 0.34f, 0.46f, 0.3f),
            floatArrayOf(0.18f, 0.45f, 0.28f, 0.42f, 0.34f, 0.48f),
            floatArrayOf(0.3f, 0.15f, 0.38f, 0.2f, 0.48f, 0.16f),
            floatArrayOf(0.7f, 0.16f, 0.74f, 0.26f, 0.82f, 0.24f, 0.88f, 0.32f),
            floatArrayOf(0.66f, 0.34f, 0.74f, 0.38f, 0.82f, 0.36f),
            floatArrayOf(0.96f, 0.46f, 1.02f, 0.52f),
            floatArrayOf(0.45f, 0.6f, 0.56f, 0.58f, 0.66f, 0.62f, 0.78f, 0.58f),
        )) smooth(*g).width(0.6f)
        points(central).width(1.1f)
        points(fissure1 + fissure2).width(1.1f)
        points(po).width(0.9f).dashed()
        points(jb).width(0.9f).dashed()
        points(je).width(0.9f).dashed()
        points(out, closed = true).width(1.4f)
    }

    private val digestive = sci(
        "sci.bio.digestive", "Sindirim sistemi", 0.85f, 1.1f,
        tags = listOf("mide", "bağırsak", "karaciğer", "yemek borusu", "pankreas", "sindirim"),
    ) { _ ->
        tube(spline(0.4f, 0.0f, 0.42f, 0.15f, 0.5f, 0.3f), 0.022f).fill("#F6C1CC").width(1f)
        tube(spline(0.44f, 0.565f, 0.56f, 0.55f, 0.67f, 0.51f), 0.024f, 0.012f).fill("#FFE082").width(0.9f)
        // ince bağırsak
        val si = ArrayList<P>()
        val tr = 0.0325f
        si += P(0.47f, 0.645f); si += P(0.54f, 0.68f); si += P(0.62f, 0.68f)
        for (row in 0..2) {
            val y = 0.68f + row * 2 * tr
            val right = row % 2 == 0
            val x = if (right) 0.62f else 0.34f
            for (k in 1..12) si += onCircle(x, y + tr, tr, -90f + (if (right) 180f else -180f) * k / 12)
            si += P(if (right) 0.34f else 0.62f, y + 2 * tr)
        }
        si += P(0.27f, 0.89f)
        tube(si, 0.022f).fill("#F7C6B8").width(0.9f)
        tube(spline(0.24f, 0.93f, 0.26f, 0.98f, 0.3f, 1.0f), 0.01f).fill("#E9B394").width(0.8f)
        // kalın bağırsak
        val colon = spline(
            0.24f, 0.92f, 0.2f, 0.75f, 0.2f, 0.62f, 0.25f, 0.575f, 0.45f, 0.6f, 0.66f, 0.575f, 0.74f, 0.61f,
            0.76f, 0.75f, 0.74f, 0.88f, 0.62f, 0.95f, 0.5f, 0.95f, 0.46f, 1.02f, 0.46f, 1.09f,
        )
        tube(colon, 0.042f, 0.02f).fill("#E9B394").width(1.1f)
        for (i in 6 until colon.size - 12 step 6) {
            val a = colon[i - 1]; val b = colon[i + 1]; val u = unit(b.x - a.x, b.y - a.y)
            val hw = 0.042f - 0.022f * i / colon.size
            line(colon[i].x - u.y * hw * 0.9f, colon[i].y + u.x * hw * 0.9f, colon[i].x + u.y * hw * 0.9f, colon[i].y - u.x * hw * 0.9f)
                .width(0.6f).stroke("#A0735A")
        }
        // on iki parmak bağırsağı ve mide
        tube(spline(0.42f, 0.49f, 0.37f, 0.54f, 0.39f, 0.61f, 0.47f, 0.63f), 0.022f).fill("#F7C6B8").width(0.9f)
        blob(0.5f, 0.28f, 0.6f, 0.24f, 0.7f, 0.28f, 0.74f, 0.38f, 0.7f, 0.48f, 0.6f, 0.53f, 0.48f, 0.54f, 0.4f, 0.5f, 0.42f, 0.45f, 0.5f, 0.46f, 0.58f, 0.42f, 0.58f, 0.35f, 0.52f, 0.33f)
            .fill("#F48FB1").width(1.2f)
        smooth(0.62f, 0.3f, 0.68f, 0.38f, 0.64f, 0.46f).width(0.5f).stroke("#C2185B")
        // karaciğer ve safra kesesi
        blob(0.08f, 0.3f, 0.2f, 0.22f, 0.42f, 0.21f, 0.57f, 0.25f, 0.52f, 0.32f, 0.4f, 0.39f, 0.24f, 0.45f, 0.12f, 0.43f)
            .fill("#B5654E").width(1.2f)
        ellipse(0.3f, 0.45f, 0.03f, 0.045f, rotate = -20f).fill("#8BC34A").width(0.9f)
    }

    /** Kemik: gövde + iki uçta boğumlar. */
    private fun VectorBuilder.bone(ax: Float, ay: Float, bx: Float, by: Float, hw: Float) {
        val u = unit(bx - ax, by - ay); val n = P(-u.y, u.x)
        for (p in listOf(P(ax, ay), P(bx, by))) {
            circle(p.x + n.x * hw * 0.75f, p.y + n.y * hw * 0.75f, hw * 1.05f).fill(BONE).width(0.8f)
            circle(p.x - n.x * hw * 0.75f, p.y - n.y * hw * 0.75f, hw * 1.05f).fill(BONE).width(0.8f)
        }
        bar(ax, ay, bx, by, hw).fill(BONE).noStroke()
        line(ax + n.x * hw, ay + n.y * hw, bx + n.x * hw, by + n.y * hw).width(0.8f)
        line(ax - n.x * hw, ay - n.y * hw, bx - n.x * hw, by - n.y * hw).width(0.8f)
    }

    private val skeleton = sci(
        "sci.bio.skeleton", "İnsan iskeleti", 0.5f, 1.2f,
        tags = listOf("iskelet", "kemik", "kafatası", "omurga", "kaburga", "destek ve hareket"),
    ) { _ ->
        val cx = 0.25f
        // kollar
        for (s in listOf(-1f, 1f)) {
            bone(cx + s * 0.12f, 0.245f, cx + s * 0.155f, 0.42f, 0.013f)
            bone(cx + s * 0.16f, 0.435f, cx + s * 0.185f, 0.58f, 0.008f)
            bone(cx + s * 0.145f, 0.44f, cx + s * 0.165f, 0.585f, 0.007f)
            for (k in -2..2) {
                val bx = cx + s * 0.178f + k * 0.008f
                line(bx, 0.6f, bx + s * 0.004f + k * 0.006f, 0.665f - abs(k) * 0.008f).width(1.3f)
            }
            ellipse(cx + s * 0.177f, 0.6f, 0.022f, 0.016f).fill(BONE).width(0.8f)
        }
        // bacaklar
        for (s in listOf(-1f, 1f)) {
            bone(cx + s * 0.06f, 0.63f, cx + s * 0.065f, 0.86f, 0.016f)
            circle(cx + s * 0.066f, 0.872f, 0.014f).fill(BONE).width(0.8f)
            bone(cx + s * 0.062f, 0.885f, cx + s * 0.06f, 1.09f, 0.012f)
            bone(cx + s * 0.09f, 0.89f, cx + s * 0.088f, 1.08f, 0.006f)
            path(closed = true) {
                moveTo(cx + s * 0.045f, 1.1f); lineTo(cx + s * 0.1f, 1.1f); quadTo(cx + s * 0.13f, 1.13f, cx + s * 0.12f, 1.16f)
                lineTo(cx + s * 0.045f, 1.16f)
            }.fill(BONE).width(0.8f)
        }
        // leğen kemiği
        for (s in listOf(-1f, 1f)) {
            blob(cx + s * 0.015f, 0.55f, cx + s * 0.07f, 0.52f, cx + s * 0.12f, 0.54f, cx + s * 0.11f, 0.6f, cx + s * 0.075f, 0.645f, cx + s * 0.03f, 0.65f)
                .fill(BONE).width(0.9f)
            circle(cx + s * 0.06f, 0.625f, 0.015f).fill("#E8DCC0").width(0.6f)
        }
        poly(cx - 0.03f, 0.54f, cx + 0.03f, 0.54f, cx, 0.62f, closed = true).fill(BONE).width(0.8f)
        // omurga
        var y = 0.175f
        while (y < 0.55f) { roundRect(cx - 0.016f, y, 0.032f, 0.016f, 0.005f).fill(BONE).width(0.6f); y += 0.021f }
        // kaburgalar ve göğüs kemiği
        for (i in 0..6) {
            val yy = 0.27f + i * 0.028f
            val rx = 0.065f + minOf(i, 4) * 0.01f
            arc(cx, yy + 0.05f, rx, 0.055f, 195f, 150f).width(1.2f)
        }
        roundRect(cx - 0.012f, 0.255f, 0.024f, 0.14f, 0.008f).fill(BONE).width(0.8f)
        // köprücük kemikleri
        for (s in listOf(-1f, 1f)) bone(cx + s * 0.02f, 0.248f, cx + s * 0.11f, 0.232f, 0.006f)
        // kafatası
        path(closed = true) {
            moveTo(cx - 0.055f, 0.13f)
            cubicTo(cx - 0.085f, 0.1f, cx - 0.08f, 0.02f, cx, 0.02f)
            cubicTo(cx + 0.08f, 0.02f, cx + 0.085f, 0.1f, cx + 0.055f, 0.13f)
            quadTo(cx + 0.045f, 0.17f, cx, 0.172f)
            quadTo(cx - 0.045f, 0.17f, cx - 0.055f, 0.13f)
        }.fill(BONE).width(1.1f)
        ellipse(cx - 0.027f, 0.09f, 0.018f, 0.016f).fill("#5D4037").noStroke()
        ellipse(cx + 0.027f, 0.09f, 0.018f, 0.016f).fill("#5D4037").noStroke()
        poly(cx, 0.105f, cx - 0.008f, 0.125f, cx + 0.008f, 0.125f, closed = true).fill("#5D4037").noStroke()
        line(cx - 0.03f, 0.145f, cx + 0.03f, 0.145f).width(0.6f)
        for (k in -3..3) line(cx + k * 0.009f, 0.138f, cx + k * 0.009f, 0.152f).width(0.5f)
    }

    // ------------------------------------------------------------------ Araç ve bitki

    private val microscope = sci(
        "sci.bio.microscope", "Işık mikroskobu", 0.8f, 1f,
        tags = listOf("mikroskop", "oküler", "objektif", "lam", "büyütme", "laboratuvar"),
    ) { _ ->
        val armC = "#78909C"
        roundRect(0.1f, 0.9f, 0.6f, 0.07f, 0.02f).fill("#607D8B").width(1.2f)
        // ışık kaynağı
        rect(0.23f, 0.8f, 0.12f, 0.1f).fill("#90A4AE").width(1f)
        ellipse(0.29f, 0.8f, 0.05f, 0.014f).fill("#FFF176").width(0.8f)
        // kol
        tube(spline(0.58f, 0.9f, 0.62f, 0.68f, 0.6f, 0.46f, 0.5f, 0.27f), 0.045f).fill(armC).width(1.2f)
        bar(0.5f, 0.26f, 0.32f, 0.25f, 0.035f).fill(armC).width(1f)
        // tüp, oküler, döner başlık, objektifler
        rect(0.24f, 0.14f, 0.1f, 0.24f).fill("#ECEFF1").width(1.2f)
        rect(0.255f, 0.045f, 0.07f, 0.095f).fill("#CFD8DC").width(1.1f)
        rect(0.25f, 0.03f, 0.08f, 0.02f).fill("#455A64").width(0.9f)
        poly(0.22f, 0.38f, 0.36f, 0.38f, 0.34f, 0.43f, 0.24f, 0.43f, closed = true).fill("#90A4AE").width(1f)
        bar(0.255f, 0.43f, 0.23f, 0.51f, 0.016f).fill("#CFD8DC").width(0.9f)
        bar(0.29f, 0.43f, 0.29f, 0.53f, 0.019f).fill("#CFD8DC").width(0.9f)
        bar(0.325f, 0.43f, 0.35f, 0.5f, 0.015f).fill("#CFD8DC").width(0.9f)
        // tabla, lam, kondansör
        rect(0.12f, 0.56f, 0.47f, 0.035f).fill("#455A64").width(1f)
        rect(0.19f, 0.548f, 0.2f, 0.012f).fill("#E3F2FD").width(0.7f)
        line(0.15f, 0.55f, 0.23f, 0.543f).width(1.2f)
        rect(0.25f, 0.595f, 0.08f, 0.045f).fill("#90A4AE").width(0.9f)
        // ayar vidaları
        circle(0.61f, 0.5f, 0.052f).fill("#546E7A").width(1f)
        circle(0.61f, 0.5f, 0.028f).fill("#90A4AE").width(0.7f)
        circle(0.615f, 0.64f, 0.032f).fill("#546E7A").width(1f)
    }

    private val flower = sci(
        "sci.bio.flower", "Çiçeğin yapısı", 1f, 1.1f,
        tags = listOf("çiçek", "taç yaprak", "çanak yaprak", "başçık", "dişicik", "yumurtalık", "tozlaşma", "bitki"),
    ) { _ ->
        tube(spline(0.5f, 1.09f, 0.5f, 0.72f), 0.022f).fill("#7CB342").width(1f)
        blob(0.52f, 0.95f, 0.62f, 0.87f, 0.78f, 0.84f, 0.68f, 0.94f, 0.54f, 0.975f).fill("#9CCC65").width(1f)
        line(0.53f, 0.95f, 0.72f, 0.875f).width(0.6f)
        // taç yapraklar
        blob(0.44f, 0.66f, 0.38f, 0.45f, 0.4f, 0.22f, 0.5f, 0.12f, 0.6f, 0.22f, 0.62f, 0.45f, 0.56f, 0.66f).fill("#F8BBD0").width(1f)
        for (s in listOf(-1f, 1f)) {
            fun x(v: Float) = 0.5f + s * (v - 0.5f)
            blob(x(0.46f), 0.68f, x(0.34f), 0.62f, x(0.18f), 0.48f, x(0.08f), 0.3f, x(0.12f), 0.19f, x(0.22f), 0.25f, x(0.34f), 0.4f, x(0.44f), 0.58f)
                .fill("#F48FB1").width(1.2f)
            smooth(x(0.44f), 0.64f, x(0.3f), 0.5f, x(0.16f), 0.28f).stroke("#D81B60").width(0.5f)
            // çanak yaprak
            blob(x(0.47f), 0.73f, x(0.36f), 0.73f, x(0.22f), 0.65f, x(0.34f), 0.665f, x(0.46f), 0.675f).fill("#8BC34A").width(1f)
        }
        ellipse(0.5f, 0.71f, 0.08f, 0.035f).fill("#9CCC65").width(1f)
        // erkek organlar: sapçık + başçık
        for ((fx, tx, ty) in listOf(Triple(0.45f, 0.33f, 0.36f), Triple(0.47f, 0.41f, 0.3f), Triple(0.53f, 0.59f, 0.3f), Triple(0.55f, 0.67f, 0.36f))) {
            smooth(fx, 0.66f, (fx + tx) / 2f + (tx - 0.5f) * 0.1f, 0.5f, tx, ty + 0.03f).width(1.1f)
            ellipse(tx, ty, 0.02f, 0.04f, rotate = angleOf(fx, 0.66f, tx, ty) - 90f).fill("#FFB300").width(0.9f)
            dot(tx + (tx - 0.5f) * 0.25f, ty - 0.05f, 0.006f, "#F9A825")
        }
        // dişi organ: yumurtalık, dişicik borusu, dişicik tepesi
        tube(spline(0.5f, 0.54f, 0.5f, 0.3f), 0.012f).fill("#C5E1A5").width(0.9f)
        ellipse(0.5f, 0.285f, 0.045f, 0.022f).fill("#AED581").width(1f)
        blob(0.5f, 0.52f, 0.56f, 0.575f, 0.575f, 0.64f, 0.54f, 0.69f, 0.46f, 0.69f, 0.425f, 0.64f, 0.44f, 0.575f).fill("#C5E1A5").width(1.1f)
        for ((ox, oy) in listOf(0.48f to 0.6f, 0.52f to 0.62f, 0.49f to 0.655f, 0.525f to 0.66f)) circle(ox, oy, 0.013f).fill("#FFF9C4").width(0.6f)
    }
}
