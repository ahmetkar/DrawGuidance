import XCTest
@testable import DrawGuidance

/// v53: ekran tonları ve desenler, yapı kılavuzları, foto düzenleme (HSL, renk dengesi, kırpma maskesi), balon yazısı.
final class V53FeaturesTests: XCTestCase {

    func testTonesAreDeterministicAndBounded() {
        XCTAssertEqual(ComicTones.TONES.count, 10)
        XCTAssertEqual(ComicTones.PATTERNS.count, 14)
        for p in ComicTones.ALL {
            let a = ComicTones.prims(p.id, density: p.density, scale: p.scale, angle: p.angle, 0, 0, 600, 400)
            let b = ComicTones.prims(p.id, density: p.density, scale: p.scale, angle: p.angle, 0, 0, 600, 400)
            XCTAssertFalse(a.isEmpty, p.key)
            XCTAssertEqual(a.count, b.count, p.key)
            let big = ComicTones.prims(p.id, density: p.density, scale: 0.3, angle: p.angle, 0, 0, 6000, 9000)
            XCTAssertLessThanOrEqual(big.count, ComicTones.MAX_PRIMS * 3, p.key)
        }
    }

    func testToneFillRoundTripsThroughJson() throws {
        let tone = ToneFill(id: "pat_flowers", density: 0.6, scale: 1.5, angle: 30, colorHex: "#E91E63")
        let style = ShapeStyle(tone: tone)
        let data = try JSONEncoder().encode(style)
        XCTAssertEqual(try JSONDecoder().decode(ShapeStyle.self, from: data).tone, tone)
        let plain = try JSONEncoder().encode(ShapeStyle())
        XCTAssertFalse(String(decoding: plain, as: UTF8.self).contains("tone"))
        // Android'in yazdığı (varsayılanları atlanmış) ton
        let android = Data(#"{"strokeColorHex":"#111111","tone":{"id":"tone_dot"}}"#.utf8)
        let t = try JSONDecoder().decode(ShapeStyle.self, from: android).tone
        XCTAssertEqual(t?.id, "tone_dot")
        XCTAssertEqual(t?.density, 0.3)
        XCTAssertEqual(t?.angle, 45)
    }

    func testStructureGuidesLoad() {
        let all = StructureGuides.all
        XCTAssertEqual(all.count, 27)
        XCTAssertEqual(Set(all.map(\.id)).count, all.count)
        for g in StructureGroup.allCases { XCTAssertGreaterThanOrEqual(StructureGuides.byGroup(g).count, 6, g.rawValue) }
    }

    func testHslTouchesOnlyChosenRange() {
        let red: UInt32 = 0xFFE53935, blue: UInt32 = 0xFF1E88E5
        var adj = PhotoHsl()
        adj.saturation[0] = -1
        let out = PhotoOps.hsl([red, blue], adj)
        XCTAssertNotEqual(out[0], red)
        XCTAssertEqual(out[1], blue)
    }

    func testBalanceAndClip() {
        var b = PhotoBalance()
        b.set(0, 2, 1)
        let out = PhotoOps.balance([0xFF303030], b)
        XCTAssertGreaterThan(out[0] & 0xFF, out[0] >> 16 & 0xFF)
        let px: [UInt32] = [0, 0xFF808080, 0, 0xFF808080]
        let c = PhotoOps.clip(px, 2, 2, PhotoClip(fill: .COLOR, color1: 0xFFFF0000, color2: 0xFF0000FF, angle: 0, blend: .NORMAL, opacity: 1))
        XCTAssertEqual(c[0], 0)
        XCTAssertEqual(c[1], 0xFFFF0000)
    }

    func testBalloonTextFollowsBalloon() {
        XCTAssertEqual(ComicController.balloonFont(.SHOUT), .BANGERS)
        XCTAssertEqual(ComicController.balloonFont(.SPEECH), .COMIC_RELIEF)
        let speech = ComicController.balloonTextSize(.SPEECH, 1200, 1800)
        XCTAssertGreaterThan(ComicController.balloonTextSize(.SHOUT, 1200, 1800), speech)
        XCTAssertLessThan(ComicController.balloonTextSize(.WHISPER, 1200, 1800), speech)
    }
}
