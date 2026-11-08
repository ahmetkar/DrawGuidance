import Foundation

/// Veri grafikleri: sütun (BAR), çizgi (LINE), daire (PIE), serpilme (SCATTER), histogram (HISTOGRAM) (Android `Charts`).
enum Charts {
    private static let BASE_BAR: [Float] = [6, 9, 5, 8, 4, 7, 10, 6, 8, 5, 9, 7, 6, 8, 5]
    private static let BASE_PIE: [Float] = [35, 25, 20, 12, 8, 15, 10, 18, 6, 9, 11, 7, 5, 13, 4]
    private static let NOISE: [Double] = [0.8, -0.6, 1.1, -0.9, 0.3, -0.4, 0.9, -1.0, 0.5, -0.2, 0.7, -0.8, 0.2, -0.5, 1.0].map { Double(Float($0)) }

    static func isChart(_ kind: GraphKind) -> Bool { kind == .BAR || kind == .LINE || kind == .PIE || kind == .SCATTER || kind == .HISTOGRAM }

    /// values boşken öğe sayısı.
    static func defaultCount(_ p: GraphParams) -> Int { clampI(p.repeat + 3, 2, 15) }

    /// [kind] için [n] öğelik hoş varsayılan veri (SCATTER'da n nokta = 2n değer).
    static func defaults(_ kind: GraphKind, _ n: Int) -> [Float] {
        switch kind {
        case .PIE: return (0..<max(n, 0)).map { BASE_PIE[$0 % BASE_PIE.count] }
        case .HISTOGRAM:
            return (0..<max(n, 0)).map { i in
                let t = (Double(i) - Double(n - 1) / 2.0) / (Double(n) / 3.4)
                return Float((2 + 10 * exp(-t * t)).rounded(.toNearestOrEven))
            }
        case .SCATTER:
            var out: [Float] = []
            for i in 0..<max(n, 0) {
                let x = n <= 1 ? 5.0 : 1.0 + 8.0 * Double(i) / Double(n - 1)
                let y = 1.5 + 0.7 * x + NOISE[i % NOISE.count]
                out.append(Float((x * 10).rounded(.toNearestOrEven) / 10))
                out.append(Float((y * 10).rounded(.toNearestOrEven) / 10))
            }
            return out
        default: return (0..<max(n, 0)).map { BASE_BAR[$0 % BASE_BAR.count] }
        }
    }

    /// Çizimde kullanılan veri: kullanıcınınki ya da varsayılan.
    static func effective(_ p: GraphParams) -> [Float] {
        if !p.values.isEmpty || !isChart(p.kind) { return p.values }
        // v45: formül yazılmışsa öğeler x = 1..n'de f(x)'ten örneklenir
        if let s = GraphFormula.sampled(p, defaultCount(p)) { return s }
        let n = p.kind == .SCATTER ? defaultCount(p) * 2 : defaultCount(p)
        return defaults(p.kind, n)
    }

    static func itemCount(_ p: GraphParams) -> Int {
        let n = effective(p).count
        return p.kind == .SCATTER ? n / 2 : n
    }

    static func build(_ c: GCanvas) {
        switch c.p.kind {
        case .BAR, .LINE, .HISTOGRAM: valueChart(c)
        case .PIE: pie(c)
        case .SCATTER: scatter(c)
        default: break
        }
        // v45: formülden örneklenen grafikte formül sağ üst köşede
        let p = c.p
        if !p.formula.allSatisfy(\.isWhitespace) && p.values.isEmpty && GraphFormula.sampled(p, defaultCount(p)) != nil {
            let label = p.tickLabels["formula"] ?? Graphs.formulaLabel(p)
            c.setDefault("formula", Graphs.formulaLabel(p))
            if !label.allSatisfy(\.isWhitespace) {
                let ts = GStyle.FORMULA_SIZE * 0.9
                c.layer = 6
                c.text(c.W - GStyle.PAD - c.measure(label, ts), GStyle.PAD * 0.3, label, ts, color: c.color, tag: "formula", reserve: false)
            }
        }
    }

