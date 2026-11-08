package com.karapp.drawguidance.content.ink

/** Harfin ana gövdesine eklenen küçük işaretler (nokta, iki nokta, kısa çizgi, çengel). */
internal enum class Marks { NONE, DOT_ABOVE, TWO_DOTS_ABOVE, BREVE_ABOVE, CEDILLA_BELOW, DOT_BELOW, DOTS_ABOVE_BELOW, ONLY_DOT, ONLY_COLON, ONLY_TICK }

internal enum class Cat { DIGIT, LOWER, UPPER, SYMBOL, PUNCT, BRACKET }

/**
 * Çıktı karakteri: [shapes] kabul ettiği gövde şekil etiketleri, [marks] gereken işaretler, [top]/[bottom] taslak kutusunun
 * (y 0..100) satırdaki yeri (0 = büyük harf üstü, 1 = taban çizgisi, 1.45 = kuyruk altı).
 */
internal class CharClass(
    val text: String,
    val shapes: List<String>,
    val marks: Marks,
    val top: Float,
    val bottom: Float,
    val cat: Cat,
    val prior: Float,
    val sigma: Float = 0.12f,
) {
    /** Gövdenin satırdaki sıkı bandı (taslakların gerçek y aralığından). */
    var bandTop = top
    var bandBottom = bottom
}

/**
 * Tanıyıcının şablonları: gövde şekilleri (etiket → taslaklar) ve çıktı karakter tablosu. Taslaklar el yazısının yaygın
 * biçimleridir (açık/kapalı 4, çizgili/çizgisiz 7, tek/çift katlı a, bitişik l ...). Türkçe harfler gövde + işaret
 * olarak kurulur (ç = c + çengel, ğ = g + kısa yay, ü = u + iki nokta, i = ı + nokta, İ = I + nokta).
 */
internal object Glyphs {

