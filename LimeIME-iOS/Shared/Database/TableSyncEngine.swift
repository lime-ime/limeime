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
            // No cold snapshot yet (fresh keyboard / App Group unavailable) → nothing to
            // sync; the keyboard reads its bundled-default `im` (or the last `im.json`, §1.5).
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
            // `im` is not synced into hot; the keyboard reads it from `im.json` (§1.5), which
            // the app republishes on restore. Hot's own `im` table rides along in the whole-file
            // swap but is no longer read on the keyboard side.
            return true
        }

        // Same lineage (applied), generation moved → per-table incremental reconcile.
        // (`im` is not synced here — it is read from `im.json`, §1.5.)
        try applyIncremental(from: coldSnapshotURL)
        try Self.stampAppliedEpoch(coldEpoch, on: hotMeta)
        try hotMeta.setAppliedGeneration(coldGeneration)
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

        // §1.8: keyboard-owned hamburger prefs live in the extension's own
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

    /// §1.8: copy the active device profile's keyboard-owned prefs from the extension's own container
    /// (`UserDefaults.standard`) to the App Group (cold) so a backup's preference sidecar
    /// captures current values. Runs only inside the FA-on backup handshake — the one time
    /// the keyboard may write the App Group.
    private func flushHotPrefsToColdForBackup() {
        let hot = UserDefaults.standard
        guard let cold = UserDefaults(suiteName: LIMEPreferenceManager.suiteName) else { return }
        // Issue #169: flush only the active device-class geometry profile. The cold
        // store keeps the other profile dormant; a phone must never rewrite iPad
        // split/numpad values, and an iPad must never rewrite phone values.
        var keys = ["han_convert_option"]
        switch hot.string(forKey: "keyboard_geometry_profile") {
        case "phone":
            keys += ["phone_portrait_keyboard_mode", "phone_landscape_split"]
        case "tablet":
            keys += ["split_keyboard_mode", "numpad_anchor"]
        default:
            break
        }
        for key in keys {
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

    /// Issue #209: absorb TRANSIENT cold-side write contention by retrying the WHOLE attempt.
    ///
    /// `harvestEditorRefreshAttempt` reads the attached cold database (table probe + dirty-key
    /// scan) before it writes it, so the cold write is a read→write promotion. SQLite
    /// deliberately does NOT invoke the busy handler for that promotion — it returns
    /// SQLITE_BUSY immediately to break a possible deadlock — so the connection's
    /// `busy_timeout = 5000` never applies to it, and a Settings-side write lock held for even
    /// a few milliseconds fails the entire refresh. Re-running the pragma would change nothing;
    /// only re-running the transaction can succeed.
    ///
    /// Each retry uses a fresh connection so rollback/close releases all transaction, temp-table,
    /// and attached-database state before the next attempt.
    ///
    /// Budget: attempts may start for 3 seconds and each uses a short 500 ms busy timeout for
    /// lock points where SQLite DOES invoke the busy handler. Even allowing two timeout-bearing
    /// lock points in the final attempt, the path ends within roughly 4 seconds, leaving
    /// scheduling and receipt-delivery headroom inside the Settings-side 10-second poll, and
    /// remaining far inside the 30-second request TTL.
    ///
    /// Only SQLITE_BUSY / SQLITE_LOCKED are retried. Schema, I/O and data-integrity failures
    /// still fail on the first attempt.
    private static let editorRefreshBusyRetryWindow: TimeInterval = 3
    private static let editorRefreshBusyRetryBackoff: TimeInterval = 0.15
    private static let editorRefreshAttemptBusyTimeoutMilliseconds = 500

    private func harvestEditorRefresh(table: String, into coldDatabaseURL: URL) throws {
        let retryDeadline = Date().addingTimeInterval(Self.editorRefreshBusyRetryWindow)
        while true {
            do {
                return try harvestEditorRefreshAttempt(table: table, into: coldDatabaseURL)
            } catch let error as DatabaseError where Self.isTransientLockError(error) {
                let remaining = retryDeadline.timeIntervalSinceNow
                guard remaining > 0 else {
                    throw error
                }
                Thread.sleep(forTimeInterval: min(Self.editorRefreshBusyRetryBackoff, remaining))
            }
        }
    }

    private static func isTransientLockError(_ error: DatabaseError) -> Bool {
        // `primaryResultCode` also matches the extended codes (SQLITE_BUSY_SNAPSHOT,
        // SQLITE_LOCKED_SHAREDCACHE, …) and is idempotent on an already-primary code.
        let code = error.resultCode.primaryResultCode
        return code == .SQLITE_BUSY || code == .SQLITE_LOCKED
    }

    private func harvestEditorRefreshAttempt(table: String, into coldDatabaseURL: URL) throws {
        let keyColumns = Self.editorRefreshKeyColumns(for: table)
        let connection = try SyncDatabaseConnection(
            databaseURL: hotDatabaseURL,
            busyTimeoutMilliseconds: Self.editorRefreshAttemptBusyTimeoutMilliseconds)
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
        // An empty `tables` set (a metadata-only edit bumps `generation`, not any per-table
        // `rev`) just no-ops the loop below; the caller still stamps `applied_generation` and
        // returns true, so the keyboard rebuilds and re-reads `im.json` (§1.5).

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

        // §1.5: `im` is NOT synced into hot. It is app-published as `im.json` and read by the
        // keyboard directly (no mirror, no cold-DB open). A metadata-only edit changes no
        // per-table `rev`, so `tables` is empty and this loop no-ops — but the caller still
        // stamps `applied_generation` and returns true, so the runtime rebuild re-reads the
        // fresh `im.json`. The old wholesale hot `im` mirror is gone.
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
