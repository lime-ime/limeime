import Foundation
import GRDB
import ZIPFoundation

// MARK: - SharedDatabase
// Process-local owner for the live LimeDB handle. This mirrors Android's static
// LimeDB.db shape: DBServer and SearchServer are different facades, but the open
// SQLite connection belongs to one neutral owner per process.
final class SharedDatabase {
    static let shared = SharedDatabase()

    private static let databaseName = "lime.db"
    private let dataDirOverride: URL?
    private var cachedDatasource: LimeDB?
    private let lock = NSLock()

    init(dataDirOverride: URL? = nil, datasource: LimeDB? = nil) {
        self.dataDirOverride = dataDirOverride
        self.cachedDatasource = datasource
    }

    var dataDirURL: URL {
        if let dataDirOverride { return dataDirOverride }
        let url = SyncDatabaseLocator.liveDatabaseDirectory()
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        if SyncDatabaseLocator.appGroupDirectory() == nil,
           !SyncDatabaseLocator.isKeyboardExtension() {
            print("[DBServer] App Group unavailable; using persistent fallback database directory: \(url.path)")
        }
        return url
    }

    func current() -> LimeDB? {
        lock.lock()
        defer { lock.unlock() }
        if cachedDatasource == nil {
            cachedDatasource = openDatasource()
        }
        return cachedDatasource
    }

    func setCurrent(_ datasource: LimeDB?) {
        lock.lock()
        cachedDatasource = datasource
        lock.unlock()
    }

    func closeCurrentForReplacement() {
        lock.lock()
        let datasource = cachedDatasource
        lock.unlock()
        do {
            try datasource?.closeForReplacement()
        } catch {
            print("[DBServer] closeDatabase() failed: \(error)")
        }
    }

    func reopenFromDisk() {
        closeCurrentForReplacement()
        setCurrent(nil)
        setCurrent(openDatasource())
    }

    func copyBundledDatabase(to destinationURL: URL) throws {
        guard let sourceURL = Bundle.main.url(forResource: "lime", withExtension: "db") else {
            throw DBServerError.bundledDatabaseMissing
        }
        try FileManager.default.createDirectory(at: destinationURL.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        try FileManager.default.copyItem(at: sourceURL, to: destinationURL)
    }

    func ensureDatabaseFile() throws -> URL {
        let directoryURL = dataDirURL
        try FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        let dbURL = directoryURL.appendingPathComponent(Self.databaseName)
        guard !FileManager.default.fileExists(atPath: dbURL.path) else { return dbURL }

        if dataDirOverride == nil,
           SyncDatabaseLocator.isKeyboardExtension(),
           let bundledURL = Bundle.main.url(forResource: "lime", withExtension: "db") {
            let legacyURL = SyncDatabaseLocator.appGroupDirectory()?
                .appendingPathComponent(Self.databaseName)
            _ = try SyncDatabaseBootstrap.ensureKeyboardHotDatabase(
                hotDatabaseURL: dbURL,
                legacyDatabaseURL: legacyURL,
                bundledDefaultURL: bundledURL)
        } else {
            try copyBundledDatabase(to: dbURL)
        }
        return dbURL
    }

    private func openDatasource() -> LimeDB? {
        let dbURL: URL
        do {
            dbURL = try ensureDatabaseFile()
        } catch {
            print("[DBServer] openDatasource: database bootstrap failed: \(error)")
            return nil
        }
        guard let db = try? LimeDB(path: dbURL.path) else { return nil }
        if let bundledURL = Bundle.main.url(forResource: "lime", withExtension: "db") {
            db.repairKeyboardCatalogIfNeeded(from: bundledURL)
        }
        return db
    }
}

// MARK: - DBServer
// Port of DBServer.java — thin orchestration layer between callers and LimeDB.
// Singleton. No Android Context, Uri, or SharedPreferences.

final class DBServer {

    struct KeyboardRuntimeContext {
        let searchServer: SearchServer
        let activatedIMs: [ImConfig]
        let initialIM: String
        let capabilities: (hasNumber: Bool, hasSymbol: Bool)
    }

    // MARK: - Singleton
    static let shared = DBServer(database: .shared)
    private let database: SharedDatabase

    // Internal (not private) so @testable import LimeIME tests can construct fresh
    // instances for isolation. Production code should use DBServer.shared.
    internal init() {
        self.database = SharedDatabase()
    }

    private init(database: SharedDatabase) {
        self.database = database
    }

    /// Test hook: inject a pre-opened LimeDB so tests can use isolated temp databases
    /// without touching the shared App Group container. Do not use in production code.
    init(_testDatasource: LimeDB) {
        let dataDirURL = URL(fileURLWithPath: _testDatasource.dbPath()).deletingLastPathComponent()
        self.database = SharedDatabase(dataDirOverride: dataDirURL, datasource: _testDatasource)
    }

    /// Test hook: use an isolated database directory while exercising DBServer's
    /// own open/reopen path.
    init(_testDatabaseDirectory dataDirURL: URL) {
        self.database = SharedDatabase(dataDirOverride: dataDirURL)
    }

    // MARK: - Constants
    private static let appGroupID      = LIMEPreferenceManager.suiteName
    static let databaseName            = "lime.db"
    static let databaseJournal         = "lime.db-journal"
    static let sharedPrefsBackupName   = "shared_prefs.bak"
    static let preferenceManifestPath  = PreferenceBackupAdapter.manifestPath
    static let databaseBackupName      = "backup.zip"
    static let databaseExt             = ".db"
    static let dbTableRelated          = "related"
    static let bufferSize4KB: Int      = 4096
    static let databaseGenerationKey   = "lime_db_generation"

    // MARK: - Security limits (zip bomb guard)
    /// Maximum cumulative uncompressed size of any archive we extract (500 MB).
    private static let maxExtractTotalBytes: UInt64 = 500 * 1024 * 1024
    /// Maximum number of entries in any archive we extract.
    private static let maxExtractEntries: Int = 10_000
    /// Maximum compression ratio per entry (uncompressed / compressed).
    private static let maxCompressionRatio: Double = 100.0
    /// Maximum byte size of a plist we'll deserialize from user input.
    private static let maxPlistBytes: Int = 1 * 1024 * 1024

    // MARK: - UserDefaults restore allowlist (SEC fix)
    /// Keys that are allowed to be written back from a restored plist backup.
    /// Anything else is silently dropped to prevent tampering with license flags,
    /// server URLs, feature toggles, etc. Add new preference keys here only after
    /// explicit security review.
    private static let restoreAllowedKeys: Set<String> = [
        "phonetic_keyboard_type",
        "smart_chinese_input",
        "keyboard_theme",
        "related_phrase",
        "sort_suggestions",
        "soft_keyboard_show_popup",
        "phonetic_associate_phrase",
        "haptic_feedback",
        "double_space_period",
        "last_im_code",
        "keyboard_height_portrait",
        "keyboard_height_landscape",
        "current_im",
        "select_keyboard_type"
    ]

