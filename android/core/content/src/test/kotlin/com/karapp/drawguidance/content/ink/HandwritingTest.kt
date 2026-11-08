package com.karapp.drawguidance.content.ink

import com.karapp.drawguidance.content.Preview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.random.Random

/**
 * El yazısı tanıyıcı: yapay (şablonlardan ayrı taslaklarla üretilmiş) örneklerle tek karakter doğruluğu, çok karakterli
 * dizgiler, çizim/karalama reddi, hız ve API sözleşmesi. -PpreviewDir verilirse şablon ve örnek PNG'leri yazılır.
 */
class HandwritingTest {

    companion object {
        const val DIGITS = "0123456789"
        const val LOWER = "abcçdefgğhıijklmnoöpqrsştuüvwxyz"
        const val UPPER = "ABCÇDEFGĞHIİJKLMNOÖPQRSŞTUÜVWXYZ"
        const val SYMBOLS = "+−=×÷/().,?!:<>%"

        /** Biçimi aynı (yalnız boyut/konumla ayrılan) karakterler: tek başına yazıldığında alternatiflerde olması yeter. */
        val SAME_SHAPE = listOf("oO0", "öÖ", "iİ", "cC", "çÇ", "sS", "şŞ", "vV", "wW", "xX×", "zZ", "uU", "üÜ", "pP", "lI1ı")

        /** Bağlam olmadan anlamsız simgeler iki rakam arasında sınanır ("4.5"). */
        const val IN_CONTEXT = ".,−:×"
    }

    private fun sameShape(a: Char, b: String): Boolean =
        b.length == 1 && SAME_SHAPE.any { a in it && b[0] in it }

    private class Tally(var ok: Int = 0, var n: Int = 0) { val rate get() = ok.toFloat() / n.coerceAtLeast(1) }

    /** Tek karakter doğruluğu; dönen: karakter → (doğru, toplam), yanlışların örnekleri. */
    private fun charAccuracy(chars: String, perChar: Int, seed: Int, mode: InkMode = InkMode.MIXED): Pair<Map<Char, Tally>, Map<Char, Map<String, Int>>> {
        val rnd = Random(seed)
        val res = LinkedHashMap<Char, Tally>()
        val conf = LinkedHashMap<Char, MutableMap<String, Int>>()
        for (c in chars) {
            val t = Tally()
            repeat(perChar) {
                val ctx = c in IN_CONTEXT
                val text = if (ctx) "4${c}7" else c.toString()
                val ink = InkSynth.sample(text, rnd)
                val r = Handwriting.recognize(ink, mode)
                val got = when {
                    r == null -> "∅"
                    ctx -> if (r.text.length == 3 && r.text[0] == '4' && r.text[2] == '7') r.text.substring(1, 2) else "[${r.text}]"
                    else -> r.text
                }
                val ok = got == c.toString() || (!ctx && r != null && sameShape(c, got) &&
                    (r.alternatives + r.chars.flatMap { ch -> ch.candidates.map { it.text } }).contains(c.toString()))
                t.n++
                if (ok) t.ok++ else conf.getOrPut(c) { LinkedHashMap() }.merge(got, 1, Int::plus)
            }
            res[c] = t
        }
        return res to conf
    }

    private fun report(name: String, acc: Map<Char, Tally>, conf: Map<Char, Map<String, Int>>): Float {
        val ok = acc.values.sumOf { it.ok }; val n = acc.values.sumOf { it.n }
        val rate = ok.toFloat() / n
        println("[$name] ${"%.1f".format(rate * 100)}% ($ok/$n)")
        acc.filter { it.value.rate < 0.9f }.forEach { (c, t) ->
            println("   $c ${"%.0f".format(t.rate * 100)}%  → ${conf[c]?.entries?.sortedByDescending { it.value }?.take(4)?.joinToString { "${it.key}×${it.value}" }}")
        }
        return rate
    }

    @Test
    fun digitsAccuracy() {
        val (a, c) = charAccuracy(DIGITS, 60, 11)
        assertTrue(report("rakam", a, c) >= 0.95f)
    }

    @Test
    fun lowercaseAccuracy() {
        val (a, c) = charAccuracy(LOWER, 40, 12)
        assertTrue(report("küçük", a, c) >= 0.85f)
    }

