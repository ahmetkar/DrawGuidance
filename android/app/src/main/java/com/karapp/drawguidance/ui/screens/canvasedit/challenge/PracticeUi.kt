package com.karapp.drawguidance.ui.screens.canvasedit.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.delay

/*
 * v50 yetişkin çizim alıştırmalarının ekranı: menüdeki "Alıştırmalar" düğmesi, alıştırma listesi (gruplar, küçük
 * resimler, en iyi puan ve yıldızlar), kâğıttaki kılavuz (kesikli çizgiler; kaybolan kılavuzda birkaç saniye sonra
 * solar, simetri görevinde sol yarı ve eksen), üstteki kart (ad, tür, anlık puan, İpucu, Bitti) ve sonuç penceresi.
 */

@Stable
class PracticeUi {
    /** Alıştırma listesi açık mı. */
    var sheet by mutableStateOf(false)

    internal var label = ""

    /** Menüdeki "Alıştırmalar" düğmesi. */
    fun tool(active: Boolean): SimpleTool = SimpleTool("practice", Icons.Filled.FitnessCenter, label, sheet || active, { sheet = !sheet })
}

@Composable
fun rememberPracticeUi(): PracticeUi {
    val ui = remember { PracticeUi() }
    ui.label = stringResource(R.string.v50_practice)
    return ui
}

/** Kılavuzun rengi (tema vurgusundan bağımsız, açık ve koyu kâğıtta seçilir). */
private val GuideBlue = Color(0xFF2563EB)

/** Kaybolan kılavuzun ilk görünme süresi ve ipucunda yeniden görünme süresi (ms). */
private const val FADE_SHOW_MS = 5000L
private const val HINT_SHOW_MS = 2500L

@Composable
fun PracticeLayer(viewModel: CanvasEditViewModel, ui: PracticeUi, viewport: ViewportState, tutorial: Boolean) {
    if (tutorial) return
    val ctl = viewModel.practice
    val run by ctl.run.collectAsState()
    val result by ctl.result.collectAsState()
    val live by ctl.live.collectAsState()
    val hints by ctl.hints.collectAsState()
    val best by ctl.best.collectAsState()

    val current = run
    if (current != null) {
        val alpha = remember(current.seq) { Animatable(1f) }
        if (current.level.kind == PracticeKind.FADE) {
            LaunchedEffect(current.seq, hints) {
                alpha.snapTo(1f)
                delay(if (hints == 0) FADE_SHOW_MS else HINT_SHOW_MS)
                alpha.animateTo(0f, tween(1200))
            }
        }
        PracticeGuide(current.target, alpha.value, viewport)
        LaunchedEffect(current.seq) { viewport.fit() }
    }

    Box(Modifier.fillMaxSize().testTag("practice_layer")) {
        if (current != null) {
            PracticeCard(
                current, ctl.levels().size, live, hints,
                Modifier.align(Alignment.TopCenter).padding(top = 52.dp, start = 12.dp, end = 12.dp),
                onHint = ctl::hint, onFinish = { ctl.finish() }, onList = { ui.sheet = true }, onClose = ctl::stop,
            )
        }
    }

    result?.let { r ->
        PracticeResultDialog(
            r, onNext = { ctl.next() }, onRetry = { ctl.retry() },
            onList = { ctl.dismissResult(); ui.sheet = true }, onClose = ctl::stop,
        )
    }
    if (ui.sheet) {
        PracticeSheet(
            levels = ctl.levels(), best = best, current = current?.index, active = current != null,
            onOpen = { i -> ui.sheet = false; ctl.open(i) },
            onStop = { ui.sheet = false; ctl.stop() },
            onDismiss = { ui.sheet = false },
        )
    }
}