    private static func fitSize(_ c: GCanvas, _ texts: [String], _ room: Float, _ maxS: Float, _ minS: Float) -> Float {
        let unit = texts.map { c.measure($0, 1) }.max().map { max($0, 0.3) } ?? 1
        return clampF(room / unit, minS, maxS)
    }

    // MARK: - BAR / LINE / HISTOGRAM

    private static func valueChart(_ c: GCanvas) {
        let p = c.p
        let kind = p.kind
        let vals = effective(p).map { $0.isFinite ? Double($0) : 0 }
        let n = vals.count
        if n == 0 { return }
        let vmax = max(0, vals.max()!)
        let vmin = min(0, vals.min()!)
        let ext0 = max(vmax, -vmin)
        let ext = ext0 <= 0 ? 1.0 : ext0
        let tk = clampI(p.ticks, 1, 12)
        var step = p.tickStep > 0 ? Double(p.tickStep) : Fmt.niceCeil(ext / Double(tk))
        while (vmax - vmin) / step > 60 { step = Fmt.larger(step) }
        let kTop = max(vmax > 0 ? ktInt((vmax / step - 1e-9).rounded(.up)) : 0, vmin < 0 ? 0 : 1)
        let kBot = vmin < 0 ? ktInt((vmin / step + 1e-9).rounded(.down)) : 0
        let yt = Ticks(step)
        let tsize = GStyle.TICK_SIZE
        var yTexts: [Int: (String?, Bool)] = [:]
        for k in kBot...kTop { yTexts[k] = c.tickText("y:\(k)", yt.label(k)) }
        let yw = (kBot...kTop).map { k -> Float in
            let (s, forced) = yTexts[k]!
            if let s, p.showNumbers || forced { return c.measure(s, tsize) }
            return 0
        }.max()!

        let names = (0..<n).map { p.valueLabels[$0] ?? Cartesian.letter($0) }
        let nameS = GStyle.NAME_SIZE
        let xName = p.xLabel
        let xNameW: Float = xName.isEmpty ? 0 : c.measure(xName, nameS)
        let xNameRight = !xName.isEmpty && xNameW <= 0.22 * c.W
        let headroom: Float = p.showNumbers ? GStyle.KEY_SIZE + GStyle.GAP : 0
        let mT = GStyle.PAD + GStyle.AXIS_EXT + headroom
        let mL = GStyle.PAD + yw + GStyle.GAP + GStyle.TICK + 0.004
        let mR = GStyle.PAD + GStyle.AXIS_EXT + (xNameRight ? xNameW + GStyle.GAP * 1.2 : 0)
        let L = mL
        let R = c.W - mR
        let slot = (R - L) / Float(n)
        let catSize = kind == .HISTOGRAM ? tsize : fitSize(c, names, slot * 0.94, 0.042, 0.022)
        let labelRow = kind == .HISTOGRAM ? GStyle.TICK + GStyle.GAP + tsize : catSize + GStyle.GAP * 1.4
        let mB = GStyle.PAD + labelRow + ((!xNameRight && !xName.isEmpty) ? nameS + GStyle.GAP : 0)
        let T = mT
        let B = c.H - mB
        let fr = Frame(0, Double(n), Double(kBot) * step, Double(kTop) * step, L, T, R, B)
        let ay = Float(fr.py(0))
        let tipY = GStyle.PAD

        // Izgara (yatay)
        if p.grid {
            c.layer = 0
            for k in kBot...kTop where k != 0 {
                let y = Float(fr.py(yt.value(k)))
                c.line(L, y, R, y, stroke: c.pal.grid, width: GStyle.W_GRID)
            }
        }

        // Sütunlar / çizgi
        let valSize = fitSize(c, vals.map { Fmt.auto($0) }, slot * 0.92, GStyle.KEY_SIZE, 0.02)
        switch kind {
        case .BAR, .HISTOGRAM:
            c.layer = 1
            for i in 0..<n {
                let v = vals[i]
                let bw = kind == .BAR ? slot * 0.62 : slot
                let x = kind == .BAR ? L + slot * (Float(i) + 0.5) - bw / 2 : L + slot * Float(i)
                let yv = Float(fr.py(v))
                let fill = kind == .BAR ? GStyle.PALETTE[i % GStyle.PALETTE.count] : GColors.lighten(c.color, 0.3)
                let stroke = kind == .BAR ? GColors.darken(fill, 0.22) : GColors.darken(c.color, 0.2)
                let radius: Float = kind == .BAR ? min(bw * 0.14, 0.01) : 0
                c.add(barPoints(x, bw, ay, yv, radius), closed: true, fill: fill, stroke: stroke, width: kind == .BAR ? 0.6 : 0.8, tag: "value:\(i)")
            }
        default:
            let pts = (0..<n).map { P(L + slot * (Float($0) + 0.5), Float(fr.py(vals[$0]))) }
            if p.fillUnder && n >= 2 {
                c.layer = 1
                c.add([P(pts.first!.x, ay)] + pts + [P(pts.last!.x, ay)], closed: true, fill: c.color, width: 0, alpha: 0.16, tag: "fill")
            }
            c.layer = 4
            if n >= 2 { c.add(pts, stroke: c.color, width: GStyle.W_CURVE, tag: "curve") }
            c.layer = 5
            for (i, pt) in pts.enumerated() {
                c.add(c.circlePoints(pt.x, pt.y, GStyle.DOT * 1.05), closed: true, fill: c.pal.paper, stroke: c.color, width: 1.3, tag: "value:\(i)")
            }
        }

        // Eksenler
        c.layer = 3
        c.line(L, ay, R + GStyle.AXIS_EXT - GStyle.ARROW_LEN * 0.6, ay, width: GStyle.W_AXIS)
        c.arrowHead(R + GStyle.AXIS_EXT, ay, 1, 0)
        c.line(L, B, L, tipY + GStyle.ARROW_LEN * 0.6, width: GStyle.W_AXIS)
        c.arrowHead(L, tipY, 0, -1)
        for k in kBot...kTop {
            if k == 0 && kBot == 0 { continue }
            let y = Float(fr.py(yt.value(k)))
            c.line(L - GStyle.TICK, y, L, y, width: GStyle.W_TICK, tag: "tick:y:\(k)")
        }
        if kind == .HISTOGRAM {
            for i in 0...n {
                let x = L + slot * Float(i)
                c.line(x, ay, x, ay + GStyle.TICK, width: GStyle.W_TICK, tag: "tick:x:\(i)")
            }
        }

        // Yazılar
        c.layer = 6
        axisNamesChart(c, L, R, ay, B + labelRow, xNameRight)
        let everyY = max(1, ktInt((Double(tsize + 0.008) / (step * fr.sy)).rounded(.up)))
        for k in kBot...kTop {
            let (s, forced) = yTexts[k]!
            guard let s else { continue }
            if !forced && (!p.showNumbers || k % everyY != 0) { continue }
            let y = Float(fr.py(yt.value(k)))
            let w = c.measure(s, tsize)
            let box = c.textBox(L - GStyle.TICK - GStyle.GAP * 0.6 - w, y - tsize * 0.52, s, tsize)
            if !forced && !c.fits(box) { continue }
            c.text(box.l, box.t, s, tsize, tag: "tick:y:\(k)")
        }
        if kind == .HISTOGRAM {
            let texts = (0...n).map { c.tickText("x:\($0)", Fmt.auto(Double($0) * 10)) }
            let maxW = texts.map { $0.0.map { c.measure($0, tsize) } ?? 0 }.max()!
            let every = max(1, ktInt(((maxW + 0.012) / slot).rounded(.up)))
            for i in 0...n {
                let (s, forced) = texts[i]
                guard let s else { continue }
                if !forced && (!p.showNumbers || i % every != 0) { continue }
                let w = c.measure(s, tsize)
                c.text(L + slot * Float(i) - w / 2, B + GStyle.TICK + GStyle.GAP * 0.5, s, tsize, tag: "tick:x:\(i)")
            }
        } else {
            for i in 0..<n {
                let s = names[i]
                if s.isEmpty { continue }
                let w = c.measure(s, catSize)
                c.text(L + slot * (Float(i) + 0.5) - w / 2, B + GStyle.GAP * 0.9, s, catSize, tag: "vlabel:\(i)")
            }
        }
        if p.showNumbers {
            for i in 0..<n {
                let v = vals[i]
                let s = Fmt.auto(v)
                let w = c.measure(s, valSize)
                let cx = L + slot * (Float(i) + 0.5)
                let yv = Float(fr.py(v))
                let y: Float
                if kind == .LINE { y = yv - GStyle.DOT * 1.1 - GStyle.GAP * 0.8 - valSize }
                else if v >= 0 { y = yv - GStyle.GAP * 0.7 - valSize }
                else { y = yv + GStyle.GAP * 0.7 }
                c.text(cx - w / 2, y, s, valSize, color: kind == .LINE ? c.color : nil, tag: "value:\(i)")
            }
        }
    }

