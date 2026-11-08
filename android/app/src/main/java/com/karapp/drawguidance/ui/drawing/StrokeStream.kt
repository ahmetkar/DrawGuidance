package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import com.karapp.drawguidance.data.model.BrushType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Akışlı (streaming) fırça motoru.
 *
 * Canlı çizimde her karede tüm çizgiyi baştan çizmek, çizgi uzadıkça her kareyi yavaşlatır. Bu motor
 * her fırçayı "önek-kararlı" üretir: çizginin başı, sonradan gelen noktalarla değişmez. Böylece
 *  - kesinleşen parçalar bir kez çizilip ekrandaki karolara yazılır ([takeFinal]),
 *  - her karede yalnızca henüz kesinleşmemiş kısa kuyruk çizilir ([tail]).
 * Statik çizim (önbellek, PNG, küçük resim) aynı kodla tüm noktaları verip bitirerek yapılır; canlı ve
 * kaydedilmiş görünüm aynıdır. Rastgelelik çizgi uzunluğuna değil damga sırasına bağlıdır.
 */

/** Tohum + damga sırası + tuzdan türetilen deterministik rastgelelik (çizgi uzadıkça değişmez). */
internal object DabRandom {
    private fun mix(input: Long): Long {
        var z = input
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** [0, 1) aralığında sayı. */
    fun unit(seed: Int, index: Int, salt: Int): Float {
        val h = mix(seed.toLong() * -0x61c8864680b583ebL + index.toLong() * -0x40a7b892e31b1a47L + salt.toLong() * -0x6b2fb644ecceee15L)
        return (h ushr 40).toInt() / 16777216f
    }

    /** Standart normal dağılımlı, ±2.5 ile sınırlı sayı. */
    fun gaussian(seed: Int, index: Int, salt: Int): Float {
        val u1 = max(1e-7f, unit(seed, index, salt))
        val u2 = unit(seed, index, salt + 7919)
        return (sqrt(-2f * ln(u1)) * cos(2f * PI.toFloat() * u2)).coerceIn(-2.5f, 2.5f)
    }
}

/** Örnek (damga konumu) listesi: konum, yön, çizgi boyunca mesafe, kaynak ham nokta ve sıra numarası. */
internal class SampleBuffer(capacity: Int = 64) {
    var x = FloatArray(capacity); var y = FloatArray(capacity)
    var dx = FloatArray(capacity); var dy = FloatArray(capacity)
    var along = FloatArray(capacity); var raw = IntArray(capacity); var index = IntArray(capacity)
    var size = 0

    fun clear() { size = 0 }

    fun add(sx: Float, sy: Float, sdx: Float, sdy: Float, a: Float, r: Int, k: Int) {
        if (size == x.size) {
            val n = size * 2
            x = x.copyOf(n); y = y.copyOf(n); dx = dx.copyOf(n); dy = dy.copyOf(n)
            along = along.copyOf(n); raw = raw.copyOf(n); index = index.copyOf(n)
        }
        x[size] = sx; y[size] = sy; dx[size] = sdx; dy[size] = sdy; along[size] = a; raw[size] = r; index[size] = k
        size++
    }

    fun addFrom(other: SampleBuffer, i: Int) = add(other.x[i], other.y[i], other.dx[i], other.dy[i], other.along[i], other.raw[i], other.index[i])
}

/**
 * Ham noktalardan, karesel orta nokta eğrileriyle yumuşatılmış çizgi üzerinde eşit [step] aralıklı
 * örnekler üretir. Noktalar geldikçe yalnızca kesinleşen kısım örneklenir ([advance]); son ham noktaya
 * uzanan düz kuyruk geçicidir ([tail]) ve çizim bitince kesinleşir ([finish]).
 */
internal class CurveSampler(private val step: Float) {
    val samples = SampleBuffer()

    /** Yürüyüş durumu: son kesin köşe, oraya kadarki uzunluk, sıradaki örnek numarası, son yön. */
    private class Walk {
        var started = false
        var vx = 0f; var vy = 0f
        var acc = 0f
        var k = 0
        var lastDx = 1f; var lastDy = 0f
        fun copyFrom(o: Walk) { started = o.started; vx = o.vx; vy = o.vy; acc = o.acc; k = o.k; lastDx = o.lastDx; lastDy = o.lastDy }
    }

    private val walk = Walk()
    private val scratch = Walk()
    private var nextSegment = 1
    var finished = false
        private set

    /** Kesinleşmiş çizgi uzunluğu. */
    val finalLength: Float get() = walk.acc

    private fun segment(w: Walk, ax: Float, ay: Float, bx: Float, by: Float, raw: Int, out: SampleBuffer) {
        val segLen = hypot(bx - ax, by - ay)
        val end = w.acc + segLen
        while (true) {
            val target = w.k * step
            if (end < target) break
            val u = if (segLen == 0f) 0f else ((target - w.acc) / segLen).coerceIn(0f, 1f)
            val sx = ax + (bx - ax) * u
            val sy = ay + (by - ay) * u
            if (segLen > 0f) { w.lastDx = (bx - ax) / segLen; w.lastDy = (by - ay) / segLen }
            else if (w.k == 0) { w.lastDx = 1f; w.lastDy = 0f }
            out.add(sx, sy, w.lastDx, w.lastDy, target, raw, w.k)
            w.k++
        }
        w.acc = end
        w.vx = bx; w.vy = by
    }

    /** Yeni kesinleşen eğri parçalarını örnekler (en az 3 nokta gerekir; son nokta hep geçicidir). */
    fun advance(px: FloatArray, py: FloatArray, count: Int) {
        if (finished || count < 3) return
        val w = walk
        if (!w.started) { w.vx = px[0]; w.vy = py[0]; w.started = true }
        while (nextSegment <= count - 1) {
            val i = nextSegment
            val sx = w.vx; val sy = w.vy
            val cx = px[i - 1]; val cy = py[i - 1]
            val ex = (px[i - 1] + px[i]) / 2f; val ey = (py[i - 1] + py[i]) / 2f
            val len = hypot(cx - sx, cy - sy) + hypot(ex - cx, ey - cy)
            val n = max(1, ceil(len / max(0.5f, step * 0.5f)).toInt()).coerceAtMost(64)
            for (q in 1..n) {
                val u = q / n.toFloat()
                val a = (1 - u) * (1 - u); val b = 2 * (1 - u) * u; val c = u * u
                segment(w, w.vx, w.vy, a * sx + b * cx + c * ex, a * sy + b * cy + c * ey, i, samples)
            }
            nextSegment++
        }
    }

    /** Geçici kuyruk örnekleri (kesin örneklere dokunmaz); sıra numaraları kesin örneklerden devam eder. */
    fun tail(px: FloatArray, py: FloatArray, count: Int, out: SampleBuffer) {
        if (finished || count == 0) return
        val w = scratch
        when {
            count == 1 -> out.add(px[0], py[0], 1f, 0f, 0f, 0, 0)
            count == 2 -> {
                w.started = true; w.vx = px[0]; w.vy = py[0]; w.acc = 0f; w.k = 0; w.lastDx = 1f; w.lastDy = 0f
                segment(w, px[0], py[0], px[1], py[1], 1, out)
            }
            else -> {
                w.copyFrom(walk)
                segment(w, w.vx, w.vy, px[count - 1], py[count - 1], count - 1, out)
            }
        }
    }

    /** Kuyruğu kesinleştirir; toplam uzunluğa göre son örneği tamamlar. */
    fun finish(px: FloatArray, py: FloatArray, count: Int) {
        if (finished) return
        if (count >= 3) advance(px, py, count)
        finished = true
        if (count == 0) return
        val w = walk
        when {
            count == 1 -> { samples.add(px[0], py[0], 1f, 0f, 0f, 0, 0); return }
            count == 2 -> {
                w.started = true; w.vx = px[0]; w.vy = py[0]
                segment(w, px[0], py[0], px[1], py[1], 1, samples)
            }
            else -> segment(w, w.vx, w.vy, px[count - 1], py[count - 1], count - 1, samples)
        }
        val total = w.acc
        if (total <= 0f) {
            samples.size = 0
            samples.add(px[0], py[0], 1f, 0f, 0f, 0, 0)
            return
        }
        val n = max(1, (total / step).toInt() + 1)
        if (samples.size > n) samples.size = n
        while (samples.size < n) {
            val k = samples.size
            samples.add(px[count - 1], py[count - 1], w.lastDx, w.lastDy, total, count - 1, k)
        }
    }
}

/**
 * Bir kez hesaplanıp birden çok kez (her ekran karosuna) çizilebilen çizim parçası. v20: [shadowOps] kağıda düşen
 * gölgedir (3B fırça); opak siyah çizilir, ayrı bir katmanda [StrokeStream.shadowAlpha] ile ve izin altında birleştirilir
 * (parçalar üst üste bindiğinde gölge koyulaşmaz).
 */
internal class StrokeChunk(
    val bounds: RectF,
    private val ops: List<(Canvas) -> Unit>,
    private val shadowOps: List<(Canvas) -> Unit> = emptyList(),
    /** Gölge çiziminin tamamı: canlı katmandaki gölge karoları temizlenip bununla yeniden çizilir (çizim bitince). */
    val replaceShadow: Boolean = false,
) {
    val hasShadow: Boolean get() = shadowOps.isNotEmpty()
    fun draw(canvas: Canvas) { for (op in ops) op(canvas) }
    fun drawShadow(canvas: Canvas) { for (op in shadowOps) op(canvas) }
}

/**
 * Aynı boyuttaki noktaları tek çağrıda çizmek için boyut kovaları. v18: kalem basıncıyla küçülen damgalar için
 * kovalar daha küçük boyutlara da iner; [alphaLevels] > 1 ise damgalar koyuluk düzeylerine de ayrılır.
 */
internal class DabBuckets(val base: Float, private val alphaLevels: Int = 1) {
    private val sizes = FloatArray(BUCKETS) { base * (MIN_F + (MAX_F - MIN_F) * it / (BUCKETS - 1)) }
    private val points = Array(BUCKETS * alphaLevels) { FloatArrayBuilder() }
    var isEmpty = true
        private set

    fun add(x: Float, y: Float, diameter: Float, alpha: Float = 1f) {
        val f = (diameter / base - MIN_F) / (MAX_F - MIN_F)
        val i = (f * (BUCKETS - 1)).roundToInt().coerceIn(0, BUCKETS - 1)
        val a = if (alphaLevels == 1) 0 else (alpha.coerceIn(0f, 1f) * alphaLevels - 0.5f).roundToInt().coerceIn(0, alphaLevels - 1)
        points[a * BUCKETS + i].add(x, y)
        isEmpty = false
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val baseAlpha = paint.alpha
        for (a in 0 until alphaLevels) {
            if (alphaLevels > 1) paint.alpha = (baseAlpha * (a + 1f) / alphaLevels).roundToInt().coerceIn(1, 255)
            for (i in 0 until BUCKETS) {
                val bucket = points[a * BUCKETS + i]
                if (bucket.size == 0) continue
                paint.strokeWidth = max(0.6f, sizes[i])
                canvas.drawPoints(bucket.data, 0, bucket.size, paint)
            }
        }
        paint.alpha = baseAlpha
    }

    private class FloatArrayBuilder {
        var data = FloatArray(32)
        var size = 0
        fun add(x: Float, y: Float) {
            if (size + 2 > data.size) data = data.copyOf(data.size * 2)
            data[size++] = x; data[size++] = y
        }
    }

