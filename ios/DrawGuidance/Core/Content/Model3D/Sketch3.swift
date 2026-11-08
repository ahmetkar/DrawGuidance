import Foundation

/*
 * v27 3B çizim önerisi (Android `Sketch3`; cihazı yormayan makine öğrenmesi): iz çizim düzlemine indirilir, 17 boyutlu
 * dönme/ölçekten bağımsız öznitelikler çıkarılır, sabit tohumlu sentetik çizimlerle eğitilmiş Gauss Naive Bayes sınıf
 * olasılıklarını verir; kabul/ret ile cihazda kişiselleşir ve seçilen öneri izin konumuna/boyuna/yönüne oturtulur.
 */

@inline(__always) func ktHypotF(_ a: Float, _ b: Float) -> Float { Float(hypot(Double(a), Double(b))) }
@inline(__always) func ktAtan2F(_ y: Float, _ x: Float) -> Float { Float(atan2(Double(y), Double(x))) }
/// Kotlin `FloatArray.average()` (Double toplam).
func ktAverage(_ a: [Float]) -> Double {
    if a.isEmpty { return .nan }
    var s = 0.0
    for v in a { s += Double(v) }
    return s / Double(a.count)
}
func ktAverageD(_ a: [Double]) -> Double {
    if a.isEmpty { return .nan }
    var s = 0.0
    for v in a { s += v }
    return s / Double(a.count)
}
/// Java `Float.compare`.
func javaFloatCompare(_ a: Float, _ b: Float) -> Int {
    if a < b { return -1 }
    if a > b { return 1 }
    let x = Int32(bitPattern: a.isNaN ? 0x7FC0_0000 : a.bitPattern), y = Int32(bitPattern: b.isNaN ? 0x7FC0_0000 : b.bitPattern)
    return x == y ? 0 : (x < y ? -1 : 1)
}
@inline(__always) private func toDeg(_ r: Double) -> Double { r * 57.29577951308232 }

/// v27 çizilen izin sınıfı.
enum SketchClass: Int, CaseIterable { case LINE, ARC, WAVE, SPIRAL, CIRCLE, ELLIPSE, TRIANGLE, QUAD, POLYGON, STAR, BLOB }

/// v27 önerilebilen cisimler ve fırça dönüşümleri.
enum Suggest3Kind: Int, CaseIterable {
    case SPHERE, ELLIPSOID, CYLINDER, TORUS, DOME, CAPSULE, CUBE, BOX, SLAB, PYRAMID, CONE, PRISM3, PRISM6, STAR, ROD
    case TUBE, RIBBON, WALL, LATHE, INFLATE, SPRING

    var primitive: PrimitiveKind? {
        switch self {
        case .SPHERE, .ELLIPSOID: return .SPHERE
        case .CYLINDER, .ROD: return .CYLINDER
        case .TORUS: return .TORUS
        case .DOME: return .DOME
        case .CAPSULE: return .CAPSULE
        case .CUBE, .BOX, .SLAB: return .CUBE
        case .PYRAMID: return .PYRAMID
        case .CONE: return .CONE
        case .PRISM3: return .PRISM3
        case .PRISM6: return .PRISM6
        case .STAR: return .STAR
        default: return nil
        }
    }

    var brush: Brush3Kind? {
        switch self {
        case .TUBE, .SPRING: return .TUBE
        case .RIBBON: return .RIBBON
        case .WALL: return .WALL
        case .LATHE: return .LATHE
        case .INFLATE: return .INFLATE
        default: return nil
        }
    }
}

/// Düzlemdeki iz: 2B noktalar ve düzlemin dünya tabanı (merkez, sağ, yukarı, normal).
struct Sketch2 {
    let xs: [Float], ys: [Float]
    let origin: V3, u: V3, v: V3, n: V3
    var size: Int { xs.count }

    func world(_ x: Float, _ y: Float) -> V3 {
        V3(origin.x + u.x * x + v.x * y, origin.y + u.y * x + v.y * y, origin.z + u.z * x + v.z * y)
    }

    func dir(_ x: Float, _ y: Float) -> V3 { (u * x + v * y).normalized() }

    /// Dünya noktalarını düzleme indirir (`up` düzlemin yukarısı; normale paralelse dünya yukarısı).
    static func of(_ points: [V3], _ normal: V3, _ up: V3) -> Sketch2? {
        if points.count < 2 { return nil }
        let n = normal.normalized()
        if n.length < 0.5 { return nil }
        var vv = up - n * up.dot(n)
        if vv.length < 1e-4 {
            let alt = abs(n.y) < 0.9 ? V3.UP : V3(0, 0, -1)
            vv = alt - n * alt.dot(n)
        }
        let v = vv.normalized()
        let u = v.cross(n).normalized()
        var sx: Float = 0, sy: Float = 0, sz: Float = 0
        for p in points { sx += p.x; sy += p.y; sz += p.z }
        let c = Float(points.count)
        let o = V3(sx / c, sy / c, sz / c)
        return Sketch2(xs: points.map { ($0 - o).dot(u) }, ys: points.map { ($0 - o).dot(v) }, origin: o, u: u, v: v, n: n)
    }
}

/// 2B geometri yardımcıları (öznitelikler ve uydurma için).
enum SketchGeom {
    static func length(_ xs: [Float], _ ys: [Float]) -> Float {
        var l: Float = 0
        if xs.count > 1 { for i in 1..<xs.count { l += ktHypotF(xs[i] - xs[i - 1], ys[i] - ys[i - 1]) } }
        return l
    }

    /// Yay uzunluğuna göre eşit aralıklı `n` nokta; `closed` ise kapatan kenar da dahil.
    static func resample(_ xs: [Float], _ ys: [Float], _ n: Int, _ closed: Bool) -> ([Float], [Float])? {
        let m = xs.count
        if m < 2 { return nil }
        let count = closed ? m + 1 : m
        let px: [Float] = (0..<count).map { xs[$0 % m] }
        let py: [Float] = (0..<count).map { ys[$0 % m] }
        let total = length(px, py)
        if total < 1e-9 { return nil }
        let step = closed ? total / Float(n) : total / Float(n - 1)
        var ox = [Float](repeating: 0, count: n), oy = [Float](repeating: 0, count: n)
        ox[0] = px[0]; oy[0] = py[0]
        var k = 1
        var acc: Float = 0
        var i = 1
        var ax = px[0], ay = py[0]
        while i < count && k < n {
            let bx = px[i], by = py[i]
            let d = ktHypotF(bx - ax, by - ay)
            if acc + d >= step && d > 0 {
                let t = (step - acc) / d
                let nx = ax + (bx - ax) * t, ny = ay + (by - ay) * t
                ox[k] = nx; oy[k] = ny; k += 1
                ax = nx; ay = ny; acc = 0
            } else {
                acc += d
                ax = bx; ay = by; i += 1
            }
        }
        while k < n { ox[k] = px[count - 1]; oy[k] = py[count - 1]; k += 1 }
        return (ox, oy)
    }

    /// İşaretli alan (kapalı çokgen kabul edilir).
    static func signedArea(_ xs: [Float], _ ys: [Float]) -> Float {
        var a: Float = 0
        let n = xs.count
        for i in 0..<n {
            let j = (i + 1) % n
            a += xs[i] * ys[j] - xs[j] * ys[i]
        }
        return a / 2
    }

