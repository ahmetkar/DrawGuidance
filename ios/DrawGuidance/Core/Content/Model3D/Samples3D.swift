import Foundation

/// v23 hazır örnek 3B modeller (Android `Samples3D`): 3B Şekillerim'de ilk açılışta görünür; düzenlenebilir ve silinebilir.
enum Samples3D {

    static let VERSION = 1

    private final class B {
        let prefix: String
        var parts: [Part] = []
        init(_ prefix: String) { self.prefix = prefix }

        func add(_ kind: PrimitiveKind, _ name: String, _ pos: V3, _ scale: V3, _ color: UInt32,
                 rot: V3 = .ZERO, texture: String? = nil, texScale: Float = 1, kindM: MaterialKind = .PLASTIC,
                 shine: Float = 0.4, opacity: Float = 1, spec: PrimitiveSpec? = nil) {
            let sp = spec ?? Primitives.defaultSpec(kind)
            var p = Part("\(prefix)-\(parts.count)", name, Primitives.build(sp), sp, position: pos, rotation: rot, scale: scale)
            p.material = Material(color: Int32(bitPattern: color), texture: texture, textureScale: texScale, opacity: opacity, kind: kindM, shine: shine)
            p.smooth = kind.smoothByDefault
            p.uv = kind.uvByDefault
            parts.append(p)
        }
    }

    private static func model(_ id: String, _ name: String, _ camera: Camera3 = Camera3(), _ build: (B) -> Void) -> Model3D {
        let b = B(id)
        build(b)
        return Model3D(id, name, b.parts, camera: camera)
    }

    private static func cam(_ yaw: Float, _ pitch: Float, _ ty: Float) -> Camera3 {
        var c = Camera3(); c.yaw = yaw; c.pitch = pitch; c.target = V3(0, ty, 0); return c
    }

    private static let WHITE: UInt32 = 0xFFFF_FFFF

