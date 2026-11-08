package com.karapp.drawguidance.ui.kids

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * v44 çocuk kilidi açık mı (uygulamanın her yerinde; MainActivity sağlar). Açıkken yalnız çocuk çizim modu görünür.
 */
val LocalKidsLocked = staticCompositionLocalOf { false }

/**
 * v44 çocuk kilidinde açılmaması gereken ekran: kilit açıksa [onBlocked] çağrılır (ör. Ana sayfaya dönülür) ve true
 * döner (ekran çizilmez).
 */
@androidx.compose.runtime.Composable
fun kidsBlocked(onBlocked: () -> Unit): Boolean {
    val locked = LocalKidsLocked.current
    if (locked) androidx.compose.runtime.LaunchedEffect(Unit) { onBlocked() }
    return locked
}
