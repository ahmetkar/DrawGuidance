package com.karapp.drawguidance.content.library.drawings

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.cos
import kotlin.math.sin

/** "Eşyalar" hazır çizimleri. */
internal object ObjectDrawings {

    val shapes: List<LibShape> by lazy {
        listOf(chair(), deskLamp(), mug(), books(), alarmClock(), umbrella(), guitar(), laptop(), teapot())
    }

    private fun chair() = drawing("chair", "Sandalye", 80f, 100f, listOf("sandalye", "mobilya", "ahşap", "oturak", "chair")) {
        shadow(40f, 95f, 34f, 3.5f, 0.16f)
        // arka ayaklar ve sırtlık
        rect(23f, 60f, 4.5f, 33f).fill(C.BROWN)
        rect(52.5f, 60f, 4.5f, 33f).fill(C.BROWN)
        roundRect(19f, 10f, 6.5f, 50f, 2.5f).fill(C.WOOD)
        roundRect(54.5f, 10f, 6.5f, 50f, 2.5f).fill(C.WOOD)
        for (x in listOf(29.5f, 37.5f, 45.5f)) {
            roundRect(x, 18f, 5f, 36f, 2f).fill(C.WOOD).width(0.8f)
            line(x + 2.5f, 22f, x + 2.5f, 50f).stroke(C.BROWN_LIGHT).width(0.4f)
        }
        path(closed = true) { moveTo(17f, 14f); quadTo(40f, 5f, 63f, 14f); lineTo(63f, 21f); quadTo(40f, 13f, 17f, 21f) }.fill(C.WOOD)
        path { moveTo(22f, 15f); quadTo(40f, 9f, 58f, 15f) }.stroke(C.BROWN_LIGHT).width(0.45f)
        rect(24f, 44f, 32f, 4f).fill(C.WOOD).width(0.8f)
        roundRect(20f, 12.5f, 2f, 45f, 1f).fill(C.WHITE).noStroke().alpha(0.35f)
        // oturak ve minder
        poly(15f, 57f, 65f, 57f, 71f, 65f, 9f, 65f, closed = true).fill(C.WOOD)
        rect(9f, 65f, 62f, 6f).fill(C.BROWN_LIGHT)
        smooth(16f, 60f, 26f, 54f, 40f, 53f, 54f, 54f, 64f, 60f, 56f, 63.5f, 40f, 64f, 24f, 63.5f, closed = true).fill(C.TEAL)
        smooth(20f, 59.5f, 30f, 55.5f, 40f, 55f, 50f, 55.5f, 60f, 59.5f, 50f, 61.5f, 40f, 62f, 30f, 61.5f, closed = true).fill(C.TEAL).stroke(C.WHITE).width(0.4f).dashed()
        circle(40f, 58.5f, 1.2f).fill(C.TEAL_LIGHT).width(0.5f)
        // ön ayaklar ve bağlantı
        rect(15f, 83f, 50f, 3f).fill(C.BROWN).width(0.8f)
        roundRect(11f, 70f, 6.5f, 25f, 2f).fill(C.WOOD)
        roundRect(62.5f, 70f, 6.5f, 25f, 2f).fill(C.WOOD)
        rect(11f, 92f, 6.5f, 3f).fill(C.BROWN_DARK).width(0.6f)
        rect(62.5f, 92f, 6.5f, 3f).fill(C.BROWN_DARK).width(0.6f)
        line(14.2f, 73f, 14.2f, 89f).stroke(C.BROWN_LIGHT).width(0.4f)
        line(65.8f, 73f, 65.8f, 89f).stroke(C.BROWN_LIGHT).width(0.4f)
    }

