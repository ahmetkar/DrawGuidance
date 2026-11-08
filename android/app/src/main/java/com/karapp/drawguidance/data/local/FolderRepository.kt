package com.karapp.drawguidance.data.local

import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Tuval ve resim klasörleri: oluşturma, yeniden adlandırma, taşıma ve içeriğiyle birlikte silme. Not tuvalinin
 * tüm sayfaları resim olarak kaydedilirken tuvale ait tek bir resim klasörü kullanılır ([folderForCanvas]).
 */
class FolderRepository(
    private val dao: FolderDao,
    private val canvases: CanvasRepository,
    private val images: ImagesRepository,
) {
    fun observe(kind: FolderKind): Flow<List<FolderEntity>> = dao.observe(kind.name)

    suspend fun get(id: String): FolderEntity? = dao.get(id)

    /**
     * Klasör oluşturur. v17: resim klasörü kayıt yerinde (izinli klasör / Galeri albümü) gerçek klasör olarak da
     * oluşturulur; aynı adlı resim klasörü varsa ada sıra numarası eklenir (diskte iki klasör karışmasın).
     */
    suspend fun create(name: String, kind: FolderKind, sourceCanvasId: String = ""): FolderEntity {
        var title = name.trim().ifBlank { "Yeni klasör" }
        if (kind == FolderKind.IMAGE) {
            val taken = dao.all(kind.name).map { com.karapp.drawguidance.data.images.ImageExporter.safeName(it.name) }.toSet()
            val base = title
            var i = 2
            while (com.karapp.drawguidance.data.images.ImageExporter.safeName(title) in taken) title = "$base (${i++})"
        }
        val folder = FolderEntity(UUID.randomUUID().toString(), title, kind.name, System.currentTimeMillis(), sourceCanvasId)
        dao.upsert(folder)
        if (kind == FolderKind.IMAGE) images.createFolderOnDisk(title)
        return folder
    }

    suspend fun rename(id: String, name: String) {
        if (name.isBlank()) return
        val old = dao.get(id)
        dao.rename(id, name.trim())
        if (old != null && old.kind == FolderKind.IMAGE.name) images.renameFolderOnDisk(id, old.name, name.trim())
    }

    /** Tuvalin resimlerinin toplandığı klasör (yoksa tuvalin adıyla oluşturulur). */
    suspend fun folderForCanvas(canvasId: String, canvasName: String): FolderEntity =
        dao.findForCanvas(FolderKind.IMAGE.name, canvasId) ?: create(canvasName, FolderKind.IMAGE, canvasId)

    /** Tuval klasörlerini içindeki tuvallerle birlikte siler. */
    suspend fun deleteCanvasFolders(ids: List<String>) {
        if (ids.isEmpty()) return
        canvases.idsInFolders(ids).forEach { canvases.deleteCanvas(it) }
        dao.delete(ids)
    }

    /** Resim klasörlerini içindeki resimlerle (dosyalarıyla) birlikte siler. */
    suspend fun deleteImageFolders(ids: List<String>) {
        if (ids.isEmpty()) return
        val names = ids.mapNotNull { dao.get(it)?.name }
        images.deleteInFolders(ids)
        dao.delete(ids)
        names.forEach { images.deleteFolderOnDisk(it) }
    }
}
