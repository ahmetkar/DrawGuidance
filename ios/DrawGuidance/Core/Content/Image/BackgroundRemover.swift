import Foundation

/// v30 arka plan kaldırma ayarları (Android `BackgroundOptions`). Dokunma noktaları resme göre oranlıdır (0..1).
struct BackgroundOptions: Equatable {
    /// 0..1: arka plan sayılan renk aralığının genişliği.
    var tolerance: Float = 0.5
    /// 0..1: kenar yumuşaklığı.
    var feather: Float = 0.4
    /// Kenara değmeyen ama arka plan renginde olan iç boşluklar da kaldırılır.
    var removeHoles: Bool = true
    /// Kenardan otomatik bulma (kapalıyken yalnız dokunulan yerler kaldırılır).
    var auto: Bool = true
    /// Dokunarak kaldırılan bölgeler.
    var removeAt: [(Float, Float)] = []
    /// Dokunarak geri getirilen bölgeler.
    var keepAt: [(Float, Float)] = []

    static func == (a: BackgroundOptions, b: BackgroundOptions) -> Bool {
        a.tolerance == b.tolerance && a.feather == b.feather && a.removeHoles == b.removeHoles && a.auto == b.auto &&
            a.removeAt.map { [$0.0, $0.1] } == b.removeAt.map { [$0.0, $0.1] } && a.keepAt.map { [$0.0, $0.1] } == b.keepAt.map { [$0.0, $0.1] }
    }
}

/// Maskenin özeti (arayüzde gösterilir).
struct BackgroundMask {
    let w: Int
    let h: Int
    let alpha: [Float]
    let removedRatio: Float
    let background: [UInt32]
}

/// v30 arka plan kaldırma (Android `BackgroundRemover` ile birebir; cihazda, ağ ve model gerekmez): kenar renkleri
/// Lab uzayında kümelenir, kenardan bölge büyütülür, iç boşluklar ve dokunma noktaları işlenir, kalıntılar temizlenir,
/// kenar yumuşatılıp arka plan rengi kenar piksellerinden ayrıştırılır. Maske en fazla [WORK_SIDE] kenarlı çalışma
/// boyutunda hesaplanır ve tam boyuta çift doğrusal büyütülür.
enum BackgroundRemover {
    static let WORK_SIDE = 900

    @inline(__always) private static func roundK(_ v: Float) -> Int { Int((v + 0.5).rounded(.down)) }

