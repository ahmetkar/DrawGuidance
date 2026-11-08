import Foundation

/// Hazır cisimlerin ağları (Android `Primitives`): birim kutuya sığar, merkezleri başlangıçta.
enum Primitives {

    static func defaultSpec(_ kind: PrimitiveKind) -> PrimitiveSpec {
        switch kind {
        case .CUBE: return PrimitiveSpec(kind, 4, 1)
        case .SPHERE: return PrimitiveSpec(kind, 20, 10)
        case .CYLINDER: return PrimitiveSpec(kind, 24, 1)
        case .CONE: return PrimitiveSpec(kind, 24, 1, top: 0)
        case .PYRAMID: return PrimitiveSpec(kind, 4, 1, top: 0)
        case .PRISM3: return PrimitiveSpec(kind, 3, 1)
        case .PRISM6: return PrimitiveSpec(kind, 6, 1)
        case .TORUS: return PrimitiveSpec(kind, 24, 10, thickness: 0.3)
        case .PLANE: return PrimitiveSpec(kind, 4, 4)
        case .DOME: return PrimitiveSpec(kind, 20, 6)
        case .CAPSULE: return PrimitiveSpec(kind, 16, 4)
        case .TUBE: return PrimitiveSpec(kind, 24, 1, thickness: 0.3)
        case .WEDGE: return PrimitiveSpec(kind, 4, 1)
        case .STAR: return PrimitiveSpec(kind, 5, 1, thickness: 0.4)
        }
    }

    static let MAX_SEGMENTS = 48
    static let MAX_RINGS = 24
    private static let SQRT2: Float = 1.41421356

    static func build(_ spec: PrimitiveSpec) -> Mesh {
        switch spec.kind {
        case .CUBE: return cube()
        case .SPHERE: return sphere(spec.segments.coerceIn(6, 96), spec.rings.coerceIn(3, 64))
        case .CYLINDER, .CONE: return revolveFrustum(spec.segments.coerceIn(3, 128), 0.5, 0.5 * spec.top.coerceIn(0, 2), 0)
        case .PYRAMID: return revolveFrustum(spec.segments.coerceIn(3, 16), 0.5 * SQRT2, 0.5 * SQRT2 * spec.top.coerceIn(0, 2), 45)
        case .PRISM3: return revolveFrustum(3, 0.5, 0.5 * spec.top.coerceIn(0, 2), 0)
        case .PRISM6: return revolveFrustum(6, 0.5, 0.5 * spec.top.coerceIn(0, 2), 0)
        case .TORUS: return torus(spec.segments.coerceIn(6, 96), spec.rings.coerceIn(3, 48), spec.thickness.coerceIn(0.05, 0.95))
        case .PLANE: return plane(spec.segments.coerceIn(1, 32))
        case .DOME: return dome(spec.segments.coerceIn(6, 96), spec.rings.coerceIn(2, 32))
        case .CAPSULE: return capsule(spec.segments.coerceIn(6, 96), spec.rings.coerceIn(2, 24))
        case .TUBE: return tube(spec.segments.coerceIn(3, 128), spec.thickness.coerceIn(0.05, 0.95))
        case .WEDGE: return wedge()
        case .STAR: return star(spec.segments.coerceIn(3, 12), spec.thickness.coerceIn(0.1, 0.9))
        }
    }

    static func cube() -> Mesh {
        let h: Float = 0.5
        let p: [Float] = [-h, -h, -h, h, -h, -h, h, h, -h, -h, h, -h,
                          -h, -h, h, h, -h, h, h, h, h, -h, h, h]
        let f = [[4, 5, 6, 7], [1, 0, 3, 2], [5, 1, 2, 6], [0, 4, 7, 3], [7, 6, 2, 3], [0, 1, 5, 4]]
        return Mesh(p, f)
    }

