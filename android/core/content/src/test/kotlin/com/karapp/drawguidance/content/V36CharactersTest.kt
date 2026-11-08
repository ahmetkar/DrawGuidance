package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.catalog.CatalogCodec
import com.karapp.drawguidance.content.comic.CharExpression
import com.karapp.drawguidance.content.comic.CharKind
import com.karapp.drawguidance.content.comic.CharPose
import com.karapp.drawguidance.content.comic.CharacterDef
import com.karapp.drawguidance.content.comic.ComicCharacters
import com.karapp.drawguidance.content.library.ShapeLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * v36 (8.5) karakterler: 15 tür, 8 ifade, 9 poz (1080 özgün karakter; v37'de 13 ifade, 26 poz: 5070); kimlikler geri çözülür ve kütüphanede bulunur
 * (seçici listesinde ve sunucu kataloğunda yok); her karakter kutusunun içinde, boş değil ve ifade/poz birbirinden
 * farklı; arama Türkçe adlarla ve çeviriyle, süzgeçler birlikte çalışır. -PpreviewDir ile önizleme sayfaları
 * (v36-characters-*.png).
 */
class V36CharactersTest {

    @Test
    fun countsIdsAndLibrary() {
        assertEquals(15, CharKind.entries.size)
        // v37: 13 ifade, 26 poz (v36'nın 8 ifadesi ve 9 pozu aynı kimliklerle başta)
        assertEquals(13, CharExpression.entries.size)
        assertEquals(26, CharPose.entries.size)
        val all = ComicCharacters.all
        assertEquals(15 * 13 * 26, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        for (d in all.filterIndexed { i, _ -> i % 37 == 0 }) {
            assertEquals(d, ComicCharacters.parse(d.id))
            assertEquals(d, ComicCharacters.parse(ShapeLibrary.PREFIX + d.id))
            val lib = ShapeLibrary.find(ShapeLibrary.PREFIX + d.id)
            assertNotNull(d.id, lib)
            assertEquals(d.kind.title, lib!!.title)
        }
        // Aynalı hal: kimlik ".m" ekli, aynı kutu, x ekseninde ters
        val point = ComicCharacters.def(CharKind.HERO, CharExpression.HAPPY, CharPose.POINT)
        val mirror = point.mirror()
        assertEquals("chr.hero.happy.point.m", mirror.id)
        assertEquals(mirror, ComicCharacters.parse("lib.chr.hero.happy.point.m"))
        assertEquals(point, mirror.mirror())
        assertTrue(point.bounds.contentEquals(mirror.bounds))
        val a = ComicCharacters.shape(point.id)!!.paths(64f)
        val m = ComicCharacters.shape(mirror.id)!!.paths(64f)
        assertEquals(a.size, m.size)
        a.zip(m).forEach { (p, q) -> p.points.zip(q.points).forEach { (u, v) -> assertEquals(1f - u.x, v.x, 1e-3f); assertEquals(u.y, v.y, 1e-3f) } }
        assertNull(ComicCharacters.parse("chr.kid.happy.stand.x"))
        assertNull(ComicCharacters.parse("chr.kid.happy"))
        assertNull(ComicCharacters.parse("chr.kid.happy.dance"))
        assertNull(ShapeLibrary.find("lib.chr.alien.happy.stand"))
        // Seçici kategorilerinde ve sunucu kataloğunda yok
        assertTrue(ShapeLibrary.bundledCategories.none { c -> c.shapes.any { it.id.startsWith(ComicCharacters.ID_PREFIX) } })
        assertTrue(CatalogCodec.bundledVectors().none { it.id.startsWith(ComicCharacters.ID_PREFIX) })
    }

    @Test
    fun everyCharacterIsDrawnInsideItsBox() {
        val t0 = System.nanoTime()
        var total = 0
        for (d in ComicCharacters.all) {
            val paths = ComicCharacters.shape(d.id)!!.paths(96f)
            assertTrue(d.id, paths.size > 20)
            for (p in paths) for (q in p.points) {
                assertTrue("${d.id} kutu dışında: $q", q.x in -0.02f..1.02f && q.y in -0.02f..1.02f)
            }
            val b = d.bounds
            assertTrue("${d.id} boy ${b[3]} en ${b[2]}", b[3] in 100f..290f && b[2] in 40f..250f)
            total += paths.sumOf { it.points.size }
        }
        println("${ComicCharacters.all.size} karakter: ${(System.nanoTime() - t0) / 1_000_000} ms, $total nokta")
    }

    @Test
    fun expressionsAndPosesDiffer() {
        fun sig(d: CharacterDef) = ComicCharacters.shape(d.id)!!.paths(64f).joinToString { p -> p.points.joinToString { "${(it.x * 500).toInt()},${(it.y * 500).toInt()}" } }
        for (k in listOf(CharKind.KID, CharKind.ROBOT, CharKind.WIZARD)) {
            val faces = CharExpression.entries.map { sig(ComicCharacters.def(k, it, CharPose.STAND)) }
            assertEquals("$k ifadeler", faces.size, faces.toSet().size)
            val poses = CharPose.entries.map { sig(ComicCharacters.def(k, CharExpression.HAPPY, it)) }
            assertEquals("$k pozlar", poses.size, poses.toSet().size)
        }
        // Çocuk, yetişkinden kısa
        assertTrue(ComicCharacters.def(CharKind.KID, CharExpression.CALM, CharPose.STAND).bounds[3] < ComicCharacters.def(CharKind.OFFICE, CharExpression.CALM, CharPose.STAND).bounds[3])
    }

    @Test
    fun searchAndFilters() {
        val doctorHappy = ComicCharacters.search("doktor mutlu")
        assertEquals(CharPose.entries.size, doctorHappy.size)
        assertTrue(doctorHappy.all { it.kind == CharKind.DOCTOR && it.expression == CharExpression.HAPPY })
        // Etiketle: "uzay" astronot
        assertTrue(ComicCharacters.search("uzay").all { it.kind == CharKind.ASTRONAUT })
        assertEquals(13 * 26, ComicCharacters.search("uzay").size)
        // Türkçe büyük harf: "ŞÖVALYE" → şövalye
        assertEquals(13 * 26, ComicCharacters.search("ŞÖVALYE").size)
        // Süzgeçler
        assertEquals(15, ComicCharacters.search("", expression = CharExpression.SAD, pose = CharPose.SIT).size)
        assertEquals(1, ComicCharacters.search("", CharKind.ROBOT, CharExpression.ANGRY, CharPose.JUMP).size)
        assertEquals(13 * 26, ComicCharacters.search("", kind = CharKind.CHEF).size)
        assertEquals(0, ComicCharacters.search("doktor", kind = CharKind.CHEF).size)
        // Çeviriyle: İngilizce ad da aranır
        val en = mapOf("Dedektif" to "Detective", "Şaşkın" to "Surprised")
        assertEquals(26, ComicCharacters.search("detective surprised") { en[it] ?: it }.size)
    }

    @Test
    fun previewSheets() {
        if (Preview.dir == null) return
        // Bütün türler: mutlu, ayakta
        Preview.write(sheet("Türler", CharKind.entries.map { ComicCharacters.def(it, CharExpression.HAPPY, CharPose.STAND) }, 5), "v36-characters-kinds")
        // İfadeler ve pozlar
        for (k in listOf(CharKind.STUDENT, CharKind.ROBOT, CharKind.WIZARD)) {
            Preview.write(sheet("${k.title} ifadeler", CharExpression.entries.map { ComicCharacters.def(k, it, CharPose.STAND) }, 4, 300), "v36-characters-faces-${k.id}")
        }
        for (k in listOf(CharKind.KID, CharKind.HERO, CharKind.KNIGHT, CharKind.DETECTIVE)) {
            Preview.write(sheet("${k.title} pozlar", CharPose.entries.map { ComicCharacters.def(k, CharExpression.HAPPY, it) }, 5), "v36-characters-poses-${k.id}")
        }
        System.getProperty("chars")?.split(',')?.forEach { key ->
            val k = CharKind.entries.first { it.id == key }
            Preview.write(sheet(k.title, CharPose.entries.map { ComicCharacters.def(k, CharExpression.entries[it.ordinal % 8], it) }, 5), "v36-characters-$key")
        }
    }

    /** Karakterler aynı ölçekte (tasarım birimi → piksel), altında adlarıyla. */
    private fun sheet(title: String, defs: List<CharacterDef>, columns: Int, cell: Int = 260): BufferedImage {
        val rows = (defs.size + columns - 1) / columns
        val img = BufferedImage(columns * cell, rows * (cell + 20) + 40, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0xF4F2EC); g.fillRect(0, 0, img.width, img.height)
        g.color = Color(0x333333); g.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
        g.drawString(title, 10, 26)
        val unit = (cell - 16) / 230f
        defs.forEachIndexed { i, d ->
            val cx = (i % columns) * cell; val cy = 40 + (i / columns) * (cell + 20)
            val b = d.bounds
            val w = b[2] * unit; val h = b[3] * unit
            // Ayak tabanı hücrenin altına yakın, yatayda ortada
            val x = cx + (cell - w) / 2f
            val y = cy + cell - 8f - (196f - b[1]) * unit
            Preview.draw(g, ComicCharacters.shape(d.id)!!.paths(max(w, h)), x, y, w, h, inkWidth = unit)
            g.color = Color(0x444444); g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            g.drawString("${d.kind.title} · ${d.expression.title} · ${d.pose.title}", cx + 6, cy + cell + 14)
        }
        g.dispose()
        return img
    }

    private fun max(a: Float, b: Float) = kotlin.math.max(a, b)
}
