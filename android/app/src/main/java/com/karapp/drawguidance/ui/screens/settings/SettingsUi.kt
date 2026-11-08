package com.karapp.drawguidance.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.ThemePalette
import com.karapp.drawguidance.ui.theme.ThemePrefs

/**
 * v36 Ayarlar sekmeleri: ayarlar tek uzun listede açık durmaz, konularına göre sekmelere bölünür. Hesap başlığı
 * sekmelerin üstünde hep görünür.
 */
enum class SettingsTab(@StringRes val label: Int, val icon: ImageVector) {
    GENERAL(R.string.settings_tab_general, Icons.Filled.Tune),
    DRAWING(R.string.settings_tab_drawing, Icons.Filled.Brush),
    SAVING(R.string.settings_tab_saving, Icons.Filled.SaveAlt),
    CONNECTION(R.string.settings_tab_connection, Icons.Filled.Cloud),
    ADVANCED(R.string.settings_tab_advanced, Icons.Filled.Code),
}

/** v36 sekme çubuğu: simgeli haplar; dar ekranda yana kayar, seçili sekme vurgu renginde dolgulu. */
@Composable
fun SettingsTabBar(selected: SettingsTab, onSelect: (SettingsTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp).testTag("settings_tabs"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsTab.entries.forEach { tab ->
            val on = tab == selected
            Row(
                Modifier.height(40.dp).clip(NeuPillShape)
                    .background(if (on) colors.accent else colors.background)
                    .border(1.dp, if (on) colors.accent else colors.darkShadow.copy(alpha = 0.45f), NeuPillShape)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .semantics { this.selected = on }
                    .testTag("settings_tab_${tab.name.lowercase()}")
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon, null, tint = if (on) colors.onAccent else colors.contentMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(tab.label), style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) colors.onAccent else colors.content, maxLines = 1,
                )
            }
        }
    }
}

/**
 * v36 ayar kartı: başlıkta vurgu renginde yuvarlak simge, başlık ve isteğe bağlı kısa açıklama; altında içerik.
 * Bütün ayar kartları aynı görünür (eski kartların başlık satırı bunu kullanır).
 */
@Composable
fun SettingsCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NeuSurface(modifier = modifier.fillMaxWidth(), elevation = 7.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsCardHeader(icon, title, subtitle, trailing)
            content()
        }
    }
}

@Composable
fun SettingsCardHeader(icon: ImageVector, title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val colors = NeuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(colors.accent.copy(alpha = if (colors.isDark) 0.22f else 0.13f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = colors.accent, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
        trailing?.invoke()
    }
}

/** v36 açma/kapama satırı: başlık, açıklama ve anahtar; satırlar arasında ince ayraç. */
@Composable
fun SettingSwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, divider: Boolean = true) {
    val colors = NeuTheme.colors
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.darkShadow.copy(alpha = 0.25f)))
        Row(
            Modifier.fillMaxWidth().padding(top = if (divider) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) colors.content else colors.contentMuted)
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            }
            NeuSwitch(
                checked = checked && enabled, onCheckedChange = { if (enabled) onChange(it) },
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
    }
}

/**
 * v36 Ayarlar → Görünüm: tasarımın üç renk grubu. Her seçenek kendi paletinin küçük önizlemesidir (zemin, vurgu ve
 * gradyan); seçilince bütün uygulama yeniden başlatmadan yeni renklere geçer. Yazı renkleri her grupta okunaklı kalır.
 */
@Composable
fun ThemePaletteCard() {
    val colors = NeuTheme.colors
    val context = LocalContext.current
    val current by ThemePrefs.palette.collectAsState()
    SettingsCard(
        Icons.Filled.Palette, stringResource(R.string.theme_palette_title),
        Modifier.testTag("theme_palette_card"), subtitle = stringResource(R.string.theme_palette_hint),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemePalette.entries.forEach { palette ->
                val on = palette == current
                val p = ThemePrefs.resolve(palette, colors.isDark, colors.highContrast)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .background(p.background)
                        .border(if (on) 2.5.dp else 1.dp, if (on) p.accent else p.content.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                        .clickable(role = Role.RadioButton) { ThemePrefs.select(context, palette) }
                        .semantics { this.selected = on }
                        .testTag("theme_palette_${palette.name.lowercase()}")
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(12.dp))
                            .background(GradientBrush.linearGradient(listOf(p.gradientStart, p.gradientEnd))),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(p.accent, p.content, p.contentMuted).forEach { c ->
                            Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                        }
                    }
                    Text(
                        stringResource(palette.label), style = MaterialTheme.typography.labelMedium, color = p.content,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // v40 açık / koyu / sistem teması ve yüksek karşıtlık
        ThemeModeSection()
    }
}
