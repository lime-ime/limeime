import Foundation
import GRDB
import Darwin

final class TableSyncEngine {
    private let appGroupBaseURL: URL
    private let hotDatabaseURL: URL
    private let dbServer: DBServer
    /// Own-container store for the `im`-inbox consume cursor (`UserDefaults.standard` in the
    /// keyboard; an isolated suite in tests). Survives the full-replace (§1.5).
    private let consumeDefaults: UserDefaults

    init(appGroupBaseURL: URL,
         hotDatabaseURL: URL,
         dbServer: DBServer = .shared,
         consumeDefaults: UserDefaults = .standard) {
        self.appGroupBaseURL = appGroupBaseURL
        self.hotDatabaseURL = hotDatabaseURL
        self.dbServer = dbServer
        self.consumeDefaults = consumeDefaults
    }

    convenience init(locator: SyncDatabaseLocator = .production(),
                     dbServer: DBServer = .shared,
                     consumeDefaults: UserDefaults = .standard) {
        self.init(appGroupBaseURL: locator.appGroupDirectory,
                  hotDatabaseURL: locator.hotDatabaseURL,
                  dbServer: dbServer,
                  consumeDefaults: consumeDefaults)
    }

    /// Returns `true` when a cold→hot change was applied to hot's IM data (full replace
    /// or incremental import) — the caller reloads the keyboard's IM list only then.
    /// `hasFullAccess` gates the App Group **writers** (backup snapshot / editor receipt):
    /// those are FA-on operations, so a stale request never triggers an FA-off write.
    @discardableResult
    func scanAndApply(hasFullAccess: Bool = true) throws -> Bool {
        if hasFullAccess {
            try processBackupExportRequestIfNeeded()
            try processEditorRefreshRequestIfNeeded()
        }

        let coldSnapshotURL = SyncPaths.coldDB(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: coldSnapshotURL.path) else {
            try drainIMInboxIfNeeded()
            return false
        }

        let coldMeta = try SyncMetaStore(databaseURL: coldSnapshotURL)
        let hotMeta = try SyncMetaStore(databaseURL: hotDatabaseURL)
        let coldEpoch = try coldMeta.epochUUID()
        let coldGeneration = try coldMeta.generation()
        // Is cold's lineage already applied to hot? hot.epoch_uuid is hot's OWN identity — a
        // random value at bootstrap (ensureKeyboardHotDatabase), or cold's epoch after a
        // completed full-replace copy — so it is NOT a reliable applied-marker on its own: an
        // install-only cold has no epoch_uuid (nil), and a hot that synced incrementally keeps
        // its bootstrap epoch, so coldEpoch(nil) != hotEpoch(bootstrap) would falsely read as a
        // NEW lineage and full-replace (clearing pending inboxes, wiping hot). The decoupled
        // applied_epoch marker records the applied cold epoch (nil for an install-only lineage)
        // — nil == nil → converged. hot.epoch_uuid is kept only as a fast-path so a completed
        // restore copy is not re-copied when its applied_epoch stamp was interrupted (§1.2).
        // Applied when EITHER marker matches cold.
        let appliedEpoch = try hotMeta.appliedEpoch()
        let hotEpoch = try hotMeta.epochUUID()
        let appliedGeneration = try hotMeta.appliedGeneration()
        let epochApplied = coldEpoch == appliedEpoch || coldEpoch == hotEpoch

        if epochApplied, coldGeneration == appliedGeneration {
            try drainIMInboxIfNeeded()
            return false
        }

        if !epochApplied {
            // Different lineage (a restore stamps a fresh non-nil epoch) → wholesale full
            // replace. The whole-file swap carries cold's epoch_uuid into hot (self-marking);
            // also stamp applied_epoch so an install-only (nil-epoch) cold is not re-read as a
            // new lineage next scan. An interrupted copy leaves hot on its old epoch and
            // re-applies here; a completed copy matches above via hot.epoch_uuid — no re-copy.
            try dbServer.replaceDatabaseFromSnapshot(coldSnapshotURL)
            let refreshedHotMeta = try SyncMetaStore(databaseURL: hotDatabaseURL)
            try Self.stampAppliedEpoch(coldEpoch, on: refreshedHotMeta)
            try refreshedHotMeta.setAppliedGeneration(coldGeneration)
            // The snapshot already carries the authoritative `im` table, so the pending im
            // inbox is SUPERSEDED — do NOT apply it (a not-yet-drained install would land on
            // top of the restore, resurrecting a wiped IM; a stale delete would remove a
            // restored one). Just advance the cursor past every current record so a later
            // incremental drain skips them; records written AFTER this restore still apply.
            // The cursor lives in UserDefaults (survives the swap). Lifecycle is rev-gated.
            markIMInboxConsumed()
            return true
        }

        // Same lineage (applied), generation moved → per-table incremental reconcile.
        try applyIncremental(from: coldSnapshotURL)
        try Self.stampAppliedEpoch(coldEpoch, on: hotMeta)
        try hotMeta.setAppliedGeneration(coldGeneration)
        try drainIMInboxIfNeeded()
        return true
    }

    /// Record the applied cold epoch on hot. A nil cold epoch (install-only lineage) removes
    /// the marker so `nil == nil` reads as converged on the next scan.
    private static func stampAppliedEpoch(_ epoch: String?, on meta: SyncMetaStore) throws {
        if let epoch {
            try meta.setAppliedEpoch(epoch)
        } else {
            try meta.removeValue(forKey: SyncMetaStore.appliedEpochKey)
        }
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

        // §1.8: the four keyboard-owned hamburger prefs live in the extension's own
        // container, not this hot DB. Backup is the one FA-on moment the keyboard may write
        // the App Group, so flush them to cold now — the app's preference sidecar then
        // captures the CURRENT values, not cold's stale copy.
        flushHotPrefsToColdForBackup()

        let epoch = try SyncMetaStore(databaseURL: hotDatabaseURL).epochUUID() ?? ""
        let receipt = ExportReceipt(requestUUID: request.requestUUID,
                                    epochUUID: epoch,
                                    at: Date().timeIntervalSince1970)
        try atomicWrite(try JSONEncoder().encode(receipt),
                        to: SyncPaths.receipt(appGroupBaseURL))
    }

