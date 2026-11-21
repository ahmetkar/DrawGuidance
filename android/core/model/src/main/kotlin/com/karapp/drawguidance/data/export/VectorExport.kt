package com.karapp.drawguidance.data.export

import com.karapp.drawguidance.data.model.ArrowKind
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CapStyle
import com.karapp.drawguidance.data.model.JoinStyle
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.LayerBlend
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

/*
 * v56 aşama 9 (2D-3) SVG ve DXF yazıcıları (saf; iOS `VectorExport.swift` aynı belgeden aynı metni üretir).
 * Sayılar en çok iki ondalıkla, nokta ayraçla yazılır.
 */

object ExportNum {
    fun f(v: Float): String = f(v.toDouble())
    fun f(v: Double): String {
        val n = floor(v * 100.0 + 0.5).toLong()
        val neg = n < 0
        val a = abs(n)
        val ip = a / 100; val fp = (a % 100).toInt()
        val body = when {
            fp == 0 -> "$ip"
            fp % 10 == 0 -> "$ip.${fp / 10}"
            fp < 10 -> "$ip.0$fp"
            else -> "$ip.$fp"
        }
        return if (neg && (ip != 0L || fp != 0)) "-$body" else body
    }
}

/** Belge: tek sayfanın çizimleri, katmanlar ve kâğıt rengi. */
data class ExportDoc(val width: Int, val height: Int, val paperHex: String, val layers: List<Layer>, val strokes: List<Stroke>, val title: String = "")

object VectorKinds {
    /** Vektör olarak yazılabilen fırçalar (düz uçlu kalemler). */
    private val lineBrushes = setOf(
        BrushType.PENCIL, BrushType.INK_PEN, BrushType.FINELINER, BrushType.MARKER, BrushType.HIGHLIGHTER, BrushType.HARD_ROUND,
        BrushType.NOTE_PENCIL, BrushType.NOTE_BALLPOINT, BrushType.NOTE_FOUNTAIN, BrushType.NOTE_GEL, BrushType.NOTE_FINELINER,
        BrushType.NOTE_FELT, BrushType.NOTE_HIGHLIGHTER, BrushType.CALLIGRAPHY,
    )

    fun isLine(s: Stroke): Boolean = s.brushType in lineBrushes && s.params?.grain == null && s.params?.stampAssetId == null && s.points.size >= 1
    fun isVector(s: Stroke): Boolean = when {
        s.brushType == BrushType.SHAPE -> s.shape != null && s.shape.subpaths.none { it.label != null } && s.shape.style.fillGradient == null &&
            s.shape.style.tone == null && s.style2.let { v -> v == null || (v.freeGradient == null && v.fills.isEmpty() && v.strokes.isEmpty() && v.opacityMask == null && v.patternId == null) }
        s.brushType == BrushType.LASSO_FILL -> s.points.size >= 3
        s.brushType == BrushType.TEXT -> s.text != null && s.text.box == null && s.text.path == null && s.points.size >= 4
        else -> isLine(s)
    }

    /** Şeklin alt yolları (nokta listesi, kapalı mı). */
    fun subpaths(s: Stroke): List<Pair<List<StrokePoint>, Boolean>> {
        val spec = s.shape ?: return listOf(s.points to (s.brushType == BrushType.LASSO_FILL))
        val out = ArrayList<Pair<List<StrokePoint>, Boolean>>()
        var start = 0
        for (sub in spec.subpaths) {
            val end = minOf(s.points.size, start + sub.count)
            if (end > start) out += s.points.subList(start, end) to sub.closed
            start = end
        }
        if (out.isEmpty() && s.points.isNotEmpty()) out += s.points to false
        return out
    }
}

object SvgWriter {
    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun color(hex: String): String {
        val h = hex.removePrefix("#")
        return if (h.length == 8) "#" + h.substring(2).uppercase() else "#" + h.uppercase()
    }
    private fun alphaOf(hex: String): Float {
        val h = hex.removePrefix("#")
        return if (h.length == 8) (h.substring(0, 2).toIntOrNull(16) ?: 255) / 255f else 1f
    }

