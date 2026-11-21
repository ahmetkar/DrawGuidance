import CryptoKit
import XCTest
@testable import DrawGuidance

/// v56 aşama 11 (Android `Sketch3DCoreTest`): D3 v2 (ortak bayt vektörü), düzlem ve yapıştırma, eski kayıtların
/// çevrimi, katmanlar, Metal sahne kurulumu ve 3B Çizim modeli.
@MainActor
final class Sketch3DCoreTests: XCTestCase {

    /// Ortak örneğin D3 v2 gövdesinin SHA-256'sı (Android `Sketch3DCoreTest.D3V2_SHA` ile aynı).
    static let D3V2_SHA = "40afdf2bdf748c586ea46b2bda6a648af9b9c567122a60412fcc5050a4635088"

    /// Android ile ortak örnek çizim.
    static func sample() -> Model3D {
        let quad = Mesh([0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0], [[0, 1, 2, 3]], [[0, 0, 1, 0, 1, 1, 0, 1]])
        let cube = Part("c", "Küp", quad, PrimitiveSpec(.CUBE, 4, 1), position: V3(0, 0.5, 0), group: "g1", layer: "L2")
        let locked = Part("k", "Kilitli", Mesh([0, 0, 0, 1, 0, 0, 0, 1, 0], [[0, 1, 2]]), nil,
                          material: Material(color: Int32(bitPattern: 0xFF33_6699), gradient: Int32(bitPattern: 0xFFFF_CC00), gradientAxis: 2),
                          locked: true)
        let s1 = Stroke3("s1", [0, 0, 0, 1, 0.5, 0, 2, 0, 0], Int32(bitPattern: 0xFF26_3238), 0.02, time: 1_700_000_000_123,
                         widths: [0.2, 1, 0.6], opacity: 0.8, brush: Brush3D.INK.rawValue, plane: "front", layer: "L1")
        let s2 = Stroke3("s2", [0, 1, 0, 0, 1, 1], Int32(bitPattern: 0xFFE5_3935), 0.05)
        var cam = Camera3()
        cam.yaw = 30; cam.pitch = 20
        return Model3D("m-1", "Sokak köşesi", [cube, locked], strokes: [s1, s2], camera: cam,
                       groups: [Group3(id: "g1", name: "Grup")],
                       layers: [Layer3("L1", "Katman 1"), Layer3("L2", "Gölge", visible: false, locked: true)],
                       plane: Plane3(V3(0, 0.5, 0), V3(1, 0, 0), V3(0, 1, 0), id: "front"))
    }

    func testCodecV2RoundTripAndSharedVector() throws {
        let m = Self.sample()
        let enc = Model3DCodec.encode(m)
        XCTAssertEqual(enc[2], 2)
        let back = try Model3DCodec.decode(enc)
        XCTAssertEqual(back, m)
        XCTAssertEqual(back.strokes[0].widths?[2], 0.6)
        XCTAssertTrue(back.parts[1].locked)
        XCTAssertEqual(back.parts[1].material.gradient, Int32(bitPattern: 0xFFFF_CC00))
        XCTAssertEqual(back.parts[0].mesh.uvs?[0], [0, 0, 1, 0, 1, 1, 0, 1])
        let sha = SHA256.hash(data: Model3DCodec.rawBody(m)).map { String(format: "%02x", $0) }.joined()
        XCTAssertEqual(sha, Self.D3V2_SHA, "Android ile aynı baytlar yazılmalı")
        XCTAssertFalse(Model3DStore.isLegacy(Model3DStore.encode(m)))
    }

    func testLegacyGlbBecomesLockedParts() throws {
        let cube = ModelEdit.newPart(.CUBE, "c", 0)
        var edited = cube
        edited.id = "e"; edited.primitive = nil
        let glb = Data(Gltf3.write(Model3D("x", "Eski", [cube, edited])))
        XCTAssertTrue(Model3DStore.isLegacy(glb))
        let conv = Model3DStore.fromLegacy(try Model3DStore.decode(glb))
        XCTAssertFalse(conv.parts.first { $0.id == "c" }!.locked)
        XCTAssertTrue(conv.parts.first { $0.id == "e" }!.locked)
    }

