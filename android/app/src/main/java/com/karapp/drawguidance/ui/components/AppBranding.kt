package com.karapp.drawguidance.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.delay
import kotlin.math.max
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** v30 uygulama simgesinin renkleri (tools/icon/make_icon.py ile aynı). */
object BrandColors {
    val Indigo = Color(0xFF4F46E5)
    val Violet = Color(0xFF9333EA)
    val Amber = Color(0xFFFDE68A)
    val Rose = Color(0xFFFB7185)
}

// Simge geometrisi (108 birimlik tuval; tools/icon/make_icon.py ile aynı)
private val P0 = Offset(26f, 79f); private val C1 = Offset(22f, 49f); private val C2 = Offset(49f, 33f); private val P1 = Offset(57f, 53f)
private val C3 = Offset(64f, 69f); private val C4 = Offset(85f, 62f); private val P2 = Offset(82f, 35f)
private val PENCIL_DIR = Offset(0.6428f, -0.766f)

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 8) {
        val a = -Math.PI / 2 + i * Math.PI / 4
        val rr = if (i % 2 == 0) r else r * 0.28f
        val x = c.x + rr * kotlin.math.cos(a).toFloat(); val y = c.y + rr * kotlin.math.sin(a).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

/**
 * v31 logonun renk düzeni: [BRAND] mor gradyanlı zemin (simge), [ON_DARK] zeminsiz, koyu/renkli yüzeyde (beyaz kılavuz),
 * [INK] zeminsiz (saydam), nöromorfik ve editoryal açık/koyu zeminde: mürekkep renginde kılavuz ve kalem, tek vurgu
 * rengiyle çizgi; çizgi ve kalem nöromorfik iki gölgeyle (açık sol-üst, koyu sağ-alt) zemine kabartılır.
 */
enum class LogoStyle { BRAND, ON_DARK, INK }

/** Logonun çizim renkleri (INK düzeninde temadan). */
private class LogoPalette(
    val guide: Color,
    val traceFrom: Color,
    val traceTo: Color,
    val pencilBody: Color,
    val pencilStripe: Color,
    val ferrule: Color,
    val outline: Color?,
    val sparkle: Color,
    val sparkleSoft: Color,
    val lightShadow: Color?,
    val darkShadow: Color?,
)

private fun DrawScope.pencilAt(tip: Offset, pal: LogoPalette, shift: Offset = Offset.Zero, flat: Color? = null) {
    val u = PENCIL_DIR; val n = Offset(-u.y, u.x)
    fun at(s: Float, w: Float) = tip + shift + u * s + n * w
    fun poly(p: List<Offset>) = Path().apply { moveTo(p[0].x, p[0].y); for (i in 1 until p.size) lineTo(p[i].x, p[i].y); close() }
    val w = 5.2f
    val body = poly(listOf(at(9f, w), at(30f, w), at(30f, -w), at(9f, -w)))
    val wood = poly(listOf(at(0f, 0f), at(9f, w), at(9f, -w)))
    val ferrule = poly(listOf(at(30f, w), at(33f, w), at(33f, -w), at(30f, -w)))
    val eraser = poly(listOf(at(33f, w), at(38f, w), at(38f, -w), at(33f, -w)))
    if (flat != null) {
        // Gölge: kalemin tüm dış hattı tek renkte
        listOf(body, wood, ferrule, eraser).forEach { drawPath(it, flat) }
        return
    }
    drawPath(body, pal.pencilBody)
    drawPath(poly(listOf(at(9f, w * 0.33f), at(30f, w * 0.33f), at(30f, -w * 0.33f), at(9f, -w * 0.33f))), pal.pencilStripe)
    drawPath(wood, Color(0xFFFCD9B8))
    drawPath(poly(listOf(at(0f, 0f), at(3.2f, w * 0.36f), at(3.2f, -w * 0.36f))), Color(0xFF1F2937))
    drawPath(ferrule, pal.ferrule)
    drawPath(eraser, BrandColors.Rose)
    pal.outline?.let { c -> listOf(body, wood, ferrule, eraser).forEach { drawPath(it, c, style = Stroke(0.9f)) } }
}

/**
 * v30 uygulamanın logosu (simgeyle aynı çizim): kesik çizgili kılavuz, kılavuzu izleyen renkli çizgi ve kalem; [style]
 * zemini ve renkleri belirler (v31: [LogoStyle.INK] saydam, nöromorfik/editoryal zemine uygun). [progress] (0..1)
 * çizginin ne kadarının çizildiğini belirler (karşılama ekranında canlandırılır). [rounded] eski çağrılar için: false
 * ise [LogoStyle.ON_DARK].
 */
@Composable
fun AppLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    rounded: Boolean = true,
    style: LogoStyle = if (rounded) LogoStyle.BRAND else LogoStyle.ON_DARK,
) {
    val label = stringResource(R.string.app_name)
    val neu = NeuTheme.colors
    val pal = when (style) {
        LogoStyle.INK -> LogoPalette(
            guide = neu.contentMuted.copy(alpha = 0.7f),
            traceFrom = if (neu.isDark) Color(0xFF8B87FF) else BrandColors.Indigo,
            traceTo = if (neu.isDark) Color(0xFFC084FC) else BrandColors.Violet,
            pencilBody = neu.background,
            pencilStripe = neu.accent.copy(alpha = 0.22f).compositeOverSafe(neu.background),
            ferrule = neu.contentMuted.copy(alpha = 0.55f).compositeOverSafe(neu.background),
            outline = neu.content.copy(alpha = 0.85f),
            sparkle = if (neu.isDark) Color(0xFFF0784A) else Color(0xFFD9572B),
            sparkleSoft = (if (neu.isDark) Color(0xFFF0784A) else Color(0xFFD9572B)).copy(alpha = 0.75f),
            lightShadow = neu.lightShadow.copy(alpha = if (neu.isDark) 0.7f else 0.95f),
            darkShadow = neu.darkShadow.copy(alpha = 0.75f),
        )
        else -> LogoPalette(
            guide = Color.White.copy(alpha = 0.75f), traceFrom = BrandColors.Amber, traceTo = BrandColors.Rose,
            pencilBody = Color.White, pencilStripe = Color(0xFFE0E7FF), ferrule = Color(0xFFC7D2FE), outline = null,
            sparkle = Color.White, sparkleSoft = Color.White.copy(alpha = 0.85f), lightShadow = null, darkShadow = null,
        )
    }
    // v34 sağdan sola yazılan dillerde (Arapça) logo aynalanır: kalem çizgiyi sağdan sola çizer
    val mirror = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Canvas(modifier.size(size).graphicsLayer { if (mirror) scaleX = -1f }.semantics { contentDescription = label }) {
        val k = this.size.minDimension / 108f
        scale(k, pivot = Offset.Zero) {
            if (style == LogoStyle.BRAND) {
                val box = Size(108f, 108f)
                drawRoundRect(Brush.linearGradient(listOf(BrandColors.Indigo, BrandColors.Violet), Offset.Zero, Offset(108f, 108f)), size = box, cornerRadius = CornerRadius(28f))
                drawRoundRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), Offset(30f, 22f), 60f), size = box, cornerRadius = CornerRadius(28f))
            }
            val guide = Path().apply { moveTo(P1.x, P1.y); cubicTo(C3.x, C3.y, C4.x, C4.y, P2.x, P2.y) }
            drawPath(guide, pal.guide, style = Stroke(3.4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.6f, 7.6f), 2f)))
            val full = Path().apply { moveTo(P0.x, P0.y); cubicTo(C1.x, C1.y, C2.x, C2.y, P1.x, P1.y) }
            val p = progress.coerceIn(0f, 1f)
            val measure = PathMeasure().apply { setPath(full, false) }
            val traced = Path()
            val end = measure.length * p
            if (end > 0.5f) measure.getSegment(0f, end, traced, true)
            val tip = if (p >= 1f) P1 else measure.getPosition(max(end, 0.01f))
            // Nöromorfik kabartma: önce koyu (sağ-alt) ve açık (sol-üst) gölgeler
            if (pal.darkShadow != null && pal.lightShadow != null) {
                val d = Offset(1.6f, 1.6f)
                translate(d.x, d.y) { drawPath(traced, pal.darkShadow, style = Stroke(7.5f, cap = StrokeCap.Round)) }
                translate(-1.1f, -1.1f) { drawPath(traced, pal.lightShadow, style = Stroke(7.5f, cap = StrokeCap.Round)) }
                pencilAt(tip, pal, shift = d, flat = pal.darkShadow)
                pencilAt(tip, pal, shift = Offset(-1.1f, -1.1f), flat = pal.lightShadow)
            }
            drawPath(traced, Brush.linearGradient(listOf(pal.traceFrom, pal.traceTo), P0, P1), style = Stroke(7.5f, cap = StrokeCap.Round))
            pencilAt(tip, pal)
            val sp = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
            if (sp > 0f) {
                sparkle(Offset(35f, 37f), 7.5f * sp, pal.sparkle)
                sparkle(Offset(45f, 26f), 3.6f * sp, pal.sparkleSoft)
            }
        }
    }
}

