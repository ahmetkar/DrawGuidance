package com.karapp.drawguidance.content.ink

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * v37 yazı ağının eğitimi (uzun sürer; yalnız `-PtrainTextNet=1` ile çalışır):
 *
 *     ./gradlew :core:content:test --tests '*TextNetTraining*' -PtrainTextNet=1
 *
 * Her dönemde yeni yapay örnekler üretilir (yazı tipi iskeleti + el yazısı bozulmaları; bkz. [GlyphInk], [TextNetData]),
 * Adam ile küçük yığınlarla (3 iş parçacığı) eğitilir, int8'e çevrilir ve `src/main/resources/shapenet/notes_text.bin`
 * yazılır. Rapor (ayrılmış tohumlarla ve ayrılmış yazı tipleriyle ölçülen doğruluk, dosya boyu, karakter başına süre)
 * standart çıktıya ve `build/textnet-report.txt` dosyasına yazılır. Ortam: TEXTNET_EPOCHS (dönem sayısı, varsayılan 44).
 */
class TextNetTraining {
    private fun enabled(): Boolean = System.getProperty("trainTextNet") == "1" || System.getenv("TEXTNET_TRAIN") == "1"

    companion object {
        const val IN = TextFeatures.DIM
        const val H1 = 256
        const val H2 = 192
        const val THREADS = 3
        const val BATCH = 120
    }

    /** Eğitilen başlar: ad, çıktılar ve kayıp ağırlığı. */
    private class HeadSpec(val name: String, val outputs: List<List<String>>, val weight: Float)

    private fun headSpecs(): List<HeadSpec> {
        val out = ArrayList<HeadSpec>()
        out += HeadSpec(HandwritingNet.SCRIPT, InkScript.entries.map { listOf(it.name) }, 0.5f)
        for (s in listOf(InkScript.LATIN, InkScript.CYRILLIC, InkScript.ARABIC, InkScript.JAPANESE)) {
            out += HeadSpec(s.name, TextNetData.classes(s).map { it.outputs } + listOf(emptyList()), 1f)
        }
        for ((name, outs) in TextNetData.koreanOutputs()) out += HeadSpec(name, outs, 0.8f)
        return out
    }

    // ---------------------------------------------------------------- ağ (float)

    private class Params(val heads: List<HeadSpec>, r: Random) {
        val w1 = FloatArray(H1 * IN) { (r.nextGaussian() * sqrt(2.0 / IN) * 2.0).toFloat() }
        val b1 = FloatArray(H1)
        val w2 = FloatArray(H2 * H1) { (r.nextGaussian() * sqrt(2.0 / H1)).toFloat() }
        val b2 = FloatArray(H2)
        val hw = heads.map { h -> FloatArray(h.outputs.size * H2) { (r.nextGaussian() * sqrt(1.0 / H2)).toFloat() } }
        val hb = heads.map { h -> FloatArray(h.outputs.size) }
        fun all(): List<FloatArray> = listOf(w1, b1, w2, b2) + hw + hb
    }

    private class Worker(val p: Params) {
        val g = p.all().map { FloatArray(it.size) }
        val z1 = FloatArray(H1); val a1 = FloatArray(H1); val z2 = FloatArray(H2); val a2 = FloatArray(H2)
        val dA2 = FloatArray(H2); val dZ2 = FloatArray(H2); val dA1 = FloatArray(H1); val dZ1 = FloatArray(H1)
        var loss = 0.0
        var count = 0
        val nz = IntArray(IN)

        fun zero() { g.forEach { it.fill(0f) }; loss = 0.0; count = 0 }

