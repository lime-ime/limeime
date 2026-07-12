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

//
//  StrokeBenchmark.swift
//  LimeTests
//
//  XCUITest fixture that drives a deterministic stroke sequence for each
//  IM. Pairs with `scripts/profile_keyboard.py`, which records an
//  Instruments trace around this test and parses the `os_signpost`
//  intervals emitted from production code (see docs/IOS_PROFILING.md).
//
//  IMPORTANT: This file lives in the LimeTests folder for repo-layout
//  reasons, but it is an XCUITest (`XCTestCase` driving `XCUIApplication`)
//  and therefore requires a UI-test target — NOT a unit-test target.
//  XCUITest cannot run inside a `bundle.unit-test` host.
//
//  Wiring (one-time):
//    1. Add a UI-test target to `LimeIME-iOS/project.yml` that includes
//       only this file (exclude it from the existing `LimeIMETests`
//       unit-test target):
//
//         LimeIMEUITests:
//           type: bundle.ui-testing
//           platform: iOS
//           sources:
//             - path: LimeTests
//               includes:
//                 - "StrokeBenchmark.swift"
//           dependencies:
//             - target: LimeIME      # the host app
//           settings:
//             base:
//               PRODUCT_BUNDLE_IDENTIFIER: org.limeime.uitests
//               TEST_TARGET_NAME: LimeIME
//
//       And exclude it from `LimeIMETests`:
//
//         LimeIMETests:
//           sources:
//             - path: LimeTests
//               excludes:
//                 - "StrokeBenchmark.swift"
//
//    2. Re-run `xcodegen generate` and commit the new project file.
//    3. Pre-enable LimeIME in iOS Settings on the target simulator and
//       set it as the default Chinese keyboard so `typeText` routes
//       through the extension.
//
//  This fixture's only job is to generate strokes. All timing is
//  captured by signposts already embedded in production code paths.
//

import XCTest

final class StrokeBenchmark: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    // MARK: - Per-IM tests (names referenced by profile_keyboard.py)

    func testPhonetic() throws {
        try runFixture(im: "phonetic",
                       strokes: "wo3 jiao4 li2 ming2 ")
    }

    func testCangjie() throws {
        try runFixture(im: "cj",
                       strokes: "r5,e ji jq ")
    }

    func testArray() throws {
        try runFixture(im: "array",
                       strokes: "wlw rlr ")
    }

    // MARK: - Driver

    /// Drives a deterministic stroke sequence in the iOS Notes app.
    /// Each character is sent individually so the inter-stroke pause is
    /// not absorbed into a single batched insert; this matches a real
    /// user's typing cadence.
    private func runFixture(im: String, strokes: String) throws {
        let app = XCUIApplication(bundleIdentifier: "com.apple.mobilenotes")
        app.launch()

        // Open a new note.
        let composeButton = app.buttons["ComposeButton"]
        if composeButton.waitForExistence(timeout: 5) {
            composeButton.tap()
        }

        // Focus the body text view.
        let textView = app.textViews.firstMatch
        XCTAssertTrue(textView.waitForExistence(timeout: 5),
                      "Notes text view did not appear (im=\(im))")
        textView.tap()

        // NOTE: Switching the active keyboard programmatically is not
        // possible from XCUITest. The reference simulator must already
        // have LimeIME pinned as the active Chinese keyboard.

        for char in strokes {
            textView.typeText(String(char))
            // Tiny pause so signposts from the previous stroke close
            // before the next stroke begins. Keep this small enough that
            // the trace still captures genuine inter-stroke latency.
            usleep(20_000)  // 20 ms
        }

        // Allow the stage-2 candidate swap and any deferred UI work to
        // complete so all `Stroke` signposts are closed before xctrace
        // stops recording.
        Thread.sleep(forTimeInterval: 0.5)
    }
}