    private fun deskLamp() = drawing("desk_lamp", "Masa lambası", 90f, 100f, listOf("lamba", "masa", "ışık", "çalışma", "lamp")) {
        val paint = C.TEAL
        // ışık huzmesi ve defter
        poly(66f, 50f, 82f, 44f, 90f, 92f, 48f, 92f, closed = true).fill(C.YELLOW).noStroke().alpha(0.28f)
        poly(56f, 86f, 86f, 86f, 88f, 93f, 54f, 93f, closed = true).fill(C.WHITE).width(0.7f)
        poly(56f, 86f, 71f, 86f, 71f, 93f, 54f, 93f, closed = true).fill(C.BLUE_LIGHT).width(0.7f)
        for (k in 1..3) line(73f, 86f + k * 1.8f, 85f + k * 0.4f, 86f + k * 1.8f).stroke(C.STONE).width(0.4f)
        rect(70.4f, 86f, 1.2f, 7f).fill(C.RED).noStroke()
        // taban
        shadow(30f, 94f, 22f, 3f, 0.16f)
        ellipse(30f, 90f, 19f, 5f).fill(paint)
        rect(11f, 86f, 38f, 4f).fill(paint).noStroke()
        ellipse(30f, 86f, 19f, 4.5f).fill(C.TEAL_LIGHT)
        roundRect(38f, 84f, 5f, 2.4f, 1f).fill(C.GRAY_DARK).width(0.5f)
        // kollar ve eklemler
        tube(paint, 3f, 28f, 86f, 42f, 56f)
        line(31f, 82f, 44f, 59f).stroke(C.STONE_DARK).width(0.5f).dashed()
        tube(paint, 3f, 42f, 56f, 62f, 32f)
        circle(28f, 85f, 3f).fill(C.METAL).width(0.7f)
        circle(42f, 56f, 3.4f).fill(C.METAL).width(0.7f)
        circle(42f, 56f, 1.2f).fill(C.STONE_DARK).noStroke()
        circle(62f, 32f, 3f).fill(C.METAL).width(0.7f)
        // başlık
        group(rotate = -32f, px = 66f, py = 34f) {
            roundRect(61f, 22f, 10f, 7f, 2f).fill(paint)
            poly(60f, 28f, 72f, 28f, 82f, 48f, 50f, 48f, closed = true).fill(paint)
            poly(66f, 28f, 72f, 28f, 82f, 48f, 68f, 48f, closed = true).fill(C.SHADOW).noStroke().alpha(0.12f)
            ellipse(66f, 48f, 16f, 3.4f).fill(C.YELLOW_LIGHT)
            circle(66f, 50f, 4f).fill(C.YELLOW).width(0.6f)
            shine(64.5f, 49f, 1.3f, 1f, 0f, 0.8f)
            poly(62f, 30f, 64.5f, 30f, 56f, 46f, 53.5f, 46f, closed = true).fill(C.WHITE).noStroke().alpha(0.3f)
        }
    }

    private fun mug() = drawing("mug", "Kupa ve kurabiye", 100f, 90f, listOf("kupa", "kahve", "çay", "kurabiye", "mug")) {
        val paint = "#E86A5B"
        // tabak
        ellipse(48f, 81f, 40f, 7.5f).fill(C.WHITE)
        ellipse(48f, 80f, 30f, 5f).fill(C.STONE_LIGHT).width(0.5f)
        // buhar
        for (x in listOf(38f, 47f, 56f)) smooth(x, 22f, x - 3f, 16f, x + 2f, 10f, x - 1f, 3f).stroke(C.STONE_DARK).width(0.7f)
        // kulp
        path(closed = true) {
            moveTo(68f, 38f); quadTo(86f, 36f, 86f, 53f); quadTo(86f, 70f, 68f, 68f); lineTo(68f, 62f)
            quadTo(79f, 63f, 79f, 53f); quadTo(79f, 43f, 68f, 44f)
        }.fill(paint)
        // gövde
        path(closed = true) { moveTo(24f, 28f); lineTo(24f, 72f); quadTo(24f, 80f, 32f, 80f); lineTo(62f, 80f); quadTo(70f, 80f, 70f, 72f); lineTo(70f, 28f) }.fill(paint)
        path(closed = true) { moveTo(58f, 28f); lineTo(70f, 28f); lineTo(70f, 72f); quadTo(70f, 80f, 62f, 80f); lineTo(58f, 80f) }.fill(C.SHADOW).noStroke().alpha(0.1f)
        roundRect(28f, 34f, 4f, 36f, 2f).fill(C.WHITE).noStroke().alpha(0.3f)
        for ((x, y) in listOf(38f to 46f, 52f to 42f, 46f to 58f, 60f to 56f, 36f to 68f, 56f to 70f)) heart(x, y, 2.6f).fill(C.WHITE).width(0.4f)
        ellipse(47f, 28f, 23f, 5f).fill("#F29A8E")
        ellipse(47f, 29f, 20f, 3.6f).fill(C.BROWN).width(0.6f)
        heart(47f, 29f, 2.2f).fill(C.CREAM).noStroke()
        // kaşık ve kurabiye
        group(rotate = 12f, px = 80f, py = 80f) {
            roundRect(70f, 79f, 16f, 1.8f, 0.9f).fill(C.METAL).width(0.6f)
            ellipse(89f, 80f, 4f, 2.4f).fill(C.METAL).width(0.6f)
        }
        circle(14f, 78f, 8f).fill(C.WOOD)
        circle(14f, 78f, 6.5f).fill(C.BROWN_LIGHT).noStroke().alpha(0.35f)
        for ((x, y) in listOf(11f to 75f, 16f to 74f, 13f to 80f, 18f to 79f, 10f to 82f)) circle(x, y, 1.1f).fill(C.BROWN_DARK).noStroke()
    }

