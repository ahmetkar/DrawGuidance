import Foundation

/// v20 fırça başına ayarlar (Android `BrushTuning`).
struct BrushTuning: Codable, Equatable {
    var spacing: Float? = nil
    var speed: Float = 1
    var stabilizer = false
    var stabilizerStrength: Float = 0.5
    var tiltWidth: Float? = nil
    var tiltLight: Float? = nil
    var tiltThreshold: Float = 0
    var nibFollow = false
    var tube = TubeParams()

    static let MIN_SIZE: Float = 1
    static let MAX_SIZE: Float = 380

    var stabilizeAmount: Float { stabilizer ? stabilizerStrength.coerceIn(0.05, 1) : 0 }

    func params(_ type: BrushType) -> BrushParams? {
        let sp = spacing.flatMap { d in (type.defaultSpacing.map { abs($0 - d) > 1e-3 } ?? false) ? d : nil }
        let tw = tiltWidth.flatMap { abs($0 - type.defaultTiltWidth) > 1e-3 ? $0 : nil }
        let tl = tiltLight.flatMap { abs($0 - type.defaultTiltLight) > 1e-3 ? $0 : nil }
        let th = tiltThreshold.coerceIn(0, 80)
        let nib = nibFollow && type.hasFlatNib
        let t: TubeParams? = (type == .BRUSH_3D && tube != TubeParams()) ? tube : nil
        if sp == nil && tw == nil && tl == nil && th <= 0 && !nib && t == nil { return nil }
        return BrushParams(spacing: sp, tiltWidth: tw, tiltLight: tl, tiltThreshold: th, nibFollow: nib, tube: t)
    }
}

/// Çizim alanının yan panelinde ayarlanan fırça durumu (Android `BrushSettings`).
struct BrushSettings: Equatable {
    var brushType: BrushType = .PENCIL
    var sizePx: Float = BrushType.PENCIL.defaultSize
    var colorHex = "#1E1E1E"
    var opacity: Float = 1
    var flow: Float = 1
    var strength: Float = 1
    var tuning = BrushTuning()

    var params: BrushParams? { tuning.params(brushType) }
}

enum PanelTab { case BRUSH, ELEMENTS, LAYERS }

/// Mod oturumu politikası (Android `ModeKind`).
enum ModeKind { case BASE, INSERT, EDIT, TOOL }

/// Üst araç menüsünün türü (v18).
enum ToolRowKind { case DRAW, SELECT, MOVE, ADD, EDIT, FRAME }

/// Çizim alanında dokunuşların ne yapacağı (Android `EditMode`).
enum EditMode: String, CaseIterable {
    case DRAW, SELECT, MOVE, ROTATE, SCALE, EXPAND, CROP, SHAPE, TEXT, DIAGRAM, SHAPE_EDIT, GUIDE, PERSPECTIVE, DIAGRAM_EDIT,
         FORMULA, IMAGE_EDIT, PEN, ADD, EDIT_PICK, PAINT

    var transformsSelection: Bool { self == .MOVE || self == .ROTATE || self == .SCALE }

    var kind: ModeKind {
        switch self {
        case .DRAW, .SELECT, .ADD, .EDIT_PICK, .PAINT: return .BASE
        case .SHAPE, .DIAGRAM, .FORMULA: return .INSERT
        case .SHAPE_EDIT, .DIAGRAM_EDIT, .IMAGE_EDIT: return .EDIT
        default: return .TOOL
        }
    }

    var allowsDrawTools: Bool { kind == .BASE && self != .PAINT }

    var family: EditMode {
        switch self {
        case .DRAW, .PEN, .PAINT: return .DRAW
        case .SELECT: return .SELECT
        case .MOVE: return .MOVE
        case .ADD, .SHAPE, .TEXT, .DIAGRAM, .FORMULA, .GUIDE, .PERSPECTIVE: return .ADD
        case .EDIT_PICK, .SHAPE_EDIT, .DIAGRAM_EDIT, .IMAGE_EDIT, .ROTATE, .SCALE, .CROP: return .EDIT_PICK
        case .EXPAND: return .EXPAND
        }
    }

