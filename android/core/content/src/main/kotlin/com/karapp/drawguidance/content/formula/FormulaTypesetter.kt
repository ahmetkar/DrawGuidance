package com.karapp.drawguidance.content.formula

import com.karapp.drawguidance.content.formula.AtomType.BIN
import com.karapp.drawguidance.content.formula.AtomType.CLOSE
import com.karapp.drawguidance.content.formula.AtomType.INNER
import com.karapp.drawguidance.content.formula.AtomType.OP
import com.karapp.drawguidance.content.formula.AtomType.OPEN
import com.karapp.drawguidance.content.formula.AtomType.ORD
import com.karapp.drawguidance.content.formula.AtomType.PUNCT
import com.karapp.drawguidance.content.formula.AtomType.REL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------- kutular

/** TeX kutusu: genişlik, taban çizgisi üstü (h) ve altı (d); çizimler kutunun kendi koordinatlarında. */
internal class Box(
    val w: Float, val h: Float, val d: Float,
    val ops: List<DrawOp>, val slots: List<SlotRegion>,
    /** İtalik düzeltmesi (TeX δ): üst indis bu kadar sağa kayar; tek başına dizilirken genişliğe eklenir. */
    val ic: Float = 0f,
    /** Tek karakterlik çekirdek mi (TeX kural 18: indis kaydırması buna göre). */
    val char: Boolean = false,
) {
    fun withIc(): Box = if (ic <= 0f) this else Box(w + ic, h, d, ops, slots, 0f, false)

    companion object {
        val EMPTY = Box(0f, 0f, 0f, emptyList(), emptyList())
    }
}

internal fun DrawOp.shifted(dx: Float, dy: Float): DrawOp {
    if (dx == 0f && dy == 0f) return this
    return when (this) {
        is DrawOp.Glyph -> copy(x = x + dx, y = y + dy)
        is DrawOp.Rule -> copy(x1 = x1 + dx, y1 = y1 + dy, x2 = x2 + dx, y2 = y2 + dy)
        is DrawOp.Stroke -> {
            val p = ArrayList<Float>(points.size)
            for (k in points.indices) p.add(points[k] + if (k % 2 == 0) dx else dy)
            copy(points = p)
        }
    }
}

internal class BoxBuilder {
    val ops = ArrayList<DrawOp>()
    val slots = ArrayList<SlotRegion>()
    var x = 0f
    var h = 0f
    var d = 0f

    fun put(b: Box, dx: Float, dy: Float) {
        for (o in b.ops) ops.add(o.shifted(dx, dy))
        for (r in b.slots) slots.add(r.copy(left = r.left + dx, right = r.right + dx, top = r.top + dy, bottom = r.bottom + dy))
        h = max(h, b.h - dy)
        d = max(d, b.d + dy)
    }

    fun append(b: Box, dy: Float = 0f) {
        put(b, x, dy)
        x += b.w
    }

    fun op(o: DrawOp) {
        ops.add(o)
    }

    fun build(w: Float = x, ic: Float = 0f, char: Boolean = false) = Box(w, h, d, ops, slots, ic, char)
}

/** TeX biçemleri: 0 = display, 1 = text, 2 = script, 3 = scriptscript; cramped üst indisleri alçaltır. */
internal class MathStyle(val level: Int, val cramped: Boolean) {
    val scale: Float get() = SCALE[level]
    private val smaller get() = if (level < 2) 2 else 3
    fun sup() = MathStyle(smaller, cramped)
    fun sub() = MathStyle(smaller, true)
    fun num() = MathStyle(if (level == 0) 1 else smaller, cramped)
    fun den() = MathStyle(if (level == 0) 1 else smaller, true)
    fun cramp() = MathStyle(level, true)

    companion object {
        val SCALE = floatArrayOf(1f, 1f, 0.7f, 0.5f)
        val DISPLAY = MathStyle(0, false)
    }
}

// ---------------------------------------------------------------- dizgi

/**
 * TeX kurallarına yakın dizgi (The TeXbook, Ek G): atom sınıfları arası boşluklar, ikili işleç yeniden sınıflama,
 * üst/alt indis kaydırmaları, kesir/kök yerleşimi, büyük işleç sınırları. Ölçüler geçerli biçemin em'i cinsindendir.
 */
internal class Typesetter(private val metrics: GlyphMetrics, private val size: Float) {

    private class Atom(val box: Box, var left: AtomType, var right: AtomType, val st: MathStyle, val space: Float = Float.NaN) {
        val isSpace get() = !space.isNaN()
    }

    class RowResult(val box: Box, val count: Int, val first: AtomType, val last: AtomType)

    private val inkCache = HashMap<String, FloatArray>()
    private val advCache = HashMap<String, Float>()
    private val hasInk: Boolean = try {
        metrics.inkBounds("x", REF, GlyphStyle.ROMAN) != null
    } catch (e: Exception) {
        false
    }

    /** Yazı tipi parametreleri (em cinsinden). */
    val xh: Float
    val axis: Float
    val cap: Float
    val rule: Float
    private val fs: Float
    private val parenTop: Float
    private val parenBot: Float

    init {
        xh = (-ink("x", GlyphStyle.ROMAN)[1]).takeIf { it in 0.3f..0.75f } ?: EST_XH
        val m = ink("−", GlyphStyle.ROMAN)
        val ax = -(m[1] + m[3]) / 2
        axis = if (hasInk && ax in 0.15f..0.5f) ax else EST_AXIS
        cap = (-ink("H", GlyphStyle.ROMAN)[1]).takeIf { it in 0.5f..0.9f } ?: EST_CAP
        val th = m[3] - m[1]
        rule = if (hasInk && th in 0.02f..0.15f) (th * 0.8f).coerceIn(0.05f, 0.062f) else 0.055f
        fs = (cap / 0.683f).coerceIn(0.92f, 1.15f)
        val p = ink("(", GlyphStyle.ROMAN)
        parenTop = (-p[1]).takeIf { it in 0.5f..1.2f } ?: 0.76f
        parenBot = p[3].takeIf { it in 0.05f..0.5f } ?: 0.2f
    }

