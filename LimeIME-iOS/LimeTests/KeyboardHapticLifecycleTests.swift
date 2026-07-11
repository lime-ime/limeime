import XCTest
import UIKit

final class KeyboardHapticLifecycleTests: XCTestCase {
    func testViewWillDisappearStopsPendingRepeatAndHaptics() {
        let harness = makeHarness()
        let controller = KeyboardViewController()
        controller.installKeyboardViewForTesting(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)

        controller.viewWillDisappear(false)
        waitForTimers(0.7)

        XCTAssertEqual(harness.delegate.pressedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.hapticCount(), 1)
    }

    func testViewWillDisappearStopsActiveRepeatAndHaptics() {
        let harness = makeHarness()
        let controller = KeyboardViewController()
        controller.installKeyboardViewForTesting(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        waitUntil { harness.delegate.pressedCodes.count >= 3 }
        XCTAssertGreaterThanOrEqual(harness.delegate.pressedCodes.count, 3)

        controller.viewWillDisappear(false)
        assertCountsFreeze(harness)
    }

    func testWindowDetachmentStopsPendingRepeatAndHaptics() {
        let harness = makeHarness()
        let window = attach(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)

        harness.keyboard.removeFromSuperview()
        waitForTimers(0.7)

        XCTAssertEqual(harness.delegate.pressedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.hapticCount(), 1)
        withExtendedLifetime(window) {}
    }

    func testWindowDetachmentStopsActiveRepeatAndHaptics() {
        let harness = makeHarness()
        let window = attach(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        waitUntil { harness.delegate.pressedCodes.count >= 3 }
        XCTAssertGreaterThanOrEqual(harness.delegate.pressedCodes.count, 3)

        harness.keyboard.removeFromSuperview()
        assertCountsFreeze(harness)
        withExtendedLifetime(window) {}
    }

    func testDuplicateLifecycleCleanupAllowsNextRepeatInteraction() {
        let harness = makeHarness()
        let window = attach(harness.keyboard)
        let controller = KeyboardViewController()
        controller.installKeyboardViewForTesting(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        controller.viewWillDisappear(false)
        harness.keyboard.removeFromSuperview()
        harness.keyboard.cancelActiveInteractions()
        assertCountsFreeze(harness)

        window.addSubview(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.arrowRight.rawValue)
        waitUntil {
            harness.delegate.pressedCodes.filter { $0 == LimeKeyCode.arrowRight.rawValue }.count >= 3
                && harness.hapticCount() >= 4
        }
        XCTAssertGreaterThanOrEqual(
            harness.delegate.pressedCodes.filter { $0 == LimeKeyCode.arrowRight.rawValue }.count, 3)
        XCTAssertGreaterThanOrEqual(harness.hapticCount(), 4)
        harness.keyboard.cancelActiveInteractions()
        withExtendedLifetime(window) {}
    }

    func testNormalReleaseStopsRepeatAndHaptics() {
        let harness = makeHarness()
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        waitUntil { harness.delegate.pressedCodes.count >= 3 }
        XCTAssertGreaterThanOrEqual(harness.delegate.pressedCodes.count, 3)

        harness.keyboard.endKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        assertCountsFreeze(harness)
        XCTAssertEqual(harness.delegate.releasedCodes, [LimeKeyCode.delete.rawValue])
    }

    private func assertCountsFreeze(_ harness: KeyboardLifecycleHarness,
                                    file: StaticString = #filePath, line: UInt = #line) {
        let pressed = harness.delegate.pressedCodes
        let haptics = harness.hapticCount()
        waitForTimers(0.35)
        XCTAssertEqual(harness.delegate.pressedCodes, pressed, file: file, line: line)
        XCTAssertEqual(harness.hapticCount(), haptics, file: file, line: line)
    }

    /// Poll the main run loop until `condition` holds or `timeout` elapses. Load-tolerant
    /// replacement for a fixed sleep before a "fired at least N times" assertion: returns
    /// as soon as the repeat timers have fired enough, and only waits longer under load —
    /// instead of asserting against a fixed wall-clock budget that starves under CPU load.
    private func waitUntil(_ condition: @escaping () -> Bool, timeout: TimeInterval = 5) {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    private func waitForTimers(_ seconds: TimeInterval) {
        let expectation = expectation(description: "wait for keyboard repeat timers")
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds) { expectation.fulfill() }
        // Generous margin over `seconds`: under CPU load the main queue is starved and the
        // asyncAfter fulfillment can be delayed well past a tight `seconds + 1` timeout.
        wait(for: [expectation], timeout: seconds + 5)
    }

    private func attach(_ keyboard: KeyboardView) -> UIWindow {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.addSubview(keyboard)
        return window
    }

    private func makeHarness() -> KeyboardLifecycleHarness {
        let keyboard = KeyboardView(layout: Self.repeatLayout)
        let delegate = RecordingLifecycleDelegate()
        var hapticCount = 0
        keyboard.delegate = delegate
        keyboard.feedbackVibration = true
        keyboard.hapticRequestedForTesting = { hapticCount += 1 }
        keyboard.frame = CGRect(x: 0, y: 0, width: 390, height: 64)
        keyboard.layoutIfNeeded()
        return KeyboardLifecycleHarness(keyboard: keyboard, delegate: delegate,
                                        hapticCount: { hapticCount })
    }

    private static let repeatLayout = LimeKeyLayout(id: "repeat_lifecycle_test", rows: [
        KeyRow(keys: [
            KeyDef(code: LimeKeyCode.delete.rawValue, widthPercent: 20,
                   icon: "delete.left", isRepeatable: true, isModifier: true),
            KeyDef(code: LimeKeyCode.arrowLeft.rawValue, widthPercent: 20,
                   icon: "arrow.left", isRepeatable: true, isModifier: true),
            KeyDef(code: LimeKeyCode.arrowUp.rawValue, widthPercent: 20,
                   icon: "arrow.up", isRepeatable: true, isModifier: true),
            KeyDef(code: LimeKeyCode.arrowDown.rawValue, widthPercent: 20,
                   icon: "arrow.down", isRepeatable: true, isModifier: true),
            KeyDef(code: LimeKeyCode.arrowRight.rawValue, widthPercent: 20,
                   icon: "arrow.right", isRepeatable: true, isModifier: true),
        ])
    ])
}

private struct KeyboardLifecycleHarness {
    let keyboard: KeyboardView
    let delegate: RecordingLifecycleDelegate
    let hapticCount: () -> Int
}

private final class RecordingLifecycleDelegate: KeyboardViewDelegate {
    var pressedCodes: [Int] = []
    var releasedCodes: [Int] = []

    func keyboardView(_ view: KeyboardView, didPress keyDef: KeyDef) {
        pressedCodes.append(keyDef.code)
    }

    func keyboardView(_ view: KeyboardView, didRelease keyDef: KeyDef) {
        releasedCodes.append(keyDef.code)
    }
    func keyboardView(_ view: KeyboardView, didUpdateShiftHoldActive active: Bool) {}
    func keyboardView(_ view: KeyboardView, didLongPress keyDef: KeyDef) {}
    func keyboardView(_ view: KeyboardView, didLongPressKey keyDef: KeyDef) {}
    func keyboardView(_ view: KeyboardView, didLongPressPopupKey keyDef: KeyDef, sourceRect: CGRect) {}
    func keyboardView(_ view: KeyboardView, didReleasePopupKey keyDef: KeyDef, commit: Bool) {}
    func keyboardView(_ view: KeyboardView, showPreviewFor keyDef: KeyDef, keyRect: CGRect) {}
    func keyboardViewDismissPreview(_ view: KeyboardView) {}
    func keyboardView(_ view: KeyboardView, didMoveCaretBy steps: Int) {}
    func keyboardViewHasOpenPopup(_ view: KeyboardView) -> Bool { false }
    func keyboardView(_ view: KeyboardView, popupKeyAtKeyboardPoint point: CGPoint) -> KeyDef? { nil }
    func keyboardView(_ view: KeyboardView, highlightPopupKey keyDef: KeyDef?) {}
    func keyboardView(_ view: KeyboardView, didSelectPopupKey keyDef: KeyDef) {}
    func keyboardViewDidCancelPopupSlide(_ view: KeyboardView) {}
    func keyboardViewDidSwipeLeft(_ view: KeyboardView) {}
    func keyboardViewDidSwipeRight(_ view: KeyboardView) {}
}
