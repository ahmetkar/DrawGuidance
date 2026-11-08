package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.ink.Geo
import com.karapp.drawguidance.content.ink.Poly
import com.karapp.drawguidance.content.ink.ShapeFeatures
import com.karapp.drawguidance.content.vector.P
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Arama türü: tek çizgi (bireysel şekil) ya da birden çok elemanın oluşturduğu şekil. */
enum class MatchMode { SINGLE, GROUP }

/** Şablonun kullanıcıya görünen bilgisi ve öğrenilen tercih sayaçları. */
class TemplateMeta(
    val key: String,
    val name: String,
    val category: String,
    val group: String,
    val description: String = "",
    val solid: String? = null,
    val usage: Int = 0,
    val rejects: Int = 0,
)

/** Dizinin bir satırı: katalog şablonu ([user] false) ya da kullanıcının kabul ettiği çizimden öğrenilen örnek. */
class IndexEntry(val id: Long, val templateKey: String, val signature: ShapeSignature, val user: Boolean = false)

/** Arama sonucu: [geometry] şablonun birim kutudaki çizgileri (değiştirmede çizimin kutusuna oturtulur). */
class TemplateMatch(
    val key: String,
    val name: String,
    val category: String,
    val group: String,
    val solid: String?,
    val confidence: Float,
    val distance: Float,
    val mode: MatchMode,
    val geometry: List<List<P>>,
    /**
     * Genel (az ayırt edici) şablon: tek, açık, en çok bir köşeli ve az dönen çizgi (kaş, gülümseme, kavis). Her yay bunlara
     * benzeyebileceğinden öneride daha yüksek güven istenir.
     */
    val generic: Boolean = false,
)

/**
 * v21 şekil arama dizini (bellekte, veritabanından bir kez yüklenir). Algoritma:
 *
 * 1. Ön eleme: çizgi sayısı uyumu (tek çizgide yalnız tek çizgili şablonlar; grupta sayı oranı) — O(1)/kayıt.
 * 2. Kaba tarama: 80 boyutlu havuzlanmış öznitelikle kosinüs benzerliği + en-boy terimi, tüm kopyalar üzerinde
 *    (4 birikimli iç çarpım). Dizin büyükse ([LSH_MIN_ROWS]) önce SimHash kovalarından çoklu yoklamayla aday
 *    toplanır. En iyi [COARSE_KEEP] şablon kalır.
 * 3. İnce tarama: 320 boyutlu öznitelikle en yakın komşu (kNN, k=1; çoğaltılmış kopyalar ve kullanıcı örnekleri).
 * 4. Yeniden sıralama (en iyi [RERANK] şablon):
 *    - Tek çizgi: çizgi dizisi hizalaması (32 nokta, iki yön ve kapalı çizgide döngüsel kaydırma; $1 tanıyıcısı
 *      benzeri), kapalılık ve köşe sayısı farkı.
 *    - Grup: nokta bulutu (chamfer) uzaklığı, parça eşleme (her parçanın konumu ve boyu; uzunluk ağırlıklı, iki yönlü)
 *      ve çizgi sayısı farkı.
 * 5. Öğrenilen önsel: kabul/ret sayılarından Laplace düzeltmeli log oran (az ama sürekli düzeltme).
 * 6. Benzerlik (güven) = exp(−uzaklık / [TAU]); genel (az ayırt edici) şablonlara [GENERIC_D] kadar uzaklık eklenir.
 *    v22: ölçek, hedef çizime pek benzemeyen çizimlerde de tahmin yapılacak biçimde ayarlandı (eşik %50, bkz.
 *    [TemplateSuggest]): doğru eşleşmeler çoğunlukla %75–95, uzak ama aynı nesneye işaret eden çizimler %50–75.
 * 7. v23 popülerlik önseli: en çok çizilen şekiller ([TemplatePopularity], 1..5) ve kullanıcının sık kabul ettikleri
 *    uzaklıktan az miktarda ([POP_W]) düşülerek öne alınır; benzerlik oranı yine belirleyicidir.
 * 8. v23 iç içe alt şekil araması: karışık (çok parçalı) çizimde asıl eşleşme zayıfsa ([SUBSET_SKIP_D] üstü), parçaların
 *    alt kümeleri yalnız en çok kullanılan şablonlarla ([FREQUENT_BY_USAGE]) yeniden aranır; dışarıda kalan parça başına
 *    ceza ([DROP_W], [DROP_FIXED]) eklenir ve sonuçlar asıl aramayla birleştirilir ([mergeBest]).
 */
class ShapeIndex(metas: List<TemplateMeta>, entries: List<IndexEntry>) {

    private val metaList = metas
    private val keyIndex: Map<String, Int> = metas.withIndex().associate { it.value.key to it.index }
    private val usage = IntArray(metas.size) { metas[it].usage }
    private val rejects = IntArray(metas.size) { metas[it].rejects }
    /** v23 şeklin ne kadar sık çizildiği (1..5; [TemplatePopularity]). */
    private val popularity = IntArray(metas.size) { TemplatePopularity.of(metas[it].key) }
    private val entryList: List<IndexEntry> = entries.filter { it.templateKey in keyIndex && it.signature.variants > 0 }

    /** Katalog geometri (değiştirmede kullanılan çizim) — kullanıcı örneği yerine hep katalogdaki çizim konur. */
    private val baseGeometry: Array<List<List<P>>?> = arrayOfNulls<List<List<P>>>(metas.size).also { arr ->
        entryList.forEach { e -> if (!e.user) arr[keyIndex.getValue(e.templateKey)] = e.signature.geometry }
        entryList.forEach { e -> val k = keyIndex.getValue(e.templateKey); if (arr[k] == null) arr[k] = e.signature.geometry }
    }

    private val entryKey = IntArray(entryList.size) { keyIndex.getValue(entryList[it].templateKey) }

