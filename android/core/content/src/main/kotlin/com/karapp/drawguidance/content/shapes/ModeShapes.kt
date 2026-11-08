package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.vector.P
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** v29 tahminin yapıldığı mod: her modun kendi şablon kapsamı ve kendi sinir ağı vardır. */
/** v34 KIDS: çocuklar için çizimin kendi kapsamı ve ağı (çocuk şekilleri ve çocuklara uygun genel şablonlar). */
enum class ShapeMode { ART, NOTES, COMIC, KIDS }

/** Şablon olasılıkları veren sınıflandırıcı (uygulamayla gelen [ShapeNet] ya da moda özel [ModeNet]). */
interface ShapeClassifier {
    val keys: List<String>
    fun indexOf(key: String): Int
    fun probs(x: FloatArray): FloatArray
}

/**
 * v29 moda özel arama kapsamı: aranan şablonlar ([keys]), modun ağı ([net], hazır değilse null) ve ağırlığı, modda
 * öğrenilen kabul/ret sayaçları (önsel).
 */
class ShapeScope(
    val mode: ShapeMode,
    val keys: Set<String>,
    val net: ShapeClassifier?,
    val netWeight: Float,
    val usage: Map<String, Int>,
    val rejects: Map<String, Int>,
)

/**
 * v29 modların şablon kapsamı:
 * - **Resim (tuval):** genel katalogun tamamı (yüz, insan, hayvan, yapı, doğa, eşya, 3B cisim…).
 * - **Not alma:** simgeler, 3B cisimler, eşya/alet/teknoloji, doğa ve bitki (fen) şablonları ve not alma şablonları
 *   ("ders.": eksen, sayı doğrusu, açı, Venn, akış şeması, grafik, devre, atom…).
 * - **Çizgi roman:** 3B cisimler ve teknoloji simgeleri dışında genel katalog ve çizgi roman şablonları ("roman.": balon,
 *   patlama, duygu işaretleri, hız çizgileri, paneller, manga gözü…).
 * - v37 kütüphane şablonları ([LibraryTemplates]): nesneler ("obje.") resim ve çizgi roman modlarında (not alma modunda
 *   yalnız not kategorilerindekiler), karakterler ("roman.karakter.") ve efektler ("roman.efekt.") yalnız çizgi romanda.
 *   Çocuk modunun kapsamı değişmez.
 */
object ModeCatalog {
    const val NOTES_PREFIX = "ders."
    const val COMIC_PREFIX = "roman."
    /** v34 çocuklar için çizimin şablonları (çocuk boyama şekillerinden üretilir). */
    const val KIDS_PREFIX = "cocuk."

    private val notesCategories = setOf("Simge", "3B cisim", "Nesne", "Teknoloji", "Alet", "Gökyüzü ve doğa", "Bitki", "Meyve", "Müzik")
    private val comicExcluded = setOf("3B cisim", "Teknoloji")
    /** v34 çocuklar için: çocukların çizdiği konular (hayvan, meyve, doğa, taşıt, oyuncak, hayal…). */
    private val kidsCategories = setOf(
        "Hayvan", "Meyve", "Sebze", "Gökyüzü ve doğa", "Bitki", "Taşıt", "Spor ve oyun", "Yiyecek ve mutfak", "Hayal",
        "Simge", "Nesne", "Müzik", "Yapı",
    )
    /** v34 basit resimde aranmayan ayrıntı/teknik kategoriler (parça şablonları ve uzman konular). */
    private val simpleArtExcluded = setOf(
        "Vücut", "Hayvan uzvu", "Saç", "Çatı", "Kapı", "Pencere", "Teknoloji", "Fen", "Matematik", "Şema ve grafik",
        "Çizgi roman", "Sandalye", "Alet",
    )

    /** v34 basit resim kapsamında mı (resim modunun şablonlarından sade konular). */
    fun allowsSimpleArt(key: String, category: String): Boolean = allows(ShapeMode.ART, key, category) && category !in simpleArtExcluded

    /** v37 kütüphane nesnelerinin şablonları ([LibraryTemplates]): genel aramada ve çocuk modunda yok. */
    const val OBJECT_PREFIX = LibraryTemplates.OBJECT_PREFIX

