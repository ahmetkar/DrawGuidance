package com.karapp.drawguidance.content.library.sketch

import com.karapp.drawguidance.content.library.LibShape

/** "Hayvanlar": kedi, köpek, kuş, balık, at, tavşan, kelebek, baykuş, kaplumbağa, fil. */
internal object AnimalSketches {

    val shapes: List<LibShape> by lazy {
        listOf(cat(), dog(), bird(), fish(), horse(), rabbit(), butterfly(), owl(), turtle(), elephant())
    }

    private fun cat() = sketch("cat", "Kedi", 84f, 100f, listOf("kedi", "evcil", "hayvan", "cat")) {
        // Kuyruk (gövdenin arkasından öne kıvrılır)
        path(closed = true) { moveTo(58f, 93f); cubicTo(80f, 93f, 84f, 70f, 73f, 58f); cubicTo(70f, 55.5f, 66.5f, 57.5f, 68f, 61f); cubicTo(76f, 72f, 72f, 86f, 57f, 87f) }
        // Gövde
        blob(40f, 44f, 54f, 50f, 60f, 66f, 62f, 84f, 56f, 96f, 40f, 98f, 24f, 96f, 18f, 84f, 20f, 66f, 26f, 50f)
        // Ön bacaklar ve patiler
        path { moveTo(33f, 68f); cubicTo(32f, 78f, 32f, 88f, 33f, 95f) }.detail()
        path { moveTo(47f, 68f); cubicTo(48f, 78f, 48f, 88f, 47f, 95f) }.detail()
        ellipse(30f, 96f, 5f, 2.6f)
        ellipse(50f, 96f, 5f, 2.6f)
        // Göğüs tüyleri
        path { moveTo(34f, 50f); lineTo(37f, 55f); lineTo(40f, 50f); lineTo(43f, 55f); lineTo(46f, 50f) }.width(0.45f)
        // Kafa ve kulaklar
        mirrored(42f) {
            path(closed = true) { moveTo(27f, 22f); lineTo(25f, 6f); lineTo(37f, 16f) }
            path { moveTo(28.5f, 18f); lineTo(27.5f, 10.5f); lineTo(33.5f, 15.5f) }.width(0.45f)
        }
        ellipse(42f, 30f, 18f, 15f)
        // Gözler (dikey göz bebeği), burun, ağız, bıyıklar
        mirrored(42f) {
            ellipse(35f, 28f, 3.6f, 2.8f)
            ellipse(35f, 28f, 0.9f, 2.4f).detail()
            for (k in -1..1) line(33f, 37f + k * 1.5f, 22f, 36f + k * 3f).width(0.4f)
        }
        path(closed = true) { moveTo(40.2f, 34f); lineTo(43.8f, 34f); lineTo(42f, 36f) }.detail()
        path { moveTo(42f, 36f); lineTo(42f, 38f); cubicTo(41f, 40f, 38.5f, 40f, 38f, 38.5f) }.detail()
        path { moveTo(42f, 38f); cubicTo(43f, 40f, 45.5f, 40f, 46f, 38.5f) }.detail()
    }

    private fun dog() = sketch("dog", "Köpek", 100f, 100f, listOf("köpek", "evcil", "hayvan", "dog")) {
        // Kuyruk
        path { moveTo(80f, 80f); cubicTo(92f, 76f, 96f, 64f, 92f, 56f) }
        path { moveTo(80f, 84f); cubicTo(95f, 80f, 99f, 66f, 94f, 55f) }
        // Arka bacak (kalça) ve ayak
        ellipse(70f, 80f, 13f, 12f)
        capsule(60f, 95f, 78f, 95f, 3.2f, 3f)
        // Gövde
        blob(38f, 38f, 52f, 40f, 66f, 50f, 78f, 64f, 82f, 80f, 76f, 93f, 60f, 97f, 44f, 96f, 36f, 82f, 32f, 64f)
        // Ön bacaklar ve patiler
        capsule(38f, 60f, 36f, 93f, 4.6f, 3.6f)
        capsule(47f, 62f, 47f, 93f, 4.6f, 3.6f)
        ellipse(35f, 95f, 5f, 2.6f)
        ellipse(48f, 95f, 5f, 2.6f)
        // Tasma
        path { moveTo(33f, 42f); cubicTo(38f, 46f, 46f, 46f, 50f, 42f) }.width(0.8f)
        circle(41f, 47f, 1.6f).detail()
        // Kafa: kafatası ve burun
        blob(26f, 14f, 38f, 12f, 47f, 20f, 47f, 31f, 41f, 38f, 28f, 40f, 14f, 40f, 7f, 36f, 7f, 30f, 13f, 27f, 21f, 24f, 23f, 18f)
        // Sarkık kulak
        path(closed = true) { moveTo(36f, 15f); cubicTo(44f, 14f, 50f, 24f, 49f, 38f); cubicTo(48f, 43f, 42f, 43f, 40f, 37f); cubicTo(38f, 30f, 37f, 22f, 36f, 15f) }
        // Göz, burun, ağız
        circle(28f, 25f, 2.2f)
        circle(28.6f, 24.4f, 0.7f).detail()
        ellipse(9.5f, 31f, 3f, 2.3f)
        path { moveTo(9f, 36f); cubicTo(14f, 39f, 20f, 38f, 24f, 35f) }.detail()
        path { moveTo(20f, 20f); cubicTo(22f, 18f, 26f, 18f, 30f, 20f) }.width(0.5f)
    }

