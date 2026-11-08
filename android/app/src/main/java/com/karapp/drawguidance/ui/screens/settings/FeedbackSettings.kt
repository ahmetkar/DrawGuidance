package com.karapp.drawguidance.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.feedback.FeedbackModes
import com.karapp.drawguidance.data.feedback.FeedbackRepository
import com.karapp.drawguidance.data.feedback.FeedbackResult
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.components.SignInRequiredDialog
import com.karapp.drawguidance.ui.components.activityOrNull
import com.karapp.drawguidance.ui.components.rememberSignedIn
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** v40 modun ekrandaki adı. */
fun feedbackModeLabel(mode: String): Int = when (mode) {
    FeedbackModes.ART -> R.string.v40_fb_mode_art
    FeedbackModes.NOTES -> R.string.v40_fb_mode_notes
    FeedbackModes.COMIC -> R.string.v40_fb_mode_comic
    FeedbackModes.MODEL3D -> R.string.v40_fb_mode_3d
    FeedbackModes.SIMPLE_ART -> R.string.v40_fb_mode_simple_art
    FeedbackModes.SIMPLE_NOTES -> R.string.v40_fb_mode_simple_notes
    else -> R.string.v40_fb_mode_kids
}

/** v40 özelliğin ekrandaki adı. */
fun feedbackFeatureLabel(feature: String): Int = when (feature) {
    "brushes" -> R.string.v40_fb_f_brushes
    "layers" -> R.string.v40_fb_f_layers
    "shapes" -> R.string.v40_fb_f_shapes
    "ai" -> R.string.v40_fb_f_ai
    "export" -> R.string.v40_fb_f_export
    "pages" -> R.string.v40_fb_f_pages
    "handwriting" -> R.string.v40_fb_f_handwriting
    "text" -> R.string.v40_fb_f_text
    "templates" -> R.string.v40_fb_f_templates
    "panels" -> R.string.v40_fb_f_panels
    "balloons" -> R.string.v40_fb_f_balloons
    "characters" -> R.string.v40_fb_f_characters
    "textures" -> R.string.v40_fb_f_textures
    "colors" -> R.string.v40_fb_f_colors
    "challenge" -> R.string.v40_fb_f_challenge
    else -> R.string.v40_fb_f_mascot
}

/**
 * v40 Ayarlar → Gelişmiş → Geri bildirim: mod (ve isteğe bağlı özellik) seçilir, 1–5 puan ve yazı; "Cihaz bilgilerim ve
 * e-postam gönderilsin" izni. Giriş ister (girişsizken uyarı ve "Giriş yap").
 */
@Composable
fun FeedbackCard(repo: FeedbackRepository = koinInject()) {
    val signedIn = rememberSignedIn()
    var open by remember { mutableStateOf(false) }
    var askSignIn by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    SettingsCard(
        Icons.Filled.RateReview, stringResource(R.string.v40_fb_title), Modifier.testTag("feedback_card"),
        subtitle = stringResource(R.string.v40_fb_hint),
    ) {
        if (sent) Text(stringResource(R.string.v40_fb_sent), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.accent, modifier = Modifier.testTag("feedback_sent"))
        NeuButton(
            stringResource(R.string.v40_fb_open), { if (signedIn) open = true else askSignIn = true },
            Modifier.fillMaxWidth().testTag("feedback_open"), icon = Icons.Filled.RateReview,
        )
    }
    if (askSignIn) {
        val accountVm = org.koin.compose.viewmodel.koinViewModel<com.karapp.drawguidance.ui.screens.account.AccountViewModel>()
        val context = LocalContext.current
        SignInRequiredDialog(
            onSignIn = { context.activityOrNull()?.let { accountVm.signIn(it) } }, onDismiss = { askSignIn = false },
            text = R.string.v40_fb_signin_required,
        )
    }
    if (open) FeedbackSheet(repo, onDone = { ok -> open = false; if (ok) sent = true })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackSheet(repo: FeedbackRepository, onDone: (sent: Boolean) -> Unit) {
    val colors = NeuTheme.colors
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<String?>(null) }
    var feature by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    NeuSheet({ onDone(false) }) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp).testTag("feedback_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.v40_fb_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.v40_fb_pick_mode), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackModes.ALL.forEach { m ->
                    NeuChip(stringResource(feedbackModeLabel(m)), mode == m, { mode = m; feature = null }, Modifier.testTag("feedback_mode_${m.lowercase()}"))
                }
            }
            mode?.let { m ->
                Text(stringResource(R.string.v40_fb_pick_feature), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackModes.FEATURES[m].orEmpty().forEach { f ->
                        NeuChip(stringResource(feedbackFeatureLabel(f)), feature == f, { feature = if (feature == f) null else f }, Modifier.testTag("feedback_feature_$f"))
                    }
                }
            }
            Text(stringResource(R.string.v40_fb_rating), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { n ->
                    Icon(
                        if (n <= rating) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(R.string.v40_fb_star, n),
                        tint = if (n <= rating) colors.accent else colors.contentMuted,
                        modifier = Modifier.size(36.dp).clickable(role = Role.Button) { rating = n }.testTag("feedback_star_$n"),
                    )
                }
            }
            NeuTextField(
                text, { text = it.take(FeedbackRepository.MAX_TEXT) }, stringResource(R.string.v40_fb_text), Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.v40_fb_text_hint), singleLine = false, fieldModifier = Modifier.testTag("feedback_text"),
            )
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { consent = !consent }.testTag("feedback_consent"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(consent, { consent = it })
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.v40_fb_consent), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.v40_fb_consent_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                }
            }
            error?.let { Text(stringResource(it), color = colors.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("feedback_error")) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton(stringResource(R.string.cancel_button), { onDone(false) }, Modifier.weight(1f).testTag("feedback_cancel"))
                NeuButton(
                    stringResource(R.string.v40_fb_send), {
                        val m = mode ?: return@NeuButton
                        busy = true
                        scope.launch {
                            val r = repo.submit(m, feature, rating, text, consent)
                            busy = false
                            when (r) {
                                FeedbackResult.QUEUED -> onDone(true)
                                FeedbackResult.NOT_SIGNED_IN -> error = R.string.v40_fb_signin_required
                                FeedbackResult.INVALID -> error = R.string.v40_fb_invalid
                            }
                        }
                    },
                    Modifier.weight(1f).testTag("feedback_send"), primary = true, enabled = !busy && mode != null && rating in 1..5,
                    icon = Icons.AutoMirrored.Filled.Send,
                )
            }
        }
    }
}
