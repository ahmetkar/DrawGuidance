package com.karapp.drawguidance.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karapp.drawguidance.data.local.SyncEventDao
import com.karapp.drawguidance.data.local.SyncEventEntity
import com.karapp.drawguidance.data.remote.v1.RemoteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Sunucu işinin türleri (olay tablosundaki `type`). */
object SyncTypes {
    /** Cihazın kullanıcısını sunucunun `users` koleksiyonuna yazar (son hali; tek bekleyen iş). */
    const val USER_UPSERT = "USER_UPSERT"
    /** Hazır kataloğu (şablonlar, vektörler, temel ağlar) sunucuyla eşitler (günde bir). */
    const val CATALOG_SYNC = "CATALOG_SYNC"
    /** v37 hata/çökme raporunu sunucuya gönderir (her rapor ayrı iş). */
    const val ERROR_REPORT = "ERROR_REPORT"
    /** v40 mod başına geri bildirimi sunucuya gönderir (her geri bildirim ayrı iş). */
    const val FEEDBACK = "FEEDBACK"
    /** v39 çizim görevi ilerlemesi (bkz. ChallengeProgressRepository.SYNC_TYPE). */
    const val CHALLENGE_SYNC = "CHALLENGE_SYNC"

    /**
     * v40 hesap verileri: sunucunun eşitleme iznini beklemez ve içerik eşitlemesinin ağ/sınır ayarlarına
     * (yalnız Wi-Fi, günlük sınırlar) takılmaz; yalnız bağlantı gerekir. Bağlantı yoksa ya da gönderilemezse olay
     * tablosunda bekler, yeniden denenir. İçerik (katalog) eşitlemesi ise izin ve ayarlarla yapılır.
     */
    val ACCOUNT: Set<String> = setOf(USER_UPSERT, ERROR_REPORT, FEEDBACK, CHALLENGE_SYNC)
}

/** Kalıcı hata: iş yeniden denenmez (olay tablosunda FAILED). */
class PermanentSyncException(message: String) : Exception(message)

/**
 * v33 çevrimdışı çalışma: sunucuyla ilgili her iş önce olay tablosuna ([SyncEventEntity]) yazılır, bağlantı varsa
 * hemen yapılır; yoksa ya da sunucu yanıt vermezse bekler ve **müsait zamanda** yapılır:
 * - bağlantı geldiğinde ([Connectivity.online]),
 * - uygulama açılınca ([start]),
 * - arka planda WorkManager ile (bağlantı koşullu tek seferlik iş ve günlük katalog eşitlemesi).
 *
 * Geçici hatalar (ağ, 5xx, 401/408/429) üstel beklemeyle yeniden denenir; kalıcı hatalar FAILED olur. Yapılan işler
 * geçmiş olarak saklanır (en yeni 200). İşler sırayla ve tek tek yapılır (aynı anda iki işleyici çalışmaz).
 */
