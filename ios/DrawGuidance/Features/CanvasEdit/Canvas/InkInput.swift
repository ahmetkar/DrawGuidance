import Foundation

/// v20 çizim girdisi süzgeci (Android `InkFilter`): sabitleme (ip çekme + kayan ortalama) ve çizim hızı (gecikmeli takip).
/// Mesafeler tuval birimindedir; `unit` ekrandaki 1 noktanın tuval birimi karşılığı.
final class InkFilter {
    struct Point: Equatable { var x: Float; var y: Float; var pressure: Float = 1; var tilt: Float = 0; var orient: Float = 0 }

    static let MAX_STRING_PX: Float = 26
    static let MAX_WINDOW: Float = 8
    static let MAX_LAG_MS: Float = 240
    static let MIN_STEP_PX: Float = 0.4

    private let unit: Float
    private let radius: Float
    private let window: Int
    private let tau: Float

    var active: Bool { radius > 0 || window > 1 || tau > 0 }
    var needsTicks: Bool { tau > 0 }

    private var started = false
    private var ax: Float = 0, ay: Float = 0
    private var wx: [Float], wy: [Float]
    private var wCount = 0, wNext = 0
    private var fx: Float = 0, fy: Float = 0, tx: Float = 0, ty: Float = 0
    private var lastTime: Int64 = 0
    private var dyn = Point(x: 0, y: 0)
    private var lastOutX = Float.nan, lastOutY = Float.nan

    init(stabilize: Float, speed: Float, unit: Float) {
        self.unit = unit
        let s = stabilize.coerceIn(0, 1)
        radius = s * Self.MAX_STRING_PX * unit
        window = 1 + Int((s * Self.MAX_WINDOW).rounded())
        tau = speed >= 0.999 ? 0 : (1 - speed.coerceIn(0.1, 1)) * Self.MAX_LAG_MS
        wx = [Float](repeating: 0, count: window); wy = wx
    }

    func start(_ p: Point, _ time: Int64) -> [Point] {
        started = true
        ax = p.x; ay = p.y
        wCount = 0; wNext = 0
        pushWindow(p.x, p.y)
        fx = p.x; fy = p.y; tx = p.x; ty = p.y
        lastTime = time
        dyn = p
        return [emit(fx, fy)]
    }

    func move(_ p: Point, _ time: Int64) -> [Point] {
        if !started { return start(p, time) }
        dyn = p
        let d = hypot(p.x - ax, p.y - ay)
        if radius > 0 {
            if d > radius {
                let k = (d - radius) / d
                ax += (p.x - ax) * k; ay += (p.y - ay) * k
            } else if tau <= 0 {
                return []
            }
        } else { ax = p.x; ay = p.y }
        pushWindow(ax, ay)
        tx = avgX(); ty = avgY()
        return follow(time)
    }

    func tick(_ time: Int64) -> [Point] { !started || tau <= 0 ? [] : follow(time) }

    func finish(_ end: Point?, _ time: Int64) -> [Point] {
        if !started { return [] }
        var out: [Point] = []
        if let end { dyn = end; ax = end.x; ay = end.y }
        for _ in 0..<max(window - 1, 0) {
            pushWindow(ax, ay)
            tx = avgX(); ty = avgY()
            out += catchUp()
        }
        tx = ax; ty = ay
        out += catchUp()
        lastTime = max(lastTime, time)
        return out
    }

    private func follow(_ time: Int64) -> [Point] {
        let dt = max(time - lastTime, 0)
        lastTime = max(lastTime, time)
        if tau <= 0 { fx = tx; fy = ty } else {
            let a = 1 - exp(-Float(dt) / tau)
            fx += (tx - fx) * a; fy += (ty - fy) * a
        }
        return moved(fx, fy) ? [emit(fx, fy)] : []
    }

    private func catchUp() -> [Point] {
        let dist = hypot(tx - fx, ty - fy)
        if dist < Self.MIN_STEP_PX * unit { return [] }
        let n = Int(dist / (4 * unit)).coerceIn(1, 24)
        let sx = fx, sy = fy
        var out: [Point] = []
        for i in 1...n {
            let u = Float(i) / Float(n)
            fx = sx + (tx - sx) * u; fy = sy + (ty - sy) * u
            if moved(fx, fy) { out.append(emit(fx, fy)) }
        }
        return out
    }

    private func moved(_ x: Float, _ y: Float) -> Bool { lastOutX.isNaN || hypot(x - lastOutX, y - lastOutY) >= Self.MIN_STEP_PX * unit }

    private func emit(_ x: Float, _ y: Float) -> Point {
        lastOutX = x; lastOutY = y
        return Point(x: x, y: y, pressure: dyn.pressure, tilt: dyn.tilt, orient: dyn.orient)
    }

