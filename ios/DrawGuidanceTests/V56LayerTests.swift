import XCTest
@testable import DrawGuidance

/// v56 aşama 2 (Android `V56LayerCompositeTest` ile aynı): katman opaklığı, alttakine kırpma, alfa kilidi, önbellek kuralı.
final class V56LayerTests: XCTestCase {
    private let base = Layer.Default

    private func line(_ id: String, _ x0: Float, _ x1: Float, _ y: Float, layer: String? = nil, locked: Bool = false,
                      color: String = "#FF0000") -> Stroke {
        Stroke(id: id, points: [StrokePoint(x0, y), StrokePoint((x0 + x1) / 2, y), StrokePoint(x1, y)], colorHex: color,
               brushType: .INK_PEN, sizePx: 20, opacity: 1, order: 0, layerId: layer,
               params: locked ? BrushParams(alphaLocked: true) : nil)
    }

    private func alphaAt(_ strokes: [Stroke], _ layers: [Layer], _ x: Int, _ y: Int) -> Int {
        let img = StrokeRenderer.render(strokes, canvasWidth: 200, canvasHeight: 100, scale: 1, layers: layers)
        let px = HarmonyRenderer.argbPixels(img, 200, 100)
        return Int((px[y * 200 + x] >> 24) & 0xFF)
    }

    func testLayerOpacityHalvesAlpha() {
        let top = Layer(id: "t", name: "Üst")
        let strokes = [line("a", 20, 180, 50, layer: "t")]
        let full = alphaAt(strokes, [base, top], 100, 50)
        let half = alphaAt(strokes, [base, Layer(id: "t", name: "Üst", opacity: 0.5)], 100, 50)
        XCTAssertGreaterThan(full, 200)
        XCTAssertEqual(Double(half), Double(full) / 2, accuracy: 6)
    }

    func testClipToBelowShowsOnlyWhereBaseIsPainted() {
        let top = Layer(id: "t", name: "Gölge", clipToBelow: true)
        let strokes = [line("b", 10, 90, 50), line("c", 10, 190, 50, layer: "t", color: "#0000FF")]
        XCTAssertGreaterThan(alphaAt(strokes, [base, top], 50, 50), 200)
        XCTAssertEqual(alphaAt(strokes, [base, top], 150, 50), 0, "taban boş yerde kırpılan katman görünmez")
        XCTAssertGreaterThan(alphaAt(strokes, [base, Layer(id: "t", name: "Gölge")], 150, 50), 200)
    }

    func testAlphaLockedStrokePaintsOnlyOverExistingInk() {
        let strokes = [line("b", 10, 90, 50), line("l", 10, 190, 50, locked: true, color: "#00FF00")]
        XCTAssertGreaterThan(alphaAt(strokes, [base], 50, 50), 200)
        XCTAssertEqual(alphaAt(strokes, [base], 150, 50), 0)
    }

    func testAppendRuleRefusesCompositedLayers() {
        let plain = [base, Layer(id: "t", name: "Üst")]
        XCTAssertTrue(LayerCompositor.appendSafe([line("a", 0, 1, 1, layer: "t")], plain))
        let blended = [base, Layer(id: "t", name: "Üst", blend: .MULTIPLY)]
        XCTAssertFalse(LayerCompositor.appendSafe([line("a", 0, 1, 1, layer: "t")], blended))
        XCTAssertTrue(LayerCompositor.appendSafe([line("a", 0, 1, 1)], blended))
        let clipped = [base, Layer(id: "t", name: "Gölge", clipToBelow: true)]
        XCTAssertFalse(LayerCompositor.appendSafe([line("a", 0, 1, 1)], clipped), "kırpılan katmanın tabanı")
        XCTAssertFalse(LayerCompositor.appendSafe([line("a", 0, 1, 1, locked: true)], plain))
    }

    func testBlendModeCodesMatchAndroid() {
        XCTAssertEqual(LayerBlend.allCases.map(\.code), Array(0...16))
        XCTAssertEqual(LayerBlend.of(16), .ADD)
        XCTAssertEqual(LayerBlend.of(99), .NORMAL)
    }
}
