package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.PanelTab
import com.karapp.drawguidance.ui.screens.canvasedit.components.KidsMenu
import com.karapp.drawguidance.ui.screens.canvasedit.components.PanelMenu
import com.karapp.drawguidance.ui.screens.canvasedit.components.Properties
import com.karapp.drawguidance.ui.screens.canvasedit.components.PropertiesContent
import com.karapp.drawguidance.ui.screens.canvasedit.components.ToolRowSplitMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.modeToolbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v47 menü kuralları (saf). */
class V47MenuRulesTest {
    @Test fun fourModesInOrder() {
        val expected = listOf(EditMode.SELECT, EditMode.DRAW, EditMode.ADD, EditMode.EDIT_PICK)
        for (kind in listOf(CanvasKind.ART, CanvasKind.NOTES, CanvasKind.COMIC)) assertEquals(expected, modeToolbar(kind))
        // Taşı ve Genişlet Düzenle'nin aracı: mod menüsünde Düzenle seçili görünür, üst satırları kendi satırı
        assertEquals(EditMode.EDIT_PICK, EditMode.MOVE.menuFamily)
        assertEquals(EditMode.EDIT_PICK, EditMode.EXPAND.menuFamily)
        assertEquals(EditMode.DRAW, EditMode.PEN.menuFamily)
        assertEquals(EditMode.ADD, EditMode.SHAPE.menuFamily)
    }

    @Test fun propertiesOnlyForAdjustableTools() {
        assertEquals(PropertiesContent.BRUSH, Properties.content(EditMode.DRAW, kids = false, paintBrush = false, textSelected = false))
        assertEquals(PropertiesContent.BRUSH, Properties.content(EditMode.PEN, kids = false, paintBrush = false, textSelected = false))
        assertEquals(PropertiesContent.TEXT, Properties.content(EditMode.TEXT, kids = false, paintBrush = false, textSelected = false))
        // Düzenlenen yazıda da renk (biçimi Elemanlar'da)
        assertEquals(PropertiesContent.TEXT, Properties.content(EditMode.TEXT, kids = false, paintBrush = false, textSelected = true))
        for (m in listOf(EditMode.SELECT, EditMode.ADD, EditMode.EDIT_PICK, EditMode.MOVE, EditMode.SHAPE, EditMode.PAINT)) {
            assertEquals(m.name, PropertiesContent.EMPTY, Properties.content(m, kids = false, paintBrush = false, textSelected = false))
        }
        // Çocuk modunda boya fırçasının boyu ve renkleri; kovanın ayarı yok
        assertEquals(PropertiesContent.BRUSH, Properties.content(EditMode.PAINT, kids = true, paintBrush = true, textSelected = false))
        assertEquals(PropertiesContent.EMPTY, Properties.content(EditMode.PAINT, kids = true, paintBrush = false, textSelected = false))
    }

    @Test fun kidsMenuOrder() {
        val raw = listOf("brush", "eraser", "paint_brush", "paint", "elements", "layers", "brush_settings", "challenge", "background", "image", "text", "model3d", "select", "eyedropper", "ruler", "template", "expand", "shape")
        val ordered = KidsMenu.order(raw) { it }
        assertEquals(listOf("brush", "eraser", "paint_brush", "paint", "shape", "challenge", "template", "text"), ordered.take(8))
        // Ötekiler kendi sıralarıyla ardında
        assertEquals(listOf("elements", "layers", "brush_settings", "background", "image", "model3d", "select", "eyedropper", "ruler", "expand"), ordered.drop(8))
    }

    @Test fun simpleSplitAndRail() {
        assertEquals(ToolRowSplitMode.STACK_ALWAYS, PanelMenu.simpleSplit(landscape = false))
        assertEquals(ToolRowSplitMode.NONE, PanelMenu.simpleSplit(landscape = true))
        assertTrue(PanelMenu.railShown(true))
        assertFalse(PanelMenu.railShown(false))
        assertEquals(listOf(PanelTab.ELEMENTS, PanelTab.LAYERS, PanelTab.BRUSH), PanelMenu.TABS)
    }
}

