package com.karapp.drawguidance.content.library.general

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * v42 resim tuvallerinin (gelişmiş ve basit resim) genel şekilleri: çizgiler ve oklar, blok oklar, yıldızlar ve
 * patlamalar, afişler ve kurdeleler, akış şeması sembolleri, konuşma ve açıklama kutuları, semboller ve çerçeveler.
 * Hepsi renksizdir (kalem rengi çizgi; "İçi dolu" açıksa seçili dolgu). Nesne çizimleri (hayvan, taşıt, eşya...)
 * resim tuvallerinin şekil seçicisinde yoktur.
 *
 * Kimlikler "gen.<ad>" (kayıtta "lib.gen.<ad>"): değiştirilmemeli. Kutuya serbestçe yerleşir ([Fit.BOX]; oran
 * sürüklemeyle değişir), daire/yıldız gibileri kare ([Fit.SQUARE]), çizgiler iki nokta arasında ([Fit.LINE]).
 */
object GeneralShapes {
    const val ID_PREFIX = "gen."
    const val SECTION = "Temel"
    const val FLOWCHART = "gen_flowchart"

    val categories: List<LibCategory> by lazy {
        listOf(
            LibCategory("gen_lines", "Çizgiler ve oklar", Audience.ART, lines(), SECTION, listed = false),
            LibCategory("gen_block_arrows", "Blok oklar", Audience.ART, blockArrows(), SECTION, listed = false),
            LibCategory("gen_stars", "Yıldızlar ve patlamalar", Audience.ART, stars(), SECTION, listed = false),
            LibCategory("gen_banners", "Afişler ve kurdeleler", Audience.ART, banners(), SECTION, listed = false),
            LibCategory(FLOWCHART, "Akış şeması", Audience.ART, flowchart(), SECTION, listed = false),
            LibCategory("gen_callouts", "Konuşma ve açıklama kutuları", Audience.ART, callouts(), SECTION, listed = false),
            LibCategory("gen_symbols", "Semboller ve çerçeveler", Audience.ART, symbols(), SECTION, listed = false),
        )
    }

    val shapes: List<LibShape> get() = categories.flatMap { it.shapes }

    fun isGeneral(id: String): Boolean = id.removePrefix("lib.").startsWith(ID_PREFIX)

    // ---------------------------------------------------------------- yardımcılar

    private fun g(id: String, title: String, fit: Fit = Fit.BOX, aspect: Float = 1f, tags: List<String> = emptyList(), body: VectorBuilder.() -> Unit) =
        LibShape(ID_PREFIX + id, title, fit, aspect, tags) { style(fill = null, stroke = null, width = 1f) { body() } }

    private fun rad(deg: Float) = Math.toRadians(deg.toDouble())

    /**
     * Çizgi yerleşiminde ([Fit.LINE]) ok ucu: birim kutunun y ekseni [a] kat sıkışık olduğundan ucun açısı gerçek
     * oranlarla hesaplanır. [dx],[dy] ucun gerçek yönü; [size] uzunluğa oranla uç boyu.
     */
    private fun VectorBuilder.lineHead(tx: Float, ty: Float, dx: Float, dy: Float, a: Float, size: Float = 0.07f, closed: Boolean = false): VectorBuilder.Part {
        val len = hypot(dx, dy).coerceAtLeast(1e-6f)
        val ux = dx / len; val uy = dy / len
        val nx = -uy; val ny = ux
        val bx = -ux * size; val by = -uy * size
        val w = size * 0.55f
        return poly(tx + bx + nx * w, ty + (by + ny * w) * a, tx, ty, tx + bx - nx * w, ty + (by - ny * w) * a, closed = closed)
    }

    /** Kare kutuda ok ucu ([dx],[dy] yönüne bakan, açık). */
    private fun VectorBuilder.head(x: Float, y: Float, dx: Float, dy: Float, size: Float, closed: Boolean = false): VectorBuilder.Part {
        val len = hypot(dx, dy).coerceAtLeast(1e-6f)
        val ux = dx / len; val uy = dy / len
        val nx = -uy; val ny = ux
        val bx = x - ux * size; val by = y - uy * size
        return poly(bx + nx * size * 0.55f, by + ny * size * 0.55f, x, y, bx - nx * size * 0.55f, by - ny * size * 0.55f, closed = closed)
    }

    /** Elips üzerinde [n] tümsekli kapalı şekil (bulut, mühür). */
    private fun VectorBuilder.scallop(cx: Float, cy: Float, rx: Float, ry: Float, n: Int, depth: Float, phase: Float = -90f): VectorBuilder.Part =
        path(closed = true) {
            fun at(deg: Float, k: Float) = P(cx + rx * k * cos(rad(deg)).toFloat(), cy + ry * k * sin(rad(deg)).toFloat())
            val first = at(phase, 1f)
            moveTo(first.x, first.y)
            for (i in 0 until n) {
                val a0 = phase + 360f * i / n
                val a1 = phase + 360f * (i + 1) / n
                val c = at((a0 + a1) / 2f, 1f + depth)
                val e = at(a1, 1f)
                quadTo(c.x, c.y, e.x, e.y)
            }
        }

    /** Çokgen noktaları (x, y çiftleri) → kapalı parça. */
    private fun VectorBuilder.shape(vararg xy: Float) = poly(*xy, closed = true)

    /** x eksenine göre ayna ([xy] çiftleri). */
    private fun mirrorX(xy: FloatArray) = FloatArray(xy.size) { if (it % 2 == 0) 1f - xy[it] else xy[it] }

    /** y eksenine göre ayna. */
    private fun mirrorY(xy: FloatArray) = FloatArray(xy.size) { if (it % 2 == 1) 1f - xy[it] else xy[it] }

    /** Kutuyu çeyrek tur döndürür (x ↔ y). */
    private fun swapXY(xy: FloatArray) = FloatArray(xy.size) { if (it % 2 == 0) xy[it + 1] else xy[it - 1] }

    // ---------------------------------------------------------------- çizgiler ve oklar

