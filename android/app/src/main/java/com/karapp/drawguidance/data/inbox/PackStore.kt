package com.karapp.drawguidance.data.inbox

import com.karapp.drawguidance.data.remote.v1.InstalledPackageDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * v43 sunucudan gelen içerik paketlerinin cihazdaki yeri: `files/packs/<mod>/<paket>/` (ör. `packs/comic/uzay/`).
 * Her paket klasöründe `manifest.json` ve pencere başına alt klasörlerde resimler bulunur:
 * `objects/roket.webp`, `effects/toz.webp` …
 *
 * - İndirme önce geçici klasöre ([staging]) yapılır; paket **tamamen** inince tek adımda modun klasörüne taşınır
 *   ([install]). İndirme yarıda kalırsa (günlük sınır, bağlantı) geçici klasör silinir ([discard]): yarım paket
 *   hiçbir pencerede görünmez.
 * - Yeni sürüm gelince eski sürümün klasörü yenisiyle değiştirilir.
 * - Uygulamanın içinde gelen içerik "taban paket" sayılır ([BASE]): sunucuya kurulu olarak bildirilir, yeniden inmez.
 */
class PackStore(private val root: File, private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }) {

    @Serializable
    data class Item(val id: String, val window: String, val title: String, val category: String? = null, val file: String, val w: Int = 0, val h: Int = 0)

    @Serializable
    data class Manifest(val id: String, val name: String, val mode: String, val version: Int, val items: List<Item>)

    /** Kurulu paket ve klasörü. */
    data class Pack(val manifest: Manifest, val dir: File) {
        val id: String get() = manifest.id
        val mode: String get() = manifest.mode
        fun file(item: Item): File = File(dir, item.file)
        fun items(window: String): List<Item> = manifest.items.filter { it.window == window }
    }

    private val _packs = MutableStateFlow<List<Pack>>(emptyList())
    /** Kurulu paketler (açılışta ve her kurulumda yenilenir). */
    val packs: StateFlow<List<Pack>> = _packs.asStateFlow()

    init { runCatching { reload() } }

    @Synchronized
    fun reload() {
        val out = ArrayList<Pack>()
        root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { modeDir ->
            modeDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { dir ->
                val m = runCatching { json.decodeFromString(Manifest.serializer(), File(dir, MANIFEST).readText()) }.getOrNull()
                if (m != null) out += Pack(m, dir)
            }
        }
        _packs.value = out.sortedBy { it.manifest.name.lowercase() }
    }

    fun forMode(mode: String): List<Pack> = _packs.value.filter { it.mode.equals(mode, ignoreCase = true) }

    /** Sunucuya bildirilecek kurulu paketler (taban paketler dahil). */
    fun installed(): List<InstalledPackageDto> =
        BASE.map { (id, v) -> InstalledPackageDto(id, v) } + _packs.value.map { InstalledPackageDto(it.id, it.manifest.version) }

    fun version(id: String): Int = BASE.firstOrNull { it.first == id }?.second ?: _packs.value.firstOrNull { it.id == id }?.manifest?.version ?: 0

    /** İndirme için boş geçici klasör (varsa içi silinir). */
    fun staging(key: String): File {
        val dir = File(root, "$STAGING/${safe(key)}")
        dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }

    /** Yarım kalan indirmeyi siler. */
    fun discard(key: String) { File(root, "$STAGING/${safe(key)}").deleteRecursively() }

    /** Bütün yarım indirmeleri siler (açılışta: önceki oturumdan kalanlar). */
    fun discardAll() { File(root, STAGING).deleteRecursively() }

    /**
     * Tamamlanmış indirmeyi modun klasörüne taşır (eski sürüm varsa yenisiyle değiştirilir). Bütün öğe dosyaları
     * geçici klasörde olmalı.
     */
    @Synchronized
    fun install(staged: File, manifest: Manifest): Pack {
        for (i in manifest.items) require(File(staged, i.file).isFile) { "Eksik dosya: ${i.file}" }
        File(staged, MANIFEST).writeText(json.encodeToString(Manifest.serializer(), manifest))
        val modeDir = File(root, manifest.mode.lowercase()).apply { mkdirs() }
        val target = File(modeDir, safe(manifest.id))
        val old = File(root, "$STAGING/.old-${safe(manifest.id)}-${System.nanoTime()}")
        if (target.exists() && !target.renameTo(old)) target.deleteRecursively()
        if (!staged.renameTo(target)) {
            // Farklı bölümdeyse kopyalanır
            staged.copyRecursively(target, overwrite = true)
            staged.deleteRecursively()
        }
        old.deleteRecursively()
        reload()
        return Pack(manifest, target)
    }

    companion object {
        const val MANIFEST = "manifest.json"
        const val STAGING = ".staging"

        /** Uygulamanın içinde gelen taban paketler (sunucudaki `base-*` paketleriyle aynı kimlik ve sürüm). */
        val BASE: List<Pair<String, Int>> = listOf("base-comic" to 1, "base-notes" to 1, "base-art" to 1)

        /** Dosya/klasör adı için güvenli hal (yalnız harf, rakam, _ - .). */
        fun safe(s: String): String = s.replace(Regex("[^A-Za-z0-9_.\\-]"), "_").trimStart('.').ifEmpty { "_" }.take(80)
    }
}
