package com.karapp.drawguidance.content.library.drawings

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** "Doğa" hazır çizimleri. */
internal object NatureDrawings {

    val shapes: List<LibShape> by lazy {
        listOf(oak(), pine(), flowerPot(), sunflower(), mushroom(), cactus(), mountains(), rainbow(), lake())
    }

    /** Testere dişi alt kenarlı çam katmanı. */
    private fun VectorBuilder.pineTier(cx: Float, top: Float, bottom: Float, hw: Float, fill: String, teeth: Int = 5) {
        path(closed = true) {
            moveTo(cx, top)
            lineTo(cx + hw, bottom)
            val step = 2f * hw / teeth
            for (i in 0 until teeth) {
                val x0 = cx + hw - step * i
                quadTo(x0 - step * 0.5f, bottom - step * 0.55f, x0 - step, bottom)
            }
        }.fill(fill).width(0.9f)
        poly(cx, top + 0.5f, cx + hw - 1f, bottom - 1.2f, cx, bottom - 3f, closed = true).fill(C.SHADOW).noStroke().alpha(0.14f)
    }

    private fun VectorBuilder.smallPine(cx: Float, base: Float, h: Float) {
        rect(cx - h * 0.05f, base - h * 0.18f, h * 0.1f, h * 0.18f).fill(C.BROWN).width(0.6f)
        pineTier(cx, base - h * 0.62f, base - h * 0.14f, h * 0.3f, C.GREEN_DARK, 3)
        pineTier(cx, base - h, base - h * 0.45f, h * 0.22f, C.GREEN_DARK, 3)
    }

    private fun oak() = drawing("oak_tree", "Meşe ağacı", 100f, 100f, listOf("ağaç", "meşe", "orman", "yaprak", "tree")) {
        shadow(50f, 93f, 38f, 5f, 0.14f)
        ellipse(50f, 92f, 44f, 6.5f).fill(C.GREEN_LIGHT)
        // gövde ve dallar
        path(closed = true) {
            moveTo(35f, 93f)
            quadTo(43f, 91f, 44f, 83f)
            lineTo(45f, 66f)
            quadTo(40f, 58f, 27f, 53f)
            lineTo(29f, 49.5f)
            quadTo(41f, 53f, 47f, 59f)
            lineTo(47.5f, 44f)
            lineTo(52.5f, 44f)
            lineTo(53f, 57f)
            quadTo(61f, 50f, 73f, 48f)
            lineTo(74f, 51.5f)
            quadTo(62f, 56f, 56f, 65f)
            lineTo(56.5f, 83f)
            quadTo(57f, 91f, 66f, 93f)
        }.fill(C.BROWN)
        poly(52f, 60f, 56f, 65f, 56.5f, 83f, 58f, 90f, 52f, 90f, closed = true).fill(C.SHADOW).noStroke().alpha(0.15f)
        path { moveTo(48f, 88f); quadTo(46.5f, 78f, 48.5f, 68f) }.stroke(C.BROWN_DARK).width(0.5f)
        path { moveTo(52.5f, 84f); quadTo(54f, 76f, 52f, 70f) }.stroke(C.BROWN_DARK).width(0.5f)
        path { moveTo(49.5f, 64f); quadTo(50.5f, 58f, 49.5f, 52f) }.stroke(C.BROWN_DARK).width(0.5f)
        ellipse(50.5f, 76f, 1.8f, 2.8f).fill(C.BROWN_DARK).width(0.5f)
        path { moveTo(40f, 92f); quadTo(38f, 94f, 33f, 95f) }.stroke(C.BROWN_DARK).width(0.6f)
        path { moveTo(60f, 92f); quadTo(63f, 94.5f, 68f, 95f) }.stroke(C.BROWN_DARK).width(0.6f)
        // taç: koyu arka, orta, açık ön kümeler
        puffy(28f, 36f, 18f, 14f, 7, 0.3f).fill(C.GREEN_DARK)
        puffy(72f, 35f, 18f, 14f, 7, 0.3f, -60f).fill(C.GREEN_DARK)
        puffy(50f, 23f, 24f, 17f, 8, 0.3f).fill(C.GREEN_DARK)
        puffy(36f, 31f, 15f, 12f, 7, 0.3f, -70f).fill(C.GREEN)
        puffy(63f, 29f, 16f, 12f, 7, 0.3f).fill(C.GREEN)
        puffy(49f, 17f, 15f, 10f, 6, 0.3f).fill(C.GREEN)
        puffy(50f, 38f, 13f, 8f, 6, 0.3f, -80f).fill(C.GREEN)
        puffy(42f, 23f, 7f, 5f, 5, 0.3f).fill(C.GREEN_LIGHT).noStroke()
        puffy(62f, 22f, 6f, 4.5f, 5, 0.3f).fill(C.GREEN_LIGHT).noStroke()
        puffy(30f, 32f, 5f, 4f, 5, 0.3f).fill(C.GREEN_LIGHT).noStroke()
        for ((x, y) in listOf(24f to 42f, 34f to 20f, 58f to 36f, 76f to 30f, 46f to 30f, 68f to 42f, 55f to 12f, 20f to 30f, 80f to 40f)) {
            arc(x, y, 2.2f, 1.6f, 20f, 140f).stroke(C.GREEN_DEEP).width(0.5f)
        }
        // palamutlar
        for ((x, y) in listOf(33f to 46f, 64f to 45f, 44f to 41f)) {
            line(x, y - 3f, x, y - 1.2f).width(0.5f)
            ellipse(x, y + 1.6f, 1.6f, 2.2f).fill(C.WOOD).width(0.5f)
            path(closed = true) { moveTo(x - 2f, y); arcTo(x, y, 2f, 1.7f, 180f, 180f) }.fill(C.BROWN_DARK).width(0.5f)
        }
        tuft(20f, 93f, 7f, 6f)
        tuft(78f, 93f, 7f, 6f)
        tuft(62f, 97f, 5f, 4f)
        blossom(28f, 95f, 2.6f, C.PINK, C.YELLOW, 5, 0f, 0.45f)
        blossom(73f, 97f, 2.4f, C.YELLOW, C.ORANGE, 5, 20f, 0.45f)
    }

