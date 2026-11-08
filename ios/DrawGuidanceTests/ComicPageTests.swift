import XCTest
@testable import DrawGuidance

/// Çizgi roman sayfa düzeni, şablonlar ve filtreler (değerler Android'den).
final class ComicPageTests: XCTestCase {
    func testTemplates() {
        XCTAssertEqual(ComicTemplates.all.count, 23)
        XCTAssertEqual(ComicTemplates.byId("six_grid")?.panelCount, 6)
        XCTAssertEqual(ComicTemplates.byId("romance")?.panelCount, 4)
        XCTAssertEqual(ComicTemplates.byId("horror")?.line, 1.6)
        XCTAssertEqual(ComicTemplates.inGroup(.MANGA).count, 3)
    }

    func testEncodeRoundTrip() {
        for t in ComicTemplates.all {
            let e = t.page.encode()
            XCTAssertEqual(PanelPage.decode(e)?.encode(), e, t.id)
        }
        XCTAssertEqual(ComicTemplates.byId("three_big")!.page.encode(), "P1|0.045|0.022|0|R[1.4:p0,1:C[1:p1,1:p2]]|")
        XCTAssertNil(PanelPage.decode("P2|x"))
        XCTAssertNil(PanelPage.decode("P1|0.04|0.02|0|R[1:p0"))
    }

    func testShapesAndEditing() {
        let p = ComicTemplates.byId("two_cols")!.page
        let s = p.shapes(1000, 1414)
        XCTAssertEqual(s.count, 2)
        XCTAssertEqual(s[0].bounds()[0], 45, accuracy: 0.01)
        XCTAssertTrue(s[0].contains(200, 700))
        XCTAssertFalse(s[0].contains(800, 700))
        let (q, id) = p.split(1, rows: true)!
        XCTAssertEqual(id, 2)
        XCTAssertEqual(q.leafIds(), [0, 1, 2])
        XCTAssertEqual(q.remove(2).encode(), p.encode())
        XCTAssertEqual(p.widthOf(0)!, 0.5, accuracy: 1e-6)
        XCTAssertEqual(p.setWidth(0, 0.7).widthOf(0)!, 0.7, accuracy: 1e-5)
        XCTAssertEqual(p.move(0, forward: true)!.leafIds(), [1, 0])
    }

    func testFiltersKeepAlpha() {
        let w = 20, h = 12
        let src = (0..<(w * h)).map { i -> UInt32 in 0x80000000 | UInt32((i * 37) & 0xFFFFFF) }
        for k in ComicFilter.allCases {
            let o = ComicFilters.apply(k, src, w, h)
            XCTAssertEqual(o.count, w * h)
            XCTAssertTrue(o.allSatisfy { $0 >> 24 == 0x80 }, "\(k)")
        }
    }
}
