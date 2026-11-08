package com.karapp.drawguidance.data.inbox

import android.content.SharedPreferences
import com.karapp.drawguidance.data.account.AuthProvider
import com.karapp.drawguidance.data.local.SyncEventDao
import com.karapp.drawguidance.data.local.SyncEventEntity
import com.karapp.drawguidance.data.local.SyncEventEntity.Companion.DIR_IN
import com.karapp.drawguidance.data.local.SyncEventEntity.Companion.STATUS_DONE
import com.karapp.drawguidance.data.local.SyncEventEntity.Companion.STATUS_PENDING
import com.karapp.drawguidance.data.local.SyncEventEntity.Companion.TYPE_INBOX
import com.karapp.drawguidance.data.remote.v1.AckItemDto
import com.karapp.drawguidance.data.remote.v1.DeliveryDataDto
import com.karapp.drawguidance.data.remote.v1.DeviceRegistration
import com.karapp.drawguidance.data.remote.v1.InboxItemDto
import com.karapp.drawguidance.data.remote.v1.RemoteException
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.sync.Connectivity
import com.karapp.drawguidance.data.sync.SyncHold
import com.karapp.drawguidance.data.sync.SyncPolicy
import com.karapp.drawguidance.diagnostics.AppLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.random.Random

/**
 * v43 sunucudan gelen içerik (paket, bildirim, duyuru, uygulama güncellemesi) — Firebase/FCM olmadan.
 *
 * Bir tur ([sync]):
 * 1. Giriş yapılmışsa cihaz sunucuya kaydedilir (bilgi ya da kurulu paketler değiştiyse, en geç günde bir).
 * 2. Gelen kutusu yoklanır: değişmediyse sunucu 304 döner (tek ucuz istek). Uygulama açıkken [waitSeconds] ile uzun
 *    yoklama yapılır: sunucu yeni bir şey olana kadar bekletir.
 * 3. Yeni teslimatlar olay tablosuna yazılır ("bilgi geldi", [SyncEventEntity.isNew]) ve sunucuya toplu "bilgi
 *    alındı" onayı gider; sunucu veri hazırsa aynı yanıtta söyler.
 * 4. Verisi hazır olanlar indirilir: bildirim metni, duyurunun küçük resmi, paketin bütün resimleri. Günlük indirme
 *    sınırı her parçadan önce sorulur; aşılacaksa indirme bırakılır ve **yarım kalan silinir** (ertesi gün baştan).
 * 5. Tamamen alınanlar işlenir: bildirim gösterilir, paket modun klasörüne taşınır ([PackStore]), duyuru ana sayfada,
 *    güncelleme bildirilir; sunucuya toplu "teslim edildi" onayı gider.
 *
 * Yoklama aralığını sunucu söyler (`X-Poll-After`, yoğunlukta uzar); cihaz üzerine rastgele sapma ekler (binlerce cihaz
 * aynı anda gelmesin). Turlar sırayla yapılır ([mutex]).
 */
