package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.diagnostics.AppErrors
import com.karapp.drawguidance.diagnostics.ErrorReport
import com.karapp.drawguidance.diagnostics.ReportKind
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v37 uygulamanın her yerinde rapor penceresi: [AppErrors.prompt] dolunca (çökmeden sonraki açılış, kullanıcının
 * fark ettiği hata ya da "Sorun bildir") alttan açılır. Ne olduğu, isteğe bağlı not, açılır ayrıntılar ve
 * Gönder / Gönderme düğmeleri.
 */
@Composable
fun ErrorReportHost() {
    val report by AppErrors.prompt.collectAsState()
    val context = LocalContext.current
    val signedIn = rememberSignedIn()
    // v40 girişsizken "Sorun bildir": giriş uyarısı; "Giriş yap" ile başlayan girişin pencereleri (doğum tarihi, sonuç)
    var askSignIn by remember { mutableStateOf(false) }
    var signInStarted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    report?.let { r ->
        ErrorReportSheet(
            r,
            onSend = { note -> if (signedIn) AppErrors.send(note, context.applicationContext) else askSignIn = true },
            onDismiss = { AppErrors.dismiss(context.applicationContext) },
            signedIn = signedIn,
        )
    }
    if (askSignIn) SignInRequiredDialog(onSignIn = { signInStarted = true }, onDismiss = { askSignIn = false })
    if (signInStarted) RootSignIn(onFinished = { signInStarted = false })
}

/** v40 uygulama kökünde Google ile giriş (etkinliğe bağlı hesap görünüm modeli; doğum tarihi ve sonuç pencereleri). */
@Composable
private fun RootSignIn(onFinished: () -> Unit) {
    val vm = org.koin.compose.viewmodel.koinViewModel<com.karapp.drawguidance.ui.screens.account.AccountViewModel>()
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    var launched by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            context.activityOrNull()?.let { vm.signIn(it) }
        }
    }
    com.karapp.drawguidance.ui.screens.account.AccountDialogs(vm)
    // Giriş bitti ve pencereler kapandı: kök giriş kaldırılır
    if (launched && !state.busy && state.pendingBirth == null && state.signInOutcome == null) androidx.compose.runtime.SideEffect { onFinished() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorReportSheet(report: ErrorReport, onSend: (String) -> Unit, onDismiss: () -> Unit, signedIn: Boolean = true) {
    val colors = NeuTheme.colors
    var note by remember(report.id) { mutableStateOf("") }
    var details by remember(report.id) { mutableStateOf(false) }
    val user = report.kind == ReportKind.USER
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp).testTag("error_report_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.BugReport, null, tint = colors.accent)
                }
                Text(
                    stringResource(
                        when (report.kind) {
                            ReportKind.CRASH -> R.string.err_title_crash
                            ReportKind.ERROR -> R.string.err_title_error
                            ReportKind.USER -> R.string.err_title_user
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("error_report_title"),
                )
            }
            Text(stringResource(if (user) R.string.err_text_user else R.string.err_text), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
            // v40 aynı hata bu oturumda birden çok kez olduysa sayısı
            val repeats = if (user) 0 else AppErrors.repeatCount(report.signature)
            if (repeats > 1) Text(stringResource(R.string.v40_err_repeats, repeats), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.testTag("error_report_repeats"))
            // v40 girişsiz kullanıcıya: bildirmek için giriş gerekir
            if (!signedIn) Text(stringResource(R.string.v40_err_sign_in_hint), style = MaterialTheme.typography.bodySmall, color = colors.danger, modifier = Modifier.testTag("error_report_sign_in_hint"))
            NeuTextField(
                note, { note = it.take(1000) }, stringResource(R.string.err_note), Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.err_note_hint), singleLine = false, fieldModifier = Modifier.testTag("error_report_note"),
            )
            if (!user) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { details = !details }.padding(vertical = 4.dp).testTag("error_report_details_toggle"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(if (details) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = colors.contentMuted)
                    Text(stringResource(R.string.err_details), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
                }
                if (details) {
                    NeuSurface(modifier = Modifier.fillMaxWidth(), elevation = 3.dp, inset = true) {
                        Text(
                            "${report.feature}: ${report.message}\n\n" + report.stackTrace.lineSequence().take(14).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.contentMuted,
                            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState()).padding(12.dp).testTag("error_report_details"),
                        )
                    }
                }
            }
            // v42 sunucusuz gönderim: kopyala ya da paylaş (e-posta); giriş ve bağlantı gerekmez
            if (!user) {
                val context = LocalContext.current
                var copied by remember(report.id) { mutableStateOf(false) }
                Text(stringResource(R.string.v42_err_share_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeuButton(
                        stringResource(R.string.v42_err_copy),
                        { copied = com.karapp.drawguidance.diagnostics.CrashExport.copy(context, com.karapp.drawguidance.diagnostics.CrashExport.text(context, report, note)) },
                        Modifier.weight(1f).testTag("error_report_copy"), icon = Icons.Filled.ContentCopy,
                    )
                    NeuButton(
                        stringResource(R.string.v42_err_share),
                        { com.karapp.drawguidance.diagnostics.CrashExport.share(context, com.karapp.drawguidance.diagnostics.CrashExport.text(context, report, note)) },
                        Modifier.weight(1f).testTag("error_report_share"), icon = Icons.Filled.Share,
                    )
                }
                if (copied) Text(stringResource(R.string.v42_err_copied), style = MaterialTheme.typography.bodySmall, color = colors.accent, modifier = Modifier.testTag("error_report_copied"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // v40 "Kapat" ve "Sorun bildir"
                NeuButton(stringResource(R.string.v40_err_close), onDismiss, Modifier.weight(1f).testTag("error_report_dismiss"))
                NeuButton(
                    stringResource(R.string.v40_err_report), { onSend(note) }, Modifier.weight(1f).testTag("error_report_send"),
                    primary = true, enabled = !user || note.isNotBlank(), icon = Icons.AutoMirrored.Filled.Send,
                )
            }
            Text(
                stringResource(R.string.err_policy_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
                fontWeight = FontWeight.Normal, modifier = Modifier.alpha(0.85f),
            )
        }
    }
}
