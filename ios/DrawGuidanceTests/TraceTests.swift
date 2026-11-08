import XCTest
@testable import DrawGuidance

/// Resimden çizim: renkli vektörleştirme ve kenar/orta çizgi izleme (sonuçlar Android ile karşılaştırıldı).
final class TraceTests: XCTestCase {
    private func lineart(_ w: Int, _ h: Int) -> [UInt32] {
        var p = [UInt32](repeating: 0xFFFF_FFFF, count: w * h)
        for y in 0..<h {
            for x in 0..<w {
                let dx = Double(x) - Double(w) * 0.4, dy = Double(y) - Double(h) * 0.5
                let d = (dx * dx + dy * dy).squareRoot()
                let ink = abs(d - Double(h) * 0.3) < 1.6 || (abs(Double(x) - Double(w) * 0.8) < 1.5 && y > 15 && y < h - 15)
                if ink { p[y * w + x] = 0xFF20_2830 }
            }
        }
        return p
    }

    func testVectorizerPalettesAndPaths() throws {
        let w = 120, h = 80
        var px = [UInt32](repeating: 0xFF3060C0, count: w * h)
        for y in 20..<60 { for x in 30..<90 { px[y * w + x] = 0xFFE0C020 } }
        let r = try Vectorizer.trace(px, w, h)
        XCTAssertEqual(Set(r.palette), ["#3060C0", "#E0C020"])
        XCTAssertEqual(r.paths.count, 2)
        XCTAssertEqual(r.paths[0].fill, "#3060C0")  // büyük parça önce (ressam sırası)
        XCTAssertTrue(r.paths.allSatisfy { $0.closed && $0.points.allSatisfy { $0.x >= 0 && $0.x <= 1 && $0.y >= 0 && $0.y <= 1 } })
    }

    func testEdgeTracerFindsCenterlines() throws {
        let r = try EdgeTracer.trace(lineart(320, 220), 320, 220)
        XCTAssertEqual(r.mode, .CENTERLINE)
        XCTAssertGreaterThanOrEqual(r.paths.count, 2)
        XCTAssertTrue(r.paths.contains { $0.closed })  // daire kapalı kalır
    }

    func testCancel() {
        XCTAssertThrowsError(try Vectorizer.trace([UInt32](repeating: 0xFF000000, count: 100), 10, 10, cancel: { true }))
    }
}
