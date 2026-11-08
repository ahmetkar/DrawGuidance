package com.karapp.drawguidance.content.shapes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val MATH = "Matematik"
private const val SCIENCE = "Fen"
private const val CHART = "Şema ve grafik"
private const val COMIC = "Çizgi roman"

/**
 * v29 not alma moduna özel şablonlar ([ModeCatalog.NOTES_PREFIX]): derste ve not tutarken sık çizilen eksenler, sayı
 * doğrusu, açı, dik üçgen, Venn şeması, akış şeması kutuları, grafikler, devre ve fen çizimleri. Yalnız not alma
 * tuvalinin tahmininde kullanılır.
 */
internal fun TemplateScope.notesTemplates() {
    t("ders.eksen", "Koordinat ekseni", MATH, "Eksen", "Dik kesişen iki eksen ve uçlarında oklar") {
        l(0.1f, 0.95f, 0.1f, 0.02f); l(0.1f, 0.9f, 0.98f, 0.9f)
        l(0.04f, 0.1f, 0.1f, 0.02f, 0.16f, 0.1f); l(0.9f, 0.84f, 0.98f, 0.9f, 0.9f, 0.96f)
    }
    t("ders.eksen_dort", "Dört bölgeli eksen", MATH, "Eksen", "Ortadan kesişen iki eksen (artı biçimli) ve oklar") {
        l(0.5f, 1f, 0.5f, 0f); l(0f, 0.5f, 1f, 0.5f); l(0.44f, 0.08f, 0.5f, 0f, 0.56f, 0.08f); l(0.92f, 0.44f, 1f, 0.5f, 0.92f, 0.56f)
    }
    t("ders.sayi_dogrusu", "Sayı doğrusu", MATH, "Eksen", "Yatay doğru ve eşit aralıklı çentikler") {
        l(0f, 0.5f, 1f, 0.5f)
        for (i in 0..4) { val x = 0.1f + 0.2f * i; l(x, 0.38f, x, 0.62f) }
    }
    t("ders.dik_ucgen", "Dik üçgen", MATH, "Geometri", "Üçgen ve dik açı işareti") {
        lc(0.05f, 0.95f, 0.95f, 0.95f, 0.05f, 0.05f); l(0.05f, 0.8f, 0.2f, 0.8f, 0.2f, 0.95f)
    }
    t("ders.aci", "Açı", MATH, "Geometri", "Bir noktadan çıkan iki ışın ve açı yayı") {
        l(1f, 0.92f, 0.05f, 0.92f, 0.8f, 0.08f); arc(0.05f, 0.92f, 0.3f, 0.3f, -48f, 48f)
    }
    t("ders.vektor", "Vektör", MATH, "Geometri", "Eğik doğru parçası ve ucunda ok") {
        l(0.05f, 0.95f, 0.95f, 0.05f); l(0.62f, 0.08f, 0.95f, 0.05f, 0.92f, 0.38f)
    }
    t("ders.kesir_daire", "Kesir (daire dilimi)", MATH, "Kesir", "Daire ve iki yarıçap (dilim)") {
        circle(0.5f, 0.5f, 0.47f); l(0.5f, 0.03f, 0.5f, 0.5f, 0.97f, 0.5f)
    }
    t("ders.kesir_cizgi", "Kesir çizgisi", MATH, "Kesir", "Yatay kesir çizgisi, üstünde ve altında birer kutu") {
        rect(0.3f, 0.02f, 0.4f, 0.3f); l(0f, 0.5f, 1f, 0.5f); rect(0.3f, 0.68f, 0.4f, 0.3f)
    }
    t("ders.venn", "Venn şeması (iki küme)", CHART, "Küme", "Kesişen iki daire") {
        circle(0.34f, 0.5f, 0.32f); circle(0.66f, 0.5f, 0.32f)
    }
    t("ders.venn3", "Venn şeması (üç küme)", CHART, "Küme", "Kesişen üç daire") {
        circle(0.36f, 0.36f, 0.28f); circle(0.64f, 0.36f, 0.28f); circle(0.5f, 0.64f, 0.28f)
    }
    t("ders.akis_karar", "Karar kutusu", CHART, "Akış şeması", "Eşkenar dörtgen (koşul)") {
        lc(0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f)
    }
    t("ders.akis_basla", "Başla / bitir kutusu", CHART, "Akış şeması", "Uçları yuvarlak yatay kutu") {
        roundRect(0f, 0.25f, 1f, 0.5f, 0.25f)
    }
    t("ders.akis_iki_kutu", "Bağlı iki kutu", CHART, "Akış şeması", "Okla bağlanan iki kutu") {
        rect(0f, 0.3f, 0.36f, 0.4f); rect(0.64f, 0.3f, 0.36f, 0.4f); l(0.36f, 0.5f, 0.64f, 0.5f); l(0.56f, 0.42f, 0.64f, 0.5f, 0.56f, 0.58f)
    }
    t("ders.onay_kutusu", "İşaretli kutu", CHART, "Liste", "Kare kutu ve içinde onay işareti") {
        rect(0.05f, 0.05f, 0.9f, 0.9f); l(0.22f, 0.52f, 0.42f, 0.74f, 0.8f, 0.24f)
    }
    t("ders.suslu_parantez", "Süslü parantez", MATH, "İşaret", "Ortası sivri süslü parantez ({)") {
        s(0.75f, 0f, 0.5f, 0.06f, 0.48f, 0.3f, 0.4f, 0.46f, 0.25f, 0.5f, 0.4f, 0.54f, 0.48f, 0.7f, 0.5f, 0.94f, 0.75f, 1f)
    }
    t("ders.sutun_grafigi", "Sütun grafiği", CHART, "Grafik", "Eksenler ve üç sütun") {
        l(0.05f, 0f, 0.05f, 0.95f, 1f, 0.95f)
        rect(0.18f, 0.55f, 0.16f, 0.4f); rect(0.44f, 0.25f, 0.16f, 0.7f); rect(0.7f, 0.42f, 0.16f, 0.53f)
    }
    t("ders.cizgi_grafigi", "Çizgi grafiği", CHART, "Grafik", "Eksenler ve kırık çizgi") {
        l(0.05f, 0f, 0.05f, 0.95f, 1f, 0.95f); l(0.1f, 0.8f, 0.32f, 0.55f, 0.52f, 0.66f, 0.72f, 0.3f, 0.95f, 0.18f)
    }
    t("ders.parabol", "Parabol", MATH, "Grafik", "Eksenler ve U biçimli eğri") {
        l(0.5f, 1f, 0.5f, 0f); l(0f, 0.85f, 1f, 0.85f)
        curve(48) { tt -> val x = tt * 0.8f + 0.1f; val u = (x - 0.5f) / 0.4f; com.karapp.drawguidance.content.vector.P(x, 0.85f - 0.75f * (1f - u * u)) }
    }
    t("ders.sinus", "Sinüs dalgası", MATH, "Grafik", "Yatay eksen üzerinde dalga") {
        l(0f, 0.5f, 1f, 0.5f)
        curve(64) { tt -> com.karapp.drawguidance.content.vector.P(tt, 0.5f - 0.4f * sin(tt * 2f * PI.toFloat())) }
    }
    t("ders.direnc", "Direnç", SCIENCE, "Devre", "İki uç tel arasında zikzak") {
        l(0f, 0.5f, 0.2f, 0.5f, 0.27f, 0.3f, 0.37f, 0.7f, 0.47f, 0.3f, 0.57f, 0.7f, 0.67f, 0.3f, 0.74f, 0.5f, 1f, 0.5f)
    }
    t("ders.pil_simgesi", "Pil (devre simgesi)", SCIENCE, "Devre", "Biri uzun biri kısa iki paralel çizgi ve teller") {
        l(0f, 0.5f, 0.42f, 0.5f); l(0.42f, 0.1f, 0.42f, 0.9f); l(0.58f, 0.3f, 0.58f, 0.7f); l(0.58f, 0.5f, 1f, 0.5f)
    }
    t("ders.ampul_devre", "Basit devre", SCIENCE, "Devre", "Dikdörtgen tel, pil ve ampul (daire içinde çarpı)") {
        lc(0.05f, 0.1f, 0.95f, 0.1f, 0.95f, 0.9f, 0.05f, 0.9f); circle(0.5f, 0.1f, 0.1f); l(0.43f, 0.03f, 0.57f, 0.17f); l(0.57f, 0.03f, 0.43f, 0.17f)
        l(0.44f, 0.8f, 0.44f, 1f); l(0.56f, 0.84f, 0.56f, 0.96f)
    }
    t("ders.atom", "Atom", SCIENCE, "Fen", "Çekirdek ve üç eğik yörünge") {
        ellipse(0.5f, 0.5f, 0.48f, 0.17f); ellipse(0.5f, 0.5f, 0.48f, 0.17f, 60f); ellipse(0.5f, 0.5f, 0.48f, 0.17f, -60f); circle(0.5f, 0.5f, 0.06f)
    }
    t("ders.hucre", "Hücre", SCIENCE, "Fen", "Oval zar ve içinde çekirdek") {
        ellipse(0.5f, 0.5f, 0.48f, 0.36f); circle(0.56f, 0.46f, 0.13f)
    }
    t("ders.mercek", "İnce kenarlı mercek", SCIENCE, "Fen", "İki yayla sınırlı mercek ve asal eksen") {
        s(0.5f, 0f, 0.6f, 0.5f, 0.5f, 1f); s(0.5f, 0f, 0.4f, 0.5f, 0.5f, 1f); l(0f, 0.5f, 1f, 0.5f)
    }
    t("ders.huni", "Erlenmeyer", SCIENCE, "Fen", "Dar boyunlu, geniş tabanlı deney şişesi") {
        lc(0.4f, 0f, 0.6f, 0f, 0.6f, 0.35f, 0.95f, 0.95f, 0.05f, 0.95f, 0.4f, 0.35f)
    }
    // v30 görsel not tutma (sketchnoting) dağarcığından: kaplar, bağlayıcılar, liste ve insan simgesi
    t("ders.serit", "Şerit başlık (afiş)", CHART, "Kap", "Başlık şeridi ve iki yanda katlanmış uçlar") {
        lc(0.16f, 0.15f, 0.84f, 0.15f, 0.84f, 0.65f, 0.16f, 0.65f)
        l(0.16f, 0.35f, 0f, 0.35f, 0.08f, 0.6f, 0f, 0.85f, 0.16f, 0.85f, 0.16f, 0.65f)
        l(0.84f, 0.35f, 1f, 0.35f, 0.92f, 0.6f, 1f, 0.85f, 0.84f, 0.85f, 0.84f, 0.65f)
    }
    t("ders.golgeli_kutu", "Gölgeli kutu", CHART, "Kap", "Dikdörtgen kap ve sağ alttan gölge çizgisi") {
        rect(0f, 0f, 0.86f, 0.8f); l(0.08f, 0.9f, 0.96f, 0.9f, 0.96f, 0.1f)
    }
    t("ders.kesik_ok", "Kesik çizgili ok", CHART, "Bağlayıcı", "Kesik çizgili yol ve ucunda ok başı") {
        for (i in 0 until 4) { val x = 0.02f + i * 0.2f; l(x, 0.5f, x + 0.12f, 0.5f) }
        l(0.82f, 0.3f, 1f, 0.5f, 0.82f, 0.7f)
    }
    t("ders.yildiz_insan", "Yıldız insan", CHART, "İnsan", "Daire baş ve yıldız biçimli gövde (not çizimlerinin insanı)") {
        circle(0.5f, 0.14f, 0.13f)
        lc(0.5f, 0.32f, 0.95f, 0.45f, 0.64f, 0.6f, 0.8f, 1f, 0.5f, 0.76f, 0.2f, 1f, 0.36f, 0.6f, 0.05f, 0.45f)
    }
    t("ders.madde_listesi", "Madde listesi", CHART, "Liste", "Üç madde işareti ve yanlarında yazı çizgileri") {
        for (i in 0 until 3) { val y = 0.12f + i * 0.38f; circle(0.07f, y, 0.06f); l(0.22f, y, 1f, y) }
    }
    t("ders.tabela", "Yön tabelası", CHART, "Bağlayıcı", "Direk, ok biçimli levha ve zemin çizgisi") {
        l(0.3f, 0.1f, 0.3f, 1f); lc(0.08f, 0.08f, 0.8f, 0.08f, 0.96f, 0.26f, 0.8f, 0.44f, 0.08f, 0.44f); l(0.12f, 1f, 0.48f, 1f)
    }
    t("ders.uyari", "Uyarı üçgeni", CHART, "İşaret", "Üçgen ve içinde ünlem") {
        lc(0.5f, 0.02f, 0.98f, 0.95f, 0.02f, 0.95f); l(0.5f, 0.32f, 0.5f, 0.68f); circle(0.5f, 0.82f, 0.04f)
    }
}

