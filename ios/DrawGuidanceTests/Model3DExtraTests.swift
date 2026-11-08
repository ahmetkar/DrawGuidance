import XCTest
import UIKit
@testable import DrawGuidance

/// 3B: katı işlemler, glTF, katalog ve 3B çizim önerisi (değerler Android sürümünden alındı).
final class Model3DExtraTests: XCTestCase {
    func testSolidBooleans() {
        let a = ModelEdit.newPart(.CUBE, "a", 0)
        let b = ModelEdit.newPart(.SPHERE, "b", 1, V3(0.4, 0.3, 0.2))
        let u = Solid3.boolean([a, b], .UNION, "u", "U")!
        XCTAssertEqual(u.mesh.vertexCount, 533); XCTAssertEqual(u.mesh.faceCount, 562)
        let s = Solid3.boolean([a, b], .SUBTRACT, "s", "S")!
        XCTAssertEqual(s.mesh.vertexCount, 432); XCTAssertEqual(s.mesh.faceCount, 405)
        let i = Solid3.boolean([a, b], .INTERSECT, "i", "I")!
        XCTAssertEqual(i.mesh.vertexCount, 125); XCTAssertEqual(i.mesh.faceCount, 156)
        XCTAssertEqual(Solid3.simplified(Primitives.sphere(48, 24), 300).faceCount, 300)
    }

    func testGltfRoundTrip() throws {
        var a = ModelEdit.newPart(.CUBE, "a", 0)
        a.group = "g1"
        var b = ModelEdit.newPart(.SPHERE, "b", 1, V3(0.4, 0.3, 0.2))
        b.rotation = V3(10, 20, 30); b.smooth = true
        let m = Model3D("m1", "Model ç", [a, b], strokes: [Stroke3("s1", [-1, 1, 0, 0, 1.5, 0], -1, 0.03, time: 9)], groups: [Group3(id: "g1", name: "Grup 1")])
        let glb = Gltf3.write(m)
        XCTAssertTrue(Gltf3.isGlb(glb))
        let back = try XCTUnwrap(try Gltf3.read(glb))
        XCTAssertEqual(back.parts.count, 2)
        XCTAssertEqual(back.strokes.count, 1)
        XCTAssertEqual(back.groups, m.groups)
        XCTAssertEqual(back.parts[1].rotation, b.rotation)
        XCTAssertEqual(back.parts[1].mesh.pos, b.mesh.pos)
        XCTAssertEqual(back.parts[0].group, "g1")
        XCTAssertEqual(Gltf3.write(back), glb)
        let stored = try Model3DStore.decode(Model3DStore.encode(m))
        XCTAssertEqual(stored.triangleCount, m.triangleCount)
        // Eski D3 biçimi okunur
        let legacy = Model3DCodec.encode(m)
        XCTAssertTrue(Model3DStore.isLegacy(legacy))
        XCTAssertEqual(try Model3DStore.decode(legacy).parts.count, 2)
    }

    func testEmbeddedTexturePng() throws {
        let t = TextureCatalog.byId("wood.oak")!.generate(32)
        let png = PngWriter.encode(t)
        let img = try XCTUnwrap(UIImage(data: png))
        XCTAssertEqual(img.size.width * img.scale, 32)
        var a = ModelEdit.newPart(.CUBE, "a", 0)
        a.material.texture = "wood.oak"
        let glb = Gltf3.write(Model3D("m", "M", [a]), GltfWriteOptions(embedTextures: true, textures: { _ in t }))
        let imp = try Gltf3.importModel(glb, "x", 500, { _, _ in nil })
        XCTAssertEqual(imp.model.parts.count, 1)
    }

    func testCatalogAndSamples() {
        XCTAssertEqual(Catalog3D.all.count, 68)
        for it in Catalog3D.all {
            let m = it.build()
            XCTAssertFalse(m.isEmpty, it.id)
            XCTAssertTrue(Catalog3D.categories.contains(it.category))
        }
        XCTAssertEqual(Samples3D.all.count, 6)
        XCTAssertEqual(Samples3D.all[0].parts.count, 5)
        XCTAssertNotNil(Catalog3D.byId("catalog:geo.cube_diag"))
    }

    func testSketchSuggestions() {
        let sm = SketchModel.create()
        // Daire → küre önerisi
        let circle: [V3] = (0..<60).map { (i: Int) -> V3 in
            let t: Float = Float(i) / 60 * 2 * Float.pi
            let y: Float = 0.5 * sin(t) + 0.5
            return V3(0.5 * cos(t), y, 0)
        }
        let r = Sketch3Suggest.analyze(circle, V3(0, 0, 1), .UP, .LINE, sm, 0.05)
        XCTAssertEqual(r?.1.sketchClass, .CIRCLE)
        XCTAssertEqual(r?.1.suggestions.first?.kind, .SPHERE)
        let part = Sketch3Fit.solid(.SPHERE, r!.0, "x", 0, Material())
        XCTAssertEqual(part!.scale.x, 1, accuracy: 0.05)
        sm.learnAccept(r!.1.features, .CYLINDER)
        let enc = sm.encode()
        let sm2 = SketchModel.create(); sm2.decode(enc)
        XCTAssertEqual(sm2.encode(), enc)
    }
}
