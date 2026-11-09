import Foundation

/*
 * v45 (8) formül okuma (Android `chem/Chem.kt`). Girdi sırasıyla: bilinen ad, kapalı formül (bilinen tablo ya da
 * örnek izomer), H içeren yarı açık formül, SMILES benzeri. Sonuçta yapı bilinen tabloyla imzadan eşlenir.
 */

/// Okuma sonucu.
struct ChemResult {
    enum Kind { case NAME, FORMULA, CONDENSED, SMILES }
    let mol: Molecule
    let input: String
    let known: KnownMolecule?
    let example: Bool
    let kind: Kind
}

/// Sıralı element sayıları (Kotlin `LinkedHashMap<String, Int>`).
typealias ElementCounts = [(String, Int)]

extension Character {
    var isAsciiDigit: Bool { isASCII && isNumber }
    var digitValue: Int { Int(String(self)) ?? 0 }
}

enum Chem {
    static let MAX_ATOMS = 40
    private static let SUB: [Character] = Array("₀₁₂₃₄₅₆₇₈₉")

    /// Hill sırası: C, H, sonra alfabetik (karbon yoksa hepsi alfabetik).
    static func hill(_ counts: ElementCounts) -> String {
        var m: [String: Int] = [:]
        var keys: [String] = []
        for (k, v) in counts { if m[k] == nil { keys.append(k) }; m[k, default: 0] += v }
        keys = keys.filter { (m[$0] ?? 0) > 0 }
        let order: [String] = keys.contains("C")
            ? ["C"] + (keys.contains("H") ? ["H"] : []) + keys.filter { $0 != "C" && $0 != "H" }.sorted()
            : keys.sorted()
        return order.map { el in let n = m[el]!; return n == 1 ? el : "\(el)\(n)" }.joined()
    }

    /// Rakamları alt simge yazar ("C2H5OH" → "C₂H₅OH").
    static func pretty(_ text: String) -> String {
        var out: [Character] = []
        for c in text {
            if c.isAsciiDigit, let prev = out.last, prev.isLetter || prev == ")" || SUB.contains(prev) { out.append(SUB[c.digitValue]) }
            else { out.append(c) }
        }
        return String(out).replacingOccurrences(of: "#", with: "≡")
    }

    private static func normalize(_ text: String) -> String {
        var out = ""
        for c in text.trimmingCharacters(in: .whitespacesAndNewlines) {
            if let sub = SUB.firstIndex(of: c) { out.append(String(sub)) }
            else if c == "−" || c == "–" || c == "—" { out.append("-") }
            else if c == "≡" { out.append("#") }
            else if c == " " || c == "\t" { }
            else if c == "⁺" { out.append("+") }
            else if c == "⁻" { out.append("-") }
            else { out.append(c) }
        }
        return out
    }

    /// Metni okur; okunamazsa ChemError fırlatır.
    static func read(_ text: String) throws -> ChemResult {
        let raw = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if raw.isEmpty { throw ChemError(code: .EMPTY) }
        if let k = KnownMolecules.byName(raw) { return ChemResult(mol: k.molecule(), input: raw, known: k, example: false, kind: .NAME) }
        let s = normalize(raw)
        if s.isEmpty { throw ChemError(code: .EMPTY) }
        if let formula = pureFormula(s) {
            if let k = KnownMolecules.byHill(hill(formula)) { return ChemResult(mol: k.molecule(), input: raw, known: k, example: false, kind: .FORMULA) }
            let mol = try Isomer.example(formula)
            return try finish(mol, raw, .FORMULA, true)
        }
        if hasPlainH(s) {
            let cond: Molecule
            do { cond = try Condensed.parse(s) } catch {
                if let sm = try? Smiles.parse(s) { return try finish(sm, raw, .SMILES, false) }
                throw error
            }
            return try finish(cond, raw, .CONDENSED, false)
        }
        return try finish(try Smiles.parse(s), raw, .SMILES, false)
    }

