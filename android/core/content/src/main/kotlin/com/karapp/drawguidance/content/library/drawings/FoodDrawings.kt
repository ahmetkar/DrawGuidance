package com.karapp.drawguidance.content.library.drawings

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.cos
import kotlin.math.sin

/** "Yiyecek" hazır çizimleri. */
internal object FoodDrawings {

    val shapes: List<LibShape> by lazy {
        listOf(apple(), pear(), cake(), iceCream(), pizza(), cherries(), strawberry(), watermelon(), bread(), carrot())
    }

    private fun apple() = drawing("apple", "Elma", 80f, 90f, listOf("elma", "meyve", "sağlıklı", "kırmızı", "apple")) {
        shadow(40f, 86f, 28f, 3.5f, 0.16f)
        path(closed = true) {
            moveTo(40f, 26f); cubicTo(52f, 15f, 76f, 21f, 74f, 50f); cubicTo(72f, 74f, 56f, 86f, 48f, 82f)
            quadTo(40f, 78f, 32f, 82f); cubicTo(24f, 86f, 8f, 74f, 6f, 50f); cubicTo(4f, 21f, 28f, 15f, 40f, 26f)
        }.fill(C.RED)
        path(closed = true) { moveTo(56f, 22f); cubicTo(70f, 26f, 76f, 38f, 74f, 50f); cubicTo(72f, 72f, 58f, 84f, 50f, 82f); cubicTo(64f, 72f, 70f, 44f, 56f, 22f) }
            .fill(C.SHADOW).noStroke().alpha(0.14f)
        ellipse(20f, 40f, 4f, 9f, 20f).fill(C.WHITE).noStroke().alpha(0.4f)
        circle(24f, 30f, 2f).fill(C.WHITE).noStroke().alpha(0.55f)
        for ((x, y) in listOf(30f to 50f, 50f to 60f, 58f to 44f, 38f to 68f, 22f to 60f)) circle(x, y, 0.6f).fill(C.YELLOW_LIGHT).noStroke().alpha(0.8f)
        path { moveTo(34f, 25f); quadTo(40f, 29f, 46f, 25f) }.stroke(C.RED_DARK).width(0.6f)
        ribbon(3f, 2f, 40f, 27f, 40.5f, 18f, 43f, 9f).fill(C.BROWN)
        path(closed = true) { moveTo(43f, 17f); quadTo(52f, 6f, 64f, 10f); quadTo(56f, 22f, 43f, 17f) }.fill(C.GREEN)
        path { moveTo(45f, 16.5f); quadTo(53f, 13f, 61f, 11f) }.stroke(C.GREEN_DEEP).width(0.5f)
        for (k in 1..3) line(45f + k * 4f, 15.8f - k * 1.3f, 47f + k * 4f, 12.5f - k * 1.2f).stroke(C.GREEN_DEEP).width(0.4f)
        leaf(33f, 16f, 9f, 2.6f, 200f, C.GREEN_LIGHT)
        path { moveTo(38f, 80.5f); quadTo(40f, 79.5f, 42f, 80.5f) }.stroke(C.RED_DARK).width(0.6f)
        // elma dilimi
        path(closed = true) { moveTo(56f, 86f); lineTo(76f, 86f); arcTo(66f, 86f, 10f, 11f, 0f, -180f) }.fill(C.RED)
        path(closed = true) { moveTo(57.6f, 86f); lineTo(74.4f, 86f); arcTo(66f, 86f, 8.4f, 9.4f, 0f, -180f) }.fill(C.CREAM).width(0.5f)
        ellipse(66f, 81f, 2.6f, 3.4f).fill(C.YELLOW_LIGHT).noStroke()
        drop(64.8f, 81.8f, 0.8f).fill(C.BROWN_DARK).noStroke()
        drop(67.2f, 81.8f, 0.8f).fill(C.BROWN_DARK).noStroke()
    }

