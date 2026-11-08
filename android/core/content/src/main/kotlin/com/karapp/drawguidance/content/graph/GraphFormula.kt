package com.karapp.drawguidance.content.graph

import com.karapp.drawguidance.content.math.AngleMode
import com.karapp.drawguidance.content.math.MNode
import com.karapp.drawguidance.content.math.MRelation
import com.karapp.drawguidance.content.math.MathError
import com.karapp.drawguidance.content.math.MathException
import com.karapp.drawguidance.content.math.MathExpr
import com.karapp.drawguidance.content.math.Rel
import kotlin.math.abs
import kotlin.math.round

/**
 * v45: her grafik türünde formülün anlamı.
 *
 * - FUNCTION, AXES, COORD_PLANE: y = f(x) eğrisi ya da kapalı denklem (x² + y² = 9); ';' ile birden çok eğri.
 * - AXES_3D: z = f(x, y) yüzeyi.
 * - NUMBER_LINE: nokta(lar) ya da çözüm kümesi: "x = 3", "x = -1; x = 2", "x > 2", "-1 ≤ x < 4", "x² < 4", "x ≠ 0".
 * - BAR, LINE, HISTOGRAM, PIE: y = f(x) x = 1, 2, … n'de örneklenir (öğe değerleri); SCATTER: (x, f(x)) noktaları.
 * - UNIT_CIRCLE: formülü sabittir (x² + y² = 1), formül girilmez.
 */
object GraphFormula {

    enum class Meaning { CURVE, SURFACE, SET, SAMPLED, NONE }

    fun meaning(kind: GraphKind): Meaning = when (kind) {
        GraphKind.FUNCTION, GraphKind.AXES, GraphKind.COORD_PLANE -> Meaning.CURVE
        GraphKind.AXES_3D -> Meaning.SURFACE
        GraphKind.NUMBER_LINE -> Meaning.SET
        GraphKind.BAR, GraphKind.LINE, GraphKind.HISTOGRAM, GraphKind.PIE, GraphKind.SCATTER -> Meaning.SAMPLED
        GraphKind.UNIT_CIRCLE -> Meaning.NONE
    }

    /** Formül girilebilir mi (birim çemberin formülü sabittir). */
    fun accepts(kind: GraphKind): Boolean = meaning(kind) != Meaning.NONE

    /** Örnek yazım (giriş alanının ipucu). */
    fun example(kind: GraphKind): String = when (meaning(kind)) {
        Meaning.CURVE -> "y = x^2 - 2; y = 2x + 1"
        Meaning.SURFACE -> "z = x^2 - y^2"
        Meaning.SET -> "-1 ≤ x < 3"
        Meaning.SAMPLED -> "y = 2x + 1"
        Meaning.NONE -> ""
    }

