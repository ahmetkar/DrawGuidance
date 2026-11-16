package com.karapp.drawguidance.content.shapes

import kotlin.math.cos
import kotlin.math.sin

/*
 * v54 çizim modları (resim ve çizgi roman) için eklenen şablonlar: tam boy hayvanlar, deniz canlıları, böcekler,
 * bitki türleri, doğa öğeleri, taşıtlar, ev ve atölye eşyaları, müzik aletleri, spor, giyim, yapılar, hayal
 * öğeleri ve çizgi roman simgeleri. Kaynak: çizim kitaplarının "temel şekillerle çizim" konuları (her nesne birkaç
 * basit çizgiyle tanınabilir biçimde). Anahtarlar kalıcıdır.
 */

private const val ANIMAL = "Hayvan"
private const val PLANT = "Bitki"
private const val NATURE = "Gökyüzü ve doğa"
private const val VEHICLE = "Taşıt"
private const val THING = "Nesne"
private const val TOOL = "Alet"
private const val MUSIC = "Müzik"
private const val SPORT = "Spor ve oyun"
private const val CLOTH = "Giyim"
private const val BUILD = "Yapı"
private const val FANTASY = "Hayal"
private const val FOOD = "Yiyecek ve mutfak"
private const val FURNITURE = "Mobilya"

private fun rad(deg: Float) = Math.toRadians(deg.toDouble())

internal fun TemplateScope.v54AnimalTemplates() {
    t("hayvan.at", "At (yandan)", ANIMAL, "Tam hayvan", "Gövde, uzun boyun, baş, dört bacak ve kuyruk") {
        ellipse(0.48f, 0.5f, 0.27f, 0.14f)
        s(0.68f, 0.42f, 0.76f, 0.24f, 0.8f, 0.12f)
        sc(0.76f, 0.1f, 0.95f, 0.16f, 0.97f, 0.24f, 0.86f, 0.24f, 0.78f, 0.2f)
        l(0.3f, 0.6f, 0.28f, 0.95f); l(0.38f, 0.62f, 0.4f, 0.95f); l(0.58f, 0.62f, 0.56f, 0.95f); l(0.66f, 0.6f, 0.68f, 0.95f)
        s(0.22f, 0.45f, 0.1f, 0.55f, 0.08f, 0.75f)
        s(0.74f, 0.12f, 0.68f, 0.2f, 0.66f, 0.36f)
    }
    t("hayvan.inek", "İnek (yandan)", ANIMAL, "Tam hayvan", "Kutu gövde, lekeler, baş, boynuzlar ve bacaklar") {
        roundRect(0.18f, 0.32f, 0.56f, 0.32f, 0.08f)
        roundRect(0.72f, 0.24f, 0.2f, 0.22f, 0.06f)
        l(0.76f, 0.24f, 0.74f, 0.16f); l(0.88f, 0.24f, 0.9f, 0.16f)
        l(0.25f, 0.64f, 0.25f, 0.92f); l(0.35f, 0.64f, 0.35f, 0.92f); l(0.58f, 0.64f, 0.58f, 0.92f); l(0.68f, 0.64f, 0.68f, 0.92f)
        sc(0.3f, 0.38f, 0.4f, 0.36f, 0.42f, 0.46f, 0.32f, 0.48f); sc(0.52f, 0.44f, 0.62f, 0.42f, 0.6f, 0.54f, 0.5f, 0.54f)
        s(0.18f, 0.38f, 0.1f, 0.5f, 0.12f, 0.64f)
    }
    t("hayvan.domuz", "Domuz (yandan)", ANIMAL, "Tam hayvan", "Yuvarlak gövde, burun, kulak, kısa bacaklar, kıvrık kuyruk") {
        ellipse(0.48f, 0.5f, 0.32f, 0.22f)
        ellipse(0.84f, 0.48f, 0.06f, 0.08f)
        l(0.68f, 0.32f, 0.72f, 0.2f, 0.78f, 0.34f)
        l(0.3f, 0.68f, 0.3f, 0.86f); l(0.42f, 0.7f, 0.42f, 0.86f); l(0.56f, 0.7f, 0.56f, 0.86f); l(0.66f, 0.68f, 0.66f, 0.86f)
        s(0.16f, 0.46f, 0.08f, 0.42f, 0.1f, 0.34f, 0.16f, 0.38f)
    }
    t("hayvan.tavuk", "Tavuk", ANIMAL, "Tam hayvan", "Damla gövde, ibik, gaga, kanat ve ayaklar") {
        sc(0.2f, 0.45f, 0.5f, 0.25f, 0.62f, 0.12f, 0.75f, 0.2f, 0.75f, 0.45f, 0.62f, 0.72f, 0.35f, 0.72f)
        l(0.75f, 0.24f, 0.86f, 0.28f, 0.75f, 0.31f)
        sc(0.62f, 0.12f, 0.64f, 0.04f, 0.68f, 0.1f, 0.72f, 0.04f, 0.74f, 0.16f)
        s(0.38f, 0.45f, 0.5f, 0.5f, 0.6f, 0.45f)
        l(0.42f, 0.72f, 0.42f, 0.9f); l(0.36f, 0.95f, 0.42f, 0.9f, 0.48f, 0.95f)
        l(0.55f, 0.72f, 0.55f, 0.9f); l(0.49f, 0.95f, 0.55f, 0.9f, 0.61f, 0.95f)
    }
    t("hayvan.kugu", "Kuğu", ANIMAL, "Tam hayvan", "S kıvrımlı boyun, gaga ve suda gövde") {
        sc(0.1f, 0.62f, 0.4f, 0.55f, 0.75f, 0.6f, 0.9f, 0.5f, 0.85f, 0.75f, 0.45f, 0.82f)
        s(0.3f, 0.58f, 0.22f, 0.4f, 0.32f, 0.2f, 0.26f, 0.08f, 0.18f, 0.12f)
        l(0.18f, 0.12f, 0.08f, 0.16f)
        s(0.0f, 0.9f, 0.3f, 0.88f, 0.6f, 0.92f, 1f, 0.9f)
    }
    t("hayvan.papagan", "Papağan (dalda)", ANIMAL, "Tam hayvan", "Dik duran gövde, kıvrık gaga, uzun kuyruk ve dal") {
        sc(0.42f, 0.12f, 0.6f, 0.14f, 0.62f, 0.4f, 0.56f, 0.62f, 0.44f, 0.62f, 0.36f, 0.36f)
        s(0.42f, 0.18f, 0.32f, 0.2f, 0.34f, 0.3f)
        circle(0.52f, 0.22f, 0.025f)
        l(0.46f, 0.62f, 0.42f, 0.98f); l(0.54f, 0.62f, 0.56f, 0.98f)
        l(0.1f, 0.66f, 0.9f, 0.62f)
    }
    t("hayvan.kartal", "Kartal (kanatlar açık)", ANIMAL, "Tam hayvan", "Geniş tüylü kanatlar, beyaz baş ve kuyruk") {
        ellipse(0.5f, 0.5f, 0.08f, 0.2f)
        circle(0.5f, 0.24f, 0.06f)
        path(closed = true) { moveTo(0.44f, 0.42f); lineTo(0f, 0.28f); lineTo(0.06f, 0.4f); lineTo(0.02f, 0.46f); lineTo(0.1f, 0.52f); lineTo(0.08f, 0.58f); lineTo(0.44f, 0.56f) }
        path(closed = true) { moveTo(0.56f, 0.42f); lineTo(1f, 0.28f); lineTo(0.94f, 0.4f); lineTo(0.98f, 0.46f); lineTo(0.9f, 0.52f); lineTo(0.92f, 0.58f); lineTo(0.56f, 0.56f) }
        l(0.44f, 0.7f, 0.4f, 0.92f, 0.5f, 0.86f, 0.6f, 0.92f, 0.56f, 0.7f)
    }
    t("hayvan.geyik", "Geyik", ANIMAL, "Tam hayvan", "İnce bacaklı gövde, boyun, dallı boynuzlar") {
        ellipse(0.45f, 0.55f, 0.24f, 0.12f)
        l(0.64f, 0.48f, 0.72f, 0.28f)
        ellipse(0.76f, 0.25f, 0.08f, 0.05f, -15f)
        l(0.72f, 0.2f, 0.66f, 0.04f); l(0.69f, 0.12f, 0.6f, 0.08f); l(0.78f, 0.2f, 0.86f, 0.04f); l(0.83f, 0.12f, 0.92f, 0.1f)
        l(0.3f, 0.64f, 0.28f, 0.95f); l(0.38f, 0.66f, 0.38f, 0.95f); l(0.54f, 0.66f, 0.54f, 0.95f); l(0.62f, 0.64f, 0.64f, 0.95f)
    }
    t("hayvan.deve", "Deve", ANIMAL, "Tam hayvan", "İki hörgüç, uzun boyun ve bacaklar") {
        path { moveTo(0.18f, 0.55f); quadTo(0.28f, 0.2f, 0.4f, 0.45f); quadTo(0.52f, 0.2f, 0.64f, 0.48f); lineTo(0.72f, 0.42f) }
        l(0.18f, 0.55f, 0.72f, 0.58f)
        s(0.72f, 0.42f, 0.8f, 0.3f, 0.82f, 0.16f); ellipse(0.87f, 0.16f, 0.07f, 0.04f)
        l(0.24f, 0.56f, 0.22f, 0.94f); l(0.32f, 0.57f, 0.32f, 0.94f); l(0.56f, 0.58f, 0.56f, 0.94f); l(0.64f, 0.58f, 0.66f, 0.94f)
    }
    t("hayvan.kanguru", "Kanguru", ANIMAL, "Tam hayvan", "Eğik gövde, uzun ayaklar, kalın kuyruk, uzun kulaklar") {
        sc(0.4f, 0.3f, 0.55f, 0.25f, 0.62f, 0.5f, 0.55f, 0.75f, 0.38f, 0.72f, 0.34f, 0.5f)
        ellipse(0.55f, 0.18f, 0.07f, 0.06f); l(0.52f, 0.12f, 0.5f, 0.02f); l(0.56f, 0.12f, 0.6f, 0.02f)
        l(0.44f, 0.74f, 0.4f, 0.92f, 0.62f, 0.94f)
        s(0.36f, 0.68f, 0.2f, 0.8f, 0.04f, 0.94f)
        l(0.58f, 0.42f, 0.66f, 0.48f)
    }
    t("hayvan.maymun", "Maymun", ANIMAL, "Tam hayvan", "Yuvarlak baş, kulaklar, gövde, uzun kollar ve kıvrık kuyruk") {
        circle(0.5f, 0.2f, 0.12f); circle(0.36f, 0.2f, 0.04f); circle(0.64f, 0.2f, 0.04f)
        ellipse(0.5f, 0.24f, 0.07f, 0.05f)
        ellipse(0.5f, 0.52f, 0.12f, 0.17f)
        s(0.4f, 0.42f, 0.24f, 0.55f, 0.2f, 0.72f); s(0.6f, 0.42f, 0.76f, 0.55f, 0.8f, 0.72f)
        l(0.44f, 0.68f, 0.4f, 0.92f); l(0.56f, 0.68f, 0.6f, 0.92f)
        s(0.6f, 0.62f, 0.84f, 0.8f, 0.94f, 0.6f, 0.86f, 0.5f)
    }
    t("hayvan.fare_tam", "Fare (yandan)", ANIMAL, "Tam hayvan", "Damla gövde, büyük kulak, bıyık ve ince kuyruk") {
        sc(0.2f, 0.7f, 0.45f, 0.45f, 0.72f, 0.5f, 0.82f, 0.62f, 0.7f, 0.72f)
        circle(0.62f, 0.44f, 0.08f)
        l(0.82f, 0.62f, 0.95f, 0.58f); l(0.82f, 0.64f, 0.95f, 0.68f)
        s(0.2f, 0.7f, 0.1f, 0.6f, 0.04f, 0.72f, 0f, 0.62f)
    }
    t("hayvan.sincap", "Sincap", ANIMAL, "Tam hayvan", "Oturan gövde, kocaman kabarık kuyruk ve sivri kulak") {
        sc(0.5f, 0.35f, 0.66f, 0.45f, 0.66f, 0.8f, 0.48f, 0.88f, 0.4f, 0.65f)
        circle(0.6f, 0.28f, 0.09f); l(0.58f, 0.2f, 0.6f, 0.12f, 0.64f, 0.2f)
        sc(0.42f, 0.85f, 0.18f, 0.8f, 0.08f, 0.5f, 0.2f, 0.18f, 0.38f, 0.2f, 0.3f, 0.45f, 0.44f, 0.65f)
        ellipse(0.72f, 0.5f, 0.05f, 0.04f)
    }
    t("hayvan.kirpi", "Kirpi", ANIMAL, "Tam hayvan", "Dikenli sırt, sivri burun ve küçük ayaklar") {
        path { moveTo(0.1f, 0.75f); var x = 0.1f; var up = true; while (x < 0.78f) { x += 0.06f; lineTo(x, if (up) 0.3f + kotlin.math.abs(x - 0.44f) * 0.6f else 0.45f + kotlin.math.abs(x - 0.44f) * 0.5f); up = !up }; lineTo(0.8f, 0.6f) }
        s(0.8f, 0.6f, 0.95f, 0.7f, 0.82f, 0.78f)
        l(0.1f, 0.75f, 0.82f, 0.78f)
        l(0.3f, 0.78f, 0.3f, 0.86f); l(0.6f, 0.78f, 0.6f, 0.86f)
    }
    t("hayvan.denizati", "Denizatı", ANIMAL, "Deniz", "Kıvrık gövde, uzun burun ve sırt yüzgeci") {
        path { moveTo(0.5f, 0.15f); cubicTo(0.75f, 0.2f, 0.7f, 0.5f, 0.55f, 0.6f); cubicTo(0.45f, 0.7f, 0.6f, 0.85f, 0.5f, 0.92f); cubicTo(0.42f, 0.96f, 0.38f, 0.86f, 0.46f, 0.84f) }
        path { moveTo(0.5f, 0.15f); cubicTo(0.35f, 0.18f, 0.32f, 0.35f, 0.42f, 0.45f); cubicTo(0.48f, 0.55f, 0.4f, 0.62f, 0.44f, 0.72f) }
        l(0.42f, 0.2f, 0.26f, 0.22f)
        l(0.66f, 0.38f, 0.78f, 0.42f, 0.66f, 0.5f)
    }
    t("hayvan.deniz_yildizi", "Deniz yıldızı", ANIMAL, "Deniz", "Yuvarlak uçlu beş kollu yıldız") {
        star(0.5f, 0.52f, 0.46f, 0.2f, 5, -90f)
        for (i in 0..4) { val a = rad(-90f + 72f * i); circle(0.5f + 0.24f * cos(a).toFloat(), 0.52f + 0.24f * sin(a).toFloat(), 0.025f) }
    }
    t("hayvan.deniz_kabugu", "Deniz kabuğu", ANIMAL, "Deniz", "Yelpaze kabuk ve ışınsal çizgiler") {
        path(closed = true) { moveTo(0.4f, 0.9f); quadTo(0f, 0.5f, 0.2f, 0.2f); quadTo(0.5f, 0.02f, 0.8f, 0.2f); quadTo(1f, 0.5f, 0.6f, 0.9f) }
        for (i in 0..4) { val x = 0.18f + i * 0.16f; l(0.5f, 0.88f, x, 0.2f + kotlin.math.abs(x - 0.5f) * 0.3f) }
    }
    t("hayvan.kaplumbaga_tam", "Kaplumbağa (yandan)", ANIMAL, "Deniz", "Kubbe kabuk, baş, ayaklar ve kabuk desenleri") {
        arc(0.48f, 0.68f, 0.34f, 0.4f, 180f, 180f); l(0.14f, 0.68f, 0.82f, 0.68f)
        ellipse(0.88f, 0.6f, 0.08f, 0.06f)
        l(0.22f, 0.68f, 0.2f, 0.82f, 0.3f, 0.82f, 0.32f, 0.68f); l(0.62f, 0.68f, 0.62f, 0.82f, 0.72f, 0.82f, 0.72f, 0.68f)
        polygon(0.48f, 0.48f, 0.1f, 6); l(0.38f, 0.48f, 0.22f, 0.5f); l(0.58f, 0.48f, 0.74f, 0.5f)
    }
    t("hayvan.ari_tam", "Arı (çizgili)", ANIMAL, "Böcek", "Çizgili oval gövde, iki kanat, iğne ve anten") {
        ellipse(0.5f, 0.6f, 0.26f, 0.16f)
        l(0.4f, 0.45f, 0.4f, 0.75f); l(0.5f, 0.44f, 0.5f, 0.76f); l(0.6f, 0.46f, 0.6f, 0.74f)
        ellipse(0.42f, 0.32f, 0.1f, 0.14f, -20f); ellipse(0.6f, 0.32f, 0.1f, 0.14f, 20f)
        l(0.76f, 0.6f, 0.86f, 0.6f)
        circle(0.22f, 0.56f, 0.06f); s(0.2f, 0.5f, 0.16f, 0.4f, 0.1f, 0.38f)
    }
    t("hayvan.yusufcuk", "Yusufçuk", ANIMAL, "Böcek", "İnce uzun gövde ve dört ince kanat") {
        circle(0.5f, 0.12f, 0.05f); l(0.5f, 0.17f, 0.5f, 0.95f)
        ellipse(0.28f, 0.28f, 0.22f, 0.06f, -8f); ellipse(0.72f, 0.28f, 0.22f, 0.06f, 8f)
        ellipse(0.3f, 0.4f, 0.2f, 0.05f, 10f); ellipse(0.7f, 0.4f, 0.2f, 0.05f, -10f)
    }
    t("hayvan.bocek_tam", "Böcek (altı bacaklı)", ANIMAL, "Böcek", "Oval kabuk, orta çizgi, baş, altı bacak") {
        ellipse(0.5f, 0.56f, 0.2f, 0.28f); l(0.5f, 0.3f, 0.5f, 0.84f)
        circle(0.5f, 0.22f, 0.08f)
        for (i in 0..2) { val y = 0.44f + i * 0.14f; l(0.31f, y, 0.12f, y - 0.06f + i * 0.04f); l(0.69f, y, 0.88f, y - 0.06f + i * 0.04f) }
        l(0.46f, 0.15f, 0.38f, 0.04f); l(0.54f, 0.15f, 0.62f, 0.04f)
    }
    t("hayvan.ordek_yavru", "Ördek yavrusu (suda)", ANIMAL, "Tam hayvan", "Yuvarlak baş, gaga, gövde ve su çizgisi") {
        circle(0.4f, 0.32f, 0.14f); l(0.26f, 0.32f, 0.14f, 0.36f, 0.27f, 0.38f)
        sc(0.36f, 0.48f, 0.7f, 0.46f, 0.86f, 0.36f, 0.84f, 0.62f, 0.4f, 0.66f)
        circle(0.36f, 0.28f, 0.02f)
        s(0.05f, 0.72f, 0.3f, 0.7f, 0.6f, 0.74f, 0.95f, 0.71f)
    }
    t("hayvan.kopek_oturan", "Köpek (oturan, önden)", ANIMAL, "Tam hayvan", "Baş, sarkık kulaklar, oturan gövde ve ön patiler") {
        circle(0.5f, 0.24f, 0.13f)
        ellipse(0.35f, 0.28f, 0.05f, 0.11f, 15f); ellipse(0.65f, 0.28f, 0.05f, 0.11f, -15f)
        ellipse(0.5f, 0.3f, 0.04f, 0.03f)
        sc(0.38f, 0.38f, 0.62f, 0.38f, 0.72f, 0.9f, 0.28f, 0.9f)
        l(0.44f, 0.6f, 0.44f, 0.9f); l(0.56f, 0.6f, 0.56f, 0.9f)
    }
    t("hayvan.kedi_yatan", "Kedi (uzanan)", ANIMAL, "Tam hayvan", "Uzun yatık gövde, kulaklı baş ve kuyruk") {
        sc(0.1f, 0.78f, 0.25f, 0.55f, 0.65f, 0.55f, 0.8f, 0.78f)
        circle(0.82f, 0.56f, 0.1f); l(0.76f, 0.5f, 0.76f, 0.38f, 0.82f, 0.46f); l(0.86f, 0.46f, 0.92f, 0.38f, 0.92f, 0.5f)
        s(0.12f, 0.74f, 0.04f, 0.6f, 0.1f, 0.5f)
    }
}

