import CoreGraphics
import Foundation

/// Türe özgü süsler (Android `DiagramDecor`): grafik eksenleri ve ızgaraları, sıra diyagramının yaşam çizgileri, radar
/// halkaları, Gantt hafta ızgarası, yolculuk duygu eğrisi, dörtlü konum ve Wardley eksenleri, zaman çizelgesi ekseni,
/// balık kılçığı omurgası, patlatılmış / aksonometrik levhalar. [before] oklardan ve kutulardan önce, [after] sonra.
enum DiagramDecor {
    typealias Ctx = DiagramPainter.Ctx
    typealias Look = DiagramPainter.Look

    private static let U = DiagramPainter.U
    private static let PLOT_L = DiagramCatalog.PLOT_L
    private static let PLOT_R = DiagramCatalog.PLOT_R
    private static let PLOT_T = DiagramCatalog.PLOT_T
    private static let PLOT_B = DiagramCatalog.PLOT_B
    private static let GANTT_L = DiagramCatalog.GANTT_L
    private static let sans = DiagramFace.sans
    private static let medium = DiagramFace.medium
    private static let mono = DiagramFace.mono
    private static let BLACK = DiagramPainter.BLACK
    private static let WHITE = DiagramPainter.WHITE

    private static func alpha(_ c: UInt32, _ a: Float) -> UInt32 { DiagramPainter.alpha(c, a) }
    private static func blend(_ a: UInt32, _ b: UInt32, _ t: Float) -> UInt32 { DiagramPainter.blend(a, b, t) }
    private static func fmt(_ v: Float) -> String { DiagramPainter.fmt(v) }
    private static func measure(_ t: String, _ f: DiagramFace, _ s: Float, _ sp: Float) -> Float { DiagramPainter.measure(t, f, s, sp) }
    private static func fitSize(_ t: String, _ f: DiagramFace, _ s: Float, _ w: Float, _ sp: Float) -> Float { DiagramPainter.fitSize(t, f, s, w, sp) }
    private static func rectOf(_ n: DiagramNode) -> RectF { DiagramPainter.rectOf(n) }
    private static func drawText(_ x: Ctx, _ text: String, _ px: Float, _ py: Float, _ face: DiagramFace, _ size: Float, _ color: UInt32, spacing: Float = 0) {
        DiagramPainter.drawText(x, text, px, py, face, size, color, spacing: spacing)
    }
    private static func lastValue(_ text: String) -> Float? { DiagramCatalog.parse(text).values.last }

    static func before(_ x: Ctx) {
        switch x.kind {
        case .BAR: chartAxes(x, DiagramCatalog.barScale(x.nodes))
        case .WATERFALL: chartAxes(x, DiagramCatalog.waterfallScale(x.nodes)); waterfallConnectors(x)
        case .LINE: chartAxes(x, DiagramCatalog.lineScale(x.nodes)); lineSeries(x)
        case .SCATTER:
            let (mx, my) = DiagramCatalog.scatterScale(x.nodes)
            chartAxes(x, my, mx); trend(x)
        case .RADAR: radar(x)
        case .POLAR: polar(x)
        case .GANTT: ganttGrid(x)
        case .SEQUENCE: lifelines(x)
        case .JOURNEY: journey(x)
        case .QUADRANT: quadrant(x)
        case .WARDLEY: wardley(x)
        case .EXPLODED: explodedAxis(x)
        case .AXONOMETRIC: isoGround(x)
        case .TIMELINE: timeline(x)
        case .FISHBONE: fishbone(x)
        case .STORY_MAP: backbone(x)
        default: break
        }
    }

    static func after(_ x: Ctx) {
        switch x.kind {
        case .BAR, .WATERFALL: barLabels(x)
        case .LINE: lineLabels(x)
        case .SCATTER: scatterLabels(x)
        case .GANTT: ganttLabels(x)
        case .SANKEY: sankeyLabels(x)
        case .LAYERS: layerNumbers(x)
        case .EXPLODED: explodedLabels(x)
        case .WARDLEY: wardleyMoves(x)
        default: break
        }
    }

    private static func paper(_ x: Ctx) -> UInt32 { x.pal.editorial ? x.pal.paper : WHITE }

    private static func fill(_ x: Ctx, _ color: UInt32) {
        x.p.reset(); x.p.style = .FILL; x.p.color = x.pal.coverage ? BLACK : color
    }

    private static func stroke(_ x: Ctx, _ color: UInt32, _ width: Float, dashed: Bool = false) {
        DiagramPainter.strokePaint(x, x.pal.coverage ? BLACK : color, width, dashed)
    }