    private fun pear() = drawing("pear", "Armut", 70f, 100f, listOf("armut", "meyve", "sağlıklı", "yeşil", "pear")) {
        shadow(35f, 95f, 24f, 3f, 0.16f)
        smooth(35f, 20f, 42f, 23f, 44f, 36f, 55f, 55f, 58f, 74f, 50f, 88f, 35f, 92f, 20f, 88f, 12f, 74f, 15f, 55f, 26f, 36f, 28f, 23f, closed = true).fill("#C9DC6A")
        smooth(44f, 38f, 55f, 56f, 57f, 74f, 50f, 87f, 38f, 91f, 48f, 80f, 51f, 62f, closed = true).fill(C.SHADOW).noStroke().alpha(0.1f)
        ellipse(46f, 70f, 7f, 9f).fill(C.ORANGE).noStroke().alpha(0.35f)
        ellipse(22f, 62f, 3f, 8f, 15f).fill(C.WHITE).noStroke().alpha(0.45f)
        for ((x, y) in listOf(30f to 50f, 38f to 60f, 26f to 74f, 44f to 80f, 34f to 84f, 40f to 44f, 20f to 80f, 50f to 66f)) circle(x, y, 0.7f).fill(C.BROWN_LIGHT).noStroke().alpha(0.8f)
        ribbon(2.6f, 1.8f, 35f, 22f, 35f, 14f, 38f, 6f).fill(C.BROWN)
        leaf(46f, 14f, 16f, 4.5f, -25f, C.GREEN)
        leaf(27f, 17f, 9f, 2.6f, 200f, C.GREEN_LIGHT)
        star(35f, 90.5f, 2.4f, 1f, 5).fill(C.BROWN).width(0.5f)
        circle(22f, 55f, 1.3f).fill(C.WHITE).noStroke().alpha(0.6f)
        for ((x, y) in listOf(28f to 66f, 44f to 56f, 24f to 70f)) circle(x, y, 0.6f).fill(C.BROWN_LIGHT).noStroke().alpha(0.8f)
    }

    private fun cake() = drawing("cake_slice", "Pasta dilimi", 100f, 90f, listOf("pasta", "tatlı", "çilek", "doğum günü", "cake")) {
        // tabak ve çatal
        ellipse(50f, 76f, 46f, 10f).fill(C.WHITE)
        ellipse(50f, 75f, 34f, 6.5f).fill(C.STONE_LIGHT).width(0.5f)
        group(rotate = -8f, px = 80f, py = 80f) {
            roundRect(66f, 81f, 26f, 2f, 1f).fill(C.METAL).width(0.6f)
            path(closed = true) { moveTo(90f, 80f); lineTo(98f, 79.5f); lineTo(98f, 84.5f); lineTo(90f, 84f) }.fill(C.METAL).width(0.6f)
        }
        // dilim: yan yüz katmanları
        val p1x = 14f; val p1y = 50f; val p3x = 86f; val p3y = 44f; val h = 24f
        fun band(t0: Float, t1: Float, color: String) =
            poly(p1x, p1y + h * t0, p3x, p3y + h * t0, p3x, p3y + h * t1, p1x, p1y + h * t1, closed = true).fill(color).width(0f)
        band(0f, 1f, "#F9D68A")
        band(0.3f, 0.42f, C.CREAM)
        band(0.42f, 0.5f, C.RED)
        band(0.72f, 0.84f, C.CREAM)
        for ((x, y) in listOf(28f to 56f, 44f to 66f, 60f to 55f, 74f to 64f, 36f to 70f, 66f to 68f)) circle(x, y, 0.8f).fill(C.WOOD).noStroke()
        poly(p1x, p1y, p3x, p3y, p3x, p3y + h, p1x, p1y + h, closed = true).fill(C.WHITE).alpha(0f).width(0f)
        poly(p1x, p1y + h, p1x, p1y, p3x, p3y, p3x, p3y + h, p1x, p1y + h)
        // arka (kabuk) yüz
        poly(86f, 44f, 70f, 32f, 70f, 36f, 86f, 68f, closed = true).fill("#E8B96A")
        // üst krema ve damlalar
        path(closed = true) {
            moveTo(p1x, p1y); lineTo(70f, 32f); lineTo(p3x, p3y)
            val n = 7
            for (i in 0 until n) {
                val t0 = i / n.toFloat(); val t1 = (i + 1) / n.toFloat()
                val xa = p3x + (p1x - p3x) * t0; val ya = p3y + (p1y - p3y) * t0
                val xb = p3x + (p1x - p3x) * t1; val yb = p3y + (p1y - p3y) * t1
                val drip = if (i % 2 == 0) 6f else 3.5f
                cubicTo(xa, ya + drip, xb, yb + drip, xb, yb)
            }
        }.fill(C.PINK)
        shine(40f, 44f, 8f, 1.4f, -12f, 0.4f)
        // çilek ve krema
        puffy(64f, 36f, 5f, 3.4f, 6, 0.3f).fill(C.WHITE).width(0.7f)
        path(closed = true) { moveTo(40f, 30f); cubicTo(48f, 29f, 48f, 36f, 44f, 42f); cubicTo(42f, 44f, 38f, 44f, 36f, 42f); cubicTo(32f, 36f, 32f, 29f, 40f, 30f) }.fill(C.RED)
        for ((x, y) in listOf(38f to 34f, 42f to 34f, 40f to 38f, 37f to 39f, 43f to 39f)) ellipse(x, y, 0.4f, 0.7f).fill(C.YELLOW).noStroke()
        for (a in listOf(-40f, 0f, 40f)) leaf(40f + 3f * sin(Math.toRadians(a.toDouble())).toFloat(), 29.5f, 6f, 1.6f, a - 90f + 90f, C.GREEN, vein = false)
        for ((x, y, col) in listOf(Triple(24f, 47f, C.YELLOW), Triple(54f, 42f, C.TEAL), Triple(30f, 43f, C.PURPLE), Triple(60f, 46f, C.YELLOW), Triple(50f, 38f, C.ORANGE))) {
            group(rotate = x * 7f, px = x, py = y) { roundRect(x - 1.5f, y - 0.5f, 3f, 1f, 0.5f).fill(col).noStroke() }
        }
    }

