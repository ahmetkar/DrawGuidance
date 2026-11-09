import Foundation

// v46 (4) formülle yeniden çizilen basit makineler ve serbest cisim diyagramı (Android `ParamMechanics.kt`).

fileprivate func n(_ v: Double, _ d: Int = 2) -> String { ParamModel.num(v, d) }

/// Serbest cisim diyagramı: yatay zeminde blok; F·cosα − Fs = m·a.
final class FreeBodyModel: ParamModel {
    static let shared = FreeBodyModel()
    private init() {
        super.init("sci.phys.free_body", "Serbest cisim diyagramı", w: 1.3, h: 1,
                   tags: ["kuvvet", "normal kuvvet", "ağırlık", "sürtünme", "net kuvvet", "ivme", "mekanik", "formül"])
    }
    private static let D = [
        ParamDef("m", "m", "Kütle", default: 2, min: 0.1, max: 500, step: 0.5, unit: "kg"),
        ParamDef("F", "F", "Uygulanan kuvvet", default: 15, min: 0, max: 5000, step: 1, unit: "N"),
        ParamDef("alpha", "α", "Kuvvetin açısı", kind: .ANGLE, default: 0, min: -80, max: 80, step: 5, unit: "°"),
        ParamDef("mu", "μ", "Sürtünme katsayısı", default: 0.3, min: 0, max: 1.5, step: 0.05),
        ParamDef("g", "g", "Yer çekimi ivmesi", default: 9.8, min: 0.5, max: 30, step: 0.1, unit: "m/s²", advanced: true),
    ]
    override var defs: [ParamDef] { Self.D }

    struct Forces { let g: Double, n: Double, fx: Double, fy: Double, fs: Double, a: Double, moving: Bool }

    static func forces(_ v: ParamValues) -> Forces {
        let g = v["m"] * v["g"]
        let fx = v["F"] * cos(rad(v["alpha"]))
        let fy = v["F"] * sin(rad(v["alpha"]))
        let nf = max(0, g - fy)
        let fsMax = v["mu"] * nf
        let moving = fx > fsMax + 1e-9
        let fs = moving ? fsMax : fx
        let a = moving ? (fx - fs) / v["m"] : 0
        return Forces(g: g, n: nf, fx: fx, fy: fy, fs: fs, a: a, moving: moving)
    }

    private static let DV = [
        Derived("G", "Ağırlık", unit: "N", solveFor: "m") { $0["m"] * $0["g"] },
        Derived("N", "Normal kuvvet", unit: "N", solveFor: "m") { forces($0).n },
        Derived("Fs", "Sürtünme kuvveti", unit: "N", solveFor: "mu") { forces($0).fs },
        Derived("a", "İvme", unit: "m/s²", solveFor: "F") { forces($0).a },
    ]
    override var derived: [Derived] { Self.DV }

    override func label(_ v: ParamValues) -> [String] {
        let f = Self.forces(v)
        return ["F·cosα − Fs = m·a", "G = \(n(f.g)) N; N = \(n(f.n)) N", "Fs = \(n(f.fs)) N; a = \(n(f.a)) m/s²"]
    }
    override var labelSize: Float { 0.05 }

    private static let CX: Float = 0.7, CY: Float = 0.6, GROUND: Float = 0.72, MAXLEN: Float = 0.3

    private func scale(_ f: Forces, _ v: ParamValues) -> Float { Float(Double(Self.MAXLEN) / max(f.g, f.n, f.fs, v["F"], 1e-6)) }