    /** Yalnız bir modun kapsamındaki şablon (genel aramada kullanılmaz). */
    fun isModeOnly(key: String): Boolean =
        key.startsWith(NOTES_PREFIX) || key.startsWith(COMIC_PREFIX) || key.startsWith(KIDS_PREFIX) || key.startsWith(OBJECT_PREFIX)

    /** Genel katalog ya da (v37) kütüphane nesnesi: resim, not ve çizgi roman modlarında kategoriye göre aranır. */
    private fun general(key: String): Boolean = !isModeOnly(key) || key.startsWith(OBJECT_PREFIX)

    fun allows(mode: ShapeMode, key: String, category: String): Boolean = when (mode) {
        ShapeMode.ART -> general(key)
        ShapeMode.NOTES -> key.startsWith(NOTES_PREFIX) || (general(key) && category in notesCategories)
        ShapeMode.COMIC -> key.startsWith(COMIC_PREFIX) || (general(key) && category !in comicExcluded)
        // v37 çocuk modunun kapsamı değişmez (kütüphane nesneleri girmez)
        ShapeMode.KIDS -> key.startsWith(KIDS_PREFIX) || (!isModeOnly(key) && category in kidsCategories)
    }

    fun keys(mode: ShapeMode, metas: List<TemplateMeta>): List<String> = metas.filter { allows(mode, it.key, it.category) }.map { it.key }
}

/**
 * v29 moda özel, cihazda eğitilen sinir ağı (float): girdi [ShapeNet.IN_DIM] → gizli katman (ReLU) → modun şablonları
 * (softmax). Başlangıç ağırlıkları uygulamayla gelen ağdan alınır (modun şablonlarına ait satırlar); ağın bilmediği
 * (moda özel) şablonların satırları el çizimi benzetimleriyle cihazda öğrenilir. Kullanıcı geri bildirimiyle
 * ([train], [unlearn]) güncellenir.
 */
