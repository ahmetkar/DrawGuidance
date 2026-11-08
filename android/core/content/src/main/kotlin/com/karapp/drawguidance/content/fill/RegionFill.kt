package com.karapp.drawguidance.content.fill

import com.karapp.drawguidance.content.vector.P
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Alan alan boyama (boyama kitabı gibi): çizgilerle çevrili alanın sınırı bulunur. Çizgiler bir ızgaraya kalınlıklarıyla
 * çizilir, dokunulan yerden taşma doldurma yapılır (dışarı taşıyorsa alan kapalı değildir); sınır çizginin iç kenarında
 * (bir hücre çizginin altına girer: çizgiyle dolgu arasında boşluk kalmaz, dolgu çizgilerin üstüne çizilse de çizgiyi
 * örtmez), piksel kenarlarından izlenir; alanın içindeki adacıklar (ör. yüzdeki göz) delik olarak kalır — delikler dış
 * sınıra ince bir köprüyle bağlanır, böylece sonuç tek bir kapalı yoldur ve sıfırdan farklı sarım kuralıyla doğru dolar.
 * Saf ve durumsuzdur.
 */
object RegionFill {

    /** Bir çizgi: noktalar (tuval birimi), kapalı mı, kalınlığı. */
    class Line(val points: List<P>, val closed: Boolean, val width: Float)

    /**
     * [tap] noktasını içeren kapalı alanın sınırı (tuval biriminde, tek kapalı yol) ya da alan kapalı değilse / çizginin
     * üstüne dokunulduysa null. [resolution] ızgaranın uzun kenarı (0: en ince çizgi en az üç hücre olacak biçimde,
     * 500..1400), [gap] kapatılacak küçük boşluk (tuval birimi).
     */
    fun region(lines: List<Line>, tap: P, resolution: Int = 0, gap: Float = 0f): List<P>? {
        val m = fillMask(lines, tap, resolution, gap) ?: return null
        val loops = boundaries(m.cells, m.w, m.h)
        if (loops.isEmpty()) return null
        val toCanvas: (Pair<Int, Int>) -> P = { (x, y) -> m.toCanvas(x, y) }
        val polys = loops.map { loop -> simplify(loop.map(toCanvas), 0.7f / m.scale) }.filter { it.size >= 3 }
        if (polys.isEmpty()) return null
        val outer = polys.maxBy { abs(area(it)) }
        val holes = polys.filter { it !== outer }
        return keyhole(orient(outer, positive = true), holes.map { orient(it, positive = false) })
    }

    /**
     * v45 alanın ızgaradaki maskesi: [cells] (w×h, satır satır) alanın hücreleri (çizginin altına giren bir hücre
     * dahil); ızgara hücresi (x, y) tuvalde ([ox] + x / [scale], [oy] + y / [scale]) köşesindedir.
     */
    class Mask(val cells: BooleanArray, val w: Int, val h: Int, val ox: Float, val oy: Float, val scale: Float) {
        fun toCanvas(x: Int, y: Int): P = P(ox + x / scale, oy + y / scale)
        fun gx(x: Float): Float = (x - ox) * scale
        fun gy(y: Float): Float = (y - oy) * scale
    }

