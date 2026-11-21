import Foundation

/// v56 aşama 12 dolgu (Android `Sketch3Fill`): kapalı 3B çizgiden ya da düzlemde çizilen kementten iki yüzlü düz parça
/// ("fill-" kimlikli, cisim değil). Çokgen `MeshOps.triangulate` ile üçgenlenir.
enum Sketch3Fill {
    static let PREFIX = "fill-"
    static let MAX_POINTS = 160

    static func isFill(_ part: Part) -> Bool { part.id.hasPrefix(PREFIX) }

    /// Uçlar yeterince yakınsa kapalı (çevrenin %12'si ya da 3 kalınlık).
    static func isClosed(_ points: [Float], _ width: Float) -> Bool {
        let n = points.count / 3
        guard n >= 3 else { return false }
        var len: Float = 0
        for i in 1..<n { len += dist(points, i - 1, i) }
        guard len >= 1e-4 else { return false }
        return dist(points, 0, n - 1) <= max(len * 0.12, width * 3)
    }

    static func simplify(_ points: [Float]) -> [Float] {
        let n = points.count / 3
        guard n > 0 else { return points }
        var len: Float = 0
        for i in stride(from: 1, to: n, by: 1) { len += dist(points, i - 1, i) }
        let eps = len / (Float(MAX_POINTS) * 4)
        var keep = [0]
        for i in stride(from: 1, to: n, by: 1) where dist(points, keep.last!, i) > eps { keep.append(i) }
        if keep.count > 2 && dist(points, keep.first!, keep.last!) <= eps * 2 { keep.removeLast() }
        let step = max(1, (keep.count + MAX_POINTS - 1) / MAX_POINTS)
        var out: [Float] = []
        var k = 0
        while k < keep.count { let i = keep[k] * 3; out += [points[i], points[i + 1], points[i + 2]]; k += step }
        return out
    }

    static func normal(_ p: [Float]) -> V3 {
        let n = p.count / 3
        var nx: Float = 0, ny: Float = 0, nz: Float = 0
        for i in 0..<n {
            let a = i * 3, b = ((i + 1) % n) * 3
            nx += (p[a + 1] - p[b + 1]) * (p[a + 2] + p[b + 2])
            ny += (p[a + 2] - p[b + 2]) * (p[a] + p[b])
            nz += (p[a] - p[b]) * (p[a + 1] + p[b + 1])
        }
        return V3(nx, ny, nz).normalized()
    }

    /// Kapalı halka için iki yüzlü dolgu parçası; 3'ten az nokta ya da sıfır alan için nil.
    static func fromLoop(_ loop: [Float], _ id: String, _ color: Int32, opacity: Float = 1, layer: String? = nil, name: String = "Dolgu") -> Part? {
        let pts = simplify(loop)
        let n = pts.count / 3
        guard n >= 3 else { return nil }
        let nrm = normal(pts)
        guard nrm.length >= 0.5 else { return nil }
        var c = V3.ZERO
        for i in 0..<n { c = c + V3(pts[3 * i], pts[3 * i + 1], pts[3 * i + 2]) }
        c = c * (1 / Float(n))
        var pos = [Float](repeating: 0, count: n * 3)
        for i in 0..<n {
            let p = V3(pts[3 * i], pts[3 * i + 1], pts[3 * i + 2]) - c
            let q = p - nrm * p.dot(nrm)
            pos[3 * i] = q.x; pos[3 * i + 1] = q.y; pos[3 * i + 2] = q.z
        }
        guard area(pos, nrm) >= 1e-6 else { return nil }
        let mesh = Mesh(pos, [Array(0..<n), Array((0..<n).reversed())])
        return Part(PREFIX + id, name, mesh, nil, position: c,
                    material: Material(color: color, opacity: opacity.coerceIn(0.05, 1), kind: .MATTE, shine: 0),
                    uv: .PLANAR, layer: layer)
    }

