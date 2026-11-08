package com.karapp.drawguidance.content.graph

import com.karapp.drawguidance.content.vector.VPath

/**
 * Grafik üreticisi: [GraphParams] → birim kutuda (0..1, y aşağı) düzenlenebilir vektör parçaları.
 *
 * Parça etiketleri (uygulamada şekil üzerinde dokunarak düzenleme için):
 * - "tick:x:3", "tick:y:-1", "tick:z:2" — çentik ve numarası ([GraphParams.tickLabels] anahtarıyla aynı biçim;
 *   başlangıç yazısı "tick:x:0"; NUMBER_LINE ara çentikleri "tick:x:1/2"; UNIT_CIRCLE "tick:a:30" açı,
 *   "tick:c:30" koordinat yazısı),
 * - "curve" eğri / yüzey / çizgi grafiği, "fill" alan dolgusu, "asymptote" asimptot, "key:<i>" önemli nokta,
 * - "value:<i>" sütun / dilim / nokta / değer yazısı, "vlabel:<i>" kategori ya da nokta adı,
 * - "axis:x" / "axis:y" / "axis:z" eksen adı, "formula" formül kutusu, "angle:<derece>" birim çember noktası.
 */
object Graphs {

    /** [aspect] = genişlik / yükseklik; [resolution] ≈ piksel boyu (örnekleme sıklığı). */
    fun build(params: GraphParams, aspect: Float, resolution: Float = 512f): List<VPath> =
        render(params, aspect, resolution).paths()

    internal fun render(params: GraphParams, aspect: Float, resolution: Float): Canvas {
        val c = Canvas(aspect, resolution, params)
        try {
            when (params.kind) {
                GraphKind.AXES, GraphKind.COORD_PLANE, GraphKind.FUNCTION -> Cartesian.build(c)
                GraphKind.NUMBER_LINE -> Special.numberLine(c)
                GraphKind.UNIT_CIRCLE -> Special.unitCircle(c)
                GraphKind.AXES_3D -> Special.axes3d(c)
                GraphKind.BAR, GraphKind.LINE, GraphKind.PIE, GraphKind.SCATTER, GraphKind.HISTOGRAM -> Charts.build(c)
            }
        } catch (e: RuntimeException) {
            // Beklenmeyen bir formül/değer uygulamayı düşürmesin: o ana kadar çizilenler döner.
            if (System.getProperty("graphs.strict") == "true") throw e
        }
        return c
    }

    private fun periodic(p: GraphParams): Boolean =
        p.kind == GraphKind.FUNCTION && CurveSource.resolve(p).periodic

    /** v18: FUNCTION'da desenin (eğri sıklığı k) en büyük değeri. */
    const val MAX_FREQ = 8

    /** Desen göstergesindeki sayı: FUNCTION'da eğrinin sıklığı ([GraphParams.freq]), diğerlerinde tekrar. */
    fun patternValue(p: GraphParams): Int = if (p.kind == GraphKind.FUNCTION) p.freq.coerceIn(1, MAX_FREQ) else p.repeat

    /** "Desen" (repeat) sınırları; v18 FUNCTION'da sıklık (1..[MAX_FREQ]). */
    fun patternRange(p: GraphParams): IntRange = when (p.kind) {
        GraphKind.FUNCTION -> 1..MAX_FREQ
        GraphKind.AXES, GraphKind.COORD_PLANE -> 1..10
        GraphKind.NUMBER_LINE -> 1..20
        GraphKind.UNIT_CIRCLE -> 1..4
        GraphKind.AXES_3D -> 1..8
        GraphKind.BAR, GraphKind.LINE, GraphKind.PIE, GraphKind.SCATTER, GraphKind.HISTOGRAM -> -1..12
    }

