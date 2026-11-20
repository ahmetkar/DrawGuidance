import Foundation

/// v56 2C-1 canlı yayının fark paketi (Android `LiveDiff` ile aynı kural). `apply(prev, diff(prev, next)) == next`.
enum LiveDiff {
    static let FULL_EVERY = 20

    struct Diff: Equatable {
        /// Eklenen ya da değişen çizgiler (yeni sırayla).
        var upserts: [Stroke]
        var removed: [String]
        /// Kalan çizgilerin göreli sırası değiştiyse yeni sayfanın bütün kimlikleri.
        var order: [String]?
        var isEmpty: Bool { upserts.isEmpty && removed.isEmpty && order == nil }
    }

    static func diff(_ prev: [Stroke], _ next: [Stroke]) -> Diff {
        var before: [String: Stroke] = [:]
        for s in prev { before[s.id] = s }
        let nextIds = Set(next.map(\.id))
        let upserts = next.filter { before[$0.id] != $0 }
        let removed = prev.filter { !nextIds.contains($0.id) }.map(\.id)
        let plain = apply(prev, Diff(upserts: upserts, removed: removed, order: nil))
        return Diff(upserts: upserts, removed: removed, order: plain.map(\.id) == next.map(\.id) ? nil : next.map(\.id))
    }

    static func apply(_ page: [Stroke], _ d: Diff) -> [Stroke] {
        let removed = Set(d.removed)
        var changed: [String: Stroke] = [:]
        for s in d.upserts { changed[s.id] = s }
        let kept = page.filter { !removed.contains($0.id) }.map { changed[$0.id] ?? $0 }
        let known = Set(kept.map(\.id))
        let merged = kept + d.upserts.filter { !known.contains($0.id) }
        guard let order = d.order else { return merged }
        var byId: [String: Stroke] = [:]
        for s in merged { byId[s.id] = s }
        let listed = Set(order)
        return order.compactMap { byId[$0] } + merged.filter { !listed.contains($0.id) }
    }

    /// Fark paketi yeterli mi (Android `LiveDiff.changedPage`): değişen tek sayfa, -1 değişiklik yok, nil tam paket.
    static func changedPage(_ prev: CanvasUi, _ next: CanvasUi) -> Int? {
        if prev.localId != next.localId || prev.name != next.name || prev.width != next.width || prev.height != next.height ||
            prev.backgroundColorHex != next.backgroundColorHex || prev.kind != next.kind || prev.currentPage != next.currentPage ||
            prev.guide != next.guide || prev.layers != next.layers || prev.background != next.background ||
            prev.pageBackgrounds != next.pageBackgrounds || prev.pageSettings != next.pageSettings || prev.pages.count != next.pages.count {
            return nil
        }
        var changed = -1
        for i in next.pages.indices where prev.pages[i] != next.pages[i] {
            if changed >= 0 { return nil }
            changed = i
        }
        return changed
    }
}
