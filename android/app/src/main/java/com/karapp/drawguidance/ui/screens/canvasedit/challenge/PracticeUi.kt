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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
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

    /** v51 ödüllü reklam yükleniyor / gösteriliyor. */
    var rewardBusy by mutableStateOf(false)

    /** v51 ödüllü reklam gösterilemedi uyarısı. */
    var rewardFailed by mutableStateOf(false)

    /** v51 kartın açıklama satırı açık mı (alıştırma başında birkaç saniye açık, sonra küçülür). */
    var introOpen by mutableStateOf(true)

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

    val remaining by ctl.remaining.collectAsState()
    val unlocked by ctl.unlocked.collectAsState()
    val lockedIndex by ctl.locked.collectAsState()
    Box(Modifier.fillMaxSize().testTag("practice_layer")) {
        if (current != null) {
            LaunchedEffect(current.seq) {
                ui.introOpen = true
                delay(INTRO_MS)
                ui.introOpen = false
            }
            PracticeCard(
                current, ctl.levels().size, live, hints, remaining, ui.introOpen,
                Modifier.compactNotice(),
                onToggleIntro = { ui.introOpen = !ui.introOpen },
                onHint = ctl::hint, onFinish = { ctl.finish() }, onList = { ui.sheet = true }, onClose = ctl::stop,
            )
        }
    }

    result?.let { r ->
        PracticeResultDialog(
            r, ctl.levels().getOrNull(r.index),
            onNext = { ctl.next() }, onRetry = { ctl.retry() },
            onList = { ctl.dismissResult(); ui.sheet = true }, onClose = ctl::stop,
        )
    }
    if (ui.sheet) {
        PracticeSheet(
            levels = ctl.levels(), best = best, current = current?.index, active = current != null,
            // unlocked okunur: açılınca liste yeniden çizilir (kilit rozeti kalkar)
            locked = { it.id !in unlocked && ctl.isLocked(it) },
            onOpen = { i -> ui.sheet = false; ctl.open(i) },
            onStop = { ui.sheet = false; ctl.stop() },
            onDismiss = { ui.sheet = false },
        )
    }
    // v51 ödüllü reklamla açılan alıştırma
    val ads = org.koin.compose.koinInject<com.karapp.drawguidance.data.ads.AdsCoordinator>()
    val activity = LocalContext.current.let { c ->
        generateSequence(c) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
    }
    lockedIndex?.let { index ->
        val level = ctl.levels().getOrNull(index)
        if (level == null) { ctl.dismissLocked(); return@let }
        PracticeRewardDialog(
            level, busy = ui.rewardBusy, failed = ui.rewardFailed,
            onWatch = {
                val act = activity ?: return@PracticeRewardDialog
                ui.rewardBusy = true
                ui.rewardFailed = false
                ads.requestReward(act) { outcome ->
                    ui.rewardBusy = false
                    when (outcome) {
                        com.karapp.drawguidance.data.ads.RewardOutcome.EARNED,
                        com.karapp.drawguidance.data.ads.RewardOutcome.FREE -> ctl.unlockAndOpen(index)
                        com.karapp.drawguidance.data.ads.RewardOutcome.CANCELLED -> Unit
                        com.karapp.drawguidance.data.ads.RewardOutcome.UNAVAILABLE -> ui.rewardFailed = true
                    }
                }
            },
            onDismiss = { if (!ui.rewardBusy) { ui.rewardFailed = false; ctl.dismissLocked() } },
        )
    }
}

/** Açıklama satırının alıştırma başında açık kaldığı süre. */
private const val INTRO_MS = 4500L

/** v51 dikey ekranda mı (bildirim kartı üstte ince satır; yatayda solda dar sütun). */
@Composable
internal fun isPortrait(): Boolean {
    val c = LocalConfiguration.current
    return c.screenHeightDp >= c.screenWidthDp
}