internal fun TemplateScope.v54NatureTemplates() {
    t("bitki.cam_agaci", "Çam ağacı (katlı)", PLANT, "Ağaç", "Üst üste üç üçgen kat ve gövde") {
        lc(0.5f, 0.04f, 0.3f, 0.32f, 0.7f, 0.32f); lc(0.5f, 0.2f, 0.24f, 0.56f, 0.76f, 0.56f); lc(0.5f, 0.4f, 0.16f, 0.82f, 0.84f, 0.82f)
        rect(0.44f, 0.82f, 0.12f, 0.15f)
    }
    t("bitki.sogut", "Söğüt ağacı", PLANT, "Ağaç", "Gövde, yuvarlak taç ve sarkan dallar") {
        l(0.48f, 0.98f, 0.5f, 0.45f); l(0.52f, 0.98f, 0.54f, 0.45f)
        arc(0.5f, 0.42f, 0.36f, 0.3f, 180f, 180f)
        for (i in 0..5) { val x = 0.18f + i * 0.13f; s(x, 0.38f, x - 0.02f, 0.6f, x + 0.01f, 0.78f) }
    }
    t("bitki.cali", "Çalı", PLANT, "Ağaç", "Yer seviyesinde kabarık yuvarlak çalı") {
        path(closed = true) { moveTo(0.06f, 0.8f); quadTo(0f, 0.5f, 0.22f, 0.46f); quadTo(0.3f, 0.2f, 0.52f, 0.3f); quadTo(0.74f, 0.18f, 0.8f, 0.44f); quadTo(1f, 0.5f, 0.94f, 0.8f) }
        l(0f, 0.8f, 1f, 0.8f)
    }
    t("bitki.bambu", "Bambu", PLANT, "Bitki", "Boğumlu iki gövde ve yapraklar") {
        l(0.4f, 1f, 0.4f, 0f); l(0.5f, 1f, 0.5f, 0f); for (i in 1..3) l(0.4f, i * 0.25f, 0.5f, i * 0.25f)
        l(0.62f, 1f, 0.64f, 0.1f); l(0.72f, 1f, 0.74f, 0.1f); for (i in 1..3) l(0.63f, i * 0.25f + 0.1f, 0.73f, i * 0.25f + 0.1f)
        ellipse(0.24f, 0.22f, 0.14f, 0.04f, 20f); ellipse(0.86f, 0.42f, 0.14f, 0.04f, -20f)
    }
    t("bitki.lotus", "Nilüfer (lotus)", PLANT, "Çiçek", "Sivri yapraklı açık çiçek ve su yaprağı") {
        sc(0.5f, 0.15f, 0.6f, 0.4f, 0.5f, 0.62f, 0.4f, 0.4f)
        sc(0.35f, 0.25f, 0.52f, 0.5f, 0.45f, 0.64f, 0.22f, 0.5f)
        sc(0.65f, 0.25f, 0.78f, 0.5f, 0.55f, 0.64f, 0.48f, 0.5f)
        ellipse(0.5f, 0.75f, 0.42f, 0.1f)
    }
    t("bitki.zambak", "Zambak", PLANT, "Çiçek", "Altı sivri taç yaprak, gövde ve yaprak") {
        for (i in 0..5) { val a = rad(-90f + 60f * i); val ex = 0.5f + 0.32f * cos(a).toFloat(); val ey = 0.35f + 0.28f * sin(a).toFloat(); sc(0.5f, 0.35f, (0.5f + ex) / 2 + 0.04f * sin(a).toFloat(), (0.35f + ey) / 2 - 0.04f * cos(a).toFloat(), ex, ey, (0.5f + ex) / 2 - 0.04f * sin(a).toFloat(), (0.35f + ey) / 2 + 0.04f * cos(a).toFloat()) }
        l(0.5f, 0.6f, 0.5f, 1f); ellipse(0.62f, 0.82f, 0.12f, 0.04f, -30f)
    }
    t("bitki.orkide", "Saksıda orkide", PLANT, "Çiçek", "Kavisli dal, çiçekler, yapraklar ve saksı") {
        lc(0.32f, 0.72f, 0.68f, 0.72f, 0.62f, 1f, 0.38f, 1f)
        ellipse(0.38f, 0.66f, 0.14f, 0.05f, -20f); ellipse(0.62f, 0.66f, 0.14f, 0.05f, 20f)
        s(0.5f, 0.72f, 0.48f, 0.3f, 0.7f, 0.12f)
        circle(0.5f, 0.36f, 0.06f); circle(0.58f, 0.2f, 0.06f); circle(0.7f, 0.12f, 0.05f)
    }
    t("bitki.sukulent", "Sukulent", PLANT, "Bitki", "Saksıda katmanlı sivri yapraklar") {
        lc(0.25f, 0.62f, 0.75f, 0.62f, 0.68f, 0.95f, 0.32f, 0.95f)
        for (i in 0..4) { val x = 0.2f + i * 0.15f; sc(x, 0.62f, x + 0.05f, 0.4f, x + 0.1f, 0.62f) }
        sc(0.42f, 0.6f, 0.5f, 0.3f, 0.58f, 0.6f)
    }
    t("bitki.cicek_demeti", "Çiçek demeti", PLANT, "Çiçek", "Bağlı saplar ve üç çiçek") {
        circle(0.3f, 0.25f, 0.1f); circle(0.55f, 0.18f, 0.1f); circle(0.72f, 0.35f, 0.1f)
        l(0.3f, 0.35f, 0.5f, 0.75f); l(0.55f, 0.28f, 0.5f, 0.75f); l(0.72f, 0.45f, 0.5f, 0.75f)
        lc(0.42f, 0.62f, 0.58f, 0.62f, 0.62f, 0.72f, 0.38f, 0.72f)
        l(0.5f, 0.75f, 0.44f, 0.98f); l(0.5f, 0.75f, 0.56f, 0.98f)
    }
    t("doga.dag_karli", "Karlı dağlar", NATURE, "Manzara", "İki zirve ve kar başlıkları") {
        l(0f, 0.95f, 0.35f, 0.2f, 0.6f, 0.65f, 0.75f, 0.4f, 1f, 0.95f)
        l(0.24f, 0.44f, 0.3f, 0.38f, 0.35f, 0.45f, 0.41f, 0.38f, 0.46f, 0.44f)
        l(0.69f, 0.52f, 0.72f, 0.48f, 0.75f, 0.53f, 0.79f, 0.49f, 0.82f, 0.54f)
    }
    t("doga.selale", "Şelale", NATURE, "Manzara", "İki kaya arasından akan su ve gölcük") {
        l(0.1f, 0.1f, 0.3f, 0.12f, 0.32f, 0.75f); l(0.9f, 0.1f, 0.7f, 0.12f, 0.68f, 0.75f)
        for (i in 0..3) { val x = 0.36f + i * 0.09f; l(x, 0.14f, x, 0.76f) }
        ellipse(0.5f, 0.85f, 0.42f, 0.1f)
    }
    t("doga.gol_kamis", "Göl ve kamışlar", NATURE, "Manzara", "Su çizgileri ve kamış sapları") {
        ellipse(0.5f, 0.72f, 0.45f, 0.18f)
        s(0.3f, 0.7f, 0.42f, 0.68f, 0.5f, 0.71f); s(0.55f, 0.78f, 0.65f, 0.76f, 0.72f, 0.79f)
        l(0.12f, 0.72f, 0.14f, 0.2f); ellipse(0.14f, 0.25f, 0.03f, 0.08f)
        l(0.22f, 0.74f, 0.2f, 0.3f); ellipse(0.2f, 0.34f, 0.03f, 0.08f)
    }
    t("doga.magara", "Mağara girişi", NATURE, "Manzara", "Kaya kütlesi ve kemerli karanlık giriş") {
        path { moveTo(0f, 0.95f); quadTo(0.1f, 0.2f, 0.5f, 0.1f); quadTo(0.9f, 0.2f, 1f, 0.95f) }
        path { moveTo(0.3f, 0.95f); quadTo(0.32f, 0.45f, 0.5f, 0.42f); quadTo(0.68f, 0.45f, 0.7f, 0.95f) }
        l(0f, 0.95f, 1f, 0.95f)
    }
    t("doga.ay_dolunay", "Dolunay", NATURE, "Gök", "Daire ve kraterler") {
        circle(0.5f, 0.5f, 0.42f); circle(0.36f, 0.38f, 0.08f); circle(0.6f, 0.62f, 0.1f); circle(0.62f, 0.32f, 0.05f)
    }
    t("doga.bulut_yagmurlu", "Yağmur bulutu", NATURE, "Hava", "Kabarık bulut ve eğik damla çizgileri") {
        path(closed = true) { moveTo(0.15f, 0.5f); quadTo(0.05f, 0.3f, 0.25f, 0.28f); quadTo(0.32f, 0.08f, 0.52f, 0.16f); quadTo(0.72f, 0.06f, 0.78f, 0.28f); quadTo(0.98f, 0.32f, 0.86f, 0.5f) }
        for (i in 0..3) { val x = 0.26f + i * 0.16f; l(x, 0.6f, x - 0.06f, 0.82f) }
    }
    t("doga.gunes_batan", "Batan güneş", NATURE, "Gök", "Ufuk çizgisi, yarım güneş ve ışınlar") {
        l(0f, 0.7f, 1f, 0.7f); arc(0.5f, 0.7f, 0.25f, 0.25f, 180f, 180f)
        for (i in 0..4) { val a = rad(-170f + 40f * i); l(0.5f + 0.32f * cos(a).toFloat(), 0.7f + 0.32f * sin(a).toFloat(), 0.5f + 0.44f * cos(a).toFloat(), 0.7f + 0.44f * sin(a).toFloat()) }
        s(0.2f, 0.82f, 0.4f, 0.8f, 0.6f, 0.83f, 0.8f, 0.81f)
    }
    t("doga.ruzgar", "Rüzgâr", NATURE, "Hava", "Ucu kıvrılan üç rüzgâr çizgisi") {
        path { moveTo(0.05f, 0.3f); lineTo(0.7f, 0.3f); cubicTo(0.9f, 0.3f, 0.9f, 0.1f, 0.75f, 0.12f) }
        path { moveTo(0.05f, 0.5f); lineTo(0.82f, 0.5f); cubicTo(1f, 0.5f, 1f, 0.72f, 0.84f, 0.7f) }
        l(0.12f, 0.7f, 0.55f, 0.7f)
    }
    t("doga.kum_tepesi", "Kum tepeleri ve kaktüs", NATURE, "Manzara", "Dalgalı tepeler ve kollu kaktüs") {
        s(0f, 0.8f, 0.3f, 0.62f, 0.6f, 0.78f, 1f, 0.6f)
        l(0.7f, 0.72f, 0.7f, 0.3f); l(0.78f, 0.7f, 0.78f, 0.3f); arc(0.74f, 0.3f, 0.04f, 0.04f, 180f, 180f)
        l(0.7f, 0.5f, 0.62f, 0.5f, 0.62f, 0.4f); l(0.78f, 0.45f, 0.86f, 0.45f, 0.86f, 0.36f)
    }
    t("doga.alev", "Kamp ateşi", NATURE, "Ateş", "Alev dilleri ve çapraz kütükler") {
        sc(0.5f, 0.1f, 0.62f, 0.35f, 0.7f, 0.55f, 0.6f, 0.72f, 0.4f, 0.72f, 0.3f, 0.55f, 0.4f, 0.4f)
        sc(0.5f, 0.4f, 0.58f, 0.55f, 0.55f, 0.68f, 0.45f, 0.68f, 0.42f, 0.55f)
        l(0.2f, 0.92f, 0.8f, 0.76f); l(0.2f, 0.76f, 0.8f, 0.92f)
    }
}

