package com.karapp.drawguidance.data.reports

import android.content.Context
import android.os.Build
import com.karapp.drawguidance.BuildConfig
import com.karapp.drawguidance.data.account.AuthProvider
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.sync.SyncEngine
import com.karapp.drawguidance.data.sync.SyncTypes
import com.karapp.drawguidance.diagnostics.AppErrors
import com.karapp.drawguidance.diagnostics.ErrorReport
import com.karapp.drawguidance.diagnostics.ErrorSink
import com.karapp.drawguidance.diagnostics.ReportPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** v37 sunucuya giden hata raporu (`POST /api/v1/reports`; ayrıntı backend/docs/v37-api.md). */
@Serializable
data class ReportBody(
    val kind: String,
    val message: String,
    val stackTrace: String,
    val feature: String,
    val appVersion: String,
    val versionCode: Int,
    val sdk: Int,
    val device: String,
    val language: String,
    val installId: String,
    val deviceTime: Long,
    val note: String? = null,
    val breadcrumbs: List<String> = emptyList(),
)

/** v37 sunucunun yanıtı (202). */
@Serializable
data class ReportAccepted(val id: String = "")

/**
 * v37 hata raporlarının gönderilmesi: rapor önce olay tablosuna yazılır (`ERROR_REPORT`), bağlantı varken hemen,
 * yoksa bağlantı gelince gönderilir. Girişliyse kimlik jetonu eklenir (yoksa adsız). Tercih ve kurulum kimliği
 * (rastgele, kişisel değil) ayar dosyasında.
 */
class ErrorReporter(
    private val context: Context,
    private val engine: SyncEngine,
    private val remote: RemoteStore,
    private val auth: AuthProvider?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ErrorSink {
    private val prefs = context.getSharedPreferences("error_reports", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _policy = MutableStateFlow(runCatching { ReportPolicy.valueOf(prefs.getString("policy", null) ?: "") }.getOrDefault(ReportPolicy.ASK))
    val policyFlow: StateFlow<ReportPolicy> = _policy.asStateFlow()
    override val policy: ReportPolicy get() = _policy.value

    /** Gönderilen rapor sayısı (Ayarlar'da gösterilir). */
    val sentCount: Int get() = prefs.getInt("sent", 0)

    val installId: String
        get() = prefs.getString("install", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("install", it).apply() }

    fun setPolicy(p: ReportPolicy) {
        _policy.value = p
        prefs.edit().putString("policy", p.name).apply()
    }

    init {
        engine.register(SyncTypes.ERROR_REPORT) { event ->
            val body = json.decodeFromString(ReportBody.serializer(), event.payload)
            // v40 sunucu giriş ister: girişsizken (çıkış yapılmış) rapor girişi bekler, yeniden denenir
            val token = runCatching { auth?.idToken() }.getOrNull() ?: throw IllegalStateException("Rapor için giriş bekleniyor")
            remote.postReport(body, token)
            prefs.edit().putInt("sent", sentCount + 1).apply()
        }
    }

    /** v40 sorun bildirmek giriş ister. */
    override val signedIn: Boolean get() = runCatching { auth?.current() != null }.getOrDefault(false)

    override fun send(report: ErrorReport, note: String?) {
        // v40 girişsiz kullanıcının raporu gönderilmez (pencere önce giriş ister; "sormadan gönder" de girişsiz göndermez)
        if (!signedIn) {
            com.karapp.drawguidance.diagnostics.AppLog.i("ErrorReporter", "giriş yapılmadığı için rapor gönderilmedi (${report.kind})")
            return
        }
        val body = body(report, note)
        scope.launch {
            AppErrors.guard("reports.enqueue", Unit) {
                engine.enqueue(SyncTypes.ERROR_REPORT, json.encodeToString(ReportBody.serializer(), body), dedupeKey = report.id)
            }
        }
    }

    fun body(report: ErrorReport, note: String?): ReportBody = ReportBody(
        kind = report.kind.name,
        message = report.message,
        stackTrace = report.stackTrace,
        feature = report.feature,
        appVersion = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        sdk = Build.VERSION.SDK_INT,
        device = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80),
        language = runCatching { com.karapp.drawguidance.i18n.LanguageManager.current(context).code }.getOrDefault("tr"),
        installId = installId,
        deviceTime = report.time,
        note = note,
        breadcrumbs = report.breadcrumbs,
    )
}
