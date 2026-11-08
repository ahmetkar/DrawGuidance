package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.ink.Box
import com.karapp.drawguidance.content.ink.Geo
import com.karapp.drawguidance.content.ink.Poly
import com.karapp.drawguidance.content.ink.ShapeFeatures
import com.karapp.drawguidance.content.vector.P
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * v21 şekil veritabanında bir kaydın aramaya hazır imzası (veritabanında saklanır, açılışta yeniden hesaplanmaz).
 *
 * - [strokeCount], [closedMask] (ilk 30 çizginin kapalı olup olmadığı), [aspect] (kutunun köşegen açısı: 0 ince dikey,
 *   0.5 kare, 1 yassı yatay) ve [corners] (keskin köşe sayısı): hızlı ön eleme ve yapı karşılaştırması.
 * - [descriptors]: yön ızgarası öznitelikleri (8×8 hücre × 4 doğrultu + uç noktası = 320 boyut, L2 normalize); katalog
 *   şablonlarında el çizimi bozulmalarıyla (eğim, döndürme, yatay ölçek) çoğaltılmış [variants] kopya art arda.
 * - [lsh]: yerellik duyarlı özet (SimHash, 16 bit): büyük dizinlerde aday kovası.
 * - [geometry]: birim kutuya (uzun kenar 1, merkez 0.5) getirilmiş çizgiler.
 */
class ShapeSignature(
    val strokeCount: Int,
    val closedMask: Int,
    val aspect: Float,
    val corners: Int,
    val descriptors: FloatArray,
    val lsh: Int,
    val geometry: List<List<P>>,
) {
    val variants: Int get() = descriptors.size / DIM

    /** 8 kovalı en-boy sınıfı (veritabanı dizini için). */
    val aspectBucket: Int get() = (aspect * 8f).toInt().coerceIn(0, 7)

    companion object {
        val DIM: Int = ShapeFeatures.DIM
    }
}

/** İmzaların hesaplanması ve sıkıştırılmış saklama biçimleri. */
object ShapeSignatures {

    private class Distort(val slantDeg: Float, val rotDeg: Float, val sx: Float)

    /** Katalog şablonlarının el çizimine benzetilmiş kopyaları (veri çoğaltma). */
    private val distortions = listOf(
        Distort(0f, 0f, 1f),
        Distort(10f, 0f, 1f), Distort(-10f, 0f, 1f),
        Distort(0f, 8f, 1f), Distort(0f, -8f, 1f),
        Distort(0f, 0f, 0.8f), Distort(0f, 0f, 1.25f),
    )

    val VARIANTS: Int get() = distortions.size

    /** Çizgileri birim kutuya getirir: uzun kenar 1, kutu merkezi (0.5, 0.5). */
    fun normalize(strokes: List<List<P>>): List<List<P>> {
        var a = Float.POSITIVE_INFINITY; var b = Float.POSITIVE_INFINITY; var c = Float.NEGATIVE_INFINITY; var d = Float.NEGATIVE_INFINITY
        for (s in strokes) for (p in s) { a = minOf(a, p.x); b = minOf(b, p.y); c = maxOf(c, p.x); d = maxOf(d, p.y) }
        if (!a.isFinite()) return strokes
        val size = max(max(c - a, d - b), 1e-4f)
        val cx = (a + c) / 2f; val cy = (b + d) / 2f
        return strokes.map { s -> s.map { P((it.x - cx) / size + 0.5f, (it.y - cy) / size + 0.5f) } }
    }

    /** Kutunun köşegen açısı (0 ince dikey, 0.5 kare, 1 yassı yatay). */
    fun aspectOf(strokes: List<List<P>>): Float = ShapeFeatures.aspect(polys(strokes))

    internal fun polys(strokes: List<List<P>>): List<Poly> = strokes.filter { it.isNotEmpty() }.map { s ->
        Poly(FloatArray(s.size) { s[it].x }, FloatArray(s.size) { s[it].y })
    }

    /** Grubun uzun kenarına göre bu orandan küçük parçalar nokta sayılır. */
    const val DOT_PART = 0.12f

