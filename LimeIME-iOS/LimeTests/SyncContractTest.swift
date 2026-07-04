// SyncContractTest.swift

import XCTest
@testable import LimeIME

final class SyncContractTest: XCTestCase {
    func testPathDerivations() {
        let base = URL(fileURLWithPath: "/tmp/x")

        XCTAssertEqual(SyncPaths.coldDB(base).path, "/tmp/x/cold.limedb")
        XCTAssertEqual(SyncPaths.coldMeta(base).path, "/tmp/x/cold.meta.json")
        XCTAssertEqual(SyncPaths.outboxDir(base).path, "/tmp/x/outbox")
        XCTAssertEqual(SyncPaths.exportRequest(base).path, "/tmp/x/outbox/export.request.json")
        XCTAssertEqual(SyncPaths.backupSnapshot(base).path, "/tmp/x/outbox/backup.limedb")
        XCTAssertEqual(SyncPaths.receipt(base).path, "/tmp/x/outbox/receipt.json")
    }

    func testFileIdentity() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let url = dir.appendingPathComponent("file")

        XCTAssertNil(FileIdentity(url: url))

        try atomicWrite(Data("a".utf8), to: url)
        let first = try XCTUnwrap(FileIdentity(url: url))

        try atomicWrite(Data("ab".utf8), to: url)
        let second = try XCTUnwrap(FileIdentity(url: url))

        XCTAssertNotEqual(first, second)
    }

    func testAtomicWriteReplacesAndLeavesNoTemp() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let url = dir.appendingPathComponent("payload.limedb")

        try atomicWrite(Data("first".utf8), to: url)
        try atomicWrite(Data("second".utf8), to: url)

        XCTAssertEqual(try String(contentsOf: url, encoding: .utf8), "second")
        let files = try FileManager.default.contentsOfDirectory(atPath: dir.path)
        XCTAssertEqual(files, ["payload.limedb"])
    }

    func testColdSnapshotMetaRoundTrip() throws {
        let meta = ColdSnapshotMeta(generation: 1, epochUUID: "E", schemaVersion: 104)
        let data = try JSONEncoder().encode(meta)

        XCTAssertNotEqual(Array(data.prefix(3)), [0xEF, 0xBB, 0xBF])
        XCTAssertEqual(try JSONDecoder().decode(ColdSnapshotMeta.self, from: data), meta)
    }
}
