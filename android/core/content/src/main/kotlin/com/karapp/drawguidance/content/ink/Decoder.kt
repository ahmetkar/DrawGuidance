package com.karapp.drawguidance.content.ink

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal enum class MarkType { DOT, TICK, DASH, BREVE }

internal class Mark(val poly: Poly, val type: MarkType)

/** Satır ölçüsü varsayımı: [base] taban çizgisi y'si, [h] büyük harf yüksekliği. */
internal class Line(val base: Float, val h: Float) {
    val cap get() = base - h
    fun u(y: Float) = (y - cap) / h
}

/** Aday karakter grubu (sıralı çizgilerde [a]..[b]). */
internal class Group(val a: Int, val b: Int) {
    var valid = false
    var members: List<Int> = emptyList()
    var main: List<Int> = emptyList()
    var above: List<Mark> = emptyList()
    var below: List<Mark> = emptyList()
    var marksOnly = false
    lateinit var box: Box
    var mergeCost = 0f
    /** (sınıf, işaretsiz/konumsuz taban maliyeti, şekil maliyeti) — küçükten büyüğe. */
    var cls: List<ClassCost> = emptyList()
}

internal class ClassCost(val c: Int, val base: Float, val shape: Float)

internal class Decoder(input: List<Pair<Int, Poly>>, private val mode: InkMode, private val lexicon: Boolean = true) {

    companion object {
        const val K = 5              // karakter başına en çok çizgi
        const val BEAM = 8
        const val OPTS = 7
        const val SHAPE_K = 14f      // şekil uzaklığı → maliyet
        const val LAMBDA = 0.6f      // karakter başı sabit maliyet
        const val MARKS_ONLY_SHAPE = 1.2f
        const val Q0 = 4.0f          // mutlak kalite: bu şekil maliyetine kadar tam puan
        const val Q1 = 1.8f

        // bağlam: kategori geçiş maliyetleri (satır: önceki, sütun: sonraki) D L U S P B
        private val CTX = arrayOf(
            floatArrayOf(0.0f, 1.0f, 1.2f, 0.0f, 0.2f, 0.1f),
            floatArrayOf(1.0f, 0.0f, 1.4f, 0.4f, 0.1f, 0.4f),
            floatArrayOf(1.0f, 0.0f, 0.35f, 0.5f, 0.1f, 0.4f),
            floatArrayOf(0.0f, 0.4f, 0.8f, 1.0f, 0.8f, 0.1f),
            floatArrayOf(0.2f, 0.3f, 0.3f, 0.8f, 0.7f, 0.4f),
            floatArrayOf(0.1f, 0.2f, 0.4f, 0.3f, 0.2f, 0.5f),
        )
        private val START = floatArrayOf(0.0f, 0.0f, 0.0f, 0.5f, 1.5f, 0.2f)
        private val END = floatArrayOf(0.0f, 0.0f, 0.0f, 0.4f, 0.0f, 0.1f)
        /** matematikte değişken olarak sık görülen harfler */
        private const val VARS = "xyabnkt"
        /** v23 Türkçe harf ikilisi dil modelinin ağırlığı ve sözlükteki sözcüğe verilen ödül. */
        const val LM_W = 0.3f
        const val LEX_BONUS = 1.6f
        const val LEX_RERANK = 16

        /**
         * v37 yazı ağının (LATIN başı: gövde şekli etiketleri) ağırlığı: sınıf maliyetine NET_W · −ln p(etiket) eklenir
         * (şablon uzaklığına ek kanıt; 0 = kapalı). Sınama ve ayar için değiştirilebilir.
         */
        @Volatile internal var NET_W = 0.6f
    }