class SyncEngine(
    private val dao: SyncEventDao,
    private val connectivity: Connectivity,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val handlers = HashMap<String, suspend (SyncEventEntity) -> Unit>()
    private val mutex = Mutex()
    private var started = false

    /** Arka plan işleri planlanır mı (WorkManager; testlerde Robolectric'te yok). */
    var scheduler: ((Context) -> Unit)? = null

    /** Bağlantı yokken iş eklenince: bağlantı gelince (uygulama kapalı olsa da) çalışacak arka plan işi istenir. */
    var whenOnline: (() -> Unit)? = null

    /** v37 eşitleme denetimi ([SyncPolicy]): false iken işler bekler (durduruldu, yalnız Wi-Fi, günlük sınır). */
    var gate: (() -> Boolean)? = null

    /** v40 bu tür şimdi yapılabilir mi: hesap verileri yalnız bağlantı ister, içerik ayrıca [gate]'i. */
    internal fun readyFor(type: String): Boolean = connectivity.online.value && (type in SyncTypes.ACCOUNT || gate?.invoke() != false)

    /** Yeniden deneme zamanı gelince bekleyenleri yeniden deneyen iş (tek). */
    private var retryJob: kotlinx.coroutines.Job? = null

    private val _lastRun = MutableStateFlow<Long?>(null)
    val lastRun: StateFlow<Long?> = _lastRun.asStateFlow()

    /** Bekleyen iş sayısı (tek akış: her okumada yeni sorgu açılmasın). */
    val pendingCount: Flow<Int> = dao.observePendingCount()

    fun register(type: String, handler: suspend (SyncEventEntity) -> Unit) {
        synchronized(handlers) { handlers[type] = handler }
    }

    /** İşi olay tablosuna yazar (aynı [dedupeKey]'li bekleyen işin yerine) ve hemen denemeye başlar. */
    suspend fun enqueue(type: String, payload: String = "", dedupeKey: String? = type): Long {
        val now = clock()
        val id = dao.enqueue(SyncEventEntity(type = type, payload = payload, dedupeKey = dedupeKey, createdAt = now, updatedAt = now))
        if (!connectivity.online.value) runCatching { whenOnline?.invoke() }
        kick()
        return id
    }

    /** v34 bekleyen (henüz yapılmamış) [dedupeKey]'li işi siler (ör. hesap silinirken kullanıcı yazma işi). */
    suspend fun cancelPending(dedupeKey: String) = dao.deletePendingWithKey(dedupeKey)

    /**
     * v34 [block] çalışırken işler yapılmaz (o an yapılan iş bitince başlar): hesap silinirken yarım kalan bir
     * kullanıcı yazma işi sunucuda kullanıcıyı yeniden oluşturamasın.
     */
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    /** Bekleyenleri arka planda dener. */
    fun kick() { scope.launch { runCatching { process() } } }

    /** Açılışta: bağlantı izlenir (gelince bekleyenler hemen denenir) ve bekleyenler denenir. */
    fun start(context: Context? = null) {
        if (started) return
        started = true
        context?.let { ctx -> runCatching { scheduler?.invoke(ctx) } }
        scope.launch {
            connectivity.online.collect { online ->
                if (online) { dao.resetBackoff(); runCatching { process() } }
            }
        }
    }

    /**
     * Zamanı gelmiş bekleyen işleri sırayla yapar; yapılan iş sayısı. Bağlantı yoksa hiçbir şey denenmez (deneme
     * sayısı artmaz).
     */
    suspend fun process(): Int = mutex.withLock {
        // v40 hesap verileri içerik ayarlarına (Wi-Fi, sınır) takılmaz: yalnız bağlantı yoksa hiç denenmez
        if (!connectivity.online.value) return 0
        var done = 0
        while (true) {
            val batch = dao.due(clock())
            if (batch.isEmpty()) break
            var progressed = false
            for (event in batch) {
                if (!connectivity.online.value) break
                if (!readyFor(event.type)) continue
                val handler = synchronized(handlers) { handlers[event.type] }
                if (handler == null) {
                    dao.markFailed(event.id, "Bilinmeyen iş türü: ${event.type}", clock())
                    continue
                }
                try {
                    handler(event)
                    dao.markDone(event.id, clock())
                    done++
                    progressed = true
                } catch (e: Throwable) {
                    val now = clock()
                    if (isPermanent(e)) dao.markFailed(event.id, describe(e), now)
                    else dao.markRetry(event.id, describe(e), now + backoff(event.attempts + 1), now)
                    Log.w(TAG, "iş ${event.type} (#${event.id}) yapılamadı: ${describe(e)}")
                }
            }
            if (!progressed) break
        }
        dao.trimHistory()
        _lastRun.value = clock()
        scheduleRetry()
        done
    }

    /** Bekleyen iş varsa en yakın deneme zamanında yeniden denenir (uygulama açıkken; kapalıyken WorkManager). */
    private suspend fun scheduleRetry() {
        val next = dao.pending().minOfOrNull { it.nextAttemptAt } ?: return
        val wait = (next - clock()).coerceIn(1_000L, 6 * 3_600_000L)
        retryJob?.cancel()
        retryJob = scope.launch {
            kotlinx.coroutines.delay(wait)
            runCatching { process() }
        }
    }

    private fun isPermanent(e: Throwable): Boolean = when (e) {
        is PermanentSyncException -> true
        is RemoteException -> !e.isRetryable
        is kotlinx.serialization.SerializationException -> true
        else -> false
    }

    private fun describe(e: Throwable): String = when (e) {
        is RemoteException -> "${e.status} ${e.code}: ${e.message}"
        else -> e.message ?: e.javaClass.simpleName
    }.take(500)

    companion object {
        private const val TAG = "SyncEngine"

        /** Üstel bekleme: 30 sn, 1 dk, 2 dk … en çok 6 saat. */
        fun backoff(attempt: Int): Long = (30_000L shl (attempt - 1).coerceIn(0, 10)).coerceAtMost(6 * 3_600_000L)
    }
}