    fun morePattern(p: GraphParams): GraphParams {
        // v18: fonksiyon grafiğinde desen eğrinin sıklığıdır (formül de değişir: sin(x) → sin(2x))
        if (p.kind == GraphKind.FUNCTION) return p.copy(freq = (p.freq + 1).coerceIn(1, MAX_FREQ))
        if (Charts.isChart(p.kind) && p.values.isNotEmpty()) {
            val n = Charts.itemCount(p)
            if (n >= 15) return p
            val extra = Charts.defaults(p.kind, n + 1)
            val add = if (p.kind == GraphKind.SCATTER) extra.takeLast(2) else listOf(extra.last())
            return p.copy(values = p.values + add, repeat = (p.repeat + 1).coerceIn(patternRange(p)))
        }
        return p.copy(repeat = (p.repeat + 1).coerceIn(patternRange(p)))
    }

    fun lessPattern(p: GraphParams): GraphParams {
        if (p.kind == GraphKind.FUNCTION) return p.copy(freq = (p.freq - 1).coerceIn(1, MAX_FREQ))
        if (Charts.isChart(p.kind) && p.values.isNotEmpty()) {
            val n = Charts.itemCount(p)
            if (n <= 2) return p
            val drop = if (p.kind == GraphKind.SCATTER) 2 else 1
            return p.copy(values = p.values.dropLast(drop), repeat = (p.repeat - 1).coerceIn(patternRange(p)))
        }
        return p.copy(repeat = (p.repeat - 1).coerceIn(patternRange(p)))
    }

    fun morePoints(p: GraphParams): GraphParams = p.copy(ticks = (p.ticks + 1).coerceIn(1, 12))

    fun lessPoints(p: GraphParams): GraphParams = p.copy(ticks = (p.ticks - 1).coerceIn(1, 12))

    /** Çentik yazısını değiştirir ("" gizler). Anahtar: "x:3", "y:-1", "formula", "a:30"… */
    fun withTickLabel(p: GraphParams, key: String, text: String): GraphParams =
        p.copy(tickLabels = p.tickLabels + (key.removePrefix("tick:") to text))

    /** Değiştirilmiş çentik yazısını varsayılana döndürür. */
    fun withoutTickLabel(p: GraphParams, key: String): GraphParams =
        p.copy(tickLabels = p.tickLabels - key.removePrefix("tick:"))

    /**
     * Grafik verisini düzenler ([GraphParams.values] boşsa önce varsayılanlar yazılır). SCATTER'da [index]. noktanın
     * y değeri, AXES_3D'de düz dizin, diğerlerinde [index]. öğe değişir; dizin taşarsa varsayılanlarla uzatılır.
     */
    fun withValue(p: GraphParams, index: Int, value: Float): GraphParams {
        if (index < 0 || !value.isFinite()) return p
        val list = Charts.effective(p).toMutableList()
        val flat = if (p.kind == GraphKind.SCATTER) index * 2 + 1 else index
        if (flat >= list.size) {
            val needItems = if (p.kind == GraphKind.SCATTER) index + 1 else index + 1
            val def = Charts.defaults(p.kind, needItems.coerceAtMost(64))
            while (list.size <= flat && list.size < def.size) list += def[list.size]
            while (list.size <= flat) list += 0f
        }
        list[flat] = if (p.kind == GraphKind.PIE) value.coerceAtLeast(0f) else value
        return p.copy(values = list)
    }

    /** Grafik verisi (kullanıcınınki ya da çizimde kullanılan varsayılan). */
    fun values(p: GraphParams): List<Float> = Charts.effective(p)

    /** Grafikteki öğe sayısı (sütun, dilim, nokta…). */
    fun itemCount(p: GraphParams): Int = Charts.itemCount(p)

    /**
     * Çentikte şu an görünen metin (düzenleme penceresinin ilk metni). Değiştirilmişse kullanıcının metni. Bazı
     * adımlar kutunun en-boy oranına bağlı olduğundan (FUNCTION y ekseni) [aspect] verilmesi önerilir.
     */
    fun tickValue(p: GraphParams, key: String, aspect: Float = 1f): String {
        val k = key.removePrefix("tick:")
        p.tickLabels[k]?.let { return it }
        return render(p, aspect, 256f).defaults[k] ?: ""
    }

