package com.karapp.drawguidance

import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.notes.TempCanvases
import com.karapp.drawguidance.data.notes.TempImageGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v51: not alma tek mod (basit çizim altında) ve geçici tuvalde resim düzenleme. */
class V51NotesMergeTest {

    @Test
    fun legacySimpleNotesMigratesToNotes() {
        assertEquals(CanvasKind.NOTES, CanvasKind.parse("SIMPLE_NOTES"))
        assertEquals(CanvasKind.NOTES, CanvasKind.parse("NOTES"))
        assertEquals(listOf(CanvasKind.SIMPLE_ART, CanvasKind.NOTES, CanvasKind.KIDS), CanvasKind.Simple)
        assertEquals(listOf(CanvasKind.ART, CanvasKind.COMIC), CanvasKind.Advanced)
        assertFalse(CanvasKind.SIMPLE_NOTES in CanvasKind.Selectable)
        assertTrue(CanvasKind.NOTES.simple)
    }

    private fun image(w: Float, h: Float) = Stroke(
        id = "img", points = listOf(StrokePoint(100f, 100f), StrokePoint(100f + w, 100f), StrokePoint(100f + w, 100f + h), StrokePoint(100f, 100f + h)),
        colorHex = "#000000", brushType = BrushType.IMAGE, sizePx = 1f, opacity = 1f, order = 0, assetId = "old",
    )

    private fun result(w: Int, h: Int, reshaped: Boolean) = TempCanvases.Result("t", "s", "img", "new", w, h, reshaped)

    @Test
    fun replaceKeepsFrameWhenSizeUnchanged() {
        val img = image(400f, 200f)
        val out = TempImageGeometry.replace(img, result(800, 400, reshaped = false))
        assertEquals("new", out.assetId)
        assertEquals(img.points, out.points)
    }

    @Test
    fun replaceFitsNewAspectKeepingWidthAndCenter() {
        val out = TempImageGeometry.replace(image(400f, 200f), result(400, 400, reshaped = true))
        val p = out.points
        assertEquals(400f, p[1].x - p[0].x, 0.5f)
        assertEquals(400f, p[3].y - p[0].y, 0.5f)
        assertEquals(300f, (p[0].x + p[2].x) / 2, 0.5f)
        assertEquals(200f, (p[0].y + p[2].y) / 2, 0.5f)
        assertNull(out.clip)
    }

    @Test
    fun canvasSizeAndReshape() {
        assertEquals(2400 to 1200, TempImageGeometry.canvasSize(4800, 2400))
        assertEquals(300 to 200, TempImageGeometry.canvasSize(300, 200))
        assertEquals(0 to 0, TempImageGeometry.canvasSize(0, 10))
        assertFalse(TempImageGeometry.reshaped(800, 400, 802, 401))
        assertTrue(TempImageGeometry.reshaped(800, 400, 800, 800))
    }
}
