package com.karapp.drawguidance.content.library.practice

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.Affine
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * v50 yetişkinler için çizim alıştırmaları (basit ve gelişmiş resim modları): üstünden geçerek, ezberden ya da simetriyle
 * tamamlanarak çizilen karmaşık şekiller. Çizim kurslarının temel alıştırmaları (düz ve ışınsal çizgiler, elips tüneli,
 * spiral, dalga, tarama ve ton skalası), desenler (mandala, petek, balık pulu, sekiz ve on iki kollu geometrik yıldız,
 * lale ve rumi motifi) ve konular (perspektif kutular, küre, silindir, göz, yüz oranları, yaprak, gül, kubbe silueti,
 * fincan). Hepsi uygulamaya özgü çizimlerdir (internetten resim, marka ya da tescilli desen adı yok).
 *
 * Renksiz çizgi çizimleri: kapalı parçalar dolgusuz, iç ayrıntılar ince. Görevler ("Alıştırmalar") ve şekil önerisi
 * (şablonlar, bkz. shapes.practiceTemplates) bu çizimlerin bütün çizgilerini kullanır.
 */

/** Kimlik ön eki: kütüphanede "practice.<ad>", kayıtta "lib.practice.<ad>". */
const val PRACTICE_ID = "practice."

/** Alıştırma çizimi: [w]×[h] tasarım biriminde çizilir, en/boy oranı korunarak kutuya eşlenir. */
internal fun practice(
    name: String,
    title: String,
    w: Float,
    h: Float,
    tags: List<String>,
    body: VectorBuilder.() -> Unit,
): LibShape = LibShape(PRACTICE_ID + name, title, Fit.BOX, aspect = w / h, tags = tags + listOf("alıştırma", "practice", "egzersiz")) {
    inBox(0f, 0f, 1f / w, 1f / h, 0f) { style(fill = null, stroke = null, width = 1f) { body() } }
}

/** İç ayrıntı çizgisi (ince). */
private fun VectorBuilder.Part.thin(): VectorBuilder.Part = width(0.6f)

/** [block] [deg] derece döndürülmüş olarak ([cx],[cy] çevresinde) çizilir. */
private fun VectorBuilder.rotated(deg: Float, cx: Float, cy: Float, block: VectorBuilder.() -> Unit) =
    withTransform(Affine.rotate(deg, cx, cy), block)

/** [block] çizilir, sonra [cx] dikey eksenine göre aynası çizilir. */
private fun VectorBuilder.mirror(cx: Float, block: VectorBuilder.() -> Unit) {
    block()
    withTransform(Affine.translate(cx, 0f).then(Affine.scale(-1f, 1f)).then(Affine.translate(-cx, 0f)), block)
}

/**
 * Dikdörtgenin içine 45°'lik paralel tarama çizgileri: [dir] 1 ise "\" yönünde, -1 ise "/" yönünde; [step] çizgiler
 * arası (yatay) uzaklık.
 */
private fun VectorBuilder.hatch(x: Float, y: Float, w: Float, h: Float, step: Float, dir: Int) {
    // "\" : y - y0 = (x - x0) ; "/" : y - y0 = -(x - x0). Çizgiler kutunun sol üst köşesinden başlayan c kaydırmasıyla.
    var c = -h + step / 2f
    while (c < w) {
        // Yerel koordinat: u ∈ [0,w], v ∈ [0,h]; "\" için v = u - c
        val u0 = maxOf(0f, c)
        val u1 = minOf(w, c + h)
        if (u1 - u0 > 0.5f) {
            val v0 = u0 - c
            val v1 = u1 - c
            if (dir > 0) line(x + u0, y + v0, x + u1, y + v1).thin()
            else line(x + w - u0, y + v0, x + w - u1, y + v1).thin()
        }
        c += step
    }
}

/** Sivri uçlu yaprak (taç yaprağı): [r0] yarıçapında tabandan [r1] yarıçapındaki uca, en geniş yeri [half]. Tepede (−y) çizilir. */
private fun VectorBuilder.petal(cx: Float, cy: Float, r0: Float, r1: Float, half: Float) =
    path(closed = true) {
        val mid = (r0 + r1) / 2f
        moveTo(cx, cy - r0)
        quadTo(cx - half * 2f, cy - mid, cx, cy - r1)
        quadTo(cx + half * 2f, cy - mid, cx, cy - r0)
    }

