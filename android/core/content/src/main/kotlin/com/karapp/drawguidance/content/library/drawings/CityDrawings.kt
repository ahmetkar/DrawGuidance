package com.karapp.drawguidance.content.library.drawings

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** "Ev ve şehir" hazır çizimleri. */
internal object CityDrawings {

    val shapes: List<LibShape> by lazy {
        listOf(cottage(), apartment(), castle(), lighthouse(), shop(), bridge(), tent(), windmill(), skyline())
    }

    // ---- ortak parçalar ----

    /** Kiremit sırası: [y] hizasında [x0]..[x1] arası aşağı sarkan yarım daireler. */
    private fun VectorBuilder.tileRow(x0: Float, x1: Float, y: Float, tile: Float, color: String) {
        val n = ((x1 - x0) / tile).toInt().coerceAtLeast(1)
        val tw = (x1 - x0) / n
        path {
            moveTo(x0, y)
            for (i in 0 until n) arcTo(x0 + tw * (i + 0.5f), y, tw / 2f, tw * 0.42f, 180f, -180f)
        }.stroke(color).width(0.55f)
    }

    /** Sivri uçlu çit tahtaları + kirişler. */
    private fun VectorBuilder.fence(x0: Float, x1: Float, top: Float, bottom: Float, board: Float = 4f, gap: Float = 1.6f) {
        rect(x0, top + 4f, x1 - x0, 2.4f).fill(C.WALL_DARK).width(0.6f)
        rect(x0, bottom - 6f, x1 - x0, 2.4f).fill(C.WALL_DARK).width(0.6f)
        var x = x0 + 0.5f
        while (x + board <= x1 + 0.01f) {
            poly(x, bottom, x, top + 2.4f, x + board / 2f, top, x + board, top + 2.4f, x + board, bottom, closed = true)
                .fill(C.WHITE).width(0.7f)
            x += board + gap
        }
    }

    /** Kemerli kapı/pencere yolu (alt kenar [bottom], kemer merkezi [cx],[archY]). */
    private fun VectorBuilder.archShape(cx: Float, halfW: Float, archY: Float, bottom: Float) = path(closed = true) {
        moveTo(cx - halfW, bottom)
        lineTo(cx - halfW, archY)
        arcTo(cx, archY, halfW, halfW, 180f, 180f)
        lineTo(cx + halfW, bottom)
    }

    private fun VectorBuilder.bush(cx: Float, cy: Float, rx: Float, ry: Float, flowers: String? = null) {
        puffy(cx, cy, rx, ry, 7, 0.34f).fill(C.GREEN)
        puffy(cx - rx * 0.2f, cy - ry * 0.25f, rx * 0.45f, ry * 0.4f, 5, 0.3f).fill(C.GREEN_LIGHT).noStroke().alpha(0.8f)
        if (flowers != null) {
            circle(cx - rx * 0.45f, cy + ry * 0.05f, rx * 0.12f).fill(flowers).width(0.5f)
            circle(cx + rx * 0.15f, cy - ry * 0.35f, rx * 0.12f).fill(flowers).width(0.5f)
            circle(cx + rx * 0.55f, cy + ry * 0.2f, rx * 0.12f).fill(flowers).width(0.5f)
        }
    }

    // ---- çizimler ----

    private fun cottage() = drawing("house_cottage", "Kır evi", 120f, 100f, listOf("ev", "kır evi", "çatı", "baca", "çit", "house")) {
        shadow(60f, 92f, 57f, 6f, 0.12f)
        roundRect(3f, 85f, 114f, 11f, 5f).fill(C.GREEN_LIGHT)
        poly(49f, 90f, 61f, 90f, 67f, 98.5f, 43f, 98.5f, closed = true).fill(C.SAND).width(0.7f)
        // baca ve duman (çatının arkasında)
        rect(74f, 20f, 10f, 22f).fill(C.BRICK)
        line(74f, 26f, 84f, 26f).stroke(C.RED_DARK).width(0.5f)
        line(74f, 32f, 84f, 32f).stroke(C.RED_DARK).width(0.5f)
        line(79f, 20f, 79f, 26f).stroke(C.RED_DARK).width(0.5f)
        line(77f, 26f, 77f, 32f).stroke(C.RED_DARK).width(0.5f)
        rect(72.5f, 17f, 13f, 4f).fill(C.GRAY_DARK)
        circle(80f, 12.5f, 3f).fill("#E9EEF4").width(0.6f)
        circle(86f, 8f, 3.8f).fill("#E9EEF4").width(0.6f)
        circle(93.5f, 5f, 4.2f).fill("#E9EEF4").width(0.6f)
        // çit (evin iki yanında)
        fence(2f, 24f, 72f, 90f)
        fence(90f, 118f, 72f, 90f)
        // duvar
        rect(22f, 46f, 66f, 42f).fill(C.WALL)
        for (y in listOf(55f, 64f, 73f, 82f)) line(22f, y, 88f, y).stroke(C.WALL_DARK).width(0.5f)
        rect(22f, 46f, 66f, 7f).fill(C.SHADOW).noStroke().alpha(0.1f)
        rect(80f, 46f, 8f, 42f).fill(C.SHADOW).noStroke().alpha(0.06f)
        // çatı
        poly(11f, 50f, 55f, 15f, 99f, 50f, closed = true).fill(C.RED)
        poly(55f, 15f, 99f, 50f, 55f, 50f, closed = true).fill(C.SHADOW).noStroke().alpha(0.1f)
        var y = 23f
        while (y < 48f) {
            val half = (y - 15f) * 44f / 35f
            tileRow(55f - half + 1f, 55f + half - 1f, y, 6f, C.RED_DARK)
            y += 5f
        }
        roundRect(9f, 48.5f, 92f, 4f, 1.5f).fill(C.RED_DARK)
        circle(55f, 34f, 5.5f).fill(C.WHITE)
        circle(55f, 34f, 4f).fill(C.GLASS).width(0.6f)
        line(55f, 30f, 55f, 38f).stroke(C.WHITE).width(0.8f)
        line(51f, 34f, 59f, 34f).stroke(C.WHITE).width(0.8f)
        // pencereler
        window(28f, 58f, 15f, 16f, curtain = C.PINK)
        window(67f, 58f, 15f, 16f, curtain = C.PINK)
        roundRect(65.5f, 75.8f, 18f, 4.2f, 1f).fill(C.BROWN)
        for ((i, fx) in listOf(68f, 72f, 76f, 80f).withIndex()) {
            circle(fx, 74.6f, 1.7f).fill(if (i % 2 == 0) C.PINK_DARK else C.YELLOW).width(0.5f)
        }
        // kapı
        archShape(55f, 10f, 69f, 88f).fill(C.WHITE)
        archShape(55f, 8f, 69f, 88f).fill(C.TEAL)
        path(closed = true) { moveTo(49f, 69f); arcTo(55f, 69f, 6f, 6f, 180f, 180f) }.fill(C.GLASS).width(0.6f)
        line(55f, 63f, 55f, 69f).stroke(C.WHITE).width(0.6f)
        roundRect(50f, 72f, 10f, 6f, 1f).fill(C.SHADOW).alpha(0.12f).width(0.5f)
        roundRect(50f, 80f, 10f, 6f, 1f).fill(C.SHADOW).alpha(0.12f).width(0.5f)
        circle(60.2f, 79f, 1.2f).fill(C.YELLOW).width(0.5f)
        // basamaklar
        rect(43f, 88f, 24f, 2.6f).fill(C.STONE_LIGHT).width(0.7f)
        rect(41f, 90.6f, 28f, 2.6f).fill(C.STONE).width(0.7f)
        // çalılar ve çiçekler
        bush(23f, 83f, 10f, 6.5f, C.PINK_DARK)
        bush(88f, 83f, 10f, 6.5f, C.YELLOW)
        tuft(8f, 94f, 6f, 5f, C.GREEN)
        tuft(106f, 95f, 6f, 5f, C.GREEN)
        tuft(34f, 96f, 5f, 4f, C.GREEN)
        tuft(80f, 96f, 5f, 4f, C.GREEN)
    }

