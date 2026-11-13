import Foundation

/// v40 cetvelin birimi; `perInch` bir inçteki birim sayısı (piksel için 0) (Android `RulerUnit`).
enum RulerUnit: String, CaseIterable {
    case MM, CM, IN, PX
    var label: String { switch self { case .MM: return "mm"; case .CM: return "cm"; case .IN: return "in"; case .PX: return "px" } }
    var perInch: Float { switch self { case .MM: return 25.4; case .CM: return 2.54; case .IN: return 1; case .PX: return 0 } }
    static func parse(_ v: String?) -> RulerUnit { v.flatMap(RulerUnit.init(rawValue:)) ?? .CM }
}

/// v40 cetvel ölçüleri: tuval pikseli ↔ birim, 1:N ölçek (Android `RulerUnits`).
enum RulerUnits {
    static let DEFAULT_DPI: Float = 150
    static let Scales = [1, 2, 5, 10, 20, 50, 100]

    static func toUnits(_ px: Float, _ unit: RulerUnit, dpi: Float = DEFAULT_DPI) -> Float { unit == .PX ? px : px / dpi * unit.perInch }
    static func toPx(_ value: Float, _ unit: RulerUnit, dpi: Float = DEFAULT_DPI) -> Float { unit == .PX ? value : value / unit.perInch * dpi }
    static func real(_ px: Float, _ unit: RulerUnit, _ scale: Int, dpi: Float = DEFAULT_DPI) -> Float { toUnits(px, unit, dpi: dpi) * Float(max(scale, 1)) }
    static func format(_ px: Float, _ unit: RulerUnit, _ scale: Int, dpi: Float = DEFAULT_DPI) -> String {
        "\(number(real(px, unit, scale, dpi: dpi))) \(unit.label)"
    }
    static func number(_ value: Float) -> String {
        let s = String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), Double(value))
        return s.hasSuffix(".0") ? String(s.dropLast(2)) : s
    }
    static func scaleLabel(_ scale: Int) -> String { "1:\(max(scale, 1))" }

    /// Küçük çentik adımı (birim) ve kaç küçükte bir büyük çentik.
    static func tickStep(_ pxPerUnit: Float, _ minGapPx: Float, _ unit: RulerUnit) -> (Float, Int) {
        let steps: [Float]
        switch unit {
        case .MM: steps = [1, 5, 10, 50, 100, 500, 1000]
        case .CM: steps = [0.1, 0.5, 1, 5, 10, 50, 100]
        case .IN: steps = [0.125, 0.25, 0.5, 1, 2, 5, 10]
        case .PX: steps = [10, 50, 100, 500, 1000, 5000]
        }
        for s in steps where s * pxPerUnit >= minGapPx {
            let major: Int
            if unit == .IN { major = s < 1 ? Int((1 / s).rounded()) : 1 }
            else { major = (s == 0.1 || s == 1 || s == 10 || s == 100) ? 10 : 2 }
            return (s, max(major, 1))
        }
        return (steps.last!, 1)
    }
}

/// v40 döndürmede 15°'lik açı yakalama (Android `AngleSnap`).
enum AngleSnap {
    static let STEP: Float = 15
    static let TOLERANCE: Float = 4
    static let RELEASE_MS: Int64 = 600

    static func normalize(_ deg: Float) -> Float {
        var a = deg.truncatingRemainder(dividingBy: 360)
        if a > 180 { a -= 360 }
        if a <= -180 { a += 360 }
        return a
    }

    static func snap(_ deg: Float, enabled: Bool = true, step: Float = STEP, tolerance: Float = TOLERANCE) -> Float {
        let a = normalize(deg)
        if !enabled { return a }
        let nearest = (a / step + 0.5).rounded(.down) * step
        return abs(a - nearest) <= tolerance ? normalize(nearest) : a
    }

    static func display(_ deg: Float) -> Int { ((Int(normalize(deg).rounded()) % 360) + 360) % 360 }
}

/// v40 cetvel türleri (Android `RulerType`).
enum RulerType: String, CaseIterable {
    case STRAIGHT, PROTRACTOR, SET_SQUARE_45, SET_SQUARE_30, COMPASS, ELLIPSE, FRENCH_CURVE
    static func parse(_ v: String?) -> RulerType { v.flatMap(RulerType.init(rawValue:)) ?? .STRAIGHT }
}

