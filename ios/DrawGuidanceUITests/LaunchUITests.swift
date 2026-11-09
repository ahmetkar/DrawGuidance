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
        // Fen kategorisine geçilip ilk hazır şekil seçilir
        let physics = app.descendants(matching: .any)["shape_cat_sci_physics"].firstMatch
        XCTAssertTrue(physics.waitForExistence(timeout: 10))
        // Kategori şeridi yatay kayar: çip görünene kadar sola kaydırılır
        var tries = 0
        let window = app.windows.firstMatch.frame
        while !window.contains(physics.frame) && tries < 12 { 
            // Şerit hizasında, pencere koordinatıyla sağdan sola sürüklenir
            // Çip soldaysa sağa, sağdaysa sola; kısa sürüklemeyle (taşmasın)
            let origin = app.windows.firstMatch.coordinate(withNormalizedOffset: .zero)
            let left = physics.frame.minX < window.minX
            let from = left ? 0.3 : 0.7, to = left ? 0.7 : 0.3
            origin.withOffset(CGVector(dx: window.width * from, dy: physics.frame.midY))
                .press(forDuration: 0.05, thenDragTo: origin.withOffset(CGVector(dx: window.width * to, dy: physics.frame.midY)))
            tries += 1
        }
        physics.tap()
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

    func testPickBrushAndDraw() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(tr)"]
        app.launch()
        let newArt = app.buttons["new_ART"]
        XCTAssertTrue(newArt.waitForExistence(timeout: 10))
        newArt.tap()

        let menu = app.descendants(matching: .any)["brush_menu"].firstMatch
        XCTAssertTrue(menu.waitForExistence(timeout: 10))
        menu.tap()
        let marker = app.descendants(matching: .any)["brush_MARKER"].firstMatch
        XCTAssertTrue(marker.waitForExistence(timeout: 10))
        marker.tap()

        let canvas = app.descendants(matching: .any)["drawing_canvas"].firstMatch
        XCTAssertTrue(canvas.waitForExistence(timeout: 10))
        canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.4))
            .press(forDuration: 0.1, thenDragTo: canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5)))
        let undo = app.buttons["undo"]
        expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: undo)
        waitForExpectations(timeout: 5)
    }

    func testModel3DEditorMoveUndo() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(tr)"]
        app.launch()
        let models = app.buttons["home_models3d"]
        XCTAssertTrue(models.waitForExistence(timeout: 10))
        models.tap()
        let new = app.buttons["models3d_new"]
        XCTAssertTrue(new.waitForExistence(timeout: 10))
        new.tap()
        let viewport = app.descendants(matching: .any)["model3d_viewport"].firstMatch
        XCTAssertTrue(viewport.waitForExistence(timeout: 15))
        sleep(2)
        // Küpün üstünden başlayan sürükleme küpü taşır: geri al etkinleşir
        let start = viewport.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let end = viewport.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: 0.5))
        start.press(forDuration: 0.2, thenDragTo: end)
        let undo = app.buttons["model3d_undo"]
        XCTAssertTrue(undo.waitForExistence(timeout: 5))
        expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: undo)
        waitForExpectations(timeout: 8)
        undo.tap()
        XCTAssertTrue(app.buttons["model3d_redo"].isEnabled)
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "model3d_editor"
        shot.lifetime = .keepAlways
        add(shot)
    }
}
