// SetupImController.swift
// LimeIME-iOS
//
// Orchestrates IM import, backup/restore, seeding.
// Mirrors Android SetupImController.

import Foundation
import GRDB
import ZIPFoundation

// MARK: - SetupImController

@MainActor
final class SetupImController: BaseController {

    // MARK: - Dependencies

    private let progress: ProgressManager

    // MARK: - Init

    init(dbServer: DBServer = .shared, prefs: LIMEPreferenceManager = .shared,
         progress: ProgressManager) {
        self.progress = progress
        super.init(dbServer: dbServer, prefs: prefs)
    }

    // MARK: - Import txt file (.cin / .lime)

    func importTxtFile(url: URL, tableName: String, view: (any SetupImView)?) {
        progress.show(status: "匯入中…")
        let server = self.dbServer
        let restoreLearning = self.prefs.restoreOnImport(for: tableName)
        Task.detached(priority: .userInitiated) {
            final class CountBox: @unchecked Sendable { var value: Int = 0 }
            let counter = CountBox()
            do {
                try server.importTxtFile(at: url.path,
                                         tableName: tableName,
                                         publish: false,
                                         progress: { count in
                    counter.value = count
                    Task { @MainActor in
                        view?.onProgress(50, status: "已匯入 \(count) 筆…")
                    }
                })
                try server.writeIMLifecycleRecord(table: tableName,
                                                  action: .install,
                                                  preserveLearning: restoreLearning,
                                                  postSignal: false)
                try server.markTableChangedAndPublish(tableName)
                await MainActor.run {
                    self.progress.dismiss()
                    view?.onProgress(100, status: "文字檔匯入完成，共 \(counter.value) 筆")
                    view?.refreshImList()
                }
            } catch {
                let msg = error.localizedDescription
                await MainActor.run {
                    self.progress.dismiss()
                    view?.onError("匯入失敗：\(msg)")
                }
            }
        }
    }

    // MARK: - Import txt file (async, SwiftUI-friendly)

    func importTxtFile(url: URL, tableName: String, restoreLearning: Bool = false) async -> Result<Int, Error> {
        await MainActor.run { progress.show(status: "匯入中…") }
        let server = self.dbServer
        let result: Result<Int, Error> = await Task.detached(priority: .userInitiated) {
            do {
                var lastCount = 0
                try server.importTxtFile(at: url.path,
                                         tableName: tableName,
                                         publish: false,
                                         progress: { count in
                    lastCount = count
                })
                try server.writeIMLifecycleRecord(table: tableName,
                                                  action: .install,
                                                  preserveLearning: restoreLearning,
                                                  postSignal: false)
                try server.markTableChangedAndPublish(tableName)
                return .success(lastCount)
            } catch {
                return .failure(error)
            }
        }.value
        await MainActor.run { progress.dismiss() }
        return result
    }

    // MARK: - Import zipped DB file (.limedb / .zip)

    func importDBFile(url: URL, tableName: String, view: (any SetupImView)?) {
        progress.show(status: "匯入中…")
        let server = self.dbServer
        let safeTable = server.isValidTableName(tableName) ? tableName : "custom"
        let restoreLearning = self.prefs.restoreOnImport(for: safeTable)
        Task.detached(priority: .userInitiated) {
            do {
                try importDatabaseFile(server: server,
                                       url: url,
                                       tableName: safeTable,
                                       restoreLearning: restoreLearning)
                await MainActor.run {
                    self.progress.dismiss()
                    view?.onProgress(100, status: "已成功匯入 \(safeTable)")
                    view?.refreshImList()
                }
            } catch {
                let msg = error.localizedDescription
                await MainActor.run {
                    self.progress.dismiss()
                    view?.onError("匯入失敗：\(msg)")
                }
            }
        }
    }

    // MARK: - Import DB file (async, SwiftUI-friendly)

    func importDBFile(url: URL, tableName: String, restoreLearning: Bool = false) async -> Result<String, Error> {
        await MainActor.run { progress.show(status: "匯入中…") }
        let server = self.dbServer
        let safeTable = server.isValidTableName(tableName) ? tableName : "custom"
        let result: Result<String, Error> = await Task.detached(priority: .userInitiated) {
            do {
                try importDatabaseFile(server: server,
                                       url: url,
                                       tableName: safeTable,
                                       restoreLearning: restoreLearning)
                return .success(safeTable)
            } catch {
                return .failure(error)
            }
        }.value
        await MainActor.run { progress.dismiss() }
        return result
    }

