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

    var isEmbedded: Bool = false

    @State private var phrases: [Related] = []
    @State private var totalCount: Int = 0
    @State private var page: Int = 0
    @State private var query: String = ""
    @State private var editingCapability: RecordEditingCapability = .readOnly
    @State private var faPingThisSession: Bool?
    @State private var faPingAt: TimeInterval?
    @State private var hasFreshFAEvidence = false
    @State private var faPingObserver: FAPingObserver?
    @State private var faPollTimer: Timer?
    @State private var probeText = ""
    @State private var statusMessage = ""
    @State private var isRefreshingHotSnapshot = false
    @State private var didAttemptHotRefresh = false
    @FocusState private var probeFocused: Bool

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
    private let groupSuite = LIMEPreferenceManager.suiteName

    private var totalPages: Int { max(1, (totalCount + pageSize - 1) / pageSize) }
    private var isLastPage: Bool { page >= totalPages - 1 }
    private var canEdit: Bool { editingCapability == .live }
    private var unlockHint: String { "開啟完整取用以編輯關聯字庫（顯示實際分數）" }
    private var forceLiveEditing: Bool { RecordEditingCapability.forceLiveEditingEnabled() }

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
                      systemImage: canEdit ? "checkmark.circle" : "lock")
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
                            Text(canEdit ? "\(phrase.score)" : "—")
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

                TextField("", text: $probeText)
                    .focused($probeFocused)
                    .frame(width: SettingsMetrics.invisibleProbeSize,
                           height: SettingsMetrics.invisibleProbeSize)
                    .opacity(SettingsMetrics.invisibleProbeOpacity)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .accessibilityHidden(true)
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
            .onAppear {
                ensureFAPingObserver()
                refreshCapability()
                startFAPolling()
                triggerProbeIfNeeded()
                loadPhrases()
            }
            .onDisappear {
                stopFAPolling()
            }
            .onChange(of: probeText) { _ in
                refreshCapability()
            }
            .onChange(of: manageRelatedController.refreshToken) { _ in resetAndLoad() }
            .onReceive(NotificationCenter.default.publisher(
                for: UIApplication.didBecomeActiveNotification)) { _ in
                ensureFAPingObserver()
                refreshCapability()
                startFAPolling()
                triggerProbeIfNeeded()
            }
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
        if isRefreshingHotSnapshot { return "更新實際分數中…" }
        return canEdit ? "完整取用已開啟，顯示實際分數" : unlockHint
    }

    private func refreshCapability() {
        let heartbeat = readKeyboardHeartbeat()
        let faState = FAStateResolver.resolve(heartbeat: heartbeat,
                                              faPingThisSession: faPingThisSession,
                                              faPingAt: faPingAt)
        let next = RecordEditingCapability.resolve(faState: faState)
        editingCapability = next
        hasFreshFAEvidence = FAStateResolver.hasFreshEvidence(heartbeat: heartbeat,
                                                              faPingThisSession: faPingThisSession)
        if next == .live {
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
            if hasFreshFAEvidence {
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
        Task {
            let result = await setupController.refreshTableFromKeyboard(stem: "related")
            isRefreshingHotSnapshot = false
            switch result {
            case .success:
                statusMessage = ""
                loadPhrases()
            case .failure:
                editingCapability = .readOnly
                statusMessage = "即時資料更新逾時，已切換為唯讀。\(unlockHint)"
            }
        }
    }
}
