import CoreGraphics
import XCTest
@testable import DrawGuidance

/// Yazı ve konuşma balonları: ölçü, kutuya kaydırma, dörtgene çizim, yazı tipleri, balon biçimleri.
final class TextRendererTests: XCTestCase {
    private func pixels(_ w: Int, _ h: Int, _ body: (CGContext) -> Void) -> [UInt8] {
        var data = [UInt8](repeating: 0, count: w * h * 4)
        data.withUnsafeMutableBytes { buf in
            let c = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: Bitmaps.space,
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            c.translateBy(x: 0, y: CGFloat(h)); c.scaleBy(x: 1, y: -1)
            body(c)
        }
        return data
    }

    private func inkBox(_ px: [UInt8], _ w: Int, _ h: Int) -> CGRect? {
        var bb = BoundsBuilder()
        for y in 0..<h { for x in 0..<w where px[(y * w + x) * 4 + 3] > 40 { bb.add(Float(x), Float(y)) } }
        return bb.isEmpty ? nil : bb.rect(0)
    }

    func testMeasureGrowsWithTextAndSize() {
        let a = TextRenderer.measure(TextSpec(content: "Merhaba"), 40)
        let b = TextRenderer.measure(TextSpec(content: "Merhaba dünya"), 40)
        let c = TextRenderer.measure(TextSpec(content: "Merhaba"), 80)
        XCTAssertGreaterThan(b.0, a.0)
        XCTAssertGreaterThan(c.1, a.1 * 1.7)
        let two = TextRenderer.measure(TextSpec(content: "Bir\nİki"), 40)
        XCTAssertGreaterThan(two.1, a.1 * 1.7)
    }

    func testBoxWidthWrapsLines() {
        var spec = TextSpec(content: "Uzun bir cümle kutunun içinde satırlara bölünmeli")
        spec.box = TextBox(width: 200)
        let (w, h) = TextRenderer.measure(spec, 30)
        XCTAssertEqual(w, 200)
        XCTAssertGreaterThan(h, 30 * 3)
    }

    func testDrawsInsideQuadAndFollowsRotation() {
        let spec = TextSpec(content: "ABC")
        let quad = TextRenderer.quadAt(spec, 40, 20, 20)
        let s = Stroke(id: "t", points: quad, colorHex: "#000000", brushType: .TEXT, sizePx: 40, opacity: 1, order: 0, text: spec)
        let px = pixels(300, 200) { StrokeRenderer.drawStroke($0, s) }
        let ink = inkBox(px, 300, 200)
        XCTAssertNotNil(ink)
        XCTAssertGreaterThanOrEqual(ink!.minX, 19); XCTAssertLessThanOrEqual(ink!.maxX, CGFloat(quad[1].x) + 1)
        XCTAssertGreaterThanOrEqual(ink!.minY, 19); XCTAssertLessThanOrEqual(ink!.maxY, CGFloat(quad[2].y) + 1)
        // 90° döndürülmüş dörtgen: yazı dikey uzanır
        let (w, h) = TextRenderer.measure(spec, 40)
        let rot = [StrokePoint(150, 20), StrokePoint(150, 20 + w), StrokePoint(150 - h, 20 + w), StrokePoint(150 - h, 20)]
        var r = s; r.points = rot
        let px2 = pixels(300, 200) { StrokeRenderer.drawStroke($0, r) }
        let ink2 = inkBox(px2, 300, 200)!
        XCTAssertGreaterThan(ink2.height, ink2.width)
    }

    func testBundledFontsLoad() {
        for f in TextFont.allCases where f.asset != nil {
            let (font, _, _) = FontRegistry.font(f, size: 20, bold: false, italic: false)
            let name = CTFontCopyFamilyName(font) as String
            XCTAssertFalse(name.contains("System") || name.hasPrefix("."), "\(f) yüklenmedi: \(name)")
        }
    }

    func testEffectsOutlineGradientAndCaps() {
        var spec = TextSpec(content: "istanbul")
        spec.box = TextBox(effects: TextEffects(gradient: GradientSpec(colors: ["#FF0000", "#0000FF"]), caps: true))
        XCTAssertEqual(TextRenderer.displayText(spec), "İSTANBUL")
        spec.outlineColorHex = "#00FF00"; spec.outlineWidth = 3
        let s = Stroke(id: "t", points: TextRenderer.quadAt(spec, 40, 10, 10), colorHex: "#000000", brushType: .TEXT, sizePx: 40, opacity: 1, order: 0, text: spec)
        let px = pixels(400, 120) { StrokeRenderer.drawStroke($0, s) }
        var green = 0, redOrBlue = 0
        for i in stride(from: 0, to: px.count, by: 4) where px[i + 3] > 200 {
            if px[i + 1] > 200 && px[i] < 80 { green += 1 }
            if px[i] > 150 || px[i + 2] > 150 { redOrBlue += 1 }
        }
        XCTAssertGreaterThan(green, 50); XCTAssertGreaterThan(redOrBlue, 50)
    }

    func testEveryBalloonStyleDraws() {
        var rnd = JavaRandom(31)
        XCTAssertEqual(rnd.nextFloat().bitPattern, 1060847119)
        XCTAssertEqual(rnd.nextFloat().bitPattern, 1056504238)
        for style in BalloonStyle.allCases where style != .FREE {
            var spec = TextSpec(content: "Hey!")
            spec.box = TextBox(width: 200, balloon: BalloonSpec(style: style))
            let s = Stroke(id: "b", points: TextRenderer.quadAt(spec, 30, 40, 20), colorHex: "#000000", brushType: .TEXT, sizePx: 30, opacity: 1, order: 0, text: spec)
            let px = pixels(320, 320) { StrokeRenderer.drawStroke($0, s) }
            XCTAssertNotNil(inkBox(px, 320, 320), "\(style)")
            let (shape, _) = BalloonRenderer.outline(spec.box!.balloon!, 200, 100)
            XCTAssertFalse(shape.boundingBoxOfPath.isEmpty, "\(style)")
        }
    }

    func testTextOnPathDraws() {
        var spec = TextSpec(content: "Yol üstünde yazı")
        spec.path = TextPathSpec(points: [StrokePoint(0, 80), StrokePoint(150, 0), StrokePoint(300, 80)], w: 300, h: 100)
        let s = Stroke(id: "p", points: [StrokePoint(0, 0), StrokePoint(300, 0), StrokePoint(300, 100), StrokePoint(0, 100)], colorHex: "#000000",
                       brushType: .TEXT, sizePx: 24, opacity: 1, order: 0, text: spec)
        let px = pixels(300, 120) { StrokeRenderer.drawStroke($0, s) }
        XCTAssertNotNil(inkBox(px, 300, 120))
    }
}