    /** En dış düzeyde ';' (ya da satır sonu) ile ayrılmış parçalar (parantez içindeki ';' bölmez). */
    fun parts(formula: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        val cur = StringBuilder()
        for (ch in formula) {
            when (ch) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth = (depth - 1).coerceAtLeast(0)
            }
            if ((ch == ';' || ch == '\n') && depth == 0) {
                if (cur.isNotBlank()) out += cur.toString().trim()
                cur.clear()
            } else cur.append(ch)
        }
        if (cur.isNotBlank()) out += cur.toString().trim()
        return out
    }

    /** En çok eğri sayısı (fazlası çizilmez). */
    const val MAX_CURVES = 4

    private fun mode(deg: Boolean) = if (deg) AngleMode.DEG else AngleMode.RAD

    /**
     * Formülün hatası (null: geçerli; boş formül de geçerli = formül yok). Hata konumu her parçanın kendi metnine
     * göredir; [MathError.detail] bilinmeyen adı taşır.
     */
    fun error(kind: GraphKind, formula: String): MathError? {
        if (formula.isBlank()) return null
        if (!accepts(kind)) return MathError(MathError.Code.UNEXPECTED, 0, "")
        val list = parts(formula)
        if (list.isEmpty()) return MathError(MathError.Code.EMPTY, 0)
        return when (meaning(kind)) {
            Meaning.CURVE -> list.firstNotNullOfOrNull { Expression.error(it, allowY = true) }
            Meaning.SURFACE -> if (list.size > 1) MathError(MathError.Code.RELATION) else surfaceError(list[0])
            Meaning.SAMPLED -> if (list.size > 1) MathError(MathError.Code.RELATION) else Expression.error(list[0], allowY = false)?.let { e ->
                // örneklenen grafikte kapalı denklem olmaz
                e
            } ?: if (Expression.isImplicit(list[0])) MathError(MathError.Code.RELATION) else null
            Meaning.SET -> list.firstNotNullOfOrNull { setError(it) }
            Meaning.NONE -> null
        }
    }

    private fun surfaceError(text: String): MathError? {
        if (Expression.isImplicit(text)) return MathError(MathError.Code.RELATION)
        val (node, err) = MathExpr.tryParse(Expression.body(text).lowercase())
        if (err != null) return err
        node!!.variables().firstOrNull { it != "x" && it != "y" }?.let { return MathError(MathError.Code.UNKNOWN_NAME, -1, it) }
        return null
    }

    private fun setError(text: String): MathError? {
        val (rel, err) = MathExpr.tryParseRelation(text.lowercase())
        if (err != null) return err
        rel!!.sides.flatMap { it.variables() }.firstOrNull { it != "x" }?.let { return MathError(MathError.Code.UNKNOWN_NAME, -1, it) }
        // yalın sayı: "3" → x = 3 noktası
        return null
    }

    // ---------------------------------------------------------------- örnekleme (sütun, çizgi, pasta, nokta)

    /**
     * Örneklenen grafik verisi: x = 1..n için f(x) (SCATTER'da x, f(x) çiftleri). Formül geçersizse ya da hiç sonlu
     * değer yoksa null. PIE'de negatif değerler 0 olur. Tanımsız noktalar 0 (SCATTER'da atlanır).
     */
    fun sampled(p: GraphParams, n: Int): List<Float>? {
        if (meaning(p.kind) != Meaning.SAMPLED || p.formula.isBlank()) return null
        if (error(p.kind, p.formula) != null) return null
        val f = Expression.parse(parts(p.formula)[0], mode(p.angleDeg)) ?: return null
        val out = ArrayList<Float>()
        var finite = 0
        for (i in 1..n.coerceIn(1, 64)) {
            val x = i.toDouble()
            val y = f.eval(x, 0.0)
            val ok = y.isFinite() && abs(y) < 1e7
            if (ok) finite++
            val v = if (ok) (round(y * 1000) / 1000).toFloat() else 0f
            if (p.kind == GraphKind.SCATTER) {
                if (!ok) continue
                out += x.toFloat(); out += v
            } else out += if (p.kind == GraphKind.PIE) v.coerceAtLeast(0f) else v
        }
        return if (finite == 0) null else out
    }

    // ---------------------------------------------------------------- sayı doğrusu: çözüm kümesi

    /** Çözüm kümesinin bir aralığı: uçlar dahil mi; sonsuza gidiyorsa uç değeri aralık sınırındadır. */
    data class Interval(val a: Double, val b: Double, val aClosed: Boolean, val bClosed: Boolean, val aInf: Boolean, val bInf: Boolean)

    /** [lo]..[hi] görünür aralığındaki çözüm kümesi: ayrık noktalar ve aralıklar. */
    data class SolutionSet(val points: List<Double>, val intervals: List<Interval>) {
        val isEmpty get() = points.isEmpty() && intervals.isEmpty()
    }

    /** Bir parçanın (örn. "x > 2") görünür aralıktaki çözüm kümesi; geçersizse null. */
    fun solve(text: String, lo: Double, hi: Double, deg: Boolean = false): SolutionSet? {
        if (setError(text) != null) return null
        val rel = MathExpr.parseRelation(text.lowercase())
        val m = mode(deg)
        // yalın ifade: sayı → nokta; x'li ifade → ifade = 0'ın kökleri
        val r = if (!rel.isRelation) {
            if (rel.sides[0].variables().isEmpty()) {
                val v = try { MathExpr.evalStrict(rel.sides[0], emptyMap(), m) } catch (_: MathException) { return null }
                return SolutionSet(listOf(v), emptyList())
            }
            MRelation(listOf(rel.sides[0], MNode.Num("0")), listOf(Rel.EQ))
        } else rel
        val fs = r.sides.map { side -> MathExpr.compile(side, listOf("x"), m) }
        fun value(k: Int, x: Double) = fs[k](doubleArrayOf(x))
        fun truth(x: Double): Boolean {
            for (k in r.ops.indices) {
                val a = value(k, x); val b = value(k + 1, x)
                if (!a.isFinite() || !b.isFinite()) return false
                if (!r.ops[k].holds(a, b, 1e-9)) return false
            }
            return true
        }
        // yalnız eşitlik(ler): kökler
        if (r.ops.all { it == Rel.EQ }) {
            val g = { x: Double -> value(0, x) - value(1, x) }
            return SolutionSet(roots(g, lo, hi).filter { truth(it) || r.ops.size == 1 }, emptyList())
        }
        // eşitsizlik: sınırlar = taraf farklarının kökleri ve tanımsızlık geçişleri
        val n = 2000
        val xs = DoubleArray(n + 1) { lo + (hi - lo) * it / n }
        val t = BooleanArray(n + 1) { truth(xs[it]) }
        val points = ArrayList<Double>()
        val intervals = ArrayList<Interval>()
        var i = 0
        while (i <= n) {
            if (!t[i]) { i++; continue }
            var j = i
            while (j < n && t[j + 1]) j++
            // [xs[i], xs[j]] doğru; uçlar ikiye bölmeyle inceltilir
            val a = if (i == 0) lo else edge(xs[i - 1], xs[i], ::truth)
            val b = if (j == n) hi else edge(xs[j + 1], xs[j], ::truth)
            if (b - a < (hi - lo) * 1e-6) {
                points += snap(a)
            } else {
                val aInf = i == 0
                val bInf = j == n
                val sa = snap(a); val sb = snap(b)
                intervals += Interval(sa, sb, aInf || truth(sa), bInf || truth(sb), aInf, bInf)
            }
            i = j + 1
        }
        // tek noktalık doğrular (x ≤ 0 ∧ x ≥ 0 gibi) örneklemeye takılmaz: eşitlikli uçlarda aranır
        if (r.ops.any { it == Rel.LE || it == Rel.GE }) {
            for (k in r.ops.indices) {
                val g = { x: Double -> value(k, x) - value(k + 1, x) }
                for (root in roots(g, lo, hi)) {
                    val s = snap(root)
                    if (truth(s) && intervals.none { s >= it.a - 1e-9 && s <= it.b + 1e-9 } && points.none { abs(it - s) < 1e-9 }) points += s
                }
            }
        }
        return SolutionSet(points.sorted(), intervals)
    }

    /** Doğru ([inside]) ile yanlış ([outside]) arasındaki geçiş noktası. */
    private fun edge(outside: Double, inside: Double, truth: (Double) -> Boolean): Double {
        var a = outside; var b = inside
        repeat(60) {
            val m = 0.5 * (a + b)
            if (truth(m)) b = m else a = m
        }
        return 0.5 * (a + b)
    }

    /** g'nin [lo, hi]'deki kökleri (işaret değişimi ve dokunma). */
    private fun roots(g: (Double) -> Double, lo: Double, hi: Double): List<Double> {
        val n = 4000
        val out = ArrayList<Double>()
        var px = lo
        var pv = g(lo)
        if (pv == 0.0) out += lo
        for (k in 1..n) {
            val x = lo + (hi - lo) * k / n
            val v = g(x)
            if (v == 0.0) { out += x }
            else if (pv.isFinite() && v.isFinite() && pv != 0.0 && (pv < 0) != (v < 0)) {
                var a = px; var b = x; var fa = pv
                repeat(80) {
                    val m = 0.5 * (a + b)
                    val fm = g(m)
                    if (!fm.isFinite()) return@repeat
                    if ((fm < 0) == (fa < 0)) { a = m; fa = fm } else b = m
                }
                val root = 0.5 * (a + b)
                // asimptot (1/x) değil gerçek kök: değer küçük
                if (abs(g(root)) < 1e-6 * (1 + abs(pv) + abs(v))) out += root
            }
            px = x; pv = v
        }
        // çift katlı kök (x² = 0): yerel en küçük |g| ≈ 0
        val step = (hi - lo) / n
        for (k in 1 until n) {
            val x = lo + step * k
            val a = abs(g(x - step)); val b = abs(g(x)); val c = abs(g(x + step))
            if (b <= a && b <= c && b < 1e-3 * step) {
                var l = x - step; var r = x + step
                repeat(80) {
                    val m1 = l + (r - l) / 3; val m2 = r - (r - l) / 3
                    if (abs(g(m1)) < abs(g(m2))) r = m2 else l = m1
                }
                val m = 0.5 * (l + r)
                if (abs(g(m)) < 1e-9) out += m
            }
        }
        val sorted = out.map { snap(it) }.sorted()
        val dedup = ArrayList<Double>()
        for (v in sorted) if (dedup.isEmpty() || abs(dedup.last() - v) > 1e-6 * (1 + abs(v))) dedup += v
        return dedup
    }

    /** Küçük yuvarlama hatalarını temizler (1.9999999 → 2, 0.333333334 → 1/3 olarak kalır). */
    internal fun snap(v: Double): Double {
        for (d in intArrayOf(1, 2, 3, 4, 5, 6, 8, 10, 100, 1000)) {
            val r = round(v * d) / d
            if (abs(r - v) < 1e-7) return r + 0.0
        }
        return v
    }

    /** Sayı doğrusundaki formül yazısı (güzel yazım). */
    fun label(text: String): String {
        val (rel, err) = MathExpr.tryParseRelation(text.lowercase())
        if (err != null || rel == null) return text
        if (!rel.isRelation && rel.sides[0].variables().isEmpty()) return "x = " + MathExpr.pretty(rel.sides[0])
        return MathExpr.pretty(rel)
    }
}
