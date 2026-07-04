// ManageRelatedControllerTest.swift
// LimeIMETests
//
// CRUD + pagination tests for ManageRelatedController.
// Uses a real LimeDB temp fixture and MockManageRelatedView.

import XCTest
@testable import LimeIME

// MARK: - MockManageRelatedView

@MainActor
class MockManageRelatedView: ManageRelatedView {
    var errors: [String] = []
    var progressCalls: [(Int, String)] = []
    var displayedPhrases: [LimeIME.Related] = []
    var refreshCount: Int = 0

    func onError(_ message: String) { errors.append(message) }
    func onProgress(_ percentage: Int, status: String) { progressCalls.append((percentage, status)) }
    func displayRelatedPhrases(_ phrases: [LimeIME.Related]) { displayedPhrases = phrases }
    func refreshPhraseList() { refreshCount += 1 }
}

// MARK: - ManageRelatedControllerTest

final class ManageRelatedControllerTest: XCTestCase {

    // MARK: - Helpers

    private func makeDB() throws -> (url: URL, db: LimeIME.LimeDB) {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
        let db = try LimeIME.LimeDB(path: url.path)
        _ = db.openDBConnection(false)
        return (url, db)
    }

    private func makeDBDirectory() throws -> (dir: URL, db: LimeIME.LimeDB) {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("manage-related-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let db = try LimeIME.LimeDB(path: dir.appendingPathComponent("lime.db").path)
        _ = db.openDBConnection(false)
        return (dir, db)
    }

    private func readColdMeta(in dir: URL) throws -> LimeIME.ColdSnapshotMeta {
        try JSONDecoder().decode(LimeIME.ColdSnapshotMeta.self,
                                 from: Data(contentsOf: LimeIME.SyncPaths.coldMeta(dir)))
    }

    // MARK: - loadRelated

    func testLoadRelatedEmptyTable() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageRelatedView()
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run { controller.loadRelated(query: nil, page: 0, view: mock) }
        try await Task.sleep(nanoseconds: 300_000_000)

        await MainActor.run {
            XCTAssertTrue(mock.displayedPhrases.isEmpty)
            XCTAssertTrue(mock.errors.isEmpty)
        }
    }

    // MARK: - addRelated

    func testAddRelatedSuccess() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageRelatedView()
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run { controller.addRelated(parentWord: "你好", childWord: "世界", view: mock) }
        try await Task.sleep(nanoseconds: 300_000_000)

