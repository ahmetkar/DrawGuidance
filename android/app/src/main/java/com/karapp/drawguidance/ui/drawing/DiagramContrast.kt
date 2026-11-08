package com.karapp.drawguidance.ui.drawing

import kotlin.math.pow

/**
 * v40 diyagram yazılarının okunurluğu (WCAG 2.x): dolgu üstündeki yazı renginin karşıtlık oranı 4,5'in altındaysa
 * yazı siyah ya da beyaz (hangisi dolguyla daha karşıtsa) olur. Koyu dolgulu diyagramlarda (ör. koyu kalem rengiyle
 * dolgu, koyu kutu rengi) yazı artık kaybolmaz.
 */
object DiagramContrast {

    /** WCAG AA normal yazı eşiği. */
    const val MIN_RATIO = 4.5

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** Göreli açıklık (0 siyah … 1 beyaz). */
    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    /** İki rengin karşıtlık oranı (1..21). */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * [fill] üstünde okunur yazı rengi: [text] yeterince karşıtsa kendisi, değilse siyah ya da beyaz (yazının saydamlığı
     * korunur). Yazı saydam dolgunun üstündeyse ([fillAlpha] < 0,5) dolgu zemini belirlemez, yazı değişmez.
     */
    fun readable(text: Int, fill: Int?, fillAlpha: Float = 1f): Int {
        if (fill == null) return text
        val alpha = ((fill ushr 24) / 255f) * fillAlpha
        if (alpha < 0.5f) return text
        val opaqueFill = fill or (0xFF shl 24)
        val opaqueText = text or (0xFF shl 24)
        if (ratio(opaqueText, opaqueFill) >= MIN_RATIO) return text
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val best = if (ratio(black, opaqueFill) >= ratio(white, opaqueFill)) 0x000000 else 0xFFFFFF
        return (text and (0xFF shl 24)) or best
    }
}
