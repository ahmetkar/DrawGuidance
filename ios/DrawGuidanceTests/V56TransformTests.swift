import XCTest
@testable import DrawGuidance

/// v56 aşama 6: homografi, ağ, yaslama, çevirme/döndürme, sığdırma, uygulama, yansıma, sıvılaştır (Android
/// V56TransformRulesTest ile aynı).
final class V56TransformTests: XCTestCase {
    private func line(_ id: String, _ xy: [Float]) -> Stroke {
        Stroke(id: id, points: stride(from: 0, to: xy.count, by: 2).map { StrokePoint(xy[$0], xy[$0 + 1]) }, colorHex: "#000000",
               brushType: .INK_PEN, sizePx: 10, opacity: 1, order: 0)
    }

    private func near(_ a: [StrokePoint], _ b: [StrokePoint], _ tol: Float = 1e-3) -> Bool {
        a.count == b.count && zip(a, b).allSatisfy { hypot($0.x - $1.x, $0.y - $1.y) <= tol }
    }

    func testHomographyCornersWithinHalfPixel() {
        let src: [Float] = [0, 0, 200, 0, 200, 100, 0, 100]
        let dst: [Float] = [30, 12, 260, 40, 220, 190, 10, 140]
        let h = Homography.solve(src, dst)!
        for i in 0..<4 {
            let (x, y) = Homography.map(h, src[2 * i], src[2 * i + 1])
            XCTAssertLessThanOrEqual(hypot(x - dst[2 * i], y - dst[2 * i + 1]), 0.5)
        }
        XCTAssertFalse(Homography.convex([StrokePoint(0, 0), StrokePoint(100, 0), StrokePoint(10, 10), StrokePoint(0, 100)]))
    }

    func testFlatMeshIdentityAndLocalCornerMove() {
        let m = WarpMesh.identity(0, 0, 300, 300, WarpMesh.BASIC)
        for (u, v) in [(Float(0.1), Float(0.2)), (0.5, 0.5), (0.93, 0.4)] {
            let (x, y) = WarpMesh.eval(m, WarpMesh.BASIC, u, v)
            XCTAssertEqual(x, 300 * u, accuracy: 1e-2); XCTAssertEqual(y, 300 * v, accuracy: 1e-2)
        }
        let moved = WarpMesh.movePoint(m, 0, -40, -30)
        let far = WarpMesh.eval(moved, WarpMesh.BASIC, 0.95, 0.95)
        XCTAssertEqual(far.0, 285, accuracy: 1e-2); XCTAssertEqual(far.1, 285, accuracy: 1e-2)
        XCTAssertLessThan(WarpMesh.eval(moved, WarpMesh.BASIC, 0.05, 0.05).0, 14)
        XCTAssertTrue(near(WarpMesh.resample(m, WarpMesh.BASIC, WarpMesh.ADVANCED), WarpMesh.identity(0, 0, 300, 300, WarpMesh.ADVANCED), 1e-2))
    }

    func testSnappingStepsDistanceSpeedAndPrefs() {
        XCTAssertEqual(Snapping.angle(13), 15)
        XCTAssertEqual(Snapping.angle(47, tolerance: 3), 45)
        XCTAssertEqual(Snapping.angle(40, tolerance: 3), 40)
        XCTAssertEqual(Snapping.nearest(104, [0, 100, 500], 5), 100)
        XCTAssertNil(Snapping.nearest(110, [0, 100], 5))
        XCTAssertTrue(Snapping.active(800, 1500))
        XCTAssertFalse(Snapping.active(3000, 1500))
        XCTAssertTrue(Snapping.active(9000, 0))
        let r = Snapping.snapBox(97, 10, 147, 60, [100], [500], 5)
        XCTAssertEqual(r.dx, 3, accuracy: 1e-4); XCTAssertEqual(r.dy, 0); XCTAssertEqual(r.guideX, 100); XCTAssertNil(r.guideY)
        let p = SnapPrefs(magnets: false, snap: true, distance: 22, speed: 900)
        XCTAssertEqual(SnapPrefs.decode(p.encode()), p)
        XCTAssertEqual(SnapPrefs.decode("bozuk"), SnapPrefs())
    }

    func testTwoFlipsAndEightEighthTurnsAreIdentity() {
        let s = TransformOps.rotate(TransformState.start(10, 20, 210, 120), 30)
        XCTAssertTrue(near(TransformOps.flip(TransformOps.flip(s, horizontal: true), horizontal: true).quad, s.quad))
        XCTAssertTrue(near(TransformOps.flip(TransformOps.flip(s, horizontal: false), horizontal: false).quad, s.quad))
        var r = s
        for _ in 0..<8 { r = TransformOps.rotate(r, 45) }
        XCTAssertTrue(near(r.quad, s.quad, 1e-2))
        XCTAssertEqual(TransformOps.flip(TransformState.start(0, 0, 100, 50), horizontal: true).quad[0].x, 100, accuracy: 1e-3)
    }