    private fun apartment() = drawing("apartment", "Apartman", 80f, 100f, listOf("apartman", "bina", "şehir", "balkon", "building")) {
        shadow(40f, 95.5f, 38f, 3f, 0.14f)
        // çatı üstü
        rect(17f, 5f, 12f, 8f).fill(C.METAL)
        ellipse(23f, 5f, 6f, 1.8f).fill(C.STONE_LIGHT).width(0.7f)
        line(20f, 7.5f, 26f, 7.5f).stroke(C.STONE_DARK).width(0.5f)
        rect(44f, 7f, 12f, 6f).fill(C.STONE).width(0.8f)
        rect(46f, 8.5f, 4f, 3f).fill(C.GRAY_DARK).width(0.5f)
        line(62f, 1f, 62f, 12f).width(0.8f)
        line(58f, 3.5f, 66f, 3.5f).width(0.7f)
        line(59f, 6.5f, 65f, 6.5f).width(0.7f)
        // gövde
        rect(9f, 12f, 62f, 4.5f).fill("#C9785D")
        rect(12f, 16f, 56f, 78f).fill("#F4B183")
        rect(60f, 16f, 8f, 78f).fill(C.SHADOW).noStroke().alpha(0.08f)
        for (k in 1..4) rect(12f, 16f + 14f * k - 0.8f, 56f, 1.6f).fill("#E39B6B").width(0.4f)
        val lit = setOf(0 to 0, 2 to 1, 1 to 2, 0 to 3, 2 to 3)
        for (row in 0 until 4) for (col in 0 until 3) {
            val wx = 16f + col * 18f
            val wy = 19f + row * 14f
            val glass = if ((col to row) in lit) C.YELLOW_LIGHT else C.GLASS
            val curtain = if ((col + row) % 3 == 0) C.PURPLE_LIGHT else null
            if (col == 1 && row % 2 == 1) {
                // balkon kapısı + balkon
                window(wx + 1f, wy - 1f, 10f, 11f, glass = glass, cols = 2, rows = 1, curtain = curtain, sill = false)
                rect(wx - 3f, wy + 10f, 18f, 1.8f).fill(C.STONE).width(0.7f)
                line(wx - 3f, wy + 4.5f, wx + 15f, wy + 4.5f).width(0.8f)
                var bx = wx - 2.5f
                while (bx <= wx + 14.6f) { line(bx, wy + 4.5f, bx, wy + 10f).width(0.5f); bx += 2.2f }
                poly(wx + 11f, wy + 6.5f, wx + 14f, wy + 6.5f, wx + 13.5f, wy + 10f, wx + 11.5f, wy + 10f, closed = true).fill(C.BRICK).width(0.5f)
                puffy(wx + 12.5f, wy + 5.3f, 2.4f, 1.7f, 5, 0.3f).fill(C.GREEN).width(0.5f)
            } else {
                window(wx, wy, 12f, 9f, glass = glass, cols = 2, rows = 1, curtain = curtain)
            }
        }
        // zemin kat
        rect(12f, 72f, 56f, 22f).fill("#E9967A")
        for (k in 0..3) line(12f, 76.5f + k * 4.5f, 68f, 76.5f + k * 4.5f).stroke("#D07F63").width(0.4f)
        window(15f, 78f, 12f, 11f, glass = C.GLASS, cols = 1, rows = 1, sill = false)
        window(53f, 78f, 12f, 11f, glass = C.YELLOW_LIGHT, cols = 1, rows = 1, sill = false)
        rect(31f, 77f, 18f, 17f).fill(C.WHITE)
        rect(32.5f, 78.5f, 7f, 15.5f).fill(C.GLASS_DARK).width(0.6f)
        rect(40.5f, 78.5f, 7f, 15.5f).fill(C.GLASS_DARK).width(0.6f)
        poly(33.5f, 93f, 36.5f, 79.5f, 38f, 79.5f, 35f, 93f, closed = true).fill(C.WHITE).noStroke().alpha(0.35f)
        poly(41.5f, 93f, 44.5f, 79.5f, 46f, 79.5f, 43f, 93f, closed = true).fill(C.WHITE).noStroke().alpha(0.35f)
        line(38.5f, 84f, 38.5f, 88f).stroke(C.GRAY_DARK).width(1f)
        line(41.5f, 84f, 41.5f, 88f).stroke(C.GRAY_DARK).width(1f)
        poly(28f, 72.5f, 52f, 72.5f, 54f, 77f, 26f, 77f, closed = true).fill(C.TEAL)
        for (k in 0 until 7) {
            val sx = 26f + k * 4f
            path { moveTo(sx, 77f); arcTo(sx + 2f, 77f, 2f, 1.4f, 180f, -180f) }.fill(C.TEAL).width(0.6f)
        }
        roundRect(51f, 80f, 5f, 3f, 0.6f).fill(C.NAVY).width(0.5f)
        circle(29f, 82f, 1.4f).fill(C.YELLOW_LIGHT).width(0.5f)
        circle(51f, 86f, 1.4f).fill(C.YELLOW_LIGHT).width(0.5f)
        // kaldırım ve çalılar
        rect(3f, 94f, 74f, 3.5f).fill(C.STONE_LIGHT).width(0.7f)
        rect(28f, 92.5f, 24f, 1.8f).fill(C.STONE).width(0.6f)
        bush(10f, 91f, 7f, 4.5f)
        bush(70f, 91f, 7f, 4.5f, C.PINK_DARK)
    }

