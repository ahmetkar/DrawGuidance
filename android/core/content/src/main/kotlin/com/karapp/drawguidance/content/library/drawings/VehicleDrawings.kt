package com.karapp.drawguidance.content.library.drawings

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.cos
import kotlin.math.sin

/** "Taşıtlar" hazır çizimleri. */
internal object VehicleDrawings {

    val shapes: List<LibShape> by lazy {
        listOf(car(), bus(), bicycle(), airplane(), sailboat(), train(), helicopter(), rocket())
    }

    private fun VectorBuilder.puff(cx: Float, cy: Float, r: Float) {
        circle(cx, cy, r).fill("#EEF2F6").width(0.6f)
        circle(cx - r * 0.3f, cy - r * 0.3f, r * 0.35f).fill(C.WHITE).noStroke()
    }

    private fun car() = drawing("car", "Araba", 140f, 80f, listOf("araba", "otomobil", "taşıt", "yol", "car")) {
        val paint = C.RED
        shadow(70f, 70f, 62f, 4f, 0.18f)
        // egzoz dumanı
        puff(5f, 58f, 3f)
        puff(3f, 49f, 2.2f)
        rect(5f, 58.5f, 6f, 2.2f).fill(C.STONE_DARK).width(0.6f)
        // gövde
        path(closed = true) {
            moveTo(10f, 58f); lineTo(10f, 46f); quadTo(10f, 39f, 18f, 38f); lineTo(36f, 36f)
            lineTo(47f, 21f); quadTo(49f, 18f, 54f, 18f); lineTo(86f, 18f); quadTo(90f, 18f, 93f, 21f)
            lineTo(106f, 35f); lineTo(122f, 37.5f); quadTo(132f, 39.5f, 132f, 48f); lineTo(132f, 58f)
            lineTo(120f, 58f); arcTo(106f, 58f, 14f, 14f, 0f, -180f)
            lineTo(48f, 58f); arcTo(34f, 58f, 14f, 14f, 0f, -180f)
            lineTo(10f, 58f)
        }.fill(paint)
        rect(12f, 50f, 8f, 8f).fill(C.SHADOW).noStroke().alpha(0.12f)
        rect(48f, 50f, 58f, 8f).fill(C.SHADOW).noStroke().alpha(0.12f)
        rect(120f, 50f, 12f, 8f).fill(C.SHADOW).noStroke().alpha(0.12f)
        rect(14f, 41f, 114f, 2.4f).fill(C.WHITE).noStroke().alpha(0.75f)
        // camlar
        poly(40.5f, 35.5f, 50f, 22.5f, 65f, 22.5f, 65f, 35.5f, closed = true).fill(C.GLASS).width(0.7f)
        poly(70f, 22.5f, 87f, 22.5f, 98.5f, 35.5f, 70f, 35.5f, closed = true).fill(C.GLASS).width(0.7f)
        poly(52f, 35.5f, 58f, 22.5f, 61f, 22.5f, 55f, 35.5f, closed = true).fill(C.WHITE).noStroke().alpha(0.5f)
        poly(76f, 35.5f, 82f, 22.5f, 86f, 22.5f, 80f, 35.5f, closed = true).fill(C.WHITE).noStroke().alpha(0.5f)
        path { moveTo(56f, 19.5f); lineTo(84f, 19.5f) }.stroke(C.WHITE).width(0.5f).alpha(0.6f)
        // kapılar
        line(67.5f, 36f, 67.5f, 56f).width(0.6f)
        path { moveTo(38f, 37f); quadTo(37f, 44f, 39f, 45f) }.width(0.6f)
        path { moveTo(99f, 37f); lineTo(98f, 45f) }.width(0.6f)
        roundRect(57f, 39.5f, 6f, 1.8f, 0.9f).fill(C.GRAY_DARK).width(0.4f)
        roundRect(87f, 39.5f, 6f, 1.8f, 0.9f).fill(C.GRAY_DARK).width(0.4f)
        // ayna, farlar, tamponlar
        path(closed = true) { moveTo(99f, 33f); lineTo(104f, 30f); lineTo(106f, 34.5f); lineTo(100f, 36f) }.fill(paint).width(0.6f)
        path(closed = true) { moveTo(125f, 41f); quadTo(130.5f, 42f, 131.5f, 46f); lineTo(125f, 46f) }.fill(C.YELLOW_LIGHT).width(0.6f)
        poly(131f, 43f, 139f, 40f, 139f, 50f, 131f, 47f, closed = true).fill(C.YELLOW_LIGHT).noStroke().alpha(0.5f)
        roundRect(10.5f, 41f, 3.5f, 6f, 1f).fill(C.ORANGE).width(0.6f)
        roundRect(127f, 52f, 8f, 5f, 2f).fill(C.STONE).width(0.7f)
        roundRect(6f, 52f, 7f, 5f, 2f).fill(C.STONE).width(0.7f)
        line(40f, 18.5f, 34f, 8f).width(0.5f)
        circle(34f, 8f, 0.8f).fill(C.GRAY_DARK).noStroke()
        // tekerlekler
        wheel(34f, 58f, 11f)
        wheel(106f, 58f, 11f)
    }

