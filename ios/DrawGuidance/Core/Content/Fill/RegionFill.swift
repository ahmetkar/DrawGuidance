import Foundation

/// Alan alan boyama (boyama kitabı gibi; Android `RegionFill`): çizgilerle çevrili alanın sınırı bulunur. Çizgiler
/// ızgaraya kalınlıklarıyla çizilir, dokunulan yerden taşma doldurma yapılır (dışarı taşıyorsa alan kapalı değildir);
/// sınır bir hücre çizginin altına girer, piksel kenarlarından izlenir; adacıklar delik olarak kalır ve dış sınıra
/// köprüyle bağlanır (tek kapalı yol). Saf ve durumsuzdur.
enum RegionFill {

    /// Bir çizgi: noktalar (tuval birimi), kapalı mı, kalınlığı.
    struct Line {
        let points: [P]
        let closed: Bool
        let width: Float
        init(_ points: [P], _ closed: Bool, _ width: Float) { self.points = points; self.closed = closed; self.width = width }
    }

    /// Alanın ızgaradaki maskesi: hücre (x, y) tuvalde (ox + x / scale, oy + y / scale) köşesindedir.
    struct Mask {
        let cells: [Bool]
        let w: Int
        let h: Int
        let ox: Float
        let oy: Float
        let scale: Float
        func toCanvas(_ x: Int, _ y: Int) -> P { P(ox + Float(x) / scale, oy + Float(y) / scale) }
        func gx(_ x: Float) -> Float { (x - ox) * scale }
        func gy(_ y: Float) -> Float { (y - oy) * scale }
    }

    /// [tap] noktasını içeren kapalı alanın sınırı ya da alan kapalı değilse / çizginin üstüne dokunulduysa nil.
    static func region(_ lines: [Line], _ tap: P, resolution: Int = 0, gap: Float = 0) -> [P]? {
        guard let m = fillMask(lines, tap, resolution: resolution, gap: gap) else { return nil }
        let loops = boundaries(m.cells, m.w, m.h)
        if loops.isEmpty { return nil }
        let polys = loops.map { loop in simplify(loop.map { m.toCanvas($0.0, $0.1) }, 0.7 / m.scale) }.filter { $0.count >= 3 }
        if polys.isEmpty { return nil }
        var oi = 0
        for i in polys.indices where abs(area(polys[i])) > abs(area(polys[oi])) { oi = i }
        let holes = polys.indices.filter { $0 != oi }.map { orient(polys[$0], positive: false) }
        return keyhole(orient(polys[oi], positive: true), holes)
    }

    /// [region] ile aynı ızgara ve taşma doldurmayla alanın maskesi; alan kapalı değilse nil.
    static func fillMask(_ lines: [Line], _ tap: P, resolution: Int = 0, gap: Float = 0) -> Mask? {
        if lines.isEmpty { return nil }
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude
        var r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        var maxW: Float = 0, minW = Float.greatestFiniteMagnitude
        for line in lines {
            maxW = max(maxW, line.width); minW = min(minW, line.width)
            for p in line.points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        }
        if tap.x < l || tap.x > r || tap.y < t || tap.y > b { return nil }
        let span = max(max(r - l, b - t), 1)
        let res = resolution > 0 ? resolution : min(max(Int(span * 3 / max(minW, 0.2)), 500), 1400)
        let scale = Float(res) / span
        let gapCells = gap * scale
        let pad = 2 + Int((maxW * scale + gapCells).rounded(.up))
        let w = Int(((r - l) * scale).rounded(.up)) + pad * 2 + 1
        let h = Int(((b - t) * scale).rounded(.up)) + pad * 2 + 1
        let ox = l - Float(pad) / scale
        let oy = t - Float(pad) / scale
        func gx(_ x: Float) -> Float { (x - ox) * scale }
        func gy(_ y: Float) -> Float { (y - oy) * scale }
        var wall = [Bool](repeating: false, count: w * h)
        for line in lines {
            let rad = max(0.75, line.width * scale / 2) + gapCells
            let pts = line.closed && line.points.count > 2 ? line.points + [line.points[0]] : line.points
            if pts.count == 1 { stamp(&wall, w, h, gx(pts[0].x), gy(pts[0].y), gx(pts[0].x), gy(pts[0].y), rad) }
            if pts.count > 1 {
                for i in 1..<pts.count { stamp(&wall, w, h, gx(pts[i - 1].x), gy(pts[i - 1].y), gx(pts[i].x), gy(pts[i].y), rad) }
            }
        }
        var sx = min(max(Int(gx(tap.x)), 0), w - 1)
        var sy = min(max(Int(gy(tap.y)), 0), h - 1)
        if wall[sy * w + sx] {
            // Çizginin kenarına dokunulduysa en yakın boş hücre (2 hücre içinde)
            var found = false
            search: for d in 1...2 {
                for dy in -d...d {
                    for dx in -d...d {
                        let x = sx + dx, y = sy + dy
                        if x >= 0 && x < w && y >= 0 && y < h && !wall[y * w + x] { sx = x; sy = y; found = true; break search }
                    }
                }
            }
            if !found { return nil }
        }
        var mask = [Bool](repeating: false, count: w * h)
        var queue = [Int](repeating: 0, count: w * h)
        var qh = 0, qt = 0
        mask[sy * w + sx] = true
        queue[qt] = sy * w + sx; qt += 1
        while qh < qt {
            let i = queue[qh]; qh += 1
            let x = i % w, y = i / w
            if x == 0 || y == 0 || x == w - 1 || y == h - 1 { return nil } // dışarı taşıyor
            for j in [i - 1, i + 1, i - w, i + w] where !mask[j] && !wall[j] { mask[j] = true; queue[qt] = j; qt += 1 }
        }
        // Dolgu çizginin altına bir hücre (ve kapatılan boşluk kadar) girer
        let grow = 1 + Int(gapCells)
        var frontier = Array(queue[0..<qt])
        for _ in 0..<grow {
            var next: [Int] = []
            next.reserveCapacity(frontier.count * 4 + 4)
            let cap = frontier.count * 4 + 4
            for i in frontier {
                let x = i % w, y = i / w
                for dy in -1...1 {
                    for dx in -1...1 {
                        let nx = x + dx, ny = y + dy
                        if nx <= 0 || ny <= 0 || nx >= w - 1 || ny >= h - 1 { continue }
                        let j = ny * w + nx
                        if !mask[j] && wall[j] {
                            mask[j] = true
                            if next.count >= cap { break }
                            next.append(j)
                        }
                    }
                }
            }
            frontier = next
        }
        return Mask(cells: mask, w: w, h: h, ox: ox, oy: oy, scale: scale)
    }

