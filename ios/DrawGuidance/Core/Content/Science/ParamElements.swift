import Foundation

// v46 (4) yazılan elemente göre yeniden çizilen element şekilleri (Android `ParamElements.kt`).

class ElementModel: ParamModel {
    private let defaultZ: Int

    init(_ id: String, _ title: String, w: Float, h: Float, fit: Fit, tags: [String], defaultZ: Int) {
        self.defaultZ = defaultZ
        super.init(id, title, w: w, h: h, fit: fit, tags: tags + ["element", "formül", "kimya"])
    }

    private lazy var _defs = [ParamDef("z", "Z", "Atom numarası", kind: .INT, default: Double(defaultZ), min: 1, max: 118, step: 1)]
    override var defs: [ParamDef] { _defs }

    func element(_ v: ParamValues) -> PtElement { PeriodicTable[Int(v.num("z"))] ?? PeriodicTable[defaultZ]! }

    override func editText(_ raw: [String: String]?) -> String { element(values(raw)).symbol }

    override func apply(_ raw: [String: String]?, _ text: String) -> ParamApply {
        // "Z = 11" ya da yalın "11", "Na", "sodyum"
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        var t = trimmed
        if let r = trimmed.range(of: "^[Zz]\\s*=\\s*(.+)$", options: .regularExpression) {
            let s = String(trimmed[r])
            if let eq = s.firstIndex(of: "=") { t = s[s.index(after: eq)...].trimmingCharacters(in: .whitespaces) }
        }
        if let e = PeriodicTable.find(t) { return .ok(withValue(raw, "z", Double(e.z))) }
        return super.apply(raw, text)
    }
}

/// Periyodik tablo hücresi.
final class PeriodicCellModel: ElementModel {
    static let shared = PeriodicCellModel()
    private init() {
        super.init("sci.chem.periodic_cell", "Periyodik tablo hücresi", w: 0.9, h: 1, fit: .BOX,
                   tags: ["periyodik tablo", "atom numarası", "kütle numarası", "sembol"], defaultZ: 6)
    }
    override func label(_ v: ParamValues) -> [String] { [element(v).kind.title] }
    override var labelRight: Bool { true }
    override var labelSize: Float { 0.045 }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let e = element(v)
        b.roundRect(0.05, 0.1, 0.8, 0.85, 0.04).fill(e.kind.color).width(1.5).tag("cell")
        lb.t(0.11, 0.15, "\(e.z)", 0.12).tag("z")
        let symSize: Float = e.symbol.count > 1 ? 0.3 : 0.36
        lb.c(0.45, 0.49, e.symbol, symSize).tag("symbol")
        let nameSize = min(0.1, 0.62 / Float(max(1, e.name.count)) / 0.56)
        lb.c(0.45, 0.75, e.name, nameSize).tag("name")
        let mass = e.stable ? ParamModel.num(e.mass, 3) : "(\(e.massNumber))"
        lb.c(0.45, 0.87, mass, 0.085).tag("mass")
    }
}

/// Bohr atom modeli.
final class BohrAtomModel: ElementModel {
    static let shared = BohrAtomModel()
    private init() {
        super.init("sci.chem.bohr_atom", "Bohr atom modeli", w: 1, h: 1, fit: .SQUARE,
                   tags: ["atom", "elektron", "çekirdek", "katman", "yörünge", "proton", "nötron"], defaultZ: 11)
    }
    override func label(_ v: ParamValues) -> [String] {
        let e = element(v)
        return ["\(e.symbol): \(e.shells.map(String.init).joined(separator: ", "))"]
    }
    override var labelSize: Float { 0.06 }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        let e = element(v)
        let shells = e.shells
        let c: Float = 0.5, nucR: Float = 0.085, outer: Float = 0.41
        let gap = (outer - nucR - 0.03) / Float(shells.count)
        let radii = shells.indices.map { nucR + 0.03 + gap * Float($0 + 1) }
        for r in radii { b.circle(c, c, r).width(0.9).tag("shell") }
        b.circle(c, c, nucR).fill("#FFCDD2").width(1).tag("nucleus")
        lb.c(c, c - 0.025, "\(e.z)p", 0.042, "#C62828")
        lb.c(c, c + 0.03, "\(e.neutrons)n", 0.042, "#455A64")
        for k in shells.indices {
            let n = shells[k]
            let r = radii[k]
            let er = min(0.022, (2 * Float.pi * r / Float(n)) * 0.32)
            for i in 0..<n {
                let p = onCircle(c, c, r, -90 + 360 * Float(i) / Float(n) + Float(k) * 17)
                b.circle(p.x, p.y, er).fill(Sc.BLUE).width(0.6).tag("electron")
            }
        }
        lb.c(c, 0.96, e.name, 0.05)
    }
}