    private val generic = BooleanArray(metas.size) { k ->
        val g = baseGeometry[k] ?: return@BooleanArray false
        if (g.size != 1) return@BooleanArray false
        val p = ShapeSignatures.polys(g)[0]
        !ShapeSignatures.isClosed(p) && ShapeSignatures.cornersOf(p) <= 1 && Geo.turning(p, 0.02f).absTurns < 0.6f
    }

    /** v29 yalnız bir modun kapsamındaki şablonlar (genel aramada yok). */
    private val modeOnly = BooleanArray(metas.size) { ModeCatalog.isModeOnly(metas[it].key) }
    private val generalMask = BooleanArray(metas.size) { !modeOnly[it] }

    /** v29 aranacak şablonlar: kapsam verilirse modun şablonları, yoksa moda özel olmayanlar. */
    private fun maskFor(scope: ShapeScope?): BooleanArray =
        if (scope == null) generalMask else BooleanArray(metaList.size) { metaList[it].key in scope.keys }

    /** v29 kapsamın (modun) sayaçlarıyla, yoksa genel sayaçlarla kabul ve ret. */
    private fun usageIn(scope: ShapeScope?, k: Int): Int = if (scope == null) usage[k] else scope.usage[metaList[k].key] ?: 0
    private fun rejectsIn(scope: ShapeScope?, k: Int): Int = if (scope == null) rejects[k] else scope.rejects[metaList[k].key] ?: 0

    /** Tüm kopyaların satırları: satır → kayıt; öznitelikler bitişik dizilerde (önbellek dostu). */
    private val rowEntry: IntArray
    private val fine: FloatArray
    private val coarse: FloatArray
    private val rowLsh: IntArray
    private val lshBuckets: HashMap<Int, IntArray>

    init {
        val rows = entryList.sumOf { it.signature.variants }
        rowEntry = IntArray(rows)
        fine = FloatArray(rows * DIM)
        coarse = FloatArray(rows * PD)
        rowLsh = IntArray(rows)
        var r = 0
        entryList.forEachIndexed { e, entry ->
            val d = entry.signature.descriptors
            for (v in 0 until entry.signature.variants) {
                rowEntry[r] = e
                d.copyInto(fine, r * DIM, v * DIM, (v + 1) * DIM)
                val pooled = ShapeSignatures.pool(d, v * DIM)
                pooled.copyInto(coarse, r * PD)
                rowLsh[r] = ShapeSignatures.simHashPooled(pooled)
                r++
            }
        }
        val tmp = HashMap<Int, ArrayList<Int>>()
        for (i in 0 until rows) tmp.getOrPut(rowLsh[i]) { ArrayList() } += i
        lshBuckets = HashMap<Int, IntArray>(tmp.size * 2).also { m -> tmp.forEach { (k, v) -> m[k] = v.toIntArray() } }
    }

    /** Önbellek: kayıtların nokta bulutu ve (tek çizgili kayıtlarda) çizgi dizisi. */
    private val clouds = arrayOfNulls<FloatArray>(entryList.size)
    private val seqs = arrayOfNulls<FloatArray>(entryList.size)
    private val parts = arrayOfNulls<Array<FloatArray>>(entryList.size)

    val templateCount: Int get() = metaList.size
    val entryCount: Int get() = entryList.size
    val rowCount: Int get() = rowEntry.size
    val metas: List<TemplateMeta> get() = metaList

    fun meta(key: String): TemplateMeta? = keyIndex[key]?.let { metaList[it] }

    /** Şablonun genel (az ayırt edici) olup olmadığı. */
    fun isGeneric(key: String): Boolean = keyIndex[key]?.let { generic[it] } ?: false

    fun geometryOf(key: String): List<List<P>>? = keyIndex[key]?.let { baseGeometry[it] }

    /** Öneri kabul/ret sayacı (bellekte; kalıcı kayıt veritabanında ayrıca tutulur). */
    @Synchronized
    fun recordFeedback(key: String, accepted: Boolean) {
        val k = keyIndex[key] ?: return
        if (accepted) usage[k]++ else rejects[k]++
    }

    fun usageOf(key: String): Int = keyIndex[key]?.let { usage[it] } ?: 0
    fun rejectsOf(key: String): Int = keyIndex[key]?.let { rejects[it] } ?: 0

    /** Yeni kullanıcı örneğiyle yeni dizin (sayaçlar ve v27 ek ölçüt önbellekleri korunur). */
    fun plus(entry: IndexEntry): ShapeIndex {
        val metas = metaList.mapIndexed { i, m -> TemplateMeta(m.key, m.name, m.category, m.group, m.description, m.solid, usage[i], rejects[i]) }
        return ShapeIndex(metas, entryList + entry).also { next ->
            for (e in entryList.indices) { next.qClouds[e] = qClouds[e]; next.graphs[e] = graphs[e] }
        }
    }

    /**
     * v27: ek ölçütlerin şablon tarafı ($Q bulutları, parça grafikleri) ve sinir ağı önceden (arka planda) hazırlanır;
     * ilk seçim önerisinde gecikme olmaz.
     */
    fun warmAdvanced() {
        if (!AdvancedWeights.enabled) return
        netFor()
        for (e in entryList.indices) {
            if (entryList[e].user) continue
            runCatching { qOf(e); graphOf(e) }
        }
    }

    /** Anahtarın kullanıcı örneği sayısı. */
    fun userExemplars(key: String): Int = entryList.count { it.user && it.templateKey == key }