    private static func tick(_ x: Ctx) -> Float { clampF(x.base * 0.5, 10, 26) }

    // MARK: - grafikler

    private static func chartAxes(_ x: Ctx, _ maxY: Float, _ maxX: Float? = nil) {
        let l = PLOT_L * U, r = PLOT_R * U, t = PLOT_T * U, b = PLOT_B * U
        let s = tick(x)
        for i in 0...4 {
            let y = b - (b - t) * Float(i) / 4
            if i == 0 { stroke(x, x.pal.ink, x.line * 0.9) } else { stroke(x, x.pal.rule, x.line * 0.55) }
            x.c.drawLine(l, y, r, y, x.p)
            let label = fmt(maxY * Float(i) / 4)
            drawText(x, label, l - 8 * x.k - measure(label, mono, s, 0) * x.k, y + s * 0.36, mono, s, x.pal.muted)
        }
        if let maxX {
            for i in 0...4 {
                let xx = l + (r - l) * Float(i) / 4
                if i > 0 { stroke(x, x.pal.rule, x.line * 0.55); x.c.drawLine(xx, t, xx, b, x.p) }
                let label = fmt(maxX * Float(i) / 4)
                drawText(x, label, xx - measure(label, mono, s, 0) * x.k / 2, b + s * 1.5, mono, s, x.pal.muted)
            }
            stroke(x, x.pal.ink, x.line * 0.9); x.c.drawLine(l, t, l, b, x.p)
        }
    }

    private static func barLabels(_ x: Ctx) {
        let s = tick(x)
        let running = x.kind == .WATERFALL ? DiagramCatalog.waterfallLevels(x.nodes) : nil
        for (i, n) in x.nodes.enumerated() where n.box == .BAR {
            let r = rectOf(n)
            let item = DiagramCatalog.parse(n.text)
            let v = item.values.last
            let value: String
            if x.kind == .WATERFALL && (item.computed || v == nil) {
                value = fmt((running.flatMap { i < $0.count ? $0[i].1 : nil }) ?? 0)
            } else if let v {
                if item.signed && v > 0 { value = "+" + fmt(v) }
                else if item.signed && v < 0 { value = "−" + fmt(-v) }
                else { value = fmt(v) }
            } else { value = "" }
            let focal = n.role == "focal"
            let vs = s * 1.05
            let face: DiagramFace = (focal || item.computed) ? medium : mono
            let color = focal ? x.pal.accent : x.pal.ink
            drawText(x, value, r.centerX() - measure(value, face, vs, 0) * x.k / 2, r.top - vs * 0.45, face, vs, color)
            let name = DiagramCatalog.label(x.kind, n.text)
            let pitch = (PLOT_R - PLOT_L) * U / Float(max(1, x.nodes.count))
            let ns = fitSize(name, sans, s * 1.05, (pitch - 6) / x.k, 0)
            drawText(x, name, r.centerX() - measure(name, sans, ns, 0) * x.k / 2, PLOT_B * U + ns * 1.45, sans, ns, focal ? x.pal.accent : x.pal.muted)
        }
    }

    private static func waterfallConnectors(_ x: Ctx) {
        let levels = DiagramCatalog.waterfallLevels(x.nodes)
        let mx = DiagramCatalog.waterfallScale(x.nodes)
        func y(_ v: Float) -> Float { (PLOT_B - (PLOT_B - PLOT_T) * (max(v, 0) / mx)) * U }
        stroke(x, x.pal.muted, x.line * 0.7, dashed: true)
        if x.nodes.count < 2 { return }
        for i in 0..<(x.nodes.count - 1) {
            let a = rectOf(x.nodes[i]), b = rectOf(x.nodes[i + 1])
            let yy = y(levels[i].1)
            x.c.drawLine(a.right, yy, b.left, yy, x.p)
        }
    }

    private static func centers(_ x: Ctx) -> [(Float, Float)] { x.nodes.filter { $0.box == .DOT }.map { DiagramPainter.dotCenter($0) } }

    private static func lineSeries(_ x: Ctx) {
        let pts = centers(x)
        if pts.count < 2 { return }
        let path = APath()
        for (i, q) in pts.enumerated() { if i == 0 { path.moveTo(q.0, q.1) } else { path.lineTo(q.0, q.1) } }
        let area = APath(path)
        area.lineTo(pts.last!.0, PLOT_B * U); area.lineTo(pts[0].0, PLOT_B * U); area.close()
        fill(x, alpha(x.pal.ink, 0.06)); x.c.drawPath(area, x.p)
        stroke(x, x.pal.ink, x.line * 1.35); x.c.drawPath(path, x.p)
    }

