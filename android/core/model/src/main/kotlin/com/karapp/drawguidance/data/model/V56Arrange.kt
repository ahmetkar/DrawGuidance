package com.karapp.drawguidance.data.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * v56 aşama 6A (2E, yalnız Resim): Nesne › Hizala ve dağıt ile Nesne › Efektler (vektör girdap, büzüştür, şişir). Saf;
 * iOS `V56Arrange.swift` ile aynı.
 */
object V56Arrange {
    enum class Align { LEFT, HCENTER, RIGHT, TOP, VCENTER, BOTTOM }

    /** Hizalama referansı: kâğıt, seçimin kutusu, ana nesne. */
    enum class Ref { PAPER, SELECTION, KEY }

    enum class Distribute { HORIZONTAL, VERTICAL, SPACE_H, SPACE_V }

    /** Birim: birlikte taşınan elemanlar (grup) ve kutusu (sol, üst, sağ, alt). */
    data class Unit(val ids: Set<String>, val l: Float, val t: Float, val r: Float, val b: Float) {
        val cx: Float get() = (l + r) / 2f
        val cy: Float get() = (t + b) / 2f
    }

    /** Seçimin birimleri; [boundsOf] elemanın görünen kutusu (yoksa noktaların kutusu). */
    fun units(strokes: List<Stroke>, ids: Set<String>, boundsOf: (Stroke) -> FloatArray? = { TransformOps.pointsBounds(listOf(it)) }): List<Unit> =
        TransformOps.units(strokes, ids).mapNotNull { g ->
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            strokes.filter { it.id in g }.forEach { s -> boundsOf(s)?.let { bb -> l = min(l, bb[0]); t = min(t, bb[1]); r = max(r, bb[2]); b = max(b, bb[3]) } }
            if (l > r) null else Unit(g, l, t, r, b)
        }

    /** Hizalama kaymaları (birim sırasıyla). [keyId] ana nesnenin (birimindeki) bir elemanı. */
    fun align(units: List<Unit>, align: Align, ref: Ref, paperW: Float, paperH: Float, keyId: String? = null): List<Pair<Float, Float>> {
        if (units.isEmpty()) return emptyList()
        val key = units.firstOrNull { keyId != null && keyId in it.ids }
        val box: FloatArray = when {
            ref == Ref.PAPER || (ref == Ref.SELECTION && units.size == 1) -> floatArrayOf(0f, 0f, paperW, paperH)
            ref == Ref.KEY && key != null -> floatArrayOf(key.l, key.t, key.r, key.b)
            else -> floatArrayOf(units.minOf { it.l }, units.minOf { it.t }, units.maxOf { it.r }, units.maxOf { it.b })
        }
        return units.map { u ->
            if (ref == Ref.KEY && u === key) 0f to 0f
            else when (align) {
                Align.LEFT -> (box[0] - u.l) to 0f
                Align.HCENTER -> ((box[0] + box[2]) / 2f - u.cx) to 0f
                Align.RIGHT -> (box[2] - u.r) to 0f
                Align.TOP -> 0f to (box[1] - u.t)
                Align.VCENTER -> 0f to ((box[1] + box[3]) / 2f - u.cy)
                Align.BOTTOM -> 0f to (box[3] - u.b)
            }
        }
    }

