package com.karapp.drawguidance.content.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Random

/**
 * v37 not alma modunun yazı ağı: dosya biçimi ve sürüm kuralları, ayrılmış (eğitimde kullanılmayan tohumlarla üretilmiş)
 * yapay örneklerde karakter doğruluğu, Latin dışı satırların okunması (Kiril, Arapça, Japonca, Korece), yazı sistemi
 * bulma ve hız. Örnekler yazı tiplerinden üretildiği için gerekli yazı tipleri yoksa (başka makine) doğruluk testleri atlanır.
 */
class HandwritingNetTest {
    private val net: HandwritingNet get() = HandwritingNet.bundled!!

    private fun fontsFor(s: InkScript): List<String> = TextNetData.trainFonts(s).filter { GlyphInk.font(it) != null }

    private fun assumeFonts(s: InkScript) = assumeTrue("$s yazı tipleri yok", fontsFor(s).size >= 2)

    @Test fun bundledNetFormat() {
        val n = net
        assertEquals(TextFeatures.DIM, n.inDim)
        val names = n.heads.keys
        assertTrue(names.containsAll(listOf(HandwritingNet.SCRIPT, "LATIN", "CYRILLIC", "ARABIC", "JAPANESE",
            HandwritingNet.KOREAN_L, HandwritingNet.KOREAN_V, HandwritingNet.KOREAN_T)))
        assertEquals(InkScript.entries.size, n.head(HandwritingNet.SCRIPT)!!.size)
        // her başta "karakter değil" sınıfı (Korecede ilk ünsüz başında)
        for (h in listOf("LATIN", "CYRILLIC", "ARABIC", "JAPANESE", HandwritingNet.KOREAN_L)) assertTrue(h, n.head(h)!!.junk >= 0)
        assertEquals(19 + 2, n.head(HandwritingNet.KOREAN_L)!!.size)
        assertEquals(21 + 1, n.head(HandwritingNet.KOREAN_V)!!.size)
        assertEquals(27 + 1, n.head(HandwritingNet.KOREAN_T)!!.size)
        // Latin başı mevcut çözücünün gövde etiketlerini kapsar
        val labels = Glyphs.classes.flatMap { it.shapes }.toSet()
        assertTrue(labels.all { n.head("LATIN")!!.classOf(it) >= 0 })
        // biçimi aynı karakterler tek sınıf
        val jp = n.head("JAPANESE")!!
        assertEquals(jp.classOf("ロ"), jp.classOf("口")); assertEquals(jp.classOf("や"), jp.classOf("ゃ"))
        val cy = n.head("CYRILLIC")!!
        assertEquals(cy.classOf("к"), cy.classOf("К")); assertTrue(cy.classOf("а") != cy.classOf("А"))
        // kodlama gidiş-dönüş, bozuk dosya reddi
        val bytes = n.encode()
        assertTrue(bytes.contentEquals(HandwritingNet::class.java.getResourceAsStream(HandwritingNet.RESOURCE)!!.readBytes()))
        val back = HandwritingNet.decode(bytes)!!
        val x = TextFeatures.extract(listOf(Poly(floatArrayOf(0f, 10f, 20f), floatArrayOf(0f, 20f, 0f))))
        assertTrue(n.probs(n.hidden(x), "LATIN")!!.contentEquals(back.probs(back.hidden(x), "LATIN")!!))
        assertNull(HandwritingNet.decode(bytes.copyOf(bytes.size / 2)))
        assertNull(HandwritingNet.decode(ByteArray(16)))
        println("yazı ağı: ${bytes.size} bayt, ${n.parameters} parametre, başlar ${n.heads.map { "${it.key}=${it.value.size}" }}")
    }

    @Test fun remoteVersionRules() {
        val bytes = net.encode()
        try {
            assertTrue(HandwritingNet.installRemote(HandwritingNet.VERSION + 1, bytes))
            assertEquals(HandwritingNet.VERSION + 1, HandwritingNet.current().second)
            assertTrue(!HandwritingNet.installRemote(HandwritingNet.VERSION + 2, byteArrayOf(1, 2)))
            assertEquals("bozuk ağ kurulu ağı değiştirmez", HandwritingNet.VERSION + 1, HandwritingNet.current().second)
            assertTrue(HandwritingNet.installRemote(HandwritingNet.VERSION, ByteArray(0)))
            assertEquals(HandwritingNet.VERSION, HandwritingNet.current().second)
        } finally {
            HandwritingNet.installRemote(HandwritingNet.VERSION, ByteArray(0))
        }
    }