/** İki doğrunun kesişimi (a1→a2 ve b1→b2). */
private fun cross(a1: P, a2: P, b1: P, b2: P): P {
    val d = (a1.x - a2.x) * (b1.y - b2.y) - (a1.y - a2.y) * (b1.x - b2.x)
    if (kotlin.math.abs(d) < 1e-6f) return a2
    val t = ((a1.x - b1.x) * (b1.y - b2.y) - (a1.y - b1.y) * (b1.x - b2.x)) / d
    return P(a1.x + t * (a2.x - a1.x), a1.y + t * (a2.y - a1.y))
}

private fun lerp(a: P, b: P, t: Float) = P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun VectorBuilder.seg(a: P, b: P) = line(a.x, a.y, b.x, b.y)

/** Alıştırma şekilleri ve kütüphanedeki kategorileri. */
object PracticeShapes {

    const val SECTION = "Alıştırmalar"

    // --- Temel alıştırmalar ---

    private fun lines() = practice("lines", "Düz çizgiler", 120f, 100f, listOf("çizgi", "düz çizgi", "paralel", "line", "lines")) {
        for (i in 0 until 5) line(10f, 10f + i * 9f, 110f, 10f + i * 9f)
        for (i in 0 until 5) line(8f + i * 22f, 94f, 30f + i * 22f, 56f)
    }

    private fun radial() = practice("radial", "Işınsal çizgiler", 100f, 100f, listOf("ışın", "ışınsal", "merkez", "radial")) {
        circle(50f, 50f, 6f)
        for (i in 0 until 16) {
            val a = 2.0 * PI * i / 16
            val c = cos(a).toFloat(); val s = sin(a).toFloat()
            line(50f + c * 10f, 50f + s * 10f, 50f + c * 46f, 50f + s * 46f)
        }
    }

    private fun ellipses() = practice("ellipses", "Elips tüneli", 100f, 100f, listOf("elips", "daire", "tünel", "ellipse")) {
        for (i in 0 until 6) ellipse(50f, 12f + i * 15f, 38f, 3f + i * 2.4f)
        line(12f, 12f, 12f, 87f).thin()
        line(88f, 12f, 88f, 87f).thin()
    }

    private fun spiral() = practice("spiral", "Spiral", 100f, 100f, listOf("spiral", "sarmal", "helezon")) {
        curve(samples = 420) { t ->
            val a = t * 3.5f * 2f * PI.toFloat()
            val r = 2f + 42f * t
            P(50f + r * cos(a), 50f + r * sin(a))
        }
    }

    private fun waves() = practice("waves", "Dalga ritmi", 120f, 100f, listOf("dalga", "ritim", "sinüs", "wave")) {
        for (i in 0 until 4) {
            val y0 = 15f + i * 23f
            val cycles = 2 + i
            curve(samples = 160) { t -> P(8f + 104f * t, y0 + 7f * sin(t * cycles * 2f * PI.toFloat())) }
        }
    }

    private fun valueScale() = practice("value_scale", "Tarama ve ton skalası", 150f, 40f, listOf("tarama", "ton", "gölge", "çapraz tarama", "hatching", "value")) {
        val w = 26f; val h = 30f
        for (i in 0 until 5) {
            val x = 4f + i * 29f
            rect(x, 5f, w, h)
            when (i) {
                1 -> hatch(x, 5f, w, h, 7f, 1)
                2 -> hatch(x, 5f, w, h, 4f, 1)
                3 -> { hatch(x, 5f, w, h, 5f, 1); hatch(x, 5f, w, h, 5f, -1) }
                4 -> { hatch(x, 5f, w, h, 3f, 1); hatch(x, 5f, w, h, 3f, -1) }
            }
        }
    }

    // --- Desenler ---

    private fun mandala() = practice("mandala", "Mandala", 100f, 100f, listOf("mandala", "simetri", "desen", "çiçek")) {
        circle(50f, 50f, 6f)
        for (i in 0 until 8) rotated(i * 45f, 50f, 50f) { petal(50f, 50f, 8f, 26f, 5.5f) }
        circle(50f, 50f, 28f)
        for (i in 0 until 16) rotated(i * 22.5f + 11.25f, 50f, 50f) { petal(50f, 50f, 29f, 40f, 3.2f) }
        circle(50f, 50f, 44f)
        for (i in 0 until 16) rotated(i * 22.5f, 50f, 50f) { circle(50f, 50f - 42f, 1.2f).thin() }
    }

