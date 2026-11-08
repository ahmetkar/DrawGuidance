package com.karapp.drawguidance.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.ThemeMode
import com.karapp.drawguidance.ui.theme.ThemePrefs

/**
 * v40 Ayarlar → Renk teması: Açık / Koyu / Sistem seçimi ve yüksek karşıtlık anahtarı. Seçim `dg_theme` ayar
 * dosyasında saklanır ve bütün uygulamaya hemen uygulanır ([ThemePrefs]).
 */
@Composable
fun ThemeModeSection() {
    val colors = NeuTheme.colors
    val context = LocalContext.current
    val mode by ThemePrefs.mode.collectAsState()
    val highContrast by ThemePrefs.highContrast.collectAsState()
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.v40_theme_mode_title), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
        Row(Modifier.fillMaxWidth().testTag("theme_mode_row"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m ->
                NeuChip(
                    text = stringResource(m.label), selected = mode == m,
                    onClick = { ThemePrefs.selectMode(context, m) },
                    icon = when (m) {
                        ThemeMode.LIGHT -> Icons.Filled.LightMode
                        ThemeMode.DARK -> Icons.Filled.DarkMode
                        ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                    },
                    modifier = Modifier.weight(1f).testTag("theme_mode_${m.name.lowercase()}"),
                )
            }
        }
        val title = stringResource(R.string.v40_theme_high_contrast)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.content)
                Text(stringResource(R.string.v40_theme_high_contrast_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            }
            NeuSwitch(
                checked = highContrast, onCheckedChange = { ThemePrefs.setHighContrast(context, it) },
                modifier = Modifier.semantics { contentDescription = title }.testTag("theme_high_contrast"),
            )
        }
    }
}
