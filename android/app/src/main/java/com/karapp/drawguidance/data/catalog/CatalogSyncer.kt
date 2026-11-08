package com.karapp.drawguidance.data.catalog

import android.content.Context
import android.util.Log
import com.karapp.drawguidance.content.catalog.CatalogBinary
import com.karapp.drawguidance.content.catalog.CatalogCodec
import com.karapp.drawguidance.content.catalog.CatalogInfo
import com.karapp.drawguidance.content.catalog.CatalogModeNet
import com.karapp.drawguidance.content.catalog.CatalogVector
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.shapes.ShapeModeModels
import com.karapp.drawguidance.data.remote.v1.CatalogBinaryInfo
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.repository.ShapeTemplateRepository
import java.io.File

/**
 * v33 sunucudan gelen kataloğun cihazdaki kaydı: uygulanan sürüm, manifestin ETag'i, son denetim zamanı; vektörler
 * ve temel ağlar dosyada (`files/catalog/`), şablonlar şekil veritabanında.
 */
class CatalogStore(context: Context) {
    private val prefs = context.getSharedPreferences("catalog_sync", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "catalog")

    /** Uygulanan katalog sürümü (sunucudan bir şey uygulanmadıysa uygulamayla gelen). */
    var appliedVersion: Long
        get() = maxOf(prefs.getLong("applied", 0L), CatalogInfo.VERSION)
        set(v) { prefs.edit().putLong("applied", v).apply() }

    /** Sunucunun kataloğu uygulanmış ve uygulamayla gelenden yeni mi. */
    val remoteActive: Boolean get() = prefs.getLong("applied", 0L) > CatalogInfo.VERSION

    var etag: String?
        get() = prefs.getString("etag", null)
        set(v) { prefs.edit().putString("etag", v).apply() }

    var lastCheck: Long
        get() = prefs.getLong("lastCheck", 0L)
        set(v) { prefs.edit().putLong("lastCheck", v).apply() }

    /** Vektörlerin sunucudan gelen gövdesi (v37 ikili ya da JSON; veritabanı yoksa). */
    fun saveVectors(body: ByteArray) {
        dir.mkdirs()
        val binary = CatalogBinary.isBinary(body)
        File(dir, if (binary) VECTORS_BIN else VECTORS_JSON).writeBytes(body)
        File(dir, if (binary) VECTORS_JSON else VECTORS_BIN).delete()
    }

    /** Kayıtlı vektörler (ikili ya da v35'ten kalan JSON dosyası). */
    fun vectors(): List<CatalogVector>? {
        File(dir, VECTORS_BIN).takeIf { it.exists() }?.let { return CatalogBinary.decodeVectors(it.readBytes()).items }
        return File(dir, VECTORS_JSON).takeIf { it.exists() }?.readText()?.let { text -> CatalogCodec.items(text).second.mapNotNull { CatalogCodec.vector(it) } }
    }

    /** Ağ dosyaya (veritabanı yoksa; v37'den önce bütün ağlar böyle saklanıyordu). */
    fun saveNet(net: CatalogModeNet) {
        dir.mkdirs()
        File(dir, "modenet_${net.mode.lowercase()}.bin").writeBytes(net.weights)
        prefs.edit().putInt("net_${net.mode}", net.version).putString("net_format_${net.mode}", net.format).apply()
    }

    /** Dosyadaki ağlar. */
    fun nets(): List<CatalogModeNet> = dir.listFiles { f -> f.name.startsWith("modenet_") && f.name.endsWith(".bin") }.orEmpty().mapNotNull { f ->
        val mode = f.name.removePrefix("modenet_").removeSuffix(".bin").uppercase()
        val v = prefs.getInt("net_$mode", -1)
        if (v < 0) null else CatalogModeNet(mode, v, prefs.getString("net_format_$mode", null) ?: CatalogInfo.MODE_NET_FORMAT, f.readBytes())
    }

    /** v37 ağ dosyaları veritabanına taşındıktan sonra silinir. */
    fun clearNets() {
        dir.listFiles { f -> f.name.startsWith("modenet_") }?.forEach { it.delete() }
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("net_") }.forEach { e.remove(it) }
        e.apply()
    }

    private companion object {
        const val VECTORS_JSON = "vectors.json"
        const val VECTORS_BIN = "vectors.bin"
    }
}

/**
 * v33 günlük katalog eşitlemesi (olay tablosundaki `CATALOG_SYNC` işi): sunucunun manifesti ETag ile sorulur (değişmediyse
 * gövde inmez); sunucunun sürümü cihazdakinden yeniyse şablonlar, vektör şekiller ve temel ağlar indirilip uygulanır
 * (**sunucudakiler önceliklidir**). Kişisel öğrenme cihazda kalır; temel ağ değişince cihazda öğrenilenler yeni ağın
 * üstüne yeniden çalışılır.
 */