    static func tryRead(_ text: String) -> (ChemResult?, ChemError?) {
        do { return (try read(text), nil) } catch let e as ChemError { return (nil, e) } catch { return (nil, ChemError(code: .SYNTAX)) }
    }

    private static func finish(_ mol: Molecule, _ raw: String, _ kind: ChemResult.Kind, _ example: Bool) throws -> ChemResult {
        let heavy = mol.atoms.filter { $0.el != "H" }.count
        if heavy > MAX_ATOMS { throw ChemError(code: .TOO_LARGE, arg: String(MAX_ATOMS)) }
        let bad = mol.valenceError()
        if bad >= 0 { throw ChemError(code: .VALENCE, arg: mol.atoms[bad].el) }
        return ChemResult(mol: mol, input: raw, known: KnownMolecules.bySignature(mol), example: example, kind: kind)
    }

    private static func hasPlainH(_ str: String) -> Bool {
        let s = Array(str)
        var depth = 0
        for i in s.indices {
            switch s[i] {
            case "[": depth += 1
            case "]": depth -= 1
            case "H": if depth == 0 && (i + 1 >= s.count || !s[i + 1].isLowercase) { return true }
            default: break
            }
        }
        return false
    }

    private static func pureFormula(_ s: String) -> ElementCounts? {
        if s.contains(where: { !($0.isLetter || $0.isNumber || $0 == "(" || $0 == ")") }) { return nil }
        guard let tokens = elementTokens(s) else { return nil }
        let els = tokens.filter { $0.0 != "(" && $0.0 != ")" }.map(\.0)
        if els.count != Set(els).count { return nil }
        return formulaCounts(s)
    }

    private static func elementTokens(_ str: String) -> [(String, Int)]? {
        let s = Array(str)
        var out: [(String, Int)] = []
        var i = 0
        func digits() -> Int? {
            var n = 0, any = false
            while i < s.count && s[i].isAsciiDigit { n = n * 10 + s[i].digitValue; i += 1; any = true }
            return any ? n : nil
        }
        while i < s.count {
            let c = s[i]
            if c == "(" { out.append(("(", 0)); i += 1 }
            else if c == ")" { i += 1; out.append((")", digits() ?? 1)) }
            else if c.isUppercase {
                var el = String(c)
                if i + 1 < s.count && s[i + 1].isLowercase && Elements.known(el + String(s[i + 1])) { el += String(s[i + 1]); i += 1 }
                if !Elements.known(el) { return nil }
                i += 1
                out.append((el, digits() ?? 1))
            } else { return nil }
        }
        return out
    }

    /// Kapalı formülün element sayıları ("Ca(OH)2" → Ca 1, O 2, H 2); okunamazsa nil.
    static func formulaCounts(_ s: String) -> ElementCounts? {
        guard let tokens = elementTokens(s) else { return nil }
        var stack: [ElementCounts] = [[]]
        func addTo(_ m: inout ElementCounts, _ k: String, _ v: Int) {
            if let i = m.firstIndex(where: { $0.0 == k }) { m[i].1 += v } else { m.append((k, v)) }
        }
        for (t, n) in tokens {
            switch t {
            case "(": stack.append([])
            case ")":
                if stack.count < 2 { return nil }
                let top = stack.removeLast()
                for (k, v) in top { addTo(&stack[stack.count - 1], k, v * n) }
            default:
                if n <= 0 { return nil }
                addTo(&stack[stack.count - 1], t, n)
            }
        }
        if stack.count != 1 || stack[0].isEmpty { return nil }
        return stack[0]
    }
}

// MARK: - SMILES

enum Smiles {
    private static let ORGANIC: Set<String> = ["B", "C", "N", "O", "P", "S", "F", "Cl", "Br", "I"]
    private static let AROMATIC: Set<Character> = ["b", "c", "n", "o", "p", "s"]

