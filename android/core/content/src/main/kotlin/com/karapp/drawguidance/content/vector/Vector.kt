package com.karapp.drawguidance.content.vector

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Birim kutuda bir nokta: x sağa, y aşağı; kutu 0..1 (taşma serbest, ör. gölge). */
data class P(val x: Float, val y: Float) {
    operator fun plus(o: P) = P(x + o.x, y + o.y)
    operator fun minus(o: P) = P(x - o.x, y - o.y)
    operator fun times(k: Float) = P(x * k, y * k)
}

/** Renk yerine yazılabilen özel değer: elemanın kalem (kenar) rengi. */
const val INK_COLOR = "@ink"

/** Parçanın çizgi biçimi. */
enum class LineStyle { SOLID, DASHED, DOTTED }

/**
 * Vektör çizimin tek parçası (uygulamada şeklin bir alt yolu). Renkler "#RRGGBB".
 *
 * - [stroke] null: elemanın kalem (kenar) rengi; [fill] null: elemanın dolgusu (varsa).
 * - [width] elemanın kenar kalınlığına göre çarpan; 0 = kenar çizilmez (yalnız dolgu).
 * - [label] verilirse parça tek noktalık yazı çapasıdır (yazının sol-üstü); [labelSize] kutunun kısa kenarına oranı.
 */
data class VPath(
    val points: List<P>,
    val closed: Boolean = false,
    val fill: String? = null,
    val stroke: String? = null,
    val width: Float = 1f,
    val line: LineStyle = LineStyle.SOLID,
    val alpha: Float = 1f,
    val label: String? = null,
    val labelSize: Float = 0f,
    /**
     * Uygulamanın parçayı tanıması için etiket (ör. grafikte "tick:x:3" çentik yazısı, "curve:0" eğri). Çizimi etkilemez;
     * kayıtta parçayla saklanır, dokunulan parçanın ne olduğunu bulmakta kullanılır.
     */
    val tag: String? = null,
)

/** 2B afin dönüşüm (grup içindeki çizimler için). */
class Affine(
    val a: Float = 1f, val b: Float = 0f, val c: Float = 0f,
    val d: Float = 1f, val e: Float = 0f, val f: Float = 0f,
) {
    fun map(p: P) = P(a * p.x + c * p.y + e, b * p.x + d * p.y + f)
    /** Önce [inner] sonra bu. */
    fun then(inner: Affine) = Affine(
        a * inner.a + c * inner.b, b * inner.a + d * inner.b,
        a * inner.c + c * inner.d, b * inner.c + d * inner.d,
        a * inner.e + c * inner.f + e, b * inner.e + d * inner.f + f,
    )
    /** Ortalama ölçek (yazı boyu için). */
    val scale: Float get() = kotlin.math.sqrt(abs(a * d - b * c))

    companion object {
        val Identity = Affine()
        fun translate(dx: Float, dy: Float) = Affine(e = dx, f = dy)
        fun scale(sx: Float, sy: Float = sx) = Affine(a = sx, d = sy)
        fun rotate(degrees: Float, px: Float = 0f, py: Float = 0f): Affine {
            val r = Math.toRadians(degrees.toDouble())
            val cs = cos(r).toFloat(); val sn = sin(r).toFloat()
            return Affine(cs, sn, -sn, cs, px - cs * px + sn * py, py - sn * px - cs * py)
        }
    }
}

/** Yeni parçalara uygulanan varsayılan biçim ([VectorBuilder.style] ile değişir). */
data class PathStyle(
    val fill: String? = null,
    val stroke: String? = null,
    val width: Float = 1f,
    val line: LineStyle = LineStyle.SOLID,
    val alpha: Float = 1f,
)

