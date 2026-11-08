package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.GuideKind
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v10 diyagramlar: çalışma/not tuvaline diyagram eklenir ve pencere açılmadan tuvalin üstünde düzenlenir:
 * dokunulan adımın yanında küçük panel açılır; adın değişir, yeni adım ve ok eklenir. Diyagram tek elemandır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V9DiagramTest : EditorTestBase() {

    @Test
    fun diagramIsPlacedAsOneElementAndEditedOnTheCanvas() {
        createNotesCanvasAndOpen("Akış")
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        compose.onNodeWithText("Akış Diyagramı").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Diyagram: Akış Diyagramı")

        val size = canvasSize()
        val screen = Rect(size.x * 0.15f, size.y * 0.15f, size.x * 0.75f, size.y * 0.6f)
        drag(screen.topLeft, Offset(screen.width, screen.height))
        waitFor { canvas().strokes.isNotEmpty() }

        val diagram = canvas().strokes.single()
        assertEquals(BrushType.DIAGRAM, diagram.brushType)
        assertEquals(DiagramKind.FLOW, diagram.diagram!!.kind)
        assertEquals(4, diagram.diagram!!.nodes.size)
        assertEquals(3, diagram.diagram!!.edges.size)
        assertEquals("dört köşeli tek eleman", 4, diagram.points.size)
        assertEquals(1, ElementOps.index(canvas().strokes).elements.size)

        // Tuval koordinatı → ekran (diyagram sürüklenen dikdörtgene yerleşir)
        val box = Rect(
            diagram.points.minOf { it.x }, diagram.points.minOf { it.y }, diagram.points.maxOf { it.x }, diagram.points.maxOf { it.y },
        )
        // v47 menü satırı değişince kâğıt yeniden sığdırılır: konum her seferinde görünümden hesaplanır
        fun toScreen(p: Offset) = fittedScreen(p)
        fun nodeCenter(index: Int): Offset {
            val stroke = canvas().strokes.single()
            val node = stroke.diagram!!.nodes[index]
            return toScreen(DiagramRenderer.toCanvas(stroke, node.x + node.w / 2f, node.y + node.h / 2f)!!)
        }

        // Yerleştirilince seçili olur: düzenleme tuvalin üstünde (pencere açılmaz)
        editButton("Diyagramı düzenle")
        waitForText("Adım ekle")
        assertTrue("odak yokken panel yok", compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isEmpty())

        // İlk adıma dokun: yanında küçük panel açılır, adı değiştirilir
        tap(nodeCenter(0))
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("diagram_focus_panel"))).performTextReplacement("Başla")
        waitFor { canvas().strokes.single().diagram!!.nodes.first().text == "Başla" }

        // Yeni adım: odaktaki adımın ardına (zincirin arasına) girer
        compose.onNodeWithContentDescription("Adım ekle").performClick()
        waitFor { canvas().strokes.single().diagram!!.nodes.size == 5 }
        val withStep = canvas().strokes.single().diagram!!
        assertEquals("zincir korunur", 4, withStep.edges.size)
        assertTrue("yerleşim yeniden hesaplandı", withStep.nodes.all { it.y >= -0.01f && it.y + it.h <= 1.01f })

        // Yeni ok: odaktaki (yeni) adımdan dokunulan son adıma
        compose.onNodeWithContentDescription("Ok ekle").performClick()
        waitForText("Okun gideceği adımın", substring = true)
        tap(nodeCenter(withStep.nodes.lastIndex))
        waitFor { canvas().strokes.single().diagram!!.edges.size == 5 }
        assertEquals("hâlâ tek eleman", 1, ElementOps.index(canvas().strokes).elements.size)
    }
}
