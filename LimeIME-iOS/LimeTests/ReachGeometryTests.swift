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

final class ReachGeometryTests: XCTestCase {

    // 13" iPad landscape (1366 pt, .large): half cap = 66 mm × 5.20 = 343.2 pt,
    // fraction ≈ 0.251 — far below the legacy (1 − 0.06)/2 = 0.47.
    func testSplitHalfFractionCapsOnLargeIPad() {
        let f = ReachGeometry.splitHalfMaxFraction(viewWidth: 1366, sizeClass: .large)
        XCTAssertEqual(f, 66 * 5.20 / 1366, accuracy: 0.001)
        XCTAssertLessThan(f, 0.47)
    }

    // iPad mini portrait (744 pt, .small): cap 66 × 6.42 = 423.7 pt exceeds the
    // legacy half — legacy behavior must be preserved.
    func testSplitHalfFractionKeepsLegacyOnSmallIPad() {
        let f = ReachGeometry.splitHalfMaxFraction(viewWidth: 744, sizeClass: .small)
        XCTAssertEqual(f, (1 - LayoutMetrics.KeyboardRow.splitGapFraction) / 2, accuracy: 0.001)
    }

    func testOneHandWidth() {
        XCTAssertEqual(ReachGeometry.oneHandWidth(viewWidth: 430), 360, accuracy: 0.01)
        XCTAssertEqual(ReachGeometry.oneHandWidth(viewWidth: 375), 360, accuracy: 0.01)
        XCTAssertEqual(ReachGeometry.oneHandWidth(viewWidth: 320), 320, accuracy: 0.01)
    }

    // 11" landscape (1194 pt, .medium): 5 × 14 mm × 5.20 = 364 pt < 40% cap (477.6);
    // mini portrait (744 pt, .small): the 40% cap (297.6) wins.
    func testNumpadAnchorWidth() {
        XCTAssertEqual(ReachGeometry.numpadAnchorWidth(viewWidth: 1194, columns: 5, sizeClass: .medium),
                       5 * 14 * 5.20, accuracy: 0.001)
        XCTAssertEqual(ReachGeometry.numpadAnchorWidth(viewWidth: 744, columns: 5, sizeClass: .small),
                       744 * 0.40, accuracy: 0.001)
    }

    // Android-parity split partition: iPad dayi bottom row — globe 8, .?123 10,
    // emoji 7, space 57, .?123 10, chevron 8 (unit 7 → 14 columns, border 49%).
    // The space bar crosses the border and must be cut and cloned to both halves.
    func testSplitPartitionClonesSpaceToBothHalves() {
        let row = [
            KeyDef(code: -7, widthPercent: 8, icon: "globe"),
            KeyDef(code: -2, label: ".?123", widthPercent: 10),
            KeyDef(code: -14, widthPercent: 7, icon: "face.smiling"),
            KeyDef(code: 32, widthPercent: 57, icon: "space.bar"),
            KeyDef(code: -2, label: ".?123", widthPercent: 10),
            KeyDef(code: -8, widthPercent: 8, icon: "keyboard.chevron.compact.down"),
        ]
        let (left, right) = SplitPartition.partition(row)
        XCTAssertEqual(left.map { $0.code }, [-7, -2, -14, 32])
        XCTAssertEqual(right.map { $0.code }, [32, -2, -8])
        XCTAssertEqual(left.last!.widthPercent, 24, accuracy: 0.001)   // 49 − 25
        XCTAssertEqual(right.first!.widthPercent, 33, accuracy: 0.001) // 82 − 49
    }

    // Uniform 14-key row (7% each, total 98): border after 7 columns — halves 7/7,
    // nothing cloned.
    func testSplitPartitionBalancedUniformRow() {
        let row = (0..<14).map { KeyDef(code: 97 + $0, widthPercent: 7) }
        let (left, right) = SplitPartition.partition(row)
        XCTAssertEqual(left.count, 7)
        XCTAssertEqual(right.count, 7)
        XCTAssertEqual(left.reduce(0) { $0 + $1.widthPercent },
                       right.reduce(0) { $0 + $1.widthPercent }, accuracy: 0.001)
    }

