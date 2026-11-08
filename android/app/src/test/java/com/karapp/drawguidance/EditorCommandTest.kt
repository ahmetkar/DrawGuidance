package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Boşlukları doldur" (v18: komut kutusu kalktı, iki parmakla uzun basınca açılan bağlam menüsünde): seçili çizgiler
 * arasındaki boşlukları aynı fırçayla eğriyle kapatır; yeni kategorili fırça seçici ile fırça seçimi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorCommandTest : EditorTestBase() {

    private fun fillGapsFromContextMenu(at: Offset) {
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(0, at - Offset(30f, 0f))
            down(1, at + Offset(30f, 0f))
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up(0); up(1) }
        waitFor { contextMenuOpen() }
        compose.onNode(hasText("Boşlukları doldur") and inContextMenu()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun fillGapsJoinsSelectedLinesWithTheirBrush() {
        createCanvasAndOpen("Boşluk Testi")

        // Yeni kategorideki bir fırça (Sanat Kalemleri → Kömür) ile iki ayrı çizgi
        pickBrush("Kömür")
        val size = canvasSize()
        val a = Offset(size.x * 0.12f, size.y * 0.4f)
        drag(a, Offset(size.x * 0.3f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val b = Offset(size.x * 0.58f, size.y * 0.4f)
        drag(b, Offset(size.x * 0.3f, 0f))
        waitFor { canvas().strokes.size == 2 }
        assertTrue(canvas().strokes.all { it.brushType == BrushType.CHARCOAL })
        assertEquals(2, ElementOps.index(canvas().strokes).elements.size)

        // Seç modunda iki çizgi seçilir, bağlam menüsünden boşluklar doldurulur
        mode("Seç")
        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(a + Offset(size.x * 0.15f, 0f)) }
        waitForText("1 eleman seçili")
        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(b + Offset(size.x * 0.15f, 0f)) }
        waitForText("2 eleman seçili")
        fillGapsFromContextMenu(Offset(size.x * 0.5f, size.y * 0.7f))
        waitForText("1 boşluk dolduruldu")
        waitFor { canvas().strokes.size == 3 }

        val strokes = canvas().strokes
        val bridge = strokes[2]
        assertEquals(BrushType.CHARCOAL, bridge.brushType)
        assertEquals(strokes[0].sizePx, bridge.sizePx)
        assertEquals("köprü sonrası tek eleman", 1, ElementOps.index(strokes).elements.size)
        // Köprü, ilk çizginin bitişinden ikincinin başlangıcına uzanır
        assertEquals(strokes[0].points.last().x, bridge.points.first().x, 1f)
        assertEquals(strokes[1].points.first().x, bridge.points.last().x, 1f)
        // Seçim yeni köprüyü de kapsar
        waitForText("1 eleman seçili")

        // Tekrar çalıştırınca doldurulacak boşluk kalmaz
        fillGapsFromContextMenu(Offset(size.x * 0.5f, size.y * 0.7f))
        waitForText("doldurulacak boşluk bulunamadı", substring = true)
        assertEquals(3, canvas().strokes.size)
    }
}
