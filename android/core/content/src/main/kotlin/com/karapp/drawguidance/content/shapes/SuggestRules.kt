package com.karapp.drawguidance.content.shapes

import kotlin.math.max

/**
 * v38 öneri kuralları (bütün modlarda: resim, not alma, çizgi roman, çocuk, basit resim, basit not). Öneriler üç ayrı
 * türdür; her türün kendi algoritması, kendi balonu, kendi ayarı ve kendi tek düğmeli düzeltmesi vardır:
 *
 * | Tür | Gruplama kuralı | Algoritma | Düzeltme (tek düğme, tek geri alma adımı) |
 * |---|---|---|---|
 * | Çizgi | en az [LINE_MIN] çizgi, birbirine yakın ([LINE_PAD]) | klasik makine öğrenmesi ve geometri: en küçük kareler/kovaryans ile çember-elips, RDP köşe bulma ile doğru-kırık çizgi-çokgen, Gauss yumuşatma, uç noktaların tek bağlantılı kümelenmesi ([com.karapp.drawguidance.content.ink.InkLines]) | dikkate alınan bütün çizgiler düzgün haline |
 * | Yazı | birbirine yakın en az [TEXT_MIN] eleman (aynı satırda harf aralığıyla zincirlenen) | yazı sinir ağı ([com.karapp.drawguidance.content.ink.HandwritingNet]: yazı mı kapısı + karakter başları) ve çözücü | bütün çizgiler tek yazıya |
 * | Şekil | birbirine yakın en az [SHAPE_MIN] eleman ([SHAPE_PAD]) | modun şekil sinir ağı ([ShapeNet], v38: 4+ elemanlı çizimlerle eğitildi) + şablon veritabanı araması ([ShapeIndex]) | bütün çizgiler şablonun çizgileriyle |
 *
 * v39 tek eleman: tek çizgiye şekil önerisi hiç yapılmaz (en az [SHAPE_MIN]); yazı ve çizgi önerisi tek çizgide
 * yalnız çizim miktarı fazlaysa yapılır ([bigLine], [bigText]). Küçük tek çizgiye hiçbir türde öneri yoktur.
 *
 * Çizim miktarı tuvalin kısa kenarına göre ölçülür (tuval büyüdükçe eşik de büyür; çok küçük tuvallerde px tabanları):
 * - Çizgi: yol uzunluğu ≥ kısa kenarın [LINE_SINGLE_LENGTH] katı (en az [LINE_SINGLE_MIN_LENGTH] px), kutunun uzun kenarı
 *   ≥ kısa kenarın [LINE_SINGLE_SPAN] katı (en az [LINE_SINGLE_MIN_SPAN] px) ve en az [LINE_SINGLE_POINTS] nokta. Örnek
 *   1000 px tuvalde: 750 px'lik çizim (yarım sayfa genişliğinde bir dalga, büyük bir çember ya da tek çizgiyle kutu).
 * - Yazı: tanıyıcı her çizgiyi en çok bir karaktere böldüğünden tek çizgi tek büyük harf/rakamdır (2, 3, 8, S, m, e…):
 *   yol uzunluğu ≥ kısa kenarın [TEXT_SINGLE_LENGTH] katı (en az [TEXT_SINGLE_MIN_LENGTH] px), yükseklik ≥ kısa kenarın
 *   [TEXT_SINGLE_HEIGHT] katı (en az [TEXT_SINGLE_MIN_HEIGHT] px), en az [TEXT_SINGLE_POINTS] nokta ve tanıma güveni ≥
 *   [TEXT_SINGLE_CONFIDENCE] (daire ve dik çizgi gibi çizim de olabilen karakterler bu güvenin altında kalır).
 */
object SuggestRules {
    /** Çizgi önerisi bu kadar çizgiden başlar (v39: tek çizgide yalnız [bigLine] ise). */
    const val LINE_MIN = 2

    /** Yazı önerisi birbirine yakın bu kadar elemandan başlar (v39: tek çizgide yalnız [bigText] ise). */
    const val TEXT_MIN = 2

    /** v39 tek çizginin çizgi önerisi: yol uzunluğu / tuvalin kısa kenarı. */
    const val LINE_SINGLE_LENGTH = 0.75f
    /** v39 tek çizginin çizgi önerisi: en kısa yol (px; küçük tuvallerde taban). */
    const val LINE_SINGLE_MIN_LENGTH = 400f
    /** v39 tek çizginin çizgi önerisi: kutunun uzun kenarı / tuvalin kısa kenarı (yerinde karalama sayılmaz). */
    const val LINE_SINGLE_SPAN = 0.25f
    const val LINE_SINGLE_MIN_SPAN = 120f
    /** v39 tek çizginin çizgi önerisi: en az nokta (hızlı tek çekilmiş düz çizgi az noktalıdır). */
    const val LINE_SINGLE_POINTS = 40

