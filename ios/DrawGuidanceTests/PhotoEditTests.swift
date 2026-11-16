import XCTest
@testable import DrawGuidance

/// v52 foto düzenleme hattı (Android `PhotoEditTest` ile aynı).
final class PhotoEditTests: XCTestCase {
    private let w = 40, h = 20
    private lazy var src: [UInt32] = (0..<(w * h)).map { i in
        let x = UInt32(i % w), y = UInt32(i / w)
        return 0xFF00_0000 | ((x * 6) << 16) | ((y * 12) << 8) | 0x40
    }

    func testIdentityKeepsPixels() {
        let r = PhotoOps.render(src, w, h, PhotoEdit())
        XCTAssertEqual(r.w, w); XCTAssertEqual(r.h, h)
        XCTAssertEqual(r.px, src)
    }

    func testRotationAndCrop() {
        let r = PhotoOps.render(src, w, h, PhotoEdit(rotation: 1))
        XCTAssertEqual(r.w, h); XCTAssertEqual(r.h, w)
        let c = PhotoOps.render(src, w, h, PhotoEdit(crop: PhotoCrop(left: 0.25, top: 0, right: 0.75, bottom: 0.5)))
        XCTAssertEqual(c.w, 20); XCTAssertEqual(c.h, 10)
        let sq = PhotoOps.cropForAspect(w, h, PhotoEdit(), 1)
        XCTAssertEqual(sq.right - sq.left, 0.5, accuracy: 1e-4)
    }

    func testToSourceInvertsGeometry() {
        for q in 0...3 {
            for fh in [false, true] {
                let e = PhotoEdit(rotation: q, flipH: fh)
                let out = PhotoOps.render(src, w, h, e)
                let ox = 7, oy = 3
                let (u, v) = PhotoOps.toSource((Float(ox) + 0.5) / Float(out.w), (Float(oy) + 0.5) / Float(out.h), w, h, e)
                let sx = min(w - 1, max(0, Int(u * Float(w)))), sy = min(h - 1, max(0, Int(v * Float(h))))
                XCTAssertEqual(src[sy * w + sx], out.px[oy * out.w + ox], "q=\(q) flip=\(fh)")
            }
        }
    }

    func testLightAndBrush() {
        let lit = PhotoOps.render(src, w, h, PhotoEdit(light: PhotoLight(exposure: 0.5)))
        var brighter = 0
        for i in 0..<src.count {
            let a: UInt32 = (lit.px[i] >> 16) & 0xFF
            let b: UInt32 = (src[i] >> 16) & 0xFF
            if a > b { brighter += 1 }
        }
        XCTAssertGreaterThan(brighter, src.count / 2)
        let s = PhotoStroke(brush: .PAINT, points: [0.5, 0.5, 0.6, 0.5], size: 0.3, strength: 1, color: 0xFFFF0000)
        let painted = PhotoOps.render(src, w, h, PhotoEdit(strokes: [s]))
        XCTAssertNotEqual(src[10 * w + 20], painted.px[10 * w + 20])
        XCTAssertEqual(src[0], painted.px[0])
    }
}
