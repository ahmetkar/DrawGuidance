import Combine
import Foundation

/// v56 2C-3 uygulama içi eleman panosu (Android `ElementClipboard`): uygulama açık kaldıkça durur. Resim varlıklarının
/// baytları kopyalama anında alınır ki kaynak tuvalde silinse de başka tuvale yapıştırılabilsin.
@MainActor
final class ElementClipboard: ObservableObject {
    static let shared = ElementClipboard()

    struct Asset { let meta: CanvasAssetStore.Meta; let data: Data }

    struct Content {
        let strokes: [Stroke]
        let sourceCanvas: String
        let sourcePage: Int
        /// Kesildi mi (ilk yapıştırma aynı yere).
        let cut: Bool
        var assets: [Asset] = []
        let serial: Int
    }

    @Published private(set) var content: Content?
    private var serial = 0
    private var lastKey: String?
    private var repeatCount = 0

    @discardableResult
    func set(_ strokes: [Stroke], sourceCanvas: String, sourcePage: Int, cut: Bool, assets: [Asset] = []) -> Content {
        serial += 1
        lastKey = nil
        let c = Content(strokes: strokes, sourceCanvas: sourceCanvas, sourcePage: sourcePage, cut: cut, assets: assets, serial: serial)
        content = c
        return c
    }

    /// Aynı yere kaçıncı yapıştırma (Android `ElementClipboard.nextRepeat`).
    func nextRepeat(canvas: String, page: Int, anchorKey: String) -> Int {
        guard let c = content else { return 0 }
        let key = "\(c.serial)|\(canvas)|\(page)|\(anchorKey)"
        if key == lastKey { repeatCount += 1 }
        else { repeatCount = (!c.cut && c.sourceCanvas == canvas && c.sourcePage == page) ? 1 : 0 }
        lastKey = key
        return repeatCount
    }

    func clear() { content = nil; lastKey = nil }
}
