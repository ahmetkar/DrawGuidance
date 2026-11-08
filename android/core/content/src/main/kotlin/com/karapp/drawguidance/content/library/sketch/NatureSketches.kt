package com.karapp.drawguidance.content.library.sketch

import com.karapp.drawguidance.content.library.LibShape
import kotlin.math.cos
import kotlin.math.sin

/** "Doğa": bulut, bulutlar, güneş, ay, ağaç, çam, palmiye, çalı, çimen, çiçek, dağlar, yaprak. */
internal object NatureSketches {

    val shapes: List<LibShape> by lazy {
        listOf(cloud(), clouds(), sun(), moon(), tree(), pine(), palm(), bush(), grass(), flower(), mountains(), leaf())
    }

    /** Hilal: [r1] dairesinden [r2] dairesinin kestiği parça çıkarılır (iki yay, kesişim noktalarında birleşir). */
    private fun com.karapp.drawguidance.content.vector.VectorBuilder.crescent(x1: Float, y1: Float, r1: Float, x2: Float, y2: Float, r2: Float) {
        val dx = x2 - x1; val dy = y2 - y1
        val d = kotlin.math.hypot(dx, dy)
        val a = (r1 * r1 - r2 * r2 + d * d) / (2f * d)
        val h = kotlin.math.sqrt((r1 * r1 - a * a).coerceAtLeast(0f))
        val px = x1 + a * dx / d; val py = y1 + a * dy / d
        val ix1 = px + h * dy / d; val iy1 = py - h * dx / d
        val ix2 = px - h * dy / d; val iy2 = py + h * dx / d
        fun ang(cx: Float, cy: Float, x: Float, y: Float) = Math.toDegrees(kotlin.math.atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
        fun norm(v: Float) = ((v % 360f) + 360f) % 360f
        fun contains(start: Float, sweep: Float, target: Float): Boolean {
            val t = norm(target - start)
            return if (sweep >= 0f) t <= sweep else norm(start - target) <= -sweep
        }
        val a1 = ang(x1, y1, ix1, iy1); val a2 = ang(x1, y1, ix2, iy2)
        val toward2 = ang(x1, y1, x2, y2)
        var s1 = norm(a2 - a1)
        if (contains(a1, s1, toward2)) s1 -= 360f
        val b2 = ang(x2, y2, ix2, iy2); val b1 = ang(x2, y2, ix1, iy1)
        val toward1 = ang(x2, y2, x1, y1)
        var s2 = norm(b1 - b2)
        if (!contains(b2, s2, toward1)) s2 -= 360f
        path(closed = true) {
            arcTo(x1, y1, r1, r1, a1, s1)
            arcTo(x2, y2, r2, r2, b2, s2)
        }
    }

    private fun cloud() = sketch("cloud", "Bulut", 120f, 64f, listOf("bulut", "gökyüzü", "hava", "cloud")) {
        path(closed = true) {
            moveTo(18f, 52f)
            cubicTo(6f, 52f, 4f, 36f, 16f, 34f)
            cubicTo(14f, 20f, 32f, 14f, 40f, 24f)
            cubicTo(44f, 8f, 70f, 4f, 78f, 20f)
            cubicTo(86f, 12f, 104f, 16f, 102f, 30f)
            cubicTo(116f, 30f, 118f, 50f, 104f, 52f)
            lineTo(18f, 52f)
        }
        path { moveTo(40f, 24f); cubicTo(42f, 28f, 42f, 31f, 40f, 34f) }.detail()
        path { moveTo(78f, 20f); cubicTo(80f, 24f, 80f, 27f, 79f, 30f) }.detail()
    }

    private fun clouds() = sketch("clouds", "Bulutlar", 160f, 80f, listOf("bulut", "bulutlar", "gökyüzü", "cloud")) {
        puff(52f, 32f, 42f, 18f, 9, depth = 0.28f)
        puff(118f, 58f, 34f, 14f, 8, depth = 0.28f)
        path { moveTo(20f, 70f); lineTo(40f, 70f) }.width(0.4f)
        path { moveTo(64f, 72f); lineTo(76f, 72f) }.width(0.4f)
    }

    private fun sun() = sketch("sun", "Güneş", 100f, 100f, listOf("güneş", "ışık", "gökyüzü", "sun")) {
        circle(50f, 50f, 22f)
        circle(50f, 50f, 16f).width(0.4f).dashed()
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val long = i % 2 == 0
            val r1 = 27f; val r2 = if (long) 46f else 38f
            line(50f + r1 * cos(a).toFloat(), 50f + r1 * sin(a).toFloat(), 50f + r2 * cos(a).toFloat(), 50f + r2 * sin(a).toFloat())
        }
    }

