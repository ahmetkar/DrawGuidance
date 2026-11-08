package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.GuideKind
import com.karapp.drawguidance.data.model.GuideSpec
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.components.NeuSheet

/** Kılavuz çizgisi türü seçimi (ayrıntılı ayar yok: genel ayarlar çizim alanının altında). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideTypeSheet(selected: GuideKind, onPick: (GuideKind) -> Unit, onDismiss: () -> Unit, canClear: Boolean = false, onClear: () -> Unit = {}) {
    TypeSheet(
        title = stringResource(R.string.guide_title),
        options = GuideSpec.GridKinds.map { it to guideLabel(it) },
        selected = selected,
        onPick = onPick,
        onDismiss = onDismiss,
        canClear = canClear,
        onClear = onClear,
    )
}

/** Perspektif türü seçimi (1, 2 ya da 3 kaçış noktası). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerspectiveTypeSheet(selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit, canClear: Boolean = false, onClear: () -> Unit = {}) {
    TypeSheet(
        title = stringResource(R.string.perspective_title),
        options = listOf(1 to stringResource(R.string.guide_p1), 2 to stringResource(R.string.guide_p2), 3 to stringResource(R.string.guide_p3)),
        selected = selected,
        onPick = onPick,
        onDismiss = onDismiss,
        canClear = canClear,
        onClear = onClear,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> TypeSheet(
    title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit, onDismiss: () -> Unit,
    canClear: Boolean = false, onClear: () -> Unit = {},
) {
    val colors = NeuTheme.colors
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().testTag("guide_type_sheet").padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            options.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (value, label) ->
                        NeuChip(label, value == selected, { onPick(value) }, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            // v27 tuvaldeki mevcut kılavuz ve perspektif çizgilerinin hepsini kaldırır
            if (canClear) {
                NeuButton(
                    stringResource(R.string.guide_clear_all), onClear, Modifier.fillMaxWidth().testTag("guide_clear"),
                    icon = Icons.Filled.Close,
                )
            }
        }
    }
}

/**
 * Çizim alanının altındaki genel kılavuz/perspektif ayarları: aralık (kılavuz) ya da ufuk (perspektif) ve
 * görünürlük; kaldır ve bitti. Perspektifte kaçış noktaları tuvalde sürüklenir.
 */
@Composable
fun GuideSettingsBar(
    spec: GuideSpec,
    perspective: Boolean,
    onChange: (GuideSpec) -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    NeuSurface(modifier = modifier.fillMaxWidth().testTag("guide_settings_bar"), elevation = 8.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (perspective) stringResource(R.string.perspective_bar_title, spec.perspective)
                        else stringResource(R.string.guide_bar_title, guideLabel(spec.kind)),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.accent,
                    )
                    if (perspective) {
                        Text(stringResource(R.string.perspective_bar_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                    }
                }
                NeuButton(stringResource(R.string.guide_remove), onRemove, icon = Icons.Filled.Close)
                Spacer(Modifier.padding(4.dp))
                NeuButton(stringResource(R.string.move_done), onDone, primary = true, icon = Icons.Filled.Check)
            }
            if (perspective) {
                BarSlider(stringResource(R.string.guide_horizon), "%${(spec.horizon * 100).toInt()}", spec.horizon, 0f..1f) {
                    onChange(spec.copy(horizon = it))
                }
                BarSlider(stringResource(R.string.guide_opacity), "%${(spec.perspectiveOpacity * 100).toInt()}", spec.perspectiveOpacity, 0.05f..1f) {
                    onChange(spec.copy(perspectiveOpacity = it))
                }
            } else {
                BarSlider(stringResource(R.string.guide_spacing), "${spec.spacing.toInt()} px", spec.spacing, 20f..200f) {
                    onChange(spec.copy(spacing = it))
                }
                BarSlider(stringResource(R.string.guide_opacity), "%${(spec.opacity * 100).toInt()}", spec.opacity, 0.05f..1f) {
                    onChange(spec.copy(opacity = it))
                }
            }
        }
    }
}

@Composable
private fun BarSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val colors = NeuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp).fillMaxWidth(0.26f))
        NeuSlider(value = current, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun guideLabel(kind: GuideKind): String = stringResource(
    when (kind) {
        GuideKind.NONE -> R.string.guide_none
        GuideKind.GRID -> R.string.guide_grid
        GuideKind.RULED -> R.string.guide_ruled
        GuideKind.DOTS -> R.string.guide_dots
        GuideKind.ISOMETRIC -> R.string.guide_isometric
        GuideKind.PERSPECTIVE_1 -> R.string.guide_p1
        GuideKind.PERSPECTIVE_2 -> R.string.guide_p2
        GuideKind.PERSPECTIVE_3 -> R.string.guide_p3
    },
)