    /// Dışbükey zarf (monoton zincir), saat yönünün tersine indisler.
    static func hull(_ xs: [Float], _ ys: [Float]) -> [Int] {
        let n = xs.count
        if n < 3 { return Array(0..<n) }
        let idx = (0..<n).sorted { (a: Int, b: Int) -> Bool in
            let cx = javaFloatCompare(xs[a], xs[b])
            if cx != 0 { return cx < 0 }
            let cy = javaFloatCompare(ys[a], ys[b])
            if cy != 0 { return cy < 0 }
            return a < b
        }
        func cross(_ o: Int, _ a: Int, _ b: Int) -> Float { (xs[a] - xs[o]) * (ys[b] - ys[o]) - (ys[a] - ys[o]) * (xs[b] - xs[o]) }
        var out = [Int](repeating: 0, count: 2 * n)
        var k = 0
        for i in idx {
            while k >= 2 && cross(out[k - 2], out[k - 1], i) <= 0 { k -= 1 }
            out[k] = i; k += 1
        }
        let lower = k + 1
        for j in stride(from: n - 2, through: 0, by: -1) {
            let i = idx[j]
            while k >= lower && cross(out[k - 2], out[k - 1], i) <= 0 { k -= 1 }
            out[k] = i; k += 1
        }
        return Array(out.prefix(Swift.max(k - 1, 1)))
    }

    /// En küçük alanlı dikdörtgen: merkez, ana yön (birim) ve yarı boyutlar.
    struct Rect2 { let cx: Float, cy: Float, ax: Float, ay: Float, hw: Float, hh: Float; var area: Float { 4 * hw * hh } }

    static func minRect(_ xs: [Float], _ ys: [Float]) -> Rect2 {
        let h = hull(xs, ys)
        var best: Rect2? = nil
        var dirs: [(Float, Float)] = []
        if h.count >= 2 {
            for i in h.indices {
                let a = h[i], b = h[(i + 1) % h.count]
                let dx = xs[b] - xs[a], dy = ys[b] - ys[a]
                let l = ktHypotF(dx, dy)
                dirs.append(l < 1e-9 ? (1, 0) : (dx / l, dy / l))
            }
        } else { dirs = [(1, 0)] }
        for (ax, ay) in dirs {
            var minU = Float.greatestFiniteMagnitude, maxU = -Float.greatestFiniteMagnitude
            var minV = Float.greatestFiniteMagnitude, maxV = -Float.greatestFiniteMagnitude
            for i in h {
                let pu = xs[i] * ax + ys[i] * ay
                let pv = -xs[i] * ay + ys[i] * ax
                minU = Swift.min(minU, pu); maxU = Swift.max(maxU, pu); minV = Swift.min(minV, pv); maxV = Swift.max(maxV, pv)
            }
            let hw = (maxU - minU) / 2, hh = (maxV - minV) / 2
            if best == nil || 4 * hw * hh < best!.area {
                let cu = (maxU + minU) / 2, cv = (maxV + minV) / 2
                best = Rect2(cx: cu * ax - cv * ay, cy: cu * ay + cv * ax, ax: ax, ay: ay, hw: hw, hh: hh)
            }
        }
        return best!
    }

    /// Ana bileşenler: merkez, ana yön (birim) ve özdeğerler (l1 ≥ l2).
    struct Pca { let cx: Float, cy: Float, ax: Float, ay: Float, l1: Float, l2: Float }

    static func pca(_ xs: [Float], _ ys: [Float]) -> Pca {
        let n = xs.count, fn = Float(xs.count)
        var mx: Float = 0, my: Float = 0
        for i in 0..<n { mx += xs[i]; my += ys[i] }
        mx /= fn; my /= fn
        var sxx: Float = 0, syy: Float = 0, sxy: Float = 0
        for i in 0..<n {
            let dx = xs[i] - mx, dy = ys[i] - my
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        sxx /= fn; syy /= fn; sxy /= fn
        let tr = sxx + syy
        let det = sxx * syy - sxy * sxy
        let disc = Swift.max(tr * tr / 4 - det, 0).squareRoot()
        let l1 = tr / 2 + disc
        let l2 = Swift.max(tr / 2 - disc, 0)
        var ax: Float, ay: Float
        if abs(sxy) > 1e-12 {
            let vx = l1 - syy, vy = sxy
            let l = ktHypotF(vx, vy)
            ax = vx / l; ay = vy / l
        } else if sxx >= syy { ax = 1; ay = 0 } else { ax = 0; ay = 1 }
        return Pca(cx: mx, cy: my, ax: ax, ay: ay, l1: l1, l2: l2)
    }

    /// Kapalı çokgenin alan merkezi (alan ~0 ise ortalama).
    static func centroid(_ xs: [Float], _ ys: [Float]) -> (Float, Float) {
        var a: Float = 0, cx: Float = 0, cy: Float = 0
        let n = xs.count
        for i in 0..<n {
            let j = (i + 1) % n
            let c = xs[i] * ys[j] - xs[j] * ys[i]
            a += c; cx += (xs[i] + xs[j]) * c; cy += (ys[i] + ys[j]) * c
        }
        if abs(a) < 1e-9 { return (Float(ktAverage(xs)), Float(ktAverage(ys))) }
        return (cx / (3 * a), cy / (3 * a))
    }
}

/// v27 iz öznitelikleri.
enum SketchFeatures {
    /// Yeniden örnekleme noktası.
    static let N = 48
    static let DIM = 17
    /// Uçlar arası boşluk / uzunluk bu orandan küçükse iz kapalı sayılır.
    static let CLOSED_GAP: Float = 0.16

    static func closed(_ xs: [Float], _ ys: [Float]) -> Bool {
        let l = SketchGeom.length(xs, ys)
        if l < 1e-9 { return false }
        return ktHypotF(xs.last! - xs[0], ys.last! - ys[0]) / l < CLOSED_GAP
    }

