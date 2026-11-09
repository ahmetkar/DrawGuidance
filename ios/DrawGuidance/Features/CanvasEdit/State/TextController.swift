import Combine
import Foundation

/// v29 yazı biçiminde yalnız kullanıcının değiştirdiği alanlar uygulanır (Android `TextStyleMerge`).
enum TextStyleMerge {
    static func boxStyle(_ box: TextBox?) -> TextBox? {
        guard var b = box else { return nil }
        b.width = nil; b.height = nil; b.balloon = nil; b.effects = nil
        return b.orNull()
    }

    static func merge(_ target: TextSettings, _ before: TextSettings, _ after: TextSettings) -> TextSettings {
        let t = target.spec, b = before.spec, a = after.spec
        func pick<T: Equatable>(_ f: (TextSpec) -> T) -> T { f(a) != f(b) ? f(a) : f(t) }
        let style = boxStyle(a.box) != boxStyle(b.box) ? boxStyle(a.box) : boxStyle(t.box)
        let effects = mergeEffects(t.box?.effects, b.box?.effects, a.box?.effects)
        var box = style ?? TextBox()
        box.width = t.box?.width; box.height = t.box?.height; box.balloon = t.box?.balloon; box.effects = effects
        let outlineChanged = a.outlineColorHex != b.outlineColorHex || a.outlineWidth != b.outlineWidth
        var spec = t
        spec.font = pick(\.font); spec.bold = pick(\.bold); spec.italic = pick(\.italic); spec.underline = pick(\.underline)
        spec.align = pick(\.align); spec.paragraph = pick(\.paragraph)
        spec.outlineColorHex = outlineChanged ? a.outlineColorHex : t.outlineColorHex
        spec.outlineWidth = outlineChanged ? a.outlineWidth : t.outlineWidth
        spec.box = box.orNull()
        return TextSettings(spec: spec, sizePx: after.sizePx != before.sizePx ? after.sizePx : target.sizePx)
    }

    private static func mergeEffects(_ target: TextEffects?, _ before: TextEffects?, _ after: TextEffects?) -> TextEffects? {
        if before == after { return target }
        let t = target ?? TextEffects(), b = before ?? TextEffects(), a = after ?? TextEffects()
        let shadowChanged = a.shadowHex != b.shadowHex || a.shadowDx != b.shadowDx || a.shadowDy != b.shadowDy || a.shadowBlur != b.shadowBlur
        let merged = TextEffects(shadowHex: shadowChanged ? a.shadowHex : t.shadowHex, shadowDx: shadowChanged ? a.shadowDx : t.shadowDx,
                                 shadowDy: shadowChanged ? a.shadowDy : t.shadowDy, shadowBlur: shadowChanged ? a.shadowBlur : t.shadowBlur,
                                 gradient: a.gradient != b.gradient ? a.gradient : t.gradient, caps: a.caps != b.caps ? a.caps : t.caps,
                                 letterSpacing: a.letterSpacing != b.letterSpacing ? a.letterSpacing : t.letterSpacing)
        return merged.isEmpty ? nil : merged
    }
}

/// Yazı elemanı: tuvalin içinde yazma ve yazı tipi ayarları (Android `TextController`).
@MainActor
final class TextController: ObservableObject {
    static let DRAFT_ID = "__text_draft__"
    static let MIN_BOX: Float = 24
    static let MAX_BOX: Float = 20000
    static let MIN_TEXT_SIZE: Float = 8
    static let MAX_TEXT_SIZE: Float = 400

    private let store: EditorStore
    private let brush: () -> BrushController
    private let autoWrap: () -> Bool
    private let onAdded: (String) -> Void

    @Published private(set) var defaults = TextSettings()
    @Published private(set) var settings = TextSettings()
    @Published private(set) var draft: TextDraft?
    private var draftText = ""

    init(_ store: EditorStore, brush: @escaping () -> BrushController, autoWrap: @escaping () -> Bool = { false }, onAdded: @escaping (String) -> Void = { _ in }) {
        self.store = store; self.brush = brush; self.autoWrap = autoWrap; self.onAdded = onAdded
    }

