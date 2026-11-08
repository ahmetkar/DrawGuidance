import Foundation

/// Veri koordinatlarını (x sağa, y yukarı) fiziksel çizim alanına eşler (Android graph `Frame`).
struct Frame {
    let x0: Double, x1: Double, y0: Double, y1: Double
    let L: Float, T: Float, R: Float, B: Float
    let sx: Double
    let sy: Double

    init(_ x0: Double, _ x1: Double, _ y0: Double, _ y1: Double, _ L: Float, _ T: Float, _ R: Float, _ B: Float) {
        self.x0 = x0; self.x1 = x1; self.y0 = y0; self.y1 = y1
        self.L = L; self.T = T; self.R = R; self.B = B
        sx = Double(R - L) / (x1 - x0)
        sy = Double(B - T) / (y1 - y0)
    }

    func px(_ x: Double) -> Double { Double(L) + (x - x0) * sx }
    func py(_ y: Double) -> Double { Double(B) - (y - y0) * sy }
    var hasX0: Bool { x0 <= 0 && x1 >= 0 }
    var hasY0: Bool { y0 <= 0 && y1 >= 0 }
}

/// Eksen çentikleri: k·step; π kesirli ise etiket "π/2", "3π/2"…
struct Ticks {
    let step: Double
    var piN = 0
    var piD = 0

    init(_ step: Double, _ piN: Int = 0, _ piD: Int = 0) { self.step = step; self.piN = piN; self.piD = piD }

    var isPi: Bool { piD > 0 }
    func value(_ k: Int) -> Double { Double(k) * step }
    func label(_ k: Int) -> String { isPi ? Fmt.piLabel(k * piN, piD) : Fmt.auto(Double(k) * step, Fmt.decimals(step)) }

    func indices(_ lo: Double, _ hi: Double) -> [Int] {
        let a = (lo / step - 1e-7).rounded(.up)
        let b = (hi / step + 1e-7).rounded(.down)
        if !a.isFinite || !b.isFinite || b - a > 400 { return [] }
        let ia = ktInt(a), ib = ktInt(b)
        return ia <= ib ? Array(ia...ib) : []
    }

    static func pi(_ n: Int, _ d: Int) -> Ticks { Ticks(Double.pi * Double(n) / Double(d), n, d) }

    /// Kullanıcı adımı; [allowPi] ise π'nin basit kesri π ile yazılır.
    static func user(_ step: Double, _ allowPi: Bool) -> Ticks {
        if allowPi, let f = Fmt.piFraction(step), (1...12).contains(f.0), f.1 <= 12 { return pi(f.0, f.1) }
        return Ticks(step)
    }

    /// Periyodik eğri: yarım eksende en fazla [ticks] çentik olacak en küçük π adımı.
    static func piFor(_ half: Double, _ ticks: Int) -> Ticks {
        let cands = [(1, 4), (1, 2), (1, 1), (2, 1), (4, 1), (8, 1)]
        for (n, d) in cands where half / (Double.pi * Double(n) / Double(d)) <= Double(ticks) + 0.01 { return pi(n, d) }
        return pi(16, 1)
    }
}

/// Çizilecek eğrinin kaynağı: ön ayar ya da formül; açık (y = f(x)) ya da kapalı (F(x, y) = 0).
struct CurveSource {
    let text: String
    let explicit: GExpression.Expr?
    let implicit: GExpression.Expr?
    let periodic: Bool
    let period: Double
    let hint: PresetHint

    func f(_ x: Double) -> Double { explicit?(x, 0) ?? .nan }

    static func resolve(_ p: GraphParams) -> CurveSource {
        let preset = (p.kind == .FUNCTION || p.kind == .AXES || p.kind == .COORD_PLANE) ? CurvePresets.find(p.preset) : nil
        // v45: ';' ile birden çok eğri; plan ve önemli noktalar ilk eğriden (ötekiler Cartesian.extraCurves)
        let text = preset?.formula ?? (GraphFormula.parts(p.formula).first ?? "")
        let mode: AngleMode = p.angleDeg ? .DEG : .RAD
        let hint = preset.flatMap { CurvePresets.hint($0.id) } ?? PresetHint(unit: 2)
        if text.allSatisfy(\.isWhitespace) { return CurveSource(text: "", explicit: nil, implicit: nil, periodic: false, period: 0, hint: hint) }
        // v18 desen (FUNCTION): y = f(k·x); eksen aralığı k = 1'deki gibi kalır, eğri sıklaşır
        let k = p.kind == .FUNCTION ? Double(clampI(p.freq, 1, Graphs.MAX_FREQ)) : 1
        func scaled(_ e: GExpression.Expr?) -> GExpression.Expr? {
            guard let e, k != 1 else { return e }
            return { x, y in e(k * x, y) }
        }
        if GExpression.isImplicit(text) {
            var h = hint
            h.equal = true
            return CurveSource(text: text, explicit: nil, implicit: scaled(GExpression.parseImplicit(text, mode)), periodic: false, period: 0, hint: h)
        }
        let raw = GExpression.parse(text, mode)
        var periodic = preset?.periodic ?? false
        // Periyot (ve buna bağlı x aralığı) k = 1'deki eğriden: desen artınca eksenler değişmez
        var period = preset?.period ?? 0
        if preset == nil, let raw, let d = detectPeriod(text, raw) { periodic = true; period = d }
        return CurveSource(text: text, explicit: scaled(raw), implicit: nil, periodic: periodic, period: period, hint: hint)
    }

    /// Trigonometrik formüllerde π, 2π ya da 4π periyodu (ör. "3sin(2x)" → π).
    private static func detectPeriod(_ text: String, _ e: GExpression.Expr) -> Double? {
        let t = text.lowercased()
        if !["sin", "cos", "tan", "tg", "cot", "sec", "csc"].contains(where: { t.contains($0) }) { return nil }
        let probes = [-2.7, -1.9, -1.1, -0.35, 0.2, 0.9, 1.6, 2.45, 3.1]
        let vs = probes.map { e($0, 0) }.filter(\.isFinite)
        if vs.count < 4 || vs.max()! - vs.min()! < 1e-9 { return nil }
        for period in [Double.pi, 2 * .pi, 4 * .pi] {
            var ok = 0
            var bad = false
            for x in probes {
                let a = e(x, 0)
                let b = e(x + period, 0)
                if !a.isFinite || !b.isFinite { continue }
                if abs(a - b) > 1e-6 * (1 + abs(a)) { bad = true; break }
                ok += 1
            }
            if !bad && ok >= 4 { return period }
        }
        return nil
    }
}

/// AXES, COORD_PLANE ve FUNCTION: eksenler, ızgara, çentikler, eğri, önemli noktalar, formül kutusu (Android `Cartesian`).
enum Cartesian {
    private struct Plan {
        let fr: Frame, xt: Ticks, yt: Ticks
        let domA: Double, domB: Double
        let minorX: Int, minorY: Int
    }

    private struct DP { let x: Double, y: Double }
    private struct Jump { let x: Double, left: Double, right: Double }
    private struct Sampled {
        let pieces: [[DP]], xs: [Double], ys: [Double], cont: [Bool]
        let jumps: [Jump], edges: [DP]
    }

    private enum Grow { case CENTER, HIGH, LOW }

    static func build(_ c: GCanvas) {
        let src = CurveSource.resolve(c.p)
        let plan = c.p.kind == .FUNCTION ? planFunction(c, src) : planPlane(c, src)
        render(c, src, plan)
    }

    private static func finiteRange(_ a: Float, _ b: Float) -> Bool { a.isFinite && b.isFinite && b > a }

