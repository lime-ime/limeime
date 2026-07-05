import XCTest

/// Deterministically shows the enabled-but-not-active activate guide by forcing both
/// keyboard-enabled and not-active (the sim's LIME keeps loading + pinging, so it can't
/// naturally sit in Banner 2 notActive). Verifies the 選用萊姆輸入法 button + 長按 🌐 hint.
final class GuideScreenshotUITest: XCTestCase {

    func testEnabledNotActiveShowsActivateGuide() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-limeUITestForceKeyboardEnabled", "1",
                                "-limeUITestForceNotActive", "1"]
        app.launch()

        let button = app.buttons["選用萊姆輸入法"]
        XCTAssertTrue(button.waitForExistence(timeout: 10),
                      "enabled-but-not-active should show the 選用萊姆輸入法 activate button")
        XCTAssertTrue(app.staticTexts["長按 🌐 選用萊姆輸入法"].exists,
                      "activate rung should show the 長按 🌐 選用萊姆輸入法 hint")
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.lifetime = .keepAlways
        add(shot)
    }
}
