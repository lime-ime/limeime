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

    func testFlintSlideSequenceEndsOnReleaseKey() {
        let keys = makeRow(labels: ["q", "w", "e", "r"])
        let detector = KeyDetector(keys: keys, proximityThreshold: 40)
        var tracker = TouchTracker(downKey: keys[0])

        for key in keys.dropFirst() {
            _ = tracker.move(to: key.frame.center, detector: detector, hysteresis: 8)
        }

        XCTAssertEqual(tracker.currentKey, keys[3])
        XCTAssertTrue(tracker.isSliding)
    }

    func testSubKeyWidthBoundaryWobbleKeepsStartKey() {
        let keys = makeRow(labels: ["q", "w"])
        let detector = KeyDetector(keys: keys, proximityThreshold: 40)
        var tracker = TouchTracker(downKey: keys[0])
        let wobblePoint = CGPoint(x: keys[1].frame.minX + 2, y: keys[1].frame.midY)

        let releasedKey = tracker.move(to: wobblePoint, detector: detector, hysteresis: 12)

        XCTAssertNil(releasedKey)
        XCTAssertEqual(tracker.currentKey, keys[0])
        XCTAssertFalse(tracker.isSliding)
    }

    func testModifierTrackerDoesNotSwitchToNeighbor() {
        let shift = makeKey(label: "shift", x: 0, code: -1, isModifier: true)
        let letter = makeKey(label: "a", x: 48)
        let detector = KeyDetector(keys: [shift, letter], proximityThreshold: 40)
        var tracker = TouchTracker(downKey: shift)

        let releasedKey = tracker.move(to: letter.frame.center, detector: detector, hysteresis: 8)

        XCTAssertNil(releasedKey)
        XCTAssertEqual(tracker.currentKey, shift)
        XCTAssertFalse(tracker.isSliding)
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

    private func makeRow(labels: [String]) -> [KeyModel] {
        labels.enumerated().map { index, label in
            makeKey(label: label, x: CGFloat(index) * 48)
        }
    }
}

private extension CGRect {
    var center: CGPoint {
        CGPoint(x: midX, y: midY)
    }
}
