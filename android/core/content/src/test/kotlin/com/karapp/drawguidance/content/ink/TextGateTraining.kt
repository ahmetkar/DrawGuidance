package com.karapp.drawguidance.content.ink

import com.karapp.drawguidance.content.ink.GlyphInk.Pt
import com.karapp.drawguidance.content.shapes.ShapeAugment
import com.karapp.drawguidance.content.shapes.ShapeTemplates
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v38 yazı önerisinin "yazı mı" kapısının eğitimi (yalnız `-PtrainNets38=1` ya da TEXTGATE_TRAIN=1 ile çalışır):
 *
 *     ./gradlew :core:content:test --tests '*TextGateTraining*' -PtrainNets38=1
 *
 * Kapı, yazı ağının ([HandwritingNet], v37 eğitimi değişmeden) ortak gövdesi üstünde iki doğrusal baştır
 * ([HandwritingNet.WRITING]: kümelerin ortalaması, [HandwritingNet.WRITING_MAX]: öğe başına en büyüğü,
 * [HandwritingNet.WRITING_ALL]: bütün grup; 2 sınıf). Gövde
 * dondurulur; yalnız başlar eğitilir (lojistik regresyon, Adam, L2) ve int8'e çevrilip `notes_text.bin` dosyasına
 * eklenir ([HandwritingNet.VERSION] 2).
 *
 * Eğitim verisi önceki gibi yapaydır: yazı = yazı tiplerinin iskeletlerinden el yazısı satırları ([TextNetData.line];
 * Latin/Türkçe, Kiril, Arapça, Japonca, Korece; en az 2 eleman), çizim = şablon veritabanının elle çizilmiş benzetimleri
 * (v36 kütüphane nesneleri, çizgi roman karakterleri ve efektleri dahil; 2+ eleman, yarım çizimler de) ve birbirine
 * yakın çizgi grupları (kutu kenarları, oklar, çarpılar, tarama, eğriler). Sınama ayrılmıştır: başka şablonlar, başka
 * yazı tipleri ([TextNetData.HELD_OUT]), başka üreteç (Latin için [InkSynth], çizimler için [InkSynth.drawing]) ve
 * gerçek sözcükler ([TextNetWords]). Rapor `build/textgate-v38-report.txt`.
 */
class TextGateTraining {
    private fun enabled(): Boolean = System.getProperty("trainNets38") == "1" || System.getenv("TEXTGATE_TRAIN") == "1"

    companion object {
        const val EPOCHS = 60
        const val BATCH = 128
        const val LR = 3e-3f
        const val L2 = 1e-4f
        const val THREADS = 4
        /**
         * Kapı başı sayısı: kümelerin ortalaması ve en büyüğü (2). Bütün grubun başı (3) denendi: ayrılmış sınamada
         * daha kötüydü (yazı yakalama %95,3 → %93,8), kullanılmıyor.
         */
        const val PARTS = 2
        const val TEXT_PER_SCRIPT_TRAIN = 1600
        const val TEXT_PER_SCRIPT_HELD = 160
        const val DRAW_PER_TEMPLATE_TRAIN = 4
        const val LINE_GROUPS_TRAIN = 2400
        const val LINE_GROUPS_HELD = 240
    }

    class Item(val ink: List<InkStroke>, val text: Boolean, val tag: String, val lang: String?)

    // ---------------------------------------------------------------- veri

    private fun scaled(strokes: List<List<Pt>>, r: Random, augment: Boolean = false): List<InkStroke> {
        // Eğitimde satır bozulması: eğiklik, döndürme, en-boy (farklı el yazısı alışkanlıkları)
        val s = if (!augment || r.nextFloat() < 0.3f) strokes else {
            val slant = (r.nextFloat() - 0.5f) * 0.6f
            val rot = (r.nextFloat() - 0.5f) * 0.25f
            val sx = 0.8f + r.nextFloat() * 0.45f
            val c = cos(rot); val sn = sin(rot)
            strokes.map { st -> st.map { p -> val x = (p.x + slant * -p.y) * sx; Pt(x * c - p.y * sn, x * sn + p.y * c) } }
        }
        return GlyphInk.toInk(s, scale = 36f + r.nextFloat() * 90f, ox = 50f + r.nextFloat() * 300f, oy = 150f + r.nextFloat() * 400f)
    }

