import Foundation

/// Android `CanvasKind` karşılığı. Gelişmiş ve basit tuval türleri.
enum CanvasKind: String, CaseIterable, Codable, Identifiable {
    case art = "ART"
    case notes = "NOTES"
    case comic = "COMIC"
    case simpleArt = "SIMPLE_ART"
    case simpleNotes = "SIMPLE_NOTES"
    case kids = "KIDS"

    var id: String { rawValue }

    var isSimple: Bool { self == .simpleArt || self == .simpleNotes || self == .kids }

    var titleKey: String {
        switch self {
        case .art: return "kind.art"
        case .notes: return "kind.notes"
        case .comic: return "kind.comic"
        case .simpleArt: return "kind.simple_art"
        case .simpleNotes: return "kind.simple_notes"
        case .kids: return "kind.kids"
        }
    }

    var systemImage: String {
        switch self {
        case .art: return "paintpalette"
        case .notes: return "note.text"
        case .comic: return "rectangle.split.3x1"
        case .simpleArt: return "paintbrush"
        case .simpleNotes: return "pencil.and.scribble"
        case .kids: return "face.smiling"
        }
    }
}