    private fun iceCream() = drawing("ice_cream", "Dondurma külahı", 70f, 100f, listOf("dondurma", "külah", "tatlı", "yaz", "ice cream")) {
        // külah ve waffle deseni
        val ax = 17f; val ay = 52f; val bx = 53f; val by = 52f; val cx = 35f; val cy = 97f
        poly(ax, ay, bx, by, cx, cy, closed = true).fill(C.WOOD)
        for (k in 1..5) {
            val u = k / 6f
            val px = ax + (bx - ax) * u
            line(px, ay, u * bx + (1 - u) * cx, u * by + (1 - u) * cy).stroke(C.BROWN).width(0.5f)
            line(px, ay, (1 - u) * ax + u * cx, (1 - u) * ay + u * cy).stroke(C.BROWN).width(0.5f)
        }
        poly(35f, 52f, 53f, 52f, 35f, 97f, closed = true).fill(C.SHADOW).noStroke().alpha(0.1f)
        roundRect(14f, 48f, 42f, 6f, 3f).fill("#E0A060")
        // toplar: çilek, çikolata
        fun VectorBuilder.scoop(x: Float, y: Float, r: Float, color: String, drips: Boolean) {
            path(closed = true) {
                moveTo(x - r, y + r * 0.35f)
                arcTo(x, y, r, r * 0.95f, 160f, 220f)
                val n = 5
                for (i in 0 until n) {
                    val xa = x + r - 2f * r * i / n
                    val xb = x + r - 2f * r * (i + 1) / n
                    val deep = if (drips && i == 1) r * 0.6f else r * 0.2f
                    quadTo((xa + xb) / 2f, y + r * 0.35f + deep, xb, y + r * 0.35f)
                }
            }.fill(color)
        }
        scoop(35f, 42f, 16f, C.PINK, true)
        shine(26f, 36f, 3f, 1.6f, -30f, 0.45f)
        scoop(35f, 24f, 12.5f, C.BROWN, false)
        shine(28f, 19f, 2.4f, 1.3f, -30f, 0.4f)
        for ((x, y, col) in listOf(Triple(30f, 22f, C.YELLOW), Triple(38f, 18f, C.TEAL), Triple(42f, 26f, C.PINK), Triple(33f, 28f, C.WHITE), Triple(44f, 20f, C.YELLOW), Triple(26f, 26f, C.TEAL))) {
            group(rotate = x * 11f, px = x, py = y) { roundRect(x - 1.6f, y - 0.55f, 3.2f, 1.1f, 0.55f).fill(col).noStroke() }
        }
        // kiraz
        path { moveTo(36f, 8f); quadTo(38f, 3f, 43f, 1.5f) }.stroke(C.BROWN_DARK).width(0.8f)
        circle(35f, 10.5f, 4f).fill(C.RED)
        shine(33.6f, 9f, 1f, 0.8f, 0f, 0.8f)
    }

