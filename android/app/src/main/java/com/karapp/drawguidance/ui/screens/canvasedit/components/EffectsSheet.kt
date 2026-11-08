package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.comic.ComicEffects
import com.karapp.drawguidance.content.comic.ComicLines
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.LabeledValueSlider
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.state.ComicController
import com.karapp.drawguidance.ui.screens.canvasedit.state.LinesOptions
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/** v36 Efektler penceresinin sekmeleri: efektler (ses yazıları dahil) ve odak çizgileri. */
enum class EffectsTab { EFFECTS, LINES, IMAGES }

/** Efekt ve çizgi renkleri (ilki "kendi rengi"/"kalem rengi": null). */
private val EFFECT_COLORS = listOf(
    "#111111", "#FFFFFF", "#E53935", "#FF7A1A", "#FFD23F", "#43A047", "#1E88E5", "#53E0FF", "#8E24AA", "#FF4F81", "#8D6E63", "#9EA7AF",
)

/**
 * v36 Efektler ve odak çizgileri (tek düğme, tek pencere). v48 Efektler sekmesi resimli efektler (çizilen efektler ve
 * ses yazıları bölümü kaldırıldı). Odak çizgileri sekmesi: bütün çizgi türleri; odak noktası, yön, uzunluk, sıklık, kalınlık, renk ve opaklık (yeni
 * çizgiler ve seçili grup için).
 */
