package com.karapp.drawguidance.content.library.drawings

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.cos
import kotlin.math.sin

/** "Hayvanlar" hazır çizimleri. */
internal object AnimalDrawings {

    val shapes: List<LibShape> by lazy {
        listOf(cat(), dog(), fish(), bird(), butterfly(), rabbit(), owl(), bee(), turtle())
    }

    /** Parlak, canlı göz: iris, göz bebeği, iki parıltı. */
    private fun VectorBuilder.eye(cx: Float, cy: Float, rx: Float, ry: Float, iris: String = C.BLACK, pupil: Boolean = false) {
        ellipse(cx, cy, rx, ry).fill(iris).width(0.6f)
        if (pupil) ellipse(cx, cy + ry * 0.05f, rx * 0.35f, ry * 0.75f).fill(C.BLACK).noStroke()
        circle(cx + rx * 0.3f, cy - ry * 0.35f, minOf(rx, ry) * 0.32f).fill(C.WHITE).noStroke()
        circle(cx - rx * 0.3f, cy + ry * 0.35f, minOf(rx, ry) * 0.14f).fill(C.WHITE).noStroke()
    }

    private fun VectorBuilder.whiskers(cx: Float, cy: Float, dir: Float, len: Float) {
        for (k in -1..1) line(cx, cy + k * 1.6f, cx + dir * len, cy + k * 3.2f - 1.5f).width(0.45f)
    }

    private fun cat() = drawing("cat", "Kedi", 90f, 100f, listOf("kedi", "evcil", "hayvan", "tekir", "cat")) {
        val fur = "#F2A65A"; val stripe = "#D9782B"; val cream = "#FFF1DE"
        shadow(46f, 94f, 32f, 4f, 0.16f)
        // kuyruk
        ribbon(7f, 5f, 62f, 90f, 76f, 88f, 82f, 76f, 78f, 62f, 72f, 56f).fill(fur)
        for ((x, y, a) in listOf(Triple(73f, 88.5f, 80f), Triple(80f, 80f, 20f), Triple(80.5f, 70f, 0f))) {
            group(rotate = a, px = x, py = y) { roundRect(x - 3.2f, y - 0.9f, 6.4f, 1.8f, 0.9f).fill(stripe).noStroke() }
        }
        // arka bacaklar (kalçalar)
        ellipse(29f, 84f, 9f, 8f).fill(fur)
        ellipse(61f, 84f, 9f, 8f).fill(fur)
        // gövde
        smooth(33f, 54f, 27f, 70f, 25f, 86f, 34f, 93f, 56f, 93f, 65f, 86f, 63f, 70f, 57f, 54f, closed = true).fill(fur)
        smooth(39f, 60f, 35f, 76f, 37f, 90f, 53f, 90f, 55f, 76f, 51f, 60f, closed = true).fill(cream).noStroke()
        for (s in listOf(-1f, 1f)) {
            val x0 = 45f + s * 18.5f
            for (k in 0..2) {
                val y = 66f + k * 6f
                path(closed = true) { moveTo(x0, y); quadTo(x0 - s * 4f, y + 0.5f, x0 - s * 6f, y + 3f); quadTo(x0 - s * 3.5f, y + 2f, x0 + s * 0.3f, y + 2f) }
                    .fill(stripe).noStroke()
            }
        }
        // ön bacaklar ve patiler
        roundRect(36.5f, 70f, 7.5f, 23f, 3.7f).fill(fur).width(0.8f)
        roundRect(46f, 70f, 7.5f, 23f, 3.7f).fill(fur).width(0.8f)
        ellipse(40.2f, 92f, 5f, 2.8f).fill(cream).width(0.8f)
        ellipse(49.8f, 92f, 5f, 2.8f).fill(cream).width(0.8f)
        for (x in listOf(38.8f, 41.6f, 48.4f, 51.2f)) line(x, 90.6f, x, 92.4f).width(0.45f)
        ellipse(27f, 92.5f, 5.5f, 2.3f).fill(fur).width(0.8f)
        ellipse(63f, 92.5f, 5.5f, 2.3f).fill(fur).width(0.8f)
        // kulaklar
        path(closed = true) { moveTo(27f, 34f); quadTo(24f, 20f, 27f, 12f); quadTo(36f, 16f, 42f, 25f) }.fill(fur)
        path(closed = true) { moveTo(63f, 34f); quadTo(66f, 20f, 63f, 12f); quadTo(54f, 16f, 48f, 25f) }.fill(fur)
        path(closed = true) { moveTo(29f, 30f); quadTo(27.5f, 21f, 29f, 16f); quadTo(35f, 19f, 38.5f, 25f) }.fill(C.PINK).width(0.5f)
        path(closed = true) { moveTo(61f, 30f); quadTo(62.5f, 21f, 61f, 16f); quadTo(55f, 19f, 51.5f, 25f) }.fill(C.PINK).width(0.5f)
        // baş
        smooth(24f, 41f, 28f, 29f, 37f, 24f, 53f, 24f, 62f, 29f, 66f, 41f, 61f, 51f, 45f, 56f, 29f, 51f, closed = true).fill(fur)
        for ((dx, h) in listOf(-4f to 5f, 0f to 6.5f, 4f to 5f)) {
            path(closed = true) { moveTo(45f + dx - 1.3f, 24.3f); lineTo(45f + dx + 1.3f, 24.3f); lineTo(45f + dx, 24.3f + h) }.fill(stripe).noStroke()
        }
        path(closed = true) { moveTo(24.5f, 40f); quadTo(28f, 40f, 30f, 42f); quadTo(27f, 42f, 24.8f, 42.6f) }.fill(stripe).noStroke()
        path(closed = true) { moveTo(65.5f, 40f); quadTo(62f, 40f, 60f, 42f); quadTo(63f, 42f, 65.2f, 42.6f) }.fill(stripe).noStroke()
        // yüz
        eye(36.5f, 38.5f, 3.8f, 4.4f, "#9CCC65", pupil = true)
        eye(53.5f, 38.5f, 3.8f, 4.4f, "#9CCC65", pupil = true)
        ellipse(30.5f, 46f, 3.2f, 2f).fill(C.PINK).noStroke().alpha(0.55f)
        ellipse(59.5f, 46f, 3.2f, 2f).fill(C.PINK).noStroke().alpha(0.55f)
        ellipse(41.8f, 47.5f, 4f, 3f).fill(cream).width(0.6f)
        ellipse(48.2f, 47.5f, 4f, 3f).fill(cream).width(0.6f)
        path(closed = true) { moveTo(42.6f, 43.6f); lineTo(47.4f, 43.6f); quadTo(47f, 45.8f, 45f, 46.4f); quadTo(43f, 45.8f, 42.6f, 43.6f) }
            .fill(C.PINK_DARK).width(0.6f)
        line(45f, 46.4f, 45f, 48f).width(0.5f)
        whiskers(38f, 47.5f, -1f, 13f)
        whiskers(52f, 47.5f, 1f, 13f)
        // tasma ve çıngırak
        roundRect(33f, 53.5f, 24f, 3.6f, 1.8f).fill(C.RED).width(0.8f)
        circle(45f, 59f, 2.6f).fill(C.YELLOW).width(0.7f)
        line(45f, 59.4f, 45f, 61.4f).width(0.5f)
        circle(44.2f, 58.2f, 0.7f).fill(C.WHITE).noStroke()
    }

