package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.ink.Poly
import com.karapp.drawguidance.content.vector.P
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/**
 * v27 küçük sinir ağı (int8): şekil veritabanındaki şablonların el çizimi benzetimleriyle bilgisayarda eğitilir
 * (bkz. testlerdeki ShapeNetTrainer), ağırlıkları uygulamanın içinde gelir (`/shapenet/shape_net.bin`, ~40 KB).
 * Girdi: havuzlanmış yön ızgarası (80) + en-boy + çizgi sayısı + kapalı çizgi oranı (83); gizli katman 96 ReLU; çıktı
 * şablon başına olasılık. Ağırlıklar satır başına ölçekli int8 (simetrik); çıkarım saf Kotlin, ~30 bin çarpma (mikro
 * saniyeler). Yeniden sıralamada aday ekler ve olasılığı düşük adaylara küçük bir ceza verir.
 */
class ShapeNet(
    override val keys: List<String>,
    val inDim: Int,
    val hidden: Int,
    private val w1: ByteArray, private val s1: FloatArray, private val b1: FloatArray,
    private val w2: ByteArray, private val s2: FloatArray, private val b2: FloatArray,
) : ShapeClassifier {
    val outDim: Int get() = keys.size
    private val keyIndex = keys.withIndex().associate { it.value to it.index }

    override fun indexOf(key: String): Int = keyIndex[key] ?: -1

    /** v29 gizli katmanın float ağırlıkları ve eğilimleri (moda özel ağın başlangıcı). */
    fun hiddenLayer(): Pair<FloatArray, FloatArray> =
        FloatArray(hidden * inDim) { w1[it] * s1[it / inDim] } to b1.copyOf()

    /** v29 [k] çıktısının float ağırlık satırı ve eğilimi. */
    fun outputRow(k: Int): FloatArray = FloatArray(hidden) { w2[k * hidden + it] * s2[k] }
    fun outputBias(k: Int): Float = b2[k]

    /** Olasılıklar (şablon sırasıyla). */
    override fun probs(x: FloatArray): FloatArray {
        val h = FloatArray(hidden)
        for (j in 0 until hidden) {
            val o = j * inDim
            // int8 ağırlık × float girdi: satır ölçeği sonda
            var acc = 0f
            for (i in 0 until inDim) acc += w1[o + i] * x[i]
            val v = acc * s1[j] + b1[j]
            h[j] = if (v > 0f) v else 0f
        }
        val out = FloatArray(outDim)
        var mx = Float.NEGATIVE_INFINITY
        for (k in 0 until outDim) {
            var acc = 0f
            val o = k * hidden
            for (j in 0 until hidden) acc += w2[o + j] * h[j]
            val v = acc * s2[k] + b2[k]
            out[k] = v
            if (v > mx) mx = v
        }
        var sum = 0f
        for (k in 0 until outDim) { out[k] = exp(out[k] - mx); sum += out[k] }
        for (k in 0 until outDim) out[k] /= sum
        return out
    }

    fun encode(): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { d ->
            d.writeInt(MAGIC); d.writeInt(1)
            d.writeInt(inDim); d.writeInt(hidden); d.writeInt(keys.size)
            keys.forEach { d.writeUTF(it) }
            d.write(w1); s1.forEach(d::writeFloat); b1.forEach(d::writeFloat)
            d.write(w2); s2.forEach(d::writeFloat); b2.forEach(d::writeFloat)
        }
        return bos.toByteArray()
    }

    companion object {
        const val RESOURCE = "/shapenet/shape_net.bin"
        const val IN_DIM = 83
        private const val MAGIC = 0x53484E54 // "SHNT"

        /**
         * v30 moda özel, uygulamayla gelen ağlar (`/shapenet/mode_<mod>.bin`): Quick, Draw! veri kümesinin (CC BY 4.0)
         * modun şablonlarıyla eşleşen el çizimleri ve şablon benzetimleriyle bilgisayarda eğitildi. Değişince
         * [MODE_NET_VERSION] artar (cihazdaki öğrenilenler yeni ağın üstüne yeniden çalışılır).
         *
         * v37: sürüm moda göredir ([modeNetVersion]); bu sabit ilk sürümdür (çocuk modu hâlâ bu sürümde).
         */
        const val MODE_NET_VERSION = 1

        /**
         * v37 modun uygulamayla gelen ağının sürümü. ART ve COMIC (37): v36 nesneleri, karakterleri ve efektleri eklenerek
         * yeniden eğitildi; NOTES (37): yalnız şekil düzeltme için yeniden eğitildi (yazı düzeltmesi ayrı ağda,
         * [com.karapp.drawguidance.content.ink.HandwritingNet]); KIDS değişmedi (1).
         *
         * v38 bütün modlar (KIDS dahil) 38: şekil önerisi yalnız birbirine yakın 4+ elemanda yapıldığı için ağlar 4+
         * elemanlı el çizimi benzetimleriyle (eksik çizimler ve parçalara bölünmüş az çizgili şablonlar dahil) yeniden
         * eğitildi; gizli katman 256 → 320 (bkz. testlerdeki ModeNetsV38Training).
         */
        fun modeNetVersion(mode: ShapeMode): Int = when (mode) {
            ShapeMode.ART, ShapeMode.COMIC, ShapeMode.NOTES, ShapeMode.KIDS -> V38
        }

        /** v38 ağlarının sürümü. */
        const val V38 = 38

        /**
         * v37 bu sürümden eski temel ağın üstünde cihazda öğrenilenler yeni ağa taşınmaz, silinir (kabul/ret sayaçları,
         * tekrar tamponu): eski not alma ağının verisi yeni şekil ağında yeniden çalışılmaz. 0: hiç silinmez.
         */
        fun resetBelow(mode: ShapeMode): Int = if (mode == ShapeMode.NOTES) 37 else 0

        private val modeNets = java.util.concurrent.ConcurrentHashMap<ShapeMode, Optional>()
        private class Optional(val net: ShapeNet?)

        /** v33 sunucudan gelen (sürümü uygulamayla gelenden yeni) temel ağlar: sürüm → ağ. */
        private val remoteNets = java.util.concurrent.ConcurrentHashMap<ShapeMode, Pair<Int, ShapeNet>>()

        /**
         * v33 sunucunun temel ağını kurar (bozuksa false). v37: yalnız uygulamayla gelenden yeni sürüm kurulur; aynı ya da
         * eski sürüm gelirse sunucu ağı kaldırılır ve uygulamayla gelen kullanılır (true döner).
         */
        fun installRemote(mode: ShapeMode, version: Int, bytes: ByteArray): Boolean {
            if (version <= modeNetVersion(mode)) { remoteNets.remove(mode); return true }
            val net = decode(bytes) ?: return false
            remoteNets[mode] = version to net
            return true
        }

        /** Modun temel ağı ve sürümü: sunucudan gelen varsa o (öncelikli), yoksa uygulamayla gelen. */
        fun baseFor(mode: ShapeMode): Pair<ShapeNet?, Int> {
            remoteNets[mode]?.let { return it.second to it.first }
            val bundledNet = bundledFor(mode)
            return bundledNet to (if (bundledNet != null) modeNetVersion(mode) else 0)
        }

        fun bundledFor(mode: ShapeMode): ShapeNet? = modeNets.getOrPut(mode) {
            Optional(runCatching {
                ShapeNet::class.java.getResourceAsStream("/shapenet/mode_${mode.name.lowercase()}.bin")?.use { decode(it.readBytes()) }
            }.getOrNull())
        }.net

        /** Uygulamayla gelen ağ (yoksa ya da bozuksa null: arama ağsız çalışır). */
        val bundled: ShapeNet? by lazy {
            runCatching { ShapeNet::class.java.getResourceAsStream(RESOURCE)?.use { decode(it.readBytes()) } }.getOrNull()
        }

        fun decode(bytes: ByteArray): ShapeNet? = runCatching {
            DataInputStream(bytes.inputStream()).use { d ->
                if (d.readInt() != MAGIC || d.readInt() != 1) return null
                val inDim = d.readInt(); val hidden = d.readInt(); val k = d.readInt()
                val keys = List(k) { d.readUTF() }
                val w1 = ByteArray(inDim * hidden).also { d.readFully(it) }
                val s1 = FloatArray(hidden) { d.readFloat() }; val b1 = FloatArray(hidden) { d.readFloat() }
                val w2 = ByteArray(hidden * k).also { d.readFully(it) }
                val s2 = FloatArray(k) { d.readFloat() }; val b2 = FloatArray(k) { d.readFloat() }
                ShapeNet(keys, inDim, hidden, w1, s1, b1, w2, s2, b2)
            }
        }.getOrNull()

        /** Float ağırlıklardan satır başına ölçekli int8 ağ. */
        fun quantize(keys: List<String>, inDim: Int, hidden: Int, w1: FloatArray, b1: FloatArray, w2: FloatArray, b2: FloatArray): ShapeNet {
            fun q(w: FloatArray, rows: Int, cols: Int): Pair<ByteArray, FloatArray> {
                val out = ByteArray(rows * cols); val scale = FloatArray(rows)
                for (r in 0 until rows) {
                    var m = 1e-12f
                    for (c in 0 until cols) m = max(m, kotlin.math.abs(w[r * cols + c]))
                    val s = m / 127f
                    scale[r] = s
                    for (c in 0 until cols) out[r * cols + c] = Math.round(w[r * cols + c] / s).coerceIn(-127, 127).toByte()
                }
                return out to scale
            }
            val (qw1, qs1) = q(w1, hidden, inDim)
            val (qw2, qs2) = q(w2, keys.size, hidden)
            return ShapeNet(keys, inDim, hidden, qw1, qs1, b1, qw2, qs2, b2)
        }

        /** Ağın girdisi: [polys] birim kutuda (küçük parçalar nokta), [coarse] havuzlanmış öznitelik, [aspect]. */
        internal fun input(polys: List<Poly>, coarse: FloatArray, aspect: Float): FloatArray {
            val x = FloatArray(IN_DIM)
            coarse.copyInto(x, 0, 0, minOf(coarse.size, 80))
            x[80] = aspect
            x[81] = (ln(polys.size.coerceAtLeast(1).toFloat()) / ln(12f)).coerceIn(0f, 1.2f)
            x[82] = if (polys.isEmpty()) 0f else polys.count { ShapeSignatures.isClosed(it) } / polys.size.toFloat()
            return x
        }

        /** Girdiyi ham çizgilerden (eğitim ve testler için; arama kendi ara değerlerini kullanır). */
        fun inputOf(strokes: List<List<P>>): FloatArray? {
            val clean = strokes.filter { it.isNotEmpty() }
            if (clean.isEmpty()) return null
            val norm = ShapeSignatures.normalize(clean)
            val raw = ShapeSignatures.polys(norm)
            val polys = ShapeSignatures.collapseSmall(raw)
            val f = com.karapp.drawguidance.content.ink.ShapeFeatures.extract(polys)
            return input(polys, ShapeSignatures.pool(f), com.karapp.drawguidance.content.ink.ShapeFeatures.aspect(raw))
        }
    }
}

