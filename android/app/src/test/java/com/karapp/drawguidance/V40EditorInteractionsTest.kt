package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.history.DocumentState
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import androidx.compose.ui.semantics.getOrNull
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.repository.decodeBackground
import com.karapp.drawguidance.data.repository.encodeBackground
import com.karapp.drawguidance.platform.DeviceCaps
import com.karapp.drawguidance.ui.components.SplashTiming
import com.karapp.drawguidance.ui.drawing.BackgroundTransform
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.HoldToMove
import com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleToolOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** v40 (A) açılış canlandırmasının süresi cihazın hızına göre; hız anlaşılamazsa normal. */
class V40SplashTimingTest {
    private val title = "Çizim Rehberi"

    @Test fun unknownSpeedKeepsNormalTiming() {
        assertEquals(SplashTiming.Normal, SplashTiming.select(null, null))
        // Yalnız kare süresi biliniyor ve olağan: hızlandırılmaz
        assertEquals(SplashTiming.Normal, SplashTiming.select(null, 16.7f))
        // Geçersiz ölçümler (takılma, sıfır, NaN) bilinmiyor sayılır
        assertEquals(SplashTiming.Normal, SplashTiming.select(null, 5000f))
        assertEquals(SplashTiming.Normal, SplashTiming.select(null, 0f))
        assertEquals(SplashTiming.Normal, SplashTiming.select(null, Float.NaN))
    }

    @Test fun fasterDevicesGetShorterAnimation() {
        assertEquals(SplashTiming.Fast, SplashTiming.select(DeviceCaps.Tier.HIGH, 16.7f))
        assertEquals(SplashTiming.Fast, SplashTiming.select(DeviceCaps.Tier.HIGH, null))
        assertEquals(SplashTiming.Quick, SplashTiming.select(DeviceCaps.Tier.MID, 16.7f))
        val fast = SplashTiming.Fast.totalMs(title)
        val quick = SplashTiming.Quick.totalMs(title)
        val normal = SplashTiming.Normal.totalMs(title)
        assertTrue("hızlı < orta < normal: $fast $quick $normal", fast < quick && quick < normal)
        // Normal ~2,2 sn (v31'deki süreler)
        assertTrue("normal: $normal", normal in 2000L..2400L)
        assertTrue("hızlı: $fast", fast <= 1100L)
    }

    @Test fun slowDevicesGetLighterQuickerAnimation() {
        assertEquals(SplashTiming.Light, SplashTiming.select(DeviceCaps.Tier.LOW, null))
        assertEquals(SplashTiming.Light, SplashTiming.select(DeviceCaps.Tier.LOW, 16.7f))
        // Orta cihaz ya da bilinmeyen cihazda ilk kareler çok yavaşsa
        assertEquals(SplashTiming.Light, SplashTiming.select(DeviceCaps.Tier.MID, 60f))
        assertEquals(SplashTiming.Light, SplashTiming.select(null, 60f))
        // Güçlü cihaz biraz yavaş ilk karelerde hızlı kalır, çok yavaşsa hafife düşer
        assertEquals(SplashTiming.Fast, SplashTiming.select(DeviceCaps.Tier.HIGH, 45f))
        assertEquals(SplashTiming.Light, SplashTiming.select(DeviceCaps.Tier.HIGH, 70f))
        val light = SplashTiming.Light
        assertTrue(light.light && light.chunk > 1)
        assertTrue("hafif normalden kısa", light.totalMs(title) < SplashTiming.Normal.totalMs(title))
        // Gruplarla yazınca daha az adım (kare)
        assertEquals(5, (title.length + light.chunk - 1) / light.chunk)
    }

    @Test fun averageFrameTime() {
        assertNull(SplashTiming.averageFrameMs(emptyList()))
        assertNull(SplashTiming.averageFrameMs(listOf(5L)))
        assertEquals(16f, SplashTiming.averageFrameMs(listOf(0L, 16_000_000L, 32_000_000L, 48_000_000L))!!, 0.01f)
    }
}

