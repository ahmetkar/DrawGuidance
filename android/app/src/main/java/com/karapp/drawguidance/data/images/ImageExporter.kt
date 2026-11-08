package com.karapp.drawguidance.data.images

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawCanvasContent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Kaydedilen belge (PDF). */
data class ExportedFile(val uri: String, val location: String, val sizeBytes: Long)

data class ExportedImage(val uri: String, val location: String, val width: Int, val height: Int, val sizeBytes: Long)

/** Kaydetme penceresinde seçilen ayarlar. */
data class ExportOptions(
    /** Uzantısız dosya adı. */
    val fileName: String,
    val format: com.karapp.drawguidance.data.settings.ImageFormat = com.karapp.drawguidance.data.settings.ImageFormat.PNG,
    /** Tuval boyutunun katı (0,5×, 1×, 2×, 4×); en uzun kenar [ImageExporter.MAX_SIDE] ile sınırlıdır. */
    val scale: Float = 1f,
    /** JPEG kalitesi (1..100). */
    val quality: Int = 92,
    /** Çalışma/not tuvalinde tüm sayfalar (her sayfa ayrı resim). */
    val allPages: Boolean = false,
    /** Kılavuz ve perspektif çizgileri resme dahil edilsin mi. */
    val includeGuides: Boolean = false,
    /** v40 kaydetme yeri ("Konum seç"). */
    val target: SaveTarget = SaveTarget.Default,
    /** v40 baskıya hazır (CMYK) kayıt ayarları; null: RGB ([format] ile). */
    val cmyk: com.karapp.drawguidance.data.print.CmykOptions? = null,
    /** v40 CMYK kayıtta dosya türü. */
    val cmykFormat: CmykFormat = CmykFormat.JPEG,
) {
    /** Kaydedilecek dosyanın uzantısı. */
    val extension: String get() = if (cmyk != null) cmykFormat.extension else format.extension
    val mime: String get() = if (cmyk != null) cmykFormat.mime else format.mime
    /** v40 CMYK TIFF/PDF: belge olarak kaydedilir (Resimlerim'e eklenmez). */
    val isDocument: Boolean get() = cmyk != null && cmykFormat.isDocument
}

/**
 * Tuvali seçilen çözünürlük ve biçimde resme çevirir ve kaydeder:
 * - Ayarlar'da klasör seçildiyse o klasöre (Android klasör seçici, kalıcı izin)
 * - Yoksa Android 10+: Galeri'de görünen Pictures/<albüm> klasörü (izin gerekmez)
 * - Daha eski sürümler ya da Galeri kullanılamazsa: uygulamanın Pictures/<albüm> klasörü
 */