    private static func grow(_ a: Double, _ b: Double, _ span: Double, _ g: Grow) -> (Double, Double) {
        let extra = span - (b - a)
        if extra <= 0 { return (a, b) }
        switch g {
        case .CENTER: return (a - extra / 2, b + extra / 2)
        case .HIGH: return (a - extra * 0.08, b + extra * 0.92)
        case .LOW: return (a - extra * 0.92, b + extra * 0.08)
        }
    }

    static func axisX(_ fr: Frame) -> Float { fr.hasX0 ? Float(fr.px(0)) : fr.L }
    static func axisY(_ fr: Frame) -> Float { fr.hasY0 ? Float(fr.py(0)) : fr.B }

    /// Çentik yazılarının sığması için sol / alt kenar payı eksikleri.
    private static func deficits(_ c: GCanvas, _ fr: Frame, _ yt: Ticks) -> (Float, Float) {
        if !c.p.showNumbers { return (0, 0) }
        let ax = axisX(fr)
        let ay = axisY(fr)
        var yw: Float = 0
        for k in yt.indices(fr.y0, fr.y1) where k != 0 { yw = max(yw, c.measure(yt.label(k), GStyle.TICK_SIZE)) }
        let dL = GStyle.PAD * 0.6 + yw + GStyle.GAP + GStyle.TICK - ax
        let dB = ay + GStyle.TICK + GStyle.GAP + GStyle.TICK_SIZE + GStyle.PAD * 0.5 - c.H
        return (dL, dB)
    }

    private static func minorFor(_ spacing: Double, _ preferred: Int, _ minGap: Double) -> Int {
        var m = max(preferred, 1)
        while m > 1 && spacing / Double(m) < minGap { m = (m % 2 == 0 && m > 2) ? m / 2 : m - 1 }
        return m
    }

    // MARK: - düzlem (AXES, COORD_PLANE)

    private static func planPlane(_ c: GCanvas, _ src: CurveSource) -> Plan {
        let p = c.p
        let n = clampI(p.ticks, 1, 12)
        let hasX = finiteRange(p.xMin, p.xMax)
        let hasY = finiteRange(p.yMin, p.yMax)
        let user = Double(p.tickStep)
        let ext = max(
            hasX ? Double(max(abs(p.xMin), abs(p.xMax))) : 0,
            hasY ? Double(max(abs(p.yMin), abs(p.yMax))) : 0
        )
        let t: Ticks
        if user > 0 { t = Ticks.user(user, true) } else if hasX || hasY { t = Ticks(Fmt.niceCeil(ext / Double(n))) } else { t = Ticks(1) }
        let m = GStyle.PAD + GStyle.AXIS_EXT
        var mL = m
        var mB = m
        let mR = m
        let mT = m
        var fr = Frame(-1, 1, -1, 1, mL, mT, c.W - mR, c.H - mB)
        for _ in 0..<4 {
            let pw = Double(c.W - mL - mR)
            let ph = Double(c.H - mT - mB)
            var xa: Double, xb: Double, ya: Double, yb: Double
            if !hasX && !hasY {
                let half = (Double(n) + 0.5) * t.step
                xa = -half; xb = half; ya = -half; yb = half
            } else {
                xa = hasX ? Double(p.xMin) : -1e-9
                xb = hasX ? Double(p.xMax) : 1e-9
                ya = hasY ? Double(p.yMin) : -1e-9
                yb = hasY ? Double(p.yMax) : 1e-9
            }
            let s = min(pw / (xb - xa), ph / (yb - ya))
            (xa, xb) = grow(xa, xb, pw / s, .CENTER)
            (ya, yb) = grow(ya, yb, ph / s, .CENTER)
            fr = Frame(xa, xb, ya, yb, mL, mT, c.W - mR, c.H - mB)
            let (dL, dB) = deficits(c, fr, t)
            if dL <= 0.001 && dB <= 0.001 { break }
            mL += max(0, dL)
            mB += max(0, dB)
        }
        let minGap = p.kind == .AXES ? 0.012 : 0.016
        let minor = minorFor(t.step * fr.sx, clampI(p.repeat, 1, 10), minGap)
        return Plan(fr: fr, xt: t, yt: t, domA: fr.x0, domB: fr.x1, minorX: minor, minorY: minor)
    }

    // MARK: - FUNCTION

    private static func planFunction(_ c: GCanvas, _ src: CurveSource) -> Plan {
        let p = c.p
        let hint = src.hint
        let rep = Double(clampI(p.repeat, 1, 24))
        let n = clampI(p.ticks, 1, 12)
        let hasX = finiteRange(p.xMin, p.xMax)
        let hasY = finiteRange(p.yMin, p.yMax)
        let user = Double(p.tickStep)

        let a: Double
        let b: Double
        if hasX {
            a = Double(p.xMin); b = Double(p.xMax)
        } else if src.periodic && src.period > 0 {
            let per = src.period
            if hint.start == .ZERO { a = 0; b = rep * per }
            else if hint.start == .ONE_PERIOD && rep == 1 { a = 0; b = per }
            else { a = -rep * per / 2; b = rep * per / 2 }
        } else {
            let u = hint.unit
            if hint.start == .FROM_ZERO { a = hint.center - 0.15 * rep * u; b = hint.center + rep * u }
            else { a = hint.center - rep * u; b = hint.center + rep * u }
        }
        let halfX = max(max(abs(a), abs(b)), 1e-9)
        let xt: Ticks
        if user > 0 { xt = Ticks.user(user, src.periodic) } else if src.periodic { xt = Ticks.piFor(halfX, n) } else { xt = Ticks(Fmt.niceCeil(halfX / Double(n))) }
        let padX: Double
        if hasX { padX = 0 } else if src.periodic { padX = max(0.3 * xt.step, 0.03 * (b - a)) } else { padX = 0.05 * (b - a) }
        let xv0 = a - padX
        let xv1 = b + padX
        let yr: [Double] = hasY
            ? [Double(p.yMin), Double(p.yMax), Double(p.yMin), Double(p.yMax)]
            : yRange(src, src.periodic ? a : xv0, src.periodic ? b : xv1, hint, xv0, xv1)
        let coreLo = yr[2]
        let coreHi = yr[3]
        var mL = GStyle.PAD
        var mB = GStyle.PAD
        let mT = GStyle.PAD + GStyle.AXIS_EXT
        let mR = GStyle.PAD + GStyle.AXIS_EXT
        var equal = false
        var decided = false
        var fr = Frame(xv0, xv1, yr[0], yr[1], mL, mT, c.W - mR, c.H - mB)
        var yt = Ticks(1)
        for _ in 0..<5 {
            let pw = Double(c.W - mL - mR)
            let ph = Double(c.H - mT - mB)
            var x0 = xv0, x1 = xv1, y0 = yr[0], y1 = yr[1]
            if !decided {
                let sx = pw / (x1 - x0)
                let sy = ph / (y1 - y0)
                equal = src.implicit != nil || hint.equal || (!(hasX && hasY) && min(sx, sy) / max(sx, sy) >= 0.55)
                decided = true
            }
            if equal {
                let s = min(pw / (x1 - x0), ph / (y1 - y0))
                let gx: Grow = hasX ? .CENTER : (hint.start == .FROM_ZERO || hint.start == .ZERO) ? .HIGH : .CENTER
                let cs = coreHi - coreLo
                let gy: Grow
                if hasY { gy = .CENTER }
                else if coreLo >= -0.05 * cs && coreHi > 0 { gy = .HIGH }
                else if coreHi <= 0.05 * cs && coreLo < 0 { gy = .LOW }
                else { gy = .CENTER }
                (x0, x1) = grow(x0, x1, pw / s, gx)
                (y0, y1) = grow(y0, y1, ph / s, gy)
            }
            fr = Frame(x0, x1, y0, y1, mL, mT, c.W - mR, c.H - mB)
            yt = yTicks(fr, xt, coreLo, coreHi, user, equal, src.periodic, n)
            let (dL, dB) = deficits(c, fr, yt)
            if dL <= 0.001 && dB <= 0.001 { break }
            mL += max(0, dL)
            mB += max(0, dB)
        }
        let periodicDomain = src.periodic && !hasX
        let domA = periodicDomain ? a : fr.x0
        let domB = periodicDomain ? b : fr.x1
        let minorX = minorFor(xt.step * fr.sx, 2, 0.028)
        let minorY = minorFor(yt.step * fr.sy, 2, 0.028)
        return Plan(fr: fr, xt: xt, yt: yt, domA: domA, domB: domB, minorX: minorX, minorY: minorY)
    }