    private fun bird() = sketch("bird", "Kuş", 90f, 72f, listOf("kuş", "serçe", "dal", "hayvan", "bird")) {
        // Dal ve yapraklar
        path { moveTo(2f, 62f); cubicTo(30f, 58f, 60f, 60f, 88f, 55f) }
        path(closed = true) { moveTo(70f, 58f); cubicTo(74f, 64f, 82f, 68f, 86f, 66f); cubicTo(84f, 60f, 76f, 57f, 70f, 58f) }
        path(closed = true) { moveTo(14f, 61f); cubicTo(12f, 66f, 6f, 70f, 2f, 69f); cubicTo(4f, 64f, 9f, 61f, 14f, 61f) }
        // Kuyruk
        path(closed = true) { moveTo(62f, 36f); lineTo(88f, 26f); lineTo(86f, 34f); lineTo(64f, 44f) }
        // Gövde ve kafa (tek çizgi)
        blob(26f, 14f, 34f, 16f, 40f, 24f, 52f, 26f, 64f, 34f, 64f, 44f, 54f, 52f, 40f, 54f, 28f, 48f, 22f, 38f, 18f, 26f, 20f, 18f)
        // Kanat
        path(closed = true) { moveTo(34f, 32f); cubicTo(44f, 28f, 58f, 30f, 66f, 38f); cubicTo(58f, 44f, 44f, 44f, 34f, 32f) }
        for (k in 0..2) path { moveTo(46f + k * 6f, 34f + k * 0.8f); lineTo(50f + k * 6f, 40f) }.width(0.4f)
        // Gaga, göz
        path(closed = true) { moveTo(19f, 21f); lineTo(9f, 24f); lineTo(19f, 26f) }
        circle(25f, 21f, 1.8f)
        // Bacaklar
        line(38f, 53f, 36f, 60f).detail()
        line(46f, 53f, 46f, 59.5f).detail()
    }

    private fun fish() = sketch("fish", "Balık", 110f, 62f, listOf("balık", "deniz", "su", "hayvan", "fish")) {
        // Kuyruk ve yüzgeçler
        path(closed = true) { moveTo(84f, 31f); cubicTo(92f, 20f, 98f, 14f, 106f, 10f); cubicTo(102f, 22f, 102f, 40f, 106f, 52f); cubicTo(98f, 48f, 92f, 42f, 84f, 31f) }
        path(closed = true) { moveTo(42f, 12f); cubicTo(48f, 2f, 60f, 2f, 68f, 12f) }
        path(closed = true) { moveTo(46f, 50f); cubicTo(50f, 58f, 58f, 58f, 62f, 50f) }
        // Gövde
        path(closed = true) { moveTo(6f, 31f); cubicTo(22f, 6f, 64f, 4f, 86f, 31f); cubicTo(64f, 56f, 22f, 56f, 6f, 31f) }
        // Solungaç, göz, ağız, pullar
        path { moveTo(28f, 16f); cubicTo(34f, 24f, 34f, 38f, 28f, 46f) }.detail()
        circle(18f, 27f, 3.8f)
        circle(18.8f, 26.4f, 1.4f).detail()
        path { moveTo(6f, 31f); lineTo(11f, 33f) }.detail()
        for (row in 0..2) for (col in 0..3) {
            val x = 40f + col * 10f + (if (row % 2 == 1) 5f else 0f)
            val y = 22f + row * 9f
            if (x < 78f) arc(x, y, 4.5f, 4.5f, -70f, 140f).width(0.45f)
        }
        path(closed = true) { moveTo(34f, 34f); cubicTo(40f, 36f, 44f, 42f, 42f, 46f); cubicTo(38f, 44f, 34f, 40f, 34f, 34f) }.detail()
    }

