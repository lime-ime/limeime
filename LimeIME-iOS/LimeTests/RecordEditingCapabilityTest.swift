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

final class EditorRefreshViewSourceTest: XCTestCase {
    func testRecordEditorOnlyHarvestsWhenRelayGateIsLiveAndUnlocksAfterRefresh() throws {
        let source = try String(contentsOf: projectFileURL("LimeSettings/Views/RecordListView.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("relayActiveState.editingCapability"))
        // RecordListView summons a probe and ALWAYS attempts the harvest — it does NOT gate on
        // `.live` (chicken-and-egg: `.live` needs the keyboard summoned first). Editing still
        // gates on `.live` via `canEdit` below.
        XCTAssertTrue(source.contains("guard !didAttemptHotRefresh else { return }"))
        XCTAssertTrue(source.contains("probeFocused = true"))
        XCTAssertTrue(source.contains("private var canEdit: Bool { !isRefreshingHotSnapshot && editingCapability == .live }"))
        XCTAssertTrue(source.contains("systemImage: capabilityIcon"))
        XCTAssertTrue(source.contains("if isRefreshingHotSnapshot { return \"同步中...\" }"))
        XCTAssertTrue(source.contains(".redacted(reason: isRefreshingHotSnapshot ? .placeholder : [])"))
        XCTAssertTrue(source.contains(".onDisappear { publishEditorCloseIfNeeded() }"))
        XCTAssertTrue(source.contains("scenePhase == .background"))
        XCTAssertTrue(source.contains("// ponytail: background publish closes the only editor/keyboard learning interleave"))
    }

    func testRelatedEditorOnlyHarvestsWhenRelayGateIsLiveAndUnlocksAfterRefresh() throws {
        let source = try String(contentsOf: projectFileURL("LimeSettings/Views/RelatedListView.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("relayActiveState.editingCapability"))
        XCTAssertTrue(source.contains("guard !didAttemptHotRefresh, relayEditingCapability == .live else { return }"))
        XCTAssertTrue(source.contains("@FocusState private var probeFocused: Bool"))
        XCTAssertTrue(source.contains("probeFocused = true"))
        XCTAssertTrue(source.contains("FAStateResolver.activeProbeWaitNanoseconds"))
        XCTAssertTrue(source.contains("probeFocused = false"))
        XCTAssertTrue(source.contains("case .failure(let error):"))
        XCTAssertTrue(source.contains("error.localizedDescription"))
        XCTAssertTrue(source.contains("private var canEdit: Bool { !isRefreshingHotSnapshot && editingCapability == .live }"))
        XCTAssertTrue(source.contains("systemImage: capabilityIcon"))
        XCTAssertTrue(source.contains("if isRefreshingHotSnapshot { return \"同步中...\" }"))
        XCTAssertTrue(source.contains(".redacted(reason: isRefreshingHotSnapshot ? .placeholder : [])"))
        XCTAssertTrue(source.contains(".onDisappear { publishEditorCloseIfNeeded() }"))
        XCTAssertTrue(source.contains("scenePhase == .background"))
        XCTAssertTrue(source.contains("// ponytail: background publish closes the only editor/keyboard learning interleave"))
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
