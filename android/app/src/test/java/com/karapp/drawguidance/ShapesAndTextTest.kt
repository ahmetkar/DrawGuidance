package com.karapp.drawguidance

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.ui.drawing.BrushCategory
import com.karapp.drawguidance.ui.drawing.ErasedStrokePruner
import com.karapp.drawguidance.ui.drawing.ShapeCatalog
import com.karapp.drawguidance.ui.drawing.ShapeCategory
import com.karapp.drawguidance.ui.drawing.ShapeFit
import com.karapp.drawguidance.ui.drawing.ShapeKind
import com.karapp.drawguidance.ui.drawing.TextRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Tuval türleri, şekil kataloğu, yazı elemanı ve kayıt biçimi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class ShapesAndTextTest {
    @After fun tearDown() = stopKoin()

    @Test fun catalogsDifferByCanvasKind() {
        val art = ShapeCategory.forKind(CanvasKind.ART).flatMap { it.shapes }
        val notes = ShapeCategory.forKind(CanvasKind.NOTES).flatMap { it.shapes }
        // Resim: geometrik temel şekiller (v17: manzara/portre temel çizimleri seçiciden kalktı, yerine çizim
        // yardımcıları); not: geometrik cisimler ve grafikler
        // v42 resim tuvalinin temel şekilleri azaldı (yıldız ve öteki genel şekiller kütüphanede)
        assertTrue(ShapeKind.CIRCLE in art && ShapeKind.HEART in art && ShapeKind.STAR !in art)
        assertFalse(ShapeKind.MOUNTAINS in art || ShapeKind.FACE_GUIDE in art || ShapeKind.CLOUD in art)
        assertFalse(ShapeKind.MOUNTAINS in notes || ShapeKind.FACE_GUIDE in notes)
        assertTrue(ShapeKind.CUBE in notes && ShapeKind.AXES in notes && ShapeKind.NUMBER_LINE in notes && ShapeKind.RIGHT_TRIANGLE in notes)
        assertFalse(ShapeKind.CUBE in art || ShapeKind.AXES in art)
        // Seçicide olmayan şekiller eski kayıtlar için kurulabilir kalır (katalogda duruyor)
        val hidden = ShapeCategory.entries.filter { !it.listed }.flatMap { it.shapes }
        assertTrue(ShapeKind.MOUNTAINS in hidden && ShapeKind.FACE_GUIDE in hidden)
        assertEquals(ShapeKind.entries.toSet(), (art + notes + hidden).toSet())
        // v42 resim tuvali seçicisinde genel şekiller; çizim yardımcıları ve nesneler yok (çizgi romanda duruyor)
        val picker = com.karapp.drawguidance.ui.drawing.ShapeRegistry.categories(CanvasKind.ART)
        assertTrue(picker.any { it.id == "gen_flowchart" } && picker.none { it.id.startsWith("sketch_") })
        assertTrue(picker.none { it.id.startsWith("draw_") })
        assertTrue(com.karapp.drawguidance.ui.drawing.ShapeRegistry.categories(CanvasKind.COMIC).any { it.id == "sketch_portraits" })
        // Fırçalar da türe göre
        assertTrue(BrushCategory.forKind(CanvasKind.NOTES).flatMap { it.brushes }.containsAll(listOf(BrushType.NOTE_PENCIL, BrushType.NOTE_BALLPOINT, BrushType.NOTE_HIGHLIGHTER)))
    }

    @Test fun everyShapeFitsItsFrameInAnyDragDirection() {
        val drags = listOf(Offset(100f, 120f) to Offset(500f, 420f), Offset(500f, 420f) to Offset(100f, 120f), Offset(480f, 90f) to Offset(120f, 380f))
        for (shape in ShapeKind.entries) for ((start, end) in drags) {
            val paths = ShapeCatalog.paths(shape, start, end, ink = 3f)
            // Grafiklerin eksen etiketleri tek noktalık yazı çapalarıdır
            assertTrue("$shape çizgi üretmeli", paths.isNotEmpty() && paths.all { it.points.size >= 2 || it.label != null })
            val frame = if (shape.fit == ShapeFit.LINE) Rect(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y))
            else ShapeCatalog.frame(shape, start, end)
            // Ok uçları ve çentikler çizginin çevresine biraz taşabilir
            val slack = if (shape.fit == ShapeFit.LINE || shape == ShapeKind.AXES || shape == ShapeKind.PARALLEL_LINES) 40f else 2f
            val box = frame.inflate(slack)
            paths.forEach { path -> path.points.forEach { assertTrue("$shape $it kutu $frame dışında", box.contains(it) || it.x == box.right || it.y == box.bottom) } }
            if (shape.fit == ShapeFit.SQUARE) assertEquals("$shape kare kutu", frame.width, frame.height, 0.01f)
        }
        // Kapalı şekiller başladığı yerde biter
        ShapeCatalog.paths(ShapeKind.HEXAGON, Offset.Zero, Offset(200f, 200f), 2f).single().let { assertEquals(it.points.first(), it.points.last()) }
        // Katı cisimlerin görünmeyen ayrıtları kesik çizgi (birden çok kısa parça)
        val cube = ShapeCatalog.paths(ShapeKind.CUBE, Offset.Zero, Offset(400f, 400f), 3f)
        assertTrue("küpte kesik ayrıt parçaları olmalı: ${cube.size}", cube.size > 12)
    }

    @Test fun textElementRoundTripsThroughCodecAndMeasuresFromItsSettings() {
        val spec = TextSpec("Pitagoras\na² + b² = c²", TextFont.SERIF, bold = true, italic = true, underline = true, align = TextAlignKind.CENTER)
        val quad = TextRenderer.quadAt(spec, 40f, 100f, 200f)
        val text = Stroke("8f14e45f-ceea-467a-9575-3f3f1d3c2a11", quad.map { StrokePoint(ContentCodec.quantize(it.x), ContentCodec.quantize(it.y)) }, "#224488", BrushType.TEXT, 40f, 1f, 0, text = spec)
        val pen = Stroke("pen", listOf(StrokePoint(1f, 2f), StrokePoint(3f, 4f)), "#000000", BrushType.NOTE_FOUNTAIN, 6f, 1f, 1)
        val decoded = ContentCodec.decode(ContentCodec.encode(listOf(text, pen)))
        assertEquals(listOf(text, pen), decoded)

        // İki satır: kutu yüksekliği tek satırın yaklaşık iki katı; büyük boyut daha büyük kutu
        val (w1, h1) = TextRenderer.measure(spec.copy(content = "Pitagoras"), 40f)
        val (_, h2) = TextRenderer.measure(spec, 40f)
        assertTrue("iki satır daha yüksek: $h1 / $h2", h2 > h1 * 1.6f)
        val (w3, _) = TextRenderer.measure(spec.copy(content = "Pitagoras"), 80f)
        assertEquals(2f, w3 / w1, 0.25f)
        assertTrue(TextRenderer.measure(spec.copy(content = "Pitagoras", bold = false, italic = false), 40f).first < w1 + 1f)

        // Ayar değişince kutu sol-üst köşesinden büyür
        val refit = TextRenderer.refit(text, spec, 80f)
        assertEquals(text.points[0].x, refit[0].x, 0.01f)
        assertEquals(text.points[0].y, refit[0].y, 0.01f)
        assertEquals((text.points[1].x - text.points[0].x) * 2f, refit[1].x - refit[0].x, (refit[1].x - refit[0].x) * 0.12f)
    }

    @Test fun textIsDrawnInsideItsBoxAndBehavesLikeAnElement() {
        val spec = TextSpec("Deneme", TextFont.SANS)
        val quad = TextRenderer.quadAt(spec, 60f, 20f, 20f)
        val text = Stroke("t", quad, "#000000", BrushType.TEXT, 60f, 1f, 0, text = spec)
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        TextRenderer.draw(android.graphics.Canvas(bitmap), text, 0xFF000000.toInt())
        var inside = 0; var outside = 0
        val box = ElementOps.bounds(text)!!
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if ((bitmap.getPixel(x, y) ushr 24) > 40) { if (box.inflate(1f).contains(Offset(x.toFloat(), y.toFloat()))) inside++ else outside++ }
        }
        assertTrue("yazı çizilmeli: $inside", inside > 300)
        assertEquals(0, outside)

        // Yazı çizgilerle birleşmez, kendi elemanıdır; kutusuna dokunulunca bulunur
        val line = Stroke("l", listOf(StrokePoint(0f, box.center.y), StrokePoint(400f, box.center.y)), "#000000", BrushType.NOTE_BALLPOINT, 5f, 1f, 1)
        val index = ElementOps.index(listOf(text, line))
        assertEquals(2, index.elements.size)
        assertNotNull(ElementOps.hitTest(index, box.center + Offset(0f, box.height * 0.3f), 2f))

        // v15: silgi yazıyı (ve diğer nesneleri) silmez; yalnızca çizimleri siler. Yalnızca nesneye değen silgi
        // hiçbir şey silmediği için ayıklanır, yazı yerinde kalır.
        val partial = Stroke("e1", listOf(StrokePoint(box.left, box.center.y), StrokePoint(box.center.x - box.width * 0.3f, box.center.y)), "#000000", BrushType.ERASER, 30f, 1f, 1)
        assertEquals(listOf(text), ErasedStrokePruner.prune(listOf(text, partial)))
        assertTrue(abs(box.width) > 0f)
    }
}
