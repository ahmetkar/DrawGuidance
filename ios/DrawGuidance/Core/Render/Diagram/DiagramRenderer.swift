import CoreGraphics
import Foundation

/// Diyagramların çizimi ve yerleşimi (Android `DiagramRenderer`). Diyagram 0..1 alanında tanımlanır ve elemanın
/// 4 köşesine eşlenir; taşıma, döndürme ve ölçekleme diğer elemanlarla aynı çalışır.
enum DiagramRenderer {
    private static let UNIT: Float = 1000

    // MARK: - varsayılanlar

    static func defaultNodes(_ kind: DiagramKind) -> [DiagramNode] {
        if let t = DiagramCatalog.template(kind) { return t.nodes }
        switch kind {
        case .FLOW: return [node("Başla", .PILL), node("Adım"), node("Karar", .DIAMOND), node("Bitir", .PILL)]
        case .PROCESS: return [node("1. Aşama"), node("2. Aşama"), node("3. Aşama")]
        case .ARCHITECTURE: return [node("İstemci"), node("Sunucu", role: "focal"), node("Veritabanı", .CYLINDER, "store")]
        case .ORG: return [node("Yönetici", role: "hub"), node("Ekip A"), node("Ekip B"), node("Ekip C")]
        case .TIMELINE: return [node("Ocak"), node("Mart"), node("Haziran"), node("Eylül")]
        case .STATE: return [node("Boşta", .ROUNDED), node("Çalışıyor", .ROUNDED), node("Bitti", .ROUNDED)]
        case .RELATION: return [node("A", .CIRCLE), node("B", .CIRCLE)]
        case .COMPARISON: return [node("Seçenek A"), node("Seçenek B"), node("Artı"), node("Eksi")]
        case .CYCLE: return [node("Buharlaşma"), node("Yoğuşma"), node("Yağış"), node("Toplanma")]
        case .MIND_MAP:
            return [node("Ana konu", .PILL, "hub"), node("Tanım"), node("Özellikler"), node("Örnekler"), node("Nedenler"), node("Sonuçlar"), node("Sorular")]
        case .PYRAMID:
            return [node("Tatlılar", .TRIANGLE, "focal"), node("Et ve süt", .TRAPEZOID), node("Sebze ve meyve", .TRAPEZOID), node("Tahıllar", .TRAPEZOID)]
        case .VENN: return [node("A", .CIRCLE), node("B", .CIRCLE), node("C", .CIRCLE)]
        case .MATRIX: return [node("Güçlü yönler", .RECT), node("Zayıf yönler", .RECT), node("Fırsatlar", .RECT), node("Tehditler", .RECT)]
        case .FUNNEL:
            return [node("Gözlem", .TRAPEZOID), node("Soru", .TRAPEZOID), node("Hipotez", .TRAPEZOID), node("Deney", .TRAPEZOID), node("Sonuç", .TRAPEZOID)]
        case .FISHBONE: return [node("Sonuç", .ROUNDED, "focal"), node("İnsan"), node("Yöntem"), node("Malzeme"), node("Ortam")]
        case .CONCEPT_MAP:
            return [node("Canlılar", .PILL, "hub"), node("Bitkiler"), node("Hayvanlar"), node("Mantarlar"), node("Çiçekli"), node("Omurgalı"), node("Şapkalı")]
        case .TREE:
            return [node("Madde", role: "hub"), node("Saf madde"), node("Karışım"), node("Element"), node("Bileşik"), node("Homojen"), node("Heterojen")]
        case .KWL:
            return [node("Biliyorum", .PILL), node("Merak ediyorum", .PILL), node("Öğrendim", .PILL), node(" ", .RECT), node(" ", .RECT), node(" ", .RECT)]
        case .STEPS: return [node("1. Adım"), node("2. Adım"), node("3. Adım"), node("4. Adım", role: "focal")]
        case .HUB: return [node("Neden 1"), node("Neden 2"), node("Neden 3"), node("Sonuç", .ROUNDED, "focal")]
        default: return [node("Adım 1"), node("Adım 2")]
        }
    }

