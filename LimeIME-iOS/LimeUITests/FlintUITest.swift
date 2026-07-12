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
import UIKit

final class FlintUITest: LimeUITestSupport {

    @MainActor
    func testDragCommitsReleaseKeyAndWobbleCommitsStartKeyOnce() throws {
        let app = XCUIApplication()
        app.launchArguments += [
            "-LimeUITestKeyboardTheme", "0",
            "-LimeUITestKeyboardList", "phonetic",
        ]
        app.launch()

        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        let input = try focusSafariAddressField(in: safari)
        try switchToLimeIME(in: safari)
        switchToEnglishIfNeeded(in: safari)

        let q = keyCoordinate(in: safari,
                              labels: ["q", "Q"],
                              fallback: CGVector(dx: 0.06, dy: 0.70))
        let r = keyCoordinate(in: safari,
                              labels: ["r", "R"],
                              fallback: CGVector(dx: 0.36, dy: 0.70))

        let beforeDrag = focusedText(in: safari, fallback: input)
        q.press(forDuration: 0.15, thenDragTo: r)
        Thread.sleep(forTimeInterval: 0.4)
        let afterDrag = focusedText(in: safari, fallback: input)
        XCTAssertEqual(afterDrag, beforeDrag + "r",
                       "q-to-r drag should commit only the release key. Before: \(beforeDrag), after: \(afterDrag)")

        let beforeWobble = afterDrag
        q.press(forDuration: 0.15, thenDragTo: q.withOffset(CGVector(dx: 6, dy: 0)))
        Thread.sleep(forTimeInterval: 0.4)
        let afterWobble = focusedText(in: safari, fallback: input)
        XCTAssertEqual(afterWobble, beforeWobble + "q",
                       "Sub-hysteresis wobble should type the start key once. Before: \(beforeWobble), after: \(afterWobble)")
    }
}
