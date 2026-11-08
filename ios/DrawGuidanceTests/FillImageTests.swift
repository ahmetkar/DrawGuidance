import XCTest
@testable import DrawGuidance

/// Kova/boya fırçası alan bulma, resim efektleri ve arka plan silme (Android ile birebir sonuçlar; değerler Android'den).
final class FillImageTests: XCTestCase {
    private func circle(_ cx: Float, _ cy: Float, _ r: Float, _ n: Int) -> [P] {
        (0..<n).map { i in let a = 2 * Double.pi * Double(i) / Double(n); return P(cx + Float(Double(r) * cos(a)), cy + Float(Double(r) * sin(a))) }
    }

    func testRegionWithHolesAndOpenArea() {
        let face = [RegionFill.Line(circle(200, 200, 150, 64), true, 6), RegionFill.Line(circle(150, 160, 20, 24), true, 4),
                    RegionFill.Line(circle(250, 160, 20, 24), true, 4)]
        let reg = RegionFill.region(face, P(200, 200))!
        XCTAssertTrue(RegionFill.contains(reg, P(200, 250)))
        XCTAssertFalse(RegionFill.contains(reg, P(150, 160)))  // göz deliği
        XCTAssertNil(RegionFill.region(face, P(10, 10)))
        let open = [RegionFill.Line([P(0, 0), P(100, 0), P(100, 100)], false, 3)]
        XCTAssertNil(RegionFill.region(open, P(60, 30)))
        let clipped = RegionClip.clip(face, P(200, 200), [P(100, 200), P(320, 250)], 25)!
        XCTAssertFalse(clipped.isEmpty)
    }

    func testImageEffectsKeepAlphaAndChangePixels() {
        let w = 40, h = 30
        var src = [UInt32](repeating: 0, count: w * h)
        for y in 0..<h { for x in 0..<w { src[y * w + x] = (x < 3 ? 0 : 0xFF00_0000) | UInt32(x * 6) << 16 | UInt32(y * 8) << 8 | 0x40 } }
        for look in ImageLook.allCases {
            let out = ImageEffects.apply(src, w, h, look: look)
            XCTAssertEqual(out.count, src.count)
            XCTAssertEqual(out[w * 10 + 20] >> 24, 0xFF, "\(look)")
        }
        XCTAssertEqual(ImageEffects.apply(src, w, h), src)
        XCTAssertGreaterThan(ImageEffects.meanDiff(src, ImageEffects.apply(src, w, h, adjust: ImageAdjust(brightness: 0.5))), 10)
    }

    func testBackgroundRemoverRemovesPlainBackground() {
        let w = 120, h = 90
        var src = [UInt32](repeating: 0xFFE6EBF0, count: w * h)
        for y in 30..<70 { for x in 40..<80 { src[y * w + x] = 0xFF285A32 } }
        let m = BackgroundRemover.mask(src, w, h, BackgroundOptions())
        XCTAssertGreaterThan(m.removedRatio, 0.7)
        let out = BackgroundRemover.apply(src, w, h, m)
        XCTAssertEqual(out[5] >> 24, 0)
        XCTAssertEqual(out[50 * w + 60] >> 24, 0xFF)
    }
}
