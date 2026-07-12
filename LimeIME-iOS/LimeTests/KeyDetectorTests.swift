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
