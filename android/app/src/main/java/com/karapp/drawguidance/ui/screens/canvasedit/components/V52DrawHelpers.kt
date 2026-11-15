package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasThumbnail
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v52 yeni çizim yardımcıları (basit ve gelişmiş resim; yalnız görünüm, tuvale yazılmaz):
 *  - **Kompozisyon kılavuzu**: üçler kuralı, altın oran, köşegenler ya da merkez çizgileri kâğıdın üstünde.
 *  - **Kontrol önizlemesi**: köşede küçük önizleme; aynalı (yatay çevrilmiş) bakış oran ve simetri hatalarını, gri ton
 *    bakış açık-koyu (değer) dengesini gösterir. Ressamların tuvali aynada ya da gözünü kısarak kontrol etmesinin karşılığı.
 */
enum class CompositionGuide { NONE, THIRDS, GOLDEN, DIAGONALS, CENTER }

class DrawHelpersState {
    var composition by mutableStateOf(CompositionGuide.NONE)
    var preview by mutableStateOf(false)
    var mirror by mutableStateOf(true)
    var gray by mutableStateOf(false)
    var sheet by mutableStateOf(false)
    val active: Boolean get() = composition != CompositionGuide.NONE || preview
}

@Composable
fun rememberDrawHelpers(): DrawHelpersState = remember { DrawHelpersState() }

/** Kâğıt üstündeki kompozisyon çizgileri (oranlar kâğıdın 0..1'i). */
object CompositionLines {
    private const val PHI = 0.618034f

    fun lines(g: CompositionGuide): List<Pair<Offset, Offset>> = when (g) {
        CompositionGuide.NONE -> emptyList()
        CompositionGuide.THIRDS -> listOf(1f / 3, 2f / 3).flatMap { v -> listOf(Offset(v, 0f) to Offset(v, 1f), Offset(0f, v) to Offset(1f, v)) }
        CompositionGuide.GOLDEN -> listOf(1f - PHI, PHI).flatMap { v -> listOf(Offset(v, 0f) to Offset(v, 1f), Offset(0f, v) to Offset(1f, v)) }
        CompositionGuide.DIAGONALS -> listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(1f, 0f) to Offset(0f, 1f))
        CompositionGuide.CENTER -> listOf(Offset(0.5f, 0f) to Offset(0.5f, 1f), Offset(0f, 0.5f) to Offset(1f, 0.5f))
    }
}

/** Kompozisyon çizgileri tuvalin görünümüne göre (dokunuşu tutmaz). */
@Composable
fun CompositionOverlay(guide: CompositionGuide, canvas: CanvasUi, viewport: ViewportState) {
    if (guide == CompositionGuide.NONE) return
    val lines = remember(guide) { CompositionLines.lines(guide) }
    val accent = NeuTheme.colors.accent
    Canvas(Modifier.fillMaxSize().testTag("composition_overlay")) {
        val w = canvas.width * viewport.scale
        val h = canvas.height * viewport.scale
        val o = viewport.offset
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        lines.forEach { (a, b) ->
            val p = Offset(o.x + a.x * w, o.y + a.y * h)
            val q = Offset(o.x + b.x * w, o.y + b.y * h)
            drawLine(Color.White.copy(alpha = 0.7f), p, q, strokeWidth = 4f)
            drawLine(accent.copy(alpha = 0.85f), p, q, strokeWidth = 2f, pathEffect = dash)
        }
        if (guide == CompositionGuide.THIRDS || guide == CompositionGuide.GOLDEN) {
            // Güçlü noktalar: çizgilerin kesişimleri
            val xs = lines.filter { it.first.y == 0f }.map { it.first.x }
            val ys = lines.filter { it.first.x == 0f }.map { it.first.y }
            xs.forEach { x -> ys.forEach { y -> drawCircle(accent.copy(alpha = 0.8f), 7f, Offset(o.x + x * w, o.y + y * h)) } }
        }
    }
}

/** Köşedeki kontrol önizlemesi: aynalı ve/veya gri ton. */
@Composable
fun CheckPreview(state: DrawHelpersState, canvas: CanvasUi, modifier: Modifier = Modifier) {
    if (!state.preview) return
    val colors = NeuTheme.colors
    val strokes = remember(canvas.strokes, canvas.layers) { com.karapp.drawguidance.data.model.Layers.visible(canvas.strokes, canvas.layers) }
    val grayPaint = remember { Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) } }
    NeuSurface(modifier.width(132.dp).testTag("check_preview"), elevation = 6.dp, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(canvas.width.toFloat() / canvas.height.coerceAtLeast(1))
                    .clip(RoundedCornerShape(8.dp)).border(1.dp, colors.contentMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .graphicsLayer { scaleX = if (state.mirror) -1f else 1f }
                    .drawWithContent {
                        if (!state.gray) drawContent() else drawIntoCanvas { c ->
                            c.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), grayPaint)
                            drawContent()
                            c.restore()
                        }
                    },
            ) {
                CanvasThumbnail(canvas.width, canvas.height, canvas.pagePaperHex, strokes, Modifier.fillMaxSize(), canvas.pageBackground)
            }
            Text(
                stringResource(
                    when {
                        state.mirror && state.gray -> R.string.v52_helper_preview_both
                        state.mirror -> R.string.v52_helper_mirror
                        state.gray -> R.string.v52_helper_gray
                        else -> R.string.v52_helper_preview
                    },
                ),
                fontSize = 10.sp, color = colors.contentMuted, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Yardımcıların ayar penceresi. */
@Composable
fun DrawHelpersSheet(state: DrawHelpersState) {
    if (!state.sheet) return
    val colors = NeuTheme.colors
    com.karapp.drawguidance.ui.components.NeuSheet(onDismiss = { state.sheet = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("draw_helpers_sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.v52_helpers_title), color = colors.content, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(stringResource(R.string.v52_helper_composition), color = colors.content, fontSize = 15.sp)
            Text(stringResource(R.string.v52_helper_composition_hint), color = colors.contentMuted, fontSize = 12.sp)
            val options = listOf(
                CompositionGuide.NONE to R.string.v52_helper_none,
                CompositionGuide.THIRDS to R.string.v52_helper_thirds,
                CompositionGuide.GOLDEN to R.string.v52_helper_golden,
                CompositionGuide.DIAGONALS to R.string.v52_helper_diagonals,
                CompositionGuide.CENTER to R.string.v52_helper_center,
            )
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (g, label) ->
                    NeuChip(stringResource(label), state.composition == g, { state.composition = g }, Modifier.testTag("helper_comp_" + g.name.lowercase()))
                }
            }
            HelperSwitch(stringResource(R.string.v52_helper_preview), stringResource(R.string.v52_helper_preview_hint), state.preview, "helper_preview") { state.preview = it }
            if (state.preview) {
                HelperSwitch(stringResource(R.string.v52_helper_mirror), stringResource(R.string.v52_helper_mirror_hint), state.mirror, "helper_mirror") { state.mirror = it }
                HelperSwitch(stringResource(R.string.v52_helper_gray), stringResource(R.string.v52_helper_gray_hint), state.gray, "helper_gray") { state.gray = it }
            }
        }
    }
}

@Composable
private fun HelperSwitch(title: String, hint: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val colors = NeuTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.content, fontSize = 15.sp)
            Text(hint, color = colors.contentMuted, fontSize = 12.sp)
        }
        NeuSwitch(on, onChange, Modifier.testTag(tag))
    }
}
