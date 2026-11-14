import CoreGraphics
import XCTest
@testable import DrawGuidance

/// v51 yeni fırçalar: kayıt biçimi, piksel motoru (boya ve yardımcı fırçalar), kalem dinamikleri ve Android ile aynı gürültü.
final class V51BrushTests: XCTestCase {
    private let newTypes: [BrushType] = [.ACRYLIC, .OIL_PAINT, .CLOUD_SMOKE, .FX_BLUR, .FX_SHARPEN, .FX_DODGE, .FX_BURN, .FX_SPONGE,
                                         .FX_CLONE, .FX_HEAL, .FX_MIXER]

    func testNewBrushTypesAndParamsRoundTrip() throws {
        var tip = TipParams(); tip.minSize = 0.3; tip.hardness = 0.6; tip.shape = .BRISTLE; tip.wetness = 0.5
        var pen = PenDynamics(); pen.pressure = 0.7; pen.barrel = -0.4
        let params = BrushParams(tip: tip, pen: pen, fx: FxParams(mode: 2, srcDx: 12, srcDy: -8))
        let strokes = newTypes.enumerated().map { i, t in
            Stroke(id: "s\(i)", points: [StrokePoint(ContentCodec.quantize(10 + Float(i)), ContentCodec.quantize(20))], colorHex: "#336699",
                   brushType: t, sizePx: 24, opacity: 0.8, order: i, params: params)
        }
        XCTAssertEqual(try ContentCodec.decode(ContentCodec.encode(strokes)), strokes)
    }

    func testRollAndBarrelRoundTrip() throws {
        let q = ContentCodec.quantizeUnit
        let pts = [StrokePoint(1, 2, pressure: q(0.5), tilt: q(0.2), orient: q(0.3), roll: q(0.25), barrel: q(1)),
                   StrokePoint(3, 4, pressure: q(0.6), barrel: q(0.5))]
        let s = Stroke(id: "r", points: pts, colorHex: "#000000", brushType: .PENCIL, sizePx: 4, opacity: 1, order: 0)
        XCTAssertEqual(try ContentCodec.decode(ContentCodec.encode([s])), [s])
    }

    func testNoiseMatchesAndroid() {
        XCTAssertEqual(RasterBrush.hash1(5, 7).bitPattern, 1044553220)
        XCTAssertEqual(RasterBrush.hash1(-3, 123456).bitPattern, 1064352043)
        XCTAssertEqual(RasterBrush.hash2(10, -20, 99).bitPattern, 1059535438)
    }

    // MARK: Piksel motoru

    /// 80×40 bölge: sol yarı kırmızı, sağ yarı mavi (ön çarpımlı).
    private func base() -> RasterBrush.Patch {
        let p = RasterBrush.Patch(left: 0, top: 0, scale: 1, w: 80, h: 40)
        for y in 0..<40 { for x in 0..<80 {
            let o = (y * 80 + x) * 4
            if x < 40 { p.rgba[o] = 1 } else { p.rgba[o + 2] = 1 }
            p.rgba[o + 3] = 1
        } }
        return p
    }

    private func gray() -> RasterBrush.Patch {
        let p = RasterBrush.Patch(left: 0, top: 0, scale: 1, w: 80, h: 40)
        for i in 0..<(80 * 40) { p.rgba[i * 4] = 0.5; p.rgba[i * 4 + 1] = 0.5; p.rgba[i * 4 + 2] = 0.5; p.rgba[i * 4 + 3] = 1 }
        return p
    }

    private func render(_ type: BrushType, _ params: BrushParams? = nil, _ patch: RasterBrush.Patch? = nil) -> RasterBrush.Result {
        RasterBrush.render(patch ?? base(), type: type, color: 0xFF00_FF00, sizePx: 16, opacity: 1, flow: 1, strength: 1, seed: 7,
                           xs: [10, 70], ys: [20, 20], count: 2, pp: nil, pt: nil, po: nil, pr: nil, pb: nil, params: params)
    }

    func testPaintBrushesLeavePaint() {
        for t in [BrushType.ACRYLIC, .OIL_PAINT, .CLOUD_SMOKE] {
            let r = render(t, nil, RasterBrush.Patch(left: 0, top: 0, scale: 1, w: 80, h: 40))
            let row = (20..<60).map { 20 * 80 + $0 }
            XCTAssertTrue(row.contains { r.mask[$0] > 0 }, "\(t) maske")
            XCTAssertGreaterThan(row.map { r.color[$0 * 4 + 1] }.max() ?? 0, 0.1, "\(t) yeşil")
        }
    }

