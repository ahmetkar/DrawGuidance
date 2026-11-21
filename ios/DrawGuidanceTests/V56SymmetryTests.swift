import XCTest
@testable import DrawGuidance

/// v56 aşama 8: simetri ve Desen yap (Android V56SymmetryRulesTest ile aynı).
final class V56SymmetryTests: XCTestCase {
    private func eq(_ a: (Float, Float), _ x: Float, _ y: Float) -> Bool { abs(a.0 - x) < 0.01 && abs(a.1 - y) < 0.01 }

    func testTransforms() {
        let v = SymmetrySpec(kind: .VERTICAL, cx: 100, cy: 100)
        let t = V56Symmetry.transforms(v)
        XCTAssertEqual(t.count, 1)
        XCTAssertTrue(eq(t[0].map(100, 100, 130, 50), 70, 50))
        var h = v; h.kind = .HORIZONTAL
        XCTAssertTrue(eq(V56Symmetry.transforms(h)[0].map(100, 100, 130, 50), 130, 150))
        var q = v; q.kind = .QUAD
        let qt = V56Symmetry.transforms(q)
        XCTAssertEqual(qt.count, 3)
        XCTAssertTrue(eq(qt[2].map(100, 100, 130, 50), 70, 150))
        var r = v; r.kind = .RADIAL; r.arms = 4
        let rt = V56Symmetry.transforms(r)
        XCTAssertEqual(rt.count, 3)
        XCTAssertTrue(eq(rt[0].map(100, 100, 110, 100), 100, 110))
        r.arms = 40
        XCTAssertEqual(V56Symmetry.transforms(r).count, 15)
    }

    func testCopiesAndAxes() {
        let s = Stroke(id: "s", points: [StrokePoint(110, 100), StrokePoint(120, 90)], colorHex: "#000000", brushType: .INK_PEN, sizePx: 4, opacity: 1, order: 3)
        var n = 0
        let c = V56Symmetry.copies(s, SymmetrySpec(kind: .RADIAL, arms: 6, cx: 100, cy: 100)) { defer { n += 1 }; return "c\(n)" }
        XCTAssertEqual(c.map(\.id), ["c0", "c1", "c2", "c3", "c4"])
        XCTAssertTrue(c.allSatisfy { $0.points.count == 2 && $0.colorHex == s.colorHex })
        XCTAssertEqual(V56Symmetry.axes(SymmetrySpec(kind: .VERTICAL, cx: 50, cy: 50), 100).count, 1)
        XCTAssertEqual(V56Symmetry.axes(SymmetrySpec(kind: .QUAD, cx: 50, cy: 50), 100).count, 2)
        let a = V56Symmetry.axes(SymmetrySpec(kind: .RADIAL, arms: 6, cx: 50, cy: 50), 100)
        XCTAssertEqual(a.count, 6); XCTAssertEqual(a[0][0], 50, accuracy: 0.01)
        let st = V56Symmetry.start(.RADIAL, 800, 600)
        XCTAssertEqual(st.arms, 6); XCTAssertEqual(st.cx, 400); XCTAssertEqual(st.cy, 300)
    }

    func testPatternAndCodable() throws {
        let s = Stroke(id: "s", points: [StrokePoint(0, 0), StrokePoint(80, 80)], colorHex: "#000000", brushType: .INK_PEN, sizePx: 4, opacity: 1, order: 0)
        let b: [Float] = [0, 0, 80, 80]
        XCTAssertEqual(V56Symmetry.patternCount(b, 500, 500, 20), 35)
        var n = 0
        let out = V56Symmetry.pattern([s], b, 500, 500, 20) { n += 1; return "p\(n)" }
        XCTAssertEqual(out.count, 35)
        XCTAssertEqual(Set(out.map(\.groupId)).count, 1)
        XCTAssertEqual(V56Symmetry.pattern([s], b, 5000, 5000, 0, maxCopies: 10) { "x" }.count, 10)
        XCTAssertTrue(V56Symmetry.pattern([], b, 500, 500, 0) { "x" }.isEmpty)
        var g = GuideSpec(); g.symmetry = SymmetrySpec(kind: .QUAD, cx: 10, cy: 20, live: false)
        let back = try JSONDecoder().decode(GuideSpec.self, from: JSONEncoder().encode(g))
        XCTAssertEqual(back.symmetry, g.symmetry)
        XCTAssertFalse(back.isEmpty)
        let plain = String(data: try JSONEncoder().encode(GuideSpec()), encoding: .utf8)!
        XCTAssertFalse(plain.contains("symmetry"))
    }
}