    /// Ekran çokgeni (x, y…) içinde mi (çift-tek kuralı).
    static func inside(_ poly: [Float], _ x: Float, _ y: Float) -> Bool {
        let n = poly.count / 2
        guard n >= 3 else { return false }
        var c = false
        var j = n - 1
        for i in 0..<n {
            let xi = poly[2 * i], yi = poly[2 * i + 1], xj = poly[2 * j], yj = poly[2 * j + 1]
            if (yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi { c.toggle() }
            j = i
        }
        return c
    }

    static func screenArea(_ poly: [Float]) -> Float {
        let n = poly.count / 2
        var a: Float = 0
        for i in 0..<n { let j = (i + 1) % n; a += poly[2 * i] * poly[2 * j + 1] - poly[2 * j] * poly[2 * i + 1] }
        return abs(a) / 2
    }

    private static func area(_ pos: [Float], _ n: V3) -> Float {
        let k = pos.count / 3
        var s = V3.ZERO
        for i in 0..<k {
            let j = (i + 1) % k
            s = s + V3(pos[3 * i], pos[3 * i + 1], pos[3 * i + 2]).cross(V3(pos[3 * j], pos[3 * j + 1], pos[3 * j + 2]))
        }
        return abs(s.dot(n)) / 2
    }

    private static func dist(_ p: [Float], _ i: Int, _ j: Int) -> Float {
        let dx = p[3 * i] - p[3 * j], dy = p[3 * i + 1] - p[3 * j + 1], dz = p[3 * i + 2] - p[3 * j + 2]
        return (dx * dx + dy * dy + dz * dz).squareRoot()
    }
}

/// v56 aşama 13 yüzeye boya (Android `SurfacePaint`): fırça yolu cisimlerin yüzeyine ışınla düşürülür, normal boyunca
/// biraz dışarı alınır; ışının boşa gittiği yerde bölünür.
enum SurfacePaint {
    static let OFFSET: Float = 0.004

    static func cast(_ model: Model3D, _ view: View3, _ screen: [Float], offset: Float = OFFSET) -> [([Float], [Int])] {
        var out: [([Float], [Int])] = []
        var cur: [Float] = [], idx: [Int] = []
        func flush() { if idx.count >= 2 { out.append((cur, idx)) }; cur = []; idx = [] }
        for i in 0..<(screen.count / 2) {
            guard let hit = Pick3.pick(model, view, screen[2 * i], screen[2 * i + 1]) else { flush(); continue }
            let n = PlaneSnap.onFace(model, hit).normal
            let toCam = -view.ray(screen[2 * i], screen[2 * i + 1]).1
            let nn = n.dot(toCam) < 0 ? -n : n
            let p = hit.point + nn * offset
            cur += [p.x, p.y, p.z]; idx.append(i)
        }
        flush()
        return out
    }
}

/// v56 aşama 13 dönen video kamera yolu (Android `OrbitTrack`).
enum OrbitTrack {
    static let SECONDS: Float = 6

    static func frameCount(_ fps: Int, _ speed: Float) -> Int { max(2, Int(SECONDS * Float(fps) / speed.coerceIn(0.25, 4))) }

    static func cameras(_ base: Camera3, _ frames: Int, degrees: Float = 360) -> [Camera3] {
        (0..<frames).map { i in var c = base; c.yaw = base.yaw + degrees * Float(i) / Float(frames); return c }
    }
}

/// v56 aşama 13 GIF89a yazıcısı (Android `GifWriter` ile aynı palet, titreme ve LZW; sonsuz döngü).
final class GifWriter {
    let width: Int, height: Int
    private let delayCs: Int
    private var out = [UInt8]()
    private(set) var frameCount = 0

    static let PALETTE: [UInt32] = {
        var p = [UInt32]()
        for r in 0..<6 { for g in 0..<7 { for b in 0..<6 { p.append(UInt32(r * 255 / 5) << 16 | UInt32(g * 255 / 6) << 8 | UInt32(b * 255 / 5)) } } }
        return p
    }()
    private static let BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5]

