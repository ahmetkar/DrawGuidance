import XCTest
@testable import DrawGuidance

/// v56 aşama 10 (Android `V56Model3DTest`): 3B şeklin açısı, Bozma/Çarpıtma'nın 3B şekle uygulanmaması, emekli Premium anahtarları.
final class V56Model3DTests: XCTestCase {
    private func model() -> Model3D { Model3D("m", "Küp", []) }

    func testAngleIsNormalisedAndClamped() {
        let m = Model3DController.withAngle(model(), yaw: -30, pitch: 120)
        XCTAssertEqual(m.camera.yaw, 330, accuracy: 1e-3)
        XCTAssertEqual(m.camera.pitch, 80, accuracy: 1e-3)
        let n = Model3DController.withAngle(model(), yaw: 725, pitch: -95)
        XCTAssertEqual(n.camera.yaw, 5, accuracy: 1e-3)
        XCTAssertEqual(n.camera.pitch, -80, accuracy: 1e-3)
    }

    func testDistortSkips3dShapes() {
        let quad = [StrokePoint(0, 0), StrokePoint(100, 0), StrokePoint(100, 100), StrokePoint(0, 100)]
        var shape = Stroke(id: "s", points: quad, colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 0)
        shape.assetId = "a"; shape.model = Model3DLink(modelId: "catalog:cube", dataAssetId: "d")
        var image = Stroke(id: "i", points: quad, colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 1)
        image.assetId = "b"
        var moved = TransformState.start(0, 0, 100, 100, kind: .DISTORT)
        moved.quad[2] = StrokePoint(130, 140)
        let r = TransformOps.apply([shape, image], ["s", "i"], moved)
        XCTAssertEqual(r.skipped, ["s"])
        XCTAssertEqual(r.strokes[0].points, quad)
        XCTAssertNotEqual(r.strokes[1].points, quad)
    }

    func testRetired3dKeysAreNotPremium() {
        XCTAssertTrue(FeatureKeys.RETIRED.allSatisfy { !FeatureKeys.PREMIUM.contains($0) })
    }
}
