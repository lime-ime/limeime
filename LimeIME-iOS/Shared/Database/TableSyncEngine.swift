import Foundation
import GRDB

struct SyncEvent: Equatable {
    enum Kind: Equatable { case epochApplied, imported, dropped, failed, noop, exported, metaSynced }
    let kind: Kind
    let stem: String?
}

final class TableSyncEngine {
    private struct ColdRev {
        let stem: String
        let rev: Int64
        let mode: SyncRevMode
    }

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

    private let database: SharedDatabase
    private let baseURL: URL
    private let prefs: LIMEPreferenceManager
    private let fileManager = FileManager.default
    // ponytail: fixed import chunk from I0 timing; tune only if device memory/time profiles change.
    private let chunkSize = 20_000
    // ponytail: same-identity environmental retries stop after three appears.
    private let maxAttempts = 3
    private let validStems: Set<String> = [
        "custom", "cj", "scj", "cj5", "ecj", "dayi", "phonetic", "ez",
        "array", "array10", "wb", "hs", "pinyin", "cj4", "related"
    ]

    init(database: SharedDatabase, baseURL: URL, prefs: LIMEPreferenceManager = .shared) {
        self.database = database
        self.baseURL = baseURL
        self.prefs = prefs
    }

    @discardableResult
    func scanAndApply(deadline: Date? = nil) -> [SyncEvent] {
        guard let db = database.current() else {
            return [SyncEvent(kind: .failed, stem: "hot")]
        }
        guard let sidecar = readColdMeta() else {
            if let exportEvent = exportSnapshotIfRequested() {
                return [exportEvent]
            }
            return [SyncEvent(kind: .noop, stem: nil)]
        }
        let currentEpoch = db.syncMeta("epoch_uuid") ?? (try? db.ensureEpochUUID()) ?? ""
        if appliedGeneration(in: db) == sidecar.generation,
           sidecar.epochUUID == currentEpoch {
            if let exportEvent = exportSnapshotIfRequested() {
                return [exportEvent]
            }
            return [SyncEvent(kind: .noop, stem: nil)]
        }

        let coldURL = SyncPaths.coldDB(baseURL)
        let alias = "cold_\(UUID().uuidString.replacingOccurrences(of: "-", with: "_"))"
        var attached = false

        do {
            try attach(coldURL, as: alias, in: db)
            attached = true
            defer {
                if attached { detach(alias, in: db) }
            }

            guard try coldGeneration(alias: alias, in: db) == sidecar.generation else {
                return [SyncEvent(kind: .noop, stem: nil)]
            }
            guard sidecar.schemaVersion <= LimeDB.CURRENT_DB_VERSION else {
                return [SyncEvent(kind: .failed, stem: "cold")]
            }

            let coldRevs = try coldSyncRevs(alias: alias, in: db)
            if sidecar.epochUUID != currentEpoch {
                detach(alias, in: db)
                attached = false
                var events = applyEpochSnapshot(sidecar: sidecar, coldURL: coldURL, coldRevs: coldRevs)
                if let exportEvent = exportSnapshotIfRequested() {
                    events.append(exportEvent)
                }
                return events.isEmpty ? [SyncEvent(kind: .noop, stem: nil)] : events
            }

            let coldIMCodes = try coldIMCodes(alias: alias, in: db)
            let dropStems = try coldRevs.values.reduce(into: Set<String>()) { result, rev in
                guard validStems.contains(rev.stem),
                      !coldIMCodes.contains(rev.stem),
                      try coldTableIsEmpty(stem: rev.stem, alias: alias, in: db)
                else { return }
                result.insert(rev.stem)
            }

            var events: [SyncEvent] = []
            var unsettled = false
            let imChanged = try mirrorIM(alias: alias, in: db)

            for rev in coldRevs.values.sorted(by: { $0.stem < $1.stem }) {
                guard validStems.contains(rev.stem), !dropStems.contains(rev.stem) else { continue }
                let ledger = db.ledgerEntry(stem: rev.stem)
                guard ledger?.rev != rev.rev || ledger?.state != .done else { continue }
                switch importStem(rev.stem, rev: rev.rev, mode: rev.mode,
                                  alias: alias, deadline: deadline) {
                case .imported:
                    events.append(SyncEvent(kind: .imported, stem: rev.stem))
                case .failed:
                    unsettled = true
                    events.append(SyncEvent(kind: .failed, stem: rev.stem))
                case .paused:
                    return events
                case .silentSkip:
                    unsettled = true
                case .skipped:
                    break
                }
            }

            for entry in db.allLedgerEntries() where validStems.contains(entry.stem) {
                if coldRevs[entry.stem] == nil || dropStems.contains(entry.stem) {
                    if dropStem(entry.stem, in: db) {
                        events.append(SyncEvent(kind: .dropped, stem: entry.stem))
                    } else {
                        unsettled = true
                        events.append(SyncEvent(kind: .failed, stem: entry.stem))
                    }
                }
            }

            if imChanged && events.isEmpty {
                events.append(SyncEvent(kind: .metaSynced, stem: nil))
            }
            if !unsettled {
                try db.setSyncMeta("applied_generation", "\(sidecar.generation)")
            }
            if !unsettled, let exportEvent = exportSnapshotIfRequested() {
                events.append(exportEvent)
            }
            if events.isEmpty {
                events.append(SyncEvent(kind: .noop, stem: nil))
            }
            return events
        } catch {
            return [SyncEvent(kind: .failed, stem: "cold")]
        }
    }

