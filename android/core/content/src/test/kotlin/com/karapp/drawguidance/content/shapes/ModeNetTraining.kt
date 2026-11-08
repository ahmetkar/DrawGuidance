package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.vector.P
import org.junit.Assume
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * v30 moda özel sinir ağlarının bilgisayarda eğitimi (yalnız ortam değişkenleri verilince çalışır):
 *
 * 1. [export] (MODENET_DATA, MODENET_MAP, MODENET_OUT): her mod için eğitim seti: Google "Quick, Draw!" veri kümesinden
 *    (CC BY 4.0) modun şablonlarıyla eşleşen kategorilerin el çizimleri (2+ çizgili; ağ yalnız çok elemanlı çizimlerde
 *    kullanılır) ve her şablonun el çizimi benzetimleri → 83 boyutlu girdi. Ayrılan test çizimleri (3+ çizgili) ayrı yazılır.
 * 2. Ağ numpy ile eğitilir (scratchpad'deki train_modenet.py; Adam, sınıf dengeli örnekleme, girdi ölçekleme ilk katmana
 *    katlanır).
 * 3. [pack] (MODENET_PACK, MODENET_OUT): float ağırlıklar int8'e çevrilir, `src/main/resources/shapenet/mode_<mod>.bin`
 *    yazılır; test çizimlerinde ağın ve tam aramanın (ağlı / ağsız) doğruluğu ölçülür.
 */
class ModeNetTraining {
    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    private fun keysOf(mode: ShapeMode): List<String> =
        ShapeTemplates.catalog.filter { ModeCatalog.allows(mode, it.key, it.category) }.map { it.key }

    private fun mapping(file: File): Map<String, String> = file.readLines().filter { it.contains('\t') }
        .associate { line -> val (cat, key) = line.split('\t'); cat.replace(' ', '_') to key }

    private fun readDrawings(file: File, limit: Int = Int.MAX_VALUE): List<List<List<P>>> {
        if (!file.exists()) return emptyList()
        val out = ArrayList<List<List<P>>>()
        file.forEachLine { line ->
            if (out.size >= limit || line.isBlank()) return@forEachLine
            val strokes = line.split(';').map { s ->
                s.trim().split(' ').filter { it.isNotEmpty() }.map { pt -> val (x, y) = pt.split(','); P(x.toFloat(), y.toFloat()) }
            }.filter { it.isNotEmpty() }
            if (strokes.isNotEmpty()) out += strokes
        }
        return out
    }

    private fun writeSet(file: File, xs: List<FloatArray>, ys: List<Int>) {
        DataOutputStream(file.outputStream().buffered()).use { d ->
            d.writeInt(xs.size); d.writeInt(ShapeNet.IN_DIM)
            xs.forEach { x -> x.forEach(d::writeFloat) }
            ys.forEach(d::writeInt)
        }
    }

    @Test
    fun export() {
        val data = env("MODENET_DATA"); val map = env("MODENET_MAP"); val outDir = env("MODENET_OUT")
        Assume.assumeTrue(data != null && map != null && outDir != null)
        val out = File(outDir!!).apply { mkdirs() }
        val catToKey = mapping(File(map!!))
        val keyToCats = catToKey.entries.groupBy({ it.value }, { it.key })
        val byKey = ShapeTemplates.catalog.associateBy { it.key }
        for (mode in ShapeMode.entries) {
            val keys = keysOf(mode)
            val rnd = java.util.Random(3000L + mode.ordinal)
            val xs = ArrayList<FloatArray>(); val ys = ArrayList<Int>()
            val tx = ArrayList<FloatArray>(); val ty = ArrayList<Int>()
            var internet = 0; var synthetic = 0
            for ((k, key) in keys.withIndex()) {
                val cats = keyToCats[key].orEmpty()
                // İnternet verisi: 2+ çizgili çizimler (ağ yalnız 3+ elemanlı sorgularda çalışır)
                for (cat in cats) {
                    for (d in readDrawings(File(data, "$cat.train.txt"))) {
                        if (d.size < 2) continue
                        ShapeNet.inputOf(d)?.let { xs += it; ys += k; internet++ }
                    }
                    for (d in readDrawings(File(data, "$cat.test.txt"))) {
                        if (d.size < ShapeComplexity.NET_MIN_ELEMENTS) continue
                        ShapeNet.inputOf(d)?.let { tx += it; ty += k }
                    }
                }
                // Şablonun el çizimi benzetimleri (internet verisi olmayanlarda daha çok)
                val t = byKey.getValue(key)
                val n = if (cats.isEmpty()) 160 else 60
                repeat(n) { i ->
                    val q = ShapeAugment.handDrawn(t.strokes, rnd, amount = 0.6f + rnd.nextFloat() * 0.9f, strong = i % 3 == 2)
                    ShapeNet.inputOf(q)?.let { xs += it; ys += k; synthetic++ }
                }
            }
            val name = mode.name.lowercase()
            writeSet(File(out, "train_$name.bin"), xs, ys)
            writeSet(File(out, "test_$name.bin"), tx, ty)
            File(out, "keys_$name.txt").writeText(keys.joinToString("\n"))
            println("$mode: ${keys.size} sınıf, eğitim ${xs.size} (internet $internet, benzetim $synthetic), test ${tx.size}")
        }
    }