    private fun castle() = drawing("castle", "Kale", 120f, 100f, listOf("kale", "şato", "kule", "masal", "castle")) {
        path(closed = true) { moveTo(2f, 97f); quadTo(60f, 80f, 118f, 97f); lineTo(118f, 99f); lineTo(2f, 99f) }.fill(C.GREEN)
        // merkez kule (arkada)
        rect(44f, 24f, 32f, 28f).fill(C.STONE)
        for (i in 0 until 5) rect(44f + i * 7f, 19f, 4f, 5.5f).fill(C.STONE)
        path(closed = true) { moveTo(56f, 42f); lineTo(56f, 35f); arcTo(60f, 35f, 4f, 4f, 180f, 180f); lineTo(64f, 42f) }.fill(C.NAVY).width(0.7f)
        line(60f, 4f, 60f, 19f).width(0.8f)
        poly(60f, 4f, 73f, 7.5f, 60f, 11f, closed = true).fill(C.RED).width(0.7f)
        // yan kuleler
        for (tx in listOf(8f, 90f)) {
            val cx = tx + 11f
            rect(tx, 36f, 22f, 55f).fill(C.STONE)
            rect(tx + 15f, 36f, 7f, 55f).fill(C.SHADOW).noStroke().alpha(0.08f)
            for (k in 0..4) line(tx, 46f + k * 9f, tx + 22f, 46f + k * 9f).stroke(C.STONE_DARK).width(0.45f)
            path(closed = true) { moveTo(cx - 3f, 60f); lineTo(cx - 3f, 53f); arcTo(cx, 53f, 3f, 3f, 180f, 180f); lineTo(cx + 3f, 60f) }
                .fill(C.NAVY).width(0.7f)
            rect(tx - 2f, 34f, 26f, 3f).fill(C.STONE_DARK)
            poly(tx - 3f, 34.5f, cx, 9f, tx + 25f, 34.5f, closed = true).fill(C.BLUE_DARK)
            poly(cx, 9f, tx + 25f, 34.5f, cx + 2f, 34.5f, closed = true).fill(C.SHADOW).noStroke().alpha(0.15f)
            for (k in 1..3) line(cx, 9f, tx - 3f + k * 7f, 34.5f).stroke(C.NAVY).width(0.5f)
            line(cx, 9f, cx, 1.5f).width(0.8f)
            poly(cx, 1.5f, cx + 8f, 3.8f, cx, 6f, closed = true).fill(C.YELLOW).width(0.6f)
        }
        // ana sur
        rect(30f, 50f, 60f, 41f).fill("#C9CED6")
        for (i in 0 until 7) rect(31f + i * 8.5f, 44.5f, 5f, 5.5f).fill("#C9CED6")
        rect(30f, 50f, 60f, 3f).fill(C.SHADOW).noStroke().alpha(0.1f)
        val gateL = 47f; val gateR = 73f
        var row = 0
        var yy = 57f
        while (yy < 91f) {
            if (yy < 62f) line(30f, yy, 90f, yy).stroke(C.STONE_DARK).width(0.45f)
            else { line(30f, yy, gateL, yy).stroke(C.STONE_DARK).width(0.45f); line(gateR, yy, 90f, yy).stroke(C.STONE_DARK).width(0.45f) }
            var jx = 33f + (row % 2) * 5f
            while (jx < 90f) {
                if (jx < gateL - 0.5f || jx > gateR + 0.5f || yy - 6f < 58f) line(jx, yy - 6f, jx, yy).stroke(C.STONE_DARK).width(0.45f)
                jx += 10f
            }
            yy += 6f; row++
        }
        // kapı: kemer, parmaklık
        archShape(60f, 13f, 72f, 91f).fill(C.STONE_LIGHT)
        for (k in 0..8) {
            val a = Math.toRadians(180.0 + k * 22.5)
            line(60f + 11f * cos(a).toFloat(), 72f + 11f * sin(a).toFloat(), 60f + 13f * cos(a).toFloat(), 72f + 13f * sin(a).toFloat()).width(0.5f)
        }
        archShape(60f, 11f, 72f, 91f).fill("#3B2B22")
        for (x in listOf(52f, 56f, 60f, 64f, 68f)) {
            val top = 72f - sqrt((121f - (x - 60f) * (x - 60f)).coerceAtLeast(0f))
            line(x, top, x, 91f).stroke("#9C8A73").width(0.8f)
        }
        for (hy in listOf(67f, 73f, 79f, 85f)) {
            val half = if (hy >= 72f) 11f else sqrt(121f - (72f - hy) * (72f - hy))
            line(60f - half, hy, 60f + half, hy).stroke("#9C8A73").width(0.8f)
        }
        // sancaklar
        for (bx in listOf(34f, 78f)) {
            line(bx - 1f, 55f, bx + 9f, 55f).width(0.8f)
            poly(bx, 55f, bx + 8f, 55f, bx + 8f, 72f, bx + 4f, 68f, bx, 72f, closed = true).fill(C.RED).width(0.7f)
            circle(bx + 4f, 61f, 2.2f).fill(C.YELLOW).width(0.5f)
        }
        // asma köprü
        poly(47f, 91f, 73f, 91f, 78f, 99f, 42f, 99f, closed = true).fill(C.WOOD).width(0.8f)
        for (k in 1..3) line(47f - k * 1.25f, 91f + k * 2f, 73f + k * 1.25f, 91f + k * 2f).stroke(C.BROWN).width(0.5f)
        line(48f, 72f, 43f, 98f).stroke(C.GRAY_DARK).width(0.6f).dashed()
        line(72f, 72f, 77f, 98f).stroke(C.GRAY_DARK).width(0.6f).dashed()
        tuft(12f, 96f, 6f, 5f)
        tuft(108f, 96f, 6f, 5f)
        tuft(94f, 98f, 5f, 4f)
    }

