package com.karapp.drawguidance.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v27 pencere açılışını hızlandıran yardımcılar.
 *
 * Bir ModalBottomSheet açılırken içeriğin tamamı ilk karede oluşturulur ve ölçülür; yüzlerce kutulu ızgaralarda bu,
 * kayma animasyonunun başlamasını geciktirir (takılma). [rememberProgressiveCount] öğeleri karelere yayar: ilk karede
 * [first] öğe, sonra her karede [step] öğe daha. Testlerde kareler otomatik ilerlediğinden hepsi kısa sürede görünür.
 */
@Composable
fun rememberProgressiveCount(total: Int, key: Any?, first: Int = 24, step: Int = 16): Int {
    var shown by remember(key) { mutableIntStateOf(minOf(total, first)) }
    LaunchedEffect(key, total) {
        if (shown > total) shown = total
        while (shown < total) {
            withFrameNanos { }
            shown = minOf(total, shown + step)
        }
    }
    return minOf(shown, total)
}

/** v27 küçük resim üretimi için sınırlı iş parçacığı (aynı anda en çok 2; giriş düzeyi cihazda 1). */
object ThumbWork {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dispatcher: CoroutineDispatcher by lazy {
        val low = runCatching { com.karapp.drawguidance.platform.DeviceCaps.tier == com.karapp.drawguidance.platform.DeviceCaps.Tier.LOW }.getOrDefault(false)
        Dispatchers.Default.limitedParallelism(if (low) 1 else 2)
    }
}

/**
 * v37 anahtara bağlı küçük resim (bütün seçicilerin ortak yolu). Eskiden `produceState(önbellek, anahtar)` kullanılıyordu;
 * anahtar değişince durum nesnesi aynı kaldığından eski resim yerinde kalıyor ve yenisi hiç çizilmiyordu (sekme
 * değişince küçük resimler ilk sekmeninkiler olarak kalıyordu). Burada durum anahtarla birlikte yenilenir: önbellekte
 * varsa aynı karede gelir, yoksa [settleFrames] kare beklenip [dispatcher] üzerinde [render] ile üretilir.
 */
@Composable
fun <K : Any> rememberKeyedThumb(
    key: K,
    cached: (K) -> ImageBitmap?,
    settleFrames: Int = 0,
    dispatcher: CoroutineDispatcher = ThumbWork.dispatcher,
    render: suspend (K) -> ImageBitmap?,
): ImageBitmap? {
    val state = remember(key) { mutableStateOf(cached(key)) }
    LaunchedEffect(key) {
        if (state.value != null) return@LaunchedEffect
        repeat(settleFrames) { withFrameNanos { } }
        state.value = withContext(dispatcher) { runCatching { render(key) }.onFailure { com.karapp.drawguidance.diagnostics.AppErrors.report("thumb", it) }.getOrNull() }
    }
    return state.value
}
