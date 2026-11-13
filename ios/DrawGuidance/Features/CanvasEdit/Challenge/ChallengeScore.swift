import Foundation

/// v38 çizim görevinin puanı (Android `ChallengeScore`, 0–100). Çizgi puanı: isabet ve kapsamanın F1'i (yerinde ya da
/// kutusu uydurularak ×0,95), karalama cezası; boya puanı: boyanmışsa 0,3 + 0,7 × renk benzerliği; yerleştir ve boya:
/// %40 çizgi + %60 boya. Geçme 60; yıldızlar 60, 75, 90.
enum ChallengeScore {
    static let PASS = 60
    static let STARS = [60, 75, 90]
    static func stars(_ score: Int) -> Int { STARS.filter { score >= $0 }.count }
    static func passed(_ score: Int) -> Bool { score >= PASS }

    private static let SAMPLES: Float = 160
    static let NEAR: Float = 0.045
    static let FAR: Float = 0.12
    static let ALIGNED: Float = 0.95
    static let PAINTED: Float = 0.3
    static let COLOR_FAR: Float = 0.35
    static let INK_MAX: Float = 2
    static let OUTLINE_WEIGHT: Float = 0.4
    private static let MAX_RGB: Float = 441.673
    private static let THIN: Float = 0.05

    static func outline(_ drawn: [[P]], _ target: [[P]]) -> Int { outline(drawn, target, near: NEAR, far: FAR) }

    /// v50 verilen yakınlık sınırlarıyla (hedef köşegenine oran); yetişkin alıştırmaları daha dar sınır kullanır.
    static func outline(_ drawn: [[P]], _ target: [[P]], near nearRatio: Float, far farRatio: Float) -> Int {
        guard let tBox = bounds(target) else { return 0 }
        let diag = max(hypot(tBox.width, tBox.height), 1)
        let step = diag / SAMPLES
        let t = resample(target, step), d = resample(drawn, step)
        if t.isEmpty || d.count < 2 { return 0 }
        let near = diag * nearRatio, far = diag * farRatio
        let lenT = max(length(target), 1), lenD = length(drawn)
        let inPlace = f1(d, t, near, far) * excess(lenD / lenT)
        var aligned: Float = 0
        if let dBox = bounds(drawn), let (pts, s) = align(d, dBox, tBox) {
            aligned = f1(pts, t, near, far) * excess(lenD * s / lenT) * ALIGNED
        }
        return percent(max(inPlace, aligned))
    }

    /// Boyanacak yerlerin puanı (beklenen renk → görünen renk; nil boyanmamış).
    static func paint(_ checks: [(String, String?)]) -> Int {
        if checks.isEmpty { return 0 }
        let sum = checks.reduce(Double(0)) { $0 + Double(paintCredit($1.0, $1.1)) }
        return percent(Float(sum / Double(checks.count)))
    }

