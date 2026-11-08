package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.SavedImageDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Resim olarak kaydetme tuval listesinden (kaydetme penceresiyle) yapılır; alt menüden Resimlerim'e geçilip silinir. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class CanvasImageExportTest : EditorTestBase() {

    private fun images() = runBlocking { GlobalContext.get().get<SavedImageDao>().observeAll().first() }

    @Test
    fun saveImageFromCanvasListAndManageInImagesTab() {
        createCanvasAndOpen("Deniz Feneri")
        val size = canvasSize()
        drag(Offset(size.x * 0.3f, size.y * 0.4f), Offset(250f, 120f))
        waitFor { canvas().strokes.size == 1 }

        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isEmpty() }
        waitForText("Deniz Feneri")

        compose.onAllNodesWithText("Deniz Feneri")[0].performTouchInput { longClick() }
        waitForDescription("Resim olarak kaydet")
        compose.onNodeWithContentDescription("Resim olarak kaydet").performClick()
        // Kaydetme ayarlarını soran pencere
        waitFor { compose.onAllNodesWithTag("image_save_options_dialog").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(images().isEmpty())
        compose.onNodeWithTag("save_options_confirm").clickInPopup()
        waitForText("Resim kaydedildi", substring = true)
        waitForText("Dosya boyutu")
        compose.onNodeWithText("Tamam").clickInPopup()
        assertEquals(1, images().size)
        assertEquals("Deniz Feneri", images().single().title)
        assertTrue(images().single().location.contains("DrawGuidance"))

        // Alt menü: Resimlerim
        compose.onAllNodesWithText("Kaydedilenler").fetchSemanticsNodes()
        compose.onNode(hasText("Kaydedilenler") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForDescription("Resmi sil")
        compose.onNodeWithContentDescription("Resmi sil").performClick()
        waitForText("Bu resim cihazdan kalıcı olarak silinecek.")
        compose.onNode(hasText("Resmi sil") and hasClickAction()).clickInPopup()
        waitFor { images().isEmpty() }

        // Alt menü: Tuvallerim'e dönüş
        compose.onNode(hasText("Tuvallerim") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Deniz Feneri")
        compose.onNodeWithText("Deniz Feneri").assertExists()
    }
}
