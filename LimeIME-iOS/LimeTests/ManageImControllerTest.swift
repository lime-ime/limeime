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

// ManageImControllerTest.swift
// LimeIMETests
//
// CRUD + pagination tests for ManageImController.
// Uses a real LimeDB temp fixture and MockManageImView.

import XCTest
import GRDB
@testable import LimeIME

// MARK: - MockManageImView

@MainActor
class MockManageImView: ManageImView {
    var errors: [String] = []
    var progressCalls: [(Int, String)] = []
    var displayedRecords: [LimeIME.LimeRecord] = []
    var recordCount: Int = 0
    var refreshCount: Int = 0

    func onError(_ message: String) { errors.append(message) }
    func onProgress(_ percentage: Int, status: String) { progressCalls.append((percentage, status)) }
    func displayRecords(_ records: [LimeIME.LimeRecord]) { displayedRecords = records }
    func updateRecordCount(_ count: Int) { recordCount = count }
    func refreshRecordList() { refreshCount += 1 }
}

// MARK: - ManageImControllerTest

final class ManageImControllerTest: XCTestCase {

    private let testTable = "custom"
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

    private func rowCount(in db: LimeIME.LimeDB, sql: String) throws -> Int {
        let queue = try DatabaseQueue(path: db.dbPath())
        defer { try? queue.close() }
        return try queue.read { database in
            try Int.fetchOne(database, sql: sql) ?? 0
        }
    }

    // MARK: - loadRecords