    private static var counter = 0
    private static let counterLock = NSLock()

    private static func node(_ text: String, _ box: DiagramBox = .ROUNDED, _ role: String? = nil) -> DiagramNode {
        counterLock.lock(); let i = counter; counter += 1; counterLock.unlock()
        return DiagramNode(id: "n\(i)", text: text, x: 0, y: 0, w: 0.3, h: 0.12, box: box, role: role)
    }

    private static func node(_ text: String, _ box: DiagramBox = .ROUNDED, role: String?) -> DiagramNode { node(text, box, role) }

    private static func chain(_ nodes: [DiagramNode]) -> [DiagramEdge] {
        nodes.count < 2 ? [] : (0..<(nodes.count - 1)).map { DiagramEdge(from: nodes[$0].id, to: nodes[$0 + 1].id) }
    }

    /// Türüne göre varsayılan oklar.
    static func defaultEdges(_ kind: DiagramKind, _ nodes: [DiagramNode]) -> [DiagramEdge] {
        if let t = DiagramCatalog.template(kind) {
            let ids = Set(nodes.map(\.id))
            return t.edges.filter { ids.contains($0.from) && ids.contains($0.to) }
        }
        switch kind {
        case .ORG: return nodes.dropFirst().map { DiagramEdge(from: nodes[0].id, to: $0.id) }
        case .RELATION, .COMPARISON, .PYRAMID, .VENN, .MATRIX, .FUNNEL, .FISHBONE, .KWL, .TIMELINE: return []
        case .CYCLE:
            return chain(nodes) + (nodes.count > 2 ? [DiagramEdge(from: nodes.last!.id, to: nodes[0].id)] : [])
        case .MIND_MAP: return nodes.dropFirst().map { DiagramEdge(from: nodes[0].id, to: $0.id, arrow: false) }
        case .CONCEPT_MAP, .TREE:
            return treeParents(kind, nodes.count).enumerated().compactMap { i, parent in
                if parent < 0 { return nil }
                return DiagramEdge(from: nodes[parent].id, to: nodes[i].id,
                                   label: kind == .CONCEPT_MAP ? (parent == 0 ? "içerir" : "örnek") : "",
                                   arrow: kind == .CONCEPT_MAP,
                                   fromSide: kind == .TREE ? .BOTTOM : .AUTO, toSide: kind == .TREE ? .TOP : .AUTO)
            }
        case .HUB: return nodes.dropLast().map { DiagramEdge(from: $0.id, to: nodes.last!.id) }
        case .STATE:
            return chain(nodes) + (nodes.count > 2 ? [DiagramEdge(from: nodes.last!.id, to: nodes[0].id, dashed: true)] : [])
        default: return chain(nodes)
        }
    }

    // MARK: - yerleşim

