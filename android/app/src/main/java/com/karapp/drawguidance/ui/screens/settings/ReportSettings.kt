package com.karapp.drawguidance.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.reports.ErrorReporter
import com.karapp.drawguidance.diagnostics.AppErrors
import com.karapp.drawguidance.diagnostics.ReportPolicy
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.activityOrNull
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import org.koin.compose.koinInject

/**
 * v37 Ayarlar → Gelişmiş → Hata raporları: çökme ve hatalarda rapor penceresi açılsın mı (her seferinde sor,
 * sormadan gönder, gönderme) ve elle "Sorun bildir".
 */
@Composable
fun ErrorReportsCard(reporter: ErrorReporter = koinInject()) {
    val policy by reporter.policyFlow.collectAsState()
    // v40 sorun bildirmek giriş ister: girişsizken uyarı ve "Giriş yap"
    val signedIn = com.karapp.drawguidance.ui.components.rememberSignedIn()
    var askSignIn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (askSignIn) {
        val accountVm = org.koin.compose.viewmodel.koinViewModel<com.karapp.drawguidance.ui.screens.account.AccountViewModel>()
        val context = androidx.compose.ui.platform.LocalContext.current
        com.karapp.drawguidance.ui.components.SignInRequiredDialog(
            onSignIn = { context.activityOrNull()?.let { accountVm.signIn(it) } },
            onDismiss = { askSignIn = false },
        )
    }
    SettingsCard(
        Icons.Filled.BugReport, stringResource(R.string.reports_title), Modifier.testTag("reports_card"),
        subtitle = stringResource(R.string.reports_hint),
    ) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ReportPolicy.ASK to R.string.reports_ask, ReportPolicy.AUTO to R.string.reports_auto, ReportPolicy.NEVER to R.string.reports_never,
            ).forEach { (p, label) ->
                NeuChip(stringResource(label), policy == p, onClick = { reporter.setPolicy(p) }, modifier = Modifier.testTag("reports_policy_${p.name.lowercase()}"))
            }
        }
        Text(stringResource(R.string.reports_sent_count, reporter.sentCount), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
        NeuButton(
            stringResource(R.string.reports_report), { if (signedIn) AppErrors.userReport("settings") else askSignIn = true }, Modifier.fillMaxWidth().testTag("reports_user_report"),
            icon = Icons.Filled.Feedback,
        )
    }
}
