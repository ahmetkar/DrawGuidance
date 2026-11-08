package com.karapp.drawguidance.content.paper

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Desenin kullanıldığı yer: not tuvali, resim tuvali ya da yazı kutusu (not defteri parçası). */
enum class PaperUsage { NOTE, ART, BOX }

/**
 * Desen çizim komutu. [type]: [LINE] (x1, y1, x2, y2), [RECT] (x, y, w, h), [CIRCLE] (cx, cy, r), [POLY] (x, y…).
 * [slot] renk yuvası: 0 desen rengi, 1 açık tonu, 2 koyu tonu, 3 kağıt (arka plan) rengi, 4 vurgu (kırmızı kenar
 * çizgisi gibi). [fill] dolu mu, [width] çizgi kalınlığı, [dash] kesik çizgi adımı (0: düz), [alpha] örtücülük.
 * Sayfa komutlarında [centered] ise koordinatlar sayfanın ortasına oturan kare birimdedir (kısa kenar = 1), değilse
 * sayfanın oranıdır (0..1).
 */
class PatOp(
    val type: Int,
    val pts: FloatArray,
    val width: Float = 1.5f,
    val slot: Int = 0,
    val alpha: Float = 1f,
    val fill: Boolean = false,
    val dash: Float = 0f,
    val closed: Boolean = false,
    val centered: Boolean = false,
) {
    companion object {
        const val LINE = 0
        const val RECT = 1
        const val CIRCLE = 2
        const val POLY = 3

        const val SLOT_MAIN = 0
        const val SLOT_LIGHT = 1
        const val SLOT_DARK = 2
        const val SLOT_PAPER = 3
        const val SLOT_ACCENT = 4
    }
}

/**
 * v23 arka plan deseni: döşenen karo ([tileW]×[tileH] tuval birimi, ölçek 1'de) ve sayfaya bir kez çizilen ögeler
 * ([page]: kenar çizgisi, Cornell bölmeleri…). Karolar sayfanın [inset] (sol, üst, sağ, alt; oran) payı içinde döşenir.
 * [color] önerilen desen rengi, [accent] vurgu rengi (kenar çizgisi), [tint] varsa sayfaya sürülen renk (sarı hukuk
 * kağıdı gibi; [tintAlpha] örtücülükle).
 */
class PaperPattern(
    val id: String,
    val name: String,
    val usage: PaperUsage,
    val tileW: Float,
    val tileH: Float,
    val tile: List<PatOp>,
    val page: List<PatOp> = emptyList(),
    val inset: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
    val color: Int = 0xFF7C89A6.toInt(),
    val accent: Int = 0xFFE57373.toInt(),
    val tint: Int = 0,
    val tintAlpha: Float = 0f,
) {
    val hasTile: Boolean get() = tile.isNotEmpty() && tileW > 0f && tileH > 0f
}

/** Karo ve sayfa komutlarının kısa yazımı. */
class PatternBuilder {
    val ops = ArrayList<PatOp>()

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 1.5f, slot: Int = 0, alpha: Float = 1f, dash: Float = 0f, centered: Boolean = false) {
        ops += PatOp(PatOp.LINE, floatArrayOf(x1, y1, x2, y2), w, slot, alpha, dash = dash, centered = centered)
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, fill: Boolean = true, width: Float = 1.5f, slot: Int = 0, alpha: Float = 1f, dash: Float = 0f) {
        ops += PatOp(PatOp.RECT, floatArrayOf(x, y, w, h), width, slot, alpha, fill, dash)
    }

    fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = true, width: Float = 1.5f, slot: Int = 0, alpha: Float = 1f, centered: Boolean = false, dash: Float = 0f) {
        ops += PatOp(PatOp.CIRCLE, floatArrayOf(cx, cy, r), width, slot, alpha, fill, dash, centered = centered)
    }

    fun poly(vararg xy: Float, fill: Boolean = true, closed: Boolean = true, width: Float = 1.5f, slot: Int = 0, alpha: Float = 1f, dash: Float = 0f) {
        ops += PatOp(PatOp.POLY, xy, width, slot, alpha, fill, dash, closed = closed || fill)
    }

    fun polyline(pts: List<Pair<Float, Float>>, fill: Boolean = false, closed: Boolean = false, width: Float = 1.5f, slot: Int = 0, alpha: Float = 1f) {
        val a = FloatArray(pts.size * 2)
        pts.forEachIndexed { i, p -> a[2 * i] = p.first; a[2 * i + 1] = p.second }
        ops += PatOp(PatOp.POLY, a, width, slot, alpha, fill, 0f, closed = closed || fill)
    }

    /** Merkezi ([cx], [cy]), yarıçapı [r] olan yay (derece; 0 sağ, 90 aşağı), [n] parçalı. */
    fun arc(cx: Float, cy: Float, r: Float, from: Float, to: Float, w: Float = 1.5f, slot: Int = 0, alpha: Float = 1f, fill: Boolean = false, n: Int = 24) {
        val pts = (0..n).map { k ->
            val a = Math.toRadians((from + (to - from) * k / n).toDouble())
            (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat()
        }
        polyline(pts, fill = fill, closed = fill, width = w, slot = slot, alpha = alpha)
    }

    /** [points] uçlu yıldız. */
    fun star(cx: Float, cy: Float, r: Float, points: Int = 5, inner: Float = 0.45f, slot: Int = 0, alpha: Float = 1f, rot: Float = -90f) {
        val n = points * 2
        val pts = (0 until n).map { k ->
            val rr = if (k % 2 == 0) r else r * inner
            val a = Math.toRadians((rot + 360.0 * k / n))
            (cx + rr * cos(a)).toFloat() to (cy + rr * sin(a)).toFloat()
        }
        polyline(pts, fill = true, closed = true, slot = slot, alpha = alpha)
    }

    /** Kalp: merkez ([cx], [cy]), genişlik [s]. */
    fun heart(cx: Float, cy: Float, s: Float, slot: Int = 0, alpha: Float = 1f) {
        val pts = (0 until 40).map { k ->
            val t = 2 * PI * k / 40
            val x = 16 * sin(t).let { it * it * it }
            val y = -(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t))
            (cx + (x / 34.0 * s).toFloat()) to (cy + (y / 34.0 * s).toFloat())
        }
        polyline(pts, fill = true, closed = true, slot = slot, alpha = alpha)
    }
}

