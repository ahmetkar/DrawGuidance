import Foundation

// v45 (6) formülle ve tutamakla düzenlenen geometri/matematik/biyoloji şekilleri (Android `ParamGeometry.kt`).

fileprivate func n(_ v: Double, _ d: Int = 2) -> String { ParamModel.num(v, d) }
fileprivate let FILL = "#E3F2FD"
fileprivate let EDGE = "#1E88E5"
fileprivate let GRID = "#CFD8DC"

/// "1, 2, 5 × 10ⁿ" dizisinde v'den büyük ya da eşit ilk değer.
func niceCeil(_ v: Double) -> Double {
    if v <= 0 || !v.isFinite { return 1 }
    let p = pow(10.0, floor(log10(v)))
    for m in [1.0, 2.0, 5.0, 10.0] where m * p >= v - 1e-12 { return m * p }
    return 10 * p
}

/// a·x² + b·x + c'nin okunur yazımı ("x² − 2x − 3").
func polyText(_ a: Double, _ b: Double, _ c: Double, _ v: String = "x") -> String {
    var sb = ""
    func term(_ k: Double, _ body: String) {
        if abs(k) < 1e-12 { return }
        let neg = k < 0
        let mag = abs(k)
        let coef = !body.isEmpty && abs(mag - 1) < 1e-12 ? "" : n(mag, 3)
        if sb.isEmpty { sb += neg ? "−" : "" } else { sb += neg ? " − " : " + " }
        sb += coef + body
    }
    term(a, "\(v)²"); term(b, v); term(c, "")
    return sb.isEmpty ? "0" : sb
}

/// İç açı yayının süpürmesini (-180, 180] aralığına getirir.
fileprivate func wrapSweep(_ s: Float) -> Float {
    var sw = s
    while sw > 180 { sw -= 360 }
    while sw < -180 { sw += 360 }
    return sw
}

// MARK: - Üçgen

/// Üçgen: iki açı (A, B) ve aradaki kenar (c). Tutamak: C köşesi.
final class TriangleModel: ParamModel {
    static let shared = TriangleModel()
    private init() {
        super.init("sci.geo.triangle", "Üçgen (açılar ve kenarlar)", w: 1.4, h: 1,
                   tags: ["üçgen", "açı", "kenar", "iç açılar toplamı", "sinüs teoremi", "geometri", "formül"])
    }

    private static let D = [
        ParamDef("A", "A", "A açısı", kind: .ANGLE, default: 60, min: 1, max: 178, step: 5, unit: "°"),
        ParamDef("B", "B", "B açısı", kind: .ANGLE, default: 50, min: 1, max: 178, step: 5, unit: "°"),
        ParamDef("c", "c", "c kenarı", default: 6, min: 0.1, max: 1000, step: 0.5),
    ]
    override var defs: [ParamDef] { Self.D }

    private static func cAng(_ v: ParamValues) -> Double { 180 - v["A"] - v["B"] }
    private static func sideA(_ v: ParamValues) -> Double { let c = cAng(v); return c <= 0 ? .nan : v["c"] * sin(rad(v["A"])) / sin(rad(c)) }
    private static func sideB(_ v: ParamValues) -> Double { let c = cAng(v); return c <= 0 ? .nan : v["c"] * sin(rad(v["B"])) / sin(rad(c)) }
    private static let DV = [
        Derived("C", "C açısı", unit: "°", solveFor: "B") { cAng($0) },
        Derived("a", "a kenarı", solveFor: "A") { sideA($0) },
        Derived("b", "b kenarı", solveFor: "B") { sideB($0) },
        Derived("S", "Alan", solveFor: "c") { 0.5 * sideB($0) * $0["c"] * sin(rad($0["A"])) },
    ]
    override var derived: [Derived] { Self.DV }
    override func invalid(_ v: ParamValues) -> String? { Self.cAng(v) < 1 - 1e-9 ? "C" : nil }

