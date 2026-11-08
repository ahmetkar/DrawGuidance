import CoreGraphics
import Foundation

/// v28 diyagram çizimi (Android `DiagramPainter`, editoryal tarz): kağıt zemin, ince mürekkep çizgiler, rolüne göre
/// ayrışan kutular, yuvarlatılmış dirsekli oklar ve dolu ok uçları, kağıtla maskelenmiş eşaralıklı ok yazıları.
///
/// Diyagram tasarım alanı 0..[U]; [Ctx.k] yatay sıkıştırma katsayısıdır (yazı, nokta ve ok uçları bozulmaz).
enum DiagramPainter {
    static let U: Float = 1000
    static let TR = Locale(identifier: "tr_TR")

    static let BLACK: UInt32 = 0xFF00_0000
    static let WHITE: UInt32 = 0xFFFF_FFFF
    static let TRANSPARENT: UInt32 = 0

    static let sans = DiagramFace.sans
    static let medium = DiagramFace.medium
    static let mono = DiagramFace.mono

    // MARK: - renkler

    struct Pal {
        let editorial: Bool
        let coverage: Bool
        let paper: UInt32
        let ink: UInt32
        let muted: UInt32
        let soft: UInt32
        let rule: UInt32
        let accent: UInt32
        let link: UInt32
        let edge: UInt32
        let theme: DiagramThemes.Theme?
        let styleInk: UInt32
        let styleFill: UInt32?
        let fillOpacity: Float
        let line: Float
        /// Koyu dolgunun üstündeki yazının rengi.
        var onInk: UInt32
        /// v29 saydam zemin: kağıt kartı ve maskeler yok.
        var clear: Bool { editorial && paper >> 24 == 0 }
    }

    static func hex(_ s: String) -> UInt32 { ArgbColor.hex(s) }

    static func calpha(_ c: UInt32) -> Int { Int(c >> 24 & 0xFF) }

    static func alpha(_ c: UInt32, _ a: Float) -> UInt32 {
        let na = ktInt(Float(calpha(c)) * clampF(a, 0, 1))
        return (c & 0x00FF_FFFF) | UInt32(clampI(na, 0, 255)) << 24
    }

    static func argb(_ a: Int, _ r: Int, _ g: Int, _ b: Int) -> UInt32 {
        UInt32(a & 0xFF) << 24 | UInt32(r & 0xFF) << 16 | UInt32(g & 0xFF) << 8 | UInt32(b & 0xFF)
    }

    static func blend(_ a: UInt32, _ b: UInt32, _ t: Float) -> UInt32 {
        let u = clampF(t, 0, 1)
        // v29 saydam kağıt (renkli ama görünmez): karışım [b] renginin saydamlaştırılmışı olur
        if calpha(a) == 0 && (a & 0xFFFFFF) != 0 { return alpha(b, u) }
        func ch(_ x: Int, _ y: Int) -> Int { clampI(ktInt(Float(x) + Float(y - x) * u), 0, 255) }
        return argb(ch(calpha(a), calpha(b)), ch(ArgbColor.red(a), ArgbColor.red(b)), ch(ArgbColor.green(a), ArgbColor.green(b)),
                    ch(ArgbColor.blue(a), ArgbColor.blue(b)))
    }

    static func darker(_ color: UInt32) -> UInt32 {
        argb(255, ktInt(Float(ArgbColor.red(color)) * 0.62), ktInt(Float(ArgbColor.green(color)) * 0.62), ktInt(Float(ArgbColor.blue(color)) * 0.62))
    }

    static func palette(_ spec: DiagramSpec, _ coverage: Bool) -> Pal {
        let theme = DiagramThemes.find(spec.theme)
        let style = spec.style
        let background = DiagramThemes.hasBackground(spec.theme)
        let styleInk: UInt32 = {
            if let t = theme, !background, t.editorial == nil, let s = t.stroke { return hex(s) }
            return hex(style.strokeColorHex)
        }()
        let styleFill: UInt32? = background ? style.fillColorHex.map { hex($0) } : nil
        let el = DiagramThemes.editorialLight
        if coverage {
            let b = BLACK
            return Pal(editorial: false, coverage: true, paper: TRANSPARENT, ink: b, muted: b, soft: b, rule: b, accent: b, link: b, edge: b,
                       theme: nil, styleInk: b, styleFill: b, fillOpacity: 1, line: max(1, style.strokeWidth * 2.2), onInk: TRANSPARENT)
        }
        if let e = theme?.editorial {
            let ink = hex(e.ink)
            let paper = hex(e.paper)
            return Pal(editorial: true, coverage: false, paper: e.clear ? paper & 0x00FF_FFFF : paper, ink: ink, muted: hex(e.muted),
                       soft: e.clear ? alpha(ink, 0.07) : hex(e.soft), rule: hex(e.rule), accent: hex(e.accent), link: hex(e.link), edge: ink,
                       theme: theme, styleInk: styleInk, styleFill: styleFill, fillOpacity: style.fillOpacity,
                       line: clampF(style.strokeWidth * 0.6, 1.4, 8), onInk: paper)
        }
        let ink = theme?.edge.map { hex($0) } ?? styleInk
        return Pal(editorial: false, coverage: false, paper: TRANSPARENT, ink: ink, muted: alpha(ink, 0.62),
                   soft: theme?.fill(0).map { hex($0) } ?? alpha(ink, 0.08), rule: alpha(ink, 0.3), accent: hex(el.accent), link: hex(el.link),
                   edge: ink, theme: theme, styleInk: styleInk, styleFill: styleFill, fillOpacity: style.fillOpacity,
                   line: max(1, style.strokeWidth * 2.2), onInk: TRANSPARENT)
    }

    /// Bir çizimin bağlamı.
    final class Ctx {
        let c: ACanvas
        let spec: DiagramSpec
        let pal: Pal
        let k: Float
        let labels: Bool
        var p = APaint()
        let kind: DiagramKind
        let nodes: [DiagramNode]
        let byId: [String: DiagramNode]
        let base: Float
        let line: Float
        var label: Float { clampF(base * 0.54, 10, 28) }
        lazy var ribbons: [Int: DiagramCatalog.Ribbon] = kind == .SANKEY ? DiagramCatalog.ribbons(spec.nodes, spec.edges) : [:]

        init(_ c: ACanvas, _ spec: DiagramSpec, _ pal: Pal, _ k: Float, _ labels: Bool) {
            self.c = c; self.spec = spec; self.pal = pal; self.k = k; self.labels = labels
            kind = spec.kind
            nodes = spec.nodes
            var m: [String: DiagramNode] = [:]
            for n in spec.nodes { m[n.id] = n }
            byId = m
            base = spec.fontSize * U
            line = pal.line
        }
    }

    // MARK: - giriş

    static func paint(_ canvas: ACanvas, _ spec: DiagramSpec, _ coverage: Bool, _ k: Float, _ labels: Bool) {
        let pal = palette(spec, coverage)
        let x = Ctx(canvas, spec, pal, clampF(k, 0.2, 5), labels)
        if pal.editorial && !pal.clear {
            x.p.reset(); x.p.style = .FILL; x.p.color = pal.paper
            let r: Float = 14
            canvas.drawRoundRect(paperRect(spec), r * x.k, r, x.p)
        }
        DiagramDecor.before(x)
        drawContainers(x)
        drawEdges(x)
        drawNodes(x)
        DiagramDecor.after(x)
    }

    /// Kağıt zemin: v28 türlerinde diyagramın tamamı; eski türlerde adımları saran kart.
    static func paperRect(_ spec: DiagramSpec) -> RectF {
        let f = spec.frame
        let full = RectF(f.left * U, f.top * U, f.right * U, f.bottom * U)
        if DiagramCatalog.isNew(spec.kind) || spec.nodes.isEmpty { return full }
        let pad = 0.035 * U
        var l = spec.nodes.map(\.x).min()! * U - pad, t = spec.nodes.map(\.y).min()! * U - pad
        var r = spec.nodes.map { $0.x + $0.w }.max()! * U + pad, b = spec.nodes.map { $0.y + $0.h }.max()! * U + pad
        var byId: [String: DiagramNode] = [:]
        for n in spec.nodes { byId[n.id] = n }
        for (i, e) in spec.edges.enumerated() {
            guard let from = byId[e.from], let to = byId[e.to] else { continue }
            for pt in DiagramRenderer.routeOf(spec, i, e, from, to) {
                l = min(l, pt.x - pad * 0.6); r = max(r, pt.x + pad * 0.6); t = min(t, pt.y - pad * 0.6); b = max(b, pt.y + pad * 0.6)
            }
        }
        switch spec.kind {
        case .FISHBONE: l = min(l, -0.01 * U)
        case .TIMELINE: l = min(l, 0); r = max(r, U)
        default: break
        }
        l = max(l, full.left); t = max(t, full.top); r = min(r, full.right); b = min(b, full.bottom)
        return (r - l < 20 || b - t < 20) ? full : RectF(l, t, r, b)
    }