    // MARK: - Restore bundled database (factory reset)

    func restoreBundledDatabase() async -> Result<String, Error> {
        await MainActor.run { progress.show(status: "還原預設資料庫…") }
        let server = self.dbServer
        let result: Result<String, Error> = await Task.detached(priority: .userInitiated) {
            do {
                try server.restoreBundledDatabase()
                try publishRestoredCold(server: server)
                return .success("已還原預設資料庫")
            } catch {
                return .failure(error)
            }
        }.value
        await MainActor.run { progress.dismiss() }
        return result
    }

    // MARK: - Seed related phrases

    /// Seeds the related-phrase table from the bundled lime.db if it is currently empty.
    /// Only runs when the App Group DB has no related rows (first launch or after a full wipe).
    func seedRelatedIfNeeded() async {
        let server = self.dbServer
        let needsSeed = await Task.detached(priority: .userInitiated) {
            !server.tableHasData("related")
        }.value
        guard needsSeed else { return }
        await MainActor.run { progress.show(status: "載入關聯字資料庫…") }
        await Task.detached(priority: .userInitiated) {
            guard let bundledURL = Bundle.main.url(forResource: "lime", withExtension: "db") else { return }
            server.importDbRelated(sourcedb: bundledURL)
        }.value
        await MainActor.run { progress.dismiss() }
    }

    // MARK: - Backup

    /// Backup the database to a temp .zip file and return its URL for sharing.
    /// Caller is responsible for deleting the temp file after sharing.
    func backupDB() throws -> URL {
        try requestKeyboardBackup(server: dbServer)
    }

    func backupDBAsync() async -> Result<URL, Error> {
        let server = self.dbServer
        return await Task.detached(priority: .userInitiated) {
            do {
                return .success(try requestKeyboardBackup(server: server))
            } catch {
                return .failure(error)
            }
        }.value
    }

#if DEBUG
    func backupColdDBToDocumentsForUITest(fileName: String = "lime_backup.zip") async -> Result<URL, Error> {
        let server = self.dbServer
        return await Task.detached(priority: .userInitiated) {
            do {
                let backupURL = try requestKeyboardBackup(server: server)
                defer { try? FileManager.default.removeItem(at: backupURL) }
                let documentsURL = FileManager.default.urls(for: .documentDirectory,
                                                            in: .userDomainMask)[0]
                try FileManager.default.createDirectory(at: documentsURL,
                                                        withIntermediateDirectories: true)
                let destinationURL = documentsURL.appendingPathComponent(fileName)
                try? FileManager.default.removeItem(at: destinationURL)
                try FileManager.default.copyItem(at: backupURL, to: destinationURL)
                return .success(destinationURL)
            } catch {
                return .failure(error)
            }
        }.value
    }

    // ponytail: row-count fallback is a UI-test proof that restored cold tables are real when keyboard driving is flaky.
    func restoredTableCountsForUITest() async -> String {
        let server = dbServer
        let counts = await Task.detached(priority: .userInitiated) {
            [
                "dayi": server.countRecords("dayi", nil, nil),
                "phonetic": server.countRecords("phonetic", nil, nil),
            ]
        }.value
        return "restore_table_counts dayi=\(counts["dayi"] ?? 0) phonetic=\(counts["phonetic"] ?? 0)"
    }
#endif

    func refreshTableFromKeyboard(stem: String) async -> Result<Void, Error> {
        await refreshTableFromKeyboard(stem: stem,
                                       baseURL: appGroupBaseURL(),
                                       timeout: editorRefreshPollTimeout,
                                       pollInterval: editorRefreshPollInterval)
    }

    func refreshTableFromKeyboard(stem: String,
                                  baseURL: URL,
                                  timeout: TimeInterval,
                                  pollInterval: TimeInterval) async -> Result<Void, Error> {
        let requestURL = SyncPaths.editorRefreshRequest(baseURL)
        let receiptURL = SyncPaths.editorRefreshReceipt(baseURL)
        let requestUUID = UUID().uuidString
        let request = EditorRefreshRequest(requestUUID: requestUUID,
                                           table: stem,
                                           expiresAt: Date().addingTimeInterval(editorRefreshRequestTTL).timeIntervalSince1970)
        do {
            try? FileManager.default.removeItem(at: receiptURL)
            try atomicWrite(try JSONEncoder().encode(request), to: requestURL)
            postSyncSignal(.tablesUpdated)
            try await waitForEditorRefreshReceipt(at: receiptURL,
                                                  requestUUID: requestUUID,
                                                  timeout: timeout,
                                                  pollInterval: pollInterval)
            try? FileManager.default.removeItem(at: requestURL)
            try? FileManager.default.removeItem(at: receiptURL)
            return .success(())
        } catch {
            try? FileManager.default.removeItem(at: requestURL)
            try? FileManager.default.removeItem(at: receiptURL)
            return .failure(error)
        }
    }

