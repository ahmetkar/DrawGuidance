package com.karapp.drawguidance.content.ink

import com.karapp.drawguidance.content.ink.GlyphInk.Pt
import com.karapp.drawguidance.content.ink.GlyphInk.Skeleton
import java.util.Random
import kotlin.math.max

/**
 * v37 yazı ağının sınıfları ve yapay örnekleri (eğitim: TextNetTraining; sınama: HandwritingNetTest, ScriptDecoderTest).
 *
 * Her yazı sisteminin sınıfı: çıktı karakterleri ([Cls.outputs], ağ dosyasına yazılır) ve yazı tiplerinden çizilecek
 * metinler ([Cls.renders]). Latin sınıfları mevcut çözücünün gövde şekli etiketleridir; onlara tanıyıcının kendi
 * taslakları ([Glyphs.sketches]) da katılır. Testlerin üreteci InkSynth eğitimde KULLANILMAZ.
 *
 * Yazı tipleri: sistemdekiler (DejaVu, Free*, Liberation, Inter, Carlito, IPAGothic, WenQuanYi Zen Hei, Unifont) ve
 * uygulamanın yazı tipleri. Ayrılan yazı tipleri ([HELD_OUT]) eğitimde kullanılmaz, yalnız sınamada (görülmemiş biçim).
 */
internal object TextNetData {

    class Render(val text: String, val dropMarks: Boolean = false)

    class Cls(val outputs: List<String>, val renders: List<Render>, val sketches: List<String> = emptyList())

    private const val FREE = "file:/usr/share/fonts/truetype/freefont/"

    val LATIN_FONTS = listOf(
        "DejaVu Sans", "Liberation Sans", "FreeSans", "Inter", "Carlito", "${FREE}FreeSansOblique.ttf",
        "app:kalam", "app:caveat", "app:patrick_hand", "app:shantell_sans", "app:comic_relief", "app:mansalva",
        "app:nunito", "app:poppins", "app:quicksand", "app:comfortaa", "app:montserrat", "app:raleway", "app:grandstander",
        "app:righteous", "app:boogaloo",
    )
    /**
     * Kiril: tırnaksız (sans) yazı tipleri ve el yazısına yakın italik biçimler (FreeSerif İtalik: т, и, д, п el yazısı
     * biçimleri). Tırnaklı (serif) düz yazı tipleri kullanılmaz: tırnaklar elle yazılmaz ve iskelette fazladan çizgi olur.
     */
    val CYRILLIC_FONTS = listOf(
        "DejaVu Sans", "DejaVu Sans Mono", "FreeSans", "Liberation Sans", "Inter", "Carlito", "IPAGothic",
        "WenQuanYi Zen Hei", "${FREE}FreeSerifItalic.ttf", "${FREE}FreeSansOblique.ttf",
    )
    val ARABIC_FONTS = listOf("DejaVu Sans", "DejaVu Sans Mono", "FreeSerif", "FreeMono", "Unifont")
    val JAPANESE_FONTS = listOf("IPAGothic", "IPAPGothic", "WenQuanYi Zen Hei", "Unifont")
    val KOREAN_FONTS = listOf("WenQuanYi Zen Hei", "Unifont")

    /** Eğitimde kullanılmayan, yalnız sınamada çizilen yazı tipleri (görülmemiş biçim ölçümü). */
    val HELD_OUT: Map<InkScript, String> = mapOf(InkScript.LATIN to "app:kalam", InkScript.CYRILLIC to "Carlito")

    fun fonts(script: InkScript): List<String> = when (script) {
        InkScript.LATIN -> LATIN_FONTS
        InkScript.CYRILLIC -> CYRILLIC_FONTS
        InkScript.ARABIC -> ARABIC_FONTS
        InkScript.JAPANESE -> JAPANESE_FONTS
        InkScript.KOREAN -> KOREAN_FONTS
    }

    fun trainFonts(script: InkScript) = fonts(script).filter { it != HELD_OUT[script] }

    // ---------------------------------------------------------------- sınıflar

    private fun r(vararg t: String) = t.map { Render(it) }
    private fun rd(vararg t: String) = t.map { Render(it, dropMarks = true) }