    private fun pine() = drawing("pine_tree", "Çam ağacı", 80f, 100f, listOf("çam", "ağaç", "orman", "kozalak", "pine")) {
        shadow(40f, 94f, 30f, 4f, 0.14f)
        ellipse(40f, 93f, 36f, 5.5f).fill(C.GREEN_LIGHT)
        rect(35.5f, 78f, 9f, 16f).fill(C.BROWN)
        rect(40f, 78f, 4.5f, 16f).fill(C.SHADOW).noStroke().alpha(0.15f)
        line(38f, 82f, 38.5f, 91f).stroke(C.BROWN_DARK).width(0.5f)
        line(41.5f, 85f, 41.5f, 92f).stroke(C.BROWN_DARK).width(0.5f)
        group(dx = 27f, sx = 0.45f, px = 40f, py = 92f) {
            rect(36f, 76f, 8f, 16f).fill(C.BROWN)
            pineTier(40f, 42f, 82f, 31f, C.GREEN, 6)
            pineTier(40f, 22f, 62f, 23f, C.GREEN, 5)
            pineTier(40f, 5f, 42f, 15f, C.GREEN, 4)
        }
        pineTier(40f, 42f, 82f, 31f, C.GREEN_DARK, 7)
        pineTier(40f, 27f, 63f, 24f, C.GREEN_DARK, 6)
        pineTier(40f, 13f, 45f, 17f, C.GREEN_DARK, 5)
        pineTier(40f, 2f, 27f, 10f, C.GREEN_DARK, 4)
        for ((x, y) in listOf(28f to 72f, 50f to 70f, 34f to 55f, 47f to 52f, 38f to 36f, 44f to 22f, 22f to 76f, 58f to 76f)) {
            path { moveTo(x - 3f, y + 2f); lineTo(x, y); lineTo(x + 3f, y + 2f) }.stroke(C.GREEN_DEEP).width(0.5f)
        }
        // kozalaklar
        for ((x, y) in listOf(24f to 80f, 55f to 62f, 30f to 60f)) {
            line(x, y - 2.5f, x, y - 1.5f).width(0.5f)
            ellipse(x, y + 1.5f, 2f, 3f).fill(C.BROWN_LIGHT).width(0.6f)
            line(x - 1.7f, y + 0.6f, x + 1.7f, y + 0.6f).stroke(C.BROWN_DARK).width(0.4f)
            line(x - 1.9f, y + 2.2f, x + 1.9f, y + 2.2f).stroke(C.BROWN_DARK).width(0.4f)
        }
        ellipse(12f, 94f, 4f, 1.5f).fill(C.BROWN_LIGHT).width(0.5f)
        ellipse(66f, 95f, 3f, 1.2f).fill(C.BROWN_LIGHT).width(0.5f)
        tuft(16f, 93f, 6f, 5f)
        tuft(32f, 97f, 5f, 4f)
        tuft(52f, 96f, 5f, 4.5f)
    }

