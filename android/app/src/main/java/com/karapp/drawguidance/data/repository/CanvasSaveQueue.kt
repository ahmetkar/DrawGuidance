package com.karapp.drawguidance.data.repository

import com.karapp.drawguidance.data.model.Stroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Çizim kayıtlarını arka planda, birleştirerek yazar: art arda gelen kayıtlardan yalnızca
 * en sonuncusu sıkıştırılıp veritabanına yazılır (hızlı çizimde ana iş parçacığı ve disk
 * gereksiz iş yapmaz). Uygulama ömrü boyunca çalışır; ekran kapansa da bekleyen kayıt tamamlanır.
 */
class CanvasSaveQueue(private val repository: CanvasRepository) {

    private data class Pending(
        val pages: List<List<Stroke>>, val width: Int, val height: Int, val sizeChanged: Boolean,
        val backgroundColorHex: String? = null, val currentPage: Int? = null,
        val layers: List<com.karapp.drawguidance.data.model.Layer> = listOf(com.karapp.drawguidance.data.model.Layer.Default),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = ConcurrentHashMap<String, Pending>()
    private val signals = Channel<String>(Channel.UNLIMITED)
    private val inFlight = AtomicInteger(0)
    private val flushes = Channel<Unit>(Channel.CONFLATED)

    /** Bekleyen kayıt beklemeden yazılsın (uygulama arka plana geçti, ekran kapandı). */
    fun flushNow() {
        if (pending.isNotEmpty()) flushes.trySend(Unit)
    }

    init {
        scope.launch {
            for (localId in signals) {
                if (!pending.containsKey(localId)) continue
                // Art arda çizilen çizgiler tek kayıtta birleşsin diye kısa bir süre beklenir; uygulama arka plana
                // geçerken (flushNow) beklenmeden hemen yazılır
                inFlight.incrementAndGet()
                withTimeoutOrNull(COALESCE_MS) { flushes.receive() }
                inFlight.decrementAndGet()
                val job = synchronized(pending) { pending.remove(localId) } ?: continue
                inFlight.incrementAndGet()
                try {
                    repository.saveDocument(
                        localId, job.pages, job.width, job.height, job.sizeChanged, job.backgroundColorHex, job.currentPage, job.layers,
                    )
                } catch (e: Exception) {
                    // Bir sonraki kayıt yeniden deneyecek
                    com.karapp.drawguidance.diagnostics.AppLog.e("CanvasSave", "Tuval kaydedilemedi ($localId)", e, userVisible = true)
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        }
    }

    /** [backgroundColorHex] verilirse arka plan rengi de yazılır (null = değişmedi). */
    fun enqueue(
        localId: String,
        pages: List<List<Stroke>>,
        width: Int,
        height: Int,
        sizeChanged: Boolean,
        backgroundColorHex: String? = null,
        currentPage: Int? = null,
        layers: List<com.karapp.drawguidance.data.model.Layer> = listOf(com.karapp.drawguidance.data.model.Layer.Default),
    ) {
        // Map.merge Android 7.0+; eski sürümlerde de çalışsın diye kilitle birleştirilir
        val new = Pending(pages, width, height, sizeChanged, backgroundColorHex, currentPage, layers)
        synchronized(pending) {
            val old = pending[localId]
            pending[localId] = if (old == null) new else new.copy(
                sizeChanged = old.sizeChanged || new.sizeChanged,
                backgroundColorHex = new.backgroundColorHex ?: old.backgroundColorHex,
                currentPage = new.currentPage ?: old.currentPage,
            )
        }
        signals.trySend(localId)
    }

    companion object {
        /** Hızlı çizimde kayıtların birleştirildiği süre. */
        const val COALESCE_MS = 400L
    }

    /** Bekleyen tüm kayıtlar yazılana kadar bekler. */
    suspend fun awaitIdle() {
        while (pending.isNotEmpty() || inFlight.get() > 0) delay(10)
    }
}
