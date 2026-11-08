package com.karapp.drawguidance.content.shapes

import kotlin.math.cos
import kotlin.math.sin

private const val SYMBOL = "Simge"
private const val FOOD = "Yiyecek ve mutfak"
private const val ANIMAL2 = "Hayvan"
private const val NATURE2 = "Gökyüzü ve doğa"
private const val PLANT2 = "Bitki"
private const val VEHICLE2 = "Taşıt"
private const val TECH = "Teknoloji"
private const val TOOL = "Alet"
private const val MUSIC = "Müzik"
private const val SPORT = "Spor ve oyun"
private const val CLOTH = "Giyim"
private const val THING = "Nesne"
private const val BUILD = "Yapı"
private const val FANTASY = "Hayal"

/**
 * v23 en çok çizilen şekiller (çizim oyunlarında ve okul çizimlerinde sık görülen nesneler): simgeler, yiyecekler,
 * hayvanlar, taşıtlar, eşyalar, aletler, müzik aletleri, spor, giyim ve hayali varlıklar. Her biri elle çizilmiş gibi
 * sade çizgilerden oluşur.
 */
internal fun TemplateScope.commonTemplates() {
    // ------------------------------------------------------------------ simgeler
    t("simge.tik", "Onay işareti", SYMBOL, "İşaret", "Kısa sol kol ve uzun sağ kol (✓)") { l(0f, 0.55f, 0.35f, 0.9f, 1f, 0.08f) }
    t("simge.carpi", "Çarpı (X)", SYMBOL, "İşaret", "Çapraz iki çizgi") { l(0f, 0f, 1f, 1f); l(1f, 0f, 0f, 1f) }
    t("simge.ok_sag", "Ok (sağa)", SYMBOL, "Ok", "Yatay gövde ve sivri uç") { l(0f, 0.5f, 1f, 0.5f); l(0.7f, 0.22f, 1f, 0.5f, 0.7f, 0.78f) }
    t("simge.ok_yukari", "Ok (yukarı)", SYMBOL, "Ok", "Dikey gövde ve yukarı bakan uç") { l(0.5f, 1f, 0.5f, 0f); l(0.22f, 0.3f, 0.5f, 0f, 0.78f, 0.3f) }
    t("simge.ok_cift", "Çift yönlü ok", SYMBOL, "Ok", "İki ucu sivri yatay ok") {
        l(0f, 0.5f, 1f, 0.5f); l(0.25f, 0.25f, 0f, 0.5f, 0.25f, 0.75f); l(0.75f, 0.25f, 1f, 0.5f, 0.75f, 0.75f)
    }
    t("simge.ok_kivrik", "Kıvrık ok", SYMBOL, "Ok", "Yay biçiminde dönen ok") {
        s(0f, 0.9f, 0.15f, 0.35f, 0.55f, 0.12f, 0.95f, 0.4f); l(0.68f, 0.42f, 0.95f, 0.4f, 0.9f, 0.12f)
    }
    t("simge.ok_blok", "Kalın ok", SYMBOL, "Ok", "İçi boş blok ok") { lc(0f, 0.35f, 0.6f, 0.35f, 0.6f, 0.08f, 1f, 0.5f, 0.6f, 0.92f, 0.6f, 0.65f, 0f, 0.65f) }
    t("simge.arti", "Artı", SYMBOL, "İşaret", "Dik kesişen iki çizgi") { l(0.5f, 0f, 0.5f, 1f); l(0f, 0.5f, 1f, 0.5f) }
    t("simge.nota", "Müzik notası", MUSIC, "Nota", "Yuvarlak nota başı, sap ve bayrak") {
        ellipse(0.3f, 0.85f, 0.22f, 0.15f, -20f); l(0.5f, 0.8f, 0.5f, 0f); s(0.5f, 0f, 0.72f, 0.15f, 0.9f, 0.38f)
    }
    t("simge.cift_nota", "Çift nota", MUSIC, "Nota", "Kirişle bağlı iki nota") {
        ellipse(0.2f, 0.86f, 0.17f, 0.12f, -20f); ellipse(0.76f, 0.76f, 0.17f, 0.12f, -20f)
        l(0.35f, 0.82f, 0.35f, 0.12f, 0.91f, 0.02f, 0.91f, 0.72f)
    }
    t("simge.sonsuz", "Sonsuzluk", SYMBOL, "İşaret", "Yatay sekiz (∞)") {
        sc(0.5f, 0.5f, 0.27f, 0.2f, 0.03f, 0.5f, 0.27f, 0.8f, 0.5f, 0.5f, 0.73f, 0.2f, 0.97f, 0.5f, 0.73f, 0.8f)
    }
    t("simge.baris", "Barış işareti", SYMBOL, "İşaret", "Daire, dikey çizgi ve iki eğik kol") {
        circle(0.5f, 0.5f, 0.48f); l(0.5f, 0.02f, 0.5f, 0.98f); l(0.16f, 0.84f, 0.5f, 0.5f, 0.84f, 0.84f)
    }
    t("simge.yin_yang", "Yin yang", SYMBOL, "İşaret", "Daire, S biçimli ayırma ve iki nokta") {
        circle(0.5f, 0.5f, 0.48f); s(0.5f, 0.02f, 0.27f, 0.26f, 0.5f, 0.5f, 0.73f, 0.74f, 0.5f, 0.98f); circle(0.5f, 0.26f, 0.06f); circle(0.5f, 0.74f, 0.06f)
    }
    t("simge.geri_donusum", "Geri dönüşüm", SYMBOL, "İşaret", "Üçgen üzerinde dönen üç ok") {
        l(0.55f, 0.08f, 0.92f, 0.7f); l(0.8f, 0.65f, 0.92f, 0.7f, 0.95f, 0.57f)
        l(0.82f, 0.92f, 0.15f, 0.92f); l(0.27f, 0.82f, 0.15f, 0.92f, 0.27f, 1f)
        l(0.08f, 0.72f, 0.45f, 0.08f); l(0.33f, 0.12f, 0.45f, 0.08f, 0.48f, 0.21f)
    }
    t("simge.wifi", "Kablosuz (Wi-Fi)", TECH, "Simge", "İç içe üç yay ve nokta") {
        arc(0.5f, 0.92f, 0.16f, 0.16f, 225f, 90f); arc(0.5f, 0.92f, 0.34f, 0.34f, 225f, 90f); arc(0.5f, 0.92f, 0.52f, 0.52f, 225f, 90f); circle(0.5f, 0.92f, 0.05f)
    }
    t("simge.pil", "Pil", TECH, "Simge", "Dikdörtgen gövde, kutup başı ve dolu çizgiler") {
        rect(0f, 0.28f, 0.86f, 0.44f); rect(0.86f, 0.4f, 0.12f, 0.2f); l(0.14f, 0.38f, 0.14f, 0.62f); l(0.28f, 0.38f, 0.28f, 0.62f)
    }
    t("simge.kum_saati", "Kum saati", THING, "Ev eşyası", "Ortada daralan iki üçgen hazne") {
        l(0.1f, 0.02f, 0.9f, 0.02f); l(0.1f, 0.98f, 0.9f, 0.98f); lc(0.2f, 0.02f, 0.8f, 0.02f, 0.52f, 0.5f, 0.8f, 0.98f, 0.2f, 0.98f, 0.48f, 0.5f)
    }
    t("simge.buyutec", "Büyüteç", THING, "Alet", "Yuvarlak cam ve çapraz sap") { circle(0.4f, 0.4f, 0.36f); l(0.66f, 0.66f, 1f, 1f) }
    t("simge.konum", "Konum iğnesi", SYMBOL, "İşaret", "Ters damla ve içindeki daire") {
        path(closed = true) { moveTo(0.5f, 1f); quadTo(0.08f, 0.58f, 0.1f, 0.38f); cubicTo(0.12f, -0.1f, 0.88f, -0.1f, 0.9f, 0.38f); quadTo(0.92f, 0.58f, 0.5f, 1f) }
        circle(0.5f, 0.37f, 0.14f)
    }
    t("simge.hedef", "Hedef tahtası", SPORT, "Oyun", "İç içe üç daire ve merkez") {
        circle(0.5f, 0.5f, 0.48f); circle(0.5f, 0.5f, 0.32f); circle(0.5f, 0.5f, 0.16f); circle(0.5f, 0.5f, 0.04f)
    }
    t("simge.dusunce", "Düşünce balonu", SYMBOL, "Balon", "Bulut biçimli balon ve küçülen iki daire") {
        sc(0.2f, 0.45f, 0.18f, 0.18f, 0.42f, 0.05f, 0.68f, 0.08f, 0.88f, 0.25f, 0.88f, 0.5f, 0.65f, 0.65f, 0.38f, 0.66f)
        circle(0.2f, 0.8f, 0.06f); circle(0.1f, 0.94f, 0.04f)
    }
    t("simge.parilti", "Parıltı", SYMBOL, "İşaret", "İçe kavisli dört uçlu yıldız") {
        path(closed = true) { moveTo(0.5f, 0f); quadTo(0.56f, 0.44f, 1f, 0.5f); quadTo(0.56f, 0.56f, 0.5f, 1f); quadTo(0.44f, 0.56f, 0f, 0.5f); quadTo(0.44f, 0.44f, 0.5f, 0f) }
    }
    t("simge.kalp_kirik", "Kırık kalp", SYMBOL, "İşaret", "Ortasından zikzakla ayrılmış kalp") {
        path(closed = true) {
            moveTo(0.5f, 0.25f); cubicTo(0.5f, -0.05f, 1.05f, -0.05f, 1f, 0.35f); cubicTo(0.95f, 0.65f, 0.6f, 0.8f, 0.5f, 1f)
            cubicTo(0.4f, 0.8f, 0.05f, 0.65f, 0f, 0.35f); cubicTo(-0.05f, -0.05f, 0.5f, -0.05f, 0.5f, 0.25f)
        }
        l(0.5f, 0.25f, 0.42f, 0.45f, 0.56f, 0.6f, 0.45f, 0.78f, 0.5f, 1f)
    }

    // ------------------------------------------------------------------ yiyecek ve mutfak
    t("yiyecek.pasta", "Doğum günü pastası", FOOD, "Tatlı", "Katlı pasta ve üç mum") {
        rect(0.08f, 0.5f, 0.84f, 0.46f); s(0.08f, 0.66f, 0.29f, 0.72f, 0.5f, 0.66f, 0.71f, 0.72f, 0.92f, 0.66f)
        for (x in listOf(0.3f, 0.5f, 0.7f)) { l(x, 0.5f, x, 0.3f); sc(x, 0.12f, x + 0.05f, 0.24f, x, 0.28f, x - 0.05f, 0.24f) }
    }
    t("yiyecek.kek", "Kek (cupcake)", FOOD, "Tatlı", "Çizgili kalıp, kabarık krema ve kiraz") {
        lc(0.18f, 0.55f, 0.82f, 0.55f, 0.72f, 1f, 0.28f, 1f); l(0.4f, 0.55f, 0.42f, 1f); l(0.6f, 0.55f, 0.58f, 1f)
        sc(0.12f, 0.55f, 0.18f, 0.3f, 0.5f, 0.16f, 0.82f, 0.3f, 0.88f, 0.55f); circle(0.5f, 0.1f, 0.07f)
    }
    t("yiyecek.dondurma", "Dondurma külahı", FOOD, "Tatlı", "Ters üçgen külah, kafes çizgileri ve top") {
        lc(0.25f, 0.48f, 0.75f, 0.48f, 0.5f, 1f); circle(0.5f, 0.3f, 0.28f); l(0.33f, 0.55f, 0.6f, 0.82f); l(0.67f, 0.55f, 0.4f, 0.82f)
    }
    t("yiyecek.pizza", "Pizza dilimi", FOOD, "Yemek", "Üçgen dilim, kenar ve sucuklar") {
        lc(0.5f, 1f, 0.02f, 0.12f, 0.98f, 0.12f); s(0.02f, 0.12f, 0.5f, 0f, 0.98f, 0.12f); circle(0.42f, 0.34f, 0.08f); circle(0.6f, 0.52f, 0.07f); circle(0.48f, 0.72f, 0.06f)
    }
    t("yiyecek.hamburger", "Hamburger", FOOD, "Yemek", "Üst ekmek, marul, köfte ve alt ekmek") {
        path(closed = true) { moveTo(0.05f, 0.42f); cubicTo(0.05f, 0f, 0.95f, 0f, 0.95f, 0.42f); close() }
        s(0.02f, 0.5f, 0.15f, 0.44f, 0.3f, 0.52f, 0.45f, 0.44f, 0.6f, 0.52f, 0.75f, 0.44f, 0.9f, 0.52f, 0.98f, 0.47f)
        roundRect(0.05f, 0.55f, 0.9f, 0.14f, 0.06f); roundRect(0.05f, 0.76f, 0.9f, 0.2f, 0.08f)
    }
    t("yiyecek.donut", "Halka tatlı (donut)", FOOD, "Tatlı", "Dış daire ve ortadaki delik") { circle(0.5f, 0.5f, 0.48f); circle(0.5f, 0.5f, 0.16f) }
    t("yiyecek.kurabiye", "Kurabiye", FOOD, "Tatlı", "Yuvarlak kurabiye ve damla çikolatalar") {
        circle(0.5f, 0.5f, 0.47f); circle(0.32f, 0.3f, 0.05f); circle(0.62f, 0.28f, 0.05f); circle(0.42f, 0.58f, 0.05f); circle(0.7f, 0.62f, 0.05f); circle(0.3f, 0.75f, 0.05f)
    }
    t("yiyecek.ekmek", "Ekmek", FOOD, "Yemek", "Kubbeli somun ve üstündeki çizikler") {
        path(closed = true) { moveTo(0f, 0.62f); cubicTo(0f, 0.08f, 1f, 0.08f, 1f, 0.62f); lineTo(1f, 0.95f); lineTo(0f, 0.95f); close() }
        l(0.28f, 0.32f, 0.38f, 0.52f); l(0.48f, 0.27f, 0.58f, 0.47f); l(0.68f, 0.32f, 0.78f, 0.52f)
    }
    t("yiyecek.lolipop", "Lolipop", FOOD, "Tatlı", "Sarmal desenli yuvarlak şeker ve çubuk") {
        circle(0.5f, 0.32f, 0.3f); s(0.5f, 0.32f, 0.58f, 0.26f, 0.6f, 0.38f, 0.45f, 0.44f, 0.36f, 0.3f, 0.5f, 0.16f, 0.72f, 0.24f, 0.74f, 0.45f)
        l(0.5f, 0.62f, 0.5f, 1f)
    }
    t("yiyecek.bardak_pipet", "Pipetli bardak", FOOD, "Mutfak", "Aşağı daralan bardak ve eğik pipet") { l(0.18f, 0.18f, 0.3f, 1f, 0.7f, 1f, 0.82f, 0.18f); l(0.52f, 0.5f, 0.76f, 0f) }
    t("yiyecek.kadeh", "Kadeh", FOOD, "Mutfak", "Kase biçimli hazne, ince sap ve taban") {
        path { moveTo(0.2f, 0f); quadTo(0.2f, 0.48f, 0.5f, 0.5f); quadTo(0.8f, 0.48f, 0.8f, 0f) }; l(0.2f, 0f, 0.8f, 0f)
        l(0.5f, 0.5f, 0.5f, 0.92f); ellipse(0.5f, 0.94f, 0.22f, 0.05f)
    }
    t("yiyecek.caydanlik", "Çaydanlık", FOOD, "Mutfak", "Yuvarlak gövde, emzik, kulp ve kapak") {
        path(closed = true) { moveTo(0.2f, 0.95f); cubicTo(0.02f, 0.9f, 0.05f, 0.35f, 0.5f, 0.32f); cubicTo(0.95f, 0.35f, 0.98f, 0.9f, 0.8f, 0.95f); close() }
        s(0.18f, 0.6f, 0.05f, 0.5f, 0f, 0.38f); arc(0.85f, 0.6f, 0.13f, 0.18f, -80f, 160f); ellipse(0.5f, 0.3f, 0.16f, 0.05f); circle(0.5f, 0.2f, 0.05f)
    }
    t("yiyecek.catal", "Çatal", FOOD, "Mutfak", "Dört dişli çatal ve sap") {
        l(0.5f, 0.42f, 0.5f, 1f); l(0.33f, 0f, 0.33f, 0.3f); l(0.45f, 0f, 0.45f, 0.3f); l(0.55f, 0f, 0.55f, 0.3f); l(0.67f, 0f, 0.67f, 0.3f)
        path { moveTo(0.33f, 0.3f); quadTo(0.33f, 0.42f, 0.5f, 0.42f); quadTo(0.67f, 0.42f, 0.67f, 0.3f) }
    }
    t("yiyecek.bicak", "Bıçak", FOOD, "Mutfak", "Kavisli ağız ve sap") {
        path(closed = true) { moveTo(0.44f, 0f); quadTo(0.66f, 0.2f, 0.6f, 0.56f); lineTo(0.44f, 0.56f); close() }; roundRect(0.41f, 0.56f, 0.18f, 0.44f, 0.06f)
    }
    t("yiyecek.kasik", "Kaşık", FOOD, "Mutfak", "Oval kaşık başı ve sap") { ellipse(0.5f, 0.18f, 0.17f, 0.18f); l(0.5f, 0.36f, 0.5f, 1f) }
    t("yiyecek.tava", "Tava", FOOD, "Mutfak", "Yuvarlak tava ve uzun sap") { circle(0.34f, 0.5f, 0.33f); rect(0.67f, 0.45f, 0.33f, 0.1f) }
    t("yiyecek.peynir", "Peynir dilimi", FOOD, "Yemek", "Üçgen kama ve delikler") {
        lc(0f, 0.42f, 1f, 0.12f, 1f, 0.78f, 0f, 0.78f); l(0f, 0.42f, 1f, 0.42f); circle(0.3f, 0.6f, 0.06f); circle(0.65f, 0.6f, 0.08f); circle(0.85f, 0.3f, 0.04f)
    }
    t("yiyecek.yumurta", "Yumurta", FOOD, "Yemek", "Tepesi dar oval") { sc(0.5f, 0f, 0.86f, 0.45f, 0.76f, 0.92f, 0.5f, 1f, 0.24f, 0.92f, 0.14f, 0.45f) }
    t("yiyecek.sahanda", "Sahanda yumurta", FOOD, "Yemek", "Dalgalı beyaz ve yuvarlak sarı") {
        sc(0.1f, 0.45f, 0.25f, 0.12f, 0.6f, 0.05f, 0.92f, 0.3f, 0.88f, 0.7f, 0.55f, 0.95f, 0.2f, 0.82f); circle(0.48f, 0.48f, 0.17f)
    }
    t("yiyecek.sosisli", "Sosisli sandviç", FOOD, "Yemek", "Uzun ekmek, sosis ve hardal çizgisi") {
        roundRect(0.02f, 0.4f, 0.96f, 0.45f, 0.2f); roundRect(0f, 0.3f, 1f, 0.22f, 0.11f)
        s(0.12f, 0.41f, 0.25f, 0.34f, 0.38f, 0.44f, 0.5f, 0.34f, 0.62f, 0.44f, 0.75f, 0.34f, 0.88f, 0.41f)
    }

    // ------------------------------------------------------------------ hayvanlar
    t("hayvan.kedi", "Kedi (oturan)", ANIMAL2, "Bütün hayvan", "Kulaklı baş, armut gövde ve kıvrık kuyruk") {
        path(closed = true) { moveTo(0.2f, 0.12f); lineTo(0.27f, 0f); lineTo(0.36f, 0.1f); quadTo(0.45f, 0.07f, 0.54f, 0.1f); lineTo(0.63f, 0f); lineTo(0.7f, 0.12f); cubicTo(0.8f, 0.3f, 0.6f, 0.42f, 0.45f, 0.42f); cubicTo(0.3f, 0.42f, 0.1f, 0.3f, 0.2f, 0.12f) }
        sc(0.3f, 0.4f, 0.15f, 0.7f, 0.2f, 1f, 0.7f, 1f, 0.75f, 0.7f, 0.6f, 0.4f); s(0.72f, 0.95f, 0.95f, 0.85f, 0.95f, 0.6f, 0.85f, 0.5f)
    }
    t("hayvan.kopek", "Köpek (yandan)", ANIMAL2, "Bütün hayvan", "Gövde, baş, sarkık kulak, dört bacak ve kuyruk") {
        roundRect(0.18f, 0.35f, 0.6f, 0.3f, 0.12f); sc(0.7f, 0.3f, 0.78f, 0.1f, 0.95f, 0.15f, 1f, 0.3f, 0.88f, 0.38f)
        sc(0.76f, 0.13f, 0.7f, 0.25f, 0.74f, 0.38f, 0.8f, 0.24f)
        l(0.25f, 0.62f, 0.25f, 0.95f); l(0.38f, 0.64f, 0.38f, 0.95f); l(0.58f, 0.64f, 0.58f, 0.95f); l(0.7f, 0.62f, 0.7f, 0.95f); s(0.18f, 0.42f, 0.08f, 0.32f, 0.04f, 0.2f)
    }
    t("hayvan.domuz_yuz", "Domuz yüzü", ANIMAL2, "Hayvan yüzü", "Yuvarlak baş, burun ve iki delik, üçgen kulaklar") {
        circle(0.5f, 0.55f, 0.42f); ellipse(0.5f, 0.65f, 0.18f, 0.12f); circle(0.44f, 0.65f, 0.03f); circle(0.56f, 0.65f, 0.03f)
        lc(0.18f, 0.28f, 0.15f, 0.02f, 0.38f, 0.16f); lc(0.82f, 0.28f, 0.85f, 0.02f, 0.62f, 0.16f); circle(0.36f, 0.45f, 0.03f); circle(0.64f, 0.45f, 0.03f)
    }
    t("hayvan.inek_yuz", "İnek yüzü", ANIMAL2, "Hayvan yüzü", "Uzun yüz, geniş burun, boynuzlar ve kulaklar") {
        sc(0.5f, 0.1f, 0.78f, 0.25f, 0.75f, 0.6f, 0.5f, 0.62f, 0.25f, 0.6f, 0.22f, 0.25f); ellipse(0.5f, 0.75f, 0.28f, 0.2f)
        circle(0.42f, 0.76f, 0.04f); circle(0.58f, 0.76f, 0.04f); s(0.3f, 0.15f, 0.2f, 0.05f, 0.22f, 0f); s(0.7f, 0.15f, 0.8f, 0.05f, 0.78f, 0f)
        ellipse(0.12f, 0.3f, 0.12f, 0.06f, 15f); ellipse(0.88f, 0.3f, 0.12f, 0.06f, -15f)
    }
    t("hayvan.aslan_yuz", "Aslan yüzü", ANIMAL2, "Hayvan yüzü", "Zikzak yele ve yuvarlak yüz") {
        val pts = (0..24).flatMap { k -> val a = Math.toRadians(15.0 * k); val r = if (k % 2 == 0) 0.5 else 0.38; listOf((0.5 + r * cos(a)).toFloat(), (0.5 + r * sin(a)).toFloat()) }
        poly(*pts.toFloatArray()); circle(0.5f, 0.52f, 0.3f); l(0.44f, 0.6f, 0.5f, 0.66f, 0.56f, 0.6f); circle(0.4f, 0.44f, 0.03f); circle(0.6f, 0.44f, 0.03f)
    }
    t("hayvan.panda_yuz", "Panda yüzü", ANIMAL2, "Hayvan yüzü", "Yuvarlak baş, iki kulak ve göz lekeleri") {
        circle(0.5f, 0.55f, 0.42f); circle(0.18f, 0.18f, 0.13f); circle(0.82f, 0.18f, 0.13f)
        ellipse(0.35f, 0.5f, 0.1f, 0.13f, 30f); ellipse(0.65f, 0.5f, 0.1f, 0.13f, -30f); ellipse(0.5f, 0.68f, 0.07f, 0.05f)
    }
    t("hayvan.baykus", "Baykuş", ANIMAL2, "Bütün hayvan", "Oval gövde, iri iki göz, gaga ve kulak tüyleri") {
        path(closed = true) { moveTo(0.15f, 0.05f); lineTo(0.3f, 0.18f); quadTo(0.5f, 0.12f, 0.7f, 0.18f); lineTo(0.85f, 0.05f); cubicTo(0.98f, 0.5f, 0.9f, 1f, 0.5f, 1f); cubicTo(0.1f, 1f, 0.02f, 0.5f, 0.15f, 0.05f) }
        circle(0.35f, 0.38f, 0.13f); circle(0.65f, 0.38f, 0.13f); circle(0.35f, 0.38f, 0.04f); circle(0.65f, 0.38f, 0.04f); lc(0.45f, 0.5f, 0.55f, 0.5f, 0.5f, 0.6f)
    }
    t("hayvan.penguen", "Penguen", ANIMAL2, "Bütün hayvan", "Uzun oval gövde, beyaz göğüs, gaga ve kanatlar") {
        sc(0.5f, 0f, 0.82f, 0.25f, 0.85f, 0.75f, 0.5f, 1f, 0.15f, 0.75f, 0.18f, 0.25f); sc(0.5f, 0.3f, 0.7f, 0.5f, 0.68f, 0.85f, 0.5f, 0.95f, 0.32f, 0.85f, 0.3f, 0.5f)
        lc(0.45f, 0.2f, 0.55f, 0.2f, 0.5f, 0.27f); l(0.15f, 0.4f, 0.02f, 0.65f); l(0.85f, 0.4f, 0.98f, 0.65f)
    }
    t("hayvan.kurbaga", "Kurbağa", ANIMAL2, "Hayvan yüzü", "Geniş baş, tepede iki iri göz ve gülümseme") {
        sc(0.05f, 0.6f, 0.2f, 0.32f, 0.5f, 0.3f, 0.8f, 0.32f, 0.95f, 0.6f, 0.8f, 0.92f, 0.5f, 0.95f, 0.2f, 0.92f)
        circle(0.28f, 0.25f, 0.15f); circle(0.72f, 0.25f, 0.15f); circle(0.28f, 0.25f, 0.05f); circle(0.72f, 0.25f, 0.05f); s(0.25f, 0.7f, 0.5f, 0.8f, 0.75f, 0.7f)
    }
    t("hayvan.ahtapot", "Ahtapot", ANIMAL2, "Bütün hayvan", "Kubbe baş ve dalgalı kollar") {
        path { moveTo(0.15f, 0.55f); cubicTo(0.1f, -0.05f, 0.9f, -0.05f, 0.85f, 0.55f) }
        for (i in 0 until 5) { val x = 0.15f + i * 0.175f; s(x, 0.55f, x - 0.05f, 0.75f, x + 0.05f, 0.88f, x - 0.02f, 1f) }
        circle(0.38f, 0.35f, 0.04f); circle(0.62f, 0.35f, 0.04f)
    }
    t("hayvan.yengec", "Yengeç", ANIMAL2, "Bütün hayvan", "Oval gövde, iki kıskaç ve bacaklar") {
        ellipse(0.5f, 0.6f, 0.3f, 0.2f); circle(0.12f, 0.2f, 0.1f); circle(0.88f, 0.2f, 0.1f); l(0.25f, 0.48f, 0.15f, 0.3f); l(0.75f, 0.48f, 0.85f, 0.3f)
        l(0.22f, 0.65f, 0.02f, 0.72f); l(0.24f, 0.72f, 0.06f, 0.9f); l(0.78f, 0.65f, 0.98f, 0.72f); l(0.76f, 0.72f, 0.94f, 0.9f)
    }
    t("hayvan.balina", "Balina", ANIMAL2, "Bütün hayvan", "İri yuvarlak gövde, kuyruk ve su fıskiyesi") {
        path(closed = true) { moveTo(0f, 0.6f); cubicTo(0f, 0.25f, 0.6f, 0.22f, 0.75f, 0.55f); lineTo(0.98f, 0.35f); lineTo(0.95f, 0.75f); lineTo(0.78f, 0.68f); cubicTo(0.6f, 0.95f, 0f, 0.95f, 0f, 0.6f) }
        s(0.3f, 0.3f, 0.28f, 0.12f, 0.2f, 0.05f); s(0.3f, 0.3f, 0.34f, 0.1f, 0.42f, 0.04f); circle(0.18f, 0.55f, 0.03f)
    }
    t("hayvan.kopekbaligi", "Köpekbalığı", ANIMAL2, "Bütün hayvan", "Sivri burun, sırt yüzgeci ve çatal kuyruk") {
        path(closed = true) { moveTo(0f, 0.55f); quadTo(0.3f, 0.3f, 0.75f, 0.5f); lineTo(1f, 0.25f); lineTo(0.92f, 0.6f); lineTo(1f, 0.85f); lineTo(0.75f, 0.62f); quadTo(0.3f, 0.8f, 0f, 0.55f) }
        lc(0.35f, 0.4f, 0.45f, 0.1f, 0.55f, 0.43f); circle(0.15f, 0.5f, 0.025f)
    }
    t("hayvan.yunus", "Yunus", ANIMAL2, "Bütün hayvan", "Kavisli gövde, gaga burun ve kuyruk") {
        path(closed = true) { moveTo(0f, 0.55f); lineTo(0.12f, 0.48f); cubicTo(0.3f, 0.2f, 0.65f, 0.25f, 0.8f, 0.55f); lineTo(1f, 0.45f); lineTo(0.92f, 0.7f); lineTo(1f, 0.9f); lineTo(0.78f, 0.7f); cubicTo(0.6f, 0.75f, 0.3f, 0.75f, 0.12f, 0.6f); close() }
        lc(0.42f, 0.32f, 0.5f, 0.12f, 0.55f, 0.33f); circle(0.2f, 0.5f, 0.025f)
    }
    t("hayvan.fil", "Fil", ANIMAL2, "Bütün hayvan", "Büyük gövde, kulak, uzun hortum ve dört bacak") {
        roundRect(0.25f, 0.25f, 0.6f, 0.45f, 0.18f); sc(0.08f, 0.2f, 0.3f, 0.12f, 0.35f, 0.45f, 0.15f, 0.5f)
        s(0.1f, 0.4f, 0.05f, 0.65f, 0.08f, 0.85f, 0.02f, 0.95f); l(0.32f, 0.68f, 0.32f, 1f); l(0.45f, 0.7f, 0.45f, 1f); l(0.65f, 0.7f, 0.65f, 1f); l(0.78f, 0.68f, 0.78f, 1f)
        circle(0.18f, 0.28f, 0.02f)
    }
    t("hayvan.zurafa", "Zürafa", ANIMAL2, "Bütün hayvan", "Uzun boyun, küçük baş, gövde ve benekler") {
        lc(0.55f, 0.55f, 0.95f, 0.55f, 0.95f, 0.75f, 0.55f, 0.75f); l(0.6f, 0.55f, 0.25f, 0.12f); l(0.68f, 0.55f, 0.33f, 0.1f)
        sc(0.12f, 0.08f, 0.28f, 0f, 0.38f, 0.1f, 0.2f, 0.18f); l(0.6f, 0.75f, 0.6f, 1f); l(0.9f, 0.75f, 0.9f, 1f); circle(0.75f, 0.65f, 0.04f); circle(0.45f, 0.35f, 0.03f)
    }
    t("hayvan.koyun", "Koyun", ANIMAL2, "Bütün hayvan", "Bulut gibi yün gövde, baş ve bacaklar") {
        sc(0.25f, 0.3f, 0.45f, 0.15f, 0.7f, 0.2f, 0.9f, 0.35f, 0.88f, 0.62f, 0.65f, 0.75f, 0.4f, 0.75f, 0.2f, 0.62f)
        ellipse(0.12f, 0.42f, 0.1f, 0.13f); l(0.35f, 0.74f, 0.35f, 1f); l(0.7f, 0.74f, 0.7f, 1f)
    }
    t("hayvan.civciv", "Civciv", ANIMAL2, "Bütün hayvan", "Yuvarlak gövde, gaga, kanat ve ayaklar") {
        circle(0.5f, 0.5f, 0.4f); lc(0.15f, 0.4f, 0f, 0.45f, 0.15f, 0.5f); s(0.45f, 0.5f, 0.6f, 0.62f, 0.75f, 0.52f); circle(0.32f, 0.35f, 0.03f)
        l(0.4f, 0.9f, 0.4f, 1f); l(0.6f, 0.9f, 0.6f, 1f)
    }
    t("hayvan.ugur_bocegi", "Uğur böceği", ANIMAL2, "Böcek", "Yuvarlak kabuk, orta çizgi, baş ve benekler") {
        circle(0.5f, 0.58f, 0.4f); arc(0.5f, 0.2f, 0.18f, 0.14f, 180f, 180f); l(0.5f, 0.18f, 0.5f, 0.98f)
        circle(0.35f, 0.45f, 0.06f); circle(0.65f, 0.45f, 0.06f); circle(0.32f, 0.72f, 0.06f); circle(0.68f, 0.72f, 0.06f)
    }
    t("hayvan.karinca", "Karınca", ANIMAL2, "Böcek", "Üç parçalı gövde, bacaklar ve antenler") {
        circle(0.15f, 0.5f, 0.12f); ellipse(0.42f, 0.5f, 0.12f, 0.09f); ellipse(0.75f, 0.5f, 0.22f, 0.14f)
        l(0.4f, 0.55f, 0.3f, 0.85f); l(0.44f, 0.56f, 0.46f, 0.88f); l(0.48f, 0.55f, 0.62f, 0.85f); s(0.1f, 0.4f, 0.05f, 0.2f, 0f, 0.15f)
    }
    t("hayvan.yarasa", "Yarasa", ANIMAL2, "Bütün hayvan", "Açık kanatlar ve küçük gövde") {
        path(closed = true) {
            moveTo(0.5f, 0.3f); lineTo(0.45f, 0.2f); lineTo(0.4f, 0.32f); quadTo(0.2f, 0.2f, 0f, 0.3f); quadTo(0.12f, 0.45f, 0.1f, 0.65f); quadTo(0.2f, 0.55f, 0.3f, 0.65f); quadTo(0.38f, 0.58f, 0.5f, 0.72f)
            quadTo(0.62f, 0.58f, 0.7f, 0.65f); quadTo(0.8f, 0.55f, 0.9f, 0.65f); quadTo(0.88f, 0.45f, 1f, 0.3f); quadTo(0.8f, 0.2f, 0.6f, 0.32f); lineTo(0.55f, 0.2f); close()
        }
    }
    t("hayvan.dinozor", "Dinozor", FANTASY, "Hayali varlık", "Uzun boyunlu, kuyruklu dört ayaklı dinozor") {
        path(closed = true) {
            moveTo(0.2f, 0.05f); quadTo(0.32f, 0f, 0.32f, 0.12f); quadTo(0.3f, 0.35f, 0.4f, 0.45f); quadTo(0.6f, 0.35f, 0.78f, 0.5f); quadTo(0.9f, 0.6f, 1f, 0.7f)
            quadTo(0.85f, 0.7f, 0.78f, 0.68f); lineTo(0.75f, 0.95f); lineTo(0.66f, 0.95f); lineTo(0.66f, 0.72f); lineTo(0.48f, 0.72f); lineTo(0.48f, 0.95f); lineTo(0.38f, 0.95f)
            lineTo(0.38f, 0.65f); quadTo(0.22f, 0.4f, 0.22f, 0.15f); lineTo(0.12f, 0.13f); quadTo(0.1f, 0.05f, 0.2f, 0.05f)
        }
    }
    t("hayvan.tilki_yuz", "Tilki yüzü", ANIMAL2, "Hayvan yüzü", "Sivri çeneli üçgen yüz ve büyük kulaklar") {
        lc(0.1f, 0.3f, 0.2f, 0f, 0.4f, 0.22f, 0.6f, 0.22f, 0.8f, 0f, 0.9f, 0.3f, 0.5f, 1f); circle(0.37f, 0.45f, 0.03f); circle(0.63f, 0.45f, 0.03f); circle(0.5f, 0.9f, 0.04f)
    }
    t("hayvan.ayicik", "Oyuncak ayı", ANIMAL2, "Bütün hayvan", "Yuvarlak baş ve kulaklar, gövde, kollar ve bacaklar") {
        circle(0.5f, 0.25f, 0.2f); circle(0.32f, 0.08f, 0.07f); circle(0.68f, 0.08f, 0.07f); ellipse(0.5f, 0.65f, 0.25f, 0.27f)
        ellipse(0.2f, 0.58f, 0.08f, 0.15f, 30f); ellipse(0.8f, 0.58f, 0.08f, 0.15f, -30f); circle(0.32f, 0.92f, 0.1f); circle(0.68f, 0.92f, 0.1f)
    }
    t("hayvan.solucan", "Solucan", ANIMAL2, "Böcek", "Dalgalı uzun gövde ve baş") {
        s(0f, 0.7f, 0.15f, 0.5f, 0.3f, 0.7f, 0.45f, 0.5f, 0.6f, 0.7f, 0.75f, 0.5f, 0.85f, 0.45f); circle(0.9f, 0.4f, 0.1f)
    }
    t("hayvan.denizanasi", "Denizanası", ANIMAL2, "Bütün hayvan", "Kubbe ve sarkan dalgalı kollar") {
        path(closed = true) { moveTo(0.1f, 0.45f); cubicTo(0.1f, -0.05f, 0.9f, -0.05f, 0.9f, 0.45f); close() }
        for (i in 0 until 4) { val x = 0.22f + i * 0.19f; s(x, 0.45f, x - 0.04f, 0.6f, x + 0.04f, 0.75f, x - 0.03f, 0.9f, x, 1f) }
    }

    // ------------------------------------------------------------------ doğa ve bitki
    t("doga.kardan_adam", "Kardan adam", NATURE2, "Kış", "Üst üste iki-üç kartopu, havuç burun ve düğmeler") {
        circle(0.5f, 0.75f, 0.25f); circle(0.5f, 0.33f, 0.18f); lc(0.5f, 0.35f, 0.72f, 0.38f, 0.5f, 0.39f); circle(0.44f, 0.28f, 0.02f); circle(0.56f, 0.28f, 0.02f)
        circle(0.5f, 0.65f, 0.03f); circle(0.5f, 0.78f, 0.03f); l(0.3f, 0.58f, 0.05f, 0.42f); l(0.7f, 0.58f, 0.95f, 0.42f)
    }
    t("doga.yanardag", "Yanardağ", NATURE2, "Arazi", "Tepesi kesik dağ ve duman bulutu") {
        lc(0f, 1f, 0.38f, 0.42f, 0.62f, 0.42f, 1f, 1f); sc(0.45f, 0.38f, 0.32f, 0.25f, 0.38f, 0.08f, 0.55f, 0.05f, 0.68f, 0.15f, 0.6f, 0.32f)
    }
    t("doga.ada", "Ada ve palmiye", NATURE2, "Arazi", "Yarım daire ada, eğik gövde ve yapraklar") {
        path(closed = true) { moveTo(0.1f, 0.85f); cubicTo(0.2f, 0.65f, 0.8f, 0.65f, 0.9f, 0.85f); close() }
        s(0.5f, 0.72f, 0.48f, 0.45f, 0.55f, 0.2f); s(0.55f, 0.2f, 0.35f, 0.08f, 0.15f, 0.22f); s(0.55f, 0.2f, 0.75f, 0.08f, 0.95f, 0.22f); s(0.55f, 0.2f, 0.62f, 0.25f, 0.7f, 0.42f)
        s(0f, 0.95f, 0.25f, 0.9f, 0.5f, 0.97f, 0.75f, 0.9f, 1f, 0.95f)
    }
    t("doga.hortum", "Kasırga (hortum)", NATURE2, "Hava", "Aşağı daralan dönen çizgiler") {
        l(0f, 0.05f, 1f, 0.05f); l(0.12f, 0.25f, 0.88f, 0.25f); l(0.25f, 0.45f, 0.75f, 0.45f); l(0.35f, 0.65f, 0.65f, 0.65f); l(0.45f, 0.85f, 0.55f, 0.85f)
        s(0f, 0.05f, 0.3f, 0.5f, 0.5f, 1f); s(1f, 0.05f, 0.7f, 0.5f, 0.5f, 1f)
    }
    t("doga.gezegen", "Gezegen (halkalı)", NATURE2, "Uzay", "Daire ve çevresinde eğik halka") { circle(0.5f, 0.5f, 0.3f); ellipse(0.5f, 0.52f, 0.5f, 0.14f, -15f) }
    t("doga.kuyruklu_yildiz", "Kuyruklu yıldız", NATURE2, "Uzay", "Yuvarlak baş ve arkasında uzanan çizgiler") {
        circle(0.78f, 0.22f, 0.14f); l(0.66f, 0.3f, 0f, 0.95f); l(0.7f, 0.35f, 0.15f, 1f); l(0.62f, 0.25f, 0f, 0.75f)
    }
    t("doga.gunes_bulut", "Bulut arkasında güneş", NATURE2, "Hava", "Yarım görünen güneş ve önünde bulut") {
        arc(0.62f, 0.4f, 0.3f, 0.3f, 180f, 270f); for (k in 0 until 5) { val a = Math.toRadians(-160.0 + k * 40); l((0.62 + 0.36 * cos(a)).toFloat(), (0.4 + 0.36 * sin(a)).toFloat(), (0.62 + 0.48 * cos(a)).toFloat(), (0.4 + 0.48 * sin(a)).toFloat()) }
        sc(0.12f, 0.75f, 0.15f, 0.55f, 0.35f, 0.5f, 0.45f, 0.38f, 0.68f, 0.45f, 0.75f, 0.62f, 0.88f, 0.75f, 0.5f, 0.82f)
    }
    t("doga.tepeler", "Tepeler", NATURE2, "Arazi", "Yan yana yumuşak iki tepe") { s(0f, 0.9f, 0.25f, 0.35f, 0.5f, 0.75f, 0.75f, 0.45f, 1f, 0.9f); l(0f, 0.9f, 1f, 0.9f) }
    t("bitki.agac_yuvarlak", "Ağaç (yuvarlak taç)", PLANT2, "Ağaç", "Yuvarlak taç ve düz gövde") { circle(0.5f, 0.35f, 0.35f); l(0.43f, 0.68f, 0.43f, 1f); l(0.57f, 0.68f, 0.57f, 1f) }
    t("bitki.aycicegi", "Ayçiçeği", PLANT2, "Çiçek", "Büyük göbek, çevresinde taç yaprakları, sap ve yaprak") {
        circle(0.5f, 0.28f, 0.12f)
        val pts = (0..32).flatMap { k -> val a = Math.toRadians(11.25 * k); val r = if (k % 2 == 0) 0.27 else 0.16; listOf((0.5 + r * cos(a)).toFloat(), (0.28 + r * sin(a)).toFloat()) }
        poly(*pts.toFloatArray()); l(0.5f, 0.55f, 0.5f, 1f); sc(0.5f, 0.78f, 0.7f, 0.62f, 0.85f, 0.68f, 0.68f, 0.8f)
    }
    t("bitki.palamut", "Palamut", PLANT2, "Tohum", "Pürüzlü şapka ve oval meşe palamudu") {
        path(closed = true) { moveTo(0.1f, 0.4f); quadTo(0.1f, 0.12f, 0.5f, 0.12f); quadTo(0.9f, 0.12f, 0.9f, 0.4f); close() }
        path { moveTo(0.18f, 0.4f); cubicTo(0.18f, 0.85f, 0.5f, 1f, 0.5f, 1f); cubicTo(0.5f, 1f, 0.82f, 0.85f, 0.82f, 0.4f) }; l(0.5f, 0.12f, 0.55f, 0f)
    }
    t("bitki.kozalak", "Çam kozalağı", PLANT2, "Tohum", "Damla biçimi ve pullu çizgiler") {
        sc(0.5f, 0f, 0.82f, 0.4f, 0.75f, 0.85f, 0.5f, 1f, 0.25f, 0.85f, 0.18f, 0.4f); l(0.25f, 0.35f, 0.75f, 0.6f); l(0.75f, 0.35f, 0.25f, 0.6f); l(0.25f, 0.6f, 0.65f, 0.85f); l(0.75f, 0.6f, 0.35f, 0.85f)
    }
    t("bitki.yonca", "Dört yapraklı yonca", PLANT2, "Yaprak", "Kalp biçimli dört yaprak ve sap") {
        circle(0.35f, 0.3f, 0.15f); circle(0.65f, 0.3f, 0.15f); circle(0.35f, 0.6f, 0.15f); circle(0.65f, 0.6f, 0.15f); s(0.5f, 0.45f, 0.55f, 0.75f, 0.7f, 1f)
    }

    // ------------------------------------------------------------------ taşıtlar
    t("tasit.otobus", "Otobüs", VEHICLE2, "Kara taşıtı", "Uzun gövde, sıra pencereler ve iki tekerlek") {
        roundRect(0f, 0.15f, 1f, 0.62f, 0.08f); for (i in 0 until 4) rect(0.06f + i * 0.22f, 0.25f, 0.16f, 0.18f); circle(0.22f, 0.82f, 0.12f); circle(0.78f, 0.82f, 0.12f)
    }
    t("tasit.helikopter", "Helikopter", VEHICLE2, "Hava taşıtı", "Yuvarlak kabin, kuyruk, pervane ve kızaklar") {
        ellipse(0.35f, 0.5f, 0.28f, 0.2f); l(0.6f, 0.45f, 1f, 0.4f); l(0.95f, 0.3f, 0.98f, 0.52f); l(0.35f, 0.3f, 0.35f, 0.15f); l(0f, 0.15f, 0.75f, 0.15f)
        l(0.2f, 0.7f, 0.2f, 0.85f); l(0.5f, 0.7f, 0.5f, 0.85f); l(0.05f, 0.85f, 0.65f, 0.85f)
    }
    t("tasit.yelkenli", "Yelkenli", VEHICLE2, "Deniz taşıtı", "Tekne gövdesi, direk ve üçgen yelken") {
        lc(0f, 0.7f, 1f, 0.7f, 0.82f, 0.95f, 0.18f, 0.95f); l(0.5f, 0.7f, 0.5f, 0f); lc(0.52f, 0.05f, 0.95f, 0.62f, 0.52f, 0.62f); lc(0.48f, 0.15f, 0.48f, 0.62f, 0.12f, 0.62f)
    }
    t("tasit.motosiklet", "Motosiklet", VEHICLE2, "Kara taşıtı", "İki tekerlek, gövde ve gidon") {
        circle(0.18f, 0.75f, 0.17f); circle(0.82f, 0.75f, 0.17f); l(0.18f, 0.75f, 0.4f, 0.45f, 0.65f, 0.45f, 0.82f, 0.75f); l(0.65f, 0.45f, 0.72f, 0.2f, 0.85f, 0.2f); l(0.35f, 0.42f, 0.55f, 0.42f)
    }
    t("tasit.denizalti", "Denizaltı", VEHICLE2, "Deniz taşıtı", "Hap biçimli gövde, kule, periskop ve lomboz") {
        roundRect(0f, 0.45f, 1f, 0.35f, 0.17f); rect(0.4f, 0.28f, 0.2f, 0.17f); l(0.52f, 0.28f, 0.52f, 0.08f, 0.62f, 0.08f); circle(0.25f, 0.62f, 0.06f); circle(0.5f, 0.62f, 0.06f); circle(0.75f, 0.62f, 0.06f)
    }
    t("tasit.hava_balonu", "Sıcak hava balonu", VEHICLE2, "Hava taşıtı", "Büyük balon, ipler ve sepet") {
        sc(0.5f, 0f, 0.92f, 0.3f, 0.7f, 0.65f, 0.5f, 0.72f, 0.3f, 0.65f, 0.08f, 0.3f); l(0.35f, 0.68f, 0.4f, 0.85f); l(0.65f, 0.68f, 0.6f, 0.85f); rect(0.38f, 0.85f, 0.24f, 0.15f)
    }
    t("tasit.trafik_lambasi", "Trafik lambası", VEHICLE2, "Yol", "Dikdörtgen kutuda üç lamba ve direk") {
        roundRect(0.3f, 0f, 0.4f, 0.7f, 0.06f); circle(0.5f, 0.12f, 0.08f); circle(0.5f, 0.35f, 0.08f); circle(0.5f, 0.58f, 0.08f); l(0.5f, 0.7f, 0.5f, 1f)
    }
    t("tasit.dur_isareti", "Dur işareti", VEHICLE2, "Yol", "Sekizgen levha ve direk") { polygon(0.5f, 0.32f, 0.32f, 8, 22.5f); l(0.5f, 0.64f, 0.5f, 1f) }
    t("tasit.traktor", "Traktör", VEHICLE2, "Kara taşıtı", "Büyük arka teker, küçük ön teker ve kabin") {
        circle(0.28f, 0.68f, 0.28f); circle(0.82f, 0.82f, 0.15f); lc(0.15f, 0.4f, 0.15f, 0.05f, 0.5f, 0.05f, 0.5f, 0.4f); lc(0.5f, 0.4f, 0.95f, 0.45f, 0.95f, 0.68f, 0.56f, 0.68f); l(0.75f, 0.45f, 0.75f, 0.25f)
    }
    t("tasit.ambulans", "Ambulans", VEHICLE2, "Kara taşıtı", "Kutu kasa, ön kabin, artı işareti ve tekerlekler") {
        lc(0f, 0.2f, 0.65f, 0.2f, 0.65f, 0.75f, 0f, 0.75f); lc(0.65f, 0.38f, 0.88f, 0.38f, 1f, 0.55f, 1f, 0.75f, 0.65f, 0.75f)
        l(0.32f, 0.33f, 0.32f, 0.6f); l(0.2f, 0.47f, 0.44f, 0.47f); circle(0.2f, 0.82f, 0.1f); circle(0.8f, 0.82f, 0.1f)
    }
    t("tasit.ufo", "Uçan daire (UFO)", FANTASY, "Hayali varlık", "Basık disk, kubbe ve ışık huzmesi") {
        ellipse(0.5f, 0.45f, 0.48f, 0.12f); arc(0.5f, 0.38f, 0.2f, 0.2f, 180f, 180f); l(0.35f, 0.57f, 0.15f, 1f); l(0.65f, 0.57f, 0.85f, 1f)
    }

    // ------------------------------------------------------------------ teknoloji, eşyalar, aletler
    t("esya.telefon", "Akıllı telefon", TECH, "Cihaz", "Yuvarlak köşeli dikdörtgen, ekran ve tuş") {
        roundRect(0.22f, 0f, 0.56f, 1f, 0.08f); rect(0.28f, 0.1f, 0.44f, 0.72f); circle(0.5f, 0.9f, 0.04f)
    }
    t("esya.laptop", "Dizüstü bilgisayar", TECH, "Cihaz", "Ekran ve altında klavye tabanı") { rect(0.12f, 0.05f, 0.76f, 0.55f); lc(0.12f, 0.6f, 0.88f, 0.6f, 1f, 0.82f, 0f, 0.82f) }
    t("esya.monitor", "Bilgisayar ekranı", TECH, "Cihaz", "Geniş ekran, ayak ve taban") { rect(0f, 0f, 1f, 0.65f); l(0.5f, 0.65f, 0.5f, 0.85f); l(0.25f, 0.88f, 0.75f, 0.88f) }
    t("esya.kulaklik", "Kulaklık", TECH, "Cihaz", "Baş bandı ve iki kulaklık") {
        arc(0.5f, 0.55f, 0.42f, 0.5f, 180f, 180f); roundRect(0f, 0.5f, 0.18f, 0.35f, 0.07f); roundRect(0.82f, 0.5f, 0.18f, 0.35f, 0.07f)
    }
    t("esya.kamera", "Fotoğraf makinesi", TECH, "Cihaz", "Gövde, büyük mercek ve flaş") {
        roundRect(0f, 0.22f, 1f, 0.7f, 0.08f); circle(0.5f, 0.57f, 0.22f); circle(0.5f, 0.57f, 0.1f); rect(0.32f, 0.08f, 0.24f, 0.14f); rect(0.78f, 0.3f, 0.12f, 0.08f)
    }
    t("esya.kol_saati", "Kol saati", THING, "Aksesuar", "Yuvarlak kadran ve iki kayış") {
        circle(0.5f, 0.5f, 0.25f); l(0.5f, 0.5f, 0.5f, 0.35f); l(0.5f, 0.5f, 0.62f, 0.55f); lc(0.38f, 0.28f, 0.4f, 0f, 0.6f, 0f, 0.62f, 0.28f); lc(0.38f, 0.72f, 0.4f, 1f, 0.6f, 1f, 0.62f, 0.72f)
    }
    t("esya.calar_saat", "Çalar saat", THING, "Ev eşyası", "Yuvarlak saat, iki zil, ayaklar ve kollar") {
        circle(0.5f, 0.55f, 0.36f); arc(0.2f, 0.2f, 0.14f, 0.14f, 180f, 180f); arc(0.8f, 0.2f, 0.14f, 0.14f, 180f, 180f)
        l(0.5f, 0.55f, 0.5f, 0.32f); l(0.5f, 0.55f, 0.66f, 0.62f); l(0.25f, 0.85f, 0.15f, 1f); l(0.75f, 0.85f, 0.85f, 1f)
    }
    t("esya.mum", "Mum", THING, "Ev eşyası", "Dikdörtgen mum, fitil ve alev") { rect(0.32f, 0.38f, 0.36f, 0.62f); l(0.5f, 0.38f, 0.5f, 0.28f); sc(0.5f, 0f, 0.6f, 0.15f, 0.55f, 0.28f, 0.45f, 0.28f, 0.4f, 0.15f) }
    t("esya.vazo", "Vazo", THING, "Ev eşyası", "Dar boyunlu, şişkin gövdeli vazo") {
        sc(0.38f, 0f, 0.4f, 0.2f, 0.15f, 0.5f, 0.22f, 0.9f, 0.5f, 1f, 0.78f, 0.9f, 0.85f, 0.5f, 0.6f, 0.2f, 0.62f, 0f)
    }
    t("esya.makas", "Makas", TOOL, "Kırtasiye", "İki halka sap ve çapraz ağızlar") { circle(0.2f, 0.78f, 0.15f); circle(0.8f, 0.78f, 0.15f); l(0.3f, 0.66f, 0.72f, 0f); l(0.7f, 0.66f, 0.28f, 0f) }
    t("esya.atac", "Ataç", THING, "Kırtasiye", "İç içe kıvrılmış tel") {
        path { moveTo(0.6f, 0.3f); lineTo(0.6f, 0.85f); quadTo(0.6f, 1f, 0.45f, 1f); quadTo(0.3f, 1f, 0.3f, 0.85f); lineTo(0.3f, 0.12f); quadTo(0.3f, 0f, 0.5f, 0f); quadTo(0.7f, 0f, 0.7f, 0.12f); lineTo(0.7f, 0.8f) }
    }
    t("esya.raptiye", "Raptiye", THING, "Kırtasiye", "Yuvarlak baş ve sivri iğne") { ellipse(0.5f, 0.2f, 0.3f, 0.2f); lc(0.35f, 0.38f, 0.65f, 0.38f, 0.58f, 0.55f, 0.42f, 0.55f); l(0.5f, 0.55f, 0.5f, 1f) }
    t("esya.cetvel", "Cetvel", TOOL, "Kırtasiye", "Uzun dikdörtgen ve ölçü çizgileri") {
        rect(0f, 0.35f, 1f, 0.3f); for (i in 1..9) l(i * 0.1f, 0.35f, i * 0.1f, if (i % 5 == 0) 0.55f else 0.45f)
    }
    t("esya.canta", "El çantası", CLOTH, "Aksesuar", "Yamuk gövde ve kavisli sap") { lc(0.1f, 0.35f, 0.9f, 0.35f, 1f, 1f, 0f, 1f); arc(0.5f, 0.35f, 0.25f, 0.3f, 180f, 180f) }
    t("esya.sirt_cantasi", "Sırt çantası", CLOTH, "Aksesuar", "Yuvarlak üstlü gövde, cep ve askı") {
        path(closed = true) { moveTo(0.15f, 1f); lineTo(0.15f, 0.35f); quadTo(0.15f, 0.08f, 0.5f, 0.08f); quadTo(0.85f, 0.08f, 0.85f, 0.35f); lineTo(0.85f, 1f); close() }
        rect(0.28f, 0.6f, 0.44f, 0.28f); arc(0.5f, 0.1f, 0.12f, 0.1f, 180f, 180f)
    }
    t("esya.supurge", "Süpürge", TOOL, "Temizlik", "Uzun sap ve yelpaze biçimli çalı") {
        l(0.5f, 0f, 0.5f, 0.6f); lc(0.4f, 0.6f, 0.6f, 0.6f, 0.85f, 1f, 0.15f, 1f); l(0.4f, 0.75f, 0.32f, 1f); l(0.6f, 0.75f, 0.68f, 1f)
    }
    t("esya.kova", "Kova", TOOL, "Temizlik", "Aşağı daralan kova ve sap") { lc(0.12f, 0.3f, 0.88f, 0.3f, 0.75f, 1f, 0.25f, 1f); arc(0.5f, 0.3f, 0.38f, 0.3f, 180f, 180f) }
    t("esya.cekic", "Çekiç", TOOL, "El aleti", "Dikdörtgen baş ve sap") { lc(0.1f, 0.05f, 0.75f, 0.05f, 0.75f, 0.3f, 0.1f, 0.3f); rect(0.38f, 0.3f, 0.14f, 0.7f) }
    t("esya.ingiliz_anahtari", "İngiliz anahtarı", TOOL, "El aleti", "Açık ağızlı baş ve uzun sap") {
        path(closed = true) { moveTo(0.15f, 0f); lineTo(0.15f, 0.2f); lineTo(0.3f, 0.2f); lineTo(0.3f, 0f); quadTo(0.48f, 0.15f, 0.32f, 0.36f); lineTo(0.85f, 0.9f); lineTo(0.78f, 0.98f); lineTo(0.25f, 0.42f); quadTo(0f, 0.3f, 0.15f, 0f) }
    }
    t("esya.tornavida", "Tornavida", TOOL, "El aleti", "Kalın sap ve ince uç") { roundRect(0.38f, 0f, 0.24f, 0.45f, 0.08f); rect(0.47f, 0.45f, 0.06f, 0.45f); lc(0.47f, 0.9f, 0.53f, 0.9f, 0.5f, 1f) }
    t("esya.balta", "Balta", TOOL, "El aleti", "Uzun sap ve yanda kavisli ağız") { l(0.45f, 0f, 0.45f, 1f); path(closed = true) { moveTo(0.45f, 0.08f); lineTo(0.75f, 0.02f); quadTo(0.95f, 0.2f, 0.75f, 0.4f); lineTo(0.45f, 0.32f) } }
    t("esya.kurek", "Kürek", TOOL, "Bahçe", "Sap, tutamak ve sivri uçlu kürek") {
        l(0.5f, 0.08f, 0.5f, 0.55f); l(0.38f, 0.08f, 0.62f, 0.08f); path(closed = true) { moveTo(0.3f, 0.55f); lineTo(0.7f, 0.55f); lineTo(0.7f, 0.82f); lineTo(0.5f, 1f); lineTo(0.3f, 0.82f); close() }
    }
    t("esya.gitar", "Gitar", MUSIC, "Çalgı", "Sekiz biçimli gövde, ses deliği ve uzun sap") {
        sc(0.5f, 0.4f, 0.72f, 0.48f, 0.7f, 0.65f, 0.8f, 0.85f, 0.5f, 1f, 0.2f, 0.85f, 0.3f, 0.65f, 0.28f, 0.48f); circle(0.5f, 0.72f, 0.07f); rect(0.46f, 0f, 0.08f, 0.42f)
    }
    t("esya.davul", "Davul", MUSIC, "Çalgı", "Silindir davul ve çapraz iki baget") {
        ellipse(0.5f, 0.4f, 0.4f, 0.12f); l(0.1f, 0.4f, 0.1f, 0.85f); l(0.9f, 0.4f, 0.9f, 0.85f); arc(0.5f, 0.85f, 0.4f, 0.12f, 0f, 180f); l(0.3f, 0.3f, 0.05f, 0f); l(0.7f, 0.3f, 0.95f, 0f)
    }
    t("esya.mikrofon", "Mikrofon", MUSIC, "Çalgı", "Kapsül baş, sap ve taban") { roundRect(0.35f, 0f, 0.3f, 0.45f, 0.15f); arc(0.5f, 0.35f, 0.25f, 0.25f, 0f, 180f); l(0.5f, 0.6f, 0.5f, 0.9f); l(0.3f, 0.95f, 0.7f, 0.95f) }
    t("esya.futbol_topu", "Futbol topu", SPORT, "Top", "Daire, ortada beşgen ve dikiş çizgileri") {
        circle(0.5f, 0.5f, 0.48f); polygon(0.5f, 0.5f, 0.15f, 5, -90f)
        for (k in 0 until 5) { val a = Math.toRadians(-90.0 + 72 * k); l((0.5 + 0.15 * cos(a)).toFloat(), (0.5 + 0.15 * sin(a)).toFloat(), (0.5 + 0.47 * cos(a)).toFloat(), (0.5 + 0.47 * sin(a)).toFloat()) }
    }
    t("esya.basketbol", "Basketbol topu", SPORT, "Top", "Daire, iki kesişen çizgi ve yan yaylar") {
        circle(0.5f, 0.5f, 0.48f); l(0.5f, 0.02f, 0.5f, 0.98f); l(0.02f, 0.5f, 0.98f, 0.5f); arc(0f, 0.5f, 0.3f, 0.45f, -90f, 180f); arc(1f, 0.5f, 0.3f, 0.45f, 90f, 180f)
    }
    t("esya.raket", "Tenis raketi", SPORT, "Spor aleti", "Oval baş, ağ çizgileri ve sap") {
        ellipse(0.5f, 0.32f, 0.3f, 0.32f); l(0.5f, 0.64f, 0.5f, 1f); l(0.3f, 0.2f, 0.7f, 0.2f); l(0.25f, 0.35f, 0.75f, 0.35f); l(0.3f, 0.5f, 0.7f, 0.5f); l(0.4f, 0.03f, 0.4f, 0.62f); l(0.6f, 0.03f, 0.6f, 0.62f)
    }
    t("esya.dambil", "Dambıl", SPORT, "Spor aleti", "Kısa çubuk ve iki ucta ağırlıklar") { l(0.25f, 0.5f, 0.75f, 0.5f); rect(0.05f, 0.25f, 0.2f, 0.5f); rect(0.75f, 0.25f, 0.2f, 0.5f) }
    t("esya.ucurtma", "Uçurtma", SPORT, "Oyun", "Baklava biçimli uçurtma, çapraz çubuk ve kuyruk") {
        lc(0.5f, 0f, 0.85f, 0.35f, 0.5f, 0.75f, 0.15f, 0.35f); l(0.5f, 0f, 0.5f, 0.75f); l(0.15f, 0.35f, 0.85f, 0.35f); s(0.5f, 0.75f, 0.45f, 0.85f, 0.55f, 0.92f, 0.48f, 1f)
    }
    t("esya.kaykay", "Kaykay", SPORT, "Oyun", "Uçları kalkık tahta ve iki tekerlek") {
        path(closed = true) { moveTo(0f, 0.35f); quadTo(0.05f, 0.55f, 0.15f, 0.55f); lineTo(0.85f, 0.55f); quadTo(0.95f, 0.55f, 1f, 0.35f); quadTo(0.95f, 0.45f, 0.85f, 0.45f); lineTo(0.15f, 0.45f); quadTo(0.05f, 0.45f, 0f, 0.35f) }
        circle(0.25f, 0.7f, 0.08f); circle(0.75f, 0.7f, 0.08f)
    }
    t("giyim.elbise", "Elbise", CLOTH, "Giysi", "Askılı üst, ince bel ve geniş etek") {
        lc(0.38f, 0f, 0.62f, 0f, 0.7f, 0.35f, 0.6f, 0.45f, 0.95f, 1f, 0.05f, 1f, 0.4f, 0.45f, 0.3f, 0.35f); l(0.4f, 0.45f, 0.6f, 0.45f)
    }
    t("giyim.pantolon", "Pantolon", CLOTH, "Giysi", "Bel ve iki paça") { lc(0.2f, 0f, 0.8f, 0f, 0.85f, 1f, 0.58f, 1f, 0.5f, 0.35f, 0.42f, 1f, 0.15f, 1f); l(0.2f, 0.1f, 0.8f, 0.1f) }
    t("giyim.corap", "Çorap", CLOTH, "Giysi", "L biçimli çorap ve lastik çizgisi") {
        path(closed = true) { moveTo(0.3f, 0f); lineTo(0.62f, 0f); lineTo(0.62f, 0.6f); lineTo(0.92f, 0.75f); quadTo(1f, 0.95f, 0.8f, 0.98f); lineTo(0.42f, 0.9f); quadTo(0.28f, 0.85f, 0.3f, 0.65f); close() }
        l(0.3f, 0.15f, 0.62f, 0.15f)
    }
    t("giyim.eldiven", "Eldiven", CLOTH, "Giysi", "Başparmaklı yuvarlak eldiven ve bileklik") {
        path(closed = true) { moveTo(0.3f, 0.75f); lineTo(0.25f, 0.4f); quadTo(0.05f, 0.4f, 0.12f, 0.25f); quadTo(0.2f, 0.18f, 0.28f, 0.28f); quadTo(0.3f, 0f, 0.55f, 0f); quadTo(0.8f, 0f, 0.78f, 0.4f); lineTo(0.72f, 0.75f); close() }
        rect(0.28f, 0.75f, 0.46f, 0.22f)
    }
    t("giyim.papyon", "Papyon", CLOTH, "Aksesuar", "İki üçgen kanat ve ortada düğüm") { lc(0f, 0.15f, 0.42f, 0.42f, 0f, 0.85f); lc(1f, 0.15f, 0.58f, 0.42f, 1f, 0.85f); rect(0.4f, 0.35f, 0.2f, 0.3f) }
    t("giyim.kravat", "Kravat", CLOTH, "Aksesuar", "Düğüm ve sivri uçlu uzun kravat") { lc(0.38f, 0f, 0.62f, 0f, 0.56f, 0.15f, 0.44f, 0.15f); lc(0.44f, 0.15f, 0.56f, 0.15f, 0.68f, 0.82f, 0.5f, 1f, 0.32f, 0.82f) }
    t("esya.dis", "Diş", THING, "Vücut", "Yuvarlak taç ve iki kök") {
        path(closed = true) { moveTo(0.1f, 0.25f); quadTo(0.1f, 0f, 0.3f, 0.02f); quadTo(0.5f, 0.1f, 0.7f, 0.02f); quadTo(0.9f, 0f, 0.9f, 0.25f); quadTo(0.88f, 0.6f, 0.75f, 0.98f); quadTo(0.6f, 1f, 0.55f, 0.6f); quadTo(0.5f, 0.5f, 0.45f, 0.6f); quadTo(0.4f, 1f, 0.25f, 0.98f); quadTo(0.12f, 0.6f, 0.1f, 0.25f) }
    }
    t("esya.yara_bandi", "Yara bandı", THING, "Sağlık", "Eğik yuvarlak bant ve ortadaki ped") {
        roundRect(0f, 0.32f, 1f, 0.36f, 0.18f); rect(0.35f, 0.38f, 0.3f, 0.24f); circle(0.12f, 0.5f, 0.02f); circle(0.88f, 0.5f, 0.02f)
    }
    t("esya.posta_kutusu", "Posta kutusu", THING, "Ev eşyası", "Kemerli kutu, bayrak ve direk") {
        path(closed = true) { moveTo(0f, 0.55f); lineTo(0f, 0.25f); quadTo(0f, 0.05f, 0.35f, 0.05f); lineTo(0.75f, 0.05f); quadTo(1f, 0.05f, 1f, 0.25f); lineTo(1f, 0.55f); close() }
        l(0.8f, 0.3f, 0.8f, 0f, 0.95f, 0f, 0.95f, 0.1f, 0.8f, 0.1f); l(0.5f, 0.55f, 0.5f, 1f)
    }
    t("yapi.kale", "Kale", BUILD, "Yapı", "Mazgallı duvar, iki kule ve kemerli kapı") {
        lc(0f, 1f, 0f, 0.2f, 0.08f, 0.2f, 0.08f, 0.28f, 0.16f, 0.28f, 0.16f, 0.2f, 0.25f, 0.2f, 0.25f, 0.45f, 0.75f, 0.45f, 0.75f, 0.2f, 0.84f, 0.2f, 0.84f, 0.28f, 0.92f, 0.28f, 0.92f, 0.2f, 1f, 0.2f, 1f, 1f)
        path { moveTo(0.4f, 1f); lineTo(0.4f, 0.75f); quadTo(0.5f, 0.6f, 0.6f, 0.75f); lineTo(0.6f, 1f) }
    }
    t("yapi.iglo", "İglo", BUILD, "Yapı", "Kar bloklu kubbe ve kemerli giriş") {
        arc(0.5f, 0.95f, 0.48f, 0.7f, 180f, 180f); l(0.02f, 0.95f, 0.98f, 0.95f); arc(0.75f, 0.95f, 0.13f, 0.2f, 180f, 180f); l(0.12f, 0.65f, 0.88f, 0.65f); l(0.25f, 0.45f, 0.75f, 0.45f)
    }
    t("yapi.yel_degirmeni", "Yel değirmeni", BUILD, "Yapı", "Yamuk kule ve dört kanat") {
        lc(0.35f, 1f, 0.42f, 0.42f, 0.58f, 0.42f, 0.65f, 1f); l(0.5f, 0.42f, 0.05f, 0f); l(0.5f, 0.42f, 0.95f, 0f); l(0.5f, 0.42f, 0.15f, 0.8f); l(0.5f, 0.42f, 0.85f, 0.8f)
    }
    t("hayal.hayalet", "Hayalet", FANTASY, "Hayali varlık", "Kubbe başlı, alt kenarı dalgalı gövde ve gözler") {
        path(closed = true) { moveTo(0.1f, 1f); lineTo(0.1f, 0.4f); cubicTo(0.1f, -0.1f, 0.9f, -0.1f, 0.9f, 0.4f); lineTo(0.9f, 1f); quadTo(0.77f, 0.85f, 0.63f, 1f); quadTo(0.5f, 0.85f, 0.37f, 1f); quadTo(0.23f, 0.85f, 0.1f, 1f) }
        ellipse(0.36f, 0.38f, 0.06f, 0.09f); ellipse(0.64f, 0.38f, 0.06f, 0.09f)
    }
    t("hayal.kafatasi", "Kafatası", FANTASY, "Hayali varlık", "Yuvarlak kafa, göz çukurları, burun ve dişler") {
        path(closed = true) { moveTo(0.25f, 0.85f); lineTo(0.25f, 0.7f); cubicTo(-0.05f, 0.55f, 0.05f, 0f, 0.5f, 0f); cubicTo(0.95f, 0f, 1.05f, 0.55f, 0.75f, 0.7f); lineTo(0.75f, 0.85f); close() }
        circle(0.34f, 0.42f, 0.11f); circle(0.66f, 0.42f, 0.11f); lc(0.5f, 0.56f, 0.56f, 0.66f, 0.44f, 0.66f); l(0.4f, 0.75f, 0.4f, 0.85f); l(0.5f, 0.75f, 0.5f, 0.85f); l(0.6f, 0.75f, 0.6f, 0.85f)
    }
    t("hayal.robot", "Robot", FANTASY, "Hayali varlık", "Kare baş ve anten, kare gövde, kollar ve bacaklar") {
        rect(0.3f, 0.1f, 0.4f, 0.28f); l(0.5f, 0.1f, 0.5f, 0f); circle(0.42f, 0.22f, 0.04f); circle(0.58f, 0.22f, 0.04f); rect(0.22f, 0.42f, 0.56f, 0.36f)
        l(0.22f, 0.5f, 0.05f, 0.7f); l(0.78f, 0.5f, 0.95f, 0.7f); l(0.38f, 0.78f, 0.38f, 1f); l(0.62f, 0.78f, 0.62f, 1f)
    }
    t("hayal.kilic", "Kılıç", FANTASY, "Hayali varlık", "Uzun ağız, siper ve kabza") { lc(0.46f, 0.68f, 0.46f, 0.08f, 0.5f, 0f, 0.54f, 0.08f, 0.54f, 0.68f); l(0.28f, 0.7f, 0.72f, 0.7f); rect(0.46f, 0.72f, 0.08f, 0.22f); circle(0.5f, 0.97f, 0.03f) }
    t("hayal.kalkan", "Kalkan", FANTASY, "Hayali varlık", "Sivri uçlu kalkan ve ortadaki çizgi") {
        path(closed = true) { moveTo(0.1f, 0.05f); quadTo(0.5f, 0.15f, 0.9f, 0.05f); lineTo(0.9f, 0.45f); quadTo(0.85f, 0.85f, 0.5f, 1f); quadTo(0.15f, 0.85f, 0.1f, 0.45f); close() }
        l(0.5f, 0.12f, 0.5f, 0.9f)
    }
    t("esya.odul_kupasi", "Ödül kupası", SPORT, "Ödül", "Kâse, iki kulp, ayak ve taban") {
        path(closed = true) { moveTo(0.22f, 0.05f); lineTo(0.78f, 0.05f); quadTo(0.78f, 0.5f, 0.5f, 0.55f); quadTo(0.22f, 0.5f, 0.22f, 0.05f) }
        arc(0.2f, 0.25f, 0.12f, 0.14f, 90f, 180f); arc(0.8f, 0.25f, 0.12f, 0.14f, -90f, 180f); l(0.5f, 0.55f, 0.5f, 0.8f); rect(0.3f, 0.8f, 0.4f, 0.15f)
    }
    t("esya.madalya", "Madalya", SPORT, "Ödül", "V biçimli kurdele ve yuvarlak madalya") { l(0.25f, 0f, 0.45f, 0.45f); l(0.75f, 0f, 0.55f, 0.45f); circle(0.5f, 0.68f, 0.28f); star(0.5f, 0.68f, 0.14f, 0.06f) }
}