    /** etiket → taslaklar (glif kutusu: y 0..100). */
    val sketches: List<Pair<String, String>> = listOf(
        // --- daire ailesi: o O 0 ö Ö
        "O" to "@50,50,50,50,-90,-450",
        "O" to "@35,50,33,50,-70,-430",
        "O" to "@45,50,45,50,-120,240",
        "O" to "@32,50,30,50,-100,-475",
        // --- dikey çizgi: 1 l I ı !
        "|" to "50,0 50,100",
        "|" to "50,0 53,50 49,100",
        // --- rakamlar
        "1" to "*20,28 *50,0 50,100",
        "1" to "*36,12 *50,0 50,100",
        "1" to "*30,16 *50,0 *50,100 | 20,100 80,100",
        "2" to "5,25 22,4 45,0 68,12 70,35 55,58 *5,100 *75,100",
        "2" to "3,30 15,5 40,0 65,10 68,35 45,62 *3,100 30,92 55,96 75,92",
        "2" to "8,20 30,0 58,5 68,28 55,52 20,82 *2,100 *72,98",
        "3" to "5,12 30,0 58,10 60,28 42,45 *25,48 45,50 62,65 62,86 38,100 5,90",
        "3" to "*5,0 *60,0 *25,40 50,48 64,70 50,95 22,100 3,88",
        "3" to "8,10 32,0 55,12 50,35 *28,48 58,60 60,85 35,100 8,92",
        "4" to "*38,0 *0,65 *72,65 | 52,15 52,100",
        "4" to "*52,100 *52,0 *0,66 *72,66",
        "4" to "*8,0 *5,60 *68,60 | 52,0 52,100",
        "5" to "*12,0 *8,45 30,38 55,45 62,70 50,95 25,100 3,90 | 12,0 62,0",
        "5" to "*62,0 *12,0 *8,45 30,38 55,45 62,70 50,95 25,100 3,90",
        "5" to "*10,2 *6,42 35,35 58,48 62,72 48,94 22,100 2,88 | 10,2 58,0",
        "6" to "55,5 35,0 15,15 3,50 5,80 25,100 50,95 60,72 45,55 20,55 5,70",
        "6" to "50,0 20,35 5,70 15,95 38,100 55,85 55,65 38,52 15,58 6,72",
        "7" to "*0,2 *60,0 *20,100",
        "7" to "*0,2 *60,0 *20,100 | 12,52 55,50",
        "7" to "*0,16 *2,0 *62,2 *22,100",
        "7" to "*0,2 *62,0 40,45 30,100",
        "8" to "52,10 30,0 8,15 12,35 30,48 52,65 56,85 35,100 10,92 6,72 30,48 50,30 50,12 38,2",
        "8" to "@30,24,22,24,-90,-450 | @30,74,28,26,-90,-450",
        "8" to "30,48 10,30 12,8 32,0 52,10 50,32 30,48 8,68 12,92 32,100 54,90 54,68 30,48",
        "9" to "55,20 32,0 6,15 8,38 30,48 52,38 *58,8 *55,100",
        "9" to "55,20 32,0 6,15 8,38 30,48 52,38 *58,8 58,70 45,95 20,100",
        "9" to "@30,24,26,24,0,360 | 57,5 55,100",
        // --- küçük harfler
        "a" to "80,18 50,0 12,15 0,55 20,95 50,98 78,65 *83,5 83,90 95,100",
        "a" to "@42,50,40,50,-20,-360 | 84,2 84,100",
        "a" to "82,12 55,0 20,8 2,50 18,92 50,100 80,70 *84,0 *86,100",
        "a" to "12,18 40,0 70,8 80,30 *80,100 | 80,48 35,45 5,70 22,98 55,95 80,72",
        "b" to "*10,0 *10,100 10,70 35,48 70,48 88,72 75,97 40,100 10,88",
        "b" to "10,0 10,100 | 10,62 40,45 75,50 88,75 70,98 35,100 10,90",
        "c" to "85,15 55,0 20,8 0,50 20,92 55,100 85,85",
        "c" to "@50,50,48,50,-40,-320",
        "d" to "80,58 50,45 12,55 0,75 15,97 45,100 78,85 *85,0 *85,100",
        "d" to "@42,72,40,28,-20,-360 | 85,0 85,100",
        "e" to "5,52 60,52 92,45 75,10 45,0 12,15 0,55 15,90 50,100 90,85",
        "e" to "0,70 40,60 70,40 70,15 45,0 15,15 5,55 20,90 55,100 90,80",
        "f" to "85,8 65,0 45,5 38,25 *38,100 | 5,40 75,40",
        "f" to "75,3 52,0 38,15 36,100 | 8,42 68,40",
        "g" to "80,8 50,0 12,8 3,25 20,45 50,48 78,30 *82,3 82,80 65,98 30,100 8,85",
        "g" to "@42,24,38,24,-20,-360 | 82,0 82,80 65,98 30,100 8,85",
        "g" to "80,8 50,0 12,8 3,25 20,45 50,48 78,30 *82,3 82,70 70,95 45,100 30,88 45,72 85,62",
        "h" to "*10,0 *10,100 10,70 35,47 65,45 82,60 *84,100",
        "h" to "10,0 10,100 | 10,62 35,45 65,45 82,62 84,100",
        "ı" to "50,0 *50,82 60,98 78,95",
        "Jh" to "72,0 *72,75 58,97 30,100 5,82",
        "Jh" to "72,0 72,72 60,95 35,100 12,90 5,75",
        "J" to "*72,0 *72,75 58,97 30,100 5,82 | 40,0 100,0",
        "k" to "10,0 *10,100 | 65,45 *15,72 *70,100",
        "k" to "*10,0 *10,100 *10,75 *65,45 *30,68 *72,100",
        "k" to "*10,0 *10,100 10,72 45,48 68,52 58,68 *18,74 *72,100",
        "k" to "10,0 10,100 | 66,42 12,70 | 36,58 72,100",
        "l" to "5,80 30,50 52,18 45,0 30,8 25,45 25,85 38,100 60,92",
        "l" to "40,0 *40,80 50,98 70,95",
        "m" to "*5,0 *5,100 *5,30 25,3 45,0 60,15 *62,100 *62,30 82,3 102,0 117,15 *118,100",
        "m" to "5,0 5,100 | 5,30 25,2 48,5 58,25 60,100 | 60,30 80,2 102,5 115,25 118,100",
        "m" to "*0,15 *6,2 *6,100 6,35 25,5 45,2 58,20 *60,100 60,35 80,5 100,2 115,22 *118,100",
        "n" to "*5,0 *5,100 *5,30 30,3 55,0 75,15 *78,100",
        "n" to "5,0 5,100 | 5,30 30,2 60,3 75,25 78,100",
        "n" to "*0,15 *6,2 *6,100 6,35 30,5 55,2 72,20 *76,100",
        "p" to "*8,0 *8,100 | 8,8 35,0 72,3 90,25 72,48 38,50 8,42",
        "p" to "*8,100 *8,0 38,0 72,3 90,25 72,48 38,50 *8,48",
        "p" to "*8,0 *8,100 *8,15 35,0 72,3 90,25 72,48 38,50 8,40",
        "q" to "80,10 50,0 12,8 2,25 20,48 50,50 78,35 *82,0 *82,100",
        "q" to "@42,25,40,25,-20,-360 | 82,0 *82,100 96,88",
        "r" to "*8,0 *8,100 *8,40 30,8 55,0 80,8",
        "r" to "8,0 8,100 | 8,35 30,5 55,0 78,10",
        "s" to "80,12 55,0 20,5 10,25 30,45 70,55 85,78 65,98 30,100 5,88",
        "s" to "75,8 50,0 18,8 12,28 40,45 68,58 78,80 58,100 25,98 3,85",
        "t" to "40,0 *40,85 50,100 72,95 | 5,30 80,30",
        "t" to "40,0 40,100 | 5,30 80,30",
        "u" to "5,0 5,70 22,96 50,100 78,82 *85,0 *85,100",
        "u" to "5,0 8,70 25,96 50,100 75,96 92,70 95,0",
        "v" to "*0,0 *42,100 *85,0",
        "v" to "0,0 15,55 35,95 45,100 55,90 72,45 85,0",
        "w" to "*0,0 *25,100 *55,25 *82,100 *108,0",
        "w" to "0,0 3,70 15,98 30,98 45,75 *52,30 *56,75 70,98 85,98 97,70 100,0",
        "w" to "*0,0 *20,100 *50,0 *80,100 *100,0",
        "x" to "0,0 90,100 | 90,0 0,100",
        "x" to "0,0 45,48 95,100 | 90,0 45,52 5,100",
        "x2" to "5,5 25,0 42,20 45,50 42,80 25,100 5,95 | 85,5 65,0 48,20 45,50 48,80 65,100 85,95",
        "y" to "*0,0 *45,48 | *90,0 *10,100",
        "y" to "5,0 5,30 20,48 45,50 70,40 *82,0 *82,80 65,98 35,100 15,88",
        "z" to "*5,0 *85,0 *5,100 *90,100",
        "z" to "*5,0 *85,0 *5,100 *90,100 | 25,50 70,50",
        // --- büyük harfler
        "A" to "*0,100 *40,0 *80,100 | 18,60 62,60",
        "A" to "40,0 0,100 | 40,0 80,100 | 18,62 62,62",
        "B" to "*10,0 *10,100 | *10,0 55,2 72,15 65,38 *12,48 65,55 82,75 70,95 *10,100",
        "B" to "*10,100 *10,0 55,2 72,15 65,38 *12,48 65,55 82,75 70,95 *10,100",
        "D" to "10,0 10,100 | *10,0 50,5 78,30 80,70 55,95 *10,100",
        "D" to "*10,100 *10,0 50,3 78,28 82,65 60,92 *10,100",
        "E" to "*75,0 *5,0 *5,100 *75,100 | 5,50 60,50",
        "E" to "5,0 5,100 | 5,0 75,0 | 5,50 60,50 | 5,100 75,100",
        "E" to "75,12 45,0 12,10 15,35 *42,48 10,62 5,85 35,100 78,90",
        "F" to "*75,0 *5,0 *5,100 | 5,50 55,50",
        "F" to "5,0 5,100 | 5,0 75,0 | 5,50 55,50",
        "G" to "85,18 55,0 18,10 0,50 15,90 50,100 80,90 *85,55 *50,55",
        "G" to "85,18 55,0 18,10 0,50 15,90 50,100 82,88 | 50,55 88,55",
        "G" to "@50,50,45,50,-40,-330 *88,55 *55,55",
        "H" to "5,0 5,100 | 75,0 75,100 | 5,50 75,50",
        "H" to "*5,0 *5,100 *5,50 *75,50 | 75,0 75,100",
        "I" to "50,0 50,100 | 20,0 80,0 | 20,100 80,100",
        "K" to "5,0 5,100 | *75,0 *8,55 *80,100",
        "K" to "5,0 5,100 | 75,0 5,55 | 30,40 80,100",
        "L" to "*5,0 *5,100 *70,100",
        "L" to "*8,0 5,60 *5,100 40,96 *72,100",
        "M" to "*0,100 *5,0 *45,70 *85,0 *90,100",
        "M" to "*0,100 *0,0 *45,65 *90,0 *90,100",
        "M" to "0,0 0,100 | *0,0 *45,65 *90,0 *90,100",
        "N" to "*5,100 *5,0 *75,100 *75,0",
        "N" to "5,0 5,100 | *5,0 *75,100 *75,0",
        "Q" to "@45,50,45,50,-90,-450 | 55,70 90,100",
        "Q" to "@45,48,45,48,-90,-450 | 40,85 60,95 95,100",
        "R" to "*5,100 *5,0 50,2 72,20 65,45 *5,52 *75,100",
        "R" to "5,0 5,100 | *5,0 50,2 72,20 65,45 *5,52 *75,100",
        "T" to "0,0 80,0 | 40,0 40,100",
        "T" to "0,4 80,0 | 42,0 38,100",
        "Y" to "*0,0 *40,50 *80,0 | 40,50 40,100",
        "Y" to "0,0 40,50 | *80,0 *40,50 *40,100",
        // --- simgeler
        "+" to "50,0 50,100 | 0,50 100,50",
        "-" to "0,50 100,50",
        "=" to "0,30 100,30 | 0,70 100,70",
        "=" to "10,30 90,30 | 0,70 100,70",
        "/" to "70,0 0,100",
        "(" to "45,0 15,25 5,50 15,75 45,100",
        ")" to "5,0 35,25 45,50 35,75 5,100",
        "," to "55,0 50,45 25,100",
        "," to "50,0 55,40 20,100",
        "?h" to "5,22 25,0 55,0 72,18 68,40 42,58 38,100",
        "?h" to "8,25 20,5 45,0 70,10 72,32 50,55 *42,70 *42,100",
        "<" to "*80,0 *0,50 *80,100",
        ">" to "*0,0 *80,50 *0,100",
        "%" to "*80,0 *5,100 | @18,18,15,17,-90,-450 | @68,82,15,17,-90,-450",
    )

