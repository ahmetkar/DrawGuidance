package com.karapp.drawguidance.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.settings.RecentCanvasStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Bırakılıp kapatılan tuval ve bırakıldığı zaman. */
data class LeftCanvas(val canvas: CanvasUi, val closedAt: Long)

/** v40 tuval ve içinde geçirilen etkin düzenleme süresi (ms). */
data class TimedCanvas(val canvas: CanvasUi, val timeSpentMs: Long)

/** v30 Ana sayfanın içeriği. */
data class HomeState(
    val loaded: Boolean = false,
    /** En son bırakılan (devam kartı). */
    val resume: LeftCanvas? = null,
    /** Bırakıp kapattıkların (devam kartındaki hariç). */
    val leftOff: List<LeftCanvas> = emptyList(),
    /** v40 en çok vakit geçirdiklerin (süresi en uzun önce; süresi olmayanlar yok). */
    val mostTime: List<TimedCanvas> = emptyList(),
    /** Tuval sayısı yalnız karşılama yazısı için (v40 sayılar Ana sayfada gösterilmez, Tuvallerim'de). */
    val canvasCount: Int = 0,
    /** v39 en son düzenlenen çocuk tuvali (görev kartı onu açar; yoksa yeni oluşturulur). */
    val kidsCanvasId: String? = null,
)

/**
 * v30 Ana sayfa: son düzenlenen tuvaller ve bırakılıp kapatılanlar (düzenleyiciden çıkış zamanına göre). Silinen
 * tuvaller listelerden düşer.
 */
class HomeViewModel(
    repository: CanvasRepository,
    private val recentStore: RecentCanvasStore,
    private val assetStore: CanvasAssetStore,
    /** v40 tuvalde geçirilen süre (yoksa bölüm boş). */
    canvasTime: com.karapp.drawguidance.data.repository.CanvasTimeRepository? = null,
) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        repository.observeCanvases().onEach { list ->
            // Önizlemelerdeki resimler arka planda yüklenir (yalnız gösterilecek son tuvaller)
            assetStore.ensureLoaded(list.sortedByDescending { it.updatedAt }.take(RECENT + LEFT).flatMap { it.pages.flatten() })
        },
        recentStore.entries,
        canvasTime?.observe() ?: kotlinx.coroutines.flow.flowOf(emptyMap()),
    ) { canvases, entries, times ->
        val most = mostTime(canvases, times, RECENT)
        // v40 en çok vakit geçirilenlerin önizleme resimleri (son düzenlenenlerin dışında kalabilirler)
        assetStore.ensureLoaded(most.flatMap { it.canvas.pages.flatten() })
        val byId = canvases.associateBy { it.localId }
        val left = entries.filter { it.closedAt > 0 }.sortedByDescending { it.closedAt }
            .mapNotNull { e -> byId[e.canvasId]?.let { LeftCanvas(it, e.closedAt) } }
        HomeState(
            loaded = true,
            resume = left.firstOrNull(),
            leftOff = left.drop(1).take(LEFT),
            mostTime = most,
            canvasCount = canvases.size,
            kidsCanvasId = canvases.filter { it.kind == CanvasKind.KIDS }.maxByOrNull { it.updatedAt }?.localId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Silinen tuval bırakılanlardan çıkar. */
    fun forget(id: String) = recentStore.remove(id)

    companion object {
        private const val RECENT = 10
        private const val LEFT = 8

        /**
         * v40 en çok vakit geçirilen [limit] tuval: süresi en uzun önce (eşitse en son düzenlenen önce); hiç süre
         * kaydedilmemiş ya da silinmiş tuvaller yok.
         */
        fun mostTime(canvases: List<CanvasUi>, times: Map<String, Long>, limit: Int): List<TimedCanvas> =
            canvases.mapNotNull { c -> times[c.localId]?.takeIf { it > 0 }?.let { TimedCanvas(c, it) } }
                .sortedWith(compareByDescending<TimedCanvas> { it.timeSpentMs }.thenByDescending { it.canvas.updatedAt })
                .take(limit)
    }
}
