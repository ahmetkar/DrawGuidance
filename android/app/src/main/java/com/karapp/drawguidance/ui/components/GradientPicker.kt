package com.karapp.drawguidance.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.GradientSpec
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.Gradients
import com.karapp.drawguidance.ui.theme.NeuTheme

/** Compose fırçası (önizleme kutuları için). */
fun GradientSpec.brush(): Brush {
    val spec = this
    // Kutunun boyutuna göre (çizimdeki gibi) gölgelendirici
    return object : androidx.compose.ui.graphics.ShaderBrush() {
        override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader =
            Gradients.shader(spec, 0f, 0f, size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                ?: android.graphics.LinearGradient(0f, 0f, 1f, 0f, 0, 0, android.graphics.Shader.TileMode.CLAMP)
    }
}

/** v27 gradyan renk seçenekleri (başlangıç/bitiş renkleri için). */
val GradientSwatches = listOf(
    "#FFFFFF", "#000000", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#00897B", "#1E88E5", "#3949AB", "#8E24AA", "#D81B60",
    "#87CEEB", "#FFE082", "#F8BBD0", "#B2DFDB", "#6D4C41",
)

/**
 * v27 gradyan seçici: "Yok", hazır gradyanlar ve özel gradyan (2–3 renk, açı ya da dairesel). [value] null ise
 * gradyan yok; her değişiklik [onChange] ile hemen uygulanır.
 */
@Composable
fun GradientPicker(value: GradientSpec?, onChange: (GradientSpec?) -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    var editing by remember { mutableIntStateOf(0) }
    Column(modifier.testTag("gradient_picker"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .border(if (value == null) 3.dp else 1.dp, if (value == null) colors.accent else colors.contentMuted.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onChange(null) }
                    .semantics { contentDescription = "Gradyan yok"; if (value == null) selected = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Block, null, tint = colors.contentMuted) }
            Gradients.presets.forEachIndexed { i, g ->
                val chosen = value == g
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(g.brush())
                        .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onChange(g) }
                        .testTag("gradient_preset_$i")
                        .semantics { contentDescription = "Gradyan ${i + 1}"; if (chosen) selected = true },
                )
            }
        }
        if (value != null) {
            // Özel: renk durakları (dokununca o durağın rengi aşağıdan seçilir), açı ve dairesel
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gradient_stops), style = MaterialTheme.typography.labelLarge)
                value.colors.forEachIndexed { i, hex ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(hexToColor(hex))
                            .border(if (editing == i) 3.dp else 1.dp, if (editing == i) colors.accent else colors.contentMuted, CircleShape)
                            .clickable { editing = i }
                            .semantics { contentDescription = "Durak ${i + 1}"; if (editing == i) selected = true },
                    )
                }
                if (value.colors.size < 3) NeuChip("+", false, onClick = { onChange(value.copy(colors = value.colors + value.colors.last())); editing = value.colors.size })
                if (value.colors.size > 2) NeuChip("−", false, onClick = { onChange(value.copy(colors = value.colors.dropLast(1))); editing = 0 })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GradientSwatches.forEach { hex ->
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(hexToColor(hex))
                            .border(1.dp, colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                            .clickable {
                                val idx = editing.coerceIn(0, value.colors.size - 1)
                                onChange(value.copy(colors = value.colors.mapIndexed { j, c -> if (j == idx) hex else c }))
                            }
                            .semantics { contentDescription = hex },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0f, 45f, 90f, 135f, 180f, 270f).forEach { a ->
                    NeuChip("${a.toInt()}°", !value.radial && value.angle == a, onClick = { onChange(value.copy(angle = a, radial = false)) })
                }
                NeuChip(stringResource(R.string.gradient_radial), value.radial, onClick = { onChange(value.copy(radial = !value.radial)) })
            }
            Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(28.dp).clip(RoundedCornerShape(8.dp)).background(value.brush()))
        }
    }
}

/** Özel gradyan önizleme rengi (yoksa saydam). */
fun gradientPreviewColor(spec: GradientSpec?): Color = spec?.let { Color(Gradients.average(it)) } ?: Color.Transparent