    /**
     * Küçük parçalar (göz, burun deliği, çekirdek…) tek noktaya indirgenir: elle çizimde küçük daireler çoğu zaman nokta
     * ya da karalama olur; şablonla çizim aynı biçimde karşılaştırılır. Tek çizgide değişiklik yapılmaz.
     */
    internal fun collapseSmall(polys: List<Poly>): List<Poly> {
        if (polys.size < 2) return polys
        val b = Box.of(polys)
        val size = max(max(b.w, b.h), 1e-4f)
        return polys.map { p ->
            if (p.extent < DOT_PART * size && p.n > 1) Poly(floatArrayOf(p.cx), floatArrayOf(p.cy)) else p
        }
    }

    /** İmza: [augment] true ise (katalog) bozulmuş kopyalar da eklenir, kullanıcı örneklerinde yalnız kendisi. */
    fun of(strokes: List<List<P>>, augment: Boolean = true): ShapeSignature {
        val norm = normalize(strokes.filter { it.isNotEmpty() })
        val raw = polys(norm)
        val polys = collapseSmall(raw)
        val list = if (augment) distortions else distortions.take(1)
        val desc = FloatArray(list.size * ShapeSignature.DIM)
        list.forEachIndexed { i, d -> ShapeFeatures.extract(distort(polys, d)).copyInto(desc, i * ShapeSignature.DIM) }
        return ShapeSignature(
            strokeCount = polys.size,
            closedMask = closedMask(raw),
            aspect = ShapeFeatures.aspect(raw),
            corners = corners(raw),
            descriptors = desc,
            lsh = simHash(desc, 0),
            geometry = norm,
        )
    }