    private fun lighthouse() = drawing("lighthouse", "Deniz feneri", 80f, 100f, listOf("deniz feneri", "fener", "deniz", "kayalık", "lighthouse")) {
        // ışık huzmeleri
        poly(40f, 18f, 1f, 5f, 1f, 30f, closed = true).fill(C.YELLOW).noStroke().alpha(0.35f)
        poly(40f, 18f, 79f, 5f, 79f, 30f, closed = true).fill(C.YELLOW).noStroke().alpha(0.35f)
        // martılar
        path { moveTo(8f, 42f); quadTo(11f, 38f, 14f, 42f); quadTo(17f, 38f, 20f, 42f) }.width(0.7f)
        path { moveTo(60f, 48f); quadTo(62f, 45.5f, 64f, 48f); quadTo(66f, 45.5f, 68f, 48f) }.width(0.7f)
        // kule: kırmızı-beyaz bantlar
        fun xl(y: Float) = 40f - (8f + (y - 30f) / 54f * 6f)
        fun xr(y: Float) = 40f + (8f + (y - 30f) / 54f * 6f)
        val bands = listOf(30f, 43.5f, 57f, 70.5f, 84f)
        for (i in 0 until 4) {
            val y1 = bands[i]; val y2 = bands[i + 1]
            poly(xl(y1), y1, xr(y1), y1, xr(y2), y2, xl(y2), y2, closed = true).fill(if (i % 2 == 0) C.RED else C.WHITE).width(0f)
        }
        poly(40f, 30f, xr(30f), 30f, xr(84f), 84f, 40f + 5f, 84f, closed = true).fill(C.SHADOW).noStroke().alpha(0.1f)
        poly(xl(84f), 84f, xl(30f), 30f, xr(30f), 30f, xr(84f), 84f)
        for (y in bands.subList(1, 4)) line(xl(y), y, xr(y), y).width(0.6f)
        roundRect(37.8f, 46f, 4.4f, 6.5f, 2.2f).fill(C.NAVY).width(0.6f)
        roundRect(37.8f, 60f, 4.4f, 6.5f, 2.2f).fill(C.NAVY).width(0.6f)
        archShape(40f, 4f, 78f, 84f).fill(C.BROWN).width(0.8f)
        circle(42.4f, 81f, 0.6f).fill(C.YELLOW).noStroke()
        // fener odası, galeri, çatı
        rect(33f, 13f, 14f, 13f).fill(C.YELLOW_LIGHT)
        circle(40f, 19.5f, 4f).fill(C.YELLOW).width(0.6f)
        shine(38.5f, 18f, 1.4f, 1f)
        line(37.7f, 13f, 37.7f, 26f).width(0.6f)
        line(42.3f, 13f, 42.3f, 26f).width(0.6f)
        path(closed = true) { moveTo(30.5f, 13.5f); quadTo(40f, 1f, 49.5f, 13.5f) }.fill(C.RED)
        rect(30f, 12.5f, 20f, 2.2f).fill(C.RED_DARK).width(0.7f)
        circle(40f, 5.3f, 1.6f).fill(C.GRAY_DARK).width(0.6f)
        line(40f, 3.7f, 40f, 0.8f).width(0.7f)
        rect(26f, 26f, 28f, 4f).fill(C.GRAY_DARK)
        line(26.5f, 20.5f, 53.5f, 20.5f).width(0.8f)
        line(26.5f, 23.2f, 53.5f, 23.2f).width(0.5f)
        var px = 27f
        while (px <= 53.2f) { line(px, 20.5f, px, 26f).width(0.55f); px += 3.25f }
        // kayalar
        smooth(12f, 88f, 16f, 80f, 26f, 78f, 34f, 83f, 36f, 90f, 22f, 92f, closed = true).fill(C.STONE)
        smooth(44f, 86f, 50f, 80f, 60f, 79f, 70f, 84f, 72f, 91f, 56f, 93f, closed = true).fill(C.STONE_DARK)
        smooth(28f, 90f, 34f, 84f, 44f, 83f, 52f, 88f, 50f, 93f, 36f, 94f, closed = true).fill(C.STONE)
        shine(22f, 82f, 4f, 1.4f, -15f, 0.4f)
        shine(38f, 86f, 4f, 1.4f, -10f, 0.4f)
        // deniz
        path(closed = true) {
            moveTo(0f, 90f)
            var x = 0f
            while (x < 80f) { quadTo(x + 5f, 87f, x + 10f, 90f); x += 10f }
            lineTo(80f, 99f); lineTo(0f, 99f)
        }.fill(C.WATER)
        for ((wx, wy) in listOf(8f to 94f, 30f to 96f, 54f to 94f, 70f to 97f, 20f to 98f)) {
            path { moveTo(wx, wy); quadTo(wx + 3f, wy - 2f, wx + 6f, wy) }.stroke(C.WHITE).width(0.6f)
        }
    }

