import Foundation

/// v38 kalemle çizilenin yapay zekâ önerileri (Android `HandwritingController`): yazı, şekil ve çizgi önerileri ayrı
/// algoritmalarla, ayrı balonlarda; her biri tek geri alma adımıyla uygulanır.
@MainActor
final class HandwritingController: ObservableObject {
    private let store: EditorStore
    private unowned let host: EditorHost
    private let enabled: () -> Bool
    private weak var templates: TemplateSource?
    private let textEnabled: () -> Bool
    private let shapeEnabled: () -> Bool
    private let selectionEnabled: () -> Bool
    private let lineEnabled: () -> Bool

    private let textEngine = TextSuggester()
    private let shapeEngine: ShapeSuggester
    private let lineEngine = LineSuggester()

    @Published private(set) var text: TextSuggestion?
    @Published private(set) var shape: ShapeSuggestion?
    @Published private(set) var line: LineSuggestion?

    private var job: Task<Void, Never>?
    private var selectionJob: Task<Void, Never>?
    private var lastInk: String?
    private var autoKey: String?
    private var quietKeys: [String] = []
    private var lineSettled = OrderedIdSet()
    private var settled = OrderedIdSet()

    static let PAUSE_MS: UInt64 = 750
    static let SELECTION_PAUSE_MS: UInt64 = 450

    init(_ store: EditorStore, _ host: EditorHost, enabled: @escaping () -> Bool, templates: TemplateSource? = nil,
         textEnabled: @escaping () -> Bool = { true }, shapeEnabled: @escaping () -> Bool = { true },
         selectionEnabled: @escaping () -> Bool = { false }, lineEnabled: @escaping () -> Bool = { true }) {
        self.store = store; self.host = host; self.enabled = enabled; self.templates = templates
        self.textEnabled = textEnabled; self.shapeEnabled = shapeEnabled; self.selectionEnabled = selectionEnabled; self.lineEnabled = lineEnabled
        shapeEngine = ShapeSuggester(templates)
    }

    /// Ekleme sırasını koruyan, sınırlı kimlik kümesi.
    struct OrderedIdSet {
        private(set) var order: [String] = []
        private(set) var set = Set<String>()
        mutating func add(_ ids: [String], limit: Int = 2000) {
            for id in ids where set.insert(id).inserted { order.append(id) }
            if order.count > limit { let drop = order.count - limit; order.prefix(drop).forEach { set.remove($0) }; order.removeFirst(drop) }
        }
        func contains(_ id: String) -> Bool { set.contains(id) }
    }

    nonisolated private static func isWritingInk(_ stroke: Stroke) -> Bool {
        stroke.brushType.isErasable && !stroke.points.isEmpty && stroke.brushType.suggestPolicy != .NONE
    }

    private func canvasW() -> Float { Float(store.canvas?.width ?? 0) }
    private func canvasH() -> Float { Float(store.canvas?.height ?? 0) }

    func canSuggest(_ strokes: [Stroke]) -> Bool {
        let ink = strokes.filter(Self.isWritingInk)
        if ink.count >= SuggestRules.LINE_MIN { return true }
        guard ink.count == 1 else { return false }
        let one = ink[0], w = canvasW(), h = canvasH()
        return SingleInk.bigLine(one, w, h) || (textEngine.isWritingPen(one) && SingleInk.bigText(one, w, h) && !SingleInk.geometric(one))
    }

    /// v43 öneri dışı çizgi: bir daha hiçbir öneride dikkate alınmaz.
    func exclude(_ stroke: Stroke) {
        settled.add([stroke.id]); lineSettled.add([stroke.id])
        if lastInk == stroke.id { lastInk = nil }
        job?.cancel()
        clearAll()
    }

    func onInk(_ stroke: Stroke) {
        if !enabled() { return }
        if !Self.isWritingInk(stroke) { reset(); return }
        lastInk = stroke.id
        clearAll()
        job?.cancel()
        job = Task { [weak self] in
            try? await Task.sleep(nanoseconds: Self.PAUSE_MS * 1_000_000)
            if Task.isCancelled { return }
            await self?.recognize()
        }
    }

    private struct Found { let text: TextSuggestion?; let shape: ShapeSuggestion?; let line: LineSuggestion? }

    nonisolated private static func key(_ kind: String, _ ids: [String]) -> String { kind + ":" + ids.sorted().joined(separator: ",") }

