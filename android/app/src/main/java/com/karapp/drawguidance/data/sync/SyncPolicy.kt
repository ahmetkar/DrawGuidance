package com.karapp.drawguidance.data.sync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong

/** v37 eşitlemenin yapılacağı ağ. */
enum class SyncNetwork { ANY, WIFI_ONLY }

/** v37 eşitleme neden bekliyor (v39 durdurma kalktı: otomatik eşitleme kapalıyken yalnız günlük iş planlanmaz). */
enum class SyncHold { OFFLINE, METERED, DOWNLOAD_LIMIT, UPLOAD_LIMIT }

/**
 * v37 eşitleme denetimi (Ayarlar → Bağlantı): Wi-Fi ve mobil veri ya da yalnız Wi-Fi ve günlük indirme/yükleme
 * sınırları (MB; 0 sınırsız). Sunucuyla bütün ağ trafiği sayılır ([TrafficInterceptor]). Koşul uymazken olay
 * tablosundaki işler kaybolmaz, bekler; koşul düzelince kendiliğinden yapılır ([onChange]).
 *
 * v43 eşitleme her zaman kendiliğindendir: "Otomatik eşitleme" ve "Şimdi eşitle" kaldırıldı. Günlük indirme sınırına
 * ulaşılınca gelen içerik yarım indirilmez; yarım kalan silinir, ertesi gün baştan alınır ([wouldExceedDownload]).
 */