    // MARK: - düğümler

    final class Look {
        var fill: UInt32?
        var stroke: UInt32?
        var width: Float
        var text: UInt32
        var sub: UInt32
        var eyebrow: UInt32
        var dashed = false
        var bold = false
        var bar: UInt32? = nil
        var strike = false
        var fillAlpha: Float = 1
        var shadow = false

        init(_ fill: UInt32?, _ stroke: UInt32?, _ width: Float, _ text: UInt32, _ sub: UInt32, _ eyebrow: UInt32) {
            self.fill = fill; self.stroke = stroke; self.width = width; self.text = text; self.sub = sub; self.eyebrow = eyebrow
        }
    }

    private static let containers: Set<DiagramBox> = [.GROUP, .LANE, .COLUMN]
    private static let besideFill: Set<DiagramBox> = [.DOT, .BAR, .LABEL, .CELL]

    private static func drawContainers(_ x: Ctx) {
        var lane = 0
        for (i, n) in x.nodes.enumerated() where containers.contains(n.box) {
            let look = lookOf(x, i, n)
            switch n.box {
            case .GROUP: drawGroup(x, n, look)
            case .LANE: drawLane(x, n, look, lane); lane += 1
            default: drawColumn(x, n, look)
            }
        }
    }

    private static func drawNodes(_ x: Ctx) {
        for (i, n) in x.nodes.enumerated() where !containers.contains(n.box) {
            let look = lookOf(x, i, n)
            switch n.box {
            case .TABLE: drawTable(x, n, look)
            case .CARD: drawCard(x, n, look)
            case .DOT: drawDot(x, n, look)
            case .LABEL: drawLabel(x, n, look)
            case .BAR: drawBar(x, n, look)
            case .CELL: drawCell(x, n, look)
            case .SLAB: DiagramDecor.drawSlab(x, n, look, i)
            default: drawShape(x, i, n, look)
            }
        }
    }

    static func lookOf(_ x: Ctx, _ index: Int, _ n: DiagramNode) -> Look {
        let pal = x.pal
        let look = pal.editorial ? editorialLook(x, index, n) : themedLook(x, index, n)
        if let f = n.fillColorHex { look.fill = hex(f); look.fillAlpha = 1 }
        if let s = n.strokeColorHex { look.stroke = hex(s) }
        if let t = n.textColorHex { look.text = hex(t); look.sub = alpha(look.text, 0.75) }
        if !pal.coverage && n.textColorHex == nil && !besideFill.contains(n.box) {
            let fixed = DiagramContrast.readable(look.text, look.fill, fillAlpha: look.fillAlpha)
            if fixed != look.text { look.text = fixed; look.sub = alpha(fixed, 0.75); look.eyebrow = alpha(fixed, 0.7) }
        }
        if pal.coverage {
            let b = BLACK
            look.fill = b; look.stroke = b; look.text = b; look.sub = b; look.eyebrow = b; look.bar = nil; look.fillAlpha = 1; look.shadow = false
        }
        return look
    }

    private static func lastValue(_ text: String) -> Float? { DiagramCatalog.parse(text).values.last }

    private static func editorialLook(_ x: Ctx, _ index: Int, _ n: DiagramNode) -> Look {
        let P = x.pal
        let l = Look(P.paper, P.ink, x.line, P.ink, P.muted, P.muted)
        switch n.box {
        case .GROUP: l.fill = nil; l.stroke = P.rule; l.text = P.muted
        case .COLUMN: l.fill = P.soft; l.stroke = nil; l.text = P.muted
        case .CARD: l.stroke = P.rule
        case .LABEL: l.fill = nil; l.stroke = nil
        case .DOT: l.fill = P.ink; l.stroke = nil
        case .BAR: l.fill = blend(P.paper, P.ink, 0.62); l.stroke = nil
        default: break
        }
        switch x.kind {
        case .PYRAMID, .FUNNEL: l.fill = blend(P.paper, P.ink, 0.03 + 0.045 * Float(min(index, 6)))
        case .VENN:
            let s = [P.accent, P.link, P.ink][index % 3]
            l.fill = s; l.fillAlpha = 0.13; l.stroke = s
        case .SANKEY:
            switch n.role {
            case "c1": l.fill = blend(P.paper, P.ink, 0.5)
            case "c2": l.fill = blend(P.paper, P.ink, 0.32)
            default: l.fill = P.ink
            }
        case .TREEMAP:
            let mx = max(x.nodes.map { lastValue($0.text) ?? 0 }.max() ?? 1, 1e-3)
            let v = lastValue(n.text) ?? 0
            l.fill = blend(P.paper, P.ink, 0.05 + 0.22 * clampF(v / mx, 0, 1)); l.stroke = P.paper; l.width = x.line * 1.6
        default: break
        }
        switch n.role {
        case "focal":
            l.stroke = P.accent; l.width = x.line * 1.7; l.bold = true; l.eyebrow = P.accent
            if n.box == .DOT || n.box == .BAR { l.fill = P.accent }
            else if n.box == .LABEL { l.text = P.accent }
            else if n.box == .GROUP { l.text = P.accent }
            else if l.fill != nil { l.fill = blend(P.paper, P.accent, 0.09) }
        case "store": l.fill = P.soft
        case "external": l.stroke = P.muted; l.dashed = true
        case "input": l.fill = P.soft; l.stroke = P.rule
        case "optional", "waiting", "outer": l.stroke = P.muted; l.dashed = true
        case "security":
            l.fill = blend(P.paper, P.link, 0.07); l.stroke = P.link; l.eyebrow = P.link
            if n.box == .CARD { l.bar = P.link }
        case "hub", "activity":
            l.fill = P.ink; l.stroke = P.ink; l.text = P.onInk; l.sub = alpha(P.onInk, 0.72); l.eyebrow = l.sub; l.bold = true
        case "boundary": l.dashed = true; l.stroke = P.muted
        case "done": l.fill = P.soft; l.stroke = P.rule; l.text = P.muted
        case "blocked": l.stroke = P.accent; l.bar = P.accent
        case "cold": l.fill = blend(P.paper, P.link, 0.07); l.stroke = P.link; l.dashed = true
        case "added": l.fill = blend(P.paper, P.link, 0.08); l.stroke = P.link; l.width = x.line * 1.5
        case "removed": l.fill = P.paper; l.stroke = P.muted; l.dashed = true; l.text = P.muted; l.strike = true
        case "changed": l.fill = blend(P.paper, P.accent, 0.08); l.stroke = P.accent; l.width = x.line * 1.5
        case "moving": l.fill = P.accent
        case "pain": l.stroke = P.accent; l.dashed = true; l.text = P.accent; l.fill = blend(P.paper, P.accent, 0.06)
        default: break
        }
        return l
    }

    private static func themedLook(_ x: Ctx, _ index: Int, _ n: DiagramNode) -> Look {
        let pal = x.pal
        let theme = pal.theme
        let themeFill = theme?.fill(index).map { hex($0) }
        let fill = themeFill ?? pal.styleFill
        let ink: UInt32 = themeFill.map { f in theme?.stroke.map { hex($0) } ?? darker(f) } ?? pal.styleInk
        let text = theme?.text.map { hex($0) } ?? ink
        let l = Look(fill, ink, x.line, text, alpha(text, 0.75), alpha(text, 0.7))
        l.fillAlpha = themeFill != nil ? 1 : clampF(pal.fillOpacity, 0, 1)
        l.shadow = theme != nil && theme!.shadow && x.kind != .VENN
        switch n.box {
        case .GROUP, .LANE, .COLUMN: l.fillAlpha *= 0.25; l.shadow = false
        case .LABEL: l.fill = nil; l.stroke = nil; l.shadow = false
        case .DOT: l.fill = ink; l.stroke = nil; l.shadow = false
        case .BAR: l.fill = themeFill ?? ink; l.stroke = nil; l.shadow = false; l.fillAlpha = 1
        case .CELL: l.shadow = false
        default: break
        }
        if x.kind == .VENN && fill != nil { l.fillAlpha = 0.45 }
        switch n.role {
        case "focal":
            l.width = x.line * 1.7; l.bold = true
            if n.box == .DOT || n.box == .BAR { l.fill = pal.accent }
        case "boundary", "external", "waiting", "optional", "outer", "cold": l.dashed = true
        case "removed": l.dashed = true; l.strike = true
        case "blocked": l.bar = pal.accent
        default: break
        }
        return l
    }

