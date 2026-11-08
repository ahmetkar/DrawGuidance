package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.delay

/**
 * v37 maskotun konuşması: balondaki yazı harf harf yazılır ([Typing]); yazılırken maskotun ağzı oynar
 * ([Typing.mouthOpen]). Balona dokununca yazı hemen tamamlanır.
 */
@Stable
class Typing(val text: String) {
    var typed by mutableIntStateOf(0)
    val done: Boolean get() = typed >= text.length

    /** Yazılırken ağız iki harfte bir açılıp kapanır; yazı bitince kapalı. */
    val mouthOpen: Boolean get() = !done && (typed / 2) % 2 == 0

    fun skip() { typed = text.length }
}

/** Metin değişince yazma baştan başlar (her harf [charMs] ms). */
@Composable
fun rememberTyping(text: String, charMs: Long = TYPE_MS): Typing {
    val t = remember(text) { Typing(text) }
    LaunchedEffect(t) {
        while (!t.done) {
            delay(charMs)
            t.typed++
        }
    }
    return t
}

const val TYPE_MS = 32L

/** Konuşma balonu biçimi: yuvarlak köşeli kutu ve altta (maskotun başına doğru) kuyruk; [tailAt] yatay oran. */
class MascotBalloonShape(private val corner: Dp = 22.dp, private val tail: Dp = 14.dp, private val tailAt: Float = 0.82f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }
        val t = with(density) { tail.toPx() }
        val h = size.height - t
        val at = if (layoutDirection == LayoutDirection.Rtl) 1f - tailAt else tailAt
        val x = (size.width * at).coerceIn(r + t, (size.width - r - t).coerceAtLeast(r + t))
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, h, CornerRadius(r, r)))
            moveTo(x - t * 0.9f, h - 1f)
            quadraticTo(x + t * 0.1f, h + t * 0.55f, x + t * 0.35f, h + t)
            quadraticTo(x + t * 0.45f, h + t * 0.35f, x + t * 0.9f, h - 1f)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Balonun düğmesi: büyük, yuvarlak (çocuk parmağına uygun). */
enum class MascotButtonKind { PRIMARY, NEUTRAL, CLOSE }

@Composable
fun MascotRoundButton(icon: ImageVector, description: String, tag: String, kind: MascotButtonKind, onClick: () -> Unit, size: Dp = 46.dp) {
    val colors = NeuTheme.colors
    val bg: Brush = when (kind) {
        MascotButtonKind.PRIMARY -> Brush.verticalGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A)))
        MascotButtonKind.NEUTRAL -> Brush.verticalGradient(listOf(Color(0xFFFFD166), Color(0xFFF59E0B)))
        MascotButtonKind.CLOSE -> Brush.verticalGradient(listOf(Color(0xFFFF8A8A), Color(0xFFE5484D)))
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, Color.White.copy(alpha = if (colors.isDark) 0.35f else 0.85f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/**
 * Maskotun konuşma balonu: üstte isteğe bağlı küçük başlık, harf harf yazılan metin (yer baştan ayrılır, satırlar
 * kaymaz) ve büyük yuvarlak düğmeler. Kuyruk maskotun başına doğru uzanır.
 */
@Composable
fun MascotBalloon(
    typing: Typing,
    modifier: Modifier = Modifier,
    title: String? = null,
    tailAt: Float = 0.82f,
    buttons: @Composable RowScope.() -> Unit,
) {
    val colors = NeuTheme.colors
    val shape = remember(tailAt) { MascotBalloonShape(tailAt = tailAt) }
    val fill = if (colors.isDark) Color(0xFF2B2F45) else Color.White
    val textColor = if (colors.isDark) Color(0xFFF5F3FF) else Color(0xFF2B2140)
    Column(
        modifier
            .widthIn(max = 320.dp)
            .background(fill, shape)
            .border(2.5.dp, Brush.linearGradient(listOf(Color(0xFFFFB938), Color(0xFFA78BFA))), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { typing.skip() }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + 14.dp)
            .testTag("kids_mascot_balloon"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold)
        }
        val shown = typing.typed.coerceAtMost(typing.text.length)
        Text(
            buildAnnotatedString {
                append(typing.text.substring(0, shown))
                withStyle(SpanStyle(color = Color.Transparent)) { append(typing.text.substring(shown)) }
            },
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            modifier = Modifier.semantics { contentDescription = typing.text }.testTag("kids_mascot_text"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            buttons()
        }
    }
}

/**
 * Konuşan maskot: üstte balon ([text] null ise balon yok), altında maskot. Balondaki yazı yazılırken ağzı oynar.
 * [onMascot] maskota dokununca (ör. ipucu söylesin).
 */
@Composable
fun MascotSpeaker(
    look: MascotLook,
    text: String?,
    modifier: Modifier = Modifier,
    title: String? = null,
    figure: Dp = 96.dp,
    onMascot: (() -> Unit)? = null,
    mascotDescription: String? = null,
    buttons: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val typing = text?.let { rememberTyping(it) }
        if (typing != null) {
            MascotBalloon(typing, title = title, buttons = buttons, modifier = Modifier.padding(end = 4.dp))
        }
        val tap = if (onMascot != null) Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onMascot,
        ) else Modifier
        MascotFigure(
            look = look,
            mouthOpen = typing?.mouthOpen == true,
            modifier = Modifier
                .size(figure)
                .then(tap)
                .then(if (mascotDescription != null) Modifier.semantics { contentDescription = mascotDescription } else Modifier)
                .testTag("kids_mascot"),
        )
    }
}