    private fun honeycomb() = practice("honeycomb", "Petek deseni", 124f, 96f, listOf("petek", "altıgen", "tekrar", "mozaik", "hexagon")) {
        val r = 12f
        val dx = sqrt(3f) * r
        for (row in 0 until 4) for (col in 0 until 5) {
            val cx = 14f + col * dx + if (row % 2 == 1) dx / 2f else 0f
            val cy = 14f + row * 1.5f * r
            polygon(cx, cy, r, 6)
        }
    }

    private fun scales() = practice("scales", "Balık pulu deseni", 120f, 76f, listOf("pul", "balık pulu", "desen", "doku", "scales")) {
        val r = 10f
        for (row in 0 until 5) for (col in 0 until 6) {
            val cx = 10f + col * 2f * r + if (row % 2 == 1) r else 0f
            if (cx + r > 120f) continue
            arc(cx, 10f + row * r * 1.2f, r, r, 0f, 180f)
        }
    }

    private fun star8() = practice("star8", "Sekiz kollu yıldız", 100f, 100f, listOf("yıldız", "geometrik", "sekiz köşeli", "selçuklu yıldızı", "geometric star")) {
        polygon(50f, 50f, 44f, 4, rotate = 0f)
        polygon(50f, 50f, 44f, 4, rotate = 45f)
        polygon(50f, 50f, 22f, 8, rotate = 22.5f)
        circle(50f, 50f, 9f)
        circle(50f, 50f, 48f).thin()
    }

    private fun star12() = practice("star12", "On iki kollu yıldız", 100f, 100f, listOf("yıldız", "geometrik", "on iki", "rozet", "geometric star")) {
        val pts = List(12) { i ->
            val a = Math.toRadians(-90.0 + 30.0 * ((i * 5) % 12))
            P(50f + 46f * cos(a).toFloat(), 50f + 46f * sin(a).toFloat())
        }
        points(pts, closed = true)
        polygon(50f, 50f, 20f, 12, rotate = 15f)
        polygon(50f, 50f, 10f, 6)
        circle(50f, 50f, 48f).thin()
    }

    private fun tulip() = practice("tulip", "Lale motifi", 80f, 120f, listOf("lale", "motif", "süsleme", "tezhip", "tulip")) {
        // Orta yaprak
        path(closed = true) {
            moveTo(40f, 62f)
            cubicTo(28f, 50f, 31f, 22f, 40f, 6f)
            cubicTo(49f, 22f, 52f, 50f, 40f, 62f)
        }
        mirror(40f) {
            // Yan yapraklar (dışa kıvrılan sivri uç)
            path(closed = true) {
                moveTo(39f, 64f)
                cubicTo(20f, 64f, 12f, 42f, 16f, 16f)
                cubicTo(22f, 32f, 28f, 42f, 36f, 48f)
                cubicTo(36f, 54f, 37f, 60f, 39f, 64f)
            }
            // Yaprak
            path(closed = true) {
                moveTo(40f, 112f)
                cubicTo(28f, 104f, 16f, 90f, 12f, 72f)
                cubicTo(24f, 82f, 34f, 94f, 40f, 104f)
            }
        }
        // Kadeh (alt kısım)
        path { moveTo(22f, 58f); quadTo(40f, 76f, 58f, 58f) }
        // Sap
        path { moveTo(40f, 68f); cubicTo(42f, 84f, 38f, 100f, 40f, 116f) }
    }

