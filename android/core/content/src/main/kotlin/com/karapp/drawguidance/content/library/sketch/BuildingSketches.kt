package com.karapp.drawguidance.content.library.sketch

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.VectorBuilder

/** "Yapılar": ev, iki katlı ev, perspektif ev, apartman, gökdelen, dükkan, ambar, kale kulesi, deniz feneri, köprü. */
internal object BuildingSketches {

    val shapes: List<LibShape> by lazy {
        listOf(house(), twoStory(), perspectiveHouse(), apartment(), skyscraper(), shop(), barn(), tower(), lighthouse(), bridge())
    }

    /** Kanatlı pencere: çerçeve ve artı kayıt. */
    private fun VectorBuilder.window(x: Float, y: Float, w: Float, h: Float, sill: Boolean = true) {
        rect(x, y, w, h)
        line(x + w / 2f, y, x + w / 2f, y + h).detail()
        line(x, y + h / 2f, x + w, y + h / 2f).detail()
        if (sill) line(x - 1.5f, y + h + 1.2f, x + w + 1.5f, y + h + 1.2f).detail()
    }

    private fun house() = sketch("house", "Ev", 100f, 92f, listOf("ev", "yapı", "çatı", "house", "home")) {
        guide(2f, 88f, 98f, 88f)
        rect(14f, 42f, 72f, 46f)
        path(closed = true) { moveTo(8f, 44f); lineTo(50f, 10f); lineTo(92f, 44f) }
        path { moveTo(66f, 23f); lineTo(66f, 12f); lineTo(75f, 12f); lineTo(75f, 30.5f) }
        rect(64.5f, 9f, 12f, 3f).detail()
        // Kapı ve pencereler
        path(closed = true) { moveTo(42f, 88f); lineTo(42f, 64f); cubicTo(42f, 58f, 58f, 58f, 58f, 64f); lineTo(58f, 88f) }
        circle(54.5f, 75f, 1.1f).detail()
        window(20f, 52f, 16f, 15f)
        window(64f, 52f, 16f, 15f)
        // Tavan arası penceresi
        circle(50f, 32f, 5f)
        line(45f, 32f, 55f, 32f).detail()
    }

    private fun twoStory() = sketch("house_two_story", "İki katlı ev", 110f, 112f, listOf("ev", "iki katlı", "villa", "yapı", "house")) {
        guide(2f, 108f, 108f, 108f)
        rect(12f, 40f, 86f, 68f)
        path(closed = true) { moveTo(6f, 42f); lineTo(30f, 12f); lineTo(80f, 12f); lineTo(104f, 42f) }
        line(12f, 74f, 98f, 74f).detail()
        // Üst kat pencereleri
        window(20f, 48f, 16f, 18f)
        window(47f, 48f, 16f, 18f)
        window(74f, 48f, 16f, 18f)
        // Alt kat: kapı, saçak, pencereler
        rect(47f, 82f, 16f, 26f)
        line(55f, 82f, 55f, 108f).detail()
        path(closed = true) { moveTo(43f, 82f); lineTo(55f, 76f); lineTo(67f, 82f) }.detail()
        window(20f, 82f, 16f, 16f)
        window(74f, 82f, 16f, 16f)
        // Basamaklar
        rect(44f, 108f, 22f, 2f).detail()
    }

    private fun perspectiveHouse() = sketch("house_perspective", "Ev (perspektif)", 140f, 92f, listOf("perspektif", "kaçış noktası", "ev", "yapı", "iki nokta")) {
        // Ufuk çizgisi ve iki kaçış noktasına giden kılavuzlar
        guide(2f, 40f, 138f, 40f)
        guide(2f, 40f, 62f, 24f); guide(2f, 40f, 62f, 76f); guide(2f, 40f, 87f, 8f)
        guide(138f, 40f, 62f, 24f); guide(138f, 40f, 62f, 76f)
        circle(2.5f, 40f, 1.2f).width(0.5f)
        circle(137.5f, 40f, 1.2f).width(0.5f)
        // Duvarlar (sol duvar sol kaçış noktasına, sağ duvar sağa)
        points(listOf(p(22f, 34.67f), p(62f, 24f), p(62f, 76f), p(22f, 52f)), closed = true)
        points(listOf(p(62f, 24f), p(112f, 34.53f), p(112f, 52.3f), p(62f, 76f)), closed = true)
        // Çatı: ön eğim ve sağdaki üçgen alın
        points(listOf(p(22f, 34.67f), p(62f, 24f), p(87f, 8f), p(47f, 23.06f)), closed = true)
        points(listOf(p(62f, 24f), p(87f, 8f), p(112f, 34.53f)), closed = true)
        // Kapı (sol duvar) ve pencere (sağ duvar) perspektife uygun
        points(listOf(p(36f, 42.1f), p(46f, 42.8f), p(46f, 66.4f), p(36f, 60.4f)), closed = true).detail()
        points(listOf(p(76f, 39.7f), p(96f, 39.8f), p(96f, 48.4f), p(76f, 52.4f)), closed = true).detail()
        line(86f, 39.75f, 86f, 50.4f).width(0.4f)
    }