    private fun books() = drawing("books", "Kitap yığını", 100f, 90f, listOf("kitap", "okul", "okuma", "kütüphane", "books")) {
        shadow(50f, 86f, 42f, 3.5f, 0.16f)
        data class Book(val x: Float, val y: Float, val w: Float, val h: Float, val color: String, val rot: Float)
        val list = listOf(
            Book(12f, 70f, 74f, 14f, C.RED, 0f),
            Book(18f, 57.5f, 64f, 12.5f, C.BLUE, -2f),
            Book(10f, 45.5f, 68f, 12f, C.GREEN, 1.5f),
            Book(22f, 35f, 60f, 10.5f, C.PURPLE, -1f),
        )
        for ((i, b) in list.withIndex()) {
            group(rotate = b.rot, px = b.x + b.w / 2f, py = b.y + b.h / 2f) {
                // sayfalar (sağda) ve kapak
                rect(b.x + b.w - 8f, b.y + 1.2f, 8f, b.h - 2.4f).fill(C.CREAM).width(0.7f)
                for (k in 1..3) line(b.x + b.w - 8f, b.y + 1.2f + (b.h - 2.4f) * k / 4f, b.x + b.w, b.y + 1.2f + (b.h - 2.4f) * k / 4f).stroke(C.WALL_DARK).width(0.4f)
                roundRect(b.x, b.y, b.w - 7f, b.h, 1.6f).fill(b.color)
                rect(b.x, b.y, b.w - 7f, b.h * 0.25f).fill(C.WHITE).noStroke().alpha(0.18f)
                rect(b.x + 4f, b.y, 2.2f, b.h).fill(C.YELLOW).width(0.5f)
                rect(b.x + b.w - 15f, b.y, 2.2f, b.h).fill(C.YELLOW).width(0.5f)
                roundRect(b.x + 14f, b.y + b.h * 0.28f, b.w * 0.4f, b.h * 0.44f, 1f).fill(C.CREAM).width(0.5f)
                line(b.x + 17f, b.y + b.h / 2f, b.x + 12f + b.w * 0.36f, b.y + b.h / 2f).stroke(C.STONE_DARK).width(0.45f)
                if (i == 1) path(closed = true) { moveTo(b.x + 30f, b.y + b.h); lineTo(b.x + 34f, b.y + b.h); lineTo(b.x + 34f, b.y + b.h + 9f); lineTo(b.x + 32f, b.y + b.h + 7f); lineTo(b.x + 30f, b.y + b.h + 9f) }
                    .fill(C.RED).width(0.5f)
            }
        }
        // elma
        path(closed = true) {
            moveTo(52f, 18f); cubicTo(58f, 13f, 68f, 16f, 67f, 26f); cubicTo(66f, 33f, 60f, 36f, 56f, 34.5f)
            quadTo(52f, 33.5f, 48f, 34.5f); cubicTo(44f, 36f, 38f, 33f, 37f, 26f); cubicTo(36f, 16f, 46f, 13f, 52f, 18f)
        }.fill(C.RED)
        shine(44f, 22f, 2f, 3.4f, 20f, 0.45f)
        ribbon(1.8f, 1.2f, 52f, 19f, 52.5f, 14f, 54.5f, 10f).fill(C.BROWN)
        leaf(59f, 12f, 9f, 2.8f, -20f, C.GREEN)
        // gözlük
        circle(20f, 29f, 4f).fill(C.GLASS).alpha(0.8f).width(0.8f)
        circle(30f, 29f, 4f).fill(C.GLASS).alpha(0.8f).width(0.8f)
        path { moveTo(24f, 28.5f); quadTo(25f, 27f, 26f, 28.5f) }.width(0.8f)
        line(16f, 28f, 12f, 33f).width(0.8f)
    }

