package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.ink.InkSynth
import com.karapp.drawguidance.content.shapes.ShapeComplexity
import com.karapp.drawguidance.content.shapes.SuggestRules
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.screens.canvasedit.state.LineSuggester
import com.karapp.drawguidance.ui.screens.canvasedit.state.ShapeSuggester
import com.karapp.drawguidance.ui.screens.canvasedit.state.TemplateSuggester
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

/** v38 öneri testlerinin elle çizilmiş gibi çizgileri (tuval koordinatı). */
internal object V38Ink {
    fun stroke(id: String, pts: List<Pair<Float, Float>>, brush: BrushType = BrushType.MARKER) =
        Stroke(id, pts.map { StrokePoint(it.first, it.second) }, "#112233", brush, 4f, 1f, 0, null)

    /** [a] → [b] titrek doğru. */
    fun line(id: String, ax: Float, ay: Float, bx: Float, by: Float, seed: Long = 1): Stroke {
        val rnd = java.util.Random(seed)
        val n = 30
        val len = kotlin.math.hypot(bx - ax, by - ay)
        val nx = -(by - ay) / len; val ny = (bx - ax) / len
        return stroke(id, (0..n).map { i ->
            val t = i / n.toFloat()
            val w = (rnd.nextFloat() - 0.5f) * 3f
            (ax + (bx - ax) * t + nx * w) to (ay + (by - ay) * t + ny * w)
        })
    }

    /** Dört ayrı kenarla çizilmiş kare (köşelerde küçük boşluklar). */
    fun squareSides(prefix: String, x: Float, y: Float, s: Float): List<Stroke> = listOf(
        line("${prefix}0", x, y, x + s - 4f, y + 2f, 1),
        line("${prefix}1", x + s, y + 3f, x + s - 2f, y + s - 4f, 2),
        line("${prefix}2", x + s - 3f, y + s, x + 4f, y + s - 2f, 3),
        line("${prefix}3", x + 1f, y + s - 4f, x - 1f, y + 4f, 4),
    )

    /** Kalemle yazılmış sözcük ([InkSynth]; her iz ayrı eleman). */
    fun word(prefix: String, text: String, seed: Int, dx: Float = 0f, dy: Float = 0f): List<Stroke> =
        InkSynth.sample(text, kotlin.random.Random(seed)).mapIndexed { i, s ->
            stroke("$prefix$i", s.points.map { (it.x + dx) to (it.y + dy) }, BrushType.NOTE_BALLPOINT)
        }
}