    override func editText(_ raw: [String: String]?) -> String {
        let v = values(raw)
        return "A = \(n(v["A"], 2))°; B = \(n(v["B"], 2))°; c = \(n(v["c"], 3))"
    }

    override func label(_ v: ParamValues) -> [String] {
        ["A + B + C = 180°",
         "A = \(n(v["A"], 1))°; B = \(n(v["B"], 1))°; C = \(n(Self.cAng(v), 1))°",
         "a = \(n(Self.sideA(v))); b = \(n(Self.sideB(v))); c = \(n(v["c"]))"]
    }

    private static let BASE = 0.9, LEFT = 0.12, WIDTH = 1.16, HEIGHT = 0.52

    private struct Frame {
        let s: Double, ox: Double, xmin: Double
        func map(_ x: Double, _ y: Double) -> P { P(Float(ox + (x - xmin) * s), Float(BASE - y * s)) }
        func unmap(_ p: P) -> (Double, Double) { ((Double(p.x) - ox) / s + xmin, (BASE - Double(p.y)) / s) }
    }

    private func apex(_ v: ParamValues) -> (Double, Double) {
        let b = Self.sideB(v)
        return (b * cos(rad(v["A"])), b * sin(rad(v["A"])))
    }

    private func frame(_ v: ParamValues) -> Frame {
        let c = v["c"]
        let (cx, cy) = apex(v)
        let xmin = min(0, cx), xmax = max(c, cx)
        let s = min(Self.WIDTH / (xmax - xmin), Self.HEIGHT / max(cy, 1e-9))
        return Frame(s: s, ox: Self.LEFT + (Self.WIDTH - (xmax - xmin) * s) / 2, xmin: xmin)
    }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        if invalid(v) != nil { return }
        let fr = frame(v)
        let (cx, cy) = apex(v)
        let pa = fr.map(0, 0), pb = fr.map(v["c"], 0), pc = fr.map(cx, cy)
        b.poly(pa.x, pa.y, pb.x, pb.y, pc.x, pc.y, closed: true).fill(FILL).width(1.4).tag("curve")
        let g = P((pa.x + pb.x + pc.x) / 3, (pa.y + pb.y + pc.y) / 3)
        func corner(_ p: P, _ q1: P, _ q2: P, _ name: String, _ ang: Double) {
            let a1 = angleOf(p.x, p.y, q1.x, q1.y)
            let sw = wrapSweep(angleOf(p.x, p.y, q2.x, q2.y) - a1)
            b.arc(p.x, p.y, 0.07, 0.07, a1, sw).width(0.8).stroke(EDGE)
            let mid = onCircle(p.x, p.y, 0.13, a1 + sw / 2)
            lb.c(mid.x, mid.y, "\(n(ang, 0))°", 0.05, EDGE)
            let u = sciUnit(p.x - g.x, p.y - g.y)
            lb.c(p.x + u.x * 0.05, p.y + u.y * 0.05, name, 0.07)
        }
        corner(pa, pb, pc, "A", v["A"]); corner(pb, pc, pa, "B", v["B"]); corner(pc, pa, pb, "C", Self.cAng(v))
        func side(_ p: P, _ q: P, _ name: String) {
            let m = sciLerp(p, q, 0.5)
            let u = sciUnit(m.x - g.x, m.y - g.y)
            lb.c(m.x + u.x * 0.06, m.y + u.y * 0.06, name, 0.06)
        }
        side(pb, pc, "a"); side(pc, pa, "b"); side(pa, pb, "c")
        b.dot(pc.x, pc.y, 0.016, color: EDGE)
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        if invalid(v) != nil { return [] }
        let (cx, cy) = apex(v)
        let p = frame(v).map(cx, cy)
        return [ParamHandle(key: "C", x: p.x, y: p.y)]
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        if key != "C" { return nil }
        let (wx, wy) = frame(v).unmap(P(x, y))
        if wy <= v["c"] * 0.01 { return nil }
        let a = degOf(atan2(wy, wx)), bb = degOf(atan2(wy, v["c"] - wx))
        return ["A": kround(a * 10) / 10, "B": kround(bb * 10) / 10]
    }
}

