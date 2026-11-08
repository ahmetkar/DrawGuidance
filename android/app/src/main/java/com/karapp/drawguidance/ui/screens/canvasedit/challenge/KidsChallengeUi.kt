package com.karapp.drawguidance.ui.screens.canvasedit.challenge

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.components.MascotButtonKind
import com.karapp.drawguidance.ui.components.MascotFace
import com.karapp.drawguidance.ui.components.MascotFigure
import com.karapp.drawguidance.ui.components.MascotGesture
import com.karapp.drawguidance.ui.components.MascotLook
import com.karapp.drawguidance.ui.components.MascotRoundButton
import com.karapp.drawguidance.ui.components.MascotSpeaker
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

/*
 * v38 çocuk modunda çizim görevinin ekranı: üst menüdeki "Çizim Görevi" düğmesi ve küçük menüsü (Başlat / İlerleme,
 * mod açıkken Modu kapat / İlerleme), kâğıttaki hedef (kesikli çizgiler, boyanacak yerlerin renkli noktaları), üstte
 * seviye kartı (örnek resim, seviye, Bitti), köşede yönlendiren maskot Boyacık ve sonuç / İlerleme pencereleri.
 */

/** Düğmenin ve menülerin açık/kapalı durumu (CanvasEditScreen üst menüye [tool] düğmesini ekler). */
@Stable
class KidsChallengeUi {
    /** Düğmenin küçük menüsü açık mı. */
    var menu by mutableStateOf(false)

    /** İlerleme penceresi açık mı. */
    var progress by mutableStateOf(false)

    internal var label = ""

    /** Üst menüdeki "Çizim Görevi" düğmesi: dokununca küçük menü açılır/kapanır. */
    fun tool(): SimpleTool = SimpleTool("challenge", Icons.Filled.EmojiEvents, label, menu || progress, {
        menu = !menu
        progress = false
    })
}

@Composable
fun rememberKidsChallengeUi(): KidsChallengeUi {
    val ui = remember { KidsChallengeUi() }
    ui.label = stringResource(R.string.kids_challenge)
    return ui
}

/** Maskotun bir sözü: cümle kaynağı, değerleri ve o sözdeki yüz ifadesi, el hareketi. */
private class Said(@StringRes val text: Int, val args: List<Any>, val look: MascotLook, val key: String)

/** Mod çubuğunun göründüğü modlar dışındakiler (bkz. KidsMascotLayer). */
private val NO_BAR = setOf(EditMode.DRAW, EditMode.GUIDE, EditMode.PERSPECTIVE, EditMode.ADD, EditMode.EDIT_PICK)

/** Maskotun puana göre yüzü (o an bir şey söylemiyorken): çizdikçe sevinci artar. */
internal fun liveLook(eval: ChallengeEval?, kind: ChallengeKind): MascotLook {
    val score = eval?.score ?: 0
    return when {
        eval == null || score <= 0 || (kind == ChallengeKind.TRACE && !eval.drawn) -> MascotLook(MascotFace.HAPPY, MascotGesture.WAVE)
        score < 35 -> MascotLook(MascotFace.HAPPY, MascotGesture.BRUSH)
        score < ChallengeScore.PASS -> MascotLook(MascotFace.WINKING, MascotGesture.THUMBS_UP)
        else -> MascotLook(MascotFace.EXCITED, MascotGesture.CHEER)
    }
}

/**
 * v38 çizim görevinin katmanı (çocuk tuvalinde, çizim alanının üstünde). Mod kapalıyken yalnız düğmenin menüsü ve
 * İlerleme penceresi; açıkken hedef, seviye kartı ve maskot da görünür. Eğitim sürerken gizlenir. Maskot, yapay zekâ
 * önerisi söylenirken (v37 maskotu konuşurken) çekilir.
 */
