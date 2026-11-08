import Foundation

/// Hazır 3B şekil: kimlik, ad, kategori, kısa açıklama ve modeli kuran işlev.
struct CatalogItem {
    let id: String, name: String, category: String, description: String
    let build: () -> Model3D
}

/// v24 hazır 3B şekiller (Android `Catalog3D`): çizim çalışmalarında ve derslerde kullanılacak az ayrıntılı modeller.
/// Modeller program içinde kurulur; seçilince kopyası düzenlenir ya da tuvale eklenir.
enum Catalog3D {

    static let GEOMETRY = "Geometri"
    static let PHYSICS = "Fizik"
    static let CHEMISTRY = "Kimya"
    static let BIOLOGY = "Biyoloji"
    static let ASTRONOMY = "Astronomi"
    static let DRAWING = "Çizim"

    static let categories = [GEOMETRY, PHYSICS, CHEMISTRY, BIOLOGY, ASTRONOMY, DRAWING]

    /// Katalog kimliği öneki (düzenleyiciye "catalog:<kimlik>" ile açılır).
    static let PREFIX = "catalog:"

    static func byId(_ id: String) -> CatalogItem? {
        let key = id.hasPrefix(PREFIX) ? String(id.dropFirst(PREFIX.count)) : id
        return all.first { $0.id == key }
    }

    // MARK: renkler
    private static let WHITE: UInt32 = 0xFFFF_FFFF
    private static let PAPER: UInt32 = 0xFFF5_F5F2
    private static let GLASS: UInt32 = 0xFF9F_D3F2
    private static let RED: UInt32 = 0xFFE5_3935
    private static let GREEN: UInt32 = 0xFF43_A047
    private static let BLUE: UInt32 = 0xFF1E_88E5
    private static let ORANGE: UInt32 = 0xFFF2_8C28
    private static let YELLOW: UInt32 = 0xFFFD_D835
    private static let PURPLE: UInt32 = 0xFF8E_24AA
    private static let GRAY: UInt32 = 0xFF90_A4AE
    private static let DARK: UInt32 = 0xFF37_474F
    private static let TEAL: UInt32 = 0xFF26_A69A
    private static let BROWN: UInt32 = 0xFF8D_6E63
    private static let INK: UInt32 = 0xFF26_3238

    private static func cf(_ v: Float) -> Float { ktCosF(v) }
    private static func sf(_ v: Float) -> Float { ktSinF(v) }
    private static func toDegrees(_ r: Double) -> Double { r * 57.29577951308232 }

    // MARK: kurucu

    fileprivate final class B {
        let id: String
        var parts: [Part] = []
        var strokes: [Stroke3] = []
        init(_ id: String) { self.id = id }

        /// Dünya koordinatındaki ağdan parça (konumu taban merkezi).
        func mesh(_ name: String, _ mesh: Mesh, _ color: UInt32, kind: MaterialKind = .PLASTIC, smooth: Bool = false,
                  opacity: Float = 1, shine: Float = 0.4, texture: String? = nil, texScale: Float = 1, uv: UvMode = .BOX) {
            if mesh.faceCount == 0 { return }
            let b = mesh.bounds
            let origin = V3(b.center.x, b.min.y, b.center.z)
            let mat = Material(color: Int32(bitPattern: color), texture: texture, textureScale: texScale, opacity: opacity, kind: kind, shine: shine)
            parts.append(Part("\(id)-\(parts.count)", name, mesh.mapPositions { $0 - origin }, nil, position: origin, material: mat, smooth: smooth, uv: uv))
        }

        /// Hazır cisim (ayarları sonradan değiştirilebilir).
        func prim(_ kind: PrimitiveKind, _ name: String, _ pos: V3, _ scale: V3, _ color: UInt32, rot: V3 = .ZERO,
                  kindM: MaterialKind = .PLASTIC, opacity: Float = 1, shine: Float = 0.4, texture: String? = nil, texScale: Float = 1,
                  spec: PrimitiveSpec? = nil) {
            let sp = spec ?? Primitives.defaultSpec(kind)
            let mat = Material(color: Int32(bitPattern: color), texture: texture, textureScale: texScale, opacity: opacity, kind: kindM, shine: shine)
            parts.append(Part("\(id)-\(parts.count)", name, Primitives.build(sp), sp, position: pos, rotation: rot, scale: scale, material: mat,
                              smooth: kind.smoothByDefault, uv: kind.uvByDefault))
        }

        func line(_ points: [V3], _ color: UInt32 = Catalog3D.INK, _ width: Float = 0.015) {
            if points.count < 2 { return }
            var arr = [Float](repeating: 0, count: points.count * 3)
            for (i, p) in points.enumerated() { arr[3 * i] = p.x; arr[3 * i + 1] = p.y; arr[3 * i + 2] = p.z }
            strokes.append(Stroke3("\(id)-s\(strokes.count)", arr, Int32(bitPattern: color), width))
        }

        /// Kesikli çizgi.
        func dashed(_ a: V3, _ b: V3, _ color: UInt32 = Catalog3D.INK, _ width: Float = 0.012, _ dashes: Int = 8) {
            for i in 0..<dashes {
                let t0 = Float(i) / Float(dashes), t1 = (Float(i) + 0.55) / Float(dashes)
                let d = b - a
                line([a + d * t0, a + d * t1], color, width)
            }
        }
    }

    private static func model(_ id: String, _ name: String, _ yaw: Float, _ pitch: Float, _ persp: Float = 0.35, _ build: (B) -> Void) -> Model3D {
        let b = B(id)
        build(b)
        var tmp = Model3D(id, name, b.parts, strokes: b.strokes)
        let box = ModelEdit.modelBounds(tmp) ?? Box3(.ZERO, .ONE)
        let zoom = (View3.REFERENCE_EXTENT / (box.radius * 1.12).coerceAtLeast(0.05)).coerceIn(0.1, 30)
        tmp.camera = Camera3(yaw: yaw, pitch: pitch, persp: persp, zoom: zoom, target: box.center)
        return tmp
    }

    private static func item(_ id: String, _ name: String, _ category: String, _ description: String, yaw: Float = 35, pitch: Float = 22,
                             _ build: @escaping (B) -> Void) -> CatalogItem {
        CatalogItem(id: id, name: name, category: category, description: description) { model(id, name, yaw, pitch, 0.35, build) }
    }

    private typealias G = Generators3D

    private static func closedCircle(_ c: V3, _ r: Float, _ n: Int) -> [V3] { let p = G.circle(c, r, n); return p + [p[0]] }

    // MARK: katalog

    static let all: [CatalogItem] = geometry() + physics() + chemistry() + biology() + astronomy() + drawing()

