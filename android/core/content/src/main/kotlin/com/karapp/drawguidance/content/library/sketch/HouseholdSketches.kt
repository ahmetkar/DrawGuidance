package com.karapp.drawguidance.content.library.sketch

import com.karapp.drawguidance.content.library.LibShape
import kotlin.math.cos
import kotlin.math.sin

/** "Ev eşyaları": sandalye, masa, abajur, kupa, çaydanlık, kitaplar, duvar saati, kanepe, vazo, yatak, şişe ve bardak, saksı. */
internal object HouseholdSketches {

    val shapes: List<LibShape> by lazy {
        listOf(chair(), table(), lamp(), mug(), teapot(), books(), clock(), sofa(), vase(), bed(), bottleGlass(), pottedPlant())
    }

    private fun chair() = sketch("chair", "Sandalye", 70f, 100f, listOf("sandalye", "mobilya", "oturak", "chair")) {
        guide(2f, 97f, 68f, 97f)
        // Arkalık
        rect(16f, 6f, 38f, 8f)
        line(18f, 14f, 18f, 52f); line(52f, 14f, 52f, 52f)
        for (k in 1..2) line(18f + k * 11.3f, 14f, 18f + k * 11.3f, 46f).detail()
        // Oturak (hafif perspektif) ve bacaklar
        path(closed = true) { moveTo(12f, 56f); lineTo(58f, 56f); lineTo(62f, 62f); lineTo(8f, 62f) }
        rect(8f, 62f, 54f, 4f)
        line(11f, 66f, 11f, 96f); line(59f, 66f, 59f, 96f)
        line(18f, 66f, 18f, 88f).detail(); line(52f, 66f, 52f, 88f).detail()
        line(11f, 82f, 59f, 82f).detail()
    }

    private fun table() = sketch("table", "Masa", 120f, 80f, listOf("masa", "mobilya", "table")) {
        guide(2f, 77f, 118f, 77f)
        path(closed = true) { moveTo(20f, 18f); lineTo(100f, 18f); lineTo(114f, 30f); lineTo(6f, 30f) }
        rect(6f, 30f, 108f, 5f)
        rect(12f, 35f, 5f, 42f); rect(103f, 35f, 5f, 42f)
        rect(28f, 35f, 4f, 30f).detail(); rect(88f, 35f, 4f, 30f).detail()
        rect(17f, 35f, 86f, 8f).detail()
        line(56f, 39f, 64f, 39f)
    }

    private fun lamp() = sketch("lamp", "Abajur", 70f, 110f, listOf("abajur", "lamba", "ışık", "lamp")) {
        guide(2f, 106f, 68f, 106f)
        path(closed = true) { moveTo(22f, 8f); lineTo(48f, 8f); lineTo(60f, 42f); lineTo(10f, 42f) }
        line(14f, 34f, 56f, 34f).detail()
        // Gövde (vazo biçimi) ve taban
        line(35f, 42f, 35f, 52f)
        path(closed = true) {
            moveTo(30f, 52f); lineTo(40f, 52f)
            cubicTo(52f, 60f, 52f, 84f, 42f, 94f)
            lineTo(28f, 94f)
            cubicTo(18f, 84f, 18f, 60f, 30f, 52f)
        }
        ellipse(35f, 99f, 16f, 4f)
        line(19f, 99f, 19f, 102f); line(51f, 99f, 51f, 102f)
        arc(35f, 102f, 16f, 4f, 0f, 180f)
        path { moveTo(44f, 42f); lineTo(44f, 50f) }.detail()
        circle(44f, 51.5f, 1.5f).detail()
    }