    /**
     * v45 [region] ile aynı ızgara ve taşma doldurmayla alanın maskesi (boya fırçası boyayı bu maskeyle kırpar); alan
     * kapalı değilse ya da çizginin üstüne dokunulduysa null.
     */
    fun fillMask(lines: List<Line>, tap: P, resolution: Int = 0, gap: Float = 0f): Mask? {
        if (lines.isEmpty()) return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        var maxW = 0f
        var minW = Float.MAX_VALUE
        lines.forEach { line ->
            maxW = max(maxW, line.width)
            minW = min(minW, line.width)
            line.points.forEach { l = min(l, it.x); t = min(t, it.y); r = max(r, it.x); b = max(b, it.y) }
        }
        if (tap.x < l || tap.x > r || tap.y < t || tap.y > b) return null
        val span = max(r - l, b - t).coerceAtLeast(1f)
        val res = if (resolution > 0) resolution else (span * 3f / minW.coerceAtLeast(0.2f)).toInt().coerceIn(500, 1400)
        val scale = res / span
        val gapCells = gap * scale
        val pad = 2 + ceil(maxW * scale + gapCells).toInt()
        val w = ceil((r - l) * scale).toInt() + pad * 2 + 1
        val h = ceil((b - t) * scale).toInt() + pad * 2 + 1
        val ox = l - pad / scale
        val oy = t - pad / scale
        fun gx(x: Float) = (x - ox) * scale
        fun gy(y: Float) = (y - oy) * scale
        val wall = BooleanArray(w * h)
        // Çizgileri kalınlıklarıyla ızgaraya çiz
        val half = FloatArray(1)
        lines.forEach { line ->
            val rad = max(0.75f, line.width * scale / 2f) + gapCells
            half[0] = rad
            val pts = if (line.closed && line.points.size > 2) line.points + line.points.first() else line.points
            if (pts.size == 1) {
                stamp(wall, w, h, gx(pts[0].x), gy(pts[0].y), gx(pts[0].x), gy(pts[0].y), rad)
            }
            for (i in 1 until pts.size) stamp(wall, w, h, gx(pts[i - 1].x), gy(pts[i - 1].y), gx(pts[i].x), gy(pts[i].y), rad)
        }
        // Taşma doldurma
        var sx = gx(tap.x).toInt().coerceIn(0, w - 1)
        var sy = gy(tap.y).toInt().coerceIn(0, h - 1)
        if (wall[sy * w + sx]) {
            // Çizginin kenarına dokunulduysa en yakın boş hücre (2 hücre içinde)
            var found = false
            loop@ for (d in 1..2) for (dy in -d..d) for (dx in -d..d) {
                val x = sx + dx; val y = sy + dy
                if (x in 0 until w && y in 0 until h && !wall[y * w + x]) { sx = x; sy = y; found = true; break@loop }
            }
            if (!found) return null
        }
        val mask = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var qh = 0; var qt = 0
        mask[sy * w + sx] = true
        queue[qt++] = sy * w + sx
        while (qh < qt) {
            val i = queue[qh++]
            val x = i % w; val y = i / w
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) return null // dışarı taşıyor: kapalı alan değil
            val n = intArrayOf(i - 1, i + 1, i - w, i + w)
            for (j in n) if (!mask[j] && !wall[j]) { mask[j] = true; queue[qt++] = j }
        }
        // Dolgu çizginin altına bir hücre (ve kapatılan boşluk kadar) girer: kenarda boşluk kalmaz, çizgi örtülmez
        val grow = 1 + gapCells.toInt()
        var frontier = IntArray(qt).also { System.arraycopy(queue, 0, it, 0, qt) }
        var fsize = qt
        repeat(grow) {
            val next = IntArray(fsize * 4 + 4)
            var ns = 0
            for (k in 0 until fsize) {
                val i = frontier[k]
                val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx <= 0 || ny <= 0 || nx >= w - 1 || ny >= h - 1) continue
                    val j = ny * w + nx
                    if (!mask[j] && wall[j]) {
                        mask[j] = true
                        if (ns >= next.size) break
                        next[ns++] = j
                    }
                }
            }
            frontier = next; fsize = ns
        }
        return Mask(mask, w, h, ox, oy, scale)
    }

    /** İki nokta arasındaki kalın çizgiyi (yarıçap [rad] hücre) ızgaraya işler. */
    internal fun stamp(wall: BooleanArray, w: Int, h: Int, x0: Float, y0: Float, x1: Float, y1: Float, rad: Float) {
        val minX = max(0, floor(min(x0, x1) - rad).toInt())
        val maxX = min(w - 1, ceil(max(x0, x1) + rad).toInt())
        val minY = max(0, floor(min(y0, y1) - rad).toInt())
        val maxY = min(h - 1, ceil(max(y0, y1) + rad).toInt())
        val vx = x1 - x0; val vy = y1 - y0
        val len2 = vx * vx + vy * vy
        val r2 = rad * rad
        for (y in minY..maxY) for (x in minX..maxX) {
            val px = x + 0.5f; val py = y + 0.5f
            val tt = if (len2 < 1e-6f) 0f else (((px - x0) * vx + (py - y0) * vy) / len2).coerceIn(0f, 1f)
            val dx = px - (x0 + vx * tt); val dy = py - (y0 + vy * tt)
            if (dx * dx + dy * dy <= r2) wall[y * w + x] = true
        }
    }

    /**
     * Maskenin sınırları (piksel kenarlarından, köşe noktaları ızgara kesişimleri): dış sınır ve delikler ayrı döngüler.
     * Her hücrenin maske dışına bakan kenarı yönlü bir kenar olur (maske solda); kenarlar döngülere bağlanır.
     */
    internal fun boundaries(mask: BooleanArray, w: Int, h: Int): List<List<Pair<Int, Int>>> {
        fun m(x: Int, y: Int) = x in 0 until w && y in 0 until h && mask[y * w + x]
        // Yönlü kenarlar: başlangıç köşesi → bitiş köşesi (köşe = y*(w+1)+x)
        val next = HashMap<Long, MutableList<Long>>()
        fun key(x: Int, y: Int): Long = y.toLong() * (w + 1) + x
        fun add(ax: Int, ay: Int, bx: Int, by: Int) { next.getOrPut(key(ax, ay)) { ArrayList(1) } += key(bx, by) }
        for (y in 0 until h) for (x in 0 until w) {
            if (!mask[y * w + x]) continue
            // Ekran koordinatında (y aşağı) saat yönünün tersi: maske solda kalır
            if (!m(x, y - 1)) add(x + 1, y, x, y)       // üst kenar: sağdan sola
            if (!m(x - 1, y)) add(x, y, x, y + 1)       // sol kenar: yukarıdan aşağı
            if (!m(x, y + 1)) add(x, y + 1, x + 1, y + 1) // alt kenar: soldan sağa
            if (!m(x + 1, y)) add(x + 1, y + 1, x + 1, y) // sağ kenar: aşağıdan yukarı
        }
        val loops = ArrayList<List<Pair<Int, Int>>>()
        val stride = (w + 1).toLong()
        while (next.isNotEmpty()) {
            val start = next.keys.first()
            val loop = ArrayList<Pair<Int, Int>>()
            var cur = start
            var prev = -1L
            var guard = 0
            while (guard++ < 4_000_000) {
                val outs = next[cur] ?: break
                val chosen = if (outs.size == 1 || prev < 0) outs[0] else {
                    // Çapraz dokunan hücrelerde sola dönüş tercih edilir (döngüler kesişmez)
                    val px = (prev % stride).toInt(); val py = (prev / stride).toInt()
                    val cx = (cur % stride).toInt(); val cy = (cur / stride).toInt()
                    val dx0 = cx - px; val dy0 = cy - py
                    outs.maxBy { o ->
                        val ox = (o % stride).toInt() - cx; val oy = (o / stride).toInt() - cy
                        // Ekran koordinatında sola dönüş: çapraz çarpım negatif
                        -(dx0 * oy - dy0 * ox)
                    }
                }
                outs.remove(chosen)
                if (outs.isEmpty()) next.remove(cur)
                loop += (cur % stride).toInt() to (cur / stride).toInt()
                prev = cur
                cur = chosen
                if (cur == start) break
            }
            if (loop.size >= 4) loops += loop
        }
        return loops
    }

    internal fun area(p: List<P>): Float {
        var a = 0f
        for (i in p.indices) { val q = p[(i + 1) % p.size]; a += p[i].x * q.y - q.x * p[i].y }
        return a / 2f
    }

    internal fun orient(p: List<P>, positive: Boolean): List<P> = if ((area(p) > 0f) == positive) p else p.reversed()

    /** Delikleri dış sınıra en yakın noktalardan sıfır genişlikte köprüyle bağlar (tek kapalı yol). */
    internal fun keyhole(outer: List<P>, holes: List<List<P>>): List<P> {
        var result = outer
        for (hole in holes.sortedByDescending { abs(area(it)) }) {
            var bi = 0; var bj = 0; var bd = Float.MAX_VALUE
            for (i in result.indices) for (j in hole.indices) {
                val d = hypot(result[i].x - hole[j].x, result[i].y - hole[j].y)
                if (d < bd) { bd = d; bi = i; bj = j }
            }
            val spliced = ArrayList<P>(result.size + hole.size + 2)
            for (i in 0..bi) spliced += result[i]
            for (k in 0..hole.size) spliced += hole[(bj + k) % hole.size]
            spliced += result[bi]
            for (i in bi + 1 until result.size) spliced += result[i]
            result = spliced
        }
        return result
    }

    /** Kapalı çokgeni sadeleştirir (piksel basamakları düzleşir). */
    internal fun simplify(p: List<P>, eps: Float): List<P> {
        if (p.size < 8) return p
        val n = p.size
        val keep = BooleanArray(n)
        val far = (1 until n).maxBy { hypot(p[it].x - p[0].x, p[it].y - p[0].y) }
        keep[0] = true; keep[far] = true
        val ext = p + p[0]
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to far); stack.addLast(far to n)
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            if (e <= s + 1) continue
            var best = -1; var bd = 0f
            for (i in s + 1 until e) {
                val d = seg(ext[i], ext[s], ext[e])
                if (d > bd) { bd = d; best = i }
            }
            if (best >= 0 && bd > eps) { keep[best % n] = true; stack.addLast(s to best); stack.addLast(best to e) }
        }
        return p.filterIndexed { i, _ -> keep[i] }
    }

    private fun seg(q: P, a: P, b: P): Float {
        val vx = b.x - a.x; val vy = b.y - a.y
        val l2 = vx * vx + vy * vy
        if (l2 < 1e-9f) return hypot(q.x - a.x, q.y - a.y)
        val t = (((q.x - a.x) * vx + (q.y - a.y) * vy) / l2).coerceIn(0f, 1f)
        return hypot(q.x - (a.x + vx * t), q.y - (a.y + vy * t))
    }

    /** Nokta çokgenin içinde mi (tek-çift kuralı; köprülü deliklerde de doğru). */
    fun contains(poly: List<P>, q: P): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > q.y) != (b.y > q.y) && q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y + 1e-9f) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    fun areaOf(poly: List<P>): Float = abs(area(poly))

    /**
     * Çokgenin içinde kalan bir nokta (alan yeniden bulunurken tohum olarak): ortadaki yatay taramada en geniş iç
     * aralığın ortası; bulunamazsa null.
     */
    fun interiorPoint(poly: List<P>): P? {
        if (poly.size < 3) return null
        var t = Float.MAX_VALUE; var b = -Float.MAX_VALUE
        poly.forEach { t = min(t, it.y); b = max(b, it.y) }
        for (f in floatArrayOf(0.5f, 0.35f, 0.65f, 0.2f, 0.8f)) {
            val y = t + (b - t) * f
            val xs = ArrayList<Float>()
            var j = poly.size - 1
            for (i in poly.indices) {
                val a = poly[i]; val c = poly[j]
                if ((a.y > y) != (c.y > y)) xs += (c.x - a.x) * (y - a.y) / (c.y - a.y) + a.x
                j = i
            }
            xs.sort()
            var best: P? = null; var bw = 0f
            var k = 0
            while (k + 1 < xs.size) {
                val wdt = xs[k + 1] - xs[k]
                if (wdt > bw) { bw = wdt; best = P((xs[k] + xs[k + 1]) / 2f, y) }
                k += 2
            }
            if (best != null && contains(poly, best)) return best
        }
        return null
    }
}
