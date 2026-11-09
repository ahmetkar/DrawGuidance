import Foundation

// Fen ve geometri çizimleri için ortak araçlar (Android `ScienceKit.kt`). Çizim w×h sanal birimlik eşit ölçekli
// tuvalde yapılır, tuval birim kutuya eşlenir. Yazılar `Lb` ile yazılır: boy sanal birimdir.

/// Ortak renkler (açık, ders kitabı tonları).
enum Sc {
    static let LIQUID = "#BFE3F7"
    static let LIQUID_DARK = "#7FC1EA"
    static let GLASS = "#EEF6FB"
    static let LENS = "#D8ECF8"
    static let RAY = "#E53935"
    static let METAL = "#C9D1D9"
    static let METAL_DARK = "#8C97A3"
    static let WOOD = "#E8C99B"
    static let GROUND = "#EFE7DA"
    static let BLOCK = "#F6D38A"
    static let RED = "#E53935"
    static let BLUE = "#1E88E5"
    static let GREEN = "#43A047"
    static let YELLOW = "#FDD835"
    static let ORANGE = "#FB8C00"
    static let FLAME_OUT = "#FFB74D"
    static let FLAME_IN = "#FFE082"
    static let PAPER = "#FFFFFF"
    static let SPECTRUM = ["#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#3949AB", "#8E24AA"]
}

/// Sanal tuvalde yazı: boy sanal birim; kutunun kısa kenarına oranlanarak kaydedilir.
struct Lb {
    let b: VectorBuilder
    private let k: Float

    init(_ b: VectorBuilder, _ w: Float, _ h: Float) { self.b = b; k = (w * h).squareRoot() / min(w, h) }

    @discardableResult
    func t(_ x: Float, _ y: Float, _ s: String, _ size: Float = 0.08, _ color: String? = nil) -> VectorBuilder.Part {
        b.text(x, y, s, size * k, color: color)
    }

    @discardableResult
    func c(_ cx: Float, _ cy: Float, _ s: String, _ size: Float = 0.08, _ color: String? = nil) -> VectorBuilder.Part {
        b.text(cx - Lb.width(s, size) / 2, cy - size * 0.52, s, size * k, color: color)
    }

    @discardableResult
    func r(_ rx: Float, _ cy: Float, _ s: String, _ size: Float = 0.08, _ color: String? = nil) -> VectorBuilder.Part {
        b.text(rx - Lb.width(s, size), cy - size * 0.52, s, size * k, color: color)
    }

    static func width(_ s: String, _ size: Float) -> Float {
        var sum = 0.0
        for ch in s {
            if ch == " " || "ilıİ,.I".contains(ch) { sum += 0.3 }
            else if "mMWw".contains(ch) { sum += 0.85 }
            else if ch.isUppercase { sum += 0.68 }
            else if ch.isNumber { sum += 0.6 }
            else { sum += 0.56 }
        }
        return Float(sum) * size
    }
}

// --- Küçük geometri yardımcıları ---

func sciDeg(_ rad: Double) -> Float { Float(rad * 180 / .pi) }

/// (cx,cy) merkezinden (x,y) noktasının açısı (derece; y aşağı olduğundan saat yönü pozitif).
func angleOf(_ cx: Float, _ cy: Float, _ x: Float, _ y: Float) -> Float { sciDeg(atan2(Double(y - cy), Double(x - cx))) }

func onCircle(_ cx: Float, _ cy: Float, _ r: Float, _ degrees: Float) -> P {
    let a = Double(degrees) * .pi / 180
    return P(cx + r * Float(cos(a)), cy + r * Float(sin(a)))
}

func sciLerp(_ a: P, _ b: P, _ t: Float) -> P { P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) }
func sciDist(_ a: P, _ b: P) -> Float { hypotf(b.x - a.x, b.y - a.y) }
func sciUnit(_ dx: Float, _ dy: Float) -> P {
    let l = max(hypotf(dx, dy), 1e-6)
    return P(dx / l, dy / l)
}

