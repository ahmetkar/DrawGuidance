import Foundation

/// v23 3B modelin veri yapıları (Android `model3d/Model3D.kt`). Dünyada Y yukarı, X sağa, Z bakana doğru; zemin y = 0.
struct V3: Hashable {
    var x: Float, y: Float, z: Float
    init(_ x: Float, _ y: Float, _ z: Float) { self.x = x; self.y = y; self.z = z }

    static func + (a: V3, o: V3) -> V3 { V3(a.x + o.x, a.y + o.y, a.z + o.z) }
    static func - (a: V3, o: V3) -> V3 { V3(a.x - o.x, a.y - o.y, a.z - o.z) }
    static func * (a: V3, s: Float) -> V3 { V3(a.x * s, a.y * s, a.z * s) }
    static prefix func - (a: V3) -> V3 { V3(-a.x, -a.y, -a.z) }
    func dot(_ o: V3) -> Float { x * o.x + y * o.y + z * o.z }
    func cross(_ o: V3) -> V3 { V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x) }
    func mul(_ o: V3) -> V3 { V3(x * o.x, y * o.y, z * o.z) }
    var length: Float { (x * x + y * y + z * z).squareRoot() }

    func normalized() -> V3 {
        let l = length
        return l < 1e-9 ? self : V3(x / l, y / l, z / l)
    }

    func get(_ axis: Int) -> Float { axis == 0 ? x : axis == 1 ? y : z }

    func with(_ axis: Int, _ v: Float) -> V3 {
        var c = self
        switch axis { case 0: c.x = v; case 1: c.y = v; default: c.z = v }
        return c
    }

    static let ZERO = V3(0, 0, 0)
    static let ONE = V3(1, 1, 1)
    static let UP = V3(0, 1, 0)
}

/// Hazır cisimler.
enum PrimitiveKind: Int, CaseIterable {
    case CUBE, SPHERE, CYLINDER, CONE, PYRAMID, PRISM3, PRISM6, TORUS, PLANE, DOME, CAPSULE, TUBE, WEDGE, STAR

    var name: String { String(describing: self) }

    var smoothByDefault: Bool {
        switch self { case .SPHERE, .CYLINDER, .CONE, .TORUS, .DOME, .CAPSULE, .TUBE: return true; default: return false }
    }

    var uvByDefault: UvMode {
        switch self {
        case .SPHERE, .DOME, .CAPSULE: return .SPHERE
        case .CYLINDER, .CONE, .TUBE, .TORUS: return .CYLINDER
        default: return .BOX
        }
    }
}

/// Cismin üretim ayarları.
struct PrimitiveSpec: Hashable {
    var kind: PrimitiveKind
    var segments: Int = 24
    var rings: Int = 12
    var top: Float = 1
    var thickness: Float = 0.3
    init(_ kind: PrimitiveKind, _ segments: Int = 24, _ rings: Int = 12, top: Float = 1, thickness: Float = 0.3) {
        self.kind = kind; self.segments = segments; self.rings = rings; self.top = top; self.thickness = thickness
    }
}

enum MaterialKind: Int, CaseIterable { case MATTE, PLASTIC, METAL, GLASS, GLOW }

/// Yüzey malzemesi.
struct Material: Hashable {
    var color: Int32 = Material.DEFAULT_COLOR
    var texture: String? = nil
    var textureScale: Float = 1
    var opacity: Float = 1
    var kind: MaterialKind = .PLASTIC
    var shine: Float = 0.5
    /// v27 gradyan ikinci rengi (nil: tek renk).
    var gradient: Int32? = nil
    var gradientAxis: Int = 1

    var transparent: Bool { opacity < 0.995 || kind == .GLASS }

    static let DEFAULT_COLOR: Int32 = Int32(bitPattern: 0xFF4F_8BD6)
}

enum UvMode: Int, CaseIterable { case BOX, CYLINDER, SPHERE, PLANAR, MESH }

/// Modelin bir parçası.
struct Part {
    var id: String
    var name: String
    var mesh: Mesh
    var primitive: PrimitiveSpec?
    var position: V3 = .ZERO
    var rotation: V3 = .ZERO
    var scale: V3 = .ONE
    var material: Material = Material()
    var faceMaterials: [Int: Material] = [:]
    var smooth: Bool = false
    var uv: UvMode = .BOX
    var visible: Bool = true
    var group: String? = nil
    var brush: BrushSource? = nil

