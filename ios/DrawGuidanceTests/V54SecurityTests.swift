import CoreGraphics
import XCTest
@testable import DrawGuidance

/// v54 güvenlik: resim yapı/gömülü kod denetimi ve şifreli paylaşımın anahtar türetimi (Android `V54SecurityTest` ile aynı
/// sabit değerler).
final class V54SecurityTests: XCTestCase {
    private func png(_ w: Int = 20, _ h: Int = 20) -> Data { ImageImportValidator.encode(Bitmaps.draw(w, h) { _ in }, .png)! }
    private func jpeg(_ w: Int = 20, _ h: Int = 20) -> Data { ImageImportValidator.encode(Bitmaps.draw(w, h) { c in
        c.setFillColor(ArgbColor.cgColor(0xFFFF_FFFF)); c.fill(CGRect(x: 0, y: 0, width: w, height: h)) }, .jpeg, quality: 0.9)! }
    private func hex(_ d: Data) -> String { d.map { String(format: "%02x", $0) }.joined() }

    func testCleanImagesPass() {
        XCTAssertEqual(ImageSecurity.inspect(png(), mime: "image/png"), .CLEAN)
        XCTAssertEqual(ImageSecurity.inspect(jpeg(), mime: "image/jpeg"), .CLEAN)
        XCTAssertEqual(ImageSecurity.inspect(jpeg() + Data((0..<4096).map { UInt8(truncatingIfNeeded: $0 * 7) }), mime: "image/jpeg"), .CLEAN)
    }

    func testEmbeddedCodeRejected() {
        XCTAssertEqual(ImageSecurity.inspect(png() + Data("<html><body></body></html>".utf8), mime: "image/png"), .SUSPICIOUS)
        XCTAssertEqual(ImageSecurity.inspect(jpeg() + Data([0x50, 0x4B, 3, 4, 20, 0]), mime: "image/jpeg"), .SUSPICIOUS)
        XCTAssertEqual(ImageSecurity.inspect(jpeg() + Data([0x4D, 0x5A, 0, 0]), mime: "image/jpeg"), .SUSPICIOUS)
        XCTAssertEqual(ImageSecurity.inspect(jpeg() + Data("<script>alert(1)</script>".utf8), mime: "image/jpeg"), .SUSPICIOUS)
        let p = png()
        XCTAssertEqual(ImageSecurity.inspect(p.prefix(p.count - 20), mime: "image/png"), .MALFORMED)
        XCTAssertEqual(ImageSecurity.inspect(Data([0xFF, 0xD8, 0x12, 0x34]), mime: "image/jpeg"), .MALFORMED)
        // Doğrulayıcı da reddeder
        XCTAssertEqual(ImageImportValidator.check(png(100, 100) + Data("<?php echo 1; ?>".utf8)).errors, [.suspicious])
    }

    func testPairingMatchesAndroidVector() throws {
        let z = Data((0..<32).map { UInt8($0) })
        let t = SharePairing.transcript(Data([0x7F]) + Data("hello-a".utf8), Data([0x7E]) + Data("hello-b".utf8))
        XCTAssertEqual(hex(t), "b724697539edc0627144b6a820464b1df90df9bbb29f0310a73950cef6c76840")
        let k = SharePairing.derive(z: z, secret: Data("482913".utf8), transcript: t)
        XCTAssertEqual(hex(k.aToB), "a036fa627d7181c72499206efece895a867d6cef4b7db8c25f0040ae355b2e42")
        XCTAssertEqual(hex(k.bToA), "2935e19470b70101efe8f3489c0603587596547cc8c31904a5a36b94c9fff47d")
        XCTAssertEqual(hex(k.trust), "268fa0a55499ec5f69adfb29ae264bca1e004d5b04119701ef4b684b8db5a420")
        XCTAssertEqual(hex(SharePairing.confirmTag(k, "A")), "3bf6db88afdd5e4083930c3fc0d4f3c9e1a513f986be8cedaa62aeec6597c310")
        let sealer = SharePairing.Sealer(k.aToB)
        XCTAssertEqual(hex(try sealer.seal(Data("merhaba".utf8))), "6e7fc67cb9dfb7325b9919d289af22a8cdc692691effd0")
        XCTAssertEqual(hex(try sealer.seal(Data("dunya".utf8))), "1ba98262168071683254edf6d19e71eae421f592dc")
    }

    func testPairingRoundTrip() throws {
        let a = SharePairing.newKey(), b = SharePairing.newKey()
        let za = try SharePairing.sharedSecret(a, SharePairing.publicRaw(b))
        let zb = try SharePairing.sharedSecret(b, SharePairing.publicRaw(a))
        XCTAssertEqual(za, zb)
        XCTAssertEqual(SharePairing.publicRaw(a).count, 65)
        let code = SharePairing.newCode()
        XCTAssertEqual(code.count, 6)
        let t = SharePairing.transcript(Data([1]), Data([2]))
        let ka = SharePairing.derive(z: za, secret: Data(code.utf8), transcript: t)
        let kb = SharePairing.derive(z: zb, secret: Data(code.utf8), transcript: t)
        XCTAssertTrue(SharePairing.same(SharePairing.confirmTag(ka, "A"), SharePairing.confirmTag(kb, "A")))
        let sealer = SharePairing.Sealer(ka.aToB), opener = SharePairing.Opener(kb.aToB)
        for i in 0..<3 { XCTAssertEqual(String(decoding: try opener.open(try sealer.seal(Data("çerçeve \(i)".utf8))), as: UTF8.self), "çerçeve \(i)") }
        var bad = try sealer.seal(Data("x".utf8)); bad[bad.startIndex] ^= 1
        XCTAssertThrowsError(try opener.open(bad))
    }
}
