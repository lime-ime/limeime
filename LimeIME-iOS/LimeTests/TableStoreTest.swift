import XCTest
import GRDB
import ZIPFoundation
@testable import LimeIME

final class TableStoreTest: XCTestCase {
    private var baseURL: URL!
    private let fm = FileManager.default

    override func setUpWithError() throws {
        baseURL = fm.temporaryDirectory
            .appendingPathComponent("TableStoreTest-\(UUID().uuidString)", isDirectory: true)
        try fm.createDirectory(at: baseURL, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? fm.removeItem(at: baseURL)
    }

    func testInstallLimedbHappyPath() throws {
        let store = TableStore(baseURL: baseURL)
        let source = try makeLimedb(stem: "cj")

        try store.installLimedb(from: source, stem: "cj", meta: nil)

        XCTAssertTrue(fm.fileExists(atPath: SyncPaths.tableFile(baseURL, stem: "cj").path))
        XCTAssertFalse(fm.fileExists(atPath: SyncPaths.tableMeta(baseURL, stem: "cj").path))
        XCTAssertEqual(store.installedStems(), ["cj"])
        XCTAssertFalse(try tableDirContents().contains { $0.contains(".tmp-") })

        let meta = TableMeta(restoreLearning: true, displayName: "CJ", provenance: "unit")
        try store.installLimedb(from: source, stem: "cj", meta: meta)
        let metaData = try Data(contentsOf: SyncPaths.tableMeta(baseURL, stem: "cj"))
        XCTAssertEqual(try JSONDecoder().decode(TableMeta.self, from: metaData), meta)
        XCTAssertFalse(try tableDirContents().contains { $0.contains(".tmp-") })
    }

    func testInstallLimedbRejectsCorrupt() throws {
        let store = TableStore(baseURL: baseURL)
        let source = tempURL("corrupt.limedb")
        try Data(repeating: 0x7f, count: 1024).write(to: source)

        XCTAssertThrowsError(try store.installLimedb(from: source, stem: "cj", meta: nil)) { error in
            guard let error = error as? TableStoreError, case .invalidDatabase = error else {
                return XCTFail("Expected invalidDatabase, got \(error)")
            }
        }
        XCTAssertTrue(try tableDirContents().isEmpty)
    }

    func testInstallLimedbRejectsMissingTable() throws {
        let store = TableStore(baseURL: baseURL)
        let source = tempURL("missing.limedb")
        let queue = try DatabaseQueue(path: source.path)
        try queue.write { db in
            try db.execute(sql: "CREATE TABLE other (code TEXT)")
            try db.execute(sql: "INSERT INTO other (code) VALUES ('a')")
        }

        XCTAssertThrowsError(try store.installLimedb(from: source, stem: "cj", meta: nil)) { error in
            guard let error = error as? TableStoreError, case .invalidDatabase = error else {
                return XCTFail("Expected invalidDatabase, got \(error)")
            }
        }
        XCTAssertTrue(try tableDirContents().isEmpty)
    }

    func testInstallTextConvertsCin() throws {
        let store = TableStore(baseURL: baseURL)
        let source = try makeCinFixture()

        try store.installText(from: source, stem: "cj", meta: nil)

        let installed = SyncPaths.tableFile(baseURL, stem: "cj")
        XCTAssertTrue(fm.fileExists(atPath: installed.path))
        let queue = try DatabaseQueue(path: installed.path)
        let count = try queue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(quote("cj"))") ?? 0
        }
        XCTAssertEqual(count, 2)
    }

    func testInstallTextRejectsGarbage() throws {
        let store = TableStore(baseURL: baseURL)
        let source = tempURL("garbage.cin")
        try "not a cin table\nstill not rows\n".write(to: source, atomically: true, encoding: .utf8)

        XCTAssertThrowsError(try store.installText(from: source, stem: "cj", meta: nil)) { error in
            guard let error = error as? TableStoreError, case .parseFailed = error else {
                return XCTFail("Expected parseFailed, got \(error)")
            }
        }
        XCTAssertTrue(try tableDirContents().isEmpty)
    }

    func testInstallFromZipRoutesInner() throws {
        let store = TableStore(baseURL: baseURL)
        let source = try makeLimedb(stem: "cj")
        let zip = try makeZip(containing: source, entryName: "nested/cj.limedb")

        try store.installFromZip(from: zip, stem: "cj", meta: nil)

        XCTAssertEqual(store.installedStems(), ["cj"])
        XCTAssertTrue(fm.fileExists(atPath: SyncPaths.tableFile(baseURL, stem: "cj").path))
    }