    private static func geometry() -> [CatalogItem] { [
        item("geo.cube_diag", "Küp ve köşegenleri", GEOMETRY, "Yüz köşegeni (kırmızı) ve cisim köşegeni (mavi)") { b in
            b.prim(.CUBE, "Küp", V3(0, 0.5, 0), .ONE, GLASS, kindM: .GLASS, opacity: 0.55)
            b.line([V3(-0.5, 0, 0.5), V3(0.5, 1, 0.5)], RED, 0.018)
            b.line([V3(-0.5, 0, -0.5), V3(0.5, 1, 0.5)], BLUE, 0.02)
        },
        item("geo.cuboid", "Dikdörtgenler prizması", GEOMETRY, "a × b × c boyutlu prizma") { b in
            b.prim(.CUBE, "Prizma", V3(0, 0.4, 0), V3(1.4, 0.8, 0.7), ORANGE)
        },
        item("geo.pyramid", "Kare piramit", GEOMETRY, "Tabanı kare, yüksekliği gösterilmiş piramit") { b in
            b.prim(.PYRAMID, "Piramit", V3(0, 0.5, 0), .ONE, YELLOW, kindM: .GLASS, opacity: 0.6)
            b.dashed(V3(0, 0, 0), V3(0, 1, 0), RED)
        },
        item("geo.tetra", "Düzgün dörtyüzlü", GEOMETRY, "Dört eşkenar üçgen yüz (tetrahedron)") { b in b.mesh("Dörtyüzlü", G.tetrahedron(0.6), TEAL) },
        item("geo.octa", "Düzgün sekizyüzlü", GEOMETRY, "Sekiz eşkenar üçgen yüz (oktahedron)") { b in b.mesh("Sekizyüzlü", G.octahedron(0.6), PURPLE) },
        item("geo.dodeca", "Düzgün onikiyüzlü", GEOMETRY, "On iki düzgün beşgen yüz (dodekahedron)") { b in b.mesh("Onikiyüzlü", G.dodecahedron(0.6), BLUE) },
        item("geo.icosa", "Düzgün yirmiyüzlü", GEOMETRY, "Yirmi eşkenar üçgen yüz (ikosahedron)") { b in b.mesh("Yirmiyüzlü", G.icosahedron(0.6), GREEN) },
        item("geo.frustum", "Kesik koni", GEOMETRY, "Alt ve üst tabanı farklı yarıçaplı") { b in
            b.prim(.CYLINDER, "Kesik koni", V3(0, 0.45, 0), V3(1, 0.9, 1), ORANGE, spec: PrimitiveSpec(.CYLINDER, 24, 1, top: 0.5))
        },
        item("geo.frustum_pyr", "Kesik piramit", GEOMETRY, "Kare tabanlı kesik piramit") { b in
            b.prim(.PYRAMID, "Kesik piramit", V3(0, 0.4, 0), V3(1, 0.8, 1), TEAL, spec: PrimitiveSpec(.PYRAMID, 4, 1, top: 0.5))
        },
        item("geo.cylinder", "Silindir (r ve h)", GEOMETRY, "Yarıçap ve yükseklik çizgileriyle") { b in
            b.prim(.CYLINDER, "Silindir", V3(0, 0.6, 0), V3(1, 1.2, 1), GLASS, kindM: .GLASS, opacity: 0.55)
            b.line([V3(0, 1.2, 0), V3(0.5, 1.2, 0)], RED)
            b.dashed(V3(0, 0, 0), V3(0, 1.2, 0), BLUE)
        },
        item("geo.cone", "Koni (h ve ana doğru)", GEOMETRY, "Yükseklik, yarıçap ve ana doğru") { b in
            b.prim(.CONE, "Koni", V3(0, 0.6, 0), V3(1, 1.2, 1), GLASS, kindM: .GLASS, opacity: 0.55)
            b.dashed(V3(0, 0, 0), V3(0, 1.2, 0), BLUE)
            b.line([V3(0, 0, 0), V3(0.5, 0, 0)], RED)
            b.line([V3(0.5, 0, 0), V3(0, 1.2, 0)], GREEN)
        },
        item("geo.sphere", "Küre (r ve büyük çember)", GEOMETRY, "Merkez, yarıçap ve ekvator") { b in
            b.prim(.SPHERE, "Küre", V3(0, 0.6, 0), V3(1.2, 1.2, 1.2), GLASS, kindM: .GLASS, opacity: 0.5)
            b.line(closedCircle(V3(0, 0.6, 0), 0.6, 40), RED, 0.012)
            b.line([V3(0, 0.6, 0), V3(0.6, 0.6, 0)], BLUE)
        },
        item("geo.hemisphere", "Yarım küre", GEOMETRY, "Taban daireli yarım küre") { b in
            b.prim(.DOME, "Yarım küre", V3(0, 0.25, 0), V3(1.2, 1.2, 1.2), BLUE)
        },
        item("geo.prism5", "Beşgen prizma", GEOMETRY, "Düzgün beşgen tabanlı dik prizma") { b in
            b.mesh("Beşgen prizma", Primitives.revolveFrustum(5, 0.5, 0.5, 0).mapPositions { $0 + V3(0, 0.5, 0) }, GREEN)
        },
        item("geo.prism6", "Düzgün altıgen prizma", GEOMETRY, "Düzgün altıgen tabanlı dik prizma") { b in
            b.prim(.PRISM6, "Altıgen prizma", V3(0, 0.5, 0), .ONE, ORANGE)
        },
        item("geo.prism3", "Düzgün üçgen prizma", GEOMETRY, "Eşkenar üçgen tabanlı dik prizma") { b in
            b.prim(.PRISM3, "Üçgen prizma", V3(0, 0.5, 0), .ONE, PURPLE)
        },
        item("geo.torus", "Simit (torus)", GEOMETRY, "Çemberin bir eksen çevresinde dönmesiyle") { b in
            b.prim(.TORUS, "Simit", V3(0, 0.15, 0), V3(1.2, 1.2, 1.2), RED)
        },
        item("geo.axes", "Koordinat sistemi", GEOMETRY, "x (kırmızı), y (yeşil), z (mavi) eksenleri ve birim küp") { b in
            b.mesh("x ekseni", G.arrow(V3(0, 0, 0), V3(1.4, 0, 0), 0.025), RED, smooth: true)
            b.mesh("y ekseni", G.arrow(V3(0, 0, 0), V3(0, 1.4, 0), 0.025), GREEN, smooth: true)
            b.mesh("z ekseni", G.arrow(V3(0, 0, 0), V3(0, 0, 1.4), 0.025), BLUE, smooth: true)
            b.mesh("Başlangıç", G.sphere(.ZERO, 0.05), DARK, smooth: true)
            b.prim(.CUBE, "Birim küp", V3(0.5, 0.5, 0.5), .ONE, GLASS, kindM: .GLASS, opacity: 0.25)
        },
        item("geo.conic", "Koni kesitleri", GEOMETRY, "Çift koni ve eğik düzlem (elips)") { b in
            b.prim(.CONE, "Üst koni", V3(0, 1.2, 0), V3(1, 0.8, 1), ORANGE, rot: V3(180, 0, 0))
            b.prim(.CONE, "Alt koni", V3(0, 0.4, 0), V3(1, 0.8, 1), ORANGE)
            b.prim(.PLANE, "Kesen düzlem", V3(0, 0.55, 0), V3(1.6, 1, 1.6), GLASS, rot: V3(25, 0, 0), kindM: .GLASS, opacity: 0.5)
        },
        item("geo.paraboloid", "Paraboloid", GEOMETRY, "y = x² + z² yüzeyi (çanak)") { b in
            b.mesh("Paraboloid", G.surface(16, 24, closeV: true) { (u: Float, v: Float) -> V3 in
                let r = u * 0.7, a = v * 2 * PI_F
                return V3(r * cf(a), r * r * 1.6, r * sf(a))
            }, BLUE, smooth: true)
        },
        item("geo.saddle", "Eyer yüzeyi", GEOMETRY, "y = x² − z² (hiperbolik paraboloid)") { b in
            b.mesh("Eyer", G.surface(18, 18) { (u: Float, v: Float) -> V3 in
                let x = (u - 0.5) * 1.6, z = (v - 0.5) * 1.6
                return V3(x, (x * x - z * z) * 0.45 + 0.35, z)
            }, ORANGE, smooth: true)
        },
        item("geo.hyperboloid", "Tek yapraklı hiperboloid", GEOMETRY, "x² + z² − y² = 1 biçiminde dönel yüzey") { b in
            b.mesh("Hiperboloid", G.surface(24, 12, closeU: true) { (u: Float, v: Float) -> V3 in
                let a = u * 2 * PI_F, t = (v - 0.5) * 2
                let r: Float = 0.3 * (1 + 3 * t * t).squareRoot()
                return V3(r * cf(a), v * 1.4, r * sf(a))
            }, TEAL, smooth: true)
        },
        item("geo.mobius", "Möbius şeridi", GEOMETRY, "Tek yüzlü yüzey") { b in
            b.mesh("Möbius şeridi", G.surface(48, 4) { (u: Float, v: Float) -> V3 in
                let a = u * 2 * PI_F, w = (v - 0.5) * 0.45
                let r = 0.6 + w * cf(a / 2)
                return V3(r * cf(a), 0.5 + w * sf(a / 2), r * sf(a))
            }, PURPLE, smooth: true)
        },
        item("geo.helix", "Helis (sarmal)", GEOMETRY, "Silindir üzerinde sabit eğimli eğri") { b in
            let pts: [V3] = (0..<120).map { (i: Int) -> V3 in
                let t = Float(i) / 119 * 4 * PI_F
                return V3(0.45 * cf(t), 0.05 + Float(i) / 119 * 1.4, 0.45 * sf(t))
            }
            b.mesh("Sarmal", G.tube(pts, 0.045, 8), RED, smooth: true)
            b.prim(.CYLINDER, "Silindir", V3(0, 0.75, 0), V3(0.9, 1.5, 0.9), GLASS, kindM: .GLASS, opacity: 0.25)
        },
        item("geo.knot", "Simit düğümü", GEOMETRY, "(2, 3) torus düğümü (yonca düğümü)") { b in
            let pts: [V3] = (0..<150).map { (i: Int) -> V3 in
                let t = Float(i) / 150 * 2 * PI_F
                let r = 0.45 + 0.18 * cf(3 * t)
                return V3(r * cf(2 * t), 0.45 + 0.18 * sf(3 * t), r * sf(2 * t))
            }
            b.mesh("Düğüm", G.tube(pts, 0.07, 8, closed: true), ORANGE, smooth: true)
        },
        item("geo.wave", "Dalga yüzeyi", GEOMETRY, "y = sin x · cos z") { b in
            b.mesh("Dalga", G.surface(22, 22) { (u: Float, v: Float) -> V3 in
                let x = (u - 0.5) * 2, z = (v - 0.5) * 2
                return V3(x, 0.25 + 0.18 * sf(3 * x) * cf(3 * z), z)
            }, BLUE, smooth: true)
        },
    ] }

