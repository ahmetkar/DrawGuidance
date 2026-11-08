package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.library.drawings.ReadyDrawings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hazır çizimler: altı kategori, her birinde 7–10 ayrıntılı (çok parçalı) çizim; kapalı parçaların hepsinin açık
 * dolgusu var (eleman dolgusu çizimi bozmasın); kütüphaneden bulunabiliyorlar. -PpreviewDir ile büyük önizlemeler.
 */
class ReadyDrawingsTest {

    private val expected = linkedMapOf(
        "draw_city" to "Ev ve şehir",
        "draw_nature" to "Doğa",
        "draw_animals" to "Hayvanlar",
        "draw_vehicles" to "Taşıtlar",
        "draw_objects" to "Eşyalar",
        "draw_food" to "Yiyecek",
    )

    @Test
    fun categoriesAndDetail() {
        val cats = ReadyDrawings.categories
        assertEquals(expected.keys.toList(), cats.map { it.id })
        var total = 0
        cats.forEach { c ->
            assertEquals(expected[c.id], c.title)
            assertEquals(Audience.ART, c.audience)
            assertEquals(ReadyDrawings.SECTION, c.section)
            assertTrue("${c.id}: ${c.shapes.size} çizim", c.shapes.size in 7..10)
            c.shapes.forEach { s ->
                total++
                assertTrue(s.id, s.id.startsWith("draw.") && s.id.matches(Regex("^[a-z0-9_.]+$")))
                assertTrue(s.id, s.title.isNotBlank() && s.tags.isNotEmpty())
                assertTrue(s.id, ShapeLibrary.find("lib.${s.id}") === s)
                val paths = s.paths(400f)
                assertTrue("${s.id}: yalnız ${paths.size} parça (ayrıntılı olmalı)", paths.size >= 18)
                paths.forEach { p ->
                    assertTrue("${s.id}: dolgusuz kapalı parça", !p.closed || p.fill != null)
                    assertTrue("${s.id}: nokta yok", p.points.isNotEmpty())
                }
                val points = paths.sumOf { it.points.size }
                assertTrue("${s.id}: çok fazla nokta ($points)", points < 12_000)
            }
        }
        assertTrue("toplam $total", total >= 48)
    }

    @Test
    fun previews() {
        if (Preview.dir == null) return
        ReadyDrawings.categories.forEach { c ->
            Preview.write(Preview.sheet(c, cell = 300, columns = 3), "ready-${c.id}")
            c.shapes.forEach { s -> Preview.write(Preview.shapeImage(s, 480), "draw-${s.id.removePrefix("draw.")}") }
        }
    }
}
