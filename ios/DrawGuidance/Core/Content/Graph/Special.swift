import Foundation

/// Sayı doğrusu, birim çember ve üç boyutlu eksenler (Android graph `Special`).
enum Special {
    // MARK: - NUMBER_LINE

    /// ticks → birim başına alt bölme (≤5: yok).
    private static let SUBDIV = [1, 1, 1, 1, 1, 2, 3, 4, 5, 6, 8, 10]

    static func subdivisions(_ ticks: Int) -> Int { SUBDIV[clampI(ticks, 1, 12) - 1] }

    /// Sayı doğrusu çentik anahtarı: tamsayıda "x:3", ara çentikte sadeleşmiş kesir "x:-1/2".
    static func numberLineKey(_ k: Int, _ sub: Int) -> String {
        if k % sub == 0 { return "x:\(k / sub)" }
        let g = Fmt.gcd(k, sub)
        return "x:\(k / g)/\(sub / g)"
    }

    static func numberLine(_ c: GCanvas) {
        let p = c.p
        let rep = clampI(p.repeat, 1, 20)
        let sub = subdivisions(p.ticks)
        let unit = p.tickStep > 0 ? Double(p.tickStep) : 1.0
        let lineY = c.H * 0.5 - 0.01
        let x0 = GStyle.PAD
        let x1 = c.W - GStyle.PAD
        let inner = GStyle.ARROW_LEN + 0.03
        let spacing = (x1 - x0 - 2 * inner) / Float(2 * rep)
        let cx = c.W / 2

        c.layer = 3
        c.line(x0 + GStyle.ARROW_LEN * 0.6, lineY, x1 - GStyle.ARROW_LEN * 0.6, lineY, width: 1.1)
        c.arrowHead(x1, lineY, 1, 0)
        c.arrowHead(x0, lineY, -1, 0)

        let ks = Array((-rep * sub)...(rep * sub))
        let dec = Fmt.decimals(unit)
        var texts: [Int: (String?, Bool)] = [:]
        for k in ks {
            let def = k % sub == 0 ? Fmt.num(Double(k / sub) * unit, dec) : ""
            texts[k] = c.tickText(numberLineKey(k, sub), def)
        }
        let majors = ks.filter { $0 % sub == 0 }.compactMap { texts[$0]!.0 }
        var size: Float = 0.05
        let unitW = majors.map { c.measure($0, 1) }.max() ?? 1
        size = min(size, max(spacing * 0.92 / unitW, 0.022))
        let every = max(1, ktInt(((unitW * size + 0.012) / spacing).rounded(.up)))
        let minusHalf = c.measure(Fmt.MINUS, size) / 2
        for k in ks {
            let x = cx + Float(k) * spacing / Float(sub)
            let major = k % sub == 0
            let h: Float = major ? 0.024 : 0.014
            let key = numberLineKey(k, sub)
            c.layer = 3
            c.line(x, lineY - h, x, lineY + h, width: major ? 1 : 0.8, tag: "tick:\(key)")
            let (s, forced) = texts[k]!
            guard let s else { continue }
            if !forced && (!p.showNumbers || (k / sub) % every != 0) { continue }
            let ts = major ? size : size * 0.8
            let w = c.measure(s, ts)
            let shift = s.hasPrefix(Fmt.MINUS) ? minusHalf : 0
            c.layer = 6
            c.text(x - w / 2 - shift, lineY + 0.024 + GStyle.GAP, s, ts, tag: "tick:\(key)")
        }
        // v45: formülün çözüm kümesi (kalın renkli aralıklar, dolu/boş uç noktaları) ve formül yazısı
        if !p.formula.allSatisfy(\.isWhitespace) { solutionSet(c, cx, lineY, spacing, unit, x0 + inner * 0.5, x1 - inner * 0.5) }
        // İşaretli noktalar
        for (i, v) in p.values.enumerated() {
            if !v.isFinite { continue }
            let x = cx + Float(Double(v) / unit) * spacing
            if x < x0 + inner * 0.5 || x > x1 - inner * 0.5 { continue }
            c.layer = 5
            c.dot(x, lineY, r: GStyle.DOT * 1.3, tag: "value:\(i)")
            let name = p.valueLabels[i] ?? ""
            if !name.isEmpty {
                c.layer = 6
                let ts: Float = 0.045
                c.text(x - c.measure(name, ts) / 2, lineY - 0.03 - ts, name, ts, color: c.color, tag: "vlabel:\(i)")
            }
        }
    }