    private fun shop() = drawing("shop", "Dükkan", 110f, 100f, listOf("dükkan", "mağaza", "pastane", "tente", "vitrin", "shop")) {
        shadow(55f, 95f, 54f, 4f, 0.12f)
        rect(0f, 92f, 110f, 6.5f).fill(C.STONE_LIGHT).width(0.8f)
        for (k in 1..5) line(k * 18.3f, 92f, k * 18.3f - 2f, 98.5f).stroke(C.STONE).width(0.5f)
        // cephe
        rect(8f, 16f, 94f, 76f).fill("#F7D9C4")
        for (k in 0..5) line(8f, 52f + k * 7.5f, 102f, 52f + k * 7.5f).stroke("#EFC6AA").width(0.4f)
        rect(4f, 11f, 102f, 5.5f).fill("#C9785D")
        rect(6f, 8f, 98f, 3.5f).fill("#B5654C").width(0.8f)
        // tabela
        roundRect(24f, 19f, 62f, 13f, 2.5f).fill(C.NAVY)
        roundRect(26f, 21f, 58f, 9f, 1.8f).fill(C.NAVY).stroke(C.YELLOW_LIGHT).width(0.45f)
        roundRect(49f, 23.3f, 9f, 5.4f, 1.6f).fill(C.WHITE).width(0.5f)
        arc(58.2f, 26f, 1.8f, 1.6f, -90f, 180f).stroke(C.WHITE).width(0.6f)
        path { moveTo(51.5f, 22.5f); quadTo(50.5f, 21.3f, 51.8f, 20.3f) }.stroke(C.WHITE).width(0.45f)
        path { moveTo(55f, 22.5f); quadTo(54f, 21.3f, 55.3f, 20.3f) }.stroke(C.WHITE).width(0.45f)
        for (dx in listOf(32f, 38f, 44f, 64f, 70f, 76f)) circle(dx, 25.5f, 1.1f).fill(C.YELLOW).noStroke()
        // tente
        rect(9f, 34f, 92f, 2.6f).fill(C.GRAY_DARK).width(0.8f)
        val n = 9
        for (i in 0 until n) {
            val tx0 = 12f + 86f * i / n; val tx1 = 12f + 86f * (i + 1) / n
            val bx0 = 6f + 98f * i / n; val bx1 = 6f + 98f * (i + 1) / n
            path(closed = true) {
                moveTo(tx0, 36.5f); lineTo(tx1, 36.5f); lineTo(bx1, 46f)
                arcTo((bx0 + bx1) / 2f, 46f, (bx1 - bx0) / 2f, 3.2f, 0f, 180f)
            }.fill(if (i % 2 == 0) C.RED else C.WHITE).width(0.7f)
        }
        rect(8f, 47f, 94f, 3f).fill(C.SHADOW).noStroke().alpha(0.1f)
        // vitrin
        rect(13f, 54f, 48f, 32f).fill(C.WHITE)
        rect(15.5f, 56.5f, 43f, 27f).fill("#E3F4FB").width(0.6f)
        rect(15.5f, 70f, 43f, 1.6f).fill(C.BROWN_LIGHT).width(0.5f)
        // üst raf: pasta, kekler
        rect(19f, 63f, 12f, 7f).fill(C.PINK).width(0.6f)
        path(closed = true) { moveTo(18.5f, 63.5f); quadTo(25f, 58f, 31.5f, 63.5f) }.fill(C.CREAM).width(0.6f)
        circle(25f, 59.7f, 1.3f).fill(C.RED).width(0.5f)
        for (cx in listOf(37f, 44f)) {
            poly(cx - 3f, 66f, cx + 3f, 66f, cx + 2.2f, 70f, cx - 2.2f, 70f, closed = true).fill(C.YELLOW).width(0.5f)
            puffy(cx, 64.6f, 3.4f, 2.2f, 5, 0.25f).fill(if (cx < 40f) C.PINK else C.BROWN_LIGHT).width(0.5f)
        }
        ellipse(52.5f, 67f, 4.5f, 3f).fill(C.WOOD).width(0.6f)
        for (k in 0..2) line(50f + k * 2.3f, 65.2f, 51.2f + k * 2.3f, 68.8f).stroke(C.BROWN).width(0.4f)
        // alt raf: ekmek, kurabiye kavanozu
        ellipse(24f, 79.5f, 6.5f, 3.5f).fill(C.WOOD).width(0.6f)
        for (k in 0..2) path { moveTo(20.5f + k * 3f, 78f); quadTo(21.5f + k * 3f, 79.5f, 21f + k * 3f, 81.5f) }.stroke(C.BROWN).width(0.4f)
        roundRect(34f, 74f, 8f, 9f, 1.5f).fill("#DDF1F8").width(0.6f)
        rect(33.5f, 72.8f, 9f, 2f).fill(C.RED).width(0.5f)
        circle(37f, 78f, 1.4f).fill(C.BROWN_LIGHT).width(0.4f)
        circle(39.5f, 80.5f, 1.4f).fill(C.BROWN_LIGHT).width(0.4f)
        ellipse(51f, 81f, 6f, 2.2f).fill(C.ORANGE).width(0.6f)
        ellipse(51f, 79.8f, 5f, 1.2f).fill(C.YELLOW_LIGHT).width(0.4f)
        poly(18f, 83.5f, 28f, 56.5f, 33f, 56.5f, 23f, 83.5f, closed = true).fill(C.WHITE).noStroke().alpha(0.35f)
        rect(11f, 86f, 52f, 6f).fill("#C9785D")
        // kapı
        rect(68f, 51f, 26f, 41f).fill(C.WHITE)
        rect(70.5f, 53.5f, 21f, 38.5f).fill(C.TEAL)
        rect(73.5f, 56.5f, 15f, 17f).fill(C.GLASS).width(0.6f)
        poly(75f, 73f, 80f, 56.5f, 83f, 56.5f, 78f, 73f, closed = true).fill(C.WHITE).noStroke().alpha(0.35f)
        roundRect(73.5f, 77f, 15f, 11f, 1f).fill(C.SHADOW).alpha(0.12f).width(0.5f)
        roundRect(88.5f, 71f, 1.8f, 7f, 0.9f).fill(C.YELLOW).width(0.5f)
        line(78f, 56.5f, 81f, 60.5f).width(0.4f)
        line(84f, 56.5f, 81f, 60.5f).width(0.4f)
        roundRect(76.5f, 60.5f, 9f, 5.5f, 1f).fill(C.WHITE).width(0.5f)
        line(78.3f, 62.5f, 83.7f, 62.5f).stroke(C.RED).width(0.5f)
        line(78.3f, 64.2f, 82.3f, 64.2f).stroke(C.GRAY_DARK).width(0.4f)
        // lamba
        line(98f, 55f, 98f, 58f).width(0.7f)
        path { moveTo(98f, 56.5f); lineTo(95.5f, 56.5f) }.width(0.7f)
        poly(93.5f, 57f, 97.5f, 57f, 97f, 62f, 94f, 62f, closed = true).fill(C.YELLOW_LIGHT).width(0.6f)
        rect(93f, 55.8f, 5f, 1.4f).fill(C.GRAY_DARK).width(0.5f)
        // saksı bitkisi
        poly(97f, 84f, 105f, 84f, 104f, 92f, 98f, 92f, closed = true).fill(C.BRICK).width(0.7f)
        rect(96.5f, 83f, 9f, 2f).fill(C.RED_DARK).width(0.5f)
        puffy(101f, 78f, 5.5f, 5.5f, 7, 0.3f).fill(C.GREEN)
        circle(99f, 76f, 1.2f).fill(C.PINK).width(0.4f)
        circle(103f, 79f, 1.2f).fill(C.YELLOW).width(0.4f)
    }