    static func layout(_ kind: DiagramKind, _ nodes: [DiagramNode]) -> [DiagramNode] {
        if nodes.isEmpty { return nodes }
        if DiagramCatalog.isNew(kind) { return DiagramCatalog.layout(kind, nodes) }
        let n = nodes.count
        let nf = Float(n)
        switch kind {
        case .FLOW, .PROCESS, .STATE:
            let vertical = kind == .FLOW
            let bw: Float = vertical ? 0.34 : min(0.23, 0.94 / nf)
            let bh: Float = vertical ? min(0.11, 0.94 / nf) : 0.12
            return nodes.enumerated().map { i, node in
                let span = vertical ? bh : bw
                let t: Float = n == 1 ? 0.5 : (span / 2 + 0.02) + (0.96 - span) * Float(i) / (nf - 1)
                return vertical ? node.at(0.5 - bw / 2, t - bh / 2, bw, bh) : node.at(t - bw / 2, 0.44, bw, bh)
            }
        case .ARCHITECTURE:
            return nodes.enumerated().map { i, node in
                let cols = n <= 3 ? n : 3
                let row = i / cols, col = i % cols
                let rows = (n + cols - 1) / cols
                let cf = Float(cols), rf = Float(rows)
                return node.at(0.06 + Float(col) * (0.88 / cf) + 0.02, 0.1 + Float(row) * (0.8 / rf), 0.88 / cf - 0.04, min(0.2, 0.62 / rf))
            }
        case .ORG:
            let children = Array(nodes.dropFirst())
            let head = nodes[0].at(0.35, 0.05, 0.3, 0.12)
            let m = Float(children.count)
            return [head] + children.enumerated().map { i, node in
                let w = min(0.28, 0.92 / m - 0.03)
                return node.at(0.04 + Float(i) * (0.92 / m) + (0.92 / m - w) / 2, 0.55, w, 0.12)
            }
        case .TIMELINE:
            return nodes.enumerated().map { i, node in
                let w = min(0.2, 0.94 / nf)
                let t: Float = n == 1 ? 0.5 : (w / 2 + 0.02) + (0.96 - w) * Float(i) / (nf - 1)
                return node.at(t - w / 2, i % 2 == 0 ? 0.2 : 0.62, w, 0.12, box: .NOTE)
            }
        case .RELATION:
            return nodes.enumerated().map { i, node in
                let m = max(2, n)
                let r: Float = 0.3
                let angle = -Double.pi / 2 + Double(i) * 2 * Double.pi / Double(m)
                let x: Float = m == 2 ? (i == 0 ? -0.22 : 0.22) : r * Float(cos(angle))
                let y: Float = m == 2 ? 0 : r * Float(sin(angle))
                return node.at(0.5 + x - r / 2, 0.5 + y - r / 2, r, r, box: .CIRCLE)
            }
        case .COMPARISON:
            return nodes.enumerated().map { i, node in
                let col = i % 2, row = i / 2
                let rows = Float((n + 1) / 2)
                return node.at(0.06 + Float(col) * 0.48, 0.08 + Float(row) * (0.84 / rows), 0.4, min(0.16, 0.7 / rows))
            }
        case .CYCLE:
            return nodes.enumerated().map { i, node in
                let w = min(0.3, 1.6 / Float(n + 2)), h = min(0.16, 0.9 / Float(n + 2))
                let a = -Double.pi / 2 + Double(i) * 2 * Double.pi / Double(n)
                return node.at(0.5 + 0.36 * Float(cos(a)) - w / 2, 0.5 + 0.36 * Float(sin(a)) - h / 2, w, h)
            }
        case .MIND_MAP:
            let center = nodes[0].at(0.36, 0.42, 0.28, 0.16)
            let rest = Array(nodes.dropFirst())
            let m = Double(max(1, rest.count))
            return [center] + rest.enumerated().map { i, node in
                let a = -Double.pi / 2 + (Double(i) + 0.5) * 2 * Double.pi / m
                let w: Float = 0.22, h: Float = 0.11
                return node.at(clampF(0.5 + 0.37 * Float(cos(a)) - w / 2, 0.01, 0.99 - w),
                               clampF(0.5 + 0.38 * Float(sin(a)) - h / 2, 0.01, 0.99 - h), w, h)
            }
        case .PYRAMID:
            return nodes.enumerated().map { i, node in
                let h = 0.92 / nf
                let w = 0.92 * Float(i + 1) / nf
                return node.at(0.5 - w / 2, 0.04 + Float(i) * h, w, h, box: i == 0 ? .TRIANGLE : .TRAPEZOID)
            }
        case .FUNNEL:
            return nodes.enumerated().map { i, node in
                let h = 0.92 / nf
                let w = 0.94 * Float(n - i) / nf
                return node.at(0.5 - w / 2, 0.04 + Float(i) * h, w, h - 0.01, box: .TRAPEZOID)
            }
        case .VENN:
            return nodes.enumerated().map { i, node in
                let r: Float = n <= 2 ? 0.3 : 0.26
                let c: (Float, Float)
                if n <= 2 { c = (i == 0 ? 0.38 : 0.62, 0.5) }
                else if n == 3 { c = [(0.38, 0.38), (0.62, 0.38), (0.5, 0.6)][i] as (Float, Float) }
                else {
                    let a = -Double.pi / 2 + Double(i) * 2 * Double.pi / Double(n)
                    c = (0.5 + 0.16 * Float(cos(a)), 0.5 + 0.16 * Float(sin(a)))
                }
                return node.at(c.0 - r, c.1 - r, r * 2, r * 2, box: .CIRCLE)
            }
        case .MATRIX:
            let cols = n <= 4 ? 2 : 3
            let rows = (n + cols - 1) / cols
            return nodes.enumerated().map { i, node in
                let w = 0.94 / Float(cols), h = 0.94 / Float(rows)
                return node.at(0.03 + Float(i % cols) * w, 0.03 + Float(i / cols) * h, w - 0.01, h - 0.01)
            }
        case .FISHBONE:
            let head = nodes[0].at(0.76, 0.4, 0.22, 0.2)
            let causes = Array(nodes.dropFirst())
            let pairs = Float((causes.count + 1) / 2)
            return [head] + causes.enumerated().map { i, node in
                let col = Float(i / 2)
                let top = i % 2 == 0
                let w = min(0.22, 0.66 / pairs - 0.02)
                return node.at(0.04 + col * (0.68 / pairs), top ? 0.06 : 0.8, w, 0.14)
            }
        case .CONCEPT_MAP, .TREE:
            let parents = treeParents(kind, n)
            var depth = [Int](repeating: 0, count: n)
            for i in 1..<max(1, n) where i < n { depth[i] = depth[max(parents[i], 0)] + 1 }
            let levels = (depth.max() ?? 0) + 1
            let h = min(0.14, 0.8 / Float(levels))
            return nodes.enumerated().map { i, node in
                let sameLevel = (0..<n).filter { depth[$0] == depth[i] }
                let k = Float(sameLevel.firstIndex(of: i)!)
                let m = Float(sameLevel.count)
                let w = min(0.3, 0.96 / m - 0.02)
                let slot = 0.98 / m
                return node.at(0.01 + k * slot + (slot - w) / 2, 0.04 + Float(depth[i]) * ((0.92 - h) / Float(max(1, levels - 1))), w, h)
            }
        case .KWL:
            return nodes.enumerated().map { i, node in
                let col = Float(i % 3)
                let header = i < 3
                let row = Float(i / 3)
                let bodyRows = Float(max(1, (n - 3 + 2) / 3))
                let w: Float = 0.31
                return header ? node.at(0.02 + col * 0.33, 0.03, w, 0.13)
                    : node.at(0.02 + col * 0.33, 0.19 + (row - 1) * (0.78 / bodyRows), w, 0.78 / bodyRows - 0.02)
            }
        case .STEPS:
            return nodes.enumerated().map { i, node in
                let w = min(0.3, 0.96 / nf - 0.02)
                let h: Float = 0.16
                let d = Float(max(1, n - 1))
                return node.at(0.02 + Float(i) * ((0.96 - w) / d), 0.8 - h - Float(i) * ((0.76 - h) / d), w, h)
            }
        case .HUB:
            let effect = nodes.last!.at(0.64, 0.4, 0.32, 0.2)
            let causes = Array(nodes.dropLast())
            let m = Float(max(1, causes.count))
            return causes.enumerated().map { i, node in
                let h = min(0.16, 0.9 / m - 0.03)
                return node.at(0.04, 0.05 + Float(i) * (0.9 / m) + (0.9 / m - h) / 2, 0.34, h)
            } + [effect]
        default:
            return DiagramCatalog.layout(kind, nodes)
        }
    }