    func publishEditorChanges(stem: String) async -> Result<Void, Error> {
        let server = self.dbServer
        return await Task.detached(priority: .userInitiated) {
            do {
                try server.markTableChangedAndPublish(stem)
                return .success(())
            } catch {
                return .failure(error)
            }
        }.value
    }

    // MARK: - Restore

    func restoreDB(from url: URL, view: (any SetupImView)?) {
        progress.show(status: "還原中…")
        let server = self.dbServer
        Task.detached(priority: .userInitiated) {
            do {
                try restoreBackupIntoCold(server: server, from: url)
                await MainActor.run {
                    self.syncIMActivatedState()
                    self.progress.dismiss()
                    view?.onProgress(100, status: "資料庫還原完成")
                    view?.refreshImList()
                }
            } catch {
                let msg = mapSetupImError(error).localizedDescription
                await MainActor.run {
                    self.progress.dismiss()
                    view?.onError("還原失敗：\(msg)")
                }
            }
        }
    }

    // MARK: - Restore (async, SwiftUI-friendly)

    func restoreDB(from url: URL) async -> Result<Void, Error> {
        await MainActor.run { progress.show(status: "還原中…") }
        let server = self.dbServer
        let result: Result<Void, Error> = await Task.detached(priority: .userInitiated) {
            do {
                try restoreBackupIntoCold(server: server, from: url)
                return .success(())
            } catch {
                return .failure(mapSetupImError(error))
            }
        }.value
        if case .success = result {
            syncIMActivatedState()
        }
        await MainActor.run { progress.dismiss() }
        return result
    }

    // MARK: - Export (share)

    /// Exports an IM table as .lime text to a temp file and returns the URL.
    func exportIMAsText(tableNick: String) async -> URL? {
        let server = self.dbServer
        return await Task.detached(priority: .userInitiated) {
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent("\(tableNick).lime")
            let imConfigList = server.getImConfigList(tableNick, nil)
            let ok = server.exportTxtTable(table: tableNick, targetFile: dest, imConfigList: imConfigList)
            return ok ? dest : nil
        }.value
    }

    /// Exports an IM table as .limedb (zipped) to a temp file and returns the URL.
    func exportIMAsLimedb(tableNick: String) async -> URL? {
        let server = self.dbServer
        return await Task.detached(priority: .userInitiated) {
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent("\(tableNick).limedb")
            return server.exportZippedDb(tableName: tableNick, targetDbFile: dest)
        }.value
    }

    /// Exports the related-phrase table as .limedb (zipped) to a temp file and returns the URL.
    func exportRelatedAsLimedb() async -> URL? {
        let server = self.dbServer
        return await Task.detached(priority: .userInitiated) {
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent("related.limedb")
            return server.exportZippedDbRelated(targetFile: dest)
        }.value
    }

    // MARK: - Sync keyboard state

    func syncIMActivatedState() {
        prefs.syncIMActivatedState(dbServer: dbServer)
    }
}

enum SetupImControllerError: Error {
    case backupTimedOut
    case editorRefreshTimedOut
    case editorRefreshFailed(String?)
    case restoreSchemaTooNew(Int)
}

extension SetupImControllerError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .backupTimedOut:
            return "備份逾時，請開啟完整取用權限並將鍵盤切換至萊姆輸入法後再試"
        case .editorRefreshTimedOut:
            return "同步逾時，請開啟完整取用權限並將鍵盤切換至萊姆輸入法後再試"
        case .editorRefreshFailed(let message):
            return message ?? "同步失敗，請稍後再試"
        case .restoreSchemaTooNew:
            return "請先更新 LIME"
        }
    }
}

private struct RestorePayload {
    let databaseURL: URL
    let sharedPrefsURL: URL
    let preferenceManifestURL: URL
}