    /// Dönel yüzey: profil (yarıçap, y) çiftleri aşağıdan yukarı; yarıçapı 0 olan uçlar tek köşedir.
    static func revolve(_ profile: [(Float, Float)], _ segments: Int, capBottom: Bool, capTop: Bool, offsetDeg: Float = 0) -> Mesh {
        var pos: [Float] = []
        var rings: [[Int]] = []
        func add(_ x: Float, _ y: Float, _ z: Float) -> Int { pos += [x, y, z]; return pos.count / 3 - 1 }
        let off = toRadians(Double(offsetDeg))
        for (r, y) in profile {
            if r <= 1e-6 {
                let v = add(0, y, 0)
                rings.append([Int](repeating: v, count: segments))
            } else {
                rings.append((0..<segments).map { s in
                    let a = off + 2.0 * Double.pi * Double(s) / Double(segments)
                    return add(Float(Double(r) * sin(a)), y, Float(Double(r) * cos(a)))
                })
            }
        }
        var faces: [[Int]] = []
        for k in 0..<(rings.count - 1) {
            let a = rings[k], b = rings[k + 1]
            for s in 0..<segments {
                let t = (s + 1) % segments
                if a[s] == a[t] && b[s] == b[t] { continue }
                if a[s] == a[t] { faces.append([a[s], b[t], b[s]]) }
                else if b[s] == b[t] { faces.append([a[s], a[t], b[s]]) }
                else { faces.append([a[s], a[t], b[t], b[s]]) }
            }
        }
        let first = rings.first!, last = rings.last!
        if capBottom && first[0] != first[1 % segments] { faces.append((0..<segments).map { first[segments - 1 - $0] }) }
        if capTop && last[0] != last[1 % segments] { faces.append(last) }
        return Mesh(pos, faces)
    }

    static func revolveFrustum(_ segments: Int, _ bottom: Float, _ top: Float, _ offsetDeg: Float) -> Mesh {
        revolve([(bottom, -0.5), (top, 0.5)], segments, capBottom: true, capTop: true, offsetDeg: offsetDeg)
    }

    static func sphere(_ segments: Int, _ rings: Int) -> Mesh {
        let profile: [(Float, Float)] = (0...rings).map { k in
            let phi = Double.pi * Double(k) / Double(rings)
            let r: Float = (k == 0 || k == rings) ? 0 : Float(0.5 * sin(phi))
            return (r, Float(-0.5 * cos(phi)))
        }
        return revolve(profile, segments, capBottom: false, capTop: false)
    }

    static func dome(_ segments: Int, _ rings: Int) -> Mesh {
        let profile: [(Float, Float)] = (0...rings).map { k in
            let phi = Double.pi / 2 * Double(k) / Double(rings)
            let r: Float = k == rings ? 0 : Float(0.5 * cos(phi))
            return (r, Float(0.5 * sin(phi) - 0.25))
        }
        return revolve(profile, segments, capBottom: true, capTop: false)
    }

    static func capsule(_ segments: Int, _ rings: Int) -> Mesh {
        let r: Float = 0.25
        var profile: [(Float, Float)] = []
        for k in 0...rings {
            let phi = Double.pi / 2 * Double(k) / Double(rings)
            let rr: Float = k == 0 ? 0 : Float(Double(r) * sin(phi))
            profile.append((rr, -0.5 + r - Float(Double(r) * cos(phi))))
        }
        for k in 0...rings {
            let phi = Double.pi / 2 * Double(k) / Double(rings)
            let rr: Float = k == rings ? 0 : Float(Double(r) * cos(phi))
            profile.append((rr, 0.5 - r + Float(Double(r) * sin(phi))))
        }
        return revolve(profile, segments, capBottom: false, capTop: false)
    }