@Composable
fun KidsChallengeLayer(viewModel: CanvasEditViewModel, ui: KidsChallengeUi, viewport: ViewportState, mode: EditMode, barPx: Int, tutorial: Boolean) {
    if (tutorial) return
    val ctl = viewModel.challenge
    val run by ctl.run.collectAsState()
    val active by ctl.active.collectAsState()
    val result by ctl.result.collectAsState()
    val live by ctl.live.collectAsState()
    val best by ctl.best.collectAsState()
    val selected by viewModel.selectedIds.collectAsState()
    val textSuggestion by viewModel.textSuggestion.collectAsState()
    val shapeSuggestion by viewModel.shapeSuggestion.collectAsState()
    val lineSuggestion by viewModel.lineSuggestion.collectAsState()
    val suggesting = textSuggestion != null || shapeSuggestion != null || lineSuggestion != null

    // v39 Ana sayfadaki görev kartından açıldıysa istenen seviye kurulur (tuval yüklenince, bir kez)
    val loadedCanvas by viewModel.canvas.collectAsState()
    val loadedId = loadedCanvas?.localId
    LaunchedEffect(loadedId) {
        val id = loadedId ?: return@LaunchedEffect
        ChallengeLaunch.take(id)?.let { level -> ctl.open(level) }
        // v40 çocuk tuvaline giriş sayılır; ilk 5 girişte (görev açık değilse) maskot görevi önerir
        ctl.onKidsEntry()
    }
    // v40 Premium seviyeler: "Premium gerekli" penceresi (haklar değişince ızgara yeniden çizilir)
    val premium = com.karapp.drawguidance.ui.premium.LocalPremium.current
    val askPremium: () -> Unit = { premium.request(com.karapp.drawguidance.data.billing.PremiumFeature.KIDS_CHALLENGE_ADVANCED) }
    val suggest by ctl.suggest.collectAsState()

    val current = run?.takeIf { active }
    if (current != null) {
        ChallengeGuide(current, live, viewport)
        // Yeni seviyede kâğıt ekrana sığdırılır (hedefin tamamı görünsün)
        LaunchedEffect(current.seq) { viewport.fit() }
    }

    Box(Modifier.fillMaxSize().testTag("challenge_layer")) {
        if (current != null) {
            ChallengeCard(current, // Çiz modundaki fırça/yakınlaştırma satırının altında
                Modifier.align(Alignment.TopCenter).padding(top = 52.dp, start = 12.dp, end = 12.dp), ctl::finish)
            if (!suggesting) {
                val bottom = with(LocalDensity.current) {
                    val bar = mode !in NO_BAR && !(mode == EditMode.SELECT && selected.isEmpty())
                    (if (bar) barPx.toDp() else 16.dp) + 4.dp
                }
                ChallengeMascot(
                    viewModel, current, live,
                    Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 10.dp, bottom = bottom),
                )
            }
        }
        if (current == null && suggest && !suggesting) {
            val bottom = with(LocalDensity.current) {
                val bar = mode !in NO_BAR && !(mode == EditMode.SELECT && selected.isEmpty())
                (if (bar) barPx.toDp() else 16.dp) + 4.dp
            }
            // v47 en iyi puanlar değişince (seviye geçildi, kayıt geldi) öneri yeniden hesaplanır
            @Suppress("UNUSED_VARIABLE") val bestScores = ctl.best.collectAsState().value
            val next = ctl.nextIndex()
            ChallengeSuggestBubble(
                next, ctl.premiumLocked(next), ctl.passed(0),
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 10.dp, bottom = bottom),
                onStart = { if (ctl.acceptSuggestion() == KidsChallengeController.SuggestOutcome.NEEDS_PREMIUM) askPremium() },
                onCancel = ctl::cancelSuggestion,
            )
        }
        if (ui.menu) ChallengeMenu(current, onDismiss = { ui.menu = false }, onStart = {
            ui.menu = false
            // v40 sıradaki seviye Premium'sa "Premium gerekli"
            if (!ctl.start() && ctl.premiumLocked(ctl.nextIndex())) askPremium()
        }, onStop = {
            ui.menu = false
            ctl.stop()
        }, onProgress = {
            ui.menu = false
            ui.progress = true
        })
    }

    result?.let { r ->
        ChallengeResultDialog(
            r, onNext = {
                // v40 sonraki seviye Premium'sa sonuç kapanır ve "Premium gerekli" açılır
                if (ctl.nextNeedsPremium()) {
                    ctl.dismissResult()
                    askPremium()
                } else ctl.next()
            }, onRetry = { ctl.retry() }, onExit = ctl::stop,
            onProgress = {
                ctl.dismissResult()
                ui.progress = true
            },
        )
    }
    if (ui.progress) {
        ChallengeProgressSheet(
            best = best, current = current?.index, reachable = ctl::reachable,
            onOpen = { i ->
                ui.progress = false
                ctl.open(i)
            },
            onDismiss = { ui.progress = false },
            premiumLocked = ctl::premiumLocked,
            onPremium = {
                ui.progress = false
                askPremium()
            },
        )
    }
}

