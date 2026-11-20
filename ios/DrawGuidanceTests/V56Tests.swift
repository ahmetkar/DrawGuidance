import XCTest
@testable import DrawGuidance

/// v56 canlı yayın düzeltmeleri (Android `V56LiveShareTest` ile aynı): ara-ver sahipleri, şifre kapısı, onay kuralları,
/// canlı mürekkep.
final class V56Tests: XCTestCase {

    // MARK: Ara-ver sahipleri

    @MainActor
    func testReopenedEditorIsNotPausedByOldEditorsLateDispose() {
        let m = ShareManager.shared, c = "v56-" + UUID().uuidString
        m.attachEditor(c, owner: "A")
        XCTAssertFalse(m.shouldPause(c))
        m.attachEditor(c, owner: "B")
        m.detachEditor(c, owner: "A")
        XCTAssertFalse(m.shouldPause(c), "eski düzenleyicinin kapanışı yayını durdurmamalı")
        m.detachEditor(c, owner: "B")
    }

    @MainActor
    func testPauseResumeDisposeSequenceKeepsBroadcastRunning() {
        let m = ShareManager.shared, c = "v56-" + UUID().uuidString
        m.attachEditor(c, owner: "A")
        m.pauseLive(c, owner: "A")
        XCTAssertTrue(m.shouldPause(c))
        m.attachEditor(c, owner: "B")
        m.resumeLive(c, owner: "B")
        XCTAssertTrue(m.shouldPause(c), "B, A'nın arasını kaldıramaz")
        m.detachEditor(c, owner: "A")
        XCTAssertFalse(m.shouldPause(c))
        m.detachEditor(c, owner: "B")
    }

    @MainActor
    func testNoEditorMeansPausedAndReasonOwnersAreScoped() {
        let m = ShareManager.shared, c = "v56-" + UUID().uuidString
        XCTAssertTrue(m.shouldPause(c))
        m.attachEditor(c, owner: "A")
        m.pauseLive(c, owner: "A:photo")
        XCTAssertTrue(m.shouldPause(c))
        m.resumeLive(c, owner: "A:photo")
        XCTAssertFalse(m.shouldPause(c))
        m.pauseLive(c, owner: "A:photo")
        m.detachEditor(c, owner: "A")
        m.attachEditor(c, owner: "B")
        XCTAssertFalse(m.shouldPause(c), "ayrılan düzenleyicinin nedenli araları da silinir")
        XCTAssertTrue(m.isOpenInEditor(c))
        XCTAssertFalse(m.isOpenInEditor("x-" + c))
        m.detachEditor(c, owner: "B")
    }

    // MARK: Şifre kapısı ve onay kuralları

    func testPairingCodeIsNotGeneratedBeforeApproval() {
        var generated = 0
        let gate = PairingCodeGate(known: false) { generated += 1; return "123456" }
        XCTAssertNil(gate.code)
        XCTAssertEqual(generated, 0, "onaydan önce şifre üretilmemeli")
        XCTAssertEqual(gate.approve(), "123456")
        gate.approve()
        XCTAssertEqual(generated, 1)
    }

    func testKnownDeviceNeedsApprovalButNoCode() {
        let gate = PairingCodeGate(known: true) { XCTFail("eşleşmiş cihazda şifre üretilmemeli"); return "" }
        XCTAssertNil(gate.approve())
        XCTAssertTrue(gate.approved)
    }

    func testThreeRejectionsInAMinuteBlockForFiveMinutes() {
        var now: Int64 = 0
        let p = LiveApprovalPolicy { now }
        p.onRejected("k"); now += 10_000
        p.onRejected("k"); now += 10_000
        XCTAssertFalse(p.isBlocked("k"))
        p.onRejected("k")
        XCTAssertTrue(p.isBlocked("k"))
        now += LiveApprovalPolicy.BLOCK_MS - 1
        XCTAssertTrue(p.isBlocked("k"))
        now += 2
        XCTAssertFalse(p.isBlocked("k"))
    }

    func testSpreadOutRejectionsDoNotBlock() {
        var now: Int64 = 0
        let p = LiveApprovalPolicy { now }
        for _ in 0..<5 { p.onRejected("k"); now += 40_000 }
        XCTAssertFalse(p.isBlocked("k"))
    }

    func testApprovalIsUsedOnceAndExpires() {
        var now: Int64 = 0
        let p = LiveApprovalPolicy { now }
        p.onApproved("k")
        XCTAssertTrue(p.consumeApproval("k"))
        XCTAssertFalse(p.consumeApproval("k"), "onay bir kez kullanılır")
        p.onApproved("k")
        now += LiveApprovalPolicy.APPROVAL_VALID_MS + 1
        XCTAssertFalse(p.consumeApproval("k"))
    }

    // MARK: Canlı mürekkep

    func testLiveInkRoundTripAndMerge() throws {
        let first = ShareInk(stroke: "s1", brush: "PENCIL", color: "#FF0000", size: 6, opacity: 1, from: 0, pts: [1, 2, 1, 3, 4, 0.5])
        let m = try ShareWire.decode(ShareWire.encode(ShareWire.LIVE_INK, first))
        XCTAssertEqual(m.type, ShareWire.LIVE_INK)
        let back = try m.header(ShareInk.self)
        XCTAssertEqual(back, first)
        let a = LiveInk.merge(nil, back)
        XCTAssertEqual(a.pts.count, 6)
        let b = LiveInk.merge(a, ShareInk(stroke: "s1", brush: "PENCIL", color: "#FF0000", size: 6, from: 1, pts: [3, 4, 0.5, 5, 6, 1]))
        XCTAssertEqual(b.pts, [1, 2, 1, 3, 4, 0.5, 5, 6, 1])
        let c = LiveInk.merge(b, ShareInk(stroke: "s1", brush: "PENCIL", color: "#FF0000", size: 6, from: 9, pts: [9, 9, 1]))
        XCTAssertEqual(c.pts.count, 12)
    }

    /// Android'in gönderdiği JSON (eksik alanlar varsayılanla) çözülür.
    func testAndroidInkJsonDecodes() throws {
        let json = #"{"stroke":"x","brush":"PEN","color":"#112233","size":3.5,"pts":[1.0,2.0,1.0]}"#
        let ink = try JSONDecoder().decode(ShareInk.self, from: Data(json.utf8))
        XCTAssertEqual(ink.from, 0)
        XCTAssertEqual(ink.page, -1)
        XCTAssertEqual(ink.opacity, 1)
    }
}