    /// Sağlam y aralığı: [görünüm alt, üst, çekirdek alt, üst].
    private static func yRange(_ src: CurveSource, _ a: Double, _ b: Double, _ hint: PresetHint, _ xv0: Double, _ xv1: Double) -> [Double] {
        if src.implicit != nil {
            let h = max(abs(xv0), abs(xv1)) * 0.75
            return [-h, h, -h * 0.8, h * 0.8]
        }
        if !hint.yLimit.isNaN {
            let l = hint.yLimit
            return [-l, l, -l * 0.8, l * 0.8]
        }
        if src.explicit == nil {
            let h = max(abs(xv0), abs(xv1))
            return [-h, h, -h, h]
        }
        let n = 700
        var vals: [Double] = []
        vals.reserveCapacity(n + 1)
        for i in 0...n {
            let y = src.f(a + (b - a) * Double(i) / Double(n))
            if y.isFinite { vals.append(y) }
        }
        if vals.isEmpty { return [-1.2, 1.2, -1.0, 1.0] }
        vals.sort()
        func q(_ t: Double) -> Double { vals[ktRound(Double(vals.count - 1) * t)] }
        var lo = q(0.05)
        var hi = q(0.95)
        var span = max(hi - lo, 1e-12)
        if vals.first! >= lo - 0.6 * span { lo = vals.first! }
        if vals.last! <= hi + 0.6 * span { hi = vals.last! }
        span = hi - lo
        // Önemli noktalar (kökler, yerel uç değerler, y kesişimi) aralığın küçük bir kısmındaysa onlara odaklan
        do {
            var ks: [Double] = []
            var turning = false
            var prevY = Double.nan
            var prevD = Double.nan
            for i in 0...n {
                let y = src.f(a + (b - a) * Double(i) / Double(n))
                if !y.isFinite { prevY = .nan; prevD = .nan; continue }
                if prevY.isFinite {
                    if prevY * y <= 0 && abs(y - prevY) < 0.5 * span { ks.append(0) }
                    let d = y - prevY
                    if prevD.isFinite && prevD * d < 0 && abs(d) < 0.5 * span { ks.append(prevY); turning = true }
                    prevD = d
                }
                prevY = y
            }
            if a < 0 && b > 0 { let v = src.f(0); if v.isFinite { ks.append(v) } }
            if turning && ks.count >= 2 {
                let kLo = ks.min()!
                let kHi = ks.max()!
                let ksp = kHi - kLo
                if ksp > 1e-9 && span > 6.5 * ksp {
                    lo = max(lo, kLo - 1.6 * ksp)
                    hi = min(hi, kHi + 1.6 * ksp)
                }
            }
        }
        span = hi - lo
        if a < 0 && b > 0 {
            let y0 = src.f(0)
            if y0.isFinite && y0 >= lo - span && y0 <= hi + span { lo = min(lo, y0); hi = max(hi, y0) }
        }
        span = hi - lo
        if span < 1e-9 * (1 + abs(hi)) {
            let v = hi
            lo = min(0, v - 1)
            hi = max(0, v + 1)
            if v > 0 { lo = min(lo, -0.2 * v) } else if v < 0 { hi = max(hi, -0.2 * v) }
        }
        span = hi - lo
        if lo > 0 && lo <= 0.6 * span { lo = 0 }
        if hi < 0 && -hi <= 0.6 * span { hi = 0 }
        span = hi - lo
        return [lo - 0.1 * span, hi + 0.1 * span, lo, hi]
    }

    private static func yTicks(_ fr: Frame, _ xt: Ticks, _ coreLo: Double, _ coreHi: Double, _ user: Double, _ equal: Bool, _ periodic: Bool, _ ticks: Int) -> Ticks {
        if user > 0 && !periodic { return Ticks(user) }
        var st = (equal && !xt.isPi) ? xt.step : Fmt.niceRound(xt.step * fr.sx / fr.sy)
        if !equal {
            let ext = max(abs(coreLo), abs(coreHi))
            var guardN = 0
            while st > ext && ext > 0 && guardN < 40 { guardN += 1; st = Fmt.smaller(st) }
        }
        var guardN = 0
        if !(equal && !xt.isPi) {
            let core = max(abs(coreLo), abs(coreHi))
            let limit = xt.isPi ? max(1.0, Double(ticks) - 1) : Double(ticks) + 0.5
            while core / st > limit && guardN < 40 { guardN += 1; st = Fmt.larger(st) }
        }
        let half = max(abs(fr.y0), abs(fr.y1))
        guardN = 0
        while half / st > 14 && guardN < 40 { guardN += 1; st = Fmt.larger(st) }
        return Ticks(st)
    }

    // MARK: - örnekleme

    private static func sample(_ f: @escaping (Double) -> Double, _ a: Double, _ b: Double, _ fr: Frame, _ px: Float) -> Sampled {
        let widthPx = abs((b - a) * fr.sx) / Double(px)
        let n = clampI(ktInt(widthPx / 2.5), 120, 2400)
        let xs = (0...n).map { a + (b - a) * Double($0) / Double(n) }
        let ys = xs.map { f($0) }
        var cont = [Bool](repeating: true, count: n)
        let tol = 2.5 * Double(px)
        let top = fr.y1
        let bot = fr.y0
        var pieces: [[DP]] = []
        var curIndex: Int? = nil
        var jumps: [Jump] = []
        var edges: [DP] = []
        var broken = false
        func emit(_ x: Double, _ y: Double) {
            if curIndex == nil { pieces.append([]); curIndex = pieces.count - 1 }
            pieces[curIndex!].append(DP(x: x, y: y))
        }
        func seg(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ depth: Int) {
            let f0 = y0.isFinite
            let f1 = y1.isFinite
            if !f0 && !f1 { curIndex = nil; broken = true; return }
            if f0 != f1 {
                broken = true
                if depth >= 46 || x1 - x0 <= 1e-13 * (1 + abs(x0)) {
                    if f1 { curIndex = nil; emit(x1, y1); edges.append(DP(x: x1, y: y1)) } else { edges.append(DP(x: x0, y: y0)); curIndex = nil }
                    return
                }
                let xm = 0.5 * (x0 + x1)
                let ym = f(xm)
                seg(x0, y0, xm, ym, depth + 1)
                seg(xm, ym, x1, y1, depth + 1)
                return
            }
            let dyp = abs(y1 - y0) * fr.sy
            if dyp <= tol { emit(x1, y1); return }
            if (y0 > top && y1 > top) || (y0 < bot && y1 < bot) { emit(x1, y1); return }
            if depth >= 22 {
                jumps.append(Jump(x: 0.5 * (x0 + x1), left: y0, right: y1))
                broken = true
                curIndex = nil
                emit(x1, y1)
                return
            }
            let xm = 0.5 * (x0 + x1)
            let ym = f(xm)
            seg(x0, y0, xm, ym, depth + 1)
            seg(xm, ym, x1, y1, depth + 1)
        }
        if ys[0].isFinite { emit(xs[0], ys[0]); edges.append(DP(x: xs[0], y: ys[0])) }
        for i in 0..<n {
            broken = false
            seg(xs[i], ys[i], xs[i + 1], ys[i + 1], 0)
            cont[i] = !broken
        }
        if ys[n].isFinite { edges.append(DP(x: xs[n], y: ys[n])) }
        return Sampled(pieces: pieces.filter { $0.count >= 2 }, xs: xs, ys: ys, cont: cont, jumps: jumps, edges: edges)
    }

