import XCTest
@testable import DrawGuidance

/// v56 aşama 3B (Android `V56LiveDiffTest` ile aynı): canlı yayının fark paketi.
final class V56LiveDiffTests: XCTestCase {
    private func line(_ id: String, _ x: Float, _ color: String = "#1E1E1E") -> Stroke {
        Stroke(id: id, points: [StrokePoint(x, 10), StrokePoint(x + 50, 60)], colorHex: color, brushType: .MARKER, sizePx: 6, opacity: 1, order: 0)
    }

    func testDiffApplyEqualsNext() {
        let a = line("a", 0), b = line("b", 10), c = line("c", 20), d = line("d", 30)
        var b2 = b; b2.colorHex = "#FF0000"
        let cases: [([Stroke], [Stroke])] = [([a, b], [a, b, c]), ([a, b, c], [a, c]), ([a, b], [a, b2]), ([a, b, c], [c, a, b]), ([a, b], [a, d, b]), ([a, b], [a, b])]
        for (prev, next) in cases { XCTAssertEqual(LiveDiff.apply(prev, LiveDiff.diff(prev, next)), next) }
        XCTAssertTrue(LiveDiff.diff([a, b], [a, b]).isEmpty)
        XCTAssertNil(LiveDiff.diff([a, b], [a, b, c]).order)
        XCTAssertNotNil(LiveDiff.diff([a, b], [a, d, b]).order)
    }

    @MainActor
    func testDiffPackageMatchesFullPackageOnReceiver() throws {
        func device() -> SharePackages {
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            return SharePackages(store: CanvasStore(directory: dir.appendingPathComponent("canvases")),
                                 assets: CanvasAssetStore(directory: dir.appendingPathComponent("assets")))
        }
        let sender = device(), receiver = device()
        var first = sender.store.create(kind: .ART, name: "Yayın")
        first.pages = [[line("a", 0), line("b", 40)], [line("p2", 5)]]
        sender.store.save(first)
        var sent = Set<String>()
        let start = sender.livePackage(first, id: "o", seq: 1, sent: &sent, from: "iPad")
        var ids: [String: String] = [:]
        let localId = try receiver.install(SharePackage.decode(ShareWire.decode(start.encode(ShareWire.LIVE_UPDATE))), ids: &ids)
        var live = try XCTUnwrap(receiver.store.getCanvas(localId))

        var next = first
        next.pages[0] = [line("a", 0, "#FF0000"), line("b", 40), line("c", 80)]
        XCTAssertEqual(LiveDiff.changedPage(first, next), 0)
        let d = LiveDiff.diff(first.pages[0], next.pages[0])
        XCTAssertEqual(d.upserts.map(\.id), ["a", "c"])
        let pkg = sender.liveDiffPackage(next, page: 0, diff: d, id: "o", seq: 2, base: 1, sent: &sent, from: "iPad")
        let decoded = try SharePackage.decode(ShareWire.decode(pkg.encode(ShareWire.LIVE_DIFF)))
        XCTAssertEqual(decoded.header.diffBase, 1)
        live = try receiver.applyLiveDiff(decoded, ids: &ids, current: live)
        XCTAssertEqual(live.pages, next.pages)
        receiver.persistLive(live)
        XCTAssertEqual(receiver.store.getCanvas(localId)?.pages, next.pages)

        var paged = next; paged.currentPage = 1
        XCTAssertNil(LiveDiff.changedPage(next, paged))
        var both = next; both.pages[1] = []
        XCTAssertNil(LiveDiff.changedPage(first, both))
        XCTAssertEqual(LiveDiff.changedPage(next, next), -1)
    }
}