/**
 * v27 el çizimi benzetimi (ağın eğitimi ve doğruluk testleri): rastgele boyut, yer, döndürme, esneme, dalgalanma, titreme,
 * kapalı çizgide başlangıç kayması ve boşluk, karışık yön ve sıra; [strong] ile daha uzak çizimler (parça kayması ve
 * çok parçalılarda bazen bir parça eksik).
 */
object ShapeAugment {
    /**
     * v38 4+ elemanlı çizim: [strokes] [min] elemandan azsa en uzun çizgiler rastgele bir noktadan (uzunluğun %30–70'i)
     * ikiye bölünür — kullanıcı bir çemberi iki yayla, bir kalbi dört parçayla çizebilir. Bölünen yerde bazen küçük
     * boşluk kalır. Çizgiler zaten yeterliyse aynen döner.
     */
    fun atLeast(strokes: List<List<P>>, min: Int, rnd: java.util.Random): List<List<P>> {
        val out = strokes.filter { it.isNotEmpty() }.toMutableList()
        var guard = 0
        while (out.size < min && guard++ < 4 * min) {
            val i = out.indices.filter { out[it].size >= 2 }.maxByOrNull { lengthOf(out[it]) } ?: break
            val s = out[i]
            val len = lengthOf(s)
            if (len <= 0f) break
            val dense = if (s.size >= 12) s else TemplateFit.densify(s, len / 24f)
            if (dense.size < 4) break
            val cut = ((0.3f + 0.4f * rnd.nextFloat()) * (dense.size - 1)).toInt().coerceIn(1, dense.size - 2)
            val gap = if (rnd.nextFloat() < 0.35f && dense.size > 12) 1 else 0
            out[i] = dense.subList(0, cut + 1).toList()
            out.add(i + 1, dense.subList(cut + gap, dense.size).toList())
        }
        return out
    }

