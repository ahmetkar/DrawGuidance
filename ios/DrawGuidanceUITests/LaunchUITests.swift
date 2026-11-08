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

        // Düzenleyici açılmadan sürüklenirse dokunuş başka yere gider: önce tuvalin görünmesi beklenir
        let canvas = app.descendants(matching: .any)["drawing_canvas"].firstMatch
        XCTAssertTrue(canvas.waitForExistence(timeout: 10))
        let target = canvas
        let start = target.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.5))
        let end = target.coordinate(withNormalizedOffset: CGVector(dx: 0.7, dy: 0.6))
        start.press(forDuration: 0.1, thenDragTo: end)

        let undo = app.buttons["undo"]
        XCTAssertTrue(undo.waitForExistence(timeout: 5))
        let enabled = NSPredicate(format: "isEnabled == true")
        expectation(for: enabled, evaluatedWith: undo)
        waitForExpectations(timeout: 5)
        undo.tap()
        XCTAssertTrue(app.buttons["redo"].isEnabled)

        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.lifetime = .keepAlways
        add(shot)
    }

    func testInsertLibraryShape() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(tr)"]
        app.launch()
        let newNotes = app.buttons["new_NOTES"]
        XCTAssertTrue(newNotes.waitForExistence(timeout: 10))
        newNotes.tap()

        let tool = app.descendants(matching: .any)["tool_shape"].firstMatch
        XCTAssertTrue(tool.waitForExistence(timeout: 10))
        tool.tap()
        let first = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'shape_lib.'")).firstMatch
        XCTAssertTrue(first.waitForExistence(timeout: 10))
        first.tap()

        let canvas = app.descendants(matching: .any)["drawing_canvas"].firstMatch
        XCTAssertTrue(canvas.waitForExistence(timeout: 10))
        canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let undo = app.buttons["undo"]
        expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: undo)
        waitForExpectations(timeout: 5)

        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "shape-inserted"
        shot.lifetime = .keepAlways
        add(shot)
    }
}
