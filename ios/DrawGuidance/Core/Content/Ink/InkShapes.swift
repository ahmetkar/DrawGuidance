import Foundation

/// Elle çizilen tek çizgiden tanınan şeklin türü.
enum InkShapeKind: String, CaseIterable { case LINE, POLYLINE, CIRCLE, ELLIPSE, TRIANGLE, RECTANGLE, SQUARE, POLYGON }

/// Tanınan şekil: düzeltilmiş geometri (kapalıda son nokta ilk noktanın tekrarı), score 0..1.
struct InkShape: Equatable {
    let kind: InkShapeKind
    let points: [InkPoint]
    let closed: Bool
    let score: Float
}

@inline(__always) private func kRound(_ v: Double) -> Double { (v + 0.5).rounded(.down) }
private extension InkPoint {
    func with(x: Float? = nil, y: Float? = nil) -> InkPoint { InkPoint(x ?? self.x, y ?? self.y, t) }
}

/// v17 çizim düzeltme (Android `InkShapes` ile birebir): doğru, kırık çizgi, çember/elips, üçgen, dikdörtgen/kare, çokgen.
enum InkShapes {
    private static let N = 96

    static func recognize(_ raw: [InkPoint]) -> InkShape? {
        let pts = dedupe(raw)
        if pts.count < 2 { return nil }
        let length = pathLength(pts)
        let minX = pts.map(\.x).min()!, minY = pts.map(\.y).min()!, maxX = pts.map(\.x).max()!, maxY = pts.map(\.y).max()!
        let diag = kHypot(maxX - minX, maxY - minY)
        if length < 12 || diag < 10 { return nil }
        let r = resample(pts, N)
        let gap = dist(r[0], r[r.count - 1])
        let closed = gap < max(0.16 * diag, 0.08 * length) && length > diag * 1.6
        return closed ? closedShape(r, diag) : openShape(r, length, diag)
    }

    private static func avgD(_ v: [Float]) -> Float {
        var s = 0.0
        for x in v { s += Double(x) }
        return Float(s / Double(v.count))
    }

    private static func openShape(_ r: [InkPoint], _ length: Float, _ diag: Float) -> InkShape? {
        let a = r[0], b = r[r.count - 1]
        let chord = dist(a, b)
        let dev = r.map { segDist($0, a, b) }.max()!
        if chord > 0.8 * length && dev < max(0.07 * chord, 2.5) {
            let (p, q) = snapLine(a, b)
            return InkShape(kind: .LINE, points: densify([p, q], false), closed: false, score: (1 - dev / (0.07 * chord + 1e-3)).coerceIn(0.3, 1))
        }
        let corners = rdp(r, 0.06 * diag)
        if (3...5).contains(corners.count) {
            let poly = corners.map { r[$0] }
            let err = avgD(r.map { polyDist($0, poly, false) })
            var ok = true
            for i in 0..<(poly.count - 1) where !(dist(poly[i], poly[i + 1]) > 0.12 * diag) { ok = false; break }
            if err < 0.022 * diag && ok {
                let snapped = snapPolyline(poly)
                return InkShape(kind: .POLYLINE, points: densify(snapped, false), closed: false, score: (1 - err / (0.022 * diag)).coerceIn(0.3, 1))
            }
        }
        return nil
    }

    private static func closedShape(_ r: [InkPoint], _ diag: Float) -> InkShape? {
        let ring = Array(r.dropLast())
        let ellipse = fitEllipse(ring)
        let corners = rdpClosed(ring, 0.07 * diag)
        var polygon: InkShape?
        var polyErr = Float.greatestFiniteMagnitude
        if (3...8).contains(corners.count) {
            let poly = corners.map { ring[$0] }
            polyErr = avgD(ring.map { polyDist($0, poly, true) }) / diag
            if polyErr < 0.03 { polygon = makePolygon(poly, polyErr, diag) }
        }
        let ellipseErr = ellipse?.1 ?? Float.greatestFiniteMagnitude
        if polygon != nil && polyErr < 0.018 && polyErr * 2.2 < ellipseErr { return polygon }
        if let ellipse, ellipseErr < 0.07 { return ellipse.0 }
        return polygon
    }

