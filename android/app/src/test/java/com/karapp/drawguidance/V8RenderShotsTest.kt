package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.ui.drawing.BrushPainter
import com.karapp.drawguidance.ui.drawing.NoteBrushTypes
import com.karapp.drawguidance.ui.drawing.ShapeCatalog
import com.karapp.drawguidance.ui.drawing.ShapeCategory
import com.karapp.drawguidance.ui.drawing.ShapeFit
import com.karapp.drawguidance.ui.drawing.TextRenderer
import com.karapp.drawguidance.ui.drawing.defaultSize
import com.karapp.drawguidance.ui.drawing.inkWidth
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin

/** v8 görsel kontrol: şekil katalogları, not kalemleri ve yazı tipleri (DG_SHOTS verilince PNG). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V8RenderShotsTest {
    @After fun tearDown() = stopKoin()

    private fun save(bitmap: Bitmap, name: String) {
        val dir = System.getenv("DG_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun paint(canvas: Canvas, s: Stroke) {
        BrushPainter.paint(canvas, FloatArray(s.points.size) { s.points[it].x }, FloatArray(s.points.size) { s.points[it].y }, s.points.size,
            Color.parseColor(s.colorHex), s.brushType, s.sizePx, s.opacity, s.id.hashCode(), s.flow, s.strength)
    }

    @Test fun renderCatalogs() {
        if (System.getenv("DG_SHOTS").isNullOrBlank()) return
        for ((kind, brush) in listOf(CanvasKind.ART to BrushType.INK_PEN, CanvasKind.ART to BrushType.PASTEL, CanvasKind.NOTES to BrushType.NOTE_BALLPOINT, CanvasKind.NOTES to BrushType.NOTE_FOUNTAIN)) {
            val shapes = ShapeCategory.forKind(kind).flatMap { it.shapes }
            val cols = 6
            val cell = 260f
            val rows = (shapes.size + cols - 1) / cols
            val bitmap = Bitmap.createBitmap((cols * cell).toInt(), (rows * cell).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val size = brush.defaultSize()
            shapes.forEachIndexed { i, shape ->
                val x = (i % cols) * cell + 30f
                val y = (i / cols) * cell + 30f
                val start = if (shape.fit == ShapeFit.LINE) Offset(x, y + 170f) else Offset(x, y)
                val end = if (shape.fit == ShapeFit.LINE) Offset(x + 200f, y + 30f) else Offset(x + 200f, y + (if (shape.fit == ShapeFit.SQUARE) 200f else 160f))
                ShapeCatalog.paths(shape, start, end, brush.inkWidth(size)).forEachIndexed { k, p ->
                    paint(canvas, Stroke("$shape-$k", ShapeCatalog.densify(p.points).map { StrokePoint(it.x, it.y) }, "#1E1E1E", brush, size, 1f, 0))
                }
                canvas.drawText(shape.name, x, y + 225f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; color = Color.GRAY })
            }
            save(bitmap, "shapes-$kind-$brush")
        }
        // Not kalemleri
        val bitmap = Bitmap.createBitmap(900, 120 * NoteBrushTypes.size + 40, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        NoteBrushTypes.forEachIndexed { i, type ->
            val y = 70f + i * 120f
            val pts = (0..80).map { k -> StrokePoint(200f + k * 8f, y + sin(k / 8f).toFloat() * 25f) }
            paint(canvas, Stroke("n$i", pts, if (type == BrushType.NOTE_HIGHLIGHTER) "#FFD400" else "#1B3A8C", type, type.defaultSize(), 1f, 0))
            canvas.drawText(type.name, 10f, y + 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = Color.BLACK })
        }
        save(bitmap, "note-pens")
        // Yazı tipleri
        val texts = Bitmap.createBitmap(900, 700, Bitmap.Config.ARGB_8888)
        val tc = Canvas(texts)
        tc.drawColor(Color.WHITE)
        TextFont.entries.forEachIndexed { i, font ->
            val spec = TextSpec("${font.name}: Işık ğüşiöç 123", font, bold = i % 3 == 1, italic = i % 3 == 2, underline = i == 5)
            val quad = TextRenderer.quadAt(spec, 48f, 20f, 20f + i * 110f)
            TextRenderer.draw(tc, Stroke("t$i", quad, "#202020", BrushType.TEXT, 48f, 1f, 0, text = spec), Color.parseColor("#202020"))
        }
        save(texts, "fonts")
    }
}
