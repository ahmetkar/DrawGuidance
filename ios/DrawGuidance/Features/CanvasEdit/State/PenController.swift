import Combine
import Foundation

/// Kalem aracının bir noktası (Android `PenAnchor`): [handle] eğri noktasının çıkış kontrol noktası;
/// v44 [bend] bu noktadan sonrakine giden çizgi elle eğildiyse ikinci derece Bezier'in kontrol noktası.
struct PenAnchor: Equatable {
    var point: P
    var handle: P? = nil
    var bend: P? = nil
}

/// Basit kalem aracı (Android `PenController`): dokunulan yerlere köşe, sürüklenen yerlere eğri noktası; ilk noktaya
/// dokununca yol kapanır. Sonuç düzenlenebilir bir vektör şekildir.
@MainActor
final class PenController: ObservableObject {
    private let store: EditorStore
    private unowned let host: EditorHost
    private let newShapeStyle: () -> ShapeStyle

    @Published private(set) var anchors: [PenAnchor] = []
    @Published private(set) var pending: PenAnchor?
    private var bending: Int?

    init(_ store: EditorStore, _ host: EditorHost, newShapeStyle: @escaping () -> ShapeStyle) {
        self.store = store; self.host = host; self.newShapeStyle = newShapeStyle
    }

    func start() {
        anchors = []; pending = nil; bending = nil
        host.startAction(.PEN)
    }

    func grab(_ point: P, tolerance: Float) -> Bool {
        if anchors.count < 2 { return false }
        if anchors.contains(where: { ($0.point - (point)).distance <= tolerance }) { return false }
        guard let seg = PenPath.segmentAt(anchors, point, tolerance) else { return false }
        bending = seg
        return true
    }

    func editDrag(_ point: P, done: Bool) {
        guard let i = bending else { return }
        guard anchors.indices.contains(i), anchors.indices.contains(i + 1) else { bending = nil; return }
        let a = anchors[i].point, b = anchors[i + 1].point
        let control = point * 2 - (a + b) / 2
        let straight = PenPath.distanceToLine(point, a, b) < 2
        anchors[i].bend = straight ? nil : control
        if done { bending = nil }
    }

    func onModeChange(_ to: EditMode) {
        if to != .PEN {
            if anchors.count >= 2 { finish(closed: false) }
            anchors = []; pending = nil; bending = nil
        }
    }

    func tap(_ point: P, tolerance: Float) {
        if anchors.count >= 2 && (anchors[0].point - (point)).distance <= tolerance * 1.8 { finish(closed: true); return }
        anchors.append(PenAnchor(point: point))
    }

    func drag(_ start: P, _ current: P, done: Bool) {
        let anchor = PenAnchor(point: start, handle: (current - (start)).distance > 2 ? current : nil)
        if done {
            pending = nil
            if anchors.count >= 2 && (anchors[0].point - (start)).distance < 1 { finish(closed: true) } else { anchors.append(anchor) }
        } else {
            pending = anchor
        }
    }

    func undoAnchor() {
        if !anchors.isEmpty { anchors.removeLast() }
        if !anchors.isEmpty { anchors[anchors.count - 1].bend = nil }
    }

    func cancelPath() { anchors = []; pending = nil; bending = nil }

    func sample(_ anchors: [PenAnchor], closed: Bool) -> [P] { PenPath.sample(anchors, closed: closed) }

    func finish(closed: Bool) {
        let list = anchors
        anchors = []; pending = nil; bending = nil
        guard list.count >= 2, let current = store.canvas else { return }
        let (pts, nodes) = PenPath.sampleWithNodes(list, closed: closed)
        let path = ShapePath(points: closed ? pts + [pts[0]] : pts, closed: closed, nodes: nodes)
        guard let stroke = ShapeFactory.fromPaths(InkFill.FREE_PATH, [path], style: newShapeStyle(), graph: nil,
                                                  id: UUID().uuidString.lowercased(), order: current.strokes.count) else { return }
        store.commit(current.strokes + [stroke])
        store.select([stroke.id])
    }
}

/// Kalem noktalarından yol (Android `PenPath`); tuval önizlemesi de bunu kullanır.
enum PenPath {
    static func segmentAt(_ anchors: [PenAnchor], _ p: P, _ tolerance: Float) -> Int? {
        var best: Int?
        var bestD = tolerance
        guard anchors.count >= 2 else { return nil }
        for i in 0..<(anchors.count - 1) {
            var b = anchors[i + 1]; b.bend = nil
            let pts = sampleWithNodes([anchors[i], b], closed: false).0
            for k in 1..<max(pts.count, 1) {
                let d = distanceToLine(p, pts[k - 1], pts[k], segment: true)
                if d < bestD { bestD = d; best = i }
            }
        }
        return best
    }

    static func distanceToLine(_ p: P, _ a: P, _ b: P, segment: Bool = false) -> Float {
        let ab = b - a
        let len2 = ab.x * ab.x + ab.y * ab.y
        if len2 < 1e-6 { return (p - (a)).distance }
        var t = ((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2
        if segment { t = t.coerceIn(0, 1) }
        return (p - (a + ab * t)).distance
    }

    static func sample(_ anchors: [PenAnchor], closed: Bool) -> [P] { sampleWithNodes(anchors, closed: closed).0 }

    static func sampleWithNodes(_ anchors: [PenAnchor], closed: Bool) -> ([P], [Int]) {
        if anchors.count < 2 { return (anchors.map(\.point), Array(anchors.indices)) }
        var out: [P] = [anchors[0].point]
        var nodes = [0]
        let segments = closed ? anchors.count : anchors.count - 1
        let n = 24
        for i in 0..<segments {
            let a = anchors[i], b = anchors[(i + 1) % anchors.count]
            let c1 = a.handle ?? a.point
            let c2 = b.handle.map { b.point * 2 - $0 } ?? b.point
            if let q = a.bend {
                for k in 1...n {
                    let t = Float(k) / Float(n), m = 1 - t
                    out.append(a.point * (m * m) + q * (2 * m * t) + b.point * (t * t))
                }
            } else if a.handle == nil && b.handle == nil {
                out.append(b.point)
            } else {
                for k in 1...n {
                    let t = Float(k) / Float(n), m = 1 - t
                    out.append(a.point * (m * m * m) + c1 * (3 * m * m * t) + c2 * (3 * m * t * t) + b.point * (t * t * t))
                }
            }
            nodes.append(out.count - 1)
        }
        if closed && out.count > 2 && (out[out.count - 1] - (out[0])).distance < 0.5 { out.removeLast() }
        var seen = Set<Int>()
        return (out, nodes.filter { $0 < out.count && seen.insert($0).inserted })
    }
}
