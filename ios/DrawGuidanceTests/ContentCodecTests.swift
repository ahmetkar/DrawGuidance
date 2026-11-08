import XCTest
@testable import DrawGuidance

/// Android'in ürettiği belgeler iOS'ta aynı içerikle açılmalı; iOS'un yazdığı belge yine aynı içerikle okunmalı.
final class ContentCodecTests: XCTestCase {
    private func fixture(_ name: String) throws -> Data {
        let bundle = Bundle(for: Self.self)
        let url = try XCTUnwrap(bundle.url(forResource: name, withExtension: nil, subdirectory: "Fixtures")
            ?? bundle.url(forResource: name, withExtension: nil))
        return try Data(contentsOf: url)
    }

    private func strokesFromJson(_ name: String) throws -> [Stroke] {
        try JSONDecoder().decode([Stroke].self, from: fixture(name))
    }

    func testDecodesAndroidPlainDocument() throws {
        let doc = try ContentCodec.decodeDocument(fixture("plain.bin"))
        XCTAssertEqual(doc.layers, [Layer.Default, Layer(id: "l2", name: "Üst katman", visible: false, locked: true)])
        XCTAssertEqual(doc.pages.count, 2)
        // Basınç/eğim/yön kayıtta 1/255 adımla saklanır (Android'de de öyle): beklenen değerler aynı adıma yuvarlanır.
        let expected = try strokesFromJson("plain.json").map { s -> Stroke in
            guard s.points.contains(where: { $0.hasDynamics }) else { return s }
            var s = s
            s.points = s.points.map {
                StrokePoint($0.x, $0.y, pressure: ContentCodec.quantizeUnit($0.pressure), tilt: ContentCodec.quantizeUnit($0.tilt),
                            orient: ContentCodec.quantizeUnit($0.orient))
            }
            return s
        }
        XCTAssertEqual(doc.pages.flatMap { $0 }, expected)
        let t = doc.pages[0][3].text!
        XCTAssertEqual(t.content, "Merhaba ğüşİ 😀\n• madde")
        XCTAssertEqual(t.font, .CAVEAT)
        XCTAssertEqual(t.paragraph, .BULLET)
        XCTAssertEqual(doc.pages[0][1].layerId, "l2")
        XCTAssertEqual(doc.pages[0][2].order, -5)
    }

    func testDecodesAndroidFullDocument() throws {
        let doc = try ContentCodec.decodeDocument(fixture("full.bin"))
        let page = doc.pages[0]
        XCTAssertEqual(page, try strokesFromJson("full.json").enumerated().map { i, s in
            var s = s
            s.space = page[i].space
            s.model = page[i].model
            return s
        })
        XCTAssertEqual(page[0].shape?.graph?.valueLabels.map, [0: "a", 3: "b"])
        XCTAssertEqual(page[0].shape?.params, ["v": "20"])
        XCTAssertEqual(page[1].diagram?.edges.first?.toSide, .TOP)
        XCTAssertEqual(page[2].text?.box?.balloon?.style, .SHOUT)
        XCTAssertEqual(page[2].text?.path?.below, true)
        XCTAssertTrue(page[2].hidden)
        XCTAssertEqual(page[3].params?.tube?.material, .METAL)

        let parts = String(decoding: try fixture("full-space.txt"), as: UTF8.self).split(separator: "|").map(String.init)
        let sp = try XCTUnwrap(page[3].space)
        XCTAssertEqual(sp.frame, parts[0].split(separator: ",").map { Float(String($0))! })
        XCTAssertEqual(sp.local, parts[1].split(separator: ",").map { Float(String($0))! })
        XCTAssertEqual(sp.stamp, Int64(parts[2]))
        XCTAssertEqual(sp.view, Int32(parts[3]))
        XCTAssertEqual(page[4].model, Model3DLink(modelId: "m1", dataAssetId: "asset-9"))
        XCTAssertEqual(page[4].assetId, "11111111-2222-4333-8444-555555555555")
    }

