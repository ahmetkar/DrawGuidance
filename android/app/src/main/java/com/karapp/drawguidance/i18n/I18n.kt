package com.karapp.drawguidance.i18n

import java.util.Locale

/**
 * v34 koddaki Türkçe metinlerin (hazır şekil, formül, diyagram, 3B model adları; görünüm modeli iletileri) çalışma
 * anındaki çevirisi. Arayüz metinlerinin çoğu Android kaynaklarındadır (`values-xx`); burada yalnız kodda yazılı
 * metinler çevrilir.
 *
 * - Tablolar `resources/i18n/<dil>.tsv` dosyalarındadır: her satır `Türkçe<TAB>çeviri` (`\n` satır sonu, `\t` sekme).
 * - [tr] Türkçe metnin karşılığını verir; tabloda yoksa metnin kendisi döner (kullanıcının yazdığı metinler değişmez).
 * - [trf] yer tutuculu kalıpları (`{0}`, `{1}` …) çevirip doldurur.
 * - Dil uygulama açılırken bir kez seçilir ([setLanguage]); dil değişince uygulama yeniden başlatılır.
 */
object I18n {
    const val DEFAULT = "tr"

    @Volatile
    var language: String = DEFAULT
        private set

    @Volatile
    private var table: Map<String, String> = emptyMap()

    /** Etkin dil için tablo yüklenir (Türkçede tablo yok: metinler zaten Türkçe). */
    fun setLanguage(code: String) {
        val c = AppLanguage.of(code).code
        if (c == language && (c == DEFAULT || table.isNotEmpty())) return
        table = if (c == DEFAULT) emptyMap() else load(c)
        language = c
    }

    /** Dilin tablosu (testler ve kapsama denetimi için). */
    fun load(code: String): Map<String, String> {
        val stream = I18n::class.java.getResourceAsStream("/i18n/$code.tsv") ?: return emptyMap()
        val map = HashMap<String, String>(8192)
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEach
                map[unescape(line.substring(0, tab))] = unescape(line.substring(tab + 1))
            }
        }
        return map
    }

    /** Türkçe metnin etkin dildeki karşılığı (yoksa kendisi). */
    fun tr(s: String): String {
        val t = table
        if (t.isEmpty() || s.isEmpty()) return s
        t[s]?.let { return it }
        return lookup(t, s) ?: s
    }

    /** Kalıbı çevirip `{0}`, `{1}` … yerlerine [args] yazılır. */
    fun trf(pattern: String, vararg args: Any?): String {
        var out = tr(pattern)
        args.forEachIndexed { i, a -> out = out.replace("{$i}", a?.toString() ?: "") }
        return out
    }

    private fun lookup(t: Map<String, String>, s: String): String? {
        // Baştaki/sondaki boşluklar korunur
        val trimmed = s.trim()
        if (trimmed.length != s.length && trimmed.isNotEmpty()) {
            val inner = t[trimmed] ?: lookup(t, trimmed) ?: return null
            val lead = s.length - s.trimStart().length
            val trail = s.length - s.trimEnd().length
            return s.substring(0, lead) + inner + s.substring(s.length - trail)
        }
        // Sondaki iki nokta, üç nokta
        for (suffix in SUFFIXES) {
            if (s.length > suffix.length && s.endsWith(suffix)) {
                val base = s.dropLast(suffix.length)
                t[base]?.let { return it + suffix }
            }
        }
        val first = s.first()
        // Büyük harfle başlayan metin küçük harfli anahtarla (ör. çocuk şekil adları) ya da tersi
        if (first.isUpperCase()) {
            val lower = s.replaceFirstChar { it.lowercase(TR) }
            t[lower]?.let { return capitalize(it) }
            // Tamamı büyük harf (başlıklar)
            if (s.length > 1 && s.none { it.isLowerCase() } && s.any { it.isLetter() }) {
                val cap = s.lowercase(TR).replaceFirstChar { it.uppercase(TR) }
                (t[cap] ?: t[s.lowercase(TR)])?.let { return it.uppercase(locale()) }
            }
        } else if (first.isLowerCase()) {
            val cap = s.replaceFirstChar { it.uppercase(TR) }
            t[cap]?.let { return it.replaceFirstChar { c -> c.lowercase(locale()) } }
        }
        // Sonda sayı ("Katman 2", "Parça 3"): ad çevrilir, sayı kalır
        TRAILING_NUMBER.matchEntire(s)?.let { m ->
            val head = m.groupValues[1]
            t["$head {0}"]?.let { return it.replace("{0}", m.groupValues[2].trim()) }
            val r = tr(head)
            if (r != head) return r + m.groupValues[2]
        }
        // Sonda ayraç ("Silindir (kopya)", "Parça (2)")
        PAREN_SUFFIX.matchEntire(s)?.let { m ->
            val head = m.groupValues[1]
            val r = tr(head)
            if (r != head) return r + " (" + tr(m.groupValues[2]) + ")"
        }
        // "Ad: değer" (ör. "Şekil: Kare")
        val colon = s.indexOf(": ")
        if (colon > 0 && colon < s.length - 2) {
            val a = s.substring(0, colon)
            val b = s.substring(colon + 2)
            val ra = tr(a)
            val rb = tr(b)
            if (ra != a || rb != b) return "$ra: $rb"
        }
        // " · " ile birleştirilmiş parçalar ayrı ayrı
        if (s.contains(SEP)) {
            val parts = s.split(SEP)
            var any = false
            val out = parts.map { p -> val r = tr(p); if (r != p) any = true; r }
            if (any) return out.joinToString(SEP)
        }
        return null
    }

    private fun capitalize(v: String): String = v.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale()) else it.toString() }

    private fun locale(): Locale = AppLanguage.of(language).locale

    private fun unescape(s: String): String {
        if (s.indexOf('\\') < 0) return s
        val b = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> { b.append('\n'); i += 2; continue }
                    't' -> { b.append('\t'); i += 2; continue }
                    '\\' -> { b.append('\\'); i += 2; continue }
                    'u' -> if (i + 5 < s.length) {
                        s.substring(i + 2, i + 6).toIntOrNull(16)?.let { b.append(it.toChar()); i += 6; continue }
                    }
                    else -> { b.append('\\').append(n); i += 2; continue }
                }
            }
            b.append(c)
            i++
        }
        return b.toString()
    }

    private val TR = Locale("tr")
    private const val SEP = " · "
    private val SUFFIXES = listOf(":", "…", "...")
    private val TRAILING_NUMBER = Regex("^(.*[^\\d\\s])( \\d+)$")
    private val PAREN_SUFFIX = Regex("^(.+) \\(([^()]+)\\)$")
}

/** Kısa yol: `"Kare".tr()`. */
fun String.tr(): String = I18n.tr(this)