    /** v39 tek çizginin yazı önerisi: yol uzunluğu / tuvalin kısa kenarı. */
    const val TEXT_SINGLE_LENGTH = 0.3f
    const val TEXT_SINGLE_MIN_LENGTH = 200f
    /** v39 tek çizginin yazı önerisi: yükseklik / tuvalin kısa kenarı (olağan not yazısının harfleri bunun altında). */
    const val TEXT_SINGLE_HEIGHT = 0.06f
    const val TEXT_SINGLE_MIN_HEIGHT = 40f
    const val TEXT_SINGLE_POINTS = 30
    /** v39 tek çizginin yazı önerisi: en az tanıma güveni (çok çizgili yazıdaki eşikten sıkı). */
    const val TEXT_SINGLE_CONFIDENCE = 0.9f

    /** Tuval boyutu bilinmiyorsa kısa kenar (yeni tuvallerin olağan genişliği). */
    const val DEFAULT_CANVAS_SIDE = 1000f

    /** Şekil önerisi birbirine yakın bu kadar elemandan başlar. */
    const val SHAPE_MIN = 4

    /** Bu kadar elemandan fazlası tek şekil sayılmaz. */
    const val SHAPE_MAX = SelectionSuggest.MAX_ELEMENTS

    /** Çizgi önerisinde birlikte düzeltilecek en çok çizgi. */
    const val LINE_MAX = 60

    /** Şekil grubunda yakınlık: kutular arası boşluk ≤ büyük kutunun uzun kenarının bu katı (en az [SHAPE_MIN_PAD] px). */
    const val SHAPE_PAD = 0.2f
    const val SHAPE_MIN_PAD = 12f

    /** Çizgi grubunda yakınlık (şekilden gevşek: aynı çizimin ayrık kenarları da birlikte düzelir). */
    const val LINE_PAD = 0.6f
    const val LINE_MIN_PAD = 24f

    /** v39 tuvalin kısa kenarı ([w] ya da [h] bilinmiyorsa [DEFAULT_CANVAS_SIDE]). */
    fun side(w: Float, h: Float): Float = if (w > 0f && h > 0f) kotlin.math.min(w, h) else DEFAULT_CANVAS_SIDE

    /**
     * v39 tek çizginin çizgi önerisi alacak kadar çok çizim olup olmadığı: [length] yol uzunluğu, [points] nokta sayısı,
     * [w]×[h] çizginin kutusu, [canvasW]×[canvasH] tuval (0: bilinmiyor).
     */
    fun bigLine(length: Float, points: Int, w: Float, h: Float, canvasW: Float, canvasH: Float): Boolean {
        val side = side(canvasW, canvasH)
        return points >= LINE_SINGLE_POINTS &&
            length >= max(LINE_SINGLE_MIN_LENGTH, side * LINE_SINGLE_LENGTH) &&
            max(w, h) >= max(LINE_SINGLE_MIN_SPAN, side * LINE_SINGLE_SPAN)
    }

    /**
     * v39 tek çizginin yazı önerisine aday olacak kadar çok çizim olup olmadığı (tanıma güveni ayrıca
     * [TEXT_SINGLE_CONFIDENCE] ile denetlenir). Parametreler [bigLine] gibi.
     */
    fun bigText(length: Float, points: Int, w: Float, h: Float, canvasW: Float, canvasH: Float): Boolean {
        val side = side(canvasW, canvasH)
        return points >= TEXT_SINGLE_POINTS &&
            length >= max(TEXT_SINGLE_MIN_LENGTH, side * TEXT_SINGLE_LENGTH) &&
            h >= max(TEXT_SINGLE_MIN_HEIGHT, side * TEXT_SINGLE_HEIGHT)
    }

    /** v39 nokta dizisinin yol uzunluğu ([xs], [ys] aynı uzunlukta). */
    fun pathLength(xs: FloatArray, ys: FloatArray): Float {
        var len = 0f
        for (i in 1 until minOf(xs.size, ys.size)) len += kotlin.math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        return len
    }

    /**
     * [boxes] (sol, üst, sağ, alt) içinde [seed] ile birbirine yakın olanlar (tekrarlı: zincirleme yakınlık), sıralı
     * indisler. Uzak duran kutular gruba girmez.
     */
    fun near(boxes: List<FloatArray>, seed: Int, pad: Float, minPad: Float, maxCount: Int = Int.MAX_VALUE): List<Int> =
        TemplateGrouping.cluster(boxes, seed, pad, minPad, maxCount)

    /** [boxes] içindeki yakınlık kümeleri (bağlı bileşenler), büyükten küçüğe (eşitlikte sonra çizilen önce). */
    fun clusters(boxes: List<FloatArray>, pad: Float, minPad: Float): List<List<Int>> {
        val seen = BooleanArray(boxes.size)
        val out = ArrayList<List<Int>>()
        for (i in boxes.indices.reversed()) {
            if (seen[i]) continue
            val c = near(boxes, i, pad, minPad, max(1, boxes.size))
            c.forEach { seen[it] = true }
            out += c
        }
        return out.sortedWith(compareByDescending<List<Int>> { it.size }.thenByDescending { it.max() })
    }
}
