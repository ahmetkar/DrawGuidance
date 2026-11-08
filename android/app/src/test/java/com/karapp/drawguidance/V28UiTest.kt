package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.DiagramThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * v28: editoryal diyagram tuvalde (gruplu seçici, varsayılan editoryal tema, nicel grafikte yazıdan değer, vurgu
 * düğmesi); yazı kutusu düzenlenirken yazı tipi özellikleri korunur ve yazı yerinde kalır; öneri kutusu sürüklenir
 * ve yeri hatırlanır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V28DiagramUiTest : V27EditorBase() {

    @Test
    fun editorialBarChartIsEditedOnTheCanvas() {
        createNotesCanvasAndOpen("Grafik")
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        // Gruplar ve yeni türler
        waitForText("Nicel (grafik)")
        waitForText("Yazılım ve veri")
        shotAll("v28-06-diagram-picker")
        compose.onNodeWithText("Sütun grafiği").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Diyagram: Sütun grafiği")

        val size = canvasSize()
        val screen = Rect(size.x * 0.08f, size.y * 0.2f, size.x * 0.92f, size.y * 0.55f)
        drag(screen.topLeft, Offset(screen.width, screen.height))
        waitFor { canvas().strokes.isNotEmpty() }
        val stroke = canvas().strokes.single()
        val spec = stroke.diagram!!
        assertEquals(DiagramKind.BAR, spec.kind)
        assertEquals("varsayılan tema editoryal", DiagramThemes.EDITORIAL, spec.theme)
        assertEquals(6, spec.nodes.size)
        assertTrue("çubuklar okla bağlı değil", spec.edges.isEmpty())

        val box = Rect(stroke.points.minOf { it.x }, stroke.points.minOf { it.y }, stroke.points.maxOf { it.x }, stroke.points.maxOf { it.y })
        fun toScreen(p: Offset) = Offset(
            screen.left + (p.x - box.left) / box.width * screen.width,
            screen.top + (p.y - box.top) / box.height * screen.height,
        )
        fun nodeCenter(index: Int): Offset {
            val s = canvas().strokes.single()
            val n = s.diagram!!.nodes[index]
            return toScreen(DiagramRenderer.toCanvas(s, n.x + n.w / 2f, n.y + n.h * 0.6f)!!)
        }
        editButton("Diyagramı düzenle")
        waitForText("Adım ekle")
        tap(nodeCenter(0))
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        // Nicel ipucu ve vurgu düğmesi
        waitForText("Değerler adımların yazısındaki", substring = true)
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("diagram_focus_panel"))).performTextReplacement("Oca 400")
        waitFor {
            val nodes = canvas().strokes.single().diagram!!.nodes
            nodes[0].text == "Oca 400" && nodes[0].h >= nodes.maxOf { it.h } - 1e-4f
        }
        compose.onNode(hasContentDescription("Vurgula (odak)") and hasAnyAncestor(hasTestTag("diagram_focus_panel")))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.single().diagram!!.nodes[0].role == "focal" }
        shotAll("v28-07-bar-chart-edit")
        // Yeni adım: değerli yazıyla grafiğe eklenir (ok eklenmez)
        compose.onNodeWithContentDescription("Adım ekle").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.single().diagram!!.nodes.size == 7 }
        val after = canvas().strokes.single().diagram!!
        assertTrue(after.edges.isEmpty())
        assertTrue("yeni çubuğun değeri var", after.nodes.any { it.text.startsWith("Yeni") && it.h > 0.05f })
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V28TextEditTest : V27EditorBase() {

    private fun field() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor")))

    @Test
    fun editingKeepsFontStyleAndPosition() {
        createCanvasAndOpen("Yazı")
        val size = canvasSize()
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        tap(Offset(size.x * 0.25f, size.y * 0.35f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Kalın") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performSemanticsAction(SemanticsActions.OnClick)
        field().performTextInput("Merhaba")
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text != null } }
        val before = canvas().strokes.single { it.text != null }
        assertTrue("kalın", before.text!!.bold)

        // Düzenle: yazı yerinde, kalın görünür (canlı önizleme) ve değiştirilince de yerinde kalır
        mode("Seç")
        tap(Offset(size.x * 0.29f, size.y * 0.35f - 10f))
        waitForText("1 eleman seçili")
        editButton("Metni Düzenle")
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        waitFor { compose.onAllNodesWithTag("live_text_preview").fetchSemanticsNodes().isNotEmpty() }
        shotAll("v28-08-text-edit-live")
        field().performTextInput(" dünya")
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.single { it.text != null }.text!!.content.endsWith("dünya") }
        val after = canvas().strokes.single { it.text != null }
        assertTrue("hâlâ kalın", after.text!!.bold)
        assertEquals("yazı tipi aynı", before.text!!.font, after.text!!.font)
        // Sol üst köşe yerinde (yazı uzadı ama kaymadı)
        val b0 = before.points.first(); val a0 = after.points.first()
        assertTrue("yerinde: $b0 → $a0", abs(b0.x - a0.x) < 2f && abs(b0.y - a0.y) < 2f)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V28SuggestDragTest : V27EditorBase() {

    @Test
    fun suggestionBalloonIsDraggedAndRemembered() {
        createCanvasAndOpen("Öneri taşı")
        prefs().setHandwriting(false)
        val c = canvas()
        val cx = c.width * 0.5f; val cy = c.height * 0.45f; val r = c.width * 0.18f
        // v38 tek çizgiye öneri yok: çember ve altında bir çizgi (çizgi önerisi)
        val wobbly = screenCircle(cx, cy, r).mapIndexed { i, p -> P(p.x + (i % 3 - 1) * 2.5f, p.y + (i % 2) * 2.5f) }
        drawStrokes(listOf(wobbly, screenLine(cx - r, cy + r * 1.3f, cx + r * 0.2f, cy + r * 1.36f, cx + r, cy + r * 1.28f)))
        waitFor { canvas().strokes.size == 2 }
        prefs().setHandwriting(true)
        prefs().setSelectionSuggest(true)
        prefs().suggestPin = null
        mode("Seç")
        tap(screenOf(cx + r, cy))
        advanceMain(300)
        tap(screenOf(cx + r * 0.2f, cy + r * 1.33f))
        waitFor(15_000) { chip("line_suggest_chip") }
        val before = compose.onNodeWithTag("line_suggest_chip").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("suggest_drag_handle").performTouchInput {
            down(center)
            repeat(12) { moveBy(Offset(-12f, 40f)) }
            up()
        }
        waitFor { prefs().suggestPin != null }
        val moved = compose.onNodeWithTag("line_suggest_chip").fetchSemanticsNode().boundsInRoot
        assertTrue("kutu aşağı taşındı: $before → $moved", moved.top > before.top + 200f)
        assertNotNull(prefs().suggestPin)
        shotAll("v28-09-suggest-moved")
    }
}
