import XCTest
@testable import DrawGuidance

/// v56 aşama 3A (Android `V56ClipboardTest` ile aynı): sayfalar arası kes, kopyala, yapıştır.
@MainActor
final class V56ClipboardTests: XCTestCase {
    private func line(_ id: String, x: Float = 10, group: String? = nil, layer: String? = nil, mask: ClipMask? = nil) -> Stroke {
        Stroke(id: id, points: [StrokePoint(x, 20), StrokePoint(x + 40, 20)], colorHex: "#FF0000", brushType: .INK_PEN, sizePx: 6,
               opacity: 1, order: 0, groupId: group, layerId: layer, mask: mask)
    }

    private var n = 0
    private func nid() -> String { n += 1; return "n\(n)" }

    func testCopyPasteGivesNewIdsAndKeepsGroups() {
        let page = [line("a", group: "g"), line("b", group: "g"), line("c")]
        let clip = ElementClip.collect(page, ["a", "b"])
        XCTAssertEqual(clip.map(\.id), ["a", "b"])
        let pasted = ElementClip.paste(clip, newId: nid, startOrder: 3)
        XCTAssertTrue(pasted.allSatisfy { $0.id != "a" && $0.id != "b" })
        XCTAssertEqual(Set(pasted.map(\.groupId)).count, 1)
        XCTAssertNotEqual(pasted[0].groupId, "g")
        XCTAssertEqual(pasted.map(\.order), [3, 4])
        XCTAssertEqual(pasted[0].points, page[0].points)
    }

    func testClipMaskSourceTravelsAndIsRemapped() {
        let r = V56Selection.rect(0, 0, 50, 50)
        let src = Stroke(id: "s", points: r, colorHex: "#000000", brushType: .LASSO_FILL, sizePx: 1, opacity: 1, order: 1, groupId: "g", hidden: true)
        let page = [line("a", group: "g", mask: ClipMask(points: r, source: "s")), src]
        let clip = ElementClip.collect(page, ["a"])
        XCTAssertEqual(clip.map(\.id), ["a", "s"])
        let pasted = ElementClip.paste(clip, newId: nid, startOrder: 0, dx: 5)
        XCTAssertEqual(pasted[0].mask?.source, pasted[1].id)
        XCTAssertEqual(pasted[0].mask!.points[0].x, 5, accuracy: 0.001)
    }

    func testPasteGoesToActiveLayerAndRemapsAssets() {
        let r = V56Selection.rect(0, 0, 10, 10)
        let img = Stroke(id: "i", points: r, colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 0, assetId: "A", layerId: "L2")
        let text = Stroke(id: "t", points: r, colorHex: "#000000", brushType: .TEXT, sizePx: 1, opacity: 1, order: 1,
                          text: TextSpec(content: "x", box: TextBox(imageId: BackgroundSpec.ASSET + "B")))
        XCTAssertEqual(ElementClip.assetIds([img, text]), ["A", "B"])
        let pasted = ElementClip.paste([img, text], newId: nid, startOrder: 0, assets: ["A": "A2", "B": "B2"])
        XCTAssertNil(pasted[0].layerId)
        XCTAssertEqual(pasted[0].assetId, "A2")
        XCTAssertEqual(pasted[1].text?.box?.imageId, BackgroundSpec.ASSET + "B2")
    }

    func testPlacement() {
        let b: [Float] = [10, 10, 50, 30]
        XCTAssertTrue(ElementClip.offset(b, pageW: 100, pageH: 100) == (0, 0))
        XCTAssertTrue(ElementClip.offset(b, pageW: 100, pageH: 100, repeat: 1) == (16, 16))
        XCTAssertTrue(ElementClip.offset([100, 100, 140, 120], pageW: 100, pageH: 100) == (50 - 120, 50 - 110))
        XCTAssertTrue(ElementClip.offset(b, pageW: 400, pageH: 100, visible: [200, 0, 300, 100]) == (250 - 30, 50 - 20))
        XCTAssertTrue(ElementClip.offset(b, pageW: 100, pageH: 100, anchor: P(100, 100)) == (70, 80))
    }

    func testClipboardRepeatRule() {
        let board = ElementClipboard()
        board.set([line("a")], sourceCanvas: "c1", sourcePage: 0, cut: false)
        XCTAssertEqual(board.nextRepeat(canvas: "c1", page: 0, anchorKey: ""), 1)
        XCTAssertEqual(board.nextRepeat(canvas: "c1", page: 0, anchorKey: ""), 2)
        XCTAssertEqual(board.nextRepeat(canvas: "c1", page: 1, anchorKey: ""), 0)
        board.set([line("a")], sourceCanvas: "c1", sourcePage: 0, cut: true)
        XCTAssertEqual(board.nextRepeat(canvas: "c1", page: 0, anchorKey: ""), 0)
    }
}
