package com.karapp.drawguidance.data.images

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.karapp.drawguidance.data.local.CanvasAssetDao
import com.karapp.drawguidance.data.local.CanvasAssetEntity
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Çözülmüş resimlerin bellek önbelleği (tüm çizim yüzeyleri buradan okur).
 * [version] her yeni resim yüklendiğinde artar; çizim yüzeyleri yeniden çizilir.
 */
object CanvasImageCache {
    // v18: cihaz belleğine göre (Android 8.0 öncesinde Java yığınının sekizde biri, düşük RAM'de 24 MB, en çok 64 MB)
    private val cache = object : LruCache<String, ImageBitmap>(com.karapp.drawguidance.platform.DeviceCaps.imageCacheBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    init {
        // Bellek azalınca önbellek boşaltılır (resimler gerektiğinde yeniden yüklenir)
        com.karapp.drawguidance.platform.DeviceCaps.onTrim { cache.evictAll(); _version.update { it + 1 } }
    }

    /**
     * v18: bellekte olmayan (bellek azaldığı için bırakılmış) resim istenince yeniden yüklenir. Aynı resim en çok
     * saniyede bir istenir: önbellek yetmezse sürekli yükleme döngüsüne girilmez.
     */
    @Volatile var loader: ((String) -> Unit)? = null
    /** v23 hazır arka plan resimleri ("paper:<kimlik>") ve 3B dokuları ("tex:<kimlik>") ayrı yerden yüklenir. */
    @Volatile var paperLoader: ((String) -> Unit)? = null
    @Volatile var textureLoader: ((String) -> Unit)? = null
    private val lastRequest = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun get(id: String?): ImageBitmap? {
        if (id == null) return null
        val hit = cache.get(id)
        if (hit == null) {
            val now = android.os.SystemClock.uptimeMillis()
            val last = lastRequest[id]
            if (last == null || now - last > 1000L) {
                lastRequest[id] = now
                when {
                    id.startsWith("paper:") -> paperLoader?.invoke(id)
                    id.startsWith("tex:") -> textureLoader?.invoke(id)
                    else -> loader?.invoke(id)
                }
            }
        }
        return hit
    }

    fun put(id: String, bitmap: ImageBitmap) {
        cache.put(id, bitmap)
        _version.update { it + 1 }
    }

    fun contains(id: String): Boolean = cache.get(id) != null
}

/** Resim verisinin kaydı, yüklenmesi ve artık kullanılmayanların temizlenmesi. */
class CanvasAssetStore(private val dao: CanvasAssetDao) {

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    init {
        // v18: önbellekten düşen resim çizilmek istenince veritabanından yeniden çözülür
        CanvasImageCache.loader = { id ->
            scope.launch {
                runCatching {
                    dao.getByIds(listOf(id)).firstOrNull()?.let { asset ->
                        BitmapFactory.decodeByteArray(asset.data, 0, asset.data.size)?.let { CanvasImageCache.put(asset.id, it.asImageBitmap()) }
                    }
                }
            }
        }
    }

    /** Her tuvalin resim verilerinin toplam boyutu (bayt). */
    suspend fun bytesByCanvas(): Map<String, Long> = runCatching { dao.bytesByCanvas().associate { it.canvasLocalId to it.bytes } }.getOrDefault(emptyMap())