    private fun pizza() = drawing("pizza_slice", "Pizza dilimi", 90f, 100f, listOf("pizza", "dilim", "peynir", "yemek", "pizza")) {
        shadow(45f, 96f, 20f, 2.5f, 0.14f)
        // peynir (sosla)
        path(closed = true) { moveTo(10f, 21f); quadTo(45f, 9f, 80f, 21f); lineTo(46f, 96f); quadTo(44f, 97f, 43.5f, 95f) }.fill(C.RED)
        path(closed = true) {
            moveTo(12f, 22f); quadTo(45f, 11f, 78f, 22f)
            lineTo(72f, 35f); quadTo(74f, 42f, 70f, 44f); lineTo(58f, 70f); quadTo(58f, 78f, 54f, 78f); lineTo(45f, 93f); lineTo(32f, 66f)
            quadTo(27f, 64f, 29f, 58f); lineTo(20f, 40f); quadTo(15f, 38f, 17f, 32f)
        }.fill("#F9D56E")
        // kenar
        path(closed = true) { moveTo(7f, 21f); quadTo(45f, 4f, 83f, 21f); quadTo(85f, 28f, 79f, 28f); quadTo(45f, 15f, 11f, 28f); quadTo(5f, 28f, 7f, 21f) }.fill("#E3A857")
        for (x in listOf(22f, 36f, 52f, 68f)) circle(x, 18.5f - (if (x in 30f..60f) 2.5f else 0f), 0.7f).fill(C.CREAM).noStroke()
        // malzemeler
        for ((x, y, r) in listOf(Triple(32f, 36f, 6f), Triple(54f, 34f, 5.5f), Triple(44f, 56f, 5.5f), Triple(40f, 78f, 3.6f))) {
            circle(x, y, r).fill(C.RED_DARK).width(0.7f)
            for (k in 0 until 3) {
                val a = Math.toRadians(k * 120.0 + x)
                circle(x + r * 0.45f * cos(a).toFloat(), y + r * 0.45f * sin(a).toFloat(), r * 0.14f).fill("#8E2E22").noStroke()
            }
        }
        for ((x, y) in listOf(62f to 44f, 24f to 30f, 50f to 70f)) {
            circle(x, y, 2.4f).fill(C.BLACK).width(0.5f)
            circle(x, y, 1f).fill("#F9D56E").noStroke()
        }
        for ((x, y, a) in listOf(Triple(44f, 44f, -20f), Triple(64f, 28f, 30f), Triple(36f, 62f, 60f))) {
            group(rotate = a, px = x, py = y) { arc(x, y + 3f, 4.5f, 3f, 210f, 120f).stroke(C.GREEN).width(1.4f) }
        }
        leaf(26f, 46f, 7f, 2.4f, -40f, C.GREEN_DARK)
        leaf(58f, 54f, 7f, 2.4f, 30f, C.GREEN_DARK)
        shine(22f, 26f, 4f, 1f, -20f, 0.4f)
    }

    private fun cherries() = drawing("cherries", "Kiraz", 80f, 90f, listOf("kiraz", "meyve", "yaz", "kırmızı", "cherry")) {
        shadow(40f, 86f, 30f, 3f, 0.16f)
        path { moveTo(44f, 12f); quadTo(30f, 30f, 26f, 56f) }.stroke(C.GREEN_DARK).width(1.4f)
        path { moveTo(44f, 12f); quadTo(52f, 36f, 54f, 60f) }.stroke(C.GREEN_DARK).width(1.4f)
        path(closed = true) { moveTo(44f, 12f); quadTo(58f, 2f, 72f, 10f); quadTo(58f, 22f, 44f, 12f) }.fill(C.GREEN)
        path { moveTo(46f, 12f); quadTo(58f, 9f, 69f, 10f) }.stroke(C.GREEN_DEEP).width(0.5f)
        for (k in 1..3) line(47f + k * 5f, 11.4f - k * 0.5f, 49f + k * 5f, 8f - k * 0.6f).stroke(C.GREEN_DEEP).width(0.4f)
        leaf(34f, 10f, 12f, 3.4f, 195f, C.GREEN_LIGHT)
        roundRect(41f, 10f, 6f, 4f, 2f).fill(C.BROWN).width(0.7f)
        for ((x, y) in listOf(26f to 68f, 54f to 72f)) {
            path(closed = true) {
                moveTo(x, y - 11f); cubicTo(x + 6f, y - 15f, x + 15f, y - 10f, x + 14f, y); cubicTo(x + 13f, y + 11f, x - 13f, y + 11f, x - 14f, y)
                cubicTo(x - 15f, y - 10f, x - 6f, y - 15f, x, y - 11f)
            }.fill(C.RED)
            path(closed = true) { moveTo(x + 6f, y - 12f); cubicTo(x + 14f, y - 8f, x + 15f, y, x + 12f, y + 6f); cubicTo(x + 8f, y + 9f, x + 2f, y + 9f, x - 2f, y + 8f); cubicTo(x + 10f, y + 4f, x + 12f, y - 6f, x + 6f, y - 12f) }
                .fill(C.SHADOW).noStroke().alpha(0.15f)
            ellipse(x - 6f, y - 4f, 2.4f, 4f, 25f).fill(C.WHITE).noStroke().alpha(0.55f)
            circle(x - 3f, y - 8f, 1f).fill(C.WHITE).noStroke().alpha(0.7f)
            path { moveTo(x - 2.5f, y - 11f); quadTo(x, y - 9f, x + 2.5f, y - 11f) }.stroke(C.RED_DARK).width(0.5f)
            drop(x + 6f, y + 3f, 1.2f).fill(C.SKY_LIGHT).width(0.4f)
        }
    }

