package com.karapp.drawguidance

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.PdfLayout
import com.karapp.drawguidance.data.images.PdfMargin
import com.karapp.drawguidance.data.images.PdfOptions
import com.karapp.drawguidance.data.images.PdfOrientation
import com.karapp.drawguidance.data.images.PdfPages
import com.karapp.drawguidance.data.images.PdfPaper
import com.karapp.drawguidance.data.images.PdfQuality
import com.karapp.drawguidance.data.images.PdfSink
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v19 PDF ayarları: sayfa seçimi ve sayfa yerleşimi (kağıt, yön, kenar boşluğu, sayfa numarası şeridi). */
class V19PdfLayoutTest {
    private fun opts(block: PdfOptions.() -> PdfOptions = { this }) = PdfOptions("x").block()

    @Test
    fun pageSelection() {
        assertEquals(listOf(0, 1, 2, 3), opts().pageIndices(4, 2))
        assertEquals(listOf(2), opts { copy(pages = PdfPages.CURRENT) }.pageIndices(4, 2))
        assertEquals(listOf(1, 2), opts { copy(pages = PdfPages.RANGE, fromPage = 2, toPage = 3) }.pageIndices(4, 0))
        // Ters aralık düzeltilir, taşan uçlar kırpılır
        assertEquals(listOf(1, 2, 3), opts { copy(pages = PdfPages.RANGE, fromPage = 9, toPage = 2) }.pageIndices(4, 0))
        assertEquals(listOf(0), opts { copy(pages = PdfPages.RANGE, fromPage = 0, toPage = 1) }.pageIndices(4, 0))
    }

    @Test
    fun canvasPaperKeepsCanvasRatioAndAddsMargins() {
        val plain = PdfLayout.of(1240, 1754, opts())
        assertEquals(595, plain.pageWidth)
        assertEquals(842, plain.pageHeight)
        assertEquals(0f, plain.left)
        val margin = PdfLayout.of(1240, 1754, opts { copy(margin = PdfMargin.NORMAL) })
        assertEquals(595 + 72, margin.pageWidth)
        assertEquals(842 + 72, margin.pageHeight)
        assertEquals(36f, margin.left)
        assertEquals(36f, margin.top)
        // Yatay tuval: kısa kenar (yükseklik) A4 genişliği
        val wide = PdfLayout.of(1600, 900, opts())
        assertEquals(595, wide.pageHeight)
        assertEquals(1058, wide.pageWidth)
        // Sayfa numarası: kenar boşluğu yoksa altta şerit ayrılır
        val numbered = PdfLayout.of(1240, 1754, opts { copy(pageNumbers = true) })
        assertEquals(842 + PdfLayout.FOOTER.toInt(), numbered.pageHeight)
        assertEquals(PdfLayout.FOOTER, numbered.footer)
    }

    @Test
    fun fixedPaperFitsAndCentersTheCanvas() {
        // Otomatik yön: yatay tuval yatay A4'e
        val auto = PdfLayout.of(1600, 900, opts { copy(paper = PdfPaper.A4) })
        assertEquals(842, auto.pageWidth)
        assertEquals(595, auto.pageHeight)
        assertEquals(842f / 1600f, auto.scale, 1e-4f)
        assertEquals(0f, auto.left, 1e-3f)
        assertEquals((595f - 900f * auto.scale) / 2f, auto.top, 1e-3f)
        // Dikey zorlanınca genişliğe sığar, dikeyde ortalanır
        val portrait = PdfLayout.of(1600, 900, opts { copy(paper = PdfPaper.A4, orientation = PdfOrientation.PORTRAIT) })
        assertEquals(595, portrait.pageWidth)
        assertEquals(842, portrait.pageHeight)
        assertEquals(595f / 1600f, portrait.scale, 1e-4f)
        assertTrue(portrait.top > 200f)
        // Kenar boşluğu içeride kalır
        val letter = PdfLayout.of(1000, 1000, opts { copy(paper = PdfPaper.LETTER, margin = PdfMargin.NORMAL, orientation = PdfOrientation.LANDSCAPE) })
        assertEquals(792, letter.pageWidth)
        assertEquals(612, letter.pageHeight)
        assertEquals((612f - 72f) / 1000f, letter.scale, 1e-4f)
        assertEquals(36f, letter.top, 1e-3f)
        assertEquals((792f - 1000f * letter.scale) / 2f, letter.left, 1e-3f)
        val a5 = PdfLayout.of(1240, 1754, opts { copy(paper = PdfPaper.A5) })
        assertEquals(420 to 595, a5.pageWidth to a5.pageHeight)
    }
}

/**
 * Testte PDF yazıcısı: her sayfa (nokta boyutunda) bir resme çizilir; sayfaya tam boy resim olarak yazılan içerik
 * (resim kalitesi ya da silgili sayfa) [Page.images] ile izlenir.
 */
class BitmapPdfSink : PdfSink {
    class Page(val width: Int, val height: Int, val bitmap: android.graphics.Bitmap, val images: MutableList<android.graphics.RectF>)

    val pages = mutableListOf<Page>()

    override fun startPage(width: Int, height: Int, number: Int): android.graphics.Canvas {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val page = Page(width, height, bitmap, mutableListOf())
        pages += page
        return object : android.graphics.Canvas(bitmap) {
            override fun drawBitmap(bitmap: android.graphics.Bitmap, src: android.graphics.Rect?, dst: android.graphics.RectF, paint: android.graphics.Paint?) {
                page.images += android.graphics.RectF(dst)
                super.drawBitmap(bitmap, src, dst, paint)
            }
        }
    }