    /// İki nokta arasındaki kalın çizgiyi (yarıçap [rad] hücre) ızgaraya işler.
    static func stamp(_ wall: inout [Bool], _ w: Int, _ h: Int, _ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float, _ rad: Float) {
        let minX = max(0, Int((min(x0, x1) - rad).rounded(.down)))
        let maxX = min(w - 1, Int((max(x0, x1) + rad).rounded(.up)))
        let minY = max(0, Int((min(y0, y1) - rad).rounded(.down)))
        let maxY = min(h - 1, Int((max(y0, y1) + rad).rounded(.up)))
        if minX > maxX || minY > maxY { return }
        let vx = x1 - x0, vy = y1 - y0
        let len2 = vx * vx + vy * vy
        let r2 = rad * rad
        for y in minY...maxY {
            for x in minX...maxX {
                let px = Float(x) + 0.5, py = Float(y) + 0.5
                let tt: Float = len2 < 1e-6 ? 0 : min(max(((px - x0) * vx + (py - y0) * vy) / len2, 0), 1)
                let dx = px - (x0 + vx * tt), dy = py - (y0 + vy * tt)
                if dx * dx + dy * dy <= r2 { wall[y * w + x] = true }
            }
        }
    }

    /// Maskenin sınırları (piksel kenarlarından; maske solda kalır): dış sınır ve delikler ayrı döngüler.
    /// Döngülerin başlangıcı Android'in `HashMap` gezinme sırasıyla aynı seçilir (çıktı birebir aynı olsun diye).
    static func boundaries(_ mask: [Bool], _ w: Int, _ h: Int) -> [[(Int, Int)]] {
        func m(_ x: Int, _ y: Int) -> Bool { x >= 0 && x < w && y >= 0 && y < h && mask[y * w + x] }
        let stride = w + 1
        var next: [Int: [Int]] = [:]
        var order: [Int: Int] = [:]  // anahtarın eklenme sırası
        func add(_ ax: Int, _ ay: Int, _ bx: Int, _ by: Int) {
            let k = ay * stride + ax
            if next[k] == nil { order[k] = order.count; next[k] = [] }
            next[k]!.append(by * stride + bx)
        }
        for y in 0..<h {
            for x in 0..<w where mask[y * w + x] {
                if !m(x, y - 1) { add(x + 1, y, x, y) }
                if !m(x - 1, y) { add(x, y, x, y + 1) }
                if !m(x, y + 1) { add(x, y + 1, x + 1, y + 1) }
                if !m(x + 1, y) { add(x + 1, y + 1, x + 1, y) }
            }
        }
        // java.util.HashMap: kapasite (2'nin kuvveti, doluluk 0.75), kova = (h ^ h>>>16) & (kap-1); kovada eklenme sırası
        var capacity = 16
        while Double(order.count) > Double(capacity) * 0.75 { capacity *= 2 }
        func bucket(_ k: Int) -> Int {
            let hv = UInt32(truncatingIfNeeded: k ^ (k >> 32))
            return Int((hv ^ (hv >> 16)) & UInt32(capacity - 1))
        }
        var keyed = next.keys.map { ($0, bucket($0), order[$0]!) }
        keyed.sort { $0.1 != $1.1 ? $0.1 < $1.1 : $0.2 < $1.2 }
        var cursor = 0
        var loops: [[(Int, Int)]] = []
        while !next.isEmpty {
            while next[keyed[cursor].0] == nil { cursor += 1 }
            let start = keyed[cursor].0
            var loop: [(Int, Int)] = []
            var cur = start
            var prev = -1
            var guardN = 0
            while guardN < 4_000_000 {
                guardN += 1
                guard var outs = next[cur] else { break }
                var ci = 0
                if outs.count > 1 && prev >= 0 {
                    // Çapraz dokunan hücrelerde sola dönüş tercih edilir (döngüler kesişmez)
                    let px = prev % stride, py = prev / stride
                    let cx = cur % stride, cy = cur / stride
                    let dx0 = cx - px, dy0 = cy - py
                    var best = Int.min
                    for (i, o) in outs.enumerated() {
                        let ox = o % stride - cx, oy = o / stride - cy
                        let v = -(dx0 * oy - dy0 * ox)
                        if v > best { best = v; ci = i }
                    }
                }
                let chosen = outs.remove(at: ci)
                if outs.isEmpty { next[cur] = nil } else { next[cur] = outs }
                loop.append((cur % stride, cur / stride))
                prev = cur
                cur = chosen
                if cur == start { break }
            }
            if loop.count >= 4 { loops.append(loop) }
        }
        return loops
    }