    private static func lineLabels(_ x: Ctx) {
        let s = tick(x)
        let dots = x.nodes.filter { $0.box == .DOT }
        let pitch = (PLOT_R - PLOT_L) * U / Float(max(1, dots.count))
        for n in dots {
            let (cx, cy) = DiagramPainter.dotCenter(n)
            let name = DiagramCatalog.label(x.kind, n.text)
            let ns = fitSize(name, sans, s * 1.05, (pitch - 4) / x.k, 0)
            drawText(x, name, cx - measure(name, sans, ns, 0) * x.k / 2, PLOT_B * U + ns * 1.45, sans, ns, x.pal.muted)
            if n.role == "focal" {
                let v = fmt(lastValue(n.text) ?? 0)
                let vs = s * 1.15
                drawText(x, v, cx - measure(v, medium, vs, 0) * x.k / 2, cy - vs * 0.9, medium, vs, x.pal.accent)
            }
        }
    }

    private static func trend(_ x: Ctx) {
        let pts = centers(x)
        if pts.count < 3 { return }
        let n = Double(pts.count)
        let mx = pts.reduce(0.0) { $0 + Double($1.0) } / n, my = pts.reduce(0.0) { $0 + Double($1.1) } / n
        var sxy = 0.0, sxx = 0.0
        for (px, py) in pts { sxy += (Double(px) - mx) * (Double(py) - my); sxx += (Double(px) - mx) * (Double(px) - mx) }
        if sxx < 1e-6 { return }
        let slope = sxy / sxx
        let x0 = PLOT_L * U, x1 = PLOT_R * U
        let y0 = clampF(Float(my + slope * (Double(x0) - mx)), PLOT_T * U, PLOT_B * U)
        let y1 = clampF(Float(my + slope * (Double(x1) - mx)), PLOT_T * U, PLOT_B * U)
        stroke(x, x.pal.muted, x.line * 0.8, dashed: true)
        x.c.drawLine(x0, y0, x1, y1, x.p)
    }

    private static func scatterLabels(_ x: Ctx) {
        let s = tick(x)
        for n in x.nodes where n.box == .DOT {
            let (cx, cy) = DiagramPainter.dotCenter(n)
            let name = DiagramCatalog.label(x.kind, n.text)
            if name.isEmpty { continue }
            let focal = n.role == "focal"
            drawText(x, name, cx + 12 * x.k, cy - 8, focal ? medium : mono, focal ? s * 1.15 : s, focal ? x.pal.accent : x.pal.muted)
        }
    }

    // MARK: radar ve kutupsal

    private static func ringRadius(_ x: Ctx) -> Float { 0.34 * U }

    private static func radar(_ x: Ctx) {
        let n = x.nodes.count
        if n < 3 { return }
        let cx = 0.5 * U, cy = 0.5 * U, r = ringRadius(x)
        let values = x.nodes.map { DiagramCatalog.parse($0.text).values }
        let mx = niceMax(values.flatMap { $0 }.max() ?? 1)
        func pt(_ i: Int, _ f: Float) -> (Float, Float) {
            let a = -Double.pi / 2 + 2 * Double.pi * Double(i) / Double(n)
            return (cx + r * f * Float(cos(a)), cy + r * f * Float(sin(a)))
        }
        stroke(x, x.pal.rule, x.line * 0.6)
        for ring in 1...4 {
            let path = APath()
            for i in 0...n { let q = pt(i % n, Float(ring) / 4); if i == 0 { path.moveTo(q.0, q.1) } else { path.lineTo(q.0, q.1) } }
            x.c.drawPath(path, x.p)
        }
        for i in 0..<n { let q = pt(i, 1); x.c.drawLine(cx, cy, q.0, q.1, x.p) }
        let series = values.map(\.count).max() ?? 0
        let colors = [x.pal.ink, x.pal.accent, x.pal.link]
        for sIdx in 0..<max(0, series) {
            let path = APath()
            for i in 0...n {
                let vs = values[i % n]
                let v = sIdx < vs.count ? vs[sIdx] : 0
                let q = pt(i % n, clampF(v / mx, 0, 1))
                if i == 0 { path.moveTo(q.0, q.1) } else { path.lineTo(q.0, q.1) }
            }
            let c = colors[sIdx % colors.count]
            fill(x, alpha(c, sIdx == 0 ? 0.07 : 0.12)); x.c.drawPath(path, x.p)
            stroke(x, c, x.line * 1.3); x.c.drawPath(path, x.p)
            for i in 0..<n {
                let v = sIdx < values[i].count ? values[i][sIdx] : 0
                let q = pt(i, clampF(v / mx, 0, 1))
                fill(x, c); x.c.drawOval(RectF(q.0 - 4.5 * x.k, q.1 - 4.5, q.0 + 4.5 * x.k, q.1 + 4.5), x.p)
            }
        }
        let s = tick(x) * 0.85
        drawText(x, fmt(mx), cx + 6 * x.k, cy - r - 4, mono, s, x.pal.muted)
    }

