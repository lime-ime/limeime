import XCTest
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

    private var tempURL: URL!

    override func setUp() {
        super.setUp()
        tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: tempURL)
        super.tearDown()
    }

    @MainActor
    func testCloudIMInstallBackupAndRestoreLearningThroughSettingsImport() async throws {
        let (db, controller) = try makeHarness()

        for fixture in cloudFixtures {
            let cloudZip = try cloudFixtureURL(fixture.fileName)
            try await importCloudIM(cloudZip, table: fixture.table, controller: controller)
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

            db.backupUserRecords(fixture.table)
            XCTAssertTrue(db.checkBackupTable(fixture.table),
                          "\(fixture.table) should have a learned-record backup")

            try await importCloudIM(cloudZip,
                                    table: fixture.table,
                                    controller: controller,
                                    restoreLearning: true)

            XCTAssertEqual(learnedScores(db, table: fixture.table, code: code),
                           [word1: 220, word2: 210],
                           "\(fixture.table) learned scores should survive cloud re-import")
        }
    }

    @MainActor
    func testCloudIMLimedbBackupClearAndRestoreWorkflow() async throws {
        let (db, controller) = try makeHarness()

        for fixture in cloudFixtures {
            try await importCloudIM(try cloudFixtureURL(fixture.fileName),
                                    table: fixture.table,
                                    controller: controller)
        }

        let table = "phonetic"
        let originalCount = db.countRecords(table, nil, nil)
        XCTAssertGreaterThan(originalCount, 0)

        guard let backupURL = await controller.exportIMAsLimedb(tableNick: table) else {
            XCTFail("Expected .limedb export for \(table)")
            return
        }
        defer { try? FileManager.default.removeItem(at: backupURL) }
        XCTAssertTrue(FileManager.default.fileExists(atPath: backupURL.path))

        db.clearTable(table)
        XCTAssertEqual(db.countRecords(table, nil, nil), 0)

        try await importCloudIM(backupURL, table: table, controller: controller)

        XCTAssertEqual(db.countRecords(table, nil, nil),
                       originalCount,
                       "\(table) .limedb backup should restore the installed cloud table")
    }

    @MainActor
    private func makeHarness() throws -> (LimeIME.LimeDB, LimeIME.SetupImController) {
        let db = try LimeIME.LimeDB(path: tempURL.path)
        _ = db.openDBConnection(false)
        let server = LimeIME.DBServer(_testDatasource: db)
        let suiteName = "test.integration.backup.restore.\(UUID().uuidString)"
        let prefs = LimeIME.LIMEPreferenceManager(defaults: UserDefaults(suiteName: suiteName)!)
        let controller = LimeIME.SetupImController(dbServer: server,
                                                   prefs: prefs,
                                                   progress: LimeIME.ProgressManager())
        return (db, controller)
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

    private func learnedScores(_ db: LimeIME.LimeDB, table: String, code: String) -> [String: Int] {
        var scores: [String: Int] = [:]
        for record in db.getRecordList(table, code, searchByCode: true, 0, 0) where record.code == code {
            scores[record.word] = record.score
        }
        return scores
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
