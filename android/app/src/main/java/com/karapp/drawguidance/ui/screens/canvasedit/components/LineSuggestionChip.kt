package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.screens.canvasedit.state.LineSuggestion
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor

/**
 * v38 çizgi önerisi balonu (yazı ve şekil önerisinden ayrı): dikkate alınan en az iki çizginin düzgün hali (doğru,
 * çember, köşeleri birleşen kenarlar…) tek seçenekte; dokununca hepsi birden düzelir (tek geri alma adımı).
 * Düzeltmenin önizlemesi tuvalde kesik çizgiyle görünür.
 */
@Composable
fun LineSuggestionChip(
    suggestion: LineSuggestion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTurnOff: (() -> Unit)? = null,
    drag: SuggestDrag? = null,
) {
    val count = suggestion.strokeIds.size
    val label = stringResource(R.string.line_fix_all, count)
    val cd = stringResource(R.string.line_fix_cd, count)
    SuggestBalloon(
        title = stringResource(R.string.line_suggest_title),
        options = listOf(SuggestOption(label, Icons.Filled.AutoFixHigh, cd, "line_fix_option", onAccept)),
        onDismiss = onDismiss, onTurnOff = onTurnOff, turnOffLabel = stringResource(R.string.line_suggest_turn_off),
        tag = "line_suggest_chip", modifier = modifier, drag = drag, anchor = TutorialAnchor.SUGGEST_LINE,
    )
}