        fun step(x: FloatArray, targets: List<Pair<Int, Int>>) {
            var n = 0
            for (i in 0 until IN) if (x[i] != 0f) nz[n++] = i
            for (j in 0 until H1) {
                var s = p.b1[j]; val o = j * IN
                for (k in 0 until n) { val i = nz[k]; s += p.w1[o + i] * x[i] }
                z1[j] = s; a1[j] = if (s > 0f) s else 0f
            }
            for (j in 0 until H2) {
                var s = p.b2[j]; val o = j * H1
                for (i in 0 until H1) s += p.w2[o + i] * a1[i]
                z2[j] = s; a2[j] = if (s > 0f) s else 0f
            }
            dA2.fill(0f)
            for ((hi, t) in targets) {
                val hs = p.heads[hi]; val k = hs.outputs.size
                val w = p.hw[hi]; val b = p.hb[hi]
                val z = FloatArray(k)
                var mx = Float.NEGATIVE_INFINITY
                for (c in 0 until k) {
                    var s = b[c]; val o = c * H2
                    for (i in 0 until H2) s += w[o + i] * a2[i]
                    z[c] = s; if (s > mx) mx = s
                }
                var sum = 0f
                for (c in 0 until k) { z[c] = exp(z[c] - mx); sum += z[c] }
                loss += -ln((z[t] / sum).toDouble().coerceAtLeast(1e-9)) * hs.weight
                val gw = g[4 + hi]; val gb = g[4 + p.heads.size + hi]
                for (c in 0 until k) {
                    val d = (z[c] / sum - (if (c == t) 1f else 0f)) * hs.weight
                    if (d == 0f) continue
                    gb[c] += d
                    val o = c * H2
                    for (i in 0 until H2) { gw[o + i] += d * a2[i]; dA2[i] += d * w[o + i] }
                }
            }
            for (j in 0 until H2) dZ2[j] = if (z2[j] > 0f) dA2[j] else 0f
            dA1.fill(0f)
            val gw2 = g[2]; val gb2 = g[3]
            for (j in 0 until H2) {
                val d = dZ2[j]; if (d == 0f) continue
                gb2[j] += d
                val o = j * H1
                for (i in 0 until H1) { gw2[o + i] += d * a1[i]; dA1[i] += d * p.w2[o + i] }
            }
            val gw1 = g[0]; val gb1 = g[1]
            for (j in 0 until H1) {
                val d = if (z1[j] > 0f) dA1[j] else 0f
                if (d == 0f) continue
                gb1[j] += d
                val o = j * IN
                for (k in 0 until n) { val i = nz[k]; gw1[o + i] += d * x[i] }
            }
            count++
        }
    }

    private class Adam(val params: List<FloatArray>) {
        val m = params.map { FloatArray(it.size) }
        val v = params.map { FloatArray(it.size) }
        var t = 0
        fun update(grads: List<FloatArray>, lr: Float, scale: Float) {
            t++
            val b1 = 0.9f; val b2 = 0.999f
            val c1 = 1f - Math.pow(b1.toDouble(), t.toDouble()).toFloat()
            val c2 = 1f - Math.pow(b2.toDouble(), t.toDouble()).toFloat()
            for (k in params.indices) {
                val p = params[k]; val g = grads[k]; val mm = m[k]; val vv = v[k]
                for (i in p.indices) {
                    val gi = g[i] * scale
                    mm[i] = b1 * mm[i] + (1 - b1) * gi
                    vv[i] = b2 * vv[i] + (1 - b2) * gi * gi
                    p[i] -= lr * (mm[i] / c1) / (sqrt(vv[i] / c2) + 1e-8f)
                }
            }
        }
    }

    // ---------------------------------------------------------------- örnek planı

    /** Bir dönemin örnek tanımları: (yazı sistemi, sınıf; -1 = Korece, -2 = karakter değil). */
    private fun plan(epoch: Int): List<Pair<InkScript, Int>> {
        val out = ArrayList<Pair<InkScript, Int>>()
        val scale = System.getenv("TEXTNET_SCALE")?.toFloatOrNull() ?: 1f
        val per = mapOf(InkScript.LATIN to 90, InkScript.CYRILLIC to 110, InkScript.ARABIC to 110, InkScript.JAPANESE to 70)
        for ((s, k0) in per) {
            val k = max(1, (k0 * scale).toInt())
            val n = TextNetData.classes(s).size
            for (c in 0 until n) repeat(k) { out += s to c }
            repeat(n * k / 14) { out += s to -2 }
        }
        repeat((20000 * scale).toInt()) { out += InkScript.KOREAN to -1 }
        repeat((1100 * scale).toInt()) { out += InkScript.KOREAN to -2 }
        out.shuffle(Random(1000L + epoch))
        return out
    }

