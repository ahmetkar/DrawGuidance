package com.karapp.drawguidance.content.library.sketch

import com.karapp.drawguidance.content.library.LibShape

/** "Taşıtlar": araba (yandan/önden), spor araba, otobüs, kamyon, bisiklet, motosiklet, uçak, yelkenli, tren. */
internal object VehicleSketches {

    val shapes: List<LibShape> by lazy {
        listOf(carSide(), carFront(), sportsCar(), bus(), truck(), bicycle(), motorcycle(), airplane(), sailboat(), train())
    }

    private fun carSide() = sketch("car_side", "Araba (yandan)", 160f, 72f, listOf("araba", "otomobil", "taşıt", "yandan", "car")) {
        guide(2f, 67f, 158f, 67f)
        // Gövde: tampon, kaput, ön cam, tavan, arka cam, bagaj; tekerlek kemerleri
        path(closed = true) {
            moveTo(151f, 56f)
            lineTo(153f, 46f)
            cubicTo(152f, 41f, 148f, 38.5f, 140f, 37.5f)
            lineTo(112f, 34f)
            cubicTo(104f, 25f, 96f, 17f, 88f, 15f)
            lineTo(60f, 14f)
            cubicTo(52f, 15f, 46f, 22f, 40f, 30f)
            lineTo(18f, 33f)
            cubicTo(11f, 34f, 8f, 38f, 8f, 44f)
            lineTo(8f, 53f)
            lineTo(12f, 56f)
            lineTo(19f, 56f)
            arcTo(35f, 56f, 15.5f, 15.5f, 180f, 180f)
            lineTo(107f, 56f)
            arcTo(123f, 56f, 15.5f, 15.5f, 180f, 180f)
            lineTo(151f, 56f)
        }
        // Yan camlar ve direkler
        path(closed = true) { moveTo(79f, 18f); lineTo(89f, 18f); cubicTo(95f, 21f, 100f, 27f, 104.5f, 33f); lineTo(79f, 33f) }.detail()
        path(closed = true) { moveTo(75f, 18f); lineTo(61f, 18f); cubicTo(55f, 20f, 50f, 26f, 47f, 32.5f); lineTo(75f, 32.5f) }.detail()
        // Kapılar, kolları, ayna
        line(77f, 34f, 77f, 55f).detail()
        line(108f, 35f, 106f, 55f).detail()
        line(46f, 33f, 49f, 55f).detail()
        line(66f, 38f, 72f, 38f)
        line(97f, 38.5f, 103f, 38.5f)
        path(closed = true) { moveTo(106f, 31f); lineTo(111f, 29f); lineTo(112.5f, 33f); lineTo(107f, 34f) }.detail()
        // Farlar ve tampon çizgisi
        path(closed = true) { moveTo(145f, 40f); lineTo(152f, 42f); lineTo(152.5f, 46f); lineTo(144f, 45f) }.detail()
        path(closed = true) { moveTo(8.5f, 37f); lineTo(14f, 36f); lineTo(14f, 42f); lineTo(8.3f, 42f) }.detail()
        line(9f, 48f, 19f, 48f).detail()
        line(139f, 49f, 152.5f, 49f).detail()
        // Tekerlekler
        wheel(35f, 56f, 11.5f, spokes = 5)
        wheel(123f, 56f, 11.5f, spokes = 5)
    }

    private fun carFront() = sketch("car_front", "Araba (önden)", 100f, 72f, listOf("araba", "otomobil", "önden", "car")) {
        guide(50f, 2f, 50f, 70f)
        // Tekerlekler (gövdenin altında)
        roundRect(12f, 52f, 13f, 16f, 3f)
        roundRect(75f, 52f, 13f, 16f, 3f)
        // Gövde
        path(closed = true) {
            moveTo(8f, 58f)
            lineTo(7f, 40f)
            cubicTo(8f, 35f, 12f, 33f, 18f, 32f)
            lineTo(24f, 16f)
            cubicTo(26f, 12f, 30f, 10f, 36f, 10f)
            lineTo(64f, 10f)
            cubicTo(70f, 10f, 74f, 12f, 76f, 16f)
            lineTo(82f, 32f)
            cubicTo(88f, 33f, 92f, 35f, 93f, 40f)
            lineTo(92f, 58f)
        }
        // Ön cam, kaput çizgisi, ızgara, farlar, plaka, aynalar
        path(closed = true) { moveTo(27f, 30f); lineTo(32f, 16f); lineTo(68f, 16f); lineTo(73f, 30f) }.detail()
        line(18f, 34f, 82f, 34f).detail()
        roundRect(33f, 42f, 34f, 9f, 2f)
        for (k in 1..3) line(33f + k * 8.5f, 42f, 33f + k * 8.5f, 51f).width(0.4f)
        mirrored(50f) {
            path(closed = true) { moveTo(11f, 40f); lineTo(26f, 41f); lineTo(25f, 47f); lineTo(12f, 46f) }
            circle(17f, 43.5f, 2f).detail()
            path(closed = true) { moveTo(18f, 30f); lineTo(11f, 28f); lineTo(10f, 32f); lineTo(17f, 33f) }.detail()
        }
        rect(40f, 54f, 20f, 5f).detail()
        line(8f, 58f, 92f, 58f)
    }