    private fun lines(): List<LibShape> = listOf(
        g("line_dashed", "Kesik çizgi", Fit.LINE, 12f, listOf("kesik", "çizgi", "dashed")) { line(0f, 0.5f, 1f, 0.5f).dashed() },
        g("line_dotted", "Noktalı çizgi", Fit.LINE, 12f, listOf("noktalı", "çizgi", "dotted")) { line(0f, 0.5f, 1f, 0.5f).dotted() },
        g("line_double", "Çift çizgi", Fit.LINE, 12f, listOf("çift", "paralel", "çizgi")) {
            line(0f, 0.3f, 1f, 0.3f); line(0f, 0.7f, 1f, 0.7f)
        },
        g("line_wavy", "Dalgalı çizgi", Fit.LINE, 8f, listOf("dalga", "çizgi", "wavy")) {
            curve(160) { t -> P(t, 0.5f + 0.35f * sin(t * 2 * PI * 4).toFloat()) }
        },
        g("line_zigzag", "Zikzak çizgi", Fit.LINE, 8f, listOf("zikzak", "çizgi", "zigzag")) {
            points(List(13) { i -> P(i / 12f, if (i % 2 == 0) 0.5f else if (i % 4 == 1) 0.12f else 0.88f) })
        },
        g("line_end_dots", "Uçları noktalı çizgi", Fit.LINE, 10f, listOf("nokta", "uç", "çizgi")) {
            line(0.035f, 0.5f, 0.965f, 0.5f)
            ellipse(0.02f, 0.5f, 0.02f, 0.2f); ellipse(0.98f, 0.5f, 0.02f, 0.2f)
        },
        g("line_measure", "Ölçü çizgisi", Fit.LINE, 8f, listOf("ölçü", "boyut", "uzunluk")) {
            line(0f, 0.5f, 1f, 0.5f)
            line(0f, 0.1f, 0f, 0.9f); line(1f, 0.1f, 1f, 0.9f)
            lineHead(0f, 0.5f, -1f, 0f, 8f, 0.05f); lineHead(1f, 0.5f, 1f, 0f, 8f, 0.05f)
        },
        g("arrow_dashed", "Kesik ok", Fit.LINE, 8f, listOf("kesik", "ok")) {
            line(0f, 0.5f, 0.97f, 0.5f).dashed(); lineHead(1f, 0.5f, 1f, 0f, 8f)
        },
        g("arrow_dotted", "Noktalı ok", Fit.LINE, 8f, listOf("noktalı", "ok")) {
            line(0f, 0.5f, 0.97f, 0.5f).dotted(); lineHead(1f, 0.5f, 1f, 0f, 8f)
        },
        g("arrow_thick", "Kalın ok", Fit.LINE, 7f, listOf("kalın", "ok")) {
            line(0f, 0.5f, 0.94f, 0.5f).width(2.4f); lineHead(1f, 0.5f, 1f, 0f, 7f, 0.1f).width(2.4f)
        },
        g("arrow_triangle", "Üçgen uçlu ok", Fit.LINE, 8f, listOf("üçgen", "ok", "uç")) {
            line(0f, 0.5f, 0.92f, 0.5f); lineHead(1f, 0.5f, 1f, 0f, 8f, 0.08f, closed = true)
        },
        g("arrow_double_triangle", "Çift üçgen uçlu ok", Fit.LINE, 8f, listOf("çift", "üçgen", "ok")) {
            line(0.08f, 0.5f, 0.92f, 0.5f)
            lineHead(1f, 0.5f, 1f, 0f, 8f, 0.08f, closed = true); lineHead(0f, 0.5f, -1f, 0f, 8f, 0.08f, closed = true)
        },
        g("arrow_dot_tail", "Yuvarlak kuyruklu ok", Fit.LINE, 8f, listOf("yuvarlak", "nokta", "ok")) {
            ellipse(0.03f, 0.5f, 0.03f, 0.24f); line(0.06f, 0.5f, 0.97f, 0.5f); lineHead(1f, 0.5f, 1f, 0f, 8f)
        },
        g("arrow_curved", "Kavisli ok", Fit.LINE, 3f, listOf("kavis", "eğri", "ok")) {
            curve(64) { t -> P(t, 0.82f - 0.64f * sin(PI * t).toFloat()) }
            // t = 1'deki teğet: (1, 0.64·π) birim → gerçek (1, 0.64·π / 3)
            lineHead(1f, 0.82f, 1f, (0.64 * PI / 3).toFloat(), 3f, 0.12f)
        },
        g("arrow_wavy", "Dalgalı ok", Fit.LINE, 6f, listOf("dalga", "ok")) {
            curve(120) { t -> P(t * 0.94f, 0.5f + 0.3f * sin(t * 2 * PI * 3).toFloat()) }
            line(0.94f, 0.5f, 0.98f, 0.5f); lineHead(1f, 0.5f, 1f, 0f, 6f, 0.07f)
        },
        g("arrow_elbow", "Dirsekli bağlayıcı ok", Fit.LINE, 3f, listOf("dirsek", "bağlayıcı", "ok", "connector")) {
            poly(0f, 0.85f, 0.5f, 0.85f, 0.5f, 0.15f, 0.97f, 0.15f); lineHead(1f, 0.15f, 1f, 0f, 3f, 0.08f)
        },
    )

    // ---------------------------------------------------------------- blok oklar

    private val RIGHT = floatArrayOf(0f, 0.3f, 0.6f, 0.3f, 0.6f, 0.05f, 1f, 0.5f, 0.6f, 0.95f, 0.6f, 0.7f, 0f, 0.7f)
    private val UP = floatArrayOf(0.3f, 1f, 0.3f, 0.4f, 0.05f, 0.4f, 0.5f, 0f, 0.95f, 0.4f, 0.7f, 0.4f, 0.7f, 1f)

