package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.vector.VectorBuilder

private const val FRUIT = "Meyve"
private const val VEG = "Sebze"

/** Sap ve yaprak (meyvelerin tepesi). */
private fun VectorBuilder.stemLeaf(x: Float = 0.5f, y: Float = 0.18f) {
    s(x, y, x + 0.02f, y - 0.1f, x + 0.06f, y - 0.16f)
    sc(x + 0.04f, y - 0.1f, x + 0.16f, y - 0.2f, x + 0.28f, y - 0.14f, x + 0.16f, y - 0.06f)
}

internal fun TemplateScope.fruitTemplates() {
    t("meyve.elma", "Elma", FRUIT, "Meyve", "Üstü çukur, yuvarlak elma, sap ve yaprak") {
        sc(0.5f, 0.2f, 0.72f, 0.1f, 0.95f, 0.3f, 0.92f, 0.7f, 0.7f, 0.98f, 0.5f, 0.92f, 0.3f, 0.98f, 0.08f, 0.7f, 0.05f, 0.3f, 0.28f, 0.1f)
        stemLeaf(0.5f, 0.2f)
    }
    t("meyve.armut", "Armut", FRUIT, "Meyve", "Üstü dar, altı geniş armut ve sapı") {
        sc(0.5f, 0.08f, 0.64f, 0.18f, 0.66f, 0.4f, 0.88f, 0.65f, 0.8f, 0.94f, 0.5f, 1f, 0.2f, 0.94f, 0.12f, 0.65f, 0.34f, 0.4f, 0.36f, 0.18f)
        s(0.5f, 0.08f, 0.52f, 0f)
    }
    t("meyve.muz", "Muz", FRUIT, "Meyve", "Hilal biçimli muz ve sap ucu") {
        sc(0.12f, 0.12f, 0.3f, 0.62f, 0.62f, 0.82f, 0.95f, 0.72f, 0.72f, 0.95f, 0.3f, 0.85f, 0.05f, 0.45f, 0.05f, 0.12f)
        l(0.05f, 0.12f, 0.12f, 0f)
    }
    t("meyve.kiraz", "Kiraz", FRUIT, "Meyve", "İki yuvarlak kiraz ve birleşen sapları") {
        circle(0.28f, 0.78f, 0.2f); circle(0.72f, 0.8f, 0.19f); s(0.28f, 0.58f, 0.4f, 0.25f, 0.6f, 0.02f); s(0.72f, 0.61f, 0.68f, 0.3f, 0.6f, 0.02f)
    }
    t("meyve.uzum", "Üzüm salkımı", FRUIT, "Meyve", "Aşağı daralan üzüm taneleri ve sap") {
        val rows = listOf(4, 3, 2, 1); var y = 0.25f
        rows.forEach { n -> val x0 = 0.5f - (n - 1) * 0.11f; repeat(n) { circle(x0 + it * 0.22f, y, 0.11f) }; y += 0.2f }
        s(0.5f, 0.14f, 0.52f, 0.04f, 0.6f, 0f)
    }
    t("meyve.cilek", "Çilek", FRUIT, "Meyve", "Kalp biçimli çilek, çekirdekler ve yaprak tacı") {
        sc(0.5f, 0.25f, 0.85f, 0.2f, 0.9f, 0.45f, 0.7f, 0.8f, 0.5f, 1f, 0.3f, 0.8f, 0.1f, 0.45f, 0.15f, 0.2f)
        l(0.2f, 0.22f, 0.3f, 0.08f, 0.42f, 0.2f, 0.5f, 0.02f, 0.58f, 0.2f, 0.7f, 0.08f, 0.8f, 0.22f)
        l(0.35f, 0.45f, 0.36f, 0.47f); l(0.6f, 0.45f, 0.61f, 0.47f); l(0.48f, 0.62f, 0.49f, 0.64f); l(0.4f, 0.78f, 0.41f, 0.8f); l(0.6f, 0.74f, 0.61f, 0.76f)
    }
    t("meyve.portakal", "Portakal", FRUIT, "Meyve", "Yuvarlak portakal ve tepede küçük yaprak") {
        circle(0.5f, 0.55f, 0.44f); sc(0.52f, 0.12f, 0.66f, 0f, 0.8f, 0.04f, 0.66f, 0.14f)
    }
    t("meyve.portakal_dilim", "Portakal dilimi", FRUIT, "Meyve", "Kesilmiş portakal: kabuk, iç halka ve dilim çizgileri") {
        circle(0.5f, 0.5f, 0.48f); circle(0.5f, 0.5f, 0.4f)
        for (i in 0 until 8) { val a = Math.toRadians(45.0 * i); l(0.5f, 0.5f, (0.5 + 0.4 * kotlin.math.cos(a)).toFloat(), (0.5 + 0.4 * kotlin.math.sin(a)).toFloat()) }
    }
    t("meyve.limon", "Limon", FRUIT, "Meyve", "İki ucu sivri oval limon") {
        sc(0f, 0.5f, 0.1f, 0.3f, 0.5f, 0.12f, 0.9f, 0.3f, 1f, 0.5f, 0.9f, 0.7f, 0.5f, 0.88f, 0.1f, 0.7f)
    }
    t("meyve.karpuz", "Karpuz dilimi", FRUIT, "Meyve", "Yarım daire karpuz dilimi, kabuk çizgisi ve çekirdekler") {
        path(closed = true) { moveTo(0f, 0.2f); lineTo(1f, 0.2f); cubicTo(1f, 0.95f, 0f, 0.95f, 0f, 0.2f) }
        path { moveTo(0.08f, 0.2f); cubicTo(0.08f, 0.8f, 0.92f, 0.8f, 0.92f, 0.2f) }
        l(0.35f, 0.35f, 0.36f, 0.4f); l(0.5f, 0.45f, 0.5f, 0.5f); l(0.65f, 0.35f, 0.64f, 0.4f)
    }
    t("meyve.ananas", "Ananas", FRUIT, "Meyve", "Oval gövde, çapraz desen ve sivri yapraklar") {
        ellipse(0.5f, 0.66f, 0.3f, 0.34f); l(0.3f, 0.45f, 0.72f, 0.9f); l(0.28f, 0.7f, 0.5f, 0.98f); l(0.7f, 0.45f, 0.28f, 0.9f); l(0.72f, 0.7f, 0.5f, 0.98f)
        l(0.32f, 0.36f, 0.4f, 0.1f, 0.46f, 0.3f, 0.5f, 0f, 0.54f, 0.3f, 0.6f, 0.1f, 0.68f, 0.36f)
    }
    t("meyve.seftali", "Şeftali", FRUIT, "Meyve", "Ortasında oluk bulunan yuvarlak şeftali ve yaprak") {
        circle(0.5f, 0.55f, 0.42f); s(0.5f, 0.15f, 0.42f, 0.5f, 0.5f, 0.95f); sc(0.52f, 0.14f, 0.7f, 0f, 0.86f, 0.06f, 0.68f, 0.16f)
    }
    t("meyve.kivi", "Kivi dilimi", FRUIT, "Meyve", "Kivi kesiti: kabuk, orta göbek ve çekirdek halkası") {
        ellipse(0.5f, 0.5f, 0.48f, 0.42f); ellipse(0.5f, 0.5f, 0.16f, 0.12f)
        for (i in 0 until 10) { val a = Math.toRadians(36.0 * i); val x = (0.5 + 0.28 * kotlin.math.cos(a)).toFloat(); val y = (0.5 + 0.24 * kotlin.math.sin(a)).toFloat(); l(x, y, x + 0.01f, y + 0.02f) }
    }
    t("meyve.nar", "Nar", FRUIT, "Meyve", "Yuvarlak nar ve tepesinde taç") {
        sc(0.38f, 0.16f, 0.1f, 0.35f, 0.08f, 0.7f, 0.5f, 1f, 0.92f, 0.7f, 0.9f, 0.35f, 0.62f, 0.16f)
        l(0.38f, 0.16f, 0.36f, 0f, 0.45f, 0.1f, 0.5f, 0f, 0.55f, 0.1f, 0.64f, 0f, 0.62f, 0.16f)
    }
    t("meyve.avokado", "Avokado (kesik)", FRUIT, "Meyve", "Armut biçimli yarım avokado ve yuvarlak çekirdek") {
        sc(0.5f, 0f, 0.7f, 0.12f, 0.78f, 0.5f, 0.9f, 0.75f, 0.5f, 1f, 0.1f, 0.75f, 0.22f, 0.5f, 0.3f, 0.12f); circle(0.5f, 0.68f, 0.17f)
    }
    t("meyve.bogurtlen", "Ahududu / böğürtlen", FRUIT, "Meyve", "Küçük tanelerden oluşan böğürtlen") {
        listOf(0.3f to 0.3f, 0.5f to 0.25f, 0.7f to 0.3f, 0.4f to 0.5f, 0.6f to 0.5f, 0.5f to 0.72f).forEach { (x, y) -> circle(x, y, 0.13f) }
    }
}