    /// [src] (w×h ARGB) için maskeyi hesaplar (çalışma boyutunda).
    static func mask(_ src: [UInt32], _ w: Int, _ h: Int, _ opt: BackgroundOptions, cancelled: () -> Bool = { false }) -> BackgroundMask {
        precondition(src.count >= w * h && w > 0 && h > 0)
        let scale = min(1, Float(WORK_SIDE) / Float(max(w, h)))
        let ww = max(1, roundK(Float(w) * scale)), wh = max(1, roundK(Float(h) * scale))
        let small = scale < 1 ? resample(src, w, h, ww, wh) : src
        let n = ww * wh
        var lab = [Float](repeating: 0, count: n * 3)
        for i in 0..<n { toLab(small[i], &lab, i * 3) }
        let srcAlpha = (0..<n).map { Int(small[$0] >> 24) }
        let tol = min(max(opt.tolerance, 0), 1)
        let tGlobal = 7 + tol * 38
        let tLocal = 2.5 + tol * 9

        // 1. Arka plan renkleri
        let centers = opt.auto ? borderCenters(lab, srcAlpha, ww, wh) : []
        var bg = [UInt8](repeating: 0, count: n)
        for i in 0..<n where srcAlpha[i] < 8 { bg[i] = 1 }

        func distBg(_ i: Int) -> Float {
            var best = Float.greatestFiniteMagnitude
            for c in centers { let d = dist(lab, i, c); if d < best { best = d } }
            return best
        }

        var queue = [Int](repeating: 0, count: n)
        // 2. Kenardan bölge büyütme
        if !centers.isEmpty {
            var head = 0, tail = 0
            func seed(_ i: Int) { if bg[i] == 0 && distBg(i) < tGlobal { bg[i] = 1; queue[tail] = i; tail += 1 } }
            for x in 0..<ww { seed(x); seed((wh - 1) * ww + x) }
            for y in 0..<wh { seed(y * ww); seed(y * ww + ww - 1) }
            for i in 0..<n where srcAlpha[i] < 8 && tail < n { queue[tail] = i; tail += 1 }
            while head < tail {
                if head % 65536 == 0 && cancelled() { break }
                let p = queue[head]; head += 1
                let x = p % ww, y = p / ww
                for k in 0..<4 {
                    let nx = x + DX[k], ny = y + DY[k]
                    if nx < 0 || ny < 0 || nx >= ww || ny >= wh { continue }
                    let q = ny * ww + nx
                    if bg[q] != 0 { continue }
                    let step = dist2(lab, p, q)
                    let d = distBg(q)
                    if (d < tGlobal && step < tLocal * 3) || (step < tLocal && d < tGlobal * 2.2) { bg[q] = 1; queue[tail] = q; tail += 1 }
                }
            }
            // 3. İç boşluklar
            if opt.removeHoles { removeHoles(lab, &bg, ww, wh, centers, tGlobal * 0.75, max(12, n / 2500), &queue) }
        }

        // 4. Dokunma noktaları (önce kaldırma, sonra geri getirme)
        for (u, v) in opt.removeAt { flood(lab, &bg, ww, wh, u, v, 1, tGlobal * 1.1, tLocal * 1.2, &queue) }
        for (u, v) in opt.keepAt { flood(lab, &bg, ww, wh, u, v, 0, tGlobal * 1.1, tLocal * 1.2, &queue) }

        // 5. Temizlik
        let minArea = max(6, n / 1500)
        cleanComponents(&bg, ww, wh, 0, minArea, &queue)
        cleanComponents(&bg, ww, wh, 1, max(4, n / 6000), &queue, borderSafe: true)
        for i in 0..<n where srcAlpha[i] < 8 { bg[i] = 1 }

        // 6. Yumuşak kenar
        let alpha: [Float] = bg.map { $0 == 1 ? 0 : 1 }
        let radius = (0.6 + min(max(opt.feather, 0), 1) * 2.4) * max(1, Float(max(ww, wh)) / 900)
        let soft = softEdges(alpha, ww, wh, radius)
        var removed = 0
        for i in 0..<n where soft[i] < 0.5 { removed += 1 }
        let bgColors = centers.map(labToRgb)
        return BackgroundMask(w: ww, h: wh, alpha: soft, removedRatio: Float(removed) / Float(n), background: bgColors)
    }

    /// Maskeyi [w]×[h] tam boyutlu resme uygular; kenar renklerinden arka plan rengi ayrıştırılır.
    static func apply(_ src: [UInt32], _ w: Int, _ h: Int, _ mask: BackgroundMask) -> [UInt32] {
        var out = [UInt32](repeating: 0, count: w * h)
        let sx = Float(mask.w) / Float(w), sy = Float(mask.h) / Float(h)
        let bgs = mask.background
        for y in 0..<h {
            let fy = min(max((Float(y) + 0.5) * sy - 0.5, 0), Float(mask.h - 1))
            let y0 = Int(fy), y1 = min(mask.h - 1, y0 + 1), ty = fy - Float(y0)
            for x in 0..<w {
                let fx = min(max((Float(x) + 0.5) * sx - 0.5, 0), Float(mask.w - 1))
                let x0 = Int(fx), x1 = min(mask.w - 1, x0 + 1), tx = fx - Float(x0)
                let a00 = mask.alpha[y0 * mask.w + x0], a10 = mask.alpha[y0 * mask.w + x1]
                let a01 = mask.alpha[y1 * mask.w + x0], a11 = mask.alpha[y1 * mask.w + x1]
                let m = (a00 * (1 - tx) + a10 * tx) * (1 - ty) + (a01 * (1 - tx) + a11 * tx) * ty
                let i = y * w + x
                let c = src[i]
                if m <= 0.004 { out[i] = 0; continue }
                let a = UInt32(min(max(roundK(Float(c >> 24) * m), 0), 255))
                if m >= 0.996 || bgs.isEmpty { out[i] = a << 24 | (c & 0xFFFFFF); continue }
                // Kenar pikseli: ön = (c − (1−m)·arka) / m
                let bgc = nearestRgb(bgs, c)
                func un(_ v: UInt32, _ b: UInt32) -> UInt32 { UInt32(min(max(roundK((Float(v) - (1 - m) * Float(b)) / m), 0), 255)) }
                out[i] = a << 24 | un(c >> 16 & 0xFF, bgc >> 16 & 0xFF) << 16 | un(c >> 8 & 0xFF, bgc >> 8 & 0xFF) << 8 | un(c & 0xFF, bgc & 0xFF)
            }
        }
        return out
    }

