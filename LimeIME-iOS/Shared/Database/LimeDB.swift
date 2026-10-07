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
import CoreFoundation
import GRDB
import ZIPFoundation

// SQL operations layer — full port of LimeDB.java (~5700 lines) to Swift/GRDB.
// All parameterized queries against lime.db.
// Port target: LimeDB.java + LimeSQLiteOpenHelper.java

// MARK: - Supporting Types

/// Raw im-table row (key-value config entry for an IM).
/// Mirrors Android's ImConfig.java data class as returned by getImConfigList().
struct LimeImConfigRow {
    var id: Int = 0
    var code: String = ""
    var title: String = ""
    var desc: String = ""
    var keyboard: String = ""
    var disable: Bool = false
    var selkey: String = ""
    var endkey: String = ""
    var spacestyle: String = ""

    func getTitle() -> String { title }
    func getDesc() -> String { desc }
}

// MARK: - LimeDB

final class LimeDB {

    // MARK: - GRDB connection
    private let dbQueue: DatabaseQueue
    private let databasePath: String
    private let tracksHotLearning: Bool

    // MARK: - State (mirroring Android's instance fields)
    private var currentTableName: String = "custom"
    private static var _databaseOnHold: Bool = false
    private var _finishFlag: Bool = false
    private var _countImported: Int = 0
    private var _progressPercentageDone: Int = 0
    private static var _codeDualMapped: Bool = false

    // Related phrase score cache (mirrors Android's relatedScore HashMap)
    private var relatedScore: [Int64: Int] = [:]

    // Key mapping caches (mirrors Android's keysDefMap, keysReMap, keysDualMap)
    private var keysDefMap: [String: [String: String]] = [:]

    // Dual-code map cache (mirrors Android's keysDualMap)
    private var keysDualMap: [String: [Character: Character]] = [:]

    // Last code processed (used by dual-code expansion to detect changes)
    private var lastCode: String = ""
    /// Validated dual-code string after getMappingByCode; readable by SearchServer for composing hints.
    private(set) var lastValidDualCodeList: String? = nil

    // MARK: - Preference-driven behaviour knobs (mirrors Android LIMEPref fields read by LimeDB)

    /// Cap on similar-code (partial-match) candidates per query (mirrors getSimilarCodeCandidates()).
    /// Default 20 matches Android's SharedPreferences default ("similiar_list", "20").
    var similarCodeCandidatesCap: Int = 20

    /// When false, `addOrUpdateRelatedPhraseRecord` is a no-op (mirrors getLearnRelatedWord()).
    var learnRelatedWords: Bool = true

    /// When true, sort results by score DESC, basescore DESC (mirrors getSortSuggestions()).
    /// iOS is always soft-keyboard so this mirrors the softKeyboard=true path in Android.
    var sortSuggestions: Bool = true

    // Thread safety for blackListCache
    private let blackListLock = NSLock()

    // Chinese punctuation set to filter from related-phrase learning (mirrors ChineseSymbol.chineseSymbols)
    private static let chineseSymbolsToFilter: Set<String> = [
        "，", "。", "、", "；", "：", "？", "！",
        "「", "」", "『", "』", "【", "】", "〔", "〕",
        "（", "）", "《", "》", "〈", "〉",
        "…", "——", "～", "·", "※",
        "\u{201C}", "\u{201D}", "\u{2018}", "\u{2019}"
    ]

    // Blacklist cache: maps tableName_code → true for codes that return no DB results
    private var blackListCache: [String: Bool] = [:]

    // MARK: - Phonetic Keyboard Type (spec §5 preProcessingRemappingCode)
    // Remap cache: key = tableName + "|" + phoneticKeyboardType, value = char→char map
    private var remapCacheInitial: [String: [Character: Character]] = [:]
    private var remapCacheFinal:   [String: [Character: Character]] = [:]

    /// Which physical/virtual keyboard variant the user has selected.
    /// Values: "phonetic" (standard), "et_41" (ETEN 41-key), "et26" (ETEN 26-key), "hsu" (HSU).
    var phoneticKeyboardType: String = "phonetic" {
        didSet {
            if phoneticKeyboardType != oldValue {
                remapCacheInitial.removeAll()
                remapCacheFinal.removeAll()
                keysDualMap.removeAll()
                blackListCache.removeAll()
            }
        }
    }

    // MARK: - Constants (mirrors Android's LimeDB constants)
    static let INITIAL_RESULT_LIMIT = 15   // shared with SearchServer related two-stage
    static let FINAL_RESULT_LIMIT = 210
    private static let COMPOSING_CODE_LENGTH_LIMIT = 16
    private static let DUALCODE_COMPOSING_LIMIT = 16
    private static let DUALCODE_NO_CHECK_LIMIT = 2

    // Phonetic key mappings (mirrors Android's static fields)
    private static let BPMF_KEY = "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/-"
    private static let BPMF_CHAR = "ㄅ|ㄆ|ㄇ|ㄈ|ㄉ|ㄊ|ㄋ|ㄌ|ˇ|ㄍ|ㄎ|ㄏ|ˋ|ㄐ|ㄑ|ㄒ|ㄓ|ㄔ|ㄕ|ㄖ|ˊ|ㄗ|ㄘ|ㄙ|˙|ㄧ|ㄨ|ㄩ|ㄚ|ㄛ|ㄜ|ㄝ|ㄞ|ㄟ|ㄠ|ㄡ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ"
    private static let CJ_KEY   = "qwertyuiopasdfghjklzxcvbnm"
    private static let CJ_CHAR  = "手|田|水|口|廿|卜|山|戈|人|心|日|尸|木|火|土|竹|十|大|中|重|難|金|女|月|弓|一"
    // EZ (輕鬆輸入法) key→radical map. The 46 character roots from the shipped ez.limedb
    // im config; symbol/punctuation keys (which carry fullwidth-symbol labels, not roots)
    // are omitted.
    private static let EZ_KEY   = "',-./0123456789;=abcdefghijklmnopqrstuvwxyz[\\`"
    private static let EZ_CHAR  = "⺃|⼃|儿|㇏|⼛|鳥|⼁|車|糸|言|貝|雨|⼧|八|耳|寸|母|日|月|金|木|水|火|土|竹|戈|十|大|中|一|弓|人|心|手|口|尸|廾|山|女|田|乂|⼂|辶|⼕|的|厂"
    private static let DAYI_KEY  = "1234567890qwertyuiopasdfghjkl;zxcvbnm,./"
    private static let DAYI_CHAR = "言|牛|目|四|王|門|田|米|足|金|石|山|一|工|糸|火|艸|木|口|耳|人|革|日|土|手|鳥|月|立|女|虫|心|水|鹿|禾|馬|魚|雨|力|舟|竹"
    private static let ARRAY_KEY  = "qazwsxedcrfvtgbyhnujmik,ol.p;/"
    private static let ARRAY10_KEY = "1234567890"
    // Arrow glyphs (⇡/⇣) match the array keyboard layout in lime_array.xml.
    private static let ARRAY_CHAR = "1⇡|1-|1⇣|2⇡|2-|2⇣|3⇡|3-|3⇣|4⇡|4-|4⇣|5⇡|5-|5⇣|6⇡|6-|6⇣|7⇡|7-|7⇣|8⇡|8-|8⇣|9⇡|9-|9⇣|0⇡|0-|0⇣"
    // DB 105 prep: array/phonetic default `imkeys`/`imkeynames` written into the `im` table
    // on import/backfill. Distinct from ARRAY_KEY/ARRAY_CHAR and BPMF_KEY/BPMF_CHAR above —
    // those are the runtime physical-key→radical maps used by keyToKeyName/imKeysForTable —
    // these are the stored-metadata defaults (verified NOT identical to the runtime maps),
    // so they get their own constants rather than reusing ARRAY_KEY/ARRAY_CHAR/BPMF_KEY/BPMF_CHAR.
    // Single source of truth for both applyDefaultMetadataForStandardIM (import path) and
    // ensureStandardIMKeyMetadata (restore-backfill path, LIME_DB_105.md).
    private static let ARRAY_IMKEYS = "abcdefghijklmnopqrstuvwxyz./;,?*#1#2#3#4#5#6#7#8#9#0"
    private static let ARRAY_IMKEYNAMES = "1-|5⇣|3⇣|3-|3⇡|4-|5-|6-|8⇡|7-|8-|9-|7⇣|6⇣|9⇡|0⇡|1⇡|4⇡|2-|5⇡|7⇡|4⇣|2⇡|2⇣|6⇡|1⇣|9⇣|0⇣|0-|8⇣|？|＊|1|2|3|4|5|6|7|8|9|0"
    private static let BPMF_IMKEYS = ",-./0123456789;abcdefghijklmnopqrstuvwxyz'[]\\=<>?:\"{}|~!@#$%^&*()_+"
    private static let BPMF_IMKEYNAMES = "ㄝ|ㄦ|ㄡ|ㄥ|ㄢ|ㄅ|ㄉ|ˇ|ˋ|ㄓ|ˊ|˙|ㄚ|ㄞ|ㄤ|ㄇ|ㄖ|ㄏ|ㄎ|ㄍ|ㄑ|ㄕ|ㄘ|ㄛ|ㄨ|ㄜ|ㄠ|ㄩ|ㄙ|ㄟ|ㄣ|ㄆ|ㄐ|ㄋ|ㄔ|ㄧ|ㄒ|ㄊ|ㄌ|ㄗ|ㄈ|、|「|」|＼|＝|，|。|？|：|；|『|』|│|～|！|＠|＃|＄|％|︿|＆|＊|（|）|－|＋"
    private static let IM_CODES = [
        "custom", "cj", "scj", "cj5", "ecj", "dayi", "phonetic", "ez",
        "array", "array10", "wb", "hs", "pinyin", "cj4"
    ]
    private static let IM_FULL_NAMES = [
        "自建輸入法", "倉頡輸入法", "快倉輸入法", "倉頡五代輸入法", "速成輸入法",
        "大易輸入法", "注音輸入法", "輕鬆輸入法", "行列輸入法", "行列10輸入法",
        "筆順五碼輸入法", "華象直覺輸入法", "拼音輸入法", "四碼倉頡輸入法"
    ]
    // MARK: - Initializer

    /// Opens (or creates) lime.db at the given path.
    init(path: String, tracksHotLearning: Bool = false) throws {
        databasePath = path
        self.tracksHotLearning = tracksHotLearning
        var config = Configuration()
        config.prepareDatabase { db in
            try db.execute(sql: "PRAGMA journal_mode = WAL")
            try db.execute(sql: "PRAGMA cache_size = -4096")   // 4 MB page cache
            try db.execute(sql: "PRAGMA mmap_size = 8388608")  // 8 MB mmap — skips pread() for cold page reads
        }
        dbQueue = try DatabaseQueue(path: path, configuration: config)
        try migrate()
        ensureCurrentDatabase()
    }

    // MARK: - Schema Migration

    // Current schema version (mirrors Android DB_VERSION = 105).
    private static let CURRENT_DB_VERSION = 105
    private static let EMOJI_DATA_VERSION = "17.0"
    private static let EMOJI_TABLE_DATA = "emoji_data"
    private static let EMOJI_TABLE_FTS = "emoji_fts"
    private static let EMOJI_TABLE_USER = "emoji_user"
    private static let EMOJI_CATEGORY_GROUPS = [
        "Smileys & Emotion",
        "People & Body",
        "Animals & Nature",
        "Food & Drink",
        "Travel & Places",
        "Activities",
        "Objects",
        "Symbols",
        "Flags",
    ]

