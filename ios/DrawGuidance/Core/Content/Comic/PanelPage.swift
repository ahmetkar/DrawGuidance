import Foundation

/// v36 çizgi roman sayfa düzeni (Android `PanelPage.kt`): şablonların ve sayfadaki panellerin ortak modeli. Sayfa,
/// kenar boşluğu içinde art arda bölünür: bir bölme (`split`) alanı üst üste ya da yan yana çocuklara oranlarla
/// ayırır; bölme çizgileri eğik olabilir. Yapraklar paneldir (`leaf`) ya da boş alandır (`space`). İç paneller
/// ([PanelInset]) başka bir panelin üstünde durur. Kısa metin biçimi (`encode`/`decode`) Android ile aynıdır.
indirect enum PanelNode: Equatable {
    /// Panel: düzendeki kimliği; köşe yuvarlaklığı ve pahı sayfanın kısa kenarına oran.
    case leaf(id: Int, round: Float, bevel: Float)
    /// Boş alan (panel çizilmez).
    case space
    /// Bölme: `rows` ise çocuklar üst üste, değilse yan yana. `skews` aralarındaki çizgilerin eğimi (n-1 tane).
    case split(Split)

    struct Split: Equatable {
        var rows: Bool
        var children: [PanelNode]
        var weights: [Float]
        var skews: [Float]
        var gap: Float

        init(_ rows: Bool, _ children: [PanelNode], _ weights: [Float], _ skews: [Float]? = nil, _ gap: Float = 1) {
            self.rows = rows
            self.children = children
            self.weights = weights
            self.skews = skews ?? [Float](repeating: 0, count: max(0, children.count - 1))
            self.gap = gap
        }
    }

    static func Leaf(_ id: Int, _ round: Float = 0, _ bevel: Float = 0) -> PanelNode { .leaf(id: id, round: round, bevel: bevel) }

    var leafId: Int? { if case .leaf(let id, _, _) = self { return id }; return nil }
    var asSplit: Split? { if case .split(let s) = self { return s }; return nil }
}

/// İç panel: sayfanın iç alanına göre oranlarla başka panellerin üstünde durur (beyaz dolgulu).
struct PanelInset: Equatable {
    var id: Int
    var l: Float, t: Float, r: Float, b: Float
    var oval: Bool = false
    var round: Float = 0
    var after: Int = -1
}

/// Hesaplanmış panel: kimlik, okuma sırası (1'den), köşe noktaları (x0, y0, x1, y1…; kapalı).
struct PanelShape {
    let id: Int
    let number: Int
    let points: [Float]
    let inset: Bool

    var size: Int { points.count / 2 }
    func x(_ i: Int) -> Float { points[2 * i] }
    func y(_ i: Int) -> Float { points[2 * i + 1] }

    /// Sınırlar: sol, üst, sağ, alt.
    func bounds() -> [Float] {
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for i in 0..<size { l = min(l, x(i)); r = max(r, x(i)); t = min(t, y(i)); b = max(b, y(i)) }
        return [l, t, r, b]
    }

    /// Nokta panelin içinde mi (çift-tek kuralı).
    func contains(_ px: Float, _ py: Float) -> Bool {
        var inside = false
        var j = size - 1
        for i in 0..<size {
            let yi = y(i), yj = y(j)
            if (yi > py) != (yj > py) && px < (x(j) - x(i)) * (py - yi) / (yj - yi) + x(i) { inside.toggle() }
            j = i
        }
        return inside
    }
}

struct PanelPage: Equatable {
    var root: PanelNode?
    /// Sayfa kenar boşluğu (kısa kenarın oranı).
    var margin: Float = PanelPage.DEFAULT_MARGIN
    /// Paneller arası aralık (kısa kenarın oranı).
    var gutter: Float = PanelPage.DEFAULT_GUTTER
    /// Sağdan sola okuma (manga): yan yana paneller sağdan numaralanır.
    var rtl: Bool = false
    var insets: [PanelInset] = []

