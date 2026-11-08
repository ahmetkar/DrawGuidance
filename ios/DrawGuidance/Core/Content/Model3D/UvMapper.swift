import Foundation

/// v24 yüz köşelerinin doku koordinatları (Android `UvMapper`).
enum UvMapper {

    static func compute(_ part: Part) -> [[Float]] {
        let m = part.mesh
        if part.uv == .MESH, let u = m.uvs { return u }
        let sx = part.scale.x, sy = part.scale.y, sz = part.scale.z
        let fn = m.faceNormals
        let mode0: UvMode = part.uv == .MESH ? .BOX : part.uv
        let PIF = PI_F
        let per = PIF * (abs(sx) + abs(sz)) * 0.5
        return (0..<m.faceCount).map { f in
            let face = m.faces[f]
            var out = [Float](repeating: 0, count: face.count * 2)
            let nx = abs(fn[3 * f] * sx), ny = abs(fn[3 * f + 1] * sy), nz = abs(fn[3 * f + 2] * sz)
            let mode: UvMode = (mode0 == .CYLINDER && ny > 0.7 * Swift.max(nx, nz).coerceAtLeast(1e-6) * 1.4) ? .PLANAR : mode0
            for k in face.indices {
                let v = face[k]
                let x = m.pos[3 * v] * sx, y = m.pos[3 * v + 1] * sy, z = m.pos[3 * v + 2] * sz
                switch mode {
                case .BOX, .MESH:
                    if nx >= ny && nx >= nz { out[2 * k] = z; out[2 * k + 1] = -y }
                    else if ny >= nx && ny >= nz { out[2 * k] = x; out[2 * k + 1] = z }
                    else { out[2 * k] = x; out[2 * k + 1] = -y }
                case .PLANAR: out[2 * k] = x; out[2 * k + 1] = z
                case .CYLINDER:
                    out[2 * k] = (Float(atan2(Double(x), Double(z))) / (2 * PIF) + 0.5) * per
                    out[2 * k + 1] = -y
                case .SPHERE:
                    let len = (x * x + y * y + z * z).squareRoot().coerceAtLeast(1e-6)
                    out[2 * k] = (Float(atan2(Double(x), Double(z))) / (2 * PIF) + 0.5) * per
                    out[2 * k + 1] = (Float(acos(Double((y / len).coerceIn(-1, 1)))) / PIF) * per * 0.5
                }
            }
            if mode == .CYLINDER || mode == .SPHERE {
                var lo = Float.infinity, hi = -Float.infinity
                for k in face.indices { lo = Swift.min(lo, out[2 * k]); hi = Swift.max(hi, out[2 * k]) }
                if hi - lo > per * 0.5 { for k in face.indices where out[2 * k] < per * 0.5 { out[2 * k] += per } }
            }
            return out
        }
    }

    private struct Key: Hashable {
        let mesh: ObjectIdentifier, uv: UvMode, sx: Float, sy: Float, sz: Float
    }

    private static let lock = NSLock()
    private static var cache: [Key: [[Float]]] = [:]
    private static var order: [Key] = []

    /// Aynı ağ, eşleme ve ölçek için önbellekten (en çok 96 kayıt, en eski önce atılır).
    static func cached(_ part: Part) -> [[Float]] {
        let key = Key(mesh: ObjectIdentifier(part.mesh), uv: part.uv, sx: part.scale.x, sy: part.scale.y, sz: part.scale.z)
        lock.lock()
        if let v = cache[key] {
            if let i = order.firstIndex(of: key) { order.remove(at: i) }
            order.append(key)
            lock.unlock()
            return v
        }
        lock.unlock()
        let uv = compute(part)
        lock.lock()
        if cache[key] == nil { order.append(key) }
        cache[key] = uv
        while order.count > 96 { cache[order.removeFirst()] = nil }
        lock.unlock()
        return uv
    }
}
