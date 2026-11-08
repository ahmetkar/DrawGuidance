package com.karapp.drawguidance.content.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * Yapay el yazısı üreteci (testlerde tanıyıcının sınanması; v32 eğitim modunda el yazısının yazıya çevrilmesinin
 * gösterimi). Taslaklar tanıyıcının şablonlarından AYRI yazılmıştır (farklı nokta seçimi ve
 * oranlar). Her örnekte: düşük frekanslı düzgün bükülme + nokta titremesi, harf başına boyut/taban kayması, satır
 * düzeyinde eğim (±12°), döndürme (±8°), ölçek (20–160 birim), hıza bağlı örnekleme aralığı, sensör gürültüsü,
 * rastgele çizgi yönü ve sırası, işaretlerin (nokta/çengel) bazen sözcük sonunda çizilmesi.
 */
object InkSynth {

    internal class Spec(val sketches: List<String>, val top: Float, val bottom: Float, val marks: Marks = Marks.NONE, val lowerX: Boolean = false)

    private const val XH = -1f // küçük harf üstü: örnek başına rastgele x-yüksekliği

    private fun s(vararg v: String) = v.toList()

    private val O_LO = s("@48,50,46,50,-100,-460", "50,0 12,14 0,52 16,90 50,100 84,86 96,48 82,12 50,0")
    private val O_UP = s("@44,50,44,50,-90,-450", "@42,50,40,50,-60,300")
    private val U = s("6,0 6,70 22,96 50,100 76,84 *84,0 *84,100", "4,0 8,72 26,98 50,100 74,96 90,70 94,0", "4,0 6,68 22,96 48,100 72,86 *82,0 *82,86 94,100")
    private val C = s("84,18 56,0 18,10 2,52 22,92 58,100 84,82", "@46,50,44,50,-50,-310", "70,10 46,0 16,14 6,52 22,90 50,100 76,88")
    private val S = s("82,14 56,0 22,6 12,26 34,46 72,56 86,78 64,98 28,100 4,86", "76,10 48,0 20,10 16,30 44,48 70,60 76,82 54,100 22,96 2,82", "*78,6 46,0 16,10 18,32 *60,50 80,72 64,96 30,100 *4,86")
    private val G_LO = s(
        "78,10 48,0 12,10 4,28 22,46 50,48 76,32 *80,2 80,82 62,100 28,98 8,84",
        "@40,24,36,24,-10,-370 | 80,0 80,78 60,98 26,100 6,84",
        "78,10 48,0 12,10 4,28 22,46 50,48 76,32 *80,2 80,70 66,96 40,100 26,86 44,70 84,60",
    )
    private val G_UP = s("84,20 54,0 18,12 0,52 16,90 50,100 80,88 *84,56 *52,56", "84,20 54,0 16,12 0,52 16,90 50,100 82,86 | 50,56 86,56", "84,20 54,0 18,12 0,52 16,90 50,100 80,88 84,60 *60,58")
    private val STEM = s("50,0 50,100", "49,0 51,100")
    private val I_LO = s("50,0 50,100", "50,0 *50,84 60,100 76,96")
    private val J_LO = s("70,0 *70,78 54,98 26,100 6,84", "70,0 70,76 58,96 30,100 8,86")
    private val I_UP = s("50,0 50,100", "50,0 50,100 | 22,0 78,0 | 22,100 78,100")

