import CoreGraphics
import Foundation

/// v23 tuvaldeki 3B şekiller (Android `Model3DController`): tuvalde genel işlemler yapılır; şekil modelin resmidir,
/// model tuvalin verisinde saklanır. v56 aşama 10: kaynaklar yalnız kütüphane ve GLB dosyası; eklemeden önce açı
/// seçilir, sonra "Açıyı değiştir". Tuvalden 3B düzenleyiciye yol yoktur.
@MainActor
final class Model3DController: ObservableObject {
    private let store: EditorStore
    private unowned let host: EditorHost
    private let assets: CanvasAssetStore
    let repository: Model3DRepository?

    @Published private(set) var busy = false

    static let IMAGE_SIZE = 720
    static let MIME = Model3DStore.MIME
    static let MSG_IMPORT_FAIL = "İçe aktarılamadı: {0}"

    init(_ store: EditorStore, _ host: EditorHost, assets: CanvasAssetStore = .shared, repository: Model3DRepository?) {
        self.store = store; self.host = host; self.assets = assets; self.repository = repository
    }

    func selected() -> Stroke? {
        guard store.selection.count == 1 else { return nil }
        return store.selectedStroke { $0.model != nil }
    }

    /// Açı sınırları: dikey eğim −80..80, yatay dönüş 0..360.
    nonisolated static func withAngle(_ model: Model3D, yaw: Float, pitch: Float) -> Model3D {
        var m = model
        m.camera.yaw = (yaw.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
        m.camera.pitch = min(max(pitch, -80), 80)
        return m
    }

    /// Kütüphanedeki şeklin modeli (açı seçimi için).
    func catalogModel(_ catalogId: String) -> Model3D? { repository?.fromCatalog(catalogId) }

    /// GLB/glTF dosyasının modeli (ayrıntı düşürülür); okunamazsa bildirilir.
    func glbModel(_ bytes: [UInt8], name: String, maxTriangles: Int) async -> Model3D? {
        guard let repo = repository else { return nil }
        busy = true
        defer { busy = false }
        do {
            return try await repo.importGlb(bytes, name, maxTriangles).model
        } catch {
            AppLog.e("GlbImport", "İçe aktarılamadı", error)
            host.emit(.message(I18n.trf(Self.MSG_IMPORT_FAIL, error.localizedDescription)))
            return nil
        }
    }

    /// Seçili 3B şeklin tuvalde saklanan modeli (Açıyı değiştir için).
    func selectedModel() -> Model3D? {
        guard let link = selected()?.model, let bytes = assets.bytes(link.dataAssetId) else { return nil }
        return try? Model3DStore.decode(bytes)
    }

    /// Önizleme: modeli verilen açıdan kare resim olarak çizer.
    func preview(_ model: Model3D, yaw: Float, pitch: Float, size: Int) async -> CGImage? {
        guard let tex = repository?.textures else { return nil }
        let m = Self.withAngle(model, yaw: yaw, pitch: pitch)
        return await Task.detached(priority: .userInitiated) { Model3DRepository.render(m, size, size, tex) }.value
    }

    /// Modeli seçilen açıyla tuvale ekler.
    func insert(_ model: Model3D, yaw: Float, pitch: Float, center: P? = nil) {
        Task {
            busy = true
            defer { busy = false }
            await place(Self.withAngle(model, yaw: yaw, pitch: pitch), nil, center)
        }
    }

    /// Seçili 3B şeklin bakış açısını değiştirir (model aynı; tek geri alma adımı).
    func changeAngle(yaw: Float, pitch: Float) {
        guard let stroke = selected(), let model = selectedModel() else { return }
        Task {
            busy = true
            defer { busy = false }
            await place(Self.withAngle(model, yaw: yaw, pitch: pitch), stroke, nil)
        }
    }

    /// Saydam kenarları kırpılmış ARGB resim.
    nonisolated static func trim(_ px: [UInt32], _ w: Int, _ h: Int) -> ([UInt32], Int, Int) {
        var l = w, t = h, r = -1, b = -1
        for y in 0..<h { for x in 0..<w where px[y * w + x] >> 24 > 8 {
            if x < l { l = x }; if x > r { r = x }; if y < t { t = y }; if y > b { b = y }
        } }
        if r < l || b < t { return (px, w, h) }
        let pad = 4
        l = max(l - pad, 0); t = max(t - pad, 0); r = min(r + pad, w - 1); b = min(b + pad, h - 1)
        if l == 0 && t == 0 && r == w - 1 && b == h - 1 { return (px, w, h) }
        let nw = r - l + 1, nh = b - t + 1
        var out = [UInt32](repeating: 0, count: nw * nh)
        for y in 0..<nh { for x in 0..<nw { out[y * nw + x] = px[(t + y) * w + l + x] } }
        return (out, nw, nh)
    }

    private func place(_ model: Model3D, _ existing: Stroke?, _ center: P?) async {
        guard let repo = repository else { return }
        let tex = repo.textures
        let size = Self.IMAGE_SIZE
        let rendered: (PreparedImage, Data)? = await Task.detached(priority: .userInitiated) { () -> (PreparedImage, Data)? in
            tex.preload(TextureRepository.ids(model))
            let view = View3.fit(model, model.camera, size, size, 0.04)
            let f = Renderer3D.render(model, view, RenderOptions(textures: { tex.cached($0) }, supersample: 2, ground: false, background: 0))
            let (px, w, h) = Self.trim(f.color, f.width, f.height)
            let img = Bitmaps.image(argb: px, w, h)
            guard let png = PixelCodec.encode(img) else { return nil }
            return (PreparedImage(bytes: png, mimeType: "image/png", width: w, height: h, image: img, originalBytes: png.count, detailScore: 0),
                    Model3DStore.encode(model))
        }.value
        guard let (image, data) = rendered else { return }
        let assetId = assets.save(store.canvasLocalId, image)
        let dataId = assets.saveData(store.canvasLocalId, data, Self.MIME)
        let link = Model3DLink(modelId: model.id, dataAssetId: dataId)
        guard let current = store.canvas else { return }
        let q = ContentCodec.quantize
        let aspect = Float(image.width) / max(Float(image.height), 1)
        if let existing, current.strokes.contains(where: { $0.id == existing.id }) {
            let p = existing.points
            var points = p
            if p.count >= 4 {
                let cx = (p[0].x + p[2].x) / 2, cy = (p[0].y + p[2].y) / 2
                let ux = p[1].x - p[0].x, uy = p[1].y - p[0].y
                let vx = p[3].x - p[0].x, vy = p[3].y - p[0].y
                let ul = max(kHypot(ux, uy), 1e-3), vl = max(kHypot(vx, vy), 1e-3)
                let nul = vl * aspect
                let ax = ux / ul * nul / 2, ay = uy / ul * nul / 2
                let bx = vx / 2, by = vy / 2
                points = [StrokePoint(q(cx - ax - bx), q(cy - ay - by)), StrokePoint(q(cx + ax - bx), q(cy + ay - by)),
                          StrokePoint(q(cx + ax + bx), q(cy + ay + by)), StrokePoint(q(cx - ax + bx), q(cy - ay + by))]
            }
            store.commit(current.strokes.map { s in
                guard s.id == existing.id else { return s }
                var c = s; c.assetId = assetId; c.model = link; c.points = points; return c
            })
            return
        }
        let c = center ?? P(Float(current.width) / 2, Float(current.height) / 2)
        let fit = min(Float(current.width) * 0.45 / Float(image.width), Float(current.height) * 0.45 / Float(image.height))
        let hw = Float(image.width) * fit / 2, hh = Float(image.height) * fit / 2
        let stroke = Stroke(id: UUID().uuidString.lowercased(),
                            points: [StrokePoint(q(c.x - hw), q(c.y - hh)), StrokePoint(q(c.x + hw), q(c.y - hh)),
                                     StrokePoint(q(c.x + hw), q(c.y + hh)), StrokePoint(q(c.x - hw), q(c.y + hh))],
                            colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: current.strokes.count, assetId: assetId, model: link)
        store.commit(current.strokes + [stroke])
        host.inserted([stroke.id])
    }
}