    static func parse(_ str: String) throws -> Molecule {
        let s = Array(str)
        let m = Molecule()
        var aromatic = Set<Int>()
        var bracket = Set<Int>()
        var aroBonds: [Bond] = []
        var branch: [Int?] = []
        var rings: [Int: (Int, Int?)] = [:]
        var prev: Int? = nil
        var pending: Int? = nil
        var i = 0
        func err(_ code: ChemError.Code, _ arg: String = "") -> ChemError { ChemError(code: code, arg: arg, pos: i) }
        func connect(_ a: Int, _ b: Int, _ explicit: Int?) throws {
            if m.bondBetween(a, b) != nil || a == b { throw err(.SYNTAX) }
            let order = explicit.flatMap { $0 > 0 ? $0 : nil } ?? 1
            let bd = m.bond(a, b, order)
            if (explicit == nil || explicit == 0) && aromatic.contains(a) && aromatic.contains(b) { aroBonds.append(bd) }
        }
        func addAtom(_ atom: Atom, _ aro: Bool, _ br: Bool) throws {
            let idx = m.add(atom)
            if aro { aromatic.insert(idx) }
            if br { bracket.insert(idx) }
            if let p = prev { try connect(p, idx, pending) }
            pending = nil
            prev = idx
        }
        while i < s.count {
            let c = s[i]
            switch c {
            case "(": if prev == nil { throw err(.SYNTAX) }; branch.append(prev); i += 1
            case ")": if branch.isEmpty { throw err(.PARENTHESIS) }; prev = branch.removeLast(); i += 1
            case "-": pending = 1; i += 1
            case "=": pending = 2; i += 1
            case "#": pending = 3; i += 1
            case ":": pending = 0; i += 1
            case "/", "\\": i += 1
            case ".": prev = nil; pending = nil; i += 1
            default:
                if c.isAsciiDigit || c == "%" {
                    let num: Int
                    if c == "%" {
                        if i + 2 >= s.count || !s[i + 1].isAsciiDigit || !s[i + 2].isAsciiDigit { throw err(.SYNTAX) }
                        num = s[i + 1].digitValue * 10 + s[i + 2].digitValue; i += 3
                    } else { num = c.digitValue; i += 1 }
                    guard let p = prev else { throw err(.SYNTAX) }
                    if let open = rings.removeValue(forKey: num) {
                        try connect(open.0, p, pending ?? open.1)
                    } else { rings[num] = (p, pending) }
                    pending = nil
                } else if c == "[" {
                    guard let end = s[i...].firstIndex(of: "]") else { throw err(.PARENTHESIS) }
                    let body = Array(s[(i + 1)..<end])
                    var k = 0
                    while k < body.count && body[k].isAsciiDigit { k += 1 }
                    if k >= body.count { throw err(.SYNTAX) }
                    var el: String
                    var aro = false
                    if body[k].isLowercase && AROMATIC.contains(body[k]) { el = body[k].uppercased(); aro = true; k += 1 } else {
                        el = String(body[k]); k += 1
                        if k < body.count && body[k].isLowercase && Elements.known(el + String(body[k])) { el += String(body[k]); k += 1 }
                    }
                    if !Elements.known(el) { throw err(.UNKNOWN_ELEMENT, el) }
                    var h = 0
                    if k < body.count && body[k] == "H" && el != "H" {
                        k += 1; h = 1
                        if k < body.count && body[k].isAsciiDigit { h = body[k].digitValue; k += 1 }
                    }
                    var charge = 0
                    while k < body.count && (body[k] == "+" || body[k] == "-") {
                        let sign = body[k] == "+" ? 1 : -1
                        k += 1
                        if k < body.count && body[k].isAsciiDigit { charge += sign * body[k].digitValue; k += 1 } else { charge += sign }
                    }
                    if k != body.count { throw err(.SYNTAX) }
                    i = end + 1
                    try addAtom(Atom(el, h: h, charge: charge), aro, true)
                } else if c.isLetter {
                    var el: String
                    var aro = false
                    if c.isUppercase {
                        el = String(c)
                        if i + 1 < s.count && ((c == "C" && s[i + 1] == "l") || (c == "B" && s[i + 1] == "r")) { el += String(s[i + 1]); i += 1 }
                        if !ORGANIC.contains(el) { throw err(.UNKNOWN_ELEMENT, el) }
                    } else {
                        if !AROMATIC.contains(c) { throw err(.SYNTAX) }
                        el = c.uppercased(); aro = true
                    }
                    i += 1
                    try addAtom(Atom(el), aro, false)
                } else {
                    throw err(.SYNTAX)
                }
            }
        }
        if let k = rings.keys.min() { throw ChemError(code: .RING, arg: String(k), pos: s.count) }
        if !branch.isEmpty { throw ChemError(code: .PARENTHESIS, pos: s.count) }
        if m.atoms.isEmpty { throw ChemError(code: .EMPTY) }
        try kekulize(m, aromatic, bracket, aroBonds)
        for idx in m.atoms.indices where !bracket.contains(idx) {
            let a = m.atoms[idx]
            let used = m.bondSum(idx)
            guard let v = Elements.valences(a.el, a.charge).first(where: { $0 >= used }) else { throw ChemError(code: .VALENCE, arg: a.el) }
            a.h = v - used
        }
        return m
    }

