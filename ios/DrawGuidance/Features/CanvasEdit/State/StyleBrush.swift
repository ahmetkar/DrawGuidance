import Combine
import Foundation

/// Seçili elemanların biçimi (Android `StyleController`).
@MainActor
final class StyleController {
    private static let ALIGN_MARGIN: Float = 24
    private let store: EditorStore
    private let frame: FrameController
    private let perPage: () -> Bool

    init(_ store: EditorStore, _ frame: FrameController, perPage: @escaping () -> Bool = { false }) {
        self.store = store; self.frame = frame; self.perPage = perPage
    }

    func setTextFill(_ colorHex: String) {
        guard let current = store.canvas else { return }
        let ids = store.selection, hex = colorHex.uppercased()
        var changed = false
        let updated = current.strokes.map { s -> Stroke in
            if !ids.contains(s.id) || s.text == nil || s.colorHex.caseInsensitiveCompare(hex) == .orderedSame { return s }
            changed = true
            var c = s; c.colorHex = hex; return c
        }
        if changed { store.commit(updated, coalesce: "text-fill-" + store.selectionKey()) }
    }

    func setTextOutline(_ colorHex: String?, _ width: Float) {
        guard let current = store.canvas else { return }
        let ids = store.selection
        let color = width > 0 ? colorHex?.uppercased() : nil
        let w: Float = color == nil ? 0 : width.coerceIn(0, 64)
        var changed = false
        let updated = current.strokes.map { s -> Stroke in
            guard ids.contains(s.id), var spec = s.text, !(spec.outlineColorHex == color && spec.outlineWidth == w) else { return s }
            changed = true
            spec.outlineColorHex = color; spec.outlineWidth = w
            var c = s; c.text = spec; return c
        }
        if changed { store.commit(updated, coalesce: "text-outline-" + store.selectionKey()) }
    }

    func fillTargetIds() -> Set<String> {
        guard let current = store.canvas else { return [] }
        let ids = store.selection
        if ids.isEmpty { return [] }
        return Set(current.strokes.filter { ids.contains($0.id) && ElementOps.canFill($0) }.map(\.id))
    }

    func currentFillColor() -> String {
        guard let current = store.canvas else { return "#FFFFFF" }
        let targets = fillTargetIds()
        if targets.isEmpty { return current.pagePaperHex }
        guard let first = current.strokes.first(where: { targets.contains($0.id) }) else { return "#FFFFFF" }
        return ElementOps.fillColorOf(first) ?? "#FFFFFF"
    }

    func applyFill(_ colorHex: String) {
        guard let current = store.canvas else { return }
        let hex = colorHex.uppercased()
        let targets = fillTargetIds()
        if targets.isEmpty {
            frame.setBackgroundColor(hex, coalesce: "fill-background", pageOnly: perPage())
            return
        }
        let ink = Set(current.strokes.filter { targets.contains($0.id) && InkFill.isClosed($0) }.map(\.id))
        let updated = InkFill.apply(ElementOps.fill(current.strokes, targets.subtracting(ink), hex), ink, hex)
        if updated != current.strokes { store.commit(updated, coalesce: "fill-" + targets.sorted().joined(separator: ", ")) }
    }

    func currentFillGradient() -> GradientSpec? {
        guard let current = store.canvas else { return nil }
        let targets = fillTargetIds()
        if targets.isEmpty { return current.pageBackground.gradient }
        return current.strokes.first { targets.contains($0.id) }.flatMap { ElementOps.fillGradientOf($0) }
    }

    func applyFillGradient(_ gradient: GradientSpec?, onBackground: (GradientSpec?) -> Void) {
        guard let current = store.canvas else { return }
        let targets = fillTargetIds()
        if targets.isEmpty { onBackground(gradient); return }
        let updated = ElementOps.fillGradient(current.strokes, targets, gradient)
        if updated != current.strokes { store.commit(updated, coalesce: "gradient-" + targets.sorted().joined(separator: ", ")) }
    }

    func fillSelected(_ colorHex: String) {
        let color = colorHex.uppercased()
        store.mutateSelection { strokes, ids in
            let ink = Set(strokes.filter { ids.contains($0.id) && InkFill.isClosed($0) }.map(\.id))
            return InkFill.apply(ElementOps.fill(strokes, ids.subtracting(ink), color), ink, color)
        }
        store.endCoalesce()
    }