enum RulerHandle { case NONE, BODY, ROTATE, SIZE, MINOR }

/// v40 cetvelin tuvaldeki yeri (Android `RulerPose`).
struct RulerPose: Equatable {
    var type: RulerType
    var cx: Float
    var cy: Float
    var angle: Float
    var length: Float
    var thickness: Float
    var minor: Float = 0.6

    static let MIN_LENGTH: Float = 24
    static let MAX_LENGTH: Float = 40000
}

/// Nokta dizisi (x0, y0, x1, y1…) ve kapalı mı.
typealias Contour = (points: [Float], closed: Bool)

/// v40 cetvel duruşunun hesaplanmış geometrisi (Android `RulerShape`).
final class RulerShape {
    let pose: RulerPose
    private let rad: Float, cosA: Float, sinA: Float
    private let l: Float, half: Float
    let localBody: [Float]
    let localHole: [Float]?
    let localContours: [Contour]
    let edges: SnapEdges
    let bodyPolygon: [Float]
    let holePolygon: [Float]?
    private(set) lazy var contours: [Contour] = localContours.map { (world($0.points), $0.closed) }

    static let ELLIPSE_STEPS = 128
    static let MIN_MINOR: Float = 0.15
    private static let HANDLE_ORDER: [RulerHandle] = [.ROTATE, .SIZE, .MINOR]

    func toWorld(_ lx: Float, _ ly: Float) -> P { P(pose.cx + lx * cosA - ly * sinA, pose.cy + lx * sinA + ly * cosA) }
    func toLocal(_ x: Float, _ y: Float) -> P {
        let dx = x - pose.cx, dy = y - pose.cy
        return P(dx * cosA + dy * sinA, -dx * sinA + dy * cosA)
    }
    private func world(_ local: [Float]) -> [Float] {
        var out = [Float](repeating: 0, count: local.count)
        for i in 0..<(local.count / 2) { let p = toWorld(local[2 * i], local[2 * i + 1]); out[2 * i] = p.x; out[2 * i + 1] = p.y }
        return out
    }

    private var setSquareH: Float { pose.type == .SET_SQUARE_45 ? l : l * Float(tan(Double.pi / 6)) }
    private func plateMargin() -> Float { max(half * 0.18, pose.thickness * 0.35) }