/** Yarı saydam rengi zemine katıp opak renk (kalem gövdesi gölgeyi göstermesin). */
private fun Color.compositeOverSafe(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

/** v40 açılışta hız ölçümü için beklenen kare sayısı ve en uzun bekleme (ms). */
private const val FRAME_PROBE_COUNT = 4
private const val FRAME_PROBE_MS = 250L

/** v36 açılış ekranında logonun altındaki imza (her dilde aynı). */
const val SIGNATURE = "created by karApp"

/**
 * v31 açılışta karşılama ekranı: nöromorfik zeminde saydam logo kendini çizer (kalem kılavuzu izler), altında yalnız
 * uygulamanın adı (v36 Türkçede "Çizim Rehberi") harf harf yazılır, altında "created by karApp" imzası belirir (editoryal serif yazı, vurgu renginde imleç), sonra ekran yumuşakça kaybolur.
 * Dokununca hemen geçilir. Toplam ~2 sn; v40 süreler cihazın hızına göre ([SplashTiming]).
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val colors = NeuTheme.colors
    val title = stringResource(R.string.welcome_title)
    val draw = remember { Animatable(0f) }
    var typed by remember { mutableIntStateOf(0) }
    var caret by remember { mutableStateOf(true) }
    var leaving by remember { mutableStateOf(false) }
    val out = remember { Animatable(1f) }
    // v40 süreler cihazın hızına göre (bkz. SplashTiming); hız anlaşılamazsa normal süreler
    var timing by remember { mutableStateOf(SplashTiming.Normal) }
    LaunchedEffect(Unit) {
        // İlk karelerin süresi (en çok FRAME_PROBE_MS beklenir; cihaz sınıfıyla birlikte değerlendirilir)
        val frames = ArrayList<Long>(FRAME_PROBE_COUNT)
        kotlinx.coroutines.withTimeoutOrNull(FRAME_PROBE_MS) { repeat(FRAME_PROBE_COUNT) { frames += androidx.compose.runtime.withFrameNanos { it } } }
        val tier = runCatching { com.karapp.drawguidance.platform.DeviceCaps.knownTier }.getOrNull()
        val t = runCatching { SplashTiming.select(tier, SplashTiming.averageFrameMs(frames)) }.getOrDefault(SplashTiming.Normal)
        timing = t
        launch { draw.animateTo(1f, tween(t.logoMs, easing = if (t.light) LinearEasing else FastOutSlowInEasing)) }
        delay(t.leadMs)
        var i = 0
        while (i < title.length) {
            val end = (i + t.chunk).coerceAtMost(title.length)
            typed = end
            delay(if (title[end - 1] == ' ') t.spaceMs else t.charMs)
            i = end
        }
        if (t.light) delay(t.holdMs)
        else { delay(t.holdMs * 34 / 100); caret = false; delay(t.holdMs * 31 / 100); caret = true; delay(t.holdMs * 35 / 100) }
        leaving = true
    }
    LaunchedEffect(leaving) {
        if (leaving) { out.animateTo(0f, tween(timing.fadeMs)); onDone() }
    }
    val accent = if (colors.isDark) Color(0xFFF0784A) else Color(0xFFD9572B)
    Box(
        Modifier
            .fillMaxSize()
            .alpha(out.value)
            .background(colors.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { leaving = true }
            .testTag("welcome_screen"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(150.dp, progress = draw.value, style = LogoStyle.INK, modifier = if (timing.light) Modifier else Modifier.scale(0.92f + 0.08f * draw.value))
            Spacer(Modifier.height(22.dp))
            // Yazılmamış harfler saydam: yazı ortada sabit durur, imleç son yazılan harfin ardında
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            val shown = typed.coerceIn(0, title.length)
            val text = remember(shown, title) {
                buildAnnotatedString {
                    append(title)
                    addStyle(SpanStyle(color = Color.Transparent), shown, title.length)
                }
            }
            Text(
                text,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 34.sp, letterSpacing = (-0.4).sp,
                ),
                color = colors.content,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .testTag("welcome_title")
                    .drawWithContent {
                        drawContent()
                        val l = layout ?: return@drawWithContent
                        if (!caret || shown == 0 && draw.value < 0.4f) return@drawWithContent
                        val r = l.getCursorRect(shown)
                        val x = r.left + 1.5.dp.toPx()
                        drawLine(accent, Offset(x, r.top + r.height * 0.14f), Offset(x, r.bottom - r.height * 0.12f), 2.2.dp.toPx(), StrokeCap.Round)
                    },
            )
            // v36 her dilde logonun biraz altında imza (çevrilmez); başlık yazıldıkça belirir
            Spacer(Modifier.height(10.dp))
            Text(
                SIGNATURE, translate = false,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
                color = colors.contentMuted,
                modifier = Modifier.alpha(((typed.toFloat() / title.length.coerceAtLeast(1)) * 1.4f - 0.2f).coerceIn(0f, 1f)).testTag("welcome_signature"),
            )
        }
    }
}

/**
 * v30 şeffaf yükleme perdesi: beklenmesi gereken geçişlerde (tuval açılırken, resim işlenirken, PDF hazırlanırken)
 * içeriğin üstünde yarı saydam bir perde ve küçük bir kart gösterir; dokunmaları engeller. Kısa işlerde titremesin diye
 * [delayMs] sonra görünür.
 */
@Composable
fun LoadingOverlay(visible: Boolean, message: String? = null, delayMs: Long = 160) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) { delay(delayMs); shown = true } else shown = false
    }
    val colors = NeuTheme.colors
    AnimatedVisibility(visible = shown && visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .testTag("loading_overlay"),
            contentAlignment = Alignment.Center,
        ) {
            NeuSurface(elevation = 10.dp) {
                Column(Modifier.padding(horizontal = 26.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val pulse = rememberInfiniteTransition(label = "logo").animateFloat(
                        0.25f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "p",
                    )
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(58.dp), color = colors.accent, strokeWidth = 3.dp)
                        AppLogo(34.dp, progress = pulse.value)
                    }
                    if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.content)
                }
            }
        }
    }
}