    private func fTip(_ v: ParamValues) -> P {
        let f = Self.forces(v), s = scale(f, v)
        let len = Float(v["F"] * Double(s))
        let a = rad(v["alpha"])
        return P(Self.CX + 0.2 + len * Float(cos(a)), Self.CY - len * Float(sin(a)))
    }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let CX = Self.CX, CY = Self.CY, GROUND = Self.GROUND
        let f = Self.forces(v), s = scale(f, v)
        b.hatch(0.08, GROUND, 1.22, GROUND, side: 1, gap: 0.06, len: 0.045)
        b.rect(CX - 0.2, CY - 0.12, 0.4, 0.24).fill(Sc.BLOCK).width(1.2)
        lb.c(CX - 0.12, CY, "m", 0.07)
        func arrow(_ x1: Float, _ y1: Float, _ len: Float, _ ux: Float, _ uy: Float, _ label: String, _ color: String? = nil) {
            if len < 0.02 { return }
            b.vec(x1, y1, x1 + ux * len, y1 + uy * len, head: 0.04, color: color, width: 1.2)
            lb.c(x1 + ux * (len + 0.05) + (ux == 0 ? 0.07 : 0), y1 + uy * (len + 0.05), label, 0.06, color)
        }
        arrow(CX - 0.05, CY, Float(f.g * Double(s)), 0, 1, "G")
        arrow(CX + 0.05, CY - 0.12, Float(f.n * Double(s)), 0, -1, "N", Sc.BLUE)
        if v["F"] > 1e-9 {
            let tip = fTip(v)
            b.vec(CX + 0.2, CY, tip.x, tip.y, head: 0.04, color: Sc.RED, width: 1.3)
            lb.c(tip.x + 0.04, tip.y - 0.05, "F", 0.06, Sc.RED)
            if abs(v["alpha"]) > 2 {
                b.line(CX + 0.2, CY, CX + 0.36, CY).dashed().width(0.6)
                b.angleArc(CX + 0.2, CY, 0.09, v["alpha"] > 0 ? -v.f("alpha") : 0, abs(v.f("alpha")))
                lb.c(CX + 0.36, CY + (v["alpha"] > 0 ? -0.04 : 0.05), "α", 0.05)
            }
        }
        arrow(CX - 0.2, GROUND - 0.02, Float(f.fs * Double(s)), -1, 0, "Fs", Sc.GREEN)
        b.dot(CX, CY, 0.012)
        if f.moving {
            b.vec(CX - 0.2, CY - 0.2, CX + 0.02, CY - 0.2, head: 0.035, width: 0.9)
            lb.c(CX - 0.09, CY - 0.26, "a", 0.055)
        }
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] { let t = fTip(v); return [ParamHandle(key: "force", x: t.x, y: t.y)] }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        if key != "force" { return nil }
        let dx = Double(x - Self.CX - 0.2), dy = Double(Self.CY - y)
        let len = hypot(dx, dy)
        if len < 0.01 || dx <= 0 { return nil }
        let s = Double(scale(Self.forces(v), v))
        return ["F": len / s, "alpha": degOf(atan2(dy, dx)).coerceIn(-80, 80)]
    }
}

fileprivate extension VectorBuilder {
    /// Makara tekerleği: gövde, oluk ve mil.
    func pulleyWheel(_ cx: Float, _ cy: Float, _ r: Float) {
        circle(cx, cy, r).fill("#E3E7EC").width(1.2)
        circle(cx, cy, r * 0.78).width(0.6)
        circle(cx, cy, r * 0.2).fill(Sc.METAL_DARK).width(0.8)
    }
}

/// Yükün çizim boyu: ağırlıkla (karekökle) büyür, kutuya sığar.
fileprivate func blockSize(_ p: Double) -> Float { (0.14 + 0.05 * Float((p / 100).squareRoot())).coerceIn(0.12, 0.3) }

/// Sabit makara: F = P.
final class FixedPulleyModel: ParamModel {
    static let shared = FixedPulleyModel()
    private init() {
        super.init("sci.phys.fixed_pulley", "Sabit makara", w: 1, h: 1.15, tags: ["makara", "basit makine", "ip", "kuvvet", "yük", "formül"])
    }
    private static let D = [
        ParamDef("P", "P", "Yükün ağırlığı", default: 100, min: 1, max: 10000, step: 10, unit: "N"),
        ParamDef("h", "h", "Yükselme", default: 1, min: 0, max: 100, step: 0.5, unit: "m"),
    ]
    override var defs: [ParamDef] { Self.D }
    private static let DV = [
        Derived("F", "Kuvvet", unit: "N", solveFor: "P") { $0["P"] },
        Derived("s", "Çekilen ip", unit: "m", solveFor: "h") { $0["h"] },
    ]
    override var derived: [Derived] { Self.DV }
    override func label(_ v: ParamValues) -> [String] { ["F = P", "F = \(n(v["P"])) N; ip = \(n(v["h"])) m"] }
    override var labelSize: Float { 0.045 }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let cx: Float = 0.5, top: Float = 0.16, cy: Float = 0.38, r: Float = 0.15
        b.hatch(0.3, top, 0.7, top, side: -1, gap: 0.05, len: 0.04)
        b.bar(cx, top, cx, cy, 0.03).fill(Sc.METAL).width(1)
        b.pulleyWheel(cx, cy, r)
        b.arc(cx, cy, r, r, 180, 180).width(1.5)
        let bs = blockSize(v["P"])
        let by: Float = 0.82
        b.line(cx - r, cy, cx - r, by).width(1.5)
        b.line(cx + r, cy, cx + r, 0.84).width(1.5)
        let len: Float = 0.2
        b.vec(cx + r, 0.84, cx + r, 0.84 + len, head: 0.05, color: Sc.RED)
        lb.c(cx + r + 0.08, 0.96, "F", 0.08, Sc.RED)
        b.rect(cx - r - bs / 2, by, bs, bs).fill(Sc.BLOCK).width(1.2)
        lb.c(cx - r, by + bs / 2, "P", 0.07)
        if v["h"] > 0.01 {
            let rise = bs + 0.04
            b.rect(cx - r - bs / 2, by - rise, bs, bs).dashed().width(0.6)
            b.dim(cx - r - bs / 2 - 0.06, by + bs, cx - r - bs / 2 - 0.06, by + bs - rise)
            lb.c(cx - r - bs / 2 - 0.12, by + bs - rise / 2, "h", 0.06)
        }
    }
}

