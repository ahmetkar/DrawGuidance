import Foundation

extension DiagramNode {
    /// Kotlin `copy(x = …, y = …, w = …, h = …, box = …)` karşılığı.
    func at(_ x: Float, _ y: Float, _ w: Float, _ h: Float, box: DiagramBox? = nil) -> DiagramNode {
        var n = self
        n.x = x; n.y = y; n.w = w; n.h = h
        if let box { n.box = box }
        return n
    }
}

extension DiagramCatalog {
    // MARK: - değerler

    /// Yazının adı ve sondaki sayıları; [signed] işaretli (artış/azalış), [computed] "=" (hesaplanan toplam).
    struct Item { let label: String; let values: [Float]; let signed: Bool; let computed: Bool }

    private static func tokens(_ text: String) -> [String] {
        text.replacingOccurrences(of: "\n", with: " ").split(whereSeparator: { $0 == " " || $0 == "\t" || $0 == "\r" }).map(String.init)
    }

    private static func isDigits(_ s: Substring) -> Bool { !s.isEmpty && s.allSatisfy { $0.isASCII && $0.isNumber } }

    /// `\d+(?:[.,]\d+)?` → değer.
    private static func number(_ s: Substring) -> Float? {
        if let i = s.firstIndex(where: { $0 == "." || $0 == "," }) {
            let a = s[..<i], b = s[s.index(after: i)...]
            guard isDigits(a), isDigits(b) else { return nil }
            return Float(String(a) + "." + String(b))
        }
        return isDigits(s) ? Float(String(s)) : nil
    }

    /// `^([+\-−]?)(\d+(?:[.,]\d+)?)(%?)$` → (işaret, değer).
    private static func numberToken(_ t: String) -> (String, Float)? {
        var s = Substring(t)
        var sign = ""
        if let f = s.first, f == "+" || f == "-" || f == "−" { sign = String(f); s = s.dropFirst() }
        if s.last == "%" { s = s.dropLast() }
        guard let v = number(s) else { return nil }
        return (sign, v)
    }

    /// `^(\d+(?:[.,]\d+)?)[-–](\d+(?:[.,]\d+)?)$`.
    private static func rangeToken(_ t: String) -> (Float, Float)? {
        let s = Substring(t)
        for (i, c) in zip(s.indices, s) where c == "-" || c == "–" {
            if let a = number(s[..<i]), let b = number(s[s.index(after: i)...]) { return (a, b) }
        }
        return nil
    }

    static func parse(_ text: String) -> Item {
        let toks = tokens(text)
        var values: [Float] = []
        var signed = false
        var computed = false
        var end = toks.count
        while end > 0 {
            let t = toks[end - 1]
            if t == "=" { computed = true; end -= 1; continue }
            if let r = rangeToken(t) { values.insert(r.1, at: 0); values.insert(r.0, at: 0); end -= 1; continue }
            guard let tok = numberToken(t) else { break }
            let (sign, v) = tok
            if !sign.isEmpty { signed = true }
            values.insert(sign == "-" || sign == "−" ? -v : v, at: 0)
            end -= 1
        }
        var label = toks.prefix(end).joined(separator: " ")
        if label.hasPrefix("=") { label.removeFirst() }
        return Item(label: label.trimmingCharacters(in: .whitespaces), values: values, signed: signed, computed: computed)
    }

    /// Grafiklerde gösterilen ad: yazının sonundaki değerler atılır.
    static func label(_ kind: DiagramKind, _ text: String) -> String {
        var toks = tokens(text)
        let strip = kind == .SCATTER ? 2 : kind == .RADAR ? Int.max : 1
        var n = 0
        while let t = toks.last, n < strip {
            if t == "=" || numberToken(t) != nil || rangeToken(t) != nil { toks.removeLast(); n += 1 } else { break }
        }
        if kind == .WATERFALL && toks.last == "=" { toks.removeLast() }
        return toks.joined(separator: " ")
    }

    /// Başlık satırı ("[AŞAMA 1]") dışındaki ad.
    static func nameOf(_ text: String) -> String {
        text.components(separatedBy: "\n").first { !($0.hasPrefix("[") && $0.hasSuffix("]")) } ?? text
    }

    // MARK: - yerleşim