class ModeNet(
    override val keys: List<String>,
    val inDim: Int,
    val hidden: Int,
    internal val w1: FloatArray,
    internal val b1: FloatArray,
    internal val w2: FloatArray,
    internal val b2: FloatArray,
) : ShapeClassifier {
    private val keyIndex = keys.withIndex().associate { it.value to it.index }
    val outDim: Int get() = keys.size

    override fun indexOf(key: String): Int = keyIndex[key] ?: -1

    private fun hiddenOf(x: FloatArray): FloatArray {
        val h = FloatArray(hidden)
        for (j in 0 until hidden) {
            var acc = b1[j]
            val o = j * inDim
            for (i in 0 until inDim) acc += w1[o + i] * x[i]
            h[j] = if (acc > 0f) acc else 0f
        }
        return h
    }

    private fun outOf(h: FloatArray): FloatArray {
        val out = FloatArray(outDim)
        var mx = Float.NEGATIVE_INFINITY
        for (k in 0 until outDim) {
            var acc = b2[k]
            val o = k * hidden
            for (j in 0 until hidden) acc += w2[o + j] * h[j]
            out[k] = acc
            if (acc > mx) mx = acc
        }
        var sum = 0f
        for (k in 0 until outDim) { out[k] = exp(out[k] - mx); sum += out[k] }
        for (k in 0 until outDim) out[k] /= sum
        return out
    }

    @Synchronized
    override fun probs(x: FloatArray): FloatArray = outOf(hiddenOf(x))

    /**
     * Çıktı katmanında gradyan adımı: [grad] = ∂L/∂z (çıktı başına). [lrHidden] > 0 ise gizli katman da az miktarda
     * güncellenir.
     */
    private fun step(x: FloatArray, h: FloatArray, grad: FloatArray, lr: Float, lrHidden: Float) {
        val dh = if (lrHidden > 0f) FloatArray(hidden) else null
        for (k in 0 until outDim) {
            val g = grad[k]
            if (g == 0f) continue
            val o = k * hidden
            if (dh != null) for (j in 0 until hidden) dh[j] += g * w2[o + j]
            for (j in 0 until hidden) w2[o + j] -= lr * g * h[j]
            b2[k] -= lr * g
        }
        if (dh != null) {
            for (j in 0 until hidden) {
                if (h[j] <= 0f) continue
                val g = dh[j].coerceIn(-1f, 1f)
                val o = j * inDim
                for (i in 0 until inDim) w1[o + i] -= lrHidden * g * x[i]
                b1[j] -= lrHidden * g
            }
        }
    }

    /** Doğru şablon [target]: çapraz entropi adımı. Kayıp döner. */
    @Synchronized
    fun train(x: FloatArray, target: Int, lr: Float, lrHidden: Float = 0f): Float {
        if (target !in 0 until outDim) return 0f
        val h = hiddenOf(x)
        val p = outOf(h)
        val grad = FloatArray(outDim) { p[it] }
        grad[target] -= 1f
        step(x, h, grad, lr, lrHidden)
        return -kotlin.math.ln(p[target].coerceAtLeast(1e-7f))
    }

    /** Kapatılan öneri [k]: bu çizimde o şablonun olasılığı azaltılır (−log(1 − p) adımı). */
    @Synchronized
    fun unlearn(x: FloatArray, k: Int, lr: Float, lrHidden: Float = 0f) {
        if (k !in 0 until outDim) return
        val h = hiddenOf(x)
        val p = outOf(h)
        val factor = (p[k] / (1f - p[k]).coerceAtLeast(1e-4f)).coerceAtMost(2f)
        if (factor < 1e-4f) return
        val grad = FloatArray(outDim) { -factor * p[it] }
        grad[k] += factor
        step(x, h, grad, lr, lrHidden)
    }

    fun write(d: DataOutputStream) {
        d.writeInt(inDim); d.writeInt(hidden); d.writeInt(keys.size)
        keys.forEach { d.writeUTF(it) }
        w1.forEach(d::writeFloat); b1.forEach(d::writeFloat); w2.forEach(d::writeFloat); b2.forEach(d::writeFloat)
    }

    companion object {
        fun read(d: DataInputStream): ModeNet {
            val inDim = d.readInt(); val hidden = d.readInt(); val k = d.readInt()
            val keys = List(k) { d.readUTF() }
            val w1 = FloatArray(inDim * hidden) { d.readFloat() }; val b1 = FloatArray(hidden) { d.readFloat() }
            val w2 = FloatArray(hidden * k) { d.readFloat() }; val b2 = FloatArray(k) { d.readFloat() }
            return ModeNet(keys, inDim, hidden, w1, b1, w2, b2)
        }

        /**
         * [keys] için ağ: gizli katman ve bilinen şablonların çıktı satırları [base] ağdan (yoksa [prev] modelden); bilinmeyen
         * şablonların satırları sıfır, eğilimleri düşük (önce [ModeModel.bootstrap] ile öğrenilir).
         */
        fun init(keys: List<String>, base: ShapeNet?, prev: ModeNet? = null): ModeNet {
            val inDim = ShapeNet.IN_DIM
            val src = prev?.takeIf { it.inDim == inDim }
            val hidden = src?.hidden ?: base?.hidden ?: 96
            val w1: FloatArray; val b1: FloatArray
            if (src != null) { w1 = src.w1.copyOf(); b1 = src.b1.copyOf() }
            else if (base != null) { val (fw1, fb1) = base.hiddenLayer(); w1 = fw1; b1 = fb1 }
            else {
                val rnd = java.util.Random(29)
                w1 = FloatArray(inDim * hidden) { ((rnd.nextFloat() - 0.5f) * 0.3f) }; b1 = FloatArray(hidden) { 0.01f }
            }
            val w2 = FloatArray(hidden * keys.size)
            val b2 = FloatArray(keys.size)
            val known = BooleanArray(keys.size)
            for ((k, key) in keys.withIndex()) {
                val pi = src?.indexOf(key) ?: -1
                if (pi >= 0) {
                    src!!.w2.copyInto(w2, k * hidden, pi * hidden, (pi + 1) * hidden); b2[k] = src.b2[pi]; known[k] = true
                    continue
                }
                val bi = if (base != null && base.hidden == hidden) base.indexOf(key) else -1
                if (bi >= 0) {
                    base!!.outputRow(bi).copyInto(w2, k * hidden); b2[k] = base.outputBias(bi); known[k] = true
                }
            }
            val low = if (known.any { it }) b2.filterIndexed { i, _ -> known[i] }.minOrNull()!! - 0.5f else 0f
            for (k in keys.indices) if (!known[k]) b2[k] = low
            return ModeNet(keys, inDim, hidden, w1, b1, w2, b2)
        }
    }
}