    init(_ pose: RulerPose) {
        self.pose = pose
        rad = pose.angle * .pi / 180
        cosA = cos(rad); sinA = sin(rad)
        l = pose.length; half = pose.length / 2
        let b = SnapEdges.Builder()
        var body: [Float] = [], hole: [Float]? = nil, cont: [Contour] = []
        // Yerelden tuvale (self henüz hazır değil: yerel kopya)
        let (cx, cy, ca, sa) = (pose.cx, pose.cy, cosA, sinA)
        func w(_ local: [Float]) -> [Float] {
            var out = local
            for i in 0..<(local.count / 2) {
                let x = local[2 * i], y = local[2 * i + 1]
                out[2 * i] = cx + x * ca - y * sa; out[2 * i + 1] = cy + x * sa + y * ca
            }
            return out
        }
        switch pose.type {
        case .STRAIGHT:
            let t = pose.thickness / 2
            body = [-half, -t, half, -t, half, t, -half, t]
            let top: [Float] = [-half, -t, half, -t], bottom: [Float] = [-half, t, half, t]
            cont = [(top, false), (bottom, false)]
            let wt = w(top); b.line(wt[0], wt[1], wt[2], wt[3]).endContour()
            let wb = w(bottom); b.line(wb[0], wb[1], wb[2], wb[3]).endContour()
        case .PROTRACTOR:
            body = RulerShape.arcPolygon(half, .pi, .pi, 64, closeToCenter: false)
            cont = [(body, true)]
            b.arc(cx, cy, half, rad + .pi, .pi)
            let e = w([-half, 0, half, 0])
            b.line(e[0], e[1], e[2], e[3]).endContour()
        case .SET_SQUARE_45, .SET_SQUARE_30:
            let h = pose.type == .SET_SQUARE_45 ? l : l * Float(tan(Double.pi / 6))
            let gx = l / 3, gy = -h / 3
            body = [-gx, -gy, l - gx, -gy, -gx, -h - gy]
            cont = [(body, true)]
            b.polyline(w(body), closed: true).endContour()
        case .COMPASS:
            b.arc(cx, cy, half, 0, 2 * .pi).endContour()
        case .ELLIPSE:
            let a = half, bb = half * pose.minor.coerceIn(RulerShape.MIN_MINOR, 1)
            let m = max(half * 0.18, pose.thickness * 0.35)
            body = [-a - m, -bb - m, a + m, -bb - m, a + m, bb + m, -a - m, bb + m]
            var hl = [Float](repeating: 0, count: RulerShape.ELLIPSE_STEPS * 2)
            for i in 0..<RulerShape.ELLIPSE_STEPS {
                let th = 2 * Float.pi * Float(i) / Float(RulerShape.ELLIPSE_STEPS)
                hl[2 * i] = a * cos(th); hl[2 * i + 1] = bb * sin(th)
            }
            hole = hl
            cont = [(hl, true)]
            if bb >= a * 0.999 { b.arc(cx, cy, a, 0, 2 * .pi).endContour() } else { b.polyline(w(hl), closed: true).endContour() }
        case .FRENCH_CURVE:
            let len = pose.length
            let outer = FrenchCurve.outer.map { $0 * len }, inner = FrenchCurve.inner.map { $0 * len }
            body = outer; hole = inner
            cont = [(outer, true), (inner, true)]
            b.polyline(w(outer), closed: true).endContour()
            b.polyline(w(inner), closed: true).endContour()
        }
        localBody = body; localHole = hole; localContours = cont
        edges = b.build()
        bodyPolygon = w(body)
        holePolygon = hole.map(w)
    }

    func inBody(_ p: P, pinRadius: Float) -> Bool {
        if pose.type == .COMPASS { return hypot(p.x - pose.cx, p.y - pose.cy) <= pinRadius }
        if !PolygonMath.contains(bodyPolygon, p.x, p.y) { return false }
        return holePolygon.map { !PolygonMath.contains($0, p.x, p.y) } ?? true
    }

    func handle(_ kind: RulerHandle, gap: Float) -> P? {
        switch pose.type {
        case .STRAIGHT, .PROTRACTOR, .FRENCH_CURVE:
            switch kind { case .ROTATE: return toWorld(-half - gap, 0); case .SIZE: return toWorld(half + gap, 0); default: return nil }
        case .SET_SQUARE_45, .SET_SQUARE_30:
            let h = setSquareH
            switch kind {
            case .SIZE: return toWorld(l * 2 / 3 + gap, h / 3)
            case .ROTATE: return toWorld(-l / 3, -h * 2 / 3 - gap)
            default: return nil
            }
        case .COMPASS:
            return kind == .SIZE ? toWorld(half, 0) : nil
        case .ELLIPSE:
            let m = plateMargin(), bb = half * pose.minor.coerceIn(Self.MIN_MINOR, 1)
            switch kind {
            case .ROTATE: return toWorld(-half - m - gap, 0)
            case .SIZE: return toWorld(half + m + gap, 0)
            case .MINOR: return toWorld(0, -bb - m - gap)
            default: return nil
            }
        }
    }

    func rotateHandleLocalAngle(gap: Float) -> Float {
        guard let h = handle(.ROTATE, gap: gap) else { return 0 }
        let local = toLocal(h.x, h.y)
        return atan2(local.y, local.x)
    }

    func hit(_ p: P, handleRadius: Float, gap: Float) -> RulerHandle {
        for kind in Self.HANDLE_ORDER {
            guard let h = handle(kind, gap: gap) else { continue }
            if hypot(p.x - h.x, p.y - h.y) <= handleRadius { return kind }
        }
        return inBody(p, pinRadius: handleRadius) ? .BODY : .NONE
    }