internal fun TemplateScope.v54VehicleTemplates() {
    t("tasit.kamp_araci", "Minibüs", VEHICLE, "Kara taşıtı", "Uzun kutu gövde, pencereler ve iki tekerlek") {
        roundRect(0.05f, 0.3f, 0.9f, 0.42f, 0.08f)
        rect(0.12f, 0.36f, 0.16f, 0.14f); rect(0.34f, 0.36f, 0.16f, 0.14f); rect(0.56f, 0.36f, 0.16f, 0.14f); l(0.78f, 0.36f, 0.9f, 0.36f, 0.9f, 0.52f, 0.78f, 0.52f)
        circle(0.25f, 0.74f, 0.08f); circle(0.75f, 0.74f, 0.08f)
    }
    t("tasit.taksi", "Taksi", VEHICLE, "Kara taşıtı", "Araba gövdesi, üstte tabela ve tekerlekler") {
        path(closed = true) { moveTo(0.05f, 0.68f); lineTo(0.05f, 0.5f); lineTo(0.25f, 0.48f); lineTo(0.35f, 0.32f); lineTo(0.68f, 0.32f); lineTo(0.8f, 0.48f); lineTo(0.95f, 0.52f); lineTo(0.95f, 0.68f) }
        rect(0.44f, 0.2f, 0.14f, 0.1f)
        circle(0.25f, 0.72f, 0.08f); circle(0.75f, 0.72f, 0.08f)
        l(0.5f, 0.34f, 0.5f, 0.48f)
    }
    t("tasit.itfaiye", "İtfaiye aracı", VEHICLE, "Kara taşıtı", "Kamyon gövdesi, üstte merdiven ve tekerlekler") {
        rect(0.05f, 0.38f, 0.6f, 0.32f); path(closed = true) { moveTo(0.65f, 0.7f); lineTo(0.65f, 0.4f); lineTo(0.82f, 0.4f); lineTo(0.95f, 0.55f); lineTo(0.95f, 0.7f) }
        l(0.08f, 0.32f, 0.6f, 0.24f); l(0.08f, 0.36f, 0.6f, 0.28f); for (i in 0..4) { val x = 0.14f + i * 0.1f; l(x, 0.35f - i * 0.015f, x, 0.31f - i * 0.015f) }
        circle(0.2f, 0.74f, 0.07f); circle(0.5f, 0.74f, 0.07f); circle(0.82f, 0.74f, 0.07f)
    }
    t("tasit.polis_araci", "Polis arabası", VEHICLE, "Kara taşıtı", "Araba gövdesi, tepe lambası ve şerit") {
        path(closed = true) { moveTo(0.05f, 0.68f); lineTo(0.05f, 0.5f); lineTo(0.25f, 0.48f); lineTo(0.35f, 0.32f); lineTo(0.68f, 0.32f); lineTo(0.8f, 0.48f); lineTo(0.95f, 0.52f); lineTo(0.95f, 0.68f) }
        arc(0.5f, 0.32f, 0.06f, 0.06f, 180f, 180f)
        l(0.08f, 0.58f, 0.92f, 0.58f)
        circle(0.25f, 0.72f, 0.08f); circle(0.75f, 0.72f, 0.08f)
    }
    t("tasit.scooter", "Scooter", VEHICLE, "Kara taşıtı", "Dik gidon, ince taban ve iki küçük tekerlek") {
        l(0.72f, 0.82f, 0.68f, 0.15f); l(0.58f, 0.15f, 0.78f, 0.15f)
        l(0.2f, 0.8f, 0.72f, 0.8f)
        circle(0.2f, 0.86f, 0.07f); circle(0.74f, 0.86f, 0.07f)
    }
    t("tasit.gemi", "Yolcu gemisi", VEHICLE, "Deniz taşıtı", "Uzun gövde, katlar, bacalar ve lombozlar") {
        lc(0.02f, 0.6f, 0.98f, 0.6f, 0.86f, 0.82f, 0.12f, 0.82f)
        rect(0.2f, 0.45f, 0.6f, 0.15f); rect(0.3f, 0.34f, 0.4f, 0.11f)
        lc(0.42f, 0.34f, 0.44f, 0.18f, 0.52f, 0.18f, 0.54f, 0.34f)
        for (i in 0..4) circle(0.24f + i * 0.13f, 0.7f, 0.025f)
    }
    t("tasit.kano", "Kano", VEHICLE, "Deniz taşıtı", "Uçları kalkık ince tekne ve kürek") {
        path(closed = true) { moveTo(0f, 0.5f); quadTo(0.5f, 0.75f, 1f, 0.5f); quadTo(0.5f, 0.6f, 0f, 0.5f) }
        l(0.35f, 0.25f, 0.68f, 0.85f); ellipse(0.7f, 0.88f, 0.04f, 0.08f, -28f)
    }
    t("tasit.tramvay", "Tramvay", VEHICLE, "Raylı taşıt", "Kutu vagon, pencereler, pantograf ve ray") {
        roundRect(0.08f, 0.3f, 0.84f, 0.45f, 0.05f)
        for (i in 0..3) rect(0.13f + i * 0.2f, 0.36f, 0.14f, 0.14f)
        l(0.4f, 0.3f, 0.5f, 0.12f, 0.6f, 0.3f); l(0.3f, 0.1f, 0.7f, 0.1f)
        l(0f, 0.85f, 1f, 0.85f); circle(0.25f, 0.8f, 0.05f); circle(0.75f, 0.8f, 0.05f)
    }
    t("tasit.vagon", "Tren vagonu", VEHICLE, "Raylı taşıt", "Dikdörtgen vagon, kapı, tekerlekler ve bağlantı") {
        rect(0.1f, 0.3f, 0.8f, 0.42f); rect(0.44f, 0.4f, 0.12f, 0.32f)
        circle(0.25f, 0.78f, 0.07f); circle(0.75f, 0.78f, 0.07f)
        l(0f, 0.6f, 0.1f, 0.6f); l(0.9f, 0.6f, 1f, 0.6f)
    }
    t("tasit.planor", "Planör", VEHICLE, "Hava taşıtı", "Çok uzun ince kanat ve ince gövde") {
        ellipse(0.5f, 0.5f, 0.3f, 0.05f); l(0.02f, 0.46f, 0.98f, 0.46f); l(0.05f, 0.5f, 0.95f, 0.5f)
        l(0.78f, 0.48f, 0.86f, 0.3f, 0.9f, 0.48f)
    }
    t("tasit.zeplin", "Zeplin", VEHICLE, "Hava taşıtı", "Uzun balon gövde, kuyruk yüzgeçleri ve kabin") {
        ellipse(0.48f, 0.4f, 0.4f, 0.18f)
        lc(0.82f, 0.32f, 0.98f, 0.18f, 0.96f, 0.36f); lc(0.82f, 0.48f, 0.98f, 0.62f, 0.96f, 0.44f)
        roundRect(0.38f, 0.6f, 0.2f, 0.08f, 0.03f); l(0.42f, 0.57f, 0.42f, 0.6f); l(0.54f, 0.57f, 0.54f, 0.6f)
    }
    t("tasit.uzay_mekigi", "Uzay mekiği", VEHICLE, "Hava taşıtı", "Sivri burun, gövde, delta kanatlar") {
        path(closed = true) { moveTo(0.5f, 0.02f); quadTo(0.62f, 0.15f, 0.62f, 0.35f); lineTo(0.62f, 0.7f); lineTo(0.88f, 0.86f); lineTo(0.62f, 0.86f); lineTo(0.38f, 0.86f); lineTo(0.12f, 0.86f); lineTo(0.38f, 0.7f); lineTo(0.38f, 0.35f); quadTo(0.38f, 0.15f, 0.5f, 0.02f) }
        l(0.44f, 0.86f, 0.42f, 0.98f); l(0.56f, 0.86f, 0.58f, 0.98f)
        l(0.44f, 0.2f, 0.56f, 0.2f)
    }
    t("tasit.paten", "Paten", VEHICLE, "Kara taşıtı", "Bilekli ayakkabı ve dört küçük tekerlek") {
        path(closed = true) { moveTo(0.25f, 0.1f); lineTo(0.5f, 0.1f); lineTo(0.52f, 0.5f); quadTo(0.85f, 0.52f, 0.85f, 0.72f); lineTo(0.18f, 0.72f); lineTo(0.22f, 0.3f) }
        for (i in 0..3) circle(0.24f + i * 0.18f, 0.82f, 0.06f)
    }
}