    /** numpy'nin yazdığı ağırlıklar: in, hidden, k ve w1, b1, w2, b2 (büyük uçlu float). */
    private fun readWeights(file: File): FloatArray4 = DataInputStream(file.inputStream().buffered()).use { d ->
        val inDim = d.readInt(); val hidden = d.readInt(); val k = d.readInt()
        FloatArray4(inDim, hidden, k,
            FloatArray(hidden * inDim) { d.readFloat() }, FloatArray(hidden) { d.readFloat() },
            FloatArray(k * hidden) { d.readFloat() }, FloatArray(k) { d.readFloat() })
    }

    class FloatArray4(val inDim: Int, val hidden: Int, val k: Int, val w1: FloatArray, val b1: FloatArray, val w2: FloatArray, val b2: FloatArray)

    private fun readSet(file: File): Pair<List<FloatArray>, IntArray> = DataInputStream(file.inputStream().buffered()).use { d ->
        val n = d.readInt(); val dim = d.readInt()
        val xs = List(n) { FloatArray(dim) { d.readFloat() } }
        xs to IntArray(n) { d.readInt() }
    }

    @Test
    fun pack() {
        val outDir = env("MODENET_OUT")
        Assume.assumeTrue(env("MODENET_PACK") == "1" && outDir != null)
        val out = File(outDir!!)
        val res = File("src/main/resources/shapenet").apply { mkdirs() }
        for (mode in ShapeMode.entries) {
            val name = mode.name.lowercase()
            val keys = File(out, "keys_$name.txt").readLines().filter { it.isNotBlank() }
            val w = readWeights(File(out, "weights_$name.bin"))
            require(w.k == keys.size && w.inDim == ShapeNet.IN_DIM)
            val net = ShapeNet.quantize(keys, w.inDim, w.hidden, w.w1, w.b1, w.w2, w.b2)
            val file = File(res, "mode_$name.bin")
            file.writeBytes(net.encode())
            val (tx, ty) = readSet(File(out, "test_$name.bin"))
            var top1 = 0; var top3 = 0
            for (i in tx.indices) {
                val p = net.probs(tx[i])
                val order = p.indices.sortedByDescending { p[it] }
                if (order[0] == ty[i]) top1++
                if (ty[i] in order.take(3)) top3++
            }
            println("$mode int8 ağ: ${file.length() / 1024} KB, ${keys.size} sınıf; internet test çizimleri (3+ çizgi) ilk-1 %.1f%%, ilk-3 %.1f%% (%d çizim)"
                .format(100f * top1 / tx.size, 100f * top3 / tx.size, tx.size))
        }
    }