    private fun bus() = drawing("bus", "Otobüs", 150f, 80f, listOf("otobüs", "şehir", "toplu taşıma", "taşıt", "bus")) {
        val paint = C.TEAL; val dark = "#2E8F88"
        shadow(76f, 70f, 68f, 4f, 0.18f)
        roundRect(58f, 8f, 34f, 6f, 2f).fill(C.STONE_LIGHT).width(0.8f)
        for (k in 1..4) line(58f + k * 6.8f, 9.5f, 58f + k * 6.8f, 12.5f).stroke(C.STONE_DARK).width(0.4f)
        // gövde
        roundRect(8f, 12f, 136f, 52f, 6f).fill(paint)
        roundRect(8f, 12f, 136f, 8f, 4f).fill(C.WHITE).width(0.8f)
        rect(8f, 46f, 136f, 3f).fill(C.WHITE).width(0.6f)
        path(closed = true) {
            moveTo(8f, 49f); lineTo(144f, 49f); lineTo(144f, 58f); quadTo(144f, 64f, 138f, 64f)
            lineTo(8f + 6f, 64f); quadTo(8f, 64f, 8f, 58f)
        }.fill(dark).width(0.8f)
        // yolcu camları ve yolcular
        val heads = listOf(C.BROWN_DARK, C.YELLOW, C.BLACK, C.ORANGE_DARK, C.BROWN_LIGHT, C.PINK_DARK)
        for (k in 0 until 6) {
            val x = 13f + k * 16f
            roundRect(x, 23f, 13f, 17f, 1.8f).fill(C.GLASS).width(0.7f)
            if (k % 2 == 0 || k == 3) {
                path(closed = true) { moveTo(x + 2.5f, 40f); quadTo(x + 2.5f, 33f, x + 6.5f, 33f); quadTo(x + 10.5f, 33f, x + 10.5f, 40f) }
                    .fill(if (k % 3 == 0) C.RED else C.PURPLE).width(0.5f)
                circle(x + 6.5f, 29.5f, 3f).fill(C.SAND).width(0.5f)
                path(closed = true) { moveTo(x + 3.5f, 29.5f); quadTo(x + 3.8f, 26f, x + 6.5f, 26f); quadTo(x + 9.2f, 26f, x + 9.5f, 29.5f); quadTo(x + 6.5f, 27.5f, x + 3.5f, 29.5f) }
                    .fill(heads[k]).width(0.4f)
            }
            poly(x + 2f, 40f, x + 7f, 23f, x + 9.5f, 23f, x + 4.5f, 40f, closed = true).fill(C.WHITE).noStroke().alpha(0.4f)
        }
        // kapı
        roundRect(111f, 22f, 15f, 38f, 1.5f).fill(C.WHITE).width(0.8f)
        rect(112.5f, 23.5f, 5.5f, 35f).fill(C.GLASS_DARK).width(0.6f)
        rect(119f, 23.5f, 5.5f, 35f).fill(C.GLASS_DARK).width(0.6f)
        line(115.2f, 36f, 115.2f, 44f).stroke(C.GRAY_DARK).width(0.8f)
        line(121.8f, 36f, 121.8f, 44f).stroke(C.GRAY_DARK).width(0.8f)
        // ön cam, tabela
        path(closed = true) { moveTo(129f, 22f); lineTo(140f, 22f); quadTo(143f, 22f, 143f, 26f); lineTo(143f, 42f); lineTo(129f, 42f) }
            .fill(C.GLASS).width(0.7f)
        poly(131f, 42f, 136f, 22f, 139f, 22f, 134f, 42f, closed = true).fill(C.WHITE).noStroke().alpha(0.4f)
        roundRect(126f, 14f, 16f, 5f, 1f).fill(C.BLACK).width(0.6f)
        for (k in 0 until 4) roundRect(128f + k * 3.3f, 15.5f, 2.2f, 2f, 0.4f).fill(C.YELLOW).noStroke()
        roundRect(139.5f, 51f, 4f, 4f, 1f).fill(C.YELLOW_LIGHT).width(0.6f)
        roundRect(8.5f, 51f, 3f, 5f, 1f).fill(C.RED).width(0.6f)
        path { moveTo(143f, 24f); lineTo(147f, 24f); lineTo(147f, 30f) }.width(0.7f)
        roundRect(145.5f, 29f, 3f, 6f, 1f).fill(C.GRAY_DARK).width(0.5f)
        // tekerlek yuvaları ve tekerlekler
        arc(36f, 64f, 12f, 12f, 180f, 180f, pie = true).fill(C.BLACK).width(0.7f)
        arc(116f, 64f, 12f, 12f, 180f, 180f, pie = true).fill(C.BLACK).width(0.7f)
        wheel(36f, 64f, 9.5f)
        wheel(116f, 64f, 9.5f)
    }

