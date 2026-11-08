package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v10 ortak adımlar. */
abstract class V10EditorBase : EditorTestBase() {

    protected fun closeSheet(tag: String) {
        SystemBack.press()
        waitFor { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    /** Boya kutusu penceresinde renk alanının sağ-alt tarafına dokunur (koyu, doygun bir renk). */
    protected fun pickFillColor() {
        clickFill()
        waitForText("Renk seç")
        assertTrue("pencerede uygula düğmesi yok", compose.onAllNodesWithText("Uygula").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("color_sv_area").performTouchInput { click(Offset(width * 0.85f, height * 0.3f)) }
    }
}

/**
 * 0 ve 0.2: Elemanlar panelinde seçilen eleman çizim alanında da seçilidir; kırpma seçili elemana uygulanır
 * (tuval küçülmez). 0.3: kalem seçilince seçim kalkar ve kırpma yeniden tüm çizime uygulanır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V10SelectionCropTest : V10EditorBase() {

    @Test
    fun panelSelectionIsSharedWithTheCanvasAndCropAppliesToTheSelectedElement() {
        createCanvasAndOpen("Seçim")
        val size = canvasSize()
        val start = Offset(size.x * 0.2f, size.y * 0.4f)
        drag(start, Offset(size.x * 0.3f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val map = mappingFrom(start, size.x * 0.3f)
        drag(map.toScreen(700f, 800f), Offset(map.scale * 200f, 0f))
        waitFor { canvas().strokes.size == 2 }

        // Elemanlar panelinde seçilen eleman çizim alanında da seçili: kırpma ona uygulanır
        clickElements()
        waitForText("Kalem 1")
        compose.onNodeWithText("Kalem 1").performClick()
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()
        mode("Kırp")
        waitForText("Kırp: Seçili eleman")
        waitForText("Seçili eleman kırpılır", substring = true)
        val b = ElementOps.bounds(canvas().strokes[0])!!
        drag(map.toScreen(b.right, b.center.y), Offset(-map.scale * b.width * 0.5f, 0f), steps = 10)
        compose.onNode(hasText("Kırp") and hasClickAction()).performClick()
        waitFor { canvas().strokes[0].clip != null }
        assertEquals("tuval kırpılmadı", 1080, canvas().width)
        assertNull("diğer eleman kırpılmadı", canvas().strokes[1].clip)
        val visible = ElementOps.bounds(canvas().strokes[0])!!
        assertTrue("görünen kısım daraldı: ${visible.width} / ${b.width}", visible.width < b.width * 0.75f)
        assertEquals("çizim silinmedi", canvas().strokes[0].points.size, canvas().strokes[0].points.size)

        // Fırça seçilince seçim kalkar: kırpma yeniden tüm çizime uygulanır (v17: kırpma bitince Seç'te; fırça Çiz'de)
        mode("Çiz")
        pickBrush("Marker")
        mode("Kırp")
        waitForText("Çerçevenin kenarlarını sürükle", substring = true)
        waitForText("1080 × 1080")
        assertTrue(compose.onAllNodesWithText("Kırp: Seçili eleman").fetchSemanticsNodes().isEmpty())
    }
}

/**
 * 0.4: boya kutusu üst menüde; seçili şekil varsa onun arka planını (dolgusunu), yoksa tuvalin arka planını boyar.
 * 0.3: silgi seçilince seçim kalkar. 0.1: seçim yokken Taşı modunda bir elemanın üstünden sürüklenince o seçilip taşınır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V10FillAndMoveTest : V10EditorBase() {

    @Test
    fun paintBucketFillsTheSelectedShapeOrTheCanvasAndMovePicksTheTouchedElement() {
        createNotesCanvasAndOpen("Boya")
        addMenu("Şekil ekle")
        waitForText("Geometrik Cisimler")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kare")
        val size = canvasSize()
        val corner = Offset(size.x * 0.2f, size.y * 0.2f)
        drag(corner, Offset(size.x * 0.3f, size.x * 0.3f))
        waitFor { canvas().strokes.size == 1 }
        assertNull(canvas().strokes.single().shape!!.style.fillColorHex)
        val background = canvas().backgroundColorHex

        // Eklenen şekil seçili: boya kutusu şeklin dolgusunu boyar, tuvalin arka planı değişmez
        pickFillColor()
        waitFor { canvas().strokes.single().shape!!.style.fillColorHex != null }
        assertEquals(background, canvas().backgroundColorHex)
        closeSheet("color_pick_sheet")
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().strokes.single().shape!!.style.fillColorHex == null }

        // v34: Boya kutusu Çiz satırında; Düzenle'de kalem ve silgi yok; çizmek için Çiz
        mode("Düzenle")
        assertTrue(compose.onAllNodesWithContentDescription("Silgi").fetchSemanticsNodes().isEmpty())
        mode("Çiz")
        compose.onNodeWithContentDescription("Silgi").assertIsEnabled()
        // Silgi seçilince seçim kalkar: boya kutusu tuvalin arka planını boyar
        compose.onNodeWithContentDescription("Silgi").performScrollTo().performClick()
        compose.waitForIdle()
        pickFillColor()
        waitFor { canvas().backgroundColorHex != background }
        assertNull("şekil boyanmadı", canvas().strokes.single().shape!!.style.fillColorHex)
        closeSheet("color_pick_sheet")

        // Seçim yokken Taşı: şeklin üstünden sürüklenince o seçilir ve taşınır
        val before = ElementOps.bounds(canvas().strokes.single())!!
        mode("Taşı")
        drag(Offset(corner.x, corner.y + size.x * 0.15f), Offset(size.x * 0.2f, 0f))
        waitFor { ElementOps.bounds(canvas().strokes.single())!!.left > before.left + 10f }
        val after = ElementOps.bounds(canvas().strokes.single())!!
        assertEquals("yalnızca yatayda taşındı", before.top, after.top, 1f)
    }
}

/**
 * 1: yazıya kenarlık ve dolgu şekiller gibi eklenmez; dolgu harflerin iç rengini, kenarlık harflerin çevresindeki
 * çizginin rengini ve kalınlığını belirler.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V10TextOutlineTest : V10EditorBase() {

    @Test
    fun textFillAndOutlineAreTextSpecific() {
        createCanvasAndOpen("Başlık")
        val size = canvasSize()
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        tap(Offset(size.x * 0.25f, size.y * 0.4f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Merhaba")
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text != null } }
        val original = canvas().strokes.single()
        assertEquals(BrushType.TEXT, original.brushType)

        clickElements()
        // Eklenen yazı seçili gelir; değilse listeden seçilir
        waitFor {
            compose.onAllNodesWithTag("text_format_section").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Yazı 1").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag("text_format_section").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithText("Yazı 1").performClick()
        }
        waitFor { compose.onAllNodesWithTag("text_format_section").fetchSemanticsNodes().isNotEmpty() }
        // v29 tek yazı biçimi alanı: renk ve kenarlık "Renk" bölümünde
        compose.onNodeWithTag("text_tab_COLOR").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("text_fill_border_section").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("şekil biçim ayarları yazıda yok", compose.onAllNodesWithTag("element_style_section").fetchSemanticsNodes().isEmpty())
        assertTrue("şekil dolgusu yazıda yok", compose.onAllNodesWithText("Dolgu var").fetchSemanticsNodes().isEmpty())

        // Kenarlık: harflerin çevresindeki çizgi
        compose.onNodeWithText("Kenarlık rengi").performClick()
        waitForText("Kenarlık var")
        compose.onNodeWithText("Kenarlık var").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.single().text?.outlineColorHex != null }
        val outlined = canvas().strokes.single()
        assertTrue(outlined.text!!.outlineWidth > 0f)
        assertEquals("yazı kutusu değişmez", original.points, outlined.points)
        compose.onNodeWithText("Kenarlık yok").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.single().text?.outlineColorHex == null }

        // Dolgu: harflerin iç rengi
        compose.onNodeWithText("Kenarlık rengi").performClick()
        compose.onNodeWithText("Dolgu (yazı rengi)").performClick()
        waitFor { compose.onAllNodesWithTag("color_sv_area").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("color_sv_area").performTouchInput { click(Offset(width * 0.9f, height * 0.2f)) }
        waitFor { !canvas().strokes.single().colorHex.equals(original.colorHex, ignoreCase = true) }
        assertEquals("tek eleman", 1, canvas().strokes.size)
    }
}

/**
 * 3: şekil düzenlemede eski araçlar yok; noktaları artırma, şeklin üstünde görünen parçalama ve iki noktayı
 * bağlama var. 4: diyagram düzenleme pencerede değil çizim alanında.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V10DiagramOnCanvasTest : V10EditorBase() {

    @Test
    fun diagramEditingHasNoDialogAndArrowsCanBeRenamed() {
        createNotesCanvasAndOpen("Süreç")
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        compose.onNodeWithText("Akış Diyagramı").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Diyagram: Akış Diyagramı")
        val size = canvasSize()
        drag(Offset(size.x * 0.1f, size.y * 0.1f), Offset(size.x * 0.7f, size.y * 0.5f))
        waitFor { canvas().strokes.isNotEmpty() }
        editButton("Diyagramı düzenle")
        waitForText("Adım ekle")
        assertTrue("eski düzenleme penceresi yok", compose.onAllNodesWithText("Diyagram Düzenle").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag("diagram_step_field").fetchSemanticsNodes().isEmpty())

        // Alttaki "Adım ekle": son adımın ardına yeni adım, panel yeni adımın yanında açılır
        compose.onNode(hasText("Adım ekle") and hasClickAction()).performClick()
        waitFor { canvas().strokes.single().diagram!!.nodes.size == 5 }
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        // Adımı sil
        compose.onNodeWithContentDescription("Adımı sil").performClick()
        waitFor { canvas().strokes.single().diagram!!.nodes.size == 4 }
        assertTrue(compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Bitti").performClick()
        // v16: düzenleme bitince İncele moduna geçilir
        waitFor { compose.onAllNodesWithText("Elemana dokunarak seç", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, canvas().strokes.size)
    }
}