    companion object {
        const val BUCKETS = 9
        private const val MIN_F = 0.15f
        private const val MAX_F = 1.25f
    }
}

/** Sınır kutusu biriktirici. */
internal class BoundsBuilder {
    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
    val isEmpty get() = l > r
    fun add(x: Float, y: Float) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
    fun toRect(pad: Float) = RectF(l - pad, t - pad, r + pad, b + pad)
}

/**
 * Tek bir çizginin akışlı üreticisi. Canlı çizimde noktalar [add] ile verilir; kesinleşen parçalar
 * [takeFinal], geçici kuyruk [tail] ile alınır. Parçalar "ham katman" içindir: ekrana/önbelleğe
 * [compositeAlpha] ve [blend] ile birleştirilir (silgide ise kapsama maskesi olarak kullanılır).
 */
internal abstract class StrokeStream(
    val type: BrushType,
    protected val color: Int,
    sizePx: Float,
    val opacity: Float,
    protected val seed: Int,
    flow: Float,
    protected val strength: Float,
    /** Silgi: silmek yerine kapsamayı çiz (canlı çizimde maske olarak kullanılır). */
    protected val eraserMask: Boolean,
    /** v20 fırça görünüm ayarları (aralık, eğim, 3B); null: varsayılanlar. */
    val params: com.karapp.drawguidance.data.model.BrushParams? = null,
) {
    val style: BrushStyle = type.style()
    val width: Float = type.inkWidth(sizePx, strength)
    protected val flowA: Float = flow.coerceIn(0.02f, 1f)
    /**
     * v21 tek yol çizen fırçalarda akışkanlığın koyuluğa etkisi (az boyada iz açılır ama kaybolmaz; asıl etki
     * [withFlow] dokusudur).
     */
    protected val flowInk: Float = if (flowA >= FULL_FLOW) 1f else 0.35f + 0.65f * flowA

    /** v21 az boya: iz kağıt dişine takılıp kırık, kuru görünür (ikili doku: parçaların birleşiminde koyulaşmaz). */
    protected val flowGrain: android.graphics.BitmapShader? by lazy {
        if (isEraser || flowA >= FULL_FLOW) null else android.graphics.BitmapShader(
            BrushTextures.flowGrain(flowA), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT,
        ).apply {
            val sc = (width / 24f).coerceIn(0.5f, 1.5f)
            setLocalMatrix(Matrix().apply { setScale(sc, sc) })
        }
    }

    protected fun Paint.withFlow(): Paint {
        // Ölçekli doku süzülmeden (en yakın piksel) örneklenir: ikili kalır, üst üste binen parçalarda koyulaşmaz
        flowGrain?.let { g -> shader = g; colorFilter = PorterDuffColorFilter(opaqueColor, PorterDuff.Mode.SRC_IN); isFilterBitmap = false }
        return this
    }
    protected val strokeAlpha: Float = (opacity * style.alphaFactor * pressureAlpha(strength)).coerceIn(0f, 1f)
    val isEraser: Boolean = type == BrushType.ERASER
    protected val opaqueColor: Int = color or (0xFF shl 24)

    /** Ham katmanın birleştirme opaklığı ve karışımı (silgide kullanılmaz). */
    abstract val compositeAlpha: Float
    /** Yalnızca kapsama (maske) için: özel karışım modları (ör. fosforlu çarpma) kullanılmaz. */
    var coverageOnly: Boolean = false
    val blend: BlendMode get() = if (isEraser || coverageOnly) BlendMode.SrcOver else style.blendMode

    protected var px = FloatArray(128)
    protected var py = FloatArray(128)
    var count = 0
        protected set
    var ended = false
        protected set

    /** Parça sınırlarına eklenecek pay (doku, sapma, kırıntılar; v20: eğimle genişleme). */
    protected open val pad: Float get() = width * max(style.reach * 1.6f, 0.62f * dynamics.maxWidth) + 4f

    /** v20 kağıda düşen gölgenin opaklığı (0: gölge yok); v21 3B derinlikte katı gövde tam opak. */
    open val shadowAlpha: Float get() = if (hasExtrusion) 1f else 0f

    /**
     * v20 parçalar çizildikleri yerdeki önceki parçaları siler (3B fırça birleşim yerleri): canlı katman, kuyruğu ve
     * karoları kendi katmanında birleştirmelidir (alttaki çizimler silinmesin).
     */
    open val needsOwnLayer: Boolean get() = false

    /**
     * v18 kalem dinamikleri: ham noktaların basıncı ([pp], 0..1) ve eğimi ([pt], 0 = dik, 1 = yatık). Kalemle
     * çizilmemişse null (tüm çizgide basınç 1, eğim 0): motorlar eskisi gibi sabit kalınlıkla çizer.
     */
    protected var pp: FloatArray? = null
    protected var pt: FloatArray? = null
    /** v20 kalem yönü (0..1 → 0..360°; 0: bilinmiyor). */
    protected var po: FloatArray? = null
    protected val dynamics = BrushDynamics.of(type).with(params)
    val hasDynamics: Boolean get() = pp != null || pt != null

    fun add(x: Float, y: Float, pressure: Float = 1f, tilt: Float = 0f, orient: Float = 0f) {
        if (ended) return
        if (count == px.size) {
            px = px.copyOf(count * 2); py = py.copyOf(count * 2)
            pp?.let { pp = it.copyOf(count * 2) }
            pt?.let { pt = it.copyOf(count * 2) }
            po?.let { po = it.copyOf(count * 2) }
        }
        if (pressure != 1f && pp == null) pp = FloatArray(px.size) { 1f }
        if (tilt != 0f && pt == null) pt = FloatArray(px.size)
        if (orient != 0f && po == null) po = FloatArray(px.size)
        px[count] = x; py[count] = y
        pp?.set(count, pressure)
        pt?.set(count, tilt)
        po?.set(count, orient)
        count++
    }

    fun setPoints(xs: FloatArray, ys: FloatArray, n: Int, pressures: FloatArray? = null, tilts: FloatArray? = null, orients: FloatArray? = null) {
        px = xs; py = ys; count = n
        pp = pressures; pt = tilts; po = orients
    }

    fun pointsPressure(): FloatArray? = pp
    fun pointsTilt(): FloatArray? = pt
    fun pointsOrient(): FloatArray? = po

    /** v20 ham nokta [raw]'daki kalem yönü (radyan) ya da bilinmiyorsa null. */
    protected fun orientAt(raw: Int): Float? {
        val a = po ?: return null
        if (count == 0) return null
        val v = a[raw.coerceIn(0, count - 1)]
        return if (v == 0f) null else v * 2f * PI.toFloat()
    }

    /** Ham nokta [raw]'a (ve önceki noktaya) göre basınç; örnekler iki ham nokta arasındadır. */
    private fun dynAt(a: FloatArray?, raw: Int, default: Float): Float {
        a ?: return default
        if (count == 0) return default
        val i = raw.coerceIn(0, count - 1)
        return if (i == 0) a[0] else (a[i - 1] + a[i]) * 0.5f
    }

    /** Ham nokta [raw]'daki kalınlık çarpanı (basınç ve eğim; en çok ~1, eğimle genişleyen fırçalarda daha fazla). */
    protected fun wf(raw: Int): Float = if (!hasDynamics) 1f else dynamics.width(dynAt(pp, raw, 1f), dynAt(pt, raw, 0f))

    /** Ham nokta [raw]'daki koyuluk çarpanı (karakalem, pastel, yumuşak fırçalar). */
    protected fun af(raw: Int): Float = if (!hasDynamics) 1f else dynamics.alpha(dynAt(pp, raw, 1f), dynAt(pt, raw, 0f))

    fun end() { ended = true }

    fun pointsX(): FloatArray = px
    fun pointsY(): FloatArray = py

    /** Kesinleşen ve henüz verilmemiş kısım (motorun kendi çizimi). */
    protected abstract fun takeFinalRaw(minBatch: Int): StrokeChunk?

    /** Henüz kesinleşmemiş kısmın geçici çizimi (motorun kendi çizimi). */
    protected abstract fun tailRaw(): StrokeChunk?

    /** Kesinleşen ve henüz verilmemiş kısım; [minBatch] kadar birikmeden (bitmediyse) null. v21: 3B derinlik eklenir. */
    fun takeFinal(minBatch: Int = 1): StrokeChunk? = takeFinalRaw(minBatch)?.let(::extruded)

    /** Henüz kesinleşmemiş kısmın geçici çizimi (her karede yeniden hesaplanır). */
    fun tail(): StrokeChunk? = tailRaw()?.let(::extruded)

    /**
     * v21 3B çizim modu: iz, çizildiği düzlemin normali yönünde kabarır. Katı gövde, izin kendisinin kabarma yönünde
     * sık aralıklı kopyalarıdır; gölge katmanında toplanıp izin renginin koyu tonunda ([shadowTint]) izin altına konur.
     * Böylece her fırça (doku, desen, iz…) kalın, katı bir cisim gibi görünür. 3B fırça (tüp) kendi gölgesini kullanır.
     */
    private val extrude: com.karapp.drawguidance.data.model.Depth3D? =
        params?.depth3d?.takeIf { it.depth > 0.01f && !isEraser && style.engine != BrushEngine.TUBE }
    protected val extrudeLength: Float = Iso3D.extrudeLength(extrude, width)
    // v22: 3B sahnede kabarma yönü kameraya göre saklanır (kısalır, döner)
    private val extrudeDir = extrude?.let { Iso3D.extrudeVector(it) }
    protected val hasExtrusion: Boolean get() = extrudeLength > 0.5f

    private fun extruded(chunk: StrokeChunk): StrokeChunk {
        val dir = extrudeDir
        if (!hasExtrusion || dir == null) return chunk
        val len = extrudeLength
        val reach = len * max(1f, kotlin.math.hypot(dir.x, dir.y))
        val steps = ceil(reach / max(0.8f, min(width * 0.25f, 3f))).toInt().coerceIn(2, 48)
        val b = RectF(chunk.bounds)
        b.union(chunk.bounds.left + dir.x * len, chunk.bounds.top + dir.y * len)
        b.union(chunk.bounds.right + dir.x * len, chunk.bounds.bottom + dir.y * len)
        val walls: (Canvas) -> Unit = { c ->
            for (i in steps downTo 1) {
                val t = len * i / steps
                val save = c.save()
                c.translate(dir.x * t, dir.y * t)
                chunk.draw(c)
                c.restoreToCount(save)
            }
        }
        return StrokeChunk(b, listOf { c -> chunk.draw(c) }, listOf(walls))
    }

    /** v21 gölge katmanının rengi (null: çizildiği gibi; 3B derinlikte izin koyu tonu). */
    open val shadowTint: Int? get() = if (hasExtrusion) darker(opaqueColor, 0.58f) else null

    private fun darker(c: Int, f: Float): Int =
        (0xFF shl 24) or (((c shr 16 and 0xFF) * f).roundToInt() shl 16) or (((c shr 8 and 0xFF) * f).roundToInt() shl 8) or ((c and 0xFF) * f).roundToInt()

    /** Gölge katmanını birleştiren boya (saydamlık ve varsa renk). */
    fun shadowPaint(): Paint = Paint().apply {
        alpha = (shadowAlpha.coerceIn(0f, 1f) * 255).roundToInt()
        shadowTint?.let { colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
    }

    /** Tüm çizgiyi doğrudan (katman gerekiyorsa açarak) çizer. Noktalar [setPoints] ile verilmiş olmalı. */
    open fun drawStatic(canvas: Canvas) {
        end()
        val chunk = takeFinal(0) ?: return
        val shadow = chunk.hasShadow && shadowAlpha > 0f
        val needsLayer = !isEraser && (compositeAlpha < 0.999f || blend != BlendMode.SrcOver || shadow)
        if (needsLayer) {
            val restore = canvas.saveLayer(chunk.bounds, layerPaint(compositeAlpha, blend))
            if (shadow) {
                // Gölge ayrı katmanda opak çizilip saydamlıkla birleştirilir; iz üstüne çizilir
                val s = canvas.saveLayer(chunk.bounds, shadowPaint())
                chunk.drawShadow(canvas)
                canvas.restoreToCount(s)
            }
            chunk.draw(canvas)
            canvas.restoreToCount(restore)
        } else {
            chunk.draw(canvas)
        }
    }

    companion object {
        /** Bu akışkanlıktan sonrası tam boya sayılır (doku yok). */
        const val FULL_FLOW = 0.97f

        fun create(
            type: BrushType, color: Int, sizePx: Float, opacity: Float, seed: Int, flow: Float, strength: Float,
            eraserMask: Boolean = false,
            params: com.karapp.drawguidance.data.model.BrushParams? = null,
        ): StrokeStream {
            val style = type.style()
            val fullEraser = type == BrushType.ERASER && flow >= 0.99f && opacity >= 0.99f
            return when {
                type == BrushType.ERASER && !fullEraser -> SampleDabStream(type, color, sizePx, opacity, seed, flow, strength, eraserMask, params)
                style.engine == BrushEngine.PATH -> PathStream(type, color, sizePx, opacity, seed, flow, strength, eraserMask, params)
                style.engine == BrushEngine.TAPERED_PATH -> TaperedStream(type, color, sizePx, opacity, seed, flow, strength, params)
                style.engine == BrushEngine.RIBBON -> RibbonStream(type, color, sizePx, opacity, seed, flow, strength, params)
                style.engine == BrushEngine.BRISTLE -> BristleStream(type, color, sizePx, opacity, seed, flow, strength, params)
                style.engine == BrushEngine.PATTERN && style.pattern == PatternKind.ZIGZAG -> ZigzagStream(type, color, sizePx, opacity, seed, flow, strength, params)
                style.engine == BrushEngine.TUBE -> TubeStream(type, color, sizePx, opacity, seed, flow, strength, params)
                style.engine == BrushEngine.PAINT -> TubeStream(type, color, sizePx, opacity, seed, flow, strength, params, paintMode = true)
                else -> SampleDabStream(type, color, sizePx, opacity, seed, flow, strength, eraserMask, params)
            }
        }

        fun layerPaint(alpha: Float, blend: BlendMode) = Paint().apply {
            this.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
            applyBlend(this, blend)
        }

        fun applyBlend(paint: Paint, blend: BlendMode) {
            when (blend) {
                BlendMode.Multiply -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) paint.blendMode = android.graphics.BlendMode.MULTIPLY
                else paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                BlendMode.Clear -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                else -> Unit
            }
        }

        /**
         * Sık aralıklı damgalarda akışkanlığın tek geçişte gerçekten o oranda boya bırakması için damga başına
         * alfa: bir noktanın üzerinden yaklaşık 1/spacing damga geçer, 1-(1-a)^(1/spacing) = flow.
         */
        fun dabFlow(flow: Float, spacing: Float): Float =
            if (flow >= 0.999f) 1f else (1f - (1f - flow).toDouble().pow(spacing.coerceIn(0.02f, 1f).toDouble()).toFloat()).coerceIn(0.012f, 1f)

        fun texturedPaint(color: Int, flow: Float, texture: Bitmap?): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.color = color
            alpha = (flow * 255).roundToInt().coerceIn(1, 255)
            if (texture != null) {
                shader = BrushTextures.shader(texture)
                colorFilter = PorterDuffColorFilter(color or (0xFF shl 24), PorterDuff.Mode.SRC_IN)
            }
        }

        /** Uç şekli (yolun iki ucu için ayrı ayrı; parçalar düz uçla çizilir). */
        fun drawCap(canvas: Canvas, x: Float, y: Float, dirX: Float, dirY: Float, cap: StrokeCap, width: Float, paint: Paint) {
            val half = width / 2f
            when (cap) {
                StrokeCap.Round -> canvas.drawCircle(x, y, half, paint)
                StrokeCap.Square -> {
                    val l = hypot(dirX, dirY)
                    val ux = if (l > 0f) dirX / l else 1f
                    val uy = if (l > 0f) dirY / l else 0f
                    val path = Path()
                    path.moveTo(x - uy * half, y + ux * half)
                    path.lineTo(x - uy * half + ux * half, y + ux * half + uy * half)
                    path.lineTo(x + uy * half + ux * half, y - ux * half + uy * half)
                    path.lineTo(x + uy * half, y - ux * half)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                else -> Unit
            }
        }

        fun nativeCap(cap: StrokeCap) = when (cap) {
            StrokeCap.Square -> Paint.Cap.SQUARE
            StrokeCap.Butt -> Paint.Cap.BUTT
            else -> Paint.Cap.ROUND
        }

        fun nativeJoin(join: StrokeJoin) = when (join) {
            StrokeJoin.Bevel -> Paint.Join.BEVEL
            StrokeJoin.Miter -> Paint.Join.MITER
            else -> Paint.Join.ROUND
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Örnek tabanlı (damga) motorlar
// ---------------------------------------------------------------------------------------------

/** Örnek başına damga üreten motorlar: sert/dokulu damga, yumuşak damga, şekil, nokta/tarama, serpinti, yarım ton, yarı silgi. */
internal class SampleDabStream(
    type: BrushType, color: Int, sizePx: Float, opacity: Float, seed: Int, flow: Float, strength: Float, eraserMask: Boolean,
    params: com.karapp.drawguidance.data.model.BrushParams? = null,
) : StrokeStream(type, color, sizePx, opacity, seed, flow, strength, eraserMask, params) {

    private val engine = if (isEraser) BrushEngine.DABS else style.engine
    /** v20 kullanıcının seçtiği damga aralığı (genişliğin katı). */
    private val spacingOverride: Float? = if (isEraser) null else params?.spacing?.coerceIn(MIN_SPACING, MAX_SPACING)
    /** Akışkanlığın damga başına hesabında kullanılan aralık. */
    private val flowSpacing: Float = spacingOverride ?: style.spacing
    private val step: Float = when {
        isEraser -> max(1f, width * 0.25f)
        spacingOverride != null -> max(0.5f, width * spacingOverride)
        engine == BrushEngine.DABS -> max(0.6f, width * style.spacing)
        engine == BrushEngine.SOFT_DABS -> max(0.8f, width * style.spacing)
        engine == BrushEngine.STAMP_SHAPE -> max(1f, width * style.spacing)
        engine == BrushEngine.SCATTER -> max(1f, width * 0.45f)
        engine == BrushEngine.HALFTONE -> max(0.6f, width * style.spacing)
        engine == BrushEngine.SURFACE || engine == BrushEngine.TRAIL -> max(0.6f, width * style.spacing)
        style.pattern == PatternKind.DOTS -> max(2f, width * 1.6f)
        style.pattern == PatternKind.DOT_TRAIL -> max(3f, width * 2.2f)
        else -> max(0.6f, width * 0.15f) // tarama
    }
    private val sampler = CurveSampler(step)
    private var drawn = 0
    private val tailBuffer = SampleBuffer()
    private val chunkBuffer = SampleBuffer()

    override val compositeAlpha: Float get() = when {
        isEraser -> 1f
        // v21 doku/desen: damgalar opak (karo üst üste binince halkalanmasın); akışkanlık koyuluk ve kuru dokuyla
        engine == BrushEngine.SURFACE -> strokeAlpha * flowInk
        else -> strokeAlpha
    }

    private companion object {
        /** Basınçla koyuluğu değişen damgaların koyuluk düzeyi sayısı. */
        const val ALPHA_LEVELS = 6
        const val MIN_SPACING = 0.02f
        const val MAX_SPACING = 3f
    }

    // Çizgi boyunca sabit boyalar ve dokular
    private val textureLevel = (strength.coerceIn(0f, 1f) * 4).roundToInt()
    private val halftoneLevel = (strength.coerceIn(0f, 1f) * 7).roundToInt()

    override fun takeFinalRaw(minBatch: Int): StrokeChunk? {
        if (ended) sampler.finish(px, py, count) else sampler.advance(px, py, count)
        val s = sampler.samples
        val available = s.size - drawn
        if (available <= 0 || (!ended && available < minBatch)) return null
        chunkBuffer.clear()
        for (i in drawn until s.size) chunkBuffer.addFrom(s, i)
        drawn = s.size
        return build(chunkBuffer)
    }

    override fun tailRaw(): StrokeChunk? {
        if (ended) return null
        sampler.advance(px, py, count)
        tailBuffer.clear()
        val s = sampler.samples
        for (i in drawn until s.size) tailBuffer.addFrom(s, i)
        sampler.tail(px, py, count, tailBuffer)
        return if (tailBuffer.size == 0) null else build(tailBuffer)
    }

    private fun build(s: SampleBuffer): StrokeChunk {
        val bounds = BoundsBuilder()
        for (i in 0 until s.size) bounds.add(s.x[i], s.y[i])
        val ops = ArrayList<(Canvas) -> Unit>(4)
        if (isEraser) buildEraser(s, ops) else when (engine) {
            BrushEngine.DABS -> buildDabs(s, ops)
            BrushEngine.SOFT_DABS -> buildSoft(s, ops)
            BrushEngine.STAMP_SHAPE -> buildShapes(s, ops)
            BrushEngine.SCATTER -> buildScatter(s, ops)
            BrushEngine.HALFTONE -> buildHalftone(s, ops)
            BrushEngine.PATTERN -> buildPattern(s, ops)
            BrushEngine.SURFACE -> buildSurface(s, ops)
            BrushEngine.TRAIL -> buildTrail(s, ops)
            else -> Unit
        }
        return StrokeChunk(bounds.toRect(pad), ops)
    }

    private fun buildEraser(s: SampleBuffer, ops: MutableList<(Canvas) -> Unit>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
            this.color = 0xFF000000.toInt()
            alpha = (flowA * opacity * 255).roundToInt().coerceIn(1, 255)
            if (!eraserMask) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        val buckets = DabBuckets(width)
        for (i in 0 until s.size) buckets.add(s.x[i], s.y[i], width * wf(s.raw[i]))
        ops += { c -> buckets.draw(c, paint) }
    }

    private fun buildDabs(s: SampleBuffer, ops: MutableList<(Canvas) -> Unit>) {
        val texture = style.texture?.let { BrushTextures.grain(it, textureLevel) }
        if (type == BrushType.CHARCOAL) {
            // Kömürün altında çok hafif, dokusuz bir is tabakası (iz genişliğinde)
            val smudge = DabBuckets(width)
            for (i in 0 until s.size) if (s.index[i] % 3 == 0) smudge.add(s.x[i], s.y[i], width * 0.9f * wf(s.raw[i]))
            if (!smudge.isEmpty) {
                val paint = texturedPaint(color, flowA * 0.08f, null)
                ops += { c -> smudge.draw(c, paint) }
            }
        }
        // v18: kalem basıncıyla damga küçülür; karakalem/pastel/yumuşak fırçalarda koyuluk da değişir
        val main = DabBuckets(width, if (hasDynamics) ALPHA_LEVELS else 1)
        for (i in 0 until s.size) {
            val k = s.index[i]
            val w = width * wf(s.raw[i])
            val jx = if (style.jitter > 0) DabRandom.gaussian(seed, k, 1) * style.jitter * w else 0f
            val jy = if (style.jitter > 0) DabRandom.gaussian(seed, k, 2) * style.jitter * w else 0f
            val d = w * (1f - style.sizeJitter * DabRandom.unit(seed, k, 3))
            main.add(s.x[i] + jx, s.y[i] + jy, d, af(s.raw[i]))
        }
        val mainPaint = texturedPaint(color, dabFlow(flowA, flowSpacing), texture)
        ops += { c -> main.draw(c, mainPaint) }

        if (type == BrushType.PASTEL) {
            // Sert pastelin kenarından kopan kırıntılar (damga başına olasılıkla)
            val crumbs = DabBuckets(width * 0.14f)
            val chance = (2.5f * step / max(1f, width)).coerceAtMost(1f)
            for (i in 0 until s.size) {
                val k = s.index[i]
                if (DabRandom.unit(seed, k, 4) >= chance) continue
                val side = if (DabRandom.unit(seed, k, 5) < 0.5f) 1f else -1f
                val w = width * wf(s.raw[i])
                val dist = w * (0.45f + 0.3f * DabRandom.unit(seed, k, 6)) * side
                crumbs.add(s.x[i] - s.dy[i] * dist, s.y[i] + s.dx[i] * dist, w * (0.05f + 0.1f * DabRandom.unit(seed, k, 7)))
            }
            if (!crumbs.isEmpty) {
                val paint = texturedPaint(color, flowA * 0.85f, null)
                ops += { c -> crumbs.draw(c, paint) }
            }
        }
    }

    private fun buildSoft(s: SampleBuffer, ops: MutableList<(Canvas) -> Unit>) {
        val spray = type == BrushType.AIRBRUSH
        val stamp = BrushTextures.softStamp(style.hardness)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(opaqueColor, PorterDuff.Mode.SRC_IN)
            // Yumuşak damgalar sık üst üste biner: v21 damga başına boya, tek geçişte merkezde yaklaşık akışkanlık
            // oranında boya kalacak şekilde (önceden yüksek akışkanlıkta fark görünmüyordu)
            alpha = (dabFlow(flowA * (if (spray) 0.85f else 0.95f), flowSpacing) * 255).roundToInt().coerceIn(1, 255)
        }
        val r = width / 2f
        val rects = FloatArray(s.size * 4)
        // v18: kalem basıncı damga boyunu ve koyuluğunu değiştirir
        val alphas = if (hasDynamics) FloatArray(s.size) { af(s.raw[it]) } else null
        for (i in 0 until s.size) {
            val ri = r * wf(s.raw[i])
            rects[i * 4] = s.x[i] - ri; rects[i * 4 + 1] = s.y[i] - ri; rects[i * 4 + 2] = s.x[i] + ri; rects[i * 4 + 3] = s.y[i] + ri
        }
        val n = s.size
        ops += { c ->
            val rect = RectF()
            val baseAlpha = paint.alpha
            for (i in 0 until n) {
                rect.set(rects[i * 4], rects[i * 4 + 1], rects[i * 4 + 2], rects[i * 4 + 3])
                if (alphas != null) paint.alpha = (baseAlpha * alphas[i]).roundToInt().coerceIn(1, 255)
                c.drawBitmap(stamp, null, rect, paint)
            }
            paint.alpha = baseAlpha
        }
        if (spray) {
            // Airbrush: her damganın çevresinde yoğun, ince püskürtme tanecikleri
            val speckles = DabBuckets(max(1f, width * 0.03f))
            for (i in 0 until s.size) {
                val k = s.index[i]
                for (j in 0 until 6) {
                    val g = DabRandom.gaussian(seed, k, 20 + j * 3) * r * 0.45f
                    val h = DabRandom.gaussian(seed, k, 21 + j * 3) * r * 0.45f
                    speckles.add(s.x[i] + g, s.y[i] + h, max(0.8f, width * (0.02f + 0.03f * DabRandom.unit(seed, k, 22 + j * 3))))
                }
            }
            val speckPaint = texturedPaint(color, flowA * 0.5f, null)
            ops += { c -> speckles.draw(c, speckPaint) }
        }
    }

    private fun buildShapes(s: SampleBuffer, ops: MutableList<(Canvas) -> Unit>) {
        val shape = style.shape ?: return
        val unit = BrushTextures.shapePath(shape)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@SampleDabStream.color; alpha = (flowA * 255).roundToInt().coerceIn(1, 255); this.style = Paint.Style.FILL
        }
        val matrix = Matrix()
        val one = Path()
        for (i in 0 until s.size) {
            val k = s.index[i]
            val w = width * wf(s.raw[i])
            val size = w * (1f - style.sizeJitter * DabRandom.unit(seed, k, 3))
            val angle = when (shape) {
                StampShape.STAR -> DabRandom.unit(seed, k, 8) * 72f
                StampShape.HEART -> DabRandom.gaussian(seed, k, 8) * 10f
                StampShape.LEAF -> Math.toDegrees(atan2(s.dy[i], s.dx[i]).toDouble()).toFloat() + DabRandom.gaussian(seed, k, 8) * 25f
            }
            val jx = DabRandom.gaussian(seed, k, 1) * style.jitter * w
            val jy = DabRandom.gaussian(seed, k, 2) * style.jitter * w
            matrix.reset()
            matrix.postScale(size, size)
            matrix.postRotate(angle)
            matrix.postTranslate(s.x[i] + jx, s.y[i] + jy)
            unit.transform(matrix, one)
            // Şekiller ayrı ayrı çizilir: üst üste binen şekillerde boya birikir
            val copy = Path(one)
            ops += { c -> c.drawPath(copy, paint) }
        }
    }

    private fun buildScatter(s: SampleBuffer, ops: MutableList<(Canvas) -> Unit>) {
        val buckets = DabBuckets(width * 0.5f)
        for (i in 0 until s.size) {
            val k = s.index[i]
            val w = width * wf(s.raw[i])
            for (j in 0 until 3) {
                val big = DabRandom.unit(seed, k, 40 + j * 5) < 0.08f
                val v = DabRandom.unit(seed, k, 41 + j * 5)
                val d = if (big) w * (0.3f + 0.2f * v) else w * (0.06f + 0.12f * v)
                val spread = style.jitter * w * abs(DabRandom.gaussian(seed, k, 42 + j * 5)) * 0.6f
                val a = DabRandom.unit(seed, k, 43 + j * 5) * 2f * PI.toFloat()
                buckets.add(s.x[i] + cos(a) * spread, s.y[i] + sin(a) * spread, d)
            }
        }
        val paint = texturedPaint(color, flowA, null)
        ops += { c -> buckets.draw(c, paint) }
    }

    private fun buildHalftone(s: SampleBuffer, ops: MutableList<(Canvas) -> Unit>) {
        val kind = style.halftone ?: return
        val pitch = (width * 0.22f).roundToInt().coerceIn(3, 40)
        val tile = BrushTextures.halftone(kind, pitch, halftoneLevel)
        val buckets = DabBuckets(width)
        for (i in 0 until s.size) buckets.add(s.x[i], s.y[i], width * wf(s.raw[i]))
        val paint = texturedPaint(color, dabFlow(flowA, flowSpacing), tile)
        ops += { c -> buckets.draw(c, paint) }
    }

    private fun buildPattern(s: SampleBuffer, ops: MutableList<(Canvas) -> Unit>) {
        when (style.pattern) {
            PatternKind.DOTS -> {
                val buckets = DabBuckets(width * 0.62f)
                for (i in 0 until s.size) buckets.add(s.x[i], s.y[i], width * 0.62f * wf(s.raw[i]))
                val paint = texturedPaint(color, flowA, null)
                ops += { c -> buckets.draw(c, paint) }
            }
            PatternKind.DOT_TRAIL -> {
                val buckets = DabBuckets(width)
                for (i in 0 until s.size) buckets.add(s.x[i], s.y[i], width * wf(s.raw[i]))
                val paint = texturedPaint(color, flowA, null)
                ops += { c -> buckets.draw(c, paint) }
            }
            PatternKind.HATCH -> {
                val pitch = (width * 0.3f).roundToInt().coerceIn(3, 24)
                val buckets = DabBuckets(width)
                for (i in 0 until s.size) buckets.add(s.x[i], s.y[i], width * wf(s.raw[i]))
                // v21 sık damgalar: akışkanlık tek geçişteki boyayı belirler (önceden üst üste binmeyle kayboluyordu)
                val paint = texturedPaint(color, dabFlow(flowA, flowSpacing), BrushTextures.hatch(pitch))
                ops += { c -> buckets.draw(c, paint) }
            }
            else -> Unit
        }
    }

    /** v21 doku/desen: fırçanın renginden tonlanmış, kağıda sabit karo; damgalar opak karo gösterir. */
    private val surfaceShader: android.graphics.Shader? by lazy {
        val kind = style.surface ?: return@lazy null
        val texture = android.graphics.BitmapShader(
            SurfaceTextures.tile(kind, opaqueColor), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT,
        ).apply { setLocalMatrix(Matrix().apply { val sc = SurfaceTextures.scaleFor(kind, width); setScale(sc, sc) }) }
        // Az boyada doku kağıt dişine takılır (ikili maske: damgaların üst üste bindiği yerde koyulaşmaz)
        flowGrain?.let { android.graphics.ComposeShader(texture, it, PorterDuff.Mode.DST_IN) } ?: texture
    }

    private fun buildSurface(s: SampleBuffer, ops: MutableList<(Canvas) -> Unit>) {
        val shader = surfaceShader ?: return
        val buckets = DabBuckets(width, if (hasDynamics) ALPHA_LEVELS else 1)
        for (i in 0 until s.size) buckets.add(s.x[i], s.y[i], width * wf(s.raw[i]), af(s.raw[i]))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
            this.shader = shader
        }
        ops += { c -> buckets.draw(c, paint) }
    }

    /**
     * v21 ıslak izler. Mürekkep: kenarı dalgalı gövde, yer yer taşan leke, yana sıçrayan damlacıklar, başta ve sonda
     * birikme. Döküm: gövde ve gövdeden aşağı (kağıtta) akan, incelip ucunda damla toplanan boya damlaları. Rastgelelik
     * damga sırasına bağlı (çizgi uzadıkça değişmez).
     */
    private fun buildTrail(s: SampleBuffer, ops: MutableList<(Canvas) -> Unit>) {
        val body = DabBuckets(width)
        val extra = DabBuckets(width * 0.5f)
        val finalIndex = if (ended) sampler.samples.size - 1 else -1
        for (i in 0 until s.size) {
            val k = s.index[i]
            val w = width * wf(s.raw[i])
            when (style.trail) {
                TrailKind.INK -> {
                    // Dalgalı kenar: çap çizgi boyunca yumuşakça değişir
                    val wave = 0.82f + 0.3f * smoothNoise(s.along[i] / max(2f, width * 0.8f), 51)
                    body.add(s.x[i], s.y[i], w * wave)
                    if (k < 3) body.add(s.x[i], s.y[i], w * (1.35f - 0.1f * k))
                    if (k == finalIndex) body.add(s.x[i], s.y[i], w * 1.3f)
                    if (DabRandom.unit(seed, k, 52) < inkBlotChance) {
                        val a = DabRandom.unit(seed, k, 53) * 2f * PI.toFloat()
                        val d = w * 0.22f * DabRandom.unit(seed, k, 54)
                        body.add(s.x[i] + cos(a) * d, s.y[i] + sin(a) * d, w * (1.15f + 0.45f * DabRandom.unit(seed, k, 55)))
                    }
                    if (DabRandom.unit(seed, k, 56) < inkDropChance) {
                        val side = if (DabRandom.unit(seed, k, 57) < 0.5f) 1f else -1f
                        val dist = w * (0.75f + 0.75f * DabRandom.unit(seed, k, 58)) * side
                        extra.add(s.x[i] - s.dy[i] * dist, s.y[i] + s.dx[i] * dist, w * (0.07f + 0.16f * DabRandom.unit(seed, k, 59)))
                    }
                }
                TrailKind.DRIP -> {
                    body.add(s.x[i], s.y[i], w * (0.92f + 0.12f * smoothNoise(s.along[i] / max(2f, width * 0.6f), 61)))
                    if (k == finalIndex) body.add(s.x[i], s.y[i], w * 1.15f)
                    if (DabRandom.unit(seed, k, 62) < dripChance) addDrip(extra, s.x[i] + (DabRandom.unit(seed, k, 63) - 0.5f) * w * 0.6f, s.y[i], w, k)
                }
                null -> body.add(s.x[i], s.y[i], w)
            }
        }
        val bodyPaint = texturedPaint(color, dabFlow(flowA, flowSpacing), null)
        val extraPaint = texturedPaint(color, flowA, null)
        ops += { c -> body.draw(c, bodyPaint) }
        if (!extra.isEmpty) ops += { c -> extra.draw(c, extraPaint) }
    }

    /** Aşağı akan damla: gövdenin ortasından başlar, incelir, ucunda yuvarlak damla. */
    private fun addDrip(into: DabBuckets, x: Float, y: Float, w: Float, k: Int) {
        val u = DabRandom.unit(seed, k, 64)
        val length = w * (0.5f + (DRIP_MAX_LENGTH - 0.9f) * u * u)
        val w0 = w * (0.22f + 0.2f * DabRandom.unit(seed, k, 65))
        var d = 0f
        val stepY = max(0.6f, w0 * 0.3f)
        while (d <= length) {
            val t = d / length
            into.add(x, y + d, w0 * (1f - 0.35f * t))
            d += stepY
        }
        into.add(x, y + length, w0 * 1.25f)
    }

    private val inkBlotChance = (0.35f * step / max(1f, width)).coerceAtMost(1f)
    private val inkDropChance = (0.6f * step / max(1f, width)).coerceAtMost(1f)
    private val dripChance = (step / max(1f, width * 1.6f)).coerceAtMost(1f)

    /** Çizgi boyunca yumuşak değer gürültüsü (0..1). */
    private fun smoothNoise(t: Float, salt: Int): Float {
        val f = kotlin.math.floor(t)
        val a = DabRandom.unit(seed, f.toInt(), salt); val b = DabRandom.unit(seed, f.toInt() + 1, salt)
        val u = t - f
        return a + (b - a) * u * u * (3f - 2f * u)
    }
}

/** Yassı fırça: fırça genişliği boyunca paralel kıllar; her kıl örnekler arası dörtgen şeritlerle çizilir. */
internal class BristleStream(
    type: BrushType, color: Int, sizePx: Float, opacity: Float, seed: Int, flow: Float, strength: Float,
    params: com.karapp.drawguidance.data.model.BrushParams? = null,
) : StrokeStream(type, color, sizePx, opacity, seed, flow, strength, false, params) {

    private val sampler = CurveSampler(max(0.8f, width * 0.2f))
    private var drawn = 0
    private val bristles = (width / 2.2f).roundToInt().coerceIn(5, 16)
    private val halfWidth = max(0.8f, width / bristles * 2.1f) / 2f
    private val offsets = FloatArray(bristles)
    private val paints: Array<Paint>
    private val dryStart = FloatArray(bristles)
    private val dryEnd = FloatArray(bristles)

    override val compositeAlpha: Float get() = strokeAlpha

    init {
        paints = Array(bristles) { b ->
            offsets[b] = ((b + 0.5f) / bristles - 0.5f) * width + DabRandom.gaussian(seed, b, 100) * width * 0.02f
            // Kılın boya yükü: bastırma azaldıkça kenar kıllar daha az boya bırakır
            val edge = abs(b / (bristles - 1f) - 0.5f) * 2f
            val load = (0.55f + 0.45f * DabRandom.unit(seed, b, 101)) * (1f - edge * (1f - strength) * 0.6f)
            // Kılın bir yerinde zaman zaman kuru boşluk (çizgi boyunca mesafeyle)
            if (DabRandom.unit(seed, b, 102) < 0.25f) {
                dryStart[b] = DabRandom.unit(seed, b, 103) * width * 40f
                dryEnd[b] = dryStart[b] + width * (1f + 2.5f * DabRandom.unit(seed, b, 104))
            } else {
                dryStart[b] = Float.MAX_VALUE; dryEnd[b] = Float.MAX_VALUE
            }
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; alpha = (flowA * load * 255).roundToInt().coerceIn(1, 255); this.style = Paint.Style.FILL
            }
        }
    }

