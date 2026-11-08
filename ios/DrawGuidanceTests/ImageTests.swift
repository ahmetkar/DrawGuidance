import CoreGraphics
import ImageIO
import UniformTypeIdentifiers
import XCTest
@testable import DrawGuidance

/// Resim ekleme kontrolleri, tuval resimleri deposu ve resmin dörtgene çizimi.
final class ImageTests: XCTestCase {
    private func png(_ w: Int, _ h: Int, _ body: (CGContext) -> Void) -> Data {
        let img = Bitmaps.draw(w, h, body)
        return ImageImportValidator.encode(img, .png)!
    }

    private func pixels(_ w: Int, _ h: Int, _ body: (CGContext) -> Void) -> [UInt8] {
        var data = [UInt8](repeating: 0, count: w * h * 4)
        data.withUnsafeMutableBytes { buf in
            let c = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                              space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            c.translateBy(x: 0, y: CGFloat(h)); c.scaleBy(x: 1, y: -1)
            c.setFillColor(ArgbColor.cgColor(0xFFFF_FFFF)); c.fill(CGRect(x: 0, y: 0, width: w, height: h))
            body(c)
        }
        return data
    }

    func testSimpleDrawingIsAccepted() {
        let data = png(400, 300) { c in
            c.setFillColor(ArgbColor.cgColor(0xFFFF_FFFF)); c.fill(CGRect(x: 0, y: 0, width: 400, height: 300))
            c.setStrokeColor(ArgbColor.cgColor(0xFF00_0000)); c.setLineWidth(4); c.strokeEllipse(in: CGRect(x: 100, y: 50, width: 200, height: 200))
        }
        let r = ImageImportValidator.check(data)
        XCTAssertTrue(r.accepted, "\(r.errors)")
        XCTAssertEqual(r.image?.width, 400)
        XCTAssertLessThan(r.image!.detailScore, ImageImportValidator.DETAIL_WARN)
    }

    func testNoiseIsTooDetailed() {
        var rnd = JavaRandom(7)
        let data = png(300, 300) { c in
            for y in 0..<150 {
                for x in 0..<150 {
                    c.setFillColor(ArgbColor.cgColor(rnd.nextInt(2) == 0 ? 0xFF00_0000 : 0xFFFF_FFFF))
                    c.fill(CGRect(x: x * 2, y: y * 2, width: 2, height: 2))
                }
            }
        }
        let r = ImageImportValidator.check(data)
        XCTAssertFalse(r.accepted)
        XCTAssertTrue(r.errors.contains { if case .tooDetailed = $0 { return true }; return false } ||
                      r.errors.contains { if case .tooLarge = $0 { return true }; return false }, "\(r.errors)")
    }

    func testRejectsTinyAndUnknown() {
        XCTAssertEqual(ImageImportValidator.check(png(10, 10) { _ in }).errors, [.tooSmall(10, 10)])
        XCTAssertEqual(ImageImportValidator.check(Data("merhaba".utf8)).errors, [.unsupportedFormat])
        XCTAssertEqual(ImageImportValidator.sniffMime(Data([0xFF, 0xD8, 0xFF, 0xE0])), "image/jpeg")
    }

    func testLargeImageIsDownscaled() {
        let data = png(2400, 1200) { c in
            c.setFillColor(ArgbColor.cgColor(0xFFFF_FFFF)); c.fill(CGRect(x: 0, y: 0, width: 2400, height: 1200))
            c.setFillColor(ArgbColor.cgColor(0xFF33_66CC)); c.fill(CGRect(x: 600, y: 300, width: 1200, height: 600))
        }
        let r = ImageImportValidator.check(data)
        XCTAssertTrue(r.accepted, "\(r.errors)")
        XCTAssertEqual(r.image?.width, 1600); XCTAssertEqual(r.image?.height, 800)
        XCTAssertTrue(r.warnings.contains(.willDownscale(2400, 1200, 1600, 800)))
    }

    func testAffineDetection() {
        let rect = [CGPoint(x: 10, y: 10), CGPoint(x: 110, y: 10), CGPoint(x: 110, y: 60), CGPoint(x: 10, y: 60)]
        let t = ImageRenderer.affine(rect, 200, 100)!
        let p = CGPoint(x: 200, y: 100).applying(t)
        XCTAssertEqual(p.x, 110, accuracy: 0.01); XCTAssertEqual(p.y, 60, accuracy: 0.01)
        let trapezoid = [CGPoint(x: 30, y: 10), CGPoint(x: 90, y: 10), CGPoint(x: 110, y: 60), CGPoint(x: 10, y: 60)]
        XCTAssertNil(ImageRenderer.affine(trapezoid, 200, 100))
    }