    func recolorSelected(_ colorHex: String) {
        let color = colorHex.uppercased()
        store.mutateSelection { ElementOps.recolor($0, $1, color) }
    }

    func applyStyleToSelection(_ style: ShapeStyle) {
        guard let current = store.canvas else { return }
        let ids = store.selection
        if ids.isEmpty { return }
        var changed = false
        let updated = current.strokes.map { s -> Stroke in
            guard ids.contains(s.id) else { return s }
            var c = s
            if var shape = s.shape {
                if shape.style == style { return s }
                changed = true
                shape.style = style; c.shape = shape; c.colorHex = style.strokeColorHex
            } else if var d = s.diagram {
                if d.style == style { return s }
                changed = true
                d.style = style; c.diagram = d; c.colorHex = style.strokeColorHex
            }
            return c
        }
        if changed { store.commit(updated, coalesce: "shape-style-" + store.selectionKey()) }
    }

    func applyGraphToSelection(_ graph: GraphSpec) {
        guard let current = store.canvas else { return }
        let ids = store.selection
        if ids.isEmpty { return }
        var changed = false
        let updated = current.strokes.map { s -> Stroke in
            guard ids.contains(s.id), let spec = s.shape, let old = spec.graph else { return s }
            var next = graph
            if graph.formula.trimmingCharacters(in: .whitespaces) != old.formula.trimmingCharacters(in: .whitespaces) &&
                !graph.formula.trimmingCharacters(in: .whitespaces).isEmpty { next.preset = "" }
            guard let rebuilt = ShapeFactory.rebuild(s, style: spec.style, graph: next) else { return s }
            changed = true
            return rebuilt
        }
        if changed { store.commit(updated, coalesce: "graph-spec-" + store.selectionKey()) }
    }

    func alignSelection(_ align: TextAlignKind) {
        guard let current = store.canvas else { return }
        let ids = store.selection
        guard !ids.isEmpty, let bounds = ElementOps.selectionBounds(current.strokes, ids) else { return }
        let target: Float
        switch align {
        case .LEFT: target = Self.ALIGN_MARGIN
        case .CENTER: target = (Float(current.width) - bounds.width) / 2
        case .RIGHT: target = Float(current.width) - bounds.width - Self.ALIGN_MARGIN
        }
        let dx = target - bounds.left
        if abs(dx) < 0.01 { return }
        store.commit(ElementOps.translate(current.strokes, ids, dx, 0))
    }
}

/// Fırça durumu (Android `BrushController`).
@MainActor
final class BrushController: ObservableObject {
    private let store: EditorStore
    private unowned let host: EditorHost
    private let palette: ColorPaletteStore?
    private let onDrawingWidth: (Float) -> Void
    private let prefs: CanvasPrefs?

    @Published private(set) var settings = BrushSettings()
    /// Silgiden dönülecek fırça.
    @Published private(set) var drawingBrush: BrushType = .PENCIL
    @Published private(set) var isPickerOpen = false

    private var sizes: [BrushType: Float] = Dictionary(uniqueKeysWithValues: BrushType.allCases.map { ($0, $0.defaultSize) })
    private var tunings = [BrushType: BrushTuning]()
    private var loaded = false

    init(_ store: EditorStore, _ host: EditorHost, palette: ColorPaletteStore?, onDrawingWidth: @escaping (Float) -> Void, prefs: CanvasPrefs? = nil) {
        self.store = store; self.host = host; self.palette = palette; self.onDrawingWidth = onDrawingWidth; self.prefs = prefs
    }

    var savedColors: [String] { palette?.colors ?? [] }

    private func size(_ t: BrushType) -> Float { sizes[t] ?? t.defaultSize }

    private func load() {
        if loaded { return }
        loaded = true
        guard let p = prefs else { return }
        for (k, v) in p.brushTunings { if let t = BrushType(rawValue: k) { tunings[t] = v } }
        for (k, v) in p.brushSizes { if let t = BrushType(rawValue: k) { sizes[t] = v.coerceIn(BrushTuning.MIN_SIZE, BrushTuning.MAX_SIZE) } }
        settings.sizePx = size(settings.brushType)
        settings.tuning = tuningOf(settings.brushType)
    }

