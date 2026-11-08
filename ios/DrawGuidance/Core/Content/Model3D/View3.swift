import Foundation

/// Parçanın dönüşümü (Android `Transform3`): M = Öteleme · Rz · Ry · Rx · Ölçek (3×4, satır düzeni).
struct Transform3 {
    let m: [Float], n: [Float]

    func apply(_ x: Float, _ y: Float, _ z: Float, _ out: inout [Float], _ o: Int) {
        out[o] = m[0] * x + m[1] * y + m[2] * z + m[3]
        out[o + 1] = m[4] * x + m[5] * y + m[6] * z + m[7]
        out[o + 2] = m[8] * x + m[9] * y + m[10] * z + m[11]
    }

    func apply(_ v: V3) -> V3 {
        V3(m[0] * v.x + m[1] * v.y + m[2] * v.z + m[3],
           m[4] * v.x + m[5] * v.y + m[6] * v.z + m[7],
           m[8] * v.x + m[9] * v.y + m[10] * v.z + m[11])
    }

    func applyDir(_ v: V3) -> V3 {
        V3(m[0] * v.x + m[1] * v.y + m[2] * v.z, m[4] * v.x + m[5] * v.y + m[6] * v.z, m[8] * v.x + m[9] * v.y + m[10] * v.z)
    }

    func normal(_ x: Float, _ y: Float, _ z: Float, _ out: inout [Float], _ o: Int) {
        let a = n[0] * x + n[1] * y + n[2] * z
        let b = n[3] * x + n[4] * y + n[5] * z
        let c = n[6] * x + n[7] * y + n[8] * z
        let l = Swift.max((a * a + b * b + c * c).squareRoot(), 1e-12)
        out[o] = a / l; out[o + 1] = b / l; out[o + 2] = c / l
    }

    static func of(_ part: Part) -> Transform3 { of(part.position, part.rotation, part.scale) }

    static func of(_ position: V3, _ rotation: V3, _ scale: V3) -> Transform3 {
        let r = Self.rotation(rotation)
        let sx = scale.x, sy = scale.y, sz = scale.z
        let m: [Float] = [r[0] * sx, r[1] * sy, r[2] * sz, position.x,
                          r[3] * sx, r[4] * sy, r[5] * sz, position.y,
                          r[6] * sx, r[7] * sy, r[8] * sz, position.z]
        func inv(_ v: Float) -> Float { abs(v) < 1e-6 ? 1e6 : 1 / v }
        let n: [Float] = [r[0] * inv(sx), r[1] * inv(sy), r[2] * inv(sz),
                          r[3] * inv(sx), r[4] * inv(sy), r[5] * inv(sz),
                          r[6] * inv(sx), r[7] * inv(sy), r[8] * inv(sz)]
        return Transform3(m: m, n: n)
    }

    /// Döndürme matrisi (Rz · Ry · Rx, derece).
    static func rotation(_ deg: V3) -> [Float] {
        let ax = toRadians(Double(deg.x)), ay = toRadians(Double(deg.y)), az = toRadians(Double(deg.z))
        let cx = Float(cos(ax)), sx = Float(sin(ax))
        let cy = Float(cos(ay)), sy = Float(sin(ay))
        let cz = Float(cos(az)), sz = Float(sin(az))
        let r0 = cz * cy, r1 = cz * sy * sx - sz * cx, r2 = cz * sy * cx + sz * sx
        let r3 = sz * cy, r4 = sz * sy * sx + cz * cx, r5 = sz * sy * cx - cz * sx
        return [r0, r1, r2, r3, r4, r5, -sy, cy * sx, cy * cx]
    }

    static func inverseRotate(_ deg: V3, _ v: V3) -> V3 {
        let r = rotation(deg)
        return V3(r[0] * v.x + r[3] * v.y + r[6] * v.z, r[1] * v.x + r[4] * v.y + r[7] * v.z, r[2] * v.x + r[5] * v.y + r[8] * v.z)
    }
}

/// Kameranın ekrana izdüşümü (Android `View3`).
final class View3 {
    let camera: Camera3
    let width: Int, height: Int
    let scale: Float, centerX: Float, centerY: Float
    let rx: Float, ry: Float, rz: Float
    let dx: Float, dy: Float, dz: Float
    let tx: Float, ty: Float, tz: Float
    let focal: Float
    var perspective: Bool { focal.isFinite }
    private let ox: Float, oy: Float, oz: Float

    init(_ camera: Camera3, _ width: Int, _ height: Int, _ scale: Float, _ centerX: Float, _ centerY: Float) {
        self.camera = camera; self.width = width; self.height = height
        self.scale = scale; self.centerX = centerX; self.centerY = centerY
        focal = View3.focalOf(camera.persp)
        ox = camera.target.x; oy = camera.target.y; oz = camera.target.z
        let y = toRadians(Double(camera.yaw)), p = toRadians(Double(camera.pitch.coerceIn(-89.5, 89.5)))
        let cp = Float(cos(p)), sp = Float(sin(p)), cyw = Float(cos(y)), syw = Float(sin(y))
        tx = cp * syw; ty = sp; tz = cp * cyw
        rx = cyw; ry = 0; rz = -syw
        dx = ry * tz - rz * ty; dy = rz * tx - rx * tz; dz = rx * ty - ry * tx
    }

