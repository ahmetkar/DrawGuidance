package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState

/**
 * v45 çocuk modunda boya fırçası aracı: sürükleme parmağın geçtiği yeri boyar (darbe parmak kalkınca kaydedilir), kısa
 * dokunuş tek bir boya lekesi bırakır. İki parmak gelirse darbe bırakılır, görünüm kaydırılır. Gizli/kilitli katmanda
 * darbe başlamaz (sürükleme görünümü kaydırır).
 */
internal class PaintBrushTool : CanvasTool() {
    private var started = false
    private var last = Offset.Zero

    override fun begin(ctx: GestureContext): Boolean {
        started = ctx.actions.onBrushDown(ctx.startCanvas)
        last = ctx.startCanvas
        return started
    }

    override fun move(ctx: GestureContext, position: Offset, point: Offset) {
        if (!started) return
        last = point
        ctx.actions.onBrushMove(point)
    }

    override fun end(ctx: GestureContext, preview: Preview?) {
        if (!started) return
        started = false
        ctx.actions.onBrushUp(last)
    }

    override fun cancel(ctx: GestureContext) {
        super.cancel(ctx)
        if (started) ctx.actions.onBrushCancel()
        started = false
    }

    override fun tap(ctx: GestureContext, point: Offset, upTime: Long) {
        if (ctx.actions.onBrushDown(point)) ctx.actions.onBrushUp(point)
    }
}

/**
 * v45 süren fırça darbesinin önizlemesi: boya, darbenin başladığı alanın sınırıyla kırpılarak (kaydedilecek hâliyle
 * aynı) çizilir; boş kâğıtta kırpılmaz.
 */
@Composable
fun KidsBrushLayer(viewModel: CanvasEditViewModel, viewport: ViewportState, modifier: Modifier = Modifier) {
    val live by viewModel.brushLive.collectAsState()
    val stroke = live ?: return
    val color = hexToColor(stroke.colorHex)
    val outline = stroke.region?.outline
    val clip = remember(outline) {
        outline?.let { o ->
            Path().apply {
                fillType = PathFillType.EvenOdd
                o.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                close()
            }
        }
    }
    val state = if (stroke.region == null) "free" else "clipped:${stroke.region.strokeId}"
    Canvas(modifier.fillMaxSize().testTag("kids_brush_live").semantics { stateDescription = state }) {
        val sc = viewport.scale
        withTransform({
            translate(viewport.offset.x, viewport.offset.y)
            scale(sc, sc, Offset.Zero)
        }) {
            val path = Path().apply {
                stroke.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            val width = KidsBrush.radius(stroke.sizePx) * 2f
            val draw = {
                if (stroke.points.size == 1) drawCircle(color, width / 2f, stroke.points[0])
                else drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (clip != null) clipPath(clip) { draw() } else draw()
        }
        // Parmağın yerinde fırçanın boyu (ince beyaz halka): çocuk ne kadar kalın boyadığını görür
        stroke.points.lastOrNull()?.let { p ->
            val at = viewport.toScreen(p)
            val r = (KidsBrush.radius(stroke.sizePx) * sc).coerceAtLeast(6.dp.toPx())
            drawCircle(Color.White.copy(alpha = 0.9f), r, at, style = Stroke(2.5.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.25f), r + 1.5.dp.toPx(), at, style = Stroke(1.dp.toPx()))
        }
    }
}

/** v45 çocuk menüsünün kendine özgü simgeleri (uygulamada başka yerde kullanılmaz). */
object KidsIcons {
    /**
     * Boya fırçası: çapraz duran kalın saplı fırça, yuvarlak kıl ucu ve altında dalgalı boya izi ile damla. Çizilerek
     * üretilir; kovanın, kalemin ve fırça seçicinin simgeleriyle karışmaz.
     */
    val PaintBrush: ImageVector by lazy {
        ImageVector.Builder("KidsPaintBrush", 24.dp, 24.dp, 24f, 24f).apply {
            // Sap (sağ üstten sol alta)
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.4f, 2.1f)
                curveTo(20.0f, 1.5f, 21.0f, 1.5f, 21.6f, 2.1f)
                lineTo(21.9f, 2.4f)
                curveTo(22.5f, 3.0f, 22.5f, 4.0f, 21.9f, 4.6f)
                lineTo(15.2f, 11.3f)
                lineTo(12.7f, 8.8f)
                close()
            }
            // Bilezik (sapla kıl arasındaki halka; aradaki boşlukla ayrı görünür)
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.0f, 9.5f)
                lineTo(14.5f, 12.0f)
                lineTo(13.2f, 13.3f)
                lineTo(10.7f, 10.8f)
                close()
            }
            // Kıl ucu: sol alta doğru yuvarlak, sivri uçlu damla biçimi
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.1f, 11.4f)
                lineTo(12.6f, 13.9f)
                curveTo(12.9f, 16.4f, 10.9f, 18.3f, 8.2f, 18.0f)
                curveTo(7.2f, 17.9f, 6.3f, 17.6f, 5.6f, 17.9f)
                curveTo(6.6f, 16.6f, 6.4f, 15.6f, 6.9f, 14.2f)
                curveTo(7.4f, 12.8f, 8.6f, 11.9f, 10.1f, 11.4f)
                close()
            }
            // Dalgalı boya izi
            path(
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(2.6f, 21.3f)
                curveTo(5.0f, 19.4f, 7.4f, 22.6f, 10.2f, 21.0f)
                curveTo(12.0f, 20.0f, 13.2f, 20.6f, 14.6f, 21.4f)
            }
            // Damla
            path(fill = SolidColor(Color.Black)) {
                moveTo(18.0f, 15.0f)
                curveTo(18.9f, 16.3f, 19.6f, 17.2f, 19.6f, 18.0f)
                curveTo(19.6f, 18.9f, 18.9f, 19.6f, 18.0f, 19.6f)
                curveTo(17.1f, 19.6f, 16.4f, 18.9f, 16.4f, 18.0f)
                curveTo(16.4f, 17.2f, 17.1f, 16.3f, 18.0f, 15.0f)
                close()
            }
        }.build()
    }
}
