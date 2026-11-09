import Foundation

/// Serbest çizim (Android `DrawingController`): çizimin eklenmesi, tamamen silinmiş çizimlerin arka planda temizlenmesi
/// ve boşluk doldurma.
@MainActor
final class DrawingController {
    private static let MIN_POINT_DISTANCE: Float = 0.75
    private let store: EditorStore
    private unowned let host: EditorHost
    private let brush: () -> BrushController

    init(_ store: EditorStore, _ host: EditorHost, brush: @escaping () -> BrushController) {
        self.store = store; self.host = host; self.brush = brush
    }

    func onStrokeFinished(_ points: [StrokePoint], strokeId: String? = nil, penEraser: Bool = false) {
        if points.isEmpty { return }
        guard let current = store.canvas else { return }
        if let active = current.layers.first(where: { $0.id == store.activeLayer }), !active.visible || active.locked {
            host.emit(.layerLocked)
            return
        }
        let b = brush()
        var settings = b.settings
        if penEraser && settings.brushType != .ERASER {
            settings.brushType = .ERASER; settings.sizePx = b.eraserSize(); settings.opacity = 1; settings.flow = 1; settings.strength = 1
        }
        var params: BrushParams? = nil
        if settings.brushType != .ERASER {
            if let d = current.guide.draw3dSpec { var p = settings.params ?? BrushParams(); p.depth3d = d; params = p } else { params = settings.params }
        }
        let stroke = Stroke(id: strokeId ?? UUID().uuidString.lowercased(), points: compactPoints(points), colorHex: settings.colorHex,
                            brushType: settings.brushType, sizePx: settings.sizePx, opacity: settings.opacity, order: current.strokes.count,
                            flow: settings.flow, strength: settings.strength, params: params)
        store.commit(current.strokes + [stroke])
        if settings.brushType == .ERASER { pruneAfterEraser(stroke) } else { host.onInk(stroke) }
    }

    /// v32 eğitim modu: hazır çizgi tuvale eklenir.
    func commitDemo(_ stroke: Stroke, suggest: Bool) {
        guard let current = store.canvas else { return }
        var s = stroke
        s.order = current.strokes.count
        store.commit(current.strokes + [s])
        if s.brushType == .ERASER { pruneAfterEraser(s) } else if suggest { host.onInk(s) }
    }

    /// Tuval açılınca: veride kalmış görünmeyen çizimler bir kez temizlenir.
    func cleanOnOpen(_ loaded: CanvasUi) async {
        if !loaded.strokes.contains(where: { $0.brushType == .ERASER }) { return }
        let strokes = loaded.strokes
        let cleaned = await Task.detached { ErasedStrokePruner.prune(strokes, focus: nil) }.value
        if cleaned != strokes && store.canvas?.strokes == strokes { store.applySilently(cleaned) }
    }

    // MARK: Tamamen silinenlerin temizliği

    private var pendingPruneArea: FRect?
    private var pendingPruneErasers = Set<String>()
    private var pruning = false

    private func pruneAfterEraser(_ eraser: Stroke) {
        guard let box = ElementOps.bounds(eraser) else { return }
        pendingPruneArea = pendingPruneArea.map { $0.union(box) } ?? box
        pendingPruneErasers.insert(eraser.id)
        if pruning { return }
        pruning = true
        Task { [weak self] in
            await self?.runPendingPrune()
            self?.pruning = false
        }
    }

    private func runPendingPrune() async {
        while true {
            guard let focus = pendingPruneArea else { return }
            pendingPruneArea = nil
            let batchErasers = pendingPruneErasers
            guard let snapshot = store.canvas?.strokes else { return }
            let pruned = await Task.detached { ErasedStrokePruner.prune(snapshot, focus: focus) }.value
            if pruned == snapshot {
                if store.canvas?.strokes != snapshot { pendingPruneArea = pendingPruneArea.map { $0.union(focus) } ?? focus }
                else { pendingPruneErasers.subtract(batchErasers) }
                continue
            }
            let keptIds = Set(pruned.map(\.id))
            let before = await Task.detached { ElementIndexer().update(snapshot.filter { !batchErasers.contains($0.id) }) }.value
            if store.canvas?.strokes != snapshot {
                pendingPruneArea = pendingPruneArea.map { $0.union(focus) } ?? focus
                continue
            }
            pendingPruneErasers.subtract(batchErasers)
            let removedElements = before.elements.filter { e in !e.strokeIds.isEmpty && !e.strokeIds.contains(where: keptIds.contains) }.count
            store.applySilently(pruned)
            if removedElements > 0 { host.emit(.erasedRemoved(removedElements)) }
        }
    }

    // MARK: Boşlukları doldur

    func fillGaps() {
        guard let current = store.canvas else { return }
        let ids = store.selection
        if ids.isEmpty { host.emit(.commandNeedsSelection); return }
        let result = GapFiller.fill(current.strokes, ids)
        if result.bridges.isEmpty { host.emit(.noGapsFound); return }
        store.select(ids.union(result.bridges.map(\.id)))
        store.commit(result.strokes)
        host.emit(.gapsFilled(result.bridges.count))
    }

    /// Noktaları 1/100 birime yuvarlar ve çok yakın noktaları atar (son nokta korunur).
    func compactPoints(_ points: [StrokePoint]) -> [StrokePoint] {
        let q = points.map { p in
            StrokePoint(ContentCodec.quantize(p.x), ContentCodec.quantize(p.y), pressure: ContentCodec.quantizeUnit(p.pressure),
                        tilt: ContentCodec.quantizeUnit(p.tilt), orient: p.orient == 0 ? 0 : max(ContentCodec.quantizeUnit(p.orient), 1.0 / 255))
        }
        if q.count <= 2 { return q }
        var out = [q[0]]
        for i in 1..<(q.count - 1) {
            let last = out[out.count - 1], p = q[i]
            if hypotf(p.x - last.x, p.y - last.y) >= Self.MIN_POINT_DISTANCE { out.append(p) }
        }
        let end = q[q.count - 1]
        if out.count > 1 && hypotf(end.x - out[out.count - 1].x, end.y - out[out.count - 1].y) < Self.MIN_POINT_DISTANCE { out[out.count - 1] = end } else { out.append(end) }
        return out
    }
}