    private fun makeSample(spec: Pair<InkScript, Int>, seed: Long): TextNetData.Sample? {
        val r = Random(seed)
        val (s, c) = spec
        val fonts = TextNetData.trainFonts(s)
        return when (c) {
            -1 -> TextNetData.koreanSample(r, fonts, allowHeldOut = false)
            -2 -> TextNetData.junkSample(s, fonts, r)
            else -> TextNetData.charSample(s, c, fonts, r)
        }
    }

    // ---------------------------------------------------------------- eğitim

    @Test
    fun train() {
        assumeTrue("yalnız -PtrainTextNet=1 ile", enabled())
        val epochs = System.getenv("TEXTNET_EPOCHS")?.toIntOrNull() ?: 44
        val specs = headSpecs()
        val headIndex = specs.withIndex().associate { it.value.name to it.index }
        val p = Params(specs, Random(37))
        val adam = Adam(p.all())
        val workers = List(THREADS) { Worker(p) }
        val pool = Executors.newFixedThreadPool(THREADS)
        val log = StringBuilder()
        fun say(s: String) { println(s); log.appendLine(s); System.out.flush() }
        say("yazı ağı: ${specs.joinToString { "${it.name}=${it.outputs.size}" }}")
        val t0 = System.currentTimeMillis()
        val totalSteps = (0 until epochs).sumOf { (plan(it).size + BATCH - 1) / BATCH }
        var step = 0
        try {
            for (e in 0 until epochs) {
                val pl = plan(e)
                var lossSum = 0.0; var cnt = 0
                var b = 0
                while (b < pl.size) {
                    val end = minOf(b + BATCH, pl.size)
                    val slices = (0 until THREADS).map { w -> (b + w until end step THREADS) }
                    val futures = workers.mapIndexed { wi, wk ->
                        pool.submit(Callable {
                            wk.zero()
                            for (i in slices[wi]) {
                                val smp = makeSample(pl[i], e * 10_000_000L + i) ?: continue
                                val x = TextFeatures.extract(GlyphInk.toPolys(smp.strokes))
                                wk.step(x, smp.targets.map { headIndex.getValue(it.first) to it.second })
                            }
                        })
                    }
                    futures.forEach { it.get() }
                    val n = workers.sumOf { it.count }
                    if (n > 0) {
                        val g0 = workers[0].g
                        for (wk in workers.drop(1)) for (k in g0.indices) { val a = g0[k]; val o = wk.g[k]; for (i in a.indices) a[i] += o[i] }
                        val warm = minOf(1f, (step + 1) / 300f)
                        val lr = 2e-3f * warm * (0.02f + 0.98f * 0.5f * (1f + cos(Math.PI * step / totalSteps).toFloat()))
                        adam.update(g0, lr, 1f / n)
                        lossSum += workers.sumOf { it.loss }; cnt += n
                    }
                    step++
                    b = end
                }
                say("dönem ${e + 1}/$epochs: kayıp %.4f, %d örnek, %d sn".format(lossSum / max(cnt, 1), cnt, (System.currentTimeMillis() - t0) / 1000))
            }
        } finally {
            pool.shutdown()
        }
        val net = HandwritingNet.quantize(
            IN, H1, H2, p.w1, p.b1, p.w2, p.b2,
            specs.mapIndexed { i, h -> Triple(h.name, h.outputs, p.hw[i] to p.hb[i]) },
        )
        val bytes = net.encode()
        val outFile = File("src/main/resources/shapenet/notes_text.bin")
        outFile.parentFile.mkdirs()
        outFile.writeBytes(bytes)
        say("yazıldı: ${outFile.path} (${bytes.size} bayt, ${net.parameters} parametre)")
        say(report(HandwritingNet.decode(bytes)!!))
        File("build/textnet-report.txt").writeText(log.toString())
    }

