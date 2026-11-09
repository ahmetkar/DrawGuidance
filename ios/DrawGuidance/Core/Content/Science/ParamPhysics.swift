import Foundation

// v45 (9) formülle çizilen fizik şekilleri (Android `ParamPhysics.kt`). Kimlikler eski sabit çizimlerle aynıdır.

fileprivate func n(_ v: Double, _ d: Int = 2) -> String { ParamModel.num(v, d) }
fileprivate func sq(_ x: Double) -> Double { x * x }

/// Eğik atış: y = x·tanθ − g·x²/(2v²·cos²θ). Tutamak: hız vektörünün ucu.
final class ProjectileModel: ParamModel {
    static let shared = ProjectileModel()
    private init() {
        super.init("sci.phys.projectile", "Eğik atış", w: 1.8, h: 1,
                   tags: ["atış", "yörünge", "menzil", "maksimum yükseklik", "hız", "mekanik", "formül"])
    }
    private static let D = [
        ParamDef("v", "v", "İlk hız", default: 20, min: 1, max: 100, step: 1, unit: "m/s"),
        ParamDef("theta", "θ", "Atış açısı", kind: .ANGLE, default: 55, min: 5, max: 85, step: 5, unit: "°"),
        ParamDef("g", "g", "Yer çekimi ivmesi", default: 9.8, min: 0.5, max: 30, step: 0.1, unit: "m/s²", advanced: true),
    ]
    override var defs: [ParamDef] { Self.D }
    private static func range(_ v: ParamValues) -> Double { v["v"] * v["v"] * sin(2 * rad(v["theta"])) / v["g"] }
    private static func hmax(_ v: ParamValues) -> Double { v["v"] * v["v"] * sq(sin(rad(v["theta"]))) / (2 * v["g"]) }
    private static let DV = [
        Derived("R", "Menzil", unit: "m", solveFor: "v") { range($0) },
        Derived("h", "En büyük yükseklik", unit: "m", solveFor: "v") { hmax($0) },
        Derived("t", "Uçuş süresi", unit: "s", solveFor: "v") { 2 * $0["v"] * sin(rad($0["theta"])) / $0["g"] },
    ]
    override var derived: [Derived] { Self.DV }
    override var curveVar: String? { "y" }
    private func f(_ v: ParamValues) -> (Double) -> Double {
        let th = rad(v["theta"]), sp = v["v"], g = v["g"]
        return { x in x * tan(th) - g * x * x / (2 * sp * sp * cos(th) * cos(th)) }
    }
    override func curve(_ v: ParamValues) -> ((Double) -> Double)? { f(v) }

    override func fit(_ f: @escaping (Double) -> Double, _ v: ParamValues) -> [String: Double]? {
        let s = max(1, Self.range(v) / 4)
        if abs(f(0)) > 1e-6 { return nil }
        let p = (4 * f(s) - f(2 * s)) / (2 * s)
        let q = (2 * f(s) - f(2 * s)) / (2 * s * s)
        for x in [0.5 * s, 3 * s] where abs(f(x) - (p * x - q * x * x)) > 1e-6 * (1 + abs(f(x))) { return nil }
        if q <= 0 || p <= 0 { return nil }
        let th = atan(p)
        let sp = (v["g"] / (2 * q * cos(th) * cos(th))).squareRoot()
        return ["theta": degOf(th), "v": sp]
    }

    override func curveText(_ v: ParamValues) -> String? {
        let p = tan(rad(v["theta"])), q = v["g"] / (2 * v["v"] * v["v"] * sq(cos(rad(v["theta"]))))
        return "y = \(n(p, 3))x − \(n(q, 4))x²"
    }

    override func label(_ v: ParamValues) -> [String] {
        ["y = x·tanθ − g·x²/(2v²·cos²θ)",
         "v = \(n(v["v"])) m/s; θ = \(n(v["theta"], 1))°",
         "R = \(n(Self.range(v), 1)) m; h = \(n(Self.hmax(v), 1)) m"]
    }

    private static let GY: Float = 0.86, OX: Float = 0.14, VEC0: Float = 0.12, VEC_PER: Float = 0.01