/// Orta çizgisi `pts` olan şerit: kapalı çokgen noktaları; yarı kalınlık hw0 → hw1.
func ribbon(_ pts: [P], _ hw0: Float, _ hw1: Float? = nil) -> [P] {
    let hw1 = hw1 ?? hw0
    let n = pts.count
    var left: [P] = [], right: [P] = []
    for i in 0..<n {
        let a = pts[max(0, i - 1)], b = pts[min(n - 1, i + 1)]
        let u = sciUnit(b.x - a.x, b.y - a.y)
        let hw = hw0 + (hw1 - hw0) * Float(i) / Float(max(n - 1, 1))
        left.append(P(pts[i].x - u.y * hw, pts[i].y + u.x * hw))
        right.append(P(pts[i].x + u.y * hw, pts[i].y - u.x * hw))
    }
    return left + right.reversed()
}

/// Yumuşatılmış orta çizgi noktaları (Catmull-Rom).
func spline(_ xy: [Float], steps: Int = 12) -> [P] {
    VectorBuilder.catmullRom((0..<(xy.count / 2)).map { P(xy[$0 * 2], xy[$0 * 2 + 1]) }, false, steps: steps)
}

// --- Çizim yardımcıları ---

extension VectorBuilder {
    @discardableResult
    func dot(_ x: Float, _ y: Float, _ r: Float = 0.014, color: String = INK_COLOR) -> Part {
        circle(x, y, r).fill(color).noStroke()
    }

    /// Dolu uçlu vektör oku. color nil: kalem rengi.
    @discardableResult
    func vec(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, head: Float = 0.05, color: String? = nil, width: Float = 1.2) -> Part {
        let u = sciUnit(x2 - x1, y2 - y1)
        let body = line(x1, y1, x2 - u.x * head * 0.7, y2 - u.y * head * 0.7).stroke(color).width(width)
        arrowHead(x2, y2, u.x, u.y, size: head, filled: true).fill(color ?? INK_COLOR).stroke(color).width(0.8)
        return body
    }

    func dim(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, head: Float = 0.035, color: String? = nil) {
        let u = sciUnit(x2 - x1, y2 - y1)
        line(x1 + u.x * head * 0.6, y1 + u.y * head * 0.6, x2 - u.x * head * 0.6, y2 - u.y * head * 0.6).stroke(color).width(0.8)
        arrowHead(x2, y2, u.x, u.y, size: head, filled: true).fill(color ?? INK_COLOR).stroke(color).width(0.6)
        arrowHead(x1, y1, -u.x, -u.y, size: head, filled: true).fill(color ?? INK_COLOR).stroke(color).width(0.6)
    }

    /// Kırık çizgi ışın; her parçanın ortasında yön oku.
    func ray(_ xy: [Float], color: String? = Sc.RAY, head: Float = 0.04, width: Float = 1.1, heads: Bool = true) {
        poly(xy).stroke(color).width(width)
        if !heads { return }
        for i in 0..<max(0, xy.count / 2 - 1) {
            let x1 = xy[i * 2], y1 = xy[i * 2 + 1], x2 = xy[i * 2 + 2], y2 = xy[i * 2 + 3]
            if hypotf(x2 - x1, y2 - y1) < head * 2.5 { continue }
            let u = sciUnit(x2 - x1, y2 - y1)
            let mx = (x1 + x2) / 2 + u.x * head / 2, my = (y1 + y2) / 2 + u.y * head / 2
            arrowHead(mx, my, u.x, u.y, size: head, filled: true).fill(color ?? INK_COLOR).stroke(color).width(0.6)
        }
    }

    func ray(_ xy: Float..., color: String? = Sc.RAY, head: Float = 0.04, width: Float = 1.1, heads: Bool = true) {
        ray(xy, color: color, head: head, width: width, heads: heads)
    }

