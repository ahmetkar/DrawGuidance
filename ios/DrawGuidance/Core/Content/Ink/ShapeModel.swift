import Foundation

/// Gövde şekli öznitelikleri (Android `ShapeFeatures`): 8×8 ızgarada 4 doğrultu + uç kanalı, L2 normalize.
enum ShapeFeatures {
    static let G = 8
    static let CH = 5
    static let DIM = G * G * CH
    private static let STEP: Float = 0.03
    private static let END_W: Float = 0.22
    static let CLOUD = 32

    /// Kutunun köşegen açısı (0 = ince dikey, 0.5 = kare, 1 = yassı yatay).
    static func aspect(_ strokes: [Poly]) -> Float {
        let b = Box.of(strokes)
        return Float(atan2(Double(b.w), Double(b.h)) / (Double.pi / 2))
    }

    static func extract(_ strokes: [Poly]) -> [Float] {
        let b = Box.of(strokes)
        let s = max(max(b.w, b.h), 1e-4)
        let cx = b.cx, cy = b.cy
        var f = [Float](repeating: 0, count: DIM)
        for st in strokes {
            let p = Poly(st.x.map { ($0 - cx) / s + 0.5 }, st.y.map { ($0 - cy) / s + 0.5 })
            let r = Geo.resample(Geo.smooth(Geo.resample(p, STEP * 0.5), 2), STEP)
            if r.n < 2 || r.length < STEP * 0.5 {
                splat(&f, 4, r.x[0], r.y[0], END_W * 2)
                continue
            }
            for i in 0..<(r.n - 1) {
                let dx = r.x[i + 1] - r.x[i], dy = r.y[i + 1] - r.y[i]
                let len = kHypot(dx, dy)
                if len < 1e-6 { continue }
                var a = atan2(Double(dy), Double(dx))
                if a < 0 { a += Double.pi }
                let bin = a / (Double.pi / 4)
                let b0 = Int(bin.rounded(.down))
                let fr = Float(bin - Double(b0))
                let mx = (r.x[i] + r.x[i + 1]) * 0.5, my = (r.y[i] + r.y[i + 1]) * 0.5
                splat(&f, b0 % 4, mx, my, len * (1 - fr))
                splat(&f, (b0 + 1) % 4, mx, my, len * fr)
            }
            let gap = kHypot(r.x[r.n - 1] - r.x[0], r.y[r.n - 1] - r.y[0])
            let ext = max(max(r.maxX - r.minX, r.maxY - r.minY), 1e-4)
            if gap > 0.15 * ext || r.length < 1.5 * ext {
                splat(&f, 4, r.x[0], r.y[0], END_W)
                splat(&f, 4, r.x[r.n - 1], r.y[r.n - 1], END_W)
            }
        }
        blur(&f)
        var norm: Float = 0
        for v in f { norm += v * v }
        norm = norm.squareRoot()
        if norm > 0 { for i in f.indices { f[i] /= norm } }
        return f
    }

    /// Aynı normalleştirmeyle yaklaşık CLOUD noktalık nokta bulutu (x0,y0,x1,y1...).
    static func cloud(_ strokes: [Poly]) -> [Float] {
        let b = Box.of(strokes)
        let s = max(max(b.w, b.h), 1e-4)
        var total: Float = 0
        for st in strokes { total += st.length }
        var out: [Float] = []
        for st in strokes {
            let k = total > 0 ? max(1, Int((Float(CLOUD) * st.length / total + 0.5).rounded(.down))) : 1
            let r = k <= 1 || st.length <= 0 ? Poly([st.x[0]], [st.y[0]]) : Geo.resample(st, st.length / Float(k - 1))
            for i in 0..<r.n { out.append((r.x[i] - b.cx) / s); out.append((r.y[i] - b.cy) / s) }
        }
        return out
    }

    /// Karesel ortalama en yakın komşu uzaklığı (iki yönlü).
    static func chamfer(_ a: [Float], _ b: [Float]) -> Float {
        func oneWay(_ p: [Float], _ q: [Float]) -> Float {
            var sum: Float = 0
            var i = 0
            while i < p.count {
                var best = Float.infinity
                var j = 0
                while j < q.count {
                    let dx = p[i] - q[j], dy = p[i + 1] - q[j + 1]
                    let d = dx * dx + dy * dy
                    if d < best { best = d }
                    j += 2
                }
                sum += best
                i += 2
            }
            return sum / Float(p.count / 2)
        }
        return (0.5 * (oneWay(a, b) + oneWay(b, a))).squareRoot()
    }

