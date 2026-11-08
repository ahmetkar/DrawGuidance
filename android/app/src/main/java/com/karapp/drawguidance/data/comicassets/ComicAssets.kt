package com.karapp.drawguidance.data.comicassets

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * v40 çizgi roman modunun hazır karakterleri ve arka planları (uygulamayla gelen WebP resimler; `assets/comic/`).
 * Katalog `comic/catalog.json` dosyasındadır (üretim: `tools/comic_assets/build_assets.py`). Adlar 7 dilde katalogda
 * durur; seçili dil yoksa Türkçe ad gösterilir.
 *
 * Karakterler stile (manga / çizgi roman) ve temaya göre gruplanır; her karakterin çeşitleri dört gruptadır
 * (İfadeler, Pozlar, Aksiyon, Günlük). Arka planlar da stile ve alt kategoriye göre gruplanır.
 */
@Serializable
data class ComicCatalog(
    val version: Int = 1,
    val themes: List<ComicTheme> = emptyList(),
    val characters: List<ComicCharacter> = emptyList(),
    val bgCategories: List<ComicBgCategory> = emptyList(),
    val backgrounds: List<ComicBackground> = emptyList(),
    /** v42 hazır efekt resimleri (patlama, enerji, duman...; saydam WebP). */
    val effectCategories: List<ComicBgCategory> = emptyList(),
    val effects: List<ComicProp> = emptyList(),
    /** v42 hazır nesneler (silah, fener, belge, hazine...). */
    val objectCategories: List<ComicBgCategory> = emptyList(),
    val objects: List<ComicProp> = emptyList(),
)

@Serializable
data class ComicTheme(val id: String, val names: Map<String, String>)

@Serializable
data class ComicBgCategory(val id: String, val names: Map<String, String>)

@Serializable
data class ComicVariant(
    val id: String,
    /** [VariantGroup.id]: ifade, poz, aksiyon, gunluk. */
    val group: String,
    val file: String,
    val thumb: String,
    val w: Int,
    val h: Int,
)

@Serializable
data class ComicCharacter(
    val id: String,
    /** [ComicStyle.id]. */
    val style: String,
    val theme: String,
    val names: Map<String, String>,
    val variants: List<ComicVariant>,
    /** v42 karakterin tema paketleri (ilki [theme]; boşsa yalnız [theme]). */
    val themes: List<String> = emptyList(),
) {
    /** v42 tema paketleri ([themes] boşsa [theme]). */
    val packs: List<String> get() = themes.ifEmpty { listOf(theme) }

    fun variants(group: VariantGroup): List<ComicVariant> = variants.filter { it.group == group.id }

    /** Izgaradaki küçük resim: ilk poz (yoksa ilk çeşit). */
    val cover: ComicVariant get() = variants.firstOrNull { it.group == VariantGroup.POSE.id } ?: variants.first()
}

@Serializable
data class ComicBackground(
    val id: String,
    val style: String,
    val category: String,
    val names: Map<String, String>,
    val file: String,
    val thumb: String,
    val w: Int,
    val h: Int,
    /** v42 mekânın tema paketleri. */
    val themes: List<String> = emptyList(),
) {
    /** Sayfa arka planında saklanan kimlik ([com.karapp.drawguidance.data.model.BackgroundSpec.PAPER] ile). */
    val paperId: String get() = ComicAssets.BG_PREFIX + id
}

/**
 * v42 hazır efekt ya da nesne resmi (saydam WebP). Bir resim birden çok stilde ([styles]) ve tema paketinde ([themes])
 * gösterilebilir.
 */
@Serializable
data class ComicProp(
    val id: String,
    val styles: List<String>,
    val category: String,
    val themes: List<String> = emptyList(),
    val file: String,
    val thumb: String,
    val w: Int,
    val h: Int,
)

/** v42 tema paketindeki varlık türleri (veritabanındaki `comic_assets.kind`). */
enum class ComicAssetKind(val id: String) { CHARACTER("character"), BACKGROUND("background"), EFFECT("effect"), OBJECT("object") }