    /// Üst köşeleri yuvarlatılmış sütun (negatifte düz dikdörtgen).
    private static func barPoints(_ x: Float, _ w: Float, _ base: Float, _ top: Float, _ radius: Float) -> [P] {
        let h = abs(base - top)
        let r = min(radius, h * 0.5)
        if top > base || r < 1e-4 { return [P(x, base), P(x, top), P(x + w, top), P(x + w, base)] }
        var pts: [P] = [P(x, base)]
        for i in 0...5 {
            let a = Double.pi + Double.pi / 2 * Double(i) / 5
            pts.append(P(x + r + r * Float(cos(a)), top + r + r * Float(sin(a))))
        }
        for i in 0...5 {
            let a = 1.5 * Double.pi + Double.pi / 2 * Double(i) / 5
            pts.append(P(x + w - r + r * Float(cos(a)), top + r + r * Float(sin(a))))
        }
        pts.append(P(x + w, base))
        return pts
    }

    private static func axisNamesChart(_ c: GCanvas, _ L: Float, _ R: Float, _ ay: Float, _ belowY: Float, _ xRight: Bool) {
        let p = c.p
        let s = GStyle.NAME_SIZE
        if !p.xLabel.isEmpty {
            let w = c.measure(p.xLabel, s)
            if xRight { c.text(R + GStyle.AXIS_EXT + GStyle.GAP * 1.2, ay - s * 0.55, p.xLabel, s, tag: "axis:x") }
            else { c.text((L + R) / 2 - w / 2, belowY + GStyle.GAP * 0.3, p.xLabel, s, tag: "axis:x") }
        }
        if !p.yLabel.isEmpty {
            let w = c.measure(p.yLabel, s)
            var x = L + GStyle.GAP * 1.6
            if x + w > c.W - 0.004 { x = c.W - 0.004 - w }
            c.text(x, GStyle.PAD - s * 0.1, p.yLabel, s, tag: "axis:y")
        }
    }

