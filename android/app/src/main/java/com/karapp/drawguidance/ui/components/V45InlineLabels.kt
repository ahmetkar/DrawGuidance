package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic

/**
 * v45 gelişmiş modların (resim, not, çizgi roman) dikey ekranında üst menü düğmelerinin adı düğmenin içinde,
 * simgenin yanında yazar. Yalnız üst menülerin çevresinde verilir; ad artık çizim alanının köşesine yazılmaz (köşede
 * yalnız 2 sn basılı tutunca açıklama görünür).
 */
val LocalInlineLabels = staticCompositionLocalOf { false }

/** v45 testlerde düğmenin içindeki adın etiketi: "inline_label:<düğmenin adı>". */
const val INLINE_LABEL_PREFIX = "inline_label:"

/**
 * v45 adı içinde yazan hap biçimli düğme (simge + ad). Erişilebilirlikte düğmenin adı bir kez okunur; araç yardımı
 * varsa basılı tutunca açıklama köşede görünür, dokununca ad köşeye yazılmaz.
 */
@Composable
internal fun InlineLabelButton(
    icon: ImageVector,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier,
    height: Dp,
    selected: Boolean,
    enabled: Boolean,
    tint: Color?,
    label: String?,
) {
    val colors = NeuTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color = when {
        !enabled -> colors.contentMuted.copy(alpha = 0.45f)
        tint != null -> tint
        selected -> colors.accent
        else -> colors.content
    }
    val help = LocalToolHelp.current
    val click = if (help != null) {
        Modifier.toolHelpClickable(help, name, enabled, Role.Button, interaction, showName = false, onClick = onClick)
    } else {
        Modifier.toolHelpFreeClickable(interaction, enabled, onClick)
    }
    val row: @Composable () -> Unit = {
        Row(
            modifier
                .height(height)
                .widthIn(min = height)
                .neumorphic(colors, NeuPillShape, elevation = 5.dp, inset = pressed, selected = selected)
                .clip(NeuPillShape)
                .then(click)
                .semantics {
                    contentDescription = name
                    if (selected) this.selected = true
                }
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(height * 0.5f))
            Text(
                label ?: shortLabel(name), style = MaterialTheme.typography.labelMedium, color = color,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.clearAndSetSemantics { testTag = INLINE_LABEL_PREFIX + name },
            )
        }
    }
    if (help != null) ToolHold(row) else row()
}

private fun Modifier.toolHelpFreeClickable(interaction: MutableInteractionSource, enabled: Boolean, onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