    private static func splat(_ f: inout [Float], _ ch: Int, _ u: Float, _ v: Float, _ w: Float) {
        let gx = min(max(u * Float(G) - 0.5, 0), Float(G) - 1)
        let gy = min(max(v * Float(G) - 0.5, 0), Float(G) - 1)
        let x0 = min(Int(gx.rounded(.down)), G - 2), y0 = min(Int(gy.rounded(.down)), G - 2)
        let fx = gx - Float(x0), fy = gy - Float(y0)
        let base = ch * G * G
        f[base + y0 * G + x0] += w * (1 - fx) * (1 - fy)
        f[base + y0 * G + x0 + 1] += w * fx * (1 - fy)
        f[base + (y0 + 1) * G + x0] += w * (1 - fx) * fy
        f[base + (y0 + 1) * G + x0 + 1] += w * fx * fy
    }

    private static func blur(_ f: inout [Float]) {
        var tmp = [Float](repeating: 0, count: G * G)
        for ch in 0..<CH {
            let base = ch * G * G
            for y in 0..<G {
                for x in 0..<G {
                    var s = f[base + y * G + x] * 0.5
                    if x > 0 { s += f[base + y * G + x - 1] * 0.25 }
                    if x < G - 1 { s += f[base + y * G + x + 1] * 0.25 }
                    tmp[y * G + x] = s
                }
            }
            for y in 0..<G {
                for x in 0..<G {
                    var s = tmp[y * G + x] * 0.5
                    if y > 0 { s += tmp[(y - 1) * G + x] * 0.25 }
                    if y < G - 1 { s += tmp[(y + 1) * G + x] * 0.25 }
                    f[base + y * G + x] = s
                }
            }
        }
    }
}

/// Şablon kümesi (Android `ShapeModel`): taslaklar bozulmalarla çoğaltılır; sorgu en yakın komşuyla etiket başına
/// en küçük uzaklığı alır. İlk kullanımda bir kez kurulur.
enum ShapeModel {
    private struct Distort { let slantDeg: Float, rotDeg: Float, sx: Float }

    private static let distortions = [
        Distort(slantDeg: 0, rotDeg: 0, sx: 1),
        Distort(slantDeg: 12, rotDeg: 0, sx: 1), Distort(slantDeg: -10, rotDeg: 0, sx: 1),
        Distort(slantDeg: 0, rotDeg: 8, sx: 1), Distort(slantDeg: 0, rotDeg: -8, sx: 1),
        Distort(slantDeg: 0, rotDeg: 0, sx: 0.8), Distort(slantDeg: 0, rotDeg: 0, sx: 1.22),
        Distort(slantDeg: 7, rotDeg: -4, sx: 0.9), Distort(slantDeg: -5, rotDeg: 5, sx: 1.1),
    ]

    static let ASPECT_W: Float = 1.2
    static let CHAMFER_W: Float = 2.0
    static let CHAMFER_LABELS = 8
    static let PREFILTER_LABELS = 12

    static let labels: [String] = {
        var seen = Set<String>()
        return Glyphs.sketches.map(\.0).filter { seen.insert($0).inserted }
    }()
    private static let labelIndex: [String: Int] = Dictionary(uniqueKeysWithValues: labels.enumerated().map { ($0.element, $0.offset) })
    static func indexOf(_ label: String) -> Int { labelIndex[label] ?? -1 }

    private struct Bank {
        let feats: [Float], coarse: [Float], rowLabel: [Int], rowAspect: [Float], rows: Int
        let labelRows: [[Int]], clouds: [[Float]]
    }

    private static let POOLED = (ShapeFeatures.G / 2) * (ShapeFeatures.G / 2) * ShapeFeatures.CH

    /// 8×8 ızgarayı 4×4'e indirger (2×2 blok toplamı), L2 normalize.
    private static func pool(_ f: [Float]) -> [Float] {
        let g = ShapeFeatures.G, h = g / 2
        var out = [Float](repeating: 0, count: h * h * ShapeFeatures.CH)
        for ch in 0..<ShapeFeatures.CH { for y in 0..<g { for x in 0..<g { out[ch * h * h + (y / 2) * h + x / 2] += f[ch * g * g + y * g + x] } } }
        var n: Float = 0
        for v in out { n += v * v }
        n = n.squareRoot()
        if n > 0 { for i in out.indices { out[i] /= n } }
        return out
    }

    private static let bank: Bank = build()

    /// Şablonları önceden hazırlar.
    static func warmUp() { _ = bank.rows }

