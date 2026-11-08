package com.karapp.drawguidance.data.comicassets

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * v42 çizgi roman tema paketleri. Her paketin (okul, fantastik, kıyamet sonrası...) karakterleri, nesneleri,
 * efektleri ve mekân arka planları vardır. Paketler ve varlıklar ayrı tablolardadır; hangi varlığın hangi pakette
 * olduğunu iki yabancı anahtarlı bağlantı tablosu (`theme_pack_assets`) tutar. Bir varlık birden çok pakette olabilir.
 *
 * Yerleşik paketler ve bağlantılar `comic/catalog.json`'dan ([ThemePackRepository.seed]) yazılır. Sonradan eklenen
 * karakter, nesne, arka plan ya da efekt; kataloğa "themes" alanıyla eklenince ya da [ThemePackRepository.link] ile
 * bir pakete bağlanır. Elle eklenen bağlantılar (`source = user`) katalog yeniden yazılırken korunur.
 */
@Entity(tableName = "theme_packs")
data class ThemePackEntity(
    @PrimaryKey val id: String,
    /** Dil kodu → ad (JSON). */
    val namesJson: String,
    val sortOrder: Int,
    /** "builtin" (katalog) ya da "user". */
    val source: String,
    val updatedAt: Long,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `theme_packs` (`id` TEXT NOT NULL, `namesJson` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    }
}

/** v42 tema paketine bağlanabilen çizgi roman varlığı (karakter, mekân, efekt ya da nesne). */
@Entity(tableName = "comic_assets", indices = [Index("kind")])
data class ComicAssetEntity(
    /** "<tür>:<kimlik>" ([ThemeIndex.key]). */
    @PrimaryKey val id: String,
    /** [ComicAssetKind.id]. */
    val kind: String,
    /** Katalogdaki kimlik. */
    val refId: String,
    /** Virgülle stiller (manga, cizgi_roman). */
    val styles: String,
    val category: String?,
    /** Resim dosyası (karakterde kapak resmi). */
    val file: String,
    val source: String,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `comic_assets` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `refId` TEXT NOT NULL, `styles` TEXT NOT NULL, `category` TEXT, `file` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`id`))"
        const val INDEX_SQL = "CREATE INDEX IF NOT EXISTS `index_comic_assets_kind` ON `comic_assets` (`kind`)"
    }
}

