package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.comicassets.ComicPacks
import com.karapp.drawguidance.data.comicassets.ComicStyle
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.components.INLINE_LABEL_PREFIX
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.EffectsSheetTabs
import com.karapp.drawguidance.ui.screens.canvasedit.components.EffectsTab
import com.karapp.drawguidance.ui.screens.canvasedit.components.MoveExpandRules
import com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleMenus
import com.karapp.drawguidance.ui.screens.canvasedit.components.TOOL_GROUP_DIVIDER
import com.karapp.drawguidance.ui.screens.canvasedit.components.TOOL_PART_DIVIDER
import com.karapp.drawguidance.ui.screens.canvasedit.components.ToolGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** v48 kuralları (saf ve katalog). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V48RulesTest {
    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    @Test fun moveOnlyWithElementExpandOnlyWithCanvas() {
        // Eleman seçili: Taşı açık, Genişlet kapalı
        assertTrue(MoveExpandRules.moveEnabled(hasSelection = true, active = false))
        assertFalse(MoveExpandRules.expandEnabled(MoveExpandRules.canvasTarget(hasSelection = true, backgroundSelected = false), active = false))
        // Seçim yok (tuval) ya da arka plan seçili: Genişlet açık, Taşı kapalı
        assertFalse(MoveExpandRules.moveEnabled(hasSelection = false, active = false))
        assertTrue(MoveExpandRules.expandEnabled(MoveExpandRules.canvasTarget(hasSelection = false, backgroundSelected = false), active = false))
        assertTrue(MoveExpandRules.canvasTarget(hasSelection = true, backgroundSelected = true))
        // Etkin mod kendi düğmesini kapatmaz
        assertTrue(MoveExpandRules.moveEnabled(hasSelection = false, active = true))
        // Taşı ve Genişlet yine Düzenle'nin aracı
        assertEquals(EditMode.EDIT_PICK, EditMode.MOVE.menuFamily)
        assertEquals(EditMode.EDIT_PICK, EditMode.EXPAND.menuFamily)
    }

    @Test fun noGroupingAndThreeSimpleMenus() {
        for (n in 0..20) assertEquals(0, ToolGroups.dividerCount(n))
        val ids = listOf("brush", "eraser", "shape", "formula", "diagram", "panels", "background", "image", "text", "model3d", "paint",
            "elements", "layers", "brush_settings", "select", "move", "rect_select", "eyedropper", "ruler", "template", "expand", "shape_edit")
        val m = SimpleMenus.split(ids) { it }
        assertEquals(listOf("shape", "formula", "diagram", "panels", "background", "image", "text", "model3d"), m.getValue(SimpleMenus.Menu.ADD))
        assertEquals(listOf("brush", "eraser", "paint", "eyedropper", "ruler", "template"), m.getValue(SimpleMenus.Menu.DRAW))
        assertEquals(listOf("select", "move", "rect_select", "expand", "shape_edit", "elements", "layers", "brush_settings"), m.getValue(SimpleMenus.Menu.EDIT))
    }

    @Test fun panelClipIsQuadOnlyForFitting() {
        fun panel(vararg pts: Pair<Float, Float>) = com.karapp.drawguidance.data.model.Stroke(
            "p", pts.map { StrokePoint(it.first, it.second) }, "#000000",
            com.karapp.drawguidance.data.model.BrushType.SHAPE, 2f, 1f, 0,
        )
        // Eğik dörtgen panel (kapanış noktasıyla): dört köşe döner
        val skew = com.karapp.drawguidance.ui.screens.canvasedit.state.Backdrop.panelClip(
            panel(10f to 10f, 110f to 20f, 100f to 120f, 0f to 110f, 10f to 10f)
        )
        assertEquals(4, skew?.size)
        // Pahlı/çok köşeli panel: kırpma dörtgeni yok (sınır dörtgenine sığar)
        val bevel = com.karapp.drawguidance.ui.screens.canvasedit.state.Backdrop.panelClip(
            panel(10f to 10f, 90f to 10f, 110f to 30f, 110f to 110f, 10f to 110f, 10f to 10f)
        )
        assertEquals(null, bevel)
    }

    @Test fun effectsWindowOpensOnPictureEffects() {
        assertEquals(EffectsTab.IMAGES, EffectsSheetTabs.start(EffectsTab.EFFECTS, images = true))
        assertEquals(EffectsTab.IMAGES, EffectsSheetTabs.start(EffectsTab.IMAGES, images = true))
        assertEquals(EffectsTab.LINES, EffectsSheetTabs.start(EffectsTab.LINES, images = true))
        assertEquals(EffectsTab.LINES, EffectsSheetTabs.start(EffectsTab.EFFECTS, images = false))
    }

    @Test fun charactersRecategorizedAndBrokenPoseRemoved() {
        val chars = ComicAssets.catalog.characters.associateBy { it.id }
        for (id in listOf("siborg_kaya", "robotcu_mert", "hacker_ozan", "nisanci_tolga", "tamirci_ali", "mucit_volkan", "gladyator", "muhabir_deniz", "astronot_atlas")) {
            assertEquals(id, ComicStyle.COMIC.id, chars.getValue(id).style)
        }
        for (id in listOf("astronot_kaan", "asker_mert", "korsan_kaptan", "doktor_ayse")) assertEquals(id, ComicStyle.MANGA.id, chars.getValue(id).style)
        // Beyaz tulumlu astronotun bozuk ilk pozu kaldırıldı (dosya da yok; taban paket getirse de eklenmez)
        val astro = chars.getValue("astronot_kaan")
        assertFalse(astro.variants.any { it.id == "poz_01" })
        assertEquals(9, astro.variants.count { it.group == "poz" })
        assertFalse(File("src/main/assets/comic/chars/char_astronot_kaan_poz_01.webp").exists())
        assertTrue("astronot_kaan-poz_01" in ComicPacks.REMOVED_ITEMS)
        // Bütün çeşitlerin dosyaları var
        for (c in ComicAssets.catalog.characters) for (v in c.variants) assertTrue(v.file, File("src/main/assets/" + v.file).exists())
    }
}

