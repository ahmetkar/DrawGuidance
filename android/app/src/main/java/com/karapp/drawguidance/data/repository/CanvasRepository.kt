package com.karapp.drawguidance.data.repository

import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.local.CanvasDao
import com.karapp.drawguidance.data.local.CanvasEntity
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Stroke
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Tuval verisinin tek doğruluk kaynağı: cihazdaki Room veritabanı (çizimler buluta gönderilmez). */
class CanvasRepository(
    private val dao: CanvasDao,
    private val assetStore: CanvasAssetStore,
    /** Verilirse bir kaydın parçaları (içerik, boyut, arka plan, sayfa) tek transaction'da yazılır. */
    private val database: androidx.room.RoomDatabase? = null,
) {

    // Değişmeyen tuvallerin içeriği her veritabanı değişikliğinde yeniden çözülmesin
    private val decoded = java.util.concurrent.ConcurrentHashMap<String, Pair<Pair<Long, Int>, ContentCodec.Document>>()

    fun observeCanvases(): Flow<List<CanvasUi>> = dao.observeAll()
        .map { list ->
            val ids = list.mapTo(HashSet()) { it.localId }
            decoded.keys.retainAll(ids)
            list.map { entity ->
                val key = entity.updatedAt to entity.content.size
                val doc = decoded[entity.localId]?.takeIf { it.first == key }?.second
                    ?: ContentCodec.decodeDocument(entity.content).also { decoded[entity.localId] = key to it }
                entity.toUi(doc)
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)

    suspend fun getCanvas(localId: String): CanvasUi? = dao.getByLocalId(localId)?.toUi()

    suspend fun createCanvas(
        name: String,
        width: Int,
        height: Int,
        backgroundColorHex: String,
        aiEnabled: Boolean,
        kind: com.karapp.drawguidance.data.model.CanvasKind = com.karapp.drawguidance.data.model.CanvasKind.ART,
        pageCount: Int = 1,
        folderId: String? = null,
        background: com.karapp.drawguidance.data.model.BackgroundSpec = com.karapp.drawguidance.data.model.BackgroundSpec(),
        /**
         * v45 çizgi roman kapağı: verilirse ilk sayfa bu elemanlarla kapak olur, ardından [pageCount] boş sayfa gelir
         * ve tuval kapaktan sonraki ilk sayfada açılır. null: kapaksız (eski davranış).
         */
        cover: List<Stroke>? = null,
    ): CanvasUi {
        val now = System.currentTimeMillis()
        // v33 başlangıç sayfa sayısı tuval türünün sınırını aşmaz (v45 kapak da sayfa sayılır)
        val pages = pageCount.coerceIn(1, kind.maxPages - if (cover != null) 1 else 0)
        val content = when {
            cover != null -> ContentCodec.encodePages(listOf(cover.mapIndexed { i, s -> s.copy(order = i) }) + List(pages) { emptyList() })
            pages <= 1 -> ContentCodec.EMPTY
            else -> ContentCodec.encodePages(List(pages) { emptyList() })
        }
        val entity = CanvasEntity(
            localId = UUID.randomUUID().toString(),
            name = name,
            width = width,
            height = height,
            backgroundColorHex = backgroundColorHex,
            aiEnabled = aiEnabled,
            content = content,
            createdAt = now,
            updatedAt = now,
            kind = kind.name,
            folderId = folderId,
            background = if (cover != null) encodeBackground(background, listOf(com.karapp.drawguidance.data.model.PageBackground(cover = true)))
            else encodeBackground(background),
            currentPage = if (cover != null) 1 else 0,
        )
        dao.upsert(entity)
        return entity.toUi()
    }

    /**
     * v46 hazır şablondan tuval: şablonun boyutu, kâğıdı, arka planı, sayfaları ve resimleri (yeni kimliklerle) yeni
     * tuvale yazılır; tuval şablonun açılan sayfasında açılır.
     */
    suspend fun createFromTemplate(
        name: String,
        kind: com.karapp.drawguidance.data.model.CanvasKind,
        aiEnabled: Boolean,
        template: com.karapp.drawguidance.data.templates.PreparedTemplate,
        folderId: String? = null,
    ): CanvasUi {
        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()
        for ((id, data) in template.assets) {
            val (bytes, a) = data
            assetStore.saveData(localId, bytes, a.mime, id, a.w, a.h)
        }
        val m = template.meta
        val entity = CanvasEntity(
            localId = localId,
            name = name,
            width = m.width,
            height = m.height,
            backgroundColorHex = m.backgroundColorHex,
            aiEnabled = aiEnabled,
            content = template.content,
            createdAt = now,
            updatedAt = now,
            kind = kind.name,
            folderId = folderId,
            background = template.background,
            guide = template.guide,
            currentPage = m.currentPage,
        )
        dao.upsert(entity)
        return entity.toUi()
    }

    suspend fun updateSettings(
        localId: String,
        name: String,
        width: Int,
        height: Int,
        backgroundColorHex: String,
        aiEnabled: Boolean,
        background: com.karapp.drawguidance.data.model.BackgroundSpec? = null,
    ) {
        val now = System.currentTimeMillis()
        val write: suspend () -> Unit = {
            dao.updateSettings(
                localId = localId,
                name = name,
                width = width,
                height = height,
                backgroundColorHex = backgroundColorHex,
                aiEnabled = aiEnabled,
                updatedAt = now,
            )
            background?.let { spec ->
                // v24 sayfaların kendi arka planları korunur (ayarlar yalnız ortak arka planı değiştirir)
                val row = dao.getByLocalId(localId)
                val pages = row?.let { decodePageBackgrounds(it.background, Int.MAX_VALUE) } ?: emptyList()
                // v40 sayfa ayarları (numara, üst/alt bilgi) korunur
                dao.updateBackgroundSpec(localId, withPageSettings(encodeBackground(spec, pages), row?.let { decodePageSettings(it.background) }), now)
            }
        }
        val db = database
        if (db != null) db.withTransaction { write() } else write()
    }

    /** v30 tuvalin yapay zekâ ayarı (düzenleyiciden; tuvalin güncellenme zamanı değişmez). */
    suspend fun setAiEnabled(localId: String, enabled: Boolean) = dao.updateAiEnabled(localId, enabled)

    /** v30 tuvalin yapay zekâ ayarı (tuval yoksa null). */
    suspend fun isAiEnabled(localId: String): Boolean? = dao.aiEnabled(localId)

    // Tuval başına kodlama önbelleği: her kayıtta yalnızca yeni çizimler kodlanır
    private val encodeCaches = java.util.concurrent.ConcurrentHashMap<String, ContentCodec.EncodeCache>()

    private fun encodeCached(
        localId: String,
        pages: List<List<Stroke>>,
        layers: List<com.karapp.drawguidance.data.model.Layer> = listOf(com.karapp.drawguidance.data.model.Layer.Default),
    ): ByteArray {
        val cache = encodeCaches.getOrPut(localId) { ContentCodec.EncodeCache() }
        return synchronized(cache) { ContentCodec.encodePages(pages, cache, layers) }
    }

    suspend fun saveStrokes(
        localId: String,
        pages: List<List<Stroke>>,
        layers: List<com.karapp.drawguidance.data.model.Layer> = listOf(com.karapp.drawguidance.data.model.Layer.Default),
    ) {
        dao.updateContent(localId, encodeCached(localId, pages, layers), System.currentTimeMillis())
    }

    suspend fun saveSizeAndStrokes(localId: String, width: Int, height: Int, pages: List<List<Stroke>>) {
        dao.updateSizeAndContent(localId, width, height, encodeCached(localId, pages), System.currentTimeMillis())
    }

    /** Tuvalleri bir klasöre taşır (null: köke). */
    suspend fun moveToFolder(ids: List<String>, folderId: String?) {
        if (ids.isNotEmpty()) dao.moveTo(ids, folderId)
    }

    suspend fun idsInFolders(folderIds: List<String>): List<String> = if (folderIds.isEmpty()) emptyList() else dao.idsInFolders(folderIds)

    suspend fun saveCurrentPage(localId: String, page: Int) = dao.updateCurrentPage(localId, page)

    /**
     * Çizim ekranının kaydı: içerik (ve boyut değiştiyse boyut), arka plan ve açık sayfa tek transaction'da yazılır;
     * uygulama yarıda kapansa da tuval yarım kalmaz.
     */
    suspend fun saveDocument(
        localId: String,
        pages: List<List<Stroke>>,
        width: Int,
        height: Int,
        sizeChanged: Boolean,
        backgroundColorHex: String?,
        currentPage: Int?,
        layers: List<com.karapp.drawguidance.data.model.Layer> = listOf(com.karapp.drawguidance.data.model.Layer.Default),
    ) {
        val content = encodeCached(localId, pages, layers)
        val now = System.currentTimeMillis()
        val write: suspend () -> Unit = {
            if (sizeChanged) dao.updateSizeAndContent(localId, width, height, content, now) else dao.updateContent(localId, content, now)
            backgroundColorHex?.let { dao.updateBackground(localId, it, now) }
            currentPage?.let { dao.updateCurrentPage(localId, it) }
        }
        val db = database
        if (db != null) db.withTransaction { write() } else write()
    }

    suspend fun saveGuide(localId: String, guide: com.karapp.drawguidance.data.model.GuideSpec) {
        dao.updateGuide(localId, encodeGuide(guide), System.currentTimeMillis())
    }

    suspend fun saveBackground(localId: String, backgroundColorHex: String) {
        dao.updateBackground(localId, backgroundColorHex, System.currentTimeMillis())
    }

    /** v23 arka plan resmi/deseni; v24 sayfaların kendi arka planlarıyla. */
    suspend fun saveBackgroundSpec(
        localId: String,
        spec: com.karapp.drawguidance.data.model.BackgroundSpec,
        pages: List<com.karapp.drawguidance.data.model.PageBackground?> = emptyList(),
    ) {
        // v40 sayfa ayarları (numara, üst/alt bilgi) korunur
        val settings = dao.getByLocalId(localId)?.let { decodePageSettings(it.background) }
        dao.updateBackgroundSpec(localId, withPageSettings(encodeBackground(spec, pages), settings), System.currentTimeMillis())
    }

    /** v40 sayfa numarası, üst bilgi ve alt bilgi (arka plan JSON'unda; arka plan olduğu gibi kalır). */
    suspend fun savePageSettings(localId: String, settings: com.karapp.drawguidance.data.model.PageSettings) {
        val row = dao.getByLocalId(localId) ?: return
        dao.updateBackgroundSpec(localId, withPageSettings(row.background, settings), System.currentTimeMillis())
    }

    suspend fun deleteCanvas(localId: String) {
        encodeCaches.remove(localId)
        dao.deleteByLocalId(localId)
        assetStore.deleteForCanvas(localId)
    }
}
