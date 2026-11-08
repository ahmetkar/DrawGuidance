package com.karapp.drawguidance.content.library.sketch

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.cos
import kotlin.math.sin

/** "Meyveler": elma, armut, muz, çilek, üzüm, portakal, kiraz, karpuz dilimi, limon, ananas. */
internal object FruitSketches {

    val shapes: List<LibShape> by lazy {
        listOf(apple(), pear(), banana(), strawberry(), grapes(), orange(), cherries(), watermelon(), lemon(), pineapple())
    }

    private fun VectorBuilder.stemLeaf(x: Float, y: Float, stemTop: Float, leafTo: Float) {
        path { moveTo(x, y); cubicTo(x - 1f, y - 4f, x + 1f, stemTop + 3f, x + 3f, stemTop) }
        path(closed = true) { moveTo(x + 1.5f, y - 5f); cubicTo(x + 8f, y - 14f, leafTo - 4f, y - 14f, leafTo, y - 11f); cubicTo(leafTo - 6f, y - 4f, x + 8f, y - 3f, x + 1.5f, y - 5f) }
        path { moveTo(x + 2f, y - 5.5f); cubicTo(x + 8f, y - 9f, leafTo - 6f, y - 10.5f, leafTo - 1f, y - 11f) }.width(0.4f)
    }

    private fun apple() = sketch("apple", "Elma", 80f, 86f, listOf("elma", "meyve", "apple")) {
        path(closed = true) {
            moveTo(40f, 20f)
            cubicTo(52f, 12f, 76f, 16f, 74f, 44f)
            cubicTo(72f, 70f, 56f, 84f, 46f, 80f)
            cubicTo(42f, 78f, 38f, 78f, 34f, 80f)
            cubicTo(24f, 84f, 8f, 70f, 6f, 44f)
            cubicTo(4f, 16f, 28f, 12f, 40f, 20f)
        }
        stemLeaf(40f, 21f, 6f, 62f)
        path { moveTo(18f, 34f); cubicTo(16f, 40f, 16f, 46f, 18f, 52f) }.width(0.45f)
    }

    private fun pear() = sketch("pear", "Armut", 70f, 100f, listOf("armut", "meyve", "pear")) {
        path(closed = true) {
            moveTo(35f, 18f)
            cubicTo(44f, 18f, 46f, 30f, 48f, 40f)
            cubicTo(50f, 50f, 64f, 58f, 62f, 76f)
            cubicTo(60f, 92f, 46f, 96f, 35f, 96f)
            cubicTo(24f, 96f, 10f, 92f, 8f, 76f)
            cubicTo(6f, 58f, 20f, 50f, 22f, 40f)
            cubicTo(24f, 30f, 26f, 18f, 35f, 18f)
        }
        stemLeaf(35f, 19f, 5f, 56f)
        path { moveTo(16f, 66f); cubicTo(14f, 72f, 15f, 80f, 18f, 84f) }.width(0.45f)
    }

    private fun banana() = sketch("banana", "Muz", 110f, 70f, listOf("muz", "meyve", "banana")) {
        path(closed = true) {
            moveTo(10f, 20f)
            cubicTo(22f, 52f, 70f, 62f, 100f, 30f)
            lineTo(104f, 24f)
            lineTo(98f, 22f)
            cubicTo(78f, 44f, 40f, 44f, 18f, 16f)
            lineTo(14f, 12f)
            lineTo(8f, 14f)
            lineTo(10f, 20f)
        }
        path { moveTo(16f, 18f); cubicTo(30f, 46f, 72f, 52f, 100f, 27f) }.detail()
        path { moveTo(9f, 15f); lineTo(6f, 9f); lineTo(10f, 8f); lineTo(13f, 13f) }.detail()
    }

