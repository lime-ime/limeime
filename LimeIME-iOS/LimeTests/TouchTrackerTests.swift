import XCTest
import CoreGraphics

final class TouchTrackerTests: XCTestCase {

    func testConcurrentTouchesKeepIndependentTrackers() {
        let keys = [
            makeKey(label: "a", x: 0),
            makeKey(label: "s", x: 48),
        ]
        let detector = KeyDetector(keys: keys, proximityThreshold: 40)

        let firstKey = detector.keyAt(CGPoint(x: 20, y: 20))
        let secondKey = detector.keyAt(CGPoint(x: 68, y: 20))
        let trackers = [
            1: TouchTracker(downKey: firstKey),
            2: TouchTracker(downKey: secondKey),
        ]

        XCTAssertEqual(trackers[1]?.downKey, keys[0])
        XCTAssertEqual(trackers[1]?.currentKey, keys[0])
        XCTAssertEqual(trackers[2]?.downKey, keys[1])
        XCTAssertEqual(trackers[2]?.currentKey, keys[1])
    }

    func testShiftHoldPolicyUsesLiveTrackerCount() {
        let shift = makeKey(label: "shift", x: 0, code: -1, isModifier: true)
        let letter = makeKey(label: "a", x: 48)
        var trackers = [
            1: TouchTracker(downKey: shift),
            2: TouchTracker(downKey: letter),
        ]

        XCTAssertTrue(ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: trackers.count,
                                                            wasShiftAlreadyHeld: true))

        trackers.removeValue(forKey: 2)
        XCTAssertTrue(ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: trackers.count,
                                                            wasShiftAlreadyHeld: true))

        trackers.removeValue(forKey: 1)
        XCTAssertFalse(ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: trackers.count,
                                                             wasShiftAlreadyHeld: true))
    }

    func testNearMissTapStartsTrackerOnProximityCorrectedKey() {
        let keys = [
            makeKey(label: "a", x: 0),
            makeKey(label: "s", x: 48),
        ]
        let detector = KeyDetector(keys: keys, proximityThreshold: 40)
        let gapPoint = CGPoint(x: 45, y: 20)

        let tracker = TouchTracker(downKey: detector.keyAt(gapPoint))

        XCTAssertEqual(tracker.downKey, keys[1])
        XCTAssertEqual(tracker.currentKey, keys[1])
    }

    private func makeKey(label: String,
                         x: CGFloat,
                         code: Int? = nil,
                         isModifier: Bool = false) -> KeyModel {
        let resolvedCode = code ?? Int(label.unicodeScalars.first!.value)
        return KeyModel(frame: CGRect(x: x, y: 0, width: 40, height: 40),
                        codes: [resolvedCode],
                        primaryLabel: label,
                        secondaryLabel: "",
                        isRepeatable: false,
                        isModifier: isModifier,
                        hasPopup: false,
                        isDualRow: false,
                        isSpace: false)
    }
}
