package com.karapp.drawguidance

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.model3d.Brush3D
import com.karapp.drawguidance.content.model3d.PlaneSnap
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.ui.screens.model3d.ImageExport
import com.karapp.drawguidance.ui.screens.sketch3d.Sketch3DScreen
import com.karapp.drawguidance.ui.screens.sketch3d.Sketch3DViewModel
import com.karapp.drawguidance.ui.screens.sketch3d.SketchMode
import com.karapp.drawguidance.ui.screens.sketch3d.SketchTool
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v56 aşama 11 3B Çizim: düzleme basınçlı çizgi, düz çizgi, silginin çizgiyi bölmesi, düzlemin yüze ve çizgi ucuna
 * yapışması, katmanlar (gizli/kilitli), seçip taşıma (tek geri alma adımı), temel cisim, kaydetme (D3 v2) ve resim.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56Sketch3DViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private lateinit var repo: Model3DRepository

    @After
    fun close() {
        db.close()
        org.koin.core.context.stopKoin()
    }

    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idle() }

    private fun vm(id: String? = null): Sketch3DViewModel {
        val tex = TextureRepository(db.texture3DDao())
        repo = Model3DRepository(context, db.model3DDao(), tex)
        val images = ImagesRepository(db.savedImageDao(), ImageExporter(context, useGallery = false), CanvasAssetStore(db.canvasAssetDao()))
        val vm = Sketch3DViewModel(repo, tex, id, images = images)
        idle()
        vm.setViewport(900, 900)
        return vm
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 12): FloatArray =
        FloatArray(n * 2) { i -> val t = (i / 2) / (n - 1f); if (i % 2 == 0) x0 + (x1 - x0) * t else y0 + (y1 - y0) * t }

    @Test
    fun drawsPressureStrokesOnThePlaneAndStraightLines() {
        val vm = vm()
        val m0 = vm.model.value!!
        assertTrue(m0.parts.isEmpty())
        assertEquals(1, m0.layers.size)
        // Ön düzlem (z = 0): bütün noktalar düzlemde
        val pressures = FloatArray(12) { 0.2f + it * 0.06f }
        val id = vm.finishStroke(line(300f, 400f, 600f, 420f), pressures, straight = false)!!
        val s = vm.model.value!!.strokes.single { it.id == id }
        for (i in 0 until s.count) assertEquals(vm.plane.value.origin.z, s.points[3 * i + 2], 1e-4f)
        assertNotNull(s.widths)
        assertTrue("basınçla kalınlaşır", s.widths!![9] > s.widths!![2])
        assertEquals(m0.layers.first().id, s.layer)
        // Düz çizgi: iki nokta
        val st = vm.finishStroke(line(300f, 500f, 520f, 640f), null, straight = true)!!
        assertEquals(2, vm.model.value!!.strokes.single { it.id == st }.count)
        // Markör yarı saydam ve kalın
        vm.setBrush(Brush3D.MARKER)
        val mk = vm.finishStroke(line(300f, 300f, 600f, 300f), null, false)!!
        val marker = vm.model.value!!.strokes.single { it.id == mk }
        assertTrue(marker.opacity <= 0.6f)
        assertTrue(marker.width > s.width)
        // Geri al / yinele
        vm.undo(); assertEquals(2, vm.model.value!!.strokes.size)
        vm.redo(); assertEquals(3, vm.model.value!!.strokes.size)
    }

    @Test
    fun eraserSplitsStrokesAsOneUndoStep() {
        val vm = vm()
        vm.finishStroke(line(200f, 450f, 700f, 450f, 40), null, false)
        assertEquals(1, vm.model.value!!.strokes.size)
        vm.setTool(SketchTool.ERASER)
        vm.beginErase()
        vm.eraseAt(450f, 450f, 30f)
        vm.eraseAt(455f, 452f, 30f)
        vm.endErase()
        val parts = vm.model.value!!.strokes
        assertEquals("ortadan bölündü", 2, parts.size)
        vm.undo()
        assertEquals(1, vm.model.value!!.strokes.size)
    }

    @Test
    fun planeSnapsToFaceAndLineEndAndRotatesInSteps() {
        val vm = vm()
        vm.addPrimitive(PrimitiveKind.CUBE)
        assertEquals(SketchMode.SELECT, vm.mode.value)
        val cube = vm.model.value!!.parts.single()
        // Üstten bakış: küpün üst yüzüne dokununca düzlem yatay olur
        vm.presetView(3)
        val view = vm.currentView()!!
        val top = view.project(com.karapp.drawguidance.content.model3d.ModelEdit.worldBounds(cube).center)
        assertTrue(vm.snapPlaneAt(top[0], top[1], 20f))
        assertEquals(1f, kotlin.math.abs(vm.plane.value.normal.y), 1e-3f)
        // Çizgi ucuna dokunma: düzlem noktaya taşınır, yönü korunur
        vm.presetView(1)
        vm.setPlanePreset(PlaneSnap.Preset.FRONT)
        vm.finishStroke(line(200f, 300f, 400f, 300f), null, false)
        val st = vm.model.value!!.strokes.last()
        val end = V3(st.points[st.points.size - 3], st.points[st.points.size - 2], st.points[st.points.size - 1])
        val e = vm.currentView()!!.project(end)
        vm.setPlanePreset(PlaneSnap.Preset.SIDE)
        assertTrue(vm.snapPlaneAt(e[0] + 2f, e[1], 20f))
        assertEquals(end, vm.plane.value.origin)
        assertEquals(1f, kotlin.math.abs(vm.plane.value.normal.x), 1e-4f)
        // 15° döndürme ve kameraya çevirme
        vm.rotatePlane(1, PlaneSnap.STEP_DEG)
        assertEquals(kotlin.math.cos(Math.toRadians(15.0)).toFloat(), kotlin.math.abs(vm.plane.value.normal.x), 1e-3f)
        vm.planeToCamera()
        assertFalse(vm.planeTilted())
        // Düzlem kayıtta saklanır
        assertEquals(vm.plane.value, vm.model.value!!.plane)
    }

    @Test
    fun layersHideLockAndSelectionMovesAsOneStep() {
        val vm = vm()
        vm.finishStroke(line(300f, 400f, 600f, 400f), null, false)
        vm.addLayer()
        val l2 = vm.activeLayer.value
        vm.finishStroke(line(300f, 500f, 600f, 500f), null, false)
        val m = vm.model.value!!
        assertEquals(2, m.layers.size)
        assertEquals(l2, m.strokes.last().layer)
        // Gizli katman çizilmez, kilitli katmana çizilmez
        vm.toggleLayerVisible(l2)
        assertEquals(1, vm.model.value!!.visibleLayers().strokes.size)
        assertNull(vm.finishStroke(line(300f, 600f, 600f, 600f), null, false))
        vm.toggleLayerVisible(l2)
        vm.toggleLayerLocked(l2)
        assertNull(vm.finishStroke(line(300f, 600f, 600f, 600f), null, false))
        vm.toggleLayerLocked(l2)
        // Seç ve taşı (sürükleme tek geri alma adımı)
        vm.setMode(SketchMode.SELECT)
        vm.tapSelect(450f, 400f, 24f)
        assertEquals(1, vm.selection.value.size)
        val before = vm.model.value!!
        vm.beginLive()
        repeat(5) { vm.moveSelectedBy(10f, 0f) }
        vm.endLive()
        val moved = vm.model.value!!.strokes.first { it.id in vm.selection.value }
        assertTrue(moved.points[0] > before.strokes.first { it.id == moved.id }.points[0])
        vm.undo()
        assertEquals(before.strokes, vm.model.value!!.strokes)
        // Kopyala ve sil
        vm.tapSelect(450f, 400f, 24f)
        vm.duplicateSelected()
        assertEquals(3, vm.model.value!!.strokes.size)
        vm.deleteSelected()
        assertEquals(2, vm.model.value!!.strokes.size)
        // Katmanı sil: içindekiler gider
        vm.deleteLayer(l2)
        assertEquals(1, vm.model.value!!.strokes.size)
    }

    @Test
    fun savesAsSketchAndRendersImage() = runBlocking {
        val vm = vm()
        vm.finishStroke(line(300f, 400f, 600f, 420f), null, false)
        vm.addPrimitive(PrimitiveKind.SPHERE)
        vm.save()
        val id = vm.model.value!!.id
        var tries = 0
        while (db.model3DDao().get(id) == null && tries++ < 100) { idle(); Thread.sleep(50) }
        while (vm.dirty.value && tries++ < 150) { idle(); Thread.sleep(20) }
        val row = db.model3DDao().get(id)!!
        assertFalse(com.karapp.drawguidance.content.model3d.Model3DStore.isLegacy(row.data))
        val back = repo.load(id)!!
        assertEquals(vm.model.value!!.strokes, back.strokes)
        assertEquals(1, back.layers.size)
        val bmp = vm.renderImage(ImageExport(size = 256))!!
        assertEquals(256, bmp.width)
        // Yeniden açılınca düzlem ve katman gelir
        val again = vm(id)
        assertEquals(back.plane ?: vm.plane.value, again.plane.value)
    }
}