    init(_ root: PanelNode?, _ margin: Float = PanelPage.DEFAULT_MARGIN, _ gutter: Float = PanelPage.DEFAULT_GUTTER,
         _ rtl: Bool = false, _ insets: [PanelInset] = []) {
        self.root = root; self.margin = margin; self.gutter = gutter; self.rtl = rtl; self.insets = insets
    }

    static let DEFAULT_MARGIN: Float = 0.045
    static let DEFAULT_GUTTER: Float = 0.022
    static let MIN_MARGIN: Float = 0
    static let MAX_MARGIN: Float = 0.12
    static let MIN_GUTTER: Float = 0
    static let MAX_GUTTER: Float = 0.08
    static let MIN_PART: Float = 0.1
    static let MAX_PART: Float = 0.9
    private static let VERSION = "P1"
    private static let ROUND_STEPS = 7

    var isEmpty: Bool { leafIds().isEmpty }

    // MARK: Okuma sırası

    /// Panellerin kimlikleri okuma sırasıyla (iç paneller `after` panelinden sonra).
    func leafIds() -> [Int] {
        var tree: [Int] = []
        if let root { collect(root, &tree) }
        if insets.isEmpty { return tree }
        var out: [Int] = []
        for id in tree {
            out.append(id)
            for n in insets where n.after == id { out.append(n.id) }
        }
        for n in insets where !tree.contains(n.after) { out.append(n.id) }
        return out
    }

    private func collect(_ n: PanelNode, _ out: inout [Int]) {
        switch n {
        case .leaf(let id, _, _): out.append(id)
        case .space: break
        case .split(let s): for c in (!s.rows && rtl ? s.children.reversed() : s.children) { collect(c, &out) }
        }
    }

    func isInset(_ id: Int) -> Bool { insets.contains { $0.id == id } }

    func nextId() -> Int { (leafIds().max() ?? -1) + 1 }

    // MARK: Geometri

    /// [w]×[h] sayfada panellerin köşeleri (okuma sırasıyla).
    func shapes(_ w: Float, _ h: Float) -> [PanelShape] {
        let s = min(w, h)
        let m = min(margin * s, min(w, h) * 0.3)
        let l = m, t = m, r = w - m, b = h - m
        var found: [Int: [Float]] = [:]
        if let root { layout(root, Self.rect(l, t, r, b), l, t, r, b, gutter * s, s, &found) }
        let iw = r - l, ih = b - t
        for inset in insets {
            let x0 = l + inset.l * iw, x1 = l + inset.r * iw
            let y0 = t + inset.t * ih, y1 = t + inset.b * ih
            found[inset.id] = inset.oval ? Self.ellipse(x0, y0, x1, y1) : Self.decorate(Self.rect(x0, y0, x1, y1), inset.round * s, 0)
        }
        let insetIds = Set(insets.map { $0.id })
        var out: [PanelShape] = []
        for (i, id) in leafIds().enumerated() {
            if let pts = found[id] { out.append(PanelShape(id: id, number: i + 1, points: pts, inset: insetIds.contains(id))) }
        }
        return out
    }

    private func layout(_ n: PanelNode, _ poly: [Float], _ l: Float, _ t: Float, _ r: Float, _ b: Float, _ g: Float, _ s: Float,
                        _ out: inout [Int: [Float]]) {
        switch n {
        case .leaf(let id, let round, let bevel):
            if poly.count >= 6 { out[id] = Self.decorate(poly, round * s, bevel * s) }
        case .space: break
        case .split(let sp):
            let count = sp.children.count
            if count == 0 { return }
            let gg = g * sp.gap
            let extent = sp.rows ? b - t : r - l
            let avail = max(1, extent - Float(count - 1) * gg)
            let sum = sp.weights.prefix(count).reduce(0, +)
            let total = sum > 0 ? sum : Float(count)
            var pos = sp.rows ? t : l
            for i in 0..<count {
                let size = avail * ((i < sp.weights.count ? sp.weights[i] : 1) / total)
                var p = poly
                // Önceki çizginin sonrası, sonraki çizginin öncesi (aralığın yarısı kadar içeride)
                if i > 0 { p = Self.clip(p, divider(sp, i - 1, pos - gg / 2, l, t, r, b), gg / 2, after: true) }
                if i < count - 1 { p = Self.clip(p, divider(sp, i, pos + size + gg / 2, l, t, r, b), gg / 2, after: false) }
                if sp.rows { layout(sp.children[i], p, l, pos, r, pos + size, g, s, &out) }
                else { layout(sp.children[i], p, pos, t, pos + size, b, g, s, &out) }
                pos += size + gg
            }
        }
    }

