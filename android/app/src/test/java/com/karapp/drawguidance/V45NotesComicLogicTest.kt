package com.karapp.drawguidance

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.CbzOptions
import com.karapp.drawguidance.data.images.ExportOptions
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.PdfLayout
import com.karapp.drawguidance.data.images.PdfOptions
import com.karapp.drawguidance.data.images.PdfSink
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.PageAlign
import com.karapp.drawguidance.data.model.PageBackground
import com.karapp.drawguidance.data.model.PageNumberFormat
import com.karapp.drawguidance.data.model.PageNumbering
import com.karapp.drawguidance.data.model.PageSettings
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.repository.decodePageBackgrounds
import com.karapp.drawguidance.data.repository.decodePageSettings
import com.karapp.drawguidance.data.repository.encodeBackground
import com.karapp.drawguidance.data.repository.withPageSettings
import com.karapp.drawguidance.data.settings.ExportSettingsStore
import com.karapp.drawguidance.data.settings.ImageFormat
import com.karapp.drawguidance.ui.drawing.PageDecorRenderer
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.drawing.SimpleCatalog
import com.karapp.drawguidance.ui.screens.canvasedit.state.ComicController
import com.karapp.drawguidance.ui.screens.canvasedit.state.ComicCover
import com.karapp.drawguidance.ui.screens.canvasedit.state.NotesPanels
import com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.min

/** v45 testlerinde kullanılan küçük yardımcılar. */
internal object V45Fixtures {
    fun line(id: String, y: Float, color: String = "#C62828") =
        Stroke(id, listOf(StrokePoint(40f, y), StrokePoint(360f, y)), color, BrushType.MARKER, 14f, 1f, 0)

    /** [pages] sayfalık çizgi roman; [cover] ise ilk sayfa kapak (kapak bayrağı sayfa arka planında). */
    fun comic(pages: Int, cover: Boolean, settings: PageSettings = PageSettings(), kind: CanvasKind = CanvasKind.COMIC) = CanvasUi(
        localId = "c45", name = "Kahraman", width = 400, height = 600, backgroundColorHex = "#FFFFFF", aiEnabled = false,
        pages = (0 until pages).map { listOf(line("p$it", 200f + it * 40f)) }, createdAt = 0, updatedAt = 0, kind = kind,
        pageSettings = settings,
        pageBackgrounds = if (cover) listOf(PageBackground(cover = true)) else emptyList(),
    )

    /** Numara, üst bilgi ve alt bilgi; numara "n / toplam" ve alt bilgiyle aynı hizada değil (ayrı satır). */
    val fullSettings = PageSettings(
        pageNumbers = true, numberAlign = PageAlign.RIGHT, numberFormat = PageNumberFormat.NUMBER_OF_TOTAL,
        header = "BÖLÜM BİR", headerAlign = PageAlign.LEFT, footer = "Okul", footerAlign = PageAlign.CENTER,
    )
}

/**
 * v45 madde 2 — kapak sayfasının numaralandırma kuralları (saf mantık): kapak numara, üst ve alt bilgi almaz,
 * numaralar kapaktan sonraki sayfada 1'den başlar, toplam kapağı saymaz; kapaksız (eski) tuvallerde hiçbir şey
 * değişmez.
 */
class V45CoverNumberingTest {

    @Test
    fun pageNumberingSkipsTheCover() {
        assertNull(PageNumbering.number(0, cover = true))
        assertEquals(1, PageNumbering.number(1, cover = true))
        assertEquals(2, PageNumbering.number(2, cover = true))
        assertEquals(9, PageNumbering.number(9, cover = true))
        // Kapaksız: sıra + 1 (eski davranış)
        assertEquals(1, PageNumbering.number(0, cover = false))
        assertEquals(10, PageNumbering.number(9, cover = false))
        assertEquals(3, PageNumbering.count(4, cover = true))
        assertEquals(4, PageNumbering.count(4, cover = false))
        assertEquals(0, PageNumbering.count(1, cover = true))
        assertEquals(0, PageNumbering.count(0, cover = true))
    }

    @Test
    fun numberTextInBothFormats() {
        val plain = PageSettings(pageNumbers = true)
        assertEquals("", plain.numberText(0, 4, cover = true))
        assertEquals("1", plain.numberText(1, 4, cover = true))
        assertEquals("3", plain.numberText(3, 4, cover = true))
        assertEquals("1", plain.numberText(0, 4))
        val total = PageSettings(pageNumbers = true, numberFormat = PageNumberFormat.NUMBER_OF_TOTAL)
        assertEquals("1 / 3", total.numberText(1, 4, cover = true))
        assertEquals("3 / 3", total.numberText(3, 4, cover = true))
        assertEquals("1 / 4", total.numberText(0, 4))
        assertEquals("4 / 4", total.numberText(3, 4))
        // Yalnız kapak + bir sayfa: "1 / 1"; yalnız kapak kalırsa toplam en az 1 yazılır
        assertEquals("1 / 1", total.numberText(1, 2, cover = true))
    }

    @Test
    fun coverHasNoHeaderFooterOrNumberLines() {
        val s = V45Fixtures.fullSettings
        assertEquals(emptyList<PageSettings.Line>(), s.lines(0, 4, cover = true))
        val first = s.lines(1, 4, cover = true)
        assertEquals(3, first.size)
        assertEquals(PageSettings.Line(true, PageAlign.LEFT, s.headerSize, "BÖLÜM BİR"), first[0])
        assertEquals(PageSettings.Line(false, PageAlign.CENTER, s.footerSize, "Okul"), first[1])
        assertEquals(PageSettings.Line(false, PageAlign.RIGHT, s.footerSize, "1 / 3"), first[2])
        assertEquals("3 / 3", s.lines(3, 4, cover = true)[2].text)
        // Kapak bayrağı yalnız ilk sayfayı etkiler
        assertEquals(s.lines(2, 4, cover = true).map { it.text }, listOf("BÖLÜM BİR", "Okul", "2 / 3"))
    }

