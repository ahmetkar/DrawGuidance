package com.karapp.drawguidance

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
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
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

/**
 * Tuvallerim, Resimlerim gibi galeri: her tuval kendi oranında sade bir önizleme, tarihe göre bölümler,
 * sıralama ve liste görünümü; uzun basınca seçilir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V14CanvasGalleryTest : EditorTestBase() {

    @Test
    fun canvasListIsAGalleryWithSectionsSortingAndListView() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            repository.createCanvas("Manzara", 1600, 1000, "#FFFFFF", false)
            repository.createCanvas("Ders Notu", 1240, 1754, "#FFFFFF", false, kind = CanvasKind.NOTES, pageCount = 3)
        }
        waitForText("Manzara")
        waitFor { compose.onAllNodesWithTag("canvas_gallery").fetchSemanticsNodes().isNotEmpty() }
        waitForText("Bugün")
        // v15: bilgi satırında kayıt boyutu da var
        waitForText("Not · 1240×1754 · 3 sayfa", substring = true)
        waitForText("1600×1000", substring = true)
        // Uzun bas: seçilir (üst çubuk seçili tuvalin işlemlerini gösterir)
        compose.onAllNodesWithText("Manzara")[0].performTouchInput { longClick() }
        waitForText("Seçili tuval")
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { compose.onAllNodesWithText("Seçili tuval").fetchSemanticsNodes().isEmpty() }
        // Liste görünümü ve ada göre sıralama
        compose.onNodeWithContentDescription("Liste görünümü").performClick()
        waitFor { compose.onAllNodesWithTag("canvas_gallery").fetchSemanticsNodes().isEmpty() }
        waitForText("Manzara")
        compose.onNodeWithContentDescription("Sırala").performClick()
        waitForText("Ada göre")
        compose.onNodeWithText("Ada göre").performClick()
        compose.onNodeWithContentDescription("Galeri görünümü").performClick()
        waitFor { compose.onAllNodesWithTag("canvas_gallery").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("ada göre sıralıyken tarih bölümü yok", compose.onAllNodesWithText("Bugün").fetchSemanticsNodes().isEmpty())
        // Tuvale dokununca açılır
        compose.onNodeWithText("Manzara").performClick()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
    }
}

/**
 * Günlük denetimi: uygulama açılıp tuval açılınca, çizilince ve geri dönülünce uygulama kaynaklı hata
 * yazılmaz; Android 13+ geri hareketi uyarısı (OnBackInvokedCallback) verilmez.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V14LogAuditTest : EditorTestBase() {

    @Test
    fun noAppErrorsOrBackCallbackWarnings() {
        createCanvasAndOpen("Günlük")
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.4f), Offset(size.x * 0.4f, 0f))
        waitFor { canvas().strokes.size == 1 }
        compose.onNodeWithContentDescription("Geri").performClick()
        waitForText("Günlük")
        compose.onNode(hasText("Kaydedilenler") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasText("Ayarlar") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val logs = ShadowLog.getLogs()
        val backWarnings = logs.filter { it.msg?.contains("OnBackInvokedCallback is not enabled") == true }
        assertTrue("bildirimde enableOnBackInvokedCallback yok", ManifestFlags.declaresBackCallback())
        assertTrue("geri hareketi uyarısı: ${backWarnings.size}", backWarnings.isEmpty())
        // Test ortamına özgü (Robolectric) uyarılar dışında hata yok
        val ignored = setOf("PackageParser", "FileTestStorage", "VirtualDeviceManager")
        val errors = logs.filter { it.type >= Log.ERROR && it.tag !in ignored }
        assertTrue("hata günlükleri: " + errors.joinToString { "${it.tag}: ${it.msg}" }, errors.isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V14PredictiveBackTest : EditorTestBase() {

    /**
     * Öngörülü geri hareketi açıkken (bildirimde enableOnBackInvokedCallback) sistem geri hareketi
     * cihazdaki yoldan (pencerenin OnBackInvokedCallback'i) açılır menüyü kapatır, Seç modundan İncele'ye, oradan
     * Çiz'e döner, düzenleyiciden listeye çıkar ve listedeki seçimi kaldırır.
     */
    @Test
    fun systemBackWorksWithPredictiveBack() {
        createCanvasAndOpen("Geri")
        // v18: Ekle modu (eklenecekler üst menüde) → geri → seçimsiz Seç
        mode("Ekle")
        waitForText("Eklemek istediğin yere dokun")
        SystemBack.press()
        waitFor { compose.onAllNodesWithText("Eklemek istediğin yere dokun").fetchSemanticsNodes().isEmpty() }
        // v17: Seç (seçimsiz) → geri → Çiz; Çiz'den geri → liste
        mode("Seç")
        waitForText("Elemana dokunarak seç", substring = true)
        SystemBack.press()
        waitFor { compose.onAllNodesWithText("Elemana dokunarak seç", substring = true).fetchSemanticsNodes().isEmpty() }
        SystemBack.press()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isEmpty() }
        waitForText("Geri")
        compose.onAllNodesWithText("Geri")[0].performTouchInput { longClick() }
        waitForText("Seçili tuval")
        SystemBack.press()
        waitFor { compose.onAllNodesWithText("Seçili tuval").fetchSemanticsNodes().isEmpty() }
    }
}