    private fun dog() = drawing("dog", "Köpek", 90f, 100f, listOf("köpek", "evcil", "hayvan", "yavru", "dog")) {
        val fur = "#D9A066"; val dark = "#8B5A3C"; val cream = "#FFF4E6"
        shadow(45f, 94f, 32f, 4f, 0.16f)
        // sallanan kuyruk ve hareket çizgileri
        ribbon(6f, 4f, 62f, 86f, 72f, 80f, 78f, 70f, 80f, 62f).fill(fur)
        path { moveTo(84f, 62f); quadTo(87f, 66f, 86f, 70f) }.width(0.5f)
        path { moveTo(86.5f, 58f); quadTo(90f, 64f, 89f, 70f) }.width(0.5f)
        // gövde
        ellipse(29f, 84f, 9f, 8f).fill(fur)
        ellipse(61f, 84f, 9f, 8f).fill(fur)
        smooth(33f, 56f, 27f, 70f, 25f, 86f, 34f, 93f, 56f, 93f, 65f, 86f, 63f, 70f, 57f, 56f, closed = true).fill(fur)
        ellipse(56f, 74f, 5f, 6.5f).fill(dark).noStroke().alpha(0.8f)
        smooth(38f, 58f, 34f, 72f, 37f, 88f, 53f, 88f, 56f, 72f, 52f, 58f, closed = true).fill(cream).noStroke()
        roundRect(36.5f, 70f, 7.5f, 23f, 3.7f).fill(fur).width(0.8f)
        roundRect(46f, 70f, 7.5f, 23f, 3.7f).fill(fur).width(0.8f)
        ellipse(40.2f, 92f, 5f, 2.8f).fill(cream).width(0.8f)
        ellipse(49.8f, 92f, 5f, 2.8f).fill(cream).width(0.8f)
        for (x in listOf(38.8f, 41.6f, 48.4f, 51.2f)) line(x, 90.6f, x, 92.4f).width(0.45f)
        ellipse(27f, 92.5f, 5.5f, 2.3f).fill(fur).width(0.8f)
        ellipse(63f, 92.5f, 5.5f, 2.3f).fill(fur).width(0.8f)
        // tasma
        roundRect(33f, 55f, 24f, 4f, 2f).fill(C.BLUE).width(0.8f)
        circle(45f, 61.5f, 3f).fill(C.YELLOW).width(0.7f)
        circle(45f, 61.5f, 1.3f).fill(C.ORANGE).noStroke()
        // baş
        smooth(26f, 38f, 30f, 26f, 45f, 20f, 60f, 26f, 64f, 38f, 60f, 51f, 45f, 57f, 30f, 51f, closed = true).fill(fur)
        smooth(49f, 26f, 57f, 26f, 60f, 34f, 57f, 42f, 50f, 40f, 48f, 32f, closed = true).fill(dark).noStroke().alpha(0.85f)
        // sarkık kulaklar
        smooth(30f, 26f, 22f, 28f, 16f, 42f, 19f, 52f, 25f, 50f, 29f, 38f, closed = true).fill(dark)
        smooth(60f, 26f, 68f, 28f, 74f, 42f, 71f, 52f, 65f, 50f, 61f, 38f, closed = true).fill(dark)
        // ağız bölgesi
        smooth(34f, 46f, 38f, 40f, 45f, 38.5f, 52f, 40f, 56f, 46f, 52f, 53f, 45f, 55f, 38f, 53f, closed = true).fill(cream).width(0.7f)
        path(closed = true) { moveTo(42f, 49.5f); quadTo(45f, 50.5f, 48f, 49.5f); lineTo(47.6f, 53.5f); arcTo(45f, 53.5f, 2.6f, 2.6f, 0f, 180f) }
            .fill(C.PINK_DARK).width(0.6f)
        line(45f, 50f, 45f, 53.5f).stroke(C.RED_DARK).width(0.4f)
        path { moveTo(39.5f, 47.5f); quadTo(42f, 50.5f, 45f, 48f); quadTo(48f, 50.5f, 50.5f, 47.5f) }.width(0.6f)
        path(closed = true) { moveTo(41.5f, 42.5f); quadTo(45f, 41f, 48.5f, 42.5f); quadTo(47.5f, 46f, 45f, 46.5f); quadTo(42.5f, 46f, 41.5f, 42.5f) }
            .fill(C.BLACK).width(0.6f)
        shine(43.5f, 42.8f, 1.2f, 0.6f, 0f, 0.7f)
        eye(37.5f, 35f, 2.8f, 3.2f)
        eye(52.5f, 35f, 2.8f, 3.2f)
        path { moveTo(34.5f, 30f); quadTo(37.5f, 28f, 40.5f, 30f) }.width(0.6f)
        path { moveTo(49.5f, 30f); quadTo(52.5f, 28f, 55.5f, 30f) }.width(0.6f)
        ellipse(32f, 43f, 2.6f, 1.6f).fill(C.PINK).noStroke().alpha(0.5f)
        ellipse(58f, 43f, 2.6f, 1.6f).fill(C.PINK).noStroke().alpha(0.5f)
        // kemik
        group(rotate = -15f, px = 14f, py = 90f) {
            roundRect(9f, 89f, 10f, 2.4f, 1.2f).fill(C.WHITE).width(0.6f)
            for ((x, y) in listOf(9f to 89f, 9f to 91.4f, 19f to 89f, 19f to 91.4f)) circle(x, y, 1.6f).fill(C.WHITE).width(0.6f)
            roundRect(9f, 89f, 10f, 2.4f, 1.2f).fill(C.WHITE).noStroke()
        }
    }