/// Hareketli makara (palanga): F = (P + Pm)/2.
final class MovablePulleyModel: ParamModel {
    static let shared = MovablePulleyModel()
    private init() {
        super.init("sci.phys.movable_pulley", "Hareketli makara (palanga)", w: 1, h: 1.15,
                   tags: ["makara", "palanga", "basit makine", "ip", "kuvvet", "yük", "formül"])
    }
    private static let D = [
        ParamDef("P", "P", "Yükün ağırlığı", default: 100, min: 1, max: 10000, step: 10, unit: "N"),
        ParamDef("Pm", "Pm", "Makaranın ağırlığı", default: 0, min: 0, max: 1000, step: 5, unit: "N"),
        ParamDef("h", "h", "Yükselme", default: 1, min: 0, max: 100, step: 0.5, unit: "m"),
    ]
    override var defs: [ParamDef] { Self.D }
    static func force(_ v: ParamValues) -> Double { (v["P"] + v["Pm"]) / 2 }
    private static let DV = [
        Derived("F", "Kuvvet", unit: "N", solveFor: "P") { force($0) },
        Derived("s", "Çekilen ip", unit: "m", solveFor: "h") { 2 * $0["h"] },
    ]
    override var derived: [Derived] { Self.DV }
    override func label(_ v: ParamValues) -> [String] { ["F = (P + Pm)/2", "F = \(n(Self.force(v))) N; ip = \(n(2 * v["h"])) m"] }
    override var labelSize: Float { 0.045 }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let r: Float = 0.11, top: Float = 0.16
        let fx: Float = 0.64, fy: Float = 0.32
        let mx: Float = 0.4, my: Float = 0.66
        b.hatch(0.24, top, 0.9, top, side: -1, gap: 0.05, len: 0.04)
        b.bar(fx, top, fx, fy, 0.025).fill(Sc.METAL).width(1)
        b.bar(mx, my, mx, 0.83, 0.022).fill(Sc.METAL).width(1)
        b.pulleyWheel(fx, fy, r)
        b.pulleyWheel(mx, my, r)
        b.line(mx - r, top, mx - r, my).width(1.5)
        b.arc(mx, my, r, r, 180, -180).width(1.5)
        b.line(mx + r, my, fx - r, fy).width(1.5)
        b.arc(fx, fy, r, r, 180, 180).width(1.5)
        b.line(fx + r, fy, fx + r, 0.86).width(1.5)
        b.dot(mx - r, top, 0.014)
        let p = v["P"] + v["Pm"]
        let s = 0.22 / Float(max(p, 1e-6))
        let fl = max(0.05, Float(Self.force(v)) * s)
        b.vec(fx + r, 0.86, fx + r, 0.86 + fl, head: 0.045, color: Sc.RED)
        lb.c(fx + r + 0.08, 0.86 + fl / 2, "F", 0.08, Sc.RED)
        let bs = min(blockSize(v["P"]), 0.22)
        b.rect(mx - bs / 2, 0.83, bs, bs).fill(Sc.BLOCK).width(1.2)
        lb.c(mx, 0.83 + bs / 2, "P", 0.07)
        lb.c(mx - r - 0.06, (top + my) / 2, "F", 0.055, Sc.RED)
        lb.c((mx + fx) / 2 - 0.04, (my + fy) / 2 + 0.04, "F", 0.055, Sc.RED)
    }
}

