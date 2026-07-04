// LimeDBProtocol.swift
// Protocol abstracting every LimeDB call made by SearchServer, enabling dependency injection in tests.
// SearchServer's `db` property is typed `any LimeDBProtocol`; production code passes a real `LimeDB`.
// Test code passes a `SpyLimeDB` (or any other conforming double).

import Foundation

// MARK: - LimeDBProtocol

/// All methods and settable properties that SearchServer calls on its `db` field.
protocol LimeDBProtocol: AnyObject {

    // MARK: Preference-driven behaviour knobs
    var learnRelatedWords: Bool { get set }
    var similarCodeCandidatesCap: Int { get set }
    var sortSuggestions: Bool { get set }
    var phoneticKeyboardType: String { get set }

    // MARK: IM / table management
    func getSelkeyForIM(_ imCode: String) -> String
    func setTableName(_ name: String)
    func keyToKeyName(_ code: String?, _ table: String, _ composingText: Bool) -> String

    // MARK: Core mapping queries
    func getMappingByCode(_ code: String?, softKeyboard: Bool, getAllRecords: Bool) -> [Mapping]?
    func getMappingByWord(_ keyword: String?, table: String) -> [Mapping]?

    // MARK: Related phrases
    func isRelatedPhraseExist(_ pword: String?, _ cword: String?) -> Mapping?
    func getRelatedMappings(parentWord: String, limit: Int) throws -> [Mapping]

    // MARK: Score / learning
    func updateScore(id: Int64, score: Int, tableName: String) throws
    func addOrUpdateRelatedPhraseRecord(_ pword: String, _ cword: String?) -> Int
    func addOrUpdateMappingRecord(code: String, word: String, tableName: String) throws
    func addOrUpdateMappingRecord(_ table: String, _ code: String, _ word: String, _ score: Int)

    // MARK: Emoji
    func emojiConvert(_ source: String, _ emoji: Int) -> [Mapping]
    func findEmojiForCandidate(_ candidate: String, locale: LimeDB.EmojiLocale, limit: Int) -> [Mapping]
    func searchEmoji(_ queryText: String, locale: LimeDB.EmojiLocale, limit: Int) -> [Mapping]
    func loadEmojiCategoryPages() -> [[Mapping]]
    func loadRecentEmoji(limit: Int) -> [Mapping]
    func recordEmojiUsage(_ value: String, timestampSeconds: Int64)

    // MARK: Reverse lookup / English
    func getCodeListStringByWord(_ keyword: String, table: String?) -> String?
    func getEnglishSuggestions(_ word: String) -> [String]?

    // MARK: IM config
    func getImConfigList(_ code: String?, _ configEntry: String?) -> [LimeImConfigRow]
    func getImConfig(_ imCode: String?, _ field: String?) -> String?
    func setImConfig(_ imCode: String?, _ field: String?, _ value: String?)
    func setIMConfigKeyboard(_ imCode: String, _ desc: String, _ keyboardCode: String)
    func setImConfigKeyboard(_ imCode: String, _ keyboard: KeyboardConfig)
    func removeImConfig(_ imCode: String?, _ field: String?)
    func resetImConfig(_ imCode: String?)
    func restoredToDefault()

    // MARK: Keyboard config
    func getKeyboardConfigList() -> [KeyboardConfig]?
    func getKeyboardConfig(_ keyboard: String?) -> KeyboardConfig?
    func getKeyboardInfo(_ keyboardCode: String, _ field: String) -> String?
    func detectIMCapabilities(tableName: String) -> (hasNumber: Bool, hasSymbol: Bool)
    func imKeysForTable(_ tableName: String) -> String

    // MARK: Table validation
    func isValidTableName(_ name: String?) -> Bool

    // MARK: Record CRUD
    func getRecordList(_ table: String, _ query: String?, searchByCode: Bool, _ maximum: Int, _ offset: Int) -> [LimeRecord]
    func getRecord(_ table: String, _ id: Int64) -> LimeRecord?
    func addRecord(_ table: String, _ values: [String: Any?]) -> Int64
    func addRecord(_ table: String, _ values: [String: Any?], syncMode: SyncRevMode) -> Int64
    @discardableResult func deleteRecord(_ table: String, _ whereClause: String?, _ whereArgs: [String]?) -> Int
    @discardableResult func deleteRecord(_ table: String, _ whereClause: String?, _ whereArgs: [String]?, syncMode: SyncRevMode) -> Int
    @discardableResult func updateRecord(_ table: String, _ values: [String: Any?], _ whereClause: String?, _ whereArgs: [String]?) -> Int
    @discardableResult func updateRecord(_ table: String, _ values: [String: Any?], _ whereClause: String?, _ whereArgs: [String]?, syncMode: SyncRevMode) -> Int
    func countRecords(_ table: String, _ whereClause: String?, _ whereArgs: [String]?) -> Int

    // MARK: Related table
    func getRelated(_ pword: String?, _ maximum: Int, _ offset: Int) -> [Related]

    // MARK: Backup / restore
    func backupUserRecords(_ table: String)
    func restoreUserRecords(_ table: String) -> Int
    func checkBackupTable(_ table: String) -> Bool
    func getBackupTableRecords(_ backupTableName: String) -> [[String: Any]]?
    @discardableResult func dropBackupTable(_ table: String) -> Bool

    // MARK: Cache / misc
    func clearTable(_ table: String)
    func resetCache()
    func checkPhoneticKeyboardSetting()
    func hanConvert(_ input: String, _ hanOption: Int) -> String
}

// MARK: - LimeDB: LimeDBProtocol

/// Retroactive conformance — LimeDB already implements every requirement above.
/// No forwarding stubs are needed; this declaration is enough for the compiler.
extension LimeDB: LimeDBProtocol {}
