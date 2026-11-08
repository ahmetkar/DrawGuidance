import Foundation

/// Çizgi bulma türü: AUTO görüntüye göre seçer; EDGES renk/ışık değişimlerinin kenarı; CENTERLINE koyu çizgilerin ortası.
enum EdgeMode: String, KotlinEnum {
    case AUTO, EDGES, CENTERLINE
    static var fallback: EdgeMode { .AUTO }
}

/// Kenar vektörleştirme ayarları (0..1; Android `EdgeOptions`).
struct EdgeOptions: Equatable {
    var detail: Float = 0.5
    var smooth: Float = 0.5
    var minLength: Float = 0.4
    var colored: Bool = false
    var mode: EdgeMode = .AUTO
    var maxSide: Int = 640
}

/// Bulunan çizgi: noktalar birim kutuda (0..1), nodes vektör noktalarının sıraları, color "#RRGGBB".
struct EdgePath: Equatable {
    let points: [P]
    let closed: Bool
    let nodes: [Int]
    let color: String
}

/// Sonuç; mode kullanılan tür, width×height işlenen çözünürlük.
struct EdgeResult {
    let paths: [EdgePath]
    let mode: EdgeMode
    let width: Int
    let height: Int
}

/// Bitmap → düzenlenebilir çizgiler (Android `EdgeTracer` ile birebir): Canny kenarları ya da Otsu + incelme ile orta
/// çizgi; zincirleme, birleştirme, yumuşatma, RDP vektör noktaları. Arka planda çağrılmalıdır.
enum EdgeTracer {
    static let MAX_PATHS = 2500
    static let MAX_POINTS = 80_000
    private static let DARK = "#1E1E1E"

    private struct Pt: Equatable {
        let x: Float
        let y: Float
        init(_ x: Float, _ y: Float) { self.x = x; self.y = y }
        func dist(_ o: Pt) -> Float { kHypot(x - o.x, y - o.y) }
    }

    private struct Img {
        let w: Int, h: Int
        let r: [Float], g: [Float], b: [Float]
    }

    @inline(__always) private static func roundK(_ v: Float) -> Int { Int((v + 0.5).rounded(.down)) }

    static func trace(_ argb: [UInt32], _ width: Int, _ height: Int, options: EdgeOptions = EdgeOptions(),
                      cancel: () -> Bool = { false }) throws -> EdgeResult {
        precondition(width > 0 && height > 0 && argb.count >= width * height)
        let img = downscale(argb, width, height, min(max(options.maxSide, 64), 2048))
        try checkCancel(cancel)
        let w = img.w, h = img.h
        let gray: [Float] = (0..<(w * h)).map { (i: Int) -> Float in
            let r: Float = 0.299 * img.r[i], g: Float = 0.587 * img.g[i], b: Float = 0.114 * img.b[i]
            return r + g + b
        }
        let detail = min(max(options.detail, 0), 1)
        let mode: EdgeMode = options.mode == .AUTO ? (looksLikeLineArt(gray, w, h) ? .CENTERLINE : .EDGES) : options.mode
        var mask = mode == .CENTERLINE ? centerline(gray, w, h, detail) : try canny(gray, w, h, detail, cancel)
        try checkCancel(cancel)
        thin(&mask, w, h)
        try checkCancel(cancel)
        var chains = Self.chains(mask, w, h)
        chains = join(chains, 2.3)
        let minPx = (3 + 40 * min(max(options.minLength, 0), 1)) * max(Float(max(w, h)) / 640, 0.4)
        let lens = chains.map(length)
        chains = chains.indices.filter { lens[$0] >= minPx || (isClosed(chains[$0]) && lens[$0] >= minPx * 0.5) }
            .sorted { lens[$0] != lens[$1] ? lens[$0] > lens[$1] : $0 < $1 }
            .prefix(MAX_PATHS).map { chains[$0] }
        try checkCancel(cancel)
        let smooth = min(max(options.smooth, 0), 1)
        let eps: Float = 0.5 + 1.6 * (1 - detail) + 0.8 * smooth
        var out: [EdgePath] = []
        var total = 0
        for c in chains {
            let closed = isClosed(c) && c.count >= 8
            let pts = closed && c[0].dist(c[c.count - 1]) < 0.01 ? Array(c.dropLast()) : c
            let sm = smoothed(pts, closed, 1 + roundK(smooth * 4))
            let keys = rdp(sm, closed, eps)
            let (curve, nodes) = spline(sm, keys, closed)
            if curve.count < 2 { continue }
            if total + curve.count > MAX_POINTS { break }
            total += curve.count
            let color = options.colored ? chainColor(img, pts) : DARK
            out.append(EdgePath(points: curve.map { P($0.x / Float(w), $0.y / Float(h)) }, closed: closed, nodes: nodes, color: color))
        }
        return EdgeResult(paths: out, mode: mode, width: w, height: h)
    }

