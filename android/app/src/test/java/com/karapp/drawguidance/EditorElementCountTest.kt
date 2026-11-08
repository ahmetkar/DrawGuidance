package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Elemanlar listesi yalnızca gerçekten birleşik çizgileri tek eleman sayar; silgi bildirimindeki sayı
 * listeden kalkan eleman sayısıyla aynıdır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorElementCountTest : EditorTestBase() {

    private fun elementRows(): Int = (1..20).count { n -> compose.onAllNodesWithText("Kalem $n").fetchSemanticsNodes().isNotEmpty() }

    private fun openElements() {
        clickElements()
        compose.waitForIdle()
    }

    private fun closePanel() {
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()
    }

    @Test
    fun toastCountMatchesListAndErasedCrossingSplitsElements() {
        createCanvasAndOpen("Sayım")
        waitForDescription("Seçili fırça: Kalem")
        val size = canvasSize()
        val cx = size.x * 0.35f
        val cy = size.y * 0.3f

        // Eleman A: birbirini kesen 3 kısa çizgi; Eleman B: uzakta tek çizgi
        drag(Offset(cx - 70f, cy), Offset(140f, 0f))
        drag(Offset(cx, cy - 70f), Offset(0f, 140f))
        drag(Offset(cx - 60f, cy - 60f), Offset(120f, 120f))
        drag(Offset(size.x * 0.2f, size.y * 0.75f), Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 4 }
        waitFor { ElementOps.index(canvas().strokes).elements.size == 2 }
        openElements()
        waitFor { elementRows() == 2 }
        closePanel()

        // Silgi en büyük boyuta alınır ve A tek geçişte tamamen silinir (3 çizim, kesişimler de silinir)
        compose.onNodeWithContentDescription("Silgi").performScrollTo().performClick()
        waitForDescription("Seçili fırça: Silgi")
        openProperties()
        waitForText("Fırça Boyutu")
        compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress))[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(com.karapp.drawguidance.ui.screens.canvasedit.components.SizeScale.toPosition(80f)) }
        closePanel()
        drag(Offset(cx - 150f, cy), Offset(300f, 0f), steps = 30)
        waitFor(20_000) { canvas().strokes.count { it.brushType.name == "PENCIL" } == 1 }
        // Bildirim, listede görünen eleman sayısını söyler (eski sürüm çizim sayısını, yani 3'ü gösteriyordu)
        waitForText("Tamamen silinen 1 eleman", substring = true)
        openElements()
        waitFor { elementRows() == 1 }
        closePanel()

        // Artı işareti gibi kesişen iki yeni çizgi tek eleman; kesişim silinince iki ayrı eleman
        compose.onNodeWithContentDescription("Silgi").performScrollTo().performClick()
        waitForDescription("Seçili fırça: Kalem")
        drag(Offset(cx - 120f, cy), Offset(240f, 0f))
        drag(Offset(cx, cy - 120f), Offset(0f, 240f))
        waitFor { canvas().strokes.count { it.brushType.name == "PENCIL" } == 3 }
        openElements()
        waitFor { elementRows() == 2 }
        closePanel()

        compose.onNodeWithContentDescription("Silgi").performScrollTo().performClick()
        waitForDescription("Seçili fırça: Silgi")
        openProperties()
        waitForText("Fırça Boyutu")
        compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress))[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(com.karapp.drawguidance.ui.screens.canvasedit.components.SizeScale.toPosition(20f)) }
        closePanel()
        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(Offset(cx, cy)) }
        waitFor { canvas().strokes.count { it.brushType.name == "ERASER" } >= 1 && ElementOps.index(canvas().strokes).elements.size == 3 }
        assertEquals(3, ElementOps.index(canvas().strokes).elements.size)
        openElements()
        waitFor { elementRows() == 3 }
    }
}
