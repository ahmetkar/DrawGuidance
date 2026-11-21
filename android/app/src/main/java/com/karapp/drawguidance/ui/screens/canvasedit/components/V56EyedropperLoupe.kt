package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.karapp.drawguidance.data.model.EyedropLoupe
import com.karapp.drawguidance.ui.common.hexToColor

/**
 * v56 damlalığın canlı renk dairesi (2D-5): dokunma noktasının 60 dp yukarısında halka; üst yarısı o an altındaki renk,
 * alt yarısı önceki renk; ortada artı ve 1 px nokta; dış kenar açık ve koyu çift çizgi. Kenarlara yaklaşınca ekranda
 * kalır. Bırakınca 150 ms'de solar. [screen] dokunma noktasının ekran (alan) konumudur; null ise halka gizlenir.
 */
@Composable
fun EyedropperLoupe(screen: Offset?, hex: String?, previousHex: String, kids: Boolean, modifier: Modifier = Modifier) {
    // Solma için son konum ve renk saklanır
    var lastScreen by remember { mutableStateOf<Offset?>(null) }
    var lastHex by remember { mutableStateOf<String?>(null) }
    var lastPrev by remember { mutableStateOf(previousHex) }
    if (screen != null) { lastScreen = screen; lastHex = hex; lastPrev = previousHex }
    val alpha by animateFloatAsState(if (screen != null) 1f else 0f, tween(if (screen != null) 0 else 150), label = "loupe")
    val at = lastScreen ?: return
    if (alpha <= 0.01f) return
    val density = LocalDensity.current
    val r = with(density) { EyedropLoupe.radius(kids).let { androidx.compose.ui.unit.Dp(it) }.toPx() }
    val lift = with(density) { androidx.compose.ui.unit.Dp(EyedropLoupe.LIFT).toPx() }
    val ringW = with(density) { androidx.compose.ui.unit.Dp(if (kids) 18f else 14f).toPx() }
    val current = lastHex?.let { hexToColor(it) }
    val previous = hexToColor(lastPrev)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val (cx, cy) = EyedropLoupe.center(at.x, at.y, w, h, r, lift)
        Canvas(Modifier.fillMaxSize().testTag("eyedropper_loupe").semantics { contentDescription = lastHex ?: "" }) {
            val c = Offset(cx, cy)
            val inner = r - ringW / 2f
            val box = androidx.compose.ui.geometry.Rect(c.x - inner, c.y - inner, c.x + inner, c.y + inner)
            // Üst yarı: altındaki renk (yoksa saydam desen yerine gri); alt yarı: önceki renk
            drawArc(current ?: Color(0x66888888), 180f, 180f, false, box.topLeft, Size(box.width, box.height), alpha = alpha, style = Stroke(ringW))
            drawArc(previous, 0f, 180f, false, box.topLeft, Size(box.width, box.height), alpha = alpha, style = Stroke(ringW))
            // Çift dış kenar: her zeminde görünür
            drawCircle(Color.White, r + 1.5f, c, alpha = alpha, style = Stroke(2f))
            drawCircle(Color.Black.copy(alpha = 0.6f), r + 3.5f, c, alpha = alpha, style = Stroke(2f))
            drawCircle(Color.Black.copy(alpha = 0.6f), r - ringW - 1f, c, alpha = alpha, style = Stroke(1.5f))
            // Artı ve 1 px nokta
            val arm = r * 0.22f
            drawLine(Color.Black.copy(alpha = 0.7f), Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 1.5f, alpha = alpha)
            drawLine(Color.Black.copy(alpha = 0.7f), Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 1.5f, alpha = alpha)
            drawCircle(current ?: Color.White, 1.5f, c, alpha = alpha)
        }
    }
}
