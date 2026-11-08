package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.ink.InkShapeKind
import com.karapp.drawguidance.content.ink.InkSynth
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.VectorEdit
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
import com.karapp.drawguidance.ui.screens.canvasedit.state.LineSuggester
import com.karapp.drawguidance.ui.screens.canvasedit.state.ShapeSuggester
import com.karapp.drawguidance.ui.screens.canvasedit.state.SingleInk
import com.karapp.drawguidance.ui.screens.canvasedit.state.TextSuggester
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** v39 testlerinin çizgileri (tuval koordinatı). */
internal object V39Ink {
    /** Titrek çember (tek çizgi; [n] nokta). */
    fun circle(id: String, cx: Float, cy: Float, r: Float, n: Int = 90, seed: Long = 1, brush: BrushType = BrushType.MARKER): Stroke {
        val rnd = java.util.Random(seed)
        return V38Ink.stroke(id, (0..n).map { i ->
            val a = i / n.toDouble() * 2 * PI
            val rr = r * (1f + (rnd.nextFloat() - 0.5f) * 0.06f)
            (cx + rr * cos(a).toFloat()) to (cy + rr * sin(a).toFloat())
        }, brush)
    }

    /** Tek çizgilik yazılmış karakter ([InkSynth]), yüksekliği [height] olacak biçimde ölçeklenmiş. */
    fun glyph(id: String, ch: String, seed: Int, height: Float, x: Float = 200f, y: Float = 200f): Stroke? {
        val ink = InkSynth.sample(ch, kotlin.random.Random(seed)).singleOrNull() ?: return null
        val minX = ink.points.minOf { it.x }; val minY = ink.points.minOf { it.y }
        val h = (ink.points.maxOf { it.y } - minY).coerceAtLeast(1f)
        val k = height / h
        return V38Ink.stroke(id, ink.points.map { (x + (it.x - minX) * k) to (y + (it.y - minY) * k) }, BrushType.NOTE_BALLPOINT)
    }
}