    /**
     * Dağıtma kaymaları: yatay/dikey dağıt merkezleri eşit aralıklı yapar, boşlukları eşitle kenarlar arası boşluğu; en
     * baştaki ve en sondaki birim yerinde kalır. En az 3 birim gerekir (azsa kayma yok).
     */
    fun distribute(units: List<Unit>, mode: Distribute): List<Pair<Float, Float>> {
        val zero = units.map { 0f to 0f }
        if (units.size < 3) return zero
        val horizontal = mode == Distribute.HORIZONTAL || mode == Distribute.SPACE_H
        val order = units.indices.sortedBy { if (horizontal) units[it].cx else units[it].cy }
        val out = zero.toMutableList()
        if (mode == Distribute.HORIZONTAL || mode == Distribute.VERTICAL) {
            val first = units[order.first()]; val last = units[order.last()]
            val a = if (horizontal) first.cx else first.cy
            val step = ((if (horizontal) last.cx else last.cy) - a) / (order.size - 1)
            order.forEachIndexed { k, i ->
                val c = if (horizontal) units[i].cx else units[i].cy
                val d = a + step * k - c
                out[i] = if (horizontal) d to 0f else 0f to d
            }
        } else {
            val sizes = order.map { if (horizontal) units[it].r - units[it].l else units[it].b - units[it].t }
            val start = if (horizontal) units[order.first()].l else units[order.first()].t
            val end = if (horizontal) units[order.last()].r else units[order.last()].b
            val gap = (end - start - sizes.sum()) / (order.size - 1)
            var pos = start
            order.forEachIndexed { k, i ->
                val cur = if (horizontal) units[i].l else units[i].t
                val d = pos - cur
                out[i] = if (horizontal) d to 0f else 0f to d
                pos += sizes[k] + gap
            }
        }
        return out
    }

    /** Elemanı kaydırır (noktalar, kırpma, maske, resim ağı/köşeleri, yansıma dalgası). */
    fun shift(s: Stroke, dx: Float, dy: Float): Stroke {
        if (dx == 0f && dy == 0f) return s
        fun m(p: StrokePoint) = p.at(p.x + dx, p.y + dy)
        return s.copy(
            points = s.points.map(::m), clip = s.clip?.map(::m),
            mask = s.mask?.let { mk -> mk.copy(points = mk.points.map(::m)) },
            warp = s.warp?.let { w ->
                w.copy(
                    mesh = w.mesh?.map(::m), quad = w.quad?.map(::m),
                    wave = w.wave?.let { wv -> if (wv.bottom > wv.top) wv.copy(top = wv.top + dy, bottom = wv.bottom + dy, cx = wv.cx + dx) else wv },
                )
            },
        )
    }

    /** Kaymaları birimlere uygular. */
    fun applyOffsets(strokes: List<Stroke>, units: List<Unit>, offsets: List<Pair<Float, Float>>): List<Stroke> {
        val byId = HashMap<String, Pair<Float, Float>>()
        units.forEachIndexed { i, u -> val o = offsets.getOrNull(i) ?: return@forEachIndexed; u.ids.forEach { byId[it] = o } }
        return strokes.map { s -> byId[s.id]?.let { (dx, dy) -> shift(s, dx, dy) } ?: s }
    }

    // --- Nesne › Efektler: vektör girdap, büzüştür, şişir ---

    enum class Effect { TWIRL, PUCKER, BLOAT }

    /** Bu efekt uygulanabilir mi (vektör çizgi ya da şekil; resim, yazı, şema, formül değil). */
    fun canEffect(s: Stroke): Boolean = !s.brushType.isQuad && s.brushType != BrushType.ERASER && s.points.size >= 2