    /// Tek adımda: maske + uygulama.
    static func remove(_ src: [UInt32], _ w: Int, _ h: Int, _ opt: BackgroundOptions = BackgroundOptions(), cancelled: () -> Bool = { false }) -> [UInt32] {
        apply(src, w, h, mask(src, w, h, opt, cancelled: cancelled))
    }

    // MARK: Yardımcılar

    private static let DX = [1, -1, 0, 0]
    private static let DY = [0, 0, 1, -1]

    private static func nearestRgb(_ bgs: [UInt32], _ c: UInt32) -> UInt32 {
        var best = bgs[0], bd = Int.max
        for b in bgs {
            let dr = Int(b >> 16 & 0xFF) - Int(c >> 16 & 0xFF), dg = Int(b >> 8 & 0xFF) - Int(c >> 8 & 0xFF), db = Int(b & 0xFF) - Int(c & 0xFF)
            let d = dr * dr + dg * dg + db * db
            if d < bd { bd = d; best = b }
        }
        return best
    }

    @inline(__always) private static func dist(_ lab: [Float], _ i: Int, _ c: [Float]) -> Float {
        let dl = lab[i * 3] - c[0], da = lab[i * 3 + 1] - c[1], db = lab[i * 3 + 2] - c[2]
        return (dl * dl + da * da + db * db).squareRoot()
    }

    @inline(__always) private static func dist2(_ lab: [Float], _ i: Int, _ j: Int) -> Float {
        let dl = lab[i * 3] - lab[j * 3], da = lab[i * 3 + 1] - lab[j * 3 + 1], db = lab[i * 3 + 2] - lab[j * 3 + 2]
        return (dl * dl + da * da + db * db).squareRoot()
    }

    /// Kenar şeridi renklerinden arka plan renk merkezleri (Lab).
    static func borderCenters(_ lab: [Float], _ alpha: [Int], _ w: Int, _ h: Int) -> [[Float]] {
        let band = max(1, min(w, h) / 50)
        var samples: [Int] = []
        var weights: [Float] = []
        let stride = max(1, (2 * (w + h) * band) / 6000)
        var k = 0
        for y in 0..<h {
            for x in 0..<w {
                let edge = x < band || y < band || x >= w - band || y >= h - band
                if !edge { continue }
                let take = k % stride == 0
                k += 1
                if !take { continue }
                let i = y * w + x
                if alpha[i] < 8 { continue }
                samples.append(i)
                weights.append(y >= h - band && x >= band && x < w - band ? 0.5 : 1)
            }
        }
        if samples.isEmpty { return [] }
        // k-ortalamalar (k=3), en parlak, en koyu ve ortanca örnekten başlanır
        let kk = min(3, samples.count)
        let sorted = samples.enumerated().sorted { lab[$0.element * 3] != lab[$1.element * 3] ? lab[$0.element * 3] < lab[$1.element * 3] : $0.offset < $1.offset }.map(\.element)
        var centers: [[Float]] = (0..<kk).map { j in
            let s = sorted[(j * (sorted.count - 1)) / max(1, kk - 1)]
            return [lab[s * 3], lab[s * 3 + 1], lab[s * 3 + 2]]
        }
        var assign = [Int](repeating: 0, count: samples.count)
        for _ in 0..<8 {
            for (si, s) in samples.enumerated() {
                var best = 0, bd = Float.greatestFiniteMagnitude
                for c in 0..<kk { let d = dist(lab, s, centers[c]); if d < bd { bd = d; best = c } }
                assign[si] = best
            }
            for c in 0..<kk {
                var l: Float = 0, a: Float = 0, b: Float = 0, wsum: Float = 0
                for (si, s) in samples.enumerated() where assign[si] == c {
                    let wt = weights[si]; l += lab[s * 3] * wt; a += lab[s * 3 + 1] * wt; b += lab[s * 3 + 2] * wt; wsum += wt
                }
                if wsum > 0 { centers[c] = [l / wsum, a / wsum, b / wsum] }
            }
        }
        var share = [Float](repeating: 0, count: kk)
        var total: Float = 0
        for (si, c) in assign.enumerated() { share[c] += weights[si]; total += weights[si] }
        // Payı küçük kümeler arka plan sayılmaz; birbirine çok yakın merkezler birleşir
        let kept = (0..<kk).filter { share[$0] / total >= 0.18 }
            .enumerated().sorted { share[$0.element] != share[$1.element] ? share[$0.element] > share[$1.element] : $0.offset < $1.offset }
            .map { centers[$0.element] }
        var merged: [[Float]] = []
        for c in kept where !merged.contains(where: { m in
            let dl = m[0] - c[0], da = m[1] - c[1], db = m[2] - c[2]
            return (dl * dl + da * da + db * db).squareRoot() < 6
        }) { merged.append(c) }
        return merged
    }