// ponytail: fixed backup request TTL; make configurable only if product copy gains multiple retry modes.
private let backupRequestTTL: TimeInterval = 120
// ponytail: fixed poll window; replace with receipt notification only if Darwin/file polling proves flaky.
private let backupReceiptPollTimeout: TimeInterval = 15
private let backupReceiptPollInterval: TimeInterval = 0.25
// ponytail: editor refresh is a foreground entry gate; make configurable only if device traces exceed this.
private let editorRefreshRequestTTL: TimeInterval = 30
private let editorRefreshPollTimeout: TimeInterval = 10
private let editorRefreshPollInterval: TimeInterval = 0.1
private let maxRestoreExtractTotalBytes: UInt64 = 500 * 1024 * 1024
private let maxRestoreExtractEntries = 10_000
private let maxRestoreCompressionRatio = 100.0

private func appGroupBaseURL() -> URL {
    FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: LIMEPreferenceManager.suiteName)
        ?? FileManager.default.temporaryDirectory
}

private func restoreBackupIntoCold(server: DBServer, from url: URL) throws {
    let startedScopedAccess = url.startAccessingSecurityScopedResource()
    defer { if startedScopedAccess { url.stopAccessingSecurityScopedResource() } }

    let dir = FileManager.default.temporaryDirectory
        .appendingPathComponent("lime_restore_\(UUID().uuidString)", isDirectory: true)
    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: dir) }

    let localURL = try coordinatedCopy(url, into: dir)
    let payload = try restorePayload(from: localURL, in: dir)
    try validateRestoreDatabase(payload.databaseURL)
    try server.restoreDatabase(srcFilePath: localURL.path)
    reregisterKnownIMs(server: server)
    try publishRestoredCold(server: server)
}

private func requestKeyboardBackup(server: DBServer) throws -> URL {
    let snapshotURL = try requestKeyboardSnapshot(server: server)
    defer { try? FileManager.default.removeItem(at: snapshotURL) }
    return try buildBackupArchive(server: server, snapshotURL: snapshotURL)
}

private func publishRestoredCold(server: DBServer) throws {
    let liveURL = server.liveDatabaseURL()
    _ = try SyncMetaStore(databaseURL: liveURL).replaceEpochUUID()
    try ColdPublisher(liveColdDatabaseURL: liveURL,
                      appGroupBaseURL: liveURL.deletingLastPathComponent()).publish()
}

private func requestKeyboardSnapshot(server: DBServer) throws -> URL {
    let baseURL = appGroupBaseURL()
    let requestURL = SyncPaths.exportRequest(baseURL)
    let snapshotURL = SyncPaths.backupSnapshot(baseURL)
    let receiptURL = SyncPaths.receipt(baseURL)
    let requestUUID = UUID().uuidString
    let requestedAt = Date().timeIntervalSince1970

    try? FileManager.default.removeItem(at: snapshotURL)
    try? FileManager.default.removeItem(at: receiptURL)

    let request = ExportRequest(requestUUID: requestUUID,
                                expiresAt: requestedAt + backupRequestTTL)
    try atomicWrite(try JSONEncoder().encode(request), to: requestURL)
    postSyncSignal(.tablesUpdated)

    do {
        let deadline = Date().addingTimeInterval(backupReceiptPollTimeout)
        while Date() <= deadline {
            if let receipt = matchingReceipt(at: receiptURL, requestUUID: requestUUID),
               snapshotIsFresh(at: snapshotURL, since: requestedAt) {
                try? FileManager.default.removeItem(at: requestURL)
                try? FileManager.default.removeItem(at: receiptURL)
                _ = receipt
                return snapshotURL
            }
            Thread.sleep(forTimeInterval: backupReceiptPollInterval)
        }
        throw SetupImControllerError.backupTimedOut
    } catch {
        try? FileManager.default.removeItem(at: requestURL)
        throw error
    }
}

private func matchingReceipt(at url: URL, requestUUID: String) -> ExportReceipt? {
    guard let data = try? Data(contentsOf: url),
          let receipt = try? JSONDecoder().decode(ExportReceipt.self, from: data),
          receipt.requestUUID == requestUUID
    else {
        return nil
    }
    return receipt
}

private func snapshotIsFresh(at url: URL, since requestedAt: TimeInterval) -> Bool {
    guard let identity = FileIdentity(url: url) else { return false }
    return identity.mtime >= requestedAt - 1
}

