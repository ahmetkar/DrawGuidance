import XCTest
import UIKit
@testable import DrawGuidance

final class ShapeRendererTests: XCTestCase {
    func testColorMathMatchesAndroid() {
        XCTAssertEqual(ArgbColor.parse("#336699"), 0xFF336699)
        XCTAssertEqual(ArgbColor.parse("#80336699"), 0x80336699)
        XCTAssertEqual(ArgbColor.parse("red"), 0xFFFF0000)
        XCTAssertNil(ArgbColor.parse("#12"))
        XCTAssertEqual(ArgbColor.hex("bozuk"), ArgbColor.black)
        // tone(0.5) rengin kendisi; 0 → %25; 1 → beyaza %75 karışım (Kotlin roundToInt ile)
        XCTAssertEqual(ArgbColor.tone(0xFF808080, 0.5), 0xFF808080)
        XCTAssertEqual(ArgbColor.tone(0xFF808080, 0), ArgbColor.rgb(32, 32, 32))
        XCTAssertEqual(ArgbColor.tone(0xFF000000, 1), ArgbColor.rgb(191, 191, 191))
        XCTAssertEqual(ShapeRenderer.shade(0.725), "@shade:0.73")
    }

    func testResolveFill() {
        let style = ShapeStyle(strokeColorHex: "#000000", strokeWidth: 2, fillColorHex: "#FF0000")
        XCTAssertEqual(ShapeRenderer.resolveFill(nil, style), "#FF0000")
        XCTAssertEqual(ShapeRenderer.resolveFill("@ink", style), "#000000")
        XCTAssertNil(ShapeRenderer.resolveFill("@none", style))
        XCTAssertEqual(ShapeRenderer.resolveFill("@shade:0.50", style), "#FF0000")
        // Dolgusuz cisimde koyu kenar rengi önce açılır
        let plain = ShapeStyle(strokeColorHex: "#000000", strokeWidth: 2)
        XCTAssertEqual(ShapeRenderer.resolveFill("@shade:0.50", plain), "#808080")
    }

    func testFillsRules() {
        let sq = [StrokePoint(0, 0), StrokePoint(100, 0), StrokePoint(100, 100), StrokePoint(0, 100)]
        XCTAssertTrue(ShapeRenderer.fills(SubPath(count: 4, closed: true), sq[...], strokeWidth: 2))
        XCTAssertFalse(ShapeRenderer.fills(SubPath(count: 4), sq[...], strokeWidth: 2))
        let nearly = sq + [StrokePoint(0, 5)]
        XCTAssertTrue(ShapeRenderer.fills(SubPath(count: 5), nearly[...], strokeWidth: 2))
        XCTAssertFalse(ShapeRenderer.fills(SubPath(count: 4, closed: true, fillColorHex: "@none"), sq[...], strokeWidth: 2))
    }

    func testLibraryShapeBuildsAndRenders() throws {
        let cat = try XCTUnwrap(ShapePicker.categories(.ART).first)
        XCTAssertTrue(cat.id.hasPrefix("gen_"))
        XCTAssertFalse(ShapePicker.categories(.SIMPLE_ART).contains { $0.id == ShapePicker.FLOWCHART })
        XCTAssertTrue(ShapePicker.categories(.NOTES).contains { $0.id == "sci_physics" })
        XCTAssertTrue(ShapePicker.categories(.KIDS).allSatisfy { $0.id.hasPrefix("kids_") })

        var built = 0
        for c in ShapeLibrary.shared.categories {
            for s in c.shapes {
                let id = ShapeLibrary.PREFIX + s.id
                guard let st = ShapeFactory.build(id, P(10, 10), P(210, 160), style: ShapeStyle(), id: "t", order: 0) else {
                    XCTFail("kurulamadı: \(id)"); continue
                }
                XCTAssertEqual(st.brushType, .SHAPE)
                XCTAssertEqual(st.shape?.subpaths.reduce(0) { $0 + $1.count }, st.points.count, id)
                let b = try XCTUnwrap(ShapeFactory.bounds(st))
                // Oranı korunan ve çizgi gibi yerleşen şekiller sürüklenen kutudan taşabilir; yalnız sonlu ve boş olmamalı
                XCTAssertTrue(b.left.isFinite && b.right.isFinite && b.top.isFinite && b.bottom.isFinite, id)
                XCTAssertGreaterThan(b.width + b.height, 1, id)
                built += 1
            }
        }
        XCTAssertEqual(built, 502)

        let id = try XCTUnwrap(cat.items.first)
        let img = try XCTUnwrap(ShapePicker.thumbnail(id))
        XCTAssertGreaterThan(inkPixels(img), 20)
    }

