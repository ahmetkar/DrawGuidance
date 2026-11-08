package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v23 (3): yazı ve şekil önerisi ayrı balonlarda; Ayarlar'da ayrı anahtarlar; şekil seçicide 3B Şekillerim bölümü;
 * katman satırında yukarı/aşağı düğmeleri (ekran görüntüleriyle).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V23SuggestShotsTest : V23EditorBase() {
    @Test
    fun splitSuggestionsAndPanels() {
        // Ayarlar: yazı ve şekil önerisi ayrı
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithText("Ayarlar").performSemanticsAction(SemanticsActions.OnClick)
        // v36 düzenleyici ayarları Çizim sekmesinde
        waitFor { compose.onAllNodesWithTag("settings_tab_drawing").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings_tab_drawing").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("editor_settings_card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("editor_settings_card").performScrollTo()
        waitForText("Yazı önerisi")
        waitForText("Şekil önerisi")
        // v38 çizgi önerisi de ayrı
        waitForText("Çizgi önerisi")
        shot("v23-28-settings-suggestions")
        compose.onNodeWithText("Tuvallerim").performSemanticsAction(SemanticsActions.OnClick)

        createCanvasAndOpen("Öneriler")
        val size = canvasSize()
        // v38 dört ayrı kenarla kare (birbirine yakın 4 eleman) → şekil ve çizgi önerisi ayrı balonlarda, yazı önerisi yok
        val l = size.x * 0.3f; val t = size.y * 0.25f; val e = size.x * 0.4f
        drag(Offset(l, t), Offset(e - 6f, 3f))
        drag(Offset(l + e, t + 4f), Offset(-3f, e - 8f))
        drag(Offset(l + e - 4f, t + e), Offset(-e + 8f, -3f))
        drag(Offset(l + 2f, t + e - 6f), Offset(-3f, -e + 10f))
        waitFor { canvas().strokes.size == 4 }
        advanceMain(900)
        waitFor(15_000) { exists("shape_suggest_chip") && exists("line_suggest_chip") }
        assertTrue(!exists("text_suggest_chip"))
        shot("v23-29-shape-suggestion")
        compose.onNode(hasContentDescription("Öneriyi kapat") and hasAnyAncestor(hasTestTag("shape_suggest_chip"))).performClick()

        // v25 3B şekiller Ekle menüsünde ayrı seçenek: "3B şekil ekle" penceresi
        addMenu("3B şekil ekle")
        waitFor { exists("model3d_picker") }
        waitForText("Yeni 3B şekil")
        shotAll("v23-30-shape-picker-3d")
        SystemBack.press()
        waitFor { !exists("model3d_picker") }

        // Katmanlar paneli: yukarı/aşağı düğmeleri
        mode("Çiz")
        // v47 Katmanlar mod menüsünde
        runCatching { compose.onNode(hasContentDescription("Katmanlar") and hasAnyAncestor(hasTestTag("mode_selector"))).performScrollTo() }
        compose.onNode(hasContentDescription("Katmanlar") and hasAnyAncestor(hasTestTag("mode_selector"))).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { exists("layers_panel") }
        compose.onNodeWithText("Yeni katman").performClick()
        waitFor { compose.onAllNodesWithContentDescription("Yukarı taşı").fetchSemanticsNodes().size >= 2 }
        settleFor(300)
        shot("v23-31-layers-order")
    }
}

/** v23 (2): 3B düzenleyicide parçalar paneli, köşe aracı, düzlemlere fırçayla çizim ve boya aracı (ekran görüntüleriyle). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V23EditorToolsShotsTest : V23EditorBase() {
    @Test
    fun editorTools() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithText("3B Şekiller").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("models3d_screen") }
        compose.onNodeWithText("Yeni 3B şekil").performClick()
        waitFor { exists("model3d_editor") }
        settleFor(1200)
        // Silindir ekle, parçalar paneli
        mode3("Ekle")
        panelItem3("Silindir")
        settleFor(800)
        compose.onNodeWithContentDescription("Parçalar").performClick()
        waitFor { exists("model3d_panel_parts") }
        settleFor(800)
        shot("v23-32-editor-parts")
        compose.onNodeWithTag("model3d_panel_close").performClick()
        // Köşe aracı: parçaya dokun, köşeler görünür
        tool3("Kenar")
        val vp = compose.onNodeWithTag("model3d_viewport").fetchSemanticsNode().size
        compose.onNodeWithTag("model3d_viewport").performTouchInput { click(Offset(vp.width * 0.42f, vp.height * 0.5f)) }
        settleFor(1500)
        shot("v23-33-editor-vertex")
        // Çiz: ön düzleme çizgi
        tool3("Çiz")
        settleFor(600)
        compose.onNodeWithTag("model3d_viewport").performTouchInput {
            down(Offset(vp.width * 0.2f, vp.height * 0.25f))
            for (i in 1..30) moveTo(Offset(vp.width * (0.2f + i * 0.02f), vp.height * (0.25f + 0.05f * sin(i / 4.0).toFloat())))
            up()
        }
        settleFor(1500)
        shot("v23-34-editor-draw")
        // Boya: v27 kapsam seçicisinde bütün parça
        tool3("Boya")
        subtool3("Parça")
        compose.onNodeWithTag("model3d_viewport").performTouchInput { click(Offset(vp.width * 0.5f, vp.height * 0.55f)) }
        settleFor(1500)
        shot("v23-35-editor-paint")
    }

}