    private fun moon() = sketch("moon", "Ay ve yıldızlar", 100f, 100f, listOf("ay", "hilal", "gece", "yıldız", "moon")) {
        crescent(46f, 52f, 34f, 62f, 42f, 30f)
        circle(36f, 70f, 3f).detail()
        circle(28f, 50f, 2f).detail()
        star(80f, 24f, 6f, 2.6f)
        star(88f, 62f, 4f, 1.8f)
        star(70f, 88f, 3.5f, 1.5f)
    }

    private fun tree() = sketch("tree", "Ağaç", 100f, 124f, listOf("ağaç", "yaprak", "doğa", "orman", "tree")) {
        guide(2f, 120f, 98f, 120f)
        // Gövde ve kökler
        path(closed = true) {
            moveTo(42f, 120f)
            cubicTo(45f, 110f, 45f, 96f, 44f, 76f)
            lineTo(56f, 76f)
            cubicTo(55f, 96f, 55f, 110f, 58f, 120f)
            cubicTo(52f, 118f, 48f, 118f, 42f, 120f)
        }
        path { moveTo(47f, 118f); cubicTo(40f, 119f, 36f, 121f, 32f, 121f) }.detail()
        path { moveTo(53f, 118f); cubicTo(60f, 119f, 64f, 121f, 68f, 121f) }.detail()
        // Taç (kabarık) ve dallar
        puff(50f, 46f, 42f, 36f, 11, depth = 0.26f)
        path { moveTo(49f, 78f); cubicTo(46f, 66f, 38f, 60f, 30f, 56f) }.detail()
        path { moveTo(51f, 76f); cubicTo(54f, 64f, 62f, 58f, 70f, 54f) }.detail()
        path { moveTo(50f, 70f); lineTo(50f, 50f) }.detail()
        // Kabuk çizgileri
        for (k in 0..2) path { moveTo(48f + k * 2f, 116f - k * 4f); cubicTo(49f + k * 2f, 106f, 48f + k * 2f, 96f, 49f + k * 2f, 88f) }.width(0.35f)
    }

    private fun pine() = sketch("pine", "Çam ağacı", 80f, 124f, listOf("çam", "ağaç", "yılbaşı", "orman", "pine")) {
        guide(2f, 120f, 78f, 120f)
        rect(35f, 102f, 10f, 18f)
        // Üç katlı dal kümesi (tırtıklı kenar)
        for ((k, dims) in listOf(Triple(4f, 30f, 42f), Triple(28f, 34f, 70f), Triple(52f, 38f, 102f)).withIndex()) {
            val (top, half, bottom) = dims
            path(closed = true) {
                moveTo(40f, top)
                lineTo(40f + half, bottom)
                val teeth = 5 + k
                for (i in 1..teeth) {
                    val x = 40f + half - i * (2f * half / teeth)
                    quadTo(x + half / teeth, bottom - 5f, x, bottom)
                }
                lineTo(40f, top)
            }
        }
    }

    private fun palm() = sketch("palm", "Palmiye", 100f, 124f, listOf("palmiye", "ağaç", "tropik", "sahil", "palm")) {
        guide(2f, 120f, 98f, 120f)
        path(closed = true) {
            moveTo(44f, 120f)
            cubicTo(46f, 90f, 50f, 60f, 60f, 34f)
            lineTo(66f, 36f)
            cubicTo(58f, 62f, 54f, 90f, 56f, 120f)
        }
        for (k in 0..8) {
            val y = 112f - k * 9f
            val x = 45f + k * 1.8f
            path { moveTo(x, y); cubicTo(x + 4f, y - 2f, x + 8f, y - 2f, x + 11.5f - k * 0.3f, y - 0.5f) }.width(0.4f)
        }
        // Yapraklar (yelpaze)
        fun frond(ex: Float, ey: Float, cx: Float, cy: Float) {
            path(closed = true) {
                moveTo(63f, 34f)
                quadTo(cx, cy - 6f, ex, ey)
                quadTo(cx + (63f - cx) * 0.2f, cy + 4f, 63f, 34f)
            }
            path { moveTo(63f, 34f); quadTo(cx, cy - 1f, ex, ey) }.width(0.4f)
        }
        frond(6f, 44f, 30f, 14f)
        frond(20f, 70f, 36f, 36f)
        frond(96f, 50f, 88f, 18f)
        frond(88f, 76f, 82f, 44f)
        frond(58f, 4f, 50f, 14f)
        frond(92f, 16f, 78f, 12f)
        circle(60f, 40f, 3.5f).detail()
        circle(66f, 41f, 3.5f).detail()
    }