    private static func fitEllipse(_ ring: [InkPoint]) -> (InkShape, Float)? {
        let n = ring.count
        var sx = 0.0, sy = 0.0
        for p in ring { sx += Double(p.x) }
        for p in ring { sy += Double(p.y) }
        let cx = sx / Double(n), cy = sy / Double(n)
        var sxx = 0.0, syy = 0.0, sxy = 0.0
        for p in ring { let dx = Double(p.x) - cx, dy = Double(p.y) - cy; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        sxx /= Double(n); syy /= Double(n); sxy /= Double(n)
        let tr = sxx + syy
        let det = sxx * syy - sxy * sxy
        let disc = sqrt(max(0.0, tr * tr / 4 - det))
        let l2 = tr / 2 - disc
        if l2 <= 1e-6 { return nil }
        var angle = 0.5 * atan2(2 * sxy, sxx - syy)
        let c0 = cos(angle), s0 = sin(angle)
        var minU = Double.greatestFiniteMagnitude, maxU = -Double.greatestFiniteMagnitude, minV = Double.greatestFiniteMagnitude, maxV = -Double.greatestFiniteMagnitude
        for p in ring {
            let dx = Double(p.x) - cx, dy = Double(p.y) - cy
            let u = dx * c0 + dy * s0, v = -dx * s0 + dy * c0
            minU = min(minU, u); maxU = max(maxU, u); minV = min(minV, v); maxV = max(maxV, v)
        }
        let du = (minU + maxU) / 2, dv = (minV + maxV) / 2
        let ecx = cx + du * c0 - dv * s0
        let ecy = cy + du * s0 + dv * c0
        var a = (maxU - minU) / 2
        var b = (maxV - minV) / 2
        if a < 1e-3 || b < 1e-3 { return nil }
        func err(_ a0: Double, _ b0: Double, _ ang: Double) -> Double {
            let c = cos(ang), s = sin(ang)
            var sum = 0.0
            for p in ring {
                let dx = Double(p.x) - ecx, dy = Double(p.y) - ecy
                let u = dx * c + dy * s, v = -dx * s + dy * c
                sum += abs(sqrt((u / a0) * (u / a0) + (v / b0) * (v / b0)) - 1.0)
            }
            return sum / Double(n)
        }
        let e = Float(err(a, b, angle))
        let ratio = Float(b / a)
        let circle = ratio > 0.86
        if circle { let m = (a + b) / 2; a = m; b = m; angle = 0 }
        else {
            let deg = toDegrees(angle)
            let snapped = kRound(deg / 90.0) * 90.0
            if abs(deg - snapped) < 8.0 { angle = toRadians(snapped) }
        }
        let pts = (0...72).map { i -> InkPoint in
            let t = Double(i) / 72.0 * 2 * Double.pi
            let u = a * cos(t), v = b * sin(t)
            return InkPoint(Float(ecx + u * cos(angle) - v * sin(angle)), Float(ecy + u * sin(angle) + v * cos(angle)))
        }
        let score = (1 - e / 0.085).coerceIn(0, 1)
        return (InkShape(kind: circle ? .CIRCLE : .ELLIPSE, points: pts, closed: true, score: score), e)
    }

    private static func makePolygon(_ poly: [InkPoint], _ err: Float, _ diag: Float) -> InkShape {
        let score = (1 - err / 0.03).coerceIn(0, 1)
        if poly.count == 4 && poly.indices.allSatisfy({ abs(cornerAngle(poly, $0) - 90) < 22 }) {
            var longest = 0
            for i in 1..<4 where dist(poly[i], poly[(i + 1) % 4]) > dist(poly[longest], poly[(longest + 1) % 4]) { longest = i }
            var ang = atan2(Double(poly[(longest + 1) % 4].y - poly[longest].y), Double(poly[(longest + 1) % 4].x - poly[longest].x))
            let deg = toDegrees(ang)
            let snapped = kRound(deg / 90.0) * 90.0
            if abs(deg - snapped) < 10.0 { ang = toRadians(snapped) }
            let c = cos(ang), s = sin(ang)
            var sx = 0.0, sy = 0.0
            for p in poly { sx += Double(p.x) }
            for p in poly { sy += Double(p.y) }
            let cx = sx / 4, cy = sy / 4
            var hu = 0.0, hv = 0.0
            for p in poly { let dx = Double(p.x) - cx, dy = Double(p.y) - cy; hu += abs(dx * c + dy * s); hv += abs(-dx * s + dy * c) }
            hu /= 4; hv /= 4
            let square = min(hu, hv) / max(hu, hv) > 0.88
            if square { let m = (hu + hv) / 2; hu = m; hv = m }
            func at(_ u: Double, _ v: Double) -> InkPoint { InkPoint(Float(cx + u * c - v * s), Float(cy + u * s + v * c)) }
            let rect = [at(-hu, -hv), at(hu, -hv), at(hu, hv), at(-hu, hv)]
            return InkShape(kind: square ? .SQUARE : .RECTANGLE, points: densify(rect + [rect[0]], true), closed: true, score: score)
        }
        let snapped = Array(snapPolyline(poly + [poly[0]]).dropLast())
        return InkShape(kind: poly.count == 3 ? .TRIANGLE : .POLYGON, points: densify(snapped + [snapped[0]], true), closed: true, score: score)
    }

    private static func cornerAngle(_ poly: [InkPoint], _ i: Int) -> Float {
        let p = poly[i], a = poly[(i - 1 + poly.count) % poly.count], b = poly[(i + 1) % poly.count]
        let v1x = a.x - p.x, v1y = a.y - p.y, v2x = b.x - p.x, v2y = b.y - p.y
        let c = (v1x * v2x + v1y * v2y) / (kHypot(v1x, v1y) * kHypot(v2x, v2y) + 1e-6)
        return Float(toDegrees(acos(Double(c.coerceIn(-1, 1)))))
    }

    private static func snapLine(_ a: InkPoint, _ b: InkPoint) -> (InkPoint, InkPoint) {
        let len = dist(a, b)
        let ang = toDegrees(atan2(Double(b.y - a.y), Double(b.x - a.x)))
        let snapped = kRound(ang / 15.0) * 15.0
        if abs(ang - snapped) > 5.0 { return (a, b) }
        let mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2
        let rad = toRadians(snapped)
        let dx = Float(cos(rad) * Double(len) / 2), dy = Float(sin(rad) * Double(len) / 2)
        return (InkPoint(mx - dx, my - dy), InkPoint(mx + dx, my + dy))
    }

    private static func snapPolyline(_ poly: [InkPoint]) -> [InkPoint] {
        var out = poly
        for i in 0..<(out.count - 1) {
            let p = out[i], q = out[i + 1]
            let dx = q.x - p.x, dy = q.y - p.y
            let len = kHypot(dx, dy)
            if len < 1e-3 { continue }
            if abs(dy) / len < 0.09 { let y = (p.y + q.y) / 2; out[i] = p.with(y: y); out[i + 1] = q.with(y: y) }
            else if abs(dx) / len < 0.09 { let x = (p.x + q.x) / 2; out[i] = p.with(x: x); out[i + 1] = q.with(x: x) }
        }
        if poly.count > 2 && poly[0] == poly[poly.count - 1] { out[out.count - 1] = out[0] }
        return out
    }

    /// Köşe noktaları arasına sık nokta koyar.
    static func densify(_ corners: [InkPoint], _ closed: Bool, _ step: Float = 3) -> [InkPoint] {
        var out: [InkPoint] = []
        if corners.count > 1 {
            for i in 0..<(corners.count - 1) {
                let a = corners[i], b = corners[i + 1]
                let n = max(1, (dist(a, b) / step).toInt())
                for k in 0..<n { out.append(InkPoint(a.x + (b.x - a.x) * Float(k) / Float(n), a.y + (b.y - a.y) * Float(k) / Float(n))) }
            }
        }
        out.append(corners[corners.count - 1])
        if closed && out[0] != out[out.count - 1] { out.append(out[0]) }
        return out
    }

    private static func dedupe(_ p: [InkPoint]) -> [InkPoint] {
        var out: [InkPoint] = []
        out.reserveCapacity(p.count)
        for q in p where out.isEmpty || dist(out[out.count - 1], q) > 0.01 { out.append(q) }
        return out
    }

    private static func pathLength(_ p: [InkPoint]) -> Float {
        var l: Float = 0
        if p.count > 1 { for i in 1..<p.count { l += dist(p[i - 1], p[i]) } }
        return l
    }

    private static func resample(_ p: [InkPoint], _ n: Int) -> [InkPoint] {
        let total = pathLength(p)
        let step = total / Float(n - 1)
        var out: [InkPoint] = [p[0]]
        var acc: Float = 0
        var i = 1
        var prev = p[0]
        while i < p.count && out.count < n - 1 {
            let cur = p[i]
            let d = dist(prev, cur)
            if acc + d >= step && d > 0 {
                let t = (step - acc) / d
                let q = InkPoint(prev.x + (cur.x - prev.x) * t, prev.y + (cur.y - prev.y) * t)
                out.append(q)
                prev = q
                acc = 0
            } else {
                acc += d
                prev = cur
                i += 1
            }
        }
        while out.count < n { out.append(p[p.count - 1]) }
        return out
    }

    @inline(__always) static func dist(_ a: InkPoint, _ b: InkPoint) -> Float { kHypot(a.x - b.x, a.y - b.y) }

    private static func segDist(_ p: InkPoint, _ a: InkPoint, _ b: InkPoint) -> Float {
        let vx = b.x - a.x, vy = b.y - a.y
        let l2 = vx * vx + vy * vy
        if l2 < 1e-9 { return dist(p, a) }
        let t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / l2).coerceIn(0, 1)
        return kHypot(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
    }

    private static func polyDist(_ p: InkPoint, _ poly: [InkPoint], _ closed: Bool) -> Float {
        var best = Float.greatestFiniteMagnitude
        let n = closed ? poly.count : poly.count - 1
        for i in 0..<n { best = min(best, segDist(p, poly[i], poly[(i + 1) % poly.count])) }
        return best
    }

    private static func rdp(_ p: [InkPoint], _ eps: Float) -> [Int] {
        var keep = [Bool](repeating: false, count: p.count)
        keep[0] = true; keep[p.count - 1] = true
        var stack: [(Int, Int)] = [(0, p.count - 1)]
        while let (s, e) = stack.popLast() {
            var best = -1
            var bd: Float = 0
            if e > s + 1 { for i in (s + 1)..<e { let d = segDist(p[i], p[s], p[e]); if d > bd { bd = d; best = i } } }
            if best >= 0 && bd > eps { keep[best] = true; stack.append((s, best)); stack.append((best, e)) }
        }
        return p.indices.filter { keep[$0] }
    }

    private static func rdpClosed(_ ring: [InkPoint], _ eps: Float) -> [Int] {
        let n = ring.count
        var i0 = 0, i1 = 0
        var bd: Float = 0
        for i in 0..<n { for j in (i + 1)..<max(n, i + 1) { let d = dist(ring[i], ring[j]); if d > bd { bd = d; i0 = i; i1 = j } } }
        let rot = Array(ring[i0...]) + Array(ring[..<i0])
        let k = (i1 - i0 + n) % n
        let first = rdp(Array(rot[0...k]), eps)
        let second = rdp(Array(rot[k..<n]) + [rot[0]], eps).map { $0 + k }.filter { $0 < n }
        var seen = Set<Int>()
        let idx = (first + second).filter { seen.insert($0).inserted }.sorted()
        let corners = idx.map { rot[$0] }
        let kept = corners.indices.filter { c in corners.count <= 3 || cornerAngle(corners, c) < 165 }.map { idx[$0] }
        return kept.map { ($0 + i0) % n }.sorted()
    }
}
