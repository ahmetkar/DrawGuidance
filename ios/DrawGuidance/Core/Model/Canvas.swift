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
/// v51 gelişmiş not alma ve basit not alma tek not alma modunda ([NOTES]) birleşti: basit çizim grubunda, tek menülü;
/// gelişmiş not almanın bütün katalogları var, damlalık, kılavuz çizgileri, vektör (nokta/çizgi) düzenleme ve resim
/// vektörleştirme yok. Resim geçici bir basit resim tuvalinde düzenlenir (`TempImageEdit`). [SIMPLE_NOTES] yalnız eski
/// kayıtların adıdır: okunurken [NOTES]'a çevrilir.
enum CanvasKind: String, CaseIterable, Codable, Identifiable {
    case ART, NOTES, COMIC, SIMPLE_ART, SIMPLE_NOTES, KIDS

    var id: String { rawValue }

    /// v51 eski basit not alma kayıtlarının adı.
    static let LEGACY_SIMPLE_NOTES = "SIMPLE_NOTES"

    static func parse(_ value: String?) -> CanvasKind {
        if value == LEGACY_SIMPLE_NOTES { return .NOTES }
        return value.flatMap(CanvasKind.init(rawValue:)) ?? .ART
    }

    /// v51 kayıtlardan okunurken eski basit not alma tek not alma moduna taşınır.
    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = CanvasKind.parse(raw)
    }

    static let Simple: [CanvasKind] = [.SIMPLE_ART, .NOTES, .KIDS]
    static let Advanced: [CanvasKind] = [.ART, .COMIC]
    /// v51 kullanıcıya sunulan türler (eski basit not alma yok).
    static let Selectable: [CanvasKind] = Advanced + Simple

    /// Fırça, şekil kataloğu ve kağıt seçeneklerinde kullanılan temel tür.
    var base: CanvasKind {
        switch self {
        case .COMIC, .SIMPLE_ART, .KIDS: return .ART
        case .SIMPLE_NOTES: return .NOTES
        default: return self
        }
    }

    var paged: Bool { self == .NOTES || self == .COMIC || self == .SIMPLE_NOTES }
    var simple: Bool { self == .SIMPLE_ART || self == .SIMPLE_NOTES || self == .NOTES || self == .KIDS }
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