    private fun horse() = sketch("horse", "At", 130f, 110f, listOf("at", "tay", "çiftlik", "hayvan", "horse")) {
        // Uzaktaki bacaklar
        capsule(84f, 60f, 81f, 82f, 4.2f, 3.2f); capsule(81f, 82f, 83f, 101f, 3.2f, 2.7f)
        capsule(42f, 60f, 47f, 82f, 5f, 3.4f); capsule(47f, 82f, 45f, 101f, 3.4f, 2.7f)
        // Kuyruk
        path(closed = true) { moveTo(26f, 42f); cubicTo(14f, 50f, 10f, 70f, 14f, 88f); cubicTo(18f, 80f, 20f, 66f, 30f, 50f) }
        for (k in 0..2) path { moveTo(24f - k * 2f, 50f + k * 6f); cubicTo(20f - k * 2f, 62f, 18f - k, 72f, 17f - k, 82f) }.width(0.4f)
        // Gövde, boyun ve baş (tek siluet)
        blob(
            24f, 46f, 30f, 38f, 48f, 34f, 70f, 35f, 84f, 32f, 92f, 22f, 100f, 11f, 106f, 7f, 112f, 9f, 120f, 18f, 126f, 25f,
            123f, 31f, 115f, 30f, 107f, 27f, 101f, 35f, 98f, 47f, 95f, 58f, 86f, 64f, 60f, 66f, 38f, 64f, 28f, 59f,
        )
        // Yakın bacaklar ve toynaklar
        capsule(92f, 58f, 94f, 82f, 4.4f, 3.2f); capsule(94f, 82f, 94f, 101f, 3.2f, 2.8f)
        capsule(32f, 58f, 29f, 82f, 5.2f, 3.4f); capsule(29f, 82f, 33f, 101f, 3.4f, 2.8f)
        for (x in listOf(94f, 83f, 33f, 45f)) rect(x - 3.4f, 101f, 6.8f, 4f).detail()
        // Yele
        path { moveTo(84f, 32f); cubicTo(88f, 28f, 90f, 24f, 94f, 18f); cubicTo(96f, 14f, 100f, 10f, 104f, 8f) }.width(0.6f)
        for (k in 0..4) path { moveTo(86f + k * 3.6f, 30f - k * 4.6f); cubicTo(84f + k * 3.6f, 34f - k * 4.6f, 82f + k * 3.6f, 36f - k * 4.6f, 80f + k * 3.6f, 38f - k * 4.6f) }.width(0.4f)
        // Kulak, göz, burun deliği, ağız
        path(closed = true) { moveTo(104f, 8f); lineTo(104f, 1f); lineTo(109f, 7.5f) }
        ellipse(112f, 15f, 2f, 1.4f)
        circle(122f, 25.5f, 1f).detail()
        path { moveTo(120f, 29.5f); lineTo(116f, 28.5f) }.detail()
    }