    private fun lengthOf(s: List<P>): Float { var l = 0f; for (i in 1 until s.size) l += hypot(s[i].x - s[i - 1].x, s[i].y - s[i - 1].y); return l }

    /**
     * v38 şekil ağının eğitim/sınama örneği: şablonun el çizimi benzetimi, en az [min] elemanlı. [part] ise çizim
     * yarımdır (çizim sırasıyla ilk 4+ çizgi; kullanıcı çizerken öneri 4. elemandan sonra gelir). Az çizgili şablonlar
     * parçalara bölünür; çok çizgililerde bazen bir iki çizgi daha bölünür.
     */
    fun manyElements(strokes: List<List<P>>, rnd: java.util.Random, min: Int = SuggestRules.SHAPE_MIN, part: Boolean = false, amount: Float = 1f, strong: Boolean = false): List<List<P>> {
        var src = strokes
        if (part && src.size > min) src = src.take(min + rnd.nextInt(src.size - min))
        val target = if (src.size < min) min + rnd.nextInt(3) else src.size + (if (rnd.nextFloat() < 0.2f) 1 + rnd.nextInt(2) else 0)
        val drawn = handDrawn(atLeast(src, target, rnd), rnd, amount = amount, strong = strong)
        return if (drawn.size >= min) drawn else atLeast(drawn, min, rnd)
    }