/**
 * v23 şekillerin ne kadar sık çizildiğine göre önsel (1 nadir … 5 çok sık): çizim oyunlarında ve okul çizimlerinde en
 * çok görülen nesneler yüksek. Karışık (çok parçalı) çizimlerde benzerlik puanının yanında bu önsel ve kullanıcının
 * kabul ettiği önerilerin sayısı da hesaba katılır.
 */
object TemplatePopularity {
    private val table: Map<String, Int> = mapOf(
        "nesne.kalp" to 5, "doga.yildiz" to 5, "doga.yildiz_cizgi" to 5, "doga.gunes" to 5, "doga.bulut" to 5,
        "yapi.ev.besik" to 5, "bitki.agac" to 5, "bitki.agac_yuvarlak" to 5, "bitki.papatya" to 5, "bitki.cicek_basit" to 5,
        "yuz.ifade.gulen" to 5, "vucut.cop_adam" to 5, "simge.tik" to 5, "simge.ok_sag" to 5,
        "hayvan.balik" to 4, "hayvan.kedi_yuz" to 4, "hayvan.kedi" to 4, "hayvan.kopek" to 4, "hayvan.kus" to 4, "hayvan.kus_ucan" to 4,
        "hayvan.kelebek" to 4, "tasit.araba" to 4, "tasit.ucak" to 4, "tasit.tekne" to 4, "tasit.yelkenli" to 4, "tasit.roket" to 4,
        "doga.hilal" to 4, "doga.gokkusagi" to 4, "doga.dag" to 4, "doga.simsek" to 4, "nesne.balon" to 4, "nesne.ampul" to 4,
        "meyve.elma" to 4, "yiyecek.pasta" to 4, "yiyecek.dondurma" to 4, "doga.kardan_adam" to 4, "simge.carpi" to 4,
        "simge.nota" to 4, "nesne.top" to 4, "yuz.goz.badem" to 4, "yapi.ev.bacali" to 4, "bitki.lale" to 4, "bitki.cam" to 4,
        "nesne.semsiye" to 3, "nesne.hediye" to 3, "yiyecek.pizza" to 3, "meyve.muz" to 3, "meyve.cilek" to 3, "hayvan.orumcek" to 3,
        "hayvan.ari" to 3, "hayvan.salyangoz" to 3, "yapi.kale" to 3, "tasit.tren" to 3, "tasit.bisiklet" to 3, "tasit.otobus" to 3,
        "simge.sonsuz" to 3, "simge.konum" to 3, "simge.buyutec" to 3, "nesne.kupa" to 3, "nesne.kitap" to 3, "nesne.kalem" to 3,
        "nesne.saat" to 3, "nesne.anahtar" to 3, "esya.makas" to 3, "esya.gitar" to 3, "esya.futbol_topu" to 3, "vucut.el" to 3,
        "hayvan.baykus" to 3, "hayvan.penguen" to 3, "hayvan.yilan" to 3, "hayvan.kaplumbaga" to 3, "hayvan.ordek" to 3,
        "yiyecek.hamburger" to 3, "yiyecek.kek" to 3, "esya.telefon" to 3, "esya.mum" to 3, "hayal.hayalet" to 3, "hayal.robot" to 3,
        "simge.ok_yukari" to 3, "simge.arti" to 3, "doga.yagmur" to 3, "doga.kar_tanesi" to 3, "doga.ates" to 3, "doga.dalga" to 3,
        "nesne.tac" to 3, "nesne.elmas" to 3, "nesne.zarf" to 3, "nesne.konusma" to 3, "simge.dusunce" to 3, "simge.parilti" to 3,
        // v27 tam figürler
        "yuz.tam.kulakli" to 4, "yapi.ev.tam" to 4, "insan.basit_govde" to 4, "bitki.cicek_saksi" to 4, "doga.dag_gunes" to 4,
        "yuz.tam.sacli" to 3, "bitki.agac_elmali" to 3, "hayvan.tirtil" to 3, "tasit.araba_pencereli" to 3, "nesne.robot_kafa" to 3,
    )

    /** Şablonun önseli (bilinmeyen: 2). */
    fun of(key: String): Int = table[key] ?: 2

    /** Bilinen yüksek önselli şablonların anahtarları. */
    val frequent: Set<String> get() = table.filterValues { it >= 4 }.keys
}