    @Test fun languageHints() {
        assertEquals(InkScript.LATIN, InkScript.fromHint("tr")); assertEquals(InkScript.LATIN, InkScript.fromHint("en-US"))
        assertEquals(InkScript.CYRILLIC, InkScript.fromHint("ru")); assertEquals(InkScript.ARABIC, InkScript.fromHint("ar"))
        assertEquals(InkScript.JAPANESE, InkScript.fromHint("ja_JP")); assertEquals(InkScript.KOREAN, InkScript.fromHint("ko"))
        assertEquals(InkScript.KOREAN, InkScript.fromHint("KOREAN"))
        assertNull("Çince desteklenmez: kendiliğinden bulunur", InkScript.fromHint("zh"))
        assertNull(InkScript.fromHint(null)); assertNull(InkScript.fromHint(""))
    }

    /** Tek karakter doğruluğu (yazı sistemi biliniyor), ayrılmış tohumlarla. */
    @Test fun heldOutCharacterAccuracy() {
        val min = mapOf(InkScript.LATIN to 0.92f, InkScript.CYRILLIC to 0.96f, InkScript.ARABIC to 0.94f, InkScript.JAPANESE to 0.95f)
        for ((s, m) in min) {
            assumeFonts(s)
            val per = if (s == InkScript.JAPANESE) 2 else 4
            val sc = TextNetEval.chars(net, s, fontsFor(s), per, seed = TextNetEval.EVAL_SEED + 13)
            println("$s karakter: $sc")
            assertTrue("$s: $sc", sc.top1 >= m * sc.n)
        }
        assumeFonts(InkScript.KOREAN)
        val ko = TextNetEval.korean(net, fontsFor(InkScript.KOREAN), 400, heldOut = false, seed = TextNetEval.EVAL_SEED + 13)
        println("KOREAN hece: $ko")
        assertTrue("KOREAN: $ko", ko.top1 >= 0.85f * ko.n)
        val held = TextNetEval.korean(net, fontsFor(InkScript.KOREAN), 300, heldOut = true, seed = TextNetEval.EVAL_SEED + 13)
        println("KOREAN görülmemiş hece: $held")
        assertTrue("KOREAN görülmemiş: $held", held.top1 >= 0.75f * held.n)
    }

    /** Sözcük okuma (dil ipucuyla): yazı sistemi başına tam okuma ve karakter doğruluğu alt sınırları. */
    @Test fun scriptWordsWithHint() {
        val min = mapOf(
            InkScript.CYRILLIC to (0.6f to 0.8f), InkScript.ARABIC to (0.75f to 0.9f),
            InkScript.JAPANESE to (0.8f to 0.88f), InkScript.KOREAN to (0.75f to 0.85f),
        )
        for ((s, m) in min) {
            assumeFonts(s)
            val sc = TextNetWords.score(s, fontsFor(s), 1, hint = true, seed = TextNetEval.EVAL_SEED + 91_000_000L)
            println("$s sözcük (ipuçlu): $sc  ${sc.fails.take(8)}")
            assertTrue("$s tam: $sc", sc.exactPct() >= m.first * 100f)
            assertTrue("$s karakter: $sc", sc.charPct() >= m.second * 100f)
        }
    }

    /** İpucu olmadan: Latin dizgiler Latin kalır, Latin dışı satırların çoğu doğru yazı sistemine gider. */
    @Test fun scriptDetection() {
        val rnd = kotlin.random.Random(5)
        var latin = 0; var n = 0
        for (s in listOf("2026", "x+1=5", "Ahmet", "çiçek", "Güneş", "kitap", "3×4=12", "masa", "Evet!", "İzmir")) repeat(6) {
            val ink = InkSynth.sample(s, rnd)
            n++
            if (Handwriting.detectScript(ink) == InkScript.LATIN) latin++
        }
        println("Latin bulunan: $latin/$n")
        assertTrue("Latin: $latin/$n", latin >= 0.97f * n)
        // ipucusuz tek Latin karakter (ö/ة, ç/ء, d/니 gibi benzerlikler) Latin kalır: eski davranış
        var single = 0; var sn = 0
        for (c in "abcçdefgğhıijklmnoöpqrsştuüvwxyzÇĞÖŞÜİ0123456789+=%") repeat(2) {
            sn++
            if (Handwriting.detectScript(InkSynth.sample(c.toString(), rnd)) == InkScript.LATIN) single++
        }
        println("tek Latin karakter Latin kalan: $single/$sn")
        assertTrue("tek karakter: $single/$sn", single >= 0.98f * sn)
        for (s in listOf(InkScript.ARABIC, InkScript.JAPANESE, InkScript.KOREAN)) {
            if (fontsFor(s).size < 2) continue
            val sc = TextNetWords.score(s, fontsFor(s), 1, hint = false, seed = TextNetEval.EVAL_SEED + 92_000_000L)
            println("$s ipucusuz: $sc")
            assertTrue("$s bulma: $sc", sc.scriptPct() >= 75f)
        }
    }