class ImageExporter(
    private val context: Context,
    private val useGallery: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    private val settings: com.karapp.drawguidance.data.settings.ExportSettingsStore? = null,
) {

    /** Kaydedilecek resmin piksel boyutu (en uzun kenar sınırı uygulanmış). */
    fun outputSize(canvasWidth: Int, canvasHeight: Int, scale: Float): Pair<Int, Int> {
        val factor = factorFor(canvasWidth, canvasHeight, scale)
        return (canvasWidth * factor).roundToInt().coerceAtLeast(1) to (canvasHeight * factor).roundToInt().coerceAtLeast(1)
    }

    private fun factorFor(canvasWidth: Int, canvasHeight: Int, scale: Float): Float {
        val longest = max(canvasWidth, canvasHeight).coerceAtLeast(1)
        val wanted = scale.coerceIn(0.1f, 8f)
        return if (longest * wanted > MAX_SIDE) MAX_SIDE.toFloat() / longest else wanted
    }

    fun render(
        canvas: CanvasUi,
        scale: Float = 1f,
        includeGuides: Boolean = com.karapp.drawguidance.ui.drawing.GuideRenderer.exports(canvas.guide),
        page: Int = canvas.currentPage,
        /** null: tuvalin arka plan rengi (v19 PDF'te beyaz kağıt seçilebilir). */
        background: androidx.compose.ui.graphics.Color? = null,
        /** v23 arka plan resmi ve deseni çizilsin mi (PDF'te beyaz kağıt seçilince çizilmez). */
        decor: Boolean = true,
    ): Bitmap {
        val factor = factorFor(canvas.width, canvas.height, scale)
        val (width, height) = outputSize(canvas.width, canvas.height, scale)
        // Gizli katmanlar resme girmez
        val strokes = com.karapp.drawguidance.data.model.Layers.visible(canvas.pages.getOrElse(page) { canvas.strokes }, canvas.layers)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(width.toFloat(), height.toFloat()),
        ) {
            scale(factor, factor, pivot = Offset.Zero) {
                drawCanvasContent(
                    background ?: hexToColor(canvas.paperOf(page)), strokes, canvas.width.toFloat(), canvas.height.toFloat(),
                    decor = canvas.backgroundOf(page).takeIf { decor },
                ) {
                    if (includeGuides && !canvas.guide.isEmpty) {
                        with(com.karapp.drawguidance.ui.drawing.GuideRenderer) {
                            drawGuide(canvas.guide, com.karapp.drawguidance.ui.drawing.GuideRenderer.paperOf(canvas.width, canvas.height), 1f)
                        }
                    }
                    // v40 sayfa numarası, üst bilgi ve alt bilgi (tuvaldeki gibi çizimlerin altında)
                    drawPageDecor(canvas, page, (background ?: hexToColor(canvas.paperOf(page))).toArgb())
                }
            }
        }
        return bitmap
    }

    /** v40 sayfa yazıları (tuval biriminde; çizimlerin üstünde). */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPageDecor(canvas: CanvasUi, page: Int, paperArgb: Int) {
        if (canvas.pageSettings.isEmpty) return
        drawIntoCanvas {
            com.karapp.drawguidance.ui.drawing.PageDecorRenderer.draw(
                it.nativeCanvas, canvas.pageSettings, page, canvas.pageCount, canvas.width.toFloat(), canvas.height.toFloat(), paperArgb,
                cover = canvas.hasCover,
            )
        }
    }

    /** Varsayılan ayarlarla (Ayarlar ekranındaki çözünürlük ve biçim) açık sayfayı kaydeder. */
    fun export(canvas: CanvasUi): ExportedImage = export(canvas, defaultOptions(canvas)).first()

    fun defaultOptions(canvas: CanvasUi): ExportOptions {
        val s = settings?.settings?.value ?: com.karapp.drawguidance.data.settings.ExportSettings()
        return ExportOptions(
            fileName = defaultFileName(canvas.name),
            format = s.defaultFormat,
            scale = s.defaultScale,
            includeGuides = com.karapp.drawguidance.ui.drawing.GuideRenderer.exports(canvas.guide),
        )
    }

    /** Seçilen ayarlarla kaydeder; tüm sayfalar seçildiyse her sayfa ayrı resim olur. */
    fun export(canvas: CanvasUi, options: ExportOptions, folder: String? = null): List<ExportedImage> {
        val pages = if (options.allPages && canvas.pageCount > 1) canvas.pages.indices.toList() else listOf(canvas.currentPage)
        val base = safeName(options.fileName.ifBlank { defaultFileName(canvas.name) })
        // Birden çok sayfa: tüm sayfalar tuvalin klasörüne (v17: Resimlerim'deki klasörün adıyla)
        val subfolder = if (pages.size > 1) safeName(folder ?: canvas.name) else null
        // v40 "Farklı kaydet" tek dosyadır: birden çok sayfa varsayılan yere
        val target = if (pages.size > 1 && options.target is SaveTarget.Document) SaveTarget.Default else options.target
        val converter = options.cmyk?.let { com.karapp.drawguidance.data.print.CmykConverter(it) }
        return pages.mapIndexed { i, page ->
            val bitmap = render(canvas, options.scale, options.includeGuides, page)
            try {
                val name = (if (pages.size > 1) "${base}_s${i + 1}" else base) + "." + options.extension
                val bytes = if (converter != null) encodeCmyk(bitmap, converter, options.cmykFormat, options.quality) else encode(bitmap, options)
                saveImage(bytes, name, options.mime, bitmap.width, bitmap.height, subfolder, target)
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** v40 resmi seçilen yere yazar: tek dosya, seçilen klasör ya da varsayılan yer. */
    private fun saveImage(bytes: ByteArray, name: String, mime: String, width: Int, height: Int, subfolder: String?, target: SaveTarget): ExportedImage {
        when (target) {
            is SaveTarget.Document -> writeToUri(Uri.parse(target.uri), bytes)?.let {
                return ExportedImage(target.uri, target.label, width, height, bytes.size.toLong())
            }
            is SaveTarget.Folder -> saveToTree(Uri.parse(target.treeUri), target.label, bytes, name, mime, subfolder)?.let { (uri, location) ->
                return ExportedImage(uri, location, width, height, bytes.size.toLong())
            }
            SaveTarget.Default -> Unit
        }
        return saveToFolder(bytes, name, mime, width, height, subfolder)
            ?: saveToGallery(bytes, name, mime, width, height, subfolder)
            ?: saveToAppFolder(bytes, name, width, height, subfolder)
    }

    private fun writeToUri(uri: Uri, bytes: ByteArray): Unit? = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("yazılamadı")
    }.getOrNull()

    /** v40 CMYK resim baytları (JPEG ya da TIFF; PDF [exportCmykPdf] ile). */
    private fun encodeCmyk(bitmap: Bitmap, converter: com.karapp.drawguidance.data.print.CmykConverter, format: CmykFormat, quality: Int): ByteArray {
        val source = cmykSource(bitmap, converter)
        return when (format) {
            CmykFormat.JPEG -> com.karapp.drawguidance.data.print.CmykJpegEncoder.encode(source, quality)
            CmykFormat.TIFF -> com.karapp.drawguidance.data.print.CmykTiffEncoder.encode(source)
            CmykFormat.PDF -> java.io.ByteArrayOutputStream().use { out ->
                com.karapp.drawguidance.data.print.MiniPdfWriter(out).apply {
                    addPage(bitmap.width * 72f / 150f, bitmap.height * 72f / 150f, source)
                    finish()
                }
                out.toByteArray()
            }
        }
    }

    private fun cmykSource(bitmap: Bitmap, converter: com.karapp.drawguidance.data.print.CmykConverter) =
        com.karapp.drawguidance.data.print.ArgbCmykSource(bitmap.width, bitmap.height, converter) { y, count, dst ->
            bitmap.getPixels(dst, 0, bitmap.width, 0, y, bitmap.width, count)
        }

    /**
     * v40 baskıya hazır PDF (resim kaydetme penceresinden, CMYK + PDF): seçilen sayfalar tek PDF'te, her sayfa tuval
     * oranında kağıtta DeviceCMYK resim. Belge olarak kaydedilir.
     */
    fun exportCmykPdf(canvas: CanvasUi, options: ExportOptions): ExportedFile {
        val pdf = PdfOptions(
            fileName = options.fileName,
            pages = if (options.allPages && canvas.pageCount > 1) PdfPages.ALL else PdfPages.CURRENT,
            quality = PdfQuality.IMAGE,
            imageScale = options.scale,
            includeGuides = options.includeGuides,
            target = options.target,
            cmyk = options.cmyk ?: com.karapp.drawguidance.data.print.CmykOptions(),
        )
        return exportPdf(canvas, pdf)
    }

    /** v40 CMYK PDF'i akışa yazar: her sayfa (kenar boşlukları ve sayfa numarasıyla) tek DeviceCMYK resim. */
    fun writeCmykPdf(canvas: CanvasUi, options: PdfOptions, out: java.io.OutputStream) {
        val converter = com.karapp.drawguidance.data.print.CmykConverter(options.cmyk ?: com.karapp.drawguidance.data.print.CmykOptions())
        val writer = com.karapp.drawguidance.data.print.MiniPdfWriter(out, canvas.name)
        val layout = PdfLayout.of(canvas.width, canvas.height, options)
        val cw = canvas.width.toFloat()
        val ch = canvas.height.toFloat()
        options.pageIndices(canvas.pageCount, canvas.currentPage).forEach { index ->
            val paper = if (options.background) hexToColor(canvas.paperOf(index)) else androidx.compose.ui.graphics.Color.White
            val content = render(canvas.copy(currentPage = index), options.imageScale, options.includeGuides, index, paper, decor = options.background)
            val k = (content.width / (cw * layout.scale)).let { k ->
                // Kenar boşluklarıyla birlikte de en uzun kenar sınırı aşılmasın
                minOf(k, MAX_SIDE * 1.25f / maxOf(layout.pageWidth, layout.pageHeight))
            }
            val page = Bitmap.createBitmap((layout.pageWidth * k).roundToInt().coerceAtLeast(1), (layout.pageHeight * k).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                val c = android.graphics.Canvas(page)
                c.drawColor(paper.toArgb())
                c.drawBitmap(
                    content, null,
                    android.graphics.RectF(layout.left * k, layout.top * k, (layout.left + cw * layout.scale) * k, (layout.top + ch * layout.scale) * k),
                    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                )
                content.recycle()
                pdfNumberText(canvas, index)?.takeIf { options.pageNumbers }?.let { text ->
                    c.save(); c.scale(k, k)
                    drawPageNumber(c, layout, text, paper)
                    c.restore()
                }
                writer.addPage(layout.pageWidth.toFloat(), layout.pageHeight.toFloat(), cmykSource(page, converter))
            } finally {
                if (!content.isRecycled) content.recycle()
                page.recycle()
            }
        }
        writer.finish()
    }

    /**
     * v40 çizgi roman arşivi (.cbz): seçilen sayfalar 00001.png/jpg … ve ComicInfo.xml; sayfalar akışa sırayla yazılır
     * (bin sayfalık çizgi roman da belleğe sığar). Sayfa numarası, üst ve alt bilgi sayfalarda çizilir.
     */
    fun exportCbz(canvas: CanvasUi, options: CbzOptions): ExportedFile {
        val name = safeName(options.fileName.ifBlank { defaultFileName(canvas.name) }) + ".cbz"
        val mime = runCatching { android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension("cbz") }.getOrNull() ?: "application/octet-stream"
        return writeDocument(name, mime, options.target) { out -> writeCbz(canvas, options, out) }
    }

    /** CBZ içeriğini akışa yazar. */
    fun writeCbz(canvas: CanvasUi, options: CbzOptions, out: java.io.OutputStream) {
        val writer = com.karapp.drawguidance.data.print.CbzWriter(out, canvas.name, Locale.getDefault().language)
        val image = ExportOptions(options.fileName, options.format, options.scale, options.quality)
        options.pageIndices(canvas.pageCount, canvas.currentPage).forEach { index ->
            val bitmap = render(canvas.copy(currentPage = index), options.scale, includeGuides = false, page = index)
            try {
                writer.addPage(encode(bitmap, image), bitmap.width, bitmap.height, options.format.extension)
            } finally {
                bitmap.recycle()
            }
        }
        writer.finish()
    }

    /**
     * Seçilen sayfaları tek PDF'e aktarır (v19: [PdfOptions] — sayfalar, kağıt boyutu ve yönü, kenar boşluğu, kalite,
     * arka plan, kılavuz ve sayfa numarası). Keskin kalitede fırça izleri silgi içermeyen sayfalar vektör olarak (şekil,
     * yazı, formül keskin kalır), silgili sayfalar ve "Resim" kalitesinde tüm sayfalar resim olarak yazılır. Gizli
     * katmanlar girmez. Dosya İndirilenler/DrawGuidance klasörüne (Android 10+) ya da uygulamanın belgeler klasörüne
     * kaydedilir.
     */
    fun exportPdf(canvas: CanvasUi, options: PdfOptions = PdfOptions(defaultFileName(canvas.name))): ExportedFile {
        val name = safeName(options.fileName.ifBlank { defaultFileName(canvas.name) }) + ".pdf"
        // v40 baskıya hazır (CMYK) PDF elle yazılır (Android'in PdfDocument'i yalnız RGB)
        if (options.cmyk != null) return writeDocument(name, "application/pdf", options.target) { writeCmykPdf(canvas, options, it) }
        val bytes = renderPdf(canvas, options)
        return saveDocument(bytes, name, "application/pdf", options.target)
    }

    /** PDF'in baytları (kaydetmeden). */
    fun renderPdf(canvas: CanvasUi, options: PdfOptions): ByteArray {
        val pdf = pdfSinkFactory()
        try {
            val layout = PdfLayout.of(canvas.width, canvas.height, options)
            val indices = options.pageIndices(canvas.pageCount, canvas.currentPage)
            val cw = canvas.width.toFloat()
            val ch = canvas.height.toFloat()
            indices.forEachIndexed { number, index ->
                // v24 her sayfa kendi kağıt rengi ve deseni/resmiyle
                val paper = if (options.background) hexToColor(canvas.paperOf(index)) else androidx.compose.ui.graphics.Color.White
                val pageDecor = canvas.backgroundOf(index)
                val rawStrokes = canvas.pages.getOrElse(index) { canvas.strokes }
                val strokes = com.karapp.drawguidance.data.model.Layers.visible(rawStrokes, canvas.layers)
                val native = pdf.startPage(layout.pageWidth, layout.pageHeight, number + 1)
                // Sayfanın tamamı kağıt renginde (kenar boşlukları da)
                native.drawColor(paper.toArgb())
                val vector = options.quality == PdfQuality.SHARP && strokes.none { it.brushType == com.karapp.drawguidance.data.model.BrushType.ERASER }
                if (vector) {
                    CanvasDrawScope().draw(
                        Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(native),
                        Size(layout.pageWidth.toFloat(), layout.pageHeight.toFloat()),
                    ) {
                        translate(layout.left, layout.top) {
                            scale(layout.scale, layout.scale, pivot = Offset.Zero) {
                                clipRect(0f, 0f, cw, ch) {
                                    drawRect(paper, Offset.Zero, Size(cw, ch))
                                    if (options.background && !pageDecor.isEmpty) drawIntoCanvas {
                                        com.karapp.drawguidance.ui.drawing.BackgroundRenderer.drawCanvasBackground(
                                            it.nativeCanvas, pageDecor, 0f, 0f, cw, ch, 1f, paper.toArgb(), vector = true,
                                        )
                                    }
                                    if (options.includeGuides && !canvas.guide.isEmpty) {
                                        with(com.karapp.drawguidance.ui.drawing.GuideRenderer) {
                                            drawGuide(canvas.guide, com.karapp.drawguidance.ui.drawing.GuideRenderer.paperOf(canvas.width, canvas.height), 1f)
                                        }
                                    }
                                    // v40 sayfa numarası, üst bilgi ve alt bilgi
                                    drawPageDecor(canvas, index, paper.toArgb())
                                    with(com.karapp.drawguidance.ui.drawing.StrokeRenderer) { drawStrokeList(strokes) }
                                }
                            }
                        }
                    }
                } else {
                    val bitmap = render(canvas.copy(currentPage = index), options.imageScale, options.includeGuides, index, paper, decor = options.background)
                    try {
                        native.drawBitmap(
                            bitmap, null,
                            android.graphics.RectF(layout.left, layout.top, layout.left + cw * layout.scale, layout.top + ch * layout.scale),
                            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                        )
                    } finally { bitmap.recycle() }
                }
                if (options.pageNumbers) pdfNumberText(canvas, index)?.let { drawPageNumber(native, layout, it, paper) }
                pdf.finishPage()
            }
            return pdf.finish()
        } finally {
            pdf.close()
        }
    }

    /** Sayfa numarası: alttaki şeridin ortasında, kağıda göre koyu ya da açık gri. */
    private fun drawPageNumber(native: android.graphics.Canvas, layout: PdfPageLayout, text: String, paper: androidx.compose.ui.graphics.Color) {
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(paper.toArgb()) < 0.45
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFFBDBDBD.toInt() else 0xFF616161.toInt()
            textSize = 9f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val band = maxOf(layout.footer, 1f)
        val baseline = layout.pageHeight - band / 2f - (paint.ascent() + paint.descent()) / 2f
        native.drawText(text, layout.pageWidth / 2f, baseline, paint)
    }

    /** PDF'in kaydedileceği yer (kullanıcıya gösterilir). */
    val pdfLocationLabel: String
        get() = if (useGallery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
        else "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER"

    /** Belgeyi (PDF) İndirilenler/DrawGuidance'a ya da uygulamanın belgeler klasörüne yazar; v40 seçilen yere. */
    private fun saveDocument(bytes: ByteArray, fileName: String, mime: String, target: SaveTarget = SaveTarget.Default): ExportedFile =
        writeDocument(fileName, mime, target) { it.write(bytes) }

    /**
     * v40 belgeyi akışla yazar: "Farklı kaydet" dosyasına, seçilen klasöre ya da varsayılan yere (İndirilenler/
     * DrawGuidance; olmazsa uygulamanın belgeler klasörü). Yazılamayan seçili yer varsayılana düşer.
     */
    fun writeDocument(fileName: String, mime: String, target: SaveTarget, write: (java.io.OutputStream) -> Unit): ExportedFile {
        val resolver = context.contentResolver
        when (target) {
            is SaveTarget.Document -> {
                val uri = Uri.parse(target.uri)
                val size = runCatching {
                    resolver.openOutputStream(uri, "wt")?.use { out ->
                        val counting = com.karapp.drawguidance.data.print.CountingStream(out)
                        write(counting); counting.flush(); counting.count
                    }
                }.getOrNull()
                if (size != null) return ExportedFile(target.uri, target.label, size)
            }
            is SaveTarget.Folder -> {
                val tree = Uri.parse(target.treeUri)
                val written = runCatching {
                    val doc = DocumentsContract.createDocument(resolver, treeRoot(tree), mime, fileName) ?: return@runCatching null
                    val size = resolver.openOutputStream(doc)?.use { out ->
                        val counting = com.karapp.drawguidance.data.print.CountingStream(out)
                        write(counting); counting.flush(); counting.count
                    }
                    if (size == null) { runCatching { DocumentsContract.deleteDocument(resolver, doc) }; null }
                    else ExportedFile(doc.toString(), "${target.label}/$fileName", size)
                }.getOrNull()
                if (written != null) return written
            }
            SaveTarget.Default -> Unit
        }
        if (useGallery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = runCatching { resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull()
            if (uri != null) {
                val size = runCatching {
                    resolver.openOutputStream(uri)?.use { out ->
                        val counting = com.karapp.drawguidance.data.print.CountingStream(out)
                        write(counting); counting.flush(); counting.count
                    }
                }.getOrNull()
                if (size != null) {
                    values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    runCatching { resolver.update(uri, values, null, null) }
                    return ExportedFile(uri.toString(), "$relativePath/$fileName", size)
                }
                runCatching { resolver.delete(uri, null, null) }
            }
        }
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: File(context.filesDir, "documents")
        val dir = File(baseDir, FOLDER).apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().buffered().use(write)
        return ExportedFile(Uri.fromFile(file).toString(), file.absolutePath, file.length())
    }

    /** Kaydedilmiş bir resmi siler (kendi oluşturduğumuz dosya). */
    fun delete(uri: String) {
        val parsed = Uri.parse(uri)
        runCatching {
            when {
                parsed.scheme == "file" -> File(requireNotNull(parsed.path)).delete()
                DocumentsContract.isDocumentUri(context, parsed) && parsed.authority != MediaStore.AUTHORITY ->
                    DocumentsContract.deleteDocument(context.contentResolver, parsed)
                else -> context.contentResolver.delete(parsed, null, null)
            }
        }
    }

    private val album: String get() = settings?.settings?.value?.albumName ?: FOLDER

    /** Kullanıcıya gösterilen kaydetme konumu (Ayarlar'da seçilen klasör ya da Galeri albümü). */
    val locationLabel: String get() = settings?.settings?.value?.locationLabel ?: "Pictures/$album"

    /** Ayarlar'da seçilen klasöre (kalıcı izinli klasör) yazar; seçilmemişse ya da yazılamazsa null. */
    private fun saveToFolder(bytes: ByteArray, fileName: String, mime: String, width: Int, height: Int, subfolder: String? = null): ExportedImage? {
        val s = settings?.settings?.value ?: return null
        val tree = s.folderUri?.let { Uri.parse(it) } ?: return null
        return saveToTree(tree, s.locationLabel, bytes, fileName, mime, subfolder)?.let { (uri, location) ->
            ExportedImage(uri, location, width, height, bytes.size.toLong())
        }
    }

    /** İzinli klasöre (alt klasörüyle) yazar: (adres, gösterilen konum); yazılamazsa null. */
    private fun saveToTree(tree: Uri, label: String, bytes: ByteArray, fileName: String, mime: String, subfolder: String?): Pair<String, String>? {
        val resolver = context.contentResolver
        return runCatching {
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val parent = if (subfolder == null) root else findOrCreateDir(root, tree, subfolder) ?: root
            val doc = DocumentsContract.createDocument(resolver, parent, mime, fileName) ?: return null
            val written = resolver.openOutputStream(doc)?.use { it.write(bytes); true } == true
            if (!written) {
                runCatching { DocumentsContract.deleteDocument(resolver, doc) }
                null
            } else {
                doc.toString() to "$label/${subfolder?.let { "$it/" } ?: ""}$fileName"
            }
        }.getOrNull()
    }

    /** Seçilen klasörde alt klasör (varsa onu kullanır). */
    private fun findOrCreateDir(parent: Uri, tree: Uri, name: String): Uri? = runCatching {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name && c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return@runCatching DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
        }
        DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
    }.getOrNull()

    private fun saveToGallery(bytes: ByteArray, fileName: String, mime: String, width: Int, height: Int, subfolder: String? = null): ExportedImage? {
        if (!useGallery || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_PICTURES}/$album" + (subfolder?.let { "/$it" } ?: "")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        }.getOrNull() ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes); true } == true
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        runCatching { resolver.update(uri, values, null, null) }
        return ExportedImage(uri.toString(), "$relativePath/$fileName", width, height, bytes.size.toLong())
    }

    private fun saveToAppFolder(bytes: ByteArray, fileName: String, width: Int, height: Int, subfolder: String? = null): ExportedImage {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "pictures")
        val dir = (if (subfolder == null) File(baseDir, album) else File(File(baseDir, album), subfolder)).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return ExportedImage(Uri.fromFile(file).toString(), file.absolutePath, width, height, bytes.size.toLong())
    }

    // --- v17: resim klasörleri kayıt yerinde gerçek klasördür ---

    /** Taşınan resmin yeni adresi ve gösterilen konumu. */
    data class MovedImage(val uri: String, val location: String)

    private fun treeUri(): Uri? = settings?.settings?.value?.folderUri?.let { Uri.parse(it) }

    private fun treeRoot(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun appDir(sub: String?): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "pictures")
        return if (sub == null) File(baseDir, album) else File(File(baseDir, album), sub)
    }

    @Suppress("DEPRECATION")
    private fun galleryDir(sub: String?): File {
        val base = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), album)
        return if (sub == null) base else File(base, sub)
    }

    /**
     * Resim klasörünü kayıt yerinde oluşturur: Ayarlar'da seçilen klasörde (izinli klasör) alt klasör; yoksa Galeri'de
     * Pictures/<albüm>/<klasör> (Android sürümü izin vermezse ilk resim taşınınca oluşur); yoksa uygulama klasöründe.
     */
    fun createFolder(name: String) {
        val sub = safeName(name)
        val tree = treeUri()
        when {
            tree != null -> runCatching { findOrCreateDir(treeRoot(tree), tree, sub) }
            useGallery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> runCatching { galleryDir(sub).mkdirs() }
            else -> runCatching { appDir(sub).mkdirs() }
        }
    }

    /** Boşalan klasörü kayıt yerinden kaldırır (içinde başka dosya varsa dokunulmaz). */
    fun deleteFolder(name: String) {
        val sub = safeName(name)
        runCatching {
            treeUri()?.let { tree ->
                val root = treeRoot(tree)
                findDir(root, tree, sub)?.let { dir -> if (childCount(tree, dir) == 0) DocumentsContract.deleteDocument(context.contentResolver, dir) }
            }
        }
        runCatching { galleryDir(sub).takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete() }
        runCatching { appDir(sub).takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete() }
    }

    /**
     * Kaydedilmiş resmi kayıt yerinde [folderName] klasörüne (null: albümün kendisine) taşır: Galeri'de resmin yolu
     * değişir (adresi aynı kalır), izinli klasörde belge taşınır (desteklenmezse kopyalanıp eskisi silinir), uygulama
     * klasöründe dosya taşınır. Taşınamazsa null (resim yerinde kalır).
     */
    fun moveImage(uri: String, location: String, folderName: String?): MovedImage? {
        val parsed = Uri.parse(uri)
        val sub = folderName?.let { safeName(it) }
        val fileName = location.substringAfterLast('/').ifBlank { parsed.lastPathSegment?.substringAfterLast('/') ?: "resim.png" }
        val resolver = context.contentResolver
        return runCatching {
            when {
                parsed.scheme == "file" -> {
                    val src = File(requireNotNull(parsed.path))
                    val dir = appDir(sub).apply { mkdirs() }
                    if (src.parentFile?.absolutePath == dir.absolutePath) return MovedImage(uri, location)
                    val dst = uniqueFile(dir, src.name)
                    if (!src.renameTo(dst)) { src.copyTo(dst); src.delete() }
                    MovedImage(Uri.fromFile(dst).toString(), dst.absolutePath)
                }
                parsed.authority == MediaStore.AUTHORITY -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
                    val rel = "${Environment.DIRECTORY_PICTURES}/$album" + (sub?.let { "/$it" } ?: "")
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, "$rel/") }
                    if (resolver.update(parsed, values, null, null) <= 0) null else MovedImage(uri, "$rel/$fileName")
                }
                DocumentsContract.isDocumentUri(context, parsed) -> {
                    val tree = treeUri() ?: return null
                    val root = treeRoot(tree)
                    val target = if (sub == null) root else findOrCreateDir(root, tree, sub) ?: return null
                    val label = settings?.settings?.value?.locationLabel ?: "Pictures/$album"
                    val shown = "$label/${sub?.let { "$it/" } ?: ""}$fileName"
                    // Önce belgeyi taşı (adres değişir, dosya aynı); desteklenmezse kopyala + sil
                    val parent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        runCatching { DocumentsContract.findDocumentPath(resolver, parsed)?.path?.let { p -> p.getOrNull(p.size - 2) } }.getOrNull()
                            ?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
                    } else null
                    if (parent != null && parent == target) return MovedImage(uri, shown)
                    val moved = if (parent != null) runCatching { DocumentsContract.moveDocument(resolver, parsed, parent, target) }.getOrNull() else null
                    if (moved != null) return MovedImage(moved.toString(), shown)
                    val mime = resolver.getType(parsed) ?: "image/png"
                    val doc = DocumentsContract.createDocument(resolver, target, mime, fileName) ?: return null
                    val ok = resolver.openInputStream(parsed)?.use { input -> resolver.openOutputStream(doc)?.use { input.copyTo(it); true } } == true
                    if (!ok) { runCatching { DocumentsContract.deleteDocument(resolver, doc) }; return null }
                    runCatching { DocumentsContract.deleteDocument(resolver, parsed) }
                    MovedImage(doc.toString(), shown)
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 2
        while (f.exists()) { f = File(dir, name.substringBeforeLast('.') + "_$i." + name.substringAfterLast('.', "png")); i++ }
        return f
    }

    private fun findDir(parent: Uri, tree: Uri, name: String): Uri? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name && c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) return@runCatching DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        null
    }.getOrNull()

    private fun childCount(tree: Uri, dir: Uri): Int = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(dir))
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.count } ?: 0
    }.getOrDefault(1)

    /** Resim baytları (dosya boyutu kesin olarak bilinsin diye önce bellekte kodlanır). */
    private fun encode(bitmap: Bitmap, options: ExportOptions): ByteArray = java.io.ByteArrayOutputStream().use {
        when (options.format) {
            com.karapp.drawguidance.data.settings.ImageFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            com.karapp.drawguidance.data.settings.ImageFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, options.quality.coerceIn(1, 100), it)
        }
        it.toByteArray()
    }

    /** v25 hazır bir resmi (3B şeklin görüntüsü) seçilen biçimde kayıt yerine yazar. */
    fun exportBitmap(
        bitmap: Bitmap, baseName: String, format: com.karapp.drawguidance.data.settings.ImageFormat, quality: Int = 92,
        /** v40 kaydetme yeri ("Konum seç"). */
        target: SaveTarget = SaveTarget.Default,
    ): ExportedImage {
        val name = defaultFileName(baseName.ifBlank { "3B" }) + "." + format.extension
        val bytes = encode(bitmap, ExportOptions(fileName = name, format = format, quality = quality))
        return saveImage(bytes, name, format.mime, bitmap.width, bitmap.height, null, target)
    }

    /** Kayıtlı bir resmin dosya boyutu (bulunamazsa 0). */
    fun sizeOf(uri: String): Long = runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") File(requireNotNull(parsed.path)).length()
        else context.contentResolver.query(parsed, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)


    companion object {
        const val FOLDER = "DrawGuidance"

        /** PDF yazıcısı (testlerde sayfaları resme çizen sahte yazıcı verilir). */
        @androidx.annotation.VisibleForTesting
        @Volatile
        var pdfSinkFactory: () -> PdfSink = { AndroidPdfSink() }
        /**
         * Çok büyük tuvallerde bellek taşmasını önlemek için en uzun kenar sınırı: normalde 4096; v18'de düşük
         * bellekli ve Android 8.0 öncesi cihazlarda belleğe göre küçülür (bkz. DeviceCaps).
         */
        val MAX_SIDE: Int get() = com.karapp.drawguidance.platform.DeviceCaps.exportMaxSide

        /**
         * v45 PDF kenar şeridindeki sayfa numarası ("3 / 12"): kapaklı çizgi romanda kapak numarasız (null), numaralar
         * kapaktan sonraki sayfada 1'den başlar ve toplam kapağı saymaz.
         */
        fun pdfNumberText(canvas: CanvasUi, index: Int): String? {
            val cover = canvas.hasCover
            val n = com.karapp.drawguidance.data.model.PageNumbering.number(index, cover) ?: return null
            return "$n / ${com.karapp.drawguidance.data.model.PageNumbering.count(canvas.pageCount, cover).coerceAtLeast(1)}"
        }

        /** Dosya sisteminde güvenli ad: harf, rakam, _ ve - dışındaki karakterler _ olur. */
        fun safeName(name: String): String =
            name.trim().ifEmpty { "Tuval" }.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(60)

        /** Varsayılan dosya adı: tuval adı + tarih/saat. */
        fun defaultFileName(canvasName: String): String =
            "${canvasName.trim().ifEmpty { "Tuval" }}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
    }
}