    init(_ width: Int, _ height: Int, delayCs: Int = 4) {
        precondition((1...4096).contains(width) && (1...4096).contains(height))
        self.width = width; self.height = height; self.delayCs = delayCs
        out += Array("GIF89a".utf8)
        short(width); short(height)
        out += [0xF7, 0, 0]
        for i in 0..<256 {
            let c = i < 252 ? GifWriter.PALETTE[i] : 0
            out += [UInt8(c >> 16 & 0xFF), UInt8(c >> 8 & 0xFF), UInt8(c & 0xFF)]
        }
        out += [0x21, 0xFF, 11] + Array("NETSCAPE2.0".utf8) + [3, 1]
        short(0); out.append(0)
    }

    /// Kare ekler ([argb] boyu width·height).
    func addFrame(_ argb: [UInt32]) {
        precondition(argb.count == width * height)
        out += [0x21, 0xF9, 4, 0]; short(delayCs); out += [0, 0]
        out.append(0x2C); short(0); short(0); short(width); short(height); out.append(0)
        var idx = [UInt8](repeating: 0, count: argb.count)
        for y in 0..<height { for x in 0..<width {
            let i = y * width + x
            idx[i] = UInt8(GifWriter.index(argb[i], GifWriter.BAYER[(y & 3) * 4 + (x & 3)]))
        } }
        out.append(8)
        lzw(idx)
        frameCount += 1
    }

    func finish() -> Data { out.append(0x3B); return Data(out) }

    private func short(_ v: Int) { out += [UInt8(v & 0xFF), UInt8(v >> 8 & 0xFF)] }

    private func lzw(_ data: [UInt8]) {
        let minCode = 8, clear = 1 << minCode, eoi = clear + 1
        var block = [UInt8]()
        var bitBuf = 0, bitCount = 0, codeSize = minCode + 1
        var sub = [UInt8](); sub.reserveCapacity(255)
        func emitByte(_ b: Int) {
            sub.append(UInt8(b & 0xFF))
            if sub.count == 255 { block.append(255); block += sub; sub.removeAll(keepingCapacity: true) }
        }
        func emit(_ code: Int) {
            bitBuf |= code << bitCount; bitCount += codeSize
            while bitCount >= 8 { emitByte(bitBuf & 0xFF); bitBuf >>= 8; bitCount -= 8 }
        }
        var dict = [Int: Int](minimumCapacity: 8192)
        var next = eoi + 1
        emit(clear)
        var prefix = Int(data[0])
        for i in 1..<max(1, data.count) where i < data.count {
            let k = Int(data[i])
            let key = prefix << 8 | k
            if let f = dict[key] { prefix = f; continue }
            emit(prefix)
            if next < 4096 {
                dict[key] = next; next += 1
                if next > (1 << codeSize) && codeSize < 12 { codeSize += 1 }
            } else {
                emit(clear); dict.removeAll(keepingCapacity: true); next = eoi + 1; codeSize = minCode + 1
            }
            prefix = k
        }
        emit(prefix)
        emit(eoi)
        if bitCount > 0 { emitByte(bitBuf & 0xFF) }
        if !sub.isEmpty { block.append(UInt8(sub.count)); block += sub }
        block.append(0)
        out += block
    }

    /// ARGB rengin palet sırası (yarı saydam beyaza karışır); [t] titreme eşiği 0…15.
    static func index(_ argb: UInt32, _ t: Int = 8) -> Int {
        let a = Int(argb >> 24 & 0xFF)
        var r = Int(argb >> 16 & 0xFF), g = Int(argb >> 8 & 0xFF), b = Int(argb & 0xFF)
        if a < 255 { r = (r * a + 255 * (255 - a)) / 255; g = (g * a + 255 * (255 - a)) / 255; b = (b * a + 255 * (255 - a)) / 255 }
        let d = (Float(t) + 0.5) / 16
        // Kotlin Math.round: yarımda yukarı (floor(x + 0.5))
        func q(_ v: Int, _ levels: Int) -> Int { Int((Float(v) * Float(levels - 1) / 255 + d - 0.5 + 0.5).rounded(.down)).coerceIn(0, levels - 1) }
        return (q(r, 6) * 7 + q(g, 7)) * 6 + q(b, 6)
    }
}
