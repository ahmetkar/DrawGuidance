package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.DiagramSide
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v12 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü): ok tarafları, tutamaklar, inceleme modu. */
abstract class V12ShotsBase : WideLayoutTestBase() {

    protected fun diagramSides(prefix: String) {
        createNotesCanvasAndOpen("Akış")
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        compose.onNodeWithText("Akış Diyagramı").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Diyagram: Akış Diyagramı")
        val size = canvasSize()
        val screen = Rect(size.x * 0.1f, size.y * 0.08f, size.x * 0.9f, size.y * 0.68f)
        drag(screen.topLeft, Offset(screen.width, screen.height))
        waitFor { canvas().strokes.isNotEmpty() }
        val placed = canvas().strokes.single()
        val box = Rect(placed.points.minOf { it.x }, placed.points.minOf { it.y }, placed.points.maxOf { it.x }, placed.points.maxOf { it.y })
        fun toScreen(c: Offset) = fittedScreen(c)
        fun at(index: Int, fx: Float, fy: Float): Offset {
            val stroke = canvas().strokes.single()
            val n = stroke.diagram!!.nodes[index]
            return toScreen(DiagramRenderer.toCanvas(stroke, n.x + n.w * fx, n.y + n.h * fy)!!)
        }
        fun port(index: Int, side: DiagramSide): Offset {
            val stroke = canvas().strokes.single()
            return toScreen(DiagramRenderer.portPoint(stroke, stroke.diagram!!.nodes[index], side)!!)
        }
        editButton("Diyagramı düzenle")
        waitForText("Adım ekle")

        // Odaktaki adımın dört tutamağı
        tap(at(1, 0.5f, 0.5f))
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        shot("$prefix-1-node-handles")

        // Sağ tutamağa dokun: ok eklemesi sağdan başlar, hedef adımlarda taraf noktaları
        tap(port(1, DiagramSide.RIGHT))
        waitForText("Okun gideceği adımın", substring = true)
        shot("$prefix-2-linking-from-right")
        tap(at(3, 0.95f, 0.5f))
        waitFor { canvas().strokes.single().diagram!!.edges.size == 4 }
        shot("$prefix-3-edge-right-to-right")

        // Karar'ın solundan Başla'nın soluna geri dönüş oku
        compose.onNode(hasText("Ok ekle") and hasClickAction()).performClick()
        tap(at(2, 0.05f, 0.5f))
        tap(at(0, 0.05f, 0.5f))
        waitFor { canvas().strokes.single().diagram!!.edges.size == 5 }
        shot("$prefix-4-loop-left")

        // v17: Bitti → seçimsiz Seç (eski İncele): kaydırılır
        compose.onNodeWithContentDescription("Bitti").performClick()
        waitForText("Elemana dokunarak seç", substring = true)
        drag(Offset(size.x * 0.5f, size.y * 0.5f), Offset(size.x * 0.1f, size.y * 0.05f))
        shot("$prefix-5-view-mode")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V12PhoneShotsTest : V12ShotsBase() {
    @Test fun phone() = diagramSides("v12-phone")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V12LandShotsTest : V12ShotsBase() {
    @Test fun land() = diagramSides("v12-land")
}