    private fun em(st: MathStyle) = size * st.scale
    private fun t(st: MathStyle) = rule * size * max(st.scale, 0.8f)

    // ------------------------------------------------ yazı ölçüleri

    private fun adv(text: String, sz: Float, style: GlyphStyle): Float {
        val key = "${style.ordinal}|$sz|$text"
        advCache[key]?.let { return it }
        val v = metrics.advance(text, sz, style)
        advCache[key] = v
        return v
    }

    /** Mürekkep kutusu (em, boyut 1): [left, top, right, bottom], y aşağı. */
    fun ink(text: String, style: GlyphStyle): FloatArray {
        val key = "${style.ordinal}$text"
        inkCache[key]?.let { return it }
        var r: FloatArray? = null
        if (hasInk) {
            val b = try {
                metrics.inkBounds(text, REF, style)
            } catch (e: Exception) {
                null
            }
            if (b != null && b.size >= 4 && b.all { it.isFinite() }) {
                r = if (b[2] <= b[0] || b[3] <= b[1]) floatArrayOf(0f, 0f, adv(text, REF, style) / REF, 0f)
                else floatArrayOf(b[0] / REF, b[1] / REF, b[2] / REF, b[3] / REF)
            }
        }
        if (r == null) r = estimate(text, style)
        inkCache[key] = r
        return r
    }

    private fun estimate(text: String, style: GlyphStyle): FloatArray {
        val italic = style == GlyphStyle.ITALIC || style == GlyphStyle.BOLD_ITALIC
        var top = 0f
        var bot = 0f
        for (c in text) {
            top = max(top, estTop(c))
            bot = max(bot, if (italic && c == 'f') EST_DSC else estBot(c))
        }
        val w = adv(text, REF, style) / REF
        val ic = if (italic && text.isNotEmpty()) estIc(text.last()) else 0f
        return floatArrayOf(0f, -top, w + ic, bot)
    }

    private fun estTop(c: Char): Float = when {
        c in "acemnorsuvwxzıαεικνοπστυωγημρχψφςgpqyşç" -> EST_XH
        c in "bdfhklβδζθλξϑ" -> EST_ASC
        c == 't' -> 0.64f
        c == 'i' || c == 'j' || c in "ğöü" -> 0.74f
        c in "İĞÖÜ" -> 0.92f
        c.isDigit() || c.isUpperCase() -> EST_CAP
        c in "()[]{}|" -> 0.76f
        c in "+±∓×÷∈∉⊂⊆⊃⊇∪∩" -> EST_AXIS + 0.3f
        c in "−=≠≡≈∼" -> EST_AXIS + 0.16f
        c in "<>≤≥" -> EST_AXIS + 0.29f
        c in "→←↔⇒⇐⇔∝∞" -> EST_AXIS + 0.2f
        c == '·' -> EST_AXIS + 0.07f
        c == ',' || c == '.' -> 0.11f
        c == ';' || c == ':' -> 0.46f
        c in "′″°" -> 0.74f
        c == ' ' || c == ' ' -> 0f
        else -> EST_CAP
    }

    private fun estBot(c: Char): Float = when {
        c in "gpqyjβγζημξρφχψςşçÇŞQ" -> EST_DSC
        c in "()[]{}" -> 0.2f
        c == '|' -> 0.24f
        c == ',' || c == ';' -> 0.14f
        else -> 0f
    }

    private fun estIc(c: Char): Float = when {
        c == 'f' -> 0.1f
        c in "TVWYFPXUHIJKMNZEDBRSCGL" -> 0.05f
        c in "bdhklt" -> 0.02f
        else -> 0.01f
    }

    private fun glyph(text: String, style: GlyphStyle, sz: Float, char: Boolean): Box {
        val w = adv(text, sz, style)
        val k = ink(text, style)
        val italic = style == GlyphStyle.ITALIC || style == GlyphStyle.BOLD_ITALIC
        val ic = if (italic) (k[2] * sz - w).coerceIn(0f, 0.16f * sz) else 0f
        return Box(w, max(0f, -k[1] * sz), max(0f, k[3] * sz), listOf(DrawOp.Glyph(text, 0f, 0f, sz, style)), emptyList(), ic, char)
    }

    // ------------------------------------------------ satırlar

    fun layoutRoot(root: RowNode): FormulaLayout {
        val box = row(root.items, MathStyle.DISPLAY).withIc()
        val asc = max(box.h, parenTop * size)
        val desc = max(box.d, parenBot * size)
        return FormulaLayout(box.w, asc, desc, box.ops, mergeRegions(box.slots))
    }

    private fun mergeRegions(list: List<SlotRegion>): List<SlotRegion> {
        if (list.size < 2) return list
        val out = ArrayList<SlotRegion>(list)
        var merged = true
        while (merged) {
            merged = false
            loop@ for (a in out.indices) for (b in out.indices) {
                if (a == b) continue
                val p = out[a]
                val q = out[b]
                if (p.name != q.name || q.left < p.left) continue
                val gap = q.left - p.right
                val vOverlap = min(p.bottom, q.bottom) - max(p.top, q.top)
                if (gap <= 0.3f * size && vOverlap > 0f) {
                    out[a] = SlotRegion(p.name, p.left, min(p.top, q.top), max(p.right, q.right), max(p.bottom, q.bottom))
                    out.removeAt(b)
                    merged = true
                    break@loop
                }
            }
        }
        return out
    }

    private fun box(n: Node, st: MathStyle): Box = if (n is RowNode) row(n.items, st) else row(listOf(n), st)

    fun row(items: List<Node>, st: MathStyle): Box = rowFull(items, st).box