private func waitForEditorRefreshReceipt(at url: URL,
                                         requestUUID: String,
                                         timeout: TimeInterval,
                                         pollInterval: TimeInterval) async throws {
    let doneObserver = SyncSignalObserver(signal: .importDone) {}
    let failedObserver = SyncSignalObserver(signal: .importFailed) {}
    defer {
        withExtendedLifetime(doneObserver) {}
        withExtendedLifetime(failedObserver) {}
    }

    let deadline = Date().addingTimeInterval(timeout)
    while Date() <= deadline {
        if let receipt = matchingEditorRefreshReceipt(at: url, requestUUID: requestUUID) {
            switch receipt.status {
            case .done:
                return
            case .failed:
                throw SetupImControllerError.editorRefreshFailed(receipt.error)
            }
        }
        let delay = UInt64(max(0.001, pollInterval) * 1_000_000_000)
        try? await Task.sleep(nanoseconds: delay)
    }
    throw SetupImControllerError.editorRefreshTimedOut
}

private func matchingEditorRefreshReceipt(at url: URL,
                                          requestUUID: String) -> EditorRefreshReceipt? {
    guard let data = try? Data(contentsOf: url),
          let receipt = try? JSONDecoder().decode(EditorRefreshReceipt.self, from: data),
          receipt.requestUUID == requestUUID
    else {
        return nil
    }
    return receipt
}

private func buildBackupArchive(server: DBServer, snapshotURL: URL) throws -> URL {
    guard FileManager.default.fileExists(atPath: snapshotURL.path) else {
        throw DBServerError.fileNotFound(DBServer.databaseName)
    }

    let baseURL = appGroupBaseURL()
    let sharedPrefsURL = baseURL.appendingPathComponent(DBServer.sharedPrefsBackupName)
    let preferenceManifestURL = baseURL.appendingPathComponent(DBServer.preferenceManifestPath)
    try? FileManager.default.removeItem(at: sharedPrefsURL)
    try? FileManager.default.removeItem(at: preferenceManifestURL)
    server.backupDefaultSharedPreference(file: sharedPrefsURL)
    server.backupPreferenceCompatibilityManifest(file: preferenceManifestURL)
    defer {
        try? FileManager.default.removeItem(at: sharedPrefsURL)
        try? FileManager.default.removeItem(at: preferenceManifestURL)
    }

    let zipURL = FileManager.default.temporaryDirectory
        .appendingPathComponent("lime_backup_\(Int(Date().timeIntervalSince1970)).zip")
    try? FileManager.default.removeItem(at: zipURL)

    let archive = try Archive(url: zipURL, accessMode: .create)
    try archive.addEntry(with: DBServer.databaseName, fileURL: snapshotURL)
    if FileManager.default.fileExists(atPath: sharedPrefsURL.path) {
        try archive.addEntry(with: DBServer.sharedPrefsBackupName, fileURL: sharedPrefsURL)
    }
    if FileManager.default.fileExists(atPath: preferenceManifestURL.path) {
        try archive.addEntry(with: DBServer.preferenceManifestPath, fileURL: preferenceManifestURL)
    }
    guard fileSizeBytes(at: zipURL) > 0 else {
        throw DBServerError.archiveCreationFailed
    }
    try? FileManager.default.setAttributes(
        [.protectionKey: FileProtectionType.complete], ofItemAtPath: zipURL.path)
    return zipURL
}

private func coordinatedCopy(_ url: URL, into dir: URL) throws -> URL {
    let destination = dir.appendingPathComponent("restore-input-\(UUID().uuidString)")
    var coordinatorError: NSError?
    var copyError: Error?
    NSFileCoordinator().coordinate(readingItemAt: url, options: .withoutChanges, error: &coordinatorError) { coordinatedURL in
        do {
            try FileManager.default.copyItem(at: coordinatedURL, to: destination)
        } catch {
            copyError = error
        }
    }
    if let error = coordinatorError ?? copyError {
        throw error
    }
    return destination
}

