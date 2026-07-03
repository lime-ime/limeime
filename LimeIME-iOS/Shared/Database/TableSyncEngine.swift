import Foundation
import GRDB

struct SyncEvent: Equatable {
    enum Kind: Equatable { case epochApplied, imported, dropped, failed, noop }
    let kind: Kind
    let stem: String?
}

final class TableSyncEngine {
    private enum ImportOutcome {
        case imported
        case skipped
        case silentSkip
        case paused
        case failed
    }

    private enum StartImport {
        case start(marker: Int64)
        case skip
        case silentSkip
    }

    private struct RestoreInfo {
        let epochUUID: String
        let schemaVersion: Int
    }

    private let database: SharedDatabase
    private let baseURL: URL
    private let fileManager = FileManager.default
    private let chunkSize = 20_000
    private let maxAttempts = 3
    private let validStems: Set<String> = [
        "custom", "cj", "scj", "cj5", "ecj", "dayi", "phonetic", "ez",
        "array", "array10", "wb", "hs", "pinyin", "cj4", "related"
    ]

    init(database: SharedDatabase, baseURL: URL) {
        self.database = database
        self.baseURL = baseURL
    }

    @discardableResult
    func scanAndApply(deadline: Date? = nil) -> [SyncEvent] {
        var events: [SyncEvent] = []

        if let epochEvent = applyRestoreEpochIfNeeded() {
            events.append(epochEvent)
            if epochEvent.kind == .failed { return events }
        }

        let snapshot = tableSourceSnapshot()
        let sources = snapshot.sources
        let sourceStems = snapshot.stems
        var silentSkip = false

        for source in sources {
            switch importStem(source.stem, from: source.url, identity: source.identity, deadline: deadline) {
            case .imported:
                events.append(SyncEvent(kind: .imported, stem: source.stem))
            case .failed:
                events.append(SyncEvent(kind: .failed, stem: source.stem))
            case .paused:
                return events
            case .silentSkip:
                silentSkip = true
            case .skipped:
                break
            }
        }

        guard let db = database.current() else { return events }
        for entry in db.allLedgerEntries() where !sourceStems.contains(entry.stem) {
            guard validStems.contains(entry.stem) else { continue }
            if dropStem(entry.stem, in: db) {
                events.append(SyncEvent(kind: .dropped, stem: entry.stem))
            }
        }

        if events.isEmpty && !silentSkip {
            events.append(SyncEvent(kind: .noop, stem: nil))
        }
        return events
    }

    private func applyRestoreEpochIfNeeded() -> SyncEvent? {
        let restoreURL = SyncPaths.restoreDB(baseURL)
        guard fileManager.fileExists(atPath: restoreURL.path) else { return nil }
        guard let restoreInfo = readRestoreInfo(from: restoreURL) else {
            return SyncEvent(kind: .failed, stem: "restore")
        }
        guard let currentDB = database.current() else {
            return SyncEvent(kind: .failed, stem: "restore")
        }
        let currentEpoch = currentDB.syncMeta("epoch_uuid") ?? (try? currentDB.ensureEpochUUID())
        guard restoreInfo.epochUUID != currentEpoch else { return nil }
        guard restoreInfo.schemaVersion <= LimeDB.CURRENT_DB_VERSION else {
            return SyncEvent(kind: .failed, stem: "restore")
        }

        do {
            database.closeCurrentForReplacement()
            try replaceCanonicalDatabase(with: restoreURL)
            database.setCurrent(nil)
            _ = database.current()
            return SyncEvent(kind: .epochApplied, stem: nil)
        } catch {
            database.setCurrent(nil)
            _ = database.current()
            return SyncEvent(kind: .failed, stem: "restore")
        }
    }

