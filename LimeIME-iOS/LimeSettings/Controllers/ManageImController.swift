// ManageImController.swift
// LimeIME-iOS
//
// Async record CRUD + pagination for the IM Table Editor.
// Mirrors Android ManageImController.

import Foundation

// MARK: - ManageImController

@MainActor
final class ManageImController: BaseController {

    // MARK: - App Group

    private static let appGroupID = LIMEPreferenceManager.suiteName
    private static let cacheResetKey = "needsKeyboardCacheReset"

    /// Signal the keyboard extension to clear its in-memory search cache on next activation.
    private static func markKeyboardCacheDirty() {
        UserDefaults(suiteName: appGroupID)?.set(true, forKey: cacheResetKey)
    }

    nonisolated static let pageSize = 100

    /// Incrementing this causes IMListView to reload its IM list.
    /// Call after any external IM registration change.
    @Published var refreshToken: Int = 0

    func invalidate() { refreshToken += 1 }

    // MARK: - Init

    override init(dbServer: DBServer = .shared, prefs: LIMEPreferenceManager = .shared) {
        super.init(dbServer: dbServer, prefs: prefs)
    }

    // MARK: - SearchServer access

    /// SearchServer backed by the same LimeDB connection as DBServer — used for all queries.
    private var searchServer: SearchServer? { dbServer.makeSearchServer() }

    // MARK: - Load records (async, SwiftUI-friendly)

    /// Fetches one page of records AND the total count via SearchServer.
    /// Call on first appear, after query/mode change, and after any mutation.
    func loadRecords(table: String, query: String?, searchByCode: Bool,
                     page: Int) async -> (records: [LimeRecord], total: Int) {
        let offset = page * ManageImController.pageSize
        let ss = searchServer
        let q = query?.isEmpty == false ? query : nil
        return await Task.detached(priority: .userInitiated) {
            let records = ss?.getRecords(table, q, searchByCode: searchByCode,
                                         ManageImController.pageSize, offset) ?? []
            // No query → SearchServer.countRecords (fast, uses ifnull(word,'') <> '')
            // Active query → countRecordsByWordOrCode (filtered count for correct pagination)
            let total = q != nil
                ? (ss?.countRecordsByWordOrCode(table, q, searchByCode: searchByCode) ?? 0)
                : (ss?.countRecords(table) ?? 0)
            return (records, total)
        }.value
    }

    /// Fetches one page of records only — no COUNT query.
    /// Call for page-turn navigation where the total is already known.
    func loadPage(table: String, query: String?, searchByCode: Bool,
                  page: Int) async -> [LimeRecord] {
        let offset = page * ManageImController.pageSize
        let ss = searchServer
        let q = query?.isEmpty == false ? query : nil
        return await Task.detached(priority: .userInitiated) {
            ss?.getRecords(table, q, searchByCode: searchByCode,
                           ManageImController.pageSize, offset) ?? []
        }.value
    }

    // MARK: - Add record

    func addRecord(table: String, code: String, word: String,
                   score: Int) async -> Result<Void, Error> {
        guard !code.isEmpty, !word.isEmpty else {
            return .failure(ControllerError.validation("字根和文字不能為空"))
        }
        let ss = searchServer
        let server = self.dbServer
        let result: Result<Void, Error> = await Task.detached(priority: .userInitiated) {
            let rowID = ss?.addRecord(table, ["code": code, "word": word, "score": score]) ?? -1
            guard rowID > 0 else { return .failure(ControllerError.operation("新增失敗")) }
            do {
                try server.markTableChangedAndPublish(table)
                return .success(())
            } catch {
                return .failure(error)
            }
        }.value
        if case .success = result {
            ManageImController.markKeyboardCacheDirty()
        }
        return result
    }

    // MARK: - Update record

    func updateRecord(table: String, id: String, code: String, word: String,
                      score: Int) async -> Result<Void, Error> {
        guard !code.isEmpty, !word.isEmpty else {
            return .failure(ControllerError.validation("字根和文字不能為空"))
        }
        let ss = searchServer
        let server = self.dbServer
        let result: Result<Void, Error> = await Task.detached(priority: .userInitiated) {
            let affected = ss?.updateRecord(table, ["code": code, "word": word, "score": score],
                                            "_id = ?", [id]) ?? 0
            guard affected > 0 else { return .failure(ControllerError.operation("更新失敗")) }
            do {
                try server.markTableChangedAndPublish(table)
                return .success(())
            } catch {
                return .failure(error)
            }
        }.value
        if case .success = result {
            ManageImController.markKeyboardCacheDirty()
        }
        return result
    }

    // MARK: - Delete record