/** v33 arka plan işleri (WorkManager): bağlantı gelince bekleyenler ve günlük katalog eşitlemesi. */
object SyncWork {
    private const val PENDING = "dg-sync-pending"
    private const val DAILY = "dg-catalog-daily"

    /** v37 "yalnız Wi-Fi": arka plan işleri ücretsiz ağı bekler ([SyncPolicy]). */
    @Volatile var wifiOnly = false

    private fun constraints() =
        Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()

    private const val SLOT = "dg-catalog-slot"

    /** v40 katalog eşitlemesi [delayMs] sonra (sunucunun verdiği dilimde) yapılsın. Önceki planın yerine geçer. */
    fun scheduleCatalogAt(context: Context, delayMs: Long): Boolean {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return false
        wm.enqueueUniqueWork(
            SLOT, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CatalogSlotWorker>().setConstraints(constraints())
                .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS).build(),
        )
        return true
    }

    /** Bağlantı gelince bekleyen işler yapılsın (uygulama kapalı olsa da). */
    fun kickWhenOnline(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        wm.enqueueUniqueWork(PENDING, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<PendingSyncWorker>().setConstraints(constraints()).build())
    }

    /** v37 ağ ayarı değişti: arka plan işleri yeni koşulla yeniden planlanır. */
    fun reschedule(context: Context) = schedule(context, replace = true)

    /** İşleyicilerin (katalog, kullanıcı) kayıtlı olduğundan emin olunur: uygulama arka planda yeni açılmış olabilir. */
    internal fun engine(): SyncEngine? {
        val koin = org.koin.core.context.GlobalContext.getOrNull() ?: return null
        runCatching { koin.get<com.karapp.drawguidance.data.catalog.CatalogSyncer>() }
        runCatching { koin.get<com.karapp.drawguidance.data.account.AccountRepository>() }
        // v40 geri bildirim işleyicisi
        runCatching { koin.get<com.karapp.drawguidance.data.feedback.FeedbackRepository>() }
        return koin.getOrNull()
    }

    /**
     * Günlük katalog eşitlemesi (bağlantı koşullu; v43 her zaman kendiliğinden), bekleyen işler için bir deneme ve
     * v43 gelen kutusu yoklaması planlanır.
     */
    fun schedule(context: Context, replace: Boolean = false) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val online = constraints()
        wm.enqueueUniquePeriodicWork(
            DAILY, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CatalogDailyWorker>(1, TimeUnit.DAYS).setConstraints(online).build(),
        )
        com.karapp.drawguidance.data.inbox.InboxWork.schedule(context, online, replace)
        wm.enqueueUniqueWork(
            PENDING, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PendingSyncWorker>().setConstraints(online).build(),
        )
    }
}

/**
 * v40 katalog eşitleme dilimi: sunucu izin vermediyse ([SyncPermits]) eşitleme sunucunun söylediği dilime
 * ertelenir; bu iş o zaman katalog eşitlemesini olay tablosuna yazar ve yapar.
 */
class CatalogSlotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = SyncWork.engine() ?: return Result.success()
        engine.enqueue(SyncTypes.CATALOG_SYNC)
        engine.process()
        return Result.success()
    }
}

/** Bağlantı gelince bekleyen işleri yapar (uygulama kapalıyken de). */
class PendingSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = SyncWork.engine() ?: return Result.success()
        engine.process()
        return Result.success()
    }
}

/** Günde bir: katalog eşitleme işi olay tablosuna yazılır ve bekleyenlerle birlikte yapılır. */
class CatalogDailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = SyncWork.engine() ?: return Result.success()
        engine.enqueue(SyncTypes.CATALOG_SYNC)
        engine.process()
        return Result.success()
    }
}
