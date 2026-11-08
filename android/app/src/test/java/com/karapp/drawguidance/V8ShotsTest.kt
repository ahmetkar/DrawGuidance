package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v8 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü): tuval türleri, şekiller, yazı, kırpma, arka plan. */
abstract class V8ShotsBase : WideLayoutTestBase() {

    protected fun notesFlow(prefix: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        shot("$prefix-1-new-canvas")
        compose.onNodeWithText("Çalışma / Not").clickInPopup()
        compose.onNode(hasSetTextAction()).performTextInput("Geometri")
        shot("$prefix-2-new-notes")
        compose.onNodeWithText("Oluştur").clickInPopup()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("Fırça Seç").performClick()
        waitForText("Kurşun Kalem")
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        shot("$prefix-3-note-brushes")
        compose.onNodeWithText("Jel Kalem").performSemanticsAction(SemanticsActions.OnClick)
        waitForDescription("Seçili fırça: Jel Kalem")

        addMenu("Şekil ekle")
        waitForText("Geometrik Cisimler")
        compose.waitForIdle()
        shot("$prefix-4-shape-picker-notes")
        compose.onNodeWithText("Küp").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Küp")
        val size = canvasSize()
        drag(Offset(size.x * 0.15f, size.y * 0.15f), Offset(size.x * 0.35f, size.x * 0.35f))
        waitFor { canvas().strokes.isNotEmpty() }
        addMenu("Şekil ekle")
        // v15: grafikler ayrı bölümde
        waitForText("Grafik")
        compose.onAllNodesWithText("Grafik")[0].performClick()
        waitForText("Koordinat ekseni")
        compose.onNodeWithText("Koordinat ekseni").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Koordinat ekseni")
        val before = canvas().strokes.size
        drag(Offset(size.x * 0.55f, size.y * 0.55f), Offset(size.x * 0.35f, size.x * 0.35f))
        waitFor { canvas().strokes.size > before }
        shot("$prefix-5-shapes-drawn")

        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        tap(Offset(size.x * 0.2f, size.y * 0.55f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Küpün hacmi a³")
        compose.waitForIdle()
        shot("$prefix-6-inline-text")
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text != null } }
        // v15: "Yazıyı bitir" yazı eyleminden önceki moda döner
        compose.waitForIdle()
        shot("$prefix-7-notes-canvas")

        // v23: not tuvalinde eklenen yazı seçili olarak Taşı modunda; Bitti ile seçimsiz Seç'e geçilir
        val doneIcon = hasContentDescription("Bitti") and hasAnyAncestor(hasTestTag("mode_bar"))
        if (compose.onAllNodes(doneIcon).fetchSemanticsNodes().isNotEmpty()) compose.onNode(doneIcon).performClick()
        else compose.onNode(hasText("Bitti") and hasAnyAncestor(hasTestTag("mode_bar"))).performClick()
        compose.waitForIdle()
        // v17: kırpma ve boya kutusu tüm çizime (Çiz'in üst menüsünden)
        mode("Çiz")
        compose.waitForIdle()
        mode("Kırp")
        waitForText("Çerçevenin kenarlarını sürükle", substring = true)
        drag(Offset(size.x * 0.95f, size.y * 0.5f), Offset(-size.x * 0.2f, 0f), steps = 8)
        shot("$prefix-8-crop")
        compose.onNode(hasText("Kırp") and hasClickAction()).performClick()
        compose.waitForIdle()

        clickFill()
        waitForText("Renk seç")
        compose.onNodeWithTag("color_sv_area").performTouchInput { click(Offset(width * 0.12f, height * 0.05f)) }
        waitFor { canvas().backgroundColorHex != "#FFFFFF" }
        compose.waitForIdle()
        shot("$prefix-9-background")
        SystemBack.press()
        waitFor { compose.onAllNodesWithTag("color_pick_sheet").fetchSemanticsNodes().isEmpty() }
        shot("$prefix-10-final")
    }

    protected fun artShapesShot(prefix: String) {
        createCanvasAndOpen("Manzara")
        addMenu("Şekil ekle")
        // v42 resim tuvalinde nesne çizimleri yok: genel şekiller (afişler, semboller...) kategorilere ayrılmış
        waitForText("Afişler ve kurdeleler")
        compose.onAllNodesWithText("Afişler ve kurdeleler")[0].performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Kurdele afiş")
        compose.waitForIdle()
        shot("$prefix-11-shape-picker-art")
        compose.onNodeWithText("Kurdele afiş").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kurdele afiş")
        val size = canvasSize()
        drag(Offset(size.x * 0.15f, size.y * 0.35f), Offset(size.x * 0.6f, size.y * 0.15f))
        waitFor { canvas().strokes.isNotEmpty() }
        addMenu("Şekil ekle")
        waitForText("Semboller ve çerçeveler")
        compose.onAllNodesWithText("Semboller ve çerçeveler")[0].performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Güneş")
        compose.onNodeWithText("Güneş").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Güneş")
        val before = canvas().strokes.size
        drag(Offset(size.x * 0.6f, size.y * 0.15f), Offset(size.x * 0.25f, size.x * 0.25f))
        waitFor { canvas().strokes.size > before }
        shot("$prefix-12-art-shapes")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V8PortraitShotsTest : V8ShotsBase() {
    @Test fun portrait() = notesFlow("v8-portrait")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V8ArtShotsTest : V8ShotsBase() {
    @Test fun art() = artShapesShot("v8-art")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V8LandscapeShotsTest : V8ShotsBase() {
    @Test fun landscape() = notesFlow("v8-landscape")
}
