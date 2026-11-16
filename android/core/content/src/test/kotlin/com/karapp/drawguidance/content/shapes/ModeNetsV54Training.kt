package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.vector.P
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * v54 mod şekil ağlarının yeniden eğitimi (tek komut, tekrarlanabilir; 2 çekirdekte ~1 saat):
 *
 * ```
 * ./gradlew :core:content:test --tests '*ModeNetsV54Training*' -PtrainNets54=1
 * ```
 *
 * Neden: v54'te çizim modları için 144 yeni şablon eklendi ([v54AnimalTemplates] vd.; katalog sürümü 9). Ağlar v38
 * ağlarından başlar (test kaynaklarında `/shapenet-v38/`), yeni sınıflar el çizimi benzetimleriyle öğrenilir.
 *
 * Aşırı öğrenmeye karşı (v38 → v54):
 * - Veri her [REGEN] turda yeni tohumla yeniden üretilir (sabit set yerine sürekli yeni benzetimler).
 * - Gizli katmanda [DROPOUT] oranında bırakma (yalnız eğitimde; çıkarımda yok, ölçek korunur).
 * - Ayrı doğrulama seti (farklı tohum) ile erken durdurma: [PATIENCE] tur iyileşme yoksa durur, en iyi ağırlıklar alınır.
 * - Etiket yumuşatma [SMOOTH], L2 [L2]. Gizli katman 320 → [HIDDEN] (sınıf sayısı arttı; yeni birimler rastgele,
 *   çıkışları sıfır), sınıf başına [PER_CLASS] örnek, en çok [EPOCHS] tur, Adam (ısınma + kosinüs azalma).
 *
 * Rapor (`build/modenet-v54-report.txt`): ayrılmış sınama setinde (doğrulamadan da ayrı tohum) v38 ve v54 ağının
 * ilk-1/ilk-3 doğruluğu, eğitim/doğrulama farkı (aşırı öğrenme göstergesi) ve tam aramanın doğruluğu.
 */
class ModeNetsV54Training {
    private fun enabled(): Boolean = System.getProperty("trainNets54") == "1" || System.getenv("MODENET54_TRAIN") == "1"

    companion object {
        const val HIDDEN = 384
        const val PER_CLASS = 72
        const val HELD = 6
        const val VAL = 3
        const val EPOCHS = 22
        const val PATIENCE = 4
        const val REGEN = 3
        const val BATCH = 96
        const val LR_OUT = 1.5e-3f
        const val LR_HIDDEN = 5e-4f
        const val SMOOTH = 0.08f
        const val L2 = 3e-5f
        const val DROPOUT = 0.15f
        val THREADS = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

        fun v37(mode: ShapeMode): ShapeNet =
            ShapeNet.decode(ModeNetsV54Training::class.java.getResourceAsStream("/shapenet-v38/mode_${mode.name.lowercase()}.bin")!!.readBytes())!!

        /** Modun ağ sınıfları (cihazdaki [ShapeModeModels.keys] sırasıyla). */
        fun keysOf(mode: ShapeMode): List<String> = ShapeTemplates.catalog.filter { ModeCatalog.allows(mode, it.key, it.category) }.map { it.key }

        /** Çizgi roman karakterlerinin poz/ifade biçimleri: eğitim (her pozda iki ifade) ve sınama (başka ifade). */
        fun characterForms(held: Boolean): Map<String, List<List<List<P>>>> {
            val exprs = com.karapp.drawguidance.content.comic.CharExpression.entries
            val poses = com.karapp.drawguidance.content.comic.CharPose.entries
            return com.karapp.drawguidance.content.comic.CharKind.entries.associate { k ->
                LibraryTemplates.CHARACTER_PREFIX + k.id to if (!held) poses.flatMap { p ->
                    listOf(exprs[0], exprs[1 + p.ordinal % (exprs.size - 1)]).distinct().map { e -> LibraryLineArt.character(k, p, e) }
                } else poses.map { p -> LibraryLineArt.character(k, p, exprs[1 + (p.ordinal + exprs.size / 2) % (exprs.size - 1)]) }
            }
        }

        /**
         * Sınıfın [n] örneği (4+ eleman): çok çizgili şablonlarda üçte biri yarım çizim, az çizgililerde parçalara bölünmüş;
         * güçlü bozulmalar her üç örnekte bir.
         */
        fun samples(t: ShapeTemplate, forms: List<List<List<P>>>?, n: Int, rnd: Random): List<List<List<P>>> = List(n) { i ->
            val src = if (forms != null && i % 3 != 0) forms[rnd.nextInt(forms.size)] else t.strokes
            ShapeAugment.manyElements(src, rnd, part = src.size > SuggestRules.SHAPE_MIN && i % 3 == 1,
                amount = 0.6f + rnd.nextFloat() * 0.9f, strong = i % 3 == 2)
        }
    }

