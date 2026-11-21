package com.karapp.drawguidance

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.model3d.Brush3D
import com.karapp.drawguidance.content.model3d.MaterialKind
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.RenderStyle
import com.karapp.drawguidance.content.model3d.Sketch3Fill
import com.karapp.drawguidance.data.clipboard.CanvasImageInbox
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.ui.screens.sketch3d.Sketch3DScreen
import com.karapp.drawguidance.ui.screens.sketch3d.Sketch3DViewModel
import com.karapp.drawguidance.ui.screens.sketch3d.SketchEvent
import com.karapp.drawguidance.ui.screens.sketch3d.SketchMode
import com.karapp.drawguidance.ui.screens.sketch3d.SketchTool
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * v56 aşama 12–13 3B Çizim: dolgu (dokunma ve kement), silginin dolguyu kaldırması, yüzeye boya, Parlat, görünüm
 * stili, tuvale ekle ve dönen GIF.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56Sketch3DK6ViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun close() {
        db.close()
        org.koin.core.context.stopKoin()
    }

    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idle() }

    private fun vm(): Sketch3DViewModel {
        val tex = TextureRepository(db.texture3DDao())
        val repo = Model3DRepository(context, db.model3DDao(), tex)
        val images = ImagesRepository(db.savedImageDao(), ImageExporter(context, useGallery = false), CanvasAssetStore(db.canvasAssetDao()))
        val vm = Sketch3DViewModel(repo, tex, null, images = images)
        idle()
        vm.setViewport(900, 900)
        return vm
    }

    private fun ring(cx: Float, cy: Float, r: Float, n: Int = 36): FloatArray =
        FloatArray((n + 1) * 2) { i -> val a = (i / 2) * 2.0 * Math.PI / n; if (i % 2 == 0) (cx + r * cos(a)).toFloat() else (cy + r * sin(a)).toFloat() }

    @Test
    fun fillTapInsideClosedStrokeAndLassoAndEraser() {
        val vm = vm()
        vm.finishStroke(ring(450f, 450f, 150f), null, straight = false)
        vm.setTool(SketchTool.FILL)
        vm.setColor(0xFFE53935.toInt())
        // Dışarıya dokunma doldurmaz
        assertNull(vm.fillAt(50f, 50f))
        val id = vm.fillAt(450f, 450f)!!
        val fill = vm.model.value!!.parts.single { it.id == id }
        assertTrue(Sketch3Fill.isFill(fill))
        assertEquals(0xFFE53935.toInt(), fill.material.color)
        assertEquals(vm.plane.value.origin.z, fill.position.z, 1e-3f)
        // Tek geri alma adımı
        vm.undo(); assertTrue(vm.model.value!!.parts.isEmpty())
        vm.redo(); assertEquals(1, vm.model.value!!.parts.size)
        // Kement: düzlemde yeni dolgu
        val lasso = vm.fillLasso(ring(250f, 700f, 80f))!!
        assertEquals(2, vm.model.value!!.parts.size)
        assertNull("çok küçük kement", vm.fillLasso(ring(100f, 100f, 3f)))
        // Silgi dolguyu kaldırır
        vm.setTool(SketchTool.ERASER)
        vm.beginErase(); vm.eraseAt(250f, 700f, 10f); vm.endErase()
        assertTrue(vm.model.value!!.parts.none { it.id == lasso })
        assertEquals(1, vm.model.value!!.parts.size)
    }

    @Test
    fun widePaintFollowsSurfaceAndGlowAndStyle() {
        val vm = vm()
        vm.addPrimitive(PrimitiveKind.CUBE)
        vm.presetView(1)
        val cube = vm.model.value!!.parts.single()
        val c = vm.currentView()!!.project(com.karapp.drawguidance.content.model3d.ModelEdit.worldBounds(cube).center)
        vm.setBrush(Brush3D.WIDE)
        val xy = FloatArray(20 * 2) { i -> if (i % 2 == 0) c[0] - 300f + (i / 2) * 32f else c[1] }
        vm.finishStroke(xy, null, straight = false)
        val painted = vm.model.value!!.strokes
        assertTrue(painted.isNotEmpty())
        val front = com.karapp.drawguidance.content.model3d.ModelEdit.worldBounds(cube).max.z
        painted.forEach { s ->
            assertEquals(Sketch3DViewModel.SURFACE_PLANE, s.plane)
            for (i in 0 until s.count) assertEquals(front, s.points[3 * i + 2], 0.02f)
        }
        // Yüzey yoksa düzleme çizer
        val off = vm.finishStroke(FloatArray(10 * 2) { i -> if (i % 2 == 0) 20f + i * 4f else 860f }, null, false)!!
        assertTrue(vm.model.value!!.strokes.single { it.id == off }.plane != Sketch3DViewModel.SURFACE_PLANE)
        // Parlat
        vm.setMode(SketchMode.SELECT)
        vm.selectAll()
        vm.materialSelected(MaterialKind.GLOW)
        assertEquals(MaterialKind.GLOW, vm.model.value!!.parts.single().material.kind)
        // Stil
        vm.setStyle(RenderStyle.TOON); assertEquals(RenderStyle.TOON, vm.style.value)
        vm.setStyle(99); assertEquals(RenderStyle.LINES, vm.style.value)
    }

    @Test
    fun addToCanvasPostsPngAndGifVideoIsWritten() {
        val vm = vm()
        vm.addPrimitive(PrimitiveKind.SPHERE)
        val events = ArrayList<SketchEvent>()
        val job = vm.viewModelScopeForTest().launch { vm.events.collect { events += it } }
        vm.addToCanvas("canvas-1", side = 128)
        for (i in 0 until 200) { idle(); Thread.sleep(20); if (CanvasImageInbox.has("canvas-1")) break }
        val png = CanvasImageInbox.take("canvas-1")
        assertNotNull(png)
        val bmp = android.graphics.BitmapFactory.decodeByteArray(png, 0, png!!.size)
        assertEquals(128, maxOf(bmp.width, bmp.height))
        val dir = java.io.File(context.cacheDir, "models3d/video-test").apply { deleteRecursively() }
        vm.exportVideo(gif = true, speed = 2f, outDir = dir)
        for (i in 0 until 1500) { idle(); Thread.sleep(20); if (vm.videoProgress.value == null && dir.listFiles().orEmpty().isNotEmpty()) break }
        val gif = dir.listFiles()!!.single()
        assertTrue(gif.name.endsWith(".gif"))
        val bytes = gif.readBytes()
        assertEquals("GIF89a", String(bytes, 0, 6))
        idle()
        assertTrue(events.any { it is SketchEvent.AddedToCanvas && it.canvasId == "canvas-1" })
        assertTrue(events.any { it is SketchEvent.VideoSaved })
        job.cancel()
    }
}

