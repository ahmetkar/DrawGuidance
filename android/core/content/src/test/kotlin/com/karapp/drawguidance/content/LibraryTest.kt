package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.vector.bounds
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Kütüphanedeki tüm şekiller: kimlikler tekil ve kayıt dostu, her şekil en az bir parça çizer, noktalar sonlu ve
 * birim kutunun makul çevresinde, renkler geçerli. -PpreviewDir verilirse her kategorinin sayfası PNG olarak yazılır.
 */
class LibraryTest {

    private val hex = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
    private val shade = Regex("^@shade:[01]\\.[0-9]{2}$")

    @Test
    fun everyShapeIsValid() {
        val ids = HashSet<String>()
        ShapeLibrary.categories.forEach { cat ->
            assertTrue("boş kategori ${cat.id}", cat.shapes.isNotEmpty())
            cat.shapes.forEach { s ->
                if (!ids.add(s.id)) fail("yinelenen kimlik ${s.id}")
                assertTrue("kimlik biçimi ${s.id}", s.id.matches(Regex("^[a-z0-9_.]+$")))
                val paths = s.paths(400f)
                assertTrue("${s.id} boş", paths.any { it.label == null && it.points.size >= 2 })
                paths.forEach { p ->
                    p.points.forEach { pt ->
                        if (!pt.x.isFinite() || !pt.y.isFinite()) fail("${s.id}: sonlu olmayan nokta")
                    }
                    listOfNotNull(p.fill, p.stroke).forEach { c ->
                        // v36 nesne ve efekt çizimleri: "@none" dolgusuz dış hat, "@shade:x" elemanın renginin tonu
                        assertTrue("${s.id}: renk $c", c == "@ink" || c == "@none" || shade.matches(c) || hex.matches(c))
                    }
                }
                val b = paths.bounds()
                assertTrue("${s.id} kutu dışında: ${b.toList()}", b[0] > -0.15f && b[1] > -0.15f && b[2] < 1.15f && b[3] < 1.15f)
                assertTrue("${s.id} çok küçük: ${b.toList()}", b[2] - b[0] > 0.3f || b[3] - b[1] > 0.3f)
            }
        }
    }

    @Test
    fun previewSheets() {
        if (Preview.dir == null) return
        ShapeLibrary.categories.forEach { Preview.write(Preview.sheet(it), "lib-${it.id}") }
    }
}
