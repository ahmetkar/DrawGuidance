import CoreGraphics
import XCTest
@testable import DrawGuidance

/// v23 arka planlar: desen ve hazır resim kataloğu, resim üretimi, tuval ve yazı kutusu arka planının çizimi.
final class BackgroundTests: XCTestCase {
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

    private func rgb(_ px: [UInt8], _ w: Int, _ x: Int, _ y: Int) -> (Int, Int, Int) {
        let i = (y * w + x) * 4
        return (Int(px[i]), Int(px[i + 1]), Int(px[i + 2]))
    }

    private func nonWhite(_ px: [UInt8]) -> Int {
        var n = 0
        for i in stride(from: 0, to: px.count, by: 4) where px[i] < 250 || px[i + 1] < 250 || px[i + 2] < 250 { n += 1 }
        return n
    }

    func testCatalogsAreCompleteAndUnique() {
        XCTAssertEqual(PaperCatalog.all.count, 70)
        XCTAssertEqual(PaperImages.all.count, 26)
        let ids = PaperCatalog.all.map(\.id) + PaperImages.all.map(\.id)
        XCTAssertEqual(ids.count, Set(ids).count, "kimlikler tekil")
        XCTAssertEqual(PaperImages.of(.BOX).count, 10)
        for d in PaperImages.of(.BOX) { XCTAssertEqual(d.layout.kind, ImageLayout.NINE, d.id) }
    }

    func testLayoutRoundTrip() {
        for d in PaperImages.all {
            XCTAssertEqual(ImageLayout.decode(d.layout.encode()), d.layout, d.id)
        }
        XCTAssertEqual(ImageLayout.decode("9r:1,2,3,4|5,6,7,8").repeat_, true)
        XCTAssertEqual(ImageLayout.decode("bilinmeyen").kind, ImageLayout.COVER)
    }

    func testBoxImagesGenerateDeterministically() {
        for d in PaperImages.of(.BOX) {
            let a = d.generate()
            XCTAssertEqual(a.count, d.w * d.h, d.id)
            XCTAssertEqual(a, d.generate(), "\(d.id): aynı tohum aynı resim")
            // Kenarlar saydam (gölge dışında), orta kağıt opak
            XCTAssertEqual(a[0] >> 24, 0, "\(d.id): sol üst köşe saydam")
            XCTAssertEqual(a[(d.h / 2) * d.w + d.w / 2] >> 24, 255, "\(d.id): orta opak")
        }
        let linen = PaperImages.byId("note.img.linen")!
        let px = linen.generate()
        XCTAssertTrue(px.allSatisfy { $0 >> 24 == 255 }, "keten opak")
    }

    func testCanvasPatternDrawsOnPaper() {
        let spec = BackgroundSpec(patternId: "note.grid5")
        for vector in [true, false] {
            let px = pixels(300, 300) { c in
                BackgroundRenderer.drawCanvasBackground(c, spec, 0, 0, 300, 300, scale: 1, paper: 0xFFFF_FFFF, vector: vector, density: 2)
            }
            XCTAssertGreaterThan(nonWhite(px), 300 * 4, "ızgara çizgileri (vektör: \(vector))")
        }
        let empty = pixels(50, 50) { c in
            BackgroundRenderer.drawCanvasBackground(c, BackgroundSpec(), 0, 0, 50, 50, scale: 1, paper: 0xFFFF_FFFF, vector: true)
        }
        XCTAssertEqual(nonWhite(empty), 0)
    }

    func testEveryPatternDrawsBothWays() {
        for p in PaperCatalog.all {
            _ = pixels(120, 160) { c in
                BackgroundRenderer.drawCanvasBackground(c, BackgroundSpec(patternId: p.id), 0, 0, 120, 160, scale: 0.4, paper: 0xFFFF_FFFF, vector: false)
                BackgroundRenderer.drawCanvasBackground(c, BackgroundSpec(patternId: p.id), 0, 0, 120, 160, scale: 0.4, paper: 0xFFFF_FFFF, vector: true)
            }
        }
    }

    func testStickyNoteBoxIsYellow() {
        let box = TextBox(imageId: "box.img.sticky_yellow", imageFit: .NINE_SLICE)
        let px = pixels(300, 200) { c in BackgroundRenderer.drawBox(c, box, 300, 200, alpha: 1, coverage: false) }
        let (r, g, b) = rgb(px, 300, 150, 100)
        XCTAssertGreaterThan(r, 230); XCTAssertGreaterThan(g, 210); XCTAssertLessThan(b, 160, "sarı not ortası")
        // Yazı iç boşluğu resmin yazı alanından gelir
        XCTAssertEqual(BackgroundRenderer.boxContent(box), [20, 32, 24, 26])
        let spec = TextSpec(content: "Not", box: box)
        XCTAssertEqual(TextRenderer.pads(spec, 20)[1], 32, accuracy: 0.01)
    }

    func testCoverImageFillsCanvas() {
        let spec = BackgroundSpec(imageId: BackgroundSpec.PAPER + "note.img.sky", imageFit: .COVER)
        let px = pixels(200, 100) { c in
            BackgroundRenderer.drawCanvasBackground(c, spec, 0, 0, 200, 100, scale: 1, paper: 0xFFFF_FFFF, vector: true)
        }
        let top = rgb(px, 200, 100, 2)
        XCTAssertLessThan(top.0, top.2, "üst taraf mavi")
    }

    func testCornerDragKeepsOppositeCorner() {
        let spec = BackgroundSpec(imageId: "paper:note.img.sky")
        let moved = BackgroundTransform.corner(spec, 400, 600, BackgroundTransform.BOTTOM_RIGHT, P(200, 300))
        XCTAssertEqual(moved.imageScale, 0.5, accuracy: 0.001)
        let tl = BackgroundTransform.corners(moved, 400, 600)[BackgroundTransform.TOP_LEFT]
        XCTAssertEqual(tl.x, 0, accuracy: 0.01); XCTAssertEqual(tl.y, 0, accuracy: 0.01)
        XCTAssertEqual(BackgroundTransform.reset(moved).imageScale, 1)
    }

    func testPageDecorInk() {
        XCTAssertEqual(PageDecorRenderer.inkFor(0xFF10_1010), 0xFFD0_D0D0)
        XCTAssertEqual(PageDecorRenderer.inkFor(0xFFFF_FFFF), 0xFF50_5050)
    }
}
