package com.karapp.drawguidance.content.library.practice

import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.shapes.ModeCatalog
import com.karapp.drawguidance.content.shapes.ShapeMode
import com.karapp.drawguidance.content.shapes.ShapeTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v50 yetişkin alıştırma şekilleri: kütüphanede bulunur, birim kutuda çizilir, şablonları resim modlarında. */
class V50PracticeShapesTest {

    @Test
    fun shapesAreInLibraryAndInsideUnitBox() {
        assertEquals(23, PracticeShapes.all.size)
        for (s in PracticeShapes.all) {
            assertNotNull(s.id, ShapeLibrary.find("lib." + s.id))
            val paths = s.paths(256f)
            assertTrue(s.id, paths.isNotEmpty())
            val pts = paths.flatMap { it.points }
            assertTrue(s.id + " x", pts.all { it.x in -0.02f..1.02f })
            assertTrue(s.id + " y", pts.all { it.y in -0.02f..1.02f })
        }
        assertTrue(PracticeShapes.simpleIds.all { id -> PracticeShapes.all.any { it.id == id } })
    }

    @Test
    fun templatesOnlyInArtModes() {
        val practice = ShapeTemplates.modeOnly.filter { it.key.startsWith(ModeCatalog.PRACTICE_PREFIX) }
        assertEquals(23, practice.size)
        for (t in practice) {
            assertTrue(t.solid!!.startsWith("lib.practice."))
            assertTrue(ModeCatalog.allows(ShapeMode.ART, t.key, t.category))
            assertFalse(ModeCatalog.allows(ShapeMode.KIDS, t.key, t.category))
            assertFalse(ModeCatalog.allows(ShapeMode.NOTES, t.key, t.category))
            assertFalse(ModeCatalog.allows(ShapeMode.COMIC, t.key, t.category))
            val simple = ModeCatalog.allowsSimpleArt(t.key, t.category)
            assertEquals(t.key, t.solid!!.removePrefix("lib.") in PracticeShapes.simpleIds, simple)
        }
    }

    /** Önizleme: build/practice-preview klasöründe SVG (gözle denetim için). */
    @Test
    fun writePreviews() {
        val dir = File("build/practice-preview").apply { mkdirs() }
        for (s in PracticeShapes.all) {
            val paths = s.paths(256f)
            val w = 400f
            val h = 400f / s.aspect
            val sb = StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='$w' height='$h' viewBox='0 0 $w $h'><rect width='100%' height='100%' fill='white'/>")
            for (p in paths) {
                val d = p.points.joinToString(" ") { "${it.x * w},${it.y * h}" }
                val tag = if (p.closed) "polygon" else "polyline"
                sb.append("<$tag points='$d' fill='none' stroke='#222' stroke-width='${2f * p.width}' stroke-linejoin='round' stroke-linecap='round'/>")
            }
            sb.append("</svg>")
            File(dir, s.id.removePrefix("practice.") + ".svg").writeText(sb.toString())
        }
    }
}
