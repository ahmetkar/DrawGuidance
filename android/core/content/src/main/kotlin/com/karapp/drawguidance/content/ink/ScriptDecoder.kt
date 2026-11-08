package com.karapp.drawguidance.content.ink

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * v37 Latin dışı yazı sistemlerinin çözücüsü (Kiril, Arapça, Japonca, Korece): yazı ağıyla ([HandwritingNet]) karakter
 * grupları puanlanır, dinamik programlamayla en iyi bölütleme bulunur.
 *
 * 1. Çizgiler x'e göre sıralanır; ardışık en çok [maxStrokes] çizgi aday karakter grubudur (Japonca/Korece 14,
 *    diğerleri 6). Grup genişliği tipik karakter boyunun ([cs]) katlarıyla sınırlanır, içinde geniş boşluk olamaz.
 * 2. Grubun maliyeti: −ln p(sınıf) (ağın o yazı sistemi başı; Korecede ilk ünsüz × ünlü × son ünsüz olasılıkları)
 *    + karakter başı sabit + boy/en önceliği (Japonca/Korece karakterler kare ve eşit boylu: dar ya da küçük grup
 *    cezalanır) + komşu grupla yatay örtüşme cezası. Latin rakamları (ağın LATIN başı, yazı sistemi başıyla
 *    ağırlıklı) her yazı sisteminde seçenek olarak eklenir; harf↔rakam geçişi maliyetlidir.
 * 3. Sonuç metni okuma sırasıyla kurulur (Arapça sağdan sola: en sağdaki grup ilk karakter); geniş boşluk sözcük
 *    arasıdır (Japoncada boşluk yok). Biçimi aynı çıktılar bağlamla seçilir: Kiril büyük/küçük harf satırdaki üst
 *    çizgiye göre, Arapça "ا/١" komşu rakamlara göre, Japonca küçük kana boyuna, kana/kanji (ロ/口, ー/一 …) komşulara göre.
 *
 * Arapça yalnız ayrık (yalın biçimli) harflerle yazıldığında okunur; bitişik el yazısı desteklenmez.
 */
