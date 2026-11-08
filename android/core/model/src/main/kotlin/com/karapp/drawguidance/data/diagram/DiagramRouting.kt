package com.karapp.drawguidance.data.diagram

import com.karapp.drawguidance.data.model.DiagramEdge
import com.karapp.drawguidance.data.model.DiagramNode
import com.karapp.drawguidance.data.model.DiagramSide
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Diyagram alanında bir nokta (düğümlerle aynı birimde). */
data class RoutePoint(val x: Float, val y: Float)

/** Eksenlere paralel dikdörtgen (düğüm kutusu). */
data class RouteRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Diyagram oklarının yolu. Taraf seçilmemişse iki kutunun merkezlerini birleştiren düz ok kutu kenarlarında
 * kesilir; taraf seçilmişse ok o taraftan dik olarak çıkar/girer ve kutuların içinden geçmeyen, en az kırılımlı
 * dik açılı yol seçilir. Saf Kotlin: çizim, dokunma testi ve yazı konumu aynı yolu kullanır.
 */
object DiagramRouting {

    fun rectOf(node: DiagramNode, scale: Float = 1f) =
        RouteRect(node.x * scale, node.y * scale, (node.x + node.w) * scale, (node.y + node.h) * scale)

    /** Tarafın orta noktası (okun bağlandığı yer). */
    fun port(rect: RouteRect, side: DiagramSide): RoutePoint = when (side) {
        DiagramSide.TOP -> RoutePoint(rect.centerX, rect.top)
        DiagramSide.RIGHT -> RoutePoint(rect.right, rect.centerY)
        DiagramSide.BOTTOM -> RoutePoint(rect.centerX, rect.bottom)
        DiagramSide.LEFT -> RoutePoint(rect.left, rect.centerY)
        DiagramSide.AUTO -> RoutePoint(rect.centerX, rect.centerY)
    }

    /** Tarafın dışa bakan birim yönü. */
    fun direction(side: DiagramSide): RoutePoint = when (side) {
        DiagramSide.TOP -> RoutePoint(0f, -1f)
        DiagramSide.RIGHT -> RoutePoint(1f, 0f)
        DiagramSide.BOTTOM -> RoutePoint(0f, 1f)
        DiagramSide.LEFT -> RoutePoint(-1f, 0f)
        DiagramSide.AUTO -> RoutePoint(0f, 0f)
    }

    /** Kutunun [p] noktasına en yakın tarafı (kutu boyutuna göre oranlanmış). */
    fun nearestSide(rect: RouteRect, p: RoutePoint): DiagramSide {
        val nx = (p.x - rect.centerX) / max(1e-3f, rect.width / 2f)
        val ny = (p.y - rect.centerY) / max(1e-3f, rect.height / 2f)
        return if (abs(nx) >= abs(ny)) (if (nx >= 0f) DiagramSide.RIGHT else DiagramSide.LEFT)
        else (if (ny >= 0f) DiagramSide.BOTTOM else DiagramSide.TOP)
    }

    /**
     * Kutuya dokunulan yere göre taraf: kenarlara yakın dokunuş o tarafı, ortadaki [center] oranındaki bölge
     * otomatik tarafı (karşıya bakan) verir.
     */
    fun sideAt(rect: RouteRect, p: RoutePoint, center: Float = CENTER_ZONE): DiagramSide {
        val nx = (p.x - rect.centerX) / max(1e-3f, rect.width / 2f)
        val ny = (p.y - rect.centerY) / max(1e-3f, rect.height / 2f)
        return if (max(abs(nx), abs(ny)) < center) DiagramSide.AUTO else nearestSide(rect, p)
    }

    /** Kutunun ortasında taraf seçmeyen bölgenin oranı (yarı genişliğe göre). */
    const val CENTER_ZONE = 0.4f

    /** Kutudan [toward] noktasına bakan taraf (taraf seçilmemiş ucun karşısına göre). */
    private fun facingSide(rect: RouteRect, toward: RoutePoint): DiagramSide = nearestSide(rect, toward)