    private func recognize() async {
        guard let current = store.canvas else { return }
        let all = current.strokes
        guard let last = all.last(where: { $0.id == lastInk }) else { return }
        let wantText = textEnabled(), wantShape = shapeEnabled(), wantLine = lineEnabled()
        let cw = Float(current.width), ch = Float(current.height)
        let quiet = Set(quietKeys), lineDone = lineSettled.set, done = settled.set
        let textEngine = self.textEngine, shapeEngine = self.shapeEngine, lineEngine = self.lineEngine
        let version = store.visualVersion
        let found: Found = await Task.detached(priority: .userInitiated) {
            let pool = all.filter { $0.id == last.id || !done.contains($0.id) }
            let textGroup = textEngine.isWritingPen(last) ? textEngine.group(pool, last) : []
            let writing = !textGroup.isEmpty && textEngine.looksLikeText(textGroup, canvasW: cw, canvasH: ch)
            func looksLikeWriting(_ g: [Stroke]) -> Bool { g.allSatisfy(textEngine.isWritingPen) && textEngine.looksLikeText(g, canvasW: cw, canvasH: ch) }
            let t = wantText && writing && !quiet.contains(Self.key("text", textGroup.map(\.id)))
                ? textEngine.suggest(textGroup, canvasW: cw, canvasH: ch) : nil
            var sh: ShapeSuggestion?
            if wantShape {
                let g = shapeEngine.group(pool, last, Self.isWritingInk)
                if g.count >= SuggestRules.SHAPE_MIN && !quiet.contains(Self.key("shape", g.map(\.id))) && !(writing && looksLikeWriting(g)) {
                    sh = shapeEngine.suggest(g, all, Self.isWritingInk)
                }
            }
            var ln: LineSuggestion?
            if wantLine && !lineDone.contains(last.id) {
                let g = lineEngine.group(pool.filter { $0.id == last.id || (Self.isWritingInk($0) && !lineDone.contains($0.id)) }, last)
                if !g.isEmpty && !quiet.contains(Self.key("line", g.map(\.id))) && !(writing && looksLikeWriting(g)) {
                    ln = lineEngine.suggest(g, canvasW: cw, canvasH: ch)
                }
            }
            return Found(text: t, shape: sh, line: ln)
        }.value
        if Task.isCancelled || store.visualVersion != version { return }
        autoKey = nil
        text = found.text; shape = found.shape; line = found.line
    }

    /// v17 seçili fırça çizimleri için öneri ("Düzeltme öner").
    func suggestForSelection(_ onResult: @escaping (Bool) -> Void = { _ in }) {
        guard let current = store.canvas else { onResult(false); return }
        let ids = store.selection
        let ink = current.strokes.filter { ids.contains($0.id) && Self.isWritingInk($0) }
        if !canSuggest(ink) { onResult(false); return }
        job?.cancel(); selectionJob?.cancel()
        let version = store.visualVersion
        job = Task { [weak self] in
            guard let self else { return }
            let found = await self.forSelection(ink, current.strokes, ignoreQuiet: true)
            if self.store.visualVersion != version || self.store.selection != ids { return }
            if found.text == nil && found.shape == nil && found.line == nil { onResult(false); return }
            self.autoKey = nil
            self.text = found.text; self.shape = found.shape; self.line = found.line
            onResult(true)
        }
    }

    private func forSelection(_ ink: [Stroke], _ all: [Stroke], ignoreQuiet: Bool) async -> Found {
        let quiet = ignoreQuiet ? Set<String>() : Set(quietKeys)
        let cw = canvasW(), ch = canvasH()
        let wantText = textEnabled(), wantShape = shapeEnabled(), wantLine = lineEnabled()
        let textEngine = self.textEngine, shapeEngine = self.shapeEngine, lineEngine = self.lineEngine
        return await Task.detached(priority: .userInitiated) {
            let last = ink[ink.count - 1]
            func looksLikeWriting(_ g: [Stroke]) -> Bool { g.allSatisfy(textEngine.isWritingPen) && textEngine.looksLikeText(g, canvasW: cw, canvasH: ch) }
            let writers = ink.filter(textEngine.isWritingPen)
            let textGroup: [Stroke]
            if writers.count >= SuggestRules.TEXT_MIN { textGroup = textEngine.group(writers, writers[writers.count - 1]) }
            else if writers.count == 1 && ink.count == 1 { textGroup = writers }
            else { textGroup = [] }
            let writing = !textGroup.isEmpty && textEngine.looksLikeText(textGroup, canvasW: cw, canvasH: ch)
            let t = wantText && writing && !quiet.contains(Self.key("text", textGroup.map(\.id))) ? textEngine.suggest(textGroup, canvasW: cw, canvasH: ch) : nil
            var sh: ShapeSuggestion?
            if wantShape && ink.count >= SuggestRules.SHAPE_MIN {
                var g = shapeEngine.group(ink, last) { _ in true }
                if g.count < SuggestRules.SHAPE_MIN { g = Array(Self.largestCluster(ink, SuggestRules.SHAPE_PAD, SuggestRules.SHAPE_MIN_PAD).prefix(SuggestRules.SHAPE_MAX)) }
                if g.count >= SuggestRules.SHAPE_MIN && !quiet.contains(Self.key("shape", g.map(\.id))) && !(writing && looksLikeWriting(g)) {
                    sh = shapeEngine.suggest(g, all, Self.isWritingInk)
                }
            }
            let lineGroup = Array(ink.suffix(SuggestRules.LINE_MAX))
            var ln: LineSuggestion?
            if wantLine && !lineGroup.isEmpty && !quiet.contains(Self.key("line", lineGroup.map(\.id))) && !(writing && looksLikeWriting(lineGroup)) {
                ln = lineEngine.suggest(lineGroup, canvasW: cw, canvasH: ch)
            }
            return Found(text: t, shape: sh, line: ln)
        }.value
    }

