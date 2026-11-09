import Foundation

/// v40 çizgi roman modunun hazır karakterleri, mekânları, efektleri ve nesneleri (Android `ComicAssets`).
/// Resimler uygulamayla gelir (`comic/` klasörü, WebP); katalog `comic/catalog.json`.
struct ComicCatalog: Codable, Equatable {
    var version = 1
    var themes: [ComicTheme] = []
    var characters: [ComicCharacter] = []
    var bgCategories: [ComicBgCategory] = []
    var backgrounds: [ComicBackground] = []
    var effectCategories: [ComicBgCategory] = []
    var effects: [ComicProp] = []
    var objectCategories: [ComicBgCategory] = []
    var objects: [ComicProp] = []

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = (try? c.decode(Int.self, forKey: .version)) ?? 1
        themes = (try? c.decode([ComicTheme].self, forKey: .themes)) ?? []
        characters = (try? c.decode([ComicCharacter].self, forKey: .characters)) ?? []
        bgCategories = (try? c.decode([ComicBgCategory].self, forKey: .bgCategories)) ?? []
        backgrounds = (try? c.decode([ComicBackground].self, forKey: .backgrounds)) ?? []
        effectCategories = (try? c.decode([ComicBgCategory].self, forKey: .effectCategories)) ?? []
        effects = (try? c.decode([ComicProp].self, forKey: .effects)) ?? []
        objectCategories = (try? c.decode([ComicBgCategory].self, forKey: .objectCategories)) ?? []
        objects = (try? c.decode([ComicProp].self, forKey: .objects)) ?? []
    }
}

struct ComicTheme: Codable, Equatable, Identifiable { let id: String; let names: [String: String] }
struct ComicBgCategory: Codable, Equatable, Identifiable { let id: String; let names: [String: String] }

struct ComicVariant: Codable, Equatable, Identifiable {
    let id: String
    let group: String
    let file: String
    let thumb: String
    let w: Int
    let h: Int
}

struct ComicCharacter: Codable, Equatable, Identifiable {
    let id: String
    let style: String
    let theme: String
    let names: [String: String]
    let variants: [ComicVariant]
    var themes: [String] = []

    init(id: String, style: String, theme: String, names: [String: String], variants: [ComicVariant], themes: [String] = []) {
        self.id = id; self.style = style; self.theme = theme; self.names = names; self.variants = variants; self.themes = themes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id); style = try c.decode(String.self, forKey: .style)
        theme = try c.decode(String.self, forKey: .theme); names = try c.decode([String: String].self, forKey: .names)
        variants = try c.decode([ComicVariant].self, forKey: .variants)
        themes = (try? c.decode([String].self, forKey: .themes)) ?? []
    }

    var packs: [String] { themes.isEmpty ? [theme] : themes }
    func variants(_ group: VariantGroup) -> [ComicVariant] { variants.filter { $0.group == group.id } }
    var cover: ComicVariant { variants.first { $0.group == VariantGroup.POSE.id } ?? variants[0] }
}

struct ComicBackground: Codable, Equatable, Identifiable {
    let id: String
    let style: String
    let category: String
    let names: [String: String]
    let file: String
    let thumb: String
    let w: Int
    let h: Int
    var themes: [String] = []

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id); style = try c.decode(String.self, forKey: .style)
        category = try c.decode(String.self, forKey: .category); names = try c.decode([String: String].self, forKey: .names)
        file = try c.decode(String.self, forKey: .file); thumb = try c.decode(String.self, forKey: .thumb)
        w = try c.decode(Int.self, forKey: .w); h = try c.decode(Int.self, forKey: .h)
        themes = (try? c.decode([String].self, forKey: .themes)) ?? []
    }

    var paperId: String { ComicAssets.BG_PREFIX + id }
}

struct ComicProp: Codable, Equatable, Identifiable {
    let id: String
    let styles: [String]
    let category: String
    var themes: [String] = []
    let file: String
    let thumb: String
    let w: Int
    let h: Int

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id); styles = try c.decode([String].self, forKey: .styles)
        category = try c.decode(String.self, forKey: .category); themes = (try? c.decode([String].self, forKey: .themes)) ?? []
        file = try c.decode(String.self, forKey: .file); thumb = try c.decode(String.self, forKey: .thumb)
        w = try c.decode(Int.self, forKey: .w); h = try c.decode(Int.self, forKey: .h)
    }
}

