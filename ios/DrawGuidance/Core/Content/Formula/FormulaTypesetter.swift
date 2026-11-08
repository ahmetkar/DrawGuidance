import Foundation

/// TeX kutusu: genişlik, taban çizgisi üstü (h) ve altı (d).
struct FBox {
    var w: Float, h: Float, d: Float
    var ops: [DrawOp]
    var slots: [SlotRegion]
    var ic: Float = 0
    var char = false

    func withIc() -> FBox { ic <= 0 ? self : FBox(w: w + ic, h: h, d: d, ops: ops, slots: slots, ic: 0, char: false) }
    static let EMPTY = FBox(w: 0, h: 0, d: 0, ops: [], slots: [])
}

final class FBoxBuilder {
    var ops: [DrawOp] = []
    var slots: [SlotRegion] = []
    var x: Float = 0, h: Float = 0, d: Float = 0

    func put(_ b: FBox, _ dx: Float, _ dy: Float) {
        for o in b.ops { ops.append(o.shifted(dx, dy)) }
        for r in b.slots { slots.append(SlotRegion(name: r.name, left: r.left + dx, top: r.top + dy, right: r.right + dx, bottom: r.bottom + dy)) }
        h = max(h, b.h - dy)
        d = max(d, b.d + dy)
    }

    func append(_ b: FBox, _ dy: Float = 0) { put(b, x, dy); x += b.w }
    func op(_ o: DrawOp) { ops.append(o) }
    func build(_ w: Float? = nil, ic: Float = 0, char: Bool = false) -> FBox { FBox(w: w ?? x, h: h, d: d, ops: ops, slots: slots, ic: ic, char: char) }
}

/// TeX biçemleri: 0 display, 1 text, 2 script, 3 scriptscript.
struct MathStyle {
    let level: Int, cramped: Bool
    static let SCALE: [Float] = [1, 1, 0.7, 0.5]
    static let DISPLAY = MathStyle(level: 0, cramped: false)
    var scale: Float { Self.SCALE[level] }
    private var smaller: Int { level < 2 ? 2 : 3 }
    func sup() -> MathStyle { MathStyle(level: smaller, cramped: cramped) }
    func sub() -> MathStyle { MathStyle(level: smaller, cramped: true) }
    func num() -> MathStyle { MathStyle(level: level == 0 ? 1 : smaller, cramped: cramped) }
    func den() -> MathStyle { MathStyle(level: level == 0 ? 1 : smaller, cramped: true) }
    func cramp() -> MathStyle { MathStyle(level: level, cramped: true) }
}

/// TeX kurallarına yakın dizgi (Android `Typesetter`).
final class Typesetter {
    private final class Atom {
        let box: FBox; var left: AtomType; var right: AtomType; let st: MathStyle; let space: Float
        init(_ box: FBox, _ l: AtomType, _ r: AtomType, _ st: MathStyle, _ space: Float = .nan) { self.box = box; left = l; right = r; self.st = st; self.space = space }
        var isSpace: Bool { !space.isNaN }
    }

    struct RowResult { let box: FBox; let count: Int; let first: AtomType; let last: AtomType }

    static let REF: Float = 100
    static let EST_XH: Float = 0.52, EST_CAP: Float = 0.71, EST_ASC: Float = 0.75, EST_DSC: Float = 0.22, EST_AXIS: Float = 0.3
    static let SUP1: Float = 0.413, SUP2: Float = 0.363, SUP3: Float = 0.289, SUB1: Float = 0.15, SUB2: Float = 0.247
    static let SUP_DROP: Float = 0.386, SUB_DROP: Float = 0.05, SCRIPT_SPACE: Float = 0.05
    static let MU: [Float] = [0, 3, 4, 5]
    static let SPACING: [[Int]] = [
        [0, 1, -2, -3, 0, 0, 0, -1],
        [1, 1, 0, -3, 0, 0, 0, -1],
        [-2, -2, 0, 0, -2, 0, 0, -2],
        [-3, -3, 0, 0, -3, 0, 0, -3],
        [0, 0, 0, 0, 0, 0, 0, 0],
        [0, 1, -2, -3, 0, 0, 0, -1],
        [-1, -1, 0, -1, -1, -1, -1, -1],
        [-1, 1, -2, -3, -1, 0, -1, -1],
    ]
    static let LIMIT_OPS: Set<String> = ["sum", "prod", "coprod", "bigcup", "bigcap"]
    static let INTEGRALS: Set<String> = ["int", "iint", "iiint", "oint", "oiint"]
    static let GLYPH_DELIMS: Set<String> = ["(", ")", "[", "]", "{", "}", "|"]
    static let STROKE_DELIMS: Set<String> = ["⟨", "⟩", "⌊", "⌋", "⌈", "⌉", "‖"]
    static let ALIGN_ENVS: Set<String> = ["aligned", "align", "split", "eqnarray", "alignat", "alignedat", "flalign"]
    static let GATHER_ENVS: Set<String> = ["gathered", "gather", "multline"]
    static let CASES_ENVS: Set<String> = ["cases", "dcases", "rcases"]

    private let metrics: GlyphMetrics
    private let size: Float
    private var inkCache: [String: [Float]] = [:]
    private var advCache: [String: Float] = [:]
    private let hasInk: Bool

    private(set) var xh: Float = 0, axis: Float = 0, cap: Float = 0, rule: Float = 0
    private var fs: Float = 1
    private var parenTop: Float = 0.76, parenBot: Float = 0.2