    private func readRestoreInfo(from restoreURL: URL) -> RestoreInfo? {
        let metaURL = SyncPaths.restoreMeta(baseURL)
        if let data = try? Data(contentsOf: metaURL),
           let meta = try? JSONDecoder().decode(RestoreMeta.self, from: data) {
            return RestoreInfo(epochUUID: meta.epochUUID, schemaVersion: meta.schemaVersion)
        }

        var config = Configuration()
        config.readonly = true
        guard let queue = try? DatabaseQueue(path: restoreURL.path, configuration: config) else { return nil }
        defer { try? queue.close() }
        return try? queue.read { db in
            let hasMeta = try Int.fetchOne(db, sql: """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'table' AND name = 'sync_meta'
            """) ?? 0
            guard hasMeta > 0,
                  let epoch = try String.fetchOne(db,
                                                  sql: "SELECT value FROM sync_meta WHERE key = 'epoch_uuid'") else {
                return nil
            }
            let versionText = try String.fetchOne(db,
                                                  sql: "SELECT value FROM sync_meta WHERE key = 'schema_version'")
            let userVersion = try Int.fetchOne(db, sql: "PRAGMA user_version") ?? LimeDB.CURRENT_DB_VERSION
            return RestoreInfo(epochUUID: epoch,
                               schemaVersion: versionText.flatMap(Int.init) ?? userVersion)
        }
    }

    private func replaceCanonicalDatabase(with restoreURL: URL) throws {
        let canonicalURL = database.dataDirURL.appendingPathComponent("lime.db")
        let parent = canonicalURL.deletingLastPathComponent()
        try fileManager.createDirectory(at: parent, withIntermediateDirectories: true)

        removeSidecars(for: canonicalURL)
        let tmp = parent.appendingPathComponent("lime.db.restore-\(UUID().uuidString)")
        defer { try? fileManager.removeItem(at: tmp) }
        try fileManager.copyItem(at: restoreURL, to: tmp)
        if fileManager.fileExists(atPath: canonicalURL.path) {
            _ = try fileManager.replaceItemAt(canonicalURL, withItemAt: tmp)
        } else {
            try fileManager.moveItem(at: tmp, to: canonicalURL)
        }
        removeSidecars(for: canonicalURL)
    }

    private func removeSidecars(for dbURL: URL) {
        for suffix in ["-wal", "-shm", "-journal"] {
            try? fileManager.removeItem(atPath: dbURL.path + suffix)
        }
    }

    private func tableSourceSnapshot() -> (sources: [(stem: String, url: URL, identity: FileIdentity)],
                                           stems: Set<String>) {
        let tablesDir = SyncPaths.tablesDir(baseURL)
        guard let urls = try? fileManager.contentsOfDirectory(at: tablesDir,
                                                              includingPropertiesForKeys: nil) else {
            return ([], [])
        }
        var stems = Set<String>()
        let sources = urls.compactMap { url -> (stem: String, url: URL, identity: FileIdentity)? in
            guard url.pathExtension == "limedb" else { return nil }
            let stem = url.deletingPathExtension().lastPathComponent
            guard validStems.contains(stem) else { return nil }
            stems.insert(stem)
            guard let identity = FileIdentity(url: url) else { return nil }
            return (stem, url, identity)
        }.sorted { $0.stem < $1.stem }
        return (sources, stems)
    }

    private func importStem(_ stem: String,
                            from sourceURL: URL,
                            identity: FileIdentity,
                            deadline: Date?) -> ImportOutcome {
        guard let db = database.current() else { return .failed }
        do {
            let restoreLearning = readTableMeta(stem)?.restoreLearning ?? true
            let start = try beginImport(stem: stem,
                                        identity: identity,
                                        restoreLearning: restoreLearning,
                                        in: db)
            switch start {
            case .skip:
                return .skipped
            case .silentSkip:
                return .silentSkip
            case .start(let startMarker):
                let alias = "src_\(UUID().uuidString.replacingOccurrences(of: "-", with: "_"))"
                try attach(sourceURL, as: alias, in: db)
                defer { detach(alias, in: db) }
                let columns = try importColumns(stem: stem, alias: alias, in: db)
                var marker = startMarker
                while true {
                    guard let nextMarker = try copyChunk(stem: stem,
                                                         alias: alias,
                                                         columns: columns,
                                                         marker: marker,
                                                         identity: identity,
                                                         in: db) else {
                        break
                    }
                    marker = nextMarker
                    if let deadline, Date() >= deadline {
                        return .paused
                    }
                }
                try finishImport(stem: stem,
                                 identity: identity,
                                 restoreLearning: restoreLearning,
                                 in: db)
                return .imported
            }
        } catch {
            markFailed(stem: stem, identity: identity, error: error, in: db)
            return .failed
        }
    }