    nonisolated private static func largestCluster(_ strokes: [Stroke], _ pad: Float, _ minPad: Float) -> [Stroke] {
        let boxes = strokes.map { TemplateSuggester.box(TemplateSuggester.flatten($0)) }
        return SuggestRules.clusters(boxes, pad, minPad).first?.map { strokes[$0] } ?? []
    }

    /// v27 seçim değişti: seçim kısa bir süre aynı kalırsa öneri kendiliğinden gösterilir.
    func onSelectionChanged(_ ids: Set<String>) {
        selectionJob?.cancel()
        let key = ids.sorted().joined(separator: ",")
        if autoKey != nil && autoKey != key { clearAll(); autoKey = nil }
        if ids.isEmpty || !selectionEnabled() || quietKeys.contains(key) { return }
        selectionJob = Task { [weak self] in
            try? await Task.sleep(nanoseconds: Self.SELECTION_PAUSE_MS * 1_000_000)
            if Task.isCancelled { return }
            await self?.autoSuggest(ids, key)
        }
    }

    private func autoSuggest(_ ids: Set<String>, _ key: String) async {
        guard let current = store.canvas else { return }
        let ink = current.strokes.filter { ids.contains($0.id) && Self.isWritingInk($0) }
        if !canSuggest(ink) || ink.count > SuggestRules.LINE_MAX { return }
        let version = store.visualVersion
        let found = await forSelection(ink, current.strokes, ignoreQuiet: false)
        if Task.isCancelled || store.visualVersion != version || store.selection != ids { return }
        if found.text == nil && found.shape == nil && found.line == nil { return }
        if autoKey == nil && (text != nil || shape != nil || line != nil) { return }
        autoKey = key
        text = found.text; shape = found.shape; line = found.line
    }

    private func quiet(_ key: String) {
        if !quietKeys.contains(key) { quietKeys.append(key) }
        while quietKeys.count > 64 { quietKeys.removeFirst() }
    }

    private func afterAccept() {
        let ids = Set(store.canvas?.strokes.map(\.id) ?? [])
        if let s = text, !s.strokeIds.allSatisfy(ids.contains) { text = nil }
        if let s = shape, !s.strokeIds.allSatisfy(ids.contains) { shape = nil }
        if let s = line, !s.strokeIds.allSatisfy(ids.contains) { line = nil }
        if let k = autoKey { quiet(k) }
        if text == nil && shape == nil && line == nil { autoKey = nil }
    }

    private static func reorder(_ strokes: [Stroke]) -> [Stroke] {
        strokes.enumerated().map { i, s in if s.order == i { return s }; var c = s; c.order = i; return c }
    }

    private static func dominantColor(_ strokes: [Stroke], _ fallback: String) -> String {
        var counts: [String: Int] = [:]
        var order: [String] = []
        for s in strokes { if counts[s.colorHex] == nil { order.append(s.colorHex) }; counts[s.colorHex, default: 0] += 1 }
        var best: String?; var bestN = 0
        for c in order where counts[c]! > bestN { best = c; bestN = counts[c]! }
        return best ?? fallback
    }

    /// v38 çizgi önerisi kabul edildi.
    func acceptLines() {
        guard let s = line else { return }
        line = nil
        guard let current = store.canvas else { return }
        var byId: [String: ShapeFix] = [:]
        for f in s.fixes { byId[f.strokeId] = f }
        if !current.strokes.contains(where: { byId[$0.id] != nil }) { return }
        store.commit(current.strokes.map { st in guard let f = byId[st.id] else { return st }; var c = st; c.points = f.points; return c })
        lineSettled.add(s.strokeIds)
        afterAccept()
    }

