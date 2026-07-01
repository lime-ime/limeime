import XCTest
import CoreGraphics
import UIKit

final class TouchLayerGestureTests: XCTestCase {

    func testPopupDetectorResolvesDirectHitAndTopEdgeTolerance() {
        let first = KeyDef(code: 224, label: "à")
        let second = KeyDef(code: 225, label: "á")
        let targets = [
            PopupKeyHitTarget(keyDef: first, frame: CGRect(x: 0, y: 0, width: 40, height: 44)),
            PopupKeyHitTarget(keyDef: second, frame: CGRect(x: 44, y: 0, width: 40, height: 44)),
        ]
        let detector = PopupKeyDetector(targets: targets, slideAllowance: 10)

        XCTAssertEqual(detector.key(at: CGPoint(x: 64, y: 22)), second)
        XCTAssertEqual(detector.key(at: CGPoint(x: 64, y: -13)), second)
        XCTAssertNil(detector.key(at: CGPoint(x: 64, y: -16)))
    }

    func testSwipeClassifierReturnsLeftRightOrNone() {
        let bounds = CGSize(width: 320, height: 216)
        let threshold: CGFloat = 500

        XCTAssertEqual(KeyboardSwipeClassifier.classify(delta: CGSize(width: -190, height: 12),
                                                        velocity: CGVector(dx: -900, dy: 80),
                                                        endingVelocity: CGVector(dx: -300, dy: 20),
                                                        bounds: bounds,
                                                        velocityThreshold: threshold),
                       .left)
        XCTAssertEqual(KeyboardSwipeClassifier.classify(delta: CGSize(width: 190, height: -10),
                                                        velocity: CGVector(dx: 900, dy: -40),
                                                        endingVelocity: CGVector(dx: 300, dy: -20),
                                                        bounds: bounds,
                                                        velocityThreshold: threshold),
                       .right)
        XCTAssertEqual(KeyboardSwipeClassifier.classify(delta: CGSize(width: 190, height: 210),
                                                        velocity: CGVector(dx: 900, dy: 950),
                                                        endingVelocity: CGVector(dx: 300, dy: 200),
                                                        bounds: bounds,
                                                        velocityThreshold: threshold),
                       .none)
    }

    func testRepeatCancelsWhenRepeatableKeyAndLetterAreDown() {
        let delete = makeKey(label: "delete", code: LimeKeyCode.delete.rawValue,
                             isRepeatable: true, isModifier: true)
        let letter = makeKey(label: "a", code: 97, isRepeatable: false, isModifier: false)
        let shift = makeKey(label: "shift", code: LimeKeyCode.shift.rawValue,
                            isRepeatable: false, isModifier: true)

        XCTAssertTrue(TouchTracker.shouldCancelRepeat(trackers: [
            TouchTracker(downKey: delete),
            TouchTracker(downKey: letter),
        ]))
        XCTAssertFalse(TouchTracker.shouldCancelRepeat(trackers: [
            TouchTracker(downKey: delete),
            TouchTracker(downKey: shift),
        ]))
    }

    func testSpaceTrackerDoesNotSwitchToNeighbor() {
        let space = KeyModel(frame: CGRect(x: 0, y: 0, width: 80, height: 40),
                             codes: [LimeKeyCode.space.rawValue],
                             primaryLabel: "space",
                             secondaryLabel: "",
                             isRepeatable: false,
                             isModifier: false,
                             hasPopup: false,
                             isDualRow: false,
                             isSpace: true)
        let letter = makeKey(label: "a", code: 97, isRepeatable: false, isModifier: false)
        let shiftedLetter = KeyModel(frame: letter.frame.offsetBy(dx: 88, dy: 0),
                                     codes: letter.codes,
                                     primaryLabel: letter.primaryLabel,
                                     secondaryLabel: letter.secondaryLabel,
                                     isRepeatable: false,
                                     isModifier: false,
                                     hasPopup: false,
                                     isDualRow: false,
                                     isSpace: false)
        let detector = KeyDetector(keys: [space, shiftedLetter], proximityThreshold: 80)
        var tracker = TouchTracker(downKey: space)

        XCTAssertNil(tracker.move(to: shiftedLetter.frame.center, detector: detector, hysteresis: 8))
        XCTAssertEqual(tracker.currentKey, space)
    }

