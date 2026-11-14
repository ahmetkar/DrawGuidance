import Foundation

/// v51 tuval paketlerini hazırlar ve alınanları kaydeder (Android `SharePackages`). Paket alanları Android'in kayıt
/// biçimindedir: `background` ortak arka plan JSON'u + "pages" + "pageSettings", içerik ContentCodec baytları.
@MainActor
final class SharePackages {
    let store: CanvasStore
    let assets: CanvasAssetStore

    init(store: CanvasStore, assets: CanvasAssetStore = .shared) {
        self.store = store; self.assets = assets
    }

    /// Kayıtlı tuvalin paketi (yoksa nil).
    func canvasPackage(_ canvasId: String, type: String, id: String, from: String) -> SharePackage? {
        guard let c = store.getCanvas(canvasId) else { return nil }
        let metas = assets.metas(forCanvas: canvasId)
        var datas: [Data] = []
        var list: [ShareAsset] = []
        for m in metas {
            guard let d = assets.bytes(m.id) else { continue }
            datas.append(d)
            list.append(ShareAsset(id: m.id, mime: m.mimeType, w: m.width, h: m.height, len: d.count))
        }
        return package(c, type: type, id: id, seq: 0, assets: list, datas: datas, from: from)
    }

    func canvasSize(_ canvasId: String) -> Int64 {
        (store.canvases.first { $0.localId == canvasId }?.sizeBytes ?? 0) + assets.totalBytes(canvasId)
    }

    /// Yayın güncellemesi: düzenleyicideki tuval; yalnız `sent` içinde olmayan resimler eklenir (arka planda çağrılabilir).
    nonisolated func livePackage(_ c: CanvasUi, id: String, seq: Int64, sent: inout Set<String>, from: String) -> SharePackage {
        var datas: [Data] = []
        var list: [ShareAsset] = []
        for m in assets.metas(forCanvas: c.localId) where !sent.contains(m.id) {
            guard let d = assets.bytes(m.id) else { continue }
            datas.append(d)
            list.append(ShareAsset(id: m.id, mime: m.mimeType, w: m.width, h: m.height, len: d.count))
            sent.insert(m.id)
        }
        return package(c, type: ShareWire.TYPE_LIVE, id: id, seq: seq, assets: list, datas: datas, from: from)
    }

    nonisolated private func package(_ c: CanvasUi, type: String, id: String, seq: Int64, assets list: [ShareAsset], datas: [Data], from: String) -> SharePackage {
        let content = ContentCodec.encodePages(c.pages, layers: c.layers)
        var h = SharePackageHeader(id: id, type: type, name: c.name)
        h.canvasKind = c.kind.rawValue
        h.width = c.width; h.height = c.height
        h.backgroundColorHex = c.backgroundColorHex
        h.background = Self.encodeBackground(c.background, c.pageBackgrounds, c.pageSettings)
        h.guide = c.guide.isEmpty ? "" : ((try? JSONEncoder().encode(c.guide)).flatMap { String(data: $0, encoding: .utf8) } ?? "")
        h.currentPage = c.currentPage
        h.aiEnabled = c.aiEnabled
        h.contentLen = content.count
        h.assets = list
        h.seq = seq
        h.from = from
        return SharePackage(header: h, content: content, assets: datas)
    }