    private fun rumi() = practice("rumi", "Rumi motifi", 120f, 90f, listOf("rumi", "motif", "süsleme", "tezhip", "kıvrım")) {
        // Gövde: kıvrılarak sivrilen kanat
        path(closed = true) {
            moveTo(12f, 82f)
            cubicTo(18f, 46f, 52f, 18f, 92f, 18f)
            cubicTo(112f, 18f, 118f, 40f, 104f, 50f)
            cubicTo(96f, 56f, 84f, 50f, 88f, 41f)
            cubicTo(70f, 38f, 46f, 56f, 36f, 82f)
        }
        // Uçtaki sarmal
        curve(samples = 120) { t ->
            val a = -PI.toFloat() / 2f + t * 3f * PI.toFloat()
            val r = 9f * exp(-0.35f * t * 3f * PI.toFloat() / PI.toFloat())
            P(100f + r * cos(a), 34f + r * sin(a))
        }.thin()
        // Çatal dil (tabanda)
        path { moveTo(12f, 82f); cubicTo(4f, 70f, 4f, 58f, 12f, 50f) }
        path { moveTo(36f, 82f); cubicTo(42f, 72f, 50f, 66f, 60f, 64f) }
        // Orta damar
        smooth(22f, 74f, 40f, 50f, 64f, 34f, 88f, 28f).thin()
    }

    // --- Hacim ve perspektif ---

    private fun cube1p() = practice("cube_1p", "Tek kaçış noktalı küp", 120f, 90f, listOf("perspektif", "küp", "kaçış noktası", "kutu", "perspective")) {
        val vp = P(60f, 20f)
        line(0f, 20f, 120f, 20f).thin()
        circle(vp.x, vp.y, 1.6f)
        fun box(x: Float, y: Float, s: Float, depth: Float) {
            val f = listOf(P(x, y), P(x + s, y), P(x + s, y + s), P(x, y + s))
            points(f, closed = true)
            val b = f.map { lerp(it, vp, depth) }
            // Arka yüzün görünen kenarları ve derinlik kenarları
            for (i in 0 until 4) seg(f[i], b[i]).thin()
            seg(b[0], b[1]); seg(b[1], b[2]); seg(b[0], b[3])
        }
        box(14f, 48f, 30f, 0.32f)
        box(78f, 44f, 26f, 0.3f)
    }

    private fun box2p() = practice("box_2p", "İki kaçış noktalı kutu", 120f, 90f, listOf("perspektif", "kutu", "iki nokta", "kaçış noktası", "perspective")) {
        val vl = P(4f, 22f); val vr = P(116f, 22f)
        line(0f, 22f, 120f, 22f).thin()
        circle(vl.x, vl.y, 1.6f); circle(vr.x, vr.y, 1.6f)
        val a = P(60f, 40f); val b = P(60f, 82f)
        val al = lerp(a, vl, 0.36f); val bl = lerp(b, vl, 0.36f)
        val ar = lerp(a, vr, 0.3f); val br = lerp(b, vr, 0.3f)
        val back = cross(al, vr, ar, vl)
        seg(a, b)
        seg(a, al); seg(b, bl); seg(al, bl)
        seg(a, ar); seg(b, br); seg(ar, br)
        seg(al, back); seg(ar, back)
        // Kaçış noktalarına giden yardımcı çizgiler
        seg(bl, vl).thin(); seg(br, vr).thin()
    }

    private fun sphere() = practice("sphere", "Küre ve gölgesi", 100f, 90f, listOf("küre", "gölge", "ışık", "hacim", "sphere")) {
        circle(45f, 40f, 30f)
        ellipse(56f, 76f, 30f, 7f)
        arc(46f, 40f, 20f, 30f, -90f, 180f).thin()
        ellipse(34f, 28f, 6f, 3.6f, rotate = -35f).thin()
    }

    private fun cylinder() = practice("cylinder", "Silindir", 80f, 110f, listOf("silindir", "hacim", "elips", "cylinder")) {
        ellipse(40f, 18f, 28f, 8f)
        line(12f, 18f, 12f, 90f)
        line(68f, 18f, 68f, 90f)
        arc(40f, 90f, 28f, 8f, 0f, 180f)
        for (i in 0 until 4) {
            val x = 54f + i * 3.5f
            val dx = (x - 40f) / 28f
            val bottom = 90f + 8f * sqrt((1f - dx * dx).coerceAtLeast(0f))
            line(x, 28f, x, bottom - 3f).thin()
        }
        ellipse(46f, 100f, 32f, 6f).thin()
    }

    // --- Konular ---

