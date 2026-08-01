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
    private let editorRefreshBusyTimeoutMilliseconds: Int
    private let editorRefreshLockTimeout: TimeInterval
    private let editorRefreshLockFactory: (URL) throws -> EditorRefreshFileLock
    private let editorRefreshSignalPoster: (SyncSignal) -> Void
    private let beforeColdFlushWriteForTest: (() throws -> Void)?
    private let afterColdFlushCommitForTest: (() throws -> Void)?
    private let beforeHotRebuildInstallForTest: (() throws -> Void)?

    private struct RowFence {
        let table: String
        let k1: String
        let k2: String
        let action: EditorFenceAction
        let revision: Int
    }

    private struct TableFence {
        let table: String
        let action: EditorTableFenceAction
        let revision: Int
    }

    private struct LifecycleIntent {
        let table: String
        let revision: Int
        let action: IMTableLifecycleAction
        let preserveLearning: Bool
    }

    private struct ColdSyncState {
        let marker: Bool
        let revisions: [String: Int]
        let rowFences: [String: [RowFence]]
        let tableFences: [String: TableFence]
        let lifecycleIntents: [String: [LifecycleIntent]]
    }

    private struct LearningItem {
        let table: String
        let k1: String
        let k2: String
        let observedRevision: Int
        let version: Int
        let score: Int
        let baseScore: Int?
        let code3r: String?
        let isRelated: Bool
    }

    private enum FlushAbort: Error {
        case markerOrEpochRejected
    }

    init(appGroupBaseURL: URL,
         hotDatabaseURL: URL,
         dbServer: DBServer = .shared,
         editorRefreshBusyTimeoutMilliseconds: Int = 5_000,
         editorRefreshLockTimeout: TimeInterval = 2,
         editorRefreshLockFactory: @escaping (URL) throws -> EditorRefreshFileLock = {
             try EditorRefreshFileLock.shared(baseURL: $0)
         },
         editorRefreshSignalPoster: @escaping (SyncSignal) -> Void = postSyncSignal,
         beforeColdFlushWriteForTest: (() throws -> Void)? = nil,
         afterColdFlushCommitForTest: (() throws -> Void)? = nil,
         beforeHotRebuildInstallForTest: (() throws -> Void)? = nil) {
        self.appGroupBaseURL = appGroupBaseURL
        self.hotDatabaseURL = hotDatabaseURL
        self.dbServer = dbServer
        self.editorRefreshBusyTimeoutMilliseconds = editorRefreshBusyTimeoutMilliseconds
        self.editorRefreshLockTimeout = editorRefreshLockTimeout
        self.editorRefreshLockFactory = editorRefreshLockFactory
        self.editorRefreshSignalPoster = editorRefreshSignalPoster
        self.beforeColdFlushWriteForTest = beforeColdFlushWriteForTest
        self.afterColdFlushCommitForTest = afterColdFlushCommitForTest
        self.beforeHotRebuildInstallForTest = beforeHotRebuildInstallForTest
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

        let rebuiltHot = try ensureHotDatabaseUsable(hasFullAccess: hasFullAccess)
        guard FileManager.default.fileExists(atPath: hotDatabaseURL.path),
              Self.quickCheckOK(hotDatabaseURL) else {
            return false
        }

        let coldSnapshotURL = SyncPaths.coldDB(appGroupBaseURL)
        guard FileManager.default.fileExists(atPath: coldSnapshotURL.path) else {
            // No cold snapshot yet (fresh keyboard / App Group unavailable) → nothing to
            // sync; the keyboard reads its bundled-default `im` (or the last `im.json`, §1.5).
            if hasFullAccess {
                _ = try flushPendingLearning(hasFullAccess: true)
            }
            return rebuiltHot
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
        let transitioned = epochApplied ? try runLegacyTransitionIfNeeded(from: coldSnapshotURL) : false

        if epochApplied, coldGeneration == appliedGeneration {
            if hasFullAccess {
                _ = try flushPendingLearning(hasFullAccess: true)
            }
            return rebuiltHot || transitioned
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
            try markLegacyTransitionDone(in: hotDatabaseURL)
            // `im` is not synced into hot; the keyboard reads it from `im.json` (§1.5), which
            // the app republishes on restore. Hot's own `im` table rides along in the whole-file
            // swap but is no longer read on the keyboard side.
            if hasFullAccess {
                _ = try flushPendingLearning(hasFullAccess: true)
            }
            return true
        }

        // Same lineage (applied), generation moved → per-table incremental reconcile.
        // (`im` is not synced here — it is read from `im.json`, §1.5.)
        try applyIncremental(from: coldSnapshotURL)
        try Self.stampAppliedEpoch(coldEpoch, on: hotMeta)
        try hotMeta.setAppliedGeneration(coldGeneration)
        if hasFullAccess {
            _ = try flushPendingLearning(hasFullAccess: true)
        }
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

    private func ensureHotDatabaseUsable(hasFullAccess: Bool) throws -> Bool {
        let fm = FileManager.default
        if fm.fileExists(atPath: hotDatabaseURL.path), Self.quickCheckOK(hotDatabaseURL) {
            return false
        }
        guard hasFullAccess else { return false }
        return try rebuildHotFromLiveCold()
    }

    private func rebuildHotFromLiveCold() throws -> Bool {
        let liveColdURL = appGroupBaseURL.appendingPathComponent(SyncDatabaseLocator.databaseName)
        guard FileManager.default.fileExists(atPath: liveColdURL.path) else { return false }

        for _ in 0..<3 {
            let tempURL = hotDatabaseURL.deletingLastPathComponent()
                .appendingPathComponent(".hot-rebuild.\(UUID().uuidString).limedb.tmp")
            defer { Self.discardSQLiteFileSet(tempURL) }

            try FileManager.default.createDirectory(
                at: hotDatabaseURL.deletingLastPathComponent(),
                withIntermediateDirectories: true)
            let liveConnection = try SyncDatabaseConnection(databaseURL: liveColdURL)
            try liveConnection.writeWithoutTransaction { db in
                try db.execute(sql: "VACUUM INTO ?", arguments: [tempURL.path])
            }

            let capturedEpoch: String?
            let capturedGeneration: Int
            do {
                let tempMeta = try SyncMetaStore(databaseURL: tempURL)
                capturedEpoch = try tempMeta.epochUUID()
                capturedGeneration = try tempMeta.generation()
                try Self.stampAppliedEpoch(capturedEpoch, on: tempMeta)
                try tempMeta.setAppliedGeneration(capturedGeneration)
            }
            try initializeRecoveredHotMetadata(tempURL)

            try beforeHotRebuildInstallForTest?()
            let liveEpoch = try SyncMetaStore(databaseURL: liveColdURL).epochUUID()
            guard liveEpoch == capturedEpoch else { continue }

            try installRebuiltHot(tempURL)
            dbServer.reopenDatabaseFromDisk()
            return true
        }
        throw TableSyncEngineError.hotRebuildEpochChanged
    }

    private func initializeRecoveredHotMetadata(_ databaseURL: URL) throws {
        let connection = try SyncDatabaseConnection(databaseURL: databaseURL)
        try connection.write { db in
            try Self.ensureLearnOutbox(in: db)
            try db.execute(sql: "DELETE FROM learn_outbox")
            try db.execute(sql: """
                INSERT INTO sync_meta(key, value) VALUES ('legacy_transition_done', '1')
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)
        }
    }

    private func markLegacyTransitionDone(in databaseURL: URL) throws {
        let connection = try SyncDatabaseConnection(databaseURL: databaseURL)
        try connection.write { db in
            try db.execute(sql: """
                INSERT INTO sync_meta(key, value) VALUES ('legacy_transition_done', '1')
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)
        }
    }

    private func installRebuiltHot(_ tempURL: URL) throws {
        let fm = FileManager.default
        try fm.createDirectory(at: hotDatabaseURL.deletingLastPathComponent(),
                               withIntermediateDirectories: true)
        try Self.removeSQLiteFileSet(hotDatabaseURL)
        try fm.moveItem(at: tempURL, to: hotDatabaseURL)
        for suffix in ["-wal", "-shm"] {
            let sidecar = URL(fileURLWithPath: tempURL.path + suffix)
            do {
                try fm.moveItem(at: sidecar, to: URL(fileURLWithPath: hotDatabaseURL.path + suffix))
            } catch let error as NSError
                where error.domain == NSCocoaErrorDomain && error.code == NSFileNoSuchFileError {
                continue
            }
        }
    }

    private static func discardSQLiteFileSet(_ databaseURL: URL) {
        for url in sqliteFileSet(for: databaseURL) {
            do {
                try FileManager.default.removeItem(at: url)
            } catch {
            }
        }
    }

    private static func removeSQLiteFileSet(_ databaseURL: URL) throws {
        for url in sqliteFileSet(for: databaseURL) {
            do {
                try FileManager.default.removeItem(at: url)
            } catch let error as NSError
                where error.domain == NSCocoaErrorDomain && error.code == NSFileNoSuchFileError {
                continue
            }
        }
    }

    private static func sqliteFileSet(for databaseURL: URL) -> [URL] {
        [
            databaseURL,
            URL(fileURLWithPath: databaseURL.path + "-wal"),
            URL(fileURLWithPath: databaseURL.path + "-shm")
        ]
    }

    private static func quickCheckOK(_ databaseURL: URL) -> Bool {
        do {
            let connection = try SyncDatabaseConnection(databaseURL: databaseURL)
            return try connection.read { db in
                try String.fetchOne(db, sql: "PRAGMA quick_check") == "ok"
            }
        } catch {
            return false
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

        // Re-read and validate only AFTER taking cross-process ownership. Settings may have
        // timed out, removed the request and reopened cold while this keyboard was waiting.
        let ownership: EditorRefreshFileLock
        do {
            ownership = try editorRefreshLockFactory(appGroupBaseURL)
            try ownership.lock(timeout: editorRefreshLockTimeout)
        } catch EditorRefreshLockError.timedOut {
            return
        } catch {
            // A lock-file open failure defers this request to the next scan rather than
            // aborting unrelated cold→hot synchronization, but remains visible in device logs.
            NSLog("TableSyncEngine: deferring editor refresh because ownership is unavailable: %@",
                  error.localizedDescription)
            return
        }
        defer { try? ownership.unlock() }
        guard FileManager.default.fileExists(atPath: requestURL.path) else { return }
        defer { try? FileManager.default.removeItem(at: requestURL) }

        var request: EditorRefreshRequest?
        do {
            let decodedRequest = try JSONDecoder().decode(EditorRefreshRequest.self,
                                                          from: Data(contentsOf: requestURL))
            request = decodedRequest
            guard decodedRequest.expiresAt >= Date().timeIntervalSince1970 else {
                throw TableSyncEngineError.editorRefreshExpired
            }
            guard Self.isSafeTableName(decodedRequest.table) else {
                throw TableSyncEngineError.unsafeTableName(decodedRequest.table)
            }
            let liveColdURL = appGroupBaseURL.appendingPathComponent(SyncDatabaseLocator.databaseName)
            guard FileManager.default.fileExists(atPath: liveColdURL.path) else {
                throw TableSyncEngineError.liveColdMissing
            }
            // #209: harvestEditorRefresh returns only after the hot→cold transaction
            // committed, cold was detached, and the connection was closed. Ownership remains
            // held through the terminal receipt; Settings must reacquire it before reopening.
            try harvestEditorRefresh(table: decodedRequest.table, into: liveColdURL)
            try writeEditorRefreshReceipt(for: decodedRequest, status: .done, error: nil)
            editorRefreshSignalPoster(.importDone)
        } catch {
            if let request {
                try? writeEditorRefreshReceipt(for: request,
                                               status: .failed,
                                               error: error.localizedDescription)
                editorRefreshSignalPoster(.importFailed)
            } else {
                NSLog("TableSyncEngine: discarding malformed editor refresh request: %@",
                      error.localizedDescription)
            }
        }
    }

    /// Issue #209: the cold side of this handshake has an explicit lifecycle.
    ///
    /// Settings closes its own cold connection before the request becomes visible and only
    /// reopens it after the receipt, so this harvest is the ONLY cold accessor while it runs.
    /// Its job is to hand cold back provably free:
    ///
    ///   1. ATTACH cold OUTSIDE the transaction,
    ///   2. run the whole diff + write in ONE hot write transaction (atomic per database),
    ///   3. DETACH cold AFTER the commit — SQLite rejects `DETACH` inside a transaction
    ///      ("database cold_editor is locked"), which the previous `defer { try? … }` inside
    ///      GRDB's `write` swallowed, leaving cold attached until deallocation,
    ///   4. CLOSE the connection explicitly,
    ///
    /// and only then does the caller write the `.done` receipt that unlocks Settings.
    ///
    /// Settings-side cold contention is prevented by the ownership hand-off. Keep the normal
    /// 5-second busy timeout for independent hot-side keyboard writes (learning/commit). Normal
    /// Settings reopening waits for ownership. After the shared deadline, failure recovery
    /// may reopen cold best-effort rather than leaving every Settings reader empty until restart.
    private func harvestEditorRefresh(table: String, into coldDatabaseURL: URL) throws {
        let connection = try SyncDatabaseConnection(
            databaseURL: hotDatabaseURL,
            busyTimeoutMilliseconds: editorRefreshBusyTimeoutMilliseconds)
        do {
            try connection.writeWithoutTransaction { db in
                try db.execute(sql: "ATTACH DATABASE ? AS cold_editor",
                               arguments: [coldDatabaseURL.path])
            }
        } catch {
            try? connection.close()
            throw error
        }

        do {
            try harvestEditorRefreshTransaction(table: table, on: connection)
        } catch {
            try? releaseColdEditor(on: connection)
            try? connection.close()
            throw error
        }

        // A failed release means cold cannot be PROVEN free, so the request fails even
        // though the commit landed: Settings stays fail-safe read-only rather than
        // reopening cold against state this process may still hold.
        do {
            try releaseColdEditor(on: connection)
        } catch {
            try? connection.close()
            throw error
        }
        try connection.close()
    }

    /// Post-commit cleanup: both statements must run OUTSIDE the write transaction.
    private func releaseColdEditor(on connection: SyncDatabaseConnection) throws {
        try connection.writeWithoutTransaction { db in
            try db.execute(sql: "DROP TABLE IF EXISTS temp.editor_refresh_dirty_keys")
            try db.execute(sql: "DETACH DATABASE cold_editor")
        }
    }

    private func harvestEditorRefreshTransaction(table: String,
                                                 on connection: SyncDatabaseConnection) throws {
        let keyColumns = Self.editorRefreshKeyColumns(for: table)
        try connection.write { db in
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

    private func runLegacyTransitionIfNeeded(from coldSnapshotURL: URL) throws -> Bool {
        guard try !hotLegacyTransitionDone() else { return false }
        let coldState = try readColdSyncState(from: coldSnapshotURL)
        let coldRevisions = coldState.revisions
        let hotRevisions = try revisions(in: hotDatabaseURL)
        let tables = Set(coldRevisions.keys).union(hotRevisions.keys).sorted()
        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)
        try connection.writeWithoutTransaction { db in
            try db.execute(sql: "ATTACH DATABASE ? AS cold_snapshot",
                           arguments: [coldSnapshotURL.path])
        }
        defer {
            do {
                try connection.writeWithoutTransaction { db in
                    try db.execute(sql: "DETACH DATABASE cold_snapshot")
                }
            } catch {
                NSLog("TableSyncEngine: legacy transition detach failed: %@", error.localizedDescription)
            }
        }

        var changedHotData = false
        try connection.write { db in
            try Self.ensureSyncMeta(in: db)
            for table in tables where Self.isSafeTableName(table) {
                guard try Self.isEditorManagedTransitionTable(table, in: db) else { continue }
                let hotRevision = hotRevisions[table] ?? 0
                let coldRevision = coldRevisions[table] ?? 0
                if Self.hasTransitionFence(table: table,
                                           hotRevision: hotRevision,
                                           coldRevision: coldRevision,
                                           state: coldState) {
                    continue
                }
                if coldRevision != hotRevision {
                    if coldRevisions[table] != nil,
                       try Self.tableExists(table, schema: "cold_snapshot", in: db) {
                        try Self.copy(table, fromSchema: "cold_snapshot", in: db)
                        try Self.upsertMeta("rev:\(table)", value: String(coldRevision), in: db)
                    } else {
                        try Self.drop(table, in: db)
                        try Self.deleteMeta("rev:\(table)", in: db)
                    }
                    try Self.deleteOutbox(table: table, in: db)
                    changedHotData = true
                } else {
                    try Self.seedLegacyLearningDiff(table: table,
                                                    observedRevision: hotRevision,
                                                    in: db)
                }
            }
            try Self.upsertMeta("legacy_transition_done", value: "1", in: db)
        }
        return changedHotData
    }

    private func applyIncremental(from coldSnapshotURL: URL) throws {
        let coldState = try readColdSyncState(from: coldSnapshotURL)
        let coldRevisions = coldState.revisions
        let hotRevisions = try revisions(in: hotDatabaseURL)
        let tables = Set(coldRevisions.keys).union(hotRevisions.keys).sorted()
        // An empty `tables` set (a metadata-only edit bumps `generation`, not any per-table
        // `rev`) just no-ops the loop below; the caller still stamps `applied_generation` and
        // returns true, so the keyboard rebuilds and re-reads `im.json` (§1.5).

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
            if coldState.marker {
                try applyFencedTable(table,
                                     coldRevision: coldRevisions[table],
                                     hotRevision: hotRevisions[table] ?? 0,
                                     state: coldState,
                                     connection: connection)
                continue
            }

            guard let coldRevision = coldRevisions[table] else {
                try connection.write { db in
                    try Self.drop(table, in: db)
                    try Self.deleteMeta("rev:\(table)", in: db)
                }
                continue
            }
            guard coldRevision != hotRevisions[table] else { continue }

            try connection.write { db in
                guard try Self.tableExists(table, schema: "cold_snapshot", in: db) else {
                    try Self.drop(table, in: db)
                    try Self.deleteMeta("rev:\(table)", in: db)
                    return
                }
                try Self.copy(table, fromSchema: "cold_snapshot", in: db)
                try Self.upsertMeta("rev:\(table)", value: String(coldRevision), in: db)
            }
        }

        // §1.5: `im` is NOT synced into hot. It is app-published as `im.json` and read by the
        // keyboard directly (no mirror, no cold-DB open). A metadata-only edit changes no
        // per-table `rev`, so `tables` is empty and this loop no-ops — but the caller still
        // stamps `applied_generation` and returns true, so the runtime rebuild re-reads the
        // fresh `im.json`. The old wholesale hot `im` mirror is gone.
    }

    private func readColdSyncState(from coldSnapshotURL: URL) throws -> ColdSyncState {
        let connection = try SyncDatabaseConnection(databaseURL: coldSnapshotURL)
        return try connection.read { db in
            let marker = try String.fetchOne(db,
                                             sql: "SELECT value FROM sync_meta WHERE key = 'editor_fence_protocol'") == "1"
            let revisionRows = try Row.fetchAll(db, sql: """
                SELECT key, value FROM sync_meta WHERE key LIKE 'rev:%'
                """)
            var revisions: [String: Int] = [:]
            for row in revisionRows {
                guard let key = row["key"] as String?,
                      let raw = row["value"] as String?,
                      let revision = Int(raw) else { continue }
                revisions[String(key.dropFirst(4))] = revision
            }

            var rowFences: [String: [RowFence]] = [:]
            if try Self.tableExists("editor_fence", in: db) {
                for row in try Row.fetchAll(db, sql: """
                    SELECT tbl, k1, k2, action, revision FROM editor_fence
                    ORDER BY revision, tbl, k1, k2
                    """) {
                    guard let table = row["tbl"] as String?,
                          let k1 = row["k1"] as String?,
                          let k2 = row["k2"] as String?,
                          let actionRaw = row["action"] as String?,
                          let action = EditorFenceAction(rawValue: actionRaw),
                          let revision = row["revision"] as Int?
                    else { continue }
                    rowFences[table, default: []].append(RowFence(table: table,
                                                                  k1: k1,
                                                                  k2: k2,
                                                                  action: action,
                                                                  revision: revision))
                }
            }

            var tableFences: [String: TableFence] = [:]
            if try Self.tableExists("editor_table_fence", in: db) {
                for row in try Row.fetchAll(db, sql: """
                    SELECT tbl, action, revision FROM editor_table_fence
                    """) {
                    guard let table = row["tbl"] as String?,
                          let actionRaw = row["action"] as String?,
                          let action = EditorTableFenceAction(rawValue: actionRaw),
                          let revision = row["revision"] as Int?
                    else { continue }
                    tableFences[table] = TableFence(table: table,
                                                    action: action,
                                                    revision: revision)
                }
            }

            var lifecycleIntents: [String: [LifecycleIntent]] = [:]
            if try Self.tableExists("im_lifecycle_intent", in: db) {
                for row in try Row.fetchAll(db, sql: """
                    SELECT tbl, revision, action, preserve_learning
                    FROM im_lifecycle_intent
                    ORDER BY revision, tbl
                    """) {
                    guard let table = row["tbl"] as String?,
                          let revision = row["revision"] as Int?,
                          let actionRaw = row["action"] as String?,
                          let action = IMTableLifecycleAction(rawValue: actionRaw)
                    else { continue }
                    lifecycleIntents[table, default: []].append(LifecycleIntent(
                        table: table,
                        revision: revision,
                        action: action,
                        preserveLearning: (row["preserve_learning"] as Int? ?? 0) != 0))
                }
            }
            return ColdSyncState(marker: marker,
                                 revisions: revisions,
                                 rowFences: rowFences,
                                 tableFences: tableFences,
                                 lifecycleIntents: lifecycleIntents)
        }
    }

    private func applyFencedTable(_ table: String,
                                  coldRevision: Int?,
                                  hotRevision: Int,
                                  state: ColdSyncState,
                                  connection: SyncDatabaseConnection) throws {
        let targetRevision = coldRevision ?? 0
        let tableFence = state.tableFences[table].flatMap {
            $0.revision > hotRevision && $0.revision <= targetRevision ? $0 : nil
        }
        let rowFences = (state.rowFences[table] ?? [])
            .filter { $0.revision > hotRevision && $0.revision <= targetRevision }
            .sorted { $0.revision < $1.revision }
        let lifecycleIntents = (state.lifecycleIntents[table] ?? [])
            .filter { $0.revision > hotRevision && $0.revision <= targetRevision }
            .sorted { $0.revision < $1.revision }

        guard targetRevision != hotRevision || tableFence != nil || !rowFences.isEmpty || !lifecycleIntents.isEmpty else {
            return
        }
        if tableFence == nil, rowFences.isEmpty, lifecycleIntents.isEmpty,
           try hotLegacyTransitionDone() {
            throw TableSyncEngineError.markedUnfencedRevisionGap(table)
        }

        try connection.write { db in
            for intent in lifecycleIntents where intent.action == .delete && intent.preserveLearning {
                try Self.backupUserRecords(table, in: db)
            }

            var tableFloor = hotRevision
            if let tableFence {
                try apply(tableFence, in: db)
                tableFloor = tableFence.revision
            }

            for fence in rowFences where fence.revision > tableFloor {
                try apply(fence, in: db)
            }

            for intent in lifecycleIntents where intent.action == .install && intent.preserveLearning {
                try Self.restoreUserRecords(table, observedRevision: intent.revision, in: db)
            }

            if let coldRevision {
                try Self.upsertMeta("rev:\(table)", value: String(coldRevision), in: db)
            } else {
                try Self.deleteMeta("rev:\(table)", in: db)
            }
        }
    }

    private func apply(_ fence: TableFence, in db: Database) throws {
        switch fence.action {
        case .clear:
            if try Self.tableExists(fence.table, in: db) {
                try db.execute(sql: "DELETE FROM \(Self.quotedIdentifier(fence.table))")
            }
        case .replace:
            if try Self.tableExists(fence.table, schema: "cold_snapshot", in: db) {
                try Self.copy(fence.table, fromSchema: "cold_snapshot", in: db)
            } else {
                try Self.drop(fence.table, in: db)
            }
        }
        try Self.clearOutbox(table: fence.table, beforeRevision: fence.revision, in: db)
    }

    private func apply(_ fence: RowFence, in db: Database) throws {
        switch fence.action {
        case .delete:
            try Self.deleteRows(table: fence.table, k1: fence.k1, k2: fence.k2, in: db)
            try Self.deleteOutbox(table: fence.table, k1: fence.k1, k2: fence.k2, in: db)
        case .upsert:
            try Self.upsertRowsFromColdSnapshot(table: fence.table, k1: fence.k1, k2: fence.k2, in: db)
            try Self.deleteOutbox(table: fence.table, k1: fence.k1, k2: fence.k2, in: db)
        }
    }

    private func hotLegacyTransitionDone() throws -> Bool {
        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)
        return try connection.read { db in
            guard try Self.tableExists("sync_meta", in: db) else { return false }
            return try String.fetchOne(db,
                                       sql: "SELECT value FROM sync_meta WHERE key = 'legacy_transition_done'") == "1"
        }
    }

    private static func ensureSyncMeta(in db: Database) throws {
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS sync_meta (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """)
    }

    private static func hasTransitionFence(table: String,
                                           hotRevision: Int,
                                           coldRevision: Int,
                                           state: ColdSyncState) -> Bool {
        if let tableFence = state.tableFences[table],
           tableFence.revision > hotRevision,
           tableFence.revision <= coldRevision {
            return true
        }
        if (state.rowFences[table] ?? []).contains(where: {
            $0.revision > hotRevision && $0.revision <= coldRevision
        }) {
            return true
        }
        return (state.lifecycleIntents[table] ?? []).contains {
            $0.revision > hotRevision && $0.revision <= coldRevision
        }
    }

    private static func isEditorManagedTransitionTable(_ table: String, in db: Database) throws -> Bool {
        if table == "related" {
            return try tableHasColumns(table, ["pword", "cword"], schema: nil, in: db)
                || tableHasColumns(table, ["pword", "cword"], schema: "cold_snapshot", in: db)
        }
        return try tableHasColumns(table, ["code", "word"], schema: nil, in: db)
            || tableHasColumns(table, ["code", "word"], schema: "cold_snapshot", in: db)
    }

    private static func tableHasColumns(_ table: String,
                                        _ required: [String],
                                        schema: String?,
                                        in db: Database) throws -> Bool {
        guard try tableExists(table, schema: schema, in: db) else { return false }
        let existing = Set(try columns(in: table, schema: schema, db: db))
        return required.allSatisfy { existing.contains($0) }
    }

    private static func keyColumns(for table: String) -> (k1: String, k2: String) {
        table == "related" ? ("pword", "cword") : ("code", "word")
    }

    private static func liveKeyPredicate(table: String, alias: String? = nil) -> String {
        let key = keyColumns(for: table)
        let prefix = alias.map { "\($0)." } ?? ""
        let sentinel = table == "related" ? "\(prefix)\(quotedIdentifier(key.k2)) IS NOT NULL" : "\(prefix)\(quotedIdentifier(key.k2)) IS NOT NULL"
        return "\(prefix)\(quotedIdentifier(key.k1)) = ? AND \(prefix)\(quotedIdentifier(key.k2)) = ? AND \(sentinel)"
    }

    private static func deleteRows(table: String, k1: String, k2: String, in db: Database) throws {
        guard try tableExists(table, in: db) else { return }
        try db.execute(sql: """
            DELETE FROM \(quotedIdentifier(table))
            WHERE \(liveKeyPredicate(table: table))
            """, arguments: [k1, k2])
    }

    private static func deleteOutbox(table: String, k1: String, k2: String, in db: Database) throws {
        guard try tableExists("learn_outbox", in: db) else { return }
        try db.execute(sql: """
            DELETE FROM learn_outbox
            WHERE tbl = ? AND k1 = ? AND k2 = ?
            """, arguments: [table, k1, k2])
    }

    private static func deleteOutbox(table: String, in db: Database) throws {
        guard try tableExists("learn_outbox", in: db) else { return }
        try db.execute(sql: "DELETE FROM learn_outbox WHERE tbl = ?", arguments: [table])
    }

    private static func clearOutbox(table: String, beforeRevision revision: Int, in db: Database) throws {
        guard try tableExists("learn_outbox", in: db) else { return }
        try db.execute(sql: """
            DELETE FROM learn_outbox
            WHERE tbl = ? AND observed_rev < ?
            """, arguments: [table, revision])
    }

    private static func upsertRowsFromColdSnapshot(table: String,
                                                   k1: String,
                                                   k2: String,
                                                   in db: Database) throws {
        guard try tableExists(table, schema: "cold_snapshot", in: db) else {
            try deleteRows(table: table, k1: k1, k2: k2, in: db)
            return
        }
        if try !tableExists(table, in: db),
           let createSQL = try String.fetchOne(db, sql: """
                SELECT sql FROM cold_snapshot.sqlite_master
                WHERE type='table' AND name=?
                """, arguments: [table]) {
            try db.execute(sql: createSQL)
        }
        try deleteRows(table: table, k1: k1, k2: k2, in: db)

        let hotColumns = Set(try columns(in: table, schema: nil, db: db))
        let coldColumns = try columns(in: table, schema: "cold_snapshot", db: db)
            .filter { $0 != "_id" && hotColumns.contains($0) }
        guard !coldColumns.isEmpty else { return }
        let columnList = coldColumns.map(quotedIdentifier).joined(separator: ", ")
        let key = keyColumns(for: table)
        try db.execute(sql: """
            INSERT INTO \(quotedIdentifier(table)) (\(columnList))
            SELECT \(columnList)
            FROM cold_snapshot.\(quotedIdentifier(table))
            WHERE \(quotedIdentifier(key.k1)) = ?
              AND \(quotedIdentifier(key.k2)) = ?
              AND \(quotedIdentifier(key.k2)) IS NOT NULL
            """, arguments: [k1, k2])
    }

    private static func backupUserRecords(_ table: String, in db: Database) throws {
        guard try tableExists(table, in: db) else { return }
        let backup = quotedIdentifier(table + "_user")
        try db.execute(sql: "DROP TABLE IF EXISTS \(backup)")
        try db.execute(sql: """
            CREATE TABLE \(backup) AS
            SELECT * FROM \(quotedIdentifier(table))
            WHERE word IS NOT NULL AND score > 0
            ORDER BY score DESC
            """)
    }

    private static func restoreUserRecords(_ table: String,
                                           observedRevision: Int,
                                           in db: Database) throws {
        let backup = table + "_user"
        guard try tableExists(backup, in: db) else { return }
        let records = try Row.fetchAll(db, sql: """
            SELECT code, word, score FROM \(quotedIdentifier(backup))
            WHERE code IS NOT NULL AND code <> ''
              AND word IS NOT NULL AND word <> ''
            """)
        for row in records {
            guard let code = row["code"] as String?,
                  let word = row["word"] as String?,
                  !code.isEmpty,
                  !word.isEmpty else { continue }
            let score = row["score"] as Int? ?? 0
            try updateOrInsertMapping(table: table,
                                      code: code,
                                      word: word,
                                      score: score,
                                      in: db)
            try upsertOutbox(table: table,
                             k1: code,
                             k2: word,
                             observedRevision: observedRevision,
                             in: db)
        }
        try db.execute(sql: "DROP TABLE IF EXISTS \(quotedIdentifier(backup))")
    }

    private static func updateOrInsertMapping(table: String,
                                              code: String,
                                              word: String,
                                              score: Int,
                                              in db: Database) throws {
        try db.execute(sql: """
            UPDATE \(quotedIdentifier(table))
            SET score = ?
            WHERE code = ? AND word = ? AND word IS NOT NULL
            """, arguments: [score, code, word])
        guard db.changesCount == 0 else { return }

        let columns = try Self.columns(in: table, schema: nil, db: db)
        if columns.contains("code3r") {
            try db.execute(sql: """
                INSERT INTO \(quotedIdentifier(table)) (code, word, score, basescore, code3r)
                VALUES (?, ?, ?, 0, NULL)
                """, arguments: [code, word, score])
        } else {
            try db.execute(sql: """
                INSERT INTO \(quotedIdentifier(table)) (code, word, score, basescore)
                VALUES (?, ?, ?, 0)
                """, arguments: [code, word, score])
        }
    }

    private static func ensureLearnOutbox(in db: Database) throws {
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS learn_outbox (
                tbl          TEXT    NOT NULL,
                k1           TEXT    NOT NULL,
                k2           TEXT    NOT NULL,
                observed_rev INTEGER NOT NULL,
                version      INTEGER NOT NULL,
                PRIMARY KEY (tbl, k1, k2)
            ) WITHOUT ROWID
            """)
    }

    private static func upsertOutbox(table: String,
                                     k1: String,
                                     k2: String,
                                     observedRevision: Int,
                                     in db: Database) throws {
        try ensureLearnOutbox(in: db)
        try db.execute(sql: """
            INSERT INTO learn_outbox(tbl, k1, k2, observed_rev, version)
            VALUES (?, ?, ?, ?, 1)
            ON CONFLICT(tbl, k1, k2) DO UPDATE SET
                observed_rev = excluded.observed_rev,
                version = learn_outbox.version + 1
            """, arguments: [table, k1, k2, observedRevision])
    }

    private static func seedLegacyLearningDiff(table: String,
                                               observedRevision: Int,
                                               in db: Database) throws {
        guard try tableExists(table, in: db) else { return }
        try ensureLearnOutbox(in: db)
        let key = keyColumns(for: table)
        let hotTable = quotedIdentifier(table)
        let coldTable = "cold_snapshot.\(quotedIdentifier(table))"
        let hotK1 = "hot.\(quotedIdentifier(key.k1))"
        let hotK2 = "hot.\(quotedIdentifier(key.k2))"
        let coldK1 = "cold.\(quotedIdentifier(key.k1))"
        let coldK2 = "cold.\(quotedIdentifier(key.k2))"
        let hotScore = "IFNULL(hot.\(quotedIdentifier("score")), 0)"
        let coldScore = "IFNULL(cold.\(quotedIdentifier("score")), 0)"
        let rows: [Row]
        if try tableExists(table, schema: "cold_snapshot", in: db) {
            rows = try Row.fetchAll(db, sql: """
                SELECT \(hotK1) AS k1, \(hotK2) AS k2
                FROM \(hotTable) hot
                LEFT JOIN \(coldTable) cold
                  ON \(coldK1) = \(hotK1)
                 AND \(coldK2) = \(hotK2)
                 AND \(coldK2) IS NOT NULL
                WHERE \(hotK1) IS NOT NULL AND \(hotK1) <> ''
                  AND \(hotK2) IS NOT NULL AND \(hotK2) <> ''
                  AND (cold.rowid IS NULL OR \(coldScore) <> \(hotScore))
                GROUP BY \(hotK1), \(hotK2)
                """)
        } else {
            rows = try Row.fetchAll(db, sql: """
                SELECT \(quotedIdentifier(key.k1)) AS k1, \(quotedIdentifier(key.k2)) AS k2
                FROM \(hotTable)
                WHERE \(quotedIdentifier(key.k1)) IS NOT NULL AND \(quotedIdentifier(key.k1)) <> ''
                  AND \(quotedIdentifier(key.k2)) IS NOT NULL AND \(quotedIdentifier(key.k2)) <> ''
                GROUP BY \(quotedIdentifier(key.k1)), \(quotedIdentifier(key.k2))
                """)
        }
        for row in rows {
            guard let k1 = row["k1"] as String?,
                  let k2 = row["k2"] as String?,
                  !k1.isEmpty,
                  !k2.isEmpty else { continue }
            try seedOutbox(table: table, k1: k1, k2: k2,
                           observedRevision: observedRevision, in: db)
        }
    }

    private static func seedOutbox(table: String,
                                   k1: String,
                                   k2: String,
                                   observedRevision: Int,
                                   in db: Database) throws {
        try db.execute(sql: """
            INSERT INTO learn_outbox(tbl, k1, k2, observed_rev, version)
            VALUES (?, ?, ?, ?, 1)
            ON CONFLICT(tbl, k1, k2) DO UPDATE SET
                observed_rev = excluded.observed_rev
            """, arguments: [table, k1, k2, observedRevision])
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

    @discardableResult
    func flushPendingLearning(hasFullAccess: Bool = true) throws -> Bool {
        guard hasFullAccess else { return false }
        let liveColdURL = appGroupBaseURL.appendingPathComponent(SyncDatabaseLocator.databaseName)
        guard FileManager.default.fileExists(atPath: liveColdURL.path) else { return false }

        let lock = try KeyboardFlushLock.shared(baseURL: appGroupBaseURL)
        guard try lock.lock(timeout: 0) else { return false }
        defer {
            do {
                try lock.unlock()
            } catch {
            }
        }

        let snapshot = try capturePendingLearning()
        guard !snapshot.items.isEmpty else { return false }
        try beforeColdFlushWriteForTest?()

        var acknowledged: [LearningItem] = []
        do {
            let coldConnection = try SyncDatabaseConnection(databaseURL: liveColdURL)
            try coldConnection.write { db in
                let marker = try String.fetchOne(db,
                                                 sql: "SELECT value FROM sync_meta WHERE key = 'editor_fence_protocol'") == "1"
                let coldEpoch = try String.fetchOne(db,
                                                    sql: "SELECT value FROM sync_meta WHERE key = ?",
                                                    arguments: [SyncMetaStore.epochUUIDKey])
                guard marker, coldEpoch == snapshot.appliedEpoch else {
                    throw FlushAbort.markerOrEpochRejected
                }

                for item in snapshot.items {
                    if try Self.hasNewerFence(than: item.observedRevision, for: item, in: db) {
                        acknowledged.append(item)
                        continue
                    }
                    guard try Self.applyLearning(item, in: db) else { continue }
                    acknowledged.append(item)
                }
            }
        } catch FlushAbort.markerOrEpochRejected {
            NSLog("TableSyncEngine: learning flush rejected by protocol marker or epoch")
            return false
        }

        try afterColdFlushCommitForTest?()
        guard !acknowledged.isEmpty else { return false }
        try acknowledge(acknowledged)
        return true
    }

    private func capturePendingLearning() throws -> (appliedEpoch: String?, items: [LearningItem]) {
        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)
        return try connection.read { db in
            let appliedEpochMarker = try String.fetchOne(db,
                                                         sql: "SELECT value FROM sync_meta WHERE key = ?",
                                                         arguments: [SyncMetaStore.appliedEpochKey])
            let hotEpoch = try String.fetchOne(db,
                                               sql: "SELECT value FROM sync_meta WHERE key = ?",
                                               arguments: [SyncMetaStore.epochUUIDKey])
            let appliedEpoch = appliedEpochMarker ?? hotEpoch
            guard try Self.tableExists("learn_outbox", in: db) else {
                return (appliedEpoch, [])
            }
            let outbox = try Row.fetchAll(db, sql: """
                SELECT tbl, k1, k2, observed_rev, version
                FROM learn_outbox
                ORDER BY tbl, k1, k2
                """)
            var items: [LearningItem] = []
            for row in outbox {
                guard let table = row["tbl"] as String?,
                      Self.isSafeTableName(table),
                      let k1 = row["k1"] as String?,
                      let k2 = row["k2"] as String?,
                      let observedRevision = row["observed_rev"] as Int?,
                      let version = row["version"] as Int?,
                      try Self.tableExists(table, in: db)
                else { continue }

                let key = Self.keyColumns(for: table)
                let columns = try Self.columns(in: table, schema: nil, db: db)
                let baseScoreSQL = columns.contains("basescore") ? "basescore" : "NULL"
                let code3rSQL = columns.contains("code3r") ? "code3r" : "NULL"
                guard let hotRow = try Row.fetchOne(db, sql: """
                    SELECT score, \(baseScoreSQL) AS basescore, \(code3rSQL) AS code3r
                    FROM \(Self.quotedIdentifier(table))
                    WHERE \(Self.quotedIdentifier(key.k1)) = ?
                      AND \(Self.quotedIdentifier(key.k2)) = ?
                      AND \(Self.quotedIdentifier(key.k2)) IS NOT NULL
                    LIMIT 1
                    """, arguments: [k1, k2]) else { continue }
                items.append(LearningItem(table: table,
                                          k1: k1,
                                          k2: k2,
                                          observedRevision: observedRevision,
                                          version: version,
                                          score: hotRow["score"] as Int? ?? 0,
                                          baseScore: hotRow["basescore"] as Int?,
                                          code3r: hotRow["code3r"] as String?,
                                          isRelated: table == "related"))
            }
            return (appliedEpoch, items)
        }
    }

    private func acknowledge(_ items: [LearningItem]) throws {
        let connection = try SyncDatabaseConnection(databaseURL: hotDatabaseURL)
        try connection.write { db in
            guard try Self.tableExists("learn_outbox", in: db) else { return }
            for item in items {
                try db.execute(sql: """
                    DELETE FROM learn_outbox
                    WHERE tbl = ? AND k1 = ? AND k2 = ? AND version = ?
                    """, arguments: [item.table, item.k1, item.k2, item.version])
            }
        }
    }

    private static func hasNewerFence(than observedRevision: Int,
                                      for item: LearningItem,
                                      in db: Database) throws -> Bool {
        if try tableExists("editor_table_fence", in: db),
           let tableFence = try Int.fetchOne(db, sql: """
                SELECT revision FROM editor_table_fence
                WHERE tbl = ? AND revision > ?
                """, arguments: [item.table, observedRevision]),
           tableFence > observedRevision {
            return true
        }
        guard try tableExists("editor_fence", in: db) else { return false }
        let rowFence = try Int.fetchOne(db, sql: """
            SELECT revision FROM editor_fence
            WHERE tbl = ? AND k1 = ? AND k2 = ? AND revision > ?
            """, arguments: [item.table, item.k1, item.k2, observedRevision])
        return rowFence != nil
    }

    private static func applyLearning(_ item: LearningItem, in db: Database) throws -> Bool {
        guard try tableExists(item.table, in: db) else { return false }
        if item.isRelated {
            try db.execute(sql: """
                UPDATE related
                SET score = ?
                WHERE pword = ? AND cword = ? AND cword IS NOT NULL
                """, arguments: [item.score, item.k1, item.k2])
            guard db.changesCount == 0 else { return true }
            try db.execute(sql: """
                INSERT INTO related (pword, cword, basescore, score)
                SELECT ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM related
                    WHERE pword = ? AND cword = ? AND cword IS NOT NULL
                )
                """, arguments: [item.k1, item.k2, item.baseScore ?? 0, item.score, item.k1, item.k2])
            return db.changesCount > 0
        }

        try db.execute(sql: """
            UPDATE \(quotedIdentifier(item.table))
            SET score = ?
            WHERE code = ? AND word = ? AND word IS NOT NULL
            """, arguments: [item.score, item.k1, item.k2])
        guard db.changesCount == 0 else { return true }

        let columns = try Self.columns(in: item.table, schema: nil, db: db)
        if columns.contains("code3r") {
            try db.execute(sql: """
                INSERT INTO \(quotedIdentifier(item.table)) (code, word, score, basescore, code3r)
                SELECT ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM \(quotedIdentifier(item.table))
                    WHERE code = ? AND word = ? AND word IS NOT NULL
                )
                """, arguments: [
                    item.k1,
                    item.k2,
                    item.score,
                    item.baseScore ?? 0,
                    item.code3r,
                    item.k1,
                    item.k2,
                ])
        } else {
            try db.execute(sql: """
                INSERT INTO \(quotedIdentifier(item.table)) (code, word, score, basescore)
                SELECT ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM \(quotedIdentifier(item.table))
                    WHERE code = ? AND word = ? AND word IS NOT NULL
                )
                """, arguments: [item.k1, item.k2, item.score, item.baseScore ?? 0, item.k1, item.k2])
        }
        return db.changesCount > 0
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
    case hotRebuildEpochChanged
    case liveColdMissing
    case markedUnfencedRevisionGap(String)
    case tableMissing(String)
    case unsupportedEditorTable(String)
    case unsafeTableName(String)

    var errorDescription: String? {
        switch self {
        case .editorRefreshExpired:
            return "Editor refresh request expired"
        case .hotRebuildEpochChanged:
            return "Hot rebuild epoch changed before install"
        case .liveColdMissing:
            return "Live cold database is missing"
        case .markedUnfencedRevisionGap(let table):
            return "Marked cold revision advanced without a fence for table: \(table)"
        case .tableMissing(let table):
            return "Editor refresh table is missing: \(table)"
        case .unsupportedEditorTable(let table):
            return "Editor refresh table has unsupported columns: \(table)"
        case .unsafeTableName(let table):
            return "Unsafe editor refresh table name: \(table)"
        }
    }
}