    private fun flowerPot() = drawing("flower_pot", "Saksıda çiçekler", 80f, 100f, listOf("saksı", "çiçek", "lale", "papatya", "flower")) {
        shadow(40f, 96f, 26f, 3f, 0.14f)
        // saplar ve yapraklar
        stem(C.GREEN_DARK, 1.5f, 38f, 60f, 36f, 44f, 27f, 26f)
        stem(C.GREEN_DARK, 1.5f, 42f, 60f, 46f, 44f, 54f, 30f)
        stem(C.GREEN_DARK, 1.5f, 40f, 60f, 41f, 48f, 40f, 38f)
        leaf(30f, 48f, 15f, 4.5f, -35f, C.GREEN)
        leaf(51f, 46f, 15f, 4.5f, 215f, C.GREEN)
        leaf(46f, 54f, 10f, 3f, 20f, C.GREEN_LIGHT)
        leaf(34f, 55f, 9f, 2.8f, 160f, C.GREEN_LIGHT)
        // lale
        path(closed = true) {
            moveTo(21f, 20f); quadTo(20f, 30f, 27f, 31f); quadTo(34f, 30f, 33f, 20f)
            lineTo(30f, 23f); lineTo(27f, 17f); lineTo(24f, 23f)
        }.fill(C.RED)
        path(closed = true) { moveTo(24f, 23f); quadTo(25f, 28f, 27f, 30f); quadTo(29f, 28f, 30f, 23f); lineTo(27f, 17f) }
            .fill(C.RED_DARK).width(0.6f)
        shine(23.5f, 25f, 0.9f, 2.5f, 10f, 0.4f)
        // papatya
        for (i in 0 until 12) {
            val a = 360f * i / 12
            val r = Math.toRadians(a.toDouble())
            ellipse(54f + 6f * cos(r).toFloat(), 26f + 6f * sin(r).toFloat(), 4f, 1.7f, a).fill(C.WHITE).width(0.55f)
        }
        circle(54f, 26f, 3.6f).fill(C.YELLOW).width(0.6f)
        circle(53f, 25f, 1f).fill(C.WHITE).noStroke().alpha(0.5f)
        // mor çiçek ve tomurcuk
        blossom(40f, 35f, 7f, C.PURPLE, C.YELLOW_LIGHT, 5, 10f, 0.6f)
        path(closed = true) { moveTo(40f, 45f); quadTo(37f, 42f, 40f, 38f); quadTo(43f, 42f, 40f, 45f) }.fill(C.PINK).width(0.5f)
        // saksı
        ellipse(40f, 94f, 22f, 3f).fill(C.RED_DARK)
        poly(19f, 64f, 61f, 64f, 55f, 93f, 25f, 93f, closed = true).fill("#DC7550")
        poly(40f, 64f, 61f, 64f, 55f, 93f, 40f, 93f, closed = true).fill(C.SHADOW).noStroke().alpha(0.1f)
        path {
            moveTo(22f, 76f)
            var x = 22f
            var up = true
            while (x < 58f) { x += 3f; lineTo(x, if (up) 73f else 76f); up = !up }
        }.stroke(C.CREAM).width(0.7f)
        for (k in 0 until 6) circle(27f + k * 5.2f, 83f, 1f).fill(C.CREAM).noStroke()
        roundRect(16f, 58f, 48f, 8f, 2f).fill(C.BRICK)
        ellipse(40f, 58.4f, 22f, 2.4f).fill(C.BROWN_DARK).width(0.6f)
        shine(22f, 61f, 3f, 1f, 0f, 0.35f)
    }

