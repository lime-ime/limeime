// SetupDetectionTest.swift

import XCTest
@testable import LimeIME

final class SetupDetectionTest: XCTestCase {
    func testNotEnabledWinsBeforeProbeOrFullAccess() {
        XCTAssertEqual(SetupDetection.state(keyboardEnabled: false,
                                            activeThisSession: true,
                                            probePending: true,
                                            faConfirmedOn: true),
                       .notEnabled)
    }

    func testCheckingActiveWhileEnabledProbeIsPending() {
        XCTAssertEqual(SetupDetection.state(keyboardEnabled: true,
                                            activeThisSession: false,
                                            probePending: true,
                                            faConfirmedOn: false),
                       .checkingActive)
    }

    func testEnabledNotActiveAfterProbeResolvesWithoutPing() {
        XCTAssertEqual(SetupDetection.state(keyboardEnabled: true,
                                            activeThisSession: false,
                                            probePending: false,
                                            faConfirmedOn: false),
                       .enabledNotActive)
    }

    func testActiveNoFullAccessRequiresActiveKeyboard() {
        XCTAssertEqual(SetupDetection.state(keyboardEnabled: true,
                                            activeThisSession: true,
                                            probePending: false,
                                            faConfirmedOn: false),
                       .activeNoFullAccess)
    }

    func testFullyEnabledRequiresActiveKeyboardAndFullAccess() {
        XCTAssertEqual(SetupDetection.state(keyboardEnabled: true,
                                            activeThisSession: true,
                                            probePending: false,
                                            faConfirmedOn: true),
                       .fullyEnabled)
        XCTAssertNotEqual(SetupDetection.state(keyboardEnabled: true,
                                               activeThisSession: false,
                                               probePending: false,
                                               faConfirmedOn: true),
                          .fullyEnabled)
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
}