    private static func removeHoles(_ lab: [Float], _ bg: inout [UInt8], _ w: Int, _ h: Int, _ centers: [[Float]], _ t: Float, _ minArea: Int, _ queue: inout [Int]) {
        let n = w * h
        var seen = [Bool](repeating: false, count: n)
        func near(_ i: Int) -> Bool { for c in centers where dist(lab, i, c) < t { return true }; return false }
        for s in 0..<n {
            if seen[s] || bg[s] != 0 || !near(s) { continue }
            var head = 0, tail = 0
            queue[tail] = s; tail += 1; seen[s] = true
            while head < tail {
                let p = queue[head]; head += 1
                let x = p % w, y = p / w
                for k in 0..<4 {
                    let nx = x + DX[k], ny = y + DY[k]
                    if nx < 0 || ny < 0 || nx >= w || ny >= h { continue }
                    let q = ny * w + nx
                    if seen[q] || bg[q] != 0 || !near(q) { continue }
                    seen[q] = true; queue[tail] = q; tail += 1
                }
            }
            if tail >= minArea { for j in 0..<tail { bg[queue[j]] = 1 } }
        }
    }

    private static func flood(_ lab: [Float], _ bg: inout [UInt8], _ w: Int, _ h: Int, _ u: Float, _ v: Float, _ target: UInt8,
                              _ tGlobal: Float, _ tLocal: Float, _ queue: inout [Int]) {
        let sx = min(max(Int(u * Float(w)), 0), w - 1), sy = min(max(Int(v * Float(h)), 0), h - 1)
        let s = sy * w + sx
        let ref = [lab[s * 3], lab[s * 3 + 1], lab[s * 3 + 2]]
        var seen = [Bool](repeating: false, count: w * h)
        var head = 0, tail = 0
        queue[tail] = s; tail += 1; seen[s] = true
        while head < tail {
            let p = queue[head]; head += 1
            bg[p] = target
            let x = p % w, y = p / w
            for k in 0..<4 {
                let nx = x + DX[k], ny = y + DY[k]
                if nx < 0 || ny < 0 || nx >= w || ny >= h { continue }
                let q = ny * w + nx
                if seen[q] { continue }
                if bg[q] == target { continue }
                let d = dist(lab, q, ref)
                if d < tGlobal && dist2(lab, p, q) < tLocal * 2 { seen[q] = true; queue[tail] = q; tail += 1 }
            }
        }
    }

    /// [value] değerli, [maxArea]'dan küçük bağlı bölgeleri ters değere çevirir.
    private static func cleanComponents(_ bg: inout [UInt8], _ w: Int, _ h: Int, _ value: UInt8, _ maxArea: Int, _ queue: inout [Int], borderSafe: Bool = false) {
        let n = w * h
        var seen = [Bool](repeating: false, count: n)
        for s in 0..<n {
            if seen[s] || bg[s] != value { continue }
            var head = 0, tail = 0
            var touchesBorder = false
            queue[tail] = s; tail += 1; seen[s] = true
            while head < tail {
                let p = queue[head]; head += 1
                let x = p % w, y = p / w
                if x == 0 || y == 0 || x == w - 1 || y == h - 1 { touchesBorder = true }
                for k in 0..<4 {
                    let nx = x + DX[k], ny = y + DY[k]
                    if nx < 0 || ny < 0 || nx >= w || ny >= h { continue }
                    let q = ny * w + nx
                    if seen[q] || bg[q] != value { continue }
                    seen[q] = true; queue[tail] = q; tail += 1
                }
            }
            if tail <= maxArea && !(borderSafe && touchesBorder) { for j in 0..<tail { bg[queue[j]] = 1 - value } }
        }
    }

    /// Maske kenarlarını [radius] piksel yumuşatır (iki geçişli kutu bulanıklığı).
    private static func softEdges(_ a: [Float], _ w: Int, _ h: Int, _ radius: Float) -> [Float] {
        let r = max(1, roundK(radius))
        var tmp = [Float](repeating: 0, count: a.count)
        var cur = a
        for _ in 0..<2 { boxH(cur, &tmp, w, h, r); boxV(tmp, &cur, w, h, r) }
        // 0.15..0.85 → 0..1
        for i in cur.indices { cur[i] = min(max((cur[i] - 0.15) / 0.7, 0), 1) }
        return cur
    }

