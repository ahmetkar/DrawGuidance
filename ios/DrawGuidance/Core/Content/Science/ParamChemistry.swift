import Foundation

// v45 (8) formülden çizilen molekül şekilleri (Android `ParamChemistry.kt`).

/// Molekül görünümleri (kayıtta saklanan anahtarlar).
enum MolView {
    static let STRUCTURAL = "structural"
    static let CONDENSED = "condensed"
    static let SKELETAL = "skeletal"
    static let BALL = "3d"
    static let all = [STRUCTURAL, CONDENSED, SKELETAL, BALL]
}

final class MoleculeModel: ParamModel {
    private let defaultFormula: String
    private let defaultView: String

    init(_ id: String, _ title: String, _ defaultFormula: String, _ defaultView: String, w: Float, h: Float, fit: Fit = .BOX, tags: [String]) {
        self.defaultFormula = defaultFormula
        self.defaultView = defaultView
        super.init(id, title, w: w, h: h, fit: fit, tags: tags + ["molekül", "formül", "kimya"])
    }

    private lazy var _defs = [
        ParamDef("formula", "", "Formül", kind: .TEXT, defaultText: defaultFormula),
        ParamDef("view", "", "Görünüm", kind: .TEXT, defaultText: defaultView, choices: MolView.all),
    ]
    override var defs: [ParamDef] { _defs }

    override func editText(_ raw: [String: String]?) -> String { values(raw).text("formula") }

    private static var cache: [String: ChemResult?] = [:]
    private static let lock = NSLock()

    /// Formül → okuma sonucu (son okunanlar önbellekte).
    func result(_ formula: String) -> ChemResult? {
        if let hit = Self.lock.withLock({ Self.cache[formula] }) { return hit }
        let r = Chem.tryRead(formula).0
        Self.store(formula, r)
        return r
    }

    private static func store(_ key: String, _ r: ChemResult?) {
        lock.withLock {
            if cache.count >= 64 { cache.removeAll() }
            cache[key] = .some(r)
        }
    }

    override func apply(_ raw: [String: String]?, _ text: String) -> ParamApply {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let (res, err) = Chem.tryRead(t)
        guard let res else { return .failed(ParamError(code: .CHEMISTRY, detail: err?.description ?? "")) }
        Self.store(t, res)
        var out = raw ?? [:]
        out["formula"] = t
        return .ok(out)
    }

    override func label(_ v: ParamValues) -> [String] {
        let f = v.text("formula")
        guard let r = result(f) else { return [Chem.pretty(f)] }
        let hill = Chem.pretty(r.mol.hill())
        let first = r.kind == .NAME ? hill : Chem.pretty(f)
        let name = r.example ? "örnek yapı" : (r.known?.name ?? "")
        let second = [name.isEmpty ? nil : name, hill != first ? hill : nil, "M = \(num(r.mol.mass(), 2)) g/mol"].compactMap { $0 }.joined(separator: " · ")
        return [first, second]
    }

    override var labelSize: Float { 0.07 * min(w, h) }
    private var top: Float { labelSize * 2.6 + 0.02 }

    override func draw(_ b: VectorBuilder, _ lb: Lb, _ v: ParamValues) {
        guard let r = result(v.text("formula")) ?? result(defaultFormula) else { return }
        let view = MolView.all.contains(v.text("view")) ? v.text("view") : defaultView
        switch view {
        case MolView.BALL: drawBall(b, lb, r.mol)
        case MolView.CONDENSED: draw2D(b, lb, r.mol, MolView.CONDENSED)
        case MolView.SKELETAL: draw2D(b, lb, r.mol, MolView.SKELETAL)
        default: draw2D(b, lb, r.mol.explicitH(), MolView.STRUCTURAL)
        }
    }

    // MARK: 2B

    private func sup(_ charge: Int) -> String {
        if charge == 0 { return "" }
        let sign = charge > 0 ? "⁺" : "⁻"
        if abs(charge) == 1 { return sign }
        let digits = Array("²³⁴⁵⁶")
        let k = abs(charge) - 2
        return (k < digits.count ? String(digits[k]) : "") + sign
    }

    private func hs(_ n: Int) -> String {
        switch n {
        case 0: return ""
        case 1: return "H"
        default: let d = Array("₀₁₂₃₄₅₆₇₈₉"); return "H" + (n < d.count ? String(d[n]) : String(n))
        }
    }