    private fun report(net: HandwritingNet): String {
        val sb = StringBuilder()
        for (s in listOf(InkScript.LATIN, InkScript.CYRILLIC, InkScript.ARABIC, InkScript.JAPANESE)) {
            val seen = TextNetEval.chars(net, s, TextNetData.trainFonts(s), perClass = if (s == InkScript.JAPANESE) 6 else 12)
            sb.appendLine("$s (${TextNetData.classes(s).size} sınıf) görülen yazı tipleri, yeni bozulmalar: $seen")
            TextNetData.HELD_OUT[s]?.let { f ->
                if (GlyphInk.font(f) != null) sb.appendLine("$s ayrılmış yazı tipi ($f): ${TextNetEval.chars(net, s, listOf(f), perClass = 12)}")
            }
            if (s == InkScript.JAPANESE) {
                val cls = TextNetData.classes(s)
                val kanji = TextNetData.KANJI.toSet()
                fun isKanji(ci: Int) = cls[ci].outputs.all { it[0] in kanji }
                sb.appendLine("  kana: ${TextNetEval.chars(net, s, TextNetData.trainFonts(s), 6) { !isKanji(it) }}")
                sb.appendLine("  kanji: ${TextNetEval.chars(net, s, TextNetData.trainFonts(s), 6) { isKanji(it) }}")
            }
        }
        val ko = TextNetData.trainFonts(InkScript.KOREAN)
        sb.appendLine("KOREAN eğitim heceleri (yeni bozulmalar): ${TextNetEval.korean(net, ko, 1500, heldOut = false)}")
        sb.appendLine("KOREAN hiç görülmemiş heceler: ${TextNetEval.korean(net, ko, 1500, heldOut = true)}")
        sb.appendLine("KOREAN sık heceler: ${TextNetEval.koreanText(net, ko, TextNetData.COMMON_KO, 6)}")
        // karakter başına süre
        val r = Random(5)
        val inputs = List(300) { GlyphInk.toPolys(TextNetData.charSample(InkScript.JAPANESE, r.nextInt(50), TextNetData.trainFonts(InkScript.JAPANESE), r)!!.strokes) }
        repeat(3) { inputs.forEach { net.probs(net.hidden(TextFeatures.extract(it)), InkScript.JAPANESE.name) } }
        val t0 = System.nanoTime()
        repeat(5) { inputs.forEach { net.probs(net.hidden(TextFeatures.extract(it)), InkScript.JAPANESE.name) } }
        sb.appendLine("karakter başına (öznitelik + ağ, bu makinede JVM): %.3f ms".format((System.nanoTime() - t0) / 1e6 / (5 * inputs.size)))
        return sb.toString()
    }

    // ---------------------------------------------------------------- çözücü raporu

