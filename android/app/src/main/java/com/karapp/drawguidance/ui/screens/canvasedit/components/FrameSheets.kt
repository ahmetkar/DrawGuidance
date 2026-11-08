package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.components.NeuSheet

/** Tamsayı alanı: yalnızca rakam (ve eksi) kabul edilir. */
private fun digits(text: String, allowNegative: Boolean): String {
    val body = text.filter { it.isDigit() }.take(5)
    return if (allowNegative && text.trimStart().startsWith("-")) "-$body" else body
}

/**
 * v18 Taşı: seçimin sol üst köşesinin konumu (tuval pikseli). Değerler girilip Uygula'ya basılınca seçim oraya taşınır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionSheet(x: Int, y: Int, onApply: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    var xs by remember { mutableStateOf(x.toString()) }
    var ys by remember { mutableStateOf(y.toString()) }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("position_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.move_position_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.move_position_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuTextField(
                    xs, { xs = digits(it, true) }, label = stringResource(R.string.position_x), modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    fieldModifier = Modifier.testTag("position_x"),
                )
                NeuTextField(
                    ys, { ys = digits(it, true) }, label = stringResource(R.string.position_y), modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    fieldModifier = Modifier.testTag("position_y"),
                )
            }
            val nx = xs.toIntOrNull()
            val ny = ys.toIntOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f))
                NeuButton(
                    stringResource(R.string.apply_button), { if (nx != null && ny != null) { onApply(nx, ny); onDismiss() } },
                    Modifier.weight(1f), primary = true, enabled = nx != null && ny != null,
                )
            }
        }
    }
}

/**
 * v18 Genişlet: kağıdın genişliği ve yüksekliği ile sabit kalacak yer (3×3). Büyütünce kağıt seçilen yerden dışarı
 * doğru genişler (ör. sol üst seçiliyse sağa ve aşağı), küçültünce karşı kenarlardan kırpılır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasSizeSheet(width: Int, height: Int, onApply: (Int, Int, Float, Float) -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    var ws by remember { mutableStateOf(width.toString()) }
    var hs by remember { mutableStateOf(height.toString()) }
    var anchor by remember { mutableStateOf(0 to 0) }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("canvas_size_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.canvas_size_title), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuTextField(
                    ws, { ws = digits(it, false) }, label = stringResource(R.string.canvas_size_width), modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    fieldModifier = Modifier.testTag("canvas_size_width"),
                )
                NeuTextField(
                    hs, { hs = digits(it, false) }, label = stringResource(R.string.canvas_size_height), modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    fieldModifier = Modifier.testTag("canvas_size_height"),
                )
            }
            Text(stringResource(R.string.canvas_size_anchor), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in 0..2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in 0..2) {
                                val on = anchor == (c to r)
                                val description = stringResource(R.string.canvas_size_anchor_cell, c + 1, r + 1)
                                NeuSurface(
                                    elevation = if (on) 1.dp else 3.dp,
                                    selected = on,
                                    onClick = { anchor = c to r },
                                    modifier = Modifier.size(34.dp).semantics { contentDescription = description; selected = on },
                                ) {
                                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                        if (on) NeuSurface(elevation = 0.dp, fill = colors.accent, modifier = Modifier.size(12.dp)) {}
                                    }
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.canvas_size_hint), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            }
            val nw = ws.toIntOrNull()
            val nh = hs.toIntOrNull()
            val valid = nw != null && nh != null && nw in MIN_SIDE..MAX_SIDE && nh in MIN_SIDE..MAX_SIDE
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f))
                NeuButton(
                    stringResource(R.string.apply_button),
                    { if (valid) { onApply(nw!!, nh!!, anchor.first / 2f, anchor.second / 2f); onDismiss() } },
                    Modifier.weight(1f), primary = true, enabled = valid,
                )
            }
        }
    }
}

private const val MIN_SIDE = 64
private const val MAX_SIDE = 8192
