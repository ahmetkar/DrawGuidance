import Foundation

/*
 * v45 parametreli (formülle ve tutamakla düzenlenen) fen ve geometri şekilleri (Android `ParamShapes.kt`).
 * Değerler şekil elemanında saklanır (ShapeSpec.params: anahtar → metin). Model her değer kümesinden çizimi, köşedeki
 * formül yazısını ("formula" etiketli) ve sürüklenebilir tutamakları üretir.
 */

/// Değerin türü. ANGLE derece cinsindendir. TEXT serbest metin (ör. molekül formülü).
enum ParamKind { case NUMBER, INT, ANGLE, TEXT }

/// Değer tanımı.
struct ParamDef {
    let key: String
    let symbol: String
    let title: String
    var kind: ParamKind = .NUMBER
    var `default`: Double = 0
    var min: Double = -1e9
    var max: Double = 1e9
    var step: Double = 1
    var unit: String = ""
    var defaultText: String = ""
    /// Yalnız formülle değişir.
    var advanced = false
    /// TEXT değerinin seçenekleri.
    var choices: [String] = []

    init(_ key: String, _ symbol: String, _ title: String, kind: ParamKind = .NUMBER, default def: Double = 0,
         min: Double = -1e9, max: Double = 1e9, step: Double = 1, unit: String = "", defaultText: String = "",
         advanced: Bool = false, choices: [String] = []) {
        self.key = key; self.symbol = symbol; self.title = title; self.kind = kind; self.default = def
        self.min = min; self.max = max; self.step = step; self.unit = unit; self.defaultText = defaultText
        self.advanced = advanced; self.choices = choices
    }
}

/// Türetilmiş büyüklük: yazılırsa `solveFor` girişi çözülür.
struct Derived {
    let symbol: String
    let title: String
    var unit: String = ""
    var solveFor: String? = nil
    let value: (ParamValues) -> Double

    init(_ symbol: String, _ title: String, unit: String = "", solveFor: String? = nil, _ value: @escaping (ParamValues) -> Double) {
        self.symbol = symbol; self.title = title; self.unit = unit; self.solveFor = solveFor; self.value = value
    }
}

/// Bir şeklin değerleri (eksikler varsayılan).
final class ParamValues {
    let model: ParamModel
    let raw: [String: String]
    private var cache: [String: Double] = [:]

    init(_ model: ParamModel, _ raw: [String: String]) { self.model = model; self.raw = raw }

    func num(_ key: String) -> Double {
        if let c = cache[key] { return c }
        guard let def = model.def(key) else { return 0 }
        var v = def.default
        if let s = raw[key], let d = Double(s.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)), d.isFinite { v = d }
        let c = Swift.min(Swift.max(v, def.min), def.max)
        let out = def.kind == .INT ? c.rounded(.toNearestOrEven) : c
        cache[key] = out
        return out
    }

    func f(_ key: String) -> Float { Float(num(key)) }
    func i(_ key: String) -> Int { Int(num(key)) }

    func text(_ key: String) -> String {
        if let s = raw[key], !s.trimmingCharacters(in: .whitespaces).isEmpty { return s }
        return model.def(key)?.defaultText ?? ""
    }

    subscript(_ key: String) -> Double { num(key) }

    /// Formüllerde sembol → değer (açılar derece).
    func symbols() -> [String: Double] {
        var m: [String: Double] = [:]
        for d in model.defs where d.kind != .TEXT { m[d.symbol] = num(d.key) }
        return m
    }
}

/// Parametreli şekil modeli: çizim w×h sanal birimlik eşit ölçekli tuvalde yapılır.
class ParamModel: ParamShapeModel {
    static let LABEL_COLOR = "#37474F"
    static let FRAME_TAG = "param:frame"
    static let FRAME_MARK: Float = 0.001

    let id: String
    let title: String
    let w: Float
    let h: Float
    let fit: Fit
    let tags: [String]

    init(_ id: String, _ title: String, w: Float, h: Float, fit: Fit = .BOX, tags: [String] = []) {
        self.id = id; self.title = title; self.w = w; self.h = h; self.fit = fit; self.tags = tags
    }

