import Foundation
import GRDB
import ZIPFoundation

enum TableStoreError: Error, Equatable {
    case invalidDatabase(String)
    case parseFailed(String)
    case unsupportedFormat(String)
    case schemaTooNew(Int)
}

final class TableStore {
    let baseURL: URL

    private let fm = FileManager.default

    init(baseURL: URL) {
        self.baseURL = baseURL
    }

    func installLimedb(from url: URL, stem: String, meta: TableMeta?) throws {
        try validateLimedb(at: url, stem: stem)
        try deliverLimedb(from: url, stem: stem, meta: meta)
    }

    func installText(from url: URL, stem: String, meta: TableMeta?) throws {
        let ext = url.pathExtension.lowercased()
        guard ext == "cin" || ext == "lime" else {
            throw TableStoreError.unsupportedFormat(url.lastPathComponent)
        }

        let temp = try scratchFile(extension: "limedb")
        var limeDB: LimeDB?
        defer {
            limeDB = nil
            removeSQLiteFiles(at: temp)
        }

        do {
            let db = try LimeDB(path: temp.path)
            limeDB = db
            try db.importTxtFile(at: url.path, tableName: stem)
            let count = try db.dbQueue.read { sqlDB in
                try rowCount(in: sqlDB, table: stem)
            }
            guard count > 0 else {
                throw TableStoreError.parseFailed("0 rows")
            }
            try checkpoint(db.dbQueue)
        } catch let error as TableStoreError {
            throw error
        } catch {
            throw TableStoreError.parseFailed(error.localizedDescription)
        }

        limeDB = nil
        try installLimedb(from: temp, stem: stem, meta: meta)
    }

    func installFromZip(from zipURL: URL, stem: String, meta: TableMeta?) throws {
        let archive: Archive
        do {
            archive = try Archive(url: zipURL, accessMode: .read)
        } catch {
            throw TableStoreError.unsupportedFormat(error.localizedDescription)
        }

        guard let entry = archive.first(where: { entry in
            guard entry.type != .directory else { return false }
            let lower = entry.path.lowercased()
            return lower.hasSuffix(".limedb") || lower.hasSuffix(".cin") || lower.hasSuffix(".lime")
        }) else {
            throw TableStoreError.unsupportedFormat(zipURL.lastPathComponent)
        }

        let dir = try scratchDirectory()
        defer { try? fm.removeItem(at: dir) }
        let name = URL(fileURLWithPath: entry.path).lastPathComponent
        let extracted = dir.appendingPathComponent(name)
        _ = try archive.extract(entry, to: extracted, skipCRC32: false)

        switch extracted.pathExtension.lowercased() {
        case "limedb":
            try installLimedb(from: extracted, stem: stem, meta: meta)
        case "cin", "lime":
            try installText(from: extracted, stem: stem, meta: meta)
        default:
            throw TableStoreError.unsupportedFormat(entry.path)
        }
    }

    func uninstall(stem: String) throws {
        for url in [SyncPaths.tableFile(baseURL, stem: stem), SyncPaths.tableMeta(baseURL, stem: stem)] {
            if fm.fileExists(atPath: url.path) {
                try fm.removeItem(at: url)
            }
        }
    }