enum ComicAssetKind: String { case CHARACTER = "character", BACKGROUND = "background", EFFECT = "effect", OBJECT = "object" }

enum ComicStyle: String, CaseIterable {
    case MANGA = "manga", COMIC = "cizgi_roman"
    var id: String { rawValue }
}

enum VariantGroup: String, CaseIterable {
    case EXPRESSION = "ifade", POSE = "poz", ACTION = "aksiyon", DAILY = "gunluk"
    var id: String { rawValue }
}

/// v42 tema paketleri dizini: hangi varlık hangi pakette (iOS'ta katalogdaki "themes" alanlarından türetilir).
struct ComicThemeIndex {
    let packs: [ComicTheme]
    private let links: [String: Set<String>]

    static func key(_ kind: ComicAssetKind, _ id: String) -> String { "\(kind.rawValue):\(id)" }

    init(_ catalog: ComicCatalog) {
        packs = catalog.themes
        var l: [String: Set<String>] = [:]
        for c in catalog.characters { l[Self.key(.CHARACTER, c.id)] = Set(c.packs) }
        for b in catalog.backgrounds where !b.themes.isEmpty { l[Self.key(.BACKGROUND, b.id)] = Set(b.themes) }
        for e in catalog.effects where !e.themes.isEmpty { l[Self.key(.EFFECT, e.id)] = Set(e.themes) }
        for o in catalog.objects where !o.themes.isEmpty { l[Self.key(.OBJECT, o.id)] = Set(o.themes) }
        links = l
    }

    func packsOf(_ kind: ComicAssetKind, _ id: String) -> Set<String> { links[Self.key(kind, id)] ?? [] }
    func has(_ kind: ComicAssetKind, _ id: String, _ theme: String) -> Bool { packsOf(kind, id).contains(theme) }
}

enum ComicAssets {
    static let CATALOG = "comic/catalog.json"
    static let BG_PREFIX = "cbg."
    static let REMOVED_CHARACTERS: Set<String> = ["elf_lunara"]

    /// Varlık açıcı: göreli yol uygulama paketinden, "/" ile başlayan yol (inmiş paket) dosyadan.
    nonisolated(unsafe) static var opener: (String) -> Data? = { path in
        if path.hasPrefix("/") { return try? Data(contentsOf: URL(fileURLWithPath: path)) }
        final class Marker {}
        for b in [Bundle.main, Bundle(for: Marker.self)] {
            if let root = b.resourceURL, let d = try? Data(contentsOf: root.appendingPathComponent(path)) { return d }
        }
        return nil
    }

    private static let lock = NSLock()
    nonisolated(unsafe) private static var loaded: ComicCatalog?
    nonisolated(unsafe) private static var merged: ComicCatalog?
    nonisolated(unsafe) private static var index: ComicThemeIndex?
    /// v43 sunucudan inmiş paketlerin içerikleri.
    nonisolated(unsafe) static var extras = ComicCatalog() { didSet { lock.lock(); merged = nil; index = nil; lock.unlock() } }

    static var bundled: ComicCatalog {
        lock.lock(); defer { lock.unlock() }
        if let l = loaded { return l }
        let c = opener(CATALOG).flatMap { try? parse($0) } ?? ComicCatalog()
        loaded = c
        return c
    }

    static var catalog: ComicCatalog {
        let base = bundled
        lock.lock(); defer { lock.unlock() }
        if let m = merged { return m }
        let m = merge(base, extras)
        merged = m
        return m
    }

    static var themeIndex: ComicThemeIndex {
        let c = catalog
        lock.lock(); defer { lock.unlock() }
        if let i = index { return i }
        let i = ComicThemeIndex(c)
        index = i
        return i
    }

    static func parse(_ data: Data) throws -> ComicCatalog { try JSONDecoder().decode(ComicCatalog.self, from: data) }

    static func reset() { lock.lock(); loaded = nil; merged = nil; index = nil; lock.unlock() }