    private fun strawberry() = sketch("strawberry", "Çilek", 70f, 90f, listOf("çilek", "meyve", "strawberry")) {
        path(closed = true) {
            moveTo(35f, 20f)
            cubicTo(56f, 16f, 66f, 30f, 62f, 46f)
            cubicTo(58f, 64f, 44f, 82f, 35f, 86f)
            cubicTo(26f, 82f, 12f, 64f, 8f, 46f)
            cubicTo(4f, 30f, 14f, 16f, 35f, 20f)
        }
        // Yaprak taç ve sap
        path(closed = true) {
            moveTo(35f, 22f)
            lineTo(22f, 26f); lineTo(27f, 20f); lineTo(16f, 16f); lineTo(29f, 15f); lineTo(30f, 8f)
            lineTo(35f, 14f); lineTo(40f, 8f); lineTo(41f, 15f); lineTo(54f, 16f); lineTo(43f, 20f); lineTo(48f, 26f)
        }
        line(35f, 12f, 36f, 3f)
        // Tohumlar
        for (row in 0..5) for (col in -3..3) {
            val y = 32f + row * 8.5f
            val half = when (row) { 0 -> 22f; 1 -> 22f; 2 -> 20f; 3 -> 16f; 4 -> 11f; else -> 6f }
            val x = 35f + col * 7f + (if (row % 2 == 1) 3.5f else 0f)
            if (kotlin.math.abs(x - 35f) < half) ellipse(x, y, 0.9f, 1.5f).width(0.45f)
        }
    }

    private fun grapes() = sketch("grapes", "Üzüm", 76f, 100f, listOf("üzüm", "salkım", "meyve", "grapes")) {
        path { moveTo(38f, 20f); cubicTo(38f, 12f, 42f, 6f, 48f, 4f) }
        path(closed = true) { moveTo(40f, 14f); cubicTo(50f, 4f, 66f, 6f, 72f, 16f); cubicTo(62f, 22f, 50f, 22f, 40f, 14f) }
        path { moveTo(42f, 14f); cubicTo(52f, 12f, 62f, 13f, 70f, 16f) }.width(0.4f)
        val rows = listOf(5, 5, 4, 4, 3, 2, 1)
        rows.forEachIndexed { r, n ->
            val y = 28f + r * 10f
            val start = 38f - (n - 1) * 5.5f
            for (i in 0 until n) {
                val cx = start + i * 11f
                circle(cx, y, 6f)
                arc(cx - 1.5f, y - 1.5f, 3f, 3f, 200f, 70f).width(0.35f)
            }
        }
    }

    private fun orange() = sketch("orange", "Portakal", 100f, 70f, listOf("portakal", "narenciye", "meyve", "orange")) {
        // Bütün portakal
        circle(30f, 38f, 24f)
        for (k in 0..10) circle(18f + (k % 4) * 8f, 26f + (k / 4) * 10f, 0.6f).width(0.4f)
        path(closed = true) { moveTo(30f, 14f); cubicTo(34f, 6f, 44f, 4f, 48f, 8f); cubicTo(42f, 12f, 36f, 14f, 30f, 14f) }
        // Kesilmiş yarım (dilimler)
        circle(74f, 42f, 22f)
        circle(74f, 42f, 19f).detail()
        for (i in 0 until 10) {
            val a = Math.toRadians(i * 36.0)
            line(74f + 2f * cos(a).toFloat(), 42f + 2f * sin(a).toFloat(), 74f + 18f * cos(a).toFloat(), 42f + 18f * sin(a).toFloat()).width(0.5f)
        }
        circle(74f, 42f, 2f).detail()
    }

    private fun cherries() = sketch("cherries", "Kiraz", 80f, 90f, listOf("kiraz", "vişne", "meyve", "cherry")) {
        path { moveTo(24f, 62f); cubicTo(28f, 40f, 38f, 20f, 50f, 8f) }
        path { moveTo(56f, 66f); cubicTo(54f, 44f, 52f, 24f, 50f, 8f) }
        path(closed = true) { moveTo(50f, 9f); cubicTo(58f, 0f, 72f, 2f, 76f, 10f); cubicTo(66f, 16f, 56f, 15f, 50f, 9f) }
        path { moveTo(52f, 9f); cubicTo(60f, 7f, 68f, 8f, 74f, 10f) }.width(0.4f)
        for ((x, y) in listOf(22f to 72f, 57f to 76f)) {
            path(closed = true) {
                moveTo(x, y - 12f)
                cubicTo(x + 6f, y - 16f, x + 16f, y - 12f, x + 15f, y)
                cubicTo(x + 14f, y + 10f, x + 4f, y + 14f, x, y + 13f)
                cubicTo(x - 4f, y + 14f, x - 14f, y + 10f, x - 15f, y)
                cubicTo(x - 16f, y - 12f, x - 6f, y - 16f, x, y - 12f)
            }
            arc(x - 6f, y - 3f, 4f, 5f, 190f, 70f).width(0.4f)
        }
    }

