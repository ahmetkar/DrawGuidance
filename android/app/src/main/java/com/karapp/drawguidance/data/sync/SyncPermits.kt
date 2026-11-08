package com.karapp.drawguidance.data.sync

import android.content.Context
import com.karapp.drawguidance.data.account.AuthProvider
import com.karapp.drawguidance.data.remote.v1.RemoteException
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.remote.v1.RemoteSyncPermit
import com.karapp.drawguidance.diagnostics.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

/** v40 içerik eşitleme izninin sonucu. */
sealed interface PermitDecision {
    /** Sunucu izin verdi (ya da eski sunucuda izin ucu yok): katalog şimdi indirilir. */
    data object Allowed : PermitDecision

    /** İzin yok: eşitleme [delayMs] sonra (sunucu saatine göre [nextSlotStart]) yeniden denenir. */
    data class Deferred(val delayMs: Long, val nextSlotStart: Long) : PermitDecision
}

/**
 * v40 içerik eşitleme izni (`GET /api/v1/sync/permit`). Sunucu kullanıcıları 1000 kişilik gruplara böler; her grup
 * 24 saat içinde 10 dakikalık kendi diliminde katalog (şekiller, vektörler, yapay zekâ ağları) indirme izni alır.
 * İzin yoksa katalog eşitlemesi WorkManager ile sunucunun söylediği dilime ertelenir ([schedule]).
 *
 * - Girişliyse Firebase jetonu, değilse kuruluma özgü rastgele cihaz kimliği gönderilir (kişisel değil).
 * - Bekleme süresi sunucunun saatiyle hesaplanır (cihaz saati yanlış olsa da dilim kaçmaz) ve dilimin ilk yarısına
 *   yayılır (aynı gruptaki cihazlar aynı saniyede gelmesin).
 * - Eski sunucuda uç yoksa (404) izin verilmiş sayılır. Hesap verileri bu izni hiç beklemez ([SyncTypes.ACCOUNT]).
 */
class SyncPermits(
    context: Context,
    private val remote: RemoteStore?,
    private val auth: AuthProvider?,
    /** Erteleme: katalog eşitlemesi verilen süre sonra yeniden denensin (uygulamada WorkManager). */
    private val schedule: (delayMs: Long) -> Unit,
    private val random: Random = Random.Default,
) {
    private val prefs = context.getSharedPreferences("sync_permit", Context.MODE_PRIVATE)

    /** Kuruluma özgü cihaz kimliği (girişsiz izin isteği için; rastgele UUID). */
    val deviceId: String
        get() = prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }

    private val _next = MutableStateFlow<Long?>(prefs.getLong("next", 0L).takeIf { it > 0 })
    /** Ertelenen içerik eşitlemesinin zamanı (cihaz saatiyle, ms; yoksa null). Ayarlar'da gösterilir. */
    val nextSlot: StateFlow<Long?> = _next.asStateFlow()

    /** İzni sorar; izin yoksa eşitlemeyi dilime erteler. Ağ hatası istisna olarak döner (iş sonra yeniden denenir). */
    suspend fun check(now: Long = System.currentTimeMillis()): PermitDecision {
        val r = remote ?: return PermitDecision.Allowed
        val token = runCatching { auth?.takeIf { it.current() != null }?.idToken(false) }.getOrNull()
        val permit = try {
            r.syncPermit(token, if (token == null) deviceId else null)
        } catch (e: RemoteException) {
            // Jeton reddedildiyse cihaz kimliğiyle bir kez daha
            if (e.isUnauthorized && token != null) r.syncPermit(null, deviceId) else throw e
        } ?: return PermitDecision.Allowed
        val d = decide(permit, now, random)
        if (d is PermitDecision.Deferred) {
            prefs.edit().putLong("next", d.nextSlotStart).apply()
            _next.value = d.nextSlotStart
            runCatching { schedule(d.delayMs) }.onFailure { AppLog.w(TAG, "eşitleme dilimi planlanamadı: ${it.message}") }
            AppLog.i(TAG, "içerik eşitlemesi ${d.delayMs / 60_000} dk sonraya (dilim ${permit.slot}) ertelendi")
        } else {
            prefs.edit().remove("next").apply()
            _next.value = null
        }
        return d
    }

    companion object {
        private const val TAG = "SyncPermit"

        /**
         * Saf karar: izinliyse [PermitDecision.Allowed]; değilse sonraki dilime kadar bekleme (sunucu saatine göre) +
         * dilimin ilk yarısında rastgele kayma. Zaman alanları okunamazsa bir saat sonra yeniden sorulur.
         */
        fun decide(p: RemoteSyncPermit, now: Long, random: Random = Random.Default): PermitDecision {
            if (p.allowed) return PermitDecision.Allowed
            val next = RemoteSyncPermit.time(p.nextSlotStart)
            val server = RemoteSyncPermit.time(p.serverTime) ?: now
            if (next == null) return PermitDecision.Deferred(HOUR, now + HOUR)
            val slotMs = (p.slotLengthSeconds.coerceAtLeast(1) * 1000L)
            val jitter = if (slotMs > 2) random.nextLong(0, slotMs / 2) else 0L
            val wait = (next - server).coerceAtLeast(0) + jitter
            return PermitDecision.Deferred(wait, now + wait)
        }

        private const val HOUR = 3_600_000L
    }
}