    @Test
    fun oldComicWithoutCoverIsUnchanged() {
        // Numara alt bilgiyle aynı hizada: yazının yanına eklenir (v40 davranışı)
        val s = PageSettings(pageNumbers = true, numberFormat = PageNumberFormat.NUMBER_OF_TOTAL, footer = "Okul")
        assertEquals(listOf("Okul   1 / 4"), s.lines(0, 4).map { it.text })
        assertEquals(listOf("Okul   4 / 4"), s.lines(3, 4).map { it.text })
        assertEquals(s.lines(2, 4), s.lines(2, 4, cover = false))
        // Kapaklıda aynı sayfa (sıra 1) "1 / 3" yazar; kapak boş
        assertEquals(listOf("Okul   1 / 3"), s.lines(1, 4, cover = true).map { it.text })
        assertEquals(emptyList<String>(), s.lines(0, 4, cover = true).map { it.text })
        // Boş ayarlar kapakta da boş
        assertEquals(emptyList<PageSettings.Line>(), PageSettings().lines(1, 4, cover = true))
    }

    @Test
    fun pageBackgroundCoverFlag() {
        val cover = PageBackground(cover = true)
        assertFalse("kapak bayrağı tek başına saklanır", cover.isEmpty)
        assertFalse("kapak bayrağı görünüm değildir", cover.hasLook)
        assertNull("kapak bayrağı çıkınca boş kalır", cover.withoutCover())
        val paper = PageBackground(paperHex = "#FFF8E1", cover = true)
        assertTrue(paper.hasLook)
        assertEquals(PageBackground(paperHex = "#FFF8E1"), paper.withoutCover())
        val spec = PageBackground(spec = BackgroundSpec(patternId = "grid"))
        assertEquals(spec, spec.withoutCover())
        assertTrue(PageBackground().isEmpty)
    }

    @Test
    fun canvasUiCoverQueries() {
        val covered = V45Fixtures.comic(3, cover = true)
        assertTrue(covered.hasCover)
        assertTrue(covered.isCover(0))
        assertFalse(covered.isCover(1))
        assertFalse("kapak bayrağı sayfa arka planı sayılmaz", covered.hasPageBackgrounds)
        assertEquals("kapağın kağıdı ortak renk", "#FFFFFF", covered.paperOf(0))
        val old = V45Fixtures.comic(3, cover = false)
        assertFalse(old.hasCover)
        assertFalse(old.isCover(0))
        // Kapak bayrağı ilk sayfa dışında yok sayılır
        val misplaced = old.copy(pageBackgrounds = listOf(null, PageBackground(cover = true)))
        assertFalse(misplaced.hasCover)
        assertFalse(misplaced.isCover(1))
        // Kapakla birlikte kendi kağıdı olan kapak
        val tinted = old.copy(pageBackgrounds = listOf(PageBackground(paperHex = "#222222", cover = true)))
        assertTrue(tinted.hasCover)
        assertTrue(tinted.hasPageBackgrounds)
        assertEquals("#222222", tinted.paperOf(0))
    }

    @Test
    fun coverFlagSurvivesBackgroundJsonRoundTrip() {
        val pages = listOf(PageBackground(cover = true), null, PageBackground(paperHex = "#FFEEDD"))
        val json = encodeBackground(BackgroundSpec(patternId = "grid"), pages)
        assertTrue(json, json.contains("\"cover\":true"))
        assertEquals(pages, decodePageBackgrounds(json, 3))
        // Yalnız kapak bayrağı: "pages" dizisi tek elemanlı
        val onlyCover = encodeBackground(BackgroundSpec(), listOf(PageBackground(cover = true)))
        assertEquals(listOf(PageBackground(cover = true)), decodePageBackgrounds(onlyCover, 2))
        // Sayfa ayarları eklenip çıkarılınca kapak kalır
        val withSettings = withPageSettings(onlyCover, V45Fixtures.fullSettings)
        assertEquals(V45Fixtures.fullSettings, decodePageSettings(withSettings))
        assertEquals(listOf(PageBackground(cover = true)), decodePageBackgrounds(withSettings, 2))
        assertEquals(listOf(PageBackground(cover = true)), decodePageBackgrounds(withPageSettings(withSettings, null), 2))
        // Kapaksız sayfa arka planlarında "cover" anahtarı yazılmaz (eski biçim bayt bayt aynı)
        val old = encodeBackground(BackgroundSpec(), listOf(PageBackground(paperHex = "#FFEEDD")))
        assertFalse(old, old.contains("cover"))
        // Eski kayıt (anahtarsız) kapaksız okunur
        assertEquals(listOf(PageBackground(paperHex = "#FFEEDD")), decodePageBackgrounds("{\"pages\":[{\"paperHex\":\"#FFEEDD\"}]}", 1))
        assertEquals(emptyList<PageBackground?>(), decodePageBackgrounds("", 3))
        // Bilinmeyen alanlı yeni kayıt eski okuyucuda: ignoreUnknownKeys (kapak alanı ve geleceğin alanları)
        assertEquals(listOf(PageBackground(cover = true)), decodePageBackgrounds("{\"pages\":[{\"cover\":true,\"future\":1}]}", 1))
    }
}

