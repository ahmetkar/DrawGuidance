package com.karapp.drawguidance.content.library.sketch

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.VectorBuilder

/** "Portreler": yüz oranları kılavuzlu yüzler (önden, 3/4, profil, çocuk), kafa kurgusu ve yüz ayrıntıları. */
internal object PortraitSketches {

    val shapes: List<LibShape> by lazy {
        listOf(faceFront(), faceThreeQuarter(), faceProfile(), childFace(), headConstruction(), eye(), nose(), lips(), ear(), hairShort())
    }

    /** Badem göz: üst ve alt kapak, iris, göz bebeği, kapak kıvrımı. */
    private fun VectorBuilder.almondEye(cx: Float, cy: Float, w: Float, h: Float, flip: Boolean = false) {
        val s = if (flip) -1f else 1f
        val l = cx - w / 2f; val r = cx + w / 2f
        // İç köşe biraz aşağıda, dış köşe biraz yukarıda
        val inner = if (flip) r else l
        val outer = if (flip) l else r
        path(closed = true) {
            moveTo(inner, cy + h * 0.08f)
            cubicTo(inner + s * w * 0.2f, cy - h * 0.7f, outer - s * w * 0.3f, cy - h * 0.75f, outer, cy - h * 0.08f)
            cubicTo(outer - s * w * 0.25f, cy + h * 0.55f, inner + s * w * 0.25f, cy + h * 0.6f, inner, cy + h * 0.08f)
        }
        circle(cx, cy, h * 0.42f).detail()
        circle(cx, cy, h * 0.17f).detail()
        // Kapak kıvrımı
        path { moveTo(inner + s * w * 0.12f, cy - h * 0.5f); cubicTo(inner + s * w * 0.35f, cy - h * 1.05f, outer - s * w * 0.3f, cy - h * 1.05f, outer - s * w * 0.05f, cy - h * 0.45f) }.width(0.45f)
    }

    private fun faceFront() = sketch("face_front", "Yüz (önden)", 80f, 100f, listOf("yüz", "portre", "kafa", "önden", "oran", "face")) {
        // Oran kılavuzları: orta çizgi, saç çizgisi, kaş, göz (kafanın ortası), burun ve ağız hizası
        guide(40f, 4f, 40f, 98f)
        guide(8f, 22f, 72f, 22f)
        guide(8f, 40f, 72f, 40f)
        guide(6f, 50f, 74f, 50f)
        guide(10f, 65f, 70f, 65f)
        guide(14f, 75f, 66f, 75f)
        // Kafa
        blob(40f, 6f, 57f, 9f, 67f, 22f, 69.5f, 40f, 68f, 56f, 64f, 70f, 55f, 84f, 40f, 93f, 25f, 84f, 16f, 70f, 12f, 56f, 10.5f, 40f, 13f, 22f, 23f, 9f)
        // Kulaklar
        mirrored(40f) {
            path { moveTo(68.5f, 47f); cubicTo(74f, 43f, 78f, 48f, 76f, 56f); cubicTo(75f, 62f, 72f, 66f, 67.5f, 66f) }
            path { moveTo(70f, 51f); cubicTo(73.5f, 50f, 74f, 55f, 72f, 58f) }.detail()
        }
        // Kaşlar ve gözler
        mirrored(40f) {
            path { moveTo(20f, 42.5f); cubicTo(23f, 38.5f, 30f, 38f, 35.5f, 41f) }.width(0.9f)
        }
        // Sol gözün iç köşesi sağda (buruna yakın), sağ gözünki solda
        almondEye(27.5f, 50f, 13f, 6f, flip = true)
        almondEye(52.5f, 50f, 13f, 6f, flip = false)
        // Burun: sırt izi, uç ve burun delikleri
        path { moveTo(36.5f, 52f); cubicTo(36f, 56f, 35f, 60f, 34.5f, 62f) }.width(0.45f)
        path { moveTo(33.5f, 64f); cubicTo(35f, 67.5f, 38f, 66.5f, 40f, 67.5f); cubicTo(42f, 66.5f, 45f, 67.5f, 46.5f, 64f) }.detail()
        mirrored(40f) { path { moveTo(33f, 62.5f); cubicTo(31.5f, 64f, 32.5f, 66f, 34.5f, 65.5f) }.detail() }
        // Ağız: üst dudak (yay), ağız çizgisi, alt dudak
        path { moveTo(31f, 75f); cubicTo(34f, 72.5f, 37f, 72f, 40f, 73.6f); cubicTo(43f, 72f, 46f, 72.5f, 49f, 75f) }.detail()
        path { moveTo(31f, 75f); cubicTo(35f, 76.5f, 45f, 76.5f, 49f, 75f) }
        path { moveTo(32.5f, 75.8f); cubicTo(35f, 80.5f, 45f, 80.5f, 47.5f, 75.8f) }.detail()
        // Boyun
        mirrored(40f) { path { moveTo(28f, 86f); cubicTo(28.5f, 91f, 28f, 95f, 26f, 100f) } }
    }