    static func layout(_ kind: DiagramKind, _ nodes: [DiagramNode]) -> [DiagramNode] {
        if nodes.isEmpty { return nodes }
        switch kind {
        case .BAR: return layoutBars(nodes)
        case .WATERFALL: return layoutWaterfall(nodes)
        case .LINE: return layoutLine(nodes)
        case .SCATTER: return layoutScatter(nodes)
        case .RADAR, .POLAR: return layoutRing(nodes, 0.44, 0.22, 0.07)
        case .TREEMAP: return layoutTreemap(nodes)
        case .HEATMAP, .DP_SECURITY: return layoutGrid(nodes, kind == .DP_SECURITY)
        case .SANKEY: return layoutSankey(nodes)
        case .GANTT: return layoutGantt(nodes)
        case .JOURNEY: return layoutJourney(nodes)
        case .LAYERS:
            return nodes.enumerated().map { i, node in
                let h = min(0.92 / Float(nodes.count), 0.2)
                let top = (1 - h * Float(nodes.count)) / 2
                return node.at(0.12, top + Float(i) * h, 0.86, h, box: .RECT)
            }
        case .LOOP: return layoutLoop(nodes)
        case .SEQUENCE:
            let m = Float(nodes.count)
            return nodes.enumerated().map { i, node in
                let w = min(0.22, 0.9 / m)
                let slot = 0.96 / m
                return node.at(0.02 + Float(i) * slot + (slot - w) / 2, 0.02, w, 0.1, box: .RECT)
            }
        case .EXPLODED:
            let gap = 0.9 / Float(nodes.count)
            return nodes.enumerated().map { i, node in node.at(0.04, 0.04 + Float(i) * gap, 0.56, min(0.26, gap * 1.1), box: .SLAB) }
        default: return placeUnplaced(nodes)
        }
    }

    private static func isContainer(_ b: DiagramBox) -> Bool { b == .GROUP || b == .LANE || b == .COLUMN }

    /// Henüz yeri olmayan (yeni eklenen) adımlar önceki adımın yanına; diğerleri yerinde kalır.
    private static func placeUnplaced(_ nodes: [DiagramNode]) -> [DiagramNode] {
        var out: [DiagramNode] = []
        for node in nodes {
            let unplaced = (node.x == 0.5 && node.y == 0.5 && node.w == 0.3 && node.h == 0.12) || (node.w <= 0 || node.h <= 0)
            if !unplaced { out.append(node); continue }
            let ref = out.last { !isContainer($0.box) }
            let w = ref.map { min(max($0.w, 0.12), 0.3) } ?? 0.24
            let h = ref.map { min(max($0.h, 0.06), 0.2) } ?? 0.12
            let candidates: [(Float, Float)] = ref.map { r in
                [(r.x, r.y + r.h + 0.04), (r.x + r.w + 0.04, r.y), (r.x - w - 0.04, r.y), (r.x, r.y - h - 0.04)]
            } ?? [(0.38, 0.44)]
            func free(_ x: Float, _ y: Float) -> Bool {
                x >= -0.2 && y >= -0.2 && !out.contains { o in
                    !isContainer(o.box) && x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y
                }
            }
            let (x, y) = candidates.first { free($0.0, $0.1) } ?? candidates[0]
            out.append(node.at(x, y, w, h, box: (node.box == .GROUP || node.box == .LANE) ? .ROUNDED : node.box))
        }
        return out
    }

    /// Grafik alanı (eksen yazıları dışında).
    static let PLOT_L: Float = 0.1
    static let PLOT_R: Float = 0.98
    static let PLOT_T: Float = 0.06
    static let PLOT_B: Float = 0.86

    private static func niceMax(_ v: Float) -> Float {
        if v <= 0 { return 1 }
        let exp = Float(Foundation.pow(10.0, Foundation.log10(Double(v)).rounded(.down)))
        let f = v / exp
        let nice: Float = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10
        return nice * exp
    }

    private static func lastValue(_ n: DiagramNode) -> Float? { parse(n.text).values.last }

    /// Çubuk grafiğinde ölçeğin üst değeri.
    static func barScale(_ nodes: [DiagramNode]) -> Float { niceMax(nodes.map { lastValue($0) ?? 0 }.max() ?? 1) }

    private static func layoutBars(_ nodes: [DiagramNode]) -> [DiagramNode] {
        let mx = barScale(nodes)
        let pitch = (PLOT_R - PLOT_L) / Float(nodes.count)
        let bw = pitch * 0.64
        return nodes.enumerated().map { i, node in
            let v = max(lastValue(node) ?? 0, 0)
            let h = (PLOT_B - PLOT_T) * (v / mx)
            return node.at(PLOT_L + Float(i) * pitch + (pitch - bw) / 2, PLOT_B - h, bw, max(h, 0.004), box: .BAR)
        }
    }