    static func paintCredit(_ want: String, _ got: String?) -> Float {
        guard let got, !isPaper(got), let a = rgb(got), let b = rgb(want) else { return 0 }
        let dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF), dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF), db = (a & 0xFF) - (b & 0xFF)
        let dist = Float(dr * dr + dg * dg + db * db).squareRoot() / MAX_RGB
        let similar = min(max(1 - dist / COLOR_FAR, 0), 1)
        return PAINTED + (1 - PAINTED) * similar
    }

    static func combine(_ outline: Int, _ paint: Int) -> Int {
        Int((OUTLINE_WEIGHT * Float(outline) + (1 - OUTLINE_WEIGHT) * Float(paint)).rounded())
    }

    static func isPaper(_ hex: String) -> Bool { rgb(hex).map { $0 == 0xFFFFFF } ?? true }

    private static func percent(_ f: Float) -> Int { min(max(Int((f * 100).rounded()), 0), 100) }

    /// "#RRGGBB" ya da "#AARRGGBB" → 0xRRGGBB.
    static func rgb(_ hex: String) -> Int? {
        var h = hex.trimmingCharacters(in: .whitespaces)
        if h.hasPrefix("#") { h.removeFirst() }
        guard let v = Int64(h, radix: 16) else { return nil }
        switch h.count {
        case 6: return Int(v)
        case 8: return Int(v & 0xFFFFFF)
        default: return nil
        }
    }

    private static func f1(_ d: [P], _ t: [P], _ near: Float, _ far: Float) -> Float {
        let tGrid = PointGrid(t, far), dGrid = PointGrid(d, far)
        let precision = Float(d.reduce(Double(0)) { $0 + Double(credit(tGrid.nearest($1), near, far)) } / Double(d.count))
        let recall = Float(t.reduce(Double(0)) { $0 + Double(credit(dGrid.nearest($1), near, far)) } / Double(t.count))
        return precision + recall <= 0 ? 0 : 2 * precision * recall / (precision + recall)
    }

    private static func credit(_ d: Float, _ near: Float, _ far: Float) -> Float {
        if d <= near { return 1 }
        if d >= far { return 0 }
        return (far - d) / (far - near)
    }

    static func resample(_ lines: [[P]], _ step: Float) -> [P] {
        var out: [P] = []
        for line in lines where !line.isEmpty {
            out.append(line[0])
            var carry: Float = 0
            for i in 1..<max(line.count, 1) {
                let a = line[i - 1], b = line[i]
                let len = hypot(b.x - a.x, b.y - a.y)
                if len <= 0 { continue }
                var at = step - carry
                while at <= len {
                    let k = at / len
                    out.append(P(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k))
                    at += step
                }
                carry = (carry + len).truncatingRemainder(dividingBy: step)
            }
            if line.count > 1 { out.append(line[line.count - 1]) }
        }
        return out
    }

    static func bounds(_ lines: [[P]]) -> FRect? {
        var minX = Float.greatestFiniteMagnitude, minY = Float.greatestFiniteMagnitude
        var maxX = -Float.greatestFiniteMagnitude, maxY = -Float.greatestFiniteMagnitude
        var any = false
        for line in lines { for p in line { any = true; minX = min(minX, p.x); minY = min(minY, p.y); maxX = max(maxX, p.x); maxY = max(maxY, p.y) } }
        return any ? FRect(minX, minY, maxX, maxY) : nil
    }

    private static func align(_ points: [P], _ from: FRect, _ to: FRect) -> ([P], Float)? {
        let fw = from.width, fh = from.height, longest = max(fw, fh)
        if longest < 1e-3 { return nil }
        let thin = min(fw, fh) < longest * THIN
        if thin != (min(to.width, to.height) < max(to.width, to.height) * THIN) { return nil }
        let raw = thin ? max(to.width, to.height) / longest : ((to.width / fw) * (to.height / fh)).squareRoot()
        let s = min(max(raw, 0.2), 5)
        let fc = from.center, tc = to.center
        return (points.map { P(tc.x + ($0.x - fc.x) * s, tc.y + ($0.y - fc.y) * s) }, s)
    }

    private static func excess(_ ratio: Float) -> Float { ratio <= INK_MAX ? 1 : INK_MAX / ratio }

    static func length(_ lines: [[P]]) -> Float {
        var sum: Float = 0
        for line in lines where line.count > 1 { for i in 1..<line.count { sum += hypot(line[i].x - line[i - 1].x, line[i].y - line[i - 1].y) } }
        return sum
    }

    static func distanceToPolyline(_ p: P, _ line: [P], _ closed: Bool) -> Float {
        if line.isEmpty { return .greatestFiniteMagnitude }
        if line.count == 1 { return hypot(p.x - line[0].x, p.y - line[0].y) }
        var best = Float.greatestFiniteMagnitude
        let n = closed ? line.count : line.count - 1
        for i in 0..<n { best = min(best, segment(p, line[i], line[(i + 1) % line.count])) }
        return best
    }

    static func segment(_ p: P, _ a: P, _ b: P) -> Float {
        let dx = b.x - a.x, dy = b.y - a.y, l = dx * dx + dy * dy
        let t = l <= 0 ? 0 : min(max(((p.x - a.x) * dx + (p.y - a.y) * dy) / l, 0), 1)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /// En yakın nokta araması için ızgara (gözden uzaktakiler "çok uzak").
    private struct PointGrid {
        private var map: [Int64: [P]] = [:]
        private let cell: Float
        init(_ points: [P], _ cell: Float) {
            self.cell = cell
            for p in points { map[Self.key(Self.cx(p.x, cell), Self.cx(p.y, cell)), default: []].append(p) }
        }
        private static func cx(_ v: Float, _ cell: Float) -> Int32 { Int32(clamping: Int((v / cell).rounded(.down))) }
        private static func key(_ x: Int32, _ y: Int32) -> Int64 { (Int64(x) << 32) | (Int64(y) & 0xFFFF_FFFF) }
        func nearest(_ p: P) -> Float {
            let x = Self.cx(p.x, cell), y = Self.cx(p.y, cell)
            var best = Float.greatestFiniteMagnitude
            for i in Int32(-1)...1 { for j in Int32(-1)...1 {
                guard let list = map[Self.key(x &+ i, y &+ j)] else { continue }
                for q in list { let d = hypot(p.x - q.x, p.y - q.y); if d < best { best = d } }
            } }
            return best
        }
    }
}