    private fun rabbit() = sketch("rabbit", "Tavşan", 80f, 100f, listOf("tavşan", "hayvan", "orman", "rabbit")) {
        // Kulaklar
        ellipse(33f, 18f, 5.5f, 16f, -10f)
        ellipse(33f, 19f, 2.5f, 11f, -10f).detail()
        ellipse(49f, 18f, 5.5f, 16f, 10f)
        ellipse(49f, 19f, 2.5f, 11f, 10f).detail()
        // Kuyruk ve gövde
        puff(63f, 84f, 6f, 5f, 7, depth = 0.25f)
        blob(41f, 48f, 56f, 56f, 62f, 72f, 60f, 88f, 52f, 96f, 30f, 96f, 22f, 88f, 20f, 72f, 26f, 56f)
        // Arka ayak ve ön patiler
        ellipse(26f, 95f, 9f, 3.4f)
        ellipse(36f, 88f, 3.4f, 5f).detail()
        ellipse(46f, 88f, 3.4f, 5f).detail()
        // Kafa
        ellipse(41f, 42f, 15f, 13f)
        mirrored(41f) {
            circle(35f, 39f, 2.3f)
            circle(35.6f, 38.4f, 0.7f).detail()
            for (k in -1..1) line(36f, 47f + k * 1.3f, 24f, 46f + k * 3f).width(0.4f)
        }
        path(closed = true) { moveTo(39f, 44.5f); lineTo(43f, 44.5f); lineTo(41f, 46.5f) }.detail()
        path { moveTo(41f, 46.5f); lineTo(41f, 48f); cubicTo(40f, 49.5f, 38f, 49.5f, 37.5f, 48.5f) }.detail()
        path { moveTo(41f, 48f); cubicTo(42f, 49.5f, 44f, 49.5f, 44.5f, 48.5f) }.detail()
    }

    private fun butterfly() = sketch("butterfly", "Kelebek", 100f, 82f, listOf("kelebek", "böcek", "kanat", "butterfly")) {
        mirrored(50f) {
            // Üst ve alt kanat
            path(closed = true) { moveTo(48f, 30f); cubicTo(40f, 8f, 14f, 0f, 6f, 10f); cubicTo(0f, 22f, 14f, 38f, 47f, 40f) }
            path(closed = true) { moveTo(47f, 42f); cubicTo(26f, 42f, 12f, 54f, 16f, 68f); cubicTo(20f, 78f, 36f, 74f, 48f, 52f) }
            // Kanat desenleri
            ellipse(24f, 20f, 8f, 6f, -20f).detail()
            circle(14f, 12f, 2.6f).detail()
            circle(30f, 60f, 5f).detail()
            path { moveTo(47f, 34f); cubicTo(36f, 30f, 24f, 28f, 12f, 20f) }.width(0.4f)
            path { moveTo(47f, 46f); cubicTo(36f, 52f, 28f, 58f, 20f, 66f) }.width(0.4f)
            // Duyargalar
            path { moveTo(48.5f, 16f); cubicTo(46f, 8f, 42f, 4f, 36f, 3f) }
            circle(35.5f, 3f, 1.4f).detail()
        }
        // Gövde ve baş
        capsule(50f, 22f, 50f, 64f, 3.2f, 2f)
        circle(50f, 17f, 4f)
        for (k in 1..5) line(47.6f, 26f + k * 6f, 52.4f, 26f + k * 6f).width(0.35f)
    }

    private fun owl() = sketch("owl", "Baykuş", 76f, 100f, listOf("baykuş", "kuş", "gece", "orman", "owl")) {
        // Dal
        path { moveTo(2f, 90f); cubicTo(24f, 87f, 52f, 88f, 74f, 85f) }
        path { moveTo(2f, 95f); cubicTo(24f, 92f, 52f, 93f, 74f, 90f) }
        // Gövde ve kulak tüyleri
        path(closed = true) {
            moveTo(16f, 22f); lineTo(14f, 6f); lineTo(26f, 14f)
            cubicTo(34f, 11f, 42f, 11f, 50f, 14f)
            lineTo(62f, 6f); lineTo(60f, 22f)
            cubicTo(68f, 34f, 70f, 60f, 60f, 78f)
            cubicTo(52f, 90f, 24f, 90f, 16f, 78f)
            cubicTo(6f, 60f, 8f, 34f, 16f, 22f)
        }
        // Yüz diski, gözler, gaga
        mirrored(38f) {
            circle(27f, 32f, 11f).detail()
            circle(27f, 32f, 6.5f)
            circle(27f, 32f, 3f).detail()
            circle(28.5f, 30.5f, 1f).detail()
            // Kanat
            path { moveTo(14f, 44f); cubicTo(10f, 58f, 14f, 72f, 22f, 80f) }.detail()
        }
        path(closed = true) { moveTo(35f, 40f); lineTo(41f, 40f); lineTo(38f, 47f) }
        // Göğüs tüyleri
        for (row in 0..3) for (col in 0..2) {
            val x = 30f + col * 8f + (if (row % 2 == 1) 4f else 0f)
            val y = 54f + row * 6f
            if (x < 50f) path { moveTo(x - 3f, y); lineTo(x, y + 2.6f); lineTo(x + 3f, y) }.width(0.45f)
        }
        // Pençeler
        for (x in listOf(30f, 46f)) for (k in -1..1) line(x, 84f, x + k * 2.2f, 88f).detail()
    }

