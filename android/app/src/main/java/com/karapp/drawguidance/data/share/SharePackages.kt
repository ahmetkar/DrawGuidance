package com.karapp.drawguidance.data.share

import com.karapp.drawguidance.data.local.CanvasAssetDao
import com.karapp.drawguidance.data.local.CanvasAssetEntity
import com.karapp.drawguidance.data.local.CanvasDao
import com.karapp.drawguidance.data.local.CanvasEntity
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.local.FolderKind
import com.karapp.drawguidance.data.local.FolderRepository
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.encodeBackground
import com.karapp.drawguidance.data.repository.encodeGuide
import com.karapp.drawguidance.data.repository.withPageSettings
import java.util.UUID

/** Kurulmaya hazır paket: başlık, içerik ve resimler (başlıktaki sırayla). */
class SharePackage(val header: SharePackageHeader, val content: ByteArray, val assets: List<ByteArray>) {
    fun encode(messageType: Int): ByteArray =
        ShareWire.encode(messageType, ShareWire.text(header), listOf(content) + assets)

    companion object {
        fun decode(m: ShareWire.Message): SharePackage {
            val h = ShareWire.header<SharePackageHeader>(m)
            val parts = m.parts(listOf(h.contentLen) + h.assets.map { it.len })
            return SharePackage(h, parts.first(), parts.drop(1))
        }
    }
}

/**
 * v51 tuval paketlerini hazırlar ve alınanları veritabanına yazar. Gönderilen tuval veritabanındaki kaydın kendisidir
 * (içerik baytları yeniden kodlanmaz); alınan tuval yeni kimlikle, resimleri yeni kimliklerle kaydedilir.
 */
class SharePackages(
    private val canvasDao: CanvasDao,
    private val assetDao: CanvasAssetDao,
    private val folders: FolderRepository,
    private val settings: ShareSettings,
    /** "Paylaşılanlar" klasörünün etkin dildeki adı. */
    private val folderName: () -> String = { "Paylaşılanlar" },
) {
    /** Kayıtlı tuvalin paketi (yoksa null). */
    suspend fun canvasPackage(canvasId: String, type: String, id: String, from: String): SharePackage? {
        val e = canvasDao.getByLocalId(canvasId) ?: return null
        val assetIds = assetDao.idsForCanvas(canvasId)
        val assets = if (assetIds.isEmpty()) emptyList() else assetDao.getByIds(assetIds)
        val header = SharePackageHeader(
            id = id, type = type, name = e.name, canvasKind = e.kind, width = e.width, height = e.height,
            backgroundColorHex = e.backgroundColorHex, background = e.background, guide = e.guide,
            currentPage = e.currentPage, aiEnabled = e.aiEnabled, contentLen = e.content.size,
            assets = assets.map { ShareAsset(it.id, it.mimeType, it.width, it.height, it.data.size) }, from = from,
        )
        return SharePackage(header, e.content, assets.map { it.data })
    }

    /** Toplam boyut tahmini (teklifte gösterilir). */
    suspend fun canvasSize(canvasId: String): Long =
        (canvasDao.getByLocalId(canvasId)?.content?.size ?: 0).toLong() + assetDao.totalBytes(canvasId)

    /**
     * Canlı yayın güncellemesi: düzenleyicinin bellekteki tuvali; yalnız [sent] içinde olmayan resimler eklenir
     * (eklenenler [sent]'e yazılır).
     */
    suspend fun livePackage(canvas: CanvasUi, id: String, seq: Long, sent: MutableSet<String>, from: String): SharePackage {
        val content = ContentCodec.encodePages(canvas.pages, layers = canvas.layers)
        val ids = assetDao.idsForCanvas(canvas.localId).filterNot { it in sent }
        val assets = if (ids.isEmpty()) emptyList() else assetDao.getByIds(ids)
        sent += assets.map { it.id }
        val background = withPageSettings(encodeBackground(canvas.background, canvas.pageBackgrounds), canvas.pageSettings)
        val header = SharePackageHeader(
            id = id, type = ShareWire.TYPE_LIVE, name = canvas.name, canvasKind = canvas.kind.name,
            width = canvas.width, height = canvas.height, backgroundColorHex = canvas.backgroundColorHex,
            background = background, guide = encodeGuide(canvas.guide), currentPage = canvas.currentPage,
            aiEnabled = canvas.aiEnabled, contentLen = content.size,
            assets = assets.map { ShareAsset(it.id, it.mimeType, it.width, it.height, it.data.size) }, seq = seq, from = from,
        )
        return SharePackage(header, content, assets.map { it.data })
    }

    /**
     * Alınan tuvali kaydeder ya da ([existingId] verilirse) canlı yayında günceller. [ids]: gönderenin resim kimliği →
     * bu cihazdaki kimlik (yayın boyunca korunur). Tuvalin kimliğini döner. Bozuk içerik kaydedilmez (istisna).
     */
    suspend fun install(pkg: SharePackage, ids: MutableMap<String, String>, existingId: String? = null): String {
        val h = pkg.header
        val localId = existingId ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        pkg.header.assets.forEachIndexed { i, a ->
            val newId = ids.getOrPut(a.id) { UUID.randomUUID().toString() }
            assetDao.insert(CanvasAssetEntity(newId, localId, a.mime, a.w, a.h, pkg.assets[i], now))
        }
        // İçerik çözülebilmeli (bozuk paket kaydedilmez); resim kimlikleri bu cihazdakilerle değişir
        val doc = ContentCodec.decodeDocument(pkg.content)
        val content = if (ids.isEmpty()) pkg.content
        else ContentCodec.encodePages(doc.pages.map { page -> page.map { remap(it, ids) } }, layers = doc.layers)
        var background = h.background
        var guide = h.guide
        for ((old, new) in ids) {
            background = background.replace(old, new)
            guide = guide.replace(old, new)
        }
        val kind = CanvasKind.parse(h.canvasKind)
        val pages = doc.pages.size.coerceAtLeast(1)
        val existing = existingId?.let { canvasDao.getByLocalId(it) }
        val entity = CanvasEntity(
            localId = localId,
            name = h.name.ifBlank { "Tuval" }.take(120),
            width = h.width.coerceIn(16, 20000),
            height = h.height.coerceIn(16, 20000),
            backgroundColorHex = h.backgroundColorHex.takeIf { HEX.matches(it) } ?: "#FFFFFF",
            aiEnabled = h.aiEnabled,
            content = content,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            kind = kind.name,
            currentPage = h.currentPage.coerceIn(0, pages - 1),
            guide = guide,
            folderId = existing?.folderId ?: receivedFolder(),
            background = background,
        )
        canvasDao.upsert(entity)
        return localId
    }

    private suspend fun receivedFolder(): String? {
        if (!settings.receivedFolder.value) return null
        settings.sharedFolderId?.let { id -> if (folders.get(id) != null) return id }
        return folders.create(folderName(), FolderKind.CANVAS).id.also { settings.sharedFolderId = it }
    }

    private fun remap(s: Stroke, ids: Map<String, String>): Stroke {
        var c = s
        s.assetId?.let { a -> ids[a]?.let { c = c.copy(assetId = it) } }
        s.model?.let { link -> ids[link.dataAssetId]?.let { c = c.copy(model = link.copy(dataAssetId = it)) } }
        return c
    }

    private companion object {
        val HEX = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
    }
}
