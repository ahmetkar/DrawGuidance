import Foundation

/// v45: her grafik türünde formülün anlamı (Android `GraphFormula`).
enum GraphFormula {
    enum Meaning { case CURVE, SURFACE, SET, SAMPLED, NONE }

    static func meaning(_ kind: GraphKind) -> Meaning {
        switch kind {
        case .FUNCTION, .AXES, .COORD_PLANE: return .CURVE
        case .AXES_3D: return .SURFACE
        case .NUMBER_LINE: return .SET
        case .BAR, .LINE, .HISTOGRAM, .PIE, .SCATTER: return .SAMPLED
        case .UNIT_CIRCLE: return .NONE
        }
    }

    static func accepts(_ kind: GraphKind) -> Bool { meaning(kind) != .NONE }

    static func example(_ kind: GraphKind) -> String {
        switch meaning(kind) {
        case .CURVE: return "y = x^2 - 2; y = 2x + 1"
        case .SURFACE: return "z = x^2 - y^2"
        case .SET: return "-1 ≤ x < 3"
        case .SAMPLED: return "y = 2x + 1"
        case .NONE: return ""
        }
    }

    /// En dış düzeyde ';' (ya da satır sonu) ile ayrılmış parçalar.
    static func parts(_ formula: String) -> [String] {
        var out: [String] = []
        var depth = 0
        var cur = ""
        func flush() {
            let t = cur.trimmingCharacters(in: .whitespacesAndNewlines)
            if !t.isEmpty { out.append(t) }
            cur = ""
        }
        for ch in formula {
            if "([{".contains(ch) { depth += 1 } else if ")]}".contains(ch) { depth = max(depth - 1, 0) }
            if (ch == ";" || ch == "\n" || ch == "\r\n") && depth == 0 { flush() } else { cur.append(ch) }
        }
        flush()
        return out
    }

    static let MAX_CURVES = 4

    private static func mode(_ deg: Bool) -> AngleMode { deg ? .DEG : .RAD }

    /// Formülün hatası (nil: geçerli; boş formül de geçerli).
    static func error(_ kind: GraphKind, _ formula: String) -> MathError? {
        if formula.allSatisfy(\.isWhitespace) { return nil }
        if !accepts(kind) { return MathError(code: .UNEXPECTED, pos: 0, detail: "") }
        let list = parts(formula)
        if list.isEmpty { return MathError(code: .EMPTY, pos: 0) }
        switch meaning(kind) {
        case .CURVE: return list.lazy.compactMap { GExpression.error($0, allowY: true) }.first
        case .SURFACE: return list.count > 1 ? MathError(code: .RELATION) : surfaceError(list[0])
        case .SAMPLED:
            if list.count > 1 { return MathError(code: .RELATION) }
            if let e = GExpression.error(list[0], allowY: false) { return e }
            return GExpression.isImplicit(list[0]) ? MathError(code: .RELATION) : nil
        case .SET: return list.lazy.compactMap { setError($0) }.first
        case .NONE: return nil
        }
    }

    private static func surfaceError(_ text: String) -> MathError? {
        if GExpression.isImplicit(text) { return MathError(code: .RELATION) }
        let (node, err) = MathExpr.tryParse(GExpression.body(text).lowercased())
        if let err { return err }
        if let bad = node!.variables().first(where: { $0 != "x" && $0 != "y" }) { return MathError(code: .UNKNOWN_NAME, pos: -1, detail: bad) }
        return nil
    }

    private static func setError(_ text: String) -> MathError? {
        let (rel, err) = MathExpr.tryParseRelation(text.lowercased())
        if let err { return err }
        if let bad = rel!.sides.flatMap({ $0.variables() }).first(where: { $0 != "x" }) { return MathError(code: .UNKNOWN_NAME, pos: -1, detail: bad) }
        return nil
    }

    // MARK: Örnekleme

    /// Örneklenen grafik verisi: x = 1..n için f(x) (SCATTER'da x, f(x) çiftleri).
    static func sampled(_ p: GraphParams, _ n: Int) -> [Float]? {
        if meaning(p.kind) != .SAMPLED || p.formula.allSatisfy(\.isWhitespace) { return nil }
        if error(p.kind, p.formula) != nil { return nil }
        guard let f = GExpression.parse(parts(p.formula)[0], mode(p.angleDeg)) else { return nil }
        var out: [Float] = []
        var finite = 0
        for i in 1...min(max(n, 1), 64) {
            let x = Double(i)
            let y = f(x, 0)
            let ok = y.isFinite && abs(y) < 1e7
            if ok { finite += 1 }
            let v: Float = ok ? Float((y * 1000).rounded(.toNearestOrEven) / 1000) : 0
            if p.kind == .SCATTER {
                if !ok { continue }
                out.append(Float(x)); out.append(v)
            } else {
                out.append(p.kind == .PIE ? max(v, 0) : v)
            }
        }
        return finite == 0 ? nil : out
    }

    // MARK: Sayı doğrusu: çözüm kümesi

    struct Interval: Equatable { let a: Double, b: Double, aClosed: Bool, bClosed: Bool, aInf: Bool, bInf: Bool }

    struct SolutionSet: Equatable {
        let points: [Double]
        let intervals: [Interval]
        var isEmpty: Bool { points.isEmpty && intervals.isEmpty }
    }

