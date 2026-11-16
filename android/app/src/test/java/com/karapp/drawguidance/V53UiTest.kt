package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.comic.ComicEffects
import com.karapp.drawguidance.content.comic.ComicLines
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.comic.ComicSfx
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * v53 Android arayüzü: çizgi romanda Ekran tonları ve Desenler ayrı düğme/pencere (ton eklenir, seçili şekle uygulanır
 * ve kaldırılır), yapı kılavuzu penceresi ve tuval üstü kılavuz, not almada hızlı yazı kutusu (Enter ile yazı eklenir
 * ve kutu alta geçer), seçimde öteki elemanların soluklaşması.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V53UiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openCanvas(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1400, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun strokes(): List<Stroke> = vm().canvas.value?.strokes.orEmpty()

    private fun click(tag: String) {
        runCatching { compose.onNodeWithTag(tag).performScrollTo() }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun tonesAndPatternsHaveTheirOwnWindows() {
        openCanvas("Tonlar", CanvasKind.COMIC)
        addMenu("Ekran tonları")
        waitFor { exists("tones_sheet") }
        click("tone_soft_shadow")
        shot("v53-tones-sheet")
        click("tone_insert")
        waitFor { strokes().any { it.shape?.style?.tone?.id == "tone_dot" } }
        // Eklenen alan seçili: desen uygulanır, sonra kaldırılır
        addMenu("Desenler")
        waitFor { exists("patterns_sheet") }
        click("tone_flowers")
        click("tone_apply")
        waitFor { strokes().any { it.shape?.style?.tone?.id == "pat_flowers" } }
        addMenu("Desenler")
        waitFor { exists("tone_remove") }
        click("tone_remove")
        waitFor { strokes().none { it.shape?.style?.tone != null } }
        assertTrue(vm().canvas.value!!.strokes.isNotEmpty())
    }

    @Test
    fun structureGuideOpensAndPlaces() {
        openCanvas("Kılavuz", CanvasKind.COMIC)
        mode("Çiz")
        waitFor { exists("tool_structure") }
        click("tool_structure")
        waitFor { exists("structure_sheet") }
        click("structure_human_front")
        waitFor { exists("structure_overlay") }
        shot("v53-structure-guide")
        // Kılavuz tuvale yazılmaz
        assertTrue(strokes().isEmpty())
    }

    @Test
    fun quickTextBoxAddsLinesBelowEachOther() {
        openCanvas("Hızlı yazı", CanvasKind.NOTES)
        addMenu("Yazı kutusu")
        waitFor { exists("quick_text_field") }
        compose.onNodeWithTag("quick_text_field").performTextInput("Birinci satır")
        click("quick_text_commit")
        waitFor { strokes().count { it.text != null } == 1 }
        compose.onNodeWithTag("quick_text_field").performTextInput("İkinci satır")
        click("quick_text_commit")
        waitFor { strokes().count { it.text != null } == 2 }
        val (a, b) = strokes().filter { it.text != null }
        assertTrue("ikinci yazı birincinin altında", b.points.minOf { it.y } > a.points.minOf { it.y })
        assertTrue(a.text!!.box?.balloon == null)
        shot("v53-quick-text")
        click("quick_text_close")
        waitFor { !exists("quick_text_box") }
    }
}