    init(_ metrics: GlyphMetrics, _ size: Float) {
        self.metrics = metrics
        self.size = size
        hasInk = metrics.inkBounds("x", Self.REF, .ROMAN) != nil
        let x = -ink("x", .ROMAN)[1]
        xh = (0.3...0.75).contains(x) ? x : Self.EST_XH
        let m = ink("−", .ROMAN)
        let ax = -(m[1] + m[3]) / 2
        axis = hasInk && (0.15...0.5).contains(ax) ? ax : Self.EST_AXIS
        let c = -ink("H", .ROMAN)[1]
        cap = (0.5...0.9).contains(c) ? c : Self.EST_CAP
        let th = m[3] - m[1]
        rule = hasInk && (0.02...0.15).contains(th) ? min(max(th * 0.8, 0.05), 0.062) : 0.055
        fs = min(max(cap / 0.683, 0.92), 1.15)
        let p = ink("(", .ROMAN)
        parenTop = (0.5...1.2).contains(-p[1]) ? -p[1] : 0.76
        parenBot = (0.05...0.5).contains(p[3]) ? p[3] : 0.2
    }

    private func emOf(_ st: MathStyle) -> Float { size * st.scale }
    private func ruleOf(_ st: MathStyle) -> Float { rule * size * max(st.scale, 0.8) }

    private func adv(_ text: String, _ sz: Float, _ style: GlyphStyle) -> Float {
        let key = "\(style.rawValue)|\(sz)|\(text)"
        if let v = advCache[key] { return v }
        let v = metrics.advance(text, sz, style)
        advCache[key] = v
        return v
    }

    func ink(_ text: String, _ style: GlyphStyle) -> [Float] {
        let key = "\(style.rawValue)\(text)"
        if let r = inkCache[key] { return r }
        var r: [Float]? = nil
        if hasInk, let b = metrics.inkBounds(text, Self.REF, style), b.count >= 4, b.allSatisfy(\.isFinite) {
            r = (b[2] <= b[0] || b[3] <= b[1]) ? [0, 0, adv(text, Self.REF, style) / Self.REF, 0] : [b[0] / Self.REF, b[1] / Self.REF, b[2] / Self.REF, b[3] / Self.REF]
        }
        let out = r ?? estimate(text, style)
        inkCache[key] = out
        return out
    }

    private func estimate(_ text: String, _ style: GlyphStyle) -> [Float] {
        let italic = style == .ITALIC || style == .BOLD_ITALIC
        var top: Float = 0, bot: Float = 0
        for c in text {
            top = max(top, estTop(c))
            bot = max(bot, italic && c == "f" ? Self.EST_DSC : estBot(c))
        }
        let w = adv(text, Self.REF, style) / Self.REF
        let ic: Float = italic && !text.isEmpty ? estIc(text.last!) : 0
        return [0, -top, w + ic, bot]
    }

    private func estTop(_ c: Character) -> Float {
        if "acemnorsuvwxzıαεικνοπστυωγημρχψφςgpqyşç".contains(c) { return Self.EST_XH }
        if "bdfhklβδζθλξϑ".contains(c) { return Self.EST_ASC }
        if c == "t" { return 0.64 }
        if c == "i" || c == "j" || "ğöü".contains(c) { return 0.74 }
        if "İĞÖÜ".contains(c) { return 0.92 }
        if c.isDigitK || c.isUppercase { return Self.EST_CAP }
        if "()[]{}|".contains(c) { return 0.76 }
        if "+±∓×÷∈∉⊂⊆⊃⊇∪∩".contains(c) { return Self.EST_AXIS + 0.3 }
        if "−=≠≡≈∼".contains(c) { return Self.EST_AXIS + 0.16 }
        if "<>≤≥".contains(c) { return Self.EST_AXIS + 0.29 }
        if "→←↔⇒⇐⇔∝∞".contains(c) { return Self.EST_AXIS + 0.2 }
        if c == "·" { return Self.EST_AXIS + 0.07 }
        if c == "," || c == "." { return 0.11 }
        if c == ";" || c == ":" { return 0.46 }
        if "′″°".contains(c) { return 0.74 }
        if c == " " || c == "\u{00A0}" { return 0 }
        return Self.EST_CAP
    }

    private func estBot(_ c: Character) -> Float {
        if "gpqyjβγζημξρφχψςşçÇŞQ".contains(c) { return Self.EST_DSC }
        if "()[]{}".contains(c) { return 0.2 }
        if c == "|" { return 0.24 }
        if c == "," || c == ";" { return 0.14 }
        return 0
    }

    private func estIc(_ c: Character) -> Float {
        if c == "f" { return 0.1 }
        if "TVWYFPXUHIJKMNZEDBRSCGL".contains(c) { return 0.05 }
        if "bdhklt".contains(c) { return 0.02 }
        return 0.01
    }

    private func glyph(_ text: String, _ style: GlyphStyle, _ sz: Float, _ char: Bool) -> FBox {
        let w = adv(text, sz, style)
        let k = ink(text, style)
        let italic = style == .ITALIC || style == .BOLD_ITALIC
        let ic: Float = italic ? min(max(k[2] * sz - w, 0), 0.16 * sz) : 0
        return FBox(w: w, h: max(0, -k[1] * sz), d: max(0, k[3] * sz), ops: [.glyph(text: text, x: 0, y: 0, size: sz, style: style, slot: nil)], slots: [], ic: ic, char: char)
    }

    // MARK: Satırlar

    func layoutRoot(_ root: RowNode) -> FormulaLayout {
        let box = row(root.items, .DISPLAY).withIc()
        return FormulaLayout(width: box.w, ascent: max(box.h, parenTop * size), descent: max(box.d, parenBot * size), ops: box.ops, slots: mergeRegions(box.slots))
    }

