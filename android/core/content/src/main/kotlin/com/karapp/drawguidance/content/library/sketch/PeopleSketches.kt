package com.karapp.drawguidance.content.library.sketch

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder

/** "İnsan": manken (eklem ve uzuv) kurgusuyla duruşlar — ayakta (8 kafa oranı), yürüme, koşma, oturma, çocuk ve el. */
internal object PeopleSketches {

    val shapes: List<LibShape> by lazy { listOf(standing(), walking(), running(), sitting(), child(), hand()) }

    /** Bir kol ya da bacak: üç eklem (omuz/dirsek/bilek ya da kalça/diz/bilek) ve uç (el ya da ayak). */
    private class Limb(val a: P, val b: P, val c: P, val end: P, val r: Float)

    /** Manken: kafa, boyun, göğüs kafesi, leğen, uzuvlar; [far] uzuvlar gövdenin arkasında (önce) çizilir. */
    private fun VectorBuilder.mannequin(
        head: P, headRx: Float, headRy: Float, headRot: Float,
        chest: P, chestRx: Float, chestRy: Float, chestRot: Float,
        pelvis: P, pelvisRx: Float, pelvisRy: Float, pelvisRot: Float,
        neckTop: P, neckBottom: P,
        far: List<Limb>, near: List<Limb>,
    ) {
        fun limb(l: Limb, arm: Boolean) {
            val r = l.r
            capsule(l.a.x, l.a.y, l.b.x, l.b.y, r, r * 0.8f)
            capsule(l.b.x, l.b.y, l.c.x, l.c.y, r * 0.8f, r * 0.58f)
            if (arm) {
                // El: bilekten uca doğru avuç
                val dx = l.end.x - l.c.x; val dy = l.end.y - l.c.y
                val ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                val len = kotlin.math.hypot(dx, dy)
                ellipse((l.c.x + l.end.x) / 2f, (l.c.y + l.end.y) / 2f, len / 2f + r * 0.2f, r * 0.75f, ang)
            } else {
                capsule(l.c.x, l.c.y, l.end.x, l.end.y, r * 0.5f, r * 0.36f)
            }
            circle(l.b.x, l.b.y, r * 0.55f).width(0.4f).dashed()
        }
        far.forEachIndexed { i, l -> limb(l, arm = i == 0) }
        capsule(neckTop.x, neckTop.y, neckBottom.x, neckBottom.y, headRx * 0.42f, headRx * 0.48f)
        ellipse(pelvis.x, pelvis.y, pelvisRx, pelvisRy, pelvisRot)
        ellipse(chest.x, chest.y, chestRx, chestRy, chestRot)
        ellipse(head.x, head.y, headRx, headRy, headRot)
        near.forEachIndexed { i, l -> limb(l, arm = i == 0) }
        // Omurga yönü (kılavuz)
        guide(neckTop.x, neckTop.y, pelvis.x, pelvis.y)
    }

    private fun standing() = sketch("figure_standing", "Vücut oranları (ayakta)", 50f, 100f, listOf("insan", "vücut", "oran", "manken", "figür", "ayakta")) {
        // 8 kafa boyu: her kafa hizası kılavuz çizgisi
        for (k in 0..8) guide(2f, 2f + 12f * k, 48f, 2f + 12f * k)
        for (k in 1..8) text(0.5f, 2f + 12f * k - 5f, "$k", 0.022f)
        fun arm(side: Float) = Limb(P(25f + 9.5f * side, 19.5f), P(25f + 12f * side, 36f), P(25f + 13.5f * side, 50f), P(25f + 14f * side, 57f), 2.3f)
        fun leg(side: Float) = Limb(P(25f + 4.8f * side, 49f), P(25f + 5f * side, 72f), P(25f + 5.5f * side, 93f), P(25f + 9f * side, 97f), 3.3f)
        mannequin(
            head = P(25f, 8f), headRx = 4.6f, headRy = 6f, headRot = 0f,
            chest = P(25f, 27f), chestRx = 8.2f, chestRy = 9.8f, chestRot = 0f,
            pelvis = P(25f, 46f), pelvisRx = 7.4f, pelvisRy = 5.4f, pelvisRot = 0f,
            neckTop = P(25f, 13.5f), neckBottom = P(25f, 18f),
            far = listOf(arm(-1f), leg(-1f)), near = listOf(arm(1f), leg(1f)),
        )
    }

    private fun walking() = sketch("figure_walking", "Yürüyen insan", 60f, 100f, listOf("insan", "yürüme", "manken", "figür", "hareket")) {
        guide(2f, 97f, 58f, 97f)
        mannequin(
            head = P(33f, 8f), headRx = 4.6f, headRy = 6f, headRot = 8f,
            chest = P(30.5f, 27f), chestRx = 6.6f, chestRy = 10f, chestRot = 4f,
            pelvis = P(29f, 46f), pelvisRx = 6.4f, pelvisRy = 5.6f, pelvisRot = -8f,
            neckTop = P(32f, 13.5f), neckBottom = P(31f, 18f),
            far = listOf(
                Limb(P(31.5f, 20f), P(37f, 34f), P(43f, 44f), P(47f, 49f), 2.2f),
                Limb(P(30f, 49f), P(24f, 71f), P(16f, 90f), P(20.5f, 96.5f), 3.2f),
            ),
            near = listOf(
                Limb(P(30f, 20.5f), P(25f, 35f), P(21f, 48f), P(19.5f, 55f), 2.3f),
                Limb(P(28.5f, 49f), P(36f, 71f), P(38f, 92f), P(45f, 96.5f), 3.3f),
            ),
        )
    }

