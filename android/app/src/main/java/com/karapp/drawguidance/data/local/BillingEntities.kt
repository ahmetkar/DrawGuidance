package com.karapp.drawguidance.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/*
 * v39 abonelik tabloları (yerel önbellek). Sunucudaki satın alma geçmişi (transactions) ve Play bildirimleri
 * (webhook olayları) bilerek cihazda tutulmaz: bunlar yalnız sunucuda doğrulanır ve saklanır; cihaz yalnız planları,
 * planların erişim haklarını ve kullanıcının güncel aboneliğini önbellekler (çevrimdışı da kilitler doğru çalışsın).
 */

/** v39 abonelik planı (Free, Premium). Fiyat Play'in yerel fiyatı gelene kadar gösterilir ([priceMicros], [currency]). */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    /** Play ürün ve temel plan kimliği (Free'de null). */
    val productId: String?,
    val basePlanId: String?,
    val priceMicros: Long,
    val currency: String,
    /** ISO 8601 süre (P1M); Free'de boş. */
    val period: String,
    val sortOrder: Int,
    /** "bundled" (uygulamayla gelen) ya da "remote" (sunucudan). */
    val source: String,
    val updatedAt: Long,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, " +
            "`productId` TEXT, `basePlanId` TEXT, `priceMicros` INTEGER NOT NULL, `currency` TEXT NOT NULL, `period` TEXT NOT NULL, " +
            "`sortOrder` INTEGER NOT NULL, `source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    }
}

/** v39 bilinen erişim hakları (özellik anahtarları; ör. `art.image_bg_removal`) ve hangi moda ait oldukları. */
@Entity(tableName = "features")
data class FeatureEntity(
    @PrimaryKey val key: String,
    /** "art", "notes", "comic", "model3d", "ads". */
    val mode: String,
    val sortOrder: Int,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `features` (`key` TEXT NOT NULL, `mode` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`key`))"
    }
}

/** v39 planın içerdiği erişim hakları (plan ↔ özellik). */
@Entity(tableName = "plan_features", primaryKeys = ["planId", "featureKey"], indices = [Index("featureKey")])
data class PlanFeatureEntity(val planId: String, val featureKey: String) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `plan_features` (`planId` TEXT NOT NULL, `featureKey` TEXT NOT NULL, PRIMARY KEY(`planId`, `featureKey`))"
        const val INDEX_SQL = "CREATE INDEX IF NOT EXISTS `index_plan_features_featureKey` ON `plan_features` (`featureKey`)"
    }
}