/** v40 (D) arka plan resminin taşınma ve boyutlandırma hesabı; eski kayıtlarla uyum. */
class V40BackgroundTransformMathTest {
    private val spec = BackgroundSpec(imageId = "asset:a")

    @Test fun moveAndPinchAndCorner() {
        val moved = BackgroundTransform.moved(spec, 1000f, 500f, Offset(100f, -50f))
        assertEquals(0.1f, moved.imageOffsetX, 1e-4f)
        assertEquals(-0.1f, moved.imageOffsetY, 1e-4f)
        assertEquals(1f, moved.imageScale, 0f)
        // İki parmak: merkez çevresinde yarıya
        val half = BackgroundTransform.pinch(spec, 1000f, 500f, Offset.Zero, 0.5f, Offset(500f, 250f))
        assertEquals(0.5f, half.imageScale, 1e-4f)
        assertEquals(Offset(250f, 125f), BackgroundTransform.frame(half, 1000f, 500f).topLeft)
        // Sol üst köşede yakınlaştırma: köşe yerinde kalır
        val corner = BackgroundTransform.pinch(spec, 1000f, 500f, Offset.Zero, 0.5f, Offset.Zero)
        assertEquals(Offset.Zero, BackgroundTransform.frame(corner, 1000f, 500f).topLeft)
        // Sağ alt köşe tutamağı ortaya: sol üst köşe yerinde, yarı boy
        val resized = BackgroundTransform.corner(spec, 1000f, 500f, BackgroundTransform.BOTTOM_RIGHT, Offset(500f, 250f))
        assertEquals(0.5f, resized.imageScale, 1e-3f)
        val f = BackgroundTransform.frame(resized, 1000f, 500f)
        assertEquals(0f, f.left, 0.5f); assertEquals(0f, f.top, 0.5f)
        // Sınırlar
        assertEquals(BackgroundTransform.MAX_SCALE, BackgroundTransform.pinch(spec, 1000f, 500f, Offset.Zero, 100f, Offset(500f, 250f)).imageScale, 0f)
        assertEquals(BackgroundTransform.MIN_SCALE, BackgroundTransform.pinch(spec, 1000f, 500f, Offset.Zero, 0.001f, Offset(500f, 250f)).imageScale, 0f)
        // Çok uzağa sürüklenen resim kağıttan tamamen kaçmaz
        assertTrue(BackgroundTransform.moved(spec, 1000f, 500f, Offset(99999f, 0f)).imageOffsetX < 1f)
        assertEquals(spec, BackgroundTransform.reset(resized))
    }

    @Test fun storedTransformIsBackwardCompatible() {
        // Eski kayıt (alanlar yok): ortada, ölçek 1
        val old = decodeBackground("""{"imageId":"asset:a","imageOpacity":0.5}""")
        assertFalse(old.hasImageTransform)
        assertEquals(1f, old.imageScale, 0f)
        // Varsayılanlar yazılmaz (eski sürümler okumaya devam eder), değişenler yazılır ve okunur
        assertFalse(encodeBackground(spec).contains("imageScale"))
        val moved = spec.copy(imageOffsetX = 0.2f, imageScale = 0.6f)
        assertEquals(moved, decodeBackground(encodeBackground(moved)))
        // Geri almada yalnız yer ve boy aktarılır
        val other = BackgroundSpec(imageId = "asset:b", patternId = "p").withImageTransformOf(moved)
        assertEquals("asset:b", other.imageId)
        assertEquals(0.6f, other.imageScale, 0f)
        assertNull(DocumentState(emptyList(), 1, 1, "#FFFFFF", 0).sharedBackground)
    }

