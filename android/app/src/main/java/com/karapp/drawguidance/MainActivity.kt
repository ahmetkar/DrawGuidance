package com.karapp.drawguidance

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import com.karapp.drawguidance.data.settings.ServerConfig
import com.karapp.drawguidance.ui.navigation.DrawGuidanceNavGraph
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val serverConfig: ServerConfig by inject()
    private var saveQueue: com.karapp.drawguidance.data.repository.CanvasSaveQueue? = null
    /** v43 güncelleme bildirimine dokunularak açıldı. */
    private var updateRequested by androidx.compose.runtime.mutableStateOf(false)

    /** v34 seçili dil (ve Arapçada sağdan sola yön) etkinliğe uygulanır. */
    override fun attachBaseContext(newBase: android.content.Context) {
        val manager = com.karapp.drawguidance.i18n.LanguageManager
        super.attachBaseContext(manager.wrap(newBase))
        applyOverrideConfiguration(manager.overrideConfiguration(manager.current(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        saveQueue = get()
        applyServerUrlExtra(intent)
        updateRequested = intent?.getBooleanExtra(com.karapp.drawguidance.notify.AppNotifications.EXTRA_SHOW_UPDATE, false) == true
        enableEdgeToEdge()
        val app = application as? DrawGuidanceApp
        // v30 karşılama ekranı yalnız uygulamanın soğuk açılışında (döndürmede ya da geri gelişte tekrar gösterilmez)
        val welcome = (app?.showWelcome ?: true) && savedInstanceState == null && !intent.getBooleanExtra(EXTRA_LANGUAGE_RESTART, false)
        // v39 uygulamaya girişte (soğuk açılış) geçiş reklamı kurallara göre (ilk açılışta, Premium'da, çocukta yok)
        if (savedInstanceState == null && !intent.getBooleanExtra(EXTRA_LANGUAGE_RESTART, false)) {
            runCatching { get<com.karapp.drawguidance.data.ads.AdsCoordinator>().onAppEntry(this) }
        }
        // v40 uygulama açıldı: uzun süre kullanılmama hatırlatmasının sayacı sıfırlanır
        if (savedInstanceState == null) runCatching { get<com.karapp.drawguidance.notify.NotificationPrefs>().onAppOpened() }
        setContent {
            DrawGuidanceTheme {
                // v44 çocuk kilidi uygulamanın her yerinde
                val kidsLock = remember { runCatching { get<com.karapp.drawguidance.data.kids.KidsLockRepository>() }.getOrNull() }
                val kidsLocked by (kidsLock?.locked ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
                androidx.compose.runtime.CompositionLocalProvider(com.karapp.drawguidance.ui.kids.LocalKidsLocked provides kidsLocked) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    DrawGuidanceNavGraph(startOnHome = app?.startOnHome ?: true)
                    var showWelcome by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(welcome) }
                    if (showWelcome) com.karapp.drawguidance.ui.components.WelcomeScreen(onDone = { showWelcome = false })
                    // v37 hata/çökme raporu penceresi (uygulamanın her yerinde)
                    if (!showWelcome) com.karapp.drawguidance.ui.components.ErrorReportHost()
                    // v40 zorunlu güncelleme ve (bir kez) bildirim izni açıklaması
                    if (!showWelcome) com.karapp.drawguidance.ui.screens.settings.ForceUpdateHost()
                    if (!showWelcome) com.karapp.drawguidance.ui.screens.settings.NotificationPermissionHost(app?.askNotificationPermission ?: true)
                    // v43 yeni sürüm penceresi (Google Play uygulama içi güncelleme); bildirimine dokunulunca hemen
                    if (!showWelcome) com.karapp.drawguidance.ui.screens.settings.AppUpdateHost(
                        requested = updateRequested, enabled = app?.showAppUpdates ?: true,
                    )
                }
                }
            }
        }
    }

    /** Uygulama arka plana geçerken bekleyen çizim kaydı beklemeden yazılır (sistem uygulamayı kapatabilir). */
    override fun onStop() {
        saveQueue?.flushNow()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyServerUrlExtra(intent)
        if (intent.getBooleanExtra(com.karapp.drawguidance.notify.AppNotifications.EXTRA_SHOW_UPDATE, false)) updateRequested = true
    }

    /** `adb shell am start ... --es server_url http://192.168.1.20:4747/` ile gelen adresi kaydeder. */
    private fun applyServerUrlExtra(intent: Intent?) {
        // v39 yayın sürümünde sunucu sabit (drawapi.ahmetkar.com); adres yalnız hata ayıklama sürümünde değişir
        if (!BuildConfig.DEBUG) return
        val url = intent?.getStringExtra(EXTRA_SERVER_URL) ?: return
        val ok = serverConfig.update(url)
        com.karapp.drawguidance.diagnostics.AppLog.i("DrawGuidance", "server_url extra '$url' -> ${if (ok) serverConfig.baseUrl.value else "invalid"}")
    }

    companion object {
        const val EXTRA_SERVER_URL = "server_url"
        /** v34 dil değişince yeniden başlatma: karşılama ekranı gösterilmez. */
        const val EXTRA_LANGUAGE_RESTART = "language_restart"
    }
}
