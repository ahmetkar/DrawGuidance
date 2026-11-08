package com.karapp.drawguidance.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import com.karapp.drawguidance.ui.theme.NeuFieldShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.MainActivity
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.settings.LanguageRepository
import com.karapp.drawguidance.i18n.AppLanguage
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * v34 Ayarlar → Dil: yedi dil kendi adlarıyla; seçili dil işaretli. v36: açılır seçim kutusu. İlk açılışta dil sistemin dili ve ülkesine göre
 * kendiliğinden seçilir (kart bunu söyler). Dil değişince uygulama yeniden başlatılır ki bütün ekranlar, hazır
 * içerik adları ve logo yeni dile geçsin.
 */
@Composable
fun LanguageSettingsCard(repo: LanguageRepository = koinInject()) {
    val colors = NeuTheme.colors
    val settings by repo.settings.collectAsState(initial = null)
    val active = repo.active
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<AppLanguage?>(null) }
    var open by remember { mutableStateOf(false) }
    // v36 dil, tasarıma uygun açılır seçim kutusundan seçilir (dil kendi adıyla, seçili dil işaretli)
    SettingsCard(Icons.Filled.Translate, stringResource(R.string.language_title), Modifier.testTag("language_card")) {
        val auto = settings?.auto ?: true
        val system = listOfNotNull(settings?.systemLanguage, settings?.systemCountry?.takeIf { it.isNotBlank() }).joinToString("-")
        Box(Modifier.fillMaxWidth()) {
            NeuSurface(
                modifier = Modifier.fillMaxWidth().testTag("language_select"), shape = NeuFieldShape, elevation = 3.dp, inset = true,
                onClick = { open = true },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        active.nativeName, translate = false, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                        color = colors.content, modifier = Modifier.weight(1f).testTag("language_current"),
                    )
                    Icon(if (open) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, null, tint = colors.accent)
                }
            }
            DropdownMenu(
                expanded = open, onDismissRequest = { open = false },
                modifier = Modifier.background(colors.background).testTag("language_menu"),
            ) {
                AppLanguage.entries.forEach { lang ->
                    val selected = lang == active
                    DropdownMenuItem(
                        text = {
                            Text(
                                lang.nativeName, translate = false, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) colors.accent else colors.content,
                            )
                        },
                        trailingIcon = { if (selected) Icon(Icons.Filled.CheckCircle, null, tint = colors.accent, modifier = Modifier.size(20.dp)) },
                        onClick = { open = false; if (!selected) pending = lang },
                        modifier = Modifier.semantics { this.selected = selected }.testTag("language_${lang.code}"),
                    )
                }
            }
        }
        Text(
            if (auto) stringResource(R.string.language_auto_hint, system.ifEmpty { "—" }) else stringResource(R.string.language_chosen_hint),
            style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
        )
    }
    pending?.let { lang ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.language_change_title)) },
            text = { Text(stringResource(R.string.language_change_text, lang.nativeName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        scope.launch {
                            repo.select(lang)
                            context.findActivity()?.let { AppRestarter.restart(it) }
                        }
                    },
                    modifier = Modifier.testTag("language_confirm"),
                ) { Text(stringResource(R.string.language_change_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * v34 dil değişince uygulama yeniden başlatılır: kayıt kuyruğu boşaltılır, ana etkinlik görev baştan kurularak açılır
 * (karşılama ekranı gösterilmez) ve süreç kapanır; yeni süreç seçili dille açılır. Robolectric testlerinde yalnız
 * etkinlik yeniden kurulur.
 */
object AppRestarter {
    fun restart(activity: Activity) {
        runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<com.karapp.drawguidance.data.repository.CanvasSaveQueue>()?.flushNow() }
        if (android.os.Build.FINGERPRINT == "robolectric") {
            activity.recreate()
            return
        }
        val intent = (activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: Intent(activity, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(MainActivity.EXTRA_LANGUAGE_RESTART, true)
        activity.startActivity(intent)
        activity.finishAffinity()
        Handler(Looper.getMainLooper()).postDelayed({ Runtime.getRuntime().exit(0) }, 250)
    }
}