    var defs: [ParamDef] { [] }
    var derived: [Derived] { [] }
    var curveVar: String? { nil }
    var curveArg: String { "x" }
    func curve(_ v: ParamValues) -> ((Double) -> Double)? { nil }
    func fit(_ f: @escaping (Double) -> Double, _ v: ParamValues) -> [String: Double]? { nil }
    func label(_ v: ParamValues) -> [String] { [] }
    func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {}
    func handles(_ v: ParamValues) -> [ParamHandle] { [] }
    func drag(_ v: ParamValues, _ key: String, _ x: Float, _ y: Float) -> [String: Double]? { nil }
    var labelRight: Bool { false }
    var labelSize: Float { 0.075 }
    func invalid(_ v: ParamValues) -> String? { nil }
    func curveText(_ v: ParamValues) -> String? { nil }

    private lazy var defIndex: [String: ParamDef] = Dictionary(defs.map { ($0.key, $0) }, uniquingKeysWith: { a, _ in a })
    func def(_ key: String) -> ParamDef? { defIndex[key] }

    func values(_ raw: [String: String]?) -> ParamValues { ParamValues(self, raw ?? [:]) }

    /// Kütüphane şekli (varsayılan değerlerle çizilir); kayıt `ParamShapes` bir kez kurar.
    func makeShape() -> LibShape { LibShape(id: id, title: title, fit: fit, aspect: w / h, tags: tags, paths: paths(nil), param: self) }

    /// Değerlerle birim kutudaki çizim.
    func paths(_ raw: [String: String]?, resolution: Float = 512) -> [VPath] {
        let b = VectorBuilder(resolution: resolution)
        render(b, values(raw))
        return b.paths
    }

    private func render(_ b: VectorBuilder, _ v: ParamValues) {
        b.withTransform(Affine.scale(1 / w, 1 / h)) {
            let lb = Lb(b, w, h)
            b.line(0, 0, Self.FRAME_MARK, 0).width(0).tag(Self.FRAME_TAG)
            b.line(w - Self.FRAME_MARK, h, w, h).width(0).tag(Self.FRAME_TAG)
            draw(b, lb, v)
            drawLabel(lb, v)
        }
    }

    private func drawLabel(_ lb: Lb, _ v: ParamValues) {
        let lines = label(v).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        let size = labelSize
        for (i, s) in lines.enumerated() {
            let y = 0.02 + Float(i) * size * 1.25
            let sz = i == 0 ? size : size * 0.85
            let part = labelRight ? lb.r(w - 0.02, y + sz * 0.52, s, sz, Self.LABEL_COLOR) : lb.t(0.02, y, s, sz, Self.LABEL_COLOR)
            part.tag(i == 0 ? "formula" : "formula:\(i)")
        }
    }

    func unitHandles(_ raw: [String: String]?) -> [ParamHandle] {
        handles(values(raw)).map { ParamHandle(key: $0.key, x: $0.x / w, y: $0.y / h) }
    }

    func dragUnit(_ raw: [String: String]?, _ key: String, _ ux: Float, _ uy: Float) -> [String: String]? {
        let v = values(raw)
        guard let out = drag(v, key, ux * w, uy * h) else { return nil }
        var next = raw ?? [:]
        for (k, value) in out.sorted(by: { $0.key < $1.key }) { next = withValue(next, k, value) }
        if invalid(values(next)) != nil { return nil }
        return next == (raw ?? [:]) ? nil : next
    }

    func withValue(_ raw: [String: String]?, _ key: String, _ value: Double) -> [String: String] {
        guard let d = def(key), value.isFinite else { return raw ?? [:] }
        let c = Swift.min(Swift.max(value, d.min), d.max)
        var out = raw ?? [:]
        out[key] = Self.store(d.kind == .INT ? c.rounded(.toNearestOrEven) : c)
        return out
    }

    func setValue(_ raw: [String: String]?, _ key: String, _ value: Double) -> [String: String]? {
        let next = withValue(raw, key, value)
        return invalid(values(next)) != nil ? nil : next
    }

    func withText(_ raw: [String: String]?, _ key: String, _ text: String) -> [String: String] {
        var out = raw ?? [:]
        out[key] = text
        return out
    }