/// Kaldıraç (destek ortada): F·b = P·a.
final class LeverModel: ParamModel {
    static let shared = LeverModel()
    private init() {
        super.init("sci.phys.lever", "Kaldıraç", w: 1.8, h: 1,
                   tags: ["basit makine", "destek", "yük", "kuvvet kolu", "yük kolu", "moment", "formül"])
    }
    private static let D = [
        ParamDef("P", "P", "Yükün ağırlığı", default: 60, min: 1, max: 10000, step: 5, unit: "N"),
        ParamDef("a", "a", "Yük kolu", default: 0.4, min: 0.05, max: 50, step: 0.05, unit: "m"),
        ParamDef("b", "b", "Kuvvet kolu", default: 1, min: 0.05, max: 50, step: 0.05, unit: "m"),
    ]
    override var defs: [ParamDef] { Self.D }
    static func force(_ v: ParamValues) -> Double { v["P"] * v["a"] / v["b"] }
    private static let DV = [Derived("F", "Kuvvet", unit: "N", solveFor: "b") { force($0) }]
    override var derived: [Derived] { Self.DV }
    override func label(_ v: ParamValues) -> [String] { ["F·b = P·a", "F = \(n(Self.force(v))) N"] }
    override var labelSize: Float { 0.055 }

    private static let LOAD: Float = 0.26, EFFORT: Float = 1.62, TOP: Float = 0.5, BOT: Float = 0.56, GY: Float = 0.8

    private func pivotX(_ v: ParamValues) -> Float { Float(Double(Self.LOAD) + Double(Self.EFFORT - Self.LOAD) * v["a"] / (v["a"] + v["b"])) }
    private func scale(_ v: ParamValues) -> Float { 0.34 / Float(max(v["P"], Self.force(v))) }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let LOAD = Self.LOAD, EFFORT = Self.EFFORT, TOP = Self.TOP, BOT = Self.BOT, GY = Self.GY
        let px = pivotX(v)
        b.hatch(px - 0.24, GY, px + 0.24, GY, side: 1, gap: 0.05, len: 0.035)
        b.poly(px, BOT, px - 0.12, GY, px + 0.12, GY, closed: true).fill(Sc.METAL).width(1.2)
        b.rect(0.1, TOP, 1.6, BOT - TOP).fill(Sc.WOOD).width(1.2)
        let bs = (0.14 + 0.05 * Float((v["P"] / 60).squareRoot())).coerceIn(0.12, 0.3)
        b.rect(LOAD - bs / 2, TOP - bs, bs, bs).fill(Sc.BLOCK).width(1.2)
        lb.c(LOAD, TOP - bs / 2, "P", 0.08)
        let s = scale(v)
        let pl = max(0.05, v.f("P") * s)
        let fl = max(0.05, Float(Self.force(v)) * s)
        b.vec(EFFORT, TOP - fl, EFFORT, TOP - 0.005, head: 0.045, color: Sc.RED, width: 1.3)
        lb.c(EFFORT + 0.08, TOP - fl / 2, "F", 0.08, Sc.RED)
        for x in [LOAD, px, EFFORT] { b.line(x, BOT, x, 0.9).dashed().width(0.6) }
        b.dim(LOAD, 0.88, px, 0.88)
        b.dim(px, 0.88, EFFORT, 0.88)
        lb.c((LOAD + px) / 2, 0.95, "a = \(n(v["a"])) m", 0.05)
        lb.c((px + EFFORT) / 2, 0.95, "b = \(n(v["b"])) m", 0.05)
        b.vec(LOAD - bs / 2 - 0.05, TOP - pl, LOAD - bs / 2 - 0.05, TOP, head: 0.04, width: 1)
    }

    override func handles(_ v: ParamValues) -> [ParamHandle] {
        [ParamHandle(key: "pivot", x: pivotX(v), y: (Self.BOT + Self.GY) / 2),
         ParamHandle(key: "force", x: Self.EFFORT, y: Self.TOP - max(0.05, Float(Self.force(v)) * scale(v)))]
    }

    override func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? {
        switch key {
        case "pivot":
            let t = Double((x - Self.LOAD) / (Self.EFFORT - Self.LOAD)).coerceIn(0.03, 0.97)
            let total = v["a"] + v["b"]
            return ["a": total * t, "b": total * (1 - t)]
        case "force":
            let len = Double(Self.TOP - y)
            if len <= 0.01 { return nil }
            let f = len / Double(scale(v))
            return ["P": f * v["b"] / v["a"]]
        default: return nil
        }
    }
}