    private fun dry(b: Int, along: Float) = along >= dryStart[b] && along <= dryEnd[b]

    override fun takeFinalRaw(minBatch: Int): StrokeChunk? {
        if (ended) sampler.finish(px, py, count) else sampler.advance(px, py, count)
        val s = sampler.samples
        val available = s.size - drawn
        if (available <= 0 || (!ended && available < minBatch)) return null
        val buffer = SampleBuffer()
        if (drawn > 0) buffer.addFrom(s, drawn - 1)
        for (i in drawn until s.size) buffer.addFrom(s, i)
        val chunk = build(buffer, single = s.size == 1)
        drawn = s.size
        return chunk
    }

    override fun tailRaw(): StrokeChunk? {
        if (ended) return null
        sampler.advance(px, py, count)
        val s = sampler.samples
        val buffer = SampleBuffer()
        if (drawn > 0) buffer.addFrom(s, drawn - 1)
        for (i in drawn until s.size) buffer.addFrom(s, i)
        sampler.tail(px, py, count, buffer)
        if (buffer.size == 0) return null
        return build(buffer, single = buffer.size == 1 && s.size <= 1)
    }

    private fun build(s: SampleBuffer, single: Boolean): StrokeChunk {
        val bounds = BoundsBuilder()
        val paths = Array(bristles) { Path() }
        for (b in 0 until bristles) {
            val o = offsets[b]
            val path = paths[b]
            if (single && s.size == 1) {
                val x = s.x[0]; val y = s.y[0] + o
                path.addRect(x - 0.5f, y - halfWidth, x + 0.5f, y + halfWidth, Path.Direction.CW)
                bounds.add(x, y)
                continue
            }
            for (i in 1 until s.size) {
                if (dry(b, s.along[i]) || dry(b, s.along[i - 1])) continue
                // v18: kalem basıncıyla fırça (kıllar arası mesafe ve kıl kalınlığı) daralır
                val fa = wf(s.raw[i - 1]); val fb = wf(s.raw[i])
                val ax = s.x[i - 1] - s.dy[i - 1] * o * fa; val ay = s.y[i - 1] + s.dx[i - 1] * o * fa
                val bx = s.x[i] - s.dy[i] * o * fb; val by = s.y[i] + s.dx[i] * o * fb
                val anx = -s.dy[i - 1] * halfWidth * fa; val any = s.dx[i - 1] * halfWidth * fa
                val bnx = -s.dy[i] * halfWidth * fb; val bny = s.dx[i] * halfWidth * fb
                path.moveTo(ax + anx, ay + any)
                path.lineTo(bx + bnx, by + bny)
                path.lineTo(bx - bnx, by - bny)
                path.lineTo(ax - anx, ay - any)
                path.close()
            }
        }
        for (i in 0 until s.size) bounds.add(s.x[i], s.y[i])
        return StrokeChunk(bounds.toRect(pad), listOf { c -> for (b in 0 until bristles) if (!paths[b].isEmpty) c.drawPath(paths[b], paints[b]) })
    }
}