    private func readColdMeta() -> ColdSnapshotMeta? {
        guard let data = try? Data(contentsOf: SyncPaths.coldMeta(baseURL)) else { return nil }
        return try? JSONDecoder().decode(ColdSnapshotMeta.self, from: data)
    }

    private func appliedGeneration(in db: LimeDB) -> Int64 {
        Int64(db.syncMeta("applied_generation") ?? "") ?? 0
    }

    private func coldGeneration(alias: String, in limeDB: LimeDB) throws -> Int64 {
        try limeDB.dbQueue.read { db in
            let raw = try String.fetchOne(db,
                sql: "SELECT value FROM \(quoted(alias)).sync_meta WHERE key = 'generation'")
            return Int64(raw ?? "") ?? -1
        }
    }

    private func coldSyncRevs(alias: String, in limeDB: LimeDB) throws -> [String: ColdRev] {
        try limeDB.dbQueue.read { db in
            let rows = try Row.fetchAll(db,
                sql: "SELECT stem, rev, mode FROM \(quoted(alias)).sync_rev ORDER BY stem")
            var result: [String: ColdRev] = [:]
            for row in rows {
                let stem: String = row["stem"]
                let rev: Int64 = row["rev"]
                let rawMode: String = row["mode"]
                result[stem] = ColdRev(stem: stem,
                                       rev: rev,
                                       mode: SyncRevMode(rawValue: rawMode) ?? .merge)
            }
            return result
        }
    }

    private func coldIMCodes(alias: String, in limeDB: LimeDB) throws -> Set<String> {
        try limeDB.dbQueue.read { db in
            let hasIM = try tableExists("im", schema: alias, in: db)
            guard hasIM else { return [] }
            return Set(try String.fetchAll(db, sql: "SELECT code FROM \(quoted(alias)).im"))
        }
    }

    private func coldTableIsEmpty(stem: String, alias: String, in limeDB: LimeDB) throws -> Bool {
        try limeDB.dbQueue.read { db in
            guard try tableExists(stem, schema: alias, in: db) else { return true }
            let count = try Int.fetchOne(db,
                sql: "SELECT COUNT(*) FROM \(quoted(alias)).\(quoted(stem))") ?? 0
            return count == 0
        }
    }

    private func mirrorIM(alias: String, in limeDB: LimeDB) throws -> Bool {
        try limeDB.dbQueue.write { db in
            let hot = try imSnapshot(schema: nil, in: db)
            let cold = try imSnapshot(schema: alias, in: db)
            guard hot != cold else { return false }
            try db.execute(sql: "DELETE FROM im")
            try db.execute(sql: """
                INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                SELECT code, title, desc, keyboard, disable, selkey, endkey, spacestyle
                FROM \(quoted(alias)).im
            """)
            return true
        }
    }

    private func imSnapshot(schema: String?, in db: Database) throws -> [String] {
        let prefix = schema.map { "\(quoted($0))." } ?? ""
        guard try tableExists("im", schema: schema, in: db) else { return [] }
        return try String.fetchAll(db, sql: """
            SELECT quote(code) || char(31) || quote(title) || char(31) ||
                   quote(desc) || char(31) || quote(keyboard) || char(31) ||
                   quote(disable) || char(31) || quote(selkey) || char(31) ||
                   quote(endkey) || char(31) || quote(spacestyle)
            FROM \(prefix)im
            ORDER BY 1
        """)
    }

