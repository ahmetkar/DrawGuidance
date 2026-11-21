import XCTest
@testable import DrawGuidance

/// v56 aşama 4: hızlı fırça listeleri, mod başına kayıt, yerleşim ve canlı renk dairesi (Android V56QuickBrushRulesTest ile aynı).
final class V56QuickBrushTests: XCTestCase {
    func testRecentKeepsSixMostRecentWithoutEraser() {
        var l = QuickBrushLists()
        for t: BrushType in [.PENCIL, .MARKER, .INK_PEN, .PASTEL, .CHARCOAL, .OIL_PAINT, .AIRBRUSH] { l = l.used(t) }
        XCTAssertEqual(l.recent.count, 6)
        XCTAssertEqual(l.recent.first, .AIRBRUSH)
        XCTAssertFalse(l.recent.contains(.PENCIL))
        l = l.used(.PASTEL).used(.ERASER).used(.IMAGE)
        XCTAssertEqual(l.recent.first, .PASTEL)
        XCTAssertEqual(l.recent.count, 6)
        XCTAssertFalse(l.recent.contains(.ERASER))
        XCTAssertEqual(l.previous(.PASTEL), .AIRBRUSH)
    }

    func testFavoritesPinUnpinMaxSix() {
        var l = QuickBrushLists()
        let six: [BrushType] = [.PENCIL, .MARKER, .INK_PEN, .PASTEL, .CHARCOAL, .OIL_PAINT]
        for t in six { l = l.toggleFavorite(t).0 }
        XCTAssertEqual(l.favorites, six)
        let (same, ok) = l.toggleFavorite(.AIRBRUSH)
        XCTAssertFalse(ok)
        XCTAssertEqual(same.favorites, six)
        l = l.toggleFavorite(.MARKER).0
        XCTAssertFalse(l.favorites.contains(.MARKER))
        l = l.used(.MARKER).used(.PENCIL)
        XCTAssertEqual(l.strip(), l.favorites + [.MARKER])
    }

    func testEncodeMatchesAndroidAndStoreIsPerMode() {
        let l = QuickBrushLists(recent: [.PENCIL, .MARKER], favorites: [.INK_PEN])
        XCTAssertEqual(l.encode(), "recent=PENCIL,MARKER;fav=INK_PEN")
        XCTAssertEqual(QuickBrushLists.decode(l.encode()), l)
        XCTAssertEqual(QuickBrushLists.decode("recent=PENCIL,NOPE;fav="), QuickBrushLists(recent: [.PENCIL]))
        XCTAssertEqual(QuickBrushLists.decode(nil), QuickBrushLists())
    }

    @MainActor
    func testStoreKeepsSeparateListsPerMode() {
        let d = UserDefaults(suiteName: "v56quick-\(UUID().uuidString)")!
        let s = QuickBrushStore(defaults: d)
        s.bind(.ART); s.used(.MARKER)
        s.bind(.SIMPLE_ART); s.used(.PENCIL)
        XCTAssertEqual(QuickBrushLists.decode(d.string(forKey: "brushQuick.ART")).recent, [.MARKER])
        XCTAssertEqual(QuickBrushLists.decode(d.string(forKey: "brushQuick.SIMPLE_ART")).recent, [.PENCIL])
        s.bind(.ART)
        XCTAssertEqual(s.lists.recent, [.MARKER])
        s.setCorner(.BOTTOM_END)
        let again = QuickBrushStore(defaults: d)
        again.bind(.ART)
        XCTAssertEqual(again.corner, .BOTTOM_END)
    }

    func testLayoutRailInsetCornersAndRtl() {
        XCTAssertEqual(QuickBrushLayout.railTopInset(landscape: true, quickCorner: .TOP_START, rtl: false), QuickBrushLayout.SLOT)
        XCTAssertEqual(QuickBrushLayout.railTopInset(landscape: true, quickCorner: .TOP_START, rtl: true), QuickBrushLayout.SLOT)
        XCTAssertNil(QuickBrushLayout.railTopInset(landscape: true, quickCorner: .TOP_END, rtl: false))
        XCTAssertNil(QuickBrushLayout.railTopInset(landscape: false, quickCorner: .TOP_START, rtl: false))
        XCTAssertGreaterThanOrEqual(QuickBrushLayout.SLOT, QuickBrushLayout.BUTTON + 8)
        XCTAssertEqual(QuickBrushLayout.pushBelowSlot(20, .TOP_START), QuickBrushLayout.SLOT)
        let ltr = QuickBrushLayout.physical(.TOP_START, rtl: false), rtl = QuickBrushLayout.physical(.TOP_START, rtl: true)
        XCTAssertFalse(ltr.right); XCTAssertTrue(rtl.right)
        XCTAssertEqual(QuickBrushLayout.nearest(900, 700, 1000, 800, rtl: false), .BOTTOM_END)
        XCTAssertEqual(QuickBrushLayout.nearest(900, 700, 1000, 800, rtl: true), .BOTTOM_START)
        XCTAssertGreaterThan(QuickBrushLayout.dodge(30, 30, 30, 30, 22), 0)
        XCTAssertEqual(QuickBrushLayout.dodge(300, 300, 30, 30, 22), 0)
    }

    func testLoupeStaysOnScreen() {
        XCTAssertTrue(EyedropLoupe.center(200, 200, 1000, 1000, 40) == (200, 140))
        XCTAssertTrue(EyedropLoupe.center(200, 20, 1000, 1000, 40) == (200, 80))
        XCTAssertTrue(EyedropLoupe.center(5, 200, 1000, 1000, 40) == (40, 140))
        XCTAssertGreaterThan(EyedropLoupe.radius(kids: true), EyedropLoupe.radius(kids: false))
        XCTAssertFalse(EyedropLoupe.inside(-1, 10, 100, 100))
    }

    func testFlags() {
        XCTAssertTrue(V56Flags.enabled(.QUICK_BRUSH, .ART))
        XCTAssertTrue(V56Flags.enabled(.QUICK_BRUSH, .SIMPLE_ART))
        for k: CanvasKind in [.NOTES, .COMIC, .KIDS] { XCTAssertFalse(V56Flags.enabled(.QUICK_BRUSH, k)) }
        XCTAssertTrue(V56Flags.enabled(.EYEDROPPER_LOUPE, .KIDS))
    }
}