    static func normalize(_ spec: TextSpec) -> TextSpec {
        var s = spec
        s.content = ""
        if var b = s.box { b.width = nil; b.height = nil; b.balloon = nil; s.box = b.orNull() }
        return s
    }

    static func mergeBox(_ style: TextBox?, _ own: TextBox?) -> TextBox? {
        var b = style ?? TextBox()
        b.width = own?.width; b.height = own?.height; b.balloon = own?.balloon
        return b.orNull()
    }

    private func setDefaults(_ value: TextSettings) {
        defaults = value
        if draft?.editingId == nil { settings = value }
    }

    func adaptTo(_ width: Int, _ height: Int) {
        var d = defaults
        d.sizePx = (Float(min(width, height)) / 22).coerceIn(24, 120).rounded()
        setDefaults(d)
    }

    func restore(_ spec: TextSpec, sizePx: Float? = nil) {
        setDefaults(TextSettings(spec: Self.normalize(spec), sizePx: sizePx?.coerceIn(Self.MIN_TEXT_SIZE, Self.MAX_TEXT_SIZE) ?? defaults.sizePx))
    }

    func updateDraftText(_ text: String) { draftText = text }

    func commitOpenDraft() { if draft != nil { confirm(draftText) } }

    func onModeChange(_ to: EditMode) { if to != .TEXT && draft != nil { commitOpenDraft() } }

    func onContentReplaced() {
        guard let id = draft?.editingId else { return }
        if !store.strokes.contains(where: { $0.id == id }) { draft = nil }
    }

    func beginAt(_ x: Float, _ y: Float, tolerance: Float) {
        if draft != nil { commitOpenDraft(); return }
        guard let current = store.canvas else { return }
        if let hit = current.strokes.last(where: { $0.brushType == .TEXT && ElementOps.distanceToShape(P(x, y), $0) <= tolerance }) {
            edit(hit); return
        }
        draftText = ""
        draft = TextDraft(anchor: P(x, y), editingId: nil, content: "")
    }

    func beginBox(_ from: P, _ to: P, tolerance: Float) {
        if draft != nil { commitOpenDraft(); return }
        let size = settings.sizePx
        let width = abs(to.x - from.x)
        if width < size * 2 && abs(to.y - from.y) < size { beginAt(from.x, from.y, tolerance: tolerance); return }
        draftText = ""
        draft = TextDraft(anchor: P(min(from.x, to.x), min(from.y, to.y) + size * 0.7), editingId: nil, content: "",
                          boxWidth: width.coerceIn(size * 3, Self.MAX_BOX))
    }

    func moveDraft(_ dx: Float, _ dy: Float) {
        guard var d = draft, dx != 0 || dy != 0 else { return }
        if let id = d.editingId {
            guard let current = store.canvas, current.strokes.contains(where: { $0.id == id }) else { return }
            store.commit(ElementOps.translate(current.strokes, [id], dx, dy))
        }
        d.anchor = d.anchor + P(dx, dy)
        d.content = draftText
        draft = d
    }

    func setDraftParagraph(_ paragraph: Bool) {
        guard var d = draft else { return }
        guard let id = d.editingId else {
            let width: Float? = paragraph ? (d.boxWidth ?? paragraphWidth(d.anchor.x)) : nil
            if width != d.boxWidth { d.boxWidth = width; d.content = draftText; draft = d }
            return
        }
        guard let current = store.canvas, let old = current.strokes.first(where: { $0.id == id }), let text = old.text else { return }
        let width: Float? = paragraph ? (text.box?.width ?? paragraphWidth(old.points[0].x)) : nil
        if width == text.box?.width { return }
        var spec = text
        var box = text.box ?? TextBox()
        box.width = width
        spec.box = box.orNull()
        var updated = old
        updated.text = spec
        updated.points = quantized(TextRenderer.refit(old, spec, old.sizePx))
        store.commit(current.strokes.map { $0.id == id ? updated : $0 }, coalesce: "text-box-\(id)")
        d.boxWidth = width; d.content = draftText
        draft = d
    }

    private func quantized(_ pts: [StrokePoint]) -> [StrokePoint] {
        pts.map { StrokePoint(ContentCodec.quantize($0.x), ContentCodec.quantize($0.y)) }
    }

