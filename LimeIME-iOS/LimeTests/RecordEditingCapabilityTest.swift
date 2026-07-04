import Foundation
import XCTest
@testable import LimeIME

final class RecordEditingCapabilityTest: XCTestCase {
    func testReadOnlyUnlessFAStateConfirmedOn() {
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .unknown), .readOnly)
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .confirmedOff), .readOnly)
        XCTAssertEqual(RecordEditingCapability.resolve(faState: .confirmedOn), .live)
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

    func testRefreshTableFromSnapshotCopiesScoresWithoutRevBump() throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("record-editing-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }

        let cold = try LimeDB(path: dir.appendingPathComponent("cold.db").path)
        let hot = try LimeDB(path: dir.appendingPathComponent("hot.db").path)
        defer {
            try? cold.closeForReplacement()
            try? hot.closeForReplacement()
        }
        XCTAssertGreaterThan(cold.addRecord("custom", ["code": "a", "word": "一", "score": 1]), 0)
        let before = try XCTUnwrap(cold.syncRevs()["custom"])

        XCTAssertGreaterThan(hot.addRecord("custom", ["code": "a", "word": "一", "score": 99],
                                           syncMode: .replace), 0)
        let snapshot = dir.appendingPathComponent("hot-snapshot.limedb")
        try hot.vacuumInto(snapshot.path)

        try cold.refreshTableFromSnapshot("custom", snapshotURL: snapshot)

        let after = try XCTUnwrap(cold.syncRevs()["custom"])
        let rows = cold.getRecordList("custom", nil, searchByCode: true, 10, 0)
        XCTAssertEqual(rows.first?.score, 99)
        XCTAssertEqual(after.rev, before.rev)
        XCTAssertEqual(after.mode, before.mode)
    }
}