    private fun p(x: Float, y: Float) = com.karapp.drawguidance.content.vector.P(x, y)

    private fun apartment() = sketch("apartment", "Apartman", 80f, 132f, listOf("apartman", "bina", "şehir", "yapı", "building")) {
        guide(2f, 128f, 78f, 128f)
        rect(10f, 10f, 60f, 118f)
        rect(7f, 6f, 66f, 4f).detail()
        for (row in 0..6) for (col in 0..3) {
            window(15f + col * 14f, 16f + row * 14.5f, 8f, 9f, sill = false)
        }
        // Giriş
        rect(31f, 116f, 18f, 12f)
        line(40f, 116f, 40f, 128f).detail()
        path(closed = true) { moveTo(28f, 116f); lineTo(52f, 116f); lineTo(50f, 113f); lineTo(30f, 113f) }.detail()
    }

    private fun skyscraper() = sketch("skyscraper", "Gökdelen", 64f, 150f, listOf("gökdelen", "bina", "kule", "şehir", "yapı")) {
        guide(2f, 147f, 62f, 147f)
        line(32f, 2f, 32f, 14f)
        points(listOf(p(24f, 22f), p(32f, 14f), p(40f, 22f)), closed = false)
        rect(22f, 22f, 20f, 16f)
        rect(16f, 38f, 32f, 26f)
        rect(10f, 64f, 44f, 83f)
        // Dikey pencere şeritleri
        for (k in 0..2) line(27f + k * 5f, 25f, 27f + k * 5f, 36f).width(0.4f)
        for (k in 0..4) line(20f + k * 6f, 41f, 20f + k * 6f, 61f).width(0.4f)
        for (k in 0..6) line(14f + k * 6f, 68f, 14f + k * 6f, 136f).width(0.4f)
        for (k in 0..5) line(10f, 76f + k * 11f, 54f, 76f + k * 11f).width(0.4f)
        rect(26f, 138f, 12f, 9f).detail()
    }

    private fun shop() = sketch("shop", "Dükkan", 110f, 92f, listOf("dükkan", "mağaza", "market", "vitrin", "shop")) {
        guide(2f, 88f, 108f, 88f)
        rect(10f, 22f, 90f, 66f)
        // Tabela
        rect(22f, 8f, 66f, 14f)
        // Tente (dilimli ve dalgalı kenarlı)
        path(closed = true) {
            moveTo(6f, 30f)
            lineTo(104f, 30f)
            lineTo(104f, 42f)
            for (k in 0 until 7) {
                val x0 = 104f - k * 14f
                quadTo(x0 - 7f, 48f, x0 - 14f, 42f)
            }
            lineTo(6f, 30f)
        }
        for (k in 1..6) line(6f + k * 14f, 30f, 6f + k * 14f, 42f).width(0.45f)
        // Vitrin ve kapı
        rect(16f, 50f, 46f, 30f)
        line(39f, 50f, 39f, 80f).detail()
        rect(70f, 50f, 22f, 38f)
        rect(74f, 54f, 14f, 18f).detail()
        circle(88f, 72f, 1f).detail()
        line(12f, 84f, 66f, 84f).detail()
    }

    private fun barn() = sketch("barn", "Ambar", 110f, 92f, listOf("ambar", "ahır", "çiftlik", "köy", "barn")) {
        guide(2f, 88f, 108f, 88f)
        // Mansart çatı
        path(closed = true) {
            moveTo(12f, 88f); lineTo(12f, 44f); lineTo(22f, 22f); lineTo(55f, 8f); lineTo(88f, 22f); lineTo(98f, 44f); lineTo(98f, 88f)
        }
        line(12f, 44f, 98f, 44f).detail()
        // Büyük kapı çarpıları
        rect(35f, 54f, 40f, 34f)
        line(55f, 54f, 55f, 88f).detail()
        line(35f, 54f, 55f, 88f).width(0.45f); line(55f, 54f, 35f, 88f).width(0.45f)
        line(55f, 54f, 75f, 88f).width(0.45f); line(75f, 54f, 55f, 88f).width(0.45f)
        // Samanlık kapağı
        rect(47f, 24f, 16f, 14f)
        line(47f, 24f, 63f, 38f).width(0.45f); line(63f, 24f, 47f, 38f).width(0.45f)
    }