private func restorePayload(from url: URL, in dir: URL) throws -> RestorePayload {
    let databaseURL = dir.appendingPathComponent(DBServer.databaseName)
    let sharedPrefsURL = dir.appendingPathComponent(DBServer.sharedPrefsBackupName)
    let preferenceManifestURL = dir.appendingPathComponent(DBServer.preferenceManifestPath)

    guard isZipArchive(at: url) else {
        return RestorePayload(databaseURL: url,
                              sharedPrefsURL: sharedPrefsURL,
                              preferenceManifestURL: preferenceManifestURL)
    }

    let archive: Archive
    do {
        archive = try Archive(url: url, accessMode: .read)
    } catch {
        throw DBServerError.invalidRestoreArchive(url.path)
    }
    try validateRestoreArchive(archive)

    var dbExtracted = false
    for entry in archive where entry.type == .file {
        if entry.path == DBServer.preferenceManifestPath {
            try FileManager.default.createDirectory(at: preferenceManifestURL.deletingLastPathComponent(),
                                                    withIntermediateDirectories: true)
            try? FileManager.default.removeItem(at: preferenceManifestURL)
            _ = try archive.extract(entry, to: preferenceManifestURL, skipCRC32: false)
            continue
        }
        if URL(fileURLWithPath: entry.path).lastPathComponent == DBServer.sharedPrefsBackupName {
            try? FileManager.default.removeItem(at: sharedPrefsURL)
            _ = try archive.extract(entry, to: sharedPrefsURL, skipCRC32: false)
            continue
        }
        guard !dbExtracted,
              URL(fileURLWithPath: entry.path).lastPathComponent == DBServer.databaseName else {
            continue
        }
        try? FileManager.default.removeItem(at: databaseURL)
        _ = try archive.extract(entry, to: databaseURL, skipCRC32: false)
        dbExtracted = true
    }

    guard dbExtracted else { throw DBServerError.missingDatabaseInRestoreArchive }
    return RestorePayload(databaseURL: databaseURL,
                          sharedPrefsURL: sharedPrefsURL,
                          preferenceManifestURL: preferenceManifestURL)
}

private func validateRestoreArchive(_ archive: Archive) throws {
    var totalBytes: UInt64 = 0
    var count = 0
    for entry in archive where entry.type != .directory {
        count += 1
        if count > maxRestoreExtractEntries { throw DBServerError.zipBombDetected }
        totalBytes = totalBytes &+ UInt64(entry.uncompressedSize)
        if totalBytes > maxRestoreExtractTotalBytes { throw DBServerError.zipBombDetected }
        if entry.compressedSize > 0 {
            let ratio = Double(entry.uncompressedSize) / Double(entry.compressedSize)
            if ratio > maxRestoreCompressionRatio { throw DBServerError.zipBombDetected }
        }
        let components = entry.path.split(separator: "/", omittingEmptySubsequences: true)
        if entry.path.isEmpty || entry.path.hasPrefix("/") || components.contains(where: { $0 == ".." }) {
            throw DBServerError.unsafeZipEntry(entry.path)
        }
    }
}

private func validateRestoreDatabase(_ databaseURL: URL) throws {
    // Open READ-WRITE (on the throwaway extracted copy): a WAL-mode backup DB cannot
    // be opened read-only without its -wal/-shm sidecars, which are not carried in the
    // backup zip — a read-only open throws SQLITE_CANTOPEN (error 14) and breaks
    // restore for every WAL backup. Read-write lets SQLite recreate the sidecars.
    let queue = try DatabaseQueue(path: databaseURL.path)
    defer { try? queue.close() }
    try queue.read { db in
        let quickCheck = try String.fetchOne(db, sql: "PRAGMA quick_check")
        guard quickCheck == "ok" else {
            throw DBServerError.invalidRestoreSource(quickCheck ?? "quick_check failed")
        }
        if try tableExists("sync_meta", in: db),
           let raw = try String.fetchOne(db,
                                         sql: "SELECT value FROM sync_meta WHERE key = 'schema_version'"),
           let version = Int(raw) {
            if version > 104 /* LimeDB.CURRENT_DB_VERSION is private; 104 = portable schema, lock-step Android/iOS */ {
                throw SetupImControllerError.restoreSchemaTooNew(version)
            }
            return
        }
        let userVersion = try Int.fetchOne(db, sql: "PRAGMA user_version") ?? 0
        if userVersion > 0 && userVersion > 104 /* LimeDB.CURRENT_DB_VERSION is private; 104 = portable schema, lock-step Android/iOS */ {
            throw SetupImControllerError.restoreSchemaTooNew(userVersion)
        }
    }
}

