// ColdPublisherTest.swift

import GRDB
import XCTest
@testable import LimeIME

final class ColdPublisherTest: XCTestCase {
    private func tempDir() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private func makeDatabase(at url: URL, epoch: String = "epoch-a") throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        let queue = try DatabaseQueue(path: url.path)
        defer { try? queue.close() }
        try queue.write { db in
            try db.execute(sql: "PRAGMA user_version = 104")
            try db.execute(sql: """
                CREATE TABLE custom (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT,
                    word TEXT,
                    score INTEGER DEFAULT 0,
                    basescore INTEGER DEFAULT 0,
                    code3r TEXT
                )
                """)
            try db.execute(sql: "INSERT INTO custom (code, word, score) VALUES ('a', '冷', 7)")
        }
        let meta = try SyncMetaStore(databaseURL: url)
        try meta.setValue(epoch, forKey: SyncMetaStore.epochUUIDKey)
    }

    func testPublishBumpsGenerationAndPublishesSnapshotWithSyncMeta() throws {
        let appGroup = try tempDir()
        defer { try? FileManager.default.removeItem(at: appGroup) }
        let liveCold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: liveCold)

        try ColdPublisher(liveColdDatabaseURL: liveCold,
                          appGroupBaseURL: appGroup).publish()

        let liveMeta = try SyncMetaStore(databaseURL: liveCold)
        let snapshotURL = SyncPaths.coldDB(appGroup)
        let snapshotMeta = try SyncMetaStore(databaseURL: snapshotURL)

        XCTAssertTrue(FileManager.default.fileExists(atPath: snapshotURL.path))
        XCTAssertEqual(try liveMeta.generation(), 1)
        XCTAssertEqual(try snapshotMeta.generation(), 1)
        XCTAssertEqual(try snapshotMeta.epochUUID(), "epoch-a")
    }
}