    private func mergeRegions(_ list: [SlotRegion]) -> [SlotRegion] {
        if list.count < 2 { return list }
        var out = list
        var merged = true
        while merged {
            merged = false
            outer: for a in out.indices {
                for b in out.indices where a != b {
                    let p = out[a], q = out[b]
                    if p.name != q.name || q.left < p.left { continue }
                    let gap = q.left - p.right
                    let vOverlap = min(p.bottom, q.bottom) - max(p.top, q.top)
                    if gap <= 0.3 * size && vOverlap > 0 {
                        out[a] = SlotRegion(name: p.name, left: p.left, top: min(p.top, q.top), right: max(p.right, q.right), bottom: max(p.bottom, q.bottom))
                        out.remove(at: b)
                        merged = true
                        break outer
                    }
                }
            }
        }
        return out
    }

    private func box(_ n: FNode, _ st: MathStyle) -> FBox { (n as? RowNode).map { row($0.items, st) } ?? row([n], st) }

    func row(_ items: [FNode], _ st: MathStyle) -> FBox { rowFull(items, st).box }

    func rowFull(_ items: [FNode], _ st0: MathStyle) -> RowResult {
        let nodes = SlotRules.apply(items)
        var st = st0
        var atoms: [Atom] = []
        for n in nodes {
            if let s = n as? StyleNode { st = MathStyle(level: s.level, cramped: st.cramped) }
            else if let sp = n as? SpaceNode { atoms.append(Atom(.EMPTY, .ORD, .ORD, st, sp.em * emOf(st))) }
            else { atoms.append(atom(n, st)) }
        }
        var prev: Atom? = nil
        var count = 0
        var first: Atom? = nil
        for a in atoms where !a.isSpace {
            if a.left == .BIN, prev == nil || [.BIN, .OP, .REL, .OPEN, .PUNCT].contains(prev!.right) { a.left = .ORD; a.right = .ORD }
            if [.REL, .CLOSE, .PUNCT].contains(a.left), let p = prev, p.right == .BIN { p.left = .ORD; p.right = .ORD }
            if first == nil { first = a }
            prev = a
            count += 1
        }
        if let p = prev, p.right == .BIN { p.left = .ORD; p.right = .ORD }
        if atoms.count == 1 && count == 1 {
            let a = atoms[0]
            return RowResult(box: a.box, count: 1, first: a.left, last: a.right)
        }
        let b = FBoxBuilder()
        var last: Atom? = nil
        for a in atoms {
            if a.isSpace { b.x += a.space; continue }
            if let l = last {
                b.x += gap(l.right, a.left, a.st)
                if l.right == .OP && a.left == .OPEN && a.box.h > 1.1 * emOf(a.st) { b.x += 3 / 18 * emOf(a.st) }
            }
            b.append(a.box.withIc())
            last = a
        }
        return RowResult(box: b.build(), count: count, first: first?.left ?? .ORD, last: last?.right ?? .ORD)
    }

    private func gap(_ l: AtomType, _ r: AtomType, _ st: MathStyle) -> Float {
        let code = Self.SPACING[l.rawValue][r.rawValue]
        if code == 0 || (code < 0 && st.level >= 2) { return 0 }
        return Self.MU[abs(code)] / 18 * emOf(st)
    }

    private func atom(_ n: FNode, _ st: MathStyle) -> Atom {
        switch n {
        case let s as SymNode: return Atom(s.pad > 0 ? padded(sym(s, st), s.pad * emOf(st)) : sym(s, st), s.type, s.type, st)
        case let s as ScriptsNode: return scripts(s, st)
        case let f as FracNode: return Atom(frac(f, st), .INNER, .INNER, st)
        case let q as SqrtNode: return Atom(sqrt(q, st), .ORD, .ORD, st)
        case let a as AccentNode: return Atom(accent(a, st), .ORD, .ORD, st)
        case let f as FenceNode: return f.binom ? Atom(fence(f, st), .INNER, .INNER, st) : Atom(fence(f, st), .OPEN, .CLOSE, st)
        case let m as MatrixNode:
            let (b, delimited) = matrix(m, st)
            return delimited ? Atom(b, .OPEN, .CLOSE, st) : Atom(b, .ORD, .ORD, st)
        case let t as TextNode: return Atom(glyph(t.text, t.style, emOf(st), false), .ORD, .ORD, st)
        case let s as SlotNode: return slot(s, st)
        case let b as BigOpNode: return Atom(bigOp(b, st), .OP, .OP, st)
        case let f as FuncNode: return Atom(glyph(f.name, .ROMAN, emOf(st), false), .OP, .OP, st)
        case let d as DotsNode: return Atom(dots(d, st), .INNER, .INNER, st)
        case let r as RowNode: return Atom(rowFull(r.items, st).box, .ORD, .ORD, st)
        case let sp as SpaceNode: return Atom(.EMPTY, .ORD, .ORD, st, sp.em * emOf(st))
        default: return Atom(.EMPTY, .ORD, .ORD, st, 0)
        }
    }

    private func padded(_ b: FBox, _ pad: Float) -> FBox {
        let out = FBoxBuilder()
        let bi = b.withIc()
        out.put(bi, pad, 0)
        return out.build(bi.w + 2 * pad)
    }

    private func sym(_ n: SymNode, _ st: MathStyle) -> FBox {
        if n.text.isEmpty { return .EMPTY }
        if Self.STROKE_DELIMS.contains(n.text) { return delim(n.text, 0, st, false) }
        return glyph(n.text, n.style, emOf(st), true)
    }

    private func tagged(_ ops: [DrawOp], _ name: String) -> [DrawOp] {
        ops.map {
            if case let .glyph(t, x, y, s, st, slot) = $0, slot == nil { return .glyph(text: t, x: x, y: y, size: s, style: st, slot: name) }
            return $0
        }
    }