    private fun fish() = drawing("fish", "Balık", 120f, 80f, listOf("balık", "deniz", "akvaryum", "su", "fish")) {
        val body = C.ORANGE; val dark = C.ORANGE_DARK
        // kuyruk
        path(closed = true) { moveTo(86f, 38f); quadTo(98f, 26f, 112f, 16f); quadTo(106f, 30f, 104f, 40f); quadTo(106f, 50f, 112f, 64f); quadTo(98f, 54f, 86f, 42f) }
            .fill(C.YELLOW)
        for (k in listOf(-14f, -7f, 7f, 14f)) line(92f, 40f + k * 0.2f, 106f, 40f + k * 1.5f).stroke(dark).width(0.45f)
        // sırt ve karın yüzgeçleri
        path(closed = true) { moveTo(40f, 20f); quadTo(52f, 4f, 72f, 10f); quadTo(70f, 18f, 72f, 26f) }.fill(C.YELLOW)
        for (k in 0..3) line(46f + k * 6f, 19f - k * 0.5f, 50f + k * 6f, 9f + k * 0.3f).stroke(dark).width(0.45f)
        path(closed = true) { moveTo(50f, 60f); quadTo(58f, 72f, 70f, 70f); quadTo(68f, 62f, 70f, 56f) }.fill(C.YELLOW)
        // gövde
        path(closed = true) {
            moveTo(14f, 42f)
            cubicTo(20f, 16f, 66f, 10f, 88f, 36f)
            lineTo(88f, 44f)
            cubicTo(66f, 68f, 22f, 66f, 14f, 42f)
        }.fill(body)
        path(closed = true) { moveTo(18f, 50f); cubicTo(34f, 64f, 66f, 62f, 86f, 44.5f); cubicTo(64f, 56f, 36f, 58f, 18f, 50f) }
            .fill(C.YELLOW_LIGHT).noStroke().alpha(0.7f)
        // pullar
        for (row in 0..3) {
            val y = 29f + row * 6f
            var x = 46f + (row % 2) * 3.5f
            while (x < 82f) {
                val inside = ((x - 52f) / 34f).let { it * it } + ((y + 3f - 40f) / 21f).let { it * it } < 0.8f
                if (inside) arc(x, y, 3.5f, 3.2f, 10f, 160f).stroke(dark).width(0.5f)
                x += 7f
            }
        }
        // şeritler
        path(closed = true) { moveTo(38f, 19f); quadTo(44f, 40f, 38f, 61f); lineTo(33f, 60f); quadTo(38f, 40f, 33f, 20.5f) }.fill(C.WHITE).width(0.6f)
        path { moveTo(26f, 27f); quadTo(31f, 40f, 26f, 55f) }.stroke(dark).width(0.7f)
        // yan yüzgeç
        path(closed = true) { moveTo(46f, 44f); quadTo(54f, 38f, 62f, 44f); quadTo(56f, 52f, 46f, 44f) }.fill(C.YELLOW).width(0.7f)
        line(48f, 44f, 60f, 42f).stroke(dark).width(0.4f)
        line(48f, 44f, 59f, 46.5f).stroke(dark).width(0.4f)
        // göz ve ağız
        circle(22f, 36f, 5.5f).fill(C.WHITE).width(0.7f)
        eye(23f, 36.2f, 3.3f, 3.3f, C.NAVY)
        path { moveTo(14.5f, 45f); quadTo(17f, 46.5f, 19f, 45f) }.width(0.6f)
        shine(40f, 22f, 7f, 2f, -12f, 0.35f)
        // kabarcıklar
        for ((x, y, r) in listOf(Triple(8f, 30f, 2.6f), Triple(5f, 18f, 3.4f), Triple(11f, 7f, 2.2f), Triple(116f, 70f, 1.8f))) {
            circle(x, y, r).fill(C.SKY_LIGHT).width(0.6f)
            circle(x - r * 0.35f, y - r * 0.35f, r * 0.3f).fill(C.WHITE).noStroke()
        }
    }

