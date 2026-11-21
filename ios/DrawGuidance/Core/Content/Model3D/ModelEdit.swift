import Foundation

/// v23 3B düzenleme işlemleri (Android `ModelEdit`; saf işlevler).
enum ModelEdit {

    static let palette: [Int32] = [0xFF4F8BD6, 0xFFE07A5F, 0xFF81B29A, 0xFFF2CC8F, 0xFF9C6ADE, 0xFF3D405B, 0xFFE5989B, 0xFF6EC1E4]
        .map { Int32(bitPattern: UInt32($0)) }

    static func primitiveName(_ kind: PrimitiveKind) -> String {
        switch kind {
        case .CUBE: return "Küp"
        case .SPHERE: return "Küre"
        case .CYLINDER: return "Silindir"
        case .CONE: return "Koni"
        case .PYRAMID: return "Piramit"
        case .PRISM3: return "Üçgen prizma"
        case .PRISM6: return "Altıgen prizma"
        case .TORUS: return "Simit"
        case .PLANE: return "Düzlem"
        case .DOME: return "Kubbe"
        case .CAPSULE: return "Hap"
        case .TUBE: return "Boru"
        case .WEDGE: return "Rampa"
        case .STAR: return "Yıldız"
        }
    }

    static func newPart(_ kind: PrimitiveKind, _ id: String, _ index: Int, _ at: V3 = .ZERO) -> Part {
        let spec = Primitives.defaultSpec(kind)
        let mesh = Primitives.build(spec)
        let b = mesh.bounds
        return Part(id, "\(primitiveName(kind)) \(index + 1)", mesh, spec, position: V3(at.x, at.y - b.min.y, at.z),
                    material: Material(color: palette[index % palette.count]), smooth: kind.smoothByDefault, uv: kind.uvByDefault)
    }

    static func setPrimitive(_ part: Part, _ spec: PrimitiveSpec) -> Part {
        if part.primitive == nil { return part }
        var p = part
        p.primitive = spec; p.mesh = Primitives.build(spec); p.faceMaterials = [:]
        return p
    }

    static func worldBounds(_ part: Part) -> Box3 {
        let t = Transform3.of(part)
        var out = [Float](repeating: 0, count: 3)
        var ax = Float.infinity, ay = Float.infinity, az = Float.infinity
        var bx = -Float.infinity, by = -Float.infinity, bz = -Float.infinity
        let p = part.mesh.pos
        for i in 0..<part.mesh.vertexCount {
            t.apply(p[3 * i], p[3 * i + 1], p[3 * i + 2], &out, 0)
            ax = min(ax, out[0]); ay = min(ay, out[1]); az = min(az, out[2])
            bx = max(bx, out[0]); by = max(by, out[1]); bz = max(bz, out[2])
        }
        return ax.isFinite ? Box3(V3(ax, ay, az), V3(bx, by, bz)) : Box3(part.position, part.position)
    }

    static func modelBounds(_ model: Model3D) -> Box3? {
        var box: Box3?
        for p in model.parts where p.visible { let b = worldBounds(p); box = box?.union(b) ?? b }
        for s in model.strokes {
            for i in 0..<s.count {
                let v = V3(s.points[3 * i], s.points[3 * i + 1], s.points[3 * i + 2])
                box = box?.union(Box3(v, v)) ?? Box3(v, v)
            }
        }
        return box
    }

    static func dropToGround(_ part: Part) -> Part {
        var p = part
        p.position.y = part.position.y - worldBounds(part).min.y
        return p
    }

    static func duplicate(_ part: Part, _ newId: String) -> Part {
        var p = part
        p.id = newId; p.name = part.name + " (kopya)"; p.position = part.position + V3(0.35, 0, 0.35)
        return p
    }

    static func worldToObjectDelta(_ part: Part, _ d: V3) -> V3 {
        let r = Transform3.inverseRotate(part.rotation, d)
        func div(_ a: Float, _ s: Float) -> Float { abs(s) < 1e-6 ? 0 : a / s }
        return V3(div(r.x, part.scale.x), div(r.y, part.scale.y), div(r.z, part.scale.z))
    }

    static func merge(_ parts: [Part], _ newId: String, _ name: String) -> Part? {
        guard let first = parts.first else { return nil }
        var meshes: [Mesh] = []
        var mats: [Int: Material] = [:]
        let base = first.material
        var offset = 0
        for p in parts {
            let t = Transform3.of(p)
            meshes.append(p.mesh.mapPositions { t.apply($0) })
            for f in 0..<p.mesh.faceCount { let m = p.materialOf(f); if m != base { mats[offset + f] = m } }
            offset += p.mesh.faceCount
        }
        let world = Mesh.concat(meshes)
        let b = world.bounds
        let origin = V3(b.center.x, b.min.y, b.center.z)
        return Part(newId, name, world.mapPositions { $0 - origin }, nil, position: origin, material: base, faceMaterials: mats,
                    smooth: parts.allSatisfy { $0.smooth }, uv: first.uv)
    }

    static func constrain(_ d: V3, _ axis: Int) -> V3 {
        switch axis {
        case 0: return V3(d.x, 0, 0)
        case 1: return V3(0, d.y, 0)
        case 2: return V3(0, 0, d.z)
        default: return d
        }
    }

    static func dragDelta(_ view: View3, _ dxPx: Float, _ dyPx: Float, _ axis: Int, _ atDepth: Float = 0) -> V3 {
        let free = view.screenDelta(dxPx, dyPx, atDepth)
        if !(0...2).contains(axis) { return free }
        let a = axis == 0 ? V3(1, 0, 0) : axis == 1 ? V3(0, 1, 0) : V3(0, 0, 1)
        let sx = a.dot(view.right), sy = a.dot(view.down)
        let len2 = sx * sx + sy * sy
        if len2 < 0.02 { return constrain(free, axis) }
        let ps = view.persp(atDepth)
        let s: Float = ps > 0 ? ps : 1
        let px = (dxPx * sx + dyPx * sy) / len2.squareRoot()
        return a * (px / (view.scale * s * len2.squareRoot()))
    }
}