    private fun bridge() = drawing("bridge", "Taş köprü", 140f, 100f, listOf("köprü", "nehir", "taş", "kemer", "bridge")) {
        // su ve kıyılar
        rect(0f, 70f, 140f, 28f).fill(C.WATER)
        rect(0f, 70f, 140f, 3f).fill(C.WATER_DARK).noStroke().alpha(0.35f)
        poly(0f, 48f, 12f, 48f, 26f, 86f, 16f, 98f, 0f, 98f, closed = true).fill(C.GREEN)
        poly(140f, 48f, 128f, 48f, 114f, 86f, 124f, 98f, 140f, 98f, closed = true).fill(C.GREEN)
        ellipse(70f, 83f, 24f, 2.6f).fill(C.WATER_DARK).noStroke().alpha(0.45f)
        ellipse(22f, 83f, 7f, 1.6f).fill(C.WATER_DARK).noStroke().alpha(0.45f)
        ellipse(118f, 83f, 7f, 1.6f).fill(C.WATER_DARK).noStroke().alpha(0.45f)
        // gövde (kemer boşluklarıyla)
        path(closed = true) {
            moveTo(4f, 42f); lineTo(136f, 42f); lineTo(136f, 80f); lineTo(127f, 80f)
            arcTo(118f, 80f, 9f, 9f, 0f, -180f)
            lineTo(98f, 80f)
            arcTo(70f, 80f, 28f, 28f, 0f, -180f)
            lineTo(31f, 80f)
            arcTo(22f, 80f, 9f, 9f, 0f, -180f)
            lineTo(4f, 80f)
        }.fill(C.STONE)
        rect(4f, 42f, 132f, 4f).fill(C.SHADOW).noStroke().alpha(0.1f)
        // taş sıraları ve derzler (kemer halkalarının dışında)
        fun inRing(x: Float, y: Float): Boolean {
            fun d(cx: Float) = sqrt((x - cx) * (x - cx) + (y - 80f) * (y - 80f))
            return d(70f) < 33.5f || d(22f) < 12.5f || d(118f) < 12.5f
        }
        var row = 0
        var yy = 50f
        while (yy <= 78f) {
            var x = 4f
            while (x < 136f) {
                val x2 = (x + 1f).coerceAtMost(136f)
                if (!inRing(x, yy) && !inRing(x2, yy)) line(x, yy, x2, yy).stroke(C.STONE_DARK).width(0.45f)
                x += 1f
            }
            var jx = 9f + (row % 2) * 6f
            while (jx < 136f) {
                if (!inRing(jx, yy) && !inRing(jx, yy - 8f)) line(jx, yy - 8f, jx, yy).stroke(C.STONE_DARK).width(0.45f)
                jx += 12f
            }
            yy += 8f; row++
        }
        for ((cx, r0, r1, n) in listOf(Quad(70f, 28f, 33f, 13), Quad(22f, 9f, 12f, 7), Quad(118f, 9f, 12f, 7))) {
            arc(cx, 80f, r1, r1, 180f, 180f).width(0.6f)
            for (k in 1 until n) {
                val a = Math.toRadians(180.0 + 180.0 * k / n)
                line(cx + r0 * cos(a).toFloat(), 80f + r0 * sin(a).toFloat(), cx + r1 * cos(a).toFloat(), 80f + r1 * sin(a).toFloat()).width(0.5f)
            }
        }
        // korkuluk
        rect(4f, 40f, 132f, 2.5f).fill(C.STONE_DARK).width(0.7f)
        var bx = 6f
        while (bx < 134f) { roundRect(bx, 34.5f, 2.6f, 6f, 1f).fill(C.STONE_LIGHT).width(0.5f); bx += 5.2f }
        roundRect(2f, 32f, 136f, 3f, 1.5f).fill(C.STONE_LIGHT).width(0.8f)
        // fenerler
        for (lx in listOf(8f, 70f, 132f)) {
            rect(lx - 0.9f, 17f, 1.8f, 15f).fill(C.GRAY_DARK).width(0.6f)
            rect(lx - 2.5f, 30f, 5f, 2f).fill(C.GRAY_DARK).width(0.6f)
            poly(lx - 3f, 11f, lx + 3f, 11f, lx + 2f, 17f, lx - 2f, 17f, closed = true).fill(C.YELLOW_LIGHT).width(0.7f)
            poly(lx - 3.8f, 11f, lx, 7.5f, lx + 3.8f, 11f, closed = true).fill(C.GRAY_DARK).width(0.6f)
            circle(lx, 14f, 1.4f).fill(C.YELLOW).noStroke()
        }
        // dalgalar ve ördekler
        for ((wx, wy) in listOf(6f to 88f, 34f to 92f, 60f to 94f, 92f to 90f, 104f to 95f, 128f to 90f, 80f to 96f)) {
            path { moveTo(wx, wy); quadTo(wx + 3f, wy - 2f, wx + 6f, wy) }.stroke(C.WHITE).width(0.6f)
        }
        for ((dx, dy) in listOf(96f to 88f, 106f to 91f)) {
            path(closed = true) { moveTo(dx - 4f, dy - 1f); quadTo(dx - 3f, dy + 2.5f, dx + 3f, dy + 1.8f); quadTo(dx + 4f, dy - 1f, dx + 1f, dy - 1.2f); lineTo(dx - 4f, dy - 1f) }
                .fill(C.WHITE).width(0.6f)
            circle(dx + 2f, dy - 2.8f, 1.8f).fill(C.WHITE).width(0.6f)
            poly(dx + 3.6f, dy - 3.2f, dx + 5.6f, dy - 2.6f, dx + 3.6f, dy - 2f, closed = true).fill(C.ORANGE).width(0.4f)
            circle(dx + 2.4f, dy - 3.2f, 0.35f).fill(C.BLACK).noStroke()
        }
        tuft(6f, 60f, 6f, 5f, C.GREEN_DARK)
        tuft(134f, 60f, 6f, 5f, C.GREEN_DARK)
        tuft(10f, 96f, 6f, 5f, C.GREEN_DARK)
        tuft(130f, 96f, 6f, 5f, C.GREEN_DARK)
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val n: Int)

