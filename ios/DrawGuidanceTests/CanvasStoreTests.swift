import XCTest
@testable import DrawGuidance

final class CanvasStoreTests: XCTestCase {
    @MainActor
    func testPersistsCanvasWithAndroidContentFormat() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = CanvasStore(directory: dir)
        var c = store.create(kind: .NOTES, name: "Deneme")
        XCTAssertEqual(c.width, 1240)
        XCTAssertEqual(c.height, 1754)
        c.pages = [[Stroke(id: "a", points: [StrokePoint(1, 2)], colorHex: "#FF0000", brushType: .PENCIL, sizePx: 2, opacity: 1, order: 0)], []]
        store.save(c)
        let again = CanvasStore(directory: dir)
        let loaded = try XCTUnwrap(again.canvases.first)
        XCTAssertEqual(loaded.name, "Deneme")
        XCTAssertEqual(loaded.kind, .NOTES)
        XCTAssertEqual(loaded.pages, c.pages)
        let bin = try Data(contentsOf: dir.appendingPathComponent(c.localId).appendingPathComponent("content.bin"))
        XCTAssertEqual(bin.prefix(2), Data("DG".utf8))
    }

    func testCanvasKindRules() {
        XCTAssertEqual(CanvasKind.COMIC.base, .ART)
        XCTAssertEqual(CanvasKind.SIMPLE_NOTES.base, .NOTES)
        XCTAssertTrue(CanvasKind.COMIC.paged)
        XCTAssertEqual(CanvasKind.COMIC.maxPages, 1000)
        XCTAssertEqual(CanvasKind.KIDS.packMode(), "ART")
        XCTAssertEqual(CanvasKind.Simple, [.SIMPLE_ART, .NOTES, .KIDS])
        XCTAssertEqual(CanvasKind.Advanced, [.ART, .COMIC])
        XCTAssertTrue(CanvasKind.NOTES.simple)
    }

    /// v51 not alma tek mod: eski "SIMPLE_NOTES" kayıtları okunurken not almaya çevrilir.
    func testLegacySimpleNotesMigratesToNotes() throws {
        XCTAssertEqual(CanvasKind.parse("SIMPLE_NOTES"), .NOTES)
        let decoded = try JSONDecoder().decode([CanvasKind].self, from: Data(#"["SIMPLE_NOTES","ART"]"#.utf8))
        XCTAssertEqual(decoded, [.NOTES, .ART])
    }
}
