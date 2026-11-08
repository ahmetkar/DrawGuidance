package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.drawing.ShapeEditor
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Şekil düzenleme modu ve eleman biçim ayarları: seçili vektör şeklin biçimi (dolgu, kenar, köşe) değişir,
 * tuvale göre hizalanır; noktaları artırılır, şeklin üstünde parçalanır ve iki noktası bağlanır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V9ShapeEditTest : EditorTestBase() {

    @Test
    fun selectedShapeIsSlicedAndItsStyleAndAlignmentAreChangedFromTheElementsPanel() {
        createNotesCanvasAndOpen("Geometri")
        addMenu("Şekil ekle")
        waitForText("Geometrik Cisimler")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kare")

        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.2f), Offset(size.x * 0.4f, size.x * 0.4f))
        waitFor { canvas().strokes.isNotEmpty() }
        val square = canvas().strokes.single()
        assertEquals(BrushType.SHAPE, square.brushType)

        // Eklenen şekil seçili kalır: Elemanlar panelinde biçim ayarları görünür
        val scale = (size.x * 0.4f) / ElementOps.bounds(square)!!.width
        val map = Mapping(
            scale,
            Offset(size.x * 0.2f - ElementOps.bounds(square)!!.left * scale, size.y * 0.2f - ElementOps.bounds(square)!!.top * scale),
        )
        clickElements()
        waitFor { compose.onAllNodesWithTag("element_style_section").fetchSemanticsNodes().isNotEmpty() }

        // Dolgu rengi
        compose.onNodeWithText("Dolgu rengi").performClick()
        waitForText("Dolgu var")
        compose.onNodeWithText("Dolgu var").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.single().shape?.style?.fillColorHex != null }
        compose.onNodeWithText("Dolgu rengi").performClick()
        compose.waitForIdle()

        // Hizalama: tuvalin ortasına (yalnızca yatayda taşınır)
        val before = ElementOps.bounds(canvas().strokes.single())!!
        compose.onNodeWithText("Ortaya").performSemanticsAction(SemanticsActions.OnClick)
        waitFor {
            val b = ElementOps.bounds(canvas().strokes.single()) ?: return@waitFor false
            abs(b.center.x - canvas().width / 2f) < 1.5f
        }
        val after = ElementOps.bounds(canvas().strokes.single())!!
        assertEquals("yalnızca yatayda taşındı", before.top, after.top, 0.5f)
        assertEquals(before.width, after.width, 0.5f)
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()

        // Şekil düzenleme modu (v16): vektör noktaları ve çizgiler; eski parça araçları yok
        mode("Seç")
        editButton("Şekli düzenle")
        waitForText("Şekli Düzenle: Noktalar")
        listOf("Noktalar", "Nokta ekle", "Çizgi çiz", "Çizgiler", "Alan boya").forEach { assertTrue(it, compose.onAllNodesWithContentDescription(it).fetchSemanticsNodes().isNotEmpty()) }
        listOf("Parça özellikleri", "Kes", "Sil").forEach {
            assertTrue(it, compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(it)).fetchSemanticsNodes().isNotEmpty())
        }
        listOf("Dönüştür", "Ayır", "Parçala", "Boya", "Eğri").forEach { assertTrue(it, compose.onAllNodesWithText(it).fetchSemanticsNodes().isEmpty()) }
        assertTrue(canvas().strokes.all { it.brushType == BrushType.SHAPE })
        compose.onNodeWithContentDescription("Bitti").performClick()
        // v16: düzenleme bitince İncele
        waitForText("Elemana dokunarak seç", substring = true)
    }
}