    private func scale(_ v: ParamValues) -> Double { min(1.5 / max(Self.range(v), 1e-6), 0.5 / max(Self.hmax(v), 1e-6)) }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let GY = Self.GY, OX = Self.OX
        let s = scale(v)
        let fn = f(v)
        let r = Self.range(v)
        let th = rad(v["theta"])
        b.line(0.04, GY, 1.76, GY).width(1.5)
        b.curve(samples: 140) { t in let x = r * Double(t); return P(OX + Float(x * s), GY - Float(fn(x) * s)) }.dashed().width(1).tag("curve")
        let len = Self.VEC0 + Self.VEC_PER * v.f("v")
        let tip = P(OX + len * Float(cos(th)), GY - len * Float(sin(th)))
        b.vec(OX, GY, tip.x, tip.y, head: 0.05)
        lb.c(tip.x + 0.06, tip.y - 0.02, "v₀", 0.07)
        b.angleArc(OX, GY, 0.1, -Float(degOf(th)), Float(degOf(th)))
        let pa = onCircle(OX, GY, 0.16, -Float(degOf(th)) / 2)
        lb.c(pa.x + 0.012, pa.y, "θ", 0.065)
        b.dot(OX, GY, 0.02)
        let px = OX + Float(r / 2 * s), py = GY - Float(Self.hmax(v) * s)
        b.line(px, py, px, GY).dashed().width(0.6)
        b.dot(px, py, 0.018)
        lb.c(px + 0.07, (py + GY) / 2, "h", 0.07)
        let end = OX + Float(r * s)
        b.dim(OX, 0.93, end, 0.93)
        lb.c((OX + end) / 2, 0.97, "R", 0.065)
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        let th = rad(v["theta"])
        let len = Self.VEC0 + Self.VEC_PER * v.f("v")
        return [ParamHandle(key: "v0", x: Self.OX + len * Float(cos(th)), y: Self.GY - len * Float(sin(th)))]
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        if key != "v0" { return nil }
        let dx = Double(x - Self.OX), dy = Double(Self.GY - y)
        if hypot(dx, dy) < 0.02 { return nil }
        return ["theta": degOf(atan2(dy, dx)), "v": (hypot(dx, dy) - Double(Self.VEC0)) / Double(Self.VEC_PER)]
    }
}

/// Enine dalga: y = A·sin(kx); λ = 2π/k. Tutamak: ilk tepe.
final class WaveModel: ParamModel {
    static let shared = WaveModel()
    private init() {
        super.init("sci.phys.transverse_wave", "Enine dalga", w: 2, h: 1,
                   tags: ["dalga", "dalga boyu", "genlik", "tepe", "çukur", "lambda", "sinüs", "formül"])
    }
    private static let D = [
        ParamDef("A", "A", "Genlik", default: 3, min: 0.2, max: 4.5, step: 0.5),
        ParamDef("k", "k", "Dalga sayısı", default: 1, min: 0.25, max: 6, step: 0.25),
    ]
    override var defs: [ParamDef] { Self.D }
    private static let DV = [Derived("λ", "Dalga boyu", solveFor: "k") { 2 * .pi / $0["k"] }]
    override var derived: [Derived] { Self.DV }
    override var curveVar: String? { "y" }
    private func f(_ v: ParamValues) -> (Double) -> Double { let a = v["A"], k = v["k"]; return { x in a * sin(k * x) } }
    override func curve(_ v: ParamValues) -> ((Double) -> Double)? { f(v) }

    private static let X_RANGE = 12.0
    override func fit(_ f: @escaping (Double) -> Double, _ v: ParamValues) -> [String: Double]? {
        if abs(f(0)) > 1e-6 { return nil }
        let steps = 4000
        var prev = f(1e-6)
        var x0 = Double.nan
        for i in 1...steps {
            let x = Self.X_RANGE * 2 * Double(i) / Double(steps)
            let y = f(x)
            if prev.isFinite && y.isFinite && (prev < 0) != (y < 0) {
                var a = x - Self.X_RANGE * 2 / Double(steps), b = x
                for _ in 0..<60 { let m = (a + b) / 2; if (f(m) < 0) == (f(a) < 0) { a = m } else { b = m } }
                x0 = (a + b) / 2
                break
            }
            prev = y
        }
        if x0.isNaN { return nil }
        let k = Double.pi / x0
        let a = f(.pi / (2 * k))
        if a <= 0 { return nil }
        for x in [0.37, 1.9, 4.4, 7.3] where abs(f(x) - a * sin(k * x)) > 1e-6 * (1 + a) { return nil }
        return ["A": a, "k": k]
    }