    private fun eye() = practice("eye", "Göz", 120f, 70f, listOf("göz", "yüz", "portre", "anatomi", "eye")) {
        withTransform(Affine.translate(0f, 12f)) {
            path { moveTo(8f, 32f); cubicTo(30f, 6f, 84f, 4f, 112f, 30f) }
            path { moveTo(8f, 32f); cubicTo(34f, 50f, 82f, 52f, 112f, 30f) }
            circle(58f, 28f, 13f)
            circle(58f, 28f, 5f)
            circle(62f, 24f, 2f).thin()
            path { moveTo(16f, 22f); cubicTo(38f, 0f, 82f, -2f, 104f, 18f) }.thin()
            path { moveTo(10f, 33f); quadTo(14f, 36f, 18f, 34f) }.thin()
            for (i in 0 until 5) {
                val x = 40f + i * 10f
                line(x, 10f + kotlin.math.abs(i - 2) * 1.5f, x + 3f, 3f + kotlin.math.abs(i - 2) * 1.5f).thin()
            }
            path { moveTo(6f, -2f); cubicTo(30f, -14f, 80f, -16f, 116f, 0f) }
        }
    }

    private fun face() = practice("face", "Yüz oranları", 80f, 110f, listOf("yüz", "oran", "portre", "kafa", "face", "proportion")) {
        ellipse(40f, 52f, 30f, 42f)
        line(40f, 8f, 40f, 96f).thin()
        line(8f, 50f, 72f, 50f).thin()
        line(28f, 68f, 52f, 68f).thin()
        line(26f, 78f, 54f, 78f).thin()
        mirror(40f) {
            path(closed = true) { moveTo(20f, 50f); quadTo(27f, 44f, 34f, 50f); quadTo(27f, 54f, 20f, 50f) }
            circle(27f, 49.6f, 1.8f).thin()
            path { moveTo(18f, 43f); quadTo(26f, 38f, 35f, 42f) }
            path { moveTo(10f, 50f); cubicTo(5f, 52f, 6f, 64f, 11f, 66f) }
        }
        path { moveTo(38f, 56f); lineTo(35f, 67f); quadTo(40f, 70f, 45f, 67f) }
        path { moveTo(30f, 78f); quadTo(40f, 74f, 50f, 78f) }
        path { moveTo(30f, 78f); quadTo(40f, 85f, 50f, 78f) }
        path { moveTo(13f, 34f); cubicTo(20f, 18f, 60f, 18f, 67f, 34f) }.thin()
    }

    private fun leaf() = practice("leaf", "Damarlı yaprak", 120f, 70f, listOf("yaprak", "damar", "bitki", "botanik", "leaf")) {
        path(closed = true) {
            moveTo(8f, 35f)
            cubicTo(30f, 2f, 90f, 4f, 114f, 35f)
            cubicTo(90f, 66f, 30f, 68f, 8f, 35f)
        }
        line(0f, 37f, 114f, 35f)
        for (i in 1..6) {
            val x = 8f + i * 14f
            val half = 30f * sin(PI.toFloat() * (x + 12f - 8f) / 106f).coerceAtLeast(0.2f)
            path { moveTo(x, 35f); quadTo(x + 6f, 35f - half * 0.4f, x + 12f, 35f - half * 0.75f) }.thin()
            path { moveTo(x, 35f); quadTo(x + 6f, 35f + half * 0.4f, x + 12f, 35f + half * 0.75f) }.thin()
        }
    }

    private fun rose() = practice("rose", "Gül", 100f, 120f, listOf("gül", "çiçek", "bitki", "botanik", "rose")) {
        curve(samples = 120) { t ->
            val a = t * 3f * PI.toFloat()
            val r = 2f + 10f * t
            P(50f + r * cos(a), 38f + r * 0.8f * sin(a))
        }
        path { moveTo(34f, 40f); cubicTo(30f, 20f, 70f, 18f, 66f, 40f) }
        path { moveTo(26f, 36f); cubicTo(20f, 62f, 80f, 62f, 74f, 36f) }
        mirror(50f) { path { moveTo(26f, 36f); cubicTo(10f, 30f, 12f, 58f, 30f, 62f) } }
        path { moveTo(30f, 62f); cubicTo(40f, 72f, 60f, 72f, 70f, 62f) }
        path { moveTo(50f, 68f); cubicTo(52f, 88f, 46f, 100f, 50f, 118f) }
        path(closed = true) { moveTo(49f, 92f); cubicTo(36f, 82f, 24f, 84f, 16f, 92f); cubicTo(28f, 100f, 40f, 98f, 49f, 92f) }
        line(18f, 92f, 47f, 92f).thin()
        poly(51f, 102f, 56f, 99f, 51f, 98f).thin()
    }

