package com.karapp.drawguidance.content.library.sketch

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.LibCategory

/**
 * v17 "Çizim yardımcıları" (resim tuvali): renksiz, özgün çizgi çizimler — portreler, insan, taşıtlar, yapılar, doğa
 * (bulut, güneş, ağaç, çalı, çimen), hayvanlar, meyveler, sebzeler ve ev eşyaları. Eski renkli hazır çizimlerin ve
 * temel çizimlerin yerine seçicide bunlar görünür.
 */
object SketchLibrary {

    const val SECTION = "Çizim yardımcıları"

    val categories: List<LibCategory> by lazy {
        listOf(
            LibCategory("sketch_portraits", "Portreler", Audience.ART, PortraitSketches.shapes, SECTION),
            LibCategory("sketch_people", "İnsan", Audience.ART, PeopleSketches.shapes, SECTION),
            LibCategory("sketch_vehicles", "Taşıtlar", Audience.ART, VehicleSketches.shapes, SECTION),
            LibCategory("sketch_buildings", "Yapılar", Audience.ART, BuildingSketches.shapes, SECTION),
            LibCategory("sketch_nature", "Doğa", Audience.ART, NatureSketches.shapes, SECTION),
            LibCategory("sketch_animals", "Hayvanlar", Audience.ART, AnimalSketches.shapes, SECTION),
            LibCategory("sketch_fruits", "Meyveler", Audience.ART, FruitSketches.shapes, SECTION),
            LibCategory("sketch_vegetables", "Sebzeler", Audience.ART, VegetableSketches.shapes, SECTION),
            LibCategory("sketch_household", "Ev eşyaları", Audience.ART, HouseholdSketches.shapes, SECTION),
        ).filter { it.shapes.isNotEmpty() }
    }
}
