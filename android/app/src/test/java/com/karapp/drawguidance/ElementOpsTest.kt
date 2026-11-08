package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eleman modelinin (değen çizimler tek eleman, silgi görünmez) ve dönüşümlerin saf testleri. */
class ElementOpsTest {

    /** (x1,y1)'den (x2,y2)'ye 10 px kalınlığında düz çizgi. */
    private fun line(
        id: String, x1: Float, y1: Float, x2: Float, y2: Float,
        type: BrushType = BrushType.MARKER, group: String? = null, size: Float = 10f,
    ) = Stroke(id, listOf(StrokePoint(x1, y1), StrokePoint((x1 + x2) / 2, (y1 + y2) / 2), StrokePoint(x2, y2)), "#112233", type, size, 1f, 0, group)

    @Test fun touchingStrokesFormOneElementSeparateOnesDoNot() {
        // MARKER genişlik çarpanı 1.6 → 10 px fırça 16 px mürekkep, yarısı 8 px
        val a = line("a", 0f, 0f, 100f, 0f)
        val b = line("b", 50f, -40f, 50f, 40f)        // a'yı kesiyor
        val c = line("c", 0f, 14f, 100f, 14f)          // a'ya 14 px uzakta: mürekkepler (8+8) üst üste
        val d = line("d", 0f, 200f, 100f, 200f)        // hiçbir şeye değmiyor
        val index = ElementOps.index(listOf(a, b, c, d))
        assertEquals(2, index.elements.size)
        assertEquals(listOf("a", "b", "c"), index.elements[0].strokeIds)
        assertEquals(listOf("d"), index.elements[1].strokeIds)
        assertEquals(listOf(1, 2), index.elements.map { it.number })
    }

    @Test fun strokesJustApartStaySeparate() {
        val a = line("a", 0f, 0f, 100f, 0f)
        val b = line("b", 0f, 17f, 100f, 17f)          // 17 px > 16 px → değmiyor
        assertEquals(2, ElementOps.index(listOf(a, b)).elements.size)
    }

    @Test fun erasersAreHiddenButTravelWithTheElementTheyErase() {
        val a = line("a", 0f, 0f, 100f, 0f)
        val far = line("far", 0f, 300f, 100f, 300f)
        val eraser = line("e", 40f, -10f, 60f, 10f, type = BrushType.ERASER)
        val orphanEraser = line("o", 500f, 500f, 600f, 500f, type = BrushType.ERASER)
        val index = ElementOps.index(listOf(a, far, eraser, orphanEraser))
        assertEquals(2, index.elements.size)
        assertTrue(index.elements.none { element -> element.strokeIds.any { it == "e" || it == "o" } })
        assertEquals(listOf("e"), index.elements[0].eraserIds)
        assertEquals(setOf("a", "e"), index.expand(setOf("a")))
        // Silgi iki elemanı birleştirmez
        val bridging = line("bridge", 50f, 0f, 50f, 300f, type = BrushType.ERASER)
        assertEquals(2, ElementOps.index(listOf(a, far, bridging)).elements.size)
    }

    @Test fun groupingWorksOnElementsAndSelectionExpandsToGroup() {
        val strokes = listOf(line("a", 0f, 0f, 10f, 0f), line("b", 5f, -5f, 5f, 5f), line("c", 0f, 100f, 10f, 100f), line("d", 0f, 300f, 10f, 300f))
        val grouped = ElementOps.group(strokes, setOf("a", "c"), "g1")
        // "a" ile ona değen "b" aynı eleman olduğu için ikisi de gruba girer
        assertEquals(listOf("g1", "g1", "g1", null), grouped.map { it.groupId })
        val index = ElementOps.index(grouped)
        assertEquals(setOf("a", "b", "c"), index.expand(setOf("c")))
        val rows = ElementOps.rows(index)
        assertEquals(2, rows.size)
        assertTrue(rows[0] is ElementRow.Single)          // en yeni: d
        val group = rows[1] as ElementRow.Group
        assertEquals(1, group.number)
        assertEquals(2, group.members.size)

        assertEquals(strokes, ElementOps.group(strokes, setOf("a", "b"), "g2")) // tek eleman gruplanmaz
        assertTrue(ElementOps.ungroup(grouped, setOf("b")).all { it.groupId == null })
    }

