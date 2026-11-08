import Foundation

/// Kalem noktası: tuval birimleri (y aşağı), t milisaniye (0 olabilir).
struct InkPoint: Equatable {
    let x: Float
    let y: Float
    let t: Int64
    init(_ x: Float, _ y: Float, _ t: Int64 = 0) { self.x = x; self.y = y; self.t = t }
}

/// Kalemin kalkmadan çizdiği tek çizgi.
struct InkStroke {
    let points: [InkPoint]
    init(_ points: [InkPoint]) { self.points = points }
}

/// MIXED = rakam + harf + simge; DIGITS = rakam + matematik simgeleri; LETTERS = harfler (Türkçe dahil) + noktalama.
enum InkMode: String, CaseIterable { case MIXED, DIGITS, LETTERS }

/// Bir karakter adayı; score 0..1 (yüksek = daha iyi).
struct Candidate: Equatable {
    let text: String
    let score: Float
    init(_ text: String, _ score: Float) { self.text = text; self.score = score }
}

/// Tek karakter: hangi çizgilerden oluştuğu ve adayları (en iyi önce).
struct CharResult: Equatable {
    let strokeIndices: [Int]
    let candidates: [Candidate]
}

/// Tanıma sonucu (Android `Recognition`).
struct Recognition: Equatable {
    let text: String
    let confidence: Float
    let alternatives: [String]
    let chars: [CharResult]
}

/// İç kullanım: tek çizginin koordinatları (y aşağı) ve sınır kutusu.
final class Poly {
    let x: [Float]
    let y: [Float]
    let minX: Float, maxX: Float, minY: Float, maxY: Float
    var n: Int { x.count }

    init(_ x: [Float], _ y: [Float]) {
        self.x = x; self.y = y
        var a = Float.infinity, b = -Float.infinity, c = Float.infinity, d = -Float.infinity
        for i in x.indices { a = min(a, x[i]); b = max(b, x[i]); c = min(c, y[i]); d = max(d, y[i]) }
        minX = a; maxX = b; minY = c; maxY = d
    }

    var w: Float { maxX - minX }
    var h: Float { maxY - minY }
    var cx: Float { (minX + maxX) * 0.5 }
    var cy: Float { (minY + maxY) * 0.5 }
    var extent: Float { max(w, h) }
    lazy var length: Float = {
        var s: Float = 0
        if n > 1 { for i in 1..<n { s += kHypot(x[i] - x[i - 1], y[i] - y[i - 1]) } }
        return s
    }()

    /// x' = a·x + b·y + c, y' = d·x + e·y + f
    func affine(_ a: Float, _ b: Float, _ c: Float, _ d: Float, _ e: Float, _ f: Float) -> Poly {
        var nx = [Float](repeating: 0, count: n), ny = nx
        for i in 0..<n {
            nx[i] = a * x[i] + b * y[i] + c
            ny[i] = d * x[i] + e * y[i] + f
        }
        return Poly(nx, ny)
    }
}

struct Box {
    let minX: Float, minY: Float, maxX: Float, maxY: Float
    init(_ minX: Float, _ minY: Float, _ maxX: Float, _ maxY: Float) { self.minX = minX; self.minY = minY; self.maxX = maxX; self.maxY = maxY }
    var w: Float { maxX - minX }
    var h: Float { maxY - minY }
    var cx: Float { (minX + maxX) * 0.5 }
    var cy: Float { (minY + maxY) * 0.5 }

    static func of(_ polys: [Poly]) -> Box {
        var a = Float.infinity, b = Float.infinity, c = -Float.infinity, d = -Float.infinity
        for p in polys { a = min(a, p.minX); b = min(b, p.minY); c = max(c, p.maxX); d = max(d, p.maxY) }
        return Box(a, b, c, d)
    }
}

