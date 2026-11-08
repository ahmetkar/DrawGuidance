import Foundation

/// Java `Math.toRadians` (aynı çarpan).
@inline(__always) func toRadians(_ deg: Double) -> Double { deg * 0.017453292519943295 }

/// 2B afin dönüşüm (Android `content.vector.Affine`).
struct Affine {
    var a: Float = 1, b: Float = 0, c: Float = 0, d: Float = 1, e: Float = 0, f: Float = 0

    func map(_ p: P) -> P { P(a * p.x + c * p.y + e, b * p.x + d * p.y + f) }

    /// Önce [inner] sonra bu.
    func then(_ inner: Affine) -> Affine {
        Affine(a: a * inner.a + c * inner.b, b: b * inner.a + d * inner.b,
               c: a * inner.c + c * inner.d, d: b * inner.c + d * inner.d,
               e: a * inner.e + c * inner.f + e, f: b * inner.e + d * inner.f + f)
    }

    /// Ortalama ölçek (yazı boyu için).
    var scale: Float { abs(a * d - b * c).squareRoot() }

    static let identity = Affine()
    static func translate(_ dx: Float, _ dy: Float) -> Affine { Affine(e: dx, f: dy) }
    static func scale(_ sx: Float, _ sy: Float) -> Affine { Affine(a: sx, d: sy) }
    static func rotate(_ degrees: Float, _ px: Float = 0, _ py: Float = 0) -> Affine {
        let r = toRadians(Double(degrees))
        let cs = Float(cos(r)), sn = Float(sin(r))
        return Affine(a: cs, b: sn, c: -sn, d: cs, e: px - cs * px + sn * py, f: py - sn * px - cs * py)
    }
}

/// Yeni parçalara uygulanan varsayılan biçim.
struct PathStyle {
    var fill: String? = nil
    var stroke: String? = nil
    var width: Float = 1
    var line: LineStyle = .SOLID
    var alpha: Float = 1
}

/// Birim kutuda vektör çizim dili (Android `VectorBuilder`): her çağrı bir parça ekler ve zincirle biçimlenen [Part] döner.
/// [resolution] çizimin ekrandaki yaklaşık boyu (piksel); eğriler buna göre örneklenir.
final class VectorBuilder {
    static let INK = INK_COLOR

    let resolution: Float
    private(set) var paths: [VPath] = []
    private var transform = Affine.identity
    private var current = PathStyle()

    init(resolution: Float = 512) { self.resolution = resolution }

    /// Zincirle biçim verilen parça.
    struct Part {
        let owner: VectorBuilder
        let index: Int
        @discardableResult private func edit(_ f: (inout VPath) -> Void) -> Part { f(&owner.paths[index]); return self }
        @discardableResult func fill(_ hex: String?) -> Part { edit { $0.fill = hex } }
        @discardableResult func stroke(_ hex: String?) -> Part { edit { $0.stroke = hex } }
        @discardableResult func width(_ scale: Float) -> Part { edit { $0.width = scale } }
        @discardableResult func noStroke() -> Part { edit { $0.width = 0 } }
        @discardableResult func dashed() -> Part { edit { $0.line = .DASHED } }
        @discardableResult func dotted() -> Part { edit { $0.line = .DOTTED } }
        @discardableResult func alpha(_ v: Float) -> Part { edit { $0.alpha = v } }
        @discardableResult func solid(_ hex: String) -> Part { edit { $0.fill = hex; $0.stroke = hex } }
        @discardableResult func tag(_ v: String) -> Part { edit { $0.tag = v } }
    }

    @discardableResult
    private func add(_ points: [P], _ closed: Bool) -> Part {
        let t = transform
        paths.append(VPath(points: points.map { t.map($0) }, closed: closed, fill: current.fill, stroke: current.stroke,
                           width: current.width, line: current.line, alpha: current.alpha))
        return Part(owner: self, index: paths.count - 1)
    }

    /// Hazır bir parçayı dönüşümle ekler.
    @discardableResult
    func addPath(_ path: VPath) -> Part {
        var p = path
        let t = transform
        p.points = path.points.map { t.map($0) }
        p.labelSize = path.labelSize * t.scale
        paths.append(p)
        return Part(owner: self, index: paths.count - 1)
    }

    /// [block] içindeki parçaların varsayılan biçimi (verilmeyenler şimdikinden).
    func style(fill: String?? = nil, stroke: String?? = nil, width: Float? = nil, line: LineStyle? = nil, alpha: Float? = nil,
               _ block: () -> Void) {
        let saved = current
        current = PathStyle(fill: fill ?? saved.fill, stroke: stroke ?? saved.stroke, width: width ?? saved.width,
                            line: line ?? saved.line, alpha: alpha ?? saved.alpha)
        defer { current = saved }
        block()
    }

