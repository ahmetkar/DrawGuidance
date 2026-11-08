package com.karapp.drawguidance.ui.screens.canvasedit.ruler

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** v40 cetvelin birimi; [perInch] bir inçteki birim sayısı (piksel için 0: tuval pikseli). */
enum class RulerUnit(val label: String, val perInch: Float) {
    MM("mm", 25.4f),
    CM("cm", 2.54f),
    IN("in", 1f),
    PX("px", 0f);

    companion object {
        fun parse(value: String?): RulerUnit = entries.firstOrNull { it.name == value } ?: CM
    }
}

/**
 * v40 cetvel ölçüleri: tuval pikseli ↔ birim. Tuvallerde DPI bilgisi saklanmaz; not alma A4 şablonu 150 dpi
 * (1240 × 1754) olduğundan bütün tuvallerde [DEFAULT_DPI] kullanılır. Ölçek 1:N: kağıttaki 1 birim gerçekte N birim
 * (ör. 1:100'de kağıtta 1 cm = 100 cm); etiketler gerçek ölçüyü gösterir.
 */
object RulerUnits {
    const val DEFAULT_DPI = 150f

    /** Sunulan ölçekler (1:N). */
    val Scales: List<Int> = listOf(1, 2, 5, 10, 20, 50, 100)

    /** [px] tuval pikselinin [unit] birimindeki kağıt ölçüsü (ölçeksiz). */
    fun toUnits(px: Float, unit: RulerUnit, dpi: Float = DEFAULT_DPI): Float =
        if (unit == RulerUnit.PX) px else px / dpi * unit.perInch

    /** [value] birimin tuval pikseli. */
    fun toPx(value: Float, unit: RulerUnit, dpi: Float = DEFAULT_DPI): Float =
        if (unit == RulerUnit.PX) value else value / unit.perInch * dpi

    /** [px] uzunluğun 1:[scale] ölçekte gerçek ölçüsü. */
    fun real(px: Float, unit: RulerUnit, scale: Int, dpi: Float = DEFAULT_DPI): Float = toUnits(px, unit, dpi) * scale.coerceAtLeast(1)

    /** Etiket: "12,5 cm" gibi (en çok bir ondalık; tam sayılarda ondalık yok). */
    fun format(px: Float, unit: RulerUnit, scale: Int, dpi: Float = DEFAULT_DPI): String =
        "${number(real(px, unit, scale, dpi))} ${unit.label}"

    fun number(value: Float): String {
        val s = String.format(java.util.Locale.ROOT, "%.1f", value)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    fun scaleLabel(scale: Int): String = "1:${scale.coerceAtLeast(1)}"

    /**
     * Cetvel çentiklerinin aralığı (kağıt birimi): küçük çentikler en az [minGapPx] ekran pikseli aralıkla.
     * [pxPerUnit] bir birimin ekran pikseli. Dönen: küçük çentik adımı (birim) ve kaç küçükte bir büyük (etiketli) çentik.
     */
    fun tickStep(pxPerUnit: Float, minGapPx: Float, unit: RulerUnit): Pair<Float, Int> {
        val steps = when (unit) {
            RulerUnit.MM -> floatArrayOf(1f, 5f, 10f, 50f, 100f, 500f, 1000f)
            RulerUnit.CM -> floatArrayOf(0.1f, 0.5f, 1f, 5f, 10f, 50f, 100f)
            RulerUnit.IN -> floatArrayOf(0.125f, 0.25f, 0.5f, 1f, 2f, 5f, 10f)
            RulerUnit.PX -> floatArrayOf(10f, 50f, 100f, 500f, 1000f, 5000f)
        }
        for (s in steps) {
            if (s * pxPerUnit >= minGapPx) {
                val major = when (unit) {
                    RulerUnit.IN -> if (s < 1f) (1f / s).roundToInt() else 1
                    else -> if (s == 0.1f || s == 1f || s == 10f || s == 100f) 10 else 2
                }
                return s to major.coerceAtLeast(1)
            }
        }
        return steps.last() to 1
    }
}

/** v40 cetveli döndürürken açı yakalama (15°'nin katları). */
object AngleSnap {
    const val STEP = 15f
    /** Yakalama payı (derece). */
    const val TOLERANCE = 4f
    /** Parmak tutamakta bu kadar hareketsiz kalırsa yakalama bu sürükleme için bırakılır (ince ayar). */
    const val RELEASE_MS = 600L

    /** Açıyı -180..180 aralığına getirir. */
    fun normalize(deg: Float): Float {
        var a = deg % 360f
        if (a > 180f) a -= 360f
        if (a <= -180f) a += 360f
        return a
    }

    /** [deg] en yakın [step] katına [tolerance] içindeyse ona yapışır; değilse aynen döner (normalize edilmiş). */
    fun snap(deg: Float, enabled: Boolean = true, step: Float = STEP, tolerance: Float = TOLERANCE): Float {
        val a = normalize(deg)
        if (!enabled) return a
        val nearest = floor(a / step + 0.5f) * step
        return if (abs(a - nearest) <= tolerance) normalize(nearest) else a
    }

    /** Açıyı 0..360 aralığında gösterir (etiket). */
    fun display(deg: Float): Int = ((normalize(deg).roundToInt() % 360) + 360) % 360
}