    @Test fun holdToMoveRules() {
        assertTrue(HoldToMove.eligible(EditMode.DRAW) && HoldToMove.eligible(EditMode.SELECT) && HoldToMove.eligible(EditMode.PAINT))
        assertFalse(HoldToMove.eligible(EditMode.TEXT) || HoldToMove.eligible(EditMode.SHAPE_EDIT) || HoldToMove.eligible(EditMode.CROP))
        // Çocuk boyamasında halka kovanın 2 sn halkasından sonra
        assertEquals(2000L, HoldToMove.ringDelay(EditMode.PAINT, CanvasKind.KIDS))
        assertEquals(HoldToMove.RING_DELAY_MS, HoldToMove.ringDelay(EditMode.PAINT, CanvasKind.SIMPLE_ART))
        assertNull(HoldToMove.ringProgress(100, 400))
        assertEquals(0.5f, HoldToMove.ringProgress(2500, 400)!!, 1e-4f)
        // Seç ve Taşı 5. ve 6. (geri al, yinele dahil)
        val order = SimpleToolOrder.placeSelectAndMove(listOf("brush", "eraser", "shape", "shape_edit", "background", "select", "move", "expand")) { it }
        assertEquals(listOf("brush", "eraser", "select", "move", "shape", "shape_edit", "background", "expand"), order)
    }
}

/** v40 (B) her tuval türünde elemana 5 sn basılı tutunca Taşı; eleman parmağı izler. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V40HoldToMoveTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun scale(): Float {
        val view = canvasSize()
        val margin = 24f * compose.density.density
        return minOf((view.x - 2 * margin) / 1000f, (view.y - 2 * margin) / 1000f)
    }

    private fun strokes(): List<Stroke> = vm().canvas.value?.strokes.orEmpty()

    /** [point]'te [millis] ms basılı tutar; halka görüldü mü döner. Sonra [delta] kadar sürükleyip bırakır. */
    private fun holdThenDrag(point: Offset, millis: Long, delta: Offset): Boolean {
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput { down(point) }
        compose.mainClock.advanceTimeBy(1000)
        val ring = exists("hold_move_ring")
        compose.mainClock.advanceTimeBy(millis - 1000)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            repeat(6) { moveBy(delta / 6f) }
            up()
        }
        compose.waitForIdle()
        return ring
    }

    private fun holdMovesElement(kind: CanvasKind) {
        open("Basılı ${kind.name}", kind)
        if (vm().mode.value != EditMode.DRAW) compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        drag(screenOf(300f, 500f), Offset(400f * scale(), 0f))
        waitFor { strokes().size == 1 }
        val before = strokes().single()
        val delta = Offset(120f, 60f)
        val ring = holdThenDrag(screenOf(500f, 500f), 5300, delta)
        assertTrue("$kind: halka görünür", ring)
        waitFor { vm().mode.value == EditMode.MOVE }
        assertEquals("$kind: yalnız o eleman seçili", setOf(before.id), vm().selectedIds.value)
        waitFor { strokes().single().points.first().x != before.points.first().x }
        val after = strokes().single()
        assertEquals("$kind: yeni çizim eklenmez", 1, strokes().size)
        val dx = after.points.first().x - before.points.first().x
        val dy = after.points.first().y - before.points.first().y
        assertEquals("$kind: x", delta.x / scale(), dx, delta.x / scale() * 0.25f)
        assertEquals("$kind: y", delta.y / scale(), dy, delta.y / scale() * 0.25f)
        assertFalse(exists("hold_move_ring"))
        // Sonra Taşı'da sürükleyerek de taşınır
        drag(screenOf(500f + dx, 500f + dy), Offset(-60f, 0f))
        waitFor { abs(strokes().single().points.first().x - after.points.first().x) > 1f }
    }

    @Test fun art() = holdMovesElement(CanvasKind.ART)
    @Test fun notes() = holdMovesElement(CanvasKind.NOTES)
    @Test fun comic() = holdMovesElement(CanvasKind.COMIC)
    @Test fun simpleArt() = holdMovesElement(CanvasKind.SIMPLE_ART)
    @Test fun simpleNotes() = holdMovesElement(CanvasKind.SIMPLE_NOTES)
    @Test fun kids() = holdMovesElement(CanvasKind.KIDS)

    @Test
    fun shortHoldStillSelectsAndEmptyHoldDoesNothing() {
        open("Kısa basma", CanvasKind.ART)
        drag(screenOf(300f, 500f), Offset(400f * scale(), 0f))
        waitFor { strokes().size == 1 }
        // 2 sn: seçilir ve Seç'e geçilir (Taşı'ya geçilmez)
        longPress(screenOf(500f, 500f))
        waitFor { vm().mode.value == EditMode.SELECT && vm().selectedIds.value.isNotEmpty() }
        compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        // Boş yerde 5 sn: Taşı'ya geçilmez (çizim modunda nokta çizilir)
        holdThenDrag(screenOf(500f, 150f), 5300, Offset.Zero)
        waitFor { strokes().size == 2 }
        assertTrue(vm().mode.value != EditMode.MOVE)
    }

    @Test
    fun kidsBucketHoldStillPaintsWholeShapeFirst() {
        open("Kova", CanvasKind.KIDS)
        drag(screenOf(300f, 500f), Offset(400f * scale(), 0f))
        waitFor { strokes().size == 1 }
        compose.runOnIdle { vm().chooseMode(EditMode.PAINT) }
        waitFor { vm().mode.value == EditMode.PAINT }
        // 1 sn: halka henüz yok (kovanın 2 sn halkası sürer)
        compose.waitForIdle()
        // Kovanın 2 sn halkası bir canlandırma: saat kendiliğinden ilerlemesin (yoksa beklerken 2 sn geçer)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drawing_canvas").performTouchInput { down(screenOf(500f, 500f)) }
        compose.mainClock.advanceTimeBy(1000)
        assertFalse("1 sn: ${vm().mode.value} ${vm().canvas.value?.kind}", exists("hold_move_ring"))
        compose.mainClock.advanceTimeBy(1500)
        assertTrue("2 sn'den sonra halka", exists("hold_move_ring"))
        assertEquals(EditMode.PAINT, vm().mode.value)
        compose.mainClock.advanceTimeBy(2800)
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        waitFor { vm().mode.value == EditMode.MOVE }
    }
}