    /// Dönüşümlü grup: önce ölçek (px,py çevresinde), sonra döndürme, sonra öteleme.
    func group(dx: Float = 0, dy: Float = 0, sx: Float = 1, sy: Float? = nil, rotate: Float = 0, px: Float = 0.5, py: Float = 0.5,
               _ block: () -> Void) {
        var t = Affine.translate(-px, -py)
        t = Affine.scale(sx, sy ?? sx).then(t)
        if rotate != 0 { t = Affine.rotate(rotate).then(t) }
        t = Affine.translate(px + dx, py + dy).then(t)
        withTransform(t, block)
    }

    /// Birim kutuyu [x],[y],[w],[h] dikdörtgenine eşleyen grup.
    func inBox(_ x: Float, _ y: Float, _ w: Float, _ h: Float, rotate: Float = 0, _ block: () -> Void) {
        var t = Affine(a: w, d: h, e: x, f: y)
        if rotate != 0 { t = Affine.rotate(rotate, x + w / 2, y + h / 2).then(t) }
        withTransform(t, block)
    }

    func withTransform(_ t: Affine, _ block: () -> Void) {
        let saved = transform
        transform = saved.then(t)
        defer { transform = saved }
        block()
    }

    // MARK: Temel parçalar

    /// Noktalardan kırık çizgi: x1, y1, x2, y2, …
    @discardableResult
    func poly(_ xy: [Float], closed: Bool = false) -> Part {
        precondition(xy.count >= 4 && xy.count % 2 == 0, "poly: en az iki nokta gerekli")
        return add((0..<(xy.count / 2)).map { P(xy[$0 * 2], xy[$0 * 2 + 1]) }, closed)
    }

    @discardableResult
    func poly(_ xy: Float..., closed: Bool = false) -> Part { poly(xy, closed: closed) }

    @discardableResult
    func points(_ list: [P], closed: Bool = false) -> Part { add(list, closed) }

    @discardableResult
    func line(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float) -> Part { add([P(x1, y1), P(x2, y2)], false) }

    @discardableResult
    func rect(_ x: Float, _ y: Float, _ w: Float, _ h: Float) -> Part {
        add([P(x, y), P(x + w, y), P(x + w, y + h), P(x, y + h)], true)
    }

    @discardableResult
    func roundRect(_ x: Float, _ y: Float, _ w: Float, _ h: Float, _ r: Float) -> Part {
        let rr = min(r, min(abs(w), abs(h)) / 2)
        if rr <= 1e-4 { return rect(x, y, w, h) }
        var pts: [P] = []
        func corner(_ cx: Float, _ cy: Float, _ from: Double) {
            let n = segments(rr * Float(Double.pi) / 2, 4, 24)
            for i in 0...n {
                let a = from + (Double.pi / 2) * Double(i) / Double(n)
                pts.append(P(cx + rr * Float(cos(a)), cy + rr * Float(sin(a))))
            }
        }
        corner(x + w - rr, y + rr, -Double.pi / 2)
        corner(x + w - rr, y + h - rr, 0)
        corner(x + rr, y + h - rr, Double.pi / 2)
        corner(x + rr, y + rr, Double.pi)
        return add(pts, true)
    }

    @discardableResult
    func ellipse(_ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, rotate: Float = 0) -> Part {
        let n = segments(Float(Double.pi) * (rx + ry), 20, 720)
        let r = toRadians(Double(rotate))
        let cr = Float(cos(r)), sr = Float(sin(r))
        return add((0..<n).map { i in
            let a = 2 * Double.pi * Double(i) / Double(n)
            let ex = rx * Float(cos(a)), ey = ry * Float(sin(a))
            return P(cx + ex * cr - ey * sr, cy + ex * sr + ey * cr)
        }, true)
    }

    @discardableResult
    func circle(_ cx: Float, _ cy: Float, _ r: Float) -> Part { ellipse(cx, cy, r, r) }

    /// Elips yayı: [start] ve [sweep] derece (0° sağ, saat yönü pozitif).
    @discardableResult
    func arc(_ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, _ start: Float, _ sweep: Float, pie: Bool = false, closed: Bool = false) -> Part {
        let n = segments(abs(sweep) / 360 * Float(Double.pi) * (rx + ry), 6, 720)
        var pts: [P] = []
        for i in 0...n {
            let a = toRadians(Double(start + sweep * Float(i) / Float(n)))
            pts.append(P(cx + rx * Float(cos(a)), cy + ry * Float(sin(a))))
        }
        if pie { pts.append(P(cx, cy)) }
        return add(pts, pie || closed)
    }