    /// Formül alanında gösterilen metin.
    func editText(_ raw: [String: String]?) -> String {
        let v = values(raw)
        if curve(v) != nil, let t = curveText(v) { return t }
        return defs.filter { $0.kind != .TEXT }.map { d in "\(d.symbol) = \(num(v[d.key]))\(d.kind == .ANGLE ? "°" : "")" }.joined(separator: "; ")
    }

    func derivedValues(_ raw: [String: String]?) -> [String: Double] {
        let v = values(raw)
        var m: [String: Double] = [:]
        for d in derived { m[d.symbol] = d.value(v) }
        return m
    }

    private lazy var names: Set<String> = Set((defs.map(\.symbol) + derived.map(\.symbol)).filter { $0.count > 1 && !$0.contains("_") })

    func apply(_ raw: [String: String]?, _ text: String) -> ParamApply {
        var cur = raw ?? [:]
        let parts = text.split(whereSeparator: { $0 == ";" || $0 == "\n" }).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        if parts.isEmpty { return .failed(ParamError(code: .SYNTAX, math: MathError(code: .EMPTY))) }
        for part in parts {
            switch applyPart(cur, part) {
            case .ok(let p): cur = p
            case .failed(let e): return .failed(e)
            }
        }
        if let bad = invalid(values(cur)) { return failed(.OUT_OF_RANGE, bad) }
        return .ok(cur)
    }

    private func failed(_ code: ParamError.Code, _ detail: String = "", math: MathError? = nil) -> ParamApply {
        .failed(ParamError(code: code, detail: detail, math: math))
    }

    private struct UnknownSymbol: Error { let symbol: String }

    private func applyPart(_ raw: [String: String], _ part: String) -> ParamApply {
        let rel: MRelation
        do { rel = try MathExpr.parseRelation(part, names: names) } catch let e as MathException { return failed(.SYNTAX, math: e.error) } catch { return failed(.SYNTAX) }
        let v = values(raw)
        var known = v.symbols()
        for d in derived { known[d.symbol] = d.value(v) }
        let cv = curveVar
        let sides: [MNode] = (!rel.isRelation && cv != nil) ? [.variable(cv!), rel.sides[0]] : rel.sides
        if sides.count != 2 || (rel.isRelation && (rel.ops.count != 1 || rel.ops[0] != .EQ)) { return failed(.NO_MATCH) }
        let lhs = sides[0], rhs = sides[1]
        func evalRhs(_ node: MNode, _ extra: [String: Double] = [:]) throws -> Double {
            if let u = node.variables().first(where: { known[$0] == nil && extra[$0] == nil }) { throw UnknownSymbol(symbol: u) }
            return try MathExpr.evalStrict(node, known.merging(extra) { _, b in b }, .DEG)
        }
        do {
            if case let .variable(name) = lhs {
                if let d = defs.first(where: { $0.symbol == name && $0.kind != .TEXT }), cv != name {
                    let value = try evalRhs(rhs)
                    if value < d.min - 1e-9 || value > d.max + 1e-9 { return failed(.OUT_OF_RANGE, d.symbol) }
                    return .ok(withValue(raw, d.key, value))
                }
                if let dv = derived.first(where: { $0.symbol == name }), cv != name || !rhs.variables().contains(curveArg) {
                    let target = try evalRhs(rhs)
                    guard let key = dv.solveFor else { return failed(.NO_MATCH, dv.symbol) }
                    guard let solved = solve(raw, key, { dv.value($0) - target }) else { return failed(.OUT_OF_RANGE, dv.symbol) }
                    return .ok(solved)
                }
                if let cv, name == cv {
                    let arg = curveArg
                    if let u = rhs.variables().first(where: { $0 != arg && known[$0] == nil }) { return failed(.UNKNOWN_SYMBOL, u) }
                    let base = known
                    // v45: eğride x bir sayıdır (açı değil): sin(kx) radyan
                    let f: (Double) -> Double = { x in
                        var vars = base; vars[arg] = x
                        return (try? MathExpr.eval(rhs, vars, .RAD)) ?? .nan
                    }
                    guard let fitted = fit(f, v) else { return failed(.NO_MATCH) }
                    var next = raw
                    for (k, value) in fitted.sorted(by: { $0.key < $1.key }) {
                        guard let dd = def(k) else { continue }
                        if value < dd.min - 1e-6 || value > dd.max + 1e-6 || !value.isFinite { return failed(.OUT_OF_RANGE, dd.symbol) }
                        next = withValue(next, k, value)
                    }
                    return .ok(next)
                }
                return failed(.UNKNOWN_SYMBOL, name)
            }
            let a = try evalRhs(lhs), b = try evalRhs(rhs)
            return Rel.EQ.holds(a, b, eps: 1e-6) ? .ok(raw) : failed(.NO_MATCH)
        } catch let e as UnknownSymbol {
            return failed(.UNKNOWN_SYMBOL, e.symbol)
        } catch let e as MathException {
            return e.error.code == .UNKNOWN_NAME ? failed(.UNKNOWN_SYMBOL, e.error.detail) : failed(.NOT_NUMBER, math: e.error)
        } catch {
            return failed(.NOT_NUMBER)
        }
    }

