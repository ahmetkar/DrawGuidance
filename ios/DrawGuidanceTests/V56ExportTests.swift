import XCTest
@testable import DrawGuidance

/// v56 aşama 9: SVG, PSD ve DXF yazıcıları (Android V56ExportRulesTest ile aynı beklentiler).
final class V56ExportTests: XCTestCase {
    private let layers = [Layer(id: "base", name: "Çizgi"), Layer(id: "l2", name: "Gölge", opacity: 0.5, blend: .MULTIPLY), Layer(id: "l3", name: "Gizli", visible: false)]
    private func rect(_ id: String, _ layer: String) -> Stroke {
        Stroke(id: id, points: [StrokePoint(10, 10), StrokePoint(90, 10), StrokePoint(90, 60), StrokePoint(10, 60)], colorHex: "#000000",
               brushType: .SHAPE, sizePx: 4, opacity: 1, order: 0,
               shape: ShapeSpec(kind: "rect", subpaths: [SubPath(count: 4, closed: true)], style: ShapeStyle(strokeColorHex: "#FF112233", strokeWidth: 3, fillColorHex: "#00FF00")),
               layerId: layer)
    }
    private func pen(_ id: String, _ layer: String) -> Stroke {
        Stroke(id: id, points: [StrokePoint(0, 0), StrokePoint(50.125, 20)], colorHex: "#FF0000", brushType: .INK_PEN, sizePx: 5, opacity: 1, order: 1, layerId: layer)
    }
    private var doc: ExportDoc {
        let sponge = Stroke(id: "s", points: [StrokePoint(0, 0), StrokePoint(5, 5)], colorHex: "#0000FF", brushType: .TEXTURE_SPONGE, sizePx: 20, opacity: 1, order: 2, layerId: "base")
        let text = Stroke(id: "t", points: [StrokePoint(20, 30), StrokePoint(120, 30), StrokePoint(120, 60), StrokePoint(20, 60)], colorHex: "#000000",
                          brushType: .TEXT, sizePx: 24, opacity: 1, order: 3, text: TextSpec(content: "Merhaba <dünya>"), layerId: "l2")
        return ExportDoc(width: 200, height: 100, paperHex: "#FFFFFF", layers: layers, strokes: [rect("a", "base"), sponge, pen("p", "l2"), text, pen("h", "l3")], title: "Deneme")
    }

    func testNumberFormat() {
        XCTAssertEqual(ExportNum.f(Float(1.5)), "1.5"); XCTAssertEqual(ExportNum.f(Float(2)), "2"); XCTAssertEqual(ExportNum.f(0.05), "0.05")
        XCTAssertEqual(ExportNum.f(-3.25), "-3.25"); XCTAssertEqual(ExportNum.f(50.125), "50.13"); XCTAssertEqual(ExportNum.f(-0.001), "0")
    }

    func testSvg() {
        var runs = 0
        let svg = SvgWriter.write(doc) { run in runs += 1; XCTAssertEqual(run.map(\.id), ["s"]); return Data([1, 2, 3]) }
        XCTAssertEqual(runs, 1)
        XCTAssertTrue(svg.contains("inkscape:label=\"Gölge\" opacity=\"0.5\" style=\"mix-blend-mode:multiply\""))
        XCTAssertFalse(svg.contains("Gizli"))
        XCTAssertTrue(svg.contains("<path d=\"M10 10 L90 10 L90 60 L10 60 Z\" fill=\"#00FF00\" stroke=\"#112233\" stroke-width=\"3\""))
        XCTAssertTrue(svg.contains("M0 0 L50.13 20"))
        XCTAssertTrue(svg.contains("data:image/png;base64,AQID"))
        XCTAssertTrue(svg.contains("Merhaba &lt;dünya&gt;"))
    }

    func testDxf() {
        let r = DxfWriter.write(doc)
        XCTAssertEqual(r.skipped, 1)
        XCTAssertTrue(r.text.contains("\n2\nCIZGI_1\n")); XCTAssertTrue(r.text.contains("\n2\nGOLGE_2\n"))
        XCTAssertEqual(r.text.components(separatedBy: "\nPOLYLINE\n").count - 1, 3)
        XCTAssertTrue(r.text.contains("\n10\n2.65\n20\n23.81\n"))
        XCTAssertTrue(r.text.contains("\n62\n-"))
    }

    func testPsd() {
        XCTAssertEqual(PsdWriter.packBits([5, 5, 5, 5, 1, 2, 3]), [253, 5, 2, 1, 2, 3])
        let px = [UInt32](repeating: 0xFF336699, count: 12)
        let data = PsdWriter.write(4, 3, [PsdWriter.PsdLayer(name: "Arka plan", pixels: px),
                                          PsdWriter.PsdLayer(name: "Gölge", pixels: [UInt32](repeating: 0, count: 12), visible: false, opacity: 0.5, blend: .MULTIPLY, clipping: true, alphaLock: true)], px)
        let info = PsdWriter.readInfo(data)!
        XCTAssertEqual(info.width, 4); XCTAssertEqual(info.height, 3)
        XCTAssertEqual(info.layers.map(\.0), ["Arka plan", "G_lge"])
        XCTAssertEqual(info.layers[1].1, "mul "); XCTAssertEqual(info.layers[1].2, 128)
        XCTAssertEqual(info.flags[1], 3); XCTAssertEqual(info.clipping[1], 1)
    }
}