    private func beginImport(stem: String,
                             identity: FileIdentity,
                             restoreLearning: Bool,
                             in limeDB: LimeDB) throws -> StartImport {
        try limeDB.dbQueue.write { db in
            let ledger = try ledgerEntry(stem: stem, in: db)
            if let ledger, ledger.identity == identity {
                if ledger.state == .done { return .skip }
                if ledger.state == .failed && ledger.attempts >= maxAttempts { return .silentSkip }
                if ledger.state == .inProgress {
                    return .start(marker: ledger.resumeMarker ?? 0)
                }
            }

            try ensureDestinationTable(stem, in: db)
            try ensureStashTable(in: db)
            try db.execute(sql: "DELETE FROM sync_stash WHERE stem = ?", arguments: [stem])
            if restoreLearning {
                let columns = try tableColumns(stem, schema: nil, in: db)
                if columns.contains("code"), columns.contains("word"), columns.contains("score") {
                    try db.execute(sql: """
                        INSERT INTO sync_stash (stem, code, word, score)
                        SELECT ?, code, word, score FROM \(quoted(stem)) WHERE score <> 0
                    """, arguments: [stem])
                }
            }
            try db.execute(sql: "DELETE FROM \(quoted(stem))")
            // ponytail: same-identity environmental retries stop after three appears.
            let attempts = min((ledger?.attempts ?? 0) + 1, maxAttempts)
            try limeDB.upsertLedger(LedgerEntry(stem: stem,
                                                identity: identity,
                                                state: .inProgress,
                                                error: nil,
                                                attempts: attempts,
                                                resumeMarker: 0),
                                    in: db)
            return .start(marker: 0)
        }
    }

    private func attach(_ sourceURL: URL, as alias: String, in limeDB: LimeDB) throws {
        try limeDB.dbQueue.writeWithoutTransaction { db in
            try db.execute(sql: "ATTACH DATABASE ? AS \(quoted(alias))",
                           arguments: ["\(sourceURL.absoluteString)?immutable=1"])
        }
    }

    private func detach(_ alias: String, in limeDB: LimeDB) {
        try? limeDB.dbQueue.writeWithoutTransaction { db in
            try db.execute(sql: "DETACH DATABASE \(quoted(alias))")
        }
    }

    private func importColumns(stem: String, alias: String, in limeDB: LimeDB) throws -> [String] {
        try limeDB.dbQueue.read { db in
            let sourceExists = try Int.fetchOne(db, sql: """
                SELECT COUNT(*) FROM \(quoted(alias)).sqlite_master
                WHERE type = 'table' AND name = ?
            """, arguments: [stem]) ?? 0
            guard sourceExists > 0 else { throw SyncImportError.missingSourceTable(stem) }

            let source = Set(try tableColumns(stem, schema: alias, in: db))
            let destination = try tableColumns(stem, schema: nil, in: db)
            let excluded: Set<String> = ["id", "_id", "rowid"]
            let columns = destination.filter { source.contains($0) && !excluded.contains($0.lowercased()) }
            guard !columns.isEmpty else { throw SyncImportError.noSharedColumns(stem) }
            return columns
        }
    }

    private func copyChunk(stem: String,
                           alias: String,
                           columns: [String],
                           marker: Int64,
                           identity: FileIdentity,
                           in limeDB: LimeDB) throws -> Int64? {
        try limeDB.dbQueue.write { db in
            guard let lastRowID = try Int64.fetchOne(db, sql: """
                SELECT MAX(rowid) FROM (
                    SELECT rowid FROM \(quoted(alias)).\(quoted(stem))
                    WHERE rowid > ?
                    ORDER BY rowid
                    LIMIT \(chunkSize)
                )
            """, arguments: [marker]) else {
                return nil
            }

            let columnList = columns.map(quoted).joined(separator: ", ")
            try db.execute(sql: """
                INSERT INTO \(quoted(stem)) (\(columnList))
                SELECT \(columnList)
                FROM \(quoted(alias)).\(quoted(stem))
                WHERE rowid > ? AND rowid <= ?
                ORDER BY rowid
            """, arguments: [marker, lastRowID])

            let current = try ledgerEntry(stem: stem, in: db)
            try limeDB.upsertLedger(LedgerEntry(stem: stem,
                                                identity: identity,
                                                state: .inProgress,
                                                error: nil,
                                                attempts: current?.attempts ?? 1,
                                                resumeMarker: lastRowID),
                                    in: db)
            return lastRowID
        }
    }

