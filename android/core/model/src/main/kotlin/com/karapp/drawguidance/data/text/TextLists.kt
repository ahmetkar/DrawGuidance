package com.karapp.drawguidance.data.text

import com.karapp.drawguidance.data.model.TextParagraph

/**
 * Not yazılarında liste işaretleri: madde (•, –, ·), numara (1. / a. / i.) ve yapılacaklar (☐). İşaretler
 * yazının içinde durur (düzenlerken de görünür); satır başındaki her 4 boşluk bir liste seviyesidir.
 * Hepsi saf işlemlerdir.
 */
object TextLists {
    const val INDENT = "    "
    private const val MAX_LEVEL = 5
    private val bullets = listOf("•", "–", "·")
    private val checks = listOf("☐", "☑")

    /** Bir satırın parçaları: seviye (girinti), işaret (sonundaki boşluk dahil) ve metin. */
    data class Line(val level: Int, val marker: String, val body: String) {
        val hasMarker: Boolean get() = marker.isNotEmpty()
    }

    private val markerRegex = Regex("^(•|–|·|☐|☑|\\d{1,3}\\.|[a-z]{1,2}\\.|[ivxlc]{1,5}\\.) ")

    fun parse(line: String): Line {
        var i = 0
        var spaces = 0
        while (i < line.length && (line[i] == ' ' || line[i] == '\t')) {
            spaces += if (line[i] == '\t') 4 else 1
            i++
        }
        val rest = line.substring(i)
        val match = markerRegex.find(rest)
        return if (match != null) Line(minOf(MAX_LEVEL, spaces / 4), match.value, rest.substring(match.value.length))
        else Line(minOf(MAX_LEVEL, spaces / 4), "", rest)
    }

    /** [level] seviyesindeki [index]. (1'den başlar) öğenin işareti. */
    fun marker(paragraph: TextParagraph, level: Int, index: Int, checked: Boolean = false): String = when (paragraph) {
        TextParagraph.BULLET -> bullets[level % bullets.size] + " "
        TextParagraph.CHECKLIST -> (if (checked) checks[1] else checks[0]) + " "
        TextParagraph.NUMBERED -> when (level % 3) {
            0 -> "$index. "
            1 -> "${letters(index)}. "
            else -> "${roman(index)}. "
        }
        else -> ""
    }

    private fun letters(n: Int): String {
        var v = n
        val sb = StringBuilder()
        while (v > 0) { v--; sb.insert(0, 'a' + v % 26); v /= 26 }
        return sb.toString()
    }