    /// v45: sayı doğrusunda formülün çözüm kümesi. [xa]..[xb] çizilebilir yatay aralık.
    private static func solutionSet(_ c: GCanvas, _ cx: Float, _ lineY: Float, _ spacing: Float, _ unit: Double, _ xa: Float, _ xb: Float) {
        let p = c.p
        let lo = Double((xa - cx) / spacing) * unit
        let hi = Double((xb - cx) / spacing) * unit
        func X(_ v: Double) -> Float { Float(Double(cx) + (v / unit) * Double(spacing)) }
        let parts = GraphFormula.parts(p.formula).prefix(GraphFormula.MAX_CURVES)
        for (k, text) in parts.enumerated() {
            guard let set = GraphFormula.solve(text, lo, hi, deg: p.angleDeg) else { continue }
            let color = k == 0 ? c.color : Cartesian.EXTRA_COLORS[(k - 1) % Cartesian.EXTRA_COLORS.count]
            // birden çok formülde kümeler biraz yukarı kaydırılır (üst üste binmesin)
            let y = lineY - Float(k) * 0.035
            c.layer = 4
            for iv in set.intervals {
                let a = X(iv.a), b = X(iv.b)
                c.line(a, y, b, y, stroke: color, width: 3.2, tag: "curve")
                if iv.aInf { c.arrowHead(a - 0.004, y, -1, 0, color: color) } else { endDot(c, a, y, iv.aClosed, color) }
                if iv.bInf { c.arrowHead(b + 0.004, y, 1, 0, color: color) } else { endDot(c, b, y, iv.bClosed, color) }
            }
            for v in set.points {
                let x = X(v)
                if x < xa || x > xb { continue }
                c.layer = 5
                c.dot(x, y, r: GStyle.DOT * 1.3, fill: color, tag: "key")
            }
        }
        let label = p.tickLabels["formula"] ?? Graphs.formulaLabel(p)
        c.setDefault("formula", Graphs.formulaLabel(p))
        if !label.allSatisfy(\.isWhitespace) {
            c.layer = 6
            let ts: Float = 0.05
            c.text(GStyle.PAD, GStyle.PAD * 0.4, label, ts, color: c.color, tag: "formula")
        }
    }

    private static func endDot(_ c: GCanvas, _ x: Float, _ y: Float, _ closed: Bool, _ color: String) {
        c.layer = 5
        if closed { c.dot(x, y, r: GStyle.DOT * 1.3, fill: color, tag: "key") } else { c.openDot(x, y, r: GStyle.DOT * 1.3, stroke: color, tag: "key") }
    }

    // MARK: - UNIT_CIRCLE

    private static func exact(_ v: Double) -> String {
        let a = abs(v)
        let s: String
        if a < 1e-9 { return "0" }
        else if abs(a - 1) < 1e-9 { s = "1" }
        else if abs(a - 0.5) < 1e-9 { s = "1/2" }
        else if abs(a - 2.0.squareRoot() / 2) < 1e-9 { s = "√2/2" }
        else if abs(a - 3.0.squareRoot() / 2) < 1e-9 { s = "√3/2" }
        else { s = Fmt.auto(a, 3) }
        return v < 0 ? Fmt.MINUS + s : s
    }

    @inline(__always) private static func rad(_ deg: Double) -> Double { deg * (Double.pi / 180) }

    /// repeat = çeyrek sayısı, ticks = açı kümesi.
    static func angles(_ p: GraphParams) -> [Int] {
        let quadrants = clampI(p.repeat, 1, 4)
        let set = clampI(p.ticks, 1, 12)
        var out: [Int] = []
        for deg in Swift.stride(from: 15, to: 360, by: 15) {
            if deg % 90 == 0 || deg / 90 >= quadrants { continue }
            let r = deg % 90
            let ok: Bool
            if set <= 2 { ok = false } else if set == 3 { ok = r == 45 } else if set == 4 { ok = r == 30 || r == 60 } else { ok = r == 30 || r == 45 || r == 60 }
            if ok { out.append(deg) }
        }
        return out
    }