    /// Kutu biçiminin yolu.
    static func boxPath(_ box: DiagramBox, _ r: RectF, _ inverted: Bool, _ k: Float, _ cornerRadius: Float) -> APath {
        let path = APath()
        switch box {
        case .RECT, .CELL, .BAR, .LABEL: path.addRect(r)
        case .ROUNDED, .TABLE, .GROUP, .LANE, .COLUMN, .CARD, .SLAB:
            let rr = min(cornerRadius, min(r.width() / k, r.height()) / 2)
            path.addRoundRect(r, rr * k, rr)
        case .PILL: path.addRoundRect(r, r.height() / 2 * k, r.height() / 2)
        case .CIRCLE, .ELLIPSE, .DOT: path.addOval(r)
        case .DIAMOND:
            path.moveTo(r.centerX(), r.top); path.lineTo(r.right, r.centerY())
            path.lineTo(r.centerX(), r.bottom); path.lineTo(r.left, r.centerY()); path.close()
        case .CYLINDER:
            let e = r.height() * 0.16
            path.moveTo(r.left, r.top + e)
            path.lineTo(r.left, r.bottom - e)
            path.arcTo(RectF(r.left, r.bottom - 2 * e, r.right, r.bottom), 180, -180)
            path.lineTo(r.right, r.top + e)
            path.arcTo(RectF(r.left, r.top, r.right, r.top + 2 * e), 0, -180)
            path.close()
        case .NOTE:
            let c = min(r.width(), r.height()) * 0.22
            path.moveTo(r.left, r.top); path.lineTo(r.right - c, r.top); path.lineTo(r.right, r.top + c)
            path.lineTo(r.right, r.bottom); path.lineTo(r.left, r.bottom); path.close()
        case .HEXAGON:
            let c = min(r.width() * 0.25, r.height() * 0.5)
            path.moveTo(r.left + c, r.top); path.lineTo(r.right - c, r.top); path.lineTo(r.right, r.centerY())
            path.lineTo(r.right - c, r.bottom); path.lineTo(r.left + c, r.bottom); path.lineTo(r.left, r.centerY()); path.close()
        case .PARALLELOGRAM:
            let c = min(r.width() * 0.2, r.height() * 0.6)
            path.moveTo(r.left + c, r.top); path.lineTo(r.right, r.top); path.lineTo(r.right - c, r.bottom)
            path.lineTo(r.left, r.bottom); path.close()
        case .DOCUMENT:
            let wave = r.height() * 0.12
            path.moveTo(r.left, r.top); path.lineTo(r.right, r.top); path.lineTo(r.right, r.bottom - wave)
            path.cubicTo(r.right - r.width() * 0.25, r.bottom - wave * 2.2, r.left + r.width() * 0.45, r.bottom + wave * 1.2, r.left, r.bottom - wave * 0.4)
            path.close()
        case .CLOUD:
            let cx = r.centerX(), cy = r.centerY(), rx = r.width() / 2, ry = r.height() / 2
            let bumps = 9
            for i in 0...(bumps * 8) {
                let t = Double(i) / Double(Float(bumps * 8)) * 2 * Double.pi
                let g = 0.86 + 0.14 * abs(sin(t * Double(bumps) / 2))
                let px = cx + Float(Double(rx) * g * cos(t)), py = cy + Float(Double(ry) * g * sin(t))
                if i == 0 { path.moveTo(px, py) } else { path.lineTo(px, py) }
            }
            path.close()
        case .TRAPEZOID:
            let c = min(r.width() * 0.25, r.height() * 0.5)
            if inverted {
                path.moveTo(r.left, r.top); path.lineTo(r.right, r.top); path.lineTo(r.right - c, r.bottom)
                path.lineTo(r.left + c, r.bottom); path.close()
            } else {
                path.moveTo(r.left + c, r.top); path.lineTo(r.right - c, r.top); path.lineTo(r.right, r.bottom)
                path.lineTo(r.left, r.bottom); path.close()
            }
        case .TRIANGLE:
            path.moveTo(r.centerX(), r.top); path.lineTo(r.right, r.bottom); path.lineTo(r.left, r.bottom); path.close()
        case .CHEVRON:
            let c = min(r.width() * 0.22, r.height() * 0.5)
            path.moveTo(r.left, r.top); path.lineTo(r.right - c, r.top); path.lineTo(r.right, r.centerY())
            path.lineTo(r.right - c, r.bottom); path.lineTo(r.left, r.bottom); path.lineTo(r.left + c, r.centerY()); path.close()
        }
        return path
    }

    static func rectOf(_ n: DiagramNode) -> RectF { RectF(n.x * U, n.y * U, (n.x + n.w) * U, (n.y + n.h) * U) }

    /// Yolu doldurur ve kenarını çizer (gölge, saydamlık, kesik kenar).
    static func fillStroke(_ x: Ctx, _ path: APath, _ look: Look) {
        if look.shadow, let fill = look.fill, !x.pal.coverage {
            _ = fill
            x.p.reset(); x.p.style = .FILL; x.p.color = 0x2200_0000
            let save = x.c.save()
            x.c.translate(U * 0.006, U * 0.009)
            x.c.drawPath(path, x.p)
            x.c.restoreToCount(save)
        }
        if let fill = look.fill {
            x.p.reset(); x.p.style = .FILL; x.p.color = fill
            x.p.alpha = clampI(ktInt(Float(calpha(fill)) * look.fillAlpha), 0, 255)
            x.c.drawPath(path, x.p)
        }
        guard let stroke = look.stroke else { return }
        strokePaint(x, stroke, look.width, look.dashed)
        x.c.drawPath(path, x.p)
    }

    static func strokePaint(_ x: Ctx, _ color: UInt32, _ width: Float, _ dashed: Bool, _ corner: Float = 0) {
        x.p.reset(); x.p.style = .STROKE
        x.p.strokeWidth = width; x.p.color = color; x.p.join = .round; x.p.cap = .round
        x.p.dash = dashed ? [max(5, width * 3.2), max(4, width * 2.4)] : nil
        x.p.corner = corner
        if dashed { x.p.cap = .butt }
    }

    private static func fillPaint(_ x: Ctx, _ color: UInt32) {
        x.p.reset(); x.p.style = .FILL; x.p.color = color
    }

    /// Eski kutu biçimleri (yuvarlak köşeli, elmas, silindir…).
    private static func drawShape(_ x: Ctx, _ index: Int, _ n: DiagramNode, _ look: Look) {
        let r = rectOf(n)
        let corner: Float = x.pal.editorial ? 10 : max(8, x.spec.style.cornerRadius * 3)
        let path = boxPath(n.box, r, x.kind == .FUNNEL, x.k, corner)
        fillStroke(x, path, look)
        if n.box == .CYLINDER, let s = look.stroke {
            let e = r.height() * 0.16
            strokePaint(x, s, look.width, look.dashed)
            let arc = APath(); arc.arcTo(RectF(r.left, r.top, r.right, r.top + 2 * e), 180, -180)
            x.c.drawPath(arc, x.p)
        }
        drawBarStripe(x, r, look)
        if !x.labels { return }
        var tr = textRect(n.box, r, x.kind == .FUNNEL)
        if x.kind == .VENN, let shift = vennShift(n, x.nodes) { tr.offset(shift.0 * r.width(), shift.1 * r.height()) }
        let text = x.kind == .TREEMAP ? treemapText(x, n) : n.text
        switch x.kind {
        case .TREEMAP: drawBlock(x, text, RectF(r.left + 10 * x.k, r.top + 8, r.right - 8 * x.k, r.bottom - 6), look, .LEFT, top: true)
        case .LAYERS: drawBlock(x, text, RectF(r.left + 16 * x.k, r.top, r.right - 12 * x.k, r.bottom), look, .LEFT)
        default: drawBlock(x, text, tr, look, .CENTER)
        }
    }

    /// Sol kenardaki vurgu çubuğu (engelli kart, güvenlik).
    private static func drawBarStripe(_ x: Ctx, _ r: RectF, _ look: Look) {
        guard let bar = look.bar else { return }
        fillPaint(x, bar)
        let w = 5 * x.k
        x.c.drawRoundRect(RectF(r.left, r.top, r.left + w, r.bottom), w / 2, 2, x.p)
    }

    private static func treemapText(_ x: Ctx, _ n: DiagramNode) -> String {
        let total = Float(x.nodes.reduce(0.0) { $0 + Double(lastValue($1.text) ?? 0) })
        guard let v = lastValue(n.text) else { return n.text }
        let name = DiagramCatalog.label(x.kind, n.text)
        let pct: Float = total > 0 ? v / total * 100 : 0
        return "\(name)\n%\(fmt(pct))"
    }

