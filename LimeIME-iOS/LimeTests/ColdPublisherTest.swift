import XCTest
import GRDB
@testable import LimeIME

final class ColdPublisherTest: XCTestCase {
    private var baseURL: URL!
    private let fm = FileManager.default

    override func setUpWithError() throws {
        try super.setUpWithError()
        baseURL = fm.temporaryDirectory
            .appendingPathComponent("cold-publisher-\(UUID().uuidString)", isDirectory: true)
        try fm.createDirectory(at: baseURL, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? fm.removeItem(at: baseURL)
        try super.tearDownWithError()
    }

    func testPublishWritesSnapshotSidecarAndMonotonicGeneration() throws {
        let liveDB = try LimeDB(path: baseURL.appendingPathComponent("lime.db").path)
        defer { try? liveDB.closeForReplacement() }
        let shared = SharedDatabase(runMode: .app, dataDirOverride: baseURL, datasource: liveDB)
        let publisher = ColdPublisher(database: shared, baseURL: baseURL)

        let first = try publisher.publish()

        XCTAssertTrue(fm.fileExists(atPath: SyncPaths.coldDB(baseURL).path))
        XCTAssertEqual(try quickCheck(SyncPaths.coldDB(baseURL)), "ok")
        XCTAssertNoColdSidecars()
        XCTAssertEqual(try readSidecar(), first)
        XCTAssertEqual(try readSnapshotMeta(), first)
        XCTAssertEqual(try readSyncMetaInt64("generation", in: SyncPaths.coldDB(baseURL)), first.generation)
        XCTAssertEqual(liveDB.coldGeneration(), first.generation)
        XCTAssertEqual(first.epochUUID, liveDB.syncMeta("epoch_uuid"))
        XCTAssertEqual(first.schemaVersion, LimeDB.CURRENT_DB_VERSION)

        XCTAssertGreaterThan(liveDB.addRecord("custom", ["code": "b", "word": "乙"]), 0)
        let second = try publisher.publish()

        XCTAssertEqual(second.generation, first.generation + 1)
        XCTAssertEqual(try readSidecar(), second)
        XCTAssertEqual(try readSnapshotMeta(), second)
        XCTAssertEqual(try readSyncMetaInt64("generation", in: SyncPaths.coldDB(baseURL)), second.generation)
        XCTAssertEqual(try readInt("SELECT COUNT(*) FROM custom", in: SyncPaths.coldDB(baseURL)), 1)
        XCTAssertEqual(liveDB.coldGeneration(), second.generation)
        XCTAssertNoColdSidecars()
        XCTAssertTrue(try tmpResidueNames().isEmpty)
    }

    func testTornPublishGenerationMismatchIsReadable() throws {
        let snapshotSource = try LimeDB(path: baseURL.appendingPathComponent("snapshot-source.db").path)
        defer { try? snapshotSource.closeForReplacement() }
        let epoch = try snapshotSource.ensureEpochUUID()
        try snapshotSource.dbQueue.write { db in
            try db.execute(sql: "INSERT OR REPLACE INTO sync_meta (key, value) VALUES ('generation', '41')")
        }
        try snapshotSource.vacuumInto(SyncPaths.coldDB(baseURL).path)
        try atomicWrite(try JSONEncoder().encode(
            ColdSnapshotMeta(generation: 42, epochUUID: epoch, schemaVersion: LimeDB.CURRENT_DB_VERSION)
        ), to: SyncPaths.coldMeta(baseURL))

        let sidecar = try readSidecar()
        let snapshotGeneration = try readSyncMetaInt64("generation", in: SyncPaths.coldDB(baseURL))

        XCTAssertEqual(snapshotGeneration, 41)
        XCTAssertEqual(sidecar.generation, 42)
        XCTAssertNotEqual(snapshotGeneration, sidecar.generation)
    }

    private func readSidecar() throws -> ColdSnapshotMeta {
        try JSONDecoder().decode(ColdSnapshotMeta.self,
                                 from: Data(contentsOf: SyncPaths.coldMeta(baseURL)))
    }

    private func readSnapshotMeta() throws -> ColdSnapshotMeta {
        let url = SyncPaths.coldDB(baseURL)
        return try readOnlyQueue(url).read { db in
            let generation = try String.fetchOne(db,
                sql: "SELECT value FROM sync_meta WHERE key = 'generation'") ?? ""
            let epoch = try String.fetchOne(db,
                sql: "SELECT value FROM sync_meta WHERE key = 'epoch_uuid'") ?? ""
            let schema = try String.fetchOne(db,
                sql: "SELECT value FROM sync_meta WHERE key = 'schema_version'") ?? ""
            return ColdSnapshotMeta(generation: Int64(generation) ?? -1,
                                    epochUUID: epoch,
                                    schemaVersion: Int(schema) ?? -1)
        }
    }

    private func quickCheck(_ url: URL) throws -> String {
        try readOnlyQueue(url).read { db in
            try String.fetchOne(db, sql: "PRAGMA quick_check") ?? ""
        }
    }

    private func readSyncMetaInt64(_ key: String, in url: URL) throws -> Int64 {
        let value = try readOnlyQueue(url).read { db in
            try String.fetchOne(db, sql: "SELECT value FROM sync_meta WHERE key = ?", arguments: [key])
        }
        return Int64(value ?? "") ?? -1
    }

    private func readInt(_ sql: String, in url: URL) throws -> Int {
        try readOnlyQueue(url).read { db in
            try Int.fetchOne(db, sql: sql) ?? 0
        }
    }

    private func readOnlyQueue(_ url: URL) throws -> DatabaseQueue {
        var config = Configuration()
        config.readonly = true
        return try DatabaseQueue(path: url.path, configuration: config)
    }

    private func XCTAssertNoColdSidecars(file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertFalse(fm.fileExists(atPath: SyncPaths.coldDB(baseURL).path + "-wal"), file: file, line: line)
        XCTAssertFalse(fm.fileExists(atPath: SyncPaths.coldDB(baseURL).path + "-shm"), file: file, line: line)
    }

    private func tmpResidueNames() throws -> [String] {
        try fm.contentsOfDirectory(atPath: baseURL.path).filter {
            $0 == ".tmp" || $0.hasSuffix(".tmp") || $0.contains(".tmp-")
        }
    }
}