    private static func physics() -> [CatalogItem] { [
        item("phy.incline", "Eğik düzlem", PHYSICS, "Eğik düzlemde blok; ağırlık (kırmızı) ve tepki kuvveti (mavi)", yaw: 70) { b in
            b.prim(.WEDGE, "Eğik düzlem", V3(0, 0.35, 0), V3(0.8, 0.7, 2), 0xFFBC_AAA4, texture: "wood.pine", texScale: 0.6)
            let n = V3(0, 2, 0.7).normalized()
            let angle = Float(toDegrees(asin(Double(n.z))))
            let c = V3(0, 0.35, 0) + n * 0.16
            b.prim(.CUBE, "Blok", c, V3(0.32, 0.32, 0.32), BLUE, rot: V3(angle, 0, 0))
            b.mesh("Ağırlık", G.arrow(c, c + V3(0, -0.6, 0), 0.02), RED, smooth: true)
            b.mesh("Tepki kuvveti", G.arrow(c, c + n * 0.55, 0.02), 0xFF39_49AB, smooth: true)
        },
        item("phy.pulley", "Makara", PHYSICS, "Sabit makara ve iki kütle") { b in
            b.prim(.CUBE, "Tavan", V3(0, 1.75, 0), V3(1.2, 0.08, 0.4), GRAY)
            b.prim(.CUBE, "Askı", V3(0, 1.6, 0), V3(0.06, 0.24, 0.06), DARK)
            b.prim(.CYLINDER, "Makara", V3(0, 1.45, 0), V3(0.5, 0.1, 0.5), 0xFFB0_BEC5, rot: V3(90, 0, 0), kindM: .METAL, shine: 0.6)
            b.line([V3(-0.25, 1.45, 0), V3(-0.25, 0.75, 0)], INK, 0.012)
            b.line([V3(0.25, 1.45, 0), V3(0.25, 0.45, 0)], INK, 0.012)
            b.prim(.CUBE, "Kütle 1", V3(-0.25, 0.62, 0), V3(0.24, 0.26, 0.24), RED)
            b.prim(.CUBE, "Kütle 2", V3(0.25, 0.29, 0), V3(0.3, 0.32, 0.3), BLUE)
        },
        item("phy.pendulum", "Basit sarkaç", PHYSICS, "İp, kütle ve salınım yayı") { b in
            b.prim(.CUBE, "Taban", V3(0, 0.03, 0), V3(1.2, 0.06, 0.5), BROWN, texture: "wood.walnut", texScale: 0.5)
            b.prim(.CYLINDER, "Direk", V3(-0.5, 0.8, 0), V3(0.05, 1.5, 0.05), GRAY, kindM: .METAL)
            b.prim(.CYLINDER, "Kol", V3(-0.15, 1.53, 0), V3(0.04, 0.7, 0.04), GRAY, rot: V3(0, 0, 90), kindM: .METAL)
            let pivot = V3(0.15, 1.53, 0)
            let a = toRadians(20.0)
            let bob = pivot + V3(Float(sin(a) * 1.1), Float(-cos(a) * 1.1), 0)
            b.line([pivot, bob], INK, 0.01)
            b.mesh("Kütle", G.sphere(bob, 0.09), ORANGE, kind: .METAL, smooth: true, shine: 0.7)
            b.dashed(pivot, pivot + V3(0, -1.1, 0), GRAY, 0.008)
            let arc: [V3] = (0..<16).map { (i: Int) -> V3 in
                let t = toRadians(-20.0 + 40.0 * Double(i) / 15)
                return pivot + V3(Float(sin(t) * 1.1), Float(-cos(t) * 1.1), 0)
            }
            b.line(arc, BLUE, 0.01)
        },
        item("phy.spring", "Yay ve kütle", PHYSICS, "Tavana asılı yay ve kütle (Hooke yasası)") { b in
            b.prim(.CUBE, "Tavan", V3(0, 1.6, 0), V3(0.8, 0.06, 0.4), GRAY)
            let pts: [V3] = (0..<140).map { (i: Int) -> V3 in
                let t = Float(i) / 139 * 10 * 2 * PI_F
                return V3(0.12 * cf(t), 1.57 - Float(i) / 139 * 0.9, 0.12 * sf(t))
            }
            b.mesh("Yay", G.tube(pts, 0.018, 6), 0xFFB0_BEC5, kind: .METAL, smooth: true, shine: 0.7)
            b.prim(.CUBE, "Kütle", V3(0, 0.5, 0), V3(0.36, 0.36, 0.36), RED)
        },
        item("phy.lens", "İnce kenarlı mercek", PHYSICS, "Paralel ışınlar odakta toplanır") { b in
            let rs: [Float] = [0, 0.14, 0.26, 0.36, 0.43, 0.48, 0.5]
            func z(_ r: Float) -> Float { 1 - (r / 0.5) * (r / 0.5) }
            var prof: [(Float, Float)] = rs.map { (r: Float) -> (Float, Float) in (r, -0.07 * z(r)) }
            for r in rs.reversed().dropFirst() { prof.append((r, 0.07 * z(r))) }
            let lens = G.place(G.revolve(prof, 24, capBottom: false, capTop: false), V3(0, 0.6, 0), .ONE, V3(90, 0, 0))
            b.mesh("Mercek", lens, GLASS, kind: .GLASS, smooth: true, opacity: 0.6)
            b.dashed(V3(0, 0.6, -1.4), V3(0, 0.6, 1.4), GRAY, 0.008, 14)
            for h: Float in [-0.3, -0.15, 0.15, 0.3] {
                b.line([V3(0, 0.6 + h, -1.3), V3(0, 0.6 + h, 0), V3(0, 0.6, 0.9)], ORANGE, 0.01)
            }
            b.mesh("Odak", G.sphere(V3(0, 0.6, 0.9), 0.035), RED, smooth: true)
        },
        item("phy.prism", "Işık prizması", PHYSICS, "Beyaz ışığın renklere ayrılması", yaw: 10, pitch: 30) { b in
            b.prim(.PRISM3, "Prizma", V3(0, 0.4, 0), V3(0.9, 0.8, 0.9), GLASS, rot: V3(90, 0, 0), kindM: .GLASS, opacity: 0.55)
            b.line([V3(-1.3, 0.25, 0), V3(-0.2, 0.45, 0)], WHITE, 0.02)
            for (i, c) in [RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE].enumerated() {
                b.line([V3(0.2, 0.45, 0), V3(1.3, 0.75 - Float(i) * 0.12, 0)], c, 0.014)
            }
        },
        item("phy.magnet", "U mıknatıs", PHYSICS, "Kuzey (kırmızı) ve güney (mavi) kutupları") { b in
            var left: [V3] = [], right: [V3] = []
            for i in 0...10 { left.append(V3(-0.3, 1.1 - Float(i) * 0.07, 0)) }
            for i in 0...12 { let a = PI_F + Float(i) / 12 * PI_F / 2; left.append(V3(0.3 * cf(a), 0.4 + 0.3 * sf(a), 0)) }
            for i in 0...12 { let a = 1.5 * PI_F + Float(i) / 12 * PI_F / 2; right.append(V3(0.3 * cf(a), 0.4 + 0.3 * sf(a), 0)) }
            for i in 0...10 { right.append(V3(0.3, 0.4 + Float(i) * 0.07, 0)) }
            b.mesh("Kuzey (N)", G.tube(left, 0.1, 8), RED, smooth: true)
            b.mesh("Güney (S)", G.tube(right, 0.1, 8), BLUE, smooth: true)
            for k in 1...3 {
                let kk = Double(k)
                let pts: [V3] = (0..<17).map { (i: Int) -> V3 in
                    let t = Double.pi * Double(i) / 16
                    let x: Double = -0.3 * cos(t) * (1 + kk * 0.25)
                    let y: Double = 1.1 + 0.25 * kk * sin(t)
                    return V3(Float(x), Float(y), 0)
                }
                b.line(pts, GRAY, 0.008)
            }
        },
        item("phy.atom", "Atom modeli", PHYSICS, "Çekirdek (proton, nötron) ve yörüngelerde elektronlar") { b in
            var nucleus: [Mesh] = [], neutrons: [Mesh] = []
            let offs = [V3(0, 0, 0), V3(0.08, 0.05, 0), V3(-0.07, 0.06, 0.04), V3(0.02, -0.07, 0.06), V3(-0.05, -0.04, -0.07), V3(0.06, 0.02, -0.07)]
            for (i, o) in offs.enumerated() {
                let s = G.sphere(V3(0, 0.8, 0) + o, 0.07, 10, 5)
                if i % 2 == 0 { nucleus.append(s) } else { neutrons.append(s) }
            }
            b.mesh("Protonlar", Mesh.concat(nucleus), RED, smooth: true)
            b.mesh("Nötronlar", Mesh.concat(neutrons), GRAY, smooth: true)
            var rings: [Mesh] = [], electrons: [Mesh] = []
            for k in 0..<3 {
                let tilt = V3(60 * Float(k - 1), 60 * Float(k), 0)
                let circle = G.circle(.ZERO, 0.6, 48)
                let t = Transform3.of(V3(0, 0.8, 0), tilt, .ONE)
                rings.append(G.tube(circle.map { t.apply($0) }, 0.008, 4, closed: true))
                electrons.append(G.sphere(t.apply(circle[k * 16]), 0.045, 10, 5))
            }
            b.mesh("Yörüngeler", Mesh.concat(rings), GRAY, smooth: true)
            b.mesh("Elektronlar", Mesh.concat(electrons), BLUE, kind: .GLOW, smooth: true)
        },
        item("phy.vectors", "Vektör toplama", PHYSICS, "a (kırmızı) + b (mavi) = a + b (mor), paralelkenar kuralı") { b in
            let a = V3(1.1, 0, 0.2), c = V3(0.3, 0, 0.9)
            b.mesh("a", G.arrow(.ZERO, a, 0.022), RED, smooth: true)
            b.mesh("b", G.arrow(.ZERO, c, 0.022), BLUE, smooth: true)
            b.mesh("a + b", G.arrow(.ZERO, a + c, 0.026), PURPLE, smooth: true)
            b.dashed(a, a + c, GRAY); b.dashed(c, a + c, GRAY)
            b.prim(.PLANE, "Düzlem", V3(0.7, -0.002, 0.55), V3(2, 1, 1.5), PAPER, kindM: .MATTE)
        },
        item("phy.circuit", "Basit elektrik devresi", PHYSICS, "Pil, ampul ve iletken teller") { b in
            b.prim(.CYLINDER, "Pil", V3(-0.6, 0.12, 0), V3(0.24, 0.5, 0.24), 0xFF42_4242, rot: V3(0, 0, 90))
            b.prim(.CYLINDER, "Pil ucu (+)", V3(-0.33, 0.12, 0), V3(0.1, 0.06, 0.1), 0xFFB0_BEC5, rot: V3(0, 0, 90), kindM: .METAL)
            b.prim(.CYLINDER, "Duy", V3(0.6, 0.1, 0), V3(0.16, 0.2, 0.16), GRAY, kindM: .METAL)
            b.mesh("Ampul", G.sphere(V3(0.6, 0.36, 0), 0.15), 0xFFFF_F59D, kind: .GLOW, smooth: true)
            let wire1 = [V3(-0.3, 0.12, 0), V3(-0.1, 0.12, 0), V3(-0.1, 0.12, 0.45), V3(0.6, 0.12, 0.45), V3(0.6, 0.05, 0.08)]
            let wire2 = [V3(-0.86, 0.12, 0), V3(-1, 0.12, 0), V3(-1, 0.12, -0.45), V3(0.6, 0.12, -0.45), V3(0.6, 0.05, -0.08)]
            b.mesh("Kırmızı tel", G.tube(wire1, 0.015, 6), RED, smooth: true)
            b.mesh("Siyah tel", G.tube(wire2, 0.015, 6), DARK, smooth: true)
        },
    ] }