    private fun tower() = sketch("castle_tower", "Kale kulesi", 64f, 122f, listOf("kale", "kule", "şato", "burç", "castle")) {
        guide(2f, 118f, 62f, 118f)
        // Mazgallar
        path(closed = true) {
            moveTo(8f, 28f); lineTo(8f, 10f)
            val seg = 48f / 7f
            for (i in 0 until 7) {
                val x0 = 8f + i * seg; val x1 = x0 + seg
                if (i % 2 == 0) lineTo(x1, 10f) else { lineTo(x0, 16f); lineTo(x1, 16f); lineTo(x1, 10f) }
            }
            lineTo(56f, 28f)
        }
        path(closed = true) { moveTo(12f, 28f); lineTo(52f, 28f); lineTo(50f, 118f); lineTo(14f, 118f) }
        // Taş sıraları, pencere, kapı
        for (k in 1..7) line(13f, 28f + k * 11f, 51f, 28f + k * 11f).width(0.35f).alpha(0.7f)
        path(closed = true) { moveTo(28f, 56f); lineTo(28f, 46f); cubicTo(28f, 40f, 36f, 40f, 36f, 46f); lineTo(36f, 56f) }
        path(closed = true) { moveTo(24f, 118f); lineTo(24f, 98f); cubicTo(24f, 88f, 40f, 88f, 40f, 98f); lineTo(40f, 118f) }
        line(32f, 91f, 32f, 118f).detail()
    }

    private fun lighthouse() = sketch("lighthouse", "Deniz feneri", 64f, 122f, listOf("fener", "deniz", "kule", "sahil", "lighthouse")) {
        guide(2f, 116f, 62f, 116f)
        path(closed = true) { moveTo(32f, 4f); lineTo(42f, 14f); lineTo(22f, 14f) }
        rect(22f, 14f, 20f, 16f)
        for (k in 1..2) line(22f + k * 6.7f, 14f, 22f + k * 6.7f, 30f).detail()
        rect(16f, 30f, 32f, 4f)
        path(closed = true) { moveTo(20f, 34f); lineTo(44f, 34f); lineTo(50f, 116f); lineTo(14f, 116f) }
        // Şeritler
        points(listOf(p(21.2f, 52f), p(42.8f, 52f), p(44.1f, 70f), p(19.9f, 70f)), closed = true).detail()
        points(listOf(p(18.5f, 88f), p(45.5f, 88f), p(46.9f, 106f), p(17.1f, 106f)), closed = true).detail()
        // Işık huzmeleri
        line(44f, 18f, 60f, 12f).width(0.4f).dashed(); line(44f, 24f, 60f, 28f).width(0.4f).dashed()
        line(20f, 18f, 4f, 12f).width(0.4f).dashed(); line(20f, 24f, 4f, 28f).width(0.4f).dashed()
        // Kayalık
        path { moveTo(4f, 116f); cubicTo(8f, 110f, 12f, 110f, 16f, 113f) }.detail()
        path { moveTo(48f, 113f); cubicTo(52f, 109f, 58f, 110f, 60f, 116f) }.detail()
    }

    private fun bridge() = sketch("bridge", "Köprü", 180f, 72f, listOf("köprü", "kemer", "nehir", "yapı", "bridge")) {
        // Yol, korkuluk, kemerler ve ayaklar
        rect(4f, 22f, 172f, 8f)
        line(4f, 12f, 176f, 12f)
        for (k in 0..21) line(4f + k * 8.19f, 12f, 4f + k * 8.19f, 22f).width(0.45f)
        path(closed = true) {
            moveTo(4f, 30f)
            lineTo(176f, 30f)
            lineTo(176f, 60f)
            lineTo(160f, 60f)
            arcTo(140f, 60f, 20f, 22f, 0f, -180f)
            lineTo(110f, 60f)
            arcTo(90f, 60f, 20f, 22f, 0f, -180f)
            lineTo(60f, 60f)
            arcTo(40f, 60f, 20f, 22f, 0f, -180f)
            lineTo(4f, 60f)
        }
        // Su
        for (row in 0..1) {
            path {
                moveTo(2f, 64f + row * 5f)
                for (k in 0..8) quadTo(12f + k * 20f, 61f + row * 5f, 22f + k * 20f, 64f + row * 5f)
            }.width(0.45f)
        }
    }
}