    func testPlanePresetsProjectAndTilt() {
        let view = View3.editor(Camera3(yaw: 0, pitch: 0, persp: 0, zoom: 1, target: .ZERO), 400, 400)
        let front = PlaneSnap.preset(.FRONT)
        let p = PlaneSnap.project(front, view, 300, 100)!
        XCTAssertEqual(p.z, 0, accuracy: 1e-4)
        XCTAssertTrue(p.x > 0 && p.y > 0)
        XCTAssertEqual(PlaneSnap.tilt(front, view), 0, accuracy: 0.01)
        let top = PlaneSnap.preset(.TOP)
        XCTAssertTrue(PlaneSnap.tooTilted(top, view))
        var c = Camera3(); c.yaw = 40; c.pitch = 25
        var cp = c; cp.persp = 0
        let cam = PlaneSnap.faceCamera(top, View3.editor(c, 400, 400))
        XCTAssertEqual(PlaneSnap.tilt(cam, View3.editor(cp, 400, 400)), 0, accuracy: 0.5)
        XCTAssertEqual(front.normal.z, 1, accuracy: 1e-5)
        XCTAssertEqual(top.normal.y, 1, accuracy: 1e-5)
        XCTAssertEqual(PlaneSnap.preset(.SIDE).normal.x, 1, accuracy: 1e-5)
    }

    func testSnapRotationFaceEndpointAndIntersection() {
        XCTAssertEqual(PlaneSnap.snapAngle(37), 30)
        XCTAssertEqual(PlaneSnap.snapAngle(38), 45)
        let r = PlaneSnap.rotate(PlaneSnap.preset(.FRONT), 1, 88)
        XCTAssertEqual(abs(r.normal.x), 1, accuracy: 1e-4)
        let cube = ModelEdit.newPart(.CUBE, "c", 0)
        let m = Model3D("m", "M", [cube])
        let hit = Pick3.ray(m, V3(cube.position.x, 5, cube.position.z), V3(0, -1, 0))!
        let face = PlaneSnap.onFace(m, hit)
        XCTAssertEqual(face.normal.y, 1, accuracy: 1e-4)
        XCTAssertEqual(face.origin.y, hit.point.y, accuracy: 1e-4)
        let view = View3.editor(Camera3(yaw: 0, pitch: 0, persp: 0, zoom: 1, target: .ZERO), 400, 400)
        let a = Stroke3("a", [-1, 0, 0, 1, 0, 0], 0, 0.01)
        let b = Stroke3("b", [0.3, -1, 0.5, 0.3, 1, 0.5], 0, 0.01)
        let sm = Model3D("s", "S", [], strokes: [a, b])
        let end = view.project(V3(1, 0, 0))
        XCTAssertEqual(PlaneSnap.snapPoint(sm, view, end[0] + 3, end[1], 20), V3(1, 0, 0))
        let x = view.project(V3(0.3, 0, 0))
        let ix = PlaneSnap.snapPoint(sm, view, x[0] + 2, x[1] - 2, 20)
        XCTAssertNotNil(ix)
        XCTAssertEqual(ix!.x, 0.3, accuracy: 1e-3)
        XCTAssertEqual(ix!.z, 0.25, accuracy: 1e-3)
        XCTAssertNil(PlaneSnap.snapPoint(sm, view, 10, 10, 20))
    }

    func testHiddenLayersAndMetalScene() {
        let m = Self.sample()
        let v = m.visibleLayers()
        XCTAssertFalse(v.parts.first { $0.id == "c" }!.visible)
        XCTAssertEqual(v.strokes.count, m.strokes.count)
        XCTAssertTrue(m.layerLocked("L2"))
        // Metal sahnesi: görünen parça üçgenleri ve çizgi şeritleri (s1 yarı saydam ayrı geçişte)
        let sc = MetalRenderer3.build(v, View3.editor(m.camera, 300, 300))
        XCTAssertEqual(sc.opaque.count % (3 * MetalRenderer3.FLOATS_PER_VERTEX), 0)
        XCTAssertGreaterThan(sc.opaque.count, 0)
        XCTAssertGreaterThan(sc.transparent.count, 0)
        for i in stride(from: 0, to: sc.opaque.count, by: MetalRenderer3.FLOATS_PER_VERTEX) {
            XCTAssertTrue((0...1).contains(sc.opaque[i + 2]), "derinlik 0…1")
        }
    }

