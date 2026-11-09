import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// v25 3B düzenleyicinin cihaz sınıfına göre bütçeleri (Android `Budget3D`; sınıf bellek miktarından).
enum Budget3D {
    enum Tier { case LOW, MID, HIGH }

    static let tier: Tier = {
        let gb = Double(ProcessInfo.processInfo.physicalMemory) / 1_073_741_824
        return gb < 2.5 ? .LOW : gb < 5.5 ? .MID : .HIGH
    }()

    static var csgTriangles: Int { tier == .LOW ? 4_000 : tier == .MID ? 12_000 : 30_000 }
    static var brushSides: Int { tier == .LOW ? 8 : tier == .MID ? 10 : 14 }
    static var brushSamples: Int { tier == .LOW ? 90 : tier == .MID ? 160 : 240 }
    static var holeSegments: Int { tier == .LOW ? 16 : tier == .MID ? 24 : 32 }
    /// Resim dışa aktarmada en uzun kenar (piksel).
    static var exportMaxSide: Int { tier == .LOW ? 1600 : tier == .MID ? 2048 : 3072 }
    /// Yazılım çizicinin bir karede işleyebileceği en çok piksel (örnekleme dahil).
    static var exportPixels: Int { tier == .LOW ? 2_500_000 : tier == .MID ? 6_000_000 : 12_000_000 }
    static var defaultQuality: Int { tier == .LOW ? Model3DPrefs.QUALITY_FAST : tier == .MID ? Model3DPrefs.QUALITY_BALANCED : Model3DPrefs.QUALITY_HIGH }
    static var softwareScale: Float { tier == .LOW ? 0.75 : 1 }
    /// İçe aktarmada önerilen ayrıntı (0 az, 1 orta, 2 çok).
    static var importDetail: Int { tier == .LOW ? 0 : 1 }
}

/// ARGB pikseller ile görüntü dosyaları arasında dönüşüm.
enum PixelCodec {
    /// PNG/JPEG/WEBP verisinden ARGB pikseller (en çok [maxSide] piksele küçültülür).
    static func decode(_ bytes: [UInt8], maxSide: Int = 0) -> Texture? {
        guard let src = CGImageSourceCreateWithData(Data(bytes) as CFData, nil),
              let img = CGImageSourceCreateImageAtIndex(src, 0, nil) else { return nil }
        return texture(img, maxSide: maxSide)
    }

