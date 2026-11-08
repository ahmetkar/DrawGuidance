package com.karapp.drawguidance.content.ink

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.max

/**
 * v37 not alma modunun yazı düzeltme ağı (şekil düzeltme ağından ayrı; bkz. ShapeNet NOTES): tek karakterlik çizgi
 * grubunu sınıflandıran küçük int8 sinir ağı. Saf Kotlin çıkarım (ölçüm için bkz. HandwritingNetTest).
 *
 * Yapı: girdi [TextFeatures] (616) → [h1] ReLU → [h2] ReLU (ortak gövde) → adlı çıktı katmanları (başlar):
 * - [SCRIPT]: yazı sistemi ([InkScript] sırasıyla; kendiliğinden bulma),
 * - [InkScript.name] adlı başlar (LATIN, CYRILLIC, ARABIC, JAPANESE): o yazı sisteminin karakter sınıfları,
 * - Korece üç baş ([KOREAN_L], [KOREAN_V], [KOREAN_T]): hece bloğunun ilk ünsüzü, ünlüsü ve son ünsüzü ayrı ayrı
 *   sınıflandırılır (bileşenlere ayrılmış tanıma: 11 172 hecenin hepsi 19 + 21 + 27 çıktıyla kapsanır; tek başına
 *   yazılan jamo ilk ünsüz ya da ünlü + "yok" olarak gösterilir).
 *
 * Her sınıfın çıktıları ([Head.outputs]) biçimi aynı karakterlerdir (ör. Kiril "к/К", Japonca "ロ/口"); ilk çıktı
 * varsayılandır, seçimi çözücü bağlama göre yapar. LATIN başının çıktıları karakter değil, mevcut çözücünün gövde
 * şekli etiketleridir ([Glyphs.sketches]: "O" = o/O/0, "|" = l/I/1 ...). Çıktısı boş sınıf "karakter değil"dir (iki
 * karakterin birleşimi ya da yarım karakter: bölütlemede yanlış grubu cezalandırır). Korece başlarda [NONE] "yok"tur.
 *
 * Ağırlıklar satır başına ölçekli int8 (simetrik); eğitim bilgisayarda yazı tiplerinin iskeletlerinden üretilen yapay el
 * yazısıyla yapılır (testlerdeki TextNetTraining). Dosya: [RESOURCE]; sunucu kataloğunda [CATALOG_MODE] adıyla ve
 * [FORMAT] biçimiyle taşınır, sürümü [VERSION].
 */
