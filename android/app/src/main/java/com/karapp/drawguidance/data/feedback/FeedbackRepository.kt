package com.karapp.drawguidance.data.feedback

import android.os.Build
import com.karapp.drawguidance.BuildConfig
import com.karapp.drawguidance.data.account.AuthProvider
import com.karapp.drawguidance.data.remote.v1.FeedbackBody
import com.karapp.drawguidance.data.remote.v1.FeedbackDevice
import com.karapp.drawguidance.data.remote.v1.RemoteException
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.sync.PermanentSyncException
import com.karapp.drawguidance.data.sync.SyncEngine
import com.karapp.drawguidance.data.sync.SyncTypes
import kotlinx.serialization.json.Json
import java.util.UUID

/** v40 geri bildirimin modları (sunucudaki `mode`) ve her modun özellikleri (isteğe bağlı `feature`). */
object FeedbackModes {
    const val ART = "ART"
    const val NOTES = "NOTES"
    const val COMIC = "COMIC"
    const val MODEL3D = "MODEL3D"
    const val SIMPLE_ART = "SIMPLE_ART"
    const val SIMPLE_NOTES = "SIMPLE_NOTES"
    const val KIDS = "KIDS"

    /** Mod → özellik anahtarları (ekranda sırayla). */
    val FEATURES: Map<String, List<String>> = linkedMapOf(
        ART to listOf("brushes", "layers", "shapes", "ai", "export"),
        NOTES to listOf("pages", "handwriting", "text", "templates", "export"),
        COMIC to listOf("panels", "balloons", "characters", "export"),
        MODEL3D to listOf("shapes", "textures", "export"),
        SIMPLE_ART to listOf("brushes", "colors", "shapes"),
        SIMPLE_NOTES to listOf("handwriting", "pages", "text"),
        KIDS to listOf("challenge", "mascot", "colors"),
    )

    val ALL: List<String> = FEATURES.keys.toList()
}

/** v40 gönderme sonucu. */
enum class FeedbackResult { QUEUED, NOT_SIGNED_IN, INVALID }

/**
 * v40 mod başına geri bildirim (Ayarlar → Geri bildirim). Hesap verisidir: sunucunun eşitleme iznini beklemez; önce
 * olay tablosuna (`FEEDBACK`) yazılır, bağlantı varsa hemen, yoksa bağlantı gelince gönderilir (yeniden denenir).
 *
 * Giriş zorunludur. **Cihaz bilgisi** (model, Android sürümü, uygulama sürümü, dil) ve **e-posta** (sunucu jetondan
 * okur) yalnız kullanıcı izin verdiyse gönderilir/saklanır; izin yoksa hiçbiri gönderilmez.
 */
class FeedbackRepository(
    private val engine: SyncEngine,
    private val remote: RemoteStore?,
    private val auth: AuthProvider?,
    private val locale: () -> String = { java.util.Locale.getDefault().toLanguageTag() },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        engine.register(SyncTypes.FEEDBACK) { event ->
            val r = remote ?: throw PermanentSyncException("Sunucu yapılandırılmadı")
            val body = json.decodeFromString(FeedbackBody.serializer(), event.payload)
            var token = auth?.idToken(false) ?: throw IllegalStateException("Geri bildirim için giriş bekleniyor")
            try {
                r.postFeedback(token, body)
            } catch (e: RemoteException) {
                if (!e.isUnauthorized) throw e
                token = auth.idToken(true) ?: throw e
                r.postFeedback(token, body)
            }
        }
    }

    val signedIn: Boolean get() = runCatching { auth?.current() != null }.getOrDefault(false)

    suspend fun submit(mode: String, feature: String?, rating: Int, text: String, consent: Boolean): FeedbackResult {
        if (!signedIn) return FeedbackResult.NOT_SIGNED_IN
        val body = build(mode, feature, rating, text, consent, device()) ?: return FeedbackResult.INVALID
        engine.enqueue(SyncTypes.FEEDBACK, json.encodeToString(FeedbackBody.serializer(), body), dedupeKey = "feedback:" + UUID.randomUUID())
        return FeedbackResult.QUEUED
    }

    /** Bu cihazın bilgisi (yalnız izinle gönderilir). */
    fun device(): FeedbackDevice = FeedbackDevice(
        model = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80),
        androidVersion = Build.VERSION.RELEASE.orEmpty() + " (API ${Build.VERSION.SDK_INT})",
        appVersion = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        locale = locale(),
    )

    companion object {
        const val MAX_TEXT = 5000

        /**
         * Gövde (saf, birim testli): mod ve puan (1–5) geçerli değilse null; özellik o modun değilse atılır; yazı
         * kırpılır ve en çok [MAX_TEXT] karakter. İzin yoksa [device] eklenmez.
         */
        fun build(mode: String, feature: String?, rating: Int, text: String, consent: Boolean, device: FeedbackDevice): FeedbackBody? {
            if (mode !in FeedbackModes.ALL || rating !in 1..5) return null
            val f = feature?.takeIf { it in FeedbackModes.FEATURES[mode].orEmpty() }
            val t = text.trim().take(MAX_TEXT).ifEmpty { null }
            return FeedbackBody(mode = mode, feature = f?.let { "${mode.lowercase()}.$it" }, rating = rating, text = t, consent = consent,
                device = if (consent) device else null)
        }
    }
}
