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

    private var testSuiteNames: [String] = []

    override func tearDown() {
        for name in testSuiteNames { UserDefaults().removePersistentDomain(forName: name) }
        testSuiteNames = []
        super.tearDown()
    }

    /// A fresh UserDefaults suite for the im-inbox seq counter + consume cursor, so the
    /// seq-guarded drain is deterministic and isolated between tests.
    private func isolatedDefaults() throws -> UserDefaults {
        let name = "test.iminbox.\(UUID().uuidString)"
        testSuiteNames.append(name)
        return try XCTUnwrap(UserDefaults(suiteName: name))
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

    private func customRows(in dbURL: URL) throws -> [String] {
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

    private func customBackupRows(in dbURL: URL) throws -> [String] {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Row.fetchAll(db, sql: """
                SELECT code, word, score FROM custom_user ORDER BY code, word
                """).map {
                    "\($0["code"] as String? ?? "")|\($0["word"] as String? ?? "")|\($0["score"] as Int? ?? 0)"
                }
        }
    }

    private func lifecycleInboxURL(_ appGroup: URL) -> URL {
        appGroup.appendingPathComponent("inbox", isDirectory: true)
            .appendingPathComponent("lifecycle.json")
    }

    private func writeLifecycleRecords(appGroup: URL, _ records: [[String: Any]]) throws {
        let data = try JSONSerialization.data(withJSONObject: records)
        try atomicWrite(data, to: lifecycleInboxURL(appGroup))
    }

    private func createCustomUserBackup(in dbURL: URL) throws {
        let db = try LimeDB(path: dbURL.path)
        db.backupUserRecords("custom")
        db.clearTable("custom")
    }

    private func customRowID(code: String, word: String, in dbURL: URL) throws -> Int64? {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Int64.fetchOne(db,
                               sql: "SELECT _id FROM custom WHERE code = ? AND word = ?",
                               arguments: [code, word])
        }
    }

    private func editCustomRows(in dbURL: URL, _ body: (Database) throws -> Void) throws {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        try queue.write(body)
    }

    private func writeEditorRefreshRequest(appGroup: URL,
                                           table: String,
                                           requestUUID: String = UUID().uuidString) throws {
        let request = EditorRefreshRequest(requestUUID: requestUUID,
                                           table: table,
                                           expiresAt: Date().addingTimeInterval(60).timeIntervalSince1970)
        try atomicWrite(try JSONEncoder().encode(request),
                        to: SyncPaths.editorRefreshRequest(appGroup))
    }

    private func writeExportRequest(appGroup: URL,
                                    requestUUID: String = UUID().uuidString) throws {
        let request = ExportRequest(requestUUID: requestUUID,
                                    expiresAt: Date().addingTimeInterval(60).timeIntervalSince1970)
        try atomicWrite(try JSONEncoder().encode(request),
                        to: SyncPaths.exportRequest(appGroup))
    }

    private func editorRefreshReceipt(appGroup: URL) throws -> EditorRefreshReceipt {
        let data = try Data(contentsOf: SyncPaths.editorRefreshReceipt(appGroup))
        return try JSONDecoder().decode(EditorRefreshReceipt.self, from: data)
    }

    private func exportReceipt(appGroup: URL) throws -> ExportReceipt {
        let data = try Data(contentsOf: SyncPaths.receipt(appGroup))
        return try JSONDecoder().decode(ExportReceipt.self, from: data)
    }

    private func makeRelatedDatabase(at url: URL,
                                     rows: [(pword: String, cword: String, score: Int)]) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        let queue = try DatabaseQueue(path: url.path)
        defer { try? queue.close() }
        try queue.write { db in
            try db.execute(sql: """
                CREATE TABLE related (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pword TEXT,
                    cword TEXT,
                    score INTEGER DEFAULT 0,
                    basescore INTEGER DEFAULT 0
                )
                """)
            for row in rows {
                try db.execute(sql: """
                    INSERT INTO related (pword, cword, score) VALUES (?, ?, ?)
                    """, arguments: [row.pword, row.cword, row.score])
            }
        }
        _ = try SyncMetaStore(databaseURL: url)
    }

    private func relatedRows(in dbURL: URL) throws -> [String] {
        let queue = try DatabaseQueue(path: dbURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            try Row.fetchAll(db, sql: """
                SELECT pword, cword, score FROM related ORDER BY pword, cword
                """).map {
                    "\($0["pword"] as String? ?? "")|\($0["cword"] as String? ?? "")|\($0["score"] as Int? ?? 0)"
                }
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
        try hotMeta.setValue("epoch-a", forKey: SyncMetaStore.epochUUIDKey)

        try publish(cold, appGroup: appGroup)
        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try customWords(in: hot), ["冷"])
        XCTAssertEqual(try hotMeta.appliedGeneration(), 1)
        XCTAssertEqual(try hotMeta.revision(forTable: "custom"), 1)
    }

    func testEpochDifferenceFullReplacesHotAndHotCarriesColdEpoch() throws {
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
        XCTAssertEqual(try hotMeta.epochUUID(), "epoch-new",
                       "the whole-file swap carries cold's epoch into hot — no separate stamp")
        XCTAssertEqual(try hotMeta.appliedGeneration(), 1)
    }

    /// §1.2 self-marking apply: once hot carries cold's `epoch_uuid` (a completed restore
    /// copy), the next scan must NOT full-replace — hot's own epoch IS the applied marker,
    /// so there is no re-copy. A hot-only sentinel proves it: a full replace would wipe it.
    /// This is the old "interrupted probe → redundant 3-5s re-copy on the next appearance"
    /// bug, now structurally impossible.
    func testHotAlreadyOnColdEpochIsNotReplaced() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        let hot = hotDir.appendingPathComponent("lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: cold, rows: [("new", "新", 9)], epoch: "epoch-new")
        // Hot already carries cold's epoch (the swap that applied the restore brought it in),
        // plus a hot-only "sentinel" row that cold does not have.
        try makeDatabase(at: hot,
                         rows: [("new", "新", 9), ("sentinel", "哨", 5)],
                         epoch: "epoch-new")
        let server = DBServer(_testDatabaseDirectory: hotDir)
        try publish(cold, appGroup: appGroup)

        _ = try TableSyncEngine(appGroupBaseURL: appGroup,
                                hotDatabaseURL: hot,
                                dbServer: server).scanAndApply()

        // hot.epoch_uuid == cold.epoch_uuid → no full replace → the hot-only sentinel survives.
        XCTAssertEqual(server.countRecords("custom", "code = 'sentinel'", nil), 1,
                       "hot already on cold's epoch → no re-copy → hot-only row survives")
        XCTAssertEqual(server.countRecords("custom", "code = 'new'", nil), 1)
    }

    /// End-to-end guard for the FA-off "install two IMs → nothing active on the keyboard"
    /// failure. Drives the REAL cross-process chain the engine-only tests never covered:
    /// app installs two enabled IMs into cold → publishes → keyboard syncs cold→hot →
    /// keyboard resolves its active IM list from `keyboard_state`. The app writes
    /// `keyboard_state` as tableNicks (syncIMActivatedState format); the keyboard must
    /// resolve them, not treat them as numeric row offsets.
    func testInstalledIMsResolveAsActiveOnKeyboardAfterSync() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        let cold = appGroup.appendingPathComponent("lime.db")
        let hot = hotDir.appendingPathComponent("lime.db")

        // Cold (app side): two IMs installed + enabled, as registerIM leaves them
        // (seed row title = display label, no title="disable" row → enabled).
        try makeIMDatabase(at: cold, rows: [
            ["code": "cj",   "title": "倉頡", "desc": "", "keyboard": "lime"],
            ["code": "dayi", "title": "大易", "desc": "", "keyboard": "lime"]
        ], epoch: "epoch-install", generation: 1)
        // Hot (keyboard side): fresh, no IMs, no applied epoch.
        try makeIMDatabase(at: hot, rows: [], epoch: "epoch-hot-initial", generation: 0)

        let server = DBServer(_testDatabaseDirectory: hotDir)

        // App publishes; keyboard syncs (epoch differs → wholesale replace hot from cold).
        try publish(cold, appGroup: appGroup)
        try TableSyncEngine(appGroupBaseURL: appGroup,
                            hotDatabaseURL: hot,
                            dbServer: server).scanAndApply()

        // The im rows DID reach hot (this part the old tests already proved).
        let rows = try imRows(in: hot)
        XCTAssertTrue(rows.contains { $0.hasPrefix("cj|") }, "hot should hold cj after sync; got \(rows)")
        XCTAssertTrue(rows.contains { $0.hasPrefix("dayi|") }, "hot should hold dayi after sync; got \(rows)")

        // App side records the enabled list as tableNicks (syncIMActivatedState format).
        let suite = UserDefaults(suiteName: LIMEPreferenceManager.suiteName)!
        let savedState = suite.string(forKey: "keyboard_state")
        defer {
            if let savedState { suite.set(savedState, forKey: "keyboard_state") }
            else { suite.removeObject(forKey: "keyboard_state") }
        }
        suite.set("cj;dayi", forKey: "keyboard_state")

        // Keyboard resolves its active IM list — the untested cross-process contract.
        let context = try server.prepareKeyboardRuntimeDatabase()
        let nicks = context.activatedIMs.map { $0.tableNick }
        XCTAssertTrue(nicks.contains("cj"),
                      "cj must resolve as active from tableNick keyboard_state; got \(nicks)")
        XCTAssertTrue(nicks.contains("dayi"),
                      "dayi must resolve as active from tableNick keyboard_state; got \(nicks)")
    }

    /// Same failure, but through the REAL install write-path (`registerIM`) instead of a
    /// hand-built cold snapshot — the incremental path a real "install after first run"
    /// takes (shared/absent epoch, generation bumped). Proves registerIM writes the im
    /// inbox + publishes, the keyboard drains it into hot, AND scanAndApply reports the
    /// change so the keyboard reloads (`applied == true`).
    func testRealRegisterIMInstallReachesKeyboardActiveList() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let coldDir = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        try FileManager.default.createDirectory(at: coldDir, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: hotDir, withIntermediateDirectories: true)
        let coldURL = coldDir.appendingPathComponent("lime.db")
        let hotURL = hotDir.appendingPathComponent("lime.db")

        // App side: real install path (two IMs).
        let coldDB = try LimeDB(path: coldURL.path)
        let coldServer = DBServer(_testDatasource: coldDB)
        try coldServer.registerIM(imName: "cj", tableName: "cj", label: "倉頡", keyboardId: "lime")
        try coldServer.registerIM(imName: "dayi", tableName: "dayi", label: "大易", keyboardId: "lime")

        // registerIM must have written the im inbox AND published cold.limedb.
        XCTAssertTrue(FileManager.default.fileExists(atPath: SyncPaths.imInbox(coldDir).path),
                      "install must write the im inbox")
        XCTAssertTrue(FileManager.default.fileExists(atPath: SyncPaths.coldDB(coldDir).path),
                      "install must publish cold.limedb")

        // Keyboard side: real sync.
        let hotDB = try LimeDB(path: hotURL.path)
        let hotServer = DBServer(_testDatasource: hotDB)
        let applied = try TableSyncEngine(appGroupBaseURL: coldDir,
                                          hotDatabaseURL: hotURL,
                                          dbServer: hotServer).scanAndApply()

        let rows = try imRows(in: hotURL)
        XCTAssertTrue(rows.contains { $0.hasPrefix("cj|") },
                      "hot must hold cj after real install+sync; applied=\(applied) rows=\(rows)")
        XCTAssertTrue(rows.contains { $0.hasPrefix("dayi|") },
                      "hot must hold dayi after real install+sync; applied=\(applied) rows=\(rows)")
        XCTAssertTrue(applied,
                      "scanAndApply must report a change so the keyboard reloads its IM list")

        let suite = UserDefaults(suiteName: LIMEPreferenceManager.suiteName)!
        let saved = suite.string(forKey: "keyboard_state")
        defer {
            if let saved { suite.set(saved, forKey: "keyboard_state") }
            else { suite.removeObject(forKey: "keyboard_state") }
        }
        suite.set("cj;dayi", forKey: "keyboard_state")

        let context = try hotServer.prepareKeyboardRuntimeDatabase()
        let nicks = context.activatedIMs.map { $0.tableNick }
        XCTAssertTrue(nicks.contains("cj") && nicks.contains("dayi"),
                      "installed IMs must resolve as active on the keyboard; got \(nicks)")
    }

    /// Regression: restore-a-backup after a converged sync must full-replace hot with the
    /// backup's IMs. Mirrors the REAL restore sequence — publishRestoredCold stamps a FRESH
    /// epoch (replaceEpochUUID) on cold then republishes — which the old applied_epoch design
    /// handled and the epoch_uuid design must too. The failure symptom was an empty IM picker
    /// (hot's im table wiped/empty) + a "同步中" toast on every keyboard appearance.
    func testRestoreFreshEpochFullReplacesHotWithBackupIMs() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        let cold = appGroup.appendingPathComponent("lime.db")
        let hot = hotDir.appendingPathComponent("lime.db")

        let base: [[String: String?]] = [
            ["code": "cj", "title": "倉頡", "desc": "", "keyboard": "lime"],
            ["code": "dayi", "title": "大易", "desc": "", "keyboard": "lime"]
        ]
        // Converged working keyboard: hot == cold, epoch-1, [cj, dayi].
        try makeIMDatabase(at: cold, rows: base, epoch: "epoch-1", generation: 1)
        try makeIMDatabase(at: hot, rows: base, epoch: "epoch-1", generation: 1, applied: true)
        let server = DBServer(_testDatabaseDirectory: hotDir)
        try publish(cold, appGroup: appGroup)
        _ = try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot,
                                dbServer: server).scanAndApply()
        XCTAssertTrue(try imRows(in: hot).contains { $0.hasPrefix("cj|") })

        // RESTORE: replace cold with the backup ([cj,dayi,phonetic]), stamp a FRESH epoch
        // (publishRestoredCold's replaceEpochUUID), then publish — the exact real sequence.
        try FileManager.default.removeItem(at: cold)
        try makeIMDatabase(at: cold, rows: base + [
            ["code": "phonetic", "title": "注音", "desc": "", "keyboard": "lime"]
        ], epoch: "epoch-1", generation: 1)
        _ = try SyncMetaStore(databaseURL: cold).replaceEpochUUID()
        try publish(cold, appGroup: appGroup)

        let applied = try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot,
                                          dbServer: server).scanAndApply()
        let rows = try imRows(in: hot)
        XCTAssertTrue(applied, "restore's fresh epoch must trigger an apply")
        XCTAssertTrue(rows.contains { $0.hasPrefix("phonetic|") },
                      "restored phonetic must reach hot; got \(rows)")

        // Re-open: converged, no re-copy, IMs stay.
        let secondApplied = try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot,
                                                dbServer: server).scanAndApply()
        XCTAssertFalse(secondApplied, "second scan must be a no-op (converged)")
        XCTAssertTrue(try imRows(in: hot).contains { $0.hasPrefix("phonetic|") })
    }

    /// Regression, real enabled-state path: after a real registerIM install + converged sync,
    /// a restore (fresh epoch → full replace) must leave the IMs ENABLED as read by the real
    /// `getAllImConfigs` (KV `title="disable"` rows), which is what `prepareKeyboardRuntimeDatabase`
    /// filters `activatedIMs` on. Empty enabled list == the empty-picker / 同步中 symptom.
    func testRestoreAfterInstallKeepsIMsEnabledViaGetAllImConfigs() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let coldDir = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        try FileManager.default.createDirectory(at: coldDir, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: hotDir, withIntermediateDirectories: true)
        let coldURL = coldDir.appendingPathComponent("lime.db")
        let hotURL = hotDir.appendingPathComponent("lime.db")

        // App installs cj + dayi via the REAL path (writes enabled KV rows + im inbox + publish).
        let coldServer = DBServer(_testDatabaseDirectory: coldDir)
        try coldServer.registerIM(imName: "cj", tableName: "cj", label: "倉頡", keyboardId: "lime")
        try coldServer.registerIM(imName: "dayi", tableName: "dayi", label: "大易", keyboardId: "lime")

        // Keyboard first sync → hot gets the enabled IMs.
        let hotServer = DBServer(_testDatabaseDirectory: hotDir)
        _ = try TableSyncEngine(appGroupBaseURL: coldDir, hotDatabaseURL: hotURL,
                                dbServer: hotServer).scanAndApply()
        XCTAssertTrue(try hotServer.getAllImConfigs().contains { $0.tableNick == "cj" && $0.enabled },
                      "baseline: cj enabled on hot after install")

        // RESTORE: stamp a fresh cold epoch + republish (publishRestoredCold), keyboard re-syncs.
        _ = try SyncMetaStore(databaseURL: coldURL).replaceEpochUUID()
        try ColdPublisher(liveColdDatabaseURL: coldURL, appGroupBaseURL: coldDir).publish()
        let applied = try TableSyncEngine(appGroupBaseURL: coldDir, hotDatabaseURL: hotURL,
                                          dbServer: hotServer).scanAndApply()

        let configs = try hotServer.getAllImConfigs()
        let enabledNicks = configs.filter { $0.enabled }.map { $0.tableNick }
        XCTAssertTrue(applied, "restore's fresh epoch must apply")
        XCTAssertTrue(enabledNicks.contains("cj") && enabledNicks.contains("dayi"),
                      "restored IMs must stay ENABLED on keyboard; got enabled=\(enabledNicks) all=\(configs.map { "\($0.tableNick):\($0.enabled)" })")
    }

    /// Most faithful non-UI reproduction of "restore a backup → empty IM picker / 同步中".
    /// Drives the REAL backup→restore path: backupDatabase (zip + pref sidecars) →
    /// restoreDatabase (extract + file swap + restore shared prefs incl. keyboard_state) →
    /// reregisterKnownIMs → publishRestoredCold (fresh epoch + publish) → keyboard scanAndApply
    /// → getAllImConfigs enabled resolution. If restore ever leaves hot with no enabled IMs,
    /// this fails exactly as the device does.
    func testRealBackupRestoreRoundTripKeepsIMsEnabledOnKeyboard() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let coldDir = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        try FileManager.default.createDirectory(at: coldDir, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: hotDir, withIntermediateDirectories: true)

        // App installs cj + dayi, then makes a real backup zip.
        let coldServer = DBServer(_testDatabaseDirectory: coldDir)
        try coldServer.registerIM(imName: "cj", tableName: "cj", label: "倉頡", keyboardId: "lime")
        try coldServer.registerIM(imName: "dayi", tableName: "dayi", label: "大易", keyboardId: "lime")
        let backupURL = root.appendingPathComponent("backup.zip")
        try coldServer.backupDatabase(uri: backupURL)

        // Restore it via the REAL path (SetupImController.restoreBackupIntoCold sequence).
        try coldServer.restoreDatabase(srcFilePath: backupURL.path)
        for (name, title, kb) in [("cj", "倉頡", "lime_cj_number"), ("dayi", "大易", "lime_dayi")]
        where coldServer.tableHasData(name) {
            try? coldServer.registerIM(imName: name, tableName: name, label: title, keyboardId: kb)
        }
        let liveCold = coldServer.liveDatabaseURL()
        _ = try SyncMetaStore(databaseURL: liveCold).replaceEpochUUID()
        try ColdPublisher(liveColdDatabaseURL: liveCold, appGroupBaseURL: coldDir).publish()

        // Keyboard syncs and resolves its enabled IM list.
        let hotServer = DBServer(_testDatabaseDirectory: hotDir)
        let hotURL = hotDir.appendingPathComponent("lime.db")
        _ = try TableSyncEngine(appGroupBaseURL: coldDir, hotDatabaseURL: hotURL,
                                dbServer: hotServer).scanAndApply()

        let configs = try hotServer.getAllImConfigs()
        let enabled = configs.filter { $0.enabled }.map { $0.tableNick }
        XCTAssertTrue(enabled.contains("cj") && enabled.contains("dayi"),
                      "restored IMs must be ENABLED on keyboard; got enabled=\(enabled) all=\(configs.map { "\($0.tableNick):\($0.enabled)" })")
    }

    /// ROOT-CAUSE regression (real device state): an install-only cold has NO epoch_uuid
    /// (registerIM/publish never stamps one), while a hot that synced via the incremental path
    /// keeps its RANDOM bootstrap epoch_uuid (ensureKeyboardHotDatabase). coldEpoch(nil) !=
    /// hotEpoch(bootstrap) must NOT be read as "different lineage → full replace" — that clears
    /// the pending IM/lifecycle inboxes without applying them and wipes hot-only state, which
    /// is the empty-picker / 同步中 symptom. hot's own epoch is its identity, NOT the applied
    /// cold-epoch marker; the decoupled applied_epoch marker (nil == nil) proves "converged".
    func testInstallOnlyColdNilEpochDoesNotSpuriouslyFullReplace() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        let hot = hotDir.appendingPathComponent("lime.db")
        let coldSnapshot = SyncPaths.coldDB(appGroup)

        // Cold snapshot: install-only, NO epoch_uuid, generation 4, phonetic enabled.
        try makeIMDatabase(at: coldSnapshot, rows: [
            ["code": "phonetic", "title": "注音", "desc": "", "keyboard": "lime"]
        ], generation: 4)
        try SyncMetaStore(databaseURL: coldSnapshot).removeValue(forKey: SyncMetaStore.epochUUIDKey)

        // Hot: phonetic + a hot-only sentinel IM, converged (applied_gen 4), bootstrap epoch.
        try makeIMDatabase(at: hot, rows: [
            ["code": "phonetic", "title": "注音", "desc": "", "keyboard": "lime"],
            ["code": "sentinel", "title": "哨", "desc": "", "keyboard": "lime"]
        ], epoch: "boot-276A", generation: 4, applied: true)

        let server = DBServer(_testDatabaseDirectory: hotDir)
        _ = try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot,
                                dbServer: server).scanAndApply()

        let codes = Set(try imRows(in: hot).map { String($0.prefix(while: { $0 != "|" })) })
        XCTAssertTrue(codes.contains("sentinel"),
                      "install-only cold (nil epoch) must NOT full-replace a converged hot; got \(codes)")
        XCTAssertTrue(codes.contains("phonetic"))
    }

    /// A stale, ALREADY-CONSUMED im-inbox record (lingering because the keyboard can't delete
    /// the App Group FA-off) must NOT resurrect after a full-replace — the seq cursor lives in
    /// UserDefaults (survives the whole-file swap), so the record stays below it. This is the
    /// restore-to-default → install-one → "picker shows the old IMs, only the new works" bug.
    func testStaleConsumedIMInboxNotResurrectedByFullReplace() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        let hot = hotDir.appendingPathComponent("lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeIMDatabase(at: cold,
                           rows: [
                               ["code": "restored", "title": "keyboard", "desc": "Restored", "keyboard": "lime"]
                           ],
                           epoch: "epoch-restored",
                           generation: 1)
        try makeIMDatabase(at: hot,
                           rows: [
                               ["code": "old", "title": "keyboard", "desc": "Old", "keyboard": "oldkb"]
                           ],
                           epoch: "epoch-old",
                           generation: 1)
        // A stale im record the keyboard has ALREADY consumed: its seq (1) is at/below the
        // cursor, which lives outside the hot DB so the full-replace can't reset it.
        let suite = try isolatedDefaults()
        try IMInbox.append([
            IMInboxRecord(op: .upsert, row: ["code": "stale", "title": "keyboard", "desc": "Stale", "keyboard": "stale"])
        ], base: appGroup, defaults: suite)
        suite.set(1, forKey: IMInbox.consumedSeqKey)
        let server = DBServer(_testDatabaseDirectory: hotDir)

        try publish(cold, appGroup: appGroup)
        try TableSyncEngine(appGroupBaseURL: appGroup,
                            hotDatabaseURL: hot,
                            dbServer: server,
                            consumeDefaults: suite).scanAndApply()

        let rows = try imRows(in: hot)
        XCTAssertTrue(rows.contains("restored|keyboard|Restored|lime"))
        XCTAssertFalse(rows.contains { $0.hasPrefix("stale|") },
                       "consumed stale record must not resurrect after the full-replace; got \(rows)")
    }

    /// The reported bug, end-to-end through the picker's real source (`getAllImConfigs`):
    /// restore-to-default wipes hot; the pre-restore installs linger in the inbox but are
    /// already consumed (cursor); installing ONE new IM must leave ONLY that IM enabled — the
    /// wiped ones do not reappear.
    func testInstallAfterRestoreWipeEnablesOnlyTheNewIM() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        let hot = hotDir.appendingPathComponent("lime.db")
        let coldSnapshot = SyncPaths.coldDB(appGroup)
        let server = DBServer(_testDatabaseDirectory: hotDir)
        let suite = try isolatedDefaults()

        // Pre-restore: cj + dayi were installed (inbox seq 1,2) and already consumed.
        try IMInbox.append([
            IMInboxRecord(op: .upsert, row: ["code": "cj", "title": "倉頡", "desc": "", "keyboard": "lime"]),
            IMInboxRecord(op: .upsert, row: ["code": "dayi", "title": "大易", "desc": "", "keyboard": "lime"])
        ], base: appGroup, defaults: suite)
        suite.set(2, forKey: IMInbox.consumedSeqKey)

        // restore-to-default left hot wiped (empty im, same lineage as the restored cold).
        try makeIMDatabase(at: hot, rows: [], epoch: "epoch-default", generation: 1, applied: true)
        // Then install phonetic: cold now carries it (gen bumped) + inbox gets phonetic@3.
        try makeIMDatabase(at: coldSnapshot, rows: [
            ["code": "phonetic", "title": "注音", "desc": "", "keyboard": "lime"]
        ], epoch: "epoch-default", generation: 2)
        try IMInbox.append([
            IMInboxRecord(op: .upsert, row: ["code": "phonetic", "title": "注音", "desc": "", "keyboard": "lime"])
        ], base: appGroup, defaults: suite)

        _ = try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot,
                                dbServer: server, consumeDefaults: suite).scanAndApply()

        let enabled = try server.getAllImConfigs().filter { $0.enabled }.map { $0.tableNick }.sorted()
        XCTAssertEqual(enabled, ["phonetic"],
                       "only the newly-installed IM should be enabled; the wiped cj/dayi must not reappear. got \(enabled)")
    }

    /// A full replace must NOT apply a pending (un-consumed, seq > cursor) im record — the
    /// snapshot is authoritative, so applying it would land a stale IM on top of the restore.
    /// It marks the record consumed (cursor advances) instead.
    func testFullReplaceMarksPendingInboxConsumedWithoutApplying() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hotDir = root.appendingPathComponent("hot", isDirectory: true)
        let hot = hotDir.appendingPathComponent("lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeIMDatabase(at: cold, rows: [
            ["code": "restored", "title": "注音", "desc": "", "keyboard": "lime"]
        ], epoch: "epoch-new", generation: 1)
        try makeIMDatabase(at: hot, rows: [
            ["code": "old", "title": "大易", "desc": "", "keyboard": "old"]
        ], epoch: "epoch-old", generation: 1)
        // A pending record (seq 1 > cursor 0) for a stale IM the restore snapshot does NOT have.
        let suite = try isolatedDefaults()
        try IMInbox.append([
            IMInboxRecord(op: .upsert, row: ["code": "stale", "title": "舊", "desc": "", "keyboard": "lime"])
        ], base: appGroup, defaults: suite)
        let server = DBServer(_testDatabaseDirectory: hotDir)
        try publish(cold, appGroup: appGroup)

        _ = try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot,
                                dbServer: server, consumeDefaults: suite).scanAndApply()

        let rows = try imRows(in: hot)
        XCTAssertTrue(rows.contains { $0.hasPrefix("restored|") })
        XCTAssertFalse(rows.contains { $0.hasPrefix("stale|") },
                       "un-consumed record must NOT be applied on the restore snapshot; got \(rows)")
        XCTAssertEqual(suite.integer(forKey: IMInbox.consumedSeqKey), 1,
                       "cursor advanced past the superseded record so a later drain skips it")
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
        try hotMeta.setValue("epoch-a", forKey: SyncMetaStore.epochUUIDKey)
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
        try hotMeta.setValue("epoch-a", forKey: SyncMetaStore.epochUUIDKey)
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
        try hotMeta.setValue("epoch-a", forKey: SyncMetaStore.epochUUIDKey)

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
        // Isolated suite holds the seq counter (append stamps 1,2) + the consume cursor
        // (fresh 0), so the seq-guarded drain applies both records deterministically.
        let suite = try isolatedDefaults()
        try IMInbox.append([
            IMInboxRecord(op: .upsert, row: [
                "code": "custom", "title": "keyboard", "desc": "New", "keyboard": "lime"
            ]),
            IMInboxRecord(op: .delete, row: ["code": "old"])
        ], base: appGroup, defaults: suite)

        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot,
                            consumeDefaults: suite).scanAndApply()

        XCTAssertEqual(try imRows(in: hot), ["custom|keyboard|New|lime"])
        XCTAssertTrue(try imRows(in: coldSnapshot).isEmpty)
        // Consumed via the cursor, NOT by deleting the App Group inbox (read-only FA-off).
        XCTAssertEqual(suite.integer(forKey: IMInbox.consumedSeqKey), 2)
    }

    func testBackupExportRequestVacuumHotSnapshotAndWritesReceipt() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hot = root.appendingPathComponent("hot/lime.db")
        try makeDatabase(at: hot,
                         rows: [("learned", "學", 42)],
                         epoch: "hot-epoch",
                         generation: 7)
        try writeExportRequest(appGroup: appGroup, requestUUID: "backup-1")

        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try customRows(in: SyncPaths.backupSnapshot(appGroup)),
                       ["learned|學|42"])
        let receipt = try exportReceipt(appGroup: appGroup)
        XCTAssertEqual(receipt.requestUUID, "backup-1")
        XCTAssertEqual(receipt.epochUUID, "hot-epoch")
        XCTAssertFalse(FileManager.default.fileExists(atPath: SyncPaths.exportRequest(appGroup).path))
    }

    func testEditorRefreshHarvestsNewAndScoreChangedRowsIntoLiveCold() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let liveCold = appGroup.appendingPathComponent("lime.db")
        let hot = root.appendingPathComponent("hot/lime.db")
        try makeDatabase(at: liveCold,
                         rows: [
                            ("changed", "分", 1),
                            ("same", "同", 2),
                         ])
        try makeDatabase(at: hot,
                         rows: [
                            ("changed", "分", 9),
                            ("learned", "學", 5),
                            ("same", "同", 2),
                         ])
        let unchangedID = try customRowID(code: "same", word: "同", in: liveCold)
        try writeEditorRefreshRequest(appGroup: appGroup, table: "custom", requestUUID: "refresh-1")

        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try customRows(in: liveCold),
                       ["changed|分|9", "learned|學|5", "same|同|2"])
        XCTAssertEqual(try customRowID(code: "same", word: "同", in: liveCold), unchangedID)
        let receipt = try editorRefreshReceipt(appGroup: appGroup)
        XCTAssertEqual(receipt.requestUUID, "refresh-1")
        XCTAssertEqual(receipt.status, .done)
        XCTAssertFalse(FileManager.default.fileExists(atPath: SyncPaths.editorRefreshRequest(appGroup).path))
    }

    func testEditorRefreshHarvestsRelatedRowsByParentChildKey() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let liveCold = appGroup.appendingPathComponent("lime.db")
        let hot = root.appendingPathComponent("hot/lime.db")
        try makeRelatedDatabase(at: liveCold,
                                rows: [("你", "好", 1)])
        try makeRelatedDatabase(at: hot,
                                rows: [
                                    ("你", "好", 8),
                                    ("天", "氣", 2),
                                ])
        try writeEditorRefreshRequest(appGroup: appGroup, table: "related", requestUUID: "related-1")

        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try relatedRows(in: liveCold), ["你|好|8", "天|氣|2"])
        XCTAssertEqual(try editorRefreshReceipt(appGroup: appGroup).status, .done)
    }

    func testCloseReconcileAppliesColdAddEditAndDeleteToHot() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let liveCold = appGroup.appendingPathComponent("lime.db")
        let hot = root.appendingPathComponent("hot/lime.db")
        let initialRows = [
            (code: "delete", word: "刪", score: 1),
            (code: "edit", word: "改", score: 2),
            (code: "keep", word: "留", score: 3),
        ]
        try makeDatabase(at: liveCold,
                         rows: initialRows,
                         epoch: "epoch-a",
                         generation: 1,
                         revisions: ["custom": 1])
        try makeDatabase(at: hot,
                         rows: initialRows,
                         epoch: "epoch-a",
                         revisions: ["custom": 1])
        let hotMeta = try SyncMetaStore(databaseURL: hot)
        try hotMeta.setValue("epoch-a", forKey: SyncMetaStore.epochUUIDKey)
        try hotMeta.setAppliedGeneration(1)
        try editCustomRows(in: liveCold) { db in
            try db.execute(sql: "DELETE FROM custom WHERE code = ?", arguments: ["delete"])
            try db.execute(sql: "UPDATE custom SET score = ? WHERE code = ?", arguments: [7, "edit"])
            try db.execute(sql: "INSERT INTO custom (code, word, score) VALUES (?, ?, ?)",
                           arguments: ["add", "加", 4])
        }
        _ = try SyncMetaStore(databaseURL: liveCold).bumpRevision(forTable: "custom")
        try publish(liveCold, appGroup: appGroup)

        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try customRows(in: hot), ["add|加|4", "edit|改|7", "keep|留|3"])
    }

    func testEditorRefreshThenCloseReconcileRoundTripsLearningAndAppEdits() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let liveCold = appGroup.appendingPathComponent("lime.db")
        let hot = root.appendingPathComponent("hot/lime.db")
        try makeDatabase(at: liveCold,
                         rows: [
                            ("delete", "刪", 1),
                            ("edit", "改", 2),
                         ])
        try makeDatabase(at: hot,
                         rows: [
                            ("delete", "刪", 1),
                            ("edit", "改", 2),
                            ("learned", "學", 9),
                         ])
        try writeEditorRefreshRequest(appGroup: appGroup, table: "custom")
        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()
        XCTAssertEqual(try customRows(in: liveCold),
                       ["delete|刪|1", "edit|改|2", "learned|學|9"])

        try editCustomRows(in: liveCold) { db in
            try db.execute(sql: "DELETE FROM custom WHERE code = ?", arguments: ["delete"])
            try db.execute(sql: "UPDATE custom SET score = ? WHERE code = ?", arguments: [6, "edit"])
            try db.execute(sql: "INSERT INTO custom (code, word, score) VALUES (?, ?, ?)",
                           arguments: ["app", "編", 4])
        }
        _ = try SyncMetaStore(databaseURL: liveCold).bumpRevision(forTable: "custom")
        try publish(liveCold, appGroup: appGroup)

        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try customRows(in: hot),
                       ["app|編|4", "edit|改|6", "learned|學|9"])
    }

    func testLifecycleDeleteWithBackupCreatesHotUserTableBeforeClearing() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hot = root.appendingPathComponent("hot/lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: cold, epoch: "epoch-a", revisions: ["custom": 2])
        try makeDatabase(at: hot,
                         rows: [("learned", "學", 8), ("base", "基", 0)],
                         epoch: "epoch-a",
                         revisions: ["custom": 1])
        let hotMeta = try SyncMetaStore(databaseURL: hot)
        try hotMeta.setValue("epoch-a", forKey: SyncMetaStore.epochUUIDKey)
        try writeLifecycleRecords(appGroup: appGroup, [
            ["table": "custom", "action": "delete", "preserveLearning": true]
        ])

        try publish(cold, appGroup: appGroup)
        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try customRows(in: hot), [])
        XCTAssertEqual(try customBackupRows(in: hot), ["learned|學|8"])    }

    func testLifecycleImportWithRestoreRestoresHotUserTableAfterCopy() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hot = root.appendingPathComponent("hot/lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: cold,
                         rows: [("learned", "學", 0), ("base", "基", 0)],
                         epoch: "epoch-a",
                         revisions: ["custom": 2])
        try makeDatabase(at: hot,
                         rows: [("learned", "學", 8)],
                         epoch: "epoch-a",
                         revisions: ["custom": 1])
        try createCustomUserBackup(in: hot)
        let hotMeta = try SyncMetaStore(databaseURL: hot)
        try hotMeta.setValue("epoch-a", forKey: SyncMetaStore.epochUUIDKey)
        try writeLifecycleRecords(appGroup: appGroup, [
            ["table": "custom", "action": "install", "preserveLearning": true]
        ])

        try publish(cold, appGroup: appGroup)
        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try customRows(in: hot), ["base|基|0", "learned|學|8"])
        XCTAssertFalse(try tableExists("custom_user", in: hot))    }

    func testLifecycleOptOutSkipsBackupAndRestore() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hot = root.appendingPathComponent("hot/lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: cold, epoch: "epoch-a", revisions: ["custom": 2])
        try makeDatabase(at: hot,
                         rows: [("learned", "學", 8)],
                         epoch: "epoch-a",
                         revisions: ["custom": 1])
        let hotMeta = try SyncMetaStore(databaseURL: hot)
        try hotMeta.setValue("epoch-a", forKey: SyncMetaStore.epochUUIDKey)
        try writeLifecycleRecords(appGroup: appGroup, [
            ["table": "custom", "action": "delete", "preserveLearning": false]
        ])
        try publish(cold, appGroup: appGroup)
        let engine = TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot)

        try engine.scanAndApply()

        XCTAssertEqual(try customRows(in: hot), [])
        XCTAssertFalse(try tableExists("custom_user", in: hot))

        try editCustomRows(in: cold) { db in
            try db.execute(sql: "INSERT INTO custom (code, word, score) VALUES (?, ?, ?)",
                           arguments: ["learned", "學", 0])
        }
        _ = try SyncMetaStore(databaseURL: cold).bumpRevision(forTable: "custom")
        try writeLifecycleRecords(appGroup: appGroup, [
            ["table": "custom", "action": "install", "preserveLearning": false]
        ])
        try publish(cold, appGroup: appGroup)

        try engine.scanAndApply()

        XCTAssertEqual(try customRows(in: hot), ["learned|學|0"])
        XCTAssertFalse(try tableExists("custom_user", in: hot))    }

    func testLifecycleDeleteThenReimportRoundTripPreservesLearnedScores() throws {
        let root = try tempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let appGroup = root.appendingPathComponent("app-group", isDirectory: true)
        let hot = root.appendingPathComponent("hot/lime.db")
        let cold = appGroup.appendingPathComponent("lime.db")
        try makeDatabase(at: cold,
                         rows: [("learned", "學", 0), ("base", "基", 0)],
                         epoch: "epoch-a",
                         revisions: ["custom": 3])
        try makeDatabase(at: hot,
                         rows: [("learned", "學", 8), ("hotonly", "熱", 4)],
                         epoch: "epoch-a",
                         revisions: ["custom": 1])
        let hotMeta = try SyncMetaStore(databaseURL: hot)
        try hotMeta.setValue("epoch-a", forKey: SyncMetaStore.epochUUIDKey)
        try writeLifecycleRecords(appGroup: appGroup, [
            ["table": "custom", "action": "delete", "preserveLearning": true],
            ["table": "custom", "action": "install", "preserveLearning": true]
        ])

        try publish(cold, appGroup: appGroup)
        try TableSyncEngine(appGroupBaseURL: appGroup, hotDatabaseURL: hot).scanAndApply()

        XCTAssertEqual(try customRows(in: hot),
                       ["base|基|0", "hotonly|熱|4", "learned|學|8"])
        XCTAssertFalse(try tableExists("custom_user", in: hot))    }
}