    fun blendCss(b: LayerBlend): String = when (b) {
        LayerBlend.NORMAL -> "normal"; LayerBlend.MULTIPLY -> "multiply"; LayerBlend.SCREEN -> "screen"; LayerBlend.OVERLAY -> "overlay"
        LayerBlend.DARKEN -> "darken"; LayerBlend.LIGHTEN -> "lighten"; LayerBlend.COLOR_DODGE -> "color-dodge"; LayerBlend.COLOR_BURN -> "color-burn"
        LayerBlend.HARD_LIGHT -> "hard-light"; LayerBlend.SOFT_LIGHT -> "soft-light"; LayerBlend.DIFFERENCE -> "difference"
        LayerBlend.EXCLUSION -> "exclusion"; LayerBlend.HUE -> "hue"; LayerBlend.SATURATION -> "saturation"; LayerBlend.COLOR -> "color"
        LayerBlend.LUMINOSITY -> "luminosity"; LayerBlend.ADD -> "plus-lighter"
    }

    private fun pathData(pts: List<StrokePoint>, closed: Boolean): String {
        val sb = StringBuilder()
        pts.forEachIndexed { i, p -> sb.append(if (i == 0) "M" else " L").append(ExportNum.f(p.x)).append(' ').append(ExportNum.f(p.y)) }
        if (pts.size == 1) sb.append(" L").append(ExportNum.f(pts[0].x)).append(' ').append(ExportNum.f(pts[0].y))
        if (closed) sb.append(" Z")
        return sb.toString()
    }

