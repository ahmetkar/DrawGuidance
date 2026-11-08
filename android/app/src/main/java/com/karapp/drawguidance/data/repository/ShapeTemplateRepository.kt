package com.karapp.drawguidance.data.repository

import com.karapp.drawguidance.content.shapes.IndexEntry
import com.karapp.drawguidance.content.shapes.ShapeIndex
import com.karapp.drawguidance.content.shapes.ShapeSignature
import com.karapp.drawguidance.content.shapes.ShapeSignatures
import com.karapp.drawguidance.content.shapes.ShapeTemplates
import com.karapp.drawguidance.content.shapes.TemplateMeta
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.local.ShapeTemplateDao
import com.karapp.drawguidance.data.local.ShapeTemplateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Öneride kullanılan şekil kaynağı (dizin ve öğrenme geri bildirimi). */
interface TemplateSource {
    /** Hazırsa arama dizini (yüklenmediyse null: o öneride şablon aranmaz, yükleme başlar). */
    val index: ShapeIndex?

    /** v29 aramanın kapsamı: açık tuvalin modunun şablonları, sinir ağı ve sayaçları (null: genel arama). */
    val searchScope: com.karapp.drawguidance.content.shapes.ShapeScope? get() = null

    /** v34 açık tuvalin türü (basit türlerde daha çok ve türün kataloğundan öneri). */
    val canvasKind: com.karapp.drawguidance.data.model.CanvasKind? get() = null

    /** Öneri kabul edildi: kullanım sayacı artar, kullanıcının çizimi o şablonun örneği olarak öğrenilir. */
    fun accepted(key: String, drawn: List<List<P>>)

    /**
     * v29 parça önerisi kabul edildi: [drawn] seçili (eksik) parçalar. Veritabanına örnek olarak yazılmaz (bütün şeklin
     * örneği değil); yalnız modun sinir ağı bu eksik çizimden öğrenir.
     */
    fun acceptedPart(key: String, drawn: List<List<P>>) = accepted(key, emptyList())

    /** Gösterilen öneriler kapatıldı: ret sayaçları artar. v29 [drawn] gösterildiği çizim (modun ağı öğrenir). */
    fun rejected(keys: List<String>, drawn: List<List<P>> = emptyList())
}

/**
 * v21 şekil veritabanının deposu. Açılışta (arka planda) katalog sürümü kontrol edilir; eskiyse katalog kayıtları
 * imzalarıyla yeniden yazılır (kullanım/ret sayaçları ve kullanıcı örnekleri korunur), sonra tüm kayıtlar bir kez
 * belleğe alınıp arama dizini kurulur. Öğrenme (kabul/ret) önce veritabanına yazılır, dizin yenilenir.
 */