    func testKeyTouchLayerAccessibilityElementsExposeVisibleKeysAndActivateThroughOwner() throws {
        let layout = LimeKeyLayout(id: "accessibility_test", rows: [
            KeyRow(keys: [
                KeyDef(code: 97, label: "a", widthPercent: 20),
                KeyDef(code: LimeKeyCode.space.rawValue, label: "space", widthPercent: 40),
                KeyDef(code: LimeKeyCode.delete.rawValue, widthPercent: 20,
                       icon: "delete.left", isRepeatable: true, isModifier: true),
                KeyDef(code: LimeKeyCode.shift.rawValue, widthPercent: 20,
                       icon: "shift", isModifier: true),
            ])
        ])
        let keyboard = KeyboardView(layout: layout)
        let delegate = RecordingKeyboardDelegate()
        keyboard.delegate = delegate
        keyboard.frame = CGRect(x: 0, y: 0, width: 320, height: 64)
        keyboard.layoutIfNeeded()

        let layer = try XCTUnwrap(keyTouchLayers(in: keyboard).first)
        let elements = try XCTUnwrap(layer.accessibilityElements as? [UIButton])
        XCTAssertEqual(elements.map(\.accessibilityLabel), ["a", "space", "delete", "shift"])
        XCTAssertTrue(elements.allSatisfy { $0.accessibilityTraits == .keyboardKey })

        for label in ["space", "delete", "shift"] {
            let button = try XCTUnwrap(elements.first { $0.accessibilityLabel == label })
            XCTAssertTrue(button.accessibilityActivate())
        }
        XCTAssertEqual(delegate.pressedCodes, [
            LimeKeyCode.space.rawValue,
            LimeKeyCode.delete.rawValue,
            LimeKeyCode.shift.rawValue,
        ])

        let splitKeyboard = KeyboardView(layout: layout)
        splitKeyboard.splitMode = true
        splitKeyboard.frame = CGRect(x: 0, y: 0, width: 640, height: 64)
        splitKeyboard.layoutIfNeeded()
        XCTAssertEqual(accessibilityLabels(in: splitKeyboard), ["a", "space", "delete", "shift"])
    }

    private func makeKey(label: String,
                         code: Int,
                         isRepeatable: Bool,
                         isModifier: Bool) -> KeyModel {
        KeyModel(frame: CGRect(x: 0, y: 0, width: 40, height: 40),
                 codes: [code],
                 primaryLabel: label,
                 secondaryLabel: "",
                 isRepeatable: isRepeatable,
                 isModifier: isModifier,
                 hasPopup: false,
                 isDualRow: false,
                 isSpace: false)
    }

    private func keyTouchLayers(in view: UIView) -> [UIView] {
        view.subviews.flatMap { subview -> [UIView] in
            let current = String(describing: type(of: subview)) == "KeyTouchLayer" ? [subview] : []
            return current + keyTouchLayers(in: subview)
        }
    }

    private func accessibilityLabels(in keyboard: KeyboardView) -> [String?] {
        keyTouchLayers(in: keyboard).flatMap {
            ($0.accessibilityElements as? [UIButton])?.map(\.accessibilityLabel) ?? []
        }
    }
}

private extension CGRect {
    var center: CGPoint {
        CGPoint(x: midX, y: midY)
    }
}

private final class RecordingKeyboardDelegate: KeyboardViewDelegate {
    var pressedCodes: [Int] = []

    func keyboardView(_ view: KeyboardView, didPress keyDef: KeyDef) {
        pressedCodes.append(keyDef.code)
    }

    func keyboardView(_ view: KeyboardView, didRelease keyDef: KeyDef) {}
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
