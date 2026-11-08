package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.vector.VectorBuilder

private const val FACE = "Yüz"
private const val HAIR = "Saç"
private const val BODY = "Vücut"

/** Badem göz çerçevesi (üst ve alt kapak tek çizgi). */
private fun VectorBuilder.almond(tilt: Float = 0f, open: Float = 1f) = path(closed = true) {
    moveTo(0f, 0.5f + tilt)
    quadTo(0.5f, 0.5f - 0.62f * open, 1f, 0.5f - tilt)
    quadTo(0.5f, 0.5f + 0.62f * open, 0f, 0.5f + tilt)
}

internal fun TemplateScope.faceTemplates() {
    // --- Göz ---
    t("yuz.goz.badem", "Göz (badem)", FACE, "Göz", "Uçları sivri, badem biçimli göz çerçevesi") { almond() }
    t("yuz.goz.badem_irisli", "Göz (irisli)", FACE, "Göz", "Badem göz ve ortasında iris") {
        almond(); circle(0.5f, 0.5f, 0.2f)
    }
    t("yuz.goz.bebekli", "Göz (iris ve bebek)", FACE, "Göz", "Badem göz, iris ve göz bebeği") {
        almond(); circle(0.5f, 0.5f, 0.2f); circle(0.5f, 0.5f, 0.08f)
    }
    t("yuz.goz.yuvarlak", "Göz (yuvarlak)", FACE, "Göz", "Yuvarlak göz ve alt kısmında iris") {
        circle(0.5f, 0.5f, 0.45f); circle(0.5f, 0.58f, 0.2f)
    }
    t("yuz.goz.cekik", "Göz (çekik)", FACE, "Göz", "Dış köşesi yukarı kalkık göz") {
        path(closed = true) { moveTo(0f, 0.62f); quadTo(0.42f, 0.02f, 1f, 0.3f); quadTo(0.58f, 0.98f, 0f, 0.62f) }
    }
    t("yuz.goz.kapali", "Göz (kapalı)", FACE, "Göz", "Kapalı göz: aşağı kıvrılan kapak ve kirpikler") {
        path { moveTo(0f, 0.25f); quadTo(0.5f, 0.95f, 1f, 0.25f) }
        l(0.25f, 0.58f, 0.18f, 0.85f); l(0.5f, 0.67f, 0.5f, 0.95f); l(0.75f, 0.58f, 0.82f, 0.85f)
    }
    t("yuz.goz.kirpikli", "Göz (kirpikli)", FACE, "Göz", "Badem göz, iris ve üst kapakta kirpikler") {
        almond(); circle(0.5f, 0.5f, 0.18f)
        l(0.12f, 0.36f, 0.02f, 0.18f); l(0.3f, 0.24f, 0.24f, 0.02f); l(0.5f, 0.2f, 0.5f, -0.02f); l(0.7f, 0.24f, 0.76f, 0.02f); l(0.88f, 0.36f, 0.98f, 0.18f)
    }
    t("yuz.goz.yari_kapali", "Göz (yarı kapalı)", FACE, "Göz", "Ağır kapaklı göz: düz üst kapak, alt kapak ve yarım iris") {
        s(0f, 0.45f, 0.5f, 0.3f, 1f, 0.45f)
        path { moveTo(0f, 0.45f); quadTo(0.5f, 1.05f, 1f, 0.45f) }
        arc(0.5f, 0.45f, 0.2f, 0.2f, 0f, 180f)
    }
    t("yuz.goz.gozluk", "Gözlük", FACE, "Göz", "İki yuvarlak cam ve köprü") {
        circle(0.22f, 0.5f, 0.2f); circle(0.78f, 0.5f, 0.2f); s(0.42f, 0.48f, 0.5f, 0.42f, 0.58f, 0.48f)
    }

    // --- Kaş ---
    t("yuz.kas.kavisli", "Kaş (kavisli)", FACE, "Kaş", "Ortası yukarı kavisli kaş") { s(0f, 0.9f, 0.3f, 0.2f, 0.7f, 0.1f, 1f, 0.6f) }
    t("yuz.kas.duz", "Kaş (düz)", FACE, "Kaş", "Neredeyse düz, hafif eğik kaş") { s(0f, 0.7f, 0.5f, 0.35f, 1f, 0.45f) }
    t("yuz.kas.acili", "Kaş (açılı)", FACE, "Kaş", "Tepesi köşeli kaş") { l(0f, 0.95f, 0.65f, 0.05f, 1f, 0.75f) }
    t("yuz.kas.kalin", "Kaş (kalın)", FACE, "Kaş", "Kalın, dolgun kaş çerçevesi") {
        sc(0f, 0.9f, 0.3f, 0.15f, 0.7f, 0.05f, 1f, 0.55f, 0.7f, 0.4f, 0.3f, 0.5f)
    }

    // --- Burun ---
    t("yuz.burun.dugme", "Burun (düğme)", FACE, "Burun", "Küçük yuvarlak burun ucu ve iki burun deliği") {
        s(0.15f, 0.45f, 0.5f, 0.95f, 0.85f, 0.45f); arc(0.32f, 0.55f, 0.08f, 0.06f, 0f, 360f); arc(0.68f, 0.55f, 0.08f, 0.06f, 0f, 360f)
    }
    t("yuz.burun.duz", "Burun (düz, önden)", FACE, "Burun", "Burun sırtı çizgisi ve alt kıvrım") {
        s(0.52f, 0f, 0.5f, 0.35f, 0.44f, 0.7f); s(0.15f, 0.72f, 0.3f, 0.95f, 0.5f, 0.85f, 0.7f, 0.95f, 0.85f, 0.72f)
    }
    t("yuz.burun.yandan", "Burun (yandan)", FACE, "Burun", "Profilden burun: sırt, uç ve kanat") {
        s(0.25f, 0f, 0.4f, 0.4f, 0.9f, 0.82f, 0.55f, 0.95f, 0.35f, 0.85f)
    }
    t("yuz.burun.kemerli", "Burun (kemerli)", FACE, "Burun", "Sırtı çıkıntılı, ucu aşağı dönük profil") {
        s(0.2f, 0f, 0.58f, 0.25f, 0.8f, 0.75f, 0.45f, 0.95f, 0.28f, 0.85f)
    }
    t("yuz.burun.kalkik", "Burun (kalkık)", FACE, "Burun", "Ucu yukarı kalkık profil") {
        s(0.3f, 0f, 0.32f, 0.5f, 0.7f, 0.72f, 0.95f, 0.58f, 0.65f, 0.95f, 0.35f, 0.88f)
    }
    t("yuz.burun.genis", "Burun (geniş)", FACE, "Burun", "Geniş burun ucu ve yanlarda kanatlar") {
        arc(0.5f, 0.6f, 0.2f, 0.18f, 20f, 140f); arc(0.18f, 0.62f, 0.16f, 0.2f, 60f, 200f); arc(0.82f, 0.62f, 0.16f, 0.2f, -80f, 200f)
    }

    // --- Ağız ---
    t("yuz.agiz.gulumseme", "Ağız (gülümseme)", FACE, "Ağız", "Yukarı kıvrılan gülümseme") { path { moveTo(0f, 0.15f); quadTo(0.5f, 1.25f, 1f, 0.15f) } }
    t("yuz.agiz.uzgun", "Ağız (üzgün)", FACE, "Ağız", "Aşağı kıvrılan ağız") { path { moveTo(0f, 0.85f); quadTo(0.5f, -0.25f, 1f, 0.85f) } }
    t("yuz.agiz.kahkaha", "Ağız (kahkaha)", FACE, "Ağız", "Açık gülen ağız (D biçimi)") {
        path(closed = true) { moveTo(0f, 0.1f); lineTo(1f, 0.1f); quadTo(0.5f, 1.35f, 0f, 0.1f) }
    }
    t("yuz.agiz.disli", "Ağız (dişli gülüş)", FACE, "Ağız", "Gülen ağız ve üst dişler çizgisi") {
        path(closed = true) { moveTo(0f, 0.1f); lineTo(1f, 0.1f); quadTo(0.5f, 1.35f, 0f, 0.1f) }
        l(0.08f, 0.35f, 0.92f, 0.35f)
    }
    t("yuz.agiz.dilli", "Ağız (dil çıkaran)", FACE, "Ağız", "Açık ağız ve sarkan dil") {
        path(closed = true) { moveTo(0f, 0.1f); lineTo(1f, 0.1f); quadTo(0.5f, 1.2f, 0f, 0.1f) }
        path { moveTo(0.3f, 0.5f); quadTo(0.5f, 1.35f, 0.7f, 0.5f) }
    }
    t("yuz.agiz.dudak", "Dudaklar", FACE, "Ağız", "Yay biçimli üst dudak, dolgun alt dudak ve orta çizgi") {
        sc(0f, 0.5f, 0.22f, 0.2f, 0.4f, 0.16f, 0.5f, 0.28f, 0.6f, 0.16f, 0.78f, 0.2f, 1f, 0.5f, 0.76f, 0.86f, 0.5f, 0.94f, 0.24f, 0.86f)
        s(0f, 0.5f, 0.5f, 0.56f, 1f, 0.5f)
    }
    t("yuz.agiz.dalgali", "Ağız (şaşkın, dalgalı)", FACE, "Ağız", "Titrek, dalgalı ağız çizgisi") { s(0f, 0.5f, 0.2f, 0.3f, 0.4f, 0.7f, 0.6f, 0.3f, 0.8f, 0.7f, 1f, 0.5f) }
    t("yuz.agiz.opucuk", "Ağız (öpücük)", FACE, "Ağız", "Büzülmüş dudaklar (iki küçük yay)") {
        path { moveTo(0.1f, 0.5f); cubicTo(0.2f, -0.1f, 0.9f, 0.1f, 0.4f, 0.5f) }
        path { moveTo(0.4f, 0.5f); cubicTo(0.9f, 0.9f, 0.2f, 1.1f, 0.1f, 0.5f) }
    }

    // --- Kulak, bıyık, sakal ---
    t("yuz.kulak.onden", "Kulak", FACE, "Kulak", "Kulak kepçesi ve iç kıvrım") {
        path { moveTo(0.25f, 0.05f); cubicTo(1.05f, -0.1f, 1.05f, 0.8f, 0.4f, 0.97f) }
        s(0.4f, 0.3f, 0.65f, 0.25f, 0.7f, 0.5f, 0.5f, 0.72f)
    }
    t("yuz.kulak.basit", "Kulak (C)", FACE, "Kulak", "C biçimli basit kulak") { arc(0.2f, 0.5f, 0.7f, 0.48f, -90f, 180f) }
    t("yuz.biyik", "Bıyık", FACE, "Bıyık/Sakal", "Uçları kıvrık, iki yana açılan bıyık") {
        sc(0.5f, 0.35f, 0.3f, 0.2f, 0.1f, 0.35f, 0f, 0.65f, 0.15f, 0.55f, 0.32f, 0.55f, 0.5f, 0.6f, 0.68f, 0.55f, 0.85f, 0.55f, 1f, 0.65f, 0.9f, 0.35f, 0.7f, 0.2f)
    }
    t("yuz.sakal", "Sakal", FACE, "Bıyık/Sakal", "Çeneyi saran U biçimli sakal") {
        path { moveTo(0f, 0f); quadTo(0.02f, 1.1f, 0.5f, 1f); quadTo(0.98f, 1.1f, 1f, 0f) }
    }
    t("yuz.keci_sakal", "Keçi sakalı", FACE, "Bıyık/Sakal", "Çenede sivri sakal") { sc(0.2f, 0f, 0.8f, 0f, 0.62f, 0.6f, 0.5f, 1f, 0.38f, 0.6f) }

    // --- Yüzler (birden çok eleman) ---
    fun VectorBuilder.head() = circle(0.5f, 0.5f, 0.48f)
    t("yuz.ifade.gulen", "Gülen yüz", FACE, "Yüz ifadesi", "Yuvarlak yüz, iki göz ve gülümseme") {
        head(); circle(0.34f, 0.38f, 0.05f); circle(0.66f, 0.38f, 0.05f); path { moveTo(0.28f, 0.6f); quadTo(0.5f, 0.88f, 0.72f, 0.6f) }
    }
    t("yuz.ifade.uzgun", "Üzgün yüz", FACE, "Yüz ifadesi", "Yuvarlak yüz, iki göz ve aşağı kıvrık ağız") {
        head(); circle(0.34f, 0.38f, 0.05f); circle(0.66f, 0.38f, 0.05f); path { moveTo(0.28f, 0.75f); quadTo(0.5f, 0.52f, 0.72f, 0.75f) }
    }
    t("yuz.ifade.saskin", "Şaşkın yüz", FACE, "Yüz ifadesi", "Yuvarlak yüz, iri gözler ve O biçimli ağız") {
        head(); circle(0.34f, 0.38f, 0.08f); circle(0.66f, 0.38f, 0.08f); circle(0.5f, 0.7f, 0.09f)
    }
    t("yuz.ifade.goz_kirpan", "Göz kırpan yüz", FACE, "Yüz ifadesi", "Bir göz açık, bir göz kırpık, gülümseme") {
        head(); circle(0.34f, 0.38f, 0.05f); path { moveTo(0.58f, 0.36f); quadTo(0.66f, 0.46f, 0.74f, 0.36f) }
        path { moveTo(0.28f, 0.6f); quadTo(0.5f, 0.88f, 0.72f, 0.6f) }
    }
    t("yuz.ifade.notr", "Nötr yüz", FACE, "Yüz ifadesi", "Yuvarlak yüz, iki göz ve düz ağız") {
        head(); circle(0.34f, 0.38f, 0.05f); circle(0.66f, 0.38f, 0.05f); l(0.3f, 0.68f, 0.7f, 0.68f)
    }
    t("yuz.oval_yuz", "Yüz (oval, çene)", FACE, "Yüz şekli", "Oval yüz hattı ve sivri çene") {
        sc(0.5f, 0f, 0.9f, 0.18f, 0.95f, 0.55f, 0.72f, 0.88f, 0.5f, 1f, 0.28f, 0.88f, 0.05f, 0.55f, 0.1f, 0.18f)
    }
    t("yuz.kare_yuz", "Yüz (köşeli çene)", FACE, "Yüz şekli", "Geniş, köşeli çeneli yüz hattı") {
        sc(0.5f, 0f, 0.92f, 0.12f, 0.96f, 0.6f, 0.85f, 0.9f, 0.5f, 1f, 0.15f, 0.9f, 0.04f, 0.6f, 0.08f, 0.12f)
    }
}