    /// Kutu biçiminin içinde yazının sığacağı dikdörtgen.
    static func textRect(_ box: DiagramBox, _ r: RectF, _ inverted: Bool) -> RectF {
        switch box {
        case .TRIANGLE:
            return RectF(r.centerX() - r.width() * 0.3, r.top + r.height() * 0.45, r.centerX() + r.width() * 0.3, r.bottom - r.height() * 0.04)
        case .TRAPEZOID:
            let c = min(r.width() * 0.25, r.height() * 0.5)
            return RectF(r.left + c, r.top, r.right - c, r.bottom)
        case .CIRCLE, .ELLIPSE, .CLOUD:
            return RectF(r.centerX() - r.width() * 0.36, r.centerY() - r.height() * 0.36, r.centerX() + r.width() * 0.36, r.centerY() + r.height() * 0.36)
        case .DIAMOND:
            return RectF(r.centerX() - r.width() * 0.3, r.centerY() - r.height() * 0.3, r.centerX() + r.width() * 0.3, r.centerY() + r.height() * 0.3)
        case .HEXAGON, .CHEVRON, .PARALLELOGRAM:
            let c = min(r.width() * 0.22, r.height() * 0.5)
            return RectF(r.left + c, r.top, r.right - c, r.bottom)
        case .CYLINDER:
            return RectF(r.left, r.top + r.height() * 0.2, r.right, r.bottom - r.height() * 0.06)
        default:
            return r
        }
    }

    private static func vennShift(_ node: DiagramNode, _ nodes: [DiagramNode]) -> (Float, Float)? {
        if nodes.count < 2 { return nil }
        let cx = Float(nodes.reduce(0.0) { $0 + Double($1.x + $1.w / 2) } / Double(nodes.count))
        let cy = Float(nodes.reduce(0.0) { $0 + Double($1.y + $1.h / 2) } / Double(nodes.count))
        let dx = node.x + node.w / 2 - cx
        let dy = node.y + node.h / 2 - cy
        let d = hypotf(dx, dy)
        if d < 1e-4 { return nil }
        return (dx / d * 0.24, dy / d * 0.24)
    }

    // MARK: editoryal kutular

    private static func oneLineUpper(_ s: String) -> String { s.replacingOccurrences(of: "\n", with: " ").uppercased(with: TR) }

    private static func isBlank(_ s: String) -> Bool { s.allSatisfy(\.isWhitespace) }

    private static func drawGroup(_ x: Ctx, _ n: DiagramNode, _ look: Look) {
        let r = rectOf(n)
        fillStroke(x, boxPath(.GROUP, r, false, x.k, 12), look)
        if !x.labels || isBlank(n.text) { return }
        let label = oneLineUpper(n.text)
        let fitted = fitSize(label, mono, x.label, (r.width() - 24 * x.k) / x.k, 0.08)
        drawText(x, label, r.left + 12 * x.k, r.top + fitted * 1.35, mono, fitted, look.text, spacing: 0.08)
    }

    private static func drawLane(_ x: Ctx, _ n: DiagramNode, _ look: Look, _ index: Int) {
        let r = rectOf(n)
        let P = x.pal
        if P.editorial {
            fillPaint(x, index % 2 == 0 ? alpha(P.soft, 0.75) : P.paper)
            x.c.drawRect(r, x.p)
            strokePaint(x, n.role == "focal" ? P.accent : P.rule, n.role == "focal" ? x.line * 1.4 : x.line * 0.8, false)
            x.c.drawLine(r.left, r.top, r.right, r.top, x.p)
            x.c.drawLine(r.left, r.bottom, r.right, r.bottom, x.p)
        } else {
            fillStroke(x, boxPath(.RECT, r, false, x.k, 0), look)
        }
        if !x.labels || isBlank(n.text) { return }
        let gutter = min(150, r.width() * 0.15)
        let label = oneLineUpper(n.text)
        let color = n.role == "focal" ? P.accent : P.editorial ? P.muted : look.text
        let fitted = fitSize(label, mono, x.label, (gutter - 16) / x.k, 0.06)
        drawText(x, label, r.left + 12 * x.k, r.centerY() + fitted * 0.35, mono, fitted, color, spacing: 0.06)
    }

    private static let wipRegex = try! NSRegularExpression(pattern: "(\\d+)\\s*/\\s*(\\d+)")

    private static func drawColumn(_ x: Ctx, _ n: DiagramNode, _ look: Look) {
        let r = rectOf(n)
        fillStroke(x, boxPath(.COLUMN, r, false, x.k, 12), look)
        let header = n.text.replacingOccurrences(of: "\n", with: " ")
        var over = false
        let ns = header as NSString
        if let m = wipRegex.firstMatch(in: header, range: NSRange(location: 0, length: ns.length)) {
            let a = Int(ns.substring(with: m.range(at: 1))) ?? 0
            let b = Int(ns.substring(with: m.range(at: 2))) ?? Int.max
            over = a > b
        }
        if over {
            fillPaint(x, x.pal.accent)
            x.c.drawRoundRect(RectF(r.left + 10 * x.k, r.top, r.right - 10 * x.k, r.top + 5), 2.5 * x.k, 2.5, x.p)
        }
        if !x.labels || isBlank(header) { return }
        let label = header.uppercased(with: TR)
        let fitted = fitSize(label, mono, x.label, (r.width() - 24 * x.k) / x.k, 0.06)
        drawText(x, label, r.left + 12 * x.k, r.top + fitted * 1.8, mono, fitted, over ? x.pal.accent : look.text, spacing: 0.06)
    }

    private static func drawCard(_ x: Ctx, _ n: DiagramNode, _ look: Look) {
        let r = rectOf(n)
        fillStroke(x, boxPath(.CARD, r, false, x.k, 7), look)
        drawBarStripe(x, r, look)
        if !x.labels { return }
        let pad: Float = (look.bar != nil ? 14 : 10) * x.k
        let tall = x.kind == .MEDALLION
        drawBlock(x, n.text, RectF(r.left + pad, r.top + (tall ? 14 : 4), r.right - 8 * x.k, r.bottom - 4), look, .LEFT, top: tall)
        if n.role == "done" {
            let s = min(r.height() * 0.22, 14)
            strokePaint(x, x.pal.muted, x.line, false)
            let cx = r.right - 10 * x.k - s * x.k, cy = r.top + 8 + s * 0.5
            let path = APath()
            path.moveTo(cx, cy); path.lineTo(cx + s * 0.4 * x.k, cy + s * 0.4); path.lineTo(cx + s * x.k, cy - s * 0.4)
            x.c.drawPath(path, x.p)
        }
    }

    /// Nokta: geniş kutuda solda nokta ve sağında yazı; kare kutuda yalnız nokta.
    static func dotCenter(_ n: DiagramNode) -> (Float, Float) {
        let wide = n.w > n.h * 1.2
        return wide ? ((n.x + n.h * 0.35) * U, (n.y + n.h / 2) * U) : ((n.x + n.w / 2) * U, (n.y + n.h / 2) * U)
    }

    private static func oval(_ x: Ctx, _ cx: Float, _ cy: Float, _ ry: Float) -> RectF { RectF(cx - ry * x.k, cy - ry, cx + ry * x.k, cy + ry) }

    private static func drawDot(_ x: Ctx, _ n: DiagramNode, _ look: Look) {
        let wide = n.w > n.h * 1.2
        let (cx, cy) = dotCenter(n)
        let P = x.pal
        var ry = wide ? n.h * U * 0.2 : n.h * U * 0.5
        let fill = look.fill ?? P.ink
        switch x.kind {
        case .LINE:
            ry *= 0.62
            fillPaint(x, n.role == "focal" ? P.accent : P.editorial ? P.paper : WHITE)
            x.c.drawOval(oval(x, cx, cy, ry), x.p)
            strokePaint(x, n.role == "focal" ? P.accent : P.ink, x.line * 1.2, false)
            x.c.drawOval(oval(x, cx, cy, ry), x.p)
            return
        case .SCATTER:
            ry *= n.role == "focal" ? 0.62 : 0.48
            fillPaint(x, n.role == "focal" ? P.accent : alpha(P.ink, 0.72))
            x.c.drawOval(oval(x, cx, cy, ry), x.p)
            return
        case .JOURNEY:
            let v = lastValue(n.text) ?? 3
            ry *= 0.5
            fillPaint(x, v <= 2 ? P.accent : P.ink)
            x.c.drawOval(oval(x, cx, cy, ry), x.p)
            strokePaint(x, P.editorial ? P.paper : WHITE, x.line * 1.2, false)
            x.c.drawOval(oval(x, cx, cy, ry), x.p)
            return
        default: break
        }
        fillPaint(x, fill)
        x.c.drawOval(oval(x, cx, cy, ry), x.p)
        if !wide || !x.labels { return }
        let r = RectF(cx + ry * x.k + 8 * x.k, n.y * U, (n.x + n.w) * U, (n.y + n.h) * U)
        drawBlock(x, n.text, r, look, .LEFT, maxSize: x.base * 0.8)
    }