// ---------------------------------------------------------------------------------------------
// Yol tabanlı motorlar (ham katman opak; opaklık ve akışkanlık birleştirmede uygulanır)
// ---------------------------------------------------------------------------------------------

/** Tek vektör yol: kalem, teknik kalem, marker, fosforlu, sert silgi. */
internal class PathStream(
    type: BrushType, color: Int, sizePx: Float, opacity: Float, seed: Int, flow: Float, strength: Float, eraserMask: Boolean,
    params: com.karapp.drawguidance.data.model.BrushParams? = null,
) : StrokeStream(type, color, sizePx, opacity, seed, flow, strength, eraserMask, params) {

    private val smooth = style.smooth
    /** Çizilmiş son parça (1..count-1). */
    private var drawnSegments = 0
    private var endDrawn = false

    override val compositeAlpha: Float get() = if (isEraser) 1f else strokeAlpha * flowInk

    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = if (isEraser) 0xFF000000.toInt() else opaqueColor
        this.style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.BUTT
        strokeJoin = nativeJoin(this@PathStream.style.join)
    }.withFlow()
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = if (isEraser) 0xFF000000.toInt() else opaqueColor
        this.style = Paint.Style.FILL
    }.withFlow()

    /**
     * v18 kalem basıncı: her parça basınca göre kalınlıkta, yuvarlak uçlu ayrı bir çizgi olarak çizilir (ham katman
     * opak olduğundan birleşim yerleri koyulaşmaz; opaklık birleştirmede uygulanır). Kalemle çizilen çizgiler sık
     * noktalı olduğundan yumuşatma gerekmez.
     */
    private val dynPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = if (isEraser) 0xFF000000.toInt() else opaqueColor
        this.style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        if (isEraser && !eraserMask) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }.withFlow()
    private val dynDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = if (isEraser) 0xFF000000.toInt() else opaqueColor
        this.style = Paint.Style.FILL
        if (isEraser && !eraserMask) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }.withFlow()

    private fun buildDynamic(from: Int, to: Int): StrokeChunk? {
        if (count == 0) return null
        val bounds = BoundsBuilder()
        if (count == 1) {
            val x = px[0]; val y = py[0]; val r = width * wf(0) / 2f
            bounds.add(x, y)
            val dot = Paint(dynDot)
            return StrokeChunk(bounds.toRect(pad), listOf { c -> c.drawCircle(x, y, r, dot) })
        }
        val first = max(1, from)
        if (first > to) return null
        val n = to - first + 1
        val seg = FloatArray(n * 5)
        for (k in 0 until n) {
            val j = first + k
            seg[k * 5] = px[j - 1]; seg[k * 5 + 1] = py[j - 1]; seg[k * 5 + 2] = px[j]; seg[k * 5 + 3] = py[j]
            seg[k * 5 + 4] = max(0.5f, width * wf(j))
            bounds.add(px[j - 1], py[j - 1]); bounds.add(px[j], py[j])
        }
        // Parça başına kendi boyası: aynı parça ön tampona başka bir iş parçacığında da çizilebilir
        val paint = Paint(dynPaint)
        return StrokeChunk(bounds.toRect(pad), listOf { c ->
            for (k in 0 until n) {
                paint.strokeWidth = seg[k * 5 + 4]
                c.drawLine(seg[k * 5], seg[k * 5 + 1], seg[k * 5 + 2], seg[k * 5 + 3], paint)
            }
        })
    }

    /** Kesin parça sayısı: yumuşak çizgide son nokta, köşeli çizgide hiçbir şey geçici değildir. */
    private fun finalSegments(): Int = when {
        ended -> count - 1
        smooth -> if (count >= 3) count - 1 else 0
        else -> count - 1
    }

    /** Parça [j]'nin başlangıç noktası (yumuşak çizgide önceki orta nokta). */
    private fun segmentStartX(j: Int) = if (!smooth || j == 1) px[j - 1] else (px[j - 2] + px[j - 1]) / 2f
    private fun segmentStartY(j: Int) = if (!smooth || j == 1) py[j - 1] else (py[j - 2] + py[j - 1]) / 2f

    private fun appendSegment(path: Path, j: Int, bounds: BoundsBuilder) {
        if (smooth) {
            val ex = (px[j - 1] + px[j]) / 2f; val ey = (py[j - 1] + py[j]) / 2f
            path.quadTo(px[j - 1], py[j - 1], ex, ey)
            bounds.add(px[j - 1], py[j - 1]); bounds.add(ex, ey)
        } else {
            path.lineTo(px[j], py[j]); bounds.add(px[j], py[j])
        }
    }

    /**
     * [from]..[to] parçalarından (yumuşak çizgide [withTail] ise son ham noktaya düz kuyrukla) opak yol.
     * Parça uçları düzdür; gerçek başlangıç/bitiş uçları ayrıca çizilir.
     */
    private fun build(from: Int, to: Int, withTail: Boolean, trueStart: Boolean): StrokeChunk? {
        if (count == 0) return null
        val bounds = BoundsBuilder()
        val ops = ArrayList<(Canvas) -> Unit>(3)
        val cap = if (isEraser) StrokeCap.Round else style.cap
        if (count == 1) {
            val x = px[0]; val y = py[0]
            bounds.add(x, y)
            ops += { c -> c.drawCircle(x, y, width / 2f, capPaint) }
            return StrokeChunk(bounds.toRect(pad), ops)
        }
        val path = Path()
        var endX: Float
        var endY: Float
        if (smooth && count == 2) {
            // İki noktalı yumuşak çizgi düz bir çizgidir (henüz kesin parça yok)
            if (!withTail) return null
            path.moveTo(px[0], py[0]); path.lineTo(px[1], py[1])
            bounds.add(px[0], py[0]); bounds.add(px[1], py[1])
            endX = px[1]; endY = py[1]
        } else {
            val first = max(1, from)
            if (first > to && !(withTail && smooth)) return null
            val sx0 = if (first <= to || !smooth) segmentStartX(first) else segmentStartX(to.coerceAtLeast(1))
            val sy0 = if (first <= to || !smooth) segmentStartY(first) else segmentStartY(to.coerceAtLeast(1))
            path.moveTo(sx0, sy0); bounds.add(sx0, sy0)
            endX = sx0; endY = sy0
            for (j in first..to) {
                appendSegment(path, j, bounds)
                if (smooth) { endX = (px[j - 1] + px[j]) / 2f; endY = (py[j - 1] + py[j]) / 2f } else { endX = px[j]; endY = py[j] }
            }
            if (withTail && smooth) {
                path.lineTo(px[count - 1], py[count - 1]); bounds.add(px[count - 1], py[count - 1])
                endX = px[count - 1]; endY = py[count - 1]
            }
        }
        ops += { c -> c.drawPath(path, piecePaint) }
        if (trueStart) {
            val sx = px[0]; val sy = py[0]; val nx = px[1]; val ny = py[1]
            ops += { c -> drawCap(c, sx, sy, sx - nx, sy - ny, cap, width, capPaint) }
        }
        if (withTail) {
            val prevX = px[count - 2]; val prevY = py[count - 2]
            val ex = endX; val ey = endY
            ops += { c -> drawCap(c, ex, ey, ex - prevX, ey - prevY, cap, width, capPaint) }
        }
        return StrokeChunk(bounds.toRect(pad), ops)
    }

    override fun takeFinalRaw(minBatch: Int): StrokeChunk? {
        if (count == 0) return null
        if (hasDynamics) {
            // Kalem basınçlı çizgi: tüm parçalar kesindir (yumuşatma kuyruğu yok)
            if (ended) {
                if (endDrawn) return null
                endDrawn = true
                val chunk = if (count == 1) buildDynamic(0, 0) else buildDynamic(max(1, drawnSegments), count - 1)
                drawnSegments = max(1, count - 1)
                return chunk
            }
            val available = count - 1 - drawnSegments
            if (available <= 0 || available < minBatch) return null
            val chunk = buildDynamic(max(1, drawnSegments), count - 1)
            drawnSegments = count - 1
            return chunk
        }
        if (ended) {
            if (endDrawn) return null
            endDrawn = true
            // Son kuyruk (son orta noktadan son ham noktaya) ve bitiş ucu her zaman bitişte çizilir
            val chunk = build(max(1, drawnSegments), count - 1, withTail = true, trueStart = drawnSegments == 0)
            drawnSegments = max(1, count - 1)
            return chunk
        }
        val final = finalSegments()
        val available = final - drawnSegments
        if (available <= 0 || available < minBatch) return null
        // Bir önceki parçayı tekrar çiz: birleşim yeri doğru olsun (opak katmanda üst üste binmek zararsız)
        val chunk = build(max(1, drawnSegments), final, withTail = false, trueStart = drawnSegments == 0)
        drawnSegments = final
        return chunk
    }

    override fun tailRaw(): StrokeChunk? {
        if (ended || count == 0) return null
        if (hasDynamics) return if (count == 1) buildDynamic(0, 0) else null
        return build(max(1, drawnSegments), finalSegments(), withTail = true, trueStart = drawnSegments == 0)
    }

    override fun drawStatic(canvas: Canvas) {
        // Basınçlı çizgi ham katmanda parça parça çizilir (katmanla birleştirilir); v21 3B derinlik de öyle
        if (hasDynamics || hasExtrusion) return super.drawStatic(canvas)
        end()
        if (count == 0) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = opaqueColor
            alpha = (compositeAlpha * 255).roundToInt().coerceIn(0, 255)
            if (isEraser) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) else applyBlend(this, blend)
        }.withFlow()
        if (count == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(px[0], py[0], width / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = if (isEraser) Paint.Cap.ROUND else nativeCap(style.cap)
        paint.strokeJoin = nativeJoin(style.join)
        canvas.drawPath(BrushPainter.buildPath(px, py, count, smooth), paint)
    }
}

