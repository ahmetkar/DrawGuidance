import Foundation

/// Tuvallerin cihazda saklanması (Android Room `canvases` tablosunun ilk karşılığı): her tuval bir klasörde,
/// üst bilgisi `meta.json`, çizimleri Android ile aynı ikili biçimde `content.bin` (ContentCodec).
@MainActor
final class CanvasStore: ObservableObject {
    struct Meta: Codable, Equatable {
        var localId: String
        var name: String
        var width: Int
        var height: Int
        var backgroundColorHex: String
        var aiEnabled: Bool
        var createdAt: Int64
        var updatedAt: Int64
        var kind: CanvasKind
        var currentPage: Int
        var guide: GuideSpec
        var folderId: String?
        var background: BackgroundSpec
        var pageBackgrounds: [PageBackground?]
        var pageSettings: PageSettings
    }

    @Published private(set) var canvases: [CanvasUi] = []
    private let root: URL

    init(directory: URL? = nil) {
        root = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("canvases", isDirectory: true)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        reload()
    }

    static func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    func create(kind: CanvasKind, name: String) -> CanvasUi {
        let size = kind.defaultSize
        let now = Self.nowMs()
        let c = CanvasUi(localId: UUID().uuidString.lowercased(), name: name, width: size.width, height: size.height,
                         backgroundColorHex: "#FFFFFF", aiEnabled: true, pages: [[]], createdAt: now, updatedAt: now, kind: kind)
        save(c)
        return c
    }

    func save(_ canvas: CanvasUi) {
        var c = canvas
        c.updatedAt = Self.nowMs()
        let dir = root.appendingPathComponent(c.localId, isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let content = ContentCodec.encodePages(c.pages, layers: c.layers)
        c.sizeBytes = Int64(content.count)
        let meta = Meta(localId: c.localId, name: c.name, width: c.width, height: c.height, backgroundColorHex: c.backgroundColorHex,
                        aiEnabled: c.aiEnabled, createdAt: c.createdAt, updatedAt: c.updatedAt, kind: c.kind, currentPage: c.currentPage,
                        guide: c.guide, folderId: c.folderId, background: c.background, pageBackgrounds: c.pageBackgrounds,
                        pageSettings: c.pageSettings)
        try? content.write(to: dir.appendingPathComponent("content.bin"), options: .atomic)
        if let m = try? JSONEncoder().encode(meta) { try? m.write(to: dir.appendingPathComponent("meta.json"), options: .atomic) }
        if let i = canvases.firstIndex(where: { $0.localId == c.localId }) { canvases[i] = c } else { canvases.insert(c, at: 0) }
        canvases.sort { $0.updatedAt > $1.updatedAt }
    }

    func delete(_ id: String) {
        try? FileManager.default.removeItem(at: root.appendingPathComponent(id, isDirectory: true))
        canvases.removeAll { $0.localId == id }
    }

    func reload() {
        let dirs = (try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)) ?? []
        canvases = dirs.compactMap(load).sorted { $0.updatedAt > $1.updatedAt }
    }

    private func load(_ dir: URL) -> CanvasUi? {
        guard let m = try? Data(contentsOf: dir.appendingPathComponent("meta.json")),
              let meta = try? JSONDecoder().decode(Meta.self, from: m) else { return nil }
        let data = (try? Data(contentsOf: dir.appendingPathComponent("content.bin"))) ?? ContentCodec.EMPTY
        let doc = (try? ContentCodec.decodeDocument(data)) ?? ContentCodec.Document(pages: [[]], layers: [Layer.Default])
        return CanvasUi(localId: meta.localId, name: meta.name, width: meta.width, height: meta.height,
                        backgroundColorHex: meta.backgroundColorHex, aiEnabled: meta.aiEnabled, pages: doc.pages,
                        createdAt: meta.createdAt, updatedAt: meta.updatedAt, kind: meta.kind, currentPage: meta.currentPage,
                        guide: meta.guide, layers: doc.layers, folderId: meta.folderId, sizeBytes: Int64(data.count),
                        background: meta.background, pageBackgrounds: meta.pageBackgrounds, pageSettings: meta.pageSettings)
    }
}