    fun rowFull(items: List<Node>, st0: MathStyle): RowResult {
        val nodes = SlotRules.apply(items)
        var st = st0
        val atoms = ArrayList<Atom>(nodes.size)
        for (n in nodes) {
            when (n) {
                is StyleNode -> st = MathStyle(n.level, st.cramped)
                is SpaceNode -> atoms.add(Atom(Box.EMPTY, ORD, ORD, st, n.em * em(st)))
                else -> atoms.add(atom(n, st))
            }
        }
        // İkili işleç yeniden sınıflama (TeX kural 5–6): baştaki/işleç sonrası − tekli olur.
        var prev: Atom? = null
        var count = 0
        var first: Atom? = null
        for (a in atoms) {
            if (a.isSpace) continue
            if (a.left == BIN && (prev == null || prev.right == BIN || prev.right == OP || prev.right == REL ||
                    prev.right == OPEN || prev.right == PUNCT)
            ) { a.left = ORD; a.right = ORD }
            if ((a.left == REL || a.left == CLOSE || a.left == PUNCT) && prev != null && prev.right == BIN) {
                prev.left = ORD; prev.right = ORD
            }
            if (first == null) first = a
            prev = a
            count++
        }
        if (prev != null && prev.right == BIN) { prev.left = ORD; prev.right = ORD }
        if (atoms.size == 1 && count == 1) {
            val a = atoms[0]
            return RowResult(a.box, 1, a.left, a.right)
        }
        val b = BoxBuilder()
        var last: Atom? = null
        for (a in atoms) {
            if (a.isSpace) { b.x += a.space; continue }
            if (last != null) {
                b.x += gap(last.right, a.left, a.st)
                // Uzun esnek ayraç işleç adına (lim, det, log_a) yapışmasın.
                if (last.right == OP && a.left == OPEN && a.box.h > 1.1f * em(a.st)) b.x += 3f / 18f * em(a.st)
            }
            b.append(a.box.withIc())
            last = a
        }
        return RowResult(b.build(), count, first?.left ?: ORD, last?.right ?: ORD)
    }

    private fun gap(l: AtomType, r: AtomType, st: MathStyle): Float {
        val code = SPACING[l.ordinal][r.ordinal]
        if (code == 0 || (code < 0 && st.level >= 2)) return 0f
        return MU[abs(code)] / 18f * em(st)
    }

    private fun atom(n: Node, st: MathStyle): Atom = when (n) {
        is SymNode -> Atom(if (n.pad > 0f) padded(sym(n, st), n.pad * em(st)) else sym(n, st), n.type, n.type, st)
        is ScriptsNode -> scripts(n, st)
        is FracNode -> Atom(frac(n, st), INNER, INNER, st)
        is SqrtNode -> Atom(sqrt(n, st), ORD, ORD, st)
        is AccentNode -> Atom(accent(n, st), ORD, ORD, st)
        is FenceNode -> if (n.binom) Atom(fence(n, st), INNER, INNER, st) else Atom(fence(n, st), OPEN, CLOSE, st)
        is MatrixNode -> {
            val (b, delimited) = matrix(n, st)
            if (delimited) Atom(b, OPEN, CLOSE, st) else Atom(b, ORD, ORD, st)
        }
        is TextNode -> Atom(glyph(n.text, n.style, em(st), false), ORD, ORD, st)
        is SlotNode -> slot(n, st)
        is BigOpNode -> Atom(bigOp(n, st), OP, OP, st)
        is FuncNode -> Atom(glyph(n.name, GlyphStyle.ROMAN, em(st), false), OP, OP, st)
        is DotsNode -> Atom(dots(n, st), INNER, INNER, st)
        is RowNode -> Atom(rowFull(n.items, st).box, ORD, ORD, st)
        is SpaceNode -> Atom(Box.EMPTY, ORD, ORD, st, n.em * em(st))
        is StyleNode -> Atom(Box.EMPTY, ORD, ORD, st, 0f)
    }

    private fun padded(b: Box, pad: Float): Box {
        val out = BoxBuilder()
        out.put(b.withIc(), pad, 0f)
        return out.build(b.withIc().w + 2 * pad)
    }

    private fun sym(n: SymNode, st: MathStyle): Box {
        if (n.text.isEmpty()) return Box.EMPTY
        if (n.text in STROKE_DELIMS) return delim(n.text, 0f, st, false)
        return glyph(n.text, n.style, em(st), true)
    }

    // ------------------------------------------------ yuvalar

    private fun slot(n: SlotNode, st: MathStyle): Atom {
        val r = rowFull(n.body.items, st)
        val em = em(st)
        var b = r.box
        if (b.w <= 0f && b.ops.isEmpty()) b = Box(0.55f * em, xh * em, 0f, emptyList(), emptyList())
        val ops = b.ops.map { if (it is DrawOp.Glyph && it.slot == null) it.copy(slot = n.name) else it }
        val top = -max(b.h, (xh + 0.12f) * em) - 0.05f * em
        val bottom = max(b.d, 0.1f * em) + 0.05f * em
        val region = SlotRegion(n.name, -0.03f * em, top, b.w + b.ic + 0.03f * em, bottom)
        val box = Box(b.w, b.h, b.d, ops, b.slots + region, b.ic, b.char)
        val single = n.body.items.singleOrNull() as? SymNode
        val lt = n.type ?: single?.type ?: if (r.count == 1) r.first else ORD
        val rt = n.type ?: single?.type ?: if (r.count == 1) r.last else ORD
        return Atom(box, lt, rt, st)
    }

    // ------------------------------------------------ indisler