    @Test fun hitTestFindsTopmostElementWithinTolerance() {
        val a = line("a", 0f, 0f, 100f, 0f)
        val b = line("b", 0f, 200f, 100f, 200f)
        val index = ElementOps.index(listOf(a, b))
        assertEquals("a", ElementOps.hitTest(index, Offset(50f, 12f), tolerance = 6f)?.id)
        assertEquals("b", ElementOps.hitTest(index, Offset(100f, 195f), tolerance = 0f)?.id)
        assertNull(ElementOps.hitTest(index, Offset(50f, 100f), tolerance = 10f))
    }

    @Test fun rotateAndScaleAroundPivot() {
        val s = listOf(line("a", 10f, 0f, 30f, 0f, size = 10f))
        val rotated = ElementOps.rotate(s, setOf("a"), 90f, Offset.Zero)
        assertEquals(0f, rotated[0].points[0].x, 1e-3f)
        assertEquals(10f, rotated[0].points[0].y, 1e-3f)
        assertEquals(30f, rotated[0].points[2].y, 1e-3f)

        val scaled = ElementOps.scale(s, setOf("a"), 2f, 2f, Offset(10f, 0f))
        assertEquals(10f, scaled[0].points[0].x, 1e-3f)
        assertEquals(50f, scaled[0].points[2].x, 1e-3f)
        assertEquals(20f, scaled[0].sizePx, 1e-3f)

        val stretched = ElementOps.scale(s, setOf("a"), 3f, 1f, Offset.Zero)
        assertEquals(90f, stretched[0].points[2].x, 1e-3f)
        assertEquals(10f * kotlin.math.sqrt(3f), stretched[0].sizePx, 1e-3f)
    }

    @Test fun recolorSkipsErasersAndDeleteReindexes() {
        val strokes = listOf(line("a", 0f, 0f, 10f, 0f), line("e", 0f, 0f, 10f, 0f, type = BrushType.ERASER), line("b", 0f, 90f, 10f, 90f))
        val recolored = ElementOps.recolor(strokes, setOf("a", "e"), "#FF0000")
        assertEquals(listOf("#FF0000", "#112233", "#112233"), recolored.map { it.colorHex })
        val deleted = ElementOps.delete(strokes, setOf("a", "e"))
        assertEquals(listOf("b"), deleted.map { it.id })
        assertEquals(0, deleted[0].order)
    }

    @Test fun boundsIncludeBrushWidth() {
        val bounds = ElementOps.bounds(line("a", 10f, 20f, 110f, 20f))!!
        assertEquals(2f, bounds.left, 1e-3f)
        assertEquals(12f, bounds.top, 1e-3f)
        assertEquals(118f, bounds.right, 1e-3f)
    }

    @Test fun indexOfManyStrokesIsFast() {
        val strokes = (0 until 300).map { i ->
            val y = (i / 20) * 40f
            Stroke("s$i", (0..40).map { StrokePoint(i % 20 * 30f + it * 2f, y + (it % 5)) }, "#000000", BrushType.PENCIL, 4f, 1f, i)
        }
        val start = System.nanoTime()
        val index = ElementOps.index(strokes)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("300 çizim için eleman hesabı $ms ms sürdü", ms < 1500)
        assertTrue(index.elements.isNotEmpty())
    }

    // --- Tamamen silinenler ---

    private fun eraser(id: String, x1: Float, y1: Float, x2: Float, y2: Float, size: Float) =
        Stroke(id, listOf(StrokePoint(x1, y1), StrokePoint(x2, y2)), "#000000", BrushType.ERASER, size, 1f, 0)