    private val D = Cat.DIGIT
    private val L = Cat.LOWER
    private val U = Cat.UPPER
    private val S = Cat.SYMBOL
    private val P = Cat.PUNCT
    private val B = Cat.BRACKET

    private const val XH = 0.45f   // küçük harf gövdesinin üstü (x-yüksekliği)
    private const val DESC = 1.45f // kuyruk altı

    val classes: List<CharClass> = buildList {
        // rakamlar
        add(CharClass("0", listOf("O"), Marks.NONE, 0f, 1f, D, 0f))
        add(CharClass("1", listOf("|", "1"), Marks.NONE, 0f, 1f, D, 0f))
        for (d in "23456789") add(CharClass(d.toString(), listOf(d.toString()), Marks.NONE, 0f, 1f, D, 0f))
        // küçük harfler
        fun lo(t: String, shapes: List<String>, top: Float = XH, bottom: Float = 1f, marks: Marks = Marks.NONE, prior: Float = 0.1f) =
            add(CharClass(t, shapes, marks, top, bottom, L, prior))
        lo("a", listOf("a"))
        lo("b", listOf("b"), top = 0f)
        lo("c", listOf("c"))
        lo("ç", listOf("c"), marks = Marks.CEDILLA_BELOW)
        lo("d", listOf("d"), top = 0f)
        lo("e", listOf("e"))
        lo("f", listOf("f"), top = 0f)
        lo("g", listOf("g"), bottom = DESC)
        lo("ğ", listOf("g"), bottom = DESC, marks = Marks.BREVE_ABOVE)
        lo("h", listOf("h"), top = 0f)
        lo("ı", listOf("|", "ı"), prior = 0.2f)
        lo("i", listOf("|", "ı"), marks = Marks.DOT_ABOVE)
        lo("j", listOf("Jh"), bottom = DESC, marks = Marks.DOT_ABOVE)
        lo("k", listOf("k"), top = 0f)
        lo("l", listOf("|", "l"), top = 0f)
        lo("m", listOf("m"))
        lo("n", listOf("n"))
        lo("o", listOf("O"))
        lo("ö", listOf("O"), marks = Marks.TWO_DOTS_ABOVE)
        lo("p", listOf("p"), bottom = DESC)
        lo("q", listOf("q"), bottom = DESC, prior = 0.4f)
        lo("r", listOf("r"))
        lo("s", listOf("s"))
        lo("ş", listOf("s"), marks = Marks.CEDILLA_BELOW)
        lo("t", listOf("t"), top = 0.12f)
        lo("u", listOf("u"))
        lo("ü", listOf("u"), marks = Marks.TWO_DOTS_ABOVE)
        lo("v", listOf("v"))
        lo("w", listOf("w"), prior = 0.3f)
        lo("x", listOf("x", "x2"), prior = 0.2f)
        lo("y", listOf("y"), bottom = DESC)
        lo("z", listOf("z"))
        // büyük harfler
        fun up(t: String, shapes: List<String>, marks: Marks = Marks.NONE, bottom: Float = 1f, prior: Float = 0.3f) =
            add(CharClass(t, shapes, marks, 0f, bottom, U, prior))
        for (c in "ABDEFGHKLMNRTY") up(c.toString(), listOf(c.toString()))
        up("C", listOf("c")); up("Ç", listOf("c"), Marks.CEDILLA_BELOW)
        up("Ğ", listOf("G"), Marks.BREVE_ABOVE)
        up("I", listOf("|", "I")); up("İ", listOf("|", "I"), Marks.DOT_ABOVE)
        up("J", listOf("Jh", "J"))
        up("O", listOf("O")); up("Ö", listOf("O"), Marks.TWO_DOTS_ABOVE)
        up("P", listOf("p")); up("Q", listOf("Q"), bottom = 1.08f, prior = 0.5f)
        up("S", listOf("s")); up("Ş", listOf("s"), Marks.CEDILLA_BELOW)
        up("U", listOf("u")); up("Ü", listOf("u"), Marks.TWO_DOTS_ABOVE)
        up("V", listOf("v")); up("W", listOf("w"), prior = 0.4f); up("X", listOf("x"), prior = 0.4f); up("Z", listOf("z"))
        // simgeler
        add(CharClass("+", listOf("+"), Marks.NONE, 0.3f, 0.9f, S, 0.3f, 0.15f))
        add(CharClass("−", listOf("-"), Marks.NONE, 0.3f, 0.9f, S, 0.3f, 0.16f))
        add(CharClass("=", listOf("="), Marks.NONE, 0.3f, 0.9f, S, 0.3f, 0.15f))
        add(CharClass("×", listOf("x"), Marks.NONE, 0.38f, 0.88f, S, 0.4f, 0.14f))
        add(CharClass("÷", listOf("-"), Marks.DOTS_ABOVE_BELOW, 0.3f, 0.9f, S, 0.3f, 0.16f))
        add(CharClass("/", listOf("/"), Marks.NONE, -0.05f, 1.05f, S, 0.3f, 0.14f))
        add(CharClass("<", listOf("<"), Marks.NONE, 0.3f, 0.9f, S, 0.4f, 0.15f))
        add(CharClass(">", listOf(">"), Marks.NONE, 0.3f, 0.9f, S, 0.4f, 0.15f))
        add(CharClass("%", listOf("%"), Marks.NONE, 0f, 1f, S, 0.4f, 0.14f))
        add(CharClass("(", listOf("("), Marks.NONE, -0.05f, 1.1f, B, 0.3f, 0.14f))
        add(CharClass(")", listOf(")"), Marks.NONE, -0.05f, 1.1f, B, 0.3f, 0.14f))
        // noktalama
        add(CharClass(".", emptyList(), Marks.ONLY_DOT, 0.92f, 1f, P, 0.4f, 0.1f))
        add(CharClass(":", emptyList(), Marks.ONLY_COLON, 0.45f, 1f, P, 0.5f, 0.14f))
        add(CharClass(",", listOf(","), Marks.NONE, 0.82f, 1.2f, P, 0.5f, 0.12f))
        add(CharClass(",", emptyList(), Marks.ONLY_TICK, 0.85f, 1.15f, P, 0.5f, 0.12f))
        add(CharClass("?", listOf("?h"), Marks.DOT_BELOW, 0f, 0.75f, P, 0.4f))
        add(CharClass("!", listOf("|"), Marks.DOT_BELOW, 0f, 0.75f, P, 0.4f))
    }

    init {
        // sıkı bantlar: ilk şekil etiketinin taslaklarının ortalama y aralığı
        for (c in classes) {
            val label = c.shapes.firstOrNull() ?: continue
            var lo = 0f; var hi = 0f; var n = 0
            for ((l, src) in sketches) if (l == label) {
                val polys = Sketch.renderAll(src, 4)
                val b = Box.of(polys)
                lo += b.minY; hi += b.maxY; n++
            }
            if (n == 0) continue
            lo /= n; hi /= n
            c.bandTop = c.top + (c.bottom - c.top) * lo / 100f
            c.bandBottom = c.top + (c.bottom - c.top) * hi / 100f
        }
    }

    fun allowed(c: CharClass, mode: InkMode): Boolean = when (mode) {
        InkMode.MIXED -> true
        InkMode.DIGITS -> c.cat == Cat.DIGIT || c.cat == Cat.SYMBOL || c.cat == Cat.BRACKET || c.text in ".,:"
        InkMode.LETTERS -> c.cat == Cat.LOWER || c.cat == Cat.UPPER || c.cat == Cat.PUNCT || c.cat == Cat.BRACKET || c.text == "−"
    }
}