/** v40 iki stil: manga (siyah-beyaz ya da anime) ve çizgi roman (renkli batı tarzı). */
enum class ComicStyle(val id: String) { MANGA("manga"), COMIC("cizgi_roman") }

/** v40 karakter çeşitlerinin grupları (pencerede bu sırayla). */
enum class VariantGroup(val id: String) { EXPRESSION("ifade"), POSE("poz"), ACTION("aksiyon"), DAILY("gunluk") }

object ComicAssets {
    const val CATALOG = "comic/catalog.json"

    /** Hazır arka planların kimlik öneki ("paper:cbg.<kimlik>"). */
    const val BG_PREFIX = "cbg."

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Varlık açıcı (uygulamada Koin'deki bağlamın AssetManager'ı; testlerde değiştirilebilir). v43 mutlak yol ("/" ile
     * başlayan) sunucudan inmiş paketin dosyasıdır ve doğrudan dosyadan açılır.
     */
    @Volatile var opener: (String) -> InputStream = { path ->
        if (path.startsWith("/")) java.io.FileInputStream(path)
        else {
            val ctx = org.koin.core.context.GlobalContext.get().get<android.content.Context>()
            ctx.assets.open(path)
        }
    }

    @Volatile private var loaded: ComicCatalog? = null

    /** v43 sunucudan inmiş çizgi roman paketlerinin içerikleri ([ComicPacks.extras]); katalogla birleştirilir. */
    @Volatile var extras: ComicCatalog = ComicCatalog()
        set(value) { field = value; merged = null }

    @Volatile private var merged: ComicCatalog? = null

    /** Uygulamayla gelen katalog (ilk istekte okunur; okunamazsa boş). */
    val bundled: ComicCatalog
        get() = loaded ?: synchronized(this) {
            loaded ?: (runCatching { opener(CATALOG).use { parse(it.readBytes().decodeToString()) } }
                .onFailure { com.karapp.drawguidance.diagnostics.AppLog.e("ComicAssets", "Katalog okunamadı", it) }
                .getOrNull() ?: ComicCatalog()).also { loaded = it }
        }

    /** Katalog: uygulamayla gelenler + v43 inmiş paketler. */
    val catalog: ComicCatalog
        get() = merged ?: synchronized(this) {
            merged ?: ComicPacks.merge(bundled, extras).also { merged = it }
        }

    fun parse(text: String): ComicCatalog = json.decodeFromString(ComicCatalog.serializer(), text)

    /** Testler için: kataloğu yeniden okutur. */
    fun reset() { loaded = null; merged = null; ComicThemeIndex.set(null) }

    fun name(names: Map<String, String>, lang: String = com.karapp.drawguidance.i18n.I18n.language): String =
        names[lang] ?: names["tr"] ?: names.values.firstOrNull().orEmpty()

    fun character(id: String): ComicCharacter? = catalog.characters.firstOrNull { it.id == id }

    fun background(id: String): ComicBackground? = catalog.backgrounds.firstOrNull { it.id == id }

    /** "cbg.<kimlik>" kimliğinin arka planı. */
    fun backgroundByPaperId(paperId: String?): ComicBackground? =
        paperId?.takeIf { it.startsWith(BG_PREFIX) }?.let { background(it.removePrefix(BG_PREFIX)) }

    /** Süzgeç: stil ve tema paketi (null: hepsi). v42: paketler veritabanındaki bağlantılardan ([ComicThemeIndex]). */
    fun characters(style: ComicStyle?, theme: String?): List<ComicCharacter> {
        val idx = if (theme != null) ComicThemeIndex.value else null
        return catalog.characters.filter {
            (style == null || it.style == style.id) && (idx == null || idx.has(ComicAssetKind.CHARACTER, it.id, theme!!))
        }
    }

    /** Stilde karakteri olan tema paketleri (paket sırasıyla). */
    fun themes(style: ComicStyle?): List<ComicTheme> = themesFor(ComicAssetKind.CHARACTER, style)