    func deleteRecord(table: String, id: String) async -> Result<Void, Error> {
        let ss = searchServer
        let server = self.dbServer
        let result: Result<Void, Error> = await Task.detached(priority: .userInitiated) {
            let affected = ss?.deleteRecord(table, "_id = ?", [id]) ?? 0
            guard affected > 0 else { return .failure(ControllerError.operation("刪除失敗")) }
            do {
                try server.markTableChangedAndPublish(table)
                return .success(())
            } catch {
                return .failure(error)
            }
        }.value
        if case .success = result {
            ManageImController.markKeyboardCacheDirty()
        }
        return result
    }

    // MARK: - IM list

    func loadIMList() async -> [ImConfig] {
        let server = self.dbServer
        return await Task.detached(priority: .userInitiated) {
            (try? server.getAllImConfigs()) ?? []
        }.value
    }

    // MARK: - Toggle IM enabled

    func setIMEnabled(imName: String, enabled: Bool) async {
        let server = self.dbServer
        let localPrefs = self.prefs
        await Task.detached(priority: .userInitiated) {
            server.updateIMEnabled(imName: imName, enabled: enabled)
            await MainActor.run { localPrefs.syncIMActivatedState(dbServer: server) }
        }.value
    }

    // MARK: - Update IM sort order

    func setIMSortOrder(id: Int64, sortOrder: Int) async {
        let server = self.dbServer
        await Task.detached(priority: .background) {
            try? server.updateIMSortOrder(id: id, sortOrder: sortOrder)
        }.value
    }

    // MARK: - Keyboard config

    func loadKeyboards(forIM tableNick: String) async -> (keyboards: [KeyboardConfig], selected: String) {
        let server = self.dbServer
        return await Task.detached(priority: .userInitiated) {
            let list = server.getKeyboardConfigList() ?? []
            let enabled = list.filter { !$0.isDisabled }
            let raw = (try? server.getAllImConfigs().first(where: { $0.tableNick == tableNick }))?.keyboardId ?? ""
            // im.keyboard may store an imkb-style id (e.g. "lime_phonetic").
            // Resolve to the matching keyboard.code so the checkmark comparison works.
            let selected: String
            if enabled.contains(where: { $0.code == raw }) {
                selected = raw
            } else if let match = enabled.first(where: { $0.imkb == raw }) {
                selected = match.code
            } else {
                selected = raw
            }
            return (enabled, selected)
        }.value
    }

    func countRecords(table: String) async -> Int {
        let ss = searchServer
        return await Task.detached(priority: .userInitiated) {
            ss?.countRecords(table) ?? 0
        }.value
    }

    /// Counts rows in the `related` table via DBServer (related has pword/cword, not word).
    func countRelated() async -> Int {
        let server = self.dbServer
        return await Task.detached(priority: .userInitiated) {
            server.countRecords("related", nil, nil)
        }.value
    }

    func setKeyboard(forIM tableNick: String, keyboard: KeyboardConfig) async {
        let server = self.dbServer
        await Task.detached(priority: .background) {
            server.setImConfigKeyboard(tableNick, keyboard)
        }.value
        ManageImController.markKeyboardCacheDirty()
    }

    func updateIMMetadata(tableNick: String, name: String, version: String) async -> Result<Void, Error> {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedVersion = version.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !tableNick.isEmpty else {
            return .failure(ControllerError.validation("輸入法代碼不能為空"))
        }
        guard !trimmedName.isEmpty else {
            return .failure(ControllerError.validation("名稱不能為空"))
        }

        let server = self.dbServer
        await Task.detached(priority: .userInitiated) {
            server.setImConfig(tableNick, "name", trimmedName)
            server.setImConfig(tableNick, "version", trimmedVersion)
        }.value

        ManageImController.markKeyboardCacheDirty()
        invalidate()
        return .success(())
    }

    func updateIMMetadataField(tableNick: String, field: String, value: String) async -> Result<Void, Error> {
        let trimmedValue = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !tableNick.isEmpty else {
            return .failure(ControllerError.validation("輸入法代碼不能為空"))
        }
        guard field == "name" || field == "version" || field == "limeendkey" else {
            return .failure(ControllerError.validation("欄位不正確"))
        }
        guard field != "name" || !trimmedValue.isEmpty else {
            return .failure(ControllerError.validation("名稱不能為空"))
        }

        let server = self.dbServer
        await Task.detached(priority: .userInitiated) {
            server.setImConfig(tableNick, field, trimmedValue)
        }.value

        ManageImController.markKeyboardCacheDirty()
        invalidate()
        return .success(())
    }

    // MARK: - Protocol-based methods (kept for unit tests with mock views)

