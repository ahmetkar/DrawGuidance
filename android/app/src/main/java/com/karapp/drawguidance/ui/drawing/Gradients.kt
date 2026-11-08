package com.karapp.drawguidance.ui.drawing

import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import com.karapp.drawguidance.data.model.GradientSpec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * v27 gradyanlar (tuval arka planı, şekil ve kutu dolgusu, yazı, balon): [GradientSpec]'ten Android gölgelendiricisi.
 * Doğrusal gradyan kutunun merkezinden geçer ve açıya göre kutuyu tam kaplar; dairesel gradyan merkezden köşelere.
 */
object Gradients {
    /** Hazır gradyanlar (seçicilerde; ilk ikisi gökyüzü ve gün batımı). */
    val presets: List<GradientSpec> = listOf(
        GradientSpec(listOf("#87CEEB", "#E0F7FF"), 90f),
        GradientSpec(listOf("#FF7E5F", "#FEB47B"), 90f),
        GradientSpec(listOf("#654EA3", "#EAAFC8"), 45f),
        GradientSpec(listOf("#11998E", "#38EF7D"), 0f),
        GradientSpec(listOf("#FFF6B7", "#F6416C"), 135f),
        GradientSpec(listOf("#0F2027", "#203A43", "#2C5364"), 90f),
        GradientSpec(listOf("#FFFFFF", "#FFE082"), 0f, radial = true),
        GradientSpec(listOf("#FFFFFF", "#90CAF9"), 0f, radial = true),
        GradientSpec(listOf("#F8FF00", "#3AD59F"), 90f),
        GradientSpec(listOf("#FF0844", "#FFB199"), 45f),
        GradientSpec(listOf("#000000", "#434343"), 90f),
        GradientSpec(listOf("#FDFBFB", "#EBEDEE"), 90f),
    )

    fun colors(spec: GradientSpec, alpha: Float = 1f): IntArray = IntArray(spec.colors.size) { i ->
        val c = BackgroundRenderer.parseColor(spec.colors[i], 0xFFFFFFFF.toInt())
        // Rengin kendi saydamlığı korunur, örtücülükle çarpılır
        (((c ushr 24) * alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255) shl 24) or (c and 0x00FFFFFF)
    }

    /** [l],[t],[r],[b] kutusu için gölgelendirici (gradyan geçersizse null). */
    fun shader(spec: GradientSpec?, l: Float, t: Float, r: Float, b: Float, alpha: Float = 1f): Shader? {
        if (spec == null || !spec.valid || r - l < 1e-3f || b - t < 1e-3f) return null
        val cols = colors(spec, alpha)
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        if (spec.radial) {
            val radius = max(hypot(r - l, b - t) / 2f, 1f)
            return RadialGradient(cx, cy, radius, cols, null, Shader.TileMode.CLAMP)
        }
        val a = Math.toRadians(spec.angle.toDouble())
        val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
        val half = (abs((r - l) * dx) + abs((b - t) * dy)) / 2f
        return LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, cols, null, Shader.TileMode.CLAMP)
    }

    /** Gradyanın ortalama rengi (eski uygulamalar ve tek renk gereken yerler için). */
    fun average(spec: GradientSpec): Int {
        val cols = colors(spec)
        var a = 0; var r = 0; var g = 0; var b = 0
        cols.forEach { c -> a += c ushr 24; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF }
        val n = cols.size.coerceAtLeast(1)
        return ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }
}