    /** v42 stilde bu türden varlığı olan tema paketleri (paket sırasıyla). */
    fun themesFor(kind: ComicAssetKind, style: ComicStyle?): List<ComicTheme> {
        val idx = ComicThemeIndex.value
        val ids: List<String> = when (kind) {
            ComicAssetKind.CHARACTER -> catalog.characters.filter { style == null || it.style == style.id }.map { it.id }
            ComicAssetKind.BACKGROUND -> catalog.backgrounds.filter { style == null || it.style == style.id }.map { it.id }
            ComicAssetKind.EFFECT -> catalog.effects.filter { style == null || style.id in it.styles }.map { it.id }
            ComicAssetKind.OBJECT -> catalog.objects.filter { style == null || style.id in it.styles }.map { it.id }
        }
        val used = HashSet<String>()
        ids.forEach { used += idx.packsOf(kind, it) }
        return idx.packs.filter { it.id in used }
    }

    fun backgrounds(style: ComicStyle, category: String?, theme: String? = null): List<ComicBackground> {
        val idx = if (theme != null) ComicThemeIndex.value else null
        return catalog.backgrounds.filter {
            it.style == style.id && (category == null || it.category == category) &&
                (idx == null || idx.has(ComicAssetKind.BACKGROUND, it.id, theme!!))
        }
    }

    fun bgCategories(style: ComicStyle, theme: String? = null): List<ComicBgCategory> {
        val used = backgrounds(style, null, theme).mapTo(HashSet()) { it.category }
        return catalog.bgCategories.filter { it.id in used }
    }

    fun effect(id: String): ComicProp? = catalog.effects.firstOrNull { it.id == id }

    fun obj(id: String): ComicProp? = catalog.objects.firstOrNull { it.id == id }

    /** v42 stildeki efektler ya da nesneler (kategori/tema null: hepsi). */
    fun props(kind: ComicAssetKind, style: ComicStyle, category: String?, theme: String? = null): List<ComicProp> {
        val idx = if (theme != null) ComicThemeIndex.value else null
        return (if (kind == ComicAssetKind.EFFECT) catalog.effects else catalog.objects).filter {
            style.id in it.styles && (category == null || it.category == category) && (idx == null || idx.has(kind, it.id, theme!!))
        }
    }

    /** v42 stilde (ve temada) resmi olan efekt/nesne kategorileri (katalog sırasıyla). */
    fun propCategories(kind: ComicAssetKind, style: ComicStyle, theme: String? = null): List<ComicBgCategory> {
        val used = props(kind, style, null, theme).mapTo(HashSet()) { it.category }
        return (if (kind == ComicAssetKind.EFFECT) catalog.effectCategories else catalog.objectCategories).filter { it.id in used }
    }

    fun theme(id: String): ComicTheme? = catalog.themes.firstOrNull { it.id == id }

    /** Varlığın baytları (ana iş parçacığında çağrılmamalı). */
    fun bytes(path: String): ByteArray = opener(path).use { it.readBytes() }

    /**
     * Varlığı en çok [maxSide] kenarla çözer (BitmapFactory inSampleSize: büyük resim önce tam boyda açılmaz).
     * Ana iş parçacığında çağrılmamalı.
     */
    fun decode(path: String, maxSide: Int): android.graphics.Bitmap? {
        val bytes = bytes(path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** v40 seçici ızgaralarının küçük resim önbelleği (LRU, bayt sınırlı). */
object ComicThumbs {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 16).coerceIn(4L shl 20, 24L shl 20).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(key: Pair<String, Int>): ImageBitmap? = cache.get("${key.first}|${key.second}")

    /** Arka planda çağrılır (G/Ç iş parçacığı). */
    fun load(key: Pair<String, Int>): ImageBitmap? {
        cached(key)?.let { return it }
        val bmp = ComicAssets.decode(key.first, key.second) ?: return null
        return bmp.asImageBitmap().also { cache.put("${key.first}|${key.second}", it) }
    }
}