    private static func drawLabel(_ x: Ctx, _ n: DiagramNode, _ look: Look) {
        let r = rectOf(n)
        let P = x.pal
        switch n.role {
        case "chevron":
            let c = min(r.width() * 0.12, r.height() * 0.5 * x.k)
            let path = APath()
            path.moveTo(r.left, r.top); path.lineTo(r.right - c, r.top); path.lineTo(r.right, r.centerY())
            path.lineTo(r.right - c, r.bottom); path.lineTo(r.left, r.bottom); path.lineTo(r.left + c, r.centerY()); path.close()
            fillPaint(x, P.editorial ? P.soft : alpha(P.ink, 0.1))
            x.c.drawPath(path, x.p)
            strokePaint(x, P.editorial ? P.paper : WHITE, x.line * 1.4, false)
            x.c.drawPath(path, x.p)
            if x.labels { monoCentered(x, n.text, r, P.ink, x.label) }
        case "axis", "row", "col":
            if x.labels {
                let dp = x.kind == .DP_SECURITY
                if n.role == "row" { monoAligned(x, n.text, r, P.muted, x.label, .RIGHT) }
                else if n.role == "col" && dp { drawBlock(x, n.text, r, look, .CENTER, maxSize: x.base * 0.62) }
                else { monoCentered(x, n.text, r, P.muted, x.label) }
            }
        case "step":
            if x.labels { monoCentered(x, n.text, r, P.ink, x.label) }
            strokePaint(x, P.rule, x.line, false)
            x.c.drawLine(r.left + 6 * x.k, r.bottom, r.right - 6 * x.k, r.bottom, x.p)
        case "focal":
            if x.kind == .RADAR || x.kind == .POLAR || x.kind == .JOURNEY {
                if x.labels { drawBlock(x, chartLabel(x, n), r, look, .CENTER, maxSize: x.base * 0.62) }
            } else {
                if x.labels { monoCentered(x, n.text, r, P.accent, x.label) }
                if x.kind == .DATA_FLOW || r.height() < 80 {
                    strokePaint(x, P.accent, x.line * 1.6, false)
                    x.c.drawLine(r.left + 6 * x.k, r.bottom, r.right - 6 * x.k, r.bottom, x.p)
                }
            }
        case "ledger":
            if x.labels {
                let sym = n.text.first
                let color: UInt32
                switch sym {
                case "Δ": color = P.accent
                case "+": color = P.link
                case "−", "-": color = P.muted
                default: color = P.ink
                }
                drawBlock(x, n.text, r, Look(nil, nil, x.line, color, P.muted, P.muted), .LEFT, maxSize: x.base * 0.8)
            }
        case "action":
            if x.labels { drawBlock(x, n.text, r, Look(nil, nil, x.line, P.muted, P.muted, P.muted), .CENTER, maxSize: x.base * 0.66) }
        default:
            if x.labels {
                let chart = x.kind == .RADAR || x.kind == .POLAR
                drawBlock(x, chartLabel(x, n), r, look, .CENTER, maxSize: (chart || x.kind == .JOURNEY) ? x.base * 0.66 : x.base)
            }
        }
    }

    private static func chartLabel(_ x: Ctx, _ n: DiagramNode) -> String {
        (x.kind == .RADAR || x.kind == .POLAR) ? DiagramCatalog.label(x.kind, n.text) : n.text
    }

    private static func drawBar(_ x: Ctx, _ n: DiagramNode, _ look: Look) {
        let r = rectOf(n)
        let P = x.pal
        var fill = look.fill ?? P.ink
        var stroke: UInt32? = nil
        let paper = P.editorial ? P.paper : WHITE
        if x.kind == .WATERFALL && n.fillColorHex == nil && n.role != "focal" {
            let it = DiagramCatalog.parse(n.text)
            let v = it.values.last ?? 0
            if it.computed || !it.signed { fill = P.ink }
            else if v >= 0 { fill = blend(paper, P.ink, 0.55) }
            else { fill = blend(paper, P.ink, 0.12); stroke = P.muted }
        }
        if x.kind == .GANTT && n.fillColorHex == nil && n.role != "focal" { fill = blend(paper, P.ink, 0.7) }
        if x.pal.coverage { fill = BLACK }
        fillPaint(x, fill)
        let rad: Float = x.kind == .GANTT ? min(r.height() * 0.3, 8) : 2
        x.c.drawRoundRect(r, rad * x.k, rad, x.p)
        if let stroke {
            strokePaint(x, stroke, x.line * 0.8, false)
            x.c.drawRoundRect(r, rad * x.k, rad, x.p)
        }
    }

    /// Isı haritası hücresi ya da erişim matrisi hücresi.
    private static func drawCell(_ x: Ctx, _ n: DiagramNode, _ look: Look) {
        let r = rectOf(n)
        let P = x.pal
        let paper = P.editorial ? P.paper : WHITE
        let onDark = P.editorial ? P.onInk : WHITE
        var fill: UInt32
        var text: UInt32
        if x.kind == .DP_SECURITY {
            let level = accessLevel(n.text)
            switch level {
            case 0: fill = paper
            case 1: fill = blend(paper, P.ink, 0.08)
            case 2: fill = blend(paper, P.ink, 0.2)
            case 3: fill = blend(paper, P.ink, 0.5)
            default: fill = P.ink
            }
            text = level >= 3 ? onDark : level == 0 ? P.muted : P.ink
        } else {
            let mx = max(x.nodes.filter { $0.box == .CELL }.map { lastValue($0.text) ?? 0 }.max() ?? 1, 1e-3)
            let t = clampF((lastValue(n.text) ?? 0) / mx, 0, 1)
            let tt = Float(Foundation.pow(Double(t), 0.75))
            fill = blend(blend(paper, P.accent, 0.05), P.accent, tt)
            text = tt > 0.55 ? onDark : P.ink
        }
        if let f = n.fillColorHex { fill = hex(f) }
        if let t = n.textColorHex { text = hex(t) }
        if n.textColorHex == nil { text = DiagramContrast.readable(text, fill) }
        if P.coverage { fill = BLACK; text = BLACK }
        fillPaint(x, fill)
        x.c.drawRoundRect(r, 3 * x.k, 3, x.p)
        if n.role == "focal" {
            strokePaint(x, x.kind == .DP_SECURITY ? P.accent : P.ink, x.line * 1.6, false)
            x.c.drawRoundRect(r, 3 * x.k, 3, x.p)
        } else if x.kind == .DP_SECURITY && accessLevel(n.text) == 0 {
            strokePaint(x, P.rule, x.line * 0.8, false)
            x.c.drawRoundRect(r, 3 * x.k, 3, x.p)
        }
        if !x.labels { return }
        let l = Look(nil, nil, x.line, text, text, text)
        if x.kind == .DP_SECURITY { drawBlock(x, n.text, r, l, .CENTER, maxSize: x.base * 0.62) }
        else { monoCentered(x, n.text, r, text, x.base * 0.62) }
    }

    static func accessLevel(_ text: String) -> Int {
        let t = text.lowercased(with: TR)
        if t.contains("yok") || t == "—" || t == "-" { return 0 }
        if t.contains("giriş") { return 1 }
        if t.contains("yalnız") || t.contains("okuma") || t.contains("select") { return 2 }
        if t.contains("oku/yaz") || t.contains("yaz") { return 3 }
        return 4
    }