/**
 * v45 madde 2 — kapak sayfası çıktılarda: tuval üstü yazılar (PageDecorRenderer), PDF (vektör ve resim), CBZ ve
 * resim çıktısı. Kapak yazısız, sonraki sayfa "1" ile başlar; kapaksız tuval eskisi gibi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V45CoverExportTest {

    @After fun tearDown() {
        stopKoin()
        ImageExporter.pdfSinkFactory = { com.karapp.drawguidance.data.images.AndroidPdfSink() }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exporter by lazy { ImageExporter(context, useGallery = false, settings = ExportSettingsStore(context)) }

    /** drawText çağrılarını (metin, x, y) kaydeden tuval. */
    private class RecordingCanvas(bitmap: Bitmap) : android.graphics.Canvas(bitmap) {
        data class Call(val text: String, val x: Float, val y: Float, val align: Paint.Align, val size: Float)
        val calls = mutableListOf<Call>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            calls += Call(text, x, y, paint.textAlign, paint.textSize)
            super.drawText(text, x, y, paint)
        }
    }

    /** Her PDF sayfasındaki yazıları kaydeden yazıcı. */
    private class RecordingPdfSink : PdfSink {
        val pages = mutableListOf<RecordingCanvas>()
        override fun startPage(width: Int, height: Int, number: Int): android.graphics.Canvas =
            RecordingCanvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)).also { pages += it }
        override fun finishPage() = Unit
        override fun finish(): ByteArray = "%PDF-test".toByteArray()
        override fun close() = Unit
    }

    private fun decor(c: CanvasUi, page: Int, cover: Boolean = c.hasCover): List<RecordingCanvas.Call> {
        val rc = RecordingCanvas(Bitmap.createBitmap(c.width, c.height, Bitmap.Config.ARGB_8888))
        PageDecorRenderer.draw(rc, c.pageSettings, page, c.pageCount, c.width.toFloat(), c.height.toFloat(), -1, cover = cover)
        return rc.calls
    }

    @Test
    fun decorRendererDrawsNothingOnTheCoverAndNumbersFromOne() {
        val c = V45Fixtures.comic(4, cover = true, settings = V45Fixtures.fullSettings)
        assertEquals(emptyList<RecordingCanvas.Call>(), decor(c, 0))
        val page1 = decor(c, 1)
        assertEquals(listOf("BÖLÜM BİR", "Okul", "1 / 3"), page1.map { it.text })
        // Yerleşim v40 ile aynı: sol üst bilgi kenardan %4,5 içeride, numara sağda, alt bilgi ortada
        val w = 400f; val h = 600f
        val inset = w * 0.045f
        val header = PageSettings.Line(true, PageAlign.LEFT, V45Fixtures.fullSettings.headerSize, "BÖLÜM BİR")
        val footer = PageSettings.Line(false, PageAlign.CENTER, V45Fixtures.fullSettings.footerSize, "Okul")
        assertEquals(inset, page1[0].x, 0.01f)
        assertEquals(PageDecorRenderer.baseline(header, w, h), page1[0].y, 0.01f)
        assertEquals(Paint.Align.LEFT, page1[0].align)
        assertEquals(w / 2f, page1[1].x, 0.01f)
        assertEquals(PageDecorRenderer.baseline(footer, w, h), page1[1].y, 0.01f)
        assertEquals(w - inset, page1[2].x, 0.01f)
        assertEquals(Paint.Align.RIGHT, page1[2].align)
        assertEquals(PageDecorRenderer.textSize(footer, w, h), page1[2].size, 0.01f)
        assertEquals("3 / 3", decor(c, 3)[2].text)
        // Aynı tuval kapak bayrağı olmadan: ilk sayfa "1 / 4"
        val old = V45Fixtures.comic(4, cover = false, settings = V45Fixtures.fullSettings)
        assertEquals(listOf("BÖLÜM BİR", "Okul", "1 / 4"), decor(old, 0).map { it.text })
        assertEquals("2 / 4", decor(old, 1)[2].text)
        // Ekran dönüşümüyle (tuvalde) de kapak boş
        val rc = RecordingCanvas(Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888))
        PageDecorRenderer.draw(rc, c.pageSettings, 0, 4, w, h, -1, left = 30f, top = 40f, scale = 1.5f, cover = true)
        assertTrue(rc.calls.isEmpty())
        PageDecorRenderer.draw(rc, c.pageSettings, 1, 4, w, h, -1, left = 30f, top = 40f, scale = 1.5f, cover = true)
        assertEquals(30f + inset * 1.5f, rc.calls[0].x, 0.01f)
    }

    @Test
    fun pdfNumberTextSkipsTheCover() {
        val c = V45Fixtures.comic(4, cover = true)
        assertNull(ImageExporter.pdfNumberText(c, 0))
        assertEquals("1 / 3", ImageExporter.pdfNumberText(c, 1))
        assertEquals("3 / 3", ImageExporter.pdfNumberText(c, 3))
        val old = V45Fixtures.comic(4, cover = false)
        assertEquals("1 / 4", ImageExporter.pdfNumberText(old, 0))
        assertEquals("4 / 4", ImageExporter.pdfNumberText(old, 3))
        assertEquals("1 / 1", ImageExporter.pdfNumberText(V45Fixtures.comic(2, cover = true), 1))
    }

    @Test
    fun vectorPdfHasNoTextOnTheCoverAndNumbersFromOne() {
        val sinks = mutableListOf<RecordingPdfSink>()
        ImageExporter.pdfSinkFactory = { RecordingPdfSink().also { sinks += it } }
        val c = V45Fixtures.comic(3, cover = true, settings = V45Fixtures.fullSettings)
        val options = PdfOptions("roman", pageNumbers = true)
        exporter.renderPdf(c, options)
        val pages = sinks.single().pages
        assertEquals(3, pages.size)
        assertEquals("kapakta ne sayfa yazısı ne kenar numarası", emptyList<String>(), pages[0].calls.map { it.text })
        // Sayfa 1: tuvaldeki yazılar ("1 / 2") + PDF kenar şeridi numarası ("1 / 2")
        assertEquals(listOf("BÖLÜM BİR", "Okul", "1 / 2", "1 / 2"), pages[1].calls.map { it.text })
        assertEquals(listOf("BÖLÜM BİR", "Okul", "2 / 2", "2 / 2"), pages[2].calls.map { it.text })
        // Kenar şeridi numarası sayfanın ortasında, alt şeritte
        val layout = PdfLayout.of(c.width, c.height, options)
        val band = pages[1].calls.last()
        assertEquals(layout.pageWidth / 2f, band.x, 0.01f)
        assertTrue("alt şeritte: ${band.y}", band.y > layout.pageHeight - layout.footer && band.y < layout.pageHeight)
        // Kapaksız aynı tuval: "1 / 3" ilk sayfada
        sinks.clear()
        exporter.renderPdf(V45Fixtures.comic(3, cover = false, settings = V45Fixtures.fullSettings), options)
        assertEquals(listOf("BÖLÜM BİR", "Okul", "1 / 3", "1 / 3"), sinks.single().pages[0].calls.map { it.text })
        // Yalnız kapak seçilirse (aralık 1-1) numara yok
        sinks.clear()
        exporter.renderPdf(c, PdfOptions("kapak", pages = com.karapp.drawguidance.data.images.PdfPages.RANGE, fromPage = 1, toPage = 1, pageNumbers = true))
        assertEquals(1, sinks.single().pages.size)
        assertTrue(sinks.single().pages[0].calls.isEmpty())
    }

    @Test
    fun imagePdfPagesUseTheSameNumbering() {
        val sinks = mutableListOf<RecordingPdfSink>()
        ImageExporter.pdfSinkFactory = { RecordingPdfSink().also { sinks += it } }
        val c = V45Fixtures.comic(3, cover = true, settings = V45Fixtures.fullSettings)
        exporter.renderPdf(c, PdfOptions("roman", pageNumbers = true, quality = com.karapp.drawguidance.data.images.PdfQuality.IMAGE, imageScale = 1f))
        val pages = sinks.single().pages
        // Resim sayfalarında tuval yazıları resmin içinde; PDF tuvaline yalnız kenar numarası yazılır
        assertEquals(emptyList<String>(), pages[0].calls.map { it.text })
        assertEquals(listOf("1 / 2"), pages[1].calls.map { it.text })
        assertEquals(listOf("2 / 2"), pages[2].calls.map { it.text })
    }

    private fun diff(a: Bitmap, b: Bitmap): Int {
        assertEquals(a.width, b.width); assertEquals(a.height, b.height)
        var n = 0
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }

    @Test
    fun imageRenderLeavesTheCoverUndecorated() {
        val settings = V45Fixtures.fullSettings
        val coverComic = V45Fixtures.comic(3, cover = true, settings = settings)
        val bare = V45Fixtures.comic(3, cover = true)
        // Kapak: ayarlı ve ayarsız resim birebir aynı
        assertEquals(0, diff(exporter.render(coverComic, 1f, page = 0), exporter.render(bare, 1f, page = 0)))
        // Sayfa 1 yazılı
        assertTrue(diff(exporter.render(coverComic, 1f, page = 1), exporter.render(bare, 1f, page = 1)) > 50)
        // Kapaklı tuvalin sayfa 1'i ("1 / 2") = aynı çizimli kapaksız 2 sayfalık tuvalin ilk sayfası ("1 / 2")
        val shifted = coverComic.copy(pages = coverComic.pages.drop(1), pageBackgrounds = emptyList())
        assertEquals(0, diff(exporter.render(coverComic, 1f, page = 1), exporter.render(shifted, 1f, page = 0)))
        // Ama kapaksız 3 sayfalık tuvalin aynı sayfası ("2 / 3") farklı
        val old = coverComic.copy(pageBackgrounds = emptyList())
        assertTrue(diff(exporter.render(coverComic, 1f, page = 1), exporter.render(old, 1f, page = 1)) > 0)
        // Resim dışa aktarma (tüm sayfalar) da aynı resimleri yazar: kapak dosyası yazısız
        val files = exporter.export(coverComic, ExportOptions("kapakli", ImageFormat.PNG, 1f, allPages = true))
        assertEquals(3, files.size)
        val first = File(Uri.parse(files[0].uri).path!!).readBytes().let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        assertEquals(0, diff(first, exporter.render(bare, 1f, page = 0)))
    }

    @Test
    fun cbzCoverImageIsUndecoratedAndMarkedFrontCover() {
        val settings = V45Fixtures.fullSettings
        val coverComic = V45Fixtures.comic(3, cover = true, settings = settings)
        val file = exporter.exportCbz(coverComic, CbzOptions("Roman", format = ImageFormat.PNG))
        val images = ArrayList<Bitmap>()
        var info = ""
        ZipInputStream(ByteArrayInputStream(File(Uri.parse(file.uri).path!!).readBytes())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val b = zip.readBytes()
                if (e.name.endsWith(".png")) images += BitmapFactory.decodeByteArray(b, 0, b.size) else info = String(b)
            }
        }
        assertEquals(3, images.size)
        assertTrue(info, info.contains("<Page Image=\"0\" Type=\"FrontCover\""))
        assertTrue(info.contains("<PageCount>3</PageCount>"))
        val bare = V45Fixtures.comic(3, cover = true)
        assertEquals("kapak yazısız", 0, diff(images[0], exporter.render(bare, 1f, page = 0)))
        assertTrue("sayfa 1 yazılı", diff(images[1], exporter.render(bare, 1f, page = 1)) > 50)
        val shifted = coverComic.copy(pages = coverComic.pages.drop(1), pageBackgrounds = emptyList())
        assertEquals("sayfa 1 = '1 / 2'", 0, diff(images[1], exporter.render(shifted, 1f, page = 0)))
        assertEquals("sayfa 2 = '2 / 2'", 0, diff(images[2], exporter.render(shifted, 1f, page = 1)))
    }
}