    private fun ordered(vararg strokes: Stroke) = strokes.mapIndexed { i, s -> s.copy(order = i) }

    @Test fun fullyErasedStrokeAndItsEraserAreRemoved() {
        // 10 px kalem çizgisi (PENCIL 0.5× → 5 px iz) üzerinden geçen 40 px silgi (2× → 80 px iz)
        val pencil = line("p", 0f, 0f, 100f, 0f, type = BrushType.PENCIL)
        val keep = line("k", 0f, 300f, 100f, 300f)
        val wipe = eraser("e", -20f, 0f, 120f, 0f, size = 40f)
        val result = ElementOps.pruneErased(ordered(pencil, keep, wipe))
        assertEquals(listOf("k"), result.map { it.id })
        assertEquals(0, result[0].order)
    }

    @Test fun partiallyErasedStrokeStaysWithItsEraser() {
        val pencil = line("p", 0f, 0f, 100f, 0f, type = BrushType.PENCIL)
        val half = eraser("e", 50f, 0f, 150f, 0f, size = 40f)
        assertEquals(listOf("p", "e"), ElementOps.pruneErased(ordered(pencil, half)).map { it.id })
    }

    @Test fun thinEraserAlongTheCenterDoesNotRemoveAWideStroke() {
        // Marker izi 16 px; 6 px'lik silgi (12 px iz) kenarları örtmez
        val marker = line("m", 0f, 0f, 100f, 0f)
        val narrow = eraser("e", -10f, 0f, 110f, 0f, size = 6f)
        assertEquals(2, ElementOps.pruneErased(ordered(marker, narrow)).size)
    }

    @Test fun eraserDrawnBeforeAStrokeDoesNotEraseIt() {
        val wipe = eraser("e", -20f, 0f, 120f, 0f, size = 40f)
        val pencil = line("p", 0f, 0f, 100f, 0f, type = BrushType.PENCIL)
        // Silgi kendinden sonra çizileni silmez; hiçbir şeye değmediği için silgi atılır
        assertEquals(listOf("p"), ElementOps.pruneErased(ordered(wipe, pencil)).map { it.id })
    }