/**
 * Kâğıttaki hedef (tuval koordinatında, görünümle birlikte): izlenecek kesikli çizgiler (beyaz kenarlı mor) ve boyanacak
 * yerlerde beklenen rengin noktası (doğru boyanınca yeşil tik).
 */
@Composable
private fun ChallengeGuide(run: ChallengeRun, live: ChallengeEval?, viewport: ViewportState) {
    val target = run.target
    val paths = remember(target) {
        target.lines.map { line ->
            Path().apply { line.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        }
    }
    val marks: List<PaintMark> = live?.marks?.takeIf { it.isNotEmpty() }
        ?: remember(target) { target.spots().map { (at, color) -> PaintMark(at, color, false) } }
    val showMarks = target.level.kind != ChallengeKind.TRACE
    Canvas(
        Modifier.fillMaxSize()
            .semantics { stateDescription = "${target.lines.size}/${if (showMarks) marks.size else 0}" }
            .testTag("challenge_guide"),
    ) {
        val sc = viewport.scale
        translate(viewport.offset.x, viewport.offset.y) {
            scale(sc, sc, Offset.Zero) {
                val halo = Stroke(7.dp.toPx() / sc, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val dash = Stroke(
                    3.dp.toPx() / sc, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx() / sc, 7.dp.toPx() / sc)),
                )
                paths.forEach { drawPath(it, Color.White.copy(alpha = 0.7f), style = halo) }
                paths.forEach { drawPath(it, KidPurple.copy(alpha = 0.8f), style = dash) }
                if (showMarks) marks.forEach { m ->
                    val r = 7.dp.toPx() / sc
                    val fill = hex(m.color)
                    drawCircle(Color.Black.copy(alpha = 0.18f), r * 1.35f, m.at + Offset(0f, r * 0.12f))
                    drawCircle(Color.White, r * 1.3f, m.at)
                    drawCircle(fill, r, m.at)
                    if (m.done) {
                        val c = Offset(m.at.x + r * 0.95f, m.at.y - r * 0.95f)
                        drawCircle(Color.White, r * 0.62f, c)
                        drawCircle(Color(0xFF16A34A), r * 0.5f, c)
                        val tick = Path().apply {
                            moveTo(c.x - r * 0.24f, c.y + r * 0.02f)
                            lineTo(c.x - r * 0.06f, c.y + r * 0.2f)
                            lineTo(c.x + r * 0.26f, c.y - r * 0.18f)
                        }
                        drawPath(tick, Color.White, style = Stroke(r * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

private fun hex(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.Gray)

/** Üstteki seviye kartı: örnek resim, "Seviye n / 15", şeklin adı, görevin türü ve yeşil Bitti düğmesi. */
@Composable
private fun ChallengeCard(run: ChallengeRun, modifier: Modifier, onFinish: () -> Unit) {
    val level = run.level
    val ink = kidInk()
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .widthIn(max = 460.dp)
            .clip(shape)
            .background(kidCardBrush())
            .border(2.dp, KidBorder, shape)
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
            .semantics { stateDescription = level.id }
            .testTag("challenge_card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.5.dp, KidPurple.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        ) {
            ChallengeThumb(level, Modifier.fillMaxSize().padding(3.dp).testTag("challenge_card_thumb"))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                stringResource(R.string.challenge_level_of, level.number, ChallengeLevels.count),
                style = MaterialTheme.typography.labelMedium, color = KidPurple, fontWeight = FontWeight.Bold, maxLines = 1,
                translate = false, modifier = Modifier.testTag("challenge_card_level"),
            )
            Text(
                I18n.tr(level.title) + " · " + stringResource(level.kind.label),
                style = MaterialTheme.typography.titleSmall, color = ink, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false,
            )
        }
        KidPill(stringResource(R.string.challenge_finish), Icons.Filled.Check, KidPillKind.PRIMARY, "challenge_finish", onFinish)
    }
}

/**
 * Köşedeki maskot: seviye başında görevi anlatır, ipucu düğmesiyle (ya da kendisine dokununca) sırayla seviyenin
 * araçlarının ipuçlarını verir; çizim hedefe yaklaşınca "Bitti'ye dokun" der. Konuşmuyorken yüzü o anki puana göre
 * değişir. Balon ilk çizgide kendiliğinden kapanır (çizimin önünü kapatmasın).
 */
@Composable
private fun ChallengeMascot(viewModel: CanvasEditViewModel, run: ChallengeRun, live: ChallengeEval?, modifier: Modifier) {
    val level = run.level
    val locale = androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current)[0] ?: java.util.Locale.getDefault()
    val title = I18n.tr(level.title)
    var said by remember(run.seq) {
        mutableStateOf<Said?>(Said(level.kind.intro, listOf(level.number, title), MascotLook(MascotFace.EXPLAINING, MascotGesture.WAVE), "intro"))
    }
    var tips by remember(run.seq) { mutableIntStateOf(0) }
    var cheered by remember(run.seq) { mutableStateOf(false) }

    // İlk çizgide anlatım balonu kapanır
    LaunchedEffect(run.seq) {
        snapshotFlow { viewModel.canvas.value?.strokes }.drop(1).first()
        if (said?.key == "intro") said = null
    }
    // Hedefe yaklaşınca bir kez Bitti'yi hatırlatır
    val close = (live?.score ?: 0) >= ChallengeScore.PASS && live?.drawn == true
    LaunchedEffect(close, run.seq) {
        if (close && !cheered) {
            cheered = true
            said = Said(R.string.challenge_live_close, emptyList(), MascotLook(MascotFace.EXCITED, MascotGesture.CHEER), "close")
        }
    }

    // v45 kovanın ardından boya fırçasının ipucu
    val tipTools = remember(level) { ChallengeTool.tips(level) }
    fun nextTip() {
        val all = tipTools.size + 1
        val i = tips++ % all
        said = if (i < tipTools.size) {
            val tool = tipTools[i]
            Said(tool.tip, if (tool == ChallengeTool.SHAPE) listOf(title.lowercase(locale)) else emptyList(), tool.look, "tip_${tool.name.lowercase()}")
        } else Said(R.string.challenge_tip_finish, emptyList(), MascotLook(MascotFace.PROUD, MascotGesture.POINT), "tip_finish")
    }

    val line = said
    val text = line?.let { stringResource(it.text, *it.args.toTypedArray()) }
    MascotSpeaker(
        look = line?.look ?: liveLook(live, level.kind),
        text = text,
        title = stringResource(R.string.kids_mascot_name),
        figure = 84.dp,
        onMascot = ::nextTip,
        mascotDescription = stringResource(R.string.challenge_mascot_cd),
        modifier = modifier.semantics { stateDescription = line?.key ?: "idle" }.testTag("challenge_mascot"),
    ) {
        MascotRoundButton(Icons.Filled.Close, stringResource(R.string.kids_mascot_close), "challenge_mascot_close", MascotButtonKind.CLOSE, { said = null })
        MascotRoundButton(Icons.Filled.Lightbulb, stringResource(R.string.challenge_tip_next), "challenge_mascot_tip", MascotButtonKind.NEUTRAL, ::nextTip)
    }
}

/**
 * Düğmenin küçük menüsü (çizim alanının üstünde, karartılmış zemin dokununca kapanır): mod kapalıyken Başlat ve
 * İlerleme, açıkken Modu kapat ve İlerleme.
 */
@Composable
private fun ChallengeMenu(run: ChallengeRun?, onDismiss: () -> Unit, onStart: () -> Unit, onStop: () -> Unit, onProgress: () -> Unit) {
    val ink = kidInk()
    Box(Modifier.fillMaxSize()) {
        // Zemin kartın kardeşi (üst öğesi değil): dokunma işlevi kartın düğmelerini kendi içine katmasın
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .testTag("challenge_menu_scrim"),
        )
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(kidCardBrush())
                .border(2.5.dp, KidBorder, shape)
                // Kartın içine dokunmak menüyü kapatmaz
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp)
                .semantics { stateDescription = if (run != null) "running" else "idle" }
                .testTag("challenge_menu"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MascotFigure(
                    if (run != null) MascotLook(MascotFace.WINKING, MascotGesture.THUMBS_UP) else MascotLook(MascotFace.EXCITED, MascotGesture.WAVE),
                    Modifier.size(58.dp), faceOnly = true,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.kids_challenge), style = MaterialTheme.typography.titleLarge, color = ink, fontWeight = FontWeight.ExtraBold)
                    val note = if (run != null) {
                        stringResource(R.string.challenge_menu_running, run.level.number, I18n.tr(run.level.title))
                    } else stringResource(R.string.challenge_menu_idle)
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f), translate = false)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (run == null) {
                    KidPill(stringResource(R.string.challenge_start), Icons.Filled.PlayArrow, KidPillKind.PRIMARY, "challenge_start", onStart, Modifier.weight(1f))
                } else {
                    KidPill(stringResource(R.string.challenge_stop), Icons.Filled.Stop, KidPillKind.DANGER, "challenge_stop", onStop, Modifier.weight(1f))
                }
                KidPill(stringResource(R.string.challenge_progress), Icons.Filled.EmojiEvents, KidPillKind.NEUTRAL, "challenge_progress", onProgress, Modifier.weight(1f))
            }
        }
    }
}