    private fun randomChars(script: InkScript, r: Random): List<String> {
        // Tek karakter de (Korece hece, Japonca kanji gibi çok çizgili karakterler tek başına da yazıdır)
        val n = if (r.nextFloat() < (if (script == InkScript.KOREAN || script == InkScript.JAPANESE) 0.3f else 0.12f)) 1 else 2 + r.nextInt(6)
        val out = ArrayList<String>()
        for (i in 0 until n) {
            if (i > 0 && i < n - 1 && script != InkScript.JAPANESE && r.nextFloat() < 0.12f) { out += " "; continue }
            out += if (script == InkScript.KOREAN) {
                var id: Int
                do { id = r.nextInt(11172) } while (TextNetData.heldOutSyllable(id))
                val p = TextNetData.syllableParts(id)
                TextNetData.syllable(p.first, p.second, p.third).toString()
            } else {
                val cls = TextNetData.classes(script)
                val c = cls[r.nextInt(cls.size)]
                if (c.renders.isEmpty()) "a" else c.renders[r.nextInt(c.renders.size)].text
            }
        }
        return out
    }

    private fun latinWords(held: Boolean): List<String> =
        TurkishLexicon.words.filter { it.length >= 2 && ((it.hashCode() and 0x7fffffff) % 5 == 0) == held }.sorted()

    private fun textItems(held: Boolean, seed: Long): List<Item> {
        val out = ArrayList<Item>()
        for (script in InkScript.entries) {
            val lang = TextNetWords.hints[script]
            val fonts = if (held) (TextNetData.HELD_OUT[script]?.let { listOf(it) } ?: TextNetData.fonts(script)) else TextNetData.trainFonts(script)
            val words = if (script == InkScript.LATIN) latinWords(held) else TextNetWords.words.getValue(script)
            var chosen = ""
            val n = if (held) TEXT_PER_SCRIPT_HELD else TEXT_PER_SCRIPT_TRAIN
            var k = 0; var guard = 0
            while (k < n && guard < n * 4) {
                guard++
                val r = Random(seed + script.ordinal * 1_000_000L + guard)
                val ink: List<InkStroke>? = when {
                    // Latin ayrılmış: yarısı başka üreteç (InkSynth), yarısı ayrılmış yazı tipi
                    held && script == InkScript.LATIN && k % 2 == 0 -> InkSynth.sample(words[r.nextInt(words.size)], kotlin.random.Random(r.nextLong()))
                    // ayrılmış: gerçek sözcükler; eğitim: Latin'de sözlük sözcükleri ya da rastgele karakterler, diğerlerinde rastgele karakterler
                    held || (script == InkScript.LATIN && r.nextBoolean()) -> {
                        chosen = words[r.nextInt(words.size)]
                        TextNetData.line(script, TextNetWords.splitChars(chosen), fonts, r, 0.5f + 0.6f * r.nextFloat())?.let { scaled(it.strokes, r, !held) }
                    }
                    else -> TextNetData.line(script, randomChars(script, r), fonts, r, 0.5f + 0.6f * r.nextFloat())?.let { scaled(it.strokes, r, true) }
                }
                if (ink == null || ink.count { it.points.isNotEmpty() } < 2) continue
                val tag = when {
                    held && script == InkScript.LATIN -> if (k % 2 == 0) "LATIN:InkSynth" else "LATIN:ayrılmış yazı tipi"
                    held && chosen.length == 1 -> script.name + ":tek karakter"
                    else -> script.name
                }
                out += Item(ink, true, tag, lang)
                k++
            }
        }
        return out
    }

