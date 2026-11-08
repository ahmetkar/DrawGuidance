package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.drawing.TextOnPath
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v42 yol üzerinde yazı: yazının yerleşeceği yol seçilir. Sayfadaki kalem yolları kesik vurgu çizgisiyle işaretlenir;
 * bir yola dokununca yazı onun üstüne yerleşir. Boşluğa dokunmak bir şey yapmaz (ipucu kırmızılaşır); Vazgeç kapatır.
 */
@Composable
fun TextPathPickOverlay(
    hosts: List<com.karapp.drawguidance.data.model.Stroke>,
    viewport: ViewportState,
    onPick: (canvasPoint: Offset, tolerance: Float) -> Boolean,
    onCancel: () -> Unit,
) {
    val colors = NeuTheme.colors
    var missed by remember { mutableStateOf(false) }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f) }
    Box(Modifier.fillMaxSize().testTag("text_path_pick")) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { pos ->
                    val ok = onPick(viewport.toCanvas(pos), 28.dp.toPx() / viewport.scale.coerceAtLeast(0.05f))
                    missed = !ok
                }
            },
        ) {
            hosts.forEach { s ->
                val pts = TextOnPath.points(s).map { viewport.toScreen(it) }
                if (pts.size < 2) return@forEach
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) }
                drawPath(path, colors.accent.copy(alpha = 0.35f), style = Stroke(width = 14f))
                drawPath(path, colors.accent, style = Stroke(width = 3f, pathEffect = dash))
            }
        }
        NeuSurface(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), elevation = 8.dp) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(if (missed) R.string.v42_text_path_missed else R.string.v42_text_path_pick),
                    style = MaterialTheme.typography.bodyMedium, color = if (missed) colors.danger else colors.content,
                    modifier = Modifier.weight(1f).testTag("text_path_hint"),
                )
                NeuButton(stringResource(R.string.cancel_button), onCancel, Modifier.testTag("text_path_cancel"))
            }
        }
    }
}