class SyncPolicy(
    context: Context,
    private val connectivity: Connectivity,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    data class Settings(
        val network: SyncNetwork = SyncNetwork.ANY,
        val downloadLimitMb: Int = 0,
        val uploadLimitMb: Int = 0,
    )

    /** Bugünkü trafik (bayt). */
    data class Usage(val downloaded: Long = 0, val uploaded: Long = 0)

    private val prefs = context.getSharedPreferences("sync_policy", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        Settings(
            network = runCatching { SyncNetwork.valueOf(prefs.getString(KEY_NETWORK, null) ?: "") }.getOrDefault(SyncNetwork.ANY),
            downloadLimitMb = prefs.getInt(KEY_DOWN_LIMIT, 0),
            uploadLimitMb = prefs.getInt(KEY_UP_LIMIT, 0),
        ),
    )
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private var day = prefs.getInt(KEY_DAY, today())
    private val down = AtomicLong(prefs.getLong(KEY_DOWN, 0))
    private val up = AtomicLong(prefs.getLong(KEY_UP, 0))
    private var savedAt = 0L
    private var savedBytes = down.get() + up.get()
    private val _usage = MutableStateFlow(Usage(down.get(), up.get()))
    val usage: StateFlow<Usage> = _usage.asStateFlow()

    /** Ayar ya da ağ değişti: bekleyenler yeniden denensin, arka plan işi yeniden planlansın. */
    @Volatile var onChange: (() -> Unit)? = null

    /** Eşitleme şu an neden bekliyor (null: çalışabilir); ayar, bağlantı ve günlük trafikle güncellenir. */
    val hold: StateFlow<SyncHold?> = combine(_settings, _usage, connectivity.online, connectivity.metered) { _, _, _, _ -> currentHold() }
        .stateIn(scope, SharingStarted.Eagerly, currentHold())

    init {
        SyncWork.wifiOnly = _settings.value.network == SyncNetwork.WIFI_ONLY
        // v43 eski "otomatik eşitleme kapalı" ayarı artık yok
        if (prefs.contains(KEY_AUTO) || prefs.contains(KEY_PAUSED)) prefs.edit().remove(KEY_AUTO).remove(KEY_PAUSED).apply()
        scope.launch { connectivity.metered.drop(1).collect { runCatching { onChange?.invoke() } } }
    }

    val allowed: Boolean get() = currentHold() == null

    fun currentHold(): SyncHold? {
        rollDay()
        val s = _settings.value
        return when {
            !connectivity.online.value -> SyncHold.OFFLINE
            s.network == SyncNetwork.WIFI_ONLY && connectivity.metered.value -> SyncHold.METERED
            s.downloadLimitMb > 0 && down.get() >= s.downloadLimitMb * MB -> SyncHold.DOWNLOAD_LIMIT
            s.uploadLimitMb > 0 && up.get() >= s.uploadLimitMb * MB -> SyncHold.UPLOAD_LIMIT
            else -> null
        }
    }

    fun setNetwork(network: SyncNetwork) = update { it.copy(network = network) }
    fun setDownloadLimit(mb: Int) = update { it.copy(downloadLimitMb = mb.coerceAtLeast(0)) }
    fun setUploadLimit(mb: Int) = update { it.copy(uploadLimitMb = mb.coerceAtLeast(0)) }

    private fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        SyncWork.wifiOnly = next.network == SyncNetwork.WIFI_ONLY
        prefs.edit()
            .putString(KEY_NETWORK, next.network.name)
            .putInt(KEY_DOWN_LIMIT, next.downloadLimitMb).putInt(KEY_UP_LIMIT, next.uploadLimitMb)
            .apply()
        runCatching { onChange?.invoke() }
    }

    /**
     * v43 bugün [more] bayt daha indirilirse günlük indirme sınırı aşılır mı (sınırsızsa hiç). Gelen içerik indirilirken
     * her parçadan önce sorulur: aşılacaksa indirme bırakılır ve yarım kalan silinir.
     */
    fun wouldExceedDownload(more: Long): Boolean {
        rollDay()
        val limit = _settings.value.downloadLimitMb
        return limit > 0 && down.get() + more > limit * MB
    }

    /** Ağ trafiği (bayt): sayılır, ara ara kaydedilir. */
    fun count(downloaded: Long, uploaded: Long) {
        if (downloaded <= 0 && uploaded <= 0) return
        rollDay()
        if (downloaded > 0) down.addAndGet(downloaded)
        if (uploaded > 0) up.addAndGet(uploaded)
        val total = down.get() + up.get()
        val now = clock()
        if (total - savedBytes >= SAVE_EVERY_BYTES || now - savedAt >= SAVE_EVERY_MS) save(now)
    }

    /** Sayaçlar hemen kaydedilir ve gösterilir. */
    fun flush() = save(clock())

    @Synchronized
    private fun save(now: Long) {
        savedAt = now
        savedBytes = down.get() + up.get()
        prefs.edit().putInt(KEY_DAY, day).putLong(KEY_DOWN, down.get()).putLong(KEY_UP, up.get()).apply()
        _usage.value = Usage(down.get(), up.get())
    }

    /** Gün değişince sayaçlar sıfırlanır. */
    @Synchronized
    private fun rollDay() {
        val d = today()
        if (d == day) return
        day = d
        down.set(0); up.set(0)
        save(clock())
    }

    private fun today(): Int = Calendar.getInstance().apply { timeInMillis = clock() }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }

    companion object {
        const val MB = 1024L * 1024L
        private const val SAVE_EVERY_BYTES = 256L * 1024L
        private const val SAVE_EVERY_MS = 15_000L
        private const val KEY_NETWORK = "network"
        private const val KEY_PAUSED = "paused"
        private const val KEY_AUTO = "auto"
        private const val KEY_DOWN_LIMIT = "down_limit_mb"
        private const val KEY_UP_LIMIT = "up_limit_mb"
        private const val KEY_DAY = "day"
        private const val KEY_DOWN = "down"
        private const val KEY_UP = "up"

        /** Ayarlar'daki günlük sınır seçenekleri (MB; 0 sınırsız). */
        val LIMITS = listOf(0, 10, 50, 200, 1000)
    }
}

/**
 * v37 sunucuyla ağ trafiğini sayar (ağ katmanında: önbellekten gelen yanıtlar sayılmaz, sıkıştırılmış boyut):
 * yükleme = istek başlıkları + gövdesi, indirme = yanıt başlıkları + okunan gövde.
 */
class TrafficInterceptor(private val count: (downloaded: Long, uploaded: Long) -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val sent = request.headers.byteCount() + (request.body?.contentLength() ?: 0L).coerceAtLeast(0L)
        val response = chain.proceed(request)
        runCatching { count(response.headers.byteCount(), sent) }
        val body = response.body ?: return response
        val counting = object : okio.ForwardingSource(body.source()) {
            override fun read(sink: okio.Buffer, byteCount: Long): Long {
                val n = super.read(sink, byteCount)
                if (n > 0) runCatching { count(n, 0) }
                return n
            }
        }.buffer()
        return response.newBuilder().body(counting.asResponseBody(body.contentType(), body.contentLength())).build()
    }
}
