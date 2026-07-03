// DBManagerView.swift
// LimeIME-iOS
//
// DB backup and restore — the 資料庫 tab.
// Spec §7.

import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - DBManagerView

struct DBManagerView: View {

    @EnvironmentObject private var setupController: SetupImController
    @EnvironmentObject private var manageImController: ManageImController
    @EnvironmentObject private var manageRelatedController: ManageRelatedController

    @State private var statusMessage: String = ""
    @State private var isWorking = false
    @State private var showRestoreConfirm = false
    @State private var showInitConfirm = false
    @State private var showFilePicker = false
    @State private var backupURL: URL?
    @State private var showShareSheet = false
    @State private var backupProgress: Double = 0
    @State private var preparingShare = false
    @State private var faState: FAState = .unknown
    @State private var faPingThisSession: Bool?
    @State private var hasFreshFAEvidence = false
    @State private var faPingObserver: FAPingObserver?
    @State private var faPollTimer: Timer?
    @State private var backupAwaitingReceipt = false
    @State private var faPingSeenDuringBackup = false
    @State private var probeText = ""
    @FocusState private var probeFocused: Bool

    private let groupSuite = LIMEPreferenceManager.suiteName

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("資料庫管理")
                        .font(.largeTitle).bold()
                        .padding(.top, SettingsMetrics.titleTopPadding)
                        .padding(.bottom, 20)

