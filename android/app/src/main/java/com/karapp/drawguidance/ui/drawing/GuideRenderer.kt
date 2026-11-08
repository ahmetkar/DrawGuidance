package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import com.karapp.drawguidance.data.model.GuideKind
import com.karapp.drawguidance.data.model.GuideSpec
import com.karapp.drawguidance.ui.common.hexToColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Kılavuz ve perspektif çizgileri: kağıdın üstünde, çizimlerin altında çizilir.
 * [paper] kağıdın ekrandaki dikdörtgeni, [scale] tuval biriminin ekran ölçeğidir
 * (aralık tuval biriminde saklanır, ekranda ölçeklenerek çizilir).
 */
object GuideRenderer {

    /** Kaçış noktalarından çıkan ışınların uzunluğu (kağıt köşegeninin katı). */
    private const val RAY_REACH = 2.4f

    fun DrawScope.drawGuide(spec: GuideSpec, paper: Rect, scale: Float, highlightVanish: Boolean = false) {
        val width = max(0.7f, 1.1f * kotlin.math.min(1.5f, max(0.6f, scale)))
        clipRect(paper.left, paper.top, paper.right, paper.bottom) {
            // Kılavuz çizgileri
            if (spec.kind != GuideKind.NONE && spec.opacity > 0.01f) {
                val color = hexToColor(spec.colorHex).copy(alpha = spec.opacity.coerceIn(0f, 1f))
                val step = max(6f, spec.spacing * scale)
                when (spec.kind) {
                    GuideKind.GRID -> grid(paper, step, color, width)
                    GuideKind.RULED -> ruled(paper, step, color, width)
                    GuideKind.DOTS -> dots(paper, step, color, width)
                    GuideKind.ISOMETRIC -> isometric(paper, step, color, width)
                    // Eski kayıtlar: perspektif türleri ayrı alana taşınır
                    GuideKind.PERSPECTIVE_1, GuideKind.PERSPECTIVE_2, GuideKind.PERSPECTIVE_3, GuideKind.NONE -> Unit
                }
            }
            // Perspektif çizgileri (kılavuzdan bağımsız)
            if (spec.perspective in 1..3 && spec.perspectiveOpacity > 0.01f) {
                val color = hexToColor(spec.colorHex).copy(alpha = spec.perspectiveOpacity.coerceIn(0f, 1f))
                perspective(paper, spec, color, width, spec.perspective, highlightVanish)
            }
        }
    }

    /** Kaçış noktalarının kağıttaki konumları (perspektif ayar modunda sürüklemek için). */
    fun vanishingPoints(spec: GuideSpec, paper: Rect): List<Offset> {
        val horizonY = paper.top + paper.height * spec.horizon.coerceIn(0f, 1f)
        val a = Offset(paper.left + paper.width * spec.vanishA.coerceIn(-1f, 2f), horizonY)
        val b = Offset(paper.left + paper.width * spec.vanishB.coerceIn(-1f, 2f), horizonY)
        val c = Offset(paper.left + paper.width * 0.5f, paper.top + paper.height * spec.vanishC.coerceIn(-1f, 2f))
        return when (spec.perspective) {
            1 -> listOf(a)
            2 -> listOf(a, b)
            3 -> listOf(a, b, c)
            else -> emptyList()
        }
    }

    private fun DrawScope.grid(paper: Rect, step: Float, color: Color, width: Float) {
        var x = paper.left
        while (x <= paper.right) {
            drawLine(color, Offset(x, paper.top), Offset(x, paper.bottom), width)
            x += step
        }
        var y = paper.top
        while (y <= paper.bottom) {
            drawLine(color, Offset(paper.left, y), Offset(paper.right, y), width)
            y += step
        }
    }

    private fun DrawScope.ruled(paper: Rect, step: Float, color: Color, width: Float) {
        var y = paper.top + step
        while (y <= paper.bottom) {
            drawLine(color, Offset(paper.left, y), Offset(paper.right, y), width)
            y += step
        }
        // Sol kenar payı
        val margin = paper.left + paper.width * 0.1f
        drawLine(color.copy(alpha = color.alpha * 0.8f), Offset(margin, paper.top), Offset(margin, paper.bottom), width * 1.4f)
    }

    private fun DrawScope.dots(paper: Rect, step: Float, color: Color, width: Float) {
        val r = max(0.8f, width * 1.2f)
        var y = paper.top
        while (y <= paper.bottom) {
            var x = paper.left
            while (x <= paper.right) {
                drawCircle(color, r, Offset(x, y))
                x += step
            }
            y += step
        }
    }