    private func edgeMargin(_ canvasWidth: Float) -> Float { canvasWidth * 0.04 }

    private func paragraphWidth(_ left: Float) -> Float {
        guard let wi = store.canvas?.width else { return Self.MIN_BOX * 20 }
        let w = Float(wi)
        let size = settings.sizePx
        return max(size * 4, min(w - edgeMargin(w) - left, w * 0.95))
    }

    private func wrapped(_ spec: TextSpec, _ sizePx: Float, _ left: Float, stretch: Float = 1) -> TextSpec {
        if !autoWrap() || spec.box?.width != nil || spec.box?.balloon != nil || spec.content.isEmpty { return spec }
        guard let cw = store.canvas?.width else { return spec }
        let canvasWidth = Float(cw)
        let room = (canvasWidth - edgeMargin(canvasWidth) - left) / max(stretch, 1e-3)
        if room < sizePx * 3 { return spec }
        if TextRenderer.measure(spec, sizePx).0 <= room { return spec }
        var s = spec
        var box = s.box ?? TextBox()
        box.width = room
        s.box = box
        return s
    }

    private func wrappedEdit(_ old: Stroke, _ spec: TextSpec, _ sizePx: Float) -> TextSpec {
        let q = old.points
        guard let oldSpec = old.text else { return spec }
        if q.count < 4 || abs(q[1].y - q[0].y) > 0.5 || q[1].x <= q[0].x { return spec }
        let stretch = (q[1].x - q[0].x) / max(TextRenderer.measure(oldSpec, old.sizePx).0, 1e-3)
        return wrapped(spec, sizePx, q[0].x, stretch: stretch)
    }

    private func newSpec(_ d: TextDraft, _ s: TextSettings, _ content: String) -> TextSpec {
        var base = s.spec
        base.content = content
        if let w = d.boxWidth { var box = base.box ?? TextBox(); box.width = w; base.box = box }
        return wrapped(base, s.sizePx, d.anchor.x)
    }

    func editSelected() {
        guard let current = store.canvas, let text = current.strokes.first(where: { store.selection.contains($0.id) && $0.brushType == .TEXT }) else { return }
        edit(text)
    }

    private func edit(_ stroke: Stroke) {
        guard let spec = stroke.text else { return }
        settings = TextSettings(spec: Self.normalize(spec), sizePx: stroke.sizePx)
        draftText = spec.content
        draft = TextDraft(anchor: P(stroke.points[0].x, stroke.points[0].y), editingId: stroke.id, content: spec.content, boxWidth: spec.box?.width)
    }

    @discardableResult
    func setColor(_ colorHex: String) -> Bool {
        guard let id = draft?.editingId, let current = store.canvas else { return false }
        let hex = colorHex.uppercased()
        var changed = false
        let updated = current.strokes.map { s -> Stroke in
            if s.id != id || s.colorHex.caseInsensitiveCompare(hex) == .orderedSame { return s }
            changed = true
            var c = s; c.colorHex = hex; return c
        }
        if changed { store.commit(updated, coalesce: "text-fill-\(id)") }
        return true
    }

    func cancel() {
        draft = nil
        settings = defaults
    }

    private func editedSpec(_ old: Stroke, _ s: TextSettings, _ content: String) -> TextSpec {
        var spec = s.spec
        spec.content = content
        spec.box = Self.mergeBox(s.spec.box, old.text?.box ?? s.spec.box)
        return wrappedEdit(old, spec, s.sizePx)
    }

    /// v28 yazarken önizleme.
    func previewStroke(_ content: String) -> Stroke? {
        guard let d = draft, let current = store.canvas else { return nil }
        let s = settings
        if let eid = d.editingId {
            guard let old = current.strokes.first(where: { $0.id == eid }) else { return nil }
            let spec = editedSpec(old, s, content)
            var c = old
            c.text = spec; c.sizePx = s.sizePx; c.points = TextRenderer.refit(old, spec, s.sizePx)
            return c
        }
        let spec = newSpec(d, s, content)
        return Stroke(id: Self.DRAFT_ID, points: TextRenderer.quadAt(spec, s.sizePx, d.anchor.x, d.anchor.y - s.sizePx * 0.7),
                      colorHex: brush().inkColor(), brushType: .TEXT, sizePx: s.sizePx, opacity: 1, order: 0, text: spec)
    }