    private fun blockArrows(): List<LibShape> = listOf(
        g("block_right", "Sağ blok ok", aspect = 1.8f, tags = listOf("blok", "ok", "sağ")) { shape(*RIGHT) },
        g("block_left", "Sol blok ok", aspect = 1.8f, tags = listOf("blok", "ok", "sol")) { shape(*mirrorX(RIGHT)) },
        g("block_up", "Yukarı blok ok", aspect = 0.56f, tags = listOf("blok", "ok", "yukarı")) { shape(*UP) },
        g("block_down", "Aşağı blok ok", aspect = 0.56f, tags = listOf("blok", "ok", "aşağı")) { shape(*mirrorY(UP)) },
        g("block_left_right", "Sağ-sol blok ok", aspect = 2.4f, tags = listOf("blok", "ok", "iki yönlü")) {
            shape(0f, 0.5f, 0.25f, 0.05f, 0.25f, 0.3f, 0.75f, 0.3f, 0.75f, 0.05f, 1f, 0.5f, 0.75f, 0.95f, 0.75f, 0.7f, 0.25f, 0.7f, 0.25f, 0.95f)
        },
        g("block_up_down", "Yukarı-aşağı blok ok", aspect = 0.42f, tags = listOf("blok", "ok", "iki yönlü")) {
            shape(*swapXY(floatArrayOf(0f, 0.5f, 0.25f, 0.05f, 0.25f, 0.3f, 0.75f, 0.3f, 0.75f, 0.05f, 1f, 0.5f, 0.75f, 0.95f, 0.75f, 0.7f, 0.25f, 0.7f, 0.25f, 0.95f)))
        },
        g("block_quad", "Dört yönlü ok", Fit.SQUARE, tags = listOf("blok", "ok", "dört yön")) {
            shape(
                0.5f, 0f, 0.68f, 0.18f, 0.58f, 0.18f, 0.58f, 0.42f, 0.82f, 0.42f, 0.82f, 0.32f, 1f, 0.5f, 0.82f, 0.68f, 0.82f, 0.58f,
                0.58f, 0.58f, 0.58f, 0.82f, 0.68f, 0.82f, 0.5f, 1f, 0.32f, 0.82f, 0.42f, 0.82f, 0.42f, 0.58f, 0.18f, 0.58f,
                0.18f, 0.68f, 0f, 0.5f, 0.18f, 0.32f, 0.18f, 0.42f, 0.42f, 0.42f, 0.42f, 0.18f, 0.32f, 0.18f,
            )
        },
        g("block_notched", "Çentikli ok", aspect = 1.8f, tags = listOf("çentik", "blok", "ok")) {
            shape(0f, 0.3f, 0.6f, 0.3f, 0.6f, 0.05f, 1f, 0.5f, 0.6f, 0.95f, 0.6f, 0.7f, 0f, 0.7f, 0.15f, 0.5f)
        },
        g("block_striped", "Çizgili ok", aspect = 1.8f, tags = listOf("çizgili", "blok", "ok")) {
            rect(0f, 0.3f, 0.05f, 0.4f); rect(0.09f, 0.3f, 0.05f, 0.4f)
            shape(0.18f, 0.3f, 0.6f, 0.3f, 0.6f, 0.05f, 1f, 0.5f, 0.6f, 0.95f, 0.6f, 0.7f, 0.18f, 0.7f)
        },
        g("block_chevron", "Köşeli şerit ok", aspect = 1.6f, tags = listOf("köşeli", "şerit", "chevron")) {
            shape(0f, 0f, 0.7f, 0f, 1f, 0.5f, 0.7f, 1f, 0f, 1f, 0.3f, 0.5f)
        },
        g("block_pentagon", "Beşgen ok", aspect = 1.8f, tags = listOf("beşgen", "ok", "adım")) {
            shape(0f, 0f, 0.72f, 0f, 1f, 0.5f, 0.72f, 1f, 0f, 1f)
        },
        g("block_bent", "Bükülmüş ok", aspect = 1.1f, tags = listOf("bükülmüş", "köşe", "ok")) {
            shape(0.05f, 1f, 0.05f, 0.35f, 0.65f, 0.35f, 0.65f, 0.1f, 1f, 0.45f, 0.65f, 0.8f, 0.65f, 0.55f, 0.27f, 0.55f, 0.27f, 1f)
        },
        g("block_uturn", "U dönüşlü ok", tags = listOf("dönüş", "u", "ok")) {
            path(closed = true) {
                moveTo(0.06f, 1f); lineTo(0.06f, 0.45f)
                arcTo(0.44f, 0.45f, 0.38f, 0.38f, 180f, 180f)
                lineTo(0.82f, 0.6f); lineTo(0.98f, 0.6f); lineTo(0.72f, 0.95f); lineTo(0.46f, 0.6f); lineTo(0.62f, 0.6f); lineTo(0.62f, 0.45f)
                arcTo(0.44f, 0.45f, 0.18f, 0.18f, 0f, -180f)
                lineTo(0.26f, 1f)
            }
        },
        g("block_curved", "Kavisli blok ok", aspect = 1.5f, tags = listOf("kavis", "blok", "ok")) {
            path(closed = true) {
                moveTo(0f, 1f); quadTo(0.1f, 0.25f, 0.68f, 0.24f); lineTo(0.66f, 0.04f); lineTo(1f, 0.4f); lineTo(0.68f, 0.76f)
                lineTo(0.68f, 0.56f); quadTo(0.36f, 0.56f, 0.26f, 1f)
            }
        },
        g("arrow_circular", "Dairesel ok", Fit.SQUARE, tags = listOf("daire", "döngü", "yenile", "ok")) {
            arc(0.5f, 0.5f, 0.44f, 0.44f, 200f, 290f)
            val a = rad(130f)
            head(0.5f + 0.44f * cos(a).toFloat(), 0.5f + 0.44f * sin(a).toFloat(), -sin(a).toFloat(), cos(a).toFloat(), 0.16f, closed = true)
        },
        g("arrow_circular_two", "Çift dairesel ok", Fit.SQUARE, tags = listOf("daire", "döngü", "iki", "ok")) {
            for (start in listOf(-70f, 110f)) {
                arc(0.5f, 0.5f, 0.44f, 0.44f, start, 140f)
                val a = rad(start + 140f)
                head(0.5f + 0.44f * cos(a).toFloat(), 0.5f + 0.44f * sin(a).toFloat(), -sin(a).toFloat(), cos(a).toFloat(), 0.15f, closed = true)
            }
        },
    )

    // ---------------------------------------------------------------- yıldızlar ve patlamalar

    private fun starShape(n: Int, inner: Float, title: String) =
        g("star$n", title, Fit.SQUARE, tags = listOf("yıldız", "$n köşe", "star")) { star(0.5f, 0.5f, 0.5f, inner, n) }

    /** Düzensiz patlama: dış ve iç yarıçaplar sırayla (birim daire oranı). */
    private fun VectorBuilder.burst(outer: FloatArray, inner: FloatArray, rotate: Float) = points(
        List(outer.size * 2) { i ->
            val a = rad(-90f + rotate + 180f * i / outer.size)
            val r = if (i % 2 == 0) outer[i / 2] else inner[i / 2]
            P(0.5f + 0.5f * r * cos(a).toFloat(), 0.5f + 0.5f * r * sin(a).toFloat())
        },
        closed = true,
    )

