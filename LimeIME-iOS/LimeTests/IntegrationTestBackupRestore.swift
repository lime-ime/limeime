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

import GRDB
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

    private var tempDir: URL!
    private var tempURL: URL!
    private var hotURL: URL!

    override func setUp() {
        super.setUp()
        tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        tempURL = tempDir.appendingPathComponent("lime.db")
        hotURL = tempDir.appendingPathComponent("hot/lime.db")
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: tempDir)
        super.tearDown()
    }

    @MainActor
    func testCloudIMInstallBackupAndRestoreLearningThroughSettingsImport() async throws {
        let (db, controller, server) = try makeHarness()

        for fixture in cloudFixtures {
            let cloudZip = try cloudFixtureURL(fixture.fileName)
            try await importCloudIM(cloudZip, table: fixture.table, controller: controller)
            try markProtocolReadyAndPublish(server: server)
            try prepareHotDatabaseForMarkedReconcile(table: fixture.table)
            let engine = TableSyncEngine(appGroupBaseURL: tempDir, hotDatabaseURL: hotURL)
            try engine.scanAndApply(hasFullAccess: false)
            XCTAssertGreaterThan(db.countRecords(fixture.table, nil, nil),
                                 0,
                                 "\(fixture.table) cloud fixture should install records")

            let code = "ios_backup_pair_\(fixture.table)"
            let word1 = "備份對\(fixture.table)"
            let word2 = "還原對\(fixture.table)"
            let hotDB = try LimeIME.LimeDB(path: hotURL.path, tracksHotLearning: true)
            hotDB.addOrUpdateMappingRecord(fixture.table, code, word1, 220)
            hotDB.addOrUpdateMappingRecord(fixture.table, code, word2, 210)
            XCTAssertEqual(learnedScores(hotDB, table: fixture.table, code: code),
                           [word1: 220, word2: 210])

            try server.performTableLifecycleMutation(.delete(table: fixture.table,
                                                             preserveLearning: true,
                                                             publishImmediately: true))

            try await importCloudIM(cloudZip,
                                    table: fixture.table,
                                    controller: controller,
                                    restoreLearning: true)
            try engine.scanAndApply(hasFullAccess: false)
            XCTAssertEqual(learnedScores(hotDB, table: fixture.table, code: code),
                           [word1: 220, word2: 210],
                           "\(fixture.table) learned scores should be restored into hot before flush")
            try engine.flushPendingLearning(hasFullAccess: true)

            XCTAssertEqual(learnedScores(db, table: fixture.table, code: code),
                           [word1: 220, word2: 210],
                           "\(fixture.table) learned scores should survive via hot reconcile and flush")
        }
    }

    @MainActor
    func testCloudIMLimedbBackupClearAndRestoreWorkflow() async throws {
        let (db, controller, _) = try makeHarness()

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
    private func makeHarness() throws -> (LimeIME.LimeDB, LimeIME.SetupImController, LimeIME.DBServer) {
        let db = try LimeIME.LimeDB(path: tempURL.path)
        _ = db.openDBConnection(false)
        let server = LimeIME.DBServer(_testDatasource: db)
        let suiteName = "test.integration.backup.restore.\(UUID().uuidString)"
        let prefs = LimeIME.LIMEPreferenceManager(defaults: UserDefaults(suiteName: suiteName)!)
        let controller = LimeIME.SetupImController(dbServer: server,
                                                   prefs: prefs,
                                                   progress: LimeIME.ProgressManager())
        return (db, controller, server)
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

    private func markProtocolReadyAndPublish(server: LimeIME.DBServer) throws {
        let queue = try DatabaseQueue(path: tempURL.path)
        defer { try? queue.close() }
        try queue.write { db in
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS sync_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
                """)
            try db.execute(sql: """
                INSERT INTO sync_meta(key, value) VALUES ('epoch_uuid', 'epoch-a')
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)
            try db.execute(sql: """
                INSERT INTO sync_meta(key, value) VALUES ('editor_fence_protocol', '1')
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)
        }
        try ColdPublisher(liveColdDatabaseURL: tempURL, appGroupBaseURL: tempDir).publish()
        server.publishImJson()
    }

    private func prepareHotDatabaseForMarkedReconcile(table: String) throws {
        try FileManager.default.createDirectory(at: hotURL.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        _ = try LimeIME.LimeDB(path: hotURL.path, tracksHotLearning: true)
        let queue = try DatabaseQueue(path: hotURL.path)
        defer { try? queue.close() }
        try queue.write { db in
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS sync_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
                """)
            for (key, value) in [
                ("epoch_uuid", "epoch-a"),
                ("applied_epoch", "epoch-a"),
                ("legacy_transition_done", "1"),
                ("rev:\(table)", "0"),
            ] {
                try db.execute(sql: """
                    INSERT INTO sync_meta(key, value) VALUES (?, ?)
                    ON CONFLICT(key) DO UPDATE SET value = excluded.value
                    """, arguments: [key, value])
            }
        }
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