    // MARK: Görüntü

    /// Alan ortalamalı küçültme; saydam pikseller beyaz zemine bindirilir.
    private static func downscale(_ argb: [UInt32], _ width: Int, _ height: Int, _ maxSide: Int) -> Img {
        let k = min(1, Float(maxSide) / Float(max(width, height)))
        let w = max(1, roundK(Float(width) * k)), h = max(1, roundK(Float(height) * k))
        var r = [Float](repeating: 0, count: w * h), g = r, b = r, n = r
        for y in 0..<height {
            let ty = min(h - 1, Int(Float(y) * k))
            for x in 0..<width {
                let tx = min(w - 1, Int(Float(x) * k))
                let c = argb[y * width + x]
                let a = Float(c >> 24 & 0xFF) / 255
                let i = ty * w + tx
                let bgw: Float = 255 * (1 - a)
                r[i] += Float(c >> 16 & 0xFF) * a + bgw
                g[i] += Float(c >> 8 & 0xFF) * a + bgw
                b[i] += Float(c & 0xFF) * a + bgw
                n[i] += 1
            }
        }
        for i in 0..<(w * h) {
            if n[i] > 0 { r[i] /= n[i]; g[i] /= n[i]; b[i] /= n[i] } else { r[i] = 255; g[i] = 255; b[i] = 255 }
        }
        return Img(w: w, h: h, r: r, g: g, b: b)
    }

    /// Açık zemin üstünde ince koyu çizgiler mi (kalem çizimi).
    private static func looksLikeLineArt(_ gray: [Float], _ w: Int, _ h: Int) -> Bool {
        let t = otsu(gray)
        var dark = 0, light = 0
        for v in gray { if v < t { dark += 1 } else if v > 200 { light += 1 } }
        let n = gray.count
        if dark == 0 || Float(dark) > Float(n) * 0.28 || Float(light) < Float(n) * 0.5 { return false }
        var border = 0
        for y in 0..<h {
            for x in 0..<w {
                let i = y * w + x
                if gray[i] >= t { continue }
                if x == 0 || y == 0 || x == w - 1 || y == h - 1 || gray[i - 1] >= t || gray[i + 1] >= t || gray[i - w] >= t || gray[i + w] >= t { border += 1 }
            }
        }
        let thickness = 2 * Float(dark) / Float(max(1, border))
        return thickness <= 7
    }

    private static func otsu(_ gray: [Float]) -> Float {
        var hist = [Int](repeating: 0, count: 256)
        for v in gray { hist[min(max(roundK(v), 0), 255)] += 1 }
        let total = Double(gray.count)
        var sum = 0.0
        for i in 0..<256 { sum += Double(i) * Double(hist[i]) }
        var sumB = 0.0, wB = 0.0, best = 0.0
        var t = 128
        for i in 0..<256 {
            wB += Double(hist[i])
            if wB == 0 { continue }
            let wF = total - wB
            if wF == 0 { break }
            sumB += Double(i) * Double(hist[i])
            let mB = sumB / wB
            let mF = (sum - sumB) / wF
            let between = wB * wF * (mB - mF) * (mB - mF)
            if between > best { best = between; t = i }
        }
        return Float(t) + 0.5
    }

    private static func centerline(_ gray: [Float], _ w: Int, _ h: Int, _ detail: Float) -> [Bool] {
        let blurred = blur(gray, w, h, 0.6 + 0.6 * (1 - detail))
        let t = otsu(blurred)
        return blurred.map { $0 < t }
    }

