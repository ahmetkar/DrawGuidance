package com.karapp.drawguidance.ui.screens.canvasedit.state

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ExportOptions
import com.karapp.drawguidance.data.images.ImageCheckResult
import com.karapp.drawguidance.data.images.ImageImporter
import com.karapp.drawguidance.data.images.ImageIssue
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.images.PreparedImage
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.screens.canvasedit.EditEvent
import com.karapp.drawguidance.ui.screens.canvasedit.ImageImportState
import com.karapp.drawguidance.ui.screens.canvasedit.ImageSaveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** PDF dışa aktarma durumu. */
sealed interface PdfExportState {
    data object Idle : PdfExportState
    data object Exporting : PdfExportState
    data class Done(val file: com.karapp.drawguidance.data.images.ExportedFile) : PdfExportState
    /** v40 baskıya hazır (CMYK TIFF/PDF) belge kaydedildi (resim kaydetme penceresinden). */
    data class PrintDone(val file: com.karapp.drawguidance.data.images.ExportedFile) : PdfExportState
    data object Failed : PdfExportState
}

/** Resimler: tuvale resim ekleme (kontrol, onay) ve tuvali resim olarak kaydetme (ayar penceresi). */
class ImageController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val assetStore: CanvasAssetStore,
    private val imageImporter: ImageImporter,
    private val imagesRepository: ImagesRepository,
) {
    // --- v27 çizgi roman filtreleri ---

    private val _filtering = MutableStateFlow(false)
    /** Seçili resme filtre uygulanıyor. */
    val filtering: StateFlow<Boolean> = _filtering.asStateFlow()

    /**
     * Seçili resme çizgi roman filtresi uygular (çizgi roman, ton noktaları, manga, pop art): yeni resim verisi
     * olarak saklanır, resim elemanı onu gösterir (tek geri alma adımı; özgün veri geri almada geri gelir).
     */
    fun filterSelected(kind: com.karapp.drawguidance.content.comic.ComicFilter) =
        processSelected(MSG_FILTER_FAIL) { px, w, h -> com.karapp.drawguidance.content.comic.ComicFilters.apply(kind, px, w, h) }

    /** v30 seçili resme hazır görünüm ve genel ayarlar (parlaklık, karşıtlık, bulanıklık, vinyet…). */
    fun effectsSelected(look: com.karapp.drawguidance.content.image.ImageLook, adjust: com.karapp.drawguidance.content.image.ImageAdjust) {
        if (look == com.karapp.drawguidance.content.image.ImageLook.NONE && adjust.isIdentity) return
        processSelected(MSG_EFFECT_FAIL) { px, w, h -> com.karapp.drawguidance.content.image.ImageEffects.apply(px, w, h, look, adjust) }
    }

    /** v30 seçili resmin arka planını kaldırır (saydam zeminli yeni resim; kaldırılan pay bildirilir). */
    fun removeBackgroundSelected(options: com.karapp.drawguidance.content.image.BackgroundOptions) =
        processSelected(MSG_BG_FAIL, onDone = { ratio -> host.emit(EditEvent.BackgroundRemoved((ratio * 100).toInt())) }) { px, w, h ->
            val mask = com.karapp.drawguidance.content.image.BackgroundRemover.mask(px, w, h, options)
            lastRemovedRatio = mask.removedRatio
            com.karapp.drawguidance.content.image.BackgroundRemover.apply(px, w, h, mask)
        }

    @Volatile private var lastRemovedRatio = 0f

    /**
     * v30 seçili resmin önizleme için küçültülmüş kopyası ([maxSide] kenar). Seçili tek resim yoksa null.
     * Araç pencereleri (efektler, arka plan) önizlemeyi bununla arka planda hesaplar.
     */
    suspend fun selectedPreviewSource(maxSide: Int): android.graphics.Bitmap? {
        val image = store.selectedStroke { it.brushType == BrushType.IMAGE }?.takeIf { store.selection.size == 1 } ?: return null
        val assetId = image.assetId ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val bmp = com.karapp.drawguidance.data.images.CanvasImageCache.get(assetId)?.asAndroidBitmap()
                    ?: assetStore.bytes(assetId)?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                    ?: return@runCatching null
                val source = com.karapp.drawguidance.platform.Compat.readable(bmp)
                val scale = minOf(1f, maxSide.toFloat() / maxOf(source.width, source.height))
                if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
                else source
            }.getOrNull()
        }
    }

    /**
     * Seçili resmin piksellerini arka planda [transform] ile işler; sonuç yeni resim verisi olarak saklanır ve resim
     * elemanı onu gösterir (tek geri alma adımı). Çok büyük resimler cihaz sınıfına göre küçültülür.
     */
    private fun processSelected(failMessage: String, onDone: ((Float) -> Unit)? = null, transform: (IntArray, Int, Int) -> IntArray) {
        val image = store.selectedStroke { it.brushType == BrushType.IMAGE }?.takeIf { store.selection.size == 1 } ?: return
        val assetId = image.assetId ?: return
        if (_filtering.value) return
        _filtering.value = true
        host.scope.launch {
            try {
                val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val bmp = com.karapp.drawguidance.data.images.CanvasImageCache.get(assetId)?.asAndroidBitmap()
                        ?: assetStore.bytes(assetId)?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                        ?: return@withContext null
                    val source = com.karapp.drawguidance.platform.Compat.readable(bmp)
                    // Çok büyük resimler küçültülür (bellek ve süre; giriş düzeyi cihazlar)
                    val maxSide = if (com.karapp.drawguidance.platform.DeviceCaps.tier == com.karapp.drawguidance.platform.DeviceCaps.Tier.LOW) 1100f else 1600f
                    val scale = minOf(1f, maxSide / maxOf(source.width, source.height))
                    val src = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
                    val w = src.width; val h = src.height
                    val px = IntArray(w * h)
                    src.getPixels(px, 0, w, 0, 0, w, h)
                    val out = transform(px, w, h)
                    val result = android.graphics.Bitmap.createBitmap(out, w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    val opaque = out.all { it ushr 24 == 0xFF }
                    val bytes = com.karapp.drawguidance.data.library.BitmapCodec.encode(result, opaque = opaque)
                    PreparedImage(bytes, if (opaque) "image/webp" else "image/png", w, h, result, bytes.size, 0f)
                } ?: return@launch
                val newId = assetStore.save(store.canvasLocalId, prepared)
                val current = store.canvas.value ?: return@launch
                if (current.strokes.none { it.id == image.id }) return@launch
                store.commit(current.strokes.map { if (it.id == image.id) it.copy(assetId = newId) else it })
                onDone?.invoke(lastRemovedRatio)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // v27 bellek yetmezse de (OutOfMemoryError) uygulama kapanmaz
                com.karapp.drawguidance.diagnostics.AppLog.e("ImageProcess", failMessage, e, userVisible = true)
                host.emit(EditEvent.Message(failMessage))
            } finally {
                _filtering.value = false
            }
        }
    }

    // --- Resim olarak kaydetme ---

    private val _save = MutableStateFlow<ImageSaveState>(ImageSaveState.Idle)
    /** Resim olarak kaydetme durumu (kaydedilince boyut/konum penceresi gösterilir). */
    val save: StateFlow<ImageSaveState> = _save.asStateFlow()

    private val _saveOptions = MutableStateFlow<ExportOptions?>(null)
    /** Açık kaydetme penceresinin başlangıç ayarları (null: pencere kapalı). */
    val saveOptions: StateFlow<ExportOptions?> = _saveOptions.asStateFlow()

    /** Resimlerin kaydedileceği yer. */
    val saveLocation: String get() = imagesRepository.locationLabel

    /** Başlıktaki kaydet düğmesi: kaydetme ayarlarını soran pencereyi açar. */
    fun openSaveDialog() {
        val current = store.canvas.value ?: return
        if (_save.value == ImageSaveState.Saving) return
        _saveOptions.value = imagesRepository.defaultOptions(current)
    }

    fun closeSaveDialog() {
        _saveOptions.value = null
    }

    /** Seçilen çözünürlükte kaydedilecek resmin piksel boyutu. */
    fun outputSize(scale: Float): Pair<Int, Int> {
        val current = store.canvas.value ?: return 0 to 0
        return imagesRepository.outputSize(current, scale)
    }

    /** Tuvalin şu anki halini varsayılan ayarlarla kaydeder. */
    fun saveAsImage() {
        val current = store.canvas.value ?: return
        saveAsImage(imagesRepository.defaultOptions(current))
    }

    /** Tuvalin şu anki halini penceredeki ayarlarla kaydeder. */
    fun saveAsImage(options: ExportOptions) {
        val current = store.canvas.value ?: return
        _saveOptions.value = null
        // v40 CMYK TIFF/PDF Resimlerim'e eklenmez: belge olarak kaydedilir (bildirimde yeri gösterilir)
        if (options.isDocument) { exportPrintDocument(current, options); return }
        if (_save.value == ImageSaveState.Saving) return
        _save.value = ImageSaveState.Saving
        host.scope.launch {
            _save.value = try {
                val images = imagesRepository.saveCanvasAsImage(current, options)
                if (images.isEmpty()) ImageSaveState.Failed else ImageSaveState.Saved(images)
            } catch (e: Exception) {
                com.karapp.drawguidance.diagnostics.AppLog.e("ImageSave", "Resim kaydedilemedi", e, userVisible = true)
                ImageSaveState.Failed
            }
        }
    }

    fun dismissSave() {
        _save.value = ImageSaveState.Idle
    }

    // --- PDF olarak dışa aktarma (ders notları) ---

    private val _pdf = MutableStateFlow<PdfExportState>(PdfExportState.Idle)
    val pdf: StateFlow<PdfExportState> = _pdf.asStateFlow()

    private val _pdfOptions = MutableStateFlow<com.karapp.drawguidance.data.images.PdfOptions?>(null)
    /** v19 açık "PDF olarak kaydet" penceresinin başlangıç ayarları (null: pencere kapalı). */
    val pdfOptions: StateFlow<com.karapp.drawguidance.data.images.PdfOptions?> = _pdfOptions.asStateFlow()

    /** Son kullanılan PDF ayarları (bu oturumda; dosya adı ve sayfa seçimi her seferinde yenilenir). */
    private var lastPdf: com.karapp.drawguidance.data.images.PdfOptions? = null

    /** PDF'in kaydedileceği yer. */
    val pdfLocation: String get() = imagesRepository.pdfLocationLabel

    /** ⋮ → PDF olarak kaydet: ayarları soran pencereyi açar. */
    fun openPdfDialog() {
        val current = store.canvas.value ?: return
        if (_pdf.value == PdfExportState.Exporting) return
        val defaults = imagesRepository.defaultPdfOptions(current)
        _pdfOptions.value = lastPdf?.copy(
            fileName = defaults.fileName,
            pages = defaults.pages, fromPage = 1, toPage = current.pageCount.coerceAtLeast(1),
            includeGuides = lastPdf!!.includeGuides && !current.guide.isEmpty,
        ) ?: defaults.copy(toPage = current.pageCount.coerceAtLeast(1))
    }

    fun closePdfDialog() {
        _pdfOptions.value = null
    }

    /** v42 birleşik Kaydet penceresinde başlangıç ayarlarını değiştirir (ör. varsayılan CMYK). */
    fun adjustSaveDefaults(image: (ExportOptions) -> ExportOptions, pdf: (com.karapp.drawguidance.data.images.PdfOptions) -> com.karapp.drawguidance.data.images.PdfOptions) {
        _saveOptions.value?.let { _saveOptions.value = image(it) }
        _pdfOptions.value?.let { _pdfOptions.value = pdf(it) }
    }

    /** Penceredeki ayarlarla PDF olarak kaydeder (yalnızca görünür katmanlar). */
    fun exportPdf(options: com.karapp.drawguidance.data.images.PdfOptions? = null) {
        val current = store.canvas.value ?: return
        _pdfOptions.value = null
        if (_pdf.value == PdfExportState.Exporting) return
        val chosen = options ?: imagesRepository.defaultPdfOptions(current)
        lastPdf = chosen
        _pdf.value = PdfExportState.Exporting
        host.scope.launch {
            _pdf.value = try {
                PdfExportState.Done(imagesRepository.exportPdf(current, chosen))
            } catch (e: Exception) {
                com.karapp.drawguidance.diagnostics.AppLog.e("PdfExport", "PDF kaydedilemedi", e, userVisible = true)
                PdfExportState.Failed
            }
        }
    }

    fun dismissPdf() { _pdf.value = PdfExportState.Idle }

    /** v40 baskıya hazır belge (CMYK TIFF ya da PDF). */
    private fun exportPrintDocument(current: com.karapp.drawguidance.data.model.CanvasUi, options: ExportOptions) {
        if (_pdf.value == PdfExportState.Exporting) return
        _pdf.value = PdfExportState.Exporting
        host.scope.launch {
            _pdf.value = try {
                PdfExportState.PrintDone(imagesRepository.exportCmykDocument(current, options))
            } catch (e: Exception) {
                com.karapp.drawguidance.diagnostics.AppLog.e("PrintExport", "Baskı dosyası kaydedilemedi", e, userVisible = true)
                PdfExportState.Failed
            }
        }
    }

    /** v40 CMYK provası için tuvalin küçük resmi. */
    suspend fun previewBitmap(maxSide: Int = 320): android.graphics.Bitmap? {
        val current = store.canvas.value ?: return null
        return runCatching { imagesRepository.previewBitmap(current, maxSide) }.getOrNull()
    }

    // --- Resim ekleme ---

    private val _import = MutableStateFlow<ImageImportState?>(null)
    val import: StateFlow<ImageImportState?> = _import.asStateFlow()

    /** Seçilen resmi kontrol eder; hata varsa reddeder, uyarı varsa onay ister, yoksa ekler. */
    fun importImage(uri: Uri, center: Offset) {
        if (_import.value == ImageImportState.Checking) return
        _import.value = ImageImportState.Checking
        host.scope.launch {
            val result = runCatching { imageImporter.check(uri) }.getOrElse {
                com.karapp.drawguidance.diagnostics.AppLog.w("ImageImport", "Resim okunamadı: $uri", it)
                ImageCheckResult(listOf(ImageIssue.Unreadable), emptyList(), null)
            }
            val image = result.image
            _import.value = when {
                !result.accepted || image == null -> ImageImportState.Rejected(result.errors.ifEmpty { listOf(ImageIssue.Unreadable) }, result.warnings)
                result.warnings.isNotEmpty() -> ImageImportState.NeedsConfirmation(result.warnings, image, center)
                else -> {
                    addImage(image, center)
                    null
                }
            }
        }
    }

    /** v29 Resimlerim'den (uygulamanın kaydettiği) resim: sınır kontrolü olmadan, küçültülerek eklenir. */
    fun importSaved(uri: Uri, center: Offset) {
        if (_import.value == ImageImportState.Checking) return
        _import.value = ImageImportState.Checking
        host.scope.launch {
            val image = imageImporter.prepareSaved(uri)
            if (image == null) {
                _import.value = ImageImportState.Rejected(listOf(ImageIssue.Unreadable), emptyList())
                return@launch
            }
            _import.value = null
            addImage(image, center)
        }
    }

    /** v23 galeriden arka plan resmi: kontrol edilir, tuvalin resim verisine eklenir; kimliği [onAdded]'a verilir. */
    fun importBackground(uri: Uri, onAdded: (String) -> Unit) {
        host.scope.launch {
            val result = runCatching { imageImporter.check(uri) }.getOrNull()
            val image = result?.image?.takeIf { result.accepted }
            if (image == null) {
                _import.value = ImageImportState.Rejected(result?.errors?.ifEmpty { null } ?: listOf(ImageIssue.Unreadable), result?.warnings ?: emptyList())
                return@launch
            }
            val id = try {
                assetStore.save(store.canvasLocalId, image)
            } catch (e: Exception) {
                com.karapp.drawguidance.diagnostics.AppLog.e("ImageImport", "Arka plan resmi yazılamadı", e, userVisible = true)
                return@launch
            }
            onAdded(id)
        }
    }

    fun confirmImport() {
        val pending = _import.value as? ImageImportState.NeedsConfirmation ?: return
        _import.value = null
        host.scope.launch { addImage(pending.image, pending.center) }
    }

    fun dismissImport() {
        (_import.value as? ImageImportState.NeedsConfirmation)?.image?.bitmap?.recycle()
        _import.value = null
    }

    /**
     * v40 hazır karakter çeşidi ([path]: uygulamadaki WebP): resim elemanı olarak eklenir; boyu sayfanın kısa kenarının
     * [heightFraction] katı (sayfaya sığacak kadar). Resim G/Ç iş parçacığında okunur ve çözülür.
     */
    fun insertAssetImage(path: String, heightFraction: Float, center: Offset) {
        host.scope.launch {
            val image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val bytes = com.karapp.drawguidance.data.comicassets.ComicAssets.bytes(path)
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                    PreparedImage(bytes, "image/webp", bmp.width, bmp.height, bmp, bytes.size, 0f)
                }.onFailure { com.karapp.drawguidance.diagnostics.AppLog.e("ComicAssets", "Karakter resmi okunamadı: $path", it, userVisible = true) }
                    .getOrNull()
            } ?: return@launch
            addImage(image, center, heightFraction, announce = false)
        }
    }

    /**
     * v41 hazır mekân arka planı ([path] çizgi roman varlığı): resim [target] dikdörtgeninin oranına göre ortadan
     * kırpılır ([Backdrop.cropRect]), tam o dikdörtgene ve bütün elemanların arkasına konur (panel çizgileri üstte
     * kalır). Tek geri alma adımı; eklenen resmin kimliğiyle [onDone] çağrılır (okunamazsa null).
     */
    fun insertBackdrop(path: String, target: androidx.compose.ui.geometry.Rect, clip: List<StrokePoint>? = null, onDone: (String?) -> Unit = {}) {
        if (target.width <= 1f || target.height <= 1f) { onDone(null); return }
        host.scope.launch {
            val image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val bytes = com.karapp.drawguidance.data.comicassets.ComicAssets.bytes(path)
                    val src = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                    val r = Backdrop.cropRect(src.width, src.height, target.width / target.height)
                    if (r.width == src.width && r.height == src.height) {
                        PreparedImage(bytes, "image/webp", src.width, src.height, src, bytes.size, 0f)
                    } else {
                        val bmp = android.graphics.Bitmap.createBitmap(src, r.left, r.top, r.width, r.height)
                        val out = java.io.ByteArrayOutputStream()
                        @Suppress("DEPRECATION")
                        val format = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                        else android.graphics.Bitmap.CompressFormat.WEBP
                        bmp.compress(format, 88, out)
                        val data = out.toByteArray()
                        PreparedImage(data, "image/webp", bmp.width, bmp.height, bmp, bytes.size, 0f)
                    }
                }.onFailure { com.karapp.drawguidance.diagnostics.AppLog.e("ComicAssets", "Mekân arka planı okunamadı: $path", it, userVisible = true) }
                    .getOrNull()
            }
            if (image == null) { onDone(null); return@launch }
            val assetId = try {
                assetStore.save(store.canvasLocalId, image)
            } catch (e: Exception) {
                com.karapp.drawguidance.diagnostics.AppLog.e("ImageImport", "Resim verisi yazılamadı", e, userVisible = true)
                _import.value = ImageImportState.Rejected(listOf(ImageIssue.Unreadable), emptyList())
                onDone(null)
                return@launch
            }
            val latest = store.canvas.value ?: return@launch
            val q = ContentCodec::quantize
            val stroke = Stroke(
                id = UUID.randomUUID().toString(),
                points = listOf(
                    StrokePoint(q(target.left), q(target.top)),
                    StrokePoint(q(target.right), q(target.top)),
                    StrokePoint(q(target.right), q(target.bottom)),
                    StrokePoint(q(target.left), q(target.bottom)),
                ),
                colorHex = "#000000",
                brushType = BrushType.IMAGE,
                sizePx = 1f,
                opacity = 1f,
                order = 0,
                assetId = assetId,
                // v49 panel dörtgeninin dış hattına kırp (eğik panelde de panelin dışına taşmaz)
                clip = clip?.takeIf { it.size == 4 },
            )
            val merged = listOf(stroke) + latest.strokes
            store.commit(merged.mapIndexed { i, st -> if (st.order == i) st else st.copy(order = i) })
            onDone(stroke.id)
        }
    }

    /**
     * v46 eğitim çizimleri için hazır resim elemanı (eklenmez): [path] çizgi roman varlığı. [fill] ise resim [target]
     * oranına kırpılıp dikdörtgeni doldurur (mekân), değilse oranı korunarak dikdörtgene sığar ve alt kenara basar
     * (karakter). Resim verisi tuvale kaydedilir (eğitim bitince kullanılmayan veri bir sonraki açılışta temizlenir).
     */
    suspend fun assetImageStroke(path: String, target: androidx.compose.ui.geometry.Rect, fill: Boolean): Stroke? {
        if (target.width <= 1f || target.height <= 1f) return null
        val image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val bytes = com.karapp.drawguidance.data.comicassets.ComicAssets.bytes(path)
                val src = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                val r = if (fill) Backdrop.cropRect(src.width, src.height, target.width / target.height) else Backdrop.Crop(0, 0, src.width, src.height)
                if (r.width == src.width && r.height == src.height) PreparedImage(bytes, "image/webp", src.width, src.height, src, bytes.size, 0f)
                else {
                    val bmp = android.graphics.Bitmap.createBitmap(src, r.left, r.top, r.width, r.height)
                    val out = java.io.ByteArrayOutputStream()
                    @Suppress("DEPRECATION")
                    val format = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSY else android.graphics.Bitmap.CompressFormat.WEBP
                    bmp.compress(format, 85, out)
                    PreparedImage(out.toByteArray(), "image/webp", bmp.width, bmp.height, bmp, bytes.size, 0f)
                }
            }.onFailure { com.karapp.drawguidance.diagnostics.AppLog.w("ComicAssets", "Eğitim resmi okunamadı: $path", it) }.getOrNull()
        } ?: return null
        val assetId = runCatching { assetStore.save(store.canvasLocalId, image) }.getOrNull() ?: return null
        val rect = if (fill) target else {
            val k = minOf(target.width / image.width, target.height / image.height)
            val w = image.width * k; val h = image.height * k
            androidx.compose.ui.geometry.Rect(target.center.x - w / 2f, target.bottom - h, target.center.x + w / 2f, target.bottom)
        }
        val q = ContentCodec::quantize
        return Stroke(
            id = UUID.randomUUID().toString(),
            points = listOf(
                StrokePoint(q(rect.left), q(rect.top)), StrokePoint(q(rect.right), q(rect.top)),
                StrokePoint(q(rect.right), q(rect.bottom)), StrokePoint(q(rect.left), q(rect.bottom)),
            ),
            colorHex = "#000000", brushType = BrushType.IMAGE, sizePx = 1f, opacity = 1f, order = 0, assetId = assetId,
        )
    }

    private suspend fun addImage(image: PreparedImage, center: Offset, heightFraction: Float? = null, announce: Boolean = true) {
        val current = store.canvas.value ?: return
        // Resim verisi yazılamazsa (disk dolu vb.) uygulama çökmez; kullanıcıya "okunamadı" gösterilir
        val assetId = try {
            assetStore.save(store.canvasLocalId, image)
        } catch (e: Exception) {
            com.karapp.drawguidance.diagnostics.AppLog.e("ImageImport", "Resim verisi yazılamadı", e, userVisible = true)
            _import.value = ImageImportState.Rejected(listOf(ImageIssue.Unreadable), emptyList())
            return
        }
        // Tuvalin en fazla %60'ını kaplayacak şekilde, oranı korunarak ortaya yerleştirilir
        // v40 hazır karakter: boyu sayfanın kısa kenarına oranla (en çok sayfanın %90'ı)
        val fit = if (heightFraction != null) {
            minOf(minOf(current.width, current.height) * heightFraction / image.height, current.width * 0.9f / image.width, current.height * 0.9f / image.height)
        } else minOf(1f, current.width * 0.6f / image.width, current.height * 0.6f / image.height)
        val halfW = image.width * fit / 2f
        val halfH = image.height * fit / 2f
        val q = ContentCodec::quantize
        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            points = listOf(
                StrokePoint(q(center.x - halfW), q(center.y - halfH)),
                StrokePoint(q(center.x + halfW), q(center.y - halfH)),
                StrokePoint(q(center.x + halfW), q(center.y + halfH)),
                StrokePoint(q(center.x - halfW), q(center.y + halfH)),
            ),
            colorHex = "#000000",
            brushType = BrushType.IMAGE,
            sizePx = 1f,
            opacity = 1f,
            order = current.strokes.size,
            assetId = assetId,
        )
        val latest = store.canvas.value ?: return
        store.commit(latest.strokes + stroke.copy(order = latest.strokes.size))
        if (announce) host.emit(EditEvent.ImageAdded(image.bytes.size, image.originalBytes))
        // v17: eklenen resim de diğer eklenenler gibi seçili olarak Taşı'ya geçer (Bitti → Seç)
        host.inserted(setOf(stroke.id))
    }
}

