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

// RelatedListView.swift
// LimeIME-iOS
//
// Related-phrase CRUD — the 關聯字 tab.
// Spec §6.2.

import SwiftUI
import UIKit

// MARK: - RelatedListView

struct RelatedListView: View {

    @EnvironmentObject private var manageRelatedController: ManageRelatedController
    @EnvironmentObject private var setupController: SetupImController
    @Environment(\.scenePhase) private var scenePhase

    var isEmbedded: Bool = false

    @State private var phrases: [Related] = []
    @State private var totalCount: Int = 0
    @State private var page: Int = 0
    @State private var query: String = ""
    @State private var statusMessage = ""
    @State private var didPublishEditorClose = false

    @State private var loadTask: Task<Void, Never>?
    @State private var showAdd = false
    @State private var editingPhrase: IdentifiableRelated?
    @State private var deleteCandidate: IdentifiableRelated?
    @State private var showDeleteConfirm = false

    struct IdentifiableRelated: Identifiable {
        var id: Int64 { phrase.id }
        let phrase: Related
    }

    private let pageSize = 100

    private var totalPages: Int { max(1, (totalCount + pageSize - 1) / pageSize) }
    private var isLastPage: Bool { page >= totalPages - 1 }
    private var canEdit: Bool { true }

    var body: some View {
        if isEmbedded {
            editorContent
        } else {
            NavigationView { editorContent }
        }
    }

    private var editorContent: some View {
        VStack(spacing: 0) {
                // Inline search field — works reliably in both embedded and standalone contexts
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.secondary)
                    TextField("搜尋詞彙", text: $query)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .onChange(of: query) { _ in resetAndLoad() }
                    if !query.isEmpty {
                        Button { query = "" } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .padding(8)
                .background(Color(.systemGray6))
                .cornerRadius(10)
                .padding(.horizontal)
                .padding(.vertical, 6)

                Label(statusMessage.isEmpty ? capabilityMessage : statusMessage,
                      systemImage: capabilityIcon)
                    .font(.footnote)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                    .padding(.bottom, 6)

                List {
                    ForEach(phrases, id: \.id) { phrase in
                        HStack(spacing: 0) {
                            Text(phrase.parentWord)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text(phrase.childWord)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text(phrase.score.description)
                                .frame(width: 48, alignment: .trailing)
                                .foregroundColor(.secondary)
                        }
                        .font(.body)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            editingPhrase = IdentifiableRelated(phrase: phrase)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            if canEdit {
                                Button(role: .destructive) {
                                    deleteCandidate = IdentifiableRelated(phrase: phrase)
                                    showDeleteConfirm = true
                                } label: {
                                    Label("刪除", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
                .listStyle(.plain)

                // Pagination bar
                HStack {
                    Button("< 上頁") { changePage(page - 1) }
                        .disabled(page == 0)
                    Spacer()
                    Text("第 \(page + 1) / \(totalPages) 頁 · \(totalCount) 筆")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                    Button("下頁 >") { changePage(page + 1) }
                        .disabled(isLastPage)
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
                .background(Color(.systemGroupedBackground))
            }
            .navigationTitle("關聯字管理")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showAdd = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .disabled(!canEdit)
                }
            }
            .onAppear { loadPhrases() }
            .onChange(of: manageRelatedController.refreshToken) { _ in resetAndLoad() }
            .onChange(of: scenePhase) { _ in
                if scenePhase == .background {
                    publishEditorCloseIfNeeded()
                }
            }
            .onDisappear { publishEditorCloseIfNeeded() }
            .sheet(isPresented: $showAdd, onDismiss: { loadPhrases() }) {
                AddRelatedView()
            }
            .sheet(item: $editingPhrase, onDismiss: { loadPhrases() }) { wrapper in
                EditRelatedView(phrase: wrapper.phrase)
            }
            .alert("確認刪除", isPresented: $showDeleteConfirm, presenting: deleteCandidate) { wrapper in
                Button("刪除", role: .destructive) { deletePhrase(wrapper.phrase) }
                Button("取消", role: .cancel) {}
            } message: { wrapper in
                Text("確定要刪除「\(wrapper.phrase.parentWord) → \(wrapper.phrase.childWord)」？")
            }
    }

    // MARK: - Data

    private func loadPhrases() {
        loadTask?.cancel()
        loadTask = Task {
            let result = await manageRelatedController.loadRelated(
                query: query.isEmpty ? nil : query, page: page)
            guard !Task.isCancelled else { return }
            phrases = result.phrases
            totalCount = result.total
        }
    }

    private func resetAndLoad() {
        page = 0
        loadPhrases()
    }

    private func changePage(_ newPage: Int) {
        guard newPage >= 0, newPage < totalPages else { return }
        page = newPage
        loadPhrases()
    }

    private func deletePhrase(_ phrase: Related) {
        guard canEdit else { return }
        Task {
            _ = await manageRelatedController.deleteRelated(id: phrase.id)
            loadPhrases()
        }
    }

    private var capabilityMessage: String {
        "即時資料可編輯；鍵盤會在離開後套用變更。"
    }

    private var capabilityIcon: String {
        "checkmark.circle"
    }

    private func publishEditorCloseIfNeeded() {
        guard !didPublishEditorClose else { return }
        didPublishEditorClose = true
        Task {
            _ = await setupController.publishEditorChanges(stem: "related")
        }
    }
}
