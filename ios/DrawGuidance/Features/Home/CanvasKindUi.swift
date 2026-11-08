import SwiftUI

extension CanvasKind {
    var titleKey: String {
        switch self {
        case .ART: return "kind.art"
        case .NOTES: return "kind.notes"
        case .COMIC: return "kind.comic"
        case .SIMPLE_ART: return "kind.simple_art"
        case .SIMPLE_NOTES: return "kind.simple_notes"
        case .KIDS: return "kind.kids"
        }
    }

    var systemImage: String {
        switch self {
        case .ART: return "paintpalette"
        case .NOTES: return "note.text"
        case .COMIC: return "rectangle.split.3x1"
        case .SIMPLE_ART: return "paintbrush"
        case .SIMPLE_NOTES: return "pencil.and.scribble"
        case .KIDS: return "face.smiling"
        }
    }
}
