package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.settings.ExportSettingsStore
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.AngleSnap
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.EdgesGuide
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerController
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerHandle
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerLabels
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerPose
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerSettings
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerSettingsStore
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerShape
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerType
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerUnit
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerUnits
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.SnapEdges
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.TemplateGhost
import kotlinx.coroutines.runBlocking
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/** v40 cetvel: kenara yapışma hesabı (doğru, yay, çokgen), birim/ölçek dönüşümü, açı yakalama. */
class V40RulerMathTest {
    private val out = FloatArray(3)

    @Test fun lineSnapping() {
        val e = SnapEdges.Builder().line(0f, 0f, 100f, 0f).endContour().line(0f, 50f, 100f, 50f).endContour().build()
        assertEquals(2, e.contourCount)
        // Yakın kenar (üst), uzak nokta kilitlenmez
        assertEquals(0, e.lock(40f, 8f, 20f))
        assertEquals(1, e.lock(40f, 44f, 20f))
        assertEquals(-1, e.lock(40f, 25f, 20f))
        e.project(0, 30f, 7f, out)
        assertEquals(30f, out[0], 1e-4f); assertEquals(0f, out[1], 1e-4f); assertEquals(7f, out[2], 1e-4f)
        // Kilitliyken uzaklaşan parmak da kenarda kalır; uçtan öte uca kısılır
        e.project(0, 60f, 300f, out)
        assertEquals(60f, out[0], 1e-4f); assertEquals(0f, out[1], 1e-4f)
        e.project(0, 160f, -5f, out)
        assertEquals(100f, out[0], 1e-4f); assertEquals(0f, out[1], 1e-4f)
    }

    @Test fun arcSnapping() {
        // Üst yarım çember (y yukarı negatif): π..2π
        val e = SnapEdges.Builder().arc(0f, 0f, 100f, PI.toFloat(), PI.toFloat()).endContour().build()
        e.project(0, 0f, -150f, out)
        assertEquals(0f, out[0], 1e-3f); assertEquals(-100f, out[1], 1e-3f)
        e.project(0, 30f, -40f, out)
        assertEquals(100f, hypot(out[0], out[1]), 1e-3f)
        assertEquals(0.6f, out[0] / 100f, 1e-3f)
        // Yayın dışında (alt yarı): yakın uca
        e.project(0, 150f, 20f, out)
        assertEquals(100f, out[0], 1e-3f); assertEquals(0f, out[1], 1e-3f)
        assertEquals(0, e.lock(0f, -110f, 15f))
        assertEquals(-1, e.lock(0f, -60f, 15f))
        // Ters tarama aynı yay
        val back = SnapEdges.Builder().arc(0f, 0f, 100f, 2 * PI.toFloat(), -PI.toFloat()).endContour().build()
        back.project(0, 0f, -150f, out)
        assertEquals(-100f, out[1], 1e-3f)
    }

    @Test fun polygonSnappingFollowsCorners() {
        val tri = floatArrayOf(0f, 0f, 100f, 0f, 0f, -100f)
        val e = SnapEdges.Builder().polyline(tri, closed = true).endContour().build()
        assertEquals(3, e.segmentCount)
        val c = e.lock(50f, 5f, 10f)
        assertEquals(0, c)
        // Aynı konturda köşeyi dönüp hipotenüse geçer
        e.project(c, 60f, -50f, out)
        assertEquals(55f, out[0], 1e-3f); assertEquals(-45f, out[1], 1e-3f)
        // Dik kenar (x = 0)
        e.project(c, -7f, -30f, out)
        assertEquals(0f, out[0], 1e-3f); assertEquals(-30f, out[1], 1e-3f)
    }