    private fun alarmClock() = drawing("alarm_clock", "Çalar saat", 80f, 100f, listOf("saat", "çalar saat", "zaman", "sabah", "clock")) {
        shadow(40f, 94f, 26f, 3f, 0.16f)
        // çınlama çizgileri
        for (s in listOf(-1f, 1f)) {
            path { moveTo(40f + s * 31f, 14f); quadTo(40f + s * 35f, 18f, 40f + s * 35f, 24f) }.width(0.7f)
            path { moveTo(40f + s * 34f, 9f); quadTo(40f + s * 40f, 15f, 40f + s * 40f, 23f) }.width(0.7f)
        }
        // zil ve çekiç
        for (s in listOf(-1f, 1f)) {
            group(rotate = s * 35f, px = 40f + s * 18f, py = 22f) {
                path(closed = true) { moveTo(40f + s * 18f - 10f, 26f); quadTo(40f + s * 18f - 10f, 14f, 40f + s * 18f, 14f); quadTo(40f + s * 18f + 10f, 14f, 40f + s * 18f + 10f, 26f) }
                    .fill(C.YELLOW)
                roundRect(40f + s * 18f - 11f, 25f, 22f, 3f, 1.5f).fill(C.ORANGE).width(0.7f)
                circle(40f + s * 18f, 13f, 1.8f).fill(C.ORANGE).width(0.6f)
                shine(40f + s * 18f - 4f, 19f, 1.4f, 3f, 20f, 0.5f)
            }
        }
        line(40f, 26f, 40f, 16f).width(1f)
        roundRect(36.5f, 14f, 7f, 3f, 1.5f).fill(C.GRAY_DARK)
        // ayaklar
        group(rotate = 30f, px = 24f, py = 86f) { roundRect(21f, 80f, 5f, 12f, 2.5f).fill(C.GRAY_DARK) }
        group(rotate = -30f, px = 56f, py = 86f) { roundRect(54f, 80f, 5f, 12f, 2.5f).fill(C.GRAY_DARK) }
        // gövde ve kadran
        circle(40f, 56f, 29f).fill(C.RED)
        arc(40f, 56f, 26f, 26f, 200f, 60f).stroke(C.WHITE).width(0.8f).alpha(0.5f)
        circle(40f, 56f, 23f).fill(C.CREAM).width(0.8f)
        for (k in 0 until 12) {
            val a = Math.toRadians(k * 30.0 - 90)
            val r0 = if (k % 3 == 0) 17.5f else 19.5f
            line(40f + r0 * cos(a).toFloat(), 56f + r0 * sin(a).toFloat(), 40f + 21f * cos(a).toFloat(), 56f + 21f * sin(a).toFloat())
                .width(if (k % 3 == 0) 1f else 0.6f)
        }
        for (k in 0 until 60) {
            if (k % 5 == 0) continue
            val a = Math.toRadians(k * 6.0)
            circle(40f + 20.6f * cos(a).toFloat(), 56f + 20.6f * sin(a).toFloat(), 0.3f).fill(C.STONE_DARK).noStroke()
        }
        // akrep, yelkovan, saniye
        fun hand(angle: Double, len: Float, w: Float, color: String?) {
            val a = Math.toRadians(angle - 90)
            val ex = 40f + len * cos(a).toFloat(); val ey = 56f + len * sin(a).toFloat()
            val nx = -sin(a).toFloat() * w; val ny = cos(a).toFloat() * w
            poly(40f + nx, 56f + ny, ex, ey, 40f - nx, 56f - ny, 40f - 3f * cos(a).toFloat(), 56f - 3f * sin(a).toFloat(), closed = true).fill(color).width(0.6f)
        }
        hand(305.0, 12f, 1.6f, C.NAVY)
        hand(60.0, 17f, 1.2f, C.NAVY)
        line(40f + 4f * cos(Math.toRadians(90.0)).toFloat(), 56f + 4f, 40f, 56f - 19f).stroke(C.RED).width(0.5f)
        circle(40f, 56f, 2f).fill(C.RED).width(0.6f)
        circle(40f, 56f, 0.7f).fill(C.YELLOW).noStroke()
        path { moveTo(24f, 50f); quadTo(26f, 40f, 34f, 36f) }.stroke(C.WHITE).width(0.8f).alpha(0.7f)
    }

