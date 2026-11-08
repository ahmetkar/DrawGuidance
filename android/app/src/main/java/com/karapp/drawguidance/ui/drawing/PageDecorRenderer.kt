package com.karapp.drawguidance.ui.drawing

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import com.karapp.drawguidance.data.model.PageAlign
import com.karapp.drawguidance.data.model.PageSettings

/**
 * v40 sayfa yazıları: üst bilgi sayfanın üst şeridine, alt bilgi ve sayfa numarası alt şeridine (ayarlara göre sol,
 * orta ya da sağ) çizilir. Tuvalde (ekran dönüşümüyle) ve tüm çıktılarda (resim, PDF, CBZ) aynı yerleşim kullanılır;
 * yazı rengi kağıdın açıklığına göre koyu ya da açık gridir.
 */
object PageDecorRenderer {

    private val paint = object : ThreadLocal<TextPaint>() {
        override fun initialValue() = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    }

    /** Kağıda göre okunur yazı rengi (WCAG açıklığı). */
    fun inkFor(paperArgb: Int): Int =
        if (DiagramContrast.luminance(paperArgb) < 0.4) 0xFFD0D0D0.toInt() else 0xFF505050.toInt()

    /** Bir satırın yazı boyu (tuval birimi). */
    fun textSize(line: PageSettings.Line, canvasWidth: Float, canvasHeight: Float): Float =
        line.size.ratio * minOf(canvasWidth, canvasHeight)

    /** Satırın taban çizgisi (tuval birimi): üst şeritte yazı boyunun 1,9 katı aşağıda, altta 1,1 katı yukarıda. */
    fun baseline(line: PageSettings.Line, canvasWidth: Float, canvasHeight: Float): Float {
        val size = textSize(line, canvasWidth, canvasHeight)
        return if (line.top) size * 1.9f else canvasHeight - size * 1.1f
    }

    /**
     * [page] sayfasının yazılarını çizer. Tuval birimleri [left], [top] noktasından [scale] ile çizilir (çıktılarda 0, 0, 1;
     * ekranda görünümün dönüşümü). v45 [cover] iken ilk sayfa (kapak) yazısız kalır.
     */
    fun draw(
        canvas: Canvas,
        settings: PageSettings,
        page: Int,
        pageCount: Int,
        canvasWidth: Float,
        canvasHeight: Float,
        paperArgb: Int,
        left: Float = 0f,
        top: Float = 0f,
        scale: Float = 1f,
        /** v45 ilk sayfa çizgi roman kapağı: kapakta yazı yok, numaralar sonraki sayfada 1'den başlar. */
        cover: Boolean = false,
    ) {
        val lines = settings.lines(page, pageCount, cover)
        if (lines.isEmpty()) return
        val p = paint.get()!!
        p.color = inkFor(paperArgb)
        val inset = canvasWidth * 0.045f
        val avail = canvasWidth - 2 * inset
        lines.forEach { line ->
            p.textSize = textSize(line, canvasWidth, canvasHeight) * scale
            val text = TextUtils.ellipsize(line.text, p, avail * scale, TextUtils.TruncateAt.END).toString()
            p.textAlign = when (line.align) {
                PageAlign.LEFT -> Paint.Align.LEFT
                PageAlign.CENTER -> Paint.Align.CENTER
                PageAlign.RIGHT -> Paint.Align.RIGHT
            }
            val x = when (line.align) {
                PageAlign.LEFT -> inset
                PageAlign.CENTER -> canvasWidth / 2f
                PageAlign.RIGHT -> canvasWidth - inset
            }
            canvas.drawText(text, left + x * scale, top + baseline(line, canvasWidth, canvasHeight) * scale, p)
        }
    }
}
