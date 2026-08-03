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

// ManageRelatedController.swift
// LimeIME-iOS
//
// Async related-phrase CRUD + pagination.
// Mirrors Android ManageRelatedController (part of ManageImController).

import Foundation

// MARK: - ManageRelatedController

@MainActor
final class ManageRelatedController: BaseController {

    nonisolated static let pageSize = 100

    /// Incrementing this causes RelatedListView to reload its data.
    /// Call after seeding or any external data change.
    @Published var refreshToken: Int = 0

    func invalidate() { refreshToken += 1 }

    // MARK: - Init

    override init(dbServer: DBServer = .shared, prefs: LIMEPreferenceManager = .shared) {
        super.init(dbServer: dbServer, prefs: prefs)
    }

    // MARK: - Load (async, SwiftUI-friendly)

    func loadRelated(query: String?, page: Int) async -> (phrases: [Related], total: Int) {
        let offset = page * ManageRelatedController.pageSize
        let server = self.dbServer
        let q: String? = (query?.isEmpty == false) ? query : nil
        return await Task.detached(priority: .userInitiated) {
            let phrases = server.searchRelatedForManagement(q, ManageRelatedController.pageSize, offset)
            let total = server.countRelatedForManagement(q)
            return (phrases, total)
        }.value
    }

    func addRelated(parentWord: String, childWord: String,
                    score: Int = 0) async -> Result<Void, Error> {
        guard !parentWord.isEmpty, !childWord.isEmpty else {
            return .failure(ControllerError.validation("詞彙和關聯字不能為空"))
        }
        let server = self.dbServer
        let result: Result<Void, Error> = await Task.detached(priority: .userInitiated) {
            do {
                _ = try server.performEditorMutation(.addRelated(parentWord: parentWord,
                                                                  childWord: childWord,
                                                                  score: score))
                ManageImController.markKeyboardCacheDirty()   // #161: flush keyboard relatedCache
                return .success(())
            } catch {
                return .failure(error)
            }
        }.value
        return result
    }

    func updateRelated(id: Int64, parentWord: String,
                       childWord: String, score: Int = 0) async -> Result<Void, Error> {
        guard !parentWord.isEmpty, !childWord.isEmpty else {
            return .failure(ControllerError.validation("詞彙和關聯字不能為空"))
        }
        let server = self.dbServer
        let result: Result<Void, Error> = await Task.detached(priority: .userInitiated) {
            do {
                let result = try server.performEditorMutation(.updateRelated(id: id,
                                                                             parentWord: parentWord,
                                                                             childWord: childWord,
                                                                             score: score))
                guard result.affectedRows > 0 else {
                    return .failure(ControllerError.operation("更新失敗"))
                }
                ManageImController.markKeyboardCacheDirty()   // #161: flush keyboard relatedCache
                return .success(())
            } catch {
                return .failure(error)
            }
        }.value
        return result
    }

    func clearRelated() async -> Result<Void, Error> {
        let server = self.dbServer
        let result: Result<Void, Error> = await Task.detached(priority: .userInitiated) {
            do {
                _ = try server.performEditorMutation(.clearTable("related"))
                ManageImController.markKeyboardCacheDirty()   // #161: flush keyboard relatedCache
                return .success(())
            } catch {
                return .failure(error)
            }
        }.value
        if case .success = result {
            invalidate()
        }
        return result
    }

    func deleteRelated(id: Int64) async -> Result<Void, Error> {
        let server = self.dbServer
        let result: Result<Void, Error> = await Task.detached(priority: .userInitiated) {
            do {
                let result = try server.performEditorMutation(.deleteRelated(id: id))
                guard result.affectedRows > 0 else {
                    return .failure(ControllerError.operation("刪除失敗"))
                }
                ManageImController.markKeyboardCacheDirty()   // #161: flush keyboard relatedCache
                return .success(())
            } catch {
                return .failure(error)
            }
        }.value
        return result
    }

    // MARK: - Protocol-based methods (kept for unit tests with mock views)

    func loadRelated(query: String?, page: Int, view: (any ManageRelatedView)?) {
        let offset = page * ManageRelatedController.pageSize
        let server = self.dbServer
        let q: String? = (query?.isEmpty == false) ? query : nil
        Task.detached(priority: .userInitiated) {
            let phrases = server.searchRelatedForManagement(q, ManageRelatedController.pageSize, offset)
            await MainActor.run { view?.displayRelatedPhrases(phrases) }
        }
    }

    func addRelated(parentWord: String, childWord: String, score: Int = 0,
                    view: (any ManageRelatedView)?) {
        guard !parentWord.isEmpty, !childWord.isEmpty else {
            view?.onError("詞彙和關聯字不能為空"); return
        }
        let server = self.dbServer
        Task.detached(priority: .userInitiated) {
            let result: Result<Void, Error>
            do {
                _ = try server.performEditorMutation(.addRelated(parentWord: parentWord,
                                                                 childWord: childWord,
                                                                 score: score))
                ManageImController.markKeyboardCacheDirty()   // #161: flush keyboard relatedCache
                result = .success(())
            } catch {
                result = .failure(error)
            }
            await MainActor.run {
                switch result {
                case .success:
                    view?.refreshPhraseList()
                case .failure(let error):
                    view?.onError(error.localizedDescription)
                }
            }
        }
    }

    func updateRelated(id: Int64, parentWord: String, childWord: String,
                       score: Int = 0, view: (any ManageRelatedView)?) {
        guard !parentWord.isEmpty, !childWord.isEmpty else {
            view?.onError("詞彙和關聯字不能為空"); return
        }
        let server = self.dbServer
        Task.detached(priority: .userInitiated) {
            let result: Result<Void, Error>
            do {
                let mutation = try server.performEditorMutation(.updateRelated(id: id,
                                                                               parentWord: parentWord,
                                                                               childWord: childWord,
                                                                               score: score))
                if mutation.affectedRows > 0 {
                    ManageImController.markKeyboardCacheDirty()   // #161: flush keyboard relatedCache
                    result = .success(())
                } else {
                    result = .failure(ControllerError.operation("更新失敗"))
                }
            } catch {
                result = .failure(error)
            }
            await MainActor.run {
                switch result {
                case .success:
                    view?.refreshPhraseList()
                case .failure(let error):
                    view?.onError(error.localizedDescription)
                }
            }
        }
    }

    func deleteRelated(id: Int64, view: (any ManageRelatedView)?) {
        let server = self.dbServer
        Task.detached(priority: .userInitiated) {
            let result: Result<Void, Error>
            do {
                let mutation = try server.performEditorMutation(.deleteRelated(id: id))
                if mutation.affectedRows > 0 {
                    ManageImController.markKeyboardCacheDirty()   // #161: flush keyboard relatedCache
                    result = .success(())
                } else {
                    result = .failure(ControllerError.operation("刪除失敗"))
                }
            } catch {
                result = .failure(error)
            }
            await MainActor.run {
                switch result {
                case .success:
                    view?.refreshPhraseList()
                case .failure(let error):
                    view?.onError(error.localizedDescription)
                }
            }
        }
    }
}
