import XCTest
@testable import DrawGuidance

/// v56 aşama 7: renk ayarları, katman çoğalt/birleştir, hazır ayarlar (Android V56ColorRulesTest ile aynı).
final class V56ColorTests: XCTestCase {
    private func ch(_ c: UInt32, _ shift: UInt32) -> Int { Int((c >> shift) & 0xFF) }
    private func near(_ a: UInt32, _ b: UInt32, _ tol: Int = 1) -> Bool {
        [0, 8, 16].allSatisfy { abs(ch(a, UInt32($0)) - ch(b, UInt32($0))) <= tol } && (a >> 24) == (b >> 24)
    }

    func testIdentityAndHsl() {
        let red: UInt32 = 0xFFFF0000
        XCTAssertEqual(ColorAdjustOps.apply(red, ColorAdjust()), red)
        XCTAssertTrue(near(0xFF00FFFF, ColorAdjustOps.apply(red, ColorAdjust(hue: 180))))
        let gray = ColorAdjustOps.apply(red, ColorAdjust(saturation: -100))
        XCTAssertEqual(ch(gray, 16), ch(gray, 8)); XCTAssertEqual(ch(gray, 8), ch(gray, 0))
        XCTAssertEqual(ColorAdjustOps.apply(red, ColorAdjust(lightness: 100)), 0xFFFFFFFF)
        XCTAssertEqual(ColorAdjustOps.apply(red, ColorAdjust(lightness: -100)), 0xFF000000)
        let blue = ColorAdjustOps.apply(0xFF808080, ColorAdjust(hue: 240, colorize: true))
        XCTAssertTrue(ch(blue, 0) > ch(blue, 16) && ch(blue, 0) > ch(blue, 8))
        XCTAssertEqual(ColorAdjustOps.apply(0, ColorAdjust(hue: 90)), 0)
        XCTAssertEqual(ColorAdjustOps.apply(0x80FF0000, ColorAdjust(hue: 90)) >> 24, 0x80)
    }

    func testBalanceAndCurves() {
        let mid: UInt32 = 0xFF808080
        var noPreserve = ColorAdjust().withBalance(1, 0, 100); noPreserve.preserveLuminosity = false
        XCTAssertGreaterThan(ch(ColorAdjustOps.apply(mid, noPreserve), 16), 0x80)
        let kept = ColorAdjustOps.apply(mid, ColorAdjust().withBalance(1, 0, 100))
        let lum = { (c: UInt32) in 0.299 * Double(self.ch(c, 16)) + 0.587 * Double(self.ch(c, 8)) + 0.114 * Double(self.ch(c, 0)) }
        XCTAssertEqual(lum(mid), lum(kept), accuracy: 1.5)
        var shadows = ColorAdjust().withBalance(0, 2, 100); shadows.preserveLuminosity = false
        XCTAssertEqual(ColorAdjustOps.apply(0xFFFFFFFF, shadows), 0xFFFFFFFF)
        var invert = ColorAdjust(); invert.curves[0] = ColorAdjust.curveX.reversed()
        XCTAssertTrue(near(0xFF000000, ColorAdjustOps.apply(0xFFFFFFFF, invert)))
        XCTAssertTrue(near(0xFF00FFFF, ColorAdjustOps.apply(0xFFFF0000, invert)))
        let lut = ColorAdjustOps.curveLut(ColorAdjust.curveX)
        XCTAssertTrue((0...255).allSatisfy { abs(lut[$0] - $0) <= 1 })
        let r = ColorAdjustOps.apply(mid, ColorAdjust().withCurve(1, 2, 0.9))
        XCTAssertTrue(ch(r, 16) > 0xD0 && ch(r, 8) == 0x80)
    }

    func testHexAndStrokeMapping() {
        let m = ColorAdjustOps.Mapper(ColorAdjust(hue: 180))
        XCTAssertEqual(ColorAdjustOps.mapHex("#FF0000", m), "#00FFFF")
        XCTAssertEqual(ColorAdjustOps.mapHex("#80FF0000", m), "#8000FFFF")
        XCTAssertEqual(ColorAdjustOps.mapHex("bad", m), "bad")
        var shape = Stroke(id: "s", points: [StrokePoint(0, 0), StrokePoint(10, 0), StrokePoint(10, 10)], colorHex: "#FF0000",
                           brushType: .SHAPE, sizePx: 2, opacity: 1, order: 0, shape: ShapeSpec(kind: "tri", subpaths: [SubPath(count: 3, closed: true)]))
        shape.shape?.style.strokeColorHex = "#FF0000"; shape.shape?.style.fillColorHex = "#FF0000"
        let img = Stroke(id: "i", points: [StrokePoint(0, 0)], colorHex: "#FF0000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 1, assetId: "a")
        let out = ColorAdjustOps.mapStrokes([shape, img], ["s", "i"], ColorAdjust(hue: 180))
        XCTAssertEqual(out[0].colorHex, "#00FFFF")
        XCTAssertEqual(out[0].shape?.style.fillColorHex, "#00FFFF")
        XCTAssertEqual(out[0].shape?.style.strokeColorHex, "#00FFFF")
        XCTAssertEqual(out[1], img)
    }

    private func s(_ id: String, _ layer: String?, group: String? = nil) -> Stroke {
        Stroke(id: id, points: [StrokePoint(0, 0), StrokePoint(5, 5)], colorHex: "#000000", brushType: .INK_PEN, sizePx: 2, opacity: 1, order: 0,
               groupId: group, layerId: layer)
    }

    func testDuplicateAndMerge() {
        let layers = [Layer(id: "a", name: "A"), Layer(id: "b", name: "B", opacity: 0.5), Layer(id: "c", name: "C")]
        let pages = [[s("1", nil), s("2", "b", group: "g"), s("3", "b", group: "g"), s("4", "c")], [s("5", "b")]]
        var n = 0
        let (l2, p2) = LayerOps.duplicate(layers, pages, "b", copyId: "b2", name: "B (kopya)") { n += 1; return "n\(n)" }
        XCTAssertEqual(l2.map(\.id), ["a", "b", "b2", "c"])
        XCTAssertEqual(l2[2].opacity, 0.5)
        let copies = p2[0].filter { $0.layerId == "b2" }
        XCTAssertEqual(copies.count, 2)
        XCTAssertEqual(Set(copies.map(\.groupId)).count, 1); XCTAssertNotEqual(copies[0].groupId, "g")
        XCTAssertEqual(p2[0].last?.layerId, "c")
        XCTAssertEqual(p2[1].count, 2)
        XCTAssertTrue(LayerOps.needsRaster(layers, ["a", "b"]))
        XCTAssertFalse(LayerOps.needsRaster(layers, ["a", "c"]))
        let (l3, p3) = LayerOps.mergeVector(layers, pages, ["a", "c"])
        XCTAssertEqual(l3.map(\.id), ["b", "c"])
        XCTAssertTrue(p3[0].filter { $0.id == "1" || $0.id == "4" }.allSatisfy { $0.layerId == "c" })
        XCTAssertEqual(LayerOps.below(layers, "b")?.id, "a")
    }

    func testPresetsAndFlags() {
        XCTAssertEqual(V56Presets.layer(.LIGHT).blend, .SCREEN)
        XCTAssertEqual(V56Presets.layer(.SHADOW).blend, .MULTIPLY)
        XCTAssertTrue(V56Flags.enabled(.LAYER_HSL_BALANCE, .COMIC))
        XCTAssertFalse(V56Flags.enabled(.LAYER_HSL_BALANCE, .NOTES))
    }
}