    private fun bird() = drawing("bird", "Dalda kuş", 100f, 90f, listOf("kuş", "dal", "serçe", "bahar", "bird")) {
        val body = "#4F9BD9"; val belly = "#F7B267"
        // dal
        ribbon(5f, 2.2f, 0f, 74f, 30f, 70f, 60f, 69f, 98f, 62f).fill(C.BROWN)
        ribbon(2.4f, 1.2f, 72f, 67f, 80f, 58f, 86f, 54f).fill(C.BROWN)
        leaf(88f, 50f, 11f, 3.2f, -40f, C.GREEN)
        leaf(80f, 72f, 12f, 3.4f, 30f, C.GREEN)
        leaf(12f, 79f, 12f, 3.4f, 20f, C.GREEN_LIGHT)
        blossom(92f, 60f, 5f, C.PINK, C.YELLOW, 5, 10f, 0.5f)
        blossom(20f, 68f, 4.4f, C.PINK, C.YELLOW, 5, 30f, 0.5f)
        // kuyruk tüyleri
        leaf(76f, 55f, 20f, 4f, 30f, C.BLUE_DARK, vein = false)
        leaf(77f, 51f, 20f, 4f, 18f, body, vein = false)
        // gövde
        smooth(28f, 38f, 32f, 26f, 42f, 22f, 51f, 27f, 62f, 38f, 70f, 48f, 66f, 56f, 55f, 62f, 42f, 61f, 33f, 52f, closed = true).fill(body)
        smooth(33f, 46f, 38f, 42f, 48f, 46f, 54f, 55f, 48f, 60.5f, 40f, 59f, 34f, 53f, closed = true).fill(belly).noStroke()
        // kanat
        path(closed = true) {
            moveTo(44f, 40f); quadTo(58f, 34f, 70f, 50f); quadTo(66f, 51f, 64f, 53f); quadTo(61f, 52f, 58f, 54f); quadTo(54f, 53f, 50f, 53.5f); quadTo(44f, 50f, 44f, 40f)
        }.fill(C.BLUE_DARK).width(0.8f)
        path { moveTo(48f, 44f); quadTo(56f, 42f, 62f, 48f) }.stroke(C.BLUE_LIGHT).width(0.5f)
        // baş tüyü, göz, gaga
        path(closed = true) { moveTo(40f, 23f); quadTo(40f, 17f, 45f, 16f); quadTo(42f, 19f, 44f, 23f) }.fill(body).width(0.6f)
        eye(37f, 32f, 2.6f, 2.8f)
        ellipse(34f, 38f, 2.4f, 1.5f).fill(C.PINK).noStroke().alpha(0.6f)
        path(closed = true) { moveTo(29.5f, 31.5f); lineTo(21f, 35f); lineTo(29f, 37.5f) }.fill(C.YELLOW).width(0.7f)
        line(22.5f, 35f, 29f, 34.6f).width(0.4f)
        // ayaklar
        for (x in listOf(47f, 53f)) {
            line(x, 60f, x - 0.5f, 67f).stroke(C.ORANGE_DARK).width(0.9f)
            path { moveTo(x - 3.5f, 69.5f); quadTo(x - 1.5f, 66f, x - 0.5f, 67f); quadTo(x + 1f, 66f, x + 3f, 69f) }.stroke(C.ORANGE_DARK).width(0.8f)
        }
        // nota
        path(closed = true) { moveTo(18f, 16f); arcTo(16.2f, 16f, 1.8f, 1.4f, 0f, 360f) }.fill(C.BLACK).width(0.4f)
        line(18f, 16f, 18f, 8f).width(0.6f)
        path { moveTo(18f, 8f); quadTo(21f, 9f, 21.5f, 12f) }.width(0.6f)
    }