/** Uçları sivrilen, hızlı çizilen yerlerde incelen mürekkep. */
internal class TaperedStream(
    type: BrushType, color: Int, sizePx: Float, opacity: Float, seed: Int, flow: Float, strength: Float,
    params: com.karapp.drawguidance.data.model.BrushParams? = null,
) : StrokeStream(type, color, sizePx, opacity, seed, flow, strength, false, params) {

    private val sampler = CurveSampler(max(0.8f, width * 0.2f))
    private val maxTaper = width * 4f
    /** Çizilmiş son örnek (bu örneğe kadarki dörtgenler çizildi). */
    private var drawnUntil = 0
    private var gapSums = FloatArray(128)

    override val compositeAlpha: Float get() = strokeAlpha * flowInk

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = opaqueColor; this.style = Paint.Style.FILL }.withFlow()

    /** Ham noktalar arası mesafe (hız) ve o ana kadarki ortalama (geleceğe bakmaz). */
    private fun speedFactor(raw: Int): Float {
        // v18: kalem basıncı varsa kalınlığı basınç belirler (hıza göre incelme kalemsiz çizim içindir)
        if (hasDynamics) return wf(raw)
        if (raw < 1 || raw >= count) return 1f
        if (gapSums.size < count) gapSums = gapSums.copyOf(max(count, gapSums.size * 2))
        // Önek toplamları gerektiği kadar hesaplanır
        var i = computed
        while (i < count) {
            gapSums[i] = if (i == 0) 0f else gapSums[i - 1] + hypot(px[i] - px[i - 1], py[i] - py[i - 1])
            i++
        }
        computed = count
        val gap = gapSums[raw] - gapSums[raw - 1]
        val mean = max(0.5f, gapSums[raw] / raw)
        val speed = gap / mean
        return (1.15f - 0.3f * (speed - 1f)).coerceIn(0.55f, 1.15f)
    }
    private var computed = 0

    private fun half(s: SampleBuffer, i: Int, length: Float): Float {
        val taper = min(length * 0.3f, maxTaper).coerceAtLeast(1f)
        val along = s.along[i]
        val ends = min(1f, min(along, length - along) / taper).coerceAtLeast(0f)
        return width / 2f * speedFactor(s.raw[i]) * (0.15f + 0.85f * sqrt(ends))
    }

    private fun quads(s: SampleBuffer, halves: FloatArray, from: Int, bounds: BoundsBuilder): Path {
        val path = Path()
        for (i in max(1, from) until s.size) {
            val an = halves[i - 1]; val bn = halves[i]
            val ax = s.x[i - 1]; val ay = s.y[i - 1]; val bx = s.x[i]; val by = s.y[i]
            path.moveTo(ax - s.dy[i - 1] * an, ay + s.dx[i - 1] * an)
            path.lineTo(bx - s.dy[i] * bn, by + s.dx[i] * bn)
            path.lineTo(bx + s.dy[i] * bn, by - s.dx[i] * bn)
            path.lineTo(ax + s.dy[i - 1] * an, ay - s.dx[i - 1] * an)
            path.close()
            bounds.add(ax, ay); bounds.add(bx, by)
        }
        return path
    }

    override fun takeFinalRaw(minBatch: Int): StrokeChunk? {
        if (ended) sampler.finish(px, py, count) else sampler.advance(px, py, count)
        val s = sampler.samples
        val commitUntil: Int = if (ended) s.size else {
            val final = sampler.finalLength
            if (final * 0.3f < maxTaper) 0 else {
                var c = drawnUntil
                while (c < s.size && s.along[c] <= final - maxTaper) c++
                c
            }
        }
        if (commitUntil <= drawnUntil || (!ended && commitUntil - drawnUntil < minBatch)) return null
        val length = if (ended) sampler.finalLength else Float.MAX_VALUE
        val buffer = SampleBuffer()
        val from = max(0, drawnUntil - 1)
        for (i in from until commitUntil) buffer.addFrom(s, i)
        // Canlıda kesinleşen örneklerin sonu en az maxTaper uzakta: yalnızca başlangıç sivrilmesi etkiler
        val halves = FloatArray(buffer.size) { if (ended) half(buffer, it, length) else halfFixed(buffer, it) }
        val bounds = BoundsBuilder()
        val ops = ArrayList<(Canvas) -> Unit>(1)
        if (ended && s.size < 2) {
            val x = s.x[0]; val y = s.y[0]
            bounds.add(x, y)
            ops += { c -> c.drawCircle(x, y, width * 0.35f, fill) }
        } else {
            val path = quads(buffer, halves, 1, bounds)
            ops += { c -> c.drawPath(path, fill) }
        }
        drawnUntil = commitUntil
        return StrokeChunk(bounds.toRect(pad), ops)
    }

    /** Uzunluğu henüz belli olmayan (ama sonu en az maxTaper uzakta olan) örneğin yarı genişliği. */
    private fun halfFixed(s: SampleBuffer, i: Int): Float {
        val ends = min(1f, s.along[i] / maxTaper)
        return width / 2f * speedFactor(s.raw[i]) * (0.15f + 0.85f * sqrt(ends))
    }

    override fun tailRaw(): StrokeChunk? {
        if (ended || count == 0) return null
        sampler.advance(px, py, count)
        val s = sampler.samples
        val buffer = SampleBuffer()
        for (i in max(0, drawnUntil - 1) until s.size) buffer.addFrom(s, i)
        sampler.tail(px, py, count, buffer)
        val bounds = BoundsBuilder()
        if (buffer.size < 2) {
            if (buffer.size == 0 || drawnUntil > 0) return null
            val x = buffer.x[0]; val y = buffer.y[0]
            bounds.add(x, y)
            return StrokeChunk(bounds.toRect(pad), listOf { c -> c.drawCircle(x, y, width * 0.35f, fill) })
        }
        val length = buffer.along[buffer.size - 1]
        val halves = FloatArray(buffer.size) { half(buffer, it, length) }
        val path = quads(buffer, halves, 1, bounds)
        return StrokeChunk(bounds.toRect(pad), listOf { c -> c.drawPath(path, fill) })
    }

    override fun drawStatic(canvas: Canvas) {
        if (hasExtrusion) return super.drawStatic(canvas)
        end()
        if (count == 0) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = opaqueColor; alpha = (compositeAlpha * 255).roundToInt().coerceIn(0, 255); this.style = Paint.Style.FILL
            applyBlend(this, blend)
        }.withFlow()
        sampler.finish(px, py, count)
        val s = sampler.samples
        if (count == 1 || s.size < 2) { canvas.drawCircle(s.x[0], s.y[0], width * 0.35f, paint); return }
        val length = sampler.finalLength
        val halves = FloatArray(s.size) { half(s, it, length) }
        // Tek dolgu yolu: dörtgenler aynı yolda olduğundan üst üste binen yerler koyulaşmaz
        val path = quads(s, halves, 1, BoundsBuilder())
        canvas.drawPath(path, paint)
    }
}

