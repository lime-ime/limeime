import Foundation
import GRDB

final class TableSyncEngine {
    private let appGroupBaseURL: URL
    private let hotDatabaseURL: URL
    private let dbServer: DBServer

    init(appGroupBaseURL: URL,
         hotDatabaseURL: URL,
         dbServer: DBServer = .shared) {
        self.appGroupBaseURL = appGroupBaseURL
        self.hotDatabaseURL = hotDatabaseURL
        self.dbServer = dbServer
    }

    convenience init(locator: SyncDatabaseLocator = .production(),
                     dbServer: DBServer = .shared) {
        self.init(appGroupBaseURL: locator.appGroupDirectory,
                  hotDatabaseURL: locator.hotDatabaseURL,
                  dbServer: dbServer)
    }

    func scanAndApply() throws {
        let coldSnapshotURL = SyncPaths.coldDB(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: coldSnapshotURL.path) else { return }

        let coldMeta = try SyncMetaStore(databaseURL: coldSnapshotURL)
        let hotMeta = try SyncMetaStore(databaseURL: hotDatabaseURL)
        let coldEpoch = try coldMeta.epochUUID()
        let coldGeneration = try coldMeta.generation()
        let appliedEpoch = try hotMeta.appliedEpoch()
        let appliedGeneration = try hotMeta.appliedGeneration()

        if coldGeneration == appliedGeneration, coldEpoch == appliedEpoch {
            return
        }

        if coldEpoch != appliedEpoch {
            try dbServer.replaceDatabaseFromSnapshot(coldSnapshotURL)
            let refreshedHotMeta = try SyncMetaStore(databaseURL: hotDatabaseURL)
            if let coldEpoch {
                try refreshedHotMeta.setAppliedEpoch(coldEpoch)
            } else {
                try refreshedHotMeta.removeValue(forKey: SyncMetaStore.appliedEpochKey)
            }
            try refreshedHotMeta.setAppliedGeneration(coldGeneration)
            return
        }

        try applyIncremental(from: coldSnapshotURL)
        if let coldEpoch {
            try hotMeta.setAppliedEpoch(coldEpoch)
        }
        try hotMeta.setAppliedGeneration(coldGeneration)
    }

    private func applyIncremental(from coldSnapshotURL: URL) throws {
        let coldRevisions = try revisions(in: coldSnapshotURL)
        let hotRevisions = try revisions(in: hotDatabaseURL)
        let tables = Set(coldRevisions.keys).union(hotRevisions.keys).sorted()
        guard !tables.isEmpty else { return }

        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)
        try connection.write { db in
            try db.execute(sql: "ATTACH DATABASE ? AS cold_snapshot",
                           arguments: [coldSnapshotURL.path])
            defer { try? db.execute(sql: "DETACH DATABASE cold_snapshot") }

            for table in tables where Self.isSafeTableName(table) {
                guard let coldRevision = coldRevisions[table] else {
                    try Self.drop(table, in: db)
                    try Self.deleteMeta("rev:\(table)", in: db)
                    continue
                }
                guard coldRevision != hotRevisions[table] else { continue }
                guard try Self.tableExists(table, schema: "cold_snapshot", in: db) else {
                    try Self.drop(table, in: db)
                    try Self.deleteMeta("rev:\(table)", in: db)
                    continue
                }
                try Self.copy(table, fromSchema: "cold_snapshot", in: db)
                try Self.upsertMeta("rev:\(table)", value: String(coldRevision), in: db)
            }
        }
    }

    private func revisions(in databaseURL: URL) throws -> [String: Int] {
        _ = try SyncMetaStore(databaseURL: databaseURL)
        let connection = try SyncDatabaseConnection(databaseURL: databaseURL)
        return try connection.read { db in
            let rows = try Row.fetchAll(db, sql: """
                SELECT key, value FROM sync_meta WHERE key LIKE 'rev:%'
                """)
            var revisions: [String: Int] = [:]
            for row in rows {
                guard let key = row["key"] as String?,
                      let value = Int(row["value"] as String? ?? ""),
                      key.hasPrefix("rev:")
                else { continue }
                revisions[String(key.dropFirst(4))] = value
            }
            return revisions
        }
    }

    private static func copy(_ table: String, fromSchema schema: String, in db: Database) throws {
        let tableName = quotedIdentifier(table)
        if try !tableExists(table, in: db),
           let createSQL = try String.fetchOne(db, sql: """
                SELECT sql FROM \(quotedIdentifier(schema)).sqlite_master
                WHERE type='table' AND name=?
                """, arguments: [table]) {
            try db.execute(sql: createSQL)
        }

        let columns = try Row.fetchAll(db, sql: """
            PRAGMA \(quotedIdentifier(schema)).table_info(\(tableName))
            """).compactMap { $0["name"] as String? }
        guard !columns.isEmpty else { return }
        let columnList = columns.map(quotedIdentifier).joined(separator: ", ")

        try db.execute(sql: "DELETE FROM \(tableName)")
        try db.execute(sql: """
            INSERT OR REPLACE INTO \(tableName) (\(columnList))
            SELECT \(columnList) FROM \(quotedIdentifier(schema)).\(tableName)
            """)
    }

    private static func drop(_ table: String, in db: Database) throws {
        try db.execute(sql: "DROP TABLE IF EXISTS \(quotedIdentifier(table))")
    }

    private static func tableExists(_ table: String, schema: String? = nil, in db: Database) throws -> Bool {
        let master = schema.map { "\(quotedIdentifier($0)).sqlite_master" } ?? "sqlite_master"
        return try (Int.fetchOne(db, sql: """
            SELECT COUNT(*) FROM \(master) WHERE type='table' AND name=?
            """, arguments: [table]) ?? 0) > 0
    }

    private static func upsertMeta(_ key: String, value: String, in db: Database) throws {
        try db.execute(sql: """
            INSERT INTO sync_meta(key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """, arguments: [key, value])
    }

    private static func deleteMeta(_ key: String, in db: Database) throws {
        try db.execute(sql: "DELETE FROM sync_meta WHERE key = ?", arguments: [key])
    }

    private static func quotedIdentifier(_ identifier: String) -> String {
        "\"\(identifier.replacingOccurrences(of: "\"", with: "\"\""))\""
    }

    private static func isSafeTableName(_ table: String) -> Bool {
        guard table != "sync_meta", !table.hasPrefix("sqlite_") else { return false }
        return table.range(of: #"^[A-Za-z][A-Za-z0-9_]*$"#,
                           options: .regularExpression) != nil
    }
}
