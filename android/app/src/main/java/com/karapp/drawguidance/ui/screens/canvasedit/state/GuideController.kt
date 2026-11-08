package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.model.GuideKind
import com.karapp.drawguidance.data.model.GuideSpec
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlinx.coroutines.launch

/** Kılavuz ve perspektif çizgileri (kağıdın üstünde, çizimlerin altında; geçmişe girmez, hemen kaydedilir). */
class GuideController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val repository: CanvasRepository,
) {
    /** Kılavuz/perspektif ayarları; [persist] false ise yalnızca ekranda (sürükleme sürerken). */
    fun setGuide(spec: GuideSpec, persist: Boolean = true) {
        val current = store.canvas.value ?: return
        if (current.guide == spec && !persist) return
        store.updateCanvas { it.copy(guide = spec) }
        if (persist) host.scope.launch { repository.saveGuide(store.canvasLocalId, spec) }
    }

    /** Kılavuz çizgisi türünü açar ve alttaki ayar çubuğunu gösterir. */
    fun addGuide(kind: GuideKind) {
        val current = store.canvas.value ?: return
        setGuide(current.guide.copy(kind = kind))
        host.startAction(EditMode.GUIDE)
    }

    /** Perspektifi (kaçış noktası sayısı) açar ve alttaki ayar çubuğunu gösterir. */
    fun addPerspective(points: Int) {
        val current = store.canvas.value ?: return
        // v27 not tuvallerinde perspektif yok
        if (current.kind.isNotes) return
        setGuide(current.guide.copy(perspective = points.coerceIn(1, 3)))
        host.startAction(EditMode.PERSPECTIVE)
    }

    /** v27 kılavuz ekleme penceresinden: mevcut kılavuz ve perspektif çizgilerinin hepsi kaldırılır (3B mod korunur). */
    fun clearGuides() {
        val current = store.canvas.value ?: return
        if (current.guide.kind == GuideKind.NONE && current.guide.perspective == 0) return
        setGuide(current.guide.copy(kind = GuideKind.NONE, perspective = 0))
        if (host.mode.value == EditMode.GUIDE || host.mode.value == EditMode.PERSPECTIVE) host.finishAction()
    }

    fun removeGuide() {
        val current = store.canvas.value ?: return
        setGuide(current.guide.copy(kind = GuideKind.NONE))
        host.finishAction()
    }

    fun removePerspective() {
        val current = store.canvas.value ?: return
        setGuide(current.guide.copy(perspective = 0))
        host.finishAction()
    }

    /**
     * Kaçış noktasını sürükler: [fx], [fy] kağıdın 0..1 oranı. İlk iki nokta ufuk çizgisindedir (dikey hareket
     * ufku değiştirir), üçüncüsü dikey eksende.
     */
    fun moveVanishingPoint(index: Int, fx: Float, fy: Float, persist: Boolean) {
        val current = store.canvas.value ?: return
        val g = current.guide
        val x = fx.coerceIn(-0.5f, 1.5f)
        val y = fy.coerceIn(-0.5f, 1.5f)
        val next = when (index) {
            0 -> g.copy(vanishA = x, horizon = y.coerceIn(0f, 1f))
            1 -> g.copy(vanishB = x, horizon = y.coerceIn(0f, 1f))
            else -> g.copy(vanishC = y)
        }
        setGuide(next, persist)
    }
}
