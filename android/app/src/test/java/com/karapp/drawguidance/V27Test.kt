package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.GuideKind
import com.karapp.drawguidance.ui.drawing.BalloonRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.state.ShapeSuggester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/** v27 testlerinde ortak: önerileri açıp kapatma, öneri balonunun beklenmesi. */
abstract class V27EditorBase : V24EditorBase() {
    protected fun chip(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Tuval noktalarından (sığdırılmış görünüm) ekran çizgisi. */
    protected fun screenLine(vararg xy: Float): List<P> = (xy.indices step 2).flatMap { i ->
        if (i + 2 >= xy.size) listOf(screenOf(xy[i], xy[i + 1]).let { P(it.x, it.y) })
        else {
            // Ara noktalar (parmak hareketi gibi)
            val a = screenOf(xy[i], xy[i + 1]); val b = screenOf(xy[i + 2], xy[i + 3])
            List(8) { k -> val t = k / 8f; P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) }
        }
    }

    protected fun screenCircle(cx: Float, cy: Float, r: Float): List<P> = List(40) { k ->
        val a = Math.PI * 2 * k / 39
        screenOf(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat()).let { P(it.x, it.y) }
    }
}

/**
 * v27 seçimde kendiliğinden öneri: dört ayrı doğruyla çizilen kare seçilince düğmeye basmadan şekil önerisi çıkar ve
 * hazır vektör şekil (Kare/Dikdörtgen) seçeneği vardır; kabul edilince çizgiler tek vektör şekle döner. v38 tek eleman
 * seçilince öneri yok (parça önerisi kalktı); iki çizgi seçilince çizgi önerisi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V27SelectionSuggestTest : V27EditorBase() {

    @Test
    fun manyElementsGetShapeSuggestionWithVectorShape() {
        createCanvasAndOpen("Seçim önerisi")
        prefs().setHandwriting(false)
        val c = canvas()
        val l = c.width * 0.3f; val t = c.height * 0.3f; val s = c.width * 0.3f
        drawStrokes(
            listOf(
                screenLine(l, t, l + s, t), screenLine(l + s, t, l + s, t + s),
                screenLine(l + s, t + s, l, t + s), screenLine(l, t + s, l, t),
            ),
        )
        waitFor { canvas().strokes.size == 4 }
        prefs().setHandwriting(true)
        prefs().setSelectionSuggest(true)
        mode("Seç")
        listOf(screenOf(l + s / 2, t), screenOf(l + s, t + s / 2), screenOf(l + s / 2, t + s), screenOf(l, t + s / 2)).forEach { tap(it) }
        // Düğmeye basmadan şekil önerisi
        waitFor(15_000) { chip("shape_suggest_chip") }
        val vector = hasText("Şekil:", substring = true) and hasAnyAncestor(hasTestTag("shape_suggest_chip"))
        waitFor { compose.onAllNodes(vector).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(vector)[0].performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.size == 1 && canvas().strokes[0].brushType == BrushType.SHAPE }
        val kind = canvas().strokes[0].shape!!.kind
        assertTrue(kind, kind in setOf("SQUARE", "RECTANGLE", "ROUNDED_RECT", "DIAMOND", "PARALLELOGRAM"))
    }

    /**
     * v38 (eski parça önerisinin yerine): tek eleman seçiliyken hiçbir öneri yok; iki çizgi seçilince çizgi önerisi
     * çıkar ve tek düğmeyle iki çizgi birden düzelir (tek geri alma adımı).
     */
    @Test
    fun singleElementGetsNoSuggestionTwoGetLineFix() {
        createCanvasAndOpen("Parça önerisi")
        prefs().setHandwriting(false)
        val c = canvas()
        val cx = c.width * 0.5f; val cy = c.height * 0.45f; val r = c.width * 0.18f
        val wobbly = screenCircle(cx, cy, r).mapIndexed { i, p -> P(p.x + (i % 3 - 1) * 2.5f, p.y + (i % 2) * 2.5f) }
        drawStrokes(listOf(wobbly, screenLine(cx - r, cy + r * 1.3f, cx + r * 0.2f, cy + r * 1.36f, cx + r, cy + r * 1.28f)))
        waitFor { canvas().strokes.size == 2 }
        prefs().setHandwriting(true)
        prefs().setSelectionSuggest(true)
        mode("Seç")
        tap(screenOf(cx + r, cy))
        repeat(15) { Thread.sleep(100); compose.waitForIdle() }
        assertTrue(!chip("handwriting_chip"))
        tap(screenOf(cx + r * 0.2f, cy + r * 1.33f))
        waitFor(15_000) { chip("line_suggest_chip") }
        assertTrue(!chip("shape_suggest_chip") && !chip("text_suggest_chip"))
        val before = canvas().strokes
        compose.onNodeWithTag("line_fix_option", useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.size == 2 && canvas().strokes.zip(before).all { (x, y) -> x.points != y.points } }
        compose.onNode(hasContentDescription("Geri al")).performClick()
        waitFor { canvas().strokes.map { it.points } == before.map { it.points } }
    }

    @Test
    fun turnedOffSelectionSuggestStaysQuiet() {
        createCanvasAndOpen("Kapalı")
        prefs().setHandwriting(false)
        val c = canvas()
        val cx = c.width * 0.5f; val cy = c.height * 0.45f; val r = c.width * 0.18f
        drawStrokes(listOf(screenCircle(cx, cy, r)))
        waitFor { canvas().strokes.size == 1 }
        prefs().setHandwriting(true)
        prefs().setSelectionSuggest(false)
        mode("Seç")
        tap(screenOf(cx + r, cy))
        repeat(10) { Thread.sleep(100); compose.waitForIdle() }
        assertTrue(!chip("line_suggest_chip") && !chip("shape_suggest_chip"))
    }
}

