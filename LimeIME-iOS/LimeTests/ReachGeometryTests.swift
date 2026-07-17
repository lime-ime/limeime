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

    // Gate = 64 mm × 6.0 = 384 pt: Pro-Max (430) and 6.1" (390) pass, mini/SE (375) fail.
    func testOneHandGate() {
        XCTAssertTrue(ReachGeometry.oneHandAvailable(screenWidthPt: 430))
        XCTAssertTrue(ReachGeometry.oneHandAvailable(screenWidthPt: 390))
        XCTAssertFalse(ReachGeometry.oneHandAvailable(screenWidthPt: 375))
    }

    func testOneHandWidth() {
        XCTAssertEqual(ReachGeometry.oneHandWidth(viewWidth: 430), 60 * 6.0, accuracy: 0.001)
        XCTAssertEqual(ReachGeometry.oneHandWidth(viewWidth: 300), 300, accuracy: 0.001)
    }

    // 11" landscape (1194 pt, .medium): 5 × 14 mm × 5.20 = 364 pt < 40% cap (477.6);
    // mini portrait (744 pt, .small): the 40% cap (297.6) wins.
    func testNumpadAnchorWidth() {
        XCTAssertEqual(ReachGeometry.numpadAnchorWidth(viewWidth: 1194, columns: 5, sizeClass: .medium),
                       5 * 14 * 5.20, accuracy: 0.001)
        XCTAssertEqual(ReachGeometry.numpadAnchorWidth(viewWidth: 744, columns: 5, sizeClass: .small),
                       744 * 0.40, accuracy: 0.001)
    }
}