enum Geo {
    /// Yol boyunca eşit aralıklı yeniden örnekleme (uçlar korunur).
    static func resample(_ p: Poly, _ step: Float) -> Poly {
        if p.n < 2 || step <= 0 { return p }
        let len = p.length
        if len < 1e-6 { return Poly([p.x[0]], [p.y[0]]) }
        let count = max(2, Int((len / step).rounded(.up)) + 1)
        let real = len / Float(count - 1)
        var ox = [Float](repeating: 0, count: count), oy = ox
        ox[0] = p.x[0]; oy[0] = p.y[0]
        var k = 1
        var acc: Float = 0
        var px = p.x[0], py = p.y[0]
        var i = 1
        while i < p.n && k < count - 1 {
            let qx = p.x[i], qy = p.y[i]
            let d = kHypot(qx - px, qy - py)
            if acc + d >= real && d > 0 {
                let t = (real - acc) / d
                let nx = px + t * (qx - px), ny = py + t * (qy - py)
                ox[k] = nx; oy[k] = ny; k += 1
                px = nx; py = ny; acc = 0
            } else {
                acc += d; px = qx; py = qy; i += 1
            }
        }
        while k < count { ox[k] = p.x[p.n - 1]; oy[k] = p.y[p.n - 1]; k += 1 }
        return Poly(ox, oy)
    }

    /// [1,2,1] yumuşatma (uçlar sabit).
    static func smooth(_ p: Poly, _ passes: Int = 1) -> Poly {
        if p.n < 3 { return p }
        var x = p.x, y = p.y
        for _ in 0..<passes {
            var nx = x, ny = y
            for i in 1..<(x.count - 1) {
                nx[i] = (x[i - 1] + 2 * x[i] + x[i + 1]) * 0.25
                ny[i] = (y[i - 1] + 2 * y[i] + y[i + 1]) * 0.25
            }
            x = nx; y = ny
        }
        return Poly(x, y)
    }

    /// İki çizgi arasındaki en kısa nokta uzaklığı.
    static func minDistance(_ a: Poly, _ b: Poly) -> Float {
        var best = Float.infinity
        for i in 0..<a.n {
            for j in 0..<b.n {
                let dx = a.x[i] - b.x[j], dy = a.y[i] - b.y[j]
                let d = dx * dx + dy * dy
                if d < best { best = d }
            }
        }
        return best.squareRoot()
    }

    struct Turning {
        let absTurns: Float
        let sharpTurns: Int
        let reversals: Int
        var netTurns: Float = 0
    }

    /// Yönelim değişimleri: toplam mutlak dönüş (tur), keskin (>110°) köşe ve geri dönüş (>150°) sayısı.
    static func turning(_ p: Poly, _ step: Float) -> Turning {
        let r = smooth(resample(p, step), 1)
        if r.n < 3 { return Turning(absTurns: 0, sharpTurns: 0, reversals: 0) }
        var total = 0.0
        var sharp = 0, rev = 0
        var prevA = Double.nan
        var angles: [Double] = []
        for i in 1..<r.n {
            let dx = Double(r.x[i] - r.x[i - 1]), dy = Double(r.y[i] - r.y[i - 1])
            if dx * dx + dy * dy < 1e-12 { continue }
            angles.append(atan2(dy, dx))
        }
        var net = 0.0
        for a in angles {
            if !prevA.isNaN { let d = wrap(a - prevA); total += abs(d); net += d }
            prevA = a
        }
        let win = 2
        var i = win
        while i < angles.count - win {
            let d = abs(wrap(angles[i + win - 1] - angles[i - win]))
            if d > 110.0 * Double.pi / 180.0 {
                sharp += 1
                if d > 150.0 * Double.pi / 180.0 { rev += 1 }
                i += 2 * win
            } else { i += 1 }
        }
        return Turning(absTurns: Float(total / (2 * Double.pi)), sharpTurns: sharp, reversals: rev, netTurns: Float(abs(net) / (2 * Double.pi)))
    }

    /// Çizginin kutu merkezi etrafında dolanma sayısı.
    static func winding(_ p: Poly) -> Float {
        if p.n < 3 { return 0 }
        let cx = p.cx, cy = p.cy
        let r0 = 0.08 * p.extent
        var total = 0.0
        var prev = Double.nan
        for i in 0..<p.n {
            let dx = Double(p.x[i] - cx), dy = Double(p.y[i] - cy)
            if dx * dx + dy * dy < Double(r0 * r0) { continue }
            let a = atan2(dy, dx)
            if !prev.isNaN { total += wrap(a - prev) }
            prev = a
        }
        return Float(abs(total) / (2 * Double.pi))
    }