    private fun tent() = drawing("tent", "Kamp çadırı", 120f, 100f, listOf("çadır", "kamp", "ateş", "doğa", "tent")) {
        // gökyüzü süsleri
        path(closed = true) { moveTo(21.5f, 5.94f); arcTo(18f, 12f, 7f, 7f, -60f, -240f); arcTo(25f, 12f, 7f, 7f, 120f, 120f) }
            .fill(C.YELLOW_LIGHT).width(0.7f)
        star(40f, 8f, 2.4f, 1f).fill(C.YELLOW).width(0.5f)
        star(88f, 6f, 1.8f, 0.8f).fill(C.YELLOW).width(0.5f)
        star(30f, 20f, 1.6f, 0.7f).fill(C.YELLOW).width(0.5f)
        // zemin
        ellipse(60f, 91f, 58f, 8f).fill(C.GREEN_LIGHT)
        // çam (arkada)
        rect(106f, 70f, 3.5f, 14f).fill(C.BROWN)
        poly(96f, 72f, 107.7f, 48f, 119f, 72f, closed = true).fill(C.GREEN_DARK)
        poly(98f, 60f, 107.7f, 38f, 117f, 60f, closed = true).fill(C.GREEN_DARK)
        poly(100.5f, 48f, 107.7f, 30f, 115f, 48f, closed = true).fill(C.GREEN_DARK)
        // çadır
        shadow(76f, 88f, 42f, 4f, 0.15f)
        poly(64f, 24f, 100f, 20f, 116f, 82f, 98f, 87f, closed = true).fill(C.ORANGE_DARK)
        line(66f, 30f, 98f, 83f).stroke(C.YELLOW_LIGHT).width(0.5f).dashed()
        line(100f, 20f, 116f, 82f).width(0.8f)
        poly(30f, 87f, 64f, 24f, 98f, 87f, closed = true).fill(C.ORANGE)
        poly(64f, 24f, 98f, 87f, 64f, 87f, closed = true).fill(C.SHADOW).noStroke().alpha(0.06f)
        line(34f, 83f, 63f, 30f).stroke(C.YELLOW_LIGHT).width(0.5f).dashed()
        line(94f, 83f, 65f, 30f).stroke(C.YELLOW_LIGHT).width(0.5f).dashed()
        poly(51f, 87f, 64f, 46f, 77f, 87f, closed = true).fill("#5A3B2E")
        poly(56f, 87f, 64f, 60f, 72f, 87f, closed = true).fill(C.SHADOW).noStroke().alpha(0.25f)
        path(closed = true) { moveTo(64f, 46f); lineTo(51f, 87f); lineTo(42f, 87f); quadTo(50f, 70f, 64f, 46f) }.fill("#F7B267").width(0.8f)
        path(closed = true) { moveTo(64f, 46f); lineTo(77f, 87f); lineTo(86f, 87f); quadTo(78f, 70f, 64f, 46f) }.fill("#F7B267").width(0.8f)
        line(64f, 24f, 64f, 46f).width(0.7f)
        roundRect(46f, 69f, 5f, 2f, 1f).fill(C.BROWN).width(0.5f)
        roundRect(77f, 69f, 5f, 2f, 1f).fill(C.BROWN).width(0.5f)
        line(64f, 24f, 64f, 17f).width(1.2f)
        line(100f, 20f, 100f, 12f).width(0.9f)
        poly(100f, 12f, 108f, 14.5f, 100f, 17f, closed = true).fill(C.RED).width(0.6f)
        // halatlar ve kazıklar
        line(64f, 24f, 24f, 92f).stroke(C.BROWN_DARK).width(0.5f)
        line(100f, 20f, 118f, 60f).stroke(C.BROWN_DARK).width(0.5f)
        line(23f, 90f, 25f, 94f).stroke(C.GRAY_DARK).width(1f)
        // kamp ateşi
        val fx = 15f; val fy = 90f
        ellipse(fx, fy + 1f, 11f, 3f).fill(C.SHADOW).noStroke().alpha(0.15f)
        roundRect(fx - 9f, fy - 1.5f, 18f, 3.5f, 1.7f).fill(C.BROWN).width(0.7f)
        group(rotate = 25f, px = fx, py = fy) { roundRect(fx - 9f, fy - 1.7f, 18f, 3.5f, 1.7f).fill(C.BROWN_LIGHT).width(0.7f) }
        group(rotate = -25f, px = fx, py = fy) { roundRect(fx - 9f, fy - 1.7f, 18f, 3.5f, 1.7f).fill(C.BROWN).width(0.7f) }
        path(closed = true) {
            moveTo(fx - 6f, fy)
            quadTo(fx - 8f, fy - 9f, fx - 2f, fy - 16f)
            quadTo(fx - 2f, fy - 10f, fx + 1f, fy - 9f)
            quadTo(fx + 1f, fy - 14f, fx + 4f, fy - 18f)
            quadTo(fx + 9f, fy - 9f, fx + 6f, fy)
        }.fill(C.RED).width(0.7f)
        path(closed = true) {
            moveTo(fx - 4f, fy)
            quadTo(fx - 5f, fy - 7f, fx - 1f, fy - 11f)
            quadTo(fx, fy - 7f, fx + 2f, fy - 7f)
            quadTo(fx + 3f, fy - 11f, fx + 4f, fy - 12f)
            quadTo(fx + 7f, fy - 6f, fx + 4f, fy)
        }.fill(C.ORANGE).noStroke()
        path(closed = true) { moveTo(fx - 2f, fy); quadTo(fx - 2.5f, fy - 5f, fx + 1f, fy - 8f); quadTo(fx + 4f, fy - 4f, fx + 2.5f, fy) }
            .fill(C.YELLOW).noStroke()
        for ((sx, sy) in listOf(fx - 5f to fy - 20f, fx + 6f to fy - 22f, fx + 1f to fy - 25f)) circle(sx, sy, 0.7f).fill(C.ORANGE).noStroke()
        for (k in 0 until 7) {
            val a = Math.toRadians(20.0 + 140.0 * k / 6)
            ellipse(fx + 10f * cos(a).toFloat(), fy + 1.5f + 2.8f * sin(a).toFloat(), 2.2f, 1.5f).fill(C.STONE).width(0.6f)
        }
        tuft(40f, 95f, 6f, 5f, C.GREEN)
        tuft(92f, 96f, 6f, 5f, C.GREEN)
    }

    private fun windmill() = drawing("windmill", "Yel değirmeni", 100f, 100f, listOf("yel değirmeni", "değirmen", "kır", "windmill")) {
        cloud(18f, 14f, 24f, 11f)
        cloud(86f, 60f, 18f, 8f)
        path(closed = true) { moveTo(0f, 96f); quadTo(40f, 78f, 100f, 90f); lineTo(100f, 99f); lineTo(0f, 99f) }.fill(C.GREEN_LIGHT)
        path(closed = true) { moveTo(44f, 88f); lineTo(56f, 88f); quadTo(62f, 94f, 70f, 99f); lineTo(52f, 99f); quadTo(48f, 93f, 44f, 88f) }
            .fill(C.SAND).width(0.7f)
        // kule
        poly(33f, 88f, 67f, 88f, 60f, 36f, 40f, 36f, closed = true).fill(C.WALL)
        poly(50f, 36f, 60f, 36f, 67f, 88f, 50f, 88f, closed = true).fill(C.SHADOW).noStroke().alpha(0.08f)
        for (k in 0 until 6) {
            val y = 44f + k * 8f
            val half = 10f + (y - 36f) / 52f * 7f
            line(50f - half, y, 50f + half, y).stroke(C.WALL_DARK).width(0.45f)
            val off = if (k % 2 == 0) -4f else 4f
            line(50f + off, y - 8f, 50f + off, y).stroke(C.WALL_DARK).width(0.45f)
        }
        archShape(50f, 5.5f, 79f, 88f).fill(C.BROWN)
        for (dx in listOf(-2.8f, 0f, 2.8f)) line(50f + dx, 76f, 50f + dx, 88f).stroke(C.BROWN_DARK).width(0.45f)
        circle(52.8f, 83f, 0.6f).fill(C.YELLOW).noStroke()
        window(44.5f, 55f, 7f, 8f, cols = 1, rows = 2, sill = true)
        rect(41.5f, 55f, 3f, 8f).fill(C.RED).width(0.5f)
        rect(51.5f, 55f, 3f, 8f).fill(C.RED).width(0.5f)
        circle(50f, 45.5f, 2.6f).fill(C.GLASS).width(0.6f)
        // başlık
        rect(37f, 34.5f, 26f, 3.2f).fill(C.BROWN).width(0.8f)
        path(closed = true) { moveTo(38f, 35f); quadTo(50f, 12f, 62f, 35f) }.fill(C.RED_DARK)
        path(closed = true) { moveTo(50f, 23.5f); quadTo(58f, 26f, 62f, 35f); lineTo(52f, 35f) }.fill(C.SHADOW).noStroke().alpha(0.12f)
        // kanatlar
        for (a in listOf(30f, 120f, 210f, 300f)) {
            group(rotate = a, px = 50f, py = 29f) {
                rect(51f, 1f, 8f, 23f).fill(C.CREAM).width(0.7f)
                for (k in 1..5) line(51f, 1f + k * 3.8f, 59f, 1f + k * 3.8f).stroke(C.BROWN).width(0.45f)
                line(55f, 1f, 55f, 24f).stroke(C.BROWN).width(0.45f)
                rect(49f, -1f, 2f, 30f).fill(C.BROWN_DARK).width(0.6f)
            }
        }
        circle(50f, 29f, 3.2f).fill(C.GRAY_DARK)
        circle(50f, 29f, 1.2f).fill(C.STONE).noStroke()
        // çiçekler, çimen
        for ((x, col) in listOf(22f to C.PINK_DARK, 28f to C.YELLOW, 74f to C.PURPLE, 81f to C.PINK_DARK)) {
            line(x, 92f, x, 86f).stroke(C.GREEN_DARK).width(0.6f)
            blossom(x, 85f, 3.2f, col, C.YELLOW_LIGHT, 5, 0f, 0.45f)
        }
        tuft(12f, 95f, 7f, 6f)
        tuft(90f, 94f, 7f, 6f)
        tuft(36f, 97f, 5f, 4f)
    }