/** v56 3B Çizim ekranı: mod çubuğu, araçlar, parmakla çizme ve dışa aktarma menüsü. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w1280dp-h800dp-xhdpi")
class V56Sketch3DUiTest : V23EditorBase() {

    @Test
    fun drawSnapPlaneSelectAndLayersOnTablet() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val k = GlobalContext.get()
        val vm = Sketch3DViewModel(k.get(), k.get(), null, k.get(), images = k.get())
        compose.runOnUiThread { compose.activity.setContent { DrawGuidanceTheme(darkTheme = false) { Sketch3DScreen(null, onBack = {}, vm = vm) } } }
        waitFor { exists("sketch3d_screen") && vm.currentView() != null }
        settleFor(600)
        listOf("draw", "plane", "select", "add", "view").forEach { assertTrue(it, exists("sketch3d_mode_$it")) }
        // Parmakla çiz (öntanımlı açık): sürükleme düzleme çizgi ekler
        compose.onNodeWithTag("sketch3d_viewport").performTouchInput {
            down(Offset(width * 0.3f, height * 0.5f))
            repeat(10) { moveTo(Offset(width * (0.3f + it * 0.03f), height * (0.5f - it * 0.01f))) }
            up()
        }
        waitFor { vm.model.value!!.strokes.size == 1 }
        shot("v56-sk3-01-draw")
        // Düzlem modu: Üst hazır yönü
        compose.onNodeWithTag("sketch3d_mode_plane").performClick()
        waitFor { exists("sketch3d_plane_top") }
        compose.onNodeWithTag("sketch3d_plane_top").performClick()
        waitFor { vm.plane.value.id == "top" }
        shot("v56-sk3-02-plane")
        // Ekle: küp
        compose.onNodeWithTag("sketch3d_mode_add").performClick()
        waitFor { exists("sketch3d_add_cube") }
        compose.onNodeWithTag("sketch3d_add_cube").performClick()
        waitFor { vm.model.value!!.parts.size == 1 && vm.mode.value == SketchMode.SELECT }
        // Katmanlar paneli
        compose.onNodeWithTag("sketch3d_layers").performClick()
        waitFor { exists("sketch3d_add_layer") }
        compose.onNodeWithTag("sketch3d_add_layer").performClick()
        waitFor { vm.model.value!!.layers.size == 2 }
        settleFor(400)
        shot("v56-sk3-03-layers")
        // Parmakla çiz kapatılınca tek parmak sahneyi döndürür
        compose.onNodeWithTag("sketch3d_props").performClick()
        waitFor { exists("sketch3d_finger") }
        compose.onNodeWithTag("sketch3d_finger").performClick()
        waitFor { !vm.fingerDraw.value }
        compose.onNodeWithTag("sketch3d_mode_draw").performClick()
        val yaw = vm.camera().yaw
        compose.onNodeWithTag("sketch3d_viewport").performTouchInput {
            down(Offset(width * 0.3f, height * 0.5f)); repeat(8) { moveTo(Offset(width * (0.3f + it * 0.03f), height * 0.5f)) }; up()
        }
        waitFor { vm.camera().yaw != yaw }
        assertEquals(1, vm.model.value!!.strokes.size)
    }
}
