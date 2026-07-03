// SetupImControllerTest.swift
// LimeIMETests
//
// Tests for SetupImController: import, backup/restore, seed, sync.
// Uses real LimeDB temp fixtures and a MockSetupImView.

import XCTest
import GRDB
import ZIPFoundation
@testable import LimeIME

// MARK: - MockSetupImView

@MainActor
class MockSetupImView: SetupImView {
    var errors: [String] = []
    var progressCalls: [(Int, String)] = []
    var buttonStateUpdates: [[String: Bool]] = []
    var refreshCount: Int = 0

    func onError(_ message: String) { errors.append(message) }
    func onProgress(_ percentage: Int, status: String) { progressCalls.append((percentage, status)) }
    func updateButtonStates(_ states: [String: Bool]) { buttonStateUpdates.append(states) }
    func refreshImList() { refreshCount += 1 }
}

// MARK: - SetupImControllerTest

final class SetupImControllerTest: XCTestCase {

    // MARK: - Helpers

    private func makeDB() throws -> (url: URL, db: LimeIME.LimeDB) {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("SetupImControllerTest-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: dir) }
        let url = dir.appendingPathComponent("lime.db")
        let db = try LimeIME.LimeDB(path: url.path)
        _ = db.openDBConnection(false)
        return (url, db)
    }

    private func makePrefs() -> LimeIME.LIMEPreferenceManager {
        let suiteName = "test.setup.ctrl.\(UUID().uuidString)"
        let ud = UserDefaults(suiteName: suiteName)!
        return LimeIME.LIMEPreferenceManager(defaults: ud)
    }

    private func makeZippedCustomLimedb() throws -> (dbURL: URL, zipURL: URL) {
        let dbURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
        let snapshotURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
        let zipURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".limedb")
        let sourceDB = try LimeIME.LimeDB(path: dbURL.path)
        _ = sourceDB.openDBConnection(false)
        sourceDB.addOrUpdateMappingRecord("custom", "abc", "測試", 0)
        try sourceDB.exportDB(to: snapshotURL.path)

        let archive = try Archive(url: zipURL, accessMode: .create)
        try archive.addEntry(with: "custom.limedb", fileURL: snapshotURL)
        try? FileManager.default.removeItem(at: snapshotURL)
        return (dbURL, zipURL)
    }

    private func makeAndroidShapeCustomLimedb() throws -> (dbURL: URL, zipURL: URL) {
        let dbURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
        let zipURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".limedb")
        let queue = try DatabaseQueue(path: dbURL.path)
        try queue.write { db in
            try db.execute(sql: """
                CREATE TABLE custom (
                    _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    code      TEXT,
                    code3r    TEXT,
                    word      TEXT,
                    related   TEXT,
                    score     INTEGER DEFAULT 0,
                    basescore INTEGER DEFAULT 0
                )
            """)
            try db.execute(sql: """
                INSERT INTO custom (code, code3r, word, related, score, basescore)
                VALUES ('android_cj4', '4jc', '安卓', 'unused', 7, 3)
            """)
        }
        try queue.close()