    static func area(_ p: [P]) -> Float {
        var a: Float = 0
        for i in p.indices { let q = p[(i + 1) % p.count]; a += p[i].x * q.y - q.x * p[i].y }
        return a / 2
    }

    static func orient(_ p: [P], positive: Bool) -> [P] { (area(p) > 0) == positive ? p : p.reversed() }

    /// Delikleri dış sınıra en yakın noktalardan sıfır genişlikte köprüyle bağlar (tek kapalı yol).
    static func keyhole(_ outer: [P], _ holes: [[P]]) -> [P] {
        var result = outer
        // Kotlin sortedByDescending kararlıdır
        let sorted = holes.enumerated().sorted { abs(area($0.element)) != abs(area($1.element)) ? abs(area($0.element)) > abs(area($1.element)) : $0.offset < $1.offset }.map(\.element)
        for hole in sorted {
            var bi = 0, bj = 0, bd = Float.greatestFiniteMagnitude
            for i in result.indices {
                for j in hole.indices {
                    let d = kHypot(result[i].x - hole[j].x, result[i].y - hole[j].y)
                    if d < bd { bd = d; bi = i; bj = j }
                }
            }
            var spliced: [P] = []
            spliced.reserveCapacity(result.count + hole.count + 2)
            spliced.append(contentsOf: result[0...bi])
            for k in 0...hole.count { spliced.append(hole[(bj + k) % hole.count]) }
            spliced.append(result[bi])
            if bi + 1 < result.count { spliced.append(contentsOf: result[(bi + 1)...]) }
            result = spliced
        }
        return result
    }

    /// Kapalı çokgeni sadeleştirir (piksel basamakları düzleşir).
    static func simplify(_ p: [P], _ eps: Float) -> [P] {
        if p.count < 8 { return p }
        let n = p.count
        var keep = [Bool](repeating: false, count: n)
        var far = 1, fd = -Float.greatestFiniteMagnitude
        for i in 1..<n { let d = kHypot(p[i].x - p[0].x, p[i].y - p[0].y); if d > fd { fd = d; far = i } }
        keep[0] = true; keep[far] = true
        let ext = p + [p[0]]
        var stack: [(Int, Int)] = [(0, far), (far, n)]
        while let (s, e) = stack.popLast() {
            if e <= s + 1 { continue }
            var best = -1, bd: Float = 0
            for i in (s + 1)..<e {
                let d = seg(ext[i], ext[s], ext[e])
                if d > bd { bd = d; best = i }
            }
            if best >= 0 && bd > eps { keep[best % n] = true; stack.append((s, best)); stack.append((best, e)) }
        }
        return p.indices.filter { keep[$0] }.map { p[$0] }
    }

    private static func seg(_ q: P, _ a: P, _ b: P) -> Float {
        let vx = b.x - a.x, vy = b.y - a.y
        let l2 = vx * vx + vy * vy
        if l2 < 1e-9 { return kHypot(q.x - a.x, q.y - a.y) }
        let t = min(max(((q.x - a.x) * vx + (q.y - a.y) * vy) / l2, 0), 1)
        return kHypot(q.x - (a.x + vx * t), q.y - (a.y + vy * t))
    }

