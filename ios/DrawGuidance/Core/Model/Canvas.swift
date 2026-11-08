import Foundation

/// Hazır tuval boyut şablonları.
enum CanvasSizeTemplate: String, CaseIterable {
    case SQUARE, PORTRAIT, LANDSCAPE, NOTE_A4, COMIC_PAGE, CUSTOM

    var label: String {
        switch self {
        case .SQUARE: return "Kare"
        case .PORTRAIT: return "Portre"
        case .LANDSCAPE: return "Manzara"
        case .NOTE_A4: return "Çalışma/Not (A4)"
        case .COMIC_PAGE: return "Çizgi roman sayfası"
        case .CUSTOM: return "Özel"
        }
    }

    var width: Int {
        switch self {
        case .SQUARE, .PORTRAIT, .CUSTOM: return 1080
        case .LANDSCAPE: return 1920
        case .NOTE_A4: return 1240
        case .COMIC_PAGE: return 1200
        }
    }

    var height: Int {
        switch self {
        case .SQUARE, .CUSTOM: return 1080
        case .PORTRAIT: return 1920
        case .LANDSCAPE: return 1080
        case .NOTE_A4: return 1754
        case .COMIC_PAGE: return 1800
        }
    }
}

/// Tuval türü: fırça ve şekil kataloğunu belirler.
enum CanvasKind: String, CaseIterable, Codable, Identifiable {
    case ART, NOTES, COMIC, SIMPLE_ART, SIMPLE_NOTES, KIDS

    var id: String { rawValue }

    static func parse(_ value: String?) -> CanvasKind { value.flatMap(CanvasKind.init(rawValue:)) ?? .ART }

    static let Simple: [CanvasKind] = [.SIMPLE_ART, .SIMPLE_NOTES, .KIDS]
    static let Advanced: [CanvasKind] = [.ART, .NOTES, .COMIC]

    /// Fırça, şekil kataloğu ve kağıt seçeneklerinde kullanılan temel tür.
    var base: CanvasKind {
        switch self {
        case .COMIC, .SIMPLE_ART, .KIDS: return .ART
        case .SIMPLE_NOTES: return .NOTES
        default: return self
        }
    }

    var paged: Bool { self == .NOTES || self == .COMIC || self == .SIMPLE_NOTES }
    var simple: Bool { self == .SIMPLE_ART || self == .SIMPLE_NOTES || self == .KIDS }
    var isNotes: Bool { base == .NOTES }

    func packMode() -> String {
        if self == .COMIC { return "COMIC" }
        if isNotes { return "NOTES" }
        return "ART"
    }

    var isPlainArt: Bool { base == .ART && self != .COMIC }

    var defaultSize: CanvasSizeTemplate {
        if isNotes { return .NOTE_A4 }
        if self == .COMIC { return .COMIC_PAGE }
        return .SQUARE
    }

    var maxPages: Int {
        if self == .COMIC { return 1000 }
        if paged { return 200 }
        return 1
    }
}

/// Tuval domain modeli (Android `CanvasUi`).
struct CanvasUi: Equatable, Identifiable {
    var localId: String
    var name: String
    var width: Int
    var height: Int
    var backgroundColorHex: String
    var aiEnabled: Bool
    var pages: [[Stroke]]
    var createdAt: Int64
    var updatedAt: Int64
    var kind: CanvasKind = .ART
    var currentPage = 0
    var guide = GuideSpec()
    var layers: [Layer] = [Layer.Default]
    var folderId: String? = nil
    var sizeBytes: Int64 = 0
    var background = BackgroundSpec()
    var pageBackgrounds: [PageBackground?] = []
    var pageSettings = PageSettings()

    var id: String { localId }

    var strokes: [Stroke] {
        if pages.indices.contains(currentPage) { return pages[currentPage] }
        return pages.first ?? []
    }

    var pageCount: Int { pages.count }

    func paperOf(_ page: Int) -> String {
        (pageBackgrounds.indices.contains(page) ? pageBackgrounds[page]?.paperHex : nil) ?? backgroundColorHex
    }

    func backgroundOf(_ page: Int) -> BackgroundSpec {
        (pageBackgrounds.indices.contains(page) ? pageBackgrounds[page]?.spec : nil) ?? background
    }

    var pagePaperHex: String { paperOf(currentPage) }
    var pageBackground: BackgroundSpec { backgroundOf(currentPage) }
    var hasPageBackgrounds: Bool { pageBackgrounds.contains { $0?.hasLook == true } }
    var hasCover: Bool { pageBackgrounds.first??.cover == true }
    func isCover(_ page: Int) -> Bool { page == 0 && hasCover }
}
