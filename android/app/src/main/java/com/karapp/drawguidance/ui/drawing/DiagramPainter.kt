package com.karapp.drawguidance.ui.drawing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposePathEffect
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.karapp.drawguidance.data.diagram.DiagramRouting
import com.karapp.drawguidance.data.diagram.RoutePoint
import com.karapp.drawguidance.data.model.DiagramBox
import com.karapp.drawguidance.data.model.DiagramEdge
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramNode
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.ui.common.hexToColor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * v28 diyagram çizimi (diagram-design editoryal tarzı): kağıt zemin, ince mürekkep çizgiler, rolüne göre ayrışan
 * kutular (odak = tek vurgu rengi, depo = yumuşak dolgu, dış = kesik kenar, göbek = koyu dolgu…), yuvarlatılmış
 * dirsekli oklar ve dolu ok uçları, kağıtla maskelenmiş eşaralıklı büyük harfli ok yazıları, başlık / üst etiket
 * ("[ETİKET]") / alt satır (eşaralıklı) yazı düzeni. Eski renk temaları aynı çizgi diliyle kendi renklerinde çizilir.
 *
 * Diyagram tasarım alanı 0..[U]; [Ctx.k] yatay sıkıştırma katsayısıdır: diyagramın en-boy oranı ne olursa olsun yazı,
 * nokta ve ok uçları bozulmadan (yuvarlak, orantılı) görünür.
 */
internal object DiagramPainter {

    const val U = 1000f
    private val TR = Locale("tr")

    private val paints = object : ThreadLocal<Paint>() { override fun initialValue() = Paint(Paint.ANTI_ALIAS_FLAG) }
    private val textPaints = object : ThreadLocal<TextPaint>() { override fun initialValue() = TextPaint(Paint.ANTI_ALIAS_FLAG) }
    val p: Paint get() = paints.get()!!
    val tp: TextPaint get() = textPaints.get()!!