    @Test fun rulerShapes() {
        val straight = RulerShape(RulerPose(RulerType.STRAIGHT, 500f, 500f, 0f, 400f, 60f))
        assertEquals(2, straight.edges.contourCount)
        assertTrue(straight.inBody(Offset(500f, 500f), 10f))
        assertFalse(straight.inBody(Offset(500f, 540f), 10f))
        assertEquals(RulerHandle.BODY, straight.hit(Offset(450f, 500f), 20f, 30f))
        assertEquals(RulerHandle.ROTATE, straight.hit(Offset(270f, 500f), 20f, 30f))
        assertEquals(RulerHandle.SIZE, straight.hit(Offset(730f, 500f), 20f, 30f))
        // Boy tutamağı: sol uç yerinde kalır
        val longer = straight.resized(RulerHandle.SIZE, Offset(830f, 500f), 30f)
        assertEquals(500f, longer.length, 1e-3f)
        assertEquals(300f, longer.cx - longer.length / 2f, 1e-3f)
        // Döndürülmüş cetvelin üst kenarı
        val turned = RulerShape(RulerPose(RulerType.STRAIGHT, 0f, 0f, 90f, 200f, 40f))
        val g = EdgesGuide(turned.edges)
        val lock = g.lock(25f, 10f, 10f)
        assertTrue(lock >= 0)
        g.project(lock, 27f, 60f, out)
        assertEquals(20f, out[0], 1e-3f); assertEquals(60f, out[1], 1e-3f)

        val protractor = RulerShape(RulerPose(RulerType.PROTRACTOR, 0f, 0f, 0f, 200f, 40f))
        assertEquals(1, protractor.edges.contourCount)
        assertEquals(2, protractor.edges.segmentCount)
        protractor.edges.project(0, 0f, -130f, out)
        assertEquals(-100f, out[1], 1e-3f)
        protractor.edges.project(0, 40f, 6f, out)
        assertEquals(40f, out[0], 1e-3f); assertEquals(0f, out[1], 1e-3f)
        assertEquals(100f, protractor.measuredLength, 0f)

        for (type in listOf(RulerType.SET_SQUARE_45, RulerType.SET_SQUARE_30)) {
            val sq = RulerShape(RulerPose(type, 0f, 0f, 0f, 300f, 40f))
            assertEquals(3, sq.edges.segmentCount)
            assertTrue(sq.inBody(Offset(0f, 0f), 1f))
            val bigger = sq.resized(RulerHandle.SIZE, sq.handle(RulerHandle.SIZE, 30f)!! + Offset(100f, 0f), 30f)
            assertEquals(450f, bigger.length, 1e-2f)
        }

        // Pergel: her yerden çembere yapışır, yarıçap tutamağı açıyı da belirler
        val compass = RulerShape(RulerPose(RulerType.COMPASS, 0f, 0f, 0f, 200f, 40f))
        val cg = EdgesGuide(compass.edges, captureAll = true)
        val cl = cg.lock(300f, 300f, 5f)
        assertEquals(0, cl)
        cg.project(cl, 0f, 250f, out)
        assertEquals(100f, out[1], 1e-3f)
        val r = compass.resized(RulerHandle.SIZE, Offset(0f, 150f), 30f)
        assertEquals(300f, r.length, 1e-3f); assertEquals(90f, r.angle, 1e-3f)

        val circle = RulerShape(RulerPose(RulerType.ELLIPSE, 0f, 0f, 0f, 200f, 40f, minor = 1f))
        circle.edges.project(0, 0f, 70f, out)
        assertEquals(100f, out[1], 1e-3f)
        assertFalse("delik çizilebilir", circle.inBody(Offset(0f, 0f), 1f))
        assertTrue(circle.inBody(Offset(110f, 0f), 1f))
        val ellipse = RulerShape(RulerPose(RulerType.ELLIPSE, 0f, 0f, 0f, 200f, 40f, minor = 0.5f))
        ellipse.edges.project(0, 0f, 20f, out)
        assertEquals(50f, out[1], 0.5f)

        val french = RulerShape(RulerPose(RulerType.FRENCH_CURVE, 0f, 0f, 0f, 400f, 40f))
        assertEquals(2, french.edges.contourCount)
    }