    /// Tablo (varlık, UML sınıfı, veritabanı tablosu): başlık bandı ve satırlar; "--" ayırıcı.
    private static func drawTable(_ x: Ctx, _ n: DiagramNode, _ look: Look) {
        let r = rectOf(n)
        let P = x.pal
        let lines = n.text.components(separatedBy: "\n")
        let stereo: String? = (lines.first.map { $0.hasPrefix("«") } ?? false) && lines.count > 1 ? lines[0] : nil
        let title = stereo != nil ? (lines.count > 1 ? lines[1] : "") : (lines.first ?? "")
        let rows = Array(lines.dropFirst(stereo != nil ? 2 : 1))
        func isSep(_ s: String) -> Bool { s.trimmingCharacters(in: .whitespaces) == "--" }
        let sepCount = rows.filter(isSep).count
        let rowCount = rows.count - sepCount
        let units = Float(rowCount) + 0.45 * Float(sepCount) + 1.55 + (stereo != nil ? 0.7 : 0)
        let rh = min((r.height() - 6) / max(1, units), x.base * 1.05)
        let headerH = rh * (1.55 + (stereo != nil ? 0.7 : 0))
        let path = boxPath(.TABLE, r, false, x.k, 7)
        let headFill: UInt32?
        if n.fillColorHex != nil { headFill = nil }
        else if n.role == "focal" && P.editorial { headFill = blend(P.paper, P.accent, 0.12) }
        else if P.editorial { headFill = P.soft }
        else { headFill = look.fill.map { alpha($0, 1) } }
        if let f = look.fill {
            fillPaint(x, f)
            x.p.alpha = clampI(ktInt(Float(calpha(f)) * look.fillAlpha), 0, 255)
            x.c.drawPath(path, x.p)
        }
        if let hf = headFill, !P.coverage {
            let save = x.c.save()
            x.c.clipPath(path)
            fillPaint(x, hf)
            x.c.drawRect(r.left, r.top, r.right, r.top + headerH, x.p)
            x.c.restoreToCount(save)
        }
        let stroke = look.stroke ?? P.ink
        strokePaint(x, stroke, look.width, look.dashed)
        x.c.drawPath(path, x.p)
        strokePaint(x, P.editorial && n.role != "focal" ? P.rule : stroke, x.line * 0.9, false)
        x.c.drawLine(r.left, r.top + headerH, r.right, r.top + headerH, x.p)
        if !x.labels {
            var yy = r.top + headerH + rh * 0.6
            strokePaint(x, P.rule, x.line * 2, false)
            for _ in rows.filter({ !isSep($0) }) {
                x.c.drawLine(r.left + 10 * x.k, yy, r.left + r.width() * 0.6, yy, x.p); yy += rh
            }
            return
        }
        let pad = 10 * x.k
        let inner = (r.width() - 2 * pad) / x.k
        var hy = r.top + rh * 0.35
        if let stereo {
            let s = fitSize(stereo, mono, rh * 0.5, inner, 0)
            hy += s
            drawText(x, stereo, r.centerX() - measure(stereo, mono, s, 0) * x.k / 2, hy, mono, s, P.muted)
            hy += s * 0.35
        }
        let ts = fitSize(title, medium, rh * 0.78, inner, 0)
        hy += ts * 1.05
        drawText(x, title, r.centerX() - measure(title, medium, ts, 0) * x.k / 2, hy, medium, ts,
                 n.role == "focal" && P.editorial ? P.accent : look.text)
        let rs0 = rh * 0.58
        let rs = rows.filter { !isSep($0) }.map { fitSize($0.replacingOccurrences(of: " : ", with: "  "), mono, rs0, inner, 0) }.min() ?? rs0
        var y = r.top + headerH
        for (j, row) in rows.enumerated() {
            if isSep(row) && j == 0 { y += rh * 0.2; continue }
            if isSep(row) {
                strokePaint(x, P.rule, x.line * 0.8, false)
                x.c.drawLine(r.left, y + rh * 0.22, r.right, y + rh * 0.22, x.p)
                y += rh * 0.45
                continue
            }
            let base = y + rh * 0.5 + rs * 0.36
            if row.contains(" : ") {
                let parts = row.components(separatedBy: " : ").map { $0.trimmingCharacters(in: .whitespaces) }
                drawText(x, parts[0], r.left + pad, base, mono, rs, look.text)
                var right = r.right - pad
                if parts.count > 2 {
                    let c = parts[2]
                    let color: UInt32
                    switch c.uppercased(with: TR) {
                    case "PK": color = P.accent
                    case "FK": color = P.link
                    default: color = P.muted
                    }
                    let w = measure(c, mono, rs * 0.85, 0.04) * x.k
                    drawText(x, c, right - w, base, mono, rs * 0.85, color, spacing: 0.04)
                    right -= w + 10 * x.k
                }
                if parts.count > 1 {
                    let t = parts[1]
                    let w = measure(t, mono, rs, 0) * x.k
                    drawText(x, t, right - w, base, mono, rs, P.muted)
                }
            } else {
                let chars = Array(row)
                let color: UInt32?
                if row.hasPrefix("#") { color = P.accent }
                else if row.hasPrefix("→") { color = P.link }
                else if row.hasPrefix("+") || row.hasPrefix("-") || row.hasPrefix("~") { color = P.muted }
                else { color = nil }
                if let color, chars.count > 2, chars[1] == " " {
                    drawText(x, String(chars[0]), r.left + pad, base, mono, rs, color)
                    let off = measure("\(chars[0]) ", mono, rs, 0) * x.k
                    drawText(x, String(chars[2...]), r.left + pad + off, base, mono, rs, look.text)
                } else {
                    drawText(x, row, r.left + pad, base, mono, rs, look.text)
                }
            }
            y += rh
        }
    }

    // MARK: - oklar

    private static func drawEdges(_ x: Ctx) {
        if x.kind == .LINE { return }
        for (i, edge) in x.spec.edges.enumerated() {
            guard let a = x.byId[edge.from], let b = x.byId[edge.to] else { continue }
            if x.kind == .MIND_MAP { drawBranch(x, i, a, b, edge) }
            else if x.kind == .SANKEY { drawRibbon(x, i, a, edge) }
            else { drawEdge(x, i, edge, a, b) }
        }
    }

    static func edgeColor(_ x: Ctx, _ edge: DiagramEdge) -> UInt32 {
        let P = x.pal
        if P.coverage { return BLACK }
        if let c = edge.colorHex { return hex(c) }
        switch edge.role {
        case "accent", "cycle": return P.accent
        case "link": return P.link
        case "async", "spoke": return P.muted
        default: return P.editorial && x.kind == .WARDLEY ? P.muted : P.edge
        }
    }

    private static func drawEdge(_ x: Ctx, _ index: Int, _ edge: DiagramEdge, _ a: DiagramNode, _ b: DiagramNode) {
        let route = DiagramRenderer.routeOf(x.spec, index, edge, a, b)
        if route.count < 2 { return }
        let color = edgeColor(x, edge)
        let strong = edge.role == "accent" || edge.role == "cycle"
        let width = x.line * (strong ? 1.5 : 1) * (x.kind == .WARDLEY ? 0.8 : 1)
        let role = edge.role
        let dashed = edge.dashed || ["async", "spoke", "realize", "depend"].contains(role ?? "")
        let hollow = role == "inherit" || role == "realize"
        let open = role == "depend" || (x.kind == .SEQUENCE && (edge.dashed || role == "async"))
        let headLen = max(hollow ? 18 : 14, width * (hollow ? 3.4 : 2.8))
        let headHalf = max(hollow ? 9 : 5.5, width * (hollow ? 1.7 : 1.15))
        var pts: [(Float, Float)] = route.map { ($0.x, $0.y) }
        let end = pts[pts.count - 1]
        let prev = pts[pts.count - 2]
        let dir = visualDir(x, prev.0, prev.1, end.0, end.1)
        if (edge.arrow || hollow), let dir, !open {
            let back = hollow ? headLen : headLen * 0.75
            let segLen = hypotf((end.0 - prev.0) / x.k, end.1 - prev.1)
            if segLen > back * 1.2 { pts[pts.count - 1] = (end.0 - dir.0 * back * x.k, end.1 - dir.1 * back) }
        }
        let startDiamond = role == "compose" || role == "aggregate"
        if startDiamond {
            let s = pts[0], n1 = pts[1]
            if let d = visualDir(x, n1.0, n1.1, s.0, s.1) { pts[0] = (s.0 - d.0 * 20 * x.k, s.1 - d.1 * 20) }
        }
        let line = APath()
        line.moveTo(pts[0].0, pts[0].1)
        for k in 1..<pts.count { line.lineTo(pts[k].0, pts[k].1) }
        let elbow = pts.count > 2 && !DiagramCatalog.straightKinds.contains(x.kind) && edge.role != "arc"
        strokePaint(x, color, width, dashed, elbow ? 14 : 0)
        x.c.drawPath(line, x.p)
        if let dir {
            if hollow {
                let head = headPath(x, end.0, end.1, dir, headLen, headHalf)
                fillPaint(x, x.pal.editorial ? x.pal.paper : WHITE)
                x.c.drawPath(head, x.p)
                strokePaint(x, color, width, false); x.c.drawPath(head, x.p)
            } else if open && edge.arrow {
                strokePaint(x, color, width, false)
                let l: Float = 13, h: Float = 6
                let bx = end.0 / x.k - dir.0 * l, by = end.1 - dir.1 * l
                let path = APath()
                path.moveTo((bx - dir.1 * h) * x.k, by + dir.0 * h); path.lineTo(end.0, end.1); path.lineTo((bx + dir.1 * h) * x.k, by - dir.0 * h)
                x.c.drawPath(path, x.p)
            } else if edge.arrow {
                fillPaint(x, color)
                x.c.drawPath(headPath(x, end.0, end.1, dir, headLen, headHalf), x.p)
            }
        }
        if startDiamond {
            let s = route[0], n1 = route[1]
            if let d = visualDir(x, n1.x, n1.y, s.x, s.y) {
                let l: Float = 20, h: Float = 7
                let mx = s.x / x.k - d.0 * l / 2, my = s.y - d.1 * l / 2
                let path = APath()
                path.moveTo(s.x, s.y); path.lineTo((mx - d.1 * h) * x.k, my + d.0 * h)
                path.lineTo((s.x / x.k - d.0 * l) * x.k, s.y - d.1 * l); path.lineTo((mx + d.1 * h) * x.k, my - d.0 * h); path.close()
                fillPaint(x, role == "compose" ? color : x.pal.editorial ? x.pal.paper : WHITE)
                x.c.drawPath(path, x.p)
                strokePaint(x, color, width, false); x.c.drawPath(path, x.p)
            }
        }
        if let fl = edge.fromLabel { cardinality(x, route[0], route[1], fl, color, width) }
        if let tl = edge.toLabel { cardinality(x, route[route.count - 1], route[route.count - 2], tl, color, width) }
        if x.labels && !isBlank(edge.label) { edgeLabel(x, edge, route, color) }
    }