    static func of(_ xs: [Float], _ ys: [Float]) -> [Float]? {
        let total = SketchGeom.length(xs, ys)
        if total < 1e-9 || xs.count < 3 { return nil }
        let gap = ktHypotF(xs.last! - xs[0], ys.last! - ys[0])
        let gapRatio = (gap / total).coerceIn(0, 1)
        let closed = gapRatio < CLOSED_GAP
        guard let (rx, ry) = SketchGeom.resample(xs, ys, N, closed) else { return nil }
        var f = [Float](repeating: 0, count: DIM)
        f[0] = gapRatio
        // Sehim: kirişe en uzak noktanın uzaklığı / uzunluk
        do {
            let dx = xs.last! - xs[0], dy = ys.last! - ys[0]
            let cl = ktHypotF(dx, dy)
            var sag: Float = 0
            for i in 0..<N {
                let d: Float = cl > 1e-6 * total ? abs((rx[i] - xs[0]) * dy - (ry[i] - ys[0]) * dx) / cl : ktHypotF(rx[i] - xs[0], ry[i] - ys[0])
                sag = Swift.max(sag, d)
            }
            f[1] = (sag / total).coerceIn(0, 1)
        }
        let area = abs(SketchGeom.signedArea(rx, ry))
        let per = total + gap
        f[2] = (4 * PI_F * area / (per * per)).coerceIn(0, 1)
        let hull = SketchGeom.hull(rx, ry)
        let hullArea = abs(SketchGeom.signedArea(hull.map { rx[$0] }, hull.map { ry[$0] }))
        f[3] = hullArea > 1e-12 ? (area / hullArea).coerceIn(0, 1) : 0
        let pca = SketchGeom.pca(rx, ry)
        f[4] = pca.l1 > 1e-12 ? (pca.l2 / pca.l1).squareRoot().coerceIn(0, 1) : 0
        var r = [Float](repeating: 0, count: N)
        var mean: Float = 0
        for i in 0..<N { r[i] = ktHypotF(rx[i] - pca.cx, ry[i] - pca.cy); mean += r[i] }
        mean /= Float(N)
        var sd: Float = 0
        for i in 0..<N { sd += (r[i] - mean) * (r[i] - mean) }
        sd = (sd / Float(N)).squareRoot()
        f[5] = mean > 1e-9 ? (sd / mean).coerceIn(0, 1.5) : 0
        f[6] = Float(corners(rx, ry, closed)) / 8
        do {
            var sumAbs: Float = 0, sumSigned: Float = 0
            let last = closed ? N : N - 1
            for i in 1..<last {
                let a = (i - 1 + N) % N, b = i, c = (i + 1) % N
                let t = turn(rx[b] - rx[a], ry[b] - ry[a], rx[c] - rx[b], ry[c] - ry[b])
                sumAbs += abs(t); sumSigned += t
            }
            f[7] = (sumAbs / (2 * PI_F)).coerceIn(0, 6) / 2
            f[8] = sumAbs > 1e-6 ? abs(sumSigned) / sumAbs : 0
        }
        let rect = SketchGeom.minRect(rx, ry)
        f[9] = rect.area > 1e-12 ? (area / rect.area).coerceIn(0, 1) : 0
        if mean > 1e-9 {
            for k in 1...6 {
                var re: Float = 0, im: Float = 0
                for i in 0..<N {
                    let a = 2 * PI_F * Float(k) * Float(i) / Float(N)
                    re += r[i] * ktCosF(a); im -= r[i] * ktSinF(a)
                }
                f[9 + k] = (ktHypotF(re, im) / (Float(N) * mean)).coerceIn(0, 1) * 2
            }
        }
        do {
            let mi = Float(N - 1) / 2
            var sxy: Float = 0, sxx: Float = 0, syy: Float = 0
            for i in 0..<N {
                let dx = Float(i) - mi, dy = r[i] - mean
                sxy += dx * dy; sxx += dx * dx; syy += dy * dy
            }
            f[16] = sxx > 0 && syy > 1e-12 ? (abs(sxy) / (sxx * syy).squareRoot()).coerceIn(0, 1) : 0
        }
        return f
    }

    private static func turn(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float) -> Float { ktAtan2F(ax * by - ay * bx, ax * bx + ay * by) }

    /// Köşe sayısı: pencereli dönüş açısı 42°'yi aşan yerel en büyükler.
    static func corners(_ rx: [Float], _ ry: [Float], _ closed: Bool, _ window: Int = 3, _ minDeg: Float = 42) -> Int {
        let n = rx.count
        var ang = [Float](repeating: 0, count: n)
        for i in 0..<n {
            let a = i - window, c = i + window
            if !closed && (a < 0 || c >= n) { continue }
            let ia = (a + n) % n, ic = c % n
            ang[i] = abs(turn(rx[i] - rx[ia], ry[i] - ry[ia], rx[ic] - rx[i], ry[ic] - ry[i]))
        }
        let minA = Float(toRadians(Double(minDeg)))
        var count = 0
        for i in 0..<n where ang[i] > minA {
            var best = true
            for d in -window...window {
                if d == 0 { continue }
                let j = i + d
                if !closed && (j < 0 || j >= n) { continue }
                let jj = (j + n) % n
                if ang[jj] > ang[i] || (ang[jj] == ang[i] && d < 0) { best = false; break }
            }
            if best { count += 1 }
        }
        return count
    }
}

/// v27 sentetik eğitim çizimleri (sabit tohum): gürültülü, döndürülmüş, ölçeklenmiş, başlangıcı kaydırılmış.
enum SketchSynth {
    typealias P = (Float, Float)

    static func sample(_ c: SketchClass, _ rnd: inout KotlinRandom) -> ([Float], [Float]) {
        var pts: [P]
        switch c {
        case .LINE:
            let bend = range(&rnd, -0.035, 0.035)
            pts = (0..<24).map { (i: Int) -> P in let t = Float(i) / 23; return (t, bend * 4 * t * (1 - t)) }
        case .ARC:
            let sweep = Float(toRadians(Double(range(&rnd, 70, 290))))
            pts = (0..<36).map { (i: Int) -> P in let a = sweep * Float(i) / 35; return (ktCosF(a), ktSinF(a)) }
        case .WAVE:
            if rnd.nextBoolean() {
                let k = range(&rnd, 1.6, 4), amp = range(&rnd, 0.07, 0.24)
                pts = (0..<60).map { (i: Int) -> P in let t = Float(i) / 59; return (t, amp * ktSinF(2 * PI_F * k * t)) }
            } else {
                let teeth = rnd.nextInt(3, 7), amp = range(&rnd, 0.08, 0.25)
                let v: [P] = (0..<(teeth * 2 + 1)).map { (i: Int) -> P in (Float(i) / (Float(teeth) * 2), i % 2 == 0 ? 0 : amp) }
                pts = polyline(v, 6)
            }
        case .SPIRAL:
            let turns = range(&rnd, 1.6, 4)
            let a0 = range(&rnd, 0, 0.12)
            let total = 2 * PI_F * turns
            let list: [P] = (0..<110).map { (i: Int) -> P in
                let th = total * Float(i) / 109
                let rr = a0 + (1 - a0) * th / total
                return (rr * ktCosF(th), rr * ktSinF(th))
            }
            pts = rnd.nextBoolean() ? list.reversed() : list
        case .CIRCLE:
            pts = curve(&rnd)
        case .ELLIPSE:
            let by = range(&rnd, 0.32, 0.78)
            pts = curve(&rnd, by: by)
        case .TRIANGLE:
            var v: [P]
            repeat {
                v = []
                for k in 0..<3 {
                    let a = 2 * PI_F * Float(k) / 3 + range(&rnd, -0.45, 0.45)
                    let x = ktCosF(a) * range(&rnd, 0.7, 1.2)
                    let y = ktSinF(a) * range(&rnd, 0.7, 1.2)
                    v.append((x, y))
                }
            } while minAngle(v) < 24
            pts = closedTrace(v, &rnd)
        case .QUAD:
            let h = rnd.nextFloat() < 0.4 ? range(&rnd, 0.85, 1) : range(&rnd, 0.25, 0.85)
            let j: Float = 0.06
            var v: [P] = []
            for (x, y) in [(Float(-1), -h), (1, -h), (1, h), (-1, h)] {
                let nx = x + range(&rnd, -j, j)
                let ny = y + range(&rnd, -j, j)
                v.append((nx, ny))
            }
            pts = closedTrace(v, &rnd)
        case .POLYGON:
            let k = rnd.nextInt(5, 8)
            var v: [P] = []
            for i in 0..<k { let a = 2 * PI_F * Float(i) / Float(k); let r = range(&rnd, 0.93, 1.07); v.append((r * ktCosF(a), r * ktSinF(a))) }
            pts = closedTrace(v, &rnd)
        case .STAR:
            let k = rnd.nextInt(5, 7), inner = range(&rnd, 0.35, 0.55)
            let v: [P] = (0..<(2 * k)).map { (i: Int) -> P in
                let a = PI_F * Float(i) / Float(k)
                let r: Float = i % 2 == 0 ? 1 : inner
                return (r * ktCosF(a), r * ktSinF(a))
            }
            pts = closedTrace(v, &rnd)
        case .BLOB:
            var amps: [Float] = [], phases: [Float] = []
            for _ in 0..<4 { amps.append(range(&rnd, 0.06, 0.26)) }
            for _ in 0..<4 { phases.append(range(&rnd, 0, 6.28)) }
            pts = curve(&rnd, radial: { (th: Float) -> Float in
                var s = 0.0
                for k in 0..<4 { s += Double(amps[k] * ktCosF(Float(k + 2) * th + phases[k])) }
                return 1 + Float(s)
            }, wob: 0)
        }
        // Döndür, ölçekle, el titremesi
        let rot = range(&rnd, 0, 2 * PI_F)
        let s = range(&rnd, 0.3, 3)
        let cr = ktCosF(rot), sr = ktSinF(rot)
        let jitter: Float = 0.009 * s
        var xs = [Float](repeating: 0, count: pts.count), ys = [Float](repeating: 0, count: pts.count)
        for i in pts.indices {
            let (x, y) = pts[i]
            xs[i] = s * (x * cr - y * sr) + range(&rnd, -jitter, jitter)
            ys[i] = s * (x * sr + y * cr) + range(&rnd, -jitter, jitter)
        }
        return (xs, ys)
    }

