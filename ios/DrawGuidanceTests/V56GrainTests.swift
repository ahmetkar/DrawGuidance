import CoreGraphics
import XCTest
@testable import DrawGuidance

/// v56 aşama 5: hazır doku paketi, gren yerleşimi, fırça ayarı, doku dolgusu ve çizim (Android V56GrainTest ile aynı).
final class V56GrainTests: XCTestCase {
    func testTexturesDeterministicTileableAndMatchAndroid() {
        for id in V56Textures.BUILTIN {
            let t = V56Textures.tile(id)
            XCTAssertEqual(t.count, V56Textures.SIZE * V56Textures.SIZE)
            XCTAssertTrue(t.allSatisfy { $0 >= 0 && $0 <= 1 }, id)
            XCTAssertTrue(t.contains { $0 < 0.5 } && t.contains { $0 > 0.9 }, id)
            XCTAssertEqual(V56Textures.value(id, 5, 9), V56Textures.value(id, 5 + V56Textures.SIZE, 9 - V56Textures.SIZE))
        }
        XCTAssertEqual(V56Textures.value("builtin:hatch", 0, 0), 1)
        XCTAssertEqual(V56Textures.value("builtin:hatch", 6, 0), 0.15)
        XCTAssertEqual(V56Textures.value("builtin:dots", 7, 7), 1)
        XCTAssertEqual(V56Textures.value("bilinmeyen", 3, 3), 1)
        // Android V56GrainRulesTest.SAMPLES ile aynı değerler (aynı karma ve formül)
        let samples: [(String, Int, Int, Float)] = [
            ("builtin:paper", 0, 0, 0.1244707), ("builtin:paper", 13, 7, 1), ("builtin:paper", 77, 101, 0.50449765),
            ("builtin:canvas", 0, 0, 0.36003512), ("builtin:canvas", 13, 7, 0.7779521), ("builtin:canvas", 77, 101, 0.8011633),
            ("builtin:charcoal", 0, 0, 0), ("builtin:charcoal", 77, 101, 1),
            ("builtin:noise", 0, 0, 0.32896167), ("builtin:noise", 13, 7, 0.61222017), ("builtin:noise", 77, 101, 0.17517483),
        ]
        for (id, x, y, v) in samples { XCTAssertEqual(V56Textures.value(id, x, y), v, accuracy: 1e-4, "\(id) \(x),\(y)") }
    }

    func testGrainAnchorsScaleAndStrength() {
        let sliding = GrainSpec(textureId: "builtin:hatch")
        var fixed = sliding; fixed.moving = false
        XCTAssertEqual(V56Textures.sample(sliding, 6, 0, 0, 0), V56Textures.sample(sliding, 6, 0, 50, 50))
        XCTAssertEqual(V56Textures.sample(fixed, 6, 0, 0, 0), V56Textures.sample(fixed, 56, 50, 50, 50))
        var big = sliding; big.scale = 2
        XCTAssertEqual(V56Textures.sample(sliding, 6, 0), V56Textures.sample(big, 12, 0))
        XCTAssertEqual(V56Textures.alpha(0.2, 0), 1)
        XCTAssertEqual(V56Textures.alpha(0.2, 1), 0.2, accuracy: 1e-6)
        XCTAssertEqual(V56Textures.alpha(0.2, 0.5), 0.6, accuracy: 1e-6)
    }