    /// JSON parçası olmayan belgede iOS'un ikili gövdesi Android'inkiyle bayt bayt aynıdır.
    func testPlainBodyIsByteIdenticalToAndroid() throws {
        let android = try fixture("plain.bin")
        let doc = try ContentCodec.decodeDocument(android)
        let ios = ContentCodec.encodePages(doc.pages, layers: doc.layers)
        XCTAssertEqual(android.prefix(3), ios.prefix(3))
        XCTAssertEqual(try ContentCodec.inflate(android.dropFirst(3)), try ContentCodec.inflate(ios.dropFirst(3)))
    }

    func testRoundTripFull() throws {
        let doc = try ContentCodec.decodeDocument(fixture("full.bin"))
        let again = try ContentCodec.decodeDocument(ContentCodec.encodePages(doc.pages, layers: doc.layers))
        XCTAssertEqual(again, doc)
    }

    func testEmptyAndLegacy() throws {
        XCTAssertEqual(try ContentCodec.decode(ContentCodec.EMPTY), [])
        let legacy = ##"[{"id":"x","points":[{"x":1.0,"y":2.0}],"colorHex":"#000000","brushType":"PENCIL","sizePx":3.0,"opacity":1.0,"order":0}]"##
        XCTAssertEqual(try ContentCodec.decode(Data(legacy.utf8)).first?.points, [StrokePoint(1, 2)])
    }

    func testQuantizeMatchesJavaRounding() {
        XCTAssertEqual(ContentCodec.roundToLong(-2.5), -2)
        XCTAssertEqual(ContentCodec.roundToLong(2.5), 3)
        XCTAssertEqual(ContentCodec.quantize(1.005), Float(ContentCodec.roundToLong(1.005 * 100)) / 100)
    }
}

final class ModelHelpersTests: XCTestCase {
    func testTextListsNumbering() {
        XCTAssertEqual(TextLists.apply("a\nb\n    c", .NUMBERED), "1. a\n2. b\n    a. c")
        let e = TextLists.onEdit(old: "1. a", new: "1. a\n", cursor: 5, paragraph: .NUMBERED)
        XCTAssertEqual(e, TextLists.Edit(text: "1. a\n2. ", cursor: 8))
        XCTAssertEqual(TextLists.apply("• x", .NORMAL), "x")
    }

    func testPageSettingsLines() {
        var p = PageSettings()
        p.pageNumbers = true
        p.footer = "Alt"
        XCTAssertEqual(p.lines(page: 1, count: 3), [PageSettings.Line(top: false, align: .CENTER, size: .MEDIUM, text: "Alt   2")])
        XCTAssertEqual(p.lines(page: 0, count: 3, cover: true), [])
        p.numberFormat = .NUMBER_OF_TOTAL
        XCTAssertEqual(p.numberText(page: 2, count: 3, cover: true), "2 / 2")
    }

    func testLayersSort() {
        let layers = [Layer.Default, Layer(id: "top", name: "T")]
        func s(_ id: String, _ l: String?) -> Stroke {
            Stroke(id: id, points: [], colorHex: "#000000", brushType: .PENCIL, sizePx: 1, opacity: 1, order: 0, layerId: l)
        }
        let sorted = Layers.sort([s("a", "top"), s("b", nil), s("c", "top")], layers)
        XCTAssertEqual(sorted.map(\.id), ["b", "a", "c"])
        XCTAssertEqual(sorted.map(\.order), [0, 1, 2])
    }

    func testDiagramRoutingIsOrthogonal() {
        let a = RouteRect(left: 0, top: 0, right: 1, bottom: 1)
        let b = RouteRect(left: 3, top: 3, right: 4, bottom: 4)
        let path = DiagramRouting.route(a, b, .RIGHT, .TOP, margin: 0.1)
        XCTAssertEqual(path.first, RoutePoint(1, 0.5))
        XCTAssertEqual(path.last, RoutePoint(3.5, 3))
        for i in 1..<path.count { XCTAssertTrue(path[i].x == path[i - 1].x || path[i].y == path[i - 1].y) }
    }

    func testEditHistoryCoalesces() {
        let h = EditHistory<Int>()
        h.record(before: 0, after: 1, key: "k", now: 0)
        h.record(before: 1, after: 2, key: "k", now: 100)
        XCTAssertEqual(h.undoSize, 1)
        XCTAssertEqual(h.undo(), 0)
        XCTAssertEqual(h.redo(), 2)
    }
}
