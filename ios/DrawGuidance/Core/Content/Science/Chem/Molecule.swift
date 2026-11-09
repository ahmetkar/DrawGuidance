import Foundation

// v45 (8) küçük kimya motoru (Android `chem/Molecule.kt`): element verisi, molekül çizgesi, değerlik denetimi,
// kapalı formül ve imza.

/// Element bilgisi: olağan değerlikler, değerlik elektronu, kovalent yarıçap (Å).
struct ChemElement {
    let symbol: String
    let valences: [Int]
    let ve: Int
    let mass: Double
    let radius: Double
    let color: String
    var metal = false
}

enum Elements {
    private static let list: [ChemElement] = [
        ChemElement(symbol: "H", valences: [1], ve: 1, mass: 1.008, radius: 0.31, color: "#F4F6F8"),
        ChemElement(symbol: "B", valences: [3], ve: 3, mass: 10.81, radius: 0.84, color: "#FFAB91"),
        ChemElement(symbol: "C", valences: [4], ve: 4, mass: 12.011, radius: 0.76, color: "#455A64"),
        ChemElement(symbol: "N", valences: [3], ve: 5, mass: 14.007, radius: 0.71, color: "#1E88E5"),
        ChemElement(symbol: "O", valences: [2], ve: 6, mass: 15.999, radius: 0.66, color: "#E53935"),
        ChemElement(symbol: "F", valences: [1], ve: 7, mass: 18.998, radius: 0.57, color: "#7CB342"),
        ChemElement(symbol: "Si", valences: [4], ve: 4, mass: 28.085, radius: 1.11, color: "#BCAAA4"),
        ChemElement(symbol: "P", valences: [3, 5], ve: 5, mass: 30.974, radius: 1.07, color: "#FB8C00"),
        ChemElement(symbol: "S", valences: [2, 4, 6], ve: 6, mass: 32.06, radius: 1.05, color: "#FDD835"),
        ChemElement(symbol: "Cl", valences: [1], ve: 7, mass: 35.45, radius: 1.02, color: "#43A047"),
        ChemElement(symbol: "Br", valences: [1], ve: 7, mass: 79.904, radius: 1.20, color: "#8D6E63"),
        ChemElement(symbol: "I", valences: [1], ve: 7, mass: 126.90, radius: 1.39, color: "#7E57C2"),
        ChemElement(symbol: "Se", valences: [2], ve: 6, mass: 78.97, radius: 1.20, color: "#FFA726"),
        ChemElement(symbol: "Li", valences: [1], ve: 1, mass: 6.94, radius: 1.28, color: "#B39DDB", metal: true),
        ChemElement(symbol: "Na", valences: [1], ve: 1, mass: 22.990, radius: 1.66, color: "#9575CD", metal: true),
        ChemElement(symbol: "K", valences: [1], ve: 1, mass: 39.098, radius: 2.03, color: "#7E57C2", metal: true),
        ChemElement(symbol: "Mg", valences: [2], ve: 2, mass: 24.305, radius: 1.41, color: "#81C784", metal: true),
        ChemElement(symbol: "Ca", valences: [2], ve: 2, mass: 40.078, radius: 1.76, color: "#66BB6A", metal: true),
        ChemElement(symbol: "Al", valences: [3], ve: 3, mass: 26.982, radius: 1.21, color: "#B0BEC5", metal: true),
        ChemElement(symbol: "Fe", valences: [2, 3], ve: 2, mass: 55.845, radius: 1.32, color: "#E57373", metal: true),
        ChemElement(symbol: "Cu", valences: [1, 2], ve: 1, mass: 63.546, radius: 1.32, color: "#FF8A65", metal: true),
        ChemElement(symbol: "Zn", valences: [2], ve: 2, mass: 65.38, radius: 1.22, color: "#90A4AE", metal: true),
        ChemElement(symbol: "Ag", valences: [1], ve: 1, mass: 107.87, radius: 1.45, color: "#CFD8DC", metal: true),
        ChemElement(symbol: "Ba", valences: [2], ve: 2, mass: 137.33, radius: 2.15, color: "#66BB6A", metal: true),
        ChemElement(symbol: "He", valences: [0], ve: 2, mass: 4.0026, radius: 0.28, color: "#80DEEA"),
        ChemElement(symbol: "Ne", valences: [0], ve: 8, mass: 20.180, radius: 0.58, color: "#80DEEA"),
        ChemElement(symbol: "Ar", valences: [0], ve: 8, mass: 39.948, radius: 1.06, color: "#80DEEA"),
    ]
    private static let bySymbol: [String: ChemElement] = Dictionary(list.map { ($0.symbol, $0) }, uniquingKeysWith: { a, _ in a })

