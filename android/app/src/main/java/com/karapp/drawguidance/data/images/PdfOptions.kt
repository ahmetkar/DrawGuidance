package com.karapp.drawguidance.data.images

import kotlin.math.min
import kotlin.math.roundToInt

/** Hangi sayfalar PDF'e girer. */
enum class PdfPages { ALL, CURRENT, RANGE }

/** Kağıt boyutu (nokta, 1/72 inç; dikey). [CANVAS]: kağıt tuvalin oranında (kısa kenar A4 genişliği). */
enum class PdfPaper(val widthPt: Int, val heightPt: Int) {
    CANVAS(0, 0),
    A4(595, 842),
    A5(420, 595),
    LETTER(612, 792),
}

/** Sabit boyutlu kağıtta yön; [AUTO]: tuval yataysa yatay. */
enum class PdfOrientation { AUTO, PORTRAIT, LANDSCAPE }

/** Kenar boşluğu (nokta). */
enum class PdfMargin(val pt: Int) { NONE(0), NARROW(18), NORMAL(36) }

/**
 * [SHARP]: silgi kullanılmayan sayfalar vektör olarak yazılır (yazı, şekil ve formül her büyütmede keskin, dosya küçük);
 * silgili sayfalar resim olur. [IMAGE]: tüm sayfalar [PdfOptions.imageScale] çözünürlükte resim.
 */
enum class PdfQuality { SHARP, IMAGE }

/** v19 "PDF olarak kaydet" penceresinde seçilen ayarlar. */
data class PdfOptions(
    /** Uzantısız dosya adı. */
    val fileName: String,
    val pages: PdfPages = PdfPages.ALL,
    /** [PdfPages.RANGE] için 1'den başlayan ilk ve son sayfa (dahil). */
    val fromPage: Int = 1,
    val toPage: Int = Int.MAX_VALUE,
    val paper: PdfPaper = PdfPaper.CANVAS,
    val orientation: PdfOrientation = PdfOrientation.AUTO,
    val margin: PdfMargin = PdfMargin.NONE,
    val quality: PdfQuality = PdfQuality.SHARP,
    /** Resim olarak yazılan sayfaların çözünürlüğü (tuval boyutunun katı; en uzun kenar sınırlıdır). */
    val imageScale: Float = 2f,
    /** false: tuvalin rengi yerine beyaz kağıt (yazdırmak için). */
    val background: Boolean = true,
    val includeGuides: Boolean = false,
    /** Sayfanın altına "n / toplam" yazılır. */
    val pageNumbers: Boolean = false,
    /** v40 kaydetme yeri ("Konum seç"). */
    val target: SaveTarget = SaveTarget.Default,
    /** v40 baskıya hazır PDF: sayfalar DeviceCMYK resim olarak yazılır (null: RGB). */
    val cmyk: com.karapp.drawguidance.data.print.CmykOptions? = null,
) {
    /** PDF'e girecek sayfaların (0'dan başlayan) sırası. */
    fun pageIndices(pageCount: Int, currentPage: Int): List<Int> {
        val count = pageCount.coerceAtLeast(1)
        return when (pages) {
            PdfPages.ALL -> (0 until count).toList()
            PdfPages.CURRENT -> listOf(currentPage.coerceIn(0, count - 1))
            PdfPages.RANGE -> {
                val a = min(fromPage, toPage).coerceIn(1, count)
                val b = maxOf(fromPage, toPage).coerceIn(a, count)
                ((a - 1) until b).toList()
            }
        }
    }

    companion object {
        /** Pencerede sunulan resim çözünürlükleri: standart, yüksek, çok yüksek. */
        val ImageScales = listOf(1f, 2f, 3f)
    }
}

/**
 * Bir PDF sayfasının yerleşimi (nokta): sayfa boyu, tuvalin sayfadaki sol üst köşesi ve ölçeği; [footer] sayfa
 * numarası için alttaki şerit (0: yok).
 */
data class PdfPageLayout(
    val pageWidth: Int,
    val pageHeight: Int,
    val left: Float,
    val top: Float,
    val scale: Float,
    val footer: Float,
)

object PdfLayout {
    /** Tuval oranındaki kağıdın kısa kenarı (A4 genişliği). */
    const val CANVAS_SHORT_SIDE = 595f

    /** Sayfa numarası şeridinin yüksekliği. */
    const val FOOTER = 22f

    fun of(canvasWidth: Int, canvasHeight: Int, options: PdfOptions): PdfPageLayout {
        val cw = canvasWidth.coerceAtLeast(1).toFloat()
        val ch = canvasHeight.coerceAtLeast(1).toFloat()
        val margin = options.margin.pt.toFloat()
        val footer = if (options.pageNumbers) FOOTER else 0f
        // Sayfa numarası alt boşluğa yazılır; boşluk yetmezse şerit kadar yer ayrılır
        val bottom = maxOf(margin, footer)
        if (options.paper == PdfPaper.CANVAS) {
            val scale = CANVAS_SHORT_SIDE / min(cw, ch)
            val w = (cw * scale + 2 * margin).roundToInt().coerceAtLeast(1)
            val h = (ch * scale + margin + bottom).roundToInt().coerceAtLeast(1)
            return PdfPageLayout(w, h, margin, margin, scale, footer)
        }
        val landscape = when (options.orientation) {
            PdfOrientation.AUTO -> cw > ch
            PdfOrientation.PORTRAIT -> false
            PdfOrientation.LANDSCAPE -> true
        }
        val pw = if (landscape) options.paper.heightPt else options.paper.widthPt
        val ph = if (landscape) options.paper.widthPt else options.paper.heightPt
        val areaW = (pw - 2 * margin).coerceAtLeast(1f)
        val areaH = (ph - margin - bottom).coerceAtLeast(1f)
        val scale = min(areaW / cw, areaH / ch)
        // Tuval alanın ortasına yerleşir
        val left = margin + (areaW - cw * scale) / 2f
        val top = margin + (areaH - ch * scale) / 2f
        return PdfPageLayout(pw, ph, left, top, scale, footer)
    }
}