    /// Şelalede her çubuğun alt ve üst düzeyi.
    static func waterfallLevels(_ nodes: [DiagramNode]) -> [(Float, Float)] {
        var running: Float = 0
        return nodes.map { node in
            let it = parse(node.text)
            guard let v = it.values.last, !it.computed else { return (0, running) }
            if it.signed { let before = running; running += v; return (before, running) }
            running = v
            return (0, v)
        }
    }

    static func waterfallScale(_ nodes: [DiagramNode]) -> Float { niceMax(waterfallLevels(nodes).map { max($0.0, $0.1) }.max() ?? 1) }

    private static func layoutWaterfall(_ nodes: [DiagramNode]) -> [DiagramNode] {
        let levels = waterfallLevels(nodes)
        let mx = waterfallScale(nodes)
        let pitch = (PLOT_R - PLOT_L) / Float(nodes.count)
        let bw = pitch * 0.66
        func y(_ v: Float) -> Float { PLOT_B - (PLOT_B - PLOT_T) * (max(v, 0) / mx) }
        return nodes.enumerated().map { i, node in
            let (a, b) = levels[i]
            let top = y(max(a, b)), bottom = y(min(a, b))
            return node.at(PLOT_L + Float(i) * pitch + (pitch - bw) / 2, top, bw, max(bottom - top, 0.004), box: .BAR)
        }
    }

    static func lineScale(_ nodes: [DiagramNode]) -> Float { niceMax(nodes.map { lastValue($0) ?? 0 }.max() ?? 1) }

    private static func layoutLine(_ nodes: [DiagramNode]) -> [DiagramNode] {
        let mx = lineScale(nodes)
        let m = nodes.count
        let d: Float = 0.04
        return nodes.enumerated().map { i, node in
            let v = max(lastValue(node) ?? 0, 0)
            let x = m == 1 ? (PLOT_L + PLOT_R) / 2 : PLOT_L + 0.03 + (PLOT_R - PLOT_L - 0.06) * Float(i) / Float(m - 1)
            let y = PLOT_B - (PLOT_B - PLOT_T) * (v / mx)
            return node.at(x - d / 2, y - d / 2, d, d, box: .DOT)
        }
    }

    private static func secondLast(_ v: [Float], _ def: Float) -> Float { v.count >= 2 ? v[v.count - 2] : def }

    static func scatterScale(_ nodes: [DiagramNode]) -> (Float, Float) {
        let items = nodes.map { parse($0.text).values }
        return (niceMax(items.map { secondLast($0, 0) }.max() ?? 1), niceMax(items.map { $0.last ?? 0 }.max() ?? 1))
    }

    private static func layoutScatter(_ nodes: [DiagramNode]) -> [DiagramNode] {
        let (mx, my) = scatterScale(nodes)
        let d: Float = 0.035
        return nodes.map { node in
            let v = parse(node.text).values
            let xv = secondLast(v, 0), yv = v.last ?? 0
            let x = PLOT_L + (PLOT_R - PLOT_L) * min(max(xv / mx, 0), 1)
            let y = PLOT_B - (PLOT_B - PLOT_T) * min(max(yv / my, 0), 1)
            return node.at(x - d / 2, y - d / 2, d, d, box: .DOT)
        }
    }

    /// Radar/kutupsal: ad kutuları çemberin dışında (saat 12'den saat yönünde).
    private static func layoutRing(_ nodes: [DiagramNode], _ r: Float, _ w: Float, _ h: Float) -> [DiagramNode] {
        let m = Double(nodes.count)
        return nodes.enumerated().map { i, node in
            let a = -Double.pi / 2 + 2 * Double.pi * Double(i) / m
            let cx = 0.5 + r * Float(cos(a)), cy = 0.5 + r * Float(sin(a))
            return node.at(min(max(cx - w / 2, 0), 1 - w), min(max(cy - h / 2, 0), 1 - h), w, h, box: .LABEL)
        }
    }

