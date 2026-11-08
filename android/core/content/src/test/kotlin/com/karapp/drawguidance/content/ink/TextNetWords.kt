package com.karapp.drawguidance.content.ink

import java.util.Random

/** v37 çözücü sınaması: yazı sistemi başına sözcükler ve yapay satırlardaki okuma doğruluğu. */
internal object TextNetWords {
    val words: Map<InkScript, List<String>> = mapOf(
        InkScript.CYRILLIC to listOf(
            "привет", "мир", "дом", "кот", "книга", "школа", "вода", "яблоко", "мама", "солнце", "день", "друг", "жизнь",
            "время", "рука", "город", "слово", "работа", "ночь", "чай", "небо", "щука", "съезд", "юг", "эхо",
        ),
        InkScript.ARABIC to listOf(
            "سلام", "كتاب", "بيت", "قلم", "شمس", "ماء", "باب", "ولد", "نور", "علم", "قمر", "بحر", "جبل", "خبز", "ورد",
            "ثلج", "ذهب", "ظل", "عين", "غرفة", "٢٠٢٤", "٣٥", "١٩",
        ),
        InkScript.JAPANESE to listOf(
            "ありがとう", "こんにちは", "さくら", "ねこ", "やま", "カメラ", "テレビ", "コーヒー", "日本", "学生", "先生", "山川",
            "今日", "大学", "子ども", "ひらがな", "カタカナ", "三月", "火よう日", "白い花", "赤い車", "小さな犬", "みず", "ゆき",
        ),
        InkScript.KOREAN to listOf(
            "안녕", "한국", "사랑", "감사합니다", "학교", "친구", "사람", "물", "하늘", "가방", "시간", "우리", "대학교", "나무",
            "바다", "음식", "노래", "선생님", "책", "꽃", "길", "밥", "손", "눈",
        ),
    )

    val hints = mapOf(InkScript.CYRILLIC to "ru", InkScript.ARABIC to "ar", InkScript.JAPANESE to "ja", InkScript.KOREAN to "ko", InkScript.LATIN to "tr")

    class Score(var n: Int = 0, var exact: Int = 0, var chars: Int = 0, var charErr: Int = 0, var script: Int = 0, var none: Int = 0) {
        val fails = ArrayList<String>()
        fun exactPct() = if (n == 0) 0f else 100f * exact / n
        fun charPct() = if (chars == 0) 0f else 100f * (chars - charErr).coerceAtLeast(0) / chars
        fun scriptPct() = if (n == 0) 0f else 100f * script / n
        override fun toString() = "tam %.1f%%, karakter %.1f%%, yazı sistemi %.1f%%, sonuçsuz %d (%d satır)".format(exactPct(), charPct(), scriptPct(), none, n)
    }

    fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return d[a.length][b.length]
    }

    /**
     * Her sözcük [reps] kez (farklı tohum ve yazı tipiyle) yazılır ve okunur. [hint]: dil ipucu verilsin mi (yoksa
     * kendiliğinden bulma). [twoWords]: iki sözcüklük satırlar da (boşluk ölçümü).
     */
    fun score(script: InkScript, fonts: List<String>, reps: Int, hint: Boolean, seed: Long = TextNetEval.EVAL_SEED + 90_000_000L, twoWords: Boolean = false): Score {
        val sc = Score()
        val list = words.getValue(script)
        var k = 0
        for (rep in 0 until reps) for ((wi, w) in list.withIndex()) {
            val r = Random(seed + rep * 1000L + wi)
            val sep = if (script == InkScript.JAPANESE) "" else " "
            val text = if (twoWords && wi % 3 == 0) w + sep + list[(wi + 1) % list.size] else w
            val chars = splitChars(text)
            val ln = TextNetData.line(script, chars, fonts, r) ?: continue
            k++
            val ink = GlyphInk.toInk(ln.strokes)
            val lang = if (hint) hints[script] else null
            val res = Handwriting.analyze(ink, InkMode.MIXED, true, lang)
            val detected = Handwriting.detectScript(ink, lang)
            sc.n++
            if (detected == script) sc.script++
            val want = text
            val got = res?.text ?: ""
            if (res == null) sc.none++
            if (got == want) sc.exact++ else if (sc.fails.size < 30) sc.fails += "$want→$got"

            sc.chars += want.replace(" ", "").length
            sc.charErr += minOf(distance(got.replace(" ", ""), want.replace(" ", "")), want.replace(" ", "").length)
        }
        return sc
    }

    /** Metni çizilecek karakterlere böler (boşluk korunur). */
    fun splitChars(text: String): List<String> = text.map { it.toString() }
}
