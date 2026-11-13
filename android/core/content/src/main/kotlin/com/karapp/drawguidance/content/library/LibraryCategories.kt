package com.karapp.drawguidance.content.library

import com.karapp.drawguidance.content.library.drawings.ReadyDrawings
import com.karapp.drawguidance.content.library.kids.KidsShapes
import com.karapp.drawguidance.content.library.science.ScienceLibrary
import com.karapp.drawguidance.content.library.sketch.SketchLibrary

/** Kütüphanedeki kategorilerin sırası (seçicide bu sırayla görünür). */
internal object LibraryCategories {
    fun all(): List<LibCategory> = listOfNotNull(
        // Fen ve ders şekilleri (not tuvali)
        *ScienceLibrary.categories.toTypedArray(),
        // v43 nesne kütüphanesi (v36) Şekil ekle penceresiyle birlikte tamamen kaldırıldı (çizgi romanda hazır nesne
        // resimleri Nesneler penceresinde)
        // v17 çizim yardımcıları (resim tuvali, renksiz)
        *SketchLibrary.categories.toTypedArray(),
        // v50 yetişkinler için çizim alıştırmaları (resim tuvali; Alıştırmalar görevleri ve şekil önerisi)
        *com.karapp.drawguidance.content.library.practice.PracticeShapes.categories.toTypedArray(),
        // Hazır çizimler: v17'de seçiciden kalktı; eski kayıtlar açılabilsin diye kütüphanede duruyor
        *ReadyDrawings.categories.toTypedArray(),
        // v36 çizgi roman efektleri: seçicide listelenmez (Efektler penceresinden eklenir), kimliğiyle bulunur
        *com.karapp.drawguidance.content.comic.ComicEffects.categories.toTypedArray(),
        // v42 resim tuvallerinin genel şekilleri (çizgiler, oklar, yıldızlar, afişler, akış şeması, konuşma kutuları)
        *com.karapp.drawguidance.content.library.general.GeneralShapes.categories.toTypedArray(),
        // Çocuklar için çizim: boyama kitabı çizimleri (yalnız çocuk modunda, Audience.KIDS)
        *KidsShapes.categories.toTypedArray(),
    )
}
