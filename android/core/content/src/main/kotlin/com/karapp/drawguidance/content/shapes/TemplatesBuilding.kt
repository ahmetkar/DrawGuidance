package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.vector.VectorBuilder

private const val ROOF = "Çatı"
private const val DOOR = "Kapı"
private const val WINDOW = "Pencere"
private const val BUILDING = "Yapı"
private const val FURNITURE = "Mobilya"
private const val CHAIR = "Sandalye"

internal fun TemplateScope.buildingTemplates() {
    // --- Çatı çeşitleri (yalnız çatı) ---
    t("yapi.cati.besik", "Beşik çatı", ROOF, "Çatı", "Üçgen alınlıklı iki yöne eğimli çatı") { lc(0f, 1f, 0.5f, 0.1f, 1f, 1f) }
    t("yapi.cati.kirma", "Kırma çatı", ROOF, "Çatı", "Dört yöne eğimli, mahyası kısa yamuk çatı") { lc(0f, 1f, 0.25f, 0.2f, 0.75f, 0.2f, 1f, 1f) }
    t("yapi.cati.mansard", "Mansard çatı", ROOF, "Çatı", "Alt eğimi dik, üst eğimi yatık kırık çatı") {
        lc(0f, 1f, 0.1f, 0.4f, 0.3f, 0.08f, 0.7f, 0.08f, 0.9f, 0.4f, 1f, 1f)
    }
    t("yapi.cati.tek_egim", "Tek eğimli çatı", ROOF, "Çatı", "Bir yana eğimli, sundurma tipi çatı") { lc(0f, 0.6f, 1f, 0.05f, 1f, 0.45f, 0f, 1f) }
    t("yapi.cati.kubbe", "Kubbe", ROOF, "Çatı", "Yarım daire kubbe ve tabanı") {
        path(closed = true) { moveTo(0f, 1f); cubicTo(0f, 0.05f, 1f, 0.05f, 1f, 1f); close() }
    }
    t("yapi.cati.kulah", "Külah çatı", ROOF, "Çatı", "Kuleye ait sivri, yüksek konik çatı") { lc(0.18f, 1f, 0.5f, 0f, 0.82f, 1f) }
    t("yapi.cati.pagoda", "Pagoda çatısı", ROOF, "Çatı", "Saçak uçları yukarı kıvrık uzak doğu çatısı") {
        path(closed = true) {
            moveTo(0f, 0.7f); quadTo(0.2f, 0.62f, 0.3f, 0.2f); lineTo(0.7f, 0.2f); quadTo(0.8f, 0.62f, 1f, 0.7f); quadTo(0.5f, 0.95f, 0f, 0.7f)
        }
    }
    t("yapi.cati.tonoz", "Tonoz çatı", ROOF, "Çatı", "Yarım silindir (beşik tonoz) kesiti, alçak kemer") {
        path(closed = true) { moveTo(0f, 1f); quadTo(0.5f, 0.1f, 1f, 1f); close() }
    }
    t("yapi.cati.testere", "Testere dişi çatı", ROOF, "Çatı", "Fabrika çatısı gibi art arda dişler") {
        l(0f, 1f, 0f, 0.4f, 0.33f, 1f, 0.33f, 0.4f, 0.66f, 1f, 0.66f, 0.4f, 1f, 1f)
    }
    t("yapi.cati.duz", "Düz çatı (korkuluklu)", ROOF, "Çatı", "Düz dam ve üstünde korkuluk çizgisi") {
        l(0f, 1f, 0f, 0.35f, 1f, 0.35f, 1f, 1f); l(0f, 0.6f, 1f, 0.6f)
    }

    // --- Evler (çatı + gövde; çok elemanlı) ---
    fun VectorBuilder.body() = rect(0.12f, 0.45f, 0.76f, 0.55f)
    t("yapi.ev.besik", "Ev (beşik çatılı)", ROOF, "Ev", "Kare gövde, üçgen çatı, kapı ve pencere") {
        body(); lc(0.02f, 0.47f, 0.5f, 0.02f, 0.98f, 0.47f); rect(0.42f, 0.7f, 0.18f, 0.3f); rect(0.2f, 0.56f, 0.14f, 0.14f)
    }
    t("yapi.ev.kirma", "Ev (kırma çatılı)", ROOF, "Ev", "Kare gövde, yamuk çatı, kapı ve iki pencere") {
        body(); lc(0.02f, 0.47f, 0.26f, 0.12f, 0.74f, 0.12f, 0.98f, 0.47f); rect(0.42f, 0.7f, 0.18f, 0.3f)
        rect(0.18f, 0.56f, 0.14f, 0.14f); rect(0.68f, 0.56f, 0.14f, 0.14f)
    }
    t("yapi.ev.bacali", "Ev (bacalı)", ROOF, "Ev", "Beşik çatılı ev ve çatıda baca") {
        body(); lc(0.02f, 0.47f, 0.5f, 0.02f, 0.98f, 0.47f); l(0.7f, 0.2f, 0.7f, 0.06f, 0.8f, 0.06f, 0.8f, 0.29f)
        rect(0.42f, 0.7f, 0.18f, 0.3f)
    }
    t("yapi.ev.iki_katli", "Ev (iki katlı)", ROOF, "Ev", "Yüksek gövde, üçgen çatı, iki kat pencere ve kapı") {
        rect(0.15f, 0.35f, 0.7f, 0.65f); lc(0.05f, 0.37f, 0.5f, 0f, 0.95f, 0.37f)
        rect(0.24f, 0.44f, 0.14f, 0.13f); rect(0.62f, 0.44f, 0.14f, 0.13f); rect(0.24f, 0.68f, 0.14f, 0.13f); rect(0.44f, 0.72f, 0.14f, 0.28f)
    }

    // --- Kapı çeşitleri ---
    t("yapi.kapi.duz", "Kapı (düz)", DOOR, "Kapı", "Dikdörtgen kapı ve kapı kolu") { rect(0.2f, 0f, 0.6f, 1f); circle(0.68f, 0.55f, 0.04f) }
    t("yapi.kapi.kemerli", "Kapı (kemerli)", DOOR, "Kapı", "Üstü yarım daire kemerli kapı ve kolu") {
        path(closed = true) { moveTo(0.2f, 1f); lineTo(0.2f, 0.3f); cubicTo(0.2f, -0.08f, 0.8f, -0.08f, 0.8f, 0.3f); lineTo(0.8f, 1f); close() }
        circle(0.68f, 0.6f, 0.04f)
    }
    t("yapi.kapi.cift", "Kapı (çift kanatlı)", DOOR, "Kapı", "İki kanatlı kapı, orta çizgi ve iki kol") {
        rect(0.05f, 0f, 0.9f, 1f); l(0.5f, 0f, 0.5f, 1f); circle(0.42f, 0.55f, 0.035f); circle(0.58f, 0.55f, 0.035f)
    }
    t("yapi.kapi.panelli", "Kapı (panelli)", DOOR, "Kapı", "İki gömme panelli ahşap kapı ve kolu") {
        rect(0.2f, 0f, 0.6f, 1f); rect(0.3f, 0.08f, 0.4f, 0.36f); rect(0.3f, 0.54f, 0.4f, 0.38f); circle(0.72f, 0.5f, 0.03f)
    }
    t("yapi.kapi.camli", "Kapı (camlı)", DOOR, "Kapı", "Üst yarısı camlı kapı, cam bölmeleri ve kol") {
        rect(0.2f, 0f, 0.6f, 1f); rect(0.3f, 0.08f, 0.4f, 0.36f); l(0.5f, 0.08f, 0.5f, 0.44f); circle(0.7f, 0.58f, 0.035f)
    }
    t("yapi.kapi.garaj", "Garaj kapısı", DOOR, "Kapı", "Geniş, yatay şeritli kepenk kapı") {
        rect(0f, 0.1f, 1f, 0.9f); l(0f, 0.32f, 1f, 0.32f); l(0f, 0.54f, 1f, 0.54f); l(0f, 0.76f, 1f, 0.76f)
    }
    t("yapi.kapi.ahir", "Ahır kapısı", DOOR, "Kapı", "Çapraz (X) destekli ahır kapısı") {
        rect(0.1f, 0f, 0.8f, 1f); l(0.1f, 0f, 0.9f, 1f); l(0.9f, 0f, 0.1f, 1f)
    }
    t("yapi.kapi.yuvarlak", "Kapı (yuvarlak)", DOOR, "Kapı", "Yuvarlak (masal evi) kapı ve ortada kol") {
        circle(0.5f, 0.5f, 0.48f); circle(0.5f, 0.5f, 0.05f)
    }

    // --- Pencere çeşitleri ---
    t("yapi.pencere.dort", "Pencere (dört bölmeli)", WINDOW, "Pencere", "Kare pencere ve artı biçimli kayıt") {
        rect(0f, 0f, 1f, 1f); l(0.5f, 0f, 0.5f, 1f); l(0f, 0.5f, 1f, 0.5f)
    }
    t("yapi.pencere.kemerli", "Pencere (kemerli)", WINDOW, "Pencere", "Üstü kemerli pencere ve orta kayıt") {
        path(closed = true) { moveTo(0.15f, 1f); lineTo(0.15f, 0.35f); cubicTo(0.15f, -0.1f, 0.85f, -0.1f, 0.85f, 0.35f); lineTo(0.85f, 1f); close() }
        l(0.5f, 0.02f, 0.5f, 1f)
    }
    t("yapi.pencere.lomboz", "Pencere (yuvarlak)", WINDOW, "Pencere", "Gemi lombozu: iç içe iki daire") { circle(0.5f, 0.5f, 0.48f); circle(0.5f, 0.5f, 0.36f) }
    t("yapi.pencere.perdeli", "Pencere (perdeli)", WINDOW, "Pencere", "Pencere ve iki yana toplanmış perdeler") {
        rect(0f, 0f, 1f, 1f); s(0.05f, 0f, 0.12f, 0.5f, 0.05f, 1f); s(0.95f, 0f, 0.88f, 0.5f, 0.95f, 1f); l(0.5f, 0f, 0.5f, 1f)
    }
    t("yapi.pencere.panjurlu", "Pencere (panjurlu)", WINDOW, "Pencere", "Ortada pencere, iki yanda panjur kanatları") {
        rect(0.3f, 0f, 0.4f, 1f); rect(0f, 0f, 0.25f, 1f); rect(0.75f, 0f, 0.25f, 1f); l(0.3f, 0.5f, 0.7f, 0.5f)
    }
    t("yapi.pencere.ucgen", "Çatı penceresi (üçgen)", WINDOW, "Pencere", "Çatı arasındaki üçgen pencere") { lc(0f, 0.9f, 0.5f, 0.1f, 1f, 0.9f); l(0.5f, 0.1f, 0.5f, 0.9f) }

    // --- Diğer yapı elemanları ---
    t("yapi.baca", "Baca", BUILDING, "Yapı elemanı", "Dikdörtgen baca ve üstünden çıkan duman") {
        rect(0.3f, 0.45f, 0.4f, 0.55f); s(0.5f, 0.42f, 0.35f, 0.3f, 0.55f, 0.18f, 0.4f, 0.02f)
    }
    t("yapi.merdiven_el", "El merdiveni", BUILDING, "Yapı elemanı", "İki dikme ve basamaklar") {
        l(0.25f, 0f, 0.25f, 1f); l(0.75f, 0f, 0.75f, 1f)
        for (i in 1..4) { val y = i / 5f; l(0.25f, y, 0.75f, y) }
    }
    t("yapi.basamak", "Basamaklar", BUILDING, "Yapı elemanı", "Yandan görünen merdiven basamakları") {
        l(0f, 1f, 0f, 0.75f, 0.25f, 0.75f, 0.25f, 0.5f, 0.5f, 0.5f, 0.5f, 0.25f, 0.75f, 0.25f, 0.75f, 0f, 1f, 0f)
    }
    t("yapi.cit", "Çit", BUILDING, "Yapı elemanı", "Sivri uçlu tahtalar ve iki yatay kuşak") {
        for (i in 0..3) { val x = 0.05f + i * 0.27f; lc(x, 1f, x, 0.15f, x + 0.08f, 0f, x + 0.16f, 0.15f, x + 0.16f, 1f) }
        l(0f, 0.35f, 1f, 0.35f); l(0f, 0.75f, 1f, 0.75f)
    }
    t("yapi.tugla", "Tuğla duvar", BUILDING, "Yapı elemanı", "Şaşırtmalı sıralanmış tuğla örgüsü") {
        rect(0f, 0f, 1f, 1f); for (r in 1..3) l(0f, r / 4f, 1f, r / 4f)
        for (r in 0..3) { val off = if (r % 2 == 0) 0.33f else 0.16f; var x = off; while (x < 1f) { l(x, r / 4f, x, (r + 1) / 4f); x += 0.33f } }
    }
    t("yapi.kale_kulesi", "Kale kulesi", BUILDING, "Yapı", "Mazgallı (dişli) üst kısımlı kule ve kapısı") {
        l(0.2f, 1f, 0.2f, 0.25f, 0.1f, 0.25f, 0.1f, 0f, 0.26f, 0f, 0.26f, 0.12f, 0.42f, 0.12f, 0.42f, 0f, 0.58f, 0f, 0.58f, 0.12f,
            0.74f, 0.12f, 0.74f, 0f, 0.9f, 0f, 0.9f, 0.25f, 0.8f, 0.25f, 0.8f, 1f)
        path { moveTo(0.4f, 1f); lineTo(0.4f, 0.78f); quadTo(0.5f, 0.66f, 0.6f, 0.78f); lineTo(0.6f, 1f) }
    }
    t("yapi.cadir", "Çadır", BUILDING, "Yapı", "Üçgen çadır ve açık giriş") { lc(0f, 1f, 0.5f, 0.05f, 1f, 1f); l(0.5f, 0.05f, 0.38f, 1f); l(0.5f, 0.05f, 0.62f, 1f) }
    t("yapi.kopru", "Köprü", BUILDING, "Yapı", "Kemerli taş köprü, yol ve kemer") {
        l(0f, 0.3f, 1f, 0.3f); path { moveTo(0f, 0.55f); lineTo(0.15f, 0.55f); quadTo(0.5f, 0.1f, 0.85f, 0.55f); lineTo(1f, 0.55f) }
        l(0f, 0.3f, 0f, 1f); l(1f, 0.3f, 1f, 1f)
    }
    t("yapi.deniz_feneri", "Deniz feneri", BUILDING, "Yapı", "Yukarı daralan kule, ışık odası ve kubbe") {
        lc(0.3f, 1f, 0.38f, 0.35f, 0.62f, 0.35f, 0.7f, 1f); rect(0.36f, 0.18f, 0.28f, 0.17f); path { moveTo(0.33f, 0.18f); quadTo(0.5f, -0.05f, 0.67f, 0.18f) }
        l(0.34f, 0.6f, 0.66f, 0.6f)
    }
    t("yapi.gokdelen", "Gökdelen", BUILDING, "Yapı", "Yüksek dikdörtgen bina ve pencere sıraları") {
        rect(0.25f, 0f, 0.5f, 1f); for (i in 1..5) { val y = i / 6f; l(0.25f, y, 0.75f, y) }; l(0.5f, 0f, 0.5f, 1f)
    }
    t("yapi.cami_kubbe", "Kubbeli yapı", BUILDING, "Yapı", "Kare gövde üzerinde kubbe ve yanda ince kule") {
        rect(0.15f, 0.55f, 0.55f, 0.45f); path { moveTo(0.15f, 0.55f); cubicTo(0.15f, 0.12f, 0.7f, 0.12f, 0.7f, 0.55f) }
        l(0.8f, 1f, 0.8f, 0.2f, 0.85f, 0.05f, 0.9f, 0.2f, 0.9f, 1f)
    }
}

