package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.Affine
import com.karapp.drawguidance.content.vector.INK_COLOR
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Fen ve geometri çizimleri için ortak araçlar.
 *
 * Her çizim [sci] ile tanımlanır: çizim kodu w×h "sanal" birimlik eşit ölçekli bir tuvalde çalışır (daireler daire kalır),
 * tuval birim kutuya eşlenir ve şeklin en/boy oranı w/h olur. Yazılar [Lb] ile yazılır: boy sanal birimdir.
 */

/** Ortak renkler (açık, ders kitabı tonları). */
internal object Sc {
    const val LIQUID = "#BFE3F7"
    const val LIQUID_DARK = "#7FC1EA"
    const val GLASS = "#EEF6FB"
    const val LENS = "#D8ECF8"
    const val RAY = "#E53935"
    const val METAL = "#C9D1D9"
    const val METAL_DARK = "#8C97A3"
    const val WOOD = "#E8C99B"
    const val GROUND = "#EFE7DA"
    const val BLOCK = "#F6D38A"
    const val RED = "#E53935"
    const val BLUE = "#1E88E5"
    const val GREEN = "#43A047"
    const val YELLOW = "#FDD835"
    const val ORANGE = "#FB8C00"
    const val FLAME_OUT = "#FFB74D"
    const val FLAME_IN = "#FFE082"
    const val PAPER = "#FFFFFF"

    /** Görünür tayf (kırmızıdan mora). */
    val SPECTRUM = listOf("#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#3949AB", "#8E24AA")
}

/** Sanal tuvalde yazı: [size] sanal birim (harf boyu); kutunun kısa kenarına oranlanarak kaydedilir. */
internal class Lb(private val b: VectorBuilder, w: Float, h: Float) {
    private val k = sqrt(w * h) / min(w, h)

    /** Sol-üst çapalı yazı. */
    fun t(x: Float, y: Float, s: String, size: Float = 0.08f, color: String? = null) = b.text(x, y, s, size * k, color)

    /** ([cx],[cy]) merkezli yazı (genişlik yaklaşık). */
    fun c(cx: Float, cy: Float, s: String, size: Float = 0.08f, color: String? = null) =
        b.text(cx - width(s, size) / 2f, cy - size * 0.52f, s, size * k, color)

    /** Sağa yaslı yazı: sağ kenarı [rx]. */
    fun r(rx: Float, cy: Float, s: String, size: Float = 0.08f, color: String? = null) =
        b.text(rx - width(s, size), cy - size * 0.52f, s, size * k, color)

    private fun width(s: String, size: Float) = s.sumOf { ch ->
        when {
            ch == ' ' -> 0.3
            ch == 'i' || ch == 'l' || ch == 'ı' || ch == 'İ' || ch == ',' || ch == '.' || ch == 'I' -> 0.3
            ch == 'm' || ch == 'M' || ch == 'W' || ch == 'w' -> 0.85
            ch.isUpperCase() -> 0.68
            ch.isDigit() -> 0.6
            else -> 0.56
        }
    }.toFloat() * size
}

/**
 * Kütüphane şekli: çizim w×h sanal birimlik tuvalde yapılır, en/boy oranı w/h. [Fit.SQUARE] yalnızca w == h için.
 */
internal fun sci(
    id: String,
    title: String,
    w: Float = 1f,
    h: Float = 1f,
    fit: Fit = Fit.BOX,
    tags: List<String> = emptyList(),
    draw: VectorBuilder.(Lb) -> Unit,
): LibShape = LibShape(id, title, fit, w / h, tags) {
    withTransform(Affine.scale(1f / w, 1f / h)) { draw(Lb(this, w, h)) }
}

// --- Küçük geometri yardımcıları ---

internal fun deg(rad: Double): Float = Math.toDegrees(rad).toFloat()

/** (cx,cy) merkezinden (x,y) noktasının açısı (derece; 0 = sağ, y aşağı olduğundan saat yönü pozitif). */
internal fun angleOf(cx: Float, cy: Float, x: Float, y: Float): Float = deg(atan2((y - cy).toDouble(), (x - cx).toDouble()))