    // MARK: kimya

    fileprivate final class Mol {
        let b: B
        var order: [String] = []
        var atoms: [String: (UInt32, [Mesh])] = [:]
        var bonds: [Mesh] = []
        init(_ b: B) { self.b = b }
        func atom(_ element: String, _ color: UInt32, _ p: V3, _ r: Float) {
            if atoms[element] == nil { order.append(element); atoms[element] = (color, []) }
            atoms[element]!.1.append(G.sphere(p, r, 14, 7))
        }
        func bond(_ a: V3, _ c: V3, _ r: Float = 0.035) { bonds.append(G.cylinder(a, c, r, 8)) }
        func double(_ a: V3, _ c: V3, _ side: V3, _ r: Float = 0.026) { let s = side.normalized() * 0.045; bond(a + s, c + s, r); bond(a - s, c - s, r) }
        func done() {
            for el in order { let v = atoms[el]!; b.mesh(el, Mesh.concat(v.1), v.0, smooth: true, shine: 0.6) }
            if !bonds.isEmpty { b.mesh("Bağlar", Mesh.concat(bonds), 0xFFCF_D8DC, smooth: true) }
        }
    }

    private static func molecule(_ id: String, _ name: String, _ description: String, _ build: @escaping (Mol) -> Void) -> CatalogItem {
        item(id, name, CHEMISTRY, description) { b in let m = Mol(b); build(m); m.done() }
    }