    /** Okun yolu (en az iki nokta); ilk nokta [a] kutusunda, son nokta [b] kutusundadır. */
    fun route(a: RouteRect, b: RouteRect, fromSide: DiagramSide, toSide: DiagramSide, margin: Float): List<RoutePoint> {
        if (fromSide == DiagramSide.AUTO && toSide == DiagramSide.AUTO) return straight(a, b)
        val sa = if (fromSide == DiagramSide.AUTO) facingSide(a, RoutePoint(b.centerX, b.centerY)) else fromSide
        val sb = if (toSide == DiagramSide.AUTO) facingSide(b, port(a, sa)) else toSide
        val s = port(a, sa)
        val e = port(b, sb)
        val da = direction(sa)
        val db = direction(sb)
        val p1 = RoutePoint(s.x + da.x * margin, s.y + da.y * margin)
        val p2 = RoutePoint(e.x + db.x * margin, e.y + db.y * margin)
        val mx = (p1.x + p2.x) / 2f
        val my = (p1.y + p2.y) / 2f
        val candidates = listOf(
            listOf(s, RoutePoint(e.x, s.y), e),
            listOf(s, RoutePoint(s.x, e.y), e),
            listOf(s, RoutePoint(mx, s.y), RoutePoint(mx, e.y), e),
            listOf(s, RoutePoint(s.x, my), RoutePoint(e.x, my), e),
            listOf(s, p1, RoutePoint(p1.x, p2.y), p2, e),
            listOf(s, p1, RoutePoint(p2.x, p1.y), p2, e),
            listOf(s, p1, RoutePoint(mx, p1.y), RoutePoint(mx, p2.y), p2, e),
            listOf(s, p1, RoutePoint(p1.x, my), RoutePoint(p2.x, my), p2, e),
            // Kutuların dışından dolaşan yollar (aynı yöne bakan taraflar)
            outside(s, e, p1, p2, a, b, margin, horizontal = true),
            outside(s, e, p1, p2, a, b, margin, horizontal = false),
        ).map { simplify(it) }
        return candidates.minByOrNull { score(it, da, db, a, b) } ?: listOf(s, e)
    }

    /** Okun yolu; düğümler bulunamazsa null. */
    fun route(edge: DiagramEdge, from: DiagramNode, to: DiagramNode, scale: Float = 1f, margin: Float = 0.03f * scale): List<RoutePoint> =
        route(rectOf(from, scale), rectOf(to, scale), edge.fromSide, edge.toSide, margin)