/**
 * v38 yapay zekâ önerilerinin kuralları (algoritma düzeyi): yazı, şekil ve çizgi önerisi ayrı algoritmalar; küçük tek
 * çizgiye öneri yok (v39 büyük tek çizgiye yazı/çizgi önerisi); çizgi 2+, yazı birbirine yakın 2+, şekil birbirine yakın 4+ eleman; uzak duran çizgiler
 * gruplanmaz; çizgi önerisi dikkate alınan bütün çizgileri birlikte düzeltir.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V38AiLogicTest {
    @After
    fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun rules() {
        assertEquals(2, SuggestRules.LINE_MIN)
        assertEquals(2, SuggestRules.TEXT_MIN)
        assertEquals(4, SuggestRules.SHAPE_MIN)
        assertTrue(!ShapeComplexity.usesNet(3) && ShapeComplexity.usesNet(4))
    }

    @Test
    fun lineNeedsTwoStrokesAndFixesThemTogether() {
        val engine = LineSuggester()
        val a = V38Ink.line("a", 100f, 100f, 300f, 104f, 1)
        // Tek çizgi: öneri yok
        assertNull(engine.suggest(listOf(a)))
        assertEquals(listOf(a), engine.group(listOf(a), a))
        // İkinci çizgi ilkinin ucunda (L): ikisi birlikte düzelir, köşe birleşir
        val b = V38Ink.line("b", 306f, 108f, 302f, 300f, 2)
        val g = engine.group(listOf(a, b), b)
        assertEquals(listOf("a", "b"), g.map { it.id })
        val s = engine.suggest(g)
        assertNotNull(s)
        assertEquals(setOf("a", "b"), s!!.strokeIds)
        assertTrue("uçlar birleşti: ${s.joints}", s.joints >= 1)
        val fa = s.fixes.first { it.strokeId == "a" }.points; val fb = s.fixes.first { it.strokeId == "b" }.points
        val corner = listOf(fa.first(), fa.last()).minOf { p -> listOf(fb.first(), fb.last()).minOf { q -> kotlin.math.hypot(p.x - q.x, p.y - q.y) } }
        assertTrue("köşe kapandı ($corner)", corner < 1.5f)
    }

    /** İki yayla çizilen çember (eğitimdeki ay): iki yay birlikte düzelir, uçları birleşir. */
    @Test
    fun twoArcsAreFixedTogether() {
        val rnd = java.util.Random(7)
        val pts = (0..80).map { i ->
            val a = i / 80.0 * 2 * Math.PI
            val rr = 100f * (1f + (rnd.nextFloat() - 0.5f) * 0.08f)
            (300f + rr * kotlin.math.cos(a).toFloat()) to (300f + rr * kotlin.math.sin(a).toFloat())
        }
        val a = V38Ink.stroke("a", pts.subList(0, 41)); val b = V38Ink.stroke("b", pts.subList(42, 81))
        val s = LineSuggester().suggest(listOf(a, b))
        assertNotNull(s)
        assertEquals(setOf("a", "b"), s!!.strokeIds)
        val all = s.fixes.flatMap { it.points }
        val cx = all.map { it.x }.average().toFloat(); val cy = all.map { it.y }.average().toFloat()
        val radii = all.map { kotlin.math.hypot(it.x - cx, it.y - cy) }
        println("iki yay: ${s.fixes.map { it.kind }} yarıçap ${radii.min()}..${radii.max()} ort ${radii.average()} birleşen ${s.joints}")
        // Ortak çemberin yayları: tek düzgün çember, uçlar birleşik
        assertTrue("düzgün çember (uçların birleştiği yerde küçük kayma)", radii.max() - radii.min() < radii.average() * 0.06f)
        assertTrue(s.joints >= 1)
        // Düz çizgiler yaya dönmez (Kåsa uydurmasının kaplama sınırı)
        val l1 = V38Ink.line("l1", 100f, 100f, 400f, 104f, 1); val l2 = V38Ink.line("l2", 100f, 130f, 400f, 133f, 2)
        assertTrue(LineSuggester().suggest(listOf(l1, l2))?.fixes.orEmpty().all { it.kind == com.karapp.drawguidance.content.ink.InkShapeKind.LINE })
    }

    @Test
    fun farStrokesAreNotGrouped() {
        val engine = LineSuggester()
        val a = V38Ink.line("a", 100f, 100f, 200f, 100f, 1)
        val far = V38Ink.line("far", 900f, 900f, 1000f, 900f, 2)
        assertEquals(listOf("far"), engine.group(listOf(a, far), far).map { it.id })
        // Şekil gruplaması da uzaktakini almaz
        val sq = V38Ink.squareSides("q", 100f, 100f, 200f)
        val all = listOf(far) + sq
        assertEquals(sq.map { it.id }, TemplateSuggester.group(all, sq.last()) { true }.map { it.id })
    }

    @Test
    fun shapeNeedsFourCloseElements() {
        val engine = ShapeSuggester(null)
        val sq = V38Ink.squareSides("q", 100f, 100f, 200f)
        // 3 kenar: şekil önerisi yok
        val three = sq.take(3)
        assertNull(engine.suggest(three, three) { true })
        // 4 kenar: kare/dikdörtgen önerilir; öneri dört kenarın hepsini değiştirir
        val s = engine.suggest(sq, sq) { true }
        assertNotNull(s)
        assertEquals(sq.map { it.id }.toSet(), s!!.strokeIds)
        assertTrue(s.templates.toString(), s.templates.all { it.strokeIds == s.strokeIds })
    }

    @Test
    fun textNeedsTwoCloseElements() {
        val engine = TextSuggester { "tr" }
        val word = V38Ink.word("w", "merhaba", 3801, 100f, 100f)
        assertTrue(word.size >= 2)
        // v39 küçük tek iz (olağan not boyu, ~30 px): yazı önerisi yok (büyük tek iz için bkz. V39SuggestLogicTest)
        val first = word.first()
        val y0 = first.points.minOf { it.y }; val h = (first.points.maxOf { it.y } - y0).coerceAtLeast(1f)
        val small = first.copy(points = first.points.map { it.copy(x = 100f + (it.x - 100f) * 30f / h, y = y0 + (it.y - y0) * 30f / h) })
        assertNull(engine.suggest(listOf(small), canvasW = 1000f, canvasH = 1000f))
        assertFalse(engine.looksLikeText(listOf(small), 1000f, 1000f))
        // Yakın izler satırda gruplanır ve yazı ağının kapısından geçer
        val g = engine.group(word, word.last())
        assertEquals(word.size, g.size)
        assertTrue(engine.looksLikeText(g))
        // Çok aşağıdaki başka bir sözcük bu satıra katılmaz
        val other = V38Ink.word("o", "kitap", 3802, 100f, 900f)
        assertEquals(word.map { it.id }, engine.group(word + other, word.last()).map { it.id })
        // Dört kenarla çizilmiş kare yazı değil (kalemle çizilse de)
        val sq = V38Ink.squareSides("q", 100f, 100f, 200f).map { it.copy(brushType = BrushType.NOTE_BALLPOINT) }
        assertFalse(engine.looksLikeText(sq))
    }
}