class InboxRepository(
    private val dao: SyncEventDao,
    private val remote: RemoteStore?,
    private val auth: AuthProvider?,
    private val policy: SyncPolicy,
    private val connectivity: Connectivity,
    private val packs: PackStore,
    private val downloader: Downloader,
    private val notifier: InboxNotifier,
    private val device: DeviceInfo,
    private val prefs: SharedPreferences,
    private val announcementsDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    /** Resim indirici (yalnız sunucunun verdiği https adresleri; testlerde sahte). */
    interface Downloader {
        /** Akışı açar ([onOpen] içinde okunur, sonra kapatılır); içerik uzunluğu bilinmiyorsa -1. */
        suspend fun <T> open(url: String, onOpen: suspend (stream: InputStream, length: Long) -> T): T
    }

    /** Bildirim gösterici (uygulamada Android bildirimleri). */
    interface InboxNotifier {
        /** Gösterildiyse true (bildirim izni yoksa false). */
        fun message(event: SyncEventEntity): Boolean
        fun appUpdate(event: SyncEventEntity): Boolean
    }

    /** Cihazın kendisi (kimlik ve sunucuya şifreli giden bilgiler). */
    data class DeviceInfo(
        val deviceId: String, val versionCode: Long, val versionName: String, val model: String?, val manufacturer: String?,
        val sdkInt: Int?, val locale: () -> String?,
    )

    enum class Outcome { DONE, NOT_MODIFIED, SIGNED_OUT, OFFLINE, HELD, ERROR }

    data class Result(val outcome: Outcome, val newItems: Int = 0, val received: Int = 0, val pollAfterMs: Long? = null, val error: String? = null)

    /** Günlük sınıra takıldı: indirme bırakıldı. */
    class LimitReachedException : IOException("Günlük indirme sınırı")

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Bir sonraki arka plan yoklamasının zamanı (sunucunun önerisi + sapma). */
    val nextPollAt: Long get() = prefs.getLong(KEY_NEXT, 0L)

    // ------------------------------------------------------------------ tur

    suspend fun sync(waitSeconds: Int? = null): Result = mutex.withLock {
        val r = remote ?: return Result(Outcome.SIGNED_OUT)
        val a = auth ?: return Result(Outcome.SIGNED_OUT)
        if (a.current() == null) return Result(Outcome.SIGNED_OUT)
        if (!connectivity.online.value) return Result(Outcome.OFFLINE)
        // Yalnız Wi-Fi ayarında mobil veride ya da günlük sınır dolmuşsa hiç istek yapılmaz; yerel işler yine yapılır
        when (policy.currentHold()) {
            SyncHold.OFFLINE -> return Result(Outcome.OFFLINE)
            SyncHold.METERED, SyncHold.DOWNLOAD_LIMIT, SyncHold.UPLOAD_LIMIT -> {
                runLocalActions()
                return Result(Outcome.HELD)
            }
            null -> Unit
        }
        try {
            var token = a.idToken(false) ?: return Result(Outcome.SIGNED_OUT)
            ensureRegistered(r, token, force = false)
            val poll = try {
                r.inbox(token, device.deviceId, prefs.getString(KEY_ETAG, null), waitSeconds)
            } catch (e: RemoteException) {
                when {
                    e.isUnauthorized -> {
                        token = a.idToken(true) ?: return Result(Outcome.SIGNED_OUT)
                        r.inbox(token, device.deviceId, prefs.getString(KEY_ETAG, null), waitSeconds)
                    }
                    e.isNotFound -> {
                        // Sunucuda kayıt yok (veritabanı sıfırlanmış, başka sunucu): yeniden kaydol
                        ensureRegistered(r, token, force = true)
                        r.inbox(token, device.deviceId, null, null)
                    }
                    else -> throw e
                }
            }
            val pollAfter = schedule(poll.pollAfterSec)
            var newItems = 0
            if (!poll.notModified) {
                newItems = record(poll.items)
                poll.etag?.let { prefs.edit().putString(KEY_ETAG, it).apply() }
            }
            ackInfo(r, token)
            val received = fetchData(r, token)
            ackDelivered(r, token)
            runLocalActions()
            dao.trimIncoming()
            return Result(if (poll.notModified && newItems == 0 && received == 0) Outcome.NOT_MODIFIED else Outcome.DONE, newItems, received, pollAfter)
        } catch (e: RemoteException) {
            AppLog.w(TAG, "gelen kutusu: ${e.status} ${e.code}")
            return Result(Outcome.ERROR, error = "${e.status} ${e.code}")
        } catch (e: IOException) {
            return Result(Outcome.ERROR, error = e.message ?: e.javaClass.simpleName)
        }
    }

    /** Sunucunun önerdiği aralık (yoksa 15 dk) + %0–20 sapma; ms. */
    private fun schedule(pollAfterSec: Long?): Long {
        val base = (pollAfterSec ?: DEFAULT_POLL_SEC).coerceIn(60L, 24 * 3600L) * 1000L
        val jitter = (base * random.nextDouble(0.0, 0.2)).toLong()
        prefs.edit().putLong(KEY_NEXT, clock() + base + jitter).putLong(KEY_LAST, clock()).apply()
        return base + jitter
    }

    // ------------------------------------------------------------------ kayıt

    private fun registration(): DeviceRegistration = DeviceRegistration(
        deviceId = device.deviceId, appVersionCode = device.versionCode, appVersionName = device.versionName,
        model = device.model, manufacturer = device.manufacturer, sdkInt = device.sdkInt, locale = device.locale(),
        packages = packs.installed(),
    )

    private suspend fun ensureRegistered(r: RemoteStore, token: String, force: Boolean) {
        val body = registration()
        val hash = json.encodeToString(DeviceRegistration.serializer(), body).hashCode()
        val fresh = clock() - prefs.getLong(KEY_REG_AT, 0L) < REGISTER_EVERY_MS && prefs.getInt(KEY_REG_HASH, 0) == hash
        if (fresh && !force) return
        r.registerDevice(token, body)
        prefs.edit().putLong(KEY_REG_AT, clock()).putInt(KEY_REG_HASH, hash).apply()
    }

    /** Çıkışta: cihazın hesapla bağı sunucuda kaldırılır (en iyi çaba), yerel yoklama durumu sıfırlanır. */
    suspend fun onSignedOut(tokenBeforeSignOut: String?) {
        mutex.withLock {
            if (tokenBeforeSignOut != null) runCatching { remote?.unregisterDevice(tokenBeforeSignOut, device.deviceId) }
            prefs.edit().remove(KEY_ETAG).remove(KEY_REG_AT).remove(KEY_REG_HASH).apply()
        }
    }

    // ------------------------------------------------------------------ bilgi

    /** Yeni teslimatları olay tablosuna yazar; yeni sayısı. */
    private suspend fun record(items: List<InboxItemDto>): Int {
        var added = 0
        val now = clock()
        for (i in items) {
            val existing = dao.incoming(i.deliveryId)
            if (existing == null) {
                val id = dao.insertIncoming(
                    SyncEventEntity(
                        type = TYPE_INBOX, payload = i.stage, dedupeKey = null, status = STATUS_PENDING, createdAt = now, updatedAt = now,
                        direction = DIR_IN, kind = i.kind, remoteId = i.deliveryId, refId = i.refId, refVersion = i.version, isNew = true,
                        // Sunucu zaten bilgi alındı aşamasını geçmişse (ör. veri yeniden) onay gerekmez
                        infoAcked = i.stage == "INFO_RECEIVED" || i.stage == "DATA_SENT",
                    ),
                )
                if (id > 0) added++
            } else if (existing.payload != i.stage && !existing.deliveredAcked) {
                dao.update(existing.copy(payload = i.stage, updatedAt = now))
            }
        }
        return added
    }

    private suspend fun ackInfo(r: RemoteStore, token: String) {
        val open = dao.incomingOpen().filter { !it.infoAcked }
        for (chunk in open.chunked(ACK_BATCH)) {
            val results = r.ackInbox(token, device.deviceId, chunk.map { AckItemDto(it.remoteId!!, "INFO_RECEIVED") })
            val now = clock()
            for (e in chunk) {
                val stage = results[e.remoteId] ?: continue
                when (stage) {
                    "NOT_FOUND" -> dao.update(e.copy(status = SyncEventEntity.STATUS_FAILED, lastError = "sunucuda yok", updatedAt = now))
                    "CANCELLED", "FAILED" -> dao.update(e.copy(status = STATUS_DONE, infoAcked = true, payload = stage, updatedAt = now))
                    else -> dao.update(e.copy(infoAcked = true, payload = stage, updatedAt = now))
                }
            }
        }
    }

    // ------------------------------------------------------------------ veri

    /** Verisi hazır olanları indirir; tamamen alınan sayısı. Sınıra takılınca durur. */
    private suspend fun fetchData(r: RemoteStore, token: String): Int {
        var received = 0
        val ready = dao.incomingOpen().filter { it.infoAcked && !it.dataReceived && (it.payload == "DATA_SENT" || it.payload == "DELIVERED") }
        for (e in ready) {
            try {
                val data = r.inboxData(token, device.deviceId, e.remoteId!!)
                val updated = when (e.kind) {
                    KIND_NOTIFICATION -> e.copy(title = data.title, body = data.body)
                    KIND_ANNOUNCEMENT -> e.copy(title = data.title, body = data.body, localPath = data.imageUrl?.let { downloadAnnouncementImage(e, it) })
                    KIND_PACKAGE -> installPackage(e, data)
                    KIND_APP_UPDATE -> e.copy(title = data.versionName, body = data.message, refVersion = (data.versionCode ?: 0L).toInt())
                    else -> e
                }.copy(dataReceived = true, data = json.encodeToString(DeliveryDataDto.serializer(), data), updatedAt = clock(), lastError = null)
                dao.update(updated)
                received++
            } catch (x: LimitReachedException) {
                // Yarım kalan zaten silindi; bugün başka indirme yapılmaz
                dao.update(e.copy(lastError = "günlük indirme sınırı: yarın yeniden", updatedAt = clock()))
                break
            } catch (x: RemoteException) {
                if (x.status == 409) continue // veri henüz hazır değil
                if (x.isNotFound) {
                    dao.update(e.copy(status = SyncEventEntity.STATUS_FAILED, lastError = "sunucuda yok", updatedAt = clock()))
                    continue
                }
                throw x
            } catch (x: IllegalArgumentException) {
                // Bozuk içerik (ör. resim çözülemedi): sunucuya bildirilir, yeniden denenmez
                dao.update(e.copy(status = SyncEventEntity.STATUS_FAILED, lastError = x.message?.take(300), updatedAt = clock()))
                runCatching { r.ackInbox(token, device.deviceId, listOf(AckItemDto(e.remoteId!!, "FAILED", x.message?.take(300)))) }
            }
        }
        return received
    }

    private suspend fun downloadAnnouncementImage(e: SyncEventEntity, url: String): String {
        announcementsDir.mkdirs()
        val tmp = File(announcementsDir, "${PackStore.safe(e.refId ?: e.remoteId!!)}.part")
        val target = File(announcementsDir, "${PackStore.safe(e.refId ?: e.remoteId!!)}.img")
        try {
            download(url, tmp)
            requireImage(tmp)
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Duyuru resmi taşınamadı")
            return target.absolutePath
        } finally {
            tmp.delete()
        }
    }

    /** Paketin bütün öğelerini geçici klasöre indirir, tamamsa modun klasörüne taşır (yarım kalırsa siler). */
    private suspend fun installPackage(e: SyncEventEntity, data: DeliveryDataDto): SyncEventEntity {
        val id = data.id ?: e.refId ?: throw IllegalArgumentException("Paket kimliği yok")
        val mode = data.mode ?: throw IllegalArgumentException("Paketin modu yok")
        val key = e.remoteId!!
        val staging = packs.staging(key)
        try {
            val items = ArrayList<PackStore.Item>(data.items.size)
            for (item in data.items) {
                val window = PackStore.safe(item.window)
                val ext = extension(item.url)
                val rel = "$window/${PackStore.safe(item.id)}.$ext"
                val f = File(staging, rel)
                f.parentFile?.mkdirs()
                download(item.url, f)
                val (w, h) = requireImage(f)
                items += PackStore.Item(item.id, item.window, item.title, item.category, rel, w, h)
            }
            val pack = packs.install(staging, PackStore.Manifest(id, data.name ?: id, mode.uppercase(), data.version ?: e.refVersion, items))
            // Kurulu paketler değişti: sunucuya bir sonraki turda bildirilir
            prefs.edit().remove(KEY_REG_AT).apply()
            return e.copy(title = data.name, localPath = pack.dir.absolutePath, handled = true, handledAt = clock(), isNew = false)
        } catch (x: Throwable) {
            packs.discard(key)
            throw x
        }
    }

    /** İndirir; günlük sınır her parçadan önce denetlenir (aşılacaksa dosya silinir, [LimitReachedException]). */
    private suspend fun download(url: String, target: File) {
        if (!url.startsWith("https://")) throw IllegalArgumentException("Yalnız https adresleri indirilir")
        downloader.open(url) { input, length ->
            if (length > MAX_FILE) throw IllegalArgumentException("Dosya çok büyük")
            if (length > 0 && policy.wouldExceedDownload(length)) throw LimitReachedException()
            var total = 0L
            target.outputStream().use { out ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    if (policy.wouldExceedDownload(buf.size.toLong())) throw LimitReachedException()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    if (total > MAX_FILE) throw IllegalArgumentException("Dosya çok büyük")
                }
            }
        }
    }

    /** Resim mi (başlığı çözülebiliyor mu); boyutları. Değilse bozuk içerik. */
    private fun requireImage(f: File): Pair<Int, Int> {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            f.delete()
            throw IllegalArgumentException("Resim çözülemedi")
        }
        if (o.outWidth > 8192 || o.outHeight > 8192) {
            f.delete()
            throw IllegalArgumentException("Resim çok büyük")
        }
        return o.outWidth to o.outHeight
    }

    private fun extension(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in setOf("webp", "png", "jpg", "jpeg")) ext else "img"
    }

    private suspend fun ackDelivered(r: RemoteStore, token: String) {
        val done = dao.incomingOpen().filter { it.dataReceived && !it.deliveredAcked }
        for (chunk in done.chunked(ACK_BATCH)) {
            val results = r.ackInbox(token, device.deviceId, chunk.map { AckItemDto(it.remoteId!!, "DELIVERED") })
            val now = clock()
            for (e in chunk) {
                val stage = results[e.remoteId] ?: continue
                if (stage == "DELIVERED" || stage == "NOT_FOUND") {
                    dao.update(e.copy(deliveredAcked = true, status = STATUS_DONE, doneAt = now, payload = stage, updatedAt = now))
                }
            }
        }
    }

    // ------------------------------------------------------------------ yerel işler

    /** Verisi gelmiş bildirimleri gösterir, güncellemeyi bildirir (ağ gerekmez). */
    suspend fun runLocalActions() {
        for (e in dao.incomingRecent(200)) {
            if (!e.dataReceived || e.handled) continue
            when (e.kind) {
                KIND_NOTIFICATION -> {
                    val shown = notifier.message(e)
                    dao.markHandled(e.id, clock())
                    if (!shown) dao.update(dao.incoming(e.remoteId!!)!!.copy(lastError = "bildirim izni yok"))
                }
                KIND_APP_UPDATE -> {
                    if (e.refVersion > device.versionCode) notifier.appUpdate(e)
                    dao.markHandled(e.id, clock())
                }
            }
        }
    }

    /** Ana sayfada gösterilen duyuru: gösterildi olarak işaretlenir. */
    suspend fun announcementShown(id: Long) = dao.markHandled(id, clock())

    suspend fun dismiss(id: Long) = dao.dismiss(id, clock())

    companion object {
        private const val TAG = "Inbox"
        const val KIND_PACKAGE = "PACKAGE"
        const val KIND_NOTIFICATION = "NOTIFICATION"
        const val KIND_ANNOUNCEMENT = "ANNOUNCEMENT"
        const val KIND_APP_UPDATE = "APP_UPDATE"
        const val DEFAULT_POLL_SEC = 15 * 60L
        const val REGISTER_EVERY_MS = 24 * 3600_000L
        const val ACK_BATCH = 100
        /** Tek resim dosyasının üst sınırı. */
        const val MAX_FILE = 20L * 1024 * 1024
        const val KEY_ETAG = "etag"
        const val KEY_NEXT = "next_poll_at"
        const val KEY_LAST = "last_poll_at"
        const val KEY_REG_AT = "registered_at"
        const val KEY_REG_HASH = "registered_hash"
    }
}
