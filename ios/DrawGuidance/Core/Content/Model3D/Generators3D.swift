import Foundation

/// v24 hazır şekiller için ağ üreticileri (Android `Generators3D`).
enum Generators3D {

    private static let GOLD = Float((1 + 5.0.squareRoot()) / 2)

    static func place(_ mesh: Mesh, _ center: V3, _ scale: Float) -> Mesh { mesh.mapPositions { center + $0 * scale } }

    static func place(_ mesh: Mesh, _ center: V3, _ scale: V3, _ rotationDeg: V3 = .ZERO) -> Mesh {
        let t = Transform3.of(center, rotationDeg, scale)
        return mesh.mapPositions { t.apply($0) }
    }

    static func box(_ min: V3, _ max: V3) -> Mesh { place(Primitives.cube(), (min + max) * 0.5, max - min) }

    static func sphere(_ center: V3, _ r: Float, _ segments: Int = 14, _ rings: Int = 7) -> Mesh {
        place(Primitives.sphere(segments, rings), center, r * 2)
    }

    static func cylinder(_ a: V3, _ b: V3, _ r: Float, _ segments: Int = 10, rTop: Float? = nil) -> Mesh {
        let d = b - a
        let len = d.length
        if len < 1e-6 { return Mesh([], []) }
        let base = Primitives.revolveFrustum(segments, r, rTop ?? r, 0)
        let n = d * (1 / len)
        let x = frame(n).0
        let z = x.cross(n).normalized()
        return base.mapPositions { p in
            let t = p.y + 0.5
            return a + x * p.x + d * t + z * p.z
        }
    }

    static func cone(_ a: V3, _ b: V3, _ r: Float, _ segments: Int = 12) -> Mesh { cylinder(a, b, r, segments, rTop: 0) }

    private static func frame(_ n: V3) -> (V3, V3) {
        let helper = abs(n.y) < 0.9 ? V3.UP : V3(1, 0, 0)
        let x = helper.cross(n).normalized()
        let z = n.cross(x).normalized()
        return (x, z)
    }

    static func arrow(_ a: V3, _ b: V3, _ r: Float) -> Mesh {
        let d = b - a
        let len = d.length
        let head = (len * 0.22).coerceAtMost(r * 7)
        let neck = a + d * ((len - head) / len)
        return Mesh.concat([cylinder(a, neck, r, 10), cone(neck, b, r * 2.4, 12)])
    }

    static func tube(_ points: [V3], _ r: Float, _ sides: Int = 8, closed: Bool = false) -> Mesh {
        let n = points.count
        if n < 2 { return Mesh([], []) }
        let tangents: [V3] = (0..<n).map { i in
            let prev = i > 0 ? points[i - 1] : closed ? points[n - 1] : points[i]
            let next = i < n - 1 ? points[i + 1] : closed ? points[0] : points[i]
            return (next - prev).normalized()
        }
        var nx = frame(tangents[0]).0
        var pos = [Float](repeating: 0, count: n * sides * 3)
        for i in 0..<n {
            let t = tangents[i]
            let q = (nx - t * nx.dot(t)).normalized()
            nx = q.length < 0.5 ? frame(t).0 : q
            let bz = t.cross(nx).normalized()
            for s in 0..<sides {
                let a = 2.0 * Double.pi * Double(s) / Double(sides)
                let p = points[i] + nx * (r * Float(cos(a))) + bz * (r * Float(sin(a)))
                let k = (i * sides + s) * 3
                pos[k] = p.x; pos[k + 1] = p.y; pos[k + 2] = p.z
            }
        }
        var faces: [[Int]] = []
        let segs = closed ? n : n - 1
        for i in 0..<segs {
            let j = (i + 1) % n
            for s in 0..<sides {
                let s1 = (s + 1) % sides
                faces.append([i * sides + s, i * sides + s1, j * sides + s1, j * sides + s])
            }
        }
        if !closed {
            faces.append((0..<sides).map { sides - 1 - $0 })
            faces.append((0..<sides).map { (n - 1) * sides + $0 })
        }
        return fixOrientation(Mesh(pos, faces))
    }

    static func surface(_ uSeg: Int, _ vSeg: Int, closeU: Bool = false, closeV: Bool = false, _ f: (Float, Float) -> V3) -> Mesh {
        let nu = closeU ? uSeg : uSeg + 1
        let nv = closeV ? vSeg : vSeg + 1
        var pos = [Float](repeating: 0, count: nu * nv * 3)
        for i in 0..<nu { for j in 0..<nv {
            let p = f(Float(i) / Float(uSeg), Float(j) / Float(vSeg))
            let k = (i * nv + j) * 3
            pos[k] = p.x; pos[k + 1] = p.y; pos[k + 2] = p.z
        } }
        var faces: [[Int]] = []
        for i in 0..<uSeg { for j in 0..<vSeg {
            let i1 = closeU ? (i + 1) % nu : i + 1
            let j1 = closeV ? (j + 1) % nv : j + 1
            faces.append([i * nv + j, i1 * nv + j, i1 * nv + j1, i * nv + j1])
        } }
        return Mesh(pos, faces)
    }

