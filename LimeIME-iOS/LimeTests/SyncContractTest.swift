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
        XCTAssertEqual(SyncPaths.learnedScores(base).path, "/tmp/x/outbox/learned-scores.json")
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

    func testAppLaunchCleanupRemovesLegacyV1ArtifactsOnly() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let tables = dir.appendingPathComponent("tables", isDirectory: true)
        let restoreDB = dir.appendingPathComponent(["restore", "limedb"].joined(separator: "."))
        let restoreSidecar = dir.appendingPathComponent(["restore", "meta", "json"].joined(separator: "."))
        let keep = dir.appendingPathComponent("cold.limedb")
        try FileManager.default.createDirectory(at: tables, withIntermediateDirectories: true)
        try Data().write(to: tables.appendingPathComponent("cj.limedb"))
        try Data().write(to: restoreDB)
        try Data().write(to: restoreSidecar)
        try Data().write(to: keep)

        AppDelegate.removeLegacyV1Artifacts(in: dir)

        XCTAssertFalse(FileManager.default.fileExists(atPath: tables.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: restoreDB.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: restoreSidecar.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: keep.path))
    }

    func testLearnedScoresFileRoundTrip() throws {
        let file = LearnedScoresFile(tables: [
            "custom": [LearnedScoreRow(a: "abc", b: "測", s: 3)]
        ])

        let data = try JSONEncoder().encode(file)
        let decoded = try JSONDecoder().decode(LearnedScoresFile.self, from: data)

        let row = try XCTUnwrap(decoded.tables["custom"]?.first)
        XCTAssertEqual(row.a, "abc")
        XCTAssertEqual(row.b, "測")
        XCTAssertEqual(row.s, 3)
    }
}
