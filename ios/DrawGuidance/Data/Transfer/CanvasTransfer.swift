import CoreGraphics
import Foundation

/// v29 aktarmanın hedefi: kayıtlı bir tuval ya da bu türde yeni bir tuval.
enum TransferTarget: Equatable {
    case existing(String)
    case new(CanvasKind)
}

struct TransferResult: Equatable { let canvasId: String; let name: String }

/// v29 modlar arası aktarma (Android `CanvasTransfer`): çizim not alma/çizgi roman tuvaline resim (saydam PNG) ya da
/// vektör olarak aktarılır; hedef sayfanın ortasına, en çok %70'ini kaplayacak şekilde yerleşir.
@MainActor
final class CanvasTransfer {
    static let FIT: Float = 0.7
    private let repository: CanvasStore
    private let assets: CanvasAssetStore

    init(_ repository: CanvasStore, assets: CanvasAssetStore = .shared) { self.repository = repository; self.assets = assets }

    func targets(_ kinds: Set<CanvasKind>, exclude: String?) -> [CanvasUi] {
        repository.canvases.filter { kinds.contains($0.kind) && $0.localId != exclude }.sorted { $0.updatedAt > $1.updatedAt }
    }

    private func resolve(_ target: TransferTarget, _ name: String) -> CanvasUi? {
        switch target {
        case let .existing(id): return repository.getCanvas(id)
        case let .new(kind): return repository.create(kind: kind, name: name)
        }
    }

    private static func newId() -> String { UUID().uuidString.lowercased() }

    func sendStrokes(_ target: TransferTarget, _ newName: String, _ strokes: [Stroke], group: Bool = false) -> TransferResult? {
        if strokes.allSatisfy({ $0.brushType == .ERASER }) { return nil }
        guard let canvas = resolve(target, newName), let bounds = ElementOps.bounds(strokes) else { return nil }
        var groups: [String: String] = [:]
        let shared = group ? Self.newId() : nil
        let copied = strokes.map { s -> Stroke in
            var c = s
            c.id = Self.newId()
            if let shared { c.groupId = shared } else if let g = s.groupId {
                if groups[g] == nil { groups[g] = Self.newId() }
                c.groupId = groups[g]
            }
            c.layerId = nil
            c.space = nil
            if let id = s.assetId { c.assetId = assets.copyTo(id, canvas.localId) ?? id }
            if var link = s.model { link.dataAssetId = assets.copyTo(link.dataAssetId, canvas.localId) ?? link.dataAssetId; c.model = link }
            return c
        }
        return append(canvas, Self.fit(copied, bounds, canvas.width, canvas.height))
    }

    func sendImage(_ target: TransferTarget, _ newName: String, _ image: CGImage) -> TransferResult? {
        guard let canvas = resolve(target, newName), let bytes = try? ImageExporter.encode(image, .PNG, 100) else { return nil }
        let prepared = PreparedImage(bytes: bytes, mimeType: "image/png", width: image.width, height: image.height, image: image,
                                     originalBytes: bytes.count, detailScore: 0)
        let assetId = assets.save(canvas.localId, prepared)
        let q = ContentCodec.quantize
        let w = Float(image.width), h = Float(image.height)
        var stroke = Stroke(id: Self.newId(), points: [StrokePoint(q(0), q(0)), StrokePoint(q(w), q(0)), StrokePoint(q(w), q(h)), StrokePoint(q(0), q(h))],
                            colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 0)
        stroke.assetId = assetId
        return append(canvas, Self.fit([stroke], FRect(0, 0, w, h), canvas.width, canvas.height))
    }

    private func append(_ canvas: CanvasUi, _ added: [Stroke]) -> TransferResult {
        var c = canvas
        if c.pages.isEmpty { c.pages = [[]] }
        let page = c.currentPage.coerceIn(0, c.pages.count - 1)
        let existing = c.pages[page]
        c.pages[page] = existing + added.enumerated().map { i, s in var x = s; x.order = existing.count + i; return x }
        repository.save(c)
        return TransferResult(canvasId: c.localId, name: c.name)
    }

    nonisolated static func fit(_ strokes: [Stroke], _ bounds: FRect, _ width: Int, _ height: Int) -> [Stroke] {
        let k = min(1, min(Float(width) * FIT / max(bounds.width, 1), Float(height) * FIT / max(bounds.height, 1)))
        let ids = Set(strokes.map(\.id))
        let scaled = k < 0.999 ? ElementOps.scale(strokes, ids, k, k, pivot: bounds.center) : strokes
        return ElementOps.translate(scaled, ids, Float(width) / 2 - bounds.center.x, Float(height) / 2 - bounds.center.y)
    }

    /// Elemanların saydam zeminli resmi (en uzun kenar en çok `maxSide`; küçükler 2 kata kadar büyütülür).
    nonisolated static func render(_ strokes: [Stroke], pad: Float = 12, maxSide: Int = 2048) -> CGImage? {
        guard let b = ElementOps.bounds(strokes) else { return nil }
        let r = FRect(b.left - pad, b.top - pad, b.right + pad, b.bottom + pad)
        let k = min(2, Float(maxSide) / max(max(r.width, r.height), 1))
        let w = Int((r.width * k).rounded()).coerceIn(1, maxSide), h = Int((r.height * k).rounded()).coerceIn(1, maxSide)
        let moved = ElementOps.translate(strokes, Set(strokes.map(\.id)), -r.left, -r.top)
        guard let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else { return nil }
        ctx.translateBy(x: 0, y: CGFloat(h))
        ctx.scaleBy(x: CGFloat(k), y: -CGFloat(k))
        ctx.beginTransparencyLayer(auxiliaryInfo: nil)
        StrokeRenderer.drawStrokeList(ctx, moved)
        ctx.endTransparencyLayer()
        return ctx.makeImage()
    }
}
