import CoreGraphics
import Foundation

/// v43 "Arkaplan ile Nesneyi Uyumla" ve "Nesneyi Mekâna Yerleştir" (Android `ObjectSceneController`). Değişiklikler
/// önce önizlenir; Uygula tek geri alma adımı, İptal özgün hâl.
@MainActor
final class ObjectSceneController: ObservableObject {
    struct HarmonyDraft: Equatable {
        let target: Stroke
        var options: SceneAnalyzer.Options
        var analysis: SceneAnalyzer.Analysis?
        var busy = false
        var hadHarmony = false
    }

    struct PlaceDraft: Equatable {
        let target: Stroke
        var perspective: SceneAnalyzer.Perspective?
        var horizonY: Float
        var footY: Float?
        var scaleMul: Float = 1
        var result: ScenePlacement.Result? = nil
        var references = 0
        var busy = false
        var canvasHeight: Float = 1
    }

    private let store: EditorStore
    @Published private(set) var harmony: HarmonyDraft?
    @Published private(set) var place: PlaceDraft?
    private var job: Task<Void, Never>?

    init(_ store: EditorStore) { self.store = store }

    func available(_ kind: CanvasKind?) -> Bool { kind == .ART || kind == .COMIC }

    func target() -> Stroke? {
        guard let c = store.canvas else { return nil }
        let ids = store.selection
        let picked = c.strokes.filter { ids.contains($0.id) && $0.brushType != .ERASER }
        guard picked.count == 1, picked[0].brushType == .IMAGE, picked[0].points.count >= 4 else { return nil }
        return picked[0]
    }

    // MARK: Uyum

    @discardableResult
    func openHarmony() -> Bool {
        guard available(store.canvas?.kind), let t = target(), let c = store.canvas else { return false }
        endAll()
        harmony = HarmonyDraft(target: t, options: SceneAnalyzer.Options(), analysis: nil, busy: true, hadHarmony: t.harmony != nil)
        job = Task { [weak self] in
            let a = await Self.analyzeHarmony(c, t)
            guard let self, var d = self.harmony, !Task.isCancelled else { return }
            d.analysis = a; d.busy = false
            self.harmony = d
            self.previewHarmony()
        }
        return true
    }

    func setHarmonyOptions(_ o: SceneAnalyzer.Options) {
        guard var d = harmony else { return }
        d.options = o
        harmony = d
        previewHarmony()
    }

    func harmonized(_ d: HarmonyDraft) -> Stroke {
        guard let a = d.analysis else { return d.target }
        let o = d.options
        let spec = SceneAnalyzer.suggest(a, o)
        let any = o.shadow || o.color || o.edge
        var s = d.target
        s.harmony = any && o.intensity > 0 ? spec : nil
        if o.position, let b = ElementOps.selectionBounds([s], [s.id]), let g = a.groundY, abs(g - b.bottom) < b.height * 0.25 {
            s = ElementOps.translate([s], [s.id], 0, g - b.bottom)[0]
        }
        return s
    }

    private func previewHarmony() {
        guard let d = harmony, d.analysis != nil, let base = store.canvas?.strokes else { return }
        let next = harmonized(d)
        store.previewStrokes(base.map { $0.id == next.id ? next : $0 })
    }

    func applyHarmony() {
        guard let d = harmony else { return }
        harmony = nil
        job?.cancel()
        _ = store.endPreview()
        if d.analysis == nil { return }
        let next = harmonized(d)
        guard let current = store.canvas else { return }
        store.commit(current.strokes.map { $0.id == next.id ? next : $0 })
        store.select([next.id])
    }

    func removeHarmony() {
        guard let d = harmony else { return }
        harmony = nil
        job?.cancel()
        _ = store.endPreview()
        guard let current = store.canvas else { return }
        store.commit(current.strokes.map { s in guard s.id == d.target.id else { return s }; var c = s; c.harmony = nil; return c })
    }

    func cancelHarmony() {
        harmony = nil
        job?.cancel()
        _ = store.endPreview()
    }

    nonisolated private static func analyzeHarmony(_ c: CanvasUi, _ t: Stroke) async -> SceneAnalyzer.Analysis? {
        await Task.detached(priority: .userInitiated) { () -> SceneAnalyzer.Analysis? in
            guard let img = CanvasImageCache.get(t.assetId), let obj = PixelCodec.texture(img, maxSide: 256),
                  let b = ElementOps.selectionBounds([t], [t.id]) else { return nil }
            let pad = max(b.width, b.height) * 0.6
            let region = FRect(max(b.left - pad, 0), max(b.top - pad, 0), min(b.right + pad, Float(c.width)), min(b.bottom + pad, Float(c.height)))
            if region.width < 2 || region.height < 2 { return nil }
            let bg = renderBelow(c, t.id, region, 256)
            return SceneAnalyzer.analyze(bg, region, b, SceneAnalyzer.Pixels(px: obj.px, w: obj.w, h: obj.h))
        }.value
    }

