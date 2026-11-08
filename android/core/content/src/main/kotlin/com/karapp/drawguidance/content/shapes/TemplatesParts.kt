package com.karapp.drawguidance.content.shapes

private const val FACE = "Yüz"
private const val BODY = "Vücut"
private const val BUILD = "Yapı"
private const val PLANT = "Bitki"
private const val NATURE = "Gökyüzü ve doğa"
private const val ANIMAL = "Hayvan"
private const val VEHICLE = "Taşıt"
private const val THING = "Nesne"

/**
 * v27 tam figürler (veritabanı sürüm 3): parça önerisinin en çok işe yaradığı, çocukların ve çizim öğrenenlerin sık
 * çizdiği bütünler. Az eleman seçiliyken (ör. yalnız bir çember ya da kare) bu figürlerin eksik parçaları (gözler,
 * kulaklar, kapı, pencereler, tekerlekler, yapraklar…) önerilir; çok eleman seçiliyken bütün figür önerilir. Her parça
 * ayrı bir çizgidir (parça sayısı figürün kendisini tanımlar).
 */
internal fun TemplateScope.partTemplates() {
    t("yuz.tam.kulakli", "Kulaklı yüz", FACE, "Tam yüz", "Baş, iki kulak, iki göz, burun ve gülümseme") {
        circle(0.5f, 0.5f, 0.4f)
        s(0.1f, 0.38f, 0.02f, 0.45f, 0.03f, 0.58f, 0.11f, 0.63f); s(0.9f, 0.38f, 0.98f, 0.45f, 0.97f, 0.58f, 0.89f, 0.63f)
        circle(0.36f, 0.42f, 0.05f); circle(0.64f, 0.42f, 0.05f)
        s(0.5f, 0.47f, 0.47f, 0.58f, 0.53f, 0.6f)
        s(0.34f, 0.7f, 0.5f, 0.78f, 0.66f, 0.7f)
    }
    t("yuz.tam.sacli", "Saçlı yüz", FACE, "Tam yüz", "Baş, üstte saç, kaşlar, gözler ve ağız") {
        ellipse(0.5f, 0.56f, 0.36f, 0.42f)
        s(0.14f, 0.5f, 0.18f, 0.2f, 0.5f, 0.04f, 0.82f, 0.2f, 0.86f, 0.5f)
        s(0.3f, 0.42f, 0.36f, 0.39f, 0.43f, 0.41f); s(0.57f, 0.41f, 0.64f, 0.39f, 0.7f, 0.42f)
        circle(0.37f, 0.5f, 0.04f); circle(0.63f, 0.5f, 0.04f)
        s(0.38f, 0.76f, 0.5f, 0.82f, 0.62f, 0.76f)
    }
    t("insan.basit_govde", "İnsan (gövdeli)", BODY, "Tam figür", "Baş, gövde, iki kol ve iki bacak") {
        circle(0.5f, 0.12f, 0.11f)
        lc(0.38f, 0.26f, 0.62f, 0.26f, 0.66f, 0.62f, 0.34f, 0.62f)
        l(0.38f, 0.3f, 0.18f, 0.5f); l(0.62f, 0.3f, 0.82f, 0.5f)
        l(0.42f, 0.62f, 0.38f, 0.98f); l(0.58f, 0.62f, 0.62f, 0.98f)
    }
    t("yapi.ev.tam", "Ev (kapılı, pencereli)", BUILD, "Ev", "Gövde, üçgen çatı, kapı, iki pencere ve baca") {
        lc(0.1f, 0.45f, 0.9f, 0.45f, 0.9f, 1f, 0.1f, 1f)
        lc(0.04f, 0.47f, 0.5f, 0.08f, 0.96f, 0.47f)
        lc(0.42f, 1f, 0.42f, 0.72f, 0.58f, 0.72f, 0.58f, 1f)
        lc(0.18f, 0.56f, 0.33f, 0.56f, 0.33f, 0.7f, 0.18f, 0.7f); lc(0.67f, 0.56f, 0.82f, 0.56f, 0.82f, 0.7f, 0.67f, 0.7f)
        l(0.7f, 0.29f, 0.7f, 0.12f, 0.8f, 0.12f, 0.8f, 0.37f)
    }
    t("bitki.cicek_saksi", "Saksıda çiçek", PLANT, "Çiçek", "Göbek, beş taç yaprak, sap, yaprak ve saksı") {
        circle(0.5f, 0.24f, 0.07f)
        for (k in 0 until 5) {
            val a = Math.PI * 2 * k / 5 - Math.PI / 2
            ellipse(0.5f + 0.15f * kotlin.math.cos(a).toFloat(), 0.24f + 0.15f * kotlin.math.sin(a).toFloat(), 0.08f, 0.05f, Math.toDegrees(a).toFloat())
        }
        l(0.5f, 0.31f, 0.5f, 0.72f)
        sc(0.5f, 0.56f, 0.62f, 0.46f, 0.74f, 0.47f, 0.64f, 0.56f)
        lc(0.3f, 0.72f, 0.7f, 0.72f, 0.64f, 1f, 0.36f, 1f)
    }
    t("doga.dag_gunes", "Dağ ve güneş", NATURE, "Manzara", "İki tepeli dağ, köşede güneş ve bulut") {
        l(0f, 1f, 0.3f, 0.45f, 0.48f, 0.72f, 0.68f, 0.38f, 1f, 1f)
        circle(0.86f, 0.14f, 0.1f)
        sc(0.12f, 0.2f, 0.18f, 0.1f, 0.3f, 0.09f, 0.38f, 0.16f, 0.36f, 0.24f, 0.14f, 0.25f)
    }
    t("bitki.agac_elmali", "Elma ağacı", PLANT, "Ağaç", "Yuvarlak taç, gövde ve üç elma") {
        sc(0.5f, 0.02f, 0.82f, 0.1f, 0.94f, 0.36f, 0.8f, 0.6f, 0.5f, 0.66f, 0.2f, 0.6f, 0.06f, 0.36f, 0.18f, 0.1f)
        lc(0.42f, 0.64f, 0.58f, 0.64f, 0.6f, 1f, 0.4f, 1f)
        circle(0.34f, 0.3f, 0.05f); circle(0.62f, 0.24f, 0.05f); circle(0.6f, 0.48f, 0.05f)
    }
    t("hayvan.tirtil", "Tırtıl", ANIMAL, "Böcek", "Art arda beş yuvarlak boğum, baş ve iki anten") {
        circle(0.12f, 0.66f, 0.1f); circle(0.3f, 0.62f, 0.1f); circle(0.48f, 0.66f, 0.1f); circle(0.66f, 0.62f, 0.1f)
        circle(0.85f, 0.52f, 0.13f)
        s(0.8f, 0.4f, 0.76f, 0.26f, 0.72f, 0.2f); s(0.9f, 0.4f, 0.94f, 0.26f, 0.98f, 0.2f)
    }
    t("tasit.araba_pencereli", "Araba (pencereli)", VEHICLE, "Araba", "Gövde, kabin, iki pencere ve iki tekerlek") {
        lc(0.02f, 0.55f, 0.98f, 0.55f, 0.98f, 0.8f, 0.02f, 0.8f)
        l(0.2f, 0.55f, 0.32f, 0.28f, 0.7f, 0.28f, 0.82f, 0.55f)
        lc(0.33f, 0.34f, 0.49f, 0.34f, 0.49f, 0.52f, 0.27f, 0.52f); lc(0.53f, 0.34f, 0.68f, 0.34f, 0.75f, 0.52f, 0.53f, 0.52f)
        circle(0.25f, 0.82f, 0.1f); circle(0.75f, 0.82f, 0.1f)
    }
    t("nesne.robot_kafa", "Robot kafası", THING, "Robot", "Kare kafa, anten, iki göz, ağız ızgarası ve kulaklar") {
        lc(0.2f, 0.25f, 0.8f, 0.25f, 0.8f, 0.9f, 0.2f, 0.9f)
        l(0.5f, 0.25f, 0.5f, 0.08f); circle(0.5f, 0.06f, 0.04f)
        circle(0.37f, 0.48f, 0.07f); circle(0.63f, 0.48f, 0.07f)
        lc(0.35f, 0.68f, 0.65f, 0.68f, 0.65f, 0.78f, 0.35f, 0.78f)
        lc(0.12f, 0.45f, 0.2f, 0.45f, 0.2f, 0.65f, 0.12f, 0.65f); lc(0.8f, 0.45f, 0.88f, 0.45f, 0.88f, 0.65f, 0.8f, 0.65f)
    }
}