    private fun turtle() = sketch("turtle", "Kaplumbağa", 120f, 72f, listOf("kaplumbağa", "sürüngen", "hayvan", "turtle")) {
        guide(2f, 68f, 118f, 68f)
        // Bacaklar, kuyruk, baş
        capsule(34f, 54f, 30f, 66f, 6f, 5f)
        capsule(78f, 54f, 82f, 66f, 6f, 5f)
        path(closed = true) { moveTo(18f, 50f); lineTo(6f, 56f); lineTo(18f, 56f) }
        path(closed = true) { moveTo(96f, 44f); cubicTo(102f, 34f, 116f, 34f, 116f, 44f); cubicTo(116f, 52f, 104f, 54f, 96f, 52f) }
        circle(108f, 42f, 1.6f)
        path { moveTo(110f, 48f); cubicTo(112f, 49f, 114f, 48.5f, 115f, 47f) }.detail()
        // Kabuk ve alt kenar
        path(closed = true) { moveTo(14f, 52f); cubicTo(18f, 16f, 94f, 14f, 100f, 52f) }
        rect(12f, 52f, 90f, 5f)
        // Kabuk desenleri (altıgenler)
        polygon(57f, 34f, 9f, 6, 30f)
        polygon(38f, 42f, 7.5f, 6, 30f)
        polygon(76f, 42f, 7.5f, 6, 30f)
        line(48.5f, 30f, 36f, 26f).detail(); line(65.5f, 30f, 78f, 26f).detail()
        line(57f, 25f, 57f, 20f).detail()
    }

    private fun elephant() = sketch("elephant", "Fil", 130f, 100f, listOf("fil", "hortum", "hayvan", "safari", "elephant")) {
        guide(2f, 96f, 128f, 96f)
        // Uzak bacaklar
        rect(38f, 66f, 13f, 28f)
        rect(80f, 66f, 13f, 28f)
        // Kuyruk
        path { moveTo(18f, 44f); cubicTo(12f, 52f, 10f, 60f, 11f, 66f) }
        path(closed = true) { moveTo(9f, 66f); lineTo(13f, 66f); lineTo(12f, 71f); lineTo(10f, 71f) }.detail()
        // Gövde
        blob(20f, 40f, 34f, 22f, 60f, 16f, 86f, 20f, 100f, 34f, 100f, 58f, 90f, 70f, 60f, 72f, 30f, 70f, 18f, 58f)
        // Yakın bacaklar
        path(closed = true) { moveTo(24f, 62f); lineTo(24f, 94f); lineTo(38f, 94f); lineTo(38f, 66f) }
        path(closed = true) { moveTo(70f, 66f); lineTo(70f, 94f); lineTo(84f, 94f); lineTo(84f, 64f) }
        for (x in listOf(26f, 30f, 34f, 72f, 76f, 80f)) arc(x + 1.5f, 94f, 1.6f, 1.6f, 180f, 180f).width(0.4f)
        // Baş ve hortum
        path(closed = true) {
            moveTo(92f, 26f)
            cubicTo(100f, 14f, 118f, 16f, 120f, 32f)
            cubicTo(122f, 44f, 122f, 60f, 124f, 76f)
            cubicTo(125f, 82f, 120f, 84f, 118f, 80f)
            cubicTo(116f, 66f, 114f, 54f, 110f, 50f)
            cubicTo(104f, 52f, 98f, 50f, 96f, 44f)
        }
        for (k in 0..4) line(117f + k * 0.5f, 58f + k * 5f, 122.5f + k * 0.2f, 57f + k * 5f).width(0.35f)
        // Kulak, göz, fildişi
        path(closed = true) { moveTo(96f, 24f); cubicTo(80f, 18f, 74f, 38f, 80f, 50f); cubicTo(86f, 58f, 96f, 52f, 98f, 44f) }
        circle(109f, 31f, 1.8f)
        path(closed = true) { moveTo(110f, 48f); cubicTo(106f, 54f, 102f, 56f, 98f, 55f); cubicTo(102f, 53f, 105f, 50f, 106f, 47f) }
    }
}