    private fun bicycle() = drawing("bicycle", "Bisiklet", 130f, 80f, listOf("bisiklet", "pedal", "sepet", "spor", "bicycle")) {
        val frame = C.RED
        shadow(65f, 77f, 56f, 2.5f, 0.16f)
        fun VectorBuilder.bikeWheel(cx: Float, cy: Float, r: Float) {
            for (k in 0 until 16) {
                val a = Math.toRadians(k * 22.5)
                line(cx + 2f * cos(a).toFloat(), cy + 2f * sin(a).toFloat(), cx + (r - 2.5f) * cos(a).toFloat(), cy + (r - 2.5f) * sin(a).toFloat())
                    .stroke(C.STONE_DARK).width(0.3f)
            }
            arc(cx, cy, r - 2.6f, r - 2.6f, 0f, 360f).stroke(C.METAL).width(1.2f)
            arc(cx, cy, r, r, 0f, 360f).width(3.6f)
            arc(cx, cy, r, r, 0f, 360f).stroke(C.TIRE).width(2.6f)
            arc(cx, cy, r + 0.6f, r + 0.6f, 200f, 60f).stroke(C.WHITE).width(0.4f).alpha(0.4f)
            circle(cx, cy, 2.4f).fill(C.METAL).width(0.6f)
        }
        bikeWheel(30f, 54f, 21f)
        bikeWheel(100f, 54f, 21f)
        // çamurluklar
        arc(30f, 54f, 24.5f, 24.5f, 190f, 110f).width(2.2f)
        arc(30f, 54f, 24.5f, 24.5f, 190f, 110f).stroke(frame).width(1.3f)
        arc(100f, 54f, 24.5f, 24.5f, 240f, 100f).width(2.2f)
        arc(100f, 54f, 24.5f, 24.5f, 240f, 100f).stroke(frame).width(1.3f)
        // zincir
        line(62f, 50.5f, 30f, 51f).stroke(C.GRAY_DARK).width(0.7f).dotted()
        line(62f, 61.5f, 30f, 57f).stroke(C.GRAY_DARK).width(0.7f).dotted()
        circle(30f, 54f, 3.4f).fill(C.STONE).width(0.6f)
        // kadro
        tube(frame, 2.2f, 30f, 54f, 62f, 56f)
        tube(frame, 2.2f, 30f, 54f, 55f, 28f)
        tube(frame, 2.4f, 62f, 56f, 54f, 25f)
        tube(frame, 2.4f, 55f, 28f, 92f, 27f)
        tube(frame, 2.6f, 62f, 56f, 94f, 33f)
        tube(frame, 2.2f, 95f, 32f, 98f, 44f, 100f, 54f)
        tube(C.WHITE, 3f, 91f, 22f, 95f, 34f)
        // sele
        tube(C.STONE, 1.4f, 54f, 25f, 52.5f, 19f)
        path(closed = true) { moveTo(43f, 17f); quadTo(50f, 14.5f, 59f, 16.5f); quadTo(60f, 19.5f, 55f, 20f); quadTo(47f, 19.5f, 43f, 17f) }.fill(C.BROWN_DARK)
        // gidon
        tube(C.STONE, 1.4f, 91f, 22f, 90f, 15f)
        tube(C.STONE, 1.4f, 90f, 15f, 84f, 13.5f)
        roundRect(78f, 11.8f, 7f, 3.4f, 1.7f).fill(C.BLACK)
        circle(88f, 13.5f, 1.3f).fill(C.YELLOW).width(0.5f)
        // sepet ve çiçekler
        blossom(104f, 16f, 5f, C.PINK, C.YELLOW, 5, 0f, 0.5f)
        blossom(111f, 18f, 4.4f, C.YELLOW, C.ORANGE, 5, 20f, 0.5f)
        leaf(100f, 19f, 7f, 2f, -30f, C.GREEN, vein = false)
        poly(96f, 20f, 118f, 20f, 115f, 34f, 99f, 34f, closed = true).fill(C.WOOD)
        for (k in 1..3) line(96f + k * 0.8f, 20f + k * 3.5f, 118f - k * 0.8f, 20f + k * 3.5f).stroke(C.BROWN).width(0.45f)
        for (k in 1..4) line(96f + k * 4.4f, 20f, 99f + k * 3.2f, 34f).stroke(C.BROWN).width(0.45f)
        roundRect(95f, 19f, 24f, 2.4f, 1.2f).fill(C.BROWN_LIGHT).width(0.6f)
        // far
        roundRect(93f, 35f, 5f, 3.4f, 1.2f).fill(C.YELLOW_LIGHT).width(0.6f)
        // aynakol ve pedallar
        circle(62f, 56f, 6f).fill(C.METAL).width(0.8f)
        circle(62f, 56f, 3.6f).fill(C.STONE).width(0.5f)
        tube(C.STONE_DARK, 1.2f, 62f, 56f, 57f, 47f)
        roundRect(53f, 45.5f, 7f, 2.6f, 1f).fill(C.BLACK).width(0.5f)
        tube(C.STONE_DARK, 1.2f, 62f, 56f, 67f, 65f)
        roundRect(64f, 64f, 7f, 2.6f, 1f).fill(C.BLACK).width(0.5f)
        circle(62f, 56f, 1.2f).fill(C.GRAY_DARK).noStroke()
    }