    private fun strawberry() = drawing("strawberry", "Çilek", 80f, 100f, listOf("çilek", "meyve", "yaz", "kırmızı", "strawberry")) {
        shadow(40f, 95f, 22f, 3f, 0.16f)
        path(closed = true) {
            moveTo(40f, 28f); cubicTo(64f, 23f, 74f, 44f, 64f, 62f); cubicTo(58f, 78f, 46f, 92f, 40f, 94f)
            cubicTo(34f, 92f, 22f, 78f, 16f, 62f); cubicTo(6f, 44f, 16f, 23f, 40f, 28f)
        }.fill(C.RED)
        path(closed = true) { moveTo(56f, 28f); cubicTo(70f, 36f, 70f, 52f, 64f, 62f); cubicTo(58f, 78f, 46f, 92f, 40f, 94f); cubicTo(54f, 78f, 64f, 56f, 56f, 28f) }
            .fill(C.SHADOW).noStroke().alpha(0.12f)
        // tohumlar
        for (row in 0 until 7) {
            val y = 38f + row * 7.5f
            val half = when (row) { 0 -> 22f; 1 -> 24f; 2 -> 23f; 3 -> 20f; 4 -> 15f; 5 -> 10f; else -> 4f }
            var x = 40f - half + 5f + (row % 2) * 3.5f
            while (x < 40f + half - 3f) {
                path(closed = true) { moveTo(x, y - 1.4f); quadTo(x + 1f, y, x, y + 1.1f); quadTo(x - 1f, y, x, y - 1.4f) }.fill(C.YELLOW).width(0.35f)
                x += 7f
            }
        }
        ellipse(25f, 46f, 3f, 7f, 20f).fill(C.WHITE).noStroke().alpha(0.4f)
        // çanak yapraklar ve sap
        path(closed = true) {
            moveTo(40f, 24f)
            val tips = listOf(-160f, -120f, -60f, -20f, 30f, 100f, 150f)
            for ((i, deg) in listOf(200f, 235f, 270f, 305f, 340f, 20f, 160f).withIndex()) {
                val a = Math.toRadians(deg.toDouble())
                val len = if (deg == 20f || deg == 160f) 11f else 15f
                val tx = 40f + len * cos(a).toFloat(); val ty = 30f + len * 0.62f * sin(a).toFloat() + 2f
                val b = Math.toRadians(deg + 18.0)
                quadTo(40f + 4f * cos(a).toFloat(), 30f + 3f * sin(a).toFloat(), tx, ty)
                quadTo(40f + 5f * cos(b).toFloat(), 30f + 4f * sin(b).toFloat(), 40f + 3f * cos(b).toFloat(), 30f + 2f * sin(b).toFloat())
                if (i == tips.size) break
            }
        }.fill(C.GREEN)
        ribbon(3f, 2f, 40f, 27f, 40f, 18f, 43f, 12f).fill(C.GREEN_DARK)
        leaf(58f, 16f, 14f, 4f, -30f, C.GREEN)
        blossom(20f, 14f, 6f, C.WHITE, C.YELLOW, 5, 0f, 0.5f)
    }