    /// Aromatik bağlara dönüşümlü çift bağ yerleştirir (geri izlemeli eşleme).
    private static func kekulize(_ m: Molecule, _ aromatic: Set<Int>, _ bracket: Set<Int>, _ aroBonds: [Bond]) throws {
        if aroBonds.isEmpty { return }
        let need = aromatic.filter { i in
            let a = m.atoms[i]
            switch a.el {
            case "C", "B": return true
            case "N", "P": return bracket.contains(i) ? a.h == 0 && a.charge == 0 && m.degree(i) == 2 : m.degree(i) == 2
            default: return false
            }
        }.sorted()
        let needSet = Set(need)
        var taken = Set<Int>()
        func solve(_ k: Int) -> Bool {
            if k == need.count { return true }
            let a = need[k]
            if taken.contains(a) { return solve(k + 1) }
            for b in aroBonds where b.has(a) {
                let o = b.other(a)
                if taken.contains(o) || !needSet.contains(o) { continue }
                taken.insert(a); taken.insert(o); b.order = 2
                if solve(k + 1) { return true }
                taken.remove(a); taken.remove(o); b.order = 1
            }
            return false
        }
        if !solve(0) { throw ChemError(code: .VALENCE, arg: "aromatic") }
    }
}

// MARK: - Yarı açık formül

/// "CH3-CH2-OH", "CH3COOH", "CH3CH(CH3)CH3", "(CH3)3COH", "HC≡CH", "C2H5OH", "C6H5COOH".
enum Condensed {
    private indirect enum CUnit {
        case a(el: String, count: Int, h: Int, bond: Int?)
        case g(units: [CUnit], count: Int, bond: Int?)
        var bond: Int? { switch self { case .a(_, _, _, let b), .g(_, _, let b): return b } }
    }