    func testLoadRecordsEmptyTable() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageImView()
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.loadRecords(table: testTable, query: nil, searchByCode: true,
                                   page: 0, view: mock)
        }
        try await Task.sleep(nanoseconds: 1_500_000_000)

        await MainActor.run {
            XCTAssertTrue(mock.displayedRecords.isEmpty)
            XCTAssertTrue(mock.errors.isEmpty)
        }
    }

    // MARK: - addRecord

    func testAddRecordSuccess() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageImView()
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.addRecord(table: testTable, code: "abc", word: "æ¸¬è©¦",
                                 score: 5, view: mock)
        }
        await waitUntil { mock.refreshCount == 1 }

        await MainActor.run {
            XCTAssertEqual(mock.refreshCount, 1)
            XCTAssertTrue(mock.errors.isEmpty)
        }
    }

    func testEditorSavesBumpRevisionAndPublish() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let server = LimeIME.DBServer(_testDatasource: db)
        let controller = await LimeIME.ManageImController(dbServer: server)
        let meta = try syncMeta(for: url)

        let add = await controller.addRecord(table: testTable, code: "i3", word: "æ°å¢", score: 1)
        guard case .success = add else {
            XCTFail("Expected add to succeed")
            return
        }
        XCTAssertEqual(try meta.revision(forTable: testTable), 1)
        XCTAssertEqual(try meta.generation(), 0)

        let record = try XCTUnwrap(db.getRecordList(testTable, "i3", searchByCode: true, 10, 0).first)
        let update = await controller.updateRecord(table: testTable, id: record.id,
                                                   code: "i3", word: "æ´æ°", score: 2)
        guard case .success = update else {
            XCTFail("Expected update to succeed")
            return
        }
        XCTAssertEqual(try meta.revision(forTable: testTable), 2)
        XCTAssertEqual(try meta.generation(), 0)

        let delete = await controller.deleteRecord(table: testTable, id: record.id)
        guard case .success = delete else {
            XCTFail("Expected delete to succeed")
            return
        }
        XCTAssertEqual(try meta.revision(forTable: testTable), 3)
        XCTAssertEqual(try meta.generation(), 0)

        try server.publishPendingEditorChanges()
        XCTAssertEqual(try meta.generation(), 1,
                       "record edits publish once when the editor exits or Settings backgrounds")
    }

    func testAddRecordEmptyCodeReportsError() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageImView()
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.addRecord(table: testTable, code: "", word: "æ¸¬è©¦",
                                 score: 0, view: mock)
            XCTAssertFalse(mock.errors.isEmpty)
        }
    }

    func testAddRecordEmptyWordReportsError() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageImView()
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.addRecord(table: testTable, code: "abc", word: "",
                                 score: 0, view: mock)
            XCTAssertFalse(mock.errors.isEmpty)
        }
    }

    // MARK: - updateIMMetadata

    func testUpdateIMMetadataPersistsNameAndVersion() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        let result = await controller.updateIMMetadata(tableNick: testTable,
                                                       name: "Edited Custom",
                                                       version: "Version 2026.05")

        guard case .success = result else {
            XCTFail("Expected metadata update to succeed, got \(result)")
            return
        }
        XCTAssertEqual(db.getImConfig(testTable, "name"), "Edited Custom")
        XCTAssertEqual(db.getImConfig(testTable, "version"), "Version 2026.05")
    }

    func testUpdateIMMetadataRejectsEmptyName() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        let result = await controller.updateIMMetadata(tableNick: testTable,
                                                       name: "   ",
                                                       version: "Version 2026.05")

        guard case .failure = result else {
            XCTFail("Expected empty name validation failure, got \(result)")
            return
        }
        XCTAssertNil(db.getImConfig(testTable, "name"))
        XCTAssertNil(db.getImConfig(testTable, "version"))
    }

    func testUpdateIMMetadataFieldPersistsIndependentVersion() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        let result = await controller.updateIMMetadataField(tableNick: testTable,
                                                            field: "version",
                                                            value: "Independent Version")

        guard case .success = result else {
            XCTFail("Expected metadata field update to succeed, got \(result)")
            return
        }
        XCTAssertEqual(db.getImConfig(testTable, "version"), "Independent Version")
    }

    func testUpdateIMMetadataFieldAllowsBlankLimeEndkey() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        let setResult = await controller.updateIMMetadataField(tableNick: testTable,
                                                               field: "limeendkey",
                                                               value: " ., ")
        guard case .success = setResult else {
            XCTFail("Expected limeendkey update to succeed, got \(setResult)")
            return
        }
        XCTAssertEqual(db.getImConfig(testTable, "limeendkey"), ".,")

        let clearResult = await controller.updateIMMetadataField(tableNick: testTable,
                                                                 field: "limeendkey",
                                                                 value: " ")
        guard case .success = clearResult else {
            XCTFail("Expected blank limeendkey update to succeed, got \(clearResult)")
            return
        }
        XCTAssertEqual(db.getImConfig(testTable, "limeendkey"), "")
    }

    // §1.5: an `im`-metadata edit no longer writes an inbox — it publishes cold (a bare
    // generation bump) so the keyboard's next sync re-mirrors `im`. Verify the edit applied
    // and cold.limedb was published.
    func testIMMetadataEditPublishesCold() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        let result = await controller.updateIMMetadataField(tableNick: testTable,
                                                            field: "version",
                                                            value: "I3")

        guard case .success = result else {
            XCTFail("Expected metadata update to succeed")
            return
        }
        XCTAssertEqual(db.getImConfig(testTable, "version"), "I3")
        XCTAssertTrue(FileManager.default.fileExists(
            atPath: SyncPaths.coldDB(url.deletingLastPathComponent()).path),
            "an im-metadata edit must publish cold.limedb so the keyboard re-mirrors im")
    }

    // MARK: - updateRecord

    func testUpdateRecordAfterAdd() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        _ = await controller.addRecord(table: testTable, code: "xyz", word: "åæ", score: 1)

        let records = db.getRecordList(testTable, nil, searchByCode: true, 10, 0)
        guard let first = records.first else {
            XCTFail("Expected a record after add")
            return
        }

        let updateMock = await MockManageImView()
        await MainActor.run {
            controller.updateRecord(table: testTable, id: first.id,
                                    code: "xyz", word: "æ°æ", score: 2, view: updateMock)
        }
        await waitUntil { updateMock.refreshCount == 1 }

        await MainActor.run {
            XCTAssertEqual(updateMock.refreshCount, 1)
            XCTAssertTrue(updateMock.errors.isEmpty)
        }

        let updated = db.getRecordList(testTable, nil, searchByCode: true, 10, 0)
        XCTAssertTrue(updated.contains { $0.word == "æ°æ" })
    }

    func testUpdateRecordEmptyCodeReportsError() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageImView()
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.updateRecord(table: testTable, id: "1",
                                    code: "", word: "test", score: 0, view: mock)
            XCTAssertFalse(mock.errors.isEmpty)
        }
    }

    // MARK: - deleteRecord

    func testDeleteRecordAfterAdd() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        _ = await controller.addRecord(table: testTable, code: "del", word: "åªé¤", score: 0)

        let records = db.getRecordList(testTable, nil, searchByCode: true, 10, 0)
        guard let toDelete = records.first(where: { $0.word == "åªé¤" }) else {
            XCTFail("Expected to find åªé¤ record")
            return
        }

        let deleteMock = await MockManageImView()
        await MainActor.run {
            controller.deleteRecord(table: testTable, id: toDelete.id, view: deleteMock)
        }
        await waitUntil { deleteMock.refreshCount == 1 }

        await MainActor.run {
            XCTAssertEqual(deleteMock.refreshCount, 1)
        }

        let after = db.getRecordList(testTable, nil, searchByCode: true, 10, 0)
        XCTAssertFalse(after.contains { $0.word == "åªé¤" })
    }

    func testClearTableBumpsRevisionAndPublishes() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        db.addOrUpdateMappingRecord(testTable, "clear_i3", "æ¸é¤", 0)
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))
        let meta = try syncMeta(for: url)

        let result = await controller.clearTable(tableNick: testTable, backupLearning: true)

        guard case .success = result else {
            XCTFail("Expected clearTable to succeed")
            return
        }
        XCTAssertEqual(try meta.revision(forTable: testTable), 1)
        XCTAssertEqual(try meta.generation(), 1)
        let records = try lifecycleRecords(in: db, table: testTable)
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records[0]["tbl"] as String?, testTable)
        XCTAssertEqual(records[0]["action"] as? String, "delete")
        XCTAssertEqual(records[0]["preserve_learning"] as Int?, 1)
    }

    func testAddRecordViewRefreshesOnlyAfterAtomicMutationSucceeds() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let queue = try DatabaseQueue(path: db.dbPath())
        defer { try? queue.close() }
        try await queue.write { database in
            try database.execute(sql: "CREATE TABLE editor_fence (tbl TEXT NOT NULL)")
        }
        let mock = await MockManageImView()
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.addRecord(table: testTable, code: "ui_fail", word: "不可見",
                                 score: 1, view: mock)
        }
        await waitUntil { !mock.errors.isEmpty }

        await MainActor.run {
            XCTAssertEqual(mock.refreshCount, 0)
            XCTAssertFalse(mock.errors.isEmpty)
        }
        XCTAssertEqual(try rowCount(in: db, sql: "SELECT COUNT(*) FROM custom WHERE code = 'ui_fail'"), 0)
    }

    // MARK: - Pagination

    func testPaginationPageSizeConstant() {
        XCTAssertEqual(LimeIME.ManageImController.pageSize, 100)
    }

    func testLoadRecordsReturnsCorrectCount() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        _ = await controller.addRecord(table: testTable, code: "a", word: "ä¸", score: 0)
        _ = await controller.addRecord(table: testTable, code: "b", word: "äº", score: 0)
        _ = await controller.addRecord(table: testTable, code: "c", word: "ä¸", score: 0)

        let mock = await MockManageImView()
        await MainActor.run {
            controller.loadRecords(table: testTable, query: nil, searchByCode: true,
                                   page: 0, view: mock)
        }
        await waitUntil { mock.displayedRecords.count >= 3 }

        await MainActor.run {
            XCTAssertGreaterThanOrEqual(mock.displayedRecords.count, 3)
            XCTAssertGreaterThanOrEqual(mock.recordCount, 3)
        }
    }

    // MARK: - Search

    func testLoadRecordsWithCodeQuery() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        _ = await controller.addRecord(table: testTable, code: "search1", word: "æ¾å°", score: 0)
        _ = await controller.addRecord(table: testTable, code: "other", word: "å¶ä»", score: 0)

        let mock = await MockManageImView()
        await MainActor.run {
            controller.loadRecords(table: testTable, query: "search", searchByCode: true,
                                   page: 0, view: mock)
        }
        await waitUntil { mock.displayedRecords.count >= 1 }

        await MainActor.run {
            XCTAssertTrue(mock.displayedRecords.allSatisfy { $0.code.hasPrefix("search") })
        }
    }

    func testLoadRecordsWordSearch() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        _ = await controller.addRecord(table: testTable, code: "w1", word: "æ¸¬è©¦è©", score: 0)
        _ = await controller.addRecord(table: testTable, code: "w2", word: "å¶ä»", score: 0)

        let mock = await MockManageImView()
        await MainActor.run {
            controller.loadRecords(table: testTable, query: "æ¸¬è©¦", searchByCode: false,
                                   page: 0, view: mock)
        }
        await waitUntil { mock.displayedRecords.count >= 1 }

        await MainActor.run {
            XCTAssertTrue(mock.displayedRecords.allSatisfy { $0.word.contains("æ¸¬è©¦") })
        }
    }

    // MARK: - toggleIMEnabled

    func testToggleIMEnabledDoesNotCrash() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageImView()
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.toggleIMEnabled(imName: "phonetic", enabled: true, view: mock)
        }
        await waitUntil { mock.refreshCount >= 1 }
        // No crash is sufficient; error for missing row is acceptable
    }

    // MARK: - Callbacks on main thread

    func testRefreshCallbackOnMainThread() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }

        class ThreadCaptureMock: MockManageImView {
            var capturedThread: Thread?
            override func refreshRecordList() {
                capturedThread = Thread.current
                super.refreshRecordList()
            }
        }

        let threadMock = await ThreadCaptureMock()
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.addRecord(table: "custom", code: "t", word: "T", score: 0, view: threadMock)
        }
        await waitUntil { threadMock.capturedThread != nil }

        await MainActor.run {
            if let t = threadMock.capturedThread {
                XCTAssertTrue(t.isMainThread, "refreshRecordList must be on main thread")
            }
        }
    }

    func testDisplayRecordsCallbackOnMainThread() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }

        class ThreadCaptureMock: MockManageImView {
            var capturedThread: Thread?
            override func displayRecords(_ records: [LimeIME.LimeRecord]) {
                capturedThread = Thread.current
                super.displayRecords(records)
            }
        }

        let threadMock = await ThreadCaptureMock()
        let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.loadRecords(table: "custom", query: nil, searchByCode: true,
                                   page: 0, view: threadMock)
        }
        await waitUntil { threadMock.capturedThread != nil }

        await MainActor.run {
            if let t = threadMock.capturedThread {
                XCTAssertTrue(t.isMainThread, "displayRecords must be on main thread")
            }
        }
    }
}
