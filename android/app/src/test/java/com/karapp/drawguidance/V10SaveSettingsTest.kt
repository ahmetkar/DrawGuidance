package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.SavedImageDao
import com.karapp.drawguidance.data.settings.ExportSettingsStore
import com.karapp.drawguidance.data.settings.ImageFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 5 ve 6: kaydedilecek dizin (Galeri albümü ya da seçilen klasör) ve varsayılan çözünürlük/biçim Ayarlar'dan
 * değiştirilir; kaydetme penceresi bu varsayılanlarla açılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V10SaveSettingsTest : EditorTestBase() {

    private fun images() = runBlocking { GlobalContext.get().get<SavedImageDao>().observeAll().first() }

    @Test
    fun exportSettingsFromSettingsScreenAreUsedBySaveDialog() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNode(hasText("Ayarlar") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        // v36 kaydetme ayarları Kaydetme sekmesinde
        waitFor { compose.onAllNodesWithTag("settings_tab_saving").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings_tab_saving").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Resim kaydetme")
        compose.onNodeWithTag("export_settings_card").performScrollTo()
        compose.onNodeWithTag("export_location").assertExists()
        waitForText("Pictures/DrawGuidance")

        // Galeri albümü
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("export_settings_card"))).performTextReplacement("Okul")
        compose.onNode(hasText("Kaydet") and hasAnyAncestor(hasTestTag("export_settings_card"))).performScrollTo().performClick()
        waitForText("Pictures/Okul")
        // Varsayılan çözünürlük ve biçim
        compose.onNode(hasText("2×") and hasAnyAncestor(hasTestTag("export_settings_card"))).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasText("JPEG") and hasAnyAncestor(hasTestTag("export_settings_card"))).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        val store = GlobalContext.get().get<ExportSettingsStore>()
        waitFor { store.settings.value.defaultFormat == ImageFormat.JPEG }
        assertEquals(2f, store.settings.value.defaultScale, 0f)
        assertEquals("Okul", store.settings.value.albumName)

        // Kaydetme penceresi bu varsayılanlarla açılır
        compose.onNode(hasText("Tuvallerim") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        createCanvasAndOpen("Ayar Testi")
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.4f), Offset(size.x * 0.4f, 40f))
        waitFor { canvas().strokes.size == 1 }
        compose.onNodeWithContentDescription("Resim olarak kaydet").performClick()
        waitFor { compose.onAllNodesWithTag("image_save_options_dialog").fetchSemanticsNodes().isNotEmpty() }
        waitForText("2160 × 2160 piksel")
        waitForText("JPEG kalitesi", substring = true)
        compose.onNodeWithTag("save_location").assertExists()
        waitForText("Pictures/Okul")
        compose.onNodeWithTag("save_options_confirm").clickInPopup()
        waitForText("Resim kaydedildi")
        val saved = images().single()
        assertEquals(2160, saved.width)
        assertTrue(saved.location, saved.location.contains("Okul") && saved.location.endsWith(".jpg"))
        compose.onNodeWithText("Tamam").clickInPopup()
    }
}