    static func parse(_ str: String) throws -> Molecule {
        let s = Array(str)
        var i = 0
        func err(_ code: ChemError.Code, _ arg: String = "") -> ChemError { ChemError(code: code, arg: arg, pos: i) }
        func digits() -> Int? {
            var n = 0, any = false
            while i < s.count && s[i].isAsciiDigit { n = n * 10 + s[i].digitValue; i += 1; any = true }
            return any ? n : nil
        }
        func seq(_ depth: Int) throws -> [CUnit] {
            var out: [CUnit] = []
            while i < s.count {
                var bond: Int? = nil
                switch s[i] {
                case "-": bond = 1; i += 1
                case "=": bond = 2; i += 1
                case "#": bond = 3; i += 1
                default: break
                }
                if i >= s.count { throw err(.SYNTAX) }
                let c = s[i]
                if c == ")" {
                    if bond != nil || depth == 0 { throw err(depth == 0 ? .PARENTHESIS : .SYNTAX) }
                    return out
                } else if c == "(" {
                    i += 1
                    let inner = try seq(depth + 1)
                    if i >= s.count || s[i] != ")" { throw err(.PARENTHESIS) }
                    i += 1
                    if inner.isEmpty { throw err(.SYNTAX) }
                    out.append(.g(units: inner, count: digits() ?? 1, bond: bond))
                } else if c.isUppercase {
                    var el = String(c)
                    if i + 1 < s.count && s[i + 1].isLowercase && Elements.known(el + String(s[i + 1])) { el += String(s[i + 1]); i += 1 }
                    if !Elements.known(el) { throw err(.UNKNOWN_ELEMENT, el) }
                    i += 1
                    let count = digits() ?? 1
                    var h = 0
                    if el != "H" && i < s.count && s[i] == "H" && (i + 1 >= s.count || !s[i + 1].isLowercase) { i += 1; h = digits() ?? 1 }
                    if count == 0 { throw err(.SYNTAX) }
                    out.append(.a(el: el, count: count, h: h, bond: bond))
                } else {
                    throw err(.SYNTAX)
                }
            }
            if depth > 0 { throw err(.PARENTHESIS) }
            return out
        }
        let units = try seq(0)
        if units.isEmpty { throw err(.EMPTY) }
        let m = Molecule()
        var locked = Set<Bond>()
        func maxVal(_ a: Int) -> Int { Elements.valences(m.atoms[a].el, m.atoms[a].charge).max() ?? 0 }
        func rem(_ a: Int) -> Int { maxVal(a) - m.bondSum(a) - m.atoms[a].h }
        @discardableResult func link(_ a: Int, _ b: Int, _ order: Int?, _ lock: Bool) -> Bond {
            let bd = m.bond(a, b, order ?? 1)
            if lock || order != nil { locked.insert(bd) }
            return bd
        }

        @discardableResult
        func build(_ list: [CUnit], _ attach: Int?) throws -> Int? {
            var cur: Int? = attach
            var first: Int? = nil
            var pendingH = 0
            var pendingGroups: [(units: [CUnit], count: Int)] = []
            func placed(_ idx: Int, _ bond: Int?) throws {
                if let c = cur { link(c, idx, bond, bond != nil) }
                if first == nil { first = idx }
                cur = idx
                if !pendingGroups.isEmpty {
                    let gs = pendingGroups
                    pendingGroups.removeAll()
                    for g in gs { for _ in 0..<g.count { try build(g.units, idx) } }
                }
            }
            for (k, u) in list.enumerated() {
                let more = list[(k + 1)...].contains { x in
                    if case .g = x { return true }
                    if case let .a(el, _, _, _) = x { return el != "H" }
                    return false
                }
                switch u {
                case let .g(units, count, bond):
                    guard let c = cur else { pendingGroups.append((units, count)); continue }
                    for _ in 0..<count {
                        var inner = units
                        if bond != nil, case let .a(fel, fcount, fh, _) = units[0] {
                            inner = [.a(el: fel, count: fcount, h: fh, bond: bond)] + units.dropFirst()
                        }
                        try build(inner, c)
                    }
                case let .a(el, count, h, bond):
                    if el == "H" {
                        if let c = cur, !(attach != nil && c == attach && first == nil) { m.atoms[c].h += count } else { pendingH += count }
                        continue
                    }
                    // alkil (C2H5, C3H7…)
                    if el == "C" && count > 1 && h == 2 * count + 1 {
                        let ids = (0..<count).map { _ in m.add(Atom("C", h: 2)) }
                        for j in 1..<ids.count { link(ids[j - 1], ids[j], 1, true) }
                        if cur == nil && attach == nil {
                            m.atoms[ids[0]].h = 3
                            cur = ids.last; first = ids[0]
                            for g in pendingGroups { for _ in 0..<g.count { try build(g.units, ids.last!) } }
                            pendingGroups.removeAll()
                        } else {
                            if more { throw err(.SYNTAX, "C\(count)H\(h)") }
                            m.atoms[ids.last!].h = 3
                            link(cur!, ids[0], bond ?? 1, true)
                            if first == nil { first = ids[0] }
                        }
                        continue
                    }
                    // fenil (C6H5)
                    if el == "C" && count == 6 && h == 5 {
                        let ids = (0..<6).map { m.add(Atom("C", h: $0 == 0 ? 0 : 1)) }
                        for j in 0..<6 { link(ids[j], ids[(j + 1) % 6], j % 2 == 0 ? 2 : 1, true) }
                        try placed(ids[0], bond)
                        continue
                    }
                    if count > 1 {
                        guard let c = cur, h == 0 else { throw err(.SYNTAX, "\(el)\(count)") }
                        for _ in 0..<count { let idx = m.add(Atom(el, h: 0)); link(c, idx, bond, bond != nil) }
                        continue
                    }
                    let atom = Atom(el, h: h + pendingH)
                    pendingH = 0
                    if let c = cur, bond == nil, atom.h == 0, el == "O" || el == "S", rem(c) >= 2 + (more ? 1 : 0), m.atoms[c].el == "C" {
                        let idx = m.add(atom); link(c, idx, 2, true); if first == nil { first = idx }
                        continue
                    }
                    if let c = cur, bond == nil, atom.h == 0, el == "N", !more, rem(c) >= 3, m.atoms[c].el == "C" {
                        let idx = m.add(atom); link(c, idx, 3, true); if first == nil { first = idx }
                        continue
                    }
                    try placed(m.add(atom), bond)
                }
            }
            if !pendingGroups.isEmpty || pendingH > 0 {
                guard let c = cur else { throw err(.SYNTAX) }
                m.atoms[c].h += pendingH
                for g in pendingGroups { for _ in 0..<g.count { try build(g.units, c) } }
            }
            return first
        }
        try build(units, nil)
        if m.components().count > 1 { throw err(.SYNTAX) }
        if !m.fillUnsaturation(locked) {
            let bad = m.atoms.indices.first { rem($0) != 0 } ?? 0
            throw ChemError(code: .VALENCE, arg: m.atoms[bad].el)
        }
        return m
    }
}

