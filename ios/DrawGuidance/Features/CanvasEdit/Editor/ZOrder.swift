import Foundation

/// v23 elemanların üst/alt sırası (aynı katmanda) (Android `ZOrder`).
enum ZOrder {
    static let FORWARD = 1
    static let BACKWARD = -1
    static let FRONT = 2
    static let BACK = -2

    static func reorder(_ strokes: [Stroke], _ selected: Set<String>, _ index: ElementIndex, _ direction: Int, _ layers: [Layer]) -> [Stroke] {
        let chosen = index.selectedElements(selected)
        if chosen.isEmpty { return strokes }
        var moving = Set<String>()
        for e in chosen { moving.formUnion(e.allIds) }
        var pos = [String: Int]()
        for (i, s) in strokes.enumerated() { pos[s.id] = i }
        let movingList = strokes.filter { moving.contains($0.id) }
        guard let lastMoving = movingList.last else { return strokes }
        let rest = strokes.filter { !moving.contains($0.id) }
        let layer = Layers.of(lastMoving, layers)
        var result: [Stroke]
        switch direction {
        case FRONT: result = rest + movingList
        case BACK: result = movingList + rest
        case FORWARD, BACKWARD:
            let others = index.elements.filter { e in
                guard let first = e.strokeIds.first, e.allIds.allSatisfy({ !moving.contains($0) }) else { return false }
                guard let p = pos[first], strokes.indices.contains(p) else { return false }
                return Layers.of(strokes[p], layers) == layer
            }
            func maxPos(_ e: Element) -> Int { e.allIds.map { pos[$0] ?? -1 }.max() ?? -1 }
            func minPos(_ e: Element) -> Int { e.allIds.map { pos[$0] ?? Int.max }.min() ?? Int.max }
            if direction == FORWARD {
                let top = movingList.map { pos[$0.id] ?? 0 }.max() ?? 0
                guard let next = others.filter({ maxPos($0) > top }).min(by: { maxPos($0) < maxPos($1) }),
                      let lastId = next.allIds.max(by: { (pos[$0] ?? -1) < (pos[$1] ?? -1) }) else { return strokes }
                let at = (rest.firstIndex { $0.id == lastId } ?? -1) + 1
                result = Array(rest[0..<at]) + movingList + Array(rest[at...])
            } else {
                let bottom = movingList.map { pos[$0.id] ?? 0 }.min() ?? 0
                guard let prev = others.filter({ minPos($0) < bottom }).max(by: { minPos($0) < minPos($1) }),
                      let firstId = prev.allIds.min(by: { (pos[$0] ?? Int.max) < (pos[$1] ?? Int.max) }) else { return strokes }
                let at = max(rest.firstIndex { $0.id == firstId } ?? 0, 0)
                result = Array(rest[0..<at]) + movingList + Array(rest[at...])
            }
        default: return strokes
        }
        let sorted = Layers.sort(result, layers)
        if sorted.map(\.id) == strokes.map(\.id) { return strokes }
        return ElementOps.reorder(sorted)
    }
}