/**
 * v51 çizim alanındaki bildirim kartlarının (alıştırma, çocuk görevi) ortak yeri: dikey ekranda üstte ortada, alçak ve
 * en çok 460 dp; yatay ekranda sol üstte dar (en çok 260 dp) — çizim alanının ortasını kapatmaz.
 */
@Composable
internal fun Modifier.compactNotice(): Modifier =
    if (isPortrait()) this.fillMaxSize().wrapContentHeight(Alignment.Top).padding(top = 52.dp, start = 10.dp, end = 10.dp)
        .wrapContentWidth().widthIn(max = 460.dp)
    else this.fillMaxSize().wrapContentHeight(Alignment.Top).wrapContentWidth(Alignment.Start)
        .padding(top = 52.dp, start = 8.dp).widthIn(max = 260.dp)

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
                // v51 Bakarak çiz: örnek çerçevesi (açık zemin) ve çizim çerçevesi (kesikli)
                target.reference?.let { r ->
                    val corner = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx() / sc)
                    val pad = 8.dp.toPx() / sc
                    drawRoundRect(
                        Color.White.copy(alpha = 0.85f), androidx.compose.ui.geometry.Offset(r.left - pad, r.top - pad),
                        androidx.compose.ui.geometry.Size(r.width + 2 * pad, r.height + 2 * pad), corner,
                    )
                    drawRoundRect(
                        GuideBlue.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(r.left - pad, r.top - pad),
                        androidx.compose.ui.geometry.Size(r.width + 2 * pad, r.height + 2 * pad), corner, style = Stroke(1.5.dp.toPx() / sc),
                    )
                    val b = target.box
                    drawRoundRect(
                        GuideBlue.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(b.left - pad, b.top - pad),
                        androidx.compose.ui.geometry.Size(b.width + 2 * pad, b.height + 2 * pad), corner,
                        style = Stroke(1.5.dp.toPx() / sc, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx() / sc, 6.dp.toPx() / sc))),
                    )
                    val solid = Stroke(1.6.dp.toPx() / sc, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    paths.forEach { drawPath(it, Color(0xFF1F2937), style = solid) }
                    return@scale
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

/**
 * v51 küçük bildirim kartı (çizimi kapatmaz). Dikey ekranda tek alçak satır: tür simgesi, ad ve sıra, anlık puan (süreli
 * çizimde kalan süre), İpucu, Liste, Bitti ve Kapat; açıklama alıştırma başında bir süre görünür, ada dokununca açılıp
 * kapanır. Yatay ekranda solda dar sütun: aynı ögeler alt alta.
 */
@Composable
private fun PracticeCard(
    run: PracticeRun,
    count: Int,
    live: Int?,
    hints: Int,
    remaining: Int?,
    intro: Boolean,
    modifier: Modifier,
    onToggleIntro: () -> Unit,
    onHint: () -> Unit,
    onFinish: () -> Unit,
    onList: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = NeuTheme.colors
    val level = run.level
    val portrait = isPortrait()
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.shadow(4.dp, shape).clip(shape).background(colors.background.copy(alpha = 0.96f))
            .border(1.dp, colors.outline.copy(alpha = 0.7f), shape)
            .padding(horizontal = 8.dp, vertical = 5.dp).testTag("practice_card"),
    ) {
        val title: @Composable (Modifier) -> Unit = { m ->
            Column(m.clickable(onClick = onToggleIntro)) {
                Text(
                    I18n.tr(level.title), color = colors.content, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(level.kind.label) + " · " + stringResource(R.string.v50_practice_level, run.index + 1, count),
                    color = colors.contentMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val score: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (remaining != null) {
                    val urgent = remaining <= 5 && run.startedAt != null
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).background((if (urgent) Color(0xFFDC2626) else colors.accent).copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp).testTag("practice_timer"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Timer, null, Modifier.size(13.dp), tint = if (urgent) Color(0xFFDC2626) else colors.accent)
                        Text(" $remaining", color = if (urgent) Color(0xFFDC2626) else colors.content, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "${live ?: 0}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(scoreColor(live ?: 0))
                        .padding(horizontal = 7.dp, vertical = 2.dp).testTag("practice_live"),
                )
            }
        }
        val actions: @Composable () -> Unit = {
            if (level.kind == PracticeKind.FADE) {
                SmallAction(Icons.Filled.Lightbulb, stringResource(R.string.v50_practice_hint) + if (hints > 0) " ($hints)" else "", "practice_hint", onHint)
            }
            SmallAction(Icons.Filled.FormatListBulleted, stringResource(R.string.v50_practice_list), "practice_list", onList)
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF16A34A)).clickable(onClick = onFinish).testTag("practice_finish"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, stringResource(R.string.v50_practice_done), tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        if (portrait) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KindBadge(level.kind)
                title(Modifier.weight(1f, fill = false).widthIn(min = 60.dp))
                Spacer(Modifier.width(2.dp))
                score()
                actions()
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.v50_practice_close), tint = colors.contentMuted, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KindBadge(level.kind)
                title(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.v50_practice_close), tint = colors.contentMuted, modifier = Modifier.size(18.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                score()
                Spacer(Modifier.weight(1f))
                actions()
            }
        }
        if (intro) {
            Text(
                stringResource(level.kind.intro), color = colors.contentMuted, fontSize = 11.sp, lineHeight = 14.sp,
                modifier = Modifier.padding(top = 3.dp).testTag("practice_intro"),
            )
        }
    }
}