    private fun stars(): List<LibShape> = listOf(
        starShape(4, 0.2f, "4 köşeli yıldız"),
        starShape(5, 0.2f, "5 köşeli yıldız"),
        starShape(6, 0.29f, "6 köşeli yıldız"),
        starShape(7, 0.3f, "7 köşeli yıldız"),
        starShape(8, 0.33f, "8 köşeli yıldız"),
        starShape(10, 0.36f, "10 köşeli yıldız"),
        starShape(12, 0.38f, "12 köşeli yıldız"),
        starShape(16, 0.4f, "16 köşeli yıldız"),
        starShape(24, 0.42f, "24 köşeli yıldız"),
        starShape(32, 0.44f, "32 köşeli yıldız"),
        g("explosion1", "Patlama 1", Fit.SQUARE, tags = listOf("patlama", "çarpma", "explosion")) {
            burst(floatArrayOf(1f, 0.82f, 0.95f, 0.78f, 1f, 0.86f, 0.92f, 0.8f, 0.98f, 0.84f, 0.9f, 0.88f), floatArrayOf(0.52f, 0.48f, 0.56f, 0.5f, 0.46f, 0.54f, 0.5f, 0.47f, 0.55f, 0.5f, 0.48f, 0.53f), 8f)
        },
        g("explosion2", "Patlama 2", Fit.SQUARE, tags = listOf("patlama", "bum", "explosion")) {
            burst(floatArrayOf(0.9f, 1f, 0.7f, 0.95f, 0.8f, 1f, 0.75f, 0.92f, 0.85f, 1f, 0.72f, 0.96f, 0.82f, 0.88f), floatArrayOf(0.4f, 0.55f, 0.42f, 0.5f, 0.38f, 0.52f, 0.45f, 0.4f, 0.55f, 0.43f, 0.5f, 0.38f, 0.48f, 0.44f), -4f)
        },
        g("seal", "Mühür", Fit.SQUARE, tags = listOf("mühür", "damga", "rozet")) { scallop(0.5f, 0.5f, 0.45f, 0.45f, 20, 0.1f) },
        g("rosette", "Rozet", Fit.SQUARE, tags = listOf("rozet", "madalya", "ödül")) {
            scallop(0.5f, 0.5f, 0.44f, 0.44f, 16, 0.12f); circle(0.5f, 0.5f, 0.31f)
        },
        g("sparkle", "Parıltı", Fit.SQUARE, tags = listOf("parıltı", "ışıltı", "sparkle")) {
            path(closed = true) {
                moveTo(0.5f, 0f); quadTo(0.56f, 0.44f, 1f, 0.5f); quadTo(0.56f, 0.56f, 0.5f, 1f); quadTo(0.44f, 0.56f, 0f, 0.5f); quadTo(0.44f, 0.44f, 0.5f, 0f)
            }
        },
        g("star_badge", "Yıldızlı rozet", Fit.SQUARE, tags = listOf("yıldız", "rozet", "daire")) {
            circle(0.5f, 0.5f, 0.5f); star(0.5f, 0.53f, 0.36f, 0.15f, 5)
        },
        g("sunburst", "Işın patlaması", Fit.SQUARE, tags = listOf("ışın", "patlama", "ışık")) {
            for (i in 0 until 12) {
                val a = rad(-90f + 30f * i)
                val b0 = rad(-90f + 30f * i - 6f); val b1 = rad(-90f + 30f * i + 6f)
                shape(
                    0.5f + 0.18f * cos(b0).toFloat(), 0.5f + 0.18f * sin(b0).toFloat(),
                    0.5f + 0.5f * cos(a).toFloat(), 0.5f + 0.5f * sin(a).toFloat(),
                    0.5f + 0.18f * cos(b1).toFloat(), 0.5f + 0.18f * sin(b1).toFloat(),
                )
            }
        },
    )

    // ---------------------------------------------------------------- afişler ve kurdeleler

    /** Kurdele: önde şerit, iki yanda arkaya katlanan uçlar ([bend] > 0: ortası aşağı, < 0: yukarı kavisli). */
    private fun VectorBuilder.ribbon(bend: Float) {
        val top = 0.12f; val h = 0.5f
        fun y(t: Float) = top + bend * sin(PI * t).toFloat()
        val ly = y(0.08f); val ry = y(0.92f)
        // Uçlar (arkada): şeridin ucundan biraz aşağıda
        shape(0.17f, ly + 0.2f, 0f, ly + 0.2f, 0.08f, ly + 0.45f, 0f, ly + 0.7f, 0.25f, ly + 0.7f, 0.25f, ly + h)
        shape(0.83f, ry + 0.2f, 1f, ry + 0.2f, 0.92f, ry + 0.45f, 1f, ry + 0.7f, 0.75f, ry + 0.7f, 0.75f, ry + h)
        line(0.17f, ly + h, 0.25f, ly + 0.7f); line(0.83f, ry + h, 0.75f, ry + 0.7f)
        // Ön şerit
        val n = 24
        val upper = List(n + 1) { i -> val t = i / n.toFloat(); P(0.17f + 0.66f * t, y(0.08f + 0.84f * t)) }
        points(upper + upper.reversed().map { P(it.x, it.y + h) }, closed = true)
    }