/** Desenlerin sıkıştırılmış ikili biçimi (veritabanında saklanır). */
object PaperCodec {
    private const val Q = 16f

    fun encode(p: PaperPattern): ByteArray {
        val w = W()
        w.str(p.id); w.str(p.name); w.b(p.usage.ordinal)
        w.q(p.tileW); w.q(p.tileH)
        p.inset.forEach { w.q(it * 1000f) }
        w.i(p.color); w.i(p.accent); w.i(p.tint); w.q(p.tintAlpha * 1000f)
        ops(w, p.tile); ops(w, p.page)
        val body = w.bytes()
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            d.setInput(body); d.finish()
            val out = ByteArrayOutputStream(body.size)
            out.write(byteArrayOf('D'.code.toByte(), 'P'.code.toByte(), 1))
            val buf = ByteArray(4096)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            return out.toByteArray()
        } finally { d.end() }
    }

    fun decode(data: ByteArray): PaperPattern {
        require(data.size > 3 && data[0] == 'D'.code.toByte() && data[1] == 'P'.code.toByte()) { "desen verisi değil" }
        val inf = Inflater(true)
        val body = try {
            inf.setInput(data, 3, data.size - 3)
            val out = ByteArrayOutputStream(data.size * 4)
            val buf = ByteArray(4096)
            while (!inf.finished()) { val n = inf.inflate(buf); if (n == 0 && inf.needsInput()) break; out.write(buf, 0, n) }
            out.toByteArray()
        } finally { inf.end() }
        val r = R(body)
        val id = r.str(); val name = r.str(); val usage = PaperUsage.entries[r.b()]
        val tw = r.q(); val th = r.q()
        val inset = FloatArray(4) { r.q() / 1000f }
        val color = r.i(); val accent = r.i(); val tint = r.i(); val tintAlpha = r.q() / 1000f
        val tile = ops(r); val page = ops(r)
        return PaperPattern(id, name, usage, tw, th, tile, page, inset, color, accent, tint, tintAlpha)
    }

    private fun ops(w: W, list: List<PatOp>) {
        w.u(list.size)
        list.forEach { o ->
            w.b(o.type or (if (o.fill) 8 else 0) or (if (o.closed) 16 else 0) or (if (o.centered) 32 else 0))
            w.b(o.slot); w.q(o.width); w.q(o.alpha * 1000f); w.q(o.dash)
            w.u(o.pts.size)
            // Sayfa komutları oran (0..1) olduğu için daha ince adımla
            val scale = if (o.centered || o.pts.all { it in -2f..2f }) 10000f else Q
            w.b(if (scale == Q) 0 else 1)
            o.pts.forEach { w.z((it * scale).roundToInt()) }
        }
    }

    private fun ops(r: R): List<PatOp> = List(r.u()) {
        val head = r.b()
        val slot = r.b(); val width = r.q(); val alpha = r.q() / 1000f; val dash = r.q()
        val n = r.u()
        val scale = if (r.b() == 0) Q else 10000f
        val pts = FloatArray(n) { r.z() / scale }
        PatOp(head and 7, pts, width, slot, alpha, head and 8 != 0, dash, head and 16 != 0, head and 32 != 0)
    }

    private class W {
        private val o = ByteArrayOutputStream()
        fun b(v: Int) = o.write(v and 0xFF)
        fun i(v: Int) { b(v shr 24); b(v shr 16); b(v shr 8); b(v) }
        fun u(value: Int) { var v = value.toLong() and 0xFFFFFFFFL; while (v and 0x7FL.inv() != 0L) { b(((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }; b(v.toInt()) }
        fun z(v: Int) = u((v shl 1) xor (v shr 31))
        /** 1/16 adımla. */
        fun q(v: Float) = z((v * Q).roundToInt())
        fun str(s: String) { val bytes = s.toByteArray(Charsets.UTF_8); u(bytes.size); o.write(bytes) }
        fun bytes(): ByteArray = o.toByteArray()
    }

    private class R(private val d: ByteArray) {
        private var p = 0
        fun b(): Int = d[p++].toInt() and 0xFF
        fun i(): Int = (b() shl 24) or (b() shl 16) or (b() shl 8) or b()
        fun u(): Int { var s = 0; var r = 0L; while (true) { val x = b(); r = r or ((x and 0x7F).toLong() shl s); if (x and 0x80 == 0) return r.toInt(); s += 7 } }
        fun z(): Int { val v = u(); return (v ushr 1) xor -(v and 1) }
        fun q(): Float = z() / Q
        fun str(): String { val n = u(); val s = String(d, p, n, Charsets.UTF_8); p += n; return s }
    }
}
