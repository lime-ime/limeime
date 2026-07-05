import Foundation
import GRDB

final class SyncDatabaseConnection {
    private let queue: DatabaseQueue

    init(databaseURL: URL, busyTimeoutMilliseconds: Int = 5_000) throws {
        try FileManager.default.createDirectory(at: databaseURL.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        var config = Configuration()
        config.prepareDatabase { db in
            try db.execute(sql: "PRAGMA busy_timeout = \(busyTimeoutMilliseconds)")
        }
        queue = try DatabaseQueue(path: databaseURL.path, configuration: config)
    }

    deinit {
        try? queue.close()
    }

    func read<T>(_ body: (Database) throws -> T) throws -> T {
        try queue.read(body)
    }

    func write<T>(_ body: (Database) throws -> T) throws -> T {
        try queue.write(body)
    }

    func writeWithoutTransaction<T>(_ body: (Database) throws -> T) throws -> T {
        try queue.writeWithoutTransaction(body)
    }

    func busyTimeoutMilliseconds() throws -> Int {
        try read { db in
            try Int.fetchOne(db, sql: "PRAGMA busy_timeout") ?? 0
        }
    }
}

final class SyncMetaStore {
    static let epochUUIDKey = "epoch_uuid"
    static let generationKey = "generation"
    static let appliedEpochKey = "applied_epoch"
    static let appliedGenerationKey = "applied_generation"

    private let connection: SyncDatabaseConnection

    init(databaseURL: URL, busyTimeoutMilliseconds: Int = 5_000) throws {
        connection = try SyncDatabaseConnection(databaseURL: databaseURL,
                                                busyTimeoutMilliseconds: busyTimeoutMilliseconds)
        try ensureTable()
    }

    func value(forKey key: String) throws -> String? {
        try connection.read { db in
            try String.fetchOne(db, sql: "SELECT value FROM sync_meta WHERE key = ?",
                                arguments: [key])
        }
    }

    func setValue(_ value: String, forKey key: String) throws {
        guard Self.isAllowedKey(key) else { throw SyncMetaStoreError.unsupportedKey(key) }
        try connection.write { db in
            try db.execute(sql: """
                INSERT INTO sync_meta(key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """, arguments: [key, value])
        }
    }

    func removeValue(forKey key: String) throws {
        try connection.write { db in
            try db.execute(sql: "DELETE FROM sync_meta WHERE key = ?", arguments: [key])
        }
    }

    func epochUUID() throws -> String? {
        try value(forKey: Self.epochUUIDKey)
    }

    @discardableResult
    func replaceEpochUUID() throws -> String {
        let uuid = UUID().uuidString
        try setValue(uuid, forKey: Self.epochUUIDKey)
        return uuid
    }

    func generation() throws -> Int {
        try intValue(forKey: Self.generationKey)
    }

    func appliedEpoch() throws -> String? {
        try value(forKey: Self.appliedEpochKey)
    }

    func setAppliedEpoch(_ epoch: String) throws {
        try setValue(epoch, forKey: Self.appliedEpochKey)
    }

    func appliedGeneration() throws -> Int {
        try intValue(forKey: Self.appliedGenerationKey)
    }

    func setAppliedGeneration(_ generation: Int) throws {
        try setValue(String(generation), forKey: Self.appliedGenerationKey)
    }

    @discardableResult
    func bumpGeneration() throws -> Int {
        try bumpCounter(forKey: Self.generationKey)
    }

    func revision(forTable table: String) throws -> Int {
        try intValue(forKey: Self.revisionKey(forTable: table))
    }

    @discardableResult
    func bumpRevision(forTable table: String) throws -> Int {
        try bumpCounter(forKey: Self.revisionKey(forTable: table))
    }

