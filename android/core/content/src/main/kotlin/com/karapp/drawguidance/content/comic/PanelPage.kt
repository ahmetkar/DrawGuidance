package com.karapp.drawguidance.content.comic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * v36 çizgi roman sayfa düzeni (şablonların ve sayfadaki panellerin ortak modeli). Sayfa, kenar boşluğu içinde
 * art arda bölünür: bir bölme ([PanelNode.Split]) alanı üst üste ya da yan yana çocuklara oranlarla ([weights])
 * ayırır; bölme çizgileri eğik olabilir ([skews]). Yapraklar paneldir ([PanelNode.Leaf]) ya da boş alandır
 * ([PanelNode.Space], webtoon'da kaydırılmış paneller için). Paneller arasındaki aralık her yerde aynıdır (eğik
 * çizgilerde de dik uzaklık). İç paneller ([PanelInset]) başka bir panelin üstünde durur.
 *
 * Düzen bir kez kurulur, panellerin köşeleri sayfa boyutundan hesaplanır ([shapes]); düzenleme işlemleri (boyut,
 * sıra, aralık, ekleme, silme) yeni bir düzen döndürür. Kısa metin biçimi ([encode]/[decode]) her panelde saklanır.
 */
sealed class PanelNode {
    /** Panel: [id] düzendeki kimliği; köşe yuvarlaklığı [round] ve köşe pahı [bevel] sayfanın kısa kenarına oran. */
    data class Leaf(val id: Int, val round: Float = 0f, val bevel: Float = 0f) : PanelNode()

    /** Boş alan (panel çizilmez). */
    data object Space : PanelNode()

    /**
     * Bölme: [rows] ise çocuklar üst üste, değilse yan yana. [weights] çocukların oranları, [skews] aralarındaki
     * çizgilerin eğimi (n-1 tane; yan yana bölmede çizginin üst ucu yüksekliğin skew/2'si kadar sağa, alt ucu sola
     * kayar). [gap] bu bölmedeki aralığın çarpanı (webtoon'da geniş dikey aralık).
     */
    data class Split(
        val rows: Boolean,
        val children: List<PanelNode>,
        val weights: List<Float>,
        val skews: List<Float> = List(max(0, children.size - 1)) { 0f },
        val gap: Float = 1f,
    ) : PanelNode()
}

/**
 * İç panel: sayfanın iç alanına göre oranlarla ([l], [t], [r], [b]) başka panellerin üstünde durur (beyaz dolgulu).
 * [oval] elips; [round] köşe yuvarlaklığı. Okuma sırasında [after] kimlikli panelden sonra gelir (-1: en sonda).
 */
data class PanelInset(
    val id: Int, val l: Float, val t: Float, val r: Float, val b: Float,
    val oval: Boolean = false, val round: Float = 0f, val after: Int = -1,
)

/** Hesaplanmış panel: kimlik, okuma sırası ([number], 1'den), köşe noktaları (x0, y0, x1, y1…; kapalı). */
class PanelShape(val id: Int, val number: Int, val points: FloatArray, val inset: Boolean) {
    val size: Int get() = points.size / 2
    fun x(i: Int) = points[2 * i]
    fun y(i: Int) = points[2 * i + 1]

    /** Sınırlar: sol, üst, sağ, alt. */
    fun bounds(): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until size) { l = min(l, x(i)); r = max(r, x(i)); t = min(t, y(i)); b = max(b, y(i)) }
        return floatArrayOf(l, t, r, b)
    }

    /** Nokta panelin içinde mi (çift-tek kuralı). */
    fun contains(px: Float, py: Float): Boolean {
        var inside = false
        var j = size - 1
        for (i in 0 until size) {
            val yi = y(i); val yj = y(j)
            if ((yi > py) != (yj > py) && px < (x(j) - x(i)) * (py - yi) / (yj - yi) + x(i)) inside = !inside
            j = i
        }
        return inside
    }
}