class ShapeTemplateRepository(
    private val dao: ShapeTemplateDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** v33 sunucunun (daha yeni) kataloğu uygulandı mı: uygulandıysa açılışta yerleşik katalog yeniden yazılmaz. */
    private val remoteCatalogActive: () -> Boolean = { false },
) : TemplateSource {

    private val _index = MutableStateFlow<ShapeIndex?>(null)
    val indexFlow: StateFlow<ShapeIndex?> = _index.asStateFlow()
    override val index: ShapeIndex? get() = _index.value.also { if (it == null) warmUp() }

    private val mutex = Mutex()
    @Volatile private var started = false

    /** Dizini arka planda hazırlar (bir kez). */
    fun warmUp() {
        if (started) return
        started = true
        scope.launch {
            runCatching { load() }.onFailure { e ->
                com.karapp.drawguidance.diagnostics.AppLog.e(TAG, "şekil veritabanı yüklenemedi, katalogdan kuruluyor", e)
                _index.value = ShapeIndex.fromCatalog(ShapeTemplates.catalog)
            }
        }
    }

    /** Dizin (gerekirse veritabanını doldurup yükler). */
    suspend fun load(): ShapeIndex = mutex.withLock {
        _index.value?.let { return it }
        val catalog = ShapeTemplates.catalog
        val remote = remoteCatalogActive() && dao.catalogCount(ShapeTemplates.VERSION) > 0
        if (!remote && dao.catalogCount(ShapeTemplates.VERSION) != catalog.size) seed()
        val idx = build(dao.all())
        _index.value = idx
        // v27 gelişmiş tanımanın şablon tarafı arka planda hazırlanır (ilk öneride takılma olmasın)
        scope.launch { runCatching { idx.warmAdvanced() } }
        idx
    }

    /** Katalog kayıtlarını (imzalarıyla) yazar; sayaçlar anahtara göre korunur. */
    private suspend fun seed() {
        val counts = dao.catalogCounts().associateBy { it.key }
        val rows = ShapeTemplates.catalog.map { t ->
            val sig = ShapeSignatures.of(t.strokes, augment = true)
            val c = counts[t.key]
            entity(t.key, t.key, t.name, t.category, t.group, t.description, t.solid, sig, c?.usage ?: 0, c?.rejects ?: 0, ShapeTemplateEntity.SOURCE_CATALOG)
        }
        dao.replaceCatalog(rows)
    }

    /**
     * v33 sunucunun kataloğu (yerleşikten yeni): katalog kayıtları sunucudakilerle değişir (aynı anahtarın kabul/ret
     * sayaçları ve kullanıcının öğrettiği örnekler korunur), dizin yeniden kurulur.
     */
    suspend fun applyRemote(rows: List<com.karapp.drawguidance.content.catalog.CatalogTemplate>) = mutex.withLock {
        if (rows.isEmpty()) return@withLock
        val counts = dao.catalogCounts().associateBy { it.key }
        val entities = rows.map { t ->
            val c = counts[t.key]
            ShapeTemplateEntity(
                key = t.key, templateKey = t.templateKey, name = t.name, category = t.category, group = t.group,
                description = t.description, solid = t.solid, strokeCount = t.strokeCount, closedMask = t.closedMask,
                aspectBucket = t.aspectBucket, cornerCount = t.cornerCount, geometry = t.geometry, descriptor = t.descriptor,
                lshKey = t.lshKey, usage = c?.usage ?: 0, rejects = c?.rejects ?: 0, source = ShapeTemplateEntity.SOURCE_CATALOG,
                catalogVersion = ShapeTemplates.VERSION,
            )
        }
        dao.replaceCatalog(entities)
        val idx = build(dao.all())
        _index.value = idx
        scope.launch { runCatching { idx.warmAdvanced() } }
    }

    private fun entity(
        key: String, templateKey: String, name: String, category: String, group: String, description: String, solid: String?,
        sig: ShapeSignature, usage: Int, rejects: Int, source: String,
    ) = ShapeTemplateEntity(
        key = key, templateKey = templateKey, name = name, category = category, group = group, description = description,
        solid = solid, strokeCount = sig.strokeCount, closedMask = sig.closedMask, aspectBucket = sig.aspectBucket,
        cornerCount = sig.corners, geometry = ShapeSignatures.encodeGeometry(sig.geometry),
        descriptor = ShapeSignatures.encodeDescriptors(sig.descriptors), lshKey = sig.lsh, usage = usage, rejects = rejects,
        source = source, catalogVersion = ShapeTemplates.VERSION,
    )

    private fun build(rows: List<ShapeTemplateEntity>): ShapeIndex {
        val catalog = rows.filter { it.source == ShapeTemplateEntity.SOURCE_CATALOG }
        val metas = catalog.map { TemplateMeta(it.templateKey, it.name, it.category, it.group, it.description, it.solid, it.usage, it.rejects) }
        val entries = rows.map { r ->
            val sig = ShapeSignature(
                strokeCount = r.strokeCount, closedMask = r.closedMask, aspect = (r.aspectBucket + 0.5f) / 8f, corners = r.cornerCount,
                descriptors = ShapeSignatures.decodeDescriptors(r.descriptor), lsh = r.lshKey,
                geometry = ShapeSignatures.decodeGeometry(r.geometry),
            )
            IndexEntry(r.id, r.templateKey, sig.withExactAspect(), user = r.source == ShapeTemplateEntity.SOURCE_USER)
        }
        return ShapeIndex(metas, entries)
    }

    /** Veritabanında en-boy kovası saklanır; dizinde kesin değer geometriden yeniden hesaplanır (ucuz). */
    private fun ShapeSignature.withExactAspect(): ShapeSignature = ShapeSignature(
        strokeCount, closedMask, ShapeSignatures.aspectOf(geometry), corners, descriptors, lsh, geometry,
    )

    override fun accepted(key: String, drawn: List<List<P>>) {
        _index.value?.recordFeedback(key, accepted = true)
        scope.launch {
            runCatching {
                dao.addUsage(key)
                if (drawn.isNotEmpty() && drawn.any { it.size >= 2 }) {
                    val sig = ShapeSignatures.of(drawn, augment = false)
                    val current = _index.value ?: return@runCatching
                    val meta = current.meta(key) ?: return@runCatching
                    val trimmed = dao.userCount(key) >= MAX_USER_PER_TEMPLATE
                    if (trimmed) dao.deleteOldestUser(key)
                    val rowId = dao.insert(
                        entity(
                            "$key#u${System.currentTimeMillis()}", key, meta.name, meta.category, meta.group, meta.description, meta.solid,
                            sig, 0, 0, ShapeTemplateEntity.SOURCE_USER,
                        ),
                    )
                    // v37 yalnız örnek eklendiyse dizin yeniden kurulmaz: yeni örnek eklenir, hazır ölçüt önbellekleri
                    // korunur (eski örnek silindiyse dizin veritabanından kurulur)
                    mutex.withLock {
                        val now = _index.value
                        _index.value = if (trimmed || now == null) build(dao.all())
                        else now.plus(IndexEntry(rowId, key, sig.withExactAspect(), user = true))
                    }
                }
            }.onFailure { com.karapp.drawguidance.diagnostics.AppLog.e(TAG, "öğrenme kaydedilemedi", it) }
        }
    }

    override fun rejected(keys: List<String>, drawn: List<List<P>>) {
        if (keys.isEmpty()) return
        _index.value?.let { idx -> keys.forEach { idx.recordFeedback(it, accepted = false) } }
        scope.launch { runCatching { dao.addRejects(keys) } }
    }

    companion object {
        private const val TAG = "ShapeTemplates"

        /** Şablon başına saklanan en çok kullanıcı örneği (en eskisi silinir). */
        const val MAX_USER_PER_TEMPLATE = 4
    }
}