    private fun watermelon() = sketch("watermelon", "Karpuz dilimi", 110f, 64f, listOf("karpuz", "dilim", "yaz", "meyve", "watermelon")) {
        // Dilim: kabuk, kabuk içi ve kırmızı kısım (yarım daire)
        path(closed = true) { moveTo(4f, 10f); lineTo(106f, 10f); arcTo(55f, 10f, 51f, 50f, 0f, 180f) }
        arc(55f, 10f, 45f, 44f, 0f, 180f).detail()
        arc(55f, 10f, 41f, 40f, 0f, 180f).detail()
        // Çekirdekler
        for ((x, y) in listOf(30f to 20f, 44f to 26f, 58f to 20f, 72f to 26f, 84f to 18f, 38f to 36f, 56f to 38f, 72f to 36f, 26f to 30f, 48f to 44f, 64f to 44f)) {
            path(closed = true) { moveTo(x, y - 2.6f); cubicTo(x + 2f, y - 1f, x + 1.5f, y + 2f, x, y + 2.4f); cubicTo(x - 1.5f, y + 2f, x - 2f, y - 1f, x, y - 2.6f) }.width(0.5f)
        }
    }

    private fun lemon() = sketch("lemon", "Limon", 100f, 64f, listOf("limon", "narenciye", "meyve", "lemon")) {
        path(closed = true) {
            moveTo(8f, 32f)
            cubicTo(10f, 29f, 12f, 28f, 16f, 26f)
            cubicTo(26f, 6f, 74f, 6f, 84f, 26f)
            cubicTo(88f, 28f, 90f, 29f, 92f, 32f)
            cubicTo(90f, 35f, 88f, 36f, 84f, 38f)
            cubicTo(74f, 58f, 26f, 58f, 16f, 38f)
            cubicTo(12f, 36f, 10f, 35f, 8f, 32f)
        }
        path { moveTo(24f, 22f); cubicTo(34f, 14f, 50f, 13f, 60f, 16f) }.width(0.4f)
        for (k in 0..8) circle(30f + (k % 3) * 16f, 28f + (k / 3) * 7f, 0.6f).width(0.4f)
    }

    private fun pineapple() = sketch("pineapple", "Ananas", 70f, 110f, listOf("ananas", "tropik", "meyve", "pineapple")) {
        // Yapraklar
        for ((ang, len) in listOf(-40f to 22f, -20f to 30f, 0f to 36f, 20f to 30f, 40f to 22f)) {
            group(rotate = ang, px = 35f, py = 40f) {
                path(closed = true) { moveTo(31f, 40f); cubicTo(31f, 30f, 33f, 40f - len * 0.7f, 35f, 40f - len); cubicTo(37f, 40f - len * 0.7f, 39f, 30f, 39f, 40f) }
            }
        }
        // Gövde ve baklava deseni
        ellipse(35f, 72f, 24f, 32f)
        for (k in -5..5) {
            clippedLine(35f + k * 9f - 18f, 40f, 35f + k * 9f + 18f, 104f, 35f, 72f, 23f, 31f)
            clippedLine(35f + k * 9f + 18f, 40f, 35f + k * 9f - 18f, 104f, 35f, 72f, 23f, 31f)
        }
    }
}