    func testPhonePortraitMigrationAlignsWithAndroidWithoutInventingLegacyIPhoneSplit() {
        XCTAssertEqual(PhoneKeyboardModePolicy.migratePortraitMode(legacyOneHand: 1,
                                                                    legacySplit: 1,
                                                                    legacyPhoneSplitSupported: false),
                       .oneHandLeft)
        XCTAssertEqual(PhoneKeyboardModePolicy.migratePortraitMode(legacyOneHand: 0,
                                                                    legacySplit: 1,
                                                                    legacyPhoneSplitSupported: false),
                       .standard)
        XCTAssertFalse(PhoneKeyboardModePolicy.migrateLandscapeSplit(legacySplit: 1,
                                                                      legacyPhoneSplitSupported: false))
    }

    func testPhonePortraitModesAreMutuallyExclusive() {
        XCTAssertTrue(PhoneKeyboardModePolicy.splitActive(isLandscape: false,
                                                           splitEligible: true,
                                                           portraitMode: .split,
                                                           landscapeSplit: false))
        XCTAssertFalse(PhoneKeyboardModePolicy.splitActive(isLandscape: false,
                                                            splitEligible: true,
                                                            portraitMode: .oneHandLeft,
                                                            landscapeSplit: true))
        XCTAssertEqual(PhoneKeyboardModePolicy.oneHandAnchor(isLandscape: false,
                                                              portraitMode: .oneHandLeft), 1)
        XCTAssertEqual(PhoneKeyboardModePolicy.oneHandAnchor(isLandscape: false,
                                                              portraitMode: .oneHandRight), 2)
        XCTAssertEqual(PhoneKeyboardModePolicy.oneHandAnchor(isLandscape: false,
                                                              portraitMode: .split), 0)
    }

    func testPhoneLandscapeUsesOnlyLandscapeSplitAndNumpadNeverSplits() {
        XCTAssertTrue(PhoneKeyboardModePolicy.splitActive(isLandscape: true,
                                                           splitEligible: true,
                                                           portraitMode: .standard,
                                                           landscapeSplit: true))
        XCTAssertFalse(PhoneKeyboardModePolicy.splitActive(isLandscape: true,
                                                            splitEligible: true,
                                                            portraitMode: .split,
                                                            landscapeSplit: false))
        XCTAssertFalse(PhoneKeyboardModePolicy.splitActive(isLandscape: true,
                                                            splitEligible: false,
                                                            portraitMode: .split,
                                                            landscapeSplit: true))
    }

    // Issue #169: the integrated phone controls apply to EVERY iPhone and NEVER to
    // iPad — no screen-width or physical-size gate.
    func testPhoneControlsApplyToEveryPhoneNeverPad() {
        XCTAssertTrue(PhoneKeyboardModePolicy.phoneControlsApply(isPad: false))
        XCTAssertFalse(PhoneKeyboardModePolicy.phoneControlsApply(isPad: true))
    }

    // A narrow iPhone (previously below the removed one-hand width gate) still resolves
    // its portrait one-hand anchor purely from the stored mode — width is not an input.
    func testNarrowPhoneStillResolvesPortraitOneHandWithoutWidthGate() {
        XCTAssertTrue(PhoneKeyboardModePolicy.phoneControlsApply(isPad: false))
        XCTAssertEqual(PhoneKeyboardModePolicy.oneHandAnchor(isLandscape: false,
                                                             portraitMode: .oneHandLeft), 1)
        XCTAssertEqual(PhoneKeyboardModePolicy.oneHandAnchor(isLandscape: false,
                                                             portraitMode: .oneHandRight), 2)
        XCTAssertTrue(PhoneKeyboardModePolicy.splitActive(isLandscape: false,
                                                          splitEligible: true,
                                                          portraitMode: .split,
                                                          landscapeSplit: false))
    }
}