    /// Liang–Barsky ile çoklu çizgiyi dikdörtgene kırpar (dışarı çıkan yerlerde böler).
    private static func clip(_ pts: [DP], _ L: Float, _ T: Float, _ R: Float, _ B: Float) -> [[P]] {
        var out: [[P]] = []
        var curOpen = false
        if pts.count < 2 { return [] }
        for i in 0..<(pts.count - 1) {
            let a = pts[i]
            let b = pts[i + 1]
            let dx = b.x - a.x
            let dy = b.y - a.y
            var t0 = 0.0
            var t1 = 1.0
            var ok = true
            func edge(_ pp: Double, _ q: Double) {
                if !ok { return }
                if pp == 0 { if q < 0 { ok = false }; return }
                let r = q / pp
                if pp < 0 { if r > t1 { ok = false } else if r > t0 { t0 = r } } else { if r < t0 { ok = false } else if r < t1 { t1 = r } }
            }
            edge(-dx, a.x - Double(L)); edge(dx, Double(R) - a.x); edge(-dy, a.y - Double(T)); edge(dy, Double(B) - a.y)
            if !ok { curOpen = false; continue }
            let s = P(Float(a.x + t0 * dx), Float(a.y + t0 * dy))
            let e = P(Float(a.x + t1 * dx), Float(a.y + t1 * dy))
            if !curOpen || t0 > 1e-9 { out.append([s]) }
            out[out.count - 1].append(e)
            curOpen = !(t1 < 1 - 1e-9)
        }
        return out.filter { $0.count >= 2 }
    }

