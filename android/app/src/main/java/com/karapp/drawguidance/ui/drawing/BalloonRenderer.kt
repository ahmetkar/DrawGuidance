package com.karapp.drawguidance.ui.drawing

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.karapp.drawguidance.data.model.BalloonSpec
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.BalloonTail
import com.karapp.drawguidance.data.model.GradientSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v27 çizgi roman konuşma balonları. Balon yazı kutusunun yerel düzleminde (0..w × 0..h) çizilir; kuyruk uçları
 * kutuya göre oranla ([BalloonTail.x], [BalloonTail.y]) saklandığından balon taşınınca, döndürülünce ve ölçeklenince
 * kuyruklar da onunla birlikte dönüşür. Gövde ve kuyruklar tek yolda birleştirilir (Path.op, API 19+): kenar
 * çizgisi kesintisizdir. Düşünce balonunda kuyruk küçülen kabarcıklardır.
 */
object BalloonRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    /** Balon biçimine göre yazının iç boşlukları (sol, üst, sağ, alt; tuval birimi). */
    fun pads(b: BalloonSpec, width: Float, size: Float): FloatArray {
        val spiky = b.style == BalloonStyle.SHOUT || b.style == BalloonStyle.FLASH || b.style == BalloonStyle.ROUGH
        val oval = spiky || b.style == BalloonStyle.SPEECH || b.style == BalloonStyle.WHISPER || b.style == BalloonStyle.THOUGHT ||
            b.style == BalloonStyle.WAVY || b.style == BalloonStyle.DOUBLE || b.style == BalloonStyle.ICICLE || b.style == BalloonStyle.DRIP
        // v36 serbest yazı: balon yok, yazının çevresinde az boşluk (seçim ve kuyruk tutamağı için)
        if (b.style == BalloonStyle.FREE) return floatArrayOf(size * 0.15f, size * 0.15f, size * 0.15f, size * 0.15f)
        if (b.style == BalloonStyle.HEART) {
            // Kalp: yazı iki lobun altında, sivri ucun üstünde
            val side = max(width * 0.2f, size * 0.7f)
            return floatArrayOf(side, size * 1.15f, side, size * 1.9f)
        }
        val side = when {
            spiky -> max(width * 0.2f, size * 0.6f)
            oval -> max(width * 0.15f, size * 0.6f)
            b.style == BalloonStyle.SCALLOP -> max(width * 0.09f, size * 0.7f)
            else -> max(width * 0.07f, size * 0.5f)
        }
        val top = when {
            spiky || b.style == BalloonStyle.THOUGHT -> size * 1.1f
            oval -> size * 0.85f
            b.style == BalloonStyle.SCALLOP -> size * 0.7f
            else -> size * 0.45f
        }
        // Buz saçağı ve damlalar altta: yazı biraz yukarıda
        val bottom = if (b.style == BalloonStyle.ICICLE || b.style == BalloonStyle.DRIP) top * 1.45f else top
        return floatArrayOf(side, top, side, bottom)
    }

    /** Gövde (kuyruksuz) yolu. */
    fun body(b: BalloonSpec, w: Float, h: Float): Path {
        val p = Path()
        when (b.style) {
            BalloonStyle.SPEECH, BalloonStyle.WHISPER -> p.addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
            BalloonStyle.ROUNDED, BalloonStyle.ELECTRIC -> {
                val r = min(w, h) * 0.24f
                p.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
            }
            BalloonStyle.NARRATION -> p.addRect(RectF(0f, 0f, w, h), Path.Direction.CW)
            BalloonStyle.THOUGHT -> cloud(p, w, h)
            BalloonStyle.SHOUT -> burst(p, w, h)
            BalloonStyle.WAVY -> wavy(p, w, h)
            BalloonStyle.DOUBLE -> p.addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
            BalloonStyle.ICICLE -> icicle(p, w, h)
            BalloonStyle.DRIP -> drip(p, w, h)
            BalloonStyle.SCALLOP -> scallop(p, w, h)
            BalloonStyle.FLASH -> radial(p, w, h, spikes = 44, inner = 0.84f, jitter = 0.08f)
            BalloonStyle.HEART -> heart(p, w, h)
            BalloonStyle.CUT -> {
                val c = min(w, h) * 0.2f
                p.moveTo(c, 0f); p.lineTo(w - c, 0f); p.lineTo(w, c); p.lineTo(w, h - c); p.lineTo(w - c, h)
                p.lineTo(c, h); p.lineTo(0f, h - c); p.lineTo(0f, c); p.close()
            }
            BalloonStyle.ROUGH -> rough(p, w, h)
            BalloonStyle.PHONE -> {
                val r = min(min(w, h) * 0.45f, h / 2f)
                p.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
            }
            // v36 serbest yazı: görünmez kutu (yalnız seçme ve kapsama için; çizilmez)
            BalloonStyle.FREE -> p.addRect(RectF(0f, 0f, w, h), Path.Direction.CW)
        }
        return p
    }

    private data class OutlineKey(val b: BalloonSpec, val w: Int, val h: Int)

    /**
     * v27 hız: birleşik yol (Path.op) her karede yeniden kurulmaz; balon, boyut (yarım birim yuvarlanmış) başına
     * önbellekte durur. Yollar önbellekten sonra değiştirilmez.
     */
    private val outlines = android.util.LruCache<OutlineKey, Pair<Path, Path?>>(48)

    /** Gövde ve kuyruklar birleşik yol; düşünce balonunda kabarcıklar ikinci yol (önbellekli). */
    fun outline(b: BalloonSpec, w: Float, h: Float): Pair<Path, Path?> {
        val key = OutlineKey(b, Math.round(w * 2f), Math.round(h * 2f))
        outlines.get(key)?.let { return it }
        return buildOutline(b, w, h).also { outlines.put(key, it) }
    }

    private fun buildOutline(b: BalloonSpec, w: Float, h: Float): Pair<Path, Path?> {
        val body = body(b, w, h)
        if (b.tails.isEmpty() || w < 1f || h < 1f || b.style == BalloonStyle.FREE) return body to null
        if (b.style == BalloonStyle.THOUGHT) {
            val bubbles = Path()
            b.tails.forEach { t -> thoughtBubbles(bubbles, t, w, h) }
            return body to bubbles
        }
        val tails = Path()
        b.tails.forEach { t -> tail(tails, b.style, t, w, h) }
        val merged = Path(body)
        // Birleştirme başarısızsa (çok eski/bozuk yol) ayrı ayrı çizilir
        return if (runCatching { merged.op(tails, Path.Op.UNION) }.getOrDefault(false)) merged to null else body to tails
    }

    /**
     * Balonu çizer: dolgu (renk ya da [gradient]), kenar (fısıltıda kesik çizgi). [size] yazı boyutu (kenar kalınlığı
     * bunun katı), [alpha] örtücülük; [coverage] silinme denetimi için tam opak siyah.
     */
    @Synchronized
    fun draw(c: Canvas, b: BalloonSpec, w: Float, h: Float, size: Float, alpha: Float, coverage: Boolean, gradient: GradientSpec? = null) {
        val (shape, extra) = outline(b, w, h)
        if (coverage) {
            fill.shader = null
            fill.color = 0xFF000000.toInt()
            c.drawPath(shape, fill)
            extra?.let { c.drawPath(it, fill) }
            return
        }
        if (b.style == BalloonStyle.FREE) return
        val stroke = max(1f, b.strokeWidth * size)
        fill.shader = Gradients.shader(gradient, 0f, 0f, w, h, alpha)
        fill.color = withAlpha(BackgroundRenderer.parseColor(b.fillHex, 0xFFFFFFFF.toInt()), alpha)
        c.drawPath(shape, fill)
        extra?.let { c.drawPath(it, fill) }
        fill.shader = null
        line.color = withAlpha(BackgroundRenderer.parseColor(b.strokeHex, 0xFF111111.toInt()), alpha)
        line.strokeWidth = stroke
        line.pathEffect = if (b.style == BalloonStyle.WHISPER) DashPathEffect(floatArrayOf(stroke * 3f, stroke * 2.2f), 0f) else null
        c.drawPath(shape, line)
        extra?.let { c.drawPath(it, line) }
        line.pathEffect = null
        if (b.style == BalloonStyle.DOUBLE) {
            // Çift çizgi: gövdenin dışında ikinci bir oval (vurgu)
            val g = stroke * 2.4f
            c.drawOval(RectF(-g, -g, w + g, h + g), line)
        }
    }

    /** Kuyruk ucunun yerel koordinatı. */
    fun tip(t: BalloonTail, w: Float, h: Float): Pair<Float, Float> = t.x * w to t.y * h

    private fun withAlpha(c: Int, a: Float): Int = (((c ushr 24) * a.coerceIn(0f, 1f)).toInt().coerceIn(0, 255) shl 24) or (c and 0x00FFFFFF)

    /** Elips üstünde [dx],[dy] yönündeki nokta (merkeze göre). */
    private fun ellipseAt(w: Float, h: Float, dx: Float, dy: Float, k: Float): Pair<Float, Float> {
        val a = w / 2f; val bb = h / 2f
        val l = hypot(dx, dy).coerceAtLeast(1e-4f)
        val ux = dx / l; val uy = dy / l
        val r = 1f / sqrt((ux * ux) / (a * a) + (uy * uy) / (bb * bb))
        return w / 2f + ux * r * k to h / 2f + uy * r * k
    }

    private fun tail(out: Path, style: BalloonStyle, t: BalloonTail, w: Float, h: Float) {
        val (tx, ty) = tip(t, w, h)
        val cx = w / 2f; val cy = h / 2f
        val dx = tx - cx; val dy = ty - cy
        val dist = hypot(dx, dy)
        if (dist < 1e-3f) return
        // Taban gövdenin biraz içinde (birleşince kenar kesintisiz)
        val (bx, by) = ellipseAt(w, h, dx, dy, 0.78f)
        val ux = dx / dist; val uy = dy / dist
        val px = -uy; val py = ux
        // v36 telefon mesajında kuyruk kısa ve geniş (kıvrık uç)
        val half = min(w, h) * (if (style == BalloonStyle.PHONE) 0.2f else 0.13f)
        val ax = bx + px * half; val ay = by + py * half
        val zx = bx - px * half; val zy = by - py * half
        if (style == BalloonStyle.ELECTRIC) {
            // Şimşek kuyruk: tabandan uca zikzak
            val steps = 4
            out.moveTo(ax, ay)
            for (i in 1..steps) {
                val f = i / (steps + 1f)
                val mx = bx + (tx - bx) * f; val my = by + (ty - by) * f
                val side = if (i % 2 == 0) 1f else -1f
                val wobble = half * (1f - f) * 0.9f * side
                out.lineTo(mx + px * wobble + px * half * (1f - f) * 0.4f, my + py * wobble + py * half * (1f - f) * 0.4f)
            }
            out.lineTo(tx, ty)
            for (i in steps downTo 1) {
                val f = i / (steps + 1f)
                val mx = bx + (tx - bx) * f; val my = by + (ty - by) * f
                val side = if (i % 2 == 0) 1f else -1f
                val wobble = half * (1f - f) * 0.9f * side
                out.lineTo(mx + px * wobble - px * half * (1f - f) * 0.4f, my + py * wobble - py * half * (1f - f) * 0.4f)
            }
            out.lineTo(zx, zy)
            out.close()
            return
        }
        // Eğri kuyruk: iki kenar aynı yöne kıvrılır (uç ince)
        val len = hypot(tx - bx, ty - by)
        val bend = t.curve.coerceIn(-1f, 1f) * len * 0.35f
        val mx = (bx + tx) / 2f + px * bend; val my = (by + ty) / 2f + py * bend
        out.moveTo(ax, ay)
        out.quadTo(mx + px * half * 0.35f, my + py * half * 0.35f, tx, ty)
        out.quadTo(mx - px * half * 0.35f, my - py * half * 0.35f, zx, zy)
        out.close()
    }

    private fun thoughtBubbles(out: Path, t: BalloonTail, w: Float, h: Float) {
        val (tx, ty) = tip(t, w, h)
        val dx = tx - w / 2f; val dy = ty - h / 2f
        val (bx, by) = ellipseAt(w, h, dx, dy, 1.02f)
        val base = min(w, h) * 0.09f
        for (i in 0 until 3) {
            val f = (i + 1) / 3.6f
            val x = bx + (tx - bx) * f; val y = by + (ty - by) * f
            out.addCircle(x, y, base * (1f - i * 0.28f), Path.Direction.CW)
        }
        out.addCircle(tx, ty, base * 0.28f, Path.Direction.CW)
    }

    private fun cloud(p: Path, w: Float, h: Float) {
        val a = w / 2f; val b = h / 2f
        val perim = (PI * (3 * (a + b) - sqrt((3 * a + b) * (a + 3 * b)))).toFloat()
        val n = (perim / (min(w, h) * 0.42f)).toInt().coerceIn(8, 18)
        val bump = perim / n * 0.62f
        val core = Path().apply { addOval(RectF(w * 0.08f, h * 0.1f, w * 0.92f, h * 0.9f), Path.Direction.CW) }
        val bumps = Path()
        for (k in 0 until n) {
            val ang = 2 * PI * k / n
            val x = a + (a - bump * 0.45f) * cos(ang).toFloat()
            val y = b + (b - bump * 0.45f) * sin(ang).toFloat()
            bumps.addCircle(x, y, bump * 0.6f, Path.Direction.CW)
        }
        if (!runCatching { core.op(bumps, Path.Op.UNION) }.getOrDefault(false)) { p.addOval(RectF(0f, 0f, w, h), Path.Direction.CW); return }
        p.addPath(core)
    }

    private fun burst(p: Path, w: Float, h: Float) {
        val a = w / 2f; val b = h / 2f
        val spikes = 14
        // Düzensiz ama kararlı (her çizimde aynı) uç boyları
        val jitter = floatArrayOf(1f, 0.86f, 1.08f, 0.9f, 1.04f, 0.82f, 1.1f, 0.94f, 1.02f, 0.88f, 1.06f, 0.92f, 1f, 0.84f)
        for (k in 0 until spikes * 2) {
            val ang = 2 * PI * k / (spikes * 2) - PI / 2
            val outer = k % 2 == 0
            val r = if (outer) 1.0f * jitter[(k / 2) % jitter.size] else 0.78f
            val x = a + a * r * cos(ang).toFloat()
            val y = b + b * r * sin(ang).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    /** Elips çevresinde [spikes] sivri uç (iç yarıçap [inner]); kararlı küçük düzensizlik [jitter]. */
    private fun radial(p: Path, w: Float, h: Float, spikes: Int, inner: Float, jitter: Float) {
        val a = w / 2f; val b = h / 2f
        for (k in 0 until spikes * 2) {
            val ang = 2 * PI * k / (spikes * 2) - PI / 2
            val outer = k % 2 == 0
            val j = 1f - jitter * (((k * 7919) % 13) / 13f)
            val r = if (outer) j else inner
            val x = a + a * r * cos(ang).toFloat()
            val y = b + b * r * sin(ang).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    /** Buz saçaklı balon: üst yarı yumuşak elips, alt kenardan sarkan sivri saçaklar. */
    private fun icicle(p: Path, w: Float, h: Float) {
        val a = w / 2f; val b = h / 2f
        val n = 160
        val teeth = 9
        for (k in 0..n) {
            val ang = 2 * PI * k / n
            var r = 0.9f
            val s = sin(ang).toFloat()
            if (s > 0.15f) {
                // Alt yarı: dişler (aşağı sivri), boyları kararlı biçimde değişir
                val u = ((ang - 0.15) / (PI - 0.3)).toFloat().coerceIn(0f, 1f) * teeth
                val frac = u - kotlin.math.floor(u)
                val tooth = (u.toInt() * 37 % 5) / 5f
                val tri = 1f - abs(frac - 0.5f) * 2f
                r = 0.9f + tri * (0.12f + 0.12f * tooth) * s
            }
            val x = a + a * r * cos(ang).toFloat()
            val y = b + b * r * s
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    /** Damlayan balon: elips ve alt kenarından sarkan yuvarlak uçlu damlalar (birleşik yol). */
    private fun drip(p: Path, w: Float, h: Float) {
        val core = Path().apply { addOval(RectF(0f, 0f, w, h * 0.9f), Path.Direction.CW) }
        val drops = Path()
        val xs = floatArrayOf(0.26f, 0.43f, 0.6f, 0.74f)
        val lens = floatArrayOf(0.2f, 0.32f, 0.14f, 0.24f)
        val r = min(w, h) * 0.045f
        for (i in xs.indices) {
            val x = w * xs[i]
            // Elipsin o noktadaki alt kenarı
            val ex = (x - w / 2f) / (w / 2f)
            val yEdge = h * 0.45f + h * 0.45f * sqrt((1f - ex * ex).coerceAtLeast(0f))
            val y1 = yEdge + h * lens[i]
            drops.addRoundRect(RectF(x - r, yEdge - h * 0.08f, x + r, y1), r, r, Path.Direction.CW)
            drops.addCircle(x, y1 - r * 0.3f, r * 1.35f, Path.Direction.CW)
        }
        if (!runCatching { core.op(drops, Path.Op.UNION) }.getOrDefault(false)) { p.addOval(RectF(0f, 0f, w, h), Path.Direction.CW); return }
        p.addPath(core)
    }

    /** Fistolu (tırtıklı) kutu: yuvarlak köşeli kutunun çevresine eşit küçük yarım daireler. */
    private fun scallop(p: Path, w: Float, h: Float) {
        val inset = min(w, h) * 0.07f
        val core = Path().apply {
            val rr = min(w, h) * 0.2f
            addRoundRect(RectF(inset, inset, w - inset, h - inset), rr, rr, Path.Direction.CW)
        }
        val bumps = Path()
        val step = min(w, h) * 0.16f
        val r = step * 0.62f
        fun along(x0: Float, y0: Float, x1: Float, y1: Float) {
            val len = hypot(x1 - x0, y1 - y0)
            val n = max(2, (len / step).toInt())
            for (i in 0..n) {
                val f = i / n.toFloat()
                bumps.addCircle(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, r, Path.Direction.CW)
            }
        }
        val a = inset + r * 0.3f; val bx = w - inset - r * 0.3f; val by = h - inset - r * 0.3f
        along(a, a, bx, a); along(bx, a, bx, by); along(bx, by, a, by); along(a, by, a, a)
        if (!runCatching { core.op(bumps, Path.Op.UNION) }.getOrDefault(false)) { p.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW); return }
        p.addPath(core)
    }

    /** Kalp balonu: iki üst lob ve alttaki sivri uç. */
    private fun heart(p: Path, w: Float, h: Float) {
        p.moveTo(w * 0.5f, h * 0.22f)
        p.cubicTo(w * 0.5f, h * -0.02f, w * 0.02f, h * -0.05f, w * 0.02f, h * 0.3f)
        p.cubicTo(w * 0.02f, h * 0.58f, w * 0.3f, h * 0.74f, w * 0.5f, h)
        p.cubicTo(w * 0.7f, h * 0.74f, w * 0.98f, h * 0.58f, w * 0.98f, h * 0.3f)
        p.cubicTo(w * 0.98f, h * -0.05f, w * 0.5f, h * -0.02f, w * 0.5f, h * 0.22f)
        p.close()
    }

    /** Pürüzlü (canavar) balon: elipsin çevresi kararlı, keskin düzensizliklerle. */
    private fun rough(p: Path, w: Float, h: Float) {
        val a = w / 2f; val b = h / 2f
        // Elle hızlıca çizilmiş, düzensiz dişli kenar (sabit tohum: her çizimde aynı biçim)
        val n = 58
        val rnd = java.util.Random(31)
        for (k in 0 until n) {
            val ang = 2 * PI * (k + (rnd.nextFloat() - 0.5f) * 0.5f) / n
            val r = (if (k % 2 == 0) 0.99f else 0.9f) + (rnd.nextFloat() - 0.5f) * 0.05f + 0.02f * sin(ang * 5 + 0.7).toFloat()
            val x = a + a * r * cos(ang).toFloat()
            val y = b + b * r * sin(ang).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    private fun wavy(p: Path, w: Float, h: Float) {
        val a = w / 2f; val b = h / 2f
        val n = 120
        for (k in 0..n) {
            val ang = 2 * PI * k / n
            val r = 0.94f + 0.05f * sin(ang * 18).toFloat()
            val x = a + a * r * cos(ang).toFloat()
            val y = b + b * r * sin(ang).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    /** Kuyruk ucunun kutuya göre oranı (dünya noktasından; [inverse] tuvalden yerel düzleme). */
    fun tailFromLocal(x: Float, y: Float, w: Float, h: Float, curve: Float = 0.3f): BalloonTail =
        BalloonTail((x / w.coerceAtLeast(1e-3f)).coerceIn(-3f, 4f), (y / h.coerceAtLeast(1e-3f)).coerceIn(-3f, 4f), curve)

    /**
     * v36 kuyrukların yönü: [dir] tarafta, kenardan [length] (kutu boyunun oranı) uzakta, [curve] eğrilikte; birden çok
     * kuyruk o kenar boyunca yayılır.
     */
    fun tailsToward(dir: TailDirection, count: Int, length: Float, curve: Float): List<BalloonTail> {
        if (count <= 0) return emptyList()
        val l = length.coerceIn(MIN_TAIL, MAX_TAIL)
        return List(count) { i ->
            val f = if (count == 1) -1f else i / (count - 1f)
            fun along(single: Float) = if (f < 0f) single else 0.15f + 0.7f * f
            when (dir) {
                TailDirection.BOTTOM_LEFT -> BalloonTail(along(0.3f), 1f + l, curve)
                TailDirection.BOTTOM_RIGHT -> BalloonTail(along(0.7f), 1f + l, -curve)
                TailDirection.TOP_LEFT -> BalloonTail(along(0.3f), -l, -curve)
                TailDirection.TOP_RIGHT -> BalloonTail(along(0.7f), -l, curve)
                TailDirection.LEFT -> BalloonTail(-l, if (f < 0f) 0.62f else 0.25f + 0.5f * f, -curve)
                TailDirection.RIGHT -> BalloonTail(1f + l, if (f < 0f) 0.62f else 0.25f + 0.5f * f, curve)
            }
        }
    }

    /** İlk kuyruğun yönü, uzaklığı ve eğriliği (pencerede gösterilir; kuyruk yoksa alt sol). */
    fun tailState(b: BalloonSpec): Triple<TailDirection, Float, Float> {
        val t = b.tails.firstOrNull() ?: return Triple(TailDirection.BOTTOM_LEFT, 0.6f, 0.3f)
        val dir = when {
            t.y > 1f -> if (t.x < 0.5f) TailDirection.BOTTOM_LEFT else TailDirection.BOTTOM_RIGHT
            t.y < 0f -> if (t.x < 0.5f) TailDirection.TOP_LEFT else TailDirection.TOP_RIGHT
            t.x < 0.5f -> TailDirection.LEFT
            else -> TailDirection.RIGHT
        }
        val length = when (dir) {
            TailDirection.BOTTOM_LEFT, TailDirection.BOTTOM_RIGHT -> t.y - 1f
            TailDirection.TOP_LEFT, TailDirection.TOP_RIGHT -> -t.y
            TailDirection.LEFT -> -t.x
            TailDirection.RIGHT -> t.x - 1f
        }
        val sign = if (dir == TailDirection.BOTTOM_RIGHT || dir == TailDirection.TOP_LEFT || dir == TailDirection.LEFT) -1f else 1f
        // Yüzde birlik adımlara yuvarlanır (1.4 - 1 gibi kayan nokta artıkları kaydırıcıda görünmesin)
        fun r(v: Float) = Math.round(v * 100f) / 100f
        return Triple(dir, r(length.coerceIn(MIN_TAIL, MAX_TAIL)), r((t.curve * sign).coerceIn(-1f, 1f)))
    }

    const val MIN_TAIL = 0.15f
    const val MAX_TAIL = 1.6f

    /** Varsayılan kuyruklar: [count] konuşana (alt kenar boyunca yayılmış). */
    fun defaultTails(count: Int): List<BalloonTail> = when (count) {
        0 -> emptyList()
        1 -> listOf(BalloonTail(0.3f, 1.65f))
        else -> List(count) { i ->
            val f = if (count == 1) 0.5f else i / (count - 1f)
            BalloonTail(0.1f + 0.8f * f, 1.6f + 0.1f * abs(f - 0.5f), if (f < 0.5f) 0.3f else -0.3f)
        }
    }
}

/** v27 balon kuyruklarının tuvaldeki yerleri ve sürüklenmesi (yazı kutusunun dörtgenine göre). */
object BalloonTails {
    /** Kuyruk uçlarının tuval koordinatları (balon değilse boş). */
    fun positions(stroke: com.karapp.drawguidance.data.model.Stroke): List<androidx.compose.ui.geometry.Offset> {
        val b = stroke.text?.box?.balloon ?: return emptyList()
        val p = stroke.points
        if (p.size < 4) return emptyList()
        return b.tails.map { t ->
            androidx.compose.ui.geometry.Offset(
                p[0].x + (p[1].x - p[0].x) * t.x + (p[3].x - p[0].x) * t.y,
                p[0].y + (p[1].y - p[0].y) * t.x + (p[3].y - p[0].y) * t.y,
            )
        }
    }

    /** [index]. kuyruğun ucu tuvaldeki [at] noktasına taşınmış balon (dörtgenin yerel oranına çevrilir). */
    fun moved(stroke: com.karapp.drawguidance.data.model.Stroke, index: Int, at: androidx.compose.ui.geometry.Offset): com.karapp.drawguidance.data.model.Stroke? {
        val text = stroke.text ?: return null
        val box = text.box ?: return null
        val b = box.balloon ?: return null
        if (index !in b.tails.indices || stroke.points.size < 4) return null
        val p = stroke.points
        val ux = p[1].x - p[0].x; val uy = p[1].y - p[0].y
        val vx = p[3].x - p[0].x; val vy = p[3].y - p[0].y
        val det = ux * vy - uy * vx
        if (abs(det) < 1e-6f) return null
        val dx = at.x - p[0].x; val dy = at.y - p[0].y
        val a = (dx * vy - dy * vx) / det
        val c = (ux * dy - uy * dx) / det
        val tails = b.tails.toMutableList()
        tails[index] = tails[index].copy(x = a.coerceIn(-3f, 4f), y = c.coerceIn(-3f, 4f))
        return stroke.copy(text = text.copy(box = box.copy(balloon = b.copy(tails = tails))))
    }
}

/** v36 balon kuyruğunun yönü (pencereden seçilir; uç sonra tuvalde sürüklenebilir). */
enum class TailDirection { BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT }
