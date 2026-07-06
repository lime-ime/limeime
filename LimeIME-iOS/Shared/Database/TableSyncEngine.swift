import Foundation
import GRDB
import Darwin

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

    /// Returns `true` when a cold→hot change was applied to hot's IM data (full replace
    /// or incremental import) — the caller reloads the keyboard's IM list only then.
    @discardableResult
    func scanAndApply() throws -> Bool {
        try processBackupExportRequestIfNeeded()
        try processEditorRefreshRequestIfNeeded()

        let coldSnapshotURL = SyncPaths.coldDB(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: coldSnapshotURL.path) else {
            try drainIMInboxIfNeeded()
            return false
        }

        let coldMeta = try SyncMetaStore(databaseURL: coldSnapshotURL)
        let hotMeta = try SyncMetaStore(databaseURL: hotDatabaseURL)
        let coldEpoch = try coldMeta.epochUUID()
        let coldGeneration = try coldMeta.generation()
        let appliedEpoch = try hotMeta.appliedEpoch()
        let appliedGeneration = try hotMeta.appliedGeneration()

        if coldGeneration == appliedGeneration, coldEpoch == appliedEpoch {
            try drainIMInboxIfNeeded()
            return false
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
            try clearIMInboxIfNeeded()
            try clearIMLifecycleInboxIfNeeded()
            return true
        }

        try applyIncremental(from: coldSnapshotURL)
        if let coldEpoch {
            try hotMeta.setAppliedEpoch(coldEpoch)
        }
        try hotMeta.setAppliedGeneration(coldGeneration)
        try drainIMInboxIfNeeded()
        return true
    }

    private func processBackupExportRequestIfNeeded() throws {
        let requestURL = SyncPaths.exportRequest(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: requestURL.path) else { return }
        defer { try? FileManager.default.removeItem(at: requestURL) }

        let request = try JSONDecoder().decode(ExportRequest.self,
                                               from: Data(contentsOf: requestURL))
        guard request.expiresAt >= Date().timeIntervalSince1970 else { return }

        let snapshotURL = SyncPaths.backupSnapshot(appGroupBaseURL)
        let tempURL = SyncPaths.outboxDir(appGroupBaseURL)
            .appendingPathComponent(".backup.\(UUID().uuidString).limedb.tmp")
        defer { try? FileManager.default.removeItem(at: tempURL) }

        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)
        try connection.writeWithoutTransaction { db in
            try db.execute(sql: "VACUUM INTO ?", arguments: [tempURL.path])
        }
        try Self.renameReplacing(tempURL, with: snapshotURL)

        let epoch = try SyncMetaStore(databaseURL: hotDatabaseURL).epochUUID() ?? ""
        let receipt = ExportReceipt(requestUUID: request.requestUUID,
                                    epochUUID: epoch,
                                    at: Date().timeIntervalSince1970)
        try atomicWrite(try JSONEncoder().encode(receipt),
                        to: SyncPaths.receipt(appGroupBaseURL))
    }

    private func processEditorRefreshRequestIfNeeded() throws {
        let requestURL = SyncPaths.editorRefreshRequest(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: requestURL.path) else { return }
        defer { try? FileManager.default.removeItem(at: requestURL) }

        let request = try JSONDecoder().decode(EditorRefreshRequest.self,
                                               from: Data(contentsOf: requestURL))
        do {
            guard request.expiresAt >= Date().timeIntervalSince1970 else {
                throw TableSyncEngineError.editorRefreshExpired
            }
            guard Self.isSafeTableName(request.table) else {
                throw TableSyncEngineError.unsafeTableName(request.table)
            }
            let liveColdURL = appGroupBaseURL.appendingPathComponent(SyncDatabaseLocator.databaseName)
            guard FileManager.default.fileExists(atPath: liveColdURL.path) else {
                throw TableSyncEngineError.liveColdMissing
            }
            try harvestEditorRefresh(table: request.table, into: liveColdURL)
            try writeEditorRefreshReceipt(for: request, status: .done, error: nil)
            postSyncSignal(.importDone)
        } catch {
            try? writeEditorRefreshReceipt(for: request,
                                           status: .failed,
                                           error: error.localizedDescription)
            postSyncSignal(.importFailed)
        }
    }

    private func harvestEditorRefresh(table: String, into coldDatabaseURL: URL) throws {
        let keyColumns = Self.editorRefreshKeyColumns(for: table)
        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)
        try connection.write { db in
            try db.execute(sql: "ATTACH DATABASE ? AS cold_editor",
                           arguments: [coldDatabaseURL.path])
            defer {
                try? db.execute(sql: "DROP TABLE IF EXISTS temp.editor_refresh_dirty_keys")
                try? db.execute(sql: "DETACH DATABASE cold_editor")
            }

            guard try Self.tableExists(table, in: db),
                  try Self.tableExists(table, schema: "cold_editor", in: db)
            else {
                throw TableSyncEngineError.tableMissing(table)
            }

            let hotColumns = Set(try Self.columns(in: table, schema: nil, db: db))
            let coldColumns = try Self.columns(in: table, schema: "cold_editor", db: db)
            guard keyColumns.allSatisfy({ hotColumns.contains($0) && coldColumns.contains($0) }),
                  hotColumns.contains("score"),
                  coldColumns.contains("score")
            else {
                throw TableSyncEngineError.unsupportedEditorTable(table)
            }

            let copyColumns = coldColumns.filter { $0 != "_id" && hotColumns.contains($0) }
            guard !copyColumns.isEmpty else { return }

            let tableName = Self.quotedIdentifier(table)
            let keyList = keyColumns.map(Self.quotedIdentifier).joined(separator: ", ")
            let keyDefinitions = keyColumns
                .map { "\(Self.quotedIdentifier($0)) TEXT" }
                .joined(separator: ", ")
            let hotColdJoin = keyColumns
                .map { "cold.\(Self.quotedIdentifier($0)) IS hot.\(Self.quotedIdentifier($0))" }
                .joined(separator: " AND ")
            let dirtyHotJoin = keyColumns
                .map { "hot.\(Self.quotedIdentifier($0)) IS dirty.\(Self.quotedIdentifier($0))" }
                .joined(separator: " AND ")
            let dirtyColdJoin = keyColumns
                .map { "cold.\(Self.quotedIdentifier($0)) IS dirty.\(Self.quotedIdentifier($0))" }
                .joined(separator: " AND ")
            let columnList = copyColumns.map(Self.quotedIdentifier).joined(separator: ", ")
            let hotColumnList = copyColumns
                .map { "hot.\(Self.quotedIdentifier($0))" }
                .joined(separator: ", ")
            let firstKey = Self.quotedIdentifier(keyColumns[0])

            try db.execute(sql: "DROP TABLE IF EXISTS temp.editor_refresh_dirty_keys")
            try db.execute(sql: "CREATE TEMP TABLE editor_refresh_dirty_keys (\(keyDefinitions))")
            try db.execute(sql: """
                INSERT INTO temp.editor_refresh_dirty_keys (\(keyList))
                SELECT \(keyColumns.map { "hot.\(Self.quotedIdentifier($0))" }.joined(separator: ", "))
                FROM main.\(tableName) hot
                LEFT JOIN cold_editor.\(tableName) cold ON \(hotColdJoin)
                WHERE cold.\(firstKey) IS NULL
                   OR IFNULL(cold.\(Self.quotedIdentifier("score")), 0) <> IFNULL(hot.\(Self.quotedIdentifier("score")), 0)
                """)
            try db.execute(sql: """
                DELETE FROM cold_editor.\(tableName)
                WHERE rowid IN (
                    SELECT cold.rowid
                    FROM cold_editor.\(tableName) cold
                    JOIN temp.editor_refresh_dirty_keys dirty ON \(dirtyColdJoin)
                )
                """)
            try db.execute(sql: """
                INSERT INTO cold_editor.\(tableName) (\(columnList))
                SELECT \(hotColumnList)
                FROM main.\(tableName) hot
                JOIN temp.editor_refresh_dirty_keys dirty ON \(dirtyHotJoin)
                """)
        }
    }

    private func writeEditorRefreshReceipt(for request: EditorRefreshRequest,
                                           status: EditorRefreshReceipt.Status,
                                           error: String?) throws {
        let receipt = EditorRefreshReceipt(requestUUID: request.requestUUID,
                                           table: request.table,
                                           status: status,
                                           error: error,
                                           at: Date().timeIntervalSince1970)
        try atomicWrite(try JSONEncoder().encode(receipt),
                        to: SyncPaths.editorRefreshReceipt(appGroupBaseURL))
    }

    private func drainIMInboxIfNeeded() throws {
        let inboxURL = SyncPaths.imInbox(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: inboxURL.path) else { return }
        let inbox = try JSONDecoder().decode(IMInboxFile.self, from: Data(contentsOf: inboxURL))
        guard !inbox.records.isEmpty else {
            try? FileManager.default.removeItem(at: inboxURL)
            return
        }

        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)
        try connection.write { db in
            try Self.ensureIMTable(in: db)
            for record in inbox.records {
                switch record.op {
                case .upsert:
                    try Self.upsertIM(record.row, in: db)
                case .delete:
                    try Self.deleteIM(record.row, in: db)
                }
            }
        }
        try FileManager.default.removeItem(at: inboxURL)
    }

    private func clearIMInboxIfNeeded() throws {
        let inboxURL = SyncPaths.imInbox(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: inboxURL.path) else { return }
        try FileManager.default.removeItem(at: inboxURL)
    }

    private func readIMLifecycleRecords() throws -> [IMLifecycleRecord] {
        let inboxURL = SyncPaths.imLifecycleInbox(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: inboxURL.path) else { return [] }
        let records = try JSONDecoder().decode([IMLifecycleRecord].self,
                                               from: Data(contentsOf: inboxURL))
        if records.isEmpty {
            try? FileManager.default.removeItem(at: inboxURL)
        }
        return records
    }

    private func writeRemainingIMLifecycleRecords(_ records: [IMLifecycleRecord]) throws {
        let inboxURL = SyncPaths.imLifecycleInbox(appGroupBaseURL)
        guard !records.isEmpty else {
            try? FileManager.default.removeItem(at: inboxURL)
            return
        }
        try atomicWrite(try JSONEncoder().encode(records), to: inboxURL)
    }

    private func clearIMLifecycleInboxIfNeeded() throws {
        let inboxURL = SyncPaths.imLifecycleInbox(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: inboxURL.path) else { return }
        try FileManager.default.removeItem(at: inboxURL)
    }

    private func applyIncremental(from coldSnapshotURL: URL) throws {
        let coldRevisions = try revisions(in: coldSnapshotURL)
        let hotRevisions = try revisions(in: hotDatabaseURL)
        let tables = Set(coldRevisions.keys).union(hotRevisions.keys).sorted()
        guard !tables.isEmpty else { return }

        let lifecycleRecords = try readIMLifecycleRecords()
        var consumedLifecycleIndexes = Set<Int>()
        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)
        for table in tables where Self.isSafeTableName(table) {
            guard let coldRevision = coldRevisions[table] else {
                let records = lifecycleRecords.enumerated().filter { $0.element.table == table }
                try applyDeleteLifecycle(records.map(\.element), for: table)
                consumedLifecycleIndexes.formUnion(records.map(\.offset))
                try connection.write { db in
                    try Self.drop(table, in: db)
                    try Self.deleteMeta("rev:\(table)", in: db)
                }
                continue
            }
            guard coldRevision != hotRevisions[table] else { continue }

            let records = lifecycleRecords.enumerated().filter { $0.element.table == table }
            try applyDeleteLifecycle(records.map(\.element), for: table)

            var copied = false
            try connection.write { db in
                try db.execute(sql: "ATTACH DATABASE ? AS cold_snapshot",
                               arguments: [coldSnapshotURL.path])
                defer { try? db.execute(sql: "DETACH DATABASE cold_snapshot") }

                guard try Self.tableExists(table, schema: "cold_snapshot", in: db) else {
                    try Self.drop(table, in: db)
                    try Self.deleteMeta("rev:\(table)", in: db)
                    return
                }
                try Self.copy(table, fromSchema: "cold_snapshot", in: db)
                try Self.upsertMeta("rev:\(table)", value: String(coldRevision), in: db)
                copied = true
            }

            if copied {
                try applyInstallLifecycle(records.map(\.element), for: table)
            }
            consumedLifecycleIndexes.formUnion(records.map(\.offset))
        }

        if !consumedLifecycleIndexes.isEmpty {
            let remaining = lifecycleRecords.enumerated()
                .filter { !consumedLifecycleIndexes.contains($0.offset) }
                .map(\.element)
            try writeRemainingIMLifecycleRecords(remaining)
        }
    }

    private func applyDeleteLifecycle(_ records: [IMLifecycleRecord], for table: String) throws {
        for record in records where record.action == .delete {
            let db = try LimeDB(path: hotDatabaseURL.path)
            if record.preserveLearning {
                db.backupUserRecords(table)
            }
            db.clearTable(table)
        }
    }

    private func applyInstallLifecycle(_ records: [IMLifecycleRecord], for table: String) throws {
        for record in records where record.action == .install && record.preserveLearning {
            let db = try LimeDB(path: hotDatabaseURL.path)
            guard db.checkBackupTable(table) else { continue }
            _ = db.restoreUserRecords(table)
            _ = db.dropBackupTable(table)
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

    private static func columns(in table: String, schema: String?, db: Database) throws -> [String] {
        let tableName = quotedIdentifier(table)
        if let schema {
            return try Row.fetchAll(db, sql: """
                PRAGMA \(quotedIdentifier(schema)).table_info(\(tableName))
                """).compactMap { $0["name"] as String? }
        }
        return try Row.fetchAll(db, sql: "PRAGMA table_info(\(tableName))")
            .compactMap { $0["name"] as String? }
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

    private static func ensureIMTable(in db: Database) throws {
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS im (
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
    }

    private static func upsertIM(_ row: [String: String?], in db: Database) throws {
        guard let code = row["code"] ?? nil, !code.isEmpty,
              let title = row["title"] ?? nil, !title.isEmpty
        else { return }
        try db.execute(sql: "DELETE FROM im WHERE code = ? AND title = ?",
                       arguments: [code, title])
        try db.execute(sql: """
            INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, arguments: [
                code,
                title,
                row["desc"] ?? nil,
                row["keyboard"] ?? nil,
                row["disable"] ?? nil,
                row["selkey"] ?? nil,
                row["endkey"] ?? nil,
                row["spacestyle"] ?? nil
            ])
    }

    private static func deleteIM(_ row: [String: String?], in db: Database) throws {
        guard let code = row["code"] ?? nil, !code.isEmpty else { return }
        if let title = row["title"] ?? nil, !title.isEmpty {
            try db.execute(sql: "DELETE FROM im WHERE code = ? AND title = ?",
                           arguments: [code, title])
        } else {
            try db.execute(sql: "DELETE FROM im WHERE code = ?", arguments: [code])
        }
    }

    private static func quotedIdentifier(_ identifier: String) -> String {
        "\"\(identifier.replacingOccurrences(of: "\"", with: "\"\""))\""
    }

    private static func renameReplacing(_ source: URL, with destination: URL) throws {
        guard rename(source.path, destination.path) == 0 else {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
    }

    private static func isSafeTableName(_ table: String) -> Bool {
        guard table != "sync_meta", !table.hasPrefix("sqlite_") else { return false }
        return table.range(of: #"^[A-Za-z][A-Za-z0-9_]*$"#,
                           options: .regularExpression) != nil
    }

    private static func editorRefreshKeyColumns(for table: String) -> [String] {
        table == "related" ? ["pword", "cword"] : ["code", "word"]
    }
}

private enum TableSyncEngineError: LocalizedError {
    case editorRefreshExpired
    case liveColdMissing
    case tableMissing(String)
    case unsupportedEditorTable(String)
    case unsafeTableName(String)

    var errorDescription: String? {
        switch self {
        case .editorRefreshExpired:
            return "Editor refresh request expired"
        case .liveColdMissing:
            return "Live cold database is missing"
        case .tableMissing(let table):
            return "Editor refresh table is missing: \(table)"
        case .unsupportedEditorTable(let table):
            return "Editor refresh table has unsupported columns: \(table)"
        case .unsafeTableName(let table):
            return "Unsafe editor refresh table name: \(table)"
        }
    }
}
