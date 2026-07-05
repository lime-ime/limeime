// RecordListView.swift
// LimeIME-iOS
//
// Paginated mapping record list with search, add, edit, delete.
// Spec §6.1.

import SwiftUI
import UIKit

// MARK: - RecordListView

struct RecordListView: View {

    let tableName: String
    let imLabel: String

    @EnvironmentObject private var manageImController: ManageImController
    @EnvironmentObject private var setupController: SetupImController
    @EnvironmentObject private var relayActiveState: RelayActiveState
    @Environment(\.scenePhase) private var scenePhase

    @State private var records: [LimeRecord] = []
    @State private var totalCount: Int = 0
    @State private var page: Int = 0
    @State private var query: String = ""
    @State private var searchByCode: Bool = true
    @State private var statusMessage = ""
    @State private var isRefreshingHotSnapshot = false
    @State private var didAttemptHotRefresh = false
    @State private var hotRefreshFailed = false
    @State private var didPublishEditorClose = false

    @State private var showAdd = false
    @State private var editingRecord: IdentifiableRecord?
    @State private var deleteCandidate: IdentifiableRecord?
    @State private var showDeleteConfirm = false

    // Wrapper to make LimeRecord usable as sheet item
    struct IdentifiableRecord: Identifiable {
        var id: String { record.id }
        let record: LimeRecord
    }

    private let pageSize = 100

    private var totalPages: Int { max(1, (totalCount + pageSize - 1) / pageSize) }
    private var isLastPage: Bool { page >= totalPages - 1 }
    private var relayEditingCapability: RecordEditingCapability { relayActiveState.editingCapability }
    private var editingCapability: RecordEditingCapability {
        hotRefreshFailed ? .readOnly : relayEditingCapability
    }
    private var canEdit: Bool { !isRefreshingHotSnapshot && editingCapability == .live }
    private var unlockHint: String { "開啟完整取用並將鍵盤切換至萊姆輸入法以編輯字根資料（顯示實際分數）" }

    var body: some View {
        VStack(spacing: 0) {
            // Search-mode picker
            Picker("搜尋模式", selection: $searchByCode) {
                Text("字根").tag(true)
                Text("文字").tag(false)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.vertical, 6)

            // Inline search field
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.secondary)
                TextField("搜尋", text: $query)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
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
            .padding(.bottom, 6)

            Label(statusMessage.isEmpty ? capabilityMessage : statusMessage,
                  systemImage: capabilityIcon)
                .font(.footnote)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
                .padding(.bottom, 6)

            List {
                ForEach(records, id: \.id) { record in
                    HStack(spacing: 0) {
                        Text(record.code)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.system(.body, design: .monospaced))
                        Text(record.word)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(canEdit ? "\(record.score)" : "—")
                            .frame(width: 48, alignment: .trailing)
                            .foregroundColor(.secondary)
                    }
                    .font(.body)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        guard canEdit else {
                            statusMessage = unlockHint
                            return
                        }
                        editingRecord = IdentifiableRecord(record: record)
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        if canEdit {
                            Button(role: .destructive) {
                                deleteCandidate = IdentifiableRecord(record: record)
                                showDeleteConfirm = true
                            } label: {
                                Label("刪除", systemImage: "trash")
                            }
                        }
                    }
                }
            }
            .listStyle(.plain)
            .redacted(reason: isRefreshingHotSnapshot ? .placeholder : [])
            .disabled(isRefreshingHotSnapshot)

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
        .onChange(of: query) { _ in resetAndLoad() }
        .onChange(of: searchByCode) { _ in resetAndLoad() }
        .navigationTitle(imLabel)
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
        .onAppear {
            refreshHotSnapshotIfNeeded()
            loadRecords()
        }
        .onChange(of: relayActiveState.editingCapability) { _ in
            refreshHotSnapshotIfNeeded()
        }
        .onChange(of: scenePhase) { _ in
            if scenePhase == .background {
                publishEditorCloseIfNeeded()
            }
        }
        .onDisappear { publishEditorCloseIfNeeded() }
        .sheet(isPresented: $showAdd, onDismiss: { loadRecords() }) {
            AddRecordView(tableName: tableName)
        }
        .sheet(item: $editingRecord, onDismiss: { loadRecords() }) { wrapper in
            EditRecordView(tableName: tableName, record: wrapper.record)
        }
        .alert("確認刪除", isPresented: $showDeleteConfirm, presenting: deleteCandidate) { wrapper in
            Button("刪除", role: .destructive) {
                deleteRecord(wrapper.record)
            }
            Button("取消", role: .cancel) {}
        } message: { wrapper in
            Text("確定要刪除「\(wrapper.record.word)」(\(wrapper.record.code))？")
        }
    }

    // MARK: - Data

    /// Full reload: fetches records + count. Use on appear, query change, and after mutations.
    private func loadRecords() {
        Task {
            let result = await manageImController.loadRecords(
                table: tableName, query: query.isEmpty ? nil : query,
                searchByCode: searchByCode, page: page)
            records = result.records
            totalCount = result.total
        }
    }

    /// Page-only reload: fetches records without re-running COUNT. Use for page navigation.
    private func loadPage() {
        Task {
            records = await manageImController.loadPage(
                table: tableName, query: query.isEmpty ? nil : query,
                searchByCode: searchByCode, page: page)
        }
    }

    private func resetAndLoad() {
        page = 0
        loadRecords()   // query changed — need fresh count
    }

    private func changePage(_ newPage: Int) {
        guard newPage >= 0, newPage < totalPages else { return }
        page = newPage
        loadPage()      // total already known — skip COUNT
    }

    private func deleteRecord(_ record: LimeRecord) {
        guard canEdit else { return }
        Task {
            _ = await manageImController.deleteRecord(table: tableName, id: record.id)
            loadRecords()   // count changed — full reload
        }
    }

    private var capabilityMessage: String {
        if isRefreshingHotSnapshot { return "同步中..." }
        return canEdit ? "完整取用已開啟，碼表編輯功能已啓用。" : unlockHint
    }

    private var capabilityIcon: String {
        if isRefreshingHotSnapshot { return "clock.arrow.circlepath" }
        return canEdit ? "checkmark.circle" : "lock"
    }

    private func refreshHotSnapshotIfNeeded() {
        guard !didAttemptHotRefresh, relayEditingCapability == .live else { return }
        didAttemptHotRefresh = true
        isRefreshingHotSnapshot = true
        statusMessage = ""
        Task {
            let result = await setupController.refreshTableFromKeyboard(stem: tableName)
            isRefreshingHotSnapshot = false
            switch result {
            case .success:
                statusMessage = ""
                loadRecords()
            case .failure:
                hotRefreshFailed = true
                statusMessage = "即時資料更新逾時，已切換為唯讀。\(unlockHint)"
            }
        }
    }

    private func publishEditorCloseIfNeeded() {
        guard didAttemptHotRefresh, !isRefreshingHotSnapshot, !didPublishEditorClose, !hotRefreshFailed else { return }
        didPublishEditorClose = true
        // ponytail: background publish closes the only editor/keyboard learning interleave.
        Task {
            _ = await setupController.publishEditorChanges(stem: tableName)
        }
    }
}