    private func atomText(_ m: Molecule, _ i: Int, _ view: String, _ left: Bool) -> String {
        let a = m.atoms[i]
        let c = sup(a.charge)
        switch view {
        case MolView.STRUCTURAL: return a.el + c
        case MolView.SKELETAL:
            if a.el == "C" && a.charge == 0 && m.degree(i) > 0 { return "" }
            return left ? hs(a.h) + a.el + c : a.el + hs(a.h) + c
        default: return left ? hs(a.h) + a.el + c : a.el + hs(a.h) + c
        }
    }

    private func textWidth(_ s: String, _ size: Float) -> Float {
        var sum = 0.0
        for ch in s { sum += "₀₁₂₃₄₅₆₇₈₉⁺⁻²³".contains(ch) ? 0.4 : ch.isUppercase ? 0.68 : 0.56 }
        return Float(sum) * size
    }

    private func draw2D(_ b: VectorBuilder, _ lb: Lb, _ m: Molecule, _ view: String) {
        let pts = Layout2D.layout(m, crossH: view == MolView.STRUCTURAL)
        if pts.isEmpty { return }
        let minX = pts.map(\.x).min()!, maxX = pts.map(\.x).max()!
        let minY = pts.map(\.y).min()!, maxY = pts.map(\.y).max()!
        let availW = w - 0.08, availH = h - top - 0.04
        let pad = 1.1
        let s = Float(min(min(Double(availW) / (maxX - minX + pad), Double(availH) / (maxY - minY + pad)), 0.3 * Double(min(w, h))))
        let ox = 0.04 + (availW - Float(maxX - minX) * s) / 2
        let oy = top + (availH - Float(maxY - minY) * s) / 2
        func at(_ i: Int) -> P { P(ox + Float(pts[i].x - minX) * s, oy + Float(maxY - pts[i].y) * s) }
        let fs = s * (view == MolView.STRUCTURAL ? 0.42 : 0.38)
        let texts = m.atoms.indices.map { i -> String in
            let p = at(i)
            let nbs = m.neighbors(i)
            let left = !nbs.isEmpty && nbs.allSatisfy { at($0).x > p.x + 0.01 }
            return atomText(m, i, view, left)
        }
        func gap(_ i: Int, _ dx: Float, _ dy: Float) -> Float {
            let t = texts[i]
            if t.isEmpty { return 0 }
            let hw = textWidth(t, fs) / 2 + fs * 0.12
            let hh = fs * 0.55
            let ax = abs(dx), ay = abs(dy)
            return min(min(ax < 1e-6 ? 9 : hw / ax, ay < 1e-6 ? 9 : hh / ay), 0.45 * s)
        }
        let rings = m.rings()
        for bd in m.bonds {
            let pa = at(bd.a), pb = at(bd.b)
            let len = hypotf(pb.x - pa.x, pb.y - pa.y)
            if len < 1e-6 { continue }
            let ux = (pb.x - pa.x) / len, uy = (pb.y - pa.y) / len
            let ga = gap(bd.a, ux, uy), gb = gap(bd.b, ux, uy)
            let a = P(pa.x + ux * ga, pa.y + uy * ga), c = P(pb.x - ux * gb, pb.y - uy * gb)
            let nx = -uy, ny = ux
            let off = s * 0.1
            let ring = rings.first { $0.contains(bd.a) && $0.contains(bd.b) }
            switch bd.order {
            case 1:
                b.line(a.x, a.y, c.x, c.y).width(1.3).tag("bond")
            case 2:
                if let ring {
                    let cx = ring.map { at($0).x }.reduce(0, +) / Float(ring.count)
                    let cy = ring.map { at($0).y }.reduce(0, +) / Float(ring.count)
                    let side: Float = (cx - (pa.x + pb.x) / 2) * nx + (cy - (pa.y + pb.y) / 2) * ny > 0 ? 1 : -1
                    b.line(a.x, a.y, c.x, c.y).width(1.3).tag("bond")
                    let sx = side * nx * off * 1.4, sy = side * ny * off * 1.4
                    let k = 0.16 * len
                    b.line(a.x + sx + ux * k, a.y + sy + uy * k, c.x + sx - ux * k, c.y + sy - uy * k).width(1.3).tag("bond")
                } else {
                    b.line(a.x + nx * off, a.y + ny * off, c.x + nx * off, c.y + ny * off).width(1.3).tag("bond")
                    b.line(a.x - nx * off, a.y - ny * off, c.x - nx * off, c.y - ny * off).width(1.3).tag("bond")
                }
            default:
                b.line(a.x, a.y, c.x, c.y).width(1.2).tag("bond")
                let o3 = off * 1.3
                b.line(a.x + nx * o3, a.y + ny * o3, c.x + nx * o3, c.y + ny * o3).width(1.2).tag("bond")
                b.line(a.x - nx * o3, a.y - ny * o3, c.x - nx * o3, c.y - ny * o3).width(1.2).tag("bond")
            }
        }
        for i in m.atoms.indices {
            let t = texts[i]
            if t.isEmpty { continue }
            let p = at(i)
            let col: String?
            switch m.atoms[i].el {
            case "O": col = "#C62828"
            case "N": col = "#1565C0"
            case "S": col = "#9E7C00"
            case "Cl", "F": col = "#2E7D32"
            case "Br": col = "#6D4C41"
            case "P": col = "#E65100"
            default: col = nil
            }
            lb.c(p.x, p.y, t, fs, col).tag("atom:\(i)")
        }
    }