    private fun mug() = sketch("mug", "Kupa", 90f, 80f, listOf("kupa", "fincan", "kahve", "çay", "mug")) {
        // Buhar
        for (k in 0..2) path { moveTo(26f + k * 12f, 18f); cubicTo(22f + k * 12f, 12f, 30f + k * 12f, 8f, 26f + k * 12f, 2f) }.width(0.6f)
        // Gövde, ağız elipsi, kulp, tabak
        path(closed = true) { moveTo(12f, 26f); lineTo(62f, 26f); lineTo(60f, 66f); cubicTo(60f, 70f, 56f, 72f, 52f, 72f); lineTo(22f, 72f); cubicTo(18f, 72f, 14f, 70f, 14f, 66f) }
        ellipse(37f, 26f, 25f, 5f)
        ellipse(37f, 27f, 22f, 3.6f).detail()
        path(closed = true) { moveTo(61.5f, 34f); cubicTo(80f, 32f, 84f, 58f, 60.5f, 60f); lineTo(60.8f, 54f); cubicTo(74f, 52f, 72f, 38f, 61.3f, 40f) }
        ellipse(37f, 74f, 34f, 5f)
        ellipse(37f, 74f, 20f, 2.6f).detail()
    }

    private fun teapot() = sketch("teapot", "Çaydanlık", 110f, 90f, listOf("çaydanlık", "demlik", "çay", "mutfak", "teapot")) {
        // Kapak ve topuz
        circle(52f, 12f, 4f)
        path(closed = true) { moveTo(36f, 24f); cubicTo(38f, 16f, 66f, 16f, 68f, 24f) }
        // Gövde
        path(closed = true) {
            moveTo(30f, 26f)
            lineTo(74f, 26f)
            cubicTo(90f, 36f, 92f, 64f, 80f, 78f)
            lineTo(24f, 78f)
            cubicTo(12f, 64f, 14f, 36f, 30f, 26f)
        }
        ellipse(52f, 26f, 22f, 3.4f).detail()
        // Emzik ve kulp
        path(closed = true) { moveTo(20f, 48f); cubicTo(10f, 44f, 8f, 34f, 2f, 28f); lineTo(6f, 26f); cubicTo(12f, 32f, 16f, 38f, 22f, 40f) }
        path(closed = true) { moveTo(84f, 34f); cubicTo(104f, 30f, 108f, 60f, 86f, 68f); lineTo(84.5f, 62f); cubicTo(98f, 56f, 98f, 40f, 85.5f, 40f) }
        // Taban ve süs çizgisi
        rect(28f, 78f, 48f, 4f)
        path { moveTo(22f, 52f); cubicTo(40f, 58f, 64f, 58f, 84f, 52f) }.width(0.45f)
    }

    private fun books() = sketch("books", "Kitaplar", 100f, 90f, listOf("kitap", "kitaplık", "okuma", "book")) {
        guide(2f, 86f, 98f, 86f)
        // Dik duran kitaplar
        rect(8f, 20f, 14f, 66f)
        rect(22f, 12f, 12f, 74f)
        for (y in listOf(26f, 76f)) line(8f, y, 22f, y).detail()
        for (y in listOf(18f, 80f)) line(22f, y, 34f, y).detail()
        // Eğik kitap
        points(listOf(p(34f, 86f), p(34f, 30f), p(46f, 27f), p(58f, 82f)), closed = true)
        // Üst üste yatan kitaplar
        rect(58f, 72f, 38f, 14f)
        rect(62f, 60f, 32f, 12f)
        rect(60f, 50f, 34f, 10f)
        line(64f, 79f, 90f, 79f).detail()
        line(66f, 66f, 90f, 66f).detail()
        // Açık kitap (üstte)
        path(closed = true) { moveTo(77f, 50f); cubicTo(70f, 44f, 62f, 44f, 58f, 46f); lineTo(58f, 36f); cubicTo(64f, 34f, 72f, 35f, 77f, 40f) }
        path(closed = true) { moveTo(77f, 50f); cubicTo(84f, 44f, 92f, 44f, 96f, 46f); lineTo(96f, 36f); cubicTo(90f, 34f, 82f, 35f, 77f, 40f) }
    }

    private fun p(x: Float, y: Float) = com.karapp.drawguidance.content.vector.P(x, y)