    private static func boxH(_ src: [Float], _ dst: inout [Float], _ w: Int, _ h: Int, _ r: Int) {
        let div: Float = 2 * Float(r) + 1
        for y in 0..<h {
            let row = y * w
            var sum: Float = 0
            for i in -r...r { sum += src[row + min(max(i, 0), w - 1)] }
            for x in 0..<w {
                dst[row + x] = sum / div
                sum += src[row + min(w - 1, x + r + 1)] - src[row + max(0, x - r)]
            }
        }
    }

    private static func boxV(_ src: [Float], _ dst: inout [Float], _ w: Int, _ h: Int, _ r: Int) {
        let div: Float = 2 * Float(r) + 1
        for x in 0..<w {
            var sum: Float = 0
            for i in -r...r { sum += src[min(max(i, 0), h - 1) * w + x] }
            for y in 0..<h {
                dst[y * w + x] = sum / div
                sum += src[min(h - 1, y + r + 1) * w + x] - src[max(0, y - r) * w + x]
            }
        }
    }

    /// Alan ortalamalı küçültme (ARGB, saydamlık ağırlıklı).
    static func resample(_ src: [UInt32], _ w: Int, _ h: Int, _ nw: Int, _ nh: Int) -> [UInt32] {
        var out = [UInt32](repeating: 0, count: nw * nh)
        for y in 0..<nh {
            let y0 = y * h / nh, y1 = max(y0 + 1, (y + 1) * h / nh)
            for x in 0..<nw {
                let x0 = x * w / nw, x1 = max(x0 + 1, (x + 1) * w / nw)
                var a = 0, r = 0, g = 0, b = 0, k = 0
                for yy in y0..<y1 {
                    for xx in x0..<x1 {
                        let c = src[yy * w + xx], ca = Int(c >> 24)
                        a += ca; r += Int(c >> 16 & 0xFF) * ca; g += Int(c >> 8 & 0xFF) * ca; b += Int(c & 0xFF) * ca; k += 1
                    }
                }
                out[y * nw + x] = a == 0 ? 0 : UInt32(a / k) << 24 | UInt32(r / a) << 16 | UInt32(g / a) << 8 | UInt32(b / a)
            }
        }
        return out
    }

    // MARK: sRGB ↔ Lab (D65)

    private static let LIN: [Float] = (0..<256).map { i in
        let c = Float(i) / 255
        return c <= 0.04045 ? c / 12.92 : Float(pow(Double((c + 0.055) / 1.055), 2.4))
    }

    @inline(__always) private static func f(_ t: Float) -> Float { t > 0.008856 ? Float(cbrt(Double(t))) : 7.787 * t + 16 / 116 }

    static func toLab(_ c: UInt32, _ out: inout [Float], _ at: Int) {
        let r = LIN[Int(c >> 16 & 0xFF)], g = LIN[Int(c >> 8 & 0xFF)], b = LIN[Int(c & 0xFF)]
        let x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        let y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        let z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        let fx = f(x), fy = f(y), fz = f(z)
        out[at] = 116 * fy - 16; out[at + 1] = 500 * (fx - fy); out[at + 2] = 200 * (fy - fz)
    }

    private static func labToRgb(_ lab: [Float]) -> UInt32 {
        let fy = (lab[0] + 16) / 116, fx = fy + lab[1] / 500, fz = fy - lab[2] / 200
        func inv(_ t: Float) -> Float { t * t * t > 0.008856 ? t * t * t : (t - 16 / 116) / 7.787 }
        let x = inv(fx) * 0.95047, y = inv(fy), z = inv(fz) * 1.08883
        let r: Float = 3.2406 * x - 1.5372 * y - 0.4986 * z
        let g: Float = -0.9689 * x + 1.8758 * y + 0.0415 * z
        let b: Float = 0.0557 * x - 0.2040 * y + 1.0570 * z
        func gam(_ v: Float) -> UInt32 {
            let c: Float = v <= 0.0031308 ? 12.92 * v : 1.055 * Float(pow(Double(v), 1 / 2.4)) - 0.055
            return UInt32(min(max(roundK(c * 255), 0), 255))
        }
        return 0xFF << 24 | gam(r) << 16 | gam(g) << 8 | gam(b)
    }
}