    // MARK: 3B top-çubuk

    private func light(_ hex: String) -> Bool {
        let c = Int(hex.hasPrefix("#") ? String(hex.dropFirst()) : hex, radix: 16) ?? 0
        let r = (c >> 16) & 255, g = (c >> 8) & 255, bl = c & 255
        return 0.299 * Double(r) + 0.587 * Double(g) + 0.114 * Double(bl) > 150
    }

    private func ballR(_ el: String) -> Double { 0.2 + 0.3 * (Elements[el]?.radius ?? 0.7) }

    private func drawBall(_ b: VectorBuilder, _ lb: Lb, _ mol: Molecule) {
        let m = mol.explicitH()
        let raw = Embed3D.embed(m)
        let p = Embed3D.planar(raw) ? raw : Embed3D.view(raw, 22, 16)
        let rad = m.atoms.map { ballR($0.el) }
        let minX = p.indices.map { p[$0].x - rad[$0] }.min()!, maxX = p.indices.map { p[$0].x + rad[$0] }.max()!
        let minY = p.indices.map { p[$0].y - rad[$0] }.min()!, maxY = p.indices.map { p[$0].y + rad[$0] }.max()!
        let availW = w - 0.08, availH = h - top - 0.04
        let s = Float(min(min(Double(availW) / (maxX - minX), Double(availH) / (maxY - minY)), 0.34 * Double(min(w, h))))
        let ox = 0.04 + (availW - Float(maxX - minX) * s) / 2
        let oy = top + (availH - Float(maxY - minY) * s) / 2
        let zMax = max(1.0, p.map { abs($0.z) }.max() ?? 0)
        func at(_ i: Int) -> P { P(ox + Float(p[i].x - minX) * s, oy + Float(maxY - p[i].y) * s) }
        func persp(_ i: Int) -> Float { Float(1 + 0.08 * p[i].z / zMax) }
        let sortedBonds = m.bonds.enumerated().sorted { l, r in
            let zl = (p[l.element.a].z + p[l.element.b].z) / 2, zr = (p[r.element.a].z + p[r.element.b].z) / 2
            return zl != zr ? zl < zr : l.offset < r.offset
        }.map(\.element)
        for bd in sortedBonds {
            let a = at(bd.a), c = at(bd.b)
            let hw = s * 0.07
            if bd.order == 1 { b.bar(a.x, a.y, c.x, c.y, hw).fill("#B8BEC6").width(0.8).tag("bond") }
            else {
                let u = sciUnit(c.x - a.x, c.y - a.y)
                let k = bd.order
                for j in 0..<k {
                    let o = (Float(j) - Float(k - 1) / 2) * hw * 1.9
                    b.bar(a.x - u.y * o, a.y + u.x * o, c.x - u.y * o, c.y + u.x * o, hw * 0.6).fill("#B8BEC6").width(0.7).tag("bond")
                }
            }
        }
        let order = m.atoms.indices.sorted { p[$0].z != p[$1].z ? p[$0].z < p[$1].z : $0 < $1 }
        for i in order {
            let col = Elements[m.atoms[i].el]?.color ?? "#9E9E9E"
            let c = at(i)
            let r = Float(rad[i]) * s * persp(i)
            b.circle(c.x, c.y, r).fill(col).width(1).tag("atom:\(i)")
            b.shine(c.x, c.y, r)
            lb.c(c.x, c.y + r * 0.04, m.atoms[i].el + sup(m.atoms[i].charge), r * 0.95, light(col) ? nil : "#FFFFFF")
        }
    }
}

