// SyncMetaStoreTest.swift

import GRDB
import XCTest
@testable import LimeIME

final class SyncMetaStoreTest: XCTestCase {
    private func tempDir() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private func makeDatabase(at url: URL, marker: String = "seed") throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        let queue = try DatabaseQueue(path: url.path)
        defer { try? queue.close() }
        try queue.write { db in
            try db.execute(sql: "PRAGMA user_version = 104")
            try db.execute(sql: "CREATE TABLE marker (value TEXT NOT NULL)")
            try db.execute(sql: "INSERT INTO marker (value) VALUES (?)", arguments: [marker])
        }
    }

    private func userVersion(_ url: URL) throws -> Int {
        let queue = try DatabaseQueue(path: url.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Int.fetchOne(db, sql: "PRAGMA user_version") ?? 0
        }
    }

    private func marker(_ url: URL) throws -> String? {
        let queue = try DatabaseQueue(path: url.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try String.fetchOne(db, sql: "SELECT value FROM marker LIMIT 1")
        }
    }

    func testSyncConnectionSetsItsOwnBusyTimeout() throws {
        let dir = try tempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let dbURL = dir.appendingPathComponent("lime.db")
        try makeDatabase(at: dbURL)

        let connection = try SyncDatabaseConnection(databaseURL: dbURL, busyTimeoutMilliseconds: 1234)

        XCTAssertEqual(try connection.busyTimeoutMilliseconds(), 1234)
    }

    func testSyncMetaCrudRoundTripOnSyncConnection() throws {
        let dir = try tempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let dbURL = dir.appendingPathComponent("lime.db")
        try makeDatabase(at: dbURL)
        let store = try SyncMetaStore(databaseURL: dbURL)

        try store.setValue("epoch-a", forKey: SyncMetaStore.epochUUIDKey)
        XCTAssertEqual(try store.value(forKey: SyncMetaStore.epochUUIDKey), "epoch-a")

        try store.setValue("epoch-b", forKey: SyncMetaStore.epochUUIDKey)
        XCTAssertEqual(try store.value(forKey: SyncMetaStore.epochUUIDKey), "epoch-b")

        try store.removeValue(forKey: SyncMetaStore.epochUUIDKey)
        XCTAssertNil(try store.value(forKey: SyncMetaStore.epochUUIDKey))
    }

    func testEpochGenerationAndRevisionBumpsAreStoredInSyncMeta() throws {
        let dir = try tempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let dbURL = dir.appendingPathComponent("lime.db")
        try makeDatabase(at: dbURL)
        let store = try SyncMetaStore(databaseURL: dbURL)

        let firstEpoch = try store.replaceEpochUUID()
        let secondEpoch = try store.replaceEpochUUID()

        XCTAssertFalse(firstEpoch.isEmpty)
        XCTAssertFalse(secondEpoch.isEmpty)
        XCTAssertNotEqual(firstEpoch, secondEpoch)
        XCTAssertEqual(try store.epochUUID(), secondEpoch)
        XCTAssertEqual(try store.bumpGeneration(), 1)
        XCTAssertEqual(try store.bumpGeneration(), 2)
        XCTAssertEqual(try store.generation(), 2)
        XCTAssertEqual(try store.bumpRevision(forTable: "custom"), 1)
        XCTAssertEqual(try store.bumpRevision(forTable: "custom"), 2)
        XCTAssertEqual(try store.revision(forTable: "custom"), 2)
    }

    func testSyncMetaOperationsDoNotBumpUserVersion() throws {
        let dir = try tempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let dbURL = dir.appendingPathComponent("lime.db")
        try makeDatabase(at: dbURL)
        let store = try SyncMetaStore(databaseURL: dbURL)

        _ = try store.replaceEpochUUID()
        _ = try store.bumpGeneration()
        _ = try store.bumpRevision(forTable: "custom")

        XCTAssertEqual(try userVersion(dbURL), 104)
    }

    func testRunModeLocatorSplitsColdAndHotDatabasePaths() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let appSupport = root.appendingPathComponent("app-support", isDirectory: true)

        let locator = SyncDatabaseLocator(appGroupDirectory: appGroup,
                                          applicationSupportDirectory: appSupport)

        XCTAssertEqual(locator.coldDatabaseURL.path,
                       appGroup.appendingPathComponent("lime.db").path)
        XCTAssertEqual(locator.hotDatabaseURL.path,
                       appSupport.appendingPathComponent("LimeIME", isDirectory: true)
                           .appendingPathComponent("lime.db").path)
    }

    func testValidLegacyAppGroupDatabaseIsAdoptedAndStamped() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let hotDB = root.appendingPathComponent("app-support/LimeIME/lime.db")
        let legacyDB = root.appendingPathComponent("app-group/lime.db")
        let bundledDB = root.appendingPathComponent("bundle/lime.db")
        try makeDatabase(at: legacyDB, marker: "legacy")
        try makeDatabase(at: bundledDB, marker: "bundle")

        let result = try SyncDatabaseBootstrap.ensureKeyboardHotDatabase(
            hotDatabaseURL: hotDB,
            legacyDatabaseURL: legacyDB,
            bundledDefaultURL: bundledDB)

        XCTAssertEqual(result, .adoptedLegacy)
        XCTAssertEqual(try marker(hotDB), "legacy")
        XCTAssertNotNil(try SyncMetaStore(databaseURL: hotDB).epochUUID())
        XCTAssertEqual(try userVersion(hotDB), 104)
    }

    func testCorruptLegacyAppGroupDatabaseFallsBackToBundledDefault() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let hotDB = root.appendingPathComponent("app-support/LimeIME/lime.db")
        let legacyDB = root.appendingPathComponent("app-group/lime.db")
        let bundledDB = root.appendingPathComponent("bundle/lime.db")
        try FileManager.default.createDirectory(at: legacyDB.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        try Data("not sqlite".utf8).write(to: legacyDB)
        try makeDatabase(at: bundledDB, marker: "bundle")

        let result = try SyncDatabaseBootstrap.ensureKeyboardHotDatabase(
            hotDatabaseURL: hotDB,
            legacyDatabaseURL: legacyDB,
            bundledDefaultURL: bundledDB)

        XCTAssertEqual(result, .copiedBundledDefault)
        XCTAssertEqual(try marker(hotDB), "bundle")
        XCTAssertNotNil(try SyncMetaStore(databaseURL: hotDB).epochUUID())
        XCTAssertEqual(try userVersion(hotDB), 104)
    }
}