    /**
     * [query] çizgileri (tuval koordinatı, y aşağı) için en uygun şablonlar, güvene göre azalan. [minConfidence] altı
     * elenir. [useLsh] null ise dizin büyüklüğüne göre seçilir.
     */
    fun search(
        query: List<List<P>>,
        mode: MatchMode,
        limit: Int = 3,
        minConfidence: Float = 0f,
        useLsh: Boolean? = null,
        /** v29 modun kapsamı: yalnız modun şablonları, modun ağı ve modun sayaçları (null: genel). */
        scope: ShapeScope? = null,
    ): List<TemplateMatch> {
        val main = searchCore(query, mode, maxOf(limit, RERANK), useLsh, maskFor(scope), advanced = AdvancedWeights.enabled, scope = scope)
        // v23 iç içe arama: karışık (çok parçalı) çizimde sık çizilen ve kullanıcının en çok kullandığı şekiller, çizimden
        // bir-iki parça çıkarılarak (alt kümeler) yeniden aranır; çıkarılan parçanın payı kadar ceza eklenir.
        // Çizimin tamamı zaten güçlü eşleşiyorsa (benzerlik ≥ ~%80) alt kümelere bakılmaz
        val strong = (main.firstOrNull()?.second ?: Float.POSITIVE_INFINITY) <= SUBSET_SKIP_D
        val merged = if (mode == MatchMode.GROUP && !strong) mergeBest(main, frequentSubsets(query, useLsh, scope)) else main
        return merged.asSequence()
            .map { (k, d) ->
                val m = metaList[k]
                TemplateMatch(m.key, m.name, m.category, m.group, m.solid, exp(-d / TAU), d, mode, baseGeometry[k] ?: emptyList(), generic[k])
            }
            .filter { it.confidence >= minConfidence }
            .take(limit)
            .toList()
    }

    private fun mergeBest(a: List<Pair<Int, Float>>, b: List<Pair<Int, Float>>): List<Pair<Int, Float>> {
        if (b.isEmpty()) return a
        val best = HashMap<Int, Float>()
        (a + b).forEach { (k, d) -> if (d < (best[k] ?: Float.POSITIVE_INFINITY)) best[k] = d }
        return best.entries.map { it.key to it.value }.sortedBy { it.second }
    }

    /** Sık şekiller: genel önseli yüksek (çok çizilen) ya da kullanıcının en çok kabul ettiği şablonlar. */
    private fun frequentMask(scope: ShapeScope?): BooleanArray {
        val within = maskFor(scope)
        val u = IntArray(metaList.size) { usageIn(scope, it) }
        val byUsage = u.indices.filter { u[it] > 0 && within[it] }.sortedByDescending { u[it] }.take(FREQUENT_BY_USAGE).toHashSet()
        // Moda özel şablonlar o modda sık çizilen sayılır
        return BooleanArray(metaList.size) { k -> within[k] && (popularity[k] >= 4 || u[k] >= 2 || k in byUsage || (scope != null && modeOnly[k])) }
    }

    /**
     * Çizimden 1 (4 parçadan çoksa 2) parça çıkarılmış alt kümeler yalnız sık şekiller arasında aranır. Alt kümenin
     * uzaklığına çıkarılan parçaların uzunluk payı × [DROP_W] eklenir; iyi eşleşmeyen alt kümeler atılır.
     */
    private fun frequentSubsets(query: List<List<P>>, useLsh: Boolean?, scope: ShapeScope? = null): List<Pair<Int, Float>> {
        val strokes = query.filter { it.isNotEmpty() }
        val n = strokes.size
        if (n < 3 || n > SUBSET_MAX_PARTS) return emptyList()
        val mask = frequentMask(scope)
        if (mask.none { it }) return emptyList()
        val lengths = strokes.map { st -> var l = 0f; for (i in 1 until st.size) l += hypot(st[i].x - st[i - 1].x, st[i].y - st[i - 1].y); max(l, 1f) }
        val total = lengths.sum()
        val subsets = ArrayList<IntArray>()
        for (i in 0 until n) subsets += intArrayOf(i)
        if (n >= 4) for (i in 0 until n) for (j in i + 1 until n) subsets += intArrayOf(i, j)
        val out = ArrayList<Pair<Int, Float>>()
        for (drop in subsets.take(SUBSET_LIMIT)) {
            val share = drop.sumOf { lengths[it].toDouble() }.toFloat() / total
            if (share > 0.45f) continue
            val sub = strokes.filterIndexed { i, _ -> i !in drop }
            searchCore(sub, MatchMode.GROUP, 2, useLsh, mask, scope = scope).forEach { (k, d) ->
                if (d <= SUBSET_MAX_D) out += k to (d + DROP_W * share + DROP_FIXED * drop.size)
            }
        }
        return out
    }

