package com.karapp.drawguidance.data.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.karapp.drawguidance.content.paper.ImageLayout
import com.karapp.drawguidance.content.paper.PaperCatalog
import com.karapp.drawguidance.content.paper.PaperCodec
import com.karapp.drawguidance.content.paper.PaperImages
import com.karapp.drawguidance.content.paper.PaperPattern
import com.karapp.drawguidance.content.paper.PaperUsage
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.local.PaperAssetDao
import com.karapp.drawguidance.data.local.PaperAssetEntity
import com.karapp.drawguidance.diagnostics.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/** Arka plan kütüphanesinin bir ögesi (seçim listeleri için). */
data class PaperItem(val id: String, val name: String, val usage: PaperUsage, val isImage: Boolean, val layout: ImageLayout?)

/**
 * v23 desenlerin ve hazır resimlerin bellekteki kaydı: çizim kodu (tuval, yazı kutusu, dışa aktarma) buradan okur.
 * Veritabanı henüz yüklenmediyse katalog kullanılır (ilk açılışta da arka plan hemen görünür). Resimler
 * [CanvasImageCache]'te "paper:<kimlik>" anahtarıyla tutulur; bellekte yoksa istenince yüklenir.
 */
object PaperLibrary {
    const val KEY = "paper:"

    @Volatile private var patterns: Map<String, PaperPattern> = emptyMap()
    @Volatile private var layouts: Map<String, ImageLayout> = emptyMap()

    fun pattern(id: String?): PaperPattern? = id?.let { patterns[it] ?: PaperCatalog.byId(it) }

    fun layout(id: String): ImageLayout = layouts[id] ?: PaperImages.byId(id)?.layout ?: ImageLayout(ImageLayout.COVER)

    /** Hazır resim (yüklenmediyse yükleme başlar, null döner). */
    fun image(id: String): ImageBitmap? = CanvasImageCache.get(KEY + id)

    /** Hazır resmi bekleyerek yükler (dışa aktarmadan önce). */
    @Volatile internal var load: (suspend (String) -> Unit)? = null

    /**
     * Tuvalin kullandığı tüm arka plan resimlerinin (tuval arka planı ve yazı kutuları) bellekte olmasını sağlar;
     * galeriden eklenenler [assets] ile yüklenir.
     */
    suspend fun ensureLoaded(
        background: com.karapp.drawguidance.data.model.BackgroundSpec,
        strokes: List<com.karapp.drawguidance.data.model.Stroke>,
        assets: suspend (List<String>) -> Unit,
    ) {
        val paper = HashSet<String>(); val gallery = HashSet<String>()
        background.paperImageId?.let(paper::add); background.assetId?.let(gallery::add)
        strokes.forEach { s ->
            val id = s.text?.box?.imageId ?: return@forEach
            if (id.startsWith(com.karapp.drawguidance.data.model.BackgroundSpec.ASSET)) gallery += id.removePrefix(com.karapp.drawguidance.data.model.BackgroundSpec.ASSET)
            else paper += id
        }
        paper.filterNot { CanvasImageCache.contains(KEY + it) }.forEach { id -> load?.invoke(id) }
        if (gallery.isNotEmpty()) assets(gallery.toList())
    }

    internal fun update(patterns: Map<String, PaperPattern>, layouts: Map<String, ImageLayout>) {
        this.patterns = patterns
        this.layouts = layouts
    }
}

/**
 * v23 arka plan kütüphanesinin deposu (`paper_assets`). Açılışta (arka planda) katalog sürümü denetlenir: eksik ya da
 * eski desenler sıkıştırılmış ikili biçimde ([PaperCodec]), resimler üretilip WebP/PNG olarak yazılır; sonra desenler
 * bir kez belleğe alınır. Resimler istendikçe çözülür.
 */
/** v36 sahne kimliklerinin öneki (v40'ta üreticisi kaldırıldı; veritabanındaki kopyaları korunur). */
private const val LEGACY_SCENE = "scene."