internal fun TemplateScope.v54ObjectTemplates() {
    t("esya.lamba_masa", "Masa lambası", THING, "Ev", "Taban, eklemli kol ve koni başlık") {
        ellipse(0.35f, 0.92f, 0.2f, 0.04f); l(0.35f, 0.9f, 0.3f, 0.55f, 0.55f, 0.3f)
        lc(0.5f, 0.22f, 0.78f, 0.18f, 0.9f, 0.45f, 0.62f, 0.5f)
        circle(0.3f, 0.55f, 0.025f)
    }
    t("esya.cay_bardagi", "Çay bardağı ve tabağı", FOOD, "Mutfak", "İnce belli bardak ve altında tabak") {
        path { moveTo(0.32f, 0.2f); quadTo(0.42f, 0.5f, 0.34f, 0.8f); lineTo(0.66f, 0.8f); quadTo(0.58f, 0.5f, 0.68f, 0.2f) }
        l(0.32f, 0.2f, 0.68f, 0.2f); ellipse(0.5f, 0.86f, 0.32f, 0.05f)
    }
    t("esya.tabak_kase", "Kase", FOOD, "Mutfak", "Yarım daire kase ve ağız elipsi") {
        ellipse(0.5f, 0.4f, 0.42f, 0.08f); arc(0.5f, 0.4f, 0.42f, 0.42f, 0f, 180f); l(0.4f, 0.82f, 0.6f, 0.82f)
    }
    t("esya.kavanoz", "Kavanoz", FOOD, "Mutfak", "Kapaklı kavanoz ve etiket") {
        rect(0.3f, 0.12f, 0.4f, 0.1f); roundRect(0.24f, 0.22f, 0.52f, 0.7f, 0.08f); rect(0.32f, 0.45f, 0.36f, 0.2f)
    }
    t("esya.sepet", "Hasır sepet", THING, "Ev", "Kulplu sepet ve örgü çizgileri") {
        lc(0.12f, 0.45f, 0.88f, 0.45f, 0.78f, 0.92f, 0.22f, 0.92f)
        arc(0.5f, 0.45f, 0.3f, 0.35f, 180f, 180f)
        l(0.15f, 0.6f, 0.85f, 0.6f); l(0.18f, 0.76f, 0.82f, 0.76f)
    }
    t("esya.kutu_karton", "Karton kutu", THING, "Ev", "Açık kapaklı kutu") {
        rect(0.2f, 0.4f, 0.6f, 0.5f); lc(0.2f, 0.4f, 0.05f, 0.25f, 0.3f, 0.25f, 0.45f, 0.4f); lc(0.8f, 0.4f, 0.95f, 0.25f, 0.7f, 0.25f, 0.55f, 0.4f)
    }
    t("esya.palet", "Boya paleti", TOOL, "Sanat", "Başparmak delikli palet ve boya lekeleri") {
        path(closed = true) { moveTo(0.5f, 0.1f); cubicTo(0.95f, 0.1f, 1f, 0.6f, 0.75f, 0.85f); cubicTo(0.55f, 1f, 0.6f, 0.7f, 0.42f, 0.78f); cubicTo(0.1f, 0.9f, 0f, 0.4f, 0.2f, 0.22f); cubicTo(0.3f, 0.12f, 0.4f, 0.1f, 0.5f, 0.1f) }
        circle(0.35f, 0.62f, 0.06f); circle(0.3f, 0.32f, 0.05f); circle(0.5f, 0.24f, 0.05f); circle(0.7f, 0.3f, 0.05f); circle(0.78f, 0.52f, 0.05f)
    }
    t("esya.firca_boya", "Boya fırçası", TOOL, "Sanat", "Uzun sap, metal kısım ve kıl ucu") {
        l(0.1f, 0.9f, 0.55f, 0.45f); l(0.14f, 0.94f, 0.59f, 0.49f)
        lc(0.55f, 0.45f, 0.59f, 0.49f, 0.66f, 0.42f, 0.62f, 0.38f)
        sc(0.62f, 0.38f, 0.8f, 0.15f, 0.9f, 0.1f, 0.85f, 0.2f, 0.66f, 0.42f)
    }
    t("esya.sovale", "Şövale", TOOL, "Sanat", "Üç ayak, tuval ve tutucu") {
        l(0.5f, 0.05f, 0.2f, 0.98f); l(0.5f, 0.05f, 0.8f, 0.98f); l(0.5f, 0.05f, 0.5f, 0.98f)
        rect(0.24f, 0.16f, 0.52f, 0.42f); l(0.2f, 0.6f, 0.8f, 0.6f)
    }
    t("esya.klasor", "Klasör", THING, "Ofis", "Sekmeli dosya klasörü") {
        path(closed = true) { moveTo(0.08f, 0.25f); lineTo(0.38f, 0.25f); lineTo(0.45f, 0.33f); lineTo(0.92f, 0.33f); lineTo(0.92f, 0.85f); lineTo(0.08f, 0.85f) }
        l(0.08f, 0.42f, 0.92f, 0.42f)
    }
    t("esya.kalemlik", "Kalemlik", THING, "Ofis", "Silindir kap ve içinde kalemler") {
        rect(0.3f, 0.45f, 0.4f, 0.5f); l(0.38f, 0.45f, 0.3f, 0.08f); l(0.5f, 0.45f, 0.5f, 0.05f); l(0.62f, 0.45f, 0.72f, 0.1f)
        lc(0.47f, 0.05f, 0.5f, 0f, 0.53f, 0.05f)
    }
    t("esya.not_defteri", "Spiralli defter", THING, "Ofis", "Dikdörtgen sayfa, spiral halkalar ve satırlar") {
        rect(0.2f, 0.08f, 0.6f, 0.86f); for (i in 0..5) circle(0.2f, 0.16f + i * 0.14f, 0.03f)
        for (i in 0..4) l(0.3f, 0.25f + i * 0.13f, 0.72f, 0.25f + i * 0.13f)
    }
    t("esya.hesap_makinesi", "Hesap makinesi", THING, "Ofis", "Ekranlı kutu ve tuş ızgarası") {
        roundRect(0.22f, 0.05f, 0.56f, 0.9f, 0.05f); rect(0.3f, 0.12f, 0.4f, 0.16f)
        for (r in 0..3) for (c in 0..2) rect(0.3f + c * 0.14f, 0.36f + r * 0.14f, 0.1f, 0.1f)
    }
    t("esya.zarf_mektup", "Mühürlü mektup", THING, "Ofis", "Zarf, kapak ve yuvarlak mühür") {
        rect(0.08f, 0.22f, 0.84f, 0.56f); l(0.08f, 0.22f, 0.5f, 0.55f, 0.92f, 0.22f); circle(0.5f, 0.55f, 0.07f)
    }
    t("esya.anahtar_halka", "Anahtarlık", THING, "Ev", "Halka ve iki anahtar") {
        circle(0.3f, 0.3f, 0.16f)
        circle(0.48f, 0.5f, 0.06f); l(0.52f, 0.54f, 0.86f, 0.88f); l(0.76f, 0.78f, 0.82f, 0.72f); l(0.82f, 0.84f, 0.88f, 0.78f)
        circle(0.4f, 0.58f, 0.05f); l(0.4f, 0.63f, 0.4f, 0.95f); l(0.4f, 0.85f, 0.46f, 0.85f)
    }
    t("esya.fener", "El feneri", TOOL, "Atölye", "Silindir gövde, geniş başlık ve ışık") {
        rect(0.1f, 0.42f, 0.44f, 0.16f); lc(0.54f, 0.38f, 0.66f, 0.3f, 0.66f, 0.7f, 0.54f, 0.62f)
        l(0.7f, 0.3f, 0.98f, 0.12f); l(0.7f, 0.7f, 0.98f, 0.88f); l(0.7f, 0.5f, 0.98f, 0.5f)
    }
    t("esya.testere", "El testeresi", TOOL, "Atölye", "Dişli bıçak ve tutamak") {
        path { moveTo(0.3f, 0.35f); lineTo(0.98f, 0.5f); var x = 0.98f; var down = true; while (x > 0.32f) { x -= 0.04f; lineTo(x, if (down) 0.62f - (0.98f - x) * 0.02f else 0.57f - (0.98f - x) * 0.02f); down = !down }; lineTo(0.3f, 0.62f) }
        roundRect(0.04f, 0.3f, 0.26f, 0.36f, 0.08f); ellipse(0.17f, 0.48f, 0.06f, 0.08f)
    }
    t("esya.pense", "Pense", TOOL, "Atölye", "Çapraz iki sap ve çene") {
        l(0.2f, 0.95f, 0.5f, 0.45f, 0.62f, 0.05f); l(0.8f, 0.95f, 0.5f, 0.45f, 0.38f, 0.05f); circle(0.5f, 0.45f, 0.04f)
    }
    t("esya.matkap", "Matkap", TOOL, "Atölye", "Gövde, tutamak, uç") {
        roundRect(0.15f, 0.25f, 0.55f, 0.2f, 0.06f); lc(0.35f, 0.45f, 0.55f, 0.45f, 0.5f, 0.85f, 0.32f, 0.85f)
        l(0.7f, 0.35f, 0.98f, 0.35f)
    }
    t("esya.boya_kovasi", "Boya kovası", TOOL, "Atölye", "Kulplu silindir kova ve damla") {
        ellipse(0.5f, 0.3f, 0.3f, 0.07f); l(0.2f, 0.3f, 0.24f, 0.88f, 0.76f, 0.88f, 0.8f, 0.3f)
        arc(0.5f, 0.3f, 0.3f, 0.25f, 180f, 180f); sc(0.32f, 0.34f, 0.36f, 0.5f, 0.32f, 0.54f, 0.28f, 0.5f)
    }
    t("esya.mutfak_tencere", "Tencere", FOOD, "Mutfak", "Kapaklı tencere ve kulplar") {
        rect(0.18f, 0.4f, 0.64f, 0.45f); ellipse(0.5f, 0.38f, 0.34f, 0.06f); circle(0.5f, 0.28f, 0.04f)
        l(0.18f, 0.5f, 0.06f, 0.5f); l(0.82f, 0.5f, 0.94f, 0.5f)
    }
    t("esya.cay_fincani_buhar", "Dumanı tüten kupa", FOOD, "Mutfak", "Kulplu kupa ve dalgalı buhar") {
        rect(0.25f, 0.4f, 0.4f, 0.5f); arc(0.7f, 0.6f, 0.1f, 0.12f, -90f, 180f)
        s(0.35f, 0.32f, 0.3f, 0.22f, 0.38f, 0.12f, 0.33f, 0.02f); s(0.52f, 0.32f, 0.47f, 0.22f, 0.55f, 0.12f, 0.5f, 0.02f)
    }
    t("esya.yastik", "Yastık", FURNITURE, "Ev", "Köşeleri kabarık yastık") {
        path(closed = true) { moveTo(0.1f, 0.25f); quadTo(0.5f, 0.35f, 0.9f, 0.25f); quadTo(0.8f, 0.5f, 0.9f, 0.75f); quadTo(0.5f, 0.65f, 0.1f, 0.75f); quadTo(0.2f, 0.5f, 0.1f, 0.25f) }
    }
    t("esya.ayna", "Ayna", FURNITURE, "Ev", "Oval çerçeveli ayna ve yansıma çizgileri") {
        ellipse(0.5f, 0.45f, 0.28f, 0.4f); ellipse(0.5f, 0.45f, 0.22f, 0.34f); l(0.38f, 0.3f, 0.46f, 0.2f); l(0.38f, 0.4f, 0.54f, 0.22f)
    }
    t("esya.saksi_kaktus", "Saksıda kaktüs", PLANT, "Bitki", "Yuvarlak kaktüs gövdesi, dikenler ve saksı") {
        lc(0.28f, 0.62f, 0.72f, 0.62f, 0.66f, 0.95f, 0.34f, 0.95f)
        sc(0.5f, 0.2f, 0.66f, 0.3f, 0.66f, 0.62f, 0.34f, 0.62f, 0.34f, 0.3f)
        l(0.5f, 0.24f, 0.5f, 0.6f); l(0.42f, 0.4f, 0.38f, 0.38f); l(0.58f, 0.46f, 0.62f, 0.44f)
    }
    t("esya.cerceve", "Resim çerçevesi", FURNITURE, "Ev", "İç içe iki dikdörtgen ve dağ resmi") {
        rect(0.1f, 0.15f, 0.8f, 0.7f); rect(0.18f, 0.23f, 0.64f, 0.54f); l(0.18f, 0.7f, 0.38f, 0.42f, 0.52f, 0.6f, 0.62f, 0.5f, 0.82f, 0.72f); circle(0.68f, 0.34f, 0.05f)
    }
    t("esya.duvar_saati", "Guguklu saat", FURNITURE, "Ev", "Ev biçimli saat, kadran ve sarkaç") {
        lc(0.25f, 0.35f, 0.5f, 0.1f, 0.75f, 0.35f); rect(0.28f, 0.35f, 0.44f, 0.4f); circle(0.5f, 0.52f, 0.12f)
        l(0.5f, 0.52f, 0.5f, 0.44f); l(0.5f, 0.52f, 0.56f, 0.54f); l(0.45f, 0.75f, 0.45f, 0.95f); l(0.55f, 0.75f, 0.55f, 0.9f)
    }
    t("esya.sapka_kovboy", "Kovboy şapkası", CLOTH, "Şapka", "Geniş kıvrık kenar ve oyuklu tepe") {
        path { moveTo(0.02f, 0.55f); quadTo(0.1f, 0.75f, 0.5f, 0.72f); quadTo(0.9f, 0.75f, 0.98f, 0.55f) }
        path { moveTo(0.25f, 0.62f); quadTo(0.25f, 0.2f, 0.4f, 0.22f); quadTo(0.5f, 0.3f, 0.6f, 0.22f); quadTo(0.75f, 0.2f, 0.75f, 0.62f) }
    }
    t("esya.sapka_bere", "Bere", CLOTH, "Şapka", "Kıvrık kenarlı bere ve ponpon") {
        arc(0.5f, 0.7f, 0.36f, 0.45f, 180f, 180f); rect(0.12f, 0.62f, 0.76f, 0.16f); circle(0.5f, 0.2f, 0.07f)
    }
    t("giyim.kazak", "Kazak", CLOTH, "Üst giyim", "Uzun kollu kazak ve yaka") {
        path(closed = true) { moveTo(0.35f, 0.12f); quadTo(0.5f, 0.2f, 0.65f, 0.12f); lineTo(0.9f, 0.22f); lineTo(0.98f, 0.75f); lineTo(0.85f, 0.78f); lineTo(0.78f, 0.4f); lineTo(0.78f, 0.92f); lineTo(0.22f, 0.92f); lineTo(0.22f, 0.4f); lineTo(0.15f, 0.78f); lineTo(0.02f, 0.75f); lineTo(0.1f, 0.22f) }
        l(0.22f, 0.84f, 0.78f, 0.84f)
    }
    t("giyim.etek", "Etek", CLOTH, "Alt giyim", "Bel bandı ve pileli etek") {
        rect(0.3f, 0.15f, 0.4f, 0.08f); lc(0.3f, 0.23f, 0.7f, 0.23f, 0.92f, 0.85f, 0.08f, 0.85f)
        l(0.43f, 0.23f, 0.36f, 0.85f); l(0.57f, 0.23f, 0.64f, 0.85f)
    }
    t("giyim.mont", "Mont", CLOTH, "Üst giyim", "Fermuarlı, kapüşonlu mont") {
        path(closed = true) { moveTo(0.3f, 0.12f); lineTo(0.7f, 0.12f); lineTo(0.92f, 0.25f); lineTo(0.98f, 0.8f); lineTo(0.82f, 0.8f); lineTo(0.8f, 0.92f); lineTo(0.2f, 0.92f); lineTo(0.18f, 0.8f); lineTo(0.02f, 0.8f); lineTo(0.08f, 0.25f) }
        l(0.5f, 0.16f, 0.5f, 0.92f); arc(0.5f, 0.14f, 0.18f, 0.1f, 180f, 180f); rect(0.28f, 0.55f, 0.12f, 0.06f); rect(0.6f, 0.55f, 0.12f, 0.06f)
    }
    t("giyim.bot", "Bot", CLOTH, "Ayakkabı", "Yüksek konçlu bot, bağcıklar ve taban") {
        path(closed = true) { moveTo(0.25f, 0.05f); lineTo(0.55f, 0.05f); lineTo(0.56f, 0.55f); quadTo(0.95f, 0.6f, 0.95f, 0.82f); lineTo(0.15f, 0.82f); lineTo(0.22f, 0.4f) }
        l(0.12f, 0.9f, 0.98f, 0.9f); l(0.12f, 0.9f, 0.15f, 0.82f); l(0.98f, 0.9f, 0.95f, 0.82f)
        for (i in 0..3) l(0.3f, 0.15f + i * 0.1f, 0.5f, 0.15f + i * 0.1f)
    }
    t("giyim.topuklu", "Topuklu ayakkabı", CLOTH, "Ayakkabı", "Sivri burun, ince topuk") {
        path { moveTo(0.1f, 0.3f); quadTo(0.3f, 0.6f, 0.55f, 0.72f); lineTo(0.95f, 0.75f); lineTo(0.95f, 0.8f); lineTo(0.5f, 0.82f); quadTo(0.25f, 0.6f, 0.15f, 0.55f); lineTo(0.15f, 0.92f); lineTo(0.1f, 0.92f); lineTo(0.1f, 0.3f) }
    }
    t("giyim.atki", "Atkı", CLOTH, "Aksesuar", "Boyna dolanan atkı ve saçaklı uçlar") {
        ellipse(0.5f, 0.3f, 0.3f, 0.12f); lc(0.55f, 0.38f, 0.7f, 0.38f, 0.68f, 0.85f, 0.53f, 0.85f)
        for (i in 0..3) l(0.55f + i * 0.04f, 0.85f, 0.55f + i * 0.04f, 0.95f)
    }
}

