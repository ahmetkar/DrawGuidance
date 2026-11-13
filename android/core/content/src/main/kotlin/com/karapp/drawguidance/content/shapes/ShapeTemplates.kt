package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder

/**
 * v21 şekil veritabanının kaydı: çizimin elemanı olabilecek bir nesnenin (göz, burun, çatı, kapı, sandalye, meyve,
 * hayvan uzvu…) elle çizilmiş gibi basit, düzenlenebilir çizgilerden oluşan şablonu.
 *
 * - [key] kalıcı kimlik ("yuz.goz.badem"); veritabanında benzersiz.
 * - [name] kullanıcıya gösterilen ad, [category] ve [group] seçici/öneri gruplaması.
 * - [strokes] birim kutuda (0..1, y aşağı) çizgiler; her biri ayrı bir fırça izi olur.
 * - [description] kaydın ne olduğu (belgede ve erişilebilirlikte).
 * - [solid] 3B çizim modunda şablon yerine konabilecek gölgeli cisim (ör. küp çizimi → "SOLID_CUBE").
 */
data class ShapeTemplate(
    val key: String,
    val name: String,
    val category: String,
    val group: String,
    val strokes: List<List<P>>,
    val description: String = "",
    val solid: String? = null,
) {
    val strokeCount: Int get() = strokes.size
}

/** Şablon tanımlarında kullanılan kısa yazımlar (VectorBuilder üzerinde). */
internal class TemplateScope {
    val out = ArrayList<ShapeTemplate>()

    fun t(
        key: String, name: String, category: String, group: String, description: String = "", solid: String? = null,
        draw: VectorBuilder.() -> Unit,
    ) {
        val builder = VectorBuilder(resolution = 256f).apply(draw)
        val strokes = builder.paths.filter { it.points.size >= 2 && it.label == null }.map { p ->
            if (p.closed && p.points.first() != p.points.last()) p.points + p.points.first() else p.points
        }
        require(strokes.isNotEmpty()) { key }
        out += ShapeTemplate(key, name, category, group, strokes, description, solid)
    }
}

/** Açık yumuşak eğri (Catmull-Rom): x1, y1, x2, y2… */
internal fun VectorBuilder.s(vararg xy: Float) = smooth(*xy)

/** Kapalı yumuşak eğri. */
internal fun VectorBuilder.sc(vararg xy: Float) = smooth(*xy, closed = true)

/** Açık kırık çizgi. */
internal fun VectorBuilder.l(vararg xy: Float) = poly(*xy)

/** Kapalı kırık çizgi. */
internal fun VectorBuilder.lc(vararg xy: Float) = poly(*xy, closed = true)

/** Tüm şablonlar (kategori dosyalarından). Sıra ve anahtarlar kalıcıdır (veritabanı sürümü [VERSION]). */
object ShapeTemplates {
    /**
     * Katalog değişince artar: veritabanı yeniden doldurulur (kullanıcının öğrettiği örnekler korunur). v29 (4): moda
     * özel şablonlar ([modeOnly]). v30 (5): araştırmayla eklenen not (görsel not tutma) ve çizgi roman (Lexicon of
     * Comicana) şablonları. v34 (6): çocuk boyama şekillerinden üretilen çocuk modu şablonları ("cocuk."). v37 (7):
     * kütüphaneden üretilen nesne, karakter ve efekt şablonları ([LibraryTemplates]). v50 (8): yetişkin alıştırma
     * şekillerinden üretilen şablonlar ("alistirma.").
     */
    const val VERSION = 8

    val all: List<ShapeTemplate> by lazy {
        val scope = TemplateScope()
        with(scope) {
            faceTemplates()
            hairAndBodyTemplates()
            buildingTemplates()
            furnitureTemplates()
            fruitTemplates()
            vegetableTemplates()
            animalTemplates()
            plantAndSkyTemplates()
            objectTemplates()
            solidTemplates()
            // v23 en çok çizilen şekiller
            commonTemplates()
            // v27 tam figürler (parça önerisi)
            partTemplates()
        }
        val keys = HashSet<String>()
        scope.out.forEach { require(keys.add(it.key)) { "yinelenen şablon: ${it.key}" } }
        scope.out
    }

    /**
     * v29 moda özel şablonlar (not alma: "ders.", çizgi roman: "roman."). [all]'a girmez: genel (modsuz) aramada
     * kullanılmaz, yalnız o modun kapsamında ([ModeCatalog]) aranır. v37: kütüphane nesneleri ("obje.") de buradadır
     * (resim, çizgi roman ve not modlarının kapsamında).
     */
    val modeOnly: List<ShapeTemplate> by lazy {
        val scope = TemplateScope()
        with(scope) {
            notesTemplates()
            comicTemplates()
            // v34 çocuklar için çizimin şablonları (86 boyama şeklinden)
            kidsTemplates()
            // v50 yetişkin alıştırma şekilleri (desenler, motifler, perspektif, konular)
            practiceTemplates()
        }
        // v37 kütüphaneden üretilen nesne, karakter ve efekt şablonları (uygulamayla gelen kaynak dosyasından)
        val out = scope.out + LibraryTemplates.all
        val keys = all.mapTo(HashSet()) { it.key }
        out.forEach { require(keys.add(it.key)) { "yinelenen şablon: ${it.key}" } }
        out
    }

    /** Veritabanına yazılan bütün katalog: genel şablonlar ve moda özel şablonlar. */
    val catalog: List<ShapeTemplate> by lazy { all + modeOnly }

    fun byKey(key: String): ShapeTemplate? = all.firstOrNull { it.key == key } ?: modeOnly.firstOrNull { it.key == key }

    /** Kategoriler (belge ve istatistik için), kayıt sırasıyla. */
    val categories: List<String> get() = all.map { it.category }.distinct()
}
