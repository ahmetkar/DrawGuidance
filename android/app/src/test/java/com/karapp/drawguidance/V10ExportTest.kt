package com.karapp.drawguidance

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.ExportOptions
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.GuideKind
import com.karapp.drawguidance.data.model.GuideSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.settings.ExportSettings
import com.karapp.drawguidance.data.settings.ExportSettingsStore
import com.karapp.drawguidance.data.settings.ImageFormat
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * v10 resim kaydetme ayarları (dosya adı, biçim, çözünürlük, sayfalar, kılavuz; albüm ve varsayılanlar Ayarlar'dan)
 * ve eleman kırpmasının saklanması.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V10ExportTest {

    @After fun tearDown() = stopKoin()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun line(id: String, y: Float, order: Int) =
        Stroke(id, listOf(StrokePoint(20f, y), StrokePoint(380f, y)), "#1E1E1E", BrushType.MARKER, 12f, 1f, order)

    private fun notes(pages: Int) = CanvasUi(
        localId = "n1", name = "Ders Notu", width = 400, height = 300, backgroundColorHex = "#FFFFFF", aiEnabled = false,
        pages = (0 until pages).map { listOf(line("p$it", 50f + it * 60f, 0)) }, createdAt = 0, updatedAt = 0,
        kind = CanvasKind.NOTES, guide = GuideSpec(kind = GuideKind.GRID, perspective = 2),
    )

    private fun decodeSize(uri: String): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(Uri.parse(uri).path, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun settingsStoreKeepsFolderAlbumAndDefaults() {
        val store = ExportSettingsStore(context)
        assertEquals(ExportSettings(), store.settings.value)
        assertEquals("Pictures/DrawGuidance", store.settings.value.locationLabel)
        store.setAlbumName("  Çizimlerim / Okul ")
        assertEquals("Çizimlerim _ Okul", store.settings.value.albumName)
        store.setAlbumName("   ")
        assertEquals("boşsa varsayılan", "DrawGuidance", store.settings.value.albumName)
        store.setAlbumName("Çizimlerim")
        store.setDefaultScale(2f)
        store.setDefaultFormat(ImageFormat.JPEG)
        store.setFolder("content://com.android.externalstorage.documents/tree/primary%3APictures%2FOkul", "Pictures/Okul")
        // Yeniden açılınca aynı ayarlar okunur
        val reloaded = ExportSettingsStore(context).settings.value
        assertEquals("Çizimlerim", reloaded.albumName)
        assertEquals(2f, reloaded.defaultScale, 0f)
        assertEquals(ImageFormat.JPEG, reloaded.defaultFormat)
        assertEquals("Pictures/Okul", reloaded.locationLabel)
        store.setFolder(null, null)
        assertEquals("Pictures/Çizimlerim", ExportSettingsStore(context).settings.value.locationLabel)

        assertEquals("Pictures/Okul", ExportSettingsStore.folderLabelOf("primary:Pictures/Okul"))
        assertEquals("SD kart/Resimler", ExportSettingsStore.folderLabelOf("1A2B-3C4D:Resimler"))
        assertEquals("Dahili depolama", ExportSettingsStore.folderLabelOf("primary:"))
    }

    @Test
    fun exportUsesTheChosenNameFormatScalePagesAndAlbum() {
        val store = ExportSettingsStore(context)
        store.setAlbumName("Okul")
        store.setDefaultScale(2f)
        store.setDefaultFormat(ImageFormat.JPEG)
        val exporter = ImageExporter(context, useGallery = false, settings = store)
        val canvas = notes(3)

        // Pencerenin başlangıç ayarları Ayarlar'dan gelir
        val defaults = exporter.defaultOptions(canvas)
        assertEquals(2f, defaults.scale, 0f)
        assertEquals(ImageFormat.JPEG, defaults.format)
        assertFalse("kılavuz varsayılan olarak dahil değil", defaults.includeGuides)
        assertTrue(defaults.fileName.startsWith("Ders Notu_"))
        assertEquals("Pictures/Okul", exporter.locationLabel)

        // Çözünürlük: tuval boyutunun katı, en uzun kenar sınırlı
        assertEquals(800 to 600, exporter.outputSize(400, 300, 2f))
        assertEquals(200 to 150, exporter.outputSize(400, 300, 0.5f))
        assertEquals(ImageExporter.MAX_SIDE to 3072, exporter.outputSize(2000, 1500, 4f))

        // Tüm sayfalar: her sayfa ayrı resim, adlar _s1.._s3, JPEG, 2×, albüm klasörü
        val all = exporter.export(canvas, defaults.copy(fileName = "Hafta 1", allPages = true))
        assertEquals(3, all.size)
        all.forEachIndexed { i, image ->
            // v15: tüm sayfalar tuvalin adını taşıyan tek klasöre
            assertTrue(image.location, image.location.endsWith("Okul/Ders_Notu/Hafta_1_s${i + 1}.jpg"))
            assertEquals(800 to 600, decodeSize(image.uri))
            assertEquals(800, image.width)
            val bytes = File(Uri.parse(image.uri).path!!).readBytes()
            assertTrue("JPEG", bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte())
            assertEquals(bytes.size.toLong(), image.sizeBytes)
        }

        // Yalnızca açık sayfa, PNG, 0,5×
        val single = exporter.export(canvas.copy(currentPage = 1), ExportOptions("Tek", ImageFormat.PNG, scale = 0.5f))
        assertEquals(1, single.size)
        assertTrue(single[0].location.endsWith("Okul/Tek.png"))
        assertEquals(200 to 150, decodeSize(single[0].uri))
        val png = File(Uri.parse(single[0].uri).path!!).readBytes()
        assertTrue("PNG", png[1] == 'P'.code.toByte() && png[2] == 'N'.code.toByte())

        // Kılavuz dahil edilince kağıtta ızgara görünür
        val plain = exporter.render(canvas, 1f, includeGuides = false, page = 0)
        val guided = exporter.render(canvas, 1f, includeGuides = true, page = 0)
        var differs = 0
        for (x in 0 until 400 step 4) for (y in 150 until 300 step 4) if (plain.getPixel(x, y) != guided.getPixel(x, y)) differs++
        assertTrue("kılavuz çizgileri resme çizilmeli: $differs", differs > 20)
        store.setAlbumName("DrawGuidance")
        store.setDefaultScale(1f)
        store.setDefaultFormat(ImageFormat.PNG)
    }

    @Test
    fun elementCropIsStoredAndOnlyHidesTheOutsidePart() {
        val stroke = line("a", 100f, 0).copy(
            clip = listOf(StrokePoint(0f, 0f), StrokePoint(200f, 0f), StrokePoint(200f, 300f), StrokePoint(0f, 300f)),
        )
        val back = ContentCodec.decode(ContentCodec.encode(listOf(stroke, line("b", 200f, 1))))
        assertEquals(stroke.clip, back[0].clip)
        assertNull(back[1].clip)
        assertEquals("çizim silinmez", stroke.points, back[0].points)
        // Görünen kutu kırpma alanıyla sınırlı; dışarıdaki kısım seçilmez
        val bounds = ElementOps.bounds(back[0])!!
        assertTrue("görünen sağ kenar kırpma sınırında: ${bounds.right}", bounds.right <= 200.5f)
        assertTrue(ElementOps.insideClip(back[0], androidx.compose.ui.geometry.Offset(100f, 100f)))
        assertFalse(ElementOps.insideClip(back[0], androidx.compose.ui.geometry.Offset(300f, 100f)))
        // Taşınınca kırpma alanı da taşınır
        val moved = ElementOps.translate(listOf(back[0]), setOf("a"), 50f, 0f).single()
        assertEquals(250f, moved.clip!![1].x, 0.01f)
    }
}