        await MainActor.run {
            XCTAssertEqual(mock.refreshCount, 1)
            XCTAssertTrue(mock.errors.isEmpty)
        }
    }

    func testAddRelatedPersistsScore() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        let result = await controller.addRelated(parentWord: "分數", childWord: "新增", score: 42)

        guard case .success = result else {
            XCTFail("Expected addRelated to succeed")
            return
        }
        let phrases = db.getRelated(nil, 10, 0)
        XCTAssertTrue(phrases.contains {
            $0.parentWord == "分數" && $0.childWord == "新增" && $0.score == 42
        })
    }

    func testAddRelatedEmptyParentReportsError() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageRelatedView()
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.addRelated(parentWord: "", childWord: "world", view: mock)
            XCTAssertFalse(mock.errors.isEmpty)
        }
    }

    func testAddRelatedEmptyChildReportsError() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageRelatedView()
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.addRelated(parentWord: "hello", childWord: "", view: mock)
            XCTAssertFalse(mock.errors.isEmpty)
        }
    }

    // MARK: - updateRelated

    func testUpdateRelatedAfterAdd() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run { controller.addRelated(parentWord: "一", childWord: "二", view: nil) }
        try await Task.sleep(nanoseconds: 300_000_000)

        let phrases = db.getRelated(nil, 10, 0)
        guard let first = phrases.first else {
            XCTFail("Expected a related phrase after add")
            return
        }

        let updateMock = await MockManageRelatedView()
        await MainActor.run {
            controller.updateRelated(id: first.id, parentWord: "一", childWord: "三",
                                     view: updateMock)
        }
        try await Task.sleep(nanoseconds: 300_000_000)

        await MainActor.run {
            XCTAssertEqual(updateMock.refreshCount, 1)
            XCTAssertTrue(updateMock.errors.isEmpty)
        }

        let updated = db.getRelated(nil, 10, 0)
        XCTAssertTrue(updated.contains { $0.childWord == "三" })
    }

    func testUpdateRelatedPersistsScore() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        let addResult = await controller.addRelated(parentWord: "分數", childWord: "編輯", score: 7)
        guard case .success = addResult,
              let first = db.getRelated(nil, 10, 0).first(where: {
                  $0.parentWord == "分數" && $0.childWord == "編輯"
              }) else {
            XCTFail("Expected seeded related phrase")
            return
        }

        let updateResult = await controller.updateRelated(id: first.id,
                                                          parentWord: "分數",
                                                          childWord: "更新",
                                                          score: 88)

        guard case .success = updateResult else {
            XCTFail("Expected updateRelated to succeed")
            return
        }
        let updated = db.getRelated(nil, 10, 0)
        XCTAssertTrue(updated.contains {
            $0.parentWord == "分數" && $0.childWord == "更新" && $0.score == 88
        })
    }

    func testUpdateRelatedPublishesReplaceModeSnapshot() async throws {
        let (dir, db) = try makeDBDirectory()
        defer {
            try? db.closeForReplacement()
            try? FileManager.default.removeItem(at: dir)
        }
        let server = LimeIME.DBServer(_testDatasource: db)
        let controller = await LimeIME.ManageRelatedController(dbServer: server)
        let rowID = db.addRecord("related",
                                 ["pword": "分數", "cword": "編輯",
                                  "basescore": 0, "score": 7])
        XCTAssertGreaterThan(rowID, 0)
        let firstMeta = try server.publishColdSnapshot()

        let result = await controller.updateRelated(id: rowID,
                                                    parentWord: "分數",
                                                    childWord: "更新",
                                                    score: 88)

        guard case .success = result else {
            XCTFail("Expected updateRelated to succeed, got \(result)")
            return
        }
        let rev = try XCTUnwrap(db.syncRevs()["related"])
        XCTAssertEqual(rev.mode, .replace)
        XCTAssertEqual(try readColdMeta(in: dir).generation, firstMeta.generation + 1)
        XCTAssertTrue(FileManager.default.fileExists(atPath: LimeIME.SyncPaths.coldDB(dir).path))
    }

    func testUpdateRelatedEmptyWordReportsError() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageRelatedView()
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.updateRelated(id: 1, parentWord: "", childWord: "三", view: mock)
            XCTAssertFalse(mock.errors.isEmpty)
        }
    }

    // MARK: - deleteRelated

    func testDeleteRelatedAfterAdd() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run { controller.addRelated(parentWord: "刪", childWord: "除", view: nil) }
        try await Task.sleep(nanoseconds: 300_000_000)

        let phrases = db.getRelated(nil, 10, 0)
        guard let toDelete = phrases.first(where: { $0.parentWord == "刪" }) else {
            XCTFail("Expected to find 刪 phrase")
            return
        }

        let deleteMock = await MockManageRelatedView()
        await MainActor.run { controller.deleteRelated(id: toDelete.id, view: deleteMock) }
        try await Task.sleep(nanoseconds: 300_000_000)

        await MainActor.run { XCTAssertEqual(deleteMock.refreshCount, 1) }

        let after = db.getRelated(nil, 10, 0)
        XCTAssertFalse(after.contains { $0.parentWord == "刪" })
    }

    // MARK: - Pagination

    func testPaginationPageSizeConstant() {
        XCTAssertEqual(LimeIME.ManageRelatedController.pageSize, 100)
    }

    func testLoadRelatedAfterMultipleAdds() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.addRelated(parentWord: "A", childWord: "B", view: nil)
            controller.addRelated(parentWord: "C", childWord: "D", view: nil)
        }
        try await Task.sleep(nanoseconds: 300_000_000)

        let mock = await MockManageRelatedView()
        await MainActor.run { controller.loadRelated(query: nil, page: 0, view: mock) }
        try await Task.sleep(nanoseconds: 300_000_000)

        await MainActor.run {
            XCTAssertGreaterThanOrEqual(mock.displayedPhrases.count, 2)
        }
    }

    func testLoadRelatedWithQuery() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run {
            controller.addRelated(parentWord: "搜尋", childWord: "結果", view: nil)
            controller.addRelated(parentWord: "其他", childWord: "詞彙", view: nil)
        }
        try await Task.sleep(nanoseconds: 300_000_000)

        let mock = await MockManageRelatedView()
        await MainActor.run { controller.loadRelated(query: "搜", page: 0, view: mock) }
        try await Task.sleep(nanoseconds: 300_000_000)

        await MainActor.run {
            XCTAssertTrue(mock.errors.isEmpty)
        }
    }

    // MARK: - Callbacks on main thread

    func testRefreshPhraseListOnMainThread() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }

        class ThreadCaptureMock: MockManageRelatedView {
            var capturedThread: Thread?
            override func refreshPhraseList() {
                capturedThread = Thread.current
                super.refreshPhraseList()
            }
        }

        let threadMock = await ThreadCaptureMock()
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run { controller.addRelated(parentWord: "主線", childWord: "回呼", view: threadMock) }
        try await Task.sleep(nanoseconds: 400_000_000)

        await MainActor.run {
            if let t = threadMock.capturedThread {
                XCTAssertTrue(t.isMainThread, "refreshPhraseList must be called on main thread")
            }
        }
    }

    func testDisplayRelatedPhrasesOnMainThread() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }

        class ThreadCaptureMock: MockManageRelatedView {
            var capturedThread: Thread?
            override func displayRelatedPhrases(_ phrases: [LimeIME.Related]) {
                capturedThread = Thread.current
                super.displayRelatedPhrases(phrases)
            }
        }

        let threadMock = await ThreadCaptureMock()
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run { controller.loadRelated(query: nil, page: 0, view: threadMock) }
        try await Task.sleep(nanoseconds: 400_000_000)

        await MainActor.run {
            if let t = threadMock.capturedThread {
                XCTAssertTrue(t.isMainThread, "displayRelatedPhrases must be on main thread")
            }
        }
    }
}