/** v42 paket ↔ varlık (iki yabancı anahtar; paket ya da varlık silinince bağlantı da silinir). */
@Entity(
    tableName = "theme_pack_assets",
    primaryKeys = ["packId", "assetId"],
    foreignKeys = [
        ForeignKey(entity = ThemePackEntity::class, parentColumns = ["id"], childColumns = ["packId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ComicAssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("assetId")],
)
data class ThemePackAssetEntity(
    val packId: String,
    val assetId: String,
    /** "builtin" (katalog) ya da "user" (elle bağlandı; yeniden yazmada korunur). */
    val source: String,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `theme_pack_assets` (`packId` TEXT NOT NULL, `assetId` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`packId`, `assetId`), FOREIGN KEY(`packId`) REFERENCES `theme_packs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`assetId`) REFERENCES `comic_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        const val INDEX_SQL = "CREATE INDEX IF NOT EXISTS `index_theme_pack_assets_assetId` ON `theme_pack_assets` (`assetId`)"
    }
}

@Dao
interface ThemePackDao {
    @Query("SELECT * FROM theme_packs ORDER BY sortOrder, id")
    fun observePacks(): Flow<List<ThemePackEntity>>

    @Query("SELECT * FROM theme_pack_assets")
    fun observeLinks(): Flow<List<ThemePackAssetEntity>>

    @Query("SELECT * FROM theme_packs ORDER BY sortOrder, id")
    suspend fun packs(): List<ThemePackEntity>

    @Query("SELECT * FROM comic_assets")
    suspend fun assets(): List<ComicAssetEntity>

    @Query("SELECT * FROM theme_pack_assets")
    suspend fun links(): List<ThemePackAssetEntity>

    /** Paketin belirli türdeki varlıkları. */
    @Query("SELECT a.* FROM comic_assets a JOIN theme_pack_assets l ON l.assetId = a.id WHERE l.packId = :packId AND a.kind = :kind ORDER BY a.refId")
    suspend fun assetsOf(packId: String, kind: String): List<ComicAssetEntity>

    @Query("SELECT packId FROM theme_pack_assets WHERE assetId = :assetId")
    suspend fun packsOf(assetId: String): List<String>

    // Upsert (güncelle ya da ekle): REPLACE satırı silip yeniden eklediğinden yabancı anahtarın CASCADE'i elle
    // eklenen bağlantıları da silerdi
    @androidx.room.Upsert
    suspend fun upsertPacks(items: List<ThemePackEntity>)

    @androidx.room.Upsert
    suspend fun upsertAssets(items: List<ComicAssetEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(items: List<ThemePackAssetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLink(item: ThemePackAssetEntity)

    @Query("DELETE FROM theme_pack_assets WHERE packId = :packId AND assetId = :assetId")
    suspend fun unlink(packId: String, assetId: String): Int

    @Query("DELETE FROM theme_pack_assets WHERE source = 'builtin'")
    suspend fun clearBuiltinLinks()

    @Query("SELECT id FROM theme_packs WHERE source = 'builtin'")
    suspend fun builtinPackIds(): List<String>

    @Query("SELECT id FROM comic_assets WHERE source = 'builtin'")
    suspend fun builtinAssetIds(): List<String>

    @Query("DELETE FROM theme_packs WHERE id IN (:ids)")
    suspend fun deletePacks(ids: List<String>)

    @Query("DELETE FROM comic_assets WHERE id IN (:ids)")
    suspend fun deleteAssets(ids: List<String>)

    @Query("SELECT COUNT(*) FROM theme_pack_assets")
    suspend fun linkCount(): Int

    /**
     * Kataloğu bir işlemde yazar: yerleşik paketler ve varlıklar güncellenir, katalogdan kalkanlar silinir (bağlantıları
     * da), yerleşik bağlantılar yeniden yazılır; elle eklenenler korunur.
     */
    @Transaction
    suspend fun replaceBuiltin(packs: List<ThemePackEntity>, assets: List<ComicAssetEntity>, links: List<ThemePackAssetEntity>) {
        val packIds = packs.mapTo(HashSet()) { it.id }
        val assetIds = assets.mapTo(HashSet()) { it.id }
        // SQLite'ın değişken sınırı (999) aşılmasın: silinecekler parça parça
        builtinPackIds().filter { it !in packIds }.chunked(500).forEach { deletePacks(it) }
        builtinAssetIds().filter { it !in assetIds }.chunked(500).forEach { deleteAssets(it) }
        upsertPacks(packs)
        upsertAssets(assets)
        clearBuiltinLinks()
        insertLinks(links)
    }
}

/**
 * v42 tema paketlerinin bellek dizini (seçici pencereleri bunu okur): paketler sırasıyla ve varlık anahtarı →
 * paket kimlikleri.
 */
data class ThemeIndex(val packs: List<ComicTheme>, val links: Map<String, Set<String>>) {
    fun packsOf(kind: ComicAssetKind, id: String): Set<String> = links[key(kind, id)].orEmpty()

    fun has(kind: ComicAssetKind, id: String, pack: String): Boolean = pack in packsOf(kind, id)

    companion object {
        fun key(kind: ComicAssetKind, id: String) = "${kind.id}:$id"

        /** Katalogdaki "themes" alanlarından (veritabanı hazır olmadan önce ve testlerde). */
        fun fromCatalog(c: ComicCatalog): ThemeIndex {
            val map = HashMap<String, MutableSet<String>>()
            fun add(kind: ComicAssetKind, id: String, themes: List<String>) {
                if (themes.isNotEmpty()) map.getOrPut(key(kind, id)) { LinkedHashSet() } += themes
            }
            val known = c.themes.mapTo(HashSet()) { it.id }
            c.characters.forEach { add(ComicAssetKind.CHARACTER, it.id, it.packs.filter { t -> t in known }) }
            c.backgrounds.forEach { add(ComicAssetKind.BACKGROUND, it.id, it.themes.filter { t -> t in known }) }
            c.effects.forEach { add(ComicAssetKind.EFFECT, it.id, it.themes.filter { t -> t in known }) }
            c.objects.forEach { add(ComicAssetKind.OBJECT, it.id, it.themes.filter { t -> t in known }) }
            return ThemeIndex(c.themes, map)
        }
    }
}

/** v42 geçerli tema dizini: veritabanından gelince [ThemePackRepository] günceller; o zamana dek katalogdan. */
object ComicThemeIndex {
    private val state = MutableStateFlow<ThemeIndex?>(null)
    val flow: StateFlow<ThemeIndex?> = state.asStateFlow()

    val value: ThemeIndex get() = state.value ?: ThemeIndex.fromCatalog(ComicAssets.catalog).also { state.compareAndSet(null, it) }

    fun set(index: ThemeIndex?) { state.value = index }
}

class ThemePackRepository(
    private val dao: ThemePackDao,
    private val now: () -> Long = System::currentTimeMillis,
    /** Son yazılan kataloğun imzası (değişmediyse açılışta yeniden yazılmaz; null: her seferinde yazılır). */
    private val prefs: android.content.SharedPreferences? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val namesSerializer = MapSerializer(String.serializer(), String.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    /** Kataloğun imzası: paketler, varlıklar ve temaları (değişince yeniden yazılır). */
    fun signature(c: ComicCatalog): String {
        var h = 1125899906842597L
        fun mix(v: String) { v.forEach { h = 31 * h + it.code } ; h = 31 * h + 7 }
        c.themes.forEach { mix(it.id); it.names.toSortedMap().forEach { (k, v) -> mix(k); mix(v) } }
        c.characters.forEach { mix(it.id); it.packs.forEach(::mix) }
        c.backgrounds.forEach { mix(it.id); it.themes.forEach(::mix) }
        c.effects.forEach { mix(it.id); it.themes.forEach(::mix) }
        c.objects.forEach { mix(it.id); it.themes.forEach(::mix) }
        return "${c.version}-${java.lang.Long.toHexString(h)}"
    }

    /** Katalog değiştiyse (ya da tablolar boşsa) yazar. Döner: yazıldı mı. */
    suspend fun seedIfChanged(catalog: ComicCatalog = ComicAssets.catalog): Boolean {
        if (catalog.themes.isEmpty()) return false
        val sig = signature(catalog)
        if (prefs?.getString(KEY_SIGNATURE, null) == sig && dao.linkCount() > 0) return false
        seed(catalog)
        prefs?.edit()?.putString(KEY_SIGNATURE, sig)?.apply()
        return true
    }

    /** Katalogdaki paket, varlık ve bağlantıları veritabanına yazar. Döner: yazılan bağlantı sayısı. */
    suspend fun seed(catalog: ComicCatalog = ComicAssets.catalog): Int {
        val t = now()
        val packs = catalog.themes.mapIndexed { i, th ->
            ThemePackEntity(th.id, json.encodeToString(namesSerializer, th.names), i, SOURCE_BUILTIN, t)
        }
        val known = packs.mapTo(HashSet()) { it.id }
        val assets = ArrayList<ComicAssetEntity>()
        val links = ArrayList<ThemePackAssetEntity>()
        fun add(kind: ComicAssetKind, id: String, styles: List<String>, category: String?, file: String, themes: List<String>) {
            val key = ThemeIndex.key(kind, id)
            assets += ComicAssetEntity(key, kind.id, id, styles.joinToString(","), category, file, SOURCE_BUILTIN)
            themes.distinct().filter { it in known }.forEach { links += ThemePackAssetEntity(it, key, SOURCE_BUILTIN) }
        }
        catalog.characters.forEach { add(ComicAssetKind.CHARACTER, it.id, listOf(it.style), null, it.cover.file, it.packs) }
        catalog.backgrounds.forEach { add(ComicAssetKind.BACKGROUND, it.id, listOf(it.style), it.category, it.file, it.themes) }
        catalog.effects.forEach { add(ComicAssetKind.EFFECT, it.id, it.styles, it.category, it.file, it.themes) }
        catalog.objects.forEach { add(ComicAssetKind.OBJECT, it.id, it.styles, it.category, it.file, it.themes) }
        dao.replaceBuiltin(packs, assets, links)
        return links.size
    }

    /** Veritabanından okunan dizin (paket adları JSON'dan). */
    suspend fun load(): ThemeIndex = build(dao.packs(), dao.links())

    private fun build(packs: List<ThemePackEntity>, links: List<ThemePackAssetEntity>): ThemeIndex {
        val themes = packs.map { p ->
            ComicTheme(p.id, runCatching { json.decodeFromString(namesSerializer, p.namesJson) }.getOrDefault(emptyMap()))
        }
        val map = HashMap<String, MutableSet<String>>()
        links.forEach { map.getOrPut(it.assetId) { LinkedHashSet() } += it.packId }
        return ThemeIndex(themes, map)
    }

    /** Veritabanındaki değişiklikleri [ComicThemeIndex]'e yansıtır (açılışta bir kez). */
    fun start() {
        scope.launch {
            combine(dao.observePacks(), dao.observeLinks()) { p, l -> build(p, l) }.collect { idx ->
                if (idx.packs.isNotEmpty()) ComicThemeIndex.set(idx)
            }
        }
    }

    /** Varlığı bir pakete bağlar (elle; katalog yeniden yazılırken korunur). Varlık ve paket veritabanında olmalı. */
    suspend fun link(packId: String, kind: ComicAssetKind, id: String) =
        dao.upsertLink(ThemePackAssetEntity(packId, ThemeIndex.key(kind, id), SOURCE_USER))

    suspend fun unlink(packId: String, kind: ComicAssetKind, id: String): Boolean = dao.unlink(packId, ThemeIndex.key(kind, id)) > 0

    /** Yeni (kullanıcı) paketi. */
    suspend fun addPack(id: String, names: Map<String, String>, sortOrder: Int = 1000) =
        dao.upsertPacks(listOf(ThemePackEntity(id, json.encodeToString(namesSerializer, names), sortOrder, SOURCE_USER, now())))

    suspend fun packAssets(packId: String, kind: ComicAssetKind): List<String> = dao.assetsOf(packId, kind.id).map { it.refId }

    companion object {
        const val SOURCE_BUILTIN = "builtin"
        const val SOURCE_USER = "user"
        const val KEY_SIGNATURE = "catalog_signature"
    }
}