/**
 * v38 öneri akışı (düzenleyicide): her türün kendi balonu; tek çizgiye öneri yok; iki çizgiye çizgi önerisi ve tek
 * düğmeyle hepsi tek geri alma adımında düzelir; şekil önerisi 4+ yakın elemanda; bütün modlarda (çocuk modunda
 * maskot) çalışır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V38AiUiTest : V37KidsBase() {

    private fun open(kind: CanvasKind, name: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = true, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    /** Ekranda titrek doğru (tuval düğümünün koordinatı). */
    private fun wobbly(a: Offset, b: Offset, seed: Long) {
        val rnd = java.util.Random(seed)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(a)
            for (i in 1..24) {
                val t = i / 24f
                moveTo(Offset(a.x + (b.x - a.x) * t + (rnd.nextFloat() - 0.5f) * 3f, a.y + (b.y - a.y) * t + (rnd.nextFloat() - 0.5f) * 3f))
            }
            up()
        }
        compose.waitForIdle()
    }

    private fun strokes() = vm().canvas.value!!.strokes

    private fun settle() {
        advanceMain(900)
        tick(600)
        advanceMain(300)
    }

    private fun noSuggestion() =
        vm().lineSuggestion.value == null && vm().shapeSuggestion.value == null && vm().textSuggestion.value == null

    @Test
    fun twoLinesGetOneButtonLineFixInOneUndoStep() {
        open(CanvasKind.ART, "Çizgi")
        val size = canvasSize()
        val x0 = size.x * 0.2f; val y0 = size.y * 0.3f; val w = size.x * 0.5f
        wobbly(Offset(x0, y0), Offset(x0 + w, y0), 1)
        waitFor { strokes().size == 1 }
        settle()
        // Tek çizgi: hiçbir tür önermez
        assertTrue(noSuggestion())
        assertFalse(exists("line_suggest_chip") || exists("shape_suggest_chip") || exists("text_suggest_chip"))
        // İkinci çizgi ilkinin ucunda: çizgi önerisi (ikisi birden); şekil önerisi yok (4'ten az)
        wobbly(Offset(x0 + w + 6f, y0 + 8f), Offset(x0 + w + 2f, y0 + w), 2)
        waitFor { strokes().size == 2 }
        settle()
        waitFor(15_000) { vm().lineSuggestion.value != null }
        val before = strokes()
        assertEquals(before.map { it.id }.toSet(), vm().lineSuggestion.value!!.strokeIds)
        assertNull(vm().shapeSuggestion.value)
        waitFor { exists("line_suggest_chip") }
        compose.onNodeWithTag("line_fix_option", useUnmergedTree = true).performClick()
        waitFor { vm().lineSuggestion.value == null && strokes().zip(before).all { (a, b) -> a.points != b.points } }
        // Tek geri alma: iki çizgi birden eski haline
        compose.runOnIdle { vm().undo() }
        waitFor { strokes().map { it.points } == before.map { it.points } }
    }

    @Test
    fun shapeNeedsFourCloseElementsKindsAreIndependent() {
        open(CanvasKind.NOTES, "Şekil")
        val size = canvasSize()
        val x = size.x * 0.25f; val y = size.y * 0.3f; val s = size.x * 0.4f
        wobbly(Offset(x, y), Offset(x + s - 6f, y + 3f), 1)
        wobbly(Offset(x + s, y + 4f), Offset(x + s - 3f, y + s - 6f), 2)
        wobbly(Offset(x + s - 4f, y + s), Offset(x + 6f, y + s - 3f), 3)
        waitFor { strokes().size == 3 }
        settle()
        assertNull("3 eleman: şekil önerisi yok", vm().shapeSuggestion.value)
        wobbly(Offset(x + 2f, y + s - 6f), Offset(x - 2f, y + 6f), 4)
        waitFor { strokes().size == 4 }
        settle()
        waitFor(15_000) { vm().shapeSuggestion.value != null }
        val ids = strokes().map { it.id }.toSet()
        assertEquals(ids, vm().shapeSuggestion.value!!.strokeIds)
        // Türler ayrı: çizgi önerisi de kendi balonunda (aynı anda)
        waitFor { exists("shape_suggest_chip") }
        assertNotNull(vm().lineSuggestion.value)
        assertTrue(exists("line_suggest_chip"))
        assertNull("kare yazı değil", vm().textSuggestion.value)
        val before = strokes()
        compose.runOnIdle { vm().acceptTemplate(0) }
        waitFor { strokes().none { it.id in ids } }
        // Şekil uygulanınca çizgi önerisinin çizgileri gitti: o da kalkar
        waitFor { vm().lineSuggestion.value == null }
        compose.runOnIdle { vm().undo() }
        waitFor { strokes().map { it.id } == before.map { it.id } }
    }

    @Test
    fun kidsMascotNeedsTwoStrokes() {
        open(CanvasKind.KIDS, "Çocuk")
        val size = canvasSize()
        val r = size.x * 0.1f
        wobblyCircleAt(Offset(size.x * 0.3f, size.y * 0.3f), r, 3)
        waitFor { strokes().size == 1 }
        settle()
        assertTrue(noSuggestion())
        assertFalse(exists("kids_mascot_suggest"))
        wobblyCircleAt(Offset(size.x * 0.3f + r * 2.3f, size.y * 0.3f), r, 5)
        waitFor { strokes().size == 2 }
        settle()
        waitFor(15_000) { exists("kids_mascot_suggest") }
        assertNotNull(vm().lineSuggestion.value)
        // Büyüklerin balonu yok; maskot uygular (iki çizgi birden)
        assertFalse(exists("line_suggest_chip"))
        val before = strokes()
        compose.onNodeWithTag("mascot_apply").performClick()
        waitFor { strokes().zip(before).all { (a, b) -> a.points != b.points } }
    }

    @Test
    fun everyModeSuggestsLinesForTwoStrokesOnly() {
        for ((i, kind) in listOf(CanvasKind.COMIC, CanvasKind.SIMPLE_ART, CanvasKind.SIMPLE_NOTES).withIndex()) {
            if (i > 0) {
                compose.onNodeWithContentDescription("Geri").performClick()
                waitFor { !exists("drawing_canvas") }
            }
            val name = "Mod$i"
            if (i == 0) waitForText("Henüz bir tuvaliniz yok", substring = true)
            runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = true, kind = kind) }
            waitForText(name)
            compose.onAllNodesWithText(name)[0].performClick()
            waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
            val size = canvasSize()
            val x0 = size.x * 0.2f; val y0 = size.y * 0.3f; val w = size.x * 0.4f
            wobbly(Offset(x0, y0), Offset(x0 + w, y0), 1)
            waitFor { strokes().size == 1 }
            settle()
            assertTrue("$kind tek çizgi", noSuggestion())
            wobbly(Offset(x0 + w + 5f, y0 + 6f), Offset(x0 + w, y0 + w), 2)
            waitFor { strokes().size == 2 }
            settle()
            waitFor(15_000) { vm().lineSuggestion.value != null }
            assertEquals("$kind", 2, vm().lineSuggestion.value!!.strokeIds.size)
        }
    }
}