/** Kartta küçük yuvarlak düğme (simge; açıklaması erişilebilirlikte). */
@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.12f)).clickable(onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = colors.accent, modifier = Modifier.size(18.dp)) }
}

/** Alıştırma türünün renkli simgesi. */
@Composable
private fun KindBadge(kind: PracticeKind, size: Int = 28) {
    val (icon, tint) = kindLook(kind)
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.6f).dp))
    }
}

private fun kindLook(kind: PracticeKind): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> = when (kind) {
    PracticeKind.TRACE -> Icons.Filled.FitnessCenter to Color(0xFF2563EB)
    PracticeKind.FADE -> Icons.Filled.Lightbulb to Color(0xFF9333EA)
    PracticeKind.MIRROR -> Icons.Filled.Replay to Color(0xFFDC2626)
    PracticeKind.COPY -> Icons.Filled.PlayCircle to Color(0xFF0D9488)
    PracticeKind.TIMED -> Icons.Filled.Timer to Color(0xFFEA580C)
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

/**
 * v51 sonuç penceresi: renkli başlık (geçti / tekrar dene), dolan puan halkası, yıldızlar, rekor rozeti, puanın
 * parçaları (isabet, kapsama, akıcılık, süreli çizimde hız) çubuklarla, en iyi puan ve düğmeler.
 */
@Composable
private fun PracticeResultDialog(
    result: PracticeResult, level: PracticeLevel?, onNext: () -> Unit, onRetry: () -> Unit, onList: () -> Unit, onClose: () -> Unit,
) {
    val colors = NeuTheme.colors
    val passed = result.passed
    val top = if (passed) listOf(Color(0xFF22C55E), Color(0xFF0EA5E9)) else listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
    val ring = remember(result) { Animatable(0f) }
    LaunchedEffect(result) { ring.animateTo(result.score / 100f, tween(900)) }
    Dialog(onDismissRequest = onList, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 380.dp).fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)).background(colors.background).verticalScroll(rememberScrollState())
                .testTag("practice_result"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(top)).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(if (result.timeUp) R.string.v51_practice_time_up else if (passed) R.string.v50_practice_passed else R.string.v50_practice_failed),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    )
                    level?.let {
                        Text(
                            I18n.tr(it.title) + " · " + stringResource(it.kind.label), color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(Modifier.padding(top = 16.dp).size(118.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxSize(), color = colors.outline.copy(alpha = 0.35f), strokeWidth = 10.dp)
                CircularProgressIndicator(
                    progress = { ring.value }, modifier = Modifier.fillMaxSize(), color = scoreColor(result.score), strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(ring.value * 100).toInt()}", color = colors.content, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("practice_result_score"),
                    )
                    Text(stringResource(R.string.v51_practice_points), color = colors.contentMuted, fontSize = 11.sp)
                }
            }
            Stars(result.stars, 28)
            if (result.record) {
                Row(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF59E0B).copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                    Text(" " + stringResource(R.string.v51_practice_record), color = Color(0xFFB45309), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val d = result.detail
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ScoreBar(stringResource(R.string.v51_practice_accuracy), d.accuracy, "practice_part_accuracy")
                ScoreBar(stringResource(R.string.v51_practice_coverage), d.coverage, "practice_part_coverage")
                ScoreBar(stringResource(R.string.v51_practice_smoothness), d.smoothness, "practice_part_smoothness")
                d.speed?.let { ScoreBar(stringResource(R.string.v51_practice_speed), it, "practice_part_speed") }
            }
            Text(stringResource(R.string.v50_practice_best, result.best), color = colors.contentMuted, fontSize = 13.sp)
            if (result.hints > 0) {
                Text(
                    stringResource(R.string.v50_practice_hints_used, result.hints, result.hints * PracticeLevels.HINT_PENALTY),
                    color = colors.contentMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (passed && !result.last) {
                    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("practice_next"), shape = RoundedCornerShape(14.dp)) {
                        Text(stringResource(R.string.v50_practice_next))
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                    }
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(stringResource(R.string.v50_practice_retry))
                    }
                } else {
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("practice_retry"), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.Replay, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.v50_practice_retry))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onList) { Text(stringResource(R.string.v50_practice_list)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.v50_practice_close)) }
                }
            }
        }
    }
}

