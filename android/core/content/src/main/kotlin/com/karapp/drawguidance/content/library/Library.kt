package com.karapp.drawguidance.content.library

import com.karapp.drawguidance.content.vector.VPath
import com.karapp.drawguidance.content.vector.VectorBuilder

/** Şeklin parmakla yerleştirilişi: serbest kutu, oranı korunan kutu ya da iki nokta arası çizgi. */
enum class Fit { BOX, SQUARE, LINE }

/**
 * Şeklin sunulduğu tuval türleri: [ART] resim, [NOTES] not tuvali, [BOTH] ikisi birden. [KIDS]: yalnız "Çocuklar için
 * çizim" modu (kalın kenarlı, her parçası Alan boya ile boyanan boyama kitabı çizimleri); resim/not tuvallerinde ve
 * [BOTH] sorgusunda görünmez, [BOTH] kategorileri de çocuk modunda listelenmez (bkz. [ShapeLibrary.forAudience]).
 */
enum class Audience { ART, NOTES, BOTH, KIDS }

/**
 * Kütüphanedeki bir şekil. [build] birim kutuda (0..1) çizer; uygulama kutuyu sürüklenen dikdörtgene eşler.
 * [aspect] dokunarak eklemede kullanılan en/boy oranı ([Fit.BOX] için). [id] kayıtta saklanır: değiştirilmemeli.
 */
class LibShape(
    val id: String,
    val title: String,
    val fit: Fit = Fit.BOX,
    val aspect: Float = 1f,
    /** Aramada ve yardımda kullanılan anahtar sözcükler. */
    val tags: List<String> = emptyList(),
    /** v45 parametreli şekil: değerleri, formülü ve tutamakları olan çizim (yoksa sabit çizim). */
    val param: com.karapp.drawguidance.content.library.science.ParamModel? = null,
    val build: VectorBuilder.() -> Unit,
) {
    fun paths(resolution: Float = 512f): List<VPath> = VectorBuilder(resolution).apply(build).paths

    /** v45: şeklin kayıttaki değerleriyle çizimi (parametresiz şekilde [paths]). */
    fun paths(resolution: Float, params: Map<String, String>?): List<VPath> =
        param?.paths(params, resolution) ?: paths(resolution)
}

/** Şekil seçicideki bir kategori. */
class LibCategory(
    val id: String,
    val title: String,
    val audience: Audience,
    val shapes: List<LibShape>,
    /** Kategorinin üst grubu (seçicide sekme): ör. "Fen", "Hazır çizimler". */
    val section: String = "",
    /** Seçicide görünür mü (v17: eski hazır çizimler seçiciden kalktı; eski kayıtlar açılsın diye kütüphanede). */
    val listed: Boolean = true,
)

/**
 * Tüm kütüphane şekillerinin tek kaydı. Kategoriler ayrı dosyalarda tanımlanır ve buraya eklenir; uygulama
 * yalnızca bu nesne üzerinden şekil bulur, çizer ve seçicide listeler (yeni şekil eklemek = listeye eklemek).
 */
object ShapeLibrary {

    /** Kayıtta saklanan şekil türü ön eki: "lib.<id>". */
    const val PREFIX = "lib."

    /** Uygulamayla gelen kategoriler. */
    val bundledCategories: List<LibCategory> by lazy { LibraryCategories.all() }

    private val bundledById: Map<String, LibShape> by lazy {
        val map = LinkedHashMap<String, LibShape>()
        bundledCategories.forEach { c -> c.shapes.forEach { s -> require(map.put(s.id, s) == null || map[s.id] === s) { "Yinelenen şekil kimliği: ${s.id}" } } }
        map
    }

    /** v33 sunucudan gelen (daha yeni katalogdaki) şekiller: aynı kimlikli yerleşiklerin yerine geçer. */
    @Volatile private var remoteById: Map<String, LibShape> = emptyMap()
    @Volatile private var merged: List<LibCategory>? = null

    /** Seçicide gösterilen kategoriler (sunucudan gelenler uygulanmış). */
    val categories: List<LibCategory> get() = merged ?: bundledCategories

    val shapes: Collection<LibShape> get() = if (remoteById.isEmpty()) bundledById.values else (bundledById + remoteById).values

    fun find(id: String): LibShape? = id.removePrefix(PREFIX).let {
        // v45 parametreli yerleşik şekiller sunucudaki sabit çizimle değişmez (değerleri, formülü, tutamakları var)
        bundledById[it]?.takeIf { s -> s.param != null } ?: remoteById[it] ?: bundledById[it] ?: if (it.startsWith(com.karapp.drawguidance.content.comic.ComicCharacters.ID_PREFIX)) {
            // v36 karakterler kimlikten üretilir (listede ve katalogda yok)
            com.karapp.drawguidance.content.comic.ComicCharacters.shape(it)
        } else null
    }

    /**
     * v33 sunucunun kataloğunu uygular: aynı kimlikli şekiller sunucudakiyle değişir (kategorisi aynı kalır), yeni
     * kimlikler kendi kategorilerinde (yeni kategoriyse sona) eklenir. Boş liste uygulananları kaldırır.
     */
    @Synchronized
    fun install(remote: List<com.karapp.drawguidance.content.catalog.CatalogVector>) {
        if (remote.isEmpty()) { remoteById = emptyMap(); merged = null; return }
        val shapes = remote.associate { it.id to com.karapp.drawguidance.content.catalog.CatalogCodec.libShape(it) }
        val byCategory = remote.filter { it.id !in bundledById }.groupBy { it.categoryId }
        val known = bundledCategories.map { it.id }.toSet()
        val updated = bundledCategories.map { c ->
            val extra = byCategory[c.id].orEmpty().map { shapes.getValue(it.id) }
            LibCategory(c.id, c.title, c.audience, c.shapes.map { shapes[it.id] ?: it } + extra, c.section, c.listed)
        }
        val added = com.karapp.drawguidance.content.catalog.CatalogCodec.newCategories(remote.filter { it.id !in bundledById }, known)
        remoteById = shapes
        merged = updated + added
    }

    fun forAudience(audience: Audience): List<LibCategory> = categories.filter { shownIn(it.audience, audience) }

    /**
     * [category] kitlesindeki bir kategori [audience] tuvalinde sunulur mu: resim/not kendi kategorileri ve [Audience.BOTH];
     * [Audience.BOTH] sorgusu çocuk modu dışındaki her şey; [Audience.KIDS] yalnız çocuk kategorileri.
     */
    fun shownIn(category: Audience, audience: Audience): Boolean = when (audience) {
        Audience.KIDS -> category == Audience.KIDS
        Audience.BOTH -> category != Audience.KIDS
        Audience.ART, Audience.NOTES -> category == audience || category == Audience.BOTH
    }
}