    // MARK: - Data Directory
    /// App Group container URL (mirrors Android's ContextCompat.getDataDir()).
    private var dataDirURL: URL {
        database.dataDirURL
    }

    // MARK: - LimeDB accessor
    // DBServer uses the process-local SharedDatabase owner instead of owning LimeDB directly.
    private var datasource: LimeDB? {
        get {
            database.current()
        }
        set {
            database.setCurrent(newValue)
        }
    }

    // MARK: - Private helper: close / reopen database around backup-restore critical sections.
    private func closeDatabase() {
        database.closeCurrentForReplacement()
    }

    /// Discards the current `datasource` and rebuilds a fresh `LimeDB` against the
    /// on-disk `lime.db`. Required in the keyboard extension after a Settings-app
    /// restore (#86): the restore happens in the Settings process, replacing the
    /// shared `lime.db` file on disk, but the keyboard extension's own
    /// `DBServer.shared.datasource` still holds a GRDB `DatabaseQueue` bound to the
    /// old (now-swapped/deleted) inode, so every read returns zero rows → zero IMs.
    /// Closing + reopening rebinds the queue to the restored file. Mirrors the
    /// inline rebuild already used inside `backupDatabase` / `restoreDatabase`.
    func reopenDatabaseFromDisk() {
        database.reopenFromDisk()
    }

    func replaceDatabaseFromSnapshot(_ snapshotURL: URL) throws {
        let fm = FileManager.default
        guard fm.fileExists(atPath: snapshotURL.path) else {
            throw DBServerError.fileNotFound(snapshotURL.path)
        }
        let dbURL = dataDirURL.appendingPathComponent(Self.databaseName)
        let tempURL = dataDirURL.appendingPathComponent(Self.databaseName + ".sync_tmp")

        try fm.createDirectory(at: dataDirURL, withIntermediateDirectories: true)
        try? fm.removeItem(at: tempURL)
        try fm.copyItem(at: snapshotURL, to: tempURL)

        let liveDatasource = datasource
        liveDatasource?.holdDBConnection()
        closeDatabase()
        datasource = nil
        defer {
            reopenDatabaseFromDisk()
            datasource?.unHoldDBConnection()
            try? fm.removeItem(at: tempURL)
        }

        for url in [
            dbURL,
            URL(fileURLWithPath: dbURL.path + "-wal"),
            URL(fileURLWithPath: dbURL.path + "-shm")
        ] {
            try? fm.removeItem(at: url)
        }
        try fm.moveItem(at: tempURL, to: dbURL)
    }

    // MARK: - 1. isDatabaseOnHold
    func isDatabaseOnHold() -> Bool {
        return datasource?.isDatabaseOnHold() ?? false
    }

    // MARK: - 2. importTxtTable
    /// Imports a text mapping file (.lime / .cin / delimited) into the specified table.
    /// - Parameters:
    ///   - sourcefile: URL of the text file to import.
    ///   - tablename: Target table name.
    ///   - progress: Optional closure receiving (statusMessage, percentDone).
    func importTxtTable(sourcefile: URL?, tablename: String, progress: ((_ message: String, _ percent: Int) -> Void)? = nil) {
        guard let sourcefile = sourcefile else {
            print("[DBServer] importTxtTable: sourcefile is nil")
            return
        }
        guard FileManager.default.fileExists(atPath: sourcefile.path) else {
            print("[DBServer] importTxtTable: file does not exist at \(sourcefile.path)")
            return
        }
        guard let ds = datasource else {
            print("[DBServer] importTxtTable: datasource is nil")
            return
        }

        ds.setFinish(false)
        ds.setFilename(sourcefile)
        ds.importTxtFileAsync(at: sourcefile.path, tableName: tablename, progress: progress.map { cb in
            { count in cb("Imported \(count) records", 0) }
        }, completion: { [weak ds] _ in
            ds?.resetCache()
        })
    }

    // MARK: - 3 & 4. exportTxtTable
    @discardableResult
    func exportTxtTable(table: String, targetFile: URL, imConfigList: [LimeImConfigRow]?, progress: ((_ message: String, _ percent: Int) -> Void)? = nil) -> Bool {
        guard let ds = datasource else {
            print("[DBServer] exportTxtTable: datasource is nil")
            return false
        }
        return ds.exportTxtTable(table, targetFile: targetFile, imConfig: imConfigList)
    }

    @discardableResult
    func exportTxtTable(table: String, targetFile: URL, imConfigList: [LimeImConfigRow]?) -> Bool {
        return exportTxtTable(table: table, targetFile: targetFile, imConfigList: imConfigList, progress: nil)
    }

    // MARK: - 5. importDbRelated
    func importDbRelated(sourcedb: URL) {
        guard let ds = datasource else { return }
        ds.importDbRelated(sourcedb)
    }