    // MARK: - PIE

    /// En büyük kalan yöntemiyle toplamı 100 olan tamsayı yüzdeler.
    static func percents(_ vals: [Double]) -> [Int] {
        let sum = vals.reduce(0, +)
        if sum <= 0 { return [Int](repeating: 0, count: vals.count) }
        let raw = vals.map { $0 / sum * 100 }
        var out = raw.map { ktInt($0.rounded(.down)) }
        var rest = 100 - out.reduce(0, +)
        // Kotlin sortedByDescending kararlıdır
        let order = raw.indices.sorted { a, b in
            let fa = raw[a] - raw[a].rounded(.down), fb = raw[b] - raw[b].rounded(.down)
            return fa != fb ? fa > fb : a < b
        }
        for i in order {
            if rest <= 0 { break }
            if vals[i] > 0 { out[i] += 1; rest -= 1 }
        }
        return out
    }

    private static func pie(_ c: GCanvas) {
        let p = c.p
        let vals = effective(p).map { $0.isFinite && $0 > 0 ? Double($0) : 0 }
        let n = vals.count
        if n == 0 { return }
        let sum = vals.reduce(0, +)
        let names = (0..<n).map { p.valueLabels[$0] ?? Cartesian.letter($0) }
        let ls: Float = 0.042
        let sw = ls * 0.85
        let rowH = ls * 1.5
        let legendW = sw + GStyle.GAP * 1.3 + (names.map { c.measure($0, ls) }.max() ?? 0)
        let right = c.W >= c.H * 1.15
        let outer: Float = p.showNumbers ? 0.07 : 0.02
        let gapL: Float = 0.05
        let cx: Float
        let cy: Float
        let r: Float
        var legend: [P] = []
        if right {
            let availW = c.W - 2 * GStyle.PAD - legendW - gapL
            r = max(min(availW, c.H - 2 * GStyle.PAD) / 2 - outer, 0.08)
            let block = 2 * (r + outer) + gapL + legendW
            let x0 = (c.W - block) / 2
            cx = x0 + outer + r
            cy = c.H / 2
            let lx = x0 + 2 * (r + outer) + gapL
            let ly0 = cy - Float(n) * rowH / 2
            for i in 0..<n { legend.append(P(lx, ly0 + Float(i) * rowH)) }
        } else {
            let colW = legendW + 0.04
            let cols = min(max(1, ktInt(((c.W - 2 * GStyle.PAD + 0.04) / colW).rounded(.down))), n)
            let rows = (n + cols - 1) / cols
            let legendH = Float(rows) * rowH
            r = max(min(c.W - 2 * GStyle.PAD, c.H - 2 * GStyle.PAD - legendH - 0.04) / 2 - outer, 0.08)
            let block = 2 * (r + outer) + 0.04 + legendH
            let y0 = (c.H - block) / 2
            cx = c.W / 2
            cy = y0 + outer + r
            let gridW = Float(cols) * colW - 0.04
            let lx0 = (c.W - gridW) / 2
            let ly0 = y0 + 2 * (r + outer) + 0.04
            for i in 0..<n { legend.append(P(lx0 + Float(i % cols) * colW, ly0 + Float(i / cols) * rowH)) }
        }

        c.layer = 1
        if sum <= 0 {
            c.ring(cx, cy, r, stroke: c.pal.guide, width: 1, tag: "curve")
        } else {
            var start = -90.0
            for i in 0..<n {
                let sweep = 360.0 * vals[i] / sum
                if sweep <= 0 { continue }
                let m = min(max(2, ktInt(abs(sweep) / 360.0 * 2 * Double.pi * Double(r) * Double(c.res) / 4)), 240)
                var pts: [P] = []
                if sweep < 359.99 { pts.append(P(cx, cy)) }
                for k in 0...m {
                    let a = (start + sweep * Double(k) / Double(m)) * (Double.pi / 180)
                    pts.append(P(cx + r * Float(cos(a)), cy + r * Float(sin(a))))
                }
                c.add(pts, closed: true, fill: GStyle.PALETTE[i % GStyle.PALETTE.count], stroke: c.pal.paper, width: 1.4, tag: "value:\(i)")
                start += sweep
            }
        }

        // Yüzdeler
        if p.showNumbers && sum > 0 {
            let pc = percents(vals)
            var start = -90.0
            for i in 0..<n {
                let sweep = 360.0 * vals[i] / sum
                if sweep <= 0 { continue }
                let mid = (start + sweep / 2) * (Double.pi / 180)
                start += sweep
                let s = "%\(pc[i])"
                let cs = Float(cos(mid))
                let sn = Float(sin(mid))
                let inside = sweep >= 26.0
                if inside {
                    let size = min(0.042, r * 0.2)
                    let w = c.measure(s, size)
                    let fill = GStyle.PALETTE[i % GStyle.PALETTE.count]
                    let col = GColors.luminance(fill) < 0.55 ? GStyle.WHITE : GStyle.TEXT_DARK
                    c.layer = 6
                    c.text(cx + 0.63 * r * cs - w / 2, cy + 0.63 * r * sn - size * 0.52, s, size, color: col, tag: "value:\(i)")
                } else {
                    let size: Float = 0.034
                    let w = c.measure(s, size)
                    c.layer = 3
                    c.line(cx + r * cs, cy + r * sn, cx + (r + 0.022) * cs, cy + (r + 0.022) * sn, width: 0.6)
                    let ax = cx + (r + 0.03) * cs
                    let ay = cy + (r + 0.03) * sn
                    let bx = cs >= 0 ? ax : ax - w
                    c.layer = 6
                    c.text(bx, ay - size * 0.52, s, size, tag: "value:\(i)")
                }
            }
        }

        // Açıklama
        for i in 0..<n {
            let at = legend[i]
            c.layer = 5
            c.add(c.roundRectPoints(at.x, at.y + (ls - sw) / 2, sw, sw, sw * 0.22), closed: true, fill: GStyle.PALETTE[i % GStyle.PALETTE.count], width: 0, tag: "value:\(i)")
            c.layer = 6
            c.text(at.x + sw + GStyle.GAP * 1.3, at.y, names[i], ls, tag: "vlabel:\(i)")
        }
    }

