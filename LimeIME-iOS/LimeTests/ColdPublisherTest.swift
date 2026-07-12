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