    private static func blur(_ src: [Float], _ w: Int, _ h: Int, _ sigma: Float) -> [Float] {
        let rad = max(1, Int((sigma * 2.5).rounded(.up)))
        var k: [Float] = (0..<(rad * 2 + 1)).map { i in
            let d = Float(i - rad)
            return Float(exp(Double(-(d * d) / (2 * sigma * sigma))))
        }
        var ks: Float = 0
        for v in k { ks += v }
        for i in k.indices { k[i] /= ks }
        var tmp = [Float](repeating: 0, count: w * h)
        var out = tmp
        for y in 0..<h {
            for x in 0..<w {
                var s: Float = 0
                for j in -rad...rad { s += src[y * w + min(max(x + j, 0), w - 1)] * k[j + rad] }
                tmp[y * w + x] = s
            }
        }
        for y in 0..<h {
            for x in 0..<w {
                var s: Float = 0
                for j in -rad...rad { s += tmp[min(max(y + j, 0), h - 1) * w + x] * k[j + rad] }
                out[y * w + x] = s
            }
        }
        return out
    }

    /// Canny: yumuşatma, Sobel, en büyük olmayanı bastırma ve uyarlanan çift eşikle histerezis.
    private static func canny(_ gray: [Float], _ w: Int, _ h: Int, _ detail: Float, _ cancel: () -> Bool) throws -> [Bool] {
        let g = blur(gray, w, h, 1.9 - 0.9 * detail)
        try checkCancel(cancel)
        var mag = [Float](repeating: 0, count: w * h)
        var dir = [UInt8](repeating: 0, count: w * h)
        if h > 2 && w > 2 {
            for y in 1..<(h - 1) {
                for x in 1..<(w - 1) {
                    let i = y * w + x
                    let gx1: Float = g[i - w + 1] + 2 * g[i + 1] + g[i + w + 1]
                    let gx2: Float = g[i - w - 1] + 2 * g[i - 1] + g[i + w - 1]
                    let gy1: Float = g[i + w - 1] + 2 * g[i + w] + g[i + w + 1]
                    let gy2: Float = g[i - w - 1] + 2 * g[i - w] + g[i - w + 1]
                    let gx = gx1 - gx2, gy = gy1 - gy2
                    mag[i] = kHypot(gx, gy)
                    let ax = abs(gx), ay = abs(gy)
                    dir[i] = ay <= ax * 0.4142 ? 0 : ay >= ax * 2.4142 ? 2 : (gx > 0) == (gy > 0) ? 1 : 3
                }
            }
        }
        var nms = [Float](repeating: 0, count: w * h)
        if h > 2 && w > 2 {
            for y in 1..<(h - 1) {
                for x in 1..<(w - 1) {
                    let i = y * w + x
                    let m = mag[i]
                    if m <= 0 { continue }
                    let a: Float, b: Float
                    switch dir[i] {
                    case 0: a = mag[i - 1]; b = mag[i + 1]
                    case 2: a = mag[i - w]; b = mag[i + w]
                    case 1: a = mag[i - w - 1]; b = mag[i + w + 1]
                    default: a = mag[i - w + 1]; b = mag[i + w - 1]
                    }
                    if m >= a && m >= b { nms[i] = m }
                }
            }
        }
        try checkCancel(cancel)
        let values = nms.filter { $0 > 4 }.sorted()
        if values.isEmpty { return [Bool](repeating: false, count: w * h) }
        let pct = min(max(0.86 - 0.36 * detail, 0.3), 0.95)
        let high = max(values[Int(Float(values.count - 1) * pct)], 28 + 40 * (1 - detail))
        let low = high * 0.42
        var mask = [Bool](repeating: false, count: w * h)
        var stack = [Int](repeating: 0, count: w * h)
        var sp = 0
        for i in 0..<(w * h) where nms[i] >= high { mask[i] = true; stack[sp] = i; sp += 1 }
        while sp > 0 {
            sp -= 1
            let i = stack[sp]
            let x = i % w, y = i / w
            for dy in -1...1 {
                for dx in -1...1 {
                    if dx == 0 && dy == 0 { continue }
                    let nx = x + dx, ny = y + dy
                    if nx < 0 || ny < 0 || nx >= w || ny >= h { continue }
                    let j = ny * w + nx
                    if !mask[j] && nms[j] >= low { mask[j] = true; stack[sp] = j; sp += 1 }
                }
            }
        }
        return mask
    }

