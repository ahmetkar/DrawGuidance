package com.karapp.drawguidance.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.ui.theme.NeuCardShape
import com.karapp.drawguidance.ui.theme.NeuFieldShape
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic

/** Kabarık ya da gömülü neumorphic kutu; isteğe bağlı tıklama/uzun basma. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NeuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = NeuCardShape,
    elevation: Dp = 6.dp,
    inset: Boolean = false,
    fill: Color = NeuTheme.colors.background,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** v33 araç çubuklarında: basılı tutunca bu adın açıklaması görünür (uzun basma işlemi yoksa). */
    helpLabel: String? = null,
    /** v40 seçili yüzey: gölge yerine vurgu renginde kalın kenar. */
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = NeuTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val help = LocalToolHelp.current
    val withHelp = help != null && helpLabel != null && onClick != null && onLongClick == null
    val clickModifier = when {
        withHelp -> Modifier.toolHelpClickable(help!!, helpLabel!!, true, null, interaction, showName = false, onClick = onClick!!)
        onClick != null || onLongClick != null -> Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick ?: {},
            onLongClick = onLongClick,
        )
        else -> Modifier
    }
    val surface: @Composable () -> Unit = {
        Box(
            modifier = modifier
                .neumorphic(colors, shape, elevation, inset = inset || pressed, fill = fill, selected = selected)
                .clip(shape)
                .then(clickModifier),
            content = content,
        )
    }
    if (withHelp) ToolHold(surface) else surface()
}

@Composable
fun NeuIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    selected: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    /** v38 yatay ekranda simgenin altında yazan kısa ad (verilmezse adın parantezsiz hali). */
    label: String? = null,
) {
    val colors = NeuTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val iconTint = when {
        !enabled -> colors.contentMuted.copy(alpha = 0.45f)
        tint != null -> tint
        selected -> colors.accent
        else -> colors.content
    }
    // v33 araç çubuklarında: dokununca ad, 2 sn basılı tutunca açıklama (çizim alanının köşesindeki kutuda)
    val help = LocalToolHelp.current
    val button: @Composable (Modifier) -> Unit = { click ->
        Box(
            modifier = modifier
                .size(size)
                .neumorphic(colors, CircleShape, elevation = 5.dp, inset = pressed, selected = selected)
                .clip(CircleShape)
                .then(click)
                .semantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                    // v18: seçili araç erişilebilirlikte (ve testlerde) seçili görünür
                    if (selected) this.selected = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.5f))
        }
    }
    // v45 gelişmiş modların dikey ekranında üst menüde ad düğmenin içinde (simgenin yanında) yazar
    if (LocalInlineLabels.current && !contentDescription.isNullOrEmpty()) {
        InlineLabelButton(icon, contentDescription, onClick, modifier, size, selected, enabled, tint, label)
        return
    }
    val clickable: @Composable () -> Unit = {
        if (help != null && !contentDescription.isNullOrEmpty()) {
            ToolHold { button(Modifier.toolHelpClickable(help, contentDescription, enabled, Role.Button, interaction, onClick = onClick)) }
        } else {
            button(Modifier.toggleableOrClickable(interaction, enabled, onClick))
        }
    }
    // v38 yatay ekranda üst menüde ad simgenin altında sürekli yazar
    if (LocalButtonLabels.current && !contentDescription.isNullOrEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            clickable()
            ButtonLabel(label ?: shortLabel(contentDescription), selected, enabled, tag = contentDescription)
        }
    } else {
        clickable()
    }
}

/** v38 yatay ekranda üst menü düğmelerinin adı simgenin altında sürekli görünür (dokunmadan). */
val LocalButtonLabels = staticCompositionLocalOf { false }

/**
 * v38 düğmenin altındaki ad. Erişilebilirlikte düğmenin kendi adı okunur, bu yazı tekrar okunmaz; testlerde
 * "button_label:<düğmenin adı>" etiketiyle bulunur.
 */
@Composable
fun ButtonLabel(text: String, selected: Boolean, enabled: Boolean, tag: String = text) {
    val colors = NeuTheme.colors
    val color = when {
        !enabled -> colors.contentMuted.copy(alpha = 0.45f)
        selected -> colors.accent
        else -> colors.contentMuted
    }
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, softWrap = false,
        modifier = Modifier.padding(top = 3.dp).clearAndSetSemantics { testTag = "button_label:$tag" },
    )
}

/** v38 düğme altındaki ad için kısa hali: parantez içi açıklama atılır ("Kalem aracı (yol)" → "Kalem aracı"). */
fun shortLabel(name: String): String = name.replace(Regex("\\s*[(（][^)）]*[)）]"), "").trim().ifEmpty { name }

