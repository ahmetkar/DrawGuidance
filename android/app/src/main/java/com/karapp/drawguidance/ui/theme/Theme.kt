package com.karapp.drawguidance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.compositeOver

@Composable
fun DrawGuidanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // v36 Ayarlar'da seçilen renk grubu (Lavanta, Okyanus, Gün batımı)
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.remember(context) { ThemePrefs.init(context); context }
    val palette by ThemePrefs.palette.collectAsState()
    // v40 açık / koyu / sistem teması ve yüksek karşıtlık
    val mode by ThemePrefs.mode.collectAsState()
    val highContrast by ThemePrefs.highContrast.collectAsState()
    val dark = mode.isDark(darkTheme)
    val neu = ThemePrefs.resolve(palette, dark, highContrast)
    SystemBarsAppearance(dark)
    // Material bileşenleri (snackbar, dialog, sheet, imleç) neumorphic paletle uyumlu olsun diye
    // tüm yüzey renkleri zemin rengine eşitlenir; v40 derinliği ince kenarlar verir.
    val scheme = if (dark) {
        darkColorScheme(
            primary = neu.accent, onPrimary = neu.onAccent,
            secondary = neu.accent, onSecondary = neu.onAccent,
            background = neu.background, onBackground = neu.content,
            surface = neu.background, onSurface = neu.content,
            surfaceVariant = neu.background, onSurfaceVariant = neu.contentMuted,
            surfaceContainerLowest = neu.background, surfaceContainerLow = neu.background,
            surfaceContainer = neu.background, surfaceContainerHigh = neu.background,
            surfaceContainerHighest = neu.background,
            outline = neu.outlineStrong.compositeOver(neu.background), outlineVariant = neu.outline.compositeOver(neu.background),
            error = neu.danger,
        )
    } else {
        lightColorScheme(
            primary = neu.accent, onPrimary = neu.onAccent,
            secondary = neu.accent, onSecondary = neu.onAccent,
            background = neu.background, onBackground = neu.content,
            surface = neu.background, onSurface = neu.content,
            surfaceVariant = neu.background, onSurfaceVariant = neu.contentMuted,
            surfaceContainerLowest = neu.background, surfaceContainerLow = neu.background,
            surfaceContainer = neu.background, surfaceContainerHigh = neu.background,
            surfaceContainerHighest = neu.background,
            outline = neu.outlineStrong.compositeOver(neu.background), outlineVariant = neu.outline.compositeOver(neu.background),
            error = neu.danger,
        )
    }

    CompositionLocalProvider(LocalNeuColors provides neu) {
        MaterialTheme(colorScheme = scheme, typography = DrawGuidanceTypography) {
            CompositionLocalProvider(LocalContentColor provides neu.content, content = content)
        }
    }
}

/**
 * v40 açık/koyu tema uygulamada seçilince (sistemden farklı olabilir) durum ve gezinme çubuğu simgeleri de buna
 * uyar: açık temada koyu simgeler, koyu temada açık simgeler.
 */
@Composable
private fun SystemBarsAppearance(dark: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    androidx.compose.runtime.SideEffect {
        var ctx: android.content.Context? = view.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val window = (ctx as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