    /// Yazı önerisi kabul edildi: izler tek yazıya dönüşür.
    func accept(_ value: String) {
        guard let s = text, !value.isBlank else { return }
        text = nil
        guard let current = store.canvas else { return }
        let idSet = Set(s.strokeIds)
        let ink = current.strokes.filter { idSet.contains($0.id) }
        guard let firstInk = ink.first else { return }
        let color = Self.dominantColor(ink, "#1E1E1E")
        let size = (s.bounds.height / 0.72).coerceIn(10, 400)
        let spec = TextSpec(content: value, font: .SANS)
        let q = ContentCodec.quantize
        let points = TextRenderer.quadAt(spec, size, s.bounds.left, s.bounds.top - size * 0.12).map { StrokePoint(q($0.x), q($0.y)) }
        let first = current.strokes.firstIndex { idSet.contains($0.id) } ?? 0
        let stroke = Stroke(id: UUID().uuidString.lowercased(), points: points, colorHex: color, brushType: .TEXT, sizePx: size, opacity: 1,
                            order: first, text: spec, layerId: firstInk.layerId ?? "")
        var updated: [Stroke] = []
        for st in current.strokes {
            if idSet.contains(st.id) { if st.id == firstInk.id { updated.append(stroke) } } else { updated.append(st) }
        }
        store.commit(Self.reorder(updated))
        afterAccept()
    }

    /// v21 şablon önerisi kabul edildi: izler şablonun çizgileriyle ya da cisim/vektör şekliyle değiştirilir.
    func acceptTemplate(_ position: Int) {
        guard let s = shape, s.templates.indices.contains(position) else { return }
        let fix = s.templates[position]
        shape = nil
        guard let current = store.canvas else { return }
        let idSet = Set(fix.strokeIds)
        let group = current.strokes.filter { idSet.contains($0.id) }
        guard let base = group.max(by: { a, b in
            let wa = ElementOps.bounds(a).map { $0.width + $0.height } ?? 0, wb = ElementOps.bounds(b).map { $0.width + $0.height } ?? 0
            return wa < wb
        }) else { return }
        let color = Self.dominantColor(group, base.colorHex)
        guard let firstId = current.strokes.first(where: { idSet.contains($0.id) })?.id else { return }
        let start = P(fix.bounds.left, fix.bounds.top), end = P(fix.bounds.right, fix.bounds.bottom)
        var replacement: [Stroke] = []
        if let solid = fix.solid {
            let width = (ElementOps.halfWidth(base) * 2).coerceIn(1, 14)
            var style = ShapeStyle(); style.strokeColorHex = color; style.strokeWidth = width
            let built: Stroke?
            if !fix.vector && group.contains(where: { $0.space != nil }) {
                let cam = Scene3D.camera(current.guide, current.width, current.height)
                built = Scene3D.solidStroke(solid, start: start, end: end, style: style, cam: cam, id: UUID().uuidString.lowercased(), order: 0, opacity: base.opacity)
            } else {
                built = ShapeFactory.build(solid, start, end, style: style, id: UUID().uuidString.lowercased(), order: 0, opacity: base.opacity)
            }
            if var b = built { b.layerId = base.layerId; replacement = [b] }
        } else {
            let groupId = fix.strokes.count > 1 ? UUID().uuidString.lowercased() : base.groupId
            for (i, pts) in fix.strokes.enumerated() where pts.count >= 2 {
                var c = base
                c.id = UUID().uuidString.lowercased(); c.points = pts; c.colorHex = color; c.order = 0; c.groupId = groupId; c.clip = nil
                c.space = fix.spaces.indices.contains(i) ? fix.spaces[i] : nil
                replacement.append(c)
            }
        }
        if replacement.isEmpty { return }
        var updated: [Stroke] = []
        for st in current.strokes {
            if idSet.contains(st.id) { if st.id == firstId { updated += replacement } } else { updated.append(st) }
        }
        store.commit(Self.reorder(updated))
        settled.add(replacement.map(\.id))
        if !fix.vector { templates?.accepted(fix.key, fix.drawn) }
        afterAccept()
    }

    func dismissText() {
        if let s = text { quiet(Self.key("text", s.strokeIds)) }
        text = nil
        afterDismiss()
    }

    func dismissShape() {
        if let s = shape {
            quiet(Self.key("shape", s.strokeIds))
            var seen = Set<String>()
            let keys = s.templates.map(\.key).filter { !$0.hasPrefix(ShapeSuggester.VECTOR_PREFIX) && seen.insert($0).inserted }
            templates?.rejected(keys, s.templates.first { !$0.drawn.isEmpty }?.drawn ?? [])
        }
        shape = nil
        afterDismiss()
    }

    func dismissLine() {
        if let s = line { quiet(Self.key("line", s.strokeIds)); lineSettled.add(s.strokeIds) }
        line = nil
        afterDismiss()
    }

    private func afterDismiss() {
        if text == nil && shape == nil && line == nil {
            if let k = autoKey { quiet(k) }
            autoKey = nil
        }
    }

    func dismiss() { dismissShape(); dismissText(); dismissLine() }

    private func clearAll() { text = nil; shape = nil; line = nil }

    func reset() {
        job?.cancel(); selectionJob?.cancel()
        clearAll()
        autoKey = nil
    }
}