    /// Çizginin kendini kesme sayısı (komşu olmayan parçalar arasında).
    static func selfIntersections(_ p: Poly, _ step: Float) -> Int {
        let r = resample(p, step)
        var count = 0
        if r.n < 2 { return 0 }
        for i in 0..<(r.n - 1) {
            var j = i + 2
            while j < r.n - 1 {
                defer { j += 1 }
                if i == 0 && j == r.n - 2 { continue }
                if !segmentsCross(r.x[i], r.y[i], r.x[i + 1], r.y[i + 1], r.x[j], r.y[j], r.x[j + 1], r.y[j + 1]) { continue }
                let ux = r.x[i + 1] - r.x[i], uy = r.y[i + 1] - r.y[i]
                let vx = r.x[j + 1] - r.x[j], vy = r.y[j + 1] - r.y[j]
                let s = abs(ux * vy - uy * vx) / max(kHypot(ux, uy) * kHypot(vx, vy), 1e-9)
                if s > 0.5 { count += 1 }
            }
        }
        return count
    }

    private static func segmentsCross(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ cx: Float, _ cy: Float, _ dx: Float, _ dy: Float) -> Bool {
        func cross(_ ox: Float, _ oy: Float, _ px: Float, _ py: Float, _ qx: Float, _ qy: Float) -> Float { (px - ox) * (qy - oy) - (py - oy) * (qx - ox) }
        let d1 = cross(cx, cy, dx, dy, ax, ay), d2 = cross(cx, cy, dx, dy, bx, by)
        let d3 = cross(ax, ay, bx, by, cx, cy), d4 = cross(ax, ay, bx, by, dx, dy)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    static func wrap(_ a: Double) -> Double {
        var v = a
        while v > Double.pi { v -= 2 * Double.pi }
        while v < -Double.pi { v += 2 * Double.pi }
        return v
    }
}

/// Taslak kontrol noktası: corner ise spline burada kırılır.
struct Ctrl {
    let x: Float, y: Float, corner: Bool
    init(_ x: Float, _ y: Float, _ corner: Bool) { self.x = x; self.y = y; self.corner = corner }
}

/// Glif taslak dili (Android `Sketch`): çizgiler `|` ile, noktalar boşlukla ayrılır; `x,y` yumuşak, `*x,y` köşe,
/// `@cx,cy,rx,ry,a0,a1` elips yayı. Noktalar merkezcil Catmull-Rom eğrisiyle birleştirilir.
enum Sketch {
    static func parse(_ src: String) -> [[Ctrl]] {
        src.split(separator: "|", omittingEmptySubsequences: false).map { parseStroke($0.trimmingCharacters(in: .whitespacesAndNewlines)) }.filter { !$0.isEmpty }
    }

    private static func parseStroke(_ s: String) -> [Ctrl] {
        var out: [Ctrl] = []
        for tok0 in s.split(whereSeparator: { $0 == " " || $0 == "\n" || $0 == "\t" }) {
            var tok = Substring(tok0)
            var corner = false
            if tok.hasPrefix("*") { corner = true; tok = tok.dropFirst() }
            if tok.hasPrefix("@") {
                let v = tok.dropFirst().split(separator: ",", omittingEmptySubsequences: false).map { Float(String($0))! }
                precondition(v.count == 6, "yay: \(tok0)")
                let a0 = v[4], a1 = v[5]
                let steps = max(2, Int((abs(a1 - a0) / 30).rounded(.up)))
                for k in 0...steps {
                    let a = toRadians(Double(a0 + (a1 - a0) * Float(k) / Float(steps)))
                    out.append(Ctrl(v[0] + v[2] * Float(cos(a)), v[1] + v[3] * Float(sin(a)), corner && k == 0))
                }
            } else {
                let v = tok.split(separator: ",", omittingEmptySubsequences: false).map { Float(String($0))! }
                precondition(v.count == 2, "nokta: \(tok0)")
                if let last = out.last, last.x == v[0] && last.y == v[1] { continue }
                out.append(Ctrl(v[0], v[1], corner))
            }
        }
        return out
    }