    /// Bölme çizgisi [index]: iki nokta (ax, ay, bx, by); [c] çizginin ortasının konumu.
    private func divider(_ n: PanelNode.Split, _ index: Int, _ c: Float, _ l: Float, _ t: Float, _ r: Float, _ b: Float) -> [Float] {
        let skew = index < n.skews.count ? n.skews[index] : 0
        if n.rows {
            let d = skew * (r - l) / 2
            return [l, c + d, r, c - d]
        }
        let d = skew * (b - t) / 2
        return [c + d, t, c - d, b]
    }

    private static func rect(_ l: Float, _ t: Float, _ r: Float, _ b: Float) -> [Float] { [l, t, r, t, r, b, l, b] }

    /// Dışbükey çokgeni, çizginin bir yanında ve çizgiden [d] uzakta kalacak biçimde kırpar (Sutherland-Hodgman).
    private static func clip(_ poly: [Float], _ line: [Float], _ d: Float, after: Bool) -> [Float] {
        let ax = line[0], ay = line[1]
        var nx = line[3] - ay, ny = -(line[2] - ax)
        let len = kHypot(nx, ny)
        if len < 1e-6 { return poly }
        nx /= len; ny /= len
        // Normal "sonraki" yana baksın (dikey çizgide +x, yatay çizgide +y)
        if abs(nx) >= abs(ny) { if nx < 0 { nx = -nx; ny = -ny } } else if ny < 0 { nx = -nx; ny = -ny }
        if !after { nx = -nx; ny = -ny }
        func f(_ x: Float, _ y: Float) -> Float { (x - ax) * nx + (y - ay) * ny - d }
        let n = poly.count / 2
        var out: [Float] = []
        out.reserveCapacity(poly.count + 4)
        for i in 0..<n {
            let px = poly[2 * i], py = poly[2 * i + 1]
            let j = (i + 1) % n
            let qx = poly[2 * j], qy = poly[2 * j + 1]
            let fp = f(px, py), fq = f(qx, qy)
            if fp >= 0 { out.append(px); out.append(py) }
            if (fp >= 0) != (fq >= 0) {
                let k = fp / (fp - fq)
                out.append(px + (qx - px) * k); out.append(py + (qy - py) * k)
            }
        }
        return out
    }

    /// Köşeleri pahlar ([bevel]) ya da yuvarlar ([round]); ikisi de 0 ise aynı çokgen.
    private static func decorate(_ poly: [Float], _ round: Float, _ bevel: Float) -> [Float] {
        if round <= 0.5 && bevel <= 0.5 { return poly }
        let n = poly.count / 2
        var out: [Float] = []
        for i in 0..<n {
            let vx = poly[2 * i], vy = poly[2 * i + 1]
            let p = (i + n - 1) % n, q = (i + 1) % n
            let ux = poly[2 * p], uy = poly[2 * p + 1]
            let wx = poly[2 * q], wy = poly[2 * q + 1]
            let c = round > 0.5 ? round : bevel
            let k1 = min(c / max(1e-3, kHypot(ux - vx, uy - vy)), 0.45)
            let k2 = min(c / max(1e-3, kHypot(wx - vx, wy - vy)), 0.45)
            let ax = vx + (ux - vx) * k1, ay = vy + (uy - vy) * k1
            let bx = vx + (wx - vx) * k2, by = vy + (wy - vy) * k2
            if round > 0.5 {
                for step in 0..<ROUND_STEPS {
                    let tt = Float(step) / (Float(ROUND_STEPS) - 1), mt = 1 - tt
                    out.append(mt * mt * ax + 2 * mt * tt * vx + tt * tt * bx)
                    out.append(mt * mt * ay + 2 * mt * tt * vy + tt * tt * by)
                }
            } else {
                out.append(ax); out.append(ay); out.append(bx); out.append(by)
            }
        }
        return out
    }