    override func curveText(_ v: ParamValues) -> String? { "y = \(n(v["A"]))·sin(\(n(v["k"], 3))x)" }
    override func label(_ v: ParamValues) -> [String] { ["y = A·sin(kx)", "A = \(n(v["A"])); k = \(n(v["k"], 2)); λ = \(n(2 * .pi / v["k"]))"] }

    private static let AX: Float = 0.56, X0: Float = 0.1
    private static let XS = 1.8 / X_RANGE, YS = 0.08

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let AX = Self.AX, X0 = Self.X0, XS = Self.XS, YS = Self.YS
        let fn = f(v)
        let a = v["A"], k = v["k"]
        b.line(0.04, AX, 1.96, AX).dashed().width(0.7)
        b.curve(samples: 240) { t in let x = Self.X_RANGE * Double(t); return P(X0 + Float(x * XS), AX - Float(fn(x) * YS)) }.width(1.6).tag("curve")
        let lam = 2 * Double.pi / k
        let c1 = Double.pi / (2 * k)
        let c2 = c1 + lam
        let top = AX - Float(a * YS)
        if c2 <= Self.X_RANGE {
            let xa = X0 + Float(c1 * XS), xb = X0 + Float(c2 * XS)
            b.line(xa, top - 0.08, xa, top - 0.01).dashed().width(0.6)
            b.line(xb, top - 0.08, xb, top - 0.01).dashed().width(0.6)
            b.dim(xa, top - 0.05, xb, top - 0.05)
            lb.c((xa + xb) / 2, top - 0.1, "λ", 0.07)
        }
        let ax: Float = 1.9
        b.dim(ax, AX, ax, AX + Float(a * YS))
        lb.c(ax - 0.06, AX + Float(a * YS / 2), "A", 0.07)
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        let c1 = Double.pi / (2 * v["k"])
        return [ParamHandle(key: "crest", x: Self.X0 + Float(c1 * Self.XS), y: Self.AX - Float(v["A"] * Self.YS))]
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        if key != "crest" { return nil }
        let xx = max(Double(x - Self.X0) / Self.XS, 0.05)
        return ["k": .pi / (2 * xx), "A": Double(Self.AX - y) / Self.YS]
    }
}

/// Yay–kütle: F = k·x. Tutamak: kütle.
final class SpringModel: ParamModel {
    static let shared = SpringModel()
    private init() {
        super.init("sci.phys.spring_mass", "Yay–kütle sistemi", w: 1.8, h: 0.9,
                   tags: ["yay", "esneklik", "hooke", "salınım", "kütle", "mekanik", "formül"])
    }
    private static let D = [
        ParamDef("k", "k", "Yay sabiti", default: 50, min: 1, max: 500, step: 5, unit: "N/m"),
        ParamDef("x", "x", "Uzama", default: 0.2, min: 0, max: 0.5, step: 0.05, unit: "m"),
    ]
    override var defs: [ParamDef] { Self.D }
    private static let DV = [Derived("F", "Yay kuvveti", unit: "N", solveFor: "x") { $0["k"] * $0["x"] }]
    override var derived: [Derived] { Self.DV }
    override var curveVar: String? { "F" }
    override func curve(_ v: ParamValues) -> ((Double) -> Double)? { let k = v["k"]; return { x in k * x } }
    override func fit(_ f: @escaping (Double) -> Double, _ v: ParamValues) -> [String: Double]? {
        let k = f(1)
        if abs(f(0)) > 1e-9 || abs(f(0.3) - 0.3 * k) > 1e-9 * (1 + abs(k)) { return nil }
        return ["k": k]
    }
    override func curveText(_ v: ParamValues) -> String? { "F = \(n(v["k"]))x" }
    override func editText(_ raw: [String: String]?) -> String { let v = values(raw); return "k = \(n(v["k"])); x = \(n(v["x"]))" }
    override func label(_ v: ParamValues) -> [String] { ["F = k·x", "k = \(n(v["k"])) N/m; x = \(n(v["x"])) m; F = \(n(v["k"] * v["x"])) N"] }
    override var labelRight: Bool { true }

