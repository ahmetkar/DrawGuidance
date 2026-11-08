import Foundation

/// Grafik üreticisi: GraphParams → birim kutuda (0..1, y aşağı) düzenlenebilir vektör parçaları (Android `Graphs`).
///
/// Parça etiketleri (şekil üzerinde dokunarak düzenleme için): "tick:x:3", "tick:y:-1", "tick:z:2" (çentik), "curve",
/// "fill", "asymptote", "key:<i>", "value:<i>", "vlabel:<i>", "axis:x|y|z", "formula", "angle:<derece>".
enum Graphs {
    /// [aspect] = genişlik / yükseklik; [resolution] ≈ piksel boyu (örnekleme sıklığı).
    static func build(_ params: GraphParams, aspect: Float, resolution: Float = 512) -> [VPath] {
        render(params, aspect, resolution).paths()
    }

    static func render(_ params: GraphParams, _ aspect: Float, _ resolution: Float) -> GCanvas {
        let c = GCanvas(aspect, resolution, params)
        switch params.kind {
        case .AXES, .COORD_PLANE, .FUNCTION: Cartesian.build(c)
        case .NUMBER_LINE: Special.numberLine(c)
        case .UNIT_CIRCLE: Special.unitCircle(c)
        case .AXES_3D: Special.axes3d(c)
        case .BAR, .LINE, .PIE, .SCATTER, .HISTOGRAM: Charts.build(c)
        }
        return c
    }

    /// v18: FUNCTION'da desenin (eğri sıklığı k) en büyük değeri.
    static let MAX_FREQ = 8

    /// Desen göstergesindeki sayı: FUNCTION'da eğrinin sıklığı, diğerlerinde tekrar.
    static func patternValue(_ p: GraphParams) -> Int { p.kind == .FUNCTION ? clampI(p.freq, 1, MAX_FREQ) : p.repeat }

    /// "Desen" (repeat) sınırları; v18 FUNCTION'da sıklık (1..MAX_FREQ).
    static func patternRange(_ p: GraphParams) -> ClosedRange<Int> {
        switch p.kind {
        case .FUNCTION: return 1...MAX_FREQ
        case .AXES, .COORD_PLANE: return 1...10
        case .NUMBER_LINE: return 1...20
        case .UNIT_CIRCLE: return 1...4
        case .AXES_3D: return 1...8
        case .BAR, .LINE, .PIE, .SCATTER, .HISTOGRAM: return -1...12
        }
    }

    private static func clampR(_ v: Int, _ r: ClosedRange<Int>) -> Int { clampI(v, r.lowerBound, r.upperBound) }

    static func morePattern(_ p: GraphParams) -> GraphParams {
        // v18: fonksiyon grafiğinde desen eğrinin sıklığıdır (formül de değişir: sin(x) → sin(2x))
        if p.kind == .FUNCTION { return p.with { $0.freq = clampI(p.freq + 1, 1, MAX_FREQ) } }
        if Charts.isChart(p.kind) && !p.values.isEmpty {
            let n = Charts.itemCount(p)
            if n >= 15 { return p }
            let extra = Charts.defaults(p.kind, n + 1)
            let add = p.kind == .SCATTER ? Array(extra.suffix(2)) : [extra.last!]
            return p.with { $0.values = p.values + add; $0.repeat = clampR(p.repeat + 1, patternRange(p)) }
        }
        return p.with { $0.repeat = clampR(p.repeat + 1, patternRange(p)) }
    }

    static func lessPattern(_ p: GraphParams) -> GraphParams {
        if p.kind == .FUNCTION { return p.with { $0.freq = clampI(p.freq - 1, 1, MAX_FREQ) } }
        if Charts.isChart(p.kind) && !p.values.isEmpty {
            let n = Charts.itemCount(p)
            if n <= 2 { return p }
            let drop = p.kind == .SCATTER ? 2 : 1
            return p.with { $0.values = Array(p.values.dropLast(drop)); $0.repeat = clampR(p.repeat - 1, patternRange(p)) }
        }
        return p.with { $0.repeat = clampR(p.repeat - 1, patternRange(p)) }
    }

    static func morePoints(_ p: GraphParams) -> GraphParams { p.with { $0.ticks = clampI(p.ticks + 1, 1, 12) } }
    static func lessPoints(_ p: GraphParams) -> GraphParams { p.with { $0.ticks = clampI(p.ticks - 1, 1, 12) } }

    private static func stripTick(_ key: String) -> String { key.hasPrefix("tick:") ? String(key.dropFirst(5)) : key }

    /// Çentik yazısını değiştirir ("" gizler). Anahtar: "x:3", "y:-1", "formula", "a:30"…
    static func withTickLabel(_ p: GraphParams, _ key: String, _ text: String) -> GraphParams {
        p.with { $0.tickLabels[stripTick(key)] = text }
    }

    /// Değiştirilmiş çentik yazısını varsayılana döndürür.
    static func withoutTickLabel(_ p: GraphParams, _ key: String) -> GraphParams {
        p.with { $0.tickLabels.removeValue(forKey: stripTick(key)) }
    }