    @Test fun unitAndScaleConversion() {
        assertEquals(150f / 2.54f, RulerUnits.toPx(1f, RulerUnit.CM), 1e-3f)
        assertEquals(150f, RulerUnits.toPx(1f, RulerUnit.IN), 1e-3f)
        assertEquals(10f, RulerUnits.toUnits(RulerUnits.toPx(10f, RulerUnit.MM), RulerUnit.MM), 1e-3f)
        assertEquals(42f, RulerUnits.toPx(42f, RulerUnit.PX), 0f)
        assertEquals(2f, RulerUnits.toUnits(300f, RulerUnit.IN), 1e-4f)
        assertEquals(1f, RulerUnits.toUnits(300f, RulerUnit.IN, dpi = 300f), 1e-4f)
        val tenCm = RulerUnits.toPx(10f, RulerUnit.CM)
        assertEquals("10 cm", RulerUnits.format(tenCm, RulerUnit.CM, 1))
        assertEquals("100 cm", RulerUnits.format(tenCm, RulerUnit.CM, 10))
        assertEquals("100 mm", RulerUnits.format(tenCm, RulerUnit.MM, 1))
        assertEquals("12.5 cm", RulerUnits.format(RulerUnits.toPx(12.5f, RulerUnit.CM), RulerUnit.CM, 1))
        assertEquals("1:20", RulerUnits.scaleLabel(20))
        val shape = RulerShape(RulerPose(RulerType.STRAIGHT, 0f, 0f, 30f, tenCm, 40f))
        val labels = RulerLabels.of(shape, RulerSettings(RulerType.STRAIGHT, RulerUnit.CM, 2))
        assertEquals("20 cm", labels.length)
        assertEquals("30°", labels.angle)
        assertEquals("1:2 · cm", labels.scale)
        // Çentikler ekranda çok sık olmaz
        val (step, major) = RulerUnits.tickStep(pxPerUnit = 60f, minGapPx = 7f, unit = RulerUnit.CM)
        assertTrue(step * 60f >= 7f)
        assertTrue(major >= 1)
    }

    @Test fun angleSnapping() {
        assertEquals(15f, AngleSnap.snap(13f), 0f)
        assertEquals(10f, AngleSnap.snap(10f), 0f)
        assertEquals(45f, AngleSnap.snap(44.5f), 0f)
        assertEquals(13f, AngleSnap.snap(13f, enabled = false), 0f)
        assertEquals(-90f, AngleSnap.snap(-92f), 0f)
        assertEquals(180f, abs(AngleSnap.snap(-178f)), 0f)
        assertEquals(0f, AngleSnap.snap(358f), 0f)
        assertEquals(270, AngleSnap.display(-90f))
        assertEquals(30f, AngleSnap.normalize(390f), 0f)
    }

    @Test fun templateContoursIncludeHoles() {
        // Dış kare ve içinde delik (iki alt yol)
        val pts = listOf(0f to 0f, 100f to 0f, 100f to 100f, 0f to 100f, 40f to 40f, 60f to 40f, 60f to 60f, 40f to 60f)
            .map { StrokePoint(it.first, it.second) }
        val stroke = Stroke(
            "s", pts, "#000000", BrushType.SHAPE, 3f, 1f, 0,
            shape = ShapeSpec("frame", listOf(SubPath(4, closed = true), SubPath(4, closed = true))),
        )
        val contours = TemplateGhost.contoursOf(listOf(stroke))
        assertEquals(2, contours.size)
        val ghost = TemplateGhost.of(contours)!!
        assertEquals(50f, ghost.cx, 1e-3f); assertEquals(50f, ghost.cy, 1e-3f)
        assertEquals(2, ghost.edges.contourCount)
        // İç delik kenarına yakın: iç kontura kilitlenir
        assertEquals(1, ghost.edges.lock(50f, 43f, 8f))
        // Taşı ve ölçekle
        val moved = ghost.moved(10f, 0f).scaled(2f)
        assertEquals(-40f, moved.bounds.left, 1e-3f)
        assertEquals(160f, moved.bounds.right, 1e-3f)
        assertEquals(0, moved.edges.lock(160f, 50f, 2f))
        // Serbest çizim açık kırık çizgi
        val ink = Stroke("i", listOf(StrokePoint(0f, 0f), StrokePoint(10f, 10f)), "#000000", BrushType.PENCIL, 3f, 1f, 1)
        assertFalse(TemplateGhost.contoursOf(listOf(ink)).single().second)
    }