    // MARK: - SCATTER

    private static func scatter(_ c: GCanvas) {
        let p = c.p
        let v = effective(p)
        let m = v.count / 2
        let xs = (0..<m).map { Double(v[2 * $0]) }
        let ys = (0..<m).map { Double(v[2 * $0 + 1]) }
        let tk = clampI(p.ticks, 1, 12)
        func range(_ a: [Double]) -> (Double, Double, Ticks) {
            let f = a.filter(\.isFinite)
            let lo0 = min(0, f.min() ?? 0)
            let hi0 = max(0, f.max() ?? 1)
            let span0 = hi0 - lo0
            let span = span0 <= 0 ? 1.0 : span0
            var st = p.tickStep > 0 ? Double(p.tickStep) : Fmt.niceCeil(span / Double(tk))
            while span / st > 60 { st = Fmt.larger(st) }
            var lo = (lo0 / st + 1e-9).rounded(.down) * st
            var hi = (hi0 / st - 1e-9).rounded(.up) * st
            if hi <= lo { hi = lo + st }
            if !f.isEmpty && hi - (f.max() ?? 0) < st * 0.12 { hi += st * 0.5 }
            if lo < 0 && (f.min() ?? 0) - lo < st * 0.12 { lo -= st * 0.5 }
            return (lo, hi, Ticks(st))
        }
        let (x0, x1, xt) = range(xs)
        let (y0, y1, yt) = range(ys)
        let tsize = GStyle.TICK_SIZE
        var yw: Float = 0
        for k in yt.indices(y0, y1) where k != 0 { yw = max(yw, c.measure(yt.label(k), tsize)) }
        let L = GStyle.PAD + (p.showNumbers ? yw + GStyle.GAP : 0) + GStyle.TICK + 0.004
        let T = GStyle.PAD + GStyle.AXIS_EXT
        let R = c.W - GStyle.PAD - GStyle.AXIS_EXT
        let B = c.H - GStyle.PAD - (p.showNumbers ? tsize + GStyle.GAP : 0) - GStyle.TICK
        let fr = Frame(x0, x1, y0, y1, L, T, R, B)
        let ax = Cartesian.axisX(fr)
        let ay = Cartesian.axisY(fr)

        if p.grid {
            c.layer = 0
            for k in xt.indices(x0, x1) {
                let x = Float(fr.px(xt.value(k)))
                if abs(x - ax) > 1e-3 { c.line(x, T, x, B, stroke: c.pal.grid, width: GStyle.W_GRID) }
            }
            for k in yt.indices(y0, y1) {
                let y = Float(fr.py(yt.value(k)))
                if abs(y - ay) > 1e-3 { c.line(L, y, R, y, stroke: c.pal.grid, width: GStyle.W_GRID) }
            }
        }
        c.layer = 3
        c.line(L, ay, R + GStyle.AXIS_EXT - GStyle.ARROW_LEN * 0.6, ay, width: GStyle.W_AXIS)
        c.arrowHead(R + GStyle.AXIS_EXT, ay, 1, 0)
        c.line(ax, B, ax, T - GStyle.AXIS_EXT + GStyle.ARROW_LEN * 0.6, width: GStyle.W_AXIS)
        c.arrowHead(ax, T - GStyle.AXIS_EXT, 0, -1)
        for k in xt.indices(x0, x1) {
            if k == 0 && fr.hasX0 { continue }
            let x = Float(fr.px(xt.value(k)))
            c.line(x, ay - GStyle.TICK, x, ay + GStyle.TICK, width: GStyle.W_TICK, tag: "tick:x:\(k)")
        }
        for k in yt.indices(y0, y1) {
            if k == 0 && fr.hasY0 { continue }
            let y = Float(fr.py(yt.value(k)))
            c.line(ax - GStyle.TICK, y, ax + GStyle.TICK, y, width: GStyle.W_TICK, tag: "tick:y:\(k)")
        }

        c.layer = 5
        for i in 0..<m where xs[i].isFinite && ys[i].isFinite {
            c.dot(Float(fr.px(xs[i])), Float(fr.py(ys[i])), r: GStyle.DOT * 1.15, tag: "value:\(i)")
        }

        c.layer = 6
        let s = GStyle.NAME_SIZE
        if !p.xLabel.isEmpty { c.text(R + GStyle.AXIS_EXT - c.measure(p.xLabel, s), ay - GStyle.GAP * 1.2 - s, p.xLabel, s, tag: "axis:x") }
        if !p.yLabel.isEmpty { c.text(ax + GStyle.GAP * 1.6, T - GStyle.AXIS_EXT - s * 0.1, p.yLabel, s, tag: "axis:y") }
        let maxW = xt.indices(x0, x1).map { c.measure(xt.label($0), tsize) }.max() ?? 0
        let everyX = max(1, ktInt((Double(maxW + 0.014) / (xt.step * fr.sx)).rounded(.up)))
        for k in xt.indices(x0, x1) where k != 0 {
            let (t, forced) = c.tickText("x:\(k)", xt.label(k))
            guard let t else { continue }
            if !forced && (!p.showNumbers || k % everyX != 0) { continue }
            let w = c.measure(t, tsize)
            let box = c.textBox(Float(fr.px(xt.value(k))) - w / 2, ay + GStyle.TICK + GStyle.GAP * 0.5, t, tsize)
            if !forced && !c.fits(box) { continue }
            c.text(box.l, box.t, t, tsize, tag: "tick:x:\(k)")
        }
        let everyY = max(1, ktInt((Double(tsize + 0.008) / (yt.step * fr.sy)).rounded(.up)))
        for k in yt.indices(y0, y1) where k != 0 {
            let (t, forced) = c.tickText("y:\(k)", yt.label(k))
            guard let t else { continue }
            if !forced && (!p.showNumbers || k % everyY != 0) { continue }
            let w = c.measure(t, tsize)
            let box = c.textBox(ax - GStyle.TICK - GStyle.GAP * 0.6 - w, Float(fr.py(yt.value(k))) - tsize * 0.52, t, tsize)
            if !forced && !c.fits(box) { continue }
            c.text(box.l, box.t, t, tsize, tag: "tick:y:\(k)")
        }
        if fr.hasX0 && fr.hasY0 {
            let (t, forced) = c.tickText("x:0", "0")
            if let t, p.showNumbers || forced { c.text(ax - GStyle.GAP * 0.8 - c.measure(t, tsize), ay + GStyle.GAP * 0.5, t, tsize, tag: "tick:x:0") }
        }
        for i in 0..<m {
            guard let name = p.valueLabels[i] else { continue }
            if name.isEmpty || !xs[i].isFinite || !ys[i].isFinite { continue }
            let X = Float(fr.px(xs[i]))
            let Y = Float(fr.py(ys[i]))
            c.text(X + GStyle.DOT * 1.4, Y - GStyle.DOT * 1.4 - GStyle.KEY_SIZE, name, GStyle.KEY_SIZE, tag: "vlabel:\(i)")
        }
    }
}
