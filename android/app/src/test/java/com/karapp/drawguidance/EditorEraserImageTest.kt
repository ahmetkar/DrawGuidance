package com.karapp.drawguidance

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.local.CanvasAssetDao
import com.karapp.drawguidance.data.model.BrushType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Silgi üst menüde (fırça listesinde değil), "Opaklık" etiketi, fırçaya özgü boyutlar,
 * tamamen silinen çizimlerin otomatik kaldırılması ve resim ekleme kontrolleri.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorEraserImageTest : EditorTestBase() {

    private fun indicatorSize(): String {
        val node = compose.onNodeWithContentDescription("Seçili fırça:", substring = true).fetchSemanticsNode()
        return node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }.first { it.endsWith("px") }
    }

    private fun pickImage(file: File) {
        // v18: eklenecekler Ekle modunun üst menüsünde
        addMenu("Resim ekle")
        compose.waitForIdle()
        val shadow = shadowOf(compose.activity)
        val started = shadow.nextStartedActivityForResult
        requireNotNull(started) { "resim seçici açılmadı" }
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        compose.waitForIdle()
    }

    @Test
    fun eraserSizesPruningAndImageImport() {
        createCanvasAndOpen("Silgi ve Resim")

        // Fırça listesinde silgi yok
        compose.onNodeWithContentDescription("Fırça Seç").performScrollTo().performClick()
        waitForText("Fosforlu Kalem")
        assertTrue(compose.onAllNodesWithText("Silgi").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Marker").clickInPopup()
        waitForDescription("Seçili fırça: Marker")

        // Fırçaya özgü varsayılan boyutlar; değiştirilen boyut o fırça için hatırlanır
        waitFor { indicatorSize() == "14px" }
        openProperties()
        waitForText("Opaklık")
        assertTrue(compose.onAllNodesWithText("Vurgu", substring = true).fetchSemanticsNodes().isEmpty())
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(com.karapp.drawguidance.ui.screens.canvasedit.components.SizeScale.toPosition(40f)) }
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        waitFor { indicatorSize() == "40px" }
        pickBrush("Pastel Boya")
        waitFor { indicatorSize() == "18px" }
        pickBrush("Kalem")
        waitFor { indicatorSize() == "6px" }
        pickBrush("Marker")
        waitFor { indicatorSize() == "40px" }

        // Üst menüdeki Silgi: aç / kapat (son fırçaya döner)
        compose.onNodeWithContentDescription("Silgi").performScrollTo().performClick()
        waitForDescription("Seçili fırça: Silgi")
        waitFor { indicatorSize() == "30px" }
        compose.onNodeWithContentDescription("Silgi").performScrollTo().performClick()
        waitForDescription("Seçili fırça: Marker")

        // Tamamen silinen çizim hem listeden hem kayıttan kalkar
        pickBrush("Kalem")
        val size = canvasSize()
        val a = Offset(size.x * 0.3f, size.y * 0.3f)
        drag(a, Offset(200f, 0f))
        drag(Offset(size.x * 0.3f, size.y * 0.6f), Offset(200f, 0f))
        waitFor { canvas().strokes.size == 2 }
        compose.onNodeWithContentDescription("Silgi").performScrollTo().performClick()
        waitForDescription("Seçili fırça: Silgi")
        drag(a - Offset(40f, 0f), Offset(280f, 0f))
        waitFor { canvas().strokes.size == 1 }
        assertEquals(BrushType.PENCIL, canvas().strokes.single().brushType)
        waitForText("Tamamen silinen 1 eleman", substring = true)
        clickElements()
        waitForText("Kalem 1")
        assertTrue(compose.onAllNodesWithText("Kalem 2").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().strokes.size == 2 }

        // Çok detaylı resim reddedilir ve nedeni gösterilir
        val dir = compose.activity.cacheDir
        val noisy = File(dir, "gurultu.png").apply { writeBytes(ImageImportValidatorTest.png(ImageImportValidatorTest.noise(300, 300))) }
        pickImage(noisy)
        waitForText("Resim eklenemedi")
        waitForText("çok detaylı", substring = true)
        compose.onNodeWithText("Tamam").clickInPopup()
        waitFor { compose.onAllNodesWithText("Resim eklenemedi").fetchSemanticsNodes().isEmpty() }

        // 1 MB'yi aşan dosya reddedilir
        val huge = File(dir, "buyuk.png").apply { writeBytes(ImageImportValidatorTest.png(ImageImportValidatorTest.noise(1024, 1024))) }
        pickImage(huge)
        waitForText("Dosya çok büyük", substring = true)
        compose.onNodeWithText("Tamam").clickInPopup()

        // Yüksek çözünürlüklü sade resim: uyarı + onayla eklenir, küçültülüp saklanır
        val wide = File(dir, "logo.jpg").apply { writeBytes(ImageImportValidatorTest.jpeg(ImageImportValidatorTest.simpleDrawing(3200, 1600))) }
        pickImage(wide)
        waitForText("Resim eklenmeden önce")
        waitForText("küçültülecek", substring = true)
        compose.onNodeWithText("Yine de ekle").clickInPopup()
        waitFor { canvas().strokes.any { it.brushType == BrushType.IMAGE } }
        val image = canvas().strokes.single { it.brushType == BrushType.IMAGE }
        assertEquals(4, image.points.size)
        val assets = runBlocking { GlobalContext.get().get<CanvasAssetDao>().getByIds(listOf(image.assetId!!)) }
        assertEquals(1600, assets.single().width)
        assertTrue("saklanan resim orijinalden büyük olmamalı", assets.single().data.size <= wide.length())
        waitForText("Resim eklendi", substring = true)
        clickElements()
        waitForText("Resim 3")
    }
}