internal fun TemplateScope.v54MusicSportTemplates() {
    t("muzik.piyano", "Piyano tuşları", MUSIC, "Çalgı", "Beyaz tuşlar ve aradaki siyah tuşlar") {
        rect(0.05f, 0.3f, 0.9f, 0.45f); for (i in 1..6) l(0.05f + i * 0.9f / 7, 0.3f, 0.05f + i * 0.9f / 7, 0.75f)
        for (i in listOf(1, 2, 4, 5, 6)) rect(0.05f + i * 0.9f / 7 - 0.03f, 0.3f, 0.06f, 0.26f)
    }
    t("muzik.keman", "Keman", MUSIC, "Çalgı", "Sekiz biçimli gövde, sap ve teller") {
        sc(0.5f, 0.35f, 0.66f, 0.38f, 0.62f, 0.55f, 0.7f, 0.75f, 0.5f, 0.95f, 0.3f, 0.75f, 0.38f, 0.55f, 0.34f, 0.38f)
        l(0.5f, 0.35f, 0.5f, 0.05f); s(0.5f, 0.05f, 0.56f, 0.02f, 0.54f, 0.09f)
        l(0.46f, 0.55f, 0.44f, 0.6f); l(0.54f, 0.55f, 0.56f, 0.6f)
    }
    t("muzik.trompet", "Trompet", MUSIC, "Çalgı", "Ağızlık, boru, valfler ve geniş ağız") {
        l(0.05f, 0.48f, 0.7f, 0.48f); l(0.05f, 0.54f, 0.7f, 0.54f); lc(0.7f, 0.48f, 0.95f, 0.3f, 0.95f, 0.72f, 0.7f, 0.54f)
        for (i in 0..2) { val x = 0.35f + i * 0.08f; rect(x, 0.36f, 0.04f, 0.12f) }
        roundRect(0.3f, 0.54f, 0.3f, 0.12f, 0.05f)
    }
    t("muzik.flut", "Flüt", MUSIC, "Çalgı", "Uzun ince boru ve delikler") {
        roundRect(0.03f, 0.44f, 0.94f, 0.1f, 0.04f); for (i in 0..5) circle(0.3f + i * 0.1f, 0.49f, 0.02f); circle(0.12f, 0.49f, 0.025f)
    }
    t("muzik.saz", "Bağlama (saz)", MUSIC, "Çalgı", "Armut gövde, uzun sap, perdeler") {
        sc(0.5f, 0.55f, 0.72f, 0.7f, 0.66f, 0.92f, 0.5f, 0.98f, 0.34f, 0.92f, 0.28f, 0.7f)
        l(0.47f, 0.55f, 0.47f, 0.04f); l(0.53f, 0.55f, 0.53f, 0.04f); for (i in 0..4) l(0.47f, 0.12f + i * 0.08f, 0.53f, 0.12f + i * 0.08f)
        circle(0.5f, 0.78f, 0.05f)
    }
    t("muzik.zil_ziller", "Zil ve baget", MUSIC, "Çalgı", "Ayaklı zil ve çapraz bagetler") {
        ellipse(0.5f, 0.3f, 0.35f, 0.06f); l(0.5f, 0.3f, 0.5f, 0.9f); l(0.5f, 0.9f, 0.3f, 0.98f); l(0.5f, 0.9f, 0.7f, 0.98f)
        l(0.1f, 0.85f, 0.4f, 0.55f); l(0.9f, 0.85f, 0.6f, 0.55f)
    }
    t("muzik.hoparlor", "Hoparlör", MUSIC, "Ses", "Kutu, büyük ve küçük koni") {
        roundRect(0.25f, 0.05f, 0.5f, 0.9f, 0.05f); circle(0.5f, 0.62f, 0.17f); circle(0.5f, 0.62f, 0.06f); circle(0.5f, 0.24f, 0.08f)
    }
    t("spor.voleybol", "Voleybol topu", SPORT, "Top", "Daire ve kavisli üç dilim") {
        circle(0.5f, 0.5f, 0.44f); s(0.5f, 0.06f, 0.42f, 0.4f, 0.5f, 0.5f); s(0.5f, 0.5f, 0.82f, 0.62f, 0.9f, 0.7f); s(0.5f, 0.5f, 0.3f, 0.74f, 0.2f, 0.84f)
    }
    t("spor.amerikan_futbolu", "Amerikan futbolu topu", SPORT, "Top", "Sivri uçlu oval top ve dikiş") {
        ellipse(0.5f, 0.5f, 0.44f, 0.26f, -20f); l(0.34f, 0.54f, 0.66f, 0.42f); for (i in 0..3) { val x = 0.4f + i * 0.07f; l(x - 0.02f, 0.47f - (x - 0.4f) * 0.4f, x + 0.02f, 0.55f - (x - 0.4f) * 0.4f) }
    }
    t("spor.beyzbol_sopa", "Beyzbol sopası ve top", SPORT, "Spor aleti", "Kalınlaşan sopa ve dikişli top") {
        lc(0.1f, 0.9f, 0.16f, 0.94f, 0.72f, 0.36f, 0.62f, 0.26f); circle(0.8f, 0.78f, 0.12f); s(0.72f, 0.7f, 0.78f, 0.78f, 0.72f, 0.86f)
    }
    t("spor.golf", "Golf sopası ve top", SPORT, "Spor aleti", "Uzun sap, başlık ve top") {
        l(0.6f, 0.05f, 0.45f, 0.82f); lc(0.45f, 0.82f, 0.25f, 0.84f, 0.25f, 0.9f, 0.48f, 0.88f); circle(0.7f, 0.88f, 0.05f)
    }
    t("spor.kale_futbol", "Futbol kalesi", SPORT, "Saha", "Direkler, üst direk ve ağ") {
        l(0.05f, 0.9f, 0.05f, 0.2f, 0.95f, 0.2f, 0.95f, 0.9f); for (i in 1..5) l(0.05f + i * 0.15f, 0.2f, 0.05f + i * 0.15f, 0.9f); for (i in 1..3) l(0.05f, 0.2f + i * 0.17f, 0.95f, 0.2f + i * 0.17f)
    }
    t("spor.pota", "Basketbol potası", SPORT, "Saha", "Pano, çember ve file") {
        rect(0.25f, 0.05f, 0.5f, 0.32f); rect(0.4f, 0.18f, 0.2f, 0.12f); ellipse(0.5f, 0.38f, 0.15f, 0.04f)
        l(0.36f, 0.38f, 0.42f, 0.7f); l(0.64f, 0.38f, 0.58f, 0.7f); l(0.42f, 0.7f, 0.58f, 0.7f); l(0.4f, 0.5f, 0.6f, 0.5f)
    }
    t("spor.zirve_bayrak", "Zirve bayrağı", SPORT, "Spor aleti", "Dağ zirvesine dikilmiş bayrak") {
        l(0.05f, 0.95f, 0.5f, 0.3f, 0.95f, 0.95f); l(0.5f, 0.3f, 0.5f, 0.05f); lc(0.5f, 0.05f, 0.78f, 0.12f, 0.5f, 0.2f)
    }
    t("spor.bowling", "Bowling lobutu ve topu", SPORT, "Spor aleti", "Şişe biçimli lobut ve delikli top") {
        sc(0.3f, 0.05f, 0.36f, 0.15f, 0.32f, 0.3f, 0.42f, 0.6f, 0.36f, 0.9f, 0.24f, 0.9f, 0.18f, 0.6f, 0.28f, 0.3f, 0.24f, 0.15f)
        circle(0.72f, 0.72f, 0.2f); circle(0.68f, 0.64f, 0.025f); circle(0.76f, 0.64f, 0.025f); circle(0.72f, 0.72f, 0.025f)
    }
    t("spor.zar", "Zar", SPORT, "Oyun", "Yuvarlak köşeli kare ve noktalar") {
        roundRect(0.15f, 0.15f, 0.7f, 0.7f, 0.1f); circle(0.32f, 0.32f, 0.05f); circle(0.5f, 0.5f, 0.05f); circle(0.68f, 0.68f, 0.05f)
    }
    t("spor.satranc_at", "Satranç atı", SPORT, "Oyun", "At başı profili ve taban") {
        path(closed = true) { moveTo(0.3f, 0.82f); lineTo(0.35f, 0.5f); quadTo(0.2f, 0.42f, 0.28f, 0.3f); lineTo(0.45f, 0.18f); lineTo(0.48f, 0.08f); lineTo(0.55f, 0.15f); quadTo(0.78f, 0.25f, 0.72f, 0.82f) }
        rect(0.22f, 0.82f, 0.58f, 0.1f)
    }
    t("spor.oyun_kolu", "Oyun kumandası", SPORT, "Oyun", "Kulplu gövde, yön tuşu ve düğmeler") {
        path(closed = true) { moveTo(0.2f, 0.3f); lineTo(0.8f, 0.3f); quadTo(1f, 0.32f, 0.95f, 0.75f); quadTo(0.85f, 0.85f, 0.72f, 0.62f); lineTo(0.28f, 0.62f); quadTo(0.15f, 0.85f, 0.05f, 0.75f); quadTo(0f, 0.32f, 0.2f, 0.3f) }
        l(0.18f, 0.46f, 0.34f, 0.46f); l(0.26f, 0.38f, 0.26f, 0.54f); circle(0.7f, 0.42f, 0.03f); circle(0.78f, 0.5f, 0.03f)
    }
    t("spor.yapboz", "Yapboz parçası", SPORT, "Oyun", "Çıkıntılı ve girintili kenarlı parça") {
        path(closed = true) { moveTo(0.15f, 0.25f); lineTo(0.38f, 0.25f); cubicTo(0.3f, 0.05f, 0.62f, 0.05f, 0.54f, 0.25f); lineTo(0.78f, 0.25f); lineTo(0.78f, 0.45f); cubicTo(0.98f, 0.38f, 0.98f, 0.7f, 0.78f, 0.62f); lineTo(0.78f, 0.85f); lineTo(0.15f, 0.85f); lineTo(0.15f, 0.62f); cubicTo(0.32f, 0.7f, 0.32f, 0.38f, 0.15f, 0.45f) }
    }
}

