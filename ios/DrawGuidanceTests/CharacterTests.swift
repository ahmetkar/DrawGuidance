import XCTest
@testable import DrawGuidance

/// Çizgi roman karakterleri: Android (Kotlin) çıktısıyla aynı (değerler Android'den alındı).
final class CharacterTests: XCTestCase {
    func testCatalogSize() {
        XCTAssertEqual(CharKind.allCases.count, 15)
        XCTAssertEqual(CharExpression.allCases.count, 13)
        XCTAssertEqual(CharPose.allCases.count, 26)
        XCTAssertEqual(ComicCharacters.all.count, 5070)
    }

    func testParse() {
        XCTAssertNotNil(ComicCharacters.parse("lib.chr.hero.happy.run"))
        XCTAssertEqual(ComicCharacters.parse("chr.hero.happy.run.m")?.mirrored, true)
        XCTAssertNil(ComicCharacters.parse("chr.hero.happy"))
        XCTAssertNil(ComicCharacters.parse("chr.nobody.happy.run"))
        XCTAssertEqual(ComicCharacters.def(.KID, .CALM, .STAND).id, "chr.kid.calm.stand")
    }

    private func check(_ id: String, aspect: Float, count: Int, first: [Float], file: StaticString = #filePath, line: UInt = #line) {
        guard let s = ComicCharacters.shape(id) else { return XCTFail("yok: \(id)", file: file, line: line) }
        XCTAssertEqual(s.aspect, aspect, accuracy: 0.001, file: file, line: line)
        XCTAssertEqual(s.paths.count, count, file: file, line: line)
        let p = s.paths[0].points
        for i in 0..<first.count / 2 {
            XCTAssertEqual(p[i].x * 100, first[i * 2], accuracy: 0.002, file: file, line: line)
            XCTAssertEqual(p[i].y * 100, first[i * 2 + 1], accuracy: 0.002, file: file, line: line)
        }
    }

    func testMatchesAndroid() {
        check("chr.robot.smug.kick", aspect: 1.045, count: 118, first: [95.322, 97.384, 95.245, 97.506, 95.013, 97.627])
        check("chr.wizard.calm.salute", aspect: 0.270, count: 137, first: [88.792, 97.500, 88.600, 97.663, 88.023, 97.824])
    }

    func testMirrorKeepsBox() {
        let a = ComicCharacters.def(.HERO, .ANGRY, .PUNCH)
        XCTAssertEqual(a.bounds, a.mirror().bounds)
        let sa = a.shape(), sm = a.mirror().shape()
        XCTAssertEqual(sa.paths.count, sm.paths.count)
        XCTAssertEqual(sa.paths[0].points[0].x, 1 - sm.paths[0].points[0].x, accuracy: 1e-4)
    }

    func testSearch() {
        XCTAssertEqual(ComicCharacters.search("robot").count, 13 * 26)
        XCTAssertEqual(ComicCharacters.search("kahraman mutlu", pose: .RUN).count, 1)
    }
}
