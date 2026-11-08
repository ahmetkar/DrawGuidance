package com.karapp.drawguidance.data.templates

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.UUID

/*
 * v46 hazır tuval şablonları (örnek çalışmalar): çizgi roman ve not alma için uygulamanın kendi elemanlarıyla
 * (panel şablonları, mekânlar, karakterler, balonlar, efekt yazıları, hız çizgileri, not alanları, yazılar, formüller,
 * grafikler, diyagramlar ve bilim şekilleri) hazırlanmış belgeler. Yeni tuval oluşturulurken kapak resmi ve adıyla
 * listelenir; seçilen şablon kullanıcının yeni tuvaline kopyalanır ve oradan devam edilir.
 *
 * Şablonlar uygulamanın içindedir (assets/templates): `catalog.json` listesi ve her şablonun klasöründe
 * `meta.json` (boyut, kâğıt, arka plan, açılan sayfa, resimler), `content.bin` (belgenin [ContentCodec] baytları),
 * `cover.webp` (kapak resmi) ve resim dosyaları. Şablonlar V46TemplateExport ile düzenleyicinin kendisi sürülerek üretilir.
 */

/** Katalogdaki şablon: [kind] tuval türü, [names]/[hints] dillere göre adı ve kısa açıklaması. */
@Serializable
data class TemplateEntry(
    val id: String,
    val kind: String,
    val names: Map<String, String>,
    val hints: Map<String, String> = emptyMap(),
    val pages: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
) {
    val canvasKind: CanvasKind? get() = runCatching { CanvasKind.valueOf(kind) }.getOrNull()
}

@Serializable
data class TemplateCatalog(val version: Int = 1, val templates: List<TemplateEntry> = emptyList())

/** Şablonun resmi: belgedeki kimliği, klasördeki dosyası, türü ve boyutu. */
@Serializable
data class TemplateAsset(val id: String, val file: String, val mime: String = "image/webp", val w: Int = 0, val h: Int = 0)

/** Şablon belgesinin ayarları. [titleId] çizgi roman kapağının başlık elemanı (tuvalin adı olur). */
@Serializable
data class TemplateMeta(
    val width: Int,
    val height: Int,
    val backgroundColorHex: String = "#FFFFFF",
    /** Tuvalin arka plan kaydı (CanvasEntity.background biçiminde). */
    val background: String = "",
    val guide: String = "",
    val currentPage: Int = 0,
    val titleId: String? = null,
    val assets: List<TemplateAsset> = emptyList(),
)

/** Kurulmaya hazır şablon: yeni kimlikli resimler ve onlara göre güncellenmiş belge. */
class PreparedTemplate(
    val meta: TemplateMeta,
    val content: ByteArray,
    val background: String,
    val guide: String,
    /** Yeni kimlik → (baytlar, şablondaki resim kaydı). */
    val assets: List<Pair<String, Pair<ByteArray, TemplateAsset>>>,
)

object CanvasTemplates {
    const val DIR = "templates"
    const val CATALOG = "$DIR/catalog.json"

