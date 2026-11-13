import Foundation

/// v46 hazır tuval şablonları (Android `CanvasTemplates`): çizgi roman ve not alma için uygulamanın kendi elemanlarıyla
/// hazırlanmış belgeler. Yeni tuval oluşturulurken kapak resmi ve adıyla listelenir; seçilen şablon yeni tuvale kopyalanır.
/// Dosyalar Android ile ortak (`templates/`): `catalog.json`, her şablonda `meta.json`, `content.bin`, `cover.webp`.
struct TemplateEntry: Codable, Equatable, Identifiable {
    let id: String
    let kind: String
    let names: [String: String]
    var hints: [String: String] = [:]
    var pages = 1
    var width = 0
    var height = 0

    var canvasKind: CanvasKind? { CanvasKind(rawValue: kind) }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id); kind = try c.decode(String.self, forKey: .kind)
        names = try c.decode([String: String].self, forKey: .names)
        hints = try c.decodeIfPresent([String: String].self, forKey: .hints) ?? [:]
        pages = try c.decodeIfPresent(Int.self, forKey: .pages) ?? 1
        width = try c.decodeIfPresent(Int.self, forKey: .width) ?? 0
        height = try c.decodeIfPresent(Int.self, forKey: .height) ?? 0
    }
}

struct TemplateAsset: Codable, Equatable {
    let id: String
    let file: String
    var mime = "image/webp"
    var w = 0
    var h = 0
}

struct CanvasTemplateMeta: Codable, Equatable {
    let width: Int
    let height: Int
    var backgroundColorHex = "#FFFFFF"
    var background = ""
    var guide = ""
    var currentPage = 0
    var titleId: String?
    var assets: [TemplateAsset] = []

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        width = try c.decode(Int.self, forKey: .width); height = try c.decode(Int.self, forKey: .height)
        backgroundColorHex = try c.decodeIfPresent(String.self, forKey: .backgroundColorHex) ?? "#FFFFFF"
        background = try c.decodeIfPresent(String.self, forKey: .background) ?? ""
        guide = try c.decodeIfPresent(String.self, forKey: .guide) ?? ""
        currentPage = try c.decodeIfPresent(Int.self, forKey: .currentPage) ?? 0
        titleId = try c.decodeIfPresent(String.self, forKey: .titleId)
        assets = try c.decodeIfPresent([TemplateAsset].self, forKey: .assets) ?? []
    }
}

/// Kurulmaya hazır şablon: yeni kimlikli resimler ve onlara göre güncellenmiş belge.
struct PreparedTemplate {
    let meta: CanvasTemplateMeta
    let pages: [[Stroke]]
    let layers: [Layer]
    let background: BackgroundSpec
    let guide: GuideSpec
    /// Yeni kimlik → (baytlar, şablondaki resim kaydı).
    let assets: [(String, Data, TemplateAsset)]
}

enum CanvasTemplates {
    static let DIR = "templates"
    static let CATALOG = "\(DIR)/catalog.json"
    /// Şablonu olan tuval türleri (v47 basit not almada da).
    static let KINDS: Set<CanvasKind> = [.COMIC, .NOTES, .SIMPLE_NOTES]

    private final class Marker {}

    /// Dosya açıcı (uygulama paketindeki `templates/` klasörü; testlerde değiştirilebilir).
    nonisolated(unsafe) static var opener: (String) -> Data? = { path in
        for b in [Bundle.main, Bundle(for: Marker.self)] {
            if let url = b.resourceURL?.appendingPathComponent(path), let d = try? Data(contentsOf: url) { return d }
        }
        return nil
    }

    nonisolated(unsafe) private static var loaded: [TemplateEntry]?

    static var catalog: [TemplateEntry] {
        if let loaded { return loaded }
        struct Catalog: Decodable { var templates: [TemplateEntry] = [] }
        let list = opener(CATALOG).flatMap { try? JSONDecoder().decode(Catalog.self, from: $0) }?.templates ?? []
        loaded = list
        return list
    }

    static func reset() { loaded = nil }

    static func forKind(_ kind: CanvasKind) -> [TemplateEntry] { catalog.filter { $0.kind == kind.rawValue } }
    static func find(_ id: String) -> TemplateEntry? { catalog.first { $0.id == id } }

    static func name(_ e: TemplateEntry, _ lang: String = I18n.language) -> String { e.names[lang] ?? e.names["en"] ?? e.names["tr"] ?? e.id }
    static func hint(_ e: TemplateEntry, _ lang: String = I18n.language) -> String { e.hints[lang] ?? e.hints["en"] ?? e.hints["tr"] ?? "" }

    static func coverBytes(_ id: String) -> Data? { opener("\(DIR)/\(id)/cover.webp") }

    /// Şablonu yeni tuvale hazırlar: resimlere yeni kimlik verir, belgedeki kimlikleri değiştirir ve çizgi roman
    /// kapağının başlığını [canvasName] yapar. Okunamazsa nil.
    static func prepare(_ id: String, canvasName: String, newId: () -> String = { UUID().uuidString.lowercased() }) -> PreparedTemplate? {
        guard let metaData = opener("\(DIR)/\(id)/meta.json"), let meta = try? JSONDecoder().decode(CanvasTemplateMeta.self, from: metaData),
              let content = opener("\(DIR)/\(id)/content.bin"), let doc = try? ContentCodec.decodeDocument(content) else { return nil }
        var ids: [String: String] = [:]
        for a in meta.assets { ids[a.id] = newId() }
        let pages = doc.pages.enumerated().map { p, page in
            page.map { s -> Stroke in
                var n = s
                if let a = s.assetId, let new = ids[a] { n.assetId = new }
                if p == 0, let t = meta.titleId, n.id == t { n = ComicCover.retitle(n, meta.width, canvasName) }
                return n
            }
        }
        var bg = meta.background, guide = meta.guide
        for (old, new) in ids { bg = bg.replacingOccurrences(of: old, with: new); guide = guide.replacingOccurrences(of: old, with: new) }
        let background = bg.isEmpty ? BackgroundSpec() : (try? JSONDecoder().decode(BackgroundSpec.self, from: Data(bg.utf8))) ?? BackgroundSpec()
        let guideSpec = guide.isEmpty ? GuideSpec() : (try? JSONDecoder().decode(GuideSpec.self, from: Data(guide.utf8))) ?? GuideSpec()
        let assets = meta.assets.map { a in (ids[a.id]!, opener("\(DIR)/\(id)/\(a.file)") ?? Data(), a) }
        return PreparedTemplate(meta: meta, pages: pages, layers: doc.layers, background: background, guide: guideSpec, assets: assets)
    }
}