    /// Ağaç haritası: karelere yakın (squarified) yerleşim, büyükten küçüğe.
    private static func layoutTreemap(_ nodes: [DiagramNode]) -> [DiagramNode] {
        let vals = nodes.map { max(lastValue($0) ?? 1, 0.01) }
        let total = vals.reduce(0, +)
        // Kotlin sortedByDescending kararlıdır
        let order = vals.indices.sorted { vals[$0] != vals[$1] ? vals[$0] > vals[$1] : $0 < $1 }
        var rects = [[Float]?](repeating: nil, count: nodes.count)
        var x: Float = 0.02, y: Float = 0.02, w: Float = 0.96, h: Float = 0.96
        let area = w * h
        let gap: Float = 0.008
        func a(_ k: Int) -> Float { vals[k] / total * area }
        var i = 0
        while i < order.count {
            let short = min(w, h)
            var row: [Int] = []
            var best = Float.greatestFiniteMagnitude
            while i < order.count {
                let candidate = row + [order[i]]
                let sum = Float(candidate.reduce(0.0) { $0 + Double(a($1)) })
                let side = sum / short
                let worst = candidate.map { k -> Float in let l = a(k) / side; return max(l / side, side / l) }.max()!
                if worst > best && !row.isEmpty { break }
                best = worst
                row.append(order[i]); i += 1
            }
            let sum = Float(row.reduce(0.0) { $0 + Double(a($1)) })
            let thick = sum / short
            var off: Float = 0
            for k in row {
                let l = a(k) / thick
                rects[k] = w >= h ? [x, y + off, thick, l] : [x + off, y, l, thick]
                off += l
            }
            if w >= h { x += thick; w -= thick } else { y += thick; h -= thick }
        }
        return nodes.enumerated().map { k, node in
            let r = rects[k] ?? [0, 0, 0.1, 0.1]
            return node.at(r[0] + gap / 2, r[1] + gap / 2, max(r[2] - gap, 0.005), max(r[3] - gap, 0.005), box: .RECT)
        }
    }

    /// Isı haritası / erişim matrisi: sütun başlıkları, satır adları ve hücreler.
    private static func layoutGrid(_ nodes: [DiagramNode], _ matrix: Bool) -> [DiagramNode] {
        let cols = nodes.filter { $0.role == "col" }
        let rows = nodes.filter { $0.role == "row" }
        let cellCount = nodes.filter { $0.role != "col" && $0.role != "row" }.count
        let nc = max(1, cols.count)
        let nr = max(max(1, rows.count), (cellCount + nc - 1) / nc)
        let left: Float = matrix ? 0.24 : 0.16
        let top: Float = matrix ? 0.12 : 0.08
        let cw = (0.99 - left) / Float(nc), rh = (0.98 - top) / Float(nr)
        var cell = 0
        return nodes.map { node in
            switch node.role {
            case "col":
                let i = Float(cols.firstIndex(of: node)!)
                return node.at(left + i * cw + 0.004, 0, cw - 0.008, top - 0.016, box: .LABEL)
            case "row":
                let i = Float(rows.firstIndex(of: node)!)
                return node.at(0, top + i * rh + 0.004, left - 0.016, rh - 0.008, box: .LABEL)
            default:
                let k = cell
                cell += 1
                let r = Float(k / nc), c = Float(k % nc)
                return node.at(left + c * cw + 0.004, top + r * rh + 0.004, cw - 0.008, rh - 0.008, box: .CELL)
            }
        }
    }

    /// Sankey: üç sütun ("c0".."c2"), düğüm çubuğu boyu değerle orantılı.
    private static func layoutSankey(_ nodes: [DiagramNode]) -> [DiagramNode] {
        let columns = (0...2).map { c in nodes.filter { ($0.role ?? "c0") == "c\(c)" } }
        let totals = columns.map { col in Float(col.reduce(0.0) { $0 + Double(lastValue($1) ?? 0) }) }
        let maxTotal = max(totals.max() ?? 1, 1e-3)
        let gap: Float = 0.04
        let span: Float = 0.86
        let xs: [Float] = [0.24, 0.49, 0.74]
        var placed: [String: DiagramNode] = [:]
        for (c, col) in columns.enumerated() {
            let gaps = gap * Float(max(0, col.count - 1))
            let k = (span - gaps) / maxTotal
            let colH = totals[c] * k + gaps
            var y = 0.07 + (span - colH) / 2
            for node in col {
                let h = max((lastValue(node) ?? 0) * k, 0.006)
                placed[node.id] = node.at(xs[c] - 0.01, y, 0.02, h, box: .BAR)
                y += h + gap
            }
        }
        return nodes.map { placed[$0.id] ?? $0 }
    }

    /// Gantt: hafta aralığı yazıdan ("Tasarım 2-5").
    static func ganttWeeks(_ nodes: [DiagramNode]) -> Int {
        guard let m = nodes.map({ ktInt(lastValue($0) ?? 1) }).max() else { return 12 }
        return min(max(m, 1), 52)
    }

