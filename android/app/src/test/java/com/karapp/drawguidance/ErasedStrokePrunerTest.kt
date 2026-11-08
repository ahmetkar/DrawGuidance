package com.karapp.drawguidance

import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.ErasedStrokePruner
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Silgiyle tamamen yok edilen çizimler (ve elemanları) veriden ve elemanlar listesinden kalkar. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class ErasedStrokePrunerTest {

    @After fun tearDown() = stopKoin()

    private fun line(
        id: String, x1: Float, y1: Float, x2: Float, y2: Float, type: BrushType, size: Float,
        opacity: Float = 1f, flow: Float = 1f,
    ) = Stroke(id, (0..20).map { StrokePoint(x1 + (x2 - x1) * it / 20f, y1 + (y2 - y1) * it / 20f) }, "#223344", type, size, opacity, 0, flow = flow)

    private fun ordered(vararg s: Stroke) = s.mapIndexed { i, st -> st.copy(order = i) }

    @Test fun strokeMadeInvisibleByRepeatedSoftEraserPassesIsRemovedWithItsErasers() {
        // Yarı akışkan silgiyle dört kez üzerinden geçilen pastel çizgi: tek geçiş silmez, birlikte görünmez yapar
        val pastel = line("p", 50f, 100f, 250f, 100f, BrushType.PASTEL, 18f)
        val passes = (0 until 4).map { i -> line("e$i", 30f, 100f + (i - 1.5f) * 3f, 270f, 100f + (i - 1.5f) * 3f, BrushType.ERASER, 24f, flow = 0.5f) }
        val strokes = ordered(pastel, *passes.toTypedArray())
        assertEquals("eski geometrik test bunu bulamıyordu", strokes.size, ElementOps.pruneErased(strokes).size)
        // Tek geçiş yetmez
        assertEquals(listOf("p", "e0"), ErasedStrokePruner.prune(ordered(pastel, passes[0]), null).map { it.id })

        val result = ErasedStrokePruner.prune(strokes, focus = Rect(0f, 0f, 300f, 200f))
        assertTrue("çizgi ve silgiler kalkmalı: ${result.map { it.id }}", result.isEmpty())
        assertEquals(0, ElementOps.index(result).elements.size)
    }

    @Test fun strokeCoveredOnlyByManyNarrowEraserPassesTogetherIsRemoved() {
        val splatter = line("sp", 50f, 100f, 250f, 100f, BrushType.TEXTURE_SPLATTER, 16f)
        val passes = (0 until 12).map { i -> line("e$i", 0f, 40f + i * 10f, 300f, 40f + i * 10f, BrushType.ERASER, 7f) }
        val strokes = ordered(splatter, *passes.toTypedArray())
        val result = ErasedStrokePruner.prune(strokes, focus = null)
        assertTrue("serpinti tamamen silindi: ${result.map { it.id }}", result.none { it.id == "sp" })
    }

    @Test fun erasingEverythingOnACanvasEmptiesTheElementList() {
        val drawing = listOf(
            line("a", 40f, 40f, 200f, 60f, BrushType.MARKER, 14f),
            line("b", 60f, 120f, 220f, 90f, BrushType.CHARCOAL, 16f),
            line("c", 30f, 170f, 230f, 170f, BrushType.HIGHLIGHTER, 20f),
            line("d", 120f, 20f, 120f, 200f, BrushType.AIRBRUSH, 20f),
        )
        // Tuvalin tamamını tarayan geniş silgi geçişleri
        val sweep = (0 until 9).map { i -> line("s$i", -40f, i * 30f, 300f, i * 30f, BrushType.ERASER, 22f) }
        val strokes = ordered(*(drawing + sweep).toTypedArray())
        val focus = Rect(-100f, -100f, 400f, 400f)
        val result = ErasedStrokePruner.prune(strokes, focus)
        assertTrue("her şey silindi, liste boş olmalı: ${result.map { it.id }}", result.isEmpty())
    }

    @Test fun partlyErasedFaintAndSoftErasedStrokesStay() {
        val partly = line("partly", 50f, 100f, 250f, 100f, BrushType.PENCIL, 6f)
        val halfEraser = line("half", 40f, 100f, 150f, 100f, BrushType.ERASER, 20f)
        val faint = line("faint", 50f, 300f, 250f, 300f, BrushType.MARKER, 14f, opacity = 0.08f)
        val nearFaint = line("near", 40f, 330f, 260f, 330f, BrushType.ERASER, 20f) // değiyor ama örtmüyor
        val soft = line("soft", 50f, 500f, 250f, 500f, BrushType.MARKER, 14f)
        val softEraser = line("softE", 30f, 500f, 270f, 500f, BrushType.ERASER, 40f, flow = 0.4f)
        val highlighter = line("hl", 50f, 700f, 250f, 700f, BrushType.HIGHLIGHTER, 20f)
        val hlEraser = line("hlE", 40f, 700f, 120f, 700f, BrushType.ERASER, 70f)
        val strokes = ordered(partly, halfEraser, faint, nearFaint, soft, softEraser, highlighter, hlEraser)
        val result = ErasedStrokePruner.prune(strokes, focus = null).map { it.id }.toSet()
        assertTrue("kısmen silinen kalmalı: $result", "partly" in result)
        assertTrue("soluk ama silinmemiş kalmalı: $result", "faint" in result)
        assertTrue("yarı akışkan silgi tamamen silmez: $result", "soft" in result)
        assertTrue("fosforlu (çarpma karışımı) kısmen silindi, kalmalı: $result", "hl" in result)
    }

    @Test fun fullyErasedHighlighterIsRemovedAndObjectsAreDeletedOnTouch() {
        val highlighter = line("hl", 50f, 100f, 250f, 100f, BrushType.HIGHLIGHTER, 20f)
        val image = Stroke("img", listOf(StrokePoint(400f, 50f), StrokePoint(500f, 50f), StrokePoint(500f, 120f), StrokePoint(400f, 120f)), "#000000", BrushType.IMAGE, 1f, 1f, 0, assetId = "x")
        val erasers = (0 until 6).map { i -> line("e$i", 20f, 40f + i * 18f, 540f, 40f + i * 18f, BrushType.ERASER, 16f) }
        val strokes = ordered(highlighter, image, *erasers.toTypedArray())
        // v9: nesne elemanlar (resim, yazı, şekil, diyagram) silgi değdiği anda bütün olarak silinir,
        // bu yüzden piksel tabanlı ayıklama onlara dokunmaz.
        val touched = com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.objectsHitBy(strokes, erasers.first())
        assertTrue("silgi değen resim tamamen silinir: $touched", "img" in touched)
        val result = ErasedStrokePruner.prune(strokes.filterNot { it.id in touched }, focus = Rect(0f, 0f, 600f, 200f))
        assertTrue("fosforlu tamamen silindi: ${result.map { it.id }}", result.none { it.id == "hl" })
        assertTrue("boşa kalan silgiler de kalkmalı: ${result.map { it.id }}", result.isEmpty())
    }

    @Test fun eraserDrawnBeforeAStrokeDoesNotHideIt() {
        val eraser = line("e", 30f, 100f, 270f, 100f, BrushType.ERASER, 40f)
        val after = line("after", 50f, 100f, 250f, 100f, BrushType.MARKER, 14f)
        val result = ErasedStrokePruner.prune(ordered(eraser, after), null)
        assertEquals(listOf("after"), result.map { it.id })
    }

    private fun render(strokes: List<Stroke>): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(420, 320, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        for (s in strokes) {
            if (s.brushType == BrushType.IMAGE) continue
            com.karapp.drawguidance.ui.drawing.BrushPainter.paint(
                canvas, FloatArray(s.points.size) { s.points[it].x }, FloatArray(s.points.size) { s.points[it].y }, s.points.size,
                0xFF223344.toInt(), s.brushType, s.sizePx, s.opacity, s.id.hashCode(), s.flow, s.strength,
            )
        }
        return bitmap
    }

    @Test fun pruningNeverChangesWhatIsVisible() {
        val random = kotlin.random.Random(11)
        val brushes = listOf(BrushType.PENCIL, BrushType.MARKER, BrushType.PASTEL, BrushType.CHARCOAL, BrushType.AIRBRUSH,
            BrushType.TEXTURE_SPLATTER, BrushType.STAMP_STAR, BrushType.HIGHLIGHTER, BrushType.FLAT_BRUSH, BrushType.HALFTONE_DOTS)
        var totalRemoved = 0
        repeat(16) { scene ->
            val strokes = ArrayList<Stroke>()
            repeat(random.nextInt(4, 12)) { i ->
                val eraser = random.nextInt(3) == 0
                val x = random.nextFloat() * 360f; val y = random.nextFloat() * 260f
                val pts = (0..random.nextInt(3, 25)).map { StrokePoint(x + it * (random.nextFloat() * 14f - 4f), y + it * (random.nextFloat() * 10f - 5f)) }
                strokes += if (eraser) Stroke("e$scene-$i", pts, "#000000", BrushType.ERASER, random.nextInt(6, 40).toFloat(), if (random.nextBoolean()) 1f else 0.6f, i, flow = if (random.nextBoolean()) 1f else 0.5f)
                else Stroke("s$scene-$i", pts, "#223344", brushes.random(random), random.nextInt(4, 24).toFloat(), 1f, i)
            }
            val before = render(strokes)
            val pruned = ErasedStrokePruner.prune(strokes, null)
            totalRemoved += strokes.size - pruned.size
            val after = render(pruned)
            var bigDiff = 0
            for (yy in 0 until before.height) for (xx in 0 until before.width) {
                val a = before.getPixel(xx, yy); val b = after.getPixel(xx, yy)
                if (kotlin.math.abs(android.graphics.Color.alpha(a) - android.graphics.Color.alpha(b)) > 40) bigDiff++
            }
            assertTrue("sahne $scene: temizlik görüntüyü değiştirdi ($bigDiff piksel), kaldırılan: ${strokes.map { it.id } - pruned.map { it.id }.toSet()}", bigDiff <= 4)
        }
        println("PRUNE rastgele sahnelerde kaldırılan çizim/silgi: $totalRemoved")
    }
}