    /// Grafik verisini düzenler (values boşsa önce varsayılanlar yazılır). SCATTER'da [index]. noktanın y değeri,
    /// AXES_3D'de düz dizin, diğerlerinde [index]. öğe değişir; dizin taşarsa varsayılanlarla uzatılır.
    static func withValue(_ p: GraphParams, _ index: Int, _ value: Float) -> GraphParams {
        if index < 0 || !value.isFinite { return p }
        var list = Charts.effective(p)
        let flat = p.kind == .SCATTER ? index * 2 + 1 : index
        if flat >= list.count {
            let needItems = index + 1
            let def = Charts.defaults(p.kind, min(needItems, 64))
            while list.count <= flat && list.count < def.count { list.append(def[list.count]) }
            while list.count <= flat { list.append(0) }
        }
        list[flat] = p.kind == .PIE ? max(value, 0) : value
        return p.with { $0.values = list }
    }

    /// Grafik verisi (kullanıcınınki ya da çizimde kullanılan varsayılan).
    static func values(_ p: GraphParams) -> [Float] { Charts.effective(p) }

    /// Grafikteki öğe sayısı (sütun, dilim, nokta…).
    static func itemCount(_ p: GraphParams) -> Int { Charts.itemCount(p) }

    /// Çentikte şu an görünen metin (düzenleme penceresinin ilk metni).
    static func tickValue(_ p: GraphParams, _ key: String, aspect: Float = 1) -> String {
        let k = stripTick(key)
        if let v = p.tickLabels[k] { return v }
        return render(p, aspect, 256).defaults[k] ?? ""
    }

    /// Formül kutusunun varsayılan metni, ör. "y = sin(x)", "z = x² + y²"; v18 desen (k) formüle yansır.
    static func formulaLabel(_ p: GraphParams) -> String {
        switch p.kind {
        case .FUNCTION, .AXES, .COORD_PLANE:
            let base: String
            if let preset = CurvePresets.find(p.preset) {
                let l = CurvePresets.hint(preset.id)?.label ?? ""
                base = l.allSatisfy(\.isWhitespace) ? FormulaText.label(preset.formula) : l
            } else {
                // v45: birden çok eğri "y = x²;  y = 2x + 1"
                base = GraphFormula.parts(p.formula).prefix(GraphFormula.MAX_CURVES).map { FormulaText.label($0, "y") }.joined(separator: ";  ")
            }
            let k = p.kind == .FUNCTION ? clampI(p.freq, 1, MAX_FREQ) : 1
            return k == 1 ? base : FormulaText.scaleX(base, k)
        case .AXES_3D: return FormulaText.label(p.formula, "z")
        // v45: sayı doğrusunda nokta/çözüm kümesi, örneklenen grafiklerde y = f(x)
        case .NUMBER_LINE: return GraphFormula.parts(p.formula).map { GraphFormula.label($0) }.joined(separator: ";  ")
        case .BAR, .LINE, .HISTOGRAM, .PIE, .SCATTER:
            return GraphFormula.parts(p.formula).first.map { FormulaText.label($0, "y") } ?? ""
        default: return ""
        }
    }

    /// v45: kullanıcının yazdığı formülü uygular. Geçersiz formülde [p] aynen döner.
    static func withFormula(_ p: GraphParams, _ text: String) -> GraphParams {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if !GraphFormula.accepts(p.kind) { return p }
        if t.isEmpty { return p.with { $0.formula = ""; $0.freq = 1 } }
        if GraphFormula.error(p.kind, t) != nil { return p }
        if GraphFormula.meaning(p.kind) == .SAMPLED { return p.with { $0.formula = t; $0.values = [] } }
        return p.with { $0.formula = t; $0.preset = ""; $0.freq = 1 }
    }

    /// v45: formül alanında gösterilen (yazılabilir) metin: hazır eğrinin formülü ya da kullanıcınınki.
    static func formulaText(_ p: GraphParams) -> String {
        if p.kind == .FUNCTION && p.freq > 1 { return editableFormula(p) }
        if p.kind == .FUNCTION || p.kind == .AXES || p.kind == .COORD_PLANE, let f = CurvePresets.find(p.preset)?.formula { return f }
        return p.formula
    }

    /// Formül düzenleme penceresinin ilk metni: desen (k) varsa x yerine (k*x) yazılır, ör. "sin(x)" → "sin(2*x)".
    static func editableFormula(_ p: GraphParams) -> String {
        let text = CurvePresets.find(p.preset)?.formula ?? p.formula
        let k = p.kind == .FUNCTION ? clampI(p.freq, 1, MAX_FREQ) : 1
        if k == 1 || text.allSatisfy(\.isWhitespace) { return text }
        let ch = Array(text)
        func isLetter(_ c: Character) -> Bool { c.isASCII && c.isLetter }
        var out = ""
        for (i, c) in ch.enumerated() {
            let standalone = c == "x" && (i == 0 || !isLetter(ch[i - 1])) && (i + 1 >= ch.count || !isLetter(ch[i + 1]))
            if !standalone { out.append(c); continue }
            let before = ch[..<i].last(where: { !$0.isWhitespace })
            let after = ch[(i + 1)...].first(where: { !$0.isWhitespace })
            // Öncelik bozulmayacaksa parantezsiz: sin(x) → sin(2*x); x^2 → (2*x)^2, 1/x → 1/(2*x)
            let safe = (before == nil || "(,+-=".contains(before!)) && (after == nil || "),+-=".contains(after!))
            out += safe ? "\(k)*x" : "(\(k)*x)"
        }
        return out
    }
}