    private fun VectorBuilder.butterflyWings(main: String, rim: String, spot: String) {
        val upper = floatArrayOf(51f, 42f, 58f, 24f, 72f, 12f, 88f, 9f, 96f, 18f, 93f, 32f, 80f, 42f, 60f, 46f)
        val lower = floatArrayOf(52f, 48f, 66f, 48f, 80f, 54f, 86f, 66f, 80f, 78f, 68f, 80f, 58f, 70f, 52f, 56f)
        smooth(*lower, closed = true).fill(rim)
        group(sx = 0.76f, px = 52f, py = 50f) { smooth(*lower, closed = true).fill(C.YELLOW).width(0f) }
        smooth(*upper, closed = true).fill(rim)
        group(sx = 0.8f, px = 52f, py = 43f) { smooth(*upper, closed = true).fill(main).width(0f) }
        for ((x, y) in listOf(64f to 26f, 76f to 20f, 84f to 26f)) line(53f, 44f, x, y).stroke(rim).width(0.5f)
        for ((x, y) in listOf(66f to 56f, 72f to 66f)) line(53f, 50f, x, y).stroke(rim).width(0.5f)
        for ((x, y, r) in listOf(Triple(88f, 14f, 1.4f), Triple(93f, 22f, 1.2f), Triple(91f, 30f, 1.1f), Triple(82f, 70f, 1.3f), Triple(76f, 77f, 1.2f), Triple(84f, 61f, 1f))) {
            circle(x, y, r).fill(C.WHITE).noStroke()
        }
        circle(76f, 30f, 3.6f).fill(spot).width(0.6f)
        circle(76f, 30f, 1.5f).fill(C.WHITE).noStroke()
        circle(68f, 64f, 2.6f).fill(spot).width(0.6f)
    }

    private fun butterfly() = drawing("butterfly", "Kelebek", 100f, 90f, listOf("kelebek", "böcek", "bahar", "kanat", "butterfly")) {
        path { moveTo(4f, 86f); cubicTo(14f, 70f, 26f, 88f, 30f, 72f) }.width(0.6f).dashed()
        butterflyWings(C.ORANGE, "#3E2F4F", C.PINK)
        group(sx = -1f, sy = 1f, px = 50f, py = 45f) { butterflyWings(C.ORANGE, "#3E2F4F", C.PINK) }
        // gövde
        ellipse(50f, 54f, 3.2f, 15f).fill("#3E2F4F")
        for (k in 0..3) arc(50f, 48f + k * 4.5f, 3f, 1f, 20f, 140f).stroke(C.STONE_DARK).width(0.45f)
        circle(50f, 36f, 4.6f).fill("#3E2F4F")
        circle(48.3f, 35.2f, 1f).fill(C.WHITE).noStroke()
        circle(51.7f, 35.2f, 1f).fill(C.WHITE).noStroke()
        path { moveTo(48.5f, 32f); quadTo(44f, 22f, 38f, 18f) }.width(0.7f)
        path { moveTo(51.5f, 32f); quadTo(56f, 22f, 62f, 18f) }.width(0.7f)
        circle(38f, 18f, 1.5f).fill("#3E2F4F").width(0.5f)
        circle(62f, 18f, 1.5f).fill("#3E2F4F").width(0.5f)
    }