    var eye: V3 { V3(ox + tx * focal, oy + ty * focal, oz + tz * focal) }
    var toViewer: V3 { V3(tx, ty, tz) }
    var right: V3 { V3(rx, ry, rz) }
    var down: V3 { V3(dx, dy, dz) }

    func depth(_ x: Float, _ y: Float, _ z: Float) -> Float { (x - ox) * tx + (y - oy) * ty + (z - oz) * tz }

    func persp(_ zc: Float) -> Float { !perspective ? 1 : focal / (focal - zc) }

    /// out = (x, y, zc, s); görünmüyorsa s ≤ 0.
    func project(_ x: Float, _ y: Float, _ z: Float, _ out: inout [Float], _ o: Int = 0) {
        let ax = x - ox, ay = y - oy, az = z - oz
        let xc = ax * rx + ay * ry + az * rz
        let yc = ax * dx + ay * dy + az * dz
        let zc = ax * tx + ay * ty + az * tz
        let s: Float
        if !perspective { s = 1 } else {
            let den = focal - zc
            s = den < focal * 0.02 ? -1 : focal / den
        }
        out[o] = centerX + xc * s * scale
        out[o + 1] = centerY + yc * s * scale
        out[o + 2] = zc
        out[o + 3] = s
    }

    func project(_ v: V3) -> [Float] { var o = [Float](repeating: 0, count: 4); project(v.x, v.y, v.z, &o); return o }

    func ray(_ sx: Float, _ sy: Float) -> (V3, V3) {
        let a = (sx - centerX) / scale, b = (sy - centerY) / scale
        let q = V3(ox + rx * a + dx * b, oy + ry * a + dy * b, oz + rz * a + dz * b)
        if !perspective { return (q + V3(tx, ty, tz) * 1000, V3(-tx, -ty, -tz)) }
        return (eye, (q - eye).normalized())
    }

    func screenDelta(_ dxPx: Float, _ dyPx: Float, _ atDepth: Float = 0) -> V3 {
        let ps = persp(atDepth)
        let s: Float = ps > 0 ? ps : 1
        let k = 1 / (scale * s)
        return V3((rx * dxPx + dx * dyPx) * k, (ry * dxPx + dy * dyPx) * k, (rz * dxPx + dz * dyPx) * k)
    }

    static let REFERENCE_EXTENT: Float = 1.6

    static func focalOf(_ persp: Float) -> Float { persp < 0.02 ? .infinity : 0.8 + 3.2 / persp.coerceIn(0.02, 1) }

    static func editor(_ camera: Camera3, _ width: Int, _ height: Int) -> View3 {
        View3(camera, width, height, Float(Swift.min(width, height)) * 0.5 / REFERENCE_EXTENT * camera.zoom.coerceIn(0.05, 40),
              Float(width) / 2, Float(height) / 2)
    }

    static func fit(_ model: Model3D, _ camera: Camera3, _ width: Int, _ height: Int, _ margin: Float = 0.06) -> View3 {
        let probe = View3(camera, width, height, 1, 0, 0)
        var ax = Float.infinity, ay = Float.infinity, bx = -Float.infinity, by = -Float.infinity
        var tmp = [Float](repeating: 0, count: 3), pr = [Float](repeating: 0, count: 4)
        func acc(_ x: Float, _ y: Float, _ z: Float) {
            probe.project(x, y, z, &pr)
            if pr[3] <= 0 { return }
            ax = Swift.min(ax, pr[0]); ay = Swift.min(ay, pr[1]); bx = Swift.max(bx, pr[0]); by = Swift.max(by, pr[1])
        }
        for part in model.parts where part.visible {
            let t = Transform3.of(part)
            let p = part.mesh.pos
            for i in 0..<part.mesh.vertexCount { t.apply(p[3 * i], p[3 * i + 1], p[3 * i + 2], &tmp, 0); acc(tmp[0], tmp[1], tmp[2]) }
        }
        for s in model.strokes { for i in 0..<s.count { acc(s.points[3 * i], s.points[3 * i + 1], s.points[3 * i + 2]) } }
        if !ax.isFinite { return editor(camera, width, height) }
        let bw = Swift.max(bx - ax, 1e-4), bh = Swift.max(by - ay, 1e-4)
        let mw = Float(width) * (1 - 2 * margin), mh = Float(height) * (1 - 2 * margin)
        let k = Swift.min(mw / bw, mh / bh)
        let cx = Float(width) / 2 - (ax + bx) / 2 * k
        let cy = Float(height) / 2 - (ay + by) / 2 * k
        return View3(camera, width, height, k, cx, cy)
    }
}