// MARK: - Çember

/// Çember: x² + y² = r². Tutamak: yarıçapın ucu.
final class CircleModel: ParamModel {
    static let shared = CircleModel()
    private init() {
        super.init("sci.geo.circle", "Çember (yarıçap)", w: 1, h: 1, fit: .SQUARE,
                   tags: ["çember", "daire", "yarıçap", "çevre", "alan", "pi", "geometri", "formül"])
    }
    private static let D = [ParamDef("r", "r", "Yarıçap", default: 3, min: 0.1, max: 1000, step: 0.5)]
    override var defs: [ParamDef] { Self.D }
    private static let DV = [
        Derived("Ç", "Çevre", solveFor: "r") { 2 * .pi * $0["r"] },
        Derived("A", "Alan", solveFor: "r") { .pi * $0["r"] * $0["r"] },
        Derived("d", "Çap", solveFor: "r") { 2 * $0["r"] },
    ]
    override var derived: [Derived] { Self.DV }

    override func label(_ v: ParamValues) -> [String] {
        let r = v["r"]
        return ["x² + y² = r²", "r = \(n(r)); x² + y² = \(n(r * r))", "Ç = 2πr ≈ \(n(2 * .pi * r)); A = πr² ≈ \(n(.pi * r * r))"]
    }

    override func editText(_ raw: [String: String]?) -> String { let r = values(raw)["r"]; return "x² + y² = \(n(r * r, 4))" }

    private static let CX: Float = 0.5, CY: Float = 0.6, HALF: Float = 0.36, RAY_DEG: Float = -40