    private fun sportsCar() = sketch("sports_car", "Spor araba", 170f, 56f, listOf("spor", "araba", "yarış", "otomobil", "car")) {
        guide(2f, 52f, 168f, 52f)
        path(closed = true) {
            moveTo(163f, 42f)
            cubicTo(163f, 36f, 158f, 32f, 148f, 30f)
            lineTo(118f, 27f)
            cubicTo(108f, 18f, 96f, 13f, 82f, 13f)
            cubicTo(66f, 13f, 56f, 18f, 46f, 24f)
            lineTo(18f, 27f)
            lineTo(10f, 22f)
            lineTo(6f, 23f)
            lineTo(8f, 34f)
            lineTo(8f, 42f)
            lineTo(22f, 43f)
            arcTo(37f, 43f, 13.5f, 13.5f, 180f, 180f)
            lineTo(120f, 43f)
            arcTo(135f, 43f, 13.5f, 13.5f, 180f, 180f)
            lineTo(163f, 42f)
        }
        path(closed = true) { moveTo(58f, 24f); cubicTo(66f, 18f, 74f, 16.5f, 84f, 16.5f); cubicTo(96f, 16.5f, 104f, 21f, 111f, 27f); lineTo(58f, 27f) }.detail()
        line(86f, 16.5f, 88f, 27f).detail()
        path { moveTo(62f, 30f); cubicTo(80f, 33f, 100f, 33f, 118f, 30f) }.detail()
        path(closed = true) { moveTo(150f, 32f); lineTo(161f, 35f); lineTo(160f, 38f); lineTo(149f, 36f) }.detail()
        path(closed = true) { moveTo(80f, 37f); lineTo(96f, 37f); lineTo(92f, 41f); lineTo(82f, 41f) }.detail()
        wheel(37f, 43f, 10f, spokes = 6)
        wheel(135f, 43f, 10f, spokes = 6)
    }

    private fun bus() = sketch("bus", "Otobüs", 170f, 82f, listOf("otobüs", "taşıt", "toplu taşıma", "bus")) {
        guide(2f, 78f, 168f, 78f)
        path(closed = true) {
            moveTo(8f, 66f)
            lineTo(8f, 14f)
            cubicTo(8f, 9f, 11f, 6f, 16f, 6f)
            lineTo(152f, 6f)
            cubicTo(160f, 6f, 164f, 12f, 164f, 22f)
            lineTo(164f, 66f)
            lineTo(146f, 66f)
            arcTo(134f, 66f, 12f, 12f, 0f, -180f)
            lineTo(46f, 66f)
            arcTo(34f, 66f, 12f, 12f, 0f, -180f)
            lineTo(8f, 66f)
        }
        // Pencereler
        for (k in 0..6) roundRect(14f + k * 17f, 14f, 14f, 18f, 1.5f).detail()
        // Ön cam ve kapı
        path(closed = true) { moveTo(137f, 12f); lineTo(152f, 12f); cubicTo(157f, 12f, 159f, 16f, 159f, 22f); lineTo(159f, 34f); lineTo(137f, 34f) }.detail()
        rect(137f, 38f, 14f, 26f).detail()
        line(144f, 38f, 144f, 64f).width(0.4f)
        line(8f, 40f, 132f, 40f).detail()
        roundRect(156f, 52f, 6f, 5f, 1f).detail()
        wheel(34f, 66f, 9.5f, spokes = 0)
        wheel(134f, 66f, 9.5f, spokes = 0)
    }

