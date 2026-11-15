package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * v52 not almada tablo: tek bir şekil elemanı (BrushType.SHAPE, kind [KIND]). Bütün içerik ve ayarlar şeklin
 * değerlerinde (ShapeSpec.params) saklanır; şekil her değişiklikte bulunduğu kutuda yeniden kurulur (bkz.
 * ShapeFactory.rebuild). iOS'taki `TableShape` ile aynı anahtarlar ve aynı yerleşim.
 *
 * Genel anahtarlar: rows, cols, hr (başlık satırı 1/0), hc (başlık sütunu), zebra (dönüşümlü satır rengi),
 * border (all | outer | rows | header | none), bc (kenar rengi), bw (kenar kalınlığı çarpanı), hf / ht (başlık
 * dolgu / yazı rengi), zf (dönüşümlü satır rengi), bg (hücre zemini), tc (yazı rengi), fs (yazı boyu, satır
 * yüksekliğine oranla), al (hizalama l | c | r).
 * Satır ayarları `R.<ayar>.<satır>`, sütun ayarları `C.<ayar>.<sütun>`: h / w (yükseklik / genişlik payı), a
 * (hizalama), f (dolgu), t (yazı rengi), b (kalın 1/0). Hücre yazısı `T.<satır>.<sütun>`.
 * Öncelik: satır ayarı > sütun ayarı > başlık > dönüşümlü satır > genel.
 */
object TableShape {
    const val KIND = "dg.table"
    const val MAX_ROWS = 40
    const val MAX_COLS = 12
    /** Hücre yazısı etiketinin tanımı: "tbl:" + hizalama (l/c/r) + kalınsa "b". */
    const val TAG = "tbl:"

    fun isTable(kind: String?): Boolean = kind == KIND

    /** Hazır görünümler. */
    data class Preset(val id: String, val border: String, val headerFill: String, val headerText: String, val zebraFill: String, val bg: String = "#FFFFFF", val text: String = "#263238", val borderStyle: String = "all")

    val presets = listOf(
        Preset("classic", "#455A64", "#ECEFF1", "#263238", "#F5F7F8"),
        Preset("blue", "#1565C0", "#1565C0", "#FFFFFF", "#E3F2FD"),
        Preset("green", "#2E7D32", "#2E7D32", "#FFFFFF", "#E8F5E9"),
        Preset("orange", "#E65100", "#EF6C00", "#FFFFFF", "#FFF3E0"),
        Preset("purple", "#6A1B9A", "#7B1FA2", "#FFFFFF", "#F3E5F5"),
        Preset("minimal", "#9E9E9E", "#FFFFFF", "#111111", "#FAFAFA", borderStyle = "rows"),
    )

    val borderStyles = listOf("all", "outer", "rows", "header", "none")

    /** Seçilebilir renkler (ayar pencerelerinde). */
    val palette = listOf(
        "#FFFFFF", "#F5F5F5", "#ECEFF1", "#FFF9C4", "#FFE0B2", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#BBDEFB", "#B2EBF2", "#C8E6C9", "#DCEDC8",
        "#263238", "#000000", "#455A64", "#1565C0", "#2E7D32", "#C62828", "#AD1457", "#6A1B9A", "#E65100", "#F9A825", "#00838F", "#5D4037",
    )