    private fun faceThreeQuarter() = sketch("face_three_quarter", "Yüz (3/4 açı)", 80f, 100f, listOf("yüz", "portre", "3/4", "açılı", "kafa", "face")) {
        // Kılavuz: yüzün eğik orta çizgisi ve göz/burun/ağız hizaları kafanın dönüşüne göre kavisli
        guideArc(62f, 50f, 20f, 46f, 140f, 80f)
        guideArc(40f, 50f, 34f, 4f, 0f, 180f)
        guideArc(40f, 65f, 32f, 4f, 0f, 180f)
        guideArc(40f, 75f, 28f, 4f, 0f, 180f)
        // Kafa: arka taraf geniş, yüz tarafı çene ve elmacık kemiğiyle
        blob(38f, 6f, 57f, 10f, 66f, 22f, 67f, 38f, 64f, 52f, 63f, 64f, 58f, 78f, 49f, 90f, 40f, 93f, 30f, 86f, 20f, 72f, 12f, 56f, 10f, 38f, 16f, 18f)
        // Kulak (arka tarafta)
        path { moveTo(19f, 49f); cubicTo(12f, 45f, 8f, 52f, 11f, 60f); cubicTo(13f, 66f, 17f, 68f, 21f, 66f) }
        path { moveTo(17f, 53f); cubicTo(13.5f, 53f, 13.5f, 58f, 16f, 61f) }.detail()
        // Kaşlar
        path { moveTo(31f, 42f); cubicTo(35f, 38f, 42f, 38f, 47f, 41f) }.width(0.9f)
        path { moveTo(54f, 41f); cubicTo(57f, 38.5f, 61f, 39f, 63f, 41f) }.width(0.9f)
        // Gözler (uzaktaki göz daha dar)
        path(closed = true) { moveTo(32f, 50.5f); cubicTo(35f, 45.5f, 42f, 45f, 46f, 49.5f); cubicTo(42f, 53f, 36f, 53f, 32f, 50.5f) }
        circle(39.5f, 49.5f, 2.5f).detail()
        circle(39.5f, 49.5f, 1f).detail()
        path(closed = true) { moveTo(54f, 49.5f); cubicTo(56f, 46f, 60.5f, 46f, 62.5f, 49f); cubicTo(60.5f, 52f, 56f, 52f, 54f, 49.5f) }
        circle(58.3f, 49.2f, 2.1f).detail()
        circle(58.3f, 49.2f, 0.9f).detail()
        // Burun: yüzün önüne taşan profil çizgisi
        path { moveTo(51f, 52f); cubicTo(53f, 56f, 57f, 61f, 58f, 64f); cubicTo(57f, 66.5f, 54f, 67f, 51f, 66f) }
        path { moveTo(46f, 64.5f); cubicTo(46f, 66.5f, 48.5f, 67.3f, 50f, 66f) }.detail()
        // Ağız
        path { moveTo(43f, 75f); cubicTo(46f, 73f, 50f, 72.5f, 52.5f, 74f); cubicTo(54.5f, 73f, 56.5f, 73.5f, 58f, 75f) }.detail()
        path { moveTo(43f, 75f); cubicTo(48f, 76.5f, 54f, 76.5f, 58f, 75f) }
        path { moveTo(44.5f, 75.8f); cubicTo(47f, 80f, 54f, 80f, 56.5f, 75.8f) }.detail()
        // Boyun
        path { moveTo(27f, 82f); cubicTo(28f, 90f, 27f, 95f, 24f, 100f) }
        path { moveTo(50f, 90f); cubicTo(51f, 94f, 52f, 97f, 54f, 100f) }
    }

