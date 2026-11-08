package com.karapp.drawguidance.content.ink

import java.util.Random

/**
 * Yazı ağının ayrılmış (eğitimde kullanılmayan tohumlarla üretilmiş) yapay örneklerdeki doğruluğu: tek karakter
 * sınıflandırma (yazı sistemi biliniyor) ve yazı sistemi başının doğruluğu. Eğitim raporunda ve testlerde kullanılır.
 */
internal object TextNetEval {
    /** Eğitim tohumlarından ayrık tohum tabanı. */
    const val EVAL_SEED = 7_700_000_000L

    class Score(var n: Int = 0, var top1: Int = 0, var top3: Int = 0, var script: Int = 0) {
        fun add(ok1: Boolean, ok3: Boolean, okScript: Boolean) { n++; if (ok1) top1++; if (ok3) top3++; if (okScript) script++ }
        fun pct(v: Int) = if (n == 0) 0f else 100f * v / n
        override fun toString() = "ilk-1 %.1f%% ilk-3 %.1f%% yazı sistemi %.1f%% (%d)".format(pct(top1), pct(top3), pct(script), n)
    }

    private fun topK(p: FloatArray, k: Int): List<Int> = p.indices.sortedByDescending { p[it] }.take(k)

    /** Tek karakterler: her sınıftan [perClass] örnek, [fonts] yazı tiplerinden. Japonca için kana/kanji ayrı da döner. */
    fun chars(net: HandwritingNet, script: InkScript, fonts: List<String>, perClass: Int, seed: Long = EVAL_SEED, filter: (Int) -> Boolean = { true }): Score {
        val sc = Score()
        val classes = TextNetData.classes(script)
        for (ci in classes.indices) {
            if (!filter(ci)) continue
            for (k in 0 until perClass) {
                val r = Random(seed + script.ordinal * 1_000_000L + ci * 1000L + k)
                val s = TextNetData.charSample(script, ci, fonts, r) ?: continue
                val h = net.hidden(TextFeatures.extract(GlyphInk.toPolys(s.strokes)))
                val p = net.probs(h, script.name)!!
                val sp = net.scriptProbs(h)
                val t = topK(p, 3)
                sc.add(t[0] == ci, ci in t, sp.indices.maxByOrNull { sp[it] } == script.ordinal)
            }
        }
        return sc
    }

    /** Korece heceler: üç bileşen de doğruysa doğru. [mode]: "seen" eğitim heceleri, "held" hiç görülmemiş heceler. */
    fun korean(net: HandwritingNet, fonts: List<String>, n: Int, heldOut: Boolean, seed: Long = EVAL_SEED): Score {
        val sc = Score()
        for (k in 0 until n) {
            val r = Random(seed + 50_000_000L + k + (if (heldOut) 1_000_000L else 0L))
            val s = TextNetData.koreanSample(r, fonts, allowHeldOut = heldOut, onlyHeldOut = heldOut) ?: continue
            sc.add(koreanTop(net, s, 1), koreanTop(net, s, 3), scriptOk(net, s))
        }
        return sc
    }

    /** Verilen metindeki heceler (sık heceler listesi). */
    fun koreanText(net: HandwritingNet, fonts: List<String>, text: String, perChar: Int, seed: Long = EVAL_SEED): Score {
        val sc = Score()
        text.forEachIndexed { i, ch ->
            val id = ch.code - 0xAC00
            if (id !in 0 until 11172) return@forEachIndexed
            val (l, v, t) = TextNetData.syllableParts(id)
            for (k in 0 until perChar) {
                val r = Random(seed + 70_000_000L + i * 100L + k)
                val f = fonts[r.nextInt(fonts.size)]
                val sk = GlyphInk.skeleton(f, ch.toString()) ?: continue
                val s = TextNetData.Sample(
                    GlyphInk.handwrite(sk, r, TextNetData.amount(r)), InkScript.KOREAN,
                    listOf(
                        HandwritingNet.KOREAN_L to l, HandwritingNet.KOREAN_V to v,
                        HandwritingNet.KOREAN_T to (if (t < 0) TextNetData.HANGUL_T.length else t),
                    ),
                )
                sc.add(koreanTop(net, s, 1), koreanTop(net, s, 3), scriptOk(net, s))
            }
        }
        return sc
    }

    private fun scriptOk(net: HandwritingNet, s: TextNetData.Sample): Boolean {
        val sp = net.scriptProbs(net.hidden(TextFeatures.extract(GlyphInk.toPolys(s.strokes))))
        return sp.indices.maxByOrNull { sp[it] } == s.script.ordinal
    }

    /** Bileşen olasılıklarının çarpımına göre en iyi [k] hece içinde doğru hece var mı. */
    private fun koreanTop(net: HandwritingNet, s: TextNetData.Sample, k: Int): Boolean {
        val h = net.hidden(TextFeatures.extract(GlyphInk.toPolys(s.strokes)))
        val pl = net.probs(h, HandwritingNet.KOREAN_L)!!; val pv = net.probs(h, HandwritingNet.KOREAN_V)!!; val pt = net.probs(h, HandwritingNet.KOREAN_T)!!
        val want = s.targets.associate { it }
        val combos = ArrayList<Pair<Triple<Int, Int, Int>, Float>>()
        for (l in topK(pl, 3)) for (v in topK(pv, 3)) for (t in topK(pt, 3)) combos += Triple(l, v, t) to pl[l] * pv[v] * pt[t]
        val best = combos.sortedByDescending { it.second }.take(k).map { it.first }
        val target = Triple(want.getValue(HandwritingNet.KOREAN_L), want.getValue(HandwritingNet.KOREAN_V), want.getValue(HandwritingNet.KOREAN_T))
        return target in best
    }
}