    /// §1.8: copy the four keyboard-owned prefs from the extension's own container
    /// (`UserDefaults.standard`) to the App Group (cold) so a backup's preference sidecar
    /// captures current values. Runs only inside the FA-on backup handshake — the one time
    /// the keyboard may write the App Group.
    private func flushHotPrefsToColdForBackup() {
        let hot = UserDefaults.standard
        guard let cold = UserDefaults(suiteName: LIMEPreferenceManager.suiteName) else { return }
        for key in ["han_convert_option", "split_keyboard_mode"] {
            if let value = hot.object(forKey: key) { cold.set(value, forKey: key) }
        }
        if let activeIM = hot.string(forKey: "active_im"), !activeIM.isEmpty {
            cold.set(activeIM, forKey: "active_im")
        }
        for (key, value) in hot.dictionaryRepresentation() where key.hasSuffix("_im_reverselookup") {
            cold.set(value, forKey: key)
        }
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
        guard let inbox = IMInbox.read(base: appGroupBaseURL), !inbox.records.isEmpty else { return }
        // §1.5: consume via an own-container cursor (UserDefaults, so it survives a
        // full-replace) — never by deleting the App Group inbox (read-only FA-off). Skip
        // already-consumed records; the app GCs the file via the relayed cursor.
        let cursor = consumeDefaults.integer(forKey: IMInbox.consumedSeqKey)
        let pending = inbox.records.filter { $0.seq > cursor }
        guard !pending.isEmpty else { return }

        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)
        try connection.write { db in
            try Self.ensureIMTable(in: db)
            for record in pending {
                switch record.op {
                case .upsert:
                    try Self.upsertIM(record.row, in: db)
                case .delete:
                    try Self.deleteIM(record.row, in: db)
                }
            }
        }
        let applied = pending.map(\.seq).max() ?? cursor
        consumeDefaults.set(max(cursor, applied), forKey: IMInbox.consumedSeqKey)
    }

    /// After a full-replace the snapshot is authoritative for `im`, so the pending inbox is
    /// superseded — advance the cursor past every current record WITHOUT applying it, so a
    /// later drain skips them (records written after the restore still apply).
    private func markIMInboxConsumed() {
        guard let inbox = IMInbox.read(base: appGroupBaseURL) else { return }
        let maxSeq = inbox.records.map(\.seq).max() ?? 0
        let cursor = consumeDefaults.integer(forKey: IMInbox.consumedSeqKey)
        if maxSeq > cursor { consumeDefaults.set(maxSeq, forKey: IMInbox.consumedSeqKey) }
    }

    private func readIMLifecycleRecords() throws -> [IMLifecycleRecord] {
        let inboxURL = SyncPaths.imLifecycleInbox(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: inboxURL.path) else { return [] }
        // Read-only: the keyboard never deletes/rewrites this App Group file (FA-off). A
        // record is applied only when its table's rev moves (rev-gated in applyIncremental),
        // so a lingering one is never re-applied; the app GCs consumed records via the relay.
        return (try? JSONDecoder().decode([IMLifecycleRecord].self,
                                          from: Data(contentsOf: inboxURL))) ?? []
    }

    private func applyIncremental(from coldSnapshotURL: URL) throws {
        let coldRevisions = try revisions(in: coldSnapshotURL)
        let hotRevisions = try revisions(in: hotDatabaseURL)
        let tables = Set(coldRevisions.keys).union(hotRevisions.keys).sorted()
        guard !tables.isEmpty else { return }

        // Read-only: apply lifecycle records per table (rev-gated below), never write the
        // App Group back (§1.6). A consumed record simply lingers until the app GCs it; the
        // rev gate keeps it from being re-applied.
        let lifecycleRecords = try readIMLifecycleRecords()
        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)

        // Attach the cold snapshot ONCE for the whole loop. Per-table ATTACH/DETACH on
        // the shared connection is unsafe: a DETACH issued inside GRDB's write
        // transaction fails silently, leaving `cold_snapshot` attached, so the NEXT
        // changed table's ATTACH throws "database cold_snapshot is already in use" and
        // aborts the entire sync whenever 2+ tables changed at once (e.g. installing two
        // IMs). Attach outside a transaction, reuse it across iterations, detach once.
        try connection.writeWithoutTransaction { db in
            try db.execute(sql: "ATTACH DATABASE ? AS cold_snapshot",
                           arguments: [coldSnapshotURL.path])
        }
        defer {
            try? connection.writeWithoutTransaction { db in
                try? db.execute(sql: "DETACH DATABASE cold_snapshot")
            }
        }

        for table in tables where Self.isSafeTableName(table) {
            guard let coldRevision = coldRevisions[table] else {
                let records = lifecycleRecords.filter { $0.table == table }
                try applyDeleteLifecycle(records, for: table)
                try connection.write { db in
                    try Self.drop(table, in: db)
                    try Self.deleteMeta("rev:\(table)", in: db)
                }
                continue
            }
            guard coldRevision != hotRevisions[table] else { continue }

            let records = lifecycleRecords.filter { $0.table == table }
            try applyDeleteLifecycle(records, for: table)

            var copied = false
            try connection.write { db in
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
                try applyInstallLifecycle(records, for: table)
            }
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