    static func torus(_ segments: Int, _ rings: Int, _ thickness: Float) -> Mesh {
        let tube = Swift.max(0.02, 0.05 + 0.2 * thickness)
        let big = 0.5 - tube
        var pos = [Float](repeating: 0, count: segments * rings * 3)
        for u in 0..<segments {
            let a = 2.0 * Double.pi * Double(u) / Double(segments)
            for v in 0..<rings {
                let b = 2.0 * Double.pi * Double(v) / Double(rings)
                let i = (u * rings + v) * 3
                let rr = Double(big) + Double(tube) * cos(b)
                pos[i] = Float(rr * sin(a))
                pos[i + 1] = Float(Double(tube) * sin(b))
                pos[i + 2] = Float(rr * cos(a))
            }
        }
        var faces: [[Int]] = []
        for u in 0..<segments { for v in 0..<rings {
            let u1 = (u + 1) % segments, v1 = (v + 1) % rings
            faces.append([u * rings + v, u1 * rings + v, u1 * rings + v1, u * rings + v1])
        } }
        return Mesh(pos, faces)
    }

    static func plane(_ n: Int) -> Mesh {
        var pos = [Float](repeating: 0, count: (n + 1) * (n + 1) * 3)
        for i in 0...n { for j in 0...n {
            let k = (i * (n + 1) + j) * 3
            pos[k] = -0.5 + Float(i) / Float(n); pos[k + 1] = 0; pos[k + 2] = -0.5 + Float(j) / Float(n)
        } }
        var faces: [[Int]] = []
        for i in 0..<n { for j in 0..<n {
            let a = i * (n + 1) + j
            faces.append([a, a + 1, a + n + 2, a + n + 1])
        } }
        return Mesh(pos, faces)
    }

    static func tube(_ segments: Int, _ thickness: Float) -> Mesh {
        let ro: Float = 0.5, ri: Float = 0.5 * (1 - thickness)
        var pos = [Float](repeating: 0, count: segments * 4 * 3)
        func put(_ i: Int, _ r: Float, _ y: Float, _ s: Int) {
            let a = 2.0 * Double.pi * Double(s) / Double(segments)
            pos[3 * i] = Float(Double(r) * sin(a)); pos[3 * i + 1] = y; pos[3 * i + 2] = Float(Double(r) * cos(a))
        }
        for s in 0..<segments {
            put(s, ro, -0.5, s); put(segments + s, ro, 0.5, s)
            put(2 * segments + s, ri, -0.5, s); put(3 * segments + s, ri, 0.5, s)
        }
        let ob = 0, ot = segments, ib = 2 * segments, it = 3 * segments
        var faces: [[Int]] = []
        for s in 0..<segments {
            let t = (s + 1) % segments
            faces.append([ob + s, ob + t, ot + t, ot + s])
            faces.append([ib + t, ib + s, it + s, it + t])
            faces.append([ot + s, ot + t, it + t, it + s])
            faces.append([ob + t, ob + s, ib + s, ib + t])
        }
        return Mesh(pos, faces)
    }

    static func wedge() -> Mesh {
        let h: Float = 0.5
        let p: [Float] = [-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, -h, h, -h, h, h, -h]
        let f = [[0, 1, 2, 3], [1, 0, 4, 5], [3, 2, 5, 4], [0, 3, 4], [1, 5, 2]]
        return Mesh(p, f)
    }

    static func star(_ points: Int, _ inner: Float) -> Mesh {
        let n = points * 2
        let h: Float = 0.125
        var pos = [Float](repeating: 0, count: (2 * n + 2) * 3)
        for k in 0..<n {
            let r: Float = k % 2 == 0 ? 0.5 : 0.5 * inner
            let a = 2.0 * Double.pi * Double(k) / Double(n)
            let x = Float(Double(r) * sin(a)), z = Float(Double(r) * cos(a))
            pos[3 * k] = x; pos[3 * k + 1] = -h; pos[3 * k + 2] = z
            pos[3 * (n + k)] = x; pos[3 * (n + k) + 1] = h; pos[3 * (n + k) + 2] = z
        }
        let cb = 2 * n, ct = 2 * n + 1
        pos[3 * cb + 1] = -h; pos[3 * ct + 1] = h
        var faces: [[Int]] = []
        for k in 0..<n {
            let j = (k + 1) % n
            faces.append([k, j, n + j, n + k])
            faces.append([ct, n + k, n + j])
            faces.append([cb, j, k])
        }
        return Mesh(pos, faces)
    }
}