    private func tableExists(_ table: String, schema: String?, in db: Database) throws -> Bool {
        let prefix = schema.map { "\(quoted($0))." } ?? ""
        let count = try Int.fetchOne(db, sql: """
            SELECT COUNT(*) FROM \(prefix)sqlite_master
            WHERE type = 'table' AND name = ?
        """, arguments: [table]) ?? 0
        return count > 0
    }

    private func applyEpochSnapshot(sidecar: ColdSnapshotMeta,
                                    coldURL: URL,
                                    coldRevs: [String: ColdRev]) -> [SyncEvent] {
        let stashURL = database.dataDirURL
            .appendingPathComponent("sync-stash-\(UUID().uuidString).limedb")
        defer {
            try? fileManager.removeItem(at: stashURL)
            removeSidecars(for: stashURL)
        }

        do {
            if let oldHot = database.current() {
                try stashLearnedRows(from: oldHot, to: stashURL)
            }
            database.closeCurrentForReplacement()
            removeSidecars(for: coldURL)
            try replaceCanonicalDatabase(with: coldURL)
            database.setCurrent(nil)
            guard let reopened = database.current() else {
                return [SyncEvent(kind: .failed, stem: "hot")]
            }
            try mergeLearnedRows(from: stashURL, into: reopened)
            try reopened.dbQueue.write { db in
                try reopened.wipeLedger(in: db)
                for rev in coldRevs.values where validStems.contains(rev.stem) {
                    try reopened.upsertLedger(LedgerEntry(stem: rev.stem,
                                                          identity: nil,
                                                          rev: rev.rev,
                                                          state: .done,
                                                          error: nil,
                                                          attempts: 0,
                                                          resumeMarker: nil),
                                              in: db)
                }
                try db.execute(sql: """
                    INSERT OR REPLACE INTO sync_meta (key, value) VALUES ('applied_generation', ?)
                """, arguments: ["\(sidecar.generation)"])
            }
            return [SyncEvent(kind: .epochApplied, stem: nil)]
        } catch {
            database.setCurrent(nil)
            _ = database.current()
            return [SyncEvent(kind: .failed, stem: "cold")]
        }
    }

    private func stashLearnedRows(from limeDB: LimeDB, to stashURL: URL) throws {
        try? fileManager.removeItem(at: stashURL)
        removeSidecars(for: stashURL)
        try fileManager.createDirectory(at: stashURL.deletingLastPathComponent(),
                                        withIntermediateDirectories: true)
        try limeDB.dbQueue.writeWithoutTransaction { db in
            try db.execute(sql: "ATTACH DATABASE ? AS stash", arguments: [stashURL.path])
            defer { try? db.execute(sql: "DETACH DATABASE stash") }
            try db.execute(sql: """
                CREATE TABLE stash.sync_stash (
                    stem TEXT, keya TEXT, keyb TEXT, score INTEGER
                )
            """)
            for stem in validStems where prefs.restoreOnImport(for: stem) {
                let keys = learnKeys(for: stem)
                let columns = try tableColumns(stem, schema: nil, in: db)
                guard columns.contains(keys.a), columns.contains(keys.b), columns.contains("score") else {
                    continue
                }
                try db.execute(sql: """
                    INSERT INTO stash.sync_stash (stem, keya, keyb, score)
                    SELECT ?, \(quoted(keys.a)), \(quoted(keys.b)), score
                    FROM \(quoted(stem)) WHERE score <> 0
                """, arguments: [stem])
            }
        }
    }

    private func mergeLearnedRows(from stashURL: URL, into limeDB: LimeDB) throws {
        guard fileManager.fileExists(atPath: stashURL.path) else { return }
        try limeDB.dbQueue.writeWithoutTransaction { db in
            try db.execute(sql: "ATTACH DATABASE ? AS stash", arguments: [stashURL.path])
            defer { try? db.execute(sql: "DETACH DATABASE stash") }
            let stems = try String.fetchAll(db, sql: "SELECT DISTINCT stem FROM stash.sync_stash")
            for stem in stems where validStems.contains(stem) {
                try ensureDestinationTable(stem, in: db)
                let keys = learnKeys(for: stem)
                let columns = try tableColumns(stem, schema: nil, in: db)
                guard columns.contains(keys.a), columns.contains(keys.b), columns.contains("score") else {
                    continue
                }
                let keyA = quoted(keys.a)
                let keyB = quoted(keys.b)
                try db.execute(sql: """
                    UPDATE \(quoted(stem))
                    SET score = (
                        SELECT s.score FROM stash.sync_stash s
                        WHERE s.stem = ?
                          AND s.keya IS \(quoted(stem)).\(keyA)
                          AND s.keyb IS \(quoted(stem)).\(keyB)
                        LIMIT 1
                    )
                    WHERE EXISTS (
                        SELECT 1 FROM stash.sync_stash s
                        WHERE s.stem = ?
                          AND s.keya IS \(quoted(stem)).\(keyA)
                          AND s.keyb IS \(quoted(stem)).\(keyB)
                    )
                """, arguments: [stem, stem])
            }
        }
    }