class HandwritingNet internal constructor(
    val inDim: Int,
    val h1: Int,
    val h2: Int,
    private val w1: ByteArray, private val s1: FloatArray, private val b1: FloatArray,
    private val w2: ByteArray, private val s2: FloatArray, private val b2: FloatArray,
    /** Adlı başlar (yazılış sırasıyla). */
    internal val heads: Map<String, Head>,
) {
    /** Çıktı katmanı: sınıfların çıktı karakterleri ve int8 ağırlıkları. */
    class Head internal constructor(
        val outputs: List<List<String>>,
        internal val w: ByteArray, internal val s: FloatArray, internal val b: FloatArray,
    ) {
        val size: Int get() = outputs.size
        /** "Karakter değil" sınıfı (yoksa -1). */
        val junk: Int = outputs.indexOfFirst { it.isEmpty() }
        private val index: Map<String, Int> by lazy {
            val m = HashMap<String, Int>()
            outputs.forEachIndexed { i, o -> o.forEach { m.putIfAbsent(it, i) } }
            m
        }
        /** Bu karakteri (ya da etiketi) çıktı veren sınıf (yoksa -1). */
        fun classOf(text: String): Int = index[text] ?: -1
    }

    /** Toplam parametre sayısı (rapor). */
    val parameters: Int get() = w1.size + w2.size + heads.values.sumOf { it.w.size } + h1 + h2 + heads.values.sumOf { it.size }

    /** Ortak gövdenin çıktısı (ikinci gizli katman). */
    fun hidden(x: FloatArray): FloatArray {
        val a = layer(x, inDim, h1, w1, s1, b1, relu = true)
        return layer(a, h1, h2, w2, s2, b2, relu = true)
    }

    fun head(name: String): Head? = heads[name]

    /** [name] başının olasılıkları (baş yoksa null). */
    fun probs(h: FloatArray, name: String): FloatArray? {
        val hd = heads[name] ?: return null
        return softmax(layer(h, h2, hd.size, hd.w, hd.s, hd.b, relu = false))
    }

    /** Yazı sistemi olasılıkları ([InkScript.entries] sırasıyla). */
    fun scriptProbs(h: FloatArray): FloatArray = probs(h, SCRIPT) ?: FloatArray(InkScript.entries.size) { 1f / InkScript.entries.size }

    /** Çizgilerden doğrudan: [name] başının olasılıkları. */
    internal fun classify(strokes: List<Poly>, name: String): FloatArray? = probs(hidden(TextFeatures.extract(strokes)), name)

    /** [name] başının ham çıktıları (softmax öncesi; baş yoksa null). */
    internal fun logits(h: FloatArray, name: String): FloatArray? {
        val hd = heads[name] ?: return null
        return layer(h, h2, hd.size, hd.w, hd.s, hd.b, relu = false)
    }

    /** v38 "yazı mı" kapısı var mı (eski ağlarda yok). */
    val hasGate: Boolean get() = heads[WRITING] != null && heads[WRITING_MAX] != null

    /**
     * v38 "yazı mı" kapısı: birbirine yakın 2+ elemanın yazı olma olasılığı (kapı başı yoksa null). Elemanlar kabaca
     * karakterlere kümelenir ([Prep.clusters]); her kümenin ortak gövde çıktısı ([hidden]) alınır, ortalaması [WRITING]
     * başına, öğe başına en büyüğü [WRITING_MAX] başına, bütün grubun gövde çıktısı [WRITING_ALL] başına girer; başların
     * çıktıları toplanıp softmax'tan geçer.
     */
    internal fun writingProb(strokes: List<Poly>): Float? {
        if (!hasGate || strokes.isEmpty()) return null
        val g = gateInput(strokes) ?: return null
        val a = logits(g[0], WRITING) ?: return null
        val b = logits(g[1], WRITING_MAX) ?: return null
        val c = logits(g[2], WRITING_ALL)
        val z = FloatArray(a.size) { a[it] + b[it] + (c?.get(it) ?: 0f) }
        return softmax(z)[GATE_TEXT]
    }

    /**
     * Kapının girdisi: kümelerin gövde çıktılarının ortalaması, öğe başına en büyüğü ve bütün grubun gövde çıktısı
     * (eğitim de bunu kullanır).
     */
    internal fun gateInput(strokes: List<Poly>): List<FloatArray>? {
        val clusters = Prep.clusters(strokes)
        if (clusters.isEmpty()) return null
        val mean = FloatArray(h2); val mx = FloatArray(h2)
        for (c in clusters.take(GATE_MAX_CLUSTERS)) {
            val h = hidden(TextFeatures.extract(c.members.map { strokes[it] }))
            for (i in 0 until h2) { mean[i] += h[i]; if (h[i] > mx[i]) mx[i] = h[i] }
        }
        val n = minOf(clusters.size, GATE_MAX_CLUSTERS).toFloat()
        for (i in 0 until h2) mean[i] /= n
        return listOf(mean, mx, hidden(TextFeatures.extract(strokes)))
    }

    /** v38 kapı başlarını ([extra]) ekleyerek ([drop] adlıları çıkararak) yeni ağ (eğitim; diğer başlar aynı kalır). */
    internal fun withHeads(extra: Map<String, Head>, drop: Set<String> = emptySet()): HandwritingNet =
        HandwritingNet(inDim, h1, h2, w1, s1, b1, w2, s2, b2, LinkedHashMap(heads.filterKeys { it !in drop }).apply { putAll(extra) })

    fun encode(): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { d ->
            d.writeInt(MAGIC); d.writeInt(1)
            d.writeInt(inDim); d.writeInt(h1); d.writeInt(h2)
            d.writeInt(heads.size)
            for ((name, hd) in heads) {
                d.writeUTF(name); d.writeInt(hd.size)
                hd.outputs.forEach { o -> d.writeByte(o.size); o.forEach(d::writeUTF) }
            }
            d.write(w1); s1.forEach(d::writeFloat); b1.forEach(d::writeFloat)
            d.write(w2); s2.forEach(d::writeFloat); b2.forEach(d::writeFloat)
            for (hd in heads.values) { d.write(hd.w); hd.s.forEach(d::writeFloat); hd.b.forEach(d::writeFloat) }
        }
        return bos.toByteArray()
    }

    companion object {
        const val RESOURCE = "/shapenet/notes_text.bin"

        /**
         * Uygulamayla gelen yazı ağının sürümü (değişince artar; sunucudaki daha yeni sürüm öncelikli). v38: 2 ("yazı mı"
         * kapısı eklendi; karakter başları aynı).
         */
        const val VERSION = 2

        /** Katalogdaki adı (`CatalogModeNet.mode`): şekil modlarından ayrı, eski uygulamalar tanımaz ve yok sayar. */
        const val CATALOG_MODE = "NOTES_TEXT"

        /** Katalogdaki biçimi (`CatalogModeNet.format`). */
        const val FORMAT = "inknet-int8-v1"

        const val SCRIPT = "SCRIPT"
        const val KOREAN_L = "KOREAN_L"
        const val KOREAN_V = "KOREAN_V"
        const val KOREAN_T = "KOREAN_T"

        /**
         * v38 "yazı mı" kapısının başları (2 sınıf: [GATE_SHAPE] çizim/şekil, [GATE_TEXT] yazı). Eski uygulamalar bu adları
         * tanımaz ve yok sayar (biçim aynı: inknet-int8-v1).
         */
        const val WRITING = "WRITING"
        const val WRITING_MAX = "WRITING_MAX"
        const val WRITING_ALL = "WRITING_ALL"
        const val GATE_SHAPE = 0
        const val GATE_TEXT = 1
        /** Kapıya en çok bu kadar küme (karakter) girer. */
        const val GATE_MAX_CLUSTERS = 24

        /** Korece başlarda "bu bileşen yok" çıktısı. */
        const val NONE = "-"

        private const val MAGIC = 0x494E4B4E // "INKN"

        /** Uygulamayla gelen ağ (yoksa ya da bozuksa null: yazı tanıma ağsız, yalnız şablonlarla çalışır). */
        val bundled: HandwritingNet? by lazy {
            runCatching { HandwritingNet::class.java.getResourceAsStream(RESOURCE)?.use { decode(it.readBytes()) } }.getOrNull()
        }

        private val remote = AtomicReference<Pair<Int, HandwritingNet>?>(null)

        /**
         * Sunucudan inen yazı ağını kurar (bozuksa false). Yalnız uygulamayla gelenden yeni sürüm kurulur; aynı ya da eski
         * sürüm gelirse sunucu ağı kaldırılır (true döner, uygulamayla gelen kullanılır).
         */
        fun installRemote(version: Int, bytes: ByteArray): Boolean {
            if (version <= VERSION) { remote.set(null); return true }
            val net = decode(bytes) ?: return false
            remote.set(version to net)
            return true
        }

        /** Kullanılan ağ ve sürümü: sunucudan gelen (yeni) varsa o, yoksa uygulamayla gelen (ağ yoksa sürüm 0). */
        fun current(): Pair<HandwritingNet?, Int> {
            remote.get()?.let { return it.second to it.first }
            val b = bundled
            return b to (if (b != null) VERSION else 0)
        }

        /** Etkin ağ. */
        val active: HandwritingNet? get() = remote.get()?.second ?: bundled

        fun decode(bytes: ByteArray): HandwritingNet? = runCatching {
            DataInputStream(bytes.inputStream()).use { d ->
                if (d.readInt() != MAGIC || d.readInt() != 1) return null
                val inDim = d.readInt(); val h1 = d.readInt(); val h2 = d.readInt()
                if (inDim != TextFeatures.DIM || h1 !in 1..4096 || h2 !in 1..4096) return null
                val n = d.readInt()
                if (n !in 1..64) return null
                val meta = List(n) {
                    val name = d.readUTF()
                    val k = d.readInt()
                    if (k !in 1..20000) return null
                    name to List(k) { List(d.readUnsignedByte()) { d.readUTF() } }
                }
                val w1 = ByteArray(inDim * h1).also { d.readFully(it) }
                val s1 = FloatArray(h1) { d.readFloat() }; val b1 = FloatArray(h1) { d.readFloat() }
                val w2 = ByteArray(h1 * h2).also { d.readFully(it) }
                val s2 = FloatArray(h2) { d.readFloat() }; val b2 = FloatArray(h2) { d.readFloat() }
                val heads = LinkedHashMap<String, Head>()
                for ((name, outs) in meta) {
                    val k = outs.size
                    val w = ByteArray(k * h2).also { d.readFully(it) }
                    heads[name] = Head(outs, w, FloatArray(k) { d.readFloat() }, FloatArray(k) { d.readFloat() })
                }
                if (heads[SCRIPT]?.size != InkScript.entries.size) return null
                HandwritingNet(inDim, h1, h2, w1, s1, b1, w2, s2, b2, heads)
            }
        }.getOrNull()

        /** Float ağırlıklardan satır başına ölçekli int8 ağ (eğitim). Başların ağırlıkları satır = sınıf. */
        fun quantize(
            inDim: Int, h1: Int, h2: Int,
            w1: FloatArray, b1: FloatArray, w2: FloatArray, b2: FloatArray,
            heads: List<Triple<String, List<List<String>>, Pair<FloatArray, FloatArray>>>,
        ): HandwritingNet {
            val (qw1, qs1) = q(w1, h1, inDim)
            val (qw2, qs2) = q(w2, h2, h1)
            val out = LinkedHashMap<String, Head>()
            for ((name, outputs, wb) in heads) {
                val (qw, qs) = q(wb.first, outputs.size, h2)
                out[name] = Head(outputs, qw, qs, wb.second.copyOf())
            }
            return HandwritingNet(inDim, h1, h2, qw1, qs1, b1.copyOf(), qw2, qs2, b2.copyOf(), out)
        }

        /** v38 tek başın int8'e çevrilmesi (kapı eğitimi). */
        internal fun quantizeHead(outputs: List<List<String>>, w: FloatArray, b: FloatArray, cols: Int): Head {
            val (qw, qs) = q(w, outputs.size, cols)
            return Head(outputs, qw, qs, b.copyOf())
        }

        private fun q(w: FloatArray, rows: Int, cols: Int): Pair<ByteArray, FloatArray> {
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

        /** int8 ağırlık × float girdi; satır ölçeği sonda. */
        private fun layer(x: FloatArray, n: Int, m: Int, w: ByteArray, s: FloatArray, b: FloatArray, relu: Boolean): FloatArray {
            val out = FloatArray(m)
            // seyrek girdi (öznitelikler ve ReLU çıktısının çoğu sıfır): yalnız sıfır olmayan girdiler toplanır
            var nzCount = 0
            for (i in 0 until n) if (x[i] != 0f) nzCount++
            if (nzCount < n / 2) {
                val nz = IntArray(nzCount)
                val xv = FloatArray(nzCount)
                var c = 0
                for (i in 0 until n) if (x[i] != 0f) { nz[c] = i; xv[c] = x[i]; c++ }
                for (j in 0 until m) {
                    val o = j * n
                    var a0 = 0f; var a1 = 0f
                    var k = 0
                    while (k + 1 < nzCount) {
                        a0 += w[o + nz[k]] * xv[k]; a1 += w[o + nz[k + 1]] * xv[k + 1]
                        k += 2
                    }
                    if (k < nzCount) a0 += w[o + nz[k]] * xv[k]
                    val v = (a0 + a1) * s[j] + b[j]
                    out[j] = if (relu && v < 0f) 0f else v
                }
                return out
            }
            for (j in 0 until m) {
                val o = j * n
                var a0 = 0f; var a1 = 0f; var a2 = 0f; var a3 = 0f
                var i = 0
                while (i + 3 < n) {
                    a0 += w[o + i] * x[i]; a1 += w[o + i + 1] * x[i + 1]
                    a2 += w[o + i + 2] * x[i + 2]; a3 += w[o + i + 3] * x[i + 3]
                    i += 4
                }
                while (i < n) { a0 += w[o + i] * x[i]; i++ }
                val v = ((a0 + a1) + (a2 + a3)) * s[j] + b[j]
                out[j] = if (relu && v < 0f) 0f else v
            }
            return out
        }

        internal fun softmax(z: FloatArray): FloatArray {
            var mx = Float.NEGATIVE_INFINITY
            for (v in z) if (v > mx) mx = v
            var sum = 0f
            for (k in z.indices) { z[k] = exp(z[k] - mx); sum += z[k] }
            for (k in z.indices) z[k] /= sum
            return z
        }
    }
}
