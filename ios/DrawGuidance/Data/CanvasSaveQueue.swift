import Foundation

/// Tuval klasörlerinin dosya işlemleri (Android `CanvasDao` karşılığı): her tuval bir klasörde, üst bilgi `meta.json`,
/// çizimler `content.bin` (ContentCodec). Tüm yazmalar tek bir seri kuyrukta yapılır.
final class CanvasFiles: @unchecked Sendable {
    static let shared = CanvasFiles()
    let root: URL
    let queue = DispatchQueue(label: "drawguidance.canvasfiles")

    init(root: URL? = nil) {
        self.root = root ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("canvases", isDirectory: true)
        try? FileManager.default.createDirectory(at: self.root, withIntermediateDirectories: true)
    }

    func dir(_ id: String) -> URL { root.appendingPathComponent(id, isDirectory: true) }

    func readMeta(_ id: String) -> CanvasStore.Meta? {
        guard let m = try? Data(contentsOf: dir(id).appendingPathComponent("meta.json")) else { return nil }
        return try? JSONDecoder().decode(CanvasStore.Meta.self, from: m)
    }

    func writeMeta(_ meta: CanvasStore.Meta) {
        let d = dir(meta.localId)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        if let m = try? JSONEncoder().encode(meta) { try? m.write(to: d.appendingPathComponent("meta.json"), options: .atomic) }
    }

    func writeContent(_ id: String, _ data: Data) {
        let d = dir(id)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        try? data.write(to: d.appendingPathComponent("content.bin"), options: .atomic)
    }

    func load(_ id: String) -> CanvasUi? {
        guard let meta = readMeta(id) else { return nil }
        let data = (try? Data(contentsOf: dir(id).appendingPathComponent("content.bin"))) ?? ContentCodec.EMPTY
        let doc = (try? ContentCodec.decodeDocument(data)) ?? ContentCodec.Document(pages: [[]], layers: [Layer.Default])
        return CanvasUi(localId: meta.localId, name: meta.name, width: meta.width, height: meta.height,
                        backgroundColorHex: meta.backgroundColorHex, aiEnabled: meta.aiEnabled, pages: doc.pages.isEmpty ? [[]] : doc.pages,
                        createdAt: meta.createdAt, updatedAt: meta.updatedAt, kind: meta.kind, currentPage: meta.currentPage,
                        guide: meta.guide, layers: doc.layers, folderId: meta.folderId, sizeBytes: Int64(data.count),
                        background: meta.background, pageBackgrounds: meta.pageBackgrounds, pageSettings: meta.pageSettings)
    }

    /// Üst bilgiyi değiştirip yazar (seri kuyrukta, eşzamanlı).
    func updateMeta(_ id: String, touch: Bool = true, _ block: (inout CanvasStore.Meta) -> Void) {
        queue.sync {
            guard var meta = readMeta(id) else { return }
            block(&meta)
            if touch { meta.updatedAt = CanvasStore.nowMs() }
            writeMeta(meta)
        }
    }

    /// Android `CanvasRepository.saveDocument`.
    func saveDocument(_ id: String, pages: [[Stroke]], width: Int, height: Int, sizeChanged: Bool,
                      background: String?, currentPage: Int?, layers: [Layer]) {
        let content = ContentCodec.encodePages(pages, layers: layers)
        queue.sync {
            writeContent(id, content)
            guard var meta = readMeta(id) else { return }
            if sizeChanged { meta.width = width; meta.height = height }
            if let background { meta.backgroundColorHex = background }
            if let currentPage { meta.currentPage = currentPage }
            meta.updatedAt = CanvasStore.nowMs()
            writeMeta(meta)
        }
    }
}

/// Kayıt kuyruğu (Android `CanvasSaveQueue`): art arda gelen değişiklikler kısa bir süre birleştirilip arka planda
/// yazılır; [flushNow] beklemeden yazar.
final class CanvasSaveQueue: @unchecked Sendable {
    static let COALESCE_MS = 400

    private struct Pending {
        var pages: [[Stroke]]; var width: Int; var height: Int; var sizeChanged: Bool
        var background: String?; var currentPage: Int?; var layers: [Layer]
    }

    private let files: CanvasFiles
    private let lock = NSLock()
    private var pending = [String: Pending]()
    private var scheduled = Set<String>()
    private let work = DispatchQueue(label: "drawguidance.savequeue")
    /// Yazıldıktan sonra (ana iş parçacığında) çağrılır: tuval listesi güncellensin.
    var onSaved: (@Sendable (String) -> Void)?

    init(files: CanvasFiles = .shared) { self.files = files }

    func enqueue(_ id: String, _ pages: [[Stroke]], _ width: Int, _ height: Int, _ sizeChanged: Bool,
                 _ background: String? = nil, _ currentPage: Int? = nil, _ layers: [Layer] = [Layer.Default]) {
        lock.lock()
        let new = Pending(pages: pages, width: width, height: height, sizeChanged: sizeChanged, background: background, currentPage: currentPage, layers: layers)
        if let old = pending[id] {
            var m = new
            m.sizeChanged = old.sizeChanged || new.sizeChanged
            m.background = new.background ?? old.background
            m.currentPage = new.currentPage ?? old.currentPage
            pending[id] = m
        } else {
            pending[id] = new
        }
        let schedule = scheduled.insert(id).inserted
        lock.unlock()
        if schedule {
            work.asyncAfter(deadline: .now() + .milliseconds(Self.COALESCE_MS)) { [weak self] in self?.write(id) }
        }
    }

    private func write(_ id: String) {
        lock.lock()
        scheduled.remove(id)
        let job = pending.removeValue(forKey: id)
        lock.unlock()
        guard let job else { return }
        files.saveDocument(id, pages: job.pages, width: job.width, height: job.height, sizeChanged: job.sizeChanged,
                           background: job.background, currentPage: job.currentPage, layers: job.layers)
        if let cb = onSaved { DispatchQueue.main.async { cb(id) } }
    }

    /// Bekleyen tüm kayıtları hemen yazar (uygulama arka plana geçerken).
    func flushNow() {
        lock.lock()
        let ids = Array(pending.keys)
        lock.unlock()
        work.sync { for id in ids { write(id) } }
    }

    var isIdle: Bool { lock.lock(); defer { lock.unlock() }; return pending.isEmpty }
}