    private fun faceProfile() = sketch("face_profile", "Yüz (profil)", 80f, 100f, listOf("yüz", "portre", "profil", "yandan", "kafa", "face")) {
        guide(4f, 50f, 78f, 50f)
        guide(4f, 65f, 78f, 65f)
        guide(4f, 75f, 78f, 75f)
        guide(4f, 40f, 78f, 40f)
        // Kafa arkası, tepe, alın, burun, dudaklar, çene ve boyun tek çizgi
        path {
            moveTo(30f, 97f)
            cubicTo(30f, 90f, 26f, 84f, 20f, 80f)
            cubicTo(10f, 72f, 6f, 58f, 7f, 44f)
            cubicTo(8f, 22f, 24f, 7f, 42f, 7f)
            cubicTo(58f, 7f, 66f, 20f, 66f, 34f)
            cubicTo(66f, 39f, 65f, 42f, 66f, 46f)
            cubicTo(68f, 52f, 71f, 58f, 73f, 63f)
            cubicTo(73.5f, 65f, 71f, 66f, 68f, 66f)
            cubicTo(68.5f, 68f, 68f, 70f, 69.5f, 71.5f)
            cubicTo(70f, 73f, 67.5f, 74.5f, 67f, 75f)
            cubicTo(68.5f, 76.5f, 68.5f, 78.5f, 66f, 79.5f)
            cubicTo(66f, 83f, 65f, 86f, 60f, 88f)
            cubicTo(55f, 89f, 50f, 87f, 47f, 87f)
            cubicTo(46f, 91f, 46f, 95f, 47f, 100f)
        }
        // Kulak
        path(closed = true) { moveTo(30f, 48f); cubicTo(24f, 44f, 20f, 50f, 22f, 58f); cubicTo(23f, 64f, 27f, 67f, 31f, 64f); cubicTo(33f, 60f, 33f, 52f, 30f, 48f) }
        path { moveTo(28f, 52f); cubicTo(25f, 52f, 25f, 58f, 28f, 60f) }.detail()
        // Göz (yandan üçgen), kaş, burun kanadı, ağız köşesi
        path(closed = true) { moveTo(55f, 50.5f); cubicTo(58f, 47.5f, 61f, 47f, 63.5f, 48f); cubicTo(62f, 51f, 59f, 52.5f, 55f, 50.5f) }
        path { moveTo(51f, 41f); cubicTo(55f, 38.5f, 60f, 38.5f, 64f, 40.5f) }.width(0.9f)
        path { moveTo(63f, 63f); cubicTo(62f, 60.5f, 64.5f, 59.5f, 66.5f, 61f) }.detail()
        path { moveTo(67f, 75f); cubicTo(65f, 75.4f, 63.5f, 75f, 62.5f, 74f) }.detail()
    }

    private fun childFace() = sketch("face_child", "Çocuk yüzü", 80f, 90f, listOf("çocuk", "bebek", "yüz", "portre", "kid")) {
        // Çocukta gözler kafanın ortasının altında, yüz yuvarlak
        guide(40f, 4f, 40f, 88f)
        guide(6f, 52f, 74f, 52f)
        guide(10f, 66f, 70f, 66f)
        blob(40f, 6f, 59f, 10f, 70f, 26f, 72f, 46f, 68f, 64f, 58f, 78f, 40f, 84f, 22f, 78f, 12f, 64f, 8f, 46f, 10f, 26f, 21f, 10f)
        mirrored(40f) {
            path { moveTo(71f, 48f); cubicTo(77f, 45f, 79f, 54f, 76f, 60f); cubicTo(74f, 63f, 71f, 63f, 69.5f, 62f) }
            circle(26f, 53f, 5f)
            circle(26f, 53f, 2.4f).detail()
            circle(27.5f, 51.5f, 1f).detail()
            path { moveTo(20f, 44f); cubicTo(23f, 42f, 28f, 42f, 31f, 43.5f) }.width(0.8f)
            // Yanak
            circle(22f, 64f, 4f).width(0.4f).dashed().alpha(0.5f)
        }
        path { moveTo(37.5f, 64f); cubicTo(39f, 66f, 41f, 66f, 42.5f, 64f) }.detail()
        path { moveTo(34f, 71f); cubicTo(37f, 74.5f, 43f, 74.5f, 46f, 71f) }
        // Saç tutamı
        path { moveTo(33f, 9f); cubicTo(36f, 14f, 40f, 15f, 45f, 13f); cubicTo(42f, 17f, 46f, 19f, 50f, 17f) }.detail()
        mirrored(40f) { path { moveTo(30f, 82f); cubicTo(30f, 85f, 29f, 88f, 27f, 90f) } }
    }

