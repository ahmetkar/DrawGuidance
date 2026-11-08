import CoreGraphics
import Foundation
import ImageIO

/// Çözülmüş resimlerin bellek önbelleği (Android `CanvasImageCache`): tüm çizim yüzeyleri buradan okur. Bellekte
/// olmayan resim istenince [loader] ile (en çok saniyede bir) yeniden yüklenir; [version] her yüklemede artar.
enum CanvasImageCache {
    private static let cache: NSCache<NSString, CGImage> = {
        let c = NSCache<NSString, CGImage>()
        c.totalCostLimit = 64 * 1024 * 1024
        return c
    }()
    private static let lock = NSLock()
    private static var lastRequest: [String: TimeInterval] = [:]
    private(set) static var version = 0

    /// Bellekte olmayan resmi yükler (tuval resimleri deposu bağlar).
    static var loader: ((String) -> Void)?

    static func get(_ id: String?) -> CGImage? {
        guard let id else { return nil }
        if let hit = cache.object(forKey: id as NSString) { return hit }
        let now = Date().timeIntervalSince1970
        lock.lock()
        let due = now - (lastRequest[id] ?? 0) > 1
        if due { lastRequest[id] = now }
        lock.unlock()
        if due { loader?(id) }
        return nil
    }

    static func put(_ id: String, _ image: CGImage) {
        cache.setObject(image, forKey: id as NSString, cost: image.width * image.height * 4)
        lock.lock(); version += 1; lock.unlock()
        NotificationCenter.default.post(name: changed, object: nil)
    }

    static func contains(_ id: String) -> Bool { cache.object(forKey: id as NSString) != nil }

    static func clear() { cache.removeAllObjects(); lock.lock(); version += 1; lock.unlock() }

    /// Yeni resim yüklendi (çizim yüzeyleri yeniden çizer).
    static let changed = Notification.Name("CanvasImageCache.changed")

    /// Bayt dizisinden görüntü (PNG, JPEG, WEBP, HEIC).
    static func decode(_ data: Data, maxSide: Int? = nil) -> CGImage? {
        guard let src = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        if let maxSide {
            let opts: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceThumbnailMaxPixelSize: maxSide,
                                         kCGImageSourceCreateThumbnailWithTransform: true]
            return CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary)
        }
        return CGImageSourceCreateImageAtIndex(src, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
    }
}

/// Tuval resimlerinin cihazda saklanması (Android `CanvasAssetStore` / `canvas_assets` tablosu): her resim tuvalin
/// klasöründe `assets/<kimlik>` dosyası ve `<kimlik>.json` üst bilgisi.
final class CanvasAssetStore {
    struct Meta: Codable {
        var id: String
        var canvasLocalId: String
        var mimeType: String
        var width: Int
        var height: Int
        var createdAt: Int64
    }

    static let shared = CanvasAssetStore()

    private let root: URL
    private let queue = DispatchQueue(label: "CanvasAssetStore", qos: .userInitiated)
    private var index: [String: Meta] = [:]
    private let lock = NSLock()

    init(directory: URL? = nil) {
        root = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("assets", isDirectory: true)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        loadIndex()
    }

    /// Önbellekten düşen resimleri bu depodan yeniden çözer; galeriden eklenen arka plan resimlerini de bağlar.
    func install() {
        CanvasImageCache.loader = { [weak self] id in self?.queue.async { self?.decodeIntoCache(id) } }
        PaperLibrary.assetImage = { id in CanvasImageCache.get(id) }
    }

    private func dir(_ canvas: String) -> URL { root.appendingPathComponent(canvas, isDirectory: true) }