    /**
     * Yolu sıklaştırır: her parçaya [maxSeg] uzunluğunu aşmayacak kadar ara nokta; dönen dizide her noktanın, iki çapa
     * nokta arasındaki yeri (0 çapa, 0..1 ara) da verilir. Şekilde alt yol sayıları güncellenir.
     */
    private fun densify(points: List<StrokePoint>, closed: Boolean, maxSeg: Float, anchorEvery: Int): Pair<List<StrokePoint>, List<Float>> {
        val out = ArrayList<StrokePoint>(); val ts = ArrayList<Float>()
        val n = points.size
        val segs = if (closed) n else n - 1
        // Çapa noktaları: şekilde her köşe, serbest çizgide her [anchorEvery] nokta
        val anchors = (0 until n).filter { it % anchorEvery == 0 || (!closed && it == n - 1) }
        for (i in 0 until segs) {
            val a = points[i]; val b = points[(i + 1) % n]
            val k = max(1, kotlin.math.ceil(hypot(b.x - a.x, b.y - a.y) / maxSeg).toInt())
            for (j in 0 until k) {
                val f = j / k.toFloat()
                out += a.at(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                // Yer: bulunduğu çapa aralığındaki konum
                val pos = i + f
                val lo = anchors.lastOrNull { it <= pos } ?: 0
                val hi = anchors.firstOrNull { it > pos } ?: (if (closed) n else n - 1)
                ts += if (hi > lo) (pos - lo) / (hi - lo) else 0f
            }
        }
        if (!closed) { out += points.last(); ts += 0f }
        return out to ts
    }

    /** Tek noktaya efekt: [t] çapa aralığındaki yer (girdapta kullanılmaz). */
    private fun effectPoint(p: StrokePoint, t: Float, effect: Effect, amount: Float, cx: Float, cy: Float, radius: Float): StrokePoint {
        val ox = p.x - cx; val oy = p.y - cy
        return when (effect) {
            Effect.TWIRL -> {
                val d = hypot(ox, oy)
                val a = amount * (PI.toFloat() / 180f) * max(0f, 1f - d / max(1f, radius))
                p.at(cx + ox * cos(a) - oy * sin(a), cy + ox * sin(a) + oy * cos(a))
            }
            else -> {
                // Büzüştür: çapalar dışa, aralar içe (sivri); şişir: çapalar içe, aralar dışa (yuvarlak)
                val k = if (effect == Effect.PUCKER) amount else -amount
                val w = if (t <= 1e-4f) k else -k * sin(PI.toFloat() * t)
                p.at(p.x + ox * w, p.y + oy * w)
            }
        }
    }

    /**
     * Seçili vektör elemanlara efekt (tek geri alma adımı için yeni liste). [amount]: girdapta derece (−720..720),
     * büzüştür/şişirde 0..1. Merkez ve yarıçap seçimin kutusundan.
     */
    fun effect(strokes: List<Stroke>, ids: Set<String>, effect: Effect, amount: Float): List<Stroke> {
        val sel = strokes.filter { it.id in ids && canEffect(it) }
        val bb = TransformOps.pointsBounds(sel) ?: return strokes
        val cx = (bb[0] + bb[2]) / 2f; val cy = (bb[1] + bb[3]) / 2f
        val radius = max(1f, hypot(bb[2] - bb[0], bb[3] - bb[1]) / 2f)
        val maxSeg = max(1f, radius / 24f)
        return strokes.map { s ->
            if (s.id !in ids || !canEffect(s)) return@map s
            val shape = s.shape
            if (shape != null && shape.subpaths.isNotEmpty() && shape.subpaths.sumOf { it.count } == s.points.size) {
                var at = 0
                val pts = ArrayList<StrokePoint>()
                val subs = shape.subpaths.map { sp ->
                    val part = s.points.subList(at, at + sp.count); at += sp.count
                    val (d, ts) = if (part.size >= 2) densify(part, sp.closed, maxSeg, 1) else part to part.map { 0f }
                    d.forEachIndexed { i, p -> pts += effectPoint(p, ts[i], effect, amount, cx, cy, radius) }
                    sp.copy(count = d.size)
                }
                s.copy(points = pts, shape = shape.copy(subpaths = subs))
            } else {
                val every = max(1, s.points.size / 16)
                val (d, ts) = densify(s.points, false, maxSeg, every)
                s.copy(points = d.mapIndexed { i, p -> effectPoint(p, ts[i], effect, amount, cx, cy, radius) })
            }
        }
    }

    /** Efektin varsayılan ve sınır değerleri. */
    fun range(effect: Effect): ClosedFloatingPointRange<Float> = if (effect == Effect.TWIRL) -720f..720f else 0f..1f
    fun default(effect: Effect): Float = if (effect == Effect.TWIRL) 90f else 0.3f

    @Suppress("unused")
    private fun near(a: Float, b: Float) = abs(a - b) < 1e-4f
}