    @Test fun settingsAreRemembered() {
        var saved: RulerSettings? = null
        val store = object : RulerSettingsStore {
            override fun load() = RulerSettings(RulerType.PROTRACTOR, RulerUnit.MM, 5)
            override fun save(settings: RulerSettings) { saved = settings }
        }
        val c = RulerController { null }
        c.attach(store)
        assertEquals(RulerType.PROTRACTOR, c.settings.value.type)
        c.openRuler(Offset(100f, 100f), 1000f, 50f)
        assertEquals(RulerType.PROTRACTOR, c.pose.value!!.type)
        assertEquals(600f, c.pose.value!!.length, 1e-3f)
        c.setType(RulerType.COMPASS)
        c.setUnit(RulerUnit.IN)
        c.setScale(10)
        assertEquals(RulerSettings(RulerType.COMPASS, RulerUnit.IN, 10), saved)
        assertNotNull(c.guide())
        c.finish()
        assertNull(c.guide())
        assertEquals(GuideSession.NONE, c.session.value)
    }
}

/** v40 cetvel ve şekil kalıbı çizim ekranında: menüler, kenara yapışan çizgi, Bitir, kalıp. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V40RulerUiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind, first: Boolean = true) {
        if (first) waitForText("Henüz bir tuvaliniz yok", substring = true)
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

    private fun more() {
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        compose.waitForIdle()
    }

    /** v42 Cetvel / Şekil kalıbı Çiz satırında (⋮ menüsünde değil); basit modlarda kendi düğmeleri. */
    private fun openFromMenu(tag: String) {
        val ruler = tag == "ruler_menu_item"
        val simpleTag = if (ruler) "simple_tool_ruler" else "simple_tool_template"
        if (exists(simpleTag)) {
            compose.onNodeWithTag(simpleTag).performScrollTo().performClick()
            return
        }
        if (vm().mode.value != com.karapp.drawguidance.ui.screens.canvasedit.EditMode.DRAW) {
            compose.runOnIdle { vm().chooseMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.DRAW) }
        }
        val toolTag = if (ruler) "tool_ruler" else "tool_template"
        waitFor { exists(toolTag) }
        compose.onNodeWithTag(toolTag).performScrollTo().performClick()
    }

    /** Ekranda dalgalı (elle çizilmiş gibi) sürükleme. */
    private fun wobblyDrag(from: Offset, delta: Offset, amp: Float, steps: Int = 24) {
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(from)
            var last = from
            for (i in 1..steps) {
                val p = from + delta * (i / steps.toFloat()) + Offset(0f, if (i % 2 == 0) amp else -amp)
                moveBy(p - last); last = p
            }
            up()
        }
        compose.waitForIdle()
    }

    @Test
    fun rulerStraightensStrokesAndFinishCloses() {
        open("Cetvel", CanvasKind.ART)
        openFromMenu("ruler_menu_item")
        waitFor { exists("ruler_overlay") && vm().ruler.session.value == GuideSession.RULER }
        assertEquals(EditMode.DRAW, vm().mode.value)
        compose.runOnIdle { vm().ruler.setType(RulerType.STRAIGHT) }
        val pose = vm().ruler.pose.value!!
        assertEquals(0f, pose.angle, 0f)
        assertTrue(exists("ruler_info") && exists("ruler_finish"))
        val edgeY = pose.cy - pose.thickness / 2f
        val s = scale()
        // Üst kenarın biraz dışından, dalgalı çizgi
        val start = screenOf(pose.cx - pose.length * 0.3f, edgeY - 6f / s)
        wobblyDrag(start, Offset(pose.length * 0.5f * s, 0f), amp = 5f)
        waitFor { strokes().size == 1 }
        val stroke = strokes().single()
        assertTrue(stroke.points.size >= 5)
        stroke.points.forEach { p -> assertEquals("kenarda: ${p.y}", edgeY, p.y, 0.5f) }
        assertTrue("çizgi uzar", stroke.points.last().x - stroke.points.first().x > pose.length * 0.4f)
        // Cetvel geri alma geçmişine girmez: geri al yalnız çizgiyi siler
        compose.runOnIdle { vm().undo() }
        waitFor { strokes().isEmpty() }
        assertEquals(GuideSession.RULER, vm().ruler.session.value)
        compose.runOnIdle { vm().redo() }
        waitFor { strokes().size == 1 }

        // Uzakta çizilen çizgi yapışmaz
        wobblyDrag(screenOf(200f, 900f), Offset(300f * s, 0f), amp = 6f)
        waitFor { strokes().size == 2 }
        val free = strokes().last()
        assertTrue("serbest çizgi dalgalı", free.points.maxOf { it.y } - free.points.minOf { it.y } > 3f)

        // Gövdeyi sürüklemek cetveli taşır, çizim eklemez
        drag(screenOf(pose.cx, pose.cy), Offset(0f, 100f * s))
        waitFor { vm().ruler.pose.value!!.cy > pose.cy + 80f }
        assertEquals(2, strokes().size)

        // Bitir: cetvel kapanır, aynı yerde çizgi artık yapışmaz
        compose.onNodeWithTag("ruler_finish").performClick()
        waitFor { !exists("ruler_overlay") && vm().ruler.session.value == GuideSession.NONE }
        val moved = vm().ruler.pose.value!!
        wobblyDrag(screenOf(moved.cx - 100f, moved.cy - moved.thickness / 2f - 6f / s), Offset(200f * s, 0f), amp = 6f)
        waitFor { strokes().size == 3 }
        val after = strokes().last()
        assertTrue(after.points.maxOf { it.y } - after.points.minOf { it.y } > 3f)
    }

    @Test
    fun rotateHandleSnapsAngleAndSettingsShowOnRuler() {
        open("Döndür", CanvasKind.NOTES)
        openFromMenu("ruler_menu_item")
        waitFor { exists("ruler_overlay") }
        compose.runOnIdle { vm().ruler.setType(RulerType.STRAIGHT) }
        val pose = vm().ruler.pose.value!!
        val s = scale()
        val gap = 30f * compose.density.density / s
        val handle = RulerShape(pose).handle(RulerHandle.ROTATE, gap)!!
        // Tutamağı merkez çevresinde ~-43°'ye çevir: 45°'ye yapışır (tutamak solda: açı + 180°)
        val r = pose.length / 2f + gap
        val rad = Math.toRadians(180.0 + 43.0)
        val target = Offset(pose.cx + (r * kotlin.math.cos(rad)).toFloat(), pose.cy + (r * kotlin.math.sin(rad)).toFloat())
        drag(screenOf(handle.x, handle.y), (screenOf(target.x, target.y) - screenOf(handle.x, handle.y)))
        waitFor { vm().ruler.pose.value!!.angle != 0f }
        assertEquals(45f, vm().ruler.pose.value!!.angle, 0.01f)
        assertTrue("çizim eklenmez", strokes().isEmpty())
        // Birim ve ölçek: cetvelin yanındaki tutamak düğmesinden
        assertTrue(exists("ruler_handle_type") && exists("ruler_handle_units") && exists("ruler_handle_finish"))
        compose.onNodeWithTag("ruler_handle_units").performClick()
        compose.onNodeWithTag("ruler_unit_MM").clickInPopup()
        compose.onNodeWithTag("ruler_handle_units").performClick()
        compose.onNodeWithTag("ruler_scale_10").clickInPopup()
        waitFor { vm().ruler.settings.value.unit == RulerUnit.MM && vm().ruler.settings.value.scale == 10 }
        waitFor { compose.onAllNodesWithText("1:10 · mm", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Türler
        compose.onNodeWithTag("ruler_handle_type").performClick()
        compose.onNodeWithTag("ruler_type_PROTRACTOR").clickInPopup()
        waitFor { vm().ruler.pose.value!!.type == RulerType.PROTRACTOR }
        // Ayarlar hatırlanır
        val prefs = com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerPrefs(compose.activity).load()
        assertEquals(RulerSettings(RulerType.PROTRACTOR, RulerUnit.MM, 10), prefs)
        // Başka moda geçince kapanır
        compose.runOnIdle { vm().chooseMode(EditMode.SELECT) }
        waitFor { vm().ruler.session.value == GuideSession.NONE && !exists("ruler_overlay") }
    }

    @Test
    fun templateGhostIsTracedButNotExported() {
        open("Kalıp", CanvasKind.ART)
        // Tuvale bir dikdörtgen
        compose.runOnIdle { vm().selectShapeId("RECTANGLE"); vm().addShape("g1", Offset(300f, 300f), Offset(700f, 600f)) }
        waitFor { strokes().size == 1 }
        compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        val rect = strokes().single()
        openFromMenu("template_menu_item")
        waitFor { exists("template_pick_hint") && vm().ruler.session.value == GuideSession.TEMPLATE_PICK }
        // Şeklin kenarına dokun: kalıp olur, belgeye bir şey eklenmez
        tap(screenOf(500f, 300f))
        waitFor { vm().ruler.session.value == GuideSession.TEMPLATE }
        assertTrue(exists("template_overlay") && exists("template_lock"))
        assertEquals(listOf(rect), strokes())
        // Sağ kenarın biraz dışında dalgalı dikey çizgi: kenara yapışır
        val s = scale()
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            val from = screenOf(706f, 380f)
            down(from)
            var last = from
            for (i in 1..20) {
                val p = from + Offset(if (i % 2 == 0) 5f else -5f, 140f * s * i / 20f)
                moveBy(p - last); last = p
            }
            up()
        }
        waitFor { strokes().size == 2 }
        val traced = strokes().last()
        traced.points.forEach { assertEquals(700f, it.x, 1f) }
        assertTrue(traced.points.none { it.x == 0f })
        // Kilitle: tutamaklar gider; Bitir
        compose.onNodeWithTag("template_lock").performClick()
        waitFor { vm().ruler.locked.value }
        compose.onNodeWithTag("ruler_finish").performClick()
        waitFor { vm().ruler.session.value == GuideSession.NONE && !exists("template_overlay") }
        assertEquals(2, strokes().size)
    }

    @Test
    fun libraryTemplateIsNotPartOfDocumentOrExport() {
        open("Kütüphane", CanvasKind.SIMPLE_ART)
        // Basit menüde ayrı Şekil kalıbı düğmesi
        compose.onNodeWithTag("simple_tool_template").performScrollTo().performClick()
        waitFor { vm().ruler.session.value == GuideSession.TEMPLATE_PICK }
        compose.onNodeWithTag("template_library").performClick()
        waitFor { exists("shape_picker") }
        compose.runOnIdle { vm().ruler.libraryOpen.value = false; vm().ruler.placeLibrary("CIRCLE", Offset(500f, 500f), 400f) }
        waitFor { vm().ruler.session.value == GuideSession.TEMPLATE && exists("template_overlay") }
        assertTrue("belgeye eklenmez", strokes().isEmpty())
        assertFalse("geri alınacak bir şey yok", vm().canUndo.value)
        // Dışa aktarılan resim boş kağıt
        val exporter = ImageExporter(compose.activity, useGallery = false, settings = ExportSettingsStore(compose.activity))
        val bmp = exporter.render(vm().canvas.value!!, 1f)
        val white = bmp.getPixel(0, 0)
        for (y in 0 until bmp.height step 7) for (x in 0 until bmp.width step 7) assertEquals("($x,$y)", white, bmp.getPixel(x, y))
        // Çemberin yanında çizilen çizgi çembere yapışır
        val s = scale()
        drag(screenOf(500f, 296f), Offset(120f * s, 30f * s))
        waitFor { strokes().size == 1 }
        strokes().single().points.forEach { p -> assertEquals(200f, hypot(p.x - 500f, p.y - 500f), 2f) }
    }

    @Test
    fun menuEntriesInEveryMode() {
        CanvasKind.entries.forEachIndexed { i, kind ->
            val name = "Menü $i"
            open(name, kind, first = i == 0)
            // v42 ⋮ menüsünde Cetvel ve Şekil kalıbı yok
            more()
            waitFor { exists("tutorial_menu_item") || exists("more_menu_content") || compose.onAllNodesWithText("Eğitim modu").fetchSemanticsNodes().isNotEmpty() }
            assertFalse(exists("ruler_menu_item") || exists("template_menu_item"))
            SystemBack.press()
            compose.waitForIdle()
            if (!kind.simple) assertTrue("$kind Çiz satırında Cetvel ve Şekil kalıbı", exists("tool_ruler") && exists("tool_template"))
            openFromMenu("ruler_menu_item")
            waitFor { exists("ruler_overlay") }
            if (kind.simple) assertTrue("$kind basit menüde ayrı düğmeler", exists("simple_tool_ruler") && exists("simple_tool_template"))
            else assertFalse(exists("simple_tool_ruler") || exists("simple_tool_template"))
            compose.onNodeWithTag("ruler_finish").performClick()
            waitFor { !exists("ruler_overlay") }
            compose.onNodeWithContentDescription("Geri").performClick()
            waitFor { !exists("drawing_canvas") }
        }
    }

    @Test
    fun simpleModesHaveSeparateButtonsAndBottomBar() {
        listOf(CanvasKind.SIMPLE_ART, CanvasKind.SIMPLE_NOTES, CanvasKind.KIDS).forEachIndexed { i, kind ->
            open("Ayrı $i", kind, first = i == 0)
            waitFor { exists("simple_tool_ruler") && exists("simple_tool_template") }
            assertFalse("seçim kutusu yok", exists("ruler_chooser"))
            // Cetvel: açılır, bilgi çubuğu altta, ayar düğmeleri cetvelin yanında
            compose.onNodeWithTag("simple_tool_ruler").performScrollTo().performClick()
            waitFor { vm().ruler.session.value == GuideSession.RULER && exists("ruler_bar") }
            val screenH = compose.onRoot().fetchSemanticsNode().size.height
            val bar = compose.onNodeWithTag("ruler_bar").fetchSemanticsNode().boundsInRoot
            assertTrue("$kind: çubuk altta (${bar.top} / $screenH)", bar.top > screenH * 0.6f)
            assertTrue("$kind: çubuk ekranda", bar.bottom <= screenH)
            assertTrue(exists("ruler_info") && exists("ruler_handle_type") && exists("ruler_handle_units"))
            // Tutamaktan tür ve birim
            compose.onNodeWithTag("ruler_handle_type").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            compose.onNodeWithTag("ruler_type_SET_SQUARE_45").clickInPopup()
            waitFor { vm().ruler.pose.value!!.type == RulerType.SET_SQUARE_45 }
            compose.onNodeWithTag("ruler_handle_units").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            compose.onNodeWithTag("ruler_unit_IN").clickInPopup()
            waitFor { vm().ruler.settings.value.unit == RulerUnit.IN }
            compose.onNodeWithTag("ruler_handle_type").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            compose.onNodeWithTag("ruler_type_STRAIGHT").clickInPopup()
            waitFor { vm().ruler.pose.value!!.type == RulerType.STRAIGHT }
            // Cetvelin üstündeki Bitir
            compose.onNodeWithTag("ruler_handle_finish").performClick()
            waitFor { vm().ruler.session.value == GuideSession.NONE && !exists("ruler_bar") }
            // Şekil kalıbı ayrı düğmede; yeniden dokununca kapanır
            compose.onNodeWithTag("simple_tool_template").performScrollTo().performClick()
            waitFor { vm().ruler.session.value == GuideSession.TEMPLATE_PICK && exists("template_pick_hint") }
            val tbar = compose.onNodeWithTag("ruler_bar").fetchSemanticsNode().boundsInRoot
            assertTrue("$kind: kalıp çubuğu altta", tbar.top > screenH * 0.6f)
            compose.onNodeWithTag("simple_tool_template").performScrollTo().performClick()
            waitFor { vm().ruler.session.value == GuideSession.NONE }
            compose.onNodeWithContentDescription("Geri").performClick()
            waitFor { !exists("drawing_canvas") }
        }
    }

    @Test
    fun noRulerIn3dEditor() {
        // 3B düzenleyici ayrı ekran: cetvel/kalıp kodu ona bağlı değil
        val dir = java.io.File("src/main/java/com/karapp/drawguidance/ui/screens/model3d")
        assertTrue(dir.isDirectory)
        val uses = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("canvasedit.ruler") || it.readText().contains("ruler_menu_item") }.toList()
        assertTrue("3B düzenleyicide cetvel yok: $uses", uses.isEmpty())
    }
}