    @Test
    fun uppercaseAccuracy() {
        val (a, c) = charAccuracy(UPPER, 40, 13)
        assertTrue(report("büyük", a, c) >= 0.85f)
    }

    @Test
    fun symbolAccuracy() {
        val (a, c) = charAccuracy(SYMBOLS, 40, 14)
        assertTrue(report("simge", a, c) >= 0.90f)
    }

    @Test
    fun digitsModeAccuracy() {
        val (a, c) = charAccuracy(DIGITS, 30, 15, InkMode.DIGITS)
        assertTrue(report("rakam/DIGITS", a, c) >= 0.96f)
    }

    val strings = listOf(
        "2026", "x+1=5", "Ali", "çiçek", "Ahmet", "3×4=12", "(a+b)", "50%", "Evet!", "Ağaç", "öğün", "İzmir",
        "12:45", "7/8", "Şule", "Nasıl?", "y=2x", "10<25", "Güneş", "kitap", "3,14", "Çorum", "9−4=5", "Işık",
    )

    @Test
    fun stringAccuracy() {
        val rnd = Random(21)
        var ok = 0; var n = 0
        val fails = LinkedHashMap<String, MutableList<String>>()
        for (s in strings) {
            var local = 0
            repeat(25) {
                val r = Handwriting.recognize(InkSynth.sample(s, rnd))
                n++
                if (r?.text == s) { ok++; local++ } else fails.getOrPut(s) { ArrayList() } += (r?.text ?: "∅")
            }
            println("   $s: $local/25  ${fails[s]?.groupingBy { it }?.eachCount()?.entries?.sortedByDescending { it.value }?.take(4) ?: ""}")
        }
        val rate = ok.toFloat() / n
        println("[dizgi] ${"%.1f".format(rate * 100)}% ($ok/$n)")
        assertTrue(rate >= 0.80f)
    }

    /**
     * v23 Türkçe dil modeli: sık sözcükler (normal ve sert bozulmayla) dil modeli açık ve kapalı okunur; açıkken doğruluk
     * artmalı, sözlük dışı dizgiler (sayılar, formüller) bozulmamalı.
     */
    @Test
    fun turkishWordsWithLanguageModel() {
        val words = listOf(
            "kitap", "defter", "kalem", "okul", "ödev", "sınav", "soru", "cevap", "güneş", "deniz", "ağaç", "çiçek",
            "kedi", "köpek", "elma", "ekmek", "masa", "kapı", "yeşil", "mavi", "kırmızı", "güzel", "doğru", "yanlış",
            "merhaba", "teşekkür", "öğretmen", "öğrenci", "matematik", "fizik", "dünya", "hayat", "bugün", "yarın",
        )
        val harsh = InkSynth.Options(slantDeg = 20f, rotDeg = 10f, warp = 7f)
        for ((label, opt) in listOf("normal" to InkSynth.Options(), "sert" to harsh)) {
            val rnd = Random(if (label == "normal") 41 else 43)
            var on = 0; var off = 0; var n = 0
            for (w in words) repeat(6) {
                val ink = InkSynth.sample(w, rnd, opt)
                n++
                if (Handwriting.recognize(ink, lexicon = true)?.text == w) on++
                if (Handwriting.recognize(ink, lexicon = false)?.text == w) off++
            }
            println("[sözcük/$label] dil modeli açık ${"%.1f".format(on * 100f / n)}%, kapalı ${"%.1f".format(off * 100f / n)}% ($n)")
            assertTrue("$label: $on < $off", on >= off)
        }
        // Sözlük dışı: sayılar ve formüller aynı kalır
        val rnd = Random(47)
        var same = 0; var n = 0
        for (s in listOf("2026", "x+1=5", "3×4=12", "12:45", "7/8", "y=2x")) repeat(10) {
            val ink = InkSynth.sample(s, rnd)
            n++
            if (Handwriting.recognize(ink, lexicon = true)?.text == Handwriting.recognize(ink, lexicon = false)?.text) same++
        }
        println("[sözcük dışı] aynı okuma ${same}/$n")
        assertTrue(same >= n * 0.9f)
    }

