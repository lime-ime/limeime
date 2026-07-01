import XCTest
import UIKit

final class PopupSlideUITest: LimeUITestSupport {

    @MainActor
    func testLongPressSlideIntoPopupCommitsSecondAlternate() throws {
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

        let e = keyCoordinate(in: safari,
                              labels: ["e", "E"],
                              fallback: CGVector(dx: 0.23, dy: 0.70))
        let before = focusedText(in: safari, fallback: input)
        e.press(forDuration: 0.55, thenDragTo: e.withOffset(CGVector(dx: -22, dy: -58)))
        Thread.sleep(forTimeInterval: 0.4)

        let after = focusedText(in: safari, fallback: input)
        XCTAssertEqual(after, before + "é",
                       "Sliding from e into the popup's second alternate should commit é. Before: \(before), after: \(after)")
    }

    @MainActor
    func testSwipeLeftBackspacesAndSwipeRightCommitsCandidate() throws {
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

        let a = keyCoordinate(in: safari,
                              labels: ["a", "A"],
                              fallback: CGVector(dx: 0.12, dy: 0.78))
        let b = keyCoordinate(in: safari,
                              labels: ["b", "B"],
                              fallback: CGVector(dx: 0.57, dy: 0.87))
        let c = keyCoordinate(in: safari,
                              labels: ["c", "C"],
                              fallback: CGVector(dx: 0.39, dy: 0.87))
        let q = keyCoordinate(in: safari,
                              labels: ["q", "Q"],
                              fallback: CGVector(dx: 0.06, dy: 0.70))
        let p = keyCoordinate(in: safari,
                              labels: ["p", "P"],
                              fallback: CGVector(dx: 0.94, dy: 0.70))

        let beforeLetters = focusedText(in: safari, fallback: input)
        a.tap()
        b.tap()
        c.tap()
        Thread.sleep(forTimeInterval: 0.3)
        let afterLetters = focusedText(in: safari, fallback: input)
        XCTAssertEqual(afterLetters, beforeLetters + "abc",
                       "Setup typing should produce abc before swipe-left. Before: \(beforeLetters), after: \(afterLetters)")

        p.press(forDuration: 0.02, thenDragTo: q)
        Thread.sleep(forTimeInterval: 0.4)
        let afterBackspace = focusedText(in: safari, fallback: input)
        XCTAssertEqual(afterBackspace, beforeLetters + "ab",
                       "Swipe-left should invoke backspace once. Before: \(beforeLetters), after: \(afterBackspace)")

        let switchToChinese = safari.descendants(matching: .any)
            .matching(NSPredicate(format: "label == '中' OR identifier == '中'")).firstMatch
        XCTAssertTrue(switchToChinese.waitForExistence(timeout: 2),
                      "Could not find the Chinese mode key after English swipe setup.")
        switchToChinese.tap()
        Thread.sleep(forTimeInterval: 0.5)

        let s = keyCoordinate(in: safari,
                              labels: ["s ㄋ", "s"],
                              fallback: CGVector(dx: 0.15, dy: 0.78))
        let u = keyCoordinate(in: safari,
                              labels: ["u 一", "u"],
                              fallback: CGVector(dx: 0.65, dy: 0.70))
        let tone3 = keyCoordinate(in: safari,
                                  labels: ["3 ˇ", "3"],
                                  fallback: CGVector(dx: 0.25, dy: 0.61))
        s.tap()
        u.tap()
        tone3.tap()
        Thread.sleep(forTimeInterval: 0.8)

        let beforeCommit = focusedText(in: safari, fallback: input)
        XCTAssertTrue(beforeCommit.hasSuffix("su3"),
                      "Setup composing should leave raw phonetic suffix su3 before swipe-right. Text: \(beforeCommit)")
        q.press(forDuration: 0.02, thenDragTo: p)
        Thread.sleep(forTimeInterval: 0.5)

        let afterCommit = focusedText(in: safari, fallback: input)
        XCTAssertNotEqual(afterCommit, beforeCommit,
                          "Swipe-right should commit the highlighted candidate. Before: \(beforeCommit), after: \(afterCommit)")
        XCTAssertFalse(afterCommit.hasSuffix("su3"),
                       "Swipe-right should replace the composing suffix, not leave su3 uncommitted. Text: \(afterCommit)")
    }
}