    private func replaceCanonicalDatabase(with restoreURL: URL) throws {
        let canonicalURL = database.dataDirURL.appendingPathComponent("lime.db")
        let parent = canonicalURL.deletingLastPathComponent()
        try fileManager.createDirectory(at: parent, withIntermediateDirectories: true)

        removeSidecars(for: restoreURL)
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

    private func importStem(_ stem: String,
                            rev: Int64,
                            mode: SyncRevMode,
                            alias: String,
                            deadline: Date?) -> ImportOutcome {
        guard let db = database.current() else { return .failed }
        do {
            let start = try beginImport(stem: stem,
                                        rev: rev,
                                        mode: mode,
                                        in: db)
            switch start {
            case .skip:
                return .skipped
            case .silentSkip:
                return .silentSkip
            case .start(let startMarker):
                let columns = try importColumns(stem: stem, alias: alias, in: db)
                var marker = startMarker
                while true {
                    guard let nextMarker = try copyChunk(stem: stem,
                                                         alias: alias,
                                                         columns: columns,
                                                         marker: marker,
                                                         rev: rev,
                                                         in: db) else {
                        break
                    }
                    marker = nextMarker
                    if let deadline, Date() >= deadline {
                        return .paused
                    }
                }
                try finishImport(stem: stem,
                                 rev: rev,
                                 restoreLearning: mode == .merge,
                                 in: db)
                return .imported
            }
        } catch {
            markFailed(stem: stem, rev: rev, error: error, in: db)
            return .failed
        }
    }

    private func beginImport(stem: String,
                             rev: Int64,
                             mode: SyncRevMode,
                             in limeDB: LimeDB) throws -> StartImport {
        try limeDB.dbQueue.write { db in
            let ledger = try ledgerEntry(stem: stem, in: db)
            if let ledger, ledger.rev == rev {
                if ledger.state == .done { return .skip }
                if ledger.state == .failed && ledger.attempts >= maxAttempts { return .silentSkip }
                if ledger.state == .inProgress {
                    // ponytail: two live keyboard processes can resume the same marker; add per-process claim tokens with staleness if duplicate imports show up in device traces.
                    try ensureStashTable(in: db)
                    return .start(marker: ledger.resumeMarker ?? 0)
                }
            }

            try ensureDestinationTable(stem, in: db)
            try ensureStashTable(in: db)
            try db.execute(sql: "DELETE FROM sync_stash WHERE stem = ?", arguments: [stem])
            if mode == .merge {
                let keys = learnKeys(for: stem)
                let columns = try tableColumns(stem, schema: nil, in: db)
                if columns.contains(keys.a), columns.contains(keys.b), columns.contains("score") {
                    try db.execute(sql: """
                        INSERT INTO sync_stash (stem, keya, keyb, score)
                        SELECT ?, \(quoted(keys.a)), \(quoted(keys.b)), score
                        FROM \(quoted(stem)) WHERE score <> 0
                    """, arguments: [stem])
                }
            }
            try db.execute(sql: "DELETE FROM \(quoted(stem))")
            let attempts = min((ledger?.attempts ?? 0) + 1, maxAttempts)
            try limeDB.upsertLedger(LedgerEntry(stem: stem,
                                                identity: nil,
                                                rev: rev,
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
                           rev: Int64,
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
                                                identity: nil,
                                                rev: rev,
                                                state: .inProgress,
                                                error: nil,
                                                attempts: current?.attempts ?? 1,
                                                resumeMarker: lastRowID),
                                    in: db)
            return lastRowID
        }
    }

    private func finishImport(stem: String,
                              rev: Int64,
                              restoreLearning: Bool,
                              in limeDB: LimeDB) throws {
        try limeDB.dbQueue.write { db in
            if restoreLearning {
                try ensureStashTable(in: db)
                let keys = learnKeys(for: stem)
                let columns = try tableColumns(stem, schema: nil, in: db)
                if columns.contains(keys.a), columns.contains(keys.b), columns.contains("score") {
                    let keyA = quoted(keys.a)
                    let keyB = quoted(keys.b)
                    try db.execute(sql: """
                        UPDATE \(quoted(stem))
                        SET score = (
                            SELECT s.score FROM sync_stash s
                            WHERE s.stem = ?
                              AND s.keya IS \(quoted(stem)).\(keyA)
                              AND s.keyb IS \(quoted(stem)).\(keyB)
                            LIMIT 1
                        )
                        WHERE EXISTS (
                            SELECT 1 FROM sync_stash s
                            WHERE s.stem = ?
                              AND s.keya IS \(quoted(stem)).\(keyA)
                              AND s.keyb IS \(quoted(stem)).\(keyB)
                        )
                    """, arguments: [stem, stem])
                }
            }
            try db.execute(sql: "DELETE FROM sync_stash WHERE stem = ?", arguments: [stem])
            try limeDB.upsertLedger(LedgerEntry(stem: stem,
                                                identity: nil,
                                                rev: rev,
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
                if stem != "related" {
                    try db.execute(sql: "DELETE FROM im WHERE code = ?", arguments: [stem])
                }
                try limeDB.deleteLedger(stem: stem, in: db)
            }
            return true
        } catch {
            return false
        }
    }

    private func exportSnapshotIfRequested() -> SyncEvent? {
        let requestURL = SyncPaths.exportRequest(baseURL)
        guard let data = try? Data(contentsOf: requestURL),
              let request = try? JSONDecoder().decode(ExportRequest.self, from: data),
              request.expiresAt >= Date().timeIntervalSince1970,
              let db = database.current()
        else {
            return nil
        }

        let outbox = SyncPaths.outboxDir(baseURL)
        let snapshot = SyncPaths.backupSnapshot(baseURL)
        let temp = outbox.appendingPathComponent("backup.limedb.tmp-\(UUID().uuidString)")
        defer { try? fileManager.removeItem(at: temp) }

        do {
            try fileManager.createDirectory(at: outbox, withIntermediateDirectories: true)
            try? fileManager.removeItem(at: temp)
            try db.vacuumInto(temp.path)
            if fileManager.fileExists(atPath: snapshot.path) {
                _ = try fileManager.replaceItemAt(snapshot, withItemAt: temp)
            } else {
                try fileManager.moveItem(at: temp, to: snapshot)
            }
            let epoch = db.syncMeta("epoch_uuid") ?? (try? db.ensureEpochUUID()) ?? ""
            let receipt = ExportReceipt(requestUUID: request.requestUUID,
                                        epochUUID: epoch,
                                        at: Date().timeIntervalSince1970)
            try atomicWrite(JSONEncoder().encode(receipt), to: SyncPaths.receipt(baseURL))
            try? fileManager.removeItem(at: requestURL)
            return SyncEvent(kind: .exported, stem: nil)
        } catch {
            return nil
        }
    }

    private func markFailed(stem: String, rev: Int64, error: Error, in limeDB: LimeDB) {
        try? limeDB.dbQueue.write { db in
            let current = try ledgerEntry(stem: stem, in: db)
            let attempts = min(max(current?.attempts ?? 1, 1), maxAttempts)
            try limeDB.upsertLedger(LedgerEntry(stem: stem,
                                                identity: nil,
                                                rev: rev,
                                                state: .failed,
                                                error: String(describing: error),
                                                attempts: attempts,
                                                resumeMarker: current?.resumeMarker),
                                    in: db)
        }
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
        let columns = try tableColumns("sync_stash", schema: nil, in: db)
        if !columns.isEmpty,
           !(columns.contains("stem") && columns.contains("keya") &&
             columns.contains("keyb") && columns.contains("score")) {
            try db.execute(sql: "DROP TABLE sync_stash")
        }
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS sync_stash (
                stem  TEXT,
                keya  TEXT,
                keyb  TEXT,
                score INTEGER
            )
        """)
    }

    private func learnKeys(for stem: String) -> (a: String, b: String) {
        stem == "related" ? ("pword", "cword") : ("code", "word")
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
                           rev: row["rev"],
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