    private static let FLOOR: Float = 0.72, REST: Float = 0.82, XS: Float = 0.9

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let FLOOR = Self.FLOOR, REST = Self.REST
        let bx = REST + Self.XS * v.f("x")
        b.hatch(0.1, FLOOR, 0.1, 0.1, side: -1, gap: 0.06, len: 0.045)
        b.hatch(0.1, FLOOR, 1.72, FLOOR, side: 1, gap: 0.06, len: 0.045)
        b.coil(0.1, 0.55, bx, 0.55, turns: 11, r: 0.075, lead: 0.07, tilt: 0.4)
        b.rect(bx, 0.38, 0.34, FLOOR - 0.38).fill(Sc.BLOCK).width(1.2)
        lb.c(bx + 0.17, 0.55, "m", 0.09)
        lb.c((0.1 + bx) / 2, 0.4, "k", 0.075)
        b.line(REST, 0.22, REST, FLOOR).dashed().width(0.7)
        if v["x"] > 0.005 {
            b.vec(REST, 0.29, bx, 0.29, head: 0.03, width: 0.9)
            lb.c((REST + bx) / 2, 0.23, "x", 0.07)
            let fl = min(0.02 + 0.0012 * Float(v["k"] * v["x"]), 0.3)
            b.vec(bx + 0.34, 0.47, bx + 0.34 - fl - 0.04, 0.47, head: 0.035, color: Sc.RED, width: 1)
            lb.c(bx + 0.34 - fl / 2 - 0.02, 0.43, "F", 0.065, Sc.RED)
        }
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] { [ParamHandle(key: "mass", x: Self.REST + Self.XS * v.f("x") + 0.17, y: 0.55)] }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        key == "mass" ? ["x": Double((x - 0.17 - Self.REST) / Self.XS)] : nil
    }
}

/// Basit sarkaç: T = 2π·√(L/g). Tutamak: top.
final class PendulumModel: ParamModel {
    static let shared = PendulumModel()
    private init() {
        super.init("sci.phys.pendulum", "Basit sarkaç", w: 1, h: 1, tags: ["sarkaç", "salınım", "periyot", "genlik", "mekanik", "formül"])
    }
    private static let D = [
        ParamDef("L", "L", "İp uzunluğu", default: 2, min: 0.2, max: 2.2, step: 0.1, unit: "m"),
        ParamDef("theta", "θ", "Genlik açısı", kind: .ANGLE, default: 32, min: 0, max: 70, step: 2, unit: "°"),
        ParamDef("g", "g", "Yer çekimi ivmesi", default: 9.8, min: 0.5, max: 30, step: 0.1, unit: "m/s²", advanced: true),
    ]
    override var defs: [ParamDef] { Self.D }
    private static let DV = [Derived("T", "Periyot", unit: "s", solveFor: "L") { 2 * .pi * ($0["L"] / $0["g"]).squareRoot() }]
    override var derived: [Derived] { Self.DV }
    override func label(_ v: ParamValues) -> [String] {
        ["T = 2π·√(L/g)", "L = \(n(v["L"])) m; θ = \(n(v["theta"], 0))°", "T = \(n(2 * .pi * (v["L"] / v["g"]).squareRoot())) s"]
    }
    override var labelRight: Bool { true }
    override var labelSize: Float { 0.055 }

    private static let PX: Float = 0.5, PY: Float = 0.1, LS: Float = 0.34, BR: Float = 0.055