    /// Düzgün çokgen ([rotate] derece; 0'da ilk köşe tepede).
    @discardableResult
    func polygon(_ cx: Float, _ cy: Float, _ r: Float, _ sides: Int, rotate: Float = 0, ry: Float? = nil) -> Part {
        let ryv = ry ?? r
        return add((0..<sides).map { i in
            let a = toRadians(-90.0 + Double(rotate) + 360.0 * Double(i) / Double(sides))
            return P(cx + r * Float(cos(a)), cy + ryv * Float(sin(a)))
        }, true)
    }

    @discardableResult
    func star(_ cx: Float, _ cy: Float, _ outer: Float, _ inner: Float, _ points: Int = 5, rotate: Float = 0) -> Part {
        add((0..<(points * 2)).map { i in
            let a = toRadians(-90.0 + Double(rotate) + 180.0 * Double(i) / Double(points))
            let r = i % 2 == 0 ? outer : inner
            return P(cx + r * Float(cos(a)), cy + r * Float(sin(a)))
        }, true)
    }

    /// Parametrik eğri, t ∈ [0,1].
    @discardableResult
    func curve(samples: Int = 0, closed: Bool = false, _ f: (Float) -> P) -> Part {
        let n = samples > 0 ? samples : segments(1.5, 48, 480)
        let count = closed ? n : n + 1
        return add((0..<count).map { f(Float($0) / Float(n)) }, closed)
    }

    /// Noktalardan geçen yumuşak eğri (Catmull-Rom).
    @discardableResult
    func smooth(_ xy: [Float], closed: Bool = false, tension: Float = 0.5) -> Part {
        let pts = (0..<(xy.count / 2)).map { P(xy[$0 * 2], xy[$0 * 2 + 1]) }
        return add(VectorBuilder.catmullRom(pts, closed, tension), closed)
    }

    @discardableResult
    func smooth(_ xy: Float..., closed: Bool = false, tension: Float = 0.5) -> Part { smooth(xy, closed: closed, tension: tension) }

    /// Yol kurucu: moveTo/lineTo/quadTo/cubicTo/arcTo; tek parça.
    @discardableResult
    func path(closed: Bool = false, _ block: (PathBuilder) -> Void) -> Part {
        let b = PathBuilder(self)
        block(b)
        return add(b.points, closed || b.isClosed)
    }

    /// Ok: gövde + uç.
    @discardableResult
    func arrow(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, head: Float = 0.06, double: Bool = false, filledHead: Bool = false) -> Part {
        let body = line(x1, y1, x2, y2)
        arrowHead(x2, y2, x2 - x1, y2 - y1, size: head, filled: filledHead)
        if double { arrowHead(x1, y1, x1 - x2, y1 - y2, size: head, filled: filledHead) }
        return body
    }

    @discardableResult
    func arrowHead(_ x: Float, _ y: Float, _ dx: Float, _ dy: Float, size: Float = 0.06, filled: Bool = false) -> Part {
        let len = max(kHypot(dx, dy), 1e-6)
        let ux = dx / len, uy = dy / len
        let nx = -uy, ny = ux
        let bx = x - ux * size, by = y - uy * size
        let part = add([P(bx + nx * size * 0.5, by + ny * size * 0.5), P(x, y), P(bx - nx * size * 0.5, by - ny * size * 0.5)], filled)
        if filled { part.fill(current.stroke ?? VectorBuilder.INK) }
        return part
    }

    /// Yazı: [x],[y] sol-üst, [size] kısa kenara oranla yazı boyu.
    @discardableResult
    func text(_ x: Float, _ y: Float, _ text: String, _ size: Float = 0.08, color: String? = nil) -> Part {
        let p = transform.map(P(x, y))
        paths.append(VPath(points: [p], stroke: color ?? current.stroke, alpha: current.alpha, label: text, labelSize: size * transform.scale))
        return Part(owner: self, index: paths.count - 1)
    }

    @discardableResult
    func textCentered(_ cx: Float, _ cy: Float, _ text: String, _ size: Float = 0.08, color: String? = nil) -> Part {
        self.text(cx - Float(text.count) * size * 0.55 / 2, cy - size / 2, text, size, color: color)
    }

    /// Kutu birimindeki uzunluk için eğri örnek sayısı.
    func segments(_ unitLength: Float, _ minN: Int, _ maxN: Int) -> Int {
        clampI(ktInt((abs(unitLength) * resolution * transform.scale / 6).rounded(.up)), minN, maxN)
    }