/** v45 madde 2 — yeni çizgi roman kapakla oluşturulur; kapak şablonu ve kayıt (Room v23, yeni sütun yok). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V45CoverDataTest {

    @After fun tearDown() = stopKoin()

    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    @Test
    fun coverTemplateHasFullBleedArtTitleAndSubtitle() {
        val w = 1200; val h = 1800
        val cover = ComicCover.build(w, h, "  Uzay Korsanları ", "Ayşe Yılmaz")
        assertEquals(3, cover.size)
        assertEquals(listOf(0, 1, 2), cover.map { it.order })
        // Resim alanı: kenar boşluksuz tam sayfa çizgi roman paneli
        val art = cover[0]
        assertEquals(PanelSets.KIND, art.shape!!.kind)
        assertNotNull(art.shape!!.panel)
        assertEquals(ComicController.PANEL_INK, art.shape!!.style.strokeColorHex)
        assertEquals(0f, art.points.minOf { it.x }, 0.5f)
        assertEquals(0f, art.points.minOf { it.y }, 0.5f)
        assertEquals(w.toFloat(), art.points.maxOf { it.x }, 0.5f)
        assertEquals(h.toFloat(), art.points.maxOf { it.y }, 0.5f)
        // Başlık: büyük, çizgi roman yazı tipi, ortada, üstte, beyaz kenarlıklı
        val title = cover[1]
        assertEquals(BrushType.TEXT, title.brushType)
        assertEquals("Uzay Korsanları", title.text!!.content)
        assertEquals(TextFont.BANGERS, title.text!!.font)
        assertEquals(ComicCover.OUTLINE, title.text!!.outlineColorHex)
        assertTrue(title.text!!.box!!.effects!!.caps)
        assertEquals(min(w, h) * ComicCover.TITLE_SIZE, title.sizePx, 1f)
        val tl = title.points.minOf { it.x }; val tr = title.points.maxOf { it.x }
        assertEquals("yatay ortada", w / 2f, (tl + tr) / 2f, 2f)
        assertEquals(h * ComicCover.TITLE_TOP, title.points.minOf { it.y }, 1f)
        assertTrue("sayfa içinde", tl >= 0f && tr <= w)
        // Alt başlık / yazar: altta, ortada, küçük
        val sub = cover[2]
        assertEquals("Ayşe Yılmaz", sub.text!!.content)
        assertEquals(TextFont.COMIC_RELIEF_BOLD, sub.text!!.font)
        assertTrue(sub.sizePx < title.sizePx / 2f)
        assertEquals(h * ComicCover.SUBTITLE_BOTTOM, sub.points.maxOf { it.y }, 1.5f)
        assertEquals(w / 2f, (sub.points.minOf { it.x } + sub.points.maxOf { it.x }) / 2f, 2f)
        // Alt başlık isteğe bağlı; ad boşsa "Başlık"
        val noSub = ComicCover.build(w, h, "   ", null)
        assertEquals(2, noSub.size)
        assertEquals("Başlık", noSub[1].text!!.content)
        assertEquals(2, ComicCover.build(w, h, "X", "  ").size)
        // Kodlayıcıdan geçer (kaydedilir)
        val back = ContentCodec.decode(ContentCodec.encode(cover))
        assertEquals(cover.map { it.text?.content }, back.map { it.text?.content })
        assertEquals(cover[0].shape!!.panel, back[0].shape!!.panel)
    }

    @Test
    fun newComicStartsWithCoverAndOneBlankPage() = runBlocking {
        val r = repo()
        val cover = ComicCover.build(800, 1200, "Kahraman", ComicCover.subtitlePlaceholder())
        val created = r.createCanvas("Kahraman", 800, 1200, "#FFFFFF", false, kind = CanvasKind.COMIC, cover = cover)
        assertEquals(2, created.pageCount)
        assertTrue(created.hasCover)
        assertEquals("kapaktan sonraki boş sayfada açılır", 1, created.currentPage)
        assertEquals(3, created.pages[0].size)
        assertTrue(created.pages[1].isEmpty())
        val loaded = r.getCanvas(created.localId)!!
        assertEquals(2, loaded.pageCount)
        assertTrue(loaded.hasCover)
        assertEquals(1, loaded.currentPage)
        assertEquals("Kahraman", loaded.pages[0][1].text!!.content)
        assertEquals("Yazar adı", loaded.pages[0][2].text!!.content)
        // Sayfa ayarları, arka plan ve tuval ayarları kaydedilince kapak kalır
        r.savePageSettings(created.localId, V45Fixtures.fullSettings)
        assertTrue(r.getCanvas(created.localId)!!.hasCover)
        r.saveBackgroundSpec(created.localId, BackgroundSpec(patternId = "grid"), loaded.pageBackgrounds)
        r.getCanvas(created.localId)!!.let {
            assertTrue(it.hasCover)
            assertEquals(V45Fixtures.fullSettings, it.pageSettings)
            assertEquals("grid", it.background.patternId)
        }
        r.updateSettings(created.localId, "Yeni ad", 800, 1200, "#FFFFFF", false, BackgroundSpec())
        r.getCanvas(created.localId)!!.let {
            assertTrue(it.hasCover)
            assertEquals(V45Fixtures.fullSettings, it.pageSettings)
        }
        // Kapaksız oluşturma eskisi gibi
        val plain = r.createCanvas("Eski", 800, 1200, "#FFFFFF", false, kind = CanvasKind.COMIC, pageCount = 3)
        assertFalse(plain.hasCover)
        assertEquals(3, plain.pageCount)
        assertEquals(0, plain.currentPage)
        assertFalse(r.getCanvas(plain.localId)!!.hasCover)
        // Sayfa sınırı: kapak da sayfa sayılır
        val capped = r.createCanvas("Sınır", 800, 1200, "#FFFFFF", false, kind = CanvasKind.COMIC, pageCount = 10_000, cover = cover)
        assertEquals(CanvasKind.COMIC.maxPages, capped.pageCount)
        assertTrue(capped.hasCover)
    }
}

/**
 * v45 madde 10 — not alma panelleri: yerleşimlerin alan sayıları, sayfa içinde kalmaları, birbirine binmemeleri,
 * oranları, not alanı görünümü ve kayıttan geçmeleri.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class V45NotesPanelsLogicTest {

    private data class R(val l: Float, val t: Float, val r: Float, val b: Float) {
        val w get() = r - l
        val h get() = b - t
    }

    private fun rect(s: Stroke): R = R(s.points.minOf { it.x }, s.points.minOf { it.y }, s.points.maxOf { it.x }, s.points.maxOf { it.y })

    private val w = 1240f
    private val h = 1754f
    private val short = min(w, h)

    @Test
    fun layoutsAndAreaCounts() {
        assertEquals(
            listOf(
                "two_cols", "three_cols", "two_rows", "three_rows", "grid_2x2", "grid_2x3", "main_side", "side_main", "cornell", "header_two_cols",
                "square_2x2", "square_3x3", "square_notes",
            ),
            NotesPanels.layouts.map { it.id },
        )
        assertEquals(listOf(2, 3, 2, 3, 4, 6, 2, 2, 3, 3, 4, 9, 2), NotesPanels.layouts.map { it.areaCount })
        // Kare yerleşimler ve dikdörtgen (panel motorlu) yerleşimler
        assertEquals(listOf("square_2x2", "square_3x3", "square_notes"), NotesPanels.layouts.filter { it.square }.map { it.id })
        NotesPanels.layouts.forEach { assertEquals(it.id, !it.square, it.page != null) }
        NotesPanels.layouts.forEach { layout ->
            assertEquals(layout.id, layout.areaCount, NotesPanels.build(layout, w, h).size)
            assertEquals(layout, NotesPanels.byId(layout.id))
        }
        assertNull(NotesPanels.byId("yok"))
        assertEquals("adlar benzersiz", NotesPanels.layouts.size, NotesPanels.layouts.map { it.title }.toSet().size)
    }

    @Test
    fun areasStayInsideMarginsAndNeverOverlap() {
        val m = short * NotesPanels.MARGIN
        val gap = short * NotesPanels.GAP
        NotesPanels.layouts.forEach { layout ->
            val rects = NotesPanels.build(layout, w, h).map(::rect)
            rects.forEach { r ->
                assertTrue("${layout.id} sol", r.l >= m - 0.5f)
                assertTrue("${layout.id} üst", r.t >= m - 0.5f)
                assertTrue("${layout.id} sağ", r.r <= w - m + 0.5f)
                assertTrue("${layout.id} alt", r.b <= h - m + 0.5f)
                assertTrue("${layout.id} boş değil", r.w > short * 0.1f && r.h > short * 0.05f)
            }
            for (i in rects.indices) for (j in i + 1 until rects.size) {
                val a = rects[i]; val b = rects[j]
                val dx = maxOf(b.l - a.r, a.l - b.r)
                val dy = maxOf(b.t - a.b, a.t - b.b)
                assertTrue("${layout.id} $i-$j aralık ≥ GAP: dx=$dx dy=$dy", maxOf(dx, dy) >= gap - 0.5f)
            }
            // Dikdörtgen yerleşimlerde alanlar kullanılabilir alanın çoğunu kaplar (boşluk yalnız aralıklar); kare
            // yerleşimlerin geometrisi exactGeometryOfSquareLayouts'ta
            val area = rects.sumOf { (it.w * it.h).toDouble() }
            val usable = ((w - 2 * m) * (h - 2 * m)).toDouble()
            if (!layout.square) assertTrue("${layout.id} kaplama ${area / usable}", area / usable > 0.85)
            else assertTrue("${layout.id} kaplama ${area / usable}", area / usable > 0.45)
        }
    }

    @Test
    fun exactGeometryOfKeyLayouts() {
        val m = short * NotesPanels.MARGIN
        val gap = short * NotesPanels.GAP
        val iw = w - 2 * m
        val ih = h - 2 * m
        // İki sütun: eşit, tam boy
        val two = NotesPanels.build(NotesPanels.byId("two_cols")!!, w, h).map(::rect)
        assertEquals(m, two[0].l, 0.5f)
        assertEquals(m, two[0].t, 0.5f)
        assertEquals(h - m, two[0].b, 0.5f)
        assertEquals((iw - gap) / 2f, two[0].w, 0.5f)
        assertEquals(two[0].w, two[1].w, 0.5f)
        assertEquals(gap, two[1].l - two[0].r, 0.5f)
        assertEquals(w - m, two[1].r, 0.5f)
        // 2 × 2: dört eşit alan, okuma sırası sol üst, sağ üst, sol alt, sağ alt
        val grid = NotesPanels.build(NotesPanels.byId("grid_2x2")!!, w, h).map(::rect)
        grid.forEach { assertEquals((iw - gap) / 2f, it.w, 0.5f); assertEquals((ih - gap) / 2f, it.h, 0.5f) }
        assertTrue(grid[0].l < grid[1].l && grid[0].t == grid[1].t)
        assertTrue(grid[2].t > grid[0].t && grid[2].l == grid[0].l)
        assertEquals(grid[1].l, grid[3].l, 0.01f)
        // Büyük alan + yan sütun: 2,2 : 1
        val ms = NotesPanels.build(NotesPanels.byId("main_side")!!, w, h).map(::rect)
        assertEquals(2.2f, ms[0].w / ms[1].w, 0.01f)
        assertEquals(ms[0].h, ms[1].h, 0.5f)
        val sm = NotesPanels.build(NotesPanels.byId("side_main")!!, w, h).map(::rect)
        assertEquals(2.2f, sm[1].w / sm[0].w, 0.01f)
        assertTrue(sm[0].l < sm[1].l)
        // Cornell: üstte %30 anahtar sözcük sütunu + %70 not alanı (boyun %78'i), altta tam en özet şeridi (%22)
        val cornell = NotesPanels.build(NotesPanels.byId("cornell")!!, w, h).map(::rect)
        val avail = ih - gap
        assertEquals(avail * 0.78f, cornell[0].h, 0.5f)
        assertEquals(cornell[0].h, cornell[1].h, 0.5f)
        assertEquals(avail * 0.22f, cornell[2].h, 0.5f)
        assertEquals((iw - gap) * 0.3f, cornell[0].w, 0.5f)
        assertEquals((iw - gap) * 0.7f, cornell[1].w, 0.5f)
        assertEquals(iw, cornell[2].w, 0.5f)
        assertEquals(h - m, cornell[2].b, 0.5f)
        // Başlık + iki sütun: başlık tam en, %16
        val header = NotesPanels.build(NotesPanels.byId("header_two_cols")!!, w, h).map(::rect)
        assertEquals(iw, header[0].w, 0.5f)
        assertEquals(avail * 0.16f, header[0].h, 0.5f)
        assertEquals(header[1].t, header[2].t, 0.5f)
        // Üç satır: eşit boy, tam en
        val rows = NotesPanels.build(NotesPanels.byId("three_rows")!!, w, h).map(::rect)
        rows.forEach { assertEquals(iw, it.w, 0.5f); assertEquals((ih - 2 * gap) / 3f, it.h, 0.5f) }
        // Yatay sayfada da sayfa içinde
        NotesPanels.build(NotesPanels.byId("grid_2x3")!!, h, w).map(::rect).forEach { assertTrue(it.r <= h && it.b <= w) }
    }

    @Test
    fun exactGeometryOfSquareLayouts() {
        val m = short * NotesPanels.MARGIN
        val gap = short * NotesPanels.GAP
        val iw = w - 2 * m
        val ih = h - 2 * m
        // 2 × 2 kare: dikey sayfada kenarı enden gelir, tam kare, üst kenar boşluğuna yaslı, yatayda ortalı
        val q = NotesPanels.build(NotesPanels.byId("square_2x2")!!, w, h).map(::rect)
        val side2 = (iw - gap) / 2f
        q.forEach { assertEquals(side2, it.w, 0.5f); assertEquals(side2, it.h, 0.5f) }
        assertEquals(m, q[0].l, 0.5f)
        assertEquals(m, q[0].t, 0.5f)
        assertEquals(w - m, q[1].r, 0.5f)
        assertEquals(gap, q[1].l - q[0].r, 0.5f)
        assertEquals(gap, q[2].t - q[0].b, 0.5f)
        assertEquals(m + 2 * side2 + gap, q[3].b, 0.5f)
        // 3 × 3 kare: dokuz eş kare, satır satır
        val nine = NotesPanels.build(NotesPanels.byId("square_3x3")!!, w, h).map(::rect)
        val side3 = (iw - 2 * gap) / 3f
        assertEquals(9, nine.size)
        nine.forEach { assertEquals(side3, it.w, 0.5f); assertEquals(side3, it.h, 0.5f) }
        assertEquals(nine[0].t, nine[2].t, 0.01f)
        assertEquals(nine[0].l, nine[6].l, 0.01f)
        assertEquals(m + 3 * side3 + 2 * gap, nine[8].b, 0.5f)
        // Yatay sayfada kenar boydan gelir; ızgara yatayda ortalanır, kareler yine kare
        val wide = NotesPanels.build(NotesPanels.byId("square_2x2")!!, h, w).map(::rect)
        val sideWide = (w - 2 * m - gap) / 2f
        wide.forEach { assertEquals(sideWide, it.w, 0.5f); assertEquals(sideWide, it.h, 0.5f) }
        assertEquals("ortalı", h / 2f, (wide[0].l + wide[1].r) / 2f, 0.5f)
        assertEquals(w - m, wide[3].b, 0.5f)
        // Kare + not alanı (dikey): kare üstte ortalı, kenarı min(en, boyun %60'ı); not alanı altında tam en
        val sn = NotesPanels.build(NotesPanels.byId("square_notes")!!, w, h).map(::rect)
        val sq = minOf(iw, ih * NotesPanels.SQUARE_SHARE)
        assertEquals(sq, sn[0].w, 0.5f)
        assertEquals(sq, sn[0].h, 0.5f)
        assertEquals(m, sn[0].t, 0.5f)
        assertEquals(w / 2f, (sn[0].l + sn[0].r) / 2f, 0.5f)
        assertEquals(iw, sn[1].w, 0.5f)
        assertEquals(gap, sn[1].t - sn[0].b, 0.5f)
        assertEquals(h - m, sn[1].b, 0.5f)
        // Yatay: kare solda, not alanı sağda tam boy
        val snw = NotesPanels.build(NotesPanels.byId("square_notes")!!, h, w).map(::rect)
        assertEquals(snw[0].w, snw[0].h, 0.5f)
        assertEquals(m, snw[0].l, 0.5f)
        assertEquals(gap, snw[1].l - snw[0].r, 0.5f)
        assertEquals(w - 2 * m, snw[1].h, 0.5f)
        // Her yerleşimde her alan dört köşeli kapalı dikdörtgen (köşeler sırayla)
        NotesPanels.layouts.forEach { layout ->
            NotesPanels.build(layout, w, h).forEach { s ->
                val r = rect(s)
                listOf(Offset(r.l, r.t), Offset(r.r, r.t), Offset(r.r, r.b), Offset(r.l, r.b)).forEach { c ->
                    assertTrue("${layout.id} köşe $c", s.points.any { abs(it.x - c.x) < 0.5f && abs(it.y - c.y) < 0.5f })
                }
            }
        }
    }

    @Test
    fun notesAreaLookAndCodecRoundTrip() {
        val strokes = NotesPanels.build(NotesPanels.byId("cornell")!!, w, h, order = 5)
        assertEquals(listOf(5, 6, 7), strokes.map { it.order })
        assertEquals("benzersiz kimlik", 3, strokes.map { it.id }.toSet().size)
        strokes.forEach { s ->
            assertEquals(BrushType.SHAPE, s.brushType)
            val spec = s.shape!!
            assertEquals(NotesPanels.KIND, spec.kind)
            assertTrue(NotesPanels.isNotesPanel(s))
            assertNull("çizgi roman şablonu değil", spec.panel)
            assertEquals(NotesPanels.FRAME_COLOR, spec.style.strokeColorHex)
            assertEquals(NotesPanels.TINT_COLOR, spec.style.fillColorHex)
            assertEquals(NotesPanels.TINT_OPACITY, spec.style.fillOpacity, 0.0001f)
            assertEquals(short * NotesPanels.CORNER, spec.style.cornerRadius, 0.01f)
            assertEquals(short * NotesPanels.LINE, spec.style.strokeWidth, 0.01f)
            assertTrue("ince çerçeve", spec.style.strokeWidth < 6f)
            assertTrue("kapalı tek yol", spec.subpaths.isNotEmpty())
        }
        // Çizgi kalınlığı küçük ve büyük sayfada sınırlı
        assertEquals(1.5f, NotesPanels.style(200f, 300f).strokeWidth, 0.0001f)
        assertEquals(6f, NotesPanels.style(8000f, 9000f).strokeWidth, 0.0001f)
        // Kayıt: kodlayıcıdan geçer (biçim, tür ve köşeler korunur)
        val back = ContentCodec.decode(ContentCodec.encode(strokes))
        assertEquals(strokes.size, back.size)
        back.zip(strokes).forEach { (b, s) ->
            assertEquals(s.id, b.id)
            assertEquals(s.shape!!.kind, b.shape!!.kind)
            assertEquals(s.shape!!.style.fillColorHex, b.shape!!.style.fillColorHex)
            assertEquals(s.shape!!.style.cornerRadius, b.shape!!.style.cornerRadius, 0.01f)
            assertEquals(rect(s).l, rect(b).l, 0.05f)
            assertEquals(rect(s).b, rect(b).b, 0.05f)
            assertTrue(NotesPanels.isNotesPanel(b))
        }
        // Çizgi roman paneli not paneli sayılmaz
        assertFalse(NotesPanels.isNotesPanel(V45Fixtures.line("x", 3f)))
    }
}

/**
 * v45 madde 4 — basit not alma gelişmiş not almanın bütün şekil, grafik, eğri, diyagram, formül ve fen şekli
 * kataloğunu kullanır; öteki basit türlerin katalogları değişmez.
 */