    private static func range(_ rnd: inout KotlinRandom, _ a: Float, _ b: Float) -> Float { a + rnd.nextFloat() * (b - a) }

    private static func curve(_ rnd: inout KotlinRandom, by: Float = 1, radial: (Float) -> Float = { _ in 1 }, wob: Float = 0.035) -> [P] {
        let start = range(&rnd, 0, 6.28)
        let sweep = 2 * PI_F * range(&rnd, 0.93, 1.06)
        let w1 = range(&rnd, 0, wob), w2 = range(&rnd, 0, 6.28), w3 = range(&rnd, 0, wob * 0.6)
        var out: [P] = []
        out.reserveCapacity(64)
        for i in 0..<64 {
            let th = start + sweep * Float(i) / 63
            let rr = radial(th) * (1 + w1 * ktSinF(2 * th + w2) + w3 * ktSinF(3 * th))
            out.append((rr * ktCosF(th), rr * by * ktSinF(th)))
        }
        return out
    }

    private static func polyline(_ v: [P], _ per: Int) -> [P] {
        var out: [P] = []
        for i in 0..<(v.count - 1) { for k in 0..<per {
            let t = Float(k) / Float(per)
            out.append((v[i].0 + (v[i + 1].0 - v[i].0) * t, v[i].1 + (v[i + 1].1 - v[i].1) * t))
        } }
        out.append(v[v.count - 1])
        return out
    }

    /// Çokgen kenarları boyunca kapalı iz: rastgele noktadan başlar, %94–105 tur.
    private static func closedTrace(_ v: [P], _ rnd: inout KotlinRandom) -> [P] {
        let dense = Array(polyline(v + [v[0]], 14).dropLast())
        let n = dense.count
        let start = rnd.nextInt(n)
        let count = Swift.max(Renderer3D.ktIntF(Float(n) * range(&rnd, 0.94, 1.05)), 3)
        return (0...count).map { dense[(start + $0) % n] }
    }

    private static func minAngle(_ v: [P]) -> Float {
        var m: Float = 180
        for i in 0..<3 {
            let a = v[i], b = v[(i + 1) % 3], c = v[(i + 2) % 3]
            let ux = b.0 - a.0, uy = b.1 - a.1
            let wx = c.0 - a.0, wy = c.1 - a.1
            let ang = Float(toDeg(Double(abs(ktAtan2F(ux * wy - uy * wx, ux * wx + uy * wy)))))
            m = Swift.min(m, ang)
        }
        return m
    }
}

/// Tek öneri ve puanı.
struct Suggestion3: Equatable { let kind: Suggest3Kind; let score: Float }

/// Çözümleme sonucu: en olası sınıf ve olasılığı, öneriler ve öznitelikler (öğrenme için).
struct Sketch3Result { let sketchClass: SketchClass; let confidence: Float; let suggestions: [Suggestion3]; let features: [Float] }

/// v27 Gauss Naive Bayes sınıflandırıcı: sentetik istatistikler + cihazda kişisel örnekler ve öneri tercihleri.
final class SketchModel {
    private let baseMean: [[Float]], baseVar: [[Float]]
    private let classes = SketchClass.allCases
    private let kinds = Suggest3Kind.allCases
    private var pn: [Float]
    private var pMean: [[Float]], pM2: [[Float]]
    private var accept: [[Float]], reject: [[Float]]
    /// v29 3B modunun küçük sinir ağı (yalnız kullanıcının kabul/retleriyle eğitilir).
    private let net: Sketch3Net
    private let featMean: [Float], featScale: [Float]

    private init(_ baseMean: [[Float]], _ baseVar: [[Float]]) {
        self.baseMean = baseMean; self.baseVar = baseVar
        let C = SketchClass.allCases.count, K = Suggest3Kind.allCases.count, D = SketchFeatures.DIM
        pn = [Float](repeating: 0, count: C)
        pMean = [[Float]](repeating: [Float](repeating: 0, count: D), count: C)
        pM2 = pMean
        accept = [[Float]](repeating: [Float](repeating: 0, count: K), count: C)
        reject = accept
        net = Sketch3Net(K)
        var fm = [Float](repeating: 0, count: D), fs = [Float](repeating: 0, count: D)
        for d in 0..<D { fm[d] = Float(ktAverage(baseMean.map { $0[d] })) }
        for d in 0..<D {
            let m = fm[d]
            var vs: [Float] = []
            for c in baseMean.indices { let dm = baseMean[c][d] - m; vs.append(baseVar[c][d] + dm * dm) }
            let v = Float(ktAverage(vs))
            fs[d] = 1 / Swift.max(v, SketchModel.VAR_FLOOR).squareRoot()
        }
        featMean = fm; featScale = fs
    }

    /// Ağın eğitildiği geri bildirim sayısı.
    var netSamples: Int { net.samples }

    /// v30: ağ yalnız çok parçalı (3+ köşeli) izlerde çalışır ve yalnız onlardan öğrenir.
    func usesNet(_ f: [Float]) -> Bool { javaRoundF(f[6] * 8) >= SketchModel.NET_MIN_CORNERS }