private const val MSG_FILTER_FAIL = "Filtre uygulanamadı"
private const val MSG_EFFECT_FAIL = "Efekt uygulanamadı"
private const val MSG_BG_FAIL = "Arka plan kaldırılamadı"

/** v41 mekân arka planının kırpılması (saf; birim testli). */
object Backdrop {
    /** [srcW]×[srcH] resimden [aspect] (genişlik/yükseklik) oranlı, ortalanmış en büyük dikdörtgen. */
    data class Crop(val left: Int, val top: Int, val width: Int, val height: Int)

    fun cropRect(srcW: Int, srcH: Int, aspect: Float): Crop {
        if (srcW <= 0 || srcH <= 0 || !(aspect > 0f) || aspect.isInfinite()) return Crop(0, 0, srcW.coerceAtLeast(0), srcH.coerceAtLeast(0))
        val srcAspect = srcW.toFloat() / srcH
        return if (srcAspect > aspect) {
            val w = Math.round(srcH * aspect).coerceIn(1, srcW)
            Crop((srcW - w) / 2, 0, w, srcH)
        } else {
            val h = Math.round(srcW / aspect).coerceIn(1, srcH)
            Crop(0, (srcH - h) / 2, srcW, h)
        }
    }

    /** Çizimin noktalarının sınırı (panel elemanı için). */
    fun bounds(s: Stroke): androidx.compose.ui.geometry.Rect {
        if (s.points.isEmpty()) return androidx.compose.ui.geometry.Rect.Zero
        return androidx.compose.ui.geometry.Rect(s.points.minOf { it.x }, s.points.minOf { it.y }, s.points.maxOf { it.x }, s.points.maxOf { it.y })
    }

    /**
     * v49 Panelin dört köşesi (dörtgen panel: düz ya da eğik) kırpma dörtgeni olarak; yalnız dört ayrı köşesi olan
     * panellerde döner (yuvarlak/pahlı panellerde null, bu durumda arka plan panelin sınır dörtgenine sığar).
     */
    fun panelClip(s: Stroke): List<StrokePoint>? {
        val pts = ArrayList<StrokePoint>(s.points.size)
        for (p in s.points) {
            val last = pts.lastOrNull()
            if (last == null || kotlin.math.abs(last.x - p.x) > 0.5f || kotlin.math.abs(last.y - p.y) > 0.5f) pts += p
        }
        // Kapanış noktası ilkine eşitse at
        if (pts.size >= 2) { val a = pts.first(); val b = pts.last(); if (kotlin.math.abs(a.x - b.x) <= 0.5f && kotlin.math.abs(a.y - b.y) <= 0.5f) pts.removeAt(pts.size - 1) }
        return if (pts.size == 4) pts else null
    }
}