    /** Asıl arama (uzaklıklarıyla, küçükten büyüğe); [only] verilirse yalnız o şablonlar. */
    private fun searchCore(
        query: List<List<P>>,
        mode: MatchMode,
        limit: Int,
        useLsh: Boolean?,
        only: BooleanArray?,
        /** v27 ek yeniden sıralayıcılar ($Q, parça grafiği, IDM, sinir ağı); alt küme aramalarında kapalı (hız). */
        advanced: Boolean = false,
        /** v29 modun kapsamı (ağ ve sayaçlar). */
        scope: ShapeScope? = null,
    ): List<Pair<Int, Float>> {
        val strokes = query.filter { it.isNotEmpty() }
        if (strokes.isEmpty() || entryList.isEmpty()) return emptyList()
        val norm = ShapeSignatures.normalize(strokes)
        val rawPolys = ShapeSignatures.polys(norm)
        val polys = ShapeSignatures.collapseSmall(rawPolys)
        val qn = polys.size
        val q = ShapeFeatures.extract(polys)
        val qc = ShapeSignatures.pool(q)
        val qa = ShapeFeatures.aspect(rawPolys)

        // 1-2. ön eleme ve kaba tarama
        val allowed = BooleanArray(entryList.size) { e -> (only == null || only[entryKey[e]]) && compatible(mode, qn, entryList[e].signature.strokeCount) }
        val nk = metaList.size
        val coarseBest = FloatArray(nk) { Float.POSITIVE_INFINITY }
        val lsh = useLsh ?: (rowEntry.size >= LSH_MIN_ROWS)
        var scanned = 0
        if (lsh) {
            val key = ShapeSignatures.simHashPooled(qc)
            for (probe in ShapeSignatures.probes(key, 2)) {
                val rows = lshBuckets[probe] ?: continue
                for (r in rows) scanned += coarseRow(r, qc, qa, allowed, coarseBest)
            }
        }
        if (!lsh || scanned < MIN_LSH_CANDIDATES) {
            for (r in rowEntry.indices) coarseRow(r, qc, qa, allowed, coarseBest)
        }
        val top0 = (0 until nk).filter { coarseBest[it].isFinite() }.sortedBy { coarseBest[it] }.take(COARSE_KEEP)
        // v27 sinir ağının en olası şablonları da adaydır (kaba taramanın kaçırdıkları)
        // v29 modun ağı (hazırsa), yoksa uygulamayla gelen ağ
        // v30 sinir ağı yalnız çok elemanlı (3+ çizgili) çizimlerde (karmaşıklığa göre algoritma seçimi)
        val net: ShapeClassifier? = if (advanced && ShapeComplexity.usesNet(strokes.size)) (scope?.net ?: netFor()) else null
        val netProbs = net?.probs(ShapeNet.input(polys, qc, qa))
        // v30 modun internet verisiyle eğitilmiş ağı çok elemanlı çizimde daha çok aday getirir
        val modeNet = net != null && scope?.net != null
        val netTop: List<Int> = if (net != null && netProbs != null) {
            netProbs.indices.sortedByDescending { netProbs[it] }.take(if (modeNet) AdvancedWeights.netCandidatesMany else NET_CANDIDATES)
                .mapNotNull { ni -> keyIndex[net.keys[ni]] }.filter { k -> coarseBest[k].isFinite() || (0 until entryList.size).any { e -> entryKey[e] == k && allowed[e] } }
        } else emptyList()
        val top = (top0 + netTop).distinct()
        if (top.isEmpty()) return emptyList()

        // 3. ince tarama (kNN): şablon başına en yakın kopya
        val topSet = top.toHashSet()
        val fineBest = FloatArray(nk) { Float.POSITIVE_INFINITY }
        val bestEntry = IntArray(nk) { -1 }
        val bestRow = IntArray(nk) { -1 }
        for (r in rowEntry.indices) {
            val e = rowEntry[r]
            if (!allowed[e]) continue
            val k = entryKey[e]
            if (k !in topSet) continue
            val d = 2f - 2f * dot(q, 0, fine, r * DIM, DIM) + ASPECT_W * sq(qa - entryList[e].signature.aspect)
            if (d < fineBest[k]) { fineBest[k] = d; bestEntry[k] = e; bestRow[k] = r }
        }

        // 4-5. yeniden sıralama ve önsel
        val rer = (top.filter { bestEntry[it] >= 0 }.sortedBy { fineBest[it] }.take(RERANK) +
            netTop.filter { bestEntry[it] >= 0 }.take(if (modeNet) AdvancedWeights.netRerankMany else 2)).distinct()
        val qCloud = ShapeFeatures.cloud(polys)
        val qSeq = if (mode == MatchMode.SINGLE) sequence(polys[0]) else null
        val qClosed = if (mode == MatchMode.SINGLE) ShapeSignatures.isClosed(polys[0]) else false
        val qCorners = if (mode == MatchMode.SINGLE) ShapeSignatures.cornersOf(polys[0]) else 0
        val qParts = if (mode == MatchMode.GROUP) partsOf(polys) else null
        // v27 ek yeniden sıralayıcıların sorgu tarafı (bir kez)
        val qQ = if (advanced) QDollar.cloud(polys) else null
        val qGraph = if (advanced && mode == MatchMode.GROUP) PartGraph.of(polys) else null
        val maxP = netProbs?.maxOrNull()?.coerceAtLeast(1e-6f) ?: 1f
        // v27: ek ölçütler adaylar arasında en iyisine göre (göreli) ceza olur: en iyi adayın benzerliği değişmez,
        // karalamalar yükselmez, diğer adaylar ölçüt başına geriye düşer
        val netW = if (modeNet) max(scope!!.netWeight, AdvancedWeights.netMany) else if (scope?.net != null) scope.netWeight else AdvancedWeights.net
        val adv = if (advanced) advancedPenalties(mode, rer, bestEntry, bestRow, q, qQ, qGraph, net, netProbs, maxP, netW, if (modeNet) AdvancedWeights.netCapMany else AdvancedWeights.cap) else null
        val scored = rer.mapIndexed { ri, k ->
            val e = bestEntry[k]
            val sig = entryList[e].signature
            var d = fineBest[k]
            d += CHAMFER_W * ShapeFeatures.chamfer(qCloud, cloudOf(e))
            if (mode == MatchMode.SINGLE) {
                val tClosed = sig.closedMask and 1 != 0
                d += SEQ_W * seqDistance(qSeq!!, seqOf(e), qClosed && tClosed)
                if (qClosed != tClosed) d += CLOSED_W
                d += CORNER_W * min(abs(qCorners - sig.corners), 4)
            } else {
                d += PART_W * partDistance(qParts!!, partsOfEntry(e))
                d += COUNT_W * abs(qn - sig.strokeCount).toFloat() / max(qn, sig.strokeCount)
            }

            d -= PRIOR_W * ln((usageIn(scope, k) + 1f) / (rejectsIn(scope, k) + 1f)).coerceIn(-2f, 2f)
            // v23 popülerlik önseli: çok çizilen şekiller eşitlikte öne geçer (geometriyi geçemez)
            d -= POP_W * ln(popularity[k] / 2f)
            if (generic[k]) d += GENERIC_D
            val a = adv?.get(ri) ?: 0f
            // Sıralama ek ölçütlerin tamamıyla; benzerlik (güven) yalnız bir payıyla: uzak çizimlerde doğru şekil
            // öne geçer ama benzerliği gereksiz düşmez
            Triple(k, max(d + a, 0f), max(d + AdvancedWeights.confShare * a, 0f))
        }.sortedBy { it.second }
        // Güven sırası sıralamayla aynı kalır (birleştirme uzaklığa göre sıralar)
        val out = ArrayList<Pair<Int, Float>>(min(limit, scored.size))
        var prev = 0f
        for (t in scored) {
            if (out.size >= limit) break
            val d = max(t.third, prev)
            out += t.first to d
            prev = d + 1e-4f
        }
        return out
    }