    private func scale(_ v: ParamValues) -> Float {
        let l = Self.LS * v.f("L")
        let sx = (Self.PX - Self.BR - 0.02) / max(l * Float(sin(rad(v["theta"]))), 1e-4)
        let sy = (1 - Self.BR - 0.03 - Self.PY) / l
        return min(1, sx, sy)
    }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let PX = Self.PX, PY = Self.PY, BR = Self.BR
        let l = Self.LS * v.f("L") * scale(v), amp = v.f("theta")
        b.hatch(PX - 0.15, PY, PX + 0.15, PY, side: -1, gap: 0.05, len: 0.04)
        if amp > 0.5 { b.arc(PX, PY, l, l, 90 - amp, 2 * amp).dotted().width(0.9) }
        b.line(PX, PY, PX, PY + l + BR + 0.03).dashed().width(0.7)
        func bob(_ phi: Float) -> P { P(PX + l * Float(sin(rad(Double(phi)))), PY + l * Float(cos(rad(Double(phi))))) }
        let left = bob(-amp)
        b.line(PX, PY, left.x, left.y).dashed().width(0.8)
        b.circle(left.x, left.y, BR).dashed().width(0.8)
        let right = bob(amp)
        b.line(PX, PY, right.x, right.y).width(1.1)
        b.circle(right.x, right.y, BR).fill("#90A4AE").width(1.2)
        b.shine(right.x, right.y, BR)
        b.dot(PX, PY, 0.014)
        if amp > 3 {
            b.angleArc(PX, PY, 0.14, 90 - amp, amp)
            let pt = onCircle(PX, PY, 0.2, 90 - amp / 2)
            lb.c(pt.x, pt.y, "θ", 0.065)
        }
        let mid = P((PX + right.x) / 2 + 0.05, (PY + right.y) / 2 - 0.03)
        lb.c(mid.x, mid.y, "L", 0.065)
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        let l = Self.LS * v.f("L") * scale(v), a = rad(v["theta"])
        return [ParamHandle(key: "bob", x: Self.PX + l * Float(sin(a)), y: Self.PY + l * Float(cos(a)))]
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        if key != "bob" { return nil }
        let dx = Double(x - Self.PX), dy = Double(y - Self.PY)
        if dy <= 0.01 { return nil }
        return ["L": hypot(dx, dy) / Double(Self.LS * scale(v)), "theta": degOf(atan2(dx, dy))]
    }
}

/// İnce mercek: 1/f = 1/do + 1/di. Tutamaklar: cisim (do) ve odak (f).
final class LensModel: ParamModel {
    static let convex = LensModel("sci.phys.convex_lens", "Yakınsak mercek", converging: true, w: 1.8, kind: "ince kenarlı mercek")
    static let concave = LensModel("sci.phys.concave_lens", "Iraksak mercek", converging: false, w: 1.6, kind: "kalın kenarlı mercek")

    private let converging: Bool
    private init(_ id: String, _ title: String, converging: Bool, w: Float, kind: String) {
        self.converging = converging
        super.init(id, title, w: w, h: 1, tags: [kind, "mercek", "odak", "ışın", "ışık", "kırılma", "görüntü", "optik", "formül"])
    }
    private static let D = [
        ParamDef("f", "f", "Odak uzaklığı", default: 10, min: 2, max: 40, step: 1, unit: "cm"),
        ParamDef("do", "do", "Cismin uzaklığı", default: 25, min: 1, max: 80, step: 1, unit: "cm"),
    ]
    override var defs: [ParamDef] { Self.D }
    private func fs(_ v: ParamValues) -> Double { converging ? v["f"] : -v["f"] }
    func di(_ v: ParamValues) -> Double {
        let inv = 1 / fs(v) - 1 / v["do"]
        return abs(inv) < 1e-9 ? .infinity : 1 / inv
    }
    private lazy var dv: [Derived] = [
        Derived("di", "Görüntünün uzaklığı", unit: "cm", solveFor: "do") { [unowned self] in self.di($0) },
        Derived("m", "Büyütme", solveFor: "do") { [unowned self] in -self.di($0) / $0["do"] },
    ]
    override var derived: [Derived] { dv }
    override func label(_ v: ParamValues) -> [String] {
        let d = di(v)
        return ["1/f = 1/do + 1/di",
                "f = \(n(fs(v), 1)) cm; do = \(n(v["do"], 1)) cm",
                d.isInfinite ? "di = ∞" : "di = \(n(d, 1)) cm; m = \(n(-d / v["do"]))"]
    }
    override var labelSize: Float { 0.055 }
    private var cx: Float { w / 2 }
    private let ay: Float = 0.62
    private let ho: Float = 0.2

