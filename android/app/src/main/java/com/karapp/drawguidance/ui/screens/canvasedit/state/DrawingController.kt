package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.ErasedStrokePruner
import com.karapp.drawguidance.ui.screens.canvasedit.EditEvent
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementIndexer
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.screens.canvasedit.editor.GapFiller
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Serbest çizim: parmakla çizilen çizimin eklenmesi (etkin katmana), tamamen silinmiş çizimlerin arka planda veriden
 * temizlenmesi ve boşluk doldurma. Silgi nesneleri (şekil, yazı, resim, diyagram, formül) silmez.
 */
class DrawingController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val brush: () -> BrushController,
) {
    /** Parmak kalktığında (ya da dokunulduğunda) tamamlanan çizimi ekler. */
    /**
     * v18: [penEraser] kalemin silgi ucuyla ya da yan tuşu basılıyken çizildiyse çizgi (seçili fırçadan bağımsız) silgi
     * olarak kaydedilir.
     */
    fun onStrokeFinished(points: List<StrokePoint>, strokeId: String? = null, penEraser: Boolean = false) {
        if (points.isEmpty()) return
        val current = store.canvas.value ?: return
        // Gizli ya da kilitli katmana çizilmez
        val active = current.layers.firstOrNull { it.id == store.activeLayer.value }
        if (active != null && (!active.visible || active.locked)) {
            host.emit(EditEvent.LayerLocked)
            return
        }
        val current0 = brush().settings.value
        val settings = if (!penEraser || current0.brushType == BrushType.ERASER) current0
        else current0.copy(brushType = BrushType.ERASER, sizePx = brush().eraserSize(), opacity = 1f, flow = 1f, strength = 1f)
        val stroke = Stroke(
            id = strokeId ?: UUID.randomUUID().toString(),
            points = compactPoints(points),
            colorHex = settings.colorHex,
            brushType = settings.brushType,
            sizePx = settings.sizePx,
            opacity = settings.opacity,
            order = current.strokes.size,
            flow = settings.flow,
            strength = settings.strength,
            // v20 fırçanın görünüm ayarları (aralık, eğim, 3B); silgide yok
            // v21 3B çizim modu: düzlem ve derinlik (noktalar düzleme zaten yatırılmıştır)
            params = if (settings.brushType == BrushType.ERASER) null else current.guide.draw3dSpec.let { d ->
                if (d == null) settings.params else (settings.params ?: com.karapp.drawguidance.data.model.BrushParams()).copy(depth3d = d)
            },
        )
        // Silgi yalnızca fırçayla çizilenleri (kendi katmanında) siler: şekil, diyagram, yazı, resim ve formül silinmez
        store.commit(current.strokes + stroke)
        if (settings.brushType == BrushType.ERASER) pruneAfterEraser(stroke)
        else host.onInk(stroke)
    }

    /**
     * v32 eğitim modu: fırçası, rengi ve kalınlığı verilmiş hazır çizgi tuvale eklenir (kullanıcının fırça ayarları
     * değişmez); silgiyse silinenler temizlenir, [suggest] ise yapay zekâ önerileri kullanıcının çizimindeki gibi denenir.
     */
    fun commitDemo(stroke: Stroke, suggest: Boolean) {
        val current = store.canvas.value ?: return
        val s = stroke.copy(order = current.strokes.size)
        store.commit(current.strokes + s)
        if (s.brushType == BrushType.ERASER) pruneAfterEraser(s) else if (suggest) host.onInk(s)
    }

    /** Tuval açılınca: eski sürümlerde silinmiş ama veride kalmış (görünmeyen) çizimler bir kez temizlenir. */
    suspend fun cleanOnOpen(loaded: CanvasUi) {
        if (loaded.strokes.none { it.brushType == BrushType.ERASER }) return
        val cleaned = withContext(store.worker) { ErasedStrokePruner.prune(loaded.strokes, null) }
        if (cleaned !== loaded.strokes && store.canvas.value?.strokes === loaded.strokes) store.applySilently(cleaned)
    }

    // --- Tamamen silinenlerin temizliği ---

    /** Temizlenmesi bekleyen bölge: art arda silgi geçişlerinin sınırları birleştirilir, hiçbiri atlanmaz. */
    private var pendingPruneArea: Rect? = null
    /** Henüz temizliği yapılmamış silgiler (bildirimdeki eleman sayısı bunlardan önceki listeye göre sayılır). */
    private val pendingPruneErasers = HashSet<String>()
    private val pruneSignals = Channel<Unit>(Channel.CONFLATED)
    private var pruneWorkerStarted = false

    private fun pruneAfterEraser(eraser: Stroke) {
        val box = ElementOps.bounds(eraser) ?: return
        pendingPruneArea = pendingPruneArea?.let { union(it, box) } ?: box
        pendingPruneErasers += eraser.id
        if (!pruneWorkerStarted) {
            pruneWorkerStarted = true
            host.scope.launch { for (signal in pruneSignals) runPendingPrune() }
        }
        pruneSignals.trySend(Unit)
    }

    private fun union(a: Rect, b: Rect) = Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))

    /**
     * Tamamen silinen çizimler/resimler ve boşa kalan silgiler arka planda, yalnızca silinen bölgede aranır;
     * bulunursa görüntü değişmeden veriden (ve listeden) çıkarılır. Hesap sürerken çizim değişirse sonuç
     * atılır ve bölge bir sonraki turda en güncel çizimlerle yeniden denenir.
     */
    private suspend fun runPendingPrune() {
        while (true) {
            val focus = pendingPruneArea ?: return
            pendingPruneArea = null
            val batchErasers = HashSet(pendingPruneErasers)
            val snapshot = store.canvas.value?.strokes ?: return
            val pruned = withContext(store.worker) { ErasedStrokePruner.prune(snapshot, focus) }
            if (pruned === snapshot) {
                if (store.canvas.value?.strokes !== snapshot) pendingPruneArea = pendingPruneArea?.let { union(it, focus) } ?: focus
                else pendingPruneErasers.removeAll(batchErasers)
                continue
            }
            // Bildirimdeki sayı, silmeden önce listede görünen elemanlara göredir
            val keptIds = pruned.mapTo(HashSet()) { it.id }
            val before = withContext(store.worker) { ElementIndexer().update(snapshot.filterNot { it.id in batchErasers }) }
            if (store.canvas.value?.strokes !== snapshot) {
                // Bu arada yeni çizim geldi: aynı bölge güncel çizimlerle tekrar denenir
                pendingPruneArea = pendingPruneArea?.let { union(it, focus) } ?: focus
                continue
            }
            pendingPruneErasers.removeAll(batchErasers)
            val removedElements = before.elements.count { element -> element.strokeIds.isNotEmpty() && element.strokeIds.none { it in keptIds } }
            store.applySilently(pruned)
            if (removedElements > 0) host.emit(EditEvent.ErasedRemoved(removedElements))
        }
    }

    // --- Boşlukları doldur (v18: komut kutusu kalktı; bağlam menüsünden) ---

    /** Seçili çizgiler arasındaki küçük boşlukları köprülerle kapatır. */
    fun fillGaps() {
        val current = store.canvas.value ?: return
        val ids = store.selection
        if (ids.isEmpty()) {
            host.emit(EditEvent.CommandNeedsSelection)
            return
        }
        val result = GapFiller.fill(current.strokes, ids)
        if (result.bridges.isEmpty()) {
            host.emit(EditEvent.NoGapsFound)
            return
        }
        store.select(ids + result.bridges.map { it.id })
        store.commit(result.strokes)
        host.emit(EditEvent.GapsFilled(result.bridges.size))
    }

    /**
     * Çizim noktalarını kompaktlaştırır: 1/100 birime yuvarlar ve bir öncekine 0,75 birimden
     * yakın (görsel olarak fark yaratmayan) noktaları atar; son nokta her zaman korunur.
     */
    private fun compactPoints(points: List<StrokePoint>): List<StrokePoint> {
        // v18: kalem basıncı ve eğimi korunur (1/255 adımına yuvarlanır: kayıt biçimiyle aynı)
        val q = points.map {
            StrokePoint(
                ContentCodec.quantize(it.x), ContentCodec.quantize(it.y), ContentCodec.quantizeUnit(it.pressure), ContentCodec.quantizeUnit(it.tilt),
                // Yön 0 "bilinmiyor" demektir: gerçek 0° yönü en küçük adıma yuvarlanır
                if (it.orient == 0f) 0f else ContentCodec.quantizeUnit(it.orient).coerceAtLeast(1f / 255f),
            )
        }
        if (q.size <= 2) return q
        val out = ArrayList<StrokePoint>(q.size)
        out += q.first()
        for (i in 1 until q.size - 1) {
            val last = out.last()
            val p = q[i]
            if (kotlin.math.hypot(p.x - last.x, p.y - last.y) >= MIN_POINT_DISTANCE) out += p
        }
        val end = q.last()
        if (out.size > 1 && kotlin.math.hypot(end.x - out.last().x, end.y - out.last().y) < MIN_POINT_DISTANCE) out[out.size - 1] = end else out += end
        return out
    }

    private companion object {
        const val MIN_POINT_DISTANCE = 0.75f
    }
}
