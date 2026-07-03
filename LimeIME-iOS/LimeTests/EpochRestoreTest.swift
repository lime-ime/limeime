import XCTest
import GRDB
import ZIPFoundation
@testable import LimeIME

final class EpochRestoreTest: XCTestCase {
    private struct Harness {
        let ownDir: URL
        let appGroupDir: URL
        let database: SharedDatabase
        let engine: TableSyncEngine
    }

    @MainActor
    func testRestoreZipEndToEnd() async throws {
        let appGroup = try tempDirectory()
        let own = try tempDirectory()
        defer {
            try? FileManager.default.removeItem(at: appGroup)
            try? FileManager.default.removeItem(at: own)
        }
        let sourceDB = try makeRestoreSource(rows: [("zip_marker", "還原", 3)])
        let backupZip = try makeBackupZip(databaseURL: sourceDB)
        let controller = LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatabaseDirectory: appGroup),
            prefs: makePrefs(),
            progress: LimeIME.ProgressManager())

        let result = await controller.restoreDB(from: backupZip)

        if case .failure(let error) = result {
            XCTFail("Expected restore delivery to succeed, got \(error)")
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: SyncPaths.restoreDB(appGroup).path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: SyncPaths.restoreMeta(appGroup).path))
        XCTAssertTrue(try desiredTableSources(in: appGroup).isEmpty)

        let meta = try restoreMeta(in: appGroup)
        let database = SharedDatabase(runMode: .keyboard,
                                      dataDirOverride: own,
                                      appGroupOverride: appGroup)
        let engine = TableSyncEngine(database: database, baseURL: appGroup)
        _ = try XCTUnwrap(database.current())

        let events = engine.scanAndApply()

        let canonical = try XCTUnwrap(database.current())
        XCTAssertEqual(events, [SyncEvent(kind: .epochApplied, stem: nil)])
        XCTAssertEqual(canonical.syncMeta("epoch_uuid"), meta.epochUUID)
        XCTAssertEqual(try countRows("cj", whereCode: "zip_marker", in: canonical), 1)
        database.closeCurrentForReplacement()
    }

    @MainActor
    func testFactoryReset() async throws {
        let appGroup = try tempDirectory()
        defer { try? FileManager.default.removeItem(at: appGroup) }
        try writeSource(stem: "cj", rows: 1, in: appGroup)
        let controller = LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatabaseDirectory: appGroup),
            prefs: makePrefs(),
            progress: LimeIME.ProgressManager())
        let bundled = try XCTUnwrap(Bundle.main.url(forResource: "lime", withExtension: "db"))
        let expectedKeyboardRows = try rawCount("keyboard", in: bundled)

        let result = await controller.restoreBundledDatabase()

        if case .failure(let error) = result {
            XCTFail("Expected bundled restore delivery to succeed, got \(error)")
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: SyncPaths.restoreDB(appGroup).path))
        XCTAssertTrue(try desiredTableSources(in: appGroup).isEmpty)
        XCTAssertEqual(try rawCount("keyboard", in: SyncPaths.restoreDB(appGroup)), expectedKeyboardRows)
        XCTAssertGreaterThan(expectedKeyboardRows, 0)
    }

    func testRestoreThenInstallLayering() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let store = TableStore(baseURL: h.appGroupDir)
        try store.prepareRestore(from: makeRestoreSource(rows: [("base", "底", 0)]))
        try store.installLimedb(from: makeTableDB(stem: "cj", rows: [("layer", "層", 0)]),
                                stem: "cj",
                                meta: nil)

        let events = h.engine.scanAndApply()

        let db = try XCTUnwrap(h.database.current())
        XCTAssertEqual(events, [
            SyncEvent(kind: .epochApplied, stem: nil),
            SyncEvent(kind: .imported, stem: "cj"),
        ])
        XCTAssertEqual(try countRows("cj", whereCode: "base", in: db), 0)
        XCTAssertEqual(try countRows("cj", whereCode: "layer", in: db), 1)
    }

    @MainActor
    func testSkewRejected() async throws {
        let appGroup = try tempDirectory()
        defer { try? FileManager.default.removeItem(at: appGroup) }
        let before = try writeSource(stem: "cj", rows: 1, in: appGroup)
        let beforeIdentity = try XCTUnwrap(FileIdentity(url: before))
        let sourceDB = try makeRestoreSource(schemaVersion: LimeDB.CURRENT_DB_VERSION + 1,
                                             rows: [("future", "未來", 0)])
        let backupZip = try makeBackupZip(databaseURL: sourceDB)
        let controller = LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatabaseDirectory: appGroup),
            prefs: makePrefs(),
            progress: LimeIME.ProgressManager())

        let result = await controller.restoreDB(from: backupZip)

        guard case .failure(let error) = result else {
            return XCTFail("Expected future schema restore to fail")
        }
        XCTAssertTrue(error.localizedDescription.contains("請先更新"))
        XCTAssertEqual(FileIdentity(url: before), beforeIdentity)
        XCTAssertFalse(FileManager.default.fileExists(atPath: SyncPaths.restoreDB(appGroup).path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: SyncPaths.restoreMeta(appGroup).path))
    }

    func testBackupRelayRoundTrip() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let db = try XCTUnwrap(h.database.current())
        try insertRows(into: "cj", rows: [("learned", "學習", 88)], in: db)
        let requestUUID = UUID().uuidString
        try writeExportRequest(requestUUID: requestUUID,
                               expiresAt: Date().timeIntervalSince1970 + 120,
                               in: h.appGroupDir)

        let events = h.engine.scanAndApply()

        XCTAssertTrue(events.contains(SyncEvent(kind: .exported, stem: nil)))
        let receipt = try JSONDecoder().decode(ExportReceipt.self,
                                               from: Data(contentsOf: SyncPaths.receipt(h.appGroupDir)))
        XCTAssertEqual(receipt.requestUUID, requestUUID)
        XCTAssertEqual(receipt.epochUUID, db.syncMeta("epoch_uuid"))
        XCTAssertEqual(try quickCheck(SyncPaths.backupSnapshot(h.appGroupDir)), "ok")
        XCTAssertEqual(try rawCount("cj", whereCode: "learned", in: SyncPaths.backupSnapshot(h.appGroupDir)), 1)

        try? FileManager.default.removeItem(at: SyncPaths.backupSnapshot(h.appGroupDir))
        try? FileManager.default.removeItem(at: SyncPaths.receipt(h.appGroupDir))
        try writeExportRequest(requestUUID: UUID().uuidString,
                               expiresAt: Date().timeIntervalSince1970 - 1,
                               in: h.appGroupDir)

        _ = h.engine.scanAndApply()

        XCTAssertFalse(FileManager.default.fileExists(atPath: SyncPaths.backupSnapshot(h.appGroupDir).path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: SyncPaths.receipt(h.appGroupDir).path))
    }

    func testBackupDefersDuringImport() throws {
        let h = try makeHarness()
        defer { cleanup(h) }
        let source = try writeSource(stem: "cj", rows: 7, in: h.appGroupDir)
        let db = try XCTUnwrap(h.database.current())
        try db.dbQueue.write { sqlDB in
            try sqlDB.execute(sql: """
                CREATE TABLE IF NOT EXISTS cj (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT, word TEXT, score INTEGER DEFAULT 0,
                    basescore INTEGER DEFAULT 0, code3r TEXT
                )
            """)
            try sqlDB.execute(sql: """
                CREATE TABLE IF NOT EXISTS sync_stash (
                    stem TEXT, code TEXT, word TEXT, score INTEGER
                )
            """)
            try db.upsertLedger(LedgerEntry(stem: "cj",
                                            identity: FileIdentity(url: source),
                                            state: .inProgress,
                                            error: nil,
                                            attempts: 1,
                                            resumeMarker: 0),
                                in: sqlDB)
        }
        try writeExportRequest(requestUUID: "during-import",
                               expiresAt: Date().timeIntervalSince1970 + 120,
                               in: h.appGroupDir)

        let events = h.engine.scanAndApply()

        XCTAssertEqual(events, [
            SyncEvent(kind: .imported, stem: "cj"),
            SyncEvent(kind: .exported, stem: nil),
        ])
        XCTAssertEqual(try rawCount("cj", in: SyncPaths.backupSnapshot(h.appGroupDir)), 7)
    }

    private func makeHarness() throws -> Harness {
        let own = try tempDirectory()
        let appGroup = try tempDirectory()
        let database = SharedDatabase(runMode: .keyboard,
                                      dataDirOverride: own,
                                      appGroupOverride: appGroup)
        let engine = TableSyncEngine(database: database, baseURL: appGroup)
        _ = try XCTUnwrap(database.current())
        return Harness(ownDir: own, appGroupDir: appGroup, database: database, engine: engine)
    }

    private func cleanup(_ h: Harness) {
        h.database.closeCurrentForReplacement()
        try? FileManager.default.removeItem(at: h.ownDir)
        try? FileManager.default.removeItem(at: h.appGroupDir)
    }

    private func tempDirectory() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("EpochRestoreTest-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private func makePrefs() -> LimeIME.LIMEPreferenceManager {
        LimeIME.LIMEPreferenceManager(defaults: UserDefaults(suiteName: "test.epoch.restore.\(UUID().uuidString)")!)
    }

    private func makeBackupZip(databaseURL: URL) throws -> URL {
        let dir = try tempDirectory()
        let zip = dir.appendingPathComponent("backup.zip")
        let sharedPrefs = dir.appendingPathComponent(DBServer.sharedPrefsBackupName)
        let manifest = dir.appendingPathComponent(DBServer.preferenceManifestPath)
        try FileManager.default.createDirectory(at: manifest.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        try PropertyListSerialization.data(fromPropertyList: [:], format: .binary, options: 0)
            .write(to: sharedPrefs)
        try PreferenceBackupAdapter.exportManifestData(defaults: UserDefaults.standard, sourcePlatform: "ios")
            .write(to: manifest)
        let archive = try Archive(url: zip, accessMode: .create)
        try archive.addEntry(with: DBServer.databaseName, fileURL: databaseURL)
        try archive.addEntry(with: DBServer.sharedPrefsBackupName, fileURL: sharedPrefs)
        try archive.addEntry(with: DBServer.preferenceManifestPath, fileURL: manifest)
        return zip
    }

    private func makeRestoreSource(schemaVersion: Int = LimeDB.CURRENT_DB_VERSION,
                                   rows: [(String, String, Int)]) throws -> URL {
        let url = try tempDirectory().appendingPathComponent("lime.db")
        let queue = try DatabaseQueue(path: url.path)
        try queue.write { db in
            try db.execute(sql: """
                CREATE TABLE cj (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT, word TEXT, score INTEGER DEFAULT 0,
                    basescore INTEGER DEFAULT 0, code3r TEXT
                )
            """)
            try db.execute(sql: "CREATE TABLE sync_meta (key TEXT PRIMARY KEY, value TEXT)")
            try db.execute(sql: "INSERT INTO sync_meta (key, value) VALUES ('epoch_uuid', 'old')")
            try db.execute(sql: "INSERT INTO sync_meta (key, value) VALUES ('schema_version', ?)",
                           arguments: ["\(schemaVersion)"])
            for row in rows {
                try db.execute(sql: "INSERT INTO cj (code, word, score) VALUES (?, ?, ?)",
                               arguments: [row.0, row.1, row.2])
            }
        }
        try queue.close()
        return url
    }

    private func makeTableDB(stem: String, rows: [(String, String, Int)]) throws -> URL {
        let url = try tempDirectory().appendingPathComponent("\(stem).limedb")
        let queue = try DatabaseQueue(path: url.path)
        try queue.write { db in
            try db.execute(sql: "CREATE TABLE \(quote(stem)) (code TEXT, word TEXT, score INTEGER)")
            for row in rows {
                try db.execute(sql: "INSERT INTO \(quote(stem)) (code, word, score) VALUES (?, ?, ?)",
                               arguments: [row.0, row.1, row.2])
            }
        }
        try queue.close()
        return url
    }

    @discardableResult
    private func writeSource(stem: String, rows: Int, in baseURL: URL) throws -> URL {
        let sourceRows = (0..<rows).map { ("c\($0)", "w\($0)", 0) }
        let source = try makeTableDB(stem: stem, rows: sourceRows)
        let destination = SyncPaths.tableFile(baseURL, stem: stem)
        try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        try FileManager.default.copyItem(at: source, to: destination)
        return destination
    }

    private func insertRows(into stem: String, rows: [(String, String, Int)], in db: LimeDB) throws {
        try db.dbQueue.write { sqlDB in
            try sqlDB.execute(sql: """
                CREATE TABLE IF NOT EXISTS \(quote(stem)) (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT, word TEXT, score INTEGER DEFAULT 0,
                    basescore INTEGER DEFAULT 0, code3r TEXT
                )
            """)
            for row in rows {
                try sqlDB.execute(sql: """
                    INSERT INTO \(quote(stem)) (code, word, score)
                    VALUES (?, ?, ?)
                """, arguments: [row.0, row.1, row.2])
            }
        }
    }

    private func writeExportRequest(requestUUID: String, expiresAt: TimeInterval, in baseURL: URL) throws {
        let request = ExportRequest(requestUUID: requestUUID, expiresAt: expiresAt)
        try atomicWrite(try JSONEncoder().encode(request), to: SyncPaths.exportRequest(baseURL))
    }

    private func restoreMeta(in baseURL: URL) throws -> RestoreMeta {
        try JSONDecoder().decode(RestoreMeta.self, from: Data(contentsOf: SyncPaths.restoreMeta(baseURL)))
    }

    private func desiredTableSources(in baseURL: URL) throws -> [URL] {
        let dir = SyncPaths.tablesDir(baseURL)
        guard FileManager.default.fileExists(atPath: dir.path) else { return [] }
        return try FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)
    }

    private func rawCount(_ table: String, whereCode code: String? = nil, in url: URL) throws -> Int {
        let queue = try DatabaseQueue(path: url.path)
        defer { try? queue.close() }
        return try queue.read { db in
            if let code {
                return try Int.fetchOne(db,
                                        sql: "SELECT COUNT(*) FROM \(quote(table)) WHERE code = ?",
                                        arguments: [code]) ?? 0
            }
            return try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(quote(table))") ?? 0
        }
    }

    private func quickCheck(_ url: URL) throws -> String? {
        let queue = try DatabaseQueue(path: url.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try String.fetchOne(db, sql: "PRAGMA quick_check")
        }
    }

    private func countRows(_ stem: String, whereCode code: String, in db: LimeDB) throws -> Int {
        try db.dbQueue.read { sqlDB in
            try Int.fetchOne(sqlDB,
                             sql: "SELECT COUNT(*) FROM \(quote(stem)) WHERE code = ?",
                             arguments: [code]) ?? 0
        }
    }

    private func quote(_ identifier: String) -> String {
        "\"\(identifier.replacingOccurrences(of: "\"", with: "\"\""))\""
    }
}