private fun Sketch3DViewModel.viewModelScopeForTest() = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)

/** v56 aşama 13 telefon düzeni: dar ekranda mod çubuğu, araç satırı, Görünüm stilleri, dolgu ve video penceresi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w360dp-h740dp-xxhdpi")
class V56Sketch3DPhoneUiTest : V23EditorBase() {

    @Test
    fun phoneLayoutStylesFillAndVideoSheet() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val k = GlobalContext.get()
        val vm = Sketch3DViewModel(k.get(), k.get(), null, k.get(), images = k.get())
        compose.runOnUiThread { compose.activity.setContent { DrawGuidanceTheme(darkTheme = false) { Sketch3DScreen(null, onBack = {}, vm = vm) } } }
        waitFor { exists("sketch3d_screen") && vm.currentView() != null }
        settleFor(600)
        listOf("draw", "plane", "select", "add", "view").forEach { assertTrue(it, exists("sketch3d_mode_$it")) }
        assertTrue(exists("sketch3d_tools"))
        // Kapalı çizgi çiz, Doldur aracıyla içine dokun
        compose.onNodeWithTag("sketch3d_viewport").performTouchInput {
            val cx = width * 0.5f; val cy = height * 0.45f; val r = width * 0.25f
            down(Offset(cx + r, cy))
            for (i in 1..36) { val a = i * 2 * Math.PI / 36; moveTo(Offset((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat())) }
            up()
        }
        waitFor { vm.model.value!!.strokes.size == 1 }
        compose.onNodeWithTag("sketch3d_fill").performScrollTo().performClick()
        waitFor { vm.tool.value == SketchTool.FILL }
        compose.onNodeWithTag("sketch3d_viewport").performTouchInput { down(Offset(width * 0.5f, height * 0.45f)); up() }
        waitFor { vm.model.value!!.parts.size == 1 }
        shot("v56-sk3-04-phone-fill")
        // Görünüm: toon stili
        compose.onNodeWithTag("sketch3d_mode_view").performClick()
        waitFor { exists("sketch3d_style_${RenderStyle.TOON}") }
        compose.onNodeWithTag("sketch3d_style_${RenderStyle.TOON}").performScrollTo().performClick()
        waitFor { vm.style.value == RenderStyle.TOON }
        settleFor(500)
        shot("v56-sk3-05-phone-toon")
        // Menü: dönen video penceresi
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitFor { exists("sketch3d_video") }
        compose.onNodeWithTag("sketch3d_video").performClick()
        waitFor { exists("sketch3d_video_sheet") }
        assertTrue(exists("sketch3d_video_gif") && exists("sketch3d_video_speed_2.0"))
        shot("v56-sk3-06-phone-video")
    }
}