abstract class V47MenuBase : V46SplitBase() {
    protected fun panelButtonIn(container: String, tab: PanelTab): Boolean =
        compose.onAllNodes(hasTestTag("panel_btn_${tab.name}") and hasAnyAncestor(hasTestTag(container)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun names(row: String): List<String> = rowButtons(row).map(::name)

    protected fun closeCanvas() {
        compose.runOnIdle { vm().closePanel(); vm().chooseMode(EditMode.DRAW) }
        compose.waitForIdle()
        SystemBack.press()
        waitFor { !exists("drawing_canvas") }
    }

    protected fun openNext(name: String, kind: CanvasKind) {
        kotlinx.coroutines.runBlocking { org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.repository.CanvasRepository>().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodes(androidx.compose.ui.test.hasText(name))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }
}

/** Dikey telefon: gelişmişte dört mod ve son grupta Elemanlar/Katmanlar/Özellikler; basit menü iki satır. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V47PortraitMenuTest : V47MenuBase() {
    @Test
    fun advancedModeMenuHasPanelGroup() {
        openKind("Dört mod", CanvasKind.ART)
        waitFor { exists("mode_selector") }
        val modes = names("mode_selector")
        assertEquals(listOf("Seç", "Çiz", "Ekle", "Düzenle", "Elemanlar", "Katmanlar", "Özellikler"), modes)
        assertFalse("Taşı ve Genişlet mod menüsünde yok", modes.contains("Taşı") || modes.contains("Genişlet"))
        // Üst satırlarda artık yok
        for (row in listOf("tool_row_draw")) {
            val n = names(row)
            assertFalse(n.toString(), n.contains("Elemanlar") || n.contains("Katmanlar") || n.contains("Özellikler"))
        }
        assertFalse(exists("panel_rail"))
        // Özellikler: Çiz modunda fırça ayarları
        compose.onNodeWithTag("panel_btn_BRUSH", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { exists("side_panel_title") }
        assertEquals(PanelTab.BRUSH, vm().panel.value)
        assertFalse(exists("properties_empty"))
        shot("v47-portrait-properties-brush")
        // Seç modunda ayar yok: boş
        compose.runOnIdle { vm().chooseMode(EditMode.SELECT) }
        waitFor { exists("properties_empty") }
        // Yazı aracında yeni yazının biçimi
        compose.runOnIdle { vm().chooseMode(EditMode.TEXT) }
        waitFor { exists("properties_text") }
        // Elemanlar düğmesi içeriği değiştirir
        compose.runOnIdle { vm().finishAction() }
        compose.onNodeWithTag("panel_btn_ELEMENTS", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { vm().panel.value == PanelTab.ELEMENTS }
        compose.runOnIdle { vm().closePanel() }
        // v48 Düzenle satırında Taşı ve Genişlet ayrı: seçim yokken (tuval) Taşı kapalı, Genişlet açık
        compose.runOnIdle { vm().chooseMode(EditMode.EDIT_PICK) }
        waitFor { exists("tool_row_context") }
        val edit = names("tool_row_context")
        assertTrue(edit.toString(), edit.containsAll(listOf("Taşı", "Genişlet")))
        fun disabled(label: String) = compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("tool_row_context")))
            .fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        assertTrue(disabled("Taşı"))
        assertFalse(disabled("Genişlet"))
        compose.onNode(hasContentDescription("Genişlet") and hasAnyAncestor(hasTestTag("tool_row_context"))).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { vm().mode.value == EditMode.EXPAND }
        val frame = names("tool_row_context")
        for (n in listOf("Soldan genişlet", "Sağdan genişlet", "Sığdır")) assertTrue("$n: $frame", n in frame)
        assertTrue(exists("canvas_size_chip") && !exists("move_position_chip"))
        shot("v48-portrait-expand")
    }

    @Test
    fun simpleMenuAlwaysTwoRowsWithPanelButtons() {
        openKind("Basit iki satır", CanvasKind.SIMPLE_NOTES)
        waitFor { exists("simple_tool_row") }
        // v48 basit modun dikey ekranında üç menü (ekle, çiz, seç ve düzenle)
        for (m in listOf("simple_menu_add", "simple_menu_draw", "simple_menu_edit")) assertTrue(m, exists(m))
        val all = names("simple_tool_row")
        assertTrue(all.toString(), all.containsAll(listOf("Elemanlar", "Katmanlar", "Özellikler")))
        shot("v47-portrait-simple")
        closeCanvas()
        // Çocuk modu da iki satır; Kalem, Silgi, Boya, Kova'dan sonra Şekiller, Görev, Kalıp, Yazı
        openNext("Çocuk iki satır", CanvasKind.KIDS)
        waitFor { exists("simple_tool_row_2") }
        val ids = compose.onAllNodes(hasAnyAncestor(hasTestTag("simple_tool_row")), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) }
            .filter { it.startsWith("simple_tool_") && it != "simple_tool_row" && !it.startsWith("simple_tool_row_") }
            .map { it.removePrefix("simple_tool_") }.distinct()
        assertEquals(ids.toString(), listOf("undo", "redo", "brush", "eraser", "paint_brush", "paint", "shape", "challenge", "template", "text"), ids.take(10))
        shot("v47-portrait-kids")
    }

    @Test
    fun emptyTapOpensNoMenu() {
        openKind("Menüsüz", CanvasKind.NOTES)
        compose.runOnIdle { vm().chooseMode(EditMode.SELECT) }
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(Offset(width * 0.5f, height * 0.6f)) }
        compose.waitForIdle()
        assertFalse(exists("canvas_add_menu"))
        compose.runOnIdle { vm().chooseMode(EditMode.ADD) }
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(Offset(width * 0.5f, height * 0.6f)) }
        compose.waitForIdle()
        assertFalse(exists("canvas_add_menu"))
    }
}

/** Yatay telefon: Elemanlar/Katmanlar/Özellikler sağdaki rayda (açılır alanın solunda); basit menü tek satır. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V47LandscapeMenuTest : V47MenuBase() {
    @Test
    fun advancedRailBesidePanel() {
        openKind("Yatay ray", CanvasKind.COMIC)
        waitFor { exists("panel_rail") && exists("mode_selector") }
        assertEquals(listOf("Seç", "Çiz", "Ekle", "Düzenle"), names("mode_selector"))
        for (t in PanelMenu.TABS) {
            assertTrue(panelButtonIn("panel_rail", t))
            assertFalse(panelButtonIn("mode_selector", t))
        }
        val railBefore = rectOf(node("panel_rail"))
        assertTrue("ray sağ kenarda", railBefore.right > root().width * 0.9f)
        compose.onNodeWithTag("panel_btn_LAYERS", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { vm().panel.value == PanelTab.LAYERS && exists("side_panel_right") }
        repeat(8) { compose.waitForIdle() }
        val panel = rectOf(node("side_panel_right"))
        val rail = rectOf(node("panel_rail"))
        assertTrue("ray alanın soluna yapışık: $rail / $panel", rail.right <= panel.left + 2f && panel.left - rail.right < 40f)
        compose.onNodeWithTag("panel_btn_BRUSH", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { vm().panel.value == PanelTab.BRUSH }
        shot("v47-landscape-rail")
    }

    @Test
    fun simpleSingleRowAndRail() {
        openKind("Yatay basit", CanvasKind.SIMPLE_ART)
        waitFor { exists("simple_tool_row") && exists("panel_rail") }
        assertTrue(part("simple_tool_row_2") == null)
        val n = names("simple_tool_row")
        assertFalse(n.toString(), n.contains("Elemanlar") || n.contains("Özellikler"))
        assertGrouped("simple_tool_row")
        shot("v47-landscape-simple")
    }
}