    static func texture(_ img: CGImage, maxSide: Int = 0) -> Texture? {
        var w = img.width, h = img.height
        if maxSide > 0 && max(w, h) > maxSide {
            let k = Double(maxSide) / Double(max(w, h))
            w = max(1, Int(Double(w) * k)); h = max(1, Int(Double(h) * k))
        }
        var data = [UInt8](repeating: 0, count: w * h * 4)
        let ok = data.withUnsafeMutableBytes { buf -> Bool in
            guard let ctx = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: Bitmaps.space,
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            ctx.interpolationQuality = .high
            ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        guard ok else { return nil }
        var px = [UInt32](repeating: 0, count: w * h)
        for i in 0..<(w * h) {
            let a = UInt32(data[4 * i + 3])
            var r = UInt32(data[4 * i]), g = UInt32(data[4 * i + 1]), b = UInt32(data[4 * i + 2])
            if a > 0 && a < 255 { r = min(255, r * 255 / a); g = min(255, g * 255 / a); b = min(255, b * 255 / a) }
            px[i] = a << 24 | r << 16 | g << 8 | b
        }
        return Texture(w, h, px)
    }

    /// Görüntüyü PNG ya da JPEG olarak kodlar.
    static func encode(_ img: CGImage, jpeg: Bool = false, quality: Double = 0.92) -> Data? {
        let out = NSMutableData()
        let type = (jpeg ? UTType.jpeg : UTType.png).identifier as CFString
        guard let dest = CGImageDestinationCreateWithData(out, type, 1, nil) else { return nil }
        CGImageDestinationAddImage(dest, img, jpeg ? [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary : nil)
        guard CGImageDestinationFinalize(dest) else { return nil }
        return out as Data
    }

    static func encode(_ px: [UInt32], _ w: Int, _ h: Int, jpeg: Bool = false) -> Data? {
        encode(Bitmaps.image(argb: jpeg ? px.map { $0 | 0xFF00_0000 } : px, w, h), jpeg: jpeg)
    }
}

/// v24 3B düzenleyicinin çizim tercihleri (Android `Model3DPrefs`; iOS'ta yalnız yazılım çizici).
final class Model3DPrefs {
    static let QUALITY_HIGH = 0, QUALITY_BALANCED = 1, QUALITY_FAST = 2
    static let shared = Model3DPrefs()

    private let defaults: UserDefaults
    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    var quality: Int {
        get { (defaults.object(forKey: "m3d.quality") as? Int ?? Budget3D.defaultQuality).coerceIn(Self.QUALITY_HIGH, Self.QUALITY_FAST) }
        set { defaults.set(newValue.coerceIn(Self.QUALITY_HIGH, Self.QUALITY_FAST), forKey: "m3d.quality") }
    }
    var suggest: Bool {
        get { defaults.object(forKey: "m3d.suggest") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "m3d.suggest") }
    }
    var sketchModel: String? {
        get { defaults.string(forKey: "m3d.sketchModel") }
        set { defaults.set(newValue, forKey: "m3d.sketchModel") }
    }
}

/// Doku seçme ekranındaki öge.
struct TextureItem: Identifiable, Hashable { let id: String; let name: String; let category: String }

/// v23 hazır 3B dokularının deposu (Android `TextureRepository`): katalog dokuları ilk istendiğinde üretilir ve PNG
/// olarak saklanır; içe aktarılan dokular "imp.…" kimliğiyle saklanır. Çözülmüş dokular bellekte (sınırlı) tutulur.
final class TextureRepository: @unchecked Sendable {
    static let shared = TextureRepository()
    static let IMPORTED = "İçe aktarılan"

    private let lock = NSLock()
    private var cache: [String: Texture] = [:]
    private var order: [String] = []
    private var bytes = 0
    private let limit = 24 * 1024 * 1024
    private let dir: URL

    let items: [TextureItem] = TextureCatalog.all.map { TextureItem(id: $0.id, name: $0.name.tr, category: $0.category.tr) }

