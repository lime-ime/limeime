import Foundation

import GRDB

struct ColdSnapshotMeta: Codable, Equatable {
    var generation: Int64
    var epochUUID: String
    var schemaVersion: Int
}

enum ColdPublisherError: Error {
    case datasourceUnavailable
}

final class ColdPublisher {
    private static let publishLock = NSLock()

    private let database: SharedDatabase
    private let baseURL: URL
    private let fm = FileManager.default

    init(database: SharedDatabase, baseURL: URL) {
        self.database = database
        self.baseURL = baseURL
    }

    @discardableResult
    func publish() throws -> ColdSnapshotMeta {
        Self.publishLock.lock()
        defer { Self.publishLock.unlock() }
        guard let db = database.current() else { throw ColdPublisherError.datasourceUnavailable }

        try db.dbQueue.write { sqlDB in
            _ = try db.ensureEpochUUID(in: sqlDB)
            try db.bumpColdGeneration(in: sqlDB)
        }

        let tmpParent = baseURL.appendingPathComponent(".tmp", isDirectory: true)
        let tmpDir = tmpParent.appendingPathComponent("cold-publisher-\(UUID().uuidString)", isDirectory: true)
        let tmpDB = tmpDir.appendingPathComponent("cold.limedb")
        try fm.createDirectory(at: tmpDir, withIntermediateDirectories: true)
        defer {
            try? fm.removeItem(at: tmpDir)
            if let names = try? fm.contentsOfDirectory(atPath: tmpParent.path), names.isEmpty {
                try? fm.removeItem(at: tmpParent)
            }
        }

        try db.vacuumInto(tmpDB.path)
        let meta = try snapshotMeta(from: tmpDB)
        try replaceFile(from: tmpDB, to: SyncPaths.coldDB(baseURL))
        try atomicWrite(try JSONEncoder().encode(meta), to: SyncPaths.coldMeta(baseURL))
        postSyncSignal(.tablesUpdated)
        return meta
    }

    private func snapshotMeta(from url: URL) throws -> ColdSnapshotMeta {
        var config = Configuration()
        config.readonly = true
        let queue = try DatabaseQueue(path: url.path, configuration: config)
        defer { try? queue.close() }
        return try queue.read { db in
            let generationRaw = try String.fetchOne(db,
                sql: "SELECT value FROM sync_meta WHERE key = 'generation'")
            let epoch = try String.fetchOne(db,
                sql: "SELECT value FROM sync_meta WHERE key = 'epoch_uuid'") ?? ""
            let schemaRaw = try String.fetchOne(db,
                sql: "SELECT value FROM sync_meta WHERE key = 'schema_version'")
            return ColdSnapshotMeta(generation: Int64(generationRaw ?? "") ?? 0,
                                    epochUUID: epoch,
                                    schemaVersion: Int(schemaRaw ?? "") ?? LimeDB.CURRENT_DB_VERSION)
        }
    }

    private func replaceFile(from source: URL, to destination: URL) throws {
        try fm.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
        if fm.fileExists(atPath: destination.path) {
            _ = try fm.replaceItemAt(destination, withItemAt: source)
        } else {
            try fm.moveItem(at: source, to: destination)
        }
    }
}