    internal val specs: Map<Char, Spec> = buildMap {
        // rakamlar
        put('0', Spec(s("30,0 8,20 2,55 12,88 32,100 52,85 58,50 50,15 30,0", "@30,50,27,50,200,-160", "@30,50,28,50,90,450", "34,4 12,18 4,50 14,86 32,100 52,88 58,52 50,16 30,2 22,8"), 0f, 1f))
        put('1', Spec(s("45,0 44,100", "20,30 38,15 *52,0 50,100", "*28,20 *50,0 *51,100 | 28,100 72,100", "*8,38 *52,0 50,100"), 0f, 1f))
        put('2', Spec(s("4,20 25,0 52,2 64,22 56,45 30,72 *4,98 *68,96", "6,25 30,0 58,8 62,32 40,62 12,90 6,100 16,96 30,92 50,98 *70,96", "8,14 30,0 58,6 *64,24 *6,100 *70,100"), 0f, 1f))
        put('3', Spec(s("10,8 35,0 56,12 52,32 *30,46 55,55 64,78 48,98 20,100 2,86", "*8,2 *58,0 *28,42 52,46 62,68 52,92 26,100 4,90", "10,6 30,0 50,8 46,26 *28,36 58,48 64,76 44,98 14,96 2,82"), 0f, 1f))
        put('4', Spec(s("*42,0 *4,62 *70,62 | 55,20 55,100", "*55,100 *55,2 *3,68 *72,68", "*10,0 *8,55 *70,55 | 55,5 55,100", "*32,6 *6,60 *76,60 | 58,0 58,100"), 0f, 1f))
        put('5', Spec(s("*14,0 *10,42 32,36 54,44 62,68 52,92 28,100 6,92 | 14,0 58,2", "*60,2 *16,0 *12,44 40,36 60,52 58,82 36,100 6,92", "*16,4 *12,46 36,40 56,52 58,78 42,98 18,100 2,90 | 14,4 40,0 62,4"), 0f, 1f))
        put('6', Spec(s("58,6 40,0 18,14 6,48 8,82 28,100 52,96 62,74 50,56 26,54 8,68", "52,2 22,38 8,72 18,96 40,100 58,84 56,62 36,52 14,62", "56,4 30,6 10,34 6,70 22,98 48,100 62,78 52,56 28,52 8,66"), 0f, 1f))
        put('7', Spec(s("*2,0 *62,2 *22,100", "*2,0 *62,2 *22,100 | 18,50 56,50", "*0,8 *4,0 *60,0 38,50 30,100", "*4,14 *8,2 *62,4 44,40 32,100 | 16,54 58,50"), 0f, 1f))
        put('8', Spec(s("54,14 32,0 10,14 16,36 52,62 58,86 34,100 8,88 10,66 48,36 50,12 36,2", "@30,23,20,23,-80,-440 | @30,73,26,27,-100,-460", "30,50 10,30 12,8 32,0 50,10 48,32 30,50 8,70 12,94 34,100 54,88 52,66 30,50"), 0f, 1f))
        put('9', Spec(s("56,16 34,0 8,12 6,36 28,48 50,40 *58,6 *56,100", "56,18 34,0 8,14 8,38 30,48 54,36 *58,8 56,60 46,92 22,100 6,90", "58,20 36,0 8,10 6,34 26,46 52,40 *60,10 58,60 40,94 12,100"), 0f, 1f))
        // küçük harfler
        put('a', Spec(s("78,22 52,0 16,12 2,52 18,94 48,100 76,70 *82,2 82,86 96,100", "@40,52,38,48,-30,-370 | 80,0 82,100", "14,20 44,0 72,10 78,34 *78,100 | 78,50 36,46 8,70 24,98 58,92 78,74", "86,18 72,55 60,85 44,100 18,94 2,60 14,16 40,0 62,18 76,62 92,100"), XH, 1f))
        put('b', Spec(s("*12,0 *12,100 12,68 38,48 72,50 86,74 72,98 36,100 12,86", "12,0 12,100 | 12,64 44,46 80,54 86,80 64,100 30,98 12,88", "18,30 30,10 26,0 16,6 12,30 *12,100 12,66 40,46 74,50 88,76 70,98 34,100 12,88"), 0f, 1f))
        put('c', Spec(C, XH, 1f))
        put('ç', Spec(C, XH, 1f, Marks.CEDILLA_BELOW))
        put('d', Spec(s("78,60 46,46 10,58 2,80 20,98 50,100 78,82 *84,0 *84,100", "@40,74,36,26,-10,-370 | 82,0 84,100", "80,62 50,46 14,56 2,78 18,98 48,100 76,86 *82,20 72,4 80,0 88,10 *84,100"), 0f, 1f))
        put('e', Spec(s("6,56 62,54 88,42 72,8 42,0 12,18 2,58 18,92 52,100 88,84", "2,72 44,58 66,36 62,12 40,0 14,18 6,58 24,92 58,100 86,82", "10,55 70,50 84,30 64,4 36,0 10,20 2,58 16,90 48,100 80,90"), XH, 1f))
        put('f', Spec(s("80,10 60,0 44,6 38,28 *38,100 | 8,40 70,42", "70,5 50,0 40,18 38,100 | 12,38 64,38"), 0f, 1f))
        put('g', Spec(G_LO, XH, 1.42f))
        put('ğ', Spec(G_LO, XH, 1.42f, Marks.BREVE_ABOVE))
        put('h', Spec(s("*12,0 *12,100 12,68 38,46 68,46 84,64 *84,100", "12,0 12,100 | 12,60 38,44 66,46 82,64 84,100", "4,40 24,14 26,0 14,4 10,30 *10,100 10,66 36,46 64,46 80,64 *82,100"), 0f, 1f))
        put('ı', Spec(I_LO, XH, 1f))
        put('i', Spec(I_LO, XH, 1f, Marks.DOT_ABOVE))
        put('j', Spec(J_LO, XH, 1.42f, Marks.DOT_ABOVE))
        put('k', Spec(s("12,0 *12,100 | 68,44 *16,70 *72,100", "*12,0 *12,100 *12,72 *66,44 *28,66 *74,100", "12,0 12,100 | 70,40 14,66 | 34,56 74,100"), 0f, 1f))
        put('l', Spec(s("48,0 48,100", "6,78 32,48 50,16 42,0 28,10 24,48 26,86 40,100 62,90", "42,0 *42,82 52,100 70,94"), 0f, 1f))
        put('m', Spec(s("*6,0 *6,100 *6,32 26,4 44,2 58,18 *60,100 *60,32 80,4 100,2 114,18 *116,100", "6,0 6,100 | 6,28 26,4 46,4 58,22 60,100 | 60,28 80,4 100,4 114,22 116,100", "*0,18 *8,0 *8,100 8,34 26,4 44,4 56,22 *58,100 58,34 78,4 98,4 110,22 *112,100"), XH, 1f))
        put('n', Spec(s("*6,0 *6,100 *6,30 30,4 54,2 72,18 *74,100", "6,0 6,100 | 6,28 32,4 58,4 72,24 74,100", "*0,20 *8,0 *8,100 8,36 30,4 56,2 72,20 *76,100"), XH, 1f))
        put('o', Spec(O_LO, XH, 1f))
        put('ö', Spec(O_LO, XH, 1f, Marks.TWO_DOTS_ABOVE))
        put('p', Spec(s("*10,0 *10,100 | 10,10 38,0 72,4 88,26 70,48 36,50 10,40", "*10,100 *10,0 40,0 74,6 88,28 70,48 36,50 *10,46", "*10,0 *10,100 | 10,14 40,0 76,6 90,26 74,46 40,50"), XH, 1.42f))
        put('q', Spec(s("80,12 48,0 12,10 4,28 22,48 52,50 78,34 *82,0 *82,100", "@40,26,36,24,-10,-370 | 80,0 80,100", "80,12 48,0 12,10 4,28 22,48 52,50 78,34 *82,0 *82,86 96,74"), XH, 1.42f))
        put('r', Spec(s("*10,0 *10,100 *10,38 32,8 56,0 78,10", "10,0 10,100 | 10,32 34,4 60,0 76,8", "*10,0 *10,100 10,40 24,12 46,0 70,6"), XH, 1f))
        put('s', Spec(S, XH, 1f))
        put('ş', Spec(S, XH, 1f, Marks.CEDILLA_BELOW))
        put('t', Spec(s("42,0 *42,84 54,100 74,94 | 6,28 78,30", "40,0 40,100 | 8,30 76,28", "30,0 *30,86 42,100 62,92 | 4,24 70,20"), 0.14f, 1f))
        put('u', Spec(U, XH, 1f))
        put('ü', Spec(U, XH, 1f, Marks.TWO_DOTS_ABOVE))
        put('v', Spec(s("*0,0 *44,100 *86,0", "0,0 18,58 38,96 46,100 56,88 72,42 86,0", "*0,4 *8,0 *44,100 *84,0"), XH, 1f))
        put('w', Spec(s("*0,0 *26,100 *54,28 *80,100 *106,0", "0,0 4,72 16,100 32,98 46,72 *52,30 *58,72 72,100 88,98 98,70 102,0"), XH, 1f))
        put('x', Spec(s("0,0 88,100 | 88,0 0,100", "6,6 26,0 42,22 44,50 40,78 22,100 4,94 | 84,6 64,0 48,22 44,50 48,78 64,100 84,94"), XH, 1f))
        put('y', Spec(s("*0,0 *44,50 | *88,0 *12,100", "6,0 6,32 22,48 46,50 70,40 *80,0 *80,82 62,100 32,98 12,86", "*0,0 *44,50 | 88,0 50,60 30,90 12,100"), XH, 1.42f))
        put('z', Spec(s("*6,0 *84,0 *6,100 *88,100", "*6,0 *84,0 *6,100 *88,100 | 24,50 68,50", "*8,6 *82,0 *4,100 *86,96 | 20,52 66,48"), XH, 1f))
        // büyük harfler
        put('A', Spec(s("*0,100 *38,0 *78,100 | 16,62 60,62", "38,0 2,100 | 38,0 76,100 | 18,60 58,60", "*0,100 30,10 40,0 50,10 *80,100 | 16,60 64,60"), 0f, 1f))
        put('B', Spec(s("*10,0 *10,100 | *10,0 52,2 70,16 64,38 *12,48 62,54 80,74 68,96 *10,100", "*10,100 *10,0 54,2 70,16 62,40 *12,48 64,56 80,76 66,96 *10,100", "10,0 10,100 | *10,0 50,0 66,14 60,36 *10,48 | *10,48 64,54 80,74 66,96 *10,100"), 0f, 1f))
        put('C', Spec(C, 0f, 1f))
        put('Ç', Spec(C, 0f, 1f, Marks.CEDILLA_BELOW))
        put('D', Spec(s("10,0 10,100 | *10,0 48,4 76,28 78,70 54,96 *10,100", "*10,100 *10,0 52,4 78,30 80,68 58,94 *10,100", "*8,4 *10,100 | *4,4 44,0 74,22 80,60 60,92 *10,100"), 0f, 1f))
        put('E', Spec(s("*74,0 *6,0 *6,100 *74,100 | 6,50 58,50", "6,0 6,100 | 6,0 72,0 | 6,50 56,50 | 6,100 74,100", "72,14 44,0 14,12 16,36 *40,48 12,62 6,86 36,100 76,90", "*6,0 *6,100 *74,100 | 6,0 72,0 | 6,50 56,50"), 0f, 1f))
        put('F', Spec(s("*74,0 *6,0 *6,100 | 6,48 54,48", "6,0 6,100 | 6,0 72,0 | 6,50 52,50", "*6,0 *6,100 | 6,0 72,0 | 6,46 50,46"), 0f, 1f))
        put('G', Spec(G_UP, 0f, 1f))
        put('Ğ', Spec(G_UP, 0f, 1f, Marks.BREVE_ABOVE))
        put('H', Spec(s("6,0 6,100 | 74,0 74,100 | 6,50 74,50", "*6,0 *6,100 *6,48 *74,48 | 74,0 74,100", "*6,0 *8,100 | *72,0 *74,100 | 0,52 80,48"), 0f, 1f))
        put('I', Spec(I_UP, 0f, 1f))
        put('İ', Spec(I_UP, 0f, 1f, Marks.DOT_ABOVE))
        put('J', Spec(s("74,0 *74,76 58,98 30,100 6,82", "*74,0 *74,76 58,98 30,100 6,82 | 44,0 100,0", "54,0 *74,0 *74,76 58,98 30,100 6,82"), 0f, 1f))
        put('K', Spec(s("6,0 6,100 | *74,0 *8,56 *78,100", "6,0 6,100 | 74,0 6,56 | 30,40 78,100", "6,0 6,100 | 72,0 *10,58 44,52 *78,100"), 0f, 1f))
        put('L', Spec(s("*6,0 *6,100 *70,100", "*8,0 6,60 *6,100 40,94 *74,100"), 0f, 1f))
        put('M', Spec(s("*0,100 *6,0 *44,70 *84,0 *90,100", "0,0 0,100 | *0,0 *44,66 *88,0 *88,100", "*0,100 *6,0 *44,100 *84,0 *90,100"), 0f, 1f))
        put('N', Spec(s("*6,100 *6,0 *74,100 *74,0", "6,0 6,100 | *6,0 *74,100 *74,0", "*6,100 *6,0 *74,100 *74,0 70,-4"), 0f, 1f))
        put('O', Spec(O_UP, 0f, 1f))
        put('Ö', Spec(O_UP, 0f, 1f, Marks.TWO_DOTS_ABOVE))
        put('P', Spec(s("*6,0 *6,100 | *6,0 48,2 70,20 64,44 *6,52", "*6,100 *6,0 50,2 72,22 64,44 *6,52", "*6,100 *6,0 50,2 72,20 60,44 *16,50"), 0f, 1f))
        put('Q', Spec(s("@44,50,44,50,-90,-450 | 56,72 88,100", "@44,50,44,50,-90,-450 | 40,80 62,92 94,100"), 0f, 1f))
        put('R', Spec(s("*6,100 *6,0 48,2 70,20 64,44 *6,52 *74,100", "6,0 6,100 | *6,0 48,2 70,20 64,44 *6,52 *74,100", "*6,100 *6,0 48,2 70,20 64,44 *6,52 40,64 *76,100"), 0f, 1f))
        put('S', Spec(S, 0f, 1f))
        put('Ş', Spec(S, 0f, 1f, Marks.CEDILLA_BELOW))
        put('T', Spec(s("0,0 80,0 | 40,0 40,100", "2,2 78,0 | 40,0 40,100", "0,6 40,0 82,4 | 42,0 40,100"), 0f, 1f))
        put('U', Spec(U, 0f, 1f))
        put('Ü', Spec(U, 0f, 1f, Marks.TWO_DOTS_ABOVE))
        put('V', Spec(s("*0,0 *42,100 *84,0", "0,0 16,56 36,96 44,100 52,90 70,44 84,0"), 0f, 1f))
        put('W', Spec(s("*0,0 *24,100 *52,26 *80,100 *104,0", "0,0 4,72 16,100 32,98 46,72 *52,30 *58,72 72,100 88,98 98,70 102,0"), 0f, 1f))
        put('X', Spec(s("0,0 86,100 | 86,0 0,100"), 0f, 1f))
        put('Y', Spec(s("*0,0 *40,52 *80,0 | 40,52 40,100", "0,0 40,52 | *80,0 *40,52 *40,100", "0,0 40,52 | *80,0 *40,52 *40,100"), 0f, 1f))
        put('Z', Spec(s("*6,0 *84,0 *6,100 *88,100", "*6,0 *84,0 *6,100 *88,100 | 22,50 66,50"), 0f, 1f))
        // simgeler
        put('+', Spec(s("50,0 50,100 | 0,50 100,50", "48,2 52,98 | 2,52 98,48", "50,8 50,92 | 10,50 90,50"), 0.32f, 0.88f))
        put('−', Spec(s("0,50 100,50", "0,52 100,48"), 0.32f, 0.88f))
        put('=', Spec(s("0,32 100,32 | 0,70 100,70", "4,30 96,32 | 0,70 100,68"), 0.32f, 0.88f))
        put('×', Spec(s("4,4 86,96 | 86,4 4,96"), 0.4f, 0.9f))
        put('÷', Spec(s("0,50 100,50"), 0.3f, 0.9f, Marks.DOTS_ABOVE_BELOW))
        put('/', Spec(s("68,0 2,100"), 0f, 1f))
        put('(', Spec(s("42,0 14,26 4,52 16,78 44,100", "50,0 20,20 6,50 18,82 50,100"), -0.05f, 1.1f))
        put(')', Spec(s("4,0 32,24 42,50 30,78 4,100", "0,0 30,18 44,50 32,82 0,100"), -0.05f, 1.1f))
        put('<', Spec(s("*78,0 *2,50 *78,100", "*84,6 *0,50 *84,94"), 0.32f, 0.88f))
        put('>', Spec(s("*2,0 *78,50 *2,100", "*0,6 *84,50 *0,94"), 0.32f, 0.88f))
        put('%', Spec(s("*78,0 *6,100 | @18,18,14,16,-90,-450 | @66,82,14,16,-90,-450", "*6,100 *78,0 | @16,20,12,18,-90,-450 | @66,80,12,18,-90,-450"), 0f, 1f))
        put('?', Spec(s("8,26 20,4 46,0 68,12 70,34 50,56 *44,70 *44,100", "4,22 24,2 52,0 70,16 66,40 46,56 42,100", "2,30 12,6 40,0 66,8 72,30 56,50 42,62 *40,100"), 0f, 0.72f, Marks.DOT_BELOW))
        put('!', Spec(STEM, 0f, 0.72f, Marks.DOT_BELOW))
        put(',', Spec(s("52,0 50,50 22,100"), 0.86f, 1.16f))
        put('.', Spec(emptyList(), 0.9f, 1f, Marks.ONLY_DOT))
        put(':', Spec(emptyList(), 0.5f, 1f, Marks.ONLY_COLON))
    }