class CatalogSyncer(
    private val remote: RemoteStore,
    private val store: CatalogStore,
    private val templates: ShapeTemplateRepository,
    private val models: ShapeModeModels?,
    /** v36 vektör şekillerin veritabanı kaydı (yoksa eski dosya kaydı kullanılır). */
    private val vectorsDb: CatalogVectorRepository? = null,
    /** v37 sinir ağlarının veritabanı kaydı (yoksa dosya kaydı kullanılır). */
    private val netsDb: CatalogNetRepository? = null,
    /**
     * v40 sunucunun içerik eşitleme izni: false dönerse bu sefer indirilmez (izin veren kendisi eşitlemeyi sunucunun
     * dilimine erteler). null: izin sorulmaz (eski davranış, testler).
     */
    private val permit: (suspend () -> Boolean)? = null,
) {
    /** Son günden beri denetlenmediyse true (açılışta eşitleme işi eklenir). */
    fun due(now: Long = System.currentTimeMillis()): Boolean = now - store.lastCheck >= DAY_MS

    /** Sunucuyla eşitler; bir şey uygulandıysa true. Ağ/sunucu hatasında istisna (iş sonra yeniden denenir). */
    suspend fun sync(now: Long = System.currentTimeMillis()): Boolean {
        val manifest = remote.catalogManifest(store.etag)
        if (manifest == null) { store.lastCheck = now; return false }
        val (m, etag) = manifest
        if (m.version <= store.appliedVersion) {
            store.etag = etag
            store.lastCheck = now
            return false
        }
        // v40 yeni içerik yalnız sunucunun verdiği dilimde iner (manifest küçük ve ETag'li: her zaman sorulur).
        // İzin yoksa ETag kaydedilmez: dilimde manifest yeniden okunur
        if (permit != null && !permit.invoke()) return false
        // Şablonlar (yapay zekâ eşleştirme tablosu)
        val tpl = CatalogCodec.items(remote.catalogTemplates()).second.mapNotNull { CatalogCodec.template(it) }
        // Vektör şekiller ve temel ağlar: v37 sunucu destekliyorsa ikili biçimde (JSON'un ~dörtte biri; yollar
        // cihaz veritabanındaki biçimde gelir, yeniden kodlanmaz)
        val vectorsBody = remote.catalogVectorsBytes(m.binary(CatalogBinaryInfo.VECTORS))
        val (vectors, packed) = decodeVectors(vectorsBody)
        val nets = decodeModeNets(remote.catalogModeNetsBytes(m.binary(CatalogBinaryInfo.MODE_NETS)))

        templates.applyRemote(tpl)
        // v36 sunucudan gelen şekiller cihaz veritabanına yazılır (veritabanı yoksa dosyaya)
        if (vectorsDb != null) vectorsDb.saveRemote(vectors, m.version, packed) else store.saveVectors(vectorsBody)
        ShapeLibrary.install(vectors)
        // v37 şekil düzeltme ağları ve not alma modunun yazı ağı (NOTES_TEXT); tanınmayan ağ yok sayılır
        var shapeNetChanged = false
        nets.forEach { n ->
            if (CatalogCodec.installNet(n) != true) return@forEach
            if (netsDb != null) netsDb.saveRemote(n, m.version) else store.saveNet(n)
            if (n.mode != CatalogInfo.TEXT_NET_MODE) shapeNetChanged = true
        }
        if (shapeNetChanged) models?.invalidate()
        store.appliedVersion = m.version
        store.etag = etag
        store.lastCheck = now
        Log.i(TAG, "katalog ${m.version} uygulandı: ${tpl.size} şablon, ${vectors.size} vektör, ${nets.size} ağ")
        return true
    }

    /** Açılışta: daha önce indirilmiş (yerleşikten yeni) vektörler ve ağlar yüklenir. */
    fun installSaved() {
        if (!store.remoteActive) return
        runCatching {
            // v36 önce veritabanı; v35'ten kalan dosya kaydı varsa o (bir kez veritabanına taşınır)
            val db = vectorsDb?.let { r -> kotlinx.coroutines.runBlocking { r.remoteVectors() } }.orEmpty()
            val saved = db.ifEmpty {
                store.vectors().orEmpty().also { old ->
                    if (old.isNotEmpty()) vectorsDb?.let { r -> kotlinx.coroutines.runBlocking { r.saveRemote(old, store.appliedVersion) } }
                }
            }
            if (saved.isNotEmpty()) ShapeLibrary.install(saved)
            // v37 ağlar veritabanından; eski dosya kaydı varsa bir kez veritabanına taşınır
            val nets = netsDb?.let { r ->
                kotlinx.coroutines.runBlocking {
                    store.nets().takeIf { it.isNotEmpty() }?.let { old -> old.forEach { r.saveRemote(it, store.appliedVersion) }; store.clearNets() }
                    r.remoteNets()
                }
            } ?: store.nets()
            nets.forEach { CatalogCodec.installNet(it) }
        }.onFailure { com.karapp.drawguidance.diagnostics.AppLog.e(TAG, "kayıtlı katalog yüklenemedi", it) }
    }

    companion object {
        private const val TAG = "CatalogSyncer"
        const val DAY_MS = 24 * 3_600_000L

        /** v37 vektör gövdesi (ikili ya da JSON) ve ikilideyse paketlenmiş yolları. */
        internal fun decodeVectors(body: ByteArray): Pair<List<CatalogVector>, List<ByteArray>?> =
            if (CatalogBinary.isBinary(body)) {
                val entries = CatalogBinary.decodeVectorEntries(body).items
                entries.map { it.vector } to entries.map { it.packedPaths }
            } else {
                CatalogCodec.items(body.toString(Charsets.UTF_8)).second.mapNotNull { CatalogCodec.vector(it) } to null
            }

        internal fun decodeModeNets(body: ByteArray): List<CatalogModeNet> =
            if (CatalogBinary.isBinary(body)) CatalogBinary.decodeModeNets(body).items
            else CatalogCodec.items(body.toString(Charsets.UTF_8)).second.mapNotNull { CatalogCodec.modeNet(it) }
    }
}
