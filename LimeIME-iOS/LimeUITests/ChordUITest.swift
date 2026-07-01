import XCTest
import UIKit

final class ChordUITest: LimeUITestSupport {

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

}