    /** Şablonun elle çizilmiş benzetimi (en az 2 eleman; bazen yarım çizim). */
    private fun templateItems(held: Boolean, seed: Long): List<Item> {
        val out = ArrayList<Item>()
        val catalog = ShapeTemplates.catalog.filter { ((it.key.hashCode() and 0x7fffffff) % 5 == 0) == held }
        val reps = if (held) 1 else DRAW_PER_TEMPLATE_TRAIN
        catalog.forEachIndexed { ti, t ->
            for (rep in 0 until reps) {
                val r = Random(seed + ti * 100L + rep)
                var s = ShapeAugment.handDrawn(t.strokes, r).filter { it.size >= 2 }
                if (s.size < 2) s = ShapeAugment.atLeast(s, 2, r)
                if (s.size >= 3 && r.nextFloat() < 0.35f) s = s.take(2 + r.nextInt(s.size - 1))
                if (s.size < 2) continue
                val scale = 0.8f + r.nextFloat() * 2.5f; val ox = r.nextFloat() * 300f; val oy = r.nextFloat() * 300f
                out += Item(s.map { st -> InkStroke(st.map { InkPoint(ox + it.x * scale, oy + it.y * scale) }) }, false, "şablon", "tr")
            }
        }
        return out
    }

    /** Birbirine yakın çizgi grupları (çizgi önerisinin konusu; yazı sayılmamalı). */
    private fun lineGroup(r: Random): List<InkStroke> {
        val s = 40f + r.nextFloat() * 260f
        val ox = r.nextFloat() * 300f; val oy = 100f + r.nextFloat() * 300f
        fun jitter(v: Float) = v + (r.nextFloat() - 0.5f) * 0.04f
        fun seg(x0: Float, y0: Float, x1: Float, y1: Float): List<InkPoint> {
            val n = 8 + r.nextInt(16)
            val bend = (r.nextFloat() - 0.5f) * 0.05f
            return (0..n).map { i ->
                val t = i / n.toFloat()
                val x = x0 + (x1 - x0) * t - (y1 - y0) * bend * sin(Math.PI * t).toFloat()
                val y = y0 + (y1 - y0) * t + (x1 - x0) * bend * sin(Math.PI * t).toFloat()
                InkPoint(ox + s * (x + (r.nextFloat() - 0.5f) * 0.01f), oy + s * (y + (r.nextFloat() - 0.5f) * 0.01f))
            }
        }
        fun arc(cx: Float, cy: Float, rad: Float, a0: Float, a1: Float): List<InkPoint> {
            val n = 16 + r.nextInt(16)
            return (0..n).map { i -> val a = a0 + (a1 - a0) * i / n; InkPoint(ox + s * (cx + rad * cos(a)), oy + s * (cy + rad * sin(a))) }
        }
        val w = 0.5f + r.nextFloat() * 1.5f
        val lines: List<List<InkPoint>> = when (r.nextInt(9)) {
            0 -> listOf(seg(0f, 0f, w, jitter(0f)), seg(w, 0f, jitter(w), 1f), seg(w, 1f, 0f, jitter(1f)), seg(0f, 1f, jitter(0f), 0f)).take(2 + r.nextInt(3))
            1 -> listOf(seg(0f, 0.5f, w * 2, 0.5f), seg(w * 2 - 0.3f, 0.25f, w * 2, 0.5f), seg(w * 2 - 0.3f, 0.75f, w * 2, 0.5f))
            2 -> listOf(seg(0f, 0f, 1f, 1f), seg(1f, 0f, 0f, 1f))
            3 -> List(2 + r.nextInt(6)) { i -> val y = i * (0.12f + r.nextFloat() * 0.1f); seg(0f, y, w * 2, jitter(y)) }
            4 -> List(3 + r.nextInt(5)) { i -> val x = i * 0.15f; seg(x, 0f, x + 0.4f, 1f) }
            5 -> listOf(seg(0.5f, 0f, 0f, 1f), seg(0f, 1f, 1f, 1f), seg(1f, 1f, 0.5f, 0f)).take(2 + r.nextInt(2))
            6 -> listOf(arc(0.5f, 0.5f, 0.5f, 0f, 3.3f), arc(0.5f, 0.5f, 0.5f, 3.1f, 6.4f))
            7 -> List(2 + r.nextInt(3)) { i -> arc(i * 0.6f, 0.5f, 0.3f, 3.14f, 6.28f) }
            else -> listOf(seg(0f, 0f, w, 0f), seg(0f, 0f, 0f, 1f), arc(w * 0.5f, 0.5f, 0.3f, 0f, 6.28f))
        }
        return lines.map { InkStroke(it) }
    }

