package com.karapp.drawguidance.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * v21 şekil veritabanı (SQLite, `shape_templates` tablosu): çizimin elemanı olabilecek nesnelerin şablonları
 * (göz, burun, çatı, kapı, sandalye, meyve, hayvan uzvu…) ve kullanıcının kabul ettiği çizimlerden öğrenilen örnekler.
 *
 * - [key] benzersiz kayıt anahtarı; katalog kaydında şablon anahtarı ("yuz.goz.badem"), kullanıcı örneğinde
 *   "<şablon>#u<zaman>". [templateKey] kaydın ait olduğu şablon.
 * - [strokeCount], [aspectBucket] (8 kova en-boy sınıfı), [closedMask], [cornerCount]: hızlı ön eleme sütunları
 *   (bileşik dizinli).
 * - [geometry]: birim kutudaki çizgiler, 16 bit tam sayı (BLOB). [descriptor]: yön ızgarası öznitelikleri, karekök
 *   sıkıştırmalı 8 bit (BLOB; katalogda 7 bozulmuş kopya). [lshKey]: 16 bit SimHash kovası (dizinli).
 * - [usage] / [rejects]: öneri kabul ve ret sayıları (öğrenilen önsel). [source]: "catalog" ya da "user".
 * - [catalogVersion]: kaydın hangi katalog sürümüyle yazıldığı (sürüm artınca katalog kayıtları yenilenir, sayaçlar ve
 *   kullanıcı örnekleri korunur).
 */
@Entity(
    tableName = "shape_templates",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["strokeCount", "aspectBucket"]),
        Index(value = ["lshKey"]),
        Index(value = ["templateKey"]),
    ],
)
class ShapeTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val templateKey: String,
    val name: String,
    val category: String,
    @ColumnInfo(name = "groupName") val group: String,
    val description: String,
    val solid: String?,
    val strokeCount: Int,
    val closedMask: Int,
    val aspectBucket: Int,
    val cornerCount: Int,
    val geometry: ByteArray,
    val descriptor: ByteArray,
    val lshKey: Int,
    val usage: Int,
    val rejects: Int,
    val source: String,
    val catalogVersion: Int,
) {
    companion object {
        const val SOURCE_CATALOG = "catalog"
        const val SOURCE_USER = "user"

        /** Oda (Room) şemasıyla birebir aynı tablo (v8 → v9 geçişi). */
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `shape_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`key` TEXT NOT NULL, `templateKey` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `groupName` TEXT NOT NULL, " +
            "`description` TEXT NOT NULL, `solid` TEXT, `strokeCount` INTEGER NOT NULL, `closedMask` INTEGER NOT NULL, " +
            "`aspectBucket` INTEGER NOT NULL, `cornerCount` INTEGER NOT NULL, `geometry` BLOB NOT NULL, `descriptor` BLOB NOT NULL, " +
            "`lshKey` INTEGER NOT NULL, `usage` INTEGER NOT NULL, `rejects` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
            "`catalogVersion` INTEGER NOT NULL)"
        val INDEX_SQL = listOf(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_shape_templates_key` ON `shape_templates` (`key`)",
            "CREATE INDEX IF NOT EXISTS `index_shape_templates_strokeCount_aspectBucket` ON `shape_templates` (`strokeCount`, `aspectBucket`)",
            "CREATE INDEX IF NOT EXISTS `index_shape_templates_lshKey` ON `shape_templates` (`lshKey`)",
            "CREATE INDEX IF NOT EXISTS `index_shape_templates_templateKey` ON `shape_templates` (`templateKey`)",
        )
    }
}

/** Sayaç satırı (katalog yenilenirken korunur). */
class ShapeTemplateCounts(val key: String, val usage: Int, val rejects: Int)

@Dao
interface ShapeTemplateDao {
    @Query("SELECT * FROM shape_templates")
    suspend fun all(): List<ShapeTemplateEntity>

    @Query("SELECT COUNT(*) FROM shape_templates WHERE source = 'catalog' AND catalogVersion = :version")
    suspend fun catalogCount(version: Int): Int

    @Query("SELECT `key`, usage, rejects FROM shape_templates WHERE source = 'catalog'")
    suspend fun catalogCounts(): List<ShapeTemplateCounts>

    @Query("DELETE FROM shape_templates WHERE source = 'catalog'")
    suspend fun deleteCatalog()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ShapeTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: ShapeTemplateEntity): Long

    @Transaction
    suspend fun replaceCatalog(rows: List<ShapeTemplateEntity>) {
        deleteCatalog()
        insertAll(rows)
    }

    @Query("UPDATE shape_templates SET usage = usage + 1 WHERE `key` = :key")
    suspend fun addUsage(key: String)

    @Query("UPDATE shape_templates SET rejects = rejects + 1 WHERE `key` IN (:keys)")
    suspend fun addRejects(keys: List<String>)

    @Query("SELECT COUNT(*) FROM shape_templates WHERE templateKey = :templateKey AND source = 'user'")
    suspend fun userCount(templateKey: String): Int

    /** En eski kullanıcı örneği (şablon başına örnek sınırı aşılınca silinir). */
    @Query("DELETE FROM shape_templates WHERE id = (SELECT id FROM shape_templates WHERE templateKey = :templateKey AND source = 'user' ORDER BY id LIMIT 1)")
    suspend fun deleteOldestUser(templateKey: String)

    @Query("SELECT COUNT(*) FROM shape_templates")
    suspend fun count(): Int

    /** Ön eleme sütunlarıyla (bileşik dizin) aday kayıtlar: çizgi sayısı aralığı ve en-boy kovası. */
    @Query("SELECT `key` FROM shape_templates WHERE strokeCount BETWEEN :minStrokes AND :maxStrokes AND aspectBucket BETWEEN :minAspect AND :maxAspect")
    suspend fun keysByShape(minStrokes: Int, maxStrokes: Int, minAspect: Int, maxAspect: Int): List<String>

    /** Aynı SimHash kovasındaki kayıtlar. */
    @Query("SELECT `key` FROM shape_templates WHERE lshKey IN (:keys)")
    suspend fun keysByLsh(keys: List<Int>): List<String>

    @Query("SELECT category, COUNT(*) AS count FROM shape_templates WHERE source = 'catalog' GROUP BY category ORDER BY category")
    suspend fun categoryCounts(): List<ShapeCategoryCount>
}

class ShapeCategoryCount(val category: String, val count: Int)
