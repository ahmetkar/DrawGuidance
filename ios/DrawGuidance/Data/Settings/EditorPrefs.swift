import Combine
import Foundation

/// Kullanıcının kaydettiği renkler (en yeni başta) (Android `ColorPaletteStore`).
@MainActor
final class ColorPaletteStore: ObservableObject {
    static let MAX_COLORS = 24
    private static let KEY = "saved_colors"
    private let defaults: UserDefaults
    @Published private(set) var colors: [String]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        colors = (defaults.string(forKey: Self.KEY) ?? "").split(separator: ",").map(String.init).filter(Self.isHex)
    }

    static func isHex(_ s: String) -> Bool { s.count == 7 && s.hasPrefix("#") && s.dropFirst().allSatisfy(\.isHexDigit) }

    func add(_ colorHex: String) {
        let hex = colorHex.uppercased()
        persist(Array(([hex] + colors.filter { $0.caseInsensitiveCompare(hex) != .orderedSame }).prefix(Self.MAX_COLORS)))
    }

    func remove(_ colorHex: String) { persist(colors.filter { $0.caseInsensitiveCompare(colorHex) != .orderedSame }) }

    private func persist(_ c: [String]) {
        defaults.set(c.joined(separator: ","), forKey: Self.KEY)
        colors = c
    }
}

/// Çizim ekranının kalıcı tercihleri (Android `EditorPrefsStore`).
@MainActor
final class EditorPrefsStore: ObservableObject {
    private let d: UserDefaults
    @Published private(set) var autosave: Bool
    @Published private(set) var handwriting: Bool
    @Published private(set) var textSuggest: Bool
    @Published private(set) var shapeSuggest: Bool
    @Published private(set) var selectionSuggest: Bool
    @Published private(set) var lineSuggest: Bool

    init(defaults: UserDefaults = .standard) {
        d = defaults
        func b(_ k: String) -> Bool { defaults.object(forKey: k) as? Bool ?? true }
        autosave = b("autosave"); handwriting = b("handwriting_suggest"); textSuggest = b("text_suggest")
        shapeSuggest = b("shape_suggest"); selectionSuggest = b("selection_suggest"); lineSuggest = b("line_suggest")
    }

    func setAutosave(_ v: Bool) { autosave = v; d.set(v, forKey: "autosave") }
    func setHandwriting(_ v: Bool) { handwriting = v; d.set(v, forKey: "handwriting_suggest") }
    func setTextSuggest(_ v: Bool) { textSuggest = v; d.set(v, forKey: "text_suggest") }
    func setShapeSuggest(_ v: Bool) { shapeSuggest = v; d.set(v, forKey: "shape_suggest") }
    func setSelectionSuggest(_ v: Bool) { selectionSuggest = v; d.set(v, forKey: "selection_suggest") }
    func setLineSuggest(_ v: Bool) { lineSuggest = v; d.set(v, forKey: "line_suggest") }

    /// v28 öneri balonlarının sürüklendiği yer (oran, 0..1); nil: kendiliğinden.
    var suggestPin: (Float, Float)? {
        get {
            let x = d.object(forKey: "suggest_pin_x") as? Float ?? -1, y = d.object(forKey: "suggest_pin_y") as? Float ?? -1
            return (x < 0 || y < 0) ? nil : (x, y)
        }
        set { d.set(newValue?.0 ?? -1, forKey: "suggest_pin_x"); d.set(newValue?.1 ?? -1, forKey: "suggest_pin_y") }
    }

    func canvas(_ canvasId: String) -> CanvasPrefs { CanvasPrefs(d, canvasId) }

    func clearCanvas(_ canvasId: String) {
        let prefix = "canvas/\(canvasId)/"
        for k in d.dictionaryRepresentation().keys where k.hasPrefix(prefix) { d.removeObject(forKey: k) }
    }
}

/// v29 bir tuvalin kendi son ayarları (Android `CanvasPrefs`).
final class CanvasPrefs {
    private let d: UserDefaults
    private let canvasId: String
    init(_ d: UserDefaults, _ canvasId: String) { self.d = d; self.canvasId = canvasId }
    private func key(_ n: String) -> String { "canvas/\(canvasId)/\(n)" }

    var lastColor: String? {
        get { d.string(forKey: key("last_color")) }
        set { d.set(newValue, forKey: key("last_color")) }
    }
    var lastBrush: String? {
        get { d.string(forKey: key("last_brush")) }
        set { d.set(newValue, forKey: key("last_brush")) }
    }
    var lastText: (TextSpec, Float)? {
        get {
            guard let data = d.data(forKey: key("last_text")), let spec = try? JSONDecoder().decode(TextSpec.self, from: data) else { return nil }
            return (spec, d.float(forKey: key("last_text_size")))
        }
        set {
            guard let v = newValue else { return }
            var s = v.0; s.content = ""
            d.set(try? JSONEncoder().encode(s), forKey: key("last_text"))
            d.set(v.1, forKey: key("last_text_size"))
        }
    }
    var lastAddItem: String? {
        get { d.string(forKey: key("last_add")) }
        set { d.set(newValue, forKey: key("last_add")) }
    }
    /// "" = dolgusuz, nil = hiç seçilmedi.
    var shapeFill: String? {
        get { d.string(forKey: key("shape_fill")) }
        set { d.set(newValue ?? "", forKey: key("shape_fill")) }
    }
    var brushTunings: [String: BrushTuning] {
        get { d.data(forKey: key("brush_tunings")).flatMap { try? JSONDecoder().decode([String: BrushTuning].self, from: $0) } ?? [:] }
        set { d.set(try? JSONEncoder().encode(newValue), forKey: key("brush_tunings")) }
    }
    var brushSizes: [String: Float] {
        get { d.data(forKey: key("brush_sizes")).flatMap { try? JSONDecoder().decode([String: Float].self, from: $0) } ?? [:] }
        set { d.set(try? JSONEncoder().encode(newValue), forKey: key("brush_sizes")) }
    }
}
