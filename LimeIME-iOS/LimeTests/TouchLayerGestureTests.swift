import XCTest
import CoreGraphics

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
}

private extension CGRect {
    var center: CGPoint {
        CGPoint(x: midX, y: midY)
    }
}
