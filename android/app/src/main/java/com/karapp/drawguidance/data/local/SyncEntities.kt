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
import kotlinx.coroutines.flow.Flow

/**
 * v33 olay tablosu (`sync_events`): sunucuyla ilgili **yapılmış ve yapılacak** bütün işler. Bir iş o an yapılamıyorsa
 * (bağlantı yok, sunucu kapalı) [STATUS_PENDING] kalır ve müsait zamanda (bağlantı gelince, açılışta, düzenli
 * aralıklarla) yapılır; yapılanlar [STATUS_DONE] olarak bir süre saklanır (geçmiş), kalıcı hatalar [STATUS_FAILED].
 *
 * - [type]: işin türü (ör. `USER_UPSERT`, `CATALOG_SYNC`).
 * - [dedupeKey]: aynı anahtarlı bekleyen iş varsa yenisi onun yerine geçer (ör. kullanıcının son hali yeter).
 * - [payload]: işin verisi (JSON).
 * - [attempts], [lastError], [nextAttemptAt]: yeniden deneme (üstel bekleme).
 *
 * v43 aynı tabloda **gelen** kayıtlar da tutulur ([direction] = [DIR_IN]): sunucudan gelen paket, bildirim, duyuru ve
 * uygulama güncellemesi teslimatları ([remoteId] = sunucudaki teslimat kimliği, benzersiz). Durumları:
 * - [isNew]: yeni geldi (kullanıcı/uygulama henüz ilgilenmedi),
 * - [infoAcked]: "bilgi alındı" sunucuya onaylandı,
 * - [dataReceived]: verisi tamamen geldi (paket dosyaları, duyuru resmi, bildirim metni),
 * - [handled]: asıl iş yapıldı: bildirim gösterildi, paket modun klasörüne taşındı, duyuru ana sayfada gösterildi,
 *   güncelleme bildirildi,
 * - [deliveredAcked]: "teslim edildi" sunucuya onaylandı,
 * - [dismissed]: kullanıcı kapattı (duyuru, güncelleme penceresi).
 * Giden kayıtların işleyicisi ([com.karapp.drawguidance.data.sync.SyncEngine]) yalnız [DIR_OUT] kayıtlara bakar.
 */
@Entity(
    tableName = "sync_events",
    indices = [
        Index(value = ["status", "nextAttemptAt"]), Index(value = ["dedupeKey"]),
        Index(value = ["remoteId"], unique = true), Index(value = ["direction", "kind"]),
    ],
)
data class SyncEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    @ColumnInfo(defaultValue = "") val payload: String = "",
    val dedupeKey: String? = null,
    val status: String = STATUS_PENDING,
    @ColumnInfo(defaultValue = "0") val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val nextAttemptAt: Long = 0,
    val doneAt: Long? = null,
    // v43 gelen teslimatlar
    @ColumnInfo(defaultValue = "OUT") val direction: String = DIR_OUT,
    val kind: String? = null,
    val remoteId: String? = null,
    val refId: String? = null,
    @ColumnInfo(defaultValue = "0") val refVersion: Int = 0,
    @ColumnInfo(defaultValue = "0") val isNew: Boolean = false,
    @ColumnInfo(defaultValue = "0") val infoAcked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val dataReceived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val handled: Boolean = false,
    val handledAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val deliveredAcked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val dismissed: Boolean = false,
    val title: String? = null,
    val body: String? = null,
    /** Duyurunun küçük resmi ya da paketin kurulduğu klasör (cihazdaki yol). */
    val localPath: String? = null,
    /** Gelen verinin kendisi (JSON; paket öğeleri, güncelleme bilgisi). */
    val data: String? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
        const val DIR_OUT = "OUT"
        const val DIR_IN = "IN"
        const val TYPE_INBOX = "INBOX"

        /** v43 göç: eski tabloya eklenen sütunlar (sıra ve öntanımlılar varlıkla aynı). */
        val V43_COLUMNS = listOf(
            "`direction` TEXT NOT NULL DEFAULT 'OUT'", "`kind` TEXT", "`remoteId` TEXT", "`refId` TEXT",
            "`refVersion` INTEGER NOT NULL DEFAULT 0", "`isNew` INTEGER NOT NULL DEFAULT 0", "`infoAcked` INTEGER NOT NULL DEFAULT 0",
            "`dataReceived` INTEGER NOT NULL DEFAULT 0", "`handled` INTEGER NOT NULL DEFAULT 0", "`handledAt` INTEGER",
            "`deliveredAcked` INTEGER NOT NULL DEFAULT 0", "`dismissed` INTEGER NOT NULL DEFAULT 0", "`title` TEXT", "`body` TEXT",
            "`localPath` TEXT", "`data` TEXT",
        )
    }
}