    // --- v27 ek yeniden sıralayıcılar ---

    private val qClouds = arrayOfNulls<QCloud>(entryList.size)
    private val graphs = arrayOfNulls<PGraph>(entryList.size)

    private fun qOf(e: Int): QCloud? = qClouds[e] ?: QDollar.cloud(entryPolys(e)).also { qClouds[e] = it }

    internal fun graphOf(e: Int): PGraph = graphs[e] ?: PartGraph.of(entryPolys(e)).also { graphs[e] = it }

    /** Uygulamayla gelen ağ (dizinin şablonlarıyla eşleşiyorsa). */
    private fun netFor(): ShapeNet? = ShapeNet.bundled?.takeIf { n -> n.keys.isNotEmpty() && n.keys.count { it in keyIndex } >= n.keys.size / 2 }

    /**
     * $Q bulut uzaklığı, IDM görüntü uzaklığı, (grupta) parça grafiği uzaklığı ve sinir ağının olasılığı; her ölçüt
     * adaylar arasındaki en iyi değerden farkıyla ([AdvancedWeights] ağırlıklarıyla) cezaya dönüşür.
     */
    private fun advancedPenalties(
        mode: MatchMode, rer: List<Int>, bestEntry: IntArray, bestRow: IntArray, q: FloatArray, qQ: QCloud?, qGraph: PGraph?,
        net: ShapeClassifier?, netProbs: FloatArray?, maxP: Float, netWeight: Float = AdvancedWeights.net, netCap: Float = AdvancedWeights.cap,
    ): FloatArray {
        val w = AdvancedWeights
        val n = rer.size
        val out = FloatArray(n)
        if (n == 0) return out
        val terms = Array(4) { FloatArray(n) { Float.NaN } }
        for ((i, k) in rer.withIndex()) {
            val e = bestEntry[k]
            if (qQ != null) qOf(e)?.let { t -> terms[0][i] = QDollar.distance(qQ, t) }
            val row = bestRow[k]
            if (row >= 0) terms[1][i] = Idm.distance(q, 0, fine, row * DIM)
            if (qGraph != null) terms[2][i] = PartGraph.distance(qGraph, graphOf(e))
            if (net != null && netProbs != null) {
                val ni = net.indexOf(metaList[k].key)
                // Ağın tanımadığı (kullanıcı) şablonlar cezasız
                terms[3][i] = if (ni >= 0) 1f - (netProbs[ni] / maxP).coerceIn(0f, 1f) else 0f
            }
        }
        val weights = floatArrayOf(if (mode == MatchMode.SINGLE) w.q1 else w.q, w.idm, w.graph, netWeight)
        for (t in 0 until 4) {
            val col = terms[t]
            var mn = Float.POSITIVE_INFINITY
            for (v in col) if (!v.isNaN() && v < mn) mn = v
            if (!mn.isFinite()) continue
            val cap = if (t == 3) netCap else w.cap
            for (i in 0 until n) if (!col[i].isNaN()) out[i] += weights[t] * min(col[i] - mn, cap)
        }
        return out
    }

    /** Testler için: [query]'nin [key] şablonuna ham ek ölçütleri ($Q, IDM, grafik). */
    internal fun advancedTermsFor(query: List<List<P>>, key: String): FloatArray? {
        val k = keyIndex[key] ?: return null
        val strokes = query.filter { it.isNotEmpty() }
        val polys = ShapeSignatures.collapseSmall(ShapeSignatures.polys(ShapeSignatures.normalize(strokes)))
        val q = ShapeFeatures.extract(polys)
        val qa = ShapeFeatures.aspect(ShapeSignatures.polys(ShapeSignatures.normalize(strokes)))
        var bestE = -1; var bestR = -1; var bd = Float.POSITIVE_INFINITY
        for (r in rowEntry.indices) {
            val e = rowEntry[r]
            if (entryKey[e] != k) continue
            val d = 2f - 2f * dot(q, 0, fine, r * DIM, DIM) + ASPECT_W * sq(qa - entryList[e].signature.aspect)
            if (d < bd) { bd = d; bestE = e; bestR = r }
        }
        if (bestE < 0) return null
        val qQ = QDollar.cloud(polys)
        return floatArrayOf(
            qQ?.let { a -> qOf(bestE)?.let { QDollar.distance(a, it) } } ?: Float.NaN,
            Idm.distance(q, 0, fine, bestR * DIM),
            if (polys.size > 1) PartGraph.distance(PartGraph.of(polys), graphOf(bestE)) else Float.NaN,
        )
    }