/** Doğrunun elipsin içinde kalan parçası (desen çizgileri gövdeden taşmasın). */
internal fun VectorBuilder.clippedLine(x1: Float, y1: Float, x2: Float, y2: Float, cx: Float, cy: Float, rx: Float, ry: Float) {
    val dx = x2 - x1; val dy = y2 - y1
    val ax = (x1 - cx) / rx; val ay = (y1 - cy) / ry
    val bx = dx / rx; val by = dy / ry
    val a = bx * bx + by * by
    val b = 2f * (ax * bx + ay * by)
    val c = ax * ax + ay * ay - 1f
    val disc = b * b - 4f * a * c
    if (disc <= 0f || a < 1e-9f) return
    val sq = kotlin.math.sqrt(disc)
    val t0 = ((-b - sq) / (2f * a)).coerceIn(0f, 1f)
    val t1 = ((-b + sq) / (2f * a)).coerceIn(0f, 1f)
    if (t1 - t0 < 1e-3f) return
    line(x1 + dx * t0, y1 + dy * t0, x1 + dx * t1, y1 + dy * t1).width(0.4f)
}

/** "Sebzeler": havuç, domates, patlıcan, biber, mısır, brokoli, soğan, patates, salatalık, mantar. */
internal object VegetableSketches {

    val shapes: List<LibShape> by lazy {
        listOf(carrot(), tomato(), eggplant(), pepper(), corn(), broccoli(), onion(), potato(), cucumber(), mushroom())
    }

    private fun carrot() = sketch("carrot", "Havuç", 60f, 110f, listOf("havuç", "sebze", "carrot")) {
        for ((dx, h) in listOf(-8f to 22f, 0f to 30f, 8f to 24f)) {
            path(closed = true) { moveTo(30f + dx * 0.4f, 30f); cubicTo(28f + dx, 24f, 26f + dx * 1.4f, 30f - h, 30f + dx * 1.3f, 28f - h); cubicTo(33f + dx * 1.2f, 30f - h, 32f + dx, 24f, 31f + dx * 0.4f, 30f) }
        }
        path(closed = true) { moveTo(14f, 32f); cubicTo(20f, 26f, 40f, 26f, 46f, 32f); cubicTo(44f, 60f, 36f, 88f, 30f, 106f); cubicTo(24f, 88f, 16f, 60f, 14f, 32f) }
        for (k in 0..5) {
            val y = 42f + k * 10f
            val half = 14f - k * 2.2f
            line(30f - half, y, 30f - half + 5f, y + 1f).width(0.4f)
            line(30f + half - 4f, y + 4f, 30f + half, y + 3f).width(0.4f)
        }
    }

    private fun tomato() = sketch("tomato", "Domates", 90f, 80f, listOf("domates", "sebze", "tomato")) {
        path(closed = true) {
            moveTo(45f, 18f)
            cubicTo(56f, 12f, 82f, 18f, 84f, 42f)
            cubicTo(86f, 64f, 66f, 76f, 45f, 76f)
            cubicTo(24f, 76f, 4f, 64f, 6f, 42f)
            cubicTo(8f, 18f, 34f, 12f, 45f, 18f)
        }
        path { moveTo(30f, 20f); cubicTo(26f, 36f, 26f, 56f, 32f, 72f) }.width(0.4f)
        path { moveTo(60f, 20f); cubicTo(64f, 36f, 64f, 56f, 58f, 72f) }.width(0.4f)
        // Çanak yaprakları ve sap
        path(closed = true) {
            moveTo(45f, 20f)
            lineTo(33f, 24f); lineTo(38f, 18f); lineTo(28f, 14f); lineTo(40f, 14f); lineTo(42f, 8f)
            lineTo(46f, 13f); lineTo(52f, 8f); lineTo(51f, 14f); lineTo(62f, 15f); lineTo(52f, 19f); lineTo(57f, 24f)
        }
        path { moveTo(45f, 12f); cubicTo(45f, 8f, 47f, 5f, 50f, 3f) }
    }