    // MARK: - 6. importZippedDbRelated
    func importZippedDbRelated(compressedSourceDB: URL) {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("limehd_\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }
        do {
            try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
            let unzippedURLs = try unzipReturningFiles(source: compressedSourceDB, targetDir: tempDir)
            guard unzippedURLs.count == 1 else {
                print("[DBServer] importZippedDbRelated: expected 1 file in zip, found \(unzippedURLs.count)")
                return
            }
            importDbRelated(sourcedb: unzippedURLs[0])
            // Mirror Android SearchServer.resetCache(true) — iOS has no static SearchServer,
            // so invalidate the LimeDB-level caches directly.
            datasource?.resetCache()
        } catch {
            print("[DBServer] importZippedDbRelated: error — \(error)")
        }
    }

    // MARK: - 7. importDb
    /// Imports a database file into the specified IM table.
    /// Matches Java `DBServer.importDb(File, String)` which calls
    /// `datasource.importDb(file, tables, overwriteRelated=false, overwriteExisting=true)`.
    /// Note the LimeDB.swift signature uses `(overwriteExisting, includeRelated)`.
    func importDb(sourceDbFile: URL, tableName: String) {
        guard let ds = datasource else { return }
        ds.importDb(sourceFile: sourceDbFile, tableNames: [tableName],
                    overwriteExisting: true, includeRelated: false)
    }

    // MARK: - 8. importZippedDb
    func importZippedDb(sourceDbFile: URL, tableName: String) {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("limehd_\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }
        do {
            try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
            let unzippedURLs = try unzipReturningFiles(source: sourceDbFile, targetDir: tempDir)
            guard unzippedURLs.count == 1 else {
                print("[DBServer] importZippedDb: expected 1 file in zip, found \(unzippedURLs.count)")
                return
            }
            guard let ds = datasource else { return }
            ds.importDb(sourceFile: unzippedURLs[0], tableNames: [tableName],
                        overwriteExisting: true, includeRelated: false)
            ds.resetCache()
        } catch {
            print("[DBServer] importZippedDb: error — \(error)")
        }
    }

    // MARK: - 9. backupDatabase
    /// Backs up the database + shared preferences to the specified file URL.
    /// - Parameter uri: Destination file URL. For document-picker URLs the caller should
    ///   have already started the security-scoped resource; we also start it defensively.
    func backupDatabase(uri: URL, progress: Progress? = nil) throws {
        guard let ds = datasource else {
            throw DBServerError.datasourceUnavailable
        }
        // Capture the live DB path so the post-backup reopen targets the exact
        // file the datasource was using (matches injected test DBs as well as
        // the production App Group path).
        let livePath = ds.dbPath()

        // Start security-scoped access on the destination URL for document-picker sources.
        let startedScopedAccess = uri.startAccessingSecurityScopedResource()
        defer { if startedScopedAccess { uri.stopAccessingSecurityScopedResource() } }

        let dataDir = dataDirURL
        let fileSharedPrefsBackup = dataDir.appendingPathComponent(DBServer.sharedPrefsBackupName)
        let filePreferenceManifest = dataDir.appendingPathComponent(DBServer.preferenceManifestPath)
        // Remove existing shared prefs backup
        try? FileManager.default.removeItem(at: fileSharedPrefsBackup)
        backupDefaultSharedPreference(file: fileSharedPrefsBackup)
        try? FileManager.default.removeItem(at: filePreferenceManifest)
        backupPreferenceCompatibilityManifest(file: filePreferenceManifest)

        // Hold DB and close before zipping
        ds.holdDBConnection()
        closeDatabase()

        // Use a unique temp file to avoid TOCTOU / concurrent-invocation races.
        let tempZip = FileManager.default.temporaryDirectory
            .appendingPathComponent("lime_backup_\(UUID().uuidString).zip")
        try? FileManager.default.removeItem(at: tempZip)

        defer {
            // closeDatabase() above called dbQueue.close(), permanently shutting
            // GRDB's queue down. LimeDB.openDBConnection() is a no-op stub on iOS,
            // so without rebuilding the datasource here every later write hits
            // "SQLite error 21 - out of memory" (SQLITE_MISUSE on a closed handle):
            // IM list reads return empty and reinstall fails until the app is
            // relaunched. Mirror restoreDatabase()'s rebuild pattern.
            datasource = nil
            datasource = try? LimeDB(path: livePath)
            datasource?.unHoldDBConnection()
            try? FileManager.default.removeItem(at: fileSharedPrefsBackup)
            try? FileManager.default.removeItem(at: filePreferenceManifest)
            try? FileManager.default.removeItem(at: tempZip)
        }

        do {
            let dbURL = URL(fileURLWithPath: livePath)
            let journalURL = dbURL.deletingLastPathComponent()
                .appendingPathComponent(DBServer.databaseJournal)

            // Build file list
            var filesToZip: [(URL, String)] = []
            if FileManager.default.fileExists(atPath: dbURL.path) {
                filesToZip.append((dbURL, DBServer.databaseName))
            }
            if FileManager.default.fileExists(atPath: journalURL.path) {
                filesToZip.append((journalURL, DBServer.databaseJournal))
            }
            if FileManager.default.fileExists(atPath: fileSharedPrefsBackup.path) {
                filesToZip.append((fileSharedPrefsBackup, DBServer.sharedPrefsBackupName))
            }
            if FileManager.default.fileExists(atPath: filePreferenceManifest.path) {
                filesToZip.append((filePreferenceManifest, DBServer.preferenceManifestPath))
            }
            guard filesToZip.contains(where: { $0.1 == DBServer.databaseName }) else {
                throw DBServerError.fileNotFound(DBServer.databaseName)
            }

            // Create zip archive
            let archive = try Archive(url: tempZip, accessMode: .create)
            if let progress = progress {
                progress.totalUnitCount = filesToZip.reduce(Int64(0)) { $0 + archive.totalUnitCountForAddingItem(at: $1.0) }
            }
            for (fileURL, entryName) in filesToZip {
                if let progress = progress {
                    // makeProgressForAddingItem is internal in ZIPFoundation, so build
                    // the child Progress ourselves from the public byte-count helper.
                    let entryUnits = archive.totalUnitCountForAddingItem(at: fileURL)
                    let entryProgress = Progress(totalUnitCount: entryUnits)
                    progress.addChild(entryProgress, withPendingUnitCount: entryUnits)
                    try archive.addEntry(with: entryName, fileURL: fileURL, progress: entryProgress)
                } else {
                    try archive.addEntry(with: entryName, fileURL: fileURL)
                }
            }
            guard DBServer.fileSizeBytes(at: tempZip) > 0 else {
                throw DBServerError.archiveCreationFailed
            }

            // Mark the temp zip with complete file protection (contains user dictionary data).
            try? FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.complete], ofItemAtPath: tempZip.path)

            // Copy temp zip to destination URI, removing any existing file first
            // (copyItem throws on collision — that would silently drop the backup).
            try? FileManager.default.removeItem(at: uri)
            try FileManager.default.copyItem(at: tempZip, to: uri)
            guard FileManager.default.fileExists(atPath: uri.path),
                  DBServer.fileSizeBytes(at: uri) > 0 else {
                throw DBServerError.archiveCreationFailed
            }
            // Apply protection on the destination too.
            try? FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.complete], ofItemAtPath: uri.path)
        } catch {
            print("[DBServer] backupDatabase: error — \(error)")
            throw error
        }
    }

    // MARK: - 10. restoreDatabase (URL)
    func restoreDatabase(uri: URL) throws {
        // Start security-scoped access for document-picker URLs.
        let startedScopedAccess = uri.startAccessingSecurityScopedResource()
        defer { if startedScopedAccess { uri.stopAccessingSecurityScopedResource() } }

        let tempZip = FileManager.default.temporaryDirectory
            .appendingPathComponent(DBServer.databaseBackupName + "_restore_\(UUID().uuidString).zip")
        defer { try? FileManager.default.removeItem(at: tempZip) }

        // Use NSFileCoordinator so File Provider extensions (Google Drive, Dropbox, etc.)
        // download the file content before we copy it. Without this, copyItem silently
        // fails for cloud-backed files that are not yet local.
        var coordinatorError: NSError?
        var copyError: Error?
        NSFileCoordinator().coordinate(readingItemAt: uri, options: .withoutChanges, error: &coordinatorError) { coordinatedURL in
            do {
                try FileManager.default.copyItem(at: coordinatedURL, to: tempZip)
            } catch {
                copyError = error
            }
        }
        if let err = coordinatorError ?? copyError {
            print("[DBServer] restoreDatabase(url): error copying file — \(err)")
            throw err
        }
        try validateRestoreFile(at: tempZip.path)
        try restoreDatabase(srcFilePath: tempZip.path)
    }

    // MARK: - 11. restoreDatabase (path)
    func restoreDatabase(srcFilePath: String?) throws {
        guard let srcFilePath = srcFilePath, !srcFilePath.isEmpty else {
            print("[DBServer] restoreDatabase: srcFilePath is nil or empty")
            throw DBServerError.invalidRestoreSource("備份檔路徑是空的")
        }
        try validateRestoreFile(at: srcFilePath)
        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }

        let dataDir = dataDirURL
        let sharedPrefBackup = dataDir.appendingPathComponent(DBServer.sharedPrefsBackupName)
        let preferenceManifest = dataDir.appendingPathComponent(DBServer.preferenceManifestPath)

        ds.holdDBConnection()
        closeDatabase()

        // Temp path: Android backup lands here during extraction, then swapped in after
        // the old datasource closes (so GRDB checkpoints into the OLD lime.db, not the backup).
        let tempDBPath = dataDir.appendingPathComponent(DBServer.databaseName + ".restore_tmp")
        let dbURL = dataDir.appendingPathComponent(DBServer.databaseName)

        var restoreSucceeded = false
        defer {
            // Step 1: release old datasource — GRDB checkpoints its WAL into the OLD lime.db.
            // lime.db is still the pre-restore file at this point, so the checkpoint is safe.
            datasource = nil
            // Step 2: now that the old connection is closed, remove old lime.db + WAL/SHM.
            if restoreSucceeded {
                try? FileManager.default.removeItem(at: dbURL)
                try? FileManager.default.removeItem(at: dataDir.appendingPathComponent("lime.db-wal"))
                try? FileManager.default.removeItem(at: dataDir.appendingPathComponent("lime.db-shm"))
                // Step 3: move the backup (held at temp) into place as the new lime.db.
                if FileManager.default.fileExists(atPath: tempDBPath.path) {
                    try? FileManager.default.moveItem(at: tempDBPath, to: dbURL)
                }
            }
            // Step 4: open a fresh datasource on the restored (or original) file.
            print("[DBServer] restore defer: reopening at \(dbURL.path), exists=\(FileManager.default.fileExists(atPath: dbURL.path)), restoreSucceeded=\(restoreSucceeded)")
            datasource = try? LimeDB(path: dbURL.path)
            ds.unHoldDBConnection()
            if restoreSucceeded {
                if FileManager.default.fileExists(atPath: preferenceManifest.path),
                   restorePreferenceCompatibilityManifest(file: preferenceManifest) {
                    try? FileManager.default.removeItem(at: preferenceManifest)
                    try? FileManager.default.removeItem(at: sharedPrefBackup)
                } else if FileManager.default.fileExists(atPath: sharedPrefBackup.path) {
                    restoreDefaultSharedPreference(file: sharedPrefBackup)
                    try? FileManager.default.removeItem(at: sharedPrefBackup)
                }
                datasource?.checkAndUpdateRelatedTable()
                datasource?.ensureCurrentDatabase()
                markDatabaseReplaced()
            }
        }

        // Clean up any leftover temp file from a previous failed restore.
        try? FileManager.default.removeItem(at: tempDBPath)
        try? FileManager.default.removeItem(at: preferenceManifest)

        do {
            let archive: Archive
            do {
                archive = try Archive(url: URL(fileURLWithPath: srcFilePath), accessMode: .read)
            } catch {
                print("[DBServer] restoreDatabase: cannot open archive at \(srcFilePath): \(error)")
                throw DBServerError.invalidRestoreArchive(srcFilePath)
            }
            // Extract the DB plus preference sidecars — skip directory entries and unknown files.
            // We look for lime.db at any depth (handles both iOS layout "lime.db"
            // and Android layout "databases/lime.db"). Keep scanning after the DB
            // because iOS backups append preferences/lime_prefs.json after lime.db.
            var dbExtracted = false
            for entry in archive {
                guard entry.type == .file else { continue }
                if entry.path == DBServer.preferenceManifestPath {
                    try? FileManager.default.createDirectory(
                        at: preferenceManifest.deletingLastPathComponent(),
                        withIntermediateDirectories: true)
                    try? FileManager.default.removeItem(at: preferenceManifest)
                    _ = try archive.extract(entry, to: preferenceManifest, skipCRC32: false)
                    continue
                }
                if URL(fileURLWithPath: entry.path).lastPathComponent == DBServer.sharedPrefsBackupName {
                    try? FileManager.default.removeItem(at: sharedPrefBackup)
                    _ = try archive.extract(entry, to: sharedPrefBackup, skipCRC32: false)
                    continue
                }
                guard !dbExtracted,
                      URL(fileURLWithPath: entry.path).lastPathComponent == DBServer.databaseName else { continue }
                try? FileManager.default.removeItem(at: tempDBPath)
                _ = try archive.extract(entry, to: tempDBPath, skipCRC32: false)
                try? FileManager.default.setAttributes(
                    [.protectionKey: FileProtectionType.complete], ofItemAtPath: tempDBPath.path)
                let size = ((try? FileManager.default.attributesOfItem(atPath: tempDBPath.path))?[.size] as? Int) ?? -1
                print("[DBServer] restore: extracted '\(entry.path)' to temp, size=\(size)")
                dbExtracted = true
            }
            guard dbExtracted else {
                print("[DBServer] restoreDatabase: lime.db not found in archive")
                throw DBServerError.missingDatabaseInRestoreArchive
            }
            try validateRestoreFile(at: tempDBPath.path)
            restoreSucceeded = true
        } catch {
            print("[DBServer] restoreDatabase: extract failed — \(error)")
            try? FileManager.default.removeItem(at: tempDBPath)
            throw error
        }
    }

    private func validateRestoreFile(at path: String) throws {
        guard FileManager.default.fileExists(atPath: path) else {
            print("[DBServer] restoreDatabase: file not found at \(path)")
            throw DBServerError.fileNotFound(path)
        }
        let size = ((try? FileManager.default.attributesOfItem(atPath: path))?[.size] as? NSNumber)?.uint64Value ?? 0
        guard size > 0 else {
            print("[DBServer] restoreDatabase: file is empty at \(path)")
            throw DBServerError.emptyRestoreFile(path)
        }
    }

    // MARK: - Restore bundled DB (factory reset — copies lime.db from app bundle to App Group)
    func restoreBundledDatabase() throws {
        guard let bundledURL = Bundle.main.url(forResource: "lime", withExtension: "db") else {
            throw DBServerError.fileNotFound("lime.db (bundled)")
        }
        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }

        let dataDir = dataDirURL
        let dbURL = dataDir.appendingPathComponent(DBServer.databaseName)
        let tempDBPath = dataDir.appendingPathComponent(DBServer.databaseName + ".restore_tmp")

        ds.holdDBConnection()
        closeDatabase()

        var restoreSucceeded = false
        defer {
            // Release old datasource — GRDB checkpoints its WAL into the OLD lime.db.
            datasource = nil
            if restoreSucceeded {
                try? FileManager.default.removeItem(at: dbURL)
                try? FileManager.default.removeItem(at: dataDir.appendingPathComponent("lime.db-wal"))
                try? FileManager.default.removeItem(at: dataDir.appendingPathComponent("lime.db-shm"))
                if FileManager.default.fileExists(atPath: tempDBPath.path) {
                    try? FileManager.default.moveItem(at: tempDBPath, to: dbURL)
                }
            }
            print("[DBServer] restoreBundledDatabase defer: reopening at \(dbURL.path), restoreSucceeded=\(restoreSucceeded)")
            datasource = try? LimeDB(path: dbURL.path)
            ds.unHoldDBConnection()
            if restoreSucceeded {
                datasource?.checkAndUpdateRelatedTable()
                datasource?.ensureCurrentDatabase()
                markDatabaseReplaced()
            }
        }

        try? FileManager.default.removeItem(at: tempDBPath)
        do {
            try FileManager.default.copyItem(at: bundledURL, to: tempDBPath)
            try? FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.complete], ofItemAtPath: tempDBPath.path)
            restoreSucceeded = true
        } catch {
            print("[DBServer] restoreBundledDatabase: copy failed — \(error)")
            try? FileManager.default.removeItem(at: tempDBPath)
            throw error
        }
    }

    // MARK: - 12. backupDefaultSharedPreference
    /// Serialises the restore-allowed subset of UserDefaults to a plist file.
    /// Only keys in `restoreAllowedKeys` are persisted so an attacker-crafted
    /// backup cannot smuggle arbitrary preferences back in on restore.
    func backupDefaultSharedPreference(file: URL) {
        try? FileManager.default.removeItem(at: file)

        let defaults = UserDefaults(suiteName: DBServer.appGroupID) ?? UserDefaults.standard
        let full = defaults.dictionaryRepresentation()
        var filtered: [String: Any] = [:]
        for (key, value) in full where DBServer.restoreAllowedKeys.contains(key) {
            filtered[key] = value
        }

        do {
            let data = try PropertyListSerialization.data(fromPropertyList: filtered,
                                                          format: .binary, options: 0)
            try writeProtected(data, to: file)
        } catch {
            print("[DBServer] backupDefaultSharedPreference: error — \(error)")
        }
    }

    func backupPreferenceCompatibilityManifest(file: URL) {
        let defaults = UserDefaults(suiteName: DBServer.appGroupID) ?? UserDefaults.standard
        do {
            try FileManager.default.createDirectory(
                at: file.deletingLastPathComponent(),
                withIntermediateDirectories: true)
            let data = try PreferenceBackupAdapter.exportManifestData(defaults: defaults, sourcePlatform: "ios")
            try writeProtected(data, to: file)
        } catch {
            print("[DBServer] backupPreferenceCompatibilityManifest: error — \(error)")
        }
    }

    private func markDatabaseReplaced() {
        guard let defaults = UserDefaults(suiteName: DBServer.appGroupID) else { return }
        defaults.set(defaults.integer(forKey: DBServer.databaseGenerationKey) + 1,
                     forKey: DBServer.databaseGenerationKey)
        defaults.synchronize()
    }

    @discardableResult
    func restorePreferenceCompatibilityManifest(file: URL) -> Bool {
        guard FileManager.default.fileExists(atPath: file.path) else { return false }
        let defaults = UserDefaults(suiteName: DBServer.appGroupID) ?? UserDefaults.standard
        do {
            let data = try Data(contentsOf: file)
            return try PreferenceBackupAdapter.restoreManifestData(data, defaults: defaults)
        } catch {
            print("[DBServer] restorePreferenceCompatibilityManifest: error — \(error)")
            return false
        }
    }

    // MARK: - 13. restoreDefaultSharedPreference
    /// Reads a plist file back into UserDefaults, applying an allowlist + size cap.
    func restoreDefaultSharedPreference(file: URL) {
        guard FileManager.default.fileExists(atPath: file.path) else {
            print("[DBServer] restoreDefaultSharedPreference: file not found")
            return
        }
        // Cap file size to defeat DoS via absurdly large plists.
        let attrs = try? FileManager.default.attributesOfItem(atPath: file.path)
        if let size = (attrs?[.size] as? NSNumber)?.intValue, size > DBServer.maxPlistBytes {
            print("[DBServer] restoreDefaultSharedPreference: plist exceeds size cap (\(size) bytes)")
            return
        }
        do {
            let data = try Data(contentsOf: file)
            guard data.count <= DBServer.maxPlistBytes else {
                print("[DBServer] restoreDefaultSharedPreference: in-memory size exceeds cap")
                return
            }
            guard let dict = try PropertyListSerialization.propertyList(
                    from: data, format: nil) as? [String: Any] else {
                print("[DBServer] restoreDefaultSharedPreference: invalid plist format")
                return
            }
            let defaults = UserDefaults(suiteName: DBServer.appGroupID) ?? UserDefaults.standard
            // Apply only keys on the allowlist; drop everything else silently.
            for (key, value) in dict where DBServer.restoreAllowedKeys.contains(key) {
                defaults.set(value, forKey: key)
            }
            defaults.synchronize()
        } catch {
            print("[DBServer] restoreDefaultSharedPreference: error — \(error)")
        }
    }

    // MARK: - 14. unzip
    /// Java-compat wrapper that writes every archive entry to the single `targetFile`
    /// path under `targetFolder` (last entry wins — mirrors the Java version).
    /// The zip-bomb cap still applies to the archive as a whole.
    func unzip(source: URL, targetFolder: String, targetFile: String, removeOriginal: Bool) {
        guard FileManager.default.fileExists(atPath: source.path) else {
            print("[DBServer] unzip: source file does not exist at \(source.path)")
            return
        }
        let targetFolderURL = URL(fileURLWithPath: targetFolder)
        // Validate that `targetFile` itself is a safe filename (no traversal).
        guard let outputURL = safeDestination(forEntryPath: targetFile, in: targetFolderURL) else {
            print("[DBServer] unzip: unsafe targetFile path rejected: \(targetFile)")
            return
        }

        do {
            try FileManager.default.createDirectory(at: targetFolderURL, withIntermediateDirectories: true)
            try? FileManager.default.removeItem(at: outputURL)

            let archive = try Archive(url: source, accessMode: .read)
            // Enforce zip-bomb caps on the archive before extracting.
            _ = try validatedEntries(of: archive, targetDir: targetFolderURL)

            var extracted = false
            for entry in archive {
                _ = try archive.extract(entry, to: outputURL, skipCRC32: false)
                extracted = true
            }
            if !extracted {
                print("[DBServer] unzip: no entries found in archive")
            } else {
                try? FileManager.default.setAttributes(
                    [.protectionKey: FileProtectionType.complete], ofItemAtPath: outputURL.path)
            }
            if removeOriginal {
                try? FileManager.default.removeItem(at: source)
            }
        } catch {
            print("[DBServer] unzip: error — \(error)")
        }
    }

    // MARK: - 15. zip
    func zip(source: URL, targetFolder: String, targetFile: String) {
        guard FileManager.default.fileExists(atPath: source.path),
              !source.hasDirectoryPath else {
            print("[DBServer] zip: source file does not exist or is a directory: \(source.path)")
            return
        }
        let targetFolderURL = URL(fileURLWithPath: targetFolder)
        let outputURL = targetFolderURL.appendingPathComponent(targetFile)

        do {
            try FileManager.default.createDirectory(at: targetFolderURL, withIntermediateDirectories: true)
            try? FileManager.default.removeItem(at: outputURL)

            let archive = try Archive(url: outputURL, accessMode: .create)
            // Use only the file name as the entry name (mirrors Java's ZipEntry(sourceFile.getName()))
            try archive.addEntry(with: source.lastPathComponent, fileURL: source)
        } catch {
            print("[DBServer] zip: error — \(error)")
        }
    }

    /// Returns the URL of a bundled blank template file, searching the main bundle
    /// first then the keyboard extension bundle. Returns nil if not bundled.
    /// Android ships these as `R.raw.blank` / `R.raw.blankrelated`; on iOS they
    /// need to be added as resources to the app target.
    private func bundledBlankTemplateURL(named name: String) -> URL? {
        if let url = Bundle.main.url(forResource: name, withExtension: "db") {
            return url
        }
        // Fall back to the keyboard extension's own bundle if we're running inside it.
        for bundle in Bundle.allBundles {
            if let url = bundle.url(forResource: name, withExtension: "db") {
                return url
            }
        }
        return nil
    }

    private func rawTableHasData(databaseURL: URL, tableName: String) throws -> Bool {
        let queue = try DatabaseQueue(path: databaseURL.path)
        defer { try? queue.close() }
        return try queue.read { db in
            let exists = try Int.fetchOne(db,
                sql: "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                arguments: [tableName]) ?? 0
            guard exists > 0 else { return false }
            return try (Int.fetchOne(db,
                sql: "SELECT EXISTS(SELECT 1 FROM \(tableName) LIMIT 1)") ?? 0) > 0
        }
    }

    // MARK: - 16. exportZippedDb
    /// Exports a single IM table to a zipped .db file.
    /// Uses bundled `blank.db` when available, otherwise creates a temporary blank schema.
    @discardableResult
    func exportZippedDb(tableName: String?, targetDbFile: URL?, progressCallback: (() -> Void)? = nil) -> URL? {
        guard let tableName = tableName, let targetDbFile = targetDbFile else {
            print("[DBServer] exportZippedDb: invalid parameters")
            return nil
        }
        guard let ds = datasource else {
            print("[DBServer] exportZippedDb: datasource is nil")
            return nil
        }
        guard ds.isValidTableName(tableName), ds.tableHasData(tableName) else {
            print("[DBServer] exportZippedDb: table is invalid or empty: \(tableName)")
            return nil
        }
        do {
            let cacheDir = FileManager.default.temporaryDirectory
            let dbFile = cacheDir.appendingPathComponent(tableName + DBServer.databaseExt)

            try? FileManager.default.removeItem(at: dbFile)
            try? FileManager.default.removeItem(at: targetDbFile)

            progressCallback?()

            if let template = bundledBlankTemplateURL(named: "blank") {
                try FileManager.default.copyItem(at: template, to: dbFile)
            }

            ds.prepareBackup(targetFile: dbFile, tableNames: [tableName], includeRelated: false)
            guard try rawTableHasData(databaseURL: dbFile, tableName: "custom") else {
                try? FileManager.default.removeItem(at: dbFile)
                return nil
            }

            let archive = try Archive(url: targetDbFile, accessMode: .create)
            try archive.addEntry(with: dbFile.lastPathComponent, fileURL: dbFile)
            try? FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.complete], ofItemAtPath: targetDbFile.path)

            try? FileManager.default.removeItem(at: dbFile)
            return targetDbFile
        } catch {
            print("[DBServer] exportZippedDb: error — \(error)")
            return nil
        }
    }

    // MARK: - 17. exportZippedDbRelated
    /// Exports the related-phrase table to a zipped .db file.
    /// Uses bundled `blankrelated.db` when available, otherwise creates a temporary blank schema.
    @discardableResult
    func exportZippedDbRelated(targetFile: URL?, progressCallback: (() -> Void)? = nil) -> URL? {
        guard let targetFile = targetFile else {
            print("[DBServer] exportZippedDbRelated: invalid parameters")
            return nil
        }
        guard let ds = datasource else {
            print("[DBServer] exportZippedDbRelated: datasource is nil")
            return nil
        }
        do {
            let cacheDir = FileManager.default.temporaryDirectory
            let dbFile = cacheDir.appendingPathComponent(DBServer.dbTableRelated + DBServer.databaseExt)

            try? FileManager.default.removeItem(at: dbFile)
            try? FileManager.default.removeItem(at: targetFile)

            progressCallback?()

            if let template = bundledBlankTemplateURL(named: "blankrelated") {
                try FileManager.default.copyItem(at: template, to: dbFile)
            } else {
                _ = try LimeDB(path: dbFile.path)
            }

            ds.prepareBackup(targetFile: dbFile, tableNames: [], includeRelated: true)

            let archive = try Archive(url: targetFile, accessMode: .create)
            try archive.addEntry(with: dbFile.lastPathComponent, fileURL: dbFile)
            try? FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.complete], ofItemAtPath: targetFile.path)

            try? FileManager.default.removeItem(at: dbFile)
            return targetFile
        } catch {
            print("[DBServer] exportZippedDbRelated: error — \(error)")
            return nil
        }
    }

    // MARK: - Private zip helpers

    /// Validates a zip entry path is safe to extract under `baseDir`.
    /// Rejects absolute paths, paths containing `..`, and any path whose resolved
    /// destination would escape the base directory (zip-slip defence, CWE-22).
    private func safeDestination(forEntryPath entryPath: String, in baseDir: URL) -> URL? {
        // Reject empty, absolute, or traversal paths up front.
        if entryPath.isEmpty || entryPath.hasPrefix("/") { return nil }
        let components = entryPath.split(separator: "/", omittingEmptySubsequences: true)
        if components.contains("..") { return nil }
        let candidate = baseDir.appendingPathComponent(entryPath).standardizedFileURL
        let base = baseDir.standardizedFileURL
        let basePath = base.path.hasSuffix("/") ? base.path : base.path + "/"
        // The candidate must live strictly under the base directory.
        guard candidate.path.hasPrefix(basePath) || candidate.path == base.path else { return nil }
        return candidate
    }

    /// Sums uncompressed sizes and rejects archives that would explode past caps.
    /// Returns the validated list of `(entry, destURL)` tuples ready for extraction,
    /// or throws `DBServerError.zipBombDetected` on cap violation.
    private func validatedEntries(of archive: Archive, targetDir: URL) throws -> [(Entry, URL)] {
        var totalBytes: UInt64 = 0
        var count = 0
        var result: [(Entry, URL)] = []
        for entry in archive {
            // Skip directory entries — parent dirs are created during file extraction.
            if entry.type == .directory { continue }
            count += 1
            if count > DBServer.maxExtractEntries { throw DBServerError.zipBombDetected }
            totalBytes = totalBytes &+ UInt64(entry.uncompressedSize)
            if totalBytes > DBServer.maxExtractTotalBytes { throw DBServerError.zipBombDetected }
            if entry.compressedSize > 0 {
                let ratio = Double(entry.uncompressedSize) / Double(entry.compressedSize)
                if ratio > DBServer.maxCompressionRatio { throw DBServerError.zipBombDetected }
            }
            guard let destURL = safeDestination(forEntryPath: entry.path, in: targetDir) else {
                throw DBServerError.unsafeZipEntry(entry.path)
            }
            result.append((entry, destURL))
        }
        return result
    }

    /// Writes `data` to `url` with iOS complete file protection when possible.
    private func writeProtected(_ data: Data, to url: URL) throws {
        try data.write(to: url, options: [.atomic, .completeFileProtection])
    }

    /// Unzips all entries from source into targetDir and returns their URLs.
    /// Safe: validates each entry against zip-slip and zip-bomb caps.
    private func unzipReturningFiles(source: URL, targetDir: URL) throws -> [URL] {
        let archive = try Archive(url: source, accessMode: .read)
        let validated = try validatedEntries(of: archive, targetDir: targetDir)
        var results: [URL] = []
        for (entry, destURL) in validated {
            try? FileManager.default.createDirectory(
                at: destURL.deletingLastPathComponent(),
                withIntermediateDirectories: true)
            _ = try archive.extract(entry, to: destURL, skipCRC32: false)
            // Apply complete file protection after extraction
            // (ZIPFoundation does not set protection attributes itself).
            try? FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.complete], ofItemAtPath: destURL.path)
            results.append(destURL)
        }
        return results
    }

    // MARK: - IM Config Proxies

    func getAllImConfigs() throws -> [ImConfig] {
        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }
        return try ds.getAllImConfigs()
    }

    func getImConfig(_ imCode: String, _ field: String) -> String {
        datasource?.getImConfig(imCode, field) ?? ""
    }

    func getImConfigList(_ code: String?, _ configEntry: String?) -> [LimeImConfigRow] {
        datasource?.getImConfigList(code, configEntry) ?? []
    }

    func setImConfig(_ imCode: String, _ field: String, _ value: String) {
        datasource?.setImConfig(imCode, field, value)
    }

    func updateIMEnabled(imName: String, enabled: Bool) {
        datasource?.updateIMEnabled(imName: imName, enabled: enabled)
    }

    func updateIMSortOrder(id: Int64, sortOrder: Int) throws {
        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }
        try ds.updateIMSortOrder(id: id, sortOrder: sortOrder)
    }

    func getKeyboardConfigList() -> [KeyboardConfig]? {
        datasource?.getKeyboardConfigList()
    }

    func setImConfigKeyboard(_ imCode: String, _ keyboard: KeyboardConfig) {
        datasource?.setImConfigKeyboard(imCode, keyboard)
    }

    // MARK: - Record CRUD Proxies

    func getRecordList(_ table: String, _ query: String?, searchByCode: Bool,
                       _ maxResult: Int, _ offset: Int) -> [LimeRecord] {
        datasource?.getRecordList(table, query, searchByCode: searchByCode,
                                  maxResult, offset) ?? []
    }

    func countRecords(_ table: String, _ whereClause: String?,
                      _ whereArgs: [String]?) -> Int {
        datasource?.countRecords(table, whereClause, whereArgs) ?? 0
    }

    @discardableResult
    func addRecord(_ table: String, _ values: [String: Any?]) -> Int64 {
        datasource?.addRecord(table, values) ?? -1
    }

    @discardableResult
    func updateRecord(_ table: String, _ values: [String: Any?],
                      _ whereClause: String?, _ whereArgs: [String]?) -> Int {
        datasource?.updateRecord(table, values, whereClause, whereArgs) ?? 0
    }

    @discardableResult
    func deleteRecord(_ table: String, _ whereClause: String?,
                      _ whereArgs: [String]?) -> Int {
        datasource?.deleteRecord(table, whereClause, whereArgs) ?? 0
    }

    // MARK: - SearchServer factory

    /// Returns a SearchServer backed by the process-local SharedDatabase owner.
    /// The provider re-reads the current LimeDB on every query, so a Settings-side
    /// close/reopen can replace the queue without rebuilding SearchServer.
    func makeSearchServer() -> SearchServer? {
        guard let initialDatasource = datasource else { return nil }
        let database = self.database
        return SearchServer(dbProvider: { database.current() ?? initialDatasource })
    }

    // MARK: - Keyboard Runtime Bootstrap

    /// - Parameter forceReopen: When true, discards the cached `datasource` and
    ///   rebuilds it against the on-disk `lime.db` before reading IM configs. The
    ///   keyboard extension passes `true` after a Settings-app restore (#86) so the
    ///   stale GRDB queue bound to the pre-restore inode is replaced and IM reads
    ///   see the restored data instead of returning zero IMs.
    func prepareKeyboardRuntimeDatabase(forceReopen: Bool = false) throws -> KeyboardRuntimeContext {
        _ = try database.ensureDatabaseFile()

        if forceReopen { reopenDatabaseFromDisk() }

        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }
        // No phonetic auto-seed: aligning with Android, the bundled default DB ships
        // empty IM tables and no phonetic side-file. Restore-to-default => empty IM list.
        importRelatedIfNeeded()

        let allIMs = (try? ds.getAllImConfigs()) ?? []
        // After a restore (#86), `keyboard_state` holds row offsets captured against
        // the PRE-restore im-table ordering; applying them to the restored table can
        // resolve the wrong (or zero) IMs. Ignore the saved index map on forceReopen
        // and rebuild the activated list from the restored `enabled` flags instead.
        let keyboardState = forceReopen
            ? ""
            : (UserDefaults(suiteName: DBServer.appGroupID)?.string(forKey: "keyboard_state") ?? "")
        var activated: [ImConfig]
        if keyboardState.isEmpty {
            activated = allIMs.filter { $0.enabled }
        } else {
            let enabledIndices = Set(keyboardState.components(separatedBy: ";"))
            activated = allIMs.enumerated()
                .filter { enabledIndices.contains(String($0.offset)) }
                .map { $0.element }
        }
        // Align with Android buildActivatedIMList(): the activated IM list is built
        // ONLY from enabled DB rows. When the DB has no enabled IMs (e.g. after
        // restore-to-default), the list stays empty and the keyboard runs English-only
        // (initOnStartInput uses the English layout when activatedIMs.isEmpty).
        // No fabricated fallback IM list — that is what resurrected phonetic on iOS.
        if activated.isEmpty { activated = allIMs.filter { $0.enabled } }

        // `initialIM` only seeds the SearchServer's internal table pointer; when
        // `activated` is empty it is never surfaced (keyboard is English-only).
        let firstNick = activated.first?.tableNick
            ?? allIMs.first(where: { $0.enabled })?.tableNick
            ?? "phonetic"
        let initialIM = firstNick.isEmpty ? "phonetic" : firstNick
        let database = self.database
        let searchServer = SearchServer(dbProvider: { database.current() ?? ds })
        let capabilities = searchServer.detectIMCapabilities(tableName: initialIM)
        searchServer.setTableName(initialIM,
                                  hasNumberMapping: capabilities.hasNumber,
                                  hasSymbolMapping: capabilities.hasSymbol)
        return KeyboardRuntimeContext(searchServer: searchServer,
                                      activatedIMs: activated,
                                      initialIM: initialIM,
                                      capabilities: capabilities)
    }

    private func copyBundledDatabase(to destinationURL: URL) throws {
        try database.copyBundledDatabase(to: destinationURL)
    }

    private func importRelatedIfNeeded() {
        guard !tableHasData("related") else { return }
        guard let sourceURL = Bundle.main.url(forResource: "lime", withExtension: "db") else { return }
        importDbRelated(sourcedb: sourceURL)
        datasource?.resetCache()
    }

    // MARK: - Related Proxies

    func getRelated(_ pword: String?, _ maximum: Int, _ offset: Int) -> [Related] {
        datasource?.getRelated(pword, maximum, offset) ?? []
    }

    // MARK: - Validation Proxy

    func isValidTableName(_ name: String?) -> Bool {
        datasource?.isValidTableName(name) ?? false
    }

    // MARK: - Import / Export Proxies

    func importFromAttachedDB(sourcePath: String, tableName: String) throws {
        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }
        try ds.importFromAttachedDB(sourcePath: sourcePath, tableName: tableName)
    }

    func importTxtFile(at path: String, tableName: String,
                       progress: ((Int) -> Void)?) throws {
        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }
        try ds.importTxtFile(at: path, tableName: tableName, progress: progress)
    }

    func exportDB(to destPath: String) throws {
        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }
        try ds.exportDB(to: destPath)
    }

    func tableHasData(_ name: String) -> Bool {
        datasource?.tableHasData(name) ?? false
    }

    func importFromZip(at zipURL: URL, tableName: String) throws {
        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }
        try ds.importFromZip(at: zipURL, tableName: tableName)
    }

    func registerIM(imName: String, tableName: String, label: String, keyboardId: String) throws {
        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }
        try ds.registerIM(imName: imName, tableName: tableName, label: label, keyboardId: keyboardId)
    }

    func seedCustomIM() throws {
        guard let ds = datasource else { throw DBServerError.datasourceUnavailable }
        try ds.seedCustomIM()
    }

    private static func fileSizeBytes(at url: URL) -> Int64 {
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
              let size = attrs[.size] as? NSNumber else {
            return 0
        }
        return size.int64Value
    }
}

