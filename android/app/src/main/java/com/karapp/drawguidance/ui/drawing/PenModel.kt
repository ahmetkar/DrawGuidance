package com.karapp.drawguidance.ui.drawing

import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.PenDynamics
import com.karapp.drawguidance.data.model.TipParams
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * v51 kalem ve hareket dinamikleri ([PenDynamics]) ile boyut aralığının ([TipParams.minSize], [TipParams.maxSize]) bir
 * ham noktadaki etkisi. Saf hesap (Android'e bağlı değil): iOS `PenModel.swift` aynı formülleri kullanır.
 *
 * Girdiler ham nokta dizileridir; hız, yön, mesafe ve zaman noktalardan türetilir (zaman: nokta sırası, ~120 Hz).
 * Böylece kaydedilen çizim her açılışta aynı görünür.
 */
class PenModel private constructor(private val tip: TipParams?, private val pen: PenDynamics?) {

    /** Basıncın boyuta etkisi ayarlandı mı (ayarlanmadıysa fırçanın kendi basınç eğrisi kullanılır). */
    val overridesPressure: Boolean get() = pen?.pressure != null

    /** Uç açısını değiştiren bir dinamik var mı. */
    val turnsTip: Boolean
        get() = pen != null && ((pen.tiltDirection ?: 0f) != 0f || (pen.rotation ?: 0f) != 0f || (pen.direction ?: 0f) != 0f)

    /**
     * Boyut çarpanı. [base] fırçanın kendi dinamiklerinden gelen çarpan (basınç/eğim; yoksa 1). Sonuç önce kalem
     * dinamikleriyle çarpılır, sonra en küçük/en büyük boyut aralığına yerleştirilir.
     */
    fun size(base: Float, p: PointData, i: Int): Float {
        var f = base
        val d = pen
        if (d != null) {
            d.pressure?.let { amt ->
                val pr = p.pressure(i)
                f *= 1f - amt.coerceIn(0f, 1f) * (1f - pr.coerceIn(0f, 1f).toDouble().pow(0.7).toFloat())
            }
            val tilt = p.tilt(i)
            if (tilt > 0f) {
                val az = p.azimuth(i)
                val tx = tilt * cos(az); val ty = tilt * sin(az)
                d.tiltX?.let { f *= (1f + it * abs(tx)).coerceAtLeast(0.05f) }
                d.tiltY?.let { f *= (1f + it * abs(ty)).coerceAtLeast(0.05f) }
            }
            d.velocity?.let { amt -> f *= (1f + amt * (p.speed(i) - 0.35f) * 1.4f).coerceIn(0.1f, 2.5f) }
            d.distance?.let { amt -> f *= (1f - amt.coerceIn(0f, 1f) * (p.along(i) / max(1f, p.width * DISTANCE_SPAN)).coerceAtMost(1f)).coerceAtLeast(0.05f) }
            d.barrel?.let { amt -> f *= (1f + amt * p.barrel(i)).coerceAtLeast(0.05f) }
        }
        val t = tip ?: return f
        val min = t.minSize?.coerceIn(0f, 1f) ?: 0f
        val maxS = t.maxSize?.coerceIn(0.1f, 3f) ?: 1f
        return (min + (1f - min) * f) * maxS
    }

    /** Opaklık çarpanı (basıncın opaklığa etkisi ve zamanla solma). */
    fun alpha(p: PointData, i: Int): Float {
        val d = pen ?: return 1f
        var a = 1f
        d.pressureOpacity?.let { amt -> a *= 1f - amt.coerceIn(0f, 1f) * (1f - p.pressure(i).coerceIn(0f, 1f)) }
        d.time?.let { amt -> a *= (1f - amt.coerceIn(0f, 1f) * (i / TIME_SPAN).coerceAtMost(1f)).coerceAtLeast(0.05f) }
        return a.coerceIn(0.02f, 1f)
    }

    /** Uç açısına eklenecek dönüş (radyan): kalemin yöneldiği yön, gövde dönüşü ve çizgi yönü. */
    fun turn(p: PointData, i: Int, dirX: Float, dirY: Float): Float {
        val d = pen ?: return 0f
        var a = 0f
        d.tiltDirection?.let { if (p.tilt(i) > 0f) a += it * p.azimuth(i) }
        d.rotation?.let { a += it * p.roll(i) * 2f * PI.toFloat() }
        d.direction?.let { a += it * atan2(dirY, dirX) }
        return a
    }

    /** Bir çizginin ham nokta dizileri ve iz genişliği (hız/mesafe ölçeği için). */
    class PointData(
        var xs: FloatArray, var ys: FloatArray, var count: Int,
        var pp: FloatArray?, var pt: FloatArray?, var po: FloatArray?, var pr: FloatArray?, var pb: FloatArray?,
        val width: Float,
    ) {
        /** Canlı çizimde diziler büyüyünce güncellenir; ölçülen uzunluklar önek kararlı olduğundan korunur. */
        fun update(xs: FloatArray, ys: FloatArray, count: Int, pp: FloatArray?, pt: FloatArray?, po: FloatArray?, pr: FloatArray?, pb: FloatArray?) {
            this.xs = xs; this.ys = ys; this.count = count; this.pp = pp; this.pt = pt; this.po = po; this.pr = pr; this.pb = pb
            if (measured > count) measured = 0
        }

        private var lengths = FloatArray(0)
        private var measured = 0

        fun pressure(i: Int): Float = pp?.getOrNull(i.coerceIn(0, max(0, count - 1))) ?: 1f
        fun tilt(i: Int): Float = pt?.getOrNull(i.coerceIn(0, max(0, count - 1))) ?: 0f
        fun azimuth(i: Int): Float = (po?.getOrNull(i.coerceIn(0, max(0, count - 1))) ?: 0f) * 2f * PI.toFloat()
        fun roll(i: Int): Float = pr?.getOrNull(i.coerceIn(0, max(0, count - 1))) ?: 0f
        fun barrel(i: Int): Float = pb?.getOrNull(i.coerceIn(0, max(0, count - 1))) ?: 0f

        /** Hız (0..1): ardışık iki ham nokta arasındaki uzaklığın iz genişliğine oranı. */
        fun speed(i: Int): Float {
            if (count < 2) return 0f
            val k = i.coerceIn(1, count - 1)
            val d = hypot(xs[k] - xs[k - 1], ys[k] - ys[k - 1])
            return (d / max(4f, width * 0.6f)).coerceIn(0f, 1f)
        }

        /** Çizginin başından ham nokta [i]'ye kadarki uzunluk (artımlı ölçülür). */
        fun along(i: Int): Float {
            if (count == 0) return 0f
            val k = i.coerceIn(0, count - 1)
            if (lengths.size < count) lengths = lengths.copyOf(max(count, lengths.size * 2))
            if (measured == 0) { lengths[0] = 0f; measured = 1 }
            while (measured <= k) {
                val m = measured
                lengths[m] = lengths[m - 1] + hypot(xs[m] - xs[m - 1], ys[m] - ys[m - 1])
                measured++
            }
            return lengths[k]
        }
    }

    companion object {
        /** Mesafeyle incelmenin tamamlandığı uzunluk (iz genişliğinin katı). */
        const val DISTANCE_SPAN = 40f
        /** Zamanla solmanın tamamlandığı nokta sayısı (~2 sn). */
        const val TIME_SPAN = 240f

        /** Ayar yoksa null (motorlar eskisi gibi çizer). */
        fun of(params: BrushParams?): PenModel? {
            val tip = params?.tip?.takeIf { it.minSize != null || it.maxSize != null }
            val pen = params?.pen?.orNull()
            return if (tip == null && pen == null) null else PenModel(tip, pen)
        }
    }
}