    private fun banners(): List<LibShape> = listOf(
        g("ribbon", "Kurdele afiş", aspect = 3.2f, tags = listOf("kurdele", "afiş", "başlık")) { ribbon(0f) },
        g("ribbon_curved_down", "Aşağı kavisli kurdele", aspect = 3.2f, tags = listOf("kurdele", "kavis", "afiş")) { ribbon(0.12f) },
        g("ribbon_curved_up", "Yukarı kavisli kurdele", aspect = 3.2f, tags = listOf("kurdele", "kavis", "afiş")) {
            group(dy = 0.12f) { ribbon(-0.12f) }
        },
        g("ribbon_flat", "Düz şerit", aspect = 4f, tags = listOf("şerit", "kurdele", "etiket")) {
            shape(0f, 0f, 1f, 0f, 0.92f, 0.5f, 1f, 1f, 0f, 1f, 0.08f, 0.5f)
        },
        g("wave", "Dalgalı afiş", aspect = 2f, tags = listOf("dalga", "afiş", "bayrak")) {
            path(closed = true) { moveTo(0f, 0.2f); cubicTo(0.33f, -0.05f, 0.66f, 0.45f, 1f, 0.2f); lineTo(1f, 0.8f); cubicTo(0.66f, 1.05f, 0.33f, 0.55f, 0f, 0.8f) }
        },
        g("wave_double", "Çift dalgalı afiş", aspect = 2.4f, tags = listOf("dalga", "afiş", "çift")) {
            path(closed = true) {
                moveTo(0f, 0.2f); cubicTo(0.17f, 0f, 0.33f, 0.4f, 0.5f, 0.2f); cubicTo(0.67f, 0f, 0.83f, 0.4f, 1f, 0.2f)
                lineTo(1f, 0.8f); cubicTo(0.83f, 1f, 0.67f, 0.6f, 0.5f, 0.8f); cubicTo(0.33f, 1f, 0.17f, 0.6f, 0f, 0.8f)
            }
        },
        g("banner_arc", "Kemerli afiş", aspect = 2.2f, tags = listOf("kemer", "afiş", "yay")) {
            val n = 32
            val outer = List(n + 1) { i -> val a = rad(205f + 130f * i / n); P(0.5f + 0.5f * cos(a).toFloat(), 1.05f + 0.95f * sin(a).toFloat()) }
            val inner = List(n + 1) { i -> val a = rad(335f - 130f * i / n); P(0.5f + 0.36f * cos(a).toFloat(), 1.05f + 0.6f * sin(a).toFloat()) }
            points(outer + inner, closed = true)
        },
        g("scroll_h", "Yatay parşömen", aspect = 2.4f, tags = listOf("parşömen", "tomar", "yazı")) {
            rect(0.08f, 0.12f, 0.84f, 0.76f)
            roundRect(0f, 0.04f, 0.1f, 0.92f, 0.05f); roundRect(0.9f, 0.04f, 0.1f, 0.92f, 0.05f)
            ellipse(0.05f, 0.5f, 0.02f, 0.06f); ellipse(0.95f, 0.5f, 0.02f, 0.06f)
        },
        g("scroll_v", "Dikey parşömen", aspect = 0.5f, tags = listOf("parşömen", "tomar", "dikey")) {
            rect(0.12f, 0.06f, 0.76f, 0.88f)
            roundRect(0.04f, 0f, 0.92f, 0.08f, 0.04f); roundRect(0.04f, 0.92f, 0.92f, 0.08f, 0.04f)
        },
        g("pennant", "Flama", aspect = 1.3f, tags = listOf("flama", "bayrak", "üçgen")) {
            line(0.05f, 0f, 0.05f, 1f).width(1.4f); shape(0.05f, 0.05f, 1f, 0.3f, 0.05f, 0.55f)
        },
        g("flag", "Dalgalanan bayrak", aspect = 1.2f, tags = listOf("bayrak", "dalga", "direk")) {
            line(0.05f, 0f, 0.05f, 1f).width(1.4f)
            path(closed = true) { moveTo(0.05f, 0.05f); cubicTo(0.35f, -0.05f, 0.65f, 0.15f, 0.95f, 0.05f); lineTo(0.95f, 0.55f); cubicTo(0.65f, 0.65f, 0.35f, 0.45f, 0.05f, 0.55f) }
        },
        g("award_ribbon", "Ödül kurdelesi", aspect = 0.7f, tags = listOf("ödül", "madalya", "kurdele")) {
            shape(0.32f, 0.55f, 0.16f, 1f, 0.3f, 0.93f, 0.42f, 1f, 0.5f, 0.64f)
            shape(0.68f, 0.55f, 0.84f, 1f, 0.7f, 0.93f, 0.58f, 1f, 0.5f, 0.64f)
            scallop(0.5f, 0.35f, 0.42f, 0.3f, 18, 0.1f); ellipse(0.5f, 0.35f, 0.3f, 0.21f)
        },
        g("bookmark", "Yer imi", aspect = 0.5f, tags = listOf("yer imi", "işaret", "kurdele")) { shape(0f, 0f, 1f, 0f, 1f, 1f, 0.5f, 0.76f, 0f, 1f) },
        g("tag", "Etiket", aspect = 2f, tags = listOf("etiket", "fiyat", "tag")) {
            shape(0f, 0.5f, 0.22f, 0.05f, 1f, 0.05f, 1f, 0.95f, 0.22f, 0.95f); ellipse(0.19f, 0.5f, 0.04f, 0.08f)
        },
        g("ticket", "Bilet", aspect = 2f, tags = listOf("bilet", "kupon", "ticket")) {
            path(closed = true) {
                moveTo(0f, 0f); lineTo(1f, 0f); lineTo(1f, 0.38f); arcTo(1f, 0.5f, 0.06f, 0.12f, 270f, -180f)
                lineTo(1f, 1f); lineTo(0f, 1f); lineTo(0f, 0.62f); arcTo(0f, 0.5f, 0.06f, 0.12f, 90f, -180f)
            }
            line(0.75f, 0.08f, 0.75f, 0.92f).dashed()
        },
        g("plaque", "Levha", aspect = 1.8f, tags = listOf("levha", "tabela", "çerçeve")) {
            path(closed = true) {
                moveTo(0.1f, 0f); lineTo(0.9f, 0f); arcTo(1f, 0f, 0.1f, 0.18f, 180f, -90f)
                lineTo(1f, 0.82f); arcTo(1f, 1f, 0.1f, 0.18f, 270f, -90f)
                lineTo(0.1f, 1f); arcTo(0f, 1f, 0.1f, 0.18f, 0f, -90f)
                lineTo(0f, 0.18f); arcTo(0f, 0f, 0.1f, 0.18f, 90f, -90f)
            }
        },
    )

    // ---------------------------------------------------------------- akış şeması

    private fun fc(id: String, title: String, aspect: Float = 1.6f, fit: Fit = Fit.BOX, tags: List<String> = emptyList(), body: VectorBuilder.() -> Unit) =
        g("fc_$id", title, fit, aspect, tags + listOf("akış", "şema", "flowchart"), body)