    private static func ellipse(_ l: Float, _ t: Float, _ r: Float, _ b: Float) -> [Float] {
        let cx = (l + r) / 2, cy = (t + b) / 2
        let rx = (r - l) / 2, ry = (b - t) / 2
        let n = 48
        var a = [Float](repeating: 0, count: 2 * n)
        for i in 0..<n {
            let ang = 2.0 * Double.pi * Double(i) / Double(n)
            a[2 * i] = cx + rx * Float(cos(ang)); a[2 * i + 1] = cy + ry * Float(sin(ang))
        }
        return a
    }

    // MARK: Metin biçimi

    /// Kısa metinden düzen; bozuksa nil.
    static func decode(_ text: String) -> PanelPage? {
        let parts = text.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        guard parts.count >= 5, parts[0] == VERSION,
              let m = Float(parts[1]), let g = Float(parts[2]) else { return nil }
        let rtl = parts[3] == "1"
        var root: PanelNode? = nil
        if !parts[4].isEmpty {
            var parser = Parser(Array(parts[4]))
            guard let n = parser.node() else { return nil }
            root = n
        }
        var insets: [PanelInset] = []
        if parts.count > 5 {
            for s in parts[5].split(separator: ";") where !s.trimmingCharacters(in: .whitespaces).isEmpty {
                let f = s.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
                guard f.count >= 8, let id = Int(f[0]), let l = Float(f[1]), let t = Float(f[2]), let r = Float(f[3]), let b = Float(f[4]),
                      let rd = Float(f[6]), let after = Int(f[7]) else { return nil }
                insets.append(PanelInset(id: id, l: l, t: t, r: r, b: b, oval: f[5] == "o", round: rd, after: after))
            }
        }
        return PanelPage(root, m.coerceIn(MIN_MARGIN, MAX_MARGIN), g.coerceIn(MIN_GUTTER, MAX_GUTTER), rtl, insets)
    }

    private struct Parser {
        let s: [Character]
        var i = 0
        init(_ s: [Character]) { self.s = s }

        private func at(_ k: Int) -> Character? { k < s.count ? s[k] : nil }

        mutating func node() -> PanelNode? {
            guard let c = at(i) else { return nil }
            i += 1
            switch c {
            case "_": return .space
            case "p":
                guard let id = int() else { return nil }
                var round: Float = 0, bevel: Float = 0
                while let k = at(i), k == "r" || k == "b" {
                    i += 1
                    guard let v = num() else { return nil }
                    if k == "r" { round = v } else { bevel = v }
                }
                return .leaf(id: id, round: round, bevel: bevel)
            case "R", "C":
                var gap: Float = 1
                if at(i) == "g" { i += 1; guard let v = num() else { return nil }; gap = v }
                guard at(i) == "[" else { return nil }
                i += 1
                var children: [PanelNode] = [], weights: [Float] = [], skews: [Float] = []
                while true {
                    guard let w = num() else { return nil }
                    weights.append(w)
                    guard at(i) == ":" else { return nil }
                    i += 1
                    guard let ch = node() else { return nil }
                    children.append(ch)
                    if at(i) == "/" { i += 1; guard let v = num() else { return nil }; skews.append(v) }
                    else if at(i) == "," { skews.append(0) }
                    guard let sep = at(i) else { return nil }
                    i += 1
                    if sep == "," { continue }
                    if sep == "]" { break }
                    return nil
                }
                if children.isEmpty { return nil }
                var k = Array(skews.prefix(children.count - 1))
                while k.count < children.count - 1 { k.append(0) }
                return .split(PanelNode.Split(c == "R", children, weights, k, gap))
            default: return nil
            }
        }

        mutating func int() -> Int? {
            let start = i
            while let c = at(i), c.isASCII && (c.isNumber || c == "-") { i += 1 }
            return Int(String(s[start..<i]))
        }

