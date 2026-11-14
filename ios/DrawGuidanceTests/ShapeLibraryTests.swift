import XCTest
@testable import DrawGuidance

final class ShapeLibraryTests: XCTestCase {
    func testBundledCatalogLoads() throws {
        let lib = ShapeLibrary(bundle: Bundle(for: CanvasStore.self))
        XCTAssertEqual(lib.bundledCategories.count, 44)
        XCTAssertEqual(lib.bundledCategories.reduce(0) { $0 + $1.shapes.count }, 525)
        XCTAssertEqual(lib.bundledCategories.first?.id, "sci_physics")
        let cat = try XCTUnwrap(lib.find("lib.cat") ?? lib.bundledCategories.flatMap(\.shapes).first)
        XCTAssertFalse(cat.paths.isEmpty)
        XCTAssertTrue(lib.forAudience(.KIDS).allSatisfy { $0.audience == .KIDS })
        XCTAssertFalse(lib.forAudience(.NOTES).contains { $0.audience == .KIDS || $0.audience == .ART })
    }

    func testBundledNetsLoad() throws {
        let url = try XCTUnwrap(Bundle(for: CanvasStore.self).url(forResource: "catalog-nets", withExtension: "bin"))
        let nets = try CatalogBinary.decodeModeNets(Data(contentsOf: url))
        XCTAssertEqual(nets.version, 43000)
        XCTAssertTrue(nets.items.contains { $0.mode == "ART" })
        XCTAssertTrue(nets.items.contains { $0.mode == "NOTES_TEXT" })
    }
}
