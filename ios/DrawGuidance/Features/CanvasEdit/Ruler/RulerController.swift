import Foundation

/// v40 cetvel/kalıp oturumu.
enum GuideSession { case NONE, RULER, TEMPLATE_PICK, TEMPLATE }

/// v40 hatırlanan cetvel ayarları.
struct RulerSettings: Equatable {
    var type: RulerType = .STRAIGHT
    var unit: RulerUnit = .CM
    var scale = 1
}

protocol RulerSettingsStore: AnyObject {
    func load() -> RulerSettings
    func save(_ settings: RulerSettings)
}

/// v40 cetvel ayarları cihaz tercihlerinde (Android `RulerPrefs`).
final class RulerPrefs: RulerSettingsStore {
    private let defaults: UserDefaults
    init(_ defaults: UserDefaults = .standard) { self.defaults = defaults }
    func load() -> RulerSettings {
        let scale = defaults.object(forKey: "ruler.scale") as? Int ?? 1
        return RulerSettings(type: .parse(defaults.string(forKey: "ruler.type")), unit: .parse(defaults.string(forKey: "ruler.unit")),
                             scale: RulerUnits.Scales.contains(scale) ? scale : 1)
    }
    func save(_ s: RulerSettings) {
        defaults.set(s.type.rawValue, forKey: "ruler.type")
        defaults.set(s.unit.rawValue, forKey: "ruler.unit")
        defaults.set(s.scale, forKey: "ruler.scale")
    }
}

/// v40 cetvel ve şekil kalıbı (Android `RulerController`): belgeye ve geri alma geçmişine girmeyen yarı saydam katman.
@MainActor
final class RulerController: ObservableObject {
    @Published private(set) var session: GuideSession = .NONE
    @Published private(set) var settings = RulerSettings()
    @Published private(set) var pose: RulerPose?
    @Published private(set) var template: TemplateGhost?
    @Published private(set) var locked = false
    @Published var libraryOpen = false

    private let elements: () -> ElementIndex?
    private var store: RulerSettingsStore?
    private var shapeCache: RulerShape?
    private var guideCache: (key: AnyHashable, guide: StrokeGuide)?

    init(elements: @escaping () -> ElementIndex?) { self.elements = elements }

    func attach(_ settingsStore: RulerSettingsStore) {
        if store != nil { return }
        store = settingsStore
        settings = settingsStore.load()
    }

    var active: Bool { session != .NONE }

    func shape() -> RulerShape? {
        guard let p = pose, session == .RULER else { return nil }
        if let c = shapeCache, c.pose == p { return c }
        let s = RulerShape(p)
        shapeCache = s
        return s
    }

    /// v43 çizgi kalıbın üzerinde mi (noktaların en az %60'ı genişletilmiş kutuda).
    func onTemplate(_ xs: [Float], _ ys: [Float]) -> Bool {
        guard session == .TEMPLATE, let t = template, !xs.isEmpty else { return false }
        let b = t.bounds
        let m = max(b.width, b.height) * 0.08 + 4
        var inside = 0
        for i in xs.indices where xs[i] >= b.left - m && xs[i] <= b.right + m && ys[i] >= b.top - m && ys[i] <= b.bottom + m { inside += 1 }
        return Float(inside) >= Float(xs.count) * 0.6
    }

    func guide() -> StrokeGuide? {
        switch session {
        case .RULER:
            guard let p = pose, let s = shape() else { return nil }
            let key = AnyHashable("r\(p.type)\(p.cx),\(p.cy),\(p.angle),\(p.length),\(p.thickness),\(p.minor)")
            if let c = guideCache, c.key == key { return c.guide }
            let g = EdgesGuide(s.edges, captureAll: p.type == .COMPASS)
            guideCache = (key, g)
            return g
        case .TEMPLATE:
            guard let t = template else { return nil }
            let key = AnyHashable(ObjectIdentifier(t))
            if let c = guideCache, c.key == key { return c.guide }
            let g = EdgesGuide(t.edges)
            guideCache = (key, g)
            return g
        default: return nil
        }
    }

    func openRuler(center: P, visibleWidth: Float, thickness: Float) {
        template = nil
        let type = settings.type
        if var current = pose, session == .RULER { current.type = type; pose = current }
        else {
            pose = RulerPose(type: type, cx: center.x, cy: center.y, angle: 0,
                             length: (visibleWidth * 0.6).coerceIn(RulerPose.MIN_LENGTH, RulerPose.MAX_LENGTH), thickness: thickness)
        }
        session = .RULER
    }

    func openTemplate() {
        template = nil
        locked = false
        session = .TEMPLATE_PICK
    }

    func finish() {
        session = .NONE
        template = nil
        shapeCache = nil
        guideCache = nil
    }

    func setType(_ type: RulerType) {
        var s = settings; s.type = type
        updateSettings(s)
        pose?.type = type
    }

    func setUnit(_ unit: RulerUnit) { var s = settings; s.unit = unit; updateSettings(s) }
    func setScale(_ scale: Int) { var s = settings; s.scale = max(scale, 1); updateSettings(s) }

    private func updateSettings(_ s: RulerSettings) {
        settings = s
        store?.save(s)
    }

    func setPose(_ p: RulerPose) { if session == .RULER { pose = p } }

    func setTemplate(_ ghost: TemplateGhost) {
        template = ghost
        session = .TEMPLATE
    }

    func toggleLock() { locked.toggle() }

    func pickAt(_ point: P, tolerance: Float) -> Bool {
        guard session == .TEMPLATE_PICK, let index = elements(),
              let element = ElementOps.hitTest(index, point, tolerance: tolerance) else { return false }
        let ids = Set(element.strokeIds)
        let strokes = index.strokes.filter { ids.contains($0.id) && !$0.hidden }
        guard let ghost = TemplateGhost.of(TemplateGhost.contoursOf(strokes), colorHex: strokes.first.map(colorOf) ?? "#3B82F6") else { return false }
        setTemplate(ghost)
        return true
    }

    func placeLibrary(_ shapeId: String, center: P, size: Float) -> Bool {
        let half = size / 2
        let a = ShapeFactory.aspect(shapeId)
        let aspect = a > 0 ? a : 1
        let hw = aspect >= 1 ? half : half * aspect
        let hh = aspect >= 1 ? half / aspect : half
        guard let stroke = ShapeFactory.build(shapeId, P(center.x - hw, center.y - hh), P(center.x + hw, center.y + hh), style: ShapeStyle(),
                                              graph: nil, id: "ruler-template", order: 0, opacity: 1),
              let ghost = TemplateGhost.of(TemplateGhost.contoursOf([stroke])) else { return false }
        setTemplate(ghost)
        return true
    }

    private func colorOf(_ s: Stroke) -> String { s.shape?.style.strokeColorHex ?? s.colorHex }
}
