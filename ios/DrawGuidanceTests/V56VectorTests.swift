import XCTest
@testable import DrawGuidance

/// v56 aşama 8A: bağlı kopya, tekrar, opaklık maskesi, görünüm kaydı (Android V56VectorRulesTest ile aynı).
final class V56VectorTests: XCTestCase {
    private var n = 0
    private func id() -> String { n += 1; return "n\(n)" }
    private func line(_ id: String, _ x: Float = 0, _ y: Float = 0) -> Stroke {
        Stroke(id: id, points: [StrokePoint(x, y), StrokePoint(x + 10, y + 10)], colorHex: "#000000", brushType: .INK_PEN, sizePx: 4, opacity: 1, order: 0)
    }

    func testCloneFollowsSource() {
        let withClone = V56Vector.clone([line("a")], ["a"], 50, 0, newId: id)
        XCTAssertEqual(withClone.count, 2)
        let clone = withClone[1]
        XCTAssertEqual(clone.cloneOf?.source, "a")
        XCTAssertEqual(clone.points[0].x, 50, accuracy: 1e-3)
        let moved = withClone.map { s -> Stroke in
            guard s.id == "a" else { return s }
            var m = V56Arrange.shift(s, 0, 20); m.colorHex = "#FF0000"; return m
        }
        let synced = V56Vector.sync(withClone, moved)
        XCTAssertEqual(synced[1].points[0].y, 20, accuracy: 1e-3)
        XCTAssertEqual(synced[1].colorHex, "#FF0000")
        let cloneMoved = synced.map { $0.id == clone.id ? V56Arrange.shift($0, 100, 0) : $0 }
        let again = V56Vector.sync(cloneMoved, cloneMoved.map { s in if s.id == "a" { var c = s; c.sizePx = 9; return c }; return s })
        XCTAssertEqual(again[1].points[0].x, 150, accuracy: 1e-3)
        XCTAssertEqual(again[1].sizePx, 9, accuracy: 1e-3)
        XCTAssertNil(V56Vector.unlink(again, [clone.id])[1].cloneOf)
    }

    func testRepeatKinds() {
        let src = line("a", 100, 100)
        let (lin, c1) = V56Vector.repeat([src], ["a"], RepeatSpec(kind: .LINEAR, count: 3, dx: 30), newId: id)
        XCTAssertEqual(c1, 3)
        XCTAssertEqual(lin.dropFirst().map { $0.points[0].x }, [130, 160, 190])
        XCTAssertNotNil(lin[0].repeat)
        let (grid, c2) = V56Vector.repeat(lin, ["a"], RepeatSpec(kind: .GRID, cols: 2, rows: 2, gapX: 0, gapY: 0), newId: id)
        XCTAssertEqual(c2, 3); XCTAssertEqual(grid.count, 4)
        let (rad, c3) = V56Vector.repeat([src], ["a"], RepeatSpec(kind: .RADIAL, count: 4, radius: 0), newId: id)
        XCTAssertEqual(c3, 3)
        XCTAssertEqual(rad[2].points[1].x, 100, accuracy: 0.01)
        let (mir, _) = V56Vector.repeat([src], ["a"], RepeatSpec(kind: .MIRROR, gapX: 0), newId: id)
        XCTAssertEqual(mir[1].points[0].x, 120, accuracy: 0.01)
        let (step, c5) = V56Vector.repeat([src], ["a"], RepeatSpec(kind: .STEP, count: 2, dx: 10, dy: 0, angle: 0, scale: 2), newId: id)
        XCTAssertEqual(c5, 2)
        XCTAssertEqual(step[2].sizePx, 16, accuracy: 0.01)
        XCTAssertEqual(V56Vector.repeatOf(step, [step[1].id])?.kind, .STEP)
        let removed = V56Vector.removeRepeat(step, [step[2].id])
        XCTAssertEqual(removed.count, 1); XCTAssertNil(removed[0].repeat)
        let changed = lin.map { s in if s.id == "a" { var c = s; c.colorHex = "#00FF00"; return c }; return s }
        XCTAssertTrue(V56Vector.sync(lin, changed).allSatisfy { $0.colorHex == "#00FF00" })
    }

    func testOpacityMaskAndCodec() throws {
        let a = line("a"); var m = line("m"); m.order = 1; m.colorHex = "#FFFFFF"
        let masked = V56Vector.makeOpacityMask([a, m], ["a", "m"])
        XCTAssertEqual(masked.count, 1)
        XCTAssertEqual(masked[0].style2?.opacityMask?.first?.id, "m")
        XCTAssertEqual(V56Arrange.shift(masked[0], 5, 0).style2!.opacityMask![0].points[0].x, 5, accuracy: 1e-3)
        let released = V56Vector.releaseOpacityMask(masked, ["a"])
        XCTAssertEqual(released.map(\.id), ["a", "m"])
        XCTAssertNil(released[0].style2)

        var style = VectorStyle()
        style.cap = .BUTT; style.arrowEnd = .TRIANGLE; style.dash = [6, 3]; style.blend = .MULTIPLY
        style.strokes = [PaintLayer(colorHex: "#FFFFFF", width: 8)]; style.opacityMask = [m]
        var shape = Stroke(id: "s", points: [StrokePoint(0, 0), StrokePoint(50, 0)], colorHex: "#000000", brushType: .SHAPE, sizePx: 2, opacity: 1, order: 0,
                           shape: ShapeSpec(kind: "line", subpaths: [SubPath(count: 2)]))
        shape.style2 = style; shape.cloneOf = CloneLink(source: "x", tx: 3); shape.repeat = RepeatSpec(kind: .GRID)
        let back = try ContentCodec.decodePages(ContentCodec.encode([shape]))[0][0]
        XCTAssertEqual(back.style2, shape.style2)
        XCTAssertEqual(back.cloneOf, shape.cloneOf)
        XCTAssertEqual(back.repeat, shape.repeat)
        let head = V56Vector.arrowHead(.TRIANGLE, 10, 0, 1, 0, 4)
        XCTAssertEqual(head[1].0, 10, accuracy: 1e-3)
        let c = V56Vector.freeGradientColor([GradientPoint(x: 0, y: 0, colorHex: "#FF0000"), GradientPoint(x: 1, y: 1, colorHex: "#0000FF")], 0, 0, parse: ArgbColor.hex)
        XCTAssertEqual(c, 0xFFFF0000)
    }
}