    /**
     * v27 kısmi eşleme (az eleman seçiliyken eksik parça önerisi): seçilen 1–3 parçanın çok parçalı şablonların bir
     * kısmına benzediği şablonlar; eşleme ve güvenle (en iyi [limit]).
     */
    fun partialMatches(
        query: List<List<P>>, limit: Int = 4, minConfidence: Float = 0.45f,
        /** v29 modun kapsamı: yalnız modun şablonları; modun ağı (parça çizimlerinden de öğrenir) sıralamaya katılır. */
        scope: ShapeScope? = null,
    ): List<PartialResult> {
        val strokes = query.filter { it.isNotEmpty() }
        if (strokes.isEmpty() || strokes.size > 3) return emptyList()
        val norm = ShapeSignatures.normalize(strokes)
        val polys = ShapeSignatures.polys(norm)
        val qg = PartGraph.of(polys)
        val mask = maskFor(scope)
        // v29 modun ağının bu (eksik) çizim için olasılıkları
        // v30 ağ yalnız çok elemanlı (3+ çizgili) parçalarda
        val net = scope?.net?.takeIf { ShapeComplexity.usesNet(strokes.size) }
        val probs = net?.let { n -> runCatching { ShapeNet.inputOf(strokes)?.let { n.probs(it) } }.getOrNull() }
        val maxP = probs?.maxOrNull() ?: 1f
        val best = HashMap<Int, Pair<Int, PartialMatch>>()
        for (e in entryList.indices) {
            if (entryList[e].user) continue
            if (!mask[entryKey[e]]) continue
            val sig = entryList[e].signature
            if (sig.strokeCount <= strokes.size || sig.strokeCount > 24) continue
            val k = entryKey[e]
            val prev = best[k]
            val pm = PartGraph.partial(qg, graphOf(e), min(PartGraph.PARTIAL_MAX, prev?.second?.cost ?: Float.POSITIVE_INFINITY)) ?: continue
            if (prev == null || pm.cost < prev.second.cost) best[k] = e to pm
        }
        return best.entries.asSequence()
            .map { (k, v) ->
                // Popüler ve sık kabul edilen şekiller eşitlikte öne
                var conf = PartGraph.confidence(v.second.cost) * (1f + 0.03f * (popularity[k] - 3)) * (1f + 0.05f * ln((usageIn(scope, k) + 1f) / (rejectsIn(scope, k) + 1f)).coerceIn(-1f, 1f))
                if (net != null && probs != null) conf *= 1f + 0.5f * scope.netWeight * (relativeProb(probs, net.indexOf(metaList[k].key), maxP) - 0.5f)
                PartialResult(metaList[k].key, metaList[k].name, metaList[k].category, conf.coerceIn(0f, 1f), v.second.mapping, entryList[v.first].signature.geometry)
            }
            .filter { it.confidence >= minConfidence }
            .sortedByDescending { it.confidence }
            .take(limit)
            .toList()
    }

    private fun coarseRow(r: Int, qc: FloatArray, qa: Float, allowed: BooleanArray, best: FloatArray): Int {
        val e = rowEntry[r]
        if (!allowed[e]) return 0
        val d = 2f - 2f * dot(qc, 0, coarse, r * PD, PD) + ASPECT_W * sq(qa - entryList[e].signature.aspect)
        val k = entryKey[e]
        if (d < best[k]) best[k] = d
        return 1
    }

    private fun compatible(mode: MatchMode, qn: Int, tn: Int): Boolean = when (mode) {
        MatchMode.SINGLE -> tn == 1
        MatchMode.GROUP -> tn >= 2 && (abs(qn - tn) <= 2 || min(qn, tn).toFloat() / max(qn, tn) >= 0.5f)
    }

    // --- Tek çizgi: dizi hizalaması ---

    private fun cloudOf(e: Int): FloatArray = clouds[e] ?: ShapeFeatures.cloud(entryPolys(e)).also { clouds[e] = it }

    private fun entryPolys(e: Int) = ShapeSignatures.collapseSmall(ShapeSignatures.polys(entryList[e].signature.geometry))

    private fun seqOf(e: Int): FloatArray = seqs[e] ?: sequence(ShapeSignatures.polys(entryList[e].signature.geometry)[0]).also { seqs[e] = it }

    /** Çizgi eşit aralıklı [SEQ_N] noktaya örneklenir; kutu merkezine göre, uzun kenar 1 olacak şekilde. */
    private fun sequence(p: Poly): FloatArray {
        val out = FloatArray(SEQ_N * 2)
        val s = max(p.extent, 1e-4f)
        val len = p.length
        if (len < 1e-6f || p.n < 2) {
            for (i in 0 until SEQ_N) { out[2 * i] = 0f; out[2 * i + 1] = 0f }
            return out
        }
        val step = len / (SEQ_N - 1)
        var k = 0; var acc = 0f; var i = 1
        var px = p.x[0]; var py = p.y[0]
        out[0] = px; out[1] = py; k = 1
        while (i < p.n && k < SEQ_N - 1) {
            val qx = p.x[i]; val qy = p.y[i]
            val d = hypot(qx - px, qy - py)
            if (acc + d >= step && d > 0f) {
                val t = (step - acc) / d
                px += t * (qx - px); py += t * (qy - py)
                out[2 * k] = px; out[2 * k + 1] = py; k++; acc = 0f
            } else { acc += d; px = qx; py = qy; i++ }
        }
        while (k < SEQ_N) { out[2 * k] = p.x[p.n - 1]; out[2 * k + 1] = p.y[p.n - 1]; k++ }
        for (j in 0 until SEQ_N) { out[2 * j] = (out[2 * j] - p.cx) / s; out[2 * j + 1] = (out[2 * j + 1] - p.cy) / s }
        return out
    }

    /** Ortalama nokta uzaklığı; iki yön, kapalı çizgilerde başlangıç noktası da denenir (en küçüğü). */
    private fun seqDistance(a: FloatArray, b: FloatArray, cyclic: Boolean): Float {
        var best = Float.POSITIVE_INFINITY
        val shifts = if (cyclic) SEQ_N - 1 else 1
        for (rev in 0..1) for (sh in 0 until shifts) {
            var sum = 0f
            for (i in 0 until SEQ_N) {
                var j = if (rev == 0) i else SEQ_N - 1 - i
                if (cyclic) j = (j + sh) % (SEQ_N - 1)
                sum += hypot(a[2 * i] - b[2 * j], a[2 * i + 1] - b[2 * j + 1])
                if (sum >= best * SEQ_N) break
            }
            best = min(best, sum / SEQ_N)
        }
        return best
    }

    // --- Grup: parça eşleme ---

    private fun partsOfEntry(e: Int): Array<FloatArray> = parts[e] ?: partsOf(entryPolys(e)).also { parts[e] = it }

