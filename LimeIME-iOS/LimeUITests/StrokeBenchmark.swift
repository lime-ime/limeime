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

    /// Drives a deterministic stroke sequence in MobileSafari with a
    /// `data:` URL containing an autofocused `<textarea>`. Safari is
    /// chosen because:
    ///   - It is present on every iOS simulator (Notes is not in iOS
    ///     26.4 simulator runtimes).
    ///   - The `<textarea autofocus>` opens the keyboard immediately on
    ///     load with no UI walking, eliminating per-iOS-version flake.
    ///   - First-party browser → first-party keyboard extension is the
    ///     same dispatch path a real user exercises in any app.
    /// Each character is sent individually so the inter-stroke pause is
    /// not absorbed into a single batched insert; this matches a real
    /// user's typing cadence.
    private func runFixture(im: String, strokes: String) throws {
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        // ensureBenchmarkPage foregrounds Safari and returns the keyboard-
        // focused address field, ready to receive strokes.
        let inputField = try ensureBenchmarkPage(in: safari)

        // NOTE: Keyboard switching via the globe key is intentionally
        // NOT performed here on iOS 26 simulators. Querying
        // `app.keyboards.*` triggers AccessibilitySettingsLoader inside
        // the keyboard extension, which crashes (EXC_GUARD in libxpc
        // during xpc_connection_copy_bundle_id) — Apple bug specific
        // to iPad iOS 26 simulator + a custom keyboard extension under
        // XCUITest's accessibility introspection.
        //
        // Workaround: the simulator must already have LimeIME (萊姆輸入法)
        // pinned as the active keyboard for whichever input language
        // Safari is using. Set it once via Settings → General →
        // Keyboard → Keyboards → Edit → drag to top, then keep it
        // there. The fixture relies on this preconfiguration.
        //
        // If LimeIME is NOT the active keyboard at this point, the
        // strokes will simply be typed via the system keyboard and no
        // signposts will fire — the harness will report 0 samples,
        // which is the canary that this precondition was violated.

        for char in strokes {
            inputField.typeText(String(char))
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

    // MARK: - Helpers

    /// Ensure Safari has a focused text input with the keyboard visible,
    /// and return that input element so the fixture can type strokes into it.
    ///
    /// Previously this navigated to a `data:` URL containing an autofocused
    /// `<textarea>`, but modern Safari refuses to load `data:` URLs entered
    /// in the address bar (a WebKit anti-phishing policy → "Safari cannot
    /// open the page"), so the textarea never appeared and `typeText`
    /// failed with "Neither element nor any descendant has keyboard focus".
    ///
    /// The fixture's only job is to drive IM strokes through the LimeIME
    /// extension so the production-code signposts fire (see the file header);
    /// it does not assert on inserted text. Safari's own address field is a
    /// reliable text input that brings up the active keyboard without any
    /// page navigation — the same surface the passing screenshot tests use —
    /// so we focus it directly.
    ///
    /// We deliberately do NOT reuse a webview textView: a prior failed
    /// navigation can leave a Safari error page ("Safari cannot open the
    /// page…") whose static TextView matches `webViews.textViews` but cannot
    /// take keyboard focus, which caused the strokes to fail.
    @discardableResult
    private func ensureBenchmarkPage(in safari: XCUIApplication) throws -> XCUIElement {
        safari.activate()
        XCTAssertTrue(safari.wait(for: .runningForeground, timeout: 10),
                      "Safari did not become foreground")
        dismissSafariFirstLaunch(in: safari)

        // Tap Safari's address pill (URL / TabBarItemTitle / Address across
        // iOS versions). Tapping it raises the active keyboard.
        let candidateIDs = ["URL", "TabBarItemTitle", "Address"]
        var pill: XCUIElement?
        for id in candidateIDs {
            let predicate = NSPredicate(format: "identifier == %@ OR label == %@", id, id)
            let field = safari.textFields.matching(predicate).firstMatch
            if field.waitForExistence(timeout: 2) { pill = field; break }
        }
        if pill == nil {
            let anyField = safari.textFields.firstMatch
            if anyField.waitForExistence(timeout: 3) { pill = anyField }
        }
        guard let addressPill = pill else {
            XCTFail("Safari address field not found. Tree:\n\(String(safari.debugDescription.prefix(4000)))")
            throw XCTSkip("Safari address field unavailable")
        }
        addressPill.tap()
        dismissSafariFirstLaunch(in: safari)

        // iOS 26 expands the tapped pill into a separate active search field;
        // the original pill stays in the tree, so prefer the keyboard-focused
        // field. Fall back to the pill itself on older layouts.
        let activePred = NSPredicate(format:
            "identifier CONTAINS 'isActive=true' OR hasKeyboardFocus == true")
        let active = safari.descendants(matching: .textField).matching(activePred).firstMatch
        if active.waitForExistence(timeout: 3) {
            return active
        }
        return addressPill
    }

    /// Dismiss Safari's first-launch overlays (iOS 15+ "Continue",
    /// search-suggestions opt-in, and similar one-time prompts). All
    /// taps are best-effort — if the overlay isn't present we just
    /// move on.
    private func dismissSafariFirstLaunch(in app: XCUIApplication) {
        let labels = ["Continue", "Got It", "Got it",
                      "Allow", "Don't Allow", "Not Now",
                      "Maybe Later", "Skip", "Done"]
        for label in labels {
            let b = app.buttons[label]
            if b.waitForExistence(timeout: 1) {
                b.tap()
            }
        }

        let privacyDone = app.buttons["PrivacyReportDoneButton"]
        if privacyDone.waitForExistence(timeout: 1) {
            privacyDone.tap()
        }
    }

    /// Switch the active keyboard to LimeIME. Fails the test if it
    /// cannot, because every rebuild resets the active keyboard to the
    /// system default and a stroke fixture against the system English
    /// keyboard would silently produce zero signposts.
    private func switchToLimeIME(in app: XCUIApplication, im: String) throws {
        let kb = app.keyboards.firstMatch
        XCTAssertTrue(kb.waitForExistence(timeout: 5),
                      "Keyboard did not appear (im=\(im))")

        // If LimeIME is already active, skip.
        if isLimeKeyboardActive(in: app) { return }

        // The globe / next-keyboard button label varies by iOS version
        // and device class. iPad iOS 26 typically exposes it as a
        // button with one of these identifiers/labels.
        let labelCandidates = [
            "Next keyboard", "Next Keyboard",
            "Emoji", "🌐",
            "Choose Input Method",
        ]
        var globe: XCUIElement?
        for label in labelCandidates {
            let b = kb.buttons[label]
            if b.exists { globe = b; break }
        }
        if globe == nil {
            // Last resort: scan keyboard buttons for a globe-ish glyph.
            let pred = NSPredicate(format:
                "label CONTAINS[c] 'globe' OR label CONTAINS[c] 'next' OR label == '🌐'")
            let b = kb.buttons.matching(pred).firstMatch
            if b.exists { globe = b }
        }
        guard let globeButton = globe else {
            XCTFail("""
                Could not find globe / next-keyboard button.
                Keyboard a11y tree:
                \(kb.debugDescription)
                """)
            return
        }

        // Long-press to open the keyboard picker.
        globeButton.press(forDuration: 0.8)

        // Tap the LimeIME entry. The picker surfaces enabled keyboards
        // as buttons whose label is the keyboard's CFBundleDisplayName.
        // LimeKeyboard's display name is "萊姆輸入法" (set in
        // LimeKeyboard/Info.plist) — not "LimeIME". On iPad iOS 26 the
        // picker may render as menu items (cells) under SpringBoard, so
        // search across element types and across both the app and
        // SpringBoard.
        let limePredicate = NSPredicate(format:
            "label CONTAINS '萊姆' OR label CONTAINS[c] 'lime'")
        let springBoard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let candidates: [XCUIElement] = [
            app.buttons.matching(limePredicate).firstMatch,
            app.cells.matching(limePredicate).firstMatch,
            app.menuItems.matching(limePredicate).firstMatch,
            app.staticTexts.matching(limePredicate).firstMatch,
            springBoard.buttons.matching(limePredicate).firstMatch,
            springBoard.cells.matching(limePredicate).firstMatch,
            springBoard.menuItems.matching(limePredicate).firstMatch,
            springBoard.staticTexts.matching(limePredicate).firstMatch,
        ]
        var limeButton: XCUIElement?
        // Allow up to 3s for the picker to appear, polling each candidate.
        let deadline = Date().addingTimeInterval(3)
        outer: while Date() < deadline {
            for c in candidates where c.exists {
                limeButton = c
                break outer
            }
            usleep(100_000)
        }
        guard let lime = limeButton else {
            XCTFail("""
                LimeIME (萊姆輸入法) not found in keyboard picker.
                Make sure it is enabled in Settings → General →
                Keyboard → Keyboards.

                ───── Safari tree (truncated) ─────
                \(String(app.debugDescription.prefix(4000)))

                ───── SpringBoard tree (truncated) ─────
                \(String(springBoard.debugDescription.prefix(4000)))
                """)
            return
        }
        lime.tap()

        // After tapping the picker, the textarea may have lost focus
        // (the picker is modal). Re-focus it so the new keyboard
        // renders. Wait for it — webview elements can take a moment
        // to repopulate the a11y tree after the picker dismisses.
        let safariApp = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        let textArea = safariApp.webViews.textViews.firstMatch
        XCTAssertTrue(textArea.waitForExistence(timeout: 5),
                      "Textarea vanished after keyboard switch (im=\(im))")
        textArea.tap()

        // Verify the switch took effect. Allow up to 2s for the
        // keyboard to swap.
        let deadline2 = Date().addingTimeInterval(2)
        var active = false
        while Date() < deadline2 {
            if isLimeKeyboardActive(in: app) { active = true; break }
            usleep(100_000)
        }
        if !active {
            let kb = app.keyboards.firstMatch
            // Also dump the whole app tree if no keyboard is present.
            let dump = kb.exists
                ? String(kb.debugDescription.prefix(6000))
                : "No keyboard. App tree:\n" +
                  String(app.debugDescription.prefix(6000))
            XCTFail("""
                LimeIME did not become active after globe-key switch
                (im=\(im)).
                \(dump)
                """)
        }
    }

    /// Heuristic: LimeIME is active if its candidate-bar accessibility
    /// elements are present. Falls back to checking for the keyboard's
    /// distinctive identifier if the candidate bar is empty.
    private func isLimeKeyboardActive(in app: XCUIApplication) -> Bool {
        // The LimeKeyboard candidate bar is built from CandidateBarView;
        // its cells/buttons live under the keyboard input view.
        // Cheapest tell: any button label starting with a CJK char while
        // the keyboard is visible. But on first focus the bar is empty,
        // so check for the keyboard input-view identifier instead.
        let kb = app.keyboards.firstMatch
        guard kb.exists else { return false }
        // Look for buttons unique to LimeIME (e.g. the symbol-page
        // toggle "符" or the IM switcher). Adjust if KeyboardView ever
        // changes the labels.
        let pred = NSPredicate(format:
            "label CONTAINS '符' OR label CONTAINS 'ㄅ' OR label CONTAINS '注音' OR label CONTAINS 'Lime'")
        return kb.buttons.matching(pred).firstMatch.exists
    }
}