    private func tuningOf(_ t: BrushType) -> BrushTuning { tunings[t] ?? BrushTuning() }

    private func persist() {
        guard let p = prefs else { return }
        p.brushTunings = Dictionary(uniqueKeysWithValues: tunings.map { ($0.key.rawValue, $0.value) })
        p.brushSizes = Dictionary(uniqueKeysWithValues: sizes.filter { $0.value != $0.key.defaultSize }.map { ($0.key.rawValue, $0.value) })
    }

    func adaptTo(_ kind: CanvasKind) {
        load()
        let allowed = pickerBrushTypes(kind)
        if !allowed.contains(settings.brushType) {
            let type = defaultBrush(kind)
            settings.brushType = type; settings.sizePx = size(type); settings.tuning = tuningOf(type)
            drawingBrush = type
        }
    }

    func restore(_ kind: CanvasKind, _ type: BrushType?, _ colorHex: String?) {
        load()
        var s = settings
        if let type, type != .ERASER, pickerBrushTypes(kind).contains(type) {
            s.brushType = type; s.sizePx = size(type); s.tuning = tuningOf(type)
            drawingBrush = type
            onDrawingWidth(size(type))
        }
        if let colorHex, ColorPaletteStore.isHex(colorHex) { s.colorHex = colorHex.uppercased() }
        settings = s
    }

    func openPicker() { if host.mode.allowsDrawTools { isPickerOpen = true } }

    func brushButton() {
        if !host.mode.allowsDrawTools { return }
        if settings.brushType == .ERASER || host.mode != .DRAW { select(drawingBrush) } else { openPicker() }
    }

    func pickerSelection() -> BrushType { settings.brushType != .ERASER ? settings.brushType : drawingBrush }

    func closePicker() { isPickerOpen = false }

    func select(_ type: BrushType) {
        if type == .IMAGE { return }
        store.select([])
        load()
        sizes[settings.brushType] = settings.sizePx
        var s = settings
        s.brushType = type; s.sizePx = size(type); s.tuning = tuningOf(type)
        settings = s
        if type != .ERASER {
            drawingBrush = type
            onDrawingWidth(size(type))
        }
        isPickerOpen = false
        host.setMode(.DRAW)
    }

    func eraserSize() -> Float { size(.ERASER) }

    func toggleEraser() {
        if !host.mode.allowsDrawTools { return }
        select(settings.brushType == .ERASER ? drawingBrush : .ERASER)
    }

    func updateSize(_ sizePx: Float) {
        let sz = sizePx.coerceIn(BrushTuning.MIN_SIZE, BrushTuning.MAX_SIZE)
        settings.sizePx = sz
        sizes[settings.brushType] = sz
        if settings.brushType != .ERASER { onDrawingWidth(sz) }
        persist()
    }

    func updateTuning(_ change: (BrushTuning) -> BrushTuning) {
        load()
        var next = change(settings.tuning)
        next.speed = next.speed.coerceIn(0.1, 1)
        next.stabilizerStrength = next.stabilizerStrength.coerceIn(0.05, 1)
        next.spacing = next.spacing?.coerceIn(0.02, 3)
        next.tiltWidth = next.tiltWidth?.coerceIn(0, 2)
        next.tiltLight = next.tiltLight?.coerceIn(0, 1)
        next.tiltThreshold = next.tiltThreshold.coerceIn(0, 60)
        tunings[settings.brushType] = next
        settings.tuning = next
        persist()
    }

    func resetTuning() { updateTuning { _ in BrushTuning() } }

    func updateColor(_ colorHex: String) { settings.colorHex = colorHex.uppercased() }
    func updateOpacity(_ opacity: Float) { settings.opacity = opacity }
    func updateFlow(_ flow: Float) { settings.flow = flow.coerceIn(0.05, 1) }
    func updateStrength(_ strength: Float) { settings.strength = strength.coerceIn(0.1, 1) }

    func saveCurrentColor() { palette?.add(settings.colorHex) }
    func saveColor(_ colorHex: String) { palette?.add(colorHex) }
    func removeSavedColor(_ colorHex: String) { palette?.remove(colorHex) }

    /// Yeni şekil, diyagram ve yazıların rengi: silgi seçiliyse koyu gri.
    func inkColor() -> String { settings.brushType == .ERASER ? "#1E1E1E" : settings.colorHex }
}