        mutating func num() -> Float? {
            let start = i
            while let c = at(i), c.isASCII && (c.isNumber || c == "." || c == "-" || c == "E" || c == "e") { i += 1 }
            return Float(String(s[start..<i]))
        }
    }

    /// Sayının en kısa, geri okunduğunda aynı değeri veren yazımı (tam sayılar ondalıksız; Kotlin `Float.toString`).
    static func fmt(_ v: Float) -> String {
        if v == Float(v.toInt()) { return String(v.toInt()) }
        let a = abs(v)
        if a >= 1e-3 && a < 1e7 { return "\(v)" }
        // Kotlin bilimsel yazımı: 1.0E-4
        let parts = String(format: "%g", v).split(separator: "e")
        guard parts.count == 2 else { return "\(v)" }
        var mant = String("\(Float(String(parts[0])) ?? v)")
        if !mant.contains(".") { mant += ".0" }
        return mant + "E" + String(Int(parts[1]) ?? 0)
    }

    /// Kısa metin biçimi: "P1|kenar|aralık|rtl|ağaç|iç paneller".
    func encode() -> String {
        var sb = Self.VERSION + "|" + Self.fmt(margin) + "|" + Self.fmt(gutter) + "|" + (rtl ? "1" : "0") + "|"
        if let root { write(root, &sb) }
        sb += "|"
        for (k, n) in insets.enumerated() {
            if k > 0 { sb += ";" }
            sb += "\(n.id):\(Self.fmt(n.l)):\(Self.fmt(n.t)):\(Self.fmt(n.r)):\(Self.fmt(n.b)):\(n.oval ? "o" : "q"):\(Self.fmt(n.round)):\(n.after)"
        }
        return sb
    }

    private func write(_ n: PanelNode, _ sb: inout String) {
        switch n {
        case .leaf(let id, let round, let bevel):
            sb += "p\(id)"
            if round > 0 { sb += "r" + Self.fmt(round) }
            if bevel > 0 { sb += "b" + Self.fmt(bevel) }
        case .space: sb += "_"
        case .split(let s):
            sb += s.rows ? "R" : "C"
            if s.gap != 1 { sb += "g" + Self.fmt(s.gap) }
            sb += "["
            for (i, c) in s.children.enumerated() {
                if i > 0 { sb += "," }
                sb += Self.fmt(i < s.weights.count ? s.weights[i] : 1) + ":"
                write(c, &sb)
                let skew = i < s.skews.count ? s.skews[i] : 0
                if i < s.children.count - 1 && skew != 0 { sb += "/" + Self.fmt(skew) }
            }
            sb += "]"
        }
    }

    // MARK: Düzenleme

    /// Yapraktan köke yol: her adımda (bölme, çocuk sırası).
    private func path(_ id: Int) -> [(PanelNode.Split, Int)]? {
        var out: [(PanelNode.Split, Int)] = []
        func walk(_ n: PanelNode) -> Bool {
            switch n {
            case .leaf(let lid, _, _): return lid == id
            case .space: return false
            case .split(let s):
                for i in s.children.indices {
                    out.append((s, i))
                    if walk(s.children[i]) { return true }
                    out.removeLast()
                }
                return false
            }
        }
        guard let r = root else { return nil }
        return walk(r) ? out : nil
    }

    /// Ağaçta [path] üzerindeki düğümü [f] ile değiştirir (yol boşsa kök).
    private func replaceAt(_ path: [Int], _ f: (PanelNode) -> PanelNode?) -> PanelNode? {
        func go(_ n: PanelNode, _ depth: Int) -> PanelNode? {
            if depth == path.count { return f(n) }
            guard var split = n.asSplit else { return n }
            let k = path[depth]
            guard let child = go(split.children[k], depth + 1) else { return removeChild(split, k) }
            split.children[k] = child
            return .split(split)
        }
        guard let r = root else { return nil }
        return go(r, 0)
    }