internal fun TemplateScope.hairAndBodyTemplates() {
    fun VectorBuilder.headArc() = arc(0.5f, 0.75f, 0.45f, 0.5f, 180f, 180f)
    t("sac.duz_kakul", "Saç (düz kakül)", HAIR, "Saç", "Başı saran saç çizgisi ve alna düşen düz kakül") {
        headArc(); l(0.1f, 0.5f, 0.9f, 0.5f)
        l(0.25f, 0.28f, 0.25f, 0.5f); l(0.4f, 0.26f, 0.4f, 0.5f); l(0.55f, 0.26f, 0.55f, 0.5f); l(0.7f, 0.28f, 0.7f, 0.5f)
    }
    t("sac.dalgali", "Saç (dalgalı)", HAIR, "Saç", "Omuza dökülen dalgalı saç") {
        s(0.05f, 1f, 0.1f, 0.8f, 0.02f, 0.6f, 0.1f, 0.4f, 0.05f, 0.25f, 0.3f, 0.02f, 0.7f, 0.02f, 0.95f, 0.25f, 0.9f, 0.4f, 0.98f, 0.6f, 0.9f, 0.8f, 0.95f, 1f)
    }
    t("sac.kivircik", "Saç (kıvırcık)", HAIR, "Saç", "Başın üstünde tomurcuk tomurcuk kıvırcık saç") {
        path(closed = false) {
            moveTo(0f, 0.9f)
            val bumps = 7
            for (i in 0 until bumps) {
                val a0 = Math.toRadians(180.0 + 180.0 * i / bumps); val a1 = Math.toRadians(180.0 + 180.0 * (i + 1) / bumps)
                val am = (a0 + a1) / 2
                val x1 = (0.5 + 0.5 * kotlin.math.cos(a1)).toFloat(); val y1 = (0.9 + 0.8 * kotlin.math.sin(a1)).toFloat()
                val cx = (0.5 + 0.68 * kotlin.math.cos(am)).toFloat(); val cy = (0.9 + 1.0 * kotlin.math.sin(am)).toFloat()
                quadTo(cx, cy, x1, y1)
            }
        }
    }
    t("sac.topuz", "Saç (topuz)", HAIR, "Saç", "Başın üstünde yuvarlak topuz") { arc(0.5f, 0.85f, 0.45f, 0.5f, 180f, 180f); circle(0.5f, 0.2f, 0.16f) }
    t("sac.kirpi", "Saç (diken diken)", HAIR, "Saç", "Yukarı doğru sivri, diken diken saç") {
        l(0f, 0.95f, 0.08f, 0.35f, 0.2f, 0.7f, 0.3f, 0.1f, 0.42f, 0.62f, 0.5f, 0f, 0.58f, 0.62f, 0.7f, 0.1f, 0.8f, 0.7f, 0.92f, 0.35f, 1f, 0.95f)
    }
    t("sac.atkuyrugu", "Saç (at kuyruğu)", HAIR, "Saç", "Başın arkasından sarkan at kuyruğu") {
        arc(0.4f, 0.55f, 0.38f, 0.45f, 180f, 200f); sc(0.72f, 0.35f, 0.95f, 0.5f, 0.98f, 0.8f, 0.85f, 1f, 0.8f, 0.6f)
    }
    t("sac.kisa", "Saç (kısa)", HAIR, "Saç", "Başı kapak gibi saran kısa saç") {
        path(closed = true) { moveTo(0f, 0.9f); cubicTo(-0.05f, -0.15f, 1.05f, -0.15f, 1f, 0.9f); quadTo(0.85f, 0.45f, 0.5f, 0.42f); quadTo(0.15f, 0.45f, 0f, 0.9f) }
    }
    t("sac.uzun", "Saç (uzun)", HAIR, "Saç", "Yüzün iki yanından aşağı inen uzun saç") {
        path(closed = true) {
            moveTo(0.08f, 1f); cubicTo(-0.05f, 0.3f, 0.1f, 0f, 0.5f, 0f); cubicTo(0.9f, 0f, 1.05f, 0.3f, 0.92f, 1f)
            lineTo(0.78f, 1f); cubicTo(0.85f, 0.5f, 0.75f, 0.25f, 0.5f, 0.25f); cubicTo(0.25f, 0.25f, 0.15f, 0.5f, 0.22f, 1f); close()
        }
    }
    t("sac.iki_yandan", "Saç (iki yandan toplu)", HAIR, "Saç", "Baş hattı ve iki yanda toplu saç") {
        arc(0.5f, 0.6f, 0.35f, 0.45f, 180f, 180f); sc(0.15f, 0.5f, 0f, 0.65f, 0.05f, 0.95f, 0.18f, 0.75f); sc(0.85f, 0.5f, 1f, 0.65f, 0.95f, 0.95f, 0.82f, 0.75f)
    }
    t("sac.afro", "Saç (afro)", HAIR, "Saç", "Başı saran iri, yuvarlak kabarık saç ve yüz") {
        path(closed = true) {
            moveTo(1f, 0.5f)
            for (i in 1..12) {
                val a = Math.toRadians(360.0 * i / 12); val am = Math.toRadians(360.0 * (i - 0.5) / 12)
                quadTo((0.5 + 0.62 * kotlin.math.cos(am)).toFloat(), (0.5 + 0.62 * kotlin.math.sin(am)).toFloat(), (0.5 + 0.5 * kotlin.math.cos(a)).toFloat(), (0.5 + 0.5 * kotlin.math.sin(a)).toFloat())
            }
        }
        ellipse(0.5f, 0.6f, 0.25f, 0.3f)
    }

    // --- Vücut ---
    t("vucut.el", "El", BODY, "El/Ayak", "Avuç ve beş parmaklı el") {
        sc(0.2f, 1f, 0.15f, 0.6f, 0.02f, 0.45f, 0.08f, 0.38f, 0.22f, 0.5f, 0.22f, 0.1f, 0.3f, 0.05f, 0.36f, 0.12f, 0.38f, 0.42f, 0.42f, 0.02f,
            0.5f, 0f, 0.56f, 0.05f, 0.56f, 0.42f, 0.62f, 0.08f, 0.7f, 0.06f, 0.75f, 0.12f, 0.72f, 0.46f, 0.8f, 0.2f, 0.88f, 0.2f, 0.9f, 0.3f, 0.82f, 0.62f, 0.75f, 1f)
    }
    t("vucut.ayak_izi", "Ayak izi", BODY, "El/Ayak", "Taban ve beş parmak izi") {
        sc(0.3f, 0.35f, 0.62f, 0.3f, 0.7f, 0.55f, 0.62f, 0.8f, 0.55f, 1f, 0.35f, 0.98f, 0.3f, 0.75f, 0.24f, 0.55f)
        circle(0.3f, 0.2f, 0.08f); circle(0.46f, 0.12f, 0.07f); circle(0.6f, 0.13f, 0.06f); circle(0.72f, 0.18f, 0.05f); circle(0.8f, 0.27f, 0.045f)
    }
    t("vucut.cop_adam", "Çöp adam", BODY, "Figür", "Yuvarlak baş, gövde, kollar ve bacaklar") {
        circle(0.5f, 0.14f, 0.13f); l(0.5f, 0.27f, 0.5f, 0.66f); l(0.15f, 0.42f, 0.85f, 0.42f); l(0.22f, 1f, 0.5f, 0.66f, 0.78f, 1f)
    }
    t("vucut.kol", "Kol (bükük)", BODY, "Figür", "Dirsekten bükülmüş kol, iki çizgi") {
        s(0f, 0.2f, 0.45f, 0.35f, 0.7f, 0.1f); s(0f, 0.45f, 0.55f, 0.6f, 0.95f, 0.15f)
    }
    t("vucut.bacak", "Bacak", BODY, "Figür", "Diz ve ayakla bacak") { s(0.3f, 0f, 0.35f, 0.5f, 0.3f, 0.95f, 0.7f, 1f); s(0.6f, 0f, 0.6f, 0.5f, 0.5f, 0.85f, 0.95f, 0.9f) }
}