    private func scaled(_ f: [Float]) -> [Float] { (0..<SketchFeatures.DIM).map { ((f[$0] - featMean[$0]) * featScale[$0]).coerceIn(-6, 6) } }

    /// Sınıf olasılıkları (sınıf sırasıyla).
    func posterior(_ f: [Float]) -> [Float] {
        var logp = [Float](repeating: 0, count: classes.count)
        for c in classes.indices {
            let (mu, va) = stats(c)
            var s = 0.0
            for d in 0..<SketchFeatures.DIM {
                let diff = f[d] - mu[d]
                let q: Float = diff * diff / va[d]
                s += -0.5 * (Double(q) + log(Double(va[d])))
            }
            logp[c] = Float(s / SketchModel.TEMPERATURE)
        }
        let mx = logp.max()!
        var sum: Float = 0
        for c in logp.indices { logp[c] = ktFloatExp(logp[c] - mx); sum += logp[c] }
        for c in logp.indices { logp[c] /= sum }
        return logp
    }

    private func stats(_ c: Int) -> ([Float], [Float]) {
        let n = pn[c]
        if n < 0.5 { return (baseMean[c], baseVar[c]) }
        let ws = Float(SketchModel.SYNTH_PER_CLASS), wp = n * SketchModel.PERSONAL_WEIGHT
        var mu = [Float](repeating: 0, count: SketchFeatures.DIM), va = mu
        for d in 0..<SketchFeatures.DIM {
            let pv = n > 1 ? pM2[c][d] / n : baseVar[c][d]
            let bm = baseMean[c][d], pm = pMean[c][d]
            let m = (ws * bm + wp * pm) / (ws + wp)
            let e2 = (ws * (baseVar[c][d] + bm * bm) + wp * (pv + pm * pm)) / (ws + wp)
            mu[d] = m
            va[d] = Swift.max(e2 - m * m, SketchModel.VAR_FLOOR)
        }
        return (mu, va)
    }

    private static func candidateWeight(_ c: SketchClass, _ k: Suggest3Kind) -> Float? { CANDIDATES[c]?.first { $0.0 == k }?.1 }

    private func weight(_ c: Int, _ k: Suggest3Kind) -> Float {
        guard let w = SketchModel.candidateWeight(classes[c], k) else { return 0 }
        let a = accept[c][k.rawValue], r = reject[c][k.rawValue]
        return w * (0.5 + (a + 1) / (a + r + 2))
    }

    /// Öneriler: her öneri için Σ P(sınıf) × ağırlık; çizilen fırçayla aynı olan ve eşiğin altındakiler çıkarılır.
    func suggest(_ f: [Float], _ drawn: Brush3Kind, _ max: Int = 3) -> Sketch3Result {
        let post = posterior(f)
        let square = f[4] > 0.8
        var scores = [Float?](repeating: nil, count: kinds.count)
        for c in classes.indices {
            if post[c] < 0.02 { continue }
            for (k, _) in SketchModel.CANDIDATES[classes[c]] ?? [] {
                var w = weight(c, k)
                if classes[c] == .QUAD {
                    if k == .CUBE && !square { w *= 0.45 }
                    if k == .BOX && square { w *= 0.6 }
                }
                scores[k.rawValue] = (scores[k.rawValue] ?? 0) + post[c] * w
            }
        }
        if net.samples > 0 && usesNet(f) {
            let p = net.probs(scaled(f))
            let maxP = p.max()!.coerceAtLeast(1e-6)
            let blend = (Float(net.samples) / 6).coerceAtMost(1)
            for k in kinds.indices {
                guard let s = scores[k] else { continue }
                let rel = p[k] / maxP
                scores[k] = s * (1 - blend * (1 - (0.5 + rel) / 1.5))
            }
        }
        var entries: [(Suggest3Kind, Float)] = []
        for k in kinds where scores[k.rawValue] != nil {
            let s = scores[k.rawValue]!
            if s >= SketchModel.MIN_SCORE && (k.brush == nil || k.brush != drawn || k == .SPRING) { entries.append((k, s)) }
        }
        let list = entries.enumerated().sorted { a, b in a.element.1 != b.element.1 ? a.element.1 > b.element.1 : a.offset < b.offset }
            .prefix(max).map { Suggestion3(kind: $0.element.0, score: $0.element.1) }
        let best = SketchModel.argmax(post.count) { post[$0] }
        return Sketch3Result(sketchClass: classes[best], confidence: post[best], suggestions: Array(list), features: f)
    }

    private static func argmax(_ n: Int, _ key: (Int) -> Float) -> Int {
        var best = 0, bv = key(0)
        if n > 1 { for i in 1..<n { let v = key(i); if v > bv { best = i; bv = v } } }
        return best
    }

    /// Kabul edilen öneri: izin öznitelikleri öneriyi en çok destekleyen sınıfa eklenir; tercih sayısı artar.
    func learnAccept(_ f: [Float], _ k: Suggest3Kind) {
        let post = posterior(f)
        let c = SketchModel.argmax(classes.count) { post[$0] * (SketchModel.candidateWeight(self.classes[$0], k) ?? 0) }
        accept[c][k.rawValue] += 1
        if usesNet(f) { net.accept(scaled(f), k.rawValue) }
        if pn[c] >= SketchModel.PERSONAL_CAP {
            let keep = (SketchModel.PERSONAL_CAP - 1) / pn[c]
            pn[c] *= keep
            for d in 0..<SketchFeatures.DIM { pM2[c][d] *= keep }
        }
        pn[c] += 1
        for d in 0..<SketchFeatures.DIM {
            let delta = f[d] - pMean[c][d]
            pMean[c][d] += delta / pn[c]
            pM2[c][d] += delta * (f[d] - pMean[c][d])
        }
    }

    /// Kapatılan öneriler: en olası sınıfta hafif ret.
    func learnDismiss(_ f: [Float], _ shown: [Suggest3Kind]) {
        let post = posterior(f)
        let c = SketchModel.argmax(post.count) { post[$0] }
        for k in shown { reject[c][k.rawValue] += 0.5 }
        if usesNet(f) { net.reject(scaled(f), shown.map { $0.rawValue }) }
    }

    /// Kişisel durum (tercihlerde saklanır).
    func encode() -> String {
        var s = "v1"
        for c in classes.indices {
            if pn[c] > 0 {
                s += ";p,\(classes[c]),\(javaFloatString(pn[c]))"
                for v in pMean[c] { s += "," + javaFloatString(v) }
                for v in pM2[c] { s += "," + javaFloatString(v) }
            }
            for k in kinds.indices where accept[c][k] > 0 || reject[c][k] > 0 {
                s += ";k,\(classes[c]),\(kinds[k]),\(javaFloatString(accept[c][k])),\(javaFloatString(reject[c][k]))"
            }
        }
        if net.samples > 0 { s += ";n," + net.encode() }
        return s
    }

    private struct Bad: Error {}

