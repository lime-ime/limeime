import GRDB
import XCTest
@testable import LimeIME

final class TableSyncEngineTest: XCTestCase {
    private struct Harness {
        let ownDir: URL
        let appGroupDir: URL
        let coldDir: URL
        let database: SharedDatabase
        let coldDatabase: SharedDatabase
        let engine: TableSyncEngine
        let publisher: ColdPublisher
        let prefs: LIMEPreferenceManager
        let defaults: UserDefaults
        let defaultsSuite: String
    }

    func testImportFromSnapshotRecordsRevAndSecondScanUsesFastPath() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let coldRev = try publishColdStem(h, stem: "cj", rows: 100)

        let events = h.engine.scanAndApply()

        let db = try XCTUnwrap(h.database.current())
        XCTAssertEqual(events, [SyncEvent(kind: .imported, stem: "cj")],
                       db.ledgerEntry(stem: "cj")?.error ?? "")
        XCTAssertEqual(try count("cj", in: db), 100)
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.state, .done)
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.rev, coldRev)
        let sidecar = try readColdMeta(in: h.appGroupDir)
        XCTAssertEqual(db.syncMeta("applied_generation"), "\(sidecar.generation)")

        try FileManager.default.removeItem(at: SyncPaths.coldDB(h.appGroupDir))
        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .noop, stem: nil)])
    }

    func testMetaOnlyChangeMirrorsIMWithoutReimportingRows() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let rev = try publishColdStem(h, stem: "cj", rows: 3, title: "Before")
        _ = h.engine.scanAndApply()
        let db = try XCTUnwrap(h.database.current())
        let beforeCount = try count("cj", in: db)

        try setIMRow(in: try XCTUnwrap(h.coldDatabase.current()), stem: "cj", title: "After")
        try h.publisher.publish()

        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .metaSynced, stem: nil)])
        XCTAssertEqual(try count("cj", in: db), beforeCount)
        XCTAssertEqual(try imTitle("cj", in: db), "After")
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.rev, rev)
    }

    func testMergePreservesLearnedScoreAndReplaceLetsColdWin() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        try publishColdStem(h, stem: "cj", rows: 3)
        _ = h.engine.scanAndApply()
        let db = try XCTUnwrap(h.database.current())
        try setScore(77, code: "c1", stem: "cj", in: db)

        try replaceRows(in: try XCTUnwrap(h.coldDatabase.current()),
                        stem: "cj", rows: 3, scoreForC1: 5, mode: .merge)
        try h.publisher.publish()
        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .imported, stem: "cj")])
        XCTAssertEqual(try score(for: "c1", stem: "cj", in: db), 77)

        try replaceRows(in: try XCTUnwrap(h.coldDatabase.current()),
                        stem: "cj", rows: 3, scoreForC1: 5, mode: .replace)
        try h.publisher.publish()
        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .imported, stem: "cj")])
        XCTAssertEqual(try score(for: "c1", stem: "cj", in: db), 5)
    }

    func testDeadlineResumeUsesRevAndNewRevRestartsPartialImport() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let firstRev = try publishColdStem(h, stem: "cj", rows: 50_000)

        XCTAssertTrue(h.engine.scanAndApply(deadline: Date()).isEmpty)

        let db = try XCTUnwrap(h.database.current())
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.state, .inProgress)
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.rev, firstRev)
        XCTAssertLessThan(try count("cj", in: db), 50_000)

        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .imported, stem: "cj")])
        XCTAssertEqual(try count("cj", in: db), 50_000)
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.state, .done)
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.rev, firstRev)

        try replaceRows(in: try XCTUnwrap(h.coldDatabase.current()),
                        stem: "cj", rows: 50_000, mode: .merge)
        try h.publisher.publish()
        XCTAssertTrue(h.engine.scanAndApply(deadline: Date()).isEmpty)
        XCTAssertLessThan(try count("cj", in: db), 50_000)

        let newRev = try replaceRows(in: try XCTUnwrap(h.coldDatabase.current()),
                                     stem: "cj", rows: 12, mode: .merge)
        try h.publisher.publish()
        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .imported, stem: "cj")])
        XCTAssertEqual(try count("cj", in: db), 12)
        XCTAssertEqual(db.ledgerEntry(stem: "cj")?.rev, newRev)
    }

    func testDropClearsHotRowsIMAndLedgerWhenColdTableIsEmptyAndUnregistered() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        try publishColdStem(h, stem: "cj", rows: 10)
        _ = h.engine.scanAndApply()

        let cold = try XCTUnwrap(h.coldDatabase.current())
        try cold.dbQueue.write { sqlDB in
            try sqlDB.execute(sql: "DELETE FROM cj")
            try sqlDB.execute(sql: "DELETE FROM im WHERE code = 'cj'")
            try cold.bumpSyncRev("cj", mode: .merge, in: sqlDB)
        }
        try h.publisher.publish()

        let events = h.engine.scanAndApply()

        let db = try XCTUnwrap(h.database.current())
        XCTAssertEqual(events, [SyncEvent(kind: .dropped, stem: "cj")])
        XCTAssertEqual(try count("cj", in: db), 0)
        XCTAssertNil(try imTitle("cj", in: db))
        XCTAssertNil(db.ledgerEntry(stem: "cj"))
    }

    func testEpochRebuildPreservesLearnedRowsWhenPrefIsOn() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        h.prefs.setRestoreOnImport(true, for: "cj")
        let hot = try XCTUnwrap(h.database.current())
        try replaceRows(in: hot, stem: "cj", rows: 3, scoreForC1: 77, bumpRev: false)

        let cold = try XCTUnwrap(h.coldDatabase.current())
        try replaceRows(in: cold, stem: "cj", rows: 3, scoreForC1: 5, mode: .merge)
        try setIMRow(in: cold, stem: "cj", title: "Epoch CJ")
        _ = try cold.bumpEpoch()
        let sidecar = try h.publisher.publish()

        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .epochApplied, stem: nil)])

        let reopened = try XCTUnwrap(h.database.current())
        XCTAssertEqual(try score(for: "c1", stem: "cj", in: reopened), 77)
        XCTAssertEqual(reopened.syncMeta("applied_generation"), "\(sidecar.generation)")
        XCTAssertEqual(reopened.ledgerEntry(stem: "cj")?.rev, cold.syncRevs()["cj"]?.rev)
    }

    func testEpochRebuildWipesLearnedRowsWhenPrefIsOff() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        h.prefs.setRestoreOnImport(false, for: "cj")
        let hot = try XCTUnwrap(h.database.current())
        try replaceRows(in: hot, stem: "cj", rows: 3, scoreForC1: 77, bumpRev: false)

        let cold = try XCTUnwrap(h.coldDatabase.current())
        try replaceRows(in: cold, stem: "cj", rows: 3, scoreForC1: 5, mode: .merge)
        try setIMRow(in: cold, stem: "cj", title: "Epoch CJ")
        _ = try cold.bumpEpoch()
        try h.publisher.publish()

        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .epochApplied, stem: nil)])
        XCTAssertEqual(try score(for: "c1", stem: "cj", in: try XCTUnwrap(h.database.current())), 5)
    }

    func testFutureSchemaSidecarFailsWithoutTouchingHot() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let hot = try XCTUnwrap(h.database.current())
        try replaceRows(in: hot, stem: "cj", rows: 3, scoreForC1: 77, bumpRev: false)
        var sidecar = try publishColdStemAndReadMeta(h, stem: "cj", rows: 3)
        sidecar.schemaVersion = LimeDB.CURRENT_DB_VERSION + 1
        try atomicWrite(try JSONEncoder().encode(sidecar), to: SyncPaths.coldMeta(h.appGroupDir))

        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .failed, stem: "cold")])
        XCTAssertEqual(try score(for: "c1", stem: "cj", in: hot), 77)
        XCTAssertNil(hot.syncMeta("applied_generation"))
    }

    func testMidPublishGenerationMismatchNoopsAndRetryAppliesRepublishedSnapshot() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        var sidecar = try publishColdStemAndReadMeta(h, stem: "cj", rows: 2)
        sidecar.generation += 1
        try atomicWrite(try JSONEncoder().encode(sidecar), to: SyncPaths.coldMeta(h.appGroupDir))

        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .noop, stem: nil)])
        XCTAssertNil(try XCTUnwrap(h.database.current()).syncMeta("applied_generation"))

        try h.publisher.publish()
        XCTAssertEqual(h.engine.scanAndApply(), [SyncEvent(kind: .imported, stem: "cj")])
        XCTAssertEqual(try count("cj", in: try XCTUnwrap(h.database.current())), 2)
    }

    func testExportRequestStillHonoredAfterTableWork() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        try publishColdStem(h, stem: "cj", rows: 3)
        let request = ExportRequest(requestUUID: UUID().uuidString,
                                    expiresAt: Date().timeIntervalSince1970 + 60)
        try atomicWrite(try JSONEncoder().encode(request), to: SyncPaths.exportRequest(h.appGroupDir))

        let events = h.engine.scanAndApply()

        XCTAssertEqual(events, [
            SyncEvent(kind: .imported, stem: "cj"),
            SyncEvent(kind: .exported, stem: nil),
        ])
        XCTAssertFalse(FileManager.default.fileExists(atPath: SyncPaths.exportRequest(h.appGroupDir).path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: SyncPaths.backupSnapshot(h.appGroupDir).path))
        XCTAssertEqual(try quickCheck(SyncPaths.backupSnapshot(h.appGroupDir)), "ok")
    }

    private func makeHarness() throws -> Harness {
        let own = try tempDirectory()
        let ag = try tempDirectory()
        let coldDir = try tempDirectory()
        let database = SharedDatabase(runMode: .keyboard,
                                      dataDirOverride: own,
                                      appGroupOverride: ag)
        let coldDatabase = SharedDatabase(runMode: .app,
                                          dataDirOverride: coldDir)
        let hot = try XCTUnwrap(database.current())
        let cold = try XCTUnwrap(coldDatabase.current())
        let hotEpoch = try hot.ensureEpochUUID()
        try cold.setSyncMeta("epoch_uuid", hotEpoch)
        try cold.setSyncMeta("schema_version", "\(LimeDB.CURRENT_DB_VERSION)")

        let defaultsSuite = "TableSyncEngineTest.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: defaultsSuite))
        defaults.removePersistentDomain(forName: defaultsSuite)
        let prefs = LIMEPreferenceManager(defaults: defaults)
        let engine = TableSyncEngine(database: database, baseURL: ag, prefs: prefs)
        let publisher = ColdPublisher(database: coldDatabase, baseURL: ag)
        return Harness(ownDir: own, appGroupDir: ag, coldDir: coldDir,
                       database: database, coldDatabase: coldDatabase,
                       engine: engine, publisher: publisher,
                       prefs: prefs, defaults: defaults, defaultsSuite: defaultsSuite)
    }

    private func cleanup(_ h: Harness) {
        h.database.closeCurrentForReplacement()
        h.coldDatabase.closeCurrentForReplacement()
        h.defaults.removePersistentDomain(forName: h.defaultsSuite)
        try? FileManager.default.removeItem(at: h.ownDir)
        try? FileManager.default.removeItem(at: h.appGroupDir)
        try? FileManager.default.removeItem(at: h.coldDir)
    }

    private func tempDirectory() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    @discardableResult
    private func publishColdStem(_ h: Harness,
                                 stem: String,
                                 rows: Int,
                                 title: String = "倉頡輸入法") throws -> Int64 {
        let cold = try XCTUnwrap(h.coldDatabase.current())
        let hot = try XCTUnwrap(h.database.current())
        let rev = try XCTUnwrap(replaceRows(in: cold, stem: stem, rows: rows, mode: .merge))
        try setIMRow(in: cold, stem: stem, title: title)
        try setIMRow(in: hot, stem: stem, title: title)
        try h.publisher.publish()
        return rev
    }

    private func publishColdStemAndReadMeta(_ h: Harness, stem: String, rows: Int) throws -> ColdSnapshotMeta {
        try publishColdStem(h, stem: stem, rows: rows)
        return try readColdMeta(in: h.appGroupDir)
    }

    private func readColdMeta(in baseURL: URL) throws -> ColdSnapshotMeta {
        try JSONDecoder().decode(ColdSnapshotMeta.self,
                                 from: Data(contentsOf: SyncPaths.coldMeta(baseURL)))
    }

    @discardableResult
    private func replaceRows(in db: LimeDB,
                             stem: String,
                             rows: Int,
                             scoreForC1: Int = 0,
                             mode: SyncRevMode = .merge,
                             bumpRev: Bool = true) throws -> Int64? {
        try db.dbQueue.write { sqlDB in
            try createMappingTable(stem, in: sqlDB)
            try sqlDB.execute(sql: "DELETE FROM \(quoted(stem))")
            let insert = try sqlDB.makeStatement(sql: """
                INSERT INTO \(quoted(stem)) (code, word, score)
                VALUES (?, ?, ?)
            """)
            for i in 0..<rows {
                try insert.execute(arguments: ["c\(i)", "w\(i)", i == 1 ? scoreForC1 : 0])
            }
            if bumpRev {
                try db.bumpSyncRev(stem, mode: mode, in: sqlDB)
            }
        }
        return db.syncRevs()[stem]?.rev
    }

    private func createMappingTable(_ stem: String, in db: Database) throws {
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS \(quoted(stem)) (
                _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                code      TEXT,
                word      TEXT,
                score     INTEGER DEFAULT 0,
                basescore INTEGER DEFAULT 0,
                code3r    TEXT
            )
        """)
    }

    private func setIMRow(in db: LimeDB, stem: String, title: String) throws {
        try db.dbQueue.write { sqlDB in
            try sqlDB.execute(sql: """
                DELETE FROM im WHERE code = ?
            """, arguments: [stem])
            try sqlDB.execute(sql: """
                INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                VALUES (?, ?, '', 'lime_cj', 0, '123456789', '', '')
            """, arguments: [stem, title])
        }
    }

    private func count(_ stem: String, in db: LimeDB) throws -> Int {
        try db.dbQueue.read { sqlDB in
            try Int.fetchOne(sqlDB, sql: "SELECT COUNT(*) FROM \(quoted(stem))") ?? 0
        }
    }

    private func score(for code: String, stem: String, in db: LimeDB) throws -> Int {
        try db.dbQueue.read { sqlDB in
            try Int.fetchOne(sqlDB,
                             sql: "SELECT score FROM \(quoted(stem)) WHERE code = ? LIMIT 1",
                             arguments: [code]) ?? -1
        }
    }

    private func setScore(_ score: Int, code: String, stem: String, in db: LimeDB) throws {
        try db.dbQueue.write { sqlDB in
            try sqlDB.execute(sql: "UPDATE \(quoted(stem)) SET score = ? WHERE code = ?",
                              arguments: [score, code])
        }
    }

    private func imTitle(_ stem: String, in db: LimeDB) throws -> String? {
        try db.dbQueue.read { sqlDB in
            try String.fetchOne(sqlDB,
                                sql: "SELECT title FROM im WHERE code = ? LIMIT 1",
                                arguments: [stem])
        }
    }

    private func quickCheck(_ url: URL) throws -> String {
        var config = Configuration()
        config.readonly = true
        let queue = try DatabaseQueue(path: url.path, configuration: config)
        defer { try? queue.close() }
        return try queue.read { db in
            try String.fetchOne(db, sql: "PRAGMA quick_check") ?? ""
        }
    }

    private func quoted(_ identifier: String) -> String {
        "\"\(identifier.replacingOccurrences(of: "\"", with: "\"\""))\""
    }
}