    func testSketchModelDrawEraseLayersSelect() {
        let vm = Sketch3DModel(repository: nil, modelId: nil, preloaded: Model3D("t", "T", [], camera: Camera3(yaw: 0, pitch: 0, persp: 0, zoom: 1, target: V3(0, 0.5, 0))))
        vm.setViewport(900, 900)
        func line(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float, _ n: Int = 12) -> [Float] {
            (0..<(n * 2)).map { i in let t = Float(i / 2) / Float(n - 1); return i % 2 == 0 ? x0 + (x1 - x0) * t : y0 + (y1 - y0) * t }
        }
        let press = (0..<12).map { 0.2 + Float($0) * 0.06 }
        let id = vm.finishStroke(line(300, 400, 600, 420), press, straight: false)!
        let s = vm.model!.strokes.first { $0.id == id }!
        for i in 0..<s.count { XCTAssertEqual(s.points[3 * i + 2], vm.plane.origin.z, accuracy: 1e-4) }
        XCTAssertGreaterThan(s.widths![9], s.widths![2])
        XCTAssertEqual(vm.finishStroke(line(300, 500, 520, 640), nil, straight: true).flatMap { sid in vm.model!.strokes.first { $0.id == sid }?.count }, 2)
        // Silgi çizgiyi böler; tek geri alma adımı
        _ = vm.finishStroke(line(200, 700, 700, 700, 40), nil, straight: false)
        let before = vm.model!.strokes.count
        vm.setTool(.ERASER)
        vm.beginErase(); vm.eraseAt(450, 700, 30); vm.endErase()
        XCTAssertEqual(vm.model!.strokes.count, before + 1)
        vm.undo()
        XCTAssertEqual(vm.model!.strokes.count, before)
        // Katman: gizli/kilitli katmana çizilmez
        vm.addLayer()
        let l2 = vm.activeLayer
        vm.toggleLayerLocked(l2)
        XCTAssertNil(vm.finishStroke(line(300, 300, 600, 300), nil, straight: false))
        vm.toggleLayerLocked(l2)
        // Seç, taşı (tek adım), kopyala, sil
        vm.setMode(.SELECT)
        vm.tapSelect(450, 410, 24)
        XCTAssertEqual(vm.selection.count, 1)
        let snap = vm.model!.strokes
        vm.beginLive(); for _ in 0..<5 { vm.moveSelectedBy(10, 0) }; vm.endLive()
        XCTAssertNotEqual(vm.model!.strokes, snap)
        vm.undo()
        XCTAssertEqual(vm.model!.strokes, snap)
        vm.duplicateSelected()
        XCTAssertEqual(vm.model!.strokes.count, snap.count + 1)
        vm.deleteSelected()
        XCTAssertEqual(vm.model!.strokes.count, snap.count)
    }

    func testSketchModelPrimitiveAndFaceSnap() {
        let vm = Sketch3DModel(repository: nil, modelId: nil, preloaded: Model3D("t", "T", []))
        vm.setViewport(900, 900)
        // Cisim ekle ve düzlemi üst yüzüne yapıştır
        vm.addPrimitive(.CUBE)
        XCTAssertEqual(vm.mode, .SELECT)
        vm.presetView(3)
        let part = vm.model!.parts[0]
        let top = vm.currentView()!.project(ModelEdit.worldBounds(part).center)
        XCTAssertTrue(vm.snapPlaneAt(top[0], top[1], 4))
        XCTAssertEqual(abs(vm.plane.normal.y), 1, accuracy: 1e-3)
        XCTAssertEqual(vm.model!.plane, vm.plane)
    }
}