    func decode(_ s: String?) {
        guard let s = s, !s.trimmingCharacters(in: .whitespaces).isEmpty, s.hasPrefix("v1") else { return }
        func cls(_ t: String) throws -> Int { guard let c = SketchClass.allCases.first(where: { "\($0)" == t }) else { throw Bad() }; return c.rawValue }
        func fl(_ t: String) throws -> Float { guard let v = Float(t.trimmingCharacters(in: .whitespaces)) else { throw Bad() }; return v }
        do {
            for rec in s.split(separator: ";", omittingEmptySubsequences: false).dropFirst() {
                let t = rec.split(separator: ",", omittingEmptySubsequences: false).map(String.init)
                switch t[0] {
                case "p":
                    if t.count < 2 { throw Bad() }
                    let c = try cls(t[1])
                    if t.count != 3 + 2 * SketchFeatures.DIM { continue }
                    pn[c] = try fl(t[2])
                    for d in 0..<SketchFeatures.DIM { pMean[c][d] = try fl(t[3 + d]); pM2[c][d] = try fl(t[3 + SketchFeatures.DIM + d]) }
                case "k":
                    if t.count < 5 { throw Bad() }
                    let c = try cls(t[1])
                    guard let k = Suggest3Kind.allCases.first(where: { "\($0)" == t[2] }) else { throw Bad() }
                    accept[c][k.rawValue] = try fl(t[3]); reject[c][k.rawValue] = try fl(t[4])
                case "n": try net.decode(Array(t.dropFirst()))
                default: break
                }
            }
        } catch {}
    }

    static let SYNTH_PER_CLASS = 90
    private static let PERSONAL_WEIGHT: Float = 4
    private static let PERSONAL_CAP: Float = 40
    private static let VAR_FLOOR: Float = 0.0025
    /// Bağımsızlık varsayımının aşırı güvenini yumuşatır.
    private static let TEMPERATURE = 3.0
    private static let MIN_SCORE: Float = 0.12
    /// v38 3B çizim ağının köşe eşiği.
    private static let NET_MIN_CORNERS = 3

    /// Sınıf → (öneri, ağırlık), sırayla.
    static let CANDIDATES: [SketchClass: [(Suggest3Kind, Float)]] = [
        .LINE: [(.ROD, 1), (.TUBE, 0.8), (.WALL, 0.6)],
        .ARC: [(.TUBE, 1), (.RIBBON, 0.7), (.LATHE, 0.6), (.WALL, 0.5)],
        .WAVE: [(.TUBE, 1), (.RIBBON, 0.8), (.WALL, 0.6)],
        .SPIRAL: [(.SPRING, 1), (.TUBE, 0.7)],
        .CIRCLE: [(.SPHERE, 1), (.CYLINDER, 0.85), (.TORUS, 0.7), (.DOME, 0.55)],
        .ELLIPSE: [(.ELLIPSOID, 1), (.CAPSULE, 0.8), (.CYLINDER, 0.55), (.INFLATE, 0.5)],
        .TRIANGLE: [(.CONE, 1), (.PYRAMID, 0.9), (.PRISM3, 0.75)],
        .QUAD: [(.CUBE, 1), (.BOX, 1), (.SLAB, 0.6), (.WALL, 0.45)],
        .POLYGON: [(.PRISM6, 1), (.INFLATE, 0.6), (.WALL, 0.5)],
        .STAR: [(.STAR, 1), (.WALL, 0.6), (.INFLATE, 0.5)],
        .BLOB: [(.INFLATE, 1), (.WALL, 0.8), (.LATHE, 0.35)],
    ]

    /// Sentetik istatistikler (süreç başına bir kez).
    private static let base: ([[Float]], [[Float]]) = { var r = KotlinRandom(2701); return train(&r, SYNTH_PER_CLASS) }()

    /// Yeni model (sentetik istatistiklerle; kişisel durum `decode` ile yüklenir).
    static func create() -> SketchModel { SketchModel(base.0, base.1) }

    static func train(_ rnd: inout KotlinRandom, _ perClass: Int) -> ([[Float]], [[Float]]) {
        let classes = SketchClass.allCases, D = SketchFeatures.DIM
        var mean = [[Float]](repeating: [Float](repeating: 0, count: D), count: classes.count)
        var varr = mean
        for c in classes.indices {
            var m2 = [Float](repeating: 0, count: D)
            var n = 0
            var guardN = 0
            while n < perClass && guardN < perClass * 4 {
                guardN += 1
                let (xs, ys) = SketchSynth.sample(classes[c], &rnd)
                guard let f = SketchFeatures.of(xs, ys) else { continue }
                n += 1
                for d in 0..<D {
                    let delta = f[d] - mean[c][d]
                    mean[c][d] += delta / Float(n)
                    m2[d] += delta * (f[d] - mean[c][d])
                }
            }
            for d in 0..<D { varr[c][d] = Swift.max(m2[d] / Float(Swift.max(n - 1, 1)), VAR_FLOOR) }
        }
        return (mean, varr)
    }
}

/// v29 3B modunun sinir ağı: 17 → 24 (ReLU) → öneri türü (softmax); kabul/ret adımlarıyla cihazda öğrenir.
final class Sketch3Net {
    static let HIDDEN = 24
    static let LR: Float = 0.15
    private let out: Int
    private let inDim = SketchFeatures.DIM
    private var w1: [Float]
    private var b1 = [Float](repeating: 0.05, count: Sketch3Net.HIDDEN)
    private var w2: [Float]
    private var b2: [Float]
    private(set) var samples = 0

    init(_ out: Int) {
        self.out = out
        w2 = [Float](repeating: 0, count: out * Sketch3Net.HIDDEN)
        b2 = [Float](repeating: 0, count: out)
        var rnd = KotlinRandom(3301)
        let sc: Float = (2 / Float(SketchFeatures.DIM)).squareRoot()
        w1 = (0..<(Sketch3Net.HIDDEN * SketchFeatures.DIM)).map { _ in (rnd.nextFloat() - 0.5) * 2 * sc }
    }

    private func hidden(_ x: [Float]) -> [Float] {
        (0..<Sketch3Net.HIDDEN).map { (j: Int) -> Float in
            var a = b1[j]
            for i in 0..<inDim { a += w1[j * inDim + i] * x[i] }
            return a > 0 ? a : 0
        }
    }

    private func softmax(_ h: [Float]) -> [Float] {
        var z = [Float](repeating: 0, count: out)
        for k in 0..<out { var a = b2[k]; for j in 0..<Sketch3Net.HIDDEN { a += w2[k * Sketch3Net.HIDDEN + j] * h[j] }; z[k] = a }
        let m = z.max()!
        var sum: Float = 0
        for k in 0..<out { z[k] = ktFloatExp(z[k] - m); sum += z[k] }
        for k in 0..<out { z[k] /= sum }
        return z
    }

    func probs(_ x: [Float]) -> [Float] { softmax(hidden(x)) }

    private func step(_ x: [Float], _ h: [Float], _ g: [Float], _ lr: Float) {
        let H = Sketch3Net.HIDDEN
        var dh = [Float](repeating: 0, count: H)
        for k in 0..<out {
            if g[k] == 0 { continue }
            for j in 0..<H { dh[j] += g[k] * w2[k * H + j]; w2[k * H + j] -= lr * g[k] * h[j] }
            b2[k] -= lr * g[k]
        }
        for j in 0..<H {
            if h[j] <= 0 { continue }
            let gj = dh[j].coerceIn(-1, 1) * 0.2
            for i in 0..<inDim { w1[j * inDim + i] -= lr * gj * x[i] }
            b1[j] -= lr * gj
        }
    }

