package com.karapp.drawguidance.content.shapes

import kotlin.math.cos
import kotlin.math.sin

private const val LIMB = "Hayvan uzvu"
private const val ANIMAL = "Hayvan"
private const val PLANT = "Bitki"
private const val SKY = "Gökyüzü ve doğa"

private fun rad(deg: Double) = Math.toRadians(deg)

internal fun TemplateScope.animalTemplates() {
    // --- Hayvan uzuvları ---
    t("uzuv.kulak.kedi", "Kedi kulağı", LIMB, "Kulak", "Sivri üçgen kulak ve iç çizgisi") {
        l(0f, 1f, 0.3f, 0f, 1f, 0.75f); l(0.2f, 0.85f, 0.35f, 0.25f, 0.8f, 0.7f)
    }
    t("uzuv.kulak.kopek", "Köpek kulağı (sarkık)", LIMB, "Kulak", "Aşağı sarkan uzun, yuvarlak uçlu kulak") {
        sc(0.3f, 0f, 0.7f, 0.05f, 0.85f, 0.5f, 0.75f, 0.95f, 0.5f, 1f, 0.3f, 0.7f, 0.15f, 0.2f)
    }
    t("uzuv.kulak.tavsan", "Tavşan kulakları", LIMB, "Kulak", "İki uzun oval kulak") {
        ellipse(0.3f, 0.5f, 0.13f, 0.48f, -8f); ellipse(0.7f, 0.5f, 0.13f, 0.48f, 8f)
    }
    t("uzuv.kulak.ayi", "Ayı kulağı", LIMB, "Kulak", "Yarım daire kulak ve iç yayı") {
        arc(0.5f, 0.75f, 0.45f, 0.7f, 180f, 180f); arc(0.5f, 0.78f, 0.25f, 0.4f, 180f, 180f)
    }
    t("uzuv.pati_izi", "Pati izi", LIMB, "Ayak", "Büyük taban yastığı ve dört parmak yastığı") {
        sc(0.5f, 0.5f, 0.75f, 0.62f, 0.78f, 0.9f, 0.5f, 0.98f, 0.22f, 0.9f, 0.25f, 0.62f)
        ellipse(0.15f, 0.42f, 0.09f, 0.12f); ellipse(0.37f, 0.18f, 0.09f, 0.13f); ellipse(0.63f, 0.18f, 0.09f, 0.13f); ellipse(0.85f, 0.42f, 0.09f, 0.12f)
    }
    t("uzuv.pence", "Pençe", LIMB, "Ayak", "Kıvrık, sivri üç tırnak") {
        for (i in 0..2) { val x = 0.2f + i * 0.3f; path(closed = true) { moveTo(x - 0.1f, 0f); quadTo(x + 0.14f, 0.5f, x, 1f); quadTo(x + 0.02f, 0.5f, x - 0.1f, 0f) } }
    }
    t("uzuv.toynak", "Toynak", LIMB, "Ayak", "Bacak ucu ve ortası yarık toynak") {
        l(0.3f, 0f, 0.28f, 0.55f); l(0.7f, 0f, 0.72f, 0.55f); lc(0.25f, 0.55f, 0.75f, 0.55f, 0.85f, 1f, 0.15f, 1f); l(0.5f, 0.7f, 0.5f, 1f)
    }
    t("uzuv.kus_ayagi", "Kuş ayağı", LIMB, "Ayak", "İnce bacak ve üç öne bir arkaya parmak") {
        l(0.5f, 0f, 0.5f, 0.65f); l(0.1f, 1f, 0.5f, 0.65f); l(0.5f, 0.65f, 0.5f, 1f); l(0.5f, 0.65f, 0.9f, 1f); l(0.5f, 0.65f, 0.3f, 0.5f)
    }
    t("uzuv.kanat.kus", "Kuş kanadı", LIMB, "Kanat", "Tüy uçlu, açık kuş kanadı") {
        path(closed = true) {
            moveTo(0f, 0.3f); quadTo(0.5f, -0.05f, 1f, 0.05f); lineTo(0.9f, 0.3f); lineTo(1f, 0.4f); lineTo(0.8f, 0.55f); lineTo(0.85f, 0.7f)
            lineTo(0.6f, 0.75f); lineTo(0.6f, 0.9f); lineTo(0.35f, 0.8f); quadTo(0.1f, 0.7f, 0f, 0.3f)
        }
    }
    t("uzuv.kanat.yarasa", "Yarasa kanadı", LIMB, "Kanat", "Kemikli, alt kenarı fistolu yarasa kanadı") {
        path(closed = true) {
            moveTo(0f, 0.2f); quadTo(0.5f, -0.05f, 1f, 0.15f)
            quadTo(0.88f, 0.4f, 0.95f, 0.65f); quadTo(0.75f, 0.5f, 0.62f, 0.75f); quadTo(0.48f, 0.55f, 0.3f, 0.85f); quadTo(0.2f, 0.5f, 0f, 0.2f)
        }
    }
    t("uzuv.kanat.kelebek", "Kelebek kanadı", LIMB, "Kanat", "Üstte büyük, altta küçük kelebek kanadı") {
        sc(0f, 0.5f, 0.1f, 0.05f, 0.7f, 0f, 0.95f, 0.2f, 0.6f, 0.5f); sc(0f, 0.5f, 0.55f, 0.55f, 0.75f, 0.85f, 0.4f, 1f, 0.1f, 0.85f)
    }
    t("uzuv.kuyruk.balik", "Balık kuyruğu", LIMB, "Kuyruk", "Çatal (V) biçimli balık kuyruğu") {
        path(closed = true) { moveTo(0f, 0.5f); quadTo(0.5f, 0.3f, 1f, 0f); quadTo(0.75f, 0.5f, 1f, 1f); quadTo(0.5f, 0.7f, 0f, 0.5f) }
    }
    t("uzuv.kuyruk.kedi", "Kedi kuyruğu", LIMB, "Kuyruk", "İnce, ucu kıvrık uzun kuyruk") {
        s(0f, 1f, 0.3f, 0.9f, 0.6f, 0.7f, 0.75f, 0.35f, 0.7f, 0.1f, 0.88f, 0f, 1f, 0.12f)
    }
    t("uzuv.kuyruk.tilki", "Tilki kuyruğu", LIMB, "Kuyruk", "Gür, uç kısmı ayrılmış kabarık kuyruk") {
        sc(0f, 0.9f, 0.2f, 0.45f, 0.6f, 0.1f, 1f, 0f, 0.85f, 0.4f, 0.5f, 0.8f, 0.15f, 1f); s(0.72f, 0.06f, 0.68f, 0.3f, 0.9f, 0.28f)
    }
    t("uzuv.boynuz.koc", "Koç boynuzu", LIMB, "Boynuz", "Sarmal kıvrılan koç boynuzu") {
        path {
            moveTo(0.1f, 0.2f)
            for (i in 1..36) { val a = rad(-120.0 + i * 12.0); val r = 0.46 * (1.0 - i / 48.0); lineTo((0.52 + r * cos(a)).toFloat(), (0.52 + r * sin(a)).toFloat()) }
        }
    }
    t("uzuv.boynuz.geyik", "Geyik boynuzu", LIMB, "Boynuz", "Dallanan geyik boynuzu") {
        s(0.5f, 1f, 0.45f, 0.6f, 0.2f, 0.3f, 0.1f, 0f); l(0.4f, 0.5f, 0.6f, 0.3f, 0.65f, 0.05f); l(0.27f, 0.36f, 0.35f, 0.1f); l(0.6f, 0.3f, 0.85f, 0.2f)
    }
    t("uzuv.boynuz.sivri", "Sivri boynuzlar", LIMB, "Boynuz", "İki yana kavisli, sivri boynuzlar") {
        sc(0.35f, 1f, 0.1f, 0.55f, 0f, 0f, 0.25f, 0.5f, 0.45f, 0.9f); sc(0.65f, 1f, 0.9f, 0.55f, 1f, 0f, 0.75f, 0.5f, 0.55f, 0.9f)
    }
    t("uzuv.hortum", "Fil hortumu", LIMB, "Baş", "Aşağı sarkıp ucu kıvrılan hortum") {
        s(0.2f, 0f, 0.25f, 0.5f, 0.4f, 0.85f, 0.7f, 0.95f, 0.85f, 0.8f); s(0.45f, 0f, 0.45f, 0.45f, 0.55f, 0.72f, 0.72f, 0.78f, 0.85f, 0.8f)
    }
    t("uzuv.gaga.kus", "Kuş gagası", LIMB, "Baş", "Sivri, iki parçalı gaga") { lc(0f, 0.2f, 1f, 0.5f, 0f, 0.8f); l(0f, 0.5f, 0.75f, 0.5f) }
    t("uzuv.gaga.ordek", "Ördek gagası", LIMB, "Baş", "Yassı, yuvarlak uçlu ördek gagası") {
        sc(0f, 0.3f, 0.6f, 0.15f, 1f, 0.35f, 0.95f, 0.6f, 0.6f, 0.75f, 0f, 0.7f); s(0f, 0.5f, 0.5f, 0.48f, 0.97f, 0.5f)
    }
    t("uzuv.yuzgec", "Yüzgeç", LIMB, "Kanat", "Çizgili, yelpaze biçimli yüzgeç") {
        path(closed = true) { moveTo(0f, 1f); quadTo(0.2f, 0.2f, 0.9f, 0f); quadTo(0.7f, 0.6f, 1f, 1f); close() }
        l(0.1f, 1f, 0.6f, 0.25f); l(0.3f, 1f, 0.75f, 0.5f)
    }
    t("uzuv.biyik", "Hayvan bıyıkları", LIMB, "Baş", "Burnun iki yanında üçer bıyık") {
        l(0f, 0.2f, 0.4f, 0.4f); l(0f, 0.5f, 0.4f, 0.5f); l(0f, 0.8f, 0.4f, 0.6f); l(1f, 0.2f, 0.6f, 0.4f); l(1f, 0.5f, 0.6f, 0.5f); l(1f, 0.8f, 0.6f, 0.6f)
    }
    t("uzuv.anten", "Böcek antenleri", LIMB, "Baş", "Uçları topuzlu iki anten") {
        s(0.42f, 1f, 0.35f, 0.5f, 0.15f, 0.15f); circle(0.12f, 0.1f, 0.07f); s(0.58f, 1f, 0.65f, 0.5f, 0.85f, 0.15f); circle(0.88f, 0.1f, 0.07f)
    }
    t("uzuv.kabuk", "Kaplumbağa kabuğu", LIMB, "Gövde", "Kubbe kabuk ve altıgen desen") {
        path(closed = true) { moveTo(0f, 0.9f); cubicTo(0f, 0f, 1f, 0f, 1f, 0.9f); close() }
        polygon(0.5f, 0.55f, 0.18f, 6, 30f); l(0.34f, 0.46f, 0.12f, 0.35f); l(0.66f, 0.46f, 0.88f, 0.35f); l(0.34f, 0.64f, 0.2f, 0.9f); l(0.66f, 0.64f, 0.8f, 0.9f)
    }

    // --- Basit hayvanlar (çok elemanlı) ---
    t("hayvan.balik", "Balık", ANIMAL, "Deniz", "Oval gövde, üçgen kuyruk ve göz") {
        path(closed = true) { moveTo(0f, 0.5f); quadTo(0.35f, 0.05f, 0.72f, 0.5f); quadTo(0.35f, 0.95f, 0f, 0.5f) }
        lc(0.72f, 0.5f, 1f, 0.22f, 1f, 0.78f); circle(0.2f, 0.45f, 0.04f)
    }
    t("hayvan.kus_ucan", "Uçan kuş", ANIMAL, "Kuş", "Uzaktan uçan kuş (iki kavis)") {
        path { moveTo(0f, 0.4f); quadTo(0.25f, 0.1f, 0.5f, 0.6f); quadTo(0.75f, 0.1f, 1f, 0.4f) }
    }
    t("hayvan.kus", "Kuş", ANIMAL, "Kuş", "Yuvarlak gövde, baş, gaga, kanat ve bacaklar") {
        ellipse(0.45f, 0.55f, 0.3f, 0.22f); circle(0.75f, 0.3f, 0.13f); lc(0.87f, 0.27f, 1f, 0.32f, 0.87f, 0.36f)
        s(0.3f, 0.5f, 0.45f, 0.45f, 0.6f, 0.58f); l(0.4f, 0.77f, 0.38f, 1f); l(0.52f, 0.77f, 0.54f, 1f); l(0.15f, 0.5f, 0f, 0.4f)
    }
    t("hayvan.kedi_yuz", "Kedi yüzü", ANIMAL, "Evcil", "Yuvarlak baş, sivri kulaklar, gözler, burun ve bıyıklar") {
        circle(0.5f, 0.58f, 0.38f); l(0.18f, 0.38f, 0.2f, 0.05f, 0.42f, 0.22f); l(0.58f, 0.22f, 0.8f, 0.05f, 0.82f, 0.38f)
        circle(0.38f, 0.52f, 0.05f); circle(0.62f, 0.52f, 0.05f); lc(0.46f, 0.64f, 0.54f, 0.64f, 0.5f, 0.69f)
        l(0.05f, 0.62f, 0.35f, 0.68f); l(0.95f, 0.62f, 0.65f, 0.68f)
    }
    t("hayvan.kopek_yuz", "Köpek yüzü", ANIMAL, "Evcil", "Oval baş, sarkık kulaklar, gözler ve burun") {
        ellipse(0.5f, 0.52f, 0.3f, 0.36f); sc(0.24f, 0.25f, 0.05f, 0.35f, 0.02f, 0.7f, 0.14f, 0.72f, 0.22f, 0.5f)
        sc(0.76f, 0.25f, 0.95f, 0.35f, 0.98f, 0.7f, 0.86f, 0.72f, 0.78f, 0.5f)
        circle(0.4f, 0.45f, 0.04f); circle(0.6f, 0.45f, 0.04f); ellipse(0.5f, 0.66f, 0.07f, 0.05f); s(0.42f, 0.78f, 0.5f, 0.82f, 0.58f, 0.78f)
    }
    t("hayvan.tavsan_yuz", "Tavşan yüzü", ANIMAL, "Evcil", "Yuvarlak baş, uzun kulaklar, gözler ve burun") {
        circle(0.5f, 0.7f, 0.28f); ellipse(0.38f, 0.22f, 0.08f, 0.22f); ellipse(0.62f, 0.22f, 0.08f, 0.22f)
        circle(0.4f, 0.66f, 0.035f); circle(0.6f, 0.66f, 0.035f); lc(0.47f, 0.76f, 0.53f, 0.76f, 0.5f, 0.8f)
    }
    t("hayvan.ayi_yuz", "Ayı yüzü", ANIMAL, "Vahşi", "Yuvarlak baş, yuvarlak kulaklar, burun ağzı") {
        circle(0.5f, 0.56f, 0.4f); circle(0.16f, 0.2f, 0.13f); circle(0.84f, 0.2f, 0.13f); circle(0.36f, 0.48f, 0.04f); circle(0.64f, 0.48f, 0.04f)
        ellipse(0.5f, 0.7f, 0.15f, 0.11f); ellipse(0.5f, 0.65f, 0.05f, 0.03f)
    }
    t("hayvan.salyangoz", "Salyangoz", ANIMAL, "Böcek", "Sarmal kabuk, uzun gövde ve antenler") {
        path {
            moveTo(0.42f, 0.42f)
            for (i in 1..40) { val a = rad(i * 18.0); val r = 0.012 * i; lineTo((0.42 + r * cos(a)).toFloat(), (0.42 + r * sin(a)).toFloat()) }
        }
        s(0f, 0.9f, 0.5f, 0.92f, 0.85f, 0.88f, 0.92f, 0.6f); l(0.88f, 0.62f, 0.85f, 0.35f); l(0.92f, 0.62f, 1f, 0.38f)
    }
    t("hayvan.kelebek", "Kelebek", ANIMAL, "Böcek", "İnce gövde, dört kanat ve antenler") {
        ellipse(0.5f, 0.55f, 0.05f, 0.3f); sc(0.47f, 0.45f, 0.2f, 0.05f, 0f, 0.2f, 0.1f, 0.5f); sc(0.53f, 0.45f, 0.8f, 0.05f, 1f, 0.2f, 0.9f, 0.5f)
        sc(0.47f, 0.6f, 0.15f, 0.62f, 0.12f, 0.9f, 0.4f, 0.85f); sc(0.53f, 0.6f, 0.85f, 0.62f, 0.88f, 0.9f, 0.6f, 0.85f)
        s(0.48f, 0.27f, 0.4f, 0.05f); s(0.52f, 0.27f, 0.6f, 0.05f)
    }
    t("hayvan.yilan", "Yılan", ANIMAL, "Sürüngen", "Kıvrımlı uzun gövde ve baş") {
        s(0f, 0.8f, 0.2f, 0.55f, 0.4f, 0.8f, 0.6f, 0.55f, 0.8f, 0.8f, 0.95f, 0.5f); ellipse(0.93f, 0.4f, 0.07f, 0.1f); l(0.93f, 0.3f, 0.91f, 0.18f)
    }
    t("hayvan.ordek", "Ördek", ANIMAL, "Kuş", "Kayık biçimli gövde, baş, gaga ve kanat") {
        path(closed = true) { moveTo(0f, 0.5f); quadTo(0.05f, 1f, 0.55f, 0.95f); quadTo(0.95f, 0.9f, 0.9f, 0.55f); lineTo(0.55f, 0.55f); quadTo(0.3f, 0.55f, 0f, 0.5f) }
        circle(0.72f, 0.3f, 0.16f); lc(0.87f, 0.28f, 1f, 0.34f, 0.86f, 0.38f); s(0.3f, 0.68f, 0.5f, 0.78f, 0.7f, 0.66f)
    }
    t("hayvan.kaplumbaga", "Kaplumbağa", ANIMAL, "Sürüngen", "Kubbe kabuk, baş, ayaklar ve kuyruk") {
        path(closed = true) { moveTo(0.15f, 0.75f); cubicTo(0.15f, 0.1f, 0.8f, 0.1f, 0.8f, 0.75f); close() }
        circle(0.9f, 0.62f, 0.1f); l(0.25f, 0.75f, 0.25f, 0.9f); l(0.68f, 0.75f, 0.68f, 0.9f); l(0.15f, 0.72f, 0f, 0.78f)
    }
    t("hayvan.orumcek", "Örümcek", ANIMAL, "Böcek", "Yuvarlak gövde ve iki yanda dörder kırık bacak") {
        circle(0.5f, 0.55f, 0.16f); circle(0.5f, 0.32f, 0.08f)
        for (i in 0..3) { val y = 0.45f + i * 0.07f; val dy = (i - 1.5f) * 0.18f; l(0.36f, y, 0.15f, y - 0.2f + dy * 0.5f, 0f, y + dy); l(0.64f, y, 0.85f, y - 0.2f + dy * 0.5f, 1f, y + dy) }
    }
    t("hayvan.ari", "Arı", ANIMAL, "Böcek", "Çizgili oval gövde, kanatlar ve iğne") {
        ellipse(0.48f, 0.6f, 0.34f, 0.24f); l(0.36f, 0.38f, 0.36f, 0.82f); l(0.52f, 0.36f, 0.52f, 0.84f); lc(0.82f, 0.56f, 1f, 0.6f, 0.82f, 0.66f)
        ellipse(0.38f, 0.22f, 0.12f, 0.18f); ellipse(0.6f, 0.22f, 0.12f, 0.18f)
    }
    t("hayvan.fare", "Fare", ANIMAL, "Evcil", "Damla biçimli gövde, yuvarlak kulak, burun ve kuyruk") {
        path(closed = true) { moveTo(0.05f, 0.6f); quadTo(0.4f, 0.1f, 0.8f, 0.75f); quadTo(0.5f, 0.9f, 0.05f, 0.6f) }
        circle(0.3f, 0.3f, 0.1f); s(0.8f, 0.75f, 0.95f, 0.6f, 0.9f, 0.4f, 1f, 0.3f)
    }
}