    private func migrate() throws {
        try dbQueue.write { db in
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
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS related (
                    _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    pword     TEXT,
                    cword     TEXT,
                    basescore INTEGER DEFAULT 0,
                    score     INTEGER DEFAULT 0
                )
            """)
            try db.execute(sql: "CREATE INDEX IF NOT EXISTS related_idx_pword ON related (pword)")
            try db.execute(sql: "CREATE INDEX IF NOT EXISTS related_idx_cword ON related (cword)")
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS keyboard (
                    _id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    code           TEXT,
                    name           TEXT,
                    desc           TEXT,
                    type           TEXT,
                    image          TEXT,
                    imkb           TEXT,
                    imshiftkb      TEXT,
                    engkb          TEXT,
                    engshiftkb     TEXT,
                    symbolkb       TEXT,
                    symbolshiftkb  TEXT,
                    defaultkb      TEXT,
                    defaultshiftkb TEXT,
                    extendedkb     TEXT,
                    extendedshiftkb TEXT,
                    disable        BOOLEAN DEFAULT 0
                )
            """)
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS custom (
                    _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    code      TEXT,
                    word      TEXT,
                    score     INTEGER DEFAULT 0,
                    basescore INTEGER DEFAULT 0,
                    code3r    TEXT
                )
            """)
            // Versioned upgrade path (mirrors Android onUpgrade — version 102)
            try upgradeIfNeeded(db)
        }
    }

    /// Apply incremental schema upgrades for existing DBs older than CURRENT_DB_VERSION.
    /// Mirrors Android LimeDB.onUpgrade(db, oldVersion, newVersion).
    private func upgradeIfNeeded(_ db: Database) throws {
        let version = try Int.fetchOne(db, sql: "PRAGMA user_version") ?? 0
        guard version < LimeDB.CURRENT_DB_VERSION else { return }

        // Fix related table column names if created with wrong names (base_score/user_score).
        // This covers any device that ran the app before the DDL was corrected.
        let relatedCols = try Row.fetchAll(db, sql: "PRAGMA table_info(related)").map {
            $0["name"] as String? ?? "" }
        if relatedCols.contains("base_score") {
            try db.execute(sql: "ALTER TABLE related RENAME COLUMN base_score TO basescore")
        }
        if relatedCols.contains("user_score") {
            try db.execute(sql: "ALTER TABLE related RENAME COLUMN user_score TO score")
        }

        // Version < 102: add basescore column to mapping tables if missing;
        //                insert wb and hs rows into keyboard table if absent.
        if version < 102 {
            // Add basescore column to every mapping table that is missing it
            let mappingTables = ["custom", "phonetic", "wb", "cj", "cj4", "array", "dayi", "ez",
                                 "hs", "et26", "et_41", "hsu", "scj", "ecj", "pinyin",
                                 "imtable2","imtable3","imtable4","imtable5","imtable6",
                                 "imtable7","imtable8","imtable9","imtable10"]
            for t in mappingTables {
                guard (try? Int.fetchOne(db,
                    sql: "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                    arguments: [t]) ?? 0) ?? 0 > 0 else { continue }
                let cols = try Row.fetchAll(db, sql: "PRAGMA table_info(\(t))").map {
                    $0["name"] as String? ?? "" }
                if !cols.contains("basescore") {
                    try db.execute(sql: "ALTER TABLE \(t) ADD COLUMN basescore INTEGER DEFAULT 0")
                }
            }
            // Insert default wb and hs keyboard rows if absent
            for code in ["wb", "hs"] {
                let exists = (try? Int.fetchOne(db,
                    sql: "SELECT COUNT(*) FROM keyboard WHERE code = ?",
                    arguments: [code]) ?? 0) ?? 0
                if exists == 0 {
                    try db.execute(sql: """
                        INSERT INTO keyboard (code, name, desc, type, image,
                            imkb, imshiftkb, engkb, engshiftkb,
                            symbolkb, symbolshiftkb, defaultkb, defaultshiftkb,
                            extendedkb, extendedshiftkb, disable)
                        VALUES (?, ?, ?, 'phone', '',
                            'lime_\(code)', 'lime_\(code)',
                            'lime_abc', 'lime_abc_shift',
                            'symbols1', 'symbols2',
                            '', '', '', '', 0)
                    """, arguments: [code, code.uppercased(), code.uppercased() + " 輸入法鍵盤"])
                }
            }
        }
        if version < 103 {
            try LimeDB.createEmojiTables(db, forceRecreate: false)
        }
        if version < 104 {
            try LimeDB.ensureCj4Schema(db)
            try LimeDB.ensureTricodeSchema(db)
        }
        // DB 105 goal 2: the post-104 schema/keyboard repairs — previously run UNCONDITIONALLY
        // on every open by ensureCurrentDatabase() — become a one-time, version-gated migration.
        // The branch runs the FULL idempotent schema set so that stamping user_version = 105
        // GUARANTEES the DB is complete. That guarantee (105 ⟺ complete) is what lets the
        // every-load net be removed: a restore of any pre-105 backup re-runs this via
        // init()->migrate(); the bundled seed ships at 104 so a fresh install completes here;
        // and no "stale 105" DB can exist because 105 is stamped only after this branch runs.
        if version < 105 {
            try LimeDB.createEmojiTables(db, forceRecreate: false)
            try LimeDB.ensureCj4Schema(db)
            try LimeDB.ensureTricodeSchema(db)
            try LimeDB.ensureComputerNumKeyboard(db)
            try LimeDB.ensureCangjieSemicolonKeyboards(db)
            try LimeDB.ensureLimeNumSym2Keyboard(db)
            try LimeDB.ensureStandardIMKeyMetadata(db)
        }
        // Stamp the new version (only after the branch above completes → 105 ⟺ complete).
        try db.execute(sql: "PRAGMA user_version = \(LimeDB.CURRENT_DB_VERSION)")
    }

    // MARK: - Connection Management (mirrors Android openDBConnection / hold mechanism)

    /// Always returns true on iOS — GRDB manages the connection pool.
    @discardableResult
    func openDBConnection(_ forceReload: Bool = false) -> Bool {
        return true
    }

    /// DB 105: all schema/keyboard repairs moved to the one-time `version < 105` migration in
    /// `upgradeIfNeeded` (a 105 stamp now guarantees a complete DB, so there is nothing to repair
    /// on every open). Only emoji CONTENT currency remains here: it is keyed on `EMOJI_DATA_VERSION`
    /// (an `im` row), not `user_version`, because emoji data can refresh in a patch release without
    /// a schema bump — so it is checked on every open but is a cheap no-op when already current.
    func ensureCurrentDatabase() {
        refreshEmojiDataIfNeeded()
    }

    /// LIME DB 105 IM-load-time fallback: backfills `imkeys`/`imkeynames` for a single
    /// standard table, scoped to `table`. `ensureCurrentDatabase()` only runs at open/after
    /// restore/after factory reset, not mid-session, so a restore that populates a table in
    /// an already-open, already-current database can be followed by an export/render before
    /// the next `ensureCurrentDatabase()` — this call closes that gap. Opens its own
    /// `dbQueue.write` (NOT nested — safe to call from a host-app context such as
    /// `SetupImController.exportIMAsText`); no-op when the table already has metadata, so
    /// it is safe to call on every load. Host-only: never call from the keyboard extension.
    func ensureStandardIMKeyMetadata(forTable table: String) {
        do {
            try dbQueue.write { db in
                try LimeDB.ensureStandardIMKeyMetadata(db, table: table)
            }
        } catch {
            NSLog("LimeDB ensureStandardIMKeyMetadata(forTable:) failed: \(error)")
        }
    }

    func repairKeyboardCatalogIfNeeded(from bundledDatabaseURL: URL) {
        guard !checkDBConnection() else { return }
        do {
            try dbQueue.write { db in
                let hasPhonetic = (try Int.fetchOne(db,
                    sql: "SELECT COUNT(*) FROM keyboard WHERE code = 'phonetic'") ?? 0) > 0
                guard !hasPhonetic else { return }

                try db.execute(sql: "ATTACH DATABASE ? AS bundled_keyboard_seed",
                               arguments: [bundledDatabaseURL.path])
                defer {
                    try? db.execute(sql: "DETACH DATABASE bundled_keyboard_seed")
                }

                let seedHasKeyboard = (try Int.fetchOne(db,
                    sql: """
                        SELECT COUNT(*)
                        FROM bundled_keyboard_seed.sqlite_master
                        WHERE type = 'table' AND name = 'keyboard'
                    """) ?? 0) > 0
                guard seedHasKeyboard else { return }

                try db.execute(sql: "DELETE FROM keyboard")
                try db.execute(sql: """
                    INSERT INTO keyboard (
                        code, name, desc, type, image,
                        imkb, imshiftkb, engkb, engshiftkb,
                        symbolkb, symbolshiftkb, defaultkb, defaultshiftkb,
                        extendedkb, extendedshiftkb, disable
                    )
                    SELECT
                        code, name, desc, type, image,
                        imkb, imshiftkb, engkb, engshiftkb,
                        symbolkb, symbolshiftkb, defaultkb, defaultshiftkb,
                        extendedkb, extendedshiftkb, disable
                    FROM bundled_keyboard_seed.keyboard
                """)
                try LimeDB.ensureComputerNumKeyboard(db)
                try LimeDB.ensureCangjieSemicolonKeyboards(db)
                try LimeDB.ensureLimeNumSym2Keyboard(db)
            }
        } catch {
            NSLog("LimeDB keyboard catalog repair failed: \(error)")
        }
    }

    /// Parse a column that may be stored as either TEXT ("true"/"false") or
    /// INTEGER (0/1) — some legacy Android schemas mix both in the same column.
    /// Reading as a Swift-typed subscript crashes on the mismatched storage
    /// class, so this helper takes a DatabaseValue and handles every case.
    static func parseBoolFlag(_ value: DatabaseValue) -> Bool {
        switch value.storage {
        case .null:
            return false
        case .int64(let n):
            return n != 0
        case .double(let d):
            return d != 0
        case .string(let s):
            let t = s.lowercased()
            return t == "true" || t == "1"
        case .blob:
            return false
        }
    }

    func holdDBConnection() {
        LimeDB._databaseOnHold = true
    }

    func unHoldDBConnection() {
        LimeDB._databaseOnHold = false
        relatedScore.removeAll()
    }

    func isDatabaseOnHold() -> Bool {
        return LimeDB._databaseOnHold
    }

    func closeForReplacement() throws {
        try dbQueue.close()
    }

    /// Returns true if DB is unavailable (on hold). Mirrors Android's checkDBConnection().
    /// On the main thread returns immediately (matches Android's Looper.getMainLooper() check).
    /// On background threads waits up to 5 seconds (50 × 100 ms) for the hold to be released.
    private func checkDBConnection() -> Bool {
        guard LimeDB._databaseOnHold else { return false }
        guard !Thread.isMainThread else { return true }
        for _ in 0..<50 {
            Thread.sleep(forTimeInterval: 0.1)
            if !LimeDB._databaseOnHold { return false }
        }
        return true
    }

    // MARK: - Table Name

    func setTableName(_ name: String) {
        currentTableName = name
    }

    func getTableName() -> String {
        return currentTableName
    }

    // MARK: - Filename (used by importTxtTable in Android — passthrough on iOS)

    func setFilename(_ file: URL?) {
        // iOS importTxtFile takes path directly; this is kept for API compatibility
    }

    // MARK: - Progress / State

    func setFinish(_ value: Bool) {
        _finishFlag = value
    }

    func getCountImported() -> Int {
        return _countImported
    }

    func getProgressPercentageDone() -> Int {
        return _progressPercentageDone
    }

    static func isCodeDualMapped() -> Bool {
        return _codeDualMapped
    }

    // MARK: - Table Name Validation

    func isValidTableName(_ name: String?) -> Bool {
        guard let name = name, !name.isEmpty else { return false }
        let valid: Set<String> = [
            "array", "array10", "cj", "cj4", "cj5", "custom", "dayi", "ecj", "ez",
            "hs", "phonetic", "pinyin", "scj", "tricode", "wb",
            "imtable2", "imtable3", "imtable4", "imtable5",
            "imtable6", "imtable7", "imtable8", "imtable9", "imtable10",
            "related", "im", "keyboard"
        ]
        if valid.contains(name) { return true }
        if name.hasSuffix("_user") {
            let base = String(name.dropLast(5))
            return valid.contains(base)
        }
        return false
    }

    // MARK: - Table Utilities

    func tableExists(_ name: String) -> Bool {
        (try? dbQueue.read { db in
            try Int.fetchOne(db,
                sql: "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                arguments: [name]) ?? 0
        }) ?? 0 > 0
    }

    func tableHasData(_ name: String) -> Bool {
        guard tableExists(name) else { return false }
        return (try? dbQueue.read { db in
            try Int.fetchOne(db, sql: "SELECT EXISTS(SELECT 1 FROM \(name) LIMIT 1)") ?? 0
        }) ?? 0 > 0
    }

    private func ensureLearnOutbox(_ db: Database) throws {
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

    private func appliedRevision(for table: String, db: Database) throws -> Int {
        let hasMeta = try Int.fetchOne(db, sql: """
            SELECT COUNT(*) FROM sqlite_master
            WHERE type = 'table' AND name = 'sync_meta'
            """) ?? 0
        guard hasMeta > 0 else { return 0 }
        let raw = try String.fetchOne(db, sql: "SELECT value FROM sync_meta WHERE key = ?",
                                      arguments: ["rev:\(table)"])
        return raw.flatMap(Int.init) ?? 0
    }

    private func journalHotLearning(_ db: Database, table: String, k1: String, k2: String) throws {
        guard tracksHotLearning, !k1.isEmpty, !k2.isEmpty else { return }
        try ensureLearnOutbox(db)
        let revision = try appliedRevision(for: table, db: db)
        try db.execute(sql: """
            INSERT INTO learn_outbox(tbl, k1, k2, observed_rev, version)
            VALUES (?, ?, ?, ?, 1)
            ON CONFLICT(tbl, k1, k2) DO UPDATE SET
                observed_rev = excluded.observed_rev,
                version = learn_outbox.version + 1
            """, arguments: [table, k1, k2, revision])
    }

    // MARK: - Core CRUD (mirrors Android's countRecords / addRecord / updateRecord / deleteRecord)

    /// COUNT(*) with optional WHERE clause. Returns 0 on error.
    func countRecords(_ table: String, _ whereClause: String?, _ whereArgs: [String]?) -> Int {
        guard !checkDBConnection() else { return 0 }
        guard isValidTableName(table) else { return 0 }
        guard tableExists(table) else { return 0 }
        var sql = "SELECT COUNT(*) AS count FROM \(table)"
        if let wc = whereClause, !wc.isEmpty {
            sql += " WHERE \(wc)"
        }
        let args: StatementArguments = whereArgs.map { StatementArguments($0) } ?? StatementArguments()
        return (try? dbQueue.read { db in
            try Int.fetchOne(db, sql: sql, arguments: args) ?? 0
        }) ?? 0
    }

    /// INSERT a row using a [String: Any] values dict. Returns rowid or -1 on error.
    @discardableResult
    func addRecord(_ table: String, _ values: [String: Any?]) -> Int64 {
        guard !checkDBConnection() else { return -1 }
        guard isValidTableName(table) else { return -1 }
        let cols = values.keys.joined(separator: ", ")
        let placeholders = values.keys.map { _ in "?" }.joined(separator: ", ")
        let sql = "INSERT INTO \(table) (\(cols)) VALUES (\(placeholders))"
        let args = StatementArguments(values.values.map { DatabaseValue(value: $0) })
        return (try? dbQueue.write { db in
            try db.execute(sql: sql, arguments: args)
            return db.lastInsertedRowID
        }) ?? -1
    }

    /// UPDATE rows. Returns affected count or -1 on error.
    @discardableResult
    func updateRecord(_ table: String, _ values: [String: Any?],
                      _ whereClause: String?, _ whereArgs: [String]?) -> Int {
        guard !checkDBConnection() else { return -1 }
        guard isValidTableName(table) else { return -1 }
        let setClauses = values.keys.map { "\($0) = ?" }.joined(separator: ", ")
        var sql = "UPDATE \(table) SET \(setClauses)"
        var allArgs: [DatabaseValue] = values.values.map { DatabaseValue(value: $0) }
        if let wc = whereClause, !wc.isEmpty {
            sql += " WHERE \(wc)"
            allArgs += (whereArgs ?? []).map { DatabaseValue(value: $0) }
        }
        return (try? dbQueue.write { db in
            try db.execute(sql: sql, arguments: StatementArguments(allArgs))
            return db.changesCount
        }) ?? -1
    }

    /// DELETE rows. Returns affected count or -1 on error.
    @discardableResult
    func deleteRecord(_ table: String, _ whereClause: String?, _ whereArgs: [String]?) -> Int {
        guard !checkDBConnection() else { return -1 }
        guard isValidTableName(table) else { return -1 }
        var sql = "DELETE FROM \(table)"
        var args: StatementArguments = []
        if let wc = whereClause, !wc.isEmpty {
            sql += " WHERE \(wc)"
            args = StatementArguments(whereArgs ?? [])
        }
        return (try? dbQueue.write { db in
            try db.execute(sql: sql, arguments: args)
            return db.changesCount
        }) ?? -1
    }

    // MARK: - Atomic cold editor and lifecycle mutations

    private struct StagedMappingRow {
        let code: String
        let word: String
        let score: Int
        let baseScore: Int
        let code3r: String?
    }

    private struct StagedIMRow {
        let title: String?
        let desc: String?
        let keyboard: String?
        let disable: Int
        let selkey: String
        let endkey: String
        let spacestyle: String
    }

    private struct StagedTablePayload {
        let rows: [StagedMappingRow]
        let imRows: [StagedIMRow]
    }

    func performEditorMutation(_ mutation: EditorMutation) throws -> EditorMutationResult {
        guard !checkDBConnection() else { throw DBServerError.datasourceUnavailable }
        return try dbQueue.write { db in
            try ensureColdIntentTables(db)
            switch mutation {
            case let .addMapping(table, code, word, score):
                return try addMapping(table: table, code: code, word: word, score: score, db: db)
            case let .updateMapping(table, id, code, word, score):
                return try updateMapping(table: table, id: id, code: code, word: word, score: score, db: db)
            case let .deleteMapping(table, id):
                return try deleteMapping(table: table, id: id, db: db)
            case let .addRelated(parentWord, childWord, score):
                return try addRelated(parentWord: parentWord, childWord: childWord, score: score, db: db)
            case let .updateRelated(id, parentWord, childWord, score):
                return try updateRelated(id: id, parentWord: parentWord, childWord: childWord, score: score, db: db)
            case let .deleteRelated(id):
                return try deleteRelated(id: id, db: db)
            case let .clearTable(table):
                return try clearEditorTable(table, db: db)
            }
        }
    }

    func performTableLifecycleMutation(_ mutation: TableLifecycleMutation) throws {
        guard !checkDBConnection() else { throw DBServerError.datasourceUnavailable }
        switch mutation {
        case let .replaceFromStaging(table, stagingDatabaseURL, sourceTable, preserveLearning, _):
            let payload = try readStagedTablePayload(stagingDatabaseURL,
                                                     table: table,
                                                     sourceTable: sourceTable)
            try dbQueue.write { db in
                try ensureColdIntentTables(db)
                try replaceTableFromPayload(table: table,
                                            payload: payload,
                                            preserveLearning: preserveLearning,
                                            db: db)
            }
        case let .delete(table, preserveLearning, _):
            try dbQueue.write { db in
                try ensureColdIntentTables(db)
                try deleteLifecycleTable(table: table,
                                         preserveLearning: preserveLearning,
                                         db: db)
            }
        }
    }

    func hasPendingEditorChanges(comparedToSnapshot snapshotURL: URL) throws -> Bool {
        let liveRevisions = try dbQueue.read { db in
            try revisionMap(db)
        }
        guard !liveRevisions.isEmpty else { return false }
        guard FileManager.default.fileExists(atPath: snapshotURL.path) else {
            return liveRevisions.values.contains { $0 > 0 }
        }
        var config = Configuration()
        config.readonly = true
        let snapshotQueue = try DatabaseQueue(path: snapshotURL.path, configuration: config)
        defer { closeQueue(snapshotQueue, label: snapshotURL.path) }
        let snapshotRevisions = try snapshotQueue.read { db in
            try revisionMap(db)
        }
        return liveRevisions.contains { table, revision in
            revision > (snapshotRevisions[table] ?? 0)
        }
    }

    private func addMapping(table: String, code: String, word: String, score: Int,
                            db: Database) throws -> EditorMutationResult {
        try validateMappingKey(table: table, code: code, word: word)
        try ensureMappingTable(table, db: db)
        guard try logicalMappingCount(table: table, code: code, word: word, db: db) == 0 else {
            throw DBServerError.duplicateLogicalKey("\(code)|\(word)")
        }
        if table == "phonetic" {
            let noTone = code.replacingOccurrences(of: "[3467 ]",
                                                   with: "",
                                                   options: .regularExpression)
            try db.execute(sql: """
                INSERT INTO \(table) (code, word, score, basescore, code3r)
                VALUES (?, ?, ?, 0, ?)
                """, arguments: [code, word, score, noTone])
        } else {
            try db.execute(sql: """
                INSERT INTO \(table) (code, word, score, basescore)
                VALUES (?, ?, ?, 0)
                """, arguments: [code, word, score])
        }
        let rowID = db.lastInsertedRowID
        let revision = try bumpTableRevision(table, db: db)
        try writeRowFence(table: table, k1: code, k2: word, action: .upsert, revision: revision, db: db)
        return EditorMutationResult(rowID: rowID, affectedRows: 1)
    }

    private func updateMapping(table: String, id: String, code: String, word: String, score: Int,
                               db: Database) throws -> EditorMutationResult {
        try validateMappingKey(table: table, code: code, word: word)
        guard let rowID = Int64(id) else { throw DBServerError.invalidEditorMutation("invalid row id") }
        try ensureMappingTable(table, db: db)
        guard let old = try Row.fetchOne(db,
                                         sql: "SELECT code, word FROM \(table) WHERE _id = ? AND word IS NOT NULL LIMIT 1",
                                         arguments: [rowID]),
              let oldCode = old["code"] as String?,
              let oldWord = old["word"] as String?,
              !oldCode.isEmpty,
              !oldWord.isEmpty else {
            throw DBServerError.recordNotFound(id)
        }

        let oldCount = try logicalMappingCount(table: table, code: oldCode, word: oldWord, db: db)
        let sameKey = oldCode == code && oldWord == word
        let newKeyVacant = try logicalMappingCount(table: table, code: code, word: word, db: db) == 0
        if sameKey || newKeyVacant {
            try db.execute(sql: """
                UPDATE \(table)
                SET code = ?, word = ?, score = ?
                WHERE code = ? AND word = ? AND word IS NOT NULL
                """, arguments: [code, word, score, oldCode, oldWord])
        } else {
            try db.execute(sql: "DELETE FROM \(table) WHERE code = ? AND word = ? AND word IS NOT NULL",
                           arguments: [oldCode, oldWord])
            try db.execute(sql: """
                UPDATE \(table)
                SET score = ?
                WHERE code = ? AND word = ? AND word IS NOT NULL
                """, arguments: [score, code, word])
        }

        let revision = try bumpTableRevision(table, db: db)
        if !sameKey {
            try writeRowFence(table: table, k1: oldCode, k2: oldWord, action: .delete, revision: revision, db: db)
        }
        try writeRowFence(table: table, k1: code, k2: word, action: .upsert, revision: revision, db: db)
        return EditorMutationResult(rowID: rowID, affectedRows: max(oldCount, db.changesCount))
    }

    private func deleteMapping(table: String, id: String, db: Database) throws -> EditorMutationResult {
        guard let rowID = Int64(id) else { throw DBServerError.invalidEditorMutation("invalid row id") }
        guard isValidMappingEditorTable(table) else { throw LimeDBError.invalidTableName(table) }
        guard let old = try Row.fetchOne(db,
                                         sql: "SELECT code, word FROM \(table) WHERE _id = ? AND word IS NOT NULL LIMIT 1",
                                         arguments: [rowID]),
              let oldCode = old["code"] as String?,
              let oldWord = old["word"] as String?,
              !oldCode.isEmpty,
              !oldWord.isEmpty else {
            throw DBServerError.recordNotFound(id)
        }
        try db.execute(sql: "DELETE FROM \(table) WHERE code = ? AND word = ? AND word IS NOT NULL",
                       arguments: [oldCode, oldWord])
        let affected = db.changesCount
        let revision = try bumpTableRevision(table, db: db)
        try writeRowFence(table: table, k1: oldCode, k2: oldWord, action: .delete, revision: revision, db: db)
        return EditorMutationResult(rowID: nil, affectedRows: affected)
    }

    private func addRelated(parentWord: String, childWord: String, score: Int,
                            db: Database) throws -> EditorMutationResult {
        try validateRelatedKey(parentWord: parentWord, childWord: childWord)
        guard try logicalRelatedCount(parentWord: parentWord, childWord: childWord, db: db) == 0 else {
            throw DBServerError.duplicateLogicalKey("\(parentWord)|\(childWord)")
        }
        try db.execute(sql: """
            INSERT INTO related (pword, cword, basescore, score)
            VALUES (?, ?, 0, ?)
            """, arguments: [parentWord, childWord, score])
        let rowID = db.lastInsertedRowID
        let revision = try bumpTableRevision("related", db: db)
        try writeRowFence(table: "related", k1: parentWord, k2: childWord,
                          action: .upsert, revision: revision, db: db)
        return EditorMutationResult(rowID: rowID, affectedRows: 1)
    }

    private func updateRelated(id: Int64, parentWord: String, childWord: String, score: Int,
                               db: Database) throws -> EditorMutationResult {
        try validateRelatedKey(parentWord: parentWord, childWord: childWord)
        guard let old = try Row.fetchOne(db,
                                         sql: "SELECT pword, cword FROM related WHERE _id = ? AND cword IS NOT NULL LIMIT 1",
                                         arguments: [id]),
              let oldParent = old["pword"] as String?,
              let oldChild = old["cword"] as String?,
              !oldParent.isEmpty,
              !oldChild.isEmpty else {
            throw DBServerError.recordNotFound("\(id)")
        }
        let oldCount = try logicalRelatedCount(parentWord: oldParent, childWord: oldChild, db: db)
        let sameKey = oldParent == parentWord && oldChild == childWord
        let newKeyVacant = try logicalRelatedCount(parentWord: parentWord, childWord: childWord, db: db) == 0
        if sameKey || newKeyVacant {
            try db.execute(sql: """
                UPDATE related
                SET pword = ?, cword = ?, score = ?
                WHERE pword = ? AND cword = ? AND cword IS NOT NULL
                """, arguments: [parentWord, childWord, score, oldParent, oldChild])
        } else {
            try db.execute(sql: "DELETE FROM related WHERE pword = ? AND cword = ? AND cword IS NOT NULL",
                           arguments: [oldParent, oldChild])
            try db.execute(sql: """
                UPDATE related
                SET score = ?
                WHERE pword = ? AND cword = ? AND cword IS NOT NULL
                """, arguments: [score, parentWord, childWord])
        }
        let revision = try bumpTableRevision("related", db: db)
        if !sameKey {
            try writeRowFence(table: "related", k1: oldParent, k2: oldChild,
                              action: .delete, revision: revision, db: db)
        }
        try writeRowFence(table: "related", k1: parentWord, k2: childWord,
                          action: .upsert, revision: revision, db: db)
        return EditorMutationResult(rowID: id, affectedRows: max(oldCount, db.changesCount))
    }

    private func deleteRelated(id: Int64, db: Database) throws -> EditorMutationResult {
        guard let old = try Row.fetchOne(db,
                                         sql: "SELECT pword, cword FROM related WHERE _id = ? AND cword IS NOT NULL LIMIT 1",
                                         arguments: [id]),
              let oldParent = old["pword"] as String?,
              let oldChild = old["cword"] as String?,
              !oldParent.isEmpty,
              !oldChild.isEmpty else {
            throw DBServerError.recordNotFound("\(id)")
        }
        try db.execute(sql: "DELETE FROM related WHERE pword = ? AND cword = ? AND cword IS NOT NULL",
                       arguments: [oldParent, oldChild])
        let affected = db.changesCount
        let revision = try bumpTableRevision("related", db: db)
        try writeRowFence(table: "related", k1: oldParent, k2: oldChild,
                          action: .delete, revision: revision, db: db)
        return EditorMutationResult(rowID: nil, affectedRows: affected)
    }

    private func clearEditorTable(_ table: String, db: Database) throws -> EditorMutationResult {
        guard isValidTableName(table), table != "im", table != "keyboard" else {
            throw LimeDBError.invalidTableName(table)
        }
        try db.execute(sql: "DELETE FROM \(table)")
        let affected = db.changesCount
        let revision = try bumpTableRevision(table, db: db)
        try writeTableFence(table: table, action: .clear, revision: revision, db: db)
        try deleteSupersededRowFences(table: table, revision: revision, db: db)
        return EditorMutationResult(rowID: nil, affectedRows: affected)
    }

    private func replaceTableFromPayload(table: String,
                                         payload: StagedTablePayload,
                                         preserveLearning: Bool,
                                         db: Database) throws {
        guard isValidMappingEditorTable(table) else { throw LimeDBError.invalidTableName(table) }
        try ensureMappingTable(table, db: db)
        try db.execute(sql: "DELETE FROM \(table)")
        for row in payload.rows {
            if table == "phonetic" {
                let noTone = row.code3r ?? row.code.replacingOccurrences(of: "[3467 ]",
                                                                          with: "",
                                                                          options: .regularExpression)
                try db.execute(sql: """
                    INSERT INTO \(table) (code, word, score, basescore, code3r)
                    VALUES (?, ?, ?, ?, ?)
                    """, arguments: [row.code, row.word, row.score, row.baseScore, noTone])
            } else {
                try db.execute(sql: """
                    INSERT INTO \(table) (code, word, score, basescore)
                    VALUES (?, ?, ?, ?)
                    """, arguments: [row.code, row.word, row.score, row.baseScore])
            }
        }
        try db.execute(sql: "DELETE FROM im WHERE code = ?", arguments: [table])
        for row in payload.imRows {
            try db.execute(sql: """
                INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, arguments: [
                    table,
                    row.title,
                    row.desc,
                    row.keyboard,
                    row.disable,
                    row.selkey,
                    row.endkey,
                    row.spacestyle,
                ])
        }
        if payload.imRows.isEmpty {
            try db.execute(sql: """
                INSERT INTO im (code, title, desc)
                VALUES (?, 'name', ?)
                """, arguments: [table, defaultImFullName(table, fallback: table)])
        }
        let revision = try bumpTableRevision(table, db: db)
        try writeLifecycleIntent(table: table, revision: revision,
                                 action: .install, preserveLearning: preserveLearning, db: db)
        try writeTableFence(table: table, action: .replace, revision: revision, db: db)
        try deleteSupersededRowFences(table: table, revision: revision, db: db)
    }

    private func deleteLifecycleTable(table: String,
                                      preserveLearning: Bool,
                                      db: Database) throws {
        guard isValidMappingEditorTable(table) else { throw LimeDBError.invalidTableName(table) }
        try db.execute(sql: "DELETE FROM \(table)")
        try db.execute(sql: "DELETE FROM im WHERE code = ?", arguments: [table])
        let revision = try bumpTableRevision(table, db: db)
        try writeLifecycleIntent(table: table, revision: revision,
                                 action: .delete, preserveLearning: preserveLearning, db: db)
        try writeTableFence(table: table, action: .clear, revision: revision, db: db)
        try deleteSupersededRowFences(table: table, revision: revision, db: db)
    }

    private func readStagedTablePayload(_ url: URL,
                                        table: String,
                                        sourceTable explicitSourceTable: String?) throws -> StagedTablePayload {
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw DBServerError.fileNotFound(url.path)
        }
        guard isValidTableName(table) else {
            throw LimeDBError.invalidTableName(table)
        }
        var config = Configuration()
        config.readonly = true
        let sourceQueue = try DatabaseQueue(path: url.path, configuration: config)
        defer { closeQueue(sourceQueue, label: url.path) }
        return try sourceQueue.read { db in
            let sourceTable: String
            if let explicitSourceTable {
                guard isValidTableName(explicitSourceTable) else {
                    throw LimeDBError.invalidTableName(explicitSourceTable)
                }
                guard try db.tableExists(explicitSourceTable) else {
                    throw DBServerError.invalidStagingDatabase(table)
                }
                sourceTable = explicitSourceTable
            } else if try db.tableExists("custom") {
                sourceTable = "custom"
            } else if try db.tableExists(table) {
                sourceTable = table
            } else {
                throw DBServerError.invalidStagingDatabase(table)
            }
            let columns = try Row.fetchAll(db, sql: "PRAGMA table_info(\(sourceTable))")
                .compactMap { $0["name"] as String? }
            guard columns.contains("code"), columns.contains("word") else {
                throw DBServerError.invalidStagingDatabase(table)
            }
            let scoreExpr = columns.contains("score") ? "COALESCE(score, 0)" : "0"
            let baseScoreExpr = columns.contains("basescore") ? "COALESCE(basescore, 0)" : "0"
            let code3rExpr = columns.contains("code3r") ? "code3r" : "NULL"
            let rows = try Row.fetchAll(db, sql: """
                SELECT code, word, \(scoreExpr) AS score, \(baseScoreExpr) AS basescore, \(code3rExpr) AS code3r
                FROM \(sourceTable)
                WHERE code IS NOT NULL AND code <> ''
                  AND word IS NOT NULL AND word <> ''
                """).map {
                StagedMappingRow(code: $0["code"] as String? ?? "",
                                 word: $0["word"] as String? ?? "",
                                 score: $0["score"] as Int? ?? 0,
                                 baseScore: $0["basescore"] as Int? ?? 0,
                                 code3r: $0["code3r"] as String?)
            }
            guard !rows.isEmpty else { throw DBServerError.invalidStagingDatabase(table) }

            let imRows: [StagedIMRow]
            if try db.tableExists("im") {
                let metadataRows: [Row]
                if explicitSourceTable != nil {
                    metadataRows = try Row.fetchAll(db, sql: """
                        SELECT title, desc, keyboard, disable, selkey, endkey, spacestyle
                        FROM im
                        WHERE code = ?
                        """, arguments: [sourceTable])
                } else {
                    metadataRows = try Row.fetchAll(db, sql: """
                        SELECT title, desc, keyboard, disable, selkey, endkey, spacestyle
                        FROM im
                        WHERE code = ? OR code = 'custom'
                        """, arguments: [table])
                }
                imRows = metadataRows.map {
                    StagedIMRow(title: $0["title"] as String?,
                                desc: $0["desc"] as String?,
                                keyboard: $0["keyboard"] as String?,
                                disable: $0["disable"] as Int? ?? 0,
                                selkey: $0["selkey"] as String? ?? "",
                                endkey: $0["endkey"] as String? ?? "",
                                spacestyle: $0["spacestyle"] as String? ?? "")
                }
            } else {
                imRows = []
            }
            return StagedTablePayload(rows: rows, imRows: imRows)
        }
    }

    private func validateMappingKey(table: String, code: String, word: String) throws {
        guard isValidMappingEditorTable(table) else { throw LimeDBError.invalidTableName(table) }
        guard !code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !word.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw DBServerError.invalidEditorMutation("empty mapping key")
        }
    }

    private func validateRelatedKey(parentWord: String, childWord: String) throws {
        guard !parentWord.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !childWord.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw DBServerError.invalidEditorMutation("empty related key")
        }
    }

    private func isValidMappingEditorTable(_ table: String) -> Bool {
        isValidTableName(table) && table != "related" && table != "im" && table != "keyboard"
    }

    private func logicalMappingCount(table: String, code: String, word: String, db: Database) throws -> Int {
        try Int.fetchOne(db,
                         sql: "SELECT COUNT(*) FROM \(table) WHERE code = ? AND word = ? AND word IS NOT NULL",
                         arguments: [code, word]) ?? 0
    }

    private func logicalRelatedCount(parentWord: String, childWord: String, db: Database) throws -> Int {
        try Int.fetchOne(db,
                         sql: "SELECT COUNT(*) FROM related WHERE pword = ? AND cword = ? AND cword IS NOT NULL",
                         arguments: [parentWord, childWord]) ?? 0
    }

    private func ensureColdIntentTables(_ db: Database) throws {
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS sync_meta (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """)
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS editor_fence (
                tbl      TEXT    NOT NULL,
                k1       TEXT    NOT NULL,
                k2       TEXT    NOT NULL,
                action   TEXT    NOT NULL CHECK (action IN ('upsert', 'delete')),
                revision INTEGER NOT NULL,
                PRIMARY KEY (tbl, k1, k2)
            ) WITHOUT ROWID
            """)
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS editor_table_fence (
                tbl      TEXT PRIMARY KEY,
                action   TEXT    NOT NULL CHECK (action IN ('clear', 'replace')),
                revision INTEGER NOT NULL
            ) WITHOUT ROWID
            """)
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS im_lifecycle_intent (
                tbl               TEXT    NOT NULL,
                revision          INTEGER NOT NULL,
                action            TEXT    NOT NULL CHECK (action IN ('install', 'delete')),
                preserve_learning INTEGER NOT NULL CHECK (preserve_learning IN (0, 1)),
                PRIMARY KEY (tbl, revision)
            ) WITHOUT ROWID
            """)
    }

    private func bumpTableRevision(_ table: String, db: Database) throws -> Int {
        let key = "rev:\(table)"
        let raw = try String.fetchOne(db, sql: "SELECT value FROM sync_meta WHERE key = ?",
                                      arguments: [key])
        let next = (raw.flatMap(Int.init) ?? 0) + 1
        try db.execute(sql: """
            INSERT INTO sync_meta(key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """, arguments: [key, String(next)])
        return next
    }

    private func writeRowFence(table: String, k1: String, k2: String,
                               action: EditorFenceAction, revision: Int,
                               db: Database) throws {
        try db.execute(sql: """
            INSERT INTO editor_fence(tbl, k1, k2, action, revision)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(tbl, k1, k2) DO UPDATE SET
                action = excluded.action,
                revision = excluded.revision
            """, arguments: [table, k1, k2, action.rawValue, revision])
    }

    private func writeTableFence(table: String, action: EditorTableFenceAction,
                                 revision: Int, db: Database) throws {
        try db.execute(sql: """
            INSERT INTO editor_table_fence(tbl, action, revision)
            VALUES (?, ?, ?)
            ON CONFLICT(tbl) DO UPDATE SET
                action = excluded.action,
                revision = excluded.revision
            """, arguments: [table, action.rawValue, revision])
    }

    private func writeLifecycleIntent(table: String, revision: Int,
                                      action: IMTableLifecycleAction,
                                      preserveLearning: Bool,
                                      db: Database) throws {
        try db.execute(sql: """
            INSERT INTO im_lifecycle_intent(tbl, revision, action, preserve_learning)
            VALUES (?, ?, ?, ?)
            """, arguments: [table, revision, action.rawValue, preserveLearning ? 1 : 0])
    }

    private func deleteSupersededRowFences(table: String, revision: Int, db: Database) throws {
        try db.execute(sql: "DELETE FROM editor_fence WHERE tbl = ? AND revision <= ?",
                       arguments: [table, revision])
    }

    private func revisionMap(_ db: Database) throws -> [String: Int] {
        let hasMeta = try (Int.fetchOne(db,
                                        sql: "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'sync_meta'") ?? 0) > 0
        guard hasMeta else { return [:] }
        let rows = try Row.fetchAll(db, sql: "SELECT key, value FROM sync_meta WHERE key LIKE 'rev:%'")
        var result: [String: Int] = [:]
        for row in rows {
            guard let key = row["key"] as String?,
                  let value = row["value"] as String?,
                  let revision = Int(value) else { continue }
            result[String(key.dropFirst(4))] = revision
        }
        return result
    }

    private func closeQueue(_ queue: DatabaseQueue, label: String) {
        do {
            try queue.close()
        } catch {
            NSLog("LimeDB close failed for \(label): \(error)")
        }
    }

    // MARK: - Mapping Queries (spec §13 getMappingByCode)

    /// Full port of Android getMappingByCode(). Returns nil on DB error.
    func getMappingByCode(_ code: String?, softKeyboard: Bool, getAllRecords: Bool) -> [Mapping]? {
        // Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        guard !checkDBConnection() else { return nil }
        guard let code = code, !code.isEmpty else { return nil }

        let codeOrig = code

        // Extension on multi table query. Jeremy '11,6,15
        // lastCode set to original code BEFORE any pre-processing (mirrors Java line 1796)
        lastCode = code
        lastValidDualCodeList = nil

        let table = currentTableName
        guard isValidTableName(table) else { return nil }
        guard tableExists(table) else { return nil }

        // Step 1: Code re-mapping (mirrors Java line 1802)
        var queryCode = preProcessingRemappingCode(code)
        // Jeremy '12,4,1 toLowerCase moved from SearchService (mirrors Java line 1803)
        queryCode = queryCode.lowercased()

        // Step 2: Build extra getMappingByCode conditions e.g. dualcode remap (mirrors Java line 1805)
        let extra = preProcessingForExtraQueryConditions(queryCode)
        let extraSelectClause = extra?.0 ?? ""
        let extraExactClause  = extra?.1 ?? ""

        // Step 3: Phonetic-specific tone handling (mirrors Java lines 1828–1842)
        // Jeremy '15,6,6 always search no tone code for phonetic
        var codeCol = "code"
        let tonePresent = queryCode.range(of: ".+[3467 ].*", options: .regularExpression) != nil
        let toneNotLast = queryCode.range(of: ".+[3467 ].+", options: .regularExpression) != nil
        if table == "phonetic" {
            if tonePresent {
                // LD phrase if tone symbols present but not in last character or length > 4
                if toneNotLast || queryCode.count > 4 {
                    queryCode = queryCode.replacingOccurrences(of: "[3467 ]", with: "", options: .regularExpression)
                }
            } else {
                // no tone symbols present, check NoToneCode column
                codeCol = "code3r"
            }
            queryCode = queryCode.trimmingCharacters(in: .whitespaces)
        }
        // Used in buildQueryResult validCodeMap logic below
        let searchNoToneColumn = table == "phonetic" && !tonePresent

        // Build SQL (mirrors Java lines 1847–1868)
        let escapedCode = queryCode.replacingOccurrences(of: "'", with: "''")
        let codeLen = queryCode.count
        let limitClause = getAllRecords ? LimeDB.FINAL_RESULT_LIMIT : LimeDB.INITIAL_RESULT_LIMIT
        // Mirrors Android: " (" + codeCol + " ='" + escapedCode + "' " + extraExactMatchClause + ") "
        let exactMatchCondition = " (\(codeCol) ='\(escapedCode)' \(extraExactClause)) "
        let selectClause: String
        if similarCodeCandidatesCap <= 0 {
            selectClause = exactMatchCondition
        } else {
            selectClause = expandBetweenSearchClause(column: codeCol, code: queryCode) + extraSelectClause
        }

        // Jeremy '15, 6, 1 between search clause without using related column for better sorting order.
        var sortClause = "exactmatch desc, (length(\(codeCol)) >= \(codeLen) ) desc, "
        // Jeremy '11,6,11 separated suggestions sorting option for physical keyboard
        // iOS is always soft keyboard — mirrors Android softKeyboard=true path
        // NOTE: score sort must come BEFORE the length tiebreaker (mirrors Android LimeDB.java order)
        if sortSuggestions {
            sortClause += "( exactmatch = 1 and ( score > 0 or  basescore >0) and length(word)=1) desc, "
            sortClause += "score desc, basescore desc, "
        }
        sortClause += "(length(\(codeCol)) <= \(min(codeLen, 5)) )*length(\(codeCol)) desc, "
        sortClause += "_id asc"

        // Mirrors Android: "where word is not null and " + selectClause  (no outer parens — AND/OR precedence intentional)
        let selectString = "select _id, code, code3r, word, score, basescore, \(exactMatchCondition) as exactmatch  "
                         + " from \(table) where word is not null and \(selectClause)"
                         + " order by \(sortClause) limit \(limitClause)"

        guard let rows = try? dbQueue.read({ db in try Row.fetchAll(db, sql: selectString) })
        else { return nil }

        // Build result — mirrors Android buildQueryResult (Java lines 2725–2873)
        var result: [Mapping] = []
        var duplicateCheck = Set<String>()  // Jeremy: ignore duplicate words
        var validCodeMap   = Set<String>()  // Jeremy '11,8,26 build valid code map
        var rsize = 0
        let sLimit = similarCodeCandidatesCap   // mirrors getSimilarCodeCandidates()
        var sCount = 0
        // Jeremy '11,8,30: only build validCodeList when lastValidDualCodeList is nil
        // (mirrors Java: final boolean buildValidCodeList = lastValidDualCodeList == null)
        let buildValidCodeList = lastValidDualCodeList == nil

        for row in rows {
            guard let word = row.optString("word"), !word.isEmpty else { continue }
            let rowCode = row.optString("code") ?? ""
            var m = Mapping(
                id:        row.optInt64("_id") ?? 0,
                code:      rowCode,
                word:      word,
                score:     row.optInt("score") ?? 0,
                baseScore: row.optInt("basescore") ?? 0,
                code3r:    row["code3r"],
                codeorig:  codeOrig
            )
            // Jeremy '15,6,3 new exact or partial record type
            let exactMatch = (row.optInt("exactmatch") ?? 0) == 1
            if exactMatch { m.setExactMatchToCodeRecord() } else { m.setPartialMatchToCodeRecord() }

            // Jeremy '11,8,26 build valid code map (mirrors Java buildQueryResult lines 2780–2788)
            if buildValidCodeList {
                let noToneCode = (row.optString("code3r") ?? "").trimmingCharacters(in: .whitespaces)
                let strippedQuery = queryCode.replacingOccurrences(of: "[3467 ]", with: "", options: .regularExpression)
                if searchNoToneColumn,
                   !noToneCode.isEmpty,
                   noToneCode.count == strippedQuery.count,
                   validCodeMap.count < LimeDB.DUALCODE_COMPOSING_LIMIT {
                    validCodeMap.insert(noToneCode)
                } else if !rowCode.isEmpty, rowCode.count == queryCode.count {
                    validCodeMap.insert(rowCode)
                }
            }

            // 06/Aug/2011 by Art: ignore result when word == keyToKeyName(code) for Array IM
            if rowCode.count == 1 && table == "array" {
                if keyToKeyName(rowCode, table, false) == word { continue }
            }

            // mirrors Android: rsize++ is outside duplicateCheck block
            let inserted = duplicateCheck.insert(word).inserted
            if inserted {
                if m.isPartialMatchToCodeRecord {
                    if sCount >= sLimit { break }
                    sCount += 1
                }
                result.append(m)
            }
            rsize += 1
        }

        // Jeremy '11,8,26 build valid code map → lastValidDualCodeList (mirrors Java lines 2817–2832)
        // Only write when buildValidCodeList is true (mirrors Java: if buildValidCodeList && !validCodeMap.isEmpty)
        if buildValidCodeList && !validCodeMap.isEmpty {
            lastValidDualCodeList = validCodeMap.sorted().joined(separator: "|")
        }

        // Add full shaped punctuation symbol to the third place , and . (mirrors Java lines 2837–2858)
        if queryCode.count == 1 {
            if (queryCode == "," || queryCode == "<"), duplicateCheck.insert("，").inserted {
                let temp = Mapping(id: 0, code: queryCode, word: "，", score: 0, baseScore: 0,
                                   recordType: Mapping.RecordType.chinesePunctuation)
                result.insert(temp, at: min(3, result.count))
            }
            if (queryCode == "." || queryCode == ">"), duplicateCheck.insert("。").inserted {
                let temp = Mapping(id: 0, code: queryCode, word: "。", score: 0, baseScore: 0,
                                   recordType: Mapping.RecordType.chinesePunctuation)
                result.insert(temp, at: min(3, result.count))
            }
        }

        // hasMore marker (mirrors Java lines 2861–2867)
        if !getAllRecords && rsize == LimeDB.INITIAL_RESULT_LIMIT {
            var more = Mapping(id: 0, code: "has_more_records", word: "...", score: 0, baseScore: 0)
            more.recordType = Mapping.RecordType.hasMoreMark
            result.append(more)
        }

        return result
    }

    /// GRDB-throws version used internally (keeps SearchServer.swift working).
    func getMappingByCode(_ code: String, tableName: String, limit: Int = 50) throws -> [Mapping] {
        let code = code.lowercased()
        return try dbQueue.read { db in
            let sql = """
                SELECT _id, code, word, score, basescore, code3r, related
                FROM \(tableName)
                WHERE code = ?
                ORDER BY (score + basescore) DESC
                LIMIT ?
            """
            return try Row.fetchAll(db, sql: sql, arguments: [code, limit]).map { row in
                Mapping(id: row["_id"], code: row["code"], word: row["word"],
                        score: row["score"], baseScore: row["basescore"], code3r: row["code3r"],
                        related: row.optString("related"))
            }
        }
    }

    /// Phonetic fallback variant (no-tone code3r search).
    func getMappingByCodeWithFallback(_ code: String, tableName: String, limit: Int = 50) throws -> [Mapping] {
        guard isValidTableName(tableName) else { return [] }
        let code = code.lowercased()
        var results = try getMappingByCode(code, tableName: tableName, limit: limit)
        if results.isEmpty && tableName.hasPrefix("phonetic") && code.count > 1 {
            let stripped = String(code.dropLast())
            results = try dbQueue.read { db in
                let sql = """
                    SELECT _id, code, word, score, basescore, code3r, related
                    FROM \(tableName)
                    WHERE code3r = ?
                    ORDER BY (score + basescore) DESC
                    LIMIT ?
                """
                return try Row.fetchAll(db, sql: sql, arguments: [stripped, limit]).map { row in
                    Mapping(id: row["_id"], code: row["code"], word: row["word"],
                            score: row["score"], baseScore: row["basescore"], code3r: row["code3r"],
                            related: row.optString("related"))
                }
            }
        }
        return results
    }

    /// Between-search SQL clause builder (mirrors Android expandBetweenSearchClause exactly).
    /// Android format: `code= 'a' or  (code >= 'ab' and code <'ac') `
    private func expandBetweenSearchClause(column: String, code: String) -> String {
        guard !code.isEmpty else { return "" }  // safety: empty code produces invalid SQL
        var selectClause = ""
        let len = code.count
        // Mirrors Java: int end = (len > 5) ? 6 : len;
        let end = len > 5 ? 6 : len
        if len > 1 {
            for j in 0..<(end - 1) {
                // Mirrors Java: selectClause.append(searchColumn).append("= '").append(prefix).append("' or ");
                let prefix = String(code.prefix(j + 1)).replacingOccurrences(of: "'", with: "''")
                selectClause += "\(column)= '\(prefix)' or "
            }
        }
        // Mirrors Java: chArray[code.length()-1]++ — safe: code.isEmpty checked above
        let escaped = code.replacingOccurrences(of: "'", with: "''")
        var nextCode = escaped  // fallback (extremely rare: max Unicode scalar)
        if let lastScalar = code.unicodeScalars.last,
           let inc = Unicode.Scalar(lastScalar.value + 1) {
            let stem = String(code.dropLast()).replacingOccurrences(of: "'", with: "''")
            nextCode = stem + String(inc).replacingOccurrences(of: "'", with: "''")
        }
        // Mirrors Java: " (" + col + " >= '" + escaped + "' and " + col + " <'" + nextCode + "') "
        selectClause += " (\(column) >= '\(escaped)' and \(column) <'\(nextCode)') "
        return selectClause
    }

    /// Reverse lookup: find all mapping records for a given word.
    func getMappingByWord(_ keyword: String?, table: String) -> [Mapping]? {
        guard !checkDBConnection() else { return nil }
        guard let keyword = keyword, !keyword.isEmpty else { return [] }
        guard isValidTableName(table) else { return [] }
        return try? dbQueue.read { db in
            let sql = """
                SELECT _id, code, word, score, basescore
                FROM \(table)
                WHERE word = ?
                ORDER BY score DESC
            """
            return try Row.fetchAll(db, sql: sql, arguments: [keyword]).map { row in
                var m = Mapping(id: (row.optInt64("_id") ?? 0),
                                code: (row.optString("code") ?? ""),
                                word: (row.optString("word") ?? ""),
                                score: (row.optInt("score") ?? 0),
                                baseScore: (row.optInt("basescore") ?? 0))
                m.recordType = Mapping.RecordType.exactMatchToWord
                return m
            }
        }
    }

    // MARK: - Related Phrases

    /// Returns related Mapping objects (for SearchServer). Throws version.
    func getRelatedMappings(parentWord: String, limit: Int = 10) throws -> [Mapping] {
        try dbQueue.read { db in
            let hasMultipleCharacters = parentWord.unicodeScalars.count > 1
            let predicate = hasMultipleCharacters ? "(pword = ? OR pword = ?)" : "pword = ?"
            // Android-parity ordering (LimeDB.java getRelatedPhrase): full-word matches
            // first, then user score, then base score as SEPARATE sort keys — not their
            // sum. Score fields are returned separately like Android's Mapping.
            let sql = """
                SELECT pword, cword, COALESCE(score,0) AS score, COALESCE(basescore,0) AS basescore,
                       length(pword) AS parent_length
                FROM related WHERE \(predicate) AND cword IS NOT NULL
                ORDER BY parent_length DESC, score DESC, basescore DESC LIMIT ?
            """
            let rows: [Row]
            if hasMultipleCharacters {
                let fallbackParent = String(parentWord.unicodeScalars.last!)
                rows = try Row.fetchAll(db, sql: sql,
                                        arguments: [parentWord, fallbackParent, limit])
            } else {
                rows = try Row.fetchAll(db, sql: sql, arguments: [parentWord, limit])
            }
            return rows.map { row in
                Mapping(id: 0, code: "", word: (row.optString("cword") ?? ""),
                        score: (row.optInt("score") ?? 0),
                        baseScore: (row.optInt("basescore") ?? 0),
                        recordType: Mapping.RecordType.relatedPhrase,
                        pword: (row.optString("pword") ?? parentWord))
            }
        }
    }

    /// Port of Android getRelatedPhrase() — returns Mapping list for UI display.
    func getRelatedPhrase(parentWord: String, limit: Int = 10) throws -> [Related] {
        try dbQueue.read { db in
            let sql = """
                SELECT _id, pword, cword, basescore, score
                FROM related
                WHERE pword = ? AND cword IS NOT NULL
                ORDER BY (COALESCE(basescore,0) + COALESCE(score,0)) DESC
                LIMIT ?
            """
            return try Row.fetchAll(db, sql: sql, arguments: [parentWord, limit]).map { row in
                Related(id:         (row.optInt64("_id") ?? 0),
                        parentWord: (row.optString("pword") ?? ""),
                        childWord:  (row.optString("cword") ?? ""),
                        score:      (row.optInt("score") ?? 0),
                        baseScore:  (row.optInt("basescore") ?? 0))
            }
        }
    }

    /// Port of Android getRelatedPhrase(pword, getAllRecords). Returns Mapping list.
    func getRelatedPhraseList(_ pword: String?, getAllRecords: Bool) -> [Mapping] {
        guard !checkDBConnection() else { return [] }
        guard let pword = pword, !pword.isEmpty else { return [] }
        let limit = getAllRecords ? LimeDB.FINAL_RESULT_LIMIT : LimeDB.INITIAL_RESULT_LIMIT
        var result: [Mapping] = []
        let rows: [Row]? = try? dbQueue.read { db in
            if pword.count > 1 {
                let last = String(pword.suffix(1))
                let sql = """
                    SELECT _id, pword, cword, basescore, score,
                           length(pword) AS len
                    FROM related
                    WHERE pword = ?
                       OR pword = ? AND cword IS NOT NULL
                    ORDER BY len DESC, score DESC, basescore DESC
                    LIMIT \(limit)
                """
                return try Row.fetchAll(db, sql: sql, arguments: [pword, last])
            } else {
                let sql = """
                    SELECT _id, pword, cword, basescore, score
                    FROM related
                    WHERE pword = ? AND cword IS NOT NULL
                    ORDER BY score DESC, basescore DESC
                    LIMIT \(limit)
                """
                return try Row.fetchAll(db, sql: sql, arguments: [pword])
            }
        }
        guard let rows = rows else { return [] }
        var rsize = 0
        for row in rows {
            guard let cword = row.optString("cword"), !cword.isEmpty else { continue }
            let rowPword = row.optString("pword") ?? pword
            var m = Mapping(id:        (row.optInt64("_id") ?? 0),
                            code:      "",
                            word:      cword,
                            score:     (row.optInt("score") ?? 0),
                            baseScore: (row.optInt("basescore") ?? 0),
                            pword:     rowPword)   // spec §14: populate pword
            m.recordType = Mapping.RecordType.relatedPhrase
            result.append(m)
            rsize += 1
        }
        if !getAllRecords && rsize == LimeDB.INITIAL_RESULT_LIMIT {
            var more = Mapping(id: 0, code: "has_more_records", word: "...", score: 0, baseScore: 0)
            more.recordType = Mapping.RecordType.hasMoreMark
            result.append(more)
        }
        return result
    }

    /// Check if a pword→cword pair exists in the related table. Returns Mapping? with id and score.
    func isRelatedPhraseExist(_ pword: String?, _ cword: String?) -> Mapping? {
        guard !checkDBConnection() else { return nil }
        guard let pword = pword, !pword.isEmpty else { return nil }
        return try? dbQueue.read { db in
            try isRelatedPhraseExist(pword, cword, db: db)
        }
    }

    /// Inner lookup that works on an already-open Database connection (avoids GRDB reentrance).
    private func isRelatedPhraseExist(_ pword: String, _ cword: String?, db: Database) throws -> Mapping? {
        let sql: String
        let args: StatementArguments
        if let cword = cword, !cword.isEmpty {
            sql = "SELECT _id, pword, cword, score, basescore FROM related WHERE pword = ? AND cword = ? LIMIT 1"
            args = [pword, cword]
        } else {
            sql = "SELECT _id, pword, cword, score, basescore FROM related WHERE pword = ? AND cword IS NULL LIMIT 1"
            args = [pword]
        }
        guard let row = try Row.fetchOne(db, sql: sql, arguments: args) else { return nil }
        var m = Mapping(id:        (row.optInt64("_id") ?? 0),
                        code:      "",
                        word:      (row.optString("cword") ?? ""),
                        score:     (row.optInt("score") ?? 0),
                        baseScore: (row.optInt("basescore") ?? 0))
        m.recordType = Mapping.RecordType.relatedPhrase
        return m
    }

    /// Add or update a pword→cword related phrase. Returns new score or -1.
    /// Mirrors Android addOrUpdateRelatedPhraseRecord(pword, cword) branch-for-branch.
    @discardableResult
    func addOrUpdateRelatedPhraseRecord(_ pword: String, _ cword: String?) -> Int {
        guard !checkDBConnection() else { return -1 }
        guard !pword.isEmpty else { return -1 }
        // Android line 1086: bail only when cword is non-null AND learning is disabled.
        if !learnRelatedWords && cword != nil { return -1 }
        // Android lines 1089-1103: strip Chinese symbols from cword (not pword) when learning is on.
        var effectiveCword = cword
        if learnRelatedWords {
            if var cw = cword {
                for s in LimeDB.chineseSymbolsToFilter {
                    cw = cw.replacingOccurrences(of: s, with: "")
                }
                if cw.isEmpty { return -1 }
                effectiveCword = cw
            }
        }
        var score = 1
        var relatedScoreUpdate: (rowId: Int64, score: Int)?
        do {
            try dbQueue.write { db in
                let munit = try isRelatedPhraseExist(pword, effectiveCword, db: db)
                if let m = munit {
                    let rowId = m.id
                    let cached = relatedScore[rowId]
                    score = (cached ?? m.score) + 1
                    try db.execute(sql: "UPDATE related SET score = ? WHERE _id = ?",
                                   arguments: [score, rowId])
                    relatedScoreUpdate = (rowId, score)
                } else {
                    try db.execute(
                        sql: "INSERT INTO related (pword, cword, score) VALUES (?, ?, ?)",
                        arguments: [pword, effectiveCword, score])
                }
                if let cword = effectiveCword, !cword.isEmpty {
                    try journalHotLearning(db, table: "related", k1: pword, k2: cword)
                }
            }
        } catch {
            return -1
        }
        if let update = relatedScoreUpdate {
            relatedScore[update.rowId] = update.score
        }
        return score
    }

    // MARK: - Score Update

    /// Increment score for a mapping or related phrase record.
    func addScore(_ mapping: Mapping) {
        guard !checkDBConnection() else { return }
        guard !mapping.word.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        try? dbQueue.write { db in
            if mapping.isRelatedPhraseRecord {
                let newScore = mapping.score + 1
                relatedScore[mapping.id] = newScore
                try db.execute(sql: "UPDATE related SET score = ? WHERE _id = ?",
                               arguments: [newScore, mapping.id])
            } else {
                try db.execute(
                    sql: "UPDATE \(currentTableName) SET score = score + 1 WHERE word = ?",
                    arguments: [mapping.word])
            }
        }
    }

    /// Direct score update by record ID (for SearchServer).
    func updateScore(id: Int64, score: Int, tableName: String) throws {
        guard isValidTableName(tableName) else { throw LimeDBError.invalidTableName(tableName) }
        try dbQueue.write { db in
            guard let row = try Row.fetchOne(db,
                sql: "SELECT code, word FROM \(tableName) WHERE _id = ? LIMIT 1",
                arguments: [id]) else { return }
            guard let code = row.optString("code"), !code.isEmpty,
                  let word = row.optString("word"), !word.isEmpty else { return }
            try db.execute(sql: "UPDATE \(tableName) SET score = ? WHERE _id = ?",
                           arguments: [score, id])
            try journalHotLearning(db, table: tableName, k1: code, k2: word)
        }
    }

    // MARK: - Mapping Record Upsert

    /// Upsert with current table name (convenience, mirrors Java addOrUpdateMappingRecord(code,word)).
    func addOrUpdateMappingRecord(_ code: String, _ word: String) {
        addOrUpdateMappingRecord(currentTableName, code, word, -1)
    }

    /// Full upsert with explicit table and score. score=-1 → auto-increment. Mirrors Java 4-arg overload.
    func addOrUpdateMappingRecord(_ table: String, _ code: String, _ word: String, _ score: Int) {
        guard !checkDBConnection() else { return }
        guard !code.isEmpty, !word.isEmpty else { return }
        guard isValidTableName(table) else { return }
        do {
            try dbQueue.write { db in
                try addOrUpdateMappingRecord(table, code, word, score, db: db)
                try journalHotLearning(db, table: table, k1: code, k2: word)
            }
        } catch {
            return
        }
    }

    /// Throws variant for SearchServer (LD phrase learning).
    func addOrUpdateMappingRecord(code: String, word: String, tableName: String) throws {
        guard !checkDBConnection() else { return }
        guard !code.isEmpty, !word.isEmpty else { return }
        guard isValidTableName(tableName) else { throw LimeDBError.invalidTableName(tableName) }
        try dbQueue.write { db in
            try addOrUpdateMappingRecord(tableName, code, word, -1, db: db)
            try journalHotLearning(db, table: tableName, k1: code, k2: word)
        }
    }

    private func addOrUpdateMappingRecord(_ table: String, _ code: String, _ word: String,
                                          _ score: Int, db: Database) throws {
        let existing = try Row.fetchOne(db,
            sql: "SELECT _id, score FROM \(table) WHERE code = ? AND word = ?",
            arguments: [code, word])
        if let ex = existing {
            let newScore = score == -1 ? ((ex["score"] as Int? ?? 0) + 1) : score
            try db.execute(sql: "UPDATE \(table) SET score = ? WHERE _id = ?",
                           arguments: [newScore, ex["_id"] as Int64? ?? 0])
        } else {
            let insertScore = score == -1 ? 1 : score
            if table == "phonetic" {
                let noToneCode = code.replacingOccurrences(of: "[ 3467]",
                    with: "", options: .regularExpression)
                try db.execute(
                    sql: "INSERT INTO \(table) (code, word, score, basescore, code3r) VALUES (?,?,?,0,?)",
                    arguments: [code, word, insertScore, noToneCode])
            } else {
                try db.execute(
                    sql: "INSERT INTO \(table) (code, word, score, basescore) VALUES (?,?,?,0)",
                    arguments: [code, word, insertScore])
            }
        }
    }

    // MARK: - IM Config (setImConfig / getImConfig / removeImConfig / resetImConfig)

    /// Get one config value. Mirrors Java getImConfig(imCode, field).
    func getImConfig(_ imCode: String?, _ field: String?) -> String? {
        guard !checkDBConnection() else { return nil }
        guard let imCode = imCode, !imCode.isEmpty,
              let field = field, !field.isEmpty else { return nil }
        return try? dbQueue.read { db in
            try String.fetchOne(db,
                sql: "SELECT desc FROM im WHERE code = ? AND title = ? LIMIT 1",
                arguments: [imCode, field])
        }
    }

    /// Set a config value (delete + insert). Mirrors Java setImConfig(imCode, field, value).
    func setImConfig(_ imCode: String?, _ field: String?, _ value: String?) {
        guard !checkDBConnection() else { return }
        guard let imCode = imCode, !imCode.isEmpty,
              let field = field, !field.isEmpty else { return }
        try? dbQueue.write { db in
            try db.execute(sql: "DELETE FROM im WHERE code = ? AND title = ?",
                           arguments: [imCode, field])
            if let v = value {
                try db.execute(
                    sql: "INSERT INTO im (code, title, desc) VALUES (?, ?, ?)",
                    arguments: [imCode, field, v])
            }
        }
    }

    /// Remove one config entry. Mirrors Java removeImConfig(imCode, field).
    func removeImConfig(_ imCode: String?, _ field: String?) {
        guard !checkDBConnection() else { return }
        guard let imCode = imCode, !imCode.isEmpty,
              let field = field, !field.isEmpty else { return }
        try? dbQueue.write { db in
            try db.execute(sql: "DELETE FROM im WHERE code = ? AND title = ?",
                           arguments: [imCode, field])
        }
    }

    /// Delete all im rows for an IM code. Mirrors Java resetImConfig(im).
    func resetImConfig(_ imCode: String?) {
        guard !checkDBConnection() else { return }
        guard let imCode = imCode, !imCode.isEmpty else { return }
        _ = deleteRecord("im", "code = ?", [imCode])
    }

    /// Returns the selection key string for an IM. Fallback "1234567890".
    func getSelkeyForIM(_ imCode: String) -> String {
        let key = getImConfig(imCode, "selkey")
        return (key?.isEmpty == false) ? key! : "1234567890"
    }

    // MARK: - IM Config List

    /// Returns raw im table rows filtered by code and/or configEntry.
    func getImConfigList(_ code: String?, _ configEntry: String?) -> [LimeImConfigRow] {
        guard !checkDBConnection() else { return [] }
        var whereClauses: [String] = []
        var args: [String] = []
        if let c = code, c.count > 1 { whereClauses.append("code = ?"); args.append(c) }
        if let ce = configEntry, ce.count > 1 { whereClauses.append("title = ?"); args.append(ce) }
        let whereStr = whereClauses.isEmpty ? "" : " WHERE " + whereClauses.joined(separator: " AND ")
        let sql = "SELECT _id, code, title, desc, keyboard, disable, selkey, endkey, spacestyle FROM im\(whereStr) ORDER BY desc ASC"
        return (try? dbQueue.read { db in
            try Row.fetchAll(db, sql: sql, arguments: StatementArguments(args)).map { row in
                LimeImConfigRow(
                    id:         (row.optInt("_id") ?? 0),
                    code:       (row.optString("code") ?? ""),
                    title:      (row.optString("title") ?? ""),
                    desc:       (row.optString("desc") ?? ""),
                    keyboard:   (row.optString("keyboard") ?? ""),
                    disable:    Self.parseBoolFlag(row["disable"]),
                    selkey:     (row.optString("selkey") ?? ""),
                    endkey:     (row.optString("endkey") ?? ""),
                    spacestyle: (row.optString("spacestyle") ?? "")
                )
            }
        }) ?? []
    }

    /// Returns all registered IMs from the im table.
    /// The im table uses Android's key-value schema. Each IM has a seed/registration row
    /// (title = display label) plus key-value rows (title = field name, desc = value).
    /// Keyboard code is stored in the key-value row where title="keyboard", keyboard=code.
    /// Enabled state is stored in the key-value row where title="disable", desc="true"/"false".
    func getAllImConfigs() throws -> [ImConfig] {
        // Known key-value field names — rows with these titles are config entries, not IM seed rows.
        let kvFields: Set<String> = ["keyboard","disable","selkey","endkey","limeendkey","spacestyle",
                                     "imkeys","imkeynames","name","label","version"]
        let allRows = getImConfigList(nil, nil)

        // Group all rows by IM code.
        var grouped: [String: [LimeImConfigRow]] = [:]
        for row in allRows where !row.code.isEmpty {
            if row.code == "emoji" { continue }
            grouped[row.code, default: []].append(row)
        }

        return grouped.compactMap { (code, rows) -> ImConfig? in
            // Seed/registration row: title is the display label, not a config field name.
            // Legacy DBs may only contain kv rows; still surface them through the DB model
            // and resolve their display names below.
            guard let seedRow = rows.first(where: { !kvFields.contains($0.title) && !$0.title.isEmpty })
                    ?? rows.first(where: { $0.title == "name" })
                    ?? rows.first(where: { !$0.title.isEmpty })
            else { return nil }
            // Keyboard code: from key-value row (title="keyboard"), fall back to seed row column.
            let kbRow = rows.first(where: { $0.title == "keyboard" })
            let keyboardId = kbRow?.keyboard.isEmpty == false ? kbRow!.keyboard : seedRow.keyboard
            // Enabled state: from key-value row (title="disable", desc="true"/"false").
            let disableStr = rows.first(where: { $0.title == "disable" })?.desc ?? "false"
            let enabled = disableStr != "true"
            // Full name from title="name" config entry (mirrors Android LIME.IM_FULL_NAME / sidebar).
            // Legacy DBs may only have seed rows; keep the fallback here so UI lists and pickers
            // can consume the resolved label directly.
            let storedFullName = rows.first(where: { $0.title == "name" })?.desc ?? ""
            let fullName = storedFullName.isEmpty ? defaultImFullName(code, fallback: seedRow.title) : storedFullName
            // Display label: prefer the cloud DB's `name` kv row (e.g. "拼音輸入法"),
            // because for cloud-installed IMs the seedRow.title is an arbitrary
            // non-kv field (`source` / `amount` / `original` / `import`) — whichever
            // row happens to be first. The synthetic legacy registerIM flow stores
            // the friendly label in `seedRow.title`, so fall back to that.
            let label = fullName
            return ImConfig(
                id:                  Int64(seedRow.id),
                imName:              code,
                tableNick:           code,
                label:               label,
                fullName:            fullName,
                keyboardId:          keyboardId,
                keyboardLandscapeId: keyboardId,
                enabled:             enabled,
                sortOrder:           seedRow.id
            )
        }.sorted { $0.sortOrder < $1.sortOrder }
    }

    // MARK: - Keyboard Config

    /// Get keyboard config by code. Returns nil if not found.
    func getKeyboardConfig(_ keyboard: String?) -> KeyboardConfig? {
        guard !checkDBConnection() else { return nil }
        guard let keyboard = keyboard, !keyboard.isEmpty else { return nil }
        if keyboard == "lime" {
            return KeyboardConfig(id: 0, code: "lime", name: "LIME", desc: "LIME 預設鍵盤",
                                  type: "phone", image: "lime_keyboard_preview",
                                  imkb: "lime", imshiftkb: "lime_shift",
                                  engkb: "lime", engshiftkb: "lime_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        if keyboard == "phonetic" {
            return KeyboardConfig(id: 0, code: "phonetic", name: "注音", desc: "注音輸入法鍵盤",
                                  type: "phone", image: "phonetic_keyboard_preview",
                                  imkb: "lime_phonetic", imshiftkb: "lime_phonetic_shift",
                                  engkb: "lime", engshiftkb: "lime_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        // Hardcoded fallbacks for "wb" and "hs"
        if keyboard == "wb" {
            return KeyboardConfig(id: 0, code: "wb", name: "筆順五碼", desc: "筆順五碼輸入法鍵盤",
                                  type: "phone", image: "wb_keyboard_preview",
                                  imkb: "lime_wb", imshiftkb: "lime_wb",
                                  engkb: "lime_abc", engshiftkb: "lime_abc_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        if keyboard == "hs" {
            return KeyboardConfig(id: 0, code: "hs", name: "華象直覺", desc: "華象直覺輸入法鍵盤",
                                  type: "phone", image: "hs_keyboard_preview",
                                  imkb: "lime_hs", imshiftkb: "lime_hs_shift",
                                  engkb: "lime_abc", engshiftkb: "lime_abc_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        if keyboard == "limenum" {
            return KeyboardConfig(id: 0, code: "limenum", name: "LIMENUM", desc: "LIME+數字列鍵盤",
                                  type: "phone", image: "lime_keyboard_preview",
                                  imkb: "lime_number", imshiftkb: "lime_number_shift",
                                  engkb: "lime_number", engshiftkb: "lime_number_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        if keyboard == "cjnum" {
            return KeyboardConfig(id: 0, code: "cjnum", name: "倉頡數字", desc: "倉頡+數字列鍵盤",
                                  type: "phone", image: "cj_keyboard_preview",
                                  imkb: "lime_cj_number", imshiftkb: "lime_cj_number_shift",
                                  engkb: "lime_number", engshiftkb: "lime_number_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        if keyboard == "dayisym" {
            return KeyboardConfig(id: 0, code: "dayisym", name: "大易字根", desc: "大易字根鍵盤",
                                  type: "phone", image: "dayi_keyboard_preview",
                                  imkb: "lime_dayi_sym", imshiftkb: "lime_dayi_sym_shift",
                                  engkb: "lime", engshiftkb: "lime_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        // feat#159: hardcoded fallback for "limenumsym2" (三碼/tricode) — mirrors the
        // insertKeyboardIfAbsent seed row (ensureLimeNumSym2Keyboard) column-for-column,
        // matching Android's ensureLimeNumSym2Keyboard (LimeDB.java).
        if keyboard == "limenumsym2" {
            return KeyboardConfig(id: 0, code: "limenumsym2", name: "LIMENUMSYM2", desc: "LIME+數字符號鍵盤2",
                                  type: "phone", image: "lime_number_symbol_keyboard_priview",
                                  imkb: "lime_num_sym2", imshiftkb: "lime_num_sym2_shift",
                                  engkb: "lime_english_number", engshiftkb: "lime_english_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        if keyboard == "arraynum" {
            return KeyboardConfig(id: 0, code: "arraynum", name: "行列", desc: "行列+數字列鍵盤",
                                  type: "phone", image: "array_keyboard_preview",
                                  imkb: "lime_array_number", imshiftkb: "lime_array_number_shift",
                                  engkb: "lime_number", engshiftkb: "lime_number_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        if keyboard == "phonenum" {
            return KeyboardConfig(id: 0, code: "phonenum", name: "電話數字", desc: "電話數字鍵盤",
                                  type: "phone", image: "phonenum_keyboard_preview",
                                  imkb: "phone_simple", imshiftkb: "phone_simple",
                                  engkb: "lime", engshiftkb: "lime_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        if keyboard == "ez" {
            return KeyboardConfig(id: 0, code: "ez", name: "輕鬆", desc: "輕鬆輸入法鍵盤",
                                  type: "phone", image: "ez_keyboard_preview",
                                  imkb: "lime_ez", imshiftkb: "lime_ez_shift",
                                  engkb: "lime", engshiftkb: "lime_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        // Hardcoded fallback for "cj" (倉頡). cj4 (Haha Cangjie, #84) deletes its own
        // keyboard row in ensureCj4Schema and reuses the existing Cangjie keyboard, so
        // "cj" must always resolve even when the bundled keyboard catalog is absent.
        if keyboard == "cj" {
            return KeyboardConfig(id: 0, code: "cj", name: "倉頡", desc: "倉頡輸入法鍵盤",
                                  type: "phone", image: "cj_keyboard_preview",
                                  imkb: "lime_cj", imshiftkb: "lime_cj_shift",
                                  engkb: "lime", engshiftkb: "lime_shift",
                                  symbolkb: "symbols", symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
        }
        return try? dbQueue.read { db in
            guard let row = try Row.fetchOne(db,
                sql: "SELECT * FROM keyboard WHERE code = ? LIMIT 1",
                arguments: [keyboard]) else { return nil }
            // Bind to TYPED OPTIONAL locals (see getKeyboardConfigList for the
            // rationale — inline `as T?` can hit the force-decode overload).
            let id:            Int64?  = row["_id"]
            let code:          String? = row["code"]
            let name:          String? = row["name"]
            let desc:          String? = row["desc"]
            let type:          String? = row["type"]
            let image:         String? = row["image"]
            let imkb:          String? = row["imkb"]
            let imshiftkb:     String? = row["imshiftkb"]
            let engkb:         String? = row["engkb"]
            let engshiftkb:    String? = row["engshiftkb"]
            let symbolkb:      String? = row["symbolkb"]
            let symbolshiftkb: String? = row["symbolshiftkb"]
            // The Android schema stores `disable` with mixed storage:
            // some rows hold TEXT ("true"/"false"), others hold INTEGER (0/1).
            // Any Swift-typed subscript (Int? or String?) fatal-errors on the
            // other storage class, so read the raw DatabaseValue and interpret
            // both forms ourselves.
            let isDisabled = Self.parseBoolFlag(row["disable"])
            return KeyboardConfig(
                id:            id ?? 0,
                code:          code ?? "",
                name:          name ?? "",
                desc:          desc ?? "",
                type:          type ?? "",
                image:         image ?? "",
                imkb:          imkb ?? "",
                imshiftkb:     imshiftkb ?? "",
                engkb:         engkb ?? "",
                engshiftkb:    engshiftkb ?? "",
                symbolkb:      symbolkb ?? "",
                symbolshiftkb: symbolshiftkb ?? "",
                isDisabled:    isDisabled
            )
        }
    }

    func getKeyboardConfigList() -> [KeyboardConfig]? {
        guard !checkDBConnection() else { return nil }
        return try? dbQueue.read { db in
            try Row.fetchAll(db,
                sql: "SELECT * FROM keyboard ORDER BY name ASC").map { row in
                // Bind every column to a TYPED OPTIONAL local first so Swift
                // dispatches to GRDB's optional `Row.subscript<Value>(_:) -> Value?`
                // overload. The compact `row["col"] as Int?` form can still hit the
                // non-optional force-decode overload, which crashes on NULL.
                let id:            Int64?  = row["_id"]
                let code:          String? = row["code"]
                let name:          String? = row["name"]
                let desc:          String? = row["desc"]
                let type:          String? = row["type"]
                let image:         String? = row["image"]
                let imkb:          String? = row["imkb"]
                let imshiftkb:     String? = row["imshiftkb"]
                let engkb:         String? = row["engkb"]
                let engshiftkb:    String? = row["engshiftkb"]
                let symbolkb:      String? = row["symbolkb"]
                let symbolshiftkb: String? = row["symbolshiftkb"]
                // Mixed TEXT/INTEGER storage — see parseBoolFlag.
                let isDisabled = Self.parseBoolFlag(row["disable"])
                return KeyboardConfig(
                    id:            id ?? 0,
                    code:          code ?? "",
                    name:          name ?? "",
                    desc:          desc ?? "",
                    type:          type ?? "",
                    image:         image ?? "",
                    imkb:          imkb ?? "",
                    imshiftkb:     imshiftkb ?? "",
                    engkb:         engkb ?? "",
                    engshiftkb:    engshiftkb ?? "",
                    symbolkb:      symbolkb ?? "",
                    symbolshiftkb: symbolshiftkb ?? "",
                    isDisabled:    isDisabled
                )
            }
        }
    }

    func getKeyboardInfo(_ keyboardCode: String, _ field: String) -> String? {
        guard !checkDBConnection() else { return nil }
        return try? dbQueue.read { db in
            guard let row = try Row.fetchOne(db,
                sql: "SELECT * FROM keyboard WHERE code = ? LIMIT 1",
                arguments: [keyboardCode]) else { return nil }
            // Safe optional cast — dispatches to GRDB's `-> Value?` subscript overload.
            let value: String? = row[field]
            return value
        }
    }

    /// Throws variant for getKeyboardList (used by keyboard extension).
    func getKeyboardList() throws -> [KeyboardConfig] {
        return getKeyboardConfigList() ?? []
    }

    // MARK: - IM Keyboard Assignment

    /// Mirrors Android setIMConfigKeyboard: remove existing key-value row then insert new one.
    func setIMConfigKeyboard(_ imCode: String, _ desc: String, _ keyboardCode: String) {
        guard !checkDBConnection() else { return }
        removeImConfig(imCode, "keyboard")
        try? dbQueue.write { db in
            try db.execute(
                sql: "INSERT INTO im (code, title, desc, keyboard) VALUES (?, ?, ?, ?)",
                arguments: [imCode, "keyboard", desc, keyboardCode])
        }
    }

    func setImConfigKeyboard(_ imCode: String, _ keyboard: KeyboardConfig) {
        setIMConfigKeyboard(imCode, keyboard.desc, keyboard.code)
    }

    /// Mirrors Android setImConfig(imCode, "disable", "true"/"false").
    func updateIMEnabled(imName: String, enabled: Bool) {
        setImConfig(imName, "disable", enabled ? "false" : "true")
    }

    func updateIMSortOrder(id: Int64, sortOrder: Int) throws {}

    // MARK: - Record List / Single Record

    func getRecordList(_ table: String, _ query: String?, searchByCode: Bool,
                       _ maximum: Int, _ offset: Int) -> [LimeRecord] {
        guard !checkDBConnection() else { return [] }
        guard isValidTableName(table) else { return [] }
        var whereClause: String
        if let q = query, !q.isEmpty {
            if searchByCode {
                let esc = q.replacingOccurrences(of: "'", with: "''")
                whereClause = "code LIKE '\(esc)%' AND ifnull(word, '') <> ''"
            } else {
                let esc = q.replacingOccurrences(of: "'", with: "''")
                whereClause = "word LIKE '%\(esc)%' AND ifnull(word, '') <> ''"
            }
        } else {
            whereClause = "ifnull(word, '') <> ''"
        }
        let orderCol = searchByCode ? "code" : "word"
        let limitStr = maximum > 0 ? " LIMIT \(maximum) OFFSET \(offset)" : ""
        let sql = "SELECT _id, code, word, score, basescore, code3r FROM \(table) WHERE \(whereClause) ORDER BY \(orderCol) ASC\(limitStr)"
        let rows = try? dbQueue.read { db in try Row.fetchAll(db, sql: sql) }
        return (rows ?? []).map { row in
            let rowId = (row.optInt64("_id") ?? 0)
            return LimeRecord(id: "\(rowId)",
                          code:      (row.optString("code") ?? ""),
                          word:      (row.optString("word") ?? ""),
                          score:     (row.optInt("score") ?? 0),
                          baseScore: (row.optInt("basescore") ?? 0),
                          code3r:    (row.optString("code3r") ?? ""))
        }
    }

    func getRecord(_ table: String, _ id: Int64) -> LimeRecord? {
        guard !checkDBConnection() else { return nil }
        let row: Row? = try? dbQueue.read { db in
            try Row.fetchOne(db,
                sql: "SELECT _id, code, word, score, basescore, code3r FROM \(table) WHERE _id = ? LIMIT 1",
                arguments: [id])
        }
        guard let row = row else { return nil }
        return LimeRecord(id:        "\(id)",
                      code:      (row.optString("code") ?? ""),
                      word:      (row.optString("word") ?? ""),
                      score:     (row.optInt("score") ?? 0),
                      baseScore: (row.optInt("basescore") ?? 0),
                      code3r:    (row.optString("code3r") ?? ""))
    }

    // MARK: - Related Table Operations

    func getRelated(_ pword: String?, _ maximum: Int, _ offset: Int) -> [Related] {
        guard !checkDBConnection() else { return [] }
        var whereParts: [String] = []
        var args: [String] = []
        var searchPword = pword
        var cwordFilter = ""
        if let p = pword, p.count > 1 {
            cwordFilter = String(p.dropFirst())
            searchPword = String(p.prefix(1))
        }
        if let p = searchPword, !p.isEmpty {
            whereParts.append("pword = ?"); args.append(p)
        }
        if !cwordFilter.isEmpty {
            whereParts.append("cword LIKE ?")
            args.append(cwordFilter + "%")
        }
        whereParts.append("ifnull(cword, '') <> ''")
        let whereStr = whereParts.joined(separator: " AND ")
        let limitStr = maximum > 0 ? " LIMIT \(maximum) OFFSET \(offset)" : ""
        let sql = "SELECT _id, pword, cword, basescore, score FROM related WHERE \(whereStr) ORDER BY score DESC, basescore DESC\(limitStr)"
        return (try? dbQueue.read { db in
            try Row.fetchAll(db, sql: sql, arguments: StatementArguments(args)).map { row in
                Related(id:         (row.optInt64("_id") ?? 0),
                        parentWord: (row.optString("pword") ?? ""),
                        childWord:  (row.optString("cword") ?? ""),
                        score:      (row.optInt("score") ?? 0),
                        baseScore:  (row.optInt("basescore") ?? 0))
            }
        }) ?? []
    }

    func searchRelatedForManagement(_ query: String?, _ maximum: Int, _ offset: Int) -> [Related] {
        guard !checkDBConnection() else { return [] }
        let filter = relatedManagementFilter(query)
        let limit = maximum > 0 ? " LIMIT \(maximum) OFFSET \(offset)" : ""
        let sql = "SELECT _id, pword, cword, basescore, score FROM related WHERE \(filter.whereClause) ORDER BY score DESC, basescore DESC\(limit)"
        return (try? dbQueue.read { db in
            try Row.fetchAll(db, sql: sql, arguments: StatementArguments(filter.args)).map { row in
                Related(id:         (row.optInt64("_id") ?? 0),
                        parentWord: (row.optString("pword") ?? ""),
                        childWord:  (row.optString("cword") ?? ""),
                        score:      (row.optInt("score") ?? 0),
                        baseScore:  (row.optInt("basescore") ?? 0))
            }
        }) ?? []
    }

    func countRelatedForManagement(_ query: String?) -> Int {
        let filter = relatedManagementFilter(query)
        return countRecords("related", filter.whereClause,
                            filter.args.isEmpty ? nil : filter.args)
    }

    private func relatedManagementFilter(_ query: String?) -> (whereClause: String, args: [String]) {
        let base = "ifnull(cword, '') <> ''"
        guard let trimmed = query?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty else { return (base, []) }
        let escaped = trimmed
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "%", with: "\\%")
            .replacingOccurrences(of: "_", with: "\\_")
        let whereClause = base + " AND pword LIKE ? ESCAPE '\\'"
        return (whereClause, [escaped + "%"])
    }

    // MARK: - Backup / Restore

    func backupUserRecords(_ table: String) {
        guard !checkDBConnection() else { return }
        let backupTable = table + "_user"
        try? dbQueue.write { db in
            try db.execute(sql: "DROP TABLE IF EXISTS \(backupTable)")
            try db.execute(sql: """
                CREATE TABLE \(backupTable) AS
                SELECT * FROM \(table)
                WHERE word IS NOT NULL AND score > 0
                ORDER BY score DESC
            """)
        }
    }

    func checkBackupTable(_ table: String) -> Bool {
        guard !checkDBConnection() else { return false }
        guard !table.isEmpty else { return false }
        let backupTable = table + "_user"
        guard tableExists(backupTable) else { return false }
        return countRecords(backupTable, nil, nil) > 0
    }

    func dropBackupTable(_ table: String) -> Bool {
        guard !checkDBConnection() else { return false }
        guard isValidTableName(table) else { return false }
        let backupTable = table + "_user"
        try? dbQueue.write { db in
            try db.execute(sql: "DROP TABLE IF EXISTS \(backupTable)")
        }
        return true
    }

    @discardableResult
    func restoreUserRecords(_ table: String) -> Int {
        guard !checkDBConnection() else { return 0 }
        guard !table.isEmpty, isValidTableName(table) else { return 0 }
        let backupTable = table + "_user"
        guard tableExists(backupTable) else { return 0 }
        do {
            return try dbQueue.write { db in
                let records = try Row.fetchAll(db, sql: """
                    SELECT code, word, score FROM \(backupTable)
                    WHERE code IS NOT NULL AND code <> ''
                      AND word IS NOT NULL AND word <> ''
                    """)
                for row in records {
                    let code = row.optString("code") ?? ""
                    let word = row.optString("word") ?? ""
                    let score = row.optInt("score") ?? 0
                    try addOrUpdateMappingRecord(table, code, word, score, db: db)
                    try journalHotLearning(db, table: table, k1: code, k2: word)
                }
                return records.count
            }
        } catch {
            return 0
        }
    }

    func getBackupTableRecords(_ backupTableName: String) -> [[String: Any]]? {
        guard !checkDBConnection() else { return nil }
        guard let _ = backupTableName.range(of: "_user"), backupTableName.hasSuffix("_user") else { return nil }
        let base = String(backupTableName.dropLast(5))
        guard isValidTableName(base) else { return nil }
        return try? dbQueue.read { db in
            try Row.fetchAll(db, sql: "SELECT * FROM \(backupTableName)").map { row in
                var dict: [String: Any] = [:]
                for col in row.columnNames {
                    dict[col] = row[col]
                }
                return dict
            }
        }
    }

    // MARK: - Clear Table

    func clearTable(_ table: String) {
        guard !checkDBConnection() else { return }
        guard isValidTableName(table) else { return }
        try? dbQueue.write { db in
            try db.execute(sql: "DELETE FROM \(table)")
        }
        resetImConfig(table)
    }

    // MARK: - Check and Update Related Table

    func checkAndUpdateRelatedTable() {
        guard !checkDBConnection() else { return }
        try? dbQueue.write { db in
            // Ensure indexes exist
            try? db.execute(sql: "CREATE INDEX IF NOT EXISTS related_idx_pword ON related (pword)")
            try? db.execute(sql: "CREATE INDEX IF NOT EXISTS related_idx_cword ON related (cword)")
        }
    }

    // MARK: - checkPhoneticKeyboardSetting (no-op on iOS — no physical keyboard)

    func checkPhoneticKeyboardSetting() {
        // No physical keyboard types on iOS; no-op.
    }

    // MARK: - Key to Key Name (mirrors Android keyToKeyName)

    /// Converts input codes to display-friendly symbols (e.g. "1" → "ㄅ" for phonetic).
    func keyToKeyName(_ code: String?, _ table: String, _ composingText: Bool) -> String {
        guard let code = code else { return "" }
        if composingText && code.count > LimeDB.COMPOSING_CODE_LENGTH_LIMIT { return code }

        let kbType = phoneticKeyboardType
        // For ET26/HSU composing display, use a keyboard-type-specific cache slot
        // so the dual-map tables don't collide with the standard BPMF map.
        var keyTable = table
        if composingText && (table == "phonetic" || table == "et41" || table == "et_41" || table == "eten") {
            if kbType.hasPrefix("eten26") || kbType == "et26" || kbType.hasPrefix("hsu") ||
               kbType == "et_41" || kbType == "eten" {
                keyTable = table + kbType
            }
        }

        // Load key map if not cached
        if keysDefMap[keyTable] == nil || keysDefMap[keyTable]!.isEmpty {
            let keyString: String
            let keynameString: String
            var finalKeynameString: String? = nil
            switch table {
            case "phonetic", "et41", "et_41", "eten":
                if composingText && (kbType.hasPrefix("eten26") || kbType == "et26") {
                    keyString          = LimeDB.ETEN26_KEY
                    keynameString      = LimeDB.ETEN26_CHAR_INITIAL
                    finalKeynameString = LimeDB.ETEN26_CHAR_FINAL
                } else if composingText && kbType.hasPrefix("hsu") {
                    keyString          = LimeDB.HSU_KEY
                    keynameString      = LimeDB.HSU_CHAR_INITIAL
                    finalKeynameString = LimeDB.HSU_CHAR_FINAL
                } else if composingText && (kbType == "et_41" || kbType == "eten") {
                    keyString     = LimeDB.ETEN_KEY
                    keynameString = LimeDB.ETEN_CHAR
                } else {
                    keyString     = LimeDB.BPMF_KEY
                    keynameString = LimeDB.BPMF_CHAR
                }
            case "cj", "cj4", "scj", "cj5", "ecj":
                keyString = LimeDB.CJ_KEY
                keynameString = LimeDB.CJ_CHAR
            case "dayi":
                keyString = LimeDB.DAYI_KEY
                keynameString = LimeDB.DAYI_CHAR
            case "array":
                // Only 行列30 ("array") uses the array keyname map (e.g. ',' → "8v").
                // 行列10 ("array10") deliberately has NO keyname conversion — mirrors
                // Android, where keyToKeyName switches on DB_TABLE_ARRAY ("array") only
                // and "array10" falls through to default (returns the raw typed code).
                // This is also why ',' / '.' show as themselves on array10 instead of
                // their array root keynames.
                keyString = LimeDB.ARRAY_KEY
                keynameString = LimeDB.ARRAY_CHAR
            default:
                // Try loading from im table
                let ks = getImConfig(table, "imkeys") ?? ""
                let kn = getImConfig(table, "imkeynames") ?? ""
                if !ks.isEmpty && !kn.isEmpty {
                    var km: [String: String] = [:]
                    let chars = Array(ks)
                    let names = kn.components(separatedBy: "|")
                    for (i, c) in chars.enumerated() {
                        if i < names.count { km[String(c)] = names[i] }
                    }
                    km["|"] = "|"
                    keysDefMap[keyTable] = km
                }
                // Passthrough if no map found
                if keysDefMap[keyTable] == nil { return code }
                return buildKeyName(code: code, keyMap: keysDefMap[keyTable]!)
            }
            var km: [String: String] = [:]
            let chars = Array(keyString)
            let names = keynameString.components(separatedBy: "|")
            for (i, c) in chars.enumerated() {
                if i < names.count { km[String(c)] = names[i] }
            }
            km["|"] = "|"
            keysDefMap[keyTable] = km
            // Build and cache final map for dual-map keyboards
            if let fns = finalKeynameString {
                var fkm: [String: String] = [:]
                let fnames = fns.components(separatedBy: "|")
                for (i, c) in chars.enumerated() {
                    if i < fnames.count { fkm[String(c)] = fnames[i] }
                }
                fkm["|"] = "|"
                keysDefMap["final_" + keyTable] = fkm
            }
        }
        guard let keyMap = keysDefMap[keyTable], !keyMap.isEmpty else { return code }
        if let finalMap = keysDefMap["final_" + keyTable] {
            return buildKeyNameDual(code: code, initialMap: keyMap, finalMap: finalMap, kbType: kbType)
        }
        return buildKeyName(code: code, keyMap: keyMap)
    }

    private func buildKeyName(code: String, keyMap: [String: String]) -> String {
        var result = ""
        for ch in code {
            if let name = keyMap[String(ch)] { result += name }
        }
        return result.isEmpty ? code : result
    }

    /// Position-dependent dual-map key name conversion for ET26/HSU composing display.
    /// Mirrors Android keyToKeyName() lines 1677–1726.
    private func buildKeyNameDual(code: String,
                                   initialMap: [String: String],
                                   finalMap: [String: String],
                                   kbType: String) -> String {
        if code.count == 1 {
            // ET26: only "always initial" keys (qwdfjk) show a name for single char
            // HSU: always use initial map for single char
            let useInitial: Bool
            if kbType.hasPrefix("hsu") {
                useInitial = true
            } else {
                useInitial = LimeDB.ETEN26_ALWAYS_INITIAL_CHARS.contains(code)
            }
            guard useInitial, let c = initialMap[code] else { return code }
            return c.trimmingCharacters(in: .whitespaces)
        }
        // Multi-char: position 0 = initial map; position i > 0 = check trigger regex
        let triggerRegex = kbType.hasPrefix("hsu") ? LimeDB.HSU_INITIAL_TRIGGER_REGEX
                                                    : LimeDB.ETEN26_INITIAL_TRIGGER_REGEX
        var result = ""
        let codeChars = Array(code)
        for i in codeChars.indices {
            let s = String(codeChars[i])
            let c: String?
            if i == 0 {
                c = initialMap[s]
            } else {
                let prefix = String(code.prefix(i))
                let atInitial = prefix.range(of: triggerRegex, options: .regularExpression) != nil
                c = atInitial ? initialMap[s] : finalMap[s]
            }
            if let c { result += c.trimmingCharacters(in: .whitespaces) }
        }
        return result.isEmpty ? code : result
    }

    // MARK: - Code Remapping (spec §5 preProcessingRemappingCode)

    // Shifted-key remap for standard Phonetic / Dayi / EZ — from spec §5
    // "!" → "1", "@" → "2", ... ")" → "0" (shift+digit → digit)
    private static let SHIFTED_NUMERIC_KEY    = "!@#$%^&*()"
    private static let SHIFTED_NUMERIC_REMAP  = "1234567890"
    // Shift+symbol keys for phonetic: "<>?_:+\"" → ",./-;='"
    // Mirrors Android: SHIFTED_SYMBOL_KEY = "<>?_:+\"" (7 chars: < > ? _ : + ")
    private static let SHIFTED_SYMBOL_KEY     = "<>?_:+\""
    private static let SHIFTED_SYMBOL_REMAP   = ",./-;='"
    // Array IM: shifted-symbol-only remap (no numeric remap)
    // (uses same SHIFTED_SYMBOL_KEY → SHIFTED_SYMBOL_REMAP tables as phonetic)

    // ETEN-41 remap: typed key on ETEN keyboard → standard phonetic DB code.
    // Values ported from LimeDB.java ETEN_KEY / ETEN_KEY_REMAP.
    private static let ETEN_KEY       = "abcdefghijklmnopqrstuvwxyz12347890-=;',./!@#$&*()<>?_+:\""
    private static let ETEN_KEY_REMAP = "81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg7634f0p;5tg/yh-"
    private static let ETEN_CHAR      =
        "ㄚ|ㄅ|ㄒ|ㄉ|ㄧ|ㄈ|ㄐ|ㄏ|ㄞ|ㄖ|ㄎ|ㄌ|ㄇ|ㄋ|ㄛ|ㄆ|ㄟ|ㄜ|ㄙ|ㄊ|ㄩ|ㄍ|ㄝ|ㄨ|ㄡ|ㄠ" +
        "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|ㄓ|ㄔ|ㄕ|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄓ|ㄔ|ㄕ|ㄥ|ㄦ|ㄗ|ㄘ"

    // ETEN-26 dual-remap: 26-key phonetic, position-dependent.
    // Values ported from LimeDB.java ETEN26_KEY / ETEN26_KEY_REMAP_INITIAL / ETEN26_KEY_REMAP_FINAL.
    // Exception keys (always use INITIAL even when single char): q w d f j k
    // Position trigger: if composing ends with [dfjk ] → next char is initial
    private static let ETEN26_ALWAYS_INITIAL_CHARS = "qwdfjk"
    private static let ETEN26_INITIAL_TRIGGER_REGEX = "[dfjk ]$"
    private static let ETEN26_KEY           = "qazwsxedcrfvtgbyhnujmikolp,."
    private static let ETEN26_REMAP_INITIAL = "y8lhnju2vkzewr1tcsmba9dixq<>"
    private static let ETEN26_REMAP_FINAL   = "y8lhnju7vk6ewr1tcsm3a94ixq<>"
    // ETEN26 dual-key: keys that have two possible phonetic codes (initial/final ambiguous)
    private static let ETEN26_DUALKEY       = "yhvewrscpaxqs3467"
    private static let ETEN26_DUALKEY_REMAP = "o,gf;5p-s0/.pbdz2"

    // HSU dual-remap: 26-key phonetic, position-dependent.
    // Values ported from LimeDB.java HSU_KEY / HSU_KEY_REMAP_INITIAL / HSU_KEY_REMAP_FINAL.
    // Exception keys (always use INITIAL when single char): a e s d f j
    // Position trigger: if composing ends with [sdfj ] → next char is initial
    private static let HSU_ALWAYS_INITIAL_CHARS = "aesdfj"
    private static let HSU_INITIAL_TRIGGER_REGEX = "[sdfj ]$"
    private static let HSU_KEY           = "azwsxedcrfvtgbyhnujmikolpq,."
    private static let HSU_REMAP_INITIAL = "hylnju2vbzfwe18csm5a9d.xq`<>"
    private static let HSU_REMAP_FINAL   = "hyl7ju6vb3fwe18csm4a9d.xq`<>"
    // HSU dual-key: keys that have two possible phonetic codes (initial/final ambiguous)
    private static let HSU_DUALKEY       = "vbf45x/uhecsad763"
    private static let HSU_DUALKEY_REMAP = "g8t5r/-,okip0;n2z"

    // ETEN26 / HSU composing display char tables (mirrors Android ETEN26_CHAR_INITIAL/FINAL, HSU_CHAR_INITIAL/FINAL)
    private static let ETEN26_CHAR_INITIAL =
        "(ㄗ/ㄟ)|ㄚ|ㄠ|(ㄘ/ㄝ)|ㄙ|ㄨ|ㄧ|ㄉ|(ㄕ/ㄒ)|ㄜ|ㄈ|(ㄍ/ㄑ)|(ㄊ/ㄤ)|(ㄐ/ㄓ)|ㄅ|ㄔ|(ㄏ/ㄦ)|(ㄋ/ㄣ)|ㄩ|ㄖ|(ㄇ/ㄢ)|ㄞ|ㄎ|ㄛ|(ㄌ/ㄥ)|(ㄆ/ㄡ)|，|。"
    private static let ETEN26_CHAR_FINAL =
        "(ㄗ/ㄟ)|ㄚ|ㄠ|(ㄘ/ㄝ)|ㄙ|ㄨ|ㄧ|˙|(ㄕ/ㄒ)|ㄜ|ˊ|(ㄍ/ㄑ)|(ㄊ/ㄤ)|(ㄐ/ㄓ)|ㄅ|ㄔ|(ㄏ/ㄦ)|(ㄋ/ㄣ)|ㄩ|ˇ|(ㄇ/ㄢ)|ㄞ|ˋ|ㄛ|(ㄌ/ㄥ)|(ㄆ/ㄡ)|，|。"
    private static let HSU_CHAR_INITIAL =
        "(ㄘ/ㄟ)|ㄗ|ㄠ|ㄙ|ㄨ|(ㄧ/ㄝ)|ㄉ|(ㄕ/ㄒ)|ㄖ|ㄈ|(ㄔ/ㄑ)|ㄊ|(ㄍ/ㄜ)|ㄅ|ㄚ|(ㄏ/ㄛ)|(ㄋ/ㄣ)|ㄩ|(ㄐ/ㄓ)|(ㄇ/ㄢ)|ㄞ|(ㄎ/ㄤ)|ㄡ|(ㄌ/ㄥ/ㄦ)|ㄆ|q|，|。"
    private static let HSU_CHAR_FINAL =
        "(ㄘ/ㄟ)|ㄗ|ㄠ|(ㄙ/˙)|ㄨ|(ㄧ/ㄝ)|(ㄉ/ˊ)|(ㄕ/ㄒ)|ㄖ|(ㄈ/ˇ)|(ㄔ/ㄑ)|ㄊ|(ㄍ/ㄜ)|ㄅ|ㄚ|(ㄏ/ㄛ)|(ㄋ/ㄣ)|ㄩ|(ㄐ/ㄓ/ˋ)|(ㄇ/ㄢ)|ㄞ|(ㄎ/ㄤ)|ㄡ|(ㄥ/ㄦ)|ㄆ|q|，|。"

    /// Converts a raw input code string to the canonical form expected by the current
    /// phonetic table, based on `phoneticKeyboardType`. Called in getMappingByCode()
    /// for phonetic-family IMs. (spec §5)
    func preProcessingRemappingCode(_ code: String?) -> String {
        guard let code = code, !code.isEmpty else { return "" }

        let table = currentTableName
        let kbType = phoneticKeyboardType
        let cacheKey = table + "|" + kbType

        // Apply shifted-key remap for phonetic / dayi / ez / array IMs
        let shiftedCode = applyShiftedKeyRemap(code, tableName: table)

        // Phonetic key remapping (ETEN / HSU etc.) only applies when the *current table*
        // is a phonetic-family IM. Without this guard, a saved phoneticKeyboardType of
        // e.g. "eten26" would be applied to Dayi/Array codes and corrupt the query.
        let isPhoneticFamily = table.hasPrefix("phonetic") || table.hasPrefix("et") ||
                               table.hasPrefix("hsu") || table == "ez"
        guard isPhoneticFamily else { return shiftedCode }

        // Match Android's startsWith-based branching so the `_symbol` variants
        // (eten26_symbol, hsu_symbol) route to the same dual-map remap as their
        // non-`_symbol` counterparts.
        if kbType == "et_41" || kbType == "eten" {
            // ETEN 41-key: single remap table
            let map = buildOrGetSingleMap(
                cacheKey: cacheKey,
                keys: LimeDB.ETEN_KEY,
                remap: LimeDB.ETEN_KEY_REMAP)
            return applyCharMap(shiftedCode, map: map)
        } else if kbType.hasPrefix("eten26") || kbType == "et26" {
            // ETEN 26-key (incl. eten26_symbol): dual remap with position detection
            let initMap  = buildOrGetDualMap(cacheKey: cacheKey + "|I",
                                              keys: LimeDB.ETEN26_KEY,
                                              remap: LimeDB.ETEN26_REMAP_INITIAL,
                                              cache: &remapCacheInitial)
            let finalMap = buildOrGetDualMap(cacheKey: cacheKey + "|F",
                                              keys: LimeDB.ETEN26_KEY,
                                              remap: LimeDB.ETEN26_REMAP_FINAL,
                                              cache: &remapCacheFinal)
            return applyDualRemap(shiftedCode,
                                  initial: initMap, final: finalMap,
                                  alwaysInitial: LimeDB.ETEN26_ALWAYS_INITIAL_CHARS,
                                  triggerRegex: LimeDB.ETEN26_INITIAL_TRIGGER_REGEX)
        } else if kbType.hasPrefix("hsu") {
            // HSU (incl. hsu_symbol): dual remap with position detection
            let initMap  = buildOrGetDualMap(cacheKey: cacheKey + "|I",
                                              keys: LimeDB.HSU_KEY,
                                              remap: LimeDB.HSU_REMAP_INITIAL,
                                              cache: &remapCacheInitial)
            let finalMap = buildOrGetDualMap(cacheKey: cacheKey + "|F",
                                              keys: LimeDB.HSU_KEY,
                                              remap: LimeDB.HSU_REMAP_FINAL,
                                              cache: &remapCacheFinal)
            return applyDualRemap(shiftedCode,
                                  initial: initMap, final: finalMap,
                                  alwaysInitial: LimeDB.HSU_ALWAYS_INITIAL_CHARS,
                                  triggerRegex: LimeDB.HSU_INITIAL_TRIGGER_REGEX)
        } else {
            // Standard phonetic: shifted-key remap only
            return shiftedCode
        }
    }

    // MARK: - Remap Helpers

    private func applyShiftedKeyRemap(_ code: String, tableName: String) -> String {
        let isPhoneticLike = tableName.hasPrefix("phonetic") || tableName.hasPrefix("et") ||
                             tableName.hasPrefix("hsu") || tableName == "dayi" || tableName == "ez"
        let isArray = tableName.hasPrefix("array")
        guard isPhoneticLike || isArray else { return code }

        var result = ""
        for ch in code {
            let s = String(ch)
            if !isArray, let i = LimeDB.SHIFTED_NUMERIC_KEY.firstIndex(of: ch) {
                let idx = LimeDB.SHIFTED_NUMERIC_KEY.distance(
                    from: LimeDB.SHIFTED_NUMERIC_KEY.startIndex, to: i)
                result += String(LimeDB.SHIFTED_NUMERIC_REMAP[
                    LimeDB.SHIFTED_NUMERIC_REMAP.index(
                        LimeDB.SHIFTED_NUMERIC_REMAP.startIndex, offsetBy: idx)])
            } else if let i = LimeDB.SHIFTED_SYMBOL_KEY.firstIndex(of: ch) {
                let idx = LimeDB.SHIFTED_SYMBOL_KEY.distance(
                    from: LimeDB.SHIFTED_SYMBOL_KEY.startIndex, to: i)
                result += String(LimeDB.SHIFTED_SYMBOL_REMAP[
                    LimeDB.SHIFTED_SYMBOL_REMAP.index(
                        LimeDB.SHIFTED_SYMBOL_REMAP.startIndex, offsetBy: idx)])
            } else {
                result += s
            }
        }
        return result
    }

    private func buildOrGetSingleMap(cacheKey: String, keys: String, remap: String) -> [Character: Character] {
        if let cached = remapCacheInitial[cacheKey] { return cached }
        var map: [Character: Character] = [:]
        let ks = Array(keys)
        let rs = Array(remap)
        for (i, k) in ks.enumerated() where i < rs.count {
            map[k] = rs[i]
        }
        remapCacheInitial[cacheKey] = map
        return map
    }

    private func buildOrGetDualMap(cacheKey: String, keys: String, remap: String,
                                    cache: inout [String: [Character: Character]]) -> [Character: Character] {
        if let cached = cache[cacheKey] { return cached }
        var map: [Character: Character] = [:]
        let ks = Array(keys)
        let rs = Array(remap)
        for (i, k) in ks.enumerated() where i < rs.count {
            map[k] = rs[i]
        }
        cache[cacheKey] = map
        return map
    }

    private func applyCharMap(_ code: String, map: [Character: Character]) -> String {
        String(code.map { map[$0] ?? $0 })
    }

    private func applyDualRemap(_ code: String,
                                 initial: [Character: Character],
                                 final finalMap: [Character: Character],
                                 alwaysInitial: String,
                                 triggerRegex: String) -> String {
        var result = ""
        var accumulated = ""
        let alwaysSet = Set(alwaysInitial)
        let regex = try? NSRegularExpression(pattern: triggerRegex)
        for ch in code {
            // Determine if this character is at initial position
            let atInitial: Bool
            if alwaysSet.contains(ch) {
                atInitial = true
            } else if accumulated.isEmpty {
                atInitial = true
            } else {
                // Check if accumulated code ends with a trigger sequence
                let range = NSRange(accumulated.startIndex..., in: accumulated)
                atInitial = regex?.firstMatch(in: accumulated, range: range) != nil
            }
            let remapped = atInitial ? (initial[ch] ?? ch) : (finalMap[ch] ?? ch)
            result += String(remapped)
            accumulated += String(ch)
        }
        return result
    }

    // MARK: - Cache Reset

    /// Clears all in-memory caches (remap, dual-code, blacklist, key-name).
    /// Call when the user switches IM tables or phonetic keyboard type.
    func resetCache() {
        blackListLock.lock(); blackListCache.removeAll(); blackListLock.unlock()
        remapCacheInitial.removeAll()
        remapCacheFinal.removeAll()
        keysDefMap.removeAll()
        keysDualMap.removeAll()
        lastValidDualCodeList = nil
    }

    // MARK: - Blacklist Cache (mirrors Android checkBlackList / removeFromBlackList)

    /// Cache key: tableName_code (matches Java's cacheKey() method).
    private func cacheKey(_ code: String) -> String {
        return currentTableName + "_" + code
    }

    /// Returns true if code (or any of its prefixes + "%") is blacklisted. Thread-safe.
    private func checkBlackList(_ code: String) -> Bool {
        guard code.count >= LimeDB.DUALCODE_NO_CHECK_LIMIT else { return false }
        blackListLock.lock(); defer { blackListLock.unlock() }
        if blackListCache[cacheKey(code)] != nil { return true }
        for i in (LimeDB.DUALCODE_NO_CHECK_LIMIT - 1)..<code.count {
            let prefix = String(code.prefix(i + 1)) + "%"
            if blackListCache[cacheKey(prefix)] != nil { return true }
        }
        return false
    }

    /// Removes code and its prefix wildcards from the blacklist cache. Thread-safe.
    private func removeFromBlackList(_ code: String) {
        blackListLock.lock(); defer { blackListLock.unlock() }
        blackListCache.removeValue(forKey: cacheKey(code))
        for i in (LimeDB.DUALCODE_NO_CHECK_LIMIT - 1)..<code.count {
            let prefix = String(code.prefix(i + 1)) + "%"
            blackListCache.removeValue(forKey: cacheKey(prefix))
        }
    }

    // MARK: - Dual Code Support (mirrors Android preProcessingForExtraQueryConditions)

    /// Returns (extraSelectClause, extraExactMatchClause) for dual-code expansion,
    /// or nil when no expansion is needed. iOS supports ETEN26 and HSU phonetic only.
    private func preProcessingForExtraQueryConditions(_ code: String) -> (String, String)? {
        let table = currentTableName
        let kbType = phoneticKeyboardType
        let mapCacheKey = table + kbType

        // Build dual map if not cached. Match Android's startsWith-based routing so
        // `eten26_symbol` / `hsu_symbol` receive the same dual-code expansion as
        // `eten26` / `hsu`.
        if keysDualMap[mapCacheKey] == nil {
            var dualKey = ""
            var dualKeyRemap = ""
            if table == "phonetic" {
                if kbType.hasPrefix("eten26") || kbType == "et26" {
                    dualKey = LimeDB.ETEN26_DUALKEY
                    dualKeyRemap = LimeDB.ETEN26_DUALKEY_REMAP
                } else if kbType.hasPrefix("hsu") {
                    dualKey = LimeDB.HSU_DUALKEY
                    dualKeyRemap = LimeDB.HSU_DUALKEY_REMAP
                }
            }
            var map: [Character: Character] = [:]
            let ks = Array(dualKey)
            let rs = Array(dualKeyRemap)
            for (i, k) in ks.enumerated() where i < rs.count {
                map[k] = rs[i]
                map[rs[i]] = rs[i]   // value also maps to itself (mirrors Java reMap.put(value, value))
            }
            keysDualMap[mapCacheKey] = map
        }

        guard let dualMap = keysDualMap[mapCacheKey], !dualMap.isEmpty else {
            LimeDB._codeDualMapped = false
            return nil
        }

        // Build the single-pass dual code (replace each char with its dual mapping if present)
        var dualcodeChars = ""
        for ch in code {
            if let mapped = dualMap[ch] { dualcodeChars.append(mapped) }
        }

        LimeDB._codeDualMapped = true

        // Expand if dualcode differs, code changed, or phonetic code has mid-tone symbol
        let hasMidTone = (table == "phonetic") &&
                         code.range(of: ".+[ 3467].+", options: .regularExpression) != nil
        if dualcodeChars != code || code != lastCode || hasMidTone {
            return expandDualCode(code, mapCacheKey: mapCacheKey)
        }
        return nil
    }

    /// Builds all dual-code variants for the given code using a level-by-level tree walk.
    /// Mirrors Android buildDualCodeList().
    private func buildDualCodeList(_ code: String, mapCacheKey: String) -> Set<String> {
        guard let dualMap = keysDualMap[mapCacheKey], !dualMap.isEmpty else { return [] }

        var treeDualCodeList = Set<String>()
        var treemap: [[String]] = Array(repeating: [], count: code.count)
        let table = currentTableName

        for i in 0..<code.count {
            var levelnMap: [String] = []
            let lastLevelMap: [String] = i == 0 ? [code] : treemap[i - 1]
            guard !lastLevelMap.isEmpty else { continue }

            for entry in lastLevelMap {
                let entryChars = Array(entry)
                var c: Character = entryChars[min(i, entryChars.count - 1)]
                var codeMapped = false
                repeat {
                    let prefix = String(entry.prefix(i + 1))
                    if entry.count == 1 && !levelnMap.contains(entry) {
                        if blackListCache[cacheKey(entry)] == nil { treeDualCodeList.insert(entry) }
                        levelnMap.append(entry)
                        codeMapped = true
                    } else if entry.count > 1 && !levelnMap.contains(entry) &&
                              blackListCache[cacheKey(prefix + "%")] == nil {
                        if blackListCache[cacheKey(entry)] == nil { treeDualCodeList.insert(entry) }
                        levelnMap.append(entry)
                        codeMapped = true
                    } else if let n = dualMap[c], n != c {
                        let newCode = buildNewCode(entry: entry, newChar: n, at: i)
                        let newPrefix = String(newCode.prefix(i + 1))
                        if newCode.count == 1 && !levelnMap.contains(newCode) {
                            if blackListCache[cacheKey(newCode)] == nil { treeDualCodeList.insert(newCode) }
                            levelnMap.append(newCode)
                            codeMapped = true
                        } else if newCode.count > 1 && !levelnMap.contains(newCode) &&
                                  blackListCache[cacheKey(newPrefix + "%")] == nil {
                            levelnMap.append(newCode)
                            if blackListCache[cacheKey(newCode)] == nil { treeDualCodeList.insert(newCode) }
                            codeMapped = true
                        } else {
                            codeMapped = false
                        }
                        c = n
                    } else {
                        codeMapped = false
                    }
                } while codeMapped
            }
            treemap[i] = levelnMap
        }

        // For phonetic: also add no-tone variants for codes with mid-tone symbols
        if table == "phonetic" {
            let snapshot = treeDualCodeList
            for iterCode in snapshot {
                if iterCode.range(of: ".+[ 3467].+", options: .regularExpression) != nil {
                    let noTone = iterCode.replacingOccurrences(of: "[3467 ]", with: "", options: .regularExpression)
                    if !noTone.isEmpty && !treeDualCodeList.contains(noTone) && !checkBlackList(noTone) {
                        treeDualCodeList.insert(noTone)
                    }
                }
            }
        }
        return treeDualCodeList
    }

    /// Replaces the character at position `i` in `entry` with `newChar`. Mirrors Java getNewCode().
    private func buildNewCode(entry: String, newChar: Character, at i: Int) -> String {
        var chars = Array(entry)
        guard i < chars.count else { return entry }
        chars[i] = newChar
        return String(chars)
    }

    /// Generates OR SQL clauses for all dual-code variants. Mirrors Android expandDualCode().
    private func expandDualCode(_ code: String, mapCacheKey: String) -> (String, String) {
        let dualCodeList = buildDualCodeList(code, mapCacheKey: mapCacheKey)
        var selectClauses: [String] = []
        var exactMatchClauses: [String] = []
        var validCodes: [String] = []

        let table = currentTableName
        let isPhonetic = table == "phonetic"
        let noCheck = code.count < LimeDB.DUALCODE_NO_CHECK_LIMIT

        for dualcode in dualCodeList {
            var queryCode = dualcode
            var col = "code"

            if isPhonetic {
                let tonePresent = dualcode.range(of: ".+[3467 ].*", options: .regularExpression) != nil
                let toneNotLast = dualcode.range(of: ".+[3467 ].+", options: .regularExpression) != nil
                if tonePresent {
                    if toneNotLast || dualcode.count > 4 {
                        queryCode = dualcode.replacingOccurrences(of: "[3467 ]", with: "", options: .regularExpression)
                    }
                } else {
                    col = "code3r"
                }
            }

            let escapedCode = queryCode.replacingOccurrences(of: "'", with: "''")
            guard !escapedCode.isEmpty else { continue }

            if noCheck {
                // Short codes: skip DB validation, add OR clause if different from original
                if dualcode != code {
                    selectClauses.append("(\(expandBetweenSearchClause(column: col, code: queryCode)))")
                    exactMatchClauses.append("\(col) = '\(escapedCode)'")
                }
            } else {
                // Validate that this code returns at least one record
                let exists = (try? dbQueue.read { db in
                    try Row.fetchOne(db,
                        sql: "SELECT \(col) FROM \(table) WHERE \(col) = ? LIMIT 1",
                        arguments: [queryCode]) != nil
                }) ?? false

                if exists {
                    validCodes.append(dualcode)
                    removeFromBlackList(dualcode)
                    if dualcode != code {
                        selectClauses.append("(\(expandBetweenSearchClause(column: col, code: queryCode)))")
                        exactMatchClauses.append("(\(col) = '\(escapedCode)')")
                    }
                } else {
                    // Check whether any prefix-extensions exist (to decide blacklist scope).
                    // Build the open-upper-bound by incrementing the last unicode scalar of `escapedCode`
                    // — same safe pattern used by expandBetweenSearchClause (no asciiValue force-wrap).
                    var nextCode = escapedCode
                    if let lastScalar = escapedCode.unicodeScalars.last,
                       let incremented = Unicode.Scalar(lastScalar.value + 1) {
                        let stem = String(escapedCode.dropLast())
                        nextCode = stem + String(incremented)
                    }
                    nextCode = nextCode.replacingOccurrences(of: "'", with: "''")
                    let hasExtension = (try? dbQueue.read { db in
                        try Row.fetchOne(db,
                            sql: "SELECT \(col) FROM \(table) WHERE \(col) > ? AND \(col) < ? LIMIT 1",
                            arguments: [queryCode, nextCode]) != nil
                    }) ?? false

                    blackListLock.lock()
                    if hasExtension {
                        blackListCache[cacheKey(dualcode)] = true
                    } else {
                        blackListCache[cacheKey(dualcode + "%")] = true
                    }
                    blackListLock.unlock()
                }
            }
        }

        lastValidDualCodeList = validCodes.isEmpty ? nil : validCodes.joined(separator: "|")

        let selectStr    = selectClauses.isEmpty    ? "" : " OR " + selectClauses.joined(separator: " OR ")
        let exactStr     = exactMatchClauses.isEmpty ? "" : " OR " + exactMatchClauses.joined(separator: " OR ")
        return (selectStr, exactStr)
    }

    // MARK: - Code List String by Word (reverse lookup with key names)

    /// Reverse lookup with optional table override (mirrors Android getReverseLogupTable() preference).
    /// Passing `nil` uses `currentTableName`.
    func getCodeListStringByWord(_ keyword: String, table: String? = nil) -> String? {
        guard !checkDBConnection() else { return nil }
        let lookupTable = table ?? currentTableName
        guard let results = getMappingByWord(keyword, table: lookupTable),
              !results.isEmpty else { return nil }
        var parts: [String] = []
        for m in results {
            let keyname = keyToKeyName(m.code, lookupTable, false)
            if parts.isEmpty { parts.append(m.word + "=" + keyname) }
            else { parts.append(keyname) }
        }
        return parts.joined(separator: "; ")
    }

    // MARK: - Query With Pagination (mirrors Android queryWithPagination)

    /// Returns records from a table with optional WHERE clause and pagination.
    func queryWithPagination(_ table: String, _ whereClause: String?, _ whereArgs: [String]?,
                              _ orderBy: String?, _ limit: Int, _ offset: Int) -> [[String: Any]]? {
        guard !checkDBConnection() else { return nil }
        guard isValidTableName(table) else { return nil }
        var sql = "SELECT * FROM \(table)"
        var args: StatementArguments = []
        if let wc = whereClause, !wc.isEmpty {
            sql += " WHERE \(wc)"
            args = StatementArguments(whereArgs ?? [])
        }
        if let ob = orderBy, !ob.isEmpty { sql += " ORDER BY \(ob)" }
        if limit > 0 { sql += " LIMIT \(limit) OFFSET \(offset)" }
        return try? dbQueue.read { db in
            try Row.fetchAll(db, sql: sql, arguments: args).map { row in
                var dict: [String: Any] = [:]
                for col in row.columnNames { dict[col] = row[col] }
                return dict
            }
        }
    }

    // MARK: - Raw Query

    /// Execute a SELECT query with strict table-name validation. Returns row dicts or nil.
    /// Hardened: rejects multi-statement queries, subqueries, joins, and any FROM target
    /// not in the canonical allowlist (`isValidTableName`). Rejects pragma_*/sqlite_master.
    func rawQuery(_ query: String?) -> [[String: Any]]? {
        guard !checkDBConnection() else { return nil }
        guard let query = query, !query.isEmpty else { return nil }
        let lower = query.lowercased().trimmingCharacters(in: .whitespaces)
        // Must be a single SELECT — no semicolons (defeats statement chaining).
        guard lower.hasPrefix("select"), !lower.contains(";") else { return nil }
        // Disallow subqueries, joins, CTEs, and table-valued functions.
        let banned = [" join ", "(select", " with ", "pragma_", "sqlite_master", "sqlite_schema"]
        for needle in banned where lower.contains(needle) { return nil }
        // Strict regex: capture exactly one table identifier after FROM.
        let pattern = #"(?i)\bfrom\s+([A-Za-z_][A-Za-z0-9_]*)\b"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: query, range: NSRange(query.startIndex..., in: query)),
              match.numberOfRanges >= 2,
              let tableRange = Range(match.range(at: 1), in: query) else { return nil }
        let table = String(query[tableRange])
        guard isValidTableName(table) else { return nil }
        // Make sure there's only one FROM occurrence (no implicit join via comma + FROM).
        let secondMatch = regex.firstMatch(in: query,
            range: NSRange(query.index(after: tableRange.upperBound)..., in: query))
        guard secondMatch == nil else { return nil }
        return try? dbQueue.read { db in
            try Row.fetchAll(db, sql: query).map { row in
                var dict: [String: Any] = [:]
                for col in row.columnNames { dict[col] = row[col] }
                return dict
            }
        }
    }

    // MARK: - English Suggestions (FTS)

    func getEnglishSuggestions(_ word: String) -> [String]? {
        guard !checkDBConnection() else { return nil }
        // FTS dictionary table may not exist in iOS bundle — return empty gracefully
        // Note: tableExists() cannot be called from within a dbQueue block (reentrancy).
        guard tableExists("dictionary") else { return [] }
        return try? dbQueue.read { db in
            let sql = "SELECT word FROM dictionary WHERE word MATCH ? ORDER BY word ASC LIMIT 20"
            return try String.fetchAll(db, sql: sql, arguments: ["\(word)*"])
        }
    }

    // MARK: - Emoji (integrated main-db tables)

    // Emoji type constants (mirrors Android LIME.EMOJI_EN / EMOJI_TW / EMOJI_CN)
    static let EMOJI_EN = 1
    static let EMOJI_TW = 2
    static let EMOJI_CN = 3

    enum EmojiLocale {
        case en
        case tw
    }

    struct EmojiDataRow {
        let value: String
        let cp: String
        let groupName: String
        let subgroup: String
        let sortOrder: Int
        let nameEn: String
        let nameTw: String
        let tagsEn: String
        let tagsTw: String
        let version: Double
    }

    /// Legacy-compatible emoji lookup routed through the integrated emoji tables.
    func emojiConvert(_ source: String, _ emoji: Int) -> [Mapping] {
        switch emoji {
        case LimeDB.EMOJI_EN:
            return findEmojiForCandidate(source, locale: .en, limit: 8)
        case LimeDB.EMOJI_TW:
            return findEmojiForCandidate(source, locale: .tw, limit: 8)
        default:
            return []
        }
    }

    func findEmojiForCandidate(_ candidate: String, locale: EmojiLocale, limit: Int = 8) -> [Mapping] {
        let query = LimeDB.buildEmojiCandidateQuery(candidate)
        guard !query.isEmpty else { return [] }
        return queryEmojiFTS(query: query, code: candidate, limit: limit)
    }

    func searchEmoji(_ queryText: String, locale: EmojiLocale, limit: Int = 200) -> [Mapping] {
        let query = LimeDB.buildEmojiPanelSearchQuery(queryText)
        guard !query.isEmpty else { return [] }
        return queryEmojiFTS(query: query, code: queryText, limit: limit)
    }

    private func queryEmojiFTS(query: String, code: String, limit: Int) -> [Mapping] {
        refreshEmojiDataIfNeeded()
        let safeLimit = max(limit, 1)
        let words: [String] = (try? dbQueue.read { db in
            try String.fetchAll(db, sql: """
                SELECT d.value
                FROM \(LimeDB.EMOJI_TABLE_FTS) f
                JOIN \(LimeDB.EMOJI_TABLE_DATA) d ON d.rowid = f.rowid
                LEFT JOIN \(LimeDB.EMOJI_TABLE_USER) u ON u.value = d.value
                WHERE \(LimeDB.EMOJI_TABLE_FTS) MATCH ?
                ORDER BY (u.last_used IS NULL), u.last_used DESC, d.sort_order ASC
                LIMIT ?
            """, arguments: [query, safeLimit])
        }) ?? []
        var seen = Set<String>()
        return words.compactMap { word -> Mapping? in
            guard seen.insert(word).inserted else { return nil }
            return Mapping(id: 0, code: code, word: word,
                           score: 0, baseScore: 0,
                           recordType: Mapping.RecordType.emoji)
        }
    }

    func loadEmojiPanelItems(limit: Int = 360) -> [Mapping] {
        refreshEmojiDataIfNeeded()
        let safeLimit = max(limit, 1)
        let words: [String] = (try? dbQueue.read { db in
            try String.fetchAll(db, sql: """
                SELECT d.value
                FROM \(LimeDB.EMOJI_TABLE_DATA) d
                LEFT JOIN \(LimeDB.EMOJI_TABLE_USER) u ON u.value = d.value
                ORDER BY (u.last_used IS NULL), u.last_used DESC, d.sort_order ASC
                LIMIT ?
            """, arguments: [safeLimit])
        }) ?? []
        return words.map {
            Mapping(id: 0, code: "", word: $0,
                    score: 0, baseScore: 0,
                    recordType: Mapping.RecordType.emoji)
        }
    }

    func loadEmojiCategoryPages() -> [[Mapping]] {
        refreshEmojiDataIfNeeded()
        let pages: [[String]] = (try? dbQueue.read { db in
            try LimeDB.EMOJI_CATEGORY_GROUPS.map { groupName in
                try String.fetchAll(db, sql: """
                    SELECT value
                    FROM \(LimeDB.EMOJI_TABLE_DATA)
                    WHERE group_name = ?
                    ORDER BY sort_order ASC
                """, arguments: [groupName])
            }
        }) ?? Array(repeating: [], count: LimeDB.EMOJI_CATEGORY_GROUPS.count)
        return pages.map { words in
            words.map {
                Mapping(id: 0, code: "", word: $0,
                        score: 0, baseScore: 0,
                        recordType: Mapping.RecordType.emoji)
            }
        }
    }

    func loadRecentEmoji(limit: Int = 32) -> [Mapping] {
        refreshEmojiDataIfNeeded()
        let safeLimit = max(limit, 1)
        let words: [String] = (try? dbQueue.read { db in
            try String.fetchAll(db, sql: """
                SELECT d.value
                FROM \(LimeDB.EMOJI_TABLE_USER) u
                JOIN \(LimeDB.EMOJI_TABLE_DATA) d ON d.value = u.value
                WHERE u.last_used IS NOT NULL
                ORDER BY u.last_used DESC, u.use_count DESC, d.sort_order ASC
                LIMIT ?
            """, arguments: [safeLimit])
        }) ?? []
        return words.map {
            Mapping(id: 0, code: "", word: $0,
                    score: 0, baseScore: 0,
                    recordType: Mapping.RecordType.emoji)
        }
    }

    func recordEmojiUsage(_ value: String, timestampSeconds: Int64 = Int64(Date().timeIntervalSince1970)) {
        guard !value.isEmpty else { return }
        try? dbQueue.write { db in
            try LimeDB.createEmojiTables(db, forceRecreate: false)
            try db.execute(sql: """
                UPDATE \(LimeDB.EMOJI_TABLE_USER)
                SET last_used = ?, use_count = use_count + 1
                WHERE value = ?
            """, arguments: [timestampSeconds, value])
            let updated = db.changesCount
            if updated == 0 {
                try db.execute(sql: """
                    INSERT OR IGNORE INTO \(LimeDB.EMOJI_TABLE_USER) (value, last_used, use_count)
                    VALUES (?, ?, 1)
                """, arguments: [value, timestampSeconds])
            }
        }
    }

    private func refreshEmojiDataIfNeeded() {
        do {
            try dbQueue.write { db in
                try LimeDB.createEmojiTables(db, forceRecreate: false)
            }
            let current = try dbQueue.read { db in
                try String.fetchOne(db,
                    sql: "SELECT desc FROM im WHERE code = ? AND title = ?",
                    arguments: ["emoji", "version"])
            }
            let hasIntegratedEmojiData = try dbQueue.read { db in
                try (Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(LimeDB.EMOJI_TABLE_DATA)") ?? 0) > 0
            }
            guard current != LimeDB.EMOJI_DATA_VERSION || !hasIntegratedEmojiData else { return }
            guard let url = Bundle.main.url(forResource: "emoji", withExtension: "db") else { return }
            var sourceConfig = Configuration()
            sourceConfig.readonly = true
            let sourceQueue = try DatabaseQueue(path: url.path, configuration: sourceConfig)
            let hasSourceEmojiData = try sourceQueue.read { db in try db.tableExists(LimeDB.EMOJI_TABLE_DATA) }
            guard hasSourceEmojiData else { return }
            let sourceRows = try sourceQueue.read { db in
                try Row.fetchAll(db, sql: """
                    SELECT value, cp, group_name, subgroup, sort_order, name_en, name_tw, tags_en, tags_tw, version
                    FROM \(LimeDB.EMOJI_TABLE_DATA)
                """)
            }
            let imRows = try sourceQueue.read { db in
                try Row.fetchAll(db, sql: """
                    SELECT code, title, desc, keyboard, disable, selkey, endkey, spacestyle
                    FROM im
                    WHERE code = 'emoji'
                """)
            }
            try dbQueue.write { db in
                try LimeDB.createEmojiTables(db, forceRecreate: false)
                let usageRows = try Row.fetchAll(db, sql: """
                    SELECT value, last_used, use_count
                    FROM \(LimeDB.EMOJI_TABLE_USER)
                """)
                try db.execute(sql: "DELETE FROM \(LimeDB.EMOJI_TABLE_USER)")
                try db.execute(sql: "DELETE FROM \(LimeDB.EMOJI_TABLE_DATA)")
                for row in sourceRows {
                    try db.execute(sql: """
                        INSERT INTO \(LimeDB.EMOJI_TABLE_DATA)
                        (value, cp, group_name, subgroup, sort_order, name_en, name_tw, tags_en, tags_tw, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, arguments: [
                        row["value"] as String?,
                        row["cp"] as String?,
                        row["group_name"] as String?,
                        row["subgroup"] as String?,
                        row["sort_order"] as Int? ?? 0,
                        row["name_en"] as String?,
                        row["name_tw"] as String?,
                        row["tags_en"] as String?,
                        row["tags_tw"] as String?,
                        row["version"] as Double? ?? 0.0,
                    ])
                }
                try LimeDB.rebuildEmojiFTS(db)
                try db.execute(sql: "DELETE FROM im WHERE code = 'emoji'")
                for row in imRows {
                    try db.execute(sql: """
                        INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, arguments: [
                        row["code"] as String?,
                        row["title"] as String?,
                        row["desc"] as String?,
                        row["keyboard"] as String?,
                        LimeDB.parseBoolFlag(row["disable"]) ? 1 : 0,
                        row["selkey"] as String? ?? "",
                        row["endkey"] as String? ?? "",
                        row["spacestyle"] as String? ?? "",
                    ])
                }
                for row in usageRows {
                    let value = row["value"] as String? ?? ""
                    try db.execute(sql: """
                        INSERT OR IGNORE INTO \(LimeDB.EMOJI_TABLE_USER) (value, last_used, use_count)
                        SELECT ?, ?, ?
                        WHERE EXISTS (
                            SELECT 1 FROM \(LimeDB.EMOJI_TABLE_DATA) WHERE value = ?
                        )
                    """, arguments: [
                        value,
                        row["last_used"] as Int64? ?? 0,
                        row["use_count"] as Int? ?? 0,
                        value,
                    ])
                }
            }
        } catch {
            NSLog("LimeDB emoji refresh failed: \(error)")
        }
    }

    private static func createEmojiTables(_ db: Database, forceRecreate: Bool) throws {
        if forceRecreate {
            try LimeDB.dropEmojiFTSIfPresent(db)
            try db.execute(sql: "DROP TABLE IF EXISTS \(EMOJI_TABLE_USER)")
            try db.execute(sql: "DROP TABLE IF EXISTS \(EMOJI_TABLE_DATA)")
        }
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS \(EMOJI_TABLE_DATA) (
                value TEXT PRIMARY KEY,
                cp TEXT NOT NULL,
                group_name TEXT NOT NULL,
                subgroup TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                name_en TEXT,
                name_tw TEXT,
                tags_en TEXT,
                tags_tw TEXT,
                version REAL NOT NULL
            )
        """)
        try db.execute(sql: "CREATE INDEX IF NOT EXISTS idx_emoji_group ON \(EMOJI_TABLE_DATA)(group_name, sort_order)")
        try LimeDB.dropEmojiFTSIfUnusable(db)
        if try !db.tableExists(EMOJI_TABLE_FTS) {
            do {
                try db.execute(sql: """
                    CREATE VIRTUAL TABLE \(EMOJI_TABLE_FTS) USING fts5(
                        name_en, name_tw, tags_en, tags_tw,
                        content='\(EMOJI_TABLE_DATA)', content_rowid='rowid',
                        tokenize='unicode61 remove_diacritics 1'
                    )
                """)
            } catch {
                try LimeDB.dropEmojiFTSIfPresent(db)
                try db.execute(sql: """
                    CREATE VIRTUAL TABLE \(EMOJI_TABLE_FTS) USING fts4(
                        name_en, name_tw, tags_en, tags_tw,
                        tokenize=unicode61 "remove_diacritics=1",
                        content=\(EMOJI_TABLE_DATA)
                    )
                """)
            }
        }
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS \(EMOJI_TABLE_USER) (
                value TEXT PRIMARY KEY REFERENCES \(EMOJI_TABLE_DATA)(value),
                last_used INTEGER,
                use_count INTEGER NOT NULL DEFAULT 0
            )
        """)
    }

    private static func dropEmojiFTSIfUnusable(_ db: Database) throws {
        guard try db.tableExists(EMOJI_TABLE_FTS) else { return }
        let createSQL = try String.fetchOne(db, sql: """
            SELECT sql FROM sqlite_schema
            WHERE type = 'table' AND name = ?
        """, arguments: [EMOJI_TABLE_FTS]) ?? ""
        let normalizedSQL = createSQL.lowercased()
        guard normalizedSQL.contains("virtual table") &&
              (normalizedSQL.contains("using fts4") || normalizedSQL.contains("using fts5")) else {
            try LimeDB.dropEmojiFTSIfPresent(db)
            return
        }
        do {
            _ = try Int.fetchOne(db, sql: "SELECT 1 FROM \(EMOJI_TABLE_FTS) LIMIT 1")
        } catch {
            try LimeDB.dropEmojiFTSIfPresent(db)
        }
    }

    private static func dropEmojiFTSIfPresent(_ db: Database) throws {
        do {
            try db.execute(sql: "DROP TABLE IF EXISTS \(EMOJI_TABLE_FTS)")
        } catch {
            try LimeDB.removeEmojiFTSSchemaEntries(db)
        }
    }

    private static func removeEmojiFTSSchemaEntries(_ db: Database) throws {
        let schemaVersion = try Int.fetchOne(db, sql: "PRAGMA schema_version") ?? 0
        try db.execute(sql: "PRAGMA writable_schema = ON")
        do {
            try db.execute(sql: """
                DELETE FROM sqlite_schema
                WHERE tbl_name = ?
                   OR name = ?
                   OR name LIKE ?
            """, arguments: [
                EMOJI_TABLE_FTS,
                EMOJI_TABLE_FTS,
                "\(EMOJI_TABLE_FTS)_%"
            ])
            try db.execute(sql: "PRAGMA schema_version = \(schemaVersion + 1)")
            try db.execute(sql: "PRAGMA writable_schema = OFF")
        } catch {
            try? db.execute(sql: "PRAGMA writable_schema = OFF")
            throw error
        }
    }

    private static func ensureCj4Schema(_ db: Database) throws {
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS cj4 (
                _id INTEGER primary key autoincrement,
                code text,
                code3r text,
                word text,
                related text,
                score integer,
                'basescore' type integer
            )
        """)
        try db.execute(sql: "CREATE INDEX IF NOT EXISTS cj4_idx_code ON cj4 (code)")
        try db.execute(sql: "DELETE FROM keyboard WHERE code = ?", arguments: ["cj4"])
    }

    /// feat#159: mirrors ensureCj4Schema — tricode.limedb (三碼) never imports the mapping
    /// table itself, so this seeds the empty `tricode` table (and its code index) in code
    /// on every DB open, so existing installs pick it up too.
    private static func ensureTricodeSchema(_ db: Database) throws {
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS tricode (
                _id INTEGER primary key autoincrement,
                code text,
                code3r text,
                word text,
                related text,
                score integer,
                'basescore' type integer
            )
        """)
        try db.execute(sql: "CREATE INDEX IF NOT EXISTS tricode_idx_code ON tricode (code)")
    }

    /// feat#N02: seed the computer-numpad keyboard into the global keyboard list if absent,
    /// so every IM's keyboard picker can choose it (it loads the computer_simple layout —
    /// phone_simple with 7 8 9 on top). Insert-if-absent only: this helper is the sole
    /// writer of the computernum row, so a present row is by definition correct and is left
    /// untouched (a user-chosen assignment is never overwritten). Mirrors Android's
    /// ensureCurrentDatabase() seeding.
    private static func ensureComputerNumKeyboard(_ db: Database) throws {
        let exists = (try Int.fetchOne(db,
            sql: "SELECT COUNT(*) FROM keyboard WHERE code = 'computernum'") ?? 0) > 0
        guard !exists else { return }
        try db.execute(sql: """
            INSERT INTO keyboard (code, name, desc, type, image,
                imkb, imshiftkb, engkb, engshiftkb,
                symbolkb, symbolshiftkb, defaultkb, defaultshiftkb,
                extendedkb, extendedshiftkb, disable)
            VALUES ('computernum', '電腦數字', '電腦數字鍵盤', 'phone', 'phone_simple_preview',
                'computer_simple', 'computer_simple', 'lime', 'lime_shift',
                'symbols', 'symbols_shift', '', '', '', '', 0)
        """)
    }

    private static func ensureCangjieSemicolonKeyboards(_ db: Database) throws {
        try insertKeyboardIfAbsent(db,
            code: "cj_semi",
            name: "倉頡分號",
            desc: "倉頡分號鍵盤",
            image: "cj_keyboard_preview",
            imkb: "lime_cj_semi",
            imshiftkb: "lime_cj_semi_shift",
            engkb: "lime",
            engshiftkb: "lime_shift",
            defaultkb: "lime_cj_semi",
            defaultshiftkb: "lime_cj_semi_shift",
            extendedkb: "lime_cj_number_semi",
            extendedshiftkb: "lime_cj_number_semi_shift")
        try insertKeyboardIfAbsent(db,
            code: "cj_num_semi",
            name: "倉頡數字分號",
            desc: "倉頡數字分號鍵盤",
            image: "cj_keyboard_preview",
            imkb: "lime_cj_number_semi",
            imshiftkb: "lime_cj_number_semi_shift",
            engkb: "lime_number",
            engshiftkb: "lime_number_shift",
            defaultkb: "lime_cj_number_semi",
            defaultshiftkb: "lime_cj_number_semi_shift",
            extendedkb: "",
            extendedshiftkb: "")
    }

    /// feat#159: seed the limenumsym2 keyboard (三碼/tricode) into the global keyboard list
    /// if absent. tricode.limedb never imports the keyboard table (LIMEDB_SPEC.md), so this
    /// seeds it in code, mirroring ensureComputerNumKeyboard, so it also appears on existing
    /// installs. Column values match Android's ensureLimeNumSym2Keyboard (LimeDB.java).
    private static func ensureLimeNumSym2Keyboard(_ db: Database) throws {
        try insertKeyboardIfAbsent(db,
            code: "limenumsym2",
            name: "LIMENUMSYM2",
            desc: "LIME+數字符號鍵盤2",
            image: "lime_number_symbol_keyboard_priview",
            imkb: "lime_num_sym2",
            imshiftkb: "lime_num_sym2_shift",
            engkb: "lime_english_number",
            engshiftkb: "lime_english_shift",
            defaultkb: "",
            defaultshiftkb: "",
            extendedkb: "",
            extendedshiftkb: "")
    }

    private static func insertKeyboardIfAbsent(_ db: Database,
                                               code: String,
                                               name: String,
                                               desc: String,
                                               image: String,
                                               imkb: String,
                                               imshiftkb: String,
                                               engkb: String,
                                               engshiftkb: String,
                                               defaultkb: String,
                                               defaultshiftkb: String,
                                               extendedkb: String,
                                               extendedshiftkb: String) throws {
        let exists = (try Int.fetchOne(db,
            sql: "SELECT COUNT(*) FROM keyboard WHERE code = ?",
            arguments: [code]) ?? 0) > 0
        guard !exists else { return }
        try db.execute(sql: """
            INSERT INTO keyboard (code, name, desc, type, image,
                imkb, imshiftkb, engkb, engshiftkb,
                symbolkb, symbolshiftkb, defaultkb, defaultshiftkb,
                extendedkb, extendedshiftkb, disable)
            VALUES (?, ?, ?, 'phone', ?,
                ?, ?, ?, ?,
                'symbols', 'symbols_shift', ?, ?,
                ?, ?, 0)
        """, arguments: [
            code, name, desc, image,
            imkb, imshiftkb, engkb, engshiftkb,
            defaultkb, defaultshiftkb,
            extendedkb, extendedshiftkb
        ])
    }

    /// LIME DB 105 goal 1: known standard table → default `imkeys`/`imkeynames` map.
    /// Mirrors the switch in `applyDefaultMetadataForStandardIM`, sourced from the SAME
    /// constants (prep task) so the restore-backfill default equals the import default
    /// byte-for-byte. `array10` / `custom` / `pinyin` / imported user tables are
    /// intentionally excluded — array10 has no keyname conversion by design (only
    /// ARRAY10_KEY exists, there is no ARRAY10_CHAR) and the others have no built-in map.
    private static func standardIMKeyDefaults(for table: String) -> (imkeys: String, imkeynames: String)? {
        switch table {
        case "dayi":
            return (DAYI_KEY, DAYI_CHAR)
        case "cj", "cj4", "cj5", "ecj", "scj":
            return (CJ_KEY, CJ_CHAR)
        case "array":
            return (ARRAY_IMKEYS, ARRAY_IMKEYNAMES)
        case "ez":
            return (EZ_KEY, EZ_CHAR)
        case "phonetic":
            return (BPMF_IMKEYS, BPMF_IMKEYNAMES)
        default:
            return nil
        }
    }

    private static let STANDARD_IM_TABLES = ["dayi", "cj", "cj4", "cj5", "ecj", "scj", "array", "ez", "phonetic"]

    /// LIME DB 105 goal 1: backfill `imkeys`/`imkeynames` for every known standard table
    /// that exists and has at least one mapping row, when the `im` table is missing a
    /// non-empty row for that title. Writes via the passed `db` handle ONLY — never
    /// `setImConfig`/`applyDefaultMetadataForStandardIM` (both open their own
    /// `dbQueue.write`; this runs inside `ensureCurrentDatabase()`'s write block, and
    /// GRDB `write` is non-reentrant, so a nested write would deadlock).
    /// Never overwrites an existing non-empty value — user-customised or imported
    /// values always win; this only fills the empty/absent case.
    private static func ensureStandardIMKeyMetadata(_ db: Database) throws {
        for table in STANDARD_IM_TABLES {
            try ensureStandardIMKeyMetadata(db, table: table)
        }
    }

    /// Single-table variant of `ensureStandardIMKeyMetadata(_:)` above, reused by the
    /// IM-load-time fallback (`LimeDB.ensureStandardIMKeyMetadata(forTable:)`), which opens
    /// its own write and is safe to call outside `ensureCurrentDatabase()`.
    private static func ensureStandardIMKeyMetadata(_ db: Database, table: String) throws {
        guard let defaults = standardIMKeyDefaults(for: table) else { return }
        let tableExists = (try Int.fetchOne(db,
            sql: "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
            arguments: [table]) ?? 0) > 0
        guard tableExists else { return }
        let hasMappingRows = (try Int.fetchOne(db,
            sql: "SELECT EXISTS(SELECT 1 FROM \(table))") ?? 0) > 0
        guard hasMappingRows else { return }

        try ensureImKeyMetadataRow(db, table: table, title: "imkeys", defaultValue: defaults.imkeys)
        try ensureImKeyMetadataRow(db, table: table, title: "imkeynames", defaultValue: defaults.imkeynames)
    }

    /// Writes `defaultValue` into `im(code=table, title=title)` only when no non-empty
    /// row already exists for that title (NULL/empty treated as absent). Delete-then-insert
    /// mirrors `setImConfig`'s pattern so a stray empty row left behind by an old import
    /// doesn't accumulate duplicates across repeated ensure calls.
    private static func ensureImKeyMetadataRow(_ db: Database, table: String, title: String,
                                                defaultValue: String) throws {
        let existing = try String.fetchOne(db,
            sql: "SELECT desc FROM im WHERE code = ? AND title = ?",
            arguments: [table, title])
        if let existing = existing, !existing.isEmpty { return }
        try db.execute(sql: "DELETE FROM im WHERE code = ? AND title = ?", arguments: [table, title])
        try db.execute(sql: "INSERT INTO im (code, title, desc) VALUES (?, ?, ?)",
                       arguments: [table, title, defaultValue])
    }

    private static func rebuildEmojiFTS(_ db: Database) throws {
        try db.execute(sql: "INSERT INTO \(EMOJI_TABLE_FTS)(\(EMOJI_TABLE_FTS)) VALUES ('rebuild')")
    }

    private static func buildEmojiPanelSearchQuery(_ input: String) -> String {
        sanitizedEmojiTokens(input).map { "\($0)*" }.joined(separator: " ")
    }

    private static func buildEmojiCandidateQuery(_ input: String) -> String {
        sanitizedEmojiTokens(input).flatMap { token -> [String] in
            if isSingleASCIIAlphabeticToken(token) { return [] }
            var terms = ["\(token)*"]
            if let firstCJK = firstCJKCharacter(token), token != firstCJK {
                terms.append("\(firstCJK)*")
            }
            return terms
        }.joined(separator: " OR ")
    }

    private static func sanitizedEmojiTokens(_ input: String) -> [String] {
        input.split(whereSeparator: { $0.isWhitespace }).compactMap { part in
            let tokenScalars = part.unicodeScalars.filter {
                CharacterSet.alphanumerics.contains($0) || $0.value == 95
            }
            let token = String(String.UnicodeScalarView(tokenScalars))
            return token.isEmpty ? nil : token
        }
    }

    private static func firstCJKCharacter(_ token: String) -> String? {
        guard let scalar = token.unicodeScalars.first, isCJKScalar(scalar) else { return nil }
        return String(scalar)
    }

    private static func isCJKScalar(_ scalar: Unicode.Scalar) -> Bool {
        switch scalar.value {
        case 0x3400...0x4DBF, 0x4E00...0x9FFF, 0xF900...0xFAFF,
             0x20000...0x2A6DF, 0x2A700...0x2B73F, 0x2B740...0x2B81F,
             0x2B820...0x2CEAF, 0x2CEB0...0x2EBEF, 0x30000...0x3134F:
            return true
        default:
            return false
        }
    }

    func replaceEmojiDataForTest(_ rows: [EmojiDataRow], emojiVersion: String) throws {
        try dbQueue.write { db in
            try LimeDB.createEmojiTables(db, forceRecreate: true)
            for row in rows {
                try db.execute(sql: """
                    INSERT INTO \(LimeDB.EMOJI_TABLE_DATA)
                    (value, cp, group_name, subgroup, sort_order, name_en, name_tw, tags_en, tags_tw, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, arguments: [
                    row.value, row.cp, row.groupName, row.subgroup, row.sortOrder,
                    row.nameEn, row.nameTw, row.tagsEn, row.tagsTw, row.version,
                ])
            }
            try LimeDB.rebuildEmojiFTS(db)
            try db.execute(sql: "DELETE FROM im WHERE code = 'emoji'")
            try db.execute(sql: """
                INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                VALUES ('emoji', 'version', ?, '', 0, '', '', '')
            """, arguments: [emojiVersion])
        }
    }

    func databaseVersionForTest() throws -> Int {
        try dbQueue.read { db in
            try Int.fetchOne(db, sql: "PRAGMA user_version") ?? 0
        }
    }

    func emojiDataCountForTest() throws -> Int {
        try dbQueue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(LimeDB.EMOJI_TABLE_DATA)") ?? 0
        }
    }

    func emojiImRowCountForTest() throws -> Int {
        try dbQueue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM im WHERE code = 'emoji'") ?? 0
        }
    }

    func firstEmojiValueForTest() throws -> String? {
        try dbQueue.read { db in
            try String.fetchOne(db, sql: "SELECT value FROM \(LimeDB.EMOJI_TABLE_DATA) LIMIT 1")
        }
    }

    func setEmojiVersionForTest(_ version: String) throws {
        try dbQueue.write { db in
            try db.execute(sql: "UPDATE im SET desc = ? WHERE code = 'emoji' AND title = 'version'", arguments: [version])
        }
    }

    func setEmojiUserForTest(value: String, useCount: Int, lastUsed: Int64) throws {
        var config = Configuration()
        config.foreignKeysEnabled = false
        let rawQueue = try DatabaseQueue(path: databasePath, configuration: config)
        try rawQueue.write { db in
            try LimeDB.createEmojiTables(db, forceRecreate: false)
            try db.execute(sql: """
                INSERT OR REPLACE INTO \(LimeDB.EMOJI_TABLE_USER) (value, last_used, use_count)
                VALUES (?, ?, ?)
            """, arguments: [value, lastUsed, useCount])
        }
    }

    func emojiUserCountForTest(value: String) throws -> Int {
        try dbQueue.read { db in
            try Int.fetchOne(db,
                sql: "SELECT use_count FROM \(LimeDB.EMOJI_TABLE_USER) WHERE value = ?",
                arguments: [value]) ?? 0
        }
    }

    static func buildEmojiPanelSearchQueryForTest(_ input: String) -> String {
        return buildEmojiPanelSearchQuery(input)
    }

    static func buildEmojiCandidateQueryForTest(_ input: String) -> String {
        return buildEmojiCandidateQuery(input)
    }

    private static func isSingleASCIIAlphabeticToken(_ token: String) -> Bool {
        guard token.unicodeScalars.count == 1, let scalar = token.unicodeScalars.first else {
            return false
        }
        return (65...90).contains(Int(scalar.value)) || (97...122).contains(Int(scalar.value))
    }

    /// TC↔SC conversion using iOS CFStringTransform (replaces Android hanconvertv2.db).
    /// hanOption: 0 = no conversion, 1 = Traditional→Simplified, 2 = Simplified→Traditional.
    func hanConvert(_ input: String, _ hanOption: Int) -> String {
        guard !input.isEmpty, hanOption != 0 else { return input }
        let mutable = NSMutableString(string: input)
        if hanOption == 1 {
            CFStringTransform(mutable, nil, "Hant-Hans" as CFString, false)
        } else {
            CFStringTransform(mutable, nil, "Hans-Hant" as CFString, false)
        }
        return mutable as String
    }

    /// Read-only connection to the bundled `hanconvertv2.db` frequency table.
    /// Opened lazily on first `getBaseScore` call (import time only) so LimeDB
    /// instances that never import pay nothing. `nil` if the resource is absent.
    // ponytail: lazy is not thread-safe, but imports run on a single serialized
    // thread (importThread / importTxtFileAsync), so getBaseScore is not called
    // concurrently. Add a lock only if a concurrent-import path is ever introduced.
    private lazy var hanScoreQueue: DatabaseQueue? = {
        guard let url = Bundle.main.url(forResource: "hanconvertv2", withExtension: "db") else { return nil }
        var config = Configuration()
        config.readonly = true
        return try? DatabaseQueue(path: url.path, configuration: config)
    }()

    /// Base frequency score for a word, mirroring Android `LimeHanConverter.getBaseScore()`:
    /// look up `TCSC.score` by exact code; on a miss, a phrase (length > 1) defaults to `1`
    /// and a single character to `0`. Sourced from the bundled `hanconvertv2.db` (the same
    /// Android raw asset) so scoreless `.cin`/`.lime` imports seed runtime phrase composition
    /// instead of dead-ending at score 0. Explicit positive imported basescores are preserved
    /// by the importer, which only calls this when basescore is missing or 0.
    func getBaseScore(_ input: String) -> Int {
        guard !input.isEmpty else { return 0 }
        if let queue = hanScoreQueue {
            let found: Int?? = try? queue.read { db in
                try Int.fetchOne(db, sql: "SELECT score FROM TCSC WHERE code = ?", arguments: [input])
            }
            if let score = found ?? nil { return score }
        }
        return input.utf16.count > 1 ? 1 : 0
    }

    // MARK: - Rename Table

    func renameTableName(_ source: String, _ target: String) {
        guard !checkDBConnection() else { return }
        try? dbQueue.write { db in
            try db.execute(sql: "ALTER TABLE \(source) RENAME TO \(target)")
        }
    }

    // MARK: - Backup via ATTACH DATABASE

    func prepareBackup(targetFile: URL, tableNames: [String], includeRelated: Bool) {
        guard !checkDBConnection() else { return }
        guard FileManager.default.createFile(atPath: targetFile.path, contents: nil) || true else { return }
        holdDBConnection()
        defer { unHoldDBConnection() }
        let path = targetFile.path.replacingOccurrences(of: "'", with: "''")
        try? dbQueue.write { db in
            try db.execute(sql: "ATTACH DATABASE '\(path)' AS sourceDB")
            defer { try? db.execute(sql: "DETACH DATABASE sourceDB") }
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS sourceDB.custom (
                    _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    code      TEXT,
                    code3r    TEXT,
                    word      TEXT,
                    related   TEXT,
                    score     INTEGER DEFAULT 0,
                    basescore INTEGER DEFAULT 0
                )
            """)
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS sourceDB.im (
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
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS sourceDB.related (
                    _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    pword     TEXT,
                    cword     TEXT,
                    basescore INTEGER DEFAULT 0,
                    score     INTEGER DEFAULT 0
                )
            """)
            for t in tableNames where isValidTableName(t) {
                let cols = try Row.fetchAll(db, sql: "PRAGMA table_info(\(t))").map {
                    $0["name"] as String? ?? ""
                }
                guard cols.contains("code"), cols.contains("word") else { continue }
                let scoreExpr = cols.contains("score") ? "COALESCE(score, 0)" : "0"
                let baseScoreExpr = cols.contains("basescore") ? "COALESCE(basescore, 0)" : "0"
                let code3rExpr = cols.contains("code3r") ? "code3r" : "NULL"
                let relatedExpr = cols.contains("related") ? "related" : "NULL"
                try db.execute(sql: """
                    INSERT INTO sourceDB.custom (code, code3r, word, related, score, basescore)
                    SELECT code, \(code3rExpr), word, \(relatedExpr), \(scoreExpr), \(baseScoreExpr)
                    FROM \(t)
                    WHERE code IS NOT NULL AND word IS NOT NULL
                """)
            }
            for t in tableNames where isValidTableName(t) {
                try db.execute(sql: """
                    INSERT INTO sourceDB.im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                    SELECT code, title, desc, keyboard, disable, selkey, endkey, spacestyle
                    FROM im
                    WHERE code = ?
                """, arguments: [t])
            }
            if includeRelated {
                try db.execute(sql: """
                    INSERT INTO sourceDB.related (pword, cword, basescore, score)
                    SELECT pword, cword, COALESCE(basescore, 0), COALESCE(score, 0)
                    FROM related
                """)
            }
        }
    }

    func prepareBackupDb(_ sourcedbfile: String, _ sourcetable: String) {
        prepareBackup(targetFile: URL(fileURLWithPath: sourcedbfile),
                      tableNames: [sourcetable], includeRelated: false)
    }

    func prepareBackupRelatedDb(_ sourcedbfile: String) {
        prepareBackup(targetFile: URL(fileURLWithPath: sourcedbfile),
                      tableNames: [], includeRelated: true)
    }

    private func tableColumns(_ db: Database, schemaName: String? = nil, tableName: String) throws -> Set<String> {
        let sql: String
        if let schemaName {
            sql = "PRAGMA \(schemaName).table_info(\(tableName))"
        } else {
            sql = "PRAGMA table_info(\(tableName))"
        }
        let rows = try Row.fetchAll(db, sql: sql)
        return Set(rows.map { $0["name"] as String? ?? "" })
    }

    private func importMappingRowsFromAttachedSource(_ db: Database,
                                                     targetTable: String,
                                                     sourceTable: String) throws {
        let targetCols = try tableColumns(db, tableName: targetTable)
        let sourceCols = try tableColumns(db, schemaName: "sourceDB", tableName: sourceTable)
        guard targetCols.contains("code"),
              targetCols.contains("word"),
              sourceCols.contains("code"),
              sourceCols.contains("word") else { return }

        var insertCols: [String] = ["code", "word"]
        var selectExprs: [String] = ["code", "word"]
        if targetCols.contains("score") {
            insertCols.append("score")
            selectExprs.append(sourceCols.contains("score") ? "COALESCE(score, 0)" : "0")
        }
        if targetCols.contains("basescore") {
            insertCols.append("basescore")
            selectExprs.append(sourceCols.contains("basescore") ? "COALESCE(basescore, 0)" : "0")
        }
        if targetCols.contains("code3r") && sourceCols.contains("code3r") {
            insertCols.append("code3r")
            selectExprs.append("code3r")
        }
        if targetCols.contains("related") {
            insertCols.append("related")
            selectExprs.append(sourceCols.contains("related") ? "related" : "NULL")
        }

        try db.execute(sql: """
            INSERT INTO \(targetTable) (\(insertCols.joined(separator: ", ")))
            SELECT \(selectExprs.joined(separator: ", "))
            FROM sourceDB.\(sourceTable)
            WHERE code IS NOT NULL AND word IS NOT NULL
        """)
    }

    func importDb(sourceFile: URL, tableNames: [String], overwriteExisting: Bool, includeRelated: Bool) {
        guard !checkDBConnection() else { return }
        guard FileManager.default.fileExists(atPath: sourceFile.path) else { return }
        let valid = tableNames.filter { isValidTableName($0) }
        if valid.isEmpty && !includeRelated { return }
        if overwriteExisting {
            for t in valid { clearTable(t) }
            if includeRelated { clearTable("related") }
        }
        holdDBConnection()
        defer { unHoldDBConnection() }
        let path = sourceFile.path.replacingOccurrences(of: "'", with: "''")
        try? dbQueue.write { db in
            try db.execute(sql: "ATTACH DATABASE '\(path)' AS sourceDB")
            defer { try? db.execute(sql: "DETACH DATABASE sourceDB") }
            for t in valid {
                // Try backup format (custom table) first, fall back to direct
                let hasCustom = (try? Row.fetchOne(db,
                    sql: "SELECT name FROM sourceDB.sqlite_master WHERE type='table' AND name='custom'")) != nil
                if hasCustom {
                    try? importMappingRowsFromAttachedSource(db, targetTable: t, sourceTable: "custom")
                } else {
                    try? importMappingRowsFromAttachedSource(db, targetTable: t, sourceTable: t)
                }
            }
            let hasImTable = ((try? Int.fetchOne(db,
                sql: "SELECT COUNT(*) FROM sourceDB.sqlite_master WHERE type='table' AND name='im'")) ?? 0) > 0
            if hasImTable && !valid.isEmpty {
                if valid.count == 1, let tableName = valid.first {
                    try? db.execute(sql: "DELETE FROM im WHERE code = ?", arguments: [tableName])
                    try? db.execute(sql: """
                        INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                        SELECT ?, title, desc, keyboard, disable, selkey, endkey, spacestyle
                        FROM sourceDB.im
                    """, arguments: [tableName])
                } else {
                    try? db.execute(sql: """
                        DELETE FROM im
                        WHERE code IN (SELECT code FROM sourceDB.im)
                    """)
                    try? db.execute(sql: """
                        INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                        SELECT code, title, desc, keyboard, disable, selkey, endkey, spacestyle
                        FROM sourceDB.im
                    """)
                }
            }
            if includeRelated {
                let hasRelatedTable = ((try? Int.fetchOne(db,
                    sql: "SELECT COUNT(*) FROM sourceDB.sqlite_master WHERE type='table' AND name='related'")) ?? 0) > 0
                if hasRelatedTable {
                    try? db.execute(sql: """
                        INSERT INTO related (pword, cword, basescore, score)
                        SELECT pword, cword, COALESCE(basescore, 0), COALESCE(score, 0)
                        FROM sourceDB.related
                    """)
                }
            }
        }
    }

    func importDbRelated(_ sourceFile: URL) {
        importDb(sourceFile: sourceFile, tableNames: [], overwriteExisting: true, includeRelated: true)
    }

    // MARK: - Ensure mapping table exists

    /// Creates the standard (code, word, score, basescore, code3r) mapping table if missing.
    /// The bundled lime.db ships with all mapping tables as empty shells, but a freshly
    /// created App Group DB only has im/related/keyboard/custom from migrate(), so an
    /// import targeting e.g. "dayi" hits "no such table: dayi" on the leading DELETE/INSERT.
    /// Also covers per-IM `_user` tables (same schema).
    private func ensureMappingTable(_ tableName: String) throws {
        guard isValidTableName(tableName) else {
            throw LimeDBError.invalidTableName(tableName)
        }
        try dbQueue.write { db in
            try ensureMappingTable(tableName, db: db)
        }
    }

    private func ensureMappingTable(_ tableName: String, db: Database) throws {
        guard isValidTableName(tableName) else {
            throw LimeDBError.invalidTableName(tableName)
        }
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS \(tableName) (
                _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                code      TEXT,
                word      TEXT,
                score     INTEGER DEFAULT 0,
                basescore INTEGER DEFAULT 0,
                code3r    TEXT
            )
        """)
    }

    // MARK: - Import: separate-connection copy (was ATTACH DATABASE)

    func importFromAttachedDB(sourcePath: String, tableName: String) throws {
        guard isValidTableName(tableName) else { throw LimeDBError.invalidTableName(tableName) }
        try ensureMappingTable(tableName)
        // Open source as a separate read-only DatabaseQueue instead of using ATTACH DATABASE.
        // GRDB caches compiled statements that reference "src.*"; SQLite refuses DETACH while
        // those statements are live (even after execution), producing "database src is locked".
        // A separate connection avoids the issue entirely.
        var srcConfig = Configuration()
        srcConfig.readonly = true
        let srcQueue = try DatabaseQueue(path: sourcePath, configuration: srcConfig)

        // Mirror Android importDb: cloud .limedb files store data in a "custom" table
        // (backup/export format). Fall back to tableName when custom is absent.
        let srcTableName: String = try srcQueue.read { db in
            try db.tableExists("custom") ? "custom" : tableName
        }

        // Detect available columns in the source table
        let srcCols = try srcQueue.read { db in
            try Row.fetchAll(db, sql: "PRAGMA table_info(\(srcTableName))").map { $0["name"] as String? ?? "" }
        }
        let hasCode3r    = srcCols.contains("code3r")
        let hasBasescore = srcCols.contains("basescore")

        var selCols = ["code", "word", "COALESCE(score, 0) AS score"]
        var insCols = ["code", "word", "score"]
        if hasBasescore { selCols.append("COALESCE(basescore, 0) AS basescore"); insCols.append("basescore") }
        if hasCode3r   { selCols.append("code3r");  insCols.append("code3r") }

        // Fetch source rows into memory (array10 ≈ 32 K rows, ~1–2 MB — acceptable)
        let srcRows = try srcQueue.read { db in
            try Row.fetchAll(db, sql: """
                SELECT \(selCols.joined(separator: ", "))
                FROM \(srcTableName)
                WHERE code IS NOT NULL AND word IS NOT NULL
            """)
        }

        // Also merge the cloud DB's `im` table kv rows for this IM, when present.
        // This is the authoritative source of the IM↔keyboard mapping (e.g. Pinyin's
        // `keyboard=limenum` row). Mirrors Android `LimeDB.importDb`'s
        // `INSERT INTO im SELECT … FROM sourceDB.im` step. Skipped silently if the
        // source DB doesn't ship an `im` table (e.g. user-supplied raw SQLite files).
        let srcHasImTable = (try? srcQueue.read { db in
            (try? Int.fetchOne(db,
                sql: "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='im'")) ?? 0
        }) ?? 0 > 0
        var imRows: [Row] = []
        if srcHasImTable {
            imRows = (try? srcQueue.read { db in
                try Row.fetchAll(db, sql: """
                    SELECT code, title, desc, keyboard, disable, selkey, endkey, spacestyle
                    FROM im
                    WHERE code = ?
                """, arguments: [tableName])
            }) ?? []
        }

        // Write into the main DB in a single transaction
        try dbQueue.write { db in
            try db.execute(sql: "DELETE FROM \(tableName)")
            let placeholders = insCols.map { _ in "?" }.joined(separator: ", ")
            let insertSQL = "INSERT INTO \(tableName) (\(insCols.joined(separator: ", "))) VALUES (\(placeholders))"
            for row in srcRows {
                var args: [DatabaseValueConvertible?] = [
                    row["code"] as String?,
                    row["word"] as String?,
                    row["score"] as Int? ?? 0
                ]
                if hasBasescore { args.append(row["basescore"] as Int? ?? 0) }
                if hasCode3r   { args.append(row["code3r"] as String?) }
                try db.execute(sql: insertSQL, arguments: StatementArguments(args))
            }
            // Merge im kv rows: DELETE-then-INSERT, omit `_id` so SQLite assigns fresh ids
            // (matches the explicit-column form proposed for Android).
            if !imRows.isEmpty {
                try db.execute(sql: "DELETE FROM im WHERE code = ?", arguments: [tableName])
                for row in imRows {
                    try db.execute(sql: """
                        INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, arguments: [
                        row["code"]       as String?,
                        row["title"]      as String?,
                        row["desc"]       as String?,
                        row["keyboard"]   as String?,
                        row["disable"]    as Int? ?? 0,
                        row["selkey"]     as String? ?? "",
                        row["endkey"]     as String? ?? "",
                        row["spacestyle"] as String? ?? "",
                    ])
                }
            }
            try db.execute(sql: """
                INSERT INTO im (code, title, desc)
                SELECT ?, 'name', ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM im WHERE code = ? AND title = 'name'
                )
            """, arguments: [
                tableName,
                defaultImFullName(tableName, fallback: tableName),
                tableName,
            ])
        }
    }

    // MARK: - Export / Import Text

    func exportDB(to destPath: String) throws {
        let destURL = URL(fileURLWithPath: destPath)
        if FileManager.default.fileExists(atPath: destPath) {
            try FileManager.default.removeItem(at: destURL)
        }
        try dbQueue.writeWithoutTransaction { db in
            try db.execute(sql: "VACUUM INTO ?", arguments: [destPath])
        }
    }

    func dbPath() -> String { dbQueue.path }

    @discardableResult
    func exportTxtTable(_ table: String, targetFile: URL, imConfig: [LimeImConfigRow]? = nil) -> Bool {
        guard !checkDBConnection() else { return false }
        let isRelated = table == "related"
        if !isRelated && !isValidTableName(table) { return false }
        if FileManager.default.fileExists(atPath: targetFile.path) {
            try? FileManager.default.removeItem(at: targetFile)
        }
        var lines: [String] = []
        if isRelated {
            let related = getRelated(nil, 0, 0)
            let useEscapedFormat = related.contains {
                needsEscaping($0.parentWord, delimiter: "|") || needsEscaping($0.childWord, delimiter: "|")
            }
            if useEscapedFormat { lines.append("@format@|lime-text-v2") }
            lines.append("%chardef begin")
            for r in related {
                guard !r.parentWord.isEmpty, !r.childWord.isEmpty else { continue }
                let pword = useEscapedFormat ? escapeField(r.parentWord, delimiter: "|") : r.parentWord
                let cword = useEscapedFormat ? escapeField(r.childWord, delimiter: "|") : r.childWord
                lines.append("\(pword)|\(cword)|\(r.baseScore)|\(r.score)")
            }
            lines.append("%chardef end")
        } else {
            let records = getRecordList(table, nil, searchByCode: false, 0, 0)
            var useEscapedFormat = records.contains {
                needsEscaping($0.code, delimiter: "|", codeField: true) || needsEscaping($0.word, delimiter: "|")
            }
            let configs = imConfig ?? getImConfigList(table, nil)
            if !configs.isEmpty {
                var version = ""
                var name = ""
                var selkey = ""
                var endkey = ""
                var limeendkey = ""
                var spacestyle = ""
                var imkeys = ""
                var imkeynames = ""
                for c in configs {
                    if c.title == "version" { version = c.desc }
                    if c.title == "name" { name = c.desc }
                    if c.title == "selkey" { selkey = c.desc }
                    if c.title == "endkey" { endkey = c.desc }
                    if c.title == "limeendkey" { limeendkey = c.desc }
                    if c.title == "spacestyle" { spacestyle = c.desc }
                    if c.title == "imkeys" { imkeys = c.desc }
                    if c.title == "imkeynames" { imkeynames = c.desc }
                }
                if needsEscaping(version, delimiter: "|")
                    || needsEscaping(name, delimiter: "|")
                    || needsEscaping(selkey, delimiter: "|")
                    || needsEscaping(endkey, delimiter: "|")
                    || needsEscaping(limeendkey, delimiter: "|")
                    || needsEscaping(spacestyle, delimiter: "|")
                    || needsEscaping(imkeys, delimiter: "|")
                    || needsEscaping(imkeynames, delimiter: "|") {
                    useEscapedFormat = true
                }
                if useEscapedFormat { lines.append("@format@|lime-text-v2") }
                let exportVersion = version.isEmpty ? name : version
                if !exportVersion.isEmpty { lines.append("@version@|\(useEscapedFormat ? escapeField(exportVersion, delimiter: "|") : exportVersion)") }
                if !name.isEmpty { lines.append("@cname@|\(useEscapedFormat ? escapeField(name, delimiter: "|") : name)") }
                if !selkey.isEmpty { lines.append("@selkey@|\(useEscapedFormat ? escapeField(selkey, delimiter: "|") : selkey)") }
                if !endkey.isEmpty { lines.append("@endkey@|\(useEscapedFormat ? escapeField(endkey, delimiter: "|") : endkey)") }
                if !limeendkey.isEmpty { lines.append("@limeendkey@|\(useEscapedFormat ? escapeField(limeendkey, delimiter: "|") : limeendkey)") }
                if !spacestyle.isEmpty { lines.append("@spacestyle@|\(useEscapedFormat ? escapeField(spacestyle, delimiter: "|") : spacestyle)") }
                if !imkeys.isEmpty { lines.append("@imkeys@|\(useEscapedFormat ? escapeField(imkeys, delimiter: "|") : imkeys)") }
                if !imkeynames.isEmpty { lines.append("@imkeynames@|\(useEscapedFormat ? escapeField(imkeynames, delimiter: "|") : imkeynames)") }
            } else if useEscapedFormat {
                lines.append("@format@|lime-text-v2")
            }
            lines.append("%chardef begin")
            for r in records {
                guard !(r.word.isEmpty || r.word == "null") else { continue }
                let code = useEscapedFormat ? escapeField(r.code, delimiter: "|") : r.code
                let word = useEscapedFormat ? escapeField(r.word, delimiter: "|") : r.word
                lines.append("\(code)|\(word)|\(r.score)|\(r.baseScore)")
            }
            lines.append("%chardef end")
        }
        let content = lines.joined(separator: "\n")
        do {
            try content.write(to: targetFile, atomically: true, encoding: .utf8)
            return true
        } catch { return false }
    }

    // MARK: - Import cancellation flag (mirrors Android LimeDB.threadAborted)
    var importCancelled: Bool = false

    func importTxtFile(at path: String, tableName: String,
                       progress: ((Int) -> Void)? = nil) throws {
        guard isValidTableName(tableName) else { throw LimeDBError.invalidTableName(tableName) }
        let isRelatedTable = tableName == "related"
        if !isRelatedTable { try ensureMappingTable(tableName) }
        // Mirror Android importTxtTable(): clear prior rows and IM config before
        // import so re-importing the same file replaces rather than duplicates.
        try dbQueue.write { db in try db.execute(sql: "DELETE FROM \(tableName)") }
        resetImConfig(tableName)
        guard let reader = StreamReader(path: path) else { throw LimeDBError.fileNotFound(path) }
        importCancelled = false

        let sourceName = URL(fileURLWithPath: path).lastPathComponent
        let isCinFormat = sourceName.lowercased().hasSuffix(".cin")
        var version = ""
        var name = ""
        var selkey = ""
        var endkey = ""
        var limeendkey = ""
        var spacestyle = ""
        var imkeys = ""
        var imkeynamesHeader = ""
        var imkeynames: [String] = []
        var escapedFormat = false

        // Auto-detect delimiter from the first non-comment data line (mirrors Java identifyDelimiter())
        var detectedDelimiter: Character = "\t"
        var inChardef = false
        var inKeyname = false
        var delimiterDetected = false

        var batch: [(code: String, word: String, score: Int, baseScore: Int)] = []
        var relatedBatch: [(pword: String, cword: String, baseScore: Int, userScore: Int)] = []
        let batchSize = 500
        var totalInserted = 0
        var strippedBOM = false
        for line in reader {
            guard !importCancelled else { break }
            var raw = line
            if !strippedBOM {
                if raw.hasPrefix("\u{FEFF}") { raw.removeFirst() }
                strippedBOM = true
            }
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            let lowerTrim = trimmed.lowercased()
            if lowerTrim.hasPrefix("%chardef") {
                if lowerTrim.hasSuffix("begin") { inChardef = true }
                else if lowerTrim.hasSuffix("end") { inChardef = false }
                continue
            }
            if lowerTrim.hasPrefix("%keyname") {
                if lowerTrim.hasSuffix("begin") { inKeyname = true }
                else if lowerTrim.hasSuffix("end") { inKeyname = false }
                continue
            }

            if trimmed.hasPrefix("@") {
                let parts = splitLimeMetadataFields(trimmed, escapedFormat: escapedFormat)
                if parts.count >= 2 {
                    let key = parts[0].trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                    let value = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
                    switch key {
                    case "@format@":
                        escapedFormat = value.lowercased() == "lime-text-v2"
                    case "@version@":
                        version = value
                    case "@cname@":
                        name = value
                        if version.isEmpty { version = value }
                    case "@selkey@":
                        selkey = value
                    case "@endkey@":
                        endkey = value
                    case "@limeendkey@":
                        limeendkey = value
                    case "@spacestyle@":
                        spacestyle = value
                    case "@imkeys@":
                        imkeys = value
                    case "@imkeynames@":
                        imkeynamesHeader = value
                    default:
                        break
                    }
                }
                continue
            }

            if let meta = parseMetadataLine(trimmed, delimiter: delimiterDetected ? detectedDelimiter : nil) {
                switch meta.key {
                case "version":
                    version = meta.value
                case "cname":
                    name = meta.value
                    if version.isEmpty { version = meta.value }
                case "name":
                    name = meta.value
                case "selkey":
                    selkey = meta.value
                case "endkey":
                    endkey = meta.value
                case "limeendkey":
                    limeendkey = meta.value
                case "spacestyle":
                    spacestyle = meta.value
                default:
                    break
                }
                continue
            }
            // Skip .cin comment lines starting with '#' (e.g. "# Begin" inside the
            // %chardef begin/end block). Without this, comments would be imported
            // as mappings where code="#" and word="Begin"/"End"/etc.
            if trimmed.hasPrefix("#") || trimmed.isEmpty { continue }
            if isCinFormat && !(inChardef || inKeyname) { continue }
            if !isCinFormat && trimmed.hasPrefix("%") { continue }
            if isCinFormat && trimmed.hasPrefix("%") { continue }

            // Detect delimiter from first data line
            if !delimiterDetected {
                detectedDelimiter = identifyDelimiter(trimmed)
                delimiterDetected = true
            }

            var dataLine = trimmed
            if !isCinFormat && detectedDelimiter == " " {
                for run in ["     ", "    ", "   ", "  "] {
                    dataLine = dataLine.replacingOccurrences(of: run, with: " ")
                }
            }
            let parseLine = isCinFormat
                ? dataLine.replacingOccurrences(of: "[ \t]+", with: "\t", options: .regularExpression)
                : dataLine
            let parts = splitEscapedFields(parseLine,
                                           delimiter: isCinFormat ? "\t" : detectedDelimiter,
                                           escapedFormat: escapedFormat)
            if isRelatedTable {
                if parts.count >= 4 {
                    let pword = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
                    let cword = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
                    if !pword.isEmpty && !cword.isEmpty {
                        relatedBatch.append((pword: pword,
                                             cword: cword,
                                             baseScore: Int(parts[2].trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
                                             userScore: Int(parts[3].trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0))
                    }
                } else if parts.count == 3 {
                    let combined = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
                    if combined.count > 1 {
                        let splitIndex = combined.index(after: combined.startIndex)
                        relatedBatch.append((pword: String(combined[..<splitIndex]),
                                             cword: String(combined[splitIndex...]),
                                             baseScore: Int(parts[1].trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
                                             userScore: Int(parts[2].trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0))
                    }
                }
                if relatedBatch.count >= batchSize {
                    totalInserted += try flushRelatedBatch(&relatedBatch)
                    progress?(totalInserted)
                }
                continue
            }

            if parts.count >= 2 {
                let code = parts[0].trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                let word = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
                if inKeyname {
                    imkeys += code
                    if !word.isEmpty { imkeynames.append(word) }
                } else if !code.isEmpty && !word.isEmpty {
                    let score = parts.count > 2 ? (Int(parts[2].trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0) : 0
                    // basescore is authoritative when the field is PRESENT — an explicit `0`
                    // means "low base priority" and is preserved. Fill from the frequency table
                    // only when the field is ABSENT (a `.cin` `code word` line or a `.lime`
                    // `code|word`), i.e. the record never carried a base score. See CIN_LIME_SPEC §2.5.1.
                    let baseScore = parts.count > 3
                        ? (Int(parts[3].trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0)
                        : getBaseScore(word)
                    batch.append((code: code, word: word, score: score, baseScore: baseScore))
                }
            }
            if batch.count >= batchSize {
                totalInserted += try flushBatch(&batch, tableName: tableName)
                progress?(totalInserted)
            }
        }
        if !relatedBatch.isEmpty && !importCancelled {
            totalInserted += try flushRelatedBatch(&relatedBatch)
            progress?(totalInserted)
        }
        if !batch.isEmpty && !importCancelled {
            totalInserted += try flushBatch(&batch, tableName: tableName)
            progress?(totalInserted)
        }
        if !importCancelled {
            setImConfig(tableName, "source", sourceName)
            setImConfig(tableName, "version", version.isEmpty ? sourceName : version)
            setImConfig(tableName, "name", name.isEmpty ? defaultImFullName(tableName, fallback: sourceName) : name)
            setImConfig(tableName, "amount", String(totalInserted))
            setImConfig(tableName, "import", Date().description)
            if !selkey.isEmpty { setImConfig(tableName, "selkey", selkey) }
            if !endkey.isEmpty { setImConfig(tableName, "endkey", endkey) }
            if !limeendkey.isEmpty { setImConfig(tableName, "limeendkey", limeendkey) }
            if !spacestyle.isEmpty { setImConfig(tableName, "spacestyle", spacestyle) }

            let hasImportedImkeys = !imkeys.isEmpty
            let hasImportedImkeynames = !imkeynamesHeader.isEmpty || !imkeynames.isEmpty

            if !imkeys.isEmpty { setImConfig(tableName, "imkeys", imkeys) }
            if !imkeynamesHeader.isEmpty { setImConfig(tableName, "imkeynames", imkeynamesHeader) }
            else if !imkeynames.isEmpty { setImConfig(tableName, "imkeynames", imkeynames.joined(separator: "|")) }
            applyDefaultMetadataForStandardIM(tableName,
                                              hasSelkey: !selkey.isEmpty,
                                              hasEndkey: !endkey.isEmpty,
                                              hasImportedImkeys: hasImportedImkeys,
                                              hasImportedImkeynames: hasImportedImkeynames)
            applyDefaultKeyboardForImportedIM(tableName)
        }
    }

    private func defaultImFullName(_ tableName: String, fallback: String) -> String {
        if let index = Self.IM_CODES.firstIndex(of: tableName), index < Self.IM_FULL_NAMES.count {
            return Self.IM_FULL_NAMES[index]
        }
        return fallback
    }

    /// Fill default metadata by destination table, not by the imported filename.
    /// Imported metadata/keyname blocks always win. Defaults only fill missing fields.
    private func applyDefaultMetadataForStandardIM(_ tableName: String,
                                                    hasSelkey: Bool,
                                                    hasEndkey: Bool,
                                                    hasImportedImkeys: Bool,
                                                    hasImportedImkeynames: Bool) {
        switch tableName {
        case "array":
            if !hasSelkey { setImConfig(tableName, "selkey", "1234567890") }
            if !hasImportedImkeys {
                setImConfig(tableName, "imkeys", LimeDB.ARRAY_IMKEYS)
            }
            if !hasImportedImkeynames {
                setImConfig(tableName, "imkeynames", LimeDB.ARRAY_IMKEYNAMES)
            }
        case "phonetic":
            if !hasSelkey { setImConfig(tableName, "selkey", "123456789") }
            if !hasEndkey { setImConfig(tableName, "endkey", "3467'[]\\=<>?:\"{}|~!@#$%^&*()_+") }
            if !hasImportedImkeys {
                setImConfig(tableName, "imkeys", LimeDB.BPMF_IMKEYS)
            }
            if !hasImportedImkeynames {
                setImConfig(tableName, "imkeynames", LimeDB.BPMF_IMKEYNAMES)
            }
        case "cj", "cj4", "cj5", "ecj", "scj":
            // Cangjie family (倉頡) shares one canonical key layout (matches lime_cj.xml).
            // Reuse CJ_KEY/CJ_CHAR so the labels stay in one place. Selkey is left to the
            // imported file / existing config.
            if !hasImportedImkeys {
                setImConfig(tableName, "imkeys", LimeDB.CJ_KEY)
            }
            if !hasImportedImkeynames {
                setImConfig(tableName, "imkeynames", LimeDB.CJ_CHAR)
            }
        case "dayi":
            // Dayi (大易). Reuse DAYI_KEY/DAYI_CHAR (the same key→radical map the app
            // already uses to render dayi keynames). Selkey is left to the imported
            // file / existing config.
            if !hasImportedImkeys {
                setImConfig(tableName, "imkeys", LimeDB.DAYI_KEY)
            }
            if !hasImportedImkeynames {
                setImConfig(tableName, "imkeynames", LimeDB.DAYI_CHAR)
            }
        case "ez":
            // EZ (輕鬆輸入法). Reuse EZ_KEY/EZ_CHAR (extracted from ez.limedb).
            // Selkey is left to the imported file / existing config.
            if !hasImportedImkeys {
                setImConfig(tableName, "imkeys", LimeDB.EZ_KEY)
            }
            if !hasImportedImkeynames {
                setImConfig(tableName, "imkeynames", LimeDB.EZ_CHAR)
            }
        default:
            break
        }
    }

    /// Returns the default soft-keyboard code for a text-imported IM table.
    /// Keep this in sync with Android LimeDB.getDefaultKeyboardCodeForImportedIM().
    func defaultKeyboardCodeForImportedIM(_ tableName: String) -> String {
        switch tableName {
        case "phonetic":
            return "phonetic"
        case "dayi":
            return "dayisym"
        case "cj", "cj4", "cj5", "ecj", "scj":
            return "cjnum"
        case "array":
            return "arraynum"
        case "array10":
            return "phonenum"
        case "wb":
            return "wb"
        case "hs":
            return "hs"
        case "ez":
            return "ez"
        case "pinyin", "custom":
            // fix#177: Android groups DB_TABLE_CUSTOM with DB_TABLE_PINYIN here.
            // Without the "custom" case an imported custom table fell through to
            // "lime", whose imkb is "lime" — a layout the iOS keyboard extension
            // does not ship — so layout resolution dead-ended and the previously
            // active IM's keyboard stayed on screen.
            return "limenum"
        case "tricode":
            return "limenumsym2"
        default:
            return "lime"
        }
    }

    private func applyDefaultKeyboardForImportedIM(_ tableName: String) {
        let keyboardCode = defaultKeyboardCodeForImportedIM(tableName)
        let desc = getKeyboardConfig(keyboardCode)?.desc ?? keyboardCode
        setIMConfigKeyboard(tableName, desc, keyboardCode)
    }

    /// Auto-detect field delimiter from a data line (mirrors Java identifyDelimiter()).
    /// Checks `|`, `\t`, `,`, space in that priority order.
    private func identifyDelimiter(_ line: String) -> Character {
        if line.contains("|") { return "|" }
        if line.contains("\t") { return "\t" }
        if line.contains(",") { return "," }
        return " "
    }

    private func parseMetadataLine(_ trimmed: String, delimiter: Character?) -> (key: String, value: String)? {
        if trimmed.hasPrefix("@") {
            let delimiterString = String(delimiter ?? (trimmed.contains("|") ? "|" : "\t"))
            let parts = trimmed.components(separatedBy: delimiterString)
            guard parts.count >= 2 else { return nil }
            let key = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "@"))
                .lowercased()
            let value = parts.dropFirst().joined(separator: delimiterString)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            return value.isEmpty ? nil : (key, value)
        }

        let lower = trimmed.lowercased()
        for prefix in ["%version", "%cname", "%selkey", "%endkey", "%limeendkey", "%spacestyle"] where lower.hasPrefix(prefix) {
            let rawValue = String(trimmed.dropFirst(prefix.count))
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let key = String(prefix.dropFirst())
            return rawValue.isEmpty ? nil : (key, rawValue)
        }

        return nil
    }

    /// Mirror Android splitLimeMetadataFields(): try each delimiter in priority
    /// order and return [key, value] where value re-joins any trailing fields, so
    /// legacy v1 metadata whose value contains the delimiter survives.
    private func splitLimeMetadataFields(_ line: String, escapedFormat: Bool) -> [String] {
        for delimiter in ["|", "\t", ",", " "] {
            let parts = splitEscapedFields(line, delimiter: Character(delimiter), escapedFormat: escapedFormat)
            if parts.count >= 2, parts[0].trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("@") {
                if parts.count == 2 { return parts }
                let value = parts[1...].joined(separator: delimiter)
                return [parts[0], value]
            }
        }
        return [line]
    }

    private func splitEscapedFields(_ line: String, delimiter: Character, escapedFormat: Bool) -> [String] {
        var fields: [String] = []
        var current = ""
        var escaping = false
        for char in line {
            if escapedFormat && escaping {
                current.append(decodeEscapedCharacter(char))
                escaping = false
            } else if escapedFormat && char == "\\" {
                escaping = true
            } else if char == delimiter {
                fields.append(current)
                current.removeAll(keepingCapacity: true)
            } else {
                current.append(char)
            }
        }
        if escapedFormat && escaping { current.append("\\") }
        fields.append(current)
        return fields
    }

    private func decodeEscapedCharacter(_ char: Character) -> Character {
        switch char {
        case "t": return "\t"
        case "n": return "\n"
        default: return char
        }
    }

    private func escapeField(_ value: String, delimiter: Character) -> String {
        var output = ""
        for char in value {
            switch char {
            case "\\":
                output += "\\\\"
            case let c where c == delimiter:
                output.append("\\")
                output.append(char)
            case "@":
                output += "\\@"
            case "%":
                output += "\\%"
            case "\t":
                output += "\\t"
            case "\n":
                output += "\\n"
            default:
                output.append(char)
            }
        }
        return output
    }

    private func needsEscaping(_ value: String, delimiter: Character, codeField: Bool = false) -> Bool {
        let lower = value.lowercased()
        return value.contains(delimiter)
            || value.contains("\\")
            || value.contains("\t")
            || value.contains("\n")
            || (codeField && value.hasPrefix("@"))
            || (codeField && (lower.hasPrefix("%version")
                || lower.hasPrefix("%cname")
                || lower.hasPrefix("%selkey")
                || lower.hasPrefix("%endkey")
                || lower.hasPrefix("%limeendkey")
                || lower.hasPrefix("%spacestyle")))
    }

    /// Async variant with background dispatch and main-queue completion (mirrors Java Thread spawn).
    func importTxtFileAsync(at path: String, tableName: String,
                            progress: ((Int) -> Void)? = nil,
                            completion: @escaping (Error?) -> Void) {
        importCancelled = false
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            do {
                try self.importTxtFile(at: path, tableName: tableName, progress: { count in
                    DispatchQueue.main.async { progress?(count) }
                })
                DispatchQueue.main.async { completion(nil) }
            } catch {
                DispatchQueue.main.async { completion(error) }
            }
        }
    }

    private func flushBatch(_ batch: inout [(code: String, word: String, score: Int, baseScore: Int)], tableName: String) throws -> Int {
        let count = batch.count
        try dbQueue.write { db in
            for pair in batch {
                if tableName == "phonetic" {
                    let noTone = pair.code.replacingOccurrences(of: "[3467 ]", with: "", options: .regularExpression)
                    try db.execute(
                        sql: "INSERT OR IGNORE INTO \(tableName) (code, word, score, basescore, code3r) VALUES (?, ?, ?, ?, ?)",
                        arguments: [pair.code, pair.word, pair.score, pair.baseScore, noTone])
                } else {
                    try db.execute(
                        sql: "INSERT OR IGNORE INTO \(tableName) (code, word, score, basescore) VALUES (?, ?, ?, ?)",
                        arguments: [pair.code, pair.word, pair.score, pair.baseScore])
                }
            }
        }
        batch.removeAll()
        return count
    }

    private func flushRelatedBatch(_ batch: inout [(pword: String, cword: String, baseScore: Int, userScore: Int)]) throws -> Int {
        let count = batch.count
        try dbQueue.write { db in
            for row in batch {
                try db.execute(
                    sql: "INSERT OR IGNORE INTO related (pword, cword, basescore, score) VALUES (?, ?, ?, ?)",
                    arguments: [row.pword, row.cword, row.baseScore, row.userScore])
            }
        }
        batch.removeAll()
        return count
    }

    func importFromZip(at zipURL: URL, tableName: String) throws {
        guard isValidTableName(tableName) else { throw LimeDBError.invalidTableName(tableName) }
        let archive = try Archive(url: zipURL, accessMode: .read)
        guard let entry = archive.first(where: { $0.path.hasSuffix(".db") }) else { throw LimeDBError.fileNotFound("*.db inside zip") }
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".db")
        _ = try archive.extract(entry, to: tempURL)
        defer { try? FileManager.default.removeItem(at: tempURL) }
        try importFromAttachedDB(sourcePath: tempURL.path, tableName: tableName)
    }

    // MARK: - Register IM

    func registerIM(imName: String, tableName: String, label: String, keyboardId: String) throws {
        try dbQueue.write { db in
            // If the cloud DB merge in `importFromAttachedDB` already produced kv rows
            // for this IM, those rows are authoritative — preserve them and do nothing.
            // Only synthesize a fallback row when the source DB carried no `im` table
            // (e.g. user-supplied raw SQLite, or seed `.limedb` flows).
            let existing = (try? Int.fetchOne(db,
                sql: "SELECT COUNT(*) FROM im WHERE code = ?",
                arguments: [imName])) ?? 0
            if existing > 0 { return }
            try db.execute(sql: """
                INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                VALUES (?, ?, '', ?, 0, '', '', '')
            """, arguments: [imName, label, keyboardId])
        }
    }

    /// Registers the "custom" (自建) IM in the im table if it is not already present.
    /// Always seeded on explicit user action — even when the custom table is empty.
    /// fix#177: seeds "limenum" to match defaultKeyboardCodeForImportedIM/Android.
    /// The previous 'lime_abc' is the Chinese-mode alphabet layout, whose mode key is
    /// `中` (code -10 → switchToIM) rather than an `abc` English switch, so a custom
    /// user had no way to reach the English keyboard.
    func seedCustomIM() throws {
        try dbQueue.write { db in
            let exists = (try? Int.fetchOne(db,
                sql: "SELECT COUNT(*) FROM im WHERE code = ?",
                arguments: ["custom"]) ?? 0) ?? 0 > 0
            guard !exists else {
                // fix#177: an existing registration may still carry one of the historical
                // unusable values — NULL/'' (unset), 'lime' (old import fallthrough, names
                // a layout iOS does not ship) or 'lime_abc' (old seed, `中` mode key).
                // Repair only those; any other value is a keyboard the user chose.
                try db.execute(sql: """
                    UPDATE im SET keyboard = 'limenum'
                    WHERE code = 'custom'
                      AND title IN ('自建', 'keyboard')
                      AND (keyboard IS NULL OR keyboard IN ('', 'lime', 'lime_abc'))
                """)
                return
            }
            try db.execute(sql: """
                INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
                VALUES ('custom', '自建', '', 'limenum', 0, '', '', '')
            """)
        }
    }

    // MARK: - Factory Reset (mirrors Android restoredToDefault)

    /// Resets all user-learned data to factory state.
    /// On iOS we cannot replace the DB file from a bundled raw resource, so instead we:
    ///   1. Reset scores to 0 in all IM mapping tables.
    ///   2. Delete all user-added records (score > 0 that were not imported from a file, i.e. custom table).
    ///   3. Clear the related phrase learning table.
    ///   4. Clear all in-memory caches.
    func restoredToDefault() {
        guard !checkDBConnection() else { return }
        holdDBConnection()
        defer { unHoldDBConnection() }
        let mappingTables = [
            "phonetic", "dayi", "array", "array10", "cj", "cj4", "cj5", "custom",
            "ecj", "ez", "hs", "pinyin", "scj", "wb",
            "imtable2", "imtable3", "imtable4", "imtable5",
            "imtable6", "imtable7", "imtable8", "imtable9", "imtable10"
        ]
        try? dbQueue.write { db in
            for t in mappingTables {
                // Only reset tables that exist
                let exists = (try? Int.fetchOne(db,
                    sql: "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                    arguments: [t])) ?? 0
                guard exists > 0 else { continue }
                try? db.execute(sql: "UPDATE \(t) SET score = 0")
            }
            // Clear all related-phrase learning
            try? db.execute(sql: "DELETE FROM related")
        }
        resetCache()
    }

    // MARK: - IM Capability Detection

    /// Literal characters the IM accepts as composing input.
    /// The phonetic family resolves via the active `phoneticKeyboardType`
    /// (BPMF / ETEN26 / HSU / ETEN). Other IMs prefer the table's stored
    /// `imkeys`/`imkeynames`; hardcoded keymaps are fallback only.
    /// Used by the iOS keyboard controller to route iPad dual-sliding punctuation /
    /// full-shape codes (which are guaranteed not to be in any IM's key set) to
    /// the direct-output path instead of corrupting the composing buffer.
    func imKeysForTable(_ tableName: String) -> String {
        let kbType = phoneticKeyboardType
        switch tableName {
        case "phonetic", "et41", "et_41", "eten":
            if kbType.hasPrefix("eten26") || kbType == "et26" { return LimeDB.ETEN26_KEY }
            if kbType.hasPrefix("hsu")                        { return LimeDB.HSU_KEY }
            if kbType == "et_41" || kbType == "eten"          { return LimeDB.ETEN_KEY }
            return LimeDB.BPMF_KEY
        default:
            let stored = getImConfig(tableName, "imkeys") ?? ""
            if !stored.isEmpty { return stored }

            switch tableName {
            case "cj", "cj4", "scj", "cj5", "ecj":
                return LimeDB.CJ_KEY
            case "dayi":
                return LimeDB.DAYI_KEY
            case "array":
                return LimeDB.ARRAY_KEY
            case "array10":
                return LimeDB.ARRAY10_KEY
            default:
                return ""
            }
        }
    }

    /// Companion to `imKeysForTable(_:)` (LIME DB 105): returns the stored `imkeynames`
    /// for a table if non-empty, else the known in-memory default. Lets the keyboard
    /// extension resolve a correct display name for a known standard table whose `im`
    /// row is empty (e.g. a table just restored into a live DB, before the host-app-only
    /// `ensureStandardIMKeyMetadata` backfill has run) without any DB write. `array10`,
    /// `custom`, and unknown tables have no built-in keyname map and resolve to "".
    func imKeyNamesForTable(_ tableName: String) -> String {
        let stored = getImConfig(tableName, "imkeynames") ?? ""
        if !stored.isEmpty { return stored }
        switch tableName {
        case "dayi":
            return LimeDB.DAYI_CHAR
        case "cj", "cj4", "scj", "cj5", "ecj":
            return LimeDB.CJ_CHAR
        case "array":
            return LimeDB.ARRAY_CHAR
        case "ez":
            return LimeDB.EZ_CHAR
        case "phonetic":
            return LimeDB.BPMF_CHAR
        default:
            return ""
        }
    }

    func detectIMCapabilities(tableName: String) -> (hasNumber: Bool, hasSymbol: Bool) {
        guard isValidTableName(tableName) else { return (false, false) }
        let codes = (try? dbQueue.read { db in
            try String.fetchAll(db, sql: "SELECT code FROM \(tableName) LIMIT 200")
        }) ?? []
        var hasNumber = false
        var hasSymbol = false
        for code in codes {
            for ch in code.unicodeScalars {
                let v = ch.value
                if v >= 48 && v <= 57 { hasNumber = true }
                else if (v > 32 && v < 48) || (v > 57 && v < 65) ||
                        (v > 90 && v < 97) || (v > 122 && v < 127) { hasSymbol = true }
            }
            if hasNumber && hasSymbol { break }
        }
        return (hasNumber, hasSymbol)
    }
}

// MARK: - Errors

enum LimeDBError: Error {
    case invalidTableName(String)
    case fileNotFound(String)
}

// MARK: - DatabaseValue helper

private extension DatabaseValue {
    init(value: Any?) {
        if let v = value {
            if let s = v as? String { self = s.databaseValue }
            else if let i = v as? Int { self = i.databaseValue }
            else if let i = v as? Int64 { self = i.databaseValue }
            else if let d = v as? Double { self = d.databaseValue }
            else if let b = v as? Bool { self = b.databaseValue }
            else { self = DatabaseValue.null }
        } else {
            self = DatabaseValue.null
        }
    }
}

// MARK: - StreamReader

private final class StreamReader: Sequence {
    private let fileHandle: FileHandle
    private var buffer = Data()
    private let encoding: String.Encoding
    private let chunkSize = 4096
    private var eof = false

    init?(path: String, encoding: String.Encoding = .utf8) {
        guard let fh = FileHandle(forReadingAtPath: path) else { return nil }
        fileHandle = fh
        self.encoding = encoding
    }
    deinit { fileHandle.closeFile() }

    func makeIterator() -> AnyIterator<String> {
        AnyIterator {
            while true {
                // `Data` indices are not always 0-based after mutation, so translate
                // the absolute `nl` index to an offset from startIndex before using
                // it as a count with `removeFirst`.
                if let nl = self.buffer.firstIndex(of: UInt8(ascii: "\n")) {
                    let lineData = self.buffer[self.buffer.startIndex..<nl]
                    let removeCount = self.buffer.distance(from: self.buffer.startIndex, to: nl) + 1
                    self.buffer.removeFirst(removeCount)
                    return String(data: lineData, encoding: self.encoding)
                }
                if self.eof {
                    // Return any trailing data without a final newline as a last line.
                    if !self.buffer.isEmpty {
                        let tail = self.buffer
                        self.buffer.removeAll(keepingCapacity: false)
                        return String(data: tail, encoding: self.encoding)
                    }
                    return nil
                }
                let chunk = self.fileHandle.readData(ofLength: self.chunkSize)
                if chunk.isEmpty { self.eof = true } else { self.buffer.append(chunk) }
            }
        }
    }
}

// MARK: - Safe Row accessors
//
// GRDB's generic `Row.subscript<Value>(_:) -> Value` calls `try! decode(...)` and
// crashes on NULL columns. The `row["col"] as Type? ?? default` inline form is
// unreliable — Swift overload resolution can still dispatch to the non-optional
// overload, force-decoding and crashing. These helpers bind to a TYPED OPTIONAL
// local first, which unambiguously picks GRDB's `-> Value?` overload.
fileprivate extension Row {
    func optString(_ column: String) -> String? {
        let v: String? = self[column]
        return v
    }
    func optInt(_ column: String) -> Int? {
        let v: Int? = self[column]
        return v
    }
    func optInt64(_ column: String) -> Int64? {
        let v: Int64? = self[column]
        return v
    }
}