    private fun VectorBuilder.documentShape(x: Float, y: Float, w: Float, h: Float) = path(closed = true) {
        moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h * 0.82f)
        cubicTo(x + w * 0.7f, y + h * 0.66f, x + w * 0.35f, y + h * 1.06f, x, y + h * 0.86f)
    }

    private fun flowchart(): List<LibShape> = listOf(
        fc("process", "İşlem", tags = listOf("işlem", "adım")) { rect(0f, 0f, 1f, 1f) },
        fc("alt_process", "Alternatif işlem", tags = listOf("alternatif", "işlem")) { roundRect(0f, 0f, 1f, 1f, 0.15f) },
        fc("decision", "Karar", tags = listOf("karar", "koşul", "eşkenar dörtgen")) { shape(0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f) },
        fc("data", "Veri", tags = listOf("veri", "giriş", "çıkış")) { shape(0.2f, 0f, 1f, 0f, 0.8f, 1f, 0f, 1f) },
        fc("predefined", "Önceden tanımlı işlem", tags = listOf("alt program", "işlem")) {
            rect(0f, 0f, 1f, 1f); line(0.12f, 0f, 0.12f, 1f); line(0.88f, 0f, 0.88f, 1f)
        },
        fc("internal_storage", "İç depolama", tags = listOf("bellek", "depolama")) {
            rect(0f, 0f, 1f, 1f); line(0.15f, 0f, 0.15f, 1f); line(0f, 0.2f, 1f, 0.2f)
        },
        fc("document", "Belge", tags = listOf("belge", "rapor")) { documentShape(0f, 0f, 1f, 1f) },
        fc("multidocument", "Çoklu belge", tags = listOf("belgeler", "rapor")) {
            documentShape(0.12f, 0f, 0.88f, 0.78f); documentShape(0.06f, 0.09f, 0.88f, 0.78f); documentShape(0f, 0.18f, 0.88f, 0.78f)
        },
        fc("terminator", "Sonlandırıcı", aspect = 2.4f, tags = listOf("başla", "bitir", "uç")) { roundRect(0f, 0f, 1f, 1f, 0.5f) },
        fc("preparation", "Hazırlık", tags = listOf("hazırlık", "altıgen")) { shape(0.18f, 0f, 0.82f, 0f, 1f, 0.5f, 0.82f, 1f, 0.18f, 1f, 0f, 0.5f) },
        fc("manual_input", "Elle giriş", tags = listOf("elle", "giriş", "klavye")) { shape(0f, 0.3f, 1f, 0f, 1f, 1f, 0f, 1f) },
        fc("manual_operation", "Elle işlem", tags = listOf("elle", "işlem")) { shape(0f, 0f, 1f, 0f, 0.82f, 1f, 0.18f, 1f) },
        fc("connector", "Bağlayıcı", 1f, Fit.SQUARE, listOf("bağlayıcı", "daire")) { circle(0.5f, 0.5f, 0.5f) },
        fc("offpage", "Sayfa dışı bağlayıcı", 1f, tags = listOf("sayfa dışı", "bağlayıcı")) { shape(0f, 0f, 1f, 0f, 1f, 0.65f, 0.5f, 1f, 0f, 0.65f) },
        fc("card", "Kart", tags = listOf("kart", "delikli kart")) { shape(0.2f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 0f, 0.25f) },
        fc("punched_tape", "Delikli şerit", tags = listOf("şerit", "bant")) {
            path(closed = true) { moveTo(0f, 0.1f); cubicTo(0.3f, 0.35f, 0.7f, -0.15f, 1f, 0.1f); lineTo(1f, 0.9f); cubicTo(0.7f, 0.65f, 0.3f, 1.15f, 0f, 0.9f) }
        },
        fc("summing", "Toplama birleşimi", 1f, Fit.SQUARE, listOf("toplama", "birleşim")) {
            circle(0.5f, 0.5f, 0.5f); line(0.146f, 0.146f, 0.854f, 0.854f); line(0.854f, 0.146f, 0.146f, 0.854f)
        },
        fc("or", "Veya", 1f, Fit.SQUARE, listOf("veya", "or")) { circle(0.5f, 0.5f, 0.5f); line(0.5f, 0f, 0.5f, 1f); line(0f, 0.5f, 1f, 0.5f) },
        fc("collate", "Harmanla", 0.9f, tags = listOf("harmanla", "kum saati")) { shape(0f, 0f, 1f, 0f, 0.5f, 0.5f); shape(0.5f, 0.5f, 1f, 1f, 0f, 1f) },
        fc("sort", "Sırala", 1f, tags = listOf("sırala", "sort")) { shape(0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f); line(0f, 0.5f, 1f, 0.5f) },
        fc("extract", "Ayıkla", 1.15f, tags = listOf("ayıkla", "üçgen")) { shape(0.5f, 0f, 1f, 1f, 0f, 1f) },
        fc("merge", "Birleştir", 1.15f, tags = listOf("birleştir", "üçgen")) { shape(0f, 0f, 1f, 0f, 0.5f, 1f) },
        fc("stored_data", "Depolanmış veri", tags = listOf("depolanmış", "veri")) {
            path(closed = true) { moveTo(0.15f, 0f); lineTo(1f, 0f); quadTo(0.78f, 0.5f, 1f, 1f); lineTo(0.15f, 1f); quadTo(-0.07f, 0.5f, 0.15f, 0f) }
        },
        fc("delay", "Gecikme", tags = listOf("gecikme", "bekleme")) {
            path(closed = true) { moveTo(0f, 0f); lineTo(0.6f, 0f); arcTo(0.6f, 0.5f, 0.4f, 0.5f, 270f, 180f); lineTo(0f, 1f) }
        },
        fc("sequential_storage", "Sıralı erişimli depolama", 1f, Fit.SQUARE, listOf("sıralı", "bant", "depolama")) {
            circle(0.5f, 0.5f, 0.5f); line(0.5f, 1f, 1f, 1f)
        },
        fc("magnetic_disk", "Manyetik disk", 0.75f, tags = listOf("disk", "veritabanı", "silindir")) {
            path(closed = true) {
                moveTo(0f, 0.12f); lineTo(0f, 0.88f); arcTo(0.5f, 0.88f, 0.5f, 0.12f, 180f, -180f); lineTo(1f, 0.12f)
                arcTo(0.5f, 0.12f, 0.5f, 0.12f, 0f, -180f)
            }
            ellipse(0.5f, 0.12f, 0.5f, 0.12f)
        },
        fc("direct_storage", "Doğrudan erişimli depolama", 1.8f, tags = listOf("doğrudan", "depolama", "silindir")) {
            path(closed = true) {
                moveTo(0.12f, 0f); lineTo(0.88f, 0f); arcTo(0.88f, 0.5f, 0.12f, 0.5f, 270f, 180f); lineTo(0.12f, 1f)
                arcTo(0.12f, 0.5f, 0.12f, 0.5f, 90f, 180f)
            }
            ellipse(0.88f, 0.5f, 0.12f, 0.5f)
        },
        fc("display", "Ekran", tags = listOf("ekran", "görüntü")) {
            path(closed = true) { moveTo(0f, 0.5f); lineTo(0.18f, 0f); lineTo(0.82f, 0f); arcTo(0.82f, 0.5f, 0.18f, 0.5f, 270f, 180f); lineTo(0.18f, 1f) }
        },
    )

    // ---------------------------------------------------------------- konuşma ve açıklama kutuları

    private val ARROW_BOX = floatArrayOf(0f, 0f, 0.6f, 0f, 0.6f, 0.35f, 0.8f, 0.35f, 0.8f, 0.15f, 1f, 0.5f, 0.8f, 0.85f, 0.8f, 0.65f, 0.6f, 0.65f, 0.6f, 1f, 0f, 1f)

    /** Oval balon: elips gövde ve sol alta uzanan kuyruk tek çizgi. */
    private fun VectorBuilder.ovalCallout(): VectorBuilder.Part = path(closed = true) {
        val a0 = 115f
        moveTo(0.5f + 0.5f * cos(rad(a0)).toFloat(), 0.4f + 0.4f * sin(rad(a0)).toFloat())
        arcTo(0.5f, 0.4f, 0.5f, 0.4f, a0, 310f)
        lineTo(0.18f, 1f)
    }

    private fun callouts(): List<LibShape> = listOf(
        g("callout_rect", "Dikdörtgen konuşma kutusu", aspect = 1.4f, tags = listOf("konuşma", "kutu", "balon")) {
            shape(0f, 0f, 1f, 0f, 1f, 0.72f, 0.42f, 0.72f, 0.2f, 1f, 0.26f, 0.72f, 0f, 0.72f)
        },
        g("callout_round", "Yuvarlak köşeli konuşma kutusu", aspect = 1.4f, tags = listOf("konuşma", "kutu", "yuvarlak")) {
            path(closed = true) {
                moveTo(0.12f, 0f); lineTo(0.88f, 0f); quadTo(1f, 0f, 1f, 0.12f); lineTo(1f, 0.6f); quadTo(1f, 0.72f, 0.88f, 0.72f)
                lineTo(0.42f, 0.72f); lineTo(0.2f, 1f); lineTo(0.26f, 0.72f); lineTo(0.12f, 0.72f); quadTo(0f, 0.72f, 0f, 0.6f)
                lineTo(0f, 0.12f); quadTo(0f, 0f, 0.12f, 0f)
            }
        },
        g("callout_oval", "Oval konuşma balonu", aspect = 1.4f, tags = listOf("konuşma", "balon", "oval")) { ovalCallout() },
        g("callout_whisper", "Fısıltı balonu", aspect = 1.4f, tags = listOf("fısıltı", "balon", "kesik")) { ovalCallout().dashed() },
        g("callout_cloud", "Düşünce balonu", aspect = 1.4f, tags = listOf("düşünce", "bulut", "balon")) {
            scallop(0.52f, 0.4f, 0.44f, 0.34f, 10, 0.16f)
            ellipse(0.22f, 0.86f, 0.07f, 0.06f); ellipse(0.1f, 0.96f, 0.04f, 0.035f)
        },
        g("callout_shout", "Bağırma balonu", aspect = 1.3f, tags = listOf("bağırma", "patlama", "balon")) {
            val n = 26
            val outer = floatArrayOf(1f, 0.9f, 1f, 0.94f, 0.88f, 1f, 0.92f, 1f, 0.9f, 0.96f, 1f, 0.9f, 0.95f)
            points(
                List(n) { i ->
                    val a = rad(-90f + 360f * i / n)
                    if (i == 16) P(0.06f, 1f)
                    else {
                        val r = if (i % 2 == 0) outer[(i / 2) % outer.size] else 0.74f
                        P(0.5f + 0.5f * r * cos(a).toFloat(), 0.44f + 0.44f * r * sin(a).toFloat())
                    }
                },
                closed = true,
            )
        },
        g("callout_double_tail", "İki kuyruklu kutu", aspect = 1.5f, tags = listOf("konuşma", "iki", "kuyruk")) {
            path(closed = true) {
                moveTo(0.12f, 0f); lineTo(0.88f, 0f); quadTo(1f, 0f, 1f, 0.12f); lineTo(1f, 0.6f); quadTo(1f, 0.72f, 0.88f, 0.72f)
                lineTo(0.78f, 0.72f); lineTo(0.86f, 1f); lineTo(0.62f, 0.72f); lineTo(0.38f, 0.72f); lineTo(0.14f, 1f); lineTo(0.22f, 0.72f)
                lineTo(0.12f, 0.72f); quadTo(0f, 0.72f, 0f, 0.6f); lineTo(0f, 0.12f); quadTo(0f, 0f, 0.12f, 0f)
            }
        },
        g("callout_side", "Yandan kuyruklu kutu", aspect = 1.5f, tags = listOf("konuşma", "yan", "kuyruk")) {
            shape(0f, 0f, 0.8f, 0f, 0.8f, 0.3f, 1f, 0.62f, 0.8f, 0.5f, 0.8f, 1f, 0f, 1f)
        },
        g("callout_line", "Çizgili açıklama", aspect = 1.6f, tags = listOf("açıklama", "çizgi", "not")) {
            rect(0.25f, 0f, 0.75f, 0.6f); line(0.35f, 0.6f, 0f, 1f)
        },
        g("callout_line_bent", "Kırık çizgili açıklama", aspect = 1.6f, tags = listOf("açıklama", "kırık", "çizgi")) {
            rect(0.3f, 0f, 0.7f, 0.6f); poly(0.3f, 0.4f, 0.14f, 0.4f, 0f, 1f)
        },
        g("callout_accent", "Vurgu çizgili açıklama", aspect = 1.6f, tags = listOf("açıklama", "vurgu", "çizgi")) {
            rect(0.32f, 0f, 0.68f, 0.6f); line(0.27f, 0f, 0.27f, 0.6f).width(1.6f); poly(0.27f, 0.3f, 0.12f, 0.3f, 0f, 1f)
        },
        g("callout_arrow_right", "Sağ oklu açıklama", aspect = 1.8f, tags = listOf("açıklama", "ok", "sağ")) { shape(*ARROW_BOX) },
        g("callout_arrow_left", "Sol oklu açıklama", aspect = 1.8f, tags = listOf("açıklama", "ok", "sol")) { shape(*mirrorX(ARROW_BOX)) },
        g("callout_arrow_down", "Aşağı oklu açıklama", aspect = 0.6f, tags = listOf("açıklama", "ok", "aşağı")) { shape(*swapXY(ARROW_BOX)) },
        g("callout_arrow_up", "Yukarı oklu açıklama", aspect = 0.6f, tags = listOf("açıklama", "ok", "yukarı")) { shape(*mirrorY(swapXY(ARROW_BOX))) },
        g("callout_arrow_quad", "Dört oklu açıklama", Fit.SQUARE, tags = listOf("açıklama", "ok", "dört")) {
            shape(
                0.25f, 0.25f, 0.4f, 0.25f, 0.4f, 0.15f, 0.32f, 0.15f, 0.5f, 0f, 0.68f, 0.15f, 0.6f, 0.15f, 0.6f, 0.25f, 0.75f, 0.25f,
                0.75f, 0.4f, 0.85f, 0.4f, 0.85f, 0.32f, 1f, 0.5f, 0.85f, 0.68f, 0.85f, 0.6f, 0.75f, 0.6f, 0.75f, 0.75f, 0.6f, 0.75f,
                0.6f, 0.85f, 0.68f, 0.85f, 0.5f, 1f, 0.32f, 0.85f, 0.4f, 0.85f, 0.4f, 0.75f, 0.25f, 0.75f, 0.25f, 0.6f, 0.15f, 0.6f,
                0.15f, 0.68f, 0f, 0.5f, 0.15f, 0.32f, 0.15f, 0.4f, 0.25f, 0.4f,
            )
        },
        g("note", "Not kâğıdı", tags = listOf("not", "kâğıt", "katlanmış")) {
            shape(0f, 0f, 1f, 0f, 1f, 0.78f, 0.78f, 1f, 0f, 1f); shape(1f, 0.78f, 0.8f, 0.8f, 0.78f, 1f)
        },
    )

    // ---------------------------------------------------------------- semboller ve çerçeveler

    private val PLUS = floatArrayOf(0.35f, 0f, 0.65f, 0f, 0.65f, 0.35f, 1f, 0.35f, 1f, 0.65f, 0.65f, 0.65f, 0.65f, 1f, 0.35f, 1f, 0.35f, 0.65f, 0f, 0.65f, 0f, 0.35f, 0.35f, 0.35f)

    private fun symbols(): List<LibShape> = listOf(
        g("plus", "Artı", Fit.SQUARE, tags = listOf("artı", "toplama", "plus")) { shape(*PLUS) },
        g("minus", "Eksi", Fit.SQUARE, tags = listOf("eksi", "çıkarma", "minus")) { rect(0f, 0.35f, 1f, 0.3f) },
        g("multiply", "Çarpı", Fit.SQUARE, tags = listOf("çarpı", "çarpma", "x")) { group(rotate = 45f) { shape(*PLUS) } },
        g("divide", "Bölü", Fit.SQUARE, tags = listOf("bölü", "bölme")) { rect(0f, 0.42f, 1f, 0.16f); circle(0.5f, 0.18f, 0.11f); circle(0.5f, 0.82f, 0.11f) },
        g("equal", "Eşittir", Fit.SQUARE, tags = listOf("eşittir", "eşit")) { rect(0f, 0.2f, 1f, 0.2f); rect(0f, 0.6f, 1f, 0.2f) },
        g("not_equal", "Eşit değil", Fit.SQUARE, tags = listOf("eşit değil")) {
            rect(0f, 0.2f, 1f, 0.2f); rect(0f, 0.6f, 1f, 0.2f); shape(0.64f, 0f, 0.8f, 0f, 0.36f, 1f, 0.2f, 1f)
        },
        g("check", "Onay işareti", Fit.SQUARE, tags = listOf("onay", "tik", "doğru")) {
            shape(0f, 0.55f, 0.15f, 0.4f, 0.38f, 0.62f, 0.85f, 0.1f, 1f, 0.25f, 0.38f, 0.92f)
        },
        g("no_symbol", "Yasak işareti", Fit.SQUARE, tags = listOf("yasak", "dur", "hayır")) {
            circle(0.5f, 0.5f, 0.5f); circle(0.5f, 0.5f, 0.36f); group(rotate = 45f) { rect(0.14f, 0.43f, 0.72f, 0.14f) }
        },
        g("lightning", "Şimşek", aspect = 0.62f, tags = listOf("şimşek", "yıldırım", "elektrik")) {
            shape(0.38f, 0f, 0.82f, 0f, 0.58f, 0.38f, 0.9f, 0.38f, 0.22f, 1f, 0.42f, 0.55f, 0.12f, 0.55f)
        },
        g("crescent", "Hilal", Fit.SQUARE, tags = listOf("hilal", "ay")) {
            path(closed = true) {
                moveTo(0.75f, 0.067f); arcTo(0.5f, 0.5f, 0.5f, 0.5f, -60f, -240f); arcTo(0.95f, 0.5f, 0.477f, 0.477f, 114.8f, 130.4f)
            }
        },
        g("sun", "Güneş", Fit.SQUARE, tags = listOf("güneş", "ışık")) {
            circle(0.5f, 0.5f, 0.24f)
            for (i in 0 until 12) {
                val a = rad(-90f + 30f * i); val b0 = rad(-90f + 30f * i - 7f); val b1 = rad(-90f + 30f * i + 7f)
                shape(
                    0.5f + 0.31f * cos(b0).toFloat(), 0.5f + 0.31f * sin(b0).toFloat(), 0.5f + 0.5f * cos(a).toFloat(), 0.5f + 0.5f * sin(a).toFloat(),
                    0.5f + 0.31f * cos(b1).toFloat(), 0.5f + 0.31f * sin(b1).toFloat(),
                )
            }
        },
        g("smiley", "Gülen yüz", Fit.SQUARE, tags = listOf("gülen", "yüz", "ifade")) {
            circle(0.5f, 0.5f, 0.5f); ellipse(0.35f, 0.38f, 0.05f, 0.07f); ellipse(0.65f, 0.38f, 0.05f, 0.07f)
            arc(0.5f, 0.5f, 0.28f, 0.28f, 25f, 130f)
        },
        g("cloud", "Bulut", aspect = 1.5f, tags = listOf("bulut")) { scallop(0.5f, 0.52f, 0.42f, 0.34f, 9, 0.2f) },
        g("heart_outline", "Kalp çerçeve", Fit.SQUARE, tags = listOf("kalp", "sevgi")) {
            fun heart(k: Float) = path(closed = true) {
                val c = 0.5f; val s = k
                moveTo(c, c + (0.92f - c) * s)
                cubicTo(c + (0.1f - c) * s, c + (0.6f - c) * s, c + (0f - c) * s, c + (0.25f - c) * s, c + (0.25f - c) * s, c + (0.1f - c) * s)
                cubicTo(c + (0.4f - c) * s, c + (0f - c) * s, c, c + (0.15f - c) * s, c, c + (0.28f - c) * s)
                cubicTo(c, c + (0.15f - c) * s, c + (0.6f - c) * s, c + (0f - c) * s, c + (0.75f - c) * s, c + (0.1f - c) * s)
                cubicTo(c + (1f - c) * s, c + (0.25f - c) * s, c + (0.9f - c) * s, c + (0.6f - c) * s, c, c + (0.92f - c) * s)
            }
            heart(1f); heart(0.62f)
        },
        g("gear", "Dişli", Fit.SQUARE, tags = listOf("dişli", "ayar", "çark")) {
            val teeth = 10
            points(
                List(teeth * 4) { i ->
                    val k = i % 4
                    val a = rad(360f * (i / 4) / teeth + listOf(-9f, -5f, 5f, 9f)[k])
                    val r = if (k == 1 || k == 2) 0.5f else 0.38f
                    P(0.5f + r * cos(a).toFloat(), 0.5f + r * sin(a).toFloat())
                },
                closed = true,
            )
            circle(0.5f, 0.5f, 0.14f)
        },
        g("frame", "Çerçeve", tags = listOf("çerçeve", "kenarlık")) { rect(0f, 0f, 1f, 1f); rect(0.12f, 0.12f, 0.76f, 0.76f) },
        g("bevel", "Eğimli çerçeve", tags = listOf("eğim", "çerçeve", "düğme")) {
            rect(0f, 0f, 1f, 1f); rect(0.14f, 0.14f, 0.72f, 0.72f)
            line(0f, 0f, 0.14f, 0.14f); line(1f, 0f, 0.86f, 0.14f); line(1f, 1f, 0.86f, 0.86f); line(0f, 1f, 0.14f, 0.86f)
        },
        g("donut", "Halka", Fit.SQUARE, tags = listOf("halka", "simit", "daire")) { circle(0.5f, 0.5f, 0.5f); circle(0.5f, 0.5f, 0.27f) },
        g("block_arc", "Yay bloğu", aspect = 2f, tags = listOf("yay", "kemer", "yarım halka")) {
            path(closed = true) { moveTo(0f, 1f); arcTo(0.5f, 1f, 0.5f, 1f, 180f, 180f); lineTo(0.78f, 1f); arcTo(0.5f, 1f, 0.28f, 0.56f, 0f, -180f) }
        },
        g("teardrop", "Gözyaşı damlası", Fit.SQUARE, tags = listOf("damla", "gözyaşı")) {
            path(closed = true) { moveTo(1f, 0.5f); arcTo(0.5f, 0.5f, 0.5f, 0.5f, 0f, 270f); lineTo(1f, 0f) }
        },
        g("cube", "Küp", Fit.SQUARE, tags = listOf("küp", "kutu", "3b")) {
            rect(0f, 0.25f, 0.75f, 0.75f); shape(0f, 0.25f, 0.25f, 0f, 1f, 0f, 0.75f, 0.25f); shape(0.75f, 0.25f, 1f, 0f, 1f, 0.75f, 0.75f, 1f)
        },
        g("can", "Silindir", aspect = 0.7f, tags = listOf("silindir", "kutu")) {
            path(closed = true) {
                moveTo(0f, 0.12f); lineTo(0f, 0.88f); arcTo(0.5f, 0.88f, 0.5f, 0.12f, 180f, -180f); lineTo(1f, 0.12f)
                arcTo(0.5f, 0.12f, 0.5f, 0.12f, 0f, -180f)
            }
            ellipse(0.5f, 0.12f, 0.5f, 0.12f)
        },
    )
}