    private class P(val x: Float, val y: Float)

    private class CharInk(val strokes: List<List<P>>, val delayable: Set<Int>, val width: Float)

    private fun gauss(r: Random) = (r.nextDouble() + r.nextDouble() + r.nextDouble() - 1.5).toFloat() * 1.41f

    private fun dot(r: Random, cx: Float, cy: Float): List<P> {
        if (r.nextFloat() < 0.3f) return listOf(P(cx, cy))
        val rad = 0.012f + r.nextFloat() * 0.022f
        val n = 2 + r.nextInt(4)
        val a0 = r.nextFloat() * 6.28f
        return (0 until n).map { k -> P(cx + rad * cos(a0 + k * 2.2f), cy + rad * sin(a0 + k * 2.2f)) }
    }

    /** Tek karakter (satır birimleri: H = 1, taban y = 1, x 0'dan). */
    private fun charInk(ch: Char, r: Random, xh: Float, warp: Float = 3.5f): CharInk {
        val sp = specs[ch] ?: error("tanımsız: $ch")
        val top = if (sp.top == XH) xh else sp.top
        val bottom = sp.bottom
        val out = ArrayList<List<P>>()
        val delay = HashSet<Int>()
        if (sp.sketches.isEmpty()) {
            when (sp.marks) {
                Marks.ONLY_DOT -> out += dot(r, 0.04f, 0.96f + gauss(r) * 0.01f)
                Marks.ONLY_COLON -> { out += dot(r, 0.04f, 0.52f + gauss(r) * 0.02f); out += dot(r, 0.04f + gauss(r) * 0.01f, 0.95f) }
                else -> {}
            }
            return CharInk(out, emptySet(), 0.08f)
        }
        val src = sp.sketches[r.nextInt(sp.sketches.size)]
        val k = (bottom - top) / 100f
        val kx = k * (0.85f + r.nextFloat() * 0.3f) // karakter genişliği değişimi
        // düşük frekanslı bükülme
        val ax = gauss(r) * warp; val ay = gauss(r) * warp
        val fx = 1f + r.nextFloat() * 2f; val fy = 1f + r.nextFloat() * 2f
        val px = r.nextFloat() * 6.28f; val py = r.nextFloat() * 6.28f
        val strokes = Sketch.parse(src).map { ctrls ->
            val warped = ctrls.map { c ->
                val dx = ax * sin(c.y / 100f * fx * PI.toFloat() + px) + gauss(r) * 1.4f
                val dy = ay * sin(c.x / 100f * fy * PI.toFloat() + py) + gauss(r) * 1.4f
                Ctrl(c.x + dx, c.y + dy, c.corner)
            }
            // uçlarda hafif taşma/eksik kalma
            val poly = Sketch.render(warped, 8)
            (0 until poly.n).map { P(poly.x[it] * kx, top + poly.y[it] * k) }
        }
        out += strokes
        val minX = strokes.flatten().minOf { it.x }; val maxX = strokes.flatten().maxOf { it.x }
        val minY = strokes.flatten().minOf { it.y }; val maxY = strokes.flatten().maxOf { it.y }
        val cx = (minX + maxX) / 2
        val w = maxX - minX
        fun topX(): Float = strokes.flatten().minByOrNull { it.y }!!.x
        when (sp.marks) {
            Marks.DOT_ABOVE -> { delay += out.size; out += dot(r, topX() + gauss(r) * 0.03f, minY - 0.17f - r.nextFloat() * 0.1f) }
            Marks.TWO_DOTS_ABOVE -> {
                val y = minY - 0.14f - r.nextFloat() * 0.08f
                val half = w * (0.2f + r.nextFloat() * 0.1f)
                if (r.nextFloat() < 0.15f) {
                    delay += out.size; out += listOf(P(cx - half, y), P(cx + half, y + gauss(r) * 0.01f))
                } else {
                    delay += out.size; out += dot(r, cx - half, y + gauss(r) * 0.015f)
                    delay += out.size; out += dot(r, cx + half, y + gauss(r) * 0.015f)
                }
            }
            Marks.BREVE_ABOVE -> {
                val hw = 0.1f + r.nextFloat() * 0.07f
                val d = 0.05f + r.nextFloat() * 0.05f
                val yb = minY - 0.1f - r.nextFloat() * 0.08f
                delay += out.size
                out += (0..8).map { i -> val t = i / 8f; P(cx - hw + 2 * hw * t, yb - d + d * sin(t * PI.toFloat())) }
            }
            Marks.CEDILLA_BELOW -> {
                val bx = cx + gauss(r) * 0.02f
                delay += out.size
                out += when (r.nextInt(3)) {
                    0 -> listOf(P(bx, maxY - 0.01f), P(bx + 0.035f, maxY + 0.06f), P(bx + 0.01f, maxY + 0.13f), P(bx - 0.05f, maxY + 0.15f))
                    1 -> listOf(P(bx + 0.01f, maxY + 0.01f), P(bx - 0.04f, maxY + 0.14f))
                    else -> listOf(P(bx, maxY + 0.02f), P(bx + 0.02f, maxY + 0.07f), P(bx - 0.03f, maxY + 0.14f))
                }
            }
            Marks.DOT_BELOW -> { delay += out.size; out += dot(r, strokes.flatten().maxByOrNull { it.y }!!.x + gauss(r) * 0.02f, 0.95f + gauss(r) * 0.015f) }
            Marks.DOTS_ABOVE_BELOW -> {
                val ly = (minY + maxY) / 2
                out += dot(r, cx + gauss(r) * 0.02f, ly - 0.2f - r.nextFloat() * 0.05f)
                out += dot(r, cx + gauss(r) * 0.02f, ly + 0.2f + r.nextFloat() * 0.05f)
            }
            else -> {}
        }
        // x'i 0'a hizala
        val shifted = out.map { st -> st.map { P(it.x - minX, it.y) } }
        return CharInk(shifted, delay, w)
    }