// MARK: - Örnek izomer

/// Kapalı formülden değerlik kurallarına uyan örnek bir yapı.
enum Isomer {
    private static let MULTI_ORDER = ["Si", "B", "N", "P", "O", "S", "Se"]
    private static let MONO_LIST = ["F", "Cl", "Br", "I"]
    private static let MONO: Set<String> = ["F", "Cl", "Br", "I"]
    private static let OXY: Set<String> = ["O", "S", "Se"]

    static func example(_ countsList: ElementCounts) throws -> Molecule {
        var counts: [String: Int] = [:]
        for (k, v) in countsList { counts[k, default: 0] += v }
        let h = counts["H"] ?? 0
        for (el, _) in countsList {
            guard let e = Elements[el] else { throw ChemError(code: .UNKNOWN_ELEMENT, arg: el) }
            if e.metal { throw ChemError(code: .IONIC, arg: el) }
            if e.valences == [0] && (counts.count > 1 || counts[el] != 1) { throw ChemError(code: .VALENCE, arg: el) }
        }
        let heavy = counts.filter { $0.key != "H" }.values.reduce(0, +)
        if heavy > Chem.MAX_ATOMS { throw ChemError(code: .TOO_LARGE, arg: String(Chem.MAX_ATOMS)) }
        if heavy == 0 {
            if h == 2 { let m = Molecule(); let a = m.add(Atom("H")); let b = m.add(Atom("H")); m.bond(a, b); return m }
            throw ChemError(code: .HYDROGEN_COUNT)
        }
        let m = Molecule()
        let carbons = counts["C"] ?? 0
        let nX = MONO_LIST.reduce(0) { $0 + (counts[$1] ?? 0) }
        let nN = (counts["N"] ?? 0) + (counts["P"] ?? 0)
        let dbe = Double(carbons) - Double(h) / 2 - Double(nX) / 2 + Double(nN) / 2 + 1
        let cs = (0..<carbons).map { _ in m.add(Atom("C")) }
        var fixed: [Bond] = []
        if carbons >= 6 && dbe >= 4 {
            for j in 0..<6 { fixed.append(m.bond(cs[j], cs[(j + 1) % 6], j % 2 == 0 ? 2 : 1)) }
            for j in 6..<carbons { m.bond(cs[j - 1], cs[j]) }
        } else if carbons > 1 {
            for j in 1..<carbons { m.bond(cs[j - 1], cs[j]) }
        }
        func cap(_ i: Int) -> Int { (Elements.valences(m.atoms[i].el, 0).max() ?? 0) - m.bondSum(i) }
        var others: [Int] = []
        let multi = MULTI_ORDER.flatMap { el in [String](repeating: el, count: counts[el] ?? 0) }
        let mono = MONO_LIST.flatMap { el in [String](repeating: el, count: counts[el] ?? 0) }
        if carbons > 0 {
            var oxy = multi.filter { OXY.contains($0) }
            let nit = multi.filter { !OXY.contains($0) }
            for el in nit {
                guard let host = cs.first(where: { cap($0) >= 2 }) ?? cs.first(where: { cap($0) >= 1 }) else { throw ChemError(code: .HYDROGEN_COUNT) }
                let idx = m.add(Atom(el)); m.bond(host, idx); others.append(idx)
            }
            let ringSet = Set(cs.prefix(carbons >= 6 && dbe >= 4 ? 6 : 0))
            let order = cs.reversed().filter { !ringSet.contains($0) || carbons <= 6 }
            if oxy.count >= 2 && dbe >= 1 && carbons >= 1 {
                let last = cs.last!
                if cap(last) >= 3 { for _ in 0..<2 { let idx = m.add(Atom(oxy.removeFirst())); m.bond(last, idx); others.append(idx) } }
            }
            for pass in 0...1 {
                for c in order {
                    if oxy.isEmpty { break }
                    let cnt = m.neighbors(c).filter { OXY.contains(m.atoms[$0].el) }.count
                    if cnt > pass || cap(c) < 2 { continue }
                    let idx = m.add(Atom(oxy.removeFirst())); m.bond(c, idx); others.append(idx)
                }
            }
            for el in oxy {
                guard let host = (others + cs).first(where: { cap($0) >= 2 }) else { throw ChemError(code: .HYDROGEN_COUNT) }
                let idx = m.add(Atom(el)); m.bond(host, idx); others.append(idx)
            }
            for el in mono {
                guard let host = cs.first(where: { cap($0) >= 1 && !m.neighbors($0).contains { MONO.contains(m.atoms[$0].el) } })
                    ?? cs.first(where: { cap($0) >= 1 }) ?? others.first(where: { cap($0) >= 1 }) else { throw ChemError(code: .HYDROGEN_COUNT) }
                m.bond(host, m.add(Atom(el)))
            }
        } else {
            // Kotlin sortedByDescending kararlıdır
            let sorted = multi.enumerated().sorted { l, r in
                let a = Elements[l.element]!.valences.max()!, b = Elements[r.element]!.valences.max()!
                return a != b ? a > b : l.offset < r.offset
            }.map(\.element)
            if sorted.isEmpty {
                if mono.count + h != 2 { throw ChemError(code: .HYDROGEN_COUNT) }
                if mono.count == 2 { let a = m.add(Atom(mono[0])); m.bond(a, m.add(Atom(mono[1]))) } else { m.add(Atom(mono[0], h: h)) }
                return m
            }
            let center = m.add(Atom(sorted[0]))
            for el in sorted.dropFirst() {
                let hosts = [center] + others
                guard let host = hosts.first(where: { cap($0) >= 2 }) ?? hosts.first(where: { cap($0) >= 1 }) else { throw ChemError(code: .HYDROGEN_COUNT) }
                let idx = m.add(Atom(el)); m.bond(host, idx); others.append(idx)
            }
            for el in mono {
                guard let host = ([center] + others).first(where: { cap($0) >= 1 }) else { throw ChemError(code: .HYDROGEN_COUNT) }
                m.bond(host, m.add(Atom(el)))
            }
        }
        if m.components().count > 1 { throw ChemError(code: .HYDROGEN_COUNT) }
        let variable = m.atoms.indices.filter { Elements.valences(m.atoms[$0].el, 0).count > 1 }
        let choices = variable.map { Elements.valences(m.atoms[$0].el, 0) }
        let total = choices.reduce(1) { $0 * $1.count }
        for pick in 0..<min(total, 256) {
            var v = m.atoms.indices.map { Elements.valences(m.atoms[$0].el, 0).first! }
            var r = pick
            for (k, i) in variable.enumerated() { let l = choices[k]; v[i] = l[r % l.count]; r /= l.count }
            let trial = m.copy()
            let fixedTrial = Set(fixed.compactMap { fb in m.bonds.firstIndex { $0 === fb }.map { trial.bonds[$0] } })
            if saturate(trial, v, h, fixedTrial) { return trial }
        }
        throw ChemError(code: .HYDROGEN_COUNT)
    }