    /// Ağaç türlerinde her düğümün ebeveyni (-1: kök).
    static func treeParents(_ kind: DiagramKind, _ n: Int) -> [Int] {
        var parents = [Int](repeating: -1, count: n)
        if n <= 1 { return parents }
        let second = min(n - 1, kind == .TREE ? 2 : 3)
        for i in 1...second { parents[i] = 0 }
        if second + 1 < n { for i in (second + 1)..<n { parents[i] = 1 + (i - second - 1) % second } }
        return parents
    }

    // MARK: - koordinatlar

    /// Elemanın dörtgenine eşleyen dönüşüm (0, 1 ve 3. köşeden; tasarım alanı 0..UNIT).
    static func matrix(_ stroke: Stroke) -> CGAffineTransform? {
        guard stroke.points.count >= 4 else { return nil }
        let p0 = stroke.points[0], p1 = stroke.points[1], p3 = stroke.points[3]
        let u = CGFloat(UNIT)
        let a = CGFloat(p1.x - p0.x) / u, b = CGFloat(p1.y - p0.y) / u
        let c = CGFloat(p3.x - p0.x) / u, d = CGFloat(p3.y - p0.y) / u
        if abs(a * d - b * c) < 1e-12 { return nil }
        return CGAffineTransform(a: a, b: b, c: c, d: d, tx: CGFloat(p0.x), ty: CGFloat(p0.y))
    }

