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

// SetupDetectionTest.swift

import XCTest
@testable import LimeIME

final class SetupDetectionTest: XCTestCase {
    func testFullAccessBannerStateIncludesActiveNoFullAccessCase() {
        XCTAssertEqual(SetupDetection.fullAccessBannerState(keyboardEnabled: false,
                                                            faConfirmedOn: true,
                                                            activeThisSession: true),
                       .notEnabled)
        XCTAssertEqual(SetupDetection.fullAccessBannerState(keyboardEnabled: true,
                                                            faConfirmedOn: false,
                                                            activeThisSession: false),
                       .enabledNoFullAccess)
        XCTAssertEqual(SetupDetection.fullAccessBannerState(keyboardEnabled: true,
                                                            faConfirmedOn: false,
                                                            activeThisSession: true),
                       .activeNoFullAccess)
        XCTAssertEqual(SetupDetection.fullAccessBannerState(keyboardEnabled: true,
                                                            faConfirmedOn: true,
                                                            activeThisSession: true),
                       .fullyEnabled)
    }

    func testActiveKeyboardBannerHiddenWhenKeyboardNotEnabled() {
        XCTAssertEqual(SetupDetection.activeKeyboardBannerState(keyboardEnabled: false,
                                                               activeThisSession: true,
                                                               probePending: true),
                       .hidden)
    }

    func testActiveKeyboardBannerChecksWhileProbeIsPending() {
        XCTAssertEqual(SetupDetection.activeKeyboardBannerState(keyboardEnabled: true,
                                                               activeThisSession: false,
                                                               probePending: true),
                       .checking)
    }

    func testActiveKeyboardBannerNotActiveAfterProbeResolvesWithoutPing() {
        XCTAssertEqual(SetupDetection.activeKeyboardBannerState(keyboardEnabled: true,
                                                               activeThisSession: false,
                                                               probePending: false),
                       .notActive)
    }

    func testActiveKeyboardBannerActiveRequiresActiveKeyboard() {
        XCTAssertEqual(SetupDetection.activeKeyboardBannerState(keyboardEnabled: true,
                                                               activeThisSession: true,
                                                               probePending: false),
                       .active)
    }

    func testForcedKeyboardEnabledLaunchArgument() {
        XCTAssertTrue(SetupDetection.forceKeyboardEnabled(arguments: [
            "LimeIME",
            "-limeUITestForceKeyboardEnabled",
            "1"
        ]))
        XCTAssertFalse(SetupDetection.forceKeyboardEnabled(arguments: [
            "LimeIME",
            "-limeUITestForceKeyboardEnabled",
            "0"
        ]))
    }

    func testKeyboardEnabledUsesAppleKeyboardsBundleIdsOnly() {
        XCTAssertTrue(SetupDetection.keyboardEnabled(appleKeyboards: [
            "zh-Hant",
            "org.limeime.LimeKeyboard"
        ]))
        XCTAssertFalse(SetupDetection.keyboardEnabled(appleKeyboards: [
            "zh-Hant",
            "en_US"
        ]))
        XCTAssertTrue(SetupDetection.keyboardEnabled(appleKeyboards: [],
                                                     forceEnabled: true))
    }
}
