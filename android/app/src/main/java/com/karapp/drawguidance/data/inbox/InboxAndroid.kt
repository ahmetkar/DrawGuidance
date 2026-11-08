package com.karapp.drawguidance.data.inbox

import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karapp.drawguidance.data.local.SyncEventEntity
import com.karapp.drawguidance.notify.AppNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * v43 resim indirici: sunucunun verdiği imzalı Cloud Storage (ya da https) adreslerinden. Sunucu adresine yönlendiren
 * ana istemciyi kullanmaz (yönlendirme olmasın diye), ama ağ trafiği yine günlük sayaçlara yazılır.
 */
class OkHttpDownloader(private val client: OkHttpClient) : InboxRepository.Downloader {
    override suspend fun <T> open(url: String, onOpen: suspend (stream: InputStream, length: Long) -> T): T {
        val response = client.newCall(Request.Builder().url(url).get().build()).await()
        response.use { r ->
            if (!r.isSuccessful) {
                // Süresi dolmuş imzalı adres (403) ya da silinmiş nesne (404): yeniden denenir (yeni adresle)
                throw IOException("İndirilemedi: HTTP ${r.code}")
            }
            val body = r.body ?: throw IOException("Boş yanıt")
            return withContext(Dispatchers.IO) { body.byteStream().use { onOpen(it, body.contentLength()) } }
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }

    companion object {
        /** Ayrı istemci: bağlantı havuzu, zaman aşımları ve trafik sayacı. */
        fun client(traffic: okhttp3.Interceptor): OkHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor(traffic)
            .connectionPool(okhttp3.ConnectionPool(4, 2, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }
}

/** v43 gelen bildirim ve güncellemeleri Android bildirimi olarak gösterir. */
class AndroidInboxNotifier(private val context: Context) : InboxRepository.InboxNotifier {
    override fun message(event: SyncEventEntity): Boolean {
        val title = event.title ?: return false
        return AppNotifications.postMessage(context, event.id.toInt(), title, event.body.orEmpty())
    }

    override fun appUpdate(event: SyncEventEntity): Boolean = AppNotifications.postUpdate(context, event.body)
}

/** v43 cihazın kendisi (kurulum kimliği: v40'tan beri aynı rastgele UUID). */
object DeviceIdentity {
    fun id(context: Context): String {
        val prefs = context.getSharedPreferences("sync_permit", Context.MODE_PRIVATE)
        return prefs.getString("device", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }
    }

    fun info(context: Context): InboxRepository.DeviceInfo = InboxRepository.DeviceInfo(
        deviceId = id(context),
        versionCode = com.karapp.drawguidance.BuildConfig.VERSION_CODE.toLong(),
        versionName = com.karapp.drawguidance.BuildConfig.VERSION_NAME,
        model = Build.MODEL, manufacturer = Build.MANUFACTURER, sdkInt = Build.VERSION.SDK_INT,
        locale = { java.util.Locale.getDefault().toLanguageTag() },
    )
}

/**
 * v43 arka plan yoklaması (WorkManager, ~15 dk; bağlantı ve ağ ayarı koşullu). Sunucu daha uzun aralık önerdiyse
 * (yoğunluk) o zamana kadar istek yapılmaz.
 */
object InboxWork {
    private const val PERIODIC = "dg-inbox-poll"
    private const val NOW = "dg-inbox-now"

    fun schedule(context: Context, constraints: Constraints, replace: Boolean) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        wm.enqueueUniquePeriodicWork(
            PERIODIC, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<InboxWorker>(15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES).setConstraints(constraints).build(),
        )
    }

    /** Hemen bir yoklama (giriş yapılınca, bağlantı gelince). */
    fun kick(context: Context, constraints: Constraints) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<InboxNowWorker>().setConstraints(constraints).build())
    }
}

class InboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<InboxRepository>() ?: return Result.success()
        if (System.currentTimeMillis() < repo.nextPollAt) return Result.success()
        repo.sync()
        return Result.success()
    }
}

class InboxNowWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<InboxRepository>()?.sync()
        return Result.success()
    }
}

/**
 * v43 uygulama açıkken uzun yoklama: sunucu yeni bir şey olana kadar (en çok ~25 sn) yanıtı bekletir, gelince hemen
 * döner; böylece Firebase olmadan bildirim ve içerik anında gelir. Arka plana geçince durur.
 *
 * Yük koruması: sunucu beklemeden döndüyse (yoğun, bekleyen sınırı dolu) 60 sn beklenir; hatada üstel bekleme
 * (5 sn → 5 dk); giriş yoksa ya da ağ ayarı izin vermiyorsa 60 sn sonra yeniden bakılır.
 */
class InboxForeground(
    private val repo: InboxRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
) : DefaultLifecycleObserver {
    private var job: Job? = null

    override fun onStart(owner: LifecycleOwner) = start()
    override fun onStop(owner: LifecycleOwner) = stop()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var backoff = 5_000L
            while (isActive) {
                val t0 = clock()
                val r = repo.sync(waitSeconds = WAIT_SECONDS)
                val took = clock() - t0
                val pause = when (r.outcome) {
                    InboxRepository.Outcome.ERROR -> backoff.also { backoff = (backoff * 2).coerceAtMost(300_000L) }
                    InboxRepository.Outcome.SIGNED_OUT, InboxRepository.Outcome.OFFLINE, InboxRepository.Outcome.HELD -> 60_000L
                    // Sunucu bekletmeden döndüyse (yoğun): biraz ara ver
                    InboxRepository.Outcome.NOT_MODIFIED -> if (took < 2_000L) 60_000L else 0L
                    InboxRepository.Outcome.DONE -> 0L
                }
                if (r.outcome != InboxRepository.Outcome.ERROR) backoff = 5_000L
                if (pause > 0) delay(pause)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object { const val WAIT_SECONDS = 25 }
}