    /** Her parça: merkez x, y, boy (uzun kenar) ve uzunluk payı — grubun kutusuna göre. */
    private fun partsOf(polys: List<Poly>): Array<FloatArray> {
        var a = Float.POSITIVE_INFINITY; var b = Float.POSITIVE_INFINITY; var c = Float.NEGATIVE_INFINITY; var d = Float.NEGATIVE_INFINITY
        polys.forEach { a = min(a, it.minX); b = min(b, it.minY); c = max(c, it.maxX); d = max(d, it.maxY) }
        val s = max(max(c - a, d - b), 1e-4f)
        val total = polys.sumOf { max(it.length, it.extent, 0.02f * s).toDouble() }.toFloat()
        return Array(polys.size) { i ->
            val p = polys[i]
            floatArrayOf((p.cx - a) / s, (p.cy - b) / s, p.extent / s, max(p.length, p.extent, 0.02f * s) / total)
        }
    }

    private fun max(a: Float, b: Float, c: Float) = max(max(a, b), c)

    /** İki yönlü, uzunluk ağırlıklı en yakın parça maliyeti (konum + boy farkı). */
    private fun partDistance(a: Array<FloatArray>, b: Array<FloatArray>): Float {
        fun oneWay(p: Array<FloatArray>, q: Array<FloatArray>): Float {
            var sum = 0f
            for (x in p) {
                var best = Float.POSITIVE_INFINITY
                for (y in q) {
                    val c = hypot(x[0] - y[0], x[1] - y[1]) + 0.5f * abs(x[2] - y[2])
                    if (c < best) best = c
                }
                sum += x[3] * best
            }
            return sum
        }
        return 0.5f * (oneWay(a, b) + oneWay(b, a))
    }

    companion object {
        private val DIM = ShapeSignature.DIM
        private val PD = ShapeSignatures.POOLED

        const val ASPECT_W = 1.2f
        const val CHAMFER_W = 2.0f
        const val SEQ_W = 1.5f
        const val CLOSED_W = 0.25f
        const val CORNER_W = 0.05f
        const val PART_W = 1.2f
        const val COUNT_W = 0.3f
        const val PRIOR_W = 0.05f
        /** v23 popülerlik önselinin ağırlığı. */
        const val POP_W = 0.05f
        /** v23 iç içe arama: çıkarılan parçaların uzunluk payı cezası, parça başına sabit ceza ve kabul sınırı. */
        const val DROP_W = 0.55f
        const val DROP_FIXED = 0.04f
        const val SUBSET_MAX_D = 0.42f
        const val SUBSET_SKIP_D = 0.2f
        const val SUBSET_MAX_PARTS = 12
        const val SUBSET_LIMIT = 40
        const val FREQUENT_BY_USAGE = 12
        /** v22: benzerlik ölçeği (v21'de 0.45: yalnız çok benzeyen çizimler öneriliyordu). */
        const val TAU = 0.9f
        /** Genel şablonların (kaş, gülümseme gibi tek yaylar) uzaklık cezası. */
        const val GENERIC_D = 0.4f
        const val SEQ_N = 32
        const val COARSE_KEEP = 14
        const val RERANK = 6
        const val LSH_MIN_ROWS = 3000
        /** v27 sinir ağından eklenen aday sayısı. */
        const val NET_CANDIDATES = 3
        const val MIN_LSH_CANDIDATES = 48

        private fun sq(v: Float) = v * v

        /** 4 birikimli iç çarpım ([len] 4'ün katı). */
        private fun dot(q: FloatArray, qo: Int, m: FloatArray, off: Int, len: Int): Float {
            var a0 = 0f; var a1 = 0f; var a2 = 0f; var a3 = 0f
            var k = 0
            while (k < len) {
                a0 += q[qo + k] * m[off + k]
                a1 += q[qo + k + 1] * m[off + k + 1]
                a2 += q[qo + k + 2] * m[off + k + 2]
                a3 += q[qo + k + 3] * m[off + k + 3]
                k += 4
            }
            return (a0 + a1) + (a2 + a3)
        }

        /** Katalogdan (veritabanı olmadan) dizin: testler ve veritabanı hazır olmadan önce. */
        fun fromCatalog(templates: List<ShapeTemplate> = ShapeTemplates.all): ShapeIndex {
            val metas = templates.map { TemplateMeta(it.key, it.name, it.category, it.group, it.description, it.solid) }
            val entries = templates.mapIndexed { i, t -> IndexEntry(i.toLong(), t.key, ShapeSignatures.of(t.strokes)) }
            return ShapeIndex(metas, entries)
        }
    }
}

/**
 * v27 kısmi eşleşme: [key] şablonunun parçaları; [mapping] sorgunun her parçasının eşlendiği şablon parçası, [geometry]
 * şablonun birim kutudaki çizgileri (eksik parçalar eşlenmeyenlerdir).
 */
class PartialResult(
    val key: String, val name: String, val category: String, val confidence: Float, val mapping: IntArray, val geometry: List<List<P>>,
)

/**
 * v27 ek yeniden sıralayıcıların ağırlıkları ve tipik doğru eşleşme değerleri (el çizimi benzetimiyle ayarlandı; bkz.
 * AdvancedMatchTest). [enabled] kapalıysa arama v26 ile aynıdır.
 */
object AdvancedWeights {
    @Volatile var enabled: Boolean = true
    var q: Float = 1.2f
    var q1: Float = 0.6f
    var idm: Float = 0.8f
    var graph: Float = 0.35f
    var net: Float = 0.12f
    /**
     * v30 çok elemanlı (3+ çizgili) çizimde modun ağının ağırlığı, ceza üst sınırı, ağdan gelen aday ve yeniden sıralamaya
     * giren aday sayısı: ağ, internetteki gerçek el çizimleriyle eğitildiği için bu kademede belirleyicidir
     * (bkz. ModeNetTraining.evaluateSearch).
     */
    var netMany: Float = 4f
    var netCapMany: Float = 6f
    var netCandidatesMany: Int = 8
    var netRerankMany: Int = 6
    /** Ölçüt başına en çok fark (tek ölçüt bir adayı tek başına gömemez). */
    var cap: Float = 0.5f
    /** Ek ölçütlerin benzerliğe (güvene) yansıyan payı. */
    var confShare: Float = 0.15f
}

