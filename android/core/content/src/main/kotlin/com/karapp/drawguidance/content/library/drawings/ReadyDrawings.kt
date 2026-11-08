package com.karapp.drawguidance.content.library.drawings

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.LibCategory

/**
 * "Hazır çizimler": resim tuvali için çok parçalı, ayrıntılı, renkli illüstrasyonlar. Her çizim kendi en/boy
 * oranında tasarım biriminde çizilir ([drawing]); parçalar düz dolgu + kalem rengi kenar (iç ayrıntılar ince),
 * yarı saydam gölge/parlama parçalarıyla. Tüm tasarımlar özgündür. v17: seçicide gösterilmez (yerini renksiz çizim
 * yardımcıları aldı); eski kayıtlardaki çizimler açılabilsin diye kütüphanede durur.
 */
object ReadyDrawings {

    const val SECTION = "Hazır çizimler"

    val categories: List<LibCategory> by lazy {
        listOf(
            LibCategory("draw_city", "Ev ve şehir", Audience.ART, CityDrawings.shapes, SECTION, listed = false),
            LibCategory("draw_nature", "Doğa", Audience.ART, NatureDrawings.shapes, SECTION, listed = false),
            LibCategory("draw_animals", "Hayvanlar", Audience.ART, AnimalDrawings.shapes, SECTION, listed = false),
            LibCategory("draw_vehicles", "Taşıtlar", Audience.ART, VehicleDrawings.shapes, SECTION, listed = false),
            LibCategory("draw_objects", "Eşyalar", Audience.ART, ObjectDrawings.shapes, SECTION, listed = false),
            LibCategory("draw_food", "Yiyecek", Audience.ART, FoodDrawings.shapes, SECTION, listed = false),
        ).filter { it.shapes.isNotEmpty() }
    }
}