    static func catmullRom(_ pts: [P], _ closed: Bool, _ tension: Float = 0.5, steps: Int = 12) -> [P] {
        if pts.count < 3 { return pts }
        var out: [P] = []
        let n = pts.count
        let last = closed ? n : n - 1
        for i in 0..<last {
            let p0 = pts[closed ? (i - 1 + n) % n : max(0, i - 1)]
            let p1 = pts[i]
            let p2 = pts[(i + 1) % n]
            let p3 = pts[closed ? (i + 2) % n : min(n - 1, i + 2)]
            for s in 0..<steps { out.append(catmull(p0, p1, p2, p3, Float(s) / Float(steps), tension)) }
        }
        if !closed { out.append(pts[n - 1]) }
        return out
    }

    private static func catmull(_ p0: P, _ p1: P, _ p2: P, _ p3: P, _ t: Float, _ tension: Float) -> P {
        let t2 = t * t, t3 = t2 * t
        let m1 = (p2 - p0) * tension, m2 = (p3 - p1) * tension
        let h00 = 2 * t3 - 3 * t2 + 1, h10 = t3 - 2 * t2 + t
        let h01 = -2 * t3 + 3 * t2, h11 = t3 - t2
        return P(h00 * p1.x + h10 * m1.x + h01 * p2.x + h11 * m2.x, h00 * p1.y + h10 * m1.y + h01 * p2.y + h11 * m2.y)
    }
}

/// [VectorBuilder.path] için yol kurucu (tek parça).
final class PathBuilder {
    private unowned let owner: VectorBuilder
    fileprivate(set) var points: [P] = []
    fileprivate(set) var isClosed = false
    private var cursor = P(0, 0)

    init(_ owner: VectorBuilder) { self.owner = owner }

    func moveTo(_ x: Float, _ y: Float) { cursor = P(x, y); points.append(cursor) }
    func lineTo(_ x: Float, _ y: Float) { if points.isEmpty { points.append(cursor) }; cursor = P(x, y); points.append(cursor) }

    func quadTo(_ cx: Float, _ cy: Float, _ x: Float, _ y: Float) {
        if points.isEmpty { points.append(cursor) }
        let p0 = cursor
        let n = owner.segments(kHypot(x - p0.x, y - p0.y) + kHypot(cx - p0.x, cy - p0.y), 6, 96)
        for i in 1...n {
            let t = Float(i) / Float(n), m = 1 - t
            points.append(P(m * m * p0.x + 2 * m * t * cx + t * t * x, m * m * p0.y + 2 * m * t * cy + t * t * y))
        }
        cursor = P(x, y)
    }

    func cubicTo(_ c1x: Float, _ c1y: Float, _ c2x: Float, _ c2y: Float, _ x: Float, _ y: Float) {
        if points.isEmpty { points.append(cursor) }
        let p0 = cursor
        let approx = kHypot(c1x - p0.x, c1y - p0.y) + kHypot(c2x - c1x, c2y - c1y) + kHypot(x - c2x, y - c2y)
        let n = owner.segments(approx, 8, 128)
        for i in 1...n {
            let t = Float(i) / Float(n), m = 1 - t
            let a = m * m * m, b = 3 * m * m * t, c = 3 * m * t * t, d = t * t * t
            points.append(P(a * p0.x + b * c1x + c * c2x + d * x, a * p0.y + b * c1y + c * c2y + d * y))
        }
        cursor = P(x, y)
    }

    /// Merkezli elips yayı ile devam: [start]..[start]+[sweep] derece.
    func arcTo(_ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, _ start: Float, _ sweep: Float) {
        let n = owner.segments(abs(sweep) / 360 * Float(Double.pi) * (rx + ry), 4, 360)
        for i in 0...n {
            let a = toRadians(Double(start + sweep * Float(i) / Float(n)))
            let p = P(cx + rx * Float(cos(a)), cy + ry * Float(sin(a)))
            if i == 0 && !points.isEmpty && kHypot(p.x - cursor.x, p.y - cursor.y) < 1e-4 { continue }
            points.append(p)
        }
        cursor = points[points.count - 1]
    }

    func close() { isClosed = true }
}

extension Array where Element == VPath {
    /// Parçaların birim kutudaki sınırları (yazı çapaları dahil değil).
    func bounds() -> [Float] {
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in self where p.label == nil { for q in p.points { l = Swift.min(l, q.x); t = Swift.min(t, q.y); r = Swift.max(r, q.x); b = Swift.max(b, q.y) } }
        return [l, t, r, b]
    }
}