    private fun headConstruction() = sketch("head_construction", "Kafa kurgusu", 80f, 100f, listOf("kafa", "kurgu", "kılavuz", "loomis", "oran", "portre")) {
        // Kafatası küresi, yan kesit elipsi, orta çizgi ve kaş/burun/çene hizaları; çene çizgileri
        circle(40f, 38f, 28f)
        ellipse(40f, 38f, 20f, 28f).width(0.6f).dashed()
        guide(40f, 8f, 40f, 94f)
        guide(8f, 38f, 72f, 38f)
        guide(10f, 52f, 70f, 52f)
        guide(14f, 70f, 66f, 70f)
        guide(20f, 92f, 60f, 92f)
        path { moveTo(14f, 50f); cubicTo(16f, 66f, 26f, 84f, 40f, 92f); cubicTo(54f, 84f, 64f, 66f, 66f, 50f) }
        // Kaş çıkıntısı ve burun kutusu
        path { moveTo(24f, 52f); cubicTo(30f, 49f, 50f, 49f, 56f, 52f) }.detail()
        path(closed = true) { moveTo(36f, 52f); lineTo(44f, 52f); lineTo(46f, 70f); lineTo(34f, 70f) }.detail()
        text(3f, 35f, "1", 0.035f)
        text(3f, 58f, "2", 0.035f)
        text(3f, 78f, "3", 0.035f)
    }

