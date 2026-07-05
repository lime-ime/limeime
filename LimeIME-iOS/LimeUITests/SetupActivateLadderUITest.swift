import XCTest

/// I2 visual-verify of the Setup active-keyboard ladder.
///
/// iOS-26 simulators can't detect keyboard-enabled, so we force that one axis with
/// `-limeUITestForceKeyboardEnabled 1` (via launchArguments, which reliably reach
/// ProcessInfo.arguments). The *active* axis is NOT forced — the real Darwin fa ping
/// drives it: if LIME is the sim's active keyboard, focusing the probe loads it and it
/// pings, so the app resolves to an active Banner 2 state;
/// otherwise it resolves to enabled-but-not-active and shows the 選用萊姆輸入法 button.
///
/// Either way, the force flag must move the app OFF the red not-enabled state, and the
/// ladder must land in one of the enabled sub-states — proving keyboardEnabled + the
/// active-detection pipeline + the new banner copy all work end-to-end on the sim.
final class SetupActivateLadderUITest: XCTestCase {

    func testForceEnabledResolvesLadderOffNotEnabled() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-limeUITestForceKeyboardEnabled", "1"]
        app.launch()

        let setupTab = app.tabBars.buttons["設定"].exists ? app.tabBars.buttons["設定"] : app.buttons["設定"]
        if setupTab.waitForExistence(timeout: 8) { setupTab.tap() }

        // The red not-enabled banner must be gone (force flag took effect).
        let notEnabledBanner = app.staticTexts["尚未啟用萊姆輸入法鍵盤"]
        XCTAssertFalse(notEnabledBanner.waitForExistence(timeout: 4),
                       "force-enabled must move the app off the red not-enabled state")

        // The ladder must resolve to an enabled sub-state: an active banner (LIME pinged)
        // OR the activate affordance (no ping). Poll up to the active window + margin.
        let activeBanner = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS %@", "目前輸入法")).firstMatch
        let activateButton = app.buttons["選用萊姆輸入法"]
        let deadline = Date().addingTimeInterval(12)
        var resolved = false
        while Date() < deadline {
            if activeBanner.exists || activateButton.exists { resolved = true; break }
            usleep(300_000)
        }
        XCTAssertTrue(resolved,
                      "ladder should resolve to active (目前輸入法) or enabled-not-active (選用萊姆輸入法)")

        add(XCTAttachment(screenshot: app.screenshot()))
    }
}