    private fun dome() = practice("dome", "Kubbe silueti", 120f, 100f, listOf("kubbe", "cami", "minare", "mimari", "dome", "architecture")) {
        line(0f, 94f, 120f, 94f)
        rect(24f, 62f, 72f, 32f)
        rect(36f, 50f, 48f, 12f)
        arc(60f, 50f, 24f, 24f, 180f, 180f)
        line(60f, 26f, 60f, 16f)
        circle(60f, 14f, 2f).thin()
        mirror(60f) {
            arc(32f, 62f, 8f, 8f, 180f, 180f)
            // Minare: gövde, şerefe ve külah
            rect(8f, 30f, 6f, 64f)
            line(6f, 46f, 16f, 46f)
            poly(8f, 30f, 11f, 12f, 14f, 30f)
            for (i in 0 until 3) path { moveTo(40f + i * 7f, 62f); lineTo(40f + i * 7f, 56f); quadTo(42.5f + i * 7f, 53f, 45f + i * 7f, 56f); lineTo(45f + i * 7f, 62f) }.thin()
            path { moveTo(30f, 94f); lineTo(30f, 80f); quadTo(34f, 74f, 38f, 80f); lineTo(38f, 94f) }.thin()
        }
    }

    private fun cup() = practice("cup", "Fincan", 110f, 100f, listOf("fincan", "natürmort", "çay", "kahve", "cup", "still life")) {
        withTransform(Affine.translate(0f, 20f)) {
            ellipse(52f, 66f, 46f, 10f)
            ellipse(52f, 64f, 28f, 6f).thin()
            ellipse(50f, 18f, 28f, 7f)
            ellipse(50f, 20f, 23f, 5f).thin()
            path { moveTo(22f, 18f); cubicTo(22f, 44f, 32f, 60f, 50f, 60f); cubicTo(68f, 60f, 78f, 44f, 78f, 18f) }
            path { moveTo(77f, 26f); cubicTo(96f, 22f, 98f, 46f, 72f, 46f) }
            path { moveTo(76f, 32f); cubicTo(88f, 30f, 88f, 42f, 74f, 41f) }.thin()
            smooth(42f, 8f, 38f, 0f, 44f, -8f, 40f, -16f).thin()
            smooth(56f, 8f, 52f, 0f, 58f, -8f, 54f, -16f).thin()
        }
    }

    /** Temel alıştırmaların şekilleri (basit ve gelişmiş resim modları). */
    val basics: List<LibShape> by lazy { listOf(lines(), radial(), ellipses(), spiral(), waves(), valueScale()) }

    /** Desenler (mandala, petek ve balık pulu basit modda da; ötekiler yalnız gelişmiş modda). */
    val patterns: List<LibShape> by lazy { listOf(mandala(), honeycomb(), scales(), star8(), star12(), tulip(), rumi()) }

    /** Hacim, perspektif ve konular (yalnız gelişmiş mod). */
    val subjects: List<LibShape> by lazy { listOf(cube1p(), box2p(), sphere(), cylinder(), eye(), face(), leaf(), rose(), dome(), cup()) }

    /** Basit modda da sunulan şekiller (kimlik, ön eksiz). */
    val simpleIds: Set<String> = setOf(
        "practice.lines", "practice.radial", "practice.ellipses", "practice.spiral", "practice.waves", "practice.value_scale",
        "practice.mandala", "practice.honeycomb", "practice.scales",
    )

    val all: List<LibShape> get() = basics + patterns + subjects

    val categories: List<LibCategory> by lazy {
        listOf(
            LibCategory("practice_basics", "Temel alıştırmalar", Audience.ART, basics, SECTION),
            LibCategory("practice_patterns", "Desenler ve motifler", Audience.ART, patterns, SECTION),
            LibCategory("practice_subjects", "Hacim ve konular", Audience.ART, subjects, SECTION),
        )
    }
}
