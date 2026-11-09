import Foundation

/// Compose `Rect` karşılığı (tuval koordinatları, y aşağı). Düzenleyicinin saf işlemleri bunu kullanır.
struct FRect: Equatable {
    var left: Float, top: Float, right: Float, bottom: Float
    init(_ left: Float, _ top: Float, _ right: Float, _ bottom: Float) {
        self.left = left; self.top = top; self.right = right; self.bottom = bottom
    }
    init(left: Float, top: Float, right: Float, bottom: Float) { self.init(left, top, right, bottom) }
    init(center: P, radius: Float) { self.init(center.x - radius, center.y - radius, center.x + radius, center.y + radius) }
    static let zero = FRect(0, 0, 0, 0)
    var width: Float { right - left }
    var height: Float { bottom - top }
    var center: P { P((left + right) / 2, (top + bottom) / 2) }
    var topLeft: P { P(left, top) }
    var bottomRight: P { P(right, bottom) }
    var topRight: P { P(right, top) }
    var bottomLeft: P { P(left, bottom) }
    var isEmpty: Bool { left >= right || top >= bottom }
    var minDimension: Float { min(abs(width), abs(height)) }
    var maxDimension: Float { max(abs(width), abs(height)) }
    func overlaps(_ o: FRect) -> Bool { !(left >= o.right || o.left >= right || top >= o.bottom || o.top >= bottom) }
    func contains(_ p: P) -> Bool { p.x >= left && p.x < right && p.y >= top && p.y < bottom }
    func inflate(_ d: Float) -> FRect { FRect(left - d, top - d, right + d, bottom + d) }
    func deflate(_ d: Float) -> FRect { inflate(-d) }
    func intersect(_ o: FRect) -> FRect { FRect(max(left, o.left), max(top, o.top), min(right, o.right), min(bottom, o.bottom)) }
    func translate(_ dx: Float, _ dy: Float) -> FRect { FRect(left + dx, top + dy, right + dx, bottom + dy) }
    func union(_ o: FRect) -> FRect { FRect(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom)) }
}

extension P {
    static let zero = P(0, 0)
    var distance: Float { hypotf(x, y) }
    static prefix func - (a: P) -> P { P(-a.x, -a.y) }
    static func / (a: P, k: Float) -> P { P(a.x / k, a.y / k) }
    static func * (k: Float, a: P) -> P { P(a.x * k, a.y * k) }
    static func += (a: inout P, b: P) { a = a + b }
}

extension StrokePoint {
    var p: P { P(x, y) }
}