    private fun rabbit() = drawing("rabbit", "Tavşan", 90f, 100f, listOf("tavşan", "havuç", "hayvan", "kulak", "rabbit")) {
        val fur = "#F1ECE6"; val shade = "#D8CFC5"
        shadow(50f, 94f, 34f, 4f, 0.15f)
        // gövde, kuyruk, arka bacak
        puffy(78f, 72f, 6.5f, 6.5f, 7, 0.3f).fill(C.WHITE)
        smooth(34f, 60f, 44f, 54f, 62f, 56f, 76f, 70f, 74f, 88f, 58f, 94f, 40f, 93f, 32f, 80f, closed = true).fill(fur)
        path(closed = true) { moveTo(55f, 90f); quadTo(50f, 72f, 63f, 67f); quadTo(75f, 68f, 74f, 84f); quadTo(72f, 91f, 55f, 90f) }.fill(shade).noStroke().alpha(0.45f)
        path { moveTo(55f, 90f); quadTo(50f, 72f, 63f, 67f); quadTo(72f, 67.5f, 74f, 78f) }.width(0.7f)
        ellipse(62f, 93f, 13f, 3.4f).fill(fur).width(0.8f)
        ellipse(42f, 58f, 5f, 3f).fill(C.WHITE).noStroke().alpha(0.6f)
        // ön patiler + havuç
        group(rotate = -30f, px = 30f, py = 86f) {
            path(closed = true) { moveTo(22f, 83.5f); lineTo(40f, 85f); lineTo(40f, 88f); lineTo(22f, 86.5f) }.width(0f).fill(C.ORANGE)
            path(closed = true) { moveTo(20f, 85f); quadTo(21f, 82.5f, 26f, 83f); lineTo(40f, 85.2f); quadTo(42f, 86.5f, 40f, 87.8f); lineTo(26f, 88f); quadTo(21f, 88f, 20f, 85f) }
                .fill(C.ORANGE).width(0.8f)
            for (x in listOf(27f, 31f, 35f)) line(x, 83.8f, x + 0.5f, 85.6f).stroke(C.ORANGE_DARK).width(0.45f)
            leaf(16f, 83f, 8f, 1.8f, 190f, C.GREEN, vein = false)
            leaf(16f, 87f, 8f, 1.8f, 165f, C.GREEN, vein = false)
        }
        ellipse(38f, 86f, 4.5f, 3.4f).fill(fur).width(0.8f)
        ellipse(44f, 88f, 4.5f, 3.4f).fill(fur).width(0.8f)
        // kulaklar
        ellipse(44f, 22f, 5.5f, 16f, 14f).fill(fur)
        ellipse(44f, 23f, 2.8f, 12f, 14f).fill(C.PINK).width(0.5f)
        ellipse(31f, 22f, 5.5f, 16.5f, -12f).fill(fur)
        ellipse(31f, 23f, 2.8f, 12.5f, -12f).fill(C.PINK).width(0.5f)
        // baş
        smooth(22f, 50f, 22f, 40f, 30f, 34f, 42f, 34f, 50f, 42f, 50f, 52f, 42f, 60f, 30f, 60f, closed = true).fill(fur)
        eye(31.5f, 46f, 2.8f, 3.3f)
        eye(43.5f, 46f, 2.8f, 3.3f)
        ellipse(27.5f, 52f, 2.8f, 1.7f).fill(C.PINK).noStroke().alpha(0.6f)
        ellipse(47.5f, 52f, 2.8f, 1.7f).fill(C.PINK).noStroke().alpha(0.6f)
        path(closed = true) { moveTo(35.5f, 50.5f); lineTo(39.5f, 50.5f); quadTo(38.5f, 53f, 37.5f, 53f); quadTo(36.5f, 53f, 35.5f, 50.5f) }.fill(C.PINK_DARK).width(0.5f)
        path { moveTo(34.5f, 55f); quadTo(36f, 56.5f, 37.5f, 54f); quadTo(39f, 56.5f, 40.5f, 55f) }.width(0.5f)
        roundRect(36.2f, 55.3f, 2.6f, 2.2f, 0.6f).fill(C.WHITE).width(0.4f)
        whiskers(33.5f, 53.5f, -1f, 10f)
        whiskers(41.5f, 53.5f, 1f, 10f)
        tuft(12f, 94f, 7f, 6f)
        tuft(84f, 95f, 6f, 5f)
    }