    /// A→B doğru parçası ve bir yanında eğik tarama.
    func hatch(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, side: Float = 1, gap: Float = 0.05, len: Float = 0.04) {
        line(ax, ay, bx, by).width(1.4)
        let l = hypotf(bx - ax, by - ay)
        let u = sciUnit(bx - ax, by - ay)
        let n = P(-u.y * side, u.x * side)
        let d = sciUnit(n.x - u.x, n.y - u.y)
        let count = Int(l / gap)
        let start = (l - Float(count) * gap) / 2 + gap / 2
        for i in 0..<max(count, 0) {
            let s = start + Float(i) * gap
            let px = ax + u.x * s, py = ay + u.y * s
            line(px, py, px + d.x * len * 1.2, py + d.y * len * 1.2).width(0.7)
        }
    }

    @discardableResult
    func angleArc(_ cx: Float, _ cy: Float, _ r: Float, _ start: Float, _ sweep: Float) -> Part {
        arc(cx, cy, r, r, start, sweep).width(0.8)
    }

    func rightMark(_ x: Float, _ y: Float, _ ux: Float, _ uy: Float, _ vx: Float, _ vy: Float, s: Float = 0.05) {
        let u = sciUnit(ux, uy), v = sciUnit(vx, vy)
        poly(x + u.x * s, y + u.y * s, x + u.x * s + v.x * s, y + u.y * s + v.y * s, x + v.x * s, y + v.y * s).width(0.8)
    }

    func ticks(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, count: Int = 1, size: Float = 0.03, gap: Float = 0.018) {
        let u = sciUnit(bx - ax, by - ay)
        let n = P(-u.y, u.x)
        let mx = (ax + bx) / 2, my = (ay + by) / 2
        for i in 0..<count {
            let o = (Float(i) - Float(count - 1) / 2) * gap
            let cx = mx + u.x * o, cy = my + u.y * o
            line(cx - n.x * size, cy - n.y * size, cx + n.x * size, cy + n.y * size).width(0.9)
        }
    }

    /// Helezon yay (yandan görünüş).
    func coil(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, turns: Int, r: Float,
              lead: Float = 0.06, tilt: Float = 0.45, pitch: ((Float) -> Float)? = nil) {
        let l = hypotf(bx - ax, by - ay)
        let u = sciUnit(bx - ax, by - ay)
        let n = P(-u.y, u.x)
        let lc = l - lead * 2
        let steps = turns * 28
        var pts: [P] = [P(ax, ay)]
        pts.reserveCapacity(steps + 3)
        for i in 0...max(steps, 0) {
            let s = steps == 0 ? 0 : Float(i) / Float(steps)
            let phi = 2.0 * Double.pi * Double(turns) * Double(s)
            let xi = (pitch?(s) ?? s) * lc
            let along = lead + xi + r * tilt * (Float(cos(phi)) - 1)
            let perp = r * Float(sin(phi))
            pts.append(P(ax + u.x * along + n.x * perp, ay + u.y * along + n.y * perp))
        }
        pts.append(P(bx, by))
        points(pts)
    }

    @discardableResult
    func bar(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ hw: Float) -> Part {
        let u = sciUnit(bx - ax, by - ay)
        let n = P(-u.y * hw, u.x * hw)
        return points([P(ax + n.x, ay + n.y), P(bx + n.x, by + n.y), P(bx - n.x, by - n.y), P(ax - n.x, ay - n.y)], closed: true)
    }

    @discardableResult
    func tube(_ pts: [P], _ hw0: Float, _ hw1: Float? = nil) -> Part { points(ribbon(pts, hw0, hw1), closed: true) }

    func shine(_ cx: Float, _ cy: Float, _ r: Float) {
        ellipse(cx - r * 0.35, cy - r * 0.4, r * 0.28, r * 0.18, rotate: -35).fill("#FFFFFF").noStroke().alpha(0.6)
    }
}

// --- Sayı yardımcıları (Kotlin `round` yarıda çifte yuvarlar) ---

@inline(__always) func kround(_ x: Double) -> Double { x.rounded(.toNearestOrEven) }
@inline(__always) func rad(_ d: Double) -> Double { d * .pi / 180 }
@inline(__always) func degOf(_ r: Double) -> Double { r * 180 / .pi }