    private fun lineItems(n: Int, seed: Long) = (0 until n).map { Item(lineGroup(Random(seed + it)), false, "çizgiler", "tr") }

    /** Ayrılmış çizimler: başka üreteç ([InkSynth.drawing]; 2+ elemanlı olanlar). */
    private fun synthDrawings(seed: Long): List<Item> {
        val out = ArrayList<Item>()
        for ((ki, kind) in InkSynth.drawingKinds.withIndex()) for (i in 0 until 40) {
            val r = Random(seed + ki * 1000L + i)
            val ink = InkSynth.drawing(kind, kotlin.random.Random(r.nextLong()))
            if (ink.size >= 2) out += Item(ink, false, "çizim:$kind", "tr")
        }
        return out
    }

    // ---------------------------------------------------------------- kapı

    /** Kapı başları (float): kümelerin ortalaması, en büyüğü ve bütün grup için ayrı ağırlıklar, ortak sapma. */
    private class Gate(val dim: Int) {
        val w = List(PARTS) { FloatArray(2 * dim) }; val b = FloatArray(2)
        fun logits(f: List<FloatArray>): FloatArray {
            val z = FloatArray(2)
            for (c in 0 until 2) {
                var s = b[c]
                for (p in 0 until PARTS) { val wp = w[p]; val x = f[p]; for (i in 0 until dim) s += wp[c * dim + i] * x[i] }
                z[c] = s
            }
            return z
        }
        fun prob(f: List<FloatArray>): Float {
            val z = logits(f)
            val mx = maxOf(z[0], z[1])
            val e0 = exp(z[0] - mx); val e1 = exp(z[1] - mx)
            return e1 / (e0 + e1)
        }
    }

    private class Feat(val f: List<FloatArray>, val y: Int)

    private fun features(net: HandwritingNet, items: List<Item>): List<Feat?> {
        val pool = Executors.newFixedThreadPool(THREADS)
        val out = items.chunked((items.size + THREADS * 4 - 1) / (THREADS * 4)).map { ch ->
            pool.submit(Callable {
                ch.map { it -> net.gateInput(Prep.polys(it.ink).map { p -> p.second })?.let { f -> Feat(f, if (it.text) 1 else 0) } }
            })
        }.flatMap { it.get() }
        pool.shutdown()
        return out
    }