    func resized(_ handle: RulerHandle, _ p: P, gap: Float) -> RulerPose {
        let local = toLocal(p.x, p.y)
        func clampLen(_ v: Float) -> Float { v.coerceIn(RulerPose.MIN_LENGTH, RulerPose.MAX_LENGTH) }
        var n = pose
        switch pose.type {
        case .STRAIGHT:
            let newL = clampLen(local.x - gap + half)
            let c = toWorld((newL - l) / 2, 0)
            n.cx = c.x; n.cy = c.y; n.length = newL
        case .PROTRACTOR, .FRENCH_CURVE: n.length = clampLen(2 * (local.x - gap))
        case .SET_SQUARE_45, .SET_SQUARE_30: n.length = clampLen((local.x - gap) * 1.5)
        case .COMPASS:
            n.length = clampLen(2 * hypot(p.x - pose.cx, p.y - pose.cy))
            n.angle = atan2(p.y - pose.cy, p.x - pose.cx) * 180 / .pi
        case .ELLIPSE:
            if handle == .MINOR { n.minor = ((-local.y - gap) / 1.18 / half).coerceIn(Self.MIN_MINOR, 1) }
            else { n.length = clampLen(2 * (local.x - gap) / 1.18) }
        }
        return n
    }

    var measuredLength: Float { pose.type == .PROTRACTOR || pose.type == .COMPASS ? half : l }

    static func arcPolygon(_ r: Float, _ start: Float, _ sweep: Float, _ steps: Int, closeToCenter: Bool) -> [Float] {
        var out = [Float](repeating: 0, count: (steps + 1 + (closeToCenter ? 1 : 0)) * 2)
        for i in 0...steps {
            let a = start + sweep * Float(i) / Float(steps)
            out[2 * i] = r * cos(a); out[2 * i + 1] = r * sin(a)
        }
        return out
    }
}

enum PolygonMath {
    static func contains(_ poly: [Float], _ x: Float, _ y: Float) -> Bool {
        let n = poly.count / 2
        if n < 3 { return false }
        var inside = false
        var j = n - 1
        for i in 0..<n {
            let xi = poly[2 * i], yi = poly[2 * i + 1], xj = poly[2 * j], yj = poly[2 * j + 1]
            if (yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi { inside.toggle() }
            j = i
        }
        return inside
    }
}

/// v40 pistole biçimi (genişlik 1, merkez başlangıçta).
enum FrenchCurve {
    private static let outerControl: [Float] = [
        -0.50, 0.02, -0.44, -0.10, -0.32, -0.17, -0.16, -0.18, -0.02, -0.12, 0.10, -0.15,
        0.24, -0.24, 0.38, -0.25, 0.48, -0.16, 0.49, -0.04, 0.40, 0.05, 0.26, 0.08,
        0.12, 0.12, 0.00, 0.20, -0.14, 0.22, -0.30, 0.17, -0.44, 0.12,
    ]
    private static let innerControl: [Float] = [
        -0.30, 0.02, -0.22, -0.06, -0.10, -0.05, 0.02, 0.01, 0.14, -0.05, 0.26, -0.12,
        0.34, -0.10, 0.30, -0.04, 0.18, 0.02, 0.06, 0.08, -0.08, 0.09, -0.22, 0.07,
    ]
    static let outer: [Float] = catmullRom(outerControl, 10)
    static let inner: [Float] = catmullRom(innerControl, 10)

    static func catmullRom(_ ctrl: [Float], _ steps: Int) -> [Float] {
        let n = ctrl.count / 2
        var out = [Float](repeating: 0, count: n * steps * 2)
        var k = 0
        for i in 0..<n {
            let p0 = (i - 1 + n) % n, p1 = i, p2 = (i + 1) % n, p3 = (i + 2) % n
            for s in 0..<steps {
                let t = Float(s) / Float(steps), t2 = t * t, t3 = t2 * t
                for d in 0...1 {
                    let a = ctrl[2 * p0 + d], b = ctrl[2 * p1 + d], c = ctrl[2 * p2 + d], e = ctrl[2 * p3 + d]
                    let lin = 2 * b + (-a + c) * t
                    let quad = (2 * a - 5 * b + 4 * c - e) * t2
                    let cub = (-a + 3 * b - 3 * c + e) * t3
                    out[k + d] = 0.5 * (lin + quad + cub)
                }
                k += 2
            }
        }
        return out
    }
}