data class PanelPage(
    val root: PanelNode?,
    /** Sayfa kenar boşluğu (kısa kenarın oranı). */
    val margin: Float = DEFAULT_MARGIN,
    /** Paneller arası aralık (kısa kenarın oranı). */
    val gutter: Float = DEFAULT_GUTTER,
    /** Sağdan sola okuma (manga): yan yana paneller sağdan numaralanır. */
    val rtl: Boolean = false,
    val insets: List<PanelInset> = emptyList(),
) {
    val isEmpty: Boolean get() = leafIds().isEmpty()

    // --- Okuma sırası ---

    /** Panellerin kimlikleri okuma sırasıyla (iç paneller [PanelInset.after] panelinden sonra). */
    fun leafIds(): List<Int> {
        val tree = ArrayList<Int>()
        root?.let { collect(it, tree) }
        if (insets.isEmpty()) return tree
        val out = ArrayList<Int>(tree.size + insets.size)
        for (id in tree) {
            out += id
            insets.filter { it.after == id }.forEach { out += it.id }
        }
        insets.filter { it.after !in tree }.forEach { out += it.id }
        return out
    }

    private fun collect(n: PanelNode, out: MutableList<Int>) {
        when (n) {
            is PanelNode.Leaf -> out += n.id
            PanelNode.Space -> Unit
            is PanelNode.Split -> (if (!n.rows && rtl) n.children.asReversed() else n.children).forEach { collect(it, out) }
        }
    }

    fun isInset(id: Int): Boolean = insets.any { it.id == id }

    fun nextId(): Int = (leafIds().maxOrNull() ?: -1) + 1

    // --- Geometri ---

    /** [w]×[h] sayfada panellerin köşeleri (okuma sırasıyla). */
    fun shapes(w: Float, h: Float): List<PanelShape> {
        val s = min(w, h)
        val m = (margin * s).coerceAtMost(min(w, h) * 0.3f)
        val l = m; val t = m; val r = w - m; val b = h - m
        val found = HashMap<Int, FloatArray>()
        root?.let { node -> layout(node, rect(l, t, r, b), l, t, r, b, gutter * s, s, found) }
        val iw = r - l; val ih = b - t
        for (inset in insets) {
            val x0 = l + inset.l * iw; val x1 = l + inset.r * iw
            val y0 = t + inset.t * ih; val y1 = t + inset.b * ih
            found[inset.id] = if (inset.oval) ellipse(x0, y0, x1, y1) else decorate(rect(x0, y0, x1, y1), inset.round * s, 0f)
        }
        val insetIds = insets.mapTo(HashSet()) { it.id }
        return leafIds().mapIndexedNotNull { i, id -> found[id]?.let { PanelShape(id, i + 1, it, id in insetIds) } }
    }

    private fun layout(n: PanelNode, poly: FloatArray, l: Float, t: Float, r: Float, b: Float, g: Float, s: Float, out: MutableMap<Int, FloatArray>) {
        when (n) {
            is PanelNode.Leaf -> if (poly.size >= 6) out[n.id] = decorate(poly, n.round * s, n.bevel * s)
            PanelNode.Space -> Unit
            is PanelNode.Split -> {
                val count = n.children.size
                if (count == 0) return
                val gg = g * n.gap
                val extent = if (n.rows) b - t else r - l
                val avail = max(1f, extent - (count - 1) * gg)
                val total = n.weights.take(count).sum().takeIf { it > 0f } ?: count.toFloat()
                var pos = if (n.rows) t else l
                for (i in 0 until count) {
                    val size = avail * (n.weights.getOrElse(i) { 1f } / total)
                    var p = poly
                    // Önceki çizginin sonrası, sonraki çizginin öncesi (aralığın yarısı kadar içeride)
                    if (i > 0) p = clip(p, divider(n, i - 1, pos - gg / 2f, l, t, r, b), gg / 2f, after = true)
                    if (i < count - 1) p = clip(p, divider(n, i, pos + size + gg / 2f, l, t, r, b), gg / 2f, after = false)
                    if (n.rows) layout(n.children[i], p, l, pos, r, pos + size, g, s, out)
                    else layout(n.children[i], p, pos, t, pos + size, b, g, s, out)
                    pos += size + gg
                }
            }
        }
    }

    /** Bölme çizgisi [index]: iki nokta (ax, ay, bx, by); [c] çizginin ortasının konumu. */
    private fun divider(n: PanelNode.Split, index: Int, c: Float, l: Float, t: Float, r: Float, b: Float): FloatArray {
        val skew = n.skews.getOrElse(index) { 0f }
        return if (n.rows) {
            val d = skew * (r - l) / 2f
            floatArrayOf(l, c + d, r, c - d)
        } else {
            val d = skew * (b - t) / 2f
            floatArrayOf(c + d, t, c - d, b)
        }
    }

    companion object {
        const val DEFAULT_MARGIN = 0.045f
        const val DEFAULT_GUTTER = 0.022f
        const val MIN_MARGIN = 0f
        const val MAX_MARGIN = 0.12f
        const val MIN_GUTTER = 0f
        const val MAX_GUTTER = 0.08f
        const val MIN_PART = 0.1f
        const val MAX_PART = 0.9f
        private const val VERSION = "P1"

        private fun rect(l: Float, t: Float, r: Float, b: Float) = floatArrayOf(l, t, r, t, r, b, l, b)

        /**
         * Dışbükey çokgeni, çizginin bir yanında ve çizgiden [d] uzakta kalacak biçimde kırpar (Sutherland-Hodgman).
         * Yan: [after] ise çizginin x'i (yan yana) ya da y'si (üst üste) büyük olan yanı.
         */
        private fun clip(poly: FloatArray, line: FloatArray, d: Float, after: Boolean): FloatArray {
            val ax = line[0]; val ay = line[1]
            var nx = line[3] - ay; var ny = -(line[2] - ax)
            val len = hypot(nx, ny)
            if (len < 1e-6f) return poly
            nx /= len; ny /= len
            // Normal "sonraki" yana baksın (dikey çizgide +x, yatay çizgide +y)
            if (abs(nx) >= abs(ny)) { if (nx < 0) { nx = -nx; ny = -ny } } else if (ny < 0) { nx = -nx; ny = -ny }
            if (!after) { nx = -nx; ny = -ny }
            fun f(x: Float, y: Float) = (x - ax) * nx + (y - ay) * ny - d
            val n = poly.size / 2
            val out = FloatBuf(poly.size + 4)
            for (i in 0 until n) {
                val px = poly[2 * i]; val py = poly[2 * i + 1]
                val j = (i + 1) % n
                val qx = poly[2 * j]; val qy = poly[2 * j + 1]
                val fp = f(px, py); val fq = f(qx, qy)
                if (fp >= 0f) out.add(px, py)
                if ((fp >= 0f) != (fq >= 0f)) {
                    val k = fp / (fp - fq)
                    out.add(px + (qx - px) * k, py + (qy - py) * k)
                }
            }
            return out.toArray()
        }

        private fun abs(v: Float) = kotlin.math.abs(v)

        /** Köşeleri pahlar ([bevel]) ya da yuvarlar ([round]); ikisi de 0 ise aynı çokgen. */
        private fun decorate(poly: FloatArray, round: Float, bevel: Float): FloatArray {
            if (round <= 0.5f && bevel <= 0.5f) return poly
            val n = poly.size / 2
            val out = FloatBuf(poly.size * if (round > 0.5f) ROUND_STEPS else 2)
            for (i in 0 until n) {
                val vx = poly[2 * i]; val vy = poly[2 * i + 1]
                val p = (i + n - 1) % n; val q = (i + 1) % n
                val ux = poly[2 * p]; val uy = poly[2 * p + 1]
                val wx = poly[2 * q]; val wy = poly[2 * q + 1]
                val c = if (round > 0.5f) round else bevel
                val k1 = min(c / max(1e-3f, hypot(ux - vx, uy - vy)), 0.45f)
                val k2 = min(c / max(1e-3f, hypot(wx - vx, wy - vy)), 0.45f)
                val ax = vx + (ux - vx) * k1; val ay = vy + (uy - vy) * k1
                val bx = vx + (wx - vx) * k2; val by = vy + (wy - vy) * k2
                if (round > 0.5f) {
                    for (step in 0 until ROUND_STEPS) {
                        val tt = step / (ROUND_STEPS - 1f); val mt = 1f - tt
                        out.add(mt * mt * ax + 2 * mt * tt * vx + tt * tt * bx, mt * mt * ay + 2 * mt * tt * vy + tt * tt * by)
                    }
                } else {
                    out.add(ax, ay); out.add(bx, by)
                }
            }
            return out.toArray()
        }

        private const val ROUND_STEPS = 7

        /** Kutulanmadan (Float nesnesi üretmeden) büyüyen nokta dizisi. */
        private class FloatBuf(capacity: Int) {
            private var a = FloatArray(maxOf(4, capacity))
            private var n = 0
            fun add(x: Float, y: Float) {
                if (n + 2 > a.size) a = a.copyOf(a.size * 2)
                a[n++] = x; a[n++] = y
            }
            fun toArray(): FloatArray = a.copyOf(n)
        }

        private fun ellipse(l: Float, t: Float, r: Float, b: Float): FloatArray {
            val cx = (l + r) / 2f; val cy = (t + b) / 2f
            val rx = (r - l) / 2f; val ry = (b - t) / 2f
            val n = 48
            return FloatArray(2 * n).also { a ->
                for (i in 0 until n) {
                    val ang = 2.0 * PI * i / n
                    a[2 * i] = cx + rx * cos(ang).toFloat(); a[2 * i + 1] = cy + ry * sin(ang).toFloat()
                }
            }
        }

        // --- Metin biçimi ---

        /** Kısa metinden düzen; bozuksa null. */
        fun decode(text: String): PanelPage? = runCatching {
            val parts = text.split('|')
            require(parts.size >= 5 && parts[0] == VERSION)
            val margin = parts[1].toFloat().coerceIn(MIN_MARGIN, MAX_MARGIN)
            val gutter = parts[2].toFloat().coerceIn(MIN_GUTTER, MAX_GUTTER)
            val rtl = parts[3] == "1"
            val root = if (parts[4].isEmpty()) null else Parser(parts[4]).node()
            val insets = parts.getOrNull(5).orEmpty().split(';').filter { it.isNotBlank() }.map { s ->
                val f = s.split(':')
                PanelInset(f[0].toInt(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), f[5] == "o", f[6].toFloat(), f[7].toInt())
            }
            PanelPage(root, margin, gutter, rtl, insets)
        }.getOrNull()

        private class Parser(val s: String) {
            var i = 0
            fun node(): PanelNode {
                val c = s[i++]
                return when (c) {
                    '_' -> PanelNode.Space
                    'p' -> {
                        val id = int()
                        var round = 0f; var bevel = 0f
                        while (i < s.length && (s[i] == 'r' || s[i] == 'b')) {
                            val k = s[i++]
                            if (k == 'r') round = num() else bevel = num()
                        }
                        PanelNode.Leaf(id, round, bevel)
                    }
                    'R', 'C' -> {
                        var gap = 1f
                        if (s[i] == 'g') { i++; gap = num() }
                        require(s[i++] == '[')
                        val children = ArrayList<PanelNode>(); val weights = ArrayList<Float>(); val skews = ArrayList<Float>()
                        while (true) {
                            weights += num()
                            require(s[i++] == ':')
                            children += node()
                            if (s[i] == '/') { i++; skews += num() } else if (s[i] == ',') skews += 0f
                            when (s[i++]) {
                                ',' -> continue
                                ']' -> break
                                else -> error("düzen")
                            }
                        }
                        require(children.isNotEmpty())
                        PanelNode.Split(c == 'R', children, weights, skews.take(children.size - 1).let { k -> k + List(children.size - 1 - k.size) { 0f } }, gap)
                    }
                    else -> error("düzen")
                }
            }

            fun int(): Int {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == '-')) i++
                return s.substring(start, i).toInt()
            }

            fun num(): Float {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == '-' || s[i] == 'E' || s[i] == 'e')) i++
                return s.substring(start, i).toFloat()
            }
        }

        /** Sayının en kısa, geri okunduğunda aynı değeri veren yazımı (tam sayılar ondalıksız). */
        fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
    }

    /** Kısa metin biçimi: "P1|kenar|aralık|rtl|ağaç|iç paneller". */
    fun encode(): String {
        val sb = StringBuilder(64)
        sb.append(VERSION).append('|').append(fmt(margin)).append('|').append(fmt(gutter)).append('|').append(if (rtl) '1' else '0').append('|')
        root?.let { write(it, sb) }
        sb.append('|')
        insets.forEachIndexed { k, n ->
            if (k > 0) sb.append(';')
            sb.append(n.id).append(':').append(fmt(n.l)).append(':').append(fmt(n.t)).append(':').append(fmt(n.r)).append(':').append(fmt(n.b))
                .append(':').append(if (n.oval) 'o' else 'q').append(':').append(fmt(n.round)).append(':').append(n.after)
        }
        return sb.toString()
    }

    private fun write(n: PanelNode, sb: StringBuilder) {
        when (n) {
            is PanelNode.Leaf -> {
                sb.append('p').append(n.id)
                if (n.round > 0f) sb.append('r').append(fmt(n.round))
                if (n.bevel > 0f) sb.append('b').append(fmt(n.bevel))
            }
            PanelNode.Space -> sb.append('_')
            is PanelNode.Split -> {
                sb.append(if (n.rows) 'R' else 'C')
                if (n.gap != 1f) sb.append('g').append(fmt(n.gap))
                sb.append('[')
                n.children.forEachIndexed { i, c ->
                    if (i > 0) sb.append(',')
                    sb.append(fmt(n.weights.getOrElse(i) { 1f })).append(':')
                    write(c, sb)
                    val skew = n.skews.getOrElse(i) { 0f }
                    if (i < n.children.size - 1 && skew != 0f) sb.append('/').append(fmt(skew))
                }
                sb.append(']')
            }
        }
    }

    // --- Düzenleme ---

    /** Yapraktan köke yol: her adımda (bölme, çocuk sırası). */
    private fun path(id: Int): List<Pair<PanelNode.Split, Int>>? {
        val out = ArrayList<Pair<PanelNode.Split, Int>>()
        fun walk(n: PanelNode): Boolean = when (n) {
            is PanelNode.Leaf -> n.id == id
            PanelNode.Space -> false
            is PanelNode.Split -> n.children.indices.any { i ->
                out += n to i
                walk(n.children[i]).also { found -> if (!found) out.removeAt(out.size - 1) }
            }
        }
        val r = root ?: return null
        return if (walk(r)) out else null
    }

    /** Ağaçta [path] üzerindeki düğümü [f] ile değiştirir (yol boşsa kök). */
    private fun replaceAt(path: List<Int>, f: (PanelNode) -> PanelNode?): PanelNode? {
        fun go(n: PanelNode, depth: Int): PanelNode? {
            if (depth == path.size) return f(n)
            val split = n as PanelNode.Split
            val k = path[depth]
            val child = go(split.children[k], depth + 1)
            return if (child == null) removeChild(split, k) else split.copy(children = split.children.toMutableList().also { it[k] = child })
        }
        return root?.let { go(it, 0) }
    }

    /** Bölmeden bir çocuğu çıkarır; kalan tek çocuksa onu, panel kalmadıysa null döndürür. */
    private fun removeChild(split: PanelNode.Split, k: Int): PanelNode? {
        val children = split.children.toMutableList().also { it.removeAt(k) }
        val weights = split.weights.toMutableList().also { if (k < it.size) it.removeAt(k) }
        val skews = split.skews.toMutableList().also { if (it.isNotEmpty()) it.removeAt(if (k > 0) k - 1 else 0) }
        if (children.none { hasLeaf(it) }) return null
        if (children.size == 1) return children[0]
        return split.copy(children = children, weights = weights, skews = skews)
    }

    private fun hasLeaf(n: PanelNode): Boolean = when (n) {
        is PanelNode.Leaf -> true
        PanelNode.Space -> false
        is PanelNode.Split -> n.children.any { hasLeaf(it) }
    }

    private fun leafOf(id: Int): PanelNode.Leaf? {
        val p = path(id) ?: return null
        val node = if (p.isEmpty()) root else p.last().let { (s, i) -> s.children[i] }
        return node as? PanelNode.Leaf
    }

    fun withMargin(v: Float) = copy(margin = v.coerceIn(MIN_MARGIN, MAX_MARGIN))
    fun withGutter(v: Float) = copy(gutter = v.coerceIn(MIN_GUTTER, MAX_GUTTER))

    /** Panelin genişliği: yan yana olduğu bölmedeki payı (iç panelde sayfanın iç genişliğine oranı); yoksa null. */
    fun widthOf(id: Int): Float? = partOf(id, rows = false)

    /** Panelin yüksekliği: üst üste olduğu bölmedeki payı (iç panelde sayfanın iç yüksekliğine oranı); yoksa null. */
    fun heightOf(id: Int): Float? = partOf(id, rows = true)

    private fun partOf(id: Int, rows: Boolean): Float? {
        insets.firstOrNull { it.id == id }?.let { return if (rows) it.b - it.t else it.r - it.l }
        val p = path(id) ?: return null
        val (split, k) = p.lastOrNull { it.first.rows == rows && it.first.children.size > 1 } ?: return null
        val total = split.weights.sum()
        return if (total > 0f) split.weights[k] / total else null
    }

    fun setWidth(id: Int, part: Float): PanelPage = setPart(id, rows = false, part)
    fun setHeight(id: Int, part: Float): PanelPage = setPart(id, rows = true, part)

    private fun setPart(id: Int, rows: Boolean, part0: Float): PanelPage {
        val part = part0.coerceIn(MIN_PART, MAX_PART)
        insets.firstOrNull { it.id == id }?.let { n ->
            val half = part / 2f
            val updated = if (rows) {
                val c = ((n.t + n.b) / 2f).coerceIn(half, 1f - half)
                n.copy(t = c - half, b = c + half)
            } else {
                val c = ((n.l + n.r) / 2f).coerceIn(half, 1f - half)
                n.copy(l = c - half, r = c + half)
            }
            return copy(insets = insets.map { if (it.id == id) updated else it })
        }
        val p = path(id) ?: return this
        val at = p.indexOfLast { it.first.rows == rows && it.first.children.size > 1 }
        if (at < 0) return this
        val (split, k) = p[at]
        val others = split.weights.filterIndexed { i, _ -> i != k }.sum()
        val weights = split.weights.toMutableList().also { it[k] = others * part / (1f - part) }
        return copy(root = replaceAt(p.take(at).map { it.second }) { (it as PanelNode.Split).copy(weights = weights) })
    }

    /**
     * Paneli okuma sırasında bir öne ([forward] false) ya da arkaya taşır. Aynı bölmedeki komşusuyla yer değiştirir
     * (boyutunu yanında götürür); bölmenin ucundaysa okuma sırasındaki komşu panelle yer değiştirir. Taşınamıyorsa null.
     */
    fun move(id: Int, forward: Boolean): PanelPage? {
        if (isInset(id)) return null
        val p = path(id) ?: return null
        if (p.isNotEmpty()) {
            val (split, k) = p.last()
            val step = (if (forward) 1 else -1) * (if (!split.rows && rtl) -1 else 1)
            val j = k + step
            if (j in split.children.indices && split.children[j] !is PanelNode.Split) {
                val children = split.children.toMutableList().also { val c = it[k]; it[k] = it[j]; it[j] = c }
                val weights = split.weights.toMutableList().also { val w = it[k]; it[k] = it[j]; it[j] = w }
                return copy(root = replaceAt(p.dropLast(1).map { it.second }) { (it as PanelNode.Split).copy(children = children, weights = weights) })
            }
        }
        // Okuma sırasındaki komşu panelle yer değiştirme (yerler aynı kalır, paneller değişir)
        val order = leafIds().filter { !isInset(it) }
        val i = order.indexOf(id)
        val other = order.getOrNull(if (forward) i + 1 else i - 1) ?: return null
        val a = leafOf(id) ?: return null
        val b = leafOf(other) ?: return null
        val pa = p.map { it.second }
        val pb = path(other)?.map { it.second } ?: return null
        val swapped = copy(root = replaceAt(pa) { b }).let { it.copy(root = it.replaceAt(pb) { a }) }
        return swapped
    }

    /**
     * Panelin yanına ([rows] false) ya da altına yeni panel ekler (okuma sırasında hemen arkasına; panelin yeri
     * ikiye bölünür). Yeni panel aynı köşe biçimindedir. İç panel bölünemez (null).
     */
    fun split(id: Int, rows: Boolean): Pair<PanelPage, Int>? {
        if (isInset(id)) return null
        val p = path(id) ?: return null
        val leaf = leafOf(id) ?: return null
        val newId = nextId()
        val fresh = leaf.copy(id = newId)
        val before = !rows && rtl
        val parent = p.lastOrNull()
        if (parent != null && parent.first.rows == rows) {
            val (split, k) = parent
            val at = if (before) k else k + 1
            val half = split.weights[k] / 2f
            val children = split.children.toMutableList().also { it.add(at, fresh) }
            val weights = split.weights.toMutableList().also { it[k] = half; it.add(at, half) }
            // Yeni çizgi panelle yeni panelin arasında (eğimsiz); eski çizgiler komşularıyla kalır
            val skews = split.skews.toMutableList().also { it.add((if (before) at else at - 1).coerceIn(0, it.size), 0f) }
            val updated = split.copy(children = children, weights = weights, skews = skews.take(children.size - 1))
            return copy(root = replaceAt(p.dropLast(1).map { it.second }) { updated }) to newId
        }
        val pair = if (before) listOf(fresh, leaf) else listOf(leaf, fresh)
        val node = PanelNode.Split(rows, pair, listOf(1f, 1f), listOf(0f))
        return copy(root = replaceAt(p.map { it.second }) { node }) to newId
    }

    /** Sayfanın sonuna (altına) yeni bir panel satırı ekler. */
    fun append(): Pair<PanelPage, Int> {
        val newId = nextId()
        val last = leafIds().filter { !isInset(it) }.lastOrNull()?.let { leafOf(it) }
        val fresh = PanelNode.Leaf(newId, last?.round ?: 0f, last?.bevel ?: 0f)
        val r = root ?: return copy(root = fresh) to newId
        val updated = if (r is PanelNode.Split && r.rows) {
            r.copy(children = r.children + fresh, weights = r.weights + r.weights.average().toFloat(), skews = r.skews + 0f)
        } else {
            PanelNode.Split(true, listOf(r, fresh), listOf(2f, 1f), listOf(0f))
        }
        return copy(root = updated) to newId
    }

    /** Paneli siler; komşuları boşalan yeri doldurur. Son panel silinince düzen boşalır. */
    fun remove(id: Int): PanelPage {
        if (isInset(id)) return copy(insets = insets.filter { it.id != id })
        val p = path(id) ?: return this
        val order = leafIds()
        val prev = order.getOrNull(order.indexOf(id) - 1)?.takeIf { !isInset(it) } ?: -1
        val newRoot = if (p.isEmpty()) null else replaceAt(p.map { it.second }) { null }
        return copy(root = newRoot, insets = insets.map { if (it.after == id) it.copy(after = prev) else it })
    }
}