    private static func polar(_ x: Ctx) {
        let n = x.nodes.count
        if n < 1 { return }
        let cx = 0.5 * U, cy = 0.5 * U, r = ringRadius(x)
        let values = x.nodes.map { lastValue($0.text) ?? 0 }
        let mx = niceMax(values.max() ?? 1)
        stroke(x, x.pal.rule, x.line * 0.6)
        for ring in 1...4 {
            let rr = r * Float(ring) / 4
            x.c.drawOval(RectF(cx - rr, cy - rr, cx + rr, cy + rr), x.p)
        }
        let sweep = 360 / Float(n)
        for (i, node) in x.nodes.enumerated() {
            let f = clampF(values[i] / mx, 0, 1)
            let rr = r * f
            let start = -90 + sweep * (Float(i) - 0.5)
            let path = APath()
            path.moveTo(cx, cy)
            path.arcTo(RectF(cx - rr, cy - rr, cx + rr, cy + rr), start + 1, sweep - 2)
            path.close()
            let color = node.fillColorHex.map { DiagramPainter.hex($0) } ?? (node.role == "focal" ? x.pal.accent : blend(paper(x), x.pal.ink, 0.38))
            fill(x, color); x.c.drawPath(path, x.p)
        }
        let s = tick(x) * 0.85
        drawText(x, fmt(mx), cx + 6 * x.k, cy - r - 4, mono, s, x.pal.muted)
    }

    private static func niceMax(_ v: Float) -> Float {
        if v <= 0 { return 1 }
        let exp = Float(Foundation.pow(10.0, Foundation.log10(Double(v)).rounded(.down)))
        let f = v / exp
        let nice: Float = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10
        return nice * exp
    }

    // MARK: Gantt

    private static func ganttGrid(_ x: Ctx) {
        let weeks = DiagramCatalog.ganttWeeks(x.nodes)
        let l = GANTT_L * U, r = 0.98 * U
        let pitch = (r - l) / Float(weeks)
        let bottom = (x.nodes.map { $0.y + $0.h }.max() ?? 0.9) * U + 0.03 * U
        let s = tick(x) * 0.95
        let every = pitch / x.k > s * 2.6 ? 1 : pitch / x.k > s * 1.4 ? 2 : 4
        for w in 0...weeks {
            let xx = l + pitch * Float(w)
            stroke(x, w % 4 == 0 ? blend(x.pal.rule, x.pal.ink, 0.15) : x.pal.rule, x.line * 0.55)
            x.c.drawLine(xx, 0.105 * U, xx, bottom, x.p)
            if w < weeks && w % every == 0 {
                let label = "H\(w + 1)"
                drawText(x, label, xx + pitch / 2 - measure(label, mono, s, 0) * x.k / 2, 0.08 * U, mono, s, x.pal.muted)
            }
        }
        stroke(x, x.pal.ink, x.line * 0.9)
        x.c.drawLine(0.02 * U, 0.105 * U, r, 0.105 * U, x.p)
    }

    private static func ganttLabels(_ x: Ctx) {
        let s = clampF(x.base * 0.62, 10, 30)
        for n in x.nodes where n.box == .BAR {
            let r = rectOf(n)
            let name = DiagramCatalog.label(x.kind, n.text)
            let focal = n.role == "focal"
            let face: DiagramFace = focal ? medium : sans
            let ns = fitSize(name, face, min(s, r.height() * 1.1), (GANTT_L * U - 0.04 * U) / x.k, 0)
            drawText(x, name, 0.02 * U, r.centerY() + ns * 0.36, face, ns, focal ? x.pal.accent : x.pal.ink)
            let v = DiagramCatalog.parse(n.text).values
            if v.count >= 2 {
                let range = "\(fmt(v[v.count - 2]))–\(fmt(v[v.count - 1]))"
                let rs = min(s * 0.8, r.height() * 0.8)
                let w = measure(range, mono, rs, 0) * x.k
                if r.right + 8 * x.k + w < 0.995 * U {
                    drawText(x, range, r.right + 8 * x.k, r.centerY() + rs * 0.36, mono, rs, x.pal.muted)
                } else if r.width() > w + 12 * x.k {
                    drawText(x, range, r.right - 6 * x.k - w, r.centerY() + rs * 0.36, mono, rs, x.pal.editorial ? x.pal.onInk : WHITE)
                }
            }
        }
    }