    private func loadIndex() {
        let canvases = (try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)) ?? []
        var out: [String: Meta] = [:]
        for d in canvases {
            let files = (try? FileManager.default.contentsOfDirectory(at: d, includingPropertiesForKeys: nil)) ?? []
            for f in files where f.pathExtension == "json" {
                if let data = try? Data(contentsOf: f), let m = try? JSONDecoder().decode(Meta.self, from: data) { out[m.id] = m }
            }
        }
        lock.lock(); index = out; lock.unlock()
    }

    private func meta(_ id: String) -> Meta? { lock.lock(); defer { lock.unlock() }; return index[id] }

    private func file(_ m: Meta) -> URL { dir(m.canvasLocalId).appendingPathComponent(m.id) }

    /// Hazırlanmış resmi kaydeder; kimliği döner.
    @discardableResult
    func save(_ canvasLocalId: String, _ image: PreparedImage, id: String = UUID().uuidString.lowercased()) -> String {
        saveData(canvasLocalId, image.bytes, image.mimeType, id: id, width: image.width, height: image.height)
        CanvasImageCache.put(id, image.image)
        return id
    }

    /// Resim olmayan veri de (3B model) tuvale bağlı saklanır.
    @discardableResult
    func saveData(_ canvasLocalId: String, _ bytes: Data, _ mimeType: String, id: String = UUID().uuidString.lowercased(),
                  width: Int = 0, height: Int = 0) -> String {
        let m = Meta(id: id, canvasLocalId: canvasLocalId, mimeType: mimeType, width: width, height: height, createdAt: Int64(Date().timeIntervalSince1970 * 1000))
        try? FileManager.default.createDirectory(at: dir(canvasLocalId), withIntermediateDirectories: true)
        try? bytes.write(to: file(m), options: .atomic)
        if let j = try? JSONEncoder().encode(m) { try? j.write(to: dir(canvasLocalId).appendingPathComponent(id + ".json"), options: .atomic) }
        lock.lock(); index[id] = m; lock.unlock()
        return id
    }

    func bytes(_ id: String) -> Data? { meta(id).flatMap { try? Data(contentsOf: file($0)) } }

    func mimeType(_ id: String) -> String? { meta(id)?.mimeType }

    /// Bir resmi başka tuvale kopyalar (yeni kimlik).
    func copyTo(_ id: String, _ canvasLocalId: String) -> String? {
        guard let m = meta(id), let data = bytes(id) else { return nil }
        let newId = saveData(canvasLocalId, data, m.mimeType, width: m.width, height: m.height)
        if let img = CanvasImageCache.get(id) { CanvasImageCache.put(newId, img) }
        return newId
    }

    private func decodeIntoCache(_ id: String) {
        guard !CanvasImageCache.contains(id), let data = bytes(id), let img = CanvasImageCache.decode(data) else { return }
        CanvasImageCache.put(id, img)
    }

    /// Çizimlerin ve arka planların kullandığı resimleri belleğe alır (bekleyerek; dışa aktarmadan önce).
    func ensureLoaded(_ canvas: CanvasUi) {
        var ids = Set<String>()
        func spec(_ s: BackgroundSpec) {
            if let a = s.assetId { ids.insert(a) }
            if let p = s.paperImageId { _ = PaperLibrary.image(p) }
        }
        spec(canvas.background)
        for pb in canvas.pageBackgrounds { if let s = pb?.spec { spec(s) } }
        for page in canvas.pages {
            for s in page {
                if let a = s.assetId { ids.insert(a) }
                if let box = s.text?.box?.imageId {
                    if box.hasPrefix(BackgroundSpec.ASSET) { ids.insert(String(box.dropFirst(BackgroundSpec.ASSET.count))) } else { _ = PaperLibrary.image(box) }
                }
            }
        }
        for id in ids where !CanvasImageCache.contains(id) { decodeIntoCache(id) }
    }

    /// Hiçbir çizimin kullanmadığı resimleri siler ([keep]: arka plan resmi gibi ek kimlikler).
    func prune(_ canvasLocalId: String, _ strokes: [Stroke], keep: Set<String> = []) {
        var used = keep
        for s in strokes {
            if let a = s.assetId { used.insert(a) }
            if let d = s.model?.dataAssetId { used.insert(d) }
            if let b = s.text?.box?.imageId, b.hasPrefix(BackgroundSpec.ASSET) { used.insert(String(b.dropFirst(BackgroundSpec.ASSET.count))) }
        }
        lock.lock()
        let unused = index.values.filter { $0.canvasLocalId == canvasLocalId && !used.contains($0.id) }
        for m in unused { index[m.id] = nil }
        lock.unlock()
        for m in unused {
            try? FileManager.default.removeItem(at: file(m))
            try? FileManager.default.removeItem(at: dir(canvasLocalId).appendingPathComponent(m.id + ".json"))
        }
    }

    func deleteForCanvas(_ canvasLocalId: String) {
        lock.lock(); index = index.filter { $0.value.canvasLocalId != canvasLocalId }; lock.unlock()
        try? FileManager.default.removeItem(at: dir(canvasLocalId))
    }

    func totalBytes(_ canvasLocalId: String) -> Int64 {
        let files = (try? FileManager.default.contentsOfDirectory(at: dir(canvasLocalId), includingPropertiesForKeys: [.fileSizeKey])) ?? []
        return files.filter { $0.pathExtension != "json" }.reduce(Int64(0)) { acc, f in
            acc + Int64((try? f.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
        }
    }
}