    private fun owl() = drawing("owl", "Baykuş", 80f, 100f, listOf("baykuş", "gece", "dal", "kuş", "owl")) {
        val body = "#9C6B4E"; val wing = "#7A4E36"; val belly = "#E8C9A0"; val face = "#F4E1C6"
        path(closed = true) { moveTo(71f, 6f); arcTo(66f, 12f, 8f, 8f, -50f, -260f); arcTo(71.5f, 12.5f, 7f, 7f, 150f, 150f) }.fill(C.YELLOW_LIGHT).width(0.7f)
        star(12f, 10f, 2.4f, 1f).fill(C.YELLOW).width(0.5f)
        star(22f, 22f, 1.6f, 0.7f).fill(C.YELLOW).width(0.5f)
        // dal
        ribbon(5f, 2.4f, 0f, 88f, 30f, 86f, 60f, 87f, 80f, 83f).fill(C.BROWN)
        leaf(8f, 82f, 11f, 3f, -30f, C.GREEN)
        leaf(72f, 78f, 11f, 3f, -150f, C.GREEN)
        // gövde
        path(closed = true) { moveTo(22f, 30f); lineTo(19f, 14f); lineTo(34f, 25f) }.fill(body)
        path(closed = true) { moveTo(58f, 30f); lineTo(61f, 14f); lineTo(46f, 25f) }.fill(body)
        smooth(19f, 50f, 22f, 30f, 40f, 22f, 58f, 30f, 61f, 50f, 57f, 72f, 40f, 84f, 23f, 72f, closed = true).fill(body)
        smooth(29f, 62f, 32f, 52f, 40f, 49f, 48f, 52f, 51f, 62f, 48f, 76f, 40f, 81f, 32f, 76f, closed = true).fill(belly).noStroke()
        for (row in 0..3) {
            val y = 58f + row * 5.5f
            val xs = if (row % 2 == 0) listOf(35f, 40f, 45f) else listOf(37.5f, 42.5f)
            for (x in xs) path { moveTo(x - 1.6f, y); lineTo(x, y + 1.6f); lineTo(x + 1.6f, y) }.stroke(body).width(0.5f)
        }
        // kanatlar
        path(closed = true) { moveTo(22f, 44f); quadTo(14f, 58f, 22f, 76f); quadTo(26f, 70f, 29f, 64f); quadTo(28f, 52f, 22f, 44f) }.fill(wing).width(0.8f)
        path(closed = true) { moveTo(58f, 44f); quadTo(66f, 58f, 58f, 76f); quadTo(54f, 70f, 51f, 64f); quadTo(52f, 52f, 58f, 44f) }.fill(wing).width(0.8f)
        for (k in 0..2) {
            arc(22.5f, 58f + k * 5f, 3f, 2f, 20f, 140f).stroke(belly).width(0.45f)
            arc(57.5f, 58f + k * 5f, 3f, 2f, 20f, 140f).stroke(belly).width(0.45f)
        }
        // yüz
        smooth(22f, 40f, 26f, 31f, 34f, 30f, 40f, 34f, 46f, 30f, 54f, 31f, 58f, 40f, 54f, 49f, 46f, 51f, 40f, 48f, 34f, 51f, 26f, 49f, closed = true).fill(face).width(0.7f)
        circle(32f, 40f, 7f).fill(C.WHITE).width(0.7f)
        circle(48f, 40f, 7f).fill(C.WHITE).width(0.7f)
        circle(32.5f, 40.5f, 4.8f).fill(C.ORANGE).width(0.5f)
        circle(47.5f, 40.5f, 4.8f).fill(C.ORANGE).width(0.5f)
        eye(32.5f, 40.5f, 2.9f, 2.9f)
        eye(47.5f, 40.5f, 2.9f, 2.9f)
        path(closed = true) { moveTo(37.5f, 45f); lineTo(42.5f, 45f); lineTo(40f, 50f) }.fill(C.ORANGE_DARK).width(0.6f)
        path { moveTo(26f, 31.5f); quadTo(32f, 29f, 37f, 32.5f) }.stroke(wing).width(0.8f)
        path { moveTo(54f, 31.5f); quadTo(48f, 29f, 43f, 32.5f) }.stroke(wing).width(0.8f)
        // pençeler
        for (x in listOf(34f, 46f)) for (d in listOf(-2.4f, 0f, 2.4f)) ellipse(x + d, 84.5f, 1.3f, 2f).fill(C.ORANGE).width(0.5f)
    }

    private fun bee() = drawing("bee", "Arı", 100f, 90f, listOf("arı", "bal", "böcek", "çiçek", "bee")) {
        // uçuş izi ve çiçek
        path { moveTo(4f, 30f); cubicTo(14f, 10f, 26f, 44f, 18f, 44f); cubicTo(10f, 44f, 14f, 30f, 26f, 36f) }.width(0.6f).dashed()
        stem(C.GREEN_DARK, 1.4f, 84f, 90f, 86f, 80f, 84f, 70f)
        leaf(80f, 82f, 9f, 2.6f, 210f, C.GREEN)
        blossom(84f, 68f, 9f, C.PINK, C.YELLOW, 6, 0f, 0.6f)
        // kanatlar (arkada)
        ellipse(46f, 26f, 9f, 16f, -25f).fill("#DDF1FB").width(0.8f).alpha(0.9f)
        ellipse(60f, 28f, 8f, 14f, 20f).fill("#DDF1FB").width(0.8f).alpha(0.9f)
        path { moveTo(48f, 40f); quadTo(46f, 28f, 42f, 16f) }.stroke(C.BLUE_LIGHT).width(0.45f)
        path { moveTo(58f, 40f); quadTo(60f, 30f, 64f, 20f) }.stroke(C.BLUE_LIGHT).width(0.45f)
        // gövde ve çizgiler
        path(closed = true) { moveTo(76f, 49f); lineTo(84f, 52f); lineTo(76f, 55f) }.fill(C.BLACK).width(0.6f)
        ellipse(54f, 52f, 23f, 16f).fill(C.YELLOW)
        for ((x0, x1) in listOf(45f to 51f, 57f to 63f, 68.5f to 73.5f)) ellipseBand(54f, 52f, 23f, 16f, x0, x1).fill(C.BLACK).noStroke()
        arc(54f, 52f, 23f, 16f, 0f, 360f)
        shine(50f, 42f, 7f, 2.4f, -8f, 0.4f)
        for (x in listOf(48f, 56f, 64f)) line(x, 67f, x - 1.5f, 72f).width(0.7f)
        // baş
        circle(30f, 50f, 13f).fill("#F9D65C")
        path { moveTo(26f, 38f); quadTo(22f, 28f, 16f, 26f) }.width(0.7f)
        path { moveTo(33f, 38f); quadTo(34f, 28f, 30f, 22f) }.width(0.7f)
        circle(16f, 26f, 2f).fill(C.BLACK).width(0.5f)
        circle(30f, 22f, 2f).fill(C.BLACK).width(0.5f)
        eye(25f, 48f, 2.6f, 3.4f)
        eye(34f, 48f, 2.6f, 3.4f)
        ellipse(22f, 54f, 2.6f, 1.6f).fill(C.PINK).noStroke().alpha(0.7f)
        ellipse(37f, 54f, 2.6f, 1.6f).fill(C.PINK).noStroke().alpha(0.7f)
        path { moveTo(26f, 55f); quadTo(29.5f, 58.5f, 33f, 55f) }.width(0.6f)
    }