    // ---------------------------------------------------------------- float ağ

    private class Net(override val keys: List<String>, val inDim: Int, val hidden: Int) : ShapeClassifier {
        val w1 = FloatArray(hidden * inDim); val b1 = FloatArray(hidden)
        val w2 = FloatArray(keys.size * hidden); val b2 = FloatArray(keys.size)
        private val index = keys.withIndex().associate { it.value to it.index }
        override fun indexOf(key: String): Int = index[key] ?: -1
        override fun probs(x: FloatArray): FloatArray {
            val h = FloatArray(hidden)
            for (j in 0 until hidden) { var s = b1[j]; val o = j * inDim; for (i in 0 until inDim) s += w1[o + i] * x[i]; h[j] = if (s > 0f) s else 0f }
            val z = FloatArray(keys.size)
            var mx = Float.NEGATIVE_INFINITY
            for (k in keys.indices) { var s = b2[k]; val o = k * hidden; for (j in 0 until hidden) s += w2[o + j] * h[j]; z[k] = s; if (s > mx) mx = s }
            var sum = 0f
            for (k in z.indices) { z[k] = exp(z[k] - mx); sum += z[k] }
            for (k in z.indices) z[k] /= sum
            return z
        }
        fun params() = listOf(w1, b1, w2, b2)
    }

    /** v37 ağından: gizli katman ve bilinen sınıfların satırları; yeni gizli birimler rastgele, çıkış ağırlıkları sıfır. */
    private fun init(keys: List<String>, base: ShapeNet, rnd: Random): Net {
        val net = Net(keys, ShapeNet.IN_DIM, HIDDEN)
        val (bw1, bb1) = base.hiddenLayer()
        val bh = base.hidden
        for (j in 0 until HIDDEN) {
            if (j < bh) { bw1.copyInto(net.w1, j * net.inDim, j * net.inDim, (j + 1) * net.inDim); net.b1[j] = bb1[j] }
            else for (i in 0 until net.inDim) net.w1[j * net.inDim + i] = (rnd.nextGaussian() * sqrt(2.0 / net.inDim)).toFloat()
        }
        var known = 0
        for ((k, key) in keys.withIndex()) {
            val bi = base.indexOf(key)
            if (bi >= 0) { base.outputRow(bi).copyInto(net.w2, k * HIDDEN); net.b2[k] = base.outputBias(bi); known++ }
            else { for (j in 0 until HIDDEN) net.w2[k * HIDDEN + j] = (rnd.nextGaussian() * 0.01).toFloat(); net.b2[k] = -2f }
        }
        println("${keys.size} sınıf, v38 ağında bilinen $known")
        return net
    }

    private class Grad(n: Net) {
        val g = n.params().map { FloatArray(it.size) }
        var loss = 0.0; var count = 0
        fun zero() { g.forEach { it.fill(0f) }; loss = 0.0; count = 0 }
    }

