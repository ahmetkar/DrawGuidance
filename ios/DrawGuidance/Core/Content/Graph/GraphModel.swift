import Foundation

/// Grafik üreticisinin çizebildiği türler (Android `GraphKind`).
enum GraphKind: String, CaseIterable { case AXES, COORD_PLANE, NUMBER_LINE, UNIT_CIRCLE, AXES_3D, FUNCTION, BAR, LINE, PIE, SCATTER, HISTOGRAM }

/// Bir grafik elemanının tüm ayarları (Android `GraphParams`; anlamlar oradaki belgede).
struct GraphParams: Equatable {
    var kind: GraphKind
    var preset = ""
    var formula = ""
    var xLabel = "x", yLabel = "y", zLabel = "z"
    var `repeat` = 2
    var ticks = 5
    var tickStep: Float = 0
    var showNumbers = true
    var grid = true
    var tickLabels: [String: String] = [:]
    var values: [Float] = []
    var valueLabels: [Int: String] = [:]
    var curveColor = "#1E88E5"
    var fillUnder = false
    var keyPoints = true
    var xMin: Float = .nan, xMax: Float = .nan
    var yMin: Float = .nan, yMax: Float = .nan
    /// v18 FUNCTION "desen": eğrinin yatay sıklığı k.
    var freq = 1
    /// v18 kağıt rengi.
    var paper = "#FFFFFF"
    /// v45: trigonometrik fonksiyonlar derece cinsinden.
    var angleDeg = false

    init(kind: GraphKind) { self.kind = kind }

    /// Kotlin `copy` karşılığı.
    func with(_ change: (inout GraphParams) -> Void) -> GraphParams {
        var q = self
        change(&q)
        return q
    }

    static func == (a: GraphParams, b: GraphParams) -> Bool {
        func same(_ x: Float, _ y: Float) -> Bool { x == y || (x.isNaN && y.isNaN) }
        return a.kind == b.kind && a.preset == b.preset && a.formula == b.formula && a.xLabel == b.xLabel && a.yLabel == b.yLabel &&
            a.zLabel == b.zLabel && a.repeat == b.repeat && a.ticks == b.ticks && a.tickStep == b.tickStep && a.showNumbers == b.showNumbers &&
            a.grid == b.grid && a.tickLabels == b.tickLabels && a.values == b.values && a.valueLabels == b.valueLabels &&
            a.curveColor == b.curveColor && a.fillUnder == b.fillUnder && a.keyPoints == b.keyPoints && same(a.xMin, b.xMin) &&
            same(a.xMax, b.xMax) && same(a.yMin, b.yMin) && same(a.yMax, b.yMax) && a.freq == b.freq && a.paper == b.paper && a.angleDeg == b.angleDeg
    }
}

/// Hazır eğri.
struct CurvePreset: Equatable {
    let id: String
    let title: String
    let formula: String
    let periodic: Bool
    var period: Double = 0
    var defaultRepeat = 2
}

/// Ön ayarın x aralığı biçimi.
enum XStart { case SYMMETRIC, FROM_ZERO, ONE_PERIOD, ZERO }

/// Ön ayarın görünüm ipuçları.
struct PresetHint {
    var label = ""
    var center = 0.0
    var unit = 1.0
    var start: XStart = .SYMMETRIC
    var yLimit = Double.nan
    var equal = false
    var decorate = ""
}

enum CurvePresets {
    private struct Entry { let preset: CurvePreset; let hint: PresetHint }

