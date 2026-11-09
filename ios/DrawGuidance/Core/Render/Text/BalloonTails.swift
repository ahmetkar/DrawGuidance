import Foundation

/// v36 balon kuyruğunun yönü (Android `TailDirection`).
enum TailDirection: String, CaseIterable { case BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT }

/// v27 balon kuyruklarının dizilişi, tuvaldeki yerleri ve sürüklenmesi (Android `BalloonRenderer.tailsToward`,
/// `tailState`, `defaultTails` ve `BalloonTails`).
enum BalloonTails {
    static let MIN_TAIL: Float = 0.15
    static let MAX_TAIL: Float = 1.6

    static func defaultTails(_ count: Int) -> [BalloonTail] {
        switch count {
        case 0: return []
        case 1: return [BalloonTail(x: 0.3, y: 1.65)]
        default:
            return (0..<count).map { i in
                let f = Float(i) / Float(count - 1)
                return BalloonTail(x: 0.1 + 0.8 * f, y: 1.6 + 0.1 * abs(f - 0.5), curve: f < 0.5 ? 0.3 : -0.3)
            }
        }
    }

    static func tailFromLocal(_ x: Float, _ y: Float, _ w: Float, _ h: Float, curve: Float = 0.3) -> BalloonTail {
        BalloonTail(x: (x / max(w, 1e-3)).coerceIn(-3, 4), y: (y / max(h, 1e-3)).coerceIn(-3, 4), curve: curve)
    }

    static func tailsToward(_ dir: TailDirection, _ count: Int, _ length: Float, _ curve: Float) -> [BalloonTail] {
        if count <= 0 { return [] }
        let l = length.coerceIn(MIN_TAIL, MAX_TAIL)
        return (0..<count).map { i in
            let f: Float = count == 1 ? -1 : Float(i) / Float(count - 1)
            func along(_ single: Float) -> Float { f < 0 ? single : 0.15 + 0.7 * f }
            let side: Float = f < 0 ? 0.62 : 0.25 + 0.5 * f
            switch dir {
            case .BOTTOM_LEFT: return BalloonTail(x: along(0.3), y: 1 + l, curve: curve)
            case .BOTTOM_RIGHT: return BalloonTail(x: along(0.7), y: 1 + l, curve: -curve)
            case .TOP_LEFT: return BalloonTail(x: along(0.3), y: -l, curve: -curve)
            case .TOP_RIGHT: return BalloonTail(x: along(0.7), y: -l, curve: curve)
            case .LEFT: return BalloonTail(x: -l, y: side, curve: -curve)
            case .RIGHT: return BalloonTail(x: 1 + l, y: side, curve: curve)
            }
        }
    }

    /// İlk kuyruğun yönü, uzaklığı ve eğriliği (kuyruk yoksa alt sol).
    static func tailState(_ b: BalloonSpec) -> (TailDirection, Float, Float) {
        guard let t = b.tails.first else { return (.BOTTOM_LEFT, 0.6, 0.3) }
        let dir: TailDirection
        if t.y > 1 { dir = t.x < 0.5 ? .BOTTOM_LEFT : .BOTTOM_RIGHT }
        else if t.y < 0 { dir = t.x < 0.5 ? .TOP_LEFT : .TOP_RIGHT }
        else if t.x < 0.5 { dir = .LEFT }
        else { dir = .RIGHT }
        let length: Float
        switch dir {
        case .BOTTOM_LEFT, .BOTTOM_RIGHT: length = t.y - 1
        case .TOP_LEFT, .TOP_RIGHT: length = -t.y
        case .LEFT: length = -t.x
        case .RIGHT: length = t.x - 1
        }
        let sign: Float = dir == .BOTTOM_RIGHT || dir == .TOP_LEFT || dir == .LEFT ? -1 : 1
        func r(_ v: Float) -> Float { Float(roundToLong(v * 100)) / 100 }
        return (dir, r(length.coerceIn(MIN_TAIL, MAX_TAIL)), r((t.curve * sign).coerceIn(-1, 1)))
    }

    private static func roundToLong(_ v: Float) -> Int64 { Int64((Double(v) + 0.5).rounded(.down)) }

    /// Kuyruk uçlarının tuval koordinatları (balon değilse boş).
    static func positions(_ stroke: Stroke) -> [P] {
        guard let b = stroke.text?.box?.balloon, stroke.points.count >= 4 else { return [] }
        let p = stroke.points
        return b.tails.map { t in
            P(p[0].x + (p[1].x - p[0].x) * t.x + (p[3].x - p[0].x) * t.y, p[0].y + (p[1].y - p[0].y) * t.x + (p[3].y - p[0].y) * t.y)
        }
    }

    /// [index]. kuyruğun ucu tuvaldeki [at] noktasına taşınmış balon.
    static func moved(_ stroke: Stroke, _ index: Int, _ at: P) -> Stroke? {
        guard var text = stroke.text, var box = text.box, var b = box.balloon, b.tails.indices.contains(index), stroke.points.count >= 4 else { return nil }
        let p = stroke.points
        let ux = p[1].x - p[0].x, uy = p[1].y - p[0].y
        let vx = p[3].x - p[0].x, vy = p[3].y - p[0].y
        let det = ux * vy - uy * vx
        if abs(det) < 1e-6 { return nil }
        let dx = at.x - p[0].x, dy = at.y - p[0].y
        let a = (dx * vy - dy * vx) / det
        let c = (ux * dy - uy * dx) / det
        b.tails[index].x = a.coerceIn(-3, 4); b.tails[index].y = c.coerceIn(-3, 4)
        box.balloon = b; text.box = box
        var s = stroke
        s.text = text
        return s
    }
}
