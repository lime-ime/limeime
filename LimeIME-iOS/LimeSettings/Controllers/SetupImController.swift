// SetupImController.swift
// LimeIME-iOS
//
// Orchestrates IM import, backup/restore, seeding.
// Mirrors Android SetupImController.

import Foundation

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
            // Use a reference holder so the @Sendable progress callback can
            // mutate the running count without capturing a `var` (Swift 6).
            final class CountBox: @unchecked Sendable { var value: Int = 0 }
            let counter = CountBox()
            do {
                try server.importTxtFile(at: url.path, tableName: tableName) { count in
                    counter.value = count
                    Task { @MainActor in
                        view?.onProgress(50, status: "已匯入 \(count) 筆…")
                    }
                }
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
                try server.importTxtFile(at: url.path, tableName: tableName) { count in
                    lastCount = count
                }
                if restoreLearning {
                    if let ss = server.makeSearchServer() {
                        let restored = ss.restoreUserRecords(tableName)
                        if restored > 0 { ss.dropBackupTable(tableName) }
                    }
                }
                return .success(lastCount)
            } catch {
                return .failure(error)
            }
        }.value
        await MainActor.run { progress.dismiss() }
        return result
    }

    // MARK: - Import binary DB file (.db / .limedb)

    func importDBFile(url: URL, tableName: String, view: (any SetupImView)?) {
        progress.show(status: "匯入中…")
        let server = self.dbServer
        let safeTable = server.isValidTableName(tableName) ? tableName : "custom"
        Task.detached(priority: .userInitiated) {
            do {
                try importDatabaseFile(server: server, url: url, tableName: safeTable)
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
                try importDatabaseFile(server: server, url: url, tableName: safeTable)
                if restoreLearning {
                    if let ss = server.makeSearchServer() {
                        let restored = ss.restoreUserRecords(safeTable)
                        if restored > 0 { ss.dropBackupTable(safeTable) }
                    }
                }
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
        let dest = FileManager.default.temporaryDirectory
            .appendingPathComponent("lime_backup_\(Int(Date().timeIntervalSince1970)).zip")
        try dbServer.backupDatabase(uri: dest)
        return dest
    }

    // MARK: - Restore

    func restoreDB(from url: URL, view: (any SetupImView)?) {
        progress.show(status: "還原中…")
        let server = self.dbServer
        Task.detached(priority: .userInitiated) {
            server.restoreDatabase(uri: url)
            await MainActor.run {
                self.progress.dismiss()
                view?.onProgress(100, status: "資料庫還原完成")
                view?.refreshImList()
            }
        }
    }

    // MARK: - Restore (async, SwiftUI-friendly)

    func restoreDB(from url: URL) async -> Result<Void, Error> {
        await MainActor.run { progress.show(status: "還原中…") }
        let server = self.dbServer
        await Task.detached(priority: .userInitiated) {
            server.restoreDatabase(uri: url)
        }.value
        // Re-register all known IMs in iOS structured format.
        // Android backups store im configs as key-value rows; registerIM replaces
        // them with single iOS-format rows that getAllImConfigs() can read correctly.
        await reregisterKnownIMs()
        // Signal the keyboard extension to reload its database connection.
        // The keyboard holds a stale DatabaseQueue to the old file after restore.
        UserDefaults(suiteName: "group.net.toload.limeime")?
            .set(Date().timeIntervalSince1970, forKey: "lime_db_restored_at")
        await MainActor.run { progress.dismiss() }
        return .success(())
    }

    // MARK: - Re-register IMs after Android backup restore

    private func reregisterKnownIMs() async {
        let knownIMs: [(name: String, title: String, keyboard: String)] = [
            ("phonetic", "注音",     "lime_phonetic"),
            ("dayi",     "大易",     "lime_dayi"),
            ("cj",       "倉頡",     "lime_cj"),
            ("cj5",      "倉頡五代", "lime_cj"),
            ("array",    "行列",     "lime_array"),
            ("array10",  "行列十",   "phone_simple"),
            ("wb",       "筆順五碼", "lime_wb"),
            ("hs",       "許氏",     "lime_hs"),
            ("ez",       "輕鬆",     "lime_ez"),
            ("scj",      "速成",     "lime_cj"),
            ("ecj",      "易倉頡",   "lime_cj"),
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
            let ok = server.exportTxtTable(table: tableNick, targetFile: dest, imConfigList: nil)
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

func importDatabaseFile(server: DBServer, url: URL, tableName: String) throws {
    if isZipArchive(at: url) {
        try server.importFromZip(at: url, tableName: tableName)
    } else {
        try server.importFromAttachedDB(sourcePath: url.path, tableName: tableName)
    }
}

private func isZipArchive(at url: URL) -> Bool {
    guard let handle = try? FileHandle(forReadingFrom: url) else { return false }
    defer { try? handle.close() }
    return (try? handle.read(upToCount: 4))?.starts(with: [0x50, 0x4B]) == true
}