/**
 * v29 çizgi roman moduna özel şablonlar ([ModeCatalog.COMIC_PREFIX]): balon biçimleri, patlama, duygu işaretleri
 * (ter damlası, öfke, şaşkınlık), hız çizgileri, uyku, sarmal, panel kareleri ve manga gözü. Yalnız çizgi roman
 * tuvalinin tahmininde kullanılır.
 */
internal fun TemplateScope.comicTemplates() {
    t("roman.konusma", "Konuşma balonu", COMIC, "Balon", "Oval balon ve aşağı uzanan kuyruk (tek çizgi)") {
        path(closed = true) {
            moveTo(0.3f, 0.72f); cubicTo(0.0f, 0.68f, -0.02f, 0.1f, 0.5f, 0.04f); cubicTo(1.02f, 0.1f, 1.0f, 0.68f, 0.55f, 0.72f)
            lineTo(0.22f, 0.98f); lineTo(0.3f, 0.72f)
        }
    }
    t("roman.dusunce", "Düşünce balonu (bulut)", COMIC, "Balon", "Bulut biçimli balon ve üç küçülen daire") {
        val pts = ArrayList<Float>()
        for (i in 0 until 9) {
            val a = i / 9f * 2f * PI.toFloat()
            val r = if (i % 2 == 0) 1f else 0.82f
            pts += 0.5f + 0.46f * r * cos(a); pts += 0.36f + 0.3f * r * sin(a)
        }
        sc(*pts.toFloatArray())
        circle(0.3f, 0.76f, 0.06f); circle(0.2f, 0.87f, 0.045f); circle(0.12f, 0.96f, 0.03f)
    }
    t("roman.bagirma", "Bağırma balonu", COMIC, "Balon", "Sivri dişli patlak balon") { star(0.5f, 0.5f, 0.5f, 0.36f, 12, 8f) }
    t("roman.patlama", "Patlama (BOOM)", COMIC, "Efekt", "Düzensiz, derin dişli patlama yıldızı") {
        val rs = floatArrayOf(0.5f, 0.24f, 0.44f, 0.2f, 0.5f, 0.26f, 0.4f, 0.22f, 0.48f, 0.2f, 0.42f, 0.25f, 0.5f, 0.22f, 0.4f, 0.24f)
        lc(*FloatArray(rs.size * 2) { j ->
            val i = j / 2; val a = (i / rs.size.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
            if (j % 2 == 0) 0.5f + rs[i] * cos(a) else 0.5f + rs[i] * sin(a)
        })
    }
    t("roman.ter_damlasi", "Ter damlası", COMIC, "Duygu işareti", "Üstü sivri damla") {
        path(closed = true) { moveTo(0.5f, 0f); cubicTo(0.62f, 0.3f, 0.92f, 0.55f, 0.82f, 0.8f); cubicTo(0.72f, 1.04f, 0.28f, 1.04f, 0.18f, 0.8f); cubicTo(0.08f, 0.55f, 0.38f, 0.3f, 0.5f, 0f) }
    }
    t("roman.ofke", "Öfke damarı", COMIC, "Duygu işareti", "Dışa kıvrılan dört kısa yay (artı biçimli damar)") {
        s(0.42f, 0.05f, 0.4f, 0.32f, 0.08f, 0.38f); s(0.58f, 0.05f, 0.6f, 0.32f, 0.92f, 0.38f)
        s(0.08f, 0.62f, 0.4f, 0.68f, 0.42f, 0.95f); s(0.92f, 0.62f, 0.6f, 0.68f, 0.58f, 0.95f)
    }
    t("roman.saskinlik", "Şaşkınlık çizgileri", COMIC, "Duygu işareti", "Bir noktaya doğru açılan üç kısa çizgi") {
        l(0.1f, 0.15f, 0.32f, 0.55f); l(0.5f, 0f, 0.5f, 0.48f); l(0.9f, 0.15f, 0.68f, 0.55f)
    }
    t("roman.hiz_cizgileri", "Hız çizgileri", COMIC, "Efekt", "Farklı uzunlukta dört paralel yatay çizgi") {
        l(0.2f, 0.1f, 1f, 0.1f); l(0f, 0.37f, 0.9f, 0.37f); l(0.3f, 0.63f, 1f, 0.63f); l(0.1f, 0.9f, 0.8f, 0.9f)
    }
    t("roman.sarmal", "Baş dönmesi (sarmal)", COMIC, "Duygu işareti", "İçten dışa dönen sarmal") {
        curve(96) { tt -> val a = tt * 5f * PI.toFloat(); val r = 0.04f + 0.44f * tt; com.karapp.drawguidance.content.vector.P(0.5f + r * cos(a), 0.5f + r * sin(a)) }
    }
    t("roman.uyku", "Uyku (Zzz)", COMIC, "Duygu işareti", "Küçülerek yükselen üç Z") {
        l(0.02f, 0.62f, 0.38f, 0.62f, 0.02f, 0.98f, 0.38f, 0.98f)
        l(0.44f, 0.32f, 0.7f, 0.32f, 0.44f, 0.58f, 0.7f, 0.58f)
        l(0.76f, 0.02f, 0.98f, 0.02f, 0.76f, 0.24f, 0.98f, 0.24f)
    }
    t("roman.panel_iki", "İki panel", COMIC, "Panel", "Yan yana iki dikdörtgen kare") {
        rect(0f, 0.1f, 0.46f, 0.8f); rect(0.54f, 0.1f, 0.46f, 0.8f)
    }
    t("roman.panel_uc", "Üç panel", COMIC, "Panel", "Üstte geniş, altta iki kare") {
        rect(0f, 0f, 1f, 0.46f); rect(0f, 0.54f, 0.46f, 0.46f); rect(0.54f, 0.54f, 0.46f, 0.46f)
    }
    t("roman.kalp_gozler", "Kalp gözler", COMIC, "Duygu işareti", "Yan yana iki kalp") {
        for (cx in listOf(0.25f, 0.75f)) path(closed = true) {
            moveTo(cx, 0.4f); cubicTo(cx, 0.2f, cx + 0.24f, 0.2f, cx + 0.22f, 0.44f); cubicTo(cx + 0.2f, 0.6f, cx + 0.05f, 0.7f, cx, 0.8f)
            cubicTo(cx - 0.05f, 0.7f, cx - 0.2f, 0.6f, cx - 0.22f, 0.44f); cubicTo(cx - 0.24f, 0.2f, cx, 0.2f, cx, 0.4f)
        }
    }
    t("roman.unlem", "Ünlem", COMIC, "İşaret", "Yukarıdan inen kalın çizgi ve altında nokta") {
        lc(0.38f, 0f, 0.62f, 0f, 0.55f, 0.7f, 0.45f, 0.7f); circle(0.5f, 0.9f, 0.08f)
    }
    t("roman.soru", "Soru işareti", COMIC, "İşaret", "Kıvrık kanca ve altında nokta") {
        s(0.2f, 0.25f, 0.3f, 0.06f, 0.55f, 0.02f, 0.78f, 0.14f, 0.75f, 0.36f, 0.52f, 0.5f, 0.5f, 0.7f); circle(0.5f, 0.9f, 0.07f)
    }
    t("roman.manga_goz", "Manga gözü", COMIC, "Yüz", "İri oval göz, büyük göz bebeği ve iki parıltı") {
        ellipse(0.5f, 0.55f, 0.36f, 0.44f); ellipse(0.5f, 0.62f, 0.24f, 0.32f); circle(0.42f, 0.48f, 0.08f); circle(0.6f, 0.74f, 0.04f)
    }
    t("roman.yildiz_goz", "Baş üstünde yıldızlar", COMIC, "Duygu işareti", "Baş üstünde dönen üç küçük yıldız ve yay") {
        arc(0.5f, 0.75f, 0.48f, 0.2f, 180f, 180f)
        star(0.15f, 0.62f, 0.12f, 0.05f); star(0.5f, 0.42f, 0.12f, 0.05f); star(0.85f, 0.62f, 0.12f, 0.05f)
    }
    // v30 McCloud'un "Lexicon of Comicana" sözlüğündeki simgeler (briffit, crottle, indotherm, lucaflect, solrad,
    // grawlix, agitron, swalloop, plewd): biçimleri yeniden çizildi
    t("roman.toz_bulutu", "Toz bulutu (briffit)", COMIC, "Efekt", "Hızla giden karakterin ardında kalan üç toz topağı") {
        circle(0.2f, 0.72f, 0.17f); circle(0.5f, 0.6f, 0.22f); circle(0.82f, 0.74f, 0.15f)
    }
    t("roman.carpi_gozler", "Bayılma gözleri (X)", COMIC, "Yüz", "Göz yerine iki çarpı (crottle)") {
        l(0.05f, 0.3f, 0.35f, 0.7f); l(0.35f, 0.3f, 0.05f, 0.7f); l(0.65f, 0.3f, 0.95f, 0.7f); l(0.95f, 0.3f, 0.65f, 0.7f)
    }
    t("roman.buhar", "Buhar / sıcaklık", COMIC, "Efekt", "Yükselen üç dalgalı çizgi (indotherm)") {
        for (x in listOf(0.2f, 0.5f, 0.8f)) s(x, 1f, x - 0.08f, 0.75f, x + 0.08f, 0.5f, x - 0.08f, 0.25f, x, 0f)
    }
    t("roman.cam_parlamasi", "Parlama (lucaflect)", COMIC, "Efekt", "Parlak yüzeyde dört bölmeli pencere yansıması") {
        rect(0.1f, 0.1f, 0.8f, 0.8f); l(0.5f, 0.1f, 0.5f, 0.9f); l(0.1f, 0.5f, 0.9f, 0.5f)
    }
    t("roman.isik_cizgileri", "Işık çizgileri (solrad)", COMIC, "Efekt", "Ortadaki boşluktan dışa yayılan sekiz kısa çizgi") {
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4f
            l(0.5f + 0.24f * cos(a), 0.5f + 0.24f * sin(a), 0.5f + 0.48f * cos(a), 0.5f + 0.48f * sin(a))
        }
    }
    t("roman.kufur", "Küfür simgeleri (grawlix)", COMIC, "Balon", "Yan yana #, @ ve * işaretleri") {
        l(0.08f, 0.28f, 0.05f, 0.72f); l(0.2f, 0.28f, 0.17f, 0.72f); l(0f, 0.42f, 0.26f, 0.42f); l(0f, 0.58f, 0.25f, 0.58f)
        circle(0.5f, 0.5f, 0.07f)
        s(0.57f, 0.43f, 0.58f, 0.6f, 0.65f, 0.6f, 0.67f, 0.46f, 0.6f, 0.32f, 0.46f, 0.3f, 0.35f, 0.4f, 0.35f, 0.62f, 0.48f, 0.73f, 0.62f, 0.7f)
        l(0.85f, 0.3f, 0.85f, 0.7f); l(0.7f, 0.4f, 1f, 0.6f); l(0.7f, 0.6f, 1f, 0.4f)
    }
    t("roman.titreme", "Titreme çizgileri (agitron)", COMIC, "Efekt", "Titreyen nesnenin iki yanında zikzaklı çizgiler") {
        l(0.05f, 0.1f, 0.15f, 0.25f, 0.05f, 0.4f, 0.15f, 0.55f, 0.05f, 0.7f, 0.15f, 0.85f)
        l(0.85f, 0.1f, 0.95f, 0.25f, 0.85f, 0.4f, 0.95f, 0.55f, 0.85f, 0.7f, 0.95f, 0.85f)
    }
    t("roman.sallanma", "Sallanma yayları (swalloop)", COMIC, "Efekt", "Hareket eden kolun izinde iç içe iki yay") {
        arc(0.5f, 0.95f, 0.46f, 0.46f, 200f, 140f); arc(0.5f, 0.95f, 0.3f, 0.3f, 210f, 120f)
    }
    t("roman.ter_ucusan", "Uçuşan ter damlaları (plewd)", COMIC, "Duygu işareti", "Dışa doğru uçuşan üç küçük damla") {
        for ((cx, cy) in listOf(0.15f to 0.55f, 0.5f to 0.2f, 0.85f to 0.55f)) path(closed = true) {
            val r = 0.13f
            moveTo(cx, cy - r); cubicTo(cx + r * 0.4f, cy - r * 0.2f, cx + r, cy + r * 0.2f, cx + r * 0.6f, cy + r * 0.8f)
            cubicTo(cx + r * 0.2f, cy + r * 1.2f, cx - r * 0.2f, cy + r * 1.2f, cx - r * 0.6f, cy + r * 0.8f)
            cubicTo(cx - r, cy + r * 0.2f, cx - r * 0.4f, cy - r * 0.2f, cx, cy - r)
        }
    }
}
