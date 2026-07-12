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