    static let GANTT_L: Float = 0.24

    private static func layoutGantt(_ nodes: [DiagramNode]) -> [DiagramNode] {
        let weeks = Float(ganttWeeks(nodes))
        let pitch = (0.98 - GANTT_L) / weeks
        let rowH = min(0.12, 0.86 / Float(max(1, nodes.count)))
        return nodes.enumerated().map { i, node in
            let v = parse(node.text).values
            let a = min(max(secondLast(v, 1), 1), weeks)
            let b = min(max(v.last ?? a, a), weeks)
            return node.at(GANTT_L + (a - 1) * pitch, 0.12 + Float(i) * rowH + rowH * 0.2, max((b - a + 1) * pitch, pitch * 0.5), rowH * 0.6, box: .BAR)
        }
    }

    /// Yolculuk: aşama başlıkları, duygu noktaları (1..5), eylemler ve sıkıntı etiketi.
    private static func layoutJourney(_ nodes: [DiagramNode]) -> [DiagramNode] {
        let stages = nodes.filter { $0.role == "stage" }
        let moods = nodes.filter { $0.role == "mood" }
        let actions = nodes.filter { $0.role == "action" || $0.role == nil }
        let m = max(1, max(stages.count, max(moods.count, actions.count)))
        let left: Float = 0.1
        let colW = (0.99 - left) / Float(m)
        func cx(_ i: Int) -> Float { left + colW * Float(i) + colW / 2 }
        // Kotlin minByOrNull: ilk en küçük
        var trough = 0
        if !moods.isEmpty {
            var best = Float.greatestFiniteMagnitude
            for (i, md) in moods.enumerated() { let v = lastValue(md) ?? 3; if v < best { best = v; trough = i } }
        }
        return nodes.map { node in
            switch node.role {
            case "stage":
                let i = stages.firstIndex(of: node)!
                return node.at(cx(i) - colW * 0.46, 0, colW * 0.92, 0.12, box: .LABEL)
            case "mood":
                let i = moods.firstIndex(of: node)!
                let v = min(max(lastValue(node) ?? 3, 1), 5)
                let y = 0.18 + (5 - v) / 4 * 0.34
                return node.at(cx(i) - 0.018, y - 0.018, 0.036, 0.036, box: .DOT)
            case "action", nil:
                let i = actions.firstIndex(of: node)!
                return node.at(cx(i) - colW * 0.46, 0.62, colW * 0.92, 0.12, box: .LABEL)
            case "pain":
                return node.at(cx(trough) - colW * 0.46, 0.80, colW * 0.92, 0.1, box: .CARD)
            default:
                return node
            }
        }
    }

    /// Göbekli döngü: istasyonlar çemberde, göbek ortada.
    private static func layoutLoop(_ nodes: [DiagramNode]) -> [DiagramNode] {
        let stations = nodes.filter { $0.role != "hub" }
        let m = Double(max(1, stations.count))
        let w: Float = 0.22, h: Float = 0.13
        return nodes.map { node in
            if node.role == "hub" { return node.at(0.36, 0.42, 0.28, 0.16) }
            let i = Double(stations.firstIndex(of: node)!)
            let a = -Double.pi / 2 + 2 * Double.pi * i / m
            return node.at(0.5 + 0.38 * Float(cos(a)) - w / 2, 0.5 + 0.38 * Float(sin(a)) - h / 2, w, h)
        }
    }

    // MARK: - düzenleme yardımcıları

    /// Okları kendiliğinden eklenmeyen türler.
    static let noEdgeKinds: Set<DiagramKind> = dataKinds.subtracting([.SEQUENCE, .LOOP, .SANKEY])
        .union([.QUADRANT, .KANBAN, .STORY_MAP, .NESTED, .TIMELINE])

    /// Taraf seçilmemiş okların da dik açılı çizildiği türler.
    static func elbow(_ kind: DiagramKind) -> Bool {
        switch kind {
        case .ORG, .TREE, .CONCEPT_MAP, .HUB, .ARCHITECTURE: return true
        default: return isNew(kind) && !straightKinds.contains(kind) && kind != .AXONOMETRIC
        }
    }

    private static let structuralRoles: Set<String> = ["stage", "mood", "action", "row", "col", "c0", "c1", "c2", "activity", "chevron",
                                                       "step", "ledger", "axis", "hub"]

    static func isStructural(_ role: String?) -> Bool { role.map { structuralRoles.contains($0) } ?? false }