    func testTuningCarriesGrainAndImageTip() {
        var t = BrushTuning()
        t.grain = GrainSpec(textureId: "builtin:paper", scale: 2)
        t.stampAssetId = "uc"
        XCTAssertEqual(t.params(.PENCIL)?.grain?.textureId, "builtin:paper")
        XCTAssertEqual(t.params(.PENCIL)?.stampAssetId, "uc")
        XCTAssertNotNil(t.params(.OIL_PAINT)?.grain)
        XCTAssertNil(t.params(.OIL_PAINT)?.stampAssetId)
        XCTAssertNil(t.params(.ERASER))
        XCTAssertNil(BrushTuning().params(.PENCIL))
        // Kayıt: eski ayarlarda olmayan alanlar okunur, yeni alanlar korunur
        let data = try! JSONEncoder().encode(t)
        XCTAssertEqual(try! JSONDecoder().decode(BrushTuning.self, from: data), t)
        let fill = V56Selection.lassoFill(id: "f", V56Textures.pageRect(100, 50), colorHex: "#112233", opacity: 0.5, order: 0,
                                          grain: GrainSpec(textureId: "builtin:dots"))
        XCTAssertEqual(fill.brushType, .LASSO_FILL)
        XCTAssertEqual(fill.points.count, 4)
        XCTAssertEqual(fill.params?.grain?.textureId, "builtin:dots")
    }

    /// Y aşağı bağlamda çizip (x, y) pikselinin alfası.
    private func alphaAt(_ strokes: [Stroke], _ x: Int, _ y: Int) -> Int {
        var px = [UInt8](repeating: 0, count: 80 * 80 * 4)
        px.withUnsafeMutableBytes { buf in
            let ctx = CGContext(data: buf.baseAddress, width: 80, height: 80, bitsPerComponent: 8, bytesPerRow: 80 * 4, space: Bitmaps.space,
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            ctx.translateBy(x: 0, y: 80); ctx.scaleBy(x: 1, y: -1)
            StrokeRenderer.drawStrokeList(ctx, strokes)
        }
        return Int(px[(y * 80 + x) * 4 + 3])
    }

    private func square(_ grain: GrainSpec?) -> Stroke {
        V56Selection.lassoFill(id: "s", [StrokePoint(0, 0), StrokePoint(64, 0), StrokePoint(64, 64), StrokePoint(0, 64)],
                               colorHex: "#000000", opacity: 1, order: 0, grain: grain)
    }

    func testGrainMultipliesFillWithTexture() {
        XCTAssertEqual(alphaAt([square(nil)], 6, 1), 255)
        let hatch = [square(GrainSpec(textureId: "builtin:hatch"))]
        XCTAssertGreaterThan(alphaAt(hatch, 1, 0), 200)
        XCTAssertEqual(Double(alphaAt(hatch, 7, 0)), 0.15 * 255, accuracy: 12)
        let half = alphaAt([square(GrainSpec(textureId: "builtin:hatch", strength: 0.5))], 7, 0)
        XCTAssertTrue((130...170).contains(half), "güç 0.5: \(half)")
    }

    func testImageTipStampsAlongPath() {
        let src = Bitmaps.draw(20, 20) { ctx in
            ctx.setFillColor(CGColor(gray: 1, alpha: 1)); ctx.fill(CGRect(x: 0, y: 0, width: 20, height: 20))
            ctx.setFillColor(CGColor(gray: 0, alpha: 1)); ctx.fill(CGRect(x: 0, y: 0, width: 10, height: 20))
        }
        let tip = GrainRenderer.tipFrom(src)
        XCTAssertEqual(tip.width, 10, "boş (beyaz) sağ yarı kırpılır")
        CanvasImageCache.put("v56-tip", tip)
        var s = Stroke(id: "i", points: [StrokePoint(10, 40), StrokePoint(40, 40), StrokePoint(70, 40)], colorHex: "#FF0000",
                       brushType: .MARKER, sizePx: 20, opacity: 1, order: 0)
        s.params = BrushParams(stampAssetId: "v56-tip")
        XCTAssertGreaterThan(alphaAt([s], 40, 40), 200)
        XCTAssertEqual(alphaAt([s], 40, 75), 0)
    }

    @MainActor
    func testFlags() {
        XCTAssertTrue(V56Flags.enabled(.GRAIN, .ART))
        XCTAssertFalse(V56Flags.enabled(.GRAIN, .NOTES))
        XCTAssertFalse(V56Flags.enabled(.GRAIN, .KIDS))
    }
}