    private func slot(_ n: SlotNode, _ st: MathStyle) -> Atom {
        let r = rowFull(n.body.items, st)
        let em = emOf(st)
        var b = r.box
        if b.w <= 0 && b.ops.isEmpty { b = FBox(w: 0.55 * em, h: xh * em, d: 0, ops: [], slots: []) }
        let top = -max(b.h, (xh + 0.12) * em) - 0.05 * em
        let bottom = max(b.d, 0.1 * em) + 0.05 * em
        let region = SlotRegion(name: n.name, left: -0.03 * em, top: top, right: b.w + b.ic + 0.03 * em, bottom: bottom)
        let bx = FBox(w: b.w, h: b.h, d: b.d, ops: tagged(b.ops, n.name), slots: b.slots + [region], ic: b.ic, char: b.char)
        let single = n.body.items.count == 1 ? n.body.items[0] as? SymNode : nil
        let lt = n.type ?? single?.type ?? (r.count == 1 ? r.first : .ORD)
        let rt = n.type ?? single?.type ?? (r.count == 1 ? r.last : .ORD)
        return Atom(bx, lt, rt, st)
    }

    // MARK: İndisler

    private func scripts(_ n: ScriptsNode, _ st: MathStyle) -> Atom {
        let base = n.base
        if let sl = base as? SlotNode, sl.type == nil, sl.body.items.count == 1, let inner = sl.body.items[0] as? ScriptsNode,
           n.sub == nil, let sup = n.sup, n.primes == 0, inner.sup == nil, inner.primes == 0, inner.sub != nil {
            return slotScripts(sl, inner, sup, st)
        }
        let ba = atom(base, st)
        var lim = false
        if let b = base as? BigOpNode { lim = b.limits ?? (st.level == 0 && Self.LIMIT_OPS.contains(b.kind)) }
        else if let f = base as? FuncNode { lim = f.limits ?? (st.level == 0 && f.limitsDefault) }
        let b = lim && n.primes == 0 && (n.sup != nil || n.sub != nil) ? limits(ba.box, n.sup, n.sub, st) : attach(ba.box, n.sup, n.sub, n.primes, st)
        return Atom(b, ba.left, ba.right, st)
    }

    private func slotScripts(_ slot: SlotNode, _ inner: ScriptsNode, _ sup: FNode, _ st: MathStyle) -> Atom {
        let ba = atom(inner.base, st)
        var baseBox = ba.box; baseBox.ops = tagged(baseBox.ops, slot.name)
        var subBox = box(inner.sub!, st.sub()); subBox.ops = tagged(subBox.ops, slot.name)
        let b = attach(baseBox, sup, nil, 0, st, subBox)
        let em = emOf(st)
        let region = SlotRegion(name: slot.name, left: -0.03 * em, top: -max(baseBox.h, (xh + 0.12) * em) - 0.05 * em,
                                right: max(baseBox.w + baseBox.ic, baseBox.w + subBox.w + subBox.ic) + 0.03 * em,
                                bottom: max(baseBox.d, lastSubShift + subBox.d) + 0.05 * em)
        return Atom(FBox(w: b.w, h: b.h, d: b.d, ops: b.ops, slots: b.slots + [region], ic: b.ic, char: false), ba.left, ba.right, st)
    }

    private var lastSubShift: Float = 0

    private func isDegree(_ n: FNode) -> Bool {
        let s = (n as? RowNode).map { $0.items.count == 1 ? $0.items[0] : nil } ?? n
        guard let sym = s as? SymNode else { return false }
        return sym.text == "∘" || sym.text == "°"
    }

    private func attach(_ b: FBox, _ supN: FNode?, _ subN: FNode?, _ primes: Int, _ st: MathStyle, _ subPre: FBox? = nil) -> FBox {
        let em = emOf(st)
        let out = FBoxBuilder()
        out.put(b, 0, 0)
        var sup = supN
        var tail = primes > 0 ? String(repeating: "′", count: primes) : ""
        if let s = sup, isDegree(s) { tail += "°"; sup = nil }
        var supX = b.w + b.ic
        let subX = b.w
        var right = b.w + b.ic
        if !tail.isEmpty {
            let pb = glyph(tail, .ROMAN, em, false)
            let dx = supX + (primes > 0 ? 0.03 * em : 0.01 * em)
            let raise: Float = b.char ? 0 : max(b.h - 0.04 * em - pb.h, 0)
            out.put(pb, dx, -raise)
            supX = dx + pb.w
            right = supX
        }
        if sup == nil && subN == nil && subPre == nil { return out.build(right) }
        let supSt = st.sup(), subSt = st.sub()
        let supB = sup.map { box($0, supSt) }
        let subB = subPre ?? subN.map { box($0, subSt) }
        var u: Float = b.char ? 0 : b.h - Self.SUP_DROP * self.emOf(supSt)
        var v: Float = b.char ? 0 : b.d + Self.SUB_DROP * self.emOf(subSt)
        let xhE = xh * em
        if let sb = supB {
            let p = (st.level == 0 && !st.cramped ? Self.SUP1 : st.cramped ? Self.SUP3 : Self.SUP2) * em * fs
            u = max(u, p, sb.d + xhE / 4)
        }
        if let sb = subB, supB == nil { v = max(v, Self.SUB1 * em * fs, sb.h - 0.8 * xhE) }
        if let sb = subB, let pb = supB {
            v = max(v, Self.SUB2 * em * fs)
            let minGap = 0.16 * em
            let g = (u - pb.d) - (sb.h - v)
            if g < minGap {
                v += minGap - g
                let psi = 0.8 * xhE - (u - pb.d)
                if psi > 0 { u += psi; v -= psi }
            }
        }
        var w = right
        if let pb = supB { out.put(pb, supX, -u); w = max(w, supX + pb.w + pb.ic) }
        if let sb = subB { out.put(sb, subX, v); w = max(w, subX + sb.w + sb.ic) }
        lastSubShift = v
        return out.build(w + Self.SCRIPT_SPACE * em)
    }