    class Options(
        val slantDeg: Float = 12f,
        val rotDeg: Float = 8f,
        val minH: Float = 20f,
        val maxH: Float = 160f,
        val delayMarks: Float = 0.35f,
        /** düşük frekanslı bükülme genliği (taslak birimi, 100 = glif yüksekliği) */
        val warp: Float = 3.5f,
    )

    /** [text] için yapay mürekkep (boşluk = sözcük arası). */
    fun sample(text: String, r: Random, o: Options = Options()): List<InkStroke> {
        val xh = 0.4f + r.nextFloat() * 0.12f
        val all = ArrayList<List<P>>()
        val delayed = ArrayList<List<P>>()
        var x = 0f
        for (ch in text) {
            if (ch == ' ') { x += 0.75f + r.nextFloat() * 0.35f; continue }
            val ci = charInk(ch, r, xh, o.warp)
            val sc = 0.92f + r.nextFloat() * 0.16f
            val dy = gauss(r) * 0.025f
            val rot = gauss(r) * 2.5f * PI.toFloat() / 180f
            val cw = ci.width
            val placed = ci.strokes.map { st ->
                st.map { p ->
                    // harf başına döndürme + ölçek (taban çizgisi etrafında)
                    val lx = p.x - cw / 2; val ly = p.y - 1f
                    val rx = lx * cos(rot) - ly * sin(rot); val ry = lx * sin(rot) + ly * cos(rot)
                    P(x + cw / 2 + rx * sc, 1f + ry * sc + dy)
                }
            }
            // karakter içi çizgi sırası
            val idx = placed.indices.toMutableList()
            if (idx.size > 1 && r.nextFloat() < 0.3f) idx.shuffle(r)
            for (i in idx) {
                if (i in ci.delayable && r.nextFloat() < o.delayMarks) delayed += placed[i] else all += placed[i]
            }
            x += cw * sc + 0.13f + r.nextFloat() * 0.25f
        }
        all += delayed
        // satır dönüşümü
        val slant = tan((r.nextFloat() * 2 - 1) * o.slantDeg * PI.toFloat() / 180f)
        val rot = (r.nextFloat() * 2 - 1) * o.rotDeg * PI.toFloat() / 180f
        val h = o.minH + r.nextFloat() * (o.maxH - o.minH)
        val sx = 0.9f + r.nextFloat() * 0.2f
        val ox = r.nextFloat() * 500f; val oy = 100f + r.nextFloat() * 500f
        val cxAll = x / 2
        val lines = all.map { st ->
            st.map { p ->
                val ax = (p.x - cxAll) * sx + slant * (1f - p.y)
                val ay = p.y - 0.7f
                P(ox + h * (ax * cos(rot) - ay * sin(rot)), oy + h * (ax * sin(rot) + ay * cos(rot)))
            }
        }
        val speed = h * (3f + r.nextFloat() * 5f) / 1000f // birim / ms
        val dt = 8f + r.nextFloat() * 8f
        val noise = h * (0.002f + r.nextFloat() * 0.006f)
        var t = 0L
        return lines.map { st0 ->
            val st = if (r.nextFloat() < 0.3f) st0.reversed() else st0
            val pts = timeSample(st, speed, dt, r, noise, t)
            t = pts.last().t + 150 + r.nextInt(200)
            InkStroke(pts)
        }
    }

