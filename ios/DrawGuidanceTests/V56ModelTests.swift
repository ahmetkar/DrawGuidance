import XCTest
@testable import DrawGuidance

/// v56 aşama 1 (Android `V56ModelTest` ile aynı): V11 içerik biçimi, Android'le bayt uyumu, düzleştirme ve mod kuralı.
final class V56ModelTests: XCTestCase {
    private func pts(_ xy: Float...) -> [StrokePoint] {
        stride(from: 0, to: xy.count, by: 2).map { StrokePoint(xy[$0], xy[$0 + 1]) }
    }

    private func stroke(_ id: String, _ type: BrushType = .PENCIL, layer: String? = nil) -> Stroke {
        Stroke(id: id, points: pts(1, 2, 30, 40, 50, 10), colorHex: "#FF0000", brushType: type, sizePx: 6, opacity: 0.8,
               order: 0, layerId: layer)
    }

    /// Android'in (V56FixtureGen) yazdığı V11 belge.
    private let androidV11 = "REcLfVHLSsNAFL2T1sdfKHedlqQawYi0BUHU4kLcieC0nabBJI2TaW0JA36CHyDapUtXrgU3LvRb/ASdm1pFEAeG+5jLOeeescptnonlA65inqy6rH4F5lhRbXH3+TEKBKubkpX2c8wmcSyUnKCf43mYdNHHo+bOXrOFNnIZZ+hv2NgZo+86TtUxqZl0a0XKkyASpvKqjtaWdcZi+ABoPAHUW69vUHph16X7ysPWdOW9WmbAOjmmgzBRBvMkRwNZoEyKqO2i4/7TokSf2tgTXPWFRH+NHsJkJKQSRreSQ6H7LFwwq9G+dK0lnmVCVdwywPSGAVwf3lEA65bleDHk3bkW74vF+ybe9P7QMhfj/czNW+veTN8lHwlyk8dpFKph1xTrNEL9SCSB6qM/M1CGaUoOknKzF++K4wHh1Tyt2RnrbRs/i00aAOVfdjbaOQaShwkRKTFWQyn26O9SnhprbMw6nJBrRJMp+UXrVD0b48EoTAL0ezzKhKZno7RJNhUIRVlxUQN8Ag=="

    func testPlainDocumentStaysOldVersion() {
        let bytes = ContentCodec.encodePages([[stroke("a")]])
        XCTAssertLessThan(bytes[2], 11, "V11 gerekmiyorsa eski sürüm yazılır")
    }

    func testV11RoundTripKeepsEverything() throws {
        let layers = [Layer.Default, Layer(id: "l2", name: "Gölge", opacity: 0.5, blend: .MULTIPLY, alphaLock: true, clipToBelow: true)]
        let extras = DocExtras(symmetry: SymmetrySpec(kind: .RADIAL, arms: 6, cx: 100, cy: 120, angle: 15))
        var masked = stroke("m", layer: "l2")
        masked.mask = ClipMask(points: pts(0, 0, 10, 0, 10, 10), feather: 3, inverted: true)
        let image = Stroke(id: "i", points: pts(0, 0, 100, 0, 100, 50, 0, 50), colorHex: "#000000", brushType: .IMAGE, sizePx: 1,
                           opacity: 1, order: 1, assetId: "asset-1",
                           warp: ImageWarp(quad: pts(5, 5, 95, 0, 100, 55, 0, 45), wave: WaveSpec(amplitude: 4, wavelength: 20, ripple: true, fadeTo: 0.2)))
        var lasso = stroke("f", .LASSO_FILL)
        lasso.params = BrushParams(grain: GrainSpec(textureId: "paper", scale: 2, strength: 0.5, moving: false))
        let bytes = ContentCodec.encodePages([[masked, image], [lasso]], layers: layers, extras: extras)
        XCTAssertEqual(bytes[2], 11)
        let doc = try ContentCodec.decodeDocument(bytes)
        XCTAssertEqual(doc.layers, layers)
        XCTAssertEqual(doc.extras, extras)
        XCTAssertEqual(doc.pages[0][0], masked)
        XCTAssertEqual(doc.pages[0][1], image)
        XCTAssertEqual(doc.pages[1][0], lasso)
        XCTAssertTrue(ContentCodec.requiresV11(doc))
    }

