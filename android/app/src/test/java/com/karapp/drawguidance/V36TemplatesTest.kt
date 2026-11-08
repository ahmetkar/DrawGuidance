package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.comic.ComicTemplates
import com.karapp.drawguidance.content.comic.TemplateGroup
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v36 (8.6) şablon ve grup adları bütün dillerde çevrili ("Webtoon" ve "Manga" bazı dillerde aynı yazılır). */
class V36TemplateNamesTest {
    @org.junit.After fun reset() = com.karapp.drawguidance.i18n.I18n.setLanguage("tr")

    @Test
    fun templateNamesAreTranslated() {
        val names = ComicTemplates.all.map { it.title } + TemplateGroup.entries.map { it.title }
        for (lang in com.karapp.drawguidance.i18n.AppLanguage.entries.filter { it != com.karapp.drawguidance.i18n.AppLanguage.TR }) {
            com.karapp.drawguidance.i18n.I18n.setLanguage(lang.code)
            val same = names.filter { com.karapp.drawguidance.i18n.I18n.tr(it) == it && it != "Webtoon" && it != "Manga" }
            assertTrue("${lang.code} çevrilmeyen: $same", same.isEmpty())
        }
    }
}

/**
 * v36 (8.6) Şablonlar düğmesi ve penceresi (çizgi roman tuvalinde): şablon seçilince paneller otomatik kurulur;
 * Paneller sekmesinde seçili panelin genişliği, sırası (içindeki çizimle birlikte), yanına/altına/sona ekleme,
 * silme, panel aralığı ve kenar boşluğu değişir; geri alınır; kayıtta korunur. Yeni şablon eskisinin yerine geçer,
 * çizimler yerinde kalır; elle silinen panelin yerini komşusu doldurur.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V36TemplatesTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun strokes(): List<Stroke> = vm().canvas.value?.strokes.orEmpty()
    private fun panels(): List<Stroke> = strokes().filter { PanelSets.isPanel(it) }
    private fun panel(leaf: Int): Stroke = panels().single { it.shape!!.panel!!.leaf == leaf }
    private fun set() = PanelSets.find(strokes())!!

    /** Noktaların kutusu (kalınlık hariç): sol, üst, sağ, alt. */
    private fun box(s: Stroke) = floatArrayOf(s.points.minOf { it.x }, s.points.minOf { it.y }, s.points.maxOf { it.x }, s.points.maxOf { it.y })

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun slide(container: String, value: Float) {
        val node = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and hasAnyAncestor(hasTestTag(container)))
        node.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.waitForIdle()
    }

    @Test
    fun pickEditAndKeepTemplate() {
        open("Şablon sayfası", CanvasKind.COMIC)
        addMenu("Şablonlar")
        waitFor { exists("templates_sheet") }
        // Gruplar: manga
        click("tpl_group_manga")
        waitFor { exists("tpl_yonkoma") }
        assertTrue(exists("tpl_manga") && exists("tpl_manga_action"))
        assertFalse(exists("tpl_single"))
        click("tpl_group_all")
        waitFor { exists("tpl_single") }
        shot("v36-templates-sheet")
        // Şablon: paneller kurulur, Paneller sekmesine geçilir
        click("tpl_three_big")
        waitFor { panels().size == 3 }
        waitFor { exists("tpl_map") }
        val (top, left, right) = set().page.leafIds()
        assertEquals(setOf(PanelSets.KIND), panels().map { it.shape!!.kind }.toSet())
        assertEquals(1, panels().map { it.shape!!.panel!!.set }.toSet().size)
        assertEquals(1000f * 0.045f, box(panel(top))[0], 1f)
        // Sayfanın ortasında bir karakter (üst panelin içinde)
        compose.runOnIdle { vm().insertCharacter(com.karapp.drawguidance.data.comicassets.ComicAssets.catalog.characters.first { it.theme == "okul" }.cover) }
        waitFor { strokes().any { it.brushType == com.karapp.drawguidance.data.model.BrushType.IMAGE } }
        fun hero() = strokes().single { it.brushType == com.karapp.drawguidance.data.model.BrushType.IMAGE }
        val heroBefore = ElementOps.bounds(hero())!!
        // Genişlik: 2. panel %70
        click("tpl_panel_2")
        slide("tpl_width", 0.7f)
        waitFor { set().page.widthOf(left)?.let { kotlin.math.abs(it - 0.7f) < 0.011f } == true }
        assertTrue(box(panel(left)).let { it[2] - it[0] } > 2f * box(panel(right)).let { it[2] - it[0] })
        // Sıra: 2. panel arkaya (yanındaki panelle yer değiştirir, boyutunu götürür)
        click("tpl_move_next")
        waitFor { set().page.leafIds() == listOf(top, right, left) }
        assertTrue(box(panel(left))[0] > box(panel(right))[0])
        // 1. panel arkaya: alttaki panelle yer değişir, içindeki karakter de onunla gider (küçülerek)
        click("tpl_panel_1")
        click("tpl_move_next")
        waitFor { set().page.leafIds().indexOf(top) == 1 }
        val topBox = box(panel(top))
        val heroAfter = ElementOps.bounds(hero())!!
        assertTrue("karakter panelle taşındı", heroAfter.center.x in topBox[0]..topBox[2] && heroAfter.center.y in topBox[1]..topBox[3])
        assertTrue(heroAfter.height < heroBefore.height * 0.9f)
        shot("v36-templates-edit")
        // Ekle: yanına, altına, sona; sil
        click("tpl_add_side")
        waitFor { panels().size == 4 }
        click("tpl_add_below")
        waitFor { panels().size == 5 }
        click("tpl_add_end")
        waitFor { panels().size == 6 }
        click("tpl_delete")
        waitFor { panels().size == 5 }
        // Aralık ve kenar boşluğu
        slide("tpl_gutter", 0.05f)
        waitFor { kotlin.math.abs(set().page.gutter - 0.05f) < 0.0011f }
        val first = set().page.leafIds().first()
        val second = panels().filter { it.shape!!.panel!!.leaf != first }.minOf { box(it)[1] }
        assertEquals(50f, second - box(panel(first))[3], 2f)
        slide("tpl_margin", 0.1f)
        waitFor { kotlin.math.abs(box(panel(first))[1] - 100f) < 2f }
        // Geri al: kenar boşluğu eski haline
        compose.runOnIdle { vm().endCoalesce(); vm().undo() }
        waitFor { kotlin.math.abs(box(panel(first))[1] - 45f) < 2f }
        // Kayıt: panel bilgisi ikili biçimde korunur
        val decoded = ContentCodec.decode(ContentCodec.encode(strokes()))
        assertEquals(panels().map { it.shape!!.panel }, decoded.filter { PanelSets.isPanel(it) }.map { it.shape!!.panel })
        assertNotNull(PanelSets.find(decoded))
        settleFor(300)
        shotAll("v36-templates-page")
    }

    @Test
    fun replaceTemplateAndManualDelete() {
        open("Şablon değiştir", CanvasKind.COMIC)
        compose.runOnIdle { vm().insertTemplate("four_grid") }
        waitFor { panels().size == 4 }
        compose.runOnIdle { vm().insertCharacter(com.karapp.drawguidance.data.comicassets.ComicAssets.catalog.characters.first { it.theme == "okul" }.cover) }
        waitFor { strokes().size == 5 }
        // Elle silinen panel: düzen yeniden kurulunca komşusu yerini doldurur
        val gone = panels()[1]
        val stay = panels()[0]
        compose.runOnIdle { vm().chooseMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.SELECT); vm().selectOnlyAt(box(gone)[2], (box(gone)[1] + box(gone)[3]) / 2f, 8f) }
        waitFor { vm().selectedIds.value == setOf(gone.id) }
        compose.runOnIdle { vm().deleteSelected() }
        waitFor { panels().size == 3 }
        assertEquals(3, set().page.leafIds().size)
        compose.runOnIdle { vm().editPanels { it.withGutter(0.03f) } }
        waitFor { set().page.gutter == 0.03f }
        assertEquals(1000f - 2 * 45f, box(panels().single { it.id == stay.id }).let { it[2] - it[0] }, 1f)
        // Yeni şablon eskisinin yerine (karakter yerinde kalır); pencere Şablonlar sekmesinde uyarı gösterir
        addMenu("Şablonlar")
        waitFor { exists("templates_sheet") }
        click("templates_tab_pick")
        waitFor { exists("tpl_replace_hint") }
        click("tpl_manga")
        waitFor { panels().size == 5 }
        assertEquals(1, panels().map { it.shape!!.panel!!.set }.toSet().size)
        assertEquals(1, strokes().count { it.brushType == com.karapp.drawguidance.data.model.BrushType.IMAGE })
        // Sağdan sola: 1. panel sağda
        val shapes = set().page.leafIds().map { panel(it) }
        assertTrue(box(shapes[1])[0] > box(shapes[2])[0])
    }

    @Test
    fun onlyInComicCanvas() {
        open("Resim", CanvasKind.ART)
        mode("Ekle")
        val row = hasAnyAncestor(hasTestTag("tool_row_context"))
        waitFor { compose.onAllNodes(hasContentDescription("Şekil ekle") and row).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasContentDescription("Şablonlar") and row).fetchSemanticsNodes().isEmpty())
        // v38 Karakterler de yalnız çizgi romanda
        assertTrue(compose.onAllNodes(hasContentDescription("Karakterler") and row).fetchSemanticsNodes().isEmpty())
    }
}