@Composable
fun EffectsSheet(
    initialTab: EffectsTab,
    selectedLines: Pair<ComicLinesKind, LinesOptions>?,
    onLines: (ComicLinesKind, LinesOptions) -> Unit,
    onSelectedLines: (LinesOptions) -> Unit,
    onDismiss: () -> Unit,
    /** v42 hazır efekt resmi seçildi (null: Efektler sekmesi yok). */
    onImageEffect: ((com.karapp.drawguidance.data.comicassets.ComicProp) -> Unit)? = null,
) {
    // v48 yalnız resimli efektler ("Efektler") ve odak çizgileri; çizilen efektler ve ses yazıları sekmesi kaldırıldı
    val images = onImageEffect != null && com.karapp.drawguidance.data.comicassets.ComicAssets.catalog.effects.isNotEmpty()
    var tab by rememberSaveable { mutableStateOf(EffectsSheetTabs.start(initialTab, images)) }
    ComicSheetFrame(stringResource(R.string.effects_title), stringResource(R.string.effects_hint), "effects_sheet", onDismiss) { columns ->
        PillTabs(
            (if (images) listOf(Triple(EffectsTab.IMAGES, stringResource(R.string.effects_tab_effects), Icons.Filled.Image)) else emptyList()) +
                listOf(Triple(EffectsTab.LINES, stringResource(R.string.effects_tab_lines), Icons.Filled.Flare)),
            tab,
        ) { tab = it }
        when (tab) {
            EffectsTab.IMAGES, EffectsTab.EFFECTS -> if (images) {
                Text(stringResource(R.string.v42_fx_images_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
                ComicPropsBrowser(com.karapp.drawguidance.data.comicassets.ComicAssetKind.EFFECT, columns, "fximg", onImageEffect!!)
            }
            EffectsTab.LINES -> LinesTabContent(columns, selectedLines, onLines, onSelectedLines)
        }
    }
}

/** v48 Efektler penceresinin sekmeleri: resimli efektler ("Efektler") ve odak çizgileri. */
object EffectsSheetTabs {
    /** Açılış sekmesi: eski "Efektler" isteği resimli efektlere gider (resim yoksa odak çizgileri). */
    fun start(initial: EffectsTab, images: Boolean): EffectsTab = when {
        initial == EffectsTab.LINES || !images -> EffectsTab.LINES
        else -> EffectsTab.IMAGES
    }
}

@Composable
private fun LinesTabContent(
    columns: Int,
    selected: Pair<ComicLinesKind, LinesOptions>?,
    onLines: (ComicLinesKind, LinesOptions) -> Unit,
    onSelected: (LinesOptions) -> Unit,
) {
    Box(Modifier.testTag("lines_sheet")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected != null) {
                val (kind, initial) = selected
                var o by remember(selected) { mutableStateOf(initial) }
                Section(stringResource(R.string.lines_selected_title) + ": " + linesLabel(kind), "lines_selected") {
                    LinesControls(o, kind, "lines_selected", densityTag = "lines_selected_density", densityLabel = stringResource(R.string.lines_selected_density)) {
                        if (it != o) { o = it; onSelected(it) }
                    }
                }
            }
            var o by remember { mutableStateOf(LinesOptions()) }
            Text(stringResource(R.string.lines_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
            Grid(ComicLinesKind.entries, columns.coerceAtMost(4)) { kind, m -> LinesTile(kind, o, m) { onLines(kind, o) } }
            Section(stringResource(R.string.lines_new_title), "lines_new") {
                LinesControls(o, null, "lines_new", densityTag = "lines_new_density", densityLabel = stringResource(R.string.lines_new_density)) { o = it }
            }
        }
    }
}

/** Çizgi ayarları; [kind] verilirse yalnız o türe uyan ayarlar (odak noktası, yön) gösterilir. */
@Composable
private fun LinesControls(o: LinesOptions, kind: ComicLinesKind?, prefix: String, densityTag: String, densityLabel: String, onChange: (LinesOptions) -> Unit) {
    fun r(v: Float, step: Float) = (v / step).roundToInt() * step
    Tagged(densityTag) {
        LabeledValueSlider(densityLabel, "×" + "%.2f".format(o.density), o.density, ComicLines.MIN_DENSITY..ComicLines.MAX_DENSITY) { onChange(o.copy(density = r(it, 0.05f))) }
    }
    if (kind == null || ComicLines.usesFocus(kind)) {
        Tagged("${prefix}_focus_x") {
            LabeledValueSlider(stringResource(R.string.lines_focus_x), "%${(o.focusX * 100).roundToInt()}", o.focusX, 0f..1f) { onChange(o.copy(focusX = r(it, 0.02f))) }
        }
        Tagged("${prefix}_focus_y") {
            LabeledValueSlider(stringResource(R.string.lines_focus_y), "%${(o.focusY * 100).roundToInt()}", o.focusY, 0f..1f) { onChange(o.copy(focusY = r(it, 0.02f))) }
        }
    }
    if (kind == null || ComicLines.usesAngle(kind)) {
        Tagged("${prefix}_angle") {
            LabeledValueSlider(stringResource(R.string.lines_angle), "${o.angle.roundToInt()}°", o.angle, -90f..90f) { onChange(o.copy(angle = it.roundToInt().toFloat())) }
        }
    }
    Tagged("${prefix}_length") {
        LabeledValueSlider(stringResource(R.string.lines_length), "%${(o.length * 100).roundToInt()}", o.length, ComicLines.MIN_LENGTH..ComicLines.MAX_LENGTH) { onChange(o.copy(length = r(it, 0.05f))) }
    }
    Tagged("${prefix}_thickness") {
        LabeledValueSlider(stringResource(R.string.lines_thickness), "×" + "%.1f".format(o.thickness), o.thickness, ComicController.MIN_THICKNESS..ComicController.MAX_THICKNESS) {
            onChange(o.copy(thickness = r(it, 0.1f)))
        }
    }
    ColorSwatches(o.colorHex, stringResource(R.string.lines_color_ink), null, "${prefix}_color") { onChange(o.copy(colorHex = it)) }
    Tagged("${prefix}_opacity") {
        LabeledValueSlider(stringResource(R.string.effects_opacity), "%${(o.opacity * 100).roundToInt()}", o.opacity, 0.1f..1f) { onChange(o.copy(opacity = r(it, 0.05f))) }
    }
}

/** Çizgi türünün küçük önizlemesi: yeni çizgilerin ayarlarıyla (odak, yön, uzunluk, sıklık). */
@Composable
private fun LinesTile(kind: ComicLinesKind, o: LinesOptions, modifier: Modifier, onClick: () -> Unit) {
    val ink = NeuTheme.colors.content
    val color = o.colorHex?.let { hexToColor(it) }
    ComicTile(linesLabel(kind), false, onClick, modifier, "lines_${kind.name}", premium = com.karapp.drawguidance.data.billing.PremiumFeature.COMIC_EFFECTS.takeIf { com.karapp.drawguidance.data.billing.PremiumRules.isAdvancedLines(kind) }) {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val lines = ComicLines.generate(kind, 0f, 0f, size.width, size.height, seed = 5, density = o.density * 0.6f, focusX = o.focusX, focusY = o.focusY, angle = o.angle, length = o.length)
            val k = ComicLines.widthFactor(kind) * o.thickness
            lines.forEachIndexed { n, l ->
                val c = color ?: if (kind == ComicLinesKind.ANIME_SOFT) hexToColor(ComicLines.SOFT_COLORS[n % ComicLines.SOFT_COLORS.size]) else ink
                for (i in 1 until l.xs.size) {
                    drawLine(
                        c.copy(alpha = o.opacity), androidx.compose.ui.geometry.Offset(l.xs[i - 1], l.ys[i - 1]), androidx.compose.ui.geometry.Offset(l.xs[i], l.ys[i]),
                        strokeWidth = (1.4f * l.pressures[i] + 0.4f) * k,
                    )
                }
            }
        }
    }
}

/** Başlıklı ayar bölümü (yuvarlak köşeli açık kutu). */
@Composable
internal fun Section(title: String, tag: String, content: @Composable () -> Unit) {
    val colors = NeuTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .border(1.dp, colors.darkShadow.copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .testTag(tag).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
internal fun Tagged(tag: String, content: @Composable () -> Unit) = Box(Modifier.testTag(tag)) { content() }

/** Sekmeler: simgeli haplar, seçili olan vurgu renginde dolgulu (ayarlar sekmeleriyle aynı görünüm). */
@Composable
internal fun <T> PillTabs(
    items: List<Triple<T, String, androidx.compose.ui.graphics.vector.ImageVector>>, selected: T, tagPrefix: String = "effects_tab_", onSelect: (T) -> Unit,
) {
    val colors = NeuTheme.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (value, label, icon) ->
            val on = value == selected
            Row(
                Modifier.height(40.dp).clip(NeuPillShape)
                    .background(if (on) colors.accent else colors.background)
                    .border(1.dp, if (on) colors.accent else colors.darkShadow.copy(alpha = 0.45f), NeuPillShape)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .semantics { this.selected = on }
                    .testTag(tagPrefix + value.toString().lowercase())
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, null, tint = if (on) colors.onAccent else colors.contentMuted, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) colors.onAccent else colors.content, maxLines = 1)
            }
        }
    }
}

