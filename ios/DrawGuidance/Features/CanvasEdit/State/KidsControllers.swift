import Foundation

/// v45 boya fırçasının süren darbesi (Android `BrushLive`).
struct BrushLive {
    var points: [P]
    let region: KidsBrush.Region?
    let colorHex: String
    let sizePx: Float
}

/// v45 çocuk modunda boya fırçası (Android `KidsBrushController`): parmak inince alan bulunur, kalkınca darbe kaydedilir.
@MainActor
final class KidsBrushController: ObservableObject {
    private let regionAt: (P) -> KidsBrush.Region?
    private let color: () -> String
    private let size: () -> Float
    private let commit: (KidsBrush.Region?, [P], Float, String) -> Bool

    @Published private(set) var live: BrushLive?

    init(regionAt: @escaping (P) -> KidsBrush.Region?, color: @escaping () -> String, size: @escaping () -> Float,
         commit: @escaping (KidsBrush.Region?, [P], Float, String) -> Bool) {
        self.regionAt = regionAt; self.color = color; self.size = size; self.commit = commit
    }

    func begin(_ point: P) { live = BrushLive(points: [point], region: regionAt(point), colorHex: color(), sizePx: size()) }

    func move(_ point: P) {
        guard var c = live, c.points.last != point else { return }
        c.points.append(point)
        live = c
    }

    @discardableResult
    func end(_ point: P? = nil) -> Bool {
        guard let c = live else { return false }
        live = nil
        var points = c.points
        if let point, points.last != point { points.append(point) }
        return commit(c.region, points, c.sizePx, c.colorHex)
    }

    func cancel() { live = nil }
}

/// v37 çocuk modunda kova imleci (Android `PaintCursor`).
struct PaintCursor: Equatable {
    let point: P
    var target: PaintTarget?
    let hold: Int
    var color: String? = nil
}

/// v37 kova imlecinin durumu (Android `KidsPaintController`): imleç hemen yer değiştirir; altındaki alan arka planda
/// bulunur (yalnız en son istenen yer hesaplanır).
@MainActor
final class KidsPaintController: ObservableObject {
    private let target: @MainActor (P) -> PaintTarget?
    @Published private(set) var cursor: PaintCursor?
    private var pending: P?
    private var running = false

    init(target: @escaping @MainActor (P) -> PaintTarget?) { self.target = target }

    func hover(_ point: P?, hold: Int, color: String? = nil) {
        guard let point else { cursor = nil; return }
        let prev = cursor
        let keep: PaintTarget? = prev?.target.flatMap { t in t.region.map { RegionFill.contains($0, point) } == true ? t : nil }
        cursor = PaintCursor(point: point, target: keep ?? prev?.target, hold: hold, color: color)
        if keep == nil { request(point) }
    }

    func refresh() { if let c = cursor { request(c.point) } }

    private func request(_ point: P) {
        pending = point
        if running { return }
        running = true
        Task { await drain() }
    }

    private func drain() async {
        while let p = pending {
            pending = nil
            // Ana iş parçacığında (şekil denetleyicisi); peş peşe istekler birleşsin diye önce sıra verilir
            await Task.yield()
            if pending != nil { continue }
            let t = target(p)
            if var c = cursor, c.point == p || (t?.region.map { RegionFill.contains($0, c.point) } ?? false) {
                c.target = t
                cursor = c
            }
        }
        running = false
    }
}