/** Kâğıttaki kılavuz (tuval koordinatında, görünümle birlikte). */
@Composable
private fun PracticeGuide(target: PracticeTarget, alpha: Float, viewport: ViewportState) {
    val paths = remember(target) {
        target.guide.map { line -> Path().apply { line.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } } }
    }
    Canvas(Modifier.fillMaxSize().testTag("practice_guide")) {
        val sc = viewport.scale
        translate(viewport.offset.x, viewport.offset.y) {
            scale(sc, sc, Offset.Zero) {
                target.axis?.let { x ->
                    val axisStyle = Stroke(
                        1.5.dp.toPx() / sc,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx() / sc, 4.dp.toPx() / sc)),
                    )
                    val pad = 12.dp.toPx() / sc
                    val axis = Path().apply { moveTo(x, target.box.top - pad); lineTo(x, target.box.bottom + pad) }
                    drawPath(axis, Color(0xFFDC2626).copy(alpha = 0.8f), style = axisStyle)
                }
                if (alpha <= 0.01f) return@scale
                val halo = Stroke(5.dp.toPx() / sc, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val dash = Stroke(
                    2.dp.toPx() / sc, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx() / sc, 5.dp.toPx() / sc)),
                )
                paths.forEach { drawPath(it, Color.White.copy(alpha = 0.55f * alpha), style = halo) }
                paths.forEach { drawPath(it, GuideBlue.copy(alpha = 0.75f * alpha), style = dash) }
            }
        }
    }
}

/** Üstteki kart: ad, tür, sıra, anlık puan; İpucu (kaybolan kılavuzda), Liste, Bitti ve Kapat. */
@Composable
private fun PracticeCard(
    run: PracticeRun,
    count: Int,
    live: Int?,
    hints: Int,
    modifier: Modifier,
    onHint: () -> Unit,
    onFinish: () -> Unit,
    onList: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = NeuTheme.colors
    val level = run.level
    Column(
        modifier.widthIn(max = 520.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).background(colors.background).border(1.dp, colors.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp).testTag("practice_card"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    I18n.tr(level.title), color = colors.content, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(level.kind.label) + " · " + stringResource(R.string.v50_practice_level, run.index + 1, count),
                    color = colors.contentMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(R.string.v50_practice_score, live ?: 0), color = scoreColor(live ?: 0), fontWeight = FontWeight.Bold,
                fontSize = 16.sp, modifier = Modifier.padding(horizontal = 6.dp).testTag("practice_live"),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.v50_practice_close), tint = colors.contentMuted)
            }
        }
        Text(stringResource(level.kind.intro), color = colors.contentMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onList, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Icon(Icons.Filled.FormatListBulleted, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.v50_practice_list))
            }
            if (level.kind == PracticeKind.FADE) {
                OutlinedButton(onClick = onHint, contentPadding = ButtonDefaults.TextButtonContentPadding, modifier = Modifier.testTag("practice_hint")) {
                    Icon(Icons.Filled.Lightbulb, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.v50_practice_hint) + if (hints > 0) " ($hints)" else "")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onFinish, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White),
                modifier = Modifier.testTag("practice_finish"),
            ) { Text(stringResource(R.string.v50_practice_done)) }
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= PracticeLevels.STARS[2] -> Color(0xFF16A34A)
    score >= PracticeLevels.PASS -> Color(0xFF65A30D)
    score >= 40 -> Color(0xFFD97706)
    else -> Color(0xFF9CA3AF)
}

@Composable
private fun Stars(stars: Int, size: Int = 14) {
    Text(
        "★".repeat(stars) + "☆".repeat(3 - stars), color = Color(0xFFF59E0B), fontSize = size.sp,
        modifier = Modifier.testTag("practice_stars"),
    )
}

