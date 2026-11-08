package com.karapp.drawguidance.content.ink

import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * v37 yazı ağının eğitim ve sınama verisi: yazı tiplerinin glifleri çizilir, iskeletleri (Zhang–Suen inceltme) çıkarılır ve
 * çizgilere ayrılır (uç/kavşak noktaları arası kenarlar, kısa çıkıntılar budanır; kavşaklarda en düz devam eden kenarlar
 * birleştirilerek kalemin kalkmadan çizdiği çizgiler kurulur). Her örnekte el yazısı bozulmaları uygulanır: çizgi bölme
 * ve birleştirme, uç uzatma/kısaltma, çizgi başına kayma ve döndürme, eğim (italik), döndürme, en ölçeği, düşük frekanslı
 * bükülme, dalgalanma ve titreme.
 *
 * Koordinatlar em biriminde (yazı tipi boyu = 1), y aşağı, taban çizgisi y = 0 (harfler arasındaki göreli boy ve konum
 * korunur; sözcük kurarken kullanılır).
 */
internal object GlyphInk {
    private const val EM = 64f
    private const val PAD = 6

    class Pt(val x: Float, val y: Float)

    /** Glifin iskelet çizgileri: [separate] kenarlar ayrı, [joined] kavşaklarda birleşmiş; [advance] em biriminde genişlik. */
    class Skeleton(val separate: List<List<Pt>>, val joined: List<List<Pt>>) {
        val all: List<Pt> get() = separate.flatten()
        val minX get() = all.minOf { it.x }
        val maxX get() = all.maxOf { it.x }
        val minY get() = all.minOf { it.y }
        val maxY get() = all.maxOf { it.y }
    }

    private val appFonts = File("../../app/src/main/assets/fonts")

    private val fontCache = ConcurrentHashMap<String, Font>()

    /** Ad: sistem yazı tipi ailesi, "app:<ad>" (uygulamanın yazı tipleri) ya da "file:<yol>". */
    private val missingFonts = ConcurrentHashMap.newKeySet<String>()

    fun font(name: String): Font? = fontCache[name] ?: if (name in missingFonts) null else runCatching {
        val f = when {
            name.startsWith("app:") -> Font.createFont(Font.TRUETYPE_FONT, File(appFonts, name.removePrefix("app:") + ".ttf")).deriveFont(EM)
            name.startsWith("file:") -> Font.createFont(Font.TRUETYPE_FONT, File(name.removePrefix("file:"))).deriveFont(EM)
            else -> Font(name, Font.PLAIN, EM.toInt()).takeIf { it.family.equals(name, true) || it.fontName.equals(name, true) }
        }
        f?.also { fontCache[name] = it }
    }.getOrNull().also { if (it == null) missingFonts += name }

    private class Opt(val sk: Skeleton?)
    private val skeletons = ConcurrentHashMap<String, Opt>()

    /**
     * [text] glifinin iskeleti (önbellekli). [dropMarks]: en küçük bağlı parçalar atılır (ör. "?" noktası; tanıyıcının
     * gövdesi). Yazı tipi karakteri çizemiyorsa null.
     */
    fun skeleton(fontName: String, text: String, dropMarks: Boolean = false): Skeleton? = skeletons.getOrPut("$fontName|$text|$dropMarks") {
        val f = font(fontName)
        Opt(if (f == null || text.any { !f.canDisplay(it) }) null else runCatching { render(f, text, dropMarks) }.getOrNull())
    }.sk

    private fun render(font: Font, text: String, dropMarks: Boolean): Skeleton? {
        val frc = FontRenderContext(AffineTransform(), true, true)
        val gv = font.createGlyphVector(frc, text)
        val outline = gv.outline
        val b = outline.bounds2D
        if (b.width <= 0 || b.height <= 0) return null
        val w = b.width.toInt() + 2 * PAD + 2; val h = b.height.toInt() + 2 * PAD + 2
        val img = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val ox = PAD - b.x; val oy = PAD - b.y
        g.translate(ox, oy)
        g.color = java.awt.Color.WHITE
        g.fill(outline)
        g.dispose()
        val ras = img.raster
        val on = BooleanArray(w * h) { ras.getSample(it % w, it / w, 0) >= 110 }
        if (dropMarks) dropSmallComponents(on, w, h)
        thin(on, w, h)
        val graph = trace(on, w, h)
        fun toEm(poly: List<Pt>) = poly.map { Pt(((it.x - ox) / EM).toFloat(), ((it.y - oy) / EM).toFloat()) }
        val sep = graph.first.map { toEm(it) }
        val joined = graph.second.map { toEm(it) }
        if (sep.isEmpty()) return null
        return Skeleton(sep, joined)
    }