    /// Yeni adımın rolü: odaktaki adımın yerleşim rolü.
    static func inheritRole(_ role: String?) -> String? {
        guard let role, structuralRoles.contains(role), role != "hub", role != "axis" else { return nil }
        return role
    }

    /// Nicel türde yeni adımın yazısı: [near] adımının değerleriyle.
    static func newNodeText(_ kind: DiagramKind, _ index: Int, _ near: String?) -> String {
        let base = "Adım \(index)"
        guard isData(kind), let near else { return base }
        let it = parse(near)
        if it.values.isEmpty { return base }
        func f(_ v: Float) -> String { v == v.rounded(.down) ? String(ktInt(v)) : kotlinFloat(v) }
        switch kind {
        case .GANTT:
            let a = secondLast(it.values, 1), b = it.values.last!
            let len = max(b - a, 0)
            return "Yeni iş \(f(b + 1))-\(f(b + 1 + len))"
        case .WATERFALL: return "Kalem +10"
        default: return (["Yeni"] + it.values.map { f($0) }).joined(separator: " ")
        }
    }

    /// Kotlin `Float.toString()` (kısa gösterim; tamsayı olmayan değerler için).
    static func kotlinFloat(_ v: Float) -> String { "\(v)" }

    /// Yeni diyagramın en-boy oranı; eski türler için nil.
    static func aspect(_ kind: DiagramKind) -> Float? {
        switch kind {
        case .KANBAN, .STORY_MAP, .HIGH_LEVEL, .DATA_FLOW, .MEDALLION, .JOURNEY, .SANKEY, .LINE, .GANTT: return 1.6
        case .DP_INTEGRATION, .ARCH_DELTA, .SWIMLANE, .IT_STATE, .DEPLOYMENT, .BAR, .WATERFALL, .HEATMAP, .TREEMAP: return 1.45
        case .ER, .DB_SCHEMA, .SCATTER, .WARDLEY, .DP_SECURITY, .LAYERS, .AXONOMETRIC: return 1.3
        case .UML_CLASS, .DEPENDENCY, .NESTED, .SEQUENCE, .EXPLODED: return 1.15
        case .RADAR, .POLAR, .LOOP, .QUADRANT: return 1
        default: return nil
        }
    }

    /// Sankey şeridi (diyagram birimi 0..1).
    struct Ribbon { let x0: Float, y0: Float, x1: Float, y1: Float, h: Float }

    /// Sankey şeritleri (ok sırasına göre).
    static func ribbons(_ nodes: [DiagramNode], _ edges: [DiagramEdge]) -> [Int: Ribbon] {
        var byId: [String: DiagramNode] = [:]
        for n in nodes { byId[n.id] = n }
        var units: [Float] = []
        for n in nodes { if let v = lastValue(n), v > 0 { units.append(n.h / v) } }
        guard let k = units.min() else { return [:] }
        let thick = edges.map { max(parse($0.label).values.last ?? 0, 0) * k }
        var outOffset: [String: Float] = [:]
        var inOffset: [String: Float] = [:]
        var out: [Int: Ribbon] = [:]
        func ky(_ id: String) -> Float { byId[id]?.y ?? 0 }
        let order = edges.indices.sorted { a, b in
            let (fa, fb) = (ky(edges[a].from), ky(edges[b].from))
            if fa != fb { return fa < fb }
            let (ta, tb) = (ky(edges[a].to), ky(edges[b].to))
            return ta != tb ? ta < tb : a < b
        }
        var y1: [Int: Float] = [:]
        let inOrder = edges.indices.sorted { a, b in
            let (ta, tb) = (ky(edges[a].to), ky(edges[b].to))
            if ta != tb { return ta < tb }
            let (fa, fb) = (ky(edges[a].from), ky(edges[b].from))
            return fa != fb ? fa < fb : a < b
        }
        for i in inOrder {
            guard let b = byId[edges[i].to] else { continue }
            let off = inOffset[b.id] ?? 0
            y1[i] = b.y + off
            inOffset[b.id] = off + thick[i]
        }
        for i in order {
            guard let a = byId[edges[i].from], let b = byId[edges[i].to] else { continue }
            let off = outOffset[a.id] ?? 0
            outOffset[a.id] = off + thick[i]
            out[i] = Ribbon(x0: a.x + a.w, y0: a.y + off, x1: b.x, y1: y1[i] ?? b.y, h: thick[i])
        }
        return out
    }
}