    private fun sunflower() = drawing("sunflower", "Ayçiçeği", 80f, 100f, listOf("ayçiçeği", "çiçek", "yaz", "güneş", "sunflower")) {
        shadow(40f, 95f, 24f, 3f, 0.14f)
        ellipse(40f, 94f, 26f, 4f).fill(C.GREEN_LIGHT)
        stem(C.GREEN_DARK, 3f, 40f, 94f, 42f, 70f, 39f, 46f, 40f, 34f)
        // büyük yapraklar
        group(rotate = -25f, px = 40f, py = 68f) {
            path(closed = true) { moveTo(40f, 68f); quadTo(26f, 56f, 14f, 66f); quadTo(26f, 78f, 40f, 68f) }.fill(C.GREEN)
            path { moveTo(39f, 68f); quadTo(28f, 65f, 17f, 66f) }.stroke(C.GREEN_DEEP).width(0.5f)
            for (k in 0..2) line(34f - k * 6f, 66.6f, 31f - k * 6f, 62.5f).stroke(C.GREEN_DEEP).width(0.45f)
            for (k in 0..2) line(34f - k * 6f, 66.9f, 31f - k * 6f, 71f).stroke(C.GREEN_DEEP).width(0.45f)
        }
        group(rotate = 20f, px = 41f, py = 58f) {
            path(closed = true) { moveTo(41f, 58f); quadTo(55f, 46f, 67f, 56f); quadTo(55f, 68f, 41f, 58f) }.fill(C.GREEN)
            path { moveTo(42f, 58f); quadTo(53f, 55f, 64f, 56f) }.stroke(C.GREEN_DEEP).width(0.5f)
            for (k in 0..2) line(47f + k * 6f, 56.8f, 50f + k * 6f, 52.5f).stroke(C.GREEN_DEEP).width(0.45f)
            for (k in 0..2) line(47f + k * 6f, 57.1f, 50f + k * 6f, 61f).stroke(C.GREEN_DEEP).width(0.45f)
        }
        // uğur böceği
        ellipse(22f, 67f, 2.6f, 2.2f, -20f).fill(C.RED).width(0.5f)
        circle(20.2f, 66f, 1.2f).fill(C.BLACK).noStroke()
        circle(22.5f, 66f, 0.5f).fill(C.BLACK).noStroke()
        circle(22.8f, 68.2f, 0.5f).fill(C.BLACK).noStroke()
        // taç yapraklar (arka turuncu, ön sarı)
        val cx = 40f; val cy = 28f
        for (i in 0 until 16) {
            val a = 360f * i / 16 + 11.25f
            val r = Math.toRadians(a.toDouble())
            ellipse(cx + 17f * cos(r).toFloat(), cy + 17f * sin(r).toFloat(), 8f, 3.4f, a).fill(C.ORANGE).width(0.6f)
        }
        for (i in 0 until 16) {
            val a = 360f * i / 16
            val r = Math.toRadians(a.toDouble())
            ellipse(cx + 15f * cos(r).toFloat(), cy + 15f * sin(r).toFloat(), 7.5f, 3.2f, a).fill(C.YELLOW).width(0.6f)
        }
        circle(cx, cy, 11f).fill(C.BROWN)
        circle(cx, cy, 7.5f).fill(C.BROWN_DARK).width(0.5f)
        val golden = 137.508
        for (i in 1 until 70) {
            val r = 0.98f * sqrt(i.toFloat()) * 1.2f
            if (r > 10.2f) break
            val a = Math.toRadians(i * golden)
            circle(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), 0.45f).fill(C.WOOD).noStroke()
        }
        arc(cx, cy, 9.5f, 9.5f, 200f, 60f).stroke(C.WHITE).width(0.5f).alpha(0.5f)
    }

    private fun mushroom() = drawing("mushroom", "Mantarlar", 100f, 90f, listOf("mantar", "orman", "sonbahar", "salyangoz", "mushroom")) {
        shadow(52f, 84f, 42f, 4.5f, 0.14f)
        ellipse(50f, 84f, 46f, 5.5f).fill(C.GREEN_LIGHT)
        // büyük mantar
        path(closed = true) {
            moveTo(37f, 84f); quadTo(41f, 72f, 40f, 56f); lineTo(54f, 56f); quadTo(53f, 72f, 58f, 84f)
        }.fill(C.CREAM)
        path(closed = true) { moveTo(49f, 56f); lineTo(54f, 56f); quadTo(53f, 72f, 58f, 84f); lineTo(52f, 84f); quadTo(49f, 70f, 49f, 56f) }
            .fill(C.SHADOW).noStroke().alpha(0.08f)
        path(closed = true) {
            moveTo(38.5f, 63f); quadTo(47f, 66f, 55.5f, 63f); lineTo(57f, 67f)
            quadTo(55f, 66f, 53f, 68f); quadTo(51f, 66f, 49f, 68.5f); quadTo(47f, 66f, 45f, 68.5f); quadTo(43f, 66f, 41f, 68f); quadTo(39f, 66f, 37f, 67f)
        }.fill(C.WHITE).width(0.6f)
        ellipse(47f, 53f, 27f, 6f).fill("#F0D9B5")
        for (k in 0..12) {
            val x = 24f + k * 3.8f
            line(x, 54.5f - (if (k == 0 || k == 12) 1.5f else 0f), 47f + (x - 47f) * 0.35f, 56f).stroke(C.WALL_DARK).width(0.45f)
        }
        path(closed = true) { moveTo(19f, 53f); cubicTo(18f, 22f, 76f, 22f, 75f, 53f); quadTo(47f, 60f, 19f, 53f) }.fill(C.RED)
        path(closed = true) { moveTo(60f, 30f); cubicTo(72f, 35f, 76f, 44f, 75f, 53f); quadTo(68f, 55.5f, 60f, 56f); quadTo(66f, 44f, 60f, 30f) }
            .fill(C.SHADOW).noStroke().alpha(0.1f)
        for ((x, y, r) in listOf(Triple(35f, 34f, 4f), Triple(51f, 29f, 3.2f), Triple(63f, 38f, 3.6f), Triple(28f, 46f, 2.6f), Triple(45f, 44f, 3f), Triple(69f, 48f, 2f))) {
            ellipse(x, y, r, r * 0.8f).fill(C.WHITE).width(0.55f)
        }
        shine(30f, 38f, 3f, 1.2f, -40f, 0.3f)
        // küçük mantar
        path(closed = true) { moveTo(72f, 84f); quadTo(74f, 78f, 73.5f, 71f); lineTo(80.5f, 71f); quadTo(80f, 78f, 82f, 84f) }.fill(C.CREAM)
        path(closed = true) { moveTo(66f, 71.5f); cubicTo(66f, 58f, 88f, 58f, 88f, 71.5f); quadTo(77f, 74f, 66f, 71.5f) }.fill(C.ORANGE)
        ellipse(72f, 65f, 1.8f, 1.4f).fill(C.CREAM).width(0.5f)
        ellipse(80f, 63f, 1.5f, 1.2f).fill(C.CREAM).width(0.5f)
        ellipse(83f, 68f, 1.3f, 1f).fill(C.CREAM).width(0.5f)
        // salyangoz
        path(closed = true) {
            moveTo(6f, 84f); quadTo(6f, 80f, 10f, 80f); lineTo(24f, 80.5f); quadTo(27f, 81f, 28f, 84f)
        }.fill("#C9B6A0").width(0.7f)
        line(10f, 80.5f, 8f, 75f).width(0.5f)
        line(11.5f, 80.5f, 11f, 75f).width(0.5f)
        circle(8f, 74.6f, 0.8f).fill(C.BLACK).noStroke()
        circle(11f, 74.6f, 0.8f).fill(C.BLACK).noStroke()
        circle(20f, 75.5f, 6f).fill(C.ORANGE)
        path {
            moveTo(20f, 75.5f)
            for (i in 1..40) {
                val t = i / 40f
                val a = t * 4.4 * Math.PI
                val r = 5f * t
                lineTo(20f + r * cos(a).toFloat(), 75.5f + r * sin(a).toFloat())
            }
        }.stroke(C.BROWN_DARK).width(0.55f)
        // yapraklar ve çimen
        leaf(32f, 86f, 9f, 2.8f, -10f, C.ORANGE, veinColor = C.BROWN)
        leaf(88f, 85f, 8f, 2.6f, 200f, C.YELLOW, veinColor = C.BROWN)
        tuft(64f, 86f, 6f, 5f)
        tuft(94f, 84f, 5f, 4.5f)
        tuft(40f, 88f, 5f, 3.5f)
    }

    private fun cactus() = drawing("cactus", "Kaktüs", 70f, 100f, listOf("kaktüs", "saksı", "çöl", "bitki", "cactus")) {
        shadow(35f, 96f, 22f, 3f, 0.14f)
        // kollar (gövdenin arkasında)
        path(closed = true) {
            moveTo(27f, 58f); lineTo(18f, 58f); quadTo(10f, 58f, 10f, 50f); lineTo(10f, 38f)
            arcTo(14.5f, 38f, 4.5f, 4.5f, 180f, 180f); lineTo(19f, 49f); lineTo(27f, 49f)
        }.fill(C.GREEN)
        path(closed = true) {
            moveTo(43f, 46f); lineTo(51f, 46f); quadTo(58f, 46f, 58f, 39f); lineTo(58f, 30f)
            arcTo(54f, 30f, 4f, 4f, 0f, -180f); lineTo(50f, 38f); lineTo(43f, 38f)
        }.fill(C.GREEN)
        line(14.5f, 36f, 14.5f, 50f).stroke(C.GREEN_DARK).width(0.5f)
        line(54f, 28f, 54f, 40f).stroke(C.GREEN_DARK).width(0.5f)
        // gövde
        roundRect(24f, 16f, 22f, 56f, 11f).fill(C.GREEN)
        roundRect(38f, 20f, 6f, 50f, 3f).fill(C.SHADOW).noStroke().alpha(0.1f)
        roundRect(27f, 22f, 3f, 30f, 1.5f).fill(C.WHITE).noStroke().alpha(0.3f)
        for (x in listOf(30f, 35f, 40f)) line(x, if (x == 35f) 17f else 19f, x, 70f).stroke(C.GREEN_DARK).width(0.5f)
        // dikenler
        for (x in listOf(27.5f, 32.5f, 37.5f, 42.5f)) {
            var y = 24f + (if (x == 32.5f || x == 42.5f) 4f else 0f)
            while (y < 68f) {
                line(x, y, x - 1.2f, y - 1.4f).width(0.4f)
                line(x, y, x + 1.2f, y - 1.4f).width(0.4f)
                y += 8f
            }
        }
        for ((x, y) in listOf(12f to 44f, 17f to 40f, 56f to 34f, 60f to 38f, 13f to 52f)) {
            line(x, y, x - 1.2f, y - 1.4f).width(0.4f)
            line(x, y, x + 1.2f, y - 1.4f).width(0.4f)
        }
        // çiçek
        blossom(35f, 15.5f, 7f, C.PINK, C.YELLOW, 6, 0f, 0.55f)
        circle(34f, 15f, 0.8f).fill(C.WHITE).noStroke().alpha(0.6f)
        blossom(14.5f, 33f, 3.6f, C.PINK_DARK, C.YELLOW, 5, 0f, 0.5f)
        // saksı
        poly(16f, 72f, 54f, 72f, 50f, 96f, 20f, 96f, closed = true).fill("#DC7550")
        poly(35f, 72f, 54f, 72f, 50f, 96f, 35f, 96f, closed = true).fill(C.SHADOW).noStroke().alpha(0.1f)
        for (k in 0 until 6) {
            val x = 20f + k * 5.2f
            poly(x, 88f, x + 2.6f, 83f, x + 5.2f, 88f, closed = true).fill(C.YELLOW_LIGHT).width(0.5f)
        }
        line(19f, 88f, 51f, 88f).stroke(C.CREAM).width(0.6f)
        roundRect(13f, 67f, 44f, 7f, 2f).fill(C.BRICK)
        ellipse(35f, 67.4f, 20f, 2f).fill(C.BROWN_DARK).width(0.6f)
        ellipse(21f, 67f, 1.8f, 1f).fill(C.STONE).width(0.4f)
        ellipse(49f, 67.2f, 1.6f, 0.9f).fill(C.STONE_LIGHT).width(0.4f)
    }

    private fun mountains() = drawing("mountains", "Dağ manzarası", 140f, 100f, listOf("dağ", "manzara", "göl", "güneş", "mountain")) {
        // güneş ve bulutlar
        for (k in 0 until 12) {
            val a = Math.toRadians(k * 30.0)
            line(104f + 13f * cos(a).toFloat(), 24f + 13f * sin(a).toFloat(), 104f + 17f * cos(a).toFloat(), 24f + 17f * sin(a).toFloat())
                .stroke(C.ORANGE).width(0.8f)
        }
        circle(104f, 24f, 10f).fill(C.YELLOW)
        cloud(28f, 20f, 28f, 12f)
        cloud(126f, 44f, 18f, 8f)
        // arka dağlar
        poly(0f, 76f, 24f, 36f, 44f, 58f, 70f, 30f, 96f, 60f, 118f, 42f, 140f, 68f, 140f, 82f, 0f, 82f, closed = true).fill("#B8C4EC")
        // ön dağlar
        poly(8f, 84f, 44f, 30f, 80f, 84f, closed = true).fill("#6F86C9")
        poly(44f, 30f, 80f, 84f, 52f, 84f, closed = true).fill(C.SHADOW).noStroke().alpha(0.18f)
        poly(44f, 30f, 36.5f, 41f, 40f, 40f, 43f, 44.5f, 47f, 40.5f, 51f, 42f, closed = true).fill(C.WHITE).width(0.7f)
        poly(58f, 86f, 96f, 22f, 134f, 86f, closed = true).fill("#5B73B8")
        poly(96f, 22f, 134f, 86f, 104f, 86f, closed = true).fill(C.SHADOW).noStroke().alpha(0.18f)
        poly(96f, 22f, 87.5f, 36f, 91.5f, 34.5f, 95f, 39f, 99f, 34.5f, 102.5f, 37f, 104f, 35.5f, closed = true).fill(C.WHITE).width(0.7f)
        // kuşlar
        path { moveTo(60f, 16f); quadTo(62f, 13.5f, 64f, 16f); quadTo(66f, 13.5f, 68f, 16f) }.width(0.6f)
        path { moveTo(70f, 22f); quadTo(71.5f, 20f, 73f, 22f); quadTo(74.5f, 20f, 76f, 22f) }.width(0.6f)
        // tepeler ve göl
        path(closed = true) { moveTo(0f, 80f); quadTo(38f, 68f, 76f, 82f); quadTo(110f, 70f, 140f, 80f); lineTo(140f, 99f); lineTo(0f, 99f) }
            .fill(C.GREEN)
        path(closed = true) { moveTo(0f, 90f); quadTo(30f, 84f, 60f, 92f); lineTo(60f, 99f); lineTo(0f, 99f) }.fill(C.GREEN_DARK).noStroke().alpha(0.35f)
        ellipse(84f, 90f, 30f, 5.5f).fill(C.WATER)
        ellipse(84f, 88.6f, 26f, 3f).fill(C.WATER_LIGHT).noStroke().alpha(0.6f)
        for ((x, y, w) in listOf(Triple(72f, 90f, 8f), Triple(88f, 92f, 10f), Triple(98f, 89f, 6f))) line(x, y, x + w, y).stroke(C.WHITE).width(0.6f)
        // çamlar
        for ((x, s) in listOf(12f to 16f, 20f to 12f, 118f to 15f, 126f to 11f, 132f to 13f)) smallPine(x, 84f - (if (x < 60f) 2f else 0f), s)
        for ((x, col) in listOf(40f to C.YELLOW, 46f to C.WHITE, 54f to C.PINK, 124f to C.YELLOW)) circle(x, 94f, 1f).fill(col).width(0.4f)
        tuft(30f, 96f, 6f, 5f, C.GREEN_LIGHT)
        tuft(112f, 97f, 6f, 5f, C.GREEN_LIGHT)
    }

    private fun rainbow() = drawing("rainbow", "Gökkuşağı", 120f, 90f, listOf("gökkuşağı", "bulut", "yağmur", "güneş", "rainbow")) {
        // güneş
        for (k in 0 until 10) {
            val a = Math.toRadians(k * 36.0)
            line(102f + 10f * cos(a).toFloat(), 16f + 10f * sin(a).toFloat(), 102f + 13.5f * cos(a).toFloat(), 16f + 13.5f * sin(a).toFloat())
                .stroke(C.ORANGE).width(0.8f)
        }
        circle(102f, 16f, 7.5f).fill(C.YELLOW)
        // yaylar
        val colors = listOf(C.RED, C.ORANGE, C.YELLOW, C.GREEN, C.BLUE, C.PURPLE)
        for ((i, col) in colors.withIndex()) {
            val ro = 48f - i * 5f
            val ri = ro - 5f
            path(closed = true) {
                moveTo(60f - ro, 76f)
                arcTo(60f, 76f, ro, ro, 180f, 180f)
                lineTo(60f + ri, 76f)
                arcTo(60f, 76f, ri, ri, 0f, -180f)
            }.fill(col).width(0.7f)
        }
        arc(60f, 76f, 46f, 46f, 200f, 50f).stroke(C.WHITE).width(0.6f).alpha(0.6f)
        // yağmur damlaları
        for ((x, y) in listOf(10f to 80f, 18f to 86f, 26f to 81f, 96f to 82f, 104f to 87f, 112f to 80f)) {
            path(closed = true) { moveTo(x, y - 3f); quadTo(x + 2f, y, x + 1.6f, y + 1f); arcTo(x, y + 1f, 1.6f, 1.6f, 0f, 180f); quadTo(x - 2f, y, x, y - 3f) }
                .fill(C.WATER_LIGHT).width(0.5f)
        }
        // bulutlar
        cloud(18f, 68f, 34f, 16f)
        cloud(102f, 68f, 34f, 16f)
        // ışıltılar
        for ((x, y, s) in listOf(Triple(30f, 18f, 3f), Triple(84f, 30f, 2.2f), Triple(14f, 40f, 2f), Triple(60f, 16f, 2.4f))) {
            path(closed = true) {
                moveTo(x, y - s); quadTo(x, y, x + s, y); quadTo(x, y, x, y + s); quadTo(x, y, x - s, y); quadTo(x, y, x, y - s)
            }.fill(C.YELLOW).width(0.5f)
        }
    }

    private fun lake() = drawing("lake_shore", "Göl kıyısı", 140f, 100f, listOf("göl", "kamış", "nilüfer", "iskele", "lake")) {
        // gökyüzü
        circle(114f, 20f, 12f).fill(C.YELLOW).noStroke().alpha(0.25f)
        circle(114f, 20f, 8.5f).fill(C.YELLOW)
        cloud(34f, 16f, 28f, 12f)
        cloud(82f, 26f, 18f, 8f)
        path { moveTo(56f, 12f); quadTo(58f, 9.5f, 60f, 12f); quadTo(62f, 9.5f, 64f, 12f) }.width(0.6f)
        path { moveTo(66f, 16f); quadTo(67.5f, 14f, 69f, 16f); quadTo(70.5f, 14f, 72f, 16f) }.width(0.6f)
        // uzak tepeler, çayır
        path(closed = true) { moveTo(0f, 56f); quadTo(30f, 34f, 64f, 50f); quadTo(98f, 30f, 140f, 52f); lineTo(140f, 72f); lineTo(0f, 72f) }.fill(C.GREEN_LIGHT)
        for ((x, y, r) in listOf(Triple(46f, 44f, 4f), Triple(53f, 45f, 3.2f), Triple(96f, 38f, 4.5f), Triple(104f, 40f, 3.6f))) {
            rect(x - 0.6f, y + r - 1f, 1.2f, 3.5f).fill(C.BROWN).width(0.5f)
            puffy(x, y, r, r, 6, 0.28f).fill(C.GREEN_DARK)
        }
        path(closed = true) { moveTo(0f, 62f); quadTo(46f, 50f, 90f, 62f); quadTo(118f, 54f, 140f, 60f); lineTo(140f, 99f); lineTo(0f, 99f) }.fill(C.GREEN)
        // kıyı ve göl
        smooth(16f, 81f, 32f, 68f, 70f, 64f, 110f, 67f, 130f, 79f, 112f, 93f, 68f, 96f, 28f, 92f, closed = true).fill(C.SAND)
        smooth(22f, 81f, 36f, 71f, 70f, 68f, 106f, 70f, 123f, 80f, 108f, 89.5f, 68f, 92f, 32f, 88.5f, closed = true).fill(C.WATER)
        ellipse(70f, 75f, 40f, 4f).fill(C.WATER_LIGHT).noStroke().alpha(0.6f)
        for ((x, y, w) in listOf(Triple(40f, 78f, 10f), Triple(62f, 84f, 12f), Triple(84f, 76f, 8f), Triple(94f, 85f, 9f), Triple(50f, 88f, 7f))) {
            path { moveTo(x, y); quadTo(x + w / 2f, y - 1.2f, x + w, y) }.stroke(C.WHITE).width(0.6f)
        }
        // nilüferler
        for ((x, y, r) in listOf(Triple(56f, 83f, 5f), Triple(70f, 87f, 4f), Triple(45f, 80f, 3.4f))) {
            arc(x, y, r, r * 0.5f, 20f, 320f, pie = true).fill(C.GREEN).width(0.6f)
        }
        for (i in 0 until 6) {
            val a = -90f + (i - 2.5f) * 28f
            val r = Math.toRadians(a.toDouble())
            ellipse(56f + 2.6f * cos(r).toFloat(), 81f + 2.2f * sin(r).toFloat(), 2.6f, 1.1f, a).fill(C.PINK).width(0.45f)
        }
        circle(56f, 81.4f, 1f).fill(C.YELLOW).width(0.4f)
        // iskele
        for (x in listOf(98f, 106f, 114f)) {
            rect(x, 72f, 2f, 12f).fill(C.BROWN_DARK).width(0.6f)
            line(x - 1f, 85f, x + 3f, 85f).stroke(C.WATER_DARK).width(0.6f)
        }
        poly(92f, 70f, 126f, 70f, 128f, 74f, 94f, 74f, closed = true).fill(C.WOOD)
        for (k in 1..6) line(92f + k * 5f, 70f, 94f + k * 5f, 74f).stroke(C.BROWN).width(0.45f)
        // taşlar
        smooth(118f, 90f, 122f, 85f, 130f, 85f, 134f, 90f, 126f, 92f, closed = true).fill(C.STONE)
        smooth(128f, 94f, 131f, 90f, 137f, 91f, 138f, 95f, 133f, 96f, closed = true).fill(C.STONE_DARK)
        // büyük ağaç (sol)
        rect(9f, 44f, 5f, 30f).fill(C.BROWN)
        puffy(12f, 36f, 13f, 12f, 7, 0.3f).fill(C.GREEN_DARK)
        puffy(10f, 33f, 8f, 7f, 6, 0.3f).fill(C.GREEN).noStroke()
        // kamışlar
        for ((x, h, lean) in listOf(Triple(14f, 26f, -3f), Triple(19f, 32f, 1f), Triple(24f, 24f, 4f))) {
            path { moveTo(x, 90f); quadTo(x + lean * 0.3f, 90f - h * 0.5f, x + lean, 90f - h) }.stroke(C.GREEN_DARK).width(0.9f)
            roundRect(x + lean * 0.8f - 1.4f, 90f - h + 2f, 2.8f, 8f, 1.4f).fill(C.BROWN).width(0.6f)
        }
        path(closed = true) { moveTo(11f, 91f); quadTo(8f, 80f, 4f, 72f); quadTo(12f, 80f, 14f, 91f) }.fill(C.GREEN).width(0.6f)
        path(closed = true) { moveTo(22f, 91f); quadTo(28f, 82f, 34f, 76f); quadTo(28f, 84f, 25f, 91f) }.fill(C.GREEN).width(0.6f)
        path(closed = true) { moveTo(17f, 91f); quadTo(17f, 80f, 15f, 70f); quadTo(20f, 80f, 20f, 91f) }.fill(C.GREEN_LIGHT).width(0.6f)
        for ((x, col) in listOf(8f to C.YELLOW, 40f to C.WHITE, 100f to C.PINK, 126f to C.YELLOW, 134f to C.WHITE)) circle(x, 97f - (x % 3f), 1f).fill(col).width(0.4f)
        tuft(34f, 97f, 7f, 6f)
        tuft(108f, 97f, 7f, 5f)
    }
}