internal class ScriptDecoder(
    input: List<Pair<Int, Poly>>,
    private val script: InkScript,
    private val net: HandwritingNet,
) {
    companion object {
        const val BEAM = 8
        const val OPTS = 4
        /** Karakter başı sabit maliyet (yazı sistemine göre; ayar için değiştirilebilir). */
        @Volatile internal var LAMBDA = 0.5f
        @Volatile internal var LAMBDA_CJK = 0.9f
        /** Japonca/Korece: tipik karakter boyundan geniş grubun maliyeti (birim genişlik başına). */
        @Volatile internal var WIDE_CJK = 4f
        const val DIGIT_FACTOR = 0.35f
        const val SWITCH = 1.2f
        const val MIN_CONFIDENCE = 0.3f
        const val JAMO_FACTOR = 0.3f
        /** Dil ipucunun karakter başına önsel oranı (ipucu güçlüdür; başka yazı ancak çok açıksa seçilir). */
        @Volatile internal var HINT_PRIOR = 200f
        /**
         * İpucu yokken Latin'in önsel oranı: bütün mürekkep için [LATIN_PRIOR], tek kümelik mürekkepte (tek harf, çoğu
         * çizim) ayrıca [LATIN_SINGLE_PRIOR], iki kümelikte (ör. "d" = c + |, Korece 니'ye benzer) [LATIN_DOUBLE_PRIOR].
         * Eski davranış korunur: tek Latin harf başka yazıya kaymaz (ö/ة, ç/ء gibi karışmalar); Latin dışı satır birkaç
         * karakterde açıkça ağır basar. İpucusuz tek Latin dışı karakter çoğunlukla Latin sayılır: uygulama arayüz dilini
         * ipucu olarak verir. (Ayar: TextNetTraining.detectSweep.)
         */
        @Volatile internal var LATIN_PRIOR = 6f
        @Volatile internal var LATIN_SINGLE_PRIOR = 1000f
        @Volatile internal var LATIN_DOUBLE_PRIOR = 10f
        const val TOUCH = 1.2f

        private val DIGIT_LABELS = mapOf("O" to "0", "|" to "1", "1" to "1", "2" to "2", "3" to "3", "4" to "4", "5" to "5", "6" to "6", "7" to "7", "8" to "8", "9" to "9")

        /** Korece hece bileşenleri (Unicode hece sırası: ilk ünsüz, ünlü, son ünsüz). */
        const val HANGUL_L = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        const val HANGUL_V = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        const val HANGUL_T = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

        /** Küçük kana (ağda büyük kanayla aynı sınıf; boyuna göre seçilir). */
        private const val SMALL_KANA = "ぁぃぅぇぉっゃゅょァィゥェォッャュョ"

        fun isKatakana(c: Char) = c in '゠'..'ヿ'
        fun isHiragana(c: Char) = c in '぀'..'ゟ'
        fun isKanji(c: Char) = c in '一'..'鿿'
        fun isDigitText(s: String) = s.isNotEmpty() && s.all { it.isDigit() }

        /**
         * Çizgilerin yazı sistemi: kabaca karakter kümelerinin yazı sistemi olasılıkları (log) toplanır. [hint] verilmişse
         * o yazı sistemine, verilmemişse Latin'e önsel ağırlık eklenir (eski davranış korunur). Ağ yoksa null.
         */
        fun detect(ps: List<Poly>, net: HandwritingNet, hint: InkScript?): Pair<InkScript, FloatArray>? {
            val (acc, count) = evidence(ps, net) ?: return null
            val n = acc.size
            // ipucu karakter başına güçlü önsel (ör. "ru" ile italik Kiril Latin'e benzese de Kiril), ipucu yoksa Latin'e
            // toplamda önsel (tek ve iki kümede daha güçlü)
            if (hint != null) acc[hint.ordinal] += count * ln(HINT_PRIOR)
            else acc[InkScript.LATIN.ordinal] += ln(LATIN_PRIOR) + when (count) {
                1 -> ln(LATIN_SINGLE_PRIOR)
                2 -> ln(LATIN_DOUBLE_PRIOR)
                else -> 0f
            }
            val best = acc.indices.maxByOrNull { acc[it] }!!
            // olasılığa çevir (rapor ve eşik)
            val m = acc.max()
            var z = 0f
            val p = FloatArray(n) { exp(acc[it] - m).also { v -> z += v } }
            for (k in 0 until n) p[k] /= z
            return InkScript.entries[best] to p
        }

        /**
         * Önselsiz kanıt: karakter kümelerinin yazı sistemi log olasılıklarının boya göre ağırlıklı toplamı (ağırlıklar
         * ortalama 1 olacak biçimde ölçeklenir) ve küme sayısı.
         */
        internal fun evidence(ps: List<Poly>, net: HandwritingNet): Pair<FloatArray, Int>? {
            if (ps.isEmpty()) return null
            val cl = Prep.clusters(ps)
            val n = InkScript.entries.size
            val acc = FloatArray(n)
            var wsum = 0f
            var count = 0
            val mx = cl.maxOfOrNull { max(it.box.w, it.box.h) } ?: return null
            for (c in cl) {
                if (c.members.size > 20) continue
                val size = max(c.box.w, c.box.h)
                if (size < 0.25f * mx) continue
                val members = HashSet(c.members)
                // kümeye yatayda giren küçük çizgiler (noktalar, işaretler)
                ps.indices.filter { it !in members && ps[it].extent < 0.25f * mx && ps[it].cx >= c.box.minX && ps[it].cx <= c.box.maxX }.forEach { members += it }
                val pr = net.scriptProbs(net.hidden(TextFeatures.extract(members.sorted().map { ps[it] })))
                val w = size
                for (k in 0 until n) acc[k] += w * ln(max(pr[k], 1e-4f))
                wsum += w
                count++
            }
            if (wsum <= 0f) return null
            // kanıt karakter sayısıyla birikir (ağırlıklar ortalama 1 olacak biçimde ölçeklenir)
            for (k in 0 until n) acc[k] *= count / wsum
            return acc to count
        }
    }

    private val orig = input.map { it.first }.toIntArray()
    private val ps = input.map { it.second }
    private val n = ps.size
    private val cjk = script == InkScript.JAPANESE || script == InkScript.KOREAN
    private val maxStrokes = if (cjk) 14 else 6
    private val order = ps.indices.sortedWith(compareBy({ ps[it].cx }, { ps[it].minY })).toIntArray()

    /** Tipik karakter boyu (kabaca küme boylarının üst çeyreği). */
    private val cs: Float = run {
        val cl = Prep.clusters(ps)
        val sizes = cl.map { if (cjk) max(it.box.w, it.box.h) else max(it.box.h, 0.8f * it.box.w) }.sorted()
        if (sizes.isEmpty()) 1f else max(sizes[min(sizes.size - 1, (sizes.size * 3) / 4)], 1e-3f)
    }

    private enum class Kind { LETTER, DIGIT }

    private class Opt(val text: String, val outputs: List<String>, val p: Float, val kind: Kind)

    private class Group(val a: Int, val b: Int, val members: List<Int>, val box: Box, val opts: List<Opt>, val prior: Float)

    private class Node(val cost: Float, val opt: Opt?, val g: Group?, val prev: Node?, val key: Long)

    private val groups = HashMap<Int, Group?>()

    private fun group(a: Int, b: Int): Group? = groups.getOrPut(a * 256 + b) { analyze(a, b) }

    private fun analyze(a: Int, b: Int): Group? {
        val mem = (a..b).map { order[it] }
        val polys = mem.map { ps[it] }
        val box = Box.of(polys)
        val maxW = when (script) { InkScript.ARABIC -> 2.4f; InkScript.CYRILLIC -> 1.7f; else -> 1.45f } * cs
        if (box.w > maxW) return null
        // içte geniş boşluk: büyük olasılıkla iki ayrı karakter (い, に, 나 gibi ayrık parçalı karakterler için yumuşak)
        var gapCost = 0f
        if (mem.size > 1) {
            val iv = polys.map { it.minX to it.maxX }.sortedBy { it.first }
            var end = iv[0].second
            var gap = 0f
            for (k in 1 until iv.size) {
                gap = max(gap, iv[k].first - end)
                end = max(end, iv[k].second)
            }
            if (gap > (if (cjk) 0.6f else 0.45f) * cs) return null
            gapCost = 5f * max(0f, gap / cs - (if (cjk) 0.22f else 0.25f))
        }
        val h = net.hidden(TextFeatures.extract(polys))
        val sp = net.scriptProbs(h)
        val opts = ArrayList<Opt>()
        if (script == InkScript.KOREAN) koreanOpts(h, opts) else {
            val head = net.head(script.name) ?: return null
            val p = net.probs(h, script.name)!!
            for (c in p.indices.sortedByDescending { p[it] }.take(OPTS)) {
                if (c == head.junk) continue
                val outs = head.outputs[c]
                val kind = if (outs.all { o -> o.all { it.isDigit() } }) Kind.DIGIT else Kind.LETTER
                opts += Opt(outs[0], outs, p[c], kind)
            }
        }
        // Latin rakamları (yazı sistemi başıyla ağırlıklı)
        net.head(InkScript.LATIN.name)?.let { lat ->
            val pl = net.probs(h, InkScript.LATIN.name)!!
            val wl = sp[InkScript.LATIN.ordinal] / max(sp[InkScript.LATIN.ordinal] + sp[script.ordinal], 1e-6f)
            val best = HashMap<String, Float>()
            for (c in pl.indices) {
                val d = lat.outputs[c].firstOrNull()?.let { DIGIT_LABELS[it] } ?: continue
                best[d] = max(best[d] ?: 0f, pl[c] * wl * DIGIT_FACTOR)
            }
            best.entries.sortedByDescending { it.value }.take(2).forEach { (d, v) -> if (v > 1e-3f) opts += Opt(d, listOf(d), v, Kind.DIGIT) }
        }
        if (opts.isEmpty()) return null
        // boy/en önceliği
        var prior = gapCost
        if (cjk) {
            val size = max(box.w, box.h)
            if (size < 0.55f * cs) prior += 2.2f * ln(0.55f * cs / max(size, 1e-4f))
            val narrow = 0.62f * cs
            if (box.w < narrow && box.h > 0.7f * cs) prior += 1.6f * ln(narrow / max(box.w, 1e-4f))
            if (box.w > 1.05f * cs) prior += WIDE_CJK * (box.w / cs - 1.05f)
        } else {
            val size = max(box.w, box.h)
            if (size < 0.25f * cs) prior += 2f * ln(0.25f * cs / max(size, 1e-4f))
        }
        return Group(a, b, mem, box, opts.sortedByDescending { it.p }.take(OPTS + 1), prior)
    }

    private fun koreanOpts(h: FloatArray, opts: MutableList<Opt>) {
        val pl = net.probs(h, HandwritingNet.KOREAN_L) ?: return
        val pv = net.probs(h, HandwritingNet.KOREAN_V) ?: return
        val pt = net.probs(h, HandwritingNet.KOREAN_T) ?: return
        val hl = net.head(HandwritingNet.KOREAN_L)!!; val hv = net.head(HandwritingNet.KOREAN_V)!!; val ht = net.head(HandwritingNet.KOREAN_T)!!
        fun top(p: FloatArray) = p.indices.sortedByDescending { p[it] }.take(3)
        val combos = ArrayList<Opt>()
        for (l in top(pl)) for (v in top(pv)) for (t in top(pt)) {
            if (l == hl.junk) continue
            val lo = hl.outputs[l][0]; val vo = hv.outputs[v][0]; val to = ht.outputs[t][0]
            val noL = lo == HandwritingNet.NONE; val noV = vo == HandwritingNet.NONE; val noT = to == HandwritingNet.NONE
            val text = when {
                noL && noV -> continue
                noV -> if (noT) lo else continue
                noL -> if (noT) vo else continue
                else -> {
                    val li = HANGUL_L.indexOf(lo); val vi = HANGUL_V.indexOf(vo); val ti = if (noT) -1 else HANGUL_T.indexOf(to)
                    if (li < 0 || vi < 0 || (!noT && ti < 0)) continue
                    (0xAC00 + (li * 21 + vi) * 28 + (ti + 1)).toChar().toString()
                }
            }
            // tek başına jamo (ㅋㅋ gibi) nadir: hecenin parçası olma olasılığı yüksek
            val jamo = noL || noV
            combos += Opt(text, listOf(text), pl[l] * pv[v] * pt[t] * (if (jamo) JAMO_FACTOR else 1f), Kind.LETTER)
        }
        opts += combos.sortedByDescending { it.p }.take(OPTS)
    }

    private val coarse: List<Poly> by lazy { ps.map { Geo.resample(it, cs / 14f) } }
    private val touchCache = HashMap<Int, Boolean>()

    private fun touches(i: Int, j: Int): Boolean = touchCache.getOrPut(min(i, j) * 4096 + max(i, j)) {
        val p = ps[i]; val q = ps[j]
        val tol = 0.04f * cs
        if (p.minX - tol > q.maxX || q.minX - tol > p.maxX || p.minY - tol > q.maxY || q.minY - tol > p.maxY) false
        else Geo.minDistance(coarse[i], coarse[j]) < tol
    }

    /** Ardışık iki grup: yatay örtüşme (Japonca/Korece karakterler örtüşmez) ve birbirine değen çizgiler. */
    private fun boundary(p: Group?, g: Group): Float {
        if (p == null) return 0f
        var cost = 0f
        val gap = g.box.minX - p.box.maxX
        if (gap < 0f) {
            val minW = max(min(p.box.w, g.box.w), 0.25f * cs)
            cost += if (cjk) 6f * max(0f, -gap / minW - 0.05f) else 3.5f * max(0f, -gap / minW - 0.15f)
        }
        if (gap < 0.05f * cs) {
            var t = false
            loop@ for (i in p.members) for (j in g.members) if (touches(i, j)) { t = true; break@loop }
            if (t) cost += TOUCH
        }
        return cost
    }

    private fun ctx(prev: Opt?, o: Opt): Float {
        if (prev == null) return if (o.kind == Kind.DIGIT) 0.3f else 0f
        return if (prev.kind != o.kind) SWITCH else 0f
    }

    private val lambda = if (cjk) LAMBDA_CJK else LAMBDA

    fun decode(): Recognition? {
        if (n == 0) return null
        val beams = arrayOfNulls<MutableList<Node>>(n + 1)
        beams[0] = mutableListOf(Node(0f, null, null, null, 17L))
        for (end in 1..n) {
            val cand = ArrayList<Node>()
            for (start in max(0, end - maxStrokes) until end) {
                val prevs = beams[start] ?: continue
                if (prevs.isEmpty()) continue
                val g = group(start, end - 1) ?: continue
                for (pv in prevs) {
                    val bnd = boundary(pv.g, g)
                    for (o in g.opts) {
                        val cost = pv.cost + (-ln(max(o.p, 1e-6f))) + lambda + g.prior + bnd + ctx(pv.opt, o)
                        cand += Node(cost, o, g, pv, pv.key * 1_000_003L + o.text.hashCode())
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
        val finals = beams[n] ?: return null
        if (finals.isEmpty()) return null
        return build(finals)
    }

    private fun walk(nd: Node): List<Node> {
        val out = ArrayList<Node>()
        var c: Node? = nd
        while (c?.g != null) { out += c; c = c.prev }
        out.reverse()
        return out
    }

    /** Biçimi aynı çıktılardan bağlama göre seçim ve okuma sırası. */
    private fun texts(nodes: List<Node>): List<String> {
        val t = nodes.map { it.opt!!.text }.toMutableList()
        when (script) {
            InkScript.CYRILLIC -> {
                val letters = nodes.indices.filter { nodes[it].opt!!.kind == Kind.LETTER }
                if (letters.isNotEmpty()) {
                    val top = letters.associateWith { bodyTop(nodes[it].g!!) }
                    val tops = top.values.sorted()
                    val base = letters.map { nodes[it].g!!.box.maxY }.sorted()[letters.size / 2]
                    val capTop = tops.first(); val mid = tops[tops.size / 2]
                    val mixed = (mid - capTop) > 0.22f * max(base - capTop, 1e-4f)
                    fun tall(i: Int) = mixed && top.getValue(i) < capTop + 0.4f * (mid - capTop)
                    // sözcükler (boşlukla ayrılan gruplar)
                    val words = ArrayList<MutableList<Int>>()
                    for (i in nodes.indices) {
                        if (i == 0 || spaceBetween(nodes[i - 1], nodes[i])) words += mutableListOf<Int>()
                        words.last() += i
                    }
                    for (w in words) {
                        val wl = w.filter { it in top }
                        val allTall = wl.size >= 2 && wl.all { tall(it) }
                        for (i in w) {
                            val o = nodes[i].opt!!
                            if (o.outputs.size < 2 || o.kind != Kind.LETTER) continue
                            // büyük harf: sözcük başında (ya da bütün sözcük büyük) ve gövdesi büyük harf boyunda
                            val upper = (allTall || (i == w.first() && tall(i)))
                            t[i] = if (upper) o.outputs[1] else o.outputs[0]
                        }
                    }
                }
            }
            InkScript.ARABIC -> nodes.forEachIndexed { i, nd ->
                val o = nd.opt!!
                if (o.outputs.size > 1) {
                    val digitNear = listOfNotNull(nodes.getOrNull(i - 1), nodes.getOrNull(i + 1)).any { it.opt!!.kind == Kind.DIGIT }
                    t[i] = if (digitNear) o.outputs.firstOrNull { s -> s.all { it.isDigit() } } ?: o.outputs[0] else o.outputs[0]
                }
            }
            InkScript.JAPANESE -> nodes.forEachIndexed { i, nd ->
                val o = nd.opt!!
                if (o.outputs.size < 2) return@forEachIndexed
                val small = o.outputs.firstOrNull { it[0] in SMALL_KANA }
                val size = max(nd.g!!.box.w, nd.g.box.h)
                // küçük kana: küçük ve satırın alt yarısında
                if (small != null && i > 0 && size < 0.55f * cs && nd.g.box.cy > lineMid(nodes) + 0.08f * cs) { t[i] = small; return@forEachIndexed }
                val big = o.outputs.filter { it[0] !in SMALL_KANA }
                if (big.size < 2) { t[i] = big.firstOrNull() ?: o.outputs[0]; return@forEachIndexed }
                val near = listOfNotNull(nodes.getOrNull(i - 1), nodes.getOrNull(i + 1)).map { it.opt!!.text[0] }
                t[i] = when {
                    near.any { isKatakana(it) } -> big.firstOrNull { isKatakana(it[0]) } ?: big[0]
                    near.any { isKanji(it) || isHiragana(it) } || near.isEmpty() -> big.firstOrNull { isKanji(it[0]) || isHiragana(it[0]) } ?: big[0]
                    else -> big[0]
                }
            }
            else -> {}
        }
        return t
    }

    /**
     * Okuma sırasındaki bloklar (soldan sağa sıra numarası aralıkları): Arapçada bloklar sağdan sola okunur, ama rakam
     * dizileri tek blok olarak soldan sağa.
     */
    private fun blocks(nodes: List<Node>, t: List<String>): List<IntRange> {
        if (!script.rtl) return nodes.indices.map { it..it }
        val out = ArrayList<IntRange>()
        var i = nodes.size - 1
        while (i >= 0) {
            var j = i
            if (isDigitText(t[i])) while (j - 1 >= 0 && isDigitText(t[j - 1]) && !spaceBetween(nodes[j - 1], nodes[j])) j--
            out += j..i
            i = j - 1
        }
        return out
    }

    private fun readingOrder(nodes: List<Node>, t: List<String>): List<Int> = blocks(nodes, t).flatMap { it.toList() }

    private fun spaceBetween(left: Node, right: Node): Boolean =
        script != InkScript.JAPANESE && right.g!!.box.minX - left.g!!.box.maxX > (if (cjk) 0.5f else 0.6f) * cs

    /** Grubun işaretler (й kısası, ё noktaları) dışındaki gövdesinin üstü. */
    private fun bodyTop(g: Group): Float {
        val mx = g.members.maxOf { ps[it].extent }
        return g.members.filter { ps[it].extent >= 0.35f * mx }.minOf { ps[it].minY }
    }

    private fun lineMid(nodes: List<Node>): Float = nodes.map { it.g!!.box.cy }.sorted()[nodes.size / 2]

    private fun join(nodes: List<Node>, t: List<String>): String {
        val sb = StringBuilder()
        var prev: IntRange? = null
        for (b in blocks(nodes, t)) {
            if (prev != null) {
                // bitişik bloklar: soldaki bloğun sağ ucu ile sağdakinin sol ucu
                val (left, right) = if (b.first > prev.last) prev.last to b.first else b.last to prev.first
                if (spaceBetween(nodes[left], nodes[right])) sb.append(' ')
            }
            for (k in b) sb.append(t[k])
            prev = b
        }
        return sb.toString()
    }

    private fun build(finals: List<Node>): Recognition? {
        val best = finals[0]
        val nodes = walk(best)
        if (nodes.isEmpty()) return null
        val t = texts(nodes)
        val text = join(nodes, t)
        val reading = readingOrder(nodes, t)
        val chars = reading.map { i ->
            val nd = nodes[i]
            val g = nd.g!!
            val z = g.opts.sumOf { it.p.toDouble() }.toFloat().coerceAtLeast(1e-6f)
            val cands = LinkedHashMap<String, Float>()
            cands[t[i]] = nd.opt!!.p
            for (o in g.opts) if (o.text !in cands && cands.size < 5) cands[o.text] = o.p
            CharResult(g.members.map { orig[it] }.sorted(), cands.map { Candidate(it.key, (it.value / max(z, 1f)).coerceIn(0f, 1f)) })
        }
        // güven: seçilen sınıf olasılıklarının geometrik ortalaması × en zayıf karakter × bölütleme
        val logs = nodes.map { ln(max(it.opt!!.p, 1e-6f)) }
        val geo = exp(logs.average().toFloat())
        val weakest = exp(logs.min())
        val seg = exp(-0.3f * nodes.sumOf { (boundary(it.prev?.g, it.g!!) + it.g.prior).toDouble() }.toFloat() / nodes.size)
        val conf = ((0.6f * geo + 0.4f * weakest) * seg).coerceIn(0f, 1f)
        val alts = ArrayList<String>()
        for (f in finals.drop(1)) {
            if (alts.size >= 3) break
            val ns = walk(f)
            val s = join(ns, texts(ns))
            if (s != text && s !in alts) alts += s
        }
        return Recognition(text, conf, alts, chars)
    }
}
