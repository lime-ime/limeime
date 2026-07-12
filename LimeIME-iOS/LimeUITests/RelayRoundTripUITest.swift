/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

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
