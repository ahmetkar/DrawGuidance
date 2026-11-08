import Foundation

/// Esneyen ya da yazı tipinden bağımsız işaretlerin geometrisi (Android `FormulaShapes`).
enum FormulaShapes {
    static func ribbon(_ pts: [Float], _ widths: [Float]) -> DrawOp {
        let n = pts.count / 2
        var left = [Float](repeating: 0, count: n * 2), right = left
        for k in 0..<n {
            let a = max(0, k - 1), b = min(n - 1, k + 1)
            var tx = pts[b * 2] - pts[a * 2], ty = pts[b * 2 + 1] - pts[a * 2 + 1]
            var len = (tx * tx + ty * ty).squareRoot()
            if !(len > 1e-6) { len = 1 }
            tx /= len; ty /= len
            let hw = widths[k] / 2
            left[k * 2] = pts[k * 2] - ty * hw; left[k * 2 + 1] = pts[k * 2 + 1] + tx * hw
            right[k * 2] = pts[k * 2] + ty * hw; right[k * 2 + 1] = pts[k * 2 + 1] - tx * hw
        }
        var poly: [Float] = []
        poly.reserveCapacity(n * 4)
        for k in 0..<n { poly.append(left[k * 2]); poly.append(left[k * 2 + 1]) }
        for k in stride(from: n - 1, through: 0, by: -1) { poly.append(right[k * 2]); poly.append(right[k * 2 + 1]) }
        return .line(poly, 0, closed: true, filled: true)
    }

    static func circle(_ cx: Float, _ cy: Float, _ r: Float, segments: Int = 14) -> DrawOp {
        var pts: [Float] = []
        for k in 0..<segments {
            let a = 2 * Double.pi * Double(k) / Double(segments)
            pts.append(cx + r * Float(cos(a))); pts.append(cy + r * Float(sin(a)))
        }
        return .line(pts, 0, closed: true, filled: true)
    }

    static func ellipse(_ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, _ thickness: Float, segments: Int = 24) -> DrawOp {
        var pts: [Float] = []
        for k in 0..<segments {
            let a = 2 * Double.pi * Double(k) / Double(segments)
            pts.append(cx + rx * Float(cos(a))); pts.append(cy + ry * Float(sin(a)))
        }
        return .line(pts, thickness, closed: true, filled: false)
    }