    init(_ id: String, _ name: String, _ mesh: Mesh, _ primitive: PrimitiveSpec?, position: V3 = .ZERO, rotation: V3 = .ZERO,
         scale: V3 = .ONE, material: Material = Material(), faceMaterials: [Int: Material] = [:], smooth: Bool = false,
         uv: UvMode = .BOX, visible: Bool = true, group: String? = nil, brush: BrushSource? = nil) {
        self.id = id; self.name = name; self.mesh = mesh; self.primitive = primitive; self.position = position
        self.rotation = rotation; self.scale = scale; self.material = material; self.faceMaterials = faceMaterials
        self.smooth = smooth; self.uv = uv; self.visible = visible; self.group = group; self.brush = brush
    }

    func materialOf(_ face: Int) -> Material { faceMaterials[face] ?? material }
}

struct Group3: Hashable { var id: String; var name: String }

/// 3B düzleme çizilmiş çizgi.
struct Stroke3: Equatable {
    let id: String
    let points: [Float]
    let color: Int32
    let width: Float
    var time: Int64 = 0
    init(_ id: String, _ points: [Float], _ color: Int32, _ width: Float, time: Int64 = 0) {
        self.id = id; self.points = points; self.color = color; self.width = width; self.time = time
    }
    var count: Int { points.count / 3 }
    static func == (a: Stroke3, b: Stroke3) -> Bool { a.id == b.id && a.color == b.color && a.width == b.width && a.points == b.points }
}

struct Light: Equatable {
    var yaw: Float = 35
    var pitch: Float = 55
    var intensity: Float = 1
    var ambient: Float = 0.38
    var color: Int32 = -1

    var direction: V3 {
        let y = toRadians(Double(yaw)), p = toRadians(Double(pitch))
        return V3(Float(cos(p) * sin(y)), Float(sin(p)), Float(cos(p) * cos(y)))
    }
}

struct SceneSettings: Equatable {
    var light = Light()
    var shadow = true
    var edges = true
    var edgeColor: Int32 = Int32(bitPattern: 0xFF26_3238)
    var background: Int32 = 0
    var ground = true
}

struct Camera3: Equatable {
    var yaw: Float = 35
    var pitch: Float = 24
    var persp: Float = 0.35
    var zoom: Float = 1
    var target = V3(0, 0.5, 0)
}

/// 3B model.
struct Model3D {
    var id: String
    var name: String
    var parts: [Part]
    var strokes: [Stroke3] = []
    var scene = SceneSettings()
    var camera = Camera3()
    var groups: [Group3] = []

    init(_ id: String, _ name: String, _ parts: [Part], strokes: [Stroke3] = [], scene: SceneSettings = SceneSettings(),
         camera: Camera3 = Camera3(), groups: [Group3] = []) {
        self.id = id; self.name = name; self.parts = parts; self.strokes = strokes; self.scene = scene
        self.camera = camera; self.groups = groups
    }

    var isEmpty: Bool { !parts.contains { $0.visible } && strokes.isEmpty }

    func part(_ id: String) -> Part? { parts.first { $0.id == id } }

    func replacePart(_ part: Part) -> Model3D {
        var c = self
        c.parts = parts.map { $0.id == part.id ? part : $0 }
        return c
    }

    var triangleCount: Int { parts.reduce(0) { s, p in s + p.mesh.faces.reduce(0) { $0 + max($1.count - 2, 0) } } }

    var visibleTriangles: Int {
        parts.filter { $0.visible }.reduce(0) { s, p in s + p.mesh.faces.reduce(0) { $0 + max($1.count - 2, 0) } }
    }
}

/// Dünya kutusu.
struct Box3: Equatable {
    var min: V3, max: V3
    init(_ min: V3, _ max: V3) { self.min = min; self.max = max }
    var center: V3 { (min + max) * 0.5 }
    var size: V3 { max - min }
    var radius: Float { size.length * 0.5 }

    func union(_ o: Box3) -> Box3 {
        Box3(V3(Swift.min(min.x, o.min.x), Swift.min(min.y, o.min.y), Swift.min(min.z, o.min.z)),
             V3(Swift.max(max.x, o.max.x), Swift.max(max.y, o.max.y), Swift.max(max.z, o.max.z)))
    }
}

/// Kotlin `PI.toFloat()` (en yakına yuvarlanmış; Swift'in `Float.pi` değeri sıfıra doğru yuvarlanır).
let PI_F = Float(Double.pi)

/// Kotlin `kotlin.math.sin/cos(Float)`: çift duyarlıkta hesaplanıp yuvarlanır.
@inline(__always) func ktSinF(_ x: Float) -> Float { Float(sin(Double(x))) }
@inline(__always) func ktCosF(_ x: Float) -> Float { Float(cos(Double(x))) }
@inline(__always) func floorModI(_ a: Int, _ b: Int) -> Int { let r = a % b; return r != 0 && (r < 0) != (b < 0) ? r + b : r }