    private static let C_COLOR: UInt32 = 0xFF42_4242
    private static let H_COLOR: UInt32 = 0xFFF5_F5F5
    private static let O_COLOR: UInt32 = 0xFFE5_3935
    private static let N_COLOR: UInt32 = 0xFF39_49AB

    private static func chemistry() -> [CatalogItem] { [
        molecule("chem.h2o", "Su (H₂O)", "Açısal molekül (104,5°)") { m in
            let o = V3(0, 0.5, 0)
            let a = toRadians(104.5 / 2)
            let h1 = o + V3(Float(sin(a) * 0.45), Float(-cos(a) * 0.45), 0)
            let h2 = o + V3(Float(-sin(a) * 0.45), Float(-cos(a) * 0.45), 0)
            m.atom("Oksijen", O_COLOR, o, 0.2); m.atom("Hidrojen", H_COLOR, h1, 0.13); m.atom("Hidrojen", H_COLOR, h2, 0.13)
            m.bond(o, h1); m.bond(o, h2)
        },
        molecule("chem.co2", "Karbondioksit (CO₂)", "Doğrusal molekül, iki çift bağ") { m in
            let c = V3(0, 0.3, 0), o1 = c + V3(-0.55, 0, 0), o2 = c + V3(0.55, 0, 0)
            m.atom("Karbon", C_COLOR, c, 0.18); m.atom("Oksijen", O_COLOR, o1, 0.2); m.atom("Oksijen", O_COLOR, o2, 0.2)
            m.double(c, o1, V3(0, 1, 0)); m.double(c, o2, V3(0, 1, 0))
        },
        molecule("chem.ch4", "Metan (CH₄)", "Düzgün dörtyüzlü (109,5°)") { m in
            let c = V3(0, 0.55, 0)
            m.atom("Karbon", C_COLOR, c, 0.18)
            for d in [V3(1, 1, 1), V3(1, -1, -1), V3(-1, 1, -1), V3(-1, -1, 1)] {
                let h = c + d.normalized() * 0.5
                m.atom("Hidrojen", H_COLOR, h, 0.12); m.bond(c, h)
            }
        },
        molecule("chem.nh3", "Amonyak (NH₃)", "Üçgen piramit (107°)") { m in
            let n = V3(0, 0.6, 0)
            m.atom("Azot", N_COLOR, n, 0.19)
            for k in 0..<3 {
                let a = 2.0 * Double.pi * Double(k) / 3
                let h = n + V3(Float(0.45 * cos(a)), -0.2, Float(0.45 * sin(a)))
                m.atom("Hidrojen", H_COLOR, h, 0.12); m.bond(n, h)
            }
        },
        molecule("chem.benzene", "Benzen (C₆H₆)", "Altıgen halka, ardışık çift bağlar") { m in
            let cs: [V3] = (0..<6).map { (k: Int) -> V3 in let a = 2.0 * Double.pi * Double(k) / 6; return V3(Float(0.5 * cos(a)), 0.15, Float(0.5 * sin(a))) }
            for (k, c) in cs.enumerated() {
                m.atom("Karbon", C_COLOR, c, 0.13)
                let h = c + V3(c.x, 0, c.z).normalized() * 0.38
                m.atom("Hidrojen", H_COLOR, h, 0.09); m.bond(c, h, 0.03)
                let next = cs[(k + 1) % 6]
                if k % 2 == 0 { m.double(c, next, V3(0, 1, 0)) } else { m.bond(c, next) }
            }
        },
        molecule("chem.ethanol", "Etanol (C₂H₅OH)", "İki karbonlu alkol") { m in
            let c1 = V3(-0.3, 0.5, 0), c2 = V3(0.3, 0.6, 0), o = V3(0.75, 0.35, 0), ho = o + V3(0.3, 0.15, 0.1)
            m.atom("Karbon", C_COLOR, c1, 0.16); m.atom("Karbon", C_COLOR, c2, 0.16); m.atom("Oksijen", O_COLOR, o, 0.17); m.atom("Hidrojen", H_COLOR, ho, 0.1)
            m.bond(c1, c2); m.bond(c2, o); m.bond(o, ho)
            for p in [V3(-0.55, 0.85, 0.1), V3(-0.55, 0.35, 0.3), V3(-0.45, 0.35, -0.35)] { m.atom("Hidrojen", H_COLOR, p, 0.1); m.bond(c1, p) }
            for p in [V3(0.35, 0.95, 0.15), V3(0.35, 0.7, -0.35)] { m.atom("Hidrojen", H_COLOR, p, 0.1); m.bond(c2, p) }
        },
        molecule("chem.nacl", "NaCl kristal örgüsü", "Yüzey merkezli kübik örgü: Na⁺ (mor), Cl⁻ (yeşil)") { m in
            let s: Float = 0.42
            for i in 0...2 { for j in 0...2 { for k in 0...2 {
                let p = V3(Float(i - 1) * s, 0.15 + Float(j) * s, Float(k - 1) * s)
                if (i + j + k) % 2 == 0 { m.atom("Klor (Cl⁻)", 0xFF66_BB6A, p, 0.15) } else { m.atom("Sodyum (Na⁺)", 0xFF95_75CD, p, 0.1) }
                if i < 2 { m.bond(p, p + V3(s, 0, 0), 0.015) }
                if j < 2 { m.bond(p, p + V3(0, s, 0), 0.015) }
                if k < 2 { m.bond(p, p + V3(0, 0, s), 0.015) }
            } } }
        },
        item("chem.beaker", "Beher", CHEMISTRY, "Ölçekli cam kap ve sıvı") { b in
            b.prim(.TUBE, "Beher", V3(0, 0.45, 0), V3(0.7, 0.9, 0.7), GLASS, kindM: .GLASS, opacity: 0.45, spec: PrimitiveSpec(.TUBE, 24, 1, thickness: 0.05))
            b.prim(.CYLINDER, "Taban", V3(0, 0.01, 0), V3(0.7, 0.02, 0.7), GLASS, kindM: .GLASS, opacity: 0.45)
            b.prim(.CYLINDER, "Sıvı", V3(0, 0.27, 0), V3(0.66, 0.5, 0.66), 0xFF4F_C3F7, kindM: .GLASS, opacity: 0.75)
            for k in 1...4 { b.line([V3(0.36, Float(k) * 0.18, 0), V3(0.36, Float(k) * 0.18, 0.12)], INK, 0.008) }
        },
        item("chem.flask", "Erlenmeyer", CHEMISTRY, "Konik cam şişe ve sıvı") { b in
            let prof: [(Float, Float)] = [(0.4, 0), (0.4, 0.05), (0.12, 0.75), (0.12, 1.0), (0.14, 1.03)]
            b.mesh("Erlenmeyer", G.revolve(prof, 24, capBottom: true, capTop: false), GLASS, kind: .GLASS, smooth: true, opacity: 0.45)
            b.mesh("Sıvı", G.revolve([(0.38, 0.01), (0.38, 0.06), (0.27, 0.3)], 24), 0xFFFF_8A65, kind: .GLASS, smooth: true, opacity: 0.8)
        },
        item("chem.testtube", "Deney tüpü", CHEMISTRY, "Yuvarlak dipli tüp ve sıvı") { b in
            var prof: [(Float, Float)] = []
            for k in 0...6 { let a = Double.pi / 2 * Double(k) / 6; prof.append((Float(0.12 * sin(a)), Float(0.12 - 0.12 * cos(a)))) }
            prof.append((0.12, 1.0))
            b.mesh("Tüp", G.revolve(prof, 18, capBottom: false, capTop: false), GLASS, kind: .GLASS, smooth: true, opacity: 0.45)
            var liquid: [(Float, Float)] = []
            for k in 0...6 { let a = Double.pi / 2 * Double(k) / 6; liquid.append((Float(0.11 * sin(a)), Float(0.125 - 0.11 * cos(a)))) }
            liquid.append((0.11, 0.45))
            b.mesh("Sıvı", G.revolve(liquid, 18, capBottom: false, capTop: true), 0xFF66_BB6A, kind: .GLASS, smooth: true, opacity: 0.8)
        },
    ] }

