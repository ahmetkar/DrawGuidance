package com.karapp.drawguidance.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Neumorphism (soft UI) paleti: yüzeyler zeminle aynı renktedir. v40: derinlik artık gölgeyle değil, tasarıma uyan
 * ince yarı saydam bir kenarla verilir ([outline]); [lightShadow] / [darkShadow] yalnız birkaç çizim ayrıntısında
 * (logo, kâğıt) kullanılır.
 */
@Immutable
data class NeuColors(
    val background: Color,
    val lightShadow: Color,
    val darkShadow: Color,
    val accent: Color,
    val onAccent: Color,
    val content: Color,
    val contentMuted: Color,
    val danger: Color,
    val isDark: Boolean,
    /** v36 renk grubunun gradyanları (giriş düğmesi, ana sayfa kartları); öntanımlı marka renkleri. */
    val gradientStart: Color = Color(0xFF4F46E5),
    val gradientEnd: Color = Color(0xFF9333EA),
    val gradientDeep: Color = Color(0xFF1E1B4B),
    /** v40 yüksek karşıtlık: opak kenarlar, güçlü yazı ve kalın seçim çerçevesi (bkz. [highContrast]). */
    val highContrast: Boolean = false,
) {
    /** v40 kutuların ince kenarı: içerik renginin düşük saydamlıklısı; yüksek karşıtlıkta opak içerik rengi. */
    val outline: Color get() = if (highContrast) content else content.copy(alpha = if (isDark) 0.18f else 0.14f)

    /** v40 gömülü (basılı, giriş alanı) yüzeyin kenarı: biraz daha belirgin. */
    val outlineStrong: Color get() = if (highContrast) content else content.copy(alpha = if (isDark) 0.30f else 0.24f)
}

val LightNeuColors = NeuColors(
    background = Color(0xFFE4E9F1),
    lightShadow = Color(0xFFFFFFFF),
    darkShadow = Color(0xFFA6B3C7),
    accent = Color(0xFF5A5BD8),
    onAccent = Color(0xFFFFFFFF),
    content = Color(0xFF2C3547),
    contentMuted = Color(0xFF6F7B91),
    danger = Color(0xFFD14B4B),
    isDark = false,
)

val DarkNeuColors = NeuColors(
    background = Color(0xFF272B33),
    lightShadow = Color(0xFF353A44),
    darkShadow = Color(0xFF15171B),
    accent = Color(0xFF9290FA),
    onAccent = Color(0xFF16162A),
    content = Color(0xFFE3E8F0),
    contentMuted = Color(0xFF98A2B5),
    danger = Color(0xFFFF7B7B),
    isDark = true,
)

val LocalNeuColors = staticCompositionLocalOf { LightNeuColors }

object NeuTheme {
    val colors: NeuColors
        @Composable @ReadOnlyComposable
        get() = LocalNeuColors.current
}

val NeuCardShape = RoundedCornerShape(22.dp)
val NeuFieldShape = RoundedCornerShape(16.dp)
val NeuPillShape = RoundedCornerShape(50)


/**
 * Bileşeni sade (v40) yüzey olarak çizer: dolgu + şekle uyan ince yarı saydam kenar; kenarlarda gölge yok.
 * - inset = false: düz kutu (kart, düğme), 1 dp [NeuColors.outline] kenar
 * - inset = true: gömülü görünüm (basılı düğme, giriş alanı): dolgu içerik rengine doğru hafifçe koyulaşır, kenar
 *   biraz belirginleşir ([NeuColors.outlineStrong])
 * - selected = true: seçili öğe (çip, araç, tür kartı): vurgu renginde kalın kenar ve hafif vurgu dolgusu
 * - elevation <= 0: yalnız dolgu (kenar yok)
 * Yüksek karşıtlıkta kenarlar opaktır ve kalınlaşır (seçimde 2,5 dp).
 * [elevation] geriye uyum için kalır (0 değilse kenar çizilir).
 */
fun Modifier.neumorphic(
    colors: NeuColors,
    shape: Shape = NeuCardShape,
    elevation: Dp = 6.dp,
    inset: Boolean = false,
    fill: Color = colors.background,
    selected: Boolean = false,
): Modifier {
    val style = NeuSurfaceStyle.of(colors, inset, selected, fill, elevation > 0.dp)
    val filled = this.background(style.fill, shape)
    return if (style.borderWidth > 0.dp) filled.border(style.borderWidth, style.border, shape) else filled
}

/** v40 yüzeyin çizim biçimi (dolgu, kenar rengi ve kalınlığı); testlerde de doğrulanır. */
data class NeuSurfaceStyle(val fill: Color, val border: Color, val borderWidth: Dp) {
    companion object {
        fun of(colors: NeuColors, inset: Boolean, selected: Boolean, fill: Color = colors.background, outlined: Boolean = true): NeuSurfaceStyle {
            val hc = colors.highContrast
            val surface = when {
                selected -> colors.accent.copy(alpha = if (hc) 0.16f else if (colors.isDark) 0.16f else 0.10f).compositeOver(fill)
                inset -> colors.content.copy(alpha = if (colors.isDark) 0.07f else 0.05f).compositeOver(fill)
                else -> fill
            }
            if (!outlined) return NeuSurfaceStyle(surface, Color.Transparent, 0.dp)
            return when {
                selected -> NeuSurfaceStyle(surface, colors.accent, if (hc) 2.5.dp else 1.5.dp)
                inset -> NeuSurfaceStyle(surface, colors.outlineStrong, if (hc) 1.5.dp else 1.dp)
                else -> NeuSurfaceStyle(surface, colors.outline, if (hc) 1.5.dp else 1.dp)
            }
        }
    }
}

/** v40 gölge yerine kutu kenarı (ör. `.shadow(...)` kullanan kartlar için): şekle uyan ince kenar. */
fun Modifier.neuOutline(colors: NeuColors, shape: Shape, selected: Boolean = false): Modifier =
    if (selected) border(if (colors.highContrast) 2.5.dp else 1.5.dp, colors.accent, shape)
    else border(if (colors.highContrast) 1.5.dp else 1.dp, colors.outline, shape)