    /// Bölmeden bir çocuğu çıkarır; kalan tek çocuksa onu, panel kalmadıysa nil döndürür.
    private func removeChild(_ split: PanelNode.Split, _ k: Int) -> PanelNode? {
        var s = split
        s.children.remove(at: k)
        if k < s.weights.count { s.weights.remove(at: k) }
        if !s.skews.isEmpty { s.skews.remove(at: k > 0 ? k - 1 : 0) }
        if !s.children.contains(where: { hasLeaf($0) }) { return nil }
        if s.children.count == 1 { return s.children[0] }
        return .split(s)
    }

    private func hasLeaf(_ n: PanelNode) -> Bool {
        switch n {
        case .leaf: return true
        case .space: return false
        case .split(let s): return s.children.contains { hasLeaf($0) }
        }
    }

    private func leafOf(_ id: Int) -> PanelNode? {
        guard let p = path(id) else { return nil }
        let node: PanelNode? = p.isEmpty ? root : p.last.map { $0.0.children[$0.1] }
        if let node, case .leaf = node { return node }
        return nil
    }

    func withMargin(_ v: Float) -> PanelPage { var c = self; c.margin = v.coerceIn(Self.MIN_MARGIN, Self.MAX_MARGIN); return c }
    func withGutter(_ v: Float) -> PanelPage { var c = self; c.gutter = v.coerceIn(Self.MIN_GUTTER, Self.MAX_GUTTER); return c }

    /// Panelin genişliği: yan yana olduğu bölmedeki payı (iç panelde sayfanın iç genişliğine oranı); yoksa nil.
    func widthOf(_ id: Int) -> Float? { partOf(id, rows: false) }
    /// Panelin yüksekliği: üst üste olduğu bölmedeki payı; yoksa nil.
    func heightOf(_ id: Int) -> Float? { partOf(id, rows: true) }

    private func partOf(_ id: Int, rows: Bool) -> Float? {
        if let n = insets.first(where: { $0.id == id }) { return rows ? n.b - n.t : n.r - n.l }
        guard let p = path(id), let (split, k) = p.last(where: { $0.0.rows == rows && $0.0.children.count > 1 }) else { return nil }
        let total = split.weights.reduce(0, +)
        return total > 0 ? split.weights[k] / total : nil
    }

    func setWidth(_ id: Int, _ part: Float) -> PanelPage { setPart(id, rows: false, part) }
    func setHeight(_ id: Int, _ part: Float) -> PanelPage { setPart(id, rows: true, part) }

    private func setPart(_ id: Int, rows: Bool, _ part0: Float) -> PanelPage {
        let part = part0.coerceIn(Self.MIN_PART, Self.MAX_PART)
        if let n = insets.first(where: { $0.id == id }) {
            let half = part / 2
            var u = n
            if rows {
                let c = ((n.t + n.b) / 2).coerceIn(half, 1 - half)
                u.t = c - half; u.b = c + half
            } else {
                let c = ((n.l + n.r) / 2).coerceIn(half, 1 - half)
                u.l = c - half; u.r = c + half
            }
            var copy = self
            copy.insets = insets.map { $0.id == id ? u : $0 }
            return copy
        }
        guard let p = path(id), let at = p.lastIndex(where: { $0.0.rows == rows && $0.0.children.count > 1 }) else { return self }
        let (split, k) = p[at]
        let others = split.weights.enumerated().filter { $0.offset != k }.map { $0.element }.reduce(0, +)
        var weights = split.weights
        weights[k] = others * part / (1 - part)
        var copy = self
        copy.root = replaceAt(p.prefix(at).map { $0.1 }) { node in
            guard var s = node.asSplit else { return node }
            s.weights = weights
            return .split(s)
        }
        return copy
    }