    private fun scripts(n: ScriptsNode, st: MathStyle): Atom {
        val base = n.base
        val inner = ((base as? SlotNode)?.takeIf { it.type == null }?.body?.items?.singleOrNull() as? ScriptsNode)
        if (inner != null && n.sub == null && n.sup != null && n.primes == 0 && inner.sup == null && inner.primes == 0 &&
            inner.sub != null
        ) return slotScripts(base as SlotNode, inner, n.sup!!, st)
        val ba = atom(base, st)
        val lim = when (base) {
            is BigOpNode -> base.limits ?: (st.level == 0 && base.kind in LIMIT_OPS)
            is FuncNode -> base.limits ?: (st.level == 0 && base.limitsDefault)
            else -> false
        }
        val b = if (lim && n.primes == 0 && (n.sup != null || n.sub != null)) limits(ba.box, n.sup, n.sub, st)
        else attach(ba.box, n.sup, n.sub, n.primes, st)
        return Atom(b, ba.left, ba.right, st)
    }

    /** `\slot{v0}{v_0}^2` → v₀² üst üste (TeX'teki v_0^2 gibi); yuva yalnız taban + alt indisi kapsar. */
    private fun slotScripts(slot: SlotNode, inner: ScriptsNode, sup: Node, st: MathStyle): Atom {
        val ba = atom(inner.base, st)
        val baseBox = tag(ba.box, slot.name)
        val subBox = tag(box(inner.sub!!, st.sub()), slot.name)
        val b = attach(baseBox, sup, null, 0, st, subBox)
        val em = em(st)
        val region = SlotRegion(
            slot.name, -0.03f * em, -max(baseBox.h, (xh + 0.12f) * em) - 0.05f * em,
            max(baseBox.w + baseBox.ic, baseBox.w + subBox.w + subBox.ic) + 0.03f * em,
            max(baseBox.d, lastSubShift + subBox.d) + 0.05f * em,
        )
        return Atom(Box(b.w, b.h, b.d, b.ops, b.slots + region, b.ic, false), ba.left, ba.right, st)
    }

    private fun tag(b: Box, name: String): Box =
        Box(b.w, b.h, b.d, b.ops.map { if (it is DrawOp.Glyph && it.slot == null) it.copy(slot = name) else it }, b.slots, b.ic, b.char)

    private var lastSubShift = 0f

    private fun isDegree(n: Node): Boolean {
        val s = if (n is RowNode) n.items.singleOrNull() else n
        return s is SymNode && (s.text == "∘" || s.text == "°")
    }

    private fun attach(b: Box, supN: Node?, subN: Node?, primes: Int, st: MathStyle, subPre: Box? = null): Box {
        val em = em(st)
        val out = BoxBuilder()
        out.put(b, 0f, 0f)
        var sup = supN
        var tail = if (primes > 0) "′".repeat(primes) else ""
        if (sup != null && isDegree(sup)) { tail += "°"; sup = null }
        var supX = b.w + b.ic
        val subX = b.w
        var right = b.w + b.ic
        if (tail.isNotEmpty()) {
            val pb = glyph(tail, GlyphStyle.ROMAN, em, false)
            val dx = supX + (if (primes > 0) 0.03f * em else 0.01f * em)
            // Uzun (karakter olmayan) tabanda üs işareti tabanın tepesine hizalanır: \left(…\right)′
            val raise = if (b.char) 0f else (b.h - 0.04f * em - pb.h).coerceAtLeast(0f)
            out.put(pb, dx, -raise)
            supX = dx + pb.w
            right = supX
        }
        if (sup == null && subN == null && subPre == null) return out.build(right)
        val supSt = st.sup()
        val subSt = st.sub()
        val supB = sup?.let { box(it, supSt) }
        val subB = subPre ?: subN?.let { box(it, subSt) }
        var u = if (b.char) 0f else b.h - SUP_DROP * em(supSt)
        var v = if (b.char) 0f else b.d + SUB_DROP * em(subSt)
        val xhE = xh * em
        if (supB != null) {
            val p = when {
                st.level == 0 && !st.cramped -> SUP1
                st.cramped -> SUP3
                else -> SUP2
            } * em * fs
            u = maxOf(u, p, supB.d + xhE / 4)
        }
        if (subB != null && supB == null) v = maxOf(v, SUB1 * em * fs, subB.h - 0.8f * xhE)
        if (subB != null && supB != null) {
            v = max(v, SUB2 * em * fs)
            val minGap = 0.16f * em
            val g = (u - supB.d) - (subB.h - v)
            if (g < minGap) {
                v += minGap - g
                val psi = 0.8f * xhE - (u - supB.d)
                if (psi > 0f) { u += psi; v -= psi }
            }
        }
        var w = right
        if (supB != null) { out.put(supB, supX, -u); w = max(w, supX + supB.w + supB.ic) }
        if (subB != null) { out.put(subB, subX, v); w = max(w, subX + subB.w + subB.ic) }
        lastSubShift = v
        return out.build(w + SCRIPT_SPACE * em)
    }

    /** Büyük işleç sınırları altta/üstte ortalı (TeX kural 13a). */
    private fun limits(op: Box, supN: Node?, subN: Node?, st: MathStyle): Box {
        val em = em(st)
        val supB = supN?.let { box(it, st.sup()).withIc() }
        val subB = subN?.let { box(it, st.sub()).withIc() }
        val w = maxOf(op.w, supB?.w ?: 0f, subB?.w ?: 0f)
        val out = BoxBuilder()
        out.put(op, (w - op.w) / 2, 0f)
        if (supB != null) {
            val shift = op.h + max(0.111f * em, 0.2f * em - supB.d) + supB.d
            out.put(supB, (w - supB.w) / 2, -shift)
            out.h = max(out.h, shift + supB.h + 0.1f * em)
        }
        if (subB != null) {
            val shift = op.d + max(0.167f * em, 0.6f * em - subB.h) + subB.h
            out.put(subB, (w - subB.w) / 2, shift)
            out.d = max(out.d, shift + subB.d + 0.1f * em)
        }
        return out.build(w)
    }

    // ------------------------------------------------ kesir