class V45SimpleNotesCatalogTest {

    @Test
    fun simpleNotesCategoriesEqualNotesExactly() {
        val notes = ShapeRegistry.categories(CanvasKind.NOTES)
        val simple = ShapeRegistry.categories(CanvasKind.SIMPLE_NOTES)
        assertEquals(notes.map { it.id }, simple.map { it.id })
        assertEquals(notes.map { it.section }, simple.map { it.section })
        assertEquals(notes.map { it.title }, simple.map { it.title })
        assertEquals(notes.map { it.builtin }, simple.map { it.builtin })
        assertEquals(notes.map { it.items }, simple.map { it.items })
        val all = simple.flatMap { it.items }
        assertTrue("grafikler", all.containsAll(ShapeRegistry.graphItems))
        assertTrue("eğriler", all.containsAll(ShapeRegistry.curveItems))
        assertTrue("fen şekilleri", all.any { it.startsWith(com.karapp.drawguidance.content.library.ShapeLibrary.PREFIX) })
        val notesLibrary = com.karapp.drawguidance.content.library.ShapeLibrary.categories
            .filter { it.listed && it.audience == com.karapp.drawguidance.content.library.Audience.NOTES }
            .flatMap { c -> c.shapes.map { com.karapp.drawguidance.content.library.ShapeLibrary.PREFIX + it.id } }
        assertTrue("bütün not kütüphanesi (${notesLibrary.size})", notesLibrary.isNotEmpty() && all.containsAll(notesLibrary))
        assertTrue("eski 42 şekilden çok fazla: ${all.size}", all.size > 100)
        assertTrue("grafik bölümü", simple.any { it.section == "Grafik" })
        // Her kimliğin adı var (seçicide boş kutu yok)
        all.forEach { id -> assertTrue(id, ShapeRegistry.title(id) != null || ShapeRegistry.builtin(id) != null) }
    }