internal fun TemplateScope.furnitureTemplates() {
    // --- Sandalye çeşitleri ---
    t("mobilya.sandalye.yandan", "Sandalye (yandan)", CHAIR, "Sandalye", "Yandan sandalye: arkalık, oturak ve iki bacak") {
        l(0.2f, 0f, 0.2f, 0.55f, 0.8f, 0.55f, 0.8f, 1f); l(0.2f, 0.55f, 0.2f, 1f)
    }
    t("mobilya.sandalye.onden", "Sandalye (önden)", CHAIR, "Sandalye", "Önden sandalye: arkalık çerçevesi, oturak ve bacaklar") {
        rect(0.2f, 0f, 0.6f, 0.45f); rect(0.12f, 0.45f, 0.76f, 0.1f); l(0.16f, 0.55f, 0.16f, 1f); l(0.84f, 0.55f, 0.84f, 1f)
    }
    t("mobilya.sandalye.tabure", "Tabure", CHAIR, "Sandalye", "Yuvarlak oturak ve üç bacak") {
        ellipse(0.5f, 0.15f, 0.42f, 0.12f); l(0.18f, 0.22f, 0.1f, 1f); l(0.5f, 0.27f, 0.5f, 1f); l(0.82f, 0.22f, 0.9f, 1f)
    }
    t("mobilya.sandalye.berjer", "Berjer koltuk", CHAIR, "Sandalye", "Yuvarlak arkalıklı, kollu, kısa ayaklı koltuk") {
        path(closed = true) {
            moveTo(0.15f, 0.85f); lineTo(0.15f, 0.25f); cubicTo(0.15f, -0.05f, 0.85f, -0.05f, 0.85f, 0.25f); lineTo(0.85f, 0.85f); close()
        }
        rect(0f, 0.45f, 0.18f, 0.4f); rect(0.82f, 0.45f, 0.18f, 0.4f); l(0.18f, 0.6f, 0.82f, 0.6f); l(0.08f, 0.85f, 0.08f, 1f); l(0.92f, 0.85f, 0.92f, 1f)
    }
    t("mobilya.sandalye.sallanan", "Sallanan sandalye", CHAIR, "Sandalye", "Yandan sandalye ve altında kavisli kızaklar") {
        l(0.22f, 0f, 0.25f, 0.55f, 0.78f, 0.55f); l(0.3f, 0.55f, 0.3f, 0.86f); l(0.72f, 0.55f, 0.72f, 0.86f)
        path { moveTo(0f, 0.78f); quadTo(0.5f, 1.1f, 1f, 0.8f) }
    }
    t("mobilya.sandalye.bar", "Bar taburesi", CHAIR, "Sandalye", "Yüksek oturak, uzun bacaklar ve ayak desteği") {
        l(0.2f, 0.05f, 0.8f, 0.05f); l(0.28f, 0.05f, 0.15f, 1f); l(0.72f, 0.05f, 0.85f, 1f); l(0.21f, 0.65f, 0.79f, 0.65f)
    }
    t("mobilya.sandalye.ofis", "Ofis koltuğu", CHAIR, "Sandalye", "Arkalık, oturak, orta direk ve tekerlekli ayak") {
        path(closed = true) { moveTo(0.3f, 0.5f); lineTo(0.28f, 0.08f); quadTo(0.5f, -0.04f, 0.72f, 0.08f); lineTo(0.7f, 0.5f); close() }
        rect(0.2f, 0.52f, 0.6f, 0.1f); l(0.5f, 0.62f, 0.5f, 0.85f); l(0.15f, 0.92f, 0.5f, 0.85f, 0.85f, 0.92f)
        circle(0.15f, 0.95f, 0.04f); circle(0.85f, 0.95f, 0.04f)
    }
    t("mobilya.sandalye.bank", "Bank", CHAIR, "Sandalye", "Uzun oturak, arkalık tahtası ve ayaklar") {
        rect(0f, 0.1f, 1f, 0.2f); rect(0f, 0.5f, 1f, 0.1f); l(0.1f, 0.3f, 0.1f, 0.5f); l(0.9f, 0.3f, 0.9f, 0.5f)
        l(0.08f, 0.6f, 0.08f, 1f); l(0.92f, 0.6f, 0.92f, 1f)
    }

    // --- Diğer mobilya ---
    t("mobilya.masa", "Masa", FURNITURE, "Masa", "Tabla ve dört (önden iki) bacaklı masa") {
        rect(0f, 0.2f, 1f, 0.1f); l(0.08f, 0.3f, 0.08f, 1f); l(0.92f, 0.3f, 0.92f, 1f)
    }
    t("mobilya.masa_yuvarlak", "Yuvarlak masa", FURNITURE, "Masa", "Elips tabla, orta ayak ve taban") {
        ellipse(0.5f, 0.2f, 0.48f, 0.14f); l(0.5f, 0.34f, 0.5f, 0.92f); l(0.28f, 0.98f, 0.5f, 0.92f, 0.72f, 0.98f)
    }
    t("mobilya.kanepe", "Kanepe", FURNITURE, "Oturma", "Arkalık, iki kol, oturak minderleri ve ayaklar") {
        rect(0.1f, 0.15f, 0.8f, 0.4f); rect(0f, 0.35f, 0.14f, 0.45f); rect(0.86f, 0.35f, 0.14f, 0.45f); rect(0.14f, 0.55f, 0.72f, 0.25f)
        l(0.5f, 0.55f, 0.5f, 0.8f); l(0.07f, 0.8f, 0.07f, 0.95f); l(0.93f, 0.8f, 0.93f, 0.95f)
    }
    t("mobilya.yatak", "Yatak", FURNITURE, "Yatak", "Başlık, şilte, yastık ve ayak ucu") {
        l(0f, 1f, 0f, 0.1f, 0.12f, 0.1f, 0.12f, 1f); rect(0.12f, 0.5f, 0.88f, 0.25f); l(1f, 0.45f, 1f, 1f)
        ellipse(0.25f, 0.42f, 0.1f, 0.07f)
    }
    t("mobilya.dolap", "Gardırop", FURNITURE, "Dolap", "Uzun dolap, iki kapak ve kulplar") {
        rect(0.15f, 0f, 0.7f, 0.95f); l(0.5f, 0f, 0.5f, 0.95f); l(0.44f, 0.4f, 0.44f, 0.52f); l(0.56f, 0.4f, 0.56f, 0.52f)
        l(0.2f, 0.95f, 0.2f, 1f); l(0.8f, 0.95f, 0.8f, 1f)
    }
    t("mobilya.komodin", "Çekmeceli komodin", FURNITURE, "Dolap", "Üç çekmeceli kutu ve kulplar") {
        rect(0.1f, 0.05f, 0.8f, 0.85f); l(0.1f, 0.33f, 0.9f, 0.33f); l(0.1f, 0.62f, 0.9f, 0.62f)
        l(0.42f, 0.19f, 0.58f, 0.19f); l(0.42f, 0.47f, 0.58f, 0.47f); l(0.42f, 0.76f, 0.58f, 0.76f)
    }
    t("mobilya.kitaplik", "Kitaplık", FURNITURE, "Dolap", "Rafları ve kitapları olan dolap") {
        rect(0.1f, 0f, 0.8f, 1f); l(0.1f, 0.33f, 0.9f, 0.33f); l(0.1f, 0.66f, 0.9f, 0.66f)
        l(0.2f, 0.33f, 0.2f, 0.12f); l(0.28f, 0.33f, 0.28f, 0.1f); l(0.36f, 0.33f, 0.44f, 0.14f); l(0.6f, 0.66f, 0.6f, 0.45f); l(0.68f, 0.66f, 0.68f, 0.42f)
    }
    t("mobilya.abajur", "Abajur", FURNITURE, "Aydınlatma", "Yamuk başlık, gövde ve taban") {
        lc(0.25f, 0.4f, 0.35f, 0f, 0.65f, 0f, 0.75f, 0.4f); l(0.5f, 0.4f, 0.5f, 0.92f); l(0.3f, 0.95f, 0.7f, 0.95f)
    }
    t("mobilya.televizyon", "Televizyon", FURNITURE, "Elektronik", "Ekran çerçevesi ve ayak") {
        roundRect(0f, 0.05f, 1f, 0.7f, 0.05f); rect(0.07f, 0.12f, 0.86f, 0.56f); l(0.35f, 1f, 0.5f, 0.75f, 0.65f, 1f)
    }
}