    @Test fun imagesAreSeparateElementsAndCanBeHitMovedAndErased() {
        val image = Stroke("img", listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f), StrokePoint(100f, 50f), StrokePoint(0f, 50f)), "#000000", BrushType.IMAGE, 1f, 1f, 0, assetId = "a1")
        val over = line("over", 10f, 25f, 90f, 25f)   // resmin üzerine çizilen çizgi resme katılmaz
        val index = ElementOps.index(ordered(image, over))
        assertEquals(2, index.elements.size)
        assertEquals("over", ElementOps.hitTest(index, Offset(50f, 25f), 2f)?.id)
        assertEquals("img", ElementOps.hitTest(index, Offset(50f, 45f), 0f)?.id)
        assertEquals(20f, ElementOps.translate(listOf(image), setOf("img"), 20f, 0f)[0].points[0].x, 1e-3f)

        val wipe = Stroke("e", listOf(StrokePoint(-10f, 25f), StrokePoint(110f, 25f)), "#000000", BrushType.ERASER, 40f, 1f, 1)
        assertEquals(emptyList<String>(), ElementOps.pruneErased(listOf(image, wipe)).map { it.id })
    }

    // --- Yalnızca gerçekten birleşik çizimler aynı eleman ---

    private fun eraserLine(id: String, x1: Float, y1: Float, x2: Float, y2: Float, size: Float, flow: Float = 1f, opacity: Float = 1f) =
        Stroke(id, listOf(StrokePoint(x1, y1), StrokePoint((x1 + x2) / 2, (y1 + y2) / 2), StrokePoint(x2, y2)), "#000000", BrushType.ERASER, size, opacity, 0, flow = flow)

    @Test fun erasingTheCrossingSplitsTheElement() {
        // İki marker çizgisi (16 birim kalın) artı işareti gibi kesişiyor
        val h = line("h", 0f, 100f, 200f, 100f)
        val v = line("v", 100f, 0f, 100f, 200f)
        assertEquals(1, ElementOps.index(ordered(h, v)).elements.size)
        // Kesişim bölgesini tamamen silen tam silgi (yarıçap 20): artık birbirine değmiyorlar
        val cut = eraserLine("cut", 100f, 70f, 100f, 130f, 20f)
        val split = ElementOps.index(ordered(h, v, cut))
        assertEquals("kesişim silinince iki ayrı eleman olmalı", 2, split.elements.size)
        assertEquals(listOf(listOf("h"), listOf("v")), split.elements.map { it.strokeIds })
        // Silgi yine görünmez ve bir elemana bağlı
        assertEquals(1, split.elements.sumOf { it.eraserIds.size })
    }

    @Test fun partialOrSoftErasingKeepsTheConnection() {
        val h = line("h", 0f, 100f, 200f, 100f)
        val v = line("v", 100f, 0f, 100f, 200f)
        // Kesişimin yalnızca bir kısmını silen dar silgi (yarıçap 4)
        val narrow = eraserLine("narrow", 60f, 100f, 140f, 100f, 4f)
        assertEquals(1, ElementOps.index(ordered(h, v, narrow)).elements.size)
        // Yarı akışkan silgi tamamen silmez
        val soft = eraserLine("soft", 100f, 70f, 100f, 130f, 20f, flow = 0.4f)
        assertEquals(1, ElementOps.index(ordered(h, v, soft)).elements.size)
        // Silgiden SONRA çizilen çizgi o bölgede yeniden değer
        val cut = eraserLine("cut", 100f, 70f, 100f, 130f, 20f)
        val again = line("again", 90f, 100f, 110f, 100f)
        val index = ElementOps.index(ordered(h, v, cut, again))
        assertEquals("yeni çizgi ikisini yeniden birleştirir", 1, index.elements.size)
    }

    @Test fun twoContactsNeedBothErasedToSplit() {
        // Dikdörtgen gibi iki yerden değen çizimler: tek temas silinirse hâlâ birleşik
        val a = Stroke("a", listOf(StrokePoint(0f, 0f), StrokePoint(200f, 0f), StrokePoint(200f, 100f)), "#112233", BrushType.MARKER, 10f, 1f, 0)
        val b = Stroke("b", listOf(StrokePoint(0f, 0f), StrokePoint(0f, 100f), StrokePoint(200f, 100f)), "#112233", BrushType.MARKER, 10f, 1f, 1)
        val cutFirst = eraserLine("c1", -30f, 0f, 30f, 0f, 22f)
        assertEquals(1, ElementOps.index(ordered(a, b, cutFirst)).elements.size)
        val cutSecond = eraserLine("c2", 170f, 100f, 230f, 100f, 22f)
        assertEquals(2, ElementOps.index(ordered(a, b, cutFirst, cutSecond)).elements.size)
    }

    @Test fun strokesThatNeverTouchAreNeverOneElement() {
        val random = kotlin.random.Random(9)
        repeat(200) {
            val y2 = random.nextFloat() * 60f + 30f
            val sizeA = random.nextInt(2, 30).toFloat(); val sizeB = random.nextInt(2, 30).toFloat()
            val a = line("a", 0f, 0f, 200f, 0f, size = sizeA, type = BrushType.PENCIL)
            val b = line("b", 0f, y2, 200f, y2, size = sizeB, type = BrushType.PENCIL)
            val gap = y2 - ElementOps.halfWidth(a) - ElementOps.halfWidth(b)
            val expected = if (gap > 0.6f) 2 else 1
            if (kotlin.math.abs(gap) > 0.6f) assertEquals("aralık $gap", expected, ElementOps.index(ordered(a, b)).elements.size)
        }
    }
}