    private fun train(dim: Int, data: List<Feat>, r: Random, log: (String) -> Unit): Gate {
        val g = Gate(dim)
        val params = g.w + listOf(g.b)
        val m1 = params.map { FloatArray(it.size) }; val m2 = params.map { FloatArray(it.size) }
        val pos = data.count { it.y == 1 }.toFloat(); val neg = data.size - pos
        val cw = floatArrayOf(data.size / (2f * neg), data.size / (2f * pos)) // sınıf dengesi
        val shuffled = data.toMutableList()
        var t = 0
        val steps = EPOCHS * ((data.size + BATCH - 1) / BATCH)
        for (e in 0 until EPOCHS) {
            shuffled.shuffle(r)
            var loss = 0.0
            var b0 = 0
            while (b0 < shuffled.size) {
                val end = minOf(b0 + BATCH, shuffled.size)
                val gr = params.map { FloatArray(it.size) }
                for (i in b0 until end) {
                    val f = shuffled[i]
                    val z = g.logits(f.f)
                    val mx = maxOf(z[0], z[1]); val e0 = exp(z[0] - mx); val e1 = exp(z[1] - mx)
                    val p = floatArrayOf(e0 / (e0 + e1), e1 / (e0 + e1))
                    val w = cw[f.y]
                    loss -= w * ln(p[f.y].toDouble().coerceAtLeast(1e-9))
                    for (c in 0 until 2) {
                        val d = w * (p[c] - if (c == f.y) 1f else 0f)
                        for (pp in 0 until PARTS) { val x = f.f[pp]; val gp = gr[pp]; for (k in 0 until dim) gp[c * dim + k] += d * x[k] }
                        gr[PARTS][c] += d
                    }
                }
                val n = (end - b0).toFloat()
                t++
                val lr = LR * (0.05f + 0.95f * 0.5f * (1f + cos(Math.PI * t / steps).toFloat()))
                val bc1 = 1f - Math.pow(0.9, t.toDouble()).toFloat(); val bc2 = 1f - Math.pow(0.999, t.toDouble()).toFloat()
                for ((pi, p) in params.withIndex()) {
                    val l2 = if (pi < PARTS) L2 else 0f
                    for (k in p.indices) {
                        val gk = gr[pi][k] / n + l2 * p[k]
                        m1[pi][k] = 0.9f * m1[pi][k] + 0.1f * gk
                        m2[pi][k] = 0.999f * m2[pi][k] + 0.001f * gk * gk
                        p[k] -= lr * (m1[pi][k] / bc1) / (sqrt(m2[pi][k] / bc2) + 1e-8f)
                    }
                }
                b0 = end
            }
            if (e % 10 == 9 || e == EPOCHS - 1) log("  tur ${e + 1}/$EPOCHS: kayıp " + "%.4f".format(loss / shuffled.size))
        }
        return g
    }

    private class Conf(var tp: Int = 0, var fn: Int = 0, var tn: Int = 0, var fp: Int = 0) {
        fun add(text: Boolean, said: Boolean) { if (text) { if (said) tp++ else fn++ } else { if (said) fp++ else tn++ } }
        override fun toString(): String {
            val n = tp + fn + tn + fp
            return "doğruluk " + "%.1f%%".format(100f * (tp + tn) / n.coerceAtLeast(1)) +
                ", yazı yakalama " + "%.1f%%".format(100f * tp / (tp + fn).coerceAtLeast(1)) + " ($tp/${tp + fn})" +
                ", çizimde yanlış yazı " + "%.1f%%".format(100f * fp / (fp + tn).coerceAtLeast(1)) + " ($fp/${fp + tn})"
        }
    }

