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
        XCTAssertTrue(source.contains("private var canEdit: Bool { !isRefreshingHotSnapshot && editingCapability == .live }"))
        XCTAssertTrue(source.contains("systemImage: capabilityIcon"))
        XCTAssertTrue(source.contains("if isRefreshingHotSnapshot { return \"同步中...\" }"))
        XCTAssertTrue(source.contains(".redacted(reason: isRefreshingHotSnapshot ? .placeholder : [])"))
        XCTAssertTrue(source.contains(".onDisappear { publishEditorCloseIfNeeded() }"))
        XCTAssertTrue(source.contains("scenePhase == .background"))
        XCTAssertTrue(source.contains("// ponytail: background publish closes the only editor/keyboard learning interleave"))
    }

    private func projectFileURL(_ relativePath: String) -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
    }
}