    /** Satır okuma doğruluğu (yazı sistemi başına; ipuçlu/ipucusuz) ve Latin çözücüde ağ ağırlığının etkisi. */
    @Test
    fun decodeReport() {
        assumeTrue("yalnız -PtrainTextNet=1 ile", enabled())
        val sb = StringBuilder()
        for (s in listOf(InkScript.CYRILLIC, InkScript.ARABIC, InkScript.JAPANESE, InkScript.KOREAN)) {
            val hinted = TextNetWords.score(s, TextNetData.trainFonts(s), 3, hint = true, twoWords = true)
            sb.appendLine("$s ipuçlu: $hinted")
            sb.appendLine("   hatalar: ${hinted.fails.joinToString("  ")}")
            sb.appendLine("$s ipucusuz: ${TextNetWords.score(s, TextNetData.trainFonts(s), 1, hint = false)}")
            TextNetData.HELD_OUT[s]?.let { f -> sb.appendLine("$s ayrılmış yazı tipi ($f): ${TextNetWords.score(s, listOf(f), 3, hint = true)}") }
            if (System.getenv("TEXTNET_PER_FONT") == "1") for (f in TextNetData.trainFonts(s)) {
                val sc = TextNetWords.score(s, listOf(f), 1, hint = true)
                sb.appendLine("   $f: $sc ${sc.fails.take(6)}")
            }
            println(sb); System.out.flush()
        }
        // çözücü ayarı taraması (TEXTNET_SWEEP=1): karakter başı maliyet ve geniş grup cezası
        if (System.getenv("TEXTNET_SWEEP") == "1") {
            val l0 = ScriptDecoder.LAMBDA; val l1 = ScriptDecoder.LAMBDA_CJK; val w0 = ScriptDecoder.WIDE_CJK
            try {
                for (lam in listOf(0.3f, 0.5f, 0.7f, 0.9f)) for (wide in listOf(4f, 8f)) {
                    ScriptDecoder.LAMBDA = lam; ScriptDecoder.LAMBDA_CJK = lam; ScriptDecoder.WIDE_CJK = wide
                    val line = listOf(InkScript.CYRILLIC, InkScript.ARABIC, InkScript.JAPANESE, InkScript.KOREAN).joinToString("  ") { s ->
                        val sc = TextNetWords.score(s, TextNetData.trainFonts(s), 2, hint = true, seed = TextNetEval.EVAL_SEED + 95_000_000L, twoWords = true)
                        "$s %.1f/%.1f".format(sc.exactPct(), sc.charPct())
                    }
                    sb.appendLine("λ=$lam geniş=$wide: $line")
                    println(sb.lines().last { it.isNotBlank() }); System.out.flush()
                }
            } finally {
                ScriptDecoder.LAMBDA = l0; ScriptDecoder.LAMBDA_CJK = l1; ScriptDecoder.WIDE_CJK = w0
            }
        }
        // Latin: InkSynth dizgileri (HandwritingTest'ten ayrı tohum), ağ ağırlığı taraması
        val strings = listOf(
            "2026", "x+1=5", "Ali", "çiçek", "Ahmet", "3×4=12", "(a+b)", "50%", "Evet!", "Ağaç", "öğün", "İzmir", "12:45", "7/8",
            "Şule", "Nasıl?", "y=2x", "10<25", "Güneş", "kitap", "3,14", "Çorum", "9−4=5", "Işık", "masa", "kalem", "okul", "defter",
        )
        val saved = Decoder.NET_W
        try {
            for (w in listOf(0f, 0.3f, 0.6f, 1.0f, 1.5f)) {
                Decoder.NET_W = w
                val rnd = kotlin.random.Random(9100)
                var ok = 0; var n = 0; var latinDetected = 0
                val t0 = System.nanoTime()
                for (str in strings) repeat(12) {
                    val ink = InkSynth.sample(str, rnd)
                    val r = Handwriting.recognize(ink)
                    n++
                    if (r?.text == str) ok++
                    if (w == 0f && Handwriting.detectScript(ink) == InkScript.LATIN) latinDetected++
                }
                val ms = (System.nanoTime() - t0) / 1e6 / n
                var cOk = 0; var cN = 0
                val rnd2 = kotlin.random.Random(9200)
                for (c in "abcçdefgğhıijklmnoöpqrsştuüvwxyzABCDEFGHKLMNRTY0123456789") repeat(6) {
                    val r = Handwriting.recognize(InkSynth.sample(c.toString(), rnd2))
                    cN++; if (r?.text == c.toString()) cOk++
                }
                sb.appendLine("LATIN NET_W=$w: dizgi %.1f%% (%d), tek karakter (tam) %.1f%%, %.1f ms/dizgi".format(100f * ok / n, n, 100f * cOk / cN, ms) +
                    (if (w == 0f) ", ipucusuz Latin bulunan %.1f%%".format(100f * latinDetected / n) else ""))
                println(sb.lines().last { it.isNotBlank() }); System.out.flush()
            }
        } finally {
            Decoder.NET_W = saved
        }
        println(sb)
        File("build/textnet-decode-report.txt").writeText(sb.toString())
    }

    // ---------------------------------------------------------------- önizleme

