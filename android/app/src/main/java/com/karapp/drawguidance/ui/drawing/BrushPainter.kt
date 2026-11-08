package com.karapp.drawguidance.ui.drawing

import android.graphics.Canvas
import android.graphics.Path
import com.karapp.drawguidance.data.model.BrushType

/**
 * Fırça izlerini doğrudan Android Canvas'a çizen motor (yüksek hacimde toplu çizim için
 * Compose çizim katmanının altında çalışır). Her kategori kendi motoruyla çizer ([StrokeStream]);
 * canlı çizim de aynı motoru parça parça kullanır, böylece ekranda görülen ile kaydedilen aynıdır.
 *
 * - Çizim gücü (bastırma): iz kalınlığı [pressureWidth], koyuluk [pressureAlpha] ve dokulu
 *   fırçalarda kağıt dişindeki boşlukların dolması.
 * - Akışkanlık (boya birikmesi): damga başına bırakılan boya. Damgalar üst üste bindikçe boya
 *   birikir; opaklık ise tek çizimin ulaşabileceği en yüksek koyuluktur (katmanla sınırlanır).
 * - Rastgelelik çizim kimliğinden ve damga sırasından türetilir; aynı çizim her seferinde aynı görünür.
 */
object BrushPainter {

    fun paint(
        canvas: Canvas,
        xs: FloatArray,
        ys: FloatArray,
        count: Int,
        color: Int,
        type: BrushType,
        sizePx: Float,
        opacity: Float,
        seed: Int,
        flow: Float,
        strength: Float,
        /** v18 kalem basıncı ve eğimi (nokta başına; kalemsiz çizimde null). */
        pressures: FloatArray? = null,
        tilts: FloatArray? = null,
        /** v20 fırça görünüm ayarları ve kalem yönü. */
        params: com.karapp.drawguidance.data.model.BrushParams? = null,
        orients: FloatArray? = null,
    ) {
        if (count <= 0 || type == BrushType.IMAGE || type == BrushType.TEXT) return
        val stream = StrokeStream.create(type, color, sizePx, opacity, seed, flow, strength, params = params)
        stream.setPoints(xs, ys, count, pressures, tilts, orients)
        stream.drawStatic(canvas)
    }

    /** Kalem dinamikleri: nokta başına basınç, eğim ve (v20) yön; kalemle çizilmemişse null'lar. */
    class Dynamics(val pressures: FloatArray?, val tilts: FloatArray?, val orients: FloatArray?) {
        operator fun component1() = pressures
        operator fun component2() = tilts
        operator fun component3() = orients
    }

    /** v18: çizimin nokta başına basınç ve eğim dizileri (kalemle çizilmemişse null); v20: yön. */
    fun dynamicsOf(points: List<com.karapp.drawguidance.data.model.StrokePoint>): Dynamics {
        if (points.none { it.hasDynamics }) return Dynamics(null, null, null)
        val p = if (points.any { it.pressure != 1f }) FloatArray(points.size) { points[it].pressure } else null
        val t = if (points.any { it.tilt != 0f }) FloatArray(points.size) { points[it].tilt } else null
        val o = if (points.any { it.orient != 0f }) FloatArray(points.size) { points[it].orient } else null
        return Dynamics(p, t, o)
    }

    /** Ham noktalardan (kalem/pastel için eğrilerle yumuşatılmış) yol. */
    fun buildPath(xs: FloatArray, ys: FloatArray, count: Int, smooth: Boolean): Path {
        val path = Path()
        path.moveTo(xs[0], ys[0])
        if (!smooth || count < 3) {
            for (i in 1 until count) path.lineTo(xs[i], ys[i])
            return path
        }
        for (i in 1 until count) path.quadTo(xs[i - 1], ys[i - 1], (xs[i - 1] + xs[i]) / 2f, (ys[i - 1] + ys[i]) / 2f)
        path.lineTo(xs[count - 1], ys[count - 1])
        return path
    }
}
