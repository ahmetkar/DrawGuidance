import Foundation

/// v25 parça düzeyinde katı cisim ve yapı işlemleri (Android `Solid3`).
enum Solid3 {

    enum Op { case UNION, SUBTRACT, INTERSECT }

    private final class Mats {
        var list: [Material]
        init(_ base: Material) { list = [base] }
        func tag(_ m: Material) -> Int {
            if let i = list.firstIndex(of: m) { return i }
            list.append(m); return list.count - 1
        }
    }

    private static func prepared(_ part: Part, _ budget: Int) -> Mesh {
        budget > 0 && part.mesh.triangleCount > budget ? simplified(part.mesh, budget) : part.mesh
    }

    static func simplified(_ m: Mesh, _ target: Int) -> Mesh {
        var tris: [Int] = []
        tris.reserveCapacity(m.triangleCount * 3)
        let tr = m.triangles
        for f in 0..<m.faceCount { let face = m.faces[f]; for c in tr[f] { tris.append(face[c]) } }
        let r = MeshSimplifier.simplify(TriMesh(pos: m.pos, tris: tris, uv: nil), target)
        let faces: [[Int]] = (0..<(r.tris.count / 3)).map { (t: Int) -> [Int] in [r.tris[3 * t], r.tris[3 * t + 1], r.tris[3 * t + 2]] }
        return Mesh(r.pos, faces)
    }

    private static func polys(_ part: Part, _ mats: Mats, _ budget: Int) -> [Csg.CsgPoly] {
        let mesh = prepared(part, budget)
        let sameFaces = mesh === part.mesh
        return Csg.polygons(mesh, Transform3.of(part)) { f in mats.tag(sameFaces ? part.materialOf(f) : part.material) }
    }

    static func boolean(_ parts: [Part], _ op: Op, _ newId: String, _ name: String, _ budget: Int = 0) -> Part? {
        if parts.count < 2 { return nil }
        let base = parts[0]
        let mats = Mats(base.material)
        let each = budget > 0 ? budget / parts.count : 0
        var acc = polys(base, mats, each)
        for p in parts.dropFirst() {
            let b = polys(p, mats, each)
            switch op {
            case .UNION: acc = Csg.union(acc, b)
            case .SUBTRACT: acc = Csg.subtract(acc, b)
            case .INTERSECT: acc = Csg.intersect(acc, b)
            }
        }
        return worldPart(acc, mats, base, newId, name)
    }

    private static func worldPart(_ polys: [Csg.CsgPoly], _ mats: Mats, _ base: Part, _ id: String, _ name: String) -> Part? {
        var tags: [Int] = []
        let world = Csg.toMesh(polys, &tags)
        if world.faceCount == 0 { return nil }
        let b = world.bounds
        let origin = V3(b.center.x, b.min.y, b.center.z)
        return Part(id, name, world.mapPositions { $0 - origin }, nil, position: origin, material: mats.list[0],
                    faceMaterials: faceMats(tags, mats), smooth: base.smooth, uv: base.uv == .MESH ? .BOX : base.uv)
    }

    private static func faceMats(_ tags: [Int], _ mats: Mats) -> [Int: Material] {
        var out: [Int: Material] = [:]
        for (f, t) in tags.enumerated() where t != 0 { out[f] = mats.list[t] }
        return out
    }

    private static func localPart(_ part: Part, _ polys: [Csg.CsgPoly], _ mats: Mats) -> Part? {
        var tags: [Int] = []
        let world = Csg.toMesh(polys, &tags)
        if world.faceCount == 0 { return nil }
        guard let inv = Inverse3.of(Transform3.of(part)) else { return nil }
        var p = part
        p.mesh = world.mapPositions { inv.point($0) }; p.primitive = nil; p.material = mats.list[0]
        p.faceMaterials = faceMats(tags, mats); p.uv = part.uv == .MESH ? .BOX : part.uv
        return p
    }