    private static func biology() -> [CatalogItem] { [
        item("bio.dna", "DNA çift sarmalı", BIOLOGY, "İki şeker-fosfat zinciri ve baz çiftleri (A–T, G–C)") { b in
            var back1: [Mesh] = [], back2: [Mesh] = [], at: [Mesh] = [], gc: [Mesh] = []
            var p1: [V3] = [], p2: [V3] = []
            for i in 0..<20 {
                let t = Float(i) * 0.6, y = 0.05 + Float(i) * 0.085
                let a = V3(0.35 * cf(t), y, 0.35 * sf(t)), c = V3(0.35 * cf(t + PI_F), y, 0.35 * sf(t + PI_F))
                p1.append(a); p2.append(c)
                back1.append(G.sphere(a, 0.05, 8, 4)); back2.append(G.sphere(c, 0.05, 8, 4))
                let mid = (a + c) * 0.5
                if i % 2 == 0 { at.append(G.cylinder(a, c, 0.02, 6)) }
                else { gc.append(Mesh.concat([G.cylinder(a, mid, 0.02, 6), G.cylinder(mid, c, 0.02, 6)])) }
            }
            b.mesh("Zincir 1", Mesh.concat(back1 + [G.tube(p1, 0.02, 6)]), ORANGE, smooth: true)
            b.mesh("Zincir 2", Mesh.concat(back2 + [G.tube(p2, 0.02, 6)]), BLUE, smooth: true)
            b.mesh("A–T bağları", Mesh.concat(at), GREEN, smooth: true)
            b.mesh("G–C bağları", Mesh.concat(gc), RED, smooth: true)
        },
        item("bio.cell", "Hayvan hücresi", BIOLOGY, "Zar, çekirdek, çekirdekçik, mitokondriler ve ribozomlar") { b in
            b.mesh("Hücre zarı", G.sphere(V3(0, 0.7, 0), 0.7, 24, 12), 0xFFF8_BBD0, kind: .GLASS, smooth: true, opacity: 0.35)
            b.mesh("Çekirdek", G.sphere(V3(0.05, 0.72, 0.02), 0.25, 18, 9), PURPLE, smooth: true)
            b.mesh("Çekirdekçik", G.sphere(V3(0.12, 0.8, 0.12), 0.08), 0xFF4A_148C, smooth: true)
            let mito: [(V3, V3)] = [(V3(-0.35, 0.55, 0.2), V3(0, 30, 60)), (V3(0.35, 0.45, -0.25), V3(40, 0, 20)), (V3(-0.2, 1.0, -0.25), V3(0, 70, 80))]
            for (i, pr) in mito.enumerated() { b.prim(.CAPSULE, "Mitokondri \(i + 1)", pr.0, V3(0.18, 0.3, 0.18), ORANGE, rot: pr.1) }
            var ribo: [Mesh] = []
            var rnd = JavaRandom(7)
            for _ in 0..<26 {
                let x = rnd.nextFloat() - 0.5, y = rnd.nextFloat() - 0.5, z = rnd.nextFloat() - 0.5
                let len = 0.35 + rnd.nextFloat() * 0.25
                let d = V3(x, y, z).normalized() * len
                ribo.append(G.sphere(V3(0, 0.7, 0) + d, 0.022, 6, 3))
            }
            b.mesh("Ribozomlar", Mesh.concat(ribo), DARK, smooth: true)
        },
        item("bio.virus", "Virüs", BIOLOGY, "Yirmiyüzlü kapsit ve yüzey proteinleri") { b in
            let c = V3(0, 0.7, 0)
            let ico = G.icosahedron(0.45)
            b.mesh("Kapsit", G.place(ico, c, 1), TEAL)
            var spikes: [Mesh] = [], heads: [Mesh] = []
            for i in 0..<ico.vertexCount {
                let v = ico.vertex(i).normalized()
                spikes.append(G.cylinder(c + v * 0.42, c + v * 0.62, 0.02, 6))
                heads.append(G.sphere(c + v * 0.65, 0.045, 8, 4))
            }
            b.mesh("Proteinler", Mesh.concat(spikes), GRAY, smooth: true)
            b.mesh("Uçlar", Mesh.concat(heads), RED, smooth: true)
        },
        item("bio.rbc", "Alyuvar", BIOLOGY, "İki yanı çukur disk biçiminde kan hücresi") { b in
            var prof: [(Float, Float)] = []
            let rs: [Float] = (0..<9).map { (i: Int) -> Float in 0.5 * Float(i) / 8 }
            func h(_ r: Float) -> Float { let x = r / 0.5; return 0.12 * (1 - x * x).coerceAtLeast(0).squareRoot() * (0.35 + 1.2 * x * x) }
            for r in rs { prof.append((r, -h(r))) }
            for r in rs.reversed().dropFirst() { prof.append((r, h(r))) }
            b.mesh("Alyuvar", G.revolve(prof, 28, capBottom: false, capTop: false).mapPositions { $0 + V3(0, 0.3, 0) }, RED, smooth: true, shine: 0.6)
        },
    ] }

    private static func astronomy() -> [CatalogItem] { [
        item("astro.solar", "Güneş sistemi", ASTRONOMY, "Güneş ve gezegenler (ölçeksiz)", pitch: 30) { b in
            b.mesh("Güneş", G.sphere(V3(0, 0.3, 0), 0.3, 18, 9), 0xFFFF_B300, kind: .GLOW, smooth: true)
            let planets: [(String, Float, UInt32)] = [
                ("Merkür", 0.05, 0xFF9E_9E9E), ("Venüs", 0.07, 0xFFFF_CC80), ("Dünya", 0.075, BLUE),
                ("Mars", 0.06, 0xFFD8_4315), ("Jüpiter", 0.16, 0xFFD7_B899), ("Satürn", 0.13, 0xFFE6_C88C),
                ("Uranüs", 0.1, 0xFF80_DEEA), ("Neptün", 0.1, 0xFF3F_51B5),
            ]
            for (i, p) in planets.enumerated() {
                let d = 0.55 + Float(i) * 0.3
                let a = Float(i) * 0.9
                b.mesh(p.0, G.sphere(V3(d * cf(a), 0.3, d * sf(a)), p.1, 14, 7), p.2, smooth: true)
                b.line(closedCircle(V3(0, 0.3, 0), d, 56), 0xFFB0_BEC5, 0.006)
            }
        },
        item("astro.earth_moon", "Dünya ve Ay", ASTRONOMY, "Dünya'nın 23,5° eğik ekseni ve Ay'ın yörüngesi") { b in
            let e = V3(0, 0.5, 0)
            b.mesh("Dünya", G.sphere(e, 0.4, 20, 10), BLUE, smooth: true, shine: 0.6)
            let tilt = toRadians(23.5)
            let axis = V3(Float(sin(tilt)), Float(cos(tilt)), 0)
            b.line([e - axis * 0.6, e + axis * 0.6], RED, 0.012)
            b.line(closedCircle(e, 1.2, 56), GRAY, 0.008)
            b.mesh("Ay", G.sphere(e + V3(1.2, 0, 0), 0.11), 0xFFBD_BDBD, kind: .MATTE, smooth: true)
        },
        item("astro.saturn", "Satürn", ASTRONOMY, "Gezegen ve halkaları") { b in
            let c = V3(0, 0.6, 0)
            b.mesh("Satürn", G.sphere(c, 0.4, 20, 10), 0xFFE6_C88C, smooth: true)
            b.prim(.TUBE, "Halkalar", c, V3(1.5, 0.01, 1.5), 0xFFD7_CCC8, rot: V3(20, 0, 0), opacity: 0.85, spec: PrimitiveSpec(.TUBE, 40, 1, thickness: 0.35))
        },
        item("astro.phases", "Ay'ın evreleri", ASTRONOMY, "Güneş ışığına göre Ay'ın dört konumu", pitch: 40) { b in
            b.mesh("Dünya", G.sphere(V3(0, 0.25, 0), 0.22), BLUE, smooth: true)
            for k in 0..<4 {
                let a = Double.pi / 2 * Double(k)
                b.mesh("Ay \(k + 1)", G.sphere(V3(Float(0.9 * cos(a)), 0.25, Float(0.9 * sin(a))), 0.09), 0xFFBD_BDBD, kind: .MATTE, smooth: true)
            }
            b.line(closedCircle(V3(0, 0.25, 0), 0.9, 48), GRAY, 0.006)
            for z: Float in [-0.6, 0, 0.6] { b.mesh("Güneş ışığı", G.arrow(V3(-1.8, 0.25, z), V3(-1.25, 0.25, z), 0.015), YELLOW, kind: .GLOW, smooth: true) }
        },
    ] }