    static func revolve(_ profile: [(Float, Float)], _ segments: Int = 20, capBottom: Bool = true, capTop: Bool = true) -> Mesh {
        Primitives.revolve(profile, segments, capBottom: capBottom, capTop: capTop)
    }

    /// Kapalı ağda yüzler dışa bakmıyorsa çevirir.
    static func fixOrientation(_ mesh: Mesh) -> Mesh {
        if !mesh.closed { return mesh }
        var vol = 0.0
        let p = mesh.pos
        let tri = mesh.triangles
        for f in 0..<mesh.faceCount {
            let face = mesh.faces[f], tris = tri[f]
            var k = 0
            while k + 2 < tris.count {
                let a = face[tris[k]] * 3, b = face[tris[k + 1]] * 3, c = face[tris[k + 2]] * 3
                let t1 = p[a] * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1])
                let t2 = p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c])
                let t3 = p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c])
                vol += Double(t1 - t2 + t3)
                k += 3
            }
        }
        return vol >= 0 ? mesh : Mesh(mesh.pos, mesh.faces.map { $0.reversed() })
    }

    private static func polyhedron(_ verts: [V3], _ faces: [[Int]], _ radius: Float) -> Mesh {
        var pos = [Float](repeating: 0, count: verts.count * 3)
        for (i, v) in verts.enumerated() { let u = v.normalized() * radius; pos[3 * i] = u.x; pos[3 * i + 1] = u.y; pos[3 * i + 2] = u.z }
        return fixOrientation(orientFacesOutward(Mesh(pos, faces)))
    }

    private static func orientFacesOutward(_ m: Mesh) -> Mesh {
        Mesh(m.pos, (0..<m.faceCount).map { f in m.faceNormal(f).dot(m.faceCenter(f)) < 0 ? m.faces[f].reversed() : m.faces[f] })
    }

    static func tetrahedron(_ radius: Float = 0.5) -> Mesh {
        polyhedron([V3(1, 1, 1), V3(1, -1, -1), V3(-1, 1, -1), V3(-1, -1, 1)],
                   [[0, 1, 2], [0, 3, 1], [0, 2, 3], [1, 3, 2]], radius)
    }

    static func octahedron(_ radius: Float = 0.5) -> Mesh {
        polyhedron([V3(1, 0, 0), V3(-1, 0, 0), V3(0, 1, 0), V3(0, -1, 0), V3(0, 0, 1), V3(0, 0, -1)],
                   [[0, 2, 4], [4, 2, 1], [1, 2, 5], [5, 2, 0], [0, 4, 3], [4, 1, 3], [1, 5, 3], [5, 0, 3]], radius)
    }

    static func icosahedron(_ radius: Float = 0.5) -> Mesh {
        let g = GOLD
        let v = [V3(-1, g, 0), V3(1, g, 0), V3(-1, -g, 0), V3(1, -g, 0),
                 V3(0, -1, g), V3(0, 1, g), V3(0, -1, -g), V3(0, 1, -g),
                 V3(g, 0, -1), V3(g, 0, 1), V3(-g, 0, -1), V3(-g, 0, 1)]
        let f = [[0, 11, 5], [0, 5, 1], [0, 1, 7], [0, 7, 10], [0, 10, 11],
                 [1, 5, 9], [5, 11, 4], [11, 10, 2], [10, 7, 6], [7, 1, 8],
                 [3, 9, 4], [3, 4, 2], [3, 2, 6], [3, 6, 8], [3, 8, 9],
                 [4, 9, 5], [2, 4, 11], [6, 2, 10], [8, 6, 7], [9, 8, 1]]
        return polyhedron(v, f, radius)
    }

    static func dodecahedron(_ radius: Float = 0.5) -> Mesh {
        let ico = icosahedron(1)
        let centers = (0..<ico.faceCount).map { ico.faceCenter($0).normalized() }
        var faces: [[Int]] = []
        for v in 0..<ico.vertexCount {
            let axis = ico.vertex(v).normalized()
            let (x, z) = frame(axis)
            let keyed = ico.vertexFaces[v].enumerated().map { (i, f) -> (Int, Float, Int) in
                let c = centers[f]; return (f, Float(atan2(Double(c.dot(z)), Double(c.dot(x)))), i)
            }
            faces.append(keyed.sorted { $0.1 != $1.1 ? $0.1 < $1.1 : $0.2 < $1.2 }.map { $0.0 })
        }
        return polyhedron(centers, faces, radius)
    }

    static func merge(_ meshes: Mesh...) -> Mesh { Mesh.concat(meshes.filter { $0.faceCount > 0 }) }

    static func circle(_ center: V3, _ r: Float, _ n: Int = 32, tiltX: Float = 0) -> [V3] {
        (0..<n).map { i in
            let a = 2.0 * Double.pi * Double(i) / Double(n)
            let x = Float(Double(r) * cos(a)), z = Float(Double(r) * sin(a))
            let ct = Float(cos(toRadians(Double(tiltX)))), st = Float(sin(toRadians(Double(tiltX))))
            return center + V3(x, -z * st, z * ct)
        }
    }
}