/** v39 Ana sayfadaki görev kartından açılacak seviye: tuval kimliği → seviye sırası (bir kez kullanılır). */
object ChallengeLaunch {
    @Volatile private var pending: Pair<String, Int>? = null

    fun request(canvasLocalId: String, level: Int) { pending = canvasLocalId to level }

    fun take(canvasLocalId: String): Int? {
        val p = pending ?: return null
        if (p.first != canvasLocalId) return null
        pending = null
        return p.second
    }
}

/**
 * v40 maskotun çizim görevi önerisi (çocuk tuvaline ilk 5 girişte, görev açık değilken): Boyacık görevi anlatır;
 * Başlat geçilmemiş ilk seviyeyi açar ([next]; Premium kilitliyse [premium] true: "Premium gerekli"), İptal balonu bu
 * giriş için kapatır. [resumed]: daha önce seviye geçildi (kaldığı yerden devam önerisi).
 */
@Composable
internal fun ChallengeSuggestBubble(next: Int, premium: Boolean, resumed: Boolean, modifier: Modifier, onStart: () -> Unit, onCancel: () -> Unit) {
    val text = when {
        premium -> stringResource(R.string.v40_challenge_suggest_premium)
        resumed -> stringResource(R.string.v40_challenge_suggest_resume, next + 1)
        else -> stringResource(R.string.v40_challenge_suggest_new)
    }
    MascotSpeaker(
        look = MascotLook(MascotFace.EXCITED, MascotGesture.WAVE),
        text = text,
        title = stringResource(R.string.kids_mascot_name),
        figure = 84.dp,
        mascotDescription = stringResource(R.string.v40_challenge_suggest_cd),
        modifier = modifier.semantics { stateDescription = if (premium) "premium" else "level_${next + 1}" }.testTag("challenge_suggest"),
    ) {
        KidPill(stringResource(R.string.v40_challenge_suggest_cancel), Icons.Filled.Close, KidPillKind.PLAIN, "challenge_suggest_cancel", onCancel)
        KidPill(stringResource(R.string.v40_challenge_suggest_start), Icons.Filled.PlayArrow, KidPillKind.PRIMARY, "challenge_suggest_start", onStart)
    }
}