    /** Latin: gövde şekli etiketleri (Glyphs.sketches sırası) ve yazı tiplerinden çizilen karakterler. */
    val latin: List<Cls> by lazy {
        val fontChars: Map<String, List<Render>> = mapOf(
            "O" to r("o", "O", "0"), "|" to r("l", "I") + rd("i", "!"), "1" to r("1"),
            "2" to r("2"), "3" to r("3"), "4" to r("4"), "5" to r("5"), "6" to r("6"), "7" to r("7"), "8" to r("8"), "9" to r("9"),
            "a" to r("a"), "b" to r("b"), "c" to r("c", "C"), "d" to r("d"), "e" to r("e"), "f" to r("f"), "g" to r("g"),
            "h" to r("h"), "Jh" to (r("J") + rd("j")), "k" to r("k"), "m" to r("m"), "n" to r("n"), "p" to r("p", "P"),
            "q" to r("q"), "r" to r("r"), "s" to r("s", "S"), "t" to r("t"), "u" to r("u", "U"), "v" to r("v", "V"),
            "w" to r("w", "W"), "x" to r("x", "X", "×"), "y" to r("y"), "z" to r("z", "Z"),
            "A" to r("A"), "B" to r("B"), "D" to r("D"), "E" to r("E"), "F" to r("F"), "G" to r("G"), "H" to r("H"),
            "K" to r("K"), "L" to r("L"), "M" to r("M"), "N" to r("N"), "Q" to r("Q"), "R" to r("R"), "T" to r("T"), "Y" to r("Y"),
            "+" to r("+"), "-" to r("−", "-"), "=" to r("="), "/" to r("/"), "(" to r("("), ")" to r(")"), "," to r(","),
            "?h" to rd("?"), "<" to r("<"), ">" to r(">"), "%" to r("%"),
        )
        val labels = Glyphs.sketches.map { it.first }.distinct()
        labels.map { l -> Cls(listOf(l), fontChars[l].orEmpty(), Glyphs.sketches.filter { it.first == l }.map { it.second }) }
    }

    /** Rusça: biçimi aynı küçük/büyük harfler tek sınıf (ilk çıktı küçük harf). */
    val cyrillic: List<Cls> by lazy {
        val single = "аАбБеЕёЁ"
        val pairs = "вгджзийклмнопрстуфхцчшщъыьэюя"
        val out = ArrayList<Cls>()
        for (c in single) out += Cls(listOf(c.toString()), r(c.toString()))
        for (c in pairs) {
            val u = c.uppercaseChar()
            out += Cls(listOf(c.toString(), u.toString()), r(c.toString(), u.toString()))
        }
        out
    }

    /** Arapça: yalın (ayrık) harfler, hemze biçimleri ve Hint-Arap rakamları (١ elif ile aynı sınıf). */
    val arabic: List<Cls> by lazy {
        val out = ArrayList<Cls>()
        out += Cls(listOf("ا", "١"), r("ا", "١"))
        for (c in "بتثجحخدذرزسشصضطظعغفقكلمنهوي" + "ءأإآؤئةى") out += Cls(listOf(c.toString()), r(c.toString()))
        for (c in "٠٢٣٤٥٦٧٨٩") out += Cls(listOf(c.toString()), r(c.toString()))
        out
    }

    const val HIRAGANA = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん" +
        "がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽ"
    const val KATAKANA = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン" +
        "ガギグゲゴザジズゼゾダヂヅデドバビブベボヴパピプペポー"
    /** 1. sınıf kanjileri (80) ve sık kullanılan birkaç kanji. */
    const val KANJI = "一右雨円王音下火花貝学気九休玉金空月犬見五口校左三山子四糸字耳七車手十出女小上森人水正生青夕石赤千川先早草足村" +
        "大男竹中虫町天田土二日入年白八百文木本名目立力林六今時分前後私"
    private val SMALL = mapOf('ぁ' to 'あ', 'ぃ' to 'い', 'ぅ' to 'う', 'ぇ' to 'え', 'ぉ' to 'お', 'っ' to 'つ', 'ゃ' to 'や', 'ゅ' to 'ゆ', 'ょ' to 'よ',
        'ァ' to 'ア', 'ィ' to 'イ', 'ゥ' to 'ウ', 'ェ' to 'エ', 'ォ' to 'オ', 'ッ' to 'ツ', 'ャ' to 'ヤ', 'ュ' to 'ユ', 'ョ' to 'ヨ')
    /** Biçimi aynı sayılan karakterler (ilk karakterin sınıfına katılır). */
    private val SAME = mapOf('ヘ' to 'へ', 'ベ' to 'べ', 'ペ' to 'ぺ', '一' to 'ー', '二' to 'ニ', '口' to 'ロ', '力' to 'カ', '八' to 'ハ', '夕' to 'タ')