private func tableExists(_ table: String, in db: Database) throws -> Bool {
    try (Int.fetchOne(db,
                      sql: "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                      arguments: [table]) ?? 0) > 0
}

private func reregisterKnownIMs(server: DBServer) {
    let knownIMs: [(name: String, title: String, keyboard: String)] = [
        ("phonetic", "注音",     "lime_phonetic"),
        ("dayi",     "大易",     "lime_dayi"),
        ("cj",       "倉頡",     "lime_cj_number"),
        ("cj5",      "倉頡五代", "lime_cj_number"),
        ("array",    "行列",     "lime_array"),
        ("array10",  "行列十",   "phone_simple"),
        ("wb",       "筆順五碼", "lime_wb"),
        ("hs",       "許氏",     "lime_hs"),
        ("ez",       "輕鬆",     "lime_ez"),
        ("scj",      "速成",     "lime_cj_number"),
        ("ecj",      "易倉頡",   "lime_cj_number"),
    ]
    for im in knownIMs {
        guard server.tableHasData(im.name) else { continue }
        try? server.registerIM(imName: im.name, tableName: im.name,
                               label: im.title, keyboardId: im.keyboard)
    }
}

private func mapSetupImError(_ error: Error) -> Error {
    return error
}

private func fileSizeBytes(at url: URL) -> Int64 {
    let attrs = try? FileManager.default.attributesOfItem(atPath: url.path)
    let size = attrs?[.size] as? NSNumber
    return size?.int64Value ?? 0
}

func importDatabaseFile(server: DBServer,
                        url: URL,
                        tableName: String,
                        restoreLearning: Bool = false) throws {
    let sourceURL: URL
    let cleanupURL: URL?
    if isZipArchive(at: url) {
        let extracted = try extractImportDatabase(from: url)
        sourceURL = extracted.databaseURL
        cleanupURL = extracted.tempDir
    } else {
        sourceURL = url
        cleanupURL = nil
    }
    defer {
        if let cleanupURL {
            try? FileManager.default.removeItem(at: cleanupURL)
        }
    }
    try validateImportDatabaseSource(sourceURL, tableName: tableName)
    try server.importFromAttachedDB(sourcePath: sourceURL.path, tableName: tableName, publish: false)
    try server.writeIMLifecycleRecord(table: tableName,
                                      action: .install,
                                      preserveLearning: restoreLearning,
                                      postSignal: false)
    try server.markTableChangedAndPublish(tableName)
}

private func isZipArchive(at url: URL) -> Bool {
    guard let handle = try? FileHandle(forReadingFrom: url) else { return false }
    defer { try? handle.close() }
    return (try? handle.read(upToCount: 4))?.starts(with: [0x50, 0x4B]) == true
}

private func extractImportDatabase(from zipURL: URL) throws -> (databaseURL: URL, tempDir: URL) {
    let archive = try Archive(url: zipURL, accessMode: .read)
    guard let entry = archive.first(where: {
        let lower = $0.path.lowercased()
        return lower.hasSuffix(".db") || lower.hasSuffix(".limedb")
    }) else {
        throw DBServerError.invalidRestoreSource("匯入檔格式不正確")
    }
    let tempDir = FileManager.default.temporaryDirectory
        .appendingPathComponent("lime-import-\(UUID().uuidString)", isDirectory: true)
    try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    let databaseURL = tempDir.appendingPathComponent("import.db")
    _ = try archive.extract(entry, to: databaseURL)
    return (databaseURL, tempDir)
}

private func validateImportDatabaseSource(_ url: URL, tableName: String) throws {
    guard FileManager.default.fileExists(atPath: url.path) else {
        throw DBServerError.fileNotFound(url.path)
    }
    var config = Configuration()
    config.readonly = true
    let queue = try DatabaseQueue(path: url.path, configuration: config)
    defer { try? queue.close() }

    let sourceTable = try queue.read { db -> String in
        if try db.tableExists("custom") { return "custom" }
        if try db.tableExists(tableName) { return tableName }
        throw DBServerError.invalidRestoreSource("匯入檔格式不正確")
    }
    let count = try queue.read { db in
        try Int.fetchOne(db,
                         sql: """
                         SELECT COUNT(*)
                         FROM \(sourceTable)
                         WHERE code IS NOT NULL AND word IS NOT NULL
                         """) ?? 0
    }
    guard count > 0 else {
        throw DBServerError.invalidRestoreSource("匯入檔沒有可匯入資料")
    }
}
