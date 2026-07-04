import XCTest

/// I3 spike go/no-go: proves the insertText relay round-trip works on the sim.
/// The app prefills its probe with the magic token and focuses it; if LIME is the
/// active keyboard, it reads the token from documentContextBeforeInput and types a
/// payload back, which the app decodes and flags via the DEBUG `relayPayloadReceived`
/// accessibility marker. This is independent of the Darwin fa ping.
///
/// Force-enabled so the probes fire on the iOS-26 sim (can't detect enabled otherwise).
final class RelayRoundTripUITest: XCTestCase {

    func testKeyboardTypesRelayPayloadBackToApp() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-limeUITestForceKeyboardEnabled", "1"]
        app.launch()

        let marker = app.staticTexts["relayPayloadReceived"]
        let markerAny = app.descendants(matching: .any)["relayPayloadReceived"]
        let ok = marker.waitForExistence(timeout: 14) || markerAny.waitForExistence(timeout: 2)
        add(XCTAttachment(screenshot: app.screenshot()))
        XCTAssertTrue(ok, "keyboard should type a relay payload back to the app (round-trip)")
    }
}
