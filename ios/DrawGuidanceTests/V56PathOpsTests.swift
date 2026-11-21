import XCTest
@testable import DrawGuidance

/// v56 aşama 9C: yol işlemleri, bıçak, konturu yola çevir, efektler (Android V56PathOpsTest ile aynı beklentiler).
final class V56PathOpsTests: XCTestCase {
    private var n = 0
    private func id() -> String { defer { n += 1 }; return "n\(n)" }
    private func sq(_ id: String, _ x: Float, _ y: Float, _ s: Float, _ fill: String = "#FF0000") -> Stroke {
        Stroke(id: id, points: [StrokePoint(x, y), StrokePoint(x + s, y), StrokePoint(x + s, y + s), StrokePoint(x, y + s)], colorHex: fill,
               brushType: .SHAPE, sizePx: 2, opacity: 1, order: 0,
               shape: ShapeSpec(kind: "rect", subpaths: [SubPath(count: 4, closed: true)], style: ShapeStyle(strokeColorHex: "#000000", strokeWidth: 2, fillColorHex: fill)))
    }
    private func ring(_ x: Double, _ y: Double, _ s: Double) -> [V56Bool.P] { [.init(x, y), .init(x + s, y), .init(x + s, y + s), .init(x, y + s)] }
    private func absArea(_ r: [[V56Bool.P]]) -> Double { abs(r.reduce(0) { $0 + V56Bool.area($1) }) }

    func testBooleanAreas() {
        let a = [ring(0, 0, 10)], b = [ring(5, 5, 10)]
        XCTAssertEqual(absArea(V56Bool.op(a, b, .UNION)), 175, accuracy: 1e-6)
        XCTAssertEqual(absArea(V56Bool.op(a, b, .INTERSECT)), 25, accuracy: 1e-6)
        XCTAssertEqual(absArea(V56Bool.op(a, b, .SUBTRACT)), 75, accuracy: 1e-6)
        XCTAssertEqual(absArea(V56Bool.op(a, b, .EXCLUDE)), 150, accuracy: 1e-6)
        // Kenar paylaşan kareler tek dikdörtgen olur
        let u = V56Bool.op(a, [ring(10, 0, 10)], .UNION)
        XCTAssertEqual(u.count, 1); XCTAssertEqual(u[0].count, 4); XCTAssertEqual(absArea(u), 200, accuracy: 1e-6)
        // Delik: 400 - 100
        let hole = V56Bool.op([ring(0, 0, 20)], [ring(5, 5, 10)], .SUBTRACT)
        XCTAssertEqual(hole.count, 2); XCTAssertEqual(absArea(hole), 300, accuracy: 1e-6)
        XCTAssertTrue(V56Bool.op(a, [ring(50, 50, 5)], .INTERSECT).isEmpty)
    }

    func testPathOpsOnStrokes() {
        let strokes = [sq("a", 0, 0, 10, "#FF0000"), sq("b", 5, 5, 10, "#0000FF")]
        let ids: Set<String> = ["a", "b"]
        let unite = V56PathOps.apply(strokes, ids, .UNITE, newId: id)!
        XCTAssertEqual(unite.count, 1); XCTAssertEqual(unite[0].shape?.style.fillColorHex, "#0000FF")
        XCTAssertEqual(absArea(V56PathOps.rings(unite[0])), 175, accuracy: 1e-3)
        let minus = V56PathOps.apply(strokes, ids, .MINUS_FRONT, newId: id)!
        XCTAssertEqual(absArea(V56PathOps.rings(minus[0])), 75, accuracy: 1e-3)
        XCTAssertEqual(V56PathOps.apply(strokes, ids, .DIVIDE, newId: id)!.count, 3)
        let trim = V56PathOps.apply(strokes, ids, .TRIM, newId: id)!
        XCTAssertEqual(trim.count, 2)
        XCTAssertEqual(absArea(V56PathOps.rings(trim[0])), 75, accuracy: 1e-3)
        let crop = V56PathOps.apply(strokes, ids, .CROP, newId: id)!
        XCTAssertEqual(crop.count, 1); XCTAssertEqual(absArea(V56PathOps.rings(crop[0])), 25, accuracy: 1e-3)
        XCTAssertEqual(V56PathOps.apply([sq("a", 0, 0, 10), sq("b", 5, 5, 10)], ids, .MERGE, newId: id)!.count, 1)
        let outline = V56PathOps.apply(strokes, ids, .OUTLINE, newId: id)!
        XCTAssertEqual(outline.count, 3); XCTAssertTrue(outline.allSatisfy { $0.shape?.style.fillColorHex == nil })
        // Şekil oluşturucu: kesişimi solla birleştir, sağdakini sil
        let regions = V56PathOps.builderRegions(strokes)
        let inter = V56PathOps.regionAt(regions, 7, 7), left = V56PathOps.regionAt(regions, 2, 2), right = V56PathOps.regionAt(regions, 13, 13)
        let built = V56PathOps.build(strokes, ids, merge: [left, inter], remove: [right], newId: id)!
        XCTAssertEqual(built.count, 1); XCTAssertEqual(absArea(V56PathOps.rings(built[0])), 100, accuracy: 1e-3)
    }