private fun Modifier.toggleableOrClickable(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = this.then(
    Modifier.combinedClickableCompat(interaction, enabled, onClick),
)

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = combinedClickable(
    interactionSource = interaction,
    indication = null,
    enabled = enabled,
    role = Role.Button,
    onClick = onClick,
)

@Composable
fun NeuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color? = null,
) {
    val colors = NeuTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color = when {
        !enabled -> colors.contentMuted.copy(alpha = 0.5f)
        contentColor != null -> contentColor
        primary -> colors.accent
        else -> colors.content
    }
    // v33 araç çubuklarında (mod çubuğu) basılı tutunca açıklama; ad zaten yazılı
    val help = LocalToolHelp.current
    val button: @Composable (Modifier) -> Unit = { click ->
        Row(
            modifier = modifier
                .height(48.dp)
                .neumorphic(colors, NeuPillShape, elevation = 6.dp, inset = pressed)
                .clip(NeuPillShape)
                .then(click)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (help != null && text.isNotEmpty()) {
        ToolHold { button(Modifier.toolHelpClickable(help, text, enabled, Role.Button, interaction, showName = false, onClick = onClick)) }
    } else {
        button(Modifier.combinedClickableCompat(interaction, enabled, onClick))
    }
}

/** Seçilebilir hap (şablon, sekme vb.). Seçiliyken zemine gömülür. */
@Composable
fun NeuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = NeuTheme.colors
    val tint = if (selected) colors.accent else colors.content
    // v33 araç çubuklarında (ör. şekil düzenlemenin nokta paneli) basılı tutunca açıklama
    val help = LocalToolHelp.current
    val chip: @Composable (Modifier) -> Unit = { click ->
        Row(
            modifier = modifier
                .height(38.dp)
                .neumorphic(colors, NeuPillShape, elevation = 4.dp, selected = selected)
                .clip(NeuPillShape)
                .then(click)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
    if (help != null && text.isNotEmpty()) {
        ToolHold {
            chip(
                Modifier
                    .toolHelpClickable(help, text, true, Role.RadioButton, null, showName = false, onClick = onClick)
                    .semantics { this.selected = selected },
            )
        }
    } else {
        chip(Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton))
    }
}

@Composable
fun NeuSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 2.dp, label = "thumb")
    val trackFill by animateColorAsState(
        if (checked) colors.accent.copy(alpha = if (colors.isDark) 0.35f else 0.22f) else colors.background,
        label = "track",
    )
    Box(
        modifier = modifier
            .width(54.dp)
            .height(32.dp)
            .neumorphic(colors, NeuPillShape, elevation = 3.dp, inset = !checked, fill = trackFill, selected = checked)
            .clip(NeuPillShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(28.dp)
                .neumorphic(colors, CircleShape, elevation = 3.dp, fill = if (checked) colors.accent else colors.background),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    /** v21 sürükleme bitince (ör. değeri kaydetmek için). */
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        thumb = {
            Box(
                modifier = Modifier.size(26.dp).neumorphic(colors, CircleShape, elevation = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(10.dp).background(colors.accent, CircleShape))
            }
        },
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .neumorphic(colors, NeuPillShape, elevation = 2.dp, inset = true),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .padding(3.dp)
                        .background(colors.accent.copy(alpha = 0.85f), NeuPillShape),
                )
            }
        },
    )
}

@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    /** Yazı alanının kendisine uygulanan değiştirici (odak, erişilebilirlik açıklaması). */
    fieldModifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphic(colors, NeuFieldShape, elevation = 4.dp, inset = true)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                // v34 adres, e-posta ve sayı alanları sağdan sola dillerde de soldan sağa yazılır
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.content,
                    textDirection = if (keyboardOptions.keyboardType in LTR_KEYBOARDS) androidx.compose.ui.text.style.TextDirection.Ltr
                    else androidx.compose.ui.text.style.TextDirection.Content,
                ),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth().then(fieldModifier),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.contentMuted.copy(alpha = 0.7f))
                    }
                    inner()
                },
            )
        }
        if (supportingText != null) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colors.danger else colors.contentMuted,
            )
        }
    }
}

/**
 * v34 salt okunur alan: [NeuTextField] ile aynı boy ve biçim (etiket + içe gömük kutu), değer seçilip kopyalanamaz.
 * [leading] / [trailing] kutunun içinde değerin önünde / arkasında durur (ör. fotoğraf, doğrulandı işareti).
 */
@Composable
fun NeuReadOnlyField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = NeuTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphic(colors, NeuFieldShape, elevation = 4.dp, inset = true)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading?.invoke()
            Text(
                value, style = MaterialTheme.typography.bodyLarge, color = valueColor ?: colors.content,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}

/** Ekranların üst çubuğu: durum çubuğu boşluğu + başlık + neumorphic ikon düğmeleri. */
@Composable
fun NeuTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    /** Alt satır olarak metin yerine özel içerik (ör. çizim ekranında boyut + son renk/yazı ayarları). */
    subtitleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (navigation != null) navigation()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = titleStyle,
                color = NeuTheme.colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitleContent != null) {
                subtitleContent()
            } else if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted, maxLines = 1)
            }
        }
        actions()
    }
}

private val TurkishLocale = java.util.Locale.forLanguageTag("tr-TR")

@Composable
fun NeuSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(TurkishLocale),
        style = MaterialTheme.typography.labelMedium,
        color = NeuTheme.colors.contentMuted,
        modifier = modifier,
    )
}

/** v34 içeriği her dilde soldan sağa olan klavye türleri. */
private val LTR_KEYBOARDS = setOf(
    androidx.compose.ui.text.input.KeyboardType.Uri, androidx.compose.ui.text.input.KeyboardType.Email,
    androidx.compose.ui.text.input.KeyboardType.Number, androidx.compose.ui.text.input.KeyboardType.Decimal,
    androidx.compose.ui.text.input.KeyboardType.Phone,
)
