package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.Samples3D
import com.karapp.drawguidance.content.model3d.TextureCatalog
import com.karapp.drawguidance.content.paper.PaperCatalog
import com.karapp.drawguidance.content.paper.PaperImages
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.PaperLibrary
import com.karapp.drawguidance.data.library.PaperRepository
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.PaperAssetEntity
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BoxFit
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextBox
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.ui.drawing.BackgroundRenderer
import com.karapp.drawguidance.ui.drawing.TextBoxResize
import com.karapp.drawguidance.ui.drawing.TextRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementIndexer
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ZOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v23 veri katmanı ve çizim yardımcıları: arka plan kütüphanesi (desenler sıkıştırılmış, resimler WebP/PNG), 3B
 * dokuları, 3B Şekillerim, arka plan çizimi, yazı kutusu ölçüsü/tutamakları ve eleman sırası.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V23DataTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()

    @After
    fun close() {
        db.close()
        org.koin.core.context.stopKoin()
    }

    @Test
    fun libraryTablesSeedAndLoad() = runBlocking {
        // Arka plan kütüphanesi: 70 desen + 26 resim, desenler sıkıştırılmış ve küçük
        val paper = PaperRepository(db.paperAssetDao())
        val t0 = System.nanoTime()
        paper.seed()
        paper.load()
        val seedMs = (System.nanoTime() - t0) / 1_000_000
        val info = db.paperAssetDao().info()
        assertEquals(PaperCatalog.all.size + PaperImages.all.size, info.size)
        val patterns = db.paperAssetDao().byKind(PaperAssetEntity.PATTERN)
        val patternBytes = patterns.sumOf { it.data.size }
        val images = db.paperAssetDao().byKind(PaperAssetEntity.IMAGE)
        val imageBytes = images.sumOf { it.data.size }
        println("desenler $patternBytes bayt, resimler $imageBytes bayt, tohumlama $seedMs ms")
        assertTrue(patternBytes < 40_000)
        assertTrue(imageBytes < 6_000_000)
        assertEquals(PaperCatalog.all.size, paper.items.value.count { !it.isImage })
        assertNotNull(PaperLibrary.pattern("note.grid5"))
        // Sürüm aynıysa yeniden yazılmaz
        val again = System.nanoTime()
        paper.seed()
        assertTrue((System.nanoTime() - again) / 1_000_000 < 2_000)
        // Resim istenince çözülür (önbellekte "paper:<kimlik>")
        paper.loadImage("box.img.sticky_yellow")
        assertNotNull(CanvasImageCache.get(PaperLibrary.KEY + "box.img.sticky_yellow"))

        // 3B dokuları
        val tex = TextureRepository(db.texture3DDao())
        tex.seed()
        assertEquals(TextureCatalog.all.size, db.texture3DDao().versions().size)
        val oak = tex.texture("wood.oak")
        assertNotNull(oak)
        assertEquals(TextureCatalog.SIZE, oak!!.w)

        // 3B Şekillerim: kayıt, küçük resim, okuma
        val models = Model3DRepository(ApplicationProvider.getApplicationContext(), db.model3DDao(), tex)
        val m = Model3D("m1", "Deneme", listOf(ModelEdit.newPart(PrimitiveKind.CUBE, "p1", 0), ModelEdit.newPart(PrimitiveKind.SPHERE, "p2", 1)))
        models.save(m)
        val back = models.load("m1")
        assertEquals(2, back!!.parts.size)
        val list = models.observe().first()
        assertEquals(1, list.size)
        assertTrue((list[0].thumb?.size ?: 0) > 200)
        val copy = models.saveCopy(back, "Kopya")
        assertEquals(2, models.observe().first().size)
        models.delete(listOf(copy.id))
        assertEquals(1, models.observe().first().size)
        assertTrue(Samples3D.all.isNotEmpty())
    }

    @Test
    fun backgroundDrawingAndTextBox() {
        // Desen (ekranda karo resmiyle) ve resim (döşeme) çizilir
        val bmp = Bitmap.createBitmap(400, 560, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFFFFFF.toInt())
        BackgroundRenderer.drawCanvasBackground(c, BackgroundSpec(patternId = "note.grid5"), 0f, 0f, 400f, 560f, 0.5f, -1, vector = false)
        val px = IntArray(400 * 560)
        bmp.getPixels(px, 0, 400, 0, 0, 400, 560)
        val ink = px.count { it != -1 }
        assertTrue("desen çizildi ($ink)", ink > 2_000)
        // Vektör çizim de aynı yerde çizgi bırakır
        val v = Bitmap.createBitmap(400, 560, Bitmap.Config.ARGB_8888)
        Canvas(v).apply { drawColor(-1) }.also { BackgroundRenderer.drawCanvasBackground(it, BackgroundSpec(patternId = "note.grid5"), 0f, 0f, 400f, 560f, 0.5f, -1, vector = true) }
        val vp = IntArray(400 * 560); v.getPixels(vp, 0, 400, 0, 0, 400, 560)
        assertTrue(vp.count { it != -1 } > 2_000)

        // Yazı kutusu: genişlik verilince satırlar kaydırılır, en az yükseklik korunur
        val spec = TextSpec("Bu uzun bir not satırıdır ve kutuya sığması için kaydırılmalıdır")
        val (w0, h0) = TextRenderer.measure(spec, 32f)
        val boxed = spec.copy(box = TextBox(width = 260f, height = 400f, fillHex = "#FFF7D6"))
        val (w1, h1) = TextRenderer.measure(boxed, 32f)
        assertEquals(260f, w1, 0.5f)
        assertTrue(w0 > w1)
        assertTrue(h1 >= 400f)
        val (_, h2) = TextRenderer.measure(spec.copy(box = TextBox(width = 260f)), 32f)
        assertTrue("kaydırılan yazı uzar: $h2 > $h0", h2 > h0 * 1.5f)
        // Hazır resimli kutuda yazı resmin iç boşluğundan başlar
        val pads = TextRenderer.pads(spec.copy(box = TextBox(imageId = "box.img.spiral", imageFit = BoxFit.NINE_SLICE)), 32f)
        assertTrue(pads[1] >= 50f)

        // Tutamakla genişletme: sağ kenar eni, alt kenar boyu
        val stroke = Stroke("t", TextRenderer.quadAt(boxed, 32f, 100f, 100f), "#000000", BrushType.TEXT, 32f, 1f, 0, text = boxed)
        val handles = TextBoxResize.handles(stroke)
        assertEquals(3, handles.size)
        val wider = TextBoxResize.resize(stroke, TextBoxResize.WIDTH, androidx.compose.ui.geometry.Offset(100f + 520f, 300f))!!
        assertEquals(520f, wider.text!!.box!!.width!!, 1f)
        assertEquals(620f, wider.points[1].x, 1f)
        val taller = TextBoxResize.resize(stroke, TextBoxResize.HEIGHT, androidx.compose.ui.geometry.Offset(200f, 100f + 700f))!!
        assertEquals(700f, taller.text!!.box!!.height!!, 1f)
    }

    @Test
    fun zOrderMovesElementsWithinLayer() {
        fun line(id: String, y: Float) = Stroke(id, listOf(StrokePoint(0f, y), StrokePoint(100f, y)), "#000000", BrushType.PENCIL, 4f, 1f, 0)
        val strokes = listOf(line("a", 0f), line("b", 100f), line("c", 200f))
        val index = ElementIndexer().update(strokes)
        val layers = listOf(Layer.Default)
        fun ids(l: List<Stroke>) = l.joinToString("") { it.id }
        assertEquals("bca", ids(ZOrder.reorder(strokes, setOf("a"), index, ZOrder.FRONT, layers)))
        assertEquals("cab", ids(ZOrder.reorder(strokes, setOf("c"), index, ZOrder.BACK, layers)))
        assertEquals("bac", ids(ZOrder.reorder(strokes, setOf("a"), index, ZOrder.FORWARD, layers)))
        assertEquals("acb", ids(ZOrder.reorder(strokes, setOf("c"), index, ZOrder.BACKWARD, layers)))
        // En üstteki yukarı çıkamaz
        assertTrue(ZOrder.reorder(strokes, setOf("c"), index, ZOrder.FORWARD, layers) === strokes)
    }
}