    private fun umbrella() = drawing("umbrella", "Şemsiye", 90f, 100f, listOf("şemsiye", "yağmur", "damla", "sonbahar", "umbrella")) {
        // damlalar
        for ((x, y) in listOf(10f to 14f, 22f to 6f, 74f to 8f, 84f to 20f, 8f to 64f, 82f to 70f, 16f to 84f, 72f to 90f)) drop(x, y, 1.6f).fill(C.WATER_LIGHT).width(0.5f)
        // kubbe dilimleri
        val top = 45f to 12f
        val n = 6
        val ends = List(n + 1) { i -> 5f + 80f * i / n }
        for (i in 0 until n) {
            val x0 = ends[i]; val x1 = ends[i + 1]
            path(closed = true) {
                moveTo(top.first, top.second)
                quadTo(x0, 12f + 4f * kotlin.math.abs(x0 - 45f) / 40f + (if (kotlin.math.abs(x0 - 45f) > 30f) 2f else 16f), x0, 50f)
                quadTo((x0 + x1) / 2f, 43f, x1, 50f)
                quadTo(x1, 12f + 4f * kotlin.math.abs(x1 - 45f) / 40f + (if (kotlin.math.abs(x1 - 45f) > 30f) 2f else 16f), top.first, top.second)
            }.fill(if (i % 2 == 0) C.RED else C.WHITE)
        }
        path(closed = true) { moveTo(45f, 12f); quadTo(70f, 18f, 85f, 50f); quadTo(78f, 43f, 71.7f, 50f); quadTo(70f, 28f, 45f, 12f) }.fill(C.SHADOW).noStroke().alpha(0.1f)
        path { moveTo(18f, 30f); quadTo(26f, 20f, 36f, 17f) }.stroke(C.WHITE).width(0.9f).alpha(0.6f)
        // uç, sap, kulp
        line(45f, 12f, 45f, 5f).width(1.2f)
        circle(45f, 4.5f, 1.5f).fill(C.GRAY_DARK).width(0.5f)
        rect(43.8f, 47f, 2.4f, 38f).fill(C.METAL).width(0.7f)
        ribbon(4f, 4f, 45f, 84f, 45.5f, 91f, 42f, 96f, 36f, 95f, 34.5f, 90f).fill(C.BROWN)
        for (i in 0..n) {
            val x = ends[i]
            circle(x, 50f, 0.9f).fill(C.GRAY_DARK).noStroke()
        }
    }

