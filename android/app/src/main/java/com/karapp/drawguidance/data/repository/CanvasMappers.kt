package com.karapp.drawguidance.data.repository

import com.karapp.drawguidance.data.local.CanvasEntity
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Stroke
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json



fun CanvasEntity.toUi(decoded: ContentCodec.Document? = null): CanvasUi {
    val doc = decoded ?: ContentCodec.decodeDocument(content)
    val pages = doc.pages.ifEmpty { listOf(emptyList()) }
    return CanvasUi(
        localId = localId,
        name = name,
        width = width,
        height = height,
        backgroundColorHex = backgroundColorHex,
        aiEnabled = aiEnabled,
        pages = pages,
        createdAt = createdAt,
        updatedAt = updatedAt,
        kind = com.karapp.drawguidance.data.model.CanvasKind.parse(kind),
        // Kayıtlı sayfa numarası sayfa sayısının dışındaysa (yarım kalmış kayıt) son sayfaya çekilir
        currentPage = currentPage.coerceIn(0, pages.size - 1),
        // v27 not tuvallerinde perspektif yok (yalnız kılavuz çizgileri); eski kayıtlarda kalan perspektif gösterilmez
        guide = decodeGuide(guide).let {
            if (it.perspective != 0 && com.karapp.drawguidance.data.model.CanvasKind.parse(kind).isNotes) it.copy(perspective = 0) else it
        },
        layers = com.karapp.drawguidance.data.model.Layers.normalize(doc.layers),
        folderId = folderId,
        sizeBytes = content.size.toLong(),
        background = decodeBackground(background),
        pageBackgrounds = decodePageBackgrounds(background, pages.size),
        pageSettings = decodePageSettings(background),
    )
}

private val backgroundJson = kotlinx.serialization.json.Json { encodeDefaults = false; ignoreUnknownKeys = true }

/** v23 arka plan resmi/deseni (boş ya da bozuksa yalnızca renk). */
fun decodeBackground(value: String): com.karapp.drawguidance.data.model.BackgroundSpec =
    if (value.isBlank()) com.karapp.drawguidance.data.model.BackgroundSpec()
    else runCatching { backgroundJson.decodeFromString(com.karapp.drawguidance.data.model.BackgroundSpec.serializer(), value) }
        .getOrDefault(com.karapp.drawguidance.data.model.BackgroundSpec())

fun encodeBackground(spec: com.karapp.drawguidance.data.model.BackgroundSpec): String =
    if (spec.isEmpty) "" else backgroundJson.encodeToString(com.karapp.drawguidance.data.model.BackgroundSpec.serializer(), spec)

/**
 * v24 ortak arka plan + sayfaların kendi arka planları: ortak arka planın JSON'una "pages" dizisi eklenir (sayfası
 * olmayanlarda null). Eski sürümler "pages"ı yok sayar (ortak arka planı görür).
 */
fun encodeBackground(
    spec: com.karapp.drawguidance.data.model.BackgroundSpec,
    pages: List<com.karapp.drawguidance.data.model.PageBackground?>,
): String {
    val trimmed = pages.dropLastWhile { it == null || it.isEmpty }.map { it?.takeUnless { p -> p.isEmpty } }
    if (trimmed.isEmpty()) return encodeBackground(spec)
    val base = backgroundJson.encodeToJsonElement(com.karapp.drawguidance.data.model.BackgroundSpec.serializer(), spec) as kotlinx.serialization.json.JsonObject
    val list = kotlinx.serialization.json.JsonArray(
        trimmed.map { p ->
            if (p == null) kotlinx.serialization.json.JsonNull
            else backgroundJson.encodeToJsonElement(com.karapp.drawguidance.data.model.PageBackground.serializer(), p)
        },
    )
    return kotlinx.serialization.json.JsonObject(base + (PAGES_KEY to list)).toString()
}

/** v24 sayfaların kendi arka planları ([count] sayfa için; kayıt yoksa boş). */
fun decodePageBackgrounds(value: String, count: Int): List<com.karapp.drawguidance.data.model.PageBackground?> {
    if (value.isBlank() || !value.contains(PAGES_KEY)) return emptyList()
    return runCatching {
        val obj = backgroundJson.parseToJsonElement(value) as kotlinx.serialization.json.JsonObject
        val arr = obj[PAGES_KEY] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        arr.take(count.coerceAtLeast(0)).map { e ->
            if (e is kotlinx.serialization.json.JsonNull) null
            else backgroundJson.decodeFromJsonElement(com.karapp.drawguidance.data.model.PageBackground.serializer(), e).takeUnless { it.isEmpty }
        }
    }.getOrDefault(emptyList())
}

private const val PAGES_KEY = "pages"

/** v40 sayfa ayarlarının arka plan JSON'undaki anahtarı. */
private const val PAGE_SETTINGS_KEY = "pageSettings"

/** v40 sayfa numarası / üst bilgi / alt bilgi (kayıt yoksa ya da bozuksa boş ayarlar). */
fun decodePageSettings(value: String): com.karapp.drawguidance.data.model.PageSettings {
    if (value.isBlank() || !value.contains(PAGE_SETTINGS_KEY)) return com.karapp.drawguidance.data.model.PageSettings()
    return runCatching {
        val obj = backgroundJson.parseToJsonElement(value) as kotlinx.serialization.json.JsonObject
        val e = obj[PAGE_SETTINGS_KEY] ?: return com.karapp.drawguidance.data.model.PageSettings()
        backgroundJson.decodeFromJsonElement(com.karapp.drawguidance.data.model.PageSettings.serializer(), e)
    }.getOrDefault(com.karapp.drawguidance.data.model.PageSettings())
}

/**
 * v40 arka plan JSON'una sayfa ayarlarını yazar (boşsa anahtar kaldırılır). Arka plan alanı boşsa yalnız bu anahtarı
 * taşıyan bir nesne olur; eski sürümler bilinmeyen anahtarı yok sayar (arka planı yine "yalnız renk" görür).
 */
fun withPageSettings(encoded: String, settings: com.karapp.drawguidance.data.model.PageSettings?): String {
    val base = if (encoded.isBlank()) kotlinx.serialization.json.JsonObject(emptyMap())
    else runCatching { backgroundJson.parseToJsonElement(encoded) as kotlinx.serialization.json.JsonObject }.getOrNull() ?: return encoded
    val rest = base - PAGE_SETTINGS_KEY
    if (settings == null || settings.isEmpty) return if (rest.isEmpty()) "" else kotlinx.serialization.json.JsonObject(rest).toString()
    val json = backgroundJson.encodeToJsonElement(com.karapp.drawguidance.data.model.PageSettings.serializer(), settings)
    return kotlinx.serialization.json.JsonObject(rest + (PAGE_SETTINGS_KEY to json)).toString()
}

private val guideJson = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }

fun decodeGuide(value: String): com.karapp.drawguidance.data.model.GuideSpec =
    if (value.isBlank()) com.karapp.drawguidance.data.model.GuideSpec()
    else runCatching { guideJson.decodeFromString(com.karapp.drawguidance.data.model.GuideSpec.serializer(), value).normalized() }
        .getOrDefault(com.karapp.drawguidance.data.model.GuideSpec())

fun encodeGuide(spec: com.karapp.drawguidance.data.model.GuideSpec): String =
    if (spec.isEmpty) "" else guideJson.encodeToString(com.karapp.drawguidance.data.model.GuideSpec.serializer(), spec)