    private fun watermelon() = drawing("watermelon", "Karpuz dilimi", 110f, 80f, listOf("karpuz", "meyve", "yaz", "dilim", "watermelon")) {
        shadow(55f, 74f, 46f, 3.5f, 0.16f)
        fun half(r: Float) = path(closed = true) { moveTo(55f - r, 18f); arcTo(55f, 18f, r, r, 180f, -180f) }
        half(50f).fill(C.GREEN_DARK)
        for (k in 0 until 9) {
            val a = Math.toRadians(20.0 + k * 17.5)
            val x = 55f + 48f * cos(a).toFloat(); val y = 18f + 48f * sin(a).toFloat()
            val x2 = 55f + 44f * cos(a + 0.05).toFloat(); val y2 = 18f + 44f * sin(a + 0.05).toFloat()
            line(x, y, x2, y2).stroke(C.GREEN_DEEP).width(1f)
        }
        half(45.5f).fill(C.GREEN_LIGHT).width(0.6f)
        half(42.5f).fill("#F4F7E4").width(0.4f)
        half(40.5f).fill("#F2555A").width(0.6f)
        path(closed = true) { moveTo(55f + 40f, 18f); arcTo(55f, 18f, 40f, 40f, 0f, 60f); quadTo(78f, 30f, 82f, 18f) }.fill(C.SHADOW).noStroke().alpha(0.08f)
        // çekirdekler
        for ((x, y, a) in listOf(Triple(30f, 26f, -30f), Triple(44f, 30f, -15f), Triple(58f, 32f, 5f), Triple(72f, 28f, 25f), Triple(84f, 24f, 40f),
            Triple(38f, 42f, -25f), Triple(52f, 46f, -5f), Triple(66f, 44f, 15f), Triple(78f, 38f, 30f), Triple(48f, 24f, -10f), Triple(62f, 22f, 10f), Triple(58f, 52f, 0f))) {
            group(rotate = a, px = x, py = y) {
                path(closed = true) { moveTo(x, y - 2.4f); quadTo(x + 1.6f, y, x, y + 1.8f); quadTo(x - 1.6f, y, x, y - 2.4f) }.fill(C.BLACK).noStroke()
                circle(x - 0.3f, y - 0.6f, 0.4f).fill(C.WHITE).noStroke().alpha(0.7f)
            }
        }
        line(16f, 21f, 94f, 21f).stroke(C.WHITE).width(0.6f).alpha(0.6f)
        line(5f, 18f, 105f, 18f).width(1f)
    }

    private fun bread() = drawing("bread", "Ekmek", 120f, 80f, listOf("ekmek", "somun", "fırın", "buğday", "bread")) {
        // tahta
        roundRect(4f, 62f, 104f, 10f, 4f).fill(C.WOOD)
        roundRect(106f, 64f, 12f, 6f, 3f).fill(C.WOOD)
        circle(113f, 67f, 1.3f).fill(C.BROWN).width(0.5f)
        for (k in 0..2) line(10f + k * 30f, 66f, 30f + k * 30f, 66f).stroke(C.BROWN_LIGHT).width(0.45f)
        // buğday başağı
        stem(C.YELLOW, 0.8f, 14f, 60f, 18f, 40f, 24f, 20f)
        for (k in 0 until 5) {
            val y = 22f + k * 5f
            val x = 24f - k * 1.3f
            ellipse(x - 2.4f, y + 1f, 1.6f, 3f, -30f).fill(C.YELLOW).width(0.5f)
            ellipse(x + 2.4f, y + 1f, 1.6f, 3f, 30f).fill(C.YELLOW).width(0.5f)
        }
        // somun
        path(closed = true) { moveTo(16f, 60f); cubicTo(14f, 20f, 102f, 20f, 100f, 60f); quadTo(100f, 66f, 92f, 66f); lineTo(24f, 66f); quadTo(16f, 66f, 16f, 60f) }.fill("#D9954A")
        path(closed = true) { moveTo(20f, 52f); cubicTo(22f, 28f, 94f, 28f, 96f, 52f); cubicTo(88f, 36f, 28f, 36f, 20f, 52f) }.fill(C.YELLOW_LIGHT).noStroke().alpha(0.35f)
        for (k in 0 until 4) {
            val x = 34f + k * 15f
            group(rotate = -25f, px = x, py = 40f) {
                path(closed = true) { moveTo(x - 7f, 40f); quadTo(x, 36f, x + 7f, 40f); quadTo(x, 44f, x - 7f, 40f) }.fill("#F4D39A").width(0.6f)
            }
        }
        for ((x, y) in listOf(30f to 34f, 46f to 30f, 62f to 29f, 78f to 32f, 88f to 40f, 24f to 46f)) circle(x, y, 0.8f).fill(C.WHITE).noStroke().alpha(0.8f)
        // dilim
        path(closed = true) { moveTo(66f, 66f); lineTo(66f, 50f); cubicTo(66f, 40f, 90f, 40f, 90f, 50f); lineTo(90f, 66f) }.fill("#C98040")
        path(closed = true) { moveTo(68f, 66f); lineTo(68f, 51f); cubicTo(68f, 43f, 88f, 43f, 88f, 51f); lineTo(88f, 66f) }.fill("#FBE7C0").width(0.5f)
        for ((x, y, r) in listOf(Triple(72f, 52f, 1.2f), Triple(80f, 49f, 0.9f), Triple(84f, 56f, 1.3f), Triple(75f, 60f, 1f), Triple(82f, 63f, 0.8f), Triple(71f, 58f, 0.7f))) {
            ellipse(x, y, r, r * 0.7f).fill("#E9C98E").noStroke()
        }
        shadow(78f, 66.5f, 12f, 1f, 0.2f)
    }