    private fun roman(n: Int): String {
        val values = listOf(100 to "c", 90 to "xc", 50 to "l", 40 to "xl", 10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i")
        var v = n.coerceIn(1, 399)
        val sb = StringBuilder()
        for ((value, s) in values) while (v >= value) { sb.append(s); v -= value }
        return sb.toString()
    }

    private fun compose(level: Int, marker: String, body: String) = INDENT.repeat(level) + marker + body

    /**
     * Paragraf biçimini tüm yazıya uygular: listeyse her dolu satıra (seviyesine göre) işaret konur ve
     * numaralar sıralanır; liste değilse işaretler ve liste girintileri kaldırılır.
     */
    fun apply(content: String, paragraph: TextParagraph): String {
        val lines = content.split('\n').map(::parse)
        if (!paragraph.isList) return lines.joinToString("\n") { it.body }
        // Boş yazıda liste seçilince ilk işaret hemen konur (yazmaya işaretten sonra başlanır)
        if (lines.all { it.body.isEmpty() }) return marker(paragraph, 0, 1)
        val out = lines.map { line ->
            if (line.body.isEmpty() && !line.hasMarker) line
            else line.copy(marker = marker(paragraph, line.level, 1, checked = line.marker.startsWith(checks[1])))
        }
        return renumber(out, paragraph).joinToString("\n") { compose(it.level, it.marker, it.body) }
    }

    /** Numaralı listede numaraları, maddeli listede seviye işaretlerini düzeltir (işareti olan satırlarda). */
    fun normalize(content: String, paragraph: TextParagraph): String {
        if (!paragraph.isList) return content
        val lines = content.split('\n').map(::parse)
        return renumber(lines, paragraph).joinToString("\n") { compose(it.level, it.marker, it.body) }
    }

    private fun renumber(lines: List<Line>, paragraph: TextParagraph): List<Line> {
        val counters = IntArray(MAX_LEVEL + 1)
        return lines.map { line ->
            if (!line.hasMarker) {
                // İşaretsiz dolu satır listeyi böler: numaralar baştan başlar
                if (line.body.isNotEmpty()) counters.fill(0)
                return@map line
            }
            counters[line.level]++
            for (deeper in line.level + 1..MAX_LEVEL) counters[deeper] = 0
            line.copy(marker = marker(paragraph, line.level, counters[line.level], checked = line.marker.startsWith(checks[1])))
        }
    }

    /** Düzenleme sonucu: yeni yazı ve imlecin yeri. */
    data class Edit(val text: String, val cursor: Int)

    /**
     * Yazılan değişikliğe listeyi uygular: Enter'a basılınca yeni satır bir sonraki işaretle başlar; boş bir
     * öğede Enter listeyi bitirir (işaret silinir). Numaralar her değişiklikte sıralanır.
     */
    fun onEdit(old: String, new: String, cursor: Int, paragraph: TextParagraph): Edit {
        if (!paragraph.isList) return Edit(new, cursor)
        val inserted = new.length == old.length + 1 && cursor in 1..new.length && new[cursor - 1] == '\n' &&
            new.substring(0, cursor - 1) + new.substring(cursor) == old
        if (!inserted) return normalized(new, cursor, paragraph)
        val lineStart = new.lastIndexOf('\n', cursor - 2).let { if (it < 0) 0 else it + 1 }
        val previous = parse(new.substring(lineStart, cursor - 1))
        if (!previous.hasMarker) return normalized(new, cursor, paragraph)
        if (previous.body.isBlank()) {
            // Boş öğede Enter: işaret kalkar, satır boş kalır (liste biter)
            val text = new.substring(0, lineStart) + new.substring(cursor)
            return normalized(text, lineStart, paragraph)
        }
        val prefix = INDENT.repeat(previous.level) + marker(paragraph, previous.level, 1)
        val text = new.substring(0, cursor) + prefix + new.substring(cursor)
        return normalized(text, cursor + prefix.length, paragraph)
    }

    /** İmlecin bulunduğu satırın liste seviyesini [delta] kadar değiştirir. */
    fun indent(content: String, cursor: Int, paragraph: TextParagraph, delta: Int): Edit {
        val c = cursor.coerceIn(0, content.length)
        val lineStart = content.lastIndexOf('\n', c - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = content.indexOf('\n', c).let { if (it < 0) content.length else it }
        val line = parse(content.substring(lineStart, lineEnd))
        val level = (line.level + delta).coerceIn(0, MAX_LEVEL)
        if (level == line.level) return Edit(content, c)
        val marker = if (paragraph.isList) marker(paragraph, level, 1, checked = line.marker.startsWith(checks[1])) else line.marker
        val replaced = compose(level, marker, line.body)
        val text = content.substring(0, lineStart) + replaced + content.substring(lineEnd)
        val offsetInBody = (c - lineStart) - (compose(line.level, line.marker, "").length)
        val newCursor = lineStart + compose(level, marker, "").length + offsetInBody.coerceIn(0, line.body.length)
        return normalized(text, newCursor, paragraph)
    }

    /** Numaralamadan sonra imleç aynı satırda, satır sonuna göre aynı yerde kalır. */
    private fun normalized(text: String, cursor: Int, paragraph: TextParagraph): Edit {
        val result = normalize(text, paragraph)
        if (result == text) return Edit(text, cursor.coerceIn(0, text.length))
        val c = cursor.coerceIn(0, text.length)
        val lineIndex = text.substring(0, c).count { it == '\n' }
        val fromEnd = text.indexOf('\n', c).let { if (it < 0) text.length else it } - c
        val lines = result.split('\n')
        var start = 0
        for (i in 0 until lineIndex) start += lines[i].length + 1
        val end = start + lines[lineIndex].length
        return Edit(result, (end - fromEnd).coerceIn(start, end))
    }
}
