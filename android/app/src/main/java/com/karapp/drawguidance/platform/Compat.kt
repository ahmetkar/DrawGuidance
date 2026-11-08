package com.karapp.drawguidance.platform

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Display
import android.view.WindowManager

/**
 * v18 sürüme göre API seçimi: yeni Android sürümlerindeki API varsa o, yoksa eski sürümdeki karşılığı kullanılır.
 * Her işlev Android 5.0'da (API 21) da güvenle çağrılabilir.
 */
object Compat {

    /** Donanım (GPU) belleğindeki resim mi? (Android 8.0+; pikselleri okunamaz, önce kopyalanmalı.) */
    fun isHardware(bitmap: Bitmap): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE

    /** Piksellerine erişilebilen (yazılım) kopya; zaten öyleyse kendisi. */
    fun readable(bitmap: Bitmap): Bitmap =
        if (isHardware(bitmap)) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap

    /** Çok satırlı yazı yerleşimi: Android 6.0+ Builder, öncesinde eski kurucu. */
    fun staticLayout(text: CharSequence, paint: TextPaint, width: Int, alignment: Layout.Alignment, includePad: Boolean): StaticLayout =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(alignment)
                .setIncludePad(includePad)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, alignment, 1f, 0f, includePad)
        }

    /**
     * Çizim ekranında ekranın en yüksek yenileme hızı (90/120 Hz) istenir: kalem ve parmak izi daha akıcı ve daha az
     * gecikmeli görünür. Android 6.0+ (ekran kipleri); öncesinde bir şey yapılmaz. Önceki kip kimliğini döndürür.
     */
    fun preferHighRefreshRate(activity: Activity): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val window = activity.window ?: return null
        @Suppress("DEPRECATION")
        val display: Display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else activity.windowManager.defaultDisplay) ?: return null
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return null
        val attrs: WindowManager.LayoutParams = window.attributes
        val previous = attrs.preferredDisplayModeId
        if (best.modeId != previous && best.refreshRate > current.refreshRate + 0.5f) {
            attrs.preferredDisplayModeId = best.modeId
            window.attributes = attrs
        }
        return previous
    }

    /** [preferHighRefreshRate] öncesindeki kipe döner. */
    fun restoreRefreshRate(activity: Activity, previous: Int?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || previous == null) return
        val window = activity.window ?: return
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId != previous) {
            attrs.preferredDisplayModeId = previous
            window.attributes = attrs
        }
    }
}