    val sans: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) ?: Typeface.DEFAULT }
    val medium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) ?: Typeface.DEFAULT_BOLD }
    val mono: Typeface by lazy { Typeface.MONOSPACE }

    // ------------------------------------------------------------------------------------------ renkler

    /** Çizimin renkleri. Editoryalde kağıt/mürekkep/vurgu; eski temalarda temanın ve kalemin renkleri. */
    class Pal(
        val editorial: Boolean,
        val coverage: Boolean,
        val paper: Int,
        val ink: Int,
        val muted: Int,
        val soft: Int,
        val rule: Int,
        val accent: Int,
        val link: Int,
        val edge: Int,
        val theme: DiagramThemes.Theme?,
        val styleInk: Int,
        val styleFill: Int?,
        val fillOpacity: Float,
        val line: Float,
        /** Koyu dolgunun üstündeki yazının rengi (kağıt; v29 saydam temada da opak). */
        val onInk: Int = paper,
    ) {
        /** v29 saydam zemin: kağıt kartı ve maskeler yok. */
        val clear: Boolean get() = editorial && Color.alpha(paper) == 0
    }

    fun hex(s: String): Int = hexToColor(s).toArgb()

    fun alpha(c: Int, a: Float): Int = Color.argb((Color.alpha(c) * a.coerceIn(0f, 1f)).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    fun blend(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        // v29 saydam kağıt (renkli ama görünmez): karışım [b] renginin saydamlaştırılmışı olur (tuvalin rengine göre ton)
        if (Color.alpha(a) == 0 && (a and 0xFFFFFF) != 0) return alpha(b, u)
        fun ch(x: Int, y: Int) = (x + (y - x) * u).toInt().coerceIn(0, 255)
        return Color.argb(ch(Color.alpha(a), Color.alpha(b)), ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    fun darker(color: Int): Int =
        Color.argb(255, (Color.red(color) * 0.62f).toInt(), (Color.green(color) * 0.62f).toInt(), (Color.blue(color) * 0.62f).toInt())

    fun palette(spec: DiagramSpec, coverage: Boolean): Pal {
        val theme = DiagramThemes.find(spec.theme)
        val style = spec.style
        // v40 zeminsiz diyagram: kalemin dolgusu da çizilmez; renkli temanın çizgisi temanın kenar rengidir
        val background = DiagramThemes.hasBackground(spec.theme)
        val styleInk = theme?.takeIf { !background && it.editorial == null }?.stroke?.let { hex(it) } ?: hex(style.strokeColorHex)
        val styleFill = style.fillColorHex?.let { hex(it) }?.takeIf { background }
        val el = DiagramThemes.editorialLight
        if (coverage) {
            val b = Color.BLACK
            return Pal(false, true, Color.TRANSPARENT, b, b, b, b, b, b, b, null, b, b, 1f, max(1f, style.strokeWidth * 2.2f))
        }
        val e = theme?.editorial
        if (e != null) {
            val ink = hex(e.ink)
            val paper = hex(e.paper)
            return Pal(
                true, false, if (e.clear) paper and 0x00FFFFFF else paper, ink, hex(e.muted),
                // Saydam temada açık ton: mürekkebin hafif saydamı (her zeminde seçilir)
                if (e.clear) alpha(ink, 0.07f) else hex(e.soft),
                hex(e.rule), hex(e.accent), hex(e.link), ink,
                theme, styleInk, styleFill, style.fillOpacity, (style.strokeWidth * 0.6f).coerceIn(1.4f, 8f), onInk = paper,
            )
        }
        val ink = theme?.edge?.let { hex(it) } ?: styleInk
        return Pal(
            false, false, Color.TRANSPARENT, ink, alpha(ink, 0.62f), theme?.fill(0)?.let { hex(it) } ?: alpha(ink, 0.08f),
            alpha(ink, 0.3f), hex(el.accent), hex(el.link), ink, theme, styleInk, styleFill, style.fillOpacity,
            max(1f, style.strokeWidth * 2.2f),
        )
    }

    /** Bir çizimin bağlamı. [k]: yatay tasarım birimi / dikey (yazı ve noktalar yatayda bu kadar sıkıştırılır). */
    class Ctx(val c: Canvas, val spec: DiagramSpec, val pal: Pal, val k: Float, val labels: Boolean) {
        val kind: DiagramKind = spec.kind
        val nodes: List<DiagramNode> = spec.nodes
        val byId: Map<String, DiagramNode> = spec.nodes.associateBy { it.id }
        val base: Float = spec.fontSize * U
        val line: Float = pal.line
        /** Eşaralıklı küçük etiketlerin (bölge, kulvar, ok yazısı, eksen) boyutu. */
        val label: Float get() = (base * 0.54f).coerceIn(10f, 28f)
        val ribbons: Map<Int, DiagramCatalog.Ribbon> by lazy {
            if (kind == DiagramKind.SANKEY) DiagramCatalog.ribbons(spec.nodes, spec.edges) else emptyMap()
        }
    }

    // ------------------------------------------------------------------------------------------ giriş

    fun paint(canvas: Canvas, spec: DiagramSpec, coverage: Boolean, k: Float, labels: Boolean) {
        val pal = palette(spec, coverage)
        val x = Ctx(canvas, spec, pal, k.coerceIn(0.2f, 5f), labels)
        if (pal.editorial && !pal.clear) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = pal.paper
            val r = 14f
            canvas.drawRoundRect(paperRect(spec), r * x.k, r, p)
        }
        DiagramDecor.before(x)
        // Bölgeler, kulvarlar ve sütunlar okların altında; diğer kutular üstünde
        drawContainers(x)
        drawEdges(x)
        drawNodes(x)
        DiagramDecor.after(x)
    }

    /**
     * Kağıt zemin: v28 türlerinde diyagramın tamamı (çerçeve büyüdüyse onunla); eski türlerde adımları saran kart
     * (eski yerleşimler alanın tamamını doldurmaz).
     */
    fun paperRect(spec: DiagramSpec): RectF {
        val f = spec.frame
        val full = RectF(f.left * U, f.top * U, f.right * U, f.bottom * U)
        if (DiagramCatalog.isNew(spec.kind) || spec.nodes.isEmpty()) return full
        val pad = 0.035f * U
        var l = spec.nodes.minOf { it.x } * U - pad; var t = spec.nodes.minOf { it.y } * U - pad
        var r = spec.nodes.maxOf { it.x + it.w } * U + pad; var b = spec.nodes.maxOf { it.y + it.h } * U + pad
        // Dolaşan oklar (durum diyagramının geri oku) da kartın içinde kalsın
        val byId = spec.nodes.associateBy { it.id }
        spec.edges.forEachIndexed { i, e ->
            val from = byId[e.from] ?: return@forEachIndexed
            val to = byId[e.to] ?: return@forEachIndexed
            DiagramRenderer.routeOf(spec, i, e, from, to).forEach { pt ->
                l = min(l, pt.x - pad * 0.6f); r = max(r, pt.x + pad * 0.6f); t = min(t, pt.y - pad * 0.6f); b = max(b, pt.y + pad * 0.6f)
            }
        }
        when (spec.kind) {
            DiagramKind.FISHBONE -> l = min(l, -0.01f * U)
            DiagramKind.TIMELINE -> { l = min(l, 0f); r = max(r, U) }
            else -> Unit
        }
        l = max(l, full.left); t = max(t, full.top); r = min(r, full.right); b = min(b, full.bottom)
        return if (r - l < 20f || b - t < 20f) full else RectF(l, t, r, b)
    }

    // ------------------------------------------------------------------------------------------ düğümler

    /** Düğümün görünüşü (dolgu, kenar, yazı renkleri; kesik kenar, sol çubuk…). */
    class Look(
        var fill: Int?,
        var stroke: Int?,
        var width: Float,
        var text: Int,
        var sub: Int,
        var eyebrow: Int,
        var dashed: Boolean = false,
        var bold: Boolean = false,
        var bar: Int? = null,
        var strike: Boolean = false,
        var fillAlpha: Float = 1f,
        var shadow: Boolean = false,
    )

    private val containers = setOf(DiagramBox.GROUP, DiagramBox.LANE, DiagramBox.COLUMN)

    /** v40 yazısı dolgunun yanında duran kutular (karşıtlık düzeltmesi uygulanmaz). */
    private val besideFill = setOf(DiagramBox.DOT, DiagramBox.BAR, DiagramBox.LABEL, DiagramBox.CELL)

    private fun drawContainers(x: Ctx) {
        var lane = 0
        x.nodes.forEachIndexed { i, n ->
            if (n.box !in containers) return@forEachIndexed
            val look = lookOf(x, i, n)
            when (n.box) {
                DiagramBox.GROUP -> drawGroup(x, n, look)
                DiagramBox.LANE -> drawLane(x, n, look, lane++)
                else -> drawColumn(x, n, look)
            }
        }
    }

    private fun drawNodes(x: Ctx) {
        x.nodes.forEachIndexed { i, n ->
            if (n.box in containers) return@forEachIndexed
            val look = lookOf(x, i, n)
            when (n.box) {
                DiagramBox.TABLE -> drawTable(x, n, look)
                DiagramBox.CARD -> drawCard(x, n, look)
                DiagramBox.DOT -> drawDot(x, n, look)
                DiagramBox.LABEL -> drawLabel(x, n, look)
                DiagramBox.BAR -> drawBar(x, n, look)
                DiagramBox.CELL -> drawCell(x, n, look)
                DiagramBox.SLAB -> DiagramDecor.drawSlab(x, n, look, i)
                else -> drawShape(x, i, n, look)
            }
        }
    }

    fun lookOf(x: Ctx, index: Int, n: DiagramNode): Look {
        val pal = x.pal
        val look = if (pal.editorial) editorialLook(x, index, n) else themedLook(x, index, n)
        n.fillColorHex?.let { look.fill = hex(it); look.fillAlpha = 1f }
        n.strokeColorHex?.let { look.stroke = hex(it) }
        n.textColorHex?.let { look.text = hex(it); look.sub = alpha(look.text, 0.75f) }
        // v40 koyu dolguda yazı okunur kalsın (WCAG karşıtlığı); elle seçilen yazı rengine dokunulmaz. Nokta, çubuk ve
        // etiketin yazısı dolgunun üstünde değil, yanında durur.
        if (!pal.coverage && n.textColorHex == null && n.box !in besideFill) {
            val fixed = DiagramContrast.readable(look.text, look.fill, look.fillAlpha)
            if (fixed != look.text) {
                look.text = fixed; look.sub = alpha(fixed, 0.75f); look.eyebrow = alpha(fixed, 0.7f)
            }
        }
        if (pal.coverage) {
            val b = Color.BLACK
            look.fill = b; look.stroke = b; look.text = b; look.sub = b; look.eyebrow = b; look.bar = null; look.fillAlpha = 1f; look.shadow = false
        }
        return look
    }

    private fun editorialLook(x: Ctx, index: Int, n: DiagramNode): Look {
        val P = x.pal
        val l = Look(P.paper, P.ink, x.line, P.ink, P.muted, P.muted)
        when (n.box) {
            DiagramBox.GROUP -> { l.fill = null; l.stroke = P.rule; l.text = P.muted }
            DiagramBox.COLUMN -> { l.fill = P.soft; l.stroke = null; l.text = P.muted }
            DiagramBox.CARD -> l.stroke = P.rule
            DiagramBox.LABEL -> { l.fill = null; l.stroke = null }
            DiagramBox.DOT -> { l.fill = P.ink; l.stroke = null }
            DiagramBox.BAR -> { l.fill = blend(P.paper, P.ink, 0.62f); l.stroke = null }
            else -> Unit
        }
        // Eski türlerde katmanlı biçimler (piramit, huni) kağıttan mürekkebe hafif tonlanır; Venn yarı saydam
        when (x.kind) {
            DiagramKind.PYRAMID, DiagramKind.FUNNEL -> l.fill = blend(P.paper, P.ink, 0.03f + 0.045f * min(index, 6))
            DiagramKind.VENN -> {
                val s = listOf(P.accent, P.link, P.ink)[index % 3]
                l.fill = s; l.fillAlpha = 0.13f; l.stroke = s
            }
            DiagramKind.SANKEY -> l.fill = when (n.role) { "c1" -> blend(P.paper, P.ink, 0.5f); "c2" -> blend(P.paper, P.ink, 0.32f); else -> P.ink }
            DiagramKind.TREEMAP -> {
                // Büyük pay biraz daha koyu (kağıttan mürekkebe), kenar ince kağıt çizgisi
                val max = x.nodes.maxOfOrNull { DiagramCatalog.parse(it.text).values.lastOrNull() ?: 0f }?.coerceAtLeast(1e-3f) ?: 1f
                val v = DiagramCatalog.parse(n.text).values.lastOrNull() ?: 0f
                l.fill = blend(P.paper, P.ink, 0.05f + 0.22f * (v / max).coerceIn(0f, 1f)); l.stroke = P.paper; l.width = x.line * 1.6f
            }
            else -> Unit
        }
        when (n.role) {
            "focal" -> {
                l.stroke = P.accent; l.width = x.line * 1.7f; l.bold = true; l.eyebrow = P.accent
                when {
                    n.box == DiagramBox.DOT || n.box == DiagramBox.BAR -> l.fill = P.accent
                    n.box == DiagramBox.LABEL -> l.text = P.accent
                    n.box == DiagramBox.GROUP -> l.text = P.accent
                    l.fill != null -> l.fill = blend(P.paper, P.accent, 0.09f)
                }
            }
            "store" -> l.fill = P.soft
            "external" -> { l.stroke = P.muted; l.dashed = true }
            "input" -> { l.fill = P.soft; l.stroke = P.rule }
            "optional", "waiting", "outer" -> { l.stroke = P.muted; l.dashed = true }
            "security" -> {
                l.fill = blend(P.paper, P.link, 0.07f); l.stroke = P.link; l.eyebrow = P.link
                if (n.box == DiagramBox.CARD) l.bar = P.link
            }
            "hub", "activity" -> { l.fill = P.ink; l.stroke = P.ink; l.text = P.onInk; l.sub = alpha(P.onInk, 0.72f); l.eyebrow = l.sub; l.bold = true }
            "boundary" -> { l.dashed = true; l.stroke = P.muted }
            "done" -> { l.fill = P.soft; l.stroke = P.rule; l.text = P.muted }
            "blocked" -> { l.stroke = P.accent; l.bar = P.accent }
            "cold" -> { l.fill = blend(P.paper, P.link, 0.07f); l.stroke = P.link; l.dashed = true }
            "added" -> { l.fill = blend(P.paper, P.link, 0.08f); l.stroke = P.link; l.width = x.line * 1.5f }
            "removed" -> { l.fill = P.paper; l.stroke = P.muted; l.dashed = true; l.text = P.muted; l.strike = true }
            "changed" -> { l.fill = blend(P.paper, P.accent, 0.08f); l.stroke = P.accent; l.width = x.line * 1.5f }
            "moving" -> l.fill = P.accent
            "pain" -> { l.stroke = P.accent; l.dashed = true; l.text = P.accent; l.fill = blend(P.paper, P.accent, 0.06f) }
        }
        return l
    }

    private fun themedLook(x: Ctx, index: Int, n: DiagramNode): Look {
        val pal = x.pal
        val theme = pal.theme
        val themeFill = theme?.fill(index)?.let { hex(it) }
        val fill = themeFill ?: pal.styleFill
        val ink = themeFill?.let { f -> theme.stroke?.let { hex(it) } ?: darker(f) } ?: pal.styleInk
        val text = theme?.text?.let { hex(it) } ?: ink
        val l = Look(fill, ink, x.line, text, alpha(text, 0.75f), alpha(text, 0.7f))
        l.fillAlpha = if (themeFill != null) 1f else pal.fillOpacity.coerceIn(0f, 1f)
        l.shadow = theme != null && theme.shadow && x.kind != DiagramKind.VENN
        when (n.box) {
            DiagramBox.GROUP, DiagramBox.LANE, DiagramBox.COLUMN -> { l.fillAlpha *= 0.25f; l.shadow = false }
            DiagramBox.LABEL -> { l.fill = null; l.stroke = null; l.shadow = false }
            DiagramBox.DOT -> { l.fill = ink; l.stroke = null; l.shadow = false }
            DiagramBox.BAR -> { l.fill = themeFill ?: ink; l.stroke = null; l.shadow = false; l.fillAlpha = 1f }
            DiagramBox.CELL -> l.shadow = false
            else -> Unit
        }
        if (x.kind == DiagramKind.VENN && fill != null) l.fillAlpha = 0.45f
        when (n.role) {
            "focal" -> {
                l.width = x.line * 1.7f; l.bold = true
                if (n.box == DiagramBox.DOT || n.box == DiagramBox.BAR) l.fill = pal.accent
            }
            "boundary", "external", "waiting", "optional", "outer", "cold" -> l.dashed = true
            "removed" -> { l.dashed = true; l.strike = true }
            "blocked" -> l.bar = pal.accent
        }
        return l
    }

    /** Kutu biçiminin yolu. */
    fun boxPath(box: DiagramBox, r: RectF, inverted: Boolean, k: Float, cornerRadius: Float): Path {
        val path = Path()
        when (box) {
            DiagramBox.RECT, DiagramBox.CELL, DiagramBox.BAR, DiagramBox.LABEL -> path.addRect(r, Path.Direction.CW)
            DiagramBox.ROUNDED, DiagramBox.TABLE, DiagramBox.GROUP, DiagramBox.LANE, DiagramBox.COLUMN, DiagramBox.CARD, DiagramBox.SLAB -> {
                val rr = min(cornerRadius, min(r.width() / k, r.height()) / 2f)
                path.addRoundRect(r, rr * k, rr, Path.Direction.CW)
            }
            DiagramBox.PILL -> path.addRoundRect(r, r.height() / 2f * k, r.height() / 2f, Path.Direction.CW)
            DiagramBox.CIRCLE, DiagramBox.ELLIPSE, DiagramBox.DOT -> path.addOval(r, Path.Direction.CW)
            DiagramBox.DIAMOND -> {
                path.moveTo(r.centerX(), r.top); path.lineTo(r.right, r.centerY())
                path.lineTo(r.centerX(), r.bottom); path.lineTo(r.left, r.centerY()); path.close()
            }
            DiagramBox.CYLINDER -> {
                val e = r.height() * 0.16f
                path.moveTo(r.left, r.top + e)
                path.lineTo(r.left, r.bottom - e)
                path.arcTo(RectF(r.left, r.bottom - 2 * e, r.right, r.bottom), 180f, -180f)
                path.lineTo(r.right, r.top + e)
                path.arcTo(RectF(r.left, r.top, r.right, r.top + 2 * e), 0f, -180f)
                path.close()
            }
            DiagramBox.NOTE -> {
                val c = min(r.width(), r.height()) * 0.22f
                path.moveTo(r.left, r.top); path.lineTo(r.right - c, r.top); path.lineTo(r.right, r.top + c)
                path.lineTo(r.right, r.bottom); path.lineTo(r.left, r.bottom); path.close()
            }
            DiagramBox.HEXAGON -> {
                val c = min(r.width() * 0.25f, r.height() * 0.5f)
                path.moveTo(r.left + c, r.top); path.lineTo(r.right - c, r.top); path.lineTo(r.right, r.centerY())
                path.lineTo(r.right - c, r.bottom); path.lineTo(r.left + c, r.bottom); path.lineTo(r.left, r.centerY()); path.close()
            }
            DiagramBox.PARALLELOGRAM -> {
                val c = min(r.width() * 0.2f, r.height() * 0.6f)
                path.moveTo(r.left + c, r.top); path.lineTo(r.right, r.top); path.lineTo(r.right - c, r.bottom)
                path.lineTo(r.left, r.bottom); path.close()
            }
            DiagramBox.DOCUMENT -> {
                val wave = r.height() * 0.12f
                path.moveTo(r.left, r.top); path.lineTo(r.right, r.top); path.lineTo(r.right, r.bottom - wave)
                path.cubicTo(r.right - r.width() * 0.25f, r.bottom - wave * 2.2f, r.left + r.width() * 0.45f, r.bottom + wave * 1.2f, r.left, r.bottom - wave * 0.4f)
                path.close()
            }
            DiagramBox.CLOUD -> {
                val cx = r.centerX(); val cy = r.centerY(); val rx = r.width() / 2f; val ry = r.height() / 2f
                val bumps = 9
                for (i in 0..bumps * 8) {
                    val t = i / (bumps * 8f) * 2 * Math.PI
                    val g = 0.86 + 0.14 * abs(sin(t * bumps / 2))
                    val px = cx + (rx * g * cos(t)).toFloat(); val py = cy + (ry * g * sin(t)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            DiagramBox.TRAPEZOID -> {
                val c = min(r.width() * 0.25f, r.height() * 0.5f)
                if (inverted) {
                    path.moveTo(r.left, r.top); path.lineTo(r.right, r.top); path.lineTo(r.right - c, r.bottom)
                    path.lineTo(r.left + c, r.bottom); path.close()
                } else {
                    path.moveTo(r.left + c, r.top); path.lineTo(r.right - c, r.top); path.lineTo(r.right, r.bottom)
                    path.lineTo(r.left, r.bottom); path.close()
                }
            }
            DiagramBox.TRIANGLE -> {
                path.moveTo(r.centerX(), r.top); path.lineTo(r.right, r.bottom); path.lineTo(r.left, r.bottom); path.close()
            }
            DiagramBox.CHEVRON -> {
                val c = min(r.width() * 0.22f, r.height() * 0.5f)
                path.moveTo(r.left, r.top); path.lineTo(r.right - c, r.top); path.lineTo(r.right, r.centerY())
                path.lineTo(r.right - c, r.bottom); path.lineTo(r.left, r.bottom); path.lineTo(r.left + c, r.centerY()); path.close()
            }
        }
        return path
    }

    fun rectOf(n: DiagramNode) = RectF(n.x * U, n.y * U, (n.x + n.w) * U, (n.y + n.h) * U)

    /** Yolu doldurur ve kenarını çizer (gölge, saydamlık, kesik kenar). */
    fun fillStroke(x: Ctx, path: Path, look: Look) {
        val fill = look.fill
        if (look.shadow && fill != null && !x.pal.coverage) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = 0x22000000
            val save = x.c.save()
            x.c.translate(U * 0.006f, U * 0.009f)
            x.c.drawPath(path, p)
            x.c.restoreToCount(save)
        }
        if (fill != null) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = fill
            p.alpha = (Color.alpha(fill) * look.fillAlpha).toInt().coerceIn(0, 255)
            x.c.drawPath(path, p)
        }
        val stroke = look.stroke ?: return
        strokePaint(x, stroke, look.width, look.dashed)
        x.c.drawPath(path, p)
        p.pathEffect = null
    }

    fun strokePaint(x: Ctx, color: Int, width: Float, dashed: Boolean, corner: Float = 0f) {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE
        p.strokeWidth = width; p.color = color; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
        val dash: PathEffect? = if (dashed) DashPathEffect(floatArrayOf(max(5f, width * 3.2f), max(4f, width * 2.4f)), 0f) else null
        val round: PathEffect? = if (corner > 0f) CornerPathEffect(corner) else null
        p.pathEffect = when {
            dash != null && round != null -> ComposePathEffect(dash, round)
            else -> dash ?: round
        }
        if (dashed) p.strokeCap = Paint.Cap.BUTT
    }

    /** Eski kutu biçimleri (yuvarlak köşeli, elmas, silindir…). */
    private fun drawShape(x: Ctx, index: Int, n: DiagramNode, look: Look) {
        val r = rectOf(n)
        val corner = if (x.pal.editorial) 10f else max(8f, x.spec.style.cornerRadius * 3f)
        val path = boxPath(n.box, r, x.kind == DiagramKind.FUNNEL, x.k, corner)
        fillStroke(x, path, look)
        if (n.box == DiagramBox.CYLINDER && look.stroke != null) {
            // Silindirin üst kapağının alt yayı
            val e = r.height() * 0.16f
            strokePaint(x, look.stroke!!, look.width, look.dashed)
            val arc = Path().apply { arcTo(RectF(r.left, r.top, r.right, r.top + 2 * e), 180f, -180f) }
            x.c.drawPath(arc, p)
            p.pathEffect = null
        }
        drawBar(x, r, look)
        if (!x.labels) return
        val tr = textRect(n.box, r, x.kind == DiagramKind.FUNNEL)
        if (x.kind == DiagramKind.VENN) vennShift(n, x.nodes)?.let { (dx, dy) -> tr.offset(dx * r.width(), dy * r.height()) }
        val text = when (x.kind) {
            DiagramKind.TREEMAP -> treemapText(x, n)
            else -> n.text
        }
        when (x.kind) {
            DiagramKind.TREEMAP -> drawBlock(x, text, RectF(r.left + 10f * x.k, r.top + 8f, r.right - 8f * x.k, r.bottom - 6f), look, Align.LEFT, top = true)
            DiagramKind.LAYERS -> drawBlock(x, text, RectF(r.left + 16f * x.k, r.top, r.right - 12f * x.k, r.bottom), look, Align.LEFT)
            else -> drawBlock(x, text, tr, look, Align.CENTER)
        }
    }

    /** Sol kenardaki vurgu çubuğu (engelli kart, güvenlik). */
    private fun drawBar(x: Ctx, r: RectF, look: Look) {
        val bar = look.bar ?: return
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = bar
        val w = 5f * x.k
        x.c.drawRoundRect(RectF(r.left, r.top, r.left + w, r.bottom), w / 2f, 2f, p)
    }

    private fun treemapText(x: Ctx, n: DiagramNode): String {
        val total = x.nodes.sumOf { (DiagramCatalog.parse(it.text).values.lastOrNull() ?: 0f).toDouble() }.toFloat()
        val v = DiagramCatalog.parse(n.text).values.lastOrNull() ?: return n.text
        val name = DiagramCatalog.label(x.kind, n.text)
        val pct = if (total > 0f) v / total * 100f else 0f
        return "$name\n%${fmt(pct)}"
    }

    /** Kutu biçiminin içinde yazının sığacağı dikdörtgen. */
    fun textRect(box: DiagramBox, r: RectF, inverted: Boolean): RectF = when (box) {
        DiagramBox.TRIANGLE -> RectF(r.centerX() - r.width() * 0.3f, r.top + r.height() * 0.45f, r.centerX() + r.width() * 0.3f, r.bottom - r.height() * 0.04f)
        DiagramBox.TRAPEZOID -> {
            val c = min(r.width() * 0.25f, r.height() * 0.5f)
            RectF(r.left + c, r.top, r.right - c, r.bottom).also { if (inverted) it.set(r.left + c, r.top, r.right - c, r.bottom) }
        }
        DiagramBox.CIRCLE, DiagramBox.ELLIPSE, DiagramBox.CLOUD -> RectF(
            r.centerX() - r.width() * 0.36f, r.centerY() - r.height() * 0.36f, r.centerX() + r.width() * 0.36f, r.centerY() + r.height() * 0.36f,
        )
        DiagramBox.DIAMOND -> RectF(r.centerX() - r.width() * 0.3f, r.centerY() - r.height() * 0.3f, r.centerX() + r.width() * 0.3f, r.centerY() + r.height() * 0.3f)
        DiagramBox.HEXAGON, DiagramBox.CHEVRON, DiagramBox.PARALLELOGRAM -> {
            val c = min(r.width() * 0.22f, r.height() * 0.5f)
            RectF(r.left + c, r.top, r.right - c, r.bottom)
        }
        DiagramBox.CYLINDER -> RectF(r.left, r.top + r.height() * 0.2f, r.right, r.bottom - r.height() * 0.06f)
        else -> RectF(r)
    }

    private fun vennShift(node: DiagramNode, nodes: List<DiagramNode>): Pair<Float, Float>? {
        if (nodes.size < 2) return null
        val cx = nodes.map { it.x + it.w / 2f }.average().toFloat()
        val cy = nodes.map { it.y + it.h / 2f }.average().toFloat()
        val dx = node.x + node.w / 2f - cx
        val dy = node.y + node.h / 2f - cy
        val d = hypot(dx, dy)
        if (d < 1e-4f) return null
        return (dx / d * 0.24f) to (dy / d * 0.24f)
    }

    // --- Editoryal kutular ---

    private fun drawGroup(x: Ctx, n: DiagramNode, look: Look) {
        val r = rectOf(n)
        val path = boxPath(DiagramBox.GROUP, r, false, x.k, 12f)
        fillStroke(x, path, look)
        if (!x.labels || n.text.isBlank()) return
        val size = x.label
        val label = n.text.replace('\n', ' ').uppercase(TR)
        val fitted = fitSize(label, mono, size, (r.width() - 24f * x.k) / x.k, 0.08f)
        drawText(x, label, r.left + 12f * x.k, r.top + fitted * 1.35f, mono, fitted, look.text, spacing = 0.08f)
    }

    private fun drawLane(x: Ctx, n: DiagramNode, look: Look, index: Int) {
        val r = rectOf(n)
        val P = x.pal
        if (P.editorial) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
            p.color = if (index % 2 == 0) alpha(P.soft, 0.75f) else P.paper
            x.c.drawRect(r, p)
            strokePaint(x, if (n.role == "focal") P.accent else P.rule, if (n.role == "focal") x.line * 1.4f else x.line * 0.8f, false)
            x.c.drawLine(r.left, r.top, r.right, r.top, p)
            x.c.drawLine(r.left, r.bottom, r.right, r.bottom, p)
        } else {
            fillStroke(x, boxPath(DiagramBox.RECT, r, false, x.k, 0f), look)
        }
        if (!x.labels || n.text.isBlank()) return
        // Kulvarın adı soldaki dar sütunda (eşaralıklı, büyük harf)
        val gutter = min(150f, r.width() * 0.15f)
        val size = x.label
        val label = n.text.replace('\n', ' ').uppercase(TR)
        val color = if (n.role == "focal") P.accent else if (P.editorial) P.muted else look.text
        val fitted = fitSize(label, mono, size, (gutter - 16f) / x.k, 0.06f)
        drawText(x, label, r.left + 12f * x.k, r.centerY() + fitted * 0.35f, mono, fitted, color, spacing = 0.06f)
    }

    private fun drawColumn(x: Ctx, n: DiagramNode, look: Look) {
        val r = rectOf(n)
        fillStroke(x, boxPath(DiagramBox.COLUMN, r, false, x.k, 12f), look)
        val header = n.text.replace('\n', ' ')
        // Sürüyor · 4/3: iş sınırı aşıldıysa başlık vurgulu ve üstte vurgu çizgisi
        val wip = Regex("(\\d+)\\s*/\\s*(\\d+)").find(header)
        val over = wip != null && (wip.groupValues[1].toIntOrNull() ?: 0) > (wip.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE)
        if (over) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = x.pal.accent
            x.c.drawRoundRect(RectF(r.left + 10f * x.k, r.top, r.right - 10f * x.k, r.top + 5f), 2.5f * x.k, 2.5f, p)
        }
        if (!x.labels || header.isBlank()) return
        val size = x.label
        val label = header.uppercase(TR)
        val fitted = fitSize(label, mono, size, (r.width() - 24f * x.k) / x.k, 0.06f)
        drawText(x, label, r.left + 12f * x.k, r.top + fitted * 1.8f, mono, fitted, if (over) x.pal.accent else look.text, spacing = 0.06f)
    }

    private fun drawCard(x: Ctx, n: DiagramNode, look: Look) {
        val r = rectOf(n)
        fillStroke(x, boxPath(DiagramBox.CARD, r, false, x.k, 7f), look)
        drawBar(x, r, look)
        if (!x.labels) return
        val pad = (if (look.bar != null) 14f else 10f) * x.k
        val tall = x.kind == DiagramKind.MEDALLION
        drawBlock(x, n.text, RectF(r.left + pad, r.top + (if (tall) 14f else 4f), r.right - 8f * x.k, r.bottom - 4f), look, Align.LEFT, top = tall)
        if (n.role == "done") {
            // Bitti: sağ üstte küçük onay işareti
            val s = min(r.height() * 0.22f, 14f)
            strokePaint(x, x.pal.muted, x.line, false)
            val cx = r.right - 10f * x.k - s * x.k; val cy = r.top + 8f + s * 0.5f
            val path = Path().apply { moveTo(cx, cy); lineTo(cx + s * 0.4f * x.k, cy + s * 0.4f); lineTo(cx + s * x.k, cy - s * 0.4f) }
            x.c.drawPath(path, p)
        }
    }

    /** Nokta: geniş kutuda solda nokta ve sağında yazı (dörtlü konum, Wardley); kare kutuda yalnız nokta (grafikler). */
    fun dotCenter(n: DiagramNode): Pair<Float, Float> {
        val wide = n.w > n.h * 1.2f
        return if (wide) (n.x + n.h * 0.35f) * U to (n.y + n.h / 2f) * U else (n.x + n.w / 2f) * U to (n.y + n.h / 2f) * U
    }

    private fun drawDot(x: Ctx, n: DiagramNode, look: Look) {
        val wide = n.w > n.h * 1.2f
        val (cx, cy) = dotCenter(n)
        val P = x.pal
        var ry = if (wide) n.h * U * 0.2f else n.h * U * 0.5f
        val fill = look.fill ?: P.ink
        when (x.kind) {
            DiagramKind.LINE -> {
                // Çizgi grafiği: kağıt dolgulu halka; odak (son nokta) vurgu dolgulu
                ry *= 0.62f
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
                p.color = if (n.role == "focal") P.accent else if (P.editorial) P.paper else Color.WHITE
                x.c.drawOval(RectF(cx - ry * x.k, cy - ry, cx + ry * x.k, cy + ry), p)
                strokePaint(x, if (n.role == "focal") P.accent else P.ink, x.line * 1.2f, false)
                x.c.drawOval(RectF(cx - ry * x.k, cy - ry, cx + ry * x.k, cy + ry), p)
                return
            }
            DiagramKind.SCATTER -> {
                ry *= if (n.role == "focal") 0.62f else 0.48f
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
                p.color = if (n.role == "focal") P.accent else alpha(P.ink, 0.72f)
                x.c.drawOval(RectF(cx - ry * x.k, cy - ry, cx + ry * x.k, cy + ry), p)
                return
            }
            DiagramKind.JOURNEY -> {
                val v = DiagramCatalog.parse(n.text).values.lastOrNull() ?: 3f
                ry *= 0.5f
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
                p.color = if (v <= 2f) P.accent else P.ink
                x.c.drawOval(RectF(cx - ry * x.k, cy - ry, cx + ry * x.k, cy + ry), p)
                strokePaint(x, if (P.editorial) P.paper else Color.WHITE, x.line * 1.2f, false)
                x.c.drawOval(RectF(cx - ry * x.k, cy - ry, cx + ry * x.k, cy + ry), p)
                return
            }
            else -> Unit
        }
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = fill
        x.c.drawOval(RectF(cx - ry * x.k, cy - ry, cx + ry * x.k, cy + ry), p)
        if (!wide || !x.labels) return
        val r = RectF(cx + ry * x.k + 8f * x.k, n.y * U, (n.x + n.w) * U, (n.y + n.h) * U)
        drawBlock(x, n.text, r, look, Align.LEFT, maxSize = x.base * 0.8f)
    }

    private fun drawLabel(x: Ctx, n: DiagramNode, look: Look) {
        val r = rectOf(n)
        val P = x.pal
        when (n.role) {
            "chevron" -> {
                val c = min(r.width() * 0.12f, r.height() * 0.5f * x.k)
                val path = Path().apply {
                    moveTo(r.left, r.top); lineTo(r.right - c, r.top); lineTo(r.right, r.centerY())
                    lineTo(r.right - c, r.bottom); lineTo(r.left, r.bottom); lineTo(r.left + c, r.centerY()); close()
                }
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
                p.color = if (P.editorial) P.soft else alpha(P.ink, 0.1f)
                x.c.drawPath(path, p)
                strokePaint(x, if (P.editorial) P.paper else Color.WHITE, x.line * 1.4f, false)
                x.c.drawPath(path, p)
                if (x.labels) monoCentered(x, n.text, r, P.ink, x.label)
            }
            "axis", "row", "col" -> if (x.labels) {
                val dp = x.kind == DiagramKind.DP_SECURITY
                when {
                    n.role == "row" -> monoAligned(x, n.text, r, P.muted, x.label, Align.RIGHT)
                    n.role == "col" && dp -> drawBlock(x, n.text, r, look, Align.CENTER, maxSize = x.base * 0.62f)
                    else -> monoCentered(x, n.text, r, P.muted, x.label)
                }
            }
            "step" -> {
                if (x.labels) monoCentered(x, n.text, r, P.ink, x.label)
                strokePaint(x, P.rule, x.line, false)
                x.c.drawLine(r.left + 6f * x.k, r.bottom, r.right - 6f * x.k, r.bottom, p)
            }
            "focal" -> if (x.kind == DiagramKind.RADAR || x.kind == DiagramKind.POLAR || x.kind == DiagramKind.JOURNEY) {
                if (x.labels) drawBlock(x, chartLabel(x, n), r, look, Align.CENTER, maxSize = x.base * 0.62f)
            } else {
                if (x.labels) monoCentered(x, n.text, r, P.accent, x.label)
                if (x.kind == DiagramKind.DATA_FLOW || r.height() < 80f) {
                    strokePaint(x, P.accent, x.line * 1.6f, false)
                    x.c.drawLine(r.left + 6f * x.k, r.bottom, r.right - 6f * x.k, r.bottom, p)
                }
            }
            "ledger" -> if (x.labels) {
                val sym = n.text.firstOrNull()
                val color = when (sym) { 'Δ' -> P.accent; '+' -> P.link; '−', '-' -> P.muted; else -> P.ink }
                val l = Look(null, null, x.line, color, P.muted, P.muted)
                drawBlock(x, n.text, r, l, Align.LEFT, maxSize = x.base * 0.8f)
            }
            "action" -> if (x.labels) {
                val l = Look(null, null, x.line, P.muted, P.muted, P.muted)
                drawBlock(x, n.text, r, l, Align.CENTER, maxSize = x.base * 0.66f)
            }
            else -> if (x.labels) {
                val chart = x.kind == DiagramKind.RADAR || x.kind == DiagramKind.POLAR
                drawBlock(x, chartLabel(x, n), r, look, Align.CENTER, maxSize = if (chart || x.kind == DiagramKind.JOURNEY) x.base * 0.66f else x.base)
            }
        }
    }

    /** Radar ve kutupsal grafikte ad (değerler atılır); diğerlerinde yazının kendisi. */
    private fun chartLabel(x: Ctx, n: DiagramNode): String =
        if (x.kind == DiagramKind.RADAR || x.kind == DiagramKind.POLAR) DiagramCatalog.label(x.kind, n.text) else n.text

    private fun drawBar(x: Ctx, n: DiagramNode, look: Look) {
        val r = rectOf(n)
        val P = x.pal
        var fill = look.fill ?: P.ink
        var stroke: Int? = null
        if (x.kind == DiagramKind.WATERFALL && n.fillColorHex == null && n.role != "focal") {
            val it = DiagramCatalog.parse(n.text)
            val v = it.values.lastOrNull() ?: 0f
            when {
                it.computed || !it.signed -> fill = P.ink
                v >= 0f -> fill = blend(if (P.editorial) P.paper else Color.WHITE, P.ink, 0.55f)
                else -> { fill = blend(if (P.editorial) P.paper else Color.WHITE, P.ink, 0.12f); stroke = P.muted }
            }
        }
        if (x.kind == DiagramKind.GANTT && n.fillColorHex == null && n.role != "focal") fill = blend(if (P.editorial) P.paper else Color.WHITE, P.ink, 0.7f)
        if (x.pal.coverage) fill = Color.BLACK
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = fill
        val rad = if (x.kind == DiagramKind.GANTT) min(r.height() * 0.3f, 8f) else 2f
        x.c.drawRoundRect(r, rad * x.k, rad, p)
        if (stroke != null) {
            strokePaint(x, stroke, x.line * 0.8f, false)
            x.c.drawRoundRect(r, rad * x.k, rad, p)
        }
    }

    /** Isı haritası hücresi (değere göre kağıttan vurguya) ya da erişim matrisi hücresi (izin düzeyine göre). */
    private fun drawCell(x: Ctx, n: DiagramNode, look: Look) {
        val r = rectOf(n)
        val P = x.pal
        val paper = if (P.editorial) P.paper else Color.WHITE
        val onDark = if (P.editorial) P.onInk else Color.WHITE
        var fill: Int
        var text: Int
        if (x.kind == DiagramKind.DP_SECURITY) {
            val level = accessLevel(n.text)
            fill = when (level) { 0 -> paper; 1 -> blend(paper, P.ink, 0.08f); 2 -> blend(paper, P.ink, 0.2f); 3 -> blend(paper, P.ink, 0.5f); else -> P.ink }
            text = if (level >= 3) onDark else if (level == 0) P.muted else P.ink
        } else {
            val max = x.nodes.filter { it.box == DiagramBox.CELL }.maxOfOrNull { DiagramCatalog.parse(it.text).values.lastOrNull() ?: 0f }?.coerceAtLeast(1e-3f) ?: 1f
            val t = ((DiagramCatalog.parse(n.text).values.lastOrNull() ?: 0f) / max).coerceIn(0f, 1f)
            val tt = Math.pow(t.toDouble(), 0.75).toFloat()
            fill = blend(blend(paper, P.accent, 0.05f), P.accent, tt)
            text = if (tt > 0.55f) onDark else P.ink
        }
        n.fillColorHex?.let { fill = hex(it) }
        n.textColorHex?.let { text = hex(it) }
        // v40 elle verilen dolgu koyuysa yazı açılır (WCAG karşıtlığı)
        if (n.textColorHex == null) text = DiagramContrast.readable(text, fill)
        if (P.coverage) { fill = Color.BLACK; text = Color.BLACK }
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = fill
        x.c.drawRoundRect(r, 3f * x.k, 3f, p)
        if (n.role == "focal") {
            strokePaint(x, if (x.kind == DiagramKind.DP_SECURITY) P.accent else P.ink, x.line * 1.6f, false)
            x.c.drawRoundRect(r, 3f * x.k, 3f, p)
        } else if (x.kind == DiagramKind.DP_SECURITY && accessLevel(n.text) == 0) {
            strokePaint(x, P.rule, x.line * 0.8f, false)
            x.c.drawRoundRect(r, 3f * x.k, 3f, p)
        }
        if (!x.labels) return
        val l = Look(null, null, x.line, text, text, text)
        if (x.kind == DiagramKind.DP_SECURITY) drawBlock(x, n.text, r, l, Align.CENTER, maxSize = x.base * 0.62f)
        else monoCentered(x, n.text, r, text, x.base * 0.62f)
    }

    fun accessLevel(text: String): Int {
        val t = text.lowercase(TR)
        return when {
            t.contains("yok") || t == "—" || t == "-" -> 0
            t.contains("giriş") -> 1
            t.contains("yalnız") || t.contains("okuma") || t.contains("select") -> 2
            t.contains("oku/yaz") || t.contains("yaz") -> 3
            else -> 4
        }
    }

    /** Tablo (varlık, UML sınıfı, veritabanı tablosu): başlık bandı ve satırlar; "--" ayırıcı. */
    private fun drawTable(x: Ctx, n: DiagramNode, look: Look) {
        val r = rectOf(n)
        val P = x.pal
        val lines = n.text.split('\n')
        val stereo = lines.firstOrNull()?.takeIf { it.startsWith("«") && lines.size > 1 }
        val title = if (stereo != null) lines.getOrElse(1) { "" } else lines.firstOrNull().orEmpty()
        val rows = lines.drop(if (stereo != null) 2 else 1)
        val sepCount = rows.count { it.trim() == "--" }
        val rowCount = rows.size - sepCount
        val units = rowCount + 0.45f * sepCount + 1.55f + (if (stereo != null) 0.7f else 0f)
        val rh = min((r.height() - 6f) / max(1f, units), x.base * 1.05f)
        val headerH = rh * (1.55f + if (stereo != null) 0.7f else 0f)
        val path = boxPath(DiagramBox.TABLE, r, false, x.k, 7f)
        // Başlık bandı
        val headFill = when {
            n.fillColorHex != null -> null
            n.role == "focal" && P.editorial -> blend(P.paper, P.accent, 0.12f)
            P.editorial -> P.soft
            else -> look.fill?.let { alpha(it, 1f) }
        }
        if (look.fill != null) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = look.fill!!
            p.alpha = (Color.alpha(look.fill!!) * look.fillAlpha).toInt().coerceIn(0, 255)
            x.c.drawPath(path, p)
        }
        if (headFill != null && !P.coverage) {
            val save = x.c.save()
            x.c.clipPath(path)
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = headFill
            x.c.drawRect(r.left, r.top, r.right, r.top + headerH, p)
            x.c.restoreToCount(save)
        }
        val stroke = look.stroke ?: P.ink
        strokePaint(x, stroke, look.width, look.dashed)
        x.c.drawPath(path, p)
        strokePaint(x, if (P.editorial && n.role != "focal") P.rule else stroke, x.line * 0.9f, false)
        x.c.drawLine(r.left, r.top + headerH, r.right, r.top + headerH, p)
        if (!x.labels) {
            // Küçük resimde satırlar ince çizgiler
            var yy = r.top + headerH + rh * 0.6f
            strokePaint(x, P.rule, x.line * 2f, false)
            rows.filter { it.trim() != "--" }.forEach { _ ->
                x.c.drawLine(r.left + 10f * x.k, yy, r.left + r.width() * 0.6f, yy, p); yy += rh
            }
            return
        }
        val pad = 10f * x.k
        val inner = (r.width() - 2 * pad) / x.k
        // Başlık
        var hy = r.top + rh * 0.35f
        if (stereo != null) {
            val s = fitSize(stereo, mono, rh * 0.5f, inner, 0f)
            hy += s
            drawText(x, stereo, r.centerX() - measure(stereo, mono, s, 0f) * x.k / 2f, hy, mono, s, P.muted)
            hy += s * 0.35f
        }
        val ts = fitSize(title, medium, rh * 0.78f, inner, 0f)
        hy += ts * 1.05f
        drawText(x, title, r.centerX() - measure(title, medium, ts, 0f) * x.k / 2f, hy, medium, ts, if (n.role == "focal" && P.editorial) P.accent else look.text)
        // Satırlar
        val rs0 = rh * 0.58f
        val rs = rows.filter { it.trim() != "--" }.minOfOrNull { fitSize(it.replace(" : ", "  "), mono, rs0, inner, 0f) } ?: rs0
        var y = r.top + headerH
        rows.forEachIndexed { j, row ->
            if (row.trim() == "--" && j == 0) { y += rh * 0.2f; return@forEachIndexed }
            if (row.trim() == "--") {
                strokePaint(x, P.rule, x.line * 0.8f, false)
                x.c.drawLine(r.left, y + rh * 0.22f, r.right, y + rh * 0.22f, p)
                y += rh * 0.45f
                return@forEachIndexed
            }
            val base = y + rh * 0.5f + rs * 0.36f
            if (row.contains(" : ")) {
                // ad : tür : kısıt (veritabanı tablosu)
                val parts = row.split(" : ").map { it.trim() }
                drawText(x, parts[0], r.left + pad, base, mono, rs, look.text)
                var right = r.right - pad
                parts.getOrNull(2)?.let { c ->
                    val color = when (c.uppercase(TR)) { "PK" -> P.accent; "FK" -> P.link; else -> P.muted }
                    val w = measure(c, mono, rs * 0.85f, 0.04f) * x.k
                    drawText(x, c, right - w, base, mono, rs * 0.85f, color, spacing = 0.04f)
                    right -= w + 10f * x.k
                }
                parts.getOrNull(1)?.let { t ->
                    val w = measure(t, mono, rs, 0f) * x.k
                    drawText(x, t, right - w, base, mono, rs, P.muted)
                }
            } else {
                val sym = row.take(2)
                val color = when {
                    sym.startsWith("#") -> P.accent
                    sym.startsWith("→") -> P.link
                    sym.startsWith("+") || sym.startsWith("-") || sym.startsWith("~") -> P.muted
                    else -> null
                }
                if (color != null && row.length > 2 && row[1] == ' ') {
                    drawText(x, row.substring(0, 1), r.left + pad, base, mono, rs, color)
                    val off = measure("${row[0]} ", mono, rs, 0f) * x.k
                    drawText(x, row.substring(2), r.left + pad + off, base, mono, rs, look.text)
                } else drawText(x, row, r.left + pad, base, mono, rs, look.text)
            }
            y += rh
        }
    }

    // ------------------------------------------------------------------------------------------ oklar

    private fun drawEdges(x: Ctx) {
        if (x.kind == DiagramKind.LINE) return
        x.spec.edges.forEachIndexed { i, edge ->
            val a = x.byId[edge.from] ?: return@forEachIndexed
            val b = x.byId[edge.to] ?: return@forEachIndexed
            when {
                x.kind == DiagramKind.MIND_MAP -> drawBranch(x, i, a, b, edge)
                x.kind == DiagramKind.SANKEY -> drawRibbon(x, i, a, edge)
                else -> drawEdge(x, i, edge, a, b)
            }
        }
    }

    fun edgeColor(x: Ctx, edge: DiagramEdge): Int {
        val P = x.pal
        if (P.coverage) return Color.BLACK
        edge.colorHex?.let { return hex(it) }
        return when (edge.role) {
            "accent", "cycle" -> P.accent
            "link" -> P.link
            "async", "spoke" -> P.muted
            else -> if (P.editorial && x.kind == DiagramKind.WARDLEY) P.muted else P.edge
        }
    }

    private fun drawEdge(x: Ctx, index: Int, edge: DiagramEdge, a: DiagramNode, b: DiagramNode) {
        val route = DiagramRenderer.routeOf(x.spec, index, edge, a, b)
        if (route.size < 2) return
        val color = edgeColor(x, edge)
        val strong = edge.role == "accent" || edge.role == "cycle"
        val width = x.line * (if (strong) 1.5f else 1f) * (if (x.kind == DiagramKind.WARDLEY) 0.8f else 1f)
        val dashed = edge.dashed || edge.role in setOf("async", "spoke", "realize", "depend")
        val role = edge.role
        val hollow = role == "inherit" || role == "realize"
        val open = role == "depend" || (x.kind == DiagramKind.SEQUENCE && (edge.dashed || role == "async"))
        // Ok ucu çizgi kalınlığıyla büyür (eski temalarda kalın çizgiler)
        val headLen = max(if (hollow) 18f else 14f, width * (if (hollow) 3.4f else 2.8f))
        val headHalf = max(if (hollow) 9f else 5.5f, width * (if (hollow) 1.7f else 1.15f))
        val pts = route.map { floatArrayOf(it.x, it.y) }.toMutableList()
        // Ok ucu çizginin ucunu örter: çizgi ucun tabanında biter
        val end = pts.last()
        val prev = pts[pts.size - 2]
        val dir = visualDir(x, prev[0], prev[1], end[0], end[1])
        if ((edge.arrow || hollow) && dir != null && !open) {
            val back = if (hollow) headLen else headLen * 0.75f
            val segLen = hypot((end[0] - prev[0]) / x.k, end[1] - prev[1])
            if (segLen > back * 1.2f) pts[pts.size - 1] = floatArrayOf(end[0] - dir[0] * back * x.k, end[1] - dir[1] * back)
        }
        // Bileşim / toplama: kaynakta elmas
        val startDiamond = role == "compose" || role == "aggregate"
        if (startDiamond) {
            val s = pts[0]; val n1 = pts[1]
            val d = visualDir(x, n1[0], n1[1], s[0], s[1])
            if (d != null) pts[0] = floatArrayOf(s[0] - d[0] * 20f * x.k, s[1] - d[1] * 20f)
        }
        val line = Path()
        line.moveTo(pts[0][0], pts[0][1])
        for (k in 1 until pts.size) line.lineTo(pts[k][0], pts[k][1])
        val elbow = pts.size > 2 && x.kind !in DiagramCatalog.straightKinds && edge.role != "arc"
        strokePaint(x, color, width, dashed, if (elbow) 14f else 0f)
        x.c.drawPath(line, p)
        p.pathEffect = null
        // Uçlar
        if (dir != null) {
            when {
                hollow -> {
                    val head = headPath(x, end[0], end[1], dir, headLen, headHalf)
                    p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = if (x.pal.editorial) x.pal.paper else Color.WHITE
                    x.c.drawPath(head, p)
                    strokePaint(x, color, width, false); x.c.drawPath(head, p)
                }
                open && edge.arrow -> {
                    strokePaint(x, color, width, false)
                    val l = 13f; val h = 6f
                    val bx = end[0] / x.k - dir[0] * l; val by = end[1] - dir[1] * l
                    val path = Path().apply {
                        moveTo((bx - dir[1] * h) * x.k, by + dir[0] * h); lineTo(end[0], end[1]); lineTo((bx + dir[1] * h) * x.k, by - dir[0] * h)
                    }
                    x.c.drawPath(path, p)
                }
                edge.arrow -> {
                    p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = color
                    x.c.drawPath(headPath(x, end[0], end[1], dir, headLen, headHalf), p)
                }
            }
        }
        if (startDiamond) {
            val s = route[0]; val n1 = route[1]
            val d = visualDir(x, n1.x, n1.y, s.x, s.y)
            if (d != null) {
                val l = 20f; val h = 7f
                val mx = s.x / x.k - d[0] * l / 2f; val my = s.y - d[1] * l / 2f
                val path = Path().apply {
                    moveTo(s.x, s.y); lineTo((mx - d[1] * h) * x.k, my + d[0] * h)
                    lineTo((s.x / x.k - d[0] * l) * x.k, s.y - d[1] * l); lineTo((mx + d[1] * h) * x.k, my - d[0] * h); close()
                }
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
                p.color = if (role == "compose") color else if (x.pal.editorial) x.pal.paper else Color.WHITE
                x.c.drawPath(path, p)
                strokePaint(x, color, width, false); x.c.drawPath(path, p)
            }
        }
        // Varlık–ilişki sayıları: N / M / * kaz ayağı, 1 tek çizgi
        edge.fromLabel?.let { cardinality(x, route[0], route[1], it, color, width) }
        edge.toLabel?.let { cardinality(x, route.last(), route[route.size - 2], it, color, width) }
        if (x.labels && edge.label.isNotBlank()) edgeLabel(x, edge, route, color)
    }

    /** [from]→[to] yönünün görsel birim vektörü (yatay sıkıştırma hesaba katılır). */
    fun visualDir(x: Ctx, fx: Float, fy: Float, tx: Float, ty: Float): FloatArray? {
        val vx = (tx - fx) / x.k; val vy = ty - fy
        val d = hypot(vx, vy)
        if (d < 1e-3f) return null
        return floatArrayOf(vx / d, vy / d)
    }

    fun headPath(x: Ctx, tx: Float, ty: Float, dir: FloatArray, len: Float, half: Float): Path {
        val bx = tx / x.k - dir[0] * len; val by = ty - dir[1] * len
        return Path().apply {
            moveTo(tx, ty)
            lineTo((bx - dir[1] * half) * x.k, by + dir[0] * half)
            lineTo((bx + dir[1] * half) * x.k, by - dir[0] * half)
            close()
        }
    }

    private fun cardinality(x: Ctx, end: RoutePoint, inner: RoutePoint, label: String, color: Int, width: Float) {
        val d = visualDir(x, inner.x, inner.y, end.x, end.y) ?: return
        val many = label.trim().let { it.equals("N", true) || it.equals("M", true) || it == "*" || it.contains("..*") || it.contains("n") }
        strokePaint(x, color, width, false)
        val ex = end.x / x.k; val ey = end.y
        if (many) {
            val l = 16f; val h = 9f
            val mx = ex - d[0] * l; val my = ey - d[1] * l
            val path = Path().apply {
                moveTo(mx * x.k, my); lineTo((ex - d[1] * h) * x.k, ey + d[0] * h)
                moveTo(mx * x.k, my); lineTo(end.x, end.y)
                moveTo(mx * x.k, my); lineTo((ex + d[1] * h) * x.k, ey - d[0] * h)
            }
            x.c.drawPath(path, p)
        } else {
            val l = 10f; val h = 8f
            val mx = ex - d[0] * l; val my = ey - d[1] * l
            x.c.drawLine((mx - d[1] * h) * x.k, my + d[0] * h, (mx + d[1] * h) * x.k, my - d[0] * h, p)
        }
        if (!x.labels) return
        val s = (x.base * 0.48f).coerceIn(9f, 24f)
        val off = 28f
        val lx = (ex - d[0] * off + -d[1] * 12f) * x.k
        val ly = ey - d[1] * off + d[0] * 12f
        drawText(x, label, lx - measure(label, mono, s, 0f) * x.k / 2f, ly + s * 0.36f, mono, s, x.pal.muted)
    }

    private fun edgeLabel(x: Ctx, edge: DiagramEdge, route: List<RoutePoint>, color: Int) {
        val P = x.pal
        val upper = x.kind != DiagramKind.SEQUENCE && x.kind != DiagramKind.SANKEY
        val text = if (upper) edge.label.uppercase(TR) else edge.label
        val size = if (x.kind == DiagramKind.SEQUENCE) (x.base * 0.58f).coerceIn(10f, 30f) else x.label
        val spacing = if (upper) 0.06f else 0f
        val w = measure(text, mono, size, spacing) * x.k
        val textColor = if (edge.role == "accent" || edge.role == "link" || edge.role == "cycle") color else if (P.editorial) P.muted else color
        val (mx, my) = when {
            x.kind == DiagramKind.SEQUENCE -> {
                val m = DiagramRouting.midpoint(route)
                m.x to m.y - size * 0.75f
            }
            edge.role == "arc" -> {
                val top = route.minByOrNull { it.y }!!
                top.x to top.y - size * 0.2f
            }
            else -> labelPoint(x, route, w, size)
        }
        if (P.editorial) {
            // Kağıtla maskelenmiş yazı (çizgiyi keser)
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = P.paper
            val padX = 5f * x.k; val padY = size * 0.32f
            x.c.drawRoundRect(RectF(mx - w / 2f - padX, my - size * 0.62f - padY * 0.5f, mx + w / 2f + padX, my + size * 0.42f + padY * 0.5f), 3f * x.k, 3f, p)
            drawText(x, text, mx - w / 2f, my + size * 0.33f, mono, size, textColor, spacing = spacing)
        } else {
            drawText(x, text, mx - w / 2f, my - size * 0.4f, mono, size, textColor, spacing = spacing)
        }
    }

    /**
     * Ok yazısının yeri: dirsekli okta son parça yazıya yetecek kadar uzunsa onun ortası (dallanan oklarda yazılar
     * ortak gövdede üst üste binmez), değilse yolun ortası.
     */
    private fun labelPoint(x: Ctx, route: List<RoutePoint>, w: Float, size: Float): Pair<Float, Float> {
        if (route.size >= 3) {
            val a = route[route.size - 2]; val b = route.last()
            val vertical = abs(a.x - b.x) < 1e-3f
            val len = if (vertical) abs(b.y - a.y) else abs(b.x - a.x)
            val need = if (vertical) size * 2.6f else w + 24f * x.k
            if (len > need + 24f) return ((a.x + b.x) / 2f) to ((a.y + b.y) / 2f)
        }
        val m = DiagramRouting.midpoint(route)
        return m.x to m.y
    }

    /** Zihin haritası dalı: merkezden düğüme yumuşak eğri. */
    private fun drawBranch(x: Ctx, index: Int, a: DiagramNode, b: DiagramNode, edge: DiagramEdge) {
        val P = x.pal
        val ax = (a.x + a.w / 2f) * U; val ay = (a.y + a.h / 2f) * U
        val bx = (b.x + b.w / 2f) * U; val by = (b.y + b.h / 2f) * U
        val color = when {
            P.coverage -> Color.BLACK
            edge.colorHex != null -> hex(edge.colorHex!!)
            P.editorial -> if (b.role == "focal") P.accent else P.muted
            else -> P.theme?.fill(x.nodes.indexOf(b))?.let { darker(hex(it)) } ?: P.edge
        }
        strokePaint(x, color, x.line * (if (P.editorial) 1.3f else 1.6f), false)
        val path = Path()
        path.moveTo(ax, ay)
        path.cubicTo(ax + (bx - ax) * 0.5f, ay, ax + (bx - ax) * 0.5f, by, bx, by)
        x.c.drawPath(path, p)
    }

    /** Sankey şeridi: kaynaktan hedefe değerle orantılı kalınlıkta yumuşak bant. */
    private fun drawRibbon(x: Ctx, index: Int, a: DiagramNode, edge: DiagramEdge) {
        val r = x.ribbons[index] ?: return
        val P = x.pal
        val x0 = r.x0 * U; val x1 = r.x1 * U
        val y0 = r.y0 * U; val y1 = r.y1 * U; val h = r.h * U
        val mx = (x0 + x1) / 2f
        val path = Path().apply {
            moveTo(x0, y0); cubicTo(mx, y0, mx, y1, x1, y1)
            lineTo(x1, y1 + h); cubicTo(mx, y1 + h, mx, y0 + h, x0, y0 + h); close()
        }
        val base = when {
            P.coverage -> Color.BLACK
            edge.colorHex != null -> hex(edge.colorHex!!)
            edge.role == "accent" -> P.accent
            else -> lookOf(x, x.nodes.indexOf(a), a).fill ?: P.ink
        }
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = base
        p.alpha = if (P.coverage) 255 else if (edge.role == "accent") 115 else 52
        x.c.drawPath(path, p)
        if (x.labels && edge.label.isNotBlank() && h > 14f) {
            val s = min((x.base * 0.46f).coerceIn(9f, 22f), h * 0.8f)
            val text = fmt(DiagramCatalog.parse(edge.label).values.lastOrNull() ?: 0f)
            val tx = x1 - 10f * x.k - measure(text, mono, s, 0f) * x.k
            drawText(x, text, tx, y1 + h / 2f + s * 0.36f, mono, s, if (edge.role == "accent") P.accent else P.muted)
        }
    }

    // ------------------------------------------------------------------------------------------ yazı

    enum class Align { LEFT, CENTER, RIGHT }

    /** Yazının satırları: üst etiket ("[ETİKET]" ilk satırda), başlık (kaydırılır) ve alt satırlar (eşaralıklı). */
    class Block(val eyebrow: String?, val title: List<String>, val subs: List<String>, val size: Float) {
        val eyebrowSize get() = size * 0.62f
        val subSize get() = size * 0.72f
        val height: Float
            get() = (if (eyebrow != null) eyebrowSize * 1.45f else 0f) + title.size * size * 1.18f + subs.size * subSize * 1.38f
    }

    private val blockCache = object : android.util.LruCache<String, Block>(768) {}

    fun measure(text: String, face: Typeface, size: Float, spacing: Float): Float {
        val t = tp
        t.reset(); t.isAntiAlias = true; t.typeface = face; t.textSize = size; t.letterSpacing = spacing
        return t.measureText(text)
    }

    /** [text] tek satırda [maxWidth] (görsel birim) içine sığacak boyut ([size] en çok). */
    fun fitSize(text: String, face: Typeface, size: Float, maxWidth: Float, spacing: Float): Float {
        if (text.isEmpty() || maxWidth <= 0f) return size
        val w = measure(text, face, size, spacing)
        return if (w <= maxWidth) size else max(6f, size * maxWidth / w)
    }

    fun block(text: String, width: Float, height: Float, base: Float, k: Float, bold: Boolean): Block {
        val key = "$text|${width.toInt()}|${height.toInt()}|${base.toInt()}|${(k * 100).toInt()}|$bold"
        blockCache.get(key)?.let { return it }
        val lines = text.split('\n')
        val eyebrow = lines.firstOrNull()?.trim()?.takeIf { lines.size > 1 && it.length > 2 && it.startsWith("[") && it.endsWith("]") }
            ?.removePrefix("[")?.removeSuffix("]")?.uppercase(TR)
        val rest = if (eyebrow != null) lines.drop(1) else lines
        val titleText = rest.firstOrNull().orEmpty()
        val subs = rest.drop(1)
        val maxW = width / k
        val face = if (bold) medium else sans
        var size = base
        var title = wrap(titleText, face, size, maxW)
        fun fits(): Boolean {
            val b = Block(eyebrow, title, subs, size)
            if (b.height > height * 0.94f) return false
            if (title.any { measure(it, face, size, 0f) > maxW }) return false
            if (eyebrow != null && measure(eyebrow, mono, b.eyebrowSize, 0.08f) > maxW) return false
            if (subs.any { measure(it, mono, b.subSize, 0f) > maxW }) return false
            return true
        }
        while (!fits() && size > 6.5f) {
            size *= 0.9f
            title = wrap(titleText, face, size, maxW)
        }
        val b = Block(eyebrow, title, subs, size)
        blockCache.put(key, b)
        return b
    }

    private fun wrap(text: String, face: Typeface, size: Float, maxW: Float): List<String> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf(text)
        val out = ArrayList<String>()
        var cur = StringBuilder()
        words.forEach { w ->
            val cand = if (cur.isEmpty()) w else "$cur $w"
            if (measure(cand, face, size, 0f) <= maxW || cur.isEmpty()) cur = StringBuilder(cand)
            else { out += cur.toString(); cur = StringBuilder(w) }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out.take(4)
    }

    /** Yazı bloğunu [r] içine çizer (dikeyde ortalı; [top] ise üste yaslı). */
    fun drawBlock(x: Ctx, text: String, r: RectF, look: Look, align: Align, top: Boolean = false, maxSize: Float = x.base) {
        if (text.isBlank() || r.width() <= 2f || r.height() <= 2f) return
        val bold = look.bold || x.pal.editorial && (x.kind == DiagramKind.LAYERS)
        val b = block(text, r.width() * 0.92f, r.height(), min(maxSize, x.base), x.k, bold)
        val face = if (bold) medium else sans
        var y = if (top) r.top + 4f else r.centerY() - b.height / 2f
        fun xFor(w: Float) = when (align) {
            Align.LEFT -> r.left
            Align.CENTER -> r.centerX() - w / 2f
            Align.RIGHT -> r.right - w
        }
        b.eyebrow?.let { e ->
            val s = b.eyebrowSize
            y += s * 1.15f
            drawText(x, e, xFor(measure(e, mono, s, 0.08f) * x.k), y, mono, s, look.eyebrow, spacing = 0.08f)
            y += s * 0.3f
        }
        b.title.forEach { line ->
            y += b.size * 1.0f
            val w = measure(line, face, b.size, 0f) * x.k
            val lx = xFor(w)
            drawText(x, line, lx, y, face, b.size, look.text)
            if (look.strike) {
                strokePaint(x, look.text, max(1f, b.size * 0.07f), false)
                x.c.drawLine(lx, y - b.size * 0.32f, lx + w, y - b.size * 0.32f, p)
            }
            y += b.size * 0.18f
        }
        b.subs.forEach { line ->
            val s = b.subSize
            y += s * 1.1f
            drawText(x, line, xFor(measure(line, mono, s, 0f) * x.k), y, mono, s, look.sub)
            y += s * 0.28f
        }
    }

    fun monoCentered(x: Ctx, text: String, r: RectF, color: Int, size: Float) = monoAligned(x, text, r, color, size, Align.CENTER)

    fun monoAligned(x: Ctx, text: String, r: RectF, color: Int, size: Float, align: Align) {
        val t = text.replace('\n', ' ').uppercase(TR)
        val s = fitSize(t, mono, min(size, r.height() * 0.7f).coerceAtLeast(6f), (r.width() - 6f * x.k) / x.k, 0.06f)
        val w = measure(t, mono, s, 0.06f) * x.k
        val lx = when (align) { Align.LEFT -> r.left + 4f * x.k; Align.CENTER -> r.centerX() - w / 2f; Align.RIGHT -> r.right - 4f * x.k - w }
        drawText(x, t, lx, r.centerY() + s * 0.36f, mono, s, color, spacing = 0.06f)
    }

    /** Yazıyı yatay sıkıştırmayla ([Ctx.k]) çizer: diyagram ne kadar geniş olursa olsun harfler orantılı kalır. */
    fun drawText(x: Ctx, text: String, px: Float, py: Float, face: Typeface, size: Float, color: Int, spacing: Float = 0f) {
        if (!x.labels || text.isEmpty()) return
        val t = tp
        t.reset(); t.isAntiAlias = true; t.typeface = face; t.textSize = size; t.letterSpacing = spacing
        t.color = if (x.pal.coverage) Color.BLACK else color
        val save = x.c.save()
        x.c.translate(px, py)
        x.c.scale(x.k, 1f)
        x.c.drawText(text, 0f, 0f, t)
        x.c.restoreToCount(save)
    }

    fun fmt(v: Float): String {
        val r = Math.round(v)
        return if (abs(v - r) < 0.05f) String.format(TR, "%,d", r.toLong()) else String.format(TR, "%,.1f", v)
    }
}
