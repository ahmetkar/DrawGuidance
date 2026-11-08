import CoreGraphics
import XCTest
@testable import DrawGuidance

/// Fırça motoru: Android ile aynı rastgelelik, her fırçanın boya bırakması, silgi ve katman kuralları.
final class BrushEngineTests: XCTestCase {

    /// y aşağı bakan bağlamda çizip önçarpımlı RGBA pikselleri döndürür.
    private func pixels(_ w: Int, _ h: Int, background: UInt32? = nil, _ body: (CGContext) -> Void) -> [UInt8] {
        var data = [UInt8](repeating: 0, count: w * h * 4)
        data.withUnsafeMutableBytes { buf in
            let c = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: Bitmaps.space,
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            c.translateBy(x: 0, y: CGFloat(h)); c.scaleBy(x: 1, y: -1)
            if let background { c.setFillColor(ArgbColor.cgColor(background)); c.fill(CGRect(x: 0, y: 0, width: w, height: h)) }
            body(c)
        }
        return data
    }

    private func alphaSum(_ px: [UInt8]) -> Int { stride(from: 3, to: px.count, by: 4).reduce(0) { $0 + Int(px[$1]) } }

    private func wave(_ n: Int = 30) -> ([Float], [Float]) {
        var xs: [Float] = [], ys: [Float] = []
        for i in 0..<n { let t = Float(i) / Float(n - 1); xs.append(20 + t * 160); ys.append(50 + sin(t * 6) * 20) }
        return (xs, ys)
    }

    func testDabRandomMatchesAndroid() {
        XCTAssertEqual(DabRandom.unit(12345, 7, 3).bitPattern, UInt32(1060692762))
        XCTAssertEqual(DabRandom.unit(-98765, 123, 52).bitPattern, UInt32(1019467424))
        XCTAssertEqual("stroke-1".javaHashCode, 1800457692)
        XCTAssertEqual("çizim-ğ".javaHashCode, 1976885006)
        XCTAssertEqual(DabRandom.unit("stroke-1".javaHashCode, 40, 101).bitPattern, UInt32(1036866360))
        XCTAssertEqual(DabRandom.unit(0, 0, 0), 0)
    }

    func testEveryBrushLeavesPaint() {
        let (xs, ys) = wave()
        var types = BrushCategory.allCases.flatMap(\.brushes)
        types = Array(Set(types)).sorted { $0.rawValue < $1.rawValue }
        XCTAssertGreaterThanOrEqual(types.count, 40)
        for t in types {
            let px = pixels(200, 100) { c in
                BrushPainter.paint(c, xs: xs, ys: ys, color: 0xFF22_66AA, type: t, sizePx: 14, opacity: 1, seed: 42, flow: 1, strength: 1)
            }
            XCTAssertGreaterThan(alphaSum(px), 255 * 20, "\(t) boya bırakmadı")
        }
    }

    func testRenderingIsDeterministic() {
        let (xs, ys) = wave()
        for t in [BrushType.TEXTURE_SPLATTER, .INK_TRAIL, .PAINT_TRAIL, .FLAT_BRUSH, .STAMP_STAR] {
            let a = pixels(200, 100) { BrushPainter.paint($0, xs: xs, ys: ys, color: 0xFF000000, type: t, sizePx: 16, opacity: 1, seed: 7, flow: 1, strength: 1) }
            let b = pixels(200, 100) { BrushPainter.paint($0, xs: xs, ys: ys, color: 0xFF000000, type: t, sizePx: 16, opacity: 1, seed: 7, flow: 1, strength: 1) }
            XCTAssertEqual(a, b, "\(t)")
        }
    }

    func testLowFlowAndOpacityLeaveLessPaint() {
        let (xs, ys) = wave()
        func sum(_ flow: Float, _ opacity: Float) -> Int {
            alphaSum(pixels(200, 100) { BrushPainter.paint($0, xs: xs, ys: ys, color: 0xFF000000, type: .PENCIL, sizePx: 14, opacity: opacity, seed: 1, flow: flow, strength: 1) })
        }
        let full = sum(1, 1)
        XCTAssertLessThan(sum(0.3, 1), full)
        XCTAssertLessThan(sum(1, 0.4), full * 6 / 10)
    }