/** v29 bir modun durumu (ayarlarda gösterilir). */
data class ModeInfo(val mode: ShapeMode, val templates: Int, val accepted: Int, val rejected: Int, val trainedSamples: Int, val ready: Boolean)

/**
 * v29 bir modun modeli: sinir ağı, modda kabul/ret sayaçları ve tekrar tamponu (önceki kabul edilen çizimler; yeni
 * öğrenme eskileri unutturmasın diye birlikte çalışılır). Dosyaya kaydedilir.
 */
class ModeModel internal constructor(
    val mode: ShapeMode,
    var net: ModeNet,
    internal val usage: HashMap<String, Int> = HashMap(),
    internal val rejects: HashMap<String, Int> = HashMap(),
    internal val replay: ArrayList<Pair<String, FloatArray>> = ArrayList(),
    var trained: Int = 0,
    var catalogVersion: Int = ShapeTemplates.VERSION,
    /** v30 başlangıç ağının sürümü ([ShapeNet.modeNetVersion]; 0: v29'daki genel ağdan kurulmuş). */
    var baseVersion: Int = ShapeNet.MODE_NET_VERSION,
    /** v37 dosyanın yazıldığı biçim (okunurken; 3'ten eskisi v37'den önceki uygulamanın yazdığı model). */
    val format: Int = FORMAT,
) {
    private val rnd = java.util.Random(mode.ordinal * 31L + 7L)

    val accepted: Int get() = usage.values.sum()
    val rejected: Int get() = rejects.values.sum()

    /** Kullanıcı geri bildirimi arttıkça ağın aramadaki payı büyür. */
    val netWeight: Float get() = (AdvancedWeights.net + 0.02f * trained).coerceAtMost(MAX_NET_WEIGHT)

    @Synchronized
    fun scope(keys: Set<String>): ShapeScope = ShapeScope(mode, keys, net, netWeight, HashMap(usage), HashMap(rejects))

    /**
     * Ağın bilmediği şablonların (moda özel) satırları el çizimi benzetimleriyle öğrenilir; bilinen şablonlardan da
     * birkaç örnek katılır (göreli olasılıklar dengede kalsın). Gizli katman değişmez.
     */
    @Synchronized
    fun bootstrap(unknown: List<ShapeTemplate>, known: List<ShapeTemplate>, samples: Int = 20, epochs: Int = 6) {
        if (unknown.isEmpty()) return
        val data = ArrayList<Pair<Int, FloatArray>>()
        val r = java.util.Random(4049L + mode.ordinal)
        // v38 ağ yalnız 4+ elemanlı çizimlerde çalışır: benzetimler 4+ elemanlı (az çizgili şablon parçalara bölünür)
        for (t in unknown) {
            val k = net.indexOf(t.key)
            if (k < 0) continue
            repeat(samples) { i ->
                val drawn = ShapeAugment.manyElements(t.strokes, r, part = i % 5 == 4, amount = if (i % 3 == 0) 1.4f else 1f, strong = i % 4 == 3)
                ShapeNet.inputOf(drawn)?.let { data += k to it }
            }
        }
        for (t in known.shuffled(r).take(min(known.size, unknown.size * 4))) {
            val k = net.indexOf(t.key)
            if (k < 0) continue
            ShapeNet.inputOf(ShapeAugment.manyElements(t.strokes, r))?.let { data += k to it }
        }
        repeat(epochs) { e ->
            data.shuffle(r)
            val lr = 0.12f * (1f - e / (epochs + 1f))
            for ((k, x) in data) net.train(x, k, lr)
        }
    }

    /**
     * v30 yeni başlangıç ağına geçince: önceden kabul edilen çizimler (tekrar tamponu) yeni ağda birkaç tur çalışılır;
     * kişisel tercih yeni ağa taşınır.
     */
    @Synchronized
    fun relearn(epochs: Int = 3) {
        if (replay.isEmpty()) return
        repeat(epochs) {
            for ((key, x) in replay.shuffled(rnd)) { val k = net.indexOf(key); if (k >= 0) net.train(x, k, LR, LR_HIDDEN) }
        }
    }

    /**
     * Kabul edilen öneri: sayaç artar; çok elemanlı çizimse ([x] verilmişse) ağ da o şablonun örneği olarak çalışır
     * (tekrar tamponuyla birlikte).
     */
    @Synchronized
    fun accept(key: String, x: FloatArray?) {
        usage[key] = (usage[key] ?: 0) + 1
        val k = net.indexOf(key)
        if (x == null || k < 0) return
        repeat(2) { net.train(x, k, LR, LR_HIDDEN) }
        // Tekrar: önceki kabul edilenler (eski öğrenilenler unutulmasın)
        if (replay.isNotEmpty()) repeat(min(REPLAY_STEPS, replay.size)) {
            val (rk, rx) = replay[rnd.nextInt(replay.size)]
            val ri = net.indexOf(rk)
            if (ri >= 0) net.train(rx, ri, LR * 0.5f, 0f)
        }
        replay += key to x
        while (replay.size > MAX_REPLAY) replay.removeAt(0)
        trained++
    }

    /** Kapatılan öneriler: o çizimde bu şablonların olasılığı azalır; ret sayaçları artar. */
    @Synchronized
    fun reject(keys: List<String>, x: FloatArray?) {
        keys.forEach { rejects[it] = (rejects[it] ?: 0) + 1 }
        if (x == null) return
        keys.forEach { key -> val k = net.indexOf(key); if (k >= 0) net.unlearn(x, k, LR * 0.4f, LR_HIDDEN * 0.5f) }
        trained++
    }

    fun info(templates: Int, ready: Boolean = true): ModeInfo = ModeInfo(mode, templates, accepted, rejected, trained, ready)

    @Synchronized
    fun encode(): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { d ->
            d.writeInt(MAGIC); d.writeInt(FORMAT)
            d.writeUTF(mode.name); d.writeInt(catalogVersion); d.writeInt(trained); d.writeInt(baseVersion)
            net.write(d)
            d.writeInt(usage.size); usage.forEach { (k, v) -> d.writeUTF(k); d.writeInt(v) }
            d.writeInt(rejects.size); rejects.forEach { (k, v) -> d.writeUTF(k); d.writeInt(v) }
            d.writeInt(replay.size)
            replay.forEach { (k, x) -> d.writeUTF(k); d.writeInt(x.size); x.forEach(d::writeFloat) }
        }
        return bos.toByteArray()
    }

    companion object {
        private const val MAGIC = 0x4D4F4E54 // "MONT"
        /**
         * 2 (v30): başlangıç ağı sürümü eklendi; 1 okunabilir. 3 (v37): içerik aynı; v37'den önce yazılan modeller
         * ayırt edilsin diye (eski not alma ağının öğrendikleri silinir, bkz. [ShapeNet.resetBelow]).
         */
        const val FORMAT = 3
        const val LR = 0.05f
        const val LR_HIDDEN = 0.004f
        const val REPLAY_STEPS = 6
        const val MAX_REPLAY = 240
        const val MAX_NET_WEIGHT = 0.32f

        fun decode(bytes: ByteArray): ModeModel? = runCatching {
            DataInputStream(bytes.inputStream()).use { d ->
                if (d.readInt() != MAGIC) return null
                val format = d.readInt()
                if (format !in 1..FORMAT) return null
                val mode = ShapeMode.valueOf(d.readUTF())
                val version = d.readInt(); val trained = d.readInt()
                val base = if (format >= 2) d.readInt() else 0
                val net = ModeNet.read(d)
                val usage = HashMap<String, Int>(); repeat(d.readInt()) { usage[d.readUTF()] = d.readInt() }
                val rejects = HashMap<String, Int>(); repeat(d.readInt()) { rejects[d.readUTF()] = d.readInt() }
                val replay = ArrayList<Pair<String, FloatArray>>()
                repeat(d.readInt()) { val k = d.readUTF(); val n = d.readInt(); replay += k to FloatArray(n) { d.readFloat() } }
                ModeModel(mode, net, usage, rejects, replay, trained, version, base, format)
            }
        }.getOrNull()
    }
}