@Dao
interface SyncEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SyncEventEntity): Long

    @Query("DELETE FROM sync_events WHERE status = 'PENDING' AND direction = 'OUT' AND dedupeKey = :key")
    suspend fun deletePendingWithKey(key: String)

    /** Yeni iş: aynı anahtarlı bekleyen iş varsa onun yerine geçer. */
    @Transaction
    suspend fun enqueue(event: SyncEventEntity): Long {
        event.dedupeKey?.let { deletePendingWithKey(it) }
        return insert(event)
    }

    /** Zamanı gelmiş bekleyen işler (eskiden yeniye). */
    @Query("SELECT * FROM sync_events WHERE status = 'PENDING' AND direction = 'OUT' AND nextAttemptAt <= :now ORDER BY id LIMIT :limit")
    suspend fun due(now: Long, limit: Int = 50): List<SyncEventEntity>

    @Query("SELECT * FROM sync_events WHERE status = 'PENDING' AND direction = 'OUT' ORDER BY id")
    suspend fun pending(): List<SyncEventEntity>

    @Query("SELECT COUNT(*) FROM sync_events WHERE status = 'PENDING' AND direction = 'OUT'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_events ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<SyncEventEntity>>

    @Query("SELECT * FROM sync_events ORDER BY id")
    suspend fun all(): List<SyncEventEntity>

    @Query("UPDATE sync_events SET status = 'DONE', doneAt = :now, updatedAt = :now, lastError = NULL, attempts = attempts + 1 WHERE id = :id")
    suspend fun markDone(id: Long, now: Long)

    @Query("UPDATE sync_events SET attempts = attempts + 1, lastError = :error, nextAttemptAt = :next, updatedAt = :now WHERE id = :id")
    suspend fun markRetry(id: Long, error: String, next: Long, now: Long)

    @Query("UPDATE sync_events SET status = 'FAILED', attempts = attempts + 1, lastError = :error, updatedAt = :now WHERE id = :id")
    suspend fun markFailed(id: Long, error: String, now: Long)

    /** Bekleyen işlerin hepsini hemen denenir yapar (bağlantı geldi). */
    @Query("UPDATE sync_events SET nextAttemptAt = 0 WHERE status = 'PENDING' AND direction = 'OUT'")
    suspend fun resetBackoff()

    /** Geçmiş: en yeni [keep] yapılmış/başarısız iş dışındakiler silinir. */
    @Query("DELETE FROM sync_events WHERE status != 'PENDING' AND direction = 'OUT' AND id NOT IN (SELECT id FROM sync_events WHERE status != 'PENDING' AND direction = 'OUT' ORDER BY id DESC LIMIT :keep)")
    suspend fun trimHistory(keep: Int = 200)

    // ------------------------------------------------------------------ v43 gelen teslimatlar

    @Query("SELECT * FROM sync_events WHERE remoteId = :remoteId LIMIT 1")
    suspend fun incoming(remoteId: String): SyncEventEntity?

    @Query("SELECT * FROM sync_events WHERE direction = 'IN' AND status = 'PENDING' ORDER BY id")
    suspend fun incomingOpen(): List<SyncEventEntity>

    @Query("SELECT * FROM sync_events WHERE direction = 'IN' ORDER BY id DESC LIMIT :limit")
    suspend fun incomingRecent(limit: Int = 100): List<SyncEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIncoming(event: SyncEventEntity): Long

    @androidx.room.Update
    suspend fun update(event: SyncEventEntity)

    /** Ana sayfadaki duyurular: verisi gelmiş, kapatılmamış (yeniden eskiye). */
    @Query("SELECT * FROM sync_events WHERE direction = 'IN' AND kind = 'ANNOUNCEMENT' AND dataReceived = 1 AND dismissed = 0 ORDER BY id DESC LIMIT 5")
    fun observeAnnouncements(): Flow<List<SyncEventEntity>>

    /** En yeni, kapatılmamış uygulama güncellemesi. */
    @Query("SELECT * FROM sync_events WHERE direction = 'IN' AND kind = 'APP_UPDATE' AND dataReceived = 1 AND dismissed = 0 ORDER BY refVersion DESC LIMIT 1")
    fun observeAppUpdate(): Flow<SyncEventEntity?>

    @Query("SELECT * FROM sync_events WHERE direction = 'IN' AND kind = 'APP_UPDATE' AND dataReceived = 1 ORDER BY refVersion DESC LIMIT 1")
    suspend fun latestAppUpdate(): SyncEventEntity?

    @Query("UPDATE sync_events SET handled = 1, handledAt = :now, isNew = 0 WHERE id = :id AND handled = 0")
    suspend fun markHandled(id: Long, now: Long)

    @Query("UPDATE sync_events SET dismissed = 1, isNew = 0, updatedAt = :now WHERE id = :id")
    suspend fun dismiss(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM sync_events WHERE direction = 'IN' AND isNew = 1")
    fun observeNewCount(): Flow<Int>

    /**
     * Gelen teslimat geçmişi: bitmiş ve işi kalmamış kayıtlardan (kapatılmış duyurular; gösterilmiş bildirimler, kurulmuş
     * paketler) en yeni [keep] dışındakiler silinir.
     */
    @Query("DELETE FROM sync_events WHERE direction = 'IN' AND status != 'PENDING' AND (dismissed = 1 OR (kind IN ('NOTIFICATION', 'PACKAGE') AND handled = 1)) AND id NOT IN (SELECT id FROM sync_events WHERE direction = 'IN' ORDER BY id DESC LIMIT :keep)")
    suspend fun trimIncoming(keep: Int = 300)
}

/**
 * v33 cihazın kullanıcısı (`local_user`): Google (Firebase) hesabından alınan bilgiler ve doğum tarihi, tek bir
 * birincil kimlikle ([id], UUID) tek satırda. Tabloda yalnız bu cihazın kullanıcısı bulunur (başka hesapla girilince
 * eskisi silinir). Önce buraya yazılır, sonra olay tablosu üzerinden sunucunun `users` koleksiyonuna gider.
 */
@Entity(tableName = "local_user", indices = [Index(value = ["firebaseUid"], unique = true)])
data class LocalUserEntity(
    @PrimaryKey val id: String,
    val firebaseUid: String,
    val displayName: String?,
    val givenName: String?,
    val familyName: String?,
    val email: String?,
    val emailVerified: Boolean,
    val photoUrl: String?,
    /** Hesabın oluşturulma zamanı (Firebase, ms). */
    val accountCreatedAt: Long?,
    /** Doğum tarihi (yyyy-MM-dd). */
    val birthDate: String?,
    /** Oturum açık mı (çıkış yapınca false; aynı hesapla yeniden girince bilgiler korunur). */
    val signedIn: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** Sunucuya en son yazıldığı zaman (null: henüz yazılmadı). */
    val syncedAt: Long? = null,
    /** v36 kullanım şartları ve KVKK aydınlatma metninin onaylandığı zaman (hesap açılırken). */
    val termsAcceptedAt: Long? = null,
    /** v36 onaylanan metinlerin sürümü ([com.karapp.drawguidance.ui.screens.account.LegalTexts.VERSION]). */
    val termsVersion: String? = null,
)

@Dao
interface LocalUserDao {
    @Query("SELECT * FROM local_user LIMIT 1")
    fun observe(): Flow<LocalUserEntity?>

    @Query("SELECT * FROM local_user LIMIT 1")
    suspend fun get(): LocalUserEntity?

    @Query("SELECT * FROM local_user WHERE firebaseUid = :uid LIMIT 1")
    suspend fun byUid(uid: String): LocalUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: LocalUserEntity)

    @Query("DELETE FROM local_user")
    suspend fun clear()

    /** Cihazda yalnız bu kullanıcı kalır. */
    @Transaction
    suspend fun replaceAll(user: LocalUserEntity) {
        clear()
        upsert(user)
    }

    @Query("UPDATE local_user SET syncedAt = :at WHERE id = :id")
    suspend fun markSynced(id: String, at: Long)
}