    private fun step(n: Net, gr: Grad, x: FloatArray, target: Int, rnd: Random) {
        val hd = n.hidden; val ind = n.inDim; val k = n.keys.size
        val h = FloatArray(hd)
        // Bırakma (inverted dropout): eğitimde birimlerin DROPOUT kadarı kapanır, kalanlar 1/(1-p) ile ölçeklenir
        val keep = 1f - DROPOUT
        for (j in 0 until hd) {
            var s = n.b1[j]; val o = j * ind; for (i in 0 until ind) s += n.w1[o + i] * x[i]
            h[j] = if (s > 0f && rnd.nextFloat() < keep) s / keep else 0f
        }
        val z = FloatArray(k)
        var mx = Float.NEGATIVE_INFINITY
        for (c in 0 until k) { var s = n.b2[c]; val o = c * hd; for (j in 0 until hd) s += n.w2[o + j] * h[j]; z[c] = s; if (s > mx) mx = s }
        var sum = 0f
        for (c in 0 until k) { z[c] = exp(z[c] - mx); sum += z[c] }
        gr.loss += -ln((z[target] / sum).toDouble().coerceAtLeast(1e-9))
        gr.count++
        val gw1 = gr.g[0]; val gb1 = gr.g[1]; val gw2 = gr.g[2]; val gb2 = gr.g[3]
        val dh = FloatArray(hd)
        val off = SMOOTH / k
        for (c in 0 until k) {
            val d = z[c] / sum - (if (c == target) 1f - SMOOTH + off else off)
            gb2[c] += d
            val o = c * hd
            for (j in 0 until hd) { if (h[j] != 0f) gw2[o + j] += d * h[j]; dh[j] += d * n.w2[o + j] }
        }
        for (j in 0 until hd) {
            if (h[j] <= 0f) continue
            val d = dh[j] / keep
            gb1[j] += d
            val o = j * ind
            for (i in 0 until ind) gw1[o + i] += d * x[i]
        }
    }

    private class Adam(val params: List<FloatArray>) {
        val m = params.map { FloatArray(it.size) }; val v = params.map { FloatArray(it.size) }
        var t = 0
        fun update(grads: List<FloatArray>, lrs: FloatArray, scale: Float, l2: Float) {
            t++
            val b1 = 0.9f; val b2 = 0.999f
            val c1 = 1f - Math.pow(b1.toDouble(), t.toDouble()).toFloat(); val c2 = 1f - Math.pow(b2.toDouble(), t.toDouble()).toFloat()
            for (k in params.indices) {
                val p = params[k]; val g = grads[k]; val mm = m[k]; val vv = v[k]; val lr = lrs[k]
                for (i in p.indices) {
                    val gi = g[i] * scale + l2 * p[i]
                    mm[i] = b1 * mm[i] + (1 - b1) * gi
                    vv[i] = b2 * vv[i] + (1 - b2) * gi * gi
                    p[i] -= lr * (mm[i] / c1) / (sqrt(vv[i] / c2) + 1e-8f)
                }
            }
        }
    }

    // ---------------------------------------------------------------- ölçüm

    private class Acc(val top1: Int, val top3: Int, val n: Int) {
        override fun toString() = "ilk-1 %.1f%% ilk-3 %.1f%% (%d)".format(100f * top1 / n.coerceAtLeast(1), 100f * top3 / n.coerceAtLeast(1), n)
    }

    private fun netAcc(c: ShapeClassifier, keys: List<String>, set: List<Pair<Int, FloatArray>>): Acc {
        var t1 = 0; var t3 = 0
        for ((k, x) in set) {
            val p = c.probs(x)
            val ki = c.indexOf(keys[k])
            if (ki < 0) continue
            val o = p.indices.sortedByDescending { p[it] }
            if (o[0] == ki) t1++
            if (ki in o.take(3)) t3++
        }
        return Acc(t1, t3, set.size)
    }