/**
 * v29 modların modelleri: her mod (resim, not alma, çizgi roman) için ayrı sinir ağı ve ayrı öğrenme. Modeller ilk
 * kullanımda dosyadan okunur (yoksa uygulamayla gelen ağdan kurulup moda özel şablonlar cihazda öğrenilir) ve her
 * geri bildirimden sonra [dir] klasörüne yazılır. [dir] null ise yalnız bellekte (testler).
 */
class ShapeModeModels(private val dir: File?) {
    private val models = HashMap<ShapeMode, ModeModel>()
    private val keyCache = HashMap<ShapeMode, Pair<Int, Set<String>>>()

    private fun file(mode: ShapeMode): File? = dir?.let { File(it, "shape_mode_${mode.name.lowercase()}.bin") }

    /** Modun aranacak şablonları ([index]'te olanlar). */
    @Synchronized
    fun keys(mode: ShapeMode, index: ShapeIndex): Set<String> {
        keyCache[mode]?.takeIf { it.first == index.templateCount }?.let { return it.second }
        val keys = ModeCatalog.keys(mode, index.metas).toCollection(LinkedHashSet())
        keyCache[mode] = index.templateCount to keys
        return keys
    }

    /** Modun modeli (gerekirse dosyadan okunur ya da kurulur; kurulum ilk seferde ~1 sn sürebilir). */
    fun model(mode: ShapeMode, index: ShapeIndex): ModeModel {
        synchronized(this) { models[mode] }?.let { return it }
        val keys = keys(mode, index).toList()
        // v37 eski temel ağın (ör. eski not alma ağı) üstünde öğrenilenler yeni ağa taşınmaz: dosya silinir
        val loaded = load(mode)
        // v30 modun uygulamayla gelen ağı (yoksa genel ağ); v33 sunucudan gelen daha yeni ağ öncelikli. Temel ağ
        // değişince cihazda öğrenilenler (kabuller) yeni ağın üstüne yeniden çalışılır
        val (modeBase, baseVersion) = ShapeNet.baseFor(mode)
        val base = modeBase ?: ShapeNet.bundled
        val model = if (loaded != null && loaded.net.keys == keys && loaded.catalogVersion == ShapeTemplates.VERSION && loaded.baseVersion == baseVersion) loaded
        else {
            // Yeni başlangıç ağı: ağ ondan kurulur, önceki kabuller yeniden çalışılır. Yalnız katalog değiştiyse
            // öğrenilenler (bilinen şablonların satırları, sayaçlar, tampon) korunur
            val newBase = loaded == null || loaded.baseVersion != baseVersion
            val net = ModeNet.init(keys, base, if (newBase) null else loaded?.net)
            val m = ModeModel(mode, net, loaded?.usage ?: HashMap(), loaded?.rejects ?: HashMap(),
                ArrayList(loaded?.replay.orEmpty().filter { (k, _) -> k in keys }), loaded?.trained ?: 0, baseVersion = baseVersion)
            val learnt = if (newBase) emptySet() else loaded?.net?.keys?.toHashSet() ?: emptySet()
            val bundledKeys = base?.keys?.toHashSet() ?: emptySet()
            val byKey = ShapeTemplates.catalog.associateBy { it.key }
            val unknown = keys.filter { it !in learnt && it !in bundledKeys }.mapNotNull { byKey[it] }
            val known = keys.filter { it in bundledKeys || it in learnt }.mapNotNull { byKey[it] }
            m.bootstrap(unknown, known)
            if (newBase) m.relearn()
            m.also { save(it) }
        }
        synchronized(this) { models.getOrPut(mode) { model } }
        return synchronized(this) { models.getValue(mode) }
    }

