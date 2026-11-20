import XCTest
@testable import DrawGuidance

/// v56 aşama 3 (Android `V56SelectionTest` ile aynı): seçim alanı (maske), genişlet/daralt, kement dolgu, kırpma maskesi,
/// sihirli değnek.
final class V56SelectionTests: XCTestCase {
    private func line(_ id: String, color: String = "#FF0000", size: Float = 20, type: BrushType = .INK_PEN, mask: ClipMask? = nil) -> Stroke {
        Stroke(id: id, points: [StrokePoint(10, 50), StrokePoint(100, 50), StrokePoint(190, 50)], colorHex: color, brushType: type,
               sizePx: size, opacity: 1, order: 0, mask: mask)
    }

    private func alpha(_ strokes: [Stroke], _ x: Int, _ y: Int) -> Int {
        let img = StrokeRenderer.render(strokes, canvasWidth: 200, canvasHeight: 100, scale: 1, layers: [Layer.Default])
        let px = HarmonyRenderer.argbPixels(img, 200, 100)
        return Int((px[y * 200 + x] >> 24) & 0xFF)
    }

    private let left = V56Selection.rect(0, 0, 100, 100)
    private func rectShape(_ id: String, _ pts: [StrokePoint]) -> Stroke {
        Stroke(id: id, points: pts, colorHex: "#000000", brushType: .SHAPE, sizePx: 4, opacity: 1, order: 2,
               shape: ShapeSpec(kind: "rect", subpaths: [SubPath(count: 4, closed: true)]))
    }

    func testMaskClipsInsideOutsideAndInverted() {
        XCTAssertGreaterThan(alpha([line("a", mask: ClipMask(points: left))], 50, 50), 200)
        XCTAssertEqual(alpha([line("a", mask: ClipMask(points: left))], 150, 50), 0)
        XCTAssertEqual(alpha([line("a", mask: ClipMask(points: left, inverted: true))], 50, 50), 0)
        XCTAssertGreaterThan(alpha([line("a", mask: ClipMask(points: left, inverted: true))], 150, 50), 200)
        let soft = [line("a", mask: ClipMask(points: left, feather: 16))]
        XCTAssertGreaterThan(alpha(soft, 40, 50), 200)
        let edge = alpha(soft, 100, 50)
        XCTAssertTrue((40...215).contains(edge), "kenar yarı saydam: \(edge)")
        XCTAssertLessThan(alpha(soft, 130, 50), 20)
    }

    func testLassoFillPaintsInside() {
        let fill = V56Selection.lassoFill(id: "f", V56Selection.rect(20, 20, 80, 80), colorHex: "#00FF00", opacity: 1, order: 0)
        XCTAssertGreaterThan(alpha([fill], 50, 50), 250)
        XCTAssertEqual(alpha([fill], 150, 50), 0)
    }

    func testOffsetGrowsAndShrinks() {
        let sq = V56Selection.rect(10, 10, 50, 50)
        XCTAssertEqual(abs(V56Selection.signedArea(V56Selection.offset(sq, 5)!)), 2500, accuracy: 1)
        XCTAssertEqual(abs(V56Selection.signedArea(V56Selection.offset(sq, -5)!)), 900, accuracy: 1)
        XCTAssertEqual(abs(V56Selection.signedArea(V56Selection.offset(Array(sq.reversed()), 5)!)), 2500, accuracy: 1)
        XCTAssertNil(V56Selection.offset(sq, -30), "çok daralan alan")
    }

    func testClipMaskMakeAndRelease() {
        let shape = rectShape("s", left)
        let a = line("a"), b = line("b")
        XCTAssertTrue(V56Selection.canClip([a, b, shape]))
        XCTAssertFalse(V56Selection.canClip([shape, a]))
        let changed = V56Selection.clip([a, b, shape], groupId: "g")
        let byId = Dictionary(uniqueKeysWithValues: changed.map { ($0.id, $0) })
        XCTAssertTrue(byId["s"]!.hidden)
        XCTAssertEqual(byId["a"]!.mask?.source, "s")
        XCTAssertEqual(byId["b"]!.groupId, "g")
        let released = V56Selection.release([byId["a"]!, byId["b"]!, byId["s"]!], ["a"])
        XCTAssertTrue(released.allSatisfy { $0.mask == nil && !$0.hidden && $0.groupId == nil })
        XCTAssertEqual(alpha([byId["a"]!], 150, 50), 0)
    }

    func testMagicWandCriteria() {
        let t = line("t", color: "#FF0000", size: 20)
        let near = line("n", color: "#F01010", size: 22)
        let far = line("f", color: "#0000FF", size: 20)
        let marker = line("m", color: "#FF0000", size: 20, type: .MARKER)
        let all = [t, near, far, marker]
        func ids(_ c: V56Selection.WandCriterion, _ tol: Float) -> Set<String> { Set(V56Selection.similar(t, all, c, tol).map(\.id)) }
        XCTAssertEqual(ids(.COLOR, 0.1), ["t", "n", "m"])
        XCTAssertEqual(ids(.STROKE, 0.15), ["t", "n", "f"])
        XCTAssertEqual(ids(.APPEARANCE, 0.15), ["t", "n"])
        XCTAssertEqual(ids(.APPEARANCE, 0), ["t"])
    }

    func testAreaAppliesOnlyToNewInk() {
        let out = V56Selection.applyArea(["old"], [line("old"), line("new"), rectShape("s", left)], ClipMask(points: left))
        XCTAssertNil(out[0].mask)
        XCTAssertNotNil(out[1].mask)
        XCTAssertNil(out[2].mask, "nesneler kırpılmaz")
    }
}