    func addRecord(table: String, code: String, word: String,
                   score: Int, view: (any ManageImView)?) {
        guard !code.isEmpty, !word.isEmpty else {
            view?.onError("字根和文字不能為空"); return
        }
        let ss = searchServer
        let server = self.dbServer
        Task.detached(priority: .userInitiated) {
            let rowID = ss?.addRecord(table, ["code": code, "word": word, "score": score]) ?? -1
            if rowID > 0 {
                try? server.markTableChangedAndPublish(table)
            }
            await MainActor.run {
                rowID > 0 ? view?.refreshRecordList() : view?.onError("新增失敗")
            }
        }
    }

    func updateRecord(table: String, id: String, code: String, word: String,
                      score: Int, view: (any ManageImView)?) {
        guard !code.isEmpty, !word.isEmpty else {
            view?.onError("字根和文字不能為空"); return
        }
        let ss = searchServer
        let server = self.dbServer
        Task.detached(priority: .userInitiated) {
            let affected = ss?.updateRecord(table, ["code": code, "word": word, "score": score],
                                            "_id = ?", [id]) ?? 0
            if affected > 0 {
                try? server.markTableChangedAndPublish(table)
            }
            await MainActor.run {
                affected > 0 ? view?.refreshRecordList() : view?.onError("更新失敗")
            }
        }
    }

    func deleteRecord(table: String, id: String, view: (any ManageImView)?) {
        let ss = searchServer
        let server = self.dbServer
        Task.detached(priority: .userInitiated) {
            let affected = ss?.deleteRecord(table, "_id = ?", [id]) ?? 0
            if affected > 0 {
                try? server.markTableChangedAndPublish(table)
            }
            await MainActor.run {
                affected > 0 ? view?.refreshRecordList() : view?.onError("刪除失敗")
            }
        }
    }

    func loadRecords(table: String, query: String?, searchByCode: Bool,
                     page: Int, view: (any ManageImView)?) {
        let offset = page * ManageImController.pageSize
        let ss = searchServer
        let q = query?.isEmpty == false ? query : nil
        Task.detached(priority: .userInitiated) {
            let records = ss?.getRecords(table, q, searchByCode: searchByCode,
                                         ManageImController.pageSize, offset) ?? []
            let total = q != nil
                ? (ss?.countRecordsByWordOrCode(table, q, searchByCode: searchByCode) ?? 0)
                : (ss?.countRecords(table) ?? 0)
            await MainActor.run {
                view?.displayRecords(records)
                view?.updateRecordCount(total)
            }
        }
    }

    func toggleIMEnabled(imName: String, enabled: Bool, view: (any ManageImView)?) {
        let server = self.dbServer
        let localPrefs = self.prefs
        Task.detached(priority: .userInitiated) {
            server.updateIMEnabled(imName: imName, enabled: enabled)
            await MainActor.run { localPrefs.syncIMActivatedState(dbServer: server) }
            await MainActor.run { view?.refreshRecordList() }
        }
    }

    func updateIMSortOrder(id: Int64, sortOrder: Int) {
        let server = self.dbServer
        Task.detached(priority: .background) {
            try? server.updateIMSortOrder(id: id, sortOrder: sortOrder)
        }
    }

    // MARK: - Remove IM table (mirrors Android SetupImController.clearTable)

    /// Clears all mapping records for the given IM table and resets its im-config rows.
    /// Mirrors Android: searchServer.clearTable(tableName) → LimeDB.clearTable() + resetImConfig()
    /// followed by syncIMActivatedState() to rebuild keyboard_state.
    /// keyboard_list (active IM) is intentionally left unchanged — matches Android behaviour.
    func clearTable(tableNick: String, backupLearning: Bool = false) async -> Result<Void, Error> {
        let ss = searchServer
        let server = self.dbServer
        let localPrefs = self.prefs
        let result: Result<Void, Error> = await Task.detached(priority: .userInitiated) {
            ss?.clearTable(tableNick)
            await MainActor.run { localPrefs.syncIMActivatedState(dbServer: server) }
            do {
                try server.writeIMLifecycleRecord(table: tableNick,
                                                  action: .delete,
                                                  preserveLearning: backupLearning,
                                                  postSignal: false)
                // §1.5: no `im` inbox — the `im` row deletion rides the mirror on the
                // generation bump from markTableChangedAndPublish below (rev + publish).
                try server.markTableChangedAndPublish(tableNick)
                return .success(())
            } catch {
                return .failure(error)
            }
        }.value
        if case .success = result {
            ManageImController.markKeyboardCacheDirty()
            invalidate()
        }
        return result
    }
}

// MARK: - ControllerError

enum ControllerError: LocalizedError {
    case validation(String)
    case operation(String)

    var errorDescription: String? {
        switch self {
        case .validation(let msg), .operation(let msg): return msg
        }
    }
}
