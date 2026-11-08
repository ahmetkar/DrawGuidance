import Foundation

struct RoutePoint: Equatable {
    var x: Float
    var y: Float
    init(_ x: Float, _ y: Float) { self.x = x; self.y = y }
}

struct RouteRect: Equatable {
    var left: Float, top: Float, right: Float, bottom: Float
    var centerX: Float { (left + right) / 2 }
    var centerY: Float { (top + bottom) / 2 }
    var width: Float { right - left }
    var height: Float { bottom - top }
}

/// Diyagram oklarının yolu (Android `DiagramRouting` ile aynı algoritma).
enum DiagramRouting {
    static let CENTER_ZONE: Float = 0.4

    static func rectOf(_ node: DiagramNode, scale: Float = 1) -> RouteRect {
        RouteRect(left: node.x * scale, top: node.y * scale, right: (node.x + node.w) * scale, bottom: (node.y + node.h) * scale)
    }

    static func port(_ r: RouteRect, _ side: DiagramSide) -> RoutePoint {
        switch side {
        case .TOP: return RoutePoint(r.centerX, r.top)
        case .RIGHT: return RoutePoint(r.right, r.centerY)
        case .BOTTOM: return RoutePoint(r.centerX, r.bottom)
        case .LEFT: return RoutePoint(r.left, r.centerY)
        case .AUTO: return RoutePoint(r.centerX, r.centerY)
        }
    }

    static func direction(_ side: DiagramSide) -> RoutePoint {
        switch side {
        case .TOP: return RoutePoint(0, -1)
        case .RIGHT: return RoutePoint(1, 0)
        case .BOTTOM: return RoutePoint(0, 1)
        case .LEFT: return RoutePoint(-1, 0)
        case .AUTO: return RoutePoint(0, 0)
        }
    }

    static func nearestSide(_ r: RouteRect, _ p: RoutePoint) -> DiagramSide {
        let nx = (p.x - r.centerX) / max(1e-3, r.width / 2)
        let ny = (p.y - r.centerY) / max(1e-3, r.height / 2)
        if abs(nx) >= abs(ny) { return nx >= 0 ? .RIGHT : .LEFT }
        return ny >= 0 ? .BOTTOM : .TOP
    }

    static func sideAt(_ r: RouteRect, _ p: RoutePoint, center: Float = CENTER_ZONE) -> DiagramSide {
        let nx = (p.x - r.centerX) / max(1e-3, r.width / 2)
        let ny = (p.y - r.centerY) / max(1e-3, r.height / 2)
        return max(abs(nx), abs(ny)) < center ? .AUTO : nearestSide(r, p)
    }

    static func route(_ a: RouteRect, _ b: RouteRect, _ fromSide: DiagramSide, _ toSide: DiagramSide, margin: Float) -> [RoutePoint] {
        if fromSide == .AUTO && toSide == .AUTO { return straight(a, b) }
        let sa = fromSide == .AUTO ? nearestSide(a, RoutePoint(b.centerX, b.centerY)) : fromSide
        let sb = toSide == .AUTO ? nearestSide(b, port(a, sa)) : toSide
        let s = port(a, sa), e = port(b, sb)
        let da = direction(sa), db = direction(sb)
        let p1 = RoutePoint(s.x + da.x * margin, s.y + da.y * margin)
        let p2 = RoutePoint(e.x + db.x * margin, e.y + db.y * margin)
        let mx = (p1.x + p2.x) / 2, my = (p1.y + p2.y) / 2
        let candidates: [[RoutePoint]] = [
            [s, RoutePoint(e.x, s.y), e],
            [s, RoutePoint(s.x, e.y), e],
            [s, RoutePoint(mx, s.y), RoutePoint(mx, e.y), e],
            [s, RoutePoint(s.x, my), RoutePoint(e.x, my), e],
            [s, p1, RoutePoint(p1.x, p2.y), p2, e],
            [s, p1, RoutePoint(p2.x, p1.y), p2, e],
            [s, p1, RoutePoint(mx, p1.y), RoutePoint(mx, p2.y), p2, e],
            [s, p1, RoutePoint(p1.x, my), RoutePoint(p2.x, my), p2, e],
            outside(s, e, p1, p2, a, b, margin, horizontal: true),
            outside(s, e, p1, p2, a, b, margin, horizontal: false),
        ].map(simplify)
        // Kotlin minByOrNull: eşitlikte ilk aday.
        var best: [RoutePoint]? = nil
        var bestScore = Float.greatestFiniteMagnitude
        for c in candidates {
            let sc = score(c, da, db, a, b)
            if best == nil || sc < bestScore { best = c; bestScore = sc }
        }
        return best ?? [s, e]
    }

    static func route(_ edge: DiagramEdge, _ from: DiagramNode, _ to: DiagramNode, scale: Float = 1, margin: Float? = nil) -> [RoutePoint] {
        route(rectOf(from, scale: scale), rectOf(to, scale: scale), edge.fromSide, edge.toSide, margin: margin ?? 0.03 * scale)
    }