    @Test
    fun simpleNotesHasNoCatalogFilters() {
        assertNull(SimpleCatalog.diagrams(CanvasKind.SIMPLE_NOTES))
        assertNull(SimpleCatalog.formulas(CanvasKind.SIMPLE_NOTES))
        assertNull(SimpleCatalog.models3d(CanvasKind.SIMPLE_NOTES))
        assertEquals(SimpleCatalog.diagrams(CanvasKind.NOTES), SimpleCatalog.diagrams(CanvasKind.SIMPLE_NOTES))
        // Öteki basit türler eskisi gibi süzgeçli
        assertEquals(10, SimpleCatalog.diagrams(CanvasKind.SIMPLE_ART)!!.size)
        assertEquals(10, SimpleCatalog.diagrams(CanvasKind.KIDS)!!.size)
        assertEquals(10, SimpleCatalog.models3d(CanvasKind.SIMPLE_ART)!!.size)
        assertEquals(SimpleCatalog.formulas, SimpleCatalog.formulas(CanvasKind.SIMPLE_ART))
        // Basit resim ve çocukların şekil kataloğu değişmedi
        assertNotEquals(ShapeRegistry.categories(CanvasKind.NOTES).map { it.id }, ShapeRegistry.categories(CanvasKind.SIMPLE_ART).map { it.id })
        assertFalse(ShapeRegistry.categories(CanvasKind.SIMPLE_ART).any { it.section == "Grafik" })
        assertFalse(ShapeRegistry.categories(CanvasKind.KIDS).any { it.section == "Grafik" })
    }
}