    private func extent(_ r: Double) -> Double { niceCeil(r * 1.15) }
    private func scale(_ v: ParamValues) -> Float { Self.HALF / Float(extent(v["r"])) }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let CX = Self.CX, CY = Self.CY, HALF = Self.HALF
        let e = extent(v["r"])
        let s = scale(v)
        let lead = Int(kround(e / pow(10.0, floor(log10(e)))))
        let ticks = lead == 2 ? 4 : 5
        let step = HALF / Float(ticks)
        for i in -ticks...ticks {
            let d = Float(i) * step
            b.line(CX + d, CY - HALF, CX + d, CY + HALF).stroke(GRID).width(0.5)
            b.line(CX - HALF, CY + d, CX + HALF, CY + d).stroke(GRID).width(0.5)
        }
        b.arrow(CX - HALF - 0.04, CY, CX + HALF + 0.05, CY, head: 0.03).width(0.9)
        b.arrow(CX, CY + HALF + 0.04, CX, CY - HALF - 0.05, head: 0.03).width(0.9)
        lb.c(CX + HALF + 0.05, CY + 0.045, "x", 0.05)
        lb.c(CX - 0.04, CY - HALF - 0.04, "y", 0.05)
        lb.c(CX + HALF, CY + 0.05, n(e), 0.045)
        let r = Float(v["r"] * Double(s))
        b.circle(CX, CY, r).fill(FILL).stroke(EDGE).width(1.6).tag("curve")
        let tip = onCircle(CX, CY, r, Self.RAY_DEG)
        b.line(CX, CY, tip.x, tip.y).width(1.1)
        b.dot(CX, CY, 0.014)
        lb.c(CX - 0.035, CY + 0.035, "O", 0.05)
        let m = onCircle(CX, CY, r / 2, Self.RAY_DEG - 12)
        lb.c(m.x, m.y, "r", 0.06)
        b.dot(tip.x, tip.y, 0.016, color: EDGE)
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        let tip = onCircle(Self.CX, Self.CY, Float(v["r"] * Double(scale(v))), Self.RAY_DEG)
        return [ParamHandle(key: "r", x: tip.x, y: tip.y)]
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        if key != "r" { return nil }
        let d = hypot(Double(x - Self.CX), Double(y - Self.CY)) / Double(scale(v))
        if d <= 0 { return nil }
        let p = pow(10.0, floor(log10(d)) - 1)
        return ["r": kround(d / p) * p]
    }

    /// "x² + y² = 16" gibi çember denklemi: merkezi orijinde çemberse r çıkarılır.
    override func apply(_ raw: [String: String]?, _ text: String) -> ParamApply {
        if text.contains(";") || text.contains("\n") { return super.apply(raw, text) }
        guard let rel = try? MathExpr.parseRelation(text) else { return super.apply(raw, text) }
        let vars = Set(rel.sides.flatMap { $0.variables() })
        if !vars.contains("x") || !vars.contains("y") { return super.apply(raw, text) }
        if rel.ops != [.EQ] || vars.contains(where: { $0 != "x" && $0 != "y" }) { return .failed(ParamError(code: .NO_MATCH)) }
        let diff = MNode.bin("-", rel.sides[0], rel.sides[1], implicit: false)
        func g(_ x: Double, _ y: Double) -> Double { (try? MathExpr.eval(diff, ["x": x, "y": y])) ?? .nan }
        let g0 = g(0, 0)
        if !g0.isFinite || g0 == 0 { return .failed(ParamError(code: .NO_MATCH)) }
        var hi = 0.0
        var t = 1e-3
        while t <= 2000 {
            let gt = g(t, 0)
            if gt.isFinite && (gt < 0) != (g0 < 0) { hi = t; break }
            t *= 1.5
        }
        if hi == 0 { return .failed(ParamError(code: .NO_MATCH)) }
        var lo = hi / 1.5
        if lo < 1e-3 { lo = 0 }
        for _ in 0..<80 { let m = (lo + hi) / 2; if (g(m, 0) < 0) == (g0 < 0) { lo = m } else { hi = m } }
        let r = (lo + hi) / 2
        let tol = 1e-6 * (1 + abs(g0))
        for deg in [90.0, 180.0, 37.0, 233.0, 300.0] where abs(g(r * cos(rad(deg)), r * sin(rad(deg)))) > tol {
            return .failed(ParamError(code: .NO_MATCH))
        }
        let d = def("r")!
        if r < d.min || r > d.max { return .failed(ParamError(code: .OUT_OF_RANGE, detail: "r")) }
        return .ok(withValue(raw, "r", r))
    }
}

// MARK: - Düzgün çokgen

/// Düzgün çokgen: iç açı α = (n − 2)·180°/n. Tutamak: ikinci köşe.
final class PolygonModel: ParamModel {
    static let shared = PolygonModel()
    private init() {
        super.init("sci.geo.polygon", "Düzgün çokgen", w: 1, h: 1, fit: .SQUARE,
                   tags: ["çokgen", "düzgün çokgen", "beşgen", "altıgen", "iç açı", "kenar", "geometri", "formül"])
    }
    private static let D = [
        ParamDef("n", "n", "Kenar sayısı", kind: .INT, default: 6, min: 3, max: 12, step: 1),
        ParamDef("a", "a", "Kenar uzunluğu", default: 4, min: 0.1, max: 1000, step: 0.5),
    ]
    override var defs: [ParamDef] { Self.D }
    private static func alpha(_ v: ParamValues) -> Double { (v["n"] - 2) * 180 / v["n"] }
    private static func area(_ v: ParamValues) -> Double { v["n"] * v["a"] * v["a"] / (4 * tan(.pi / v["n"])) }
    private static let DV = [
        Derived("α", "İç açı", unit: "°", solveFor: "n") { alpha($0) },
        Derived("Ç", "Çevre", solveFor: "a") { $0["n"] * $0["a"] },
        Derived("A", "Alan", solveFor: "a") { area($0) },
    ]
    override var derived: [Derived] { Self.DV }
    override func editText(_ raw: [String: String]?) -> String { let v = values(raw); return "n = \(Int(v["n"])); a = \(n(v["a"], 3))" }
    override func label(_ v: ParamValues) -> [String] {
        ["α = (n − 2)·180°/n",
         "n = \(Int(v["n"])); a = \(n(v["a"]))",
         "α = \(n(Self.alpha(v)))°; Ç = \(n(v["n"] * v["a"])); A ≈ \(n(Self.area(v)))"]
    }