    /** Japonca: hiragana, katakana, kanji; küçük kana ve biçimi aynı karakterler tek sınıf. */
    val japanese: List<Cls> by lazy {
        val order = ArrayList<Char>()
        val outs = LinkedHashMap<Char, MutableList<String>>()
        for (c in HIRAGANA + KATAKANA + KANJI) {
            val key = SAME[c] ?: c
            if (key !in outs) { outs[key] = mutableListOf(); order += key }
            outs.getValue(key) += c.toString()
        }
        for ((small, big) in SMALL) outs[big]?.add(small.toString())
        order.map { k -> val o = outs.getValue(k); Cls(o, r(*o.toTypedArray())) }
    }

    // ---------------------------------------------------------------- Korece

    const val HANGUL_L = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    const val HANGUL_V = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    const val HANGUL_T = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    /** Hece kimliği (0..11171) → (ilk, ünlü, son; son -1 = yok). */
    fun syllableParts(id: Int): Triple<Int, Int, Int> = Triple(id / (21 * 28), (id / 28) % 21, id % 28 - 1)

    fun syllable(l: Int, v: Int, t: Int): Char = (0xAC00 + (l * 21 + v) * 28 + (t + 1)).toChar()

    /**
     * Eğitimde hiç gösterilmeyen heceler (%10; bileşenlerden genelleme ölçümü). Karma ile seçilir: her ilk ünsüz, ünlü
     * ve son ünsüz eğitimde başka hecelerde görülür (yalnız bu birleşimleri görülmez).
     */
    fun heldOutSyllable(id: Int) = ((id.toLong() * 2654435761L) ushr 13) % 10 == 3L

    /** Sık kullanılan heceler (sınamada ayrıca ölçülür). */
    const val COMMON_KO = "이다의는에고을를한기서사리대도로시자수정하가지나아어요것들그있니게원우일만해부성보면내주모소" +
        "인상제전관학생국과여중동방문무경장구신마저오라말계연개실명비시회입위안진발법선물"

    // ---------------------------------------------------------------- örnekler

    class Sample(val strokes: List<List<Pt>>, val script: InkScript, val targets: List<Pair<String, Int>>)

    private fun sketchSkeleton(src: String): Skeleton {
        val polys = Sketch.renderAll(src, 6)
        val strokes = polys.map { p -> (0 until p.n).map { Pt(p.x[it] * 0.007f, (p.y[it] - 100f) * 0.007f) } }
        return Skeleton(strokes, strokes)
    }

    private val sketchCache = java.util.concurrent.ConcurrentHashMap<String, Skeleton>()

    /** Sınıfın iskeletlerinden biri (yazı tipi ya da taslak); çizilemiyorsa null. */
    fun skeleton(cls: Cls, fonts: List<String>, r: Random): Skeleton? {
        val nSketch = cls.sketches.size
        val useSketch = nSketch > 0 && (cls.renders.isEmpty() || r.nextFloat() < 0.35f)
        if (useSketch) {
            val src = cls.sketches[r.nextInt(nSketch)]
            return sketchCache.getOrPut(src) { sketchSkeleton(src) }
        }
        if (cls.renders.isEmpty()) return null
        repeat(6) {
            val rd = cls.renders[r.nextInt(cls.renders.size)]
            val f = fonts[r.nextInt(fonts.size)]
            GlyphInk.skeleton(f, rd.text, rd.dropMarks)?.let { return it }
        }
        return null
    }

    fun amount(r: Random) = 0.5f + r.nextFloat() * 0.9f

