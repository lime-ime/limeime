// SetupImController.swift
// LimeIME-iOS
//
// Orchestrates IM import, backup/restore, seeding.
// Mirrors Android SetupImController.

import Foundation
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
        Task.detached(priority: .userInitiated) {
            do {
                let count = try installTextFile(server: server, url: url, tableName: tableName,
                                                meta: TableMeta(restoreLearning: false,
                                                                displayName: nil,
                                                                provenance: "local-text"))
                await MainActor.run {
                    self.progress.dismiss()
                    view?.onProgress(100, status: "已交付鍵盤，共 \(count) 筆")
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
                let meta = TableMeta(restoreLearning: restoreLearning,
                                     displayName: nil,
                                     provenance: "local-text")
                return .success(try installTextFile(server: server, url: url, tableName: tableName, meta: meta))
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
        Task.detached(priority: .userInitiated) {
            do {
                try importDatabaseFile(server: server, url: url, tableName: safeTable,
                                       meta: TableMeta(restoreLearning: false,
                                                       displayName: nil,
                                                       provenance: "local-db"))
                await MainActor.run {
                    self.progress.dismiss()
                    view?.onProgress(100, status: "已交付鍵盤 \(safeTable)")
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
                try importDatabaseFile(server: server, url: url, tableName: safeTable,
                                       meta: TableMeta(restoreLearning: restoreLearning,
                                                       displayName: nil,
                                                       provenance: "local-db"))
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
                try prepareBundledRestore(server: server)
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

    // MARK: - Restore

    func restoreDB(from url: URL, view: (any SetupImView)?) {
        progress.show(status: "還原中…")
        let server = self.dbServer
        Task.detached(priority: .userInitiated) {
            do {
                try prepareBackupRestore(server: server, from: url)
                await MainActor.run {
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
                try prepareBackupRestore(server: server, from: url)
                return .success(())
            } catch {
                return .failure(mapSetupImError(error))
            }
        }.value
        await MainActor.run { progress.dismiss() }
        return result
    }

    // MARK: - Re-register IMs after Android backup restore

    private func reregisterKnownIMs() async {
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
        let server = self.dbServer
        await Task.detached(priority: .userInitiated) {
            for im in knownIMs {
                guard server.tableHasData(im.name) else { continue }
                try? server.registerIM(imName: im.name, tableName: im.name,
                                       label: im.title, keyboardId: im.keyboard)
            }
        }.value
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
    case restoreSchemaTooNew(Int)
}

extension SetupImControllerError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .backupTimedOut:
            return "備份逾時，請開啟完整取用權限並將鍵盤切換至萊姆輸入法後再試"
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
private let maxRestoreExtractTotalBytes: UInt64 = 500 * 1024 * 1024
private let maxRestoreExtractEntries = 10_000
private let maxRestoreCompressionRatio = 100.0

private func prepareBundledRestore(server: DBServer) throws {
    guard let bundledURL = Bundle.main.url(forResource: "lime", withExtension: "db") else {
        throw DBServerError.fileNotFound("lime.db (bundled)")
    }
    try prepareRestore(server: server, databaseURL: bundledURL)
}

private func prepareBackupRestore(server: DBServer, from url: URL) throws {
    let startedScopedAccess = url.startAccessingSecurityScopedResource()
    defer { if startedScopedAccess { url.stopAccessingSecurityScopedResource() } }

    let dir = FileManager.default.temporaryDirectory
        .appendingPathComponent("lime_restore_\(UUID().uuidString)", isDirectory: true)
    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: dir) }

    let localURL = try coordinatedCopy(url, into: dir)
    let payload = try restorePayload(from: localURL, in: dir)
    try prepareRestore(server: server, databaseURL: payload.databaseURL)
    restorePreferences(server: server,
                       sharedPrefsURL: payload.sharedPrefsURL,
                       preferenceManifestURL: payload.preferenceManifestURL)
}

private func prepareRestore(server: DBServer, databaseURL: URL) throws {
    do {
        _ = try server.makeTableStore().prepareRestore(from: databaseURL)
        postSyncSignal(.tablesUpdated)
    } catch {
        throw mapSetupImError(error)
    }
}

private func requestKeyboardBackup(server: DBServer) throws -> URL {
    let baseURL = server.makeTableStore().baseURL
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
                let zip = try buildBackupArchive(server: server, snapshotURL: snapshotURL)
                try? FileManager.default.removeItem(at: snapshotURL)
                try? FileManager.default.removeItem(at: requestURL)
                try? FileManager.default.removeItem(at: receiptURL)
                _ = receipt
                return zip
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

private func buildBackupArchive(server: DBServer, snapshotURL: URL) throws -> URL {
    guard FileManager.default.fileExists(atPath: snapshotURL.path) else {
        throw DBServerError.fileNotFound(DBServer.databaseName)
    }

    let baseURL = server.makeTableStore().baseURL
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

private func restorePreferences(server: DBServer, sharedPrefsURL: URL, preferenceManifestURL: URL) {
    if FileManager.default.fileExists(atPath: preferenceManifestURL.path),
       server.restorePreferenceCompatibilityManifest(file: preferenceManifestURL) {
        return
    }
    if FileManager.default.fileExists(atPath: sharedPrefsURL.path) {
        server.restoreDefaultSharedPreference(file: sharedPrefsURL)
    }
}

private func mapSetupImError(_ error: Error) -> Error {
    if let tableStoreError = error as? TableStoreError,
       case .schemaTooNew(let version) = tableStoreError {
        return SetupImControllerError.restoreSchemaTooNew(version)
    }
    return error
}

private func fileSizeBytes(at url: URL) -> Int64 {
    let attrs = try? FileManager.default.attributesOfItem(atPath: url.path)
    let size = attrs?[.size] as? NSNumber
    return size?.int64Value ?? 0
}

func importDatabaseFile(server: DBServer, url: URL, tableName: String, meta: TableMeta? = nil) throws {
    let store = server.makeTableStore()
    if isZipArchive(at: url) {
        do {
            try store.installFromZip(from: url, stem: tableName, meta: meta)
        } catch {
            try installLegacyZippedDB(store: store, zipURL: url,
                                      tableName: tableName, meta: meta,
                                      originalError: error)
        }
    } else {
        try store.installLimedb(from: url, stem: tableName, meta: meta)
    }
    postSyncSignal(.tablesUpdated)
}

private func installTextFile(server: DBServer, url: URL, tableName: String, meta: TableMeta?) throws -> Int {
    let store = server.makeTableStore()
    try store.installText(from: url, stem: tableName, meta: meta)
    let count = installedRowCount(baseURL: store.baseURL, tableName: tableName)
    postSyncSignal(.tablesUpdated)
    return count
}

private func installedRowCount(baseURL: URL, tableName: String) -> Int {
    guard let db = try? LimeDB(path: SyncPaths.tableFile(baseURL, stem: tableName).path) else { return 0 }
    defer { try? db.closeForReplacement() }
    return db.countRecords(tableName, nil, nil)
}

private func installLegacyZippedDB(store: TableStore, zipURL: URL,
                                   tableName: String, meta: TableMeta?,
                                   originalError: Error) throws {
    let archive: Archive
    do {
        archive = try Archive(url: zipURL, accessMode: .read)
    } catch {
        throw originalError
    }
    guard let entry = archive.first(where: { entry in
        entry.type != .directory && entry.path.lowercased().hasSuffix(".db")
    }) else {
        throw originalError
    }

    let dir = FileManager.default.temporaryDirectory
        .appendingPathComponent(UUID().uuidString, isDirectory: true)
    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: dir) }

    let extracted = dir.appendingPathComponent(URL(fileURLWithPath: entry.path).lastPathComponent)
    _ = try archive.extract(entry, to: extracted, skipCRC32: false)
    do {
        try store.installLimedb(from: extracted, stem: tableName, meta: meta)
    } catch {
        guard tableName != "custom" else { throw error }
        try store.installLimedb(from: mapLegacyCustomBackup(extracted, tableName: tableName, in: dir),
                                stem: tableName,
                                meta: meta)
    }
}

private func isZipArchive(at url: URL) -> Bool {
    guard let handle = try? FileHandle(forReadingFrom: url) else { return false }
    defer { try? handle.close() }
    return (try? handle.read(upToCount: 4))?.starts(with: [0x50, 0x4B]) == true
}

private func mapLegacyCustomBackup(_ sourceURL: URL, tableName: String, in dir: URL) throws -> URL {
    let mapped = dir.appendingPathComponent("\(tableName)-mapped.limedb")
    let db = try LimeDB(path: mapped.path)
    defer { try? db.closeForReplacement() }
    db.importDb(sourceFile: sourceURL,
                tableNames: ["custom"],
                overwriteExisting: true,
                includeRelated: false)
    db.renameTableName("custom", tableName)
    return mapped
}
