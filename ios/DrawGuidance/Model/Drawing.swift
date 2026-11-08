import CoreGraphics
import Foundation

struct StrokePoint: Codable, Equatable {
    var x: Double
    var y: Double
    var cg: CGPoint { CGPoint(x: x, y: y) }
}

struct Stroke: Codable, Equatable, Identifiable {
    var id = UUID()
    var points: [StrokePoint]
    var colorHex: UInt32
    var width: Double
    var isEraser: Bool = false
}

struct CanvasDocument: Codable, Identifiable, Equatable {
    var id = UUID()
    var name: String
    var kind: CanvasKind
    var strokes: [Stroke] = []
    var updatedAt = Date()
}

/// Geri al / yinele yığını olan basit tuval durumu.
struct UndoableStrokes {
    private(set) var strokes: [Stroke] = []
    private var redoStack: [Stroke] = []

    init(_ strokes: [Stroke] = []) { self.strokes = strokes }

    var canUndo: Bool { !strokes.isEmpty }
    var canRedo: Bool { !redoStack.isEmpty }

    mutating func add(_ s: Stroke) {
        guard !s.points.isEmpty else { return }
        strokes.append(s)
        redoStack.removeAll()
    }

    mutating func undo() {
        guard let last = strokes.popLast() else { return }
        redoStack.append(last)
    }

    mutating func redo() {
        guard let last = redoStack.popLast() else { return }
        strokes.append(last)
    }
}