    fun classes(script: InkScript): List<Cls> = when (script) {
        InkScript.LATIN -> latin
        InkScript.CYRILLIC -> cyrillic
        InkScript.ARABIC -> arabic
        InkScript.JAPANESE -> japanese
        InkScript.KOREAN -> emptyList()
    }

    /** Tek karakter örneği ([script] başının [ci] sınıfı). */
    fun charSample(script: InkScript, ci: Int, fonts: List<String>, r: Random, amount: Float = amount(r)): Sample? {
        val cls = classes(script)[ci]
        val sk = skeleton(cls, fonts, r) ?: return null
        val strokes = GlyphInk.handwrite(sk, r, amount)
        return Sample(strokes, script, listOf(HandwritingNet.SCRIPT to script.ordinal, script.name to ci))
    }

    /** Korece örnek: hece ya da tek başına jamo. [allowHeldOut] false ise ayrılan heceler seçilmez. */
    fun koreanSample(r: Random, fonts: List<String>, allowHeldOut: Boolean, onlyHeldOut: Boolean = false, amount: Float = amount(r)): Sample? {
        val nL = HANGUL_L.length; val nV = HANGUL_V.length; val nT = HANGUL_T.length
        val text: String
        val l: Int; val v: Int; val t: Int
        if (!onlyHeldOut && r.nextFloat() < 0.14f) {
            if (r.nextBoolean()) { l = r.nextInt(nL); v = -1; t = -1; text = HANGUL_L[l].toString() }
            else { l = -1; v = r.nextInt(nV); t = -1; text = HANGUL_V[v].toString() }
        } else {
            var id: Int
            var guard = 0
            do {
                val ll = if (r.nextFloat() < 0.15f) 11 else r.nextInt(nL) // ㅇ sık
                val vv = r.nextInt(nV)
                val tt = if (r.nextFloat() < 0.45f) -1 else if (r.nextFloat() < 0.4f) "ㄴㄹㅇㄱㅁ".let { HANGUL_T.indexOf(it[r.nextInt(it.length)]) } else r.nextInt(nT)
                id = (ll * 21 + vv) * 28 + (tt + 1)
                guard++
            } while (guard < 100 && ((!allowHeldOut && heldOutSyllable(id)) || (onlyHeldOut && !heldOutSyllable(id))))
            val p = syllableParts(id)
            l = p.first; v = p.second; t = p.third
            text = syllable(l, v, t).toString()
        }
        val f = fonts[r.nextInt(fonts.size)]
        val sk = GlyphInk.skeleton(f, text) ?: return null
        val strokes = GlyphInk.handwrite(sk, r, amount)
        return Sample(
            strokes, InkScript.KOREAN,
            listOf(
                HandwritingNet.SCRIPT to InkScript.KOREAN.ordinal,
                HandwritingNet.KOREAN_L to (if (l < 0) nL else l),
                HandwritingNet.KOREAN_V to (if (v < 0) nV else v),
                HandwritingNet.KOREAN_T to (if (t < 0) nT else t),
            ),
        )
    }

    /** Korece başların çıktıları: bileşenler + "yok" (+ ilk başta "karakter değil"). */
    fun koreanOutputs(): List<Pair<String, List<List<String>>>> = listOf(
        HandwritingNet.KOREAN_L to (HANGUL_L.map { listOf(it.toString()) } + listOf(listOf(HandwritingNet.NONE)) + listOf(emptyList())),
        HandwritingNet.KOREAN_V to (HANGUL_V.map { listOf(it.toString()) } + listOf(listOf(HandwritingNet.NONE))),
        HandwritingNet.KOREAN_T to (HANGUL_T.map { listOf(it.toString()) } + listOf(listOf(HandwritingNet.NONE))),
    )

    /** Rastgele bir karakterin çizgileri (karakter değil örnekleri ve sözcük kurma için). */
    fun anyChar(script: InkScript, fonts: List<String>, r: Random): List<List<Pt>>? = if (script == InkScript.KOREAN)
        koreanSample(r, fonts, allowHeldOut = false)?.strokes
    else charSample(script, r.nextInt(classes(script).size), fonts, r)?.strokes