    @Test
    fun wordsWithSpaces() {
        val rnd = Random(22)
        var ok = 0; var n = 0
        for (s in listOf("Ali Veli", "2 + 3 = 5", "iyi günler", "x = 12", "ödev 3")) {
            var local = 0
            val got = ArrayList<String>()
            repeat(20) {
                val r = Handwriting.recognize(InkSynth.sample(s, rnd))
                n++
                if (r?.text == s) { ok++; local++ } else got += (r?.text ?: "∅")
            }
            println("   '$s': $local/20 ${got.take(4)}")
        }
        println("[boşluklu] ${"%.1f".format(ok * 100f / n)}%")
        assertTrue(ok >= 0.8f * n)
    }

    /** Daha sert bozulmalarla (eğim ±20°, döndürme ±10°, 2× bükülme) sağlamlık ölçümü — yalnız rapor + gevşek alt sınır. */
    @Test
    fun robustnessUnderHarsherDistortion() {
        val rnd = Random(71)
        val o = InkSynth.Options(slantDeg = 20f, rotDeg = 10f, warp = 7f)
        val all = DIGITS + LOWER + UPPER
        var ok = 0; var n = 0
        for (c in all) repeat(10) {
            val r = Handwriting.recognize(InkSynth.sample(c.toString(), rnd, o))
            n++
            val got = r?.text
            if (got == c.toString() || (got != null && sameShape(c, got) && (r.alternatives + r.chars.flatMap { ch -> ch.candidates.map { it.text } }).contains(c.toString()))) ok++
        }
        var sOk = 0; var sN = 0
        repeat(150) { i ->
            val s = strings[i % strings.size]
            sN++
            if (Handwriting.recognize(InkSynth.sample(s, rnd, o))?.text == s) sOk++
        }
        println("[sert] karakter ${"%.1f".format(ok * 100f / n)}%, dizgi ${"%.1f".format(sOk * 100f / sN)}%")
        assertTrue(ok >= 0.8f * n && sOk >= 0.6f * sN)
    }

    @Test
    fun rejectsDrawingsAndScribbles() {
        val rnd = Random(31)
        var rejected = 0; var n = 0
        for (k in InkSynth.drawingKinds) {
            var local = 0
            val got = ArrayList<String>()
            repeat(40) {
                val r = Handwriting.recognize(InkSynth.drawing(k, rnd))
                n++
                if (r == null || r.confidence < 0.4f) { rejected++; local++ } else got += "${r.text}(${"%.2f".format(r.confidence)})"
            }
            println("   $k: $local/40 reddedildi ${got.take(6)}")
        }
        val rate = rejected.toFloat() / n
        println("[ret] ${"%.1f".format(rate * 100)}% ($rejected/$n)")
        assertTrue(rate >= 0.90f)
    }

    @Test
    fun confidenceSeparatesTextFromDrawings() {
        // öneri eşiği seçimi için: metin örneklerinin ve çizimlerin güven dağılımı
        val rnd = Random(41)
        val text = ArrayList<Float>(); val correct = ArrayList<Boolean>()
        repeat(400) {
            val s = strings[rnd.nextInt(strings.size)].take(1 + rnd.nextInt(5))
            val r = Handwriting.analyze(InkSynth.sample(s, rnd))
            text += r?.confidence ?: 0f; correct += r?.text == s
        }
        val draw = ArrayList<Float>()
        repeat(300) { draw += Handwriting.analyze(InkSynth.drawing(InkSynth.drawingKinds[it % InkSynth.drawingKinds.size], rnd))?.confidence ?: 0f }
        for (th in listOf(0.35f, 0.45f, 0.5f, 0.55f, 0.6f, 0.7f)) {
            val shown = text.indices.filter { text[it] >= th }
            val prec = shown.count { correct[it] }.toFloat() / shown.size.coerceAtLeast(1)
            println("   eşik $th: metin gösterilen ${"%.0f".format(shown.size * 100f / text.size)}% (doğru ${"%.0f".format(prec * 100)}%), çizim gösterilen ${"%.0f".format(draw.count { it >= th } * 100f / draw.size)}%")
        }
        assertTrue(text.average() > draw.average() + 0.2)
    }