    private fun distort(polys: List<Poly>, d: Distort): List<Poly> {
        if (d.slantDeg == 0f && d.rotDeg == 0f && d.sx == 1f) return polys
        val b = Box.of(polys)
        val sh = tan(Math.toRadians(d.slantDeg.toDouble())).toFloat()
        val r = Math.toRadians(d.rotDeg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        return polys.map { p ->
            p.affine(d.sx, -sh, -d.sx * b.cx + sh * b.cy + b.cx, 0f, 1f, 0f)
                .affine(c, -s, b.cx - c * b.cx + s * b.cy, s, c, b.cy - s * b.cx - c * b.cy)
        }
    }

    /** Uçları birbirine yakın (kutusunun %15'i) ve yeterince uzun çizgi kapalı sayılır. */
    internal fun isClosed(p: Poly): Boolean {
        if (p.n < 4) return false
        val ext = max(p.extent, 1e-4f)
        return hypot(p.x[p.n - 1] - p.x[0], p.y[p.n - 1] - p.y[0]) <= 0.15f * ext && p.length > 2f * ext
    }

    internal fun closedMask(polys: List<Poly>): Int {
        var m = 0
        polys.take(30).forEachIndexed { i, p -> if (isClosed(p)) m = m or (1 shl i) }
        return m
    }

    /** Keskin (>55°) köşe sayısı (birim kutudaki çizgilerde, yerel en büyükler). */
    internal fun corners(polys: List<Poly>): Int {
        var total = 0
        for (p in polys) total += cornersOf(p)
        return total.coerceAtMost(63)
    }

    internal fun cornersOf(p: Poly): Int {
        if (p.length < 0.08f) return 0
        val r = Geo.resample(p, 0.02f)
        if (r.n < 5) return 0
        val k = 2
        val ang = FloatArray(r.n)
        for (i in k until r.n - k) {
            val ax = r.x[i] - r.x[i - k]; val ay = r.y[i] - r.y[i - k]
            val bx = r.x[i + k] - r.x[i]; val by = r.y[i + k] - r.y[i]
            val la = hypot(ax, ay); val lb = hypot(bx, by)
            if (la < 1e-6f || lb < 1e-6f) continue
            ang[i] = acos(((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f))
        }
        val th = Math.toRadians(55.0).toFloat()
        var n = 0
        var i = k
        while (i < r.n - k) {
            if (ang[i] > th) {
                // tepe noktasına kadar ilerle (aynı köşenin komşuları sayılmaz)
                var j = i
                while (j + 1 < r.n - k && ang[j + 1] > th) j++
                n++
                i = j + 2
            } else i++
        }
        return n
    }

    // --- Sıkıştırılmış saklama ---

    /**
     * Öznitelikler 8 bit (karekök sıkıştırmalı) saklanır: değerler 0..1 arasıdır ve çoğu küçüktür; q = round(√v · 255)
     * küçük değerlerde daha ince adım verir. 320 boyut × 7 kopya = 2240 bayt / şablon.
     */
    fun encodeDescriptors(desc: FloatArray): ByteArray =
        ByteArray(desc.size) { (sqrt(desc[it].coerceIn(0f, 1f)) * 255f).roundToInt().toByte() }

    fun decodeDescriptors(bytes: ByteArray): FloatArray =
        FloatArray(bytes.size) { val q = (bytes[it].toInt() and 0xFF) / 255f; q * q }

    /**
     * Çizgiler 16 bit tam sayı olarak saklanır: [çizgi sayısı][her çizginin nokta sayısı…][x, y…]; koordinat aralığı
     * −0.5..1.5 (adım ≈ 0.00003).
     */
    fun encodeGeometry(strokes: List<List<P>>): ByteArray {
        val total = strokes.sumOf { it.size }
        val buf = ByteBuffer.allocate(2 + strokes.size * 2 + total * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(strokes.size.toShort())
        strokes.forEach { buf.putShort(it.size.coerceAtMost(65535).toShort()) }
        fun enc(v: Float): Short = (((v.coerceIn(-0.5f, 1.5f) + 0.5f) / 2f) * 65535f - 32768f).roundToInt().coerceIn(-32768, 32767).toShort()
        strokes.forEach { s -> s.forEach { buf.putShort(enc(it.x)); buf.putShort(enc(it.y)) } }
        return buf.array()
    }

    fun decodeGeometry(bytes: ByteArray): List<List<P>> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val n = buf.short.toInt() and 0xFFFF
        val sizes = IntArray(n) { buf.short.toInt() and 0xFFFF }
        fun dec(s: Short): Float = ((s + 32768f) / 65535f) * 2f - 0.5f
        return List(n) { i -> List(sizes[i]) { P(dec(buf.short), dec(buf.short)) } }
    }

    // --- Yerellik duyarlı özet ---

    private const val LSH_BITS = 16
    private val poolDim = (ShapeFeatures.G / 2) * (ShapeFeatures.G / 2) * ShapeFeatures.CH

    /** Sabit tohumlu rastgele hiper düzlemler (4×4 havuzlanmış 80 boyutlu uzayda). */
    private val planes: Array<FloatArray> by lazy {
        val rnd = java.util.Random(2121)
        Array(LSH_BITS) { FloatArray(poolDim) { rnd.nextGaussian().toFloat() } }
    }

    /** Havuzlanmış özniteliklerin ortalamadan farkı düzlemlerin hangi yanında: 16 bitlik kova anahtarı. */
    internal fun simHash(desc: FloatArray, offset: Int): Int = simHashPooled(pool(desc, offset))

    internal fun simHashPooled(pooled: FloatArray): Int {
        val mean = 1f / sqrt(poolDim.toFloat())
        var key = 0
        for (b in 0 until LSH_BITS) {
            var s = 0f
            val pl = planes[b]
            for (k in pooled.indices) s += pl[k] * (pooled[k] - mean)
            if (s >= 0f) key = key or (1 shl b)
        }
        return key
    }

    /** 8×8 ızgarayı 4×4'e indirger (2×2 blok toplamı), L2 normalize: hızlı ön eleme için 80 boyut. */
    internal fun pool(f: FloatArray, offset: Int = 0): FloatArray {
        val g = ShapeFeatures.G; val h = g / 2
        val out = FloatArray(h * h * ShapeFeatures.CH)
        for (ch in 0 until ShapeFeatures.CH) for (y in 0 until g) for (x in 0 until g)
            out[ch * h * h + (y / 2) * h + x / 2] += f[offset + ch * g * g + y * g + x]
        var n = 0f
        for (v in out) n += v * v
        n = sqrt(n)
        if (n > 0f) for (i in out.indices) out[i] /= n
        return out
    }

    internal val POOLED: Int get() = poolDim

    /** Hamming uzaklığı [radius] içindeki kova anahtarları (çoklu yoklama). */
    internal fun probes(key: Int, radius: Int): IntArray {
        val out = ArrayList<Int>()
        out += key
        if (radius >= 1) for (i in 0 until LSH_BITS) out += key xor (1 shl i)
        if (radius >= 2) for (i in 0 until LSH_BITS) for (j in i + 1 until LSH_BITS) out += key xor (1 shl i) xor (1 shl j)
        return out.toIntArray()
    }

}
