// TableSyncEngineTest.swift

import GRDB
import XCTest
@testable import LimeIME

final class TableSyncEngineTest: XCTestCase {
    private func tempDir() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private func makeDatabase(at url: URL,
                              rows: [(code: String, word: String, score: Int)] = [],
                              epoch: String? = nil,
                              generation: Int = 0,
                              revisions: [String: Int] = [:],
                              includeCustomTable: Bool = true) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        let queue = try DatabaseQueue(path: url.path)
        defer { try? queue.close() }
        try queue.write { db in
            try db.execute(sql: "PRAGMA user_version = 104")
            if includeCustomTable {
                try db.execute(sql: """
                    CREATE TABLE custom (
                        _id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code TEXT,
                        word TEXT,
                        score INTEGER DEFAULT 0,
                        basescore INTEGER DEFAULT 0,
                        code3r TEXT
                    )
                    """)
                for row in rows {
                    try db.execute(sql: """
                        INSERT INTO custom (code, word, score) VALUES (?, ?, ?)
                        """, arguments: [row.code, row.word, row.score])
                }
            }
        }
        let meta = try SyncMetaStore(databaseURL: url)
        if let epoch {
            try meta.setValue(epoch, forKey: SyncMetaStore.epochUUIDKey)
        }
        if generation > 0 {
            try meta.setValue(String(generation), forKey: SyncMetaStore.generationKey)
        }
        for (table, revision) in revisions {
            try meta.setValue(String(revision), forKey: "rev:\(table)")
        }
    }

    private func publish(_ liveCold: URL, appGroup: URL) throws {
        try ColdPublisher(liveColdDatabaseURL: liveCold,
                          appGroupBaseURL: appGroup).publish()
    }

    private func customWords(in dbURL: URL) throws -> [String] {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try String.fetchAll(db, sql: "SELECT word FROM custom ORDER BY code")
        }
    }

    private func tableExists(_ table: String, in dbURL: URL) throws -> Bool {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Int.fetchOne(db,
                             sql: "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                             arguments: [table]) ?? 0
        } > 0
    }

    private func insertHotRow(_ row: (code: String, word: String, score: Int), into dbURL: URL) throws {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        try queue.write { db in
            try db.execute(sql: "INSERT INTO custom (code, word, score) VALUES (?, ?, ?)",
                           arguments: [row.code, row.word, row.score])
        }
    }

    private func makeIMDatabase(at url: URL,
                                rows: [[String: String?]] = [],
                                epoch: String = "epoch-a",
                                generation: Int = 1,
                                applied: Bool = false) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        let queue = try DatabaseQueue(path: url.path)
        defer { try? queue.close() }
        try queue.write { db in
            try db.execute(sql: "PRAGMA user_version = 104")
            try db.execute(sql: """
                CREATE TABLE im (
                    _id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    code       TEXT,
                    title      TEXT,
                    desc       TEXT,
                    keyboard   TEXT,
                    disable    BOOLEAN,
                    selkey     TEXT,
                    endkey     TEXT,
                    spacestyle TEXT
                )
                """)
            for row in rows {
                try db.execute(sql: """
                    INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, arguments: [
                        row["code"] ?? nil,
                        row["title"] ?? nil,
                        row["desc"] ?? nil,
                        row["keyboard"] ?? nil,
                        row["disable"] ?? nil,
                        row["selkey"] ?? nil,
                        row["endkey"] ?? nil,
                        row["spacestyle"] ?? nil
                    ])
            }
        }
        let meta = try SyncMetaStore(databaseURL: url)
        try meta.setValue(epoch, forKey: SyncMetaStore.epochUUIDKey)
        try meta.setValue(String(generation), forKey: SyncMetaStore.generationKey)
        if applied {
            try meta.setAppliedEpoch(epoch)
            try meta.setAppliedGeneration(generation)
        }
    }

    private func imRows(in dbURL: URL) throws -> [String] {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Row.fetchAll(db, sql: """
                SELECT code, title, desc, keyboard FROM im ORDER BY code, title
                """).map {
                    "\($0["code"] as String? ?? "")|\($0["title"] as String? ?? "")|\($0["desc"] as String? ?? "")|\($0["keyboard"] as String? ?? "")"
                }
        }
    }

    func testScanAndApplyImportsChangedTableFromPublishedCold() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hot = root.appendingPathComponent("hot/lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: cold,
                         rows: [("a", "冷", 7)],
                         epoch: "epoch-a",
                         revisions: ["custom": 1])
        try makeDatabase(at: hot, epoch: "epoch-a")
        let hotMeta = try SyncMetaStore(databaseURL: hot)
        try hotMeta.setAppliedEpoch("epoch-a")

        try publish(cold, appGroup: appGroup)
        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try customWords(in: hot), ["冷"])
        XCTAssertEqual(try hotMeta.appliedGeneration(), 1)
        XCTAssertEqual(try hotMeta.revision(forTable: "custom"), 1)
    }

    func testEpochDifferenceFullReplacesHotAndStampsAppliedEpoch() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        let hot = hotDir.appendingPathComponent("lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: cold,
                         rows: [("new", "新", 9)],
                         epoch: "epoch-new",
                         revisions: ["custom": 2])
        try makeDatabase(at: hot,
                         rows: [("old", "舊", 1)],
                         epoch: "epoch-old")
        let server = DBServer(_testDatabaseDirectory: hotDir)
        XCTAssertEqual(server.countRecords("custom", "code = 'old'", nil), 1)

        try publish(cold, appGroup: appGroup)
        try TableSyncEngine(appGroupBaseURL: appGroup,
                            hotDatabaseURL: hot,
                            dbServer: server).scanAndApply()

        let hotMeta = try SyncMetaStore(databaseURL: hot)
        XCTAssertEqual(server.countRecords("custom", "code = 'old'", nil), 0)
        XCTAssertEqual(server.countRecords("custom", "code = 'new'", nil), 1)
        XCTAssertEqual(try hotMeta.appliedEpoch(), "epoch-new")
        XCTAssertEqual(try hotMeta.appliedGeneration(), 1)
    }

    func testSameGenerationAndEpochNoOpsEvenWhenRevisionsDiffer() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hot = root.appendingPathComponent("hot/lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: cold,
                         rows: [("cold", "冷", 7)],
                         epoch: "epoch-a",
                         generation: 4,
                         revisions: ["custom": 9])
        try makeDatabase(at: hot,
                         rows: [("hot", "熱", 3)],
                         epoch: "epoch-a",
                         revisions: ["custom": 1])
        let hotMeta = try SyncMetaStore(databaseURL: hot)
        try hotMeta.setAppliedEpoch("epoch-a")
        try hotMeta.setAppliedGeneration(4)
        try SyncDatabaseConnection(databaseURL: cold).writeWithoutTransaction { db in
            try db.execute(sql: "VACUUM INTO ?", arguments: [SyncPaths.coldDB(appGroup).path])
        }

        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try customWords(in: hot), ["熱"])
        XCTAssertEqual(try hotMeta.revision(forTable: "custom"), 1)
    }

    func testSecondScanAfterCompletedApplyIsNoOp() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hot = root.appendingPathComponent("hot/lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: cold,
                         rows: [("a", "冷", 7)],
                         epoch: "epoch-a",
                         revisions: ["custom": 1])
        try makeDatabase(at: hot, epoch: "epoch-a")
        let hotMeta = try SyncMetaStore(databaseURL: hot)
        try hotMeta.setAppliedEpoch("epoch-a")
        try publish(cold, appGroup: appGroup)
        let engine = TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot)

        try engine.scanAndApply()
        try insertHotRow(("local", "本機", 2), into: hot)
        try engine.scanAndApply()

        XCTAssertEqual(try customWords(in: hot), ["冷", "本機"])
    }

    func testIncrementalDropsStemGoneFromCold() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hot = root.appendingPathComponent("hot/lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: cold,
                         epoch: "epoch-a",
                         includeCustomTable: false)
        try makeDatabase(at: hot,
                         rows: [("hot", "熱", 3)],
                         epoch: "epoch-a",
                         revisions: ["custom": 1])
        let hotMeta = try SyncMetaStore(databaseURL: hot)
        try hotMeta.setAppliedEpoch("epoch-a")

        try publish(cold, appGroup: appGroup)
        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertFalse(try tableExists("custom", in: hot))
        XCTAssertEqual(try hotMeta.revision(forTable: "custom"), 0)
    }

    func testScanAndApplyDrainsIMInboxWithoutColdGenerationChange() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hot = root.appendingPathComponent("hot/lime.db")
        let coldSnapshot = SyncPaths.coldDB(appGroup)
        try makeIMDatabase(at: coldSnapshot, rows: [], applied: false)
        try makeIMDatabase(at: hot,
                           rows: [
                               ["code": "old", "title": "keyboard", "desc": "Old", "keyboard": "oldkb"]
                           ],
                           applied: true)
        let inbox = IMInboxFile(records: [
            IMInboxRecord(op: .upsert, row: [
                "code": "custom",
                "title": "keyboard",
                "desc": "New",
                "keyboard": "lime"
            ]),
            IMInboxRecord(op: .delete, row: ["code": "old"])
        ])
        let inboxURL = SyncPaths.imInbox(appGroup)
        try FileManager.default.createDirectory(at: inboxURL.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        try JSONEncoder().encode(inbox).write(to: inboxURL)

        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try imRows(in: hot), ["custom|keyboard|New|lime"])
        XCTAssertTrue(try imRows(in: coldSnapshot).isEmpty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: inboxURL.path))
    }
}