    private fun truck() = sketch("truck", "Kamyon", 170f, 82f, listOf("kamyon", "tır", "yük", "taşıt", "truck")) {
        guide(2f, 78f, 168f, 78f)
        // Yük kasası
        rect(8f, 10f, 104f, 52f)
        for (k in 1..5) line(8f + k * 17.3f, 12f, 8f + k * 17.3f, 60f).width(0.4f)
        // Kabin
        path(closed = true) {
            moveTo(116f, 64f)
            lineTo(116f, 24f)
            lineTo(146f, 24f)
            lineTo(158f, 40f)
            lineTo(163f, 42f)
            lineTo(163f, 64f)
        }
        path(closed = true) { moveTo(126f, 28f); lineTo(144f, 28f); lineTo(153f, 40f); lineTo(126f, 40f) }.detail()
        line(124f, 26f, 124f, 62f).detail()
        line(128f, 46f, 134f, 46f)
        roundRect(157f, 46f, 6f, 5f, 1f).detail()
        // Şasi ve tekerlekler
        rect(8f, 62f, 156f, 5f).detail()
        wheel(30f, 67f, 10f)
        wheel(54f, 67f, 10f)
        wheel(140f, 67f, 10f)
    }

    private fun bicycle() = sketch("bicycle", "Bisiklet", 130f, 82f, listOf("bisiklet", "pedal", "taşıt", "bike")) {
        guide(2f, 80f, 128f, 80f)
        wheel(30f, 56f, 23f, spokes = 12)
        wheel(101f, 56f, 23f, spokes = 12)
        // Kadro: arka göbek, sele borusu, orta göbek, üst ve alt boru, çatal
        line(30f, 56f, 58f, 57f)
        line(30f, 56f, 51f, 25f)
        line(58f, 57f, 49f, 20f)
        line(51f, 25f, 88f, 23f)
        line(58f, 57f, 89f, 27f)
        line(88f, 18f, 101f, 56f)
        // Gidon ve sele
        line(88f, 18f, 85f, 12f)
        path { moveTo(80f, 12f); lineTo(88f, 12f); cubicTo(92f, 12f, 93f, 16f, 91f, 18f) }
        path(closed = true) { moveTo(40f, 18f); cubicTo(44f, 16f, 54f, 16.5f, 57f, 18.5f); cubicTo(52f, 21f, 44f, 21f, 40f, 18f) }
        // Aynakol ve pedallar
        circle(58f, 57f, 4f).detail()
        line(58f, 57f, 63f, 65f).detail()
        line(60f, 66f, 66f, 64f)
        line(58f, 57f, 53f, 49f).detail()
    }

    private fun motorcycle() = sketch("motorcycle", "Motosiklet", 140f, 86f, listOf("motosiklet", "motor", "taşıt", "motorbike")) {
        guide(2f, 83f, 138f, 83f)
        wheel(28f, 62f, 19f, spokes = 8)
        wheel(113f, 62f, 19f, spokes = 8)
        // Gövde: depo, sele, arka çamurluk, motor bloğu
        path(closed = true) { moveTo(58f, 36f); cubicTo(62f, 26f, 80f, 24f, 88f, 31f); lineTo(86f, 40f); lineTo(60f, 42f) }
        path(closed = true) { moveTo(30f, 38f); lineTo(58f, 37f); lineTo(60f, 42f); lineTo(38f, 44f); cubicTo(33f, 43f, 30f, 41f, 30f, 38f) }
        path { moveTo(12f, 56f); cubicTo(14f, 44f, 22f, 40f, 32f, 41f) }
        roundRect(56f, 46f, 24f, 18f, 3f)
        for (k in 0..2) line(58f, 50f + k * 4.5f, 78f, 50f + k * 4.5f).width(0.4f)
        // Arka salıncak, egzoz, ön çatal, gidon, far
        line(28f, 62f, 58f, 58f)
        path(closed = true) { moveTo(40f, 66f); lineTo(64f, 64f); lineTo(66f, 68f); lineTo(42f, 70f) }.detail()
        line(113f, 62f, 94f, 22f)
        line(109f, 62f, 90f, 23f)
        line(92f, 22f, 86f, 16f)
        line(86f, 16f, 80f, 17f)
        path(closed = true) { moveTo(96f, 26f); cubicTo(102f, 25f, 104f, 31f, 99f, 33f); lineTo(95f, 33f) }.detail()
        path { moveTo(98f, 44f); cubicTo(108f, 40f, 124f, 44f, 130f, 54f) }.detail()
    }

