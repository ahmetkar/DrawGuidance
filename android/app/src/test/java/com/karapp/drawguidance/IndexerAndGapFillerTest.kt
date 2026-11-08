package com.karapp.drawguidance

import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementIndexer
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.screens.canvasedit.editor.GapFiller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/** Artımlı eleman dizini, performans ve "boşlukları doldur" komutunun saf testleri. */
class IndexerAndGapFillerTest {

    private fun line(
        id: String, x1: Float, y1: Float, x2: Float, y2: Float,
        type: BrushType = BrushType.MARKER, size: Float = 10f, color: String = "#112233",
        flow: Float = 1f, strength: Float = 1f, group: String? = null,
    ) = Stroke(
        id, (0..10).map { StrokePoint(x1 + (x2 - x1) * it / 10f, y1 + (y2 - y1) * it / 10f) },
        color, type, size, 1f, 0, group, flow = flow, strength = strength,
    )

    private fun randomStroke(random: Random, i: Int): Stroke {
        val type = when (random.nextInt(10)) {
            0 -> BrushType.ERASER
            1 -> BrushType.PASTEL
            2 -> BrushType.HALFTONE_DOTS
            3 -> BrushType.STAMP_STAR
            else -> BrushType.PENCIL
        }
        val x = random.nextFloat() * 1500f
        val y = random.nextFloat() * 1500f
        val points = (0..random.nextInt(2, 30)).map { StrokePoint(x + it * random.nextFloat() * 8f, y + it * (random.nextFloat() - 0.5f) * 8f) }
        return Stroke("s$i", points, "#000000", type, random.nextInt(2, 30).toFloat(), 1f, i, if (random.nextInt(8) == 0) "g${random.nextInt(3)}" else null)
    }

    // --- Artımlı dizin ---

    @Test fun incrementalIndexMatchesFullRebuildAcrossAppendsAndEdits() {
        val random = Random(42)
        val indexer = ElementIndexer()
        var strokes = emptyList<Stroke>()
        repeat(400) { step ->
            strokes = when {
                step % 37 == 36 && strokes.size > 3 -> strokes.filterIndexed { i, _ -> i != strokes.size / 2 } // ortadan silme
                step % 53 == 52 -> ElementOps.translate(strokes, strokes.take(5).map { it.id }.toSet(), 30f, -12f) // taşıma
                else -> strokes + randomStroke(random, step)                                                   // yeni çizim
            }
            val incremental = indexer.update(strokes)
            val full = ElementIndexer().update(strokes)
            assertEquals("adım $step: elemanlar farklı", full.elements, incremental.elements)
            assertEquals("adım $step: grup numaraları farklı", full.groupNumbers, incremental.groupNumbers)
        }
    }

    @Test fun appendingToALargeDrawingIsMuchCheaperThanRebuilding() {
        val random = Random(7)
        val all = (0 until 3000).map { randomStroke(random, it) }
        val indexer = ElementIndexer()
        indexer.update(all.take(2900))

        var list = all.take(2900)
        val appendStart = System.nanoTime()
        for (i in 2900 until 3000) { list = list + all[i]; indexer.update(list) }
        val appendAvgMs = (System.nanoTime() - appendStart) / 100 / 1e6

        repeat(2) { ElementIndexer().update(all) } // ısınma
        val rebuildStart = System.nanoTime()
        ElementIndexer().update(all)
        val rebuildMs = (System.nanoTime() - rebuildStart) / 1e6
        println("PERF 3000 çizim: tam dizin=${"%.1f".format(rebuildMs)} ms, çizim başına artımlı=${"%.2f".format(appendAvgMs)} ms")
        assertTrue("artımlı ekleme ($appendAvgMs ms) tam hesaptan ($rebuildMs ms) belirgin hızlı olmalı", appendAvgMs < rebuildMs / 3)
        assertEquals(ElementIndexer().update(list).elements, indexer.update(list).elements)
    }

    @Test fun typicalDrawingWithSomeErasersIndexesQuickly() {
        val random = Random(21)
        val all = (0 until 3000).map { i ->
            val s = randomStroke(random, i)
            if (s.brushType == BrushType.ERASER && random.nextInt(10) != 0) s.copy(brushType = BrushType.PENCIL) else s
        }
        repeat(2) { ElementIndexer().update(all) }
        val t0 = System.nanoTime()
        val index = ElementIndexer().update(all)
        val ms = (System.nanoTime() - t0) / 1e6
        println("PERF tipik 3000 çizim (${all.count { it.brushType == BrushType.ERASER }} silgi): tam dizin=${"%.1f".format(ms)} ms, ${index.elements.size} eleman")
        assertTrue(index.elements.isNotEmpty())
    }

    @Test fun focusedEraserPruningOnALargeDrawingIsFastAndCorrect() {
        val random = Random(3)
        val base = (0 until 3000).map { randomStroke(random, it).copy(brushType = BrushType.PENCIL) }
        val target = line("target", 5000f, 5000f, 5100f, 5000f, type = BrushType.PENCIL, size = 6f)
        val eraser = line("er", 4980f, 5000f, 5120f, 5000f, type = BrushType.ERASER, size = 60f)
        val strokes = (base + target + eraser).mapIndexed { i, s -> s.copy(order = i) }

        val focus = Rect(4900f, 4900f, 5200f, 5100f)
        repeat(2) { ElementOps.pruneErased(strokes, focus) }
        val start = System.nanoTime()
        val focused = ElementOps.pruneErased(strokes, focus)
        val focusedMs = (System.nanoTime() - start) / 1e6
        val fullStart = System.nanoTime()
        val full = ElementOps.pruneErased(strokes)
        val fullMs = (System.nanoTime() - fullStart) / 1e6
        println("PERF silgi temizliği 3000 çizim: odaklı=${"%.1f".format(focusedMs)} ms, tüm tuval=${"%.1f".format(fullMs)} ms")

        assertTrue("tamamen silinen çizim ve silgisi kaldırılmalı", focused.none { it.id == "target" || it.id == "er" })
        assertEquals(3000, focused.size)
        assertEquals(full.map { it.id }, focused.map { it.id })
        assertTrue("odaklı temizlik ($focusedMs ms) tüm tuvalden ($fullMs ms) hızlı olmalı", focusedMs <= fullMs + 25)
    }