    /**
     * Formül kutusunun varsayılan metni, ör. "y = sin(x)", "y = x² − 3x", "z = x² + y²". v18: fonksiyon grafiğinde
     * desen (sıklık k) formüle yansır: "y = sin(2x)", "y = (2x)²".
     */
    fun formulaLabel(p: GraphParams): String {
        return when (p.kind) {
            GraphKind.FUNCTION, GraphKind.AXES, GraphKind.COORD_PLANE -> {
                val preset = CurvePresets.find(p.preset)
                val base = if (preset != null) CurvePresets.hint(preset.id)?.label?.takeIf { it.isNotBlank() } ?: FormulaText.label(preset.formula)
                // v45: birden çok eğri "y = x²;  y = 2x + 1"
                else GraphFormula.parts(p.formula).take(GraphFormula.MAX_CURVES).joinToString(";  ") { FormulaText.label(it, "y") }
                val k = if (p.kind == GraphKind.FUNCTION) p.freq.coerceIn(1, MAX_FREQ) else 1
                if (k == 1) base else FormulaText.scaleX(base, k)
            }
            GraphKind.AXES_3D -> FormulaText.label(p.formula, "z")
            // v45: sayı doğrusunda nokta/çözüm kümesi, örneklenen grafiklerde y = f(x)
            GraphKind.NUMBER_LINE -> GraphFormula.parts(p.formula).joinToString(";  ") { GraphFormula.label(it) }
            GraphKind.BAR, GraphKind.LINE, GraphKind.HISTOGRAM, GraphKind.PIE, GraphKind.SCATTER ->
                GraphFormula.parts(p.formula).firstOrNull()?.let { FormulaText.label(it, "y") } ?: ""
            else -> ""
        }
    }

    /**
     * v45: kullanıcının yazdığı formülü uygular. Boş formül formülü kaldırır. Eğri türlerinde hazır eğri ve desen
     * temizlenir; örneklenen grafiklerde elle girilen değerler formülden yeniden örneklensin diye silinir; birim
     * çemberde değişmez. Geçersiz formülde [p] aynen döner (çağıran hata gösterir, son geçerli eğri kalır).
     */
    fun withFormula(p: GraphParams, text: String): GraphParams {
        val t = text.trim()
        if (!GraphFormula.accepts(p.kind)) return p
        if (t.isEmpty()) return p.copy(formula = "", freq = 1)
        if (GraphFormula.error(p.kind, t) != null) return p
        return when (GraphFormula.meaning(p.kind)) {
            GraphFormula.Meaning.SAMPLED -> p.copy(formula = t, values = emptyList())
            else -> p.copy(formula = t, preset = "", freq = 1)
        }
    }

    /** v45: formül alanında gösterilen (yazılabilir) metin: hazır eğrinin formülü ya da kullanıcınınki. */
    fun formulaText(p: GraphParams): String = if (p.kind == GraphKind.FUNCTION && p.freq > 1) editableFormula(p)
    else CurvePresets.find(p.preset)?.formula?.takeIf { p.kind == GraphKind.FUNCTION || p.kind == GraphKind.AXES || p.kind == GraphKind.COORD_PLANE } ?: p.formula

    /**
     * Formül düzenleme penceresinin ilk metni (yazılabilir biçim): desen (k) varsa x yerine (k*x) yazılır, ör.
     * "sin(x)" → "sin(2*x)". Uygulanınca bu formül çizilir ve desen 1'e döner.
     */
    fun editableFormula(p: GraphParams): String {
        val preset = CurvePresets.find(p.preset)
        val text = preset?.formula ?: p.formula
        val k = if (p.kind == GraphKind.FUNCTION) p.freq.coerceIn(1, MAX_FREQ) else 1
        if (k == 1 || text.isBlank()) return text
        return Regex("(?<![A-Za-z])x(?![A-Za-z])").replace(text) { m ->
            val before = text.substring(0, m.range.first).trimEnd().lastOrNull()
            val after = text.substring(m.range.last + 1).trimStart().firstOrNull()
            // Öncelik bozulmayacaksa parantezsiz: sin(x) → sin(2*x); x^2 → (2*x)^2, 1/x → 1/(2*x)
            val safe = (before == null || before in "(,+-=") && (after == null || after in "),+-=")
            if (safe) "$k*x" else "($k*x)"
        }
    }
}