    private fun airplane() = sketch("airplane", "Uçak", 160f, 70f, listOf("uçak", "hava", "taşıt", "plane")) {
        // Arka kanat ve dikey kuyruk
        path(closed = true) { moveTo(22f, 32f); lineTo(8f, 6f); lineTo(18f, 6f); lineTo(40f, 30f) }
        path(closed = true) { moveTo(14f, 36f); lineTo(4f, 44f); lineTo(12f, 45f); lineTo(30f, 38f) }
        // Gövde
        path(closed = true) {
            moveTo(20f, 30f)
            lineTo(128f, 28f)
            cubicTo(142f, 28f, 154f, 32f, 156f, 37f)
            cubicTo(154f, 42f, 142f, 45f, 128f, 45f)
            lineTo(34f, 44f)
            cubicTo(26f, 44f, 20f, 40f, 16f, 36f)
            cubicTo(16f, 33f, 17f, 31f, 20f, 30f)
        }
        // Kokpit camı ve yolcu pencereleri
        path(closed = true) { moveTo(140f, 32f); lineTo(150f, 34f); lineTo(146f, 35.5f); lineTo(139f, 35f) }.detail()
        for (k in 0..13) circle(44f + k * 6.4f, 34f, 1.3f).width(0.5f)
        line(118f, 30f, 118f, 44f).detail()
        // Kanat ve motor
        path(closed = true) { moveTo(86f, 42f); lineTo(58f, 66f); lineTo(70f, 66f); lineTo(104f, 43f) }
        roundRect(70f, 50f, 20f, 7f, 3.5f)
        line(86f, 50f, 86f, 57f).width(0.4f)
    }

    private fun sailboat() = sketch("sailboat", "Yelkenli", 140f, 100f, listOf("yelkenli", "tekne", "gemi", "deniz", "boat")) {
        // Direk ve yelkenler
        line(70f, 8f, 70f, 70f)
        path(closed = true) { moveTo(72f, 10f); cubicTo(96f, 30f, 110f, 50f, 114f, 66f); lineTo(72f, 66f) }
        path(closed = true) { moveTo(68f, 16f); cubicTo(52f, 34f, 40f, 50f, 34f, 66f); lineTo(68f, 66f) }
        path(closed = true) { moveTo(70f, 8f); lineTo(80f, 11f); lineTo(70f, 14f) }.detail()
        // Gövde
        path(closed = true) { moveTo(18f, 70f); lineTo(124f, 70f); cubicTo(118f, 80f, 108f, 86f, 96f, 86f); lineTo(40f, 86f); cubicTo(30f, 86f, 22f, 80f, 18f, 70f) }
        line(26f, 76f, 118f, 76f).detail()
        for (k in 0..3) circle(48f + k * 14f, 80.5f, 1.8f).detail()
        // Dalgalar
        for (row in 0..1) {
            path {
                moveTo(4f, 90f + row * 6f)
                for (k in 0..6) quadTo(13.5f + k * 19f, 86f + row * 6f, 23f + k * 19f, 90f + row * 6f)
            }.width(0.5f)
        }
    }

    private fun train() = sketch("train", "Tren", 180f, 76f, listOf("tren", "lokomotif", "raylı", "train")) {
        guide(2f, 72f, 178f, 72f)
        // Ön (burun) vagon
        path(closed = true) {
            moveTo(96f, 62f)
            lineTo(96f, 12f)
            lineTo(140f, 12f)
            cubicTo(158f, 12f, 172f, 30f, 174f, 50f)
            lineTo(174f, 62f)
        }
        path(closed = true) { moveTo(142f, 18f); cubicTo(154f, 20f, 163f, 30f, 166f, 40f); lineTo(142f, 40f) }.detail()
        for (k in 0..2) roundRect(102f + k * 13f, 20f, 10f, 14f, 1.5f).detail()
        line(96f, 46f, 174f, 46f).detail()
        circle(168f, 54f, 2.5f).detail()
        // Arka vagon
        rect(8f, 12f, 84f, 50f)
        for (k in 0..4) roundRect(13f + k * 15.5f, 20f, 11f, 14f, 1.5f).detail()
        line(8f, 46f, 92f, 46f).detail()
        line(92f, 52f, 96f, 52f)
        // Bojiler ve raylar
        for (x in listOf(24f, 44f, 66f, 86f, 112f, 132f, 156f)) circle(x, 65f, 5.5f)
        line(2f, 72f, 178f, 72f).width(0.8f)
    }
}