    func testUninstallRemovesBoth() throws {
        let store = TableStore(baseURL: baseURL)
        let source = try makeLimedb(stem: "cj")
        try store.installLimedb(from: source, stem: "cj",
                                meta: TableMeta(restoreLearning: false, displayName: "CJ", provenance: nil))

        try store.uninstall(stem: "cj")

        XCTAssertFalse(fm.fileExists(atPath: SyncPaths.tableFile(baseURL, stem: "cj").path))
        XCTAssertFalse(fm.fileExists(atPath: SyncPaths.tableMeta(baseURL, stem: "cj").path))
        XCTAssertTrue(store.installedStems().isEmpty)
    }

    func testPrepareRestoreStampsAndClears() throws {
        let store = TableStore(baseURL: baseURL)
        try store.installLimedb(from: makeLimedb(stem: "cj"), stem: "cj", meta: nil)
        try store.installLimedb(from: makeLimedb(stem: "array"), stem: "array",
                                meta: TableMeta(restoreLearning: true, displayName: "Array", provenance: nil))
        let source = try makeRestoreSource(epoch: "OLD", schemaVersion: LimeDB.CURRENT_DB_VERSION)
        let beforeIdentity = try XCTUnwrap(FileIdentity(url: source))

        let epoch = try store.prepareRestore(from: source)

        XCTAssertTrue(try tableDirContents().isEmpty)
        XCTAssertTrue(fm.fileExists(atPath: SyncPaths.restoreDB(baseURL).path))
        let restoreQueue = try DatabaseQueue(path: SyncPaths.restoreDB(baseURL).path)
        let stamped = try restoreQueue.read { db in
            try String.fetchOne(db, sql: "SELECT value FROM sync_meta WHERE key = 'epoch_uuid'")
        }
        let schemaVersion = try restoreQueue.read { db in
            try String.fetchOne(db, sql: "SELECT value FROM sync_meta WHERE key = 'schema_version'")
        }
        let ledgerCount = try restoreQueue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM sync_ledger") ?? -1
        }
        XCTAssertEqual(stamped, epoch)
        XCTAssertNotEqual(stamped, "OLD")
        XCTAssertEqual(schemaVersion, "\(LimeDB.CURRENT_DB_VERSION)")
        XCTAssertEqual(ledgerCount, 0)