    /** v37 yazı ağı ve sınıfların ağdaki gövde etiketi sınıfları (ağ yoksa null). */
    private val textNet: HandwritingNet? = HandwritingNet.active?.takeIf { NET_W > 0f && it.head(InkScript.LATIN.name) != null }
    private val netIdx: Array<IntArray>? = textNet?.head(InkScript.LATIN.name)?.let { h ->
        Array(Glyphs.classes.size) { ci -> Glyphs.classes[ci].shapes.map { h.classOf(it) }.filter { it >= 0 }.toIntArray() }
    }

    private val orig: IntArray = input.map { it.first }.toIntArray()
    private var ps: List<Poly> = input.map { it.second }
    private val n = ps.size
    private var hc = 1f
    private lateinit var order: IntArray
    private val groups = HashMap<Int, Group>()
    private val classes = Glyphs.classes
    private val allowed = BooleanArray(classes.size) { Glyphs.allowed(classes[it], mode) }
    private val touchCache = HashMap<Int, Boolean>()
    /** aynı metni veren sınıflar (ör. iki "," sınıfı) aynı kimliği alır */
    private val textId = IntArray(classes.size) { i -> classes.indexOfFirst { it.text == classes[i].text } + 1 }
    private lateinit var coarse: List<Poly>

    /** [key]: okunan metnin özeti (aynı metni veren yolları tekilleştirmek için). */
    private class Node(val cost: Float, val c: Int, val g: Group?, val prev: Node?, val key: Long)

    private class Path(val cost: Float, val line: Line, val node: Node)

    fun decode(): Recognition? {
        normalize()
        order = ps.indices.sortedWith(compareBy({ ps[it].cx }, { ps[it].minY })).toIntArray()
        coarse = ps.map { Geo.resample(it, hc / 14f) }
        val paths = ArrayList<Path>()
        for (l in hypotheses()) {
            for (nd in dp(l)) paths += Path(nd.cost, l, nd)
        }
        if (paths.isEmpty()) return null
        paths.sortBy { it.cost }
        // v23 sözlük: en iyi okumalar arasında Türkçe sözlükte bulunan sözcükler öne alınır
        if (lexicon && mode != InkMode.DIGITS) {
            val top = paths.take(LEX_RERANK)
            val scored = top.map { p ->
                val words = finalText(walk(p.node), p.line).split(' ').filter { w -> w.length >= 2 && w.all { it.isLetter() } }
                val hits = words.count { TurkishLexicon.contains(it) }
                p to p.cost - LEX_BONUS * hits
            }.sortedBy { it.second }.map { it.first }
            return build(scored + paths.drop(LEX_RERANK))
        }
        return build(paths)
    }

    // ---------------------------------------------------------------- normalleştirme

    private fun normalize() {
        hc = Prep.capHeight(ps)
        val cl = Prep.clusters(ps)
        if (cl.size >= 3) {
            val maxH = cl.maxOf { it.box.h }
            val tall = cl.filter { it.box.h >= 0.4f * maxH }
            if (tall.size >= 3) {
                val ang = baselineAngle(tall.map { it.box })
                if (abs(ang) > 0.015f && abs(ang) < 0.3f) rotate(-ang)
            }
            // not: yatıklık (italik) düzeltmesi denendi; "/" ve "7" gibi çapraz çizgileri dikleştirip zarar verdiği için
            // kullanılmıyor — şablonlar zaten ±10–12° eğik çeşitlerle genişletiliyor.
            hc = Prep.capHeight(ps)
        }
    }

