package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.GradientSpec
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.BackgroundDraft
import com.karapp.drawguidance.ui.screens.canvasedit.components.BackgroundPart
import com.karapp.drawguidance.ui.screens.canvasedit.PanelTab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v41 mekân arka planı panelin oranında ortadan kırpılır. */
class V41BackdropCropTest {
    @Test fun wideImageIntoTallPanelCropsSides() {
        val c = com.karapp.drawguidance.ui.screens.canvasedit.state.Backdrop.cropRect(1600, 1000, 0.5f)
        assertEquals(500, c.width); assertEquals(1000, c.height)
        assertEquals(550, c.left); assertEquals(0, c.top)
    }

    @Test fun tallPanelOrSameAspect() {
        val wide = com.karapp.drawguidance.ui.screens.canvasedit.state.Backdrop.cropRect(1000, 1000, 2f)
        assertEquals(1000, wide.width); assertEquals(500, wide.height); assertEquals(250, wide.top)
        val same = com.karapp.drawguidance.ui.screens.canvasedit.state.Backdrop.cropRect(1600, 900, 16f / 9f)
        assertEquals(1600, same.width); assertEquals(900, same.height)
        // Geçersiz oran: resim olduğu gibi
        val bad = com.karapp.drawguidance.ui.screens.canvasedit.state.Backdrop.cropRect(800, 600, Float.NaN)
        assertEquals(800, bad.width); assertEquals(600, bad.height)
    }
}

/** v41 arka plan penceresinin taslağı: yeni seçilen tür eski arka planın öteki türlerini kaldırır. */
class V41BackgroundDraftTest {
    private val pattern = BackgroundSpec(patternId = "art.polka", patternColorHex = "#E57373")
    private val image = "paper:art.img.coldpress"

    @Test fun newImageRemovesOldPattern() {
        val d = BackgroundDraft(pattern).pick(pattern.copy(imageId = image))
        assertEquals(image, d.spec.imageId)
        assertNull("eski desen kalkar", d.spec.patternId)
        assertEquals(setOf(BackgroundPart.IMAGE), d.chosen)
    }

    @Test fun bothPickedInSameSessionStay() {
        val d = BackgroundDraft(pattern).pick(pattern.copy(imageId = image))
        val again = d.pick(d.spec.copy(patternId = "art.polka"))
        assertEquals(image, again.spec.imageId)
        assertEquals("art.polka", again.spec.patternId)
    }

    @Test fun settingsAloneKeepOldBackground() {
        val withImage = pattern.copy(imageId = image)
        val d = BackgroundDraft(withImage).pick(withImage.copy(patternColorHex = "#000000", imageOpacity = 0.5f))
        assertEquals(image, d.spec.imageId)
        assertEquals("art.polka", d.spec.patternId)
        assertTrue(d.chosen.isEmpty())
    }

    @Test fun newImageResetsTransformAndGradientReplacesImage() {
        val moved = BackgroundSpec(imageId = "asset:a", imageOffsetX = 0.2f, imageScale = 0.5f)
        val d = BackgroundDraft(moved).pick(moved.copy(imageId = "asset:b"))
        assertFalse(d.spec.hasImageTransform)
        val g = GradientSpec(listOf("#FF0000", "#0000FF"))
        val withGradient = BackgroundDraft(moved).pick(moved.copy(gradient = g))
        assertEquals(g, withGradient.spec.gradient)
        assertNull("eski resim kalkar", withGradient.spec.imageId)
    }
}