/** 45° yassı kaligrafi ucu: her adım aynı yönde sarılmış bir dörtgen + ince orta çizgi. */
internal class RibbonStream(
    type: BrushType, color: Int, sizePx: Float, opacity: Float, seed: Int, flow: Float, strength: Float,
    params: com.karapp.drawguidance.data.model.BrushParams? = null,
) : StrokeStream(type, color, sizePx, opacity, seed, flow, strength, false, params) {

    private val sampler = CurveSampler(max(0.8f, width * 0.15f))
    private var drawn = 0
    private val nbx = 0.7071f * width / 2f
    private val nby = -0.7071f * width / 2f
    /** v20: uç kalemin yönüne döner (kalem yön bildiriyorsa); yoksa sabit 45°. */
    private val nibFollow = params?.nibFollow == true

    /** Ham nokta [raw]'daki uç yarı-vektörü: yassı ucun geniş kenarı kalemin yöneldiği yöne diktir. */
    private fun nibX(raw: Int): Float = orientAt(raw)?.takeIf { nibFollow }?.let { cos(it) * width / 2f } ?: nbx
    private fun nibY(raw: Int): Float = orientAt(raw)?.takeIf { nibFollow }?.let { sin(it) * width / 2f } ?: nby
    private val lineWidth = max(1f, width * 0.12f)
    private var drawnSegments = 0

    override val compositeAlpha: Float get() = strokeAlpha * flowInk

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = opaqueColor; this.style = Paint.Style.FILL }.withFlow()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = opaqueColor; this.style = Paint.Style.STROKE; strokeWidth = lineWidth; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }.withFlow()

    private fun quad(
        path: Path, ax: Float, ay: Float, bx: Float, by: Float, fa: Float = 1f, fb: Float = 1f,
        nax: Float = nbx, nay: Float = nby, nbX: Float = nbx, nbY: Float = nby,
    ) {
        val p0x = ax + nax * fa; val p0y = ay + nay * fa; val p1x = bx + nbX * fb; val p1y = by + nbY * fb
        val p2x = bx - nbX * fb; val p2y = by - nbY * fb; val p3x = ax - nax * fa; val p3y = ay - nay * fa
        var area = 0f
        area += p0x * p1y - p1x * p0y; area += p1x * p2y - p2x * p1y; area += p2x * p3y - p3x * p2y; area += p3x * p0y - p0x * p3y
        if (area < 0) {
            path.moveTo(p0x, p0y); path.lineTo(p3x, p3y); path.lineTo(p2x, p2y); path.lineTo(p1x, p1y)
        } else {
            path.moveTo(p0x, p0y); path.lineTo(p1x, p1y); path.lineTo(p2x, p2y); path.lineTo(p3x, p3y)
        }
        path.close()
    }

    private fun ribbon(s: SampleBuffer, from: Int, single: Boolean, bounds: BoundsBuilder): Path {
        val path = Path().apply { fillType = Path.FillType.WINDING }
        if (single && s.size == 1) quad(path, s.x[0] - 0.5f, s.y[0], s.x[0] + 0.5f, s.y[0])
        for (i in max(1, from) until s.size) {
            val a = s.raw[i - 1]; val b = s.raw[i]
            quad(path, s.x[i - 1], s.y[i - 1], s.x[i], s.y[i], wf(a), wf(b), nibX(a), nibY(a), nibX(b), nibY(b))
        }
        for (i in 0 until s.size) bounds.add(s.x[i], s.y[i])
        return path
    }

    /** Orta çizgi: kalemle aynı yumuşatılmış yol, [from]..[to] parçaları (+ kuyruk). */
    private fun centerLine(from: Int, to: Int, withTail: Boolean): Path? {
        if (count < 2) return null
        val path = Path()
        if (count == 2) {
            if (!withTail) return null
            path.moveTo(px[0], py[0]); path.lineTo(px[1], py[1]); return path
        }
        val first = max(1, from)
        if (first == 1) path.moveTo(px[0], py[0]) else path.moveTo((px[first - 2] + px[first - 1]) / 2f, (py[first - 2] + py[first - 1]) / 2f)
        for (j in first..to) path.quadTo(px[j - 1], py[j - 1], (px[j - 1] + px[j]) / 2f, (py[j - 1] + py[j]) / 2f)
        if (withTail) path.lineTo(px[count - 1], py[count - 1])
        return path
    }

    override fun takeFinalRaw(minBatch: Int): StrokeChunk? {
        if (count == 0) return null
        if (ended) sampler.finish(px, py, count) else sampler.advance(px, py, count)
        val s = sampler.samples
        val available = s.size - drawn
        val finalSegs = if (ended || count >= 3) count - 1 else 0
        if (!ended && available < max(1, minBatch)) return null
        if (ended && available <= 0 && finalSegs <= drawnSegments) return null
        val buffer = SampleBuffer()
        if (drawn > 0) buffer.addFrom(s, drawn - 1)
        for (i in drawn until s.size) buffer.addFrom(s, i)
        val bounds = BoundsBuilder()
        val quads = ribbon(buffer, 1, single = ended && s.size == 1, bounds)
        val center = if (ended || finalSegs > drawnSegments) centerLine(max(1, drawnSegments), finalSegs, withTail = ended) else null
        drawn = s.size
        drawnSegments = max(drawnSegments, finalSegs)
        bounds.add(px[0], py[0]); bounds.add(px[count - 1], py[count - 1])
        val ops = ArrayList<(Canvas) -> Unit>(2)
        ops += { c -> c.drawPath(quads, fill) }
        if (center != null) ops += { c -> c.drawPath(center, line) }
        return StrokeChunk(bounds.toRect(pad), ops)
    }

    override fun tailRaw(): StrokeChunk? {
        if (ended || count == 0) return null
        sampler.advance(px, py, count)
        val s = sampler.samples
        val buffer = SampleBuffer()
        if (drawn > 0) buffer.addFrom(s, drawn - 1)
        for (i in drawn until s.size) buffer.addFrom(s, i)
        sampler.tail(px, py, count, buffer)
        val bounds = BoundsBuilder()
        val quads = ribbon(buffer, 1, single = s.size + buffer.size <= 1, bounds)
        val finalSegs = if (count >= 3) count - 1 else 0
        val center = centerLine(max(1, drawnSegments), finalSegs, withTail = true)
        for (i in max(0, count - 3) until count) bounds.add(px[i], py[i])
        val ops = ArrayList<(Canvas) -> Unit>(2)
        ops += { c -> c.drawPath(quads, fill) }
        if (center != null) ops += { c -> c.drawPath(center, line) }
        return StrokeChunk(bounds.toRect(pad), ops)
    }

    override fun drawStatic(canvas: Canvas) {
        if (hasExtrusion) return super.drawStatic(canvas)
        end()
        if (count == 0) return
        sampler.finish(px, py, count)
        val s = sampler.samples
        val path = ribbon(s, 1, single = s.size == 1, BoundsBuilder())
        // Orta çizgi dolguya çevrilip aynı yola eklenir: tek çizimde üst üste binen yerler koyulaşmaz
        BrushPainter.buildPath(px, py, count, true).let { center ->
            val outline = Path()
            line.getFillPath(center, outline)
            path.addPath(outline)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = opaqueColor; alpha = (compositeAlpha * 255).roundToInt().coerceIn(0, 255); this.style = Paint.Style.FILL
            applyBlend(this, blend)
        }.withFlow()
        canvas.drawPath(path, paint)
    }
}

/** Zikzak desen: örnek başına iki yana dönüşümlü köşe; keskin köşeli tek çizgi. */
internal class ZigzagStream(
    type: BrushType, color: Int, sizePx: Float, opacity: Float, seed: Int, flow: Float, strength: Float,
    params: com.karapp.drawguidance.data.model.BrushParams? = null,
) : StrokeStream(type, color, sizePx, opacity, seed, flow, strength, false, params) {

    private val sampler = CurveSampler(max(1.5f, width * (params?.spacing?.coerceIn(0.1f, 3f) ?: 0.55f)))
    private var drawn = 0
    private val lineWidth = max(1f, width * 0.16f)

    override val compositeAlpha: Float get() = strokeAlpha * flowInk

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = opaqueColor; this.style = Paint.Style.STROKE; strokeWidth = lineWidth
        strokeJoin = Paint.Join.MITER; strokeCap = Paint.Cap.BUTT
    }.withFlow()
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = opaqueColor; this.style = Paint.Style.FILL }.withFlow()

    private fun vx(s: SampleBuffer, i: Int): Float {
        val side = if (s.index[i] % 2 == 0) 1f else -1f
        return s.x[i] - s.dy[i] * width * 0.45f * side * wf(s.raw[i])
    }

    private fun vy(s: SampleBuffer, i: Int): Float {
        val side = if (s.index[i] % 2 == 0) 1f else -1f
        return s.y[i] + s.dx[i] * width * 0.45f * side * wf(s.raw[i])
    }

    private fun polyline(s: SampleBuffer, trueStart: Boolean, trueEnd: Boolean): StrokeChunk? {
        if (s.size < 2) return null
        val path = Path()
        val bounds = BoundsBuilder()
        for (i in 0 until s.size) {
            val x = vx(s, i); val y = vy(s, i)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            bounds.add(x, y)
        }
        val ops = ArrayList<(Canvas) -> Unit>(3)
        ops += { c -> c.drawPath(path, line) }
        if (trueStart) {
            val x0 = vx(s, 0); val y0 = vy(s, 0); val x1 = vx(s, 1); val y1 = vy(s, 1)
            ops += { c -> drawCap(c, x0, y0, x0 - x1, y0 - y1, StrokeCap.Square, lineWidth, capPaint) }
        }
        if (trueEnd) {
            val n = s.size
            val xa = vx(s, n - 2); val ya = vy(s, n - 2); val xb = vx(s, n - 1); val yb = vy(s, n - 1)
            ops += { c -> drawCap(c, xb, yb, xb - xa, yb - ya, StrokeCap.Square, lineWidth, capPaint) }
        }
        return StrokeChunk(bounds.toRect(pad), ops)
    }

    override fun takeFinalRaw(minBatch: Int): StrokeChunk? {
        if (ended) sampler.finish(px, py, count) else sampler.advance(px, py, count)
        val s = sampler.samples
        val available = s.size - drawn
        if (available <= 0 || (!ended && available < minBatch)) return null
        val buffer = SampleBuffer()
        // İki önceki köşeden başla: birleşim köşesi bu parçada tam çizilsin
        for (i in max(0, drawn - 2) until s.size) buffer.addFrom(s, i)
        val chunk = polyline(buffer, trueStart = drawn == 0, trueEnd = ended)
        drawn = s.size
        return chunk
    }

    override fun tailRaw(): StrokeChunk? {
        if (ended) return null
        sampler.advance(px, py, count)
        val s = sampler.samples
        val buffer = SampleBuffer()
        for (i in max(0, drawn - 2) until s.size) buffer.addFrom(s, i)
        sampler.tail(px, py, count, buffer)
        return polyline(buffer, trueStart = drawn == 0, trueEnd = true)
    }

    override fun drawStatic(canvas: Canvas) {
        if (hasExtrusion) return super.drawStatic(canvas)
        end()
        if (count == 0) return
        sampler.finish(px, py, count)
        val s = sampler.samples
        if (s.size < 2) return
        val path = Path()
        for (i in 0 until s.size) if (i == 0) path.moveTo(vx(s, i), vy(s, i)) else path.lineTo(vx(s, i), vy(s, i))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = opaqueColor; alpha = (compositeAlpha * 255).roundToInt().coerceIn(0, 255)
            this.style = Paint.Style.STROKE; strokeWidth = lineWidth; strokeJoin = Paint.Join.MITER; strokeCap = Paint.Cap.SQUARE
            applyBlend(this, blend)
        }.withFlow()
        canvas.drawPath(path, paint)
    }
}