    private fun clock() = sketch("wall_clock", "Duvar saati", 90f, 90f, listOf("saat", "duvar saati", "zaman", "clock")) {
        circle(45f, 45f, 40f)
        circle(45f, 45f, 35f).detail()
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0)
            val r1 = if (i % 5 == 0) 29f else 32f
            line(45f + r1 * cos(a).toFloat(), 45f + r1 * sin(a).toFloat(), 45f + 34f * cos(a).toFloat(), 45f + 34f * sin(a).toFloat())
                .width(if (i % 5 == 0) 0.7f else 0.35f)
        }
        // Akrep (10) ve yelkovan (2), saniye
        line(45f, 45f, 45f - 12f * cos(Math.toRadians(30.0)).toFloat(), 45f - 12f * sin(Math.toRadians(30.0)).toFloat()).width(1.3f)
        line(45f, 45f, 45f + 22f * cos(Math.toRadians(-60.0)).toFloat(), 45f + 22f * sin(Math.toRadians(-60.0)).toFloat()).width(0.9f)
        line(45f, 45f, 45f, 72f).width(0.4f)
        circle(45f, 45f, 2f)
    }

    private fun sofa() = sketch("sofa", "Kanepe", 140f, 80f, listOf("kanepe", "koltuk", "mobilya", "salon", "sofa")) {
        guide(2f, 77f, 138f, 77f)
        // Arkalık, oturak minderleri, kolluklar, ayaklar
        path(closed = true) { moveTo(16f, 44f); lineTo(16f, 18f); cubicTo(16f, 10f, 22f, 8f, 30f, 8f); lineTo(110f, 8f); cubicTo(118f, 8f, 124f, 10f, 124f, 18f); lineTo(124f, 44f) }
        line(70f, 10f, 70f, 44f).detail()
        roundRect(20f, 44f, 50f, 14f, 4f)
        roundRect(70f, 44f, 50f, 14f, 4f)
        roundRect(4f, 32f, 18f, 38f, 7f)
        roundRect(118f, 32f, 18f, 38f, 7f)
        rect(20f, 58f, 100f, 12f)
        rect(10f, 70f, 5f, 7f); rect(125f, 70f, 5f, 7f)
        // Yastık
        path(closed = true) { moveTo(28f, 40f); cubicTo(26f, 30f, 30f, 22f, 34f, 20f); cubicTo(40f, 22f, 48f, 24f, 52f, 26f); cubicTo(50f, 32f, 50f, 38f, 52f, 44f) }.detail()
    }

    private fun vase() = sketch("vase", "Vazo ve çiçekler", 80f, 110f, listOf("vazo", "çiçek", "süs", "vase")) {
        // Çiçekler
        for ((x, y, sx) in listOf(Triple(24f, 16f, -1f), Triple(40f, 8f, 0f), Triple(56f, 18f, 1f))) {
            path { moveTo(40f + sx * 3f, 58f); cubicTo(40f + sx * 4f, 44f, x, y + 20f, x, y + 7f) }
            for (i in 0 until 6) {
                val a = Math.toRadians(i * 60.0)
                circle(x + 5f * cos(a).toFloat(), y + 5f * sin(a).toFloat(), 3.4f).width(0.7f)
            }
            circle(x, y, 2.6f)
        }
        path(closed = true) { moveTo(40f, 50f); cubicTo(30f, 44f, 22f, 44f, 16f, 40f); cubicTo(24f, 38f, 34f, 42f, 40f, 50f) }.detail()
        // Vazo
        path(closed = true) {
            moveTo(30f, 56f); lineTo(50f, 56f)
            cubicTo(50f, 62f, 46f, 64f, 48f, 68f)
            cubicTo(62f, 76f, 64f, 96f, 52f, 106f)
            lineTo(28f, 106f)
            cubicTo(16f, 96f, 18f, 76f, 32f, 68f)
            cubicTo(34f, 64f, 30f, 62f, 30f, 56f)
        }
        path { moveTo(22f, 86f); cubicTo(34f, 90f, 46f, 90f, 58f, 86f) }.width(0.45f)
    }

    private fun bed() = sketch("bed", "Yatak", 140f, 84f, listOf("yatak", "yatak odası", "mobilya", "bed")) {
        guide(2f, 80f, 138f, 80f)
        // Başlık
        path(closed = true) { moveTo(8f, 80f); lineTo(8f, 16f); cubicTo(8f, 8f, 30f, 6f, 30f, 16f); lineTo(30f, 80f) }
        // Şilte, yorgan, yastık, ayak ucu
        rect(30f, 44f, 102f, 14f)
        path(closed = true) { moveTo(56f, 42f); lineTo(132f, 42f); lineTo(132f, 62f); cubicTo(110f, 66f, 80f, 66f, 56f, 62f) }
        path(closed = true) { moveTo(32f, 42f); cubicTo(32f, 32f, 38f, 30f, 44f, 30f); lineTo(52f, 30f); cubicTo(58f, 30f, 58f, 42f, 52f, 44f); lineTo(36f, 44f) }
        rect(126f, 36f, 8f, 44f)
        rect(30f, 58f, 96f, 6f).detail()
        line(70f, 43f, 70f, 63f).width(0.4f)
    }

    private fun bottleGlass() = sketch("bottle_glass", "Şişe ve bardak", 90f, 100f, listOf("şişe", "bardak", "su", "içecek", "bottle", "glass")) {
        guide(2f, 96f, 88f, 96f)
        // Şişe
        rect(21f, 4f, 10f, 6f)
        path(closed = true) {
            moveTo(22f, 10f); lineTo(30f, 10f); lineTo(30f, 24f)
            cubicTo(30f, 30f, 42f, 32f, 42f, 42f)
            lineTo(42f, 92f); cubicTo(42f, 95f, 40f, 96f, 38f, 96f)
            lineTo(14f, 96f); cubicTo(12f, 96f, 10f, 95f, 10f, 92f)
            lineTo(10f, 42f); cubicTo(10f, 32f, 22f, 30f, 22f, 24f)
        }
        rect(10f, 56f, 32f, 20f).detail()
        // Bardak
        path(closed = true) { moveTo(54f, 50f); lineTo(82f, 50f); lineTo(78f, 96f); lineTo(58f, 96f) }
        ellipse(68f, 50f, 14f, 3f).detail()
        path { moveTo(55.2f, 62f); cubicTo(62f, 64f, 74f, 64f, 80.8f, 62f) }.detail()
        line(76f, 70f, 74.5f, 88f).width(0.4f)
    }

    private fun pottedPlant() = sketch("potted_plant", "Saksı bitkisi", 80f, 110f, listOf("saksı", "bitki", "çiçek", "ev", "plant")) {
        // Yapraklar
        fun leafAt(x: Float, y: Float, tx: Float, ty: Float, w: Float) {
            path(closed = true) { moveTo(x, y); cubicTo(x + (tx - x) * 0.2f - w, y + (ty - y) * 0.5f, tx - w * 0.5f, ty + 2f, tx, ty); cubicTo(tx + w * 0.5f, ty + 2f, x + (tx - x) * 0.2f + w, y + (ty - y) * 0.5f, x, y) }
            line(x, y, tx, ty).width(0.4f)
        }
        leafAt(40f, 66f, 14f, 22f, 7f)
        leafAt(40f, 66f, 40f, 6f, 7f)
        leafAt(40f, 66f, 66f, 20f, 7f)
        leafAt(40f, 66f, 6f, 46f, 6f)
        leafAt(40f, 66f, 74f, 44f, 6f)
        // Saksı
        rect(16f, 66f, 48f, 8f)
        path(closed = true) { moveTo(19f, 74f); lineTo(61f, 74f); lineTo(56f, 106f); lineTo(24f, 106f) }
        path { moveTo(21f, 86f); lineTo(59f, 86f) }.width(0.45f)
    }
}
