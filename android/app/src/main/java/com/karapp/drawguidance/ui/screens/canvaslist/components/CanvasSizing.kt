package com.karapp.drawguidance.ui.screens.canvaslist.components

import androidx.annotation.StringRes
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import kotlin.math.roundToInt

/** v40 özel boyut birimi: piksel, inç ya da santimetre (inç ve santimetre DPI ile piksele çevrilir). */
enum class SizeUnit(val symbol: String) { PX("px"), IN("in"), CM("cm") }

/** v40 tuval türüne uygun hazır boyut ([width] × [height] piksel). */
data class SizePreset(val key: String, @StringRes val label: Int, val width: Int, val height: Int)

/** v40 özel boyut doğrulamasının sonucu. */
enum class SizeIssue { TOO_SMALL, TOO_LARGE, MEMORY }

/**
 * v40 yeni tuval boyutları: her türün kendi hazır boyutları (resimde kare, telefon duvar kâğıdı, 300 dpi A4; notta
 * A4/A5/Letter sayfaları; çizgi romanda ABD çizgi roman sayfası ve B5 manga; basit türlerde birkaç sade boyut) ve
 * "Özel" boyutun px / in / cm birimleriyle hesabı. Sınırlar: kenar [MIN_SIDE]–[MAX_SIDE] px (düzenleyicinin en büyük
 * tuvali) ve toplam piksel cihazın belleğine göre ([maxPixels]: bir katmanın ARGB bit eşlemi belleğin 1/16'sını aşmaz).
 */
object CanvasSizing {
    const val MIN_SIDE = 16
    const val MAX_SIDE = com.karapp.drawguidance.ui.screens.canvasedit.state.FrameController.MAX_CANVAS
    const val DEFAULT_DPI = 300
    const val MIN_DPI = 36
    const val MAX_DPI = 1200
    const val CUSTOM = "custom"

    private val square = SizePreset("square", R.string.v40_size_square, 1080, 1080)
    private val portrait = SizePreset("portrait", R.string.v40_size_portrait, 1080, 1920)
    private val landscape = SizePreset("landscape", R.string.v40_size_landscape, 1920, 1080)
    private val wallpaper = SizePreset("wallpaper", R.string.v40_size_wallpaper, 1080, 2400)
    private val a4Print = SizePreset("a4_300", R.string.v40_size_a4_300, 2480, 3508)
    // Not sayfaları 150 dpi (eski "Çalışma/Not (A4)" boyutu)
    private val a4 = SizePreset("a4", R.string.v40_size_a4, 1240, 1754)
    private val a5 = SizePreset("a5", R.string.v40_size_a5, 874, 1240)
    private val letter = SizePreset("letter", R.string.v40_size_letter, 1275, 1650)
    private val a4Landscape = SizePreset("a4_landscape", R.string.v40_size_a4_landscape, 1754, 1240)
    private val comicPage = SizePreset("comic_page", R.string.v40_size_comic_page, 1200, 1800)
    // ABD çizgi roman sayfası 6,625 × 10,25 in ve B5 manga (182 × 257 mm), 300 dpi
    private val usComic = SizePreset("us_comic", R.string.v40_size_us_comic, 1988, 3075)
    private val mangaB5 = SizePreset("manga_b5", R.string.v40_size_manga_b5, 2150, 3035)
    private val webtoon = SizePreset("webtoon", R.string.v40_size_webtoon, 800, 1280)

    /** Türün hazır boyutları; ilki türün öntanımlı boyutudur ([CanvasKind.defaultSize] ile aynı). */
    fun presets(kind: CanvasKind): List<SizePreset> = when (kind) {
        CanvasKind.ART -> listOf(square, portrait, landscape, wallpaper, a4Print)
        CanvasKind.NOTES -> listOf(a4, a5, letter, a4Landscape)
        CanvasKind.COMIC -> listOf(comicPage, usComic, mangaB5, webtoon)
        CanvasKind.SIMPLE_ART -> listOf(square, portrait, landscape)
        CanvasKind.SIMPLE_NOTES -> listOf(a4, a5)
        CanvasKind.KIDS -> listOf(square, landscape)
    }

    /** [width] × [height] türün hazır boyutlarından biriyse onun anahtarı, değilse [CUSTOM]. */
    fun match(kind: CanvasKind, width: Int, height: Int): String =
        presets(kind).firstOrNull { it.width == width && it.height == height }?.key ?: CUSTOM

    /** Birimdeki değeri piksele çevirir (inç: × dpi, cm: ÷ 2,54 × dpi). */
    fun toPx(value: Double, unit: SizeUnit, dpi: Int): Int = when (unit) {
        SizeUnit.PX -> value.roundToInt()
        SizeUnit.IN -> (value * dpi).roundToInt()
        SizeUnit.CM -> (value / 2.54 * dpi).roundToInt()
    }

    /** Pikseli birime çevirir. */
    fun fromPx(px: Int, unit: SizeUnit, dpi: Int): Double = when (unit) {
        SizeUnit.PX -> px.toDouble()
        SizeUnit.IN -> px.toDouble() / dpi
        SizeUnit.CM -> px.toDouble() / dpi * 2.54
    }

    /** Alanda gösterilecek değer: px tam sayı, in/cm en çok iki ondalık (sondaki sıfırlar atılır). */
    fun format(value: Double, unit: SizeUnit): String =
        if (unit == SizeUnit.PX) value.roundToInt().toString()
        else java.math.BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** Kullanıcının yazdığı sayı (virgül de ondalık ayırıcı sayılır); geçersizse null. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    /** Bellekte güvenle açılabilecek en çok piksel. */
    fun maxPixels(maxMemory: Long = Runtime.getRuntime().maxMemory()): Long =
        minOf(MAX_SIDE.toLong() * MAX_SIDE, maxMemory / 16)

    /** Boyut uygun değilse sorunu (önce kenar sınırları, sonra bellek). */
    fun validate(width: Int, height: Int, maxPixels: Long = maxPixels()): SizeIssue? = when {
        width < MIN_SIDE || height < MIN_SIDE -> SizeIssue.TOO_SMALL
        width > MAX_SIDE || height > MAX_SIDE -> SizeIssue.TOO_LARGE
        width.toLong() * height > maxPixels -> SizeIssue.MEMORY
        else -> null
    }
}
