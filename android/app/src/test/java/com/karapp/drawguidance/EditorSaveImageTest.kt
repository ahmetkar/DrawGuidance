package com.karapp.drawguidance

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.SavedImageDao
import com.karapp.drawguidance.ui.components.formatFileSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Çizim ekranında başlığın en sağındaki "Resim olarak kaydet": önce kaydetme ayarları (dosya adı, biçim,
 * çözünürlük) sorulur; kaydedince boyut, dosya boyutu ve konum penceresi açılır; Resimlerim'de boyut görünür.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorSaveImageTest : EditorTestBase() {

    private fun images() = runBlocking { GlobalContext.get().get<SavedImageDao>().observeAll().first() }

    @Test
    fun saveFromEditorShowsSizeAndLocationAndListShowsSize() {
        createCanvasAndOpen("Kayıt Testi")
        val size = canvasSize()
        pickBrush("Marker")
        drag(Offset(size.x * 0.2f, size.y * 0.4f), Offset(size.x * 0.5f, 60f))
        waitFor { canvas().strokes.size == 1 }

        // Düğme başlık satırının en sağında
        val save = compose.onNodeWithContentDescription("Resim olarak kaydet").fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithContentDescription("Geri").fetchSemanticsNode().boundsInRoot
        val title = compose.onAllNodesWithText("Kayıt Testi").fetchSemanticsNodes().first().boundsInRoot
        val rootWidth = compose.activity.window.decorView.width
        assertTrue("başlık satırında (geri ile aynı hizada) olmalı", kotlin.math.abs(save.center.y - back.center.y) < 4f)
        assertTrue("en sağda olmalı: $save / genişlik $rootWidth", save.right > title.right && rootWidth - save.right < 60f * compose.density.density)

        // Kaydet: önce kaydetme ayarlarını soran pencere açılır
        compose.onNodeWithContentDescription("Resim olarak kaydet").performClick()
        waitFor { compose.onAllNodesWithTag("image_save_options_dialog").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("henüz kaydedilmedi", images().isEmpty())
        compose.onNodeWithText("Dosya adı").assertExists()
        waitForText("1080 × 1080 piksel")
        compose.onNode(hasText("Kayıt", substring = true) and hasSetTextAction()).performTextReplacement("Kayit Testi Buyuk")
        compose.onNodeWithText("2×").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("2160 × 2160 piksel")
        compose.onNodeWithText("JPEG").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("JPEG kalitesi", substring = true)
        waitForText("Kayit_Testi_Buyuk.jpg")
        compose.onNodeWithTag("save_location").assertExists()
        compose.onNodeWithTag("save_options_confirm").clickInPopup()

        waitForText("Resim kaydedildi")
        waitForText("2160 × 2160 piksel")
        compose.onNodeWithText("Dosya boyutu").assertExists()
        compose.onNodeWithText("Konum").assertExists()

        val saved = images().single()
        assertEquals("Kayıt Testi", saved.title)
        assertEquals(2160, saved.width)
        assertTrue("dosya adı ve biçim: ${saved.location}", saved.location.endsWith("Kayit_Testi_Buyuk.jpg"))
        val path = Uri.parse(saved.uri).path
        val realSize = if (saved.uri.startsWith("file:")) File(path!!).length() else saved.sizeBytes
        assertTrue("dosya boyutu kaydedilmeli", saved.sizeBytes > 0)
        assertEquals(realSize, saved.sizeBytes)
        compose.onNodeWithText(formatFileSize(saved.sizeBytes)).assertExists()
        compose.onNodeWithText(saved.location).assertExists()
        compose.onNodeWithText("Tamam").clickInPopup()
        waitFor { compose.onAllNodesWithText("Resim kaydedildi").fetchSemanticsNodes().isEmpty() }

        // Resimlerim: boyut ve dosya boyutu satırı
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { compose.onAllNodes(hasText("Kaydedilenler") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Kaydedilenler") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("2160×2160 px · ${formatFileSize(saved.sizeBytes)}", substring = true)
    }
}