    private fun eggplant() = sketch("eggplant", "Patlıcan", 110f, 70f, listOf("patlıcan", "sebze", "eggplant")) {
        path(closed = true) {
            moveTo(26f, 24f)
            cubicTo(40f, 18f, 60f, 22f, 76f, 16f)
            cubicTo(98f, 8f, 110f, 28f, 104f, 44f)
            cubicTo(96f, 64f, 70f, 64f, 50f, 54f)
            cubicTo(40f, 48f, 30f, 44f, 26f, 40f)
        }
        // Çanak ve sap
        path(closed = true) { moveTo(30f, 22f); cubicTo(22f, 16f, 14f, 22f, 12f, 30f); cubicTo(16f, 36f, 22f, 42f, 30f, 42f); lineTo(34f, 36f); lineTo(28f, 32f); lineTo(34f, 28f) }
        path { moveTo(12f, 30f); cubicTo(8f, 28f, 5f, 24f, 4f, 18f) }
        path { moveTo(60f, 30f); cubicTo(70f, 26f, 82f, 22f, 92f, 24f) }.width(0.4f)
    }

    private fun pepper() = sketch("pepper", "Biber", 80f, 100f, listOf("biber", "dolmalık", "sebze", "pepper")) {
        path(closed = true) {
            moveTo(40f, 22f)
            cubicTo(52f, 14f, 72f, 20f, 72f, 40f)
            cubicTo(72f, 62f, 64f, 84f, 54f, 94f)
            cubicTo(50f, 98f, 46f, 94f, 42f, 90f)
            cubicTo(38f, 94f, 32f, 98f, 28f, 94f)
            cubicTo(16f, 84f, 8f, 62f, 8f, 40f)
            cubicTo(8f, 20f, 28f, 14f, 40f, 22f)
        }
        path { moveTo(40f, 26f); cubicTo(38f, 48f, 40f, 70f, 42f, 88f) }.width(0.45f)
        path { moveTo(24f, 28f); cubicTo(20f, 46f, 22f, 70f, 28f, 88f) }.width(0.4f)
        path { moveTo(56f, 28f); cubicTo(60f, 46f, 58f, 70f, 54f, 88f) }.width(0.4f)
        path(closed = true) { moveTo(32f, 20f); cubicTo(36f, 16f, 44f, 16f, 48f, 20f); cubicTo(44f, 24f, 36f, 24f, 32f, 20f) }
        path { moveTo(40f, 17f); cubicTo(40f, 10f, 44f, 5f, 50f, 4f) }
    }

    private fun corn() = sketch("corn", "Mısır", 70f, 110f, listOf("mısır", "koçan", "sebze", "corn")) {
        ellipse(35f, 50f, 15f, 42f)
        // Taneler
        for (row in 0..12) for (col in -2..2) {
            val y = 16f + row * 5.6f
            val half = 15f * kotlin.math.sqrt((1f - ((y - 50f) / 42f) * ((y - 50f) / 42f)).coerceAtLeast(0f))
            val x = 35f + col * 5.4f + (if (row % 2 == 1) 2.7f else 0f)
            if (kotlin.math.abs(x - 35f) < half - 2.5f) roundRect(x - 2.3f, y - 2.3f, 4.6f, 4.6f, 1.6f).width(0.4f)
        }
        // Kabuk yaprakları
        path(closed = true) { moveTo(35f, 106f); cubicTo(18f, 96f, 6f, 70f, 10f, 40f); cubicTo(18f, 62f, 26f, 80f, 35f, 92f) }
        path(closed = true) { moveTo(35f, 106f); cubicTo(52f, 96f, 64f, 70f, 60f, 40f); cubicTo(52f, 62f, 44f, 80f, 35f, 92f) }
        line(35f, 106f, 35f, 109f)
    }

    private fun broccoli() = sketch("broccoli", "Brokoli", 90f, 100f, listOf("brokoli", "sebze", "broccoli")) {
        // Sap ve dallar
        path(closed = true) { moveTo(36f, 96f); cubicTo(38f, 80f, 38f, 66f, 30f, 54f); lineTo(60f, 54f); cubicTo(52f, 66f, 52f, 80f, 54f, 96f) }
        path { moveTo(40f, 60f); cubicTo(42f, 56f, 44f, 52f, 44f, 48f) }.detail()
        path { moveTo(50f, 60f); cubicTo(50f, 56f, 52f, 52f, 56f, 48f) }.detail()
        // Çiçek kümesi (kabarık)
        puff(45f, 34f, 38f, 22f, 12, depth = 0.3f)
        for ((x, y) in listOf(28f to 30f, 46f to 24f, 62f to 32f, 36f to 42f, 56f to 42f)) puff(x, y, 7f, 6f, 6, depth = 0.25f).width(0.4f)
    }