    @Test fun lowFlowOrLowOpacityEraserDoesNotRemoveStrokes() {
        val target = line("t", 0f, 0f, 100f, 0f, type = BrushType.PENCIL, size = 4f)
        val softEraser = line("e", -20f, 0f, 120f, 0f, type = BrushType.ERASER, size = 60f, flow = 0.3f)
        val result = ElementOps.pruneErased(listOf(target, softEraser.copy(order = 1)))
        assertEquals(listOf("t", "e"), result.map { it.id })
    }

    // --- Boşlukları doldur ---

    @Test fun collinearGapIsBridgedWithTheFirstStrokesBrushAndBecomesOneElement() {
        val a = line("a", 0f, 0f, 100f, 0f, type = BrushType.PASTEL, size = 12f, color = "#AA3355", flow = 0.4f, strength = 0.7f, group = "g")
        val b = line("b", 160f, 0f, 260f, 0f, type = BrushType.PENCIL, size = 4f).copy(order = 1)
        val strokes = listOf(a, b)
        assertEquals(2, ElementOps.index(strokes).elements.size)

        val result = GapFiller.fill(strokes, setOf("a", "b"))
        assertEquals(1, result.bridges.size)
        val bridge = result.bridges.single()
        assertEquals(BrushType.PASTEL, bridge.brushType)
        assertEquals("#AA3355", bridge.colorHex)
        assertEquals(12f, bridge.sizePx)
        assertEquals(0.4f, bridge.flow)
        assertEquals(0.7f, bridge.strength)
        assertEquals("g", bridge.groupId)
        val ends = listOf(bridge.points.first(), bridge.points.last()).sortedBy { it.x }
        assertEquals(100f, ends[0].x, 0.5f)
        assertEquals(160f, ends[1].x, 0.5f)
        assertTrue("düz boşlukta köprü düz kalmalı", bridge.points.all { kotlin.math.abs(it.y) < 0.5f })
        assertEquals(3, result.strokes.size)
        assertEquals(1, ElementOps.index(result.strokes).elements.size)
    }

    @Test fun cornerGapIsJoinedWithACurveThatFollowsBothDirections() {
        val a = line("a", 0f, 0f, 100f, 0f)
        val b = line("b", 130f, 30f, 130f, 130f)
        val bridge = GapFiller.fill(listOf(a, b), setOf("a", "b")).bridges.single()
        val mid = bridge.points[bridge.points.size / 2]
        assertTrue("köprü köşe bölgesinden geçmeli: $mid", mid.x in 105f..130f && mid.y in 0f..25f)
        // İlk adım yatay (a'nın yönü), son adım dikey (b'nin yönü)
        val p0 = bridge.points[0]; val p1 = bridge.points[1]
        val q0 = bridge.points.last(); val q1 = bridge.points[bridge.points.size - 2]
        assertTrue(kotlin.math.abs(p1.x - p0.x) > kotlin.math.abs(p1.y - p0.y))
        assertTrue(kotlin.math.abs(q1.y - q0.y) > kotlin.math.abs(q1.x - q0.x))
    }

    @Test fun brokenSquareGetsAllFourCornersClosed() {
        val s = listOf(
            line("top", 20f, 0f, 180f, 0f),
            line("right", 200f, 20f, 200f, 180f),
            line("bottom", 180f, 200f, 20f, 200f),
            line("left", 0f, 180f, 0f, 20f),
        ).mapIndexed { i, st -> st.copy(order = i) }
        val result = GapFiller.fill(s, s.map { it.id }.toSet())
        assertEquals(4, result.bridges.size)
        assertEquals(1, ElementOps.index(result.strokes).elements.size)
        // Tekrar çalıştırınca doldurulacak boşluk kalmamalı
        assertEquals(0, GapFiller.fill(result.strokes, result.strokes.map { it.id }.toSet()).bridges.size)
    }

    @Test fun onlySelectedNonEraserStrokesWithinReachAreJoined() {
        val a = line("a", 0f, 0f, 100f, 0f)
        val b = line("b", 160f, 0f, 260f, 0f)
        val far = line("far", 3000f, 0f, 3100f, 0f)
        val touching = line("touch", 100f, 0f, 100f, 80f)
        val eraser = line("er", 100f, 0f, 160f, 0f, type = BrushType.ERASER)

        assertEquals("seçili olmayan çizime köprü kurulmaz", 0, GapFiller.fill(listOf(a, b), setOf("a")).bridges.size)
        assertEquals("silgi dikkate alınmaz", 0, GapFiller.fill(listOf(a, eraser), setOf("a", "er")).bridges.size)
        assertEquals("zaten değen uçlar birleştirilmez", 0, GapFiller.fill(listOf(a, touching), setOf("a", "touch")).bridges.size)
        val farResult = GapFiller.fill(listOf(a, far), setOf("a", "far"))
        assertEquals("çok uzak uçlar birleştirilmez", 0, farResult.bridges.size)
        assertEquals(2, farResult.strokes.size)
    }
}