    static func midpoint(_ path: [RoutePoint]) -> RoutePoint {
        if path.count < 2 { return path.first ?? RoutePoint(0, 0) }
        var left = length(path) / 2
        for i in 1..<path.count {
            let a = path[i - 1], b = path[i]
            let l = hypotf(b.x - a.x, b.y - a.y)
            if l >= left && l > 0 {
                let t = left / l
                return RoutePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            left -= l
        }
        return path[path.count - 1]
    }

    static func distance(_ path: [RoutePoint], _ p: RoutePoint) -> Float {
        if path.count == 1 { return hypotf(p.x - path[0].x, p.y - path[0].y) }
        var best = Float.greatestFiniteMagnitude
        for i in 1..<max(path.count, 1) { best = min(best, segmentDistance(p, path[i - 1], path[i])) }
        return best
    }

    static func length(_ path: [RoutePoint]) -> Float {
        var total: Float = 0
        if path.count < 2 { return 0 }
        for i in 1..<path.count { total += hypotf(path[i].x - path[i - 1].x, path[i].y - path[i - 1].y) }
        return total
    }

    private static func straight(_ a: RouteRect, _ b: RouteRect) -> [RoutePoint] {
        let ca = RoutePoint(a.centerX, a.centerY), cb = RoutePoint(b.centerX, b.centerY)
        return [border(a, ca, cb), border(b, cb, ca)]
    }

    private static func border(_ r: RouteRect, _ center: RoutePoint, _ towards: RoutePoint) -> RoutePoint {
        let dx = towards.x - center.x, dy = towards.y - center.y
        if abs(dx) < 1e-4 && abs(dy) < 1e-4 { return center }
        let tx = abs(dx) < 1e-4 ? Float.greatestFiniteMagnitude : (r.width / 2) / abs(dx)
        let ty = abs(dy) < 1e-4 ? Float.greatestFiniteMagnitude : (r.height / 2) / abs(dy)
        let t = min(tx, ty)
        return RoutePoint(center.x + dx * t, center.y + dy * t)
    }

    private static func outside(_ s: RoutePoint, _ e: RoutePoint, _ p1: RoutePoint, _ p2: RoutePoint,
                                _ a: RouteRect, _ b: RouteRect, _ margin: Float, horizontal: Bool) -> [RoutePoint] {
        if horizontal {
            let top = min(a.top, b.top) - margin
            let bottom = max(a.bottom, b.bottom) + margin
            let y = abs(p1.y - top) + abs(p2.y - top) <= abs(p1.y - bottom) + abs(p2.y - bottom) ? top : bottom
            return [s, p1, RoutePoint(p1.x, y), RoutePoint(p2.x, y), p2, e]
        }
        let left = min(a.left, b.left) - margin
        let right = max(a.right, b.right) + margin
        let x = abs(p1.x - left) + abs(p2.x - left) <= abs(p1.x - right) + abs(p2.x - right) ? left : right
        return [s, p1, RoutePoint(x, p1.y), RoutePoint(x, p2.y), p2, e]
    }

    private static func simplify(_ path: [RoutePoint]) -> [RoutePoint] {
        var out: [RoutePoint] = []
        for p in path {
            if let last = out.last, abs(last.x - p.x) < 1e-4, abs(last.y - p.y) < 1e-4 { continue }
            if out.count >= 2 {
                let a = out[out.count - 2], b = out[out.count - 1]
                let collinear = (abs(a.x - b.x) < 1e-4 && abs(b.x - p.x) < 1e-4) || (abs(a.y - b.y) < 1e-4 && abs(b.y - p.y) < 1e-4)
                if collinear { out[out.count - 1] = p } else { out.append(p) }
            } else {
                out.append(p)
            }
        }
        return out
    }

    private static func score(_ path: [RoutePoint], _ da: RoutePoint, _ db: RoutePoint, _ a: RouteRect, _ b: RouteRect) -> Float {
        if path.count < 2 { return Float.greatestFiniteMagnitude }
        var penalty: Float = 0
        let scale = max(max(max(a.width, a.height), max(b.width, b.height)), 1e-3)
        for i in 1..<path.count {
            let p = path[i - 1], q = path[i]
            if abs(p.x - q.x) > 1e-4 && abs(p.y - q.y) > 1e-4 { penalty += 1000 * scale }
            if i > 1 && crosses(p, q, a) { penalty += 100 * scale }
            if i < path.count - 1 && crosses(p, q, b) { penalty += 100 * scale }
            if i == 1 && crosses(p, q, b) { penalty += 100 * scale }
            if i == path.count - 1 && crosses(p, q, a) { penalty += 100 * scale }
        }
        let first = unit(path[0], path[1])
        let last = unit(path[path.count - 2], path[path.count - 1])
        if first.x * da.x + first.y * da.y < 0.5 { penalty += 50 * scale }
        if last.x * -db.x + last.y * -db.y < 0.5 { penalty += 50 * scale }
        return length(path) + Float(path.count - 2) * 0.15 * scale + penalty
    }

    private static func unit(_ a: RoutePoint, _ b: RoutePoint) -> RoutePoint {
        let l = hypotf(b.x - a.x, b.y - a.y)
        return l < 1e-6 ? RoutePoint(0, 0) : RoutePoint((b.x - a.x) / l, (b.y - a.y) / l)
    }

    private static func crosses(_ p: RoutePoint, _ q: RoutePoint, _ r: RouteRect) -> Bool {
        let inset = min(r.width, r.height) * 0.02
        let l = r.left + inset, t = r.top + inset, rr = r.right - inset, bb = r.bottom - inset
        return max(p.x, q.x) > l && min(p.x, q.x) < rr && max(p.y, q.y) > t && min(p.y, q.y) < bb
    }

    private static func segmentDistance(_ p: RoutePoint, _ a: RoutePoint, _ b: RoutePoint) -> Float {
        let vx = b.x - a.x, vy = b.y - a.y
        let len2 = vx * vx + vy * vy
        let t: Float = len2 < 1e-9 ? 0 : min(max(((p.x - a.x) * vx + (p.y - a.y) * vy) / len2, 0), 1)
        return hypotf(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
    }
}