    private fun frac(n: FracNode, st0: MathStyle): Box {
        val st = when (n.forced) {
            0 -> MathStyle(0, st0.cramped)
            1 -> MathStyle(1, st0.cramped)
            else -> st0
        }
        val nb = box(n.num, st.num()).withIc()
        val db = box(n.den, st.den()).withIc()
        val em = em(st)
        val t = t(st)
        val a = axis * em
        val display = st.level == 0
        val u: Float
        val v: Float
        if (n.bar) {
            val clr = if (display) 3 * t else t
            val gapN = (if (display) 0.22f else 0.12f) * em
            val gapD = (if (display) 0.18f else 0.1f) * em
            val denCap = cap * em(st.den())
            u = max(a + t / 2 + gapN, a + t / 2 + clr + nb.d)
            v = max(t / 2 - a + gapD + denCap, t / 2 - a + clr + db.h)
        } else {
            val clr = (if (display) 5f else 3f) * t
            u = max(a + clr / 2 + nb.d, a + 0.28f * em)
            v = max(-a + clr / 2 + db.h, cap * em(st.den()) - a + clr / 2)
        }
        val inner = max(nb.w, db.w)
        val ext = if (n.bar) 0.07f * em else 0f
        val pad = if (n.bar) 0.08f * em else 0.02f * em
        val w = inner + 2 * ext + 2 * pad
        val out = BoxBuilder()
        out.put(nb, pad + ext + (inner - nb.w) / 2, -u)
        out.put(db, pad + ext + (inner - db.w) / 2, v)
        if (n.bar) {
            out.op(DrawOp.Rule(pad, -a, w - pad, -a, t))
            out.h = max(out.h, a + t / 2)
        }
        return out.build(w)
    }

    // ------------------------------------------------ kök

    private fun sqrt(n: SqrtNode, st: MathStyle): Box {
        val body = box(n.body, st.cramp()).withIc()
        val em = em(st)
        val t = t(st)
        val phi = if (st.level == 0) xh * em else t
        val psi = t + phi / 4
        val need = body.h + body.d + psi + t
        val total = max(need, (if (st.level == 0) 1.1f else 1.02f) * em)
        val extra = total - need
        val barY = -(body.h + psi + extra / 2 + t / 2)
        val botY = body.d + extra / 2
        val out = BoxBuilder()
        var x0 = 0f
        var idx: Box? = null
        if (n.index != null) {
            val ib = box(n.index, MathStyle(3, false)).withIc()
            val room = 0.3f * em
            x0 = max(0f, ib.w - room)
            idx = ib
        }
        val (ops, sw) = FormulaShapes.radical(x0, barY, botY, em, t)
        ops.forEach { out.op(it) }
        if (idx != null) {
            val bottom = botY - max(0.55f * total, 0.5f * em)
            out.put(idx, x0 + 0.3f * em - idx.w, bottom - idx.d)
        }
        val bx = x0 + sw + 0.06f * em
        out.put(body, bx, 0f)
        val barEnd = bx + body.w + 0.06f * em
        out.op(DrawOp.Rule(x0 + sw - t * 0.35f, barY, barEnd, barY, t))
        out.h = max(out.h, -barY + t / 2 + t)
        out.d = max(out.d, botY + t / 2)
        return out.build(barEnd + 0.05f * em)
    }

    // ------------------------------------------------ aksanlar

    private fun accent(n: AccentNode, st: MathStyle): Box {
        val raw = box(n.body, st.cramp())
        val em = em(st)
        val t = t(st)
        val single = raw.char
        val italicChar = single && raw.ops.firstOrNull().let {
            it is DrawOp.Glyph && (it.style == GlyphStyle.ITALIC || it.style == GlyphStyle.BOLD_ITALIC)
        }
        val body = raw.withIc()
        val skew = if (italicChar) (0.2f * (raw.h - 0.5f * xh * em)).coerceIn(0f, 0.12f * em) else 0f
        val cx = raw.w / 2 + skew
        val out = BoxBuilder()
        out.put(body, 0f, 0f)
        val th = t * 0.9f
        when (n.kind) {
            AccentKind.VEC, AccentKind.LEFT_VEC -> {
                val aw = if (single) max(0.48f * em, raw.w * 0.95f) else body.w
                val y = -(raw.h + 0.2f * em)
                val x0 = cx - aw / 2
                val x1 = cx + aw / 2
                val hx = 0.14f * em
                val hy = 0.09f * em
                out.op(DrawOp.Rule(x0, y, x1, y, th))
                if (n.kind == AccentKind.VEC) out.op(DrawOp.Stroke(listOf(x1 - hx, y - hy, x1, y, x1 - hx, y + hy), th))
                else out.op(DrawOp.Stroke(listOf(x0 + hx, y - hy, x0, y, x0 + hx, y + hy), th))
                out.h = max(out.h, -y + hy + th)
            }
            AccentKind.OVERLINE -> {
                val y = -(raw.h + 3 * t + t / 2)
                out.op(DrawOp.Rule(0.02f * em, y, body.w - 0.02f * em, y, t))
                out.h = max(out.h, -y + t / 2 + t)
            }
            AccentKind.UNDERLINE -> {
                val y = raw.d + 3 * t + t / 2
                out.op(DrawOp.Rule(0.02f * em, y, body.w - 0.02f * em, y, t))
                out.d = max(out.d, y + t / 2 + t)
            }
            AccentKind.BAR -> {
                val bw = if (single) max(0.32f * em, min(raw.w * 0.85f, 0.55f * em)) else body.w
                val y = -(raw.h + 0.14f * em)
                out.op(DrawOp.Rule(cx - bw / 2, y, cx + bw / 2, y, th))
                out.h = max(out.h, -y + th)
            }
            AccentKind.HAT, AccentKind.WIDEHAT -> {
                val hw = if (single && n.kind == AccentKind.HAT) max(0.36f * em, min(raw.w * 0.75f, 0.5f * em)) else body.w
                val hh = if (hw > 0.8f * em) 0.18f * em else 0.14f * em
                val yb = -(raw.h + 0.09f * em)
                out.op(DrawOp.Stroke(listOf(cx - hw / 2, yb, cx, yb - hh, cx + hw / 2, yb), th))
                out.h = max(out.h, -yb + hh + th)
            }
            AccentKind.DOT, AccentKind.DDOT -> {
                val r = 0.055f * em
                val y = -(raw.h + 0.15f * em)
                if (n.kind == AccentKind.DOT) out.op(FormulaShapes.circle(cx, y, r))
                else { out.op(FormulaShapes.circle(cx - 0.11f * em, y, r)); out.op(FormulaShapes.circle(cx + 0.11f * em, y, r)) }
                out.h = max(out.h, -y + r + 0.02f * em)
            }
            AccentKind.TILDE -> {
                val tw = if (single) max(0.4f * em, raw.w * 0.8f) else body.w
                val y = -(raw.h + 0.16f * em)
                val amp = 0.05f * em
                val pts = ArrayList<Float>()
                for (k in 0..16) {
                    val f = k / 16f
                    pts.add(cx - tw / 2 + f * tw)
                    pts.add(y - amp * kotlin.math.sin(f * 2 * Math.PI).toFloat())
                }
                out.op(DrawOp.Stroke(pts, th))
                out.h = max(out.h, -y + amp + th)
            }
        }
        return out.build(body.w)
    }