internal fun TemplateScope.plantAndSkyTemplates() {
    // --- Bitki ---
    t("bitki.yaprak", "Yaprak", PLANT, "Yaprak", "Sivri uçlu yaprak ve orta damar") {
        sc(0f, 1f, 0.1f, 0.4f, 0.6f, 0.05f, 1f, 0f, 0.9f, 0.45f, 0.45f, 0.9f); l(0f, 1f, 0.95f, 0.05f)
    }
    t("bitki.yaprak_damarli", "Yaprak (damarlı)", PLANT, "Yaprak", "Yaprak, orta damar ve yan damarlar") {
        sc(0.5f, 0f, 0.9f, 0.35f, 0.8f, 0.75f, 0.5f, 0.92f, 0.2f, 0.75f, 0.1f, 0.35f); l(0.5f, 0.05f, 0.5f, 1f)
        l(0.5f, 0.35f, 0.75f, 0.22f); l(0.5f, 0.35f, 0.25f, 0.22f); l(0.5f, 0.6f, 0.78f, 0.48f); l(0.5f, 0.6f, 0.22f, 0.48f)
    }
    t("bitki.akcaagac", "Akçaağaç yaprağı", PLANT, "Yaprak", "Beş loblu sivri akçaağaç yaprağı ve sapı") {
        lc(0.5f, 0f, 0.6f, 0.25f, 0.8f, 0.15f, 0.72f, 0.38f, 1f, 0.4f, 0.75f, 0.6f, 0.82f, 0.72f, 0.52f, 0.72f, 0.5f, 0.8f,
            0.48f, 0.72f, 0.18f, 0.72f, 0.25f, 0.6f, 0f, 0.4f, 0.28f, 0.38f, 0.2f, 0.15f, 0.4f, 0.25f)
        l(0.5f, 0.78f, 0.5f, 1f)
    }
    t("bitki.papatya", "Papatya", PLANT, "Çiçek", "Yuvarlak göbek ve çevresinde yuvarlak taç yaprakları") {
        circle(0.5f, 0.5f, 0.12f)
        for (i in 0 until 8) { val a = rad(45.0 * i); ellipse((0.5 + 0.3 * cos(a)).toFloat(), (0.5 + 0.3 * sin(a)).toFloat(), 0.17f, 0.08f, (45f * i)) }
    }
    t("bitki.cicek_basit", "Çiçek (saplı)", PLANT, "Çiçek", "Beş yapraklı çiçek, sap ve yaprak") {
        circle(0.5f, 0.28f, 0.07f)
        for (i in 0 until 5) { val a = rad(-90.0 + 72.0 * i); circle((0.5 + 0.15 * cos(a)).toFloat(), (0.28 + 0.15 * sin(a)).toFloat(), 0.09f) }
        l(0.5f, 0.52f, 0.5f, 1f); sc(0.5f, 0.8f, 0.65f, 0.62f, 0.8f, 0.65f, 0.65f, 0.78f)
    }
    t("bitki.lale", "Lale", PLANT, "Çiçek", "Üç dişli kupa çiçek ve sap") {
        l(0.25f, 0.1f, 0.38f, 0.25f, 0.5f, 0.05f, 0.62f, 0.25f, 0.75f, 0.1f); path { moveTo(0.25f, 0.1f); cubicTo(0.2f, 0.55f, 0.8f, 0.55f, 0.75f, 0.1f) }
        l(0.5f, 0.45f, 0.5f, 1f); sc(0.5f, 0.9f, 0.3f, 0.6f, 0.4f, 0.65f)
    }
    t("bitki.gul", "Gül", PLANT, "Çiçek", "Sarmal taç ve sap") {
        path {
            moveTo(0.5f, 0.3f)
            for (i in 1..30) { val a = rad(i * 24.0); val r = 0.009 * i; lineTo((0.5 + r * cos(a)).toFloat(), (0.3 + r * 0.85 * sin(a)).toFloat()) }
        }
        l(0.5f, 0.58f, 0.5f, 1f); sc(0.5f, 0.78f, 0.7f, 0.65f, 0.78f, 0.72f, 0.62f, 0.8f)
    }
    t("bitki.agac", "Ağaç", PLANT, "Ağaç", "Bulut biçimli yuvarlak taç ve gövde") {
        path(closed = true) {
            moveTo(0.3f, 0.65f); quadTo(0f, 0.6f, 0.1f, 0.35f); quadTo(0.1f, 0.05f, 0.4f, 0.08f); quadTo(0.6f, -0.05f, 0.8f, 0.12f)
            quadTo(1.02f, 0.3f, 0.9f, 0.5f); quadTo(0.95f, 0.7f, 0.7f, 0.65f); close()
        }
        l(0.42f, 0.65f, 0.4f, 1f); l(0.58f, 0.65f, 0.6f, 1f)
    }
    t("bitki.cam", "Çam ağacı", PLANT, "Ağaç", "Üst üste üç katlı üçgen taç ve gövde") {
        l(0.5f, 0f, 0.15f, 0.3f, 0.35f, 0.3f, 0.05f, 0.58f, 0.3f, 0.58f, 0f, 0.85f, 1f, 0.85f, 0.7f, 0.58f, 0.95f, 0.58f, 0.65f, 0.3f, 0.85f, 0.3f, 0.5f, 0f)
        rect(0.43f, 0.85f, 0.14f, 0.15f)
    }
    t("bitki.palmiye", "Palmiye", PLANT, "Ağaç", "Kavisli ince gövde ve sarkan yapraklar") {
        s(0.5f, 1f, 0.45f, 0.6f, 0.55f, 0.2f); s(0.55f, 0.2f, 0.3f, 0.05f, 0f, 0.25f); s(0.55f, 0.2f, 0.8f, 0.05f, 1f, 0.3f)
        s(0.55f, 0.2f, 0.3f, 0.3f, 0.15f, 0.5f); s(0.55f, 0.2f, 0.75f, 0.3f, 0.88f, 0.5f)
    }
    t("bitki.kaktus", "Kaktüs", PLANT, "Bitki", "Uzun gövde ve iki yana yukarı kalkan kollar") {
        path(closed = true) {
            moveTo(0.38f, 1f); lineTo(0.38f, 0.1f); quadTo(0.5f, -0.04f, 0.62f, 0.1f); lineTo(0.62f, 1f); close()
        }
        l(0.38f, 0.6f, 0.15f, 0.6f, 0.15f, 0.3f); l(0.38f, 0.5f, 0.25f, 0.5f, 0.25f, 0.3f); l(0.62f, 0.5f, 0.85f, 0.5f, 0.85f, 0.2f); l(0.62f, 0.4f, 0.75f, 0.4f, 0.75f, 0.2f)
    }
    t("bitki.cimen", "Çimen", PLANT, "Bitki", "Yan yana sivri ot yaprakları") {
        l(0f, 1f, 0.1f, 0.3f, 0.2f, 1f, 0.3f, 0.1f, 0.4f, 1f, 0.5f, 0.35f, 0.6f, 1f, 0.72f, 0f, 0.8f, 1f, 0.9f, 0.4f, 1f, 1f)
    }
    t("bitki.saksi", "Saksıda bitki", PLANT, "Bitki", "Yamuk saksı ve üç yapraklı bitki") {
        lc(0.25f, 0.6f, 0.75f, 0.6f, 0.68f, 1f, 0.32f, 1f); l(0.5f, 0.6f, 0.5f, 0.25f)
        sc(0.5f, 0.3f, 0.3f, 0.1f, 0.15f, 0.2f, 0.3f, 0.35f); sc(0.5f, 0.3f, 0.7f, 0.1f, 0.85f, 0.2f, 0.7f, 0.35f); sc(0.5f, 0.25f, 0.42f, 0.05f, 0.5f, 0f, 0.58f, 0.05f)
    }

    // --- Gökyüzü ve doğa ---
    t("doga.gunes", "Güneş", SKY, "Gökyüzü", "Daire ve çevresinde ışınlar") {
        circle(0.5f, 0.5f, 0.22f)
        for (i in 0 until 8) { val a = rad(45.0 * i); l((0.5 + 0.3 * cos(a)).toFloat(), (0.5 + 0.3 * sin(a)).toFloat(), (0.5 + 0.48 * cos(a)).toFloat(), (0.5 + 0.48 * sin(a)).toFloat()) }
    }
    t("doga.hilal", "Hilal (ay)", SKY, "Gökyüzü", "İnce hilal biçimli ay") {
        path(closed = true) { moveTo(0.55f, 0f); cubicTo(-0.15f, 0.05f, -0.15f, 0.95f, 0.55f, 1f); cubicTo(0.15f, 0.8f, 0.15f, 0.2f, 0.55f, 0f) }
    }
    t("doga.yildiz", "Yıldız", SKY, "Gökyüzü", "Beş köşeli yıldız") { star(0.5f, 0.53f, 0.5f, 0.2f) }
    t("doga.yildiz_cizgi", "Yıldız (tek çizgi)", SKY, "Gökyüzü", "Kesişen çizgilerle tek hamlede çizilen beş köşeli yıldız") {
        val pts = (0..5).flatMap { i -> val a = rad(-90.0 + 144.0 * i); listOf((0.5 + 0.5 * cos(a)).toFloat(), (0.53 + 0.5 * sin(a)).toFloat()) }
        l(*pts.toFloatArray())
    }
    t("doga.bulut", "Bulut", SKY, "Gökyüzü", "Düz tabanlı, kabarık bulut") {
        path(closed = true) {
            moveTo(0.1f, 0.75f); quadTo(-0.05f, 0.5f, 0.2f, 0.45f); quadTo(0.25f, 0.15f, 0.5f, 0.25f); quadTo(0.7f, 0.05f, 0.82f, 0.4f)
            quadTo(1.05f, 0.5f, 0.9f, 0.75f); close()
        }
    }
    t("doga.yagmur", "Yağmur bulutu", SKY, "Gökyüzü", "Bulut ve altında yağmur çizgileri") {
        path(closed = true) {
            moveTo(0.1f, 0.55f); quadTo(-0.05f, 0.35f, 0.2f, 0.3f); quadTo(0.25f, 0.05f, 0.5f, 0.12f); quadTo(0.7f, -0.02f, 0.82f, 0.25f)
            quadTo(1.05f, 0.35f, 0.9f, 0.55f); close()
        }
        l(0.25f, 0.68f, 0.2f, 0.85f); l(0.5f, 0.68f, 0.45f, 0.95f); l(0.75f, 0.68f, 0.7f, 0.85f)
    }
    t("doga.simsek", "Şimşek", SKY, "Gökyüzü", "Zikzak yıldırım") { lc(0.6f, 0f, 0.2f, 0.55f, 0.48f, 0.55f, 0.35f, 1f, 0.8f, 0.4f, 0.52f, 0.4f, 0.72f, 0f) }
    t("doga.damla", "Damla", SKY, "Gökyüzü", "Ucu sivri su damlası") {
        path(closed = true) { moveTo(0.5f, 0f); cubicTo(0.6f, 0.3f, 0.9f, 0.5f, 0.85f, 0.7f); cubicTo(0.8f, 1.05f, 0.2f, 1.05f, 0.15f, 0.7f); cubicTo(0.1f, 0.5f, 0.4f, 0.3f, 0.5f, 0f) }
    }
    t("doga.kar_tanesi", "Kar tanesi", SKY, "Gökyüzü", "Altı kollu, uçları çatallı kar tanesi") {
        for (i in 0 until 3) { val a = rad(60.0 * i + 90); l((0.5 + 0.5 * cos(a)).toFloat(), (0.5 + 0.5 * sin(a)).toFloat(), (0.5 - 0.5 * cos(a)).toFloat(), (0.5 - 0.5 * sin(a)).toFloat()) }
        for (i in 0 until 6) {
            val a = rad(60.0 * i + 90); val bx = 0.5 + 0.32 * cos(a); val by = 0.5 + 0.32 * sin(a)
            l((bx + 0.12 * cos(a + 0.8)).toFloat(), (by + 0.12 * sin(a + 0.8)).toFloat(), bx.toFloat(), by.toFloat(), (bx + 0.12 * cos(a - 0.8)).toFloat(), (by + 0.12 * sin(a - 0.8)).toFloat())
        }
    }
    t("doga.gokkusagi", "Gökkuşağı", SKY, "Gökyüzü", "İç içe üç yarım yay") {
        arc(0.5f, 1f, 0.5f, 0.9f, 180f, 180f); arc(0.5f, 1f, 0.38f, 0.7f, 180f, 180f); arc(0.5f, 1f, 0.26f, 0.5f, 180f, 180f)
    }
    t("doga.dag", "Dağlar", SKY, "Doğa", "Karlı zirveli iki dağ") {
        l(0f, 1f, 0.35f, 0.15f, 0.6f, 0.65f, 0.75f, 0.4f, 1f, 1f); l(0.24f, 0.42f, 0.3f, 0.36f, 0.35f, 0.45f, 0.41f, 0.36f, 0.46f, 0.42f)
    }
    t("doga.dalga", "Dalga", SKY, "Doğa", "Tepesi kıvrılan deniz dalgası") {
        s(0f, 0.8f, 0.2f, 0.7f, 0.4f, 0.3f, 0.65f, 0.1f, 0.85f, 0.2f, 0.8f, 0.4f, 0.65f, 0.35f); s(0f, 1f, 0.5f, 0.9f, 1f, 1f)
    }
    t("doga.tas", "Taş / kaya", SKY, "Doğa", "Köşeli yuvarlak kaya") { lc(0f, 1f, 0.1f, 0.5f, 0.35f, 0.2f, 0.7f, 0.25f, 0.95f, 0.55f, 1f, 1f) }
    t("doga.ates", "Ateş / alev", SKY, "Doğa", "Yukarı dalgalanan alev dilleri") {
        path(closed = true) {
            moveTo(0.5f, 1f); cubicTo(0.05f, 1f, 0.05f, 0.55f, 0.25f, 0.35f); quadTo(0.28f, 0.55f, 0.38f, 0.58f); quadTo(0.3f, 0.2f, 0.55f, 0f)
            quadTo(0.55f, 0.35f, 0.72f, 0.45f); quadTo(0.75f, 0.3f, 0.8f, 0.28f); cubicTo(1f, 0.6f, 0.95f, 1f, 0.5f, 1f)
        }
    }
}