    /// Paneli okuma sırasında bir öne ([forward] false) ya da arkaya taşır. Taşınamıyorsa nil.
    func move(_ id: Int, forward: Bool) -> PanelPage? {
        if isInset(id) { return nil }
        guard let p = path(id) else { return nil }
        if let (split, k) = p.last {
            let step = (forward ? 1 : -1) * (!split.rows && rtl ? -1 : 1)
            let j = k + step
            if split.children.indices.contains(j) && split.children[j].asSplit == nil {
                var children = split.children; children.swapAt(k, j)
                var weights = split.weights; weights.swapAt(k, j)
                var copy = self
                copy.root = replaceAt(p.dropLast().map { $0.1 }) { node in
                    guard var s = node.asSplit else { return node }
                    s.children = children; s.weights = weights
                    return .split(s)
                }
                return copy
            }
        }
        // Okuma sırasındaki komşu panelle yer değiştirme (yerler aynı kalır, paneller değişir)
        let order = leafIds().filter { !isInset($0) }
        guard let i = order.firstIndex(of: id) else { return nil }
        let oi = forward ? i + 1 : i - 1
        guard order.indices.contains(oi) else { return nil }
        let other = order[oi]
        guard let a = leafOf(id), let b = leafOf(other) else { return nil }
        let pa = p.map { $0.1 }
        guard let pb = path(other)?.map({ $0.1 }) else { return nil }
        var first = self
        first.root = replaceAt(pa) { _ in b }
        var second = first
        second.root = first.replaceAt(pb) { _ in a }
        return second
    }

    /// Panelin yanına ([rows] false) ya da altına yeni panel ekler. İç panel bölünemez (nil).
    func split(_ id: Int, rows: Bool) -> (PanelPage, Int)? {
        if isInset(id) { return nil }
        guard let p = path(id), let leaf = leafOf(id), case .leaf(_, let round, let bevel) = leaf else { return nil }
        let newId = nextId()
        let fresh = PanelNode.leaf(id: newId, round: round, bevel: bevel)
        let before = !rows && rtl
        if let (split, k) = p.last, split.rows == rows {
            let at = before ? k : k + 1
            let half = split.weights[k] / 2
            var s = split
            s.children.insert(fresh, at: at)
            s.weights[k] = half; s.weights.insert(half, at: at)
            var skews = split.skews
            skews.insert(0, at: (before ? at : at - 1).coerceIn(0, skews.count))
            s.skews = Array(skews.prefix(s.children.count - 1))
            var copy = self
            copy.root = replaceAt(p.dropLast().map { $0.1 }) { _ in .split(s) }
            return (copy, newId)
        }
        let pair = before ? [fresh, leaf] : [leaf, fresh]
        let node = PanelNode.split(PanelNode.Split(rows, pair, [1, 1], [0]))
        var copy = self
        copy.root = replaceAt(p.map { $0.1 }) { _ in node }
        return (copy, newId)
    }

    /// Sayfanın sonuna (altına) yeni bir panel satırı ekler.
    func append() -> (PanelPage, Int) {
        let newId = nextId()
        var round: Float = 0, bevel: Float = 0
        if let lastId = leafIds().filter({ !isInset($0) }).last, let l = leafOf(lastId), case .leaf(_, let r, let b) = l { round = r; bevel = b }
        let fresh = PanelNode.leaf(id: newId, round: round, bevel: bevel)
        var copy = self
        guard let r = root else { copy.root = fresh; return (copy, newId) }
        if var s = r.asSplit, s.rows {
            let avg = s.weights.isEmpty ? Float.nan : Float(s.weights.map(Double.init).reduce(0, +) / Double(s.weights.count))
            s.children.append(fresh); s.weights.append(avg); s.skews.append(0)
            copy.root = .split(s)
        } else {
            copy.root = .split(PanelNode.Split(true, [r, fresh], [2, 1], [0]))
        }
        return (copy, newId)
    }

    /// Paneli siler; komşuları boşalan yeri doldurur. Son panel silinince düzen boşalır.
    func remove(_ id: Int) -> PanelPage {
        var copy = self
        if isInset(id) { copy.insets = insets.filter { $0.id != id }; return copy }
        guard let p = path(id) else { return self }
        let order = leafIds()
        var prev = -1
        if let i = order.firstIndex(of: id), i > 0, !isInset(order[i - 1]) { prev = order[i - 1] }
        copy.root = p.isEmpty ? nil : replaceAt(p.map { $0.1 }) { _ in nil }
        copy.insets = insets.map { var n = $0; if n.after == id { n.after = prev }; return n }
        return copy
    }
}