    // ------------------------------------------------ ayraçlar

    private fun fence(n: FenceNode, st: MathStyle): Box {
        val body = row(n.body.items, st).withIc()
        val em = em(st)
        val a = axis * em
        val delta = max(body.h - a, body.d + a)
        val need = max(2 * delta * 0.901f, 2 * delta - 0.5f * em)
        val out = BoxBuilder()
        out.append(delim(n.left, need, st, n.auto))
        out.append(body)
        out.append(delim(n.right, need, st, n.auto))
        return out.build()
    }

    /** Ayraç: gerekli yükseklik yazı tipinin glifine sığıyorsa glif, değilse eksene ortalı esnek çizim. */
    private fun delim(ch: String, need: Float, st: MathStyle, auto: Boolean): Box {
        val em = em(st)
        val a = axis * em
        if (ch == "." || ch.isEmpty()) return Box(0.1f * em, 0f, 0f, emptyList(), emptyList())
        if (ch in GLYPH_DELIMS) {
            val g = glyph(ch, GlyphStyle.ROMAN, em, false)
            val normal = g.h + g.d
            if (need <= normal * (if (auto) 1.2f else 1.02f)) return g
        }
        val normal = (parenTop + parenBot) * em
        val total = max(need, normal)
        val top = -(a + total / 2)
        val bottom = total / 2 - a
        val w = FormulaShapes.delimiterWidth(ch, total, em)
        return Box(w, -top, bottom, FormulaShapes.delimiter(ch, 0f, top, bottom, em, t(st)), emptyList())
    }

    // ------------------------------------------------ matrisler

    private fun matrix(n: MatrixNode, st: MathStyle): Pair<Box, Boolean> {
        val env = n.env
        val aligned = env in ALIGN_ENVS
        val gathered = env in GATHER_ENVS
        val cases = env in CASES_ENVS
        val small = env == "smallmatrix"
        val cellSt = when {
            aligned || gathered || env == "dcases" -> st
            small -> MathStyle(max(st.level, 2), st.cramped)
            else -> MathStyle(max(st.level, 1), st.cramped)
        }
        val em = em(st)
        val cellEm = em(cellSt)
        val rows = n.rows.map { r ->
            r.mapIndexed { j, c ->
                val items = if (aligned && j % 2 == 1) listOf<Node>(RowNode(emptyList())) + c.items else c.items
                row(items, cellSt).withIc()
            }
        }
        val ncol = max(1, rows.maxOfOrNull { it.size } ?: 1)
        val colW = FloatArray(ncol)
        rows.forEach { r -> r.forEachIndexed { j, b -> colW[j] = max(colW[j], b.w) } }
        val spec = n.colSpec?.filter { it == 'l' || it == 'c' || it == 'r' }
        val align = CharArray(ncol) { j ->
            when {
                cases -> 'l'
                aligned -> if (j % 2 == 0) 'r' else 'l'
                spec != null && j < spec.length -> spec[j]
                else -> 'c'
            }
        }
        val colGap = FloatArray(ncol) { j ->
            when {
                j == ncol - 1 -> 0f
                cases -> 1.0f * em
                aligned -> if (j % 2 == 1) 1.5f * em else 0f
                small -> 0.45f * em
                else -> 0.9f * em
            }
        }
        val strutH = 0.85f * cellEm
        val strutD = 0.35f * cellEm
        val rowGap = when {
            aligned || gathered -> 0.3f * em
            cases -> 0.22f * em
            small -> 0.05f * em
            else -> 0.1f * em
        }
        val rh = rows.map { r -> max(strutH, r.maxOfOrNull { it.h } ?: 0f) }
        val rd = rows.map { r -> max(strutD, r.maxOfOrNull { it.d } ?: 0f) }
        var total = 0f
        rows.indices.forEach { total += rh[it] + rd[it] }
        total += rowGap * (rows.size - 1).coerceAtLeast(0)
        val a = axis * em
        val colX = FloatArray(ncol)
        var cx = 0f
        for (j in 0 until ncol) { colX[j] = cx; cx += colW[j] + colGap[j] }
        val inner = BoxBuilder()
        var y = -(a + total / 2)
        rows.forEachIndexed { i, r ->
            y += rh[i]
            r.forEachIndexed { j, b ->
                val dx = when (align[j]) {
                    'l' -> 0f
                    'r' -> colW[j] - b.w
                    else -> (colW[j] - b.w) / 2
                }
                inner.put(b, colX[j] + dx, y)
            }
            y += rd[i] + rowGap
        }
        inner.h = max(inner.h, a + total / 2)
        inner.d = max(inner.d, total / 2 - a)
        val innerBox = inner.build(cx)
        val delims = when (env) {
            "pmatrix" -> "(" to ")"
            "bmatrix" -> "[" to "]"
            "Bmatrix" -> "{" to "}"
            "vmatrix" -> "|" to "|"
            "Vmatrix" -> "‖" to "‖"
            "cases", "dcases" -> "{" to "."
            "rcases" -> "." to "}"
            else -> null
        } ?: return innerBox to false
        val delta = max(innerBox.h - a, innerBox.d + a)
        val need = max(2 * delta * 0.901f, 2 * delta - 0.5f * em)
        val out = BoxBuilder()
        out.append(delim(delims.first, need, st, false))
        out.x += if (cases) 0.12f * em else 0.06f * em
        out.append(innerBox)
        out.x += if (delims.second == ".") 0f else 0.06f * em
        out.append(delim(delims.second, need, st, false))
        return out.build() to true
    }