    /// `key` girişini aralığında tarayıp g = 0 yapan değeri bulur.
    func solve(_ raw: [String: String], _ key: String, _ g: (ParamValues) -> Double) -> [String: String]? {
        guard let d = def(key) else { return nil }
        func at(_ x: Double) -> Double { var r = raw; r[key] = Self.store(x); return g(values(r)) }
        let n = 400
        var best: [String: String]?
        var px = d.min
        var pv = at(px)
        for i in 1...n {
            let x = d.min + (d.max - d.min) * Double(i) / Double(n)
            let gv = at(x)
            if pv.isFinite && gv.isFinite && (pv == 0 || (pv < 0) != (gv < 0)) {
                var a = px, b = x, fa = pv
                for _ in 0..<70 {
                    let m = 0.5 * (a + b)
                    let fm = at(m)
                    if !fm.isFinite { continue }
                    if (fm < 0) == (fa < 0) { a = m; fa = fm } else { b = m }
                }
                let root = 0.5 * (a + b)
                if abs(at(root)) <= 1e-3 * (abs(pv) + abs(gv)) + 1e-9 {
                    let err = abs(at(root)) + 1e-9 * (1 + abs(pv) + abs(gv))
                    var nice = root
                    for k in 0...6 {
                        let p = pow(10.0, Double(k))
                        let c = (root * p).rounded(.toNearestOrEven) / p
                        if abs(at(c)) <= err { nice = c; break }
                    }
                    best = withValue(raw, key, nice)
                    break
                }
            }
            px = x; pv = gv
        }
        guard let b = best else { return nil }
        if d.kind != .INT { return b }
        let n0 = values(b)[key]
        let check = g(values(b))
        let near = abs(at(n0 - 1)) + abs(at(n0 + 1))
        return check.isFinite && abs(check) <= 1e-6 * (1 + near) ? b : nil
    }

    /// Kayıtta saklanan sayı (en çok 6 ondalık, yerel ayardan bağımsız).
    static func store(_ v: Double) -> String {
        let r = (v * 1e6).rounded(.toNearestOrEven) / 1e6
        if r == r.rounded(.toNearestOrEven) && abs(r) < 1e15 { return String(Int64(r)) }
        return trimZeros(String(format: "%.6f", r))
    }

    /// Gösterilen sayı: en çok `dec` ondalık, Türkçe virgül.
    static func num(_ v: Double, _ dec: Int = 2) -> String {
        if !v.isFinite { return "∞" }
        let p = pow(10.0, Double(dec))
        let r = (v * p).rounded(.toNearestOrEven) / p
        let s = r == r.rounded(.toNearestOrEven) && abs(r) < 1e15 ? String(Int64(r)) : trimZeros(String(format: "%.\(dec)f", r))
        return s.replacingOccurrences(of: ".", with: ",").replacingOccurrences(of: "-", with: "−")
    }

    func num(_ v: Double, _ dec: Int = 2) -> String { Self.num(v, dec) }

    private static func trimZeros(_ s: String) -> String {
        guard s.contains(".") else { return s }
        var t = s
        while t.hasSuffix("0") { t.removeLast() }
        if t.hasSuffix(".") { t.removeLast() }
        return t
    }
}
