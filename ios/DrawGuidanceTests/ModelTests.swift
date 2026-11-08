import XCTest
@testable import DrawGuidance

final class ModelTests: XCTestCase {
    func testUndoRedo() {
        var h = UndoableStrokes()
        let s = Stroke(points: [StrokePoint(x: 1, y: 2)], colorHex: 0x000000FF, width: 3)
        h.add(s)
        XCTAssertTrue(h.canUndo)
        h.undo()
        XCTAssertEqual(h.strokes.count, 0)
        XCTAssertTrue(h.canRedo)
        h.redo()
        XCTAssertEqual(h.strokes, [s])
        XCTAssertFalse(h.canRedo)
    }

    func testEmptyStrokeIgnored() {
        var h = UndoableStrokes()
        h.add(Stroke(points: [], colorHex: 0, width: 1))
        XCTAssertFalse(h.canUndo)
    }

    func testSimpleKinds() {
        XCTAssertEqual(CanvasKind.allCases.filter(\.isSimple), [.simpleArt, .simpleNotes, .kids])
    }

    @MainActor
    func testLibraryPersists() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let lib = CanvasLibrary(directory: dir)
        var doc = lib.create(kind: .art, name: "Deneme")
        doc.strokes = [Stroke(points: [StrokePoint(x: 0, y: 0)], colorHex: 0xFF0000FF, width: 2)]
        lib.update(doc)
        let again = CanvasLibrary(directory: dir)
        XCTAssertEqual(again.canvases.first?.name, "Deneme")
        XCTAssertEqual(again.canvases.first?.strokes.count, 1)
    }
}
