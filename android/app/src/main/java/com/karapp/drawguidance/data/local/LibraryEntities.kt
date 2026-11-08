package com.karapp.drawguidance.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * v23 arka plan kütüphanesi (`paper_assets`): desenler ve hazır resimler.
 * - [kind] "pattern" (sıkıştırılmış çizim komutları, [com.karapp.drawguidance.content.paper.PaperCodec]) ya da "image"
 *   (program içinde üretilip WebP olarak sıkıştırılmış resim).
 * - [usage] "NOTE" (not tuvali), "ART" (resim tuvali) ya da "BOX" (yazı kutusu).
 * - [width]/[height] desende karo boyu (tuval birimi ×16), resimde piksel boyu; [insets] resmi esnetirken korunan
 *   kenarlar (sol,üst,sağ,alt piksel; dokuz parçalı yerleşim).
 */
@Entity(tableName = "paper_assets", indices = [Index(value = ["usage", "kind"])])
class PaperAssetEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val usage: String,
    val name: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
    val width: Int,
    val height: Int,
    val insets: String,
    val sortOrder: Int,
    val version: Int,
) {
    companion object {
        const val PATTERN = "pattern"
        const val IMAGE = "image"
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `paper_assets` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `usage` TEXT NOT NULL, " +
            "`name` TEXT NOT NULL, `data` BLOB NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `insets` TEXT NOT NULL, " +
            "`sortOrder` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        const val INDEX_SQL = "CREATE INDEX IF NOT EXISTS `index_paper_assets_usage_kind` ON `paper_assets` (`usage`, `kind`)"
    }
}

/** Liste satırı (veri olmadan). */
class PaperAssetInfo(val id: String, val kind: String, val usage: String, val name: String, val width: Int, val height: Int, val insets: String, val sortOrder: Int)

@Dao
interface PaperAssetDao {
    @Query("SELECT id, kind, usage, name, width, height, insets, sortOrder FROM paper_assets ORDER BY usage, kind, sortOrder")
    fun observeInfo(): Flow<List<PaperAssetInfo>>

    @Query("SELECT id, kind, usage, name, width, height, insets, sortOrder FROM paper_assets ORDER BY usage, kind, sortOrder")
    suspend fun info(): List<PaperAssetInfo>

    @Query("SELECT * FROM paper_assets WHERE id = :id")
    suspend fun get(id: String): PaperAssetEntity?

    @Query("SELECT * FROM paper_assets WHERE kind = :kind")
    suspend fun byKind(kind: String): List<PaperAssetEntity>

    @Query("SELECT id, version FROM paper_assets")
    suspend fun versions(): List<IdVersion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<PaperAssetEntity>)

    @Query("DELETE FROM paper_assets WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}

/** Kimlik ve sürüm (tohumlamada neyin yenileneceği). */
class IdVersion(val id: String, val version: Int)

/**
 * v23 3B Şekillerim (`models3d`): 3B düzenleyicide kaydedilen modeller ([data]: v24'ten beri GLB,
 * [com.karapp.drawguidance.content.model3d.Model3DStore]; eski kayıtlar açılışta dönüştürülür) ve küçük resimleri (WebP). [source] "user" ya da "sample"
 * (ilk açılışta eklenen örnek).
 */
@Entity(tableName = "models3d")
class Model3DEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val thumb: ByteArray?,
    val createdAt: Long,
    val updatedAt: Long,
    val source: String,
) {
    companion object {
        const val USER = "user"
        const val SAMPLE = "sample"
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `models3d` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` BLOB NOT NULL, " +
            "`thumb` BLOB, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`id`))"
    }
}

/** Liste satırı (model verisi olmadan; küçük resim dahil). */
class Model3DInfo(val id: String, val name: String, val thumb: ByteArray?, val createdAt: Long, val updatedAt: Long, val source: String)

@Dao
interface Model3DDao {
    @Query("SELECT id, name, thumb, createdAt, updatedAt, source FROM models3d ORDER BY updatedAt DESC")
    fun observeInfo(): Flow<List<Model3DInfo>>

    @Query("SELECT * FROM models3d WHERE id = :id")
    suspend fun get(id: String): Model3DEntity?

    @Query("SELECT id FROM models3d WHERE source = :source")
    suspend fun idsBySource(source: String): List<String>

    @Query("SELECT COUNT(*) FROM models3d")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: Model3DEntity)

    @Query("UPDATE models3d SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("DELETE FROM models3d WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    /** v24 tüm kimlikler (GLB'ye geçiş). */
    @Query("SELECT id FROM models3d")
    suspend fun allIds(): List<String>

    /** v24 yalnız model verisi (biçim dönüşümü; zaman ve küçük resim korunur). */
    @Query("UPDATE models3d SET data = :data WHERE id = :id")
    suspend fun updateData(id: String, data: ByteArray)
}

/** v23 hazır 3B dokuları (`textures3d`): WebP olarak sıkıştırılmış döşenebilir resimler. */
@Entity(tableName = "textures3d")
class Texture3DEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
    val size: Int,
    val sortOrder: Int,
    val version: Int,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `textures3d` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, " +
            "`data` BLOB NOT NULL, `size` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    }
}

class TextureInfo(val id: String, val name: String, val category: String, val sortOrder: Int)

@Dao
interface Texture3DDao {
    @Query("SELECT id, name, category, sortOrder FROM textures3d ORDER BY sortOrder")
    fun observeInfo(): Flow<List<TextureInfo>>

    @Query("SELECT * FROM textures3d WHERE id = :id")
    suspend fun get(id: String): Texture3DEntity?

    @Query("SELECT id, version FROM textures3d")
    suspend fun versions(): List<IdVersion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<Texture3DEntity>)
}
