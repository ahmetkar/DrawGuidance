import Foundation

/// 3B düzenleyicinin araçları (Android `Tool3`).
enum Tool3: CaseIterable { case VIEW, MOVE, ROTATE, SCALE, POINT, EDGE, FACE, PAINT, DRAW, ERASE }

/// v26 düzenleyici modları: Seç · Düzenle · Çiz · Boya · Ekle.
enum Mode3: CaseIterable {
    case SELECT, EDIT, DRAW, PAINT, ADD

    var tools: [Tool3] {
        switch self {
        case .SELECT: return [.MOVE, .ROTATE, .SCALE]
        case .EDIT: return [.POINT, .EDGE, .FACE]
        case .DRAW: return [.DRAW, .ERASE]
        case .PAINT: return [.PAINT]
        case .ADD: return [.MOVE]
        }
    }

    static func of(_ t: Tool3) -> Mode3 {
        switch t {
        case .VIEW, .MOVE, .ROTATE, .SCALE: return .SELECT
        case .POINT, .EDGE, .FACE: return .EDIT
        case .DRAW, .ERASE: return .DRAW
        case .PAINT: return .PAINT
        }
    }
}

/// Açık yan panel.
enum Panel3: String, CaseIterable { case ADD, PARTS, MATERIAL, SHAPE, SETTINGS, BRUSH, OPS }

/// v25 Yüz aracında seçim: tek yüz ya da dokunulan yüzün bütün yüzeyi.
enum FaceMode { case SINGLE, SURFACE }

/// v27 Boya kapsamı.
enum PaintScope { case FACE, SURFACE, PART, ALL }

/// v25 parçalama düzlemi.
enum SliceAxis { case X, Y, Z, VIEW }

/// v25 3B fırça ayarları.
struct Brush3: Equatable {
    var kind: Brush3Kind = .LINE
    var height: Float = 0.5
    var bulge: Float = 0.8
    var taper: Float = 0.35
    var smoothing: Float = 0.4
    var pressure = true
    var material: MaterialKind = .PLASTIC
    var opacity: Float = 1
}

/// v25 resim olarak dışa aktarma ayarları.
struct ImageExport: Equatable {
    var size = 1080
    var aspect: Float = 1
    /// ARGB (0: saydam).
    var background: Int32 = 0
    var ground = false
    var shadow = true
    var edges = true
    var fit = true
    var jpeg = false
    var smoothness = 2
}

/// Sıra korunan küme (Kotlin `LinkedHashSet` gibi): seçim sırası birleştirme ve kesmede önemlidir.
struct OrderedSet<T: Hashable>: Equatable, Sequence {
    private(set) var items: [T] = []
    private var members: Set<T> = []

    init() {}
    init<S: Sequence>(_ s: S) where S.Element == T { for v in s where members.insert(v).inserted { items.append(v) } }

    var count: Int { items.count }
    var isEmpty: Bool { items.isEmpty }
    var first: T? { items.first }
    var asSet: Set<T> { members }
    func contains(_ v: T) -> Bool { members.contains(v) }
    func makeIterator() -> IndexingIterator<[T]> { items.makeIterator() }

    func adding<S: Sequence>(_ s: S) -> OrderedSet<T> where S.Element == T { var c = self; for v in s where c.members.insert(v).inserted { c.items.append(v) }; return c }
    func adding(_ v: T) -> OrderedSet<T> { adding([v]) }
    func removing<S: Sequence>(_ s: S) -> OrderedSet<T> where S.Element == T {
        let r = Set(s)
        return OrderedSet(items.filter { !r.contains($0) })
    }
    func removing(_ v: T) -> OrderedSet<T> { removing([v]) }
    func toggling(_ v: T) -> OrderedSet<T> { contains(v) ? removing(v) : adding(v) }
    func filter(_ f: (T) -> Bool) -> OrderedSet<T> { OrderedSet(items.filter(f)) }

    static func == (a: OrderedSet<T>, b: OrderedSet<T>) -> Bool { a.members == b.members }
}

/// Seçim: parçalar (ilk seçilen asıl parça), asıl parçanın yüzleri, köşeleri ve kenarları.
struct Selection3: Equatable {
    var parts: [String] = []
    var faces = OrderedSet<Int>()
    var vertices = OrderedSet<Int>()
    var edges = OrderedSet<EdgeRef>()

    init(_ parts: [String] = [], faces: OrderedSet<Int> = OrderedSet(), vertices: OrderedSet<Int> = OrderedSet(), edges: OrderedSet<EdgeRef> = OrderedSet()) {
        self.parts = parts; self.faces = faces; self.vertices = vertices; self.edges = edges
    }

    var primary: String? { parts.first }
}

/// v24 kaydırıcıyla yapılan düzenleme.
enum OpKind: CaseIterable {
    case BEND, STRETCH, PUSH, ROUND, EXTRUDE, INSET, FACE_SCALE, HOLE

    var min: Float { switch self { case .BEND: return -1; case .STRETCH: return 0.3; case .PUSH: return -0.5; case .ROUND: return 0
        case .EXTRUDE: return -0.6; case .INSET: return 0; case .FACE_SCALE: return 0.2; case .HOLE: return 0.05 } }
    var max: Float { switch self { case .BEND: return 1; case .STRETCH: return 3; case .PUSH: return 0.5; case .ROUND: return 1
        case .EXTRUDE: return 1.2; case .INSET: return 0.45; case .FACE_SCALE: return 2.5; case .HOLE: return 0.95 } }
    var start: Float { switch self { case .STRETCH, .FACE_SCALE: return 1; case .HOLE: return 0.4; default: return 0 } }
    var face: Bool { switch self { case .EXTRUDE, .INSET, .FACE_SCALE, .HOLE: return true; default: return false } }
}

/// Etkin kaydırıcı işlemi; [failed]: bu kenarda yapılamıyor.
struct SliderOp: Equatable { var kind: OpKind; var amount: Float; var failed = false }

/// Kaydetme ve çıkış olayları.
enum Event3: Equatable {
    case saved(name: String, toCanvas: Bool)
    case exit
    case message(String)
}

// Geri al/yinele ve "değişti mi" denetimleri için içerik eşitliği (Kotlin veri sınıfları gibi).
extension Part: Equatable {
    static func == (a: Part, b: Part) -> Bool {
        a.id == b.id && a.name == b.name && a.mesh == b.mesh && a.primitive == b.primitive && a.position == b.position
            && a.rotation == b.rotation && a.scale == b.scale && a.material == b.material && a.faceMaterials == b.faceMaterials
            && a.smooth == b.smooth && a.uv == b.uv && a.visible == b.visible && a.group == b.group && a.brush === b.brush
    }
}

extension Model3D: Equatable {
    static func == (a: Model3D, b: Model3D) -> Bool {
        a.id == b.id && a.name == b.name && a.scene == b.scene && a.camera == b.camera && a.groups == b.groups
            && a.strokes == b.strokes && a.parts == b.parts
    }

    func with(camera c: Camera3) -> Model3D { var m = self; m.camera = c; return m }
}
