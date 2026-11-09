import Combine
import Foundation

/// Formüller: katalogdan şablon seçip tuvale yerleştirme ve yuvalarını tuvalin üstünde düzenleme
/// (Android `FormulaController`). Formül yazıdır: renk, boyut ve dönüşümler yazı gibi çalışır.
@MainActor
final class FormulaController: ObservableObject {
    private let store: EditorStore
    private unowned let host: EditorHost
    private let brush: () -> BrushController

    @Published private(set) var isPickerOpen = false
    @Published private(set) var template: FormulaTemplate?
    /// Düzenlenen formül (nil: kapalı) ve odaktaki yuva.
    @Published private(set) var editing: String?
    @Published private(set) var activeSlot: String?
    /// v45 formül sayılarla hesaplanamadıysa hata.
    @Published private(set) var evalError: MathError?

    private var defaultSize: Float = 56
    private static let DECIMAL_COMMA: Set<String> = ["tr", "ru"]

    init(_ store: EditorStore, _ host: EditorHost, brush: @escaping () -> BrushController) {
        self.store = store; self.host = host; self.brush = brush
    }

    /// Seçili tek formül.
    var selected: Stroke? {
        let ids = store.selection
        guard ids.count == 1 else { return nil }
        return store.canvas?.strokes.first { ids.contains($0.id) && $0.brushType == .FORMULA }
    }

    func adaptTo(_ width: Int, _ height: Int) { defaultSize = (Float(min(width, height)) / 20).coerceIn(28, 140) }

    func openPicker() { isPickerOpen = true }
    func closePicker() { isPickerOpen = false }

    func selectTemplate(_ value: FormulaTemplate) {
        template = value
        isPickerOpen = false
        host.startAction(.FORMULA)
    }

    /// Sürüklenen kutuya sığan boyutta ekler; kısa sürüklemede dokunulan yere varsayılan boyutta.
    func add(_ gestureId: String, _ start: P, _ end: P) {
        guard let t = template else { return }
        let layout = FormulaRenderer.quadAt(t.source, 100, 0, 0)
        guard layout.count >= 4 else { return }
        let w100 = max(layout[1].x - layout[0].x, 1e-3), h100 = max(layout[3].y - layout[0].y, 1e-3)
        let dx = abs(end.x - start.x), dy = abs(end.y - start.y)
        let tap = dx < 24 && dy < 24
        let size = tap ? defaultSize : max(12, min(dx / w100, dy < 24 ? Float.greatestFiniteMagnitude : dy / h100) * 100)
        let left = tap ? start.x - w100 * size / 200 : min(start.x, end.x)
        let top = tap ? start.y - h100 * size / 200 : min(start.y, end.y)
        insert(gestureId, t, size, left, top)
    }

    func addAt(_ gestureId: String, _ center: P) { add(gestureId, center, center) }

    private func insert(_ gestureId: String, _ t: FormulaTemplate, _ size: Float, _ left: Float, _ top: Float) {
        guard let current = store.canvas else { return }
        let quad = FormulaRenderer.quadAt(t.source, size, left, top).map { StrokePoint(ContentCodec.quantize($0.x), ContentCodec.quantize($0.y)) }
        let id = StableId.from("\(gestureId)-formula")
        let stroke = Stroke(id: id, points: quad, colorHex: brush().inkColor(), brushType: .FORMULA, sizePx: size, opacity: 1,
                            order: current.strokes.count, formula: FormulaSpec(source: Formula.atomize(t.source), templateId: t.id))
        store.commit(current.strokes + [stroke])
        host.inserted([id])
    }

    func edit(_ slot: String? = nil) {
        guard let f = ensureAtomized() else { return }
        editing = f.id
        activeSlot = slot ?? Formula.slots(f.formula?.source ?? "").first?.name
    }

    private func ensureAtomized() -> Stroke? {
        guard store.selection.count == 1, let f = store.selectedStroke({ $0.brushType == .FORMULA }) else { return nil }
        guard let src = f.formula?.source else { return f }
        if Formula.isAtomized(src) { return f }
        update(f.id) { Formula.atomize($0) }
        return store.canvas?.strokes.first { $0.id == f.id } ?? f
    }

    func editSource() {
        guard store.selection.count == 1, let f = store.selectedStroke({ $0.brushType == .FORMULA }) else { return }
        editing = f.id
        activeSlot = nil
    }

    func nextSlot() {
        guard let f = selected else { closeEditor(); return }
        let slots = Formula.slots(f.formula?.source ?? "").map(\.name)
        guard let i = slots.firstIndex(where: { $0 == activeSlot }), i + 1 < slots.count else { closeEditor(); return }
        activeSlot = slots[i + 1]
    }

    func closeEditor() {
        if editing == nil { return }
        editing = nil
        activeSlot = nil
        store.endCoalesce()
    }

    func focusSlot(_ name: String?) { activeSlot = name }

    @discardableResult
    func tapSlot(_ point: P) -> Bool {
        let hit = selected.flatMap { FormulaRenderer.slotAt($0, point.x, point.y) } != nil
        let f = hit ? ensureAtomized() : selected
        guard let s = f, let slot = FormulaRenderer.slotAt(s, point.x, point.y) else { closeEditor(); return false }
        edit(slot)
        return true
    }

    func onModeChange(_ to: EditMode) { if to != .SELECT && to != .MOVE { closeEditor() } }

    func setSlot(_ name: String, _ value: String) {
        guard let id = editing else { return }
        update(id) { Formula.setSlot($0, name, value) }
    }

    func setSource(_ source: String) {
        guard let id = editing else { return }
        update(id) { _ in Formula.atomize(source) }
    }

    private func update(_ id: String, _ block: (String) -> String) {
        guard let current = store.canvas else { return }
        var changed = false
        let updated = current.strokes.map { s -> Stroke in
            guard s.id == id, let spec = s.formula else { return s }
            let eval = FormulaEval.annotate(block(spec.source), undefined: I18n.tr("tanımsız"),
                                            decimalComma: Self.DECIMAL_COMMA.contains(I18n.language))
            evalError = eval.error
            let next = eval.source
            if next == spec.source { return s }
            changed = true
            var c = s
            c.formula = FormulaSpec(source: next, templateId: spec.templateId)
            c.points = FormulaRenderer.refit(s, next).map { StrokePoint(ContentCodec.quantize($0.x), ContentCodec.quantize($0.y)) }
            return c
        }
        if changed { store.commit(updated, coalesce: "formula-\(id)") }
    }

    func onContentReplaced() {
        guard let id = editing else { return }
        if !store.strokes.contains(where: { $0.id == id }) { closeEditor() }
    }

    func onSelectionChanged(_ ids: Set<String>) {
        guard let id = editing else { return }
        if !ids.contains(id) { closeEditor() }
    }
}