    private func finishImport(stem: String,
                              identity: FileIdentity,
                              restoreLearning: Bool,
                              in limeDB: LimeDB) throws {
        try limeDB.dbQueue.write { db in
            if restoreLearning {
                let columns = try tableColumns(stem, schema: nil, in: db)
                if columns.contains("code"), columns.contains("word"), columns.contains("score") {
                    try db.execute(sql: """
                        UPDATE \(quoted(stem))
                        SET score = (
                            SELECT s.score FROM sync_stash s
                            WHERE s.stem = ? AND s.code = \(quoted(stem)).code AND s.word = \(quoted(stem)).word
                            LIMIT 1
                        )
                        WHERE EXISTS (
                            SELECT 1 FROM sync_stash s
                            WHERE s.stem = ? AND s.code = \(quoted(stem)).code AND s.word = \(quoted(stem)).word
                        )
                    """, arguments: [stem, stem])
                }
            }
            try db.execute(sql: "DELETE FROM sync_stash WHERE stem = ?", arguments: [stem])
            try limeDB.upsertLedger(LedgerEntry(stem: stem,
                                                identity: identity,
                                                state: .done,
                                                error: nil,
                                                attempts: 0,
                                                resumeMarker: nil),
                                    in: db)
        }
    }

    private func dropStem(_ stem: String, in limeDB: LimeDB) -> Bool {
        do {
            try limeDB.dbQueue.write { db in
                try ensureDestinationTable(stem, in: db)
                try db.execute(sql: "DELETE FROM \(quoted(stem))")
                try limeDB.deleteLedger(stem: stem, in: db)
            }
            return true
        } catch {
            return false
        }
    }

    private func markFailed(stem: String, identity: FileIdentity, error: Error, in limeDB: LimeDB) {
        try? limeDB.dbQueue.write { db in
            let current = try ledgerEntry(stem: stem, in: db)
            let attempts = min(max(current?.attempts ?? 1, 1), maxAttempts)
            try limeDB.upsertLedger(LedgerEntry(stem: stem,
                                                identity: identity,
                                                state: .failed,
                                                error: String(describing: error),
                                                attempts: attempts,
                                                resumeMarker: current?.resumeMarker),
                                    in: db)
        }
    }

    private func readTableMeta(_ stem: String) -> TableMeta? {
        let url = SyncPaths.tableMeta(baseURL, stem: stem)
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(TableMeta.self, from: data)
    }

    private func ensureDestinationTable(_ stem: String, in db: Database) throws {
        if stem == "related" {
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS related (
                    _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    pword     TEXT,
                    cword     TEXT,
                    basescore INTEGER DEFAULT 0,
                    score     INTEGER DEFAULT 0
                )
            """)
            return
        }

        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS \(quoted(stem)) (
                _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                code      TEXT,
                word      TEXT,
                score     INTEGER DEFAULT 0,
                basescore INTEGER DEFAULT 0,
                code3r    TEXT
            )
        """)
    }

    private func ensureStashTable(in db: Database) throws {
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS sync_stash (
                stem  TEXT,
                code  TEXT,
                word  TEXT,
                score INTEGER
            )
        """)
    }

    private func tableColumns(_ table: String, schema: String?, in db: Database) throws -> [String] {
        try String.fetchAll(db,
                            sql: "SELECT name FROM pragma_table_info(?, ?) ORDER BY cid",
                            arguments: [table, schema ?? "main"])
    }

    private func ledgerEntry(stem: String, in db: Database) throws -> LedgerEntry? {
        guard let row = try Row.fetchOne(db,
                                         sql: "SELECT * FROM sync_ledger WHERE stem = ?",
                                         arguments: [stem]) else { return nil }
        let size: Int64? = row["size"]
        let mtime: Double? = row["mtime"]
        let stateRaw: String? = row["state"]
        let identity = size.flatMap { size in mtime.map { FileIdentity(size: size, mtime: $0) } }
        return LedgerEntry(stem: row["stem"] ?? stem,
                           identity: identity,
                           state: LedgerState(rawValue: stateRaw ?? "") ?? .pending,
                           error: row["error"],
                           attempts: row["attempts"] ?? 0,
                           resumeMarker: row["resume_marker"])
    }

    private func quoted(_ identifier: String) -> String {
        "\"\(identifier.replacingOccurrences(of: "\"", with: "\"\""))\""
    }
}

private enum SyncImportError: Error {
    case missingSourceTable(String)
    case noSharedColumns(String)
}
