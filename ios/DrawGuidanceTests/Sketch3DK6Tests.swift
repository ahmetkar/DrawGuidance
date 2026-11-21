import ImageIO
import XCTest
@testable import DrawGuidance

/// v56 aşama 12–13 (Android `Sketch3K6CoreTest` ve `V56Sketch3DK6ViewModelTest`): dolgu, görünüm stilleri, yüzeye boya,
/// GIF, dönen kamera, Metal mürekkep kenarları ve 3B Çizim modelinde dolgu/Parlat/video.
@MainActor
final class Sketch3DK6Tests: XCTestCase {

    private func circle(_ n: Int, _ r: Float) -> [Float] {
        (0..<((n + 1) * 3)).map { i in
            let a = Float(i / 3) * 2 * Float.pi / Float(n)
            switch i % 3 { case 0: return r * cos(a); case 1: return 0.5 + r * sin(a); default: return 0 }
        }
    }

    func testFillFromClosedLoop() {
        let loop = circle(40, 0.4)
        XCTAssertTrue(Sketch3Fill.isClosed(loop, 0.02))
        XCTAssertFalse(Sketch3Fill.isClosed([0, 0, 0, 1, 0, 0, 2, 0, 0], 0.02))
        let p = Sketch3Fill.fromLoop(loop, "a", Int32(bitPattern: 0xFFE5_3935), layer: "L1")!
        XCTAssertTrue(Sketch3Fill.isFill(p))
        XCTAssertNil(p.primitive)
        XCTAssertEqual(p.mesh.faceCount, 2)
        XCTAssertEqual(p.mesh.faces[0].count, 40)
        XCTAssertEqual(p.position.y, 0.5, accuracy: 1e-3)
        let l: [Float] = [0, 0, 0, 2, 0, 0, 2, 1, 0, 1, 1, 0, 1, 2, 0, 0, 2, 0]
        XCTAssertEqual(Sketch3Fill.fromLoop(l, "l", 0)!.mesh.triangles[0].count / 3, 4)
        XCTAssertNil(Sketch3Fill.fromLoop([0, 0, 0, 1, 0, 0, 2, 0, 0], "x", 0))
        let model = Model3D("m", "M", [p])
        XCTAssertNotNil(Pick3.ray(model, V3(0, 0.5, 3), V3(0, 0, -1)))
        XCTAssertNotNil(Pick3.ray(model, V3(0, 0.5, -3), V3(0, 0, 1)))
        let sq: [Float] = [0, 0, 10, 0, 10, 10, 0, 10]
        XCTAssertTrue(Sketch3Fill.inside(sq, 5, 5))
        XCTAssertFalse(Sketch3Fill.inside(sq, 15, 5))
        XCTAssertEqual(Sketch3Fill.screenArea(sq), 100, accuracy: 1e-3)
    }

    func testToonAndLineStyles() {
        let m = Model3D("m", "M", [ModelEdit.newPart(.SPHERE, "s", 0)])
        let view = View3.editor(m.camera, 120, 120)
        func colors(_ st: Int) -> Set<UInt32> {
            Set(Renderer3D.render(m, view, RenderOptions(supersample: 1, ground: false, shadow: false, background: 0xFFFF_FFFF, style: st)).color)
        }
        XCTAssertLessThan(colors(RenderStyle.TOON).count, colors(RenderStyle.SOFT).count)
        XCTAssertTrue(colors(RenderStyle.LINES).contains { ($0 & 0xFF_FFFF) < 0x40_4040 })
        XCTAssertEqual(RenderStyle.toonBand(0.2), 0.5)
        XCTAssertEqual(RenderStyle.toonBand(0.9), 1)
        // Metal: toon tarzında mürekkep kenar şeritleri eklenir
        let soft = MetalRenderer3.build(m, view)
        let toon = MetalRenderer3.build(m, view, style: RenderStyle.TOON)
        XCTAssertGreaterThan(toon.opaque.count, soft.opaque.count)
        XCTAssertEqual(toon.opaque.count % (3 * MetalRenderer3.FLOATS_PER_VERTEX), 0)
    }