/** v45 yeni yazılar 7 dilde gerçek çeviriyle; Kotlin tarafındaki kapak yazıları i18n tablolarında. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class V45NotesComicStringsTest {

    private val keys = listOf(
        com.karapp.drawguidance.R.string.v45_cover, com.karapp.drawguidance.R.string.v45_cover_add,
        com.karapp.drawguidance.R.string.v45_help_cover, com.karapp.drawguidance.R.string.v45_help_cover_add,
        com.karapp.drawguidance.R.string.v45_panels_add, com.karapp.drawguidance.R.string.v45_help_panels_add,
        com.karapp.drawguidance.R.string.v45_panels_title, com.karapp.drawguidance.R.string.v45_panels_hint,
    ) + NotesPanels.layouts.map { it.title }

    private fun res(lang: String): android.content.res.Resources {
        val base: Context = ApplicationProvider.getApplicationContext()
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
        return base.createConfigurationContext(config).resources
    }

    @Test
    fun everyStringIsTranslatedInSevenLanguages() {
        val tr = res("tr")
        assertEquals("Kapak", tr.getString(com.karapp.drawguidance.R.string.v45_cover))
        assertEquals("Kapak ekle", tr.getString(com.karapp.drawguidance.R.string.v45_cover_add))
        assertEquals("Panel ekle", tr.getString(com.karapp.drawguidance.R.string.v45_panels_add))
        assertEquals("Cornell not düzeni", tr.getString(com.karapp.drawguidance.R.string.v45_panel_cornell))
        assertEquals("4 alan", tr.getString(com.karapp.drawguidance.R.string.v45_panel_count, 4))
        val en = res("en")
        assertEquals("Cover", en.getString(com.karapp.drawguidance.R.string.v45_cover))
        assertEquals("Add cover", en.getString(com.karapp.drawguidance.R.string.v45_cover_add))
        assertEquals("Add panels", en.getString(com.karapp.drawguidance.R.string.v45_panels_add))
        assertEquals("4 areas", en.getString(com.karapp.drawguidance.R.string.v45_panel_count, 4))
        val turkish = keys.map { tr.getString(it) }
        val scripts = mapOf(
            "ar" to Regex("[\\u0600-\\u06FF]"), "ja" to Regex("[\\u3040-\\u30FF\\u4E00-\\u9FFF]"),
            "ko" to Regex("[\\uAC00-\\uD7AF]"), "ru" to Regex("[\\u0400-\\u04FF]"), "zh" to Regex("[\\u4E00-\\u9FFF]"),
            "en" to Regex("[A-Za-z]"),
        )
        scripts.forEach { (lang, script) ->
            val r = res(lang)
            keys.forEachIndexed { i, key ->
                val s = r.getString(key)
                assertTrue("$lang boş: ${tr.getResourceEntryName(key)}", s.isNotBlank())
                assertNotEquals("$lang çevrilmemiş: ${tr.getResourceEntryName(key)}", turkish[i], s)
                assertTrue("$lang yazı sistemi: $s", script.containsMatchIn(s))
            }
            val count = r.getString(com.karapp.drawguidance.R.string.v45_panel_count, 6)
            // Arapçada sayı Arap-Hint rakamıyla (٦) yazılır
            assertTrue("$lang sayı: $count", count.contains(if (lang == "ar") "٦" else "6"))
        }
    }

    @Test
    fun coverPlaceholdersAreInI18nTables() {
        listOf("en", "ar", "ja", "ko", "ru", "zh").forEach { lang ->
            val table = com.karapp.drawguidance.i18n.I18n.load(lang)
            val author = table[ComicCover.SUBTITLE_TEXT]
            val title = table[ComicCover.TITLE_TEXT]
            assertTrue("$lang yazar: $author", !author.isNullOrBlank() && author != ComicCover.SUBTITLE_TEXT)
            assertTrue("$lang başlık: $title", !title.isNullOrBlank() && title != ComicCover.TITLE_TEXT)
        }
        assertEquals("Author name", com.karapp.drawguidance.i18n.I18n.load("en")[ComicCover.SUBTITLE_TEXT])
        val before = com.karapp.drawguidance.i18n.I18n.language
        try {
            com.karapp.drawguidance.i18n.I18n.setLanguage("en")
            assertEquals("Author name", ComicCover.subtitlePlaceholder())
            assertEquals("Title", ComicCover.titleFor(" "))
            assertEquals("Hero", ComicCover.titleFor(" Hero "))
        } finally {
            com.karapp.drawguidance.i18n.I18n.setLanguage(before)
        }
        assertEquals("Yazar adı", ComicCover.subtitlePlaceholder())
    }
}