/**
 * Renk seçimi: ilk yuvarlak "kendi rengi" / "kalem rengi" (null; [ownHex] varsa o renkte, yoksa çok renkli), ardından
 * hazır renkler. Seçili olan vurgu çerçeveli.
 */
@Composable
private fun ColorSwatches(selected: String?, ownLabel: String, ownHex: String?, tag: String, onPick: (String?) -> Unit) {
    val colors = NeuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag(tag)) {
        Text(stringResource(R.string.effects_color), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val ownChosen = selected == null || (ownHex != null && selected.equals(ownHex, ignoreCase = true))
            val ownBrush = ownHex?.let { Brush.linearGradient(listOf(hexToColor(it), hexToColor(it))) }
                ?: Brush.sweepGradient(listOf(Color(0xFFE53935), Color(0xFFFFD23F), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE53935)))
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(ownBrush)
                    .border(if (ownChosen) 3.dp else 1.dp, if (ownChosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                    .clickable(role = Role.RadioButton) { onPick(null) }
                    .semantics { contentDescription = ownLabel; this.selected = ownChosen },
            )
            EFFECT_COLORS.forEach { hex ->
                val chosen = !ownChosen && hex.equals(selected, ignoreCase = true)
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(hexToColor(hex))
                        .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                        .clickable(role = Role.RadioButton) { onPick(hex) }
                        .semantics { contentDescription = hex; this.selected = chosen },
                )
            }
        }
    }
}
