package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.TextParagraph
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.notesFirst
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v37 (2) not modları: yazı, diyagram ve formül menünün başında; yazı dokunulan yere ya da sürüklenerek çizilen
 * kutuya yazılır, kutusu tutamağıyla taşınır; Başlık / Kısa yazı / Paragraf kutu türleri; kısa yazı sağ kenarda,
 * paragraf kutunun eninde alt satıra geçer.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V37NotesTextTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun textStrokes(): List<Stroke> = vm().canvas.value?.strokes.orEmpty().filter { it.text != null }
    // v48 basit notun dikey ekranında üç menü alt alta: okuma sırası (önce satır, sonra soldan sağa)
    private fun left(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.let { it.top * 10000f + it.left }
    private fun leftOf(label: String) =
        // v45 dikey ekranda adlar düğmenin içinde, satır kayar: kırpılmamış konum
        // v47 dikeyde satır iki parçaya bölünebilir: okuma sırası (önce satır, sonra soldan sağa)
        compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNode().positionInRoot.let { it.y * 10000f + it.x }

    private fun beginText(x: Float, y: Float) {
        compose.runOnIdle {
            vm().startAction(EditMode.TEXT)
            vm().beginTextAt(x, y, 0f)
        }
        waitFor { exists("inline_text_editor") }
    }

    @Test
    fun notesMenusStartWithTextDiagramFormula() {
        // Ötekilerin sırası değişmez
        assertEquals(listOf("text", "diagram", "formula", "shape", "image"), notesFirst(listOf("shape", "formula", "text", "image", "diagram")) { it })
        open("Not menü", CanvasKind.NOTES)
        compose.runOnIdle { vm().chooseMode(EditMode.ADD) }
        waitFor { compose.onAllNodes(hasContentDescription("Yazı ekle") and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNodes().isNotEmpty() }
        val order = listOf("Yazı ekle", "Diyagram ekle", "Formül ekle", "Şekil ekle").map(::leftOf)
        shot("v47-notes-add-row")
        assertEquals("Ekle: yazı, diyagram, formül, şekil", order.sorted(), order)
    }

    @Test
    fun simpleNotesRowStartsWithTextDiagramFormula() {
        open("Basit not menü", CanvasKind.SIMPLE_NOTES)
        waitFor { exists("simple_tool_text") }
        val order = listOf("text", "diagram", "formula", "brush", "eraser").map { left("simple_tool_$it") }
        assertEquals("yazı, diyagram, formül, fırça, silgi", order.sorted(), order)
    }

    @Test
    fun dragDrawsParagraphBoxAndLinesWrapInIt() {
        open("Paragraf", CanvasKind.NOTES)
        compose.runOnIdle { vm().startAction(EditMode.TEXT) }
        val size = canvasSize()
        drag(Offset(size.x * 0.15f, size.y * 0.3f), Offset(size.x * 0.5f, size.y * 0.08f))
        waitFor { exists("inline_text_editor") }
        val draft = vm().textDraft.value!!
        val width = draft.boxWidth
        assertNotNull("paragraf kutusu", width)
        assertTrue(exists("text_kind_row"))
        field().performTextInput("Bu uzun bir paragraf; satırlar kutunun sağ kenarına gelince kendiliğinden alt satıra geçer ve kutu aşağı uzar.")
        finishText()
        waitFor { textStrokes().isNotEmpty() }
        val s = textStrokes().single()
        assertEquals(width!!, s.text!!.box!!.width!!, 0.5f)
        val w = s.points[1].x - s.points[0].x
        val h = s.points[3].y - s.points[0].y
        assertEquals("kutu eninde", width, w, 1f)
        assertTrue("birkaç satır: $h", h > s.sizePx * 2.5f)
        // Sürüklemenin başladığı yerde (sol üst köşe)
        assertTrue(kotlin.math.abs(s.points[0].x - draft.anchor.x) < 1f)
    }

    @Test
    fun shortTextWrapsAtRightEdgeAndHeadingKind() {
        open("Kısa yazı", CanvasKind.NOTES)
        beginText(600f, 200f)
        // Dokunulan yerde kısa yazı (kutu yazıya göre)
        assertNull(vm().textDraft.value!!.boxWidth)
        field().performTextInput("Kısa yazı tuvalin sağ kenarına varınca alt satıra geçer")
        finishText()
        waitFor { textStrokes().isNotEmpty() }
        val s = textStrokes().single()
        assertTrue("sağ kenarı geçmez: ${s.points[1].x}", s.points[1].x <= 1000f * 0.96f + 1f)
        assertTrue("alt satıra geçti", s.points[3].y - s.points[0].y > s.sizePx * 1.8f)

        // Başlık: büyük ve kalın, kutu yazıya göre
        beginText(80f, 600f)
        compose.onNodeWithTag("text_kind_heading").performClick()
        compose.waitForIdle()
        assertEquals(TextParagraph.H1, vm().textSettings.value.spec.paragraph)
        field().performTextInput("Başlık")
        finishText()
        waitFor { textStrokes().size == 2 }
        val heading = textStrokes().single { it.text!!.content == "Başlık" }
        assertEquals(TextParagraph.H1, heading.text!!.paragraph)
        assertNull(heading.text!!.box?.width)
    }

    @Test
    fun kindChipsSwitchBoxAndHandleMovesText() {
        open("Taşı", CanvasKind.NOTES)
        beginText(150f, 300f)
        compose.onNodeWithTag("text_kind_paragraph").performClick()
        compose.waitForIdle()
        assertNotNull(vm().textDraft.value!!.boxWidth)
        compose.onNodeWithTag("text_kind_short").performClick()
        compose.waitForIdle()
        assertNull(vm().textDraft.value!!.boxWidth)

        field().performTextInput("Taşınan yazı")
        val before = vm().textDraft.value!!.anchor
        compose.onNodeWithTag("text_move_handle").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(20f, 12f)) }
            up()
        }
        waitFor { vm().textDraft.value?.anchor != before }
        val after = vm().textDraft.value!!.anchor
        assertTrue("sağa ve aşağı: $before → $after", after.x > before.x + 20f && after.y > before.y + 10f)
        // Yazılan kaybolmaz
        assertEquals("Taşınan yazı", vm().textDraft.value!!.content)
        shot("v37-notes-text-draft")
        finishText()
        waitFor { textStrokes().isNotEmpty() }
        val s = textStrokes().single()
        assertEquals("Taşınan yazı", s.text!!.content)
        assertTrue(s.points[0].x > 150f + 20f)
        shot("v37-notes-text")
    }
}
