package com.karapp.drawguidance

import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.shapes.ModeCatalog
import com.karapp.drawguidance.content.shapes.ShapeMode
import com.karapp.drawguidance.content.shapes.ShapeTemplates
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.shapeMode
import com.karapp.drawguidance.ui.screens.canvasedit.state.ShapeSuggester
import com.karapp.drawguidance.ui.screens.canvasedit.state.SimpleSuggest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v34 basit türlerde yapay zekâ: çocuk modunun kendi kapsamı (86 çocuk şablonu), basit resmin sade kapsamı, balonda
 * 8 seçenek ve çizime benzeyen, türün kendi kataloğundaki şekillerin önerilmesi.
 */
class V34AiTest {

    private fun ink(id: String, pts: List<Pair<Float, Float>>) =
        Stroke(id, pts.map { StrokePoint(it.first, it.second) }, "#112233", BrushType.MARKER, 6f, 1f, 0, null)

    /** Şeklin dış hatlarını elle çizilmiş gibi (hafif kaydırılmış) izler. */
    private fun drawn(id: String, frame: Rect): List<Stroke> =
        ShapeSuggester.outline(id, frame).mapIndexed { i, s -> ink("s$i", s.mapIndexed { k, p -> (p.x + (k % 3 - 1) * 0.8f) to (p.y + (k % 2) * 0.8f) }) }

    @Test
    fun kidsHaveOwnModeAndTemplates() {
        assertEquals(ShapeMode.KIDS, CanvasKind.KIDS.shapeMode())
        assertEquals(ShapeMode.ART, CanvasKind.SIMPLE_ART.shapeMode())
        val kids = ShapeTemplates.catalog.filter { ModeCatalog.allows(ShapeMode.KIDS, it.key, it.category) }
        assertEquals(86, kids.count { it.key.startsWith(ModeCatalog.KIDS_PREFIX) })
        assertTrue(kids.none { it.key.startsWith(ModeCatalog.NOTES_PREFIX) || it.key.startsWith(ModeCatalog.COMIC_PREFIX) })
        assertTrue("çocuk şablonları gelişmiş resimde aranmaz", ShapeTemplates.catalog.none { it.key.startsWith(ModeCatalog.KIDS_PREFIX) && ModeCatalog.allows(ShapeMode.ART, it.key, it.category) })
        assertTrue(kids.size > 150)
        // Basit resim: sade konular (parça/teknik kategoriler yok)
        assertTrue(!ModeCatalog.allowsSimpleArt("x", "Saç") && ModeCatalog.allowsSimpleArt("x", "Hayvan"))
    }

    @Test
    fun simpleKindsGetMoreSuggestions() {
        assertEquals(8, SimpleSuggest.maxTemplates(CanvasKind.KIDS))
        assertEquals(8, SimpleSuggest.maxTemplates(CanvasKind.SIMPLE_NOTES))
        assertEquals(4, SimpleSuggest.maxTemplates(CanvasKind.ART))
        assertEquals(86, SimpleSuggest.catalogIds(CanvasKind.KIDS).size)
        assertEquals(ShapeLibrary.forAudience(Audience.KIDS).sumOf { it.shapes.size }, SimpleSuggest.catalogIds(CanvasKind.KIDS).size)
        assertTrue(SimpleSuggest.catalogIds(CanvasKind.SIMPLE_ART).size >= 40)
    }

    @Test
    fun kidsDrawingSuggestsKidsShapes() {
        // Çocuk bir sevimli canavarın ana hatlarını çiziyor → çocuk kataloğundan o şekil (ve benzerleri) önerilir
        for (id in listOf("lib.kid_cute_monster", "lib.kid_castle", "lib.kid_crown")) {
            val fixes = SimpleSuggest.vectorFixes(CanvasKind.KIDS, drawn(id, Rect(100f, 100f, 400f, 400f)))
            assertTrue("$id: ${fixes.map { it.solid }}", fixes.isNotEmpty() && fixes.size <= 4)
            assertEquals(id, fixes.first().solid)
            assertTrue(fixes.all { it.vector && it.solid!!.startsWith("lib.kid_") })
        }
        // Gelişmiş türde türün kataloğundan öneri yok
        assertTrue(SimpleSuggest.vectorFixes(CanvasKind.ART, drawn("lib.kid_castle", Rect(0f, 0f, 300f, 300f))).isEmpty())
    }

    @Test
    fun simpleNotesSuggestsGeometry() {
        val tri = listOf(ink("t", listOf(100f to 100f, 100f to 300f, 300f to 300f, 100f to 100f)))
        val fixes = SimpleSuggest.vectorFixes(CanvasKind.SIMPLE_NOTES, tri)
        assertTrue(fixes.map { it.solid }.toString(), fixes.any { it.solid == "RIGHT_TRIANGLE" })
    }
}
