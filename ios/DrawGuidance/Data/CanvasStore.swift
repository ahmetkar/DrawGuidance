import Foundation

/// Tuvallerin cihazda saklanması (Android `CanvasRepository` + Room `canvases` tablosu karşılığı): her tuval bir
/// klasörde, üst bilgisi `meta.json`, çizimleri Android ile aynı ikili biçimde `content.bin` (ContentCodec).
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
    let files: CanvasFiles
    let saveQueue: CanvasSaveQueue

    init(directory: URL? = nil) {
        files = directory.map { CanvasFiles(root: $0) } ?? .shared
        saveQueue = CanvasSaveQueue(files: files)
        reload()
        saveQueue.onSaved = { [weak self] id in
            Task { @MainActor in self?.refresh(id) }
        }
    }

    nonisolated static func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    func create(kind: CanvasKind, name: String) -> CanvasUi {
        let size = kind.defaultSize
        let now = Self.nowMs()
        let c = CanvasUi(localId: UUID().uuidString.lowercased(), name: name, width: size.width, height: size.height,
                         backgroundColorHex: "#FFFFFF", aiEnabled: true, pages: [[]], createdAt: now, updatedAt: now, kind: kind)
        save(c)
        return c
    }

    static func meta(_ c: CanvasUi) -> Meta {
        Meta(localId: c.localId, name: c.name, width: c.width, height: c.height, backgroundColorHex: c.backgroundColorHex,
             aiEnabled: c.aiEnabled, createdAt: c.createdAt, updatedAt: c.updatedAt, kind: c.kind, currentPage: c.currentPage,
             guide: c.guide, folderId: c.folderId, background: c.background, pageBackgrounds: c.pageBackgrounds,
             pageSettings: c.pageSettings)
    }

    /// Tuvalin tamamını yazar (oluşturma, kopyalama, şablon).
    func save(_ canvas: CanvasUi) {
        var c = canvas
        c.updatedAt = Self.nowMs()
        let content = ContentCodec.encodePages(c.pages, layers: c.layers)
        c.sizeBytes = Int64(content.count)
        let meta = Self.meta(c)
        files.queue.sync {
            files.writeContent(c.localId, content)
            files.writeMeta(meta)
        }
        upsert(c)
    }

    func getCanvas(_ id: String) -> CanvasUi? {
        files.queue.sync { files.load(id) }
    }

    func delete(_ id: String) {
        files.queue.sync { try? FileManager.default.removeItem(at: files.dir(id)) }
        canvases.removeAll { $0.localId == id }
    }

    func reload() {
        let ids: [String] = files.queue.sync {
            ((try? FileManager.default.contentsOfDirectory(at: files.root, includingPropertiesForKeys: nil)) ?? []).map(\.lastPathComponent)
        }
        canvases = ids.compactMap { id in files.queue.sync { files.load(id) } }.sorted { $0.updatedAt > $1.updatedAt }
    }

    /// Kayıttan sonra listedeki tuvali yeniler.
    func refresh(_ id: String) {
        guard let c = files.queue.sync(execute: { files.load(id) }) else { return }
        upsert(c)
    }

    private func upsert(_ c: CanvasUi) {
        if let i = canvases.firstIndex(where: { $0.localId == c.localId }) { canvases[i] = c } else { canvases.insert(c, at: 0) }
        canvases.sort { $0.updatedAt > $1.updatedAt }
    }

    // MARK: Android CanvasRepository işlemleri

    func saveGuide(_ id: String, _ guide: GuideSpec) {
        files.updateMeta(id) { $0.guide = guide }
        refresh(id)
    }

    func saveBackground(_ id: String, _ hex: String) {
        files.updateMeta(id) { $0.backgroundColorHex = hex }
        refresh(id)
    }

    func saveBackgroundSpec(_ id: String, _ spec: BackgroundSpec, _ pages: [PageBackground?] = []) {
        files.updateMeta(id) { $0.background = spec; $0.pageBackgrounds = pages }
        refresh(id)
    }

    func savePageSettings(_ id: String, _ settings: PageSettings) {
        files.updateMeta(id) { $0.pageSettings = settings }
        refresh(id)
    }

    func rename(_ id: String, _ name: String) {
        files.updateMeta(id) { $0.name = name }
        refresh(id)
    }

    func setAiEnabled(_ id: String, _ enabled: Bool) {
        files.updateMeta(id, touch: false) { $0.aiEnabled = enabled }
        refresh(id)
    }

    func setFolder(_ id: String, _ folderId: String?) {
        files.updateMeta(id, touch: false) { $0.folderId = folderId }
        refresh(id)
    }

    func updateSettings(_ id: String, name: String, width: Int, height: Int, background: String, kind: CanvasKind? = nil) {
        files.updateMeta(id) { m in
            m.name = name; m.width = width; m.height = height; m.backgroundColorHex = background
            if let kind { m.kind = kind }
        }
        refresh(id)
    }

    func duplicate(_ id: String, name: String) -> CanvasUi? {
        guard var c = getCanvas(id) else { return nil }
        let now = Self.nowMs()
        c.localId = UUID().uuidString.lowercased()
        c.name = name
        c.createdAt = now
        save(c)
        return canvases.first { $0.localId == c.localId }
    }
}