    func accept(_ x: [Float], _ k: Int) {
        for _ in 0..<3 {
            let h = hidden(x), p = softmax(h)
            var g = p; g[k] -= 1
            step(x, h, g, Sketch3Net.LR)
        }
        samples += 1
    }

    func reject(_ x: [Float], _ shown: [Int]) {
        if shown.isEmpty { return }
        for k in shown {
            let h = hidden(x), p = softmax(h)
            let f = (p[k] / (1 - p[k]).coerceAtLeast(1e-4)).coerceAtMost(2)
            var g = p.map { -f * $0 }; g[k] += f
            step(x, h, g, Sketch3Net.LR * 0.4)
        }
        samples += 1
    }

    func encode() -> String {
        var s = String(samples)
        for a in [w1, b1, w2, b2] { for v in a { s += "," + javaFloatString(v) } }
        return s
    }

    private struct Bad: Error {}

    func decode(_ t: [String]) throws {
        let n = 1 + w1.count + b1.count + w2.count + b2.count
        if t.count != n { return }
        guard let sm = Int32(t[0]) else { throw Bad() }
        samples = Int(sm)
        var i = 1
        func next() throws -> Float { guard let v = Float(t[i]) else { throw Bad() }; i += 1; return v }
        for j in w1.indices { w1[j] = try next() }
        for j in b1.indices { b1[j] = try next() }
        for j in w2.indices { w2[j] = try next() }
        for j in b2.indices { b2[j] = try next() }
    }
}

/// v27 önerinin ize oturtulması: cisim parçası ya da fırça girdisi.
enum Sketch3Fit {
    /// Fırça dönüşümü: dünya noktaları, normal, yukarı, kapalılık ve (tornada) eksen; yayda kalınlık önerisi.
    struct BrushFit { let kind: Brush3Kind; let input: BrushInput; let width: Float?; let smoothing: Float? }

    /// Cisim önerisini ize oturtur (uygun değilse nil).
    static func solid(_ kind: Suggest3Kind, _ sk: Sketch2, _ id: String, _ index: Int, _ material: Material, _ minSize: Float = 1e-3) -> Part? {
        guard let prim = kind.primitive else { return nil }
        let xs = sk.xs, ys = sk.ys
        let closed = SketchFeatures.closed(xs, ys)
        guard let (rx, ry) = SketchGeom.resample(xs, ys, 96, closed) else { return nil }
        let (cx, cy): (Float, Float) = closed ? SketchGeom.centroid(rx, ry) : (Float(ktAverage(rx)), Float(ktAverage(ry)))
        var meanR: Float = 0, maxR: Float = 0, minR = Float.greatestFiniteMagnitude
        for i in rx.indices { let r = ktHypotF(rx[i] - cx, ry[i] - cy); meanR += r; maxR = Swift.max(maxR, r); minR = Swift.min(minR, r) }
        meanR /= Float(rx.count)
        let center = sk.world(cx, cy)
        let n = sk.n
        var base = ModelEdit.newPart(prim, id, index)
        base.material = material
        // Parçanın yerel Y'si ey (tam korunur), X'i ex'in ona dik bileşeni, Z = X × Y (sağ elli)
        func place(_ ex: V3, _ ey: V3, _ size: V3, _ at: V3? = nil, _ part: Part? = nil) -> Part? {
            if size.x < minSize || size.y < minSize || size.z < minSize { return nil }
            let y = ey.normalized(), x = (ex - y * ex.dot(y)).normalized()
            if x.length < 0.5 || y.length < 0.5 { return nil }
            let z = x.cross(y)
            var p = part ?? base
            p.position = at ?? center; p.rotation = Rot3.euler(x, y, z); p.scale = size
            return p
        }
        let d2 = 2 * meanR
        switch kind {
        case .SPHERE: return place(sk.u, sk.v, V3(d2, d2, d2))
        case .TORUS: return place(sk.u, n, V3(d2, d2, d2))
        case .CYLINDER:
            let p = SketchGeom.pca(rx, ry)
            let a = extent(rx, ry, cx, cy, p.ax, p.ay), b = extent(rx, ry, cx, cy, -p.ay, p.ax)
            return place(sk.dir(p.ax, p.ay), n, V3(2 * a, a + b, 2 * b))
        case .DOME:
            return place(sk.u, n, V3(d2, d2, d2), center + n * (0.5 * meanR))
        case .ELLIPSOID, .CAPSULE:
            let p = SketchGeom.pca(rx, ry)
            let a = extent(rx, ry, cx, cy, p.ax, p.ay), b = extent(rx, ry, cx, cy, -p.ay, p.ax)
            let major = sk.dir(p.ax, p.ay), minor = sk.dir(-p.ay, p.ax)
            return kind == .ELLIPSOID ? place(major, minor, V3(2 * a, 2 * b, 2 * b)) : place(minor, major, V3(2 * b, 2 * a, 2 * b))
        case .CUBE, .BOX, .SLAB:
            let r = SketchGeom.minRect(rx, ry)
            let upFirst = abs(r.ay) > abs(r.ax)
            let w: Float = upFirst ? r.hh * 2 : r.hw * 2
            let h: Float = upFirst ? r.hw * 2 : r.hh * 2
            let ex = upFirst ? sk.dir(r.ay, -r.ax) : sk.dir(r.ax, r.ay)
            let ey = upFirst ? sk.dir(r.ax, r.ay) : sk.dir(-r.ay, r.ax)
            let eyUp = ey.dot(sk.v) < 0 ? -ey : ey
            let exR = ex.dot(sk.u) < 0 ? -ex : ex
            let at = sk.world(r.cx, r.cy)
            let depth: Float
            switch kind {
            case .CUBE: depth = (w + h) / 2
            case .SLAB: depth = Swift.max(w, h) * 0.08
            default: depth = Swift.min(w, h)
            }
            let size: V3
            if kind == .CUBE { let s = (w + h) / 2; size = V3(s, s, s) } else { size = V3(w, h, depth) }
            return place(exR, eyUp, size, at)
        case .CONE, .PYRAMID:
            guard let (apex, b1, b2) = triangle(rx, ry) else { return nil }
            let mx = (b1.0 + b2.0) / 2, my = (b1.1 + b2.1) / 2
            let axis = sk.dir(apex.0 - mx, apex.1 - my)
            let height = ktHypotF(apex.0 - mx, apex.1 - my)
            let baseLen = ktHypotF(b2.0 - b1.0, b2.1 - b1.1)
            let side = sk.dir(b2.0 - b1.0, b2.1 - b1.1)
            return place(side, axis, V3(baseLen, height, baseLen), sk.world((apex.0 + mx) / 2, (apex.1 + my) / 2))
        case .PRISM3:
            guard let (apex, b1, b2) = triangle(rx, ry) else { return nil }
            let gx = (apex.0 + b1.0 + b2.0) / 3, gy = (apex.1 + b1.1 + b2.1) / 3
            let r = (ktHypotF(apex.0 - gx, apex.1 - gy) + ktHypotF(b1.0 - gx, b1.1 - gy) + ktHypotF(b2.0 - gx, b2.1 - gy)) / 3
            let ez = sk.dir(apex.0 - gx, apex.1 - gy)
            let ex = n.cross(ez)
            return place(ex, n, V3(2 * r, r * 1.2, 2 * r), sk.world(gx, gy))
        case .PRISM6, .STAR:
            var far = 0
            for i in rx.indices where ktHypotF(rx[i] - cx, ry[i] - cy) > ktHypotF(rx[far] - cx, ry[far] - cy) { far = i }
            let ez = sk.dir(rx[far] - cx, ry[far] - cy)
            let ex = n.cross(ez)
            if kind == .PRISM6 {
                return place(ex, n, V3(2 * meanR * 1.05, meanR, 2 * meanR * 1.05))
            }
            let tips = starTips(rx, ry, cx, cy).coerceIn(4, 8)
            let inner = (minR / maxR).coerceIn(0.2, 0.8)
            guard var spec = base.primitive else { return nil }
            spec.segments = tips; spec.thickness = inner
            return place(ex, n, V3(2 * maxR, 2 * maxR, 2 * maxR), nil, ModelEdit.setPrimitive(base, spec))
        case .ROD:
            let a = sk.world(xs[0], ys[0])
            let b = sk.world(xs.last!, ys.last!)
            let len = (b - a).length
            if len < minSize { return nil }
            let axis = (b - a).normalized()
            let d = Swift.max(len * 0.07, minSize * 2)
            return place(axis.cross(n), axis, V3(d, len, d), (a + b) * 0.5)
        default: return nil
        }
    }

