import Foundation

/// 3B düzenleyicide çizim düzlemi.
enum DrawPlane: Int, CaseIterable { case FRONT, SIDE, TOP, VIEW, FACE }

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

    static func moveVertices<C: Collection>(_ part: Part, _ vertices: C, _ worldDelta: V3) -> Part where C.Element == Int {
        var p = part
        p.mesh = part.mesh.moveVertices(vertices, worldToObjectDelta(part, worldDelta)); p.primitive = nil
        return p
    }

    static func extrudeFace(_ part: Part, _ face: Int, _ distance: Float) -> Part {
        if face < 0 || face >= part.mesh.faceCount { return part }
        let n = part.mesh.faceNormal(face)
        let k = Transform3.of(.ZERO, part.rotation, part.scale).applyDir(n).length.coerceAtLeast(1e-6)
        let before = part.mesh.faceCount
        let mesh = part.mesh.extrudeFace(face, distance / k)
        var p = part
        p.mesh = mesh; p.primitive = nil; p.faceMaterials = inheritSides(part, face, before, mesh.faceCount)
        return p
    }

    static func insetFace(_ part: Part, _ face: Int, _ amount: Float) -> Part {
        if face < 0 || face >= part.mesh.faceCount { return part }
        let before = part.mesh.faceCount
        let mesh = part.mesh.insetFace(face, amount)
        var p = part
        p.mesh = mesh; p.primitive = nil; p.faceMaterials = inheritSides(part, face, before, mesh.faceCount)
        return p
    }

    static func scaleFace(_ part: Part, _ face: Int, _ factor: Float) -> Part {
        if face < 0 || face >= part.mesh.faceCount { return part }
        var p = part
        p.mesh = part.mesh.scaleFace(face, factor); p.primitive = nil
        return p
    }

    static func moveFace(_ part: Part, _ face: Int, _ worldDelta: V3) -> Part {
        if face < 0 || face >= part.mesh.faceCount { return part }
        return moveVertices(part, part.mesh.faces[face], worldDelta)
    }

    static func deleteFace(_ part: Part, _ face: Int) -> Part {
        if face < 0 || face >= part.mesh.faceCount || part.mesh.faceCount <= 1 { return part }
        var remapped: [Int: Material] = [:]
        for (f, m) in part.faceMaterials { if f < face { remapped[f] = m } else if f > face { remapped[f - 1] = m } }
        var p = part
        p.mesh = part.mesh.deleteFace(face); p.primitive = nil; p.faceMaterials = remapped
        return p
    }

    private static func inheritSides(_ part: Part, _ face: Int, _ before: Int, _ after: Int) -> [Int: Material] {
        guard let own = part.faceMaterials[face] else { return part.faceMaterials }
        var out = part.faceMaterials
        for f in before..<after { out[f] = own }
        return out
    }

    static func paintFaces<C: Collection>(_ part: Part, _ faces: C, _ material: Material?) -> Part where C.Element == Int {
        var out = part.faceMaterials
        let clear = material == nil || material == part.material
        for f in faces where f >= 0 && f < part.mesh.faceCount { if clear { out[f] = nil } else { out[f] = material! } }
        var p = part
        p.faceMaterials = out
        return p
    }

    static func paintFace(_ part: Part, _ face: Int, _ material: Material?) -> Part {
        var out = part.faceMaterials
        if material == nil || material == part.material { out[face] = nil } else { out[face] = material! }
        var p = part
        p.faceMaterials = out
        return p
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

    // MARK: düzlemde çizim

    static func plane(_ kind: DrawPlane, _ view: View3, _ origin: V3, _ faceNormal: V3? = nil) -> (V3, V3) {
        switch kind {
        case .FRONT: return (origin, V3(0, 0, 1))
        case .SIDE: return (origin, V3(1, 0, 0))
        case .TOP: return (origin, V3(0, 1, 0))
        case .VIEW: return (origin, view.toViewer)
        case .FACE: return (origin, faceNormal ?? view.toViewer)
        }
    }

    static func hitPlane(_ view: View3, _ sx: Float, _ sy: Float, _ point: V3, _ normal: V3) -> V3? {
        let (o, d) = view.ray(sx, sy)
        let dn = d.dot(normal)
        if abs(dn) < 0.08 { return nil }
        let t = (point - o).dot(normal) / dn
        if view.perspective && t <= 0 { return nil }
        return o + d * t
    }

    static func faceFrame(_ part: Part, _ face: Int) -> (V3, V3)? {
        if face < 0 || face >= part.mesh.faceCount { return nil }
        let t = Transform3.of(part)
        let c = t.apply(part.mesh.faceCenter(face))
        var out = [Float](repeating: 0, count: 3)
        let n = part.mesh.faceNormal(face)
        t.normal(n.x, n.y, n.z, &out, 0)
        let nn = V3(out[0], out[1], out[2])
        return (c + nn * 0.004, nn)
    }

    // MARK: seçim

    private static func visibleIn(_ frame: Frame3, _ s: [Float], _ partIndex: Int) -> Bool {
        let px = Int(s[0]), py = Int(s[1])
        guard s[0].isFinite, s[1].isFinite, px >= 0, px < frame.width, py >= 0, py < frame.height else { return false }
        let k = frame.index(px, py)
        return frame.depth[k] <= s[2] + 0.02 || Int(frame.part[k]) == partIndex + 1
    }

    static func pickVertex(_ part: Part, _ frame: Frame3, _ x: Float, _ y: Float, _ radius: Float, _ partIndex: Int) -> Int {
        let t = Transform3.of(part)
        var w = [Float](repeating: 0, count: 3), s = [Float](repeating: 0, count: 4)
        var best = -1, bestD = radius * radius
        var hiddenBest = -1, hiddenD = radius * radius
        let p = part.mesh.pos
        for i in 0..<part.mesh.vertexCount {
            t.apply(p[3 * i], p[3 * i + 1], p[3 * i + 2], &w, 0)
            frame.view.project(w[0], w[1], w[2], &s)
            if s[3] <= 0 { continue }
            let d = (s[0] - x) * (s[0] - x) + (s[1] - y) * (s[1] - y)
            let visible = visibleIn(frame, s, partIndex)
            if visible && d < bestD { bestD = d; best = i }
            if !visible && d < hiddenD { hiddenD = d; hiddenBest = i }
        }
        return best >= 0 ? best : hiddenBest
    }

    static func screenVertices(_ part: Part, _ frame: Frame3, _ partIndex: Int) -> [Float] {
        let t = Transform3.of(part)
        let n = part.mesh.vertexCount
        var out = [Float](repeating: 0, count: n * 3)
        var w = [Float](repeating: 0, count: 3), s = [Float](repeating: 0, count: 4)
        let p = part.mesh.pos
        for i in 0..<n {
            t.apply(p[3 * i], p[3 * i + 1], p[3 * i + 2], &w, 0)
            frame.view.project(w[0], w[1], w[2], &s)
            out[3 * i] = s[0]; out[3 * i + 1] = s[1]
            out[3 * i + 2] = s[3] > 0 && visibleIn(frame, s, partIndex) ? 1 : 0
        }
        return out
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