    /** En büyük parçanın %25'inden küçük bağlı parçalar silinir. */
    private fun dropSmallComponents(on: BooleanArray, w: Int, h: Int) {
        val label = IntArray(w * h) { -1 }
        val sizes = ArrayList<Int>()
        for (i in on.indices) {
            if (!on[i] || label[i] >= 0) continue
            val id = sizes.size
            var n = 0
            val stack = ArrayDeque<Int>(); stack.add(i); label[i] = id
            while (stack.isNotEmpty()) {
                val p = stack.removeLast(); n++
                val px = p % w; val py = p / w
                for (dy in -1..1) for (dx in -1..1) {
                    val qx = px + dx; val qy = py + dy
                    if (qx < 0 || qy < 0 || qx >= w || qy >= h) continue
                    val q = qy * w + qx
                    if (on[q] && label[q] < 0) { label[q] = id; stack.add(q) }
                }
            }
            sizes += n
        }
        if (sizes.size < 2) return
        val mx = sizes.max()
        for (i in on.indices) if (on[i] && sizes[label[i]] < 0.25f * mx) on[i] = false
    }

    /** Zhang–Suen inceltme (8 komşulu tek piksel iskelet). */
    private fun thin(on: BooleanArray, w: Int, h: Int) {
        fun v(x: Int, y: Int) = if (x < 0 || y < 0 || x >= w || y >= h) 0 else if (on[y * w + x]) 1 else 0
        var changed = true
        val del = ArrayList<Int>()
        while (changed) {
            changed = false
            for (step in 0..1) {
                del.clear()
                for (y in 0 until h) for (x in 0 until w) {
                    if (!on[y * w + x]) continue
                    val p2 = v(x, y - 1); val p3 = v(x + 1, y - 1); val p4 = v(x + 1, y); val p5 = v(x + 1, y + 1)
                    val p6 = v(x, y + 1); val p7 = v(x - 1, y + 1); val p8 = v(x - 1, y); val p9 = v(x - 1, y - 1)
                    val bn = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
                    if (bn < 2 || bn > 6) continue
                    val seq = intArrayOf(p2, p3, p4, p5, p6, p7, p8, p9, p2)
                    var a = 0
                    for (k in 0 until 8) if (seq[k] == 0 && seq[k + 1] == 1) a++
                    if (a != 1) continue
                    if (step == 0) { if (p2 * p4 * p6 != 0 || p4 * p6 * p8 != 0) continue }
                    else { if (p2 * p4 * p8 != 0 || p2 * p6 * p8 != 0) continue }
                    del += y * w + x
                }
                if (del.isNotEmpty()) { changed = true; del.forEach { on[it] = false } }
            }
        }
    }