    /// Zhang–Suen incelme: çizgiler tek piksel kalınlığa iner.
    private static func thin(_ m: inout [Bool], _ w: Int, _ h: Int) {
        var remove: [Int] = []
        var changed = true
        var guardN = 0
        while changed && guardN < 60 {
            guardN += 1
            changed = false
            for step in 0...1 {
                remove.removeAll(keepingCapacity: true)
                for y in 0..<h {
                    for x in 0..<w {
                        if !m[y * w + x] { continue }
                        func at(_ x: Int, _ y: Int) -> Bool { x >= 0 && x < w && y >= 0 && y < h && m[y * w + x] }
                        let p2 = at(x, y - 1), p3 = at(x + 1, y - 1), p4 = at(x + 1, y), p5 = at(x + 1, y + 1)
                        let p6 = at(x, y + 1), p7 = at(x - 1, y + 1), p8 = at(x - 1, y), p9 = at(x - 1, y - 1)
                        let nb = [p2, p3, p4, p5, p6, p7, p8, p9]
                        var b = 0
                        for v in nb where v { b += 1 }
                        if b < 2 || b > 6 { continue }
                        var a = 0
                        for k in 0..<8 where !nb[k] && nb[(k + 1) % 8] { a += 1 }
                        if a != 1 { continue }
                        if step == 0 {
                            if p2 && p4 && p6 { continue }
                            if p4 && p6 && p8 { continue }
                        } else {
                            if p2 && p4 && p8 { continue }
                            if p2 && p6 && p8 { continue }
                        }
                        remove.append(y * w + x)
                    }
                }
                if !remove.isEmpty { changed = true; for i in remove { m[i] = false } }
            }
        }
    }

    // MARK: Zincirler

    private static func chains(_ m: [Bool], _ w: Int, _ h: Int) -> [[Pt]] {
        var visited = [Bool](repeating: false, count: w * h)
        func on(_ x: Int, _ y: Int) -> Bool { x >= 0 && x < w && y >= 0 && y < h && m[y * w + x] }
        func degree(_ x: Int, _ y: Int) -> Int {
            var d = 0
            for dy in -1...1 { for dx in -1...1 where (dx != 0 || dy != 0) && on(x + dx, y + dy) { d += 1 } }
            return d
        }
        let onPx = (0..<(w * h)).filter { m[$0] }
        let ends = onPx.filter { degree($0 % w, $0 / w) == 1 }
        let endSet = Set(ends)
        let order = ends + onPx.filter { !endSet.contains($0) }
        var out: [[Pt]] = []
        let nbr = [0, -1, 1, 0, 0, 1, -1, 0, 1, -1, 1, 1, -1, 1, -1, -1]
        for start in order {
            if visited[start] { continue }
            var chain: [Pt] = []
            var cx = start % w, cy = start / w
            visited[start] = true
            chain.append(Pt(Float(cx) + 0.5, Float(cy) + 0.5))
            while true {
                let n = chain.count
                let back = chain[max(0, n - 4)]
                var dirX = chain[n - 1].x - back.x, dirY = chain[n - 1].y - back.y
                let dl = kHypot(dirX, dirY)
                let hasDir = dl > 0.9
                if hasDir { dirX /= dl; dirY /= dl }
                func turn(_ dx: Int, _ dy: Int) -> Float {
                    if !hasDir { return 0 }
                    let l = kHypot(Float(dx), Float(dy))
                    return 1 - (Float(dx) * dirX + Float(dy) * dirY) / l
                }
                var bestX = -1, bestY = -1, via = -1
                var bestScore = Float.greatestFiniteMagnitude
                var k = 0
                while k < nbr.count {
                    let dx = nbr[k], dy = nbr[k + 1]
                    let nx = cx + dx, ny = cy + dy
                    k += 2
                    if !on(nx, ny) { continue }
                    let j = ny * w + nx
                    if !visited[j] {
                        let score = turn(dx, dy) + (k > 8 ? 0.02 : 0)
                        if score < bestScore { bestScore = score; bestX = nx; bestY = ny; via = -1 }
                    } else if hasDir && degree(nx, ny) >= 3 {
                        var q = 0
                        while q < nbr.count {
                            let ex = nx + nbr[q], ey = ny + nbr[q + 1]
                            q += 2
                            if !on(ex, ey) || visited[ey * w + ex] { continue }
                            if abs(ex - cx) <= 1 && abs(ey - cy) <= 1 { continue }
                            let score = turn(ex - cx, ey - cy) + 0.15
                            if score < bestScore && score < 0.45 { bestScore = score; bestX = ex; bestY = ey; via = j }
                        }
                    }
                }
                if bestX < 0 || (hasDir && bestScore > 1.2) { break }
                if via >= 0 { chain.append(Pt(Float(via % w) + 0.5, Float(via / w) + 0.5)) }
                cx = bestX; cy = bestY
                visited[cy * w + cx] = true
                chain.append(Pt(Float(cx) + 0.5, Float(cy) + 0.5))
            }
            if chain.count > 3 && abs(chain[chain.count - 1].x - chain[0].x) <= 1.01 && abs(chain[chain.count - 1].y - chain[0].y) <= 1.01 {
                chain.append(chain[0])
            }
            if chain.count >= 2 { out.append(chain) }
        }
        // Daha uzun bir zincirin hemen bitişiğindeki kısa artık zincirler atılır
        var owned = [Bool](repeating: false, count: w * h)
        var kept: [[Pt]] = []
        let bySize = out.indices.sorted { out[$0].count != out[$1].count ? out[$0].count > out[$1].count : $0 < $1 }.map { out[$0] }
        for c in bySize {
            if c.count < 30 {
                var near = 0
                for p in c {
                    let x = Int(p.x), y = Int(p.y)
                    var hit = false
                    for dy in -1...1 {
                        for dx in -1...1 {
                            let xx = x + dx, yy = y + dy
                            if xx >= 0 && xx < w && yy >= 0 && yy < h && owned[yy * w + xx] { hit = true }
                        }
                    }
                    if hit { near += 1 }
                }
                if Float(near) >= Float(c.count) * 0.7 { continue }
            }
            kept.append(c)
            for p in c { owned[Int(p.y) * w + Int(p.x)] = true }
        }
        return kept
    }