/// Kimya kategorisindeki formülden çizilen moleküller.
enum Molecules {
    static let water = MoleculeModel("sci.chem.water_molecule", "Su molekülü (H₂O)", "H2O", MolView.BALL, w: 1.2, h: 1,
                                     tags: ["su", "molekül", "top çubuk", "bağ açısı", "kovalent bağ"])
    static let methane = MoleculeModel("sci.chem.methane", "Metan molekülü (CH₄)", "CH4", MolView.BALL, w: 1, h: 1, fit: .SQUARE,
                                       tags: ["metan", "molekül", "top çubuk", "tetrahedral", "organik", "karbon"])
    static let benzene = MoleculeModel("sci.chem.benzene", "Benzen halkası (C₆H₆)", "C6H6", MolView.STRUCTURAL, w: 1, h: 1, fit: .SQUARE,
                                       tags: ["benzen", "aromatik", "halka", "organik", "kekule"])
    static let any2d = MoleculeModel("sci.chem.molecule", "Molekül (formülden)", "C2H5OH", MolView.STRUCTURAL, w: 1.4, h: 1,
                                     tags: ["molekül", "yapı formülü", "açık formül", "organik", "smiles"])
    static let any3d = MoleculeModel("sci.chem.molecule_3d", "Molekül modeli (3B)", "NH3", MolView.BALL, w: 1.2, h: 1,
                                     tags: ["molekül", "top çubuk", "vsepr", "molekül geometrisi", "3b"])
    static let propane = MoleculeModel("sci.chem.org.propane", "Propan (alkan)", "CH3CH2CH3", MolView.STRUCTURAL, w: 1.4, h: 1,
                                       tags: ["alkan", "propan", "doymuş hidrokarbon", "organik"])
    static let ethylene = MoleculeModel("sci.chem.org.ethylene", "Eten (alken)", "CH2=CH2", MolView.STRUCTURAL, w: 1.4, h: 1,
                                        tags: ["alken", "eten", "etilen", "çift bağ", "organik"])
    static let ethanol = MoleculeModel("sci.chem.org.ethanol", "Etanol (alkol)", "CH3CH2OH", MolView.STRUCTURAL, w: 1.4, h: 1,
                                       tags: ["alkol", "etanol", "hidroksil", "organik"])
    static let aceticAcid = MoleculeModel("sci.chem.org.acetic_acid", "Asetik asit (karboksilli asit)", "CH3COOH", MolView.STRUCTURAL, w: 1.4, h: 1,
                                          tags: ["karboksilli asit", "asetik asit", "sirke", "karboksil", "organik"])
    static let phenol = MoleculeModel("sci.chem.org.phenol", "Fenol (benzen türevi)", "C6H5OH", MolView.STRUCTURAL, w: 1.4, h: 1,
                                      tags: ["benzen türevi", "fenol", "aromatik", "organik"])
    static let toluene = MoleculeModel("sci.chem.org.toluene", "Toluen (benzen türevi)", "C6H5CH3", MolView.SKELETAL, w: 1.2, h: 1,
                                       tags: ["benzen türevi", "toluen", "aromatik", "iskelet formül", "organik"])
    static let glycine = MoleculeModel("sci.chem.org.glycine", "Glisin (amino asit)", "NH2CH2COOH", MolView.STRUCTURAL, w: 1.4, h: 1,
                                       tags: ["amino asit", "glisin", "protein", "biyokimya", "organik"])
    static let alanine = MoleculeModel("sci.chem.org.alanine", "Alanin (amino asit)", "CH3CH(NH2)COOH", MolView.STRUCTURAL, w: 1.4, h: 1,
                                       tags: ["amino asit", "alanin", "protein", "biyokimya", "organik"])
    static let glucose = MoleculeModel("sci.chem.org.glucose", "Glikoz (C₆H₁₂O₆)", "C6H12O6", MolView.SKELETAL, w: 1.2, h: 1,
                                       tags: ["glikoz", "şeker", "karbonhidrat", "monosakkarit", "biyokimya", "organik"])

    static let all: [MoleculeModel] = [water, methane, benzene, any2d, any3d, propane, ethylene, ethanol, aceticAcid, phenol, toluene, glycine, alanine, glucose]
}