    private static func spline(_ ctrl: [Float], _ steps: Int) -> [Float] {
        let m = ctrl.count / 2
        var out: [Float] = []
        func p(_ k: Int, _ c: Int) -> Float { ctrl[min(max(k, 0), m - 1) * 2 + c] }
        for seg in 0..<(m - 1) {
            for s in 0..<steps {
                let t = Float(s) / Float(steps), t2 = t * t, t3 = t2 * t
                for c in 0...1 {
                    let p0 = p(seg - 1, c), p1 = p(seg, c), p2 = p(seg + 1, c), p3 = p(seg + 2, c)
                    let a = 2 * p1 + (-p0 + p2) * t
                    let b = (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                    let d = (-p0 + 3 * p1 - 3 * p2 + p3) * t3
                    out.append(0.5 * (a + b + d))
                }
            }
        }
        out.append(ctrl[(m - 1) * 2]); out.append(ctrl[(m - 1) * 2 + 1])
        return out
    }

    private static let INTEGRAL: [Float] = [
        0.425, 0.078, 0.405, 0.018, 0.362, 0.0, 0.318, 0.03, 0.292, 0.12,
        0.22, 0.5,
        0.148, 0.88, 0.122, 0.97, 0.078, 1.0, 0.035, 0.982, 0.015, 0.922,
    ]

    static func integral(_ x0: Float, _ top: Float, _ h: Float, _ maxWidth: Float) -> [DrawOp] {
        var ctrl = [Float](repeating: 0, count: INTEGRAL.count)
        for k in stride(from: 0, to: INTEGRAL.count, by: 2) {
            ctrl[k] = x0 + INTEGRAL[k] * h
            ctrl[k + 1] = top + INTEGRAL[k + 1] * h
        }
        let pts = spline(ctrl, 8)
        let n = pts.count / 2
        let tMin = maxWidth * 0.32
        let widths = (0..<n).map { k -> Float in
            let s = Float(k) / Float(n - 1)
            let z = (s - 0.5) / 0.21
            return tMin + (maxWidth - tMin) * expf(-z * z)
        }
        let r = max(maxWidth * 0.6, h * 0.028)
        return [ribbon(pts, widths), circle(pts[0] - r * 0.25, pts[1] + r * 0.15, r),
                circle(pts[(n - 1) * 2] + r * 0.25, pts[(n - 1) * 2 + 1] - r * 0.15, r)]
    }

    static func delimiterWidth(_ ch: String, _ h: Float, _ em: Float) -> Float {
        let hh = min(h, 6 * em)
        switch ch {
        case "(", ")": return 0.2 * em + 0.07 * hh + 0.12 * em
        case "[", "]", "⌊", "⌋", "⌈", "⌉": return 0.26 * em + 0.03 * hh + 0.1 * em
        case "{", "}": return 0.32 * em + 0.05 * hh + 0.1 * em
        case "⟨", "⟩": return 0.24 * em + 0.08 * hh + 0.1 * em
        case "|": return 0.28 * em
        case "‖": return 0.42 * em
        default: return 0.12 * em
        }
    }

    static func delimiter(_ ch: String, _ x0: Float, _ top: Float, _ bottom: Float, _ em: Float, _ rule: Float) -> [DrawOp] {
        let h = bottom - top
        let w = delimiterWidth(ch, h, em)
        let sb = 0.06 * em
        let l = x0 + sb, r = x0 + w - sb
        let mid = (top + bottom) / 2
        let stem = max(rule * 1.25, min(0.085 * em, rule * 1.25 + 0.006 * h))
        switch ch {
        case "(", ")":
            let left = ch == "("
            let n = 28
            let k = min(max(1.8 + 0.45 * (h / em - 1.2), 1.8), 4)
            let thMid = max(rule * 1.3, min(0.11 * em, 0.07 * em + 0.012 * h))
            let thEnd = max(rule * 0.55, 0.03 * em)
            let bulge = (r - l) - thMid / 2
            var pts = [Float](repeating: 0, count: n * 2), ws = [Float](repeating: 0, count: n)
            for j in 0..<n {
                let s = -1 + 2 * Float(j) / Float(n - 1)
                let off = bulge * (1 - powf(abs(s), k))
                let xEdge = left ? r - thEnd / 2 : l + thEnd / 2
                pts[j * 2] = left ? xEdge - off : xEdge + off
                pts[j * 2 + 1] = mid + s * (h / 2 - thEnd / 2)
                ws[j] = thEnd + (thMid - thEnd) * (1 - s * s)
            }
            return [ribbon(pts, ws)]
        case "[", "]", "⌊", "⌋", "⌈", "⌉":
            let left = ch == "[" || ch == "⌊" || ch == "⌈"
            let arm = max(rule * 0.95, 0.045 * em)
            let topArm = ch == "[" || ch == "]" || ch == "⌈" || ch == "⌉"
            let botArm = ch == "[" || ch == "]" || ch == "⌊" || ch == "⌋"
            let sx = left ? l : r - stem
            var out = [rect(sx, top, sx + stem, bottom)]
            if topArm { out.append(rect(l, top, r, top + arm)) }
            if botArm { out.append(rect(l, bottom - arm, r, bottom)) }
            return out
        case "{", "}":
            let left = ch == "{"
            let thick = max(rule * 1.3, min(0.09 * em, 0.07 * em + 0.008 * h))
            let thin = max(rule * 0.5, 0.028 * em)
            let xm = (l + r) / 2
            let tip = left ? l : r, end = left ? r : l
            let rr = min((h / 2) * 0.32, 0.3 * em + 0.04 * h)
            func half(_ sign: Float) -> DrawOp {
                let y0 = mid + sign * (h / 2 - thin / 2)
                let ctrl: [Float] = [
                    end, y0,
                    xm + (end - xm) * 0.35, y0 - sign * rr * 0.12,
                    xm, y0 - sign * rr * 0.75,
                    xm, mid + sign * rr * 0.75,
                    xm - (xm - tip) * 0.35, mid + sign * rr * 0.12,
                    tip, mid,
                ]
                let pts = spline(ctrl, 7)
                let n = pts.count / 2
                let ws = (0..<n).map { j -> Float in
                    let s = Float(j) / Float(n - 1)
                    let z = (s - 0.5) / 0.26
                    return thin + (thick - thin) * expf(-z * z * z * z)
                }
                return ribbon(pts, ws)
            }
            return [half(-1), half(1)]
        case "⟨", "⟩":
            let left = ch == "⟨"
            let a = left ? r : l, b = left ? l : r
            return [.line([a, top + rule / 2, b, mid, a, bottom - rule / 2], rule * 1.1)]
        case "|":
            let x = (l + r) / 2
            return [.line([x, top, x, bottom], stem * 0.9)]
        case "‖":
            let gap = 0.14 * em, x = (l + r) / 2
            return [.line([x - gap / 2, top, x - gap / 2, bottom], stem * 0.85), .line([x + gap / 2, top, x + gap / 2, bottom], stem * 0.85)]
        default: return []
        }
    }

    private static func rect(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float) -> DrawOp {
        .line([x0, y0, x1, y0, x1, y1, x0, y1], 0, closed: true, filled: true)
    }

    static func radical(_ x0: Float, _ yTop: Float, _ yBot: Float, _ em: Float, _ rule: Float) -> ([DrawOp], Float) {
        let h = yBot - yTop
        let hScale = min(max(h / em, 0.8), 1.6)
        let sw = (0.5 + 0.06 * min(max(h / em - 1, 0), 4)) * em
        let hook = min(0.5 * h, 0.42 * em * hScale)
        let p0x = x0 + 0.02 * em, p0y = yBot - hook + 0.07 * em
        let p1x = x0 + 0.13 * em, p1y = yBot - hook
        let p2x = x0 + 0.29 * em + 0.02 * min(h / em, 3) * em, p2y = yBot
        let p3x = x0 + sw, p3y = yTop
        let thick = rule * 1.9
        var ops: [DrawOp] = [.line([p0x, p0y, p1x, p1y], rule * 0.9)]
        let dx = p2x - p1x, dy = p2y - p1y
        let len = (dx * dx + dy * dy).squareRoot()
        let nx = -dy / len * thick / 2, ny = dx / len * thick / 2
        ops.append(.line([p1x - nx, p1y - ny, p1x + nx, p1y + ny, p2x + nx * 0.3, p2y + ny * 0.3, p2x - nx * 0.3, p2y - ny * 0.3], 0, closed: true, filled: true))
        ops.append(.line([p2x, p2y, p3x, p3y], rule))
        return (ops, sw)
    }
}