    static func hole(_ part: Part, _ center: V3, _ axis: V3, _ radius: Float, _ segments: Int = 24, _ budget: Int = 0) -> Part? {
        let mats = Mats(part.material)
        let a = polys(part, mats, budget)
        let ext = ModelEdit.worldBounds(part).radius * 2.2 + 0.1
        let n = axis.normalized()
        let cutter = Generators3D.cylinder(center + n * (ext * 0.5), center - n * ext, radius, segments)
        let tag = mats.tag(part.materialOf(0))
        let cut = Csg.polygons(cutter, nil) { _ in tag }
        return localPart(part, Csg.subtract(a, cut), mats)
    }

    static func slice(_ part: Part, _ point: V3, _ normal: V3, _ budget: Int = 0) -> (Part, Part)? {
        let mats = Mats(part.material)
        let a = polys(part, mats, budget)
        let n = normal.normalized()
        let r = ModelEdit.worldBounds(part).radius * 4 + 1
        let h0 = abs(n.y) < 0.9 ? V3.UP : V3(1, 0, 0)
        let t1 = (h0 - n * h0.dot(n)).normalized()
        let t2 = t1.cross(n).normalized()
        func half(_ sign: Float) -> [Csg.CsgPoly] {
            var box = Primitives.cube().mapPositions { c in
                point + t1 * (c.x * 2 * r) + n * (sign * (c.y + 0.5) * r) + t2 * (c.z * 2 * r)
            }
            if sign < 0 { box = Mesh(box.pos, box.faces.map { $0.reversed() }) }
            let tag = mats.tag(part.material)
            return Csg.polygons(box, nil) { _ in tag }
        }
        guard let front = localPart(part, Csg.intersect(a, half(1)), mats),
              let back = localPart(part, Csg.intersect(a, half(-1)), mats) else { return nil }
        return (front, back)
    }

    static func separate(_ part: Part, _ newId: () -> String) -> [Part] {
        let comps = MeshTopo.components(part.mesh)
        if comps.count < 2 { return [] }
        let sorted = comps.enumerated().sorted { $0.element.count != $1.element.count ? $0.element.count > $1.element.count : $0.offset < $1.offset }.map(\.element)
        return sorted.enumerated().map { i, faces in
            let sub = MeshTopo.subMesh(part.mesh, faces)
            var p = part
            p.id = i == 0 ? part.id : newId(); p.name = i == 0 ? part.name : "\(part.name) (\(i + 1))"
            p.mesh = sub.mesh; p.primitive = nil; p.faceMaterials = remap(part, sub.origin)
            return p
        }
    }

    static func cut(_ part: Part, _ loop: [Int], _ newId: String) -> (Part, Part)? {
        guard let r = MeshTopo.cutLoop(part.mesh, loop) else { return nil }
        var rest = part
        rest.mesh = r.rest.mesh; rest.primitive = nil; rest.faceMaterials = remap(part, r.rest.origin)
        let gap = MeshOps.extent(part.mesh) * 0.16
        var piece = part
        piece.id = newId; piece.name = "\(part.name) (kesik)"
        piece.mesh = r.piece.mesh.mapPositions { $0 + r.away * gap }; piece.primitive = nil
        piece.faceMaterials = remap(part, r.piece.origin)
        return (rest, piece)
    }

    static func remap(_ part: Part, _ origin: [Int]) -> [Int: Material] {
        if part.faceMaterials.isEmpty { return [:] }
        var out: [Int: Material] = [:]
        for (f, o) in origin.enumerated() where o >= 0 { if let m = part.faceMaterials[o] { out[f] = m } }
        return out
    }

    static func apply(_ part: Part, _ r: TopoResult) -> Part {
        var p = part
        p.mesh = r.mesh; p.primitive = nil; p.faceMaterials = remap(part, r.origin)
        return p
    }

    static func estimate(_ parts: [Part]) -> Int { parts.reduce(0) { $0 + $1.mesh.triangleCount } }
}
