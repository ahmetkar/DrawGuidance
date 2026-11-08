package com.karapp.drawguidance.data.library

import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import com.karapp.drawguidance.content.model3d.Texture
import com.karapp.drawguidance.content.model3d.TextureCatalog
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.local.Texture3DDao
import com.karapp.drawguidance.data.local.Texture3DEntity
import com.karapp.drawguidance.diagnostics.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Doku seçme ekranındaki öge. */
data class TextureItem(val id: String, val name: String, val category: String)

/**
 * v23 hazır 3B dokularının deposu (`textures3d`): ilk açılışta [TextureCatalog] dokuları üretilip WebP olarak yazılır;
 * 3B çizici dokuları buradan (bellekteki çözülmüş halleriyle) okur. Doku seçme ekranındaki küçük resimler
 * [CanvasImageCache]'te "tex:<kimlik>" anahtarıyla tutulur.
 */
class TextureRepository(
    private val dao: Texture3DDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    // Çözülmüş dokular (256×256 ≈ 256 KB); sık kullanılanlar bellekte kalır
    private val cache = object : LruCache<String, Texture>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Texture): Int = value.px.size * 4
    }
    private val mutex = Mutex()
    @Volatile private var seeded = false

    val items: List<TextureItem> = TextureCatalog.all.map { TextureItem(it.id, it.name, it.category) }

    init {
        CanvasImageCache.textureLoader = { key ->
            scope.launch {
                val id = key.removePrefix(KEY)
                texture(id)?.let { CanvasImageCache.put(key, BitmapCodec.bitmap(it.px, it.w, it.h).asImageBitmap()) }
            }
        }
    }

    fun warmUp() {
        scope.launch { runCatching { seed() }.onFailure { AppLog.e("Texture3D", "Dokular hazırlanamadı", it) } }
    }

    suspend fun seed() = mutex.withLock {
        if (seeded) return@withLock
        val existing = dao.versions().associate { it.id to it.version }
        TextureCatalog.all.forEachIndexed { i, def ->
            if (existing[def.id] == TextureCatalog.VERSION) return@forEachIndexed
            val t = def.generate(TextureCatalog.SIZE)
            cache.put(def.id, t)
            dao.upsert(listOf(Texture3DEntity(def.id, def.name, def.category, BitmapCodec.encode(t.px, t.w, t.h, true, 90), t.w, i, TextureCatalog.VERSION)))
        }
        seeded = true
    }

    /** Bellekteki doku (çizici için; yoksa null). */
    fun cached(id: String): Texture? = cache.get(id)

    /** Dokuyu çözer (veritabanında yoksa üretir). */
    suspend fun texture(id: String): Texture? {
        cache.get(id)?.let { return it }
        // v27 çözme/üretme ana iş parçacığında değil (pencere açılırken takılma olmasın)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val row = runCatching { dao.get(id) }.getOrNull()
            val t = row?.data?.let { BitmapCodec.pixels(it) }?.let { (w, h, px) -> Texture(w, h, px) }
                ?: TextureCatalog.byId(id)?.generate(TextureCatalog.SIZE)
            t?.also { cache.put(id, it) }
        }
    }

    /**
     * v24 içe aktarılan modelin dokusunu saklar ("imp.…" kimliği, "İçe aktarılan" kategorisi): model kaydedildikten
     * sonra da çizici aynı kimlikle okur.
     */
    suspend fun saveImported(id: String, name: String, texture: Texture) {
        cache.put(id, texture)
        dao.upsert(listOf(Texture3DEntity(id, name, IMPORTED, BitmapCodec.encode(texture.px, texture.w, texture.h, true, 90), texture.w, 10_000, 1)))
    }

    /** Verilen dokuları belleğe alır (çizimden önce). */
    suspend fun preload(ids: Collection<String>) {
        ids.forEach { texture(it) }
    }

    companion object {
        const val KEY = "tex:"
        const val IMPORTED = "İçe aktarılan"
    }
}