    func testFitKeepsRatioAndCenters() {
        let s = TransformState.start(10, 10, 110, 60)
        let f = TransformOps.fit(TransformOps.rotate(s, 20), 0, 0, 1000, 1000)
        XCTAssertEqual(TransformOps.widthOf(f.quad), 1000, accuracy: 1e-2)
        XCTAssertEqual(TransformOps.heightOf(f.quad), 500, accuracy: 1e-2)
        XCTAssertEqual(TransformOps.angleOf(f.quad), 0, accuracy: 1e-3)
        XCTAssertEqual(f.quad[0].y, 250, accuracy: 1e-2)
        XCTAssertTrue(TransformOps.reset(f).isIdentity)
    }

    func testUniformCornerKeepsRatioAndDistortSkipsText() {
        let s = TransformState.start(0, 0, 100, 50)
        let u = TransformOps.dragCorner(s, 2, 300, 60)
        XCTAssertEqual(TransformOps.widthOf(u.quad) / TransformOps.heightOf(u.quad), 2, accuracy: 1e-3)
        let text = Stroke(id: "t", points: [StrokePoint(0, 0), StrokePoint(100, 0), StrokePoint(100, 50), StrokePoint(0, 50)], colorHex: "#000000",
                          brushType: .TEXT, sizePx: 12, opacity: 1, order: 1, text: TextSpec(content: "x"))
        let strokes = [line("a", [0, 0, 100, 50]), text]
        let d = TransformOps.dragCorner(TransformOps.setKind(s, .DISTORT), 2, 140, 90)
        let out = TransformOps.apply(strokes, ["a", "t"], d)
        XCTAssertEqual(out.skipped, ["t"])
        XCTAssertEqual(out.strokes[1], strokes[1])
        XCTAssertEqual(out.strokes[0].points[1].x, 140, accuracy: 0.5); XCTAssertEqual(out.strokes[0].points[1].y, 90, accuracy: 0.5)
        let moved = TransformOps.apply(strokes, ["a"], TransformOps.translate(s, 5, 7)).strokes[0]
        XCTAssertEqual(moved.points[0].x, 5, accuracy: 1e-3); XCTAssertEqual(moved.points[0].y, 7, accuracy: 1e-3); XCTAssertEqual(moved.sizePx, 10, accuracy: 1e-3)
    }

    func testReflectionMirrorsBelowWithWaveAndFade() {
        let strokes = [line("a", [0, 0, 100, 100])]
        var k = 0
        let out = Reflection.create(strokes, ["a"], 0, 0, 100, 100, gap: 4, newId: { _ in defer { k += 1 }; return "r\(k)" })
        XCTAssertEqual(out.count, 2)
        let r = out[1]
        XCTAssertEqual(r.id, "r0")
        XCTAssertEqual(r.points[0].y, 204, accuracy: 1e-3)
        XCTAssertEqual(r.points[1].y, 104, accuracy: 1e-3)
        let w = r.warp!.wave!
        XCTAssertEqual(w.top, 104); XCTAssertEqual(w.bottom, 204)
        XCTAssertEqual(Reflection.offset(w, 50, w.top).0, 0, accuracy: 1e-4)
        XCTAssertEqual(Reflection.fade(w, w.top), w.fadeFrom, accuracy: 1e-4)
        XCTAssertEqual(Reflection.fade(w, w.bottom), w.fadeTo, accuracy: 1e-4)
        XCTAssertEqual(Reflection.create(strokes, [], 0, 0, 1, 1, gap: 4, newId: { _ in "x" }), strokes)
        // Yeni alanlar Android ile aynı JSON anahtarlarıyla gidip gelir
        let data = try! JSONEncoder().encode(ImageWarp(wave: w, filter: 0))
        XCTAssertEqual(try! JSONDecoder().decode(ImageWarp.self, from: data), ImageWarp(wave: w, filter: 0))
        XCTAssertTrue(ImageWarp().isEmpty); XCTAssertFalse(ImageWarp(filter: 2).isEmpty)
    }

    func testLiquifyIsLocal() {
        let n = WarpMesh.LIQUIFY
        let m = WarpMesh.identity(0, 0, 320, 320, n)
        let pushed = WarpMesh.liquify(m, .PUSH, 160, 160, 30, 0, 60, 1)
        XCTAssertEqual(pushed[8 * n + 8].x, 190, accuracy: 1e-3)
        XCTAssertEqual(pushed[0], m[0])
        let i = 8 * n + 9
        XCTAssertGreaterThan(WarpMesh.liquify(m, .BLOAT, 160, 160, 0, 0, 60, 1)[i].x, m[i].x)
        XCTAssertLessThan(WarpMesh.liquify(m, .PINCH, 160, 160, 0, 0, 60, 1)[i].x, m[i].x)
        XCTAssertNotEqual(WarpMesh.liquify(m, .TWIRL, 160, 160, 0, 0, 60, 1)[i].y, m[i].y)
    }

    func testWarpRendererVertsFollowMeshAndWave() {
        let corners = [StrokePoint(0, 0), StrokePoint(160, 0), StrokePoint(160, 160), StrokePoint(0, 160)]
        XCTAssertNil(WarpRenderer.verts(corners, nil))
        let v = WarpRenderer.verts(corners, ImageWarp(mesh: WarpMesh.identity(0, 0, 160, 160, 4)))!
        XCTAssertEqual(v.count, (WarpRenderer.SUB + 1) * (WarpRenderer.SUB + 1))
        XCTAssertEqual(v[1].x, 10, accuracy: 1e-3)
    }
}