    private fun searchAcc(index: ShapeIndex, mode: ShapeMode, keys: List<String>, net: ShapeClassifier, queries: List<Pair<Int, List<List<P>>>>): Acc {
        val scope = ShapeScope(mode, keys.toSet(), net, AdvancedWeights.net, emptyMap(), emptyMap())
        var t1 = 0; var t3 = 0
        for ((k, q) in queries) {
            val res = index.search(q, MatchMode.GROUP, 3, scope = scope)
            val r = res.indexOfFirst { it.key == keys[k] }
            if (r == 0) t1++
            if (r in 0..2) t3++
        }
        return Acc(t1, t3, queries.size)
    }

    private fun meanTop(c: ShapeClassifier, set: List<Pair<Int, FloatArray>>) = set.sumOf { c.probs(it.second).max().toDouble() } / set.size.coerceAtLeast(1)

    // ---------------------------------------------------------------- eğitim

    @Test
    fun retrain() {
        Assume.assumeTrue(enabled())
        val only = System.getenv("MODENET54_MODES")?.split(',')?.map { ShapeMode.valueOf(it.trim()) } ?: ShapeMode.entries
        val log = StringBuilder()
        val index = ShapeIndex.fromCatalog(ShapeTemplates.catalog)
        val forms = characterForms(held = false)
        val heldForms = characterForms(held = true)
        for (mode in only) {
            val t0 = System.currentTimeMillis()
            val keys = keysOf(mode)
            val byKey = ShapeTemplates.catalog.associateBy { it.key }
            val pool = Executors.newFixedThreadPool(THREADS)
            fun say(s: String) { println(s); log.appendLine(s); System.out.flush() }
            /** Sınıf başına [n] örnek, [seed] tohumla (iş parçacıklarında). */
            fun build(seed: Long, n: Int, f: Map<String, List<List<List<P>>>>): MutableList<Pair<Int, FloatArray>> {
                val chunks = keys.indices.chunked((keys.size + THREADS - 1) / THREADS)
                return chunks.map { ch ->
                    pool.submit(Callable {
                        val out = ArrayList<Pair<Int, FloatArray>>()
                        for (k in ch) {
                            val rnd = Random(seed + mode.ordinal * 100_000L + k)
                            samples(byKey.getValue(keys[k]), f[keys[k]], n, rnd).forEach { s -> ShapeNet.inputOf(s)?.let { out += k to it } }
                        }
                        out
                    })
                }.flatMap { it.get() }.toMutableList()
            }
            var data = build(540_000L, PER_CLASS, forms)
            // Doğrulama (erken durdurma) ve sınama (rapor) ayrı tohumlarla; sınamada karakterlerin görülmemiş poz/ifadeleri
            val valid = build(541_000L, VAL, heldForms)
            val heldDrawn = keys.indices.flatMap { k ->
                val rnd = Random(77_000L + mode.ordinal * 10_000L + k)
                samples(byKey.getValue(keys[k]), heldForms[keys[k]], HELD, rnd).map { k to it }
            }
            val held = heldDrawn.mapNotNull { (k, s) -> ShapeNet.inputOf(s)?.let { k to it } }
            val full = keys.indices.flatMap { k ->
                val rnd = Random(78_000L + k)
                List(3) { ShapeNet.inputOf(ShapeAugment.handDrawn(byKey.getValue(keys[k]).strokes, rnd))?.let { k to it } }.filterNotNull()
            }
            val before = v37(mode)
            say("$mode: ${keys.size} sınıf (v38: ${before.keys.size}), eğitim ${data.size}, doğrulama ${valid.size}, sınama ${held.size}")
            say("  önce (v38 ağı, gizli ${before.hidden}): sınama ${netAcc(before, keys, held)}")

            val rnd = Random(5400L + mode.ordinal)
            val net = init(keys, before, rnd)
            val adam = Adam(net.params())
            val grads = List(THREADS) { Grad(net) }
            val rnds = List(THREADS) { Random(9_000L + it) }
            val lrBase = floatArrayOf(LR_HIDDEN, LR_HIDDEN, LR_OUT, LR_OUT)
            val steps = EPOCHS * ((data.size + BATCH - 1) / BATCH)
            var step = 0
            var best = -1.0; var bestEpoch = 0
            var bestParams = net.params().map { it.copyOf() }
            for (e in 0 until EPOCHS) {
                if (e > 0 && e % REGEN == 0) data = build(540_000L + e * 1_000L, PER_CLASS, forms)
                data.shuffle(rnd)
                var loss = 0.0; var cnt = 0
                var b = 0
                while (b < data.size) {
                    val end = minOf(b + BATCH, data.size)
                    val futures = grads.mapIndexed { wi, gr ->
                        pool.submit(Callable { gr.zero(); var i = b + wi; while (i < end) { step(net, gr, data[i].second, data[i].first, rnds[wi]); i += THREADS } })
                    }
                    futures.forEach { it.get() }
                    val g0 = grads[0].g
                    for (gr in grads.drop(1)) for (k in g0.indices) { val a = g0[k]; val o = gr.g[k]; for (i in a.indices) a[i] += o[i] }
                    val n = grads.sumOf { it.count }
                    val warm = minOf(1f, (step + 1) / 200f)
                    val decay = 0.05f + 0.95f * 0.5f * (1f + cos(Math.PI * step / steps).toFloat())
                    adam.update(g0, FloatArray(4) { lrBase[it] * warm * decay }, 1f / n, L2)
                    loss += grads.sumOf { it.loss }; cnt += n
                    step++
                    b = end
                }
                val va = netAcc(net, keys, valid)
                val score = va.top1 + 0.5 * va.top3
                val trainAcc = netAcc(net, keys, data.subList(0, minOf(data.size, valid.size)))
                say("  tur ${e + 1}/$EPOCHS: kayıp " + "%.4f".format(loss / cnt) + ", eğitim $trainAcc, doğrulama $va (${(System.currentTimeMillis() - t0) / 1000} sn)")
                if (score > best) { best = score; bestEpoch = e; bestParams = net.params().map { it.copyOf() } }
                else if (e - bestEpoch >= PATIENCE) { say("  erken durdurma: en iyi tur ${bestEpoch + 1}"); break }
            }
            pool.shutdown()
            net.params().forEachIndexed { i, p -> bestParams[i].copyInto(p) }
            // Sıcaklık: ortalama en yüksek olasılık (tam çizimlerde) v38 ağınınki kadar (aramadaki göreli ağırlık değişmesin)
            val target = meanTop(before, full)
            val raw = meanTop(net, full)
            val w2 = net.w2.copyOf(); val b2 = net.b2.copyOf()
            fun scaleTo(s: Float) { for (i in w2.indices) net.w2[i] = w2[i] * s; for (i in b2.indices) net.b2[i] = b2[i] * s }
            var temp = 1f
            if (raw > target) {
                var lo = 0.01f; var hi = 1f
                repeat(24) { val mid = (lo + hi) / 2; scaleTo(mid); if (meanTop(net, full) > target) hi = mid else lo = mid }
                temp = (lo + hi) / 2
                scaleTo(temp)
            }
            val q = ShapeNet.quantize(net.keys, net.inDim, net.hidden, net.w1, net.b1, net.w2, net.b2)
            val file = File("src/main/resources/shapenet/mode_${mode.name.lowercase()}.bin")
            file.writeBytes(q.encode())
            say("  sıcaklık %.3f (ortalama en yüksek olasılık %.3f → %.3f, v38 %.3f)".format(temp, raw, meanTop(q, full), target))
            say("  sonra (v54 int8, gizli $HIDDEN, ${file.length() / 1024} KB): sınama ${netAcc(q, keys, held)}")
            val queries = heldDrawn.filterIndexed { i, _ -> i % HELD < 2 }
            say("  tam arama (veritabanı + ağ, 4+ eleman): önce ${searchAcc(index, mode, keys, before, queries)}, sonra ${searchAcc(index, mode, keys, q, queries)} (${(System.currentTimeMillis() - t0) / 1000} sn)")
        }
        File("build").mkdirs()
        File("build/modenet-v54-report.txt").appendText(log.toString())
    }
}