    func testRendersFilledSquareWithCodecRoundTrip() throws {
        let path = ShapePath(points: [P(10, 10), P(90, 10), P(90, 90), P(10, 90), P(10, 10)], closed: true)
        let stroke = try XCTUnwrap(ShapeFactory.fromPaths("lib.test", [path],
            style: ShapeStyle(strokeColorHex: "#0000FF", strokeWidth: 2, fillColorHex: "#FF0000"), graph: nil, id: "sq", order: 0))
        XCTAssertEqual(stroke.points.count, 4)
        XCTAssertEqual(stroke.shape?.subpaths.first?.closed, true)
        let doc = try ContentCodec.decodeDocument(ContentCodec.encodePages([[stroke]]))
        XCTAssertEqual(doc.pages.first?.first?.shape, stroke.shape)

        let img = UIGraphicsImageRenderer(size: CGSize(width: 100, height: 100), format: {
            let f = UIGraphicsImageRendererFormat(); f.scale = 1; return f
        }()).image { ShapeRenderer.draw($0.cgContext, stroke, cache: false) }
        let center = pixel(img, 50, 50)
        XCTAssertGreaterThan(center.r, 200); XCTAssertLessThan(center.b, 60)
        let edge = pixel(img, 10, 50)
        XCTAssertGreaterThan(edge.b, 120)
        XCTAssertEqual(ShapeRenderer.distanceTo(stroke, P(50, 50)), 0)
        XCTAssertGreaterThan(ShapeRenderer.distanceTo(stroke, P(150, 50)), 50)
    }

    func testBuiltinShapesBuild() throws {
        for k in ShapeKind.allCases where !ShapeKind.legacyGraphs.contains(k) {
            let st = ShapeFactory.build(k.rawValue, P(20, 20), P(320, 220), style: ShapeStyle(), id: "b", order: 0)
            XCTAssertNotNil(st, k.rawValue)
            XCTAssertNotEqual(L("shape_" + k.rawValue.lowercased()), "shape_" + k.rawValue.lowercased(), k.rawValue)
        }
        // Kare yerleşim: sürüklenen yönde kare kutu
        let f = ShapeCatalog.frame(.CIRCLE, P(100, 100), P(40, 180))
        XCTAssertEqual(f, FRect(left: 20, top: 100, right: 100, bottom: 180))
        // Ok: gövde + uç
        XCTAssertEqual(ShapeCatalog.paths(.ARROW, P(0, 0), P(100, 0), ink: 4).count, 2)
        // Kesik çizgi parçalara ayrılır (12 dolu, 8.8 boş)
        let d = ShapeCatalog.dashes(ShapePath(points: [P(0, 0), P(100, 0)], dashed: true), 4)
        XCTAssertEqual(d.count, 5)
        XCTAssertEqual(d[0].points.last?.x ?? 0, 12, accuracy: 0.01)
        // Gölgeli cisimlerin yüzleri renk tonuyla dolar
        let cube = ShapeSolids.paths(.SOLID_CUBE, FRect(left: 0, top: 0, right: 100, bottom: 100))
        XCTAssertEqual(cube.map(\.fillColorHex), ["@shade:0.50", "@shade:0.78", "@shade:0.30"])
        XCTAssertFalse(ShapePicker.categories(.NOTES).contains { $0.id == "NOTE_GRAPHS" })
        XCTAssertEqual(ShapePicker.categories(.ART).first?.id, "ART_GENERAL")
    }

    // MARK: - yardımcılar

    private func rgba(_ img: UIImage) -> (data: [UInt8], w: Int, h: Int) {
        let cg = img.cgImage!
        let w = cg.width, h = cg.height
        var data = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = CGContext(data: &data, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (data, w, h)
    }

    private func pixel(_ img: UIImage, _ x: Int, _ y: Int) -> (r: Int, g: Int, b: Int, a: Int) {
        let (d, w, _) = rgba(img)
        let i = (y * w + x) * 4
        return (Int(d[i]), Int(d[i + 1]), Int(d[i + 2]), Int(d[i + 3]))
    }

    private func inkPixels(_ img: UIImage) -> Int {
        let (d, w, h) = rgba(img)
        var n = 0
        for i in 0..<(w * h) where d[i * 4 + 3] > 64 { n += 1 }
        return n
    }
}
