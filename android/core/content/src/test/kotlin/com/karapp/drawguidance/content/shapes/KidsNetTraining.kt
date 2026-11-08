package com.karapp.drawguidance.content.shapes

import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * v34 çocuk modunun temel ağının bilgisayarda eğitimi (yalnız KIDSNET_TRAIN=1 iken çalışır). Ağ, resim modunun ağından
 * (gizli katman ve bilinen şablonların çıktı satırları) başlar; çocuk şablonları ("cocuk.") ve kapsamdaki genel
 * şablonlar el çizimi benzetimleriyle çalışılır: yeni şablon başına 160 tam + 40 eksik (yalnız ana parçalar) çizim,
 * bilinen şablon başına 30 çizim (unutmasın diye). Sonuç int8'e çevrilip `mode_kids.bin` olarak yazılır; cihazda bu
 * şablonlar için ilk açılışta eğitim gerekmez (önceden cihazda 14 benzetimle 6 tur öğreniliyordu).
 *
 * v38: uygulamayla gelen çocuk ağı bunun üstüne [ModeNetsV38Training] ile 4+ elemanlı çizimlerle yeniden eğitildi (sürüm
 * 38). Bu komut v34 ağını yeniden üretir; ardından ModeNetsV38Training yeniden çalıştırılmalıdır.
 */
class KidsNetTraining {
    @Test
    fun train() {
        Assume.assumeTrue(System.getenv("KIDSNET_TRAIN") == "1")
        val keys = ShapeTemplates.catalog.filter { ModeCatalog.allows(ShapeMode.KIDS, it.key, it.category) }.map { it.key }
        val byKey = ShapeTemplates.catalog.associateBy { it.key }
        val base = ShapeNet.bundledFor(ShapeMode.ART) ?: ShapeNet.bundled
        val baseKeys = base?.keys?.toHashSet() ?: emptySet()
        val rnd = java.util.Random(3400)
        val data = ArrayList<Pair<Int, FloatArray>>()
        var fresh = 0; var replay = 0
        for ((k, key) in keys.withIndex()) {
            val t = byKey.getValue(key)
            val known = key in baseKeys
            repeat(if (known) 30 else 160) { i ->
                ShapeNet.inputOf(ShapeAugment.handDrawn(t.strokes, rnd, amount = 0.6f + rnd.nextFloat() * 0.9f, strong = i % 3 == 2))
                    ?.let { data += k to it; if (known) replay++ else fresh++ }
            }
            if (!known && t.strokes.size > 3) repeat(40) {
                val part = t.strokes.take(3 + rnd.nextInt(t.strokes.size - 2))
                ShapeNet.inputOf(ShapeAugment.handDrawn(part, rnd, amount = 1f))?.let { data += k to it; fresh++ }
            }
        }
        val vr = java.util.Random(77)
        val val0 = keys.withIndex().flatMap { (k, key) ->
            List(8) { ShapeNet.inputOf(ShapeAugment.handDrawn(byKey.getValue(key).strokes, vr))?.let { k to it } }.filterNotNull()
        }
        // Önceki yol (cihazda kurulum: 14 benzetim, 6 tur) ile karşılaştırma
        val before = ModeModel(ShapeMode.KIDS, ModeNet.init(keys, base)).also { m ->
            m.bootstrap(keys.filter { it !in baseKeys }.map { byKey.getValue(it) }, keys.filter { it in baseKeys }.map { byKey.getValue(it) }, samples = 14, epochs = 6)
        }.net
        val net = ModeNet.init(keys, base)
        val epochs = 16
        repeat(epochs) { e ->
            data.shuffle(rnd)
            val lr = 0.08f * (1f - e / (epochs + 1f))
            for ((k, x) in data) net.train(x, k, lr, 0.0015f)
        }
        fun acc(c: ShapeClassifier, onlyNew: Boolean): Pair<Float, Float> {
            val set = val0.filter { !onlyNew || keys[it.first] !in baseKeys }
            var t1 = 0; var t3 = 0
            for ((k, x) in set) { val p = c.probs(x); val o = p.indices.sortedByDescending { p[it] }; if (o[0] == k) t1++; if (k in o.take(3)) t3++ }
            return t1 / set.size.toFloat() to t3 / set.size.toFloat()
        }
        val q = ShapeNet.quantize(net.keys, net.inDim, net.hidden, net.w1, net.b1, net.w2, net.b2)
        val file = File("src/main/resources/shapenet/mode_kids.bin")
        file.writeBytes(q.encode())
        println("KIDS: ${keys.size} sınıf (${keys.count { it.startsWith(ModeCatalog.KIDS_PREFIX) }} çocuk şablonu), eğitim ${data.size} (yeni $fresh, tekrar $replay)")
        println("KIDS önce (cihazda 14×6): yeni ${acc(before, true)}, tümü ${acc(before, false)}")
        println("KIDS sonra (int8): yeni ${acc(q, true)}, tümü ${acc(q, false)}; ${file.length() / 1024} KB")
    }
}