    static func toCanvas(_ stroke: Stroke, _ ux: Float, _ uy: Float) -> P? {
        guard let m = matrix(stroke) else { return nil }
        let f = stroke.diagram?.frame ?? DiagramFrame()
        let q = CGPoint(x: CGFloat((ux - f.left) / f.width * UNIT), y: CGFloat((uy - f.top) / f.height * UNIT)).applying(m)
        return P(Float(q.x), Float(q.y))
    }

    static func toUnit(_ stroke: Stroke, _ p: P) -> P? {
        guard let m = matrix(stroke) else { return nil }
        let q = CGPoint(x: CGFloat(p.x), y: CGFloat(p.y)).applying(m.inverted())
        let f = stroke.diagram?.frame ?? DiagramFrame()
        return P(f.left + Float(q.x) / UNIT * f.width, f.top + Float(q.y) / UNIT * f.height)
    }

    static func nodeCorners(_ stroke: Stroke, _ node: DiagramNode) -> [P] {
        [toCanvas(stroke, node.x, node.y), toCanvas(stroke, node.x + node.w, node.y),
         toCanvas(stroke, node.x + node.w, node.y + node.h), toCanvas(stroke, node.x, node.y + node.h)].compactMap { $0 }
    }

    static func nodeAt(_ stroke: Stroke, _ p: P) -> DiagramNode? {
        guard let spec = stroke.diagram, let u = toUnit(stroke, p) else { return nil }
        return spec.nodes.last { u.x >= $0.x && u.x <= $0.x + $0.w && u.y >= $0.y && u.y <= $0.y + $0.h }
    }

    static func edgeAt(_ stroke: Stroke, _ p: P, _ tolerance: Float) -> Int? {
        guard let spec = stroke.diagram else { return nil }
        var nodes: [String: DiagramNode] = [:]
        for n in spec.nodes { nodes[n.id] = n }
        var best: Int? = nil
        var bestDistance = Float.greatestFiniteMagnitude
        for (i, edge) in spec.edges.enumerated() {
            guard let a = nodes[edge.from], let b = nodes[edge.to] else { continue }
            let path = edgePath(stroke, edge, a, b, index: i)
            if path.count < 2 { continue }
            var d = Float.greatestFiniteMagnitude
            for k in 1..<path.count { d = min(d, segmentDistance(p, path[k - 1], path[k])) }
            if d < bestDistance { bestDistance = d; best = i }
        }
        return bestDistance <= tolerance ? best : nil
    }

    static func edgePath(_ stroke: Stroke, _ edge: DiagramEdge, _ from: DiagramNode, _ to: DiagramNode, index: Int? = nil) -> [P] {
        guard let spec = stroke.diagram else { return [] }
        let i = index ?? (spec.edges.firstIndex(of: edge) ?? -1)
        return routeOf(spec, i, edge, from, to).compactMap { toCanvas(stroke, $0.x / UNIT, $0.y / UNIT) }
    }

    static func portPoint(_ stroke: Stroke, _ node: DiagramNode, _ side: DiagramSide) -> P? {
        let p = DiagramRouting.port(DiagramRouting.rectOf(node), side)
        return toCanvas(stroke, p.x, p.y)
    }