    private static func length(_ c: [Pt]) -> Float {
        var s = 0.0
        for i in stride(from: 1, to: c.count, by: 1) { s += Double(c[i].dist(c[i - 1])) }
        return Float(s)
    }

    private static func isClosed(_ c: [Pt]) -> Bool { c.count > 3 && c[0].dist(c[c.count - 1]) < 1.6 }

    /// Uçları gap pikselden yakın zincirler birleştirilir.
    private static func join(_ src: [[Pt]], _ gap: Float) -> [[Pt]] {
        var list = src
        var merged = true
        var guardN = 0
        while merged && guardN < 6 {
            guardN += 1
            merged = false
            var i = 0
            while i < list.count {
                let a = list[i]
                if isClosed(a) { i += 1; continue }
                var bestJ = -1, mode = 0
                var bestD = gap
                for j in list.indices {
                    if j == i { continue }
                    let b = list[j]
                    if isClosed(b) { continue }
                    let d1 = a[a.count - 1].dist(b[0]), d2 = a[a.count - 1].dist(b[b.count - 1])
                    let d3 = a[0].dist(b[b.count - 1]), d4 = a[0].dist(b[0])
                    if d1 < bestD { bestD = d1; bestJ = j; mode = 1 }
                    if d2 < bestD { bestD = d2; bestJ = j; mode = 2 }
                    if d3 < bestD { bestD = d3; bestJ = j; mode = 3 }
                    if d4 < bestD { bestD = d4; bestJ = j; mode = 4 }
                }
                if bestJ < 0 { i += 1; continue }
                let b = list[bestJ]
                let joined: [Pt]
                switch mode {
                case 1: joined = a + b
                case 2: joined = a + b.reversed()
                case 3: joined = b + a
                default: joined = b.reversed() + a
                }
                let hi = max(i, bestJ), lo = min(i, bestJ)
                list.remove(at: hi); list.remove(at: lo)
                list.insert(joined, at: lo)
                merged = true
                i = lo
            }
            for ci in list.indices {
                let c = list[ci]
                if !isClosed(c) && c.count > 8 && c[0].dist(c[c.count - 1]) < gap { list[ci].append(c[0]) }
            }
        }
        return list
    }

    private static func smoothed(_ pts: [Pt], _ closed: Bool, _ window: Int) -> [Pt] {
        if pts.count < 5 || window <= 1 { return pts }
        let n = pts.count
        var out: [Pt] = []
        out.reserveCapacity(n)
        for i in 0..<n {
            if !closed && (i == 0 || i == n - 1) { out.append(pts[i]); continue }
            var sx: Float = 0, sy: Float = 0
            var c = 0
            for k in -window...window {
                let j = closed ? ((i + k) % n + n) % n : min(max(i + k, 0), n - 1)
                sx += pts[j].x; sy += pts[j].y; c += 1
            }
            out.append(Pt(sx / Float(c), sy / Float(c)))
        }
        return out
    }

