package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.GraphSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.drawing.Formula
import com.karapp.drawguidance.ui.theme.NeuFieldShape
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * Elemanlar panelindeki biçim bölümü: hizalama, dolgu ve kenar rengi, kenar kalınlığı,
 * köşe yuvarlaklığı; grafiklerde eksen etiketleri ve formül.
 */
@Composable
fun ElementStyleSection(
    style: ShapeStyle,
    graph: GraphSpec?,
    showStyle: Boolean,
    showAlign: Boolean,
    onStyleChange: (ShapeStyle) -> Unit,
    onGraphChange: (GraphSpec) -> Unit,
    onAlign: (TextAlignKind) -> Unit,
) {
    val colors = NeuTheme.colors
    var strokePicker by remember { mutableStateOf(false) }
    var fillPicker by remember { mutableStateOf(false) }
    NeuSurface(modifier = Modifier.fillMaxWidth().testTag("element_style_section"), elevation = 5.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.element_style_title), style = MaterialTheme.typography.titleSmall, color = colors.accent)

            if (showAlign) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.element_align), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeuButton(stringResource(R.string.align_left), { onAlign(TextAlignKind.LEFT) }, Modifier.weight(1f))
                        NeuButton(stringResource(R.string.align_center), { onAlign(TextAlignKind.CENTER) }, Modifier.weight(1f))
                        NeuButton(stringResource(R.string.align_right), { onAlign(TextAlignKind.RIGHT) }, Modifier.weight(1f))
                    }
                }
            }

            if (showStyle) {
                ColorRow(
                    label = stringResource(R.string.style_stroke_color),
                    colorHex = style.strokeColorHex,
                    expanded = strokePicker,
                    onToggle = { strokePicker = !strokePicker; if (strokePicker) fillPicker = false },
                )
                AnimatedVisibility(strokePicker) {
                    HsvColorPicker(colorHex = style.strokeColorHex, onColorChange = { onStyleChange(style.copy(strokeColorHex = it.uppercase())) })
                }

                ColorRow(
                    label = stringResource(R.string.style_fill_color),
                    colorHex = style.fillColorHex,
                    expanded = fillPicker,
                    onToggle = { fillPicker = !fillPicker; if (fillPicker) strokePicker = false },
                )
                AnimatedVisibility(fillPicker) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeuChip(
                                stringResource(R.string.style_fill_none),
                                selected = style.fillColorHex == null,
                                onClick = { onStyleChange(style.copy(fillColorHex = null)) },
                                modifier = Modifier.weight(1f),
                            )
                            NeuChip(
                                stringResource(R.string.style_fill_on),
                                selected = style.fillColorHex != null,
                                onClick = { onStyleChange(style.copy(fillColorHex = style.fillColorHex ?: "#FFFFFF")) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        HsvColorPicker(
                            colorHex = style.fillColorHex ?: "#FFFFFF",
                            onColorChange = { onStyleChange(style.copy(fillColorHex = it.uppercase())) },
                        )
                        SliderRow(
                            stringResource(R.string.style_fill_opacity), "%${(style.fillOpacity * 100).toInt()}",
                            style.fillOpacity, 0.05f..1f,
                        ) { onStyleChange(style.copy(fillOpacity = it)) }
                    }
                }

                SliderRow(
                    stringResource(R.string.style_stroke_width), "${style.strokeWidth.toInt()} px",
                    style.strokeWidth, 0f..40f,
                ) { onStyleChange(style.copy(strokeWidth = it)) }

                SliderRow(
                    stringResource(R.string.style_corner_radius), "${style.cornerRadius.toInt()} px",
                    style.cornerRadius, 0f..80f,
                ) { onStyleChange(style.copy(cornerRadius = it)) }
            }

            if (graph != null) {
                GraphControls(graph, onGraphChange)
            }
        }
    }
}

/** Grafik ayarları: eksen etiketleri ve çizilecek formül. */
@Composable
private fun GraphControls(graph: GraphSpec, onChange: (GraphSpec) -> Unit) {
    val colors = NeuTheme.colors
    val invalid = graph.formula.isNotBlank() && Formula.parse(graph.formula) == null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.graph_settings_title), style = MaterialTheme.typography.titleSmall, color = colors.accent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuTextField(
                value = graph.xLabel, onValueChange = { onChange(graph.copy(xLabel = it)) },
                label = stringResource(R.string.graph_x_label), modifier = Modifier.weight(1f).testTag("graph_x_field"),
            )
            NeuTextField(
                value = graph.yLabel, onValueChange = { onChange(graph.copy(yLabel = it)) },
                label = stringResource(R.string.graph_y_label), modifier = Modifier.weight(1f),
            )
        }
        NeuTextField(
            value = graph.zLabel, onValueChange = { onChange(graph.copy(zLabel = it)) },
            label = stringResource(R.string.graph_z_label), modifier = Modifier.fillMaxWidth(),
        )
        // Hazır eğride formül alanı eğrinin formülünü gösterir; yazılan formülle grafik yeniden çizilir
        val shownFormula = graph.formula.ifBlank { com.karapp.drawguidance.content.graph.CurvePresets.find(graph.preset)?.formula ?: "" }
        NeuTextField(
            value = shownFormula, onValueChange = { onChange(graph.copy(formula = it, preset = if (it.isBlank()) graph.preset else "")) },
            label = stringResource(R.string.graph_formula),
            placeholder = stringResource(R.string.graph_formula_hint),
            supportingText = if (invalid) stringResource(R.string.graph_formula_invalid) else null,
            isError = invalid,
            modifier = Modifier.fillMaxWidth().testTag("graph_formula_field"),
        )
        SliderRow(stringResource(R.string.graph_range), "${graph.xMin.toInt()} … ${graph.xMax.toInt()}", graph.xMax, 1f..20f) {
            onChange(graph.copy(xMin = -it, xMax = it, rangeSet = true))
        }
    }
}

@Composable
internal fun ColorRow(label: String, colorHex: String?, expanded: Boolean, onToggle: () -> Unit) {
    val colors = NeuTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(24.dp)
                .background(colorHex?.let { hexToColor(it) } ?: colors.background, CircleShape)
                .border(2.dp, if (expanded) colors.accent else colors.lightShadow, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            colorHex ?: stringResource(R.string.style_fill_none),
            style = MaterialTheme.typography.labelMedium, color = colors.contentMuted,
        )
    }
}

@Composable
internal fun SliderRow(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val colors = NeuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
        }
        NeuSlider(value = current, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

/** Yazı kenarlığının ilk rengi: beyaz kağıtta da koyu yazıda da görünür. */
internal const val DEFAULT_TEXT_OUTLINE = "#1E88E5"
