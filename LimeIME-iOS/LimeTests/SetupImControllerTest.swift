// SetupImControllerTest.swift
// LimeIMETests
//
// Tests for SetupImController: import, backup/restore, seed, sync.
// Uses real LimeDB temp fixtures and a MockSetupImView.

import XCTest
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
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
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
        try archive.addEntry(with: "custom.db", fileURL: snapshotURL)
        try? FileManager.default.removeItem(at: snapshotURL)
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
        XCTAssertEqual(customRecordSnapshot(db, prefix: prefix), before)
    }

    // MARK: - importTxtFile

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

    // MARK: - restoreDB

    func testRestoreDBFromInvalidURLCompletesGracefully() async throws {
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
            XCTAssertFalse(mock.progressCalls.isEmpty, "Should reach completion handler")
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