    private fun guitar() = drawing("guitar", "Gitar", 80f, 100f, listOf("gitar", "müzik", "enstrüman", "tel", "guitar")) {
        // notalar
        for ((x, y) in listOf(10f to 22f, 66f to 70f)) {
            ellipse(x, y, 2.4f, 1.8f, -20f).fill(C.BLACK).width(0.4f)
            line(x + 2.2f, y - 0.5f, x + 2.2f, y - 9f).width(0.6f)
            path { moveTo(x + 2.2f, y - 9f); quadTo(x + 5f, y - 8f, x + 5.5f, y - 5f) }.width(0.6f)
        }
        group(rotate = 20f, sx = 0.92f, px = 40f, py = 52f) {
            // sap ve baş
            roundRect(35f, 1f, 10f, 15f, 2f).fill(C.BROWN_DARK)
            for (k in 0..2) {
                circle(33.4f, 4f + k * 4.4f, 1.4f).fill(C.METAL).width(0.5f)
                circle(46.6f, 4f + k * 4.4f, 1.4f).fill(C.METAL).width(0.5f)
            }
            rect(37.3f, 14f, 5.4f, 48f).fill(C.BROWN)
            rect(37.8f, 15.5f, 4.4f, 44f).fill(C.BROWN_DARK).width(0.5f)
            rect(37.3f, 14.5f, 5.4f, 1.3f).fill(C.CREAM).width(0.4f)
            var fy = 19f; var step = 4.6f
            while (fy < 58f) { line(37.8f, fy, 42.2f, fy).stroke(C.METAL).width(0.45f); fy += step; step *= 0.94f }
            for (y in listOf(28.6f, 37.4f, 45.2f)) circle(40f, y, 0.6f).fill(C.CREAM).noStroke()
            // gövde
            smooth(40f, 40f, 51f, 42f, 55f, 50f, 50f, 58f, 58f, 68f, 59f, 80f, 51f, 92f, 40f, 95f, 29f, 92f, 21f, 80f, 22f, 68f, 30f, 58f, 25f, 50f, 29f, 42f, closed = true)
                .fill("#E8A15B")
            group(sx = 0.88f, px = 40f, py = 70f) {
                smooth(40f, 40f, 51f, 42f, 55f, 50f, 50f, 58f, 58f, 68f, 59f, 80f, 51f, 92f, 40f, 95f, 29f, 92f, 21f, 80f, 22f, 68f, 30f, 58f, 25f, 50f, 29f, 42f, closed = true)
                    .fill(C.YELLOW_LIGHT).noStroke().alpha(0.35f)
            }
            rect(37.8f, 44f, 4.4f, 16f).fill(C.BROWN_DARK).width(0.5f)
            path(closed = true) { moveTo(45f, 66f); quadTo(52f, 66f, 52f, 72f); quadTo(50f, 78f, 44f, 77f); quadTo(47f, 72f, 45f, 66f) }.fill(C.BROWN).noStroke().alpha(0.6f)
            circle(40f, 66f, 7.4f).fill(C.WOOD).width(0.5f)
            arc(40f, 66f, 6.6f, 6.6f, 0f, 360f).stroke(C.BROWN).width(0.5f)
            circle(40f, 66f, 5.4f).fill("#3B2B22").width(0.6f)
            roundRect(32f, 81f, 16f, 3.6f, 1.6f).fill(C.BROWN_DARK)
            for (k in 0 until 6) circle(34.5f + k * 2.2f, 82.8f, 0.45f).fill(C.CREAM).noStroke()
            for (k in 0 until 6) {
                val x = 38.2f + k * 0.72f
                line(x, 14.5f, 34.5f + k * 2.2f, 82.8f).stroke(C.METAL).width(0.3f)
            }
            shine(30f, 76f, 2.4f, 6f, 15f, 0.35f)
        }
    }