    private fun bush() = sketch("bush", "Çalı", 120f, 70f, listOf("çalı", "bitki", "bahçe", "yeşillik", "bush")) {
        guide(2f, 64f, 118f, 64f)
        path(closed = true) {
            moveTo(12f, 64f)
            cubicTo(2f, 60f, 4f, 44f, 16f, 42f)
            cubicTo(14f, 28f, 32f, 20f, 42f, 28f)
            cubicTo(48f, 12f, 72f, 12f, 78f, 26f)
            cubicTo(90f, 18f, 108f, 28f, 104f, 42f)
            cubicTo(118f, 44f, 118f, 62f, 108f, 64f)
            lineTo(12f, 64f)
        }
        for ((x, y) in listOf(30f to 44f, 58f to 36f, 86f to 42f, 44f to 54f, 74f to 54f, 98f to 54f)) {
            path { moveTo(x - 5f, y + 2f); quadTo(x, y - 4f, x + 5f, y + 2f) }.width(0.45f)
        }
    }

    private fun grass() = sketch("grass", "Çimen", 120f, 44f, listOf("çimen", "ot", "yeşillik", "bahçe", "grass")) {
        line(2f, 40f, 118f, 40f)
        val rnd = java.util.Random(11)
        for (k in 0 until 26) {
            val x = 5f + k * 4.4f
            val h = 16f + rnd.nextFloat() * 20f
            val lean = (rnd.nextFloat() - 0.5f) * 12f
            path(closed = true) {
                moveTo(x - 1.6f, 40f)
                quadTo(x - 0.5f + lean * 0.3f, 40f - h * 0.55f, x + lean, 40f - h)
                quadTo(x + 1f + lean * 0.3f, 40f - h * 0.5f, x + 1.8f, 40f)
            }.width(0.6f)
        }
    }

    private fun flower() = sketch("flower", "Çiçek", 60f, 110f, listOf("çiçek", "papatya", "bahçe", "bitki", "flower")) {
        // Sap ve yapraklar
        path { moveTo(30f, 106f); cubicTo(28f, 86f, 32f, 64f, 30f, 44f) }
        path(closed = true) { moveTo(29.5f, 84f); cubicTo(20f, 76f, 10f, 78f, 6f, 72f); cubicTo(14f, 70f, 24f, 72f, 29.5f, 84f) }
        path(closed = true) { moveTo(30.5f, 72f); cubicTo(38f, 62f, 48f, 64f, 54f, 58f); cubicTo(46f, 56f, 36f, 60f, 30.5f, 72f) }
        // Taç yaprakları
        for (i in 0 until 10) {
            val a = i * 36f
            group(rotate = a, px = 30f, py = 26f) { ellipse(30f, 11f, 4.5f, 11f) }
        }
        circle(30f, 26f, 7f)
        circle(30f, 26f, 4f).width(0.4f).dotted()
        line(18f, 106f, 42f, 106f).detail()
    }

    private fun mountains() = sketch("mountains", "Dağlar", 160f, 90f, listOf("dağ", "tepe", "manzara", "doğa", "mountain")) {
        guide(2f, 84f, 158f, 84f)
        path { moveTo(2f, 84f); lineTo(46f, 26f); lineTo(64f, 46f); lineTo(92f, 10f); lineTo(126f, 52f); lineTo(140f, 40f); lineTo(158f, 62f) }
        // Kar örtüleri
        path { moveTo(82f, 23f); lineTo(88f, 28f); lineTo(92f, 23f); lineTo(97f, 29f); lineTo(102f, 24f) }.detail()
        path { moveTo(38f, 37f); lineTo(43f, 40f); lineTo(47f, 36f); lineTo(51f, 40f); lineTo(54f, 36f) }.detail()
        // Sırt çizgileri
        path { moveTo(92f, 10f); lineTo(88f, 40f); lineTo(80f, 60f) }.width(0.4f)
        path { moveTo(46f, 26f); lineTo(44f, 50f); lineTo(36f, 70f) }.width(0.4f)
        // Önde tepe
        path { moveTo(20f, 84f); cubicTo(50f, 64f, 90f, 62f, 130f, 84f) }.detail()
    }

    private fun leaf() = sketch("leaf", "Yaprak", 70f, 100f, listOf("yaprak", "bitki", "sonbahar", "leaf")) {
        path(closed = true) { moveTo(35f, 6f); cubicTo(62f, 24f, 64f, 64f, 35f, 84f); cubicTo(6f, 64f, 8f, 24f, 35f, 6f) }
        line(35f, 84f, 35f, 97f)
        line(35f, 12f, 35f, 84f).detail()
        for (k in 0..4) {
            val y = 26f + k * 11f
            path { moveTo(35f, y + 6f); quadTo(42f, y + 1f, 50f - k * 0.5f, y - 2f) }.width(0.45f)
            path { moveTo(35f, y + 6f); quadTo(28f, y + 1f, 20f + k * 0.5f, y - 2f) }.width(0.45f)
        }
    }
}
