package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.ui.graphics.BlendMode
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.StrokePoint
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Parmak hareket ederken çizilen çizginin ekrandaki katmanı.
 *
 * Kesinleşen parçalar bir kez, ekran koordinatlarındaki küçük karolara (256 px) yazılır; her karede
 * yalnızca karolar (değişmeyenler GPU'da önbellekte kalır) ve kısa geçici kuyruk çizilir. Böylece bir
 * karenin maliyeti çizginin uzunluğundan bağımsızdır. Çizim bitince karolar görünüm önbelleğine
 * doğrudan kopyalanabilir ([blitInto]); çizginin yeniden çizilmesi gerekmez.
 */
class LiveStrokeLayer {

    private var stream: StrokeStream? = null
    private val tiles = HashMap<Long, Bitmap>()
    /** v20 3B fırçanın kağıda düşen gölgesi: ayrı karolarda opak, birleştirirken saydam ve izin altında. */
    private val shadowTiles = HashMap<Long, Bitmap>()
    private val pool = ArrayList<Bitmap>()
    private val tileCanvas = Canvas()
    private val bitmapPaint = Paint()
    private val bounds = RectF()
    private var hasBounds = false
    private var viewWidth = 0
    private var viewHeight = 0

    var scale = 1f
        private set
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    /** Bu katmanın ait olduğu çizim kimliği. */
    var strokeId: String? = null
        private set

    /** Parmak kaldırıldı; katman önbelleğe aktarılmayı bekliyor. */
    var finished = false
        private set

    val isActive: Boolean get() = stream != null
    val isEraser: Boolean get() = stream?.isEraser == true

    private val minBatch: Int
        get() = if (stream?.type == BrushType.FLAT_BRUSH) 6 else 1

    /**
     * v18 düşük gecikme: kesinleşen parçalar ayrıca buraya da gönderilir (yalnızca opak, normal karışımlı fırçalar;
     * yarı saydam fırça ve silgi ön tampona çizilemez).
     */
    private var front: ((StrokeChunk) -> Unit)? = null

    internal fun start(
        id: String, type: BrushType, color: Int, sizePx: Float, opacity: Float, flow: Float, strength: Float,
        scale: Float, offsetX: Float, offsetY: Float, viewWidth: Int, viewHeight: Int,
        front: ((StrokeChunk) -> Unit)? = null,
        params: com.karapp.drawguidance.data.model.BrushParams? = null,
    ) {
        release()
        this.viewWidth = viewWidth
        this.viewHeight = viewHeight
        stream = StrokeStream.create(type, color, sizePx, opacity, id.hashCode(), flow, strength, eraserMask = true, params = params)
        this.front = front?.takeIf {
            stream?.let { s -> !s.isEraser && s.compositeAlpha >= 0.999f && s.blend == BlendMode.SrcOver && s.shadowAlpha <= 0f && !s.needsOwnLayer } == true
        }
        strokeId = id
        this.scale = scale
        this.offsetX = offsetX
        this.offsetY = offsetY
        finished = false
    }

    /**
     * Tuval koordinatında yeni nokta; kesinleşen kısım karolara yazılır. v18: kalemle çizerken [pressure] (0..1) ve
     * [tilt] (0 = dik, 1 = yatık) da verilir.
     */
    fun add(x: Float, y: Float, pressure: Float = 1f, tilt: Float = 0f, orient: Float = 0f) {
        val s = stream ?: return
        if (finished) return
        s.add(x, y, pressure, tilt, orient)
        s.takeFinal(minBatch)?.let(::paint)
    }

    /** Son eklenen nokta (tuval koordinatı) ve kalınlığı; hareket tahmininin çizimi için. */
    fun lastPoint(): StrokePoint? {
        val s = stream ?: return null
        if (s.count == 0) return null
        val i = s.count - 1
        return StrokePoint(s.pointsX()[i], s.pointsY()[i], s.pointsPressure()?.get(i) ?: 1f, s.pointsTilt()?.get(i) ?: 0f, s.pointsOrient()?.get(i) ?: 0f)
    }

    /** Canlı çizginin iz genişliği (tuval birimi) ve rengi. */
    val inkWidth: Float get() = stream?.width ?: 0f

    /** Parmak kalktı: kalan kısım kesinleşir. */
    fun finish() {
        val s = stream ?: return
        if (finished) return
        s.end()
        s.takeFinal(0)?.let(::paint)
        finished = true
    }

    fun points(): List<StrokePoint> {
        val s = stream ?: return emptyList()
        val xs = s.pointsX(); val ys = s.pointsY()
        val ps = s.pointsPressure(); val ts = s.pointsTilt(); val os = s.pointsOrient()
        return List(s.count) { StrokePoint(xs[it], ys[it], ps?.get(it) ?: 1f, ts?.get(it) ?: 0f, os?.get(it) ?: 0f) }
    }

    /** Karoları havuza bırakır (bir sonraki çizimde yeniden kullanılır). */
    fun release() {
        for (bitmap in tiles.values + shadowTiles.values) {
            if (pool.size < MAX_POOL) { bitmap.eraseColor(0); pool += bitmap } else bitmap.recycle()
        }
        tiles.clear()
        shadowTiles.clear()
        stream = null
        front = null
        strokeId = null
        finished = false
        hasBounds = false
    }

    private fun tileKey(tx: Int, ty: Int): Long = (tx.toLong() shl 32) or (ty.toLong() and 0xFFFFFFFFL)

    private fun paint(chunk: StrokeChunk) {
        val b = chunk.bounds
        val left = b.left * scale + offsetX
        val top = b.top * scale + offsetY
        val right = b.right * scale + offsetX
        val bottom = b.bottom * scale + offsetY
        // Yalnızca görünen alandaki karolar (ekran dışına taşan kısım çizilmez)
        val tx0 = max(floor(left / TILE).toInt(), 0); val tx1 = kotlin.math.min(floor(right / TILE).toInt(), (viewWidth - 1) / TILE)
        val ty0 = max(floor(top / TILE).toInt(), 0); val ty1 = kotlin.math.min(floor(bottom / TILE).toInt(), (viewHeight - 1) / TILE)
        if (tx1 < tx0 || ty1 < ty0) return
        if (!hasBounds) { bounds.set(left, top, right, bottom); hasBounds = true } else bounds.union(left, top, right, bottom)
        // Gölgenin tamamı yeniden çizilecek: eski gölge karoları temizlenir
        if (chunk.replaceShadow) for (bitmap in shadowTiles.values) bitmap.eraseColor(0)
        for (tx in tx0..tx1) for (ty in ty0..ty1) {
            val key = tileKey(tx, ty)
            if (chunk.hasShadow) {
                val shadow = shadowTiles.getOrPut(key) { pool.removeLastOrNull() ?: Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888) }
                drawInto(shadow, tx, ty) { chunk.drawShadow(it) }
            }
            val bitmap = tiles.getOrPut(key) {
                pool.removeLastOrNull() ?: Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
            }
            drawInto(bitmap, tx, ty) { chunk.draw(it) }
        }
        tileCanvas.setBitmap(null)
        front?.invoke(chunk)
    }

    private fun drawInto(bitmap: Bitmap, tx: Int, ty: Int, block: (Canvas) -> Unit) {
        tileCanvas.setBitmap(bitmap)
        val save = tileCanvas.save()
        tileCanvas.translate(-tx * TILE.toFloat() + offsetX, -ty * TILE.toFloat() + offsetY)
        tileCanvas.scale(scale, scale)
        block(tileCanvas)
        tileCanvas.restoreToCount(save)
    }

    private fun compositePaint(s: StrokeStream): Paint? = when {
        s.isEraser -> Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        s.compositeAlpha < 0.999f || s.blend != BlendMode.SrcOver || s.shadowAlpha > 0f || s.needsOwnLayer -> StrokeStream.layerPaint(s.compositeAlpha, s.blend)
        else -> null
    }

    /** Gölge karoları (ve kuyruğun gölgesi) saydamlıkla, izin altına. */
    private fun drawShadow(canvas: Canvas, s: StrokeStream, area: RectF, tail: StrokeChunk?) {
        if (s.shadowAlpha <= 0f || (shadowTiles.isEmpty() && tail?.hasShadow != true)) return
        val restore = canvas.saveLayer(area, s.shadowPaint())
        for ((key, bitmap) in shadowTiles) {
            canvas.drawBitmap(bitmap, (key shr 32).toInt() * TILE.toFloat(), key.toInt() * TILE.toFloat(), bitmapPaint)
        }
        if (tail != null && tail.hasShadow) {
            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)
            tail.drawShadow(canvas)
            canvas.restore()
        }
        canvas.restoreToCount(restore)
    }

    /**
     * Ekran koordinatlarında çizer (kağıt kırpması çağıranda). Silgi, altındaki çizim katmanından siler;
     * bu yüzden silgi çizerken çağıran bir çizim katmanı içinde olmalıdır.
     */
    fun drawTo(canvas: Canvas) {
        val s = stream ?: return
        val tail = if (finished) null else s.tail()
        val area = RectF()
        if (hasBounds) area.set(bounds)
        if (tail != null) {
            val tb = tail.bounds
            val l = tb.left * scale + offsetX; val t = tb.top * scale + offsetY
            val r = tb.right * scale + offsetX; val bt = tb.bottom * scale + offsetY
            if (hasBounds) area.union(l, t, r, bt) else area.set(l, t, r, bt)
        }
        if (area.isEmpty) return
        val composite = compositePaint(s)
        val restore = if (composite != null) canvas.saveLayer(area, composite) else canvas.save()
        drawShadow(canvas, s, area, tail)
        // v20 3B fırçanın kuyruğu altındaki karolardan (birleşim yerini) siler: gölgeyi silmesin diye iz ayrı katmanda
        val body = if (s.needsOwnLayer && s.shadowAlpha > 0f && tail != null) canvas.saveLayer(area, null) else -1
        drawTiles(canvas)
        if (tail != null) {
            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)
            tail.draw(canvas)
            canvas.restore()
        }
        if (body >= 0) canvas.restoreToCount(body)
        canvas.restoreToCount(restore)
    }

    private fun drawTiles(canvas: Canvas) {
        for ((key, bitmap) in tiles) {
            val tx = (key shr 32).toInt()
            val ty = key.toInt()
            canvas.drawBitmap(bitmap, tx * TILE.toFloat(), ty * TILE.toFloat(), bitmapPaint)
        }
    }

    /**
     * Bitmiş çizgiyi, aynı görünüm dönüşümüyle üretilmiş önbellek bitmap'ine birleştirir. Önbellekteki
     * çizimler şeffaf katmanda olduğundan silgi burada doğrudan siler.
     */
    fun blitInto(target: Canvas, clip: RectF) {
        val s = stream ?: return
        if (!finished || !hasBounds) return
        val area = RectF(bounds)
        if (!area.intersect(clip)) return
        target.save()
        target.clipRect(clip)
        val composite = compositePaint(s)
        val restore = if (composite != null) target.saveLayer(area, composite) else target.save()
        drawShadow(target, s, area, null)
        drawTiles(target)
        target.restoreToCount(restore)
        target.restore()
    }

    /** Katman bu görünüm dönüşümüyle mi çizildi? */
    fun matches(scale: Float, offsetX: Float, offsetY: Float): Boolean =
        this.scale == scale && this.offsetX == offsetX && this.offsetY == offsetY

    companion object {
        const val TILE = 256
        private const val MAX_POOL = 48
    }
}
