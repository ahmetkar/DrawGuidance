package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * Yoğun kullanım: art arda çok sayıda çizim, ardından toplu silme ve silme sürerken yeni çizimler.
 * Birleştirilmiş (conflated) kayıt kuyruğu ve arka plan silgi temizliği hiçbir veriyi kaybetmemeli.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorStressTest : EditorTestBase() {

    private fun quickStroke(from: Offset, length: Float = 24f) {
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(from)
            repeat(4) { moveBy(Offset(length / 4, 0f)) }
            up()
        }
    }

    @Test
    fun manyStrokesThenBulkEraseWhileDrawingKeepsDataConsistent() {
        createCanvasAndOpen("Yoğun Çizim")
        val size = canvasSize()
        val rows = 8
        val perRow = 12
        fun at(row: Int, col: Int) = Offset(size.x * 0.1f + col * size.x * 0.066f, size.y * 0.15f + row * size.y * 0.08f)

        val start = System.nanoTime()
        for (row in 0 until rows) for (col in 0 until perRow) quickStroke(at(row, col))
        compose.waitForIdle()
        waitFor(30_000) { canvas().strokes.size == rows * perRow }
        val drawMs = (System.nanoTime() - start) / 1_000_000
        println("STRESS ${rows * perRow} çizim + kayıt: $drawMs ms")

        // Silgi ile ilk iki satır baştan sona silinir
        compose.onNodeWithContentDescription("Silgi").performClick()
        waitForDescription("Seçili fırça: Silgi")
        for (row in 0 until 2) {
            val from = at(row, 0) - Offset(size.x * 0.05f, 0f)
            drag(from, Offset(size.x * 0.9f, 0f), steps = 40)
        }
        // Temizlik arka planda sürerken hemen yeni çizimler
        compose.onNodeWithContentDescription("Silgi").performClick()
        waitForDescription("Seçili fırça: Kalem")
        repeat(4) { quickStroke(Offset(size.x * (0.15f + it * 0.18f), size.y * 0.85f), 60f) }

        val expected = rows * perRow - 2 * perRow + 4
        waitFor(30_000) { canvas().strokes.size == expected }
        waitForText("Tamamen silinen", substring = true)
        val strokes = canvas().strokes
        assertTrue("silgiler de kaldırılmalı", strokes.none { it.brushType == BrushType.ERASER })
        assertEquals(expected, strokes.size)
        assertEquals("sıra numaraları ardışık olmalı", strokes.indices.toList(), strokes.map { it.order })
        assertEquals((rows - 2) * perRow + 4, ElementOps.index(strokes).elements.size)
        // Birkaç saniye sonra da kayıt aynı kalır (geç gelen eski bir kayıt üzerine yazmaz)
        Thread.sleep(1500)
        waitFor { true }
        assertEquals(strokes.map { it.id }, canvas().strokes.map { it.id })
    }
}