    // ------------------------------------------------ büyük işleçler

    private fun bigOp(n: BigOpNode, st: MathStyle): Box {
        val em = em(st)
        val a = axis * em
        if (n.kind in INTEGRALS) return integral(n.kind, st)
        val ch = when (n.kind) {
            "prod" -> "Π"
            "coprod" -> "∐"
            "bigcup" -> "∪"
            "bigcap" -> "∩"
            else -> "Σ"
        }
        val k = when (st.level) { 0 -> 1.6f; 1 -> 1.2f; else -> 1.1f } * (if (ch == "∪" || ch == "∩") 1.1f else 1f)
        val g = glyph(ch, GlyphStyle.ROMAN, em * k, false)
        val dy = (g.h - g.d) / 2 - a
        val out = BoxBuilder()
        out.put(g, 0.04f * em, dy)
        return out.build(g.w + 0.08f * em)
    }

    private fun integral(kind: String, st: MathStyle): Box {
        val em = em(st)
        val a = axis * em
        val hgt = em * when (st.level) { 0 -> 2.05f; 1 -> 1.3f; else -> 1.15f }
        val top = -(a + hgt / 2)
        val count = when (kind) { "iint", "oiint" -> 2; "iiint" -> 3; else -> 1 }
        val wOne = 0.44f * hgt
        val step = 0.2f * hgt + 0.08f * em
        val sb = 0.05f * em
        val maxW = max(0.05f * hgt, 1.4f * t(st))
        val ops = ArrayList<DrawOp>()
        for (k in 0 until count) ops += FormulaShapes.integral(sb + k * step, top, hgt, maxW)
        if (kind == "oint" || kind == "oiint") {
            val c = sb + (count - 1) * step / 2 + 0.22f * hgt
            ops += FormulaShapes.ellipse(c, top + 0.5f * hgt, 0.11f * hgt + (count - 1) * step / 2, 0.1f * hgt, t(st) * 0.9f)
        }
        val inkRight = sb + (count - 1) * step + wOne
        val w = sb + (count - 1) * step + 0.5f * wOne
        return Box(w, a + hgt / 2, hgt / 2 - a, ops, emptyList(), inkRight - w + 0.05f * em, false)
    }

    private fun dots(n: DotsNode, st: MathStyle): Box {
        val em = em(st)
        val r = 0.052f * em
        val side = 0.1f * em
        val out = BoxBuilder()
        val w: Float
        when (n.kind) {
            'v' -> {
                for (k in 0..2) out.op(FormulaShapes.circle(side + r, -(0.1f * em + r + k * 0.3f * em), r))
                out.h = 0.1f * em + 2 * r + 0.6f * em
                w = 2 * side + 2 * r
            }
            'd' -> {
                for (k in 0..2) out.op(FormulaShapes.circle(side + r + k * 0.28f * em, -(0.7f * em - k * 0.28f * em), r))
                out.h = 0.7f * em + r
                w = 2 * side + 2 * r + 0.56f * em
            }
            else -> {
                val y = if (n.kind == 'c') -axis * em else -r - 0.005f * em
                for (k in 0..2) out.op(FormulaShapes.circle(side + r + k * 0.28f * em, y, r))
                out.h = -y + r
                w = 2 * side + 2 * r + 0.56f * em
            }
        }
        return out.build(w)
    }

    companion object {
        const val REF = 100f
        const val EST_XH = 0.52f
        const val EST_CAP = 0.71f
        const val EST_ASC = 0.75f
        const val EST_DSC = 0.22f
        const val EST_AXIS = 0.3f

        const val SUP1 = 0.413f
        const val SUP2 = 0.363f
        const val SUP3 = 0.289f
        const val SUB1 = 0.15f
        const val SUB2 = 0.247f
        const val SUP_DROP = 0.386f
        const val SUB_DROP = 0.05f
        const val SCRIPT_SPACE = 0.05f

        val MU = floatArrayOf(0f, 3f, 4f, 5f)

        // TeX boşluk tablosu: 1 ince, 2 orta, 3 kalın; negatif = yalnız display/text biçeminde.
        val SPACING = arrayOf(
            intArrayOf(0, 1, -2, -3, 0, 0, 0, -1),
            intArrayOf(1, 1, 0, -3, 0, 0, 0, -1),
            intArrayOf(-2, -2, 0, 0, -2, 0, 0, -2),
            intArrayOf(-3, -3, 0, 0, -3, 0, 0, -3),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 1, -2, -3, 0, 0, 0, -1),
            intArrayOf(-1, -1, 0, -1, -1, -1, -1, -1),
            intArrayOf(-1, 1, -2, -3, -1, 0, -1, -1),
        )

        val LIMIT_OPS = setOf("sum", "prod", "coprod", "bigcup", "bigcap")
        val INTEGRALS = setOf("int", "iint", "iiint", "oint", "oiint")
        val GLYPH_DELIMS = setOf("(", ")", "[", "]", "{", "}", "|")
        val STROKE_DELIMS = setOf("⟨", "⟩", "⌊", "⌋", "⌈", "⌉", "‖")
        val ALIGN_ENVS = setOf("aligned", "align", "split", "eqnarray", "alignat", "alignedat", "flalign")
        val GATHER_ENVS = setOf("gathered", "gather", "multline")
        val CASES_ENVS = setOf("cases", "dcases", "rcases")
    }
}