    private static func build() -> Bank {
        var feats: [[Float]] = [], lab: [Int] = [], asp: [Float] = [], clouds: [[Float]] = []
        for (label, src) in Glyphs.sketches {
            let polys = Sketch.renderAll(src, 8)
            for d in distortions {
                let t = distort(polys, d)
                feats.append(ShapeFeatures.extract(t))
                lab.append(labelIndex[label]!)
                asp.append(ShapeFeatures.aspect(t))
                clouds.append(ShapeFeatures.cloud(t))
            }
        }
        let all = feats.flatMap { $0 }
        let coarse = feats.flatMap { pool($0) }
        var labelRows = [[Int]](repeating: [], count: labels.count)
        for (i, l) in lab.enumerated() { labelRows[l].append(i) }
        return Bank(feats: all, coarse: coarse, rowLabel: lab, rowAspect: asp, rows: feats.count, labelRows: labelRows, clouds: clouds)
    }

    private static func distort(_ polys: [Poly], _ d: Distort) -> [Poly] {
        let b = Box.of(polys)
        let sh = Float(tan(toRadians(Double(d.slantDeg))))
        let r = toRadians(Double(d.rotDeg))
        let c = Float(cos(r)), s = Float(sin(r))
        return polys.map { p in
            p.affine(d.sx, -sh, -d.sx * b.cx + sh * b.cy + b.cx, 0, 1, 0)
                .affine(c, -s, b.cx - c * b.cx + s * b.cy, s, c, b.cy - s * b.cx - c * b.cy)
        }
    }

    /// Etiket başına uzaklık (küçük = benzer): 2 − 2·kosinüs + en-boy terimi + nokta bulutu terimi.
    static func distances(_ main: [Poly]) -> [Float] {
        let q = ShapeFeatures.extract(main)
        let qc = pool(q)
        let qa = ShapeFeatures.aspect(main)
        let bk = bank
        let nl = labels.count
        var coarseBest = [Float](repeating: .infinity, count: nl)
        let cd = POOLED
        for r in 0..<bk.rows {
            let dt = dot(qc, bk.coarse, r * cd, cd)
            let da = qa - bk.rowAspect[r]
            let d = 2 - 2 * dt + ASPECT_W * da * da
            let l = bk.rowLabel[r]
            if d < coarseBest[l] { coarseBest[l] = d }
        }
        let top = Array((0..<nl).sorted { coarseBest[$0] != coarseBest[$1] ? coarseBest[$0] < coarseBest[$1] : $0 < $1 }.prefix(PREFILTER_LABELS))
        var out = [Float](repeating: .nan, count: nl)
        var bestRow = [Int](repeating: -1, count: nl)
        let dim = ShapeFeatures.DIM
        var worst: Float = 0
        for l in top {
            var best = Float.infinity
            for r in bk.labelRows[l] {
                let dt = dot(q, bk.feats, r * dim, dim)
                let da = qa - bk.rowAspect[r]
                let d = 2 - 2 * dt + ASPECT_W * da * da
                if d < best { best = d; bestRow[l] = r }
            }
            out[l] = best
            worst = max(worst, best)
        }
        let qc2 = ShapeFeatures.cloud(main)
        var maxCh: Float = 0
        let chTop = Array(top.enumerated().sorted { out[$0.element] != out[$1.element] ? out[$0.element] < out[$1.element] : $0.offset < $1.offset }
            .map(\.element).prefix(CHAMFER_LABELS))
        for l in chTop {
            let ch = ShapeFeatures.chamfer(qc2, bk.clouds[bestRow[l]])
            out[l] += CHAMFER_W * ch
            maxCh = max(maxCh, ch)
        }
        worst += CHAMFER_W * maxCh
        for l in top where !chTop.contains(l) { out[l] = max(out[l] + CHAMFER_W * maxCh, 0) }
        for l in 0..<nl where out[l].isNaN { out[l] = max(worst + 0.05, coarseBest[l] + CHAMFER_W * maxCh) }
        return out
    }

    private static func dot(_ q: [Float], _ m: [Float], _ off: Int, _ len: Int) -> Float {
        var a0: Float = 0, a1: Float = 0, a2: Float = 0, a3: Float = 0
        var k = 0
        while k < len {
            a0 += q[k] * m[off + k]
            a1 += q[k + 1] * m[off + k + 1]
            a2 += q[k + 2] * m[off + k + 2]
            a3 += q[k + 3] * m[off + k + 3]
            k += 4
        }
        return (a0 + a1) + (a2 + a3)
    }

    static func sketchesOf(_ label: String) -> [[Poly]] { Glyphs.sketches.filter { $0.0 == label }.map { Sketch.renderAll($0.1, 8) } }
}