    /// [from]→[to] yönünün görsel birim vektörü (yatay sıkıştırma hesaba katılır).
    static func visualDir(_ x: Ctx, _ fx: Float, _ fy: Float, _ tx: Float, _ ty: Float) -> (Float, Float)? {
        let vx = (tx - fx) / x.k, vy = ty - fy
        let d = hypotf(vx, vy)
        if d < 1e-3 { return nil }
        return (vx / d, vy / d)
    }

    static func headPath(_ x: Ctx, _ tx: Float, _ ty: Float, _ dir: (Float, Float), _ len: Float, _ half: Float) -> APath {
        let bx = tx / x.k - dir.0 * len, by = ty - dir.1 * len
        let path = APath()
        path.moveTo(tx, ty)
        path.lineTo((bx - dir.1 * half) * x.k, by + dir.0 * half)
        path.lineTo((bx + dir.1 * half) * x.k, by - dir.0 * half)
        path.close()
        return path
    }

    private static func cardinality(_ x: Ctx, _ end: RoutePoint, _ inner: RoutePoint, _ label: String, _ color: UInt32, _ width: Float) {
        guard let d = visualDir(x, inner.x, inner.y, end.x, end.y) else { return }
        let t = label.trimmingCharacters(in: .whitespacesAndNewlines)
        let many = t.caseInsensitiveCompare("N") == .orderedSame || t.caseInsensitiveCompare("M") == .orderedSame || t == "*"
            || t.contains("..*") || t.contains("n")
        strokePaint(x, color, width, false)
        let ex = end.x / x.k, ey = end.y
        if many {
            let l: Float = 16, h: Float = 9
            let mx = ex - d.0 * l, my = ey - d.1 * l
            let path = APath()
            path.moveTo(mx * x.k, my); path.lineTo((ex - d.1 * h) * x.k, ey + d.0 * h)
            path.moveTo(mx * x.k, my); path.lineTo(end.x, end.y)
            path.moveTo(mx * x.k, my); path.lineTo((ex + d.1 * h) * x.k, ey - d.0 * h)
            x.c.drawPath(path, x.p)
        } else {
            let l: Float = 10, h: Float = 8
            let mx = ex - d.0 * l, my = ey - d.1 * l
            x.c.drawLine((mx - d.1 * h) * x.k, my + d.0 * h, (mx + d.1 * h) * x.k, my - d.0 * h, x.p)
        }
        if !x.labels { return }
        let s = clampF(x.base * 0.48, 9, 24)
        let off: Float = 28
        let lx = (ex - d.0 * off + -d.1 * 12) * x.k
        let ly = ey - d.1 * off + d.0 * 12
        drawText(x, label, lx - measure(label, mono, s, 0) * x.k / 2, ly + s * 0.36, mono, s, x.pal.muted)
    }

    private static func edgeLabel(_ x: Ctx, _ edge: DiagramEdge, _ route: [RoutePoint], _ color: UInt32) {
        let P = x.pal
        let upper = x.kind != .SEQUENCE && x.kind != .SANKEY
        let text = upper ? edge.label.uppercased(with: TR) : edge.label
        let size = x.kind == .SEQUENCE ? clampF(x.base * 0.58, 10, 30) : x.label
        let spacing: Float = upper ? 0.06 : 0
        let w = measure(text, mono, size, spacing) * x.k
        let textColor = (edge.role == "accent" || edge.role == "link" || edge.role == "cycle") ? color : P.editorial ? P.muted : color
        let mx: Float, my: Float
        if x.kind == .SEQUENCE {
            let m = DiagramRouting.midpoint(route)
            mx = m.x; my = m.y - size * 0.75
        } else if edge.role == "arc" {
            let top = route.min { $0.y < $1.y }!
            mx = top.x; my = top.y - size * 0.2
        } else {
            (mx, my) = labelPoint(x, route, w, size)
        }
        if P.editorial {
            fillPaint(x, P.paper)
            let padX = 5 * x.k, padY = size * 0.32
            x.c.drawRoundRect(RectF(mx - w / 2 - padX, my - size * 0.62 - padY * 0.5, mx + w / 2 + padX, my + size * 0.42 + padY * 0.5), 3 * x.k, 3, x.p)
            drawText(x, text, mx - w / 2, my + size * 0.33, mono, size, textColor, spacing: spacing)
        } else {
            drawText(x, text, mx - w / 2, my - size * 0.4, mono, size, textColor, spacing: spacing)
        }
    }

    private static func labelPoint(_ x: Ctx, _ route: [RoutePoint], _ w: Float, _ size: Float) -> (Float, Float) {
        if route.count >= 3 {
            let a = route[route.count - 2], b = route[route.count - 1]
            let vertical = abs(a.x - b.x) < 1e-3
            let len = vertical ? abs(b.y - a.y) : abs(b.x - a.x)
            let need = vertical ? size * 2.6 : w + 24 * x.k
            if len > need + 24 { return ((a.x + b.x) / 2, (a.y + b.y) / 2) }
        }
        let m = DiagramRouting.midpoint(route)
        return (m.x, m.y)
    }

    /// Zihin haritası dalı: merkezden düğüme yumuşak eğri.
    private static func drawBranch(_ x: Ctx, _ index: Int, _ a: DiagramNode, _ b: DiagramNode, _ edge: DiagramEdge) {
        let P = x.pal
        let ax = (a.x + a.w / 2) * U, ay = (a.y + a.h / 2) * U
        let bx = (b.x + b.w / 2) * U, by = (b.y + b.h / 2) * U
        let color: UInt32
        if P.coverage { color = BLACK }
        else if let c = edge.colorHex { color = hex(c) }
        else if P.editorial { color = b.role == "focal" ? P.accent : P.muted }
        else { color = P.theme?.fill(x.nodes.firstIndex(of: b) ?? -1).map { darker(hex($0)) } ?? P.edge }
        strokePaint(x, color, x.line * (P.editorial ? 1.3 : 1.6), false)
        let path = APath()
        path.moveTo(ax, ay)
        path.cubicTo(ax + (bx - ax) * 0.5, ay, ax + (bx - ax) * 0.5, by, bx, by)
        x.c.drawPath(path, x.p)
    }

    /// Sankey şeridi: kaynaktan hedefe değerle orantılı kalınlıkta yumuşak bant.
    private static func drawRibbon(_ x: Ctx, _ index: Int, _ a: DiagramNode, _ edge: DiagramEdge) {
        guard let r = x.ribbons[index] else { return }
        let P = x.pal
        let x0 = r.x0 * U, x1 = r.x1 * U
        let y0 = r.y0 * U, y1 = r.y1 * U, h = r.h * U
        let mx = (x0 + x1) / 2
        let path = APath()
        path.moveTo(x0, y0); path.cubicTo(mx, y0, mx, y1, x1, y1)
        path.lineTo(x1, y1 + h); path.cubicTo(mx, y1 + h, mx, y0 + h, x0, y0 + h); path.close()
        let base: UInt32
        if P.coverage { base = BLACK }
        else if let c = edge.colorHex { base = hex(c) }
        else if edge.role == "accent" { base = P.accent }
        else { base = lookOf(x, x.nodes.firstIndex(of: a) ?? -1, a).fill ?? P.ink }
        fillPaint(x, base)
        x.p.alpha = P.coverage ? 255 : edge.role == "accent" ? 115 : 52
        x.c.drawPath(path, x.p)
        if x.labels && !isBlank(edge.label) && h > 14 {
            let s = min(clampF(x.base * 0.46, 9, 22), h * 0.8)
            let text = fmt(DiagramCatalog.parse(edge.label).values.last ?? 0)
            let tx = x1 - 10 * x.k - measure(text, mono, s, 0) * x.k
            drawText(x, text, tx, y1 + h / 2 + s * 0.36, mono, s, edge.role == "accent" ? P.accent : P.muted)
        }
    }

