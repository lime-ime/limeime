import XCTest
import CoreGraphics

final class KeyDetectorTests: XCTestCase {

    private let keySize: CGFloat = 40
    private let gap: CGFloat = 8

    func testKeyCentersResolveToThemselves() {
        let keys = makeRow()
        let detector = KeyDetector(keys: keys, proximityThreshold: 40)

        for key in keys {
            XCTAssertEqual(detector.keyAt(key.frame.center), key)
        }
    }

    func testGapPointResolvesToNearestKeyWithinThreshold() {
        let keys = makeRow()
        let detector = KeyDetector(keys: keys, proximityThreshold: 40)
        let point = CGPoint(x: keys[0].frame.maxX + gap * 0.75, y: keys[0].frame.midY)

        XCTAssertEqual(detector.keyAt(point), keys[1])
    }

    func testPointBeyondProximityThresholdReturnsNil() {
        let detector = KeyDetector(keys: makeRow(), proximityThreshold: 40)

        XCTAssertNil(detector.keyAt(CGPoint(x: 1_000, y: 1_000)))
    }

    func testSubHysteresisWobbleKeepsCurrentKey() {
        let keys = makeRow()
        let detector = KeyDetector(keys: keys, proximityThreshold: 40, defaultHysteresis: 12)
        let point = CGPoint(x: keys[1].frame.minX + 1, y: keys[1].frame.midY)

        XCTAssertEqual(detector.keyAt(point, movingFrom: keys[0], hysteresis: 12), keys[0])
    }

    func testMovePastHysteresisDeadZoneSwitchesToNeighbor() {
        let keys = makeRow()
        let detector = KeyDetector(keys: keys, proximityThreshold: 40, defaultHysteresis: 12)
        let point = CGPoint(x: keys[1].frame.minX + 8, y: keys[1].frame.midY)

        XCTAssertEqual(detector.keyAt(point, movingFrom: keys[0], hysteresis: 12), keys[1])
    }

    private func makeRow() -> [KeyModel] {
        Array("qwertyuiop").enumerated().map { index, character in
            let x = CGFloat(index) * (keySize + gap)
            return KeyModel(frame: CGRect(x: x, y: 0, width: keySize, height: keySize),
                            codes: [Int(character.unicodeScalars.first!.value)],
                            primaryLabel: String(character),
                            secondaryLabel: "",
                            isRepeatable: false,
                            isModifier: false,
                            hasPopup: false,
                            isDualRow: false,
                            isSpace: false)
        }
    }
}

private extension CGRect {
    var center: CGPoint {
        CGPoint(x: midX, y: midY)
    }
}