    @Test
    fun performance() {
        val rnd = Random(51)
        val w0 = System.nanoTime()
        Handwriting.warmUp()
        println("[hız] şablon hazırlığı (ilk çağrı, JVM soğuk; bu JVM'de önceden yapılmışsa ~0): ${"%.0f".format((System.nanoTime() - w0) / 1e6)} ms")
        val samples = (0 until 40).map { InkSynth.sample(listOf("x+1=52", "Ahmet4", "2026ab", "çiçek!")[it % 4], rnd) }
        repeat(3) { samples.forEach { s -> Handwriting.recognize(s) } }
        // Paralel test koşularında (uygulama testleriyle aynı anda) makine yüklü olabilir: 8 turun en hızlısı ölçülür
        val ms = (0 until 8).minOf {
            val t0 = System.nanoTime()
            samples.forEach { s -> Handwriting.recognize(s) }
            (System.nanoTime() - t0) / 1e6 / samples.size
        }
        println("[hız] 6 karakter: ${"%.2f".format(ms)} ms/tanıma")
        assertTrue("$ms ms", ms < 20.0)
    }

    @Test
    fun apiContract() {
        assertNull(Handwriting.recognize(emptyList()))
        assertNull(Handwriting.recognize(listOf(InkStroke(emptyList()))))
        assertTrue(!Handwriting.looksLikeWriting(emptyList()))
        // > 40 çizgi
        val many = (0 until 41).map { i -> InkStroke(listOf(InkPoint(i * 10f, 0f), InkPoint(i * 10f, 20f))) }
        assertNull(Handwriting.recognize(many))
        assertTrue(!Handwriting.looksLikeWriting(many))
        // karakter çizgi indisleri girişin tamamını kapsar
        val rnd = Random(61)
        val ink = InkSynth.sample("25", rnd)
        assertTrue(Handwriting.looksLikeWriting(ink))
        val r = Handwriting.recognize(ink)
        assertNotNull(r)
        assertEquals(ink.indices.toList(), r!!.chars.flatMap { it.strokeIndices }.sorted())
        assertTrue(r.confidence in 0f..1f)
        assertTrue(r.alternatives.size <= 3 && r.text !in r.alternatives)
        r.chars.forEach { c -> assertTrue(c.candidates.isNotEmpty() && c.candidates.all { it.score in 0f..1f }) }
        // ilk adaylar metni verir (boşluklar karakter sayılmaz)
        repeat(10) {
            val rr = Handwriting.recognize(InkSynth.sample(listOf("3×4", "x+1", "Ali Veli", "9−4")[it % 4], rnd)) ?: return@repeat
            assertEquals(rr.text.replace(" ", ""), rr.chars.joinToString("") { c -> c.candidates[0].text })
        }
        // uygulamanın önerdiği boy aralığının uçları (12 ve 220 birim)
        for (h in listOf(12f, 220f)) {
            var good = 0
            repeat(10) { if (Handwriting.recognize(InkSynth.sample("25", rnd, InkSynth.Options(minH = h, maxH = h)))?.text == "25") good++ }
            assertTrue("boy $h: $good/10", good >= 8)
        }
        // DIGITS kipinde çarpı biçimi hep ×
        var dm = 0
        repeat(10) { if (Handwriting.recognize(InkSynth.sample("2×3", rnd), InkMode.DIGITS)?.text == "2×3") dm++ }
        assertTrue("DIGITS 2×3: $dm/10", dm >= 8)
        // tek nokta (dokunuş) metin değildir
        assertNull(Handwriting.recognize(listOf(InkStroke(listOf(InkPoint(5f, 5f))))))
        // kip: LETTERS'ta rakam çıkmaz
        val o = Handwriting.recognize(InkSynth.sample("0", rnd), InkMode.LETTERS)
        if (o != null) assertTrue(o.text, o.text.none { it.isDigit() })
        // x bağlamı: rakam×rakam
        var mul = 0
        repeat(20) { if (Handwriting.recognize(InkSynth.sample("3×4", rnd))?.text == "3×4") mul++ }
        assertTrue("3×4: $mul/20", mul >= 16)
    }

    // ---------------------------------------------------------------- önizleme