    func testEraserRemovesEarlierInkOnSameLayerOnly() {
        let ink = Stroke(id: "a", points: [StrokePoint(10, 50), StrokePoint(190, 50)], colorHex: "#000000", brushType: .MARKER,
                         sizePx: 20, opacity: 1, order: 0)
        let eraser = Stroke(id: "b", points: [StrokePoint(100, 0), StrokePoint(100, 100)], colorHex: "#000000", brushType: .ERASER,
                            sizePx: 20, opacity: 1, order: 1)
        let px = pixels(200, 100, background: 0xFFFF_FFFF) { c in
            StrokeRenderer.drawCanvasContent(c, paper: 0xFFFF_FFFF, strokes: [ink, eraser], width: 200, height: 100)
        }
        func at(_ x: Int, _ y: Int) -> UInt8 { px[(y * 200 + x) * 4] }
        XCTAssertLessThan(at(40, 50), 40, "mürekkep görünmeli")
        XCTAssertGreaterThan(at(100, 50), 240, "silgi kâğıdı göstermeli")
        // Silgiden sonra çizilen iz silinmez
        let later = Stroke(id: "c", points: [StrokePoint(10, 50), StrokePoint(190, 50)], colorHex: "#000000", brushType: .MARKER,
                           sizePx: 20, opacity: 1, order: 2)
        let px2 = pixels(200, 100) { c in
            StrokeRenderer.drawCanvasContent(c, paper: 0xFFFF_FFFF, strokes: [ink, eraser, later], width: 200, height: 100)
        }
        XCTAssertLessThan(px2[(50 * 200 + 100) * 4], 40)
    }

    func testHighlighterMultipliesOverInk() {
        let ink = Stroke(id: "a", points: [StrokePoint(100, 0), StrokePoint(100, 100)], colorHex: "#000000", brushType: .MARKER,
                         sizePx: 10, opacity: 1, order: 0)
        let hl = Stroke(id: "b", points: [StrokePoint(10, 50), StrokePoint(190, 50)], colorHex: "#FFEB3B", brushType: .HIGHLIGHTER,
                        sizePx: 10, opacity: 1, order: 1)
        let px = pixels(200, 100) { c in
            StrokeRenderer.drawCanvasContent(c, paper: 0xFFFF_FFFF, strokes: [ink, hl], width: 200, height: 100)
        }
        let i = (50 * 200 + 100) * 4
        XCTAssertLessThan(Int(px[i]) + Int(px[i + 1]) + Int(px[i + 2]), 60, "fosforlu mürekkebi örtmemeli")
        let j = (50 * 200 + 40) * 4
        XCTAssertGreaterThan(px[j], 200); XCTAssertLessThan(px[j + 2], 200)
    }

    func testTubeAndExtrusionDrawShadowBeyondBody() {
        let pts: ([Float], [Float]) = ([40, 160], [50, 50])
        func sum(_ params: BrushParams?, _ type: BrushType) -> Int {
            alphaSum(pixels(200, 140) { BrushPainter.paint($0, xs: pts.0, ys: pts.1, color: 0xFF3366CC, type: type, sizePx: 16, opacity: 1, seed: 3, flow: 1, strength: 1, params: params) })
        }
        let flat = sum(nil, .MARKER)
        let solid = sum(BrushParams(depth3d: Depth3D(plane: .FREE, depth: 1)), .MARKER)
        XCTAssertGreaterThan(solid, flat + 255 * 100, "3B derinlik gövdeyi büyütmeli")
        let tube = sum(BrushParams(tube: TubeParams(shadow: 1)), .BRUSH_3D)
        let noShadow = sum(BrushParams(tube: TubeParams(shadow: 0)), .BRUSH_3D)
        XCTAssertGreaterThan(tube, noShadow)
    }

    func testPaintBoundsCoverRenderedPixels() {
        let (xs, ys) = wave()
        for t in [BrushType.TEXTURE_SPLATTER, .AIRBRUSH, .DRIP_TRAIL, .BRUSH_3D, .STAMP_LEAF] {
            let s = Stroke(id: "x", points: zip(xs, ys).map { StrokePoint($0, $1) }, colorHex: "#000000", brushType: t, sizePx: 14, opacity: 1, order: 0)
            let r = StrokeRenderer.paintBounds(s)!
            let px = pixels(300, 200) { c in c.translateBy(x: 50, y: 50); StrokeRenderer.drawStroke(c, s) }
            for y in 0..<200 { for x in 0..<300 where px[(y * 300 + x) * 4 + 3] > 8 {
                let p = CGPoint(x: CGFloat(x - 50) + 0.5, y: CGFloat(y - 50) + 0.5)
                XCTAssertTrue(r.insetBy(dx: -1, dy: -1).contains(p), "\(t) sınır dışında boyadı: \(p)")
                if !r.insetBy(dx: -1, dy: -1).contains(p) { return }
            } }
        }
    }
}