/** Puan parçası: ad, çubuk ve değer. */
@Composable
private fun ScoreBar(label: String, value: Int, tag: String) {
    val colors = NeuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(tag)) {
        Text(label, color = colors.content, fontSize = 13.sp, modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        LinearProgressIndicator(
            progress = { value / 100f }, modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = scoreColor(value), trackColor = colors.outline.copy(alpha = 0.3f),
        )
        Text("$value", color = colors.content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
    }
}

/** v51 ödüllü reklamla açılan alıştırmanın penceresi. */
@Composable
private fun PracticeRewardDialog(level: PracticeLevel, busy: Boolean, failed: Boolean, onWatch: () -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        icon = { Icon(Icons.Filled.Lock, null, tint = Color(0xFFF59E0B)) },
        title = { Text(stringResource(R.string.v51_practice_reward_title), color = colors.content) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                PracticeThumb(level, Modifier.size(110.dp, 90.dp))
                Text(I18n.tr(level.title) + " · " + stringResource(level.kind.label), color = colors.content, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.v51_practice_reward_body), color = colors.contentMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                if (failed) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.v51_practice_reward_failed), color = Color(0xFFDC2626), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            Button(onClick = onWatch, enabled = !busy, modifier = Modifier.testTag("practice_reward_watch")) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Filled.PlayCircle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.v51_practice_reward_watch))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.v51_practice_reward_later)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeSheet(
    levels: List<PracticeLevel>,
    best: Map<String, Int>,
    current: Int?,
    active: Boolean,
    locked: (PracticeLevel) -> Boolean,
    onOpen: (Int) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    NeuSheet(onDismiss, full = true) {
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
                    items.forEach { (i, level) -> PracticeTile(level, best[level.id], i == current, locked(level)) { onOpen(i) } }
                }
            }
        }
    }
}

@Composable
private fun PracticeTile(level: PracticeLevel, score: Int?, current: Boolean, locked: Boolean, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    Box {
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
    // v51 ödüllü reklamla açılan alıştırma: köşede kilit ve oynat rozeti
    if (locked) {
        Row(
            Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xE61E1B4B))
                .padding(horizontal = 4.dp, vertical = 2.dp).testTag("practice_locked_${level.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayCircle, stringResource(R.string.v51_practice_reward_badge), tint = Color(0xFFF5B301), modifier = Modifier.size(13.dp))
        }
    }
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