    /**
     * v22 3B sahnenin ızgarası (kameraya göre): üç düzlemin (ön X–Y, yan Z–Y, üst X–Z) sahne merkezinden geçen çizgileri;
     * etkin düzlem belirgin, diğerleri soluk; eksenler biraz daha koyu. Kamera döndükçe ızgara da döner.
     */
    fun DrawScope.drawDraw3d(spec: GuideSpec, paper: Rect, scale: Float, canvasW: Int, canvasH: Int) {
        if (!spec.draw3d) return
        val width = max(0.7f, 1.1f * kotlin.math.min(1.5f, max(0.6f, scale)))
        val base = hexToColor(spec.colorHex)
        val weak = base.copy(alpha = 0.12f)
        val strong = base.copy(alpha = 0.4f)
        val axis = base.copy(alpha = 0.62f)
        val cam = Scene3D.camera(spec, canvasW, canvasH)
        val kx = paper.width / max(1, canvasW); val ky = paper.height / max(1, canvasH)
        fun screen(x: Float, y: Float, z: Float): Offset {
            val p = cam.project(x, y, z)
            return Offset(paper.left + p.x * kx, paper.top + p.y * ky)
        }
        val extent = max(canvasW, canvasH).toFloat()
        val reach = extent * 0.42f
        val step = max(20f, spec.spacing)
        val n = (reach / step).toInt()
        val r = n * step
        // Düzlem: (eksen1, eksen2) çizgileri; sabit eksen 0
        fun grid(plane: com.karapp.drawguidance.data.model.Plane3D, color: androidx.compose.ui.graphics.Color, w: Float) {
            for (i in -n..n) {
                val c = i * step
                val (a0, a1, b0, b1) = when (plane) {
                    com.karapp.drawguidance.data.model.Plane3D.FRONT -> listOf(screen(c, -r, 0f), screen(c, r, 0f), screen(-r, c, 0f), screen(r, c, 0f))
                    com.karapp.drawguidance.data.model.Plane3D.SIDE -> listOf(screen(0f, -r, c), screen(0f, r, c), screen(0f, c, -r), screen(0f, c, r))
                    else -> listOf(screen(c, 0f, -r), screen(c, 0f, r), screen(-r, 0f, c), screen(r, 0f, c))
                }
                drawLine(color, a0, a1, w); drawLine(color, b0, b1, w)
            }
        }
        val active = spec.plane3d
        clipRect(paper.left, paper.top, paper.right, paper.bottom) {
            listOf(com.karapp.drawguidance.data.model.Plane3D.TOP, com.karapp.drawguidance.data.model.Plane3D.FRONT, com.karapp.drawguidance.data.model.Plane3D.SIDE)
                .filter { it != active }.forEach { grid(it, weak, width * 0.8f) }
            if (active != com.karapp.drawguidance.data.model.Plane3D.FREE) grid(active, strong, width)
            // Eksenler
            drawLine(axis, screen(-r, 0f, 0f), screen(r, 0f, 0f), width * 1.3f)
            drawLine(axis, screen(0f, -r, 0f), screen(0f, r, 0f), width * 1.3f)
            drawLine(axis, screen(0f, 0f, -r), screen(0f, 0f, r), width * 1.3f)
        }
    }

    private fun DrawScope.isometric(paper: Rect, step: Float, color: Color, width: Float) {
        // 30° eğimli iki yön ve dikey çizgiler
        val span = paper.width
        var y = paper.top - span * 0.6f
        while (y <= paper.bottom + span * 0.6f) {
            drawLine(color, Offset(paper.left, y), Offset(paper.right, y + span * 0.5774f), width)
            drawLine(color, Offset(paper.left, y), Offset(paper.right, y - span * 0.5774f), width)
            y += step
        }
        var x = paper.left
        while (x <= paper.right) {
            drawLine(color, Offset(x, paper.top), Offset(x, paper.bottom), width * 0.8f)
            x += step * 2f
        }
    }

    private fun DrawScope.perspective(paper: Rect, spec: GuideSpec, color: Color, width: Float, points: Int, highlight: Boolean) {
        val horizonY = paper.top + paper.height * spec.horizon.coerceIn(0f, 1f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
        drawLine(color.copy(alpha = min1(color.alpha * 1.6f)), Offset(paper.left, horizonY), Offset(paper.right, horizonY), width * 1.6f, pathEffect = dash)
        val reach = RAY_REACH * max(paper.width, paper.height)
        val rays = spec.rays.coerceIn(6, 60)
        val vps = ArrayList<Offset>(3)
        when (points) {
            1 -> vps += Offset(paper.left + paper.width * spec.vanishA.coerceIn(-1f, 2f), horizonY)
            2 -> {
                vps += Offset(paper.left + paper.width * spec.vanishA.coerceIn(-1f, 2f), horizonY)
                vps += Offset(paper.left + paper.width * spec.vanishB.coerceIn(-1f, 2f), horizonY)
            }
            else -> {
                vps += Offset(paper.left + paper.width * spec.vanishA.coerceIn(-1f, 2f), horizonY)
                vps += Offset(paper.left + paper.width * spec.vanishB.coerceIn(-1f, 2f), horizonY)
                vps += Offset(paper.left + paper.width * 0.5f, paper.top + paper.height * spec.vanishC.coerceIn(-1f, 2f))
            }
        }
        vps.forEach { vp ->
            for (i in 0 until rays) {
                val a = PI.toFloat() * i / rays
                val d = Offset(cos(a), sin(a)) * reach
                drawLine(color, vp - d, vp + d, width)
            }
            drawCircle(color.copy(alpha = min1(color.alpha * 2f)), max(3f, width * 3f), vp)
            // Ayar modunda kaçış noktaları sürüklenebilir tutamak olarak belirginleşir
            if (highlight) {
                drawCircle(Color.White, 11f, vp)
                drawCircle(color.copy(alpha = 1f), 11f, vp, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            }
        }
    }

    private fun min1(v: Float) = if (v > 1f) 1f else v

    /** Dışa aktarmada kılavuzun çizilip çizilmeyeceği. */
    fun exports(spec: GuideSpec): Boolean = spec.includeInExport && !spec.isEmpty

    /** Boyut yardımcı (dışa aktarma tuval boyutunda çizerken kullanılır). */
    fun paperOf(width: Int, height: Int): Rect = Rect(Offset.Zero, Size(width.toFloat(), height.toFloat()))
}