/** v27 kılavuz: not tuvalinin Ekle menüsünde perspektif yok; kılavuz penceresinde mevcutları temizleme düğmesi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V27GuideTest : V27EditorBase() {
    @Test
    fun notesHaveNoPerspectiveAndGuidesCanBeCleared() {
        createNotesCanvasAndOpen("Not kılavuz")
        mode("Ekle")
        compose.waitForIdle()
        val inRow = { d: String -> hasContentDescription(d) and hasAnyAncestor(hasTestTag("tool_row_context")) }
        waitFor { compose.onAllNodes(inRow("Kılavuz çizgileri")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("not tuvalinde perspektif eklenmez", compose.onAllNodes(inRow("Perspektif çizgileri")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun clearButtonRemovesGuideAndPerspective() {
        createCanvasAndOpen("Kılavuz temizle")
        addMenu("Kılavuz çizgileri")
        waitForText("Kılavuz Çizgileri")
        // Henüz kılavuz yokken temizleme düğmesi yok
        assertTrue(compose.onAllNodesWithTag("guide_clear").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Kareli").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().guide.kind == GuideKind.GRID }
        compose.onNodeWithText("Bitti").performSemanticsAction(SemanticsActions.OnClick)
        addMenu("Perspektif çizgileri")
        waitForText("2 Nokta Perspektif")
        compose.onNodeWithText("2 Nokta Perspektif").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().guide.perspective == 2 }
        compose.onNodeWithText("Bitti").performSemanticsAction(SemanticsActions.OnClick)
        addMenu("Kılavuz çizgileri")
        waitFor { compose.onAllNodesWithTag("guide_clear").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("guide_clear").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().guide.kind == GuideKind.NONE && canvas().guide.perspective == 0 }
    }
}

/** v27 çizgi roman tuvali: oluşturma, konuşma balonu ve panel düzeni eklenir. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V27ComicTest : V27EditorBase() {
    @Test
    fun comicCanvasAddsBalloonAndPanels() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNode(hasContentDescription("Yeni Tuval")).performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onAllNodesWithText("Çizgi roman")[0].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasSetTextAction()).performTextInput("Roman")
        compose.onNodeWithText("Oluştur").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(CanvasKind.COMIC, canvas().kind)
        prefs().setSelectionSuggest(false)

        // v37 panel düzenleri Şablonlar'da
        addMenu("Şablonlar")
        waitFor { chip("templates_sheet") }
        compose.onNodeWithTag("tpl_four_grid").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.count { it.brushType == BrushType.SHAPE } == 4 }
        SystemBack.press()
        waitFor { !chip("templates_sheet") }
        // Eski ayrı "Panel düzeni" düğmesi yok
        assertTrue(compose.onAllNodes(hasContentDescription("Panel düzeni")).fetchSemanticsNodes().isEmpty())

        mode("Seç")
        addMenu("Konuşma balonu")
        waitFor { chip("balloon_sheet") }
        compose.onNodeWithTag("balloon_speakers_2").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("balloon_SPEECH").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text?.box?.balloon != null } }
        val balloon = canvas().strokes.first { it.text?.box?.balloon != null }.text!!.box!!.balloon!!
        assertEquals(2, balloon.tails.size)
    }
}

/** v27 mantık: hazır vektör şekillerin dış hatları ve balon yolunun önbelleği. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V27LogicTest {
    @org.junit.After
    fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun vectorOutlinesExistAndMatchHandDrawnRectangle() {
        ShapeSuggester.VECTOR_KINDS.forEach { k ->
            assertTrue(k.name, ShapeSuggester.outline(k.name, androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 100f)).isNotEmpty())
        }
        val items = ShapeSuggester.VECTOR_KINDS.map { k ->
            com.karapp.drawguidance.content.shapes.VectorShapeMatcher.Item(
                k.name, ShapeSuggester.outline(k.name, androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 100f)).map { s -> s.map { P(it.x, it.y) } },
                stretch = k.fit == com.karapp.drawguidance.ui.drawing.ShapeFit.BOX,
            )
        }
        val matcher = com.karapp.drawguidance.content.shapes.VectorShapeMatcher(items)
        // Dört ayrı çizgiyle (2:1) dikdörtgen
        val rect = listOf(
            listOf(P(0f, 0f), P(200f, 3f)), listOf(P(200f, 0f), P(198f, 100f)),
            listOf(P(200f, 100f), P(2f, 98f)), listOf(P(0f, 100f), P(1f, 0f)),
        )
        val top = matcher.match(rect, 3).map { it.id }
        assertTrue(top.toString(), top.firstOrNull() in setOf("RECTANGLE", "ROUNDED_RECT", "PARALLELOGRAM"))
    }

    @Test
    fun balloonOutlineIsCached() {
        val b = com.karapp.drawguidance.data.model.BalloonSpec(tails = BalloonRenderer.defaultTails(2))
        val a = BalloonRenderer.outline(b, 200f, 100f)
        val c = BalloonRenderer.outline(b, 200.1f, 100.1f)
        assertSame(a, c)
        assertNotNull(a.first)
    }
}

/**
 * v27 3B çizim önerisi: 3B düzenleyicide çizgiyle çizilen çember için düğmesiz öneri gelir (küre/silindir/halka gibi
 * cisimler); öneri uygulanınca çizgi önerilen cisimle değişir (tek geri alma adımı); kapatılınca öneri kalkar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V27Model3DSuggestTest {
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.karapp.drawguidance.data.local.AppDatabase::class.java).allowMainThreadQueries().build()

    @org.junit.After
    fun close() {
        db.close()
        org.koin.core.context.stopKoin()
    }

    private fun idle() = repeat(5) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }

    private fun vm(): com.karapp.drawguidance.ui.screens.model3d.Model3DEditorViewModel {
        val tex = com.karapp.drawguidance.data.library.TextureRepository(db.texture3DDao())
        val repo = com.karapp.drawguidance.data.library.Model3DRepository(context, db.model3DDao(), tex)
        val images = com.karapp.drawguidance.data.images.ImagesRepository(
            db.savedImageDao(), com.karapp.drawguidance.data.images.ImageExporter(context, useGallery = false),
            com.karapp.drawguidance.data.images.CanvasAssetStore(db.canvasAssetDao()),
        )
        val vm = com.karapp.drawguidance.ui.screens.model3d.Model3DEditorViewModel(repo, tex, com.karapp.drawguidance.data.library.Model3DResults(), null, null, null, images = images)
        idle()
        vm.setViewport(900, 900)
        return vm
    }

    private fun waitUntil(timeoutMs: Long = 30_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(15)
        }
        org.junit.Assert.fail("koşul sağlanmadı")
    }

    @Test
    fun circleLineGetsSolidSuggestionAndApplies() {
        val vm = vm()
        vm.setMode(com.karapp.drawguidance.ui.screens.model3d.Mode3.DRAW)
        vm.setPlane(com.karapp.drawguidance.content.model3d.DrawPlane.FRONT)
        val plane = vm.drawPlane()!!
        vm.setBrush(vm.brush.value.copy(kind = com.karapp.drawguidance.content.model3d.Brush3Kind.LINE))
        val ring = (0..48).map { val a = it / 48.0 * 2 * Math.PI; Offset(450f + 150f * cos(a).toFloat(), 420f + 150f * sin(a).toFloat()) }
        val before = vm.model.value!!
        vm.finishBrush(V26Draw.path(vm, ring), null, plane.second)
        waitUntil { vm.suggestion.value != null }
        val s = vm.suggestion.value!!
        assertTrue(s.kinds.isNotEmpty())
        vm.applySuggestion(s.kinds.first())
        waitUntil { vm.busy.value == null && vm.model.value!!.parts.size > before.parts.size }
        assertEquals("çizgi cisme dönüştü", before.strokes.size, vm.model.value!!.strokes.size)
        // Kapatma
        vm.finishBrush(V26Draw.path(vm, ring.map { it + Offset(0f, 60f) }), null, plane.second)
        waitUntil { vm.suggestion.value != null }
        vm.dismissSuggestion()
        assertTrue(vm.suggestion.value == null)
    }
}