    /**
     * İpucusuz yazı sistemi bulmanın Latin önselleri taraması: tek Latin karakterler, Latin dizgiler ve çizimler Latin
     * kalmalı (eski davranış), Latin dışı sözcükler kendi yazı sistemine gitmeli. Kanıt bir kez hesaplanır, önseller
     * üstünde karar yeniden verilir. Rapor: build/textnet-detect-report.txt
     */
    @Test
    fun detectSweep() {
        assumeTrue("yalnız -PtrainTextNet=1 ile", enabled())
        val net = HandwritingNet.bundled!!
        fun ev(ink: List<InkStroke>): Triple<FloatArray, Int, Int>? {
            val ps = Prep.polys(ink).map { it.second }
            return ScriptDecoder.evidence(ps, net)?.let { Triple(it.first, it.second, ps.size) }
        }
        val groups = LinkedHashMap<String, Pair<InkScript?, MutableList<Triple<FloatArray, Int, Int>>>>()
        fun add(name: String, want: InkScript?, e: Triple<FloatArray, Int, Int>?) { if (e != null) groups.getOrPut(name) { want to ArrayList() }.second += e }
        val kr = kotlin.random.Random(901)
        for (c in "0123456789abcçdefgğhıijklmnoöpqrsştuüvwxyzABCÇDEFGĞHIİJKLMNOÖPQRSŞTUÜVWXYZ+−=×÷/().,?!:<>%") repeat(6) {
            add("Latin tek karakter", InkScript.LATIN, ev(InkSynth.sample(c.toString(), kr)))
        }
        for (s in listOf("2026", "x+1=5", "Ali", "çiçek", "Ahmet", "3×4=12", "(a+b)", "50%", "Evet!", "Ağaç", "öğün", "İzmir",
            "12:45", "7/8", "Şule", "Nasıl?", "y=2x", "10<25", "Güneş", "kitap", "3,14", "Çorum", "9−4=5", "Işık")) repeat(6) {
            add("Latin dizgi", InkScript.LATIN, ev(InkSynth.sample(s, kr)))
        }
        for (k in InkSynth.drawingKinds) repeat(30) { add("çizim (Latin kalmalı)", InkScript.LATIN, ev(InkSynth.drawing(k, kr))) }
        for (s in listOf(InkScript.CYRILLIC, InkScript.ARABIC, InkScript.JAPANESE, InkScript.KOREAN)) {
            val fonts = TextNetData.trainFonts(s)
            for (rep in 0 until 3) for ((wi, w) in TextNetWords.words.getValue(s).withIndex()) {
                val ln = TextNetData.line(s, TextNetWords.splitChars(w), fonts, Random(TextNetEval.EVAL_SEED + 96_000_000L + rep * 1000L + wi)) ?: continue
                add("$s sözcük", s, ev(GlyphInk.toInk(ln.strokes)))
            }
            val r = Random(TextNetEval.EVAL_SEED + 97_000_000L)
            val cls = TextNetData.classes(s)
            repeat(150) {
                val smp = (if (s == InkScript.KOREAN) TextNetData.koreanSample(r, fonts, allowHeldOut = true)
                    else TextNetData.charSample(s, r.nextInt(cls.size), fonts, r)) ?: return@repeat
                add("$s tek karakter", s, ev(GlyphInk.toInk(smp.strokes)))
            }
        }
        val sb = StringBuilder()
        sb.appendLine("örnekler: " + groups.entries.joinToString { "${it.key} ${it.value.second.size}" })
        sb.appendLine("tek kümeli: " + groups.entries.joinToString { (name, g) -> "$name ${g.second.count { it.second == 1 }}" })
        sb.appendLine("iki kümeli: " + groups.entries.joinToString { (name, g) -> "$name ${g.second.count { it.second == 2 }}" })
        // seçilen: ScriptDecoder.LATIN_SINGLE_PRIOR (tek küme), LATIN_DOUBLE_PRIOR (iki küme), LATIN_PRIOR (toplam)
        for (single in listOf(1f, 1e2f, 1e3f)) for (double in listOf(1f, 3f, 10f, 30f, 100f)) for (tp in listOf(6f, 20f)) {
            val line = groups.entries.joinToString("  ") { (name, g) ->
                val ok = g.second.count { (e, count, _) ->
                    val acc = e.copyOf()
                    acc[InkScript.LATIN.ordinal] += ln(tp) + (if (count == 1) ln(single) else if (count == 2) ln(double) else 0f)
                    InkScript.entries[acc.indices.maxByOrNull { acc[it] }!!] == g.first
                }
                "$name %.1f".format(100f * ok / g.second.size.coerceAtLeast(1))
            }
            sb.appendLine("tek küme $single, iki küme $double, toplam $tp: $line")
        }
        println(sb)
        File("build/textnet-detect-report.txt").writeText(sb.toString())
    }