    private func pushWindow(_ x: Float, _ y: Float) {
        wx[wNext] = x; wy[wNext] = y
        wNext = (wNext + 1) % window
        if wCount < window { wCount += 1 }
    }

    private func avgX() -> Float { var s: Float = 0; for i in 0..<wCount { s += wx[i] }; return s / Float(wCount) }
    private func avgY() -> Float { var s: Float = 0; for i in 0..<wCount { s += wy[i] }; return s / Float(wCount) }
}

/// v23 yazı kutusunun tutamaklarla boyutlandırılması (Android `TextBoxResize`).
enum TextBoxResize {
    static let WIDTH = 1, HEIGHT = 2, BOTH = 3
    private static let MAX: Float = 20000

    static func handles(_ stroke: Stroke) -> [(P, Int)] {
        let q = stroke.points
        guard stroke.brushType == .TEXT, stroke.text != nil, q.count >= 4 else { return [] }
        func mid(_ a: StrokePoint, _ b: StrokePoint) -> P { P((a.x + b.x) / 2, (a.y + b.y) / 2) }
        return [(mid(q[1], q[2]), WIDTH), (mid(q[2], q[3]), HEIGHT), (P(q[2].x, q[2].y), BOTH)]
    }

    static func resize(_ stroke: Stroke, _ handle: Int, _ point: P) -> Stroke? {
        guard var spec = stroke.text else { return nil }
        let q = stroke.points
        if q.count < 4 { return nil }
        let (ow, oh) = TextRenderer.measure(spec, stroke.sizePx)
        let ux = q[1].x - q[0].x, uy = q[1].y - q[0].y, vx = q[3].x - q[0].x, vy = q[3].y - q[0].y
        let ul = hypot(ux, uy), vl = hypot(vx, vy)
        if ul < 1e-3 || vl < 1e-3 || ow <= 0 || oh <= 0 { return nil }
        let su = ul / ow, sv = vl / oh
        let dx = point.x - q[0].x, dy = point.y - q[0].y
        let alongU = (dx * ux + dy * uy) / ul, alongV = (dx * vx + dy * vy) / vl
        let size = TextRenderer.effectiveSize(spec, stroke.sizePx)
        let pads = TextRenderer.pads(spec, stroke.sizePx)
        let minW = pads[0] + pads[2] + size * 1.2
        let old = spec.box ?? TextBox()
        var box = old
        if handle & WIDTH != 0 { box.width = min(max(minW, alongU / su), MAX) }
        if handle & HEIGHT != 0 { box.height = min(max(size, alongV / sv), MAX) }
        if box == old { return nil }
        spec.box = box
        var out = stroke
        out.text = spec
        out.points = TextRenderer.refit(stroke, spec, stroke.sizePx).map { StrokePoint(ContentCodec.quantize($0.x), ContentCodec.quantize($0.y)) }
        return out
    }
}

/// Parmakla çizilen, henüz kaydedilmemiş çizgi (Android `LiveStrokeLayer`'ın veri kısmı).
final class LiveStroke {
    private(set) var strokeId: String?
    private(set) var type: BrushType = .PENCIL
    private(set) var colorHex = "#000000"
    private(set) var sizePx: Float = 1
    private(set) var opacity: Float = 1
    private(set) var flow: Float = 1
    private(set) var strength: Float = 1
    private(set) var params: BrushParams?
    private(set) var points: [StrokePoint] = []
    private(set) var finished = false
    /// Her değişimde artar (görünüm yenilensin).
    private(set) var version = 0

    var isActive: Bool { strokeId != nil }
    var isEraser: Bool { type == .ERASER }
    var inkWidth: Float { type.inkWidth(sizePx, strength: strength) }

    func start(_ id: String, _ type: BrushType, _ colorHex: String, _ size: Float, _ opacity: Float, _ flow: Float, _ strength: Float, params: BrushParams?) {
        strokeId = id; self.type = type; self.colorHex = colorHex; sizePx = size; self.opacity = opacity; self.flow = flow
        self.strength = strength; self.params = params; points = []; finished = false; version += 1
    }

    func add(_ x: Float, _ y: Float, _ pressure: Float, _ tilt: Float, _ orient: Float) {
        points.append(StrokePoint(x, y, pressure: pressure, tilt: tilt, orient: orient))
        version += 1
    }

    func finish() { finished = true; version += 1 }
    func release() { strokeId = nil; points = []; finished = false; version += 1 }
    func lastPoint() -> StrokePoint? { points.last }

    /// Çizilecek hâli.
    func stroke() -> Stroke? {
        guard let id = strokeId, !points.isEmpty else { return nil }
        return Stroke(id: id, points: points, colorHex: colorHex, brushType: type, sizePx: sizePx, opacity: opacity, order: 0,
                      flow: flow, strength: strength, params: params)
    }
}