    /** Hız eğrilikte ve uçlarda düşer; sabit zaman aralığıyla örneklenir. */
    private fun timeSample(st: List<P>, vmax: Float, dt: Float, r: Random, noise: Float, t0: Long): List<InkPoint> {
        if (st.size == 1) return listOf(InkPoint(st[0].x + gauss(r) * noise, st[0].y + gauss(r) * noise, t0))
        // yoğunlaştır
        val dense = ArrayList<P>()
        for (i in 0 until st.size - 1) {
            val a = st[i]; val b = st[i + 1]
            val d = hypot(b.x - a.x, b.y - a.y)
            val m = max(1, (d / (vmax * dt * 0.25f)).toInt())
            for (k in 0 until m) dense += P(a.x + (b.x - a.x) * k / m, a.y + (b.y - a.y) * k / m)
        }
        dense += st.last()
        val total = (1 until dense.size).sumOf { hypot(dense[it].x - dense[it - 1].x, dense[it].y - dense[it - 1].y).toDouble() }.toFloat()
        val out = ArrayList<InkPoint>()
        out += InkPoint(dense[0].x + gauss(r) * noise, dense[0].y + gauss(r) * noise, t0)
        var time = 0f
        var next = dt
        var s = 0f
        for (i in 1 until dense.size) {
            val a = dense[i - 1]; val b = dense[i]
            val d = hypot(b.x - a.x, b.y - a.y)
            s += d
            // eğrilik
            var turn = 0f
            if (i + 1 < dense.size) {
                val c = dense[i + 1]
                val a1 = kotlin.math.atan2(b.y - a.y, b.x - a.x); val a2 = kotlin.math.atan2(c.y - b.y, c.x - b.x)
                var da = abs(a2 - a1); if (da > PI) da = (2 * PI - da).toFloat()
                turn = da
            }
            val endF = min(1f, 0.25f + min(s, total - s) / (0.15f * max(total, 1e-3f)))
            val v = max(vmax * 0.15f, vmax * endF / (1f + 6f * turn))
            time += d / v
            if (time >= next) {
                out += InkPoint(b.x + gauss(r) * noise, b.y + gauss(r) * noise, t0 + time.toLong())
                next += dt
            }
        }
        val l = dense.last()
        if (out.last().x != l.x || out.last().y != l.y) out += InkPoint(l.x + gauss(r) * noise, l.y + gauss(r) * noise, t0 + time.toLong() + 1)
        return out
    }