    func installedStems() -> [String] {
        let dir = SyncPaths.tablesDir(baseURL)
        guard let urls = try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) else {
            return []
        }
        return urls
            .filter { $0.pathExtension.lowercased() == "limedb" }
            .map { $0.deletingPathExtension().lastPathComponent }
            .sorted()
    }

    @discardableResult
    func prepareRestore(from dbURL: URL) throws -> String {
        let temp = try scratchFile(extension: "limedb")
        defer { removeSQLiteFiles(at: temp) }
        try fm.copyItem(at: dbURL, to: temp)

        let epoch = UUID().uuidString
        var queue: DatabaseQueue?
        do {
            let dbQueue = try DatabaseQueue(path: temp.path)
            queue = dbQueue
            try dbQueue.read { db in
                let quickCheck = try String.fetchOne(db, sql: "PRAGMA quick_check")
                guard quickCheck == "ok" else {
                    throw TableStoreError.invalidDatabase(quickCheck ?? "quick_check failed")
                }
                if let version = try schemaVersion(in: db),
                   version > LimeDB.CURRENT_DB_VERSION {
                    throw TableStoreError.schemaTooNew(version)
                }
            }
            try dbQueue.write { db in
                try db.execute(sql: "CREATE TABLE IF NOT EXISTS sync_meta (key TEXT PRIMARY KEY, value TEXT)")
                try db.execute(sql: "INSERT OR REPLACE INTO sync_meta (key, value) VALUES ('epoch_uuid', ?)",
                               arguments: [epoch])
                try db.execute(sql: "INSERT OR REPLACE INTO sync_meta (key, value) VALUES ('schema_version', ?)",
                               arguments: ["\(LimeDB.CURRENT_DB_VERSION)"])
                if try tableExists("sync_ledger", in: db) {
                    try db.execute(sql: "DELETE FROM sync_ledger")
                }
            }
            try checkpoint(dbQueue)
        } catch let error as TableStoreError {
            queue = nil
            throw error
        } catch {
            queue = nil
            throw TableStoreError.invalidDatabase(error.localizedDescription)
        }
        queue = nil

        // Deliver the restore file BEFORE clearing sources: if this ordering were
        // reversed and the copy failed, the folder would be left empty with the old
        // epoch and the next keyboard scan would drop every table. With restore-first,
        // a partial clearAllSources merely re-imports stale sources on top of the new
        // baseline — convergent, no data loss.
        try replaceFile(from: temp, to: SyncPaths.restoreDB(baseURL))
        let meta = RestoreMeta(epochUUID: epoch, schemaVersion: LimeDB.CURRENT_DB_VERSION)
        try atomicWrite(JSONEncoder().encode(meta), to: SyncPaths.restoreMeta(baseURL))
        try clearAllSources()
        return epoch
    }

    func clearAllSources() throws {
        let dir = SyncPaths.tablesDir(baseURL)
        guard fm.fileExists(atPath: dir.path) else { return }
        for url in try fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) {
            try fm.removeItem(at: url)
        }
    }

    private func validateLimedb(at url: URL, stem: String) throws {
        do {
            var config = Configuration()
            config.readonly = true
            let queue = try DatabaseQueue(path: url.path, configuration: config)
            try queue.read { db in
                let quickCheck = try String.fetchOne(db, sql: "PRAGMA quick_check")
                guard quickCheck == "ok" else {
                    throw TableStoreError.invalidDatabase(quickCheck ?? "quick_check failed")
                }
                guard try tableExists(stem, in: db) else {
                    throw TableStoreError.invalidDatabase("missing table \(stem)")
                }
                guard try rowCount(in: db, table: stem) > 0 else {
                    throw TableStoreError.invalidDatabase("empty table \(stem)")
                }
            }
        } catch let error as TableStoreError {
            throw error
        } catch {
            throw TableStoreError.invalidDatabase(error.localizedDescription)
        }
    }

    private func deliverLimedb(from url: URL, stem: String, meta: TableMeta?) throws {
        try replaceFile(from: url, to: SyncPaths.tableFile(baseURL, stem: stem))
        let metaURL = SyncPaths.tableMeta(baseURL, stem: stem)
        if let meta {
            try atomicWrite(JSONEncoder().encode(meta), to: metaURL)
        } else if fm.fileExists(atPath: metaURL.path) {
            try fm.removeItem(at: metaURL)
        }
    }

    private func replaceFile(from source: URL, to destination: URL) throws {
        let parent = destination.deletingLastPathComponent()
        try fm.createDirectory(at: parent, withIntermediateDirectories: true)
        let temp = parent.appendingPathComponent("\(destination.lastPathComponent).tmp-\(UUID().uuidString)")
        defer { try? fm.removeItem(at: temp) }

        try fm.copyItem(at: source, to: temp)
        if fm.fileExists(atPath: destination.path) {
            _ = try fm.replaceItemAt(destination, withItemAt: temp)
        } else {
            try fm.moveItem(at: temp, to: destination)
        }
    }

    private func scratchFile(extension ext: String) throws -> URL {
        let dir = baseURL.appendingPathComponent(".tmp", isDirectory: true)
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("\(UUID().uuidString).\(ext)")
    }

    private func scratchDirectory() throws -> URL {
        let dir = baseURL
            .appendingPathComponent(".tmp", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func removeSQLiteFiles(at url: URL) {
        try? fm.removeItem(at: url)
        try? fm.removeItem(at: URL(fileURLWithPath: url.path + "-wal"))
        try? fm.removeItem(at: URL(fileURLWithPath: url.path + "-shm"))
    }

    private func checkpoint(_ queue: DatabaseQueue) throws {
        try queue.writeWithoutTransaction { db in
            try db.execute(sql: "PRAGMA wal_checkpoint(TRUNCATE)")
        }
    }

    private func schemaVersion(in db: Database) throws -> Int? {
        guard try tableExists("sync_meta", in: db),
              let raw = try String.fetchOne(db,
                                            sql: "SELECT value FROM sync_meta WHERE key = 'schema_version'")
        else {
            return nil
        }
        return Int(raw)
    }

    private func tableExists(_ table: String, in db: Database) throws -> Bool {
        try (Int.fetchOne(db,
                          sql: "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                          arguments: [table]) ?? 0) > 0
    }

    private func rowCount(in db: Database, table: String) throws -> Int {
        try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(quote(table))") ?? 0
    }

    private func quote(_ identifier: String) -> String {
        "\"\(identifier.replacingOccurrences(of: "\"", with: "\"\""))\""
    }
}