    private fun carrot() = drawing("carrot", "Havuç", 70f, 100f, listOf("havuç", "sebze", "turuncu", "tavşan", "carrot")) {
        shadow(38f, 95f, 12f, 2f, 0.14f)
        group(rotate = 12f, px = 35f, py = 60f) {
            // yapraklar
            for ((x, a, col) in listOf(Triple(30f, -118f, C.GREEN_DARK), Triple(40f, -62f, C.GREEN_DARK), Triple(35f, -90f, C.GREEN))) {
                val r = Math.toRadians(a.toDouble())
                val ex = x + 20f * cos(r).toFloat(); val ey = 30f + 22f * sin(r).toFloat()
                line(x, 30f, ex, ey).stroke(C.GREEN_DEEP).width(0.7f)
                for (k in 1..3) {
                    val t = k / 3.4f
                    val px = x + (ex - x) * t; val py = 30f + (ey - 30f) * t
                    val side = Math.toRadians((a - 90f).toDouble())
                    val ox = 3.6f * cos(side).toFloat(); val oy = 3.6f * sin(side).toFloat()
                    leaf(px + ox, py + oy, 8f, 2.6f, a - 50f, col, vein = false)
                    leaf(px - ox, py - oy, 8f, 2.6f, a + 50f, col, vein = false)
                }
                leaf(ex + 3f * cos(r).toFloat(), ey + 3f * sin(r).toFloat(), 8f, 2.8f, a, col, vein = false)
            }
            // gövde
            path(closed = true) { moveTo(22f, 32f); quadTo(35f, 24f, 48f, 32f); quadTo(46f, 64f, 37f, 94f); quadTo(35f, 97f, 33f, 94f); quadTo(26f, 64f, 22f, 32f) }.fill(C.ORANGE)
            path(closed = true) { moveTo(40f, 28f); quadTo(46f, 30f, 48f, 32f); quadTo(46f, 64f, 37f, 94f); quadTo(40f, 64f, 40f, 28f) }.fill(C.SHADOW).noStroke().alpha(0.1f)
            roundRect(26f, 36f, 2.6f, 22f, 1.3f).fill(C.WHITE).noStroke().alpha(0.35f)
            for ((y, w, left) in listOf(Triple(42f, 7f, true), Triple(52f, 6f, false), Triple(62f, 5f, true), Triple(72f, 4f, false), Triple(82f, 3f, true))) {
                val x0 = if (left) 30f - (y - 40f) * 0.02f else 40f - (y - 40f) * 0.12f
                path { moveTo(x0, y); quadTo(x0 + w / 2f, y + 1.2f, x0 + w, y) }.stroke(C.ORANGE_DARK).width(0.6f)
            }
            line(35f, 95f, 35.5f, 99f).stroke(C.BROWN_LIGHT).width(0.5f)
        }
    }
}