    /// Bir parçanın görünür aralıktaki çözüm kümesi; geçersizse nil.
    static func solve(_ text: String, _ lo: Double, _ hi: Double, deg: Bool = false) -> SolutionSet? {
        if setError(text) != nil { return nil }
        guard let rel = try? MathExpr.parseRelation(text.lowercased()) else { return nil }
        let m = mode(deg)
        let r: MRelation
        if !rel.isRelation {
            if rel.sides[0].variables().isEmpty {
                guard let v = try? MathExpr.evalStrict(rel.sides[0], [:], m) else { return nil }
                return SolutionSet(points: [v], intervals: [])
            }
            r = MRelation(sides: [rel.sides[0], .num("0")], ops: [.EQ])
        } else { r = rel }
        guard let fs = try? r.sides.map({ try MathExpr.compile($0, ["x"], m) }) else { return nil }
        func value(_ k: Int, _ x: Double) -> Double { fs[k]([x]) }
        func truth(_ x: Double) -> Bool {
            for k in r.ops.indices {
                let a = value(k, x), b = value(k + 1, x)
                if !a.isFinite || !b.isFinite { return false }
                if !r.ops[k].holds(a, b, eps: 1e-9) { return false }
            }
            return true
        }
        if r.ops.allSatisfy({ $0 == .EQ }) {
            let g = { (x: Double) in value(0, x) - value(1, x) }
            return SolutionSet(points: roots(g, lo, hi).filter { truth($0) || r.ops.count == 1 }, intervals: [])
        }
        let n = 2000
        let xs = (0...n).map { lo + (hi - lo) * Double($0) / Double(n) }
        let t = xs.map { truth($0) }
        var points: [Double] = []
        var intervals: [Interval] = []
        var i = 0
        while i <= n {
            if !t[i] { i += 1; continue }
            var j = i
            while j < n && t[j + 1] { j += 1 }
            let a = i == 0 ? lo : edge(xs[i - 1], xs[i], truth)
            let b = j == n ? hi : edge(xs[j + 1], xs[j], truth)
            if b - a < (hi - lo) * 1e-6 {
                points.append(snap(a))
            } else {
                let aInf = i == 0, bInf = j == n
                let sa = snap(a), sb = snap(b)
                intervals.append(Interval(a: sa, b: sb, aClosed: aInf || truth(sa), bClosed: bInf || truth(sb), aInf: aInf, bInf: bInf))
            }
            i = j + 1
        }
        if r.ops.contains(where: { $0 == .LE || $0 == .GE }) {
            for k in r.ops.indices {
                let g = { (x: Double) in value(k, x) - value(k + 1, x) }
                for root in roots(g, lo, hi) {
                    let s = snap(root)
                    if truth(s) && !intervals.contains(where: { s >= $0.a - 1e-9 && s <= $0.b + 1e-9 }) && !points.contains(where: { abs($0 - s) < 1e-9 }) {
                        points.append(s)
                    }
                }
            }
        }
        return SolutionSet(points: points.sorted(), intervals: intervals)
    }

    private static func edge(_ outside: Double, _ inside: Double, _ truth: (Double) -> Bool) -> Double {
        var a = outside, b = inside
        for _ in 0..<60 {
            let m = 0.5 * (a + b)
            if truth(m) { b = m } else { a = m }
        }
        return 0.5 * (a + b)
    }

    /// g'nin [lo, hi]'deki kökleri (işaret değişimi ve dokunma).
    private static func roots(_ g: (Double) -> Double, _ lo: Double, _ hi: Double) -> [Double] {
        let n = 4000
        var out: [Double] = []
        var px = lo
        var pv = g(lo)
        if pv == 0 { out.append(lo) }
        for k in 1...n {
            let x = lo + (hi - lo) * Double(k) / Double(n)
            let v = g(x)
            if v == 0 { out.append(x) }
            else if pv.isFinite && v.isFinite && pv != 0 && (pv < 0) != (v < 0) {
                var a = px, b = x, fa = pv
                for _ in 0..<80 {
                    let m = 0.5 * (a + b)
                    let fm = g(m)
                    if !fm.isFinite { continue }
                    if (fm < 0) == (fa < 0) { a = m; fa = fm } else { b = m }
                }
                let root = 0.5 * (a + b)
                if abs(g(root)) < 1e-6 * (1 + abs(pv) + abs(v)) { out.append(root) }
            }
            px = x; pv = v
        }
        let step = (hi - lo) / Double(n)
        for k in 1..<n {
            let x = lo + step * Double(k)
            let a = abs(g(x - step)), b = abs(g(x)), c = abs(g(x + step))
            if b <= a && b <= c && b < 1e-3 * step {
                var l = x - step, r = x + step
                for _ in 0..<80 {
                    let m1 = l + (r - l) / 3, m2 = r - (r - l) / 3
                    if abs(g(m1)) < abs(g(m2)) { r = m2 } else { l = m1 }
                }
                let m = 0.5 * (l + r)
                if abs(g(m)) < 1e-9 { out.append(m) }
            }
        }
        let sorted = out.map { snap($0) }.sorted()
        var dedup: [Double] = []
        for v in sorted where dedup.isEmpty || abs(dedup.last! - v) > 1e-6 * (1 + abs(v)) { dedup.append(v) }
        return dedup
    }

    /// Küçük yuvarlama hatalarını temizler.
    static func snap(_ v: Double) -> Double {
        for d in [1, 2, 3, 4, 5, 6, 8, 10, 100, 1000] {
            let r = (v * Double(d)).rounded(.toNearestOrEven) / Double(d)
            if abs(r - v) < 1e-7 { return r + 0.0 }
        }
        return v
    }

    /// Sayı doğrusundaki formül yazısı.
    static func label(_ text: String) -> String {
        let (rel, err) = MathExpr.tryParseRelation(text.lowercased())
        guard err == nil, let rel else { return text }
        if !rel.isRelation && rel.sides[0].variables().isEmpty { return "x = " + MathExpr.pretty(rel.sides[0]) }
        return MathExpr.pretty(rel)
    }
}