    static func merge(_ base: ComicCatalog, _ extra: ComicCatalog) -> ComicCatalog {
        if extra.effects.isEmpty && extra.objects.isEmpty && extra.backgrounds.isEmpty && extra.characters.isEmpty { return base }
        var c = base
        c.characters += extra.characters.filter { !REMOVED_CHARACTERS.contains($0.id) }
        c.bgCategories += extra.bgCategories.filter { x in !base.bgCategories.contains { $0.id == x.id } }
        c.backgrounds += extra.backgrounds
        c.effectCategories += extra.effectCategories.filter { x in !base.effectCategories.contains { $0.id == x.id } }
        c.effects += extra.effects
        c.objectCategories += extra.objectCategories.filter { x in !base.objectCategories.contains { $0.id == x.id } }
        c.objects += extra.objects
        return c
    }

    static func name(_ names: [String: String], _ lang: String = I18n.language) -> String {
        names[lang] ?? names["tr"] ?? names.values.first ?? ""
    }

    static func character(_ id: String) -> ComicCharacter? { catalog.characters.first { $0.id == id } }
    static func background(_ id: String) -> ComicBackground? { catalog.backgrounds.first { $0.id == id } }

    static func backgroundByPaperId(_ paperId: String?) -> ComicBackground? {
        guard let p = paperId, p.hasPrefix(BG_PREFIX) else { return nil }
        return background(String(p.dropFirst(BG_PREFIX.count)))
    }

    static func characters(_ style: ComicStyle?, _ theme: String?) -> [ComicCharacter] {
        let idx = theme != nil ? themeIndex : nil
        return catalog.characters.filter { c in
            (style == nil || c.style == style!.id) && (idx == nil || idx!.has(.CHARACTER, c.id, theme!))
        }
    }

    static func themes(_ style: ComicStyle?) -> [ComicTheme] { themesFor(.CHARACTER, style) }

    static func themesFor(_ kind: ComicAssetKind, _ style: ComicStyle?) -> [ComicTheme] {
        let idx = themeIndex
        let c = catalog
        let ids: [String]
        switch kind {
        case .CHARACTER: ids = c.characters.filter { style == nil || $0.style == style!.id }.map(\.id)
        case .BACKGROUND: ids = c.backgrounds.filter { style == nil || $0.style == style!.id }.map(\.id)
        case .EFFECT: ids = c.effects.filter { style == nil || $0.styles.contains(style!.id) }.map(\.id)
        case .OBJECT: ids = c.objects.filter { style == nil || $0.styles.contains(style!.id) }.map(\.id)
        }
        var used = Set<String>()
        for id in ids { used.formUnion(idx.packsOf(kind, id)) }
        return idx.packs.filter { used.contains($0.id) }
    }

    static func backgrounds(_ style: ComicStyle, _ category: String?, _ theme: String? = nil) -> [ComicBackground] {
        let idx = theme != nil ? themeIndex : nil
        return catalog.backgrounds.filter { b in
            b.style == style.id && (category == nil || b.category == category) && (idx == nil || idx!.has(.BACKGROUND, b.id, theme!))
        }
    }

    static func bgCategories(_ style: ComicStyle, _ theme: String? = nil) -> [ComicBgCategory] {
        let used = Set(backgrounds(style, nil, theme).map(\.category))
        return catalog.bgCategories.filter { used.contains($0.id) }
    }

    static func effect(_ id: String) -> ComicProp? { catalog.effects.first { $0.id == id } }
    static func obj(_ id: String) -> ComicProp? { catalog.objects.first { $0.id == id } }

    static func props(_ kind: ComicAssetKind, _ style: ComicStyle, _ category: String?, _ theme: String? = nil) -> [ComicProp] {
        let idx = theme != nil ? themeIndex : nil
        return (kind == .EFFECT ? catalog.effects : catalog.objects).filter { p in
            p.styles.contains(style.id) && (category == nil || p.category == category) && (idx == nil || idx!.has(kind, p.id, theme!))
        }
    }

    static func propCategories(_ kind: ComicAssetKind, _ style: ComicStyle, _ theme: String? = nil) -> [ComicBgCategory] {
        let used = Set(props(kind, style, nil, theme).map(\.category))
        return (kind == .EFFECT ? catalog.effectCategories : catalog.objectCategories).filter { used.contains($0.id) }
    }

    static func theme(_ id: String) -> ComicTheme? { catalog.themes.first { $0.id == id } }

    /// Varlığın baytları (ana iş parçacığında çağrılmamalı).
    static func bytes(_ path: String) throws -> Data {
        guard let d = opener(path) else { throw CocoaError(.fileNoSuchFile) }
        return d
    }
}
