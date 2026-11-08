package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v7 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü): başlıktaki kaydet düğmesi, kayıt penceresi, fırça seçici, Resimlerim. */
abstract class V7ShotsBase : WideLayoutTestBase() {
    protected fun flow(prefix: String) {
        createCanvasAndOpen("Deniz Feneri")
        val size = canvasSize()
        pickBrush("Marker")
        drag(Offset(size.x * 0.2f, size.y * 0.4f), Offset(size.x * 0.5f, 60f))
        waitFor { canvas().strokes.size == 1 }
        shot("$prefix-1-editor-titlebar")

        compose.onNodeWithContentDescription("Fırça Seç").performClick()
        waitForText("Fosforlu Kalem")
        compose.mainClock.advanceTimeBy(1000)
        waitForIdleShort()
        shot("$prefix-2-brush-picker")
        compose.onNodeWithText("Kömür").clickInPopup()
        waitForDescription("Seçili fırça: Kömür")

        compose.onNodeWithContentDescription("Resim olarak kaydet").performClick()
        waitFor { compose.onAllNodesWithTag("image_save_options_dialog").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        shot("$prefix-3-save-options")
        compose.onNodeWithTag("save_options_confirm").clickInPopup()
        waitForText("Dosya boyutu")
        shot("$prefix-3-saved-dialog")
        compose.onNodeWithText("Tamam").clickInPopup()
        waitFor { compose.onAllNodesWithText("Resim kaydedildi").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { compose.onAllNodes(hasText("Kaydedilenler") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Kaydedilenler") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForText(" px · ", substring = true)
        shot("$prefix-4-images-list")
    }

    private fun waitForIdleShort() = compose.waitForIdle()
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w360dp-h780dp-xxhdpi")
class V7PortraitShotsTest : V7ShotsBase() {
    @Test fun portrait() = flow("v7-portrait")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V7LandscapeShotsTest : V7ShotsBase() {
    @Test fun landscape() = flow("v7-landscape")
}