class PaperRepository(
    private val dao: PaperAssetDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _items = MutableStateFlow(catalogItems())
    val items: StateFlow<List<PaperItem>> = _items.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var started = false

    init {
        CanvasImageCache.paperLoader = { key -> scope.launch { loadImage(key.removePrefix(PaperLibrary.KEY)) } }
        PaperLibrary.load = { id -> loadImage(id) }
    }

    fun warmUp() {
        if (started) return
        started = true
        scope.launch {
            runCatching { seed(); load() }.onFailure { AppLog.e("Paper", "Arka plan kütüphanesi hazırlanamadı", it) }
        }
    }

    fun of(usage: PaperUsage, images: Boolean): List<PaperItem> = _items.value.filter { it.usage == usage && it.isImage == images }

    /** Eksik ya da eski kayıtları yazar, katalogda olmayanları siler. */
    suspend fun seed() = mutex.withLock {
        val existing = dao.versions().associate { it.id to it.version }
        val patternRows = PaperCatalog.all.withIndex().filter { existing[it.value.id] != PaperCatalog.VERSION }.map { (i, p) ->
            PaperAssetEntity(
                p.id, PaperAssetEntity.PATTERN, p.usage.name, p.name, PaperCodec.encode(p),
                (p.tileW * 16f).roundToInt(), (p.tileH * 16f).roundToInt(), "", i, PaperCatalog.VERSION,
            )
        }
        if (patternRows.isNotEmpty()) dao.upsert(patternRows)
        // Resimler tek tek üretilip yazılır (bellekte hep birlikte durmaz)
        PaperImages.all.forEachIndexed { i, d ->
            if (existing[d.id] == PaperImages.VERSION) return@forEachIndexed
            val bytes = BitmapCodec.encode(d.generate(), d.w, d.h, d.opaque)
            dao.upsert(listOf(PaperAssetEntity(d.id, PaperAssetEntity.IMAGE, d.usage.name, d.name, bytes, d.w, d.h, d.layout.encode(), i, PaperImages.VERSION)))
        }
        // v40 v36 sahnelerinin üreticisi kaldırıldı: daha önce kullanılıp yazılmış sahneler silinmez (eski sayfalar
        // arka planlarını bu kopyadan gösterir); hazır çizgi roman arka planları ("cbg.") veritabanına yazılmaz
        val valid = HashSet<String>().apply { PaperCatalog.all.forEach { add(it.id) }; PaperImages.all.forEach { add(it.id) } }
        val stale = existing.keys.filterNot { it in valid || it.startsWith(LEGACY_SCENE) }
        if (stale.isNotEmpty()) dao.delete(stale)
    }

    /** Desenleri ve resim yerleşimlerini belleğe alır. */
    suspend fun load() {
        val patterns = HashMap<String, PaperPattern>()
        dao.byKind(PaperAssetEntity.PATTERN).forEach { row ->
            runCatching { PaperCodec.decode(row.data) }.getOrNull()?.let { patterns[it.id] = it }
        }
        val info = dao.info()
        val layouts = info.filter { it.kind == PaperAssetEntity.IMAGE }.associate { it.id to ImageLayout.decode(it.insets) }
        PaperLibrary.update(patterns, layouts)
        if (info.isNotEmpty()) {
            _items.value = info.mapNotNull { row ->
                if (row.id.startsWith(LEGACY_SCENE)) return@mapNotNull null
                val usage = runCatching { PaperUsage.valueOf(row.usage) }.getOrNull() ?: return@mapNotNull null
                val image = row.kind == PaperAssetEntity.IMAGE
                PaperItem(row.id, row.name, usage, image, if (image) ImageLayout.decode(row.insets) else null)
            }
        }
    }

    /** Hazır resmi çözüp önbelleğe koyar (veritabanında yoksa yeniden üretir). */
    suspend fun loadImage(id: String) {
        if (id.startsWith(com.karapp.drawguidance.data.comicassets.ComicAssets.BG_PREFIX)) return loadComicBackground(id)
        val bytes = runCatching { dao.get(id)?.data }.getOrNull()
        val bmp = bytes?.let { BitmapCodec.decode(it) } ?: PaperImages.byId(id)?.let { BitmapCodec.bitmap(it.generate(), it.w, it.h) } ?: return
        CanvasImageCache.put(PaperLibrary.KEY + id, bmp.asImageBitmap())
    }

    /** v40 hazır çizgi roman/manga arka planı: uygulamadaki WebP'den (en çok 1600 px) çözülür. */
    private fun loadComicBackground(id: String) {
        val bg = com.karapp.drawguidance.data.comicassets.ComicAssets.backgroundByPaperId(id) ?: return
        val bmp = runCatching { com.karapp.drawguidance.data.comicassets.ComicAssets.decode(bg.file, 1600) }
            .onFailure { AppLog.e("Paper", "Hazır arka plan okunamadı: $id", it) }.getOrNull() ?: return
        CanvasImageCache.put(PaperLibrary.KEY + id, bmp.asImageBitmap())
    }

    private fun catalogItems(): List<PaperItem> =
        PaperCatalog.all.map { PaperItem(it.id, it.name, it.usage, false, null) } +
            PaperImages.all.map { PaperItem(it.id, it.name, it.usage, true, it.layout) }
}