internal fun TemplateScope.v54BuildingFantasyTemplates() {
    t("yapi.ahir", "Ahır", BUILD, "Yapı", "Kırık çatılı ahır, büyük kapı ve çapraz") {
        lc(0.1f, 0.45f, 0.25f, 0.2f, 0.5f, 0.08f, 0.75f, 0.2f, 0.9f, 0.45f, 0.9f, 0.95f, 0.1f, 0.95f)
        rect(0.35f, 0.55f, 0.3f, 0.4f); l(0.35f, 0.55f, 0.65f, 0.95f); l(0.65f, 0.55f, 0.35f, 0.95f); rect(0.44f, 0.3f, 0.12f, 0.1f)
    }
    t("yapi.kuyu", "Kuyu", BUILD, "Yapı", "Taş bilezik, çatı, makara ve kova") {
        rect(0.2f, 0.6f, 0.6f, 0.32f); ellipse(0.5f, 0.6f, 0.3f, 0.05f)
        l(0.25f, 0.6f, 0.25f, 0.25f); l(0.75f, 0.6f, 0.75f, 0.25f); lc(0.15f, 0.28f, 0.5f, 0.08f, 0.85f, 0.28f)
        l(0.25f, 0.35f, 0.75f, 0.35f); l(0.5f, 0.35f, 0.5f, 0.48f); rect(0.45f, 0.48f, 0.1f, 0.08f)
    }
    t("yapi.kemer", "Taş kemer", BUILD, "Yapı", "Yarım daire kemer ve iki sütun") {
        rect(0.1f, 0.45f, 0.18f, 0.5f); rect(0.72f, 0.45f, 0.18f, 0.5f)
        arc(0.5f, 0.45f, 0.4f, 0.35f, 180f, 180f); arc(0.5f, 0.45f, 0.22f, 0.22f, 180f, 180f); l(0.5f, 0.1f, 0.5f, 0.23f)
    }
    t("yapi.sutun", "Antik sütun", BUILD, "Yapı", "Başlık, oluklu gövde ve kaide") {
        rect(0.25f, 0.05f, 0.5f, 0.08f); rect(0.25f, 0.87f, 0.5f, 0.08f); l(0.32f, 0.13f, 0.32f, 0.87f); l(0.68f, 0.13f, 0.68f, 0.87f)
        l(0.44f, 0.13f, 0.44f, 0.87f); l(0.56f, 0.13f, 0.56f, 0.87f)
    }
    t("yapi.kule_saat", "Saat kulesi", BUILD, "Yapı", "Uzun kule, saat kadranı ve sivri çatı") {
        rect(0.35f, 0.3f, 0.3f, 0.65f); lc(0.32f, 0.3f, 0.5f, 0.04f, 0.68f, 0.3f); circle(0.5f, 0.42f, 0.09f); l(0.5f, 0.42f, 0.5f, 0.36f); l(0.5f, 0.42f, 0.55f, 0.44f)
        rect(0.44f, 0.78f, 0.12f, 0.17f)
    }
    t("yapi.agac_ev", "Ağaç ev", BUILD, "Yapı", "Ağaç gövdesi, platform, kulübe ve ip merdiven") {
        l(0.42f, 0.98f, 0.44f, 0.5f); l(0.58f, 0.98f, 0.56f, 0.5f); l(0.15f, 0.5f, 0.85f, 0.5f)
        rect(0.28f, 0.28f, 0.44f, 0.22f); lc(0.24f, 0.28f, 0.5f, 0.08f, 0.76f, 0.28f); l(0.2f, 0.5f, 0.2f, 0.9f); l(0.28f, 0.5f, 0.28f, 0.9f)
        for (i in 0..3) l(0.2f, 0.58f + i * 0.1f, 0.28f, 0.58f + i * 0.1f)
    }
    t("yapi.dukkan", "Dükkân", BUILD, "Yapı", "Tenteli dükkân, vitrin ve kapı") {
        rect(0.08f, 0.3f, 0.84f, 0.65f); path { moveTo(0.05f, 0.3f); for (i in 0..6) { val x0 = 0.05f + i * 0.9f / 7; quadTo(x0 + 0.9f / 14, 0.45f, x0 + 0.9f / 7, 0.3f) } }
        rect(0.14f, 0.5f, 0.4f, 0.28f); rect(0.62f, 0.5f, 0.22f, 0.45f)
    }
    t("yapi.cadir_sirk", "Sirk çadırı", BUILD, "Yapı", "Çizgili konik çatı, bayrak ve giriş") {
        lc(0.1f, 0.45f, 0.5f, 0.12f, 0.9f, 0.45f); rect(0.12f, 0.45f, 0.76f, 0.5f); l(0.5f, 0.12f, 0.5f, 0.02f); lc(0.5f, 0.02f, 0.64f, 0.05f, 0.5f, 0.08f)
        l(0.5f, 0.12f, 0.3f, 0.45f); l(0.5f, 0.12f, 0.7f, 0.45f); lc(0.4f, 0.95f, 0.5f, 0.65f, 0.6f, 0.95f)
    }
    t("yapi.ruzgar_turbini", "Rüzgâr türbini", BUILD, "Yapı", "İnce kule, göbek ve üç kanat") {
        l(0.48f, 0.98f, 0.49f, 0.35f); l(0.52f, 0.98f, 0.51f, 0.35f); circle(0.5f, 0.32f, 0.03f)
        for (i in 0..2) { val a = rad(-90f + 120f * i); l(0.5f, 0.32f, 0.5f + 0.3f * cos(a).toFloat(), 0.32f + 0.3f * sin(a).toFloat()) }
    }
    t("hayal.ejderha", "Ejderha", FANTASY, "Yaratık", "Uzun boyunlu gövde, kanat, kuyruk ve sivri baş") {
        sc(0.25f, 0.6f, 0.45f, 0.5f, 0.65f, 0.55f, 0.68f, 0.72f, 0.3f, 0.75f)
        s(0.62f, 0.52f, 0.72f, 0.35f, 0.8f, 0.2f); lc(0.78f, 0.16f, 0.98f, 0.22f, 0.82f, 0.28f)
        lc(0.42f, 0.52f, 0.3f, 0.12f, 0.45f, 0.25f, 0.55f, 0.15f, 0.56f, 0.52f)
        s(0.25f, 0.65f, 0.1f, 0.72f, 0.04f, 0.6f); lc(0.02f, 0.62f, 0.06f, 0.52f, 0.08f, 0.62f)
        l(0.35f, 0.75f, 0.33f, 0.92f); l(0.58f, 0.74f, 0.6f, 0.92f)
    }
    t("hayal.unicorn", "Tek boynuzlu at", FANTASY, "Yaratık", "At gövdesi, alında sarmal boynuz ve yele") {
        ellipse(0.45f, 0.55f, 0.25f, 0.13f); s(0.64f, 0.48f, 0.72f, 0.3f, 0.76f, 0.2f)
        sc(0.72f, 0.16f, 0.92f, 0.22f, 0.92f, 0.3f, 0.8f, 0.3f); l(0.78f, 0.17f, 0.86f, 0.02f, 0.82f, 0.18f)
        s(0.7f, 0.18f, 0.6f, 0.26f, 0.62f, 0.42f)
        l(0.3f, 0.64f, 0.28f, 0.95f); l(0.38f, 0.66f, 0.4f, 0.95f); l(0.54f, 0.66f, 0.54f, 0.95f); l(0.62f, 0.64f, 0.64f, 0.95f)
        s(0.2f, 0.5f, 0.08f, 0.6f, 0.06f, 0.78f)
    }
    t("hayal.deniz_kizi", "Deniz kızı kuyruğu", FANTASY, "Yaratık", "Pullu kıvrık kuyruk ve yüzgeç") {
        path { moveTo(0.3f, 0.05f); cubicTo(0.25f, 0.4f, 0.45f, 0.55f, 0.55f, 0.72f) }
        path { moveTo(0.55f, 0.05f); cubicTo(0.55f, 0.35f, 0.62f, 0.5f, 0.65f, 0.7f) }
        path(closed = true) { moveTo(0.55f, 0.72f); quadTo(0.4f, 0.9f, 0.3f, 0.95f); quadTo(0.6f, 0.92f, 0.62f, 0.82f); quadTo(0.75f, 0.95f, 0.98f, 0.92f); quadTo(0.8f, 0.8f, 0.65f, 0.7f) }
        arc(0.4f, 0.3f, 0.06f, 0.04f, 0f, 180f); arc(0.5f, 0.38f, 0.06f, 0.04f, 0f, 180f); arc(0.44f, 0.48f, 0.06f, 0.04f, 0f, 180f)
    }
    t("hayal.peri_kanadi", "Peri kanatları", FANTASY, "Yaratık", "Dört ince, damarlı kanat") {
        sc(0.5f, 0.45f, 0.2f, 0.05f, 0.05f, 0.2f, 0.25f, 0.45f); sc(0.5f, 0.45f, 0.8f, 0.05f, 0.95f, 0.2f, 0.75f, 0.45f)
        sc(0.5f, 0.5f, 0.3f, 0.6f, 0.2f, 0.85f, 0.42f, 0.7f); sc(0.5f, 0.5f, 0.7f, 0.6f, 0.8f, 0.85f, 0.58f, 0.7f)
        l(0.5f, 0.45f, 0.18f, 0.18f); l(0.5f, 0.45f, 0.82f, 0.18f)
    }
    t("hayal.asa", "Sihirli değnek", FANTASY, "Büyü", "Çubuk, yıldız uç ve parıltılar") {
        l(0.1f, 0.92f, 0.62f, 0.4f); star(0.7f, 0.32f, 0.16f, 0.07f, 5, -90f)
        l(0.85f, 0.55f, 0.85f, 0.65f); l(0.8f, 0.6f, 0.9f, 0.6f); l(0.45f, 0.15f, 0.45f, 0.23f); l(0.41f, 0.19f, 0.49f, 0.19f)
    }
    t("hayal.iksir", "İksir şişesi", FANTASY, "Büyü", "Yuvarlak şişe, dar boyun, mantar ve kabarcıklar") {
        rect(0.42f, 0.05f, 0.16f, 0.1f); l(0.44f, 0.15f, 0.44f, 0.35f); l(0.56f, 0.15f, 0.56f, 0.35f)
        arc(0.5f, 0.64f, 0.3f, 0.3f, -72f, 324f); s(0.24f, 0.62f, 0.4f, 0.58f, 0.6f, 0.66f, 0.76f, 0.6f); circle(0.42f, 0.74f, 0.03f); circle(0.56f, 0.8f, 0.02f)
    }
    t("hayal.kristal_kure", "Kristal küre", FANTASY, "Büyü", "Küre, ayaklı kaide ve parıltı") {
        circle(0.5f, 0.42f, 0.32f); lc(0.3f, 0.7f, 0.7f, 0.7f, 0.78f, 0.92f, 0.22f, 0.92f); s(0.32f, 0.3f, 0.38f, 0.2f, 0.48f, 0.16f)
    }
    t("hayal.hazine", "Hazine sandığı", FANTASY, "Macera", "Kubbeli kapak, kilit ve metal şeritler") {
        rect(0.1f, 0.45f, 0.8f, 0.45f); arc(0.5f, 0.45f, 0.4f, 0.22f, 180f, 180f)
        l(0.25f, 0.24f, 0.25f, 0.9f); l(0.75f, 0.24f, 0.75f, 0.9f); rect(0.44f, 0.45f, 0.12f, 0.14f)
    }
    t("hayal.harita", "Hazine haritası", FANTASY, "Macera", "Kıvrık uçlu parşömen, yol ve X") {
        path(closed = true) { moveTo(0.12f, 0.15f); lineTo(0.88f, 0.12f); quadTo(0.82f, 0.5f, 0.9f, 0.88f); lineTo(0.12f, 0.9f); quadTo(0.05f, 0.5f, 0.12f, 0.15f) }
        path { moveTo(0.22f, 0.75f); quadTo(0.4f, 0.4f, 0.55f, 0.6f); quadTo(0.65f, 0.75f, 0.72f, 0.35f) }
        l(0.66f, 0.28f, 0.78f, 0.4f); l(0.78f, 0.28f, 0.66f, 0.4f)
    }
    t("hayal.cadi_sapka", "Cadı şapkası", FANTASY, "Büyü", "Geniş kenar, eğik sivri tepe ve bant") {
        ellipse(0.5f, 0.8f, 0.45f, 0.1f); path { moveTo(0.28f, 0.78f); quadTo(0.4f, 0.4f, 0.62f, 0.1f); quadTo(0.6f, 0.4f, 0.72f, 0.78f) }
        l(0.3f, 0.68f, 0.7f, 0.68f)
    }
    t("hayal.kale_burc", "Masal şatosu", FANTASY, "Macera", "Sivri kuleli şato, kapı ve bayraklar") {
        rect(0.25f, 0.45f, 0.5f, 0.5f); rect(0.08f, 0.35f, 0.17f, 0.6f); rect(0.75f, 0.35f, 0.17f, 0.6f)
        lc(0.06f, 0.35f, 0.165f, 0.12f, 0.27f, 0.35f); lc(0.73f, 0.35f, 0.835f, 0.12f, 0.94f, 0.35f)
        lc(0.38f, 0.95f, 0.38f, 0.75f, 0.5f, 0.66f, 0.62f, 0.75f, 0.62f, 0.95f)
        for (i in 0..3) rect(0.27f + i * 0.13f, 0.4f, 0.07f, 0.05f)
    }
    t("hayal.uzayli", "Uzaylı", FANTASY, "Yaratık", "Büyük baş, iri gözler, antenler ve ince gövde") {
        ellipse(0.5f, 0.3f, 0.2f, 0.22f); ellipse(0.42f, 0.32f, 0.06f, 0.08f, 20f); ellipse(0.58f, 0.32f, 0.06f, 0.08f, -20f)
        l(0.42f, 0.1f, 0.36f, 0.02f); l(0.58f, 0.1f, 0.64f, 0.02f); ellipse(0.5f, 0.68f, 0.1f, 0.16f)
        l(0.42f, 0.6f, 0.3f, 0.72f); l(0.58f, 0.6f, 0.7f, 0.72f); l(0.46f, 0.83f, 0.44f, 0.98f); l(0.54f, 0.83f, 0.56f, 0.98f)
    }
    t("hayal.canavar", "Sevimli canavar", FANTASY, "Yaratık", "Tüylü yuvarlak gövde, tek göz, boynuzlar ve dişler") {
        path(closed = true) { moveTo(0.2f, 0.85f); quadTo(0.05f, 0.5f, 0.25f, 0.28f); quadTo(0.5f, 0.12f, 0.75f, 0.28f); quadTo(0.95f, 0.5f, 0.8f, 0.85f) }
        circle(0.5f, 0.42f, 0.1f); l(0.3f, 0.25f, 0.24f, 0.08f, 0.38f, 0.2f); l(0.62f, 0.2f, 0.76f, 0.08f, 0.7f, 0.25f)
        l(0.35f, 0.65f, 0.65f, 0.65f); lc(0.42f, 0.65f, 0.45f, 0.72f, 0.48f, 0.65f); lc(0.52f, 0.65f, 0.55f, 0.72f, 0.58f, 0.65f)
    }
    t("hayal.sovalye_migferi", "Şövalye miğferi", FANTASY, "Macera", "Kubbe miğfer, göz yarığı ve tüy") {
        path(closed = true) { moveTo(0.25f, 0.9f); lineTo(0.25f, 0.4f); quadTo(0.5f, 0.05f, 0.75f, 0.4f); lineTo(0.75f, 0.9f) }
        l(0.3f, 0.45f, 0.7f, 0.45f); l(0.5f, 0.45f, 0.5f, 0.9f); s(0.5f, 0.2f, 0.65f, 0.05f, 0.85f, 0.08f)
    }
    t("hayal.yay_ok", "Yay ve ok", FANTASY, "Macera", "Kavisli yay, gergin kiriş ve ok") {
        path { moveTo(0.3f, 0.05f); quadTo(0.75f, 0.5f, 0.3f, 0.95f) }; l(0.3f, 0.05f, 0.3f, 0.95f)
        l(0.12f, 0.5f, 0.95f, 0.5f); lc(0.95f, 0.5f, 0.86f, 0.45f, 0.86f, 0.55f); l(0.12f, 0.5f, 0.06f, 0.44f); l(0.12f, 0.5f, 0.06f, 0.56f)
    }
}

