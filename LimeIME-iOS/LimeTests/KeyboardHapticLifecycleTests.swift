import XCTest
import UIKit

final class KeyboardHapticLifecycleTests: XCTestCase {

    func testRepeatableKeyDoesNotRepeatBeforeStartDelay() {
        let harness = makeHarness()

        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay - 0.001)

        XCTAssertEqual(harness.delegate.pressedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.hapticCount(), 1)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .pending)
    }

    func testCancelActiveInteractionsStopsActiveRepeatAndHaptics() {
        let harness = makeHarness()
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 2 * LayoutMetrics.Gesture.repeatInterval)
        let pressedBeforeCleanup = harness.delegate.pressedCodes
        let hapticsBeforeCleanup = harness.hapticCount()

        harness.keyboard.cancelActiveInteractions()
        harness.scheduler.advance(by: 5 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, pressedBeforeCleanup)
        XCTAssertEqual(harness.hapticCount(), hapticsBeforeCleanup)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
    }

    func testWindowDetachmentStopsActiveRepeatAndHaptics() {
        let harness = makeHarness()
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.addSubview(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 2 * LayoutMetrics.Gesture.repeatInterval)
        let pressedBeforeDetach = harness.delegate.pressedCodes
        let hapticsBeforeDetach = harness.hapticCount()

        harness.keyboard.removeFromSuperview()
        harness.scheduler.advance(by: 5 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, pressedBeforeDetach)
        XCTAssertEqual(harness.hapticCount(), hapticsBeforeDetach)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
        withExtendedLifetime(window) {}
    }

    func testViewWillDisappearStopsPendingRepeatAndHaptics() {
        let harness = makeHarness()
        let controller = KeyboardViewController()
        controller.installKeyboardViewForTesting(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)

        controller.viewWillDisappear(false)
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 5 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.hapticCount(), 1)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
    }

    func testLifecycleCleanupIsIdempotentAndStaleCallbackCannotRestartRepeat() {
        let harness = makeHarness()
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        let staleDelayCallback = try! XCTUnwrap(harness.scheduler.capturedActions.first)

        harness.keyboard.cancelActiveInteractions()
        harness.keyboard.cancelActiveInteractions()
        staleDelayCallback()
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 5 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.hapticCount(), 1)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
    }

    func testCleanupAllowsNextInteractionToRepeatNormally() {
        let harness = makeHarness()
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        harness.keyboard.cancelActiveInteractions()

        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.arrowRight.rawValue)
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 2 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, [
            LimeKeyCode.delete.rawValue,
            LimeKeyCode.arrowRight.rawValue,
            LimeKeyCode.arrowRight.rawValue,
            LimeKeyCode.arrowRight.rawValue,
        ])
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .repeating)
    }

    func testReleaseBeforeStartDelayCancelsPendingRepeat() {
        let harness = makeHarness()
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        harness.keyboard.endKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)

        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 3 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.delegate.releasedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.hapticCount(), 1)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
    }

    func testCancelWhileRepeatingStopsFurtherEventsWithoutRelease() {
        let harness = makeHarness()
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 2 * LayoutMetrics.Gesture.repeatInterval)
        harness.keyboard.cancelKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        let pressedBeforeAdvance = harness.delegate.pressedCodes
        let hapticsBeforeAdvance = harness.hapticCount()

        harness.scheduler.advance(by: 5 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, pressedBeforeAdvance)
        XCTAssertEqual(harness.delegate.releasedCodes, [])
        XCTAssertEqual(harness.hapticCount(), hapticsBeforeAdvance)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
    }

    func testAllRepeatableKeysUseTheSameRepeatAndCleanupPolicy() {
        let codes = [
            LimeKeyCode.delete.rawValue,
            LimeKeyCode.arrowLeft.rawValue,
            LimeKeyCode.arrowUp.rawValue,
            LimeKeyCode.arrowDown.rawValue,
            LimeKeyCode.arrowRight.rawValue,
        ]

        for code in codes {
            XCTContext.runActivity(named: "repeat key code \(code)") { _ in
                let harness = makeHarness()
                harness.keyboard.beginKeyInteractionForTesting(code: code)
                harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                          + 2 * LayoutMetrics.Gesture.repeatInterval)

                XCTAssertEqual(harness.delegate.pressedCodes, [code, code, code])
                XCTAssertEqual(harness.hapticCount(), 3)
                harness.keyboard.cancelActiveInteractions()
                harness.scheduler.advance(by: 3 * LayoutMetrics.Gesture.repeatInterval)
                XCTAssertEqual(harness.delegate.pressedCodes, [code, code, code])
            }
        }
    }

    func testWindowDetachmentStopsPendingRepeat() {
        let harness = makeHarness()
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.addSubview(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)

        harness.keyboard.removeFromSuperview()
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 3 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.hapticCount(), 1)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
        withExtendedLifetime(window) {}
    }

    func testViewWillDisappearStopsActiveRepeat() {
        let harness = makeHarness()
        let controller = KeyboardViewController()
        controller.installKeyboardViewForTesting(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 2 * LayoutMetrics.Gesture.repeatInterval)
        let pressedBeforeDisappear = harness.delegate.pressedCodes
        let hapticsBeforeDisappear = harness.hapticCount()

        controller.viewWillDisappear(false)
        harness.scheduler.advance(by: 5 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, pressedBeforeDisappear)
        XCTAssertEqual(harness.hapticCount(), hapticsBeforeDisappear)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
    }

    func testViewDisappearThenWindowDetachIsIdempotent() {
        let harness = makeHarness()
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.addSubview(harness.keyboard)
        let controller = KeyboardViewController()
        controller.installKeyboardViewForTesting(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)

        controller.viewWillDisappear(false)
        harness.keyboard.removeFromSuperview()
        harness.scheduler.capturedActions.forEach { $0() }

        XCTAssertEqual(harness.delegate.pressedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.hapticCount(), 1)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
        withExtendedLifetime(window) {}
    }

    func testRepeatWithVibrationDisabledStopsOnCleanup() {
        let harness = makeHarness(vibrationEnabled: false)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 2 * LayoutMetrics.Gesture.repeatInterval)
        XCTAssertEqual(harness.delegate.pressedCodes.count, 3)
        XCTAssertEqual(harness.hapticCount(), 0)

        harness.keyboard.cancelActiveInteractions()
        harness.scheduler.advance(by: 5 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes.count, 3)
        XCTAssertEqual(harness.hapticCount(), 0)
    }

    func testWindowDetachThenViewDisappearIsIdempotent() {
        let harness = makeHarness()
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.addSubview(harness.keyboard)
        let controller = KeyboardViewController()
        controller.installKeyboardViewForTesting(harness.keyboard)
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)

        harness.keyboard.removeFromSuperview()
        controller.viewWillDisappear(false)
        harness.scheduler.capturedActions.forEach { $0() }

        XCTAssertEqual(harness.delegate.pressedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.hapticCount(), 1)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
        withExtendedLifetime(window) {}
    }

    func testLateReleaseAfterLifecycleCleanupCannotRestartRepeat() {
        let harness = makeHarness()
        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        harness.keyboard.cancelActiveInteractions()

        harness.keyboard.endKeyInteractionForTesting(code: LimeKeyCode.delete.rawValue)
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 3 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, [LimeKeyCode.delete.rawValue])
        XCTAssertEqual(harness.hapticCount(), 1)
        XCTAssertEqual(harness.keyboard.repeatStateForTesting, .idle)
    }

    func testInitialNilWindowDoesNotPreventFirstRepeatInteraction() {
        let harness = makeHarness()
        XCTAssertNil(harness.keyboard.window)

        harness.keyboard.beginKeyInteractionForTesting(code: LimeKeyCode.arrowLeft.rawValue)
        harness.scheduler.advance(by: LayoutMetrics.Gesture.repeatStartDelay
                                  + 2 * LayoutMetrics.Gesture.repeatInterval)

        XCTAssertEqual(harness.delegate.pressedCodes, [
            LimeKeyCode.arrowLeft.rawValue,
            LimeKeyCode.arrowLeft.rawValue,
            LimeKeyCode.arrowLeft.rawValue,
        ])
        XCTAssertEqual(harness.hapticCount(), 3)
    }

    private func makeHarness(vibrationEnabled: Bool = true) -> KeyboardLifecycleHarness {
        let scheduler = ManualKeyboardScheduler()
        let keyboard = KeyboardView(layout: Self.repeatLayout, repeatScheduler: scheduler)
        let delegate = RecordingLifecycleDelegate()
        var hapticCount = 0
        keyboard.delegate = delegate
        keyboard.feedbackVibration = vibrationEnabled
        keyboard.hapticRequestedForTesting = { hapticCount += 1 }
        keyboard.frame = CGRect(x: 0, y: 0, width: 390, height: 64)
        keyboard.layoutIfNeeded()
        return KeyboardLifecycleHarness(keyboard: keyboard,
                                        scheduler: scheduler,
                                        delegate: delegate,
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
    let scheduler: ManualKeyboardScheduler
    let delegate: RecordingLifecycleDelegate
    let hapticCount: () -> Int
}

private final class ManualKeyboardScheduler: KeyboardRepeatScheduling {
    private final class Task: KeyboardScheduledTask {
        var deadline: TimeInterval
        let interval: TimeInterval?
        let order: Int
        let action: () -> Void
        var isCancelled = false

        init(deadline: TimeInterval, interval: TimeInterval?, order: Int,
             action: @escaping () -> Void) {
            self.deadline = deadline
            self.interval = interval
            self.order = order
            self.action = action
        }

        func cancel() { isCancelled = true }
    }

    private var now: TimeInterval = 0
    private var nextOrder = 0
    private var tasks: [Task] = []
    private(set) var capturedActions: [() -> Void] = []

    func schedule(after delay: TimeInterval,
                  repeating interval: TimeInterval?,
                  _ action: @escaping () -> Void) -> KeyboardScheduledTask {
        let task = Task(deadline: now + delay, interval: interval,
                        order: nextOrder, action: action)
        nextOrder += 1
        tasks.append(task)
        capturedActions.append(action)
        return task
    }

    func advance(by delta: TimeInterval) {
        let target = now + delta
        while let task = tasks
            .filter({ !$0.isCancelled && $0.deadline <= target })
            .min(by: { ($0.deadline, $0.order) < ($1.deadline, $1.order) }) {
            now = task.deadline
            if let interval = task.interval {
                task.deadline += interval
            } else {
                task.isCancelled = true
            }
            task.action()
        }
        now = target
    }
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