    private static func segDist(_ p: P, _ a: P, _ b: P) -> Float {
        let dx = b.x - a.x
        let dy = b.y - a.y
        let l2 = dx * dx + dy * dy
        if l2 < 1e-12 { return hypotf(p.x - a.x, p.y - a.y) }
        let t = clampF(((p.x - a.x) * dx + (p.y - a.y) * dy) / l2, 0, 1)
        return hypotf(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /// Douglas–Peucker sadeleştirme.
    static func simplify(_ pts: [P], _ tol: Float) -> [P] {
        if pts.count <= 2 { return pts }
        var keep = [Bool](repeating: false, count: pts.count)
        keep[0] = true
        keep[pts.count - 1] = true
        var stack: [(Int, Int)] = [(0, pts.count - 1)]
        while let top = stack.popLast() {
            let (i, j) = top
            var maxD: Float = -1
            var idx = -1
            if i + 1 < j {
                for k in (i + 1)..<j {
                    let d = segDist(pts[k], pts[i], pts[j])
                    if d > maxD { maxD = d; idx = k }
                }
            }
            if idx >= 0 && maxD > tol { keep[idx] = true; stack.append((i, idx)); stack.append((idx, j)) }
        }
        return pts.indices.filter { keep[$0] }.map { pts[$0] }
    }

    // MARK: - kapalı eğri (marching squares)

    private static func contour(_ fn: @escaping GExpression.Expr, _ fr: Frame, _ c: GCanvas) -> [[DP]] {
        let nx = clampI(ktInt((fr.R - fr.L) / c.px / 3.5), 40, 260)
        let ny = clampI(ktInt((fr.B - fr.T) / c.px / 3.5), 40, 260)
        let xs = (0...nx).map { fr.x0 + (fr.x1 - fr.x0) * Double($0) / Double(nx) }
        let ys = (0...ny).map { fr.y0 + (fr.y1 - fr.y0) * Double($0) / Double(ny) }
        let v: [[Double]] = (0...ny).map { j in (0...nx).map { i in fn(xs[i], ys[j]) } }
        let stride = nx + 1
        let vBase = (ny + 1) * stride
        var cache: [Int: DP] = [:]
        func point(_ key: Int) -> DP? {
            if let hit = cache[key] { return hit }
            let i: Int, j: Int, i2: Int, j2: Int
            if key < vBase { j = key / stride; i = key % stride; i2 = i + 1; j2 = j } else {
                let k = key - vBase; j = k / stride; i = k % stride; i2 = i; j2 = j + 1
            }
            let va = v[j][i]
            let vb = v[j2][i2]
            let t = clampD(va / (va - vb), 0, 1)
            let x = xs[i] + (xs[i2] - xs[i]) * t
            let y = ys[j] + (ys[j2] - ys[j]) * t
            let fv = fn(x, y)
            if !fv.isFinite || abs(fv) > 0.5 * max(abs(va), abs(vb)) + 1e-12 { return nil }
            let r = DP(x: x, y: y)
            cache[key] = r
            return r
        }
        var segs: [(Int, Int)] = []
        func seg(_ ka: Int, _ kb: Int) { if point(ka) != nil && point(kb) != nil { segs.append((ka, kb)) } }
        for j in 0..<ny {
            for i in 0..<nx {
                let a = v[j][i], b = v[j][i + 1], cc = v[j + 1][i + 1], d = v[j + 1][i]
                if !a.isFinite || !b.isFinite || !cc.isFinite || !d.isFinite { continue }
                let idx = (a > 0 ? 1 : 0) | (b > 0 ? 2 : 0) | (cc > 0 ? 4 : 0) | (d > 0 ? 8 : 0)
                if idx == 0 || idx == 15 { continue }
                let e0 = j * stride + i
                let e1 = vBase + j * stride + i + 1
                let e2 = (j + 1) * stride + i
                let e3 = vBase + j * stride + i
                switch idx {
                case 1, 14: seg(e3, e0)
                case 2, 13: seg(e0, e1)
                case 3, 12: seg(e3, e1)
                case 4, 11: seg(e1, e2)
                case 6, 9: seg(e0, e2)
                case 7, 8: seg(e2, e3)
                case 5, 10:
                    let center = fn((xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2)
                    let joined = center > 0
                    if (idx == 5) == joined { seg(e0, e1); seg(e2, e3) } else { seg(e3, e0); seg(e1, e2) }
                default: break
                }
            }
        }
        var adj: [Int: [Int]] = [:]
        var adjOrder: [Int] = []
        func link(_ k: Int, _ s: Int) {
            if adj[k] == nil { adj[k] = []; adjOrder.append(k) }
            adj[k]!.append(s)
        }
        for (s, e) in segs.enumerated() { link(e.0, s); link(e.1, s) }
        var used = [Bool](repeating: false, count: segs.count)
        func walk(_ startSeg: Int, _ startKey: Int) -> [DP] {
            var keys = [startKey]
            var s = startSeg
            var k = startKey
            while true {
                used[s] = true
                let e = segs[s]
                let nk = e.0 == k ? e.1 : e.0
                keys.append(nk)
                k = nk
                guard let next = adj[k]?.first(where: { !used[$0] }) else { break }
                s = next
            }
            return keys.compactMap { point($0) }
        }
        var chains: [[DP]] = []
        for k in javaHashOrder(adjOrder) {
            let list = adj[k]!
            if list.count == 1 && !used[list[0]] { chains.append(walk(list[0], k)) }
        }
        for s in segs.indices where !used[s] { chains.append(walk(s, segs[s].0)) }
        return chains.filter { $0.count >= 2 }
    }

    // MARK: - çizim

    private struct KeyPt { let x: Double, y: Double }

    private static func phys(_ fr: Frame, _ pts: [DP]) -> [DP] { pts.map { DP(x: fr.px($0.x), y: fr.py($0.y)) } }

    private static func render(_ c: GCanvas, _ src: CurveSource, _ plan: Plan) {
        let p = c.p
        let fr = plan.fr
        let ax = axisX(fr)
        let ay = axisY(fr)

        if p.grid && p.kind != .AXES { grid(c, plan) }

        // Eğri geometrisi
        var curve: [[P]] = []
        var sampled: Sampled? = nil
        var closedLoops: [[P]] = []
        if src.explicit != nil {
            let s = sample({ src.f($0) }, plan.domA, plan.domB, fr, c.px)
            sampled = s
            for piece in s.pieces {
                for part in clip(phys(fr, piece), fr.L, fr.T, fr.R, fr.B) { curve.append(simplify(part, c.px * 0.35)) }
            }
        } else if let implicit = src.implicit {
            var loops: [[P]] = []
            for chain in contour(implicit, fr, c) {
                let parts = clip(phys(fr, chain), fr.L, fr.T, fr.R, fr.B).map { simplify($0, c.px * 0.35) }
                curve += parts
                let first = chain.first!
                let last = chain.last!
                if parts.count == 1 && abs(first.x - last.x) < 1e-9 && abs(first.y - last.y) < 1e-9 { loops.append(parts[0]) }
            }
            closedLoops = loops
        }

        // Alan dolgusu
        if p.fillUnder {
            c.layer = 1
            let base = clampF(Float(fr.py(0)), fr.T, fr.B)
            for piece in sampled?.pieces ?? [] {
                let pts = simplify(piece.map {
                    P(clampF(Float(fr.px($0.x)), fr.L, fr.R), Float(clampD(fr.py($0.y), Double(fr.T), Double(fr.B))))
                }, c.px * 0.5)
                if pts.count >= 2 {
                    c.add([P(pts.first!.x, base)] + pts + [P(pts.last!.x, base)], closed: true, fill: c.color, width: 0, alpha: 0.16, tag: "fill")
                }
            }
            for loop in closedLoops { c.add(loop, closed: true, fill: c.color, width: 0, alpha: 0.16, tag: "fill") }
        }

        // Asimptotlar ve sıçramalar
        let finiteJumps = sampled.map { asymptotes(c, fr, src, $0) } ?? []

        axes(c, plan, ax, ay)

        // Eğri
        c.layer = 4
        for line in curve { c.add(line, stroke: c.color, width: GStyle.W_CURVE, tag: "curve") }
        // v45: formüldeki öteki eğriler (aynı eksenlerde, ayrı renklerle)
        extraCurves(c, plan)

        // Süsleme (çember: merkez ve yarıçap)
        decorate(c, fr, src)

        // Önemli noktalar
        let keys: [KeyPt]
        if !p.keyPoints { keys = [] }
        else if let sampled { keys = keyPointsExplicit(src, sampled, fr, finiteJumps) }
        else if let implicit = src.implicit { keys = keyPointsImplicit(implicit, fr) }
        else { keys = [] }
        c.layer = 5
        if p.keyPoints {
            for j in finiteJumps {
                let xs = (j.x * 1e6).rounded(.toNearestOrEven) / 1e6
                let fv = src.f(xs)
                let X = Float(fr.px(xs))
                let closedLeft = fv.isFinite && abs(fv - j.left) < abs(fv - j.right)
                let closedRight = fv.isFinite && !closedLeft
                let yl = Float(fr.py(j.left))
                let yr = Float(fr.py(j.right))
                if X < fr.L || X > fr.R { continue }
                if yl >= fr.T && yl <= fr.B { if closedLeft { c.dot(X, yl, tag: "key") } else { c.openDot(X, yl, tag: "key") } }
                if yr >= fr.T && yr <= fr.B { if closedRight { c.dot(X, yr, tag: "key") } else { c.openDot(X, yr, tag: "key") } }
            }
        }
        for (i, k) in keys.enumerated() { c.dot(Float(fr.px(k.x)), Float(fr.py(k.y)), tag: "key:\(i)") }

        // Yazılar: eksen adları → çentikler → noktalar → formül kutusu → önemli nokta koordinatları
        c.layer = 6
        axisNames(c, fr, ax, ay)
        tickLabels(c, plan, ax, ay)
        valuePoints(c, fr, ax, ay, curve)
        let hasText = !src.text.allSatisfy(\.isWhitespace)
        let formula = p.tickLabels["formula"] ?? (hasText ? Graphs.formulaLabel(p) : "")
        c.setDefault("formula", hasText ? Graphs.formulaLabel(p) : "")
        if !formula.allSatisfy(\.isWhitespace) { formulaBox(c, fr, formula, curve, ax, ay) }
        if p.showNumbers && (1...5).contains(keys.count) { keyLabels(c, fr, keys, src.periodic, curve) }
    }

    /// v45: ikinci ve sonraki eğrilerin renkleri.
    static let EXTRA_COLORS = ["#E53935", "#43A047", "#8E24AA", "#FB8C00"]

    /// v45: ';' ile yazılan ek eğriler (en çok GraphFormula.MAX_CURVES); kesiklik ve asimptotlarda bölünür.
    private static func extraCurves(_ c: GCanvas, _ plan: Plan) {
        let p = c.p
        if CurvePresets.find(p.preset) != nil && (p.kind == .FUNCTION || p.kind == .AXES || p.kind == .COORD_PLANE) { return }
        let parts = GraphFormula.parts(p.formula)
        if parts.count < 2 { return }
        let fr = plan.fr
        let mode: AngleMode = p.angleDeg ? .DEG : .RAD
        for (k, text) in parts.dropFirst().prefix(GraphFormula.MAX_CURVES - 1).enumerated() {
            let color = EXTRA_COLORS[k % EXTRA_COLORS.count]
            var lines: [[P]] = []
            if GExpression.isImplicit(text) {
                guard let fn = GExpression.parseImplicit(text, mode) else { continue }
                for chain in contour(fn, fr, c) {
                    for part in clip(phys(fr, chain), fr.L, fr.T, fr.R, fr.B) { lines.append(simplify(part, c.px * 0.35)) }
                }
            } else {
                guard let e = GExpression.parse(text, mode) else { continue }
                let s = sample({ e($0, 0) }, fr.x0, fr.x1, fr, c.px)
                for piece in s.pieces {
                    for part in clip(phys(fr, piece), fr.L, fr.T, fr.R, fr.B) { lines.append(simplify(part, c.px * 0.35)) }
                }
            }
            for line in lines { c.add(line, stroke: color, width: GStyle.W_CURVE, tag: "curve:\(k + 1)") }
        }
    }

    private static func grid(_ c: GCanvas, _ plan: Plan) {
        let fr = plan.fr
        c.layer = 0
        func positions(_ step: Double, _ lo: Double, _ hi: Double, _ map: (Double) -> Double, _ skipMod: Int) -> [Float] {
            var out: [Float] = []
            let a = Int64(ktInt((lo / step - 1e-7).rounded(.up)))
            let b = Int64(ktInt((hi / step + 1e-7).rounded(.down)))
            if b - a > 2000 || a > b { return out }
            for k in a...b where skipMod <= 1 || k % Int64(skipMod) != 0 { out.append(Float(map(Double(k) * step))) }
            return out
        }
        func vLines(_ xs: [Float], _ color: String, _ width: Float) {
            if xs.isEmpty { return }
            var pts: [P] = []
            for (i, x) in xs.enumerated() {
                if i % 2 == 0 { pts.append(P(x, fr.B)); pts.append(P(x, fr.T)) } else { pts.append(P(x, fr.T)); pts.append(P(x, fr.B)) }
            }
            if pts.count >= 2 { c.add(pts, stroke: color, width: width) }
        }
        func hLines(_ ys: [Float], _ color: String, _ width: Float) {
            if ys.isEmpty { return }
            var pts: [P] = []
            for (i, y) in ys.enumerated() {
                if i % 2 == 0 { pts.append(P(fr.L, y)); pts.append(P(fr.R, y)) } else { pts.append(P(fr.R, y)); pts.append(P(fr.L, y)) }
            }
            if pts.count >= 2 { c.add(pts, stroke: color, width: width) }
        }
        if plan.minorX > 1 { vLines(positions(plan.xt.step / Double(plan.minorX), fr.x0, fr.x1, fr.px, plan.minorX), c.pal.gridMinor, GStyle.W_MINOR) }
        if plan.minorY > 1 { hLines(positions(plan.yt.step / Double(plan.minorY), fr.y0, fr.y1, fr.py, plan.minorY), c.pal.gridMinor, GStyle.W_MINOR) }
        vLines(positions(plan.xt.step, fr.x0, fr.x1, fr.px, 1), c.pal.grid, GStyle.W_GRID)
        hLines(positions(plan.yt.step, fr.y0, fr.y1, fr.py, 1), c.pal.grid, GStyle.W_GRID)
        c.add([P(fr.L, fr.T), P(fr.R, fr.T), P(fr.R, fr.B), P(fr.L, fr.B), P(fr.L, fr.T)], stroke: c.pal.grid, width: GStyle.W_GRID)
    }

    private static func axes(_ c: GCanvas, _ plan: Plan, _ ax: Float, _ ay: Float) {
        let fr = plan.fr
        c.layer = 3
        let neg: Float = c.p.kind == .FUNCTION ? 0 : 0.02
        let tipX = fr.R + GStyle.AXIS_EXT
        let tipY = fr.T - GStyle.AXIS_EXT
        c.line(fr.L - neg, ay, tipX - GStyle.ARROW_LEN * 0.6, ay, width: GStyle.W_AXIS)
        c.arrowHead(tipX, ay, 1, 0)
        c.line(ax, fr.B + neg, ax, tipY + GStyle.ARROW_LEN * 0.6, width: GStyle.W_AXIS)
        c.arrowHead(ax, tipY, 0, -1)
        let eps: Float = 1e-3
        if c.p.kind == .AXES && plan.minorX > 1 {
            let ms = plan.xt.step / Double(plan.minorX)
            for k in Ticks(ms).indices(fr.x0, fr.x1) {
                if k % plan.minorX == 0 { continue }
                let x = Float(fr.px(Double(k) * ms))
                c.line(x, ay - GStyle.TICK * 0.55, x, ay + GStyle.TICK * 0.55, width: GStyle.W_TICK * 0.7)
            }
            for k in Ticks(ms).indices(fr.y0, fr.y1) {
                if k % plan.minorY == 0 { continue }
                let y = Float(fr.py(Double(k) * ms))
                c.line(ax - GStyle.TICK * 0.55, y, ax + GStyle.TICK * 0.55, y, width: GStyle.W_TICK * 0.7)
            }
        }
        for k in plan.xt.indices(fr.x0, fr.x1) {
            if k == 0 && fr.hasX0 { continue }
            let x = Float(fr.px(plan.xt.value(k)))
            if x < fr.L - eps || x > fr.R + eps { continue }
            c.line(x, ay - GStyle.TICK, x, ay + GStyle.TICK, width: GStyle.W_TICK, tag: "tick:x:\(k)")
        }
        for k in plan.yt.indices(fr.y0, fr.y1) {
            if k == 0 && fr.hasY0 { continue }
            let y = Float(fr.py(plan.yt.value(k)))
            if y < fr.T - eps || y > fr.B + eps { continue }
            c.line(ax - GStyle.TICK, y, ax + GStyle.TICK, y, width: GStyle.W_TICK, tag: "tick:y:\(k)")
        }
    }

    private static func axisNames(_ c: GCanvas, _ fr: Frame, _ ax: Float, _ ay: Float) {
        let p = c.p
        let s = GStyle.NAME_SIZE
        if !p.xLabel.isEmpty {
            let w = c.measure(p.xLabel, s)
            c.text(fr.R + GStyle.AXIS_EXT - w, ay - GStyle.GAP * 1.2 - s, p.xLabel, s, tag: "axis:x")
        }
        if !p.yLabel.isEmpty {
            let w = c.measure(p.yLabel, s)
            var x = ax + GStyle.GAP * 1.6
            if x + w > c.W - 0.004 { x = ax - GStyle.GAP * 1.6 - w }
            c.text(x, fr.T - GStyle.AXIS_EXT - s * 0.1, p.yLabel, s, tag: "axis:y")
        }
    }

    private static func tickLabels(_ c: GCanvas, _ plan: Plan, _ ax: Float, _ ay: Float) {
        let p = c.p
        let fr = plan.fr
        let size = GStyle.TICK_SIZE
        let eps: Float = 1e-3
        let minusHalf = c.measure(Fmt.MINUS, size) / 2
        // x
        let xks = plan.xt.indices(fr.x0, fr.x1).filter { k in
            let x = Float(fr.px(plan.xt.value(k)))
            return k != 0 && x >= fr.L - eps && x <= fr.R + eps
        }
        var xTexts: [Int: (String?, Bool)] = [:]
        for k in xks { xTexts[k] = c.tickText("x:\(k)", plan.xt.label(k)) }
        let maxW = xks.map { k in xTexts[k]!.0.map { c.measure($0, size) } ?? 0 }.max() ?? 0
        let everyX = max(1, ktInt(Double((maxW + 0.016) / Float(plan.xt.step * fr.sx)).rounded(.up)))
        for k in xks {
            let (s, forced) = xTexts[k]!
            guard let s else { continue }
            if !forced && (!p.showNumbers || k % everyX != 0) { continue }
            let x = Float(fr.px(plan.xt.value(k)))
            let w = c.measure(s, size)
            let shift = s.hasPrefix(Fmt.MINUS) ? minusHalf : 0
            let bx = x - w / 2 - shift
            let by = ay + GStyle.TICK + GStyle.GAP * 0.5
            if !forced && !c.fits(c.textBox(bx, by, s, size)) { continue }
            c.text(bx, by, s, size, tag: "tick:x:\(k)")
        }
        // y
        let yks = plan.yt.indices(fr.y0, fr.y1).filter { k in
            let y = Float(fr.py(plan.yt.value(k)))
            return k != 0 && y >= fr.T - eps && y <= fr.B + eps
        }
        let everyY = max(1, ktInt(Double((size + 0.01) / Float(plan.yt.step * fr.sy)).rounded(.up)))
        for k in yks {
            let (s, forced) = c.tickText("y:\(k)", plan.yt.label(k))
            guard let s else { continue }
            if !forced && (!p.showNumbers || k % everyY != 0) { continue }
            let y = Float(fr.py(plan.yt.value(k)))
            let w = c.measure(s, size)
            let bx = ax - GStyle.TICK - GStyle.GAP * 0.6 - w
            let by = y - size * 0.52
            if !forced && !c.fits(c.textBox(bx, by, s, size)) { continue }
            c.text(bx, by, s, size, tag: "tick:y:\(k)")
        }
        // başlangıç noktası
        if fr.hasX0 && fr.hasY0 {
            let (s, forced) = c.tickText("x:0", "0")
            if let s, p.showNumbers || forced {
                let w = c.measure(s, size)
                c.text(ax - GStyle.GAP * 0.8 - w, ay + GStyle.GAP * 0.5, s, size, tag: "tick:x:0")
            }
        }
    }

    private static func segHitsBox(_ poly: [P], _ b: GBox) -> Int {
        var hits = 0
        if poly.count < 2 { return 0 }
        for i in 0..<(poly.count - 1) {
            let a = poly[i]
            let e = poly[i + 1]
            if max(a.x, e.x) < b.l || min(a.x, e.x) > b.r || max(a.y, e.y) < b.t || min(a.y, e.y) > b.b { continue }
            if b.contains(a.x, a.y) || b.contains(e.x, e.y) { hits += 1; continue }
            // kenarlarla kesişim (örnekleyerek)
            let steps = 8
            for s in 1..<steps {
                let t = Float(s) / Float(steps)
                if b.contains(a.x + (e.x - a.x) * t, a.y + (e.y - a.y) * t) { hits += 1; break }
            }
        }
        return hits
    }

    private static func curveHits(_ curve: [[P]], _ b: GBox) -> Int { curve.reduce(0) { $0 + segHitsBox($1, b) } }

    private static func formulaBox(_ c: GCanvas, _ fr: Frame, _ text: String, _ curve: [[P]], _ ax: Float, _ ay: Float) {
        let inset: Float = 0.018
        var size = GStyle.FORMULA_SIZE
        let avail = (fr.R - fr.L) - 2 * inset
        let unit = c.measure(text, 1) + 0.9
        if unit * size > avail { size = max(0.026, avail / unit) }
        let tw = c.measure(text, size)
        let padX = size * 0.45
        let padY = size * 0.28
        let bw = tw + 2 * padX
        let bh = size * 1.1 + 2 * padY
        let cands = [
            P(fr.L + inset, fr.T + inset), P(fr.R - inset - bw, fr.T + inset),
            P(fr.L + inset, fr.B - inset - bh), P(fr.R - inset - bw, fr.B - inset - bh),
        ]
        var best = cands[0]
        var bestScore = Double.greatestFiniteMagnitude
        for (i, cand) in cands.enumerated() {
            let box = GBox(cand.x, cand.y, cand.x + bw, cand.y + bh)
            var score = Double(i) * 0.6
            score += Double(c.boxes.filter { $0.overlaps(box, 0.004) }.count) * 100
            if ax > box.l - 0.006 && ax < box.r + 0.006 { score += 25 }
            if ay > box.t - 0.006 && ay < box.b + 0.006 { score += 25 }
            score += Double(curveHits(curve, box)) * 3
            if score < bestScore { bestScore = score; best = cand }
        }
        c.layer = 7
        c.add(c.roundRectPoints(best.x, best.y, bw, bh, size * 0.32), closed: true, fill: c.pal.paper, stroke: c.pal.boxBorder, width: 0.5, alpha: 0.93, tag: "formula")
        c.text(best.x + padX, best.y + padY + size * 0.05, text, size, color: c.color, tag: "formula")
        c.layer = 6
    }

    /// Nokta yazısını çakışmayan (tercihen eğriye değmeyen) bir yere koyar.
    @discardableResult
    private static func placeNear(_ c: GCanvas, _ x: Float, _ y: Float, _ s: String, _ size: Float, _ curve: [[P]], _ tag: String, color: String? = nil) -> Bool {
        let w = c.measure(s, size)
        let h = size
        let g: Float = 0.012
        let cands = [
            P(x + g, y - g - h), P(x - g - w, y - g - h), P(x + g, y + g * 0.7), P(x - g - w, y + g * 0.7),
            P(x - w / 2, y - g * 1.5 - h), P(x - w / 2, y + g * 1.5),
        ]
        var best: P? = nil
        var bestHits = Int.max
        for cand in cands {
            let box = c.textBox(cand.x, cand.y, s, size)
            if !c.fits(box, slack: 0.004) { continue }
            let hits = curveHits(curve, box)
            if hits < bestHits { bestHits = hits; best = cand }
            if hits == 0 { break }
        }
        guard let b = best else { return false }
        c.text(b.x, b.y, s, size, color: color, tag: tag)
        return true
    }

    private static func keyLabels(_ c: GCanvas, _ fr: Frame, _ keys: [KeyPt], _ periodic: Bool, _ curve: [[P]]) {
        for (i, k) in keys.enumerated() {
            let s = Fmt.point(Fmt.coord(k.x, periodic), Fmt.coord(k.y, false))
            placeNear(c, Float(fr.px(k.x)), Float(fr.py(k.y)), s, GStyle.KEY_SIZE, curve, "key:\(i)")
        }
    }

    /// values = x0, y0, x1, y1, …: adlandırılmış noktalar (A(2, 3)) ve eksenlere kesikli izdüşümler.
    private static func valuePoints(_ c: GCanvas, _ fr: Frame, _ ax: Float, _ ay: Float, _ curve: [[P]]) {
        let v = c.p.values
        if v.count < 2 { return }
        for i in 0..<(v.count / 2) {
            let x = Double(v[2 * i])
            let y = Double(v[2 * i + 1])
            if !x.isFinite || !y.isFinite { continue }
            let X = Float(fr.px(x))
            let Y = Float(fr.py(y))
            if X < fr.L - 1e-3 || X > fr.R + 1e-3 || Y < fr.T - 1e-3 || Y > fr.B + 1e-3 { continue }
            c.layer = 2
            if abs(Y - ay) > 0.004 { c.line(X, Y, X, ay, stroke: c.pal.guide, width: 0.7, line: .DASHED) }
            if abs(X - ax) > 0.004 { c.line(X, Y, ax, Y, stroke: c.pal.guide, width: 0.7, line: .DASHED) }
            c.layer = 5
            c.dot(X, Y, r: GStyle.DOT * 1.15, tag: "value:\(i)")
            c.layer = 6
            let name = c.p.valueLabels[i] ?? letter(i)
            let s = c.p.showNumbers ? name + Fmt.point(Fmt.auto(x), Fmt.auto(y)) : name
            if !s.isEmpty { placeNear(c, X, Y, s, GStyle.KEY_SIZE * 1.05, curve, "vlabel:\(i)") }
        }
    }

    static func letter(_ i: Int) -> String { i < 26 ? String(UnicodeScalar(UInt8(65 + i))) : "P\(i + 1)" }

    private static func decorate(_ c: GCanvas, _ fr: Frame, _ src: CurveSource) {
        let d = src.hint.decorate
        if !d.hasPrefix("circle:") { return }
        guard let r = Double(d.dropFirst("circle:".count)) else { return }
        let cx = Float(fr.px(0))
        let cy = Float(fr.py(0))
        let k = 0.5.squareRoot()
        let ex = Float(fr.px(r * k))
        let ey = Float(fr.py(r * k))
        c.layer = 2
        c.line(cx, cy, ex, ey, stroke: c.color, width: 1, line: .DASHED)
        c.layer = 5
        c.dot(cx, cy, r: GStyle.DOT * 0.9, fill: c.color)
        if c.p.showNumbers {
            c.layer = 6
            let s = "r = \(Fmt.auto(r))"
            let size = GStyle.KEY_SIZE
            let mx = (cx + ex) / 2
            let my = (cy + ey) / 2
            let w = c.measure(s, size)
            c.text(mx - w - 0.006, my - size * 0.2, s, size, color: c.color)
        }
    }

    // MARK: - asimptotlar, önemli noktalar

    private static func asymptotes(_ c: GCanvas, _ fr: Frame, _ src: CurveSource, _ s: Sampled) -> [Jump] {
        let span = fr.y1 - fr.y0
        let lo = fr.y0 - 0.5 * span
        let hi = fr.y1 + 0.5 * span
        var vx: [Double] = []
        var finite: [Jump] = []
        for j in s.jumps {
            if j.left >= lo && j.left <= hi && j.right >= lo && j.right <= hi { finite.append(j) }
            else if !vx.contains(where: { abs($0 - j.x) < 1e-6 * (1 + abs(j.x)) }) { vx.append(j.x) }
        }
        c.layer = 2
        for x in vx {
            let X = Float(fr.px(x))
            if X > fr.L + 0.003 && X < fr.R - 0.003 {
                c.line(X, fr.T, X, fr.B, stroke: c.pal.guide, width: 0.9, line: .DASHED, tag: "asymptote")
            }
        }
        if !src.periodic {
            var hys: [Double] = []
            for sign in [-1.0, 1.0] {
                let a1 = src.f(sign * 1e6)
                let a2 = src.f(sign * 1e8)
                if !a1.isFinite || !a2.isFinite { continue }
                if abs(a1 - a2) > 1e-4 * (1 + abs(a2)) { continue }
                let l = (a2 * 1e6).rounded(.toNearestOrEven) / 1e6
                if abs(l) < 1e-6 || l <= fr.y0 || l >= fr.y1 { continue }
                let dev = s.ys.filter(\.isFinite).map { abs($0 - l) }.max() ?? 0
                if dev < 0.05 * span { continue }
                if !hys.contains(where: { abs($0 - l) < 1e-6 }) { hys.append(l) }
            }
            for l in hys {
                let Y = Float(fr.py(l))
                c.line(fr.L, Y, fr.R, Y, stroke: c.pal.guide, width: 0.9, line: .DASHED, tag: "asymptote")
            }
        }
        return finite
    }

    private static func keyPointsExplicit(_ src: CurveSource, _ s: Sampled, _ fr: Frame, _ jumps: [Jump]) -> [KeyPt] {
        let xs = s.xs
        let ys = s.ys
        let n = xs.count - 1
        var out: [KeyPt] = []
        let scale = max(max(abs(fr.y1), abs(fr.y0)), 1e-9)
        let zero = 1e-10 * scale
        // Kökler
        for i in 0..<n {
            if !s.cont[i] { continue }
            let a = ys[i]
            let b = ys[i + 1]
            if !a.isFinite || !b.isFinite { continue }
            if abs(a) <= zero {
                let prev = i > 0 ? ys[i - 1] : .nan
                if (i == 0 || abs(prev) > zero) && abs(b) > zero { out.append(KeyPt(x: xs[i], y: 0)) }
                continue
            }
            if a * b < 0 && abs(b) > zero {
                var lo = xs[i]
                var hi = xs[i + 1]
                var flo = a
                for _ in 0..<70 {
                    let m = 0.5 * (lo + hi)
                    let fm = src.f(m)
                    if !fm.isFinite { break }
                    if fm == 0 { lo = m; hi = m; break }
                    if flo * fm < 0 { hi = m } else { lo = m; flo = fm }
                }
                out.append(KeyPt(x: 0.5 * (lo + hi), y: 0))
            }
        }
        if abs(ys[n]) <= zero && n > 0 && abs(ys[n - 1]) > zero { out.append(KeyPt(x: xs[n], y: 0)) }
        // Yerel en büyük / en küçük
        if n >= 2 {
            for i in 1..<n {
                if !s.cont[i - 1] || !s.cont[i] { continue }
                let d1 = ys[i] - ys[i - 1]
                let d2 = ys[i + 1] - ys[i]
                if !(d1 * d2 < 0) { continue }
                let isMax = d1 > 0
                var lo = xs[i - 1]
                var hi = xs[i + 1]
                for _ in 0..<90 {
                    let m1 = lo + (hi - lo) * 0.381966
                    let m2 = lo + (hi - lo) * 0.618034
                    let f1 = src.f(m1)
                    let f2 = src.f(m2)
                    if (f1 > f2) == isMax { hi = m2 } else { lo = m1 }
                }
                let xm = 0.5 * (lo + hi)
                let ym = src.f(xm)
                if ym.isFinite { out.append(KeyPt(x: xm, y: ym)) }
            }
        }
        // y eksenini kesme noktası
        if 0 >= xs[0] && 0 <= xs[n] {
            let y0 = src.f(0)
            if y0.isFinite { out.append(KeyPt(x: 0, y: y0)) }
        }
        // Tanım aralığının kenarları (√x'in başlangıcı gibi)
        for e in s.edges {
            let interior = e.x > xs[0] + 1e-9 * (xs[n] - xs[0]) && e.x < xs[n] - 1e-9 * (xs[n] - xs[0])
            if interior { out.append(KeyPt(x: e.x, y: e.y)) }
        }
        // Temizle: küçük değerleri sıfırla, yuvarla, çift kayıtları ve sıçrama noktalarını at
        let snapped = out.map { k -> KeyPt in
            var x = abs(k.x) < 1e-9 * (1 + abs(xs[n] - xs[0])) ? 0 : k.x
            var y = abs(k.y) <= zero * 10 ? 0 : k.y
            let rx = (x * 1e6).rounded(.toNearestOrEven) / 1e6
            if abs(rx - x) < 1e-8 { x = rx }
            let ry = (y * 1e6).rounded(.toNearestOrEven) / 1e6
            if abs(ry - y) < 1e-8 { y = ry }
            return KeyPt(x: x, y: y)
        }
        return cleanKeys(snapped, fr, jumps)
    }

    private static func cleanKeys(_ keys: [KeyPt], _ fr: Frame, _ jumps: [Jump]) -> [KeyPt] {
        var result: [KeyPt] = []
        for k in keys {
            let X = fr.px(k.x)
            let Y = fr.py(k.y)
            if X < Double(fr.L) + 0.004 || X > Double(fr.R) - 0.004 || Y < Double(fr.T) + 0.004 || Y > Double(fr.B) - 0.004 { continue }
            if jumps.contains(where: { abs(fr.px($0.x) - X) < 0.01 }) { continue }
            if result.contains(where: { hypot(fr.px($0.x) - X, fr.py($0.y) - Y) < 0.012 }) { continue }
            result.append(k)
        }
        return result.count > 16 ? [] : result
    }

    private static func keyPointsImplicit(_ fn: GExpression.Expr, _ fr: Frame) -> [KeyPt] {
        var out: [KeyPt] = []
        func scan(_ a: Double, _ b: Double, _ g: (Double) -> Double, _ make: (Double) -> KeyPt) {
            let n = 400
            var pv = g(a)
            var px = a
            for i in 1...n {
                let x = a + (b - a) * Double(i) / Double(n)
                let v = g(x)
                if pv.isFinite && v.isFinite && pv * v < 0 {
                    var lo = px
                    var hi = x
                    var flo = pv
                    for _ in 0..<60 {
                        let m = 0.5 * (lo + hi)
                        let fm = g(m)
                        if flo * fm <= 0 { hi = m } else { lo = m; flo = fm }
                    }
                    let r = 0.5 * (lo + hi)
                    let scale = max(abs(pv), abs(v))
                    let rr = (r * 1e6).rounded(.toNearestOrEven) / 1e6
                    if abs(g(r)) < 0.1 * scale { out.append(make(abs(r - rr) < 1e-8 ? rr : r)) }
                }
                pv = v
                px = x
            }
        }
        if fr.hasY0 { scan(fr.x0, fr.x1, { fn($0, 0) }) { KeyPt(x: $0, y: 0) } }
        if fr.hasX0 { scan(fr.y0, fr.y1, { fn(0, $0) }) { KeyPt(x: 0, y: $0) } }
        return cleanKeys(out, fr, [])
    }
}