    override fun finishPage() = Unit
    override fun finish(): ByteArray = ("%PDF-test " + pages.joinToString(" ") { "${it.width}x${it.height}" }).toByteArray()
    override fun close() = Unit

    companion object {
        @Volatile var last: BitmapPdfSink? = null
        /** [ImageExporter.pdfSinkFactory] olarak: her belge için yeni yazıcı, sonuncusu [last]. */
        fun install() {
            ImageExporter.pdfSinkFactory = { BitmapPdfSink().also { last = it } }
        }
        fun uninstall() {
            ImageExporter.pdfSinkFactory = { com.karapp.drawguidance.data.images.AndroidPdfSink() }
            last = null
        }
    }
}

/** v19 PDF sayfaları: seçilen sayfalar, kağıt boyu, kenar boşluğu ve kağıt rengi, sayfa numarası, keskin/resim. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class V19PdfRenderTest {

    private fun line(id: String, y: Float, type: BrushType = BrushType.INK_PEN) =
        Stroke(id, (0..20).map { StrokePoint(100f + it * 40f, y) }, "#1E1E1E", type, 12f, 1f, 0)

    private fun canvas(pages: List<List<Stroke>>, background: String = "#FFF8E1") = CanvasUi(
        "c", "Notlar", 1240, 1754, background, false, pages, 0L, 0L, kind = CanvasKind.NOTES,
    )

    private fun render(c: CanvasUi, options: PdfOptions): BitmapPdfSink {
        val exporter = ImageExporter(ApplicationProvider.getApplicationContext(), useGallery = false)
        exporter.renderPdf(c, options)
        return BitmapPdfSink.last!!
    }

    /** Koyu (mürekkep) piksel sayısı. */
    private fun ink(b: android.graphics.Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val p = b.getPixel(x, y)
            if (android.graphics.Color.red(p) < 110 && android.graphics.Color.green(p) < 110) n++
        }
        return n
    }

    @org.junit.Before fun setUp() = BitmapPdfSink.install()
    @org.junit.After fun tearDown() = BitmapPdfSink.uninstall()

    @Test
    fun rangePaperMarginsBackgroundAndPageNumbers() {
        val c = canvas(listOf(listOf(line("a", 300f)), listOf(line("b", 877f)), listOf(line("c", 1400f))))
        val all = render(c, PdfOptions("t"))
        assertEquals(3, all.pages.size)
        assertEquals(listOf(595 to 842), all.pages.map { it.width to it.height }.distinct())
        // Arka plan açık: kağıt tuvalin renginde
        assertEquals(android.graphics.Color.parseColor("#FFF8E1"), all.pages[0].bitmap.getPixel(5, 5))

        val options = PdfOptions(
            "t", pages = PdfPages.RANGE, fromPage = 2, toPage = 3, paper = PdfPaper.A4,
            orientation = PdfOrientation.LANDSCAPE, margin = PdfMargin.NORMAL, pageNumbers = true, background = false,
        )
        val range = render(c, options)
        assertEquals(2, range.pages.size)
        assertEquals(listOf(842 to 595), range.pages.map { it.width to it.height }.distinct())
        val page = range.pages[0]
        val layout = PdfLayout.of(1240, 1754, options)
        // Beyaz kağıt; tuval dikeyde sığıp yatay kağıdın ortasında
        assertEquals(android.graphics.Color.WHITE, page.bitmap.getPixel(5, 5))
        assertEquals(36f, layout.top, 0.01f)
        val left = layout.left.toInt()
        val right = (layout.left + 1240 * layout.scale).toInt()
        assertTrue("içerik ortada: $left..$right", left > 200 && kotlin.math.abs((left + right) / 2 - 421) <= 2)
        // 2. sayfanın çizgisi (tuvalin ortası) sayfanın ortasında
        val midY = (layout.top + 877 * layout.scale).toInt()
        assertTrue("çizgi var", ink(page.bitmap, left, midY - 4, right, midY + 4) > 50)
        assertEquals("kenar boşluğunda çizim yok", 0, ink(page.bitmap, 0, 0, left - 2, 595 - 40))
        // Sayfa numarası: alt şeridin ortasında gri yazı
        var grey = 0
        for (y in 595 - 34 until 595 - 4) for (x in 842 / 2 - 30 until 842 / 2 + 30) {
            if (page.bitmap.getPixel(x, y) != android.graphics.Color.WHITE) grey++
        }
        assertTrue("sayfa numarası yazıldı", grey > 10)
    }

    @Test
    fun sharpPagesAreVectorImagePagesAreBitmaps() {
        val c = canvas(listOf(listOf(line("a", 300f), line("b", 600f))))
        val sharp = render(c, PdfOptions("t"))
        assertTrue("keskin sayfada tam boy resim yok", sharp.pages.single().images.isEmpty())
        val image = render(c, PdfOptions("t", quality = PdfQuality.IMAGE, imageScale = 1f))
        assertEquals("resim sayfası", 1, image.pages.single().images.size)
        assertEquals(595f, image.pages.single().images.single().width(), 0.5f)
        // Silgili sayfa keskin kalitede de resim olur
        val erased = canvas(listOf(listOf(line("a", 300f), line("e", 300f, BrushType.ERASER))))
        assertEquals(1, render(erased, PdfOptions("t")).pages.single().images.size)
        // Kaydedilen dosya (uygulamanın belgeler klasörü)
        val exporter = ImageExporter(ApplicationProvider.getApplicationContext(), useGallery = false)
        val file = exporter.exportPdf(c, PdfOptions("v19 deneme", paper = PdfPaper.A5))
        assertTrue(file.location, file.location.endsWith("v19_deneme.pdf"))
        assertEquals("%PDF-test 420x595", String(java.io.File(file.location).readBytes()))
    }
}
