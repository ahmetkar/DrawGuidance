package com.karapp.drawguidance

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.comic.ComicTemplates
import com.karapp.drawguidance.content.comic.TemplateGroup
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v37 (7) eski "Panel düzeni"ndeki bütün düzenler Şablonlar'da: her eski düzenin aynı panel sayısında bir şablonu
 * var ("İki + büyük" eklendi). (8) Paneller boyutlanınca (yükseklik, genişlik, aralık, kenar boşluğu) içindeki
 * çizimler panelle birlikte taşınır ve ölçeklenir; panelin içinde kalır. Kaydırıcı sürüklenirken küçült-büyüt
 * kayıpsızdır (çizim başladığı boya döner).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V37PanelsTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.COMIC) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun strokes(): List<Stroke> = vm().canvas.value?.strokes.orEmpty()
    private fun set() = PanelSets.find(strokes())!!
    private fun panelBox(leaf: Int): FloatArray = strokes().single { it.shape?.panel?.leaf == leaf }.let { s ->
        floatArrayOf(s.points.minOf { it.x }, s.points.minOf { it.y }, s.points.maxOf { it.x }, s.points.maxOf { it.y })
    }
    private fun hero() = ElementOps.bounds(strokes().filter { it.brushType == com.karapp.drawguidance.data.model.BrushType.IMAGE })!!

    @Test
    fun oldPanelLayoutsAllHaveTemplates() {
        // Eski düzen kimliği → şablon (panel sayıları aynı)
        val map = mapOf(
            "single" to "single", "rows2" to "two_rows", "cols2" to "two_cols", "rows3" to "three_rows", "grid4" to "four_grid",
            "grid6" to "six_grid", "big_top" to "three_big", "big_bottom" to "three_big_bottom", "manga" to "manga", "strip3" to "three_cols",
        )
        for ((old, id) in map) {
            val layout = com.karapp.drawguidance.content.comic.ComicPanels.byId(old)!!
            val t = ComicTemplates.byId(id)
            assertNotNull("şablon yok: $id", t)
            assertEquals(old, layout.cells.size, t!!.panelCount)
        }
        assertEquals(TemplateGroup.BASIC, ComicTemplates.byId("three_big_bottom")!!.group)
        // Büyük panel altta
        val shapes = ComicTemplates.byId("three_big_bottom")!!.page.shapes(1000f, 1000f)
        val big = shapes.maxBy { it.bounds().let { b -> (b[2] - b[0]) * (b[3] - b[1]) } }
        assertTrue(shapes.filter { it !== big }.all { it.bounds()[3] < big.bounds()[1] })
    }

    @Test
    fun drawingsFollowPanelWhenResized() {
        open("Panel boyu")
        compose.runOnIdle { vm().insertTemplate("three_big") }
        waitFor { PanelSets.find(strokes()) != null }
        val top = set().page.leafIds().first()
        compose.runOnIdle { vm().insertCharacter(com.karapp.drawguidance.data.comicassets.ComicAssets.catalog.characters.first { it.theme == "okul" }.cover) }
        waitFor { strokes().any { it.brushType == com.karapp.drawguidance.data.model.BrushType.IMAGE } }
        // Karakter küçültülüp tamamen üst panelin içine alınır
        compose.runOnIdle { vm().scaleSelected(0.3f, 0.3f) }
        compose.waitForIdle()
        val a = panelBox(top)
        hero().let { h -> compose.runOnIdle { vm().moveSelected((a[0] + a[2]) / 2f - h.center.x - 120f, (a[1] + a[3]) / 2f - h.center.y + 40f) } }
        compose.waitForIdle()
        val before = hero()
        assertTrue("başta panelin içinde: $before / ${a.toList()}", before.top >= a[1] && before.bottom <= a[3] && before.left >= a[0] && before.right <= a[2])
        val rel = floatArrayOf((before.center.x - a[0]) / (a[2] - a[0]), (before.center.y - a[1]) / (a[3] - a[1]))
        val key = "panel-height-${set().set}-$top"

        // Üst panel küçülür: karakter panelle birlikte küçülür, göreli yeri korunur, panelin içinde kalır
        compose.runOnIdle { vm().editPanels(key) { it.setHeight(top, 0.35f) } }
        compose.waitForIdle()
        val b = panelBox(top)
        assertTrue("panel küçüldü", b[3] - b[1] < (a[3] - a[1]) * 0.8f)
        val small = hero()
        assertTrue("karakter küçüldü", small.height < before.height * 0.85f)
        assertTrue("panelin içinde", small.top >= b[1] - 1f && small.bottom <= b[3] + 1f && small.left >= b[0] - 1f && small.right <= b[2] + 1f)
        assertEquals(rel[0], (small.center.x - b[0]) / (b[2] - b[0]), 0.02f)
        assertEquals(rel[1], (small.center.y - b[1]) / (b[3] - b[1]), 0.02f)

        // Aynı sürüklemede eski boyuna dönünce karakter başladığı boya döner (kayıpsız)
        val startPart = ComicTemplates.byId("three_big")!!.page.heightOf(top)!!
        compose.runOnIdle { vm().editPanels(key) { it.setHeight(top, 0.75f) } }
        compose.runOnIdle { vm().editPanels(key) { it.setHeight(top, startPart) } }
        compose.waitForIdle()
        val back = hero()
        assertEquals(before.width, back.width, 1.5f)
        assertEquals(before.center.x, back.center.x, 1.5f)
        assertEquals(before.center.y, back.center.y, 1.5f)
        vm().endCoalesce()

        // Kenar boşluğu büyür: karakter yine panelle taşınır
        compose.runOnIdle { vm().editPanels("panel-margin-${set().set}") { it.withMargin(0.12f) } }
        compose.waitForIdle()
        val c = panelBox(top)
        val moved = hero()
        assertTrue(moved.left >= c[0] - 1f && moved.top >= c[1] - 1f && moved.right <= c[2] + 1f && moved.bottom <= c[3] + 1f)
        vm().endCoalesce()

        // Geri al: kenar boşluğu öncesi
        compose.runOnIdle { vm().undo() }
        compose.waitForIdle()
        assertEquals(before.center.y, hero().center.y, 1.5f)
    }
}