    fun handDrawn(strokes: List<List<P>>, rnd: java.util.Random, amount: Float = 1f, strong: Boolean = false): List<List<P>> {
        val size = 200f + rnd.nextFloat() * 200f
        val ox = 100f + rnd.nextFloat() * 300f; val oy = 100f + rnd.nextFloat() * 300f
        val rot = ((rnd.nextFloat() - 0.5f) * (if (strong) 22f else 10f) * amount) * PI.toFloat() / 180f
        val sx = 1f + (rnd.nextFloat() - 0.5f) * (if (strong) 0.5f else 0.24f) * amount
        val c = cos(rot); val s = sin(rot)
        fun map(p: P, dx: Float, dy: Float): P {
            val x = (p.x - 0.5f + dx) * size * sx; val y = (p.y - 0.5f + dy) * size
            return P(ox + c * x - s * y, oy + s * x + c * y)
        }
        var src = strokes
        if (strong && strokes.size >= 3 && rnd.nextFloat() < 0.4f) src = strokes.filterIndexed { i, _ -> i != rnd.nextInt(strokes.size) }
        val out = src.map { st ->
            val ext = maxOf(st.maxOf { it.x } - st.minOf { it.x }, st.maxOf { it.y } - st.minOf { it.y })
            val dx = if (strong) (rnd.nextFloat() - 0.5f) * 0.06f else 0f
            val dy = if (strong) (rnd.nextFloat() - 0.5f) * 0.06f else 0f
            if (src.size > 1 && ext < 0.12f && rnd.nextBoolean()) {
                val cc = map(P(st.map { it.x }.average().toFloat(), st.map { it.y }.average().toFloat()), dx, dy)
                return@map listOf(cc, P(cc.x + 1.5f, cc.y + 1f), P(cc.x + 0.5f, cc.y + 2f))
            }
            var pts = TemplateFit.densify(st.map { map(it, dx, dy) }, 3f)
            val closed = st.size > 3 && hypot(st.first().x - st.last().x, st.first().y - st.last().y) < 0.02f
            if (closed && pts.size > 8) {
                val k = rnd.nextInt(pts.size - 1)
                pts = pts.subList(k, pts.size - 1) + pts.subList(0, k + 1)
                val cut = (rnd.nextFloat() * 0.03f * pts.size).toInt()
                if (cut > 0 && rnd.nextBoolean()) pts = pts.dropLast(cut)
            }
            val f1 = rnd.nextFloat() * 3f + 1f; val f2 = rnd.nextFloat() * 7f + 3f
            val ph1 = rnd.nextFloat() * 6.28f; val ph2 = rnd.nextFloat() * 6.28f
            val amp = size * 0.012f * amount * (if (strong) 2f else 1f)
            var total = 0f
            val lens = FloatArray(pts.size)
            for (i in 1 until pts.size) { total += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y); lens[i] = total }
            val wob = pts.mapIndexed { i, p ->
                val t = if (total > 0f) lens[i] / total else 0f
                val nx = amp * sin(f1 * 6.28f * t + ph1) + amp * 0.5f * sin(f2 * 6.28f * t + ph2) + (rnd.nextFloat() - 0.5f) * 0.8f
                val ny = amp * cos(f1 * 6.28f * t + ph2) + amp * 0.5f * cos(f2 * 6.28f * t + ph1) + (rnd.nextFloat() - 0.5f) * 0.8f
                P(p.x + nx, p.y + ny)
            }
            if (rnd.nextBoolean()) wob.reversed() else wob
        }
        return out.shuffled(rnd)
    }
}
