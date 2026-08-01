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

final class EditorRefreshViewSourceTest: XCTestCase {
    func testRecordEditorLoadsColdImmediatelyWithoutProbeRefreshOrRelayGate() throws {
        let source = try String(contentsOf: projectFileURL("LimeSettings/Views/RecordListView.swift"),
                                encoding: .utf8)

        XCTAssertFalse(source.contains("relayActiveState.editingCapability"))
        XCTAssertFalse(source.contains("refreshTableFromKeyboard"))
        XCTAssertFalse(source.contains("probeFocused"))
        XCTAssertFalse(source.contains("FAStateResolver.activeProbeWaitNanoseconds"))
        XCTAssertTrue(source.contains(".onAppear { loadRecords() }"))
        XCTAssertTrue(source.contains("private var canEdit: Bool { true }"))
        XCTAssertTrue(source.contains("Text(record.score.description)"))
        XCTAssertTrue(source.contains(".onDisappear { publishEditorCloseIfNeeded() }"))
        XCTAssertTrue(source.contains("scenePhase == .background"))
        XCTAssertTrue(source.contains("setupController.publishEditorChanges(stem: tableName)"))
    }

    func testRelatedEditorLoadsColdImmediatelyWithoutProbeRefreshOrRelayGate() throws {
        let source = try String(contentsOf: projectFileURL("LimeSettings/Views/RelatedListView.swift"),
                                encoding: .utf8)

        XCTAssertFalse(source.contains("relayActiveState.editingCapability"))
        XCTAssertFalse(source.contains("refreshTableFromKeyboard"))
        XCTAssertFalse(source.contains("probeFocused"))
        XCTAssertFalse(source.contains("FAStateResolver.activeProbeWaitNanoseconds"))
        XCTAssertTrue(source.contains(".onAppear { loadPhrases() }"))
        XCTAssertTrue(source.contains("private var canEdit: Bool { true }"))
        XCTAssertTrue(source.contains("Text(phrase.score.description)"))
        XCTAssertTrue(source.contains(".onDisappear { publishEditorCloseIfNeeded() }"))
        XCTAssertTrue(source.contains("scenePhase == .background"))
        XCTAssertTrue(source.contains("setupController.publishEditorChanges(stem: \"related\")"))
    }

    func testSettingsBackgroundPublishesPendingEditorChanges() throws {
        let source = try String(contentsOf: projectFileURL("LimeSettings/AppDelegate.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("applicationDidEnterBackground"))
        XCTAssertTrue(source.contains("DBServer.shared.publishPendingEditorChanges()"))
    }

    private func projectFileURL(_ relativePath: String) -> URL {
        // Prefer the copy bundled into the test target — on Xcode Cloud the source
        // checkout is absent at test runtime, so #filePath resolves to a missing path.
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
