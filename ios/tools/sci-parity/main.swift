import Foundation

var failures = 0
var checks = 0
func check(_ c: Bool, _ msg: @autoclosure () -> String, line: Int = #line) { checks += 1; if !c { failures += 1; print("FAIL L\(line): \(msg())") } }
func eq<T: Equatable>(_ a: T, _ b: T, _ msg: String = "", line: Int = #line) { check(a == b, "\(msg) beklenen \(a) bulunan \(b)", line: line) }
func near(_ a: Double, _ b: Double, _ eps: Double, _ msg: String = "", line: Int = #line) { check(abs(a - b) <= eps, "\(msg) beklenen \(a) bulunan \(b)", line: line) }
func ok(_ r: ParamApply, line: Int = #line) -> [String: String] {
    if case let .ok(p) = r { return p }
    check(false, "uygulanmalıydı: \(r)", line: line); return [:]
}
@discardableResult func fail(_ r: ParamApply, _ code: ParamError.Code, line: Int = #line) -> ParamError {
    if case let .failed(e) = r { eq(code, e.code, "hata kodu", line: line); return e }
    check(false, "hata beklenirdi \(code): \(r)", line: line); return ParamError(code: .SYNTAX)
}
func num(_ raw: [String: String], _ k: String) -> Double { Double(raw[k] ?? "nan") ?? .nan }
func geometry(_ paths: [VPath]) -> [Float] { paths.filter { $0.label == nil }.flatMap { $0.points.flatMap { [$0.x, $0.y] } } }
func formulaPaths(_ paths: [VPath]) -> [VPath] { paths.filter { $0.tag == "formula" || ($0.tag?.hasPrefix("formula:") ?? false) } }
func read(_ t: String) -> ChemResult { let (r, e) = Chem.tryRead(t); if r == nil { check(false, "okunamadı \(t): \(String(describing: e))") }; return r ?? Chem.tryRead("H2O").0! }
func err(_ t: String, line: Int = #line) -> ChemError { let (r, e) = Chem.tryRead(t); check(r == nil, "hata beklenirdi \(t) → \(r?.mol.hill() ?? "")", line: line); return e ?? ChemError(code: .EMPTY) }
func orders(_ m: Molecule) -> [Int] { m.bonds.map(\.order).sorted() }

// ---------------- kimya
let table: [(String, String, String)] = [
    ("H2O", "H2O", "su"), ("CO2", "CO2", "karbon dioksit"), ("CH4", "CH4", "metan"),
    ("NH3", "H3N", "amonyak"), ("C2H5OH", "C2H6O", "etanol"), ("CH3CH2OH", "C2H6O", "etanol"),
    ("CH3-CH2-OH", "C2H6O", "etanol"), ("CH3COOH", "C2H4O2", "asetik asit"), ("HCOOH", "CH2O2", "formik asit"),
    ("CH2=CH2", "C2H4", "eten"), ("HC≡CH", "C2H2", "etin"), ("CH3CH(CH3)CH3", "C4H10", "izobütan"),
    ("C6H6", "C6H6", "benzen"), ("C6H5OH", "C6H6O", "fenol"), ("C6H5COOH", "C7H6O2", "benzoik asit"),
    ("C6H5CH3", "C7H8", "toluen"), ("C6H12O6", "C6H12O6", "glikoz"), ("C2H6O", "C2H6O", "etanol"),
    ("CH3OCH3", "C2H6O", "dimetil eter"), ("CH3CHO", "C2H4O", "asetaldehit"), ("CH3COCH3", "C3H6O", "aseton"),
    ("CH3CN", "C2H3N", "asetonitril"), ("NH2CH2COOH", "C2H5NO2", "glisin"), ("CH3CH(NH2)COOH", "C3H7NO2", "alanin"),
    ("NH2CONH2", "CH4N2O", "üre"), ("CH3COOCH2CH3", "C4H8O2", "etil asetat"), ("HCHO", "CH2O", "formaldehit"),
    ("CH3-CH=CH2", "C3H6", "propen"), ("H2SO4", "H2O4S", "sülfürik asit"), ("HNO3", "HNO3", "nitrik asit"),
    ("NaCl", "ClNa", "sodyum klorür"), ("CCO", "C2H6O", "etanol"), ("c1ccccc1", "C6H6", "benzen"),
    ("OC(=O)C", "C2H4O2", "asetik asit"), ("[Na+].[Cl-]", "ClNa", "sodyum klorür"), ("CO", "CO", "karbon monoksit"),
    ("glikoz", "C6H12O6", "glikoz"), ("Ethanol", "C2H6O", "etanol"), ("asetik asit", "C2H4O2", "asetik asit"),
    ("C₂H₅OH", "C2H6O", "etanol"), ("C10H8", "C10H8", "naftalin"), ("PCl5", "Cl5P", "fosfor pentaklorür"),
]
for (input, hill, name) in table {
    let r = read(input)
    eq(hill, r.mol.hill(), input); eq(name, r.known?.name ?? "nil", input); check(!r.example, input); eq(-1, r.mol.valenceError(), input)
}
check(read("CH3COOH").kind == .CONDENSED, "kind cond"); check(read("CCO").kind == .SMILES, "kind smiles")
check(read("C2H6O").kind == .FORMULA, "kind formula"); check(read("su").kind == .NAME, "kind name")
let tb = read("(CH3)3COH")
eq("C4H10O", tb.mol.hill()); check(tb.known == nil, "tb known"); check(!tb.example, "tb ex")
eq(tb.mol.signature(), read("CC(C)(C)O").mol.signature(), "tb sig")

eq([2, 2], orders(read("CO2").mol)); eq([3], orders(read("N2").mol)); eq([2], orders(read("CH2=CH2").mol))
eq([3], orders(read("HC≡CH").mol)); eq([1, 1, 2], orders(read("CH3COOH").mol)); eq([1, 1, 1, 2, 2, 2], orders(read("C6H6").mol))
eq([1, 3], orders(read("CH3CN").mol))
let naph = read("C10H8").mol
eq(2, naph.rings().count); eq(5, naph.bonds.filter { $0.order == 2 }.count)
let gl = read("C6H12O6").mol
eq(["C": 6, "H": 12, "O": 6], Dictionary(gl.counts(), uniquingKeysWith: +)); eq([6], gl.rings().map(\.count))
let py = read("c1ccncc1").mol
eq(3, py.bonds.filter { $0.order == 2 }.count); eq(0, py.atoms.first { $0.el == "N" }!.h)
let hno3 = read("HNO3").mol
eq(Set([1, -1, 0]), Set(hno3.atoms.map(\.charge))); eq(0, hno3.charge)
eq(2, read("NaCl").mol.components().count)
near(46.07, read("etanol").mol.mass(), 0.01); near(18.02, read("H2O").mol.mass(), 0.01)

eq(ChemError.Code.EMPTY, err("  ").code); eq(.UNKNOWN_ELEMENT, err("XyZ").code); eq(.HYDROGEN_COUNT, err("CH5").code)
eq(.HYDROGEN_COUNT, err("C2H7").code); eq(.IONIC, err("Fe2O3").code); eq(.TOO_LARGE, err("C50H102").code)
eq(.PARENTHESIS, err("C(").code); eq(.RING, err("C1CC").code); eq("C", err("CH3CH2").arg); eq(.VALENCE, err("CH3CH2").code)
eq(.VALENCE, err("C(C)(C)(C)(C)C").code); eq(.SYNTAX, err("CH3?").code)
eq("VALENCE:C", ChemError(code: .VALENCE, arg: "C").description)

let cases: [(String, Int)] = [("C7H16", 0), ("C8H18", 0), ("C5H10O2", 1), ("C4H6", 2), ("C3H9N", 0), ("C3H7Cl", 0), ("C9H12", 4),
                              ("C4H8O", 1), ("C5H12O", 0), ("C2H2Cl2", 1), ("SiCl4", 0), ("SF6", 0)]
for (f, dbe) in cases {
    let r = read(f)
    check(r.example || r.known != nil, "\(f) örnek")
    let m = r.mol
    eq(-1, m.valenceError(), f)
    eq(Dictionary(Chem.formulaCounts(f)!, uniquingKeysWith: +), Dictionary(m.counts().filter { $0.1 > 0 }, uniquingKeysWith: +), f)
    eq(1, m.components().count, "\(f) tek parça")
    eq(dbe, m.bonds.reduce(0) { $0 + $1.order - 1 } + m.rings().count, "\(f) doymamışlık")
}
check(read("C7H16").example, "C7H16 ex"); check(!read("C9H12").mol.rings().isEmpty, "C9H12 ring"); check(!read("C2H6O").example, "C2H6O")

let allK = KnownMolecules.all
check(allK.count >= 60, "tablo"); eq(allK.count, Set(allK.map(\.name)).count)
for k in allK {
    let m = k.molecule()
    eq(-1, m.valenceError(), k.name); eq(0, m.charge, k.name)
    check(KnownMolecules.bySignature(m) === k, "sig \(k.name)"); check(KnownMolecules.byName(k.name) === k, "name \(k.name)")
    check(KnownMolecules.byName(k.en) === k, "en \(k.en)")
}
eq("izobütan", KnownMolecules.byName("2-metilpropan")?.name ?? ""); eq("etanol", KnownMolecules.byHill("C2H6O")?.name ?? "")

var inputs: [(String, Molecule)] = allK.map { ($0.name, $0.molecule()) }
inputs += ["(CH3)3COH", "C7H16", "C9H12", "CH3CH(NH2)COOH", "C5H10O2"].map { ($0, read($0).mol) }
func bondRange(_ m: Molecule, _ p: [Layout2D.Pt]) -> (Double, Double) {
    if m.bonds.isEmpty { return (1, 1) }
    let l = m.bonds.map { hypot(p[$0.a].x - p[$0.b].x, p[$0.a].y - p[$0.b].y) }
    return (l.min()!, l.max()!)
}
for (t, base) in inputs {
    for cross in [false, true] {
        let m = cross ? base.explicitH() : base
        let p = Layout2D.layout(m, crossH: cross)
        eq(m.atoms.count, p.count)
        let (lo, hi) = bondRange(m, p)
        check(lo > 0.85 && hi < 1.15, "\(t) (H=\(cross)) bağ boyu [\(lo), \(hi)]")
        if m.atoms.count > 1 { check(Layout2D.minNonBonded(m, p) > 0.55, "\(t) (H=\(cross)) çakışma \(Layout2D.minNonBonded(m, p))") }
    }
}
do {
    let bz = read("C6H6").mol, p = Layout2D.layout(bz), ring = bz.rings()[0]
    let cx = ring.map { p[$0].x }.reduce(0, +) / 6, cy = ring.map { p[$0].y }.reduce(0, +) / 6
    for i in ring { near(1, hypot(p[i].x - cx, p[i].y - cy), 1e-6, "benzen") }
    let q = Layout2D.layout(read("CCCC").mol)
    func angle(_ a: Int, _ b: Int, _ c: Int) -> Double {
        let ux = q[a].x - q[b].x, uy = q[a].y - q[b].y, vx = q[c].x - q[b].x, vy = q[c].y - q[b].y
        return acos((ux * vx + uy * vy) / (hypot(ux, uy) * hypot(vx, vy))) * 180 / .pi
    }
    near(120, angle(0, 1, 2), 1e-6); near(120, angle(1, 2, 3), 1e-6)
    let r = Layout2D.layout(read("CC").mol.explicitH(), crossH: true); near(r[0].y, r[1].y, 1e-9, "etan düz")
    let c = Layout2D.layout(read("CO2").mol); near(0, abs(c[0].y - c[2].y), 1e-9); near(2, hypot(c[0].x - c[2].x, c[0].y - c[2].y), 1e-9)
}
func angle3(_ p: [Embed3D.V], _ a: Int, _ b: Int, _ c: Int) -> Double {
    let u = (p[a] - p[b]).norm(), v = (p[c] - p[b]).norm()
    return acos(min(max(u.dot(v), -1), 1)) * 180 / .pi
}
do {
    let ch4 = read("CH4").mol.explicitH(); let p = Embed3D.embed(ch4)
    for i in 1...4 { for j in (i + 1)..<5 { near(109.47, angle3(p, i, 0, j), 0.1, "ch4") } }
    for i in 1...4 { near(0.76 + 0.31, (p[i] - p[0]).len(), 1e-6) }
    check(!Embed3D.planar(p), "ch4 planar")
    let w = Embed3D.embed(read("H2O").mol.explicitH())
    near(104.47, angle3(w, 1, 0, 2), 0.1, "h2o"); check(Embed3D.planar(w), "h2o planar"); check(w[0].y > w[1].y, "O üstte")
    let n = Embed3D.embed(read("NH3").mol.explicitH())
    for i in 1...3 { for j in (i + 1)..<4 { near(106.97, angle3(n, i, 0, j), 0.2, "nh3") } }
    let co2 = Embed3D.embed(read("CO2").mol.explicitH())
    near(180, angle3(co2, 0, 1, 2), 1e-6); near((0.76 + 0.66) * 0.87, (co2[0] - co2[1]).len(), 1e-6)
    check(Embed3D.planar(Embed3D.embed(read("C=C").mol.explicitH())), "eten düz")
    check(Embed3D.planar(Embed3D.embed(read("C6H6").mol.explicitH())), "benzen düz")
    let ethane = read("CC").mol.explicitH(); let e = Embed3D.embed(ethane)
    for h in ethane.neighbors(0) where h != 1 { near(109.47, angle3(e, h, 0, 1), 0.1, "etan") }
    for k in allK {
        let q = Embed3D.embed(k.molecule().explicitH())
        for i in q.indices { for j in (i + 1)..<max(q.count, i + 1) { check((q[i] - q[j]).len() > 0.6, "\(k.name) \(i)-\(j) yakın") } }
    }
}
do {
    let m = Molecules.any2d
    let raw = ok(m.apply(nil, "CH3COOH"))
    eq("CH3COOH", raw["formula"] ?? "")
    eq("CH₃COOH", m.paths(raw).first { $0.tag == "formula" }?.label ?? "")
    check(m.paths(raw).first { $0.tag == "formula:1" }?.label?.contains("asetik asit") == true, "asetik etiket")
    let ex = ok(m.apply(nil, "C7H16"))
    check(m.paths(ex).first { $0.tag == "formula:1" }?.label?.hasPrefix("örnek yapı") == true, "örnek yapı")
    let bad = fail(m.apply(raw, "CH5"), .CHEMISTRY); eq("HYDROGEN_COUNT", bad.detail)
    func texts(_ view: String) -> [VPath] { var r = raw; r["view"] = view; return m.paths(r).filter { $0.tag?.hasPrefix("atom:") == true } }
    let st = texts(MolView.STRUCTURAL).compactMap(\.label)
    eq(4, st.filter { $0 == "H" }.count); eq(2, st.filter { $0 == "C" }.count)
    let sk = texts(MolView.SKELETAL).compactMap(\.label)
    check(!sk.contains("C"), "iskelet C"); check(sk.contains("OH") && sk.contains("O"), "iskelet \(sk)")
    let cd = texts(MolView.CONDENSED).compactMap(\.label)
    check(cd.contains { $0 == "CH₃" || $0 == "H₃C" }, "yoğun \(cd)")
    eq(8, texts(MolView.BALL).filter { $0.label == nil }.count, "küreler")
    let named = ok(m.apply(nil, "glikoz"))
    eq("C₆H₁₂O₆", m.paths(named).first { $0.tag == "formula" }?.label ?? "")
    eq("H2O", Molecules.water.editText(nil))
    eq("C₆H₁₂O₆", Chem.pretty("C6H12O6")); eq("HC≡CH", Chem.pretty("HC#CH"))
    for id in ["propane", "ethylene", "ethanol", "acetic_acid", "phenol", "toluene", "glycine", "alanine", "glucose"].map({ "sci.chem.org.\($0)" }) {
        let model = ParamShapes.model(id) as! MoleculeModel
        check(model.result(model.editText(nil))?.known != nil, "\(id) bilinen")
        let other = ok(model.apply(nil, "CH4"))
        check(model.paths(nil).count != model.paths(other).count, "\(id) çizim değişir")
    }
}

// ---------------- parametreli şekiller
let models: [ParamModel] = [ProjectileModel.shared, WaveModel.shared, SpringModel.shared, PendulumModel.shared, LensModel.convex, LensModel.concave,
                            InclineModel.shared, OhmModel.shared, TriangleModel.shared, CircleModel.shared, PolygonModel.shared, ParabolaModel.shared,
                            PopulationModel.shared] + Molecules.all
for m in models {
    let paths = m.paths(nil)
    check(geometry(paths).count > 8, "\(m.id) boş")
    let labels = formulaPaths(paths)
    guard let main = labels.first(where: { $0.tag == "formula" }) else { check(false, "\(m.id) formül yok"); continue }
    eq(m.label(m.values(nil))[0], main.label ?? "", m.id)
    check(main.points.map(\.y).min()! < 0.12, "\(m.id) üstte değil")
    check(main.points.map(\.x).min()! < 0.1 || m.labelRight, "\(m.id) köşede değil")
    for p in paths.flatMap(\.points) { check(p.x > -0.05 && p.x < 1.05 && p.y > -0.05 && p.y < 1.05, "\(m.id) kutu dışı \(p)") }
    check(ParamShapes.model(m.id) === m, "\(m.id) kayıt")
}
do {
    let P = ProjectileModel.shared
    let raw = ok(P.apply(nil, "v = 30; θ = 40"))
    check(formulaPaths(P.paths(raw)).contains { ($0.label ?? "").contains("v = 30 m/s") && ($0.label ?? "").contains("θ = 40°") }, "etiket")
    let bad = ["v": "abc", "theta": "NaN", "g": "1e999"]
    eq(P.paths(nil).count, P.paths(bad).count); near(20, P.values(bad)["v"], 1e-9); near(85, P.values(["theta": "120"])["theta"], 1e-9)
    eq("20", ParamModel.store(20)); eq("0.333333", ParamModel.store(1.0 / 3)); eq("-2.5", ParamModel.store(-2.5))
    eq("2,5", ParamModel.num(2.5)); eq("−1,33", ParamModel.num(-4.0 / 3)); eq("∞", ParamModel.num(.infinity))
    let r0 = ["v": "25"]
    check(fail(P.apply(r0, "v = (3"), .SYNTAX).math != nil, "math")
    eq("q", fail(P.apply(r0, "q = 3"), .UNKNOWN_SYMBOL).detail); eq("θ", fail(P.apply(r0, "θ = 95"), .OUT_OF_RANGE).detail)
    fail(P.apply(r0, "y = x^3"), .NO_MATCH); fail(P.apply(r0, "v < 3"), .NO_MATCH); fail(P.apply(r0, "v = 1/0"), .NOT_NUMBER)
    fail(P.apply(r0, ""), .SYNTAX); fail(P.apply(r0, "v = 30; θ = 200"), .OUT_OF_RANGE)
    let r1 = ok(P.apply(nil, "v = 30; θ = 45°"))
    near(30, num(r1, "v"), 1e-9); near(45, num(r1, "theta"), 1e-9); near(900 / 9.8, P.derivedValues(r1)["R"]!, 1e-6)
    let r2 = ok(P.apply(r1, "R = 50")); near((50 * 9.8).squareRoot(), num(r2, "v"), 1e-3); near(45, num(r2, "theta"), 1e-9)
    let r3 = ok(P.apply(r1, "y = x - 0,02x^2")); near(45, num(r3, "theta"), 1e-4); near((9.8 / (2 * 0.02 * 0.5)).squareRoot(), num(r3, "v"), 1e-3)
    let again = ok(P.apply(r3, P.editText(r3))); near(num(r3, "theta"), num(again, "theta"), 0.01)
    _ = ok(P.apply(r1, "h = v^2·sin(θ)^2/(2g)"))
    check(geometry(P.paths(r1)) != geometry(P.paths(ok(P.apply(r1, "θ = 30")))), "açı çizimi")
    let h = P.unitHandles(r1)[0]; eq("v0", h.key)
    let d = P.dragUnit(r1, "v0", h.x + 0.03, h.y - 0.05)!
    let h2 = P.unitHandles(d)[0]; near(Double(h.x + 0.03), Double(h2.x), 2e-3); near(Double(h.y - 0.05), Double(h2.y), 2e-3)
    check(P.dragUnit(r1, "yok", 0.5, 0.5) == nil, "yok")
}
do {
    let W = WaveModel.shared
    let w = ok(W.apply(nil, "y = 2sin(3x)")); near(2, num(w, "A"), 1e-6); near(3, num(w, "k"), 1e-6)
    near(2 * .pi / 3, W.derivedValues(w)["λ"]!, 1e-6)
    let w2 = ok(W.apply(w, "λ = 4")); near(.pi / 2, num(w2, "k"), 1e-5)
    fail(W.apply(w, "y = 2cos(3x)"), .NO_MATCH)
    let crest = W.unitHandles(w)[0]; let wd = W.dragUnit(w, "crest", crest.x + 0.05, crest.y - 0.02)!
    check(num(wd, "k") < 3 && num(wd, "A") > 2, "dalga sürükle")
    let S = SpringModel.shared
    let s = ok(S.apply(nil, "F = 20")); near(0.4, num(s, "x"), 1e-6)
    eq("F", fail(S.apply(nil, "F = 30"), .OUT_OF_RANGE).detail); near(80, num(ok(S.apply(nil, "F = 80x")), "k"), 1e-6)
    fail(S.apply(nil, "F = 80x + 1"), .NO_MATCH)
    let mass = S.unitHandles(s)[0]; let sd = S.dragUnit(s, "mass", mass.x - 0.1, mass.y)!; check(num(sd, "x") < 0.4, "yay")
    near(9.8 / (.pi * .pi), num(ok(PendulumModel.shared.apply(nil, "T = 2")), "L"), 1e-4)
    near(5, num(ok(OhmModel.shared.apply(nil, "V = 5I")), "R"), 1e-9)
    let L = LensModel.convex
    let raw = ok(L.apply(nil, "f = 10; di = 15")); near(30, num(raw, "do"), 1e-4)
    near(15, L.derivedValues(raw)["di"]!, 1e-4); near(-0.5, L.derivedValues(raw)["m"]!, 1e-4)
    _ = ok(L.apply(raw, "1/f = 1/do + 1/di")); fail(L.apply(raw, "1/f = 1/do - 1/di"), .NO_MATCH)
    let virt = ok(L.apply(nil, "f = 10; do = 5")); near(-10, L.derivedValues(virt)["di"]!, 1e-6)
    check(LensModel.concave.derivedValues(nil)["di"]! < 0, "ıraksak")
    let obj = L.unitHandles(raw).first { $0.key == "object" }!; check(num(L.dragUnit(raw, "object", obj.x - 0.05, obj.y)!, "do") > 30, "mercek")
    let I = InclineModel.shared
    let ir = ok(I.apply(nil, "θ = 45")); near(9.8 * sin(.pi / 4), I.derivedValues(ir)["a"]!, 1e-6)
    near(30, num(ok(I.apply(nil, "a = 4,9")), "theta"), 1e-3)
    let top = I.unitHandles(ir)[0]; check(num(I.dragUnit(ir, top.key, top.x, top.y + 0.1)!, "theta") < 45, "eğik")
}
do {
    let T = TriangleModel.shared
    let raw = ok(T.apply(nil, "A = 90°; B = 45°; c = 4")); let d = T.derivedValues(raw)
    near(45, d["C"]!, 1e-9); near(4 * 2.0.squareRoot(), d["a"]!, 1e-9); near(4, d["b"]!, 1e-9); near(8, d["S"]!, 1e-9)
    eq("C", fail(T.apply(nil, "A = 120; B = 70"), .OUT_OF_RANGE).detail)
    near(60, num(ok(T.apply(raw, "C = 30")), "B"), 1e-4)
    check(T.setValue(raw, "B", 100) == nil, "geçersiz"); check(T.setValue(raw, "B", 60) != nil, "geçerli")
    let apex = T.unitHandles(raw)[0]; let dr = T.dragUnit(raw, "C", apex.x + 0.08, apex.y + 0.02)!
    let v = T.values(dr); near(180, v["A"] + v["B"] + T.derivedValues(dr)["C"]!, 1e-9); check(v["A"] < 90, "A<90")
    let C = CircleModel.shared
    near(4, num(ok(C.apply(nil, "x² + y² = 16")), "r"), 1e-6); near(1.5, num(ok(C.apply(nil, "x^2+y^2=2,25")), "r"), 1e-6)
    fail(C.apply(nil, "(x−1)² + y² = 4"), .NO_MATCH); fail(C.apply(nil, "x² + y² = −4"), .NO_MATCH); fail(C.apply(nil, "x² + 2y² = 4"), .NO_MATCH)
    near(5, num(ok(C.apply(nil, "Ç = 10π")), "r"), 1e-5); near(2, num(ok(C.apply(nil, "A = 4π")), "r"), 1e-5)
    near(7, num(ok(C.apply(nil, "r = 7")), "r"), 1e-9); eq("x² + y² = 9", C.editText(nil))
    let rim = C.unitHandles(nil)[0]
    let out = C.dragUnit(nil, "r", 0.5 + (rim.x - 0.5) * 1.5, 0.6 + (rim.y - 0.6) * 1.5)!; near(4.5, num(out, "r"), 0.06)
    let Pg = PolygonModel.shared
    near(6, num(ok(Pg.apply(nil, "α = 120")), "n"), 1e-9); near(5, num(ok(Pg.apply(nil, "α = 108°")), "n"), 1e-9)
    eq("α", fail(Pg.apply(nil, "α = 125"), .OUT_OF_RANGE).detail)
    let p5 = ok(Pg.apply(nil, "n = 5; a = 2"))
    check(formulaPaths(Pg.paths(p5)).contains { ($0.label ?? "").contains("α = 108°") && ($0.label ?? "").contains("Ç = 10") }, "çokgen etiket")
    eq(5, Pg.paths(p5).first { $0.tag == "curve" }!.points.count)
    eq("7", Pg.withValue(nil, "n", 6.6)["n"] ?? "")
    let Pa = ParabolaModel.shared
    let pr = ok(Pa.apply(nil, "y = 2x^2 - 4x + 1")); near(2, num(pr, "a"), 1e-9); near(-4, num(pr, "b"), 1e-9); near(1, num(pr, "c"), 1e-9)
    eq("y = 2x² − 4x + 1", Pa.editText(pr)); near(8, Pa.derivedValues(pr)["Δ"]!, 1e-9)
    fail(Pa.apply(nil, "y = sin(x)"), .NO_MATCH); fail(Pa.apply(nil, "y = 3x + 1"), .NO_MATCH)
    near(-1, num(ok(Pa.apply(nil, "y = -(x-1)^2 + 2")), "a"), 1e-9)
    let vtx = Pa.unitHandles(pr).first { $0.key == "vertex" }!
    let moved = Pa.dragUnit(pr, "vertex", vtx.x + 0.104 / 1.2, vtx.y)!
    near(2, num(moved, "a"), 1e-9); near(2, -num(moved, "b") / (2 * num(moved, "a")), 1e-9)
    let Po = PopulationModel.shared
    let po = ok(Po.apply(nil, "N = 200/(1 + 9e^(−0,3t))")); near(200, num(po, "K"), 1e-4); near(20, num(po, "N0"), 1e-4); near(0.3, num(po, "r"), 1e-6)
    near(5, num(ok(Po.apply(po, "N₀ = 5")), "N0"), 1e-9); near(5, num(ok(Po.apply(po, "N_0 = 5")), "N0"), 1e-9)
    eq("N_0", fail(Po.apply(po, "N_0 = 300"), .OUT_OF_RANGE).detail); fail(Po.apply(po, "N = 2t"), .NO_MATCH)
    near(log(9.0) / 0.3, Po.derivedValues(po)["t_m"]!, 1e-6)
    near(30, num(ok(ProjectileModel.shared.apply(nil, "θ = asin(0,5)")), "theta"), 1e-9)
    near(2, num(ok(WaveModel.shared.apply(nil, "y = 3sin(2x)")), "k"), 1e-6)
}
// mekanik + element
do {
    let mech: [ParamModel] = [FreeBodyModel.shared, FixedPulleyModel.shared, MovablePulleyModel.shared, LeverModel.shared, PeriodicCellModel.shared, BohrAtomModel.shared]
    for m in mech { check(geometry(m.paths(nil)).count > 8, "\(m.id) boş"); check(ParamShapes.model(m.id) === m, "\(m.id) kayıt") }
    let na = ok(BohrAtomModel.shared.apply(nil, "sodyum")); eq("11", na["z"] ?? "")
    eq("Na: 2, 8, 1", BohrAtomModel.shared.label(BohrAtomModel.shared.values(na))[0])
    eq("26", ok(PeriodicCellModel.shared.apply(nil, "Z = 26"))["z"] ?? ""); eq("8", ok(PeriodicCellModel.shared.apply(nil, "O"))["z"] ?? "")
    eq([2, 8, 13, 1], PeriodicTable.shells(24))
}

do {
    func labels(_ p: [VPath]) -> [String] { formulaPaths(p).compactMap(\.label) }
    func tagged(_ p: [VPath], _ t: String) -> [VPath] { p.filter { $0.tag == t } }
    func pts(_ p: [VPath]) -> [[Float]] { p.map { $0.points.flatMap { [$0.x, $0.y] } } }
    let mech: [ParamModel] = [FreeBodyModel.shared, FixedPulleyModel.shared, MovablePulleyModel.shared, LeverModel.shared, PeriodicCellModel.shared, BohrAtomModel.shared]
    for m in mech {
        check(ShapeLibrary.shared.find("lib." + m.id)?.param === m || ShapeLibrary.shared.find(m.id)?.param === m, "\(m.id) lib")
        let paths = m.paths(nil)
        check(!labels(paths).isEmpty, "\(m.id) formül")
        for p in paths.flatMap(\.points) { check(p.x > -0.05 && p.x < 1.05 && p.y > -0.05 && p.y < 1.05, "\(m.id) kutu dışı \(p)") }
        var bad: [String: String] = [:]; for d in m.defs { bad[d.key] = "abc" }
        eq(m.paths(nil).count, m.paths(bad).count, "\(m.id) bozuk")
    }
    near(1.3, Double(FreeBodyModel.shared.w / FreeBodyModel.shared.h), 1e-4); near(1 / 1.15, Double(FixedPulleyModel.shared.w / FixedPulleyModel.shared.h), 1e-4)
    near(1.8, Double(LeverModel.shared.w / LeverModel.shared.h), 1e-4); near(0.9, Double(PeriodicCellModel.shared.w / PeriodicCellModel.shared.h), 1e-4)
    eq(118, PeriodicTable.all.count); eq("Og", PeriodicTable[118]!.symbol); eq(118, Set(PeriodicTable.all.map(\.symbol)).count)
    for e in PeriodicTable.all { eq(e.z, e.shells.reduce(0, +), e.symbol) }
    eq([2, 8, 1], PeriodicTable.find("Na")!.shells); eq([2, 8, 8, 2], PeriodicTable.find("kalsiyum")!.shells); eq([2, 8, 14, 2], PeriodicTable.find("Fe")!.shells)
    eq([2, 8, 13, 1], PeriodicTable.find("Cr")!.shells); eq([2, 8, 18, 1], PeriodicTable.find("Cu")!.shells)
    eq([2, 8, 18, 32, 18, 1], PeriodicTable.find("Au")!.shells); eq([2, 8, 18, 32, 32, 18, 8], PeriodicTable.find("Og")!.shells)
    check(PeriodicTable.find("Ar")!.kind == .NOBLE, "Ar"); check(PeriodicTable.find("Cl")!.kind == .HALOGEN, "Cl")
    eq(12, PeriodicTable.find("Na")!.neutrons); check(PeriodicTable.find("Xx") == nil, "Xx"); eq(26, PeriodicTable.find("26")!.z)
    eq("Zn", PeriodicTable.find("Zn")!.symbol); eq("İyot", PeriodicTable.find("iyot")!.name)
    let PC = PeriodicCellModel.shared, BA = BohrAtomModel.shared
    eq("C", PC.editText(nil)); eq(11, tagged(BA.paths(nil), "electron").count); eq("30", ok(PC.apply(nil, "Zn"))["z"] ?? "")
    for input in ["Fe", "demir", "26", "Z = 26"] {
        let raw = ok(PC.apply(nil, input)); eq("26", raw["z"] ?? "", input)
        let texts = PC.paths(raw).compactMap(\.label)
        check(texts.contains("Fe") && texts.contains("Demir") && texts.contains("26") && texts.contains("55,845"), "\(texts)")
        check(labels(PC.paths(raw)).contains("Geçiş metali"), "geçiş \(labels(PC.paths(raw)))")
    }
    check(PC.paths(ok(PC.apply(nil, "Tc"))).compactMap(\.label).contains("(98)"), "Tc")
    let k = ok(BA.apply(nil, "potasyum")); let kp = BA.paths(k)
    eq(19, tagged(kp, "electron").count); eq(4, tagged(kp, "shell").count); eq("K: 2, 8, 8, 1", labels(kp).first ?? "")
    check(kp.compactMap(\.label).contains("19p") && kp.compactMap(\.label).contains("20n"), "p n")
    if case .ok = BA.apply(k, "Qq") { check(false, "Qq") }; if case .ok = BA.apply(k, "Z = 200") { check(false, "Z200") }
    let FB = FreeBodyModel.shared
    let f = FreeBodyModel.forces(FB.values(nil))
    near(19.6, f.g, 1e-9); near(19.6, f.n, 1e-9); near(5.88, f.fs, 1e-9); near((15 - 5.88) / 2, f.a, 1e-9)
    let fr = ok(FB.apply(nil, "F = 20; α = 30°")); near(9.6, FreeBodyModel.forces(FB.values(fr)).n, 1e-6)
    let st = FreeBodyModel.forces(FB.values(ok(FB.apply(nil, "F = 3")))); near(0, st.a, 1e-12); near(3, st.fs, 1e-9)
    near(9.88, num(ok(FB.apply(nil, "a = 2")), "F"), 1e-3)
    check(labels(FB.paths(nil)) != labels(FB.paths(fr)), "fb label"); check(pts(FB.paths(nil)) != pts(FB.paths(fr)), "fb çizim")
    let hs = FB.unitHandles(nil); eq(1, hs.count)
    check(num(FB.dragUnit(nil, hs[0].key, hs[0].x + 0.05, hs[0].y - 0.08)!, "alpha") > 5, "fb drag")
    let FP = FixedPulleyModel.shared
    let d = FP.derivedValues(ok(FP.apply(nil, "P = 250; h = 2"))); near(250, d["F"]!, 1e-9); near(2, d["s"]!, 1e-9)
    check(labels(FP.paths(["P": "250", "h": "2"])).contains("F = 250 N; ip = 2 m"), "\(labels(FP.paths(["P": "250", "h": "2"])))")
    let MP = MovablePulleyModel.shared
    let mr = ok(MP.apply(nil, "P = 300; Pm = 20; h = 1,5")); let md = MP.derivedValues(mr)
    near(160, md["F"]!, 1e-9); near(3, md["s"]!, 1e-9); eq("F = (P + Pm)/2", labels(MP.paths(mr)).first ?? "")
    near(150, num(ok(MP.apply(nil, "F = 75")), "P"), 1e-6); check(pts(MP.paths(nil)) != pts(MP.paths(mr)), "mp çizim")
    let LV = LeverModel.shared
    near(24, LV.derivedValues(nil)["F"]!, 1e-9)
    let lr = ok(LV.apply(nil, "P = 200; a = 0,5; b = 2")); near(50, LV.derivedValues(lr)["F"]!, 1e-9)
    let lt = LV.paths(lr).compactMap(\.label); check(lt.contains("a = 0,5 m") && lt.contains("b = 2 m") && lt.contains("F = 50 N"), "\(lt)")
    near(4, num(ok(LV.apply(lr, "F = 25")), "b"), 1e-6)
    let pv = LV.unitHandles(lr).first { $0.key == "pivot" }!; let mv = LV.dragUnit(lr, "pivot", pv.x + 0.1, pv.y)!
    near(2.5, num(mv, "a") + num(mv, "b"), 1e-6); check(num(mv, "a") > 0.5, "pivot"); check(pts(LV.paths(nil)) != pts(LV.paths(lr)), "lv çizim")
}
print("checks=\(checks) failures=\(failures)")