    func testSurfacePaintOnCube() {
        let cube = ModelEdit.newPart(.CUBE, "c", 0)
        let m = Model3D("m", "M", [cube], camera: Camera3(yaw: 0, pitch: 0, persp: 0, zoom: 1, target: cube.position))
        let view = View3.editor(m.camera, 400, 400)
        let c = view.project(cube.position)
        let screen: [Float] = (0..<82).map { i in i % 2 == 0 ? c[0] - 190 + Float(i / 2) * 9.5 : c[1] }
        let segs = SurfacePaint.cast(m, view, screen)
        XCTAssertEqual(segs.count, 1)
        let front = ModelEdit.worldBounds(cube).max.z
        for i in 0..<(segs[0].0.count / 3) { XCTAssertEqual(segs[0].0[3 * i + 2], front + SurfacePaint.OFFSET, accuracy: 1e-3) }
        XCTAssertTrue(SurfacePaint.cast(m, view, [2, 2, 5, 5]).isEmpty)
    }

    func testGifRoundTripAndOrbit() throws {
        let w = 24, h = 16
        let g = GifWriter(w, h, delayCs: 5)
        let cols: [UInt32] = [0xFFFF_0000, 0xFF00_FF00, 0xFF00_00FF]
        for c in cols { g.addFrame((0..<(w * h)).map { $0 % w < w / 2 ? c : 0xFFFF_FFFF }) }
        let data = g.finish()
        XCTAssertEqual(String(data: data.prefix(6), encoding: .ascii), "GIF89a")
        let src = try XCTUnwrap(CGImageSourceCreateWithData(data as CFData, nil))
        XCTAssertEqual(CGImageSourceGetCount(src), 3)
        XCTAssertEqual(OrbitTrack.frameCount(30, 1), 180)
        XCTAssertEqual(OrbitTrack.frameCount(30, 2), 90)
        XCTAssertEqual(OrbitTrack.cameras(Camera3(yaw: 10), 4).map(\.yaw), [10, 100, 190, 280])
    }

    func testSketchModelFillGlowStyleAndGif() async throws {
        let vm = Sketch3DModel(repository: nil, modelId: nil, preloaded: Model3D("t", "T", []))
        vm.setViewport(900, 900)
        let ring: [Float] = (0..<74).map { i in let a = Float(i / 2) * 2 * Float.pi / 36; return i % 2 == 0 ? 450 + 150 * cos(a) : 450 + 150 * sin(a) }
        vm.finishStroke(ring, nil, straight: false)
        vm.setTool(.FILL)
        XCTAssertNil(vm.fillAt(50, 50))
        let id = try XCTUnwrap(vm.fillAt(450, 450))
        XCTAssertTrue(vm.model!.parts.contains { $0.id == id })
        vm.undo(); XCTAssertTrue(vm.model!.parts.isEmpty)
        vm.redo()
        vm.setTool(.ERASER)
        vm.beginErase(); vm.eraseAt(450, 450, 10); vm.endErase()
        XCTAssertTrue(vm.model!.parts.isEmpty)
        vm.addPrimitive(.CUBE)
        vm.selectAll()
        vm.materialSelected(.GLOW)
        XCTAssertEqual(vm.model!.parts.first?.material.kind, .GLOW)
        vm.setStyle(9); XCTAssertEqual(vm.style, RenderStyle.LINES)
        let png = await vm.canvasImage(side: 128)
        XCTAssertNotNil(png)
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("sk3-test-\(UUID().uuidString)")
        vm.exportVideo(gif: true, speed: 2, outDir: dir)
        for _ in 0..<600 {
            try await Task.sleep(nanoseconds: 50_000_000)
            if case .videoSaved = vm.event { break }
        }
        guard case .videoSaved(let url) = vm.event else { return XCTFail("video yazılmadı") }
        XCTAssertEqual(url.pathExtension, "gif")
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
    }
}