    private fun airplane() = drawing("airplane", "Uçak", 140f, 90f, listOf("uçak", "yolcu uçağı", "gökyüzü", "seyahat", "airplane")) {
        cloud(20f, 76f, 26f, 10f)
        cloud(118f, 72f, 26f, 10f)
        cloud(100f, 12f, 18f, 7f)
        // uzak kanat ve uzak yatay kuyruk
        poly(70f, 38f, 90f, 22f, 97f, 22f, 86f, 38f, closed = true).fill(C.STONE)
        poly(116f, 36f, 128f, 28f, 133f, 28f, 128f, 36f, closed = true).fill(C.STONE)
        // dikey kuyruk
        path(closed = true) { moveTo(106f, 36f); lineTo(121f, 8f); lineTo(131f, 8f); quadTo(131f, 22f, 129f, 32f) }.fill(C.BLUE)
        poly(116f, 17f, 130.5f, 17f, 130f, 21f, 114f, 21f, closed = true).fill(C.WHITE).noStroke()
        star(123.5f, 13f, 2.6f, 1.1f).fill(C.YELLOW).width(0.5f)
        // gövde
        path(closed = true) {
            moveTo(12f, 46f); cubicTo(12f, 38f, 21f, 34.5f, 32f, 34.5f); lineTo(104f, 34.5f)
            cubicTo(116f, 34.5f, 126f, 31f, 135f, 29f); lineTo(135f, 36f)
            cubicTo(124f, 46f, 112f, 56f, 100f, 56f); lineTo(30f, 56f); cubicTo(19f, 56f, 12f, 52f, 12f, 46f)
        }.fill("#F4F6FA")
        path(closed = true) { moveTo(15f, 50f); cubicTo(20f, 54f, 24f, 56f, 30f, 56f); lineTo(100f, 56f); cubicTo(108f, 56f, 116f, 51f, 122f, 46f); lineTo(118f, 46f); cubicTo(112f, 50f, 106f, 52f, 100f, 52f); lineTo(22f, 52f) }
            .fill(C.SHADOW).noStroke().alpha(0.08f)
        path(closed = true) { moveTo(22f, 48f); lineTo(104f, 48f); cubicTo(112f, 47f, 120f, 42f, 126f, 37f); lineTo(129f, 38f); cubicTo(122f, 45f, 112f, 51f, 104f, 51f); lineTo(24f, 51f) }
            .fill(C.BLUE).width(0.5f)
        // pencereler
        path(closed = true) { moveTo(18f, 41f); quadTo(21f, 37.5f, 27.5f, 37.5f); lineTo(28f, 42f); lineTo(17.5f, 43f) }.fill(C.NAVY).width(0.6f)
        shine(22f, 39.5f, 2f, 0.7f, -15f, 0.5f)
        roundRect(31f, 37.5f, 5f, 11f, 1.8f).fill("#F4F6FA").width(0.6f)
        circle(33.5f, 41f, 0.9f).fill(C.GLASS_DARK).noStroke()
        var wx = 40f
        while (wx < 102f) { roundRect(wx, 39f, 3f, 4f, 1.4f).fill(C.GLASS_DARK).width(0.5f); wx += 5.6f }
        // yakın kanat ve motor
        path(closed = true) { moveTo(58f, 50f); lineTo(94f, 50f); lineTo(80f, 80f); lineTo(72f, 80f) }.fill("#DDE3EA")
        line(90f, 52f, 78f, 78f).stroke(C.STONE_DARK).width(0.5f)
        path(closed = true) { moveTo(72f, 80f); lineTo(80f, 80f); lineTo(82f, 84f); lineTo(76f, 84f) }.fill(C.BLUE).width(0.7f)
        roundRect(58f, 58f, 22f, 9f, 4.5f).fill(C.STONE_LIGHT)
        ellipse(59f, 62.5f, 2.4f, 4.5f).fill(C.GRAY_DARK).width(0.6f)
        rect(76f, 59f, 4f, 7f).fill(C.STONE).width(0.5f)
        shine(68f, 60f, 6f, 1f, 0f, 0.6f)
        // yakın yatay kuyruk
        poly(114f, 40f, 132f, 38f, 138f, 42f, 122f, 44f, closed = true).fill("#DDE3EA")
    }