    /**
     * SVG metni. [raster] vektöre çevrilemeyen ardışık çizimleri (doku fırçaları, resimler, diyagram, formül, silgili
     * katmanlar) tuval boyutunda PNG'ye çevirir; null dönerse o çizimler atlanır.
     */
    fun write(doc: ExportDoc, raster: (List<Stroke>) -> ByteArray?): String {
        val w = doc.width; val h = doc.height
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
        sb.append("xmlns:inkscape=\"http://www.inkscape.org/namespaces/inkscape\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">\n")
        if (doc.title.isNotEmpty()) sb.append("<title>").append(esc(doc.title)).append("</title>\n")
        val defs = StringBuilder()
        val body = StringBuilder()
        body.append("<rect id=\"paper\" width=\"$w\" height=\"$h\" fill=\"${color(doc.paperHex)}\"/>\n")
        var defId = 0
        val layers = doc.layers.ifEmpty { listOf(Layer(Layer.BASE, "Katman 1")) }
        val visible = doc.strokes.filter { !it.hidden && it.brushType != BrushType.ERASER || it.brushType == BrushType.ERASER }
        layers.forEachIndexed { li, layer ->
            if (!layer.visible) return@forEachIndexed
            val own = visible.filter { Layers.of(it, layers) == layer.id && !it.hidden }
            if (own.isEmpty()) return@forEachIndexed
            val style = StringBuilder()
            if (layer.blend != LayerBlend.NORMAL) style.append("mix-blend-mode:${blendCss(layer.blend)}")
            body.append("<g id=\"layer${li + 1}\" inkscape:groupmode=\"layer\" inkscape:label=\"${esc(layer.name)}\"")
            if (layer.opacity < 1f) body.append(" opacity=\"${ExportNum.f(layer.opacity)}\"")
            if (style.isNotEmpty()) body.append(" style=\"$style\"")
            body.append(">\n")
            // Silgi varsa katmanın fırça izleri tek resim (silgi yalnız resimde doğru görünür)
            val erased = own.any { it.brushType == BrushType.ERASER }
            val run = ArrayList<Stroke>()
            fun flush() {
                if (run.isEmpty()) return
                raster(run.toList())?.let { png ->
                    body.append("<image x=\"0\" y=\"0\" width=\"$w\" height=\"$h\" xlink:href=\"data:image/png;base64,")
                        .append(java.util.Base64.getEncoder().encodeToString(png)).append("\"/>\n")
                }
                run.clear()
            }
            for (s in own) {
                val vector = VectorKinds.isVector(s) && !(erased && !s.brushType.let { it == BrushType.SHAPE || it == BrushType.TEXT })
                if (!vector) { run += s; continue }
                flush()
                val wrapOpen = StringBuilder()
                var wrapClose = ""
                val clipPts = s.clip?.takeIf { it.size >= 3 } ?: s.mask?.takeIf { it.points.size >= 3 && !it.inverted }?.points
                if (clipPts != null) {
                    defId++
                    defs.append("<clipPath id=\"c$defId\"><path d=\"${pathData(clipPts, true)}\"/></clipPath>\n")
                    wrapOpen.append("<g clip-path=\"url(#c$defId)\">"); wrapClose = "</g>"
                }
                val blend = s.style2?.blend ?: LayerBlend.NORMAL
                if (blend != LayerBlend.NORMAL) { wrapOpen.append("<g style=\"mix-blend-mode:${blendCss(blend)}\">"); wrapClose += "</g>" }
                body.append(wrapOpen)
                element(body, s) { defId++; defId }.let { extraDefs -> defs.append(extraDefs) }
                body.append(wrapClose).append('\n')
            }
            flush()
            body.append("</g>\n")
        }
        if (defs.isNotEmpty()) sb.append("<defs>\n").append(defs).append("</defs>\n")
        sb.append(body).append("</svg>\n")
        return sb.toString()
    }

    /** Tek vektör eleman; dönen: gereken tanımlar (ok uçları). */
    private fun element(out: StringBuilder, s: Stroke, nextId: () -> Int): String {
        val op = s.opacity.coerceIn(0f, 1f)
        when (s.brushType) {
            BrushType.SHAPE -> {
                val st = s.shape!!.style
                val v = s.style2
                val defs = StringBuilder()
                val attrs = StringBuilder()
                val fill = st.fillColorHex
                attrs.append(" fill=\"${if (fill != null) color(fill) else "none"}\"")
                if (fill != null && st.fillOpacity < 1f) attrs.append(" fill-opacity=\"${ExportNum.f(st.fillOpacity)}\"")
                if (st.strokeWidth > 0f) {
                    attrs.append(" stroke=\"${color(st.strokeColorHex)}\" stroke-width=\"${ExportNum.f(st.strokeWidth)}\"")
                    attrs.append(" stroke-linecap=\"${when (v?.cap) { CapStyle.BUTT -> "butt"; CapStyle.SQUARE -> "square"; else -> "round" }}\"")
                    attrs.append(" stroke-linejoin=\"${when (v?.join) { JoinStyle.MITER -> "miter"; JoinStyle.BEVEL -> "bevel"; else -> "round" }}\"")
                    if (v?.join == JoinStyle.MITER) attrs.append(" stroke-miterlimit=\"${ExportNum.f(v.miterLimit)}\"")
                    v?.dash?.takeIf { it.size >= 2 }?.let { d -> attrs.append(" stroke-dasharray=\"${d.joinToString(" ") { ExportNum.f(it) }}\"") }
                    if (v != null && (v.arrowStart != ArrowKind.NONE || v.arrowEnd != ArrowKind.NONE)) {
                        val id = nextId()
                        defs.append("<marker id=\"m$id\" viewBox=\"0 0 10 10\" refX=\"10\" refY=\"5\" markerWidth=\"4\" markerHeight=\"4\" orient=\"auto-start-reverse\">")
                            .append("<path d=\"M0 0 L10 5 L0 10 Z\" fill=\"${color(st.strokeColorHex)}\"/></marker>\n")
                        if (v.arrowStart != ArrowKind.NONE) attrs.append(" marker-start=\"url(#m$id)\"")
                        if (v.arrowEnd != ArrowKind.NONE) attrs.append(" marker-end=\"url(#m$id)\"")
                    }
                } else attrs.append(" stroke=\"none\"")
                if (op < 1f) attrs.append(" opacity=\"${ExportNum.f(op)}\"")
                val d = VectorKinds.subpaths(s).joinToString(" ") { (pts, closed) -> pathData(pts, closed) }
                out.append("<path d=\"$d\"$attrs/>")
                return defs.toString()
            }
            BrushType.LASSO_FILL -> {
                out.append("<path d=\"${pathData(s.points, true)}\" fill=\"${color(s.colorHex)}\"")
                if (op < 1f) out.append(" opacity=\"${ExportNum.f(op)}\"")
                out.append("/>")
            }
            BrushType.TEXT -> {
                val t = s.text!!
                val q = s.points
                val angle = Math.toDegrees(atan2((q[1].y - q[0].y).toDouble(), (q[1].x - q[0].x).toDouble()))
                val size = s.sizePx
                out.append("<text x=\"${ExportNum.f(q[0].x)}\" y=\"${ExportNum.f(q[0].y)}\" font-size=\"${ExportNum.f(size)}\" fill=\"${color(s.colorHex)}\"")
                out.append(" font-family=\"${esc(t.font.family)}\"")
                if (t.bold) out.append(" font-weight=\"bold\"")
                if (t.italic) out.append(" font-style=\"italic\"")
                if (abs(angle) > 0.01) out.append(" transform=\"rotate(${ExportNum.f(angle)} ${ExportNum.f(q[0].x)} ${ExportNum.f(q[0].y)})\"")
                if (op < 1f) out.append(" opacity=\"${ExportNum.f(op)}\"")
                out.append(">")
                t.content.split('\n').forEachIndexed { i, line ->
                    out.append("<tspan x=\"${ExportNum.f(q[0].x)}\" dy=\"${ExportNum.f(if (i == 0) size * 0.9f else size * 1.2f)}\">").append(esc(line)).append("</tspan>")
                }
                out.append("</text>")
            }
            else -> {
                val width = if (s.brushType == BrushType.HIGHLIGHTER || s.brushType == BrushType.NOTE_HIGHLIGHTER) s.sizePx * 1.6f else s.sizePx
                val alpha = op * alphaOf(s.colorHex) * (if (s.brushType == BrushType.HIGHLIGHTER || s.brushType == BrushType.NOTE_HIGHLIGHTER) 0.45f else 1f)
                out.append("<path d=\"${pathData(s.points, false)}\" fill=\"none\" stroke=\"${color(s.colorHex)}\" stroke-width=\"${ExportNum.f(width)}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"")
                if (alpha < 1f) out.append(" stroke-opacity=\"${ExportNum.f(alpha)}\"")
                out.append("/>")
            }
        }
        return ""
    }
}

/**
 * ASCII DXF (R12 / AC1009): katman tablosu, çizgiler ve şekiller POLYLINE (kapalıysa bayrak 1), yazılar TEXT. Birim mm
 * (96 dpi: 1 px = 0.2646 mm), Y ekseni ters. Resimler ve boya izleri yazılmaz ([skipped] sayısı döner).
 */
object DxfWriter {
    const val MM_PER_PX = 25.4 / 96.0

    data class Result(val text: String, val skipped: Int)

    private fun aci(hex: String): Int {
        val h = hex.removePrefix("#").let { if (it.length == 8) it.substring(2) else it }
        val v = h.toIntOrNull(16) ?: return 7
        val r = (v shr 16) and 0xFF; val g = (v shr 8) and 0xFF; val b = v and 0xFF
        val palette = listOf(1 to intArrayOf(255, 0, 0), 2 to intArrayOf(255, 255, 0), 3 to intArrayOf(0, 255, 0), 4 to intArrayOf(0, 255, 255),
            5 to intArrayOf(0, 0, 255), 6 to intArrayOf(255, 0, 255), 7 to intArrayOf(0, 0, 0), 8 to intArrayOf(128, 128, 128), 9 to intArrayOf(192, 192, 192))
        return palette.minBy { (_, c) -> (c[0] - r) * (c[0] - r) + (c[1] - g) * (c[1] - g) + (c[2] - b) * (c[2] - b) }.first
    }

    fun layerName(name: String, index: Int): String {
        val ascii = name.map { ch ->
            when (ch) { 'ç' -> 'c'; 'Ç' -> 'C'; 'ğ' -> 'g'; 'Ğ' -> 'G'; 'ı' -> 'i'; 'İ' -> 'I'; 'ö' -> 'o'; 'Ö' -> 'O'; 'ş' -> 's'; 'Ş' -> 'S'; 'ü' -> 'u'; 'Ü' -> 'U'; else -> ch }
        }.joinToString("").map { if (it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_') it else '_' }.joinToString("").uppercase()
        return ascii.ifBlank { "LAYER" } + "_" + (index + 1)
    }

    fun write(doc: ExportDoc): Result {
        val k = MM_PER_PX
        val hmm = doc.height * k
        fun x(v: Float) = ExportNum.f(v * k)
        fun y(v: Float) = ExportNum.f(hmm - v * k)
        val sb = StringBuilder()
        fun g(code: Int, value: String) { sb.append(code).append('\n').append(value).append('\n') }
        val layers = doc.layers.ifEmpty { listOf(Layer(Layer.BASE, "Katman 1")) }
        val names = layers.mapIndexed { i, l -> l.id to layerName(l.name, i) }.toMap()
        g(0, "SECTION"); g(2, "HEADER")
        g(9, "\$ACADVER"); g(1, "AC1009")
        g(9, "\$INSUNITS"); g(70, "4")
        g(9, "\$EXTMIN"); g(10, "0"); g(20, "0")
        g(9, "\$EXTMAX"); g(10, x(doc.width.toFloat())); g(20, ExportNum.f(hmm))
        g(0, "ENDSEC")
        g(0, "SECTION"); g(2, "TABLES")
        g(0, "TABLE"); g(2, "LAYER"); g(70, "${layers.size}")
        layers.forEach { l ->
            val first = doc.strokes.firstOrNull { Layers.of(it, layers) == l.id }
            g(0, "LAYER"); g(2, names.getValue(l.id)); g(70, "0"); g(62, "${if (l.visible) aci(first?.colorHex ?: "#000000") else -aci(first?.colorHex ?: "#000000")}"); g(6, "CONTINUOUS")
        }
        g(0, "ENDTAB"); g(0, "ENDSEC")
        g(0, "SECTION"); g(2, "ENTITIES")
        var skipped = 0
        for (s in doc.strokes) {
            if (s.hidden || s.brushType == BrushType.ERASER) continue
            val layer = names[Layers.of(s, layers)] ?: "0"
            if (!VectorKinds.isVector(s)) { skipped++; continue }
            val color = "${aci(if (s.brushType == BrushType.SHAPE) s.shape!!.style.strokeColorHex else s.colorHex)}"
            if (s.brushType == BrushType.TEXT) {
                val q = s.points
                val angle = Math.toDegrees(atan2(-(q[1].y - q[0].y).toDouble(), (q[1].x - q[0].x).toDouble()))
                s.text!!.content.split('\n').forEachIndexed { i, line ->
                    val dy = s.sizePx * (0.9f + 1.2f * i)
                    g(0, "TEXT"); g(8, layer); g(62, color)
                    g(10, x(q[0].x)); g(20, y(q[0].y + dy)); g(30, "0")
                    g(40, ExportNum.f(s.sizePx * k * 0.75)); g(1, line)
                    if (abs(angle) > 0.01) g(50, ExportNum.f(angle))
                }
                continue
            }
            for ((pts, closed) in VectorKinds.subpaths(s)) {
                if (pts.size < 2) continue
                g(0, "POLYLINE"); g(8, layer); g(62, color); g(66, "1"); g(10, "0"); g(20, "0"); g(30, "0"); g(70, if (closed) "1" else "0")
                pts.forEach { p -> g(0, "VERTEX"); g(8, layer); g(10, x(p.x)); g(20, y(p.y)); g(30, "0") }
                g(0, "SEQEND"); g(8, layer)
            }
        }
        g(0, "ENDSEC"); g(0, "EOF")
        return Result(sb.toString(), skipped)
    }
}

/**
 * Katmanlı PSD (sürüm 1, RGB, 8 bit, RLE): her katman tuval boyunda ARGB piksel; ad (ASCII + `luni` Unicode),
 * görünürlük, opaklık, karışım anahtarı, alfa kilidi, alttakine kırpma. Birleşik görüntü en sonda.
 */
object PsdWriter {
    data class PsdLayer(
        val name: String, val pixels: IntArray, val visible: Boolean = true, val opacity: Float = 1f,
        val blend: LayerBlend = LayerBlend.NORMAL, val clipping: Boolean = false, val alphaLock: Boolean = false,
    )

    fun blendKey(b: LayerBlend): String = when (b) {
        LayerBlend.NORMAL -> "norm"; LayerBlend.MULTIPLY -> "mul "; LayerBlend.SCREEN -> "scrn"; LayerBlend.OVERLAY -> "over"
        LayerBlend.DARKEN -> "dark"; LayerBlend.LIGHTEN -> "lite"; LayerBlend.COLOR_DODGE -> "div "; LayerBlend.COLOR_BURN -> "idiv"
        LayerBlend.HARD_LIGHT -> "hLit"; LayerBlend.SOFT_LIGHT -> "sLit"; LayerBlend.DIFFERENCE -> "diff"; LayerBlend.EXCLUSION -> "smud"
        LayerBlend.HUE -> "hue "; LayerBlend.SATURATION -> "sat "; LayerBlend.COLOR -> "colr"; LayerBlend.LUMINOSITY -> "lum "
        LayerBlend.ADD -> "lddg"
    }

    private class Out {
        val bytes = java.io.ByteArrayOutputStream()
        fun u8(v: Int) = bytes.write(v and 0xFF)
        fun u16(v: Int) { u8(v shr 8); u8(v) }
        fun u32(v: Int) { u16(v ushr 16); u16(v) }
        fun ascii(s: String) = s.forEach { u8(it.code) }
        fun raw(b: ByteArray) = bytes.write(b)
        val size: Int get() = bytes.size()
    }

    /** PackBits satırı. */
    fun packBits(row: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        val n = row.size
        while (i < n) {
            var run = 1
            while (i + run < n && run < 128 && row[i + run] == row[i]) run++
            if (run >= 2) { out.write(257 - run); out.write(row[i].toInt()); i += run; continue }
            var lit = 1
            while (i + lit < n && lit < 128 && !(i + lit + 1 < n && row[i + lit] == row[i + lit + 1])) lit++
            out.write(lit - 1); out.write(row, i, lit); i += lit
        }
        return out.toByteArray()
    }

    /** Kanal verisi (RLE): satır uzunlukları + satırlar. */
    private fun channelRle(px: IntArray, w: Int, h: Int, shift: Int): Pair<ByteArray, ByteArray> {
        val counts = java.io.ByteArrayOutputStream()
        val data = java.io.ByteArrayOutputStream()
        val row = ByteArray(w)
        for (yy in 0 until h) {
            for (xx in 0 until w) row[xx] = (px[yy * w + xx] ushr shift).toByte()
            val packed = packBits(row)
            counts.write(packed.size shr 8); counts.write(packed.size)
            data.write(packed)
        }
        return counts.toByteArray() to data.toByteArray()
    }

    private fun asciiName(s: String) = s.map { c -> if (c.code in 32..126) c else '_' }.joinToString("").take(255)

    fun write(width: Int, height: Int, layers: List<PsdLayer>, composite: IntArray): ByteArray {
        val o = Out()
        o.ascii("8BPS"); o.u16(1); repeat(6) { o.u8(0) }; o.u16(3); o.u32(height); o.u32(width); o.u16(8); o.u16(3)
        o.u32(0) // renk modu verisi
        o.u32(0) // resim kaynakları
        // Katman bilgisi
        val info = Out()
        info.u16(layers.size)
        val channelData = Out()
        layers.forEach { l ->
            info.u32(0); info.u32(0); info.u32(height); info.u32(width)
            info.u16(4)
            val chans = listOf(-1 to 24, 0 to 16, 1 to 8, 2 to 0).map { (id, shift) -> id to channelRle(l.pixels, width, height, shift) }
            chans.forEach { (id, c) -> info.u16(id and 0xFFFF); info.u32(2 + c.first.size + c.second.size) }
            info.ascii("8BIM"); info.ascii(blendKey(l.blend))
            info.u8((l.opacity.coerceIn(0f, 1f) * 255f + 0.5f).toInt())
            info.u8(if (l.clipping) 1 else 0)
            info.u8((if (l.alphaLock) 1 else 0) or (if (!l.visible) 2 else 0))
            info.u8(0)
            val extra = Out()
            extra.u32(0); extra.u32(0)
            val name = asciiName(l.name)
            val nameLen = 1 + name.length
            extra.u8(name.length); extra.ascii(name); repeat((4 - nameLen % 4) % 4) { extra.u8(0) }
            // Unicode ad
            val uni = Out()
            uni.u32(l.name.length); l.name.forEach { uni.u16(it.code) }
            if (uni.size % 2 != 0) uni.u8(0)
            extra.ascii("8BIM"); extra.ascii("luni"); extra.u32(uni.size); extra.raw(uni.bytes.toByteArray())
            info.u32(extra.size); info.raw(extra.bytes.toByteArray())
            chans.forEach { (_, c) -> channelData.u16(1); channelData.raw(c.first); channelData.raw(c.second) }
        }
        info.raw(channelData.bytes.toByteArray())
        if (info.size % 2 != 0) info.u8(0)
        o.u32(4 + info.size + 4)
        o.u32(info.size); o.raw(info.bytes.toByteArray())
        o.u32(0) // genel maske
        // Birleşik görüntü (RGB, RLE)
        o.u16(1)
        val merged = listOf(16, 8, 0).map { channelRle(composite, width, height, it) }
        merged.forEach { o.raw(it.first) }
        merged.forEach { o.raw(it.second) }
        return o.bytes.toByteArray()
    }

    /** Testler ve doğrulama için başlık ve katman özeti (ad, karışım anahtarı, opaklık, bayraklar). */
    data class Info(val width: Int, val height: Int, val channels: Int, val layers: List<Triple<String, String, Int>>, val flags: List<Int>, val clipping: List<Int>)

    fun readInfo(b: ByteArray): Info {
        var p = 0
        fun u8() = b[p++].toInt() and 0xFF
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16() shl 16) or u16()
        fun ascii(n: Int) = String(CharArray(n) { u8().toChar() })
        require(ascii(4) == "8BPS")
        u16(); p += 6
        val ch = u16(); val h = u32(); val w = u32(); u16(); u16()
        fun skip() { val n = u32(); p += n }
        skip(); skip()
        u32(); u32()
        val count = u16().toShort().toInt().let { abs(it) }
        val layers = ArrayList<Triple<String, String, Int>>()
        val flags = ArrayList<Int>(); val clip = ArrayList<Int>()
        repeat(count) {
            p += 16
            val nc = u16(); p += nc * 6
            ascii(4); val key = ascii(4); val op = u8(); clip += u8(); flags += u8(); u8()
            val extraLen = u32(); val end = p + extraLen
            skip(); skip()
            val nl = u8(); val name = ascii(nl)
            p = end
            layers += Triple(name, key, op)
        }
        return Info(w, h, ch, layers, flags, clip)
    }
}

/** sqrt yardımcı (iOS ile aynı kullanım). */
internal fun hyp(x: Float, y: Float) = sqrt(x * x + y * y)

/** v56 aşama 9 Kaydet penceresindeki yeni biçimler. */
enum class DocFormat(val extension: String, val mime: String) {
    SVG("svg", "image/svg+xml"), PSD("psd", "image/vnd.adobe.photoshop"), DXF("dxf", "application/dxf");
}