@Composable
private fun PracticeResultDialog(result: PracticeResult, onNext: () -> Unit, onRetry: () -> Unit, onList: () -> Unit, onClose: () -> Unit) {
    val colors = NeuTheme.colors
    AlertDialog(
        onDismissRequest = onList,
        containerColor = colors.background,
        title = {
            Text(stringResource(if (result.passed) R.string.v50_practice_passed else R.string.v50_practice_failed), color = colors.content)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.v50_practice_score, result.score), color = scoreColor(result.score), fontSize = 30.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.testTag("practice_result_score"),
                )
                Stars(result.stars, 26)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.v50_practice_best, result.best), color = colors.contentMuted)
                if (result.hints > 0) {
                    Text(
                        stringResource(R.string.v50_practice_hints_used, result.hints, result.hints * PracticeLevels.HINT_PENALTY),
                        color = colors.contentMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            if (result.passed && !result.last) {
                Button(onClick = onNext, modifier = Modifier.testTag("practice_next")) { Text(stringResource(R.string.v50_practice_next)) }
            } else {
                Button(onClick = onRetry, modifier = Modifier.testTag("practice_retry")) { Text(stringResource(R.string.v50_practice_retry)) }
            }
        },
        dismissButton = {
            Row {
                if (result.passed && !result.last) TextButton(onClick = onRetry) { Text(stringResource(R.string.v50_practice_retry)) }
                TextButton(onClick = onList) { Text(stringResource(R.string.v50_practice_list)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.v50_practice_close)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeSheet(
    levels: List<PracticeLevel>,
    best: Map<String, Int>,
    current: Int?,
    active: Boolean,
    onOpen: (Int) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)
                .testTag("practice_sheet"),
        ) {
            Text(stringResource(R.string.v50_practice_title), color = colors.content, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(R.string.v50_practice_subtitle), color = colors.contentMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (active) {
                OutlinedButton(onClick = onStop, modifier = Modifier.padding(top = 8.dp).testTag("practice_stop")) {
                    Text(stringResource(R.string.v50_practice_stop))
                }
            }
            val passed = levels.count { (best[it.id] ?: 0) >= PracticeLevels.PASS }
            Text(
                stringResource(R.string.v50_practice_progress, passed, levels.size), color = colors.contentMuted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            for (group in PracticeGroup.entries) {
                val items = levels.withIndex().filter { it.value.group == group }
                if (items.isEmpty()) continue
                Text(
                    stringResource(group.title), color = colors.content, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { (i, level) -> PracticeTile(level, best[level.id], i == current) { onOpen(i) } }
                }
            }
        }
    }
}

@Composable
private fun PracticeTile(level: PracticeLevel, score: Int?, current: Boolean, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    Column(
        Modifier.width(104.dp).clip(RoundedCornerShape(12.dp))
            .border(if (current) 2.dp else 1.dp, if (current) colors.accent else colors.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(6.dp).testTag("practice_tile_${level.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PracticeThumb(level, Modifier.size(84.dp, 72.dp))
        Text(
            I18n.tr(level.title), color = colors.content, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Text(stringResource(level.kind.label), color = colors.contentMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (score != null) Stars(PracticeLevels.stars(score), 12) else Text("—", color = colors.contentMuted, fontSize = 12.sp)
    }
}

/** Alıştırmanın küçük resmi: görünen kılavuz (simetri görevinde sol yarı). */
@Composable
private fun PracticeThumb(level: PracticeLevel, modifier: Modifier) {
    val target = remember(level.id) { PracticeLevels.target(level, 1000, 1000) }
    val ink = NeuTheme.colors.content
    Canvas(modifier) {
        val box = ChallengeScore.bounds(target.lines) ?: return@Canvas
        val pad = 4.dp.toPx()
        val s = minOf((size.width - 2 * pad) / box.width, (size.height - 2 * pad) / box.height)
        val ox = (size.width - box.width * s) / 2f - box.left * s
        val oy = (size.height - box.height * s) / 2f - box.top * s
        val path = Path()
        target.guide.forEach { line -> line.forEachIndexed { i, p -> if (i == 0) path.moveTo(ox + p.x * s, oy + p.y * s) else path.lineTo(ox + p.x * s, oy + p.y * s) } }
        drawPath(path, ink.copy(alpha = 0.85f), style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        target.axis?.let { x ->
            val ax = ox + x * s
            drawLine(Color(0xFFDC2626), Offset(ax, oy + box.top * s), Offset(ax, oy + box.bottom * s), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
        }
    }
}
