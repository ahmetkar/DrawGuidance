package com.karapp.drawguidance

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.CbzOptions
import com.karapp.drawguidance.data.images.CmykFormat
import com.karapp.drawguidance.data.images.ExportOptions
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.PdfOptions
import com.karapp.drawguidance.data.images.SaveTarget
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramNode
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.PageAlign
import com.karapp.drawguidance.data.model.PageSettings
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.print.CmykOptions
import com.karapp.drawguidance.data.settings.ExportSettingsStore
import com.karapp.drawguidance.data.settings.ImageFormat
import com.karapp.drawguidance.ui.drawing.DiagramContrast
import com.karapp.drawguidance.ui.drawing.DiagramPainter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * v40 dışa aktarma: CMYK JPEG/TIFF/PDF, "Farklı kaydet" (tek dosya) ve hatırlanan klasör, çizgi roman CBZ'si, sayfa
 * numarası/üst/alt bilgisinin çıktılara çizilmesi, diyagram yazısının koyu dolguda okunurluğu.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V40ExportTest {

    @After fun tearDown() = stopKoin()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exporter by lazy { ImageExporter(context, useGallery = false, settings = ExportSettingsStore(context)) }

    private fun line(id: String, y: Float, color: String = "#C62828") =
        Stroke(id, listOf(StrokePoint(20f, y), StrokePoint(380f, y)), color, BrushType.MARKER, 14f, 1f, 0)

    private fun canvas(pages: Int, kind: CanvasKind = CanvasKind.COMIC, settings: PageSettings = PageSettings()) = CanvasUi(
        localId = "c1", name = "Kahraman", width = 400, height = 600, backgroundColorHex = "#FFFFFF", aiEnabled = false,
        pages = (0 until pages).map { listOf(line("p$it", 100f + it * 60f)) }, createdAt = 0, updatedAt = 0, kind = kind,
        pageSettings = settings,
    )

    private fun tempDocument(name: String): SaveTarget.Document {
        val file = File(context.cacheDir, name).apply { delete() }
        return SaveTarget.Document(Uri.fromFile(file).toString(), name)
    }

    private fun bytesOf(uri: String) = File(Uri.parse(uri).path!!).readBytes()

    @Test
    fun cmykJpegAndTiffAreWrittenToTheChosenFile() {
        val c = canvas(1)
        val jpegTarget = tempDocument("baski.jpg")
        val jpeg = exporter.export(c, ExportOptions("baski", cmyk = CmykOptions(), cmykFormat = CmykFormat.JPEG, target = jpegTarget)).single()
        assertEquals(jpegTarget.uri, jpeg.uri)
        assertEquals("baski.jpg", jpeg.location)
        val bytes = bytesOf(jpeg.uri)
        assertTrue(bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte())
        assertTrue("Adobe APP14", String(bytes, Charsets.ISO_8859_1).contains("Adobe"))
        // Android (Skia) CMYK JPEG'i çözebilir: Resimlerim'de görünür
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(400, decoded.width)
        // Varsayılan yerde CMYK TIFF
        val tiff = exporter.export(c, ExportOptions("baski", cmyk = CmykOptions(), cmykFormat = CmykFormat.TIFF)).single()
        assertTrue(tiff.location, tiff.location.endsWith("baski.tif"))
        val t = bytesOf(tiff.uri)
        assertTrue(t[0] == 'I'.code.toByte() && t[1] == 'I'.code.toByte() && t[2] == 42.toByte())
    }

    @Test
    fun cmykPdfHasOnePagePerCanvasPage() {
        val c = canvas(3, CanvasKind.ART)
        val target = tempDocument("baski.pdf")
        val file = exporter.exportCmykPdf(c, ExportOptions("baski", allPages = true, cmyk = CmykOptions(), cmykFormat = CmykFormat.PDF, target = target))
        assertEquals(target.uri, file.uri)
        val text = String(bytesOf(file.uri), Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("/Count 3"))
        assertEquals(3, Regex("/ColorSpace /DeviceCMYK").findAll(text).count())
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        // PDF penceresinden de (not alma / çizgi roman) CMYK seçilebilir; varsayılan yerde (belgeler klasörü)
        val notes = exporter.exportPdf(canvas(2, CanvasKind.SIMPLE_NOTES), PdfOptions("not", cmyk = CmykOptions(), pageNumbers = true))
        assertTrue(notes.location, notes.location.endsWith("not.pdf"))
        assertTrue(String(bytesOf(notes.uri), Charsets.ISO_8859_1).contains("/Count 2"))
        assertEquals(File(Uri.parse(notes.uri).path!!).length(), notes.sizeBytes)
    }

    @Test
    fun comicCbzHasEveryPageAndComicInfo() {
        val c = canvas(4)
        val file = exporter.exportCbz(c, CbzOptions("Roman", format = ImageFormat.PNG, scale = 0.5f))
        assertTrue(file.location, file.location.endsWith("Roman.cbz"))
        val names = ArrayList<String>()
        var info = ""
        ZipInputStream(ByteArrayInputStream(bytesOf(file.uri))).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                names += e.name
                val b = zip.readBytes()
                if (e.name.endsWith(".png")) assertEquals(200, BitmapFactory.decodeByteArray(b, 0, b.size).width)
                else info = String(b)
            }
        }
        assertEquals(listOf("00001.png", "00002.png", "00003.png", "00004.png", "ComicInfo.xml"), names)
        assertTrue(info.contains("<PageCount>4</PageCount>"))
        assertTrue(info.contains("<Title>Kahraman</Title>"))
        // Aralık ve "Farklı kaydet"
        val target = tempDocument("aralik.cbz")
        exporter.exportCbz(c, CbzOptions("x", pages = com.karapp.drawguidance.data.images.PdfPages.RANGE, fromPage = 2, toPage = 3, target = target))
        ZipInputStream(ByteArrayInputStream(bytesOf(target.uri))).use { zip ->
            assertEquals(listOf("00001.jpg", "00002.jpg", "ComicInfo.xml"), generateSequence { zip.nextEntry?.name }.toList())
        }
    }

    @Test
    fun pageNumberHeaderAndFooterAreDrawnInExports() {
        val plain = exporter.render(canvas(3), 1f, page = 1)
        val settings = PageSettings(pageNumbers = true, header = "BÖLÜM BİR", headerAlign = PageAlign.LEFT, footer = "Okul Defteri")
        val decorated = exporter.render(canvas(3, settings = settings), 1f, page = 1)
        fun inked(y0: Int, y1: Int, x0: Int = 0, x1: Int = 400): Int {
            var n = 0
            for (y in y0 until y1) for (x in x0 until x1) if (plain.getPixel(x, y) != decorated.getPixel(x, y)) n++
            return n
        }
        assertTrue("üst bilgi solda", inked(0, 40, 0, 200) > 20)
        assertTrue("üst bilgi ortada değil", inked(0, 40, 300, 400) == 0)
        assertTrue("alt bilgi ve numara altta", inked(560, 600) > 20)
        assertEquals("ortada boş", 0, inked(200, 400))
    }

    @Test
    fun diagramTextOnDarkFillIsReadable() {
        val nodes = listOf(
            DiagramNode("a", "Koyu", 0.1f, 0.1f, 0.3f, 0.2f, fillColorHex = "#1B1B2F"),
            DiagramNode("b", "Açık", 0.5f, 0.1f, 0.3f, 0.2f, fillColorHex = "#FFF8E1"),
            DiagramNode("c", "Elle", 0.1f, 0.5f, 0.3f, 0.2f, fillColorHex = "#1B1B2F", textColorHex = "#333333"),
        )
        // Klasik tema, koyu kalem rengiyle dolgu (eskiden yazı da aynı renkteydi)
        val classic = DiagramSpec(DiagramKind.FLOW, nodes + DiagramNode("d", "Kalem", 0.5f, 0.5f, 0.3f, 0.2f), style = ShapeStyle("#202020", fillColorHex = "#202020"))
        val bmp = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
        val x = DiagramPainter.Ctx(android.graphics.Canvas(bmp), classic, DiagramPainter.palette(classic, false), 1f, true)
        fun look(i: Int) = DiagramPainter.lookOf(x, i, classic.nodes[i])
        assertTrue(DiagramContrast.ratio(look(0).text or (0xFF shl 24), look(0).fill!!) >= DiagramContrast.MIN_RATIO)
        assertEquals(0xFF202020.toInt(), look(1).text)
        assertEquals("elle seçilen yazı rengi korunur", 0xFF333333.toInt(), look(2).text)
        assertEquals(0xFFFFFFFF.toInt(), look(3).text)
        // Zeminsiz klasik: kalemin dolgusu çizilmez, yazı kalem rengi
        val clear = classic.copy(theme = "_clear", nodes = listOf(DiagramNode("d", "Kalem", 0.5f, 0.5f, 0.3f, 0.2f)))
        val cx = DiagramPainter.Ctx(android.graphics.Canvas(bmp), clear, DiagramPainter.palette(clear, false), 1f, true)
        val l = DiagramPainter.lookOf(cx, 0, clear.nodes[0])
        assertEquals(null, l.fill)
        assertEquals(0xFF202020.toInt(), l.text)
    }
}