    private func limits(_ op: FBox, _ supN: FNode?, _ subN: FNode?, _ st: MathStyle) -> FBox {
        let em = emOf(st)
        let supB = supN.map { box($0, st.sup()).withIc() }
        let subB = subN.map { box($0, st.sub()).withIc() }
        let w = max(op.w, supB?.w ?? 0, subB?.w ?? 0)
        let out = FBoxBuilder()
        out.put(op, (w - op.w) / 2, 0)
        if let sb = supB {
            let shift = op.h + max(0.111 * em, 0.2 * em - sb.d) + sb.d
            out.put(sb, (w - sb.w) / 2, -shift)
            out.h = max(out.h, shift + sb.h + 0.1 * em)
        }
        if let sb = subB {
            let shift = op.d + max(0.167 * em, 0.6 * em - sb.h) + sb.h
            out.put(sb, (w - sb.w) / 2, shift)
            out.d = max(out.d, shift + sb.d + 0.1 * em)
        }
        return out.build(w)
    }

    // MARK: Kesir, kök, aksan

    private func frac(_ n: FracNode, _ st0: MathStyle) -> FBox {
        let st = n.forced == 0 ? MathStyle(level: 0, cramped: st0.cramped) : n.forced == 1 ? MathStyle(level: 1, cramped: st0.cramped) : st0
        let nb = box(n.num, st.num()).withIc()
        let db = box(n.den, st.den()).withIc()
        let em = emOf(st), t = ruleOf(st), a = axis * em
        let display = st.level == 0
        let u: Float, v: Float
        if n.bar {
            let clr = display ? 3 * t : t
            let gapN = (display ? 0.22 : 0.12) * em
            let gapD = (display ? 0.18 : 0.1) * em
            let denCap = cap * self.emOf(st.den())
            u = max(a + t / 2 + gapN, a + t / 2 + clr + nb.d)
            v = max(t / 2 - a + gapD + denCap, t / 2 - a + clr + db.h)
        } else {
            let clr = (display ? 5 : 3) * t
            u = max(a + clr / 2 + nb.d, a + 0.28 * em)
            v = max(-a + clr / 2 + db.h, cap * self.emOf(st.den()) - a + clr / 2)
        }
        let inner = max(nb.w, db.w)
        let ext: Float = n.bar ? 0.07 * em : 0
        let pad: Float = n.bar ? 0.08 * em : 0.02 * em
        let w = inner + 2 * ext + 2 * pad
        let out = FBoxBuilder()
        out.put(nb, pad + ext + (inner - nb.w) / 2, -u)
        out.put(db, pad + ext + (inner - db.w) / 2, v)
        if n.bar {
            out.op(.rule(x1: pad, y1: -a, x2: w - pad, y2: -a, thickness: t))
            out.h = max(out.h, a + t / 2)
        }
        return out.build(w)
    }

    private func sqrt(_ n: SqrtNode, _ st: MathStyle) -> FBox {
        let body = box(n.body, st.cramp()).withIc()
        let em = emOf(st), t = ruleOf(st)
        let phi = st.level == 0 ? xh * em : t
        let psi = t + phi / 4
        let need = body.h + body.d + psi + t
        let total = max(need, (st.level == 0 ? 1.1 : 1.02) * em)
        let extra = total - need
        let barY = -(body.h + psi + extra / 2 + t / 2)
        let botY = body.d + extra / 2
        let out = FBoxBuilder()
        var x0: Float = 0
        var idx: FBox? = nil
        if let index = n.index {
            let ib = box(index, MathStyle(level: 3, cramped: false)).withIc()
            x0 = max(0, ib.w - 0.3 * em)
            idx = ib
        }
        let (ops, sw) = FormulaShapes.radical(x0, barY, botY, em, t)
        ops.forEach { out.op($0) }
        if let ib = idx {
            let bottom = botY - max(0.55 * total, 0.5 * em)
            out.put(ib, x0 + 0.3 * em - ib.w, bottom - ib.d)
        }
        let bx = x0 + sw + 0.06 * em
        out.put(body, bx, 0)
        let barEnd = bx + body.w + 0.06 * em
        out.op(.rule(x1: x0 + sw - t * 0.35, y1: barY, x2: barEnd, y2: barY, thickness: t))
        out.h = max(out.h, -barY + t / 2 + t)
        out.d = max(out.d, botY + t / 2)
        return out.build(barEnd + 0.05 * em)
    }