    private fun onion() = sketch("onion", "Soğan", 80f, 100f, listOf("soğan", "sebze", "onion")) {
        path(closed = true) {
            moveTo(40f, 18f)
            cubicTo(44f, 32f, 72f, 38f, 72f, 62f)
            cubicTo(72f, 84f, 56f, 92f, 40f, 92f)
            cubicTo(24f, 92f, 8f, 84f, 8f, 62f)
            cubicTo(8f, 38f, 36f, 32f, 40f, 18f)
        }
        path { moveTo(40f, 22f); cubicTo(30f, 38f, 24f, 60f, 32f, 90f) }.width(0.45f)
        path { moveTo(40f, 22f); cubicTo(50f, 38f, 56f, 60f, 48f, 90f) }.width(0.45f)
        // Filiz ve kökler
        path { moveTo(38f, 20f); cubicTo(36f, 12f, 34f, 6f, 30f, 2f) }
        path { moveTo(42f, 20f); cubicTo(44f, 12f, 48f, 6f, 52f, 3f) }
        for (k in -2..2) line(40f + k * 2f, 92f, 40f + k * 4f, 98f).width(0.45f)
    }

    private fun potato() = sketch("potato", "Patates", 100f, 70f, listOf("patates", "sebze", "potato")) {
        blob(20f, 16f, 44f, 10f, 70f, 12f, 90f, 22f, 94f, 40f, 84f, 56f, 60f, 62f, 34f, 60f, 12f, 50f, 8f, 32f)
        for ((x, y) in listOf(30f to 26f, 58f to 22f, 76f to 40f, 44f to 46f, 22f to 42f)) {
            arc(x, y, 3f, 1.8f, 200f, 140f).width(0.5f)
            circle(x, y + 0.6f, 0.5f).width(0.4f)
        }
    }

    private fun cucumber() = sketch("cucumber", "Salatalık", 110f, 50f, listOf("salatalık", "hıyar", "sebze", "cucumber")) {
        path(closed = true) {
            moveTo(10f, 22f)
            cubicTo(14f, 10f, 30f, 10f, 56f, 12f)
            cubicTo(80f, 13f, 98f, 14f, 102f, 24f)
            cubicTo(104f, 34f, 88f, 38f, 60f, 38f)
            cubicTo(32f, 38f, 14f, 36f, 10f, 22f)
        }
        path { moveTo(6f, 20f); lineTo(10f, 22f) }
        for (k in 0..12) circle(18f + k * 6.6f, 20f + (k % 3) * 5f, 0.6f).width(0.45f)
        path { moveTo(20f, 16f); cubicTo(40f, 14f, 70f, 15f, 94f, 18f) }.width(0.4f)
    }

    private fun mushroom() = sketch("mushroom", "Mantar", 90f, 90f, listOf("mantar", "sebze", "orman", "mushroom")) {
        path(closed = true) { moveTo(34f, 50f); cubicTo(34f, 66f, 30f, 78f, 28f, 86f); lineTo(62f, 86f); cubicTo(60f, 78f, 56f, 66f, 56f, 50f) }
        path(closed = true) {
            moveTo(6f, 50f)
            cubicTo(4f, 20f, 30f, 6f, 45f, 6f)
            cubicTo(60f, 6f, 86f, 20f, 84f, 50f)
            cubicTo(70f, 54f, 20f, 54f, 6f, 50f)
        }
        for (k in 0..6) line(16f + k * 10f, 51f, 20f + k * 8.3f, 57f).width(0.4f)
        for ((x, y, r) in listOf(Triple(28f, 24f, 5f), Triple(52f, 18f, 4f), Triple(66f, 34f, 5.5f), Triple(40f, 38f, 3.5f), Triple(18f, 40f, 3f))) circle(x, y, r).detail()
    }
}