// MARK: - DBServerError
enum DBServerError: Error {
    case datasourceUnavailable
    case bundledDatabaseMissing
    case archiveCreationFailed
    case fileNotFound(String)
    case emptyRestoreFile(String)
    case invalidRestoreSource(String)
    case invalidRestoreArchive(String)
    case missingDatabaseInRestoreArchive
    case unsafeZipEntry(String)       // SEC: zip-slip rejected entry path
    case zipBombDetected              // SEC: archive exceeded size/count/ratio cap
    case securityScopedAccessDenied   // SEC: couldn't start security-scoped resource
}

extension DBServerError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .datasourceUnavailable:
            return "資料庫尚未開啟"
        case .bundledDatabaseMissing:
            return "找不到預設資料庫"
        case .archiveCreationFailed:
            return "無法建立備份壓縮檔"
        case .fileNotFound(let path):
            return "找不到檔案：\(path)"
        case .emptyRestoreFile:
            return "備份檔是空的"
        case .invalidRestoreSource(let message):
            return message
        case .invalidRestoreArchive:
            return "備份檔格式不正確"
        case .missingDatabaseInRestoreArchive:
            return "備份檔內找不到 lime.db"
        case .unsafeZipEntry:
            return "備份檔包含不安全的路徑"
        case .zipBombDetected:
            return "備份檔過大或壓縮格式異常"
        case .securityScopedAccessDenied:
            return "無法取得檔案存取權限"
        }
    }
}

// MARK: - Test hook
extension DBServer {
    // Test hook — do not use in production code
    var _datasourceForTesting: LimeDB? { datasource }
}