    private static func saturate(_ m: Molecule, _ v: [Int], _ h: Int, _ fixed: Set<Bond>) -> Bool {
        func rem(_ i: Int) -> Int { v[i] - m.bondSum(i) }
        if m.atoms.indices.contains(where: { rem($0) < 0 }) { return false }
        let total = m.atoms.indices.reduce(0) { $0 + rem($1) }
        if total < h || (total - h) % 2 != 0 { return false }
        var k = (total - h) / 2
        func score(_ b: Bond) -> Int {
            let pair: Set<String> = [m.atoms[b.a].el, m.atoms[b.b].el]
            let term = m.degree(b.a) == 1 || m.degree(b.b) == 1
            if pair.contains("C") && (pair.contains("O") || pair.contains("S")) && term { return 0 }
            if pair.contains("C") && pair.contains("N") && term { return 1 }
            if pair == ["C"] { return 2 }
            if pair.contains("O") || pair.contains("S") { return 3 }
            return 4
        }
        while k > 0 {
            let cand = m.bonds.filter { !fixed.contains($0) && $0.order < 3 && rem($0.a) >= 1 && rem($0.b) >= 1 }
            var best: Bond?
            var bk: (Int, Int, Int) = (0, 0, 0)
            for b in cand {
                let key = (score(b), b.order, -max(b.a, b.b))
                if best == nil || key < bk { best = b; bk = key }
            }
            if let best { best.order += 1; k -= 1; continue }
            let cs = m.atoms.indices.filter { m.atoms[$0].el == "C" && rem($0) >= 1 }
            var done = false
            outer: for len in [5, 4, 3, 2, 6, 7] {
                for a in cs { for b in cs {
                    if a >= b || m.bondBetween(a, b) != nil { continue }
                    if pathLength(m, a, b) == len { m.bond(a, b); k -= 1; done = true; break outer }
                } }
            }
            if !done { return false }
        }
        for i in m.atoms.indices { m.atoms[i].h = rem(i) }
        return m.valenceError() < 0
    }

    private static func pathLength(_ m: Molecule, _ a: Int, _ b: Int) -> Int {
        var d = [Int](repeating: -1, count: m.atoms.count)
        var q = [a]
        var head = 0
        d[a] = 0
        while head < q.count {
            let x = q[head]; head += 1
            for y in m.neighbors(x) where d[y] < 0 { d[y] = d[x] + 1; q.append(y) }
        }
        return d[b]
    }
}