                    // 備份 — filled (primary) action.
                    dbAction(footer: backupFooter) {
                        Button { performBackup() } label: {
                            Label("備份資料庫", systemImage: "square.and.arrow.up")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                        .disabled(isWorking || !backupEnabled)
                    }

                    // 還原 — bordered action.
                    dbAction(footer: "還原後鍵盤將重新載入資料庫。") {
                        Button { showRestoreConfirm = true } label: {
                            Label("還原資料庫", systemImage: "arrow.down.circle")
                        }
                        .buttonStyle(LimeTonalButtonStyle())
                    }

                    // 初始資料庫 — bordered destructive action + red warning footer.
                    dbAction(
                        footer: "警告：將清除目前所有輸入法資料表，還原為萊姆內建的空白預設資料庫，此動作無法復原。",
                        warning: true
                    ) {
                        Button { showInitConfirm = true } label: {
                            Label("還原預設資料庫", systemImage: "arrow.counterclockwise.circle")
                        }
                        .buttonStyle(LimeTonalButtonStyle(tint: SettingsTheme.destructive))
                    }

                    if !statusMessage.isEmpty {
                        Label(statusMessage, systemImage: "info.circle")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                            .padding(.top, 4)
                            .padding(.horizontal, SettingsMetrics.formHeaderLeadingPadding)
                    }

                    TextField("", text: $probeText)
                        .focused($probeFocused)
                        .frame(width: SettingsMetrics.invisibleProbeSize,
                               height: SettingsMetrics.invisibleProbeSize)
                        .opacity(SettingsMetrics.invisibleProbeOpacity)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .accessibilityHidden(true)
                }
                .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)
                .padding(.bottom, SettingsMetrics.setupBottomPadding)
                .frame(maxWidth: SettingsMetrics.contentMaxWidth)
                .frame(maxWidth: .infinity)
            }
            .background(Color(UIColor.systemBackground).ignoresSafeArea())
            .onAppear {
                ensureFAPingObserver()
                refreshFAState()
                startFAPolling()
                triggerProbeIfNeeded()
            }
            .onDisappear {
                stopFAPolling()
            }
            .onChange(of: probeText) { _ in
                refreshFAState()
            }
            .onChange(of: hasFreshFAEvidence) { hasFreshEvidence in
                if hasFreshEvidence { probeFocused = false }
            }
            .onReceive(NotificationCenter.default.publisher(
                for: UIApplication.didBecomeActiveNotification)) { _ in
                ensureFAPingObserver()
                refreshFAState()
                startFAPolling()
                triggerProbeIfNeeded()
            }
            // Static inline title only (matches the other tab roots). Hide the
            // system nav bar so the title doesn't render twice on iPhone.
            .toolbar(.hidden, for: .navigationBar)
            .alert("確認還原", isPresented: $showInitConfirm) {
                Button("還原", role: .destructive) { restoreBundledDatabase() }
                Button("取消", role: .cancel) {}
            } message: {
                Text("還原後目前所有資料將被取代，確定繼續？")
            }
            .alert("確認還原", isPresented: $showRestoreConfirm) {
                Button("還原", role: .destructive) { showFilePicker = true }
                Button("取消", role: .cancel) {}
            } message: {
                Text("還原後目前所有資料將被取代，確定繼續？")
            }
            .fileImporter(
                isPresented: $showFilePicker,
                allowedContentTypes: [.item],
                allowsMultipleSelection: false
            ) { result in
                if case .success(let urls) = result, let url = urls.first {
                    performRestore(from: url)
                }
            }
            .sheet(isPresented: $showShareSheet, onDismiss: {
                // Sheet dismissed by user — release the overlay we kept up
                // through UIActivityViewController init (which can block the
                // main thread for several seconds on a large backup zip).
                isWorking = false
                backupProgress = 0
                preparingShare = false
                cleanupBackup()
            }) {
                if let url = backupURL { ShareSheet(activityItems: [url]) }
            }
            .overlay {
                if shouldShowLocalWorkingOverlay {
                    ZStack {
                        SettingsTheme.overlayScrim.ignoresSafeArea()
                        VStack(spacing: SettingsMetrics.modalSpacing) {
                            if preparingShare {
                                ProgressView("準備備份中…")
                            } else if backupProgress > 0 {
                                Text("備份中… \(Int(backupProgress * 100))%")
                                ProgressView(value: backupProgress)
                                    .frame(width: SettingsMetrics.progressBarWidth)
                            } else {
                                ProgressView("處理中…")
                            }
                        }
                        .padding(SettingsMetrics.modalPadding)
                        .background(RoundedRectangle(cornerRadius: SettingsMetrics.modalCornerRadius)
                            .fill(SettingsTheme.overlayCardBackground)
                            .shadow(radius: SettingsMetrics.modalShadowRadius))
                    }
                }
            }
        }
    }

    /// A DB action: a full-width button above a supporting footer. The
    /// 還原預設資料庫 footer is a red warning carrying a leading triangle glyph.
    /// Mirrors the design kit's DBTab `Action` (button + footer, no grouped
    /// section header — the button labels are self-explanatory).
    @ViewBuilder
    private func dbAction<Content: View>(
        footer: String,
        warning: Bool = false,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            content()
            HStack(alignment: .top, spacing: 6) {
                if warning {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.caption2)
                }
                Text(footer)
            }
            .font(.footnote)
            .foregroundColor(warning ? SettingsTheme.destructive : .secondary)
            .padding(.horizontal, SettingsMetrics.formHeaderLeadingPadding)
        }
        .padding(.bottom, SettingsMetrics.dbActionBottomSpacing)
    }

    private var backupEnabled: Bool {
        faState == .confirmedOn
    }

    private var backupFooter: String {
        backupEnabled
            ? "備份包含已學習字詞與喜好設定。"
            : "開啟完整取用權限以備份已學習字詞"
    }

    private func refreshFAState() {
        let heartbeat = readKeyboardHeartbeat()
        faState = FAStateResolver.resolve(heartbeat: heartbeat,
                                          faPingThisSession: faPingThisSession)
        hasFreshFAEvidence = FAStateResolver.hasFreshEvidence(heartbeat: heartbeat,
                                                              faPingThisSession: faPingThisSession)
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
            if backupAwaitingReceipt {
                faPingSeenDuringBackup = true
            }
            refreshFAState()
            if hasFreshFAEvidence {
                probeFocused = false
            }
        }
    }

    private func startFAPolling() {
        guard faPollTimer == nil else { return }
        faPollTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            refreshFAState()
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

    private func triggerBackupProbe() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            probeFocused = true
        }
    }

    // MARK: - Backup

    private func performBackup() {
        guard backupEnabled else { return }
        var presentationState = BackupSharePresentationState(
            isWorking: isWorking,
            backupProgress: backupProgress,
            preparingShare: preparingShare,
            showShareSheet: showShareSheet)
        presentationState.startBackup()
        apply(presentationState)
        backupAwaitingReceipt = true
        faPingSeenDuringBackup = false
        triggerBackupProbe()
        Task {
            let result = await setupController.backupDBAsync()
            backupAwaitingReceipt = false
            switch result {
            case .success(let dest):
                refreshFAState()
                var presentationState = BackupSharePresentationState(
                    isWorking: self.isWorking,
                    backupProgress: self.backupProgress,
                    preparingShare: self.preparingShare,
                    showShareSheet: self.showShareSheet)
                presentationState.finishBackupAndPresentShare()
                self.apply(presentationState)
                self.backupURL = dest
                self.statusMessage = "資料庫備份完成"
            case .failure(let error):
                self.isWorking = false
                self.backupProgress = 0
                self.preparingShare = false
                self.statusMessage = backupFailureMessage(error)
            }
        }
    }

    private func backupFailureMessage(_ error: Error) -> String {
        if let setupError = error as? SetupImControllerError,
           case .backupTimedOut = setupError {
            return faPingSeenDuringBackup
                ? "備份失敗：開啟完整取用權限以備份已學習字詞"
                : "備份失敗：請將鍵盤切換至萊姆輸入法後再試"
        }
        return "備份失敗：\(error.localizedDescription)"
    }

    private func apply(_ presentationState: BackupSharePresentationState) {
        isWorking = presentationState.isWorking
        backupProgress = presentationState.backupProgress
        preparingShare = presentationState.preparingShare
        showShareSheet = presentationState.showShareSheet
    }

    private func cleanupBackup() {
        if let url = backupURL {
            try? FileManager.default.removeItem(at: url)
            backupURL = nil
        }
    }

    private var shouldShowLocalWorkingOverlay: Bool {
        isWorking && (preparingShare || backupProgress > 0)
    }

    // MARK: - Init DB

    private func restoreBundledDatabase() {
        isWorking = true
        statusMessage = "還原中…"
        Task {
            let result = await setupController.restoreBundledDatabase()
            switch result {
            case .success(let msg):
                statusMessage = msg
                manageImController.invalidate()
            case .failure:
                statusMessage = "還原失敗"
            }
            isWorking = false
        }
    }

    // MARK: - Restore

    private func performRestore(from url: URL) {
        isWorking = true
        statusMessage = "還原中…"
        Task {
            let result = await setupController.restoreDB(from: url)
            switch result {
            case .success:
                statusMessage = "資料庫還原完成"
                manageImController.invalidate()
                manageRelatedController.invalidate()
            case .failure(let error):
                statusMessage = "還原失敗：\(error.localizedDescription)"
            }
            isWorking = false
        }
    }
}

struct BackupSharePresentationState {
    var isWorking = false
    var backupProgress = 0.0
    var preparingShare = false
    var showShareSheet = false

    mutating func startBackup() {
        isWorking = true
        backupProgress = 0
        preparingShare = true
        showShareSheet = false
    }

    mutating func finishBackupAndPresentShare() {
        isWorking = false
        backupProgress = 0
        preparingShare = false
        showShareSheet = true
    }
}

// MARK: - UIKit Share Sheet bridge

struct ShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