    static func unitCircle(_ c: GCanvas) {
        let p = c.p
        let angles = angles(p)
        let showCoords = p.showNumbers && !angles.isEmpty
        let ls: Float = 0.031
        let lrx: Float = showCoords ? 0.2 : 0.08
        let lry: Float = showCoords ? 0.085 : 0.07
        let r = max(min(c.W - 2 * (GStyle.PAD + lrx), c.H - 2 * (GStyle.PAD + lry)) / 2, 0.1)
        let cx = c.W / 2
        let cy = c.H / 2
        let ex = min(r * 1.3, c.W / 2 - GStyle.PAD)
        let ey = min(r * 1.3, c.H / 2 - GStyle.PAD)

        if p.grid {
            c.layer = 0
            c.add(c.circlePoints(cx, cy, r, 48), closed: true, fill: GColors.lighten(c.color, 0.93), width: 0)
        }
        c.layer = 3
        c.line(cx - ex, cy, cx + ex - GStyle.ARROW_LEN * 0.6, cy, width: GStyle.W_AXIS)
        c.arrowHead(cx + ex, cy, 1, 0)
        c.line(cx, cy + ey, cx, cy - ey + GStyle.ARROW_LEN * 0.6, width: GStyle.W_AXIS)
        c.arrowHead(cx, cy - ey, 0, -1)
        for (key, pt) in [("x:1", P(cx + r, cy)), ("x:-1", P(cx - r, cy))] {
            c.line(pt.x, cy - GStyle.TICK, pt.x, cy + GStyle.TICK, width: GStyle.W_TICK, tag: "tick:\(key)")
        }
        for (key, pt) in [("y:1", P(cx, cy - r)), ("y:-1", P(cx, cy + r))] {
            c.line(cx - GStyle.TICK, pt.y, cx + GStyle.TICK, pt.y, width: GStyle.W_TICK, tag: "tick:\(key)")
        }

        c.layer = 4
        c.ring(cx, cy, r, stroke: c.color, width: 1.6, tag: "curve")

        for deg in angles {
            let th = rad(Double(deg))
            let cs = Float(cos(th))
            let sn = Float(sin(th))
            let px = cx + r * cs
            let py = cy - r * sn
            c.layer = 2
            c.line(cx, cy, px, py, stroke: c.color, width: 0.8, line: .DASHED, alpha: 0.8)
            if p.ticks >= 6 {
                c.line(px, py, px, cy, stroke: c.pal.guide, width: 0.7, line: .DOTTED)
                c.line(px, py, cx, py, stroke: c.pal.guide, width: 0.7, line: .DOTTED)
            }
            c.layer = 5
            c.dot(px, py, r: GStyle.DOT, tag: "angle:\(deg)")
        }

        // Yazılar
        c.layer = 6
        let ns = GStyle.NAME_SIZE
        if !p.xLabel.isEmpty { c.text(cx + ex - c.measure(p.xLabel, ns), cy - GStyle.GAP * 1.2 - ns, p.xLabel, ns, tag: "axis:x") }
        if !p.yLabel.isEmpty { c.text(cx + GStyle.GAP * 1.6, cy - ey - ns * 0.1, p.yLabel, ns, tag: "axis:y") }
        let ts = GStyle.TICK_SIZE
        func tick(_ key: String, _ def: String, _ place: (Float, Float) -> P) {
            let (s, forced) = c.tickText(key, def)
            guard let s, p.showNumbers || forced else { return }
            let w = c.measure(s, ts)
            let at = place(w, ts)
            c.text(at.x, at.y, s, ts, tag: "tick:\(key)")
        }
        tick("x:1", "1") { _, _ in P(cx + r + 0.006, cy + 0.006) }
        tick("x:-1", "\(Fmt.MINUS)1") { w, _ in P(cx - r - 0.006 - w, cy + 0.006) }
        tick("y:1", "1") { _, h in P(cx + 0.008, cy - r - 0.006 - h) }
        tick("y:-1", "\(Fmt.MINUS)1") { _, _ in P(cx + 0.008, cy + r + 0.006) }
        tick("x:0", "O") { w, _ in P(cx - 0.008 - w, cy + 0.006) }

        for deg in angles {
            let th = rad(Double(deg))
            let cs = cos(th)
            let sn = sin(th)
            let px = cx + r * Float(cs)
            let py = cy - r * Float(sn)
            // Koordinat (cos, sin)
            let (coord, forcedC) = c.tickText("c:\(deg)", "(\(exact(cs)), \(exact(sn)))")
            if let coord, p.showNumbers || forcedC {
                let w = c.measure(coord, ls)
                var placed = false
                for off: Float in [0.016, 0.03, 0.046] {
                    let axp = px + off * Float(cs)
                    let ayp = py - off * Float(sn)
                    let bx: Float = cs > 0.1 ? axp : cs < -0.1 ? axp - w : axp - w / 2
                    let by: Float = sn > 0.1 ? ayp - ls : sn < -0.1 ? ayp : ayp - ls / 2
                    let box = c.textBox(bx, by, coord, ls)
                    if c.fits(box, margin: 0.002) || (forcedC && off > 0.04) {
                        c.text(bx, by, coord, ls, tag: "tick:c:\(deg)")
                        placed = true
                        break
                    }
                }
                if !placed && forcedC { c.text(px, py, coord, ls, tag: "tick:c:\(deg)") }
            }
            // Açı (derece)
            let (angle, forcedA) = c.tickText("a:\(deg)", "\(deg)°")
            if let angle, p.showNumbers || forcedA {
                let als = ls * 0.88
                let w = c.measure(angle, als)
                let rr = r * 0.8
                let shiftA = rad(Double(deg) - 6.0)
                let ax = cx + rr * Float(cos(shiftA))
                let ay = cy - rr * Float(sin(shiftA))
                let box = c.textBox(ax - w / 2, ay - als / 2, angle, als)
                if forcedA || c.fits(box, margin: 0.002) { c.text(box.l, box.t, angle, als, color: c.color, tag: "tick:a:\(deg)") }
            }
        }
    }

