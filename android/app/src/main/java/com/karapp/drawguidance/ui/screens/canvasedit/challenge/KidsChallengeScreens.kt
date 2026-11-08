package com.karapp.drawguidance.ui.screens.canvasedit.challenge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karapp.drawguidance.R
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.components.MascotFace
import com.karapp.drawguidance.ui.components.MascotFigure
import com.karapp.drawguidance.ui.components.MascotGesture
import com.karapp.drawguidance.ui.components.MascotLook
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.components.rememberKeyedThumb
import com.karapp.drawguidance.ui.components.rememberTyping
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/*
 * v38 çizim görevinin pencereleri: seviye sonucu (puan, yıldız ve düğmeler) ve İlerleme (bütün seviyeler; geçilenler
 * yıldızlarıyla, kilitliler kilit simgesiyle). Çocuk modunun görünüşünde: sıcak gradyanlı kartlar, turuncu-mor kenar,
 * büyük yuvarlak düğmeler ve maskot Boyacık.
 */

/** Çocuk kartlarının kenar gradyanı (maskotun balonuyla aynı). */
internal val KidBorder = Brush.linearGradient(listOf(Color(0xFFFFB938), Color(0xFFA78BFA)))

/** Çocuk kartlarının zemini (açık / koyu tema). */
@Composable
internal fun kidCardBrush(): Brush = if (NeuTheme.colors.isDark) {
    Brush.linearGradient(listOf(Color(0xFF3B2E5A), Color(0xFF2B2F45)))
} else Brush.linearGradient(listOf(Color(0xFFFFF7DD), Color(0xFFFFE6F1), Color(0xFFEDE7FF)))

@Composable
internal fun kidInk(): Color = if (NeuTheme.colors.isDark) Color(0xFFF5F3FF) else Color(0xFF2B2140)

internal val KidPurple = Color(0xFF8B5CF6)

/** Düğme türleri: yeşil (ilerle), sarı (ikincil), kırmızı (kapat), sade (çerçeveli). */
internal enum class KidPillKind { PRIMARY, NEUTRAL, DANGER, PLAIN }

/** Çocuk parmağına uygun hap düğme (simge ve yazı). */
@Composable
internal fun KidPill(text: String, icon: ImageVector?, kind: KidPillKind, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg: Brush? = when (kind) {
        KidPillKind.PRIMARY -> Brush.verticalGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A)))
        KidPillKind.NEUTRAL -> Brush.verticalGradient(listOf(Color(0xFFFFD166), Color(0xFFF59E0B)))
        KidPillKind.DANGER -> Brush.verticalGradient(listOf(Color(0xFFFF8A8A), Color(0xFFE5484D)))
        KidPillKind.PLAIN -> null
    }
    val ink = if (bg != null) Color.White else kidInk()
    Row(
        modifier
            .clip(CircleShape)
            .then(if (bg != null) Modifier.background(bg) else Modifier.background(Color.White.copy(alpha = if (NeuTheme.colors.isDark) 0.08f else 0.75f)))
            .border(2.dp, if (bg != null) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.85f))) else KidBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Üç yıldız ([stars] tanesi dolu). */
