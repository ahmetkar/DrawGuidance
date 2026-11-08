package com.karapp.drawguidance.content.shapes

import kotlin.math.cos
import kotlin.math.sin

private const val OBJECT = "Nesne"
private const val VEHICLE = "Taşıt"
private const val SOLID = "3B cisim"

internal fun TemplateScope.objectTemplates() {
    t("nesne.kalp", "Kalp", OBJECT, "Sembol", "İki yuvarlak loblu, alt ucu sivri kalp") {
        path(closed = true) {
            moveTo(0.5f, 0.25f); cubicTo(0.5f, -0.05f, 1.05f, -0.05f, 1f, 0.35f); cubicTo(0.95f, 0.65f, 0.6f, 0.8f, 0.5f, 1f)
            cubicTo(0.4f, 0.8f, 0.05f, 0.65f, 0f, 0.35f); cubicTo(-0.05f, -0.05f, 0.5f, -0.05f, 0.5f, 0.25f)
        }
    }
    t("nesne.anahtar", "Anahtar", OBJECT, "Ev eşyası", "Yuvarlak baş, uzun gövde ve dişler") {
        circle(0.15f, 0.5f, 0.14f); l(0.29f, 0.5f, 1f, 0.5f); l(0.8f, 0.5f, 0.8f, 0.68f); l(0.92f, 0.5f, 0.92f, 0.64f)
    }
    t("nesne.kupa", "Kupa (bardak)", OBJECT, "Mutfak", "Silindir bardak ve yan kulp") {
        l(0.1f, 0f, 0.15f, 1f, 0.7f, 1f, 0.75f, 0f); arc(0.75f, 0.45f, 0.2f, 0.22f, -90f, 180f)
    }
    t("nesne.fincan", "Fincan ve tabak", OBJECT, "Mutfak", "Kase biçimli fincan, kulp ve tabak") {
        path { moveTo(0.15f, 0.2f); cubicTo(0.15f, 0.85f, 0.75f, 0.85f, 0.75f, 0.2f) }; l(0.15f, 0.2f, 0.75f, 0.2f)
        arc(0.76f, 0.4f, 0.13f, 0.13f, -80f, 170f); ellipse(0.45f, 0.85f, 0.45f, 0.1f)
    }
    t("nesne.sise", "Şişe", OBJECT, "Mutfak", "Dar boyunlu şişe ve kapak") {
        l(0.42f, 0f, 0.42f, 0.25f, 0.25f, 0.4f, 0.25f, 1f, 0.75f, 1f, 0.75f, 0.4f, 0.58f, 0.25f, 0.58f, 0f); l(0.42f, 0.08f, 0.58f, 0.08f)
    }
    t("nesne.ampul", "Ampul", OBJECT, "Ev eşyası", "Armut biçimli cam, duy ve vida çizgileri") {
        path { moveTo(0.35f, 0.7f); cubicTo(0.35f, 0.5f, 0.1f, 0.45f, 0.1f, 0.3f); cubicTo(0.1f, -0.1f, 0.9f, -0.1f, 0.9f, 0.3f); cubicTo(0.9f, 0.45f, 0.65f, 0.5f, 0.65f, 0.7f) }
        rect(0.35f, 0.7f, 0.3f, 0.2f); l(0.35f, 0.8f, 0.65f, 0.8f); l(0.42f, 0.9f, 0.5f, 1f, 0.58f, 0.9f)
    }
    t("nesne.saat", "Saat", OBJECT, "Ev eşyası", "Yuvarlak kadran, akrep ve yelkovan") {
        circle(0.5f, 0.5f, 0.48f); l(0.5f, 0.5f, 0.5f, 0.18f); l(0.5f, 0.5f, 0.72f, 0.6f)
        for (i in 0 until 4) { val a = Math.toRadians(90.0 * i); l((0.5 + 0.4 * cos(a)).toFloat(), (0.5 + 0.4 * sin(a)).toFloat(), (0.5 + 0.46 * cos(a)).toFloat(), (0.5 + 0.46 * sin(a)).toFloat()) }
    }
    t("nesne.semsiye", "Şemsiye", OBJECT, "Giyim/Aksesuar", "Dilimli kubbe, sap ve kıvrık uç") {
        path(closed = true) {
            moveTo(0f, 0.45f); cubicTo(0f, -0.1f, 1f, -0.1f, 1f, 0.45f)
            quadTo(0.875f, 0.35f, 0.75f, 0.45f); quadTo(0.625f, 0.35f, 0.5f, 0.45f); quadTo(0.375f, 0.35f, 0.25f, 0.45f); quadTo(0.125f, 0.35f, 0f, 0.45f)
        }
        path { moveTo(0.5f, 0.45f); lineTo(0.5f, 0.9f); quadTo(0.5f, 1f, 0.4f, 1f); quadTo(0.32f, 1f, 0.32f, 0.9f) }
    }
    t("nesne.kalem", "Kalem", OBJECT, "Kırtasiye", "Altıgen gövde, açılmış uç ve silgi") {
        lc(0f, 0.4f, 0.75f, 0.4f, 1f, 0.5f, 0.75f, 0.6f, 0f, 0.6f); l(0.12f, 0.4f, 0.12f, 0.6f); l(0.75f, 0.4f, 0.75f, 0.6f)
    }
    t("nesne.kitap", "Kitap (açık)", OBJECT, "Kırtasiye", "Ortadan açılmış iki sayfa ve cilt") {
        path(closed = true) { moveTo(0.5f, 0.2f); quadTo(0.25f, 0.05f, 0f, 0.15f); lineTo(0f, 0.9f); quadTo(0.25f, 0.8f, 0.5f, 0.95f); quadTo(0.75f, 0.8f, 1f, 0.9f); lineTo(1f, 0.15f); quadTo(0.75f, 0.05f, 0.5f, 0.2f) }
        l(0.5f, 0.2f, 0.5f, 0.95f)
    }
    t("nesne.kitap_kapali", "Kitap (kapalı)", OBJECT, "Kırtasiye", "Dikdörtgen kapak, sırt ve sayfa kenarı") {
        rect(0.2f, 0f, 0.65f, 0.9f); l(0.3f, 0f, 0.3f, 0.9f); l(0.2f, 0.9f, 0.25f, 1f, 0.9f, 1f, 0.85f, 0.9f)
    }
    t("nesne.balon", "Balon", OBJECT, "Oyuncak/Parti", "Oval balon, düğüm ve ip") {
        ellipse(0.5f, 0.35f, 0.3f, 0.35f); lc(0.46f, 0.74f, 0.54f, 0.74f, 0.5f, 0.7f); s(0.5f, 0.74f, 0.45f, 0.85f, 0.55f, 0.92f, 0.5f, 1f)
    }
    t("nesne.zarf", "Zarf", OBJECT, "Kırtasiye", "Dikdörtgen zarf ve V biçimli kapak") { rect(0f, 0.15f, 1f, 0.7f); l(0f, 0.15f, 0.5f, 0.55f, 1f, 0.15f) }
    t("nesne.kilit", "Asma kilit", OBJECT, "Ev eşyası", "Kilit gövdesi, U biçimli halka ve anahtar deliği") {
        rect(0.15f, 0.42f, 0.7f, 0.58f); path { moveTo(0.28f, 0.42f); lineTo(0.28f, 0.25f); cubicTo(0.28f, -0.05f, 0.72f, -0.05f, 0.72f, 0.25f); lineTo(0.72f, 0.42f) }
        circle(0.5f, 0.66f, 0.06f); l(0.5f, 0.72f, 0.5f, 0.85f)
    }
    t("nesne.zil", "Çan / zil", OBJECT, "Ev eşyası", "Çan gövdesi, geniş ağız ve tokmak") {
        path(closed = true) { moveTo(0.5f, 0.05f); cubicTo(0.8f, 0.05f, 0.78f, 0.6f, 0.95f, 0.8f); lineTo(0.05f, 0.8f); cubicTo(0.22f, 0.6f, 0.2f, 0.05f, 0.5f, 0.05f) }
        circle(0.5f, 0.9f, 0.08f)
    }
    t("nesne.hediye", "Hediye kutusu", OBJECT, "Oyuncak/Parti", "Kutu, kurdele ve fiyonk") {
        rect(0.08f, 0.35f, 0.84f, 0.65f); rect(0f, 0.22f, 1f, 0.13f); l(0.5f, 0.22f, 0.5f, 1f)
        sc(0.5f, 0.22f, 0.25f, 0f, 0.2f, 0.15f); sc(0.5f, 0.22f, 0.75f, 0f, 0.8f, 0.15f)
    }
    t("nesne.tac", "Taç", OBJECT, "Giyim/Aksesuar", "Beş sivri uçlu taç") { lc(0f, 1f, 0f, 0.2f, 0.25f, 0.6f, 0.5f, 0f, 0.75f, 0.6f, 1f, 0.2f, 1f, 1f) }
    t("nesne.elmas", "Elmas", OBJECT, "Sembol", "Kesilmiş taş: üst yüzey ve sivri alt") {
        lc(0.2f, 0f, 0.8f, 0f, 1f, 0.3f, 0.5f, 1f, 0f, 0.3f); l(0f, 0.3f, 1f, 0.3f); l(0.2f, 0f, 0.35f, 0.3f, 0.5f, 1f); l(0.8f, 0f, 0.65f, 0.3f, 0.5f, 1f)
    }
    t("nesne.capa", "Çapa", OBJECT, "Sembol", "Halka, gövde, çapraz kol ve kıvrık uçlar") {
        circle(0.5f, 0.1f, 0.08f); l(0.5f, 0.18f, 0.5f, 0.95f); l(0.3f, 0.3f, 0.7f, 0.3f); path { moveTo(0.1f, 0.6f); quadTo(0.15f, 0.95f, 0.5f, 0.95f); quadTo(0.85f, 0.95f, 0.9f, 0.6f) }
    }
    t("nesne.konusma", "Konuşma balonu", OBJECT, "Sembol", "Oval balon ve aşağıdaki sivri kuyruk") {
        path(closed = true) {
            moveTo(0.3f, 0.78f); cubicTo(-0.1f, 0.75f, -0.1f, 0.05f, 0.5f, 0.05f); cubicTo(1.1f, 0.05f, 1.1f, 0.78f, 0.5f, 0.78f); lineTo(0.2f, 1f); close()
        }
    }
    t("nesne.bayrak", "Bayrak", OBJECT, "Sembol", "Direk ve dalgalanan bayrak") {
        l(0.1f, 1f, 0.1f, 0f); path(closed = true) { moveTo(0.1f, 0.05f); quadTo(0.35f, -0.05f, 0.55f, 0.08f); quadTo(0.75f, 0.2f, 0.95f, 0.1f); lineTo(0.95f, 0.5f); quadTo(0.75f, 0.6f, 0.55f, 0.48f); quadTo(0.35f, 0.35f, 0.1f, 0.45f) }
    }
    t("nesne.gozluk_gunes", "Güneş gözlüğü", OBJECT, "Giyim/Aksesuar", "İki yamuk cam ve köprü") {
        lc(0f, 0.3f, 0.42f, 0.3f, 0.38f, 0.7f, 0.05f, 0.7f); lc(0.58f, 0.3f, 1f, 0.3f, 0.95f, 0.7f, 0.62f, 0.7f); s(0.42f, 0.35f, 0.5f, 0.3f, 0.58f, 0.35f)
    }
    t("nesne.sapka", "Şapka", OBJECT, "Giyim/Aksesuar", "Yuvarlak tepe ve geniş kenar") {
        path { moveTo(0.22f, 0.7f); lineTo(0.25f, 0.25f); quadTo(0.5f, 0.1f, 0.75f, 0.25f); lineTo(0.78f, 0.7f) }; ellipse(0.5f, 0.75f, 0.5f, 0.12f)
    }
    t("nesne.tisort", "Tişört", OBJECT, "Giyim/Aksesuar", "Kısa kollu tişört ve yaka") {
        lc(0.35f, 0f, 0.1f, 0.1f, 0f, 0.35f, 0.18f, 0.42f, 0.2f, 1f, 0.8f, 1f, 0.82f, 0.42f, 1f, 0.35f, 0.9f, 0.1f, 0.65f, 0f)
        path { moveTo(0.35f, 0f); quadTo(0.5f, 0.15f, 0.65f, 0f) }
    }
    t("nesne.ayakkabi", "Ayakkabı", OBJECT, "Giyim/Aksesuar", "Yandan spor ayakkabı ve taban") {
        sc(0f, 0.85f, 0f, 0.35f, 0.3f, 0.3f, 0.45f, 0.55f, 0.85f, 0.62f, 1f, 0.8f, 0.95f, 0.92f); l(0f, 0.8f, 0.97f, 0.82f)
    }
    t("nesne.top", "Top", OBJECT, "Oyuncak/Parti", "Daire ve kavisli dikiş çizgileri") {
        circle(0.5f, 0.5f, 0.48f); arc(-0.1f, 0.5f, 0.45f, 0.45f, -50f, 100f); arc(1.1f, 0.5f, 0.45f, 0.45f, 130f, 100f)
    }

    // --- Taşıtlar ---
    t("tasit.tekerlek", "Tekerlek", VEHICLE, "Taşıt parçası", "Lastik, jant ve göbek") { circle(0.5f, 0.5f, 0.48f); circle(0.5f, 0.5f, 0.3f); circle(0.5f, 0.5f, 0.07f) }
    t("tasit.araba", "Araba", VEHICLE, "Kara", "Gövde, kabin, pencere ve iki tekerlek") {
        l(0.18f, 0.8f, 0f, 0.8f, 0f, 0.5f, 0.2f, 0.45f, 0.32f, 0.2f, 0.7f, 0.2f, 0.82f, 0.45f, 1f, 0.5f, 1f, 0.8f, 0.82f, 0.8f); l(0.3f, 0.8f, 0.7f, 0.8f)
        circle(0.24f, 0.8f, 0.1f); circle(0.76f, 0.8f, 0.1f); l(0.5f, 0.22f, 0.5f, 0.45f)
    }
    t("tasit.tekne", "Tekne", VEHICLE, "Deniz", "Yamuk gövde, direk ve üçgen yelken") {
        lc(0f, 0.65f, 1f, 0.65f, 0.8f, 0.95f, 0.2f, 0.95f); l(0.5f, 0.65f, 0.5f, 0f); lc(0.52f, 0.05f, 0.9f, 0.55f, 0.52f, 0.55f)
    }
    t("tasit.ucak", "Uçak", VEHICLE, "Hava", "Uzun gövde, kanatlar ve kuyruk") {
        sc(0f, 0.5f, 0.1f, 0.42f, 0.9f, 0.42f, 1f, 0.5f, 0.9f, 0.58f, 0.1f, 0.58f); lc(0.45f, 0.44f, 0.35f, 0f, 0.5f, 0f, 0.62f, 0.44f)
        lc(0.45f, 0.56f, 0.35f, 1f, 0.5f, 1f, 0.62f, 0.56f); lc(0.08f, 0.44f, 0f, 0.2f, 0.08f, 0.2f, 0.18f, 0.43f)
    }
    t("tasit.bisiklet", "Bisiklet", VEHICLE, "Kara", "İki tekerlek, üçgen kadro, sele ve gidon") {
        circle(0.2f, 0.72f, 0.2f); circle(0.8f, 0.72f, 0.2f); lc(0.2f, 0.72f, 0.45f, 0.72f, 0.35f, 0.38f); l(0.45f, 0.72f, 0.72f, 0.38f, 0.35f, 0.38f)
        l(0.72f, 0.38f, 0.8f, 0.72f); l(0.72f, 0.38f, 0.7f, 0.25f, 0.8f, 0.25f); l(0.3f, 0.3f, 0.42f, 0.3f)
    }
    t("tasit.roket", "Roket", VEHICLE, "Hava", "Sivri burunlu gövde, pencere, kanatçıklar ve alev") {
        path(closed = true) { moveTo(0.5f, 0f); cubicTo(0.72f, 0.15f, 0.7f, 0.5f, 0.66f, 0.78f); lineTo(0.34f, 0.78f); cubicTo(0.3f, 0.5f, 0.28f, 0.15f, 0.5f, 0f) }
        circle(0.5f, 0.35f, 0.07f); lc(0.34f, 0.55f, 0.18f, 0.85f, 0.35f, 0.78f); lc(0.66f, 0.55f, 0.82f, 0.85f, 0.65f, 0.78f); l(0.42f, 0.8f, 0.5f, 1f, 0.58f, 0.8f)
    }
    t("tasit.tren", "Tren", VEHICLE, "Kara", "Lokomotif, baca, kabin ve tekerlekler") {
        l(0f, 0.8f, 0f, 0.45f, 0.55f, 0.45f, 0.55f, 0.1f, 1f, 0.1f, 1f, 0.8f, 0f, 0.8f); rect(0.12f, 0.2f, 0.12f, 0.25f); rect(0.65f, 0.2f, 0.22f, 0.2f)
        circle(0.2f, 0.85f, 0.1f); circle(0.5f, 0.85f, 0.1f); circle(0.8f, 0.85f, 0.1f)
    }
    t("tasit.kamyon", "Kamyon", VEHICLE, "Kara", "Kasa, kabin ve üç tekerlek") {
        rect(0f, 0.15f, 0.65f, 0.6f); l(0.65f, 0.3f, 0.85f, 0.3f, 1f, 0.5f, 1f, 0.75f, 0.65f, 0.75f); circle(0.15f, 0.8f, 0.1f); circle(0.45f, 0.8f, 0.1f); circle(0.85f, 0.8f, 0.1f)
    }
}