    /**
     * İskeletten çizgiler: (ayrı kenarlar, kavşakta birleşmiş çizgiler). Uç: tek komşulu piksel; kavşak: geçiş sayısı
     * ≥ 3 (merdiven köşeleri kavşak sayılmaz). Kısa çıkıntılar (glif boyunun %10'u) budanır.
     */
    private fun trace(on: BooleanArray, w: Int, h: Int): Pair<List<List<Pt>>, List<List<Pt>>> {
        fun v(x: Int, y: Int) = x in 0 until w && y in 0 until h && on[y * w + x]
        val dirs = arrayOf(intArrayOf(0, -1), intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1), intArrayOf(-1, 0), intArrayOf(-1, -1))
        fun neighbors(p: Int): List<Int> {
            val x = p % w; val y = p / w
            val out = ArrayList<Int>(8)
            for (d in dirs) if (v(x + d[0], y + d[1])) out += (y + d[1]) * w + x + d[0]
            return out
        }
        fun crossings(p: Int): Int {
            val x = p % w; val y = p / w
            var c = 0
            for (k in 0 until 8) {
                val a = v(x + dirs[k][0], y + dirs[k][1]); val bb = v(x + dirs[(k + 1) % 8][0], y + dirs[(k + 1) % 8][1])
                if (!a && bb) c++
            }
            return c
        }
        val pixels = on.indices.filter { on[it] }
        if (pixels.isEmpty()) return emptyList<List<Pt>>() to emptyList()
        val node = HashMap<Int, Int>() // piksel → düğüm kimliği
        var nextNode = 0
        // kavşak pikselleri komşularıyla tek düğüm
        val junction = pixels.filter { neighbors(it).size >= 3 && crossings(it) >= 3 }.toHashSet()
        for (p in junction) {
            if (node.containsKey(p)) continue
            val id = nextNode++
            val stack = ArrayDeque<Int>(); stack.add(p); node[p] = id
            while (stack.isNotEmpty()) {
                val q = stack.removeLast()
                for (r in neighbors(q)) if (r in junction && !node.containsKey(r)) { node[r] = id; stack.add(r) }
            }
        }
        for (p in pixels) if (!node.containsKey(p) && neighbors(p).size <= 1) node[p] = nextNode++
        class Edge(val pts: MutableList<Int>, val a: Int, var b: Int)
        val edges = ArrayList<Edge>()
        val visited = HashSet<Int>()
        for (start in pixels) {
            val sn = node[start] ?: continue
            for (n0 in neighbors(start)) {
                if (node[n0] == sn) continue
                if (n0 in visited && node[n0] == null) continue
                val path = mutableListOf(start, n0)
                var prev = start; var cur = n0
                var end = node[cur]
                // aynı kavşak düğümünden çıkan iki piksel aynı kenarı iki kez başlatmasın
                if (end != null) {
                    if (end < sn) continue
                    if (end == sn) continue
                }
                while (end == null) {
                    visited += cur
                    val nb = neighbors(cur).filter { it != prev && (it !in visited || node[it] != null) && !(node[it] == sn && path.size < 3) }
                    if (nb.isEmpty()) break
                    // önce 4-komşu
                    val next = nb.minByOrNull { val dx = abs(it % w - cur % w); val dy = abs(it / w - cur / w); dx + dy }!!
                    path += next
                    prev = cur; cur = next
                    end = node[cur]
                }
                edges += Edge(path, sn, end ?: -1)
            }
        }
        // kalan (düğümsüz) halkalar: o, 0 gibi
        for (p in pixels) {
            if (node.containsKey(p) || p in visited) continue
            val path = mutableListOf(p)
            visited += p
            var cur = p
            while (true) {
                val nb = neighbors(cur).filter { it !in visited }
                if (nb.isEmpty()) break
                val next = nb.minByOrNull { abs(it % w - cur % w) + abs(it / w - cur / w) }!!
                path += next; visited += next; cur = next
            }
            path += p
            edges += Edge(path, -2, -2)
        }
        // tek piksellik noktalar
        val dots = pixels.filter { neighbors(it).isEmpty() }.map { mutableListOf(it) }
        fun len(e: Edge): Float { var l = 0f; for (i in 1 until e.pts.size) l += hypot((e.pts[i] % w - e.pts[i - 1] % w).toFloat(), (e.pts[i] / w - e.pts[i - 1] / w).toFloat()); return l }
        val extent = max(pixels.maxOf { it % w } - pixels.minOf { it % w }, pixels.maxOf { it / w } - pixels.minOf { it / w }).toFloat()
        // çıkıntı budama: bir ucu serbest (uç düğümü), öteki kavşak, kısa
        val degree = HashMap<Int, Int>()
        for (e in edges) { degree.merge(e.a, 1, Int::plus); degree.merge(e.b, 1, Int::plus) }
        val pruned = edges.filter { e ->
            val freeA = degree[e.a] == 1; val freeB = degree[e.b] == 1
            val spur = (freeA xor freeB) && len(e) < 0.1f * extent && (degree[if (freeA) e.b else e.a] ?: 0) >= 3
            !spur
        }
        fun pts(list: List<Int>) = list.map { Pt(it % w.toFloat(), (it / w).toFloat()) }
        val separate = pruned.map { smooth(pts(it.pts)) } + dots.map { pts(it) }
        // kavşakta birleştirme: en düz devam eden iki kenar (≥ 125°)
        class Piece(var pts: List<Pt>, var a: Int, var b: Int)
        val pieces = pruned.map { Piece(smooth(pts(it.pts)), it.a, it.b) }.toMutableList()
        fun outDir(p: List<Pt>, fromStart: Boolean): Pair<Float, Float> {
            val k = min(6, p.size - 1)
            val s = if (fromStart) p[0] else p[p.size - 1]
            val t = if (fromStart) p[k] else p[p.size - 1 - k]
            val dx = t.x - s.x; val dy = t.y - s.y
            val l = hypot(dx, dy).coerceAtLeast(1e-4f)
            return dx / l to dy / l
        }
        var merged = true
        while (merged) {
            merged = false
            val nodes = pieces.flatMap { listOf(it.a, it.b) }.filter { it >= 0 }.toSet()
            loop@ for (nd in nodes) {
                val ends = ArrayList<Pair<Piece, Boolean>>()
                for (pc in pieces) { if (pc.a == nd) ends += pc to true; if (pc.b == nd && pc.a != pc.b) ends += pc to false }
                if (ends.size < 2) continue
                var best = -0.57f; var bi = -1; var bj = -1
                for (i in ends.indices) for (j in i + 1 until ends.size) {
                    if (ends[i].first === ends[j].first) continue
                    val di = outDir(ends[i].first.pts, ends[i].second); val dj = outDir(ends[j].first.pts, ends[j].second)
                    val c = -(di.first * dj.first + di.second * dj.second)
                    if (c > best) { best = c; bi = i; bj = j }
                }
                if (bi < 0) continue
                val (p1, s1) = ends[bi]; val (p2, s2) = ends[bj]
                val a1 = if (s1) p1.pts.reversed() else p1.pts // nd'de biter
                val a2 = if (s2) p2.pts else p2.pts.reversed() // nd'de başlar
                val newA = if (s1) p1.b else p1.a
                val newB = if (s2) p2.b else p2.a
                pieces.remove(p1); pieces.remove(p2)
                pieces += Piece(a1 + a2.drop(1), newA, newB)
                merged = true
                break@loop
            }
        }
        val joined = pieces.map { it.pts } + dots.map { pts(it) }
        return separate.map { simplify(it) } to joined.map { simplify(it) }
    }

    private fun smooth(p: List<Pt>): List<Pt> {
        if (p.size < 3) return p
        return p.indices.map { i ->
            if (i == 0 || i == p.size - 1) p[i]
            else Pt((p[i - 1].x + 2 * p[i].x + p[i + 1].x) / 4f, (p[i - 1].y + 2 * p[i].y + p[i + 1].y) / 4f)
        }
    }

    private fun simplify(p: List<Pt>, eps: Float = 0.7f): List<Pt> {
        if (p.size < 3) return p
        val keep = BooleanArray(p.size); keep[0] = true; keep[p.size - 1] = true
        fun rdp(a: Int, b: Int) {
            if (b <= a + 1) return
            var best = -1f; var at = -1
            val ax = p[a].x; val ay = p[a].y; val bx = p[b].x; val by = p[b].y
            val len = hypot(bx - ax, by - ay)
            for (i in a + 1 until b) {
                val d = if (len < 1e-6f) hypot(p[i].x - ax, p[i].y - ay) else abs((bx - ax) * (ay - p[i].y) - (ax - p[i].x) * (by - ay)) / len
                if (d > best) { best = d; at = i }
            }
            if (best > eps) { keep[at] = true; rdp(a, at); rdp(at, b) }
        }
        rdp(0, p.size - 1)
        return p.filterIndexed { i, _ -> keep[i] }
    }

    // ---------------------------------------------------------------- el yazısı benzetimi

    private fun gauss(r: java.util.Random) = r.nextGaussian().toFloat().coerceIn(-2.5f, 2.5f)

    /**
     * İskeletten el yazısı (em birimi, taban y = 0). [amount] bozulma şiddeti (1 = olağan, 1.5 = sert).
     */
    fun handwrite(sk: Skeleton, r: java.util.Random, amount: Float = 1f): List<List<Pt>> {
        var strokes: MutableList<List<Pt>> = (if (r.nextFloat() < 0.65f) sk.joined else sk.separate).map { it }.toMutableList()
        val size = max(sk.maxX - sk.minX, sk.maxY - sk.minY).coerceAtLeast(0.05f)
        // köşede bölme
        if (r.nextFloat() < 0.3f * amount) {
            strokes = strokes.flatMap { st -> if (st.size >= 4 && r.nextFloat() < 0.5f) splitAtCorner(st) else listOf(st) }.toMutableList()
        }
        // yakın uçları birleştirme (kalem kalkmadan)
        if (strokes.size > 1 && r.nextFloat() < 0.3f * amount) {
            val i = r.nextInt(strokes.size)
            val a = strokes[i]
            if (a.size >= 2) {
                var bestJ = -1; var bestD = 0.12f * size; var rev = false
                for (j in strokes.indices) {
                    if (j == i || strokes[j].size < 2) continue
                    val d1 = hypot(a.last().x - strokes[j].first().x, a.last().y - strokes[j].first().y)
                    val d2 = hypot(a.last().x - strokes[j].last().x, a.last().y - strokes[j].last().y)
                    if (d1 < bestD) { bestD = d1; bestJ = j; rev = false }
                    if (d2 < bestD) { bestD = d2; bestJ = j; rev = true }
                }
                if (bestJ >= 0) {
                    val b = if (rev) strokes[bestJ].reversed() else strokes[bestJ]
                    val joined = a + b
                    val hi = max(i, bestJ); val lo = min(i, bestJ)
                    strokes.removeAt(hi); strokes.removeAt(lo)
                    strokes.add(joined)
                }
            }
        }
        // el yazısında iki nokta çoğu zaman kısa çizgi, üç nokta "^" biçiminde tek çizgi olur
        if (r.nextFloat() < 0.35f * amount) strokes = mergeDots(strokes, size, r)
        val cx = (sk.minX + sk.maxX) / 2f; val cy = (sk.minY + sk.maxY) / 2f
        // çizgi başına: uç uzatma/kısaltma, kayma, küçük döndürme
        val per = strokes.map { st ->
            var s = st
            if (s.size >= 2) {
                s = extend(s, true, gauss(r) * 0.05f * size * amount)
                s = extend(s, false, gauss(r) * 0.05f * size * amount)
            }
            val dx = gauss(r) * 0.022f * size * amount; val dy = gauss(r) * 0.022f * size * amount
            val rot = gauss(r) * 3f * amount * PI.toFloat() / 180f
            val mx = s.map { it.x }.average().toFloat(); val my = s.map { it.y }.average().toFloat()
            val c = cos(rot); val sn = sin(rot)
            s.map { p -> Pt(mx + (p.x - mx) * c - (p.y - my) * sn + dx, my + (p.x - mx) * sn + (p.y - my) * c + dy) }
        }
        // bütün glif: eğim, döndürme, en ölçeği, bükülme
        val slant = tan((gauss(r) * 7f * amount).coerceIn(-18f, 18f) * PI.toFloat() / 180f)
        val rot = gauss(r) * 3.5f * amount * PI.toFloat() / 180f
        val sx = (1f + gauss(r) * 0.1f * amount).coerceIn(0.75f, 1.3f)
        val wa = (0.01f + r.nextFloat() * 0.025f) * amount * size
        val f1 = 0.6f + r.nextFloat() * 1.4f; val f2 = 0.6f + r.nextFloat() * 1.4f
        val p1 = r.nextFloat() * 6.28f; val p2 = r.nextFloat() * 6.28f
        val c = cos(rot); val sn = sin(rot)
        return per.map { st ->
            val dense = densify(st, 0.012f * size)
            val ph1 = r.nextFloat() * 6.28f
            val amp = size * 0.006f * amount
            val n = dense.size
            dense.mapIndexed { i, p ->
                var x = p.x - cx; var y = p.y - cy
                x += wa * sin(f1 * 6.28f * (p.y - cy) / size + p1)
                y += wa * sin(f2 * 6.28f * (p.x - cx) / size + p2)
                x = (x + slant * -y) * sx
                val t = if (n > 1) i / (n - 1f) else 0f
                val wob = amp * sin(6.28f * 2f * t + ph1)
                val rx = x * c - y * sn + cx + wob + gauss(r) * 0.002f * size
                val ry = x * sn + y * c + cy + wob * 0.5f + gauss(r) * 0.002f * size
                Pt(rx, ry)
            }
        }
    }

    private fun mergeDots(strokes: MutableList<List<Pt>>, size: Float, r: java.util.Random): MutableList<List<Pt>> {
        fun ext(st: List<Pt>) = max(st.maxOf { it.x } - st.minOf { it.x }, st.maxOf { it.y } - st.minOf { it.y })
        val small = strokes.indices.filter { strokes[it].isNotEmpty() && ext(strokes[it]) < 0.1f * size }
        if (small.size < 2) return strokes
        val cx = small.associateWith { i -> strokes[i].map { it.x }.average().toFloat() }
        val cy = small.associateWith { i -> strokes[i].map { it.y }.average().toFloat() }
        val used = HashSet<Int>()
        val out = ArrayList<List<Pt>>()
        for (i in small) {
            if (i in used) continue
            val near = small.filter { j -> j !in used && hypot(cx.getValue(j) - cx.getValue(i), cy.getValue(j) - cy.getValue(i)) < 0.22f * size }
            if (near.size !in 2..3) continue
            used += near
            val pts = near.map { Pt(cx.getValue(it), cy.getValue(it)) }.sortedBy { it.x }
            out += if (pts.size == 2) {
                val a = pts[0]; val b = pts[1]
                val ex = (b.x - a.x) * 0.25f; val ey = (b.y - a.y) * 0.25f
                listOf(Pt(a.x - ex, a.y - ey), Pt(b.x + ex, b.y + ey))
            } else {
                val top = pts.minByOrNull { it.y }!!
                val rest = pts.filter { it !== top }
                if (r.nextBoolean()) listOf(rest[0], top, rest[1]) else listOf(rest[0], Pt(top.x, top.y - 0.03f * size), rest[1])
            }
        }
        if (used.isEmpty()) return strokes
        return (strokes.filterIndexed { i, _ -> i !in used } + out).toMutableList()
    }

    private fun splitAtCorner(st: List<Pt>): List<List<Pt>> {
        var best = 0f; var at = -1
        for (i in 1 until st.size - 1) {
            val ax = st[i].x - st[i - 1].x; val ay = st[i].y - st[i - 1].y
            val bx = st[i + 1].x - st[i].x; val by = st[i + 1].y - st[i].y
            val la = hypot(ax, ay); val lb = hypot(bx, by)
            if (la < 1e-4f || lb < 1e-4f) continue
            val ang = acos(((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f))
            if (ang > best) { best = ang; at = i }
        }
        if (at < 0 || best < 1.0f) return listOf(st)
        return listOf(st.subList(0, at + 1), st.subList(at, st.size))
    }

    private fun extend(st: List<Pt>, start: Boolean, d: Float): List<Pt> {
        if (abs(d) < 1e-5f || st.size < 2) return st
        val s = if (start) st.reversed() else st
        val a = s[s.size - 2]; val b = s[s.size - 1]
        val l = hypot(b.x - a.x, b.y - a.y)
        if (l < 1e-5f) return st
        val out = if (d > 0) s + Pt(b.x + (b.x - a.x) / l * d, b.y + (b.y - a.y) / l * d)
        else {
            // kısaltma: son parçadan en çok yarısı
            val cut = min(-d, 0.5f * l)
            s.dropLast(1) + Pt(b.x - (b.x - a.x) / l * cut, b.y - (b.y - a.y) / l * cut)
        }
        return if (start) out.reversed() else out
    }

    private fun densify(st: List<Pt>, step: Float): List<Pt> {
        if (st.size < 2) return st
        val out = ArrayList<Pt>()
        for (i in 0 until st.size - 1) {
            val a = st[i]; val b = st[i + 1]
            val n = max(1, (hypot(b.x - a.x, b.y - a.y) / step).toInt())
            for (k in 0 until n) out += Pt(a.x + (b.x - a.x) * k / n, a.y + (b.y - a.y) * k / n)
        }
        out += st.last()
        return out
    }

    /** Em birimindeki çizgiler → tanıyıcının çizgileri ([scale] piksel/em, [ox],[oy] konum). */
    fun toPolys(strokes: List<List<Pt>>, scale: Float = 60f, ox: Float = 0f, oy: Float = 0f): List<Poly> =
        strokes.filter { it.isNotEmpty() }.map { st -> Poly(FloatArray(st.size) { ox + st[it].x * scale }, FloatArray(st.size) { oy + st[it].y * scale }) }

    /** Em birimindeki çizgiler → kalem çizgileri (zaman damgasız). */
    fun toInk(strokes: List<List<Pt>>, scale: Float = 60f, ox: Float = 0f, oy: Float = 0f): List<InkStroke> =
        strokes.filter { it.isNotEmpty() }.map { st -> InkStroke(st.map { InkPoint(ox + it.x * scale, oy + it.y * scale) }) }

    @Suppress("unused")
    private fun angle(a: Pt, b: Pt) = atan2(b.y - a.y, b.x - a.x)

    @Suppress("unused")
    private fun dist(a: Pt, b: Pt) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
}