    private fun skyline() = drawing("city_skyline", "Şehir manzarası", 140f, 100f, listOf("şehir", "bina", "gökdelen", "sokak", "city")) {
        circle(118f, 17f, 10f).fill(C.YELLOW)
        circle(118f, 17f, 13f).fill(C.YELLOW).noStroke().alpha(0.25f)
        cloud(26f, 14f, 26f, 11f)
        cloud(80f, 9f, 18f, 8f)
        // arka sıra (açık tonlar)
        rect(10f, 36f, 18f, 56f).fill("#C9D4EC").width(0.7f)
        rect(56f, 28f, 16f, 64f).fill("#D5CBEE").width(0.7f)
        rect(98f, 38f, 20f, 54f).fill("#C9D4EC").width(0.7f)
        for ((bx, by) in listOf(10f to 36f, 56f to 28f, 98f to 38f)) {
            var wy = by + 4f
            while (wy < 88f) { line(bx + 3f, wy, bx + (if (bx == 56f) 13f else 15f), wy).stroke(C.WHITE).width(0.6f); wy += 5f }
        }
        // ön sıra binalar
        data class B(val x: Float, val top: Float, val w: Float, val color: String, val cols: Int)
        val list = listOf(
            B(2f, 48f, 22f, "#6C8EBF", 3), B(24f, 32f, 20f, "#F2A65A", 2), B(44f, 20f, 22f, "#7D6CB5", 3),
            B(66f, 50f, 18f, "#E07A5F", 2), B(84f, 34f, 24f, "#4FA3A5", 3), B(108f, 54f, 26f, "#F4C95D", 3),
        )
        for ((i, b) in list.withIndex()) {
            rect(b.x, b.top, b.w, 92f - b.top).fill(b.color)
            rect(b.x + b.w * 0.72f, b.top, b.w * 0.28f, 92f - b.top).fill(C.SHADOW).noStroke().alpha(0.1f)
            when (i) {
                2 -> { rect(b.x + 6f, b.top - 6f, 10f, 6f).fill(b.color); line(b.x + 11f, b.top - 6f, b.x + 11f, b.top - 16f).width(0.8f); circle(b.x + 11f, b.top - 16.5f, 1f).fill(C.RED).width(0.5f) }
                1 -> poly(b.x - 1f, b.top, b.x + b.w / 2f, b.top - 8f, b.x + b.w + 1f, b.top, closed = true).fill("#C9785D")
                3 -> path(closed = true) { moveTo(b.x + 3f, b.top); quadTo(b.x + b.w / 2f, b.top - 10f, b.x + b.w - 3f, b.top) }.fill(C.TEAL_LIGHT)
                4 -> { rect(b.x + 3f, b.top - 5f, b.w - 6f, 5f).fill(b.color); rect(b.x + 7f, b.top - 9f, b.w - 14f, 4f).fill(b.color) }
                else -> rect(b.x - 1f, b.top - 2f, b.w + 2f, 2.5f).fill(C.GRAY_DARK).width(0.7f)
            }
            val cw = (b.w - 4f) / b.cols
            var wy = b.top + 4f
            var r = 0
            while (wy + 4f < 84f) {
                for (c in 0 until b.cols) {
                    val on = (i * 7 + r * 3 + c * 5) % 4 == 0
                    rect(b.x + 2f + c * cw + cw * 0.18f, wy, cw * 0.64f, 4f).fill(if (on) C.YELLOW_LIGHT else C.GLASS).width(0.5f)
                }
                wy += 7f; r++
            }
            rect(b.x + b.w / 2f - 2.5f, 85f, 5f, 7f).fill(C.GRAY_DARK).width(0.6f)
        }
        // sokak
        rect(0f, 91.5f, 140f, 2.5f).fill(C.STONE_LIGHT).width(0.7f)
        rect(0f, 94f, 140f, 5f).fill(C.GRAY_DARK)
        var dx = 4f
        while (dx < 140f) { line(dx, 96.5f, dx + 5f, 96.5f).stroke(C.WHITE).width(0.6f); dx += 10f }
        // ağaçlar
        for (tx in listOf(13f, 57f, 97f, 127f)) {
            rect(tx - 0.9f, 83f, 1.8f, 9f).fill(C.BROWN).width(0.6f)
            puffy(tx, 81f, 5f, 5f, 6, 0.3f).fill(C.GREEN)
            shine(tx - 1.5f, 79.5f, 1.8f, 1.2f, -20f, 0.35f)
        }
        // küçük araba
        roundRect(68f, 90.5f, 16f, 5f, 2f).fill(C.RED).width(0.7f)
        path(closed = true) { moveTo(71f, 90.8f); lineTo(73f, 87.5f); lineTo(79f, 87.5f); lineTo(81.5f, 90.8f) }.fill(C.GLASS).width(0.6f)
        circle(72f, 95.8f, 1.8f).fill(C.TIRE).width(0.6f)
        circle(80f, 95.8f, 1.8f).fill(C.TIRE).width(0.6f)
    }
}
