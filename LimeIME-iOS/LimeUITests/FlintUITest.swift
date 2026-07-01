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