    func testKnifeOutlineSeparateReverse() {
        let cut = V56PathOps.knife([sq("a", 0, 0, 10)], [], [StrokePoint(5, -5), StrokePoint(5, 15)], newId: id)!
        XCTAssertEqual(cut.count, 2)
        for s in cut { XCTAssertEqual(absArea(V56PathOps.rings(s)), 50, accuracy: 1) }
        let line = Stroke(id: "l", points: [StrokePoint(0, 0), StrokePoint(100, 0)], colorHex: "#00FF00", brushType: .INK_PEN, sizePx: 10, opacity: 1, order: 0)
        let out = V56PathOps.outlineStroke([line], ["l"], newId: id)!
        XCTAssertEqual(out.count, 1)
        XCTAssertEqual(out[0].shape?.style.fillColorHex, "#00FF00")
        XCTAssertGreaterThan(absArea(V56PathOps.rings(out[0])), 1000)
        var two = sq("t", 0, 0, 10)
        two.points += sq("x", 20, 0, 5).points
        two.shape!.subpaths = [SubPath(count: 4, closed: true), SubPath(count: 4, closed: true)]
        XCTAssertEqual(V56PathOps.separate([two], ["t"], newId: id)!.count, 2)
        XCTAssertEqual(V56PathOps.reversePaths([line], ["l"])![0].points[0].x, 100)
    }

    func testEffectsAreEditable() {
        let s = sq("a", 0, 0, 100)
        let rough = V56PathOps.withEffects(s, [VectorEffect(.ROUGHEN, size: 5, detail: 10)])
        XCTAssertGreaterThan(rough.points.count, 4)
        XCTAssertNotNil(rough.style2?.effectBase)
        XCTAssertEqual(rough.points.count, rough.shape!.subpaths.reduce(0) { $0 + $1.count })
        let off = V56PathOps.withEffects(s, [VectorEffect(.OFFSET, amount: 10)])
        XCTAssertEqual(absArea(V56PathOps.rings(off)), 120 * 120, accuracy: 1)
        let back = V56PathOps.withEffects(rough, [])
        XCTAssertEqual(back.points, s.points)
        XCTAssertNil(back.style2)
        XCTAssertGreaterThan(V56PathOps.withEffects(s, [VectorEffect(.BEND, amount: 0.5)]).points.count, 4)
        XCTAssertEqual(V56PathOps.noise(1, 5), V56PathOps.noise(1, 5))
        let env = V56PathOps.envelope([sq("a", 0, 0, 10), sq("e", 0, 0, 20)], ["a", "e"])!
        XCTAssertEqual(env.count, 1)
        XCTAssertEqual(env[0].points.map(\.x).max()!, 20, accuracy: 0.01)
    }

    func testBezierHandles() {
        let g = VectorEdit.graph(sq("a", 0, 0, 90))
        guard let node = g.nodes.first else { return XCTFail("düğüm yok") }
        let hs = VectorEdit.nodeHandles(g, node.id)
        XCTAssertEqual(hs.count, 2)
        for h in hs { XCTAssertEqual((h - node.point).distance, 30, accuracy: 0.5) }
        let (e, start) = VectorEdit.nodeEdges(g, node.id)[0]
        let far = VectorEdit.handleOf(e, atStart: !start)
        let curved = start ? VectorEdit.bezierEdge(g, e, node.point + P(0, 40), far) : VectorEdit.bezierEdge(g, e, far, node.point + P(0, 40))
        XCTAssertGreaterThan(curved.reduce(0) { $0 + $1.points.count }, g.paths.reduce(0) { $0 + $1.points.count })
    }
}
