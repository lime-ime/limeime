import XCTest
import GRDB
import ZIPFoundation
@testable import LimeIME

/// iOS parity coverage for Android IntegrationTestBackupRestore.
/// Uses the repo's real Database/*.zip cloud fixtures and the LIME Settings
/// controller import path, but keeps the database isolated in a temp file.
final class IntegrationTestBackupRestore: XCTestCase {

    private struct CloudIMFixture {
        let table: String
        let fileName: String
    }

    private let cloudFixtures = [
        CloudIMFixture(table: "phonetic", fileName: "phonetic.zip"),
        CloudIMFixture(table: "dayi", fileName: "dayi.zip")
    ]

    private var tempRoot: URL!

    override func setUpWithError() throws {
        try super.setUpWithError()
        tempRoot = FileManager.default.temporaryDirectory
            .appendingPathComponent("IntegrationTestBackupRestore-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: tempRoot, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: tempRoot)
        try super.tearDownWithError()
    }

    @MainActor
    func testCloudIMInstallBackupAndRestoreLearningThroughSettingsImport() async throws {
        for fixture in cloudFixtures {
            let h = try makeHarness(label: fixture.table)
            defer { cleanup(h) }
            try await importCloudIM(try cloudFixtureURL(fixture.fileName),
                                    table: fixture.table,
                                    controller: h.controller)
            XCTAssertTrue(h.engine.scanAndApply().contains(SyncEvent(kind: .imported, stem: fixture.table)))
            let db = try XCTUnwrap(h.database.current())
            XCTAssertGreaterThan(db.countRecords(fixture.table, nil, nil),
                                 0,
                                 "\(fixture.table) cloud fixture should install records")

            let code = "ios_backup_pair_\(fixture.table)"
            let word1 = "備份對\(fixture.table)"
            let word2 = "還原對\(fixture.table)"
            db.addOrUpdateMappingRecord(fixture.table, code, word1, 220)
            db.addOrUpdateMappingRecord(fixture.table, code, word2, 210)
            XCTAssertEqual(learnedScores(db, table: fixture.table, code: code),
                           [word1: 220, word2: 210])

            let backupZip = try backupThroughRelay(h)
            let restoreResult = await h.controller.restoreDB(from: backupZip)
            if case .failure(let error) = restoreResult {
                throw error
            }
            h.database.closeCurrentForReplacement()
            let restoredDatabase = SharedDatabase(runMode: .keyboard,
                                                  dataDirOverride: h.ownDir,
                                                  appGroupOverride: h.appGroupDir)
            let restoredEngine = TableSyncEngine(database: restoredDatabase, baseURL: h.appGroupDir)
            _ = try XCTUnwrap(restoredDatabase.current())
            let restoreEvents = restoredEngine.scanAndApply()
            XCTAssertTrue(restoreEvents.contains(SyncEvent(kind: .epochApplied, stem: nil)),
                          "restore events: \(restoreEvents)")
            let restoredDB = try XCTUnwrap(restoredDatabase.current())

            XCTAssertEqual(learnedScores(restoredDB, table: fixture.table, code: code),
                           [word1: 220, word2: 210],
                           "\(fixture.table) learned scores should survive backup restore")
            restoredDatabase.closeCurrentForReplacement()
        }
    }

    @MainActor
    func testCloudIMLimedbBackupClearAndRestoreWorkflow() async throws {
        let h = try makeHarness(label: "limedb")
        defer { cleanup(h) }

        for fixture in cloudFixtures {
            try await importCloudIM(try cloudFixtureURL(fixture.fileName),
                                    table: fixture.table,
                                    controller: h.controller)
        }
        _ = h.engine.scanAndApply()
        let db = try XCTUnwrap(h.database.current())

        let table = "phonetic"
        let originalCount = try exportableRecordCount(db, table: table)
        XCTAssertGreaterThan(originalCount, 0)
        let exportServer = DBServer(_testDatasource: db)
        let targetBackupURL = tempRoot.appendingPathComponent("phonetic-\(UUID().uuidString).limedb")

        guard let backupURL = exportServer.exportZippedDb(tableName: table, targetDbFile: targetBackupURL) else {
            XCTFail("Expected .limedb export for \(table)")
            return
        }
        defer { try? FileManager.default.removeItem(at: backupURL) }
        XCTAssertTrue(FileManager.default.fileExists(atPath: backupURL.path))

        db.clearTable(table)
        XCTAssertEqual(db.countRecords(table, nil, nil), 0)

        try await importCloudIM(backupURL, table: table, controller: h.controller)
        XCTAssertTrue(h.engine.scanAndApply().contains(SyncEvent(kind: .imported, stem: table)))

        XCTAssertEqual(db.countRecords(table, nil, nil),
                       originalCount,
                       "\(table) .limedb backup should restore the installed cloud table")
    }

    @MainActor
    private struct Harness {
        let ownDir: URL
        let appGroupDir: URL
        let database: SharedDatabase
        let engine: TableSyncEngine
        let controller: LimeIME.SetupImController
    }

    @MainActor
    private func makeHarness(label: String = UUID().uuidString) throws -> Harness {
        let ownDir = tempRoot.appendingPathComponent("own-\(label)", isDirectory: true)
        let appGroupDir = tempRoot.appendingPathComponent("ag-\(label)", isDirectory: true)
        try FileManager.default.createDirectory(at: ownDir, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: appGroupDir, withIntermediateDirectories: true)
        let database = SharedDatabase(runMode: .keyboard,
                                      dataDirOverride: ownDir,
                                      appGroupOverride: appGroupDir)
        let engine = TableSyncEngine(database: database, baseURL: appGroupDir)
        let hot = try XCTUnwrap(database.current())
        let coldDatabase = SharedDatabase(runMode: .app, dataDirOverride: appGroupDir)
        let cold = try XCTUnwrap(coldDatabase.current())
        try cold.setSyncMeta("epoch_uuid", try hot.ensureEpochUUID())
        coldDatabase.closeCurrentForReplacement()
        let server = LimeIME.DBServer(_testDatabaseDirectory: appGroupDir)
        let suiteName = "test.integration.backup.restore.\(UUID().uuidString)"
        let prefs = LimeIME.LIMEPreferenceManager(defaults: UserDefaults(suiteName: suiteName)!)
        let controller = LimeIME.SetupImController(dbServer: server,
                                                   prefs: prefs,
                                                   progress: LimeIME.ProgressManager())
        return Harness(ownDir: ownDir,
                       appGroupDir: appGroupDir,
                       database: database,
                       engine: engine,
                       controller: controller)
    }

    @MainActor
    private func cleanup(_ h: Harness) {
        h.database.closeCurrentForReplacement()
    }

    @MainActor
    private func importCloudIM(_ url: URL,
                               table: String,
                               controller: LimeIME.SetupImController,
                               restoreLearning: Bool = false) async throws {
        let result = await controller.importDBFile(url: url,
                                                   tableName: table,
                                                   restoreLearning: restoreLearning)
        if case .failure(let error) = result {
            throw error
        }
    }

    private func learnedScores(_ db: LimeDB, table: String, code: String) -> [String: Int] {
        var scores: [String: Int] = [:]
        for record in db.getRecordList(table, code, searchByCode: true, 0, 0) where record.code == code {
            scores[record.word] = record.score
        }
        return scores
    }

    @MainActor
    private func backupThroughRelay(_ h: Harness) throws -> URL {
        let requestUUID = UUID().uuidString
        let request = ExportRequest(requestUUID: requestUUID,
                                    expiresAt: Date().timeIntervalSince1970 + 120)
        try atomicWrite(try JSONEncoder().encode(request), to: SyncPaths.exportRequest(h.appGroupDir))
        let events = h.engine.scanAndApply()
        XCTAssertTrue(events.contains(SyncEvent(kind: .exported, stem: nil)))
        let receipt = try JSONDecoder().decode(ExportReceipt.self,
                                               from: Data(contentsOf: SyncPaths.receipt(h.appGroupDir)))
        XCTAssertEqual(receipt.requestUUID, requestUUID)

        let zip = tempRoot.appendingPathComponent("backup-\(requestUUID).zip")
        let archive = try Archive(url: zip, accessMode: .create)
        try archive.addEntry(with: DBServer.databaseName, fileURL: SyncPaths.backupSnapshot(h.appGroupDir))
        try? FileManager.default.removeItem(at: SyncPaths.backupSnapshot(h.appGroupDir))
        try? FileManager.default.removeItem(at: SyncPaths.receipt(h.appGroupDir))
        return zip
    }

    private func exportableRecordCount(_ db: LimeDB, table: String) throws -> Int {
        try db.dbQueue.read { sqlDB in
            try Int.fetchOne(sqlDB,
                             sql: """
                             SELECT COUNT(*) FROM \(quote(table))
                             WHERE code IS NOT NULL AND word IS NOT NULL
                             """) ?? 0
        }
    }

    private func quote(_ identifier: String) -> String {
        "\"\(identifier.replacingOccurrences(of: "\"", with: "\"\""))\""
    }

    private func cloudFixtureURL(_ fileName: String) throws -> URL {
        let root = try repoRootURL()
        let url = root.appendingPathComponent("Database").appendingPathComponent(fileName)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Missing cloud fixture at \(url.path)")
        }
        return url
    }

    private func repoRootURL() throws -> URL {
        var url = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        for _ in 0..<8 {
            if FileManager.default.fileExists(atPath: url.appendingPathComponent("Database").path),
               FileManager.default.fileExists(atPath: url.appendingPathComponent("LimeIME-iOS").path) {
                return url
            }
            url.deleteLastPathComponent()
        }
        throw XCTSkip("Unable to locate repo root from \(#filePath)")
    }
}
