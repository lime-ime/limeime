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

import Foundation
import XCTest
@testable import LimeIME

final class RecordEditingCapabilityTest: XCTestCase {
    func testRelayCapabilityRemainsAStatusSignal() {
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
    func testDefaultsToReadOnlyUntilActiveFullAccessAndDrainAreProven() {
        let state = RelayActiveState()

        XCTAssertEqual(state.editingCapability, .readOnly)

        state.markActive(fullAccess: false, pendingSync: 0)
        XCTAssertEqual(state.editingCapability, .readOnly)

        state.markActive(fullAccess: true, pendingSync: 0)
        XCTAssertEqual(state.editingCapability, .live)

        state.markNotActive()
        XCTAssertEqual(state.editingCapability, .readOnly)
    }

    // Amendment A2: live additionally requires a proven-drained outbox — pend must be
    // exactly 0. Pending (>0), failed count (-1), and absent (nil) all stay read-only.
    func testLiveRequiresDrainedOutbox() {
        let state = RelayActiveState()

        state.markActive(fullAccess: true, pendingSync: 5)
        XCTAssertEqual(state.editingCapability, .readOnly)
        XCTAssertTrue(state.isSyncPending)

        state.markActive(fullAccess: true, pendingSync: -1)
        XCTAssertEqual(state.editingCapability, .readOnly)
        XCTAssertTrue(state.isSyncPending)

        state.markActive(fullAccess: true)   // pend absent from payload
        XCTAssertEqual(state.editingCapability, .readOnly)

        state.markActive(fullAccess: true, pendingSync: 0)
        XCTAssertEqual(state.editingCapability, .live)
        XCTAssertFalse(state.isSyncPending)

        // Not-active or FA-off never counts as "sync pending" — those show the unlock hint.
        state.markActive(fullAccess: false, pendingSync: 5)
        XCTAssertFalse(state.isSyncPending)
        state.markNotActive()
        XCTAssertFalse(state.isSyncPending)
        XCTAssertNil(state.pendingSyncCount)
    }
}

final class EditorPublishSourceTest: XCTestCase {
    func testSettingsBackgroundPublishesPendingEditorChanges() throws {
        let source = try String(contentsOf: projectFileURL("LimeSettings/AppDelegate.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("applicationDidEnterBackground"))
        XCTAssertTrue(source.contains("DBServer.shared.publishPendingEditorChanges()"))
    }

    private func projectFileURL(_ relativePath: String) -> URL {
        if let bundled = Bundle(for: type(of: self)).resourceURL?.appendingPathComponent(relativePath),
           FileManager.default.fileExists(atPath: bundled.path) {
            return bundled
        }
        return URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
    }
}