    /**
     * Dosyadaki model; v37: eskimişse ([isStale]) dosya silinir ve null döner (öğrenilenler yeniden çalışılmaz).
     */
    private fun load(mode: ShapeMode): ModeModel? {
        val f = file(mode)?.takeIf { it.exists() } ?: return null
        val loaded = runCatching { ModeModel.decode(f.readBytes()) }.getOrNull() ?: return null
        if (isStale(loaded)) { f.delete(); return null }
        return loaded
    }

    /** Modun arama kapsamı (modelle). */
    fun scope(mode: ShapeMode, index: ShapeIndex): ShapeScope = model(mode, index).scope(keys(mode, index))

    /** Model hazırsa kapsam (hazır değilse ağsız kapsam; arama beklemez). */
    fun scopeIfReady(mode: ShapeMode, index: ShapeIndex): ShapeScope {
        val m = synchronized(this) { models[mode] }
        return m?.scope(keys(mode, index)) ?: ShapeScope(mode, keys(mode, index), null, AdvancedWeights.net, emptyMap(), emptyMap())
    }

    fun isReady(mode: ShapeMode): Boolean = synchronized(this) { models[mode] != null }

    /** v33 temel ağlar değişti (sunucu kataloğu): modeller bir sonraki kullanımda yeniden kurulur. */
    @Synchronized
    fun invalidate() { models.clear() }