    init(directory: URL? = nil) {
        dir = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("textures3d", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    private func file(_ id: String) -> URL { dir.appendingPathComponent(id.replacingOccurrences(of: "/", with: "_") + "-v\(TextureCatalog.VERSION).png") }

    private func put(_ id: String, _ t: Texture) {
        lock.lock(); defer { lock.unlock() }
        if cache[id] == nil { order.append(id); bytes += t.px.count * 4 }
        cache[id] = t
        while bytes > limit, order.count > 1 {
            let old = order.removeFirst()
            if let o = cache.removeValue(forKey: old) { bytes -= o.px.count * 4 }
        }
    }

    /// Bellekteki doku (çizici için; yoksa nil). Her iş parçacığından çağrılabilir.
    func cached(_ id: String) -> Texture? { lock.lock(); defer { lock.unlock() }; return cache[id] }

    /// Dokuyu çözer (dosyada yoksa üretip yazar). Ağır olabilir: arka planda çağrılmalı.
    @discardableResult
    func texture(_ id: String) -> Texture? {
        if let t = cached(id) { return t }
        let f = file(id)
        if let d = try? Data(contentsOf: f), let t = PixelCodec.decode([UInt8](d)) { put(id, t); return t }
        guard let def = TextureCatalog.byId(id) else { return nil }
        let t = def.generate(TextureCatalog.SIZE)
        put(id, t)
        if let png = PixelCodec.encode(t.px, t.w, t.h) { try? png.write(to: f, options: .atomic) }
        return t
    }

    func saveImported(_ id: String, _ texture: Texture) {
        put(id, texture)
        if let png = PixelCodec.encode(texture.px, texture.w, texture.h) { try? png.write(to: file(id), options: .atomic) }
    }

    func preload<S: Sequence>(_ ids: S) where S.Element == String { for id in ids { texture(id) } }

    static func ids(_ m: Model3D) -> Set<String> {
        var s = Set<String>()
        for p in m.parts {
            if let t = p.material.texture { s.insert(t) }
            for fm in p.faceMaterials.values { if let t = fm.texture { s.insert(t) } }
        }
        return s
    }
}

/// 3B Şekillerim listesindeki kayıt (Android `Model3DInfo`).
struct Model3DInfo: Identifiable, Equatable {
    let id: String
    var name: String
    var createdAt: Int64
    var updatedAt: Int64
    var source: String
    var thumbURL: URL
}

/// v23 3B şekillerin deposu (Android `Model3DRepository`): her model `<kimlik>.glb` (dokular kimlikle), üst bilgisi
/// `<kimlik>.json` ve küçük resmi `<kimlik>.png` olarak saklanır. Örnek modeller ilk açılışta eklenir.
@MainActor
final class Model3DRepository: ObservableObject {
    static let USER = "user", SAMPLE = "sample"
    static let THUMB = 192
    /// İçe aktarmada ayrıntı seçenekleri (üçgen sayısı): az, orta, çok.
    static let DETAIL_LEVELS = [2_000, 5_000, 12_000]

    private struct Meta: Codable { var id: String; var name: String; var createdAt: Int64; var updatedAt: Int64; var source: String }

    @Published private(set) var models: [Model3DInfo] = []
    let textures: TextureRepository
    private let dir: URL
    private let defaults: UserDefaults

    init(directory: URL? = nil, textures: TextureRepository = .shared, defaults: UserDefaults = .standard) {
        self.textures = textures
        self.defaults = defaults
        dir = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("models3d", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        reload()
    }

    private func url(_ id: String, _ ext: String) -> URL { dir.appendingPathComponent("\(id).\(ext)") }

    func reload() {
        let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        var list: [Model3DInfo] = []
        for f in files where f.pathExtension == "json" {
            guard let d = try? Data(contentsOf: f), let m = try? JSONDecoder().decode(Meta.self, from: d) else { continue }
            list.append(Model3DInfo(id: m.id, name: m.name, createdAt: m.createdAt, updatedAt: m.updatedAt, source: m.source, thumbURL: url(m.id, "png")))
        }
        models = list.sorted { $0.updatedAt != $1.updatedAt ? $0.updatedAt > $1.updatedAt : $0.id < $1.id }
    }

    static func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// Örnek modelleri bir kez ekler (arka planda çizilen küçük resimlerle).
    func warmUp() async {
        if defaults.integer(forKey: "m3d.samples") >= Samples3D.VERSION { return }
        let existing = Set(models.filter { $0.source == Self.SAMPLE }.map(\.id))
        let now = Self.nowMs()
        for (i, m) in Samples3D.all.enumerated() where !existing.contains(m.id) {
            await upsert(m, Self.SAMPLE, now - Int64(i) * 1000, now - Int64(i) * 1000)
        }
        defaults.set(Samples3D.VERSION, forKey: "m3d.samples")
        reload()
    }

    func load(_ id: String) async -> Model3D? {
        let u = url(id, "glb"), meta = meta(id)
        return await Task.detached(priority: .userInitiated) { () -> Model3D? in
            guard let d = try? Data(contentsOf: u), var m = try? Model3DStore.decode(d) else { return nil }
            m.id = id
            if let meta { m.name = meta.name }
            return m
        }.value
    }

    func bytes(_ id: String) -> Data? { try? Data(contentsOf: url(id, "glb")) }

    private func meta(_ id: String) -> Meta? {
        guard let d = try? Data(contentsOf: url(id, "json")) else { return nil }
        return try? JSONDecoder().decode(Meta.self, from: d)
    }

    @discardableResult
    func save(_ model: Model3D) async -> String {
        let now = Self.nowMs()
        let old = meta(model.id)
        await upsert(model, old?.source ?? Self.USER, old?.createdAt ?? now, now)
        reload()
        return model.id
    }

    func saveCopy(_ model: Model3D, _ name: String) async -> Model3D {
        var copy = model
        copy.id = UUID().uuidString.lowercased()
        copy.name = name
        await save(copy)
        return copy
    }

    func rename(_ id: String, _ name: String) {
        guard var m = meta(id) else { return }
        m.name = name
        m.updatedAt = Self.nowMs()
        if let d = try? JSONEncoder().encode(m) { try? d.write(to: url(id, "json"), options: .atomic) }
        reload()
    }

    func delete(_ ids: [String]) {
        for id in ids { for e in ["glb", "json", "png"] { try? FileManager.default.removeItem(at: url(id, e)) } }
        reload()
    }

    private func upsert(_ model: Model3D, _ source: String, _ created: Int64, _ updated: Int64) async {
        let tex = textures
        let glb = url(model.id, "glb"), png = url(model.id, "png"), js = url(model.id, "json")
        let meta = Meta(id: model.id, name: model.name, createdAt: created, updatedAt: updated, source: source)
        await Task.detached(priority: .utility) {
            try? Model3DStore.encode(model).write(to: glb, options: .atomic)
            if let img = Self.render(model, Self.THUMB, Self.THUMB, tex), let d = PixelCodec.encode(img) { try? d.write(to: png, options: .atomic) }
            if let d = try? JSONEncoder().encode(meta) { try? d.write(to: js, options: .atomic) }
        }.value
    }

    /// Hazır şeklin yeni kimlikli kopyası.
    func fromCatalog(_ catalogId: String) -> Model3D? {
        let id = catalogId.hasPrefix(Catalog3D.PREFIX) ? String(catalogId.dropFirst(Catalog3D.PREFIX.count)) : catalogId
        guard var m = Catalog3D.byId(id)?.build() else { return nil }
        m.id = UUID().uuidString.lowercased()
        return m
    }

    /// Başka programın glTF/GLB dosyasını içe aktarır (kaydetmez); dokular saklanır.
    func importGlb(_ data: [UInt8], _ name: String, _ maxTriangles: Int) async throws -> GltfImport {
        let tex = textures
        return try await Task.detached(priority: .userInitiated) {
            let r = try Gltf3.importModel(data, name, maxTriangles, { b, _ in PixelCodec.decode(b) })
            for t in r.textures { tex.saveImported(t.id, t.texture) }
            return r
        }.value
    }

    /// Dokular gömülü GLB.
    func exportGlb(_ model: Model3D) async -> [UInt8] {
        let tex = textures
        return await Task.detached(priority: .userInitiated) {
            tex.preload(TextureRepository.ids(model))
            return Gltf3.write(model, GltfWriteOptions(embedTextures: true, textures: { tex.cached($0) }))
        }.value
    }

    /// Modelin saydam arka planlı resmi (modele sığdırılmış). Arka planda çağrılır.
    nonisolated static func render(_ model: Model3D, _ w: Int, _ h: Int, _ tex: TextureRepository, ground: Bool = false, margin: Float = 0.06) -> CGImage? {
        tex.preload(TextureRepository.ids(model))
        let view = View3.fit(model, model.camera, w, h, margin)
        let f = Renderer3D.render(model, view, RenderOptions(textures: { tex.cached($0) }, supersample: 2, ground: ground, background: 0))
        return Bitmaps.image(argb: f.color, f.width, f.height)
    }
}

/// v23 3B düzenleyiciden tuvale dönen sonuçlar (Android `Model3DResults`).
@MainActor
final class Model3DResults: ObservableObject {
    struct Result { let canvasId: String; let strokeId: String?; let model: Model3D }
    static let shared = Model3DResults()
    @Published private(set) var pending: [Result] = []

    func post(_ r: Result) {
        pending = pending.filter { !($0.canvasId == r.canvasId && $0.strokeId == r.strokeId && $0.strokeId != nil) } + [r]
    }

    func take(_ canvasId: String) -> [Result] {
        let taken = pending.filter { $0.canvasId == canvasId }
        pending.removeAll { $0.canvasId == canvasId }
        return taken
    }
}