    @Test
    fun preview() {
        assumeTrue("yalnız -PtrainTextNet=1 ile", enabled())
        val dir = File(System.getProperty("previewDir") ?: "build/textnet-preview").apply { mkdirs() }
        val rows = listOf(
            InkScript.LATIN to listOf(0, 3, 8, 12, 20, 33, 40, 47, 60),
            InkScript.CYRILLIC to listOf(0, 2, 9, 12, 14, 26, 28, 33, 35),
            InkScript.ARABIC to listOf(0, 1, 3, 6, 9, 13, 17, 26, 40),
            InkScript.JAPANESE to listOf(0, 10, 30, 50, 75, 100, 150, 180, 210),
        )
        val cell = 90
        val img = BufferedImage(cell * 10, cell * (rows.size * 2 + 2), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE; g.fillRect(0, 0, img.width, img.height)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val colors = listOf(Color(200, 40, 40), Color(40, 120, 200), Color(30, 150, 60), Color(160, 60, 180), Color(220, 140, 20), Color(0, 0, 0))
        fun draw(strokes: List<List<GlyphInk.Pt>>, cx: Int, cy: Int) {
            if (strokes.isEmpty()) return
            val all = strokes.flatten()
            val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }; val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
            val s = (cell - 16) / max(max(maxX - minX, maxY - minY), 1e-3f)
            g.stroke = BasicStroke(2f)
            strokes.forEachIndexed { k, st ->
                g.color = colors[k % colors.size]
                if (st.size == 1) g.fillOval((cx + 8 + (st[0].x - minX) * s).toInt() - 2, (cy + 8 + (st[0].y - minY) * s).toInt() - 2, 5, 5)
                for (i in 1 until st.size) g.drawLine(
                    (cx + 8 + (st[i - 1].x - minX) * s).toInt(), (cy + 8 + (st[i - 1].y - minY) * s).toInt(),
                    (cx + 8 + (st[i].x - minX) * s).toInt(), (cy + 8 + (st[i].y - minY) * s).toInt(),
                )
            }
        }
        var row = 0
        for ((s, idx) in rows) {
            for ((col, ci) in idx.withIndex()) {
                val cls = TextNetData.classes(s)[ci]
                val sk = TextNetData.skeleton(cls, TextNetData.trainFonts(s), Random(ci.toLong()))
                if (sk != null) draw(sk.joined, col * cell, row * cell)
                val smp = TextNetData.charSample(s, ci, TextNetData.trainFonts(s), Random(99L + ci))
                if (smp != null) draw(smp.strokes, col * cell, (row + 1) * cell)
            }
            row += 2
        }
        val ko = TextNetData.trainFonts(InkScript.KOREAN)
        for (k in 0 until 10) {
            TextNetData.koreanSample(Random(k.toLong()), ko, false, amount = 0.01f)?.let { draw(it.strokes, k * cell, row * cell) }
            TextNetData.koreanSample(Random(k.toLong()), ko, false)?.let { draw(it.strokes, k * cell, (row + 1) * cell) }
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "v37-textnet-samples.png"))
        // satırlar
        val lines = listOf(
            InkScript.CYRILLIC to "привет мир", InkScript.ARABIC to "سلام ٣٤", InkScript.JAPANESE to "ありがとう日本",
            InkScript.KOREAN to "안녕하세요 한국", InkScript.LATIN to "Merhaba 25",
        )
        val img2 = BufferedImage(900, 100 * lines.size, BufferedImage.TYPE_INT_RGB)
        val g2 = img2.createGraphics()
        g2.color = Color.WHITE; g2.fillRect(0, 0, img2.width, img2.height)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.stroke = BasicStroke(2f); g2.color = Color.BLACK
        lines.forEachIndexed { li, (s, text) ->
            val ln = TextNetData.line(s, text.map { it.toString() }, TextNetData.trainFonts(s), Random(li.toLong())) ?: return@forEachIndexed
            val all = ln.strokes.flatten()
            val minX = all.minOf { it.x }; val minY = all.minOf { it.y }
            for (st in ln.strokes) for (i in 1 until st.size) g2.drawLine(
                (10 + (st[i - 1].x - minX) * 70).toInt(), (li * 100 + 10 + (st[i - 1].y - minY) * 70).toInt(),
                (10 + (st[i].x - minX) * 70).toInt(), (li * 100 + 10 + (st[i].y - minY) * 70).toInt(),
            )
        }
        g2.dispose()
        ImageIO.write(img2, "png", File(dir, "v37-textnet-lines.png"))
    }
}
