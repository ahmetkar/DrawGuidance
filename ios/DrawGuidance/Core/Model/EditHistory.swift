import Foundation

/// Belgenin geri alınabilir durumu.
struct DocumentState: Equatable {
    var pages: [[Stroke]]
    var width: Int
    var height: Int
    var background: String
    var page: Int
    var layers: [Layer] = [Layer.Default]
    var pageBackgrounds: [PageBackground?] = []
    var sharedBackground: BackgroundSpec? = nil
    var sharedBackgroundFull = false
}

/// Geri al / yinele geçmişi; aynı anahtarla kısa aralıklarla gelen değişiklikler birleşir.
final class EditHistory<S> {
    private final class Entry {
        let before: S
        var after: S
        let key: String?
        var at: Int64
        init(before: S, after: S, key: String?, at: Int64) { self.before = before; self.after = after; self.key = key; self.at = at }
    }

    private let maxSize: Int
    private let coalesceWindowMs: Int64
    private var undoStack: [Entry] = []
    private var redoStack: [Entry] = []
    private var coalescing = true

    init(maxSize: Int = 100, coalesceWindowMs: Int64 = 2_500) {
        self.maxSize = maxSize
        self.coalesceWindowMs = coalesceWindowMs
    }

    var canUndo: Bool { !undoStack.isEmpty }
    var canRedo: Bool { !redoStack.isEmpty }
    var undoSize: Int { undoStack.count }
    var redoSize: Int { redoStack.count }

    func record(before: S, after: S, key: String? = nil, now: Int64 = 0) {
        redoStack.removeAll()
        if coalescing, let key, let top = undoStack.last, top.key == key, now - top.at <= coalesceWindowMs {
            top.after = after
            top.at = now
        } else {
            undoStack.append(Entry(before: before, after: after, key: key, at: now))
            while undoStack.count > maxSize { undoStack.removeFirst() }
        }
        coalescing = true
    }

    func undo() -> S? {
        guard let entry = undoStack.popLast() else { return nil }
        redoStack.append(entry)
        coalescing = false
        return entry.before
    }

    func redo() -> S? {
        guard let entry = redoStack.popLast() else { return nil }
        undoStack.append(entry)
        coalescing = false
        return entry.after
    }

    func breakCoalescing() { coalescing = false }

    func clear() {
        undoStack.removeAll()
        redoStack.removeAll()
    }
}