    @Test
    fun train() {
        Assume.assumeTrue(enabled())
        val log = StringBuilder()
        fun say(s: String) { println(s); log.appendLine(s); System.out.flush() }
        val t0 = System.currentTimeMillis()
        val base = HandwritingNet.decode(File("src/main/resources/shapenet/notes_text.bin").readBytes())!!
        // gövde v37 ile aynı (yalnız kapı başları eklenir/yenilenir)
        val net = base.withHeads(emptyMap(), drop = setOf(HandwritingNet.WRITING, HandwritingNet.WRITING_MAX, HandwritingNet.WRITING_ALL))

        val trainItems = textItems(false, 380_000L) + templateItems(false, 381_000L) + lineItems(LINE_GROUPS_TRAIN, 382_000L)
        val heldItems = textItems(true, 383_000_000L) + templateItems(true, 384_000L) + lineItems(LINE_GROUPS_HELD, 385_000L) + synthDrawings(386_000L)
        say("eğitim: yazı ${trainItems.count { it.text }}, çizim ${trainItems.count { !it.text }}; sınama: yazı ${heldItems.count { it.text }}, çizim ${heldItems.count { !it.text }} (${(System.currentTimeMillis() - t0) / 1000} sn)")
        val trainF = features(net, trainItems).filterNotNull()

        say("öznitelikler hazır (${(System.currentTimeMillis() - t0) / 1000} sn)")
        val gate = train(net.h2, trainF, Random(3838L), ::say)
        // int8 başlar
        val headOut = listOf(listOf("shape"), listOf("text"))
        val dim = net.h2
        val hm = HandwritingNet.quantizeHead(headOut, gate.w[0], gate.b, dim)
        val hx = HandwritingNet.quantizeHead(headOut, gate.w[1], FloatArray(2), dim)
        val heads = mutableMapOf(HandwritingNet.WRITING to hm, HandwritingNet.WRITING_MAX to hx)
        if (PARTS > 2) heads[HandwritingNet.WRITING_ALL] = HandwritingNet.quantizeHead(headOut, gate.w[2], FloatArray(2), dim)
        val out = net.withHeads(heads)
        val bytes = out.encode()
        val back = HandwritingNet.decode(bytes)!!

        // Sınama: kapı tek başına, ve yazı önerisinin önceki (sezgi + tanıma güveni) / sonraki (+ kapı) kararı
        val gateConf = Conf()
        val trainConf = Conf()
        trainF.forEach { f -> trainConf.add(f.y == 1, gate.prob(f.f) >= Handwriting.GATE_MIN) }
        val pool = Executors.newFixedThreadPool(THREADS)
        val decisions = heldItems.indices.map { i ->
            pool.submit(Callable {
                val it = heldItems[i]
                val ps = Prep.polys(it.ink).map { p -> p.second }
                val gp = back.writingProb(ps) ?: 0f
                val heuristic = Handwriting.looksLikeWriting(it.ink, it.lang)
                val rec = heuristic && (runCatching { Handwriting.recognize(it.ink, InkMode.MIXED, language = it.lang) }.getOrNull()?.let { r -> r.confidence >= 0.5f && r.text.isNotBlank() } ?: false)
                Triple(gp, heuristic, rec)
            })
        }.map { it.get() }
        pool.shutdown()
        val heurBefore = Conf(); val heurAfter = Conf(); val pipeBefore = Conf(); val pipeAfter = Conf()
        val perTag = LinkedHashMap<String, Array<Conf>>()
        heldItems.forEachIndexed { i, it ->
            val (gp, heur, rec) = decisions[i]
            val g = gp >= Handwriting.GATE_MIN
            gateConf.add(it.text, g)
            heurBefore.add(it.text, heur); heurAfter.add(it.text, heur && g)
            pipeBefore.add(it.text, rec); pipeAfter.add(it.text, rec && g)
            val t = perTag.getOrPut(if (it.text) it.tag else it.tag.substringBefore(':')) { Array(3) { Conf() } }
            t[0].add(it.text, g); t[1].add(it.text, rec); t[2].add(it.text, rec && g)
        }
        for (th in listOf(0.1f, 0.2f, 0.3f, 0.5f, 0.7f)) {
            val c = Conf()
            heldItems.forEachIndexed { i, it -> c.add(it.text, decisions[i].first >= th) }
            say("  eşik " + "%.1f".format(th) + ": $c")
        }
        say("kapı (eğitim seti): $trainConf")
        say("kapı (ayrılmış, int8): $gateConf")
        say("ön süzgeç: önce (yalnız sezgi) $heurBefore; sonra (sezgi + kapı) $heurAfter")
        say("yazı önerisi (sezgi + tanıma güveni ≥ 0,5): önce $pipeBefore; sonra (+ kapı) $pipeAfter")
        for ((tag, c) in perTag) say("  $tag: kapı ${c[0]} | öneri önce ${c[1]} | sonra ${c[2]}")
        val file = File("src/main/resources/shapenet/notes_text.bin")
        val oldSize = file.length()
        file.writeBytes(bytes)
        say("notes_text.bin: $oldSize → ${bytes.size} bayt (kapı başları $PARTS × 2×${dim} int8), ${(System.currentTimeMillis() - t0) / 1000} sn")
        File("build").mkdirs()
        File("build/textgate-v38-report.txt").writeText(log.toString())
    }
}