    private static func drawing() -> [CatalogItem] { [
        item("draw.study", "Işık-gölge çalışması", DRAWING, "Beyaz geometrik cisimler: küp, küre, silindir, koni") { b in
            b.prim(.PLANE, "Zemin", V3(0, 0, 0), V3(2.4, 1, 1.6), PAPER, kindM: .MATTE)
            b.prim(.CUBE, "Küp", V3(-0.7, 0.25, -0.2), V3(0.5, 0.5, 0.5), WHITE, rot: V3(0, 25, 0), kindM: .MATTE)
            b.prim(.SPHERE, "Küre", V3(0.05, 0.22, 0.3), V3(0.44, 0.44, 0.44), WHITE, kindM: .MATTE)
            b.prim(.CYLINDER, "Silindir", V3(0.25, 0.35, -0.35), V3(0.36, 0.7, 0.36), WHITE, kindM: .MATTE)
            b.prim(.CONE, "Koni", V3(0.85, 0.3, 0.15), V3(0.42, 0.6, 0.42), WHITE, kindM: .MATTE)
        },
        item("draw.mannequin", "Çizim mankeni", DRAWING, "Eklemleri belirgin ahşap insan figürü", yaw: 25, pitch: 10) { b in
            let wood: UInt32 = 0xFFE8_C9A0
            func limb(_ name: String, _ a: V3, _ c: V3, _ r: Float) { b.mesh(name, G.cylinder(a, c, r, 10, rTop: r * 0.85), wood, smooth: true, texture: "wood.pine", texScale: 0.3) }
            func joint(_ name: String, _ p: V3, _ r: Float) { b.mesh(name, G.sphere(p, r, 10, 5), 0xFFD7_B48A, smooth: true) }
            b.mesh("Baş", G.place(G.sphere(.ZERO, 0.5, 14, 7), V3(0, 1.62, 0), V3(0.2, 0.26, 0.22)), wood, smooth: true)
            limb("Boyun", V3(0, 1.42, 0), V3(0, 1.52, 0), 0.04)
            b.mesh("Göğüs", G.place(G.sphere(.ZERO, 0.5, 14, 7), V3(0, 1.25, 0), V3(0.36, 0.34, 0.22)), wood, smooth: true)
            b.mesh("Kalça", G.place(G.sphere(.ZERO, 0.5, 14, 7), V3(0, 0.92, 0), V3(0.3, 0.2, 0.2)), wood, smooth: true)
            limb("Bel", V3(0, 0.98, 0), V3(0, 1.12, 0), 0.06)
            for s: Float in [-1, 1] {
                let side = s < 0 ? "sol" : "sağ"
                let sh = V3(0.21 * s, 1.36, 0), el = V3(0.3 * s, 1.08, 0.04), wr = V3(0.34 * s, 0.84, 0.1)
                joint("Omuz \(side)", sh, 0.055); limb("Üst kol \(side)", sh, el, 0.04); joint("Dirsek \(side)", el, 0.04); limb("Ön kol \(side)", el, wr, 0.033)
                b.mesh("El \(side)", G.place(G.sphere(.ZERO, 0.5, 10, 5), wr + V3(0, -0.06, 0), V3(0.06, 0.11, 0.04)), wood, smooth: true)
                let hp = V3(0.1 * s, 0.86, 0), kn = V3(0.11 * s, 0.48, s < 0 ? 0.04 : -0.02), an = V3(0.11 * s, 0.08, 0)
                joint("Kalça eklemi \(side)", hp, 0.06); limb("Uyluk \(side)", hp, kn, 0.055); joint("Diz \(side)", kn, 0.045); limb("Baldır \(side)", kn, an, 0.042)
                b.mesh("Ayak \(side)", G.box(V3(0.11 * s - 0.045, 0, -0.04), V3(0.11 * s + 0.045, 0.06, 0.16)), wood, texture: "wood.pine", texScale: 0.3)
            }
        },
        item("draw.chair", "Sandalye", DRAWING, "Ahşap sandalye (perspektif çalışması)") { b in
            b.prim(.CUBE, "Oturak", V3(0, 0.45, 0), V3(0.5, 0.05, 0.5), WHITE, texture: "wood.oak", texScale: 0.5)
            for (x, z) in [(Float(-0.22), Float(-0.22)), (0.22, -0.22), (-0.22, 0.22), (0.22, 0.22)] {
                b.prim(.CUBE, "Ayak", V3(x, 0.21, z), V3(0.05, 0.42, 0.05), WHITE, texture: "wood.walnut", texScale: 0.4)
            }
            for x: Float in [-0.22, 0.22] { b.prim(.CUBE, "Arka direk", V3(x, 0.75, -0.22), V3(0.05, 0.56, 0.05), WHITE, texture: "wood.walnut", texScale: 0.4) }
            b.prim(.CUBE, "Arkalık", V3(0, 0.9, -0.22), V3(0.44, 0.18, 0.04), WHITE, texture: "wood.oak", texScale: 0.5)
        },
        item("draw.books", "Kitap yığını", DRAWING, "Üst üste dört kitap") { b in
            var y: Float = 0
            for (i, c) in [RED, BLUE, GREEN, ORANGE].enumerated() {
                let fi = Float(i)
                let h: Float = 0.08 + Float(i % 2) * 0.03
                let rot = V3(0, -12 + fi * 9, 0)
                b.prim(.CUBE, "Kitap \(i + 1)", V3(0.02 * fi, y + h / 2, -0.02 * fi), V3(0.7 - fi * 0.05, h, 0.5 - fi * 0.03), c, rot: rot, kindM: .MATTE)
                b.prim(.CUBE, "Sayfalar \(i + 1)", V3(0.02 * fi + 0.01, y + h / 2, -0.02 * fi), V3(0.66 - fi * 0.05, h * 0.8, 0.49 - fi * 0.03), PAPER, rot: rot, kindM: .MATTE)
                y += h
            }
        },
        item("draw.vase", "Vazo", DRAWING, "Döner profilli seramik vazo") { b in
            let prof: [(Float, Float)] = [(0.18, 0), (0.26, 0.1), (0.3, 0.3), (0.24, 0.6), (0.12, 0.85), (0.11, 0.95), (0.16, 1.05)]
            b.mesh("Vazo", G.revolve(prof, 24, capBottom: true, capTop: false), 0xFF5C_6BC0, smooth: true, shine: 0.7)
        },
        item("draw.bottle", "Şişe", DRAWING, "Cam şişe") { b in
            let prof: [(Float, Float)] = [(0.2, 0), (0.2, 0.6), (0.17, 0.72), (0.07, 0.85), (0.07, 1.05), (0.08, 1.08), (0.08, 1.12)]
            b.mesh("Şişe", G.revolve(prof, 20), 0xFF2E_7D32, kind: .GLASS, smooth: true, opacity: 0.7)
        },
        item("draw.glass", "Su bardağı", DRAWING, "Yarısı dolu cam bardak") { b in
            b.mesh("Bardak", G.revolve([(0.18, 0), (0.22, 0.55)], 22, capBottom: true, capTop: false), GLASS, kind: .GLASS, smooth: true, opacity: 0.4)
            b.mesh("Su", G.revolve([(0.17, 0.01), (0.195, 0.3)], 22), 0xFF4F_C3F7, kind: .GLASS, smooth: true, opacity: 0.7)
        },
        item("draw.apple", "Elma", DRAWING, "Sapı ve yaprağıyla elma") { b in
            var prof: [(Float, Float)] = []
            for k in 0...10 {
                let t = Double.pi * Double(k) / 10
                let st: Double = sin(t)
                let r = Float(0.32 * st * (1.0 + 0.12 * st))
                let dy: Float = k == 10 ? -0.05 : 0
                let y: Float = Float(0.3 - 0.29 * cos(t)) + dy
                prof.append((k == 0 || k == 10 ? 0 : r, y))
            }
            b.mesh("Elma", G.revolve(prof, 20, capBottom: false, capTop: false), RED, smooth: true, shine: 0.7)
            b.mesh("Sap", G.cylinder(V3(0, 0.52, 0), V3(0.03, 0.68, 0), 0.015, 6), BROWN, smooth: true)
            b.mesh("Yaprak", G.place(G.sphere(.ZERO, 0.5, 10, 5), V3(0.1, 0.64, 0), V3(0.16, 0.02, 0.07), V3(0, 0, 25)), GREEN, smooth: true)
        },
        item("draw.pear", "Armut", DRAWING, "Armut biçimli meyve") { b in
            let prof: [(Float, Float)] = [(0, 0), (0.18, 0.04), (0.26, 0.15), (0.25, 0.3), (0.17, 0.45), (0.11, 0.58), (0.08, 0.68), (0, 0.72)]
            b.mesh("Armut", G.revolve(prof, 20, capBottom: false, capTop: false), 0xFFC0_CA33, smooth: true, shine: 0.5)
            b.mesh("Sap", G.cylinder(V3(0, 0.7, 0), V3(0.02, 0.82, 0), 0.012, 6), BROWN, smooth: true)
        },
        item("draw.stairs", "Merdiven", DRAWING, "Beş basamaklı merdiven") { b in
            for i in 0..<5 {
                b.prim(.CUBE, "Basamak \(i + 1)", V3(0, 0.1 + Float(i) * 0.2, Float(-i) * 0.25), V3(0.9, 0.2 + Float(i) * 0, 0.25), WHITE,
                       kindM: .MATTE, texture: "stone.concrete", texScale: 0.5)
            }
            for i in 1..<5 {
                b.prim(.CUBE, "Dolgu \(i)", V3(0, Float(i) * 0.1, Float(-i) * 0.25), V3(0.9, Float(i) * 0.2, 0.25), WHITE,
                       kindM: .MATTE, texture: "stone.concrete", texScale: 0.5)
            }
        },
        item("draw.car", "Araba", DRAWING, "Basit hatlı otomobil") { b in
            b.prim(.CUBE, "Gövde", V3(0, 0.3, 0), V3(1.6, 0.3, 0.75), RED, shine: 0.8)
            b.prim(.CUBE, "Kabin", V3(-0.1, 0.57, 0), V3(0.85, 0.26, 0.68), RED, shine: 0.8, spec: PrimitiveSpec(.CUBE, 4, 1))
            b.prim(.CUBE, "Ön cam", V3(0.32, 0.57, 0), V3(0.02, 0.22, 0.6), GLASS, kindM: .GLASS, opacity: 0.7)
            for (x, z) in [(Float(-0.5), Float(0.38)), (0.5, 0.38), (-0.5, -0.38), (0.5, -0.38)] {
                b.prim(.CYLINDER, "Teker", V3(x, 0.16, z), V3(0.32, 0.12, 0.32), 0xFF21_2121, rot: V3(90, 0, 0), kindM: .MATTE)
            }
            for z: Float in [-0.25, 0.25] { b.mesh("Far", G.sphere(V3(0.8, 0.33, z), 0.06), YELLOW, kind: .GLOW, smooth: true) }
        },
        item("draw.lamp", "Masa lambası", DRAWING, "Eklemli masa lambası") { b in
            b.prim(.CYLINDER, "Taban", V3(0, 0.03, 0), V3(0.4, 0.06, 0.4), DARK, kindM: .METAL)
            let j1 = V3(0, 0.06, 0), j2 = V3(0.15, 0.55, 0), j3 = V3(0.55, 0.75, 0)
            b.mesh("Alt kol", G.cylinder(j1, j2, 0.025, 8), GRAY, kind: .METAL, smooth: true)
            b.mesh("Üst kol", G.cylinder(j2, j3, 0.022, 8), GRAY, kind: .METAL, smooth: true)
            b.mesh("Eklem", G.sphere(j2, 0.04), DARK, smooth: true)
            b.mesh("Şapka", G.cylinder(j3 + V3(0.02, 0.05, 0), j3 + V3(0.12, -0.2, 0), 0.05, 16, rTop: 0.16), 0xFF00_897B, smooth: true)
            b.mesh("Ampul", G.sphere(j3 + V3(0.11, -0.17, 0), 0.06), 0xFFFF_F59D, kind: .GLOW, smooth: true)
        },
        item("draw.house", "Çatılı ev", DRAWING, "Beşik çatılı ev (iki nokta perspektifi)") { b in
            b.prim(.CUBE, "Duvarlar", V3(0, 0.4, 0), V3(1.2, 0.8, 0.9), 0xFFFF_F3E0, kindM: .MATTE)
            // Üçgen prizma yatırılır: ekseni X boyunca, sivri ucu yukarıda (sırt), tabanı duvarların üstünde
            let roofY: Float = 0.8 + 0.25 * 0.67
            b.prim(.PRISM3, "Çatı", V3(0, roofY, 0), V3(1.15, 1.32, 0.67), 0xFFB2_3A2E, rot: V3(-90, -90, 0), kindM: .MATTE)
            b.prim(.CUBE, "Kapı", V3(0.2, 0.22, 0.455), V3(0.24, 0.44, 0.02), BROWN, texture: "wood.planks", texScale: 0.3)
            b.prim(.CUBE, "Pencere", V3(-0.3, 0.5, 0.455), V3(0.26, 0.22, 0.02), GLASS, kindM: .GLASS, opacity: 0.7)
        },
        item("draw.tree", "Yuvarlak ağaç", DRAWING, "Gövde ve küresel yaprak kümeleri") { b in
            b.prim(.CYLINDER, "Gövde", V3(0, 0.35, 0), V3(0.16, 0.7, 0.16), WHITE, kindM: .MATTE, texture: "nature.bark", texScale: 0.3,
                   spec: PrimitiveSpec(.CYLINDER, 12, 1, top: 0.7))
            let leaves: [(V3, Float)] = [(V3(0, 0.95, 0), 0.38), (V3(0.25, 0.85, 0.1), 0.26), (V3(-0.22, 0.82, -0.1), 0.27), (V3(0.05, 1.2, -0.05), 0.26)]
            b.mesh("Yapraklar", Mesh.concat(leaves.map { G.sphere($0.0, $0.1, 12, 6) }), 0xFF43_A047, kind: .MATTE, smooth: true)
        },
    ] }
}