/** Açıdaki (derece) çember noktası. */
internal fun onCircle(cx: Float, cy: Float, r: Float, degrees: Float): P {
    val a = Math.toRadians(degrees.toDouble())
    return P(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
}

internal fun lerp(a: P, b: P, t: Float) = P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

internal fun dist(a: P, b: P) = hypot(b.x - a.x, b.y - a.y)

internal fun unit(dx: Float, dy: Float): P {
    val l = hypot(dx, dy).coerceAtLeast(1e-6f)
    return P(dx / l, dy / l)
}

// --- Çizim yardımcıları ---

/** Kalem renginde (ya da [color]) dolu nokta. */
internal fun VectorBuilder.dot(x: Float, y: Float, r: Float = 0.014f, color: String = INK_COLOR) =
    circle(x, y, r).fill(color).noStroke()

/** Dolu uçlu vektör oku (gövde uca kadar uzamaz). [color] null: kalem rengi. */
internal fun VectorBuilder.vec(
    x1: Float, y1: Float, x2: Float, y2: Float,
    head: Float = 0.05f, color: String? = null, width: Float = 1.2f,
): VectorBuilder.Part {
    val u = unit(x2 - x1, y2 - y1)
    val body = line(x1, y1, x2 - u.x * head * 0.7f, y2 - u.y * head * 0.7f).stroke(color).width(width)
    arrowHead(x2, y2, u.x, u.y, head, filled = true).fill(color ?: INK_COLOR).stroke(color).width(0.8f)
    return body
}

/** Çift yönlü ölçü oku (uzunluk gösterimi). */
internal fun VectorBuilder.dim(x1: Float, y1: Float, x2: Float, y2: Float, head: Float = 0.035f, color: String? = null) {
    val u = unit(x2 - x1, y2 - y1)
    line(x1 + u.x * head * 0.6f, y1 + u.y * head * 0.6f, x2 - u.x * head * 0.6f, y2 - u.y * head * 0.6f).stroke(color).width(0.8f)
    arrowHead(x2, y2, u.x, u.y, head, filled = true).fill(color ?: INK_COLOR).stroke(color).width(0.6f)
    arrowHead(x1, y1, -u.x, -u.y, head, filled = true).fill(color ?: INK_COLOR).stroke(color).width(0.6f)
}

/** Kırık çizgi ışın; her parçanın ortasında yönü gösteren ok ucu. */
internal fun VectorBuilder.ray(vararg xy: Float, color: String? = Sc.RAY, head: Float = 0.04f, width: Float = 1.1f, heads: Boolean = true) {
    poly(*xy).stroke(color).width(width)
    if (!heads) return
    for (i in 0 until xy.size / 2 - 1) {
        val x1 = xy[i * 2]; val y1 = xy[i * 2 + 1]; val x2 = xy[i * 2 + 2]; val y2 = xy[i * 2 + 3]
        if (hypot(x2 - x1, y2 - y1) < head * 2.5f) continue
        val u = unit(x2 - x1, y2 - y1)
        val mx = (x1 + x2) / 2f + u.x * head / 2f; val my = (y1 + y2) / 2f + u.y * head / 2f
        arrowHead(mx, my, u.x, u.y, head, filled = true).fill(color ?: INK_COLOR).stroke(color).width(0.6f)
    }
}

/** A→B doğru parçası ve bir yanında eğik tarama (duvar, tavan, zemin). [side] +1/-1 hangi yana. */
internal fun VectorBuilder.hatch(ax: Float, ay: Float, bx: Float, by: Float, side: Float = 1f, gap: Float = 0.05f, len: Float = 0.04f) {
    line(ax, ay, bx, by).width(1.4f)
    val l = hypot(bx - ax, by - ay)
    val u = unit(bx - ax, by - ay)
    val n = P(-u.y * side, u.x * side)
    // tarama yönü: normal ile doğrultunun karışımı (45°)
    val d = unit(n.x - u.x, n.y - u.y)
    val count = (l / gap).toInt()
    val start = (l - count * gap) / 2f + gap / 2f
    for (i in 0 until count) {
        val s = start + i * gap
        val px = ax + u.x * s; val py = ay + u.y * s
        line(px, py, px + d.x * len * 1.2f, py + d.y * len * 1.2f).width(0.7f)
    }
}

/** Açı yayı (derece). */
internal fun VectorBuilder.angleArc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
    arc(cx, cy, r, r, start, sweep).width(0.8f)

/** Dik açı işareti: köşe (x,y), iki kol yönü (birim vektörlere çevrilir). */
internal fun VectorBuilder.rightMark(x: Float, y: Float, ux: Float, uy: Float, vx: Float, vy: Float, s: Float = 0.05f) {
    val u = unit(ux, uy); val v = unit(vx, vy)
    poly(x + u.x * s, y + u.y * s, x + u.x * s + v.x * s, y + u.y * s + v.y * s, x + v.x * s, y + v.y * s).width(0.8f)
}

/** Kenar üzerinde eşitlik çentikleri (A→B parçasının ortasında [count] tane). */
internal fun VectorBuilder.ticks(ax: Float, ay: Float, bx: Float, by: Float, count: Int = 1, size: Float = 0.03f, gap: Float = 0.018f) {
    val u = unit(bx - ax, by - ay)
    val n = P(-u.y, u.x)
    val mx = (ax + bx) / 2f; val my = (ay + by) / 2f
    for (i in 0 until count) {
        val o = (i - (count - 1) / 2f) * gap
        val cx = mx + u.x * o; val cy = my + u.y * o
        line(cx - n.x * size, cy - n.y * size, cx + n.x * size, cy + n.y * size).width(0.9f)
    }
}

/**
 * Helezon yay (yandan görünüş, halkalı): A'dan B'ye; [turns] sarım, [r] yarıçap. [pitch] (ξ→ξ') eksen boyunca
 * sıkışmayı değiştirmek için isteğe bağlı eşleme (0..1 → 0..1).
 */
internal fun VectorBuilder.coil(
    ax: Float, ay: Float, bx: Float, by: Float, turns: Int, r: Float,
    lead: Float = 0.06f, tilt: Float = 0.45f, pitch: ((Float) -> Float)? = null,
) {
    val l = hypot(bx - ax, by - ay)
    val u = unit(bx - ax, by - ay)
    val n = P(-u.y, u.x)
    val lc = l - lead * 2
    val steps = turns * 28
    val pts = ArrayList<P>(steps + 3)
    pts += P(ax, ay)
    for (i in 0..steps) {
        val s = i / steps.toFloat()
        val phi = 2.0 * PI * turns * s
        val xi = (pitch?.invoke(s) ?: s) * lc
        // eğik bakılan helezon: eksen boyunca r·tilt·cos φ salınımı halkaları oluşturur
        val along = lead + xi + r * tilt * (cos(phi).toFloat() - 1f)
        val perp = r * sin(phi).toFloat()
        pts += P(ax + u.x * along + n.x * perp, ay + u.y * along + n.y * perp)
    }
    pts += P(bx, by)
    points(pts)
}

/** Kalın çubuk (dikdörtgen gövde) A→B, yarı kalınlık [hw]. */
internal fun VectorBuilder.bar(ax: Float, ay: Float, bx: Float, by: Float, hw: Float): VectorBuilder.Part {
    val u = unit(bx - ax, by - ay)
    val n = P(-u.y * hw, u.x * hw)
    return points(listOf(P(ax + n.x, ay + n.y), P(bx + n.x, by + n.y), P(bx - n.x, by - n.y), P(ax - n.x, ay - n.y)), closed = true)
}

/**
 * Orta çizgisi [pts] olan şerit (boru, damar, bağırsak): kapalı çokgen noktaları. Yarı kalınlık baştan sona
 * [hw0] → [hw1] doğrusal değişir. Orta çizgi yumuşak ve sık örneklenmiş olmalı.
 */
internal fun ribbon(pts: List<P>, hw0: Float, hw1: Float = hw0): List<P> {
    val n = pts.size
    val left = ArrayList<P>(n); val right = ArrayList<P>(n)
    for (i in 0 until n) {
        val a = pts[maxOf(0, i - 1)]; val b = pts[minOf(n - 1, i + 1)]
        val u = unit(b.x - a.x, b.y - a.y)
        val hw = hw0 + (hw1 - hw0) * i / (n - 1).coerceAtLeast(1)
        left += P(pts[i].x - u.y * hw, pts[i].y + u.x * hw)
        right += P(pts[i].x + u.y * hw, pts[i].y - u.x * hw)
    }
    return left + right.reversed()
}

/** Yumuşatılmış orta çizgi noktaları (Catmull-Rom): x1, y1, x2, y2, ... */
internal fun spline(vararg xy: Float, steps: Int = 12): List<P> =
    VectorBuilder.catmullRom(List(xy.size / 2) { P(xy[it * 2], xy[it * 2 + 1]) }, closed = false, steps = steps)

/** Şerit çizimi: [ribbon] çokgeni. */
internal fun VectorBuilder.tube(pts: List<P>, hw0: Float, hw1: Float = hw0): VectorBuilder.Part = points(ribbon(pts, hw0, hw1), closed = true)

/** Parlak yüzey vurgusu (kürelerde üç boyut izlenimi). */
internal fun VectorBuilder.shine(cx: Float, cy: Float, r: Float) {
    ellipse(cx - r * 0.35f, cy - r * 0.4f, r * 0.28f, r * 0.18f, rotate = -35f).fill("#FFFFFF").noStroke().alpha(0.6f)
}