    private fun baselineAngle(boxes: List<Box>): Float {
        val xs = boxes.map { it.cx }; val ys = boxes.map { it.maxY }
        val tol = 0.1f * hc
        fun score(slope: Float, x0: Float, y0: Float): Float {
            var s = 0f
            for (i in xs.indices) {
                val r = (ys[i] - (y0 + slope * (xs[i] - x0))) / tol
                s += max(0f, 1f - r * r)
            }
            return s - 0.3f * abs(slope)
        }
        val med = ys.sorted()[ys.size / 2]
        var bestSlope = 0f; var bx = xs.average().toFloat(); var by = med
        var best = score(0f, bx, by)
        for (i in xs.indices) for (j in i + 1 until xs.size) {
            val dx = xs[j] - xs[i]
            if (abs(dx) < 0.3f * hc) continue
            val sl = (ys[j] - ys[i]) / dx
            if (abs(sl) > 0.32f) continue
            val sc = score(sl, xs[i], ys[i])
            if (sc > best) { best = sc; bestSlope = sl; bx = xs[i]; by = ys[i] }
        }
        // en küçük kareler (iç noktalarla)
        val inl = xs.indices.filter { abs(ys[it] - (by + bestSlope * (xs[it] - bx))) < tol }
        if (inl.size >= 3) {
            val mx = inl.map { xs[it] }.average().toFloat(); val my = inl.map { ys[it] }.average().toFloat()
            var sxy = 0f; var sxx = 0f
            for (i in inl) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) * (xs[i] - mx) }
            if (sxx > 0f) bestSlope = sxy / sxx
        } else return 0f
        return atan(bestSlope)
    }

    private fun rotate(theta: Float) {
        val b = Box.of(ps)
        val c = cos(theta); val s = sin(theta)
        ps = ps.map { it.affine(c, -s, b.cx - c * b.cx + s * b.cy, s, c, b.cy - s * b.cx - c * b.cy) }
    }

    // ---------------------------------------------------------------- satır varsayımları

    private fun hypotheses(): List<Line> {
        val cl = Prep.clusters(ps)
        val out = ArrayList<Line>()
        fun add(base: Float, h: Float) {
            if (h <= 1e-4f) return
            if (out.any { abs(it.h - h) < 0.12f * h && abs(it.base - base) < 0.1f * h }) return
            out += Line(base, h)
        }
        val maxH = cl.maxOfOrNull { it.box.h } ?: 0f
        for (c in cl) {
            val h = c.box.h
            if (h < 0.3f * maxH || h < 1e-4f) continue
            add(c.box.maxY, h)
            add(c.box.maxY, h / 0.55f)
            add(c.box.maxY - 0.45f * h, h)
        }
        // yassı simgeler (−, =, ÷) için genişlikten bir varsayım (her zaman; normal metinde maliyeti yüksek kalır)
        val b = Box.of(ps)
        val fh = max(b.w, b.h) / 0.6f
        add(b.cy + 0.4f * fh, fh)
        return out
    }

    // ---------------------------------------------------------------- gruplar

    private fun group(a: Int, b: Int): Group = groups.getOrPut(a * 64 + b) { analyze(a, b) }

    private fun analyze(a: Int, b: Int): Group {
        val g = Group(a, b)
        val mem = (a..b).map { order[it] }
        g.members = mem
        val maxExt = mem.maxOf { ps[it].extent }
        val ref = max(maxExt, 0.5f * hc)
        val thr = min(0.33f * ref, 0.3f * hc)
        var main = mem.filter { ps[it].extent >= thr }
        var cand = mem.filter { ps[it].extent < thr }
        val above = ArrayList<Mark>(); val below = ArrayList<Mark>()
        // orta boy işaretler (tire biçimli ü noktaları, büyük ğ yayı, büyük çengel): gövdeden açıkça ayrık olmalı
        if (main.size > 1) {
            val core = main.filter { ps[it].extent >= 0.65f * ref }
            if (core.isNotEmpty()) {
                val cb = Box.of(core.map { ps[it] })
                val cw = max(cb.w, 0.3f * hc)
                val med = main.filter { i ->
                    val p = ps[i]
                    i !in core && p.cx >= cb.minX - 0.25f * cw && p.cx <= cb.maxX + 0.25f * cw &&
                        ((p.maxY < cb.minY + 0.02f * hc && p.cy < cb.minY - 0.05f * hc) ||
                            (p.cy > cb.maxY + 0.04f * hc && p.minY > cb.maxY - 0.05f * hc)) &&
                        core.none { c -> Geo.minDistance(coarse[i], coarse[c]) < 0.04f * hc } // gövdeye değmez
                }
                if (med.isNotEmpty()) {
                    val rest = main - med.toSet()
                    val rb = Box.of(rest.map { ps[it] })
                    for (i in med) {
                        val p = ps[i]
                        if (p.cy < rb.minY) above += Mark(p, markType(p)) else below += Mark(p, markType(p))
                    }
                    main = rest
                }
            }
        }
        val medAbove = above.toList(); val medBelow = below.toList()
        if (main.isEmpty()) {
            if (cand.size > 2) return g
            g.marksOnly = true
            g.box = Box.of(cand.map { ps[it] })
            if (g.box.w > 0.5f * hc) return g
            g.above = cand.map { Mark(ps[it], markType(ps[it])) }
            g.main = emptyList()
        } else {
            for (pass in 0 until 2) {
                val mb = Box.of(main.map { ps[it] })
                val mh = max(mb.h, 0.2f * hc); val mw = max(mb.w, 0.3f * hc)
                above.clear(); below.clear()
                above += medAbove; below += medBelow
                val back = ArrayList<Int>()
                for (i in cand) {
                    val p = ps[i]
                    val tolX = 0.2f * hc + 0.15f * mw // nokta çoğu zaman sağa/sola kayar
                    val hOk = p.cx >= mb.minX - tolX && p.cx <= mb.maxX + tolX
                    when {
                        hOk && p.cy < mb.minY && p.maxY <= mb.minY + 0.2f * mh -> above += Mark(p, markType(p))
                        hOk && p.cy > mb.maxY && p.minY >= mb.maxY - 0.2f * mh -> below += Mark(p, markType(p))
                        else -> back += i
                    }
                }
                if (back.isEmpty()) break
                // işaret sayılmayanlar gövdeye döner; gövde kutusu büyüdüğü için bir kez daha bakılır
                main = main + back
                cand = cand - back.toSet()
            }
            g.main = main
            g.above = above.toList(); g.below = below.toList()
            g.box = Box.of(main.map { ps[it] })
            if (above.size + below.size > 2) return g
        }
        // genişlik ve iç boşluk
        if (g.box.w > 1.9f * hc) return g
        if (!g.marksOnly && g.main.size > 1) {
            val iv = g.main.map { ps[it].minX to ps[it].maxX }.sortedBy { it.first }
            var end = iv[0].second
            var gap = 0f
            for (k in 1 until iv.size) {
                if (iv[k].first > end + 0.03f * hc) gap = max(gap, iv[k].first - end)
                end = max(end, iv[k].second)
            }
            if (gap > 0.3f * hc) return g
            g.mergeCost = 12f * max(0f, gap / hc - 0.04f)
        }
        // gövdeye karışmış nokta gibi küçük çizgi (işaret konumunda değil): büyük olasılıkla ayrı "." / ":"
        if (!g.marksOnly) g.mergeCost += 1.5f * g.main.count { ps[it].extent < 0.12f * hc }
        g.cls = classCosts(g)
        g.valid = g.cls.isNotEmpty()
        return g
    }

    private fun markType(p: Poly): MarkType {
        if (p.extent < 0.1f * hc || p.length < 0.14f * hc) return MarkType.DOT
        // yay: orta nokta uçlardan belirgin aşağıda (çanak)
        val r = Geo.resample(p, max(p.length / 12f, 1e-4f))
        val mid = r.n / 2
        val endY = min(r.y[0], r.y[r.n - 1])
        val sag = r.y[mid] - endY
        if (p.w >= 0.8f * p.h && sag > 0.25f * p.w && r.y[mid] >= max(r.y[0], r.y[r.n - 1]) - 0.1f * p.w) return MarkType.BREVE
        if (p.w >= 1.5f * p.h) return MarkType.DASH
        return MarkType.TICK
    }

    private fun markCost(spec: Marks, g: Group): Float {
        val up = g.above; val dn = g.below
        fun dotLike(m: Mark) = when (m.type) { MarkType.DOT -> 0f; MarkType.TICK -> 0.3f; MarkType.DASH -> 1.0f; MarkType.BREVE -> 1.5f }
        return when (spec) {
            Marks.NONE -> 6f * (up.size + dn.size)
            Marks.DOT_ABOVE -> if (up.size == 1 && dn.isEmpty()) dotLike(up[0]) else 6f
            Marks.TWO_DOTS_ABOVE -> when {
                dn.isNotEmpty() -> 6f
                up.size == 2 -> {
                    val dx = abs(up[0].poly.cx - up[1].poly.cx); val dy = abs(up[0].poly.cy - up[1].poly.cy)
                    (if (dx > dy) 0f else 2.5f) + 0.5f * (dotLike(up[0]) + dotLike(up[1]))
                }
                up.size == 1 -> when (up[0].type) { MarkType.DASH -> 0.6f; MarkType.BREVE -> 1.6f; else -> 3.0f }
                else -> 6f
            }
            Marks.BREVE_ABOVE -> if (up.size == 1 && dn.isEmpty()) when (up[0].type) {
                MarkType.BREVE -> 0f; MarkType.DASH -> 0.6f; MarkType.TICK -> 1.2f; MarkType.DOT -> 2.5f
            } else 6f
            Marks.CEDILLA_BELOW -> if (dn.size == 1 && up.isEmpty()) when (dn[0].type) {
                MarkType.TICK -> 0f; MarkType.DOT -> 0.5f; MarkType.BREVE -> 0.8f; MarkType.DASH -> 1.0f
            } else 6f
            Marks.DOT_BELOW -> if (dn.size == 1 && up.isEmpty()) when (dn[0].type) {
                MarkType.DOT -> 0f; MarkType.TICK -> 0.4f; else -> 1.5f
            } else 6f
            Marks.DOTS_ABOVE_BELOW -> if (up.size == 1 && dn.size == 1) dotLike(up[0]) + dotLike(dn[0]) else 6f
            else -> Float.POSITIVE_INFINITY
        }
    }

    private fun marksOnlyCost(spec: Marks, g: Group): Float {
        val m = g.above
        return when (spec) {
            Marks.ONLY_DOT -> if (m.size == 1) (if (m[0].type == MarkType.DOT) 0f else 1.5f) else Float.POSITIVE_INFINITY
            Marks.ONLY_TICK -> if (m.size == 1) {
                val p = m[0].poly
                if (m[0].type != MarkType.DOT && p.h >= 0.6f * p.w) 0f else 2.0f
            } else Float.POSITIVE_INFINITY
            Marks.ONLY_COLON -> if (m.size == 2) {
                val dx = abs(m[0].poly.cx - m[1].poly.cx); val dy = abs(m[0].poly.cy - m[1].poly.cy)
                if (dy > 1.2f * dx && dy >= 0.15f * hc) 0.3f * (if (m[0].type == MarkType.DOT) 0 else 1) + 0.3f * (if (m[1].type == MarkType.DOT) 0 else 1)
                else 5f
            } else Float.POSITIVE_INFINITY
            else -> Float.POSITIVE_INFINITY
        }
    }

    private fun classCosts(g: Group): List<ClassCost> {
        val out = ArrayList<ClassCost>()
        if (g.marksOnly) {
            for ((ci, c) in classes.withIndex()) {
                if (!allowed[ci]) continue
                val mc = marksOnlyCost(c.marks, g)
                if (mc.isInfinite()) continue
                out += ClassCost(ci, MARKS_ONLY_SHAPE + mc + c.prior, MARKS_ONLY_SHAPE)
            }
        } else {
            val mainPolys = g.main.map { ps[it] }
            val dist = ShapeModel.distances(mainPolys)
            val np = textNet?.let { it.probs(it.hidden(TextFeatures.extract(mainPolys)), InkScript.LATIN.name) }
            val w = NET_W
            for ((ci, c) in classes.withIndex()) {
                if (!allowed[ci] || c.shapes.isEmpty()) continue
                var d = Float.POSITIVE_INFINITY
                for (l in c.shapes) {
                    val li = ShapeModel.indexOf(l)
                    if (li >= 0) d = min(d, dist[li])
                }
                if (d.isInfinite()) continue
                val shape = SHAPE_K * d
                val mc = markCost(c.marks, g)
                var net = 0f
                if (np != null) {
                    var m = 0f
                    for (k in netIdx!![ci]) m = max(m, np[k])
                    net = w * -ln(max(m, 1e-3f))
                }
                out += ClassCost(ci, shape + mc + c.prior + net, shape)
            }
        }
        out.sortBy { it.base }
        if (out.isEmpty()) return out
        val best = out[0].base
        return out.filter { it.base <= best + 9f }.take(18)
    }

    private fun posCost(g: Group, c: CharClass, l: Line): Float {
        val ut = l.u(g.box.minY); val ub = l.u(g.box.maxY)
        val dt = (ut - c.bandTop) / c.sigma
        val db = (ub - c.bandBottom) / (c.sigma * 0.85f)
        return 0.5f * (min(dt * dt, 30f) + min(db * db, 30f))
    }

    private fun touches(i: Int, j: Int): Boolean {
        val key = min(i, j) * 64 + max(i, j)
        return touchCache.getOrPut(key) {
            val p = ps[i]; val q = ps[j]
            val tol = 0.05f * hc
            if (p.minX - tol > q.maxX || q.minX - tol > p.maxX || p.minY - tol > q.maxY || q.minY - tol > p.maxY) false
            else Geo.minDistance(coarse[i], coarse[j]) < tol
        }
    }

    private fun boundary(p: Group?, g: Group): Float {
        if (p == null) return 0f
        val gap = g.box.minX - p.box.maxX
        var cost = 0f
        if (gap < 0f) {
            val minW = max(min(p.box.w, g.box.w), 0.25f * hc)
            cost += 3.5f * max(0f, -gap / minW - 0.2f)
        }
        if (!p.marksOnly && !g.marksOnly) {
            var t = false
            loop@ for (i in p.main) for (j in g.main) if (touches(i, j)) { t = true; break@loop }
            if (t) cost += 1.2f
        }
        return cost
    }

    private fun ctx(prev: Int, c: Int): Float {
        val cc = classes[c]
        if (prev < 0) return START[cc.cat.ordinal]
        val pc = classes[prev]
        var v = CTX[pc.cat.ordinal][cc.cat.ordinal]
        if (pc.cat == Cat.DIGIT && cc.cat == Cat.LOWER && cc.text in VARS) v = 0.4f
        if (pc.cat == Cat.LOWER && pc.text in VARS && (cc.cat == Cat.SYMBOL || cc.cat == Cat.BRACKET)) v = min(v, 0.15f)
        if (pc.cat == Cat.SYMBOL && cc.cat == Cat.LOWER && cc.text in VARS) v = min(v, 0.15f)
        return v
    }

    /** v23 harf ikilisi maliyeti: önceki ve bu karakter harfse (sözcük içinde) Türkçe geçiş olasılığından. */
    private fun lmCost(prev: Int, c: Int, last: Boolean): Float {
        if (mode == InkMode.DIGITS) return 0f
        val t = classes[c].text
        if (t.length != 1 || !t[0].isLetter()) return 0f
        val p = if (prev >= 0) classes[prev].text.takeIf { it.length == 1 && it[0].isLetter() }?.get(0) else null
        if (prev >= 0 && p == null) return 0f
        // Tek başına harf (sözcük değil): dil modeli uygulanmaz
        if (prev < 0 && last) return 0f
        var v = TurkishLexicon.cost(p, t[0])
        if (last) v += TurkishLexicon.endCost(t[0])
        return LM_W * v
    }

    // ---------------------------------------------------------------- dinamik programlama

    private fun dp(l: Line): List<Node> {
        val beams = arrayOfNulls<MutableList<Node>>(n + 1)
        beams[0] = mutableListOf(Node(0f, -1, null, null, 17L))
        val optC = IntArray(OPTS); val optV = FloatArray(OPTS)
        for (end in 1..n) {
            val cand = ArrayList<Node>()
            for (start in max(0, end - K) until end) {
                val prevs = beams[start] ?: continue
                if (prevs.isEmpty()) continue
                val g = group(start, end - 1)
                if (!g.valid) continue
                // en iyi OPTS sınıf (konum dahil), küçük sabit dizilerle
                var m = 0
                for (cc in g.cls) {
                    val v = cc.base + posCost(g, classes[cc.c], l)
                    if (m < OPTS) { optC[m] = cc.c; optV[m] = v; m++ } else {
                        var w = 0
                        for (k in 1 until OPTS) if (optV[k] > optV[w]) w = k
                        if (v < optV[w]) { optC[w] = cc.c; optV[w] = v }
                    }
                }
                for (pv in prevs) {
                    val bnd = boundary(pv.g, g)
                    for (k in 0 until m) {
                        val c = optC[k]
                        var cost = pv.cost + optV[k] + ctx(pv.c, c) + bnd + g.mergeCost + LAMBDA
                        if (end == n) cost += END[classes[c].cat.ordinal]
                        if (lexicon) cost += lmCost(pv.c, c, end == n)
                        cand += Node(cost, c, g, pv, pv.key * 1_000_003L + textId[c])
                    }
                }
            }
            cand.sortBy { it.cost }
            val seen = HashSet<Long>()
            val keep = ArrayList<Node>(BEAM)
            for (nd in cand) {
                if (!seen.add(nd.key)) continue
                keep += nd
                if (keep.size >= BEAM) break
            }
            beams[end] = keep
        }
        return beams[n] ?: emptyList()
    }

    // ---------------------------------------------------------------- sonuç

    private fun walk(nd: Node): List<Node> {
        val out = ArrayList<Node>()
        var c: Node? = nd
        while (c != null && c.g != null) { out += c; c = c.prev }
        return out.reversed()
    }

    private fun family(a: CharClass, b: CharClass): Boolean =
        a.marks == b.marks && (a === b || a.shapes.any { it in b.shapes } || (a.text == b.text))

    private fun finalText(nodes: List<Node>, l: Line): String = join(nodes, finalChars(nodes), l)

    /** Bağlama göre son biçim: "x"/"×", "−"/"-". */
    private fun finalChars(nodes: List<Node>): List<String> {
        val t = nodes.map { classes[it.c].text }.toMutableList()
        for (i in t.indices) {
            val prev = t.getOrNull(i - 1); val next = t.getOrNull(i + 1)
            fun digitish(s: String?, open: String) = s != null && (s[0].isDigit() || s == open)
            if (t[i] == "x" || t[i] == "X" || t[i] == "×") {
                t[i] = when {
                    digitish(prev, ")") && digitish(next, "(") -> "×"
                    mode == InkMode.DIGITS -> "×"
                    t[i] == "×" -> "x"
                    else -> t[i]
                }
            }
            if (t[i] == "−") {
                val letter = (prev != null && prev[0].isLetter()) || (next != null && next[0].isLetter())
                if (mode == InkMode.LETTERS || letter) t[i] = "-"
            }
        }
        return t
    }

    private fun join(nodes: List<Node>, t: List<String>, l: Line): String {
        // sözcük arası: büyük harf yüksekliğinin ~%62'sinden geniş boşluk (harf aralığı tipik olarak %15–40)
        val gaps = (1 until nodes.size).map { nodes[it].g!!.box.minX - nodes[it - 1].g!!.box.maxX }
        val spaceGap = 0.62f * l.h
        val sb = StringBuilder()
        for (i in t.indices) {
            if (i > 0 && gaps[i - 1] > spaceGap) sb.append(' ')
            sb.append(t[i])
        }
        return sb.toString()
    }

    private fun build(paths: List<Path>): Recognition {
        val best = paths[0]
        val l = best.line
        val nodes = walk(best.node)
        val shown = finalChars(nodes)
        val text = join(nodes, shown, l)
        val chars = ArrayList<CharResult>()
        val scores = ArrayList<Float>()
        for ((ni, nd) in nodes.withIndex()) {
            val g = nd.g!!
            val opts = g.cls.map { Triple(it, it.base + posCost(g, classes[it.c], l), posCost(g, classes[it.c], l)) }
            val mn = opts.minOf { it.second }
            var z = 0f
            val probs = opts.map { exp(-(it.second - mn)).also { v -> z += v } }
            val chosen = classes[nd.c]
            var fam = 0f
            opts.forEachIndexed { i, o -> if (family(classes[o.first.c], chosen)) fam += probs[i] / z }
            val mine = opts.first { it.first.c == nd.c }
            // açık fark: biçimi benzemeyen en iyi rakip sınıfa göre üstünlük (alışılmadık ama belirgin biçimler)
            val rival = opts.filter { !family(classes[it.first.c], chosen) }.minOfOrNull { it.second } ?: (mine.second + 9f)
            val margin = max(0f, rival - mine.second)
            val q = max(
                quality(mine.first.shape, mine.third),
                exp(-max(0f, mine.first.shape - Q0 - 1.5f) / Q1) * (1f - exp(-margin / 1.5f)) * exp(-max(0f, mine.third - 1.2f) / 2.5f),
            )
            scores += q * (0.3f + 0.7f * fam)
            val cands = LinkedHashMap<String, Float>()
            cands[shown[ni]] = q * (0.3f + 0.7f * fam)
            opts.indices.sortedBy { opts[it].second }.forEach { i ->
                val o = opts[i]
                val txt = classes[o.first.c].text
                if (txt !in cands && cands.size < 5) cands[txt] = quality(o.first.shape, o.third) * probs[i] / z
            }
            chars += CharResult(g.members.map { orig[it] }.sorted(), cands.map { Candidate(it.key, it.value.coerceIn(0f, 1f)) })
        }
        val segQ = exp(-0.25f * nodes.sumOf { (boundaryOf(it) + it.g!!.mergeCost).toDouble() }.toFloat())
        val mn = scores.minOrNull() ?: 0f
        val mean = if (scores.isEmpty()) 0f else scores.average().toFloat()
        var conf = ((0.5f * mn + 0.5f * mean) * segQ).coerceIn(0f, 1f)
        // yalnız eksi/tire: çizilmiş düz çizgiden ayırt edilemez
        if (text.all { it == '−' || it == '-' }) conf *= 0.3f
        // tek çizgilik tek başına daire ya da dik çizgi: çizim de olabilir (tipik harf boyu bilinmiyor) → daha düşük güven
        if (nodes.size == 1 && nodes[0].g!!.members.size == 1 && classes[nodes[0].c].shapes.any { it == "O" || it == "|" }) conf *= 0.7f
        val alts = ArrayList<String>()
        for (p in paths) {
            if (alts.size >= 3) break
            val t = finalText(walk(p.node), p.line)
            if (t != text && t !in alts) alts += t
        }
        return Recognition(text, conf, alts, chars)
    }

    private fun boundaryOf(nd: Node): Float = boundary(nd.prev?.g, nd.g!!)

    private fun quality(shape: Float, pos: Float): Float =
        exp(-max(0f, shape - Q0) / Q1) * exp(-max(0f, pos - 1.2f) / 2.5f)
}
