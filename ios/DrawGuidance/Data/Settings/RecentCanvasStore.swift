import Foundation

/// v30 son açılan tuvaller (Ana sayfa: "Kaldığın yerden devam et") (Android `RecentCanvasStore`).
final class RecentCanvasStore: ObservableObject {
    struct Entry: Equatable { let canvasId: String; let openedAt: Int64; let closedAt: Int64 }
    static let shared = RecentCanvasStore()
    private static let KEY = "recent_canvases.entries"
    private static let MAX = 20

    private let defaults: UserDefaults
    private let lock = NSLock()
    @Published private(set) var entries: [Entry] = []

    init(_ defaults: UserDefaults = .standard) {
        self.defaults = defaults
        entries = (defaults.string(forKey: Self.KEY) ?? "").split(separator: "\n").compactMap { line in
            let p = line.split(separator: "|")
            guard p.count == 3, let o = Int64(p[1]), let c = Int64(p[2]) else { return nil }
            return Entry(canvasId: String(p[0]), openedAt: o, closedAt: c)
        }
    }

    private static func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    private func write(_ next: [Entry]) {
        entries = next
        defaults.set(next.map { "\($0.canvasId)|\($0.openedAt)|\($0.closedAt)" }.joined(separator: "\n"), forKey: Self.KEY)
    }

    private func update(_ id: String, _ block: (Entry?) -> Entry) {
        lock.withLock {
            let current = entries
            write(Array(([block(current.first { $0.canvasId == id })] + current.filter { $0.canvasId != id }).prefix(Self.MAX)))
        }
    }

    func opened(_ id: String, now: Int64 = now()) { update(id) { Entry(canvasId: id, openedAt: now, closedAt: $0?.closedAt ?? 0) } }
    func closed(_ id: String, now: Int64 = now()) { update(id) { Entry(canvasId: id, openedAt: $0?.openedAt ?? now, closedAt: now) } }
    func remove(_ id: String) { lock.withLock { write(entries.filter { $0.canvasId != id }) } }
}