    suspend fun save(canvasLocalId: String, image: PreparedImage): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            CanvasAssetEntity(
                id = id,
                canvasLocalId = canvasLocalId,
                mimeType = image.mimeType,
                width = image.width,
                height = image.height,
                data = image.bytes,
                createdAt = System.currentTimeMillis(),
            ),
        )
        CanvasImageCache.put(id, image.bitmap.asImageBitmap())
        return id
    }

    /**
     * v23 resim olmayan veri (3B modelin kendisi): tuvale bağlı saklanır, kullanılmayınca [prune] ile silinir.
     * Kimliği döner.
     */
    suspend fun saveData(
        canvasLocalId: String, bytes: ByteArray, mimeType: String, id: String = UUID.randomUUID().toString(),
        /** v46 resim verisinin boyutu (şablondan gelen resimler; bilinmiyorsa 0). */
        width: Int = 0, height: Int = 0,
    ): String {
        dao.insert(CanvasAssetEntity(id, canvasLocalId, mimeType, width, height, bytes, System.currentTimeMillis()))
        return id
    }

    /** v29 bir resmi/veriyi başka tuvale kopyalar (modlar arası aktarma); yeni kimliği döner (yoksa null). */
    suspend fun copyTo(id: String, canvasLocalId: String): String? {
        val asset = dao.getByIds(listOf(id)).firstOrNull() ?: return null
        val newId = UUID.randomUUID().toString()
        dao.insert(CanvasAssetEntity(newId, canvasLocalId, asset.mimeType, asset.width, asset.height, asset.data, System.currentTimeMillis()))
        CanvasImageCache.get(id)?.let { CanvasImageCache.put(newId, it) }
        return newId
    }

    /** Kayıtlı verinin baytları (yoksa null). */
    suspend fun bytes(id: String): ByteArray? = dao.getByIds(listOf(id)).firstOrNull()?.data

    /** Tuvalin tüm resimleri: çizimler, arka plan resmi ve yazı kutularının resimleri (hazır ve galeriden). */
    suspend fun ensureLoaded(canvas: com.karapp.drawguidance.data.model.CanvasUi, pages: List<List<Stroke>> = canvas.pages) {
        val strokes = pages.flatten()
        com.karapp.drawguidance.data.library.PaperLibrary.ensureLoaded(canvas.background, strokes) { ids -> ensureLoaded(emptyList(), ids) }
        // v24 sayfaların kendi arka plan resimleri
        canvas.pageBackgrounds.mapNotNull { it?.spec }.distinct().forEach { spec ->
            com.karapp.drawguidance.data.library.PaperLibrary.ensureLoaded(spec, emptyList()) { ids -> ensureLoaded(emptyList(), ids) }
        }
        ensureLoaded(strokes)
    }

    /** Çizimlerin kullandığı ama henüz bellekte olmayan resimleri çözer ([extra]: arka plan resmi gibi ek kimlikler). */
    suspend fun ensureLoaded(strokes: List<Stroke>, extra: Collection<String> = emptyList()) {
        val missing = (strokes.asSequence()
            .filter { it.brushType == BrushType.IMAGE }
            .mapNotNull { it.assetId } + extra.asSequence())
            .filterNot { CanvasImageCache.contains(it) }
            .distinct()
            .toList()
        if (missing.isEmpty()) return
        val assets = dao.getByIds(missing)
        withContext(Dispatchers.Default) {
            assets.forEach { asset ->
                BitmapFactory.decodeByteArray(asset.data, 0, asset.data.size)?.let {
                    CanvasImageCache.put(asset.id, it.asImageBitmap())
                }
            }
        }
    }

    /**
     * Tuvalin içeriğinde artık hiçbir çizimin kullanmadığı resimleri siler ([keep]: arka plan resmi gibi çizim dışında
     * kullanılanlar; 3B şekillerin model verisi de korunur).
     */
    suspend fun prune(canvasLocalId: String, strokes: List<Stroke>, keep: Collection<String> = emptyList()) {
        val used = HashSet<String>(keep)
        strokes.forEach { s ->
            s.assetId?.let(used::add)
            s.model?.dataAssetId?.let(used::add)
            s.text?.box?.imageId?.takeIf { it.startsWith(com.karapp.drawguidance.data.model.BackgroundSpec.ASSET) }
                ?.let { used += it.removePrefix(com.karapp.drawguidance.data.model.BackgroundSpec.ASSET) }
        }
        val unused = dao.idsForCanvas(canvasLocalId).filterNot { it in used }
        if (unused.isNotEmpty()) dao.deleteByIds(unused)
    }

    suspend fun deleteForCanvas(canvasLocalId: String) = dao.deleteForCanvas(canvasLocalId)

    suspend fun totalBytes(canvasLocalId: String): Long = dao.totalBytes(canvasLocalId)
}
