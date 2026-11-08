package com.karapp.drawguidance.content.library.sketch

import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.cos
import kotlin.math.sin

/**
 * v17 çizim yardımcıları: resim çizerken üstünden geçilecek, renksiz (yalnız kalem rengi çizgi, dolgusuz) özgün çizimler.
 * Kapalı parçalar dolgusuzdur: Alan boya ile alan alan renklendirilebilir. Oran/kılavuz çizgileri ince, kesik ve
 * soluktur ([GUIDE] etiketli): Alan boyamada sınır sayılmaz.
 */
internal const val GUIDE = "guide"

/** Çizim yardımcısı: [w]×[h] tasarım biriminde çizilir, en/boy oranı korunarak kutuya eşlenir. */
internal fun sketch(
    id: String,
    title: String,
    w: Float,
    h: Float,
    tags: List<String>,
    body: VectorBuilder.() -> Unit,
): LibShape = LibShape("sketch.$id", title, Fit.BOX, aspect = w / h, tags = tags) {
    inBox(0f, 0f, 1f / w, 1f / h, 0f) { style(fill = null, stroke = null, width = 1f) { body() } }
}

/** Kılavuz çizgi (oran çizgisi): ince, kesik, soluk. */
internal fun VectorBuilder.guide(x1: Float, y1: Float, x2: Float, y2: Float): VectorBuilder.Part =
    line(x1, y1, x2, y2).width(0.4f).dashed().alpha(0.5f).tag(GUIDE)

/** Kılavuz elips. */
internal fun VectorBuilder.guideEllipse(cx: Float, cy: Float, rx: Float, ry: Float, rotate: Float = 0f): VectorBuilder.Part =
    ellipse(cx, cy, rx, ry, rotate).width(0.4f).dashed().alpha(0.5f).tag(GUIDE)

/** Kılavuz yay. */
internal fun VectorBuilder.guideArc(cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float): VectorBuilder.Part =
    arc(cx, cy, rx, ry, start, sweep).width(0.4f).dashed().alpha(0.5f).tag(GUIDE)

/** İç ayrıntı çizgisi (ince). */
internal fun VectorBuilder.Part.detail(): VectorBuilder.Part = width(0.6f)

/** [block] çizilir, sonra [cx] dikey eksenine göre aynası çizilir (simetrik çizimler). */
internal fun VectorBuilder.mirrored(cx: Float, block: VectorBuilder.() -> Unit) {
    block()
    group(sx = -1f, sy = 1f, px = cx, py = 0f, block = block)
}

/** Tekerlek: lastik, jant ve göbek (isteğe bağlı jant telleri). */
internal fun VectorBuilder.wheel(cx: Float, cy: Float, r: Float, spokes: Int = 0) {
    circle(cx, cy, r)
    circle(cx, cy, r * 0.62f).detail()
    circle(cx, cy, r * 0.16f).detail()
    for (i in 0 until spokes) {
        val a = Math.toRadians(360.0 * i / spokes)
        line(cx + r * 0.16f * cos(a).toFloat(), cy + r * 0.16f * sin(a).toFloat(), cx + r * 0.6f * cos(a).toFloat(), cy + r * 0.6f * sin(a).toFloat()).width(0.45f)
    }
}

/** Noktalardan geçen yumuşak kapalı çizgi. */
internal fun VectorBuilder.blob(vararg xy: Float): VectorBuilder.Part = smooth(*xy, closed = true)

/** Noktalardan geçen yumuşak açık çizgi. */
internal fun VectorBuilder.curveThrough(vararg xy: Float): VectorBuilder.Part = smooth(*xy, closed = false)

/** Elips üzerindeki nokta ([deg] derece, 0° sağ, y aşağı). */
internal fun onEllipse(cx: Float, cy: Float, rx: Float, ry: Float, deg: Float): P {
    val a = Math.toRadians(deg.toDouble())
    return P(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
}

/** Kabarık kenarlı kapalı şekil (bulut, çalı, ağaç tacı): elips üzerinde [bumps] tümsek. */
internal fun VectorBuilder.puff(cx: Float, cy: Float, rx: Float, ry: Float, bumps: Int, depth: Float = 0.3f, phase: Float = -90f): VectorBuilder.Part =
    path(closed = true) {
        val pts = List(bumps) { onEllipse(cx, cy, rx, ry, phase + 360f * it / bumps) }
        moveTo(pts[0].x, pts[0].y)
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
            val dx = b.x - a.x; val dy = b.y - a.y
            val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-5f)
            var nx = -dy / len; var ny = dx / len
            if (nx * (mx - cx) + ny * (my - cy) < 0f) { nx = -nx; ny = -ny }
            quadTo(mx + nx * len * depth * 2f, my + ny * len * depth * 2f, b.x, b.y)
        }
    }

/** İki eklem arası sivrilen kapsül (manken uzvu): [ra] ve [rb] uç yarıçapları. */
internal fun VectorBuilder.capsule(ax: Float, ay: Float, bx: Float, by: Float, ra: Float, rb: Float): VectorBuilder.Part {
    val ang = Math.toDegrees(kotlin.math.atan2((by - ay).toDouble(), (bx - ax).toDouble())).toFloat()
    return path(closed = true) {
        arcTo(bx, by, rb, rb, ang - 90f, 180f)
        arcTo(ax, ay, ra, ra, ang + 90f, 180f)
    }
}
