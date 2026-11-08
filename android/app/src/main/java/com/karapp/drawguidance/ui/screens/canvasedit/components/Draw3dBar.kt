package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.Plane3D
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.screens.canvasedit.CameraPreset
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** v21 3B çizim düzleminin adı. */
@Composable
fun plane3dLabel(plane: Plane3D): String = stringResource(
    when (plane) {
        Plane3D.FREE -> R.string.draw3d_plane_free
        Plane3D.FRONT -> R.string.draw3d_plane_front
        Plane3D.SIDE -> R.string.draw3d_plane_side
        Plane3D.TOP -> R.string.draw3d_plane_top
    },
)

/**
 * 3B çizim modunun alt çubuğu: çizim düzlemi (serbest, ön, yan, üst) ve derinlik (izlerin kalınlık yönünde ne kadar
 * kabaracağı). v22 döndürülebilir sahne: döndürme alanı (sürükleyince sahne döner; içinde X, Y, Z eksenleri), hazır
 * açılar (izometrik, önden, yandan, üstten) ve perspektif. Kapat düğmesi modu kapatır.
 */
@Composable
fun Draw3dBar(
    plane: Plane3D,
    depth: Float,
    onPlane: (Plane3D) -> Unit,
    onDepth: (Float, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    yaw: Float = com.karapp.drawguidance.data.model.GuideSpec.DEFAULT_YAW,
    pitch: Float = com.karapp.drawguidance.data.model.GuideSpec.DEFAULT_PITCH,
    persp: Float = 0f,
    /** Döndürme: yatay ve dikey açı farkı (derece); son adımda kalıcı. */
    onOrbit: (Float, Float, Boolean) -> Unit = { _, _, _ -> },
    onPreset: (CameraPreset) -> Unit = {},
    onPersp: (Float, Boolean) -> Unit = { _, _ -> },
) {
    val colors = NeuTheme.colors
    var local by remember(depth) { mutableFloatStateOf(depth) }
    var localPersp by remember(persp) { mutableFloatStateOf(persp) }
    var collapsed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    NeuSurface(modifier.testTag("draw3d_bar"), shape = RoundedCornerShape(22.dp), elevation = 10.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.ViewInAr, null, tint = colors.accent)
                Text(stringResource(R.string.draw3d_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                // v22: çubuk küçültülebilir (tuvalin altı görünsün; 3B mod açık kalır)
                NeuIconButton(
                    if (collapsed) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    stringResource(if (collapsed) R.string.draw3d_expand else R.string.draw3d_collapse),
                    { collapsed = !collapsed }, size = 30.dp, modifier = Modifier.testTag("draw3d_collapse"),
                )
                NeuIconButton(Icons.Filled.Close, stringResource(R.string.draw3d_close), onClose, size = 30.dp)
            }
            if (collapsed) return@Column
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Plane3D.entries.forEach { p ->
                    NeuChip(plane3dLabel(p), p == plane, { onPlane(p) }, Modifier.testTag("draw3d_plane_${p.name.lowercase()}"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrbitPad(yaw, pitch, onOrbit)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            CameraPreset.ISO to R.string.draw3d_view_iso, CameraPreset.FRONT to R.string.draw3d_view_front,
                            CameraPreset.SIDE to R.string.draw3d_view_side, CameraPreset.TOP to R.string.draw3d_view_top,
                        ).forEach { (preset, label) ->
                            NeuChip(stringResource(label), isPreset(preset, yaw, pitch), { onPreset(preset) }, Modifier.testTag("draw3d_view_${preset.name.lowercase()}"))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.draw3d_persp), style = MaterialTheme.typography.labelLarge)
                        NeuSlider(
                            value = localPersp,
                            onValueChange = { localPersp = it; onPersp(it, false) },
                            onValueChangeFinished = { onPersp(localPersp, true) },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f).testTag("draw3d_persp"),
                        )
                        Text("%${(localPersp * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.draw3d_depth), style = MaterialTheme.typography.labelLarge)
                        NeuSlider(
                            value = local,
                            onValueChange = { local = it; onDepth(it, false) },
                            onValueChangeFinished = { onDepth(local, true) },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f).testTag("draw3d_depth"),
                        )
                        Text("%${(local * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
                    }
                }
            }
        }
    }
}

private fun isPreset(p: CameraPreset, yaw: Float, pitch: Float): Boolean {
    fun near(a: Float, b: Float) = abs(a - b) < 0.5f
    return when (p) {
        CameraPreset.ISO -> near(yaw, com.karapp.drawguidance.data.model.GuideSpec.DEFAULT_YAW) && near(pitch, com.karapp.drawguidance.data.model.GuideSpec.DEFAULT_PITCH)
        CameraPreset.FRONT -> (near(yaw, 0f) || near(yaw, 360f)) && near(pitch, 0f)
        CameraPreset.SIDE -> near(yaw, 90f) && near(pitch, 0f)
        CameraPreset.TOP -> near(pitch, 90f)
    }
}

/**
 * Döndürme alanı: sürükleyince sahne döner (yatay: sağa-sola dönüş, dikey: yukarıdan-aşağıdan bakış). İçinde sahnenin
 * X (kırmızı), Y (yeşil) ve Z (mavi) eksenleri o anki görünümle çizilir. Çok sık güncellemeyi önlemek için hareket
 * birikip en çok ~30 kez/sn uygulanır; parmak kalkınca son hali kaydedilir.
 */
@Composable
private fun OrbitPad(yaw: Float, pitch: Float, onOrbit: (Float, Float, Boolean) -> Unit) {
    val colors = NeuTheme.colors
    val cd = stringResource(R.string.draw3d_orbit)
    val orbit by rememberUpdatedState(onOrbit)
    Box(
        Modifier.size(92.dp).clip(CircleShape).background(colors.background).border(1.dp, colors.accent.copy(alpha = 0.45f), CircleShape)
            .semantics { contentDescription = cd }
            .testTag("draw3d_orbit")
            .pointerInput(Unit) {
                var ax = 0f; var ay = 0f; var last = 0L
                val degPerPx = 0.45f / density
                detectDragGestures(
                    onDragEnd = { orbit(ax, ay, true); ax = 0f; ay = 0f },
                    onDragCancel = { orbit(ax, ay, true); ax = 0f; ay = 0f },
                ) { change, drag ->
                    change.consume()
                    ax += drag.x * degPerPx; ay += drag.y * degPerPx
                    val now = change.uptimeMillis
                    if (now - last >= 33L) {
                        last = now
                        orbit(ax, ay, false); ax = 0f; ay = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val cam = remember(yaw, pitch) { com.karapp.drawguidance.ui.drawing.SceneCamera(yaw, pitch, 0f, 0f, 0f, 1f) }
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val len = size.minDimension * 0.46f
            val axes = listOf(
                Triple(floatArrayOf(1f, 0f, 0f), Color(0xFFE53935), cam.depth(1f, 0f, 0f)),
                Triple(floatArrayOf(0f, 1f, 0f), Color(0xFF43A047), cam.depth(0f, 1f, 0f)),
                Triple(floatArrayOf(0f, 0f, 1f), Color(0xFF1E88E5), cam.depth(0f, 0f, 1f)),
            ).sortedBy { it.third }
            drawCircle(colors.contentMuted.copy(alpha = 0.18f), radius = len, center = c)
            axes.forEach { (v, color, _) ->
                val p = cam.project(v[0], v[1], v[2])
                val end = Offset(c.x + p.x * len, c.y + p.y * len)
                drawLine(color, c, end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color, radius = 3.5.dp.toPx(), center = end)
            }
        }
    }
}