    /// Nokta çokgenin içinde mi (tek-çift kuralı; köprülü deliklerde de doğru).
    static func contains(_ poly: [P], _ q: P) -> Bool {
        var inside = false
        var j = poly.count - 1
        for i in poly.indices {
            let a = poly[i], b = poly[j]
            if (a.y > q.y) != (b.y > q.y) && q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y + 1e-9) + a.x { inside.toggle() }
            j = i
        }
        return inside
    }

    static func areaOf(_ poly: [P]) -> Float { abs(area(poly)) }

    /// Çokgenin içinde kalan bir nokta (alan yeniden bulunurken tohum olarak); bulunamazsa nil.
    static func interiorPoint(_ poly: [P]) -> P? {
        if poly.count < 3 { return nil }
        var t = Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in poly { t = min(t, p.y); b = max(b, p.y) }
        for f: Float in [0.5, 0.35, 0.65, 0.2, 0.8] {
            let y = t + (b - t) * f
            var xs: [Float] = []
            var j = poly.count - 1
            for i in poly.indices {
                let a = poly[i], c = poly[j]
                if (a.y > y) != (c.y > y) { xs.append((c.x - a.x) * (y - a.y) / (c.y - a.y) + a.x) }
                j = i
            }
            xs.sort()
            var best: P?
            var bw: Float = 0
            var k = 0
            while k + 1 < xs.count {
                let wdt = xs[k + 1] - xs[k]
                if wdt > bw { bw = wdt; best = P((xs[k] + xs[k + 1]) / 2, y) }
                k += 2
            }
            if let best, contains(poly, best) { return best }
        }
        return nil
    }
}

/// Boya fırçası (Android `RegionClip`): fırça izi, başladığı alanın içinde kalacak biçimde kırpılır.
enum RegionClip {

    /// [seed] noktasının alanında kalan fırça izi parçaları; alan kapalı değilse nil, iz alana değmiyorsa boş.
    static func clip(_ lines: [RegionFill.Line], _ seed: P, _ stroke: [P], _ radius: Float, resolution: Int = 0, gap: Float = 0) -> [[P]]? {
        guard let mask = RegionFill.fillMask(lines, seed, resolution: resolution, gap: gap) else { return nil }
        return clip(mask, stroke, radius)
    }

    /// Bulunmuş alan maskesiyle kırpma.
    static func clip(_ mask: RegionFill.Mask, _ stroke: [P], _ radius: Float) -> [[P]] {
        if stroke.isEmpty { return [] }
        let w = mask.w, h = mask.h
        var paint = [Bool](repeating: false, count: w * h)
        let rad = max(0.75, radius * mask.scale)
        if stroke.count == 1 {
            let x = mask.gx(stroke[0].x), y = mask.gy(stroke[0].y)
            RegionFill.stamp(&paint, w, h, x, y, x, y, rad)
        }
        if stroke.count > 1 {
            for i in 1..<stroke.count {
                RegionFill.stamp(&paint, w, h, mask.gx(stroke[i - 1].x), mask.gy(stroke[i - 1].y), mask.gx(stroke[i].x), mask.gy(stroke[i].y), rad)
            }
        }
        var any = false
        for i in paint.indices {
            let on = paint[i] && mask.cells[i]
            paint[i] = on
            if on { any = true }
        }
        if !any { return [] }
        return polygons(paint, mask)
    }

    /// Maskenin parçaları tuval biriminde: her delik onu içeren en küçük dış sınıra köprüyle bağlanır.
    static func polygons(_ cells: [Bool], _ mask: RegionFill.Mask) -> [[P]] {
        let loops = RegionFill.boundaries(cells, mask.w, mask.h)
        if loops.isEmpty { return [] }
        let eps = 0.7 / mask.scale
        var outers: [[P]] = []
        var holes: [[P]] = []
        for loop in loops {
            let signed = RegionFill.area(loop.map { P(Float($0.0), Float($0.1)) })
            let poly = RegionFill.simplify(loop.map { mask.toCanvas($0.0, $0.1) }, eps)
            if poly.count < 3 { continue }
            if signed < 0 { outers.append(poly) } else { holes.append(poly) }
        }
        if outers.isEmpty { return [] }
        var owned = [[[P]]](repeating: [], count: outers.count)
        for hole in holes {
            let probe = hole[0]
            var owner = -1, oa = Float.greatestFiniteMagnitude
            for (i, o) in outers.enumerated() where RegionFill.contains(o, probe) {
                let a = abs(RegionFill.area(o))
                if a < oa { oa = a; owner = i }
            }
            if owner >= 0 { owned[owner].append(hole) }
        }
        return outers.indices.map { i in
            RegionFill.keyhole(RegionFill.orient(outers[i], positive: true), owned[i].map { RegionFill.orient($0, positive: false) })
        }
    }
}