    private static let CX: Float = 0.5, CY: Float = 0.6, R: Float = 0.33

    private func theta(_ v: ParamValues, _ k: Int) -> Float { let nn = v.f("n"); return 90 + 180 / nn + Float(k) * 360 / nn }
    private func vertex(_ v: ParamValues, _ k: Int) -> P { onCircle(Self.CX, Self.CY, Self.R, theta(v, k)) }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let nn = v.i("n")
        let pts = (0..<nn).map { vertex(v, $0) }
        b.points(pts, closed: true).fill(FILL).stroke(EDGE).width(1.5).tag("curve")
        b.circle(Self.CX, Self.CY, Self.R).dashed().width(0.5)
        for p in pts { b.dot(p.x, p.y, 0.012) }
        let p0 = pts[0], p1 = pts[1], pl = pts[nn - 1]
        let a1 = angleOf(p0.x, p0.y, pl.x, pl.y)
        let sw = wrapSweep(angleOf(p0.x, p0.y, p1.x, p1.y) - a1)
        b.arc(p0.x, p0.y, 0.06, 0.06, a1, sw).width(0.8).stroke(EDGE)
        let mid = onCircle(p0.x, p0.y, 0.11, a1 + sw / 2)
        lb.c(mid.x, mid.y, "α", 0.06, EDGE)
        let m = sciLerp(pl, p0, 0.5)
        lb.c(m.x, m.y + 0.05, "a", 0.065)
        b.dot(p1.x, p1.y, 0.016, color: EDGE)
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] { let p = vertex(v, 1); return [ParamHandle(key: "corner", x: p.x, y: p.y)] }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        if key != "corner" { return nil }
        if hypotf(x - Self.CX, y - Self.CY) < Self.R * 0.2 { return nil }
        var d = angleOf(Self.CX, Self.CY, x, y) - theta(v, 0)
        while d <= 0 { d += 360 }
        while d > 360 { d -= 360 }
        if d > 180 { return nil }
        return ["n": kround(360.0 / Double(d)).coerceIn(3, 12)]
    }
}

// MARK: - Parabol

/// Parabol y = ax² + bx + c. Tutamaklar: tepe ve tepeden bir birim sağdaki nokta.
final class ParabolaModel: ParamModel {
    static let shared = ParabolaModel()
    private init() {
        super.init("sci.math.parabola", "Parabol (y = ax² + bx + c)", w: 1.2, h: 1,
                   tags: ["parabol", "ikinci dereceden", "fonksiyon", "tepe noktası", "diskriminant", "grafik", "matematik", "formül"])
    }
    private static let D = [
        ParamDef("a", "a", "a katsayısı", default: 1, min: -20, max: 20, step: 0.5),
        ParamDef("b", "b", "b katsayısı", default: -2, min: -100, max: 100, step: 1),
        ParamDef("c", "c", "c katsayısı", default: -3, min: -1000, max: 1000, step: 1),
    ]
    override var defs: [ParamDef] { Self.D }
    private func xv(_ v: ParamValues) -> Double { -v["b"] / (2 * v["a"]) }
    private func yv(_ v: ParamValues) -> Double { v["c"] - v["b"] * v["b"] / (4 * v["a"]) }
    private static let DV = [Derived("Δ", "Diskriminant", solveFor: "c") { $0["b"] * $0["b"] - 4 * $0["a"] * $0["c"] }]
    override var derived: [Derived] { Self.DV }
    override func invalid(_ v: ParamValues) -> String? { abs(v["a"]) < 1e-9 ? "a" : nil }

