package com.karapp.drawguidance.content.library.drawings

import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Hazır çizimlerin ortak paleti: yumuşak, birbiriyle uyumlu düz illüstrasyon renkleri. */
internal object C {
    const val SHADOW = "#27283A"
    const val WHITE = "#FFFFFF"
    const val CREAM = "#FFF6E5"
    const val SKY = "#9ED8F5"
    const val SKY_LIGHT = "#D5F0FC"
    const val GLASS = "#BFE7F7"
    const val GLASS_DARK = "#86C5E4"
    const val WATER = "#5DADE2"
    const val WATER_LIGHT = "#8FCBEE"
    const val WATER_DARK = "#3A86BF"
    const val RED = "#E05A47"
    const val RED_DARK = "#B8402F"
    const val BRICK = "#C8553D"
    const val ORANGE = "#F39A3D"
    const val ORANGE_DARK = "#D9782B"
    const val YELLOW = "#F7C948"
    const val YELLOW_LIGHT = "#FCE59A"
    const val GREEN_LIGHT = "#A5D66F"
    const val GREEN = "#6BBF59"
    const val GREEN_DARK = "#3F8F4A"
    const val GREEN_DEEP = "#2E6B3A"
    const val BROWN_LIGHT = "#C98E5A"
    const val BROWN = "#A0673B"
    const val BROWN_DARK = "#74462A"
    const val WOOD = "#DDA56C"
    const val SAND = "#F2D8A7"
    const val WALL = "#F6E3C0"
    const val WALL_DARK = "#E6C697"
    const val STONE = "#B8BEC7"
    const val STONE_LIGHT = "#DCE1E7"
    const val STONE_DARK = "#8C94A1"
    const val GRAY_DARK = "#4A4E5A"
    const val TIRE = "#3B3D4A"
    const val METAL = "#C9D1DB"
    const val PINK = "#F4A3B5"
    const val PINK_DARK = "#E0718B"
    const val PURPLE = "#9C7FD8"
    const val PURPLE_LIGHT = "#C7B6EE"
    const val BLUE = "#4F86C6"
    const val BLUE_LIGHT = "#8DB6E6"
    const val BLUE_DARK = "#35609A"
    const val NAVY = "#2E3F63"
    const val TEAL = "#3FB8AF"
    const val TEAL_LIGHT = "#8ED9D2"
    const val BLACK = "#2A2A33"
}

/** Hazır çizim: [w]×[h] tasarım biriminde çizilir ([canvas]), en/boy oranı korunarak kutuya eşlenir. */
internal fun drawing(
    id: String,
    title: String,
    w: Float,
    h: Float,
    tags: List<String>,
    body: VectorBuilder.() -> Unit,
): LibShape = LibShape("draw.$id", title, Fit.BOX, aspect = w / h, tags = tags) { canvas(w, h, body) }

/** İçerideki çizimleri [w]×[h] tasarım koordinatında yapar (daireler, en/boy [w]/[h] kutuda daire kalır). */
internal fun VectorBuilder.canvas(w: Float, h: Float, block: VectorBuilder.() -> Unit) =
    inBox(0f, 0f, 1f / w, 1f / h, 0f, block)

/** Yumuşak yer gölgesi (kenarsız, yarı saydam). */
internal fun VectorBuilder.shadow(cx: Float, cy: Float, rx: Float, ry: Float, a: Float = 0.16f) =
    ellipse(cx, cy, rx, ry).fill(C.SHADOW).noStroke().alpha(a)

/** Parlama lekesi (kenarsız beyaz). */
internal fun VectorBuilder.shine(cx: Float, cy: Float, rx: Float, ry: Float, rotate: Float = 0f, a: Float = 0.5f) =
    ellipse(cx, cy, rx, ry, rotate).fill(C.WHITE).noStroke().alpha(a)

