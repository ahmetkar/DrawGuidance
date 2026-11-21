import XCTest
@testable import DrawGuidance

/// v56 aşama 6A: kaydır/eğ, referans noktası, her birini dönüştür, tekrarla, hizala/dağıt, vektör efektleri (Android
/// V56ArrangeRulesTest ile aynı).
final class V56ArrangeTests: XCTestCase {
    private func box(_ id: String, _ l: Float, _ t: Float, _ r: Float, _ b: Float, group: String? = nil) -> Stroke {
        Stroke(id: id, points: [StrokePoint(l, t), StrokePoint(r, t), StrokePoint(r, b), StrokePoint(l, b)], colorHex: "#000000",
               brushType: .INK_PEN, sizePx: 4, opacity: 1, order: 0, groupId: group)
    }

    func testShearKeepsParallelogramAndIsAffine() {
        let s = TransformOps.setKind(TransformState.start(0, 0, 100, 50), .SHEAR)
        let sh = TransformOps.dragEdge(s, 0, 30, 0)
        XCTAssertEqual(sh.quad[0].x, 30, accuracy: 1e-3); XCTAssertEqual(sh.quad[3].x, 0, accuracy: 1e-3)
        XCTAssertEqual(TransformOps.relative(sh)!.b, -0.6, accuracy: 1e-3)
        XCTAssertNotNil(TransformOps.relative(TransformOps.dragCorner(sh, 2, 200, 100)))
        XCTAssertNil(TransformOps.relative(TransformOps.dragCorner(TransformOps.setKind(s, .DISTORT), 2, 150, 90)))
    }

    func testPivotRotationKeepsPivotFixed() {
        let s = TransformState.start(0, 0, 100, 100)
        let tl = TransformOps.anchorPoint(s.quad, 0)
        XCTAssertEqual(tl.0, 0); XCTAssertEqual(tl.1, 0)
        XCTAssertEqual(TransformOps.anchorPoint(s.quad, 4).0, 50)
        let r = TransformOps.rotate(s, 90, pivot: tl)
        XCTAssertEqual(r.quad[0].x, 0, accuracy: 1e-3); XCTAssertEqual(r.quad[0].y, 0, accuracy: 1e-3)
        XCTAssertEqual(TransformOps.flip(s, horizontal: true, pivot: (100, 0)).quad[0].x, 200, accuracy: 1e-3)
    }

    func testTransformEachScalesAroundOwnCenters() {
        let strokes = [box("a", 0, 0, 10, 10), box("b", 100, 0, 110, 10)]
        var twice = TransformState.start(0, 0, 110, 10, kind: .FREE)
        twice.quad = twice.box.map { StrokePoint(($0.x - 55) * 2 + 55, ($0.y - 5) * 2 + 5) }
        let out = TransformOps.applyEach(strokes, ["a", "b"], twice).strokes
        XCTAssertEqual(out[0].points[0].x, -5, accuracy: 1e-3); XCTAssertEqual(out[0].points[1].x, 15, accuracy: 1e-3)
        XCTAssertEqual(out[1].points[0].x, 95, accuracy: 1e-3); XCTAssertEqual(out[1].points[1].x, 115, accuracy: 1e-3)
        let again = TransformOps.applyRelative(strokes, ["a"], TransformOps.relative(twice)!, each: false)
        XCTAssertEqual(again[0].points[0].x, -5, accuracy: 1e-3)
        XCTAssertEqual(again[1], strokes[1])
    }

    func testAlignAndDistributeCoordinates() {
        let strokes = [box("a", 0, 0, 10, 10), box("b", 30, 20, 50, 30), box("c", 90, 5, 100, 15, group: "g"), box("d", 95, 40, 100, 50, group: "g")]
        let units = V56Arrange.units(strokes, ["a", "b", "c", "d"])
        XCTAssertEqual(units.count, 3)
        let left = V56Arrange.applyOffsets(strokes, units, V56Arrange.align(units, .LEFT, .SELECTION, paperW: 500, paperH: 500))
        XCTAssertEqual(left[1].points.map(\.x).min()!, 0, accuracy: 1e-3)
        XCTAssertEqual(left[2].points[0].x - strokes[2].points[0].x, left[3].points[0].x - strokes[3].points[0].x, accuracy: 1e-3)
        XCTAssertEqual(V56Arrange.align(units, .BOTTOM, .PAPER, paperW: 500, paperH: 500)[0].1, 490, accuracy: 1e-3)
        let key = V56Arrange.align(units, .TOP, .KEY, paperW: 500, paperH: 500, keyId: "b")
        XCTAssertEqual(key[1].0, 0); XCTAssertEqual(key[1].1, 0); XCTAssertEqual(key[0].1, 20, accuracy: 1e-3)
        XCTAssertEqual(V56Arrange.distribute(units, .HORIZONTAL)[1].0, 10, accuracy: 1e-3)
        XCTAssertEqual(V56Arrange.distribute(units, .SPACE_H)[1].0, 10, accuracy: 1e-3)
        XCTAssertTrue(V56Arrange.distribute(Array(units.prefix(2)), .VERTICAL).allSatisfy { $0.0 == 0 && $0.1 == 0 })
    }

    func testVectorEffectsDensifyAndMove() {
        let sq = Stroke(id: "s", points: [StrokePoint(0, 0), StrokePoint(100, 0), StrokePoint(100, 100), StrokePoint(0, 100)], colorHex: "#000000",
                        brushType: .SHAPE, sizePx: 2, opacity: 1, order: 0, shape: ShapeSpec(kind: "rect", subpaths: [SubPath(count: 4, closed: true)]))
        let pucker = V56Arrange.effect([sq], ["s"], .PUCKER, 0.3)[0]
        XCTAssertGreaterThan(pucker.points.count, 4)
        XCTAssertEqual(pucker.points.count, pucker.shape!.subpaths[0].count)
        XCTAssertGreaterThan(hypot(pucker.points[0].x - 50, pucker.points[0].y - 50), hypot(50, 50))
        let bloat = V56Arrange.effect([sq], ["s"], .BLOAT, 0.3)[0]
        XCTAssertLessThan(hypot(bloat.points[0].x - 50, bloat.points[0].y - 50), hypot(50, 50))
        XCTAssertNotEqual(V56Arrange.effect([sq], ["s"], .TWIRL, 90)[0].points, sq.points)
        var img = sq; img.id = "i"; img.brushType = .IMAGE; img.shape = nil
        XCTAssertEqual(V56Arrange.effect([img], ["i"], .TWIRL, 90)[0], img)
        XCTAssertFalse(V56Flags.enabled(.ADV_EFFECTS, .SIMPLE_ART))
    }
}