    static func portAt(_ stroke: Stroke, _ p: P, _ tolerance: Float, only: String? = nil) -> (DiagramNode, DiagramSide)? {
        guard let spec = stroke.diagram else { return nil }
        var best: (DiagramNode, DiagramSide)? = nil
        var bestDistance = Float.greatestFiniteMagnitude
        for node in spec.nodes {
            if let only, node.id != only { continue }
            for side in sides {
                guard let q = portPoint(stroke, node, side) else { continue }
                let d = hypotf(q.x - p.x, q.y - p.y)
                if d < bestDistance { bestDistance = d; best = (node, side) }
            }
        }
        return bestDistance <= tolerance ? best : nil
    }

    static func sideNear(_ stroke: Stroke, _ node: DiagramNode, _ p: P) -> DiagramSide {
        guard let u = toUnit(stroke, p) else { return .AUTO }
        return DiagramRouting.sideAt(DiagramRouting.rectOf(node), RoutePoint(u.x, u.y))
    }

    static let sides: [DiagramSide] = [.TOP, .RIGHT, .BOTTOM, .LEFT]

    private static func segmentDistance(_ p: P, _ a: P, _ b: P) -> Float {
        let vx = b.x - a.x, vy = b.y - a.y
        let len2 = vx * vx + vy * vy
        let t: Float = len2 < 1e-6 ? 0 : clampF(((p.x - a.x) * vx + (p.y - a.y) * vy) / len2, 0, 1)
        return hypotf(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
    }

    // MARK: - çizim

    /// Diyagramı çizer. [labels] false ise yazılar çizilmez (küçük resimler).
    static func draw(_ c: CGContext, _ stroke: Stroke, offsetX: Float = 0, offsetY: Float = 0, coverage: Bool = false, labels: Bool = true) {
        guard let spec = stroke.diagram, let m = matrix(stroke) else { return }
        c.saveGState()
        defer { c.restoreGState() }
        if offsetX != 0 || offsetY != 0 { c.translateBy(x: CGFloat(offsetX), y: CGFloat(offsetY)) }
        let clip = c.boundingBoxOfClipPath
        if !clip.isNull && !clip.isInfinite {
            let xs = stroke.points.map(\.x), ys = stroke.points.map(\.y)
            let r = CGRect(x: CGFloat(xs.min()!), y: CGFloat(ys.min()!), width: CGFloat(xs.max()! - xs.min()!), height: CGFloat(ys.max()! - ys.min()!))
            if !clip.intersects(r.insetBy(dx: -1, dy: -1)) { return }
        }
        c.concatenate(m)
        let frame = spec.frame
        if !frame.isDefault && frame.width > 0 && frame.height > 0 {
            c.scaleBy(x: CGFloat(1 / frame.width), y: CGFloat(1 / frame.height))
            c.translateBy(x: CGFloat(-frame.left * UNIT), y: CGFloat(-frame.top * UNIT))
        }
        DiagramPainter.paint(ACanvas(c), spec, coverage, aspectK(stroke), labels)
    }

    /// Yatay sıkıştırma: tasarım alanında bir birimin ekrandaki dikey boyunun yatay boyuna oranı.
    static func aspectK(_ stroke: Stroke) -> Float {
        let pts = stroke.points
        if pts.count < 4 { return 1 }
        let f = stroke.diagram?.frame ?? DiagramFrame()
        let w = hypotf(pts[1].x - pts[0].x, pts[1].y - pts[0].y) / max(1e-3, f.width)
        let h = hypotf(pts[3].x - pts[0].x, pts[3].y - pts[0].y) / max(1e-3, f.height)
        if w < 1e-3 || h < 1e-3 { return 1 }
        return clampF(h / w, 0.2, 5)
    }

    // MARK: - ok yolları

    /// Sıra diyagramında etkinlik çubuğunun yarı genişliği.
    static let ACTIVATION: Float = 7

    static func sequenceY(_ spec: DiagramSpec, _ index: Int) -> Float {
        let top = ((spec.nodes.map { $0.y + $0.h }.max() ?? 0.12) + 0.05) * UNIT
        let m = Float(max(1, spec.edges.count))
        let step = min(0.12 * UNIT, (0.96 * UNIT - top) / m)
        return top + step * (Float(index) + 0.5)
    }

    private static func anchorRect(_ n: DiagramNode) -> RouteRect {
        if n.box == .DOT && n.w > n.h * 1.2 {
            let cx = (n.x + n.h * 0.35) * UNIT, cy = (n.y + n.h / 2) * UNIT, r = n.h * 0.22 * UNIT
            return RouteRect(left: cx - r, top: cy - r, right: cx + r, bottom: cy + r)
        }
        return DiagramRouting.rectOf(n, scale: UNIT)
    }

    private static func autoSides(_ a: RouteRect, _ b: RouteRect) -> (DiagramSide, DiagramSide) {
        let gapX = max(b.left - a.right, a.left - b.right)
        let gapY = max(b.top - a.bottom, a.top - b.bottom)
        if gapY >= gapX { return b.centerY > a.centerY ? (.BOTTOM, .TOP) : (.TOP, .BOTTOM) }
        return b.centerX > a.centerX ? (.RIGHT, .LEFT) : (.LEFT, .RIGHT)
    }

    /// Okun tasarım alanındaki (0..UNIT) yolu.
    static func routeOf(_ spec: DiagramSpec, _ index: Int, _ edge: DiagramEdge, _ a: DiagramNode, _ b: DiagramNode) -> [RoutePoint] {
        let kind = spec.kind
        if kind == .SEQUENCE && index >= 0 {
            let y = sequenceY(spec, index)
            let ax = (a.x + a.w / 2) * UNIT, bx = (b.x + b.w / 2) * UNIT
            let dir: Float = bx >= ax ? 1 : -1
            let sa: Float = a.role == "focal" ? ACTIVATION : 0
            let sb: Float = b.role == "focal" ? ACTIVATION : 0
            return [RoutePoint(ax + dir * sa, y), RoutePoint(bx - dir * sb, y)]
        }
        if edge.role == "arc" { return arcOver(a, b) }
        if kind == .SANKEY && index >= 0 {
            guard let r = DiagramCatalog.ribbons(spec.nodes, spec.edges)[index] else { return DiagramRouting.route(edge, a, b, scale: UNIT) }
            let x0 = r.x0 * UNIT, x1 = r.x1 * UNIT, y0 = (r.y0 + r.h / 2) * UNIT, y1 = (r.y1 + r.h / 2) * UNIT
            return (0...16).map { i in
                let t = Float(i) / 16, u = 1 - t
                let mx = (x0 + x1) / 2
                return RoutePoint(u * u * u * x0 + 3 * u * u * t * mx + 3 * u * t * t * mx + t * t * t * x1,
                                  u * u * u * y0 + 3 * u * u * t * y0 + 3 * u * t * t * y1 + t * t * t * y1)
            }
        }
        if (kind == .LOOP || kind == .CYCLE) && edge.role != "spoke" && edge.fromSide == .AUTO && edge.toSide == .AUTO
            && a.role != "hub" && b.role != "hub" {
            return ringArc(a, b) ?? DiagramRouting.route(edge, a, b, scale: UNIT)
        }
        let ra = anchorRect(a), rb = anchorRect(b)
        var fs = edge.fromSide, ts = edge.toSide
        if fs == .AUTO && ts == .AUTO {
            if !DiagramCatalog.elbow(kind) {
                let straight = DiagramRouting.route(ra, rb, fs, ts, margin: 0.03 * UNIT)
                if DiagramCatalog.straightKinds.contains(kind) || !crossesOthers(spec, straight, a, b) { return straight }
                let (sa, sb) = autoSides(ra, rb)
                let elbow = DiagramRouting.route(ra, rb, sa, sb, margin: 0.03 * UNIT)
                if !crossesOthers(spec, elbow, a, b) { return elbow }
                return DiagramRouting.route(ra, rb, .BOTTOM, .BOTTOM, margin: 0.05 * UNIT)
            }
            (fs, ts) = autoSides(ra, rb)
        }
        return DiagramRouting.route(ra, rb, fs, ts, margin: 0.03 * UNIT)
    }

    private static func crossesOthers(_ spec: DiagramSpec, _ route: [RoutePoint], _ a: DiagramNode, _ b: DiagramNode) -> Bool {
        let skip: Set<DiagramBox> = [.GROUP, .LANE, .COLUMN, .LABEL]
        return spec.nodes.contains { n in
            if n.id == a.id || n.id == b.id || skip.contains(n.box) { return false }
            let r = DiagramRouting.rectOf(n, scale: UNIT)
            let inset = min(r.width, r.height) * 0.08
            return route.count > 1 && (1..<route.count).contains { i in
                segmentHits(route[i - 1], route[i], r.left + inset, r.top + inset, r.right - inset, r.bottom - inset)
            }
        }
    }

    /// Parça dikdörtgeni kesiyor mu (Liang–Barsky).
    private static func segmentHits(_ p: RoutePoint, _ q: RoutePoint, _ l: Float, _ t: Float, _ r: Float, _ b: Float) -> Bool {
        var t0: Float = 0, t1: Float = 1
        let dx = q.x - p.x, dy = q.y - p.y
        let ps = [-dx, dx, -dy, dy]
        let qs = [p.x - l, r - p.x, p.y - t, b - p.y]
        for i in 0..<4 {
            if abs(ps[i]) < 1e-6 { if qs[i] < 0 { return false }; continue }
            let u = qs[i] / ps[i]
            if ps[i] < 0 { if u > t1 { return false }; if u > t0 { t0 = u } } else { if u < t0 { return false }; if u < t1 { t1 = u } }
        }
        return t0 <= t1
    }

    private static func arcOver(_ a: DiagramNode, _ b: DiagramNode) -> [RoutePoint] {
        let x0 = (a.x + a.w / 2) * UNIT, y0 = a.y * UNIT
        let x1 = (b.x + b.w / 2) * UNIT, y1 = b.y * UNIT
        let rise = max(0.07 * UNIT, abs(x1 - x0) * 0.32)
        let cy = min(y0, y1) - rise
        let cx = (x0 + x1) / 2
        return (0...20).map { i in
            let t = Float(i) / 20, u = 1 - t
            return RoutePoint(u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1)
        }
    }

    private static func ringArc(_ a: DiagramNode, _ b: DiagramNode) -> [RoutePoint]? {
        let cx = 0.5 * UNIT, cy = 0.5 * UNIT
        let ax = (a.x + a.w / 2) * UNIT - cx, ay = (a.y + a.h / 2) * UNIT - cy
        let bx = (b.x + b.w / 2) * UNIT - cx, by = (b.y + b.h / 2) * UNIT - cy
        let ra = hypotf(ax, ay), rb = hypotf(bx, by)
        if ra < 0.1 * UNIT || rb < 0.1 * UNIT { return nil }
        let r = (ra + rb) / 2
        let ta = Double(atan2f(ay, ax))
        var tb = Double(atan2f(by, bx))
        var d = tb - ta
        while d <= -Double.pi { d += 2 * Double.pi }
        while d > Double.pi { d -= 2 * Double.pi }
        tb = ta + d
        let ea = DiagramRouting.rectOf(a, scale: UNIT), eb = DiagramRouting.rectOf(b, scale: UNIT)
        let m: Float = 6
        func inside(_ px: Float, _ py: Float, _ q: RouteRect) -> Bool { px > q.left - m && px < q.right + m && py > q.top - m && py < q.bottom + m }
        let pts = (0...48).map { i -> RoutePoint in
            let t = ta + (tb - ta) * Double(i) / 48
            return RoutePoint(cx + r * Float(cos(t)), cy + r * Float(sin(t)))
        }.filter { !inside($0.x, $0.y, ea) && !inside($0.x, $0.y, eb) }
        return pts.count >= 2 ? pts : nil
    }
}
