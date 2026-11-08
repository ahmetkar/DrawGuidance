package com.karapp.drawguidance.data.images

import com.karapp.drawguidance.data.model.CanvasUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ImagesRepository(
    private val dao: SavedImageDao,
    private val exporter: ImageExporter,
    private val assetStore: CanvasAssetStore,
    private val folders: com.karapp.drawguidance.data.local.FolderDao? = null,
) {
    /** Resimler; dosya boyutu bilinmeyen eski kayıtların boyutu bir kez hesaplanıp saklanır. */
    fun observeImages(): Flow<List<SavedImageEntity>> = dao.observeAll().map { list ->
        list.map { image ->
            if (image.sizeBytes > 0) image
            else withContext(Dispatchers.IO) {
                val size = exporter.sizeOf(image.uri)
                if (size > 0) dao.updateSize(image.id, size)
                image.copy(sizeBytes = size)
            }
        }
    }

    /** Kaydetme penceresinin başlangıç ayarları (Ayarlar'daki varsayılan çözünürlük ve biçim). */
    fun defaultOptions(canvas: CanvasUi): ExportOptions = exporter.defaultOptions(canvas)

    /** Seçilen çözünürlük katında kaydedilecek resmin piksel boyutu. */
    fun outputSize(canvas: CanvasUi, scale: Float): Pair<Int, Int> = exporter.outputSize(canvas.width, canvas.height, scale)

    /** Resimlerin kaydedileceği yer (kaydetme penceresinde gösterilir). */
    val locationLabel: String get() = exporter.locationLabel

    /** Varsayılan ayarlarla açık sayfayı kaydeder. */
    suspend fun saveCanvasAsImage(canvas: CanvasUi): SavedImageEntity = saveCanvasAsImage(canvas, defaultOptions(canvas)).first()

    /** Seçilen ayarlarla kaydeder (tüm sayfalar seçildiyse sayfa başına bir resim). */
    suspend fun saveCanvasAsImage(canvas: CanvasUi, options: ExportOptions): List<SavedImageEntity> = withContext(Dispatchers.Default) {
        assetStore.ensureLoaded(canvas)
        // Not tuvalinin tüm sayfaları tuvale ait tek klasörde toplanır (sonraki kayıtlar da aynı klasöre; kayıt yerinde
        // de aynı adlı klasör)
        val folder = if (options.allPages && canvas.pageCount > 1) folderForCanvas(canvas) else null
        val exported = exporter.export(canvas, options, folder?.name)
        val multi = exported.size > 1
        val folderId = if (multi) folder?.id else null
        exported.mapIndexed { i, image ->
            val entity = SavedImageEntity(
                canvasLocalId = canvas.localId,
                title = if (multi) "${canvas.name} (${i + 1})" else canvas.name,
                uri = image.uri,
                width = image.width,
                height = image.height,
                createdAt = System.currentTimeMillis(),
                location = image.location,
                sizeBytes = image.sizeBytes,
                folderId = folderId,
                mime = options.mime,
                defaultLocation = SavedImageEntity.isDefaultUri(image.uri),
            )
            entity.copy(id = dao.insert(entity))
        }
    }

    /** v25 3B şeklin resmini Resimlerim'e kaydeder ([source]: "model3d:<kimlik>"). */
    suspend fun saveBitmap(
        bitmap: android.graphics.Bitmap, title: String, source: String,
        format: com.karapp.drawguidance.data.settings.ImageFormat, quality: Int = 92,
        /** v40 kaydetme yeri ("Konum seç"). */
        target: SaveTarget = SaveTarget.Default,
    ): SavedImageEntity = withContext(Dispatchers.IO) {
        val image = exporter.exportBitmap(bitmap, title, format, quality, target)
        val entity = SavedImageEntity(
            canvasLocalId = source, title = title, uri = image.uri, width = image.width, height = image.height,
            createdAt = System.currentTimeMillis(), location = image.location, sizeBytes = image.sizeBytes,
            mime = format.mime, defaultLocation = SavedImageEntity.isDefaultUri(image.uri),
        )
        entity.copy(id = dao.insert(entity))
    }

    private suspend fun folderForCanvas(canvas: CanvasUi): com.karapp.drawguidance.data.local.FolderEntity? {
        val dao = folders ?: return null
        val kind = com.karapp.drawguidance.data.local.FolderKind.IMAGE.name
        return dao.findForCanvas(kind, canvas.localId) ?: com.karapp.drawguidance.data.local.FolderEntity(
            java.util.UUID.randomUUID().toString(), canvas.name.ifBlank { "Tuval" }, kind, System.currentTimeMillis(), canvas.localId,
        ).also { dao.upsert(it) }
    }

    /** Seçilen sayfaları [options] ile PDF olarak dışa aktarır. */
    suspend fun exportPdf(canvas: CanvasUi, options: PdfOptions = defaultPdfOptions(canvas)): ExportedFile = withContext(Dispatchers.Default) {
        assetStore.ensureLoaded(canvas, options.pageIndices(canvas.pageCount, canvas.currentPage).map { canvas.pages.getOrElse(it) { emptyList() } })
        exporter.exportPdf(canvas, options).also { record(canvas, it, "application/pdf") }
    }

    /** v42 dışa aktarılan belge (PDF, CBZ, CMYK) Kaydedilenler'e eklenir (kaydedilemezse dışa aktarma yine geçerli). */
    private suspend fun record(canvas: CanvasUi, file: ExportedFile, mime: String) {
        runCatching {
            dao.insert(
                SavedImageEntity(
                    canvasLocalId = canvas.localId, title = canvas.name, uri = file.uri, width = canvas.width, height = canvas.height,
                    createdAt = System.currentTimeMillis(), location = file.location, sizeBytes = file.sizeBytes,
                    mime = mime, defaultLocation = SavedImageEntity.isDefaultUri(file.uri),
                ),
            )
        }.onFailure { com.karapp.drawguidance.diagnostics.AppLog.e("Images", "Belge kaydı eklenemedi", it) }
    }

    /** PDF penceresinin varsayılanları: tuval adı + tarih, tüm sayfalar, tuval oranı, keskin; kılavuz tuvaldeki gibi. */
    fun defaultPdfOptions(canvas: CanvasUi): PdfOptions = PdfOptions(
        fileName = ImageExporter.defaultFileName(canvas.name),
        includeGuides = com.karapp.drawguidance.ui.drawing.GuideRenderer.exports(canvas.guide),
    )

    /** PDF'in kaydedileceği yer. */
    val pdfLocationLabel: String get() = exporter.pdfLocationLabel

    /**
     * v40 baskıya hazır belge (CMYK TIFF ya da PDF; Resimlerim'e eklenmez, Android önizleyemez): PDF'te seçilen
     * sayfalar tek dosyada, TIFF'te sayfa başına bir dosya. Gösterilecek kayıt (ilk dosya, toplam boyut).
     */
    suspend fun exportCmykDocument(canvas: CanvasUi, options: ExportOptions): ExportedFile = withContext(Dispatchers.Default) {
        val pages = if (options.allPages && canvas.pageCount > 1) canvas.pages else listOf(canvas.strokes)
        assetStore.ensureLoaded(canvas, pages)
        val file = if (options.cmykFormat == CmykFormat.PDF) exporter.exportCmykPdf(canvas, options)
        else exporter.export(canvas, options).let { list ->
            ExportedFile(list.first().uri, list.first().location, list.sumOf { it.sizeBytes })
        }
        file.also { record(canvas, it, options.cmykFormat.mime) }
    }

    /** v40 çizgi roman arşivi (.cbz). */
    suspend fun exportCbz(canvas: CanvasUi, options: CbzOptions): ExportedFile = withContext(Dispatchers.Default) {
        assetStore.ensureLoaded(canvas, options.pageIndices(canvas.pageCount, canvas.currentPage).map { canvas.pages.getOrElse(it) { emptyList() } })
        exporter.exportCbz(canvas, options).also { record(canvas, it, SavedImageEntity.CBZ_MIME) }
    }

    /** v40 CMYK önizlemesi için tuvalin küçük resmi ([maxSide] kenar). */
    suspend fun previewBitmap(canvas: CanvasUi, maxSide: Int): android.graphics.Bitmap = withContext(Dispatchers.Default) {
        assetStore.ensureLoaded(canvas)
        val scale = maxSide.toFloat() / maxOf(canvas.width, canvas.height).coerceAtLeast(1)
        exporter.render(canvas, scale.coerceAtMost(1f))
    }

    /** Klasörlerdeki tüm resimleri (dosyalarıyla) siler. */
    suspend fun deleteInFolders(folderIds: List<String>) {
        dao.inFolders(folderIds).forEach { delete(it.id) }
    }

    /**
     * v17: resimleri klasöre taşır — kayıt yerinde de (izinli klasör / Galeri albümü / uygulama klasörü) o klasöre
     * taşınır; [folderId] null ise albümün köküne. Kayıt yerinde taşınamayan resim yalnız listede taşınır.
     */
    suspend fun moveToFolder(ids: List<Long>, folderId: String?) {
        if (ids.isEmpty()) return
        val name = folderId?.let { folders?.get(it)?.name }
        withContext(Dispatchers.IO) {
            ids.forEach { id ->
                val e = dao.getById(id) ?: return@forEach
                if (e.folderId == folderId) return@forEach
                exporter.moveImage(e.uri, e.location, name)?.let { dao.updateLocation(id, it.uri, it.location) }
            }
        }
        dao.moveTo(ids, folderId)
    }

    /** v17: klasör kayıt yerinde de oluşturulur. */
    suspend fun createFolderOnDisk(name: String) = withContext(Dispatchers.IO) { exporter.createFolder(name) }

    /** v17: klasör yeniden adlandırıldı — resimleri kayıt yerinde yeni adlı klasöre taşınır, eski klasör kaldırılır. */
    suspend fun renameFolderOnDisk(folderId: String, oldName: String, newName: String) = withContext(Dispatchers.IO) {
        if (ImageExporter.safeName(oldName) == ImageExporter.safeName(newName)) return@withContext
        exporter.createFolder(newName)
        dao.inFolders(listOf(folderId)).forEach { e ->
            exporter.moveImage(e.uri, e.location, newName)?.let { dao.updateLocation(e.id, it.uri, it.location) }
        }
        exporter.deleteFolder(oldName)
    }

    /** v17: silinen klasör kayıt yerinden de kaldırılır (boşsa). */
    suspend fun deleteFolderOnDisk(name: String) = withContext(Dispatchers.IO) { exporter.deleteFolder(name) }

    suspend fun delete(id: Long) {
        val entity = dao.getById(id) ?: return
        withContext(Dispatchers.IO) { exporter.delete(entity.uri) }
        dao.deleteById(id)
    }
}