    func testBlurMixesTheEdge() {
        let r = render(.FX_BLUR)
        let i = (20 * 80 + 40) * 4
        XCTAssertGreaterThan(r.mask[20 * 80 + 40], 0)
        XCTAssertTrue(r.color[i] > 0.05 && r.color[i + 2] > 0.05)
    }

    func testDodgeBrightensBurnDarkens() {
        let i = (20 * 80 + 40) * 4
        XCTAssertGreaterThan(render(.FX_DODGE, nil, gray()).color[i], 0.55)
        XCTAssertLessThan(render(.FX_BURN, nil, gray()).color[i], 0.45)
    }

    func testSpongeDesaturates() {
        let r = render(.FX_SPONGE, BrushParams(fx: FxParams(mode: 0)))
        XCTAssertGreaterThan(r.color[(20 * 80 + 20) * 4 + 1], 0.1)
    }

    func testCloneCopiesFromSourceOffset() {
        let r = render(.FX_CLONE, BrushParams(fx: FxParams(srcDx: 30, srcDy: 0)))
        XCTAssertEqual(r.color[(20 * 80 + 20) * 4 + 2], 1, accuracy: 0.01)
    }

    func testEffectiveTipFallsBackToDefaults() {
        var tip = TipParams(); tip.hardness = 0.1
        let t = RasterBrush.effectiveTip(.OIL_PAINT, BrushParams(tip: tip))
        XCTAssertEqual(t.hardness, 0.1)
        XCTAssertEqual(t.wetness, RasterBrush.defaults(.OIL_PAINT).wetness)
    }

    func testPenModelOnlyWhenConfigured() throws {
        XCTAssertNil(PenModel.of(nil))
        var hardOnly = TipParams(); hardOnly.hardness = 0.5
        XCTAssertNil(PenModel.of(BrushParams(tip: hardOnly)))
        var range = TipParams(); range.minSize = 0.5; range.maxSize = 2
        let pm = try XCTUnwrap(PenModel.of(BrushParams(tip: range)))
        let d = PenModel.PointData([0, 10], [0, 0], 2, nil, nil, nil, nil, nil, width: 10)
        XCTAssertEqual(pm.size(1, d, 0), 2, accuracy: 1e-4)
        XCTAssertEqual(pm.size(0, d, 0), 1, accuracy: 1e-4)
        var barrel = PenDynamics(); barrel.barrel = 1
        let bm = try XCTUnwrap(PenModel.of(BrushParams(pen: barrel)))
        let b = PenModel.PointData([0], [0], 1, nil, nil, nil, nil, [1], width: 10)
        XCTAssertEqual(bm.size(1, b, 0), 2, accuracy: 1e-4)
    }

    /// Tuval listesinde yardımcı fırça alttaki çizimi işler; tek başına (bağlamsız) hiçbir şey çizmez.
    func testHelperWorksOnContentBelowInStrokeList() {
        let ink = Stroke(id: "a", points: (0...10).map { StrokePoint(10 + Float($0) * 8, 40) }, colorHex: "#000000", brushType: .MARKER,
                         sizePx: 20, opacity: 1, order: 0)
        let blur = Stroke(id: "b", points: [StrokePoint(20, 40), StrokePoint(80, 40)], colorHex: "#000000", brushType: .FX_BLUR,
                          sizePx: 40, opacity: 1, order: 1)
        func alphaAt(_ img: CGImage, _ x: Int, _ y: Int) -> UInt8 {
            let data = img.dataProvider!.data! as Data
            return data[(y * img.bytesPerRow) + x * 4 + 3]
        }
        let plain = StrokeRenderer.render([ink], canvasWidth: 100, canvasHeight: 80, scale: 1)
        let blurred = StrokeRenderer.render([ink, blur], canvasWidth: 100, canvasHeight: 80, scale: 1)
        // Çizginin kenarının hemen dışı bulanıklıkla boyanır
        XCTAssertGreaterThan(Int(alphaAt(blurred, 50, 40 - 22)), Int(alphaAt(plain, 50, 40 - 22)))
        let alone = StrokeRenderer.render([blur], canvasWidth: 100, canvasHeight: 80, scale: 1)
        XCTAssertEqual(alphaAt(alone, 50, 40), 0)
    }
}