    private static let entries: [Entry] = [
        Entry(preset: CurvePreset(id: "dogrusal", title: "Doğrusal", formula: "2x + 1", periodic: false, defaultRepeat: 3), hint: PresetHint(label: "y = 2x + 1")),
        Entry(preset: CurvePreset(id: "parabol", title: "Parabol", formula: "x^2", periodic: false, defaultRepeat: 3), hint: PresetHint(label: "y = x²")),
        Entry(preset: CurvePreset(id: "parabol-tepe", title: "Parabol (tepe noktası)", formula: "x^2 - 4x + 3", periodic: false, defaultRepeat: 3),
              hint: PresetHint(label: "y = x² − 4x + 3", center: 2)),
        Entry(preset: CurvePreset(id: "kubik", title: "Kübik", formula: "x^3 - 3x", periodic: false, defaultRepeat: 3), hint: PresetHint(label: "y = x³ − 3x", unit: 0.8)),
        Entry(preset: CurvePreset(id: "sinus", title: "Sinüs", formula: "sin(x)", periodic: true, period: 2 * .pi, defaultRepeat: 2),
              hint: PresetHint(label: "y = sin(x)", start: .ONE_PERIOD)),
        Entry(preset: CurvePreset(id: "kosinus", title: "Kosinüs", formula: "cos(x)", periodic: true, period: 2 * .pi, defaultRepeat: 2),
              hint: PresetHint(label: "y = cos(x)", start: .ONE_PERIOD)),
        Entry(preset: CurvePreset(id: "tanjant", title: "Tanjant", formula: "tan(x)", periodic: true, period: .pi, defaultRepeat: 3),
              hint: PresetHint(label: "y = tan(x)", yLimit: 4)),
        Entry(preset: CurvePreset(id: "ustel", title: "Üstel", formula: "2^x", periodic: false, defaultRepeat: 3), hint: PresetHint(label: "y = 2ˣ")),
        Entry(preset: CurvePreset(id: "logaritma", title: "Logaritma", formula: "ln(x)", periodic: false, defaultRepeat: 3),
              hint: PresetHint(label: "y = ln(x)", unit: 2, start: .FROM_ZERO)),
        Entry(preset: CurvePreset(id: "karekok", title: "Karekök", formula: "sqrt(x)", periodic: false, defaultRepeat: 3),
              hint: PresetHint(label: "y = √x", unit: 2, start: .FROM_ZERO)),
        Entry(preset: CurvePreset(id: "kupkok", title: "Küpkök", formula: "cbrt(x)", periodic: false, defaultRepeat: 4), hint: PresetHint(label: "y = ∛x", unit: 2)),
        Entry(preset: CurvePreset(id: "mutlak", title: "Mutlak değer", formula: "abs(x)", periodic: false, defaultRepeat: 3), hint: PresetHint(label: "y = |x|")),
        Entry(preset: CurvePreset(id: "hiperbol", title: "Hiperbol", formula: "1/x", periodic: false, defaultRepeat: 4), hint: PresetHint(label: "y = 1/x")),
        Entry(preset: CurvePreset(id: "kaydirilmis-hiperbol", title: "Kaydırılmış hiperbol", formula: "1/(x - 2) + 1", periodic: false, defaultRepeat: 4),
              hint: PresetHint(label: "y = 1/(x − 2) + 1", center: 2)),
        Entry(preset: CurvePreset(id: "cember", title: "Çember", formula: "x^2 + y^2 = 9", periodic: false, defaultRepeat: 4),
              hint: PresetHint(label: "x² + y² = 9", equal: true, decorate: "circle:3")),
        Entry(preset: CurvePreset(id: "elips", title: "Elips", formula: "x^2/9 + y^2/4 = 1", periodic: false, defaultRepeat: 4),
              hint: PresetHint(label: "x²/9 + y²/4 = 1", equal: true)),
        Entry(preset: CurvePreset(id: "normal", title: "Normal dağılım (çan eğrisi)", formula: "exp(-x^2/2)/sqrt(2pi)", periodic: false, defaultRepeat: 4),
              hint: PresetHint(label: "y = e^(−x²/2) / √(2π)")),
        Entry(preset: CurvePreset(id: "basamak", title: "Tam değer (basamak)", formula: "floor(x)", periodic: false, defaultRepeat: 3), hint: PresetHint(label: "y = ⟦x⟧")),
        Entry(preset: CurvePreset(id: "sonumlu", title: "Sönümlü salınım", formula: "e^(-x/4)*sin(2x)", periodic: true, period: .pi, defaultRepeat: 4),
              hint: PresetHint(label: "y = e^(−x/4)·sin(2x)", start: .ZERO)),
        Entry(preset: CurvePreset(id: "arktanjant", title: "Arktanjant", formula: "atan(x)", periodic: false, defaultRepeat: 4), hint: PresetHint(label: "y = arctan(x)", unit: 2)),
    ]

    private static let byId: [String: Entry] = Dictionary(uniqueKeysWithValues: entries.map { ($0.preset.id, $0) })

    static let all: [CurvePreset] = entries.map(\.preset)
    static func find(_ id: String) -> CurvePreset? { byId[id]?.preset }
    static func hint(_ id: String) -> PresetHint? { byId[id]?.hint }
}