    private fun drawPolys(g: java.awt.Graphics2D, polys: List<Poly>, x: Float, y: Float, size: Float) {
        val b = Box.of(polys)
        val s = size / maxOf(b.w, b.h, 1e-3f)
        g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for (p in polys) {
            val path = Path2D.Float()
            for (i in 0 until p.n) {
                val px = x + (p.x[i] - b.cx) * s + size / 2; val py = y + (p.y[i] - b.cy) * s + size / 2
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            if (p.n == 1) path.lineTo(x + (p.x[0] - b.cx) * s + size / 2 + 0.5f, y + (p.y[0] - b.cy) * s + size / 2)
            g.draw(path)
        }
    }

    @Test
    fun previewTemplates() {
        if (Preview.dir == null) return
        val items = Glyphs.sketches
        val cols = 12; val cell = 90
        val rows = (items.size + cols - 1) / cols
        val img = BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE; g.fillRect(0, 0, img.width, img.height)
        items.forEachIndexed { i, (label, src) ->
            val x = (i % cols) * cell; val y = (i / cols) * cell
            g.color = Color(0xEE, 0xEE, 0xF2); g.drawRect(x, y, cell, cell)
            g.color = Color(0x99, 0x33, 0x33); g.font = Font(Font.SANS_SERIF, Font.BOLD, 12); g.drawString(label, x + 4, y + 14)
            g.color = Color(0x22, 0x22, 0x22)
            drawPolys(g, Sketch.renderAll(src), x + 18f, y + 16f, cell - 30f)
        }
        g.dispose()
        Preview.write(img, "ink-templates")

        // test üretecinin (şablonlardan ayrı) taslakları
        val held = InkSynth.specs.flatMap { (c, sp) -> sp.sketches.map { c.toString() to it } }
        val rows2 = (held.size + cols - 1) / cols
        val img3 = BufferedImage(cols * cell, rows2 * cell, BufferedImage.TYPE_INT_RGB)
        val g3 = img3.createGraphics()
        g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g3.color = Color.WHITE; g3.fillRect(0, 0, img3.width, img3.height)
        held.forEachIndexed { i, (label, src) ->
            val x = (i % cols) * cell; val y = (i / cols) * cell
            g3.color = Color(0xEE, 0xEE, 0xF2); g3.drawRect(x, y, cell, cell)
            g3.color = Color(0x33, 0x55, 0x99); g3.font = Font(Font.SANS_SERIF, Font.BOLD, 12); g3.drawString(label, x + 4, y + 14)
            g3.color = Color(0x22, 0x22, 0x22)
            drawPolys(g3, Sketch.renderAll(src), x + 18f, y + 16f, cell - 30f)
        }
        g3.dispose()
        Preview.write(img3, "ink-heldout")

        // örnek üretilmiş dizgiler
        val rnd = Random(3)
        val texts = strings.take(12)
        val img2 = BufferedImage(900, texts.size * 110, BufferedImage.TYPE_INT_RGB)
        val g2 = img2.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = Color.WHITE; g2.fillRect(0, 0, img2.width, img2.height)
        texts.forEachIndexed { i, t ->
            val ink = InkSynth.sample(t, rnd, InkSynth.Options(minH = 60f, maxH = 60f))
            val r = Handwriting.recognize(ink)
            val polys = ink.map { s -> Poly(s.points.map { it.x }.toFloatArray(), s.points.map { it.y }.toFloatArray()) }
            val b = Box.of(polys)
            g2.color = Color(0x22, 0x22, 0x22)
            g2.stroke = BasicStroke(2f)
            for (p in polys) {
                val path = Path2D.Float()
                for (k in 0 until p.n) { val px = 20 + p.x[k] - b.minX; val py = i * 110 + 20 + p.y[k] - b.minY; if (k == 0) path.moveTo(px, py) else path.lineTo(px, py) }
                if (p.n == 1) path.lineTo(20 + p.x[0] - b.minX + 1, i * 110 + 20 + p.y[0] - b.minY)
                g2.draw(path)
            }
            g2.color = Color(0x22, 0x66, 0x22); g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 16)
            g2.drawString("\"$t\" → \"${r?.text}\" ${"%.2f".format(r?.confidence ?: 0f)} ${r?.alternatives}", 520, i * 110 + 60)
        }
        g2.dispose()
        Preview.write(img2, "ink-samples")
    }
}