    // MARK: Sankey, katmanlar

    private static func sankeyLabels(_ x: Ctx) {
        let s = clampF(x.base * 0.54, 10, 26)
        for n in x.nodes where n.box == .BAR {
            let r = rectOf(n)
            let name = DiagramCatalog.label(x.kind, n.text)
            let v = lastValue(n.text).map { fmt($0) } ?? ""
            let first = (n.role ?? "c0") == "c0"
            let nw = measure(name, sans, s, 0) * x.k
            let vw = measure(v, mono, s * 0.85, 0) * x.k
            if x.pal.editorial && !first {
                fill(x, alpha(x.pal.paper, 0.85))
                x.c.drawRoundRect(RectF(r.right + 6 * x.k, r.centerY() - s * 1.0, r.right + 14 * x.k + max(nw, vw), r.centerY() + s * 1.25), 3 * x.k, 3, x.p)
            }
            if first {
                drawText(x, name, r.left - 10 * x.k - nw, r.centerY() - s * 0.1, sans, s, x.pal.ink)
                drawText(x, v, r.left - 10 * x.k - vw, r.centerY() + s * 0.95, mono, s * 0.85, x.pal.muted)
            } else {
                drawText(x, name, r.right + 10 * x.k, r.centerY() - s * 0.1, sans, s, x.pal.ink)
                drawText(x, v, r.right + 10 * x.k, r.centerY() + s * 0.95, mono, s * 0.85, x.pal.muted)
            }
        }
    }

    private static func layerNumbers(_ x: Ctx) {
        let s = clampF(x.base * 0.62, 10, 30)
        for (i, n) in x.nodes.enumerated() {
            let r = rectOf(n)
            let label = String(format: "%02d", i + 1)
            let ls = fitSize(label, mono, min(s, r.height() * 0.5), (r.left - 0.02 * U) / x.k, 0)
            drawText(x, label, r.left - 14 * x.k - measure(label, mono, ls, 0) * x.k, r.centerY() + ls * 0.36, mono, ls,
                     n.role == "focal" ? x.pal.accent : x.pal.muted)
        }
    }

    // MARK: sıra diyagramı

    private static func lifelines(_ x: Ctx) {
        let bottom = 0.975 * U
        for n in x.nodes {
            let cx = (n.x + n.w / 2) * U
            stroke(x, alpha(x.pal.muted, 0.7), x.line * 0.7, dashed: true)
            x.c.drawLine(cx, (n.y + n.h) * U, cx, bottom, x.p)
        }
        for n in x.nodes where n.role == "focal" {
            let ys = x.spec.edges.enumerated().compactMap { i, e -> Float? in
                (e.from != n.id && e.to != n.id) ? nil : DiagramRenderer.sequenceY(x.spec, i)
            }
            guard let lo = ys.min(), let hi = ys.max() else { continue }
            let cx = (n.x + n.w / 2) * U
            let r = RectF(cx - DiagramRenderer.ACTIVATION, lo - 14, cx + DiagramRenderer.ACTIVATION, hi + 14)
            fill(x, x.pal.editorial ? blend(x.pal.paper, x.pal.accent, 0.12) : alpha(x.pal.accent, 0.2)); x.c.drawRect(r, x.p)
            stroke(x, x.pal.accent, x.line); x.c.drawRect(r, x.p)
        }
    }

    // MARK: yolculuk