    static subscript(_ symbol: String) -> ChemElement? { bySymbol[symbol] }
    static func known(_ symbol: String) -> Bool { bySymbol[symbol] != nil }
    static var symbols: Set<String> { Set(bySymbol.keys) }

    /// Yüklü atomun değerlikleri. Metal iyonları bağ yapmaz (0).
    static func valences(_ symbol: String, _ charge: Int) -> [Int] {
        guard let e = bySymbol[symbol] else { return [] }
        if e.metal { return charge == 0 ? e.valences : [0] }
        if charge == 0 { return e.valences }
        switch symbol {
        case "C", "Si": return [4 - abs(charge)]
        case "B": return [3 - charge]
        case "H": return [0]
        default:
            let v = e.valences.map { $0 + charge }.filter { $0 >= 0 }
            return v.isEmpty ? [0] : v
        }
    }
}

/// Atom: h bağlı (gösterilmeyen) hidrojen sayısı. Kotlin'deki gibi başvuru türü (yerinde değişir).
final class Atom {
    let el: String
    var h: Int
    var charge: Int
    init(_ el: String, h: Int = 0, charge: Int = 0) { self.el = el; self.h = h; self.charge = charge }
    func copy(h: Int? = nil) -> Atom { Atom(el, h: h ?? self.h, charge: charge) }
}

/// Bağ: order 1, 2, 3. Kimliğiyle karşılaştırılır.
final class Bond: Hashable {
    let a: Int
    let b: Int
    var order: Int
    init(_ a: Int, _ b: Int, _ order: Int = 1) { self.a = a; self.b = b; self.order = order }
    func copy(order: Int? = nil) -> Bond { Bond(a, b, order ?? self.order) }
    func other(_ i: Int) -> Int { a == i ? b : a }
    func has(_ i: Int) -> Bool { a == i || b == i }
    static func == (l: Bond, r: Bond) -> Bool { l === r }
    func hash(into h: inout Hasher) { h.combine(ObjectIdentifier(self)) }
}

final class Molecule {
    var atoms: [Atom]
    var bonds: [Bond]

    init(_ atoms: [Atom] = [], _ bonds: [Bond] = []) { self.atoms = atoms; self.bonds = bonds }

    @discardableResult func add(_ a: Atom) -> Int { atoms.append(a); return atoms.count - 1 }
    @discardableResult func bond(_ a: Int, _ b: Int, _ order: Int = 1) -> Bond { let x = Bond(a, b, order); bonds.append(x); return x }
    func bondBetween(_ a: Int, _ b: Int) -> Bond? { bonds.first { ($0.a == a && $0.b == b) || ($0.a == b && $0.b == a) } }
    func neighbors(_ i: Int) -> [Int] { bonds.filter { $0.has(i) }.map { $0.other(i) } }
    func bondsOf(_ i: Int) -> [Bond] { bonds.filter { $0.has(i) } }
    func bondSum(_ i: Int) -> Int { bonds.reduce(0) { $1.has(i) ? $0 + $1.order : $0 } }
    func degree(_ i: Int) -> Int { bonds.reduce(0) { $1.has(i) ? $0 + 1 : $0 } }

    func copy() -> Molecule { Molecule(atoms.map { $0.copy() }, bonds.map { $0.copy() }) }