    /**
     * MODENET_EVAL (ve MODENET_DATA, MODENET_MAP): tam aramanın (kaba tarama, kNN, yeniden sıralama) internet test
     * çizimlerinde doğruluğu: modun ağıyla ve ağsız. Ağ yalnız 3+ çizgili sorgularda devreye girer.
     */
    @Test
    fun evaluateSearch() {
        val data = env("MODENET_DATA"); val map = env("MODENET_MAP")
        Assume.assumeTrue(env("MODENET_EVAL") == "1" && data != null && map != null)
        val catToKey = mapping(File(map!!))
        val index = ShapeIndex.fromCatalog(ShapeTemplates.catalog)
        val models = ShapeModeModels(null)
        val per = env("MODENET_EVAL_PER")?.toIntOrNull() ?: 12
        env("NET_MANY")?.toFloatOrNull()?.let { AdvancedWeights.netMany = it }
        env("NET_CAP_MANY")?.toFloatOrNull()?.let { AdvancedWeights.netCapMany = it }
        env("NET_CAND_MANY")?.toIntOrNull()?.let { AdvancedWeights.netCandidatesMany = it }
        env("NET_RERANK_MANY")?.toIntOrNull()?.let { AdvancedWeights.netRerankMany = it }
        println("ağırlık ${AdvancedWeights.netMany}, üst sınır ${AdvancedWeights.netCapMany}, aday ${AdvancedWeights.netCandidatesMany}/${AdvancedWeights.netRerankMany}")
        for (mode in listOf(ShapeMode.ART, ShapeMode.NOTES)) {
            val withNet = models.scope(mode, index)
            val noNet = ShapeScope(mode, withNet.keys, null, 0f, emptyMap(), emptyMap())
            var n = 0; var a1 = 0; var a3 = 0; var b1 = 0; var b3 = 0
            var tNet = 0L; var tPlain = 0L
            for ((cat, key) in catToKey) {
                if (key !in withNet.keys) continue
                for (d in readDrawings(File(data, "$cat.test.txt"), limit = 200).filter { it.size >= ShapeComplexity.NET_MIN_ELEMENTS }.take(per)) {
                    val q = d.map { s -> s.map { P(it.x * 2f, it.y * 2f) } }
                    n++
                    var t0 = System.nanoTime()
                    val r1 = index.search(q, MatchMode.GROUP, 3, scope = withNet).map { it.key }
                    tNet += System.nanoTime() - t0
                    t0 = System.nanoTime()
                    val r2 = index.search(q, MatchMode.GROUP, 3, scope = noNet).map { it.key }
                    tPlain += System.nanoTime() - t0
                    if (r1.firstOrNull() == key) a1++; if (key in r1) a3++
                    if (r2.firstOrNull() == key) b1++; if (key in r2) b3++
                }
            }
            // Şablon benzetimleri (3+ çizgili şablonlar): ağ şablon çizimlerini de bozmamalı
            var sn = 0; var s1 = 0; var s3 = 0; var p1 = 0; var p3 = 0
            val srnd = java.util.Random(77)
            for (t in ShapeTemplates.catalog) {
                if (t.key !in withNet.keys || t.strokeCount < ShapeComplexity.NET_MIN_ELEMENTS) continue
                repeat(2) {
                    val q = ShapeAugment.handDrawn(t.strokes, srnd)
                    sn++
                    val r1 = index.search(q, MatchMode.GROUP, 3, scope = withNet).map { it.key }
                    val r2 = index.search(q, MatchMode.GROUP, 3, scope = noNet).map { it.key }
                    if (r1.firstOrNull() == t.key) s1++; if (t.key in r1) s3++
                    if (r2.firstOrNull() == t.key) p1++; if (t.key in r2) p3++
                }
            }
            println("$mode şablon benzetimi (3+ çizgi, $sn): ağlı ilk-1 %.1f%% ilk-3 %.1f%% | ağsız ilk-1 %.1f%% ilk-3 %.1f%%"
                .format(100f * s1 / sn, 100f * s3 / sn, 100f * p1 / sn, 100f * p3 / sn))
            println("$mode tam arama (3+ çizgili internet çizimi, $n): ağlı ilk-1 %.1f%% ilk-3 %.1f%% (%.1f ms) | ağsız ilk-1 %.1f%% ilk-3 %.1f%% (%.1f ms)"
                .format(100f * a1 / n, 100f * a3 / n, tNet / 1e6 / n, 100f * b1 / n, 100f * b3 / n, tPlain / 1e6 / n))
        }
    }
}