    private func scale(_ v: ParamValues) -> Float {
        let d = di(v)
        let extent = max(max(v["do"], v["f"] * 1.25), d.isFinite ? min(abs(d), 3 * v["do"]) : 0)
        return Float(Double(w / 2 - 0.1) / extent)
    }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let cx = self.cx, ay = self.ay, ho = self.ho, w = self.w
        let s = scale(v)
        let f = v.f("f"), dob = v.f("do")
        let d = di(v)
        b.line(0.04, ay, w - 0.04, ay).width(0.8)
        if converging {
            b.path(closed: true) { p in p.moveTo(cx, ay - 0.36); p.quadTo(cx + 0.09, ay, cx, ay + 0.36); p.quadTo(cx - 0.09, ay, cx, ay - 0.36) }
                .fill(Sc.LENS).width(1.2)
        } else {
            b.path(closed: true) { p in
                p.moveTo(cx - 0.06, ay - 0.36); p.lineTo(cx + 0.06, ay - 0.36); p.quadTo(cx + 0.01, ay, cx + 0.06, ay + 0.36)
                p.lineTo(cx - 0.06, ay + 0.36); p.quadTo(cx - 0.01, ay, cx - 0.06, ay - 0.36)
            }.fill(Sc.LENS).width(1.2)
        }
        for sg: Float in [-1, 1] {
            let fx = cx + sg * f * s
            if fx > 0.02 && fx < w - 0.02 { b.dot(fx, ay, 0.013); lb.c(fx, ay + 0.05, sg < 0 ? "F" : "F′", 0.06) }
        }
        let ox = cx - dob * s
        b.vec(ox, ay, ox, ay - ho, head: 0.035, color: Sc.BLUE, width: 1.4)
        lb.c(ox, ay + 0.05, "do", 0.055)
        let top = P(ox, ay - ho)
        if d.isFinite {
            let m = Float(-d / v["do"])
            let ix = Float(Double(cx) + d * Double(s))
            let ih = (ho * m).coerceIn(-0.55, 0.55)
            let real = d > 0
            let img = b.vec(ix, ay, ix, ay - ih, head: 0.035, color: "#8E24AA", width: 1.4)
            if !real { img.dashed() }
            let lensTop = P(cx, top.y)
            b.line(top.x, top.y, lensTop.x, lensTop.y).stroke(Sc.RAY).width(1)
            let toImg = P(ix, ay - ih)
            if real {
                b.line(lensTop.x, lensTop.y, toImg.x, toImg.y).stroke(Sc.RAY).width(1)
                b.line(top.x, top.y, toImg.x, toImg.y).stroke(Sc.RAY).width(0.8)
            } else {
                let dir = sciUnit(lensTop.x - toImg.x, lensTop.y - toImg.y)
                b.line(lensTop.x, lensTop.y, lensTop.x + dir.x * 0.5, lensTop.y + dir.y * 0.5).stroke(Sc.RAY).width(1)
                b.line(lensTop.x, lensTop.y, toImg.x, toImg.y).stroke(Sc.RAY).width(0.7).dashed()
                let dir2 = sciUnit(cx - top.x, ay - top.y)
                b.line(top.x, top.y, cx + dir2.x * 0.5, ay + dir2.y * 0.5).stroke(Sc.RAY).width(0.8)
                b.line(cx, ay, toImg.x, toImg.y).stroke(Sc.RAY).width(0.7).dashed()
            }
            lb.c(ix, ay + 0.05, "di", 0.055)
        } else {
            b.line(top.x, top.y, cx, top.y).stroke(Sc.RAY).width(1)
            b.line(cx, top.y, w - 0.05, top.y + (w / 2 - 0.05) * ho / max(1e-3, f * s)).stroke(Sc.RAY).width(1)
        }
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        let s = scale(v)
        return [ParamHandle(key: "object", x: cx - v.f("do") * s, y: ay - ho), ParamHandle(key: "focus", x: cx + v.f("f") * s, y: ay)]
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        let s = scale(v)
        switch key {
        case "object": return ["do": Double((cx - x) / s)]
        case "focus": return ["f": Double(abs(x - cx) / s)]
        default: return nil
        }
    }
}