    // MARK: - AXES_3D

    private struct V3 { let x: Double, y: Double, z: Double; init(_ x: Double, _ y: Double, _ z: Double) { self.x = x; self.y = y; self.z = z } }

    private static func chunked3(_ v: [Float]) -> [[Float]] {
        Swift.stride(from: 0, to: v.count, by: 3).map { Array(v[$0..<min($0 + 3, v.count)]) }
    }

    static func axes3d(_ c: GCanvas) {
        let p = c.p
        let az = rad(32)
        let el = rad(22)
        let rx = [-sin(az), cos(az), 0.0]
        let ux = [-sin(el) * cos(az), -sin(el) * sin(az), cos(el)]
        let dv = [cos(el) * cos(az), cos(el) * sin(az), sin(el)]
        func sx(_ v: V3) -> Double { v.x * rx[0] + v.y * rx[1] + v.z * rx[2] }
        func sy(_ v: V3) -> Double { v.x * ux[0] + v.y * ux[1] + v.z * ux[2] }
        func depth(_ v: V3) -> Double { v.x * dv[0] + v.y * dv[1] + v.z * dv[2] }

        let expr: GExpression.Expr? = p.formula.allSatisfy(\.isWhitespace) ? nil
            : GExpression.parse(GraphFormula.parts(p.formula).first ?? "", p.angleDeg ? .DEG : .RAD)
        let n = clampI(p.ticks, 1, 12)
        var kz = 1.0
        let stepXY: Double
        let stepZ: Double
        let lx: Double
        let lz: Double
        let negXY: Double
        let negZ: Double
        var surface: [[V3?]]? = nil
        var zmin = 0.0
        var zmax = 0.0
        if let expr {
            let rr = Double(clampI(p.repeat, 1, 8))
            let g = clampI(ktInt(c.res / 26), 12, 30)
            var zs: [Double] = []
            var raw: [[Double]] = []
            for j in 0...g {
                var row: [Double] = []
                for i in 0...g {
                    let z = expr(-rr + 2 * rr * Double(i) / Double(g), -rr + 2 * rr * Double(j) / Double(g))
                    if z.isFinite { zs.append(z) }
                    row.append(z)
                }
                raw.append(row)
            }
            if !zs.isEmpty {
                zs.sort()
                zmin = zs[clampI(ktInt(Double(zs.count) * 0.02), 0, zs.count - 1)]
                zmax = zs[clampI(ktInt(Double(zs.count) * 0.98), 0, zs.count - 1)]
                if zs.first! >= zmin - 0.3 * (zmax - zmin) { zmin = zs.first! }
                if zs.last! <= zmax + 0.3 * (zmax - zmin) { zmax = zs.last! }
            }
            let span = zmax - zmin
            kz = span < 1e-9 ? 1 : (span >= 0.5 * rr && span <= 2.2 * rr) ? 1 : 1.2 * rr / span
            let kzv = kz
            surface = (0...g).map { j in
                (0...g).map { i -> V3? in
                    let z = raw[j][i]
                    if !z.isFinite { return nil }
                    return V3(-rr + 2 * rr * Double(i) / Double(g), -rr + 2 * rr * Double(j) / Double(g), clampD(z, zmin - 0.05 * span, zmax + 0.05 * span) * kzv)
                }
            }
            stepXY = p.tickStep > 0 ? Double(p.tickStep) : Fmt.niceCeil(rr * 1.4 / Double(n))
            let zExt = max(max(abs(zmax), abs(zmin)), 1e-9)
            stepZ = Fmt.niceCeil(zExt / Double(n))
            lx = rr * 1.45
            lz = max(max(zmax, 0) * kz * 1.25, 0.6 * rr)
            negXY = rr * 1.1
            negZ = zmin < 0 ? -zmin * kz * 1.1 : 0.35 * rr
        } else {
            stepXY = p.tickStep > 0 ? Double(p.tickStep) : 1
            stepZ = stepXY
            lx = (Double(n) + 0.8) * stepXY
            lz = lx
            negXY = 0.35 * Double(n) * stepXY
            negZ = negXY
        }

        // Sığdırma
        var probe: [V3] = [V3(lx, 0, 0), V3(-negXY, 0, 0), V3(0, lx, 0), V3(0, -negXY, 0), V3(0, 0, lz), V3(0, 0, -negZ)]
        for row in surface ?? [] { for v in row { if let v { probe.append(v) } } }
        for t in chunked3(p.values) where t.count == 3 { probe.append(V3(Double(t[0]), Double(t[1]), Double(t[2]) * kz)) }
        var minX = Double.greatestFiniteMagnitude, maxX = -Double.greatestFiniteMagnitude
        var minY = Double.greatestFiniteMagnitude, maxY = -Double.greatestFiniteMagnitude
        for v in probe { minX = min(minX, sx(v)); maxX = max(maxX, sx(v)); minY = min(minY, sy(v)); maxY = max(maxY, sy(v)) }
        let m: Float = 0.075
        let formulaRoom: Float = expr != nil ? GStyle.FORMULA_SIZE * 1.7 : 0
        let availW = c.W - 2 * (GStyle.PAD + m)
        let availH = c.H - 2 * (GStyle.PAD + m) - formulaRoom
        let s = max(min(Double(availW) / (maxX - minX), Double(availH) / (maxY - minY)), 1e-6)
        let ox = Double(c.W / 2) - s * (minX + maxX) / 2
        let oy = Double((c.H + formulaRoom) / 2) + s * (minY + maxY) / 2
        func X(_ v: V3) -> Float { Float(ox + s * sx(v)) }
        func Y(_ v: V3) -> Float { Float(oy - s * sy(v)) }
        func pt(_ v: V3) -> P { P(X(v), Y(v)) }

        // Taban ızgarası
        if p.grid && expr == nil {
            c.layer = 0
            for k in 1...n {
                let t = Double(k) * stepXY
                let e = Double(n) * stepXY
                c.add([pt(V3(t, 0, 0)), pt(V3(t, e, 0))], stroke: c.pal.grid, width: GStyle.W_GRID)
                c.add([pt(V3(0, t, 0)), pt(V3(e, t, 0))], stroke: c.pal.grid, width: GStyle.W_GRID)
            }
        }

        // Yüzey (ressam algoritması: uzaktan yakına)
        if let surface {
            c.layer = 1
            struct Quad { let pts: [V3], d: Double, zAvg: Double }
            var quads: [Quad] = []
            let g = surface.count - 1
            for j in 0..<g {
                for i in 0..<g {
                    guard let a = surface[j][i], let b = surface[j][i + 1], let cc = surface[j + 1][i + 1], let d = surface[j + 1][i] else { continue }
                    let list = [a, b, cc, d]
                    quads.append(Quad(pts: list, d: list.reduce(0) { $0 + depth($1) } / 4, zAvg: list.reduce(0) { $0 + $1.z } / 4 / kz))
                }
            }
            // Kotlin sortBy kararlıdır
            quads = quads.enumerated().sorted { $0.element.d != $1.element.d ? $0.element.d < $1.element.d : $0.offset < $1.offset }.map(\.element)
            let lv = [0.35, -0.45, 0.82]
            let ll = (lv.reduce(0) { $0 + $1 * $1 }).squareRoot()
            let light = lv.map { $0 / ll }
            let span = max(zmax - zmin, 1e-9)
            let low = GColors.lighten(c.color, 0.8)
            for q in quads {
                let a = q.pts[0], b = q.pts[1], d = q.pts[3]
                let e1 = [b.x - a.x, b.y - a.y, b.z - a.z]
                let e2 = [d.x - a.x, d.y - a.y, d.z - a.z]
                var nx = e1[1] * e2[2] - e1[2] * e2[1]
                var ny = e1[2] * e2[0] - e1[0] * e2[2]
                var nz = e1[0] * e2[1] - e1[1] * e2[0]
                let nl = max((nx * nx + ny * ny + nz * nz).squareRoot(), 1e-12)
                nx /= nl; ny /= nl; nz /= nl
                if nx * dv[0] + ny * dv[1] + nz * dv[2] < 0 { nx = -nx; ny = -ny; nz = -nz }
                let lam = max(0, nx * light[0] + ny * light[1] + nz * light[2])
                let t = Float(clampD((q.zAvg - zmin) / span, 0, 1))
                let base = GColors.mix(low, c.color, t * 0.9)
                let fill = GColors.mix(base, "#000000", Float((1 - lam) * 0.3))
                let edge = GColors.mix(fill, GColors.darken(c.color, 0.45), 0.45)
                c.add(q.pts.map { pt($0) }, closed: true, fill: fill, stroke: edge, width: 0.3, tag: "curve")
            }
        }

        // Eksenler
        let axes: [(String, V3, (Double, Double))] = [
            ("x", V3(1, 0, 0), (lx, negXY)),
            ("y", V3(0, 1, 0), (lx, negXY)),
            ("z", V3(0, 0, 1), (lz, negZ)),
        ]
        let o = V3(0, 0, 0)
        for (_, d, len) in axes {
            c.layer = 2
            let neg = V3(-d.x * len.1, -d.y * len.1, -d.z * len.1)
            c.add([pt(o), pt(neg)], width: 0.8, line: .DASHED, alpha: 0.65)
        }
        c.layer = 3
        for (name, d, len) in axes {
            let tip = V3(d.x * len.0, d.y * len.0, d.z * len.0)
            let tx = X(tip), ty = Y(tip)
            let dx = tx - X(o), dy = ty - Y(o)
            let l = max(hypotf(dx, dy), 1e-6)
            c.line(X(o), Y(o), tx - dx / l * GStyle.ARROW_LEN * 0.6, ty - dy / l * GStyle.ARROW_LEN * 0.6, width: GStyle.W_AXIS)
            c.arrowHead(tx, ty, dx, dy)
            let step = name == "z" ? stepZ : stepXY
            let kzAxis = name == "z" ? kz : 1.0
            var k = 1
            while Double(k) * step * kzAxis < len.0 - 0.35 * step * kzAxis && k <= 40 {
                let v = Double(k) * step * kzAxis
                let at = V3(d.x * v, d.y * v, d.z * v)
                let px = X(at), py = Y(at)
                let nx = -dy / l, ny = dx / l
                c.line(px - nx * GStyle.TICK, py - ny * GStyle.TICK, px + nx * GStyle.TICK, py + ny * GStyle.TICK, width: GStyle.W_TICK, tag: "tick:\(name):\(k)")
                k += 1
            }
        }

        // Yazılar
        c.layer = 6
        let ts = GStyle.TICK_SIZE
        for (name, d, len) in axes {
            let tip = V3(d.x * len.0, d.y * len.0, d.z * len.0)
            let tx = X(tip), ty = Y(tip)
            let dx = tx - X(o), dy = ty - Y(o)
            let l = max(hypotf(dx, dy), 1e-6)
            let ux1 = dx / l, uy1 = dy / l
            // Eksene dik, yazının gideceği yön: z için sol, diğerleri için aşağı
            var nx = -uy1, ny = ux1
            if name == "z" { if nx > 0 { nx = -nx; ny = -ny } } else if ny < 0 { nx = -nx; ny = -ny }
            let step = name == "z" ? stepZ : stepXY
            let kzAxis = name == "z" ? kz : 1.0
            var k = 1
            let spacing = step * kzAxis * s * hypot(sx(d), sy(d))
            var labels: [(Int, String)] = []
            while Double(k) * step * kzAxis < len.0 - 0.35 * step * kzAxis && k <= 40 {
                if let t = c.tickText("\(name):\(k)", Fmt.num(Double(k) * step, Fmt.decimals(step))).0, p.showNumbers || p.tickLabels["\(name):\(k)"] != nil {
                    labels.append((k, t))
                }
                k += 1
            }
            let maxW = labels.map { c.measure($0.1, ts) }.max() ?? 0
            let need = abs(ux1) * (maxW + 0.01) + abs(uy1) * (ts + 0.006)
            let every = max(1, ktInt((Double(need) / spacing).rounded(.up)))
            for (kk, t) in labels {
                if kk % every != 0 && p.tickLabels["\(name):\(kk)"] == nil { continue }
                let v = Double(kk) * step * kzAxis
                let at = V3(d.x * v, d.y * v, d.z * v)
                let w = c.measure(t, ts)
                let off = GStyle.TICK + GStyle.GAP * 0.6 + abs(nx) * w / 2 + abs(ny) * ts / 2
                let ccx = X(at) + nx * off
                let ccy = Y(at) + ny * off
                c.text(ccx - w / 2, ccy - ts / 2, t, ts, tag: "tick:\(name):\(kk)")
            }
            let label = name == "x" ? p.xLabel : name == "y" ? p.yLabel : p.zLabel
            if !label.isEmpty {
                let ns = GStyle.NAME_SIZE
                let w = c.measure(label, ns)
                let off = 0.012 + abs(ux1) * w / 2 + abs(uy1) * ns / 2
                let lcx = tx + ux1 * off
                let lcy = ty + uy1 * off
                c.text(lcx - w / 2, lcy - ns / 2, label, ns, tag: "axis:\(name)")
            }
        }
        do {
            let (t, forced) = c.tickText("x:0", "O")
            if let t, p.showNumbers || forced {
                let w = c.measure(t, ts)
                c.text(X(o) - w / 2, Y(o) + 0.014, t, ts, tag: "tick:x:0")
            }
        }

        // Noktalar: values = x, y, z üçlüleri
        for (i, t) in chunked3(p.values).enumerated() {
            if t.count < 3 || t.contains(where: { !$0.isFinite }) { continue }
            let x = Double(t[0]), y = Double(t[1]), z = Double(t[2])
            let P0 = V3(x, y, z * kz)
            let base = V3(x, y, 0)
            c.layer = 2
            c.add([pt(V3(x, 0, 0)), pt(base), pt(V3(0, y, 0))], stroke: c.pal.guide, width: 0.7, line: .DASHED)
            c.add([pt(base), pt(P0)], stroke: c.pal.guide, width: 0.7, line: .DASHED)
            c.add([pt(P0), pt(V3(0, 0, z * kz))], stroke: c.pal.guide, width: 0.7, line: .DASHED)
            c.layer = 5
            c.dot(X(P0), Y(P0), r: GStyle.DOT * 1.15, tag: "value:\(i)")
            c.layer = 6
            let name = p.valueLabels[i] ?? Cartesian.letter(i)
            let label = p.showNumbers ? "\(name)(\(Fmt.auto(x)), \(Fmt.auto(y)), \(Fmt.auto(z)))" : name
            if !label.isEmpty { c.text(X(P0) + 0.014, Y(P0) - 0.012 - GStyle.KEY_SIZE, label, GStyle.KEY_SIZE, tag: "vlabel:\(i)") }
        }

        // Formül kutusu
        let formula = p.tickLabels["formula"] ?? (expr != nil ? Graphs.formulaLabel(p) : "")
        c.setDefault("formula", p.formula.allSatisfy(\.isWhitespace) ? "" : Graphs.formulaLabel(p))
        if !formula.allSatisfy(\.isWhitespace) {
            let size = min(GStyle.FORMULA_SIZE, (c.W - 2 * GStyle.PAD) / (c.measure(formula, 1) + 0.9))
            let tw = c.measure(formula, size)
            let bx = GStyle.PAD
            let by = GStyle.PAD
            c.layer = 7
            c.add(c.roundRectPoints(bx, by, tw + size * 0.9, size * 1.66, size * 0.32), closed: true, fill: c.pal.paper, stroke: c.pal.boxBorder, width: 0.5, alpha: 0.93, tag: "formula")
            c.text(bx + size * 0.45, by + size * 0.33, formula, size, color: c.color, tag: "formula")
        }
    }
}