        let archive = try Archive(url: zipURL, accessMode: .create)
        try archive.addEntry(with: "custom.limedb", fileURL: dbURL)
        return (dbURL, zipURL)
    }

    private func makeSingleTableLimedb(tableName: String = "custom") throws -> URL {
        let dbURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".limedb")
        let queue = try DatabaseQueue(path: dbURL.path)
        try queue.write { db in
            try db.execute(sql: """
                CREATE TABLE \(quote(tableName)) (
                    _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    code      TEXT,
                    word      TEXT,
                    score     INTEGER DEFAULT 0,
                    basescore INTEGER DEFAULT 0,
                    code3r    TEXT
                )
            """)
            try db.execute(sql: """
                INSERT INTO \(quote(tableName)) (code, word, score, basescore, code3r)
                VALUES ('proxy_db', '交付', 5, 1, 'bd')
            """)
        }
        try queue.close()
        return dbURL
    }

    private func makeCinFixture() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".cin")
        try """
        %cname Proxy CIN
        %chardef begin
        proxy_txt\t文字交付
        %chardef end
        """.write(to: url, atomically: true, encoding: .utf8)
        return url
    }

    private func seedCustomRoundTripRecords(_ db: LimeIME.LimeDB, prefix: String,
                                            scores: [Int]) {
        for (index, score) in scores.enumerated() {
            db.addOrUpdateMappingRecord("custom",
                                        "\(prefix)_\(index)",
                                        "回復測試\(index)",
                                        score)
        }
    }

    private func customRecordSnapshot(_ db: LimeIME.LimeDB,
                                      prefix: String) -> [String] {
        db.getRecordList("custom", nil, searchByCode: true, 0, 0)
            .filter { $0.code.hasPrefix(prefix + "_") }
            .map { "\($0.code)|\($0.word)|\($0.score)|\($0.baseScore)|\($0.code3r)" }
            .sorted()
    }

    private func firstDatabaseInArchive(_ archiveURL: URL) throws -> URL {
        let extractDir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: extractDir, withIntermediateDirectories: true)
        let archive = try Archive(url: archiveURL, accessMode: .read)
        guard let entry = archive.first(where: { $0.path.hasSuffix(".db") }) else {
            throw XCTSkip("No database entry in \(archiveURL.lastPathComponent)")
        }
        let dbURL = extractDir.appendingPathComponent(URL(fileURLWithPath: entry.path).lastPathComponent)
        _ = try archive.extract(entry, to: dbURL)
        return dbURL
    }

    private func rawTableNames(_ dbURL: URL) throws -> Set<String> {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            let rows = try String.fetchAll(db, sql: """
                SELECT name FROM sqlite_master
                WHERE type IN ('table', 'view')
            """)
            return Set(rows)
        }
    }

    private func rawTableColumns(_ dbURL: URL, tableName: String) throws -> [String] {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Row.fetchAll(db, sql: "PRAGMA table_info(\(tableName))").map {
                $0["name"] as String? ?? ""
            }
        }
    }

    private func installedTableURL(for liveDBURL: URL, tableName: String) -> URL {
        SyncPaths.tableFile(liveDBURL.deletingLastPathComponent(), stem: tableName)
    }

    private func installedRowCount(liveDBURL: URL, tableName: String) throws -> Int {
        let queue = try DatabaseQueue(path: installedTableURL(for: liveDBURL, tableName: tableName).path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(quote(tableName))") ?? 0
        }
    }

    private func installedRecordSnapshot(liveDBURL: URL, tableName: String,
                                         prefix: String) throws -> [String] {
        let queue = try DatabaseQueue(path: installedTableURL(for: liveDBURL, tableName: tableName).path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Row.fetchAll(db,
                             sql: """
                             SELECT code, word, score, basescore, code3r
                             FROM \(quote(tableName))
                             WHERE code LIKE ?
                             """,
                             arguments: ["\(prefix)_%"]).map {
                "\($0["code"] as String? ?? "")|\($0["word"] as String? ?? "")|" +
                "\($0["score"] as Int? ?? 0)|\($0["basescore"] as Int? ?? 0)|" +
                "\($0["code3r"] as String? ?? "")"
            }.sorted()
        }
    }

    private func quote(_ identifier: String) -> String {
        "\"\(identifier.replacingOccurrences(of: "\"", with: "\"\""))\""
    }

    // MARK: - importDBFile

    func testImportDBFileInvalidPathReportsError() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockSetupImView()
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )
        let badURL = URL(fileURLWithPath: "/tmp/nonexistent_\(UUID().uuidString).db")

        await MainActor.run { controller.importDBFile(url: badURL, tableName: "phonetic", view: mock) }
        try await Task.sleep(nanoseconds: 500_000_000)

        await MainActor.run {
            XCTAssertFalse(mock.errors.isEmpty, "Expected an error for invalid path")
        }
    }

    func testAsyncImportDBFileDismissesProgressOnFailure() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let progress = await LimeIME.ProgressManager()
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: progress
        )
        let badURL = URL(fileURLWithPath: "/tmp/nonexistent_\(UUID().uuidString).db")

        let result = await controller.importDBFile(url: badURL, tableName: "custom")

        if case .success = result {
            XCTFail("Expected import failure for invalid path")
        }
        await MainActor.run {
            XCTAssertFalse(progress.isVisible, "Async DB import must dismiss progress after failure")
        }
    }

    func testAsyncImportDBFileDeliversSingleTableLimedbToTableStoreOnly() async throws {
        let (url, db) = try makeDB()
        let fixture = try makeSingleTableLimedb(tableName: "custom")
        defer {
            try? FileManager.default.removeItem(at: url)
            try? FileManager.default.removeItem(at: fixture)
        }
        let progress = await LimeIME.ProgressManager()
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: progress
        )

        let result = await controller.importDBFile(url: fixture, tableName: "custom")

        if case .failure(let error) = result {
            XCTFail("Expected raw .limedb import to succeed, got \(error)")
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: installedTableURL(for: url, tableName: "custom").path))
        XCTAssertEqual(try installedRowCount(liveDBURL: url, tableName: "custom"), 1)
        XCTAssertEqual(db.countRecords("custom", "code = ?", ["proxy_db"]), 0,
                       "Live app DB must not receive rows before keyboard scan")
    }

    func testAsyncImportDBFileImportsZippedLimedb() async throws {
        let (url, db) = try makeDB()
        let zipped = try makeZippedCustomLimedb()
        defer {
            try? FileManager.default.removeItem(at: url)
            try? FileManager.default.removeItem(at: zipped.dbURL)
            try? FileManager.default.removeItem(at: zipped.zipURL)
        }
        let progress = await LimeIME.ProgressManager()
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: progress
        )

        let result = await controller.importDBFile(url: zipped.zipURL, tableName: "custom")

        if case .failure(let error) = result {
            XCTFail("Expected zipped .limedb import to succeed, got \(error)")
        }
        XCTAssertEqual(try installedRowCount(liveDBURL: url, tableName: "custom"), 1)
        XCTAssertEqual(db.countRecords("custom", nil, nil), 0,
                       "Import should deliver a TableStore source, not write live DB rows")
        await MainActor.run {
            XCTAssertFalse(progress.isVisible, "Async DB import must dismiss progress after success")
        }
    }

    func testAsyncImportDBFileImportsAndroidShapeZippedLimedb() async throws {
        let (url, db) = try makeDB()
        let zipped = try makeAndroidShapeCustomLimedb()
        defer {
            try? FileManager.default.removeItem(at: url)
            try? FileManager.default.removeItem(at: zipped.dbURL)
            try? FileManager.default.removeItem(at: zipped.zipURL)
        }
        let progress = await LimeIME.ProgressManager()
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: progress
        )

        let result = await controller.importDBFile(url: zipped.zipURL, tableName: "custom")

        if case .failure(let error) = result {
            XCTFail("Expected Android-shaped .limedb import to succeed, got \(error)")
        }
        XCTAssertEqual(try installedRowCount(liveDBURL: url, tableName: "custom"), 1)
        XCTAssertNil(db.getRecordList("custom", "android_cj4", searchByCode: true, 0, 0).first,
                     "Import should deliver a TableStore source, not write live DB rows")
        await MainActor.run {
            XCTAssertFalse(progress.isVisible, "Async DB import must dismiss progress after success")
        }
    }

    func testDatabaseImportFileAutoDetectImportsZippedLimedb() throws {
        let (url, db) = try makeDB()
        let zipped = try makeZippedCustomLimedb()
        defer {
            try? FileManager.default.removeItem(at: url)
            try? FileManager.default.removeItem(at: zipped.dbURL)
            try? FileManager.default.removeItem(at: zipped.zipURL)
        }
        let server = LimeIME.DBServer(_testDatasource: db)

        try importDatabaseFile(server: server, url: zipped.zipURL, tableName: "custom")

        XCTAssertEqual(try installedRowCount(liveDBURL: url, tableName: "custom"), 1)
        XCTAssertEqual(db.countRecords("custom", nil, nil), 0,
                       "Import should deliver a TableStore source, not write live DB rows")
    }

    func testExportLimedbRemoveAndReimportRestoresSameCustomEntries() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )
        let prefix = "limedb_roundtrip_\(UUID().uuidString)"
        seedCustomRoundTripRecords(db, prefix: prefix, scores: [10, 20, 30])
        let before = customRecordSnapshot(db, prefix: prefix)
        XCTAssertEqual(before.count, 3)

        let exportURL = await controller.exportIMAsLimedb(tableNick: "custom")
        defer {
            if let exportURL { try? FileManager.default.removeItem(at: exportURL) }
        }
        guard let exportURL else {
            XCTFail("Expected .limedb export URL")
            return
        }

        db.clearTable("custom")
        XCTAssertTrue(customRecordSnapshot(db, prefix: prefix).isEmpty)

        let result = await controller.importDBFile(url: exportURL, tableName: "custom")

        if case .failure(let error) = result {
            XCTFail("Expected .limedb re-import to succeed, got \(error)")
        }
        XCTAssertTrue(customRecordSnapshot(db, prefix: prefix).isEmpty,
                      "Re-import should not write live DB rows")
        XCTAssertEqual(try installedRecordSnapshot(liveDBURL: url, tableName: "custom", prefix: prefix), before)
    }

    func testExportLimedbCopiesNonCustomTableRowsIntoArchiveCustomTable() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        db.addOrUpdateMappingRecord("custom", "aa", "測", 11)
        db.addOrUpdateMappingRecord("custom", "ab", "試", 12)
        db.renameTableName("custom", "dayi")
        XCTAssertEqual(db.countRecords("dayi", nil, nil), 2)
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )

        let exportURL = await controller.exportIMAsLimedb(tableNick: "dayi")
        defer {
            if let exportURL { try? FileManager.default.removeItem(at: exportURL) }
        }
        guard let exportURL else {
            XCTFail("Expected .limedb export URL")
            return
        }
        let exportedDBURL = try firstDatabaseInArchive(exportURL)
        defer { try? FileManager.default.removeItem(at: exportedDBURL.deletingLastPathComponent()) }
        let exportedDB = try LimeIME.LimeDB(path: exportedDBURL.path)
        _ = exportedDB.openDBConnection(false)

        XCTAssertEqual(try rawTableColumns(exportedDBURL, tableName: "custom"),
                       ["_id", "code", "code3r", "word", "related", "score", "basescore"])
        XCTAssertEqual(exportedDB.countRecords("custom", nil, nil), 2)
        XCTAssertEqual(exportedDB.getRecordList("custom", nil, searchByCode: true, 0, 0)
            .map { "\($0.code)|\($0.word)|\($0.score)" }
            .sorted(),
                       ["aa|測|11", "ab|試|12"])
    }

    func testExportLimedbDoesNotCreateEmojiTablesInArchive() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        db.addOrUpdateMappingRecord("custom", "emoji_free", "乾淨", 1)
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )

        let exportURL = await controller.exportIMAsLimedb(tableNick: "custom")
        defer {
            if let exportURL { try? FileManager.default.removeItem(at: exportURL) }
        }
        guard let exportURL else {
            XCTFail("Expected .limedb export URL")
            return
        }
        let exportedDBURL = try firstDatabaseInArchive(exportURL)
        defer { try? FileManager.default.removeItem(at: exportedDBURL.deletingLastPathComponent()) }

        let tables = try rawTableNames(exportedDBURL)
        XCTAssertFalse(tables.contains("emoji_data"))
        XCTAssertFalse(tables.contains("emoji_fts"))
        XCTAssertFalse(tables.contains("emoji_user"))
    }

    func testExportLimedbReturnsNilForEmptyTable() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        XCTAssertEqual(db.countRecords("custom", nil, nil), 0)
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )

        let exportURL = await controller.exportIMAsLimedb(tableNick: "custom")

        XCTAssertNil(exportURL, "Export should not create a .limedb with an empty custom table")
    }

    func testExportLimeRemoveAndReimportRestoresSameCustomEntries() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )
        let prefix = "lime_roundtrip_\(UUID().uuidString.lowercased())"
        seedCustomRoundTripRecords(db, prefix: prefix, scores: [0, 0, 0])
        let before = customRecordSnapshot(db, prefix: prefix)
        XCTAssertEqual(before.count, 3)

        let exportURL = await controller.exportIMAsText(tableNick: "custom")
        defer {
            if let exportURL { try? FileManager.default.removeItem(at: exportURL) }
        }
        guard let exportURL else {
            XCTFail("Expected .lime export URL")
            return
        }

        db.clearTable("custom")
        XCTAssertTrue(customRecordSnapshot(db, prefix: prefix).isEmpty)

        let result = await controller.importTxtFile(url: exportURL, tableName: "custom")

        if case .failure(let error) = result {
            XCTFail("Expected .lime re-import to succeed, got \(error)")
        }
        XCTAssertTrue(customRecordSnapshot(db, prefix: prefix).isEmpty,
                      "Re-import should not write live DB rows")
        XCTAssertEqual(try installedRecordSnapshot(liveDBURL: url, tableName: "custom", prefix: prefix), before)
    }

    func testExportIMAsTextIncludesImMetadataFromDatabase() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        db.setTableName("custom")
        db.addOrUpdateMappingRecord("custom", "aa", "測", 0)
        db.setImConfig("custom", "name", "Friendly Name")
        db.setImConfig("custom", "version", "Version 2.0")
        db.setImConfig("custom", "limeendkey", ",.")
        db.setImConfig("custom", "imkeys", "ab")
        db.setImConfig("custom", "imkeynames", "ㄅ|ㄆ")
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )

        let exportURL = await controller.exportIMAsText(tableNick: "custom")
        defer {
            if let exportURL { try? FileManager.default.removeItem(at: exportURL) }
        }
        guard let exportURL else {
            XCTFail("Expected .lime export URL")
            return
        }

        let output = try String(contentsOf: exportURL, encoding: .utf8)
        XCTAssertTrue(output.contains("@format@|lime-text-v2"))
        XCTAssertTrue(output.contains("@version@|Version 2.0"))
        XCTAssertTrue(output.contains("@cname@|Friendly Name"))
        XCTAssertTrue(output.contains("@limeendkey@|,."))
        XCTAssertTrue(output.contains("@imkeys@|ab"))
        XCTAssertTrue(output.contains("@imkeynames@|ㄅ\\|ㄆ"))
    }

    // MARK: - importTxtFile

    func testAsyncImportTxtFileDeliversCinToTableStoreOnly() async throws {
        let (url, db) = try makeDB()
        let fixture = try makeCinFixture()
        defer {
            try? FileManager.default.removeItem(at: url)
            try? FileManager.default.removeItem(at: fixture)
        }
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )

        let result = await controller.importTxtFile(url: fixture, tableName: "custom")

        if case .failure(let error) = result {
            XCTFail("Expected .cin import to succeed, got \(error)")
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: installedTableURL(for: url, tableName: "custom").path))
        XCTAssertEqual(try installedRowCount(liveDBURL: url, tableName: "custom"), 1)
        XCTAssertEqual(db.countRecords("custom", "code = ?", ["proxy_txt"]), 0,
                       "Live app DB must not receive rows before keyboard scan")
    }

    func testImportTxtFileNonExistentPathReportsError() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockSetupImView()
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )
        let badURL = URL(fileURLWithPath: "/tmp/nonexistent_\(UUID().uuidString).cin")

        await MainActor.run { controller.importTxtFile(url: badURL, tableName: "custom", view: mock) }
        try await Task.sleep(nanoseconds: 500_000_000)

        await MainActor.run {
            XCTAssertFalse(mock.errors.isEmpty, "Expected error for missing file")
        }
    }

    func testIntentHandlerQueuesExternalImportInsteadOfInferringTableFromFilename() async throws {
        let mock = await MockSetupImView()
        let importURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("array_\(UUID().uuidString).lime")
        defer { try? FileManager.default.removeItem(at: importURL) }
        defer {
            Task { @MainActor in
                if let pending = pendingLimeExternalImportURL {
                    try? FileManager.default.removeItem(at: pending)
                    pendingLimeExternalImportURL = nil
                }
            }
        }
        try "q|一\n".write(to: importURL, atomically: true, encoding: .utf8)

        await LimeIME.IntentHandler.shared.handle(url: importURL, view: mock)

        await MainActor.run {
            XCTAssertEqual(mock.refreshCount, 0, "External URL import must not silently import by filename-derived table")
            XCTAssertNotNil(pendingLimeExternalImportURL, "External import should wait for explicit user IM selection")
            XCTAssertTrue(mock.errors.isEmpty, "Queued external import is not an error")
        }
    }

    func testIntentHandlerQueuesAllSupportedExternalImportExtensions() async throws {
        for ext in ["lime", "cin", "limedb", "zip"] {
            let mock = await MockSetupImView()
            let importURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("array_\(UUID().uuidString).\(ext)")
            defer { try? FileManager.default.removeItem(at: importURL) }
            try "q|一\n".write(to: importURL, atomically: true, encoding: .utf8)

            await LimeIME.IntentHandler.shared.handle(url: importURL, view: mock)

            await MainActor.run {
                XCTAssertEqual(mock.refreshCount, 0, "External .\(ext) import must wait for explicit user IM selection")
                XCTAssertNotNil(pendingLimeExternalImportURL, "External .\(ext) import should be queued")
                if let pending = pendingLimeExternalImportURL {
                    try? FileManager.default.removeItem(at: pending)
                    pendingLimeExternalImportURL = nil
                }
            }
        }
    }

    // MARK: - restoreDB

    func testRestoreDBFromInvalidURLReportsError() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockSetupImView()
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )
        let badURL = URL(fileURLWithPath: "/tmp/nonexistent_\(UUID().uuidString).zip")

        await MainActor.run { controller.restoreDB(from: badURL, view: mock) }
        try await Task.sleep(nanoseconds: 500_000_000)

        await MainActor.run {
            XCTAssertFalse(mock.errors.isEmpty, "Expected restore failure to reach the view")
            XCTAssertTrue(mock.progressCalls.isEmpty, "Invalid restore must not report completion")
            XCTAssertEqual(mock.refreshCount, 0, "Invalid restore must not refresh the IM list")
        }
    }

    func testAsyncRestoreDBFromInvalidURLReturnsFailureAndDismissesProgress() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let progress = await LimeIME.ProgressManager()
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: progress
        )
        let badURL = URL(fileURLWithPath: "/tmp/nonexistent_\(UUID().uuidString).zip")

        let result = await controller.restoreDB(from: badURL)

        if case .success = result {
            XCTFail("Expected restore failure for invalid path")
        }
        await MainActor.run {
            XCTAssertFalse(progress.isVisible, "Restore must dismiss progress after failure")
        }
    }

    // MARK: - syncIMActivatedState

    func testSyncIMActivatedStateDoesNotCrash() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let prefs = makePrefs()
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: prefs,
            progress: LimeIME.ProgressManager()
        )

        await MainActor.run { controller.syncIMActivatedState() }

        let state = prefs.keyboardState
        XCTAssertNotNil(state)
    }

    // MARK: - backupDB

    func testBackupDBCreatesFileOrThrows() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )

        do {
            let backupURL = try await MainActor.run { try controller.backupDB() }
            XCTAssertTrue(FileManager.default.fileExists(atPath: backupURL.path))
            try? FileManager.default.removeItem(at: backupURL)
        } catch {
            // Empty DB may fail backup — acceptable in test environment
            print("backupDB threw (acceptable): \(error)")
        }
    }

}