internal fun TemplateScope.vegetableTemplates() {
    t("sebze.havuc", "Havuç", VEG, "Sebze", "Uzun sivri havuç, halka çizgileri ve yaprakları") {
        lc(0.35f, 0.22f, 0.65f, 0.22f, 0.5f, 1f); l(0.42f, 0.42f, 0.52f, 0.42f); l(0.48f, 0.6f, 0.56f, 0.6f)
        s(0.5f, 0.22f, 0.4f, 0.05f, 0.3f, 0f); s(0.5f, 0.22f, 0.5f, 0.02f); s(0.5f, 0.22f, 0.6f, 0.05f, 0.7f, 0f)
    }
    t("sebze.domates", "Domates", VEG, "Sebze", "Basık yuvarlak domates ve yıldız biçimli sapı") {
        ellipse(0.5f, 0.58f, 0.48f, 0.38f); l(0.3f, 0.24f, 0.44f, 0.22f, 0.5f, 0.1f, 0.56f, 0.22f, 0.7f, 0.24f)
    }
    t("sebze.patlican", "Patlıcan", VEG, "Sebze", "Kavisli uzun patlıcan ve şapkası") {
        sc(0.28f, 0.2f, 0.45f, 0.15f, 0.62f, 0.4f, 0.95f, 0.75f, 0.8f, 1f, 0.45f, 0.85f, 0.15f, 0.45f)
        l(0.12f, 0.35f, 0.25f, 0.1f, 0.4f, 0.25f); s(0.25f, 0.1f, 0.15f, 0f)
    }
    t("sebze.sivri_biber", "Sivri biber", VEG, "Sebze", "İnce uzun, ucu kıvrık biber ve sapı") {
        sc(0.2f, 0.1f, 0.4f, 0.08f, 0.55f, 0.45f, 0.95f, 0.95f, 0.45f, 0.62f, 0.12f, 0.2f); s(0.18f, 0.12f, 0.08f, 0f)
    }
    t("sebze.dolmalik", "Dolmalık biber", VEG, "Sebze", "Dört loblu kısa biber ve sapı") {
        sc(0.5f, 0.2f, 0.8f, 0.15f, 0.95f, 0.45f, 0.85f, 0.9f, 0.62f, 0.85f, 0.5f, 0.98f, 0.38f, 0.85f, 0.15f, 0.9f, 0.05f, 0.45f, 0.2f, 0.15f)
        s(0.5f, 0.2f, 0.5f, 0.06f, 0.58f, 0f)
    }
    t("sebze.sogan", "Soğan", VEG, "Sebze", "Damla biçimli soğan, katman çizgileri ve kökler") {
        sc(0.5f, 0f, 0.62f, 0.3f, 0.9f, 0.55f, 0.75f, 0.9f, 0.5f, 0.92f, 0.25f, 0.9f, 0.1f, 0.55f, 0.38f, 0.3f)
        s(0.5f, 0.05f, 0.4f, 0.5f, 0.45f, 0.9f); s(0.5f, 0.05f, 0.6f, 0.5f, 0.55f, 0.9f); l(0.42f, 0.92f, 0.38f, 1f); l(0.58f, 0.92f, 0.62f, 1f)
    }
    t("sebze.sarimsak", "Sarımsak", VEG, "Sebze", "Dişli sarımsak başı ve sivri ucu") {
        sc(0.5f, 0.05f, 0.58f, 0.3f, 0.9f, 0.6f, 0.7f, 0.95f, 0.5f, 0.92f, 0.3f, 0.95f, 0.1f, 0.6f, 0.42f, 0.3f)
        s(0.5f, 0.3f, 0.35f, 0.6f, 0.4f, 0.93f); s(0.5f, 0.3f, 0.65f, 0.6f, 0.6f, 0.93f)
    }
    t("sebze.patates", "Patates", VEG, "Sebze", "Yamru yumru oval patates ve gözleri") {
        sc(0.1f, 0.4f, 0.3f, 0.15f, 0.65f, 0.1f, 0.92f, 0.3f, 0.95f, 0.6f, 0.7f, 0.88f, 0.35f, 0.9f, 0.08f, 0.7f)
        l(0.35f, 0.4f, 0.37f, 0.42f); l(0.62f, 0.35f, 0.64f, 0.37f); l(0.55f, 0.65f, 0.57f, 0.67f)
    }
    t("sebze.misir", "Mısır koçanı", VEG, "Sebze", "Taneli koçan ve iki yanında yapraklar") {
        ellipse(0.5f, 0.42f, 0.18f, 0.4f); l(0.36f, 0.3f, 0.64f, 0.3f); l(0.33f, 0.45f, 0.67f, 0.45f); l(0.36f, 0.6f, 0.64f, 0.6f); l(0.5f, 0.05f, 0.5f, 0.8f)
        s(0.45f, 1f, 0.2f, 0.7f, 0.15f, 0.3f); s(0.55f, 1f, 0.8f, 0.7f, 0.85f, 0.3f)
    }
    t("sebze.bezelye", "Bezelye kabuğu", VEG, "Sebze", "Açık kabuk ve içinde sıralı taneler") {
        sc(0f, 0.5f, 0.3f, 0.25f, 0.7f, 0.25f, 1f, 0.45f, 0.7f, 0.75f, 0.3f, 0.75f)
        circle(0.25f, 0.5f, 0.1f); circle(0.5f, 0.5f, 0.11f); circle(0.75f, 0.5f, 0.1f)
    }
    t("sebze.brokoli", "Brokoli", VEG, "Sebze", "Bulut biçimli çiçek başı ve kalın sap") {
        path(closed = true) {
            moveTo(0.15f, 0.55f); quadTo(0f, 0.3f, 0.25f, 0.2f); quadTo(0.3f, 0f, 0.55f, 0.08f); quadTo(0.85f, 0f, 0.85f, 0.25f); quadTo(1.02f, 0.45f, 0.82f, 0.58f)
            close()
        }
        l(0.38f, 0.57f, 0.4f, 1f, 0.6f, 1f, 0.62f, 0.57f)
    }
    t("sebze.mantar", "Mantar", VEG, "Sebze", "Yarım kubbe şapka ve kalın sap") {
        path(closed = true) { moveTo(0f, 0.5f); cubicTo(0f, -0.1f, 1f, -0.1f, 1f, 0.5f); close() }
        l(0.35f, 0.5f, 0.32f, 0.98f, 0.68f, 0.98f, 0.65f, 0.5f)
    }
    t("sebze.salatalik", "Salatalık", VEG, "Sebze", "Uzun oval salatalık ve kabuk noktaları") {
        sc(0.05f, 0.35f, 0.3f, 0.12f, 0.8f, 0.3f, 0.97f, 0.62f, 0.7f, 0.88f, 0.2f, 0.7f)
        l(0.3f, 0.4f, 0.31f, 0.42f); l(0.5f, 0.45f, 0.51f, 0.47f); l(0.7f, 0.55f, 0.71f, 0.57f)
    }
    t("sebze.lahana", "Lahana", VEG, "Sebze", "Yuvarlak baş ve iç içe yaprak çizgileri") {
        circle(0.5f, 0.5f, 0.46f); arc(0.5f, 0.6f, 0.3f, 0.34f, 200f, 140f); arc(0.5f, 0.62f, 0.14f, 0.2f, 190f, 160f); s(0.5f, 0.04f, 0.45f, 0.5f, 0.5f, 0.96f)
    }
    t("sebze.turp", "Turp", VEG, "Sebze", "Yuvarlak kök, ince kuyruk ve yapraklar") {
        sc(0.5f, 0.35f, 0.8f, 0.45f, 0.78f, 0.75f, 0.5f, 0.88f, 0.22f, 0.75f, 0.2f, 0.45f); l(0.5f, 0.88f, 0.52f, 1f)
        sc(0.48f, 0.35f, 0.3f, 0.15f, 0.36f, 0f, 0.48f, 0.15f); sc(0.52f, 0.35f, 0.64f, 0.1f, 0.78f, 0.05f, 0.68f, 0.25f)
    }
    t("sebze.balkabagi", "Bal kabağı", VEG, "Sebze", "Dilimli basık kabak ve sapı") {
        ellipse(0.5f, 0.58f, 0.48f, 0.38f); ellipse(0.5f, 0.58f, 0.18f, 0.38f); s(0.5f, 0.2f, 0.52f, 0.05f, 0.6f, 0f)
    }
    t("sebze.marul", "Marul", VEG, "Sebze", "Dalgalı kenarlı yaprak demeti") {
        sc(0.5f, 1f, 0.15f, 0.8f, 0.05f, 0.45f, 0.2f, 0.3f, 0.25f, 0.08f, 0.45f, 0.12f, 0.55f, 0f, 0.7f, 0.12f, 0.85f, 0.25f, 0.95f, 0.5f, 0.85f, 0.8f)
        s(0.5f, 1f, 0.5f, 0.5f, 0.52f, 0.15f)
    }
}