        let metaData = try Data(contentsOf: SyncPaths.restoreMeta(baseURL))
        XCTAssertEqual(try JSONDecoder().decode(RestoreMeta.self, from: metaData),
                       RestoreMeta(epochUUID: epoch, schemaVersion: LimeDB.CURRENT_DB_VERSION))
        XCTAssertEqual(FileIdentity(url: source), beforeIdentity)
    }

    func testPrepareRestoreRejectsNewerSchema() throws {
        let store = TableStore(baseURL: baseURL)
        let sourceTable = try makeLimedb(stem: "cj")
        try store.installLimedb(from: sourceTable, stem: "cj",
                                meta: TableMeta(restoreLearning: true, displayName: "CJ", provenance: "before"))
        let beforeContents = try tableDirContents()
        let beforeIdentity = try XCTUnwrap(FileIdentity(url: SyncPaths.tableFile(baseURL, stem: "cj")))
        let source = try makeRestoreSource(epoch: "OLD", schemaVersion: LimeDB.CURRENT_DB_VERSION + 1)

        XCTAssertThrowsError(try store.prepareRestore(from: source)) { error in
            guard let error = error as? TableStoreError,
                  case .schemaTooNew(LimeDB.CURRENT_DB_VERSION + 1) = error else {
                return XCTFail("Expected schemaTooNew, got \(error)")
            }
        }
        XCTAssertEqual(try tableDirContents(), beforeContents)
        XCTAssertEqual(FileIdentity(url: SyncPaths.tableFile(baseURL, stem: "cj")), beforeIdentity)
        XCTAssertFalse(fm.fileExists(atPath: SyncPaths.restoreDB(baseURL).path))
        XCTAssertFalse(fm.fileExists(atPath: SyncPaths.restoreMeta(baseURL).path))
    }

    private func makeLimedb(stem: String, rows: [(String, String, Int)] = [
        ("a", "one", 1),
        ("b", "two", 2),
    ]) throws -> URL {
        let url = tempURL("\(stem)-\(UUID().uuidString).limedb")
        let queue = try DatabaseQueue(path: url.path)
        try queue.write { db in
            try db.execute(sql: "CREATE TABLE \(quote(stem)) (code TEXT, word TEXT, score INTEGER)")
            for row in rows {
                try db.execute(sql: "INSERT INTO \(quote(stem)) (code, word, score) VALUES (?, ?, ?)",
                               arguments: [row.0, row.1, row.2])
            }
        }
        return url
    }

    private func makeCinFixture() throws -> URL {
        let url = tempURL("fixture-\(UUID().uuidString).cin")
        try """
        %cname TableStore
        %chardef begin
        a\t一
        b\t二
        %chardef end
        """.write(to: url, atomically: true, encoding: .utf8)
        return url
    }

    private func makeZip(containing fileURL: URL, entryName: String) throws -> URL {
        let zipURL = tempURL("fixture-\(UUID().uuidString).zip")
        let archive = try Archive(url: zipURL, accessMode: .create)
        try archive.addEntry(with: entryName, fileURL: fileURL)
        return zipURL
    }

    // AMENDED restore semantics (user acceptance): the backup's im table is the
    // authority for the installed-set — restore rebuilds tables/ sources from it,
    // so the app's IM list repopulates after a restore.
    func testPrepareRestoreRebuildsSourcesFromIMTable() throws {
        let store = TableStore(baseURL: baseURL)
        let source = tempURL("restore-full-\(UUID().uuidString).limedb")
        let queue = try DatabaseQueue(path: source.path)
        try queue.write { db in
            try db.execute(sql: "CREATE TABLE cj (code TEXT, word TEXT, score INTEGER DEFAULT 0)")
            try db.execute(sql: "INSERT INTO cj VALUES ('a', '一', 5)")
            try db.execute(sql: "CREATE TABLE dayi (code TEXT, word TEXT, score INTEGER DEFAULT 0)")
            // dayi has a table but is NOT registered in im → must NOT be rebuilt
            try db.execute(sql: """
                CREATE TABLE im (code TEXT, title TEXT, desc TEXT, keyboard TEXT,
                                 disable INTEGER, selkey TEXT, endkey TEXT, spacestyle TEXT)
            """)
            try db.execute(sql: "INSERT INTO im VALUES ('cj', '倉頡輸入法', '', 'lime', 0, '', '', '')")
        }
        try queue.close()

        _ = try store.prepareRestore(from: source)

        XCTAssertEqual(store.installedStems(), ["cj"])
        let rebuilt = SyncPaths.tableFile(baseURL, stem: "cj")
        let rebuiltQueue = try DatabaseQueue(path: rebuilt.path)
        let (rows, imTitle) = try rebuiltQueue.read { db -> (Int, String?) in
            (try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM cj") ?? 0,
             try String.fetchOne(db, sql: "SELECT title FROM im WHERE code = 'cj'"))
        }
        XCTAssertEqual(rows, 1)
        XCTAssertEqual(imTitle, "倉頡輸入法")
        let metaData = try Data(contentsOf: SyncPaths.tableMeta(baseURL, stem: "cj"))
        XCTAssertEqual(try JSONDecoder().decode(TableMeta.self, from: metaData).provenance, "restore")
    }

    private func makeRestoreSource(epoch: String, schemaVersion: Int) throws -> URL {
        let url = tempURL("restore-source-\(UUID().uuidString).limedb")
        let queue = try DatabaseQueue(path: url.path)
        try queue.write { db in
            try db.execute(sql: "CREATE TABLE sync_meta (key TEXT PRIMARY KEY, value TEXT)")
            try db.execute(sql: "INSERT INTO sync_meta (key, value) VALUES ('epoch_uuid', ?)", arguments: [epoch])
            try db.execute(sql: "INSERT INTO sync_meta (key, value) VALUES ('schema_version', ?)",
                           arguments: ["\(schemaVersion)"])
            try db.execute(sql: """
                CREATE TABLE sync_ledger (
                    stem TEXT PRIMARY KEY, size INTEGER, mtime REAL,
                    state TEXT NOT NULL, error TEXT,
                    attempts INTEGER NOT NULL DEFAULT 0, resume_marker INTEGER
                )
            """)
            try db.execute(sql: """
                INSERT INTO sync_ledger (stem, size, mtime, state, attempts)
                VALUES ('cj', 1, 1.0, 'done', 0)
            """)
        }
        return url
    }

    private func tableDirContents() throws -> [String] {
        let dir = SyncPaths.tablesDir(baseURL)
        guard fm.fileExists(atPath: dir.path) else { return [] }
        return try fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)
            .map { $0.lastPathComponent }
            .sorted()
    }

    private func tempURL(_ name: String) -> URL {
        baseURL.appendingPathComponent(name)
    }

    private func quote(_ identifier: String) -> String {
        "\"\(identifier.replacingOccurrences(of: "\"", with: "\"\""))\""
    }
}
