import XCTest
import UIKit

final class ChordUITest: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testShiftHoldAndRolloverTypeExpectedCharacters() throws {
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

        let shift = keyCoordinate(in: safari,
                                  labels: ["shift", "shift.fill", "capslock.fill"],
                                  fallback: CGVector(dx: 0.08, dy: 0.87))
        let a = keyCoordinate(in: safari,
                              labels: ["a", "A"],
                              fallback: CGVector(dx: 0.12, dy: 0.78))
        let s = keyCoordinate(in: safari,
                              labels: ["s", "S"],
                              fallback: CGVector(dx: 0.21, dy: 0.78))

        let holdShift = expectation(description: "hold shift")
        DispatchQueue.global(qos: .userInitiated).async {
            shift.press(forDuration: 0.8)
            holdShift.fulfill()
        }
        Thread.sleep(forTimeInterval: 0.15)
        a.tap()
        wait(for: [holdShift], timeout: 2)
        Thread.sleep(forTimeInterval: 0.3)
        XCTAssertTrue(focusedText(in: safari, fallback: input).contains("A"),
                      "Holding shift while tapping a did not type uppercase A. Text: \(focusedText(in: safari, fallback: input))")

        let beforeRollover = focusedText(in: safari, fallback: input)
        let holdA = expectation(description: "hold a")
        DispatchQueue.global(qos: .userInitiated).async {
            a.press(forDuration: 0.5)
            holdA.fulfill()
        }
        Thread.sleep(forTimeInterval: 0.12)
        s.tap()
        wait(for: [holdA], timeout: 2)
        Thread.sleep(forTimeInterval: 0.3)
        let afterRollover = focusedText(in: safari, fallback: input)
        let rolloverText = String(afterRollover.dropFirst(beforeRollover.count))
        XCTAssertTrue(afterRollover.hasSuffix("as") || rolloverText.contains("as"),
                      "Rollover a+s did not type as in order. Before: \(beforeRollover), after: \(afterRollover)")
    }

    private func focusSafariAddressField(in safari: XCUIApplication) throws -> XCUIElement {
        safari.activate()
        XCTAssertTrue(safari.wait(for: .runningForeground, timeout: 10), "Safari did not become foreground")
        dismissSafariFirstLaunch(in: safari)

        for id in ["URL", "TabBarItemTitle", "Address"] {
            let predicate = NSPredicate(format: "identifier == %@ OR label == %@", id, id)
            let field = safari.textFields.matching(predicate).firstMatch
            if field.waitForExistence(timeout: 2) {
                field.tap()
                return activeTextField(in: safari, fallback: field)
            }
        }

        let field = safari.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 3),
                      "Safari address field not found. Tree:\n\(String(safari.debugDescription.prefix(4000)))")
        field.tap()
        return activeTextField(in: safari, fallback: field)
    }

    private func activeTextField(in app: XCUIApplication, fallback: XCUIElement) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier CONTAINS 'isActive=true' OR hasKeyboardFocus == true")
        let active = app.descendants(matching: .textField).matching(predicate).firstMatch
        return active.waitForExistence(timeout: 2) ? active : fallback
    }

    private func switchToLimeIME(in app: XCUIApplication) throws {
        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 5), "Keyboard did not appear")
        if hasLimeKeyboardSignal(in: app) { return }

        let labels = ["Next keyboard", "Next Keyboard", "Emoji", "🌐", "Choose Input Method"]
        var globe: XCUIElement?
        for label in labels where keyboard.buttons[label].exists {
            globe = keyboard.buttons[label]
            break
        }
        if globe == nil {
            let predicate = NSPredicate(format: "label CONTAINS[c] 'globe' OR label CONTAINS[c] 'next' OR label == '🌐'")
            let button = keyboard.buttons.matching(predicate).firstMatch
            if button.exists { globe = button }
        }
        guard let globeButton = globe else {
            XCTFail("Could not find globe / next-keyboard button. Keyboard tree:\n\(String(keyboard.debugDescription.prefix(4000)))")
            return
        }

        globeButton.press(forDuration: 0.8)
        let limePredicate = NSPredicate(format: "label CONTAINS '萊姆' OR label CONTAINS[c] 'lime'")
        let springBoard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let deadline = Date().addingTimeInterval(3)
        while Date() < deadline {
            for query in [
                app.buttons.matching(limePredicate),
                app.cells.matching(limePredicate),
                app.menuItems.matching(limePredicate),
                app.staticTexts.matching(limePredicate),
                springBoard.buttons.matching(limePredicate),
                springBoard.cells.matching(limePredicate),
                springBoard.menuItems.matching(limePredicate),
                springBoard.staticTexts.matching(limePredicate),
            ] {
                let candidate = query.firstMatch
                if candidate.exists {
                    candidate.tap()
                    Thread.sleep(forTimeInterval: 0.6)
                    return
                }
            }
            usleep(100_000)
        }

        XCTFail("LimeIME was not found in the keyboard picker. Enable it in simulator Settings first.")
    }

    private func switchToEnglishIfNeeded(in app: XCUIApplication) {
        if app.descendants(matching: .any).matching(NSPredicate(format: "label == 'a' OR label == 'q'")).firstMatch.exists {
            return
        }
        let abc = app.descendants(matching: .any).matching(NSPredicate(format: "label == 'ABC' OR identifier == 'ABC'")).firstMatch
        if abc.waitForExistence(timeout: 1) {
            abc.tap()
            Thread.sleep(forTimeInterval: 0.5)
        }
    }

    private func keyCoordinate(in app: XCUIApplication,
                               labels: [String],
                               fallback: CGVector) -> XCUICoordinate {
        let predicate = NSPredicate(format: labels.map { _ in "label ==[c] %@" }.joined(separator: " OR "),
                                    argumentArray: labels)
        let element = app.descendants(matching: .any).matching(predicate).firstMatch
        if element.waitForExistence(timeout: 1) {
            let frame = element.frame
            return app.coordinate(withNormalizedOffset: CGVector(dx: frame.midX / app.frame.width,
                                                                 dy: frame.midY / app.frame.height))
        }
        return app.coordinate(withNormalizedOffset: fallback)
    }

    private func focusedText(in app: XCUIApplication, fallback: XCUIElement) -> String {
        let field = activeTextField(in: app, fallback: fallback)
        return field.value as? String ?? ""
    }

    private func hasLimeKeyboardSignal(in app: XCUIApplication) -> Bool {
        if app.descendants(matching: .any)["lime_candidate_bar_emoji_button"].exists { return true }
        let predicate = NSPredicate(format: "label == 'ABC' OR label == '中' OR label == 'ㄅ' OR label == 'q ㄆ'")
        return app.descendants(matching: .any).matching(predicate).firstMatch.exists
    }

    private func dismissSafariFirstLaunch(in app: XCUIApplication) {
        for label in ["Continue", "Got It", "Got it", "Allow", "Don't Allow", "Not Now", "Maybe Later", "Skip", "Done"] {
            let button = app.buttons[label]
            if button.waitForExistence(timeout: 1) {
                button.tap()
            }
        }
    }
}