    var rowKind: ToolRowKind {
        switch family {
        case .SELECT: return .SELECT
        case .MOVE: return .MOVE
        case .ADD: return .ADD
        case .EDIT_PICK: return .EDIT
        case .EXPAND: return .FRAME
        default: return .DRAW
        }
    }

    var menuFamily: EditMode {
        switch self {
        case .MOVE, .EXPAND: return .EDIT_PICK
        default: return family
        }
    }

    static let toolbar: [EditMode] = [.SELECT, .DRAW, .ADD, .EDIT_PICK]
}

/// v17 seçimin türü.
enum SelectionKind { case NONE, TEXT, SHAPE, INK, FORMULA, IMAGE, DIAGRAM, MIXED }

/// v22 3B sahnenin hazır açıları.
enum CameraPreset { case ISO, FRONT, SIDE, TOP }

/// Tuval üstü diyagram düzenlemede odaktaki öğe.
enum DiagramFocus: Equatable {
    case node(String)
    case edge(Int)
}

/// Şekil düzenleme araçları (v16 vektör düzenleme).
enum ShapeTool {
    case NODES, ADD_NODE, CONNECT, LINES, FILL, GRAPH, PARAMS
    var nodeFamily: Bool { self == .NODES || self == .ADD_NODE || self == .CONNECT }
}

/// Vektör düzenlemede seçim (konumlarıyla).
struct VectorSelection: Equatable {
    var nodes: [P] = []
    var edge: (P, P)? = nil
    var linkFrom: P? = nil

    static func == (a: VectorSelection, b: VectorSelection) -> Bool {
        a.nodes == b.nodes && a.linkFrom == b.linkFrom && a.edge?.0 == b.edge?.0 && a.edge?.1 == b.edge?.1
    }
}

/// Parça özellikleri penceresinde değiştirilebilen biçim (nil: değiştirme).
struct PartStyle: Equatable {
    var strokeColorHex: String? = nil
    /// Dolgu: nil değiştirme, "" dolgusuz, "#RRGGBB" renk.
    var fillColorHex: String? = nil
    var widthScale: Float? = nil
    /// "solid" / "dashed" / "dotted" / "none".
    var line: String? = nil
    var alpha: Float? = nil
}

/// Şekil düzenlemede sürüklemenin yaptığı iş.
enum ShapeDrag { case NONE, NODES, CONNECT, EDGE, BEND, TURN, HANDLE }

/// Yeni yazıların yazı tipi ayarları.
struct TextSettings: Equatable {
    var spec = TextSpec(content: "")
    var sizePx: Float = 48
}

/// Açık yazı penceresi.
struct TextDraft: Equatable {
    var anchor: P
    var editingId: String?
    var content: String
    var boxWidth: Float? = nil
}

/// Resim ekleme akışının durumu.
enum ImageImportState {
    case checking
    case rejected(errors: [ImageIssue], warnings: [ImageIssue])
    case needsConfirmation(warnings: [ImageIssue], image: PreparedImage, center: P)
}

enum ImageSaveState: Equatable {
    case idle
    case saving
    case saved([SavedImage])
    case failed
}

/// Ekranın gösterdiği bildirimler (Android `EditEvent`).
enum EditEvent: Equatable {
    case imageAdded(storedBytes: Int, originalBytes: Int)
    case erasedRemoved(Int)
    case gapsFilled(Int)
    case commandNeedsSelection
    case noGapsFound
    case layerLocked
    case saved
    case regionOpen
    case noCorrection
    case swiped(redo: Bool, done: Bool)
    case suggestionsOff
    case textSuggestionsOff
    case shapeSuggestionsOff
    case selectionSuggestionsOff
    case draw3dOn
    case draw3dOff
    case message(String)
    case transferred(name: String?, empty: Bool)
    case aiOn
    case aiOff
    case hiddenChanged(hidden: Int, shown: Int)
    case backgroundRemoved(Int)
    case tutorialFinished(String)
    case pageLimit(Int)
    case paintMiss
}
