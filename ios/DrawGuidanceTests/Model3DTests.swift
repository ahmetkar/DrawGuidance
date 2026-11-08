import XCTest
@testable import DrawGuidance

final class Model3DTests: XCTestCase {
    func testPrimitivesAreClosed() {
        for k in PrimitiveKind.allCases where k != .PLANE {
            let m = Primitives.build(Primitives.defaultSpec(k))
            XCTAssertTrue(m.closed, k.name)
            XCTAssertGreaterThan(m.triangleCount, 0, k.name)
        }
    }

    func testCubeOps() {
        let c = Primitives.cube()
        XCTAssertEqual(c.triangleCount, 12)
        let e = c.extrudeFace(0, 0.5)
        XCTAssertEqual(e.faceCount, 10)
        XCTAssertEqual(e.bounds.max.z, 1, accuracy: 1e-6)
        let r = MeshOps.round(c, EdgeRef.of(4, 5), 0.1)
        XCTAssertNotNil(r)
        XCTAssertTrue(r!.closed)
        let b = MeshOps.bend(c, [EdgeRef.of(4, 5)], 0.3)
        XCTAssertEqual(b.vertexCount, 11)
    }
}
