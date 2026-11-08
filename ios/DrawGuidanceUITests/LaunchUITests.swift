import XCTest

final class LaunchUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    func testCreateCanvasDrawUndo() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(tr)"]
        app.launch()

        let newArt = app.buttons["new_ART"]
        XCTAssertTrue(newArt.waitForExistence(timeout: 10))
        newArt.tap()

        let canvas = app.otherElements["drawing_canvas"].firstMatch
        let target = canvas.exists ? canvas : app.windows.firstMatch
        let start = target.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.5))
        let end = target.coordinate(withNormalizedOffset: CGVector(dx: 0.7, dy: 0.6))
        start.press(forDuration: 0.1, thenDragTo: end)

        let undo = app.buttons["undo"]
        XCTAssertTrue(undo.waitForExistence(timeout: 5))
        XCTAssertTrue(undo.isEnabled)
        undo.tap()
        XCTAssertTrue(app.buttons["redo"].isEnabled)

        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.lifetime = .keepAlways
        add(shot)
    }
}