    /** Yol üzerinde yarı uzunluktaki nokta (ok yazısı buraya konur). */
    fun midpoint(path: List<RoutePoint>): RoutePoint {
        if (path.size < 2) return path.firstOrNull() ?: RoutePoint(0f, 0f)
        val total = length(path)
        var left = total / 2f
        for (i in 1 until path.size) {
            val a = path[i - 1]
            val b = path[i]
            val l = hypot(b.x - a.x, b.y - a.y)
            if (l >= left && l > 0f) {
                val t = left / l
                return RoutePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            left -= l
        }
        return path.last()
    }

    /** [p] noktasının yola en kısa uzaklığı. */
    fun distance(path: List<RoutePoint>, p: RoutePoint): Float {
        if (path.size == 1) return hypot(p.x - path[0].x, p.y - path[0].y)
        var best = Float.MAX_VALUE
        for (i in 1 until path.size) best = min(best, segmentDistance(p, path[i - 1], path[i]))
        return best
    }

    fun length(path: List<RoutePoint>): Float {
        var total = 0f
        for (i in 1 until path.size) total += hypot(path[i].x - path[i - 1].x, path[i].y - path[i - 1].y)
        return total
    }

    // --- Yardımcılar ---

    private fun straight(a: RouteRect, b: RouteRect): List<RoutePoint> {
        val ca = RoutePoint(a.centerX, a.centerY)
        val cb = RoutePoint(b.centerX, b.centerY)
        return listOf(border(a, ca, cb), border(b, cb, ca))
    }

    /** Kutunun kenarında, merkezden [towards] yönündeki nokta. */
    private fun border(r: RouteRect, center: RoutePoint, towards: RoutePoint): RoutePoint {
        val dx = towards.x - center.x
        val dy = towards.y - center.y
        if (abs(dx) < 1e-4f && abs(dy) < 1e-4f) return center
        val tx = if (abs(dx) < 1e-4f) Float.MAX_VALUE else (r.width / 2f) / abs(dx)
        val ty = if (abs(dy) < 1e-4f) Float.MAX_VALUE else (r.height / 2f) / abs(dy)
        val t = min(tx, ty)
        return RoutePoint(center.x + dx * t, center.y + dy * t)
    }

    /** İki kutunun da dışından (üstten/alttan ya da soldan/sağdan) dolaşan yol. */
    private fun outside(
        s: RoutePoint, e: RoutePoint, p1: RoutePoint, p2: RoutePoint,
        a: RouteRect, b: RouteRect, margin: Float, horizontal: Boolean,
    ): List<RoutePoint> {
        return if (horizontal) {
            // Üstten ya da alttan: hangisi kısaysa
            val top = min(a.top, b.top) - margin
            val bottom = max(a.bottom, b.bottom) + margin
            val y = if (abs(p1.y - top) + abs(p2.y - top) <= abs(p1.y - bottom) + abs(p2.y - bottom)) top else bottom
            listOf(s, p1, RoutePoint(p1.x, y), RoutePoint(p2.x, y), p2, e)
        } else {
            val left = min(a.left, b.left) - margin
            val right = max(a.right, b.right) + margin
            val x = if (abs(p1.x - left) + abs(p2.x - left) <= abs(p1.x - right) + abs(p2.x - right)) left else right
            listOf(s, p1, RoutePoint(x, p1.y), RoutePoint(x, p2.y), p2, e)
        }
    }

    /** Art arda aynı/aynı doğrultudaki noktaları sadeleştirir. */
    private fun simplify(path: List<RoutePoint>): List<RoutePoint> {
        val out = ArrayList<RoutePoint>(path.size)
        for (p in path) {
            if (out.isNotEmpty() && abs(out.last().x - p.x) < 1e-4f && abs(out.last().y - p.y) < 1e-4f) continue
            if (out.size >= 2) {
                val a = out[out.size - 2]
                val b = out.last()
                val collinear = (abs(a.x - b.x) < 1e-4f && abs(b.x - p.x) < 1e-4f) || (abs(a.y - b.y) < 1e-4f && abs(b.y - p.y) < 1e-4f)
                if (collinear) out[out.size - 1] = p else out += p
            } else out += p
        }
        return out
    }

    /**
     * Yolun puanı (küçük olan seçilir): uzunluk + kırılım cezası; kutuların içinden geçmek, eğik parça ve okun
     * taraftan dik çıkmaması/girmemesi büyük ceza alır.
     */
    private fun score(path: List<RoutePoint>, da: RoutePoint, db: RoutePoint, a: RouteRect, b: RouteRect): Float {
        if (path.size < 2) return Float.MAX_VALUE
        var penalty = 0f
        val scale = max(max(a.width, a.height), max(b.width, b.height)).coerceAtLeast(1e-3f)
        for (i in 1 until path.size) {
            val p = path[i - 1]
            val q = path[i]
            if (abs(p.x - q.x) > 1e-4f && abs(p.y - q.y) > 1e-4f) penalty += 1000f * scale
            // İlk parça başlangıç kutusundan, son parça bitiş kutusundan çıkabilir; diğerleri kutulara girmemeli
            if (i > 1 && crosses(p, q, a)) penalty += 100f * scale
            if (i < path.size - 1 && crosses(p, q, b)) penalty += 100f * scale
            if (i == 1 && crosses(p, q, b)) penalty += 100f * scale
            if (i == path.size - 1 && crosses(p, q, a)) penalty += 100f * scale
        }
        // Çıkış yönü tarafın dışına, giriş yönü hedefin tarafından içeri olmalı
        val first = unit(path[0], path[1])
        val last = unit(path[path.size - 2], path[path.size - 1])
        if (first.x * da.x + first.y * da.y < 0.5f) penalty += 50f * scale
        if (last.x * -db.x + last.y * -db.y < 0.5f) penalty += 50f * scale
        return length(path) + (path.size - 2) * 0.15f * scale + penalty
    }

    private fun unit(a: RoutePoint, b: RoutePoint): RoutePoint {
        val l = hypot(b.x - a.x, b.y - a.y)
        return if (l < 1e-6f) RoutePoint(0f, 0f) else RoutePoint((b.x - a.x) / l, (b.y - a.y) / l)
    }

    /** Eksene paralel parça kutunun içinden (kenarından değil) geçiyor mu. */
    private fun crosses(p: RoutePoint, q: RoutePoint, r: RouteRect): Boolean {
        val inset = min(r.width, r.height) * 0.02f
        val l = r.left + inset; val t = r.top + inset; val rr = r.right - inset; val bb = r.bottom - inset
        val minX = min(p.x, q.x); val maxX = max(p.x, q.x)
        val minY = min(p.y, q.y); val maxY = max(p.y, q.y)
        return maxX > l && minX < rr && maxY > t && minY < bb
    }

    private fun segmentDistance(p: RoutePoint, a: RoutePoint, b: RoutePoint): Float {
        val vx = b.x - a.x; val vy = b.y - a.y
        val len2 = vx * vx + vy * vy
        val t = if (len2 < 1e-9f) 0f else (((p.x - a.x) * vx + (p.y - a.y) * vy) / len2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
    }
}