    private fun eye() = sketch("eye_detail", "Göz", 100f, 50f, listOf("göz", "kirpik", "iris", "portre", "eye")) {
        // Kapak kıvrımı, üst ve alt kapak, iris, göz bebeği, ışık ve kirpikler
        path { moveTo(10f, 22f); cubicTo(26f, 3f, 70f, 1f, 90f, 18f) }.width(0.6f)
        path(closed = true) {
            moveTo(8f, 27f)
            cubicTo(24f, 7f, 68f, 5f, 92f, 25f)
            cubicTo(70f, 40f, 30f, 44f, 8f, 27f)
        }
        path { moveTo(12f, 30f); cubicTo(30f, 39f, 64f, 38f, 86f, 28f) }.width(0.45f)
        circle(50f, 25f, 12f)
        circle(50f, 25f, 5f)
        circle(55f, 20f, 2.4f).detail()
        // İrisin ışınsal dokusu
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            line(50f + 6.5f * kotlin.math.cos(a).toFloat(), 25f + 6.5f * kotlin.math.sin(a).toFloat(), 50f + 10.5f * kotlin.math.cos(a).toFloat(), 25f + 10.5f * kotlin.math.sin(a).toFloat()).width(0.3f).alpha(0.6f)
        }
        // Kirpikler
        for (k in 0..8) {
            val t = k / 8f
            val x = 20f + t * 66f
            val y = 14.5f - 7f * kotlin.math.sin(Math.PI * t).toFloat() + (if (t > 0.5f) (t - 0.5f) * 4f else 0f)
            line(x, y, x + 3f + t * 3f, y - 7f + t * 2f).width(0.5f)
        }
        // Gözyaşı kanalı
        path { moveTo(8f, 27f); cubicTo(10f, 29f, 12f, 29f, 13f, 27.5f) }.detail()
    }

    private fun nose() = sketch("nose_detail", "Burun", 60f, 70f, listOf("burun", "portre", "nose")) {
        guide(30f, 2f, 30f, 68f)
        path { moveTo(24f, 4f); cubicTo(26f, 20f, 25f, 34f, 20f, 44f) }.width(0.5f)
        path { moveTo(36f, 4f); cubicTo(34f, 20f, 35f, 34f, 40f, 44f) }.width(0.5f)
        // Burun ucu ve kanatlar
        path { moveTo(16f, 50f); cubicTo(10f, 52f, 11f, 61f, 18f, 60f); cubicTo(20f, 62f, 24f, 63f, 27f, 61f) }
        path { moveTo(44f, 50f); cubicTo(50f, 52f, 49f, 61f, 42f, 60f); cubicTo(40f, 62f, 36f, 63f, 33f, 61f) }
        path { moveTo(22f, 50f); cubicTo(24f, 44f, 36f, 44f, 38f, 50f); cubicTo(38f, 54f, 35f, 56f, 30f, 56f); cubicTo(25f, 56f, 22f, 54f, 22f, 50f) }.detail()
        path(closed = true) { moveTo(20f, 58.5f); cubicTo(22f, 56.5f, 26f, 57.5f, 27f, 60f); cubicTo(25f, 61f, 22f, 60.5f, 20f, 58.5f) }.detail()
        path(closed = true) { moveTo(40f, 58.5f); cubicTo(38f, 56.5f, 34f, 57.5f, 33f, 60f); cubicTo(35f, 61f, 38f, 60.5f, 40f, 58.5f) }.detail()
    }

    private fun lips() = sketch("lips_detail", "Dudaklar", 100f, 50f, listOf("dudak", "ağız", "portre", "lips", "mouth")) {
        // Üst dudak (yay biçimi), ağız çizgisi ve dolgun alt dudak
        path(closed = true) {
            moveTo(8f, 26f)
            cubicTo(20f, 16f, 34f, 9f, 43f, 12f)
            cubicTo(46f, 13f, 48f, 15f, 50f, 15f)
            cubicTo(52f, 15f, 54f, 13f, 57f, 12f)
            cubicTo(66f, 9f, 80f, 16f, 92f, 26f)
            cubicTo(78f, 25f, 64f, 24f, 50f, 26f)
            cubicTo(36f, 24f, 22f, 25f, 8f, 26f)
        }
        path(closed = true) {
            moveTo(8f, 26f)
            cubicTo(22f, 27f, 36f, 26f, 50f, 27.5f)
            cubicTo(64f, 26f, 78f, 27f, 92f, 26f)
            cubicTo(80f, 40f, 62f, 45f, 50f, 45f)
            cubicTo(38f, 45f, 20f, 40f, 8f, 26f)
        }
        path { moveTo(44f, 17f); cubicTo(47f, 21f, 53f, 21f, 56f, 17f) }.width(0.4f)
        path { moveTo(38f, 37f); cubicTo(44f, 40f, 56f, 40f, 62f, 37f) }.width(0.4f)
        guide(50f, 4f, 50f, 48f)
    }

    private fun ear() = sketch("ear_detail", "Kulak", 50f, 80f, listOf("kulak", "portre", "ear")) {
        path(closed = true) {
            moveTo(14f, 10f)
            cubicTo(28f, 2f, 44f, 8f, 44f, 26f)
            cubicTo(44f, 40f, 36f, 46f, 32f, 56f)
            cubicTo(30f, 64f, 30f, 74f, 22f, 76f)
            cubicTo(14f, 78f, 9f, 72f, 10f, 64f)
            cubicTo(10f, 44f, 4f, 20f, 14f, 10f)
        }
        // Kıvrımlar
        path { moveTo(16f, 16f); cubicTo(28f, 10f, 38f, 16f, 37f, 28f); cubicTo(36f, 38f, 28f, 44f, 26f, 54f) }.detail()
        path { moveTo(20f, 26f); cubicTo(26f, 24f, 30f, 28f, 28f, 34f); cubicTo(26f, 40f, 20f, 40f, 18f, 46f) }.detail()
        path { moveTo(16f, 50f); cubicTo(20f, 46f, 26f, 48f, 25f, 55f); cubicTo(24f, 60f, 18f, 60f, 17f, 64f) }.detail()
    }

    private fun hairShort() = sketch("hair_styles", "Saç çizgileri", 90f, 100f, listOf("saç", "portre", "kafa", "hair")) {
        // Kafa hattı üstünde kısa saç kütlesi ve saç yönü çizgileri
        guideEllipse(45f, 52f, 30f, 40f)
        path(closed = true) {
            moveTo(14f, 54f)
            cubicTo(8f, 30f, 22f, 8f, 46f, 8f)
            cubicTo(70f, 8f, 84f, 28f, 77f, 54f)
            cubicTo(75f, 44f, 72f, 36f, 64f, 30f)
            cubicTo(56f, 34f, 42f, 34f, 30f, 28f)
            cubicTo(22f, 34f, 17f, 44f, 14f, 54f)
        }
        for (k in 0..6) {
            val x = 24f + k * 7f
            path { moveTo(x, 12f + (if (k in 2..4) 0f else 3f)); cubicTo(x + 3f, 18f, x + 1f, 24f, x - 2f, 30f) }.width(0.45f)
        }
        // Kulak ve yüz hattı (kesik)
        path { moveTo(15f, 56f); cubicTo(17f, 76f, 30f, 92f, 45f, 93f); cubicTo(60f, 92f, 73f, 76f, 76f, 56f) }.width(0.5f).dashed()
    }
}