    private func accent(_ n: AccentNode, _ st: MathStyle) -> FBox {
        let raw = box(n.body, st.cramp())
        let em = emOf(st), t = ruleOf(st)
        let single = raw.char
        var italicChar = false
        if single, let f = raw.ops.first, case let .glyph(_, _, _, _, style, _) = f { italicChar = style == .ITALIC || style == .BOLD_ITALIC }
        let body = raw.withIc()
        let skew: Float = italicChar ? min(max(0.2 * (raw.h - 0.5 * xh * em), 0), 0.12 * em) : 0
        let cx = raw.w / 2 + skew
        let out = FBoxBuilder()
        out.put(body, 0, 0)
        let th = t * 0.9
        switch n.kind {
        case .VEC, .LEFT_VEC:
            let aw = single ? max(0.48 * em, raw.w * 0.95) : body.w
            let y = -(raw.h + 0.2 * em)
            let x0 = cx - aw / 2, x1 = cx + aw / 2
            let hx = 0.14 * em, hy = 0.09 * em
            out.op(.rule(x1: x0, y1: y, x2: x1, y2: y, thickness: th))
            if n.kind == .VEC { out.op(.line([x1 - hx, y - hy, x1, y, x1 - hx, y + hy], th)) } else { out.op(.line([x0 + hx, y - hy, x0, y, x0 + hx, y + hy], th)) }
            out.h = max(out.h, -y + hy + th)
        case .OVERLINE:
            let y = -(raw.h + 3 * t + t / 2)
            out.op(.rule(x1: 0.02 * em, y1: y, x2: body.w - 0.02 * em, y2: y, thickness: t))
            out.h = max(out.h, -y + t / 2 + t)
        case .UNDERLINE:
            let y = raw.d + 3 * t + t / 2
            out.op(.rule(x1: 0.02 * em, y1: y, x2: body.w - 0.02 * em, y2: y, thickness: t))
            out.d = max(out.d, y + t / 2 + t)
        case .BAR:
            let bw = single ? max(0.32 * em, min(raw.w * 0.85, 0.55 * em)) : body.w
            let y = -(raw.h + 0.14 * em)
            out.op(.rule(x1: cx - bw / 2, y1: y, x2: cx + bw / 2, y2: y, thickness: th))
            out.h = max(out.h, -y + th)
        case .HAT, .WIDEHAT:
            let hw = single && n.kind == .HAT ? max(0.36 * em, min(raw.w * 0.75, 0.5 * em)) : body.w
            let hh = hw > 0.8 * em ? 0.18 * em : 0.14 * em
            let yb = -(raw.h + 0.09 * em)
            out.op(.line([cx - hw / 2, yb, cx, yb - hh, cx + hw / 2, yb], th))
            out.h = max(out.h, -yb + hh + th)
        case .DOT, .DDOT:
            let r = 0.055 * em
            let y = -(raw.h + 0.15 * em)
            if n.kind == .DOT { out.op(FormulaShapes.circle(cx, y, r)) } else {
                out.op(FormulaShapes.circle(cx - 0.11 * em, y, r)); out.op(FormulaShapes.circle(cx + 0.11 * em, y, r))
            }
            out.h = max(out.h, -y + r + 0.02 * em)
        case .TILDE:
            let tw = single ? max(0.4 * em, raw.w * 0.8) : body.w
            let y = -(raw.h + 0.16 * em)
            let amp = 0.05 * em
            var pts: [Float] = []
            for k in 0...16 {
                let f = Float(k) / 16
                pts.append(cx - tw / 2 + f * tw)
                pts.append(y - amp * Float(Foundation.sin(Double(f) * 2 * Double.pi)))
            }
            out.op(.line(pts, th))
            out.h = max(out.h, -y + amp + th)
        }
        return out.build(body.w)
    }

    // MARK: Ayraçlar ve matrisler

    private func fence(_ n: FenceNode, _ st: MathStyle) -> FBox {
        let body = row(n.body.items, st).withIc()
        let em = emOf(st), a = axis * em
        let delta = max(body.h - a, body.d + a)
        let need = max(2 * delta * 0.901, 2 * delta - 0.5 * em)
        let out = FBoxBuilder()
        out.append(delim(n.left, need, st, n.auto))
        out.append(body)
        out.append(delim(n.right, need, st, n.auto))
        return out.build()
    }

    private func delim(_ ch: String, _ need: Float, _ st: MathStyle, _ auto: Bool) -> FBox {
        let em = emOf(st), a = axis * em
        if ch == "." || ch.isEmpty { return FBox(w: 0.1 * em, h: 0, d: 0, ops: [], slots: []) }
        if Self.GLYPH_DELIMS.contains(ch) {
            let g = glyph(ch, .ROMAN, em, false)
            if need <= (g.h + g.d) * (auto ? 1.2 : 1.02) { return g }
        }
        let total = max(need, (parenTop + parenBot) * em)
        let top = -(a + total / 2), bottom = total / 2 - a
        let w = FormulaShapes.delimiterWidth(ch, total, em)
        return FBox(w: w, h: -top, d: bottom, ops: FormulaShapes.delimiter(ch, 0, top, bottom, em, ruleOf(st)), slots: [])
    }