@Composable
internal fun StarRow(stars: Int, size: Dp, modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.challenge_stars_cd, stars)
    Row(modifier.semantics { contentDescription = cd; stateDescription = stars.toString() }, horizontalArrangement = Arrangement.spacedBy(size * 0.08f)) {
        repeat(3) { i ->
            val on = i < stars
            Icon(
                if (on) Icons.Filled.Star else Icons.Filled.StarBorder, null,
                tint = if (on) Color(0xFFFFB300) else Color(0xFFB8AFCF),
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * Seviyenin küçük resmi: çizme görevinde izlenecek çizgiler (mor), boyama görevlerinde örnekteki renklerle boyanmış
 * şekil.
 */
@Composable
internal fun ChallengeThumb(level: ChallengeLevel, modifier: Modifier = Modifier) {
    if (level.kind == ChallengeKind.TRACE) {
        val lines = remember(level.id) { ChallengeLevels.target(level, 1000, 1000).lines }
        Canvas(modifier) {
            val box = ChallengeScore.bounds(lines) ?: return@Canvas
            val pad = 4.dp.toPx()
            val s = minOf((size.width - 2 * pad) / box.width, (size.height - 2 * pad) / box.height)
            val ox = (size.width - box.width * s) / 2f - box.left * s
            val oy = (size.height - box.height * s) / 2f - box.top * s
            val path = Path()
            lines.forEach { line -> line.forEachIndexed { i, p -> if (i == 0) path.moveTo(ox + p.x * s, oy + p.y * s) else path.lineTo(ox + p.x * s, oy + p.y * s) } }
            drawPath(path, KidPurple, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        return
    }
    var px by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { px = it }) {
        if (px.width < 8 || px.height < 8) return@Box
        val key = Triple(level.id, px.width, px.height)
        val bitmap = rememberKeyedThumb(key, { ChallengeThumbs.cache[it] }) { ChallengeThumbs.render(level, it.second, it.third) }
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/** Boyalı örnek resimlerin önbelleği. */
internal object ChallengeThumbs {
    val cache = java.util.concurrent.ConcurrentHashMap<Triple<String, Int, Int>, ImageBitmap>()

    fun render(level: ChallengeLevel, w: Int, h: Int): ImageBitmap? {
        val key = Triple(level.id, w, h)
        cache[key]?.let { return it }
        val pad = 3f
        val aspect = com.karapp.drawguidance.ui.drawing.ShapeRegistry.aspect(level.shape)
        val aw = w - 2 * pad
        val ah = h - 2 * pad
        val bw: Float
        val bh: Float
        if (aw / ah > aspect) { bh = ah; bw = ah * aspect } else { bw = aw; bh = aw / aspect }
        val box = androidx.compose.ui.geometry.Rect(Offset((w - bw) / 2f, (h - bh) / 2f), androidx.compose.ui.geometry.Size(bw, bh))
        val style = ChallengeLevels.inkStyle.copy(strokeWidth = 1.4f)
        val stroke = ChallengeLevels.build(level, box, "challenge-thumb", style = style, fills = ChallengeLevels.fills(level)) ?: return null
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        com.karapp.drawguidance.ui.drawing.ShapeRenderer.draw(android.graphics.Canvas(bitmap), stroke, cache = false)
        return bitmap.asImageBitmap().also { cache[key] = it }
    }
}

/**
 * Seviye sonucu: başarıda maskot gururlu, puan, yıldızlar, Sonraki seviye ve Moddan çık (son seviyede İlerleme ve
 * Moddan çık); geçilemediyse maskot cesaretlendirir: puan, yıldızlar, Tekrar dene ve Moddan çık. Pencere bir düğmeyle
 * kapanır.
 */
@Composable
internal fun ChallengeResultDialog(
    result: ChallengeResult,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    onProgress: () -> Unit,
) {
    val level = ChallengeLevels.all[result.index]
    val ink = kidInk()
    val look = when {
        !result.passed -> MascotLook(MascotFace.HAPPY, MascotGesture.THUMBS_UP)
        result.stars >= 3 -> MascotLook(MascotFace.EXCITED, MascotGesture.CHEER)
        else -> MascotLook(MascotFace.PROUD, MascotGesture.CHEER)
    }
    val say = when {
        !result.passed -> stringResource(R.string.challenge_failed_say, ChallengeScore.PASS)
        result.last -> stringResource(R.string.challenge_all_done)
        result.stars >= 3 -> stringResource(R.string.challenge_passed_say_3)
        else -> stringResource(R.string.challenge_passed_say)
    }
    val typing = rememberTyping(say)
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(kidCardBrush())
                .border(2.5.dp, KidBorder, shape)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .semantics { stateDescription = if (result.passed) "passed" else "failed" }
                .testTag("challenge_result"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.challenge_level_of, level.number, ChallengeLevels.count) + " · " + I18n.tr(level.title),
                style = MaterialTheme.typography.labelLarge, color = KidPurple, fontWeight = FontWeight.Bold, translate = false,
            )
            Text(
                stringResource(if (result.passed) R.string.challenge_passed_title else R.string.challenge_failed_title),
                style = MaterialTheme.typography.headlineSmall, color = ink, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                modifier = Modifier.testTag("challenge_result_title"),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MascotFigure(look, Modifier.size(112.dp).testTag("challenge_result_mascot"), mouthOpen = typing.mouthOpen)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.challenge_score), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
                    Text(
                        result.score.toString(), fontSize = 52.sp, fontWeight = FontWeight.Black, translate = false,
                        color = if (result.passed) Color(0xFF16A34A) else Color(0xFFF59E0B),
                        modifier = Modifier.semantics { contentDescription = result.score.toString() }.testTag("challenge_result_score"),
                    )
                    StarRow(result.stars, 30.dp, Modifier.testTag("challenge_result_stars"))
                    Text(stringResource(R.string.challenge_best, result.best), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.65f))
                }
            }
            // Maskotun sözü (harf harf yazılır; dokununca tamamlanır)
            val shown = typing.typed.coerceAtMost(say.length)
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(say.substring(0, shown))
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent))
                    append(say.substring(shown))
                    pop()
                },
                style = MaterialTheme.typography.titleMedium, color = ink, textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (NeuTheme.colors.isDark) Color(0xFF2B2F45) else Color.White)
                    .clickable(role = Role.Button, onClick = typing::skip)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { contentDescription = say }
                    .testTag("challenge_result_say"),
            )
            Spacer(Modifier.height(2.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    result.passed && !result.last -> KidPill(stringResource(R.string.challenge_next), Icons.Filled.PlayArrow, KidPillKind.PRIMARY, "challenge_next", onNext, Modifier.fillMaxWidth())
                    result.passed -> KidPill(stringResource(R.string.challenge_progress), Icons.Filled.EmojiEvents, KidPillKind.PRIMARY, "challenge_result_progress", onProgress, Modifier.fillMaxWidth())
                    else -> KidPill(stringResource(R.string.challenge_retry), Icons.Filled.Refresh, KidPillKind.PRIMARY, "challenge_retry", onRetry, Modifier.fillMaxWidth())
                }
                KidPill(stringResource(R.string.challenge_exit), Icons.AutoMirrored.Filled.ExitToApp, KidPillKind.PLAIN, "challenge_exit", onExit, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Seviyenin İlerleme'deki durumu (v40 [PREMIUM]: Premium hakkı olmadan açılamaz; taçla gösterilir). */
internal enum class LevelState { PASSED, OPEN, LOCKED, PREMIUM }

/**
 * İlerleme: bütün seviyeler (üçerli ızgara). Geçilenler yeşil çerçeve, yıldızlar ve en iyi puanla; açık olanlar Oyna
 * ile; kilitliler soluk ve kilit simgesiyle; v40 Premium hakkı olmadan açılamayanlar taçla (dokununca [onPremium]). Açık bir seviyeye dokununca o seviye kurulur (mod kapalıysa açılır);
 * kilitliye dokununca maskot önce hangi seviyenin geçilmesi gerektiğini söyler.
 */
@Composable
internal fun ChallengeProgressSheet(
    best: Map<String, Int>,
    current: Int?,
    reachable: (Int) -> Boolean,
    onOpen: (Int) -> Unit,
    onDismiss: () -> Unit,
    premiumLocked: (Int) -> Boolean = { false },
    onPremium: () -> Unit = {},
) {
    val levels = ChallengeLevels.all
    val ink = kidInk()
    var lockedNote by remember { mutableStateOf<Int?>(null) }
    NeuSheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                .testTag("challenge_progress_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val passed = levels.count { (best[it.id] ?: -1) >= ChallengeScore.PASS }
            val stars = levels.sumOf { ChallengeScore.stars(best[it.id] ?: 0) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                val note = lockedNote
                MascotFigure(
                    if (note != null) MascotLook(MascotFace.THINKING, MascotGesture.THINK) else MascotLook(MascotFace.PROUD, MascotGesture.THUMBS_UP),
                    Modifier.size(64.dp), faceOnly = true,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.challenge_progress), style = MaterialTheme.typography.titleLarge, color = ink, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (note != null) stringResource(R.string.challenge_locked_say, note) else stringResource(R.string.challenge_progress_summary, passed, levels.size, stars),
                        style = MaterialTheme.typography.bodyMedium, color = if (note != null) Color(0xFFE5484D) else ink.copy(alpha = 0.75f),
                        modifier = Modifier.testTag("challenge_progress_note"),
                    )
                    // v40 Free planda Premium seviyelerin notu
                    val firstPremium = levels.indices.firstOrNull(premiumLocked)
                    if (firstPremium != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.WorkspacePremium, null, tint = com.karapp.drawguidance.ui.premium.Gold, modifier = Modifier.size(16.dp))
                            Text(
                                stringResource(R.string.v40_challenge_premium_note, firstPremium + 1, levels.size),
                                style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.75f),
                                modifier = Modifier.testTag("challenge_premium_note"),
                            )
                        }
                    }
                }
            }
            levels.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { level ->
                        val i = level.number - 1
                        val score = best[level.id]
                        val state = when {
                            premiumLocked(i) -> LevelState.PREMIUM
                            (score ?: -1) >= ChallengeScore.PASS -> LevelState.PASSED
                            reachable(i) -> LevelState.OPEN
                            else -> LevelState.LOCKED
                        }
                        LevelTile(level, state, score, current == i, Modifier.weight(1f)) {
                            // Kilitliye dokununca maskot geçilmesi gereken ilk seviyeyi söyler
                            when (state) {
                                LevelState.LOCKED -> lockedNote = (levels.indexOfFirst { (best[it.id] ?: -1) < ChallengeScore.PASS }.takeIf { it >= 0 } ?: i) + 1
                                // v40 Premium seviye: "Premium gerekli" penceresi
                                LevelState.PREMIUM -> onPremium()
                                else -> onOpen(i)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun LevelTile(level: ChallengeLevel, state: LevelState, score: Int?, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ink = kidInk()
    val shape = RoundedCornerShape(20.dp)
    val border = when {
        current -> Brush.linearGradient(listOf(KidPurple, KidPurple))
        state == LevelState.PASSED -> Brush.linearGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A)))
        state == LevelState.OPEN -> KidBorder
        state == LevelState.PREMIUM -> Brush.linearGradient(listOf(com.karapp.drawguidance.ui.premium.Gold, Color(0xFFFFD98A)))
        else -> Brush.linearGradient(listOf(Color(0xFFC9C3D9), Color(0xFFC9C3D9)))
    }
    val stateText = stringResource(
        when (state) {
            LevelState.PASSED -> R.string.challenge_state_passed
            LevelState.OPEN -> R.string.challenge_state_open
            LevelState.LOCKED -> R.string.challenge_state_locked
            LevelState.PREMIUM -> R.string.v40_challenge_state_premium
        },
    )
    val dim = state == LevelState.LOCKED || state == LevelState.PREMIUM
    val title = I18n.tr(level.title)
    Column(
        modifier
            .clip(shape)
            .background(if (dim) NeuTheme.colors.background else Color.White.copy(alpha = if (NeuTheme.colors.isDark) 0.06f else 1f))
            .then(if (!dim) Modifier.background(kidCardBrush(), alpha = 0.55f) else Modifier)
            .border(if (current) 3.dp else 2.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${level.number}. $title"; stateDescription = stateText }
            .padding(8.dp)
            .testTag("challenge_level_${level.number}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(62.dp)) {
            ChallengeThumb(level, Modifier.fillMaxSize().alpha(if (dim) 0.35f else 1f))
            Box(
                Modifier.align(Alignment.TopStart).size(24.dp).clip(CircleShape)
                    .background(if (dim) Color(0xFFB8AFCF) else KidPurple),
                contentAlignment = Alignment.Center,
            ) {
                Text(level.number.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, translate = false)
            }
            when (state) {
                LevelState.PASSED -> Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.align(Alignment.TopEnd).size(22.dp))
                LevelState.LOCKED -> Icon(Icons.Filled.Lock, null, tint = Color(0xFF8E86A6), modifier = Modifier.align(Alignment.Center).size(28.dp))
                LevelState.PREMIUM -> com.karapp.drawguidance.ui.premium.PremiumLockBadge(Modifier.align(Alignment.Center), size = 22.dp)
                LevelState.OPEN -> Unit
            }
        }
        Text(
            title, style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = if (dim) 0.5f else 1f),
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false,
        )
        when (state) {
            LevelState.PASSED -> StarRow(ChallengeScore.stars(score ?: 0), 16.dp)
            LevelState.OPEN -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.challenge_play), style = MaterialTheme.typography.labelMedium, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
            }
            LevelState.LOCKED -> Text(stateText, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.45f))
            LevelState.PREMIUM -> Text(stateText, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB7791F), fontWeight = FontWeight.Bold)
        }
    }
}
