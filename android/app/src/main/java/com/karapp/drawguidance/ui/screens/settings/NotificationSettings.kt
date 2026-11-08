package com.karapp.drawguidance.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.karapp.drawguidance.BuildConfig
import com.karapp.drawguidance.R
import com.karapp.drawguidance.notify.AppNotifications
import com.karapp.drawguidance.notify.NotificationPrefs
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** v40 Android 13+ bildirim izni: önce uygulama içinde nazik açıklama, sonra sistemin izin penceresi. */
@Composable
fun NotificationPermissionDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = NeuTheme.colors.background,
        modifier = Modifier.testTag("notif_permission_explain"),
        icon = { Icon(Icons.Filled.NotificationsActive, null, tint = NeuTheme.colors.accent) },
        title = { Text(stringResource(R.string.v40_notif_permission_title)) },
        text = { Text(stringResource(R.string.v40_notif_permission_text)) },
        confirmButton = { TextButton(onClick = onAllow, modifier = Modifier.testTag("notif_permission_allow")) { Text(stringResource(R.string.v40_notif_permission_allow)) } },
        dismissButton = { TextButton(onClick = onLater, modifier = Modifier.testTag("notif_permission_later")) { Text(stringResource(R.string.v40_notif_permission_later)) } },
    )
}

/** Sistem izin isteği (Android 13+); izin verilince [onResult]. */
@Composable
private fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }
    return { if (Build.VERSION.SDK_INT >= 33) runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } else onResult(true) }
}

/**
 * v40 Ayarlar → Genel → Bildirimler: güncelleme bildirimi ve uzun süre kullanılmama hatırlatması ayrı ayrı açılır/
 * kapanır; Android 13+ izin yoksa "Bildirimlere izin ver" (önce açıklama).
 */
@Composable
fun NotificationSettingsCard(prefs: NotificationPrefs = koinInject()) {
    val context = LocalContext.current
    val updates by prefs.updatesEnabled.collectAsState()
    val inactivity by prefs.inactivityEnabled.collectAsState()
    var granted by remember { mutableStateOf(AppNotifications.permissionGranted(context)) }
    var explain by remember { mutableStateOf(false) }
    // v43 sonuç izin tablosuna yazılır; "bir daha sorma" dendiyse sistem penceresi açılmaz, uygulama ayarları açılır
    val permissions = koinInject<com.karapp.drawguidance.data.permissions.PermissionRepository>()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var blocked by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(granted) {
        if (Build.VERSION.SDK_INT >= 33) blocked = permissions.get(Manifest.permission.POST_NOTIFICATIONS)?.permanentlyDenied == true && !granted
    }
    val request = rememberNotificationPermissionRequest { ok ->
        granted = ok || AppNotifications.permissionGranted(context)
        val activity = context as? android.app.Activity
        if (Build.VERSION.SDK_INT >= 33) scope.launch {
            val again = granted || (activity != null && androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))
            permissions.record(Manifest.permission.POST_NOTIFICATIONS, granted, again)
            blocked = !granted && !again
        }
    }
    SettingsCard(Icons.Filled.NotificationsActive, stringResource(R.string.v40_notif_title), Modifier.testTag("notifications_card"), subtitle = stringResource(R.string.v40_notif_hint)) {
        SettingSwitchRow(stringResource(R.string.v40_notif_updates), stringResource(R.string.v40_notif_updates_hint), updates, prefs::setUpdates, divider = false)
        SettingSwitchRow(stringResource(R.string.v40_notif_inactivity), stringResource(R.string.v40_notif_inactivity_hint), inactivity, prefs::setInactivity)
        if (!granted) {
            Text(stringResource(R.string.v40_notif_permission_missing), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.danger)
            NeuButton(stringResource(R.string.v40_notif_permission_allow), { explain = true }, Modifier.fillMaxWidth().testTag("notif_permission_open"), icon = Icons.Filled.NotificationsActive)
        }
    }
    if (explain) NotificationPermissionDialog(
        onAllow = {
            explain = false
            prefs.permissionExplained = true
            if (blocked) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            } else request()
        },
        onLater = { explain = false },
    )
}

/**
 * v43 ilk girişte gerekli Android izinleri sistemin kendi penceresiyle istenir (yalnız bir kez; şimdilik Android 13+
 * bildirim izni) ve sonucu veritabanına yazılır ([com.karapp.drawguidance.data.permissions.PermissionRepository]).
 * Reddedilirse Ayarlar → Bildirimler'den yeniden istenebilir ("bir daha sorma" dendiyse uygulama ayarları açılır).
 * [enabled]: eski testlerde kapalı.
 */
@Composable
fun NotificationPermissionHost(
    enabled: Boolean,
    prefs: NotificationPrefs = koinInject(),
    permissions: com.karapp.drawguidance.data.permissions.PermissionRepository = koinInject(),
) {
    if (!enabled) return
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var asked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val activity = context as? android.app.Activity
        scope.launch {
            for ((name, granted) in result) {
                val canAskAgain = granted || (activity != null && androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, name))
                permissions.record(name, granted, canAskAgain)
            }
            prefs.permissionExplained = true
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (asked) return@LaunchedEffect
        asked = true
        val ask = permissions.toAskOnFirstLaunch()
        if (ask.isNotEmpty()) runCatching { launcher.launch(ask.toTypedArray()) }
    }
}

/**
 * v40 zorunlu güncelleme: yüklü sürüm sunucunun desteklediği en eski sürümden küçükse açılışta kapatılamayan pencere
 * ("Güncelle" Play Store'u açar).
 */
@Composable
fun ForceUpdateHost(prefs: NotificationPrefs = koinInject(), installed: Long = BuildConfig.VERSION_CODE.toLong()) {
    val min by prefs.minSupported.collectAsState()
    if (min <= 0 || installed >= min) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = NeuTheme.colors.background,
        modifier = Modifier.testTag("force_update"),
        icon = { Icon(Icons.Filled.SystemUpdate, null, tint = NeuTheme.colors.accent) },
        title = { Text(stringResource(R.string.v40_update_required_title)) },
        text = { Text(prefs.message ?: stringResource(R.string.v40_update_required_text)) },
        confirmButton = {
            TextButton(onClick = { runCatching { context.startActivity(AppNotifications.storeIntent(context)) } }, modifier = Modifier.testTag("force_update_open")) {
                Text(stringResource(R.string.v40_update_now))
            }
        },
    )
}
