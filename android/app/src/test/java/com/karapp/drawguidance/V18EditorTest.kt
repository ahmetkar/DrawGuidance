package com.karapp.drawguidance

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.platform.Stylus
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * v18 düzenleyici: moda göre sadeleşen üst menü (Çiz'de + ve Kırp yok; Seç'te kopyala/sil; Düzenle'de Döndür, Ölçekle,
 * Kırp; Taşı'da konum ve kaydırma; Genişlet'te boyut ve kenarlar), komut kutusunun kalkması, bağlam menüsünde
 * "Boşlukları doldur", grafik deseninin formüle yansıması ve tablet kalemi.
 */
abstract class V18EditorBase : V17EditorBase() {

    protected fun noTopTool(label: String) = !hasTopTool(label)

    protected fun drawRow(label: String) =
        compose.onAllNodes(hasContentDescription(label) and hasAnyAncestor(hasTestTag("tool_row_draw"))).fetchSemanticsNodes().isNotEmpty()

    protected fun selectionOrigin(): Offset {
        val c = canvas()
        return ElementOps.selectionBounds(c.strokes, c.strokes.mapTo(HashSet()) { it.id })!!.topLeft
    }

    /** İlk çizimi seçer (Seç modunda çizgiye dokunarak). */
    protected fun drawAndSelect(): Pair<Offset, Mapping> {
        val size = canvasSize()
        val start = Offset(size.x * 0.2f, size.y * 0.5f)
        drag(start, Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val map = mappingFrom(start, size.x * 0.5f)
        mode("Seç")
        tap(start + Offset(size.x * 0.25f, 0f))
        waitForText("1 eleman seçili")
        return start to map
    }

    // --- Kalem olayları (Compose test girdisi kalem türü üretemez: ham MotionEvent pencereye verilir) ---

    private fun canvasOrigin(): Offset = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().positionInWindow

    protected fun penEvent(
        action: Int, down: Long, time: Long, at: Offset, pressure: Float, tilt: Float = 0f,
        tool: Int = MotionEvent.TOOL_TYPE_STYLUS, buttons: Int = 0,
    ): MotionEvent {
        val origin = canvasOrigin()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = origin.x + at.x; y = origin.y + at.y; this.pressure = pressure; size = 0.05f
            setAxisValue(MotionEvent.AXIS_TILT, tilt)
        })
        return MotionEvent.obtain(down, time, action, 1, props, coords, 0, buttons, 1f, 1f, 0, 0,
            InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    protected fun dispatch(event: MotionEvent) {
        compose.runOnUiThread { compose.activity.window.decorView.dispatchTouchEvent(event) }
        event.recycle()
        compose.waitForIdle()
    }

    /** Kalemle [points] boyunca çizer (tuval düğümüne göre ekran koordinatı). [cancel]: sistem iptal eder (avuç). */
    protected fun penStroke(
        points: List<Offset>, pressures: List<Float>, tilt: Float = 0f,
        tool: Int = MotionEvent.TOOL_TYPE_STYLUS, buttons: Int = 0, cancel: Boolean = false,
    ) {
        compose.waitForIdle()
        val t0 = SystemClock.uptimeMillis()
        dispatch(penEvent(MotionEvent.ACTION_DOWN, t0, t0, points[0], pressures[0], tilt, tool, buttons))
        for (i in 1 until points.size) dispatch(penEvent(MotionEvent.ACTION_MOVE, t0, t0 + i * 8L, points[i], pressures[i], tilt, tool, buttons))
        val last = points.size
        dispatch(penEvent(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, t0, t0 + last * 8L, points.last(), pressures.last(), tilt, tool, buttons))
    }

    protected fun line(from: Offset, to: Offset, n: Int = 24) = List(n + 1) { i -> from + (to - from) * (i / n.toFloat()) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V18TopRowTest : V18EditorBase() {
    @Test
    fun topRowDependsOnModeAndCommandBarIsGone() {
        createCanvasAndOpen("Menüler")
        // Komut kutusu yok
        assertTrue(compose.onAllNodesWithTag("command_input").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Komut yaz", substring = true).fetchSemanticsNodes().isEmpty())
        // Çiz: fırça, silgi, boya kutusu; + menüsü ve Kırp yok. v47 Elemanlar ve Özellikler mod menüsünün son grubunda
        listOf("Fırça Seç", "Silgi", "Boya kutusu").forEach { assertTrue(it, drawRow(it)) }
        listOf("Elemanlar", "Özellikler").forEach { assertFalse(it, drawRow(it)) }
        assertFalse(drawRow("Kırp"))
        assertTrue(compose.onAllNodesWithContentDescription("Ekle menüsü").fetchSemanticsNodes().isEmpty())

        drawAndSelect()
        // Seç: kopyala, sil; kırp, boya, döndür, ölçekle yok (v47 Elemanlar mod menüsünde)
        listOf("Kopyala", "Sil").forEach { assertTrue(it, hasTopTool(it)) }
        listOf("Kırp", "Boya kutusu", "Döndür", "Ölçekle").forEach { assertTrue(it, noTopTool(it)) }

        // Düzenle: döndür, ölçekle, kırp (seçim korunur)
        mode("Düzenle")
        listOf("Döndür", "Ölçekle", "Kırp").forEach { assertTrue(it, hasTopTool(it)) }
        waitForText("Düzenlemek istediğin elemana dokun")
        topTool("Döndür").performSemanticsAction(SemanticsActions.OnClick)
        modeBarTitle("Döndür")
        // Döndür, Düzenle modunun aracı: bitince Düzenle'ye dönülür
        compose.onNode(hasText("+90°") and hasAnyAncestor(hasTestTag("mode_bar"))).performClick()
        waitFor { ElementOps.bounds(canvas().strokes.single())!!.let { it.height > it.width } }
        finish()
        waitForText("Düzenlemek istediğin elemana dokun")
        assertTrue(hasTopTool("Döndür"))

        // Ekle modunda eklenecekler üst menüde
        mode("Ekle")
        listOf("Şekil ekle", "Yazı ekle", "Resim ekle").forEach { assertTrue(it, hasTopTool(it)) }

        // Arası küçük boşluklu iki çizgi seçiliyken bağlam menüsünde "Boşlukları doldur" (eski komut kutusundaki)
        mode("Çiz")
        val size = canvasSize()
        val y = size.y * 0.8f
        drag(Offset(size.x * 0.1f, y), Offset(size.x * 0.3f, 0f))
        drag(Offset(size.x * 0.44f, y), Offset(size.x * 0.3f, 0f))
        waitFor { canvas().strokes.size == 3 }
        mode("Seç")
        tap(Offset(size.x * 0.25f, y))
        waitForText("1 eleman seçili")
        tap(Offset(size.x * 0.6f, y))
        waitForText("2 eleman seçili")
        twoFingerHold(Offset(size.x * 0.5f, size.y * 0.3f))
        waitFor { compose.onAllNodes(hasText("Boşlukları doldur") and inContextMenu()).fetchSemanticsNodes().isNotEmpty() }
        menuItem("Boşlukları doldur").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.size == 4 }
        waitForText("boşluk dolduruldu", substring = true)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V18MoveTest : V18EditorBase() {
    @Test
    fun moveRowShowsPositionAndNudges() {
        createCanvasAndOpen("Taşıma")
        drawAndSelect()
        mode("Taşı")
        // Yalnızca taşımayla ilgili: konum, ok düğmeleri, ortala, adım
        listOf("Sola kaydır", "Sağa kaydır", "Yukarı kaydır", "Aşağı kaydır", "Sayfada ortala").forEach { assertTrue(it, hasTopTool(it)) }
        listOf("Kopyala", "Sil", "Kırp", "Boya kutusu", "Döndür", "Ölçekle").forEach { assertTrue(it, noTopTool(it)) }
        val o0 = selectionOrigin()
        waitForText("X ${o0.x.roundToInt()} · Y ${o0.y.roundToInt()}")

        topTool("Sağa kaydır").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { abs(selectionOrigin().x - (o0.x + 1f)) < 0.05f }
        compose.onNodeWithTag("move_step_chip").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Adım: 10 px")
        topTool("Aşağı kaydır").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { abs(selectionOrigin().y - (o0.y + 10f)) < 0.05f }
        val o1 = selectionOrigin()
        waitForText("X ${o1.x.roundToInt()} · Y ${o1.y.roundToInt()}")

        // Konuma dokun: değer girilir
        compose.onNodeWithTag("move_position_chip").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("position_sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("position_x").performTextClearance()
        compose.onNodeWithTag("position_x").performTextInput("120")
        compose.onNodeWithTag("position_y").performTextClearance()
        compose.onNodeWithTag("position_y").performTextInput("80")
        compose.onNode(hasText("Uygula") and hasAnyAncestor(hasTestTag("position_sheet"))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { (selectionOrigin() - Offset(120f, 80f)).getDistance() < 0.1f }
        waitForText("X 120 · Y 80")

        // Sayfada ortala
        topTool("Sayfada ortala").performSemanticsAction(SemanticsActions.OnClick)
        waitFor {
            val c = canvas()
            val b = ElementOps.bounds(c.strokes.single())!!
            (b.center - Offset(c.width / 2f, c.height / 2f)).getDistance() < 0.5f
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V18ExpandTest : V18EditorBase() {
    @Test
    fun expandRowShowsSizeAndGrowsSides() {
        createCanvasAndOpen("Genişletme")
        val size = canvasSize()
        drag(Offset(size.x * 0.3f, size.y * 0.5f), Offset(size.x * 0.3f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val c0 = canvas()
        mode("Genişlet")
        listOf("Soldan genişlet", "Sağdan genişlet", "Üstten genişlet", "Alttan genişlet", "Sığdır").forEach { assertTrue(it, hasTopTool(it)) }
        listOf("Kırp", "Boya kutusu", "Kopyala").forEach { assertTrue(it, noTopTool(it)) }
        waitForText("${c0.width} × ${c0.height} px")

        topTool("Sağdan genişlet").performSemanticsAction(SemanticsActions.OnClick)
        val step = ((c0.width * 0.1f / 10f).roundToInt() * 10)
        waitFor { canvas().width == c0.width + step }
        waitForText("${c0.width + step} × ${c0.height} px")

        // Soldan genişletince çizimler sağa kayar (kağıttaki yerleri aynı kalır); adım yeni genişliğin %10'u
        val x0 = canvas().strokes.single().points.first().x
        val w1 = canvas().width
        val step2 = ((w1 * 0.1f / 10f).roundToInt() * 10)
        topTool("Soldan genişlet").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().width == w1 + step2 }
        assertEquals(x0 + step2, canvas().strokes.single().points.first().x, 0.05f)

        // Boyut girme: ortası sabit
        compose.onNodeWithTag("canvas_size_chip").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("canvas_size_sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("canvas_size_width").performTextClearance()
        compose.onNodeWithTag("canvas_size_width").performTextInput("900")
        compose.onNodeWithTag("canvas_size_height").performTextClearance()
        compose.onNodeWithTag("canvas_size_height").performTextInput("700")
        compose.onNodeWithContentDescription("Sabit yer: sütun 2, satır 2").performSemanticsAction(SemanticsActions.OnClick)
        val before = canvas()
        val bx = before.strokes.single().points.first()
        compose.onNode(hasText("Uygula") and hasAnyAncestor(hasTestTag("canvas_size_sheet"))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().width == 900 && canvas().height == 700 }
        val moved = canvas().strokes.single().points.first()
        assertEquals(bx.x + ((900 - before.width) * 0.5f).roundToInt(), moved.x, 0.6f)
        assertEquals(bx.y + ((700 - before.height) * 0.5f).roundToInt(), moved.y, 0.6f)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V18GraphTest : V18EditorBase() {
    @Test
    fun patternChangesFormulaAndGraphFollowsPaper() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            val c = repository.createCanvas("Grafik", 1200, 900, "#FFFFFF", false, kind = CanvasKind.NOTES)
            val graph = ShapeFactory.build("curve.sinus", Offset(100f, 100f), Offset(1100f, 800f), ShapeStyle(strokeColorHex = "#263238", strokeWidth = 3f), id = "g", order = 0)!!
            repository.saveStrokes(c.localId, listOf(listOf(graph)))
        }
        waitForText("Grafik")
        compose.onNodeWithText("Grafik").performClick()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        fun label() = canvas().strokes.single().shape!!.subpaths.firstOrNull { it.tag == "formula" && it.label != null }?.label
        assertEquals("y = sin(x)", label())

        // Düzenle → grafiğe dokun → Grafik aracı → deseni artır
        mode("Düzenle")
        val size = canvasSize()
        val scale = minOf((size.x - 2 * 24f * compose.density.density) / 1200f, (size.y - 2 * 24f * compose.density.density) / 900f)
        val ox = (size.x - 1200f * scale) / 2f
        val oy = (size.y - 900f * scale) / 2f
        tap(Offset(ox + 600f * scale, oy + 450f * scale))
        waitFor { compose.onAllNodes(hasContentDescription("Grafik") and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNodes().isNotEmpty() }
        topTool("Grafik").performSemanticsAction(SemanticsActions.OnClick)
        waitForDescription("Deseni artır")
        compose.onNodeWithContentDescription("Deseni artır").performClick()
        waitFor { canvas().strokes.single().shape!!.graph!!.freq == 2 }
        waitFor { label() == "y = sin(2x)" }
        waitForText("Desen: 2")
        compose.onNodeWithContentDescription("Deseni artır").performClick()
        waitFor { label() == "y = sin(3x)" }
        compose.onNodeWithContentDescription("Deseni azalt").performClick()
        waitFor { label() == "y = sin(2x)" }

        // Kağıt koyulaşınca grafik kağıda uyar (formül kutusu ve nokta içleri kağıt renginde, ızgara kağıda göre)
        fun box() = canvas().strokes.single().shape!!.subpaths.first { it.tag == "formula" && it.closed }
        assertEquals("#FFFFFF", box().fillColorHex)
        finish()
        mode("Çiz")
        compose.onNode(hasContentDescription("Boya kutusu") and hasAnyAncestor(hasTestTag("tool_row_draw"))).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Renk seç")
        // Renk alanının sol-alt köşesi: çok koyu bir kağıt
        compose.onNodeWithTag("color_sv_area").performTouchInput { click(Offset(width * 0.05f, height * 0.95f)) }
        waitFor { canvas().backgroundColorHex != "#FFFFFF" }
        val paper = canvas().backgroundColorHex
        assertTrue("koyu kağıt: $paper", androidx.core.graphics.ColorUtils.calculateLuminance(android.graphics.Color.parseColor(paper)) < 0.1)
        waitFor { box().fillColorHex.equals(paper, ignoreCase = true) }
        val g = canvas().strokes.single()
        assertTrue(g.shape!!.graph!!.paper.equals(paper, ignoreCase = true))
        assertTrue("beyaz ızgara kalmaz", g.shape!!.subpaths.none { it.strokeColorHex == "#DDE3EA" })
        assertEquals("desen korunur", "y = sin(2x)", label())
        SystemBack.press()
        waitFor { compose.onAllNodesWithTag("color_pick_sheet").fetchSemanticsNodes().isEmpty() }
        // Geri alınınca grafik de eski kağıda döner (tek adım)
        compose.onNode(hasContentDescription("Geri al") and hasAnyAncestor(hasTestTag("tool_row_draw"))).performClick()
        waitFor { canvas().backgroundColorHex == "#FFFFFF" && box().fillColorHex == "#FFFFFF" }
    }
}
