package com.karapp.drawguidance.security

import android.content.Context
import app.talsec.rasp.security.api.SuspiciousAppInfo
import app.talsec.rasp.security.api.Talsec
import app.talsec.rasp.security.api.TalsecConfig
import app.talsec.rasp.security.api.TalsecMode
import app.talsec.rasp.security.api.ThreatListener
import com.karapp.drawguidance.BuildConfig
import com.karapp.drawguidance.diagnostics.AppLog
import kotlin.system.exitProcess

/**
 * v49 freeRASP (Talsec) çalışma zamanı koruması. Uygulama açılışında başlatılır; root, hata ayıklama, emülatör,
 * hook (Frida/Xposed), kurcalama/yeniden paketleme (imza uyuşmazlığı), resmi olmayan mağaza, otomasyon, kötü amaçlı
 * uygulamalar gibi tehditleri algılar.
 *
 * Politika (Ahmet'in seçimi): **hook ve kurcalama/geçersiz imza** algılanınca uygulama kapatılır (yalnız yayın
 * derlemesinde; hata ayıklamada yalnız günlüğe yazılır). Öteki tehditler (root, emülatör, resmi olmayan mağaza, VPN…)
 * yalnız günlüğe yazılır, uygulamayı kapatmaz — hatalı pozitiflerde kullanıcı mağdur olmaz.
 *
 * İmza sertifikası hash'leri (Base64 SHA-256): yan yüklenen sürüm için release/yükleme anahtarı, Play'den inen sürüm
 * için Play App Signing anahtarları. Hepsi geçerli olduğundan yasal kurulumlarda bütünlük tehdidi tetiklenmez.
 */
object TalsecGuard {

    private const val PACKAGE_NAME = "com.karapp.drawguidance"

    /** Güvenlik raporlarının gideceği e-posta (Talsec portalına kaydolunursa). Herkese açık geliştirici adresi. */
    private const val WATCHER_MAIL = "ahmetkar.1258@gmail.com"

    /**
     * Beklenen imza sertifikalarının Base64 SHA-256 hash'leri:
     * - release/yükleme anahtarı (yan yükleme APK'sı),
     * - Play App Signing anahtarları (Play'den inen kopya; Google yeniden imzalar).
     */
    private val SIGNING_HASHES = arrayOf(
        "QpZiHbWGMWnYLf9gNjLbgzf9XVPaNhpd/NPybipqAio=", // release / yükleme
        "hP4Ac6PMmDKMblGAA6snFOwhLbErAYIaleLf1gpYuBQ=", // Play App Signing 1
        "6ga1jTTl1Q7Qmv5GufenDATuy62bLd9PTSCOpkNVLX0=", // Play App Signing 2
        "yx/260luvXwDKFFRfHu5X+L88eoZCDDkKdNAGo4hhtA=", // Play App Signing 3
    )

    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        // Robolectric/birim testlerinde yerel güvenlik kitaplığı (androidx.security.BNatives) yüklenemez;
        // freeRASP yalnız gerçek cihazda anlamlı olduğundan JVM testlerinde atlanır.
        if (android.os.Build.FINGERPRINT == "robolectric") return
        runCatching {
            val config = TalsecConfig.Builder(PACKAGE_NAME, SIGNING_HASHES)
                .watcherMail(WATCHER_MAIL)
                // Google Play ve Huawei AppGallery zaten içeride; başka mağaza yok
                .supportedAlternativeStores(emptyArray())
                // Yayın derlemesinde tam denetim; hata ayıklamada gevşek (geliştirme araçları tehdit sayılmaz)
                .prod(!BuildConfig.DEBUG)
                // freeRASP'ın kendi geri çağrıları hook'lanır/değiştirilirse uygulamayı kapat
                .killOnBypass(!BuildConfig.DEBUG)
                .build()

            ThreatListener(threats).registerListener(context)
            Talsec.start(context, config, TalsecMode.BACKGROUND)
        }.onFailure { AppLog.e(TAG, "freeRASP başlatılamadı", it) }
    }

    /** Ciddi tehditte (yayın derlemesinde) uygulamayı kapat. */
    private fun fatal(reason: String) {
        AppLog.e(TAG, "Güvenlik tehdidi: $reason", null)
        if (BuildConfig.DEBUG) return
        runCatching { android.os.Process.killProcess(android.os.Process.myPid()) }
        exitProcess(0)
    }

    private val threats = object : ThreatListener.ThreatDetected() {
        // Kapatılan tehditler: hook ve kurcalama/geçersiz imza
        override fun onHooks() = fatal("hooks")
        override fun onAppIntegrity() = fatal("appIntegrity")

        // Yalnız günlüğe yazılanlar (kapatmaz)
        override fun onPrivilegedAccess() = AppLog.w(TAG, "privilegedAccess (root)")
        override fun onDebug() = AppLog.w(TAG, "debug")
        override fun onSimulator() = AppLog.w(TAG, "simulator")
        override fun onUnofficialStore() = AppLog.w(TAG, "unofficialStore")
        override fun onDeviceBinding() = AppLog.w(TAG, "deviceBinding")
        override fun onObfuscationIssues() = AppLog.w(TAG, "obfuscationIssues")
        override fun onAutomation() = AppLog.w(TAG, "automation")
        override fun onBootloader() = AppLog.w(TAG, "bootloader")
        override fun onUnsecureWifi() = AppLog.w(TAG, "unsecureWifi")
        override fun onMultiInstance() = AppLog.w(TAG, "multiInstance")
        override fun onTimeSpoofing() = AppLog.w(TAG, "timeSpoofing")
        override fun onLocationSpoofing() = AppLog.w(TAG, "locationSpoofing")
        override fun onMalware(packageInfo: List<SuspiciousAppInfo>) = AppLog.w(TAG, "malware: ${packageInfo.size}")
    }

    private const val TAG = "Talsec"
}