    /** Japonca/Korece çok çizgili karakterler ipucuyla yazı sayılır; Latin kuralları değişmez. */
    @Test fun looksLikeWritingForCjk() {
        assumeFonts(InkScript.JAPANESE)
        val f = fontsFor(InkScript.JAPANESE)
        var ok = 0
        for (k in 0 until 10) {
            val ln = TextNetData.line(InkScript.JAPANESE, listOf("学", "校", "森", "林"), f, Random(k.toLong()))!!
            val ink = GlyphInk.toInk(ln.strokes)
            if (Handwriting.looksLikeWriting(ink, "ja")) ok++
        }
        assertTrue("ja: $ok/10", ok >= 9)
        // 41 paralel düz çizgi (tarama) ipucuyla da yazı değil
        val hatch = (0 until 41).map { i -> InkStroke(listOf(InkPoint(i * 10f, 0f), InkPoint(i * 10f, 20f))) }
        assertTrue(!Handwriting.looksLikeWriting(hatch, "ja") && !Handwriting.looksLikeWriting(hatch, "ko"))
        assertNull(Handwriting.recognize(hatch, language = "ko"))
        // tek çizgilik dikdörtgen çerçeve hâlâ çizim
        val box = listOf(InkStroke(listOf(InkPoint(0f, 0f), InkPoint(100f, 0f), InkPoint(100f, 80f), InkPoint(0f, 80f), InkPoint(0f, 0f))))
        assertTrue(!Handwriting.looksLikeWriting(box, "ja"))
    }

    /** Latin dışı sonuçlar da API sözleşmesine uyar (çizgi sıra numaraları, adaylar, alternatifler). */
    @Test fun recognitionContract() {
        for (s in listOf(InkScript.CYRILLIC, InkScript.ARABIC, InkScript.JAPANESE, InkScript.KOREAN)) {
            if (fontsFor(s).size < 2) continue
            val ln = TextNetData.line(s, TextNetWords.splitChars(TextNetWords.words.getValue(s)[0]), fontsFor(s), Random(3))!!
            val ink = GlyphInk.toInk(ln.strokes)
            val r = Handwriting.analyze(ink, InkMode.MIXED, true, TextNetWords.hints[s])
            assertNotNull("$s", r)
            assertEquals(ink.indices.toList(), r!!.chars.flatMap { it.strokeIndices }.sorted())
            assertTrue(r.confidence in 0f..1f)
            assertTrue(r.alternatives.size <= 3 && r.text !in r.alternatives)
            r.chars.forEach { c -> assertTrue(c.candidates.isNotEmpty() && c.candidates.all { it.score in 0f..1f }) }
            assertEquals(r.text.replace(" ", ""), r.chars.joinToString("") { it.candidates[0].text })
        }
    }

    /** Karakter başına süre (öznitelik + ağ) ve Latin dizgide ağın eklediği süre. */
    @Test fun speed() {
        val r = Random(5)
        val inputs = List(200) { k ->
            val s = if (k % 2 == 0) InkScript.LATIN else InkScript.JAPANESE
            val fonts = fontsFor(s).ifEmpty { listOf("app:kalam") }
            TextNetData.charSample(s, r.nextInt(TextNetData.classes(s).size), fonts, r)?.let { GlyphInk.toPolys(it.strokes) }
                ?: listOf(Poly(floatArrayOf(0f, 10f, 20f), floatArrayOf(0f, 20f, 0f)))
        }
        repeat(3) { inputs.forEach { net.probs(net.hidden(TextFeatures.extract(it)), "JAPANESE") } }
        val ms = (0 until 5).minOf {
            val t0 = System.nanoTime()
            inputs.forEach { net.probs(net.hidden(TextFeatures.extract(it)), "JAPANESE") }
            (System.nanoTime() - t0) / 1e6 / inputs.size
        }
        println("yazı ağı: karakter başına %.3f ms (öznitelik + ağ)".format(ms))
        assertTrue("$ms ms", ms < 3.0)
    }
}