    private func matrix(_ n: MatrixNode, _ st: MathStyle) -> (FBox, Bool) {
        let env = n.env
        let aligned = Self.ALIGN_ENVS.contains(env), gathered = Self.GATHER_ENVS.contains(env), cases = Self.CASES_ENVS.contains(env)
        let small = env == "smallmatrix"
        let cellSt: MathStyle = aligned || gathered || env == "dcases" ? st
            : small ? MathStyle(level: max(st.level, 2), cramped: st.cramped) : MathStyle(level: max(st.level, 1), cramped: st.cramped)
        let em = emOf(st), cellEm = self.emOf(cellSt)
        let rows: [[FBox]] = n.rows.map { r in
            r.enumerated().map { j, c in
                let items: [FNode] = aligned && j % 2 == 1 ? [RowNode([]) as FNode] + c.items : c.items
                return row(items, cellSt).withIc()
            }
        }
        let ncol = max(1, rows.map(\.count).max() ?? 1)
        var colW = [Float](repeating: 0, count: ncol)
        for r in rows { for (j, b) in r.enumerated() { colW[j] = max(colW[j], b.w) } }
        let spec = n.colSpec.map { Array($0.filter { $0 == "l" || $0 == "c" || $0 == "r" }) }
        let align: [Character] = (0..<ncol).map { j in
            if cases { return "l" }
            if aligned { return j % 2 == 0 ? "r" : "l" }
            if let spec, j < spec.count { return spec[j] }
            return "c"
        }
        let colGap: [Float] = (0..<ncol).map { j in
            if j == ncol - 1 { return 0 }
            if cases { return 1.0 * em }
            if aligned { return j % 2 == 1 ? 1.5 * em : 0 }
            if small { return 0.45 * em }
            return 0.9 * em
        }
        let strutH = 0.85 * cellEm, strutD = 0.35 * cellEm
        let rowGap: Float = aligned || gathered ? 0.3 * em : cases ? 0.22 * em : small ? 0.05 * em : 0.1 * em
        let rh = rows.map { r in max(strutH, r.map(\.h).max() ?? 0) }
        let rd = rows.map { r in max(strutD, r.map(\.d).max() ?? 0) }
        var total: Float = 0
        for i in rows.indices { total += rh[i] + rd[i] }
        total += rowGap * Float(max(rows.count - 1, 0))
        let a = axis * em
        var colX = [Float](repeating: 0, count: ncol)
        var cx: Float = 0
        for j in 0..<ncol { colX[j] = cx; cx += colW[j] + colGap[j] }
        let inner = FBoxBuilder()
        var y = -(a + total / 2)
        for (i, r) in rows.enumerated() {
            y += rh[i]
            for (j, b) in r.enumerated() {
                let dx: Float = align[j] == "l" ? 0 : align[j] == "r" ? colW[j] - b.w : (colW[j] - b.w) / 2
                inner.put(b, colX[j] + dx, y)
            }
            y += rd[i] + rowGap
        }
        inner.h = max(inner.h, a + total / 2)
        inner.d = max(inner.d, total / 2 - a)
        let innerBox = inner.build(cx)
        let delims: (String, String)?
        switch env {
        case "pmatrix": delims = ("(", ")")
        case "bmatrix": delims = ("[", "]")
        case "Bmatrix": delims = ("{", "}")
        case "vmatrix": delims = ("|", "|")
        case "Vmatrix": delims = ("‖", "‖")
        case "cases", "dcases": delims = ("{", ".")
        case "rcases": delims = (".", "}")
        default: delims = nil
        }
        guard let delims else { return (innerBox, false) }
        let delta = max(innerBox.h - a, innerBox.d + a)
        let need = max(2 * delta * 0.901, 2 * delta - 0.5 * em)
        let out = FBoxBuilder()
        out.append(delim(delims.0, need, st, false))
        out.x += cases ? 0.12 * em : 0.06 * em
        out.append(innerBox)
        out.x += delims.1 == "." ? 0 : 0.06 * em
        out.append(delim(delims.1, need, st, false))
        return (out.build(), true)
    }

    // MARK: Büyük işleçler

    private func bigOp(_ n: BigOpNode, _ st: MathStyle) -> FBox {
        let em = emOf(st), a = axis * em
        if Self.INTEGRALS.contains(n.kind) { return integral(n.kind, st) }
        let ch: String
        switch n.kind {
        case "prod": ch = "Π"
        case "coprod": ch = "∐"
        case "bigcup": ch = "∪"
        case "bigcap": ch = "∩"
        default: ch = "Σ"
        }
        let k: Float = (st.level == 0 ? 1.6 : st.level == 1 ? 1.2 : 1.1) * (ch == "∪" || ch == "∩" ? 1.1 : 1)
        let g = glyph(ch, .ROMAN, em * k, false)
        let dy = (g.h - g.d) / 2 - a
        let out = FBoxBuilder()
        out.put(g, 0.04 * em, dy)
        return out.build(g.w + 0.08 * em)
    }

    private func integral(_ kind: String, _ st: MathStyle) -> FBox {
        let em = emOf(st), a = axis * em
        let hgt = em * (st.level == 0 ? 2.05 : st.level == 1 ? 1.3 : 1.15)
        let top = -(a + hgt / 2)
        let count = (kind == "iint" || kind == "oiint") ? 2 : kind == "iiint" ? 3 : 1
        let wOne = 0.44 * hgt
        let step = 0.2 * hgt + 0.08 * em
        let sb = 0.05 * em
        let maxW = max(0.05 * hgt, 1.4 * ruleOf(st))
        var ops: [DrawOp] = []
        for k in 0..<count { ops += FormulaShapes.integral(sb + Float(k) * step, top, hgt, maxW) }
        if kind == "oint" || kind == "oiint" {
            let c = sb + Float(count - 1) * step / 2 + 0.22 * hgt
            ops.append(FormulaShapes.ellipse(c, top + 0.5 * hgt, 0.11 * hgt + Float(count - 1) * step / 2, 0.1 * hgt, ruleOf(st) * 0.9))
        }
        let inkRight = sb + Float(count - 1) * step + wOne
        let w = sb + Float(count - 1) * step + 0.5 * wOne
        return FBox(w: w, h: a + hgt / 2, d: hgt / 2 - a, ops: ops, slots: [], ic: inkRight - w + 0.05 * em, char: false)
    }

    private func dots(_ n: DotsNode, _ st: MathStyle) -> FBox {
        let em = emOf(st)
        let r = 0.052 * em, side = 0.1 * em
        let out = FBoxBuilder()
        let w: Float
        switch n.kind {
        case "v":
            for k in 0...2 { out.op(FormulaShapes.circle(side + r, -(0.1 * em + r + Float(k) * 0.3 * em), r)) }
            out.h = 0.1 * em + 2 * r + 0.6 * em
            w = 2 * side + 2 * r
        case "d":
            for k in 0...2 { out.op(FormulaShapes.circle(side + r + Float(k) * 0.28 * em, -(0.7 * em - Float(k) * 0.28 * em), r)) }
            out.h = 0.7 * em + r
            w = 2 * side + 2 * r + 0.56 * em
        default:
            let y = n.kind == "c" ? -axis * em : -r - 0.005 * em
            for k in 0...2 { out.op(FormulaShapes.circle(side + r + Float(k) * 0.28 * em, y, r)) }
            out.h = -y + r
            w = 2 * side + 2 * r + 0.56 * em
        }
        return out.build(w)
    }
}