/// Eğik düzlem: a = g·(sinθ − μ·cosθ). Tutamak: düzlemin tepesi.
final class InclineModel: ParamModel {
    static let shared = InclineModel()
    private init() {
        super.init("sci.phys.inclined_plane", "Eğik düzlem", w: 1.6, h: 1,
                   tags: ["kuvvet", "sürtünme", "ağırlık", "normal kuvvet", "blok", "mekanik", "formül"])
    }
    private static let D = [
        ParamDef("theta", "θ", "Eğim açısı", kind: .ANGLE, default: 30, min: 5, max: 60, step: 5, unit: "°"),
        ParamDef("mu", "μ", "Sürtünme katsayısı", default: 0, min: 0, max: 1, step: 0.05),
        ParamDef("m", "m", "Kütle", default: 2, min: 0.1, max: 100, step: 0.5, unit: "kg"),
        ParamDef("g", "g", "Yer çekimi ivmesi", default: 9.8, min: 0.5, max: 30, step: 0.1, unit: "m/s²", advanced: true),
    ]
    override var defs: [ParamDef] { Self.D }
    private static func acc(_ v: ParamValues) -> Double { max(0, v["g"] * (sin(rad(v["theta"])) - v["mu"] * cos(rad(v["theta"])))) }
    private static let DV = [
        Derived("a", "İvme", unit: "m/s²", solveFor: "theta") { acc($0) },
        Derived("N", "Normal kuvvet", unit: "N", solveFor: "theta") { $0["m"] * $0["g"] * cos(rad($0["theta"])) },
    ]
    override var derived: [Derived] { Self.DV }
    override func label(_ v: ParamValues) -> [String] {
        ["a = g·(sinθ − μ·cosθ)", "θ = \(n(v["theta"], 0))°; μ = \(n(v["mu"])); a = \(n(Self.acc(v))) m/s²"]
    }
    override var labelSize: Float { 0.055 }

    private static let p0 = P(0.1, 0.9)
    private static let BASE: Float = 1.35

    private func baseLen(_ th: Double) -> Float { Float(min(Double(Self.BASE), 0.72 / tan(rad(th)))) }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let p0 = Self.p0
        let th = v["theta"]
        let base = baseLen(th)
        let top = P(p0.x + base, Float(0.9 - Double(base) * tan(rad(th))))
        b.hatch(0.02, 0.9, 1.58, 0.9, side: 1, gap: 0.06, len: 0.045)
        b.poly(p0.x, p0.y, top.x, 0.9, top.x, top.y, closed: true).fill(Sc.GROUND).width(1.3)
        let u = P(Float(cos(rad(th))), -Float(sin(rad(th))))
        let nv = P(u.y, -u.x)
        let along = min(0.72, hypotf(top.x - p0.x, top.y - p0.y) * 0.55)
        let c = P(p0.x + u.x * along + nv.x * 0.1, p0.y + u.y * along + nv.y * 0.1)
        b.group(rotate: -Float(th), px: c.x, py: c.y) { b.rect(c.x - 0.1, c.y - 0.1, 0.2, 0.2).fill(Sc.BLOCK).width(1.2) }
        let g: Float = 0.3
        b.vec(c.x, c.y, c.x, c.y + g, head: 0.045)
        let nlen = 0.3 * Float(cos(rad(th)))
        b.vec(c.x, c.y, c.x + nv.x * nlen, c.y + nv.y * nlen, head: 0.045)
        let fpar = 0.3 * Float(sin(rad(th)))
        b.vec(c.x, c.y, c.x - u.x * fpar, c.y - u.y * fpar, head: 0.04, color: Sc.RED, width: 1)
        b.dot(c.x, c.y, 0.012)
        lb.c(c.x + 0.05, c.y + g + 0.01, "G", 0.065)
        lb.c(c.x + nv.x * nlen - 0.04, c.y + nv.y * nlen + 0.01, "N", 0.065)
        b.angleArc(p0.x, p0.y, 0.2, -Float(th), Float(th))
        let pt = onCircle(p0.x, p0.y, 0.28, -Float(th) / 2.4)
        lb.c(pt.x, pt.y, "θ", 0.065)
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        let th = v["theta"]
        let base = baseLen(th)
        return [ParamHandle(key: "top", x: Self.p0.x + base, y: Float(0.9 - Double(base) * tan(rad(th))))]
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        if key != "top" { return nil }
        let dx = max(Double(x - Self.p0.x), 0.05), dy = max(Double(Self.p0.y - y), 0)
        return ["theta": degOf(atan2(dy, dx))]
    }
}

