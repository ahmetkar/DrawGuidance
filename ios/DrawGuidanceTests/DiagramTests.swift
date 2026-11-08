import CoreGraphics
import XCTest
@testable import DrawGuidance

/// Android `V28DiagramTest` karşılığı: her türün hazır örneği geçerli, nicel türlerde değerler yazıdan okunur, oklar
/// çizilebilir yol verir ve her tür her temada hatasız çizilir.
final class DiagramTests: XCTestCase {
    private func spec(_ kind: DiagramKind, _ theme: String = DiagramThemes.EDITORIAL) -> DiagramSpec {
        let nodes = DiagramRenderer.layout(kind, DiagramRenderer.defaultNodes(kind))
        return DiagramSpec(kind: kind, nodes: nodes, edges: DiagramRenderer.defaultEdges(kind, nodes), style: ShapeStyle(),
                           manual: DiagramCatalog.template(kind)?.manual ?? false, theme: theme)
    }

    private func stroke(_ spec: DiagramSpec, _ w: Float, _ h: Float, _ ox: Float = 0, _ oy: Float = 0) -> Stroke {
        Stroke(id: "d", points: [StrokePoint(ox, oy), StrokePoint(ox + w, oy), StrokePoint(ox + w, oy + h), StrokePoint(ox, oy + h)],
               colorHex: "#1E1E1E", brushType: .DIAGRAM, sizePx: 4, opacity: 1, order: 0, diagram: spec)
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

    private func argb(_ px: [UInt8], _ w: Int, _ x: Int, _ y: Int) -> UInt32 {
        let i = (y * w + x) * 4
        return 0xFF00_0000 | UInt32(px[i]) << 16 | UInt32(px[i + 1]) << 8 | UInt32(px[i + 2])
    }

    func testEveryKindHasAValidTemplateAndLayout() {
        for kind in DiagramKind.allCases {
            let s = spec(kind)
            XCTAssertFalse(s.nodes.isEmpty, "\(kind): adım var")
            XCTAssertEqual(s.nodes.count, Set(s.nodes.map(\.id)).count, "\(kind): kimlikler tekil")
            let ids = Set(s.nodes.map(\.id))
            for e in s.edges { XCTAssertTrue(ids.contains(e.from) && ids.contains(e.to), "\(kind): ok adımlara bağlı \(e.from)->\(e.to)") }
            for n in s.nodes {
                XCTAssertTrue(n.w > 0 && n.h > 0, "\(kind)/\(n.text): genişlik")
                XCTAssertTrue(n.x >= -0.02 && n.y >= -0.02 && n.x + n.w <= 1.02 && n.y + n.h <= 1.02,
                              "\(kind)/\(n.text): alanın içinde (\(n.x), \(n.y), \(n.w), \(n.h))")
            }
            for (i, e) in s.edges.enumerated() {
                let a = s.nodes.first { $0.id == e.from }!, b = s.nodes.first { $0.id == e.to }!
                XCTAssertGreaterThanOrEqual(DiagramRenderer.routeOf(s, i, e, a, b).count, 2, "\(kind): ok yolu")
            }
        }
        let flow = spec(.FLOW)
        XCTAssertEqual(flow.nodes.count, 4)
        XCTAssertEqual(flow.edges.count, 3)
    }

    func testValuesAreReadFromText() {
        let a = DiagramCatalog.parse("Kadro +64")
        XCTAssertEqual(a.label, "Kadro"); XCTAssertEqual(a.values, [64]); XCTAssertTrue(a.signed)
        XCTAssertTrue(DiagramCatalog.parse("Son =").computed)
        XCTAssertEqual(DiagramCatalog.parse("Tasarım 2-5").values, [2, 5])
        XCTAssertEqual(DiagramCatalog.label(.POLAR, "00–03 32"), "00–03")
        XCTAssertEqual(DiagramCatalog.label(.SCATTER, "Kampanya 78 52"), "Kampanya")
        XCTAssertEqual(DiagramCatalog.label(.RADAR, "Hız 9 6"), "Hız")
        XCTAssertEqual(DiagramCatalog.label(.WATERFALL, "Son ="), "Son")
        let wf = spec(.WATERFALL)
        XCTAssertEqual(DiagramCatalog.waterfallLevels(wf.nodes).last!.1, 276, accuracy: 0.01)
        let bars = spec(.BAR).nodes
        XCTAssertGreaterThan(bars[5].h, bars[0].h)
        XCTAssertEqual(bars[5].h / bars[0].h, 210 / 120, accuracy: 0.02)
        let g = spec(.GANTT).nodes
        XCTAssertGreaterThan(g[2].w, g[0].w, "API (4-9) Araştırma'dan (1-3) uzun")
        let sk = spec(.SANKEY)
        let ribbons = DiagramCatalog.ribbons(sk.nodes, sk.edges)
        XCTAssertEqual(ribbons.count, sk.edges.count)
        XCTAssertEqual(ribbons[0]!.h / ribbons[1]!.h, 5200 / 4000, accuracy: 0.01)
    }

    func testRenamingDataNodeRelayoutsChart() {
        let s = spec(.BAR)
        let renamed = s.nodes.map { n -> DiagramNode in var m = n; if n.id == s.nodes[0].id { m.text = "Oca 400" }; return m }
        let laid = DiagramRenderer.layout(s.kind, renamed)
        XCTAssertGreaterThanOrEqual(laid[0].h, laid.map(\.h).max()! - 1e-4, "400 en uzun çubuk")
    }

    func testDrawsEveryKindInEveryTheme() {
        _ = pixels(600, 420) { c in
            for kind in DiagramKind.allCases {
                for theme in DiagramThemes.all {
                    for labels in [true, false] {
                        c.setFillColor(ArgbColor.cgColor(0xFFFF_FFFF)); c.fill(CGRect(x: 0, y: 0, width: 600, height: 420))
                        DiagramRenderer.draw(c, stroke(spec(kind, theme.id), 580, 400, 10, 10), labels: labels)
                    }
                }
            }
        }
    }

    func testEditorialDiagramHasPaperAndAccent() {
        let px = pixels(800, 520) { c in DiagramRenderer.draw(c, stroke(spec(.BAR), 800, 520)) }
        XCTAssertEqual(argb(px, 800, 400, 6), ArgbColor.hex(DiagramThemes.editorialLight.paper), "kağıt zemin")
        let s = spec(.BAR)
        let focal = s.nodes.first { $0.role == "focal" }!
        let x = ktInt((focal.x + focal.w / 2) * 800), y = ktInt((focal.y + focal.h * 0.6) * 520)
        XCTAssertEqual(argb(px, 800, x, y), ArgbColor.hex(DiagramThemes.editorialLight.accent))
    }

    func testWideDiagramKeepsDotsRound() {
        let s = spec(.QUADRANT)
        XCTAssertEqual(DiagramRenderer.aspectK(stroke(s, 1200, 400)), 1 / 3, accuracy: 0.01)
        XCTAssertEqual(DiagramRenderer.aspectK(stroke(s, 500, 500)), 1, accuracy: 0.01)
    }

    func testEditorialIsDefaultAndHasNoShadow() {
        let t = DiagramThemes.find(DiagramThemes.EDITORIAL)
        XCTAssertNotNil(t)
        XCTAssertFalse(t!.shadow)
        XCTAssertTrue(DiagramThemes.isEditorial(DiagramThemes.EDITORIAL_DARK))
        XCTAssertEqual(DiagramThemes.all.first?.id, DiagramThemes.EDITORIAL)
    }

    func testNewStepsInheritStructureAndValues() {
        XCTAssertEqual(DiagramCatalog.inheritRole("stage"), "stage")
        XCTAssertNil(DiagramCatalog.inheritRole("focal"))
        XCTAssertEqual(DiagramCatalog.newNodeText(.BAR, 7, "Haz 210"), "Yeni 210")
        XCTAssertEqual(DiagramCatalog.newNodeText(.GANTT, 7, "Yayın 11-12"), "Yeni iş 13-14")
        XCTAssertEqual(DiagramCatalog.newNodeText(.FLOW, 3, "Karar"), "Adım 3")
        let s = spec(.HEATMAP)
        var extra = s.nodes.last!
        extra.id = "x"; extra.text = "9"; extra.role = nil; extra.box = .CELL
        let laid = DiagramRenderer.layout(s.kind, s.nodes + [extra])
        XCTAssertLessThanOrEqual(laid.last!.y + laid.last!.h, 1.01)
    }

    func testTurkishNumberFormat() {
        XCTAssertEqual(DiagramPainter.fmt(1200), "1.200")
        XCTAssertEqual(DiagramPainter.fmt(2.5), "2,5")
        XCTAssertEqual(DiagramPainter.fmt(-38), "-38")
        XCTAssertEqual(DiagramPainter.fmt(12345.67), "12.345,7")
    }

    func testCornerRoundingKeepsEnds() {
        let p = APath()
        p.moveTo(0, 0); p.lineTo(100, 0); p.lineTo(100, 100)
        let r = p.rounded(14)
        XCTAssertEqual(r.currentPoint, CGPoint(x: 100, y: 100))
        XCTAssertTrue(r.boundingBox.contains(CGPoint(x: 50, y: 0)))
    }
}
