package com.karapp.drawguidance.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.karapp.drawguidance.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v36 Ayarlar → Görünüm: tasarımın ana renk grupları. Her grup açık ve koyu temada bir nöromorfik palet verir; zemin,
 * gölgeler ve vurgu birlikte değişir, yazı renkleri her grupta zemine göre yeterli karşıtlıkta seçildi (vurgu üstünde
 * beyaz yazı, zeminde koyu mürekkep). [LAVENDER] uygulamanın ilk paleti (öntanımlı).
 */
enum class ThemePalette(@StringRes val label: Int, val light: NeuColors, val dark: NeuColors) {
    LAVENDER(R.string.theme_palette_lavender, LightNeuColors, DarkNeuColors),
    OCEAN(
        R.string.theme_palette_ocean,
        NeuColors(
            background = Color(0xFFE2EBEE), lightShadow = Color(0xFFFFFFFF), darkShadow = Color(0xFFA3B8BF),
            accent = Color(0xFF0F7C8A), onAccent = Color(0xFFFFFFFF), content = Color(0xFF223540),
            contentMuted = Color(0xFF627A84), danger = Color(0xFFD14B4B), isDark = false,
            gradientStart = Color(0xFF0F766E), gradientEnd = Color(0xFF0E6FA0), gradientDeep = Color(0xFF0B2E3A),
        ),
        NeuColors(
            background = Color(0xFF232A2D), lightShadow = Color(0xFF313B3F), darkShadow = Color(0xFF121618),
            accent = Color(0xFF5FD0D8), onAccent = Color(0xFF0B2326), content = Color(0xFFE2EEF0),
            contentMuted = Color(0xFF94A8AE), danger = Color(0xFFFF7B7B), isDark = true,
            gradientStart = Color(0xFF0F766E), gradientEnd = Color(0xFF0E6FA0), gradientDeep = Color(0xFF0B2E3A),
        ),
    ),
    SUNSET(
        R.string.theme_palette_sunset,
        NeuColors(
            background = Color(0xFFEEE7E3), lightShadow = Color(0xFFFFFFFF), darkShadow = Color(0xFFC0B1A8),
            accent = Color(0xFFB9511F), onAccent = Color(0xFFFFFFFF), content = Color(0xFF3A2E2A),
            contentMuted = Color(0xFF7D6C65), danger = Color(0xFFC0392B), isDark = false,
            gradientStart = Color(0xFFC2410C), gradientEnd = Color(0xFFBE185D), gradientDeep = Color(0xFF431407),
        ),
        NeuColors(
            background = Color(0xFF2C2724), lightShadow = Color(0xFF3B3430), darkShadow = Color(0xFF171412),
            accent = Color(0xFFFF9A62), onAccent = Color(0xFF2A1508), content = Color(0xFFF0E6E1),
            contentMuted = Color(0xFFA9998F), danger = Color(0xFFFF7B7B), isDark = true,
            gradientStart = Color(0xFFC2410C), gradientEnd = Color(0xFFBE185D), gradientDeep = Color(0xFF431407),
        ),
    ),
    ;

    fun colors(dark: Boolean): NeuColors = if (dark) this.dark else light

    companion object {
        fun of(name: String?): ThemePalette = entries.firstOrNull { it.name == name } ?: LAVENDER
    }
}

/** v40 Ayarlar → Renk teması: açık, koyu ya da sistemin ayarı (öntanımlı). */
enum class ThemeMode(@StringRes val label: Int) {
    LIGHT(R.string.v40_theme_mode_light),
    DARK(R.string.v40_theme_mode_dark),
    SYSTEM(R.string.v40_theme_mode_system),
    ;

    /** Etkin tema koyu mu ([systemDark]: cihazın gece modu). */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }

    companion object {
        fun of(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * v40 yüksek karşıtlık: paletin zemini ve kimliği korunur; yazı siyah/beyaz olur, ikincil yazı ve vurgu zemine karşı
 * en az 7:1 (WCAG AAA) karşıtlığa getirilir, vurgu üstündeki yazı da en az 7:1 olur. Kenarlar opak çizilir
 * ([NeuColors.outline]) ve seçim çerçevesi kalınlaşır.
 */
object HighContrast {
    const val TEXT_MIN = 7f

    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** [c] rengini [toward] rengine doğru, [bg] zeminine karşı [min] karşıtlığa ulaşana dek karıştırır. */
    fun reach(c: Color, toward: Color, bg: Color, min: Float): Color {
        var t = 0f
        var out = c
        while (contrast(out, bg) < min && t < 1f) {
            t = (t + 0.05f).coerceAtMost(1f)
            out = lerp(c, toward, t)
        }
        return out
    }

    fun apply(c: NeuColors): NeuColors {
        if (c.highContrast) return c
        val ink = if (c.isDark) Color.White else Color.Black
        val accent = reach(c.accent, ink, c.background, TEXT_MIN)
        val onAccent = if (contrast(accent, Color.White) >= contrast(accent, Color.Black)) Color.White else Color.Black
        return c.copy(
            content = ink,
            contentMuted = reach(c.contentMuted, ink, c.background, TEXT_MIN),
            accent = accent,
            onAccent = onAccent,
            danger = reach(c.danger, ink, c.background, 4.5f),
            darkShadow = if (c.isDark) c.darkShadow else reach(c.darkShadow, ink, c.background, 3f),
            highContrast = true,
        )
    }
}

/**
 * v36 seçili renk grubu: `dg_theme` ayar dosyasında saklanır, ilk erişimde bir kez okunur. Değişince bütün ekranlar
 * yeniden başlatma gerekmeden yeni palete geçer ([DrawGuidanceTheme] bu akışı dinler). v40: açık / koyu / sistem
 * teması ([mode]) ve yüksek karşıtlık ([highContrast]) da aynı dosyada saklanır.
 */
object ThemePrefs {
    private const val FILE = "dg_theme"
    private const val KEY = "palette"
    private const val KEY_MODE = "mode"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private val _palette = MutableStateFlow(ThemePalette.LAVENDER)
    val palette: StateFlow<ThemePalette> = _palette.asStateFlow()
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()
    private val _highContrast = MutableStateFlow(false)
    val highContrast: StateFlow<Boolean> = _highContrast.asStateFlow()
    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val p = prefs(context)
            _palette.value = ThemePalette.of(p.getString(KEY, null))
            _mode.value = ThemeMode.of(p.getString(KEY_MODE, null))
            _highContrast.value = p.getBoolean(KEY_HIGH_CONTRAST, false)
            loaded = true
        }
    }

    /** Diğer ayarlar dosyadan okunmadan önce değiştirilirse kaybolmasın diye önce yüklenir. */
    fun select(context: Context, palette: ThemePalette) {
        init(context)
        _palette.value = palette
        prefs(context).edit().putString(KEY, palette.name).apply()
    }

    fun selectMode(context: Context, mode: ThemeMode) {
        init(context)
        _mode.value = mode
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun setHighContrast(context: Context, on: Boolean) {
        init(context)
        _highContrast.value = on
        prefs(context).edit().putBoolean(KEY_HIGH_CONTRAST, on).apply()
    }

    /** Etkin palet: renk grubu + açık/koyu + yüksek karşıtlık. */
    fun resolve(palette: ThemePalette, dark: Boolean, highContrast: Boolean): NeuColors =
        palette.colors(dark).let { if (highContrast) HighContrast.apply(it) else it }
}
