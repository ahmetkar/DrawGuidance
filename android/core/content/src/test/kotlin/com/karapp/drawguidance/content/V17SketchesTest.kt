package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.library.sketch.SketchLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v17 çizim yardımcıları: resim tuvali için renksiz (dolgusuz, yalnız kalem rengi) özgün çizimler; kategoriler ve
 * her çizim kütüphaneden bulunur; kılavuz çizgileri etiketli. -PpreviewDir ile önizleme sayfaları.
 */
class V17SketchesTest {

    @Test
    fun uncoloredCategories() {
        val cats = SketchLibrary.categories
        assertTrue(cats.isNotEmpty())
        var total = 0
        cats.forEach { c ->
            assertEquals(Audience.ART, c.audience)
            assertEquals(SketchLibrary.SECTION, c.section)
            assertTrue("${c.id}: ${c.shapes.size}", c.shapes.size >= 5)
            c.shapes.forEach { s ->
                total++
                assertTrue(s.id, s.id.startsWith("sketch.") && s.id.matches(Regex("^[a-z0-9_.]+$")))
                assertTrue(s.id, s.title.isNotBlank() && s.tags.isNotEmpty())
                assertTrue(s.id, ShapeLibrary.find("lib.${s.id}") === s)
                val paths = s.paths(400f)
                assertTrue("${s.id}: az parça", paths.size >= 3)
                paths.forEach { p ->
                    // Renksiz: dolgu yok, çizgi rengi kalem rengi
                    assertEquals("${s.id}: dolgu", null, p.fill)
                    assertEquals("${s.id}: renk", null, p.stroke)
                    assertTrue("${s.id}: nokta yok", p.points.isNotEmpty())
                    p.points.forEach { pt -> assertTrue("${s.id}: kutu dışı $pt", pt.x in -0.02f..1.02f && pt.y in -0.02f..1.02f) }
                }
                assertTrue("${s.id}: çok fazla nokta", paths.sumOf { it.points.size } < 12_000)
            }
        }
        assertTrue("toplam $total", total >= 5)
    }

    @Test
    fun previews() {
        if (Preview.dir == null) return
        SketchLibrary.categories.forEach { c ->
            Preview.write(Preview.sheet(c, cell = 260, columns = 4), "sketch-${c.id}")
        }
    }
}