    /// Kontrol noktalarından yoğun çoklu çizgi.
    static func render(_ ctrls: [Ctrl], _ perSeg: Int = 8) -> Poly {
        if ctrls.count == 1 { return Poly([ctrls[0].x], [ctrls[0].y]) }
        var xs: [Float] = [], ys: [Float] = []
        var start = 0
        while start < ctrls.count - 1 {
            var end = start + 1
            while end < ctrls.count - 1 && !ctrls[end].corner { end += 1 }
            appendRun(Array(ctrls[start...end]), perSeg, &xs, &ys, start == 0)
            start = end
        }
        return Poly(xs, ys)
    }

    private static func appendRun(_ run: [Ctrl], _ perSeg: Int, _ xs: inout [Float], _ ys: inout [Float], _ first: Bool) {
        if first { xs.append(run[0].x); ys.append(run[0].y) }
        if run.count == 2 {
            for k in 1...perSeg {
                let t = Float(k) / Float(perSeg)
                xs.append(run[0].x + (run[1].x - run[0].x) * t)
                ys.append(run[0].y + (run[1].y - run[0].y) * t)
            }
            return
        }
        let n = run.count
        for i in 0..<(n - 1) {
            let p1 = run[i], p2 = run[i + 1]
            let p0x = i > 0 ? run[i - 1].x : 2 * p1.x - p2.x
            let p0y = i > 0 ? run[i - 1].y : 2 * p1.y - p2.y
            let p3x = i + 2 < n ? run[i + 2].x : 2 * p2.x - p1.x
            let p3y = i + 2 < n ? run[i + 2].y : 2 * p2.y - p1.y
            for k in 1...perSeg {
                let u = Float(k) / Float(perSeg)
                let q = centripetal(p0x, p0y, p1.x, p1.y, p2.x, p2.y, p3x, p3y, u)
                xs.append(q.0); ys.append(q.1)
            }
        }
    }

    private static func centripetal(_ p0x: Float, _ p0y: Float, _ p1x: Float, _ p1y: Float, _ p2x: Float, _ p2y: Float,
                                    _ p3x: Float, _ p3y: Float, _ u: Float) -> (Float, Float) {
        func knot(_ ti: Float, _ ax: Float, _ ay: Float, _ bx: Float, _ by: Float) -> Float {
            ti + max(1e-3, Float(pow(Double(kHypot(bx - ax, by - ay)), 0.5)))
        }
        let t0: Float = 0
        let t1 = knot(t0, p0x, p0y, p1x, p1y)
        let t2 = knot(t1, p1x, p1y, p2x, p2y)
        let t3 = knot(t2, p2x, p2y, p3x, p3y)
        let t = t1 + (t2 - t1) * u
        func lerp(_ ax: Float, _ bx: Float, _ ta: Float, _ tb: Float) -> Float { (tb - t) / (tb - ta) * ax + (t - ta) / (tb - ta) * bx }
        let a1x = lerp(p0x, p1x, t0, t1), a1y = lerp(p0y, p1y, t0, t1)
        let a2x = lerp(p1x, p2x, t1, t2), a2y = lerp(p1y, p2y, t1, t2)
        let a3x = lerp(p2x, p3x, t2, t3), a3y = lerp(p2y, p3y, t2, t3)
        let b1x = lerp(a1x, a2x, t0, t2), b1y = lerp(a1y, a2y, t0, t2)
        let b2x = lerp(a2x, a3x, t1, t3), b2y = lerp(a2y, a3y, t1, t3)
        return (lerp(b1x, b2x, t1, t2), lerp(b1y, b2y, t1, t2))
    }

    static func renderAll(_ src: String, _ perSeg: Int = 8) -> [Poly] { parse(src).map { render($0, perSeg) } }
}