private fun bulgeControl(a: P, b: P, cx: Float, cy: Float, bulge: Float): P {
    val mx = (a.x + b.x) / 2f
    val my = (a.y + b.y) / 2f
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy).coerceAtLeast(1e-5f)
    var nx = -dy / len
    var ny = dx / len
    if (nx * (mx - cx) + ny * (my - cy) < 0f) { nx = -nx; ny = -ny }
    return P(mx + nx * len * bulge * 2f, my + ny * len * bulge * 2f)
}

/**
 * Kabarık kenarlı kapalı şekil (bulut, çalı, ağaç tacı): [pts] noktaları arasında dışa doğru tümsekler.
 * [bulges] her kenarın tümsek oranı (kiriş boyuna göre; 0.5 ≈ yarım daire); yoksa [bulge].
 */
internal fun VectorBuilder.bumpy(pts: List<P>, cx: Float, cy: Float, bulge: Float = 0.32f, bulges: FloatArray? = null) =
    path(closed = true) {
        moveTo(pts[0].x, pts[0].y)
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val k = bulges?.getOrNull(i) ?: bulge
            val c = bulgeControl(a, b, cx, cy, k)
            quadTo(c.x, c.y, b.x, b.y)
        }
    }

/** Elips üzerine dizili [bumps] tümsekli kabarık şekil. */
internal fun VectorBuilder.puffy(
    cx: Float, cy: Float, rx: Float, ry: Float, bumps: Int, bulge: Float = 0.32f, phase: Float = -90f,
): VectorBuilder.Part {
    val pts = List(bumps) { i ->
        val a = Math.toRadians(phase + 360.0 * i / bumps)
        P(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
    }
    return bumpy(pts, cx, cy, bulge)
}

/**
 * Daire birleşimi kenarlı kapalı şekil: [circles] (cx, cy, r) soldan sağa, ardışıklar kesişmeli; alt kenar [bottom]
 * hizasında düz. İlk ve son dairenin en alt noktası [bottom] üzerinde olmalı (bulut, çalı tabanı).
 */
internal fun VectorBuilder.circleUnion(circles: List<FloatArray>, bottom: Float) = path(closed = true) {
    fun deg(c: FloatArray, x: Float, y: Float) = Math.toDegrees(kotlin.math.atan2((y - c[1]).toDouble(), (x - c[0]).toDouble())).toFloat()
    fun upperMeet(a: FloatArray, b: FloatArray): P? {
        val dx = b[0] - a[0]; val dy = b[1] - a[1]
        val d = hypot(dx, dy)
        if (d >= a[2] + b[2] || d <= kotlin.math.abs(a[2] - b[2]) || d < 1e-5f) return null
        val l = (a[2] * a[2] - b[2] * b[2] + d * d) / (2f * d)
        val hh = sqrt((a[2] * a[2] - l * l).coerceAtLeast(0f))
        val mx = a[0] + dx * l / d; val my = a[1] + dy * l / d
        val p1 = P(mx - dy * hh / d, my + dx * hh / d)
        val p2 = P(mx + dy * hh / d, my - dx * hh / d)
        return if (p1.y < p2.y) p1 else p2
    }
    fun norm(v: Float): Float { var x = v; while (x <= 0f) x += 360f; while (x > 360f) x -= 360f; return x }
    val first = circles.first()
    moveTo(first[0], bottom)
    var inAngle = 90f
    for (i in circles.indices) {
        val c = circles[i]
        val meet = if (i < circles.size - 1) upperMeet(c, circles[i + 1]) else null
        val outAngle = when {
            i == circles.size - 1 -> 90f
            meet != null -> deg(c, meet.x, meet.y)
            else -> 0f
        }
        arcTo(c[0], c[1], c[2], c[2], inAngle, norm(outAngle - inAngle))
        if (i < circles.size - 1) {
            val n = circles[i + 1]
            inAngle = if (meet != null) deg(n, meet.x, meet.y) else 180f
        }
    }
    lineTo(first[0], bottom)
}

/** Altı düz bulut: [w]×[h] kutuya ortalanır. */
internal fun VectorBuilder.cloud(cx: Float, cy: Float, w: Float, h: Float, fill: String = C.WHITE, shading: Boolean = true) {
    val b = cy + h / 2f
    val rl = h * 0.36f; val rr = h * 0.32f
    val circles = listOf(
        floatArrayOf(cx - w / 2f + rl, b - rl, rl),
        floatArrayOf(cx - w * 0.14f, cy - h / 2f + h * 0.46f, h * 0.46f),
        floatArrayOf(cx + w * 0.16f, cy - h / 2f + h * 0.58f, h * 0.4f),
        floatArrayOf(cx + w / 2f - rr, b - rr, rr),
    )
    circleUnion(circles, b).fill(fill)
    if (shading) {
        path(closed = true) {
            moveTo(cx - w * 0.36f, b - h * 0.02f)
            quadTo(cx, b - h * 0.34f, cx + w * 0.36f, b - h * 0.02f)
        }.fill("#DCE8F3").noStroke()
        shine(cx - w * 0.16f, cy - h * 0.2f, w * 0.07f, h * 0.07f, -20f, 0.9f)
    }
}

/** Yumuşak (Catmull-Rom) kalın sap: önce kalem renginde kalın, üstüne renkli. */
internal fun VectorBuilder.stem(color: String, thickness: Float, vararg xy: Float) {
    smooth(*xy).width(thickness + 1.1f)
    smooth(*xy).stroke(color).width(thickness)
}

/** Çimen öbeği: tabanı ([x] ortası, [baseY]), [blades] yaprak. */
internal fun VectorBuilder.tuft(x: Float, baseY: Float, w: Float, h: Float, color: String = C.GREEN, blades: Int = 3) =
    path(closed = true) {
        val left = x - w / 2f
        moveTo(left, baseY)
        for (i in 0 until blades) {
            val t = (i + 0.5f) / blades
            val tipX = left + w * t + (t - 0.5f) * w * 0.7f
            val tall = if (blades % 2 == 1 && i == blades / 2) 1f else 0.72f
            val tipY = baseY - h * tall
            val x0 = left + w * i / blades
            val x1 = left + w * (i + 1) / blades
            val valleyY = if (i == blades - 1) baseY else baseY - h * 0.22f
            quadTo(x0 + (tipX - x0) * 0.25f, baseY - h * tall * 0.55f, tipX, tipY)
            quadTo(x1 + (tipX - x1) * 0.25f, baseY - h * tall * 0.5f, if (i == blades - 1) left + w else x1, valleyY)
        }
        lineTo(left + w, baseY)
    }.fill(color).width(0.7f)

/** Badem biçimli yaprak: [cx],[cy] merkezli, [len] boyunda, [angle] derece yönünde; orta damarla. */
internal fun VectorBuilder.leaf(
    cx: Float, cy: Float, len: Float, wid: Float, angle: Float, fill: String = C.GREEN, vein: Boolean = true,
    veinColor: String? = C.GREEN_DEEP,
) {
    group(rotate = angle, px = cx, py = cy) {
        path(closed = true) {
            moveTo(cx - len / 2f, cy)
            quadTo(cx - len * 0.1f, cy - wid, cx + len / 2f, cy)
            quadTo(cx - len * 0.1f, cy + wid, cx - len / 2f, cy)
        }.fill(fill).width(0.8f)
        if (vein) {
            path { moveTo(cx - len * 0.42f, cy); quadTo(cx, cy - wid * 0.12f, cx + len * 0.38f, cy) }
                .stroke(veinColor).width(0.5f)
        }
    }
}

/** Tekerlek: lastik, jant, göbek, bijon. */
internal fun VectorBuilder.wheel(cx: Float, cy: Float, r: Float, rim: String = C.METAL, hub: String = C.STONE_DARK) {
    circle(cx, cy, r).fill(C.TIRE)
    arc(cx, cy, r * 0.83f, r * 0.83f, 200f, 70f).stroke(C.WHITE).width(0.5f).alpha(0.45f)
    circle(cx, cy, r * 0.6f).fill(rim).width(0.7f)
    circle(cx, cy, r * 0.46f).fill(C.WHITE).noStroke().alpha(0.35f)
    for (k in 0 until 5) {
        val a = Math.toRadians(-90.0 + 72.0 * k)
        val x2 = cx + r * 0.52f * cos(a).toFloat()
        val y2 = cy + r * 0.52f * sin(a).toFloat()
        line(cx, cy, x2, y2).stroke(hub).width(0.6f)
    }
    circle(cx, cy, r * 0.2f).fill(hub).width(0.6f)
    circle(cx, cy, r * 0.07f).fill(C.GRAY_DARK).noStroke()
}

/**
 * Pencere: çerçeve, cam, kanat çizgileri, yansıma; isteğe bağlı perde ve denizlik.
 */
internal fun VectorBuilder.window(
    x: Float, y: Float, w: Float, h: Float,
    frame: String = C.WHITE, glass: String = C.GLASS,
    cols: Int = 2, rows: Int = 2,
    curtain: String? = null, sill: Boolean = true,
) {
    val t = min(w, h) * 0.1f
    rect(x, y, w, h).fill(frame)
    val gx = x + t; val gy = y + t; val gw = w - 2 * t; val gh = h - 2 * t
    rect(gx, gy, gw, gh).fill(glass).width(0.6f)
    poly(gx + gw * 0.12f, gy + gh, gx + gw * 0.5f, gy, gx + gw * 0.72f, gy, gx + gw * 0.34f, gy + gh, closed = true)
        .fill(C.WHITE).noStroke().alpha(0.35f)
    if (curtain != null) {
        path(closed = true) {
            moveTo(gx, gy); lineTo(gx + gw * 0.38f, gy)
            quadTo(gx + gw * 0.12f, gy + gh * 0.35f, gx + gw * 0.16f, gy + gh * 0.72f)
            lineTo(gx, gy + gh * 0.78f)
        }.fill(curtain).width(0.5f)
        path(closed = true) {
            moveTo(gx + gw, gy); lineTo(gx + gw * 0.62f, gy)
            quadTo(gx + gw * 0.88f, gy + gh * 0.35f, gx + gw * 0.84f, gy + gh * 0.72f)
            lineTo(gx + gw, gy + gh * 0.78f)
        }.fill(curtain).width(0.5f)
    }
    for (i in 1 until cols) {
        val lx = gx + gw * i / cols
        rect(lx - t * 0.35f, gy, t * 0.7f, gh).fill(frame).width(0.5f)
    }
    for (j in 1 until rows) {
        val ly = gy + gh * j / rows
        rect(gx, ly - t * 0.35f, gw, t * 0.7f).fill(frame).width(0.5f)
    }
    if (sill) rect(x - t * 0.8f, y + h - t * 0.2f, w + t * 1.6f, t * 1.3f).fill(frame).width(0.8f)
}

/** Beş (ya da [n]) taç yapraklı çiçek başı. */
internal fun VectorBuilder.blossom(
    cx: Float, cy: Float, r: Float, petal: String, center: String = C.YELLOW, n: Int = 5, rotate: Float = 0f,
    width: Float = 0.6f,
) {
    for (i in 0 until n) {
        val a = Math.toRadians(rotate - 90.0 + 360.0 * i / n)
        val px = cx + r * 0.52f * cos(a).toFloat()
        val py = cy + r * 0.52f * sin(a).toFloat()
        ellipse(px, py, r * 0.5f, r * 0.36f, (rotate - 90f + 360f * i / n)).fill(petal).width(width)
    }
    circle(cx, cy, r * 0.3f).fill(center).width(width)
}

/** Kalın boru gibi çizgi: önce kalem renginde kalın, üstüne renkli ince çizgi (kenarlı tüp görünümü). */
internal fun VectorBuilder.tube(color: String, thickness: Float, vararg xy: Float) {
    poly(*xy).width(thickness + 1.1f)
    poly(*xy).stroke(color).width(thickness)
}

/** Elips içindeki dikey şeridin ([x0]..[x1]) kapalı çokgeni (arı çizgisi, balık bandı gibi). */
internal fun VectorBuilder.ellipseBand(cx: Float, cy: Float, rx: Float, ry: Float, x0: Float, x1: Float, steps: Int = 10): VectorBuilder.Part {
    val pts = ArrayList<P>()
    fun yAt(x: Float): Float {
        val u = ((x - cx) / rx).coerceIn(-1f, 1f)
        return ry * sqrt(1f - u * u)
    }
    for (i in 0..steps) { val x = x0 + (x1 - x0) * i / steps; pts += P(x, cy - yAt(x)) }
    for (i in steps downTo 0) { val x = x0 + (x1 - x0) * i / steps; pts += P(x, cy + yAt(x)) }
    return points(pts, closed = true)
}

/**
 * Kalınlığı [w0]'dan [w1]'e incelen kapalı şerit (kuyruk, dal, sap): merkez çizgisi [xy] noktalarından Catmull-Rom
 * ile yumuşatılır, ucu yuvarlaktır.
 */
internal fun VectorBuilder.ribbon(w0: Float, w1: Float, vararg xy: Float): VectorBuilder.Part {
    val pts = List(xy.size / 2) { P(xy[it * 2], xy[it * 2 + 1]) }
    val c = VectorBuilder.catmullRom(pts, false, 0.5f, 10)
    val n = c.size
    val left = ArrayList<P>(n + 8)
    val right = ArrayList<P>(n)
    var tx = 1f; var ty = 0f
    for (i in 0 until n) {
        val a = c[maxOf(0, i - 1)]; val b = c[minOf(n - 1, i + 1)]
        tx = b.x - a.x; ty = b.y - a.y
        val len = hypot(tx, ty).coerceAtLeast(1e-5f)
        tx /= len; ty /= len
        val w = (w0 + (w1 - w0) * i / (n - 1f)) / 2f
        left += P(c[i].x - ty * w, c[i].y + tx * w)
        right += P(c[i].x + ty * w, c[i].y - tx * w)
    }
    val e = c[n - 1]
    val w = w1 / 2f
    for (k in 1..5) {
        val th = Math.PI * k / 6
        left += P(e.x + (-ty) * w * cos(th).toFloat() + tx * w * sin(th).toFloat(), e.y + tx * w * cos(th).toFloat() + ty * w * sin(th).toFloat())
    }
    left += right.asReversed()
    return points(left, closed = true)
}

/** Kalp: [cx],[cy] merkezli, [s] yarı genişlik ölçüsünde. */
internal fun VectorBuilder.heart(cx: Float, cy: Float, s: Float) = path(closed = true) {
    moveTo(cx, cy + s * 0.9f)
    cubicTo(cx - s * 1.6f, cy - s * 0.1f, cx - s * 0.6f, cy - s * 1.25f, cx, cy - s * 0.4f)
    cubicTo(cx + s * 0.6f, cy - s * 1.25f, cx + s * 1.6f, cy - s * 0.1f, cx, cy + s * 0.9f)
}

/** Damla: sivri ucu yukarıda, [cx],[cy] alt yuvarlağın merkezi. */
internal fun VectorBuilder.drop(cx: Float, cy: Float, r: Float) = path(closed = true) {
    moveTo(cx, cy - r * 2.4f)
    quadTo(cx + r * 1.1f, cy - r * 0.8f, cx + r, cy)
    arcTo(cx, cy, r, r, 0f, 180f)
    quadTo(cx - r * 1.1f, cy - r * 0.8f, cx, cy - r * 2.4f)
}