    private fun laptop() = drawing("laptop", "Dizüstü bilgisayar", 130f, 90f, listOf("bilgisayar", "dizüstü", "laptop", "teknoloji", "ekran")) {
        shadow(65f, 84f, 62f, 3f, 0.16f)
        // ekran
        roundRect(24f, 5f, 82f, 56f, 4f).fill(C.GRAY_DARK)
        rect(29f, 10f, 72f, 45f).fill("#8CC8EC").width(0.6f)
        circle(88f, 20f, 5f).fill(C.YELLOW).noStroke()
        path(closed = true) { moveTo(29f, 44f); quadTo(46f, 30f, 64f, 42f); quadTo(82f, 32f, 101f, 42f); lineTo(101f, 55f); lineTo(29f, 55f) }.fill(C.GREEN).width(0f)
        path(closed = true) { moveTo(29f, 50f); quadTo(58f, 42f, 101f, 50f); lineTo(101f, 55f); lineTo(29f, 55f) }.fill(C.GREEN_DARK).width(0f)
        // pencere ve simgeler
        roundRect(48f, 16f, 34f, 24f, 1.5f).fill(C.WHITE).width(0.6f)
        rect(48f, 16f, 34f, 4f).fill(C.BLUE_LIGHT).width(0.5f)
        for ((k, col) in listOf(C.RED, C.YELLOW, C.GREEN).withIndex()) circle(51f + k * 3f, 18f, 0.9f).fill(col).noStroke()
        for (k in 0..3) line(51f, 24f + k * 3.6f, if (k == 3) 66f else 79f, 24f + k * 3.6f).stroke(C.STONE).width(0.6f)
        for ((k, col) in listOf(C.ORANGE, C.PURPLE, C.TEAL).withIndex()) {
            roundRect(33f, 14f + k * 9f, 6f, 6f, 1.5f).fill(col).width(0.5f)
        }
        poly(29f, 55f, 52f, 10f, 64f, 10f, 41f, 55f, closed = true).fill(C.WHITE).noStroke().alpha(0.12f)
        circle(65f, 7.5f, 0.9f).fill(C.BLACK).noStroke()
        // taban
        rect(26f, 60f, 78f, 2.5f).fill(C.STONE_DARK).width(0.6f)
        poly(16f, 62f, 114f, 62f, 125f, 79f, 5f, 79f, closed = true).fill(C.METAL)
        path(closed = true) { moveTo(5f, 79f); lineTo(125f, 79f); lineTo(125f, 80.5f); quadTo(125f, 83f, 121f, 83f); lineTo(9f, 83f); quadTo(5f, 83f, 5f, 80.5f) }.fill(C.STONE)
        roundRect(58f, 80f, 14f, 1.5f, 0.7f).fill(C.STONE_DARK).noStroke()
        // tuşlar
        for (r in 0 until 4) {
            val y0 = 63.5f + r * 2.6f
            val y1 = y0 + 2f
            fun xl(y: Float) = 16f - (y - 62f) / 17f * 11f + 4f
            fun xr(y: Float) = 114f + (y - 62f) / 17f * 11f - 4f
            val cols = 14
            for (c in 0 until cols) {
                if (r == 3 && c in 4..9) continue
                val t0 = c / cols.toFloat() + 0.006f; val t1 = (c + 1) / cols.toFloat() - 0.006f
                poly(
                    xl(y0) + (xr(y0) - xl(y0)) * t0, y0, xl(y0) + (xr(y0) - xl(y0)) * t1, y0,
                    xl(y1) + (xr(y1) - xl(y1)) * t1, y1, xl(y1) + (xr(y1) - xl(y1)) * t0, y1, closed = true,
                ).fill(C.TIRE).width(0f)
            }
            if (r == 3) {
                poly(xl(y0) + (xr(y0) - xl(y0)) * 0.29f, y0, xl(y0) + (xr(y0) - xl(y0)) * 0.71f, y0,
                    xl(y1) + (xr(y1) - xl(y1)) * 0.71f, y1, xl(y1) + (xr(y1) - xl(y1)) * 0.29f, y1, closed = true).fill(C.TIRE).width(0f)
            }
        }
        poly(55f, 74.2f, 75f, 74.2f, 76f, 78f, 54f, 78f, closed = true).fill(C.STONE_LIGHT).width(0.5f)
    }

