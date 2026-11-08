package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.math.AngleMode
import com.karapp.drawguidance.content.math.MNode
import com.karapp.drawguidance.content.math.MathError
import com.karapp.drawguidance.content.math.MathException
import com.karapp.drawguidance.content.math.MathExpr
import com.karapp.drawguidance.content.math.Rel
import com.karapp.drawguidance.content.vector.Affine
import com.karapp.drawguidance.content.vector.VPath
import com.karapp.drawguidance.content.vector.VectorBuilder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/*
 * v45 parametreli (formülle ve tutamakla düzenlenen) fen ve geometri şekilleri.
 *
 * Şeklin değerleri şekil elemanında saklanır (ShapeSpec.params: anahtar → metin). Model her değer kümesinden çizimi,
 * köşedeki formül yazısını ("formula" etiketli) ve sürüklenebilir tutamakları üretir. Kullanıcı:
 * - değerleri tek tek değiştirebilir ([ParamModel.withValue]),
 * - formül alanına yazabilir ([ParamModel.apply]): "v = 20; θ = 45°" atamaları, türetilmiş büyüklük ("di = 15",
 *   "T = 2": ilgili giriş değeri çözülür) ya da eğri şekillerinde eğrinin kendisi ("y = 2x² − 3": model değerleri
 *   eğriye uydurulur),
 * - tutamakları sürükleyebilir ([ParamModel.drag]).
 * Üçü de aynı değerleri değiştirir; çizim, formül ve tutamaklar hep tutarlıdır.
 */

/** Değerin türü. ANGLE derece cinsindendir. TEXT serbest metin (ör. molekül formülü). */
enum class ParamKind { NUMBER, INT, ANGLE, TEXT }

/**
 * Değer tanımı: [key] kayıt anahtarı, [symbol] formüldeki adı ("θ", "v", "do"), [title] Türkçe adı (uygulama
 * çevirir), [unit] birim ("m/s", "°", "cm").
 */
class ParamDef(
    val key: String,
    val symbol: String,
    val title: String,
    val kind: ParamKind = ParamKind.NUMBER,
    val default: Double = 0.0,
    val min: Double = -1e9,
    val max: Double = 1e9,
    val step: Double = 1.0,
    val unit: String = "",
    val defaultText: String = "",
    /** Yalnız formülle değişir (alanlarda sayı kutusu gösterilmez; ör. yer çekimi g). */
    val advanced: Boolean = false,
    /** TEXT değerinin seçenekleri (uygulama düğme olarak gösterir; ör. molekül görünümü "structural", "3d"). */
    val choices: List<String> = emptyList(),
)

/** Türetilmiş büyüklük (ör. menzil R, görüntü uzaklığı di): yazılırsa [solveFor] girişi çözülür. */
class Derived(
    val symbol: String,
    val title: String,
    val unit: String = "",
    val solveFor: String? = null,
    val value: (ParamValues) -> Double,
)

/** Tutamak: [key] tanımı ve kutudaki yeri (0..1, y aşağı). */
data class ParamHandle(val key: String, val x: Float, val y: Float)

/** Formül uygulamasının hatası. [math] ayrıştırma hatasıysa ayrıntısı. */
data class ParamError(val code: Code, val detail: String = "", val math: MathError? = null) {
    enum class Code {
        /** Yazım hatası ([math] dolu). */
        SYNTAX,
        /** Bilinmeyen ad ([detail]). */
        UNKNOWN_SYMBOL,
        /** Değer aralık dışında ([detail] = sembol). */
        OUT_OF_RANGE,
        /** Yazılan eğri/bağıntı bu şekle uymuyor. */
        NO_MATCH,
        /** Değer hesaplanamadı (sıfıra bölme, tanımsız). */
        NOT_NUMBER,
        /** Kimya: formül okunamadı ya da değerlik kurallarına uymuyor ([detail] açıklama kodu). */
        CHEMISTRY,
    }
}

/** Uygulama sonucu: yeni değerler ya da hata (hata varsa şekil değişmez, son geçerli çizim kalır). */
sealed class ParamApply {
    data class Ok(val params: Map<String, String>) : ParamApply()
    data class Failed(val error: ParamError) : ParamApply()
}

