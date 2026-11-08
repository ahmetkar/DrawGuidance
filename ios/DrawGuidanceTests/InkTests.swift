import XCTest
@testable import DrawGuidance

/// El yazısı tanıma ve çizgi düzeltme (Android sonuçlarıyla aynı: bkz. altın testler).
final class InkTests: XCTestCase {
    private let tutorial = InkSynth.Options(slantDeg: 6, rotDeg: 2, minH: 60, maxH: 61, delayMarks: 0, warp: 2.5)

    func testNetLoads() {
        XCTAssertNotNil(HandwritingNet.active, "notes_text.bin uygulamayla gelmeli")
    }

    func testRecognizesTurkishWords() {
        for (text, seed) in [("ev", 1), ("güneş", 2), ("İstanbul", 1), ("12+5", 3), ("hello", 2)] {
            let ink = InkSynth.sample(text, InkSynth.Rng(seed), tutorial)
            XCTAssertEqual(Handwriting.recognize(ink, language: "tr")?.text, text, text)
        }
    }

    func testDrawingIsNotWriting() {
        var pts: [InkPoint] = []
        for i in 0...200 { let a = Double(i) * 0.25; let r = 5 + Double(i) * 0.8; pts.append(InkPoint(Float(300 + r * cos(a)), Float(300 + r * sin(a)), Int64(i * 8))) }
        XCTAssertNil(Handwriting.recognize([InkStroke(pts)]))
    }

    func testShapes() {
        var circle: [InkPoint] = []
        for i in 0...64 { let a = Double(i) / 64 * 2 * Double.pi * 1.03; circle.append(InkPoint(Float(200 + 80 * cos(a)), Float(200 + 80 * sin(a)))) }
        XCTAssertEqual(InkShapes.recognize(circle)?.kind, .CIRCLE)
        let line = (0...30).map { InkPoint(Float(10 + $0 * 6), Float(50) + Float($0 % 2) * 0.8) }
        let l = InkShapes.recognize(line)
        XCTAssertEqual(l?.kind, .LINE)
        XCTAssertEqual(l?.points.first?.y, l?.points.last?.y)
    }

    func testBoxCornersSnap() {
        let c: [(Float, Float)] = [(100, 100), (300, 102), (302, 300), (98, 298)]
        var items: [InkLines.Item] = []
        for k in 0..<4 {
            let a = c[k], b = c[(k + 1) % 4]
            let pts = (0...20).map { i -> InkPoint in let f = Float(i) / 20; return InkPoint(a.0 + (b.0 - a.0) * f + (k == 0 ? -5 : 4), a.1 + (b.1 - a.1) * f) }
            let sh = InkShapes.recognize(pts)
            items.append(InkLines.Item(sh?.kind, sh?.points ?? pts))
        }
        let r = InkLines.snapEndpoints(items)
        XCTAssertEqual(r.joints, 4)
        for k in 0..<4 { XCTAssertEqual(r.items[k].points.last, r.items[(k + 1) % 4].points.first) }
    }
}