    func testDecodesAndroidV11Document() throws {
        let doc = try ContentCodec.decodeDocument(Data(base64Encoded: androidV11)!)
        XCTAssertEqual(doc.layers.count, 2)
        XCTAssertEqual(doc.layers[1].opacity, 0.5)
        XCTAssertEqual(doc.layers[1].blend, .MULTIPLY)
        XCTAssertTrue(doc.layers[1].alphaLock)
        XCTAssertTrue(doc.layers[1].clipToBelow)
        XCTAssertEqual(doc.extras.symmetry, SymmetrySpec(kind: .RADIAL, arms: 6, cx: 100, cy: 120, angle: 15))
        let m = doc.pages[0][0]
        XCTAssertEqual(m.layerId, "l2")
        XCTAssertEqual(m.mask, ClipMask(points: pts(0, 0, 10, 0, 10, 10), feather: 3, inverted: true))
        let i = doc.pages[0][1]
        XCTAssertEqual(i.warp?.quad, pts(5, 5, 95, 0, 100, 55, 0, 45))
        XCTAssertEqual(i.warp?.wave, WaveSpec(amplitude: 4, wavelength: 20, ripple: true, fadeTo: 0.25))
        let f = doc.pages[1][0]
        XCTAssertEqual(f.brushType, .LASSO_FILL)
        XCTAssertEqual(f.params?.grain, GrainSpec(textureId: "paper", scale: 2, strength: 0.5, moving: false))
        XCTAssertEqual(f.params?.stampAssetId, "stamp-1")
        // iOS'un yeniden yazdığı belge aynı içeriği taşır
        let again = try ContentCodec.decodeDocument(ContentCodec.encodePages(doc.pages, layers: doc.layers, extras: doc.extras))
        XCTAssertEqual(again, doc)
    }

    func testFlattenProducesOldReadableDocument() throws {
        let layers = [Layer.Default, Layer(id: "l2", name: "Yarı saydam", opacity: 0.5, blend: .SCREEN)]
        let image = Stroke(id: "i", points: pts(0, 0, 100, 0, 100, 50, 0, 50), colorHex: "#000000", brushType: .IMAGE, sizePx: 1,
                           opacity: 1, order: 1, warp: ImageWarp(quad: pts(5, 5, 95, 0, 100, 55, 0, 45)))
        let content = ContentCodec.encodePages([[stroke("a", layer: "l2"), image, stroke("f", .LASSO_FILL)]], layers: layers,
                                               extras: DocExtras(symmetry: SymmetrySpec(kind: .VERTICAL, cx: 1, cy: 1)))
        let (same, notFlat) = V11Flatten.forPeer(content, 11)
        XCTAssertFalse(notFlat); XCTAssertEqual(same, content)
        let (flat, flattened) = V11Flatten.forPeer(content, 10)
        XCTAssertTrue(flattened)
        XCTAssertLessThanOrEqual(flat[2], 10, "düzleştirilen içerik V10 ya da eski")
        let doc = try ContentCodec.decodeDocument(flat)
        XCTAssertEqual(doc.pages[0][0].opacity, 0.8 * 0.5, accuracy: 1e-6)
        XCTAssertEqual(doc.pages[0][1].points, pts(5, 5, 95, 0, 100, 55, 0, 45))
        XCTAssertNil(doc.pages[0][1].warp)
        XCTAssertEqual(doc.pages[0][2].brushType, .SHAPE)
        XCTAssertEqual(doc.pages[0][2].shape?.style.fillColorHex, "#FF0000")
        XCTAssertTrue(doc.extras.isEmpty)
    }

    func testModeRulesMatchPlan() {
        XCTAssertTrue(V56Flags.enabled(.QUICK_BRUSH, .ART))
        XCTAssertTrue(V56Flags.enabled(.QUICK_BRUSH, .SIMPLE_ART))
        XCTAssertFalse(V56Flags.enabled(.QUICK_BRUSH, .COMIC))
        XCTAssertFalse(V56Flags.enabled(.QUICK_BRUSH, .NOTES))
        XCTAssertEqual(V56Flags.features(.KIDS), [.SYMMETRY])
        XCTAssertTrue(V56Flags.enabled(.EXPORT_SVG_PSD_DXF, .NOTES))
        XCTAssertFalse(V56Flags.enabled(.EXPORT_SVG_PSD_DXF, .KIDS))
        XCTAssertFalse(V56Flags.enabled(.LAYER_BLEND, .NOTES))
        XCTAssertTrue(V56Flags.enabled(.ADV_PATHFINDER, .ART))
        XCTAssertFalse(V56Flags.enabled(.ADV_PATHFINDER, .SIMPLE_ART))
        XCTAssertFalse(V56Flags.enabled(.ADV_PATHFINDER, .COMIC))
    }

    func testHelloCarriesCodecAndOldPeersDefaultToZero() throws {
        let h = try JSONDecoder().decode(ShareHello.self, from: Data(#"{"name":"A","platform":"android","codec":11}"#.utf8))
        XCTAssertEqual(h.codec, 11)
        let old = try JSONDecoder().decode(ShareHello.self, from: Data(#"{"name":"B","platform":"android"}"#.utf8))
        XCTAssertEqual(old.codec, 0)
    }
}