    /**
     * Kabul edilen öneri: [drawn] kullanıcının çizimi. v30: ağ yalnız çok elemanlı (3+ çizgili) çizimlerden öğrenir;
     * az elemanlılarda yalnız sayaç (önsel) artar.
     */
    fun accepted(mode: ShapeMode, index: ShapeIndex, key: String, drawn: List<List<P>>) {
        val m = model(mode, index)
        m.accept(key, drawn.takeIf { ShapeComplexity.usesNet(it.size) }?.let { ShapeNet.inputOf(it) })
        save(m)
    }

    /** Kapatılan öneriler: [drawn] gösterildiği çizim (ağ yalnız 3+ çizgilide; yoksa yalnız sayaç). */
    fun rejected(mode: ShapeMode, index: ShapeIndex, keys: List<String>, drawn: List<List<P>>) {
        if (keys.isEmpty()) return
        val m = model(mode, index)
        m.reject(keys, drawn.takeIf { ShapeComplexity.usesNet(it.size) }?.let { ShapeNet.inputOf(it) })
        save(m)
    }

    /** Modun öğrendikleri silinir (uygulamayla gelen başlangıç modeline döner). */
    fun reset(mode: ShapeMode) {
        synchronized(this) { models.remove(mode) }
        file(mode)?.delete()
    }

    fun info(mode: ShapeMode, index: ShapeIndex?): ModeInfo {
        val m = synchronized(this) { models[mode] }
        val templates = index?.let { keys(mode, it).size } ?: 0
        if (m != null) return m.info(templates)
        // Yüklenmemiş: dosyadan özet (v37 eskimiş model sayılmaz)
        val loaded = file(mode)?.takeIf { it.exists() }?.let { runCatching { ModeModel.decode(it.readBytes()) }.getOrNull() }?.takeIf { !isStale(it) }
        return loaded?.info(templates, ready = false) ?: ModeInfo(mode, templates, 0, 0, 0, false)
    }

    companion object {
        /**
         * v37 modelin öğrendikleri artık kullanılmaz mı: temel ağı [ShapeNet.resetBelow] sürümünden eski, ya da sıfırlama
         * olan bir modda v37'den önceki uygulamanın yazdığı dosya (biçim < 3; eski not alma ağının verisi).
         */
        fun isStale(m: ModeModel): Boolean {
            val reset = ShapeNet.resetBelow(m.mode)
            return reset > 0 && (m.baseVersion < reset || m.format < 3)
        }
    }

    private fun save(m: ModeModel) {
        val f = file(m.mode) ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeBytes(m.encode())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}

/** Kapsam içindeki şablonların en yüksek olasılığına göre oran (0..1). */
internal fun relativeProb(probs: FloatArray, idx: Int, maxP: Float): Float =
    if (idx < 0) 0f else (probs[idx] / max(maxP, 1e-6f)).coerceIn(0f, 1f)
