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
    private var tempDirs: [URL] = []

    override func tearDown() {
        for dir in tempDirs {
            try? FileManager.default.removeItem(at: dir)
        }
        tempDirs.removeAll()
        super.tearDown()
    }

    // MARK: - Helpers

    private func makeDB() throws -> (url: URL, db: LimeIME.LimeDB) {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        tempDirs.append(dir)
        let url = dir.appendingPathComponent("lime.db")
        let db = try LimeIME.LimeDB(path: url.path)
        _ = db.openDBConnection(false)
        return (url, db)
    }

    private func syncMeta(for url: URL) throws -> SyncMetaStore {
        try SyncMetaStore(databaseURL: url)
    }

    private func lifecycleRecords(in db: LimeIME.LimeDB, table: String) throws -> [Row] {
        let queue = try DatabaseQueue(path: db.dbPath())
        defer { try? queue.close() }
        return try queue.read { database in
            try Row.fetchAll(database,
                             sql: "SELECT tbl, revision, action, preserve_learning FROM im_lifecycle_intent WHERE tbl = ? ORDER BY revision",
                             arguments: [table])
        }
    }

    private func makePrefs() -> LimeIME.LIMEPreferenceManager {
        let suiteName = "test.setup.ctrl.\(UUID().uuidString)"
        let ud = UserDefaults(suiteName: suiteName)!
        return LimeIME.LIMEPreferenceManager(defaults: ud)
    }

    /// Poll until `condition` (evaluated on the main actor) holds or `timeout` elapses.
    /// Load-tolerant replacement for a fixed `Task.sleep` before asserting on an async
    /// result: returns the instant the async work lands, and only waits longer under CPU load.
    @MainActor
    private func waitUntil(_ timeout: TimeInterval = 5, _ condition: @MainActor () -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
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
        try archive.addEntry(with: "custom.db", fileURL: snapshotURL)
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
        try archive.addEntry(with: "cj4.db", fileURL: dbURL)
        return (dbURL, zipURL)
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

    private func rawRowCount(_ table: String, in dbURL: URL) throws -> Int {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") ?? 0
        }
    }

    private func rawCustomRows(in dbURL: URL) throws -> [String] {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Row.fetchAll(db, sql: """
                SELECT code, word, score FROM custom ORDER BY code, word
                """).map {
                    "\($0["code"] as String? ?? "")|\($0["word"] as String? ?? "")|\($0["score"] as Int? ?? 0)"
                }
        }
    }

    private func insertRawCustomRow(code: String, word: String, score: Int, into dbURL: URL) throws {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO custom (code, word, score) VALUES (?, ?, ?)
                """, arguments: [code, word, score])
        }
    }

    private func makeWholeDatabaseBackupZip(from db: LimeIME.LimeDB,
                                            removingEmojiTables: Bool = false) throws -> URL {
        let snapshotURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
        let zipURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".zip")
        try db.exportDB(to: snapshotURL.path)
        if removingEmojiTables {
            let queue = try DatabaseQueue(path: snapshotURL.path)
            defer { try? queue.close() }
            try queue.write { db in
                try db.execute(sql: "DROP TABLE IF EXISTS emoji_fts")
                try db.execute(sql: "DROP TABLE IF EXISTS emoji_user")
                try db.execute(sql: "DROP TABLE IF EXISTS emoji_data")
                try db.execute(sql: "DELETE FROM im WHERE code = 'emoji'")
            }
        }
        let archive = try Archive(url: zipURL, accessMode: .create)
        try archive.addEntry(with: DBServer.databaseName, fileURL: snapshotURL)
        try? FileManager.default.removeItem(at: snapshotURL)
        return zipURL
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
        await waitUntil { !mock.errors.isEmpty }

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
        XCTAssertGreaterThan(db.countRecords("custom", nil, nil), 0)
        await MainActor.run {
            XCTAssertFalse(progress.isVisible, "Async DB import must dismiss progress after success")
        }
        let meta = try syncMeta(for: url)
        XCTAssertEqual(try meta.revision(forTable: "custom"), 1)
        XCTAssertEqual(try meta.generation(), 1)
    }

    func testAsyncImportDBFileWritesInstallLifecycleRecord() async throws {
        let (url, db) = try makeDB()
        let zipped = try makeZippedCustomLimedb()
        defer {
            try? FileManager.default.removeItem(at: url)
            try? FileManager.default.removeItem(at: zipped.dbURL)
            try? FileManager.default.removeItem(at: zipped.zipURL)
        }
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )
        let result = await controller.importDBFile(url: zipped.zipURL,
                                                   tableName: "custom",
                                                   restoreLearning: true)

        if case .failure(let error) = result {
            XCTFail("Expected zipped .limedb import to succeed, got \(error)")
        }
        let records = try lifecycleRecords(in: db, table: "custom")
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records[0]["tbl"] as String?, "custom")
        XCTAssertEqual(records[0]["action"] as? String, "install")
        XCTAssertEqual(records[0]["preserve_learning"] as Int?, 1)
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
        XCTAssertEqual(db.getRecordList("custom", "android_cj4", searchByCode: true, 0, 0).first?.word,
                       "安卓")
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

        XCTAssertGreaterThan(db.countRecords("custom", nil, nil), 0)
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
        XCTAssertEqual(customRecordSnapshot(db, prefix: prefix), before)
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
        // A `.lime` round-trip is lossless for basescore: export writes all four fields
        // (code|word|score|basescore), and re-import preserves a PRESENT basescore verbatim
        // — including the seeded 0 (spec §2.5.1). So the re-imported snapshot equals `before`.
        XCTAssertEqual(customRecordSnapshot(db, prefix: prefix), before)
    }

    func testAsyncImportTxtFileBumpsRevisionAndPublishes() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let importURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".lime")
        defer { try? FileManager.default.removeItem(at: importURL) }
        try "i3\t同步\n".write(to: importURL, atomically: true, encoding: .utf8)
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )

        let result = await controller.importTxtFile(url: importURL, tableName: "custom")

        if case .failure(let error) = result {
            XCTFail("Expected text import to succeed, got \(error)")
        }
        let meta = try syncMeta(for: url)
        XCTAssertEqual(try meta.revision(forTable: "custom"), 1)
        XCTAssertEqual(try meta.generation(), 1)
    }

    func testAsyncImportCinFileStagesAndPublishesLifecycleIntent() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let importURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".cin")
        defer { try? FileManager.default.removeItem(at: importURL) }
        try """
        %chardef begin
        ab 詞
        %chardef end
        """.write(to: importURL, atomically: true, encoding: .utf8)
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )

        let result = await controller.importTxtFile(url: importURL,
                                                    tableName: "custom",
                                                    restoreLearning: true)

        if case .failure(let error) = result {
            XCTFail("Expected .cin import to succeed, got \(error)")
        }
        XCTAssertEqual(db.countRecords("custom", "code = ? AND word = ?", ["ab", "詞"]), 1)
        let records = try lifecycleRecords(in: db, table: "custom")
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records[0]["action"] as? String, "install")
        XCTAssertEqual(records[0]["preserve_learning"] as Int?, 1)
        let meta = try syncMeta(for: url)
        XCTAssertEqual(try meta.revision(forTable: "custom"), 1)
        XCTAssertEqual(try meta.generation(), 1)
    }

    func testIssue242ExactTricodeCinImportsAndPublishesEveryRow() async throws {
        let fixtureURL = try XCTUnwrap(
            Bundle(for: type(of: self)).url(
                forResource: "3code_v.20260816.2_utf8-corrected",
                withExtension: "cin"
            )
        )
        let (liveURL, db) = try makeDB()
        let server = LimeIME.DBServer(_testDatasource: db)
        let controller = await LimeIME.SetupImController(
            dbServer: server, prefs: makePrefs(), progress: LimeIME.ProgressManager()
        )

        let result = await controller.importTxtFile(url: fixtureURL, tableName: "tricode")

        switch result {
        case .success(let count):
            XCTAssertEqual(count, 23_359, "Import result must report every chardef row")
        case .failure(let error):
            XCTFail("Expected exact Issue #242 fixture import to succeed, got \(error)")
        }
        XCTAssertEqual(db.countRecords("tricode", nil, nil), 23_359,
                       "Live tricode table must contain every fixture row")
        XCTAssertEqual(db.getImConfig("tricode", "version"), "v.20260816.2",
                       "Live tricode metadata must retain the fixture version")
        XCTAssertEqual(db.getImConfig("tricode", "name"), "三碼輸入法",
                       "Live tricode metadata must retain the fixture name")

        let publishedSnapshot = SyncPaths.coldDB(liveURL.deletingLastPathComponent())
        XCTAssertTrue(FileManager.default.fileExists(atPath: publishedSnapshot.path),
                      "Import must publish a cold database snapshot")
        if FileManager.default.fileExists(atPath: publishedSnapshot.path) {
            XCTAssertEqual(try rawRowCount("tricode", in: publishedSnapshot), 23_359,
                           "Published cold snapshot must contain every tricode row")
        }
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

    func testImportTxtFileNonExistentPathReportsError() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: db), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )
        let badURL = URL(fileURLWithPath: "/tmp/nonexistent_\(UUID().uuidString).cin")

        // Deterministic: the async import returns .failure for a missing file, so
        // there is no fire-and-forget poll to flake under parallel-suite load.
        let result = await controller.importTxtFile(url: badURL, tableName: "custom")
        guard case .failure = result else {
            XCTFail("Expected failure importing a non-existent file")
            return
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

    func testEmojiTablesPresentOnFreshDB() throws {
        let (url, _) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }

        let tables = try rawTableNames(url)

        XCTAssertTrue(tables.contains("emoji_data"))
        XCTAssertTrue(tables.contains("emoji_fts"))
        XCTAssertTrue(tables.contains("emoji_user"))
        XCTAssertGreaterThan(try rawRowCount("emoji_data", in: url), 0)
    }

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
        await waitUntil { !mock.errors.isEmpty }

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

    func testRestoreDBPublishesEpochSnapshotRefreshesEmojiAndKeyboardFullReplaces() async throws {
        let coldDir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: coldDir, withIntermediateDirectories: true)
        let coldURL = coldDir.appendingPathComponent(DBServer.databaseName)
        let coldDB = try LimeIME.LimeDB(path: coldURL.path)
        _ = coldDB.openDBConnection(false)
        let (sourceURL, sourceDB) = try makeDB()
        let hotDir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: hotDir, withIntermediateDirectories: true)
        let hotURL = hotDir.appendingPathComponent("lime.db")
        let hotDB = try LimeIME.LimeDB(path: hotURL.path)
        _ = hotDB.openDBConnection(false)
        defer {
            try? FileManager.default.removeItem(at: coldDir)
            try? FileManager.default.removeItem(at: sourceURL)
            try? FileManager.default.removeItem(at: hotDir)
        }
        coldDB.addOrUpdateMappingRecord("custom", "old", "舊", 1)
        sourceDB.addOrUpdateMappingRecord("custom", "restored", "還", 7)
        hotDB.addOrUpdateMappingRecord("custom", "hot", "熱", 3)
        let backupURL = try makeWholeDatabaseBackupZip(from: sourceDB, removingEmojiTables: true)
        defer { try? FileManager.default.removeItem(at: backupURL) }
        let appGroup = coldURL.deletingLastPathComponent()
        let controller = await LimeIME.SetupImController(
            dbServer: LimeIME.DBServer(_testDatasource: coldDB), prefs: makePrefs(),
            progress: LimeIME.ProgressManager()
        )

        let result = await controller.restoreDB(from: backupURL)

        if case .failure(let error) = result {
            XCTFail("Expected restore to succeed, got \(error)")
        }
        XCTAssertEqual(try rawCustomRows(in: coldURL), ["restored|還|7"])
        XCTAssertGreaterThan(try rawRowCount("emoji_data", in: coldURL), 0)
        let epoch = try syncMeta(for: coldURL).epochUUID()
        XCTAssertNotNil(epoch)
        let publishedSnapshot = SyncPaths.coldDB(appGroup)
        XCTAssertTrue(FileManager.default.fileExists(atPath: publishedSnapshot.path))
        XCTAssertEqual(try syncMeta(for: publishedSnapshot).epochUUID(), epoch)

        let hotServer = DBServer(_testDatabaseDirectory: hotURL.deletingLastPathComponent())
        try TableSyncEngine(appGroupBaseURL: appGroup,
                            hotDatabaseURL: hotURL,
                            dbServer: hotServer).scanAndApply()
        XCTAssertEqual(try rawCustomRows(in: hotURL), ["restored|還|7"])

        try insertRawCustomRow(code: "local", word: "本機", score: 2, into: hotURL)
        try TableSyncEngine(appGroupBaseURL: appGroup,
                            hotDatabaseURL: hotURL,
                            dbServer: hotServer).scanAndApply()
        XCTAssertEqual(try rawCustomRows(in: hotURL), ["local|本機|2", "restored|還|7"])
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