/**
 * Elle çizilmiş 3B cisim şablonları: [ShapeTemplate.solid] ile uygulamadaki gölgeli cisim (SOLID_*) eşleşir. 3B çizim
 * modunda kullanıcı bir küpün çizgilerini çizdiğinde öneri, çizgileri gölgeli küp şekliyle değiştirmeyi sunar.
 */
internal fun TemplateScope.solidTemplates() {
    t("cisim.kup", "Küp (çizim)", SOLID, "Cisim", "Ön yüz karesi, üst ve yan yüz: altıgen dış hat ve iç Y", solid = "SOLID_CUBE") {
        lc(0.5f, 0f, 0.93f, 0.25f, 0.93f, 0.75f, 0.5f, 1f, 0.07f, 0.75f, 0.07f, 0.25f); l(0.07f, 0.25f, 0.5f, 0.5f, 0.93f, 0.25f); l(0.5f, 0.5f, 0.5f, 1f)
    }
    t("cisim.kup_onden", "Küp (eğik izdüşüm)", SOLID, "Cisim", "Önde kare, arkada kaydırılmış kare ve birleştiren kenarlar", solid = "SOLID_CUBE") {
        rect(0f, 0.3f, 0.7f, 0.7f); l(0f, 0.3f, 0.3f, 0f, 1f, 0f, 1f, 0.7f, 0.7f, 1f); l(0.7f, 0.3f, 1f, 0f)
    }
    t("cisim.kutu", "Dikdörtgen prizma (çizim)", SOLID, "Cisim", "Uzun kutu: ön dikdörtgen, üst ve yan yüz", solid = "SOLID_BOX") {
        rect(0f, 0.3f, 0.75f, 0.7f); l(0f, 0.3f, 0.25f, 0f, 1f, 0f, 1f, 0.7f, 0.75f, 1f); l(0.75f, 0.3f, 1f, 0f)
    }
    t("cisim.silindir", "Silindir (çizim)", SOLID, "Cisim", "Üstte elips, iki dikey kenar ve altta yay", solid = "SOLID_CYLINDER") {
        ellipse(0.5f, 0.13f, 0.4f, 0.13f); l(0.1f, 0.13f, 0.1f, 0.87f); l(0.9f, 0.13f, 0.9f, 0.87f); arc(0.5f, 0.87f, 0.4f, 0.13f, 0f, 180f)
    }
    t("cisim.koni", "Koni (çizim)", SOLID, "Cisim", "Tepe noktasından inen iki kenar ve taban elipsi", solid = "SOLID_CONE") {
        l(0.05f, 0.85f, 0.5f, 0f, 0.95f, 0.85f); ellipse(0.5f, 0.85f, 0.45f, 0.15f)
    }
    t("cisim.kure", "Küre (çizim)", SOLID, "Cisim", "Daire ve ön yarısı görünen ekvator elipsi", solid = "SOLID_SPHERE") {
        circle(0.5f, 0.5f, 0.48f); arc(0.5f, 0.5f, 0.48f, 0.14f, 0f, 180f)
    }
    t("cisim.piramit", "Piramit (çizim)", SOLID, "Cisim", "Kare tabanlı piramit: tepe, iki görünen yüz ve taban", solid = "SOLID_PYRAMID") {
        lc(0.5f, 0f, 0f, 0.8f, 0.6f, 1f, 1f, 0.75f); l(0.5f, 0f, 0.6f, 1f)
    }
    t("cisim.ucgen_prizma", "Üçgen prizma (çizim)", SOLID, "Cisim", "Önde üçgen, arkaya uzanan kenarlar", solid = "SOLID_PRISM") {
        lc(0f, 1f, 0.3f, 0.4f, 0.6f, 1f); l(0.3f, 0.4f, 0.7f, 0f, 1f, 0.6f, 0.6f, 1f)
    }
    t("cisim.altigen_prizma", "Altıgen prizma (çizim)", SOLID, "Cisim", "Üstte basık altıgen, dikey kenarlar ve alt hat", solid = "SOLID_HEX_PRISM") {
        lc(0.25f, 0f, 0.75f, 0f, 1f, 0.12f, 0.75f, 0.25f, 0.25f, 0.25f, 0f, 0.12f)
        l(0f, 0.12f, 0f, 0.88f, 0.25f, 1f, 0.75f, 1f, 1f, 0.88f, 1f, 0.12f); l(0.25f, 0.25f, 0.25f, 1f); l(0.75f, 0.25f, 0.75f, 1f)
    }
    t("cisim.kubbe", "Yarım küre (çizim)", SOLID, "Cisim", "Yarım daire kubbe ve taban elipsi", solid = "SOLID_DOME") {
        arc(0.5f, 0.75f, 0.48f, 0.7f, 180f, 180f); ellipse(0.5f, 0.75f, 0.48f, 0.14f)
    }
}