// ---------------------------------------------------------------- yuva kuralları

/**
 * Yuvaya sayı/ifade yazıldığında okunaklılığı koruyan dönüşümler (hem dizgi hem düz metin kullanır):
 * `x + \slot{b}{-3}` → x − 3 (işaret birleşir), `\slot{a}{-3}^2` → (−3)², `2\slot{a}{-1}` → 2(−1),
 * `\slot{m}{2}\slot{a}{3}` → 2·3 (yan yana iki sayı çarpım noktasıyla ayrılır).
 */
internal object SlotRules {

    private fun slotOf(n: Node): SlotNode? = when (n) {
        is SlotNode -> n
        is ScriptsNode -> n.base as? SlotNode
        else -> null
    }

    fun apply(items: List<Node>): List<Node> {
        if (items.none { slotOf(it)?.type == null && slotOf(it) != null }) return items
        val out = ArrayList(items)
        var k = 0
        while (k < out.size) {
            val n = out[k]
            val slot = slotOf(n)
            if (slot == null || slot.type != null) { k++; continue }
            val body = slot.body.items
            val scripted = n is ScriptsNode && (n.sup != null || n.primes > 0)
            val pi = prevIndex(out, k)
            val prev = pi?.let { out[it] }
            val next = nextIndex(out, k)?.let { out[it] }
            val lead = leadingSign(body)
            var newBody: List<Node>? = null
            if (lead != null && body.size > 1) {
                if (!scripted && prev is SymNode && (prev.text == "+" || prev.text == "−")) {
                    val merged = if (prev.text == lead) "+" else "−"
                    newBody = body.subList(1, body.size)
                    if (merged == "+" && isUnary(out, pi!!)) {
                        out.removeAt(pi)
                        k--
                    } else {
                        out[pi!!] = SlotNode(slot.name, RowNode(listOf(SymNode(merged, BIN, GlyphStyle.ROMAN))), BIN)
                    }
                } else if (scripted || ordLike(prev, true)) {
                    newBody = listOf(paren(body))
                }
            } else if (hasInnerOperator(body) && (scripted || ordLike(prev, true) || ordLikeNext(next))) {
                newBody = listOf(paren(body))
            } else if (startsWithDigit(body) && ordLike(prev, false)) {
                out.add(k, SymNode("·", BIN, GlyphStyle.ROMAN, SymNode.K_AUTO_DOT))
                k++
            }
            if (newBody != null) {
                val ns = SlotNode(slot.name, RowNode(newBody))
                out[k] = if (n is ScriptsNode) ScriptsNode(ns, n.sup, n.sub, n.primes) else ns
            }
            if (next != null && endsWithDigit(slot.body.items) && startsWithDigit(listOf(next))) {
                out.add(k + 1, SymNode("·", BIN, GlyphStyle.ROMAN, SymNode.K_AUTO_DOT))
                k++
            }
            k++
        }
        return out
    }

    private fun paren(body: List<Node>) = FenceNode("(", RowNode(body), ")", auto = true)

    private fun prevIndex(l: List<Node>, k: Int): Int? {
        var j = k - 1
        while (j >= 0 && (l[j] is SpaceNode || l[j] is StyleNode)) j--
        return if (j >= 0) j else null
    }

    private fun nextIndex(l: List<Node>, k: Int): Int? {
        var j = k + 1
        while (j < l.size && (l[j] is SpaceNode || l[j] is StyleNode)) j++
        return if (j < l.size) j else null
    }

    private fun leadingSign(body: List<Node>): String? {
        val f = body.firstOrNull() as? SymNode ?: return null
        return if (f.text == "−" || f.text == "+") f.text else null
    }

    private fun isUnary(l: List<Node>, idx: Int): Boolean {
        val p = prevIndex(l, idx)?.let { l[it] } ?: return true
        return when (p) {
            is SymNode -> p.type == BIN || p.type == REL || p.type == OPEN || p.type == PUNCT
            is SlotNode -> p.type == BIN
            is BigOpNode, is FuncNode -> true
            else -> false
        }
    }

    private fun hasInnerOperator(body: List<Node>): Boolean =
        body.drop(1).any { it is SymNode && (it.type == BIN || it.type == REL) }

    private fun ordLike(n: Node?, forParens: Boolean): Boolean = when (n) {
        null -> false
        is SymNode -> n.type == ORD || n.type == CLOSE
        is ScriptsNode -> ordLike(n.base, forParens)
        is FracNode, is SqrtNode, is FenceNode, is AccentNode, is MatrixNode -> true
        is SlotNode -> n.type == null
        is RowNode -> n.items.isNotEmpty()
        is FuncNode, is BigOpNode -> forParens
        else -> false
    }

    private fun ordLikeNext(n: Node?): Boolean = when (n) {
        null -> false
        is SymNode -> n.type == ORD || n.type == OPEN
        is ScriptsNode -> ordLikeNext(n.base)
        is FracNode, is SqrtNode, is FenceNode, is AccentNode, is MatrixNode, is FuncNode -> true
        is SlotNode -> n.type == null
        is RowNode -> n.items.isNotEmpty()
        else -> false
    }

    private fun startsWithDigit(body: List<Node>): Boolean = when (val f = body.firstOrNull()) {
        is SymNode -> f.kind == SymNode.K_DIGITS
        is ScriptsNode -> f.base is SymNode && (f.base as SymNode).kind == SymNode.K_DIGITS
        is SlotNode -> f.type == null && startsWithDigit(f.body.items)
        else -> false
    }

    private fun endsWithDigit(body: List<Node>): Boolean {
        val l = body.lastOrNull()
        return l is SymNode && l.kind == SymNode.K_DIGITS
    }
}