/**
 * v39 kullanıcının güncel aboneliği (Firebase kullanıcısı başına bir satır; sunucunun doğruladığı son hal). Kilitler
 * "kapalı kalacak şekilde" hesaplanır (bkz. [com.karapp.drawguidance.data.billing.Entitlements]).
 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val firebaseUid: String,
    val planId: String,
    /** ACTIVE, IN_GRACE_PERIOD, CANCELED, ON_HOLD, PAUSED, PENDING, EXPIRED ya da (abonelik yoksa) NONE. */
    val status: String,
    val productId: String?,
    val basePlanId: String?,
    val startTime: Long?,
    val expiryTime: Long?,
    val autoRenewing: Boolean,
    val acknowledged: Boolean,
    val orderId: String?,
    /** Son doğrulanan satın almanın jetonu (geri yüklemede yeniden doğrulanır). */
    val purchaseToken: String?,
    /** Sunucunun verdiği erişim hakları (virgülle; boşsa planın hakları). */
    val features: String,
    /** "server" (doğrulandı) ya da "local" (ilk girişte Free kaydı). */
    val source: String,
    val verifiedAt: Long?,
    val updatedAt: Long,
    /** v40 kullanıcının iptal ettiği zaman (Play; iptal yoksa null). */
    val cancelTime: Long? = null,
    /** v40 iptal nedeni: USER, SYSTEM, DEVELOPER, REPLACEMENT (iptal yoksa null). */
    val cancelReason: String? = null,
    /** v40 duraklatılan aboneliğin kendiliğinden devam edeceği zaman (yoksa null). */
    val autoResumeTime: Long? = null,
) {
    companion object {
        /** v40 (Room 20) eklenen sütunlar: ad → SQL türü. */
        val V40_COLUMNS = listOf("cancelTime" to "INTEGER", "cancelReason" to "TEXT", "autoResumeTime" to "INTEGER")

        /** v39 tablosu (v16 göçü bunu oluşturur; v40 sütunları 19 → 20 göçünde eklenir). */
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `subscriptions` (`firebaseUid` TEXT NOT NULL, `planId` TEXT NOT NULL, " +
            "`status` TEXT NOT NULL, `productId` TEXT, `basePlanId` TEXT, `startTime` INTEGER, `expiryTime` INTEGER, " +
            "`autoRenewing` INTEGER NOT NULL, `acknowledged` INTEGER NOT NULL, `orderId` TEXT, `purchaseToken` TEXT, " +
            "`features` TEXT NOT NULL, `source` TEXT NOT NULL, `verifiedAt` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`firebaseUid`))"
    }
}

@Dao
interface BillingDao {
    @Query("SELECT * FROM plans ORDER BY sortOrder, id")
    fun observePlans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans ORDER BY sortOrder, id")
    suspend fun plans(): List<PlanEntity>

    @Query("SELECT * FROM plan_features")
    fun observePlanFeatures(): Flow<List<PlanFeatureEntity>>

    @Query("SELECT * FROM plan_features")
    suspend fun planFeatures(): List<PlanFeatureEntity>

    @Query("SELECT * FROM features ORDER BY sortOrder, `key`")
    suspend fun features(): List<FeatureEntity>

    @Query("SELECT COUNT(*) FROM plans")
    suspend fun planCount(): Int

    @Query("SELECT COUNT(*) FROM plans WHERE source = 'remote'")
    suspend fun remotePlanCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(plans: List<PlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanFeatures(items: List<PlanFeatureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeatures(items: List<FeatureEntity>)

    @Query("DELETE FROM plans")
    suspend fun clearPlans()

    @Query("DELETE FROM plan_features")
    suspend fun clearPlanFeatures()

    @Query("DELETE FROM features")
    suspend fun clearFeatures()

    /** Planlar, hakları ve özellik listesi tek seferde değiştirilir. */
    @Transaction
    suspend fun replaceCatalog(plans: List<PlanEntity>, planFeatures: List<PlanFeatureEntity>, features: List<FeatureEntity>) {
        clearPlans(); clearPlanFeatures(); clearFeatures()
        insertPlans(plans); insertPlanFeatures(planFeatures); insertFeatures(features)
    }

    @Query("SELECT * FROM subscriptions")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE firebaseUid = :uid")
    suspend fun subscription(uid: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscription(entity: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE firebaseUid = :uid")
    suspend fun deleteSubscription(uid: String)
}

/**
 * v39 çizim görevinin ilerlemesi (seviye başına en iyi puan ve yıldız). [dirty]: sunucuya henüz yazılmadı (girişli
 * kullanıcıda eşitlenir; birleştirmede iki tarafın en iyi puanı kalır).
 */
@Entity(tableName = "challenge_progress")
data class ChallengeProgressEntity(
    /** Seviyenin sırası (0'dan; sunucudaki anahtarla aynı). */
    @PrimaryKey val level: Int,
    val bestScore: Int,
    val stars: Int,
    val updatedAt: Long,
    val dirty: Boolean,
) {
    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS `challenge_progress` (`level` INTEGER NOT NULL, `bestScore` INTEGER NOT NULL, " +
            "`stars` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`level`))"
    }
}

@Dao
interface ChallengeProgressDao {
    @Query("SELECT * FROM challenge_progress")
    fun observe(): Flow<List<ChallengeProgressEntity>>

    @Query("SELECT * FROM challenge_progress")
    suspend fun all(): List<ChallengeProgressEntity>

    @Query("SELECT * FROM challenge_progress WHERE level = :level")
    suspend fun get(level: Int): ChallengeProgressEntity?

    @Query("SELECT * FROM challenge_progress WHERE dirty = 1")
    suspend fun dirty(): List<ChallengeProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<ChallengeProgressEntity>)

    @Query("UPDATE challenge_progress SET dirty = 0 WHERE level = :level AND bestScore <= :score")
    suspend fun markClean(level: Int, score: Int)

    @Query("SELECT COUNT(*) FROM challenge_progress")
    suspend fun count(): Int
}
