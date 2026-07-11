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

    private func syncMeta(for url: URL) throws -> SyncMetaStore {
        try SyncMetaStore(databaseURL: url)
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

    // MARK: - loadRelated

    func testLoadRelatedEmptyTable() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let mock = await MockManageRelatedView()
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        await MainActor.run { controller.loadRelated(query: nil, page: 0, view: mock) }
        // No positive signal: an empty table yields displayRelatedPhrases([]), leaving
        // displayedPhrases == [] whether or not the load has run. Widened fixed wait.
        try await Task.sleep(nanoseconds: 1_500_000_000)

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
        await waitUntil { mock.refreshCount == 1 }

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

    func testRelatedEditorSavesBumpRevisionAndPublish() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))
        let meta = try syncMeta(for: url)

        let add = await controller.addRelated(parentWord: "i3", childWord: "新增", score: 1)
        guard case .success = add,
              let first = db.getRelated(nil, 10, 0).first(where: { $0.parentWord == "i3" }) else {
            XCTFail("Expected addRelated to succeed")
            return
        }
        XCTAssertEqual(try meta.revision(forTable: "related"), 1)
        XCTAssertEqual(try meta.generation(), 1)

        let update = await controller.updateRelated(id: first.id,
                                                    parentWord: "i3",
                                                    childWord: "更新",
                                                    score: 2)
        guard case .success = update else {
            XCTFail("Expected updateRelated to succeed")
            return
        }
        XCTAssertEqual(try meta.revision(forTable: "related"), 2)
        XCTAssertEqual(try meta.generation(), 2)

        let delete = await controller.deleteRelated(id: first.id)
        guard case .success = delete else {
            XCTFail("Expected deleteRelated to succeed")
            return
        }
        XCTAssertEqual(try meta.revision(forTable: "related"), 3)
        XCTAssertEqual(try meta.generation(), 3)
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

        _ = await controller.addRelated(parentWord: "一", childWord: "二")

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
        await waitUntil { updateMock.refreshCount == 1 }

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

        _ = await controller.addRelated(parentWord: "刪", childWord: "除")

        let phrases = db.getRelated(nil, 10, 0)
        guard let toDelete = phrases.first(where: { $0.parentWord == "刪" }) else {
            XCTFail("Expected to find 刪 phrase")
            return
        }

        let deleteMock = await MockManageRelatedView()
        await MainActor.run { controller.deleteRelated(id: toDelete.id, view: deleteMock) }
        await waitUntil { deleteMock.refreshCount == 1 }

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

        _ = await controller.addRelated(parentWord: "A", childWord: "B")
        _ = await controller.addRelated(parentWord: "C", childWord: "D")

        let mock = await MockManageRelatedView()
        await MainActor.run { controller.loadRelated(query: nil, page: 0, view: mock) }
        await waitUntil { mock.displayedPhrases.count >= 2 }

        await MainActor.run {
            XCTAssertGreaterThanOrEqual(mock.displayedPhrases.count, 2)
        }
    }

    func testLoadRelatedWithQuery() async throws {
        let (url, db) = try makeDB()
        defer { try? FileManager.default.removeItem(at: url) }
        let controller = await LimeIME.ManageRelatedController(dbServer: LimeIME.DBServer(_testDatasource: db))

        _ = await controller.addRelated(parentWord: "搜尋", childWord: "結果")
        _ = await controller.addRelated(parentWord: "其他", childWord: "詞彙")

        let mock = await MockManageRelatedView()
        await MainActor.run { controller.loadRelated(query: "搜", page: 0, view: mock) }
        // Pure absence assertion: query "搜" matches pword exactly (not "搜尋"), so the load
        // yields displayRelatedPhrases([]) — no positive signal to poll. Widened fixed wait.
        try await Task.sleep(nanoseconds: 1_500_000_000)

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
        await waitUntil { threadMock.capturedThread != nil }

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
        await waitUntil { threadMock.capturedThread != nil }

        await MainActor.run {
            if let t = threadMock.capturedThread {
                XCTAssertTrue(t.isMainThread, "displayRelatedPhrases must be on main thread")
            }
        }
    }
}
