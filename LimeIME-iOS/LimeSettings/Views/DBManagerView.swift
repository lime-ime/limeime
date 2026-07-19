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
    @EnvironmentObject private var relayActiveState: RelayActiveState

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
    @State private var faPingAt: TimeInterval?
    @State private var hasFreshFAEvidence = false
    @State private var faPingObserver: FAPingObserver?
    @State private var syncDoneObserver: SyncSignalObserver?
    @State private var faPollTimer: Timer?
    @State private var backupAwaitingReceipt = false
    @State private var faPingSeenDuringBackup = false
    @State private var backupProbeFiredAt: TimeInterval?
    @State private var probeText = ""
    @FocusState private var probeFocused: Bool
    /// True while a post-restore "sync probe" holds LimeIME on screen so its cold→hot
    /// scan can finish before this app returns to the background. Keeps the FA-evidence
    /// handlers from dismissing the keyboard early.
    @State private var syncProbeActive = false
#if DEBUG
    @State private var uiTestBackupStatus: String?
#endif

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
#if DEBUG
                    uiTestBackupStatusView
#endif
                }
                .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)
                .padding(.bottom, SettingsMetrics.setupBottomPadding)
                .frame(maxWidth: SettingsMetrics.contentMaxWidth)
                .frame(maxWidth: .infinity)
            }
            .background(Color(UIColor.systemBackground).ignoresSafeArea())
            .onAppear {
                ensureFAPingObserver()
                ensureSyncDoneObserver()
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
                if hasFreshEvidence, !syncProbeActive { probeFocused = false }
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
#if DEBUG
        if isUITestColdBackupEnabled { return true }
#endif
        return relayActiveState.editingCapability == .live
    }

    private var backupFooter: String {
        if backupEnabled {
            return "備份包含所有字根、關聯字及喜好設定。"
        }
        if faState == .confirmedOn && relayActiveState.isActive != true {
            return "啓用備份資料庫功能需切換目前鍵盤為萊姆輸入法。"
        }
        return "開啟完整取用權限以備份已學習字詞"
    }

    private func refreshFAState() {
        let heartbeat = readKeyboardHeartbeat()
        faState = FAStateResolver.resolve(heartbeat: heartbeat,
                                          faPingThisSession: faPingThisSession,
                                          faPingAt: faPingAt)
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

    /// Dismiss the sync probe the moment the keyboard signals its cold→hot scan finished —
    /// the sync is complete (guaranteed), so no fixed hold. The 3 s window in triggerSyncProbe
    /// stays only as a fallback if this signal is missed.
    private func ensureSyncDoneObserver() {
        guard syncDoneObserver == nil else { return }
        syncDoneObserver = SyncSignalObserver(signal: .syncScanDone) {
            if syncProbeActive {
                syncProbeActive = false
                probeFocused = false
            }
        }
    }

    private func ensureFAPingObserver() {
        guard faPingObserver == nil else { return }
        faPingObserver = FAPingObserver { hasFullAccess in
            faPingThisSession = hasFullAccess
            faPingAt = Date().timeIntervalSince1970
            if backupAwaitingReceipt {
                faPingSeenDuringBackup = true
            }
            refreshFAState()
            if hasFreshFAEvidence && !backupAwaitingReceipt && !syncProbeActive {
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
                // Auto-dismiss the FA-detection probe keyboard (see SetupTabView). The
                // BACKUP probe (triggerBackupProbe) is intentionally NOT auto-dismissed —
                // backup needs the keyboard running long enough to write its snapshot.
                // ponytail: fixed 1 s probe window; lengthen only if device timing shows the FA ping is slower.
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    if !backupAwaitingReceipt && !syncProbeActive { probeFocused = false }
                }
            }
        }
    }

    private func triggerBackupProbe() {
        let probeFiredAt = Date().timeIntervalSince1970
        backupProbeFiredAt = probeFiredAt
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            probeFocused = true
        }
    }

    // MARK: - Backup

    private func performBackup() {
#if DEBUG
        if isUITestColdBackupEnabled {
            performUITestColdBackup()
            return
        }
#endif
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
            try? await Task.sleep(nanoseconds: FAStateResolver.activeProbeWaitNanoseconds)
            guard FAStateResolver.isActiveThisSession(faPingAt: faPingAt,
                                                      probeFiredAt: backupProbeFiredAt) else {
                backupAwaitingReceipt = false
                isWorking = false
                backupProgress = 0
                preparingShare = false
                backupProbeFiredAt = nil
                probeFocused = false
                statusMessage = "備份失敗：請將鍵盤切換至萊姆輸入法後再試"
                return
            }
            let result = await setupController.backupDBAsync()
            backupAwaitingReceipt = false
            backupProbeFiredAt = nil
            probeFocused = false
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

#if DEBUG
    // ponytail: debug launch arg bypasses FA/keyboard relay for the cold-restore e2e only.
    private var isUITestColdBackupEnabled: Bool {
        let arg = UserDefaults.standard.object(forKey: "limeUITestBackupColdToDocuments")
        return (arg as? String) == "1" || (arg as? Bool) == true
    }

    private func performUITestColdBackup() {
        isWorking = true
        statusMessage = "備份中…"
        uiTestBackupStatus = nil
        Task {
            let result = await setupController.backupColdDBToDocumentsForUITest()
            switch result {
            case .success(let url):
                statusMessage = "資料庫備份完成"
                uiTestBackupStatus = "backup_done \(url.path)"
            case .failure(let error):
                statusMessage = "備份失敗：\(error.localizedDescription)"
                uiTestBackupStatus = "backup_failed \(error.localizedDescription)"
            }
            isWorking = false
        }
    }

    @ViewBuilder
    private var uiTestBackupStatusView: some View {
        if let status = uiTestBackupStatus {
            Text(status)
                .frame(width: 1, height: 1)
                .opacity(0.01)
                .accessibilityIdentifier(status.hasPrefix("backup_done") ? "backup_done" : "backup_failed")
                .accessibilityLabel(status)
        }
    }
#endif

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
                pushRestoredPrefsToInbox()
                triggerSyncProbe()
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
                pushRestoredPrefsToInbox()
                triggerSyncProbe()
            case .failure(let error):
                statusMessage = "還原失敗：\(error.localizedDescription)"
            }
            isWorking = false
        }
    }

    /// §1.8: deliver the just-restored two-writer prefs to the keyboard's hot store via the
    /// one-time inbox — the keyboard reads these from hot, not cold, so a restore must push
    /// them (the active IM included; a restore overrides the live one). Runs before the
    /// probe so the summoned keyboard drains a fresh inbox.
    private func pushRestoredPrefsToInbox() {
        guard let base = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: LIMEPreferenceManager.suiteName),
              let cold = UserDefaults(suiteName: LIMEPreferenceManager.suiteName) else { return }
        try? PrefInbox.write(base: base, defaults: cold,
                             hanConvert: cold.object(forKey: "han_convert_option") as? Int,
                             splitKeyboard: cold.object(forKey: "split_keyboard_mode") as? Int,
                             phonePortraitMode: cold.object(forKey: "phone_portrait_keyboard_mode") as? Int,
                             phoneLandscapeSplit: cold.object(forKey: "phone_landscape_split") as? Bool,
                             activeIM: cold.string(forKey: "active_im")
                                       ?? cold.string(forKey: "keyboard_list"))
        for (key, value) in cold.dictionaryRepresentation()
        where key.hasSuffix("_im_reverselookup") {
            guard let v = value as? String else { continue }
            let im = String(key.dropLast("_im_reverselookup".count))
            try? PrefInbox.write(base: base, defaults: cold, reverseLookup: (im: im, value: v))
        }
    }

    /// After a restore, summon LimeIME with the invisible probe so the extension runs
    /// and performs the cold→hot sync now — before the user leaves this app — so the
    /// active IM shows on the first keyboard appearance instead of only after a re-open.
    private func triggerSyncProbe() {
        syncProbeActive = true
        probeFocused = true
        // The keyboard now rings `.syncScanDone` when its scan finishes, so the probe
        // dismisses via ensureSyncDoneObserver — usually well under a second, and only after
        // the sync is actually complete. This fixed window is a FALLBACK for the rare miss
        // (extension failed to launch); the scan is idempotent and re-runs on next appearance.
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
            syncProbeActive = false
            probeFocused = false
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
