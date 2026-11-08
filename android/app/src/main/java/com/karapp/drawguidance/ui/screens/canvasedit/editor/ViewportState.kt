package com.karapp.drawguidance.ui.screens.canvasedit.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Çizim alanının görünümü: tuval biriminden ekran pikseline ölçek ve kaydırma.
 * ekran = offset + tuval × scale
 *
 * [settledScale]/[settledOffset] son "oturmuş" görünümdür; çizim önbelleği bu
 * değerlerle üretilir, hareket sürerken önbellek aradaki farkla ölçeklenerek çizilir.
 */
@Stable
class ViewportState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var settledScale by mutableFloatStateOf(1f)
        private set
    var settledOffset by mutableStateOf(Offset.Zero)
        private set
    /** Tuvalin ekrana tam sığdığı ölçek (%100 yakınlaştırma). */
    var fitScale by mutableFloatStateOf(1f)
        private set
    var viewSize by mutableStateOf(IntSize.Zero)
        private set

    private var canvasWidth = 1
    private var canvasHeight = 1
    private var margin = 0f
    private var initialized = false

    val zoomPercent: Int get() = if (fitScale == 0f) 100 else (scale / fitScale * 100f).roundToInt()

    /** Görünüm boyutu ya da tuval boyutu bildirildiğinde çağrılır; ilk seferde ve görünüm boyutu değişince sığdırır. */
    fun layout(view: IntSize, canvasW: Int, canvasH: Int, marginPx: Float) {
        if (view.width <= 0 || view.height <= 0) return
        canvasWidth = canvasW.coerceAtLeast(1)
        canvasHeight = canvasH.coerceAtLeast(1)
        margin = marginPx
        val oldFit = fitScale
        fitScale = computeFit(view)
        if (!initialized) {
            viewSize = view
            initialized = true
            fit()
        } else if (view != viewSize) {
            // v47 menü satırı değişince (bir ya da iki satır) görünüm küçülür/büyür: kâğıt sığdırılmış görünümdeyse yeniden
            // sığdırılır; kullanıcı yakınlaştırdıysa ölçek korunur, görünümün ortasındaki yer ortada kalır
            val atFit = kotlin.math.abs(scale - oldFit) <= oldFit * 1e-3f
            val old = viewSize
            viewSize = view
            if (atFit) fit() else {
                offset += Offset((view.width - old.width) / 2f, (view.height - old.height) / 2f)
                settle()
            }
        }
    }

    fun fit() {
        if (viewSize.width <= 0) return
        fitScale = computeFit(viewSize)
        scale = fitScale
        offset = Offset((viewSize.width - canvasWidth * scale) / 2f, (viewSize.height - canvasHeight * scale) / 2f)
        settle()
    }

    fun zoomBy(factor: Float, focus: Offset) {
        val newScale = (scale * factor).coerceIn(fitScale * MIN_ZOOM, fitScale * MAX_ZOOM)
        val k = newScale / scale
        offset = focus - (focus - offset) * k
        scale = newScale
    }

    /** Ekran ortasına göre adım adım yakınlaştırır (butonlar için). */
    fun zoomStep(factor: Float) {
        zoomBy(factor, Offset(viewSize.width / 2f, viewSize.height / 2f))
        settle()
    }

    fun panBy(delta: Offset) {
        offset += delta
    }

    fun settle() {
        settledScale = scale
        settledOffset = offset
    }

    /** Tuval sol/üst kenardan büyütüldüğünde çizimler kaydırılır; görüntünün yerinde kalması için görünüm de kaydırılır. */
    fun shiftForResize(canvasDx: Float, canvasDy: Float) {
        offset += Offset(canvasDx * scale, canvasDy * scale)
        settle()
    }

    /**
     * v32 eğitim modu: kâğıdı görünümün [area] bölgesine (görünüm koordinatı) sığdıran ölçek ve kaydırma — açıklama
     * kutusunun kapatmadığı yerde çizim görünsün diye.
     */
    fun fitTarget(area: Rect): Pair<Float, Offset> {
        val s = min((area.width - 2 * margin).coerceAtLeast(1f) / canvasWidth, (area.height - 2 * margin).coerceAtLeast(1f) / canvasHeight)
            .coerceIn(fitScale * MIN_ZOOM, fitScale)
        return s to Offset(area.center.x - canvasWidth * s / 2f, area.center.y - canvasHeight * s / 2f)
    }

    /** Görünümü doğrudan ayarlar (eğitimde yumuşak geçiş); [settle] ise çizim önbelleği bu görünüme göre yenilenir. */
    fun setView(scale: Float, offset: Offset, settle: Boolean) {
        this.scale = scale
        this.offset = offset
        if (settle) settle()
    }

    fun toCanvas(screen: Offset): Offset = (screen - offset) / scale
    fun toScreen(canvas: Offset): Offset = offset + canvas * scale
    fun toScreen(rect: Rect): Rect = Rect(toScreen(rect.topLeft), toScreen(rect.bottomRight))

    private fun computeFit(view: IntSize): Float {
        val availableW = (view.width - 2 * margin).coerceAtLeast(1f)
        val availableH = (view.height - 2 * margin).coerceAtLeast(1f)
        return min(availableW / canvasWidth, availableH / canvasHeight)
    }

    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 12f
    }
}

@Composable
fun rememberViewportState(): ViewportState = remember { ViewportState() }