    /// Vektör noktaları (sıralar): Ramer–Douglas–Peucker.
    private static func rdp(_ pts: [Pt], _ closed: Bool, _ eps: Float) -> [Int] {
        let n = pts.count
        if n <= 2 { return Array(pts.indices) }
        func simplify(_ src: [Pt], _ a: Int, _ b: Int, _ keep: inout [Bool]) {
            var stack: [(Int, Int)] = [(a, b)]
            while let (s, e) = stack.popLast() {
                if e <= s + 1 { continue }
                var best = -1
                var bestD: Float = 0
                for i in (s + 1)..<e {
                    let d = segDist(src[i], src[s], src[e])
                    if d > bestD { bestD = d; best = i }
                }
                if best >= 0 && bestD > eps { keep[best] = true; stack.append((s, best)); stack.append((best, e)) }
            }
        }
        var keep = [Bool](repeating: false, count: n)
        keep[0] = true
        if closed {
            var far = 1
            var fd = pts[1].dist(pts[0])
            for i in 2..<n { let d = pts[i].dist(pts[0]); if d > fd { fd = d; far = i } }
            keep[far] = true
            simplify(pts, 0, far, &keep)
            let ext = pts + [pts[0]]
            var keepExt = [Bool](repeating: false, count: n + 1)
            simplify(ext, far, n, &keepExt)
            if far + 1 < n { for i in (far + 1)..<n where keepExt[i] { keep[i] = true } }
        } else {
            keep[n - 1] = true
            simplify(pts, 0, n - 1, &keep)
        }
        return keep.indices.filter { keep[$0] }
    }

    private static func segDist(_ p: Pt, _ a: Pt, _ b: Pt) -> Float {
        let vx = b.x - a.x, vy = b.y - a.y
        let len2 = vx * vx + vy * vy
        if len2 < 1e-6 { return p.dist(a) }
        let t = min(max(((p.x - a.x) * vx + (p.y - a.y) * vy) / len2, 0), 1)
        return kHypot(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
    }

    /// Vektör noktaları arasında kaynaktan seyrek ara noktalar; dönüş: noktalar ve vektör noktalarının sıraları.
    private static func spline(_ src: [Pt], _ keys: [Int], _ closed: Bool) -> ([Pt], [Int]) {
        let n = keys.count
        if n < 2 { return (keys.map { src[$0] }, Array(keys.indices)) }
        var out: [Pt] = []
        var nodes: [Int] = []
        let m = src.count
        let segs = closed ? n : n - 1
        for s in 0..<segs {
            let a = keys[s]
            let b = s + 1 < n ? keys[s + 1] : keys[0] + m
            nodes.append(out.count)
            out.append(src[a % m])
            let inner = b - a - 1
            if inner <= 0 { continue }
            let len = src[a % m].dist(src[b % m])
            let steps = min(inner, min(10, Int(len / 5)))
            if steps >= 1 {
                for t in 1...steps {
                    let idx = a + roundK(Float((b - a) * t) / (Float(steps) + 1))
                    if idx >= a + 1 && idx < b { out.append(src[idx % m]) }
                }
            }
        }
        if !closed { nodes.append(out.count); out.append(src[keys[keys.count - 1]]) }
        return (out, nodes)
    }

    private static func chainColor(_ img: Img, _ pts: [Pt]) -> String {
        var r: Float = 0, g: Float = 0, b: Float = 0
        var c = 0
        let step = max(1, pts.count / 64)
        var i = 0
        while i < pts.count {
            let p = pts[i]
            let x = min(max(Int(p.x), 0), img.w - 1), y = min(max(Int(p.y), 0), img.h - 1)
            var best = -1
            var bestL = Float.greatestFiniteMagnitude
            for dy in -1...1 {
                for dx in -1...1 {
                    let nx = min(max(x + dx, 0), img.w - 1), ny = min(max(y + dy, 0), img.h - 1)
                    let j = ny * img.w + nx
                    let l: Float = 0.299 * img.r[j] + 0.587 * img.g[j] + 0.114 * img.b[j]
                    if l < bestL { bestL = l; best = j }
                }
            }
            r += img.r[best]; g += img.g[best]; b += img.b[best]; c += 1
            i += step
        }
        if c == 0 { return DARK }
        let k: Float = 0.8
        func ch(_ v: Float) -> Int { min(max(Int(v / Float(c) * k), 0), 255) }
        return String(format: "#%02X%02X%02X", ch(r), ch(g), ch(b))
    }
}