    override var curveVar: String? { "y" }
    private func f(_ v: ParamValues) -> (Double) -> Double { let a = v["a"], b = v["b"], c = v["c"]; return { x in a * x * x + b * x + c } }
    override func curve(_ v: ParamValues) -> ((Double) -> Double)? { f(v) }

    override func fit(_ f: @escaping (Double) -> Double, _ v: ParamValues) -> [String: Double]? {
        let c = f(0), p = f(1), m = f(-1)
        if !c.isFinite || !p.isFinite || !m.isFinite { return nil }
        let a = (p + m - 2 * c) / 2, b = (p - m) / 2
        for x in [2.0, -3.0, 0.5, 7.0] where abs(f(x) - (a * x * x + b * x + c)) > 1e-6 * (1 + abs(f(x))) { return nil }
        if abs(a) < 1e-9 { return nil }
        return ["a": a, "b": b, "c": c]
    }

    override func curveText(_ v: ParamValues) -> String? { "y = " + polyText(v["a"], v["b"], v["c"]) }

    override func label(_ v: ParamValues) -> [String] {
        let d = v["b"] * v["b"] - 4 * v["a"] * v["c"]
        return ["y = ax² + bx + c", curveText(v)!, "T(\(n(xv(v))); \(n(yv(v)))); Δ = \(n(d))"]
    }

    private static let X0: Float = 0.1, X1: Float = 1.14, Y0: Float = 0.28, Y1: Float = 0.96
    private static let SPAN = 10.0

    private struct Win {
        let x0: Double, y0: Double
        func map(_ x: Double, _ y: Double) -> P {
            P(X0 + Float((x - x0) / SPAN * Double(X1 - X0)), Y1 - Float((y - y0) / SPAN * Double(Y1 - Y0)))
        }
        func unmap(_ p: P) -> (Double, Double) {
            (x0 + Double((p.x - X0) / (X1 - X0)) * SPAN, y0 + Double((Y1 - p.y) / (Y1 - Y0)) * SPAN)
        }
    }

    private func win(_ v: ParamValues) -> Win {
        let cx = kround(xv(v)), cy = kround(yv(v))
        return Win(x0: cx - Self.SPAN / 2, y0: v["a"] > 0 ? cy - 2 : cy - Self.SPAN + 2)
    }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        if invalid(v) != nil { return }
        let X0 = Self.X0, X1 = Self.X1, Y0 = Self.Y0, Y1 = Self.Y1, SPAN = Self.SPAN
        let w = win(v)
        for i in 0...Int(SPAN) {
            let gx = w.map(w.x0 + Double(i), w.y0), gy = w.map(w.x0, w.y0 + Double(i))
            b.line(gx.x, Y0, gx.x, Y1).stroke(GRID).width(0.4)
            b.line(X0, gy.y, X1, gy.y).stroke(GRID).width(0.4)
        }
        if (w.x0...(w.x0 + SPAN)).contains(0) { let p = w.map(0, w.y0); b.line(p.x, Y0, p.x, Y1).width(0.9); lb.c(p.x + 0.03, Y0 + 0.02, "y", 0.045) }
        if (w.y0...(w.y0 + SPAN)).contains(0) { let p = w.map(w.x0, 0); b.line(X0, p.y, X1, p.y).width(0.9); lb.c(X1 - 0.02, p.y - 0.035, "x", 0.045) }
        let fn = f(v)
        var run: [P] = []
        let samples = 240
        for i in 0...samples {
            let x = w.x0 + SPAN * Double(i) / Double(samples)
            let y = fn(x)
            if y >= w.y0 - 0.5 && y <= w.y0 + SPAN + 0.5 { run.append(w.map(x, y.coerceIn(w.y0, w.y0 + SPAN))) }
            else if !run.isEmpty { if run.count > 1 { b.points(run).stroke(EDGE).width(1.7).tag("curve") }; run = [] }
        }
        if run.count > 1 { b.points(run).stroke(EDGE).width(1.7).tag("curve") }
        let t = w.map(xv(v), yv(v))
        b.dot(t.x, t.y, 0.016, color: EDGE)
        lb.c(t.x + 0.045, t.y + (v["a"] > 0 ? 0.04 : -0.04), "T", 0.055, EDGE)
        let q = pointHandle(v)
        b.dot(q.x, q.y, 0.012)
    }

    private func pointHandle(_ v: ParamValues) -> P {
        let dx = min(1.0, (6.0 / abs(v["a"])).squareRoot())
        let x = xv(v) + dx
        return win(v).map(x, f(v)(x))
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        if invalid(v) != nil { return [] }
        let t = win(v).map(xv(v), yv(v)), q = pointHandle(v)
        return [ParamHandle(key: "vertex", x: t.x, y: t.y), ParamHandle(key: "point", x: q.x, y: q.y)]
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        let (wx, wy) = win(v).unmap(P(x, y))
        let a = v["a"]
        switch key {
        case "vertex":
            let h = kround(wx * 10) / 10, k = kround(wy * 10) / 10
            return ["b": -2 * a * h, "c": a * h * h + k]
        case "point":
            let h = xv(v), k = yv(v)
            let dx = wx - h
            if abs(dx) < 0.05 { return nil }
            let na = kround((wy - k) / (dx * dx) * 10) / 10
            if abs(na) < 1e-9 { return nil }
            return ["a": na, "b": -2 * na * h, "c": na * h * h + k]
        default: return nil
        }
    }
}

