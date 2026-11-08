import Foundation

/// Grafik şekilleri (Android `ShapeRegistry` grafik bölümü): kimlik "graph.<GraphKind>" ya da "curve.<hazır eğri>";
/// kayıttaki GraphSpec ile grafik motoru (Graphs) arasında çeviri.
enum GraphShapes {
    static let GRAPH_PREFIX = "graph."
    static let CURVE_PREFIX = "curve."

    /// Eski yerleşik grafik şekillerinin yeni karşılığı.
    private static let legacy: [String: GraphKind] = [
        "AXES": .AXES, "FUNCTION_PLOT": .FUNCTION, "COORD_GRID": .COORD_PLANE, "AXES_3D": .AXES_3D, "UNIT_CIRCLE": .UNIT_CIRCLE,
        "BAR_CHART": .BAR, "LINE_CHART": .LINE, "PIE_CHART": .PIE, "SCATTER": .SCATTER,
    ]

    /// Eski grafik şekillerinin yeni grafik kimliği (düzenlenince yeni motorla yeniden kurulur).
    static func upgradeGraphKind(_ id: String) -> String { legacy[id].map { GRAPH_PREFIX + $0.rawValue } ?? id }

    static func graphKind(_ id: String) -> GraphKind? {
        if id.hasPrefix(GRAPH_PREFIX) { return GraphKind(rawValue: String(id.dropFirst(GRAPH_PREFIX.count))) }
        if id.hasPrefix(CURVE_PREFIX) { return .FUNCTION }
        return legacy[id]
    }

    static func preset(_ id: String) -> String? { id.hasPrefix(CURVE_PREFIX) ? String(id.dropFirst(CURVE_PREFIX.count)) : nil }

    static func isGraph(_ id: String) -> Bool { graphKind(id) != nil }

    /// Yeni motorla kurulan grafik mi (eski kayıtlar değil)?
    static func isModernGraph(_ id: String) -> Bool { id.hasPrefix(GRAPH_PREFIX) || id.hasPrefix(CURVE_PREFIX) }

    static func fit(_ id: String) -> ShapeFit { graphKind(id) == .NUMBER_LINE ? .LINE : .BOX }

    static func aspect(_ id: String) -> Float {
        switch graphKind(id) {
        case .PIE, .UNIT_CIRCLE, .AXES_3D: return 1
        case .NUMBER_LINE: return 5
        default: return 1.3
        }
    }

    /// Yeni grafiğin varsayılan ayarları (hazır eğrinin tekrar sayısı).
    static func defaultGraph(_ id: String) -> GraphSpec {
        var g = GraphSpec()
        guard let kind = graphKind(id) else { return g }
        let preset = preset(id).flatMap { CurvePresets.find($0) }
        g.kind = kind.rawValue
        g.preset = preset?.id ?? ""
        g.repeat = preset?.defaultRepeat ?? 2
        // Hazır eğrisi olmayan fonksiyon grafiği bir örnek formülle başlar
        g.formula = kind == .FUNCTION && preset == nil ? "x^2 - 2" : ""
        return g
    }

    static func params(_ id: String, _ g: GraphSpec) -> GraphParams {
        var p = GraphParams(kind: graphKind(id) ?? GraphKind(rawValue: g.kind) ?? .AXES)
        // Kullanıcı formül yazdıysa (hazır eğri temizlendiyse) hazır eğri yerine formül çizilir
        p.preset = (!g.formula.allSatisfy(\.isWhitespace) && g.preset.isEmpty) ? "" : (preset(id) ?? g.preset)
        p.formula = g.formula
        p.xLabel = g.xLabel; p.yLabel = g.yLabel; p.zLabel = g.zLabel
        p.repeat = g.repeat
        p.ticks = g.ticks
        p.showNumbers = g.showNumbers
        p.grid = g.grid
        p.tickLabels = g.tickLabels
        p.values = g.values
        p.valueLabels = g.valueLabels.map
        p.curveColor = g.curveColor
        p.fillUnder = g.fillUnder
        p.keyPoints = g.keyPoints
        p.xMin = g.rangeSet ? g.xMin : .nan
        p.xMax = g.rangeSet ? g.xMax : .nan
        p.freq = max(g.freq, 1)
        p.paper = g.paper.isEmpty ? "#FFFFFF" : g.paper
        p.angleDeg = g.angleDeg
        return p
    }

    static func spec(_ p: GraphParams, _ base: GraphSpec) -> GraphSpec {
        var g = base
        g.kind = p.kind.rawValue; g.preset = p.preset; g.formula = p.formula
        g.xLabel = p.xLabel; g.yLabel = p.yLabel; g.zLabel = p.zLabel
        g.repeat = p.repeat; g.ticks = p.ticks; g.showNumbers = p.showNumbers; g.grid = p.grid
        g.tickLabels = p.tickLabels; g.values = p.values; g.valueLabels = IntKeyedStrings(p.valueLabels)
        g.curveColor = p.curveColor; g.fillUnder = p.fillUnder; g.keyPoints = p.keyPoints
        g.freq = p.freq
        g.paper = p.paper.caseInsensitiveCompare("#FFFFFF") == .orderedSame ? "" : p.paper
        g.angleDeg = p.angleDeg
        return g
    }

    /// Grafiğin parçaları (tuval koordinatlarında).
    static func paths(_ id: String, _ start: P, _ end: P, graph: GraphSpec?) -> [ShapePath] {
        let p = params(id, graph ?? defaultGraph(id))
        if fit(id) == .LINE {
            let length = max(1e-3, hypotf(end.x - start.x, end.y - start.y))
            return ShapeFactory.lineMapped(start, end, aspect: aspect(id), paths: Graphs.build(p, aspect: aspect(id), resolution: length))
        }
        let rect = FRect(left: min(start.x, end.x), top: min(start.y, end.y), right: max(start.x, end.x), bottom: max(start.y, end.y))
        let a = max(0.05, rect.width) / max(0.05, rect.height)
        return ShapeFactory.mapped(Graphs.build(p, aspect: a, resolution: max(rect.width, rect.height)), rect)
    }

    /// Seçicideki grafikler.
    static let graphItems: [String] = [GraphKind.AXES, .COORD_PLANE, .NUMBER_LINE, .UNIT_CIRCLE, .AXES_3D, .BAR, .LINE, .PIE, .SCATTER, .HISTOGRAM]
        .map { GRAPH_PREFIX + $0.rawValue }

    /// Grafik eğrileri: formülü kullanıcının yazdığı fonksiyon grafiği ve hazır fonksiyonlar.
    static var curveItems: [String] { [GRAPH_PREFIX + GraphKind.FUNCTION.rawValue] + CurvePresets.all.map { CURVE_PREFIX + $0.id } }

    static func title(_ id: String) -> String? {
        if id.hasPrefix(CURVE_PREFIX) { return CurvePresets.find(String(id.dropFirst(CURVE_PREFIX.count)))?.title }
        guard id.hasPrefix(GRAPH_PREFIX), let k = graphKind(id) else { return nil }
        switch k {
        case .AXES: return "Koordinat ekseni"
        case .COORD_PLANE: return "Koordinat düzlemi"
        case .NUMBER_LINE: return "Sayı doğrusu"
        case .UNIT_CIRCLE: return "Birim çember"
        case .AXES_3D: return "3B eksen"
        case .FUNCTION: return "Fonksiyon grafiği"
        case .BAR: return "Sütun grafiği"
        case .LINE: return "Çizgi grafiği"
        case .PIE: return "Pasta grafiği"
        case .SCATTER: return "Serpilme grafiği"
        case .HISTOGRAM: return "Histogram"
        }
    }
}