/**
 * v41 arka plan penceresinde seçimler **Uygula** ile tuvale konur (eski arka plan kalkar, tek geri alma adımı); arka plan
 * eleman gibi seçilir: Seç'te boş kağıda dokunmak ya da Elemanlar panelindeki satır; seçiliyken değiştir, örtücülük, sil.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V41BackgroundUiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()
    private val polka = BackgroundSpec(patternId = "art.polka")

    private fun open(name: String, kind: CanvasKind = CanvasKind.ART, background: BackgroundSpec = polka) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind, background = background) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun bg() = vm().canvas.value!!.background

    /** v45 arka planın değiştir/sıfırla/bitti ve saydamlığı seçim menüsünün Düzenle listesinde: liste açılıp [tag]. */
    private fun bgEdit(tag: String) {
        if (!exists(tag)) compose.onNodeWithTag("bg_selection_menu_edit").performClick()
        waitFor { exists(tag) }
        compose.onNodeWithTag(tag).performClick()
    }

    private fun openSheet() {
        val node = hasContentDescription("Arka plan") and hasClickAction() and
            (hasAnyAncestor(hasTestTag("tool_row_context")) or hasAnyAncestor(hasTestTag("tool_row_draw")))
        if (compose.onAllNodes(node).fetchSemanticsNodes().isEmpty()) mode("Çiz")
        waitFor { compose.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
        runCatching { compose.onNode(node).performScrollTo() }
        compose.onNode(node).performClick()
        waitFor { exists("background_sheet") }
    }

    @Test
    fun applyReplacesOldBackgroundAsOneUndoStep() {
        open("Uygula")
        openSheet()
        compose.onNodeWithTag("bg_apply").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Suluboya kağıdı").performScrollTo().performClick()
        compose.onNodeWithContentDescription("#E8EEF6").performScrollTo().performClick()
        compose.waitForIdle()
        // Uygulanmadan tuval değişmez
        assertEquals("art.polka", bg().patternId)
        assertNull(bg().imageId)
        assertTrue(compose.onAllNodesWithText("Seçimler henüz uygulanmadı").fetchSemanticsNodes().isNotEmpty())
        shot("v41-01-background-pending")
        compose.onNodeWithTag("bg_apply").assertIsEnabled().performClick()
        waitFor { !exists("background_sheet") }
        waitFor { bg().paperImageId == "art.img.coldpress" }
        assertNull("eski desen kalktı", bg().patternId)
        assertEquals("#E8EEF6", vm().canvas.value!!.backgroundColorHex.uppercase())
        settleFor(300)
        shot("v41-02-background-applied")
        // Tek adımda geri alınır: desen ve kağıt rengi döner
        compose.runOnIdle { vm().undo() }
        waitFor { bg().patternId == "art.polka" && bg().imageId == null }
        assertEquals("#FFFFFF", vm().canvas.value!!.backgroundColorHex.uppercase())
        compose.runOnIdle { vm().redo() }
        waitFor { bg().paperImageId == "art.img.coldpress" && bg().patternId == null }
        // Kalıcı
        waitFor { runBlocking { repo().getCanvas(vm().canvas.value!!.localId)?.background?.paperImageId == "art.img.coldpress" } }
    }

    @Test
    fun closingWithoutApplyKeepsBackground() {
        open("Vazgeç")
        openSheet()
        compose.onNodeWithContentDescription("Suluboya kağıdı").performScrollTo().performClick()
        compose.waitForIdle()
        SystemBack.press()
        waitFor { !exists("background_sheet") }
        settleFor(200)
        assertEquals("art.polka", bg().patternId)
        assertNull(bg().imageId)
    }

    @Test
    fun backgroundSelectsLikeElementAndCanBeRemoved() {
        open("Seçilen arka plan")
        compose.runOnIdle { vm().setMode(EditMode.SELECT) }
        compose.waitForIdle()
        tap(screenOf(500f, 500f))
        waitFor { exists("bg_selected_title") }
        // Desenin örtücülüğü, değiştir ve sil; resim yokken taşıma tutamakları yok
        // v45 sil seçim menüsünde; örtücülük ve değiştir Düzenle listesinde
        assertTrue(exists("bg_selection_menu_delete"))
        compose.onNodeWithTag("bg_selection_menu_edit").performClick()
        waitFor { exists("bg_selected_opacity") && exists("bg_change") }
        assertFalse(exists("bg_transform_reset"))
        shot("v41-03-background-selected")
        compose.onNodeWithTag("bg_selection_menu_delete").performClick()
        waitFor { bg().isEmpty && !exists("bg_transform_overlay") }
        compose.runOnIdle { vm().undo() }
        waitFor { bg().patternId == "art.polka" }
        // Arka plan yokken boş yere dokunmak bir şey seçmez
        compose.runOnIdle { vm().redo() }
        waitFor { bg().isEmpty }
        tap(screenOf(500f, 500f))
        settleFor(200)
        assertFalse(exists("bg_transform_overlay"))
    }

    @Test
    fun elementsPanelRowAndChangeOpenSheet() {
        open("Panelden arka plan")
        compose.runOnIdle { vm().togglePanel(PanelTab.ELEMENTS) }
        waitFor { exists("elements_list") }
        compose.onNodeWithTag("elements_list").performScrollToNode(hasTestTag("element_background"))
        waitFor { exists("element_background") }
        compose.onNodeWithTag("element_background").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { exists("bg_selected_title") && !exists("elements_list") }
        bgEdit("bg_change")
        waitFor { exists("background_sheet") && !exists("bg_transform_overlay") }
    }

    @Test
    fun emptyTapWithSelectionClearsSelectionFirst() {
        open("Önce seçim kalkar")
        compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        val s = run {
            val view = canvasSize()
            val margin = 24f * compose.density.density
            minOf((view.x - 2 * margin) / 1000f, (view.y - 2 * margin) / 1000f)
        }
        drag(screenOf(200f, 200f), Offset(300f * s, 0f))
        waitFor { vm().canvas.value!!.strokes.size == 1 }
        compose.runOnIdle { vm().setMode(EditMode.SELECT) }
        tap(screenOf(350f, 200f))
        waitFor { vm().selectedIds.value.isNotEmpty() }
        tap(screenOf(600f, 800f))
        waitFor { vm().selectedIds.value.isEmpty() }
        assertFalse("ilk dokunuş yalnız seçimi kaldırır", exists("bg_transform_overlay"))
        tap(screenOf(600f, 800f))
        waitFor { exists("bg_selected_title") }
        // Elemana dokunmak arka planın seçimini kaldırır (katman dışına dokunarak kapanır)
        bgEdit("bg_transform_done")
        waitFor { !exists("bg_transform_overlay") }
    }
}