/** Şablonun çizime oturtulması. */
object TemplateFit {
    /**
     * Birim kutudaki [geometry] [left]..[right] × [top]..[bottom] kutusuna oturtulur: en-boy oranı çizime uyar ama
     * şablonun kendi oranından en çok [stretch] kat sapar (çok ince çizimde şablon ezilmez).
     */
    fun fit(geometry: List<List<P>>, left: Float, top: Float, right: Float, bottom: Float, stretch: Float = 1.4f): List<List<P>> {
        var a = Float.POSITIVE_INFINITY; var b = Float.POSITIVE_INFINITY; var c = Float.NEGATIVE_INFINITY; var d = Float.NEGATIVE_INFINITY
        for (s in geometry) for (p in s) { a = min(a, p.x); b = min(b, p.y); c = max(c, p.x); d = max(d, p.y) }
        if (!a.isFinite()) return emptyList()
        val tw = max(c - a, 1e-3f); val th = max(d - b, 1e-3f)
        val qw = max(right - left, 1f); val qh = max(bottom - top, 1f)
        var sx = qw / tw; var sy = qh / th
        // Şablonun kendi oranına göre sınırlı esneme
        val g = sqrt(sx * sy)
        sx = g * (sx / g).coerceIn(1f / stretch, stretch)
        sy = g * (sy / g).coerceIn(1f / stretch, stretch)
        // Çok ince (çizgi gibi) şablon/çizimde boyut uzun kenardan
        if (tw < 0.08f || th < 0.08f) { val s = max(qw, qh) / max(tw, th); sx = s; sy = s }
        val cx = (left + right) / 2f; val cy = (top + bottom) / 2f
        val tcx = (a + c) / 2f; val tcy = (b + d) / 2f
        return geometry.map { s -> s.map { P(cx + (it.x - tcx) * sx, cy + (it.y - tcy) * sy) } }
    }

    /** Noktalar arası en çok [step] olacak şekilde ara noktalar eklenir (düz kenarlar ve köşeler korunur). */
    fun densify(stroke: List<P>, step: Float): List<P> {
        if (stroke.size < 2 || step <= 0f) return stroke
        val out = ArrayList<P>(stroke.size * 2)
        out += stroke[0]
        for (i in 1 until stroke.size) {
            val p = stroke[i - 1]; val q = stroke[i]
            val n = kotlin.math.ceil(hypot(q.x - p.x, q.y - p.y) / step).toInt()
            for (k in 1 until n) { val t = k / n.toFloat(); out += P(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t) }
            out += q
        }
        return out
    }
}

/** Birden çok elemanın oluşturduğu şeklin elemanları: son çizginin çevresinde birbirine değen/yakın çizgiler. */
object TemplateGrouping {
    /**
     * [boxes] çizgilerin kutuları (sol, üst, sağ, alt), çizilme sırasıyla; [seed] son çizgi. Gruptaki bir çizginin kutusuyla
     * arasındaki boşluk, ikisinden büyüğünün boyunun [pad] katını (en az [minPad]) aşmayan çizgiler gruba katılır
     * (tekrarlı: iç içe ve birbirine değen parçalar). Yan yana duran ayrı çizimler birleşmez. En çok [maxCount] çizgi.
     */
    fun cluster(boxes: List<FloatArray>, seed: Int, pad: Float = 0.2f, minPad: Float = 12f, maxCount: Int = 24): List<Int> {
        if (seed !in boxes.indices) return emptyList()
        val inGroup = BooleanArray(boxes.size)
        inGroup[seed] = true
        val order = ArrayList<Int>(); order += seed
        fun ext(b: FloatArray) = max(b[2] - b[0], b[3] - b[1])
        var changed = true
        while (changed && order.size < maxCount) {
            changed = false
            for (i in boxes.indices.reversed()) {
                if (inGroup[i]) continue
                val b = boxes[i]
                val touch = order.any { j ->
                    val g = boxes[j]
                    val gap = max(max(g[0] - b[2], b[0] - g[2]), max(g[1] - b[3], b[1] - g[3]))
                    gap <= max(minPad, pad * max(ext(g), ext(b)))
                }
                if (touch) { inGroup[i] = true; order += i; changed = true; if (order.size >= maxCount) break }
            }
        }
        return order.sorted()
    }
}

/** Öneri eşikleri (el çizimi benzetimi ve karalamalarla ayarlandı; bkz. ShapeTemplatesTest). */
object TemplateSuggest {
    /**
     * v22 sabit benzerlik eşiği (%50): çizim veritabanındaki şablona uzak olsa da bu orana ulaşan tahmin önerilir.
     * El çizimi benzetiminde doğru eşleşmeler çoğunlukla %75–95; rastgele karalamaların çoğu %45'in altında.
     */
    const val MIN_SIMILARITY = 0.5f

    /** Tek çizgide önerilecek en düşük benzerlik. */
    const val MIN_SINGLE = MIN_SIMILARITY

    /** Çok elemanlı şekilde önerilecek en düşük benzerlik. */
    const val MIN_GROUP = MIN_SIMILARITY

    /** Basit geometri (çember, üçgen…) de güçlü tanındıysa tek çizgi şablonunun önde gösterilmesi için gereken benzerlik. */
    const val STRONG_SINGLE = 0.86f

    /** Tanınan yazının önüne geçmek için çok elemanlı şablonda gereken benzerlik. */
    const val STRONG_GROUP = 0.84f
}
