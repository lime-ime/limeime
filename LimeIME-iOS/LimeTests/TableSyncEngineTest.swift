import GRDB
import XCTest
@testable import LimeIME

final class TableSyncEngineTest: XCTestCase {
    private struct Harness {
        let ownDir: URL
        let appGroupDir: URL
        let database: SharedDatabase
        let engine: TableSyncEngine
    }

    func testImportFromFolder() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let source = try writeSource(stem: "cj", rows: 100, in: h.appGroupDir)

        let events = h.engine.scanAndApply()

        let db = try XCTUnwrap(h.database.current())
        XCTAssertEqual(events, [SyncEvent(kind: .imported, stem: "cj")],
                       db.ledgerEntry(stem: "cj")?.error ?? "")
        XCTAssertEqual(try count("cj", in: db), 100)
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.state, .done)
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.identity, FileIdentity(url: source))
    }

    func testNoopOnSecondScan() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        try writeSource(stem: "cj", rows: 100, in: h.appGroupDir)
        _ = h.engine.scanAndApply()

        let events = h.engine.scanAndApply()

        XCTAssertEqual(events, [SyncEvent(kind: .noop, stem: nil)])
        let db = try XCTUnwrap(h.database.current())
        XCTAssertEqual(try count("cj", in: db), 100)
    }

    func testReimportPreservesLearnedScores() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        try writeSource(stem: "cj", rows: 100, in: h.appGroupDir)
        _ = h.engine.scanAndApply()
        let db = try XCTUnwrap(h.database.current())
        try db.dbQueue.write { sqlDB in
            try sqlDB.execute(sql: "UPDATE cj SET score = 77 WHERE code = 'c1'")
        }
        try writeSource(stem: "cj", rows: 100, in: h.appGroupDir, mtimeOffset: 10)

        let events = h.engine.scanAndApply()

        XCTAssertEqual(events, [SyncEvent(kind: .imported, stem: "cj")])
        XCTAssertEqual(try score(for: "c1", in: db), 77)
    }

    func testReimportCleanWhenRestoreLearningFalse() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        try writeSource(stem: "cj", rows: 100, in: h.appGroupDir)
        _ = h.engine.scanAndApply()
        let db = try XCTUnwrap(h.database.current())
        try db.dbQueue.write { sqlDB in
            try sqlDB.execute(sql: "UPDATE cj SET score = 77 WHERE code = 'c1'")
        }
        try writeTableMeta(stem: "cj", restoreLearning: false, in: h.appGroupDir)
        try writeSource(stem: "cj", rows: 100, in: h.appGroupDir, mtimeOffset: 10)

        let events = h.engine.scanAndApply()

        XCTAssertEqual(events, [SyncEvent(kind: .imported, stem: "cj")])
        XCTAssertEqual(try score(for: "c1", in: db), 0)
    }

    func testUninstallDrops() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let source = try writeSource(stem: "cj", rows: 100, in: h.appGroupDir)
        _ = h.engine.scanAndApply()
        try FileManager.default.removeItem(at: source)

        let events = h.engine.scanAndApply()

        let db = try XCTUnwrap(h.database.current())
        XCTAssertEqual(events, [SyncEvent(kind: .dropped, stem: "cj")])
        XCTAssertEqual(try count("cj", in: db), 0)
        XCTAssertNil(db.ledgerEntry(stem: "cj"))
    }

    // The seed lime.db ships an EMPTY im table, so the keyboard must register
    // imported IMs itself — otherwise getAllImConfigs() stays empty and the
    // keyboard remains English-only forever (final-review finding #1).
    func testImportRegistersIMRowAndDropRemovesIt() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let source = try writeSource(stem: "cj", rows: 10, in: h.appGroupDir)
        _ = h.engine.scanAndApply()

        let db = try XCTUnwrap(h.database.current())
        let title = try db.dbQueue.read { sqlDB in
            try String.fetchOne(sqlDB, sql: "SELECT title FROM im WHERE code = 'cj'")
        }
        XCTAssertEqual(title, "倉頡輸入法")

        try FileManager.default.removeItem(at: source)
        _ = h.engine.scanAndApply()
        let remaining = try db.dbQueue.read { sqlDB in
            try Int.fetchOne(sqlDB, sql: "SELECT COUNT(*) FROM im WHERE code = 'cj'") ?? 0
        }
        XCTAssertEqual(remaining, 0)
    }

    func testImportCopiesIMMetadataFromSource() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let source = try writeSource(stem: "cj", rows: 10, in: h.appGroupDir)
        // Give the source its own im row — its metadata is authoritative.
        let queue = try DatabaseQueue(path: source.path)
        try queue.write { sqlDB in
            try sqlDB.execute(sql: """
                CREATE TABLE IF NOT EXISTS im (code TEXT, title TEXT, desc TEXT,
                    keyboard TEXT, disable INTEGER, selkey TEXT, endkey TEXT, spacestyle TEXT)
            """)
            try sqlDB.execute(sql: """
                INSERT INTO im VALUES ('cj', '客製倉頡', '', 'lime_cj', 0, '123456789', '', '')
            """)
        }
        try queue.close()

        _ = h.engine.scanAndApply()

        let db = try XCTUnwrap(h.database.current())
        let row = try db.dbQueue.read { sqlDB in
            try Row.fetchOne(sqlDB, sql: "SELECT title, keyboard, selkey FROM im WHERE code = 'cj'")
        }
        XCTAssertEqual(row?["title"] as String?, "客製倉頡")
        XCTAssertEqual(row?["keyboard"] as String?, "lime_cj")
        XCTAssertEqual(row?["selkey"] as String?, "123456789")
    }

    func testDeadlineResume() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        try writeSource(stem: "cj", rows: 100_000, in: h.appGroupDir)

        _ = h.engine.scanAndApply(deadline: Date())

        let db = try XCTUnwrap(h.database.current())
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.state, .inProgress)
        XCTAssertLessThan(try count("cj", in: db), 100_000)

        let events = h.engine.scanAndApply()

        XCTAssertEqual(try count("cj", in: db), 100_000)
        XCTAssertEqual(events, [SyncEvent(kind: .imported, stem: "cj")],
                       db.ledgerEntry(stem: "cj")?.error ?? "")
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.state, .done)
    }

    func testIdentityChangeAbandonsResume() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        try writeSource(stem: "cj", rows: 100_000, in: h.appGroupDir)
        _ = h.engine.scanAndApply(deadline: Date())
        try writeSource(stem: "cj", rows: 50, in: h.appGroupDir, mtimeOffset: 10)

        let events = h.engine.scanAndApply()

        let db = try XCTUnwrap(h.database.current())
        XCTAssertEqual(events, [SyncEvent(kind: .imported, stem: "cj")])
        XCTAssertEqual(try count("cj", in: db), 50)
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.state, .done)
    }

    func testEpochApplied() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        try writeSource(stem: "array", rows: 10, in: h.appGroupDir)
        let db = try XCTUnwrap(h.database.current())
        let epoch = "E2E2E2E2-E2E2-4E2E-A2E2-E2E2E2E2E2E2"
        try writeRestore(from: db, in: h.appGroupDir, epochUUID: epoch, marker: true)

        let events = h.engine.scanAndApply()

        let reopened = try XCTUnwrap(h.database.current())
        XCTAssertEqual(events.first, SyncEvent(kind: .epochApplied, stem: nil))
        XCTAssertTrue(events.contains(SyncEvent(kind: .imported, stem: "array")))
        XCTAssertEqual(reopened.syncMeta("epoch_uuid"), epoch)
        XCTAssertEqual(try count("cj", whereCode: "epoch_marker", in: reopened), 1)
        XCTAssertEqual(try count("array", in: reopened), 10)
    }

    func testEpochSameUUIDNoop() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let db = try XCTUnwrap(h.database.current())
        let epoch = try XCTUnwrap(db.syncMeta("epoch_uuid"))
        let restore = try writeRestore(from: db, in: h.appGroupDir, epochUUID: epoch)
        try FileManager.default.setAttributes([.modificationDate: Date(timeIntervalSinceNow: 30)],
                                              ofItemAtPath: restore.path)

        let events = h.engine.scanAndApply()

        XCTAssertEqual(events, [SyncEvent(kind: .noop, stem: nil)])
        XCTAssertEqual(db.syncMeta("epoch_uuid"), epoch)
    }

    func testEpochFutureSchemaRefused() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let db = try XCTUnwrap(h.database.current())
        let originalEpoch = try XCTUnwrap(db.syncMeta("epoch_uuid"))
        try writeRestore(from: db,
                         in: h.appGroupDir,
                         epochUUID: "F2F2F2F2-F2F2-4F2F-A2F2-F2F2F2F2F2F2",
                         schemaVersion: LimeDB.CURRENT_DB_VERSION + 1)

        let events = h.engine.scanAndApply()

        XCTAssertEqual(events, [SyncEvent(kind: .failed, stem: "restore")])
        XCTAssertEqual(db.syncMeta("epoch_uuid"), originalEpoch)
    }

    private func makeHarness() throws -> Harness {
        let own = try tempDirectory()
        let ag = try tempDirectory()
        let database = SharedDatabase(runMode: .keyboard,
                                      dataDirOverride: own,
                                      appGroupOverride: ag)
        let engine = TableSyncEngine(database: database, baseURL: ag)
        _ = try XCTUnwrap(database.current())
        return Harness(ownDir: own, appGroupDir: ag, database: database, engine: engine)
    }

    private func cleanup(_ h: Harness) {
        h.database.closeCurrentForReplacement()
        try? FileManager.default.removeItem(at: h.ownDir)
        try? FileManager.default.removeItem(at: h.appGroupDir)
    }

    private func tempDirectory() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    @discardableResult
    private func writeSource(stem: String,
                             rows: Int,
                             in baseURL: URL,
                             mtimeOffset: TimeInterval = 0) throws -> URL {
        let url = SyncPaths.tableFile(baseURL, stem: stem)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        try? FileManager.default.removeItem(at: url)

        let queue = try DatabaseQueue(path: url.path)
        try queue.write { db in
            try db.execute(sql: "CREATE TABLE \(stem) (code TEXT, word TEXT, score INTEGER)")
            let insert = try db.makeStatement(sql: "INSERT INTO \(stem) (code, word, score) VALUES (?, ?, ?)")
            for i in 0..<rows {
                try insert.execute(arguments: ["c\(i)", "w\(i)", 0])
            }
        }
        try queue.close()
        try FileManager.default.setAttributes([.modificationDate: Date(timeIntervalSinceNow: mtimeOffset)],
                                              ofItemAtPath: url.path)
        return url
    }

    private func writeTableMeta(stem: String, restoreLearning: Bool, in baseURL: URL) throws {
        let meta = TableMeta(restoreLearning: restoreLearning,
                             displayName: nil,
                             provenance: nil)
        try atomicWrite(try JSONEncoder().encode(meta),
                        to: SyncPaths.tableMeta(baseURL, stem: stem))
    }

    @discardableResult
    private func writeRestore(from db: LimeDB,
                              in baseURL: URL,
                              epochUUID: String,
                              schemaVersion: Int = LimeDB.CURRENT_DB_VERSION,
                              marker: Bool = false) throws -> URL {
        let restore = SyncPaths.restoreDB(baseURL)
        try? FileManager.default.removeItem(at: restore)
        try db.dbQueue.writeWithoutTransaction { sqlDB in
            try sqlDB.execute(sql: "VACUUM INTO ?", arguments: [restore.path])
        }

        let restoreQueue = try DatabaseQueue(path: restore.path)
        try restoreQueue.write { sqlDB in
            try sqlDB.execute(sql: "CREATE TABLE IF NOT EXISTS sync_meta (key TEXT PRIMARY KEY, value TEXT)")
            try sqlDB.execute(sql: """
                CREATE TABLE IF NOT EXISTS sync_ledger (
                    stem TEXT PRIMARY KEY, size INTEGER, mtime REAL,
                    state TEXT NOT NULL, error TEXT,
                    attempts INTEGER NOT NULL DEFAULT 0, resume_marker INTEGER
                )
            """)
            try sqlDB.execute(sql: "DELETE FROM sync_ledger")
            try sqlDB.execute(sql: "INSERT OR REPLACE INTO sync_meta (key, value) VALUES ('epoch_uuid', ?)",
                              arguments: [epochUUID])
            try sqlDB.execute(sql: "INSERT OR REPLACE INTO sync_meta (key, value) VALUES ('schema_version', ?)",
                              arguments: ["\(schemaVersion)"])
            if marker {
                try sqlDB.execute(sql: """
                    INSERT INTO cj (code, word, score)
                    VALUES ('epoch_marker', 'marker', 0)
                """)
            }
        }
        try restoreQueue.close()

        let meta = RestoreMeta(epochUUID: epochUUID, schemaVersion: schemaVersion)
        try atomicWrite(try JSONEncoder().encode(meta), to: SyncPaths.restoreMeta(baseURL))
        return restore
    }

    private func count(_ stem: String, in db: LimeDB) throws -> Int {
        try db.dbQueue.read { sqlDB in
            try Int.fetchOne(sqlDB, sql: "SELECT COUNT(*) FROM \(stem)") ?? 0
        }
    }

    private func count(_ stem: String, whereCode code: String, in db: LimeDB) throws -> Int {
        try db.dbQueue.read { sqlDB in
            try Int.fetchOne(sqlDB,
                             sql: "SELECT COUNT(*) FROM \(stem) WHERE code = ?",
                             arguments: [code]) ?? 0
        }
    }

    private func score(for code: String, in db: LimeDB) throws -> Int {
        try db.dbQueue.read { sqlDB in
            try Int.fetchOne(sqlDB,
                             sql: "SELECT score FROM cj WHERE code = ? LIMIT 1",
                             arguments: [code]) ?? -1
        }
    }
}
