import Foundation

/// Kütüphane şekillerinin tek kaydı (Android `ShapeLibrary`). Hazır şekiller uygulamayla gelen katalogdan
/// (`catalog-vectors.bin`, Android'in ürettiği aynı veri) okunur; sunucudan gelenler aynı kimliklilerin yerine geçer.
final class ShapeLibrary {
    static let PREFIX = "lib."
    static let shared = ShapeLibrary()

    private(set) var bundledCategories: [LibCategory] = []
    private var bundledById: [String: LibShape] = [:]
    private var remoteById: [String: LibShape] = [:]
    private var merged: [LibCategory]?
    private let lock = NSLock()

    init(bundle: Bundle = .main) {
        load(bundle: bundle)
    }

    var categories: [LibCategory] { lock.withLock { merged ?? bundledCategories } }

    func find(_ id: String) -> LibShape? {
        let key = id.hasPrefix(Self.PREFIX) ? String(id.dropFirst(Self.PREFIX.count)) : id
        // v45 parametreli yerleşik şekiller sunucudaki sabit çizimle değişmez (değerleri, formülü, tutamakları var)
        if let p = ParamShapes.shape(key) { return p }
        if let s = lock.withLock({ remoteById[key] ?? bundledById[key] }) { return s }
        // Çizgi roman karakterleri katalogda yok: kimlikten üretilir (Android ile aynı)
        return key.hasPrefix(ComicCharacters.ID_PREFIX) ? ComicCharacters.shape(key) : nil
    }

    func forAudience(_ audience: Audience) -> [LibCategory] {
        categories.filter { Self.shownIn($0.audience, audience) }
    }

    static func shownIn(_ category: Audience, _ audience: Audience) -> Bool {
        switch audience {
        case .KIDS: return category == .KIDS
        case .BOTH: return category != .KIDS
        case .ART, .NOTES: return category == audience || category == .BOTH
        }
    }

    /// Sunucunun kataloğunu uygular (Android `install`).
    func install(_ remote: [CatalogVector]) {
        lock.withLock {
            if remote.isEmpty { remoteById = [:]; merged = nil; return }
            var shapes: [String: LibShape] = [:]
            for v in remote { shapes[v.id] = Self.libShape(v) }
            let fresh = remote.filter { bundledById[$0.id] == nil }
            let known = Set(bundledCategories.map(\.id))
            var updated = bundledCategories.map { c -> LibCategory in
                var c = c
                c.shapes = c.shapes.map { shapes[$0.id] ?? $0 } + fresh.filter { $0.categoryId == c.id }.map { shapes[$0.id]! }
                return c
            }
            var order: [String] = []
            var groups: [String: [CatalogVector]] = [:]
            for v in fresh where !known.contains(v.categoryId) {
                if groups[v.categoryId] == nil { order.append(v.categoryId) }
                groups[v.categoryId, default: []].append(v)
            }
            for id in order {
                let list = groups[id]!
                updated.append(LibCategory(id: id, title: list[0].categoryTitle, audience: list[0].audience,
                                           shapes: list.map(Self.libShape), section: list[0].section))
            }
            remoteById = shapes
            merged = updated
        }
    }

    static func libShape(_ v: CatalogVector) -> LibShape {
        LibShape(id: v.id, title: v.title, fit: v.fit, aspect: v.aspect, tags: v.tags, paths: v.paths)
    }

    private struct CategoryInfo: Decodable {
        let id: String
        let title: String
        let audience: String
        let section: String
        let listed: Bool
    }

    private func load(bundle: Bundle) {
        guard let vurl = bundle.url(forResource: "catalog-vectors", withExtension: "bin"),
              let data = try? Data(contentsOf: vurl),
              let section = try? CatalogBinary.decodeVectors(data) else { return }
        var infos: [CategoryInfo] = []
        if let curl = bundle.url(forResource: "catalog-categories", withExtension: "json"),
           let cdata = try? Data(contentsOf: curl) {
            infos = (try? JSONDecoder().decode([CategoryInfo].self, from: cdata)) ?? []
        }
        var byCategory: [String: [LibShape]] = [:]
        var order: [String] = []
        var first: [String: CatalogVector] = [:]
        for v in section.items {
            if byCategory[v.categoryId] == nil { order.append(v.categoryId); first[v.categoryId] = v }
            let s = Self.libShape(v)
            byCategory[v.categoryId, default: []].append(s)
            bundledById[s.id] = s
        }
        let infoById = Dictionary(infos.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let ids = infos.isEmpty ? order : infos.map(\.id) + order.filter { infoById[$0] == nil }
        bundledCategories = ids.compactMap { id in
            guard let shapes = byCategory[id] else { return nil }
            let v = first[id]!
            let info = infoById[id]
            return LibCategory(id: id, title: info?.title ?? v.categoryTitle,
                               audience: info.flatMap { Audience(rawValue: $0.audience) } ?? v.audience,
                               shapes: shapes, section: info?.section ?? v.section, listed: info?.listed ?? true)
        }
    }
}