// MARK: - Yuva kuralları

/// Yuvaya sayı/ifade yazıldığında okunaklılığı koruyan dönüşümler (Android `SlotRules`).
enum SlotRules {
    private static func slotOf(_ n: FNode) -> SlotNode? { (n as? SlotNode) ?? ((n as? ScriptsNode)?.base as? SlotNode) }

    static func apply(_ items: [FNode]) -> [FNode] {
        if !items.contains(where: { slotOf($0) != nil && slotOf($0)!.type == nil }) { return items }
        var out = items
        var k = 0
        while k < out.count {
            let n = out[k]
            guard let slot = slotOf(n), slot.type == nil else { k += 1; continue }
            let body = slot.body.items
            let sc = n as? ScriptsNode
            let scripted = sc != nil && (sc!.sup != nil || sc!.primes > 0)
            let pi = prevIndex(out, k)
            let prev = pi.map { out[$0] }
            let next = nextIndex(out, k).map { out[$0] }
            let lead = leadingSign(body)
            var newBody: [FNode]? = nil
            if let lead, body.count > 1 {
                if !scripted, let p = prev as? SymNode, p.text == "+" || p.text == "−" {
                    let merged = p.text == lead ? "+" : "−"
                    newBody = Array(body[1...])
                    if merged == "+" && isUnary(out, pi!) {
                        out.remove(at: pi!)
                        k -= 1
                    } else {
                        out[pi!] = SlotNode(slot.name, RowNode([SymNode(merged, .BIN, .ROMAN)]), .BIN)
                    }
                } else if scripted || ordLike(prev, true) {
                    newBody = [paren(body)]
                }
            } else if hasInnerOperator(body) && (scripted || ordLike(prev, true) || ordLikeNext(next)) {
                newBody = [paren(body)]
            } else if startsWithDigit(body) && ordLike(prev, false) {
                out.insert(SymNode("·", .BIN, .ROMAN, kind: SymNode.K_AUTO_DOT), at: k)
                k += 1
            }
            if let nb = newBody {
                let ns = SlotNode(slot.name, RowNode(nb))
                if let sc { out[k] = ScriptsNode(ns, sc.sup, sc.sub, sc.primes) } else { out[k] = ns }
            }
            if let next, endsWithDigit(slot.body.items) && startsWithDigit([next]) {
                out.insert(SymNode("·", .BIN, .ROMAN, kind: SymNode.K_AUTO_DOT), at: k + 1)
                k += 1
            }
            k += 1
        }
        return out
    }

    private static func paren(_ body: [FNode]) -> FNode { FenceNode("(", RowNode(body), ")", auto: true) }

    private static func prevIndex(_ l: [FNode], _ k: Int) -> Int? {
        var j = k - 1
        while j >= 0 && (l[j] is SpaceNode || l[j] is StyleNode) { j -= 1 }
        return j >= 0 ? j : nil
    }

    private static func nextIndex(_ l: [FNode], _ k: Int) -> Int? {
        var j = k + 1
        while j < l.count && (l[j] is SpaceNode || l[j] is StyleNode) { j += 1 }
        return j < l.count ? j : nil
    }

    private static func leadingSign(_ body: [FNode]) -> String? {
        guard let f = body.first as? SymNode else { return nil }
        return f.text == "−" || f.text == "+" ? f.text : nil
    }

    private static func isUnary(_ l: [FNode], _ idx: Int) -> Bool {
        guard let pi = prevIndex(l, idx) else { return true }
        let p = l[pi]
        if let s = p as? SymNode { return [.BIN, .REL, .OPEN, .PUNCT].contains(s.type) }
        if let s = p as? SlotNode { return s.type == .BIN }
        return p is BigOpNode || p is FuncNode
    }

    private static func hasInnerOperator(_ body: [FNode]) -> Bool {
        body.dropFirst().contains { n in
            guard let s = n as? SymNode else { return false }
            return s.type == .BIN || s.type == .REL
        }
    }

    private static func ordLike(_ n: FNode?, _ forParens: Bool) -> Bool {
        guard let n else { return false }
        if let s = n as? SymNode { return s.type == .ORD || s.type == .CLOSE }
        if let s = n as? ScriptsNode { return ordLike(s.base, forParens) }
        if n is FracNode || n is SqrtNode || n is FenceNode || n is AccentNode || n is MatrixNode { return true }
        if let s = n as? SlotNode { return s.type == nil }
        if let r = n as? RowNode { return !r.items.isEmpty }
        if n is FuncNode || n is BigOpNode { return forParens }
        return false
    }

    private static func ordLikeNext(_ n: FNode?) -> Bool {
        guard let n else { return false }
        if let s = n as? SymNode { return s.type == .ORD || s.type == .OPEN }
        if let s = n as? ScriptsNode { return ordLikeNext(s.base) }
        if n is FracNode || n is SqrtNode || n is FenceNode || n is AccentNode || n is MatrixNode || n is FuncNode { return true }
        if let s = n as? SlotNode { return s.type == nil }
        if let r = n as? RowNode { return !r.items.isEmpty }
        return false
    }

    static func startsWithDigit(_ body: [FNode]) -> Bool {
        guard let f = body.first else { return false }
        if let s = f as? SymNode { return s.kind == SymNode.K_DIGITS }
        if let s = f as? ScriptsNode { return (s.base as? SymNode)?.kind == SymNode.K_DIGITS }
        if let s = f as? SlotNode { return s.type == nil && startsWithDigit(s.body.items) }
        return false
    }

    private static func endsWithDigit(_ body: [FNode]) -> Bool { (body.last as? SymNode)?.kind == SymNode.K_DIGITS }
}
