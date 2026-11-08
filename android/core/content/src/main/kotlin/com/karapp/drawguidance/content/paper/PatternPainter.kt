package com.karapp.drawguidance.content.paper

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Desenin çizileceği yüzey (Android Canvas, Java2D…): koordinatlar çağıranın birimindedir, [save]/[restore] arasında
 * öteleme/ölçek ve kırpma geçerlidir. Renkler önçarpımsız ARGB.
 */
interface PatternSurface {
    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun scale(s: Float)
    fun clipRect(l: Float, t: Float, r: Float, b: Float)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int, dash: Float)
    fun rect(x: Float, y: Float, w: Float, h: Float, fill: Boolean, width: Float, color: Int, dash: Float)
    fun circle(cx: Float, cy: Float, r: Float, fill: Boolean, width: Float, color: Int, dash: Float)
    fun poly(xy: FloatArray, closed: Boolean, fill: Boolean, width: Float, color: Int, dash: Float)
}

/** Desen renkleri: ana renk ve ondan türeyen tonlar, kağıt rengi ve vurgu. */
class PatternColors(main: Int, val paper: Int, accent: Int, val opacity: Float = 1f) {
    val main: Int = main or (0xFF shl 24)
    val light: Int = mix(this.main, 0xFFFFFFFF.toInt(), 0.55f)
    val dark: Int = mix(this.main, 0xFF000000.toInt(), 0.3f)
    val accent: Int = accent or (0xFF shl 24)

    fun slot(s: Int): Int = when (s) {
        PatOp.SLOT_LIGHT -> light
        PatOp.SLOT_DARK -> dark
        PatOp.SLOT_PAPER -> paper or (0xFF shl 24)
        PatOp.SLOT_ACCENT -> accent
        else -> main
    }

    companion object {
        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(sh: Int) = ((a shr sh and 0xFF) + ((b shr sh and 0xFF) - (a shr sh and 0xFF)) * t).roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        fun withAlpha(c: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (c and 0x00FFFFFF)
    }
}

/**
 * v23 desen çizimi (yüzeyden bağımsız): sayfa rengi tonu → karolar (sayfanın iç payında, görünür alanla sınırlı) →
 * sayfa ögeleri. Ekranda karo bir kez resme çizilip tekrarlanır ([paintTile]); dışa aktarmada karolar tek tek çizilir.
 */
object PatternPainter {

    /**
     * [p] desenini [x], [y], [w], [h] sayfasına çizer. [visible] null değilse yalnız o dikdörtgene düşen karolar çizilir.
     * [scale] desen ölçeği (1: tasarlanan boy). [tiles]/[tint] false ise karolar/sayfa tonu çizilmez (ekranda karolar
     * önceden çizilmiş karo resminden döşenir).
     */
    fun paint(
        p: PaperPattern, s: PatternSurface, x: Float, y: Float, w: Float, h: Float,
        colors: PatternColors, scale: Float = 1f, visible: FloatArray? = null, tiles: Boolean = true, tint: Boolean = true,
    ) {
        if (w <= 0f || h <= 0f) return
        val op = colors.opacity
        if (tint && p.tint != 0 && p.tintAlpha > 0f) s.rect(x, y, w, h, true, 0f, PatternColors.withAlpha(p.tint, p.tintAlpha * op), 0f)
        if (tiles && p.hasTile) {
            val rl = x + p.inset[0] * w; val rt = y + p.inset[1] * h
            val rr = x + w - p.inset[2] * w; val rb = y + h - p.inset[3] * h
            val tw = p.tileW * scale; val th = p.tileH * scale
            if (rr > rl && rb > rt && tw > 0.5f && th > 0.5f) {
                val vl = max(rl, visible?.get(0) ?: rl); val vt = max(rt, visible?.get(1) ?: rt)
                val vr = min(rr, visible?.get(2) ?: rr); val vb = min(rb, visible?.get(3) ?: rb)
                if (vr > vl && vb > vt) {
                    s.save()
                    s.clipRect(rl, rt, rr, rb)
                    // Taşan ögeler (köşedeki nokta, karonun dışına uzanan çizgi) için bir karo pay
                    val i0 = floor((vl - rl) / tw).toInt() - 1; val i1 = ceil((vr - rl) / tw).toInt()
                    val j0 = floor((vt - rt) / th).toInt() - 1; val j1 = ceil((vb - rt) / th).toInt()
                    for (j in j0..j1) for (i in i0..i1) {
                        s.save()
                        s.translate(rl + i * tw, rt + j * th)
                        s.scale(scale)
                        drawOps(p.tile, s, colors, 1f)
                        s.restore()
                    }
                    s.restore()
                }
            }
        }
        if (p.page.isNotEmpty()) {
            s.save()
            s.clipRect(x, y, x + w, y + h)
            drawPage(p.page, s, colors, x, y, w, h, scale)
            s.restore()
        }
    }