/**
 * v39 (2) öneri kuralları (algoritma düzeyi): yazı ve çizgi önerisi tek çizgide de çalışır ama yalnız çizim miktarı
 * fazlaysa; küçük tek çizgiye öneri yok; şekil önerisi hâlâ 4+ eleman.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V39SuggestLogicTest {
    @After
    fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun singleLineOnlyWhenBig() {
        val engine = LineSuggester()
        // Küçük tek çizgi: öneri yok
        val small = V38Ink.line("s", 100f, 100f, 300f, 104f, 1)
        assertFalse(SingleInk.bigLine(small, 1000f, 1000f))
        assertNull(engine.suggest(listOf(small), 1000f, 1000f))
        val smallCircle = V39Ink.circle("sc", 300f, 300f, 60f)
        assertNull("küçük çember", engine.suggest(listOf(smallCircle), 1000f, 1000f))
        // Büyük tek çember (yol ~1250 px): tek çizgiyle çizgi önerisi, düzgün çembere
        val big = V39Ink.circle("b", 500f, 500f, 200f)
        assertTrue(SingleInk.bigLine(big, 1000f, 1000f))
        val s = engine.suggest(listOf(big), 1000f, 1000f)
        assertNotNull(s)
        assertEquals(setOf("b"), s!!.strokeIds)
        assertEquals(InkShapeKind.CIRCLE, s.fixes.single().kind)
        // Aynı çember çok büyük tuvalde azdır
        assertNull(engine.suggest(listOf(big), 4000f, 4000f))
    }

    @Test
    fun singleTextOnlyWhenBigAndConfident() {
        val engine = TextSuggester { "tr" }
        // Tek çizgilik büyük "2" (yükseklik 200 px, 1000 px tuval): yazı önerisi
        val two = (1..20).firstNotNullOf { seed -> V39Ink.glyph("t$seed", "2", seed, 200f)?.takeIf { engine.single(it, 1000f, 1000f) != null } }
        val s = engine.suggest(listOf(two), canvasW = 1000f, canvasH = 1000f)
        assertNotNull(s)
        assertEquals("2", s!!.text)
        assertEquals(setOf(two.id), s.strokeIds)
        assertTrue(engine.looksLikeText(listOf(two), 1000f, 1000f))
        // Aynı karakter olağan not boyunda (30 px): öneri yok
        val small = V38Ink.stroke("small", two.points.map { (200f + (it.x - 200f) * 0.15f) to (200f + (it.y - 200f) * 0.15f) }, BrushType.NOTE_BALLPOINT)
        assertNull(engine.suggest(listOf(small), canvasW = 1000f, canvasH = 1000f))
        assertFalse(engine.looksLikeText(listOf(small), 1000f, 1000f))
        // Büyük çember yazı değil (çizim): tek çizgide de yazı önerisi yok
        val circle = V39Ink.circle("c", 500f, 500f, 200f, brush = BrushType.NOTE_BALLPOINT)
        assertTrue(SingleInk.geometric(circle))
        assertNull(engine.suggest(listOf(circle), canvasW = 1000f, canvasH = 1000f))
        // Yazı kalemi olmayan fırçayla yazı önerisi yok
        assertNull(engine.single(two.copy(brushType = BrushType.CHARCOAL), 1000f, 1000f))
    }

    @Test
    fun shapeStillNeedsFour() {
        val big = V39Ink.circle("b", 500f, 500f, 200f)
        assertNull(ShapeSuggester(null).suggest(listOf(big), listOf(big)) { true })
    }
}

/**
 * v39 (1) boya kutusu her modda önce seçer sonra boyar: çocuk kovası (dokunma: alan, 2 sn: bütün şekil), basit modların
 * Şekil boyaması ve Düzenle'de bekleyen Alan boya. Boyanan şekil seçili olur; boyama tek geri alma adımı, seçim geri
 * alma adımı değil. (2) öneri akışı: tek büyük çizgiye çizgi önerisi (çocuk modunda maskot), küçük tek çizgiye öneri
 * yok, seçili tek büyük çizgide "Düzeltme öner".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V39BucketAndSuggestTest : V37KidsBase() {

    private fun open(kind: CanvasKind, name: String, ai: Boolean = true) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = ai, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun strokes() = vm().canvas.value!!.strokes
    private fun selected() = vm().selectedIds.value

    private fun settle() {
        advanceMain(900)
        tick(600)
        advanceMain(300)
    }

    private fun noSuggestion() =
        vm().lineSuggestion.value == null && vm().shapeSuggestion.value == null && vm().textSuggestion.value == null

    /** Ekranda titrek çember (tuval düğümünün koordinatı). */
    private fun circleOnScreen(c: Offset, r: Float, seed: Long, n: Int = 72) {
        val rnd = java.util.Random(seed)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(Offset(c.x + r, c.y))
            for (i in 1..n) {
                val a = i / n.toDouble() * 2 * PI
                val rr = r * (1f + (rnd.nextFloat() - 0.5f) * 0.06f)
                moveTo(Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat()))
            }
            up()
        }
        compose.waitForIdle()
    }

    @Test
    fun kidsBucketTapSelectsThenPaintsOneUndoStep() {
        openKids()
        val house = addKidShape("lib.kid_house")
        compose.runOnIdle { vm().clearSelection() }
        pickPaint("Kırmızı")
        assertTrue(selected().isEmpty())
        tap(screenOf(280f + 4.4f * 30f, 280f + 4.4f * 40f))
        waitFor { regions(house).any { it.fillColorHex.equals("#E53935", true) } }
        assertEquals("boyanan şekil seçili", setOf(house), selected())
        assertEquals(EditMode.PAINT, vm().mode.value)
        shot("v39-kids-bucket-selected")
        // Tek geri alma adımı: boya kalkar, geri alınacak başka adım (seçim) yok
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { regions(house).isEmpty() }
        // Bir önceki adım şeklin eklenmesi (seçim ayrı bir adım değil)
        compose.runOnIdle { vm().undo() }
        waitFor { strokes().none { it.id == house } }
        compose.runOnIdle { vm().redo() }
        waitFor { strokes().any { it.id == house } && regions(house).isEmpty() }
        // Boşluğa dokunmak seçimi kaldırır (boyanacak şekil yok)
        val size = canvasSize()
        tap(Offset(size.x * 0.05f, size.y * 0.05f))
        waitFor { selected().isEmpty() }
    }

    @Test
    fun kidsBucketHoldSelectsWholeShape() {
        openKids()
        val balloons = addKidShape("lib.kid_balloons")
        compose.runOnIdle { vm().clearSelection() }
        pickPaint("Pembe")
        val at = screenOf(300f + 4f * 50f, 280f + 4f * 44f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drawing_canvas").performTouchInput { down(at) }
        waitFor { vm().paintCursor.value?.target != null }
        tick(2300)
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        waitFor { ShapeFactory.subPaths(shape(balloons)).count { it.closed && it.fillColorHex.equals("#EC407A", true) } >= 4 }
        assertEquals(setOf(balloons), selected())
        shot("v39-kids-bucket-whole-selected")
    }

    @Test
    fun simpleArtPaintSelectsThenPaints() {
        open(CanvasKind.SIMPLE_ART, "Basit boya", ai = false)
        compose.runOnIdle {
            vm().selectShapeId("lib.kid_house")
            vm().addShapeAt("g-h", Offset(500f, 500f))
        }
        waitFor { strokes().any { it.shape != null } }
        val house = strokes().first { it.shape != null }.id
        compose.runOnIdle { vm().clearSelection(); vm().chooseMode(EditMode.PAINT) }
        waitFor { vm().mode.value == EditMode.PAINT }
        tap(screenOf(280f + 4.4f * 30f, 280f + 4.4f * 84f))
        waitFor { regions(house).isNotEmpty() }
        assertEquals(setOf(house), selected())
        shot("v39-simple-paint-selected")
        compose.runOnIdle { vm().undo() }
        waitFor { regions(house).isEmpty() }
    }

    @Test
    fun advancedPendingRegionFillSelectsThenPaints() {
        open(CanvasKind.ART, "Alan boya", ai = false)
        compose.runOnIdle {
            vm().selectShapeId("lib.kid_house")
            vm().addShapeAt("g-h", Offset(500f, 500f))
        }
        waitFor { strokes().any { it.shape != null } }
        val house = strokes().first { it.shape != null }.id
        compose.runOnIdle { vm().clearSelection(); vm().chooseMode(EditMode.DRAW) }
        mode("Düzenle")
        waitFor { vm().mode.value == EditMode.EDIT_PICK && hasTopTool("Alan boya") }
        topTool("Alan boya").performClick()
        waitFor { vm().pendingShapeTool.value == ShapeTool.FILL }
        // Duvara tek dokunuş: şekil seçilir, Alan boya ile açılır ve duvar boyanır
        tap(screenOf(280f + 4.4f * 30f, 280f + 4.4f * 84f))
        waitFor { vm().mode.value == EditMode.SHAPE_EDIT }
        assertEquals(ShapeTool.FILL, vm().shapeTool.value)
        assertEquals(setOf(house), selected())
        waitFor { regions(house).isNotEmpty() }
        shot("v39-edit-region-fill-selected")
        // Boyama tek geri alma adımı
        compose.runOnIdle { vm().undo() }
        waitFor { regions(house).isEmpty() }
    }

    @Test
    fun singleBigStrokeGetsLineSuggestionSmallDoesNot() {
        open(CanvasKind.ART, "Tek çizgi")
        val size = canvasSize()
        // Küçük tek çember: hiçbir öneri yok
        circleOnScreen(Offset(size.x * 0.25f, size.y * 0.2f), size.x * 0.06f, 1)
        waitFor { strokes().size == 1 }
        settle()
        assertTrue(noSuggestion())
        // Uzakta büyük tek çember (tuvalin ~%40'ı çapında): tek çizgiye çizgi önerisi
        circleOnScreen(Offset(size.x * 0.5f, size.y * 0.55f), size.x * 0.22f, 2, n = 96)
        waitFor { strokes().size == 2 }
        settle()
        waitFor(15_000) { vm().lineSuggestion.value != null }
        val big = strokes().last()
        assertEquals(setOf(big.id), vm().lineSuggestion.value!!.strokeIds)
        assertNull("tek çizgiye şekil önerisi yok", vm().shapeSuggestion.value)
        waitFor { exists("line_suggest_chip") }
        shot("v39-single-big-line-suggest")
        compose.onNodeWithTag("line_fix_option", useUnmergedTree = true).performClick()
        waitFor { vm().lineSuggestion.value == null && strokes().last().points != big.points }
        compose.runOnIdle { vm().undo() }
        waitFor { strokes().last().points == big.points }
    }

    @Test
    fun selectionOfOneBigStrokeOffersFix() {
        open(CanvasKind.ART, "Seçim")
        val size = canvasSize()
        circleOnScreen(Offset(size.x * 0.25f, size.y * 0.2f), size.x * 0.06f, 1)
        circleOnScreen(Offset(size.x * 0.5f, size.y * 0.55f), size.x * 0.22f, 2, n = 96)
        waitFor { strokes().size == 2 }
        val (small, big) = strokes()
        compose.runOnIdle { vm().chooseMode(EditMode.SELECT) }
        compose.runOnIdle { vm().selectOnlyAt(small.points[0].x, small.points[0].y, 12f) }
        waitFor { selected() == setOf(small.id) }
        assertFalse("küçük tek çizgi", vm().canSuggestForSelection())
        compose.runOnIdle { vm().selectOnlyAt(big.points[0].x, big.points[0].y, 12f) }
        waitFor { selected() == setOf(big.id) }
        assertTrue("büyük tek çizgi", vm().canSuggestForSelection())
        compose.runOnIdle { vm().suggestInkCorrection() }
        waitFor(15_000) { vm().lineSuggestion.value != null }
        assertEquals(setOf(big.id), vm().lineSuggestion.value!!.strokeIds)
    }

    @Test
    fun kidsMascotSuggestsForOneBigStroke() {
        open(CanvasKind.KIDS, "Çocuk büyük")
        val size = canvasSize()
        circleOnScreen(Offset(size.x * 0.5f, size.y * 0.45f), size.x * 0.25f, 4, n = 96)
        waitFor { strokes().size == 1 }
        settle()
        waitFor(15_000) { exists("kids_mascot_suggest") }
        assertNotNull(vm().lineSuggestion.value)
        assertFalse(exists("line_suggest_chip"))
        shot("v39-kids-mascot-single-big")
    }
}