    private func ensureTable() throws {
        try connection.write { db in
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS sync_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
                """)
        }
    }

    private func intValue(forKey key: String) throws -> Int {
        guard let raw = try value(forKey: key), let value = Int(raw) else { return 0 }
        return value
    }

    private func bumpCounter(forKey key: String) throws -> Int {
        try connection.write { db in
            let raw = try String.fetchOne(db, sql: "SELECT value FROM sync_meta WHERE key = ?",
                                          arguments: [key])
            let next = (raw.flatMap(Int.init) ?? 0) + 1
            try db.execute(sql: """
                INSERT INTO sync_meta(key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """, arguments: [key, String(next)])
            return next
        }
    }

    private static func revisionKey(forTable table: String) -> String {
        "rev:\(table)"
    }

    private static func isAllowedKey(_ key: String) -> Bool {
        key == epochUUIDKey
            || key == generationKey
            || key == appliedEpochKey
            || key == appliedGenerationKey
            || key.hasPrefix("rev:")
    }
}

enum SyncMetaStoreError: Error {
    case unsupportedKey(String)
}

struct SyncDatabaseLocator {
    static let databaseName = "lime.db"
    let appGroupDirectory: URL
    let applicationSupportDirectory: URL

    var coldDatabaseURL: URL {
        appGroupDirectory.appendingPathComponent(Self.databaseName)
    }

    var hotDatabaseURL: URL {
        applicationSupportDirectory
            .appendingPathComponent("LimeIME", isDirectory: true)
            .appendingPathComponent(Self.databaseName)
    }

    static func production() -> SyncDatabaseLocator {
        SyncDatabaseLocator(
            appGroupDirectory: appGroupDirectory() ?? fallbackDirectory(),
            applicationSupportDirectory: applicationSupportDirectory())
    }

    static func appGroupDirectory() -> URL? {
        FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: LIMEPreferenceManager.suiteName)
    }

    static func applicationSupportDirectory() -> URL {
        FileManager.default.urls(for: .applicationSupportDirectory,
                                 in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
    }

    static func isKeyboardExtension(bundle: Bundle = .main) -> Bool {
        bundle.bundleURL.pathExtension == "appex"
            || bundle.bundleIdentifier?.hasSuffix(".keyboard") == true
    }

    static func liveDatabaseDirectory() -> URL {
        let locator = production()
        return isKeyboardExtension()
            ? locator.hotDatabaseURL.deletingLastPathComponent()
            : locator.coldDatabaseURL.deletingLastPathComponent()
    }

    private static func fallbackDirectory() -> URL {
        applicationSupportDirectory().appendingPathComponent("LimeIME", isDirectory: true)
    }
}

enum SyncDatabaseBootstrap {
    enum Result: Equatable {
        case alreadyPresent
        case adoptedLegacy
        case copiedBundledDefault
    }

    static func ensureKeyboardHotDatabase(hotDatabaseURL: URL,
                                          legacyDatabaseURL: URL?,
                                          bundledDefaultURL: URL) throws -> Result {
        let fm = FileManager.default
        guard !fm.fileExists(atPath: hotDatabaseURL.path) else { return .alreadyPresent }

        if let legacyDatabaseURL,
           fm.fileExists(atPath: legacyDatabaseURL.path),
           quickCheckOK(legacyDatabaseURL) {
            try copySQLiteFiles(from: legacyDatabaseURL, to: hotDatabaseURL)
            _ = try SyncMetaStore(databaseURL: hotDatabaseURL).replaceEpochUUID()
            return .adoptedLegacy
        }

        try copySQLiteFiles(from: bundledDefaultURL, to: hotDatabaseURL)
        _ = try SyncMetaStore(databaseURL: hotDatabaseURL).replaceEpochUUID()
        return .copiedBundledDefault
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

    private static func copySQLiteFiles(from source: URL, to destination: URL) throws {
        let fm = FileManager.default
        try fm.createDirectory(at: destination.deletingLastPathComponent(),
                               withIntermediateDirectories: true)
        for url in sqliteFileSet(for: destination) where fm.fileExists(atPath: url.path) {
            try fm.removeItem(at: url)
        }
        try fm.copyItem(at: source, to: destination)
        for suffix in ["-wal", "-shm"] {
            let sidecar = URL(fileURLWithPath: source.path + suffix)
            guard fm.fileExists(atPath: sidecar.path) else { continue }
            try fm.copyItem(at: sidecar, to: URL(fileURLWithPath: destination.path + suffix))
        }
    }

    private static func sqliteFileSet(for databaseURL: URL) -> [URL] {
        [
            databaseURL,
            URL(fileURLWithPath: databaseURL.path + "-wal"),
            URL(fileURLWithPath: databaseURL.path + "-shm")
        ]
    }
}
