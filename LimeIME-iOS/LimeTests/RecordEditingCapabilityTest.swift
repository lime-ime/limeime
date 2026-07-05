import Foundation
import XCTest
@testable import LimeIME

final class RecordEditingCapabilityTest: XCTestCase {
    func testReadOnlyUnlessFAStateConfirmedOn() {
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .unknown), .readOnly)
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .confirmedOff), .readOnly)
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .confirmedOn), .readOnly)
    }

    func testLiveEditingRequiresActiveConfirmedOnGate() {
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .confirmedOn,
                                                       activeThisSession: true),
                       .live)
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .confirmedOn,
                                                       activeThisSession: false),
                       .readOnly)
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .confirmedOff,
                                                       activeThisSession: true),
                       .readOnly)
    }

    func testForcedLiveEditingLaunchArgument() {
        let args = ["LimeIME", "-limeUITestForceLiveEditing", "1"]

        XCTAssertTrue(RecordEditingCapability.forceLiveEditingEnabled(arguments: args))
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .unknown, forceLive: true), .live)
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .unknown,
                                                       activeThisSession: false,
                                                       forceLive: true),
                       .live)
    }

}

final class RelayActiveStateTest: XCTestCase {
    func testDefaultsToReadOnlyUntilActiveAndFullAccessAreProven() {
        let state = RelayActiveState()

        XCTAssertEqual(state.editingCapability, .readOnly)

        state.markActive(fullAccess: false)
        XCTAssertEqual(state.editingCapability, .readOnly)

        state.markActive(fullAccess: true)
        XCTAssertEqual(state.editingCapability, .live)

        state.markNotActive()
        XCTAssertEqual(state.editingCapability, .readOnly)
    }
}
