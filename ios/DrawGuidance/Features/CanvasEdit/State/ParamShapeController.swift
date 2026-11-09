import Combine
import Foundation

/// v45 parametreli şekillerin düzenlenmesi (Android `ParamShapeController`): değer/formül penceresi ve tutamaklar.
@MainActor
final class ParamShapeController: ObservableObject {
    private let store: EditorStore
    private let selected: () -> Stroke?
    @Published private(set) var error: ParamError?

    private var dragKey: String?
    private var dragBox: FRect?
    private var dragRaw: [String: String] = [:]

    init(_ store: EditorStore, selected: @escaping () -> Stroke?) { self.store = store; self.selected = selected }

    func clearError() { error = nil }

    func model(_ stroke: Stroke?) -> ParamModel? { stroke?.shape.flatMap { ShapeFactory.paramModel($0.kind) } }
    func model() -> ParamModel? { model(selected()) }

    func raw(_ stroke: Stroke?) -> [String: String] { stroke?.shape?.params ?? [:] }

    @discardableResult
    func applyFormula(_ text: String) -> Bool {
        guard let stroke = selected(), let model = model(stroke) else { return false }
        switch model.apply(raw(stroke), text) {
        case .ok(let params): error = nil; commit(stroke, params); return true
        case .failed(let e): error = e; return false
        }
    }

    @discardableResult
    func setValue(_ key: String, _ value: Double) -> Bool {
        guard let stroke = selected(), let model = model(stroke), let next = model.setValue(raw(stroke), key, value) else { return false }
        error = nil
        commit(stroke, next)
        return true
    }

    func setText(_ key: String, _ text: String) {
        guard let stroke = selected(), let model = model(stroke) else { return }
        commit(stroke, model.withText(raw(stroke), key, text))
    }

    private func rebuilt(_ stroke: Stroke, _ params: [String: String]) -> Stroke? {
        guard let spec = stroke.shape else { return nil }
        return ShapeFactory.rebuild(stroke, style: spec.style, graph: spec.graph, params: params)
    }

    private func commit(_ stroke: Stroke, _ params: [String: String]) {
        guard let current = store.canvas, params != (stroke.shape?.params ?? [:]), let next = rebuilt(stroke, params) else { return }
        store.commit(current.strokes.map { s in
            guard s.id == stroke.id else { return s }
            var c = next; c.order = s.order; return c
        }, coalesce: "params-\(stroke.id)")
        store.select([stroke.id])
    }

    func handles(_ stroke: Stroke? = nil) -> [(String, P)] {
        guard let s = stroke ?? selected(), let model = model(s), let box = ShapeFactory.box(s) else { return [] }
        return model.unitHandles(raw(s)).map { h in (h.key, P(box.left + h.x * box.width, box.top + h.y * box.height)) }
    }

    func beginDrag(_ point: P, tolerance: Float) -> Bool {
        dragKey = nil
        guard let stroke = selected(),
              let hit = handles(stroke).min(by: { ($0.1 - point).distance < ($1.1 - point).distance }),
              (hit.1 - point).distance <= tolerance * 2.2 else { return false }
        dragKey = hit.0
        dragBox = ShapeFactory.box(stroke)
        dragRaw = raw(stroke)
        return true
    }

    private func dragged(_ to: P) -> [String: String]? {
        guard let key = dragKey, let box = dragBox, let model = model() else { return nil }
        let ux = (to.x - box.left) / max(box.width, 1e-3), uy = (to.y - box.top) / max(box.height, 1e-3)
        return model.dragUnit(dragRaw, key, ux, uy)
    }

    func dragPreview(_ to: P) -> Stroke? {
        guard let stroke = selected(), let next = dragged(to) else { return nil }
        return rebuilt(stroke, next)
    }

    func endDrag(_ to: P) {
        let stroke = selected(), next = dragged(to)
        dragKey = nil
        if let stroke, let next { error = nil; commit(stroke, next) }
    }
}