// MARK: - Biyoloji: lojistik büyüme

/// Lojistik nüfus büyümesi N(t) = K / (1 + ((K − N₀)/N₀)·e^(−rt)).
final class PopulationModel: ParamModel {
    static let shared = PopulationModel()
    private init() {
        super.init("sci.bio.population_growth", "Nüfus büyümesi (lojistik)", w: 1.6, h: 1,
                   tags: ["nüfus", "popülasyon", "büyüme eğrisi", "taşıma kapasitesi", "lojistik", "ekoloji", "biyoloji", "formül"])
    }
    private static let D = [
        ParamDef("N0", "N_0", "Başlangıç nüfusu", default: 10, min: 0.01, max: 1e9, step: 5),
        ParamDef("r", "r", "Büyüme hızı", default: 0.5, min: 0.01, max: 5, step: 0.1),
        ParamDef("K", "K", "Taşıma kapasitesi", default: 100, min: 1, max: 1e9, step: 10),
    ]
    override var defs: [ParamDef] { Self.D }
    override func invalid(_ v: ParamValues) -> String? { v["N0"] >= v["K"] ? "N_0" : nil }
    override var curveVar: String? { "N" }
    override var curveArg: String { "t" }
    private func f(_ v: ParamValues) -> (Double) -> Double {
        let k = v["K"], n0 = v["N0"], r = v["r"]
        return { t in k / (1 + (k - n0) / n0 * exp(-r * t)) }
    }
    override func curve(_ v: ParamValues) -> ((Double) -> Double)? { f(v) }
    private static func tHalf(_ v: ParamValues) -> Double { log((v["K"] - v["N0"]) / v["N0"]) / v["r"] }
    private static let DV = [Derived("t_m", "Büküm zamanı", solveFor: "r") { tHalf($0) }]
    override var derived: [Derived] { Self.DV }

    override func fit(_ f: @escaping (Double) -> Double, _ v: ParamValues) -> [String: Double]? {
        let n0 = f(0), k = f(4000)
        if !n0.isFinite || !k.isFinite || n0 <= 0 || k <= n0 || abs(f(8000) - k) > 1e-6 * k { return nil }
        let q = (k - n0) / n0
        let r = -log((k / f(1) - 1) / q)
        if !r.isFinite || r <= 0 { return nil }
        for t in [0.5, 2.0, 5.0, 11.0] where abs(f(t) - k / (1 + q * exp(-r * t))) > 1e-6 * k { return nil }
        return ["N0": n0, "K": k, "r": r]
    }

