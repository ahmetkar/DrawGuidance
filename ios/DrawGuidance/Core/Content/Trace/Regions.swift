import Foundation

/// Son bölge haritası: piksel başına bölge kimliği ve bölgenin palet sırası (-1: saydam arka plan, çizilmez).
struct RegionMap {
    let w: Int
    let h: Int
    let rid: [Int]
    let count: Int
    let label: [Int]
}

/// Etiket görüntüsünden 4-bağlı bileşenler; benekler ve kenar yumuşatma kalıntısı ince şeritler komşularına katılır
/// (Android `Regions` ile birebir).
enum Regions {
    private static let TRANSPARENT_DIST: Float = 200

    static func build(_ labels: [Int], _ w: Int, _ h: Int, _ palette: [Int], _ minArea: Double, _ thinRatio: Float, _ maxParts: Int,
                      _ cancel: () -> Bool) throws -> RegionMap {
        let n = w * h
        // 4-bağlı bileşenler
        var comp = [Int](repeating: -1, count: n)
        var stack = [Int](repeating: 0, count: n)
        var compLabel: [Int] = []
        var compArea: [Int] = []
        var nc = 0
        for s in 0..<n {
            if comp[s] >= 0 { continue }
            let lab = labels[s]
            comp[s] = nc
            var sp = 0
            stack[sp] = s; sp += 1
            var area = 0
            while sp > 0 {
                sp -= 1
                let p = stack[sp]
                area += 1
                let x = p % w
                if x > 0 && comp[p - 1] < 0 && labels[p - 1] == lab { comp[p - 1] = nc; stack[sp] = p - 1; sp += 1 }
                if x < w - 1 && comp[p + 1] < 0 && labels[p + 1] == lab { comp[p + 1] = nc; stack[sp] = p + 1; sp += 1 }
                if p >= w && comp[p - w] < 0 && labels[p - w] == lab { comp[p - w] = nc; stack[sp] = p - w; sp += 1 }
                if p + w < n && comp[p + w] < 0 && labels[p + w] == lab { comp[p + w] = nc; stack[sp] = p + w; sp += 1 }
            }
            compLabel.append(lab); compArea.append(area)
            nc += 1
        }
        try checkCancel(cancel)

        // Komşuluk: ortak kenar uzunlukları, çevre
        var perim = [Int](repeating: 0, count: nc)
        var keys: [Int] = []
        keys.reserveCapacity(1024)
        for y in 0..<h {
            let row = y * w
            for x in 0..<w {
                let p = row + x
                let c = comp[p]
                if x == 0 { perim[c] += 1 }
                if x == w - 1 { perim[c] += 1 }
                if y == 0 { perim[c] += 1 }
                if y == h - 1 { perim[c] += 1 }
                if x + 1 < w {
                    let c2 = comp[p + 1]
                    if c2 != c { keys.append(min(c, c2) << 32 | max(c, c2)); perim[c] += 1; perim[c2] += 1 }
                }
                if y + 1 < h {
                    let c2 = comp[p + w]
                    if c2 != c { keys.append(min(c, c2) << 32 | max(c, c2)); perim[c] += 1; perim[c2] += 1 }
                }
            }
        }
        keys.sort()
        var adj = [[Int]?](repeating: nil, count: nc)
        do {
            var i = 0
            while i < keys.count {
                let key = keys[i]
                var j = i
                while j < keys.count && keys[j] == key { j += 1 }
                let a = key >> 32, b = key & 0xFFFF_FFFF, len = j - i
                if adj[a] == nil { adj[a] = [] }; adj[a]!.append(contentsOf: [b, len])
                if adj[b] == nil { adj[b] = [] }; adj[b]!.append(contentsOf: [a, len])
                i = j
            }
        }
        try checkCancel(cancel)

        // Birleştirme
        var parent = Array(0..<nc)
        var area = compArea
        func find(_ c: Int) -> Int {
            var r = c
            while parent[r] != r { r = parent[r] }
            var x = c
            while parent[x] != r { let nx = parent[x]; parent[x] = r; x = nx }
            return r
        }
        var acc = [Int](repeating: 0, count: nc)
        var touched: [Int] = []
        func gather(_ r: Int) {
            touched.removeAll(keepingCapacity: true)
            guard let list = adj[r] else { return }
            var i = 0
            while i < list.count {
                let nb = find(list[i]), len = list[i + 1]
                i += 2
                if nb == r { continue }
                if acc[nb] == 0 { touched.append(nb) }
                acc[nb] += len
            }
        }
        func clearGather() { for t in touched { acc[t] = 0 } }
        func colorDist(_ a: Int, _ b: Int) -> Float {
            let la = compLabel[a], lb = compLabel[b]
            if la < 0 || lb < 0 { return la == lb ? 0 : TRANSPARENT_DIST }
            return traceRgbDist(palette[la], palette[lb])
        }
        func mergeInto(_ r: Int, _ t: Int, _ shared: Int) {
            parent[r] = t
            area[t] += area[r]
            perim[t] = perim[t] + perim[r] - 2 * shared
            if let from = adj[r] {
                if adj[t] == nil { adj[t] = [] }; adj[t]!.append(contentsOf: from)
                adj[r] = nil
            }
        }
        func bestTarget(_ r: Int) -> Int {
            var best = -1
            var bestScore: Float = -1
            for nb in touched {
                let score = Float(acc[nb]) / (colorDist(r, nb) + 12)
                if score > bestScore || (score == bestScore && nb < best) { bestScore = score; best = nb }
            }
            return best
        }
        func ch(_ c: Int, _ s: Int) -> Float { Float((c >> s) & 255) }
        func isBlend(_ r: Int) -> Bool {
            let lr = compLabel[r]
            if lr < 0 { return false }
            let c = palette[lr]
            let cr = ch(c, 16), cg = ch(c, 8), cb = ch(c, 0)
            let cand = Array(touched.filter { compLabel[$0] >= 0 }
                .sorted { acc[$0] != acc[$1] ? acc[$0] > acc[$1] : $0 < $1 }.prefix(6))
            let nCand = cand.count
            for i in 0..<nCand {
                let a = palette[compLabel[cand[i]]]
                if traceRgbDist(a, c) < 40 { return true }
                let ar = ch(a, 16), ag = ch(a, 8), ab = ch(a, 0)
                for j in (i + 1)..<max(nCand, i + 1) {
                    let b = palette[compLabel[cand[j]]]
                    let dr = ch(b, 16) - ar, dg = ch(b, 8) - ag, db = ch(b, 0) - ab
                    let len2 = dr * dr + dg * dg + db * db
                    if len2 < 1 { continue }
                    let num: Float = (cr - ar) * dr + (cg - ag) * dg + (cb - ab) * db
                    let t = num / len2
                    if t < -0.15 || t > 1.15 { continue }
                    let tc = min(max(t, 0), 1)
                    let ex = cr - (ar + tc * dr), ey = cg - (ag + tc * dg), ez = cb - (ab + tc * db)
                    if ex * ex + ey * ey + ez * ez < 36 * 36 { return true }
                }
            }
            return false
        }

        let order = (0..<nc).sorted { area[$0] != area[$1] ? area[$0] < area[$1] : $0 < $1 }
        for (step, r) in order.enumerated() {
            if step & 1023 == 0 { try checkCancel(cancel) }
            if parent[r] != r { continue }
            gather(r)
            if touched.isEmpty { clearGather(); continue }
            let small = Double(area[r]) < minArea
            let thin = !small && Float(area[r]) < thinRatio * Float(perim[r]) && isBlend(r)
            if small || thin {
                let t = bestTarget(r)
                let shared = acc[t]
                clearGather()
                mergeInto(r, t, shared)
            } else { clearGather() }
        }

        // Parça sınırı: çizilen bölge sayısı fazlaysa en küçükleri katmaya devam et
        while true {
            let roots = (0..<nc).filter { parent[$0] == $0 }
            let opaque = roots.filter { compLabel[$0] >= 0 }
            if opaque.count <= maxParts { break }
            try checkCancel(cancel)
            let byArea: (Int, Int) -> Bool = { area[$0] != area[$1] ? area[$0] < area[$1] : $0 < $1 }
            let sorted = roots.sorted(by: byArea)
            let excess = opaque.count - maxParts
            let limit = area[opaque.sorted(by: byArea)[min(opaque.count - 1, excess)]]
            var merged = 0
            for r in sorted {
                if area[r] > limit { break }
                if parent[r] != r { continue }
                gather(r)
                if touched.isEmpty { clearGather(); continue }
                let t = bestTarget(r)
                let shared = acc[t]
                clearGather()
                mergeInto(r, t, shared)
                merged += 1
            }
            if merged == 0 { break }
        }

        // Sıkıştırılmış kimlikler
        var newId = [Int](repeating: -1, count: nc)
        var count = 0
        var finalLabel: [Int] = []
        var rid = [Int](repeating: 0, count: n)
        for p in 0..<n {
            let r = find(comp[p])
            if newId[r] < 0 { newId[r] = count; count += 1; finalLabel.append(compLabel[r]) }
            rid[p] = newId[r]
        }
        return RegionMap(w: w, h: h, rid: rid, count: count, label: finalLabel)
    }
}