    /** Şablonu olan tuval türleri (gelişmiş çizgi roman ve not alma). */
    /** v47 basit not almada da şablonlar. */
    val KINDS = setOf(CanvasKind.COMIC, CanvasKind.NOTES, CanvasKind.SIMPLE_NOTES)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Varlık açıcı (uygulamada bağlamın AssetManager'ı; testlerde değiştirilebilir). */
    @Volatile var opener: (String) -> InputStream = { path ->
        val ctx = org.koin.core.context.GlobalContext.get().get<android.content.Context>()
        ctx.assets.open(path)
    }

    @Volatile private var loaded: TemplateCatalog? = null

    val catalog: TemplateCatalog
        get() = loaded ?: synchronized(this) {
            loaded ?: (runCatching { opener(CATALOG).use { parseCatalog(it.readBytes().decodeToString()) } }
                .onFailure { com.karapp.drawguidance.diagnostics.AppLog.w("Templates", "Şablon kataloğu okunamadı", it) }
                .getOrNull() ?: TemplateCatalog()).also { loaded = it }
        }

    /** Testler için: kataloğu yeniden okutur. */
    fun reset() { loaded = null }

    fun parseCatalog(text: String): TemplateCatalog = json.decodeFromString(TemplateCatalog.serializer(), text)
    fun parseMeta(text: String): TemplateMeta = json.decodeFromString(TemplateMeta.serializer(), text)
    fun encodeCatalog(c: TemplateCatalog): String = Json { prettyPrint = true; encodeDefaults = false }.encodeToString(TemplateCatalog.serializer(), c)
    fun encodeMeta(m: TemplateMeta): String = Json { prettyPrint = true; encodeDefaults = false }.encodeToString(TemplateMeta.serializer(), m)

    /** [kind] türünün şablonları (katalog sırasıyla). */
    fun forKind(kind: CanvasKind): List<TemplateEntry> = catalog.templates.filter { it.kind == kind.name }

    fun find(id: String): TemplateEntry? = catalog.templates.firstOrNull { it.id == id }

    fun coverPath(id: String) = "$DIR/$id/cover.webp"
    fun metaPath(id: String) = "$DIR/$id/meta.json"
    fun contentPath(id: String) = "$DIR/$id/content.bin"
    fun assetPath(id: String, file: String) = "$DIR/$id/$file"

    fun name(entry: TemplateEntry, lang: String = com.karapp.drawguidance.i18n.I18n.language): String =
        entry.names[lang] ?: entry.names["en"] ?: entry.names["tr"] ?: entry.id

    fun hint(entry: TemplateEntry, lang: String = com.karapp.drawguidance.i18n.I18n.language): String =
        entry.hints[lang] ?: entry.hints["en"] ?: entry.hints["tr"].orEmpty()

    /** Kapak resminin baytları (yoksa null). */
    fun coverBytes(id: String): ByteArray? = runCatching { opener(coverPath(id)).use { it.readBytes() } }.getOrNull()

    /**
     * Şablonu yeni tuvale hazırlar: dosyaları okur, her resme yeni kimlik verir (tuvaller arası kimlik çakışmasın),
     * belgedeki ve arka plandaki resim kimliklerini yenileriyle değiştirir ve çizgi roman kapağının başlığını
     * [canvasName] yapar. Okunamazsa null.
     */
    fun prepare(id: String, canvasName: String, newId: () -> String = { UUID.randomUUID().toString() }): PreparedTemplate? = runCatching {
        val meta = opener(metaPath(id)).use { parseMeta(it.readBytes().decodeToString()) }
        val content = opener(contentPath(id)).use { it.readBytes() }
        val files = meta.assets.associate { a -> a.id to opener(assetPath(id, a.file)).use { it.readBytes() } }
        prepare(meta, content, files, canvasName, newId)
    }.onFailure { com.karapp.drawguidance.diagnostics.AppLog.e("Templates", "Şablon okunamadı: $id", it) }.getOrNull()

    /** [prepare]'in saf kısmı (dosyalar okunmuş). */
    fun prepare(
        meta: TemplateMeta, content: ByteArray, files: Map<String, ByteArray>, canvasName: String,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): PreparedTemplate {
        val ids = meta.assets.associate { it.id to newId() }
        val doc = ContentCodec.decodeDocument(content)
        val pages = doc.pages.mapIndexed { p, page ->
            page.map { s -> remap(s, ids).let { if (p == 0 && meta.titleId != null && it.id == meta.titleId) retitle(it, meta.width, canvasName) else it } }
        }
        var background = meta.background
        var guide = meta.guide
        for ((old, new) in ids) {
            background = background.replace(old, new)
            guide = guide.replace(old, new)
        }
        val assets = meta.assets.map { a -> ids.getValue(a.id) to ((files[a.id] ?: ByteArray(0)) to a) }
        return PreparedTemplate(meta, ContentCodec.encodePages(pages, layers = doc.layers), background, guide, assets)
    }

    private fun remap(s: Stroke, ids: Map<String, String>): Stroke {
        val a = s.assetId ?: return s
        return ids[a]?.let { s.copy(assetId = it) } ?: s
    }

    private fun retitle(s: Stroke, width: Int, name: String): Stroke =
        com.karapp.drawguidance.ui.screens.canvasedit.state.ComicCover.retitle(s, width, name)
}