    /// Fırça önerisi (tüp, şerit, duvar, torna, şişir, yay): izden fırça girdisi.
    static func brush(_ kind: Suggest3Kind, _ sk: Sketch2, _ points: [V3]) -> BrushFit? {
        guard let b = kind.brush else { return nil }
        if kind != .SPRING {
            return BrushFit(kind: b, input: BrushInput(points: points, pressures: nil, normal: sk.n, up: sk.v), width: nil, smoothing: nil)
        }
        let xs = sk.xs, ys = sk.ys
        let cx = Float(ktAverage(xs)), cy = Float(ktAverage(ys))
        var maxR: Float = 0
        for i in xs.indices { maxR = Swift.max(maxR, ktHypotF(xs[i] - cx, ys[i] - cy)) }
        if maxR < 1e-4 { return nil }
        guard let f = SketchFeatures.of(xs, ys) else { return nil }
        let turns = (f[7] * 2).coerceIn(2, 8)
        let radius = maxR * 0.85
        let height = radius * 2.2
        let wire = radius * 0.16
        let center = sk.world(cx, cy)
        let steps = Renderer3D.ktIntF(turns * 20).coerceIn(40, 150)
        let pts: [V3] = (0...steps).map { (i: Int) -> V3 in
            let t = Float(i) / Float(steps)
            let a = 2 * PI_F * turns * t
            let p1 = center + sk.u * (radius * ktCosF(a))
            let p2 = sk.n * (radius * ktSinF(a))
            return p1 + p2 + sk.v * (height * (t - 0.5))
        }
        return BrushFit(kind: .TUBE, input: BrushInput(points: pts, pressures: nil, normal: sk.u, up: sk.v, closed: false), width: wire, smoothing: 0)
    }

    private static func extent(_ xs: [Float], _ ys: [Float], _ cx: Float, _ cy: Float, _ ax: Float, _ ay: Float) -> Float {
        var m: Float = 0
        for i in xs.indices { m = Swift.max(m, abs((xs[i] - cx) * ax + (ys[i] - cy) * ay)) }
        return m
    }

    /// Zarftaki en büyük alanlı üçgen: tepe (en yüksek köşe) ve taban uçları.
    private static func triangle(_ xs: [Float], _ ys: [Float]) -> ((Float, Float), (Float, Float), (Float, Float))? {
        let h = SketchGeom.hull(xs, ys)
        if h.count < 3 { return nil }
        var best: Float = -1, bi = 0, bj = 1, bk = 2
        for i in h.indices { for j in (i + 1)..<h.count { for k in (j + 1)..<h.count {
            let a = abs((xs[h[j]] - xs[h[i]]) * (ys[h[k]] - ys[h[i]]) - (ys[h[j]] - ys[h[i]]) * (xs[h[k]] - xs[h[i]]))
            if a > best { best = a; bi = h[i]; bj = h[j]; bk = h[k] }
        } } }
        let p: [(Float, Float)] = [(xs[bi], ys[bi]), (xs[bj], ys[bj]), (xs[bk], ys[bk])]
        var apex = 0
        for i in 1..<3 where p[i].1 > p[apex].1 { apex = i }
        let base = (0...2).filter { $0 != apex }
        return (p[apex], p[base[0]], p[base[1]])
    }

    /// Yıldızın uç sayısı: merkez uzaklığının yerel en büyükleri (yumuşatılmış).
    private static func starTips(_ xs: [Float], _ ys: [Float], _ cx: Float, _ cy: Float) -> Int {
        let n = xs.count
        let r: [Float] = (0..<n).map { ktHypotF(xs[$0] - cx, ys[$0] - cy) }
        let s: [Float] = (0..<n).map { (i: Int) -> Float in (r[(i - 1 + n) % n] + 2 * r[i] + r[(i + 1) % n]) / 4 }
        let mean = Float(ktAverage(s))
        var count = 0
        for i in 0..<n where s[i] > mean {
            var peak = true
            for d in -4...4 where d != 0 && s[(i + d + n) % n] > s[i] { peak = false; break }
            if peak { count += 1 }
        }
        return count
    }
}

/// Döndürme yardımcıları.
enum Rot3 {
    /// Parçanın yerel eksenlerinin dünya yönlerinden derece cinsinden açılar (X, sonra Y, sonra Z).
    static func euler(_ ex: V3, _ ey: V3, _ ez: V3) -> V3 {
        let r: [Float] = [ex.x, ey.x, ez.x, ex.y, ey.y, ez.y, ex.z, ey.z, ez.z]
        let sy = (-r[6]).coerceIn(-1, 1)
        let y = Float(toDeg(asin(Double(sy))))
        if abs(sy) < 0.9999 {
            return V3(Float(toDeg(atan2(Double(r[7]), Double(r[8])))), y, Float(toDeg(atan2(Double(r[3]), Double(r[0])))))
        }
        return V3(Float(toDeg(atan2(Double(-r[5]), Double(r[4])))), y, 0)
    }
}

/// v27 öneri giriş noktası: dünya izinden öneriler (arka planda çağrılır).
enum Sketch3Suggest {
    static func analyze(_ points: [V3], _ normal: V3, _ up: V3, _ drawn: Brush3Kind, _ model: SketchModel, _ minLength: Float) -> (Sketch2, Sketch3Result)? {
        guard let sk = Sketch2.of(points, normal, up) else { return nil }
        if SketchGeom.length(sk.xs, sk.ys) < minLength { return nil }
        guard let f = SketchFeatures.of(sk.xs, sk.ys) else { return nil }
        let r = model.suggest(f, drawn)
        if r.suggestions.isEmpty { return nil }
        return (sk, r)
    }
}
