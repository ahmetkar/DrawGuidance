package com.karapp.drawguidance.ui.screens.settings

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.karapp.drawguidance.BuildConfig
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.local.SyncEventDao
import com.karapp.drawguidance.notify.AppNotifications
import com.karapp.drawguidance.notify.NotificationPrefs
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * v43 Google Play güncellemesi. Uygulamanın kendisi APK indirmez/yüklemez: güncelleme yalnız Google Play'den gelir
 * (imza ve bütünlük Play'in denetiminde).
 *
 * - Yeni sürüm bilgisi iki yoldan gelir: girişli cihazlarda sunucunun gelen kutusu (sunucu cihazın sürümünü bilir,
 *   yalnız eski sürümdekilere gönderir; olay tablosunda `APP_UPDATE`), herkeste günlük sürüm denetimi
 *   (`GET /api/v1/app/version`).
 * - Pencerede **Güncelle**: Play'in uygulama içi güncelleme akışı (Play Core In-App Updates) açılır; Play bu cihaz için
 *   güncelleme göstermiyorsa (ör. yan yükleme, eski Play) Play Store sayfası açılır.
 * - **Sonra**: bu oturumda yeniden gösterilmez; gelen kutusundaki güncelleme kapatılmış sayılır.
 */
object PlayUpdater {
    /** Play akışını başlatır; başlatılamadıysa Play Store sayfasını açar. */
    fun start(activity: Activity?, context: Context, launcher: ActivityResultLauncher<IntentSenderRequest>?) {
        if (activity == null || launcher == null) {
            openStore(context)
            return
        }
        val manager = runCatching { AppUpdateManagerFactory.create(activity) }.getOrNull() ?: return openStore(context)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            val type = when {
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
                else -> null
            }
            if (available && type != null) {
                val started = runCatching { manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build()) }.getOrDefault(false)
                if (!started) openStore(context)
            } else {
                openStore(context)
            }
        }.addOnFailureListener { openStore(context) }
    }

    fun openStore(context: Context) { runCatching { context.startActivity(AppNotifications.storeIntent(context)) } }
}

/**
 * v43 güncelleme penceresi: yüklü sürümden yeni bir sürüm biliniyorsa (gelen kutusu ya da sürüm denetimi) açılışta
 * bir kez, güncelleme bildirimine dokunulunca ([requested]) hemen gösterilir. Zorunlu güncelleme ayrı pencerededir
 * ([ForceUpdateHost]).
 */
@Composable
fun AppUpdateHost(
    requested: Boolean,
    enabled: Boolean = true,
    dao: SyncEventDao = koinInject(),
    prefs: NotificationPrefs = koinInject(),
    installed: Long = BuildConfig.VERSION_CODE.toLong(),
) {
    if (!enabled) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val event by dao.observeAppUpdate().collectAsState(initial = null)
    var dismissedThisSession by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val fromInbox = event?.takeIf { it.refVersion > installed }
    val latest = maxOf(fromInbox?.refVersion?.toLong() ?: 0L, prefs.latestVersion)
    val show = remember(latest, dismissedThisSession, requested) { latest > installed && (!dismissedThisSession || requested) }
    // Zorunlu güncelleme penceresi açıksa bu gösterilmez
    if (!show || (prefs.minSupported.collectAsState().value > installed)) return
    val message = fromInbox?.body ?: prefs.message
    AlertDialog(
        onDismissRequest = { dismissedThisSession = true },
        containerColor = NeuTheme.colors.background,
        modifier = Modifier.testTag("app_update_dialog"),
        icon = { Icon(Icons.Filled.SystemUpdate, null, tint = NeuTheme.colors.accent) },
        title = { Text(stringResource(R.string.v43_update_title)) },
        text = {
            Text(
                listOfNotNull(
                    fromInbox?.title?.let { stringResource(R.string.v43_update_version, it) },
                    message ?: stringResource(R.string.v43_update_text),
                ).joinToString("\n\n"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    dismissedThisSession = true
                    PlayUpdater.start(context as? Activity, context, launcher)
                },
                modifier = Modifier.testTag("app_update_now"),
            ) { Text(stringResource(R.string.v40_update_now)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    dismissedThisSession = true
                    fromInbox?.let { e -> scope.launch { dao.dismiss(e.id, System.currentTimeMillis()) } }
                },
                modifier = Modifier.testTag("app_update_later"),
            ) { Text(stringResource(R.string.v40_notif_permission_later)) }
        },
    )
}
