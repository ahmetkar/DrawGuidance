package com.karapp.drawguidance

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.model3d.Brush3Kind
import com.karapp.drawguidance.content.model3d.DrawPlane
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.Model3DResults
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.ui.screens.model3d.Mode3
import com.karapp.drawguidance.ui.screens.model3d.Model3DEditorScreen
import com.karapp.drawguidance.ui.screens.model3d.Model3DEditorViewModel
import com.karapp.drawguidance.ui.screens.model3d.Panel3
import com.karapp.drawguidance.ui.screens.model3d.Tool3
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** v26 testlerinde ortak: fırça ve silgi izleri (ekran noktaları). */
internal object V26Draw {
    fun path(vm: Model3DEditorViewModel, pts: List<Offset>): FloatArray {
        val plane = vm.drawPlane()!!
        return pts.mapNotNull { vm.planePoint(plane, it.x, it.y) }.flatMap { listOf(it.x, it.y, it.z) }.toFloatArray()
    }

    fun drawings(m: Model3D) = m.parts.count { it.brush != null }
}

/**
 * v26 (1): 3B düzenleyicinin modları (mod değişince panel kapanır, alt araç hatırlanır), parça grupları (dokununca bütün
 * grup seçilir, ortak merkez çevresinde birlikte döner/ölçeklenir, kopyası yeni grup), silgi yalnız çizimlere (tüp ve
 * çizgi sürerken bölünür, şişir parmak kalkınca oyulur, küpe dokunmaz; tek geri alma adımı), Son çizimi sil ve Tümünü
 * temizle.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V26Model3DViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun close() {
        db.close()
        org.koin.core.context.stopKoin()
    }

    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idle() }

    private fun vm(): Model3DEditorViewModel {
        val tex = TextureRepository(db.texture3DDao())
        val repo = Model3DRepository(context, db.model3DDao(), tex)
        val images = ImagesRepository(db.savedImageDao(), ImageExporter(context, useGallery = false), CanvasAssetStore(db.canvasAssetDao()))
        val vm = Model3DEditorViewModel(repo, tex, Model3DResults(), null, null, null, images = images)
        idle()
        vm.setViewport(900, 900)
        return vm
    }

    private fun waitHeavy(vm: Model3DEditorViewModel) {
        val end = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (vm.busy.value == null) return
            Thread.sleep(15)
        }
        fail("işlem bitmedi")
    }

    @Test
    fun modesSwitchToolsAndClosePanels() {
        val vm = vm()
        assertEquals(Mode3.SELECT, vm.mode.value)
        assertEquals(Tool3.MOVE, vm.tool.value)
        // Seç modunun paneli (İşlemler) Çiz'e geçince kapanır; Çiz fırça panelini açar
        vm.togglePanel(Panel3.OPS)
        vm.setMode(Mode3.DRAW)
        assertEquals(Tool3.DRAW, vm.tool.value)
        assertEquals(Panel3.BRUSH, vm.panel.value)
        // Silgiye geçince fırça paneli kapanır
        vm.setTool(Tool3.ERASE)
        assertNull(vm.panel.value)
        // Parçalar her modda açık kalır
        vm.togglePanel(Panel3.PARTS)
        vm.setMode(Mode3.EDIT)
        assertEquals(Panel3.PARTS, vm.panel.value)
        assertEquals(Tool3.POINT, vm.tool.value)
        vm.setTool(Tool3.FACE)
        vm.setMode(Mode3.SELECT)
        assertEquals(Tool3.MOVE, vm.tool.value)
        // Modun son alt aracı hatırlanır
        vm.setMode(Mode3.EDIT)
        assertEquals(Tool3.FACE, vm.tool.value)
        vm.setMode(Mode3.DRAW)
        assertEquals(Tool3.ERASE, vm.tool.value)
        // Ekle modu ekleme panelini açar, başka moda geçince kapanır
        vm.setMode(Mode3.ADD)
        assertEquals(Panel3.ADD, vm.panel.value)
        vm.setMode(Mode3.PAINT)
        assertNull(vm.panel.value)
        assertEquals(Tool3.PAINT, vm.tool.value)
        // Araç başka moddansa mod da değişir
        vm.setTool(Tool3.EDGE)
        assertEquals(Mode3.EDIT, vm.mode.value)
    }

    @Test
    fun groupsAreSelectedAndTransformedTogether() {
        val vm = vm()
        val cube = vm.model.value!!.parts[0]
        vm.addPart(PrimitiveKind.SPHERE)
        val sphere = vm.model.value!!.parts[1]
        vm.selectPart(cube.id); vm.selectPart(sphere.id, add = true)
        vm.groupSelected()
        val m = vm.model.value!!
        assertEquals(1, m.groups.size)
        assertEquals(m.groups[0].id, m.part(cube.id)!!.group)
        assertEquals(m.groups[0].id, m.part(sphere.id)!!.group)
        // Sahnede küpe dokunmak bütün grubu seçer (küp asıl)
        vm.clearSelection()
        vm.setMode(Mode3.SELECT)
        vm.fitView()
        val c = vm.toScreen(Model3DEditorViewModel.partCenter(vm.model.value!!.part(cube.id)!!))!!
        vm.tapAt(c.first, c.second)
        assertEquals(listOf(cube.id, sphere.id), vm.selection.value.parts)
        // Taşı: ikisi birlikte
        vm.moveSelected(V3(0.5f, 0f, 0f), coalesce = null)
        assertEquals(cube.position.x + 0.5f, vm.model.value!!.part(cube.id)!!.position.x, 1e-4f)
        assertEquals(sphere.position.x + 0.5f, vm.model.value!!.part(sphere.id)!!.position.x, 1e-4f)
        // Çevir: ortak merkez çevresinde (merkez yerinde kalır), parçalar 90° döner
        fun centers() = listOf(cube.id, sphere.id).map { Model3DEditorViewModel.partCenter(vm.model.value!!.part(it)!!) }
        val before = centers()
        val pivot = (before[0] + before[1]) * 0.5f
        vm.rotateSelected(90f, axis = -1, coalesce = null)
        val after = centers()
        val pivot2 = (after[0] + after[1]) * 0.5f
        assertTrue("merkez sabit: $pivot → $pivot2", (pivot2 - pivot).length < 1e-3f)
        val d0 = before[1] - before[0]; val d1 = after[1] - after[0]
        // Y çevresinde 90°: (x, z) → (z, −x)
        assertEquals(d0.z, d1.x, 1e-3f); assertEquals(-d0.x, d1.z, 1e-3f)
        assertEquals(90f, vm.model.value!!.part(cube.id)!!.rotation.y, 0.5f)
        // Ölçekle: aralık da büyür
        vm.scaleSelected(2f, axis = -1, coalesce = null)
        val scaled = centers()
        assertEquals(d1.length * 2f, (scaled[1] - scaled[0]).length, 1e-3f)
        assertEquals(cube.scale.x * 2f, vm.model.value!!.part(cube.id)!!.scale.x, 1e-4f)
        // Kopyası yeni grup olur
        vm.duplicateSelected()
        val dup = vm.model.value!!
        assertEquals(2, dup.groups.size)
        val copyGroup = dup.groups.single { it.name.endsWith("(2)") }
        assertEquals(2, dup.parts.count { it.group == copyGroup.id })
        // Grubu çöz (kopyalar seçili)
        vm.ungroupSelected()
        assertEquals(1, vm.model.value!!.groups.size)
        assertTrue(vm.model.value!!.parts.filter { it.id in vm.selection.value.parts }.all { it.group == null })
        // Tek üyesi kalan grup kendiliğinden çözülür
        vm.deleteSelected()
        vm.selectPart(sphere.id)
        vm.deleteSelected()
        val left = vm.model.value!!
        assertTrue(left.groups.isEmpty())
        assertNull(left.part(cube.id)!!.group)
    }

    @Test
    fun eraserErasesOnlyDrawingsAsOneStep() {
        val vm = vm()
        val cube = vm.model.value!!.parts[0]
        vm.setMode(Mode3.DRAW)
        vm.setPlane(DrawPlane.FRONT)
        val plane = vm.drawPlane()!!
        // Küpün önünden geçen yatay tüp ve çizgi
        vm.setBrushWidth(20f)
        vm.setBrush(vm.brush.value.copy(kind = Brush3Kind.TUBE))
        val row = (0..40).map { Offset(150f + it * 15f, 420f) }
        vm.finishBrush(V26Draw.path(vm, row), null, plane.second)
        waitHeavy(vm)
        vm.setBrush(vm.brush.value.copy(kind = Brush3Kind.LINE))
        vm.finishBrush(V26Draw.path(vm, row.map { it + Offset(0f, 120f) }), null, plane.second)
        assertEquals(1, V26Draw.drawings(vm.model.value!!))
        assertEquals(1, vm.model.value!!.strokes.size)
        val cubeMesh = vm.model.value!!.part(cube.id)!!.mesh
        // Dikey silgi izi (küpün ortasından da geçer)
        vm.setTool(Tool3.ERASE)
        val xs = FloatArray(30) { 450f }; val ys = FloatArray(30) { 300f + it * 12f }
        vm.beginErase()
        vm.eraseAlong(xs.copyOfRange(0, 12), ys.copyOfRange(0, 12), 22f)
        vm.eraseAlong(xs.copyOfRange(11, 30), ys.copyOfRange(11, 30), 22f)
        vm.endErase(xs, ys, 22f)
        waitHeavy(vm)
        val m = vm.model.value!!
        assertEquals("tüp ikiye bölündü", 2, V26Draw.drawings(m))
        assertEquals("çizgi ikiye bölündü", 2, m.strokes.size)
        assertSame("küpe dokunulmadı", cubeMesh, m.part(cube.id)!!.mesh)
        // Tek geri alma adımı
        vm.undo()
        assertEquals(1, V26Draw.drawings(vm.model.value!!))
        assertEquals(1, vm.model.value!!.strokes.size)
        vm.redo()
        // Şişir: parmak kalkınca oyulur (arka planda)
        vm.setTool(Tool3.DRAW)
        vm.setBrush(vm.brush.value.copy(kind = Brush3Kind.INFLATE))
        val ring = (0..48).map { val a = it / 48.0 * 2 * Math.PI; Offset(450f + 160f * cos(a).toFloat(), 250f + 90f * sin(a).toFloat()) }
        vm.finishBrush(V26Draw.path(vm, ring), null, plane.second)
        waitHeavy(vm)
        val pillowId = vm.model.value!!.parts.last().id
        vm.setTool(Tool3.ERASE)
        val px = FloatArray(20) { 450f }; val py = FloatArray(20) { 130f + it * 13f }
        vm.beginErase()
        vm.eraseAlong(px, py, 18f)
        assertNotEquals("şişir sürerken oyulmaz", null, vm.model.value!!.part(pillowId))
        vm.endErase(px, py, 18f)
        waitHeavy(vm)
        val carved = vm.model.value!!
        assertEquals("şişir ikiye oyuldu", 2, carved.parts.count { it.brush?.spec?.kind == Brush3Kind.INFLATE })
        assertSame(cubeMesh, carved.part(cube.id)!!.mesh)
    }

    @Test
    fun deleteLastDrawingAndClearAll() {
        val vm = vm()
        vm.setMode(Mode3.DRAW)
        val plane = vm.drawPlane()!!
        val row = (0..30).map { Offset(200f + it * 15f, 300f) }
        vm.finishBrush(V26Draw.path(vm, row), null, plane.second)
        Thread.sleep(5)
        vm.setBrush(vm.brush.value.copy(kind = Brush3Kind.TUBE))
        vm.finishBrush(V26Draw.path(vm, row.map { it + Offset(0f, 150f) }), null, plane.second)
        waitHeavy(vm)
        assertEquals(1, vm.model.value!!.strokes.size)
        assertEquals(1, V26Draw.drawings(vm.model.value!!))
        // Son çizilen (tüp) silinir, sonra çizgi
        vm.deleteLastDrawing()
        assertEquals(0, V26Draw.drawings(vm.model.value!!))
        assertEquals(1, vm.model.value!!.strokes.size)
        vm.deleteLastDrawing()
        assertTrue(vm.model.value!!.strokes.isEmpty())
        // Hepsini temizle: cisimler (küp) kalır; geri alınabilir
        vm.undo(); vm.undo()
        val parts = vm.model.value!!.parts.size
        vm.clearDrawings()
        assertTrue(vm.model.value!!.strokes.isEmpty())
        assertEquals(parts - 1, vm.model.value!!.parts.size)
        assertFalse(Model3DEditorViewModel.hasDrawings(vm.model.value!!))
        vm.undo()
        assertTrue(Model3DEditorViewModel.hasDrawings(vm.model.value!!))
    }
}

/**
 * v26 (2): yeni düzen: mod çubuğu (Seç, Düzenle, Çiz, Boya, Ekle), araç satırında geri al/yinele, panelde ✕ ve modu
 * değiştirince panelin kapanması, Parçalar'da grup, seçim çipi, sahnede sürüklenen grup, silgi ve temizleme (ekran
 * görüntüleriyle).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V26Model3DEditorUiTest : V23EditorBase() {

    private lateinit var vm: Model3DEditorViewModel

    private fun open() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val k = GlobalContext.get()
        vm = Model3DEditorViewModel(k.get(), k.get(), k.get(), null, null, null, k.get(), images = k.get())
        compose.runOnUiThread {
            compose.activity.setContent { DrawGuidanceTheme(darkTheme = false) { Model3DEditorScreen(null, null, null, onBack = {}, vm = vm) } }
        }
        waitFor { exists("model3d_editor") && vm.currentView() != null }
        settleFor(800)
    }

    private fun idleHeavy() = waitFor(90_000) { vm.busy.value == null }

    @Test
    fun modesPanelsGroupsEraserAndClear() {
        open()
        assertTrue(exists("model3d_modes"))
        assertTrue(compose.onAllNodesWithText("Taşı").fetchSemanticsNodes().isNotEmpty())
        shot("v26-01-select-mode")

        // Ekle modu: panel ✕ ile kapanır
        mode3("Ekle")
        waitFor { exists("model3d_panel_add") }
        settleFor(600)
        shot("v26-02-add-mode")
        panelItem3("Küre")
        waitFor { vm.model.value!!.parts.size == 2 }
        // Aynı moda yeniden dokunmak paneli açar
        mode3("Ekle")
        waitFor { exists("model3d_panel_add") }
        compose.onNodeWithTag("model3d_panel_close").performClick()
        waitFor { !exists("model3d_panel") }
        // Çiz modunun fırça paneli Seç'e geçince kapanır
        mode3("Çiz")
        waitFor { exists("model3d_panel_brush") }
        mode3("Seç")
        waitFor { !exists("model3d_panel_brush") }

        // Grupla: seçim çipi ve Parçalar'da grup satırı
        val ids = vm.model.value!!.parts.map { it.id }
        compose.runOnUiThread { vm.selectPart(ids[0]); vm.selectPart(ids[1], add = true) }
        action3("Grupla")
        waitFor { vm.model.value!!.groups.size == 1 }
        waitForText("Grup 1 · 2 parça")
        compose.onNodeWithContentDescription("Parçalar").performClick()
        waitFor { exists("model3d_group_row") }
        settleFor(600)
        shot("v26-03-parts-groups")
        compose.onNodeWithTag("model3d_panel_close").performClick()
        // Küpü sürüklemek grubu birlikte taşır (panel kapanınca sahne büyür: önce yerleşim bitsin)
        waitFor { !exists("model3d_panel") }
        settleFor(400)
        compose.runOnUiThread { vm.clearSelection() }
        val p0 = vm.model.value!!.parts.map { it.position }
        val c = vm.toScreen(Model3DEditorViewModel.partCenter(vm.model.value!!.parts[0]))!!
        compose.onNodeWithTag("model3d_viewport").performTouchInput {
            down(Offset(c.first, c.second))
            for (i in 1..12) moveTo(Offset(c.first + i * 8f, c.second))
            up()
        }
        waitFor { vm.model.value!!.parts[0].position != p0[0] }
        val d0 = vm.model.value!!.parts[0].position - p0[0]
        val d1 = vm.model.value!!.parts[1].position - p0[1]
        assertTrue("birlikte taşındı: $d0 / $d1", (d0 - d1).length < 1e-4f)

        // Çiz: tüp, sonra silgiyle ortadan
        tool3("Çiz")
        waitFor { exists("model3d_panel_brush") }
        compose.onNode(hasText("Tüp") and hasAnyAncestor(hasTestTag("model3d_brush_kinds"))).performClick()
        compose.onNodeWithTag("model3d_panel_close").performClick()
        waitFor { !exists("model3d_panel") }
        val vp = compose.onNodeWithTag("model3d_viewport").fetchSemanticsNode().size
        compose.onNodeWithTag("model3d_viewport").performTouchInput {
            down(Offset(vp.width * 0.1f, vp.height * 0.2f))
            for (i in 1..30) moveTo(Offset(vp.width * (0.1f + i * 0.027f), vp.height * 0.2f))
            up()
        }
        idleHeavy()
        waitFor { V26Draw.drawings(vm.model.value!!) == 1 }
        subtool3("Silgi")
        assertFalse("silgide fırça paneli kapalı", exists("model3d_panel_brush"))
        compose.onNodeWithTag("model3d_viewport").performTouchInput {
            down(Offset(vp.width * 0.5f, vp.height * 0.08f))
            for (i in 1..12) moveTo(Offset(vp.width * 0.5f, vp.height * (0.08f + i * 0.02f)))
            up()
        }
        idleHeavy()
        waitFor { V26Draw.drawings(vm.model.value!!) == 2 }
        settleFor(600)
        shot("v26-04-eraser")
        // Son çizimi sil: bölünen iki parça birlikte gider
        action3("Son çizimi sil")
        waitFor { V26Draw.drawings(vm.model.value!!) == 0 }
        // Çizgi çiz, Tümünü temizle (onaylı), geri al
        subtool3("Fırça")
        compose.onNodeWithTag("model3d_panel_close").performClick()
        compose.runOnUiThread { vm.setBrush(vm.brush.value.copy(kind = Brush3Kind.LINE)) }
        compose.onNodeWithTag("model3d_viewport").performTouchInput {
            down(Offset(vp.width * 0.2f, vp.height * 0.3f))
            for (i in 1..20) moveTo(Offset(vp.width * (0.2f + i * 0.02f), vp.height * (0.3f + 0.04f * sin(i / 3.0).toFloat())))
            up()
        }
        waitFor { vm.model.value!!.strokes.size == 1 }
        settleFor(400)
        shot("v26-05-draw-mode")
        action3("Tümünü temizle")
        waitFor { exists("model3d_clear_dialog") }
        compose.onNodeWithText("Temizle").performClick()
        waitFor { vm.model.value!!.strokes.isEmpty() }
        // Geri al (araç satırının solunda)
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { vm.model.value!!.strokes.size == 1 }

        // Düzenle modu
        tool3("Kenar")
        settleFor(400)
        shot("v26-06-edit-mode")
        assertEquals(Mode3.EDIT, vm.mode.value)
        assertTrue(abs(vm.model.value!!.parts.size - 2) == 0)
        runBlocking { GlobalContext.get().get<Model3DRepository>().observe().first() }
    }
}
