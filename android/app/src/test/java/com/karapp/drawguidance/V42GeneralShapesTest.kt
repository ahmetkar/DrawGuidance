package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.library.general.GeneralShapes
import com.karapp.drawguidance.content.library.sketch.SketchLibrary
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.i18n.AppLanguage
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.drawing.ShapeCategory
import com.karapp.drawguidance.ui.drawing.ShapeKind
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v42 resim tuvallerinin şekilleri: azaltılmış temel şekiller + genel şekiller (çizgiler/oklar, blok oklar, yıldızlar,
 * afişler, akış şeması, konuşma kutuları, semboller); nesne çizimleri yok. Çizgi roman seçicisi değişmedi.
 */
class V42GeneralShapesTest {
    @After fun reset() = I18n.setLanguage("tr")

    private val objectSections = setOf("Nesneler", SketchLibrary.SECTION)

    @Test
    fun artPickersHaveGeneralShapesAndNoObjects() {
        for (kind in listOf(CanvasKind.ART, CanvasKind.SIMPLE_ART)) {
            val cats = ShapeRegistry.categories(kind)
            val items = cats.flatMap { it.items }
            // Tek bölüm: kategoriler süzgeçle seçilir
            assertEquals("$kind", setOf("Temel"), cats.map { it.section }.toSet())
            assertEquals(ShapeCategory.ART_GENERAL, cats.first().builtin)
            assertEquals(14, cats.first().items.size)
            // Nesne kütüphanesi ve çizim yardımcıları yok
            assertTrue("$kind", items.none { id -> ShapeRegistry.isLibrary(id) && !GeneralShapes.isGeneral(id) })
            assertTrue(cats.none { it.section in objectSections })
            assertEquals(items.size, items.toSet().size)
            for (id in items) assertTrue(id, ShapeRegistry.canBuild(id))
        }
        val art = ShapeRegistry.categories(CanvasKind.ART)
        val total = art.sumOf { it.items.size }
        assertTrue("toplam şekil: $total", total in 120..160)
        assertEquals(
            listOf("gen_lines", "gen_block_arrows", "gen_stars", "gen_banners", "gen_flowchart", "gen_callouts", "gen_symbols"),
            art.drop(1).map { it.id },
        )
        assertTrue(art.first { it.id == "gen_flowchart" }.items.size >= 25)
        // Basit resimde akış şeması yok, ötekiler aynı
        assertEquals(art.map { it.id } - "gen_flowchart", ShapeRegistry.categories(CanvasKind.SIMPLE_ART).map { it.id })
        // Eski temel listeden çıkanlar (yıldız, sarmal, gölgeli cisimler) artık temel kategoride değil
        val basic = art.first().items
        for (k in listOf(ShapeKind.STAR, ShapeKind.SPIRAL, ShapeKind.SOLID_CUBE, ShapeKind.CROSS)) assertFalse(k.name, k.name in basic)
    }

    @Test
    fun comicPickerUnchanged() {
        val comic = ShapeRegistry.categories(CanvasKind.COMIC)
        assertEquals(ShapeCategory.ART_BASIC, comic.first().builtin)
        // v43 nesne kütüphanesi kaldırıldı: çizgi romanın Şekil ekle penceresinde de nesne yok
        assertTrue(comic.none { it.section == "Nesneler" })
        assertTrue(comic.none { it.id.startsWith("gen_") })
    }

    @Test
    fun everyGeneralShapeBuildsInsideItsBox() {
        assertTrue(GeneralShapes.shapes.size >= 120)
        val ids = GeneralShapes.shapes.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (s in GeneralShapes.shapes) {
            assertTrue(s.id, s.id.startsWith(GeneralShapes.ID_PREFIX))
            assertTrue(s.title, ShapeLibrary.find(ShapeLibrary.PREFIX + s.id) === s)
            val paths = s.paths(256f)
            assertTrue(s.id, paths.isNotEmpty() && paths.all { it.points.size >= 2 })
            // Renksiz: kalem rengi ve seçili dolgu kullanılır
            assertTrue(s.id, paths.all { it.fill == null && it.stroke == null })
            val xs = paths.flatMap { p -> p.points.map { it.x } }
            val ys = paths.flatMap { p -> p.points.map { it.y } }
            assertTrue("${s.id} x ${xs.min()}..${xs.max()}", xs.min() >= -0.03f && xs.max() <= 1.03f)
            assertTrue("${s.id} y ${ys.min()}..${ys.max()}", ys.min() >= -0.03f && ys.max() <= 1.03f)
            // Kutuyu doldurur (çok küçük ya da kaymış değil)
            if (s.fit != Fit.LINE) {
                val w = xs.max() - xs.min(); val h = ys.max() - ys.min()
                assertTrue("${s.id} boyut $w×$h", maxOf(w, h) > 0.85f && minOf(w, h) > 0.25f)
            }
            // Dolgu alınabilen en az bir kapalı parça (çizgiler dışında)
            if (s.fit != Fit.LINE) assertTrue(s.id, paths.any { it.closed })
        }
    }

    @Test
    fun generalShapesStretchFreelyAndLinesFollowTheDrag() {
        val box = ShapeLibrary.PREFIX + "gen.fc_process"
        val rect = ShapeRegistry.frame(box, Offset(10f, 20f), Offset(310f, 70f))
        assertEquals(300f, rect.width, 0.01f); assertEquals(50f, rect.height, 0.01f)
        // Kare yerleşenler kare kalır
        val star = ShapeLibrary.PREFIX + "gen.star5"
        val sq = ShapeRegistry.frame(star, Offset(0f, 0f), Offset(200f, 80f))
        assertEquals(sq.width, sq.height, 0.01f)
        // Çizgi şekilleri iki nokta arasında
        val line = ShapeLibrary.PREFIX + "gen.arrow_dashed"
        val paths = ShapeRegistry.paths(line, Offset(0f, 100f), Offset(400f, 100f), 4f)
        val xs = paths.flatMap { p -> p.points.map { it.x } }
        assertEquals(0f, xs.min(), 1f); assertEquals(400f, xs.max(), 1f)
        assertTrue(paths.first().dashed)
        // Akış şeması kutusu sürüklenen dikdörtgeni doldurur
        val built = ShapeRegistry.paths(box, Offset(10f, 20f), Offset(310f, 70f), 4f).flatMap { it.points }
        assertEquals(10f, built.minOf { it.x }, 0.5f); assertEquals(310f, built.maxOf { it.x }, 0.5f)
        assertEquals(20f, built.minOf { it.y }, 0.5f); assertEquals(70f, built.maxOf { it.y }, 0.5f)
    }

    @Test
    fun namesTranslatedInEveryLanguage() {
        val names = GeneralShapes.shapes.map { it.title } + GeneralShapes.categories.map { it.title }
        for (lang in AppLanguage.entries.filter { it != AppLanguage.TR }) {
            I18n.setLanguage(lang.code)
            val same = names.filter { I18n.tr(it) == it }
            assertTrue("${lang.code}: $same", same.isEmpty())
        }
    }
}
