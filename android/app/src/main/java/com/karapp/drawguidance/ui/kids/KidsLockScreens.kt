package com.karapp.drawguidance.ui.kids

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.kids.KidsLockRepository
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** v44 şifre penceresinin amacı. */
enum class PinPurpose { ENABLE, SET, DISABLE, CHANGE }

/**
 * v44 4 haneli ebeveyn şifresi penceresi (sayı tuş takımı; klavye açılmaz). Yeni şifrede iki kez sorulur;
 * değiştirmede önce eski şifre. Sonuç [KidsLockRepository] ile denetlenir; yanlış şifrede ve beklemede ileti çıkar.
 */
@Composable
fun KidsPinSheet(purpose: PinPurpose, onDone: () -> Unit, onDismiss: () -> Unit, repo: KidsLockRepository = koinInject()) {
    val colors = NeuTheme.colors
    val scope = rememberCoroutineScope()
    // Adımlar: (CHANGE: eski) → yeni → yeni (tekrar)
    var step by remember { mutableStateOf(if (purpose == PinPurpose.CHANGE || purpose == PinPurpose.DISABLE) 0 else 1) }
    var digits by remember { mutableStateOf("") }
    var old by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val title = when (purpose) {
        PinPurpose.ENABLE, PinPurpose.SET -> R.string.v44_pin_title_set
        PinPurpose.DISABLE -> R.string.v44_pin_title_disable
        PinPurpose.CHANGE -> R.string.v44_pin_title_change
    }
    val prompt = when (step) {
        0 -> if (purpose == PinPurpose.CHANGE) R.string.v44_pin_enter_old else R.string.v44_pin_enter
        1 -> R.string.v44_pin_enter_new
        else -> R.string.v44_pin_repeat
    }

    fun message(r: KidsLockRepository.Result): Int? = when (r) {
        KidsLockRepository.Result.OK -> null
        KidsLockRepository.Result.WRONG -> R.string.v44_pin_wrong
        KidsLockRepository.Result.BLOCKED -> R.string.v44_pin_blocked
        KidsLockRepository.Result.NO_PIN -> R.string.v44_pin_missing
        KidsLockRepository.Result.INVALID -> R.string.v44_pin_invalid
    }

    fun submit(pin: String) {
        when {
            purpose == PinPurpose.DISABLE -> {
                busy = true
                scope.launch {
                    val r = repo.disable(pin)
                    busy = false
                    if (r == KidsLockRepository.Result.OK) onDone() else { error = message(r); digits = "" }
                }
            }
            step == 0 -> { old = pin; step = 1; digits = ""; error = null }
            step == 1 -> { first = pin; step = 2; digits = ""; error = null }
            else -> {
                if (pin != first) { error = R.string.v44_pin_mismatch; step = 1; digits = ""; first = ""; return }
                busy = true
                scope.launch {
                    val r = when (purpose) {
                        PinPurpose.ENABLE -> repo.enable(pin)
                        PinPurpose.SET -> repo.setPin(pin)
                        PinPurpose.CHANGE -> repo.changePin(old, pin)
                        PinPurpose.DISABLE -> repo.disable(pin)
                    }
                    busy = false
                    if (r == KidsLockRepository.Result.OK) onDone() else {
                        error = message(r); digits = ""; first = ""; step = if (purpose == PinPurpose.CHANGE) 0 else 1
                    }
                }
            }
        }
    }

    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().testTag("kids_pin_sheet").padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.Lock, null, tint = colors.accent, modifier = Modifier.size(36.dp))
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(prompt), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted, modifier = Modifier.testTag("kids_pin_prompt"))
            // Girilen haneler (gizli)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.semantics { contentDescription = "${digits.length}/4" }) {
                repeat(4) { i ->
                    Box(
                        Modifier.size(18.dp).clip(CircleShape)
                            .background(if (i < digits.length) colors.accent else colors.background)
                            .border(2.dp, colors.accent, CircleShape),
                    )
                }
            }
            error?.let { Text(stringResource(it), color = colors.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("kids_pin_error")) }
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "<")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.size(64.dp).clip(CircleShape)
                                .then(if (k.isEmpty()) Modifier else Modifier.background(colors.accent.copy(alpha = 0.10f)).clickable(role = Role.Button, enabled = !busy) {
                                    if (k == "<") { digits = digits.dropLast(1); return@clickable }
                                    if (digits.length >= 4) return@clickable
                                    digits += k
                                    error = null
                                    if (digits.length == 4) submit(digits)
                                })
                                .testTag("kids_pin_key_$k"),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "" -> Unit
                                "<" -> Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.v44_pin_delete), tint = colors.content)
                                else -> Text(k, translate = false, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** v44 Ana sayfada çocuk kilidi açıkken bilgi kartı; şifre belirlenmemişse ebeveynden istenir. */
@Composable
fun KidsLockHomeCard(repo: KidsLockRepository = koinInject()) {
    val colors = NeuTheme.colors
    val state by repo.state.collectAsState()
    var pinOpen by remember { mutableStateOf(false) }
    NeuSurface(Modifier.fillMaxWidth().testTag("kids_lock_home"), shape = RoundedCornerShape(24.dp), elevation = 6.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ChildCare, null, tint = colors.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.v44_kids_lock_on), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.v44_kids_lock_on_text), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            if (!state.hasPin) {
                Text(stringResource(R.string.v44_kids_lock_parent_pin), style = MaterialTheme.typography.bodyMedium, color = colors.accent)
                NeuButton(stringResource(R.string.v44_pin_title_set), { pinOpen = true }, Modifier.fillMaxWidth().testTag("kids_lock_set_pin"), primary = true, icon = Icons.Filled.Lock)
            }
        }
    }
    if (pinOpen) KidsPinSheet(PinPurpose.SET, onDone = { pinOpen = false }, onDismiss = { pinOpen = false })
}

/** v44 Ayarlar'daki çocuk kilidi kartı: aç/kapat (şifreyle), şifre belirle/değiştir. */
@Composable
fun KidsLockSettingsCard(modifier: Modifier = Modifier, repo: KidsLockRepository = koinInject()) {
    val colors = NeuTheme.colors
    val state by repo.state.collectAsState()
    var purpose by remember { mutableStateOf<PinPurpose?>(null) }
    NeuSurface(modifier.fillMaxWidth().testTag("kids_lock_card"), elevation = 6.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ChildCare, null, tint = colors.accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.v44_kids_lock_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                NeuSwitch(state.enabled, { on ->
                    purpose = when {
                        on -> PinPurpose.ENABLE
                        state.hasPin -> PinPurpose.DISABLE
                        // Yaşla açılmış, şifre yok: önce şifre belirlenir, sonra kapatılabilir
                        else -> PinPurpose.SET
                    }
                }, Modifier.testTag("kids_lock_switch"))
            }
            Text(stringResource(R.string.v44_kids_lock_desc), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            if (state.enabled && !state.hasPin) {
                NeuButton(stringResource(R.string.v44_pin_title_set), { purpose = PinPurpose.SET }, Modifier.fillMaxWidth().testTag("kids_lock_card_set_pin"))
            } else if (state.hasPin) {
                NeuButton(stringResource(R.string.v44_pin_title_change), { purpose = PinPurpose.CHANGE }, Modifier.fillMaxWidth().testTag("kids_lock_change_pin"))
            }
            Spacer(Modifier.height(2.dp))
        }
    }
    purpose?.let { p -> KidsPinSheet(p, onDone = { purpose = null }, onDismiss = { purpose = null }) }
}