    // ---------------------------------------------------------------- çizimler (reddedilmesi gerekenler)

    internal fun drawing(kind: String, r: Random): List<InkStroke> {
        val h = 40f + r.nextFloat() * 200f
        val ox = r.nextFloat() * 400f; val oy = 100f + r.nextFloat() * 400f
        fun pt(x: Float, y: Float) = P(ox + x * h, oy + y * h)
        fun poly(vararg xy: Float): List<P> = (xy.indices step 2).map { pt(xy[it], xy[it + 1]) }
        fun smoothThrough(cs: List<Ctrl>): List<P> { val p = Sketch.render(cs, 10); return (0 until p.n).map { pt(p.x[it], p.y[it]) } }
        val strokes: List<List<P>> = when (kind) {
            "spiral" -> {
                val turns = 2.5f + r.nextFloat() * 2.5f
                val n = (turns * 40).toInt()
                listOf((0..n).map { i -> val t = i / n.toFloat(); val a = t * turns * 2 * PI.toFloat(); val rad = 0.05f + 0.5f * t; pt(0.5f + rad * cos(a), 0.5f + rad * sin(a)) })
            }
            "zigzag" -> {
                val segs = 6 + r.nextInt(9)
                val w = 1f + r.nextFloat() * 3f
                val amp = 0.3f + r.nextFloat() * 0.7f
                listOf((0..segs).map { i -> pt(w * i / segs, if (i % 2 == 0) 0f else amp) })
            }
            "rectangle" -> {
                val w = 0.6f + r.nextFloat() * 2f
                when (r.nextInt(3)) {
                    0 -> listOf(poly(0f, 0f, w, 0f, w, 1f, 0f, 1f, 0f, 0f))
                    1 -> listOf(poly(0f, 0f, w, 0f), poly(w, 0f, w, 1f), poly(w, 1f, 0f, 1f), poly(0f, 1f, 0f, 0f))
                    else -> listOf(poly(0f, 0f, w, 0f, w, 1f), poly(w, 1f, 0f, 1f, 0f, 0f))
                }
            }
            "scribble" -> {
                (0 until 1 + r.nextInt(3)).map {
                    val m = 7 + r.nextInt(7)
                    smoothThrough((0 until m).map { Ctrl(r.nextFloat() * 150f, r.nextFloat() * 100f, false) }.map { Ctrl(it.x / 100f, it.y / 100f, false) })
                }
            }
            "star" -> {
                val pts = (0..5).map { i -> val a = -PI.toFloat() / 2 + i * 4 * PI.toFloat() / 5; pt(0.5f + 0.5f * cos(a), 0.5f + 0.5f * sin(a)) }
                listOf(pts)
            }
            "house" -> {
                val w = 0.8f + r.nextFloat() * 0.6f
                if (r.nextBoolean()) listOf(poly(0f, 0.4f, 0f, 1.2f, w, 1.2f, w, 0.4f, 0f, 0.4f), poly(-0.1f, 0.45f, w / 2, 0f, w + 0.1f, 0.45f))
                else listOf(poly(0f, 1.2f, 0f, 0.4f, w / 2, 0f, w, 0.4f, w, 1.2f, 0f, 1.2f), poly(w * 0.4f, 1.2f, w * 0.4f, 0.8f, w * 0.6f, 0.8f, w * 0.6f, 1.2f))
            }
            "smiley" -> {
                val face = (0..40).map { i -> val a = i / 40f * 2 * PI.toFloat(); pt(0.5f + 0.5f * cos(a), 0.5f + 0.5f * sin(a)) }
                val e1 = listOf(pt(0.33f, 0.35f), pt(0.34f, 0.37f)); val e2 = listOf(pt(0.66f, 0.35f), pt(0.67f, 0.37f))
                val mouth = (0..12).map { i -> val a = PI.toFloat() * (0.15f + 0.7f * i / 12f); pt(0.5f + 0.28f * cos(a), 0.5f + 0.25f * sin(a)) }
                listOf(face, e1, e2, mouth)
            }
            "arrow" -> {
                val w = 1.5f + r.nextFloat() * 2f
                listOf(poly(0f, 0.5f, w, 0.5f), poly(w - 0.35f, 0.2f, w, 0.5f, w - 0.35f, 0.8f))
            }
            "wave" -> {
                val per = 3 + r.nextInt(4)
                val w = per * (0.5f + r.nextFloat() * 0.4f)
                listOf((0..per * 16).map { i -> val t = i / (per * 16f); pt(t * w, 0.5f + 0.3f * sin(t * per * 2 * PI.toFloat())) })
            }
            else -> error(kind)
        }
        val noise = h * 0.003f
        var t = 0L
        return strokes.map { st ->
            val pts = timeSample(st, h * 0.005f, 10f, r, noise, t)
            t = pts.last().t + 200
            InkStroke(pts)
        }
    }

    internal val drawingKinds = listOf("spiral", "zigzag", "rectangle", "scribble", "star", "house", "smiley", "arrow", "wave")
}
