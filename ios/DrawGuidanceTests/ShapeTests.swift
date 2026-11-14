import XCTest
@testable import DrawGuidance

final class ShapeTests: XCTestCase {
    func testCatalogAndIndex() {
        let cat = ShapeTemplates.catalog
        let idx = ShapeIndex.fromCatalog(cat)
        XCTAssertEqual(idx.templateCount, 766)
        XCTAssertEqual(idx.rowCount, 5362)
    }

    func testHeartFindsItself() throws {
        let t = try XCTUnwrap(ShapeTemplates.byKey("nesne.kalp"))
        let idx = ShapeIndex.fromCatalog(ShapeTemplates.catalog)
        let q = t.strokes.map { s in s.map { P($0.x * 300 + 50, $0.y * 300 + 80) } }
        let res = idx.search(q, q.count == 1 ? .SINGLE : .GROUP, limit: 4)
        XCTAssertEqual(res.first?.key, "nesne.kalp")
    }

    func testModeScopes() {
        let idx = ShapeIndex.fromCatalog(ShapeTemplates.catalog)
        let models = ShapeModeModels(nil)
        for m in ShapeMode.allCases { XCTAssertFalse(models.scope(m, idx).keys.isEmpty, m.rawValue) }
    }
}