    /** "Karakter değil" örneği: yan yana iki karakter ya da (Japonca) dikey kesilmiş yarım karakter. */
    fun junkSample(script: InkScript, fonts: List<String>, r: Random): Sample? {
        val junkHead = if (script == InkScript.KOREAN) HandwritingNet.KOREAN_L else script.name
        val junkIndex = if (script == InkScript.KOREAN) HANGUL_L.length + 1 else classes(script).size
        val strokes: List<List<Pt>>
        if (script == InkScript.JAPANESE && r.nextFloat() < 0.4f) {
            val a = anyChar(script, fonts, r) ?: return null
            if (a.size < 4) return null
            val all = a.flatten()
            val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
            val cut = minX + (maxX - minX) * (0.35f + 0.3f * r.nextFloat())
            val left = r.nextBoolean()
            val keep = a.filter { st -> val cx = st.map { it.x }.average(); if (left) cx < cut else cx >= cut }
            if (keep.isEmpty() || keep.size == a.size) return null
            val ink = keep.sumOf { it.size }.toFloat() / all.size
            if (ink < 0.2f || ink > 0.8f) return null
            strokes = keep
        } else {
            val a = anyChar(script, fonts, r) ?: return null
            val b = anyChar(script, fonts, r) ?: return null
            val aMax = a.flatten().maxOf { it.x }; val bMin = b.flatten().minOf { it.x }
            val gap = 0.02f + 0.12f * r.nextFloat()
            val dx = aMax - bMin + gap
            val dy = (r.nextFloat() - 0.5f) * 0.06f
            strokes = a + b.map { st -> st.map { Pt(it.x + dx, it.y + dy) } }
        }
        return Sample(strokes, script, listOf(HandwritingNet.SCRIPT to script.ordinal, junkHead to junkIndex))
    }

    // ---------------------------------------------------------------- sözcükler (çözücü sınaması)

    /** Sözcük ya da satır: karakterler sırayla yerleştirilir (Arapça sağdan sola), sözcük arası geniş boşluk. */
    class Line(val text: String, val strokes: List<List<Pt>>)

    /**
     * [chars] karakter dizisi (boşluk = sözcük arası) için yapay el yazısı satırı; karakterler [pick] ile seçilen
     * sınıftan çizilir. Karakter yüksekliği taban çizgisine göre yazı tipinden gelir.
     */
    fun line(script: InkScript, chars: List<String>, fonts: List<String>, r: Random, amount: Float = 0.8f): Line? {
        val font = fonts[r.nextInt(fonts.size)]
        val parts = ArrayList<List<List<Pt>>>()
        // sağdan sola yazıda sayılar soldan sağa yazılır: rakam dizileri yerleştirme sırasında ters çevrilir
        val order = if (!script.rtl) chars else {
            val out = ArrayList<String>()
            var i = 0
            while (i < chars.size) {
                if (chars[i].all { it.isDigit() } && chars[i] != " ") {
                    var j = i
                    while (j < chars.size && chars[j] != " " && chars[j].all { it.isDigit() }) j++
                    out += chars.subList(i, j).reversed()
                    i = j
                } else out += chars[i++]
            }
            out
        }
        for (c in order) {
            if (c == " ") { parts += emptyList<List<Pt>>(); continue }
            val sk = GlyphInk.skeleton(font, c) ?: return null
            parts += GlyphInk.handwrite(sk, r, amount)
        }
        val out = ArrayList<List<Pt>>()
        val cjk = script == InkScript.JAPANESE || script == InkScript.KOREAN
        val rtl = script.rtl
        var cursor = 0f
        val slope = (r.nextFloat() - 0.5f) * 0.06f
        for (p in parts) {
            if (p.isEmpty()) { cursor += if (cjk) 0.45f else 0.38f + 0.15f * r.nextFloat(); continue }
            val all = p.flatten()
            val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
            val gap = if (cjk) 0.06f + 0.1f * r.nextFloat() else 0.05f + 0.1f * r.nextFloat()
            val w = max(maxX - minX, 0.05f)
            val dy = (r.nextFloat() - 0.5f) * 0.04f
            val x0 = if (rtl) -(cursor + w) else cursor
            val dx = x0 - minX
            out += p.map { st -> st.map { Pt(it.x + dx, it.y + dy + slope * (it.x + dx)) } }
            cursor += w + gap
        }
        return Line(chars.joinToString(""), out)
    }
}