    private fun running() = sketch("figure_running", "Koşan insan", 70f, 100f, listOf("insan", "koşu", "manken", "figür", "hareket", "spor")) {
        guide(2f, 90f, 68f, 90f)
        mannequin(
            head = P(44f, 10f), headRx = 4.6f, headRy = 6f, headRot = 18f,
            chest = P(38.5f, 28f), chestRx = 6.8f, chestRy = 10f, chestRot = 20f,
            pelvis = P(33f, 46f), pelvisRx = 6.4f, pelvisRy = 5.6f, pelvisRot = 10f,
            neckTop = P(42.5f, 15.5f), neckBottom = P(40.5f, 19.5f),
            far = listOf(
                Limb(P(41f, 21f), P(49f, 31f), P(56f, 23f), P(60f, 19f), 2.2f),
                Limb(P(34f, 49f), P(24f, 64f), P(12f, 72f), P(9f, 78f), 3.2f),
            ),
            near = listOf(
                Limb(P(39f, 22f), P(29f, 33f), P(22f, 26f), P(18f, 21f), 2.3f),
                Limb(P(32f, 49f), P(47f, 58f), P(45f, 78f), P(52f, 81f), 3.3f),
            ),
        )
    }

    private fun sitting() = sketch("figure_sitting", "Oturan insan", 70f, 90f, listOf("insan", "oturma", "sandalye", "manken", "figür")) {
        // Sandalye
        rect(14f, 52f, 34f, 4f)
        line(16f, 56f, 16f, 86f)
        line(46f, 56f, 46f, 86f)
        line(14f, 52f, 12f, 20f)
        line(12f, 20f, 16f, 20f)
        line(16f, 20f, 18f, 52f)
        guide(2f, 86f, 68f, 86f)
        mannequin(
            head = P(30f, 10f), headRx = 4.6f, headRy = 6f, headRot = 4f,
            chest = P(29.5f, 28f), chestRx = 6.4f, chestRy = 10f, chestRot = 0f,
            pelvis = P(30f, 46f), pelvisRx = 6.4f, pelvisRy = 5.4f, pelvisRot = 0f,
            neckTop = P(30f, 15.5f), neckBottom = P(30f, 19.5f),
            far = listOf(
                Limb(P(30.5f, 21f), P(35f, 36f), P(46f, 41f), P(51f, 43f), 2.2f),
                Limb(P(32f, 49f), P(54f, 51f), P(56f, 80f), P(62f, 84f), 3.2f),
            ),
            near = listOf(
                Limb(P(29f, 21f), P(32f, 37f), P(43f, 44f), P(48f, 47f), 2.3f),
                Limb(P(30f, 50f), P(52f, 53f), P(52f, 81f), P(59f, 85f), 3.3f),
            ),
        )
    }

    private fun child() = sketch("figure_child", "Çocuk oranları", 50f, 80f, listOf("çocuk", "insan", "oran", "manken", "figür")) {
        // Çocukta kafa büyük: yaklaşık 5 kafa boyu
        for (k in 0..5) guide(2f, 2f + 15f * k, 48f, 2f + 15f * k)
        fun arm(side: Float) = Limb(P(25f + 8f * side, 23f), P(25f + 10f * side, 36f), P(25f + 11f * side, 46f), P(25f + 11.5f * side, 51f), 2.4f)
        fun leg(side: Float) = Limb(P(25f + 4.4f * side, 47f), P(25f + 4.6f * side, 62f), P(25f + 5f * side, 74f), P(25f + 8f * side, 77f), 3.2f)
        mannequin(
            head = P(25f, 10f), headRx = 6.6f, headRy = 7.8f, headRot = 0f,
            chest = P(25f, 29f), chestRx = 7.2f, chestRy = 8f, chestRot = 0f,
            pelvis = P(25f, 44f), pelvisRx = 6.8f, pelvisRy = 5f, pelvisRot = 0f,
            neckTop = P(25f, 17.5f), neckBottom = P(25f, 21.5f),
            far = listOf(arm(-1f), leg(-1f)), near = listOf(arm(1f), leg(1f)),
        )
    }

    private fun hand() = sketch("hand_open", "El (açık)", 70f, 90f, listOf("el", "parmak", "avuç", "hand")) {
        // Avuç, parmaklar (boğum çizgileriyle) ve başparmak
        blob(21f, 50f, 24f, 41f, 31f, 37.5f, 47f, 38f, 53f, 43f, 55f, 55f, 51f, 71f, 40f, 81f, 27f, 78f, 21f, 66f)
        capsule(30.5f, 40f, 27.5f, 12f, 3.6f, 3f)
        capsule(38.5f, 39f, 38.5f, 7f, 3.7f, 3.1f)
        capsule(46f, 40f, 48.5f, 13f, 3.5f, 2.9f)
        capsule(52f, 46f, 59f, 26f, 3f, 2.5f)
        capsule(23f, 58f, 13f, 48f, 4.3f, 3.6f)
        capsule(13f, 48f, 7f, 36f, 3.6f, 3f)
        // Boğumlar
        for ((x, y) in listOf(28.8f to 28f, 38.5f to 25f, 47.3f to 28f, 55.6f to 35f)) line(x - 2.6f, y, x + 2.6f, y).width(0.4f)
        for ((x, y) in listOf(28f to 19f, 38.5f to 15.5f, 48f to 20f, 57.6f to 29.5f)) line(x - 2.3f, y, x + 2.3f, y).width(0.4f)
        // Avuç çizgileri
        path { moveTo(25f, 52f); cubicTo(32f, 58f, 34f, 66f, 33f, 76f) }.width(0.4f)
        path { moveTo(24f, 48f); cubicTo(34f, 48f, 44f, 50f, 53f, 50f) }.width(0.4f)
    }
}
