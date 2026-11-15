package com.karapp.drawguidance.ui.drawing

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import com.karapp.drawguidance.content.comic.ComicTones
import com.karapp.drawguidance.data.model.ToneFill
import com.karapp.drawguidance.ui.common.hexToColor
import kotlin.math.roundToInt

/**
 * v53 çizgi roman ekran tonlarını ve desenlerini çizer (content.comic.ComicTones geometrisi). Üretilen öğeler şeklin
 * kutusuna ve tonun ayarlarına göre küçük bir LRU önbellekte tutulur (her karede yeniden üretilmez); önbellek öğe
 * sayısıyla sınırlıdır ve bellek uyarısında [trim] ile boşaltılır.
 */
object ToneRenderer {
    private data class Key(val tone: ToneFill, val l: Int, val t: Int, val r: Int, val b: Int)

    /** En çok ~120 bin öğe (her biri birkaç float): birkaç MB'ı geçmez. */
    private val cache = object : LruCache<Key, List<ComicTones.Prim>>(120_000) {
        override fun sizeOf(key: Key, value: List<ComicTones.Prim>): Int = value.size.coerceAtLeast(1)
    }
    init { com.karapp.drawguidance.platform.DeviceCaps.cache(editor = true) { synchronized(cache) { cache.evictAll() } } }

    private val paints = object : ThreadLocal<Paint>() { override fun initialValue() = Paint(Paint.ANTI_ALIAS_FLAG) }
    private val paths = object : ThreadLocal<Path>() { override fun initialValue() = Path() }

    fun prims(tone: ToneFill, l: Float, t: Float, r: Float, b: Float): List<ComicTones.Prim> {
        // Kutu 4 birimlik adımlarla yuvarlanır: taşırken önbellek isabeti yüksek kalır
        val key = Key(tone, (l / 4).roundToInt(), (t / 4).roundToInt(), (r / 4).roundToInt(), (b / 4).roundToInt())
        cache.get(key)?.let { return it }
        val made = ComicTones.prims(tone.id, tone.density, tone.scale, tone.angle, l, t, r, b)
        cache.put(key, made)
        return made
    }

    fun draw(canvas: Canvas, clip: Path, tone: ToneFill, l: Float, t: Float, r: Float, b: Float, alpha: Float) {
        val items = prims(tone, l, t, r, b)
        if (items.isEmpty()) return
        val paint = paints.get()!!
        val path = paths.get()!!
        val color = hexToColor(tone.colorHex).toArgb()
        val save = canvas.save()
        canvas.clipPath(clip)
        paint.reset()
        paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        for (p in items) {
            paint.color = color
            paint.alpha = (alpha * p.alpha * 255).roundToInt().coerceIn(0, 255)
            when (p) {
                is ComicTones.Dot -> { paint.style = Paint.Style.FILL; canvas.drawCircle(p.x, p.y, p.r, paint) }
                is ComicTones.Ring -> { paint.style = Paint.Style.STROKE; paint.strokeWidth = p.w; canvas.drawCircle(p.x, p.y, p.r, paint) }
                is ComicTones.Seg -> { paint.style = Paint.Style.STROKE; paint.strokeWidth = p.w; canvas.drawLine(p.x1, p.y1, p.x2, p.y2, paint) }
                is ComicTones.Poly -> {
                    if (p.pts.size < 4) continue
                    path.reset()
                    path.moveTo(p.pts[0], p.pts[1])
                    var i = 2
                    while (i + 1 < p.pts.size) { path.lineTo(p.pts[i], p.pts[i + 1]); i += 2 }
                    if (p.closed) path.close()
                    paint.style = if (p.filled) Paint.Style.FILL else Paint.Style.STROKE
                    paint.strokeWidth = p.w
                    canvas.drawPath(path, paint)
                }
            }
        }
        canvas.restoreToCount(save)
    }

    /** Bellek uyarısında önbelleği boşaltır. */
    fun trim() = cache.evictAll()
}