/**
 * v40 (C) basit resimde Seç'in yanında Taşı, ikisi 5. ve 6. düğme; (E) basit modlarda yazı kutusu boyutu; (D) Arka plan
 * seçiliyken resmi taşıma ve boyutlandırma (geri alınabilir, kaydedilir).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V40SimpleEditorTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind, background: BackgroundSpec = BackgroundSpec()) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind, background = background) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun scale(): Float {
        val view = canvasSize()
        val margin = 24f * compose.density.density
        return minOf((view.x - 2 * margin) / 1000f, (view.y - 2 * margin) / 1000f)
    }

    private fun rowOrder(): List<String> {
        val ids = listOf("undo", "redo", "brush", "eraser", "shape", "shape_edit", "background", "image", "text", "model3d", "paint", "elements", "select", "move", "expand")
        return ids.filter { exists("simple_tool_$it") }
            // v46 dikeyde menü iki satır alt alta: okuma sırası önce satır, sonra soldan sağa
            .sortedWith(compareBy({ (compose.onNodeWithTag("simple_tool_$it").fetchSemanticsNode().positionInRoot.y / 40f).toInt() }, { compose.onNodeWithTag("simple_tool_$it").fetchSemanticsNode().positionInRoot.x }))
    }

    @Test
    fun simpleArtHasMoveNextToSelectAtFifthAndSixth() {
        // v48 dikey ekranda üç menü: Seç ve Taşı üçüncü menünün (seç ve düzenle) başında, yan yana
        open("Basit menü", CanvasKind.SIMPLE_ART)
        waitFor { exists("simple_tool_move") }
        val edit = compose.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(hasTestTag("simple_menu_edit")), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) }.filter { it.startsWith("simple_tool_") }.map { it.removePrefix("simple_tool_") }.distinct()
        assertEquals(edit.toString(), listOf("select", "move"), edit.take(2))
        // Taşı yalnız bir eleman seçiliyken
        compose.runOnIdle { vm().onStrokeFinished(listOf(StrokePoint(300f, 300f), StrokePoint(500f, 320f))) }
        waitFor { vm().canvas.value!!.strokes.size == 1 }
        compose.runOnIdle { vm().tutorialHost.select(setOf(vm().canvas.value!!.strokes.single().id)) }
        compose.onNodeWithTag("simple_tool_move").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.MOVE }
        waitFor { compose.onAllNodes(hasTestTag("simple_tool_move") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasTestTag("simple_tool_select") and isSelected()).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("simple_tool_select").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.SELECT }
    }

    @Test
    fun simpleNotesKeepsItsRow() {
        open("Basit not", CanvasKind.SIMPLE_NOTES)
        waitFor { exists("simple_tool_select") }
        assertFalse(exists("simple_tool_move"))
    }

    @Test
    fun simpleTextBoxResizesWithHandles() {
        open("Yazı kutusu", CanvasKind.SIMPLE_ART)
        compose.runOnIdle { vm().startAction(EditMode.TEXT); vm().beginTextAt(200f, 300f, 0f) }
        waitFor { exists("inline_text_editor") }
        field().performTextInput("Merhaba dünya, bu bir yazı")
        finishText()
        waitFor { vm().canvas.value?.strokes.orEmpty().any { it.brushType == BrushType.TEXT } }
        fun text() = vm().canvas.value!!.strokes.single { it.brushType == BrushType.TEXT }
        val t0 = text()
        compose.runOnIdle { vm().selectOnlyAt((t0.points[0].x + t0.points[2].x) / 2f, (t0.points[0].y + t0.points[2].y) / 2f, 10f) }
        waitFor { vm().mode.value == EditMode.SELECT && vm().selectedIds.value == setOf(t0.id) }
        // Seç'te sağ kenar tutamağından sağa: kutu genişler
        val q = t0.points
        val handle = screenOf((q[1].x + q[2].x) / 2f, (q[1].y + q[2].y) / 2f) + Offset(6f * compose.density.density, 0f)
        val w0 = q[1].x - q[0].x
        drag(handle, Offset(-w0 * 0.4f * scale(), 0f))
        waitFor { text().text?.box?.width != null }
        val w1 = text().points[1].x - text().points[0].x
        assertTrue("dar kutu: $w0 → $w1", w1 < w0 * 0.8f)
        // Taşı'da da tutamak boyutlandırır; kutunun içinden sürüklemek taşır
        compose.runOnIdle { vm().chooseMode(EditMode.MOVE) }
        waitFor { vm().mode.value == EditMode.MOVE }
        val t1 = text()
        val c1 = t1.points
        val corner = screenOf(c1[2].x, c1[2].y) + Offset(6f, 6f) * compose.density.density
        drag(corner, Offset(80f * scale(), 40f * scale()))
        waitFor { text().points[2].x > c1[2].x + 20f }
        assertEquals("sol üst köşe yerinde", c1[0].x, text().points[0].x, 1f)
        val t2 = text()
        val inside = screenOf((t2.points[0].x + t2.points[2].x) / 2f, (t2.points[0].y + t2.points[2].y) / 2f)
        drag(inside, Offset(0f, 100f * scale()))
        waitFor { text().points[0].y > t2.points[0].y + 50f }
        assertEquals("boyut değişmez", t2.points[1].x - t2.points[0].x, text().points[1].x - text().points[0].x, 1f)
    }

    private fun dragOverlay(from: Offset, delta: Offset) {
        compose.waitForIdle()
        compose.onNodeWithTag("bg_transform_overlay").performTouchInput {
            down(from)
            repeat(10) { moveBy(delta / 10f) }
            up()
        }
        compose.waitForIdle()
    }

    private fun backgroundMovesAndUndoes(kind: CanvasKind, openSheet: () -> Unit) {
        open("Arka plan ${kind.name}", kind, BackgroundSpec(imageId = "asset:none"))
        openSheet()
        waitFor { exists("bg_transform_open") }
        compose.onNodeWithTag("bg_transform_open").performScrollTo().performClick()
        waitFor { exists("bg_transform_overlay") && !exists("background_sheet") }
        // Sürükle: resim kayar, kaydedilir
        dragOverlay(screenOf(500f, 500f), Offset(100f * scale(), 50f * scale()))
        waitFor { vm().canvas.value!!.background.imageOffsetX > 0.05f }
        assertEquals(0.1f, vm().canvas.value!!.background.imageOffsetX, 0.02f)
        assertEquals(0.05f, vm().canvas.value!!.background.imageOffsetY, 0.02f)
        waitFor { canvas().background.imageOffsetX > 0.05f }
        // Köşeden boyutlandır (sol üst köşe ortaya doğru: küçülür; sağ alt köşe ekran dışında)
        val bg = vm().canvas.value!!.background
        val tl = BackgroundTransform.frame(bg, 1000f, 1000f).topLeft
        dragOverlay(screenOf(tl.x, tl.y), Offset(300f * scale(), 300f * scale()))
        waitFor { vm().canvas.value!!.background.imageScale < 0.9f }
        // Geri al: önce boyut, sonra yer
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value!!.background.imageScale == 1f }
        assertTrue(vm().canvas.value!!.background.imageOffsetX > 0.05f)
        compose.runOnIdle { vm().undo() }
        waitFor { !vm().canvas.value!!.background.hasImageTransform }
        waitFor { !canvas().background.hasImageTransform }
        compose.runOnIdle { vm().redo() }
        waitFor { vm().canvas.value!!.background.imageOffsetX > 0.05f }
        assertEquals("asset:none", vm().canvas.value!!.background.imageId)
        // Sıfırla ve Bitti (v45 seçim menüsünün Düzenle listesinde)
        compose.onNodeWithTag("bg_selection_menu_edit").performClick()
        waitFor { exists("bg_transform_reset") }
        compose.onNodeWithTag("bg_transform_reset").performClick()
        waitFor { !vm().canvas.value!!.background.hasImageTransform }
        // v48 basit modun dikeyde üç menüsü kağıdı aşağı iter: menü üst kenara yakın, dokunuş eylemle yapılır
        compose.onNodeWithTag("bg_selection_menu_edit").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { exists("bg_transform_done") }
        compose.onNodeWithTag("bg_transform_done").performClick()
        waitFor { !exists("bg_transform_overlay") }
    }

    @Test
    fun simpleArtBackgroundMovesAndResizes() = backgroundMovesAndUndoes(CanvasKind.SIMPLE_ART) {
        compose.onNodeWithTag("simple_tool_background").performScrollTo().performClick()
        waitFor { exists("background_sheet") }
    }

    @Test
    fun artBackgroundMovesAndResizes() = backgroundMovesAndUndoes(CanvasKind.ART) {
        val node = androidx.compose.ui.test.hasContentDescription("Arka plan") and androidx.compose.ui.test.hasClickAction() and
            (androidx.compose.ui.test.hasAnyAncestor(hasTestTag("tool_row_context")) or androidx.compose.ui.test.hasAnyAncestor(hasTestTag("tool_row_draw")))
        if (compose.onAllNodes(node).fetchSemanticsNodes().isEmpty()) mode("Çiz")
        waitFor { compose.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
        runCatching { compose.onNode(node).performScrollTo() }
        compose.onNode(node).performClick()
        waitFor { exists("background_sheet") }
    }

    @Test
    fun notesHaveNoBackgroundTransform() {
        open("Not arka plan", CanvasKind.NOTES, BackgroundSpec(imageId = "asset:none"))
        assertFalse(vm().backgroundTransform.available())
    }
}
