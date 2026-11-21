import XCTest
import UIKit
@testable import DrawGuidance

/// 3B: glTF, katalog ve örnekler (değerler Android sürümünden alındı).
final class Model3DExtraTests: XCTestCase {
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
        // v56: kayıt D3 v2 (eski değil); GLB eski sayılır ve okunur
        XCTAssertFalse(Model3DStore.isLegacy(Model3DStore.encode(m)))
        XCTAssertTrue(Model3DStore.isLegacy(Data(glb)))
        XCTAssertEqual(try Model3DStore.decode(Data(glb)).parts.count, 2)
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
}