    private fun sailboat() = drawing("sailboat", "Yelkenli", 100f, 100f, listOf("yelkenli", "tekne", "deniz", "yelken", "sailboat")) {
        circle(84f, 16f, 8f).fill(C.YELLOW)
        path { moveTo(12f, 20f); quadTo(14.5f, 17f, 17f, 20f); quadTo(19.5f, 17f, 22f, 20f) }.width(0.6f)
        path { moveTo(24f, 28f); quadTo(26f, 25.5f, 28f, 28f); quadTo(30f, 25.5f, 32f, 28f) }.width(0.6f)
        // direk ve bayrak
        rect(49f, 8f, 2.6f, 60f).fill(C.BROWN_DARK).width(0.7f)
        poly(51f, 3f, 60f, 5.5f, 51f, 8.5f, closed = true).fill(C.RED).width(0.6f)
        line(50.3f, 3f, 50.3f, 8f).width(0.6f)
        // ana yelken (çizgili)
        fun leech(y: Float): Float {
            var best = 52f; var bestD = Float.MAX_VALUE
            for (i in 0..60) {
                val t = i / 60f; val m = 1 - t
                val py = m * m * 12f + 2 * m * t * 36f + t * t * 64f
                val px = m * m * 52f + 2 * m * t * 76f + t * t * 88f
                val d = kotlin.math.abs(py - y)
                if (d < bestD) { bestD = d; best = px }
            }
            return best
        }
        path(closed = true) { moveTo(52f, 12f); quadTo(76f, 36f, 88f, 64f); lineTo(52f, 64f) }.fill(C.WHITE)
        for ((y1, y2) in listOf(40f to 46f, 52f to 58f)) {
            val pts = ArrayList<Float>()
            pts += listOf(52f, y1)
            for (k in 0..4) { val y = y1 + (y2 - y1) * k / 4f; pts += listOf(leech(y), y) }
            pts += listOf(52f, y2)
            poly(*pts.toFloatArray(), closed = true).fill(C.RED).width(0f)
        }
        path { moveTo(52f, 12f); quadTo(76f, 36f, 88f, 64f) }
        for (y in listOf(22f, 31f)) line(52f, y, leech(y) - 0.5f, y + 1f).stroke(C.STONE).width(0.45f)
        // flok yelkeni
        path(closed = true) { moveTo(48f, 14f); quadTo(36f, 40f, 24f, 62f); lineTo(47.5f, 62f) }.fill("#FDE3C3")
        line(30f, 55f, 47.5f, 55f).stroke(C.ORANGE).width(0.5f)
        line(36f, 44f, 47.5f, 44f).stroke(C.ORANGE).width(0.5f)
        // gövde
        path(closed = true) { moveTo(14f, 66f); lineTo(90f, 66f); quadTo(86f, 80f, 74f, 83f); lineTo(30f, 83f); quadTo(20f, 79f, 14f, 66f) }.fill(C.NAVY)
        path(closed = true) { moveTo(15.5f, 69f); lineTo(88.6f, 69f); quadTo(88f, 71f, 87.3f, 72f); lineTo(17f, 72f) }.fill(C.WHITE).noStroke()
        rect(12f, 64f, 80f, 2.6f).fill(C.WOOD).width(0.7f)
        for (x in listOf(34f, 46f, 58f, 70f)) {
            circle(x, 76.5f, 2.2f).fill(C.GLASS).width(0.6f)
            circle(x - 0.6f, 75.9f, 0.6f).fill(C.WHITE).noStroke()
        }
        // dalgalar
        path(closed = true) {
            moveTo(0f, 82f)
            var x = 0f
            while (x < 100f) { quadTo(x + 5f, 78.5f, x + 10f, 82f); x += 10f }
            lineTo(100f, 99f); lineTo(0f, 99f)
        }.fill(C.WATER)
        path(closed = true) {
            moveTo(0f, 90f)
            var x = 0f
            while (x < 100f) { quadTo(x + 6f, 86.5f, x + 12.5f, 90f); x += 12.5f }
            lineTo(100f, 99f); lineTo(0f, 99f)
        }.fill(C.WATER_DARK).noStroke().alpha(0.5f)
        for ((x, y) in listOf(10f to 88f, 38f to 93f, 70f to 89f, 88f to 95f)) path { moveTo(x, y); quadTo(x + 3f, y - 2f, x + 6f, y) }.stroke(C.WHITE).width(0.6f)
    }

