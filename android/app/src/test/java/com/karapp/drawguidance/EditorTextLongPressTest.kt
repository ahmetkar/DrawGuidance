package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Yazı ekleme (yazı tipi ayarlarıyla), ayarlar panelinde seçili yazının yazı tipini değiştirme, metni düzenleme ve
 * elemana 2 sn basılı tutunca seçim moduna geçip o elemanı seçme.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorTextLongPressTest : EditorTestBase() {

    private fun inBar(text: String) = hasText(text) and hasAnyAncestor(hasTestTag("inline_font_bar"))

    /** Yazı tipi düğmesinden listeyi açıp [title] yazı tipini seçer (v13: yazı tipleri listede). */
    private fun pickFont(container: String, title: String) {
        val button = compose.onNode(hasTestTag("font_picker_button") and hasAnyAncestor(hasTestTag(container)))
        // Panelde düğme görünür alana kaydırılır (satır içi çubuk kaydırılmaz)
        runCatching { button.performScrollTo() }
        button.performClick()
        waitFor { compose.onAllNodesWithTag("font_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("font_list").performScrollToNode(hasContentDescription(title))
        compose.onNode(hasContentDescription(title) and hasClickAction()).performClick()
        waitFor { compose.onAllNodesWithTag("font_list").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun addTextWithFontsEditItAndLongPressSelects() {
        createCanvasAndOpen("Yazı Testi")
        val size = canvasSize()

        // Kalibrasyon çizgisi: ekran ↔ tuval dönüşümü
        val start = Offset(size.x * 0.2f, size.y * 0.8f)
        drag(start, Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val map = mappingFrom(start, size.x * 0.5f)

        // 2 sn basılı tutma: çizgi seçilir, Seç moduna geçilir, yeni çizim eklenmez
        longPress(start + Offset(size.x * 0.25f, 0f))
        waitForText("Dokun: elemanı seç", substring = true)
        waitForText("1 eleman seçili", substring = true)
        assertEquals(1, canvas().strokes.size)
        compose.onNodeWithText("Bitti").performClick()
        // v16: iş bitince İncele moduna geçilir; çizmek için Çiz seçilir
        mode("Çiz")

        // Boş yere basılı tutmak seçim yapmaz (Çiz modunda kalınır, nokta çizilir)
        longPress(Offset(size.x * 0.5f, size.y * 0.3f))
        waitFor { canvas().strokes.size == 2 }
        assertTrue(compose.onAllNodesWithText("Dokun: elemanı seç", substring = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { canvas().strokes.size == 1 }

        // Yazı modu: pencere açılmaz, dokunulan yere tuvalin içinde yazma alanı açılır
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        val tapAt = Offset(size.x * 0.25f, size.y * 0.35f)
        tap(tapAt)
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("pencere açılmamalı", compose.onAllNodesWithTag("text_dialog").fetchSemanticsNodes().isEmpty())
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Merhaba")
        pickFont("inline_font_bar", "Noto Serif")
        compose.onNode(hasContentDescription("Kalın") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.size == 2 }
        val text = canvas().strokes.last()
        assertEquals(BrushType.TEXT, text.brushType)
        assertEquals("Merhaba", text.text!!.content)
        assertEquals(TextFont.SERIF, text.text!!.font)
        assertTrue(text.text!!.bold)
        // Yazı dokunulan noktanın hizasında başlar
        val tapCanvas = Offset((tapAt.x - map.offset.x) / map.scale, (tapAt.y - map.offset.y) / map.scale)
        assertEquals(tapCanvas.x, text.points[0].x, 2f)
        val box = ElementOps.bounds(text)!!
        assertTrue("yazı dokunulan yeri kapsamalı: $box / $tapCanvas", tapCanvas.y in box.top..box.bottom)

        // Yazıya uzun basınca seçilir; ayarlar panelinde yazı ayarları seçili yazıya uygulanır
        // v16: "Yazıyı bitir" İncele moduna geçer; basılı tutarak seçmek için Çiz seçilir
        waitFor { compose.onAllNodesWithTag("mode_bar").fetchSemanticsNodes().isEmpty() }
        mode("Çiz")
        longPress(map.toScreen(box.center.x, box.center.y))
        waitForText("1 eleman seçili", substring = true)
        waitForText("Metni Düzenle")
        // Yazı tipi ayarları hem Fırça hem Elemanlar panelinde seçili yazıya uygulanır
        clickElements()
        waitForText("Seçili 1 yazıya uygulanır")
        pickFont("text_style_controls", "El Yazısı")
        compose.onNode(hasContentDescription("Altı çizili") and hasAnyAncestor(hasTestTag("text_style_controls"))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.last().text?.let { it.font == TextFont.HANDWRITING && it.underline } == true }
        val styled = canvas().strokes.last()
        assertEquals(text.points[0], styled.points[0])
        // İki ayar değişikliği tek tek geri alınabilir
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()

        // Metni düzenle: yazı olduğu yerde açılır
        compose.onNodeWithText("Metni Düzenle").performClick()
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        val field = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor")))
        field.performTextClearance()
        field.performTextInput("Merhaba Dünya")
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.last().text?.content == "Merhaba Dünya" }
        val edited = canvas().strokes.last()
        assertEquals(TextFont.HANDWRITING, edited.text!!.font)
        assertTrue("uzun metin daha geniş kutu", ElementOps.bounds(edited)!!.width > ElementOps.bounds(styled)!!.width)
        assertEquals(2, canvas().strokes.size)
    }
}
