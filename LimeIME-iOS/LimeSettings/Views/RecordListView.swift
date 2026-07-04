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

    @State private var records: [LimeRecord] = []
    @State private var totalCount: Int = 0
    @State private var page: Int = 0
    @State private var query: String = ""
    @State private var searchByCode: Bool = true
    @State private var editingCapability: RecordEditingCapability = .readOnly
    @State private var faPingThisSession: Bool?
    @State private var faPingAt: TimeInterval?
    @State private var activeProbeFiredAt: TimeInterval?
    @State private var hasFreshFAEvidence = false
    @State private var faPingObserver: FAPingObserver?
    @State private var faPollTimer: Timer?
    @State private var probeText = ""
    @State private var statusMessage = ""
    @State private var isRefreshingHotSnapshot = false
    @State private var didAttemptHotRefresh = false
    @FocusState private var probeFocused: Bool

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
    private let groupSuite = LIMEPreferenceManager.suiteName

    private var totalPages: Int { max(1, (totalCount + pageSize - 1) / pageSize) }
    private var isLastPage: Bool { page >= totalPages - 1 }
    private var canEdit: Bool { editingCapability == .live }
    private var unlockHint: String { "開啟完整取用並將鍵盤切換至萊姆輸入法以編輯字根資料（顯示實際分數）" }
    private var forceLiveEditing: Bool { RecordEditingCapability.forceLiveEditingEnabled() }

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
                  systemImage: canEdit ? "checkmark.circle" : "lock")
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

            TextField("", text: $probeText)
                .focused($probeFocused)
                .frame(width: SettingsMetrics.invisibleProbeSize,
                       height: SettingsMetrics.invisibleProbeSize)
                .opacity(SettingsMetrics.invisibleProbeOpacity)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .accessibilityHidden(true)
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
            ensureFAPingObserver()
            refreshCapability()
            startFAPolling()
            triggerProbeIfNeeded()
            loadRecords()
        }
        .onDisappear {
            stopFAPolling()
        }
        .onChange(of: probeText) { _ in
            refreshCapability()
        }
        .onReceive(NotificationCenter.default.publisher(
            for: UIApplication.didBecomeActiveNotification)) { _ in
            ensureFAPingObserver()
            refreshCapability()
            startFAPolling()
            triggerProbeIfNeeded()
        }
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
        if isRefreshingHotSnapshot { return "更新實際分數中…" }
        return canEdit ? "完整取用已開啟，顯示實際分數" : unlockHint
    }

    private func refreshCapability() {
        let heartbeat = readKeyboardHeartbeat()
        let faState = FAStateResolver.resolve(heartbeat: heartbeat,
                                              faPingThisSession: faPingThisSession,
                                              faPingAt: faPingAt)
        let activeThisSession = FAStateResolver.isActiveThisSession(faPingAt: faPingAt,
                                                                    probeFiredAt: activeProbeFiredAt)
        let next = RecordEditingCapability.resolve(faState: faState,
                                                   activeThisSession: activeThisSession,
                                                   forceLive: forceLiveEditing)
        editingCapability = next
        hasFreshFAEvidence = FAStateResolver.hasFreshEvidence(heartbeat: heartbeat,
                                                              faPingThisSession: faPingThisSession)
        if faState == .confirmedOn || forceLiveEditing {
            refreshHotSnapshotIfNeeded()
        }
    }

    private func readKeyboardHeartbeat() -> KeyboardHeartbeat? {
        guard let baseURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: groupSuite),
              let data = try? Data(contentsOf: SyncPaths.heartbeat(baseURL))
        else { return nil }
        return try? JSONDecoder().decode(KeyboardHeartbeat.self, from: data)
    }

    private func ensureFAPingObserver() {
        guard faPingObserver == nil else { return }
        faPingObserver = FAPingObserver { hasFullAccess in
            faPingThisSession = hasFullAccess
            faPingAt = Date().timeIntervalSince1970
            refreshCapability()
            if hasFreshFAEvidence && !isRefreshingHotSnapshot {
                probeFocused = false
            }
        }
    }

    private func startFAPolling() {
        guard faPollTimer == nil else { return }
        faPollTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            refreshCapability()
        }
    }

    private func stopFAPolling() {
        faPollTimer?.invalidate()
        faPollTimer = nil
    }

    private func triggerProbeIfNeeded() {
        guard !hasFreshFAEvidence else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            if !hasFreshFAEvidence {
                probeFocused = true
            }
        }
    }

    private func refreshHotSnapshotIfNeeded() {
        guard !didAttemptHotRefresh, !forceLiveEditing else { return }
        didAttemptHotRefresh = true
        isRefreshingHotSnapshot = true
        statusMessage = ""
        let probeFiredAt = Date().timeIntervalSince1970
        activeProbeFiredAt = probeFiredAt
        probeFocused = true
        Task {
            try? await Task.sleep(nanoseconds: FAStateResolver.activeProbeWaitNanoseconds)
            guard FAStateResolver.isActiveThisSession(faPingAt: faPingAt,
                                                      probeFiredAt: probeFiredAt) else {
                isRefreshingHotSnapshot = false
                editingCapability = .readOnly
                statusMessage = "請將鍵盤切換至萊姆輸入法後再試"
                probeFocused = false
                return
            }
            let result = await setupController.refreshTableFromKeyboard(stem: tableName)
            isRefreshingHotSnapshot = false
            probeFocused = false
            switch result {
            case .success:
                statusMessage = ""
                loadRecords()
            case .failure:
                editingCapability = .readOnly
                statusMessage = "即時資料更新逾時，已切換為唯讀。\(unlockHint)"
            }
        }
    }
}