    /**
     * Tek karoyu (0..tileW×scale, 0..tileH×scale) çizer: komşu karolardan taşan parçalar da (3×3 kopya) çizilip karoya
     * kırpılır; tekrarlanınca dikişsiz olur.
     */
    fun paintTile(p: PaperPattern, s: PatternSurface, colors: PatternColors, scale: Float) {
        if (!p.hasTile) return
        val tw = p.tileW * scale; val th = p.tileH * scale
        s.save()
        s.clipRect(0f, 0f, tw, th)
        for (j in -1..1) for (i in -1..1) {
            s.save()
            s.translate(i * tw, j * th)
            s.scale(scale)
            drawOps(p.tile, s, colors, 1f)
            s.restore()
        }
        s.restore()
    }

    /** Karoların döşendiği alan (sayfanın iç payı): sol, üst, sağ, alt. */
    fun tileArea(p: PaperPattern, x: Float, y: Float, w: Float, h: Float): FloatArray =
        floatArrayOf(x + p.inset[0] * w, y + p.inset[1] * h, x + w - p.inset[2] * w, y + h - p.inset[3] * h)

    private fun drawOps(ops: List<PatOp>, s: PatternSurface, colors: PatternColors, k: Float) {
        for (o in ops) {
            val c = PatternColors.withAlpha(colors.slot(o.slot), o.alpha * colors.opacity)
            val pt = o.pts
            when (o.type) {
                PatOp.LINE -> s.line(pt[0] * k, pt[1] * k, pt[2] * k, pt[3] * k, o.width * k, c, o.dash * k)
                PatOp.RECT -> s.rect(pt[0] * k, pt[1] * k, pt[2] * k, pt[3] * k, o.fill, o.width * k, c, o.dash * k)
                PatOp.CIRCLE -> s.circle(pt[0] * k, pt[1] * k, pt[2] * k, o.fill, o.width * k, c, o.dash * k)
                else -> s.poly(if (k == 1f) pt else FloatArray(pt.size) { pt[it] * k }, o.closed, o.fill, o.width * k, c, o.dash * k)
            }
        }
    }

    private fun drawPage(ops: List<PatOp>, s: PatternSurface, colors: PatternColors, x: Float, y: Float, w: Float, h: Float, scale: Float) {
        val m = min(w, h)
        fun px(o: PatOp, v: Float) = if (o.centered) x + w / 2f + (v - 0.5f) * m else x + v * w
        fun py(o: PatOp, v: Float) = if (o.centered) y + h / 2f + (v - 0.5f) * m else y + v * h
        for (o in ops) {
            val c = PatternColors.withAlpha(colors.slot(o.slot), o.alpha * colors.opacity)
            val pt = o.pts
            val lw = o.width * scale; val dash = o.dash * scale
            when (o.type) {
                PatOp.LINE -> s.line(px(o, pt[0]), py(o, pt[1]), px(o, pt[2]), py(o, pt[3]), lw, c, dash)
                PatOp.RECT -> {
                    val l = px(o, pt[0]); val t = py(o, pt[1])
                    val ww = if (o.centered) pt[2] * m else pt[2] * w; val hh = if (o.centered) pt[3] * m else pt[3] * h
                    s.rect(l, t, ww, hh, o.fill, lw, c, dash)
                }
                PatOp.CIRCLE -> s.circle(px(o, pt[0]), py(o, pt[1]), if (o.centered) pt[2] * m else pt[2] * w, o.fill, lw, c, dash)
                else -> {
                    val a = FloatArray(pt.size)
                    for (i in 0 until pt.size / 2) { a[2 * i] = px(o, pt[2 * i]); a[2 * i + 1] = py(o, pt[2 * i + 1]) }
                    s.poly(a, o.closed, o.fill, lw, c, dash)
                }
            }
        }
    }
}