    // MARK: - yazı

    enum Align { case LEFT, CENTER, RIGHT }

    /// Yazının satırları: üst etiket ("[ETİKET]" ilk satırda), başlık (kaydırılır) ve alt satırlar (eşaralıklı).
    struct Block {
        let eyebrow: String?
        let title: [String]
        let subs: [String]
        let size: Float
        var eyebrowSize: Float { size * 0.62 }
        var subSize: Float { size * 0.72 }
        var height: Float {
            (eyebrow != nil ? eyebrowSize * 1.45 : 0) + Float(title.count) * size * 1.18 + Float(subs.count) * subSize * 1.38
        }
    }

    private static var blockCache: [String: Block] = [:]
    private static let blockLock = NSLock()

    static func measure(_ text: String, _ face: DiagramFace, _ size: Float, _ spacing: Float) -> Float {
        face.measure(text, size, spacing)
    }

    /// [text] tek satırda [maxWidth] içine sığacak boyut ([size] en çok).
    static func fitSize(_ text: String, _ face: DiagramFace, _ size: Float, _ maxWidth: Float, _ spacing: Float) -> Float {
        if text.isEmpty || maxWidth <= 0 { return size }
        let w = measure(text, face, size, spacing)
        return w <= maxWidth ? size : max(6, size * maxWidth / w)
    }

    static func block(_ text: String, _ width: Float, _ height: Float, _ base: Float, _ k: Float, _ bold: Bool) -> Block {
        let key = "\(text)|\(ktInt(width))|\(ktInt(height))|\(ktInt(base))|\(ktInt(k * 100))|\(bold)"
        blockLock.lock()
        if let b = blockCache[key] { blockLock.unlock(); return b }
        blockLock.unlock()
        let lines = text.components(separatedBy: "\n")
        var eyebrow: String? = nil
        if lines.count > 1 {
            let f = lines[0].trimmingCharacters(in: .whitespacesAndNewlines)
            if f.count > 2 && f.hasPrefix("[") && f.hasSuffix("]") { eyebrow = String(f.dropFirst().dropLast()).uppercased(with: TR) }
        }
        let rest = eyebrow != nil ? Array(lines.dropFirst()) : lines
        let titleText = rest.first ?? ""
        let subs = Array(rest.dropFirst())
        let maxW = width / k
        let face: DiagramFace = bold ? medium : sans
        var size = base
        var title = wrap(titleText, face, size, maxW)
        func fits() -> Bool {
            let b = Block(eyebrow: eyebrow, title: title, subs: subs, size: size)
            if b.height > height * 0.94 { return false }
            if title.contains(where: { measure($0, face, size, 0) > maxW }) { return false }
            if let e = eyebrow, measure(e, mono, b.eyebrowSize, 0.08) > maxW { return false }
            if subs.contains(where: { measure($0, mono, b.subSize, 0) > maxW }) { return false }
            return true
        }
        while !fits() && size > 6.5 {
            size *= 0.9
            title = wrap(titleText, face, size, maxW)
        }
        let b = Block(eyebrow: eyebrow, title: title, subs: subs, size: size)
        blockLock.lock()
        if blockCache.count >= 768 { blockCache.removeAll() }
        blockCache[key] = b
        blockLock.unlock()
        return b
    }

    private static func wrap(_ text: String, _ face: DiagramFace, _ size: Float, _ maxW: Float) -> [String] {
        let words = text.split(separator: " ", omittingEmptySubsequences: true).map(String.init)
        if words.isEmpty { return [text] }
        var out: [String] = []
        var cur = ""
        for w in words {
            let cand = cur.isEmpty ? w : "\(cur) \(w)"
            if measure(cand, face, size, 0) <= maxW || cur.isEmpty { cur = cand } else { out.append(cur); cur = w }
        }
        if !cur.isEmpty { out.append(cur) }
        return Array(out.prefix(4))
    }

    /// Yazı bloğunu [r] içine çizer (dikeyde ortalı; [top] ise üste yaslı).
    static func drawBlock(_ x: Ctx, _ text: String, _ r: RectF, _ look: Look, _ align: Align, top: Bool = false, maxSize: Float? = nil) {
        if isBlank(text) || r.width() <= 2 || r.height() <= 2 { return }
        let bold = look.bold || (x.pal.editorial && x.kind == .LAYERS)
        let b = block(text, r.width() * 0.92, r.height(), min(maxSize ?? x.base, x.base), x.k, bold)
        let face: DiagramFace = bold ? medium : sans
        var y = top ? r.top + 4 : r.centerY() - b.height / 2
        func xFor(_ w: Float) -> Float {
            switch align {
            case .LEFT: return r.left
            case .CENTER: return r.centerX() - w / 2
            case .RIGHT: return r.right - w
            }
        }
        if let e = b.eyebrow {
            let s = b.eyebrowSize
            y += s * 1.15
            drawText(x, e, xFor(measure(e, mono, s, 0.08) * x.k), y, mono, s, look.eyebrow, spacing: 0.08)
            y += s * 0.3
        }
        for line in b.title {
            y += b.size * 1.0
            let w = measure(line, face, b.size, 0) * x.k
            let lx = xFor(w)
            drawText(x, line, lx, y, face, b.size, look.text)
            if look.strike {
                strokePaint(x, look.text, max(1, b.size * 0.07), false)
                x.c.drawLine(lx, y - b.size * 0.32, lx + w, y - b.size * 0.32, x.p)
            }
            y += b.size * 0.18
        }
        for line in b.subs {
            let s = b.subSize
            y += s * 1.1
            drawText(x, line, xFor(measure(line, mono, s, 0) * x.k), y, mono, s, look.sub)
            y += s * 0.28
        }
    }

    static func monoCentered(_ x: Ctx, _ text: String, _ r: RectF, _ color: UInt32, _ size: Float) {
        monoAligned(x, text, r, color, size, .CENTER)
    }

    static func monoAligned(_ x: Ctx, _ text: String, _ r: RectF, _ color: UInt32, _ size: Float, _ align: Align) {
        let t = oneLineUpper(text)
        let s = fitSize(t, mono, max(min(size, r.height() * 0.7), 6), (r.width() - 6 * x.k) / x.k, 0.06)
        let w = measure(t, mono, s, 0.06) * x.k
        let lx: Float
        switch align {
        case .LEFT: lx = r.left + 4 * x.k
        case .CENTER: lx = r.centerX() - w / 2
        case .RIGHT: lx = r.right - 4 * x.k - w
        }
        drawText(x, t, lx, r.centerY() + s * 0.36, mono, s, color, spacing: 0.06)
    }

    /// Yazıyı yatay sıkıştırmayla ([Ctx.k]) çizer.
    static func drawText(_ x: Ctx, _ text: String, _ px: Float, _ py: Float, _ face: DiagramFace, _ size: Float, _ color: UInt32, spacing: Float = 0) {
        if !x.labels || text.isEmpty { return }
        var t = APaint()
        t.face = face; t.textSize = size; t.letterSpacing = spacing
        t.color = x.pal.coverage ? BLACK : color
        let save = x.c.save()
        x.c.translate(px, py)
        x.c.scale(x.k, 1)
        x.c.drawText(text, 0, 0, t)
        x.c.restoreToCount(save)
    }

    /// Türkçe sayı biçimi (Java `%,d` / `%,.1f`, tr): binlik ".", ondalık ",".
    static func fmt(_ v: Float) -> String {
        let r = ktRound(v)
        if abs(v - Float(r)) < 0.05 { return grouped(Int64(r)) }
        let tenths = Int64((Double(abs(v)) * 10).rounded(.toNearestOrAwayFromZero))
        let whole = grouped(tenths / 10)
        return (v < 0 ? "-" : "") + whole + "," + String(tenths % 10)
    }

    private static func grouped(_ n: Int64) -> String {
        let neg = n < 0
        let digits = Array(String(n.magnitude))
        var out = ""
        for (i, ch) in digits.enumerated() {
            if i > 0 && (digits.count - i) % 3 == 0 { out.append(".") }
            out.append(ch)
        }
        return neg ? "-" + out : out
    }
}
