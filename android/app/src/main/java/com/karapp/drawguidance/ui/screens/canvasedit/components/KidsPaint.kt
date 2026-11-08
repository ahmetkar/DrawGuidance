package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.VectorEdit
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.screens.canvasedit.state.PaintCursor
import com.karapp.drawguidance.ui.theme.NeuTheme

/** v37 çocuk menüsünde boyama düğmesinin simgesi: boya kovası (imleçle aynı). */
val KidsPaintIcon: ImageVector get() = Icons.Filled.FormatColorFill

/**
 * v37 çocuk modunda boyama: parmağın biraz üstünde süzülen boya kovası (parmak kovayı örtmez) ve kovadan dokunulan
 * noktaya akan boya; kovanın altındaki şekil parlar, boyanmaya hazır alan seçili renkle yarı saydam görünür. Parmak
 * basılı kaldıkça çevresindeki halka 2 sn'de dolar: dolunca bütün şekil boyanır.
 */
@Composable
fun KidsPaintLayer(cursor: PaintCursor?, canvas: CanvasUi, viewport: ViewportState, colorHex: String, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val progress = remember { Animatable(0f) }
    val hold = cursor?.hold ?: -1
    LaunchedEffect(hold, cursor == null) {
        progress.snapTo(0f)
        if (cursor != null && hold >= 0) progress.animateTo(1f, tween(LONG_PRESS_MS.toInt(), easing = LinearEasing))
    }
    if (cursor == null) return
    val paint = hexToColor(cursor.color ?: colorHex)
    val target = cursor.target
    val shape = target?.let { t -> canvas.strokes.firstOrNull { it.id == t.strokeId } }
    val outlines = remember(shape) {
        shape?.let { s -> ShapeFactory.subPaths(s).filter { it.points.size >= 2 && !com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.isPaintLayer(it.tag) && it.label == null } }.orEmpty()
    }
    Canvas(modifier.fillMaxSize().testTag("kids_paint_cursor")) {
        val sc = viewport.scale
        // Şekil vurgusu ve boyanmaya hazır alan (tuval koordinatında, görünümle birlikte)
        withTransform({
            translate(viewport.offset.x, viewport.offset.y)
            scale(sc, sc, Offset.Zero)
        }) {
            val glow = colors.accent
            outlines.forEach { p ->
                val path = polyline(p.points, p.closed)
                drawPath(path, glow.copy(alpha = 0.28f), style = Stroke(9.dp.toPx() / sc, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Color.White.copy(alpha = 0.9f), style = Stroke(2.dp.toPx() / sc, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            target?.region?.let { region ->
                val path = polyline(region, true)
                drawPath(path, paint.copy(alpha = 0.5f))
                drawPath(
                    path, glow, style = Stroke(
                        2.5.dp.toPx() / sc, join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx() / sc, 6.dp.toPx() / sc)),
                    ),
                )
            }
        }
        val p = viewport.toScreen(cursor.point)
        // Basılı tutma halkası
        if (hold >= 0 && target != null) {
            val r = 30.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.85f), r, p, style = Stroke(7.dp.toPx()))
            drawArc(
                paint, -90f, 360f * progress.value, false, p - Offset(r, r), Size(2 * r, 2 * r),
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        drawBucket(p, paint, 1.dp.toPx())
    }
}

private fun polyline(points: List<Offset>, closed: Boolean) = Path().apply {
    points.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    if (closed) close()
}

/**
 * Boya kovası: [at] noktasının sol üstünde, ağzı noktaya doğru eğik (dökülüyor); metal gövde (silindir gibi soldan sağa
 * ışık), ağzında ve etiketinde boya rengi, sapı arkada (tutuluyormuş gibi). Ağzın alt kenarından noktaya boya akar,
 * noktada küçük boya lekesi.
 */
private fun DrawScope.drawBucket(at: Offset, paint: Color, dp: Float) {
    val w = 38f * dp; val h = 34f * dp; val bw = 28f * dp
    val center = at + Offset(-36f * dp, -58f * dp)
    val tilt = 105f
    val rad = Math.toRadians(tilt.toDouble())
    val cs = kotlin.math.cos(rad).toFloat(); val sn = kotlin.math.sin(rad).toFloat()
    // Kovanın kendi biriminde bir noktanın ekrandaki yeri (saat yönünde [tilt] derece)
    fun turned(x: Float, y: Float) = center + Offset(x * cs - y * sn, x * sn + y * cs)
    val lip = turned(w / 2f - 3f * dp, -h / 2f)
    val dir = Offset(sn, -cs).let { it / it.getDistance() }
    // Akan boya: ağzın alt kenarından çıkıp aşağı kıvrılarak noktaya
    val flow = Path().apply {
        moveTo(lip.x, lip.y)
        quadraticTo(lip.x + dir.x * 16f * dp, lip.y + dir.y * 16f * dp, at.x, at.y - 3f * dp)
    }
    drawPath(flow, Color.Black.copy(alpha = 0.16f), style = Stroke(8f * dp, cap = StrokeCap.Round))
    drawPath(flow, paint, style = Stroke(5.5f * dp, cap = StrokeCap.Round))
    drawPath(flow, Color.White.copy(alpha = 0.35f), style = Stroke(1.5f * dp, cap = StrokeCap.Round))
    drawCircle(Color.White, 7f * dp, at)
    drawCircle(paint, 5.2f * dp, at)
    drawCircle(Color.White.copy(alpha = 0.7f), 1.6f * dp, at + Offset(-1.6f * dp, -1.6f * dp))
    rotate(tilt, center) {
        val top = center.y - h / 2f; val bottom = center.y + h / 2f
        // Sap: kenarlardan arkaya dolanır (gövdenin ardında kalan kısmı görünmez)
        drawArc(
            Color(0xFF6B7280), 0f, 180f, false, Offset(center.x - w / 2f + 2f * dp, top - h * 0.1f), Size(w - 4f * dp, h * 1.5f),
            style = Stroke(3f * dp, cap = StrokeCap.Round),
        )
        // Gövde (aşağı doğru daralan)
        val body = Path().apply {
            moveTo(center.x - w / 2f, top)
            lineTo(center.x + w / 2f, top)
            lineTo(center.x + bw / 2f, bottom)
            quadraticTo(center.x, bottom + 5f * dp, center.x - bw / 2f, bottom)
            close()
        }
        drawPath(
            body, Brush.horizontalGradient(
                0f to Color(0xFF9CA3AF), 0.3f to Color(0xFFF3F4F6), 0.55f to Color(0xFFD1D5DB), 1f to Color(0xFF6B7280),
                startX = center.x - w / 2f, endX = center.x + w / 2f,
            ),
        )
        drawPath(body, Color(0xFF4B5563).copy(alpha = 0.7f), style = Stroke(2f * dp, join = StrokeJoin.Round))
        // Etiket bandı (boya rengi)
        val band = Path().apply {
            val y0 = top + h * 0.38f; val y1 = top + h * 0.66f
            val x0 = w / 2f - (w - bw) / 2f * 0.38f; val x1 = w / 2f - (w - bw) / 2f * 0.66f
            moveTo(center.x - x0, y0); lineTo(center.x + x0, y0); lineTo(center.x + x1, y1); lineTo(center.x - x1, y1); close()
        }
        drawPath(band, paint)
        drawPath(band, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.25f)), startX = center.x - w / 2f, endX = center.x + w / 2f))
        // Ağız: boya dolu elips ve parlak kenar
        drawOval(Color(0xFF4B5563), Offset(center.x - w / 2f, top - 6f * dp), Size(w, 12f * dp))
        drawOval(paint, Offset(center.x - w / 2f + 2.5f * dp, top - 4f * dp), Size(w - 5f * dp, 8f * dp))
        drawOval(Color.White.copy(alpha = 0.45f), Offset(center.x - w / 4f, top - 3f * dp), Size(w / 3f, 2.6f * dp))
        // Kenardan sarkan damla
        drawCircle(paint, 2.8f * dp, Offset(center.x - w / 2f + 5f * dp, top + 3.5f * dp))
    }
}