/**
 * v20 3B fırça: çizgi, ışığın geldiği yana doğru kaydırılmış ve içe doğru daralan gölgeli bantlarla çizilen gerçek bir
 * tüp gibi görünür (kenarlar koyu, ışığa bakan yüz aydınlık, en üstte parlama). Malzeme bantların parlaklık eğrisini
 * belirler: mat (yalnız gölgeleme), plastik (beyaz parlama), metal (yansıma bantları, renkli parlama), cam (yarı saydam
 * gövde, kenar çizgisi, karşı yanda ikinci parlama). Uçlar yarım küre gibi gölgelenir. Kağıda düşen gölge ışığın
 * tersine kayık, yumuşak kenarlıdır; ayrı katmanda (opak siyah) toplanıp saydamlıkla izin altına birleştirilir, böylece
 * parçaların birleşim yerlerinde koyulaşmaz. Kalem basıncı tüpün kalınlığını değiştirir.
 */
internal class TubeStream(
    type: BrushType, color: Int, sizePx: Float, opacity: Float, seed: Int, flow: Float, strength: Float,
    params: com.karapp.drawguidance.data.model.BrushParams? = null,
    /**
     * v21 boya izi: aynı bant yapısıyla kalın boya. Işık kayması ve gölge yok; gövdenin üstünde fırça boyunca uzanan
     * açık ve koyu şeritler (kıl izleri), kenarları hafif pürüzlü.
     */
    private val paintMode: Boolean = false,
) : StrokeStream(type, color, sizePx, opacity, seed, flow, strength, false, params) {

    private val tube = params?.tube ?: com.karapp.drawguidance.data.model.TubeParams()
    private val material = tube.material
    private val relief = tube.relief.coerceIn(0f, 1f)
    private val gloss = tube.gloss.coerceIn(0f, 1f)
    private val lx: Float
    private val ly: Float
    private val shadowDist = if (paintMode) 0f else tube.shadow.coerceIn(0f, 1f) * TUBE_SHADOW_MAX * width
    private val blur = if (paintMode) 0f else tube.shadowSoftness.coerceIn(0f, 1f) * TUBE_BLUR_MAX * width
    private val glass = !paintMode && material == com.karapp.drawguidance.data.model.TubeMaterial.GLASS
    /** Bant sayısı (boya izinde gövde + kenar + şeritler). */
    private val nBands = if (paintMode) 2 + PAINT_LANES else BANDS

    private val sampler = CurveSampler(max(0.6f, min(width * 0.1f, 6f)))
    private var drawnUntil = 0

    override val compositeAlpha: Float get() = strokeAlpha * flowA * (if (glass) 0.74f else 1f)
    override val shadowAlpha: Float get() = if (paintMode || tube.shadow <= 0.001f) 0f else if (glass) 0.2f else 0.34f
    override val needsOwnLayer: Boolean get() = true
    override val pad: Float get() = (width * 0.55f + shadowDist) * dynamics.maxWidth + blur * 1.2f + 4f

    /** Bantların yarı genişliği, ışığa kayması ve yana kayması (yarı genişliğin katı), rengi. */
    private val bandHalf = FloatArray(nBands)
    private val bandOff = FloatArray(nBands)
    private val bandLat = FloatArray(nBands)
    private val bandColor = IntArray(nBands)

    init {
        val rad = Math.toRadians(tube.lightAngle.toDouble())
        lx = if (paintMode) 0f else cos(rad).toFloat(); ly = if (paintMode) 0f else sin(rad).toFloat()
        val base = opaqueColor
        if (paintMode) initPaintBands(base) else initTubeBands(base)
    }

    /**
     * Boya izi: temel renkte gövde (kenarda çok hafif koyu sırt), üstünde çizgi başına rastgele (tohumlu) yerlerde ince,
     * hafif açık/koyu kıl şeritleri. Şeritler kenara yığılmaz (çerçeve gibi görünmez).
     */
    private fun initPaintBands(base: Int) {
        bandHalf[0] = 1f; bandColor[0] = shade(base, 0.95f)
        bandHalf[1] = 0.95f; bandColor[1] = base
        for (j in 0 until PAINT_LANES) {
            val k = 2 + j
            bandLat[k] = (DabRandom.unit(seed, j, 41) * 2f - 1f) * 0.78f
            bandHalf[k] = 0.025f + 0.055f * DabRandom.unit(seed, j, 42)
            val l = if (DabRandom.unit(seed, j, 43) < 0.55f) 0.86f + 0.09f * DabRandom.unit(seed, j, 44) else 1.05f + 0.1f * DabRandom.unit(seed, j, 45)
            bandColor[k] = shade(base, l)
        }
    }

    private fun initTubeBands(base: Int) {
        val specTint = if (material == com.karapp.drawguidance.data.model.TubeMaterial.METAL) mix(base, WHITE, 0.72f) else WHITE
        for (k in 0 until BANDS) {
            val u = k / (BANDS - 1f)
            bandHalf[k] = 1f - 0.88f * u
            bandOff[k] = u * (0.12f + 0.5f * relief)
            val diffuse = shade(base, lum(u))
            bandColor[k] = mix(diffuse, specTint, spec(u))
        }
    }

    /** Örnekteki yarı genişlik: basınç/eğim; boya izinde çizgi boyunca yumuşakça değişen pürüzlü kenar. */
    private fun halfAt(s: SampleBuffer, i: Int): Float {
        val h = width / 2f * wf(s.raw[i])
        if (!paintMode) return h
        val t = s.along[i] / max(2f, width * 0.55f)
        val f = floor(t)
        val a = DabRandom.unit(seed, f.toInt(), 47); val b = DabRandom.unit(seed, f.toInt() + 1, 47)
        val u = t - f
        val n = a + (b - a) * u * u * (3f - 2f * u)
        return h * (0.9f + 0.16f * n)
    }

    /** Kesitteki konuma (0 kenar, 1 ışığa bakan tepe) göre parlaklık: 1 temel renk, <1 koyu, >1 açık. */
    private fun lum(u: Float): Float = when (material) {
        com.karapp.drawguidance.data.model.TubeMaterial.MATTE -> 1f - 0.62f * relief * (1f - u).pow(1.5f) + 0.16f * relief * u * u
        com.karapp.drawguidance.data.model.TubeMaterial.PLASTIC -> 1f - 0.58f * relief * (1f - u).pow(1.5f) + 0.1f * relief * u * u
        com.karapp.drawguidance.data.model.TubeMaterial.METAL -> 1f + (curve(u, METAL_CURVE) - 1f) * (0.35f + 0.65f * relief)
        com.karapp.drawguidance.data.model.TubeMaterial.GLASS -> 1f + (curve(u, GLASS_CURVE) - 1f) * (0.35f + 0.65f * relief)
    }

    /** Parlama miktarı (0..1). Parlak yüzeyde küçük ve güçlü, mat yüzeyde geniş ve zayıf. */
    private fun spec(u: Float): Float = when (material) {
        com.karapp.drawguidance.data.model.TubeMaterial.MATTE -> 0.12f * gloss * smooth(0.7f, 1f, u)
        com.karapp.drawguidance.data.model.TubeMaterial.PLASTIC -> (0.3f + 0.62f * gloss) * smooth(0.6f + 0.26f * gloss, 1f, u)
        com.karapp.drawguidance.data.model.TubeMaterial.METAL -> (0.25f + 0.6f * gloss) * smooth(0.78f, 1f, u)
        com.karapp.drawguidance.data.model.TubeMaterial.GLASS -> (0.55f + 0.45f * gloss) * smooth(0.8f, 1f, u)
    }

    private fun halves(s: SampleBuffer): FloatArray = FloatArray(s.size) { halfAt(s, it) }

    /** Bir bandın örnekler arası dörtgenleri (bant merkezi ışığa doğru kaydırılmış); [segs] verilirse yalnız o aralıklar. */
    private fun bandPath(s: SampleBuffer, h: FloatArray, hf: Float, off: Float, dx: Float, dy: Float, segs: IntArray? = null, nSegs: Int = 0, lat: Float = 0f): Path {
        val path = Path().apply { fillType = Path.FillType.WINDING }
        val n = if (segs == null) s.size - 1 else nSegs
        for (q in 0 until n) {
            val i = if (segs == null) q + 1 else segs[q]
            // Aynı yerdeki örnekler (çizgi sonunda tamamlanan) sıfır alanlı dörtgen olur; kenar yumuşatması onları
            // ince bir çizgi gibi boyar: atlanır
            if (abs(s.x[i] - s.x[i - 1]) + abs(s.y[i] - s.y[i - 1]) < 0.01f) continue
            val ha = h[i - 1] * hf; val hb = h[i] * hf
            val ax = s.x[i - 1] + (lx * off + dx - s.dy[i - 1] * lat) * h[i - 1]; val ay = s.y[i - 1] + (ly * off + dy + s.dx[i - 1] * lat) * h[i - 1]
            val bx = s.x[i] + (lx * off + dx - s.dy[i] * lat) * h[i]; val by = s.y[i] + (ly * off + dy + s.dx[i] * lat) * h[i]
            path.moveTo(ax - s.dy[i - 1] * ha, ay + s.dx[i - 1] * ha)
            path.lineTo(bx - s.dy[i] * hb, by + s.dx[i] * hb)
            path.lineTo(bx + s.dy[i] * hb, by - s.dx[i] * hb)
            path.lineTo(ax + s.dy[i - 1] * ha, ay - s.dx[i - 1] * ha)
            path.close()
        }
        return path
    }

    /** Uç (yarım küre): her bant için bir daire. */
    private fun capDisk(path: Path, s: SampleBuffer, h: FloatArray, i: Int, hf: Float, off: Float, dx: Float, dy: Float, lat: Float = 0f) {
        path.addCircle(
            s.x[i] + (lx * off + dx - s.dy[i] * lat) * h[i], s.y[i] + (ly * off + dy + s.dx[i] * lat) * h[i],
            max(0.3f, h[i] * hf), Path.Direction.CW,
        )
    }

    /**
     * Keskin dönüş: [i]-1'den [i]'ye yön çok değişiyorsa aradaki dörtgen bükülür, tüpün dış kenarında sivri köşe kalır.
     * İki örneğe de uç gibi daire konur (yuvarlak dirsek).
     */
    private fun sharpTurn(s: SampleBuffer, i: Int): Boolean =
        i >= 1 && s.dx[i - 1] * s.dx[i] + s.dy[i - 1] * s.dy[i] < SHARP_TURN_COS &&
            abs(s.x[i] - s.x[i - 1]) + abs(s.y[i] - s.y[i - 1]) >= 0.01f

    /**
     * Canlı çizimde bir parçanın yeniden çizdiği bölge. Tüp, bantlar tüm çizgi boyunca üst üste (önce en dış bant,
     * en son parlama) boyanarak gölgelenir; yeni örnekler bu yüzden önceki izin görünüşünü de değiştirir (önceki
     * parçanın basamaklı biten bant uçları, kıvrım ve kesişmelerde bantların iç içe geçmesi). Parça, son örneklerin
     * tüpünü ([at]'ten sona, uçtaki basamakları kapsayacak kadar geriden) bölge alır; bölgeye değen tüm dörtgenlerle
     * (çizginin eski kısımları dahil) kayıtlı çizimdeki bant sırasıyla kendi katmanında çizilir ve karodaki izin bu
     * bölgedeki kısmının yerine geçer (SRC). Böylece her piksel kayıtlı çizimle aynı olur; birleşim yerlerinde kenar
     * pikselleri iki kez boyanmaz, kıvrımlarda delik ya da kesik kenar kalmaz. Bölge sınırı (tüp kenarının 1,5 px
     * dışı) iki yanında aynı görüntüyü karıştırır.
     */
    private fun regionRebuild(
        seq: SampleBuffer, at: Int, trueEnd: Boolean,
        /** Gölge için sürekli örnekler (bu parçanın yeni kısmı) ve başı/sonu gerçek uç mu. */
        shadowSrc: SampleBuffer, shadowStart: Boolean,
        fullShadow: Boolean,
    ): StrokeChunk? {
        if (seq.size == 0) return null
        val last = seq.size - 1
        val region = tubeRegion(seq, at, last)
        // Bölgenin kutusu; ona değen dörtgenler seçilir (tüp yarı genişliği kadar payla)
        val rb = BoundsBuilder()
        for (i in at..last) rb.add(seq.x[i], seq.y[i])
        val reach = maxHalf * 2f + 4f
        val l = rb.l - reach; val t = rb.t - reach; val r = rb.r + reach; val bt = rb.b + reach
        fun near(i: Int) = seq.x[i] >= l && seq.x[i] <= r && seq.y[i] >= t && seq.y[i] <= bt
        // Diziler parçalar arasında yeniden kullanılır (her karede çizginin uzunluğu kadar bellek ayrılmasın)
        if (segBuf.size < seq.size) {
            val n = max(seq.size, segBuf.size * 2)
            segBuf = IntArray(n); hBuf = FloatArray(n); hStamp = IntArray(n)
        }
        val segs = segBuf
        var nSegs = 0
        val h = hBuf
        val stamp = ++stampNo
        fun need(i: Int) { if (hStamp[i] != stamp) { h[i] = halfAt(seq, i); hStamp[i] = stamp } }
        for (i in 1..last) {
            // Dörtgen iki uç örneğinin tüpü içinde: uçlardan biri kutuya yakınsa ya da dörtgen kutuyu kesiyorsa
            val x0 = min(seq.x[i - 1], seq.x[i]); val x1 = max(seq.x[i - 1], seq.x[i])
            val y0 = min(seq.y[i - 1], seq.y[i]); val y1 = max(seq.y[i - 1], seq.y[i])
            if (x1 < l || x0 > r || y1 < t || y0 > bt) continue
            segs[nSegs++] = i
            need(i - 1); need(i)
        }
        val startCap = near(0) || last == 0
        val endCap = trueEnd
        if (startCap) need(0)
        if (endCap) need(last)
        // Keskin dönüşlerdeki dirsek daireleri (seçili dörtgenlerin)
        var nJoins = 0
        for (q in 0 until nSegs) if (sharpTurn(seq, segs[q])) nJoins++
        val joins = IntArray(nJoins)
        run { var j = 0; for (q in 0 until nSegs) if (sharpTurn(seq, segs[q])) joins[j++] = segs[q] }
        val bodyOps = ArrayList<(Canvas) -> Unit>(BANDS + 1)
        fun caps(hf: Float, dx: Float, dy: Float, off: Float, lat: Float = 0f): Path? {
            if (!startCap && !endCap && nJoins == 0) return null
            val p = Path()
            if (startCap) capDisk(p, seq, h, 0, hf, off, dx, dy, lat)
            if (endCap) capDisk(p, seq, h, last, hf, off, dx, dy, lat)
            for (i in joins) { capDisk(p, seq, h, i - 1, hf, off, dx, dy, lat); capDisk(p, seq, h, i, hf, off, dx, dy, lat) }
            return p
        }
        for (k in 0 until nBands) {
            val path = bandPath(seq, h, bandHalf[k], bandOff[k], 0f, 0f, segs, nSegs, bandLat[k])
            val cap = caps(bandHalf[k], 0f, 0f, bandOff[k], bandLat[k])
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = bandColor[k]; this.style = Paint.Style.FILL }
            bodyOps += { c -> c.drawPath(path, paint); if (cap != null) c.drawPath(cap, paint) }
        }
        if (glass) {
            val path = bandPath(seq, h, 0.14f, 0f, -lx * 0.55f, -ly * 0.55f, segs, nSegs)
            val cap = caps(0.14f, -lx * 0.55f, -ly * 0.55f, 0f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = mix(opaqueColor, WHITE, 0.45f); this.style = Paint.Style.FILL }
            bodyOps += { c -> c.drawPath(path, paint); if (cap != null) c.drawPath(cap, paint) }
        }
        val bounds = BoundsBuilder()
        for (i in at..last) bounds.add(seq.x[i], seq.y[i])
        for (i in 0 until shadowSrc.size) bounds.add(shadowSrc.x[i], shadowSrc.y[i])
        if (fullShadow) for (i in 0..last) bounds.add(seq.x[i], seq.y[i])
        val rect = bounds.toRect(pad)
        val layerRect = RectF(rb.l - maxHalf - 4f, rb.t - maxHalf - 4f, rb.r + maxHalf + 4f, rb.b + maxHalf + 4f)
        val body: List<(Canvas) -> Unit> = listOf { c ->
            val restore = c.save()
            c.clipPath(region)
            c.saveLayer(layerRect, srcPaint)
            for (op in bodyOps) op(c)
            c.restoreToCount(restore)
        }
        val shadowOps = shadowOps(if (fullShadow) seq else shadowSrc, start = fullShadow || shadowStart, end = trueEnd)
        return StrokeChunk(rect, body, shadowOps, replaceShadow = fullShadow)
    }

    private val srcPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    private var segBuf = IntArray(64)
    private var hBuf = FloatArray(64)
    private var hStamp = IntArray(64)
    private var stampNo = 0
    /** Kuyruklu örnek dizisi için yeniden kullanılan tampon. */
    private val seqBuf = SampleBuffer()

    /** [a]..[b] örneklerinin tüpü (örnekteki yarı genişlik + kenar yumuşatma payı: kenar pikselleri tam kapsansın). */
    private fun tubeRegion(s: SampleBuffer, a: Int, b: Int): Path {
        fun r(i: Int) = halfAt(s, i) + 1.5f
        val quads = Path().apply { fillType = Path.FillType.WINDING }
        for (i in a + 1..b) {
            if (abs(s.x[i] - s.x[i - 1]) + abs(s.y[i] - s.y[i - 1]) < 0.01f) continue
            val ra = r(i - 1); val rb = r(i)
            quads.moveTo(s.x[i - 1] - s.dy[i - 1] * ra, s.y[i - 1] + s.dx[i - 1] * ra)
            quads.lineTo(s.x[i] - s.dy[i] * rb, s.y[i] + s.dx[i] * rb)
            quads.lineTo(s.x[i] + s.dy[i] * rb, s.y[i] - s.dx[i] * rb)
            quads.lineTo(s.x[i - 1] + s.dy[i - 1] * ra, s.y[i - 1] - s.dx[i - 1] * ra)
            quads.close()
        }
        val disks = Path().apply {
            // Uçlarda bantlar tüp ekseninden ışığa doğru kayık bitebilir: daireler onları da kapsar
            addCircle(s.x[a], s.y[a], r(a), Path.Direction.CW)
            addCircle(s.x[b], s.y[b], r(b), Path.Direction.CW)
            // Keskin dönüşlerdeki dirsek daireleri
            for (i in a + 1..b) if (sharpTurn(s, i)) {
                addCircle(s.x[i - 1], s.y[i - 1], r(i - 1), Path.Direction.CW)
                addCircle(s.x[i], s.y[i], r(i), Path.Direction.CW)
            }
        }
        if (quads.isEmpty) return disks
        quads.op(disks, Path.Op.UNION)
        return quads
    }

    /** Kağıda düşen gölge: tam kalınlıkta, ışığın tersine kayık (kalınlıkla orantılı), yumuşak kenarlı. */
    private fun shadowOps(src: SampleBuffer, start: Boolean, end: Boolean): List<(Canvas) -> Unit> {
        if (shadowAlpha <= 0f || src.size == 0) return emptyList()
        // Opak siyah çizildiği için yol ile uç daireleri tek yola eklenebilir (bulanıklık birlikte uygulanır)
        val k = shadowDist / max(0.5f, width / 2f)
        val hs = halves(src)
        val path = bandPath(src, hs, 1f, 0f, -lx * k, -ly * k)
        val p = Path()
        if (start || src.size == 1) capDisk(p, src, hs, 0, 1f, 0f, -lx * k, -ly * k)
        if (end) capDisk(p, src, hs, src.size - 1, 1f, 0f, -lx * k, -ly * k)
        for (i in 1 until src.size) if (sharpTurn(src, i)) {
            capDisk(p, src, hs, i - 1, 1f, 0f, -lx * k, -ly * k); capDisk(p, src, hs, i, 1f, 0f, -lx * k, -ly * k)
        }
        if (!p.isEmpty) path.op(p, Path.Op.UNION)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFF000000.toInt(); this.style = Paint.Style.FILL
            if (blur >= 0.5f) maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        return listOf { c -> c.drawPath(path, paint) }
    }

    /** Kayıtlı çizim (tüm örnekler, iki uç). */
    private fun buildStatic(s: SampleBuffer): StrokeChunk? {
        if (s.size == 0) return null
        val h = halves(s)
        val bounds = BoundsBuilder()
        for (i in 0 until s.size) bounds.add(s.x[i], s.y[i])
        val ops = ArrayList<(Canvas) -> Unit>(BANDS + 1)
        fun caps(hf: Float, dx: Float, dy: Float, off: Float, lat: Float = 0f): Path = Path().also { p ->
            capDisk(p, s, h, 0, hf, off, dx, dy, lat)
            capDisk(p, s, h, s.size - 1, hf, off, dx, dy, lat)
            for (i in 1 until s.size) if (sharpTurn(s, i)) { capDisk(p, s, h, i - 1, hf, off, dx, dy, lat); capDisk(p, s, h, i, hf, off, dx, dy, lat) }
        }
        for (k in 0 until nBands) {
            val path = bandPath(s, h, bandHalf[k], bandOff[k], 0f, 0f, lat = bandLat[k])
            val cap = caps(bandHalf[k], 0f, 0f, bandOff[k], bandLat[k])
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = bandColor[k]; this.style = Paint.Style.FILL }
            ops += { c -> c.drawPath(path, paint); c.drawPath(cap, paint) }
        }
        if (glass) {
            // Camda ışığın karşı yanında kırılan ışıktan ikinci, sönük parlama
            val path = bandPath(s, h, 0.14f, 0f, -lx * 0.55f, -ly * 0.55f)
            val cap = caps(0.14f, -lx * 0.55f, -ly * 0.55f, 0f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = mix(opaqueColor, WHITE, 0.45f); this.style = Paint.Style.FILL }
            ops += { c -> c.drawPath(path, paint); c.drawPath(cap, paint) }
        }
        return StrokeChunk(bounds.toRect(pad), ops, shadowOps(s, start = true, end = true))
    }

    /** Bantların en geniş yarı genişliği (eğimle genişleme dahil). */
    private val maxHalf get() = width / 2f * dynamics.maxWidth
    /** Yeniden çizilen bölgenin başı, önceki parçanın bittiği yerden bu kadar geride: basamaklı bant uçları kapsansın. */
    private val clipBack get() = maxHalf * 0.7f + 3f
    /** Gölgenin bulanık başlangıcı önceki (opak) gölgenin içinde kalsın. */
    private val shadowBack get() = shadowDist * dynamics.maxWidth + blur * 2.6f + 3f

    /** [from] örneğinden çizgi boyunca en az [dist] gerideki örnek. */
    private fun backFrom(s: SampleBuffer, from: Int, dist: Float): Int {
        val f = from.coerceIn(0, max(0, s.size - 1))
        var i = f
        while (i > 0 && s.along[f] - s.along[i] < dist) i--
        return i
    }

    /** Önceki parçaların devamı: [drawnUntil]'den [all] sonuna (+ geçici kuyruk [extra]). */
    private fun continuation(all: SampleBuffer, extra: SampleBuffer?, trueEnd: Boolean, fullShadow: Boolean = false): StrokeChunk? {
        val joint = drawnUntil - 1
        val seq = if (extra == null || extra.size == 0) all else seqBuf.also { b ->
            b.clear()
            for (i in 0 until all.size) b.addFrom(all, i)
            for (i in 0 until extra.size) b.addFrom(extra, i)
        }
        if (seq.size == 0) return null
        val at = if (joint < 0) 0 else backFrom(all, joint, clipBack)
        // Gölge: yeni kısım, bulanık başı önceki gölgenin içinde kalacak kadar geriden
        val sFrom = if (joint < 0) 0 else backFrom(all, joint, shadowBack)
        val shadow = SampleBuffer().also { b -> for (i in sFrom until seq.size) b.addFrom(seq, i) }
        // Çizim bitince gölge çizginin tamamından yeniden çizilir: kayıtlı çizimle birebir aynı (canlıdaki gölge
        // parçalarının bulanık kenarları üst üste binip koyulaşmış olabilir)
        return regionRebuild(seq, at, trueEnd, shadow, shadowStart = sFrom == 0, fullShadow = fullShadow)
    }

    private var endDrawn = false

    override fun takeFinalRaw(minBatch: Int): StrokeChunk? {
        if (count == 0 || endDrawn) return null
        if (ended) sampler.finish(px, py, count) else sampler.advance(px, py, count)
        val s = sampler.samples
        if (s.size == 0) return null
        // Bitişte yeni örnek olmasa da uç (yarım küre) çizilir
        if (!ended && (s.size <= drawnUntil || s.size - drawnUntil < minBatch)) return null
        val chunk = if (drawnUntil == 0 && ended) buildStatic(s)
        else continuation(s, null, trueEnd = ended, fullShadow = ended && shadowAlpha > 0f && drawnUntil > 0)
        drawnUntil = s.size
        if (ended) endDrawn = true
        return chunk
    }

    override fun tailRaw(): StrokeChunk? {
        if (ended || count == 0) return null
        sampler.advance(px, py, count)
        val s = sampler.samples
        val extra = SampleBuffer()
        for (i in drawnUntil until s.size) extra.addFrom(s, i)
        sampler.tail(px, py, count, extra)
        if (drawnUntil == 0) return if (extra.size == 0) null else continuation(SampleBuffer(), extra, trueEnd = true)
        return continuation(s, extra, trueEnd = true)
    }

    private companion object {
        const val BANDS = 16
        /** Boya izindeki kıl şeritlerinin sayısı. */
        const val PAINT_LANES = 15
        /** Ardışık örnek yönleri arasındaki açı bundan büyükse (~30°) dirsek dairesi konur. */
        const val SHARP_TURN_COS = 0.866f
        const val WHITE = 0xFFFFFFFF.toInt()
        val METAL_CURVE = floatArrayOf(0f, 0.28f, 0.28f, 1.08f, 0.52f, 0.55f, 0.78f, 1.22f, 1f, 1.35f)
        val GLASS_CURVE = floatArrayOf(0f, 0.5f, 0.14f, 0.95f, 0.5f, 1.12f, 0.85f, 1.2f, 1f, 1.3f)

        /** (u, değer) çiftlerinden doğrusal ara değer. */
        fun curve(u: Float, pts: FloatArray): Float {
            var i = 0
            while (i + 2 < pts.size && u > pts[i + 2]) i += 2
            if (i + 2 >= pts.size) return pts[pts.size - 1]
            val u0 = pts[i]; val v0 = pts[i + 1]; val u1 = pts[i + 2]; val v1 = pts[i + 3]
            val t = if (u1 > u0) ((u - u0) / (u1 - u0)).coerceIn(0f, 1f) else 0f
            return v0 + (v1 - v0) * t
        }

        fun smooth(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / max(1e-4f, b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Temel rengi karart (l < 1) ya da beyaza doğru aç (l > 1). */
        fun shade(c: Int, l: Float): Int = if (l <= 1f) {
            val f = l.coerceIn(0f, 1f)
            (0xFF shl 24) or (((c shr 16 and 0xFF) * f).roundToInt() shl 16) or (((c shr 8 and 0xFF) * f).roundToInt() shl 8) or ((c and 0xFF) * f).roundToInt()
        } else mix(c, WHITE, (l - 1f).coerceIn(0f, 1f))

        fun mix(a: Int, b: Int, t: Float): Int {
            val f = t.coerceIn(0f, 1f)
            fun ch(shift: Int) = ((a shr shift and 0xFF) + ((b shr shift and 0xFF) - (a shr shift and 0xFF)) * f).roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
