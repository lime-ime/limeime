//
//  LimeUITests.swift
//  LimeUITests
//
//  Created by JEREMY WU on 2026/5/2.
//

import XCTest
import UIKit

final class LimeUITests: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.

        // In UI tests it is usually best to stop immediately when a failure occurs.
        continueAfterFailure = false

        // In UI tests it’s important to set the initial state - such as interface orientation - required for your tests before they run. The setUp method is a good place to do this.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    @MainActor
    func testIPadKeyboardAndGlobeLongPressVisuals() throws {
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        safari.activate()
        XCTAssertTrue(safari.wait(for: .runningForeground, timeout: 10),
                      "Safari did not become foreground")
        dismissSafariFirstLaunch(in: safari)
        try focusSafariAddressField(in: safari)
        Thread.sleep(forTimeInterval: 1.0)
        try saveScreenshot(named: "ipad_longpress_00_keyboard")

        // iPad LIME bottom row: keyboard/options key is the far-right bottom key.
        safari.coordinate(withNormalizedOffset: CGVector(dx: 0.965, dy: 0.965))
            .press(forDuration: 0.9)
        Thread.sleep(forTimeInterval: 1.0)
        try saveScreenshot(named: "ipad_longpress_01_keyboard_key_menu")

        safari.coordinate(withNormalizedOffset: CGVector(dx: 0.50, dy: 0.40)).tap()
        Thread.sleep(forTimeInterval: 0.5)

        try focusSafariAddressField(in: safari)
        Thread.sleep(forTimeInterval: 0.5)

        // iPad LIME bottom row: globe key is the far-left bottom key.
        let globeCapture = captureScreenshotDuringHold(named: "ipad_longpress_02_globe_picker")
        safari.coordinate(withNormalizedOffset: CGVector(dx: 0.035, dy: 0.965))
            .press(forDuration: 2.0)
        wait(for: [globeCapture], timeout: 2.0)
    }

    // TEMP DIAGNOSTIC (remove after iPad capture work): focus the normal field with
    // 注音 + Full Access and screenshot whatever renders, with no globe-cycle / candidate
    // assertion, to confirm whether the LIME candidate bar appears on iPad.
    @MainActor
    func testIPadDiagZhuyinNormalField() throws {
        configureKeyboardThemeCaptureDefaults(theme: 0)
        let app = XCUIApplication()
        app.launchArguments += ["-LimeUITestKeyboardTheme", "0", "-LimeUITestKeyboardList", "phonetic"]
        app.launch()
        Thread.sleep(forTimeInterval: 0.5)
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        safari.activate()
        XCTAssertTrue(safari.wait(for: .runningForeground, timeout: 10), "Safari not foreground")
        dismissSafariFirstLaunch(in: safari)
        try? focusKeyboardTestPage(in: safari)
        Thread.sleep(forTimeInterval: 2.0)
        try saveScreenshot(named: "ipad_diag_zhuyin_normal")
        let env = ProcessInfo.processInfo.environment
        let dir = env["LIME_VISUAL_VERIFY_OUTPUT_DIR"]
            ?? env["TEST_RUNNER_LIME_VISUAL_VERIFY_OUTPUT_DIR"] ?? NSTemporaryDirectory()
        try? safari.debugDescription.write(
            to: URL(fileURLWithPath: dir).appendingPathComponent("ipad_diag_tree.txt"),
            atomically: true, encoding: .utf8)
    }

    @MainActor
    func testIOSKeyboardThemeScreenshotSystemLight() throws {
        try captureIOSKeyboardThemeScreenshotScenario(label: "system_light", theme: 6)
    }

    @MainActor
    func testIOSKeyboardThemeScreenshotLight() throws {
        try captureIOSKeyboardThemeScreenshotScenario(label: "light", theme: 0)
    }

    @MainActor
    func testIOSKeyboardThemeScreenshotDark() throws {
        try captureIOSKeyboardThemeScreenshotScenario(label: "dark", theme: 1)
    }

    @MainActor
    func testIOSKeyboardThemeScreenshotSystemDark() throws {
        try captureIOSKeyboardThemeScreenshotScenario(label: "system_dark", theme: 6)
    }

    @MainActor
    func testIOSKeyboardThemeScreenshotPink() throws {
        try captureIOSKeyboardThemeScreenshotScenario(label: "pink", theme: 2)
    }

    @MainActor
    func testIOSKeyboardThemeScreenshotTechBlue() throws {
        try captureIOSKeyboardThemeScreenshotScenario(label: "tech_blue", theme: 3)
    }

    @MainActor
    func testIOSKeyboardThemeScreenshotFashionPurple() throws {
        try captureIOSKeyboardThemeScreenshotScenario(label: "fashion_purple", theme: 4)
    }

    @MainActor
    func testIOSKeyboardThemeScreenshotRelaxGreen() throws {
        try captureIOSKeyboardThemeScreenshotScenario(label: "relax_green", theme: 5)
    }

    @MainActor
    func testIOSEmojiSearchDismissReturnsToKeyboard() throws {
        configureKeyboardThemeCaptureDefaults(theme: 6)
        let app = XCUIApplication()
        app.launch()
        Thread.sleep(forTimeInterval: 0.5)

        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        safari.activate()
        XCTAssertTrue(safari.wait(for: .runningForeground, timeout: 10),
                      "Safari did not become foreground")
        dismissSafariFirstLaunch(in: safari)
        try focusKeyboardTestPage(in: safari)
        try cycleToLimeKeyboard(in: safari, scenario: "emoji_search_dismiss")
        try ensureLimeChineseKeyboardVisible(in: safari, scenario: "emoji_search_dismiss")

        // Open the emoji panel by tapping the candidate-bar emoji button via its
        // accessibility id rather than a hardcoded normalized coordinate, which
        // misses the key across themes / layouts. This button is the same stop
        // condition cycleToLimeKeyboard waited on, so it is present and hittable.
        let candidateEmojiButton = safari.descendants(matching: .any)["lime_candidate_bar_emoji_button"]
        XCTAssertTrue(candidateEmojiButton.waitForExistence(timeout: 3),
                      "LIME candidate-bar emoji button not found.")
        candidateEmojiButton.tap()
        // The emoji panel's own search field (distinct id from the in-search
        // header field) is the one whose tap begins search mode.
        let searchField = safari.descendants(matching: .any)["lime_emoji_panel_search_field"]
        XCTAssertTrue(searchField.waitForExistence(timeout: 3),
                      "LIME emoji search field did not appear.")

        searchField.tap()
        // Entering search mode hides the emoji panel and surfaces the candidate
        // bar's leading xmark as the dismiss control (the panel's own
        // searchDismissButton is never shown in this flow). Target that.
        let dismissButton = safari.descendants(matching: .any)["lime_candidate_bar_dismiss_button"]
        XCTAssertTrue(dismissButton.waitForExistence(timeout: 3),
                      "Emoji search dismiss button did not appear in search mode.")

        dismissButton.tap()
        Thread.sleep(forTimeInterval: 1.0)
        XCTAssertTrue(hasLimeCandidateBarEmoji(in: safari),
                      "Tapping emoji search dismiss did not return to the LIME keyboard.")
    }

    @MainActor
    private func captureIOSKeyboardThemeScreenshotScenario(label: String, theme: Int) throws {
        configureKeyboardThemeCaptureDefaults(theme: theme)
        let app = XCUIApplication()
        // The UITest runner cannot join the app group, so its UserDefaults writes never
        // reach the keyboard extension. Pass the theme + IM as launch arguments; the host
        // app (a group member) applies them to the shared defaults on launch, so the
        // keyboard restores 注音 (phonetic) in the requested theme.
        app.launchArguments += [
            "-LimeUITestKeyboardTheme", "\(theme)",
            "-LimeUITestKeyboardList", "phonetic",
        ]
        app.launch()
        Thread.sleep(forTimeInterval: 0.5)

        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        safari.activate()
        XCTAssertTrue(safari.wait(for: .runningForeground, timeout: 10),
                      "Safari did not become foreground")
        dismissSafariFirstLaunch(in: safari)
        try focusKeyboardTestPage(in: safari)
        try cycleToLimeKeyboard(in: safari, scenario: label)
        try ensureLimeChineseKeyboardVisible(in: safari, scenario: label)
        Thread.sleep(forTimeInterval: 1.5)

        dismissTextEditingMenuIfVisible(in: safari)
        try saveScreenshot(named: "ios_keyboard_zhuyin_\(label)")

        let abcModeKey = safari.descendants(matching: .any)
            .matching(NSPredicate(format: "label ==[c] 'ABC' OR identifier ==[c] 'ABC'")).firstMatch
        if abcModeKey.waitForExistence(timeout: 1) {
            abcModeKey.tap()
        } else {
            safari.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.955)).tap()
        }
        Thread.sleep(forTimeInterval: 1.0)
        try ensureLimeEnglishKeyboardVisible(in: safari, scenario: label)
        dismissTextEditingMenuIfVisible(in: safari)
        try saveScreenshot(named: "ios_keyboard_english_\(label)")

        let candidateEmojiButton = safari.descendants(matching: .any)["lime_candidate_bar_emoji_button"]
        if candidateEmojiButton.waitForExistence(timeout: 1) {
            candidateEmojiButton.tap()
        } else {
            safari.coordinate(withNormalizedOffset: CGVector(dx: 0.05, dy: 0.64)).tap()
        }
        Thread.sleep(forTimeInterval: 1.0)
        dismissTextEditingMenuIfVisible(in: safari)
        try saveScreenshot(named: "ios_emoji_panel_\(label)")
    }

    private func dismissTextEditingMenuIfVisible(in app: XCUIApplication) {
        let editMenuItems = ["Paste", "AutoFill", "Share..."]
        let hasEditMenu = editMenuItems.contains { label in
            app.menuItems.matching(Self.modeKeyPredicate(label)).firstMatch.exists
        }
        guard hasEditMenu else { return }

        app.typeText(" ")
        Thread.sleep(forTimeInterval: 0.3)
    }

    private func captureScreenshotDuringHold(named name: String) -> XCTestExpectation {
        let exp = expectation(description: name)
        DispatchQueue.global(qos: .userInitiated).asyncAfter(deadline: .now() + 0.8) { [weak self] in
            guard let self else {
                exp.fulfill()
                return
            }
            let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            attachment.name = name
            attachment.lifetime = .keepAlways
            self.add(attachment)
            exp.fulfill()
        }
        return exp
    }

    @MainActor
    func testExample() throws {
        // UI tests must launch the application that they test.
        let app = XCUIApplication()
        app.launch()

        // Use XCTAssert and related functions to verify your tests produce the correct results.
        // XCUIAutomation Documentation
        // https://developer.apple.com/documentation/xcuiautomation
    }

    @MainActor
    func testLaunchPerformance() throws {
        // This measures how long it takes to launch your application.
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            XCUIApplication().launch()
        }
    }

    private func focusSafariAddressField(in app: XCUIApplication) throws {
        let candidateIDs = ["URL", "TabBarItemTitle", "Address"]
        for id in candidateIDs {
            let predicate = NSPredicate(format: "identifier == %@ OR label == %@", id, id)
            let field = app.textFields.matching(predicate).firstMatch
            if field.waitForExistence(timeout: 2) {
                field.tap()
                return
            }
        }

        let anyField = app.textFields.firstMatch
        if anyField.waitForExistence(timeout: 3) {
            anyField.tap()
            return
        }

        let anyTextView = app.textViews.firstMatch
        if anyTextView.waitForExistence(timeout: 3) {
            anyTextView.tap()
            return
        }

        XCTFail("Safari address field not found. Tree:\n\(String(app.debugDescription.prefix(4000)))")
    }

    private func openKeyboardTestPage(_ url: String, in app: XCUIApplication) throws {
        try focusSafariAddressField(in: app)
        app.typeText(url)
        app.typeText("\n")
        Thread.sleep(forTimeInterval: 2.0)
    }

    private func focusKeyboardTestPage(in app: XCUIApplication) throws {
        let textView = app.textViews.firstMatch
        if textView.waitForExistence(timeout: 3) {
            for _ in 0..<3 {
                textView.tap()
                Thread.sleep(forTimeInterval: 0.8)
                if hasKeyboardSurface(in: app) {
                    return
                }
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.50, dy: 0.30)).tap()
            }
        }

        let textField = app.textFields.firstMatch
        if textField.waitForExistence(timeout: 3) {
            for _ in 0..<3 {
                textField.tap()
                Thread.sleep(forTimeInterval: 0.8)
                if hasKeyboardSurface(in: app) {
                    return
                }
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.50, dy: 0.30)).tap()
            }
        }

        for _ in 0..<3 {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.50, dy: 0.28)).tap()
            Thread.sleep(forTimeInterval: 0.8)
            if hasKeyboardSurface(in: app) {
                return
            }
        }

        XCTFail("Keyboard did not appear after focusing the screenshot test page.")
    }

    private func switchToLimeIME(in app: XCUIApplication, scenario: String) throws {
        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 5),
                      "Keyboard did not appear for \(scenario)")

        if isLimeKeyboardActive(in: app) { return }

        let labelCandidates = [
            "Next keyboard", "Next Keyboard",
            "Emoji", "🌐",
            "Choose Input Method",
        ]
        var globe: XCUIElement?
        for label in labelCandidates {
            let button = keyboard.buttons[label]
            if button.exists {
                globe = button
                break
            }
        }
        if globe == nil {
            let predicate = NSPredicate(format:
                "label CONTAINS[c] 'globe' OR label CONTAINS[c] 'next' OR label == '🌐'")
            let button = keyboard.buttons.matching(predicate).firstMatch
            if button.exists { globe = button }
        }
        guard let globeButton = globe else {
            XCTFail("""
                Could not find globe / next-keyboard button for \(scenario).
                Keyboard tree:
                \(String(keyboard.debugDescription.prefix(4000)))
                """)
            return
        }

        globeButton.press(forDuration: 0.8)

        let limePredicate = NSPredicate(format:
            "label CONTAINS '萊姆' OR label CONTAINS[c] 'lime'")
        let springBoard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        var limeButton: XCUIElement?
        let deadline = Date().addingTimeInterval(3)
        while Date() < deadline {
            for candidate in limeKeyboardPickerCandidates(app: app, springBoard: springBoard, predicate: limePredicate) {
                let label = candidate.label
                if label.contains("萊姆") || label.localizedCaseInsensitiveContains("lime") {
                    limeButton = candidate
                    break
                }
            }
            if limeButton != nil { break }
            usleep(100_000)
        }
        guard let lime = limeButton else {
            XCTFail("""
                LimeIME (萊姆輸入法) was not found in the keyboard picker for \(scenario).
                Enable it first in Settings > General > Keyboard > Keyboards.
                Safari tree:
                \(String(app.debugDescription.prefix(4000)))
                SpringBoard tree:
                \(String(springBoard.debugDescription.prefix(4000)))
                """)
            return
        }

        lime.tap()
        try focusKeyboardTestPage(in: app)

        let switchDeadline = Date().addingTimeInterval(2)
        while Date() < switchDeadline {
            if isLimeKeyboardActive(in: app) { return }
            usleep(100_000)
        }

        XCTFail("""
            LimeIME did not become active after keyboard picker selection for \(scenario).
            Keyboard tree:
            \(String(app.keyboards.firstMatch.debugDescription.prefix(5000)))
        """)
    }

    private func limeKeyboardPickerCandidates(
        app: XCUIApplication,
        springBoard: XCUIApplication,
        predicate: NSPredicate
    ) -> [XCUIElement] {
        let queries = [
            app.buttons.matching(predicate),
            app.cells.matching(predicate),
            app.menuItems.matching(predicate),
            app.staticTexts.matching(predicate),
            springBoard.buttons.matching(predicate),
            springBoard.cells.matching(predicate),
            springBoard.menuItems.matching(predicate),
            springBoard.staticTexts.matching(predicate),
            app.descendants(matching: .any).matching(predicate),
            springBoard.descendants(matching: .any).matching(predicate),
        ]
        return queries.flatMap { $0.allElementsBoundByIndex }
    }

    private func ensureLimeChineseKeyboardVisible(in app: XCUIApplication, scenario: String) throws {
        let abcModeKey = app.descendants(matching: .any)
            .matching(Self.modeKeyPredicate("ABC"))
            .firstMatch
        if abcModeKey.exists {
            abcModeKey.tap()
            Thread.sleep(forTimeInterval: 0.8)
        }

        if hasLimePhoneticLayout(in: app), hasLimeCandidateBarEmoji(in: app) || isPadIdiom {
            return
        }

        let chineseModeKey = app.descendants(matching: .any)
            .matching(Self.modeKeyPredicate("中"))
            .firstMatch
        if chineseModeKey.exists {
            chineseModeKey.tap()
            Thread.sleep(forTimeInterval: 0.8)
        }

        XCTAssertTrue(
            hasLimePhoneticLayout(in: app) && (hasLimeCandidateBarEmoji(in: app) || isPadIdiom),
            """
            LIME keyboard was active but not on the 注音 Chinese keyboard for \(scenario).
            App tree:
            \(String(app.debugDescription.prefix(5000)))
            """
        )
    }

    private func ensureLimeEnglishKeyboardVisible(in app: XCUIApplication, scenario: String) throws {
        XCTAssertTrue(
            hasLimeEnglishLayout(in: app) && (hasLimeCandidateBarEmoji(in: app) || isPadIdiom),
            """
            LIME keyboard was active but did not switch to English for \(scenario).
            App tree:
            \(String(app.debugDescription.prefix(5000)))
            """
        )
    }

    private static func modeKeyPredicate(_ label: String) -> NSPredicate {
        NSPredicate(format: "identifier == %@ OR label == %@", label, label)
    }

    private func isLimeKeyboardActive(in app: XCUIApplication) -> Bool {
        hasLimeCandidateBarEmoji(in: app)
    }

    private var isPadIdiom: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    /// LIME-active signal. iPhone uses the candidate-bar emoji launcher icon; the iPad
    /// LIME candidate bar omits that icon, so fall back to LIME-specific key layouts
    /// (bopomofo / 中 + qwerty) which are present on both platforms.
    private func hasLimeKeyboardSignal(in app: XCUIApplication) -> Bool {
        if hasLimeCandidateBarEmoji(in: app) { return true }
        if isPadIdiom { return hasLimePhoneticLayout(in: app) || hasLimeEnglishLayout(in: app) }
        return false
    }

    private func cycleToLimeKeyboard(in app: XCUIApplication, scenario: String) throws {
        for _ in 0..<8 {
            if hasLimeKeyboardSignal(in: app) { return }
            tapGlobeKey(in: app)
            Thread.sleep(forTimeInterval: 0.8)
        }

        XCTFail("""
            LIME keyboard was not found by repeated globe taps for \(scenario).
            The stop condition is the LIME candidate-bar emoji icon, not generic 注音 keys.
            App tree:
            \(String(app.debugDescription.prefix(6000)))
            """)
    }

    private func tapGlobeKey(in app: XCUIApplication) {
        let keyboard = app.keyboards.firstMatch
        let labels = ["Next keyboard", "Next Keyboard", "Globe", "🌐"]
        for label in labels {
            let button = keyboard.buttons[label]
            if button.exists {
                button.tap()
                return
            }
        }

        let predicate = NSPredicate(format:
            "label CONTAINS[c] 'globe' OR label CONTAINS[c] 'next' OR label == '🌐'")
        let button = keyboard.buttons.matching(predicate).firstMatch
        if button.exists {
            button.tap()
            return
        }

        app.coordinate(withNormalizedOffset: CGVector(dx: 0.08, dy: 0.955)).tap()
    }

    private func hasLimeCandidateBarEmoji(in app: XCUIApplication) -> Bool {
        if app.descendants(matching: .any)["lime_candidate_bar_emoji_button"].exists {
            return true
        }
        let predicate = NSPredicate(format:
            "identifier == 'lime_candidate_bar_emoji_button' OR label == 'LIME candidate bar emoji'")
        return app.descendants(matching: .any).matching(predicate).firstMatch.exists
    }

    private func hasLimePhoneticLayout(in app: XCUIApplication) -> Bool {
        // iPhone labels combine the roman key + bopomofo ('1 ㄅ'); iPad labels the
        // bopomofo glyph alone ('ㄅ'). Match both so detection works on each idiom.
        let predicate = NSPredicate(format:
            "label == '1 ㄅ' OR label == 'q ㄆ' OR label == 'w ㄊ' OR label == 'ㄅ' OR label == 'ㄆ' OR label == 'ㄓ'")
        return app.descendants(matching: .any).matching(predicate).firstMatch.exists
    }

    private func hasLimeEnglishLayout(in app: XCUIApplication) -> Bool {
        guard app.descendants(matching: .any)["中"].exists else { return false }
        let predicate = NSPredicate(format:
            "label == 'q' OR label == 'w' OR label == 'e'")
        return app.descendants(matching: .any).matching(predicate).firstMatch.exists
    }

    private func hasKeyboardSurface(in app: XCUIApplication) -> Bool {
        if app.keyboards.firstMatch.exists { return true }
        if hasLimeCandidateBarEmoji(in: app) { return true }
        let predicate = NSPredicate(format:
            "label CONTAINS[c] 'globe' OR label == '123' OR label == 'return'")
        return app.descendants(matching: .any).matching(predicate).firstMatch.exists
    }

    private func configureKeyboardThemeCaptureDefaults(theme: Int) {
        guard let defaults = UserDefaults(suiteName: "group.org.limeime") else { return }
        defaults.set(theme, forKey: "keyboard_theme")
        defaults.set("phonetic", forKey: "keyboard_list")
        defaults.set("standard", forKey: "phonetic_keyboard_type")
        defaults.set(true, forKey: "enable_emoji")
        defaults.set(5, forKey: "enable_emoji_position")
        defaults.set("", forKey: "keyboard_state")
        defaults.synchronize()
    }

    private func dismissSafariFirstLaunch(in app: XCUIApplication) {
        let labels = ["Continue", "Got It", "Got it", "Allow", "Don't Allow", "Not Now", "Maybe Later", "Skip"]
        for label in labels {
            let button = app.buttons[label]
            if button.waitForExistence(timeout: 1) {
                button.tap()
            }
        }
    }

    private func saveScreenshot(named name: String) throws {
        // xcodebuild does not propagate plain shell env vars into the test-runner
        // process, but it DOES forward TEST_RUNNER_-prefixed vars (with the prefix
        // stripped). Accept either so the output dir reaches saveScreenshot; fall back
        // to the runner tmp dir.
        let env = ProcessInfo.processInfo.environment
        let outputDir = env["LIME_VISUAL_VERIFY_OUTPUT_DIR"]
            ?? env["TEST_RUNNER_LIME_VISUAL_VERIFY_OUTPUT_DIR"]
            ?? NSTemporaryDirectory()
        let url = URL(fileURLWithPath: outputDir).appendingPathComponent("\(name).png")
        try XCUIScreen.main.screenshot().pngRepresentation.write(to: url)
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