/** v48 dikey telefon: basit modda üç menü (adlar simgenin yanında), Taşı/Genişlet ayrı ve seçime göre etkin. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V48PortraitTest : V47MenuBase() {
    private fun ids(container: String): List<String> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(container)), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            .filter { it.startsWith("simple_tool_") }.map { it.removePrefix("simple_tool_") }.distinct()

    private fun disabled(label: String) = compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("tool_row_context")))
        .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)

    @Test
    fun simpleArtThreeScrollingMenusWithLabels() {
        openKind("Üç menü", CanvasKind.SIMPLE_ART)
        waitFor { exists("simple_menu_add") }
        val add = ids("simple_menu_add")
        val draw = ids("simple_menu_draw")
        val edit = ids("simple_menu_edit")
        assertTrue(add.toString(), add.containsAll(listOf("shape", "text", "image", "background", "model3d")))
        assertEquals(listOf("undo", "redo"), draw.take(2))
        assertTrue(draw.toString(), draw.containsAll(listOf("brush", "eraser", "paint", "eyedropper", "ruler", "template")))
        assertTrue(edit.toString(), edit.containsAll(listOf("select", "move", "rect_select", "expand", "elements", "layers", "brush_settings")))
        // Serbest seçim (kement) yok
        assertFalse(exists("simple_tool_free_select"))
        // v49 her düğmenin adı simgesinin yanında; her menü tek satır (hepsi aynı üstte), sığmazsa yatay kaydırılır
        for (menu in listOf("simple_menu_add", "simple_menu_draw", "simple_menu_edit")) {
            val mids = ids(menu)
            val tops = mids.map { (rectOf(node("simple_tool_$it")).top / 6f).toInt() }.toSet()
            assertEquals("$menu tek satır: $tops", 1, tops.size)
        }
        for (id in add + draw + edit) {
            val label = node("simple_tool_$id").config.getOrNull(SemanticsProperties.ContentDescription)!!.first()
            assertTrue("$id adı", compose.onAllNodes(hasTestTag(INLINE_LABEL_PREFIX + label), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        // Ekle menüsü ekrana sığmaz: en az bir düğme görünür alanın dışına taşar (yatay kaydırma)
        val width = root().width
        assertTrue("ekle menüsü taşmalı (kaydırma)", add.any { rectOf(node("simple_tool_$it")).right > width + 1f })
        // Grup ayracı yok
        assertTrue(compose.onAllNodes(hasTestTag(TOOL_GROUP_DIVIDER), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        // Seçim yokken Taşı kapalı, Genişlet açık
        assertTrue(node("simple_tool_move").config.contains(SemanticsProperties.Disabled))
        assertFalse(node("simple_tool_expand").config.contains(SemanticsProperties.Disabled))
        shot("v48-simple-three-menus")
        closeCanvas()
        openNext("Basit not üç menü", CanvasKind.SIMPLE_NOTES)
        waitFor { exists("simple_menu_add") }
        assertFalse(exists("simple_tool_free_select"))
        assertTrue(ids("simple_menu_add").containsAll(listOf("text", "formula", "diagram")))
        shot("v48-simple-notes-three-menus")
    }

    @Test
    fun kidsHaveNo3dAndNoDividers() {
        openKind("Çocuk 3B yok", CanvasKind.KIDS)
        waitFor { exists("kids_tool_shelf") }
        assertFalse(exists("simple_tool_model3d"))
        assertTrue(compose.onAllNodes(hasTestTag(TOOL_GROUP_DIVIDER), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun advancedMoveNeedsElementExpandNeedsCanvas() {
        openKind("Taşı ve genişlet", CanvasKind.ART)
        waitFor { exists("mode_selector") }
        // Mod menüsünde ayraç yok
        assertTrue(compose.onAllNodes(hasTestTag(TOOL_GROUP_DIVIDER) and hasAnyAncestor(hasTestTag("mode_selector")), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.runOnIdle {
            vm().onStrokeFinished(listOf(StrokePoint(300f, 500f), StrokePoint(500f, 520f), StrokePoint(700f, 500f)))
        }
        waitFor { vm().canvas.value!!.strokes.size == 1 }
        compose.runOnIdle { vm().chooseMode(EditMode.EDIT_PICK) }
        waitFor { exists("tool_row_context") }
        // Tuval (seçim yok): yalnız Genişlet
        assertTrue(disabled("Taşı"))
        assertFalse(disabled("Genişlet"))
        // Eleman seçili: yalnız Taşı
        compose.runOnIdle { vm().tutorialHost.select(setOf(vm().canvas.value!!.strokes.single().id)) }
        waitFor { !disabled("Taşı") }
        assertTrue(disabled("Genişlet"))
        compose.onNode(hasContentDescription("Taşı") and hasAnyAncestor(hasTestTag("tool_row_context"))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { vm().mode.value == EditMode.MOVE }
        // Taşı satırında yalnız taşıma (genişletme düğmeleri Genişlet'te)
        val move = names("tool_row_context")
        assertTrue(move.toString(), "Sola kaydır" in move && "Soldan genişlet" !in move)
        assertTrue(exists("move_position_chip") && !exists("canvas_size_chip"))
        shot("v48-advanced-move")
    }
}

/** v48 yatay telefon: gelişmiş modlarda üstteki iki menü tek menü; efektler penceresinde yalnız resimli efektler ve odak çizgileri. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V48LandscapeTest : V47MenuBase() {
    @Test
    fun advancedTopMenusAreOneRow() {
        openKind("Tek menü", CanvasKind.COMIC)
        waitFor { exists("tool_row_draw") }
        for (m in listOf(EditMode.DRAW, EditMode.SELECT, EditMode.ADD, EditMode.EDIT_PICK)) {
            compose.runOnIdle { vm().chooseMode(m) }
            compose.waitForIdle()
            assertTrue("$m: iki parça yok", compose.onAllNodes(hasTestTag(TOOL_PART_DIVIDER), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodes(SemanticsMatcher("part") { it.config.getOrNull(SemanticsProperties.TestTag)?.endsWith("_2") == true && it.config.getOrNull(SemanticsProperties.TestTag)!!.startsWith("tool_row") }, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodes(hasTestTag(TOOL_GROUP_DIVIDER), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        }
        shot("v48-land-one-menu")
    }

    @Test
    fun effectsWindowOnlyPictureEffectsAndFocusLines() {
        openKind("Efektler v48", CanvasKind.COMIC)
        compose.runOnIdle { vm().chooseMode(EditMode.ADD) }
        waitFor { exists("tool_row_context") }
        val node = hasContentDescription("Efektler") and hasAnyAncestor(hasTestTag("tool_row_context"))
        runCatching { compose.onNode(node).performScrollTo() }
        compose.onNode(node).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("effects_sheet") }
        // Resimli efektler ("Efektler" adıyla) açık; çizilen efekt grupları ve ses yazıları yok
        waitFor { exists("fximg") }
        assertTrue(exists("effects_tab_images") && exists("effects_tab_lines"))
        assertFalse(exists("effects_tab_effects"))
        assertTrue(compose.onAllNodes(SemanticsMatcher("group") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("effects_group_") == true }).fetchSemanticsNodes().isEmpty())
        assertFalse(exists("sfx_sheet"))
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Resimli efektler")).fetchSemanticsNodes().isEmpty())
        shot("v48-effects-window")
        compose.onNodeWithTag("effects_tab_lines").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertFalse(exists("fximg"))
    }
}