    private static func journey(_ x: Ctx) {
        let stages = x.nodes.filter { $0.role == "stage" }
        let moods = x.nodes.filter { $0.role == "mood" }
        let actions = x.nodes.filter { $0.role == "action" || $0.role == nil }
        let m = max(1, max(stages.count, max(moods.count, actions.count)))
        let left = 0.1 * U
        let colW = (0.99 * U - left) / Float(m)
        fill(x, alpha(x.pal.editorial ? x.pal.soft : x.pal.rule, 0.7))
        x.c.drawRect(left, 0.155 * U, 0.99 * U, 0.545 * U, x.p)
        stroke(x, x.pal.rule, x.line * 0.6, dashed: true)
        x.c.drawLine(left, 0.35 * U, 0.99 * U, 0.35 * U, x.p)
        stroke(x, x.pal.rule, x.line * 0.6)
        if m > 1 { for i in 1..<m { let xx = left + colW * Float(i); x.c.drawLine(xx, 0, xx, 0.94 * U, x.p) } }
        let s = tick(x)
        let rowNames: [(String, Float)] = [("AŞAMA", 0.065), ("DUYGU", 0.35), ("EYLEM", 0.68), ("SIKINTI", 0.85)]
        for (t, y) in rowNames {
            let ls = fitSize(t, mono, s, (left - 12) / x.k, 0.06)
            drawText(x, t, 0.01 * U, y * U + ls * 0.36, mono, ls, x.pal.muted, spacing: 0.06)
        }
        // Kotlin sortedBy kararlıdır
        let pts = moods.map { DiagramPainter.dotCenter($0) }.enumerated().sorted { $0.element.0 != $1.element.0 ? $0.element.0 < $1.element.0 : $0.offset < $1.offset }.map(\.element)
        if pts.count >= 2 {
            let path = APath()
            path.moveTo(pts[0].0, pts[0].1)
            for i in 0..<(pts.count - 1) {
                let p0 = pts[max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[min(pts.count - 1, i + 2)]
                path.cubicTo(p1.0 + (p2.0 - p0.0) / 6, p1.1 + (p2.1 - p0.1) / 6,
                             p2.0 - (p3.0 - p1.0) / 6, p2.1 - (p3.1 - p1.1) / 6,
                             p2.0, p2.1)
            }
            stroke(x, x.pal.ink, x.line * 1.3); x.c.drawPath(path, x.p)
        }
    }

    // MARK: dörtlü konum ve Wardley

    private static func arrowHead(_ x: Ctx, _ tx: Float, _ ty: Float, _ fx: Float, _ fy: Float, _ color: UInt32) {
        guard let d = DiagramPainter.visualDir(x, fx, fy, tx, ty) else { return }
        fill(x, color); x.c.drawPath(DiagramPainter.headPath(x, tx, ty, d, 13, 5), x.p)
    }

    private static func quadrant(_ x: Ctx) {
        let P = x.pal
        fill(x, alpha(P.accent, 0.05)); x.c.drawRect(0.03 * U, 0.08 * U, 0.5 * U, 0.5 * U, x.p)
        stroke(x, P.ink, x.line)
        x.c.drawLine(0.5 * U, 0.95 * U, 0.5 * U, 0.075 * U, x.p)
        arrowHead(x, 0.5 * U, 0.07 * U, 0.5 * U, 0.5 * U, P.ink)
        stroke(x, P.ink, x.line)
        x.c.drawLine(0.03 * U, 0.5 * U, 0.85 * U, 0.5 * U, x.p)
        arrowHead(x, 0.855 * U, 0.5 * U, 0.5 * U, 0.5 * U, P.ink)
        let s = tick(x) * 0.9
        let names: [(String, Float, Float)] = [("HIZLI KAZANÇ", 0.05, 0.12), ("BÜYÜK İŞ", 0.52, 0.12), ("DOLGU İŞLER", 0.05, 0.94), ("ZAMAN KAYBI", 0.52, 0.94)]
        for (i, item) in names.enumerated() {
            let ls = fitSize(item.0, mono, s, (0.4 * U) / x.k, 0.08)
            drawText(x, item.0, item.1 * U, item.2 * U, mono, ls, i == 0 ? P.accent : P.muted, spacing: 0.08)
        }
    }

    private static let wardleyStages = ["DOĞUŞ", "ÖZEL YAPIM", "ÜRÜN", "EMTİA"]

    private static func wardley(_ x: Ctx) {
        let P = x.pal
        let l = 0.03 * U, r = 0.985 * U, b = 0.92 * U
        stroke(x, P.rule, x.line * 0.6, dashed: true)
        for i in 1...3 { let xx = l + (r - l) * Float(i) / 4; x.c.drawLine(xx, 0.03 * U, xx, b, x.p) }
        stroke(x, P.ink, x.line)
        x.c.drawLine(l, b, l, 0.035 * U, x.p); arrowHead(x, l, 0.03 * U, l, b, P.ink)
        stroke(x, P.ink, x.line)
        x.c.drawLine(l, b, r - 6, b, x.p); arrowHead(x, r, b, l, b, P.ink)
        let s = tick(x) * 0.85
        for (i, t) in wardleyStages.enumerated() {
            let cx = l + (r - l) * (Float(i) + 0.5) / 4
            let ls = fitSize(t, mono, s, ((r - l) / 4 - 8) / x.k, 0.06)
            drawText(x, t, cx - measure(t, mono, ls, 0.06) * x.k / 2, b + ls * 1.6, mono, ls, P.muted, spacing: 0.06)
        }
        drawText(x, "GÖRÜNÜRLÜK", l + 8 * x.k, 0.045 * U, mono, s, P.muted, spacing: 0.06)
    }

    private static func wardleyMoves(_ x: Ctx) {
        for n in x.nodes where n.role == "moving" && n.box == .DOT {
            let (cx, cy) = DiagramPainter.dotCenter(n)
            let len = 0.15 * U
            let end = min(cx + len, 0.97 * U)
            let yy = cy + n.h * U * 0.45
            stroke(x, x.pal.accent, x.line * 1.2, dashed: true)
            x.c.drawLine(cx + 14 * x.k, yy, end - 10 * x.k, yy, x.p)
            arrowHead(x, end, yy, cx, yy, x.pal.accent)
        }
    }

    // MARK: zaman çizelgesi, balık kılçığı, hikâye haritası

    private static func timeline(_ x: Ctx) {
        if x.nodes.isEmpty { return }
        let axisY = 0.47 * U
        let P = x.pal
        stroke(x, P.ink, x.line * 1.1)
        x.c.drawLine(0.02 * U, axisY, 0.975 * U, axisY, x.p)
        arrowHead(x, 0.985 * U, axisY, 0.02 * U, axisY, P.ink)
        for n in x.nodes {
            let cx = (n.x + n.w / 2) * U
            let above = n.y + n.h / 2 < 0.47
            stroke(x, P.rule, x.line * 0.8)
            x.c.drawLine(cx, axisY, cx, above ? (n.y + n.h) * U : n.y * U, x.p)
            let r: Float = 7
            let o = RectF(cx - r * x.k, axisY - r, cx + r * x.k, axisY + r)
            fill(x, n.role == "focal" ? P.accent : paper(x)); x.c.drawOval(o, x.p)
            stroke(x, n.role == "focal" ? P.accent : P.ink, x.line); x.c.drawOval(o, x.p)
        }
    }

    private static func fishbone(_ x: Ctx) {
        guard let head = x.nodes.first else { return }
        let color = x.pal.coverage ? BLACK : x.pal.edge
        let spineY = (head.y + head.h / 2) * U
        stroke(x, color, x.line * 1.6)
        x.c.drawLine(0.02 * U, spineY, head.x * U, spineY, x.p)
        let tail = APath()
        tail.moveTo(0.02 * U, spineY); tail.lineTo(0, spineY - 0.06 * U)
        tail.moveTo(0.02 * U, spineY); tail.lineTo(0, spineY + 0.06 * U)
        x.c.drawPath(tail, x.p)
        stroke(x, x.pal.editorial ? x.pal.muted : color, x.line)
        for cause in x.nodes.dropFirst() {
            let top = cause.y + cause.h / 2 < head.y + head.h / 2
            let sx = (cause.x + cause.w / 2) * U
            let sy = (top ? cause.y + cause.h : cause.y) * U
            x.c.drawLine(sx, sy, sx + 0.1 * U, spineY, x.p)
        }
    }

    private static func backbone(_ x: Ctx) {
        let acts = x.nodes.filter { $0.role == "activity" }
        if acts.count < 2 { return }
        let y = (acts.map { $0.y + $0.h }.max()! + 0.02) * U
        let l = acts.map(\.x).min()! * U, r = acts.map { $0.x + $0.w }.max()! * U
        stroke(x, x.pal.muted, x.line * 0.8)
        x.c.drawLine(l, y, r - 8, y, x.p)
        arrowHead(x, r, y, l, y, x.pal.muted)
    }

    // MARK: patlatılmış ve aksonometrik

    /// Levhanın köşeleri: üst yüz (sol-üst, sağ-üst, sağ-alt, sol-alt) ve kalınlık.
    private struct Slab { let tl: (Float, Float), tr: (Float, Float), br: (Float, Float), bl: (Float, Float), t: Float }

    private static let katRegex = try! NSRegularExpression(pattern: "(\\d+)\\s*kat")

    private static func slabOf(_ x: Ctx, _ n: DiagramNode) -> Slab {
        let r = rectOf(n)
        let sk = r.width() * 0.22
        let t: Float, th: Float
        if x.kind == .AXONOMETRIC {
            let lower = n.text.lowercased(with: DiagramPainter.TR) as NSString
            var levels = 1
            if let m = katRegex.firstMatch(in: lower as String, range: NSRange(location: 0, length: lower.length)) {
                levels = Int(lower.substring(with: m.range(at: 1))) ?? 1
            }
            t = r.height() * min(0.12 + 0.1 * Float(min(levels, 5)), 0.6)
            th = r.height() - t
        } else {
            th = r.height() * 0.55
            t = r.height() * 0.16
        }
        return Slab(tl: (r.left + sk, r.top), tr: (r.right, r.top), br: (r.right - sk, r.top + th), bl: (r.left, r.top + th), t: t)
    }

    private static func quad(_ a: (Float, Float), _ b: (Float, Float), _ c: (Float, Float), _ d: (Float, Float)) -> APath {
        let p = APath()
        p.moveTo(a.0, a.1); p.lineTo(b.0, b.1); p.lineTo(c.0, c.1); p.lineTo(d.0, d.1); p.close()
        return p
    }

    static func drawSlab(_ x: Ctx, _ n: DiagramNode, _ look: Look, _ index: Int) {
        let s = slabOf(x, n)
        let top = look.fill ?? paper(x)
        let front = blend(top, x.pal.ink, 0.08)
        let side = blend(top, x.pal.ink, 0.17)
        let strokeColor = look.stroke ?? x.pal.ink
        let topFace = quad(s.tl, s.tr, s.br, s.bl)
        let frontFace = quad(s.bl, s.br, (s.br.0, s.br.1 + s.t), (s.bl.0, s.bl.1 + s.t))
        let sideFace = quad(s.br, s.tr, (s.tr.0, s.tr.1 + s.t), (s.br.0, s.br.1 + s.t))
        for (path, color) in [(topFace, top), (frontFace, front), (sideFace, side)] {
            fill(x, color); x.c.drawPath(path, x.p)
            DiagramPainter.strokePaint(x, x.pal.coverage ? BLACK : strokeColor, look.width, look.dashed); x.c.drawPath(path, x.p)
        }
        if x.kind == .AXONOMETRIC && x.labels {
            let r = RectF(s.bl.0 + (s.br.0 - s.bl.0) * 0.12, s.tl.1, s.br.0 - (s.br.0 - s.bl.0) * 0.02, s.bl.1)
            DiagramPainter.drawBlock(x, n.text, r, look, .CENTER, maxSize: x.base * 0.6)
        }
    }

    private static func sortedSlabs(_ x: Ctx) -> [DiagramNode] {
        x.nodes.filter { $0.box == .SLAB }.enumerated().sorted { $0.element.y != $1.element.y ? $0.element.y < $1.element.y : $0.offset < $1.offset }.map(\.element)
    }

    private static func explodedAxis(_ x: Ctx) {
        let slabs = sortedSlabs(x).map { slabOf(x, $0) }
        stroke(x, x.pal.muted, x.line * 0.7, dashed: true)
        if slabs.count < 2 { return }
        for i in 0..<(slabs.count - 1) {
            let a = slabs[i], b = slabs[i + 1]
            x.c.drawLine(a.bl.0, a.bl.1 + a.t, b.bl.0, b.bl.1, x.p)
            x.c.drawLine(a.br.0, a.br.1 + a.t, b.br.0, b.br.1, x.p)
            x.c.drawLine(a.tr.0, a.tr.1 + a.t, b.tr.0, b.tr.1, x.p)
        }
    }

    private static func explodedLabels(_ x: Ctx) {
        let lx = 0.66 * U
        for (i, n) in sortedSlabs(x).enumerated() {
            let s = slabOf(x, n)
            let ax = (s.tr.0 + s.br.0) / 2, ay = (s.tr.1 + s.br.1) / 2
            stroke(x, x.pal.muted, x.line * 0.7)
            x.c.drawLine(ax, ay, lx - 6 * x.k, ay, x.p)
            fill(x, n.role == "focal" ? x.pal.accent : x.pal.ink)
            x.c.drawOval(RectF(ax - 4 * x.k, ay - 4, ax + 4 * x.k, ay + 4), x.p)
            if !x.labels { continue }
            let look = DiagramPainter.lookOf(x, x.nodes.firstIndex(of: n) ?? -1, n)
            let text = "[\(String(format: "%02d", i + 1))]\n" + n.text
            let h = rectOf(n).height()
            DiagramPainter.drawBlock(x, text, RectF(lx, ay - h * 0.5, 0.99 * U, ay + h * 0.5), look, .LEFT, maxSize: x.base * 0.7)
        }
    }

    private static func isoGround(_ x: Ctx) {
        let f = x.spec.frame
        let l = f.left * U, t = f.top * U, r = f.right * U, b = f.bottom * U
        let save = x.c.save()
        x.c.clipRect(l, t, r, b)
        stroke(x, alpha(x.pal.rule, 0.6), x.line * 0.5)
        let step = 0.08 * U
        let slope = 0.5 / x.k
        let span = (r - l) * slope
        var c = t - span
        while c < b + span {
            x.c.drawLine(l, c, r, c + (r - l) * slope, x.p)
            x.c.drawLine(l, c + (r - l) * slope, r, c, x.p)
            c += step
        }
        x.c.restoreToCount(save)
    }
}