    func confirm(_ content: String) {
        guard let d = draft else { return }
        draft = nil
        let s = settings
        settings = defaults
        guard let current = store.canvas else { return }
        let blank = content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        if let eid = d.editingId {
            guard let old = current.strokes.first(where: { $0.id == eid }) else { return }
            if blank { store.commit(ElementOps.delete(current.strokes, [old.id])); return }
            let spec = editedSpec(old, s, content)
            var updated = old
            updated.text = spec; updated.sizePx = s.sizePx
            updated.points = quantized(TextRenderer.refit(old, spec, s.sizePx))
            store.commit(current.strokes.map { $0.id == old.id ? updated : $0 })
            return
        }
        if blank { return }
        let spec = newSpec(d, s, content)
        let points = quantized(TextRenderer.quadAt(spec, s.sizePx, d.anchor.x, d.anchor.y - s.sizePx * 0.7))
        let stroke = Stroke(id: UUID().uuidString.lowercased(), points: points, colorHex: brush().inkColor(), brushType: .TEXT,
                            sizePx: s.sizePx, opacity: 1, order: current.strokes.count, text: spec)
        store.commit(current.strokes + [stroke])
        store.select([stroke.id])
        onAdded(stroke.id)
    }

    func updateSettings(_ value: TextSettings, before: TextSettings? = nil) {
        let bf = before ?? settings
        let after = TextSettings(spec: Self.normalize(value.spec), sizePx: value.sizePx.coerceIn(Self.MIN_TEXT_SIZE, Self.MAX_TEXT_SIZE))
        let base = TextSettings(spec: Self.normalize(bf.spec), sizePx: bf.sizePx.coerceIn(Self.MIN_TEXT_SIZE, Self.MAX_TEXT_SIZE))
        if after == base { return }
        defaults = TextStyleMerge.merge(defaults, base, after)
        settings = draft?.editingId != nil ? TextStyleMerge.merge(settings, base, after) : defaults
        if draft != nil { return }
        guard let current = store.canvas else { return }
        let ids = store.selection
        if ids.isEmpty { return }
        var changed = false
        let updated = current.strokes.map { s -> Stroke in
            guard ids.contains(s.id), s.brushType == .TEXT, let text = s.text else { return s }
            let merged = TextStyleMerge.merge(TextSettings(spec: text, sizePx: s.sizePx), base, after)
            let content = merged.spec.paragraph != text.paragraph ? TextLists.apply(text.content, merged.spec.paragraph) : text.content
            var spec = merged.spec
            spec.content = content
            if spec == text && s.sizePx == merged.sizePx { return s }
            changed = true
            var c = s
            c.text = spec; c.sizePx = merged.sizePx; c.points = quantized(TextRenderer.refit(s, spec, merged.sizePx))
            return c
        }
        if changed { store.commit(updated, coalesce: "text-style-" + store.selectionKey()) }
    }

    func setBoxSize(_ width: Float?, _ height: Float?, setWidth: Bool, setHeight: Bool, coalesce: Bool = true) {
        guard let current = store.canvas else { return }
        let ids = store.selection
        if ids.isEmpty { return }
        var changed = false
        let updated = current.strokes.map { s -> Stroke in
            guard ids.contains(s.id), s.brushType == .TEXT, let text = s.text else { return s }
            var box = text.box ?? TextBox()
            if setWidth { box.width = width?.coerceIn(Self.MIN_BOX, Self.MAX_BOX) }
            if setHeight { box.height = height?.coerceIn(Self.MIN_BOX, Self.MAX_BOX) }
            let nb = box.orNull()
            if nb == text.box { return s }
            changed = true
            var spec = text
            spec.box = nb
            var c = s
            c.text = spec; c.points = quantized(TextRenderer.refit(s, spec, s.sizePx))
            return c
        }
        if changed { store.commit(updated, coalesce: coalesce ? "text-box-" + store.selectionKey() : nil) }
    }
}