/**
 * Birim kutuda vektör çizim dili. Her çağrı bir parça ekler ve biçimi zincirle değiştirilebilen bir [Part] döner:
 *
 * ```
 * rect(0.1f, 0.4f, 0.8f, 0.6f).fill("#F4D35E")
 * circle(0.5f, 0.3f, 0.2f).fill("#FFFFFF").width(1.5f)
 * poly(0.1f, 0.4f, 0.5f, 0.05f, 0.9f, 0.4f, closed = true).fill("#C0392B")
 * text(0.42f, 0.9f, "H₂O", 0.08f)
 * ```
 *
 * [resolution]: çizimin ekrandaki yaklaşık boyu (piksel); eğriler buna göre yeterli sıklıkta örneklenir.
 */
class VectorBuilder(val resolution: Float = 512f) {
    private val out = ArrayList<VPath>()
    private var transform: Affine = Affine.Identity
    private var current = PathStyle()

    val paths: List<VPath> get() = out

    /** Zincirle biçim verilen parça. */
    inner class Part internal constructor(private val index: Int) {
        private fun edit(block: (VPath) -> VPath): Part { out[index] = block(out[index]); return this }
        fun fill(hex: String?) = edit { it.copy(fill = hex) }
        fun stroke(hex: String?) = edit { it.copy(stroke = hex) }
        fun width(scale: Float) = edit { it.copy(width = scale) }
        fun noStroke() = edit { it.copy(width = 0f) }
        fun dashed() = edit { it.copy(line = LineStyle.DASHED) }
        fun dotted() = edit { it.copy(line = LineStyle.DOTTED) }
        fun alpha(value: Float) = edit { it.copy(alpha = value) }
        /** Dolgu ve kenar aynı renk. */
        fun solid(hex: String) = edit { it.copy(fill = hex, stroke = hex) }
        fun tag(value: String) = edit { it.copy(tag = value) }
    }

    private fun add(points: List<P>, closed: Boolean): Part {
        val mapped = points.map { transform.map(it) }
        out += VPath(mapped, closed, current.fill, current.stroke, current.width, current.line, current.alpha)
        return Part(out.size - 1)
    }

    /** Hazır bir parçayı (ör. başka kütüphaneden) dönüşümle ekler. */
    fun addPath(path: VPath): Part {
        val mapped = path.points.map { transform.map(it) }
        out += path.copy(points = mapped, labelSize = path.labelSize * transform.scale)
        return Part(out.size - 1)
    }

    /** [block] içindeki parçaların varsayılan biçimi. */
    fun style(
        fill: String? = current.fill,
        stroke: String? = current.stroke,
        width: Float = current.width,
        line: LineStyle = current.line,
        alpha: Float = current.alpha,
        block: VectorBuilder.() -> Unit,
    ) {
        val saved = current
        current = PathStyle(fill, stroke, width, line, alpha)
        try { block() } finally { current = saved }
    }

    /** Dönüşümlü grup: önce ölçek (px,py çevresinde), sonra döndürme, sonra öteleme. */
    fun group(
        dx: Float = 0f, dy: Float = 0f,
        sx: Float = 1f, sy: Float = sx,
        rotate: Float = 0f,
        px: Float = 0.5f, py: Float = 0.5f,
        block: VectorBuilder.() -> Unit,
    ) {
        var t = Affine.translate(-px, -py)
        t = Affine.scale(sx, sy).then(t)
        if (rotate != 0f) t = Affine.rotate(rotate).then(t)
        t = Affine.translate(px + dx, py + dy).then(t)
        withTransform(t, block)
    }

    /** Birim kutuyu [x],[y],[w],[h] dikdörtgenine eşleyen grup (alt çizimi bir bölgeye yerleştirir). */
    fun inBox(x: Float, y: Float, w: Float, h: Float, rotate: Float = 0f, block: VectorBuilder.() -> Unit) {
        var t = Affine(a = w, d = h, e = x, f = y)
        if (rotate != 0f) t = Affine.rotate(rotate, x + w / 2f, y + h / 2f).then(t)
        withTransform(t, block)
    }