    private fun train() = drawing("train", "Buharlı tren", 150f, 90f, listOf("tren", "lokomotif", "ray", "buhar", "train")) {
        // raylar
        rect(0f, 80f, 150f, 3f).fill(C.STONE_DARK).width(0.7f)
        var sx = 2f
        while (sx < 150f) { rect(sx, 83f, 8f, 3f).fill(C.BROWN).width(0.5f); sx += 13f }
        // duman
        cloud(56f, 9f, 20f, 9f, shading = false)
        cloud(76f, 4f, 14f, 6f, shading = false)
        puff(40f, 17f, 3.4f)
        // baca ve kubbeler
        poly(37f, 20f, 49f, 20f, 47f, 25f, 39f, 25f, closed = true).fill(C.GRAY_DARK)
        rect(39.5f, 25f, 7f, 11f).fill(C.GRAY_DARK)
        path(closed = true) { moveTo(60f, 36f); quadTo(60f, 27f, 66f, 27f); quadTo(72f, 27f, 72f, 36f) }.fill(C.YELLOW)
        path(closed = true) { moveTo(79f, 36f); quadTo(79f, 30f, 83f, 30f); quadTo(87f, 30f, 87f, 36f) }.fill(C.RED_DARK)
        line(83f, 30f, 83f, 26f).width(0.7f)
        // kazan ve kabin
        rect(30f, 35f, 70f, 24f).fill(C.RED)
        rect(30f, 35f, 70f, 5f).fill(C.WHITE).noStroke().alpha(0.2f)
        for (x in listOf(52f, 68f, 84f)) rect(x, 35f, 2.2f, 24f).fill(C.YELLOW).width(0.5f)
        rect(26f, 34f, 12f, 25f).fill(C.GRAY_DARK)
        circle(32f, 46.5f, 3f).fill(C.STONE).width(0.6f)
        line(40f, 41f, 98f, 41f).stroke(C.METAL).width(0.8f)
        rect(96f, 22f, 32f, 40f).fill(C.RED_DARK)
        roundRect(92f, 17f, 40f, 6f, 2f).fill(C.GRAY_DARK)
        roundRect(102f, 28f, 16f, 13f, 1.5f).fill(C.YELLOW_LIGHT).width(0.8f)
        line(110f, 28f, 110f, 41f).width(0.6f)
        path(closed = true) { moveTo(106f, 41f); quadTo(106f, 36f, 109f, 36f); quadTo(112f, 36f, 112f, 41f) }.fill(C.NAVY).width(0.5f)
        circle(109f, 34f, 2f).fill(C.SAND).width(0.5f)
        roundRect(106.3f, 31.3f, 5.4f, 1.8f, 0.6f).fill(C.BLUE).width(0.4f)
        rect(100f, 48f, 24f, 2f).fill(C.YELLOW).width(0.5f)
        // far
        rect(28f, 27f, 8f, 7f).fill(C.GRAY_DARK)
        circle(28f, 30.5f, 2.6f).fill(C.YELLOW_LIGHT).width(0.6f)
        poly(26f, 30.5f, 6f, 24f, 6f, 37f, closed = true).fill(C.YELLOW).noStroke().alpha(0.3f)
        // şasi, silindir, mahmuz
        rect(22f, 59f, 108f, 5f).fill(C.BLACK)
        roundRect(32f, 61f, 16f, 9f, 2f).fill(C.STONE).width(0.8f)
        path(closed = true) { moveTo(22f, 62f); lineTo(22f, 78f); lineTo(10f, 78f) }.fill(C.RED)
        for (k in 1..3) line(22f - k * 3f, 78f - k * 1.3f, 22f, 62f + k * 3.7f).stroke(C.RED_DARK).width(0.5f)
        // tekerlekler ve biyel
        fun VectorBuilder.driver(cx: Float, cy: Float, r: Float) {
            circle(cx, cy, r).fill(C.TIRE)
            circle(cx, cy, r * 0.78f).fill(C.RED).width(0.6f)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                line(cx, cy, cx + r * 0.75f * cos(a).toFloat(), cy + r * 0.75f * sin(a).toFloat()).stroke(C.RED_DARK).width(0.5f)
            }
            circle(cx, cy, r * 0.22f).fill(C.METAL).width(0.5f)
        }
        driver(40f, 74f, 6f)
        driver(60f, 71f, 9f)
        driver(82f, 71f, 9f)
        driver(104f, 71f, 9f)
        driver(122f, 74f, 6f)
        roundRect(58f, 74f, 48f, 2.4f, 1.2f).fill(C.METAL).width(0.6f)
        for (x in listOf(60f, 82f, 104f)) circle(x, 75.2f, 1.2f).fill(C.GRAY_DARK).noStroke()
        line(48f, 65.5f, 60f, 75f).stroke(C.METAL).width(1f)
    }

    private fun helicopter() = drawing("helicopter", "Helikopter", 140f, 90f, listOf("helikopter", "pervane", "hava", "taşıt", "helicopter")) {
        val paint = C.YELLOW
        // rotor
        roundRect(8f, 9f, 118f, 3.2f, 1.6f).fill(C.GRAY_DARK)
        path { moveTo(14f, 5f); quadTo(40f, 1f, 66f, 3f) }.width(0.5f)
        path { moveTo(74f, 3f); quadTo(100f, 1f, 122f, 5f) }.width(0.5f)
        rect(64f, 11f, 6f, 10f).fill(C.STONE).width(0.8f)
        roundRect(61f, 8f, 12f, 4.6f, 2f).fill(C.STONE_DARK)
        // kuyruk
        path(closed = true) { moveTo(88f, 34f); lineTo(126f, 34f); lineTo(126f, 40f); lineTo(90f, 48f) }.fill(paint)
        rect(104f, 35.5f, 20f, 2.5f).fill(C.RED).noStroke()
        path(closed = true) { moveTo(120f, 34f); lineTo(126f, 18f); lineTo(133f, 18f); lineTo(131f, 40f); lineTo(126f, 40f) }.fill(paint)
        circle(131f, 25f, 7.5f).fill(C.STONE_LIGHT).alpha(0.5f).width(0.5f)
        group(rotate = 35f, px = 131f, py = 25f) { roundRect(124f, 24f, 14f, 2f, 1f).fill(C.GRAY_DARK).width(0.6f) }
        group(rotate = -55f, px = 131f, py = 25f) { roundRect(124f, 24f, 14f, 2f, 1f).fill(C.GRAY_DARK).width(0.6f) }
        circle(131f, 25f, 1.6f).fill(C.STONE).width(0.5f)
        circle(128f, 17f, 1.3f).fill(C.RED).width(0.5f)
        // kızaklar
        line(40f, 58f, 36f, 70f).width(1.4f)
        line(78f, 57f, 82f, 70f).width(1.4f)
        path(closed = true) { moveTo(20f, 69f); lineTo(96f, 69f); quadTo(101f, 69f, 102f, 64f); lineTo(104f, 64.5f); quadTo(103f, 72f, 96f, 72f); lineTo(20f, 72f); quadTo(18f, 70.5f, 20f, 69f) }
            .fill(C.GRAY_DARK)
        // gövde
        smooth(18f, 44f, 26f, 28f, 46f, 21f, 76f, 22f, 92f, 33f, 94f, 49f, 84f, 58f, 44f, 60f, 24f, 55f, closed = true).fill(paint)
        path(closed = true) { moveTo(20f, 50f); lineTo(93.5f, 45f); lineTo(93f, 49f); quadTo(90f, 56f, 84f, 58f); lineTo(44f, 60f); quadTo(28f, 59f, 22f, 54f) }
            .fill(C.RED).width(0.6f)
        smooth(20f, 43f, 26f, 30f, 38f, 24.5f, 52f, 24f, 52f, 42f, 36f, 44f, closed = true).fill(C.GLASS).width(0.8f)
        poly(28f, 40f, 36f, 27f, 41f, 26f, 33f, 40.5f, closed = true).fill(C.WHITE).noStroke().alpha(0.5f)
        roundRect(58f, 28f, 16f, 13f, 3f).fill(C.GLASS).width(0.7f)
        poly(61f, 41f, 66f, 28f, 69f, 28f, 64f, 41f, closed = true).fill(C.WHITE).noStroke().alpha(0.45f)
        line(55f, 25f, 55f, 57f).width(0.6f)
        line(77f, 25f, 78f, 56f).width(0.6f)
        roundRect(70f, 44f, 5f, 1.8f, 0.9f).fill(C.GRAY_DARK).width(0.4f)
        roundRect(76f, 18f, 10f, 5f, 2f).fill(C.STONE).width(0.7f)
        circle(94f, 40f, 1.4f).fill(C.RED).width(0.4f)
    }

    private fun rocket() = drawing("rocket", "Roket", 70f, 100f, listOf("roket", "uzay", "yıldız", "gezegen", "rocket")) {
        // yıldızlar ve gezegen
        for ((x, y, r) in listOf(Triple(8f, 10f, 2.4f), Triple(60f, 24f, 2f), Triple(12f, 48f, 1.6f), Triple(62f, 56f, 1.8f))) star(x, y, r, r * 0.42f).fill(C.YELLOW).width(0.5f)
        circle(58f, 8f, 5f).fill(C.PURPLE_LIGHT).width(0.7f)
        arc(58f, 8f, 9f, 2.2f, 0f, 180f).stroke(C.PURPLE).width(1.2f)
        // alevler ve duman
        path(closed = true) { moveTo(25f, 80f); quadTo(26f, 92f, 35f, 99f); quadTo(44f, 92f, 45f, 80f) }.fill(C.RED)
        path(closed = true) { moveTo(28f, 80f); quadTo(29f, 89f, 35f, 95f); quadTo(41f, 89f, 42f, 80f) }.fill(C.ORANGE).noStroke()
        path(closed = true) { moveTo(31f, 80f); quadTo(32f, 86f, 35f, 90f); quadTo(38f, 86f, 39f, 80f) }.fill(C.YELLOW).noStroke()
        cloud(15f, 92f, 20f, 9f, shading = false)
        cloud(56f, 92f, 20f, 9f, shading = false)
        // kanatçıklar (arkada)
        path(closed = true) { moveTo(23f, 56f); quadTo(12f, 64f, 10f, 80f); lineTo(14f, 82f); quadTo(18f, 76f, 24f, 74f) }.fill(C.RED)
        path(closed = true) { moveTo(47f, 56f); quadTo(58f, 64f, 60f, 80f); lineTo(56f, 82f); quadTo(52f, 76f, 46f, 74f) }.fill(C.RED)
        // gövde
        path(closed = true) { moveTo(22f, 26f); lineTo(48f, 26f); quadTo(51f, 50f, 47f, 74f); lineTo(23f, 74f); quadTo(19f, 50f, 22f, 26f) }.fill("#EEF2F7")
        path(closed = true) { moveTo(38f, 26f); lineTo(48f, 26f); quadTo(51f, 50f, 47f, 74f); lineTo(40f, 74f); quadTo(42f, 50f, 38f, 26f) }.fill(C.SHADOW).noStroke().alpha(0.08f)
        path(closed = true) { moveTo(35f, 5f); quadTo(46f, 12f, 48f, 26f); lineTo(22f, 26f); quadTo(24f, 12f, 35f, 5f) }.fill(C.RED)
        shine(30f, 16f, 1.6f, 5f, 25f, 0.45f)
        rect(21.6f, 25f, 26.8f, 3.2f).fill(C.NAVY).width(0.7f)
        rect(22.4f, 64f, 25.6f, 3f).fill(C.NAVY).width(0.6f)
        // pencere
        circle(35f, 41f, 7.5f).fill(C.METAL)
        circle(35f, 41f, 5.3f).fill(C.GLASS_DARK).width(0.7f)
        path(closed = true) { moveTo(31f, 40f); arcTo(35f, 41f, 4f, 4f, 190f, 80f); lineTo(35f, 39f) }.fill(C.WHITE).noStroke().alpha(0.6f)
        for (k in 0 until 8) {
            val a = Math.toRadians(k * 45.0 + 22.5)
            circle(35f + 6.4f * cos(a).toFloat(), 41f + 6.4f * sin(a).toFloat(), 0.45f).fill(C.STONE_DARK).noStroke()
        }
        for (y in listOf(52f, 58f)) for (x in listOf(25f, 45f)) circle(x, y, 0.5f).fill(C.STONE_DARK).noStroke()
        line(35f, 50f, 35f, 63f).stroke(C.STONE).width(0.5f)
        // orta kanatçık ve lüle
        roundRect(33f, 60f, 4f, 22f, 2f).fill(C.RED_DARK)
        poly(26f, 74f, 44f, 74f, 46f, 80f, 24f, 80f, closed = true).fill(C.GRAY_DARK)
    }
}