    /// Element sayıları (bağlı H'ler dahil), ilk görülme sırasıyla.
    func counts() -> [(String, Int)] {
        var order: [String] = []
        var m: [String: Int] = [:]
        func inc(_ k: String, _ n: Int) { if m[k] == nil { order.append(k) }; m[k, default: 0] += n }
        for a in atoms {
            inc(a.el, 1)
            if a.h > 0 { inc("H", a.h) }
        }
        return order.map { ($0, m[$0]!) }
    }

    var charge: Int { atoms.reduce(0) { $0 + $1.charge } }

    func hill() -> String { Chem.hill(counts()) }

    func mass() -> Double { counts().reduce(0.0) { $0 + (Elements[$1.0]?.mass ?? 0) * Double($1.1) } }

    /// Atomun yalın elektron çifti sayısı (VSEPR).
    func lonePairs(_ i: Int) -> Int {
        let a = atoms[i]
        guard let e = Elements[a.el] else { return 0 }
        if e.metal { return 0 }
        return max((e.ve - a.charge - bondSum(i) - a.h) / 2, 0)
    }

    /// Değerlik denetimi: hatalı atomun indeksi ya da -1.
    func valenceError() -> Int {
        for i in atoms.indices {
            let a = atoms[i]
            let used = bondSum(i) + a.h
            let allowed = Elements.valences(a.el, a.charge)
            if allowed.isEmpty || !allowed.contains(used) { return i }
        }
        return -1
    }

    /// Bağlı bileşenler.
    func components() -> [[Int]] {
        var seen = [Bool](repeating: false, count: atoms.count)
        var out: [[Int]] = []
        for s in atoms.indices where !seen[s] {
            var comp: [Int] = []
            var stack = [s]
            seen[s] = true
            while let x = stack.popLast() {
                comp.append(x)
                for y in neighbors(x) where !seen[y] { seen[y] = true; stack.append(y) }
            }
            out.append(comp.sorted())
        }
        return out
    }

    /// Hidrojenleri ayrı atom yapan kopya. Yeni H'ler sonda.
    func explicitH() -> Molecule {
        let m = Molecule(atoms.map { $0.copy(h: 0) }, bonds.map { $0.copy() })
        for i in atoms.indices {
            for _ in 0..<atoms[i].h { let h = m.add(Atom("H")); m.bond(i, h, 1) }
        }
        return m
    }

    /// Eşyapı imzası (Weisfeiler–Lehman tarzı).
    func signature() -> String {
        let m = collapsedH()
        var lab = m.atoms.map { "\($0.el)\($0.h)\($0.charge != 0 ? "c\($0.charge)" : "")" }
        for _ in 0..<6 {
            lab = m.atoms.indices.map { i in
                let ns = m.bondsOf(i).map { b in "\(b.order)\(lab[b.other(i)])" }.sorted()
                return javaRadix36(javaHash(lab[i] + "(" + ns.joined(separator: ",") + ")"))
            }
        }
        return lab.sorted().joined(separator: ".")
    }

    /// Ayrı H atomlarını bağlı oldukları atomun H sayısına katar.
    func collapsedH() -> Molecule {
        var drop = [Bool](repeating: false, count: atoms.count)
        var out = atoms.map { $0.copy() }
        for i in atoms.indices {
            let a = atoms[i]
            if a.el != "H" || a.charge != 0 { continue }
            let bs = bondsOf(i)
            if bs.count != 1 || bs[0].order != 1 { continue }
            let o = bs[0].other(i)
            if atoms[o].el == "H" { continue }
            drop[i] = true
            out[o] = out[o].copy(h: out[o].h + 1 + a.h)
        }
        var map = [Int](repeating: -1, count: atoms.count)
        let res = Molecule()
        for i in atoms.indices where !drop[i] { map[i] = res.add(out[i]) }
        for b in bonds where !drop[b.a] && !drop[b.b] { res.bond(map[b.a], map[b.b], b.order) }
        return res
    }