    /// Alınan tuvali kaydeder ya da (`existingId`) yayında günceller. `ids`: gönderenin resim kimliği → buradaki kimlik.
    func install(_ pkg: SharePackage, ids: inout [String: String], existingId: String? = nil) throws -> String {
        let h = pkg.header
        let localId = existingId ?? UUID().uuidString.lowercased()
        for (i, a) in h.assets.enumerated() {
            let newId = ids[a.id] ?? UUID().uuidString.lowercased()
            ids[a.id] = newId
            assets.saveData(localId, pkg.assets[i], a.mime, id: newId, width: a.w, height: a.h)
            CanvasImageCache.loader?(newId)
        }
        let doc = try ContentCodec.decodeDocument(pkg.content)
        let map = ids
        let pages = doc.pages.map { page in page.map { Self.remap($0, map) } }
        var background = h.background, guide = h.guide
        for (old, new) in map {
            background = background.replacingOccurrences(of: old, with: new)
            guide = guide.replacingOccurrences(of: old, with: new)
        }
        let existing = existingId.flatMap { store.getCanvas($0) }
        let now = CanvasStore.nowMs()
        let hex = h.backgroundColorHex.range(of: "^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$", options: .regularExpression) != nil ? h.backgroundColorHex : "#FFFFFF"
        var c = CanvasUi(localId: localId, name: String((h.name.isEmpty ? "Tuval" : h.name).prefix(120)),
                         width: min(max(h.width, 16), 20000), height: min(max(h.height, 16), 20000),
                         backgroundColorHex: hex, aiEnabled: h.aiEnabled, pages: pages.isEmpty ? [[]] : pages,
                         createdAt: existing?.createdAt ?? now, updatedAt: now, kind: CanvasKind(rawValue: h.canvasKind) ?? .ART)
        c.layers = doc.layers.isEmpty ? [Layer.Default] : doc.layers
        c.currentPage = min(max(h.currentPage, 0), c.pages.count - 1)
        let decoded = Self.decodeBackground(background)
        c.background = decoded.spec
        c.pageBackgrounds = decoded.pages
        c.pageSettings = decoded.settings
        if !guide.isEmpty, let g = try? JSONDecoder().decode(GuideSpec.self, from: Data(guide.utf8)) { c.guide = g }
        c.folderId = existing?.folderId
        store.save(c)
        return localId
    }

    nonisolated private static func remap(_ s: Stroke, _ ids: [String: String]) -> Stroke {
        var c = s
        if let a = s.assetId, let n = ids[a] { c.assetId = n }
        if let m = s.model, let n = ids[m.dataAssetId] { c.model = Model3DLink(modelId: m.modelId, dataAssetId: n) }
        return c
    }

    // MARK: Android arka plan kaydı biçimi

    nonisolated static func encodeBackground(_ spec: BackgroundSpec, _ pages: [PageBackground?], _ settings: PageSettings) -> String {
        var obj: [String: Any] = [:]
        if !spec.isEmpty, let d = try? JSONEncoder().encode(spec), let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] { obj = o }
        var trimmed = pages.map { p -> PageBackground? in (p?.isEmpty ?? true) ? nil : p }
        while let last = trimmed.last, last == nil { trimmed.removeLast() }
        if !trimmed.isEmpty {
            obj["pages"] = trimmed.map { p -> Any in
                guard let p, let d = try? JSONEncoder().encode(p), let o = try? JSONSerialization.jsonObject(with: d) else { return NSNull() }
                return o
            }
        }
        if !settings.isEmpty, let d = try? JSONEncoder().encode(settings), let o = try? JSONSerialization.jsonObject(with: d) { obj["pageSettings"] = o }
        if obj.isEmpty { return "" }
        guard let d = try? JSONSerialization.data(withJSONObject: obj, options: [.sortedKeys]) else { return "" }
        return String(data: d, encoding: .utf8) ?? ""
    }

    private struct BackgroundExtras: Decodable {
        var pages: [PageBackground?]?
        var pageSettings: PageSettings?
    }

    nonisolated static func decodeBackground(_ value: String) -> (spec: BackgroundSpec, pages: [PageBackground?], settings: PageSettings) {
        guard !value.isEmpty else { return (BackgroundSpec(), [], PageSettings()) }
        let d = Data(value.utf8)
        let spec = (try? JSONDecoder().decode(BackgroundSpec.self, from: d)) ?? BackgroundSpec()
        let extras = try? JSONDecoder().decode(BackgroundExtras.self, from: d)
        return (spec, extras?.pages ?? [], extras?.pageSettings ?? PageSettings())
    }
}
