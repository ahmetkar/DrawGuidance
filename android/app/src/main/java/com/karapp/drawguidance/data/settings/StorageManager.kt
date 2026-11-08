package com.karapp.drawguidance.data.settings

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** v37 uygulamanın cihazda kullandığı yerin bölümleri. */
enum class StorageArea {
    /** Tuvaller, sayfalar, resimler ve ayarlar (veritabanı ve uygulama dosyaları). */
    DRAWINGS,
    /** İndirilen katalog ve cihazda eğitilen sinir ağları. */
    CATALOG,
    /** Dışa aktarılan resimler ve belgeler (uygulamanın klasöründe). */
    EXPORTS,
    /** Yeniden oluşturulabilen önbellek (sunucu yanıtları, küçük resimler, 3B modeller). */
    CACHE,
}

/**
 * v37 Ayarlar → Kaydetme → Depolama: bölüm bölüm kullanılan yer, önbellek sınırı (aşılınca en eski önbellek
 * dosyaları silinir; açılışta ve sınır değişince), önbelleği temizleme ve veritabanını sıkıştırma (silinenlerin yeri
 * geri alınır). Tuvaller ve dışa aktarılanlar buradan silinmez.
 */
class StorageManager(
    private val context: Context,
    /** Veritabanını sıkıştırır (VACUUM); testlerde verilmeyebilir. */
    private val compactDatabase: (() -> Unit)? = null,
    /** Sunucu yanıtları önbelleği (OkHttp) boşaltılır. */
    private val evictHttpCache: (() -> Unit)? = null,
) {
    private val prefs = context.getSharedPreferences("storage", Context.MODE_PRIVATE)
    private val _cacheLimitMb = MutableStateFlow(prefs.getInt(KEY_LIMIT, DEFAULT_LIMIT_MB))
    /** Önbellek sınırı (MB; 0 sınırsız). */
    val cacheLimitMb: StateFlow<Int> = _cacheLimitMb.asStateFlow()

    fun setCacheLimit(mb: Int) {
        val v = mb.coerceAtLeast(0)
        _cacheLimitMb.value = v
        prefs.edit().putInt(KEY_LIMIT, v).apply()
    }

    private fun databaseDir(): File? = context.getDatabasePath("drawguidance.db").parentFile
    private fun catalogDirs() = CATALOG_DIRS.map { File(context.filesDir, it) }
    private fun exportDirs(): List<File> = listOfNotNull(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: File(context.filesDir, "documents"),
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "pictures"),
    )
    private fun cacheDirs(): List<File> = listOfNotNull(context.cacheDir, context.externalCacheDir)

    /** Bölümlerin boyutu (bayt; dosya sistemini dolaşır, ana iş parçacığında çağrılmamalı). */
    fun measure(): Map<StorageArea, Long> {
        val excluded = (catalogDirs() + exportDirs()).map { it.absoluteFile }.toSet()
        val drawings = (databaseDir()?.let(::size) ?: 0L) + size(context.filesDir, excluded)
        return mapOf(
            StorageArea.DRAWINGS to drawings,
            StorageArea.CATALOG to catalogDirs().sumOf(::size),
            StorageArea.EXPORTS to exportDirs().sumOf(::size),
            StorageArea.CACHE to cacheDirs().sumOf(::size),
        )
    }

    /** Bütün önbellek silinir; boşalan yer (bayt). */
    fun clearCache(): Long {
        val before = cacheDirs().sumOf(::size)
        runCatching { evictHttpCache?.invoke() }
        cacheDirs().forEach { dir -> dir.listFiles()?.forEach { if (it.name != HTTP_DIR) it.deleteRecursively() } }
        return (before - cacheDirs().sumOf(::size)).coerceAtLeast(0L)
    }

    /**
     * Önbellek sınırı aşıldıysa en eski dosyalar silinir (sunucu yanıtları önbelleği kendi sınırını korur, ona
     * dokunulmaz); boşalan yer (bayt).
     */
    fun trimCache(): Long {
        val limit = _cacheLimitMb.value.toLong() * MB
        if (limit <= 0) return 0L
        val files = cacheDirs().flatMap { dir ->
            dir.listFiles()?.filter { it.name != HTTP_DIR }?.flatMap { f -> f.walkTopDown().filter { it.isFile }.toList() }.orEmpty()
        }
        var total = cacheDirs().sumOf(::size)
        if (total <= limit) return 0L
        var freed = 0L
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= limit) break
            val len = f.length()
            if (f.delete()) { total -= len; freed += len }
        }
        return freed
    }

    /** Veritabanı sıkıştırılır; boşalan yer (bayt). */
    fun compact(): Long {
        val dir = databaseDir() ?: return 0L
        val before = size(dir)
        compactDatabase?.invoke()
        return (before - size(dir)).coerceAtLeast(0L)
    }

    private fun size(file: File, excluded: Set<File> = emptySet()): Long {
        if (!file.exists() || file.absoluteFile in excluded) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { size(it, excluded) } ?: 0L
    }

    companion object {
        const val MB = 1024L * 1024L
        const val DEFAULT_LIMIT_MB = 250
        private const val KEY_LIMIT = "cache_limit_mb"
        private const val HTTP_DIR = "http"
        private val CATALOG_DIRS = listOf("catalog", "shape_models")

        /** Ayarlar'daki önbellek sınırı seçenekleri (MB; 0 sınırsız). */
        val LIMITS = listOf(50, 100, 250, 500, 0)
    }
}