    /// Halkalar (en küçük halkalar kümesi). Her halka atom sırasıyla.
    func rings() -> [[Int]] {
        let n = atoms.count
        let rank = bonds.count - n + components().count
        if rank <= 0 { return [] }
        var cand: [[Int]] = []
        for bd in bonds {
            let u = bd.a, v = bd.b
            var prev = [Int](repeating: -1, count: n)
            var q = [u]
            var head = 0
            prev[u] = u
            while head < q.count {
                let x = q[head]; head += 1
                if x == v { break }
                for y in neighbors(x) {
                    if prev[y] != -1 { continue }
                    if (x == u && y == v) || (x == v && y == u) { continue }
                    prev[y] = x; q.append(y)
                }
            }
            if prev[v] == -1 { continue }
            var path: [Int] = []
            var c = v
            while c != u { path.append(c); c = prev[c] }
            path.append(u)
            let ring = Array(path.reversed())
            let rs = Set(ring)
            if !cand.contains(where: { $0.count == ring.count && Set($0) == rs }) { cand.append(ring) }
        }
        // Kotlin sortBy kararlıdır
        cand = cand.enumerated().sorted { $0.element.count != $1.element.count ? $0.element.count < $1.element.count : $0.offset < $1.offset }.map(\.element)
        var out: [[Int]] = []
        var covered = Set<[Int]>()
        func edges(_ r: [Int]) -> [[Int]] { r.indices.map { let a = r[$0], b = r[($0 + 1) % r.count]; return a < b ? [a, b] : [b, a] } }
        for r in cand {
            if out.count >= rank { break }
            let e = edges(r)
            if out.isEmpty || e.contains(where: { !covered.contains($0) }) { out.append(r); covered.formUnion(e) }
        }
        return out
    }

    /// Doymamışlığı doldurur (önce tek seçenekli atomlar). locked bağları değişmez.
    @discardableResult
    func fillUnsaturation(_ locked: Set<Bond> = []) -> Bool {
        func rem(_ i: Int) -> Int {
            let a = atoms[i]
            let used = bondSum(i) + a.h
            guard let v = Elements.valences(a.el, a.charge).first(where: { $0 >= used }) else { return -1 }
            return v - used
        }
        var guardN = 0
        while guardN < 200 {
            guardN += 1
            let needy = atoms.indices.filter { rem($0) > 0 }
            if needy.isEmpty { return true }
            func options(_ i: Int) -> [Bond] { bondsOf(i).filter { !locked.contains($0) && $0.order < 3 && rem($0.other(i)) > 0 } }
            func score(_ i: Int) -> Int { let c = options(i).count; return c == 0 ? 99 : c }
            guard let pick = minBy(needy, score) else { return true }
            let opts = options(pick)
            if opts.isEmpty { return false }
            minBy(opts) { options($0.other(pick)).count }!.order += 1
        }
        return false
    }
}

/// Kotlin `minByOrNull`: eşitlikte ilk eleman.
func minBy<T>(_ list: [T], _ key: (T) -> Int) -> T? {
    var best: T?
    var bk = Int.max
    for x in list { let k = key(x); if best == nil || k < bk { best = x; bk = k } }
    return best
}

func minByD<T>(_ list: [T], _ key: (T) -> Double) -> T? {
    var best: T?
    var bk = Double.infinity
    for x in list { let k = key(x); if best == nil || k < bk { best = x; bk = k } }
    return best
}

/// Java `String.hashCode` (UTF-16).
func javaHash(_ s: String) -> Int32 {
    var h: Int32 = 0
    for u in s.utf16 { h = h &* 31 &+ Int32(u) }
    return h
}

/// Java `Integer.toString(v, 36)`.
func javaRadix36(_ v: Int32) -> String { String(Int64(v), radix: 36) }

/// Kimya okuma hatası.
struct ChemError: Equatable, CustomStringConvertible, Error {
    enum Code: String { case EMPTY, SYNTAX, UNKNOWN_ELEMENT, VALENCE, HYDROGEN_COUNT, TOO_LARGE, IONIC, PARENTHESIS, RING }
    let code: Code
    var arg: String = ""
    var pos: Int = -1
    var description: String { arg.isEmpty ? code.rawValue : "\(code.rawValue):\(arg)" }
}