    private fun turtle() = drawing("turtle", "Kaplumbağa", 110f, 80f, listOf("kaplumbağa", "kabuk", "hayvan", "yavaş", "turtle")) {
        val skin = "#9ED36A"; val skinDark = "#7DB84F"; val shell = "#4E9A57"; val plate = "#7CC36C"
        shadow(56f, 72f, 44f, 4f, 0.16f)
        // arka bacaklar ve kuyruk
        roundRect(30f, 58f, 11f, 13f, 5f).fill(skinDark)
        roundRect(74f, 58f, 11f, 13f, 5f).fill(skinDark)
        path(closed = true) { moveTo(88f, 56f); quadTo(96f, 58f, 99f, 62f); quadTo(94f, 62f, 88f, 61f) }.fill(skin)
        // baş ve boyun
        path(closed = true) { moveTo(30f, 50f); quadTo(22f, 50f, 20f, 44f); lineTo(28f, 40f); quadTo(32f, 44f, 36f, 46f) }.fill(skin)
        smooth(8f, 40f, 11f, 30f, 20f, 27f, 28f, 32f, 29f, 42f, 22f, 48f, 12f, 48f, closed = true).fill(skin)
        eye(17f, 35.5f, 2.4f, 2.8f)
        path { moveTo(10f, 42.5f); quadTo(15f, 45.5f, 20f, 42.5f) }.width(0.6f)
        ellipse(22f, 41f, 2.4f, 1.4f).fill(C.PINK).noStroke().alpha(0.6f)
        circle(9.5f, 37f, 0.5f).fill(C.BLACK).noStroke()
        for ((x, y) in listOf(22f to 31f, 25f to 34f, 13f to 30.5f)) circle(x, y, 0.9f).fill(skinDark).noStroke()
        // ön bacaklar
        roundRect(38f, 58f, 11f, 15f, 5f).fill(skin)
        roundRect(64f, 58f, 11f, 15f, 5f).fill(skin)
        for (x in listOf(41f, 43.5f, 46f, 67f, 69.5f, 72f)) line(x, 71f, x, 73f).width(0.45f)
        // kabuk
        path(closed = true) { moveTo(26f, 58f); cubicTo(26f, 16f, 90f, 16f, 90f, 58f) }.fill(shell)
        path(closed = true) { moveTo(46f, 36f); lineTo(54f, 30f); lineTo(62f, 30f); lineTo(70f, 36f); lineTo(66f, 46f); lineTo(50f, 46f) }.fill(plate).width(0.7f)
        path(closed = true) { moveTo(31f, 50f); quadTo(34f, 36f, 44f, 30f); lineTo(46f, 36f); lineTo(50f, 46f); lineTo(46f, 54f); lineTo(32f, 54f) }.fill(plate).width(0.7f)
        path(closed = true) { moveTo(85f, 50f); quadTo(82f, 36f, 72f, 30f); lineTo(70f, 36f); lineTo(66f, 46f); lineTo(70f, 54f); lineTo(84f, 54f) }.fill(plate).width(0.7f)
        path(closed = true) { moveTo(50f, 46f); lineTo(66f, 46f); lineTo(70f, 54f); lineTo(46f, 54f) }.fill(plate).width(0.7f)
        path(closed = true) { moveTo(47f, 28f); quadTo(58f, 22f, 69f, 28f); lineTo(62f, 30f); lineTo(54f, 30f) }.fill(plate).width(0.7f)
        shine(44f, 30f, 6f, 2.2f, -30f, 0.35f)
        roundRect(23f, 54f, 70f, 7f, 3.5f).fill("#D8B26E")
        for (k in 1..6) line(23f + k * 10f, 54f, 23f + k * 10f, 61f).stroke(C.BROWN).width(0.5f)
        tuft(8f, 74f, 6f, 5f)
        tuft(102f, 74f, 6f, 5f)
    }
}
