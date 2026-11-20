import Foundation

/// v56 2C-3 seçili elemanları sayfalar (ve tuvaller) arasında kes, kopyala, yapıştır (Android `ClipboardController`).
/// Pano uygulama düzeyindedir ([ElementClipboard]); her işlem tek geri alma adımıdır.
@MainActor
final class ClipboardController {
    private let store: EditorStore
    private unowned let host: EditorHost
    private let assets: CanvasAssetStore
    let board: ElementClipboard

    init(_ store: EditorStore, _ host: EditorHost, assets: CanvasAssetStore = .shared, board: ElementClipboard = .shared) {
        self.store = store; self.host = host; self.assets = assets; self.board = board
    }

    @discardableResult
    func copy() -> Bool { take(cut: false) }

    @discardableResult
    func cut() -> Bool {
        guard take(cut: true), let current = store.canvas, let taken = board.content.map({ Set($0.strokes.map(\.id)) }) else { return false }
        store.commit(current.strokes.filter { !taken.contains($0.id) })
        store.select([])
        return true
    }

    private func take(cut: Bool) -> Bool {
        guard let current = store.canvas else { return false }
        let strokes = ElementClip.collect(current.strokes, store.selection)
        if strokes.isEmpty { return false }
        let blobs = ElementClip.assetIds(strokes).compactMap { id in assets.record(id).map { ElementClipboard.Asset(meta: $0.0, data: $0.1) } }
        board.set(strokes, sourceCanvas: store.canvasLocalId, sourcePage: current.currentPage, cut: cut, assets: blobs)
        return true
    }

    /// Panodakiler açık sayfaya yapıştırılır (Android `ClipboardController.paste`); yapıştırılanlar seçili gelir.
    func paste(anchor: P? = nil, visible: [Float]? = nil) {
        guard let content = board.content, let now = store.canvas, let b = ElementClip.bounds(content.strokes) else { return }
        var map: [String: String] = [:]
        let needed = Set(ElementClip.assetIds(content.strokes))
        for a in content.assets where needed.contains(a.meta.id) && assets.canvasOf(a.meta.id) != store.canvasLocalId {
            map[a.meta.id] = assets.insertCopy(a.meta, a.data, store.canvasLocalId)
        }
        let anchorKey = anchor.map { "\($0.x),\($0.y)" } ?? ""
        let rep = board.nextRepeat(canvas: store.canvasLocalId, page: now.currentPage, anchorKey: anchorKey)
        let (dx, dy) = ElementClip.offset(b, pageW: Float(now.width), pageH: Float(now.height), visible: visible, anchor: anchor, repeat: rep)
        let pasted = ElementClip.paste(content.strokes, newId: { UUID().uuidString.lowercased() }, startOrder: now.strokes.count,
                                       dx: dx, dy: dy, assets: map, q: ContentCodec.quantize)
        store.commit(now.strokes + pasted, useArea: false)
        if let c = store.canvas { assets.ensureLoaded(c) }
        if host.mode != .SELECT { host.setMode(.SELECT) }
        store.select(store.currentIndex().expand(Set(pasted.filter { !$0.hidden }.map(\.id))))
    }
}