internal fun TemplateScope.v54ComicSymbolTemplates() {
    t("simge.ampul_fikir", "Fikir ampulü", THING, "Simge", "Ampul ve çevresinde ışık çizgileri") {
        sc(0.5f, 0.15f, 0.7f, 0.25f, 0.68f, 0.5f, 0.58f, 0.62f, 0.42f, 0.62f, 0.32f, 0.5f, 0.3f, 0.25f)
        rect(0.42f, 0.62f, 0.16f, 0.1f); l(0.44f, 0.76f, 0.56f, 0.76f)
        l(0.5f, 0.02f, 0.5f, 0.08f); l(0.15f, 0.3f, 0.22f, 0.32f); l(0.85f, 0.3f, 0.78f, 0.32f); l(0.22f, 0.1f, 0.28f, 0.16f); l(0.78f, 0.1f, 0.72f, 0.16f)
    }
    t("simge.nota_dizisi", "Nota dizisi", MUSIC, "Simge", "Beş çizgi ve üç nota") {
        for (i in 0..4) l(0.02f, 0.3f + i * 0.08f, 0.98f, 0.3f + i * 0.08f)
        ellipse(0.25f, 0.58f, 0.05f, 0.035f, -20f); l(0.3f, 0.57f, 0.3f, 0.2f); ellipse(0.5f, 0.46f, 0.05f, 0.035f, -20f); l(0.55f, 0.45f, 0.55f, 0.1f); ellipse(0.75f, 0.54f, 0.05f, 0.035f, -20f); l(0.8f, 0.53f, 0.8f, 0.18f)
    }
    t("simge.kalpler", "Uçuşan kalpler", THING, "Simge", "Farklı boyda üç kalp") {
        for ((x, y, r) in listOf(Triple(0.3f, 0.6f, 0.18f), Triple(0.65f, 0.35f, 0.13f), Triple(0.78f, 0.75f, 0.09f))) {
            path(closed = true) { moveTo(x, y + r); cubicTo(x - r * 1.6f, y - r * 0.2f, x - r * 0.6f, y - r * 1.2f, x, y - r * 0.35f); cubicTo(x + r * 0.6f, y - r * 1.2f, x + r * 1.6f, y - r * 0.2f, x, y + r) }
        }
    }
    t("simge.yildirim_bulut", "Öfke bulutu", THING, "Simge", "Karalanmış koyu bulut ve şimşek") {
        path(closed = true) { moveTo(0.15f, 0.45f); quadTo(0.05f, 0.25f, 0.25f, 0.22f); quadTo(0.35f, 0.05f, 0.55f, 0.14f); quadTo(0.75f, 0.05f, 0.82f, 0.25f); quadTo(1f, 0.3f, 0.85f, 0.45f) }
        l(0.55f, 0.45f, 0.42f, 0.68f, 0.55f, 0.68f, 0.44f, 0.95f)
    }
    t("simge.konusma_cift", "Karşılıklı konuşma", THING, "Simge", "İki konuşma balonu, kuyrukları karşılıklı") {
        roundRect(0.05f, 0.08f, 0.5f, 0.32f, 0.08f); l(0.15f, 0.4f, 0.12f, 0.52f, 0.25f, 0.4f)
        roundRect(0.45f, 0.5f, 0.5f, 0.32f, 0.08f); l(0.75f, 0.82f, 0.88f, 0.94f, 0.85f, 0.82f)
    }
    t("simge.sicak_soguk", "Termometre", THING, "Simge", "Ölçekli tüp ve hazne") {
        roundRect(0.44f, 0.05f, 0.12f, 0.68f, 0.06f); circle(0.5f, 0.8f, 0.12f); for (i in 0..4) l(0.56f, 0.15f + i * 0.12f, 0.64f, 0.15f + i * 0.12f)
    }
    t("simge.alarm", "Alarm (çalan saat)", THING, "Simge", "Zilli saat ve titreşim çizgileri") {
        circle(0.5f, 0.55f, 0.28f); arc(0.25f, 0.25f, 0.12f, 0.1f, 180f, 180f); arc(0.75f, 0.25f, 0.12f, 0.1f, 180f, 180f)
        l(0.5f, 0.55f, 0.5f, 0.38f); l(0.5f, 0.55f, 0.62f, 0.6f); l(0.08f, 0.45f, 0.14f, 0.5f); l(0.92f, 0.45f, 0.86f, 0.5f)
    }
    t("simge.para", "Para kesesi", THING, "Simge", "Bağlı kese ve para simgesi") {
        path(closed = true) { moveTo(0.4f, 0.25f); quadTo(0.05f, 0.5f, 0.2f, 0.85f); lineTo(0.8f, 0.85f); quadTo(0.95f, 0.5f, 0.6f, 0.25f) }
        lc(0.4f, 0.25f, 0.32f, 0.1f, 0.68f, 0.1f, 0.6f, 0.25f); s(0.58f, 0.48f, 0.45f, 0.45f, 0.45f, 0.58f, 0.58f, 0.62f, 0.55f, 0.75f, 0.42f, 0.72f); l(0.5f, 0.42f, 0.5f, 0.8f)
    }
    t("simge.gozler_parlayan", "Parlayan gözler", THING, "Simge", "İki iri göz ve parıltı noktaları") {
        ellipse(0.3f, 0.5f, 0.16f, 0.22f); ellipse(0.7f, 0.5f, 0.16f, 0.22f); circle(0.34f, 0.42f, 0.04f); circle(0.74f, 0.42f, 0.04f); circle(0.26f, 0.58f, 0.02f); circle(0.66f, 0.58f, 0.02f)
    }
    t("simge.patlama_kucuk", "Darbe yıldızı", THING, "Simge", "Düzensiz sivri uçlu darbe biçimi") {
        star(0.5f, 0.5f, 0.45f, 0.24f, 9, -90f); star(0.5f, 0.5f, 0.2f, 0.1f, 6, -60f)
    }
}