    // MARK: Mekâna yerleştir

    @discardableResult
    func openPlace() -> Bool {
        guard let c = store.canvas, available(c.kind), let t = target() else { return false }
        endAll()
        place = PlaceDraft(target: t, perspective: nil, horizonY: Float(c.height) * 0.4, footY: nil, busy: true, canvasHeight: Float(c.height))
        job = Task { [weak self] in
            let p = await Task.detached(priority: .userInitiated) { () -> SceneAnalyzer.Perspective in
                let bg = Self.renderBelow(c, t.id, FRect(0, 0, Float(c.width), Float(c.height)), 320)
                return SceneAnalyzer.perspective(bg, Float(c.width), Float(c.height))
            }.value
            guard let self, var d = self.place, !Task.isCancelled else { return }
            d.perspective = p; d.horizonY = p.horizonY; d.busy = false
            self.place = d
            self.previewPlace()
        }
        return true
    }

    /// Elle düzeltme: ufuk, ayak yüksekliği (nil: otomatik) ve ek ölçek.
    func setPlaceManual(horizonY: Float? = nil, footY: Float?? = .none, scaleMul: Float? = nil) {
        guard var d = place else { return }
        d.horizonY = (horizonY ?? d.horizonY).coerceIn(0, d.canvasHeight)
        let foot: Float? = footY ?? d.footY
        d.footY = foot.map { $0.coerceIn(0, d.canvasHeight) }
        d.scaleMul = (scaleMul ?? d.scaleMul).coerceIn(0.5, 2)
        place = d
        previewPlace()
    }

    func placed(_ d: PlaceDraft) -> (Stroke, ScenePlacement.Result)? {
        guard let c = store.canvas, let b = ElementOps.selectionBounds([d.target], [d.target.id]) else { return nil }
        let t = d.target
        let refs = c.strokes.filter { $0.id != t.id && $0.brushType == .IMAGE && !$0.hidden }.compactMap { s -> ScenePlacement.Reference? in
            ElementOps.selectionBounds([s], [s.id]).map { ScenePlacement.Reference(footY: $0.bottom, height: $0.height) }
        }.filter { $0.height < Float(c.height) * 0.9 }
        let r = ScenePlacement.place(b, d.horizonY, Float(c.height), refs, footOverride: d.footY, scaleMul: d.scaleMul)
        let pivot = P((b.left + b.right) / 2, b.bottom)
        var s = ElementOps.scale([t], [t.id], r.scale, r.scale, pivot: pivot)[0]
        s = ElementOps.translate([s], [s.id], r.footX - pivot.x, r.footY - pivot.y)[0]
        return (s, r)
    }

    private func previewPlace() {
        guard var d = place, !d.busy, let (s, r) = placed(d), let base = store.canvas?.strokes else { return }
        d.result = r; d.references = r.references
        place = d
        store.previewStrokes(base.map { $0.id == s.id ? s : $0 })
    }

    func applyPlace() {
        guard let d = place else { return }
        place = nil
        job?.cancel()
        _ = store.endPreview()
        if d.busy { return }
        guard let (s, _) = placed(d), let current = store.canvas else { return }
        store.commit(current.strokes.map { $0.id == s.id ? s : $0 })
        store.select([s.id])
    }

    func cancelPlace() {
        place = nil
        job?.cancel()
        _ = store.endPreview()
    }

    func endAll() {
        if harmony != nil { cancelHarmony() }
        if place != nil { cancelPlace() }
    }

    // MARK: Çizim

    /// Nesnenin altındakilerin ([region] alanı, en uzun kenarı [maxSide] piksel) ARGB çizimi.
    nonisolated static func renderBelow(_ c: CanvasUi, _ objectId: String, _ region: FRect, _ maxSide: Int) -> SceneAnalyzer.Pixels {
        let strokes = Layers.visible(c.strokes, c.layers)
        let below = strokes.firstIndex { $0.id == objectId }.map { Array(strokes[..<$0]) } ?? strokes
        let k = Float(maxSide) / max(region.width, region.height)
        let w = max(1, Int(region.width * k)), h = max(1, Int(region.height * k))
        let paper = ArgbColor.parse(c.paperOf(c.currentPage)) ?? 0xFFFF_FFFF
        let img = Bitmaps.draw(w, h) { ctx in
            ctx.scaleBy(x: CGFloat(k), y: CGFloat(k))
            ctx.translateBy(x: CGFloat(-region.left), y: CGFloat(-region.top))
            StrokeRenderer.drawCanvasContent(ctx, paper: paper, strokes: below, width: CGFloat(c.width), height: CGFloat(c.height),
                                             decor: c.backgroundOf(c.currentPage))
        }
        return SceneAnalyzer.Pixels(px: HarmonyRenderer.argbPixels(img, w, h), w: w, h: h)
    }
}