    private fun teapot() = drawing("tea_set", "Çaydanlık ve çay", 100f, 100f, listOf("çay", "çaydanlık", "demlik", "bardak", "tea")) {
        shadow(46f, 94f, 42f, 3.5f, 0.16f)
        // buhar
        smooth(14f, 30f, 11f, 24f, 15f, 18f, 12f, 11f).stroke(C.STONE_DARK).width(0.7f)
        smooth(19f, 28f, 17f, 22f, 21f, 16f, 18f, 9f).stroke(C.STONE_DARK).width(0.7f)
        // alt güğüm: kulp, emzik, gövde
        ribbon(3.2f, 3.2f, 64f, 62f, 74f, 64f, 77f, 74f, 68f, 84f).fill(C.STONE_DARK)
        ribbon(6f, 3f, 17f, 76f, 10f, 68f, 5f, 56f).fill(C.METAL)
        path(closed = true) { moveTo(16f, 90f); lineTo(68f, 90f); quadTo(75f, 70f, 64f, 57f); lineTo(20f, 57f); quadTo(9f, 70f, 16f, 90f) }.fill(C.METAL)
        path(closed = true) { moveTo(52f, 57f); lineTo(64f, 57f); quadTo(75f, 70f, 68f, 90f); lineTo(58f, 90f); quadTo(62f, 72f, 52f, 57f) }.fill(C.SHADOW).noStroke().alpha(0.12f)
        roundRect(20f, 62f, 3f, 22f, 1.5f).fill(C.WHITE).noStroke().alpha(0.6f)
        rect(15f, 74f, 55f, 2f).fill(C.STONE_DARK).noStroke().alpha(0.35f)
        roundRect(14f, 88f, 56f, 4f, 2f).fill(C.STONE_DARK)
        ellipse(42f, 57f, 23f, 3.6f).fill(C.STONE)
        // üst demlik
        ribbon(2.6f, 2.6f, 54f, 40f, 63f, 43f, 64f, 50f, 56f, 54f).fill(C.RED_DARK)
        ribbon(5f, 2.4f, 30f, 47f, 23f, 41f, 17f, 32f).fill(C.RED)
        path(closed = true) { moveTo(28f, 56f); quadTo(21f, 43f, 32f, 34f); lineTo(52f, 34f); quadTo(63f, 43f, 56f, 56f) }.fill(C.RED)
        path(closed = true) { moveTo(46f, 34f); lineTo(52f, 34f); quadTo(63f, 43f, 56f, 56f); lineTo(50f, 56f); quadTo(55f, 44f, 46f, 34f) }.fill(C.SHADOW).noStroke().alpha(0.12f)
        blossom(37f, 46f, 5f, C.WHITE, C.YELLOW, 5, 0f, 0.45f)
        blossom(47.5f, 43f, 3.4f, C.WHITE, C.YELLOW, 5, 20f, 0.45f)
        leaf(43f, 50f, 5f, 1.4f, 20f, C.GREEN, vein = false)
        path(closed = true) { moveTo(32f, 34f); quadTo(32f, 26f, 42f, 26f); quadTo(52f, 26f, 52f, 34f) }.fill(C.RED_DARK)
        ellipse(42f, 34f, 11f, 2.4f).fill(C.RED).width(0.7f)
        circle(42f, 24.5f, 2.4f).fill(C.YELLOW).width(0.6f)
        shine(30f, 42f, 1.2f, 3.4f, 25f, 0.5f)
        // ince belli çay bardağı ve tabağı
        ellipse(89f, 92f, 11f, 2.6f).fill(C.WHITE)
        ellipse(89f, 91.5f, 7f, 1.3f).fill(C.WHITE).stroke(C.RED).width(0.45f)
        path(closed = true) {
            moveTo(82.5f, 64f); lineTo(95.5f, 64f); quadTo(94.5f, 72f, 92f, 76.5f); quadTo(95.5f, 84f, 94.5f, 91f)
            lineTo(83.5f, 91f); quadTo(82.5f, 84f, 86f, 76.5f); quadTo(83.5f, 72f, 82.5f, 64f)
        }.fill("#F6FBFE")
        path(closed = true) {
            moveTo(83f, 67.5f); lineTo(95f, 67.5f); quadTo(94.2f, 72f, 91.6f, 76.5f); quadTo(94.8f, 84f, 94f, 90.3f)
            lineTo(84f, 90.3f); quadTo(83.2f, 84f, 86.4f, 76.5f); quadTo(83.8f, 72f, 83f, 67.5f)
        }.fill("#C0452B").noStroke().alpha(0.9f)
        ellipse(89f, 64f, 6.5f, 1.2f).fill(C.WHITE).width(0.5f)
        roundRect(84.8f, 69f, 1.4f, 18f, 0.7f).fill(C.WHITE).noStroke().alpha(0.5f)
        line(93f, 60f, 90f, 86f).stroke(C.METAL).width(0.6f)
        roundRect(76f, 88f, 4f, 3.5f, 0.6f).fill(C.WHITE).width(0.5f)
    }
}