    /** Yeni tablonun değerleri. */
    fun create(rows: Int, cols: Int, header: Boolean, headerCol: Boolean, zebra: Boolean, borderStyle: String, preset: Preset): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m["rows"] = rows.coerceIn(1, MAX_ROWS).toString()
        m["cols"] = cols.coerceIn(1, MAX_COLS).toString()
        m["hr"] = if (header) "1" else "0"
        m["hc"] = if (headerCol) "1" else "0"
        m["zebra"] = if (zebra) "1" else "0"
        m["border"] = borderStyle
        applyPreset(m, preset)
        return m
    }

    fun applyPreset(m: MutableMap<String, String>, p: Preset) {
        m["bc"] = p.border; m["hf"] = p.headerFill; m["ht"] = p.headerText; m["zf"] = p.zebraFill; m["bg"] = p.bg; m["tc"] = p.text
    }

    // ------------------------------------------------------------------ okuma

    fun rows(p: Map<String, String>?): Int = p?.get("rows")?.toIntOrNull()?.coerceIn(1, MAX_ROWS) ?: 3
    fun cols(p: Map<String, String>?): Int = p?.get("cols")?.toIntOrNull()?.coerceIn(1, MAX_COLS) ?: 3
    fun flag(p: Map<String, String>?, key: String, def: Boolean = false): Boolean = p?.get(key)?.let { it == "1" } ?: def
    fun cell(p: Map<String, String>?, r: Int, c: Int): String = p?.get("T.$r.$c").orEmpty()
    fun rowProp(p: Map<String, String>?, prop: String, r: Int): String? = p?.get("R.$prop.$r")?.takeIf { it.isNotEmpty() }
    fun colProp(p: Map<String, String>?, prop: String, c: Int): String? = p?.get("C.$prop.$c")?.takeIf { it.isNotEmpty() }
    private fun g(p: Map<String, String>?, key: String, def: String): String = p?.get(key)?.takeIf { it.isNotEmpty() } ?: def

    fun isHeader(p: Map<String, String>?, r: Int, c: Int): Boolean = (r == 0 && flag(p, "hr", true)) || (c == 0 && flag(p, "hc"))

    fun fillOf(p: Map<String, String>?, r: Int, c: Int): String {
        rowProp(p, "f", r)?.let { return it }
        colProp(p, "f", c)?.let { return it }
        if (isHeader(p, r, c)) return g(p, "hf", "#ECEFF1")
        val body = r - if (flag(p, "hr", true)) 1 else 0
        if (flag(p, "zebra") && body >= 0 && body % 2 == 1) return g(p, "zf", "#F5F7F8")
        return g(p, "bg", "#FFFFFF")
    }

    fun textOf(p: Map<String, String>?, r: Int, c: Int): String =
        rowProp(p, "t", r) ?: colProp(p, "t", c) ?: if (isHeader(p, r, c)) g(p, "ht", "#263238") else g(p, "tc", "#263238")

    fun boldOf(p: Map<String, String>?, r: Int, c: Int): Boolean =
        (rowProp(p, "b", r) ?: colProp(p, "b", c))?.let { it == "1" } ?: isHeader(p, r, c)

    fun alignOf(p: Map<String, String>?, r: Int, c: Int): String =
        rowProp(p, "a", r) ?: colProp(p, "a", c) ?: if (r == 0 && flag(p, "hr", true)) "c" else g(p, "al", "l")

    private fun weight(v: String?): Float = v?.toFloatOrNull()?.coerceIn(0.2f, 8f) ?: 1f

    // ------------------------------------------------------------------ çizim

    /** Tablonun parçaları ([start]..[end] kutusunda). */
    fun paths(start: Offset, end: Offset, ink: Float, p: Map<String, String>?): List<ShapePath> {
        val left = min(start.x, end.x); val top = min(start.y, end.y)
        val width = max(8f, kotlin.math.abs(end.x - start.x)); val height = max(8f, kotlin.math.abs(end.y - start.y))
        val nr = rows(p); val nc = cols(p)
        val rw = (0 until nr).map { weight(rowProp(p, "h", it)) }
        val cw = (0 until nc).map { weight(colProp(p, "w", it)) }
        val xs = FloatArray(nc + 1).also { a -> var s = 0f; val t = cw.sum(); for (i in 0 until nc) { a[i] = left + width * s / t; s += cw[i] }; a[nc] = left + width }
        val ys = FloatArray(nr + 1).also { a -> var s = 0f; val t = rw.sum(); for (i in 0 until nr) { a[i] = top + height * s / t; s += rw[i] }; a[nr] = top + height }
        val out = ArrayList<ShapePath>()
        // Hücre zeminleri (kenarsız dolu dikdörtgenler)
        for (r in 0 until nr) for (c in 0 until nc) {
            val f = fillOf(p, r, c)
            out += rect(xs[c], ys[r], xs[c + 1], ys[r + 1], f, "cell:$r:$c")
        }
        // Çizgiler
        val bc = g(p, "bc", "#455A64")
        val bw = (p?.get("bw")?.toFloatOrNull() ?: 1f).coerceIn(0.25f, 4f)
        val style = g(p, "border", "all")
        val header = flag(p, "hr", true)
        fun line(a: Offset, b: Offset, scale: Float, tag: String) {
            out += ShapePath(listOf(a, b), strokeColorHex = bc, widthScale = scale * bw, tag = tag)
        }
        if (style != "none") {
            // Dış çerçeve (satırlar biçiminde yalnız üst ve alt)
            if (style == "rows") {
                line(Offset(left, top), Offset(left + width, top), 1f, "border:top")
                line(Offset(left, top + height), Offset(left + width, top + height), 1f, "border:bottom")
            } else {
                out += ShapePath(
                    listOf(Offset(left, top), Offset(left + width, top), Offset(left + width, top + height), Offset(left, top + height), Offset(left, top)),
                    closed = true, strokeColorHex = bc, widthScale = bw, tag = "border:outer", fillColorHex = null,
                )
            }
            for (r in 1 until nr) {
                val headLine = header && r == 1
                val draw = when (style) { "all", "rows" -> true; "header" -> headLine; else -> false }
                if (draw || headLine && style != "outer") line(Offset(left, ys[r]), Offset(left + width, ys[r]), if (headLine) 2f else 0.6f, "border:r:$r")
            }
            if (style == "all") for (c in 1 until nc) {
                val headLine = flag(p, "hc") && c == 1
                line(Offset(xs[c], top), Offset(xs[c], top + height), if (headLine) 2f else 0.6f, "border:c:$c")
            }
        }
        // Yazılar
        val fs = (p?.get("fs")?.toFloatOrNull() ?: 0.42f).coerceIn(0.15f, 0.9f)
        val minRow = (0 until nr).minOf { ys[it + 1] - ys[it] }
        for (r in 0 until nr) for (c in 0 until nc) {
            val text = cell(p, r, c)
            if (text.isBlank()) continue
            val x0 = xs[c]; val x1 = xs[c + 1]; val y0 = ys[r]; val y1 = ys[r + 1]
            val bold = boldOf(p, r, c)
            val pad = min(minRow * 0.22f, (x1 - x0) * 0.08f)
            var size = min(minRow, y1 - y0) * fs
            val est = text.length * size * (if (bold) 0.6f else 0.55f)
            if (est > x1 - x0 - 2 * pad) size = max(4f, size * (x1 - x0 - 2 * pad) / est)
            val align = alignOf(p, r, c)
            val x = when (align) { "c" -> (x0 + x1) / 2f; "r" -> x1 - pad; else -> x0 + pad }
            val y = (y0 + y1) / 2f - size * 0.5f
            out += ShapePath(listOf(Offset(x, y)), label = text, labelSize = size, strokeColorHex = textOf(p, r, c), tag = TAG + align + if (bold) "b" else "")
        }
        return out
    }

    private fun rect(l: Float, t: Float, r: Float, b: Float, fill: String, tag: String) = ShapePath(
        listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b), Offset(l, t)), closed = true, fillColorHex = fill, widthScale = 0f, tag = tag,
    )

    // ------------------------------------------------------------------ düzenleme (değer haritası üzerinde)

    /** [at] konumuna boş satır ekler (sonrakiler kayar). */
    fun insertRow(p: Map<String, String>, at: Int): Map<String, String> {
        val n = rows(p); if (n >= MAX_ROWS) return p
        return shift(p, isRow = true, from = at, delta = 1) + ("rows" to (n + 1).toString())
    }

    fun deleteRow(p: Map<String, String>, at: Int): Map<String, String> {
        val n = rows(p); if (n <= 1) return p
        return shift(p.filterKeys { idx(it, true) != at }, isRow = true, from = at + 1, delta = -1) + ("rows" to (n - 1).toString())
    }

    fun insertCol(p: Map<String, String>, at: Int): Map<String, String> {
        val n = cols(p); if (n >= MAX_COLS) return p
        return shift(p, isRow = false, from = at, delta = 1) + ("cols" to (n + 1).toString())
    }

    fun deleteCol(p: Map<String, String>, at: Int): Map<String, String> {
        val n = cols(p); if (n <= 1) return p
        return shift(p.filterKeys { idx(it, false) != at }, isRow = false, from = at + 1, delta = -1) + ("cols" to (n - 1).toString())
    }

    /** Satır/sütun sayısı (fazlası silinir). */
    fun resize(p: Map<String, String>, rows: Int, cols: Int): Map<String, String> {
        val nr = rows.coerceIn(1, MAX_ROWS); val nc = cols.coerceIn(1, MAX_COLS)
        return p.filterKeys { k -> (idx(k, true)?.let { it < nr } ?: true) && (idx(k, false)?.let { it < nc } ?: true) } +
            mapOf("rows" to nr.toString(), "cols" to nc.toString())
    }

    /** Anahtarın satır (ya da sütun) sırası; ilgili değilse null. */
    private fun idx(key: String, isRow: Boolean): Int? {
        val parts = key.split('.')
        return when {
            parts.size == 3 && parts[0] == "T" -> (if (isRow) parts[1] else parts[2]).toIntOrNull()
            parts.size == 3 && parts[0] == (if (isRow) "R" else "C") -> parts[2].toIntOrNull()
            else -> null
        }
    }

    private fun shift(p: Map<String, String>, isRow: Boolean, from: Int, delta: Int): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in p) {
            val i = idx(k, isRow)
            if (i == null || i < from) { out[k] = v; continue }
            val parts = k.split('.').toMutableList()
            if (parts[0] == "T") { if (isRow) parts[1] = (i + delta).toString() else parts[2] = (i + delta).toString() } else parts[2] = (i + delta).toString()
            out[parts.joinToString(".")] = v
        }
        return out
    }

    /** Satır ([isRow]) ya da sütun ayarı; [value] null ise kaldırılır. */
    fun setLineProp(p: Map<String, String>, isRow: Boolean, prop: String, index: Int, value: String?): Map<String, String> {
        val key = "${if (isRow) "R" else "C"}.$prop.$index"
        return if (value == null) p - key else p + (key to value)
    }

    /** Genel ayar; [clearProp] verilirse bütün satır ve sütunlardaki aynı ayar kaldırılır ("hepsi"). */
    fun setGlobal(p: Map<String, String>, key: String, value: String?, clearProp: String? = null): Map<String, String> {
        var m = if (value == null) p - key else p + (key to value)
        if (clearProp != null) m = m.filterKeys { k -> !(k.startsWith("R.$clearProp.") || k.startsWith("C.$clearProp.")) }
        return m
    }

    fun setCell(p: Map<String, String>, r: Int, c: Int, text: String): Map<String, String> =
        if (text.isEmpty()) p - "T.$r.$c" else p + ("T.$r.$c" to text)

    /** Sütun harfi (A, B, …). */
    fun colName(c: Int): String = ('A' + c).toString()

    /** Satır/sütun payı değişince kutunun yeni boyu (eleman büyür ya da küçülür; hücreler aynı boyda kalır). */
    fun weightSum(p: Map<String, String>?, isRow: Boolean): Float =
        if (isRow) (0 until rows(p)).sumOf { weight(rowProp(p, "h", it)).toDouble() }.toFloat()
        else (0 until cols(p)).sumOf { weight(colProp(p, "w", it)).toDouble() }.toFloat()
}
