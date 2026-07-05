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
