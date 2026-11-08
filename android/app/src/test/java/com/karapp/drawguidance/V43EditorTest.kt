package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick
import com.karapp.drawguidance.ui.screens.canvasedit.components.ColorMath
import com.karapp.drawguidance.ui.screens.canvasedit.components.CornerResizeGesture
import com.karapp.drawguidance.ui.screens.canvasedit.components.SidePanelDock
import com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleElementResize
import com.karapp.drawguidance.ui.screens.canvasedit.editor.AreaSelect
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerController
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.TemplateGhost
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * v43 düzenleyici: şekil kalıbında öneri yok (2), basit modlarda şekil/resim köşeden boyutlanır (3), kalem penceresinde
 * nokta/çizgi düzenleme (5), geniş ekranda yan panel kenara yerleşir (10), dikdörtgen/kalemle alan seçimi ve damlalık
 * (11), RGB/HSV renk (12).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V43EditorTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun line(vararg p: Pair<Float, Float>): List<StrokePoint> = p.map { StrokePoint(it.first, it.second) }

    // ------------------------------------------------------------------ 2 şekil kalıbı

    @Test
    fun strokesOnTemplateAreRecognisedOnlyInsideIt() {
        val ruler = RulerController { null }
        val square = floatArrayOf(100f, 100f, 300f, 100f, 300f, 300f, 100f, 300f, 100f, 100f)
        assertFalse("kalıp yokken", ruler.onTemplate(floatArrayOf(150f), floatArrayOf(150f)))
        ruler.setTemplate(TemplateGhost.of(listOf(square to true))!!)
        // Kalıbın kenarı boyunca çizilen
        assertTrue(ruler.onTemplate(floatArrayOf(100f, 200f, 300f, 300f), floatArrayOf(102f, 98f, 101f, 250f)))
        // Kalıbın dışında
        assertFalse(ruler.onTemplate(floatArrayOf(600f, 700f, 800f), floatArrayOf(600f, 650f, 700f)))
        ruler.finish()
        assertFalse(ruler.onTemplate(floatArrayOf(150f), floatArrayOf(150f)))
    }

    // ------------------------------------------------------------------ 3 basit modda boyutlandırma

    @Test
    fun simpleModesResizeShapeFromCornerKeepingAspect() {
        val shape = ShapeFactory.build("CIRCLE", Offset(100f, 100f), Offset(300f, 200f), ShapeStyle(), id = "s1", order = 0)!!
        assertTrue(SimpleElementResize.eligible(shape))
        assertTrue(SimpleElementResize.handlesShown(EditMode.SELECT, CanvasKind.SIMPLE_ART))
        assertTrue(SimpleElementResize.handlesShown(EditMode.MOVE, CanvasKind.KIDS))
        assertFalse("gelişmişte Ölçekle'de", SimpleElementResize.handlesShown(EditMode.SELECT, CanvasKind.ART))
        val b = SimpleElementResize.bounds(shape)!!
        val (corner, pivot) = SimpleElementResize.corners(b)[2] // sağ alt, karşısı sol üst
        val g = CornerResizeGesture(shape, pivot, corner)
        // Köşe iki katı uzağa çekilince iki kat büyür, sol üst yerinde kalır, oran korunur
        val bigger = SimpleElementResize.resized(g, pivot + (corner - pivot) * 2f)
        val nb = SimpleElementResize.bounds(bigger)!!
        assertEquals(b.left, nb.left, 1f)
        assertEquals(b.top, nb.top, 1f)
        assertEquals(b.width * 2f, nb.width, 2f)
        assertEquals(b.width / b.height, nb.width / nb.height, 0.02f)
        // Karşı köşeyi geçemez (en küçük ölçek)
        assertEquals(SimpleElementResize.MIN_FACTOR, SimpleElementResize.factor(g, pivot - Offset(50f, 50f)), 1e-4f)
    }

    // ------------------------------------------------------------------ 5 (v44) kalemde iki nokta arası çizgi elle eğilir

    @Test
    fun penBendsOnlyTheLineBetweenTwoPoints() {
        open("Kalem", CanvasKind.ART)
        val vm = vm()
        compose.runOnIdle { vm.startPen() }
        waitFor { vm.mode.value == EditMode.PEN }
        // v44 nokta düzenleme penceresi yok
        assertFalse(exists("pen_edit_controls"))
        compose.runOnIdle {
            vm.penTap(Offset(100f, 100f), 20f)
            vm.penTap(Offset(300f, 100f), 20f)
            vm.penTap(Offset(300f, 300f), 20f)
        }
        // Var olan noktaya yakın sürükleme çizgiyi tutmaz (nokta taşınmaz)
        compose.runOnIdle { assertFalse(vm.penGrab(Offset(300f, 100f), 20f)) }
        // İlk çizginin ortasından tutup aşağı çek: çizgi parmaktan geçer, noktalar yerinde
        compose.runOnIdle {
            assertTrue(vm.penGrab(Offset(200f, 102f), 20f))
            vm.penEditDrag(Offset(200f, 160f), false)
            vm.penEditDrag(Offset(200f, 180f), true)
        }
        val anchors = vm.penAnchors.value
        assertEquals(3, anchors.size)
        assertEquals(Offset(100f, 100f), anchors[0].point)
        assertEquals(Offset(300f, 100f), anchors[1].point)
        assertNotNull(anchors[0].bend)
        assertNull(anchors[1].bend)
        val pts = com.karapp.drawguidance.ui.screens.canvasedit.state.PenPath.sample(anchors, false)
        assertTrue("eğri parmağın yerinden geçer", pts.any { abs(it.x - 200f) < 6f && abs(it.y - 180f) < 6f })
        // Boş yere dokunmak yine nokta ekler
        compose.runOnIdle { vm.penTap(Offset(500f, 500f), 20f) }
        assertEquals(4, vm.penAnchors.value.size)
        compose.runOnIdle { vm.penFinish(false) }
        assertEquals(1, vm.canvas.value!!.strokes.size)
    }

    // ------------------------------------------------------------------ 10 yan panel

    @Test
    fun sidePanelDocksToNearestEdge() {
        assertTrue(SidePanelDock.dockLeft(200f, 1200f))
        assertFalse(SidePanelDock.dockLeft(900f, 1200f))
        assertEquals(0f, SidePanelDock.clampDrag(-50f, left = true, areaWidth = 1200f, panelWidth = 400f))
        assertEquals(800f, SidePanelDock.clampDrag(5000f, left = true, areaWidth = 1200f, panelWidth = 400f))
        assertEquals(-800f, SidePanelDock.clampDrag(-5000f, left = false, areaWidth = 1200f, panelWidth = 400f))
        assertEquals(0f, SidePanelDock.clampDrag(30f, left = false, areaWidth = 1200f, panelWidth = 400f))
    }

    // ------------------------------------------------------------------ 11 alanla seçim ve damlalık

    @Test
    fun rectangleAndLassoSelectWithoutDrawingAndEyedropperPicksColor() {
        open("Alan", CanvasKind.ART)
        val vm = vm()
        compose.runOnIdle {
            vm.updateBrushColor("#E53935")
            vm.updateBrushSize(60f)
            vm.onStrokeFinished(line(100f to 100f, 200f to 120f, 300f to 100f))
            vm.onStrokeFinished(line(600f to 600f, 700f to 650f, 800f to 600f))
        }
        val strokes = vm.canvas.value!!.strokes
        assertEquals(2, strokes.size)
        val (a, b) = strokes[0].id to strokes[1].id

        // Dikdörtgen: Seç'e geçilir, kutudakiler seçilir ve elle ölçeklensin diye Ölçekle açılır; çizim eklenmez
        compose.runOnIdle { vm.toggleCanvasPick(CanvasPick.RECT_SELECT) }
        assertEquals(EditMode.SELECT, vm.mode.value)
        // Gelişmiş türde Seç satırında Dikdörtgen, Kalemle alan ve Damlalık
        waitFor { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Dikdörtgen seçim")).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { vm.rectSelect(Offset(50f, 50f), Offset(400f, 300f)) }
        assertEquals(setOf(a), vm.selectedIds.value)
        assertEquals(EditMode.SCALE, vm.mode.value)
        assertEquals(2, vm.canvas.value!!.strokes.size)
        compose.runOnIdle { vm.finishAction() }
        waitFor { vm.mode.value == EditMode.SELECT }

        // Kalemle alan: kapalı çizginin içindekiler
        compose.runOnIdle {
            vm.toggleCanvasPick(CanvasPick.LASSO_SELECT)
            vm.lassoSelect(listOf(Offset(500f, 500f), Offset(900f, 520f), Offset(880f, 760f), Offset(520f, 740f)))
        }
        assertEquals(setOf(b), vm.selectedIds.value)
        assertEquals(2, vm.canvas.value!!.strokes.size)

        // Damlalık: çizginin üstündeki renk fırça rengi olur; araç kendiliğinden kapanır
        compose.runOnIdle {
            vm.updateBrushColor("#000000")
            vm.toggleCanvasPick(CanvasPick.EYEDROPPER)
        }
        assertEquals(CanvasPick.EYEDROPPER, vm.canvasPick.value)
        val picked = vm.eyedrop(Offset(200f, 118f))!!
        val rgb = ColorMath.parseHex(picked)!!
        assertTrue("kırmızımsı: $picked", rgb[0] > 150 && rgb[1] < 140 && rgb[2] < 140)
        assertEquals(picked, vm.brushSettings.value.colorHex.uppercase())
        assertEquals(CanvasPick.NONE, vm.canvasPick.value)
        // Boş kağıtta beyaz
        assertEquals("#FFFFFF", vm.eyedrop(Offset(950f, 50f)))
    }

    @Test
    fun areaGeometry() {
        val poly = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f), Offset(0f, 10f))
        assertTrue(AreaSelect.inside(poly, Offset(5f, 5f)))
        assertFalse(AreaSelect.inside(poly, Offset(15f, 5f)))
        assertFalse(AreaSelect.usable(listOf(Offset(0f, 0f), Offset(1f, 1f)), 4f))
        assertTrue(AreaSelect.usable(poly, 4f))
        val r = AreaSelect.rect(Offset(10f, 20f), Offset(0f, 5f))
        assertEquals(0f, r.left); assertEquals(5f, r.top); assertEquals(10f, r.right); assertEquals(20f, r.bottom)
    }

    // ------------------------------------------------------------------ 12 RGB/HSV

    @Test
    fun colorChannelsConvertBetweenRgbHsvAndHex() {
        assertArrayEquals(intArrayOf(255, 0, 0), ColorMath.rgb(0f, 1f, 1f))
        val hsv = ColorMath.hsv(0, 128, 255)
        assertEquals(210f, hsv[0], 1f)
        assertEquals("#0080FF", ColorMath.hex(0, 128, 255))
        assertArrayEquals(intArrayOf(0x11, 0x22, 0x33), ColorMath.parseHex("#123"))
        assertArrayEquals(intArrayOf(0xAB, 0xCD, 0xEF), ColorMath.parseHex("abcdef"))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#GGGGGG"))
        // Gidiş-dönüş
        val back = ColorMath.rgb(hsv[0], hsv[1], hsv[2])
        assertTrue(abs(back[1] - 128) <= 1 && back[2] == 255)
    }
}