/** Bir şeklin değerleri (eksikler varsayılan). */
class ParamValues(val model: ParamModel, val raw: Map<String, String>) {
    private val cache = HashMap<String, Double>()

    fun num(key: String): Double = cache.getOrPut(key) {
        val def = model.def(key) ?: return@getOrPut 0.0
        val v = raw[key]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: def.default
        val c = v.coerceIn(def.min, def.max)
        if (def.kind == ParamKind.INT) round(c) else c
    }

    fun f(key: String): Float = num(key).toFloat()

    fun text(key: String): String = raw[key]?.takeIf { it.isNotBlank() } ?: model.def(key)?.defaultText ?: ""

    operator fun get(key: String): Double = num(key)

    /** Formüllerde sembol → değer (açılar derece). */
    fun symbols(): Map<String, Double> {
        val m = HashMap<String, Double>()
        model.defs.filter { it.kind != ParamKind.TEXT }.forEach { m[it.symbol] = num(it.key) }
        return m
    }
}

/**
 * Parametreli şekil modeli. Çizim [w]×[h] sanal birimlik eşit ölçekli tuvalde yapılır (bkz. [sci]); tutamaklar da bu
 * birimdedir ve birim kutuya çevrilir.
 */
abstract class ParamModel(
    val id: String,
    val title: String,
    val w: Float,
    val h: Float,
    val fit: Fit = Fit.BOX,
    val tags: List<String> = emptyList(),
) {
    abstract val defs: List<ParamDef>
    open val derived: List<Derived> = emptyList()

    /** Eğri şekillerinde eğri değişkeni (ör. "y", "F", "V") ve bağımsız değişken ("x", "I", "t"). */
    open val curveVar: String? = null
    open val curveArg: String = "x"

    /** Eğri y = f(x) (eğri şekli değilse null). */
    open fun curve(v: ParamValues): ((Double) -> Double)? = null

    /** Yazılan eğriye uyan değerler (uymuyorsa null). */
    open fun fit(f: (Double) -> Double, v: ParamValues): Map<String, Double>? = null

    /** Köşedeki formül yazısının satırları (ilk satır formül, sonrakiler değerler). */
    abstract fun label(v: ParamValues): List<String>

    /** Çizim (sanal birimlerde). */
    internal abstract fun draw(b: VectorBuilder, lb: Lb, v: ParamValues)

    /** Tutamaklar (sanal birimlerde). */
    open fun handles(v: ParamValues): List<ParamHandle> = emptyList()

    /** Tutamak [key] sanal ([x], [y]) noktasına sürüklenince yeni değerler (anahtar → sayı); değişmezse null. */
    open fun drag(v: ParamValues, key: String, x: Float, y: Float): Map<String, Double>? = null

    /** Formül yazısının köşesi: sol üst (false) ya da sağ üst (true). */
    open val labelRight: Boolean = false

    /** Yazı boyu (sanal birim). */
    open val labelSize: Float = 0.075f

    fun def(key: String): ParamDef? = defs.firstOrNull { it.key == key }

    fun values(raw: Map<String, String>?): ParamValues = ParamValues(this, raw.orEmpty())

    /** Kütüphane şekli (varsayılan değerlerle çizilir). */
    val shape: LibShape by lazy {
        LibShape(id, title, fit, w / h, tags, param = this) { render(this, values(null)) }
    }

    /** [raw] değerleriyle birim kutudaki çizim. */
    fun paths(raw: Map<String, String>?, resolution: Float = 512f): List<VPath> =
        VectorBuilder(resolution).apply { render(this, values(raw)) }.paths

    private fun render(b: VectorBuilder, v: ParamValues) {
        b.withTransform(Affine.scale(1f / w, 1f / h)) {
            val lb = Lb(this, w, h)
            // v45 görünmez kutu işareti (kalınlık 0, açık iki kısa çizgi; dolgu almaz): çizimin sınırları değerlerden
            // bağımsız olarak hep modelin kutusudur. Böylece yeniden kurulan şekil kaymaz/küçülmez ve tutamaklar
            // oranı korunan (kare) şekillerde de doğru yerde durur.
            line(0f, 0f, FRAME_MARK, 0f).width(0f).tag(FRAME_TAG)
            line(w - FRAME_MARK, h, w, h).width(0f).tag(FRAME_TAG)
            draw(this, lb, v)
            drawLabel(this, lb, v)
        }
    }

    private fun drawLabel(b: VectorBuilder, lb: Lb, v: ParamValues) {
        val lines = label(v).filter { it.isNotBlank() }
        val size = labelSize
        lines.forEachIndexed { i, s ->
            val y = 0.02f + i * size * 1.25f
            val sz = if (i == 0) size else size * 0.85f
            val part = if (labelRight) lb.r(w - 0.02f, y + sz * 0.52f, s, sz, LABEL_COLOR) else lb.t(0.02f, y, s, sz, LABEL_COLOR)
            part.tag(if (i == 0) "formula" else "formula:$i")
        }
    }

    /** Tutamaklar birim kutuda. */
    fun unitHandles(raw: Map<String, String>?): List<ParamHandle> =
        handles(values(raw)).map { ParamHandle(it.key, it.x / w, it.y / h) }

    /** Birim kutudaki noktaya sürükleme: yeni değerler (değişmezse null). */
    fun dragUnit(raw: Map<String, String>?, key: String, ux: Float, uy: Float): Map<String, String>? {
        val v = values(raw)
        val out = drag(v, key, ux * w, uy * h) ?: return null
        var next = raw.orEmpty()
        for ((k, value) in out) next = withValue(next, k, value)
        if (invalid(values(next)) != null) return null
        return if (next == raw.orEmpty()) null else next
    }

    /** Tek değeri değiştirir (aralığa sıkıştırılır, tamsayıya yuvarlanır). */
    fun withValue(raw: Map<String, String>?, key: String, value: Double): Map<String, String> {
        val d = def(key) ?: return raw.orEmpty()
        if (!value.isFinite()) return raw.orEmpty()
        val c = value.coerceIn(d.min, d.max)
        val r = if (d.kind == ParamKind.INT) round(c) else c
        return raw.orEmpty() + (key to store(r))
    }

    /**
     * Değerler birlikte geçerli mi? Geçersizse sorunlu sembol (ör. üçgende A + B ≥ 180°), geçerliyse null. Alan,
     * formül ve tutamak değişiklikleri geçersiz sonuç veriyorsa uygulanmaz.
     */
    open fun invalid(v: ParamValues): String? = null

    /** Alan değişikliği: [withValue] ama birlikte geçersizse null (şekil değişmez). */
    fun setValue(raw: Map<String, String>?, key: String, value: Double): Map<String, String>? {
        val next = withValue(raw, key, value)
        return if (invalid(values(next)) != null) null else next
    }

    fun withText(raw: Map<String, String>?, key: String, text: String): Map<String, String> = raw.orEmpty() + (key to text)

    /** Formül alanında gösterilen (yazılabilir) metin. */
    open fun editText(raw: Map<String, String>?): String {
        val v = values(raw)
        curve(v)?.let { _ -> curveText(v)?.let { return it } }
        return defs.filter { it.kind != ParamKind.TEXT }.joinToString("; ") { d -> "${d.symbol} = ${num(v[d.key])}${if (d.kind == ParamKind.ANGLE) "°" else ""}" }
    }

    /** Eğri şekillerinde eğrinin sayılarla yazımı ("y = 2x² − 3"); yoksa null. */
    open fun curveText(v: ParamValues): String? = null

    /** Türetilmiş büyüklüklerin değerleri. */
    fun derivedValues(raw: Map<String, String>?): Map<String, Double> {
        val v = values(raw)
        return derived.associate { it.symbol to it.value(v) }
    }

    /** Çok harfli semboller (ayrıştırıcıya ad olarak verilir). */
    private val names: Set<String> by lazy {
        (defs.map { it.symbol } + derived.map { it.symbol }).filter { it.length > 1 && !it.contains('_') }.toSet()
    }

    /**
     * Formül alanına yazılanı uygular: ';' ile ayrılmış atamalar, türetilmiş büyüklükler ya da eğri. Hata varsa
     * değerler değişmez.
     */
    open fun apply(raw: Map<String, String>?, text: String): ParamApply {
        var cur = raw.orEmpty()
        val parts = text.split(';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ParamApply.Failed(ParamError(ParamError.Code.SYNTAX, math = MathError(MathError.Code.EMPTY)))
        for (part in parts) {
            val res = applyPart(cur, part)
            when (res) {
                is ParamApply.Ok -> cur = res.params
                is ParamApply.Failed -> return res
            }
        }
        invalid(values(cur))?.let { return failed(ParamError.Code.OUT_OF_RANGE, it) }
        return ParamApply.Ok(cur)
    }

    private fun failed(code: ParamError.Code, detail: String = "", math: MathError? = null) = ParamApply.Failed(ParamError(code, detail, math))

    private fun applyPart(raw: Map<String, String>, part: String): ParamApply {
        val rel = try { MathExpr.parseRelation(part, names) } catch (e: MathException) { return failed(ParamError.Code.SYNTAX, math = e.error) }
        val v = values(raw)
        val known = v.symbols() + derived.associate { it.symbol to it.value(v) }
        val cv = curveVar
        // eğri şeklinde yalın ifade: "2x² − 3" = eğri
        val sides = if (!rel.isRelation && cv != null) listOf(MNode.Var(cv), rel.sides[0]) else rel.sides
        if (sides.size != 2 || (rel.isRelation && (rel.ops.size != 1 || rel.ops[0] != Rel.EQ))) return failed(ParamError.Code.NO_MATCH)
        val lhs = sides[0]
        val rhs = sides[1]
        fun evalRhs(node: MNode, extra: Map<String, Double> = emptyMap()): Double {
            val unknown = node.variables().filter { it !in known && it !in extra }
            if (unknown.isNotEmpty()) throw UnknownSymbol(unknown.first())
            return MathExpr.evalStrict(node, known + extra, AngleMode.DEG)
        }
        try {
            if (lhs is MNode.Var) {
                val d = defs.firstOrNull { it.symbol == lhs.name && it.kind != ParamKind.TEXT }
                if (d != null && cv != lhs.name) {
                    val value = evalRhs(rhs)
                    if (value < d.min - 1e-9 || value > d.max + 1e-9) return failed(ParamError.Code.OUT_OF_RANGE, d.symbol)
                    return ParamApply.Ok(withValue(raw, d.key, value))
                }
                val dv = derived.firstOrNull { it.symbol == lhs.name }
                if (dv != null && (cv != lhs.name || curveArg !in rhs.variables())) {
                    val target = evalRhs(rhs)
                    val key = dv.solveFor ?: return failed(ParamError.Code.NO_MATCH, dv.symbol)
                    val solved = solve(raw, key) { dv.value(it) - target } ?: return failed(ParamError.Code.OUT_OF_RANGE, dv.symbol)
                    return ParamApply.Ok(solved)
                }
                if (cv != null && lhs.name == cv) {
                    val arg = curveArg
                    val unknown = rhs.variables().filter { it != arg && it !in known }
                    if (unknown.isNotEmpty()) return failed(ParamError.Code.UNKNOWN_SYMBOL, unknown.first())
                    // v45: eğride x bir sayıdır (açı değil): sin(kx) radyan; "55°" yazılırsa derece düğümü çevrilir
                    val f: (Double) -> Double = { x -> MathExpr.eval(rhs, known + (arg to x), AngleMode.RAD) }
                    val fitted = fit(f, v) ?: return failed(ParamError.Code.NO_MATCH)
                    var next = raw
                    for ((k, value) in fitted) {
                        val dd = def(k) ?: continue
                        if (value < dd.min - 1e-6 || value > dd.max + 1e-6 || !value.isFinite()) return failed(ParamError.Code.OUT_OF_RANGE, dd.symbol)
                        next = withValue(next, k, value)
                    }
                    return ParamApply.Ok(next)
                }
                return failed(ParamError.Code.UNKNOWN_SYMBOL, lhs.name)
            }
            // şeklin genel formülü ("1/f = 1/do + 1/di"): değerlerle doğruysa değişiklik yok
            val a = evalRhs(lhs)
            val b = evalRhs(rhs)
            return if (Rel.EQ.holds(a, b, 1e-6)) ParamApply.Ok(raw) else failed(ParamError.Code.NO_MATCH)
        } catch (e: UnknownSymbol) {
            return failed(ParamError.Code.UNKNOWN_SYMBOL, e.symbol)
        } catch (e: MathException) {
            return if (e.error.code == MathError.Code.UNKNOWN_NAME) failed(ParamError.Code.UNKNOWN_SYMBOL, e.error.detail)
            else failed(ParamError.Code.NOT_NUMBER, math = e.error)
        }
    }

    private class UnknownSymbol(val symbol: String) : RuntimeException()

    /** [key] girişini aralığında tarayıp g = 0 yapan değeri bulur (bulamazsa null). */
    protected fun solve(raw: Map<String, String>, key: String, g: (ParamValues) -> Double): Map<String, String>? {
        val d = def(key) ?: return null
        fun at(x: Double): Double = g(values(raw + (key to store(x))))
        val n = 400
        var best: Map<String, String>? = null
        var px = d.min
        var pv = at(px)
        for (i in 1..n) {
            val x = d.min + (d.max - d.min) * i / n
            val gv = at(x)
            if (pv.isFinite() && gv.isFinite() && (pv == 0.0 || (pv < 0) != (gv < 0))) {
                var a = px; var b = x; var fa = pv
                repeat(70) {
                    val m = 0.5 * (a + b)
                    val fm = at(m)
                    if (!fm.isFinite()) return@repeat
                    if ((fm < 0) == (fa < 0)) { a = m; fa = fm } else b = m
                }
                val root = 0.5 * (a + b)
                // kutup (1/x) değil gerçek kök: kökteki değer tarama uçlarındakinden çok küçük (kayıt yuvarlaması payı)
                if (abs(at(root)) <= 1e-3 * (abs(pv) + abs(gv)) + 1e-9) {
                    // yuvarlak değer de tutuyorsa onu al (F = 20 → x = 0,4; 0,399999 değil)
                    val err = abs(at(root)) + 1e-9 * (1 + abs(pv) + abs(gv))
                    val nice = (0..6).asSequence().map { k -> val p = Math.pow(10.0, k.toDouble()); round(root * p) / p }.firstOrNull { abs(at(it)) <= err } ?: root
                    best = withValue(raw, key, nice); break
                }
            }
            px = x; pv = gv
        }
        // v45: tamsayı değerde yuvarlama sonrası hâlâ tutuyor mu? (ör. iç açısı 125° olan düzgün çokgen yok)
        val b = best ?: return null
        if (d.kind != ParamKind.INT) return b
        val n0 = values(b)[key]
        val check = g(values(b))
        val near = abs(at(n0 - 1)) + abs(at(n0 + 1))
        return if (check.isFinite() && abs(check) <= 1e-6 * (1 + near)) b else null
    }

    companion object {
        const val LABEL_COLOR = "#37474F"
        /** v45 görünmez kutu işaretinin etiketi ve boyu (sanal birim). */
        const val FRAME_TAG = "param:frame"
        const val FRAME_MARK = 0.001f

        /** Kayıtta saklanan sayı (en çok 6 ondalık, Locale bağımsız). */
        fun store(v: Double): String {
            val r = round(v * 1e6) / 1e6
            return if (r == round(r) && abs(r) < 1e15) r.toLong().toString() else String.format(Locale.ROOT, "%.6f", r).trimEnd('0').trimEnd('.')
        }

        /** Gösterilen sayı: en çok [dec] ondalık, Türkçe virgül. */
        fun num(v: Double, dec: Int = 2): String {
            if (!v.isFinite()) return "∞"
            val r = round(v * Math.pow(10.0, dec.toDouble())) / Math.pow(10.0, dec.toDouble())
            val s = if (r == round(r)) r.toLong().toString() else String.format(Locale.ROOT, "%.${dec}f", r).trimEnd('0').trimEnd('.')
            return s.replace('.', ',').replace("-", "−")
        }
    }
}