    override func curveText(_ v: ParamValues) -> String? {
        let q = (v["K"] - v["N0"]) / v["N0"]
        return "N = \(n(v["K"]))/(1 + \(n(q, 3))·e^(−\(n(v["r"], 3))t))"
    }
    override func editText(_ raw: [String: String]?) -> String {
        let v = values(raw)
        return "N₀ = \(n(v["N0"], 3)); r = \(n(v["r"], 3)); K = \(n(v["K"], 3))"
    }

    override func label(_ v: ParamValues) -> [String] {
        ["N = K / (1 + ((K − N₀)/N₀)·e^(−rt))", "N₀ = \(n(v["N0"])); r = \(n(v["r"], 3)); K = \(n(v["K"]))"]
    }

    private static let OX: Float = 0.14, OY: Float = 0.92, PW: Float = 1.36, PH: Float = 0.6

    private func tMax(_ v: ParamValues) -> Double { (max(log((v["K"] - v["N0"]) / v["N0"]), 0) + 5) / v["r"] }
    private func mapT(_ v: ParamValues, _ t: Double) -> Float { Self.OX + Float(t / tMax(v) * Double(Self.PW)) }
    private func mapN(_ v: ParamValues, _ nn: Double) -> Float { Self.OY - Float(nn / (1.1 * v["K"]) * Double(Self.PH)) }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        if invalid(v) != nil { return }
        let OX = Self.OX, OY = Self.OY, PW = Self.PW, PH = Self.PH
        b.arrow(OX, OY, OX + PW + 0.05, OY, head: 0.03).width(1)
        b.arrow(OX, OY, OX, OY - PH - 0.06, head: 0.03).width(1)
        lb.c(OX + PW + 0.05, OY + 0.045, "t", 0.055)
        lb.c(OX - 0.05, OY - PH - 0.05, "N", 0.055)
        let fn = f(v)
        let tm = tMax(v)
        let yk = mapN(v, v["K"])
        b.line(OX, yk, OX + PW, yk).dashed().width(0.7).stroke(Sc.RED)
        lb.c(OX - 0.04, yk, "K", 0.055, Sc.RED)
        b.curve(samples: 160) { s in let t = tm * Double(s); return P(self.mapT(v, t), self.mapN(v, fn(t))) }.stroke(Sc.GREEN).width(1.8).tag("curve")
        let y0 = mapN(v, v["N0"])
        b.dot(OX, y0, 0.014, color: Sc.GREEN)
        lb.c(OX - 0.06, y0, "N₀", 0.05)
        let th = Self.tHalf(v)
        if th > 0 {
            let p = P(mapT(v, th), mapN(v, v["K"] / 2))
            b.line(p.x, p.y, p.x, OY).dotted().width(0.6)
            b.dot(p.x, p.y, 0.012)
        }
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        if invalid(v) != nil { return [] }
        var out = [ParamHandle(key: "K", x: Self.OX + Self.PW * 0.9, y: mapN(v, v["K"])), ParamHandle(key: "N0", x: Self.OX, y: mapN(v, v["N0"]))]
        let th = Self.tHalf(v)
        if th > 0 { out.append(ParamHandle(key: "mid", x: mapT(v, th), y: mapN(v, v["K"] / 2))) }
        return out
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        let scaleN = 1.1 * v["K"] / Double(Self.PH)
        switch key {
        case "K":
            let k = Double(Self.OY - y) * scaleN
            return k > v["N0"] ? ["K": kround(k * 10) / 10] : nil
        case "N0":
            let n0 = Double(Self.OY - y) * scaleN
            return n0 > 0 && n0 < v["K"] ? ["N0": kround(n0 * 10) / 10] : nil
        case "mid":
            let t = Double((x - Self.OX) / Self.PW) * tMax(v)
            let l = log((v["K"] - v["N0"]) / v["N0"])
            return t <= 0.01 || l <= 0 ? nil : ["r": kround(l / t * 1000) / 1000]
        default: return nil
        }
    }
}
