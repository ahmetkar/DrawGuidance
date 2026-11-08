package com.karapp.drawguidance.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * v36 kütüphane şekillerinin (vektör şekiller: hazır çizimler, nesneler, efektler…) cihazdaki veritabanı kaydı.
 * Uygulamayla gelenler ([SOURCE_BUNDLED]) katalog sürümü değişince bir kez yazılır; sunucudan gelen daha yeni katalog
 * ([SOURCE_REMOTE]) ayrı satırlarla tutulur ve açılışta kütüphaneye uygulanır. Anahtar kaynak + kimliktir: sunucu
 * yerleşik bir şeklin yeni hâlini gönderince yerleşik satır silinmez. Sunucuda aynı kayıtlar `catalog_vectors`
 * koleksiyonundadır (tohum: `catalog.json.gz`).
 *
 * [paths] çizgilerin ikili biçiminin ([com.karapp.drawguidance.content.catalog.CatalogCodec.packPaths]) Deflate ile
 * sıkıştırılmış hâlidir; [tags] satır sonuyla ayrılır.
 */
@Entity(tableName = "catalog_vectors", primaryKeys = ["source", "id"], indices = [Index("categoryId")])
class CatalogVectorEntity(
    val id: String,
    val title: String,
    val categoryId: String,
    val categoryTitle: String,
    val section: String,
    val audience: String,
    val fit: String,
    val aspect: Float,
    val tags: String,
    val paths: ByteArray,
    val source: String,
    val catalogVersion: Long,
    val updatedAt: Long,
) {
    companion object {
        const val SOURCE_BUNDLED = "bundled"
        const val SOURCE_REMOTE = "remote"

        /** Room şemasıyla birebir aynı tablo (v13 → v14 geçişi). */
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `catalog_vectors` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`categoryId` TEXT NOT NULL, `categoryTitle` TEXT NOT NULL, `section` TEXT NOT NULL, `audience` TEXT NOT NULL, " +
            "`fit` TEXT NOT NULL, `aspect` REAL NOT NULL, `tags` TEXT NOT NULL, `paths` BLOB NOT NULL, `source` TEXT NOT NULL, " +
            "`catalogVersion` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`source`, `id`))"
        const val INDEX_SQL = "CREATE INDEX IF NOT EXISTS `index_catalog_vectors_categoryId` ON `catalog_vectors` (`categoryId`)"
    }
}

@Dao
interface CatalogVectorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CatalogVectorEntity>)

    @Query("SELECT * FROM catalog_vectors WHERE source = :source")
    suspend fun bySource(source: String): List<CatalogVectorEntity>

    @Query("SELECT COUNT(*) FROM catalog_vectors WHERE source = :source")
    suspend fun count(source: String): Int

    @Query("SELECT MAX(catalogVersion) FROM catalog_vectors WHERE source = :source")
    suspend fun version(source: String): Long?

    @Query("DELETE FROM catalog_vectors WHERE source = :source")
    suspend fun deleteSource(source: String)

    @Query("SELECT * FROM catalog_vectors WHERE source = :source AND id = :id")
    suspend fun byId(source: String, id: String): CatalogVectorEntity?

    /** Kaynağın kayıtları tek işlemde yenilenir (yarım kalan yazma eski ve yeni kayıtları karıştırmaz). */
    @Transaction
    suspend fun replaceSource(source: String, items: List<CatalogVectorEntity>) {
        deleteSource(source)
        // SQLite değişken sınırı için parça parça
        items.chunked(200).forEach { upsertAll(it) }
    }
}

/**
 * v37 sinir ağlarının cihazdaki veritabanı kaydı (vektör şekiller gibi): modların şekil düzeltme ağları (ART, COMIC,
 * NOTES, KIDS; `modenet-int8-v1`) ve not alma modunun yazı düzeltme ağı (`NOTES_TEXT`, `inknet-int8-v1`).
 * Uygulamayla gelenler ([SOURCE_BUNDLED]) katalog sürümü değişince bir kez yazılır; sunucudan inenler
 * ([SOURCE_REMOTE]) ayrı satırlarda tutulur ve açılışta kurulur. Sunucuda aynı kayıtlar `catalog_mode_nets`
 * koleksiyonundadır (tohum: `catalog.json.gz`).
 */
@Entity(tableName = "catalog_nets", primaryKeys = ["source", "mode"])
class CatalogNetEntity(
    val mode: String,
    val source: String,
    val version: Int,
    val format: String,
    val weights: ByteArray,
    val catalogVersion: Long,
    val updatedAt: Long,
) {
    companion object {
        const val SOURCE_BUNDLED = "bundled"
        const val SOURCE_REMOTE = "remote"

        /** Room şemasıyla birebir aynı tablo (v14 → v15 geçişi). */
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `catalog_nets` (`mode` TEXT NOT NULL, `source` TEXT NOT NULL, " +
            "`version` INTEGER NOT NULL, `format` TEXT NOT NULL, `weights` BLOB NOT NULL, `catalogVersion` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`source`, `mode`))"
    }
}

@Dao
interface CatalogNetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CatalogNetEntity)

    @Query("SELECT * FROM catalog_nets WHERE source = :source")
    suspend fun bySource(source: String): List<CatalogNetEntity>

    @Query("SELECT * FROM catalog_nets WHERE source = :source AND mode = :mode")
    suspend fun get(source: String, mode: String): CatalogNetEntity?

    @Query("SELECT MIN(catalogVersion) FROM catalog_nets WHERE source = :source")
    suspend fun version(source: String): Long?

    @Query("SELECT COUNT(*) FROM catalog_nets WHERE source = :source")
    suspend fun count(source: String): Int

    @Query("DELETE FROM catalog_nets WHERE source = :source")
    suspend fun deleteSource(source: String)

    /** [mode] ağının [version]'dan eski kayıtları (her kaynakta) silinir; silinen satır sayısı. */
    @Query("DELETE FROM catalog_nets WHERE mode = :mode AND version < :version")
    suspend fun deleteOlder(mode: String, version: Int): Int

    @Transaction
    suspend fun replaceSource(source: String, items: List<CatalogNetEntity>) {
        deleteSource(source)
        items.forEach { upsert(it) }
    }
}
