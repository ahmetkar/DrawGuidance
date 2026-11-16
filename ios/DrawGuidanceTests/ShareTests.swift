import XCTest
@testable import DrawGuidance

/// v51 cihazlar arası paylaşım: Android ile aynı mesaj baytları, parçalama ve tuval paketinin başka cihaza kurulması.
final class ShareTests: XCTestCase {
    private func hex(_ d: Data) -> String { d.map { String(format: "%02x", $0) }.joined() }

    func testMessageLayoutMatchesAndroidFixture() throws {
        // Android ShareProtocolTest aynı baytları bekler: [tür][u32 BE JSON uzunluğu][JSON][kuyruk]
        let bytes = ShareWire.encode(ShareWire.HELLO, Data("{\"v\":1}".utf8), [Data([9, 8])])
        XCTAssertEqual(hex(bytes), "01000000077b2276223a317d0908")
        let m = try ShareWire.decode(bytes)
        XCTAssertEqual(m.type, ShareWire.HELLO)
        XCTAssertEqual(String(data: m.json, encoding: .utf8), "{\"v\":1}")
        XCTAssertEqual(m.tail, Data([9, 8]))
    }

    func testHelloIgnoresUnknownFields() throws {
        let json = Data("{\"v\":1,\"name\":\"Tablet\",\"platform\":\"android\",\"app\":\"1.9.0\",\"extra\":true}".utf8)
        let h = try ShareWire.decode(ShareWire.encode(ShareWire.HELLO, json)).header(ShareHello.self)
        XCTAssertEqual(h.name, "Tablet")
        XCTAssertEqual(h.platform, "android")
        // Android'in varsayılanları yazmadığı alanlar eksik olabilir
        let o = try JSONDecoder().decode(ShareOffer.self, from: Data("{\"id\":\"x\",\"type\":\"canvas\"}".utf8))
        XCTAssertEqual(o.bytes, 0)
        XCTAssertEqual(o.title, "")
    }

    func testChunkerSplitsAndJoinsOutOfOrder() {
        let sender = ShareChunker(chunkSize: 1000), receiver = ShareChunker(chunkSize: 1000)
        let message = Data((0..<4321).map { UInt8(truncatingIfNeeded: $0 * 7) })
        let parts = sender.split(message)
        XCTAssertEqual(parts.count, 5)
        XCTAssertEqual(hex(parts[0].prefix(12)), "000000010000000000000005")
        var whole: Data?
        for p in parts.reversed() { whole = receiver.accept(p) ?? whole }
        XCTAssertEqual(whole, message)
        XCTAssertEqual(receiver.accept(sender.split(Data([1, 2, 3]))[0]), Data([1, 2, 3]))
        XCTAssertNil(receiver.accept(Data([0, 0, 0, 1])))
    }

    func testAndroidBackgroundFormatRoundTrip() {
        var settings = PageSettings()
        settings.pageNumbers = true
        let text = SharePackages.encodeBackground(BackgroundSpec(), [nil, nil], settings)
        XCTAssertFalse(text.contains("\"pages\""))
        XCTAssertTrue(text.contains("\"pageSettings\""))
        XCTAssertEqual(SharePackages.decodeBackground(text).settings, settings)
        XCTAssertEqual(SharePackages.encodeBackground(BackgroundSpec(), [], PageSettings()), "")
    }

    @MainActor
    func testCanvasPackageInstallsOnOtherDeviceWithNewAssetIds() throws {
        func device() -> SharePackages {
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            return SharePackages(store: CanvasStore(directory: dir.appendingPathComponent("canvases")),
                                 assets: CanvasAssetStore(directory: dir.appendingPathComponent("assets")))
        }
        let sender = device(), receiver = device()
        var c = sender.store.create(kind: .NOTES, name: "Ders notu")
        let assetId = "11111111-2222-3333-4444-555555555555"
        // v54 alıcı yalnız gerçek resim kabul eder: 1×1 PNG
        let pngBytes = Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, 0xC4, 0x89, 0, 0, 0, 0x0D, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9C, 0x63, 0xF8, 0xCF, 0xC0, 0xF0, 0x1F, 0, 5, 0, 1, 0xFF, 0x89, 0x99, 0x3D, 0x1D, 0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82])
        sender.assets.saveData(c.localId, pngBytes, "image/png", id: assetId, width: 1, height: 1)
        let image = Stroke(id: "img", points: [StrokePoint(0, 0), StrokePoint(40, 0), StrokePoint(40, 40), StrokePoint(0, 40)],
                           colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 0, assetId: assetId)
        let line = Stroke(id: "line", points: [StrokePoint(10, 10), StrokePoint(200, 220)], colorHex: "#1E1E1E", brushType: .MARKER,
                          sizePx: 6, opacity: 1, order: 1)
        c.pages = [[image, line], []]
        c.backgroundColorHex = "#FFFDF5"
        sender.store.save(c)

        let pkg = try XCTUnwrap(sender.canvasPackage(c.localId, type: ShareWire.TYPE_CANVAS, id: "offer-1", from: "iPad"))
        let decoded = try SharePackage.decode(ShareWire.decode(pkg.encode(ShareWire.PACKAGE)))
        XCTAssertEqual(decoded.header.name, "Ders notu")
        XCTAssertEqual(decoded.assets.count, 1)

        var ids: [String: String] = [:]
        let localId = try receiver.install(decoded, ids: &ids)
        XCTAssertNotEqual(localId, c.localId)
        let got = try XCTUnwrap(receiver.store.getCanvas(localId))
        XCTAssertEqual(got.kind, .NOTES)
        XCTAssertEqual(got.backgroundColorHex, "#FFFDF5")
        XCTAssertEqual(got.pageCount, 2)
        let newAsset = try XCTUnwrap(ids[assetId])
        XCTAssertNotEqual(newAsset, assetId)
        XCTAssertEqual(got.pages[0].first { $0.brushType == .IMAGE }?.assetId, newAsset)
        XCTAssertEqual(receiver.assets.bytes(newAsset), pngBytes)

        // Yayın güncellemesi aynı tuvali günceller; gönderilmiş resim yeniden gönderilmez
        var sent = Set(decoded.header.assets.map(\.id))
        var edited = c
        edited.pages[0].append(Stroke(id: "line2", points: line.points, colorHex: "#1E1E1E", brushType: .MARKER, sizePx: 6, opacity: 1, order: 2))
        let live = sender.livePackage(edited, id: "offer-1", seq: 1, sent: &sent, from: "iPad")
        XCTAssertTrue(live.header.assets.isEmpty)
        _ = try receiver.install(SharePackage.decode(ShareWire.decode(live.encode(ShareWire.LIVE_UPDATE))), ids: &ids, existingId: localId)
        let updated = try XCTUnwrap(receiver.store.getCanvas(localId))
        XCTAssertEqual(updated.pages[0].count, 3)
        XCTAssertEqual(updated.pages[0].first { $0.brushType == .IMAGE }?.assetId, newAsset)
        XCTAssertEqual(updated.createdAt, got.createdAt)
    }
}