    func testImageStrokeDrawsUprightAndWarped() {
        // Üst yarısı kırmızı, alt yarısı mavi resim
        let img = Bitmaps.draw(40, 40) { c in
            c.setFillColor(ArgbColor.cgColor(0xFFFF_0000)); c.fill(CGRect(x: 0, y: 0, width: 40, height: 20))
            c.setFillColor(ArgbColor.cgColor(0xFF00_00FF)); c.fill(CGRect(x: 0, y: 20, width: 40, height: 20))
        }
        CanvasImageCache.put("test-img", img)
        func stroke(_ pts: [StrokePoint]) -> Stroke {
            Stroke(id: "i", points: pts, colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 0, assetId: "test-img")
        }
        let upright = stroke([StrokePoint(0, 0), StrokePoint(100, 0), StrokePoint(100, 100), StrokePoint(0, 100)])
        var px = pixels(100, 100) { c in StrokeRenderer.drawStroke(c, upright) }
        XCTAssertGreaterThan(px[(20 * 100 + 50) * 4], 200, "üst kırmızı")
        XCTAssertGreaterThan(px[(80 * 100 + 50) * 4 + 2], 200, "alt mavi")
        let warped = stroke([StrokePoint(30, 0), StrokePoint(70, 0), StrokePoint(100, 100), StrokePoint(0, 100)])
        px = pixels(100, 100) { c in StrokeRenderer.drawStroke(c, warped) }
        XCTAssertGreaterThan(px[(10 * 100 + 50) * 4], 200, "bükülmüş üst kırmızı")
        XCTAssertGreaterThan(px[(90 * 100 + 50) * 4 + 2], 200, "bükülmüş alt mavi")
        XCTAssertGreaterThan(px[(10 * 100 + 5) * 4 + 1], 200, "dörtgen dışı boş (beyaz)")
        // Bellekte olmayan resim: yer tutucu
        let missing = Stroke(id: "m", points: upright.points, colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 0, assetId: "yok")
        px = pixels(100, 100) { c in StrokeRenderer.drawStroke(c, missing) }
        XCTAssertLessThan(px[(50 * 100 + 50) * 4], 255, "yer tutucu gri")
    }

    func testAssetStoreSavesAndPrunes() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = CanvasAssetStore(directory: dir)
        let data = png(64, 64) { c in c.setFillColor(ArgbColor.cgColor(0xFF00_AA00)); c.fill(CGRect(x: 0, y: 0, width: 64, height: 64)) }
        let prepared = ImageImportValidator.check(data).image!
        let a = store.save("c1", prepared), b = store.save("c1", prepared)
        XCTAssertNotNil(store.bytes(a))
        XCTAssertTrue(CanvasImageCache.contains(a))
        // Yeniden açılınca dizinden okunur
        XCTAssertEqual(CanvasAssetStore(directory: dir).mimeType(a), prepared.mimeType)
        let used = Stroke(id: "s", points: [], colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 0, assetId: a)
        store.prune("c1", [used])
        XCTAssertNotNil(store.bytes(a)); XCTAssertNil(store.bytes(b))
        XCTAssertGreaterThan(store.totalBytes("c1"), 0)
        store.deleteForCanvas("c1")
        XCTAssertNil(store.bytes(a))
    }

    func testHarmonyShadowAndColor() {
        // Ortada koyu gri kare, çevresi saydam
        let img = Bitmaps.draw(60, 60) { c in
            c.setFillColor(ArgbColor.cgColor(0xFF80_8080)); c.fill(CGRect(x: 15, y: 10, width: 30, height: 40))
        }
        var spec = HarmonySpec()
        spec.gainR = 1.4; spec.gainB = 0.6
        let (px, w, h) = HarmonyRenderer.process(img, spec)
        XCTAssertEqual(w, 60); XCTAssertEqual(h, 60)
        let mid = px[30 * 60 + 30]
        XCTAssertGreaterThan(mid >> 16 & 0xFF, mid & 0xFF, "kırmızı kazancı > mavi")
        XCTAssertEqual(HarmonyRenderer.footY(px.map { UInt8($0 >> 24) }, w, h), 50)
        CanvasImageCache.put("harmony-img", img)
        let s = Stroke(id: "h", points: [StrokePoint(0, 0), StrokePoint(120, 0), StrokePoint(120, 120), StrokePoint(0, 120)], colorHex: "#000000",
                       brushType: .IMAGE, sizePx: 1, opacity: 1, order: 0, assetId: "harmony-img", harmony: spec)
        let out = pixels(120, 140) { c in StrokeRenderer.drawStroke(c, s) }
        // Ayağın (y=100) hemen altı gölgeyle kararır
        XCTAssertLessThan(out[(102 * 120 + 60) * 4], 250, "temas gölgesi")
        XCTAssertGreaterThan(out[(60 * 120 + 60) * 4], out[(60 * 120 + 60) * 4 + 2], "resim renk uyumlu")
    }
}