    static let all: [Model3D] = [
        model("sample-table", "Masa") { b in
            b.add(.CUBE, "Tabla", V3(0, 0.78, 0), V3(1.6, 0.08, 0.9), WHITE, texture: "wood.oak", texScale: 0.8)
            for (i, p) in [V3(-0.7, 0.37, -0.35), V3(0.7, 0.37, -0.35), V3(-0.7, 0.37, 0.35), V3(0.7, 0.37, 0.35)].enumerated() {
                b.add(.CYLINDER, "Ayak \(i + 1)", p, V3(0.08, 0.74, 0.08), WHITE, texture: "wood.walnut", texScale: 0.5)
            }
        },
        model("sample-house", "Ev", cam(30, 18, 0.7)) { b in
            b.add(.CUBE, "Duvarlar", V3(0, 0.45, 0), V3(1.3, 0.9, 1), WHITE, texture: "stone.brick", texScale: 0.6, kindM: .MATTE)
            b.add(.PYRAMID, "Çatı", V3(0, 1.2, 0), V3(1.55, 0.6, 1.2), 0xFFB2_3A2E, kindM: .MATTE)
            b.add(.CUBE, "Kapı", V3(0, 0.3, 0.5), V3(0.28, 0.6, 0.04), WHITE, texture: "wood.planks", texScale: 0.4)
            b.add(.CUBE, "Pencere 1", V3(-0.42, 0.55, 0.5), V3(0.26, 0.24, 0.03), 0xFF9F_D3F2, kindM: .GLASS, opacity: 0.7)
            b.add(.CUBE, "Pencere 2", V3(0.42, 0.55, 0.5), V3(0.26, 0.24, 0.03), 0xFF9F_D3F2, kindM: .GLASS, opacity: 0.7)
            b.add(.CUBE, "Baca", V3(0.38, 1.35, -0.15), V3(0.16, 0.4, 0.16), WHITE, texture: "stone.brick", texScale: 0.3, kindM: .MATTE)
        },
        model("sample-snowman", "Kardan adam", cam(20, 12, 0.75)) { b in
            b.add(.SPHERE, "Gövde", V3(0, 0.36, 0), V3(0.72, 0.72, 0.72), 0xFFF7_FAFC, kindM: .MATTE)
            b.add(.SPHERE, "Göğüs", V3(0, 0.9, 0), V3(0.52, 0.52, 0.52), 0xFFF7_FAFC, kindM: .MATTE)
            b.add(.SPHERE, "Baş", V3(0, 1.3, 0), V3(0.36, 0.36, 0.36), 0xFFF7_FAFC, kindM: .MATTE)
            b.add(.CONE, "Burun", V3(0, 1.3, 0.26), V3(0.07, 0.22, 0.07), 0xFFF2_8C28, rot: V3(90, 0, 0))
            b.add(.SPHERE, "Göz 1", V3(-0.07, 1.37, 0.16), V3(0.05, 0.05, 0.05), 0xFF1E_1E1E)
            b.add(.SPHERE, "Göz 2", V3(0.07, 1.37, 0.16), V3(0.05, 0.05, 0.05), 0xFF1E_1E1E)
            b.add(.CYLINDER, "Şapka kenarı", V3(0, 1.47, 0), V3(0.42, 0.03, 0.42), 0xFF22_252B)
            b.add(.CYLINDER, "Şapka", V3(0, 1.62, 0), V3(0.26, 0.28, 0.26), 0xFF22_252B)
            b.add(.TORUS, "Atkı", V3(0, 1.12, 0), V3(0.42, 0.42, 0.42), 0xFFD7_263D, texture: "fabric.knit", texScale: 0.15)
        },
        model("sample-rocket", "Roket", cam(40, 15, 0.9)) { b in
            b.add(.CYLINDER, "Gövde", V3(0, 0.85, 0), V3(0.42, 1.1, 0.42), 0xFFEC_EFF1, kindM: .METAL, shine: 0.7)
            b.add(.CONE, "Burun", V3(0, 1.65, 0), V3(0.42, 0.5, 0.42), 0xFFE5_3935)
            b.add(.TORUS, "Pencere çerçevesi", V3(0, 1.05, 0.21), V3(0.2, 0.2, 0.2), 0xFF90_A4AE, rot: V3(90, 0, 0), kindM: .METAL)
            b.add(.SPHERE, "Pencere", V3(0, 1.05, 0.2), V3(0.13, 0.13, 0.06), 0xFF64_B5F6, kindM: .GLASS, opacity: 0.8)
            for i in 0..<3 {
                let a = 120.0 * Double(i) * (Double.pi / 180.0) // Math.toRadians
                b.add(.WEDGE, "Kanat \(i + 1)", V3(Float(0.27 * sin(a)), 0.36, Float(0.27 * cos(a))),
                      V3(0.04, 0.42, 0.3), 0xFFE5_3935, rot: V3(0, 120 * Float(i), 0))
            }
            b.add(.CONE, "Motor", V3(0, 0.22, 0), V3(0.3, 0.18, 0.3), 0xFF54_6E7A, kindM: .METAL, spec: PrimitiveSpec(.CONE, 32, 1, top: 0.6))
        },
        model("sample-tree", "Ağaç", cam(30, 14, 0.9)) { b in
            b.add(.CYLINDER, "Gövde", V3(0, 0.4, 0), V3(0.22, 0.8, 0.22), WHITE, texture: "nature.bark", texScale: 0.35, kindM: .MATTE,
                  spec: PrimitiveSpec(.CYLINDER, 16, 1, top: 0.75))
            b.add(.CONE, "Yapraklar 1", V3(0, 1.0, 0), V3(1.1, 0.7, 1.1), 0xFF2E_7D32, kindM: .MATTE)
            b.add(.CONE, "Yapraklar 2", V3(0, 1.38, 0), V3(0.85, 0.6, 0.85), 0xFF38_8E3C, kindM: .MATTE)
            b.add(.CONE, "Yapraklar 3", V3(0, 1.72, 0), V3(0.6, 0.5, 0.6), 0xFF43_A047, kindM: .MATTE)
        },
        model("sample-mug", "Kupa", cam(25, 22, 0.35)) { b in
            b.add(.TUBE, "Gövde", V3(0, 0.35, 0), V3(0.6, 0.7, 0.6), 0xFF1E_88E5, shine: 0.8, spec: PrimitiveSpec(.TUBE, 40, 1, thickness: 0.12))
            b.add(.CYLINDER, "Taban", V3(0, 0.04, 0), V3(0.6, 0.08, 0.6), 0xFF1E_88E5, shine: 0.8)
            b.add(.TORUS, "Kulp", V3(0.36, 0.38, 0), V3(0.34, 0.34, 0.34), 0xFF1E_88E5, rot: V3(90, 0, 0), shine: 0.8,
                  spec: PrimitiveSpec(.TORUS, 24, 12, thickness: 0.35))
        },
    ]
}
