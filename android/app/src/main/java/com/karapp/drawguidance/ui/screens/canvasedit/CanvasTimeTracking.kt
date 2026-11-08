package com.karapp.drawguidance.ui.screens.canvasedit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karapp.drawguidance.data.repository.CanvasTimeRepository
import com.karapp.drawguidance.data.repository.EditTimeTracker
import org.koin.compose.koinInject

/** v40 tek saat kaynağı (ms, tekdüze): duvar saati değişse de süre bozulmaz. */
internal fun monotonicMs(): Long = System.nanoTime() / 1_000_000L

/**
 * v40 düzenleyicide geçirilen etkin süre: dönen değiştirici düzenleyicinin bütün dokunuşlarını ve tuşlarını tüketmeden
 * izler ([EditTimeTracker.onInput]); uygulama ön plandayken sayılır, arka plana geçince (ON_STOP) durur ve süre
 * yazılır. Açıkken her [FLUSH_MS] ms'de bir ve düzenleyici kapanınca biriken süre [CanvasTimeRepository]'ye eklenir.
 */
@Composable
fun rememberEditTimeModifier(canvasId: String): Modifier {
    val repo = koinInject<CanvasTimeRepository>()
    val tracker = remember(canvasId) { EditTimeTracker() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(canvasId, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> tracker.onForeground(monotonicMs())
                Lifecycle.Event.ON_STOP -> {
                    tracker.onBackground(monotonicMs())
                    repo.record(canvasId, tracker.drain(monotonicMs()))
                }
                else -> Unit
            }
        }
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) tracker.onForeground(monotonicMs())
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            tracker.onBackground(monotonicMs())
            repo.record(canvasId, tracker.drain(monotonicMs()))
        }
    }
    LaunchedEffect(canvasId) {
        while (true) {
            kotlinx.coroutines.delay(FLUSH_MS)
            repo.record(canvasId, tracker.drain(monotonicMs()))
        }
    }
    return remember(tracker) {
        Modifier
            .pointerInput(tracker) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        tracker.onInput(monotonicMs())
                    }
                }
            }
            .onPreviewKeyEvent { tracker.onInput(monotonicMs()); false }
    }
}

private const val FLUSH_MS = 15_000L