/// Ohm yasası grafiği: V = I·R. Tutamak: doğrunun ucu.
final class OhmModel: ParamModel {
    static let shared = OhmModel()
    private init() {
        super.init("sci.phys.ohm_graph", "Ohm yasası grafiği (V = I·R)", w: 1.3, h: 1,
                   tags: ["ohm", "direnç", "gerilim", "akım", "elektrik", "grafik", "formül"])
    }
    private static let D = [
        ParamDef("R", "R", "Direnç", default: 10, min: 0.5, max: 100, step: 1, unit: "Ω"),
        ParamDef("Imax", "I_max", "En büyük akım", default: 2, min: 0.5, max: 10, step: 0.5, unit: "A"),
    ]
    override var defs: [ParamDef] { Self.D }
    private static let DV = [Derived("V", "Gerilim (I_max'ta)", unit: "V", solveFor: "R") { $0["R"] * $0["Imax"] }]
    override var derived: [Derived] { Self.DV }
    override var curveVar: String? { "V" }
    override var curveArg: String { "I" }
    override func curve(_ v: ParamValues) -> ((Double) -> Double)? { let r = v["R"]; return { i in r * i } }
    override func fit(_ f: @escaping (Double) -> Double, _ v: ParamValues) -> [String: Double]? {
        let r = f(1)
        if abs(f(0)) > 1e-9 || abs(f(2.5) - 2.5 * r) > 1e-9 * (1 + abs(r)) { return nil }
        return ["R": r]
    }
    override func curveText(_ v: ParamValues) -> String? { "V = \(n(v["R"]))·I" }
    override func label(_ v: ParamValues) -> [String] { ["V = I·R", "R = \(n(v["R"])) Ω"] }
    override var labelRight: Bool { true }

    private static let L: Float = 0.18, B: Float = 0.86, R_: Float = 1.2, T: Float = 0.1

    private func vmax(_ v: ParamValues) -> Double { Fmt2.nice(v["R"] * v["Imax"] * 1.1) }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let L = Self.L, B = Self.B, R_ = Self.R_, T = Self.T
        let imax = v["Imax"], vm = vmax(v)
        b.vec(L, B, R_ + 0.06, B, head: 0.035, width: 1)
        b.vec(L, B, L, T - 0.04, head: 0.035, width: 1)
        lb.c(R_ + 0.05, B + 0.05, "I (A)", 0.055)
        lb.c(L - 0.02, T - 0.08, "V (V)", 0.055)
        func X(_ i: Double) -> Float { Float(Double(L) + (i / imax) * Double(R_ - L - 0.08)) }
        func Y(_ u: Double) -> Float { Float(Double(B) - (u / vm) * Double(B - T)) }
        for k in 1...4 {
            let i = imax * Double(k) / 4
            b.line(X(i), B - 0.012, X(i), B + 0.012).width(0.8)
            lb.c(X(i), B + 0.045, n(i), 0.045)
            let u = vm * Double(k) / 4
            b.line(L - 0.012, Y(u), L + 0.012, Y(u)).width(0.8)
            lb.r(L - 0.02, Y(u), n(u, 1), 0.045)
        }
        b.line(X(0), Y(0), X(imax), Y(v["R"] * imax)).stroke(Sc.BLUE).width(1.8).tag("curve")
        b.dot(X(imax), Y(v["R"] * imax), 0.014, color: Sc.BLUE)
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        let imax = v["Imax"], vm = vmax(v)
        return [ParamHandle(key: "end", x: Self.L + (Self.R_ - Self.L - 0.08), y: Float(Double(Self.B) - (v["R"] * imax / vm) * Double(Self.B - Self.T)))]
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        if key != "end" { return nil }
        let u = Double((Self.B - y) / (Self.B - Self.T)) * vmax(v)
        return ["R": u / v["Imax"]]
    }
}

/// Eksen ölçeği için yuvarlak sayılar.
enum Fmt2 {
    static func nice(_ x: Double) -> Double {
        if x <= 0 { return 1 }
        let e = pow(10.0, floor(log10(x)))
        let m = x / e
        let k: Double = m <= 1 ? 1 : m <= 2 ? 2 : m <= 2.5 ? 2.5 : m <= 4 ? 4 : m <= 5 ? 5 : 10
        return k * e
    }
}