    fun withTransform(t: Affine, block: VectorBuilder.() -> Unit) {
        val saved = transform
        transform = saved.then(t)
        try { block() } finally { transform = saved }
    }

    // --- Temel parçalar ---

    /** Noktalardan kırık çizgi: x1, y1, x2, y2, ... */
    fun poly(vararg xy: Float, closed: Boolean = false): Part {
        require(xy.size >= 4 && xy.size % 2 == 0) { "poly: en az iki nokta (çift sayıda değer) gerekli" }
        return add(List(xy.size / 2) { P(xy[it * 2], xy[it * 2 + 1]) }, closed)
    }

    fun points(list: List<P>, closed: Boolean = false): Part = add(list, closed)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float): Part = add(listOf(P(x1, y1), P(x2, y2)), false)

    fun rect(x: Float, y: Float, w: Float, h: Float): Part =
        add(listOf(P(x, y), P(x + w, y), P(x + w, y + h), P(x, y + h)), true)

    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): Part {
        val rr = min(r, min(abs(w), abs(h)) / 2f)
        if (rr <= 1e-4f) return rect(x, y, w, h)
        val pts = ArrayList<P>()
        fun corner(cx: Float, cy: Float, from: Double) {
            val n = segments(rr * PI.toFloat() / 2f, 4, 24)
            for (i in 0..n) {
                val a = from + (PI / 2) * i / n
                pts += P(cx + rr * cos(a).toFloat(), cy + rr * sin(a).toFloat())
            }
        }
        corner(x + w - rr, y + rr, -PI / 2)
        corner(x + w - rr, y + h - rr, 0.0)
        corner(x + rr, y + h - rr, PI / 2)
        corner(x + rr, y + rr, PI)
        return add(pts, true)
    }

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, rotate: Float = 0f): Part {
        val n = segments(PI.toFloat() * (rx + ry), 20, 720)
        val r = Math.toRadians(rotate.toDouble())
        val cr = cos(r).toFloat(); val sr = sin(r).toFloat()
        return add(List(n) { i ->
            val a = 2 * PI * i / n
            val ex = rx * cos(a).toFloat(); val ey = ry * sin(a).toFloat()
            P(cx + ex * cr - ey * sr, cy + ex * sr + ey * cr)
        }, true)
    }

    fun circle(cx: Float, cy: Float, r: Float): Part = ellipse(cx, cy, r, r)

    /**
     * Elips yayı: [start] ve [sweep] derece (0° = sağ, saat yönü pozitif çünkü y aşağı).
     * [pie] true ise merkezle kapatılır (dilim), [closed] true ise uçları birleştirilir (kesit).
     */
    fun arc(cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float, pie: Boolean = false, closed: Boolean = false): Part {
        val n = segments(abs(sweep) / 360f * PI.toFloat() * (rx + ry), 6, 720)
        val pts = ArrayList<P>(n + 2)
        for (i in 0..n) {
            val a = Math.toRadians((start + sweep * i / n).toDouble())
            pts += P(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }
        if (pie) pts += P(cx, cy)
        return add(pts, pie || closed)
    }

    /** Düzgün çokgen ([rotate] derece; 0'da ilk köşe tepede). */
    fun polygon(cx: Float, cy: Float, r: Float, sides: Int, rotate: Float = 0f, ry: Float = r): Part =
        add(List(sides) { i ->
            val a = Math.toRadians((-90.0 + rotate + 360.0 * i / sides))
            P(cx + r * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }, true)

    fun star(cx: Float, cy: Float, outer: Float, inner: Float, points: Int = 5, rotate: Float = 0f): Part =
        add(List(points * 2) { i ->
            val a = Math.toRadians(-90.0 + rotate + 180.0 * i / points)
            val r = if (i % 2 == 0) outer else inner
            P(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }, true)

    /** Parametrik eğri, t ∈ [0,1]. */
    fun curve(samples: Int = 0, closed: Boolean = false, f: (Float) -> P): Part {
        val n = if (samples > 0) samples else segments(1.5f, 48, 480)
        val count = if (closed) n else n + 1
        return add(List(count) { f(it / n.toFloat()) }, closed)
    }

    /** Noktalardan geçen yumuşak eğri (Catmull-Rom): x1, y1, x2, y2, ... */
    fun smooth(vararg xy: Float, closed: Boolean = false, tension: Float = 0.5f): Part {
        val pts = List(xy.size / 2) { P(xy[it * 2], xy[it * 2 + 1]) }
        return add(catmullRom(pts, closed, tension), closed)
    }

    /** Yol kurucu: moveTo/lineTo/quadTo/cubicTo/arcTo; tek parça. */
    fun path(closed: Boolean = false, block: PathBuilder.() -> Unit): Part {
        val builder = PathBuilder(this)
        builder.block()
        return add(builder.points, closed || builder.isClosed)
    }

    /** Ok: gövde + uç (ayrı iki parça); [head] kutu birimi. */
    fun arrow(x1: Float, y1: Float, x2: Float, y2: Float, head: Float = 0.06f, double: Boolean = false, filledHead: Boolean = false): Part {
        val body = line(x1, y1, x2, y2)
        arrowHead(x2, y2, x2 - x1, y2 - y1, head, filledHead)
        if (double) arrowHead(x1, y1, x1 - x2, y1 - y2, head, filledHead)
        return body
    }

    /** [x],[y] ucunda, ([dx],[dy]) yönüne bakan ok ucu. */
    fun arrowHead(x: Float, y: Float, dx: Float, dy: Float, size: Float = 0.06f, filled: Boolean = false): Part {
        val len = hypot(dx, dy).coerceAtLeast(1e-6f)
        val ux = dx / len; val uy = dy / len
        val nx = -uy; val ny = ux
        val bx = x - ux * size; val by = y - uy * size
        val pts = listOf(P(bx + nx * size * 0.5f, by + ny * size * 0.5f), P(x, y), P(bx - nx * size * 0.5f, by - ny * size * 0.5f))
        val part = add(pts, filled)
        if (filled) part.fill(current.stroke ?: INK)
        return part
    }

    /** Yazı: [x],[y] yazının sol-üstü, [size] kutunun kısa kenarına oranla yazı boyu. [color] null: kalem rengi. */
    fun text(x: Float, y: Float, text: String, size: Float = 0.08f, color: String? = null): Part {
        val p = transform.map(P(x, y))
        out += VPath(listOf(p), stroke = color ?: current.stroke, label = text, labelSize = size * transform.scale, alpha = current.alpha)
        return Part(out.size - 1)
    }

    /** Ortalanmış yazı (genişlik yaklaşık: harf başına 0.55·boy). */
    fun textCentered(cx: Float, cy: Float, text: String, size: Float = 0.08f, color: String? = null): Part =
        text(cx - text.length * size * 0.55f / 2f, cy - size / 2f, text, size, color)

    /** Kutu birimindeki uzunluk için eğri örnek sayısı (ekrandaki boya göre). */
    fun segments(unitLength: Float, minN: Int, maxN: Int): Int =
        ceil(abs(unitLength) * resolution * transform.scale / 6f).toInt().coerceIn(minN, maxN)

    companion object {
        /**
         * Renk yerine yazılabilen özel değer: elemanın kalem (kenar) rengi. Ör. ok ucunu kalem rengiyle doldurmak için
         * `fill(INK)`. Önizlemede #1E1E1E çizilir.
         */
        const val INK = INK_COLOR

        fun catmullRom(pts: List<P>, closed: Boolean, tension: Float = 0.5f, steps: Int = 12): List<P> {
            if (pts.size < 3) return pts
            val out = ArrayList<P>()
            val n = pts.size
            val last = if (closed) n else n - 1
            for (i in 0 until last) {
                val p0 = pts[if (closed) (i - 1 + n) % n else max(0, i - 1)]
                val p1 = pts[i]
                val p2 = pts[(i + 1) % n]
                val p3 = pts[if (closed) (i + 2) % n else min(n - 1, i + 2)]
                for (s in 0 until steps) {
                    val t = s / steps.toFloat()
                    out += catmull(p0, p1, p2, p3, t, tension)
                }
            }
            if (!closed) out += pts.last()
            return out
        }

        private fun catmull(p0: P, p1: P, p2: P, p3: P, t: Float, tension: Float): P {
            val t2 = t * t; val t3 = t2 * t
            val m1 = (p2 - p0) * tension
            val m2 = (p3 - p1) * tension
            val h00 = 2 * t3 - 3 * t2 + 1
            val h10 = t3 - 2 * t2 + t
            val h01 = -2 * t3 + 3 * t2
            val h11 = t3 - t2
            return P(
                h00 * p1.x + h10 * m1.x + h01 * p2.x + h11 * m2.x,
                h00 * p1.y + h10 * m1.y + h01 * p2.y + h11 * m2.y,
            )
        }
    }
}

/** [VectorBuilder.path] için yol kurucu (tek parça). */
class PathBuilder internal constructor(private val owner: VectorBuilder) {
    internal val points = ArrayList<P>()
    internal var isClosed = false
    private var cursor = P(0f, 0f)

    fun moveTo(x: Float, y: Float) { cursor = P(x, y); if (points.isEmpty()) points += cursor else points += cursor }
    fun lineTo(x: Float, y: Float) { if (points.isEmpty()) points += cursor; cursor = P(x, y); points += cursor }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) {
        if (points.isEmpty()) points += cursor
        val p0 = cursor
        val n = owner.segments(hypot(x - p0.x, y - p0.y) + hypot(cx - p0.x, cy - p0.y), 6, 96)
        for (i in 1..n) {
            val t = i / n.toFloat(); val m = 1 - t
            points += P(m * m * p0.x + 2 * m * t * cx + t * t * x, m * m * p0.y + 2 * m * t * cy + t * t * y)
        }
        cursor = P(x, y)
    }

    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
        if (points.isEmpty()) points += cursor
        val p0 = cursor
        val approx = hypot(c1x - p0.x, c1y - p0.y) + hypot(c2x - c1x, c2y - c1y) + hypot(x - c2x, y - c2y)
        val n = owner.segments(approx, 8, 128)
        for (i in 1..n) {
            val t = i / n.toFloat(); val m = 1 - t
            val a = m * m * m; val b = 3 * m * m * t; val c = 3 * m * t * t; val d = t * t * t
            points += P(a * p0.x + b * c1x + c * c2x + d * x, a * p0.y + b * c1y + c * c2y + d * y)
        }
        cursor = P(x, y)
    }

    /** Merkezli elips yayı ile devam: [start]..[start]+[sweep] derece. */
    fun arcTo(cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float) {
        val n = owner.segments(abs(sweep) / 360f * PI.toFloat() * (rx + ry), 4, 360)
        for (i in 0..n) {
            val a = Math.toRadians((start + sweep * i / n).toDouble())
            val p = P(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
            if (i == 0 && points.isNotEmpty() && hypot(p.x - cursor.x, p.y - cursor.y) < 1e-4f) continue
            points += p
        }
        cursor = points.last()
    }

    fun close() { isClosed = true }
}

/** Parçaların birim kutudaki sınırları (yazı çapaları dahil değil). */
fun List<VPath>.bounds(): FloatArray {
    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
    forEach { p -> if (p.label == null) p.points.forEach { l = min(l, it.x); t = min(t, it.y); r = max(r, it.x); b = max(b, it.y) } }
    return floatArrayOf(l, t, r, b)
}
