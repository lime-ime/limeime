// LimeSettingsView.swift
// LimeIME-iOS
//
// Root 5-tab TabView. Hosted via UIHostingController from MainViewController.
// Replaces the old 4-tab version per spec §2.

import SwiftUI
import UIKit

// MARK: - Shared UserDefaults for @AppStorage

let sharedDefaults = UserDefaults(suiteName: LIMEPreferenceManager.suiteName)!

// MARK: - RelayProbeField

/// UIKit-backed probe field for the keyboard→app relay. A SwiftUI `TextField`'s
/// programmatically-set text is invisible to a custom keyboard's
/// `documentContextBeforeInput`; a real `UITextField` exposes its `.text` to the proxy,
/// so the keyboard can read the relay token and type its payload back.
struct RelayProbeField: UIViewRepresentable {
    @Binding var text: String
    @Binding var isFocused: Bool

    func makeUIView(context: Context) -> UITextField {
        let tf = UITextField()
        tf.autocorrectionType = .no
        tf.autocapitalizationType = .none
        tf.smartQuotesType = .no
        tf.smartDashesType = .no
        tf.spellCheckingType = .no
        tf.delegate = context.coordinator
        tf.addTarget(context.coordinator,
                     action: #selector(Coordinator.editingChanged(_:)),
                     for: .editingChanged)
        return tf
    }

    func updateUIView(_ tf: UITextField, context: Context) {
        if tf.text != text { tf.text = text }
        // Keep the cursor at the end so the token isn't split oddly (relay reads
        // before+after anyway, but this is tidy).
        if let end = tf.position(from: tf.endOfDocument, offset: 0) {
            tf.selectedTextRange = tf.textRange(from: end, to: end)
        }
        DispatchQueue.main.async {
            if isFocused, !tf.isFirstResponder { tf.becomeFirstResponder() }
            if !isFocused, tf.isFirstResponder { tf.resignFirstResponder() }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UITextFieldDelegate {
        private let parent: RelayProbeField
        init(_ parent: RelayProbeField) { self.parent = parent }

        @objc func editingChanged(_ tf: UITextField) {
            parent.text = tf.text ?? ""
        }

        // A keyboard extension's insertText is a CROSS-PROCESS edit that does not fire
        // .editingChanged; this delegate callback does. Capture the projected text so the
        // app sees the payload (and updateUIView won't overwrite the keyboard's insert).
        func textField(_ tf: UITextField,
                       shouldChangeCharactersIn range: NSRange,
                       replacementString string: String) -> Bool {
            let current = tf.text ?? ""
            if let r = Range(range, in: current) {
                let projected = current.replacingCharacters(in: r, with: string)
                DispatchQueue.main.async { self.parent.text = projected }
            }
            return true
        }
    }
}

// MARK: - LimeSettingsView (5-tab root)

struct LimeSettingsView: View {

    @StateObject private var navManager: NavigationManager
    @StateObject private var progressManager: ProgressManager
    @StateObject private var setupController: SetupImController
    @StateObject private var manageImController: ManageImController
    @StateObject private var manageRelatedController: ManageRelatedController
    @State private var rootRelayText = ""
    @State private var rootRelayFiredAt: TimeInterval?
    @State private var rootRelayPending = false
    @State private var rootRelayDidReceivePayload = false
    @State private var rootFAPingAt: TimeInterval?
    @State private var rootRelayFocused = false
#if DEBUG
    @State private var didRunUITestRestore = false
    @State private var didPrepareRelayOnlyPrefs = false
    @State private var uiTestRestoreStatus: String?
    @State private var uiTestRestoreCounts: String?
    @State private var relayPayloadReceived = false
#endif

    init() {
        let pm = ProgressManager()
        let nav = NavigationManager()
        _progressManager = StateObject(wrappedValue: pm)
        _navManager = StateObject(wrappedValue: nav)
        _setupController = StateObject(wrappedValue: SetupImController(progress: pm))
        _manageImController = StateObject(wrappedValue: ManageImController())
        _manageRelatedController = StateObject(wrappedValue: ManageRelatedController())
    }

    var body: some View {
        TabView(selection: $navManager.selectedTab) {
            SetupTabView()
                .tabItem { Label("設定", systemImage: "gearshape") }
                .tag(0)

            IMListView()
                .tabItem { Label("輸入法", systemImage: "list.bullet") }
                .tag(1)

            PreferencesTabView()
                .tabItem { Label("喜好設定", systemImage: "slider.horizontal.3") }
                .tag(3)

            DBManagerView()
                .tabItem { Label("資料庫", systemImage: "archivebox") }
                .tag(4)
        }
        // iOS 18 introduces a "floating tab bar" at the top of the iPad screen
        // by default. When a child view uses `.searchable`, SwiftUI hoists the
        // search field into the same pill, clipping it on narrower iPads (11").
        // `.sidebarAdaptable` moves tabs to a left sidebar on iPad and keeps
        // the bottom tab bar on iPhone, restoring the detail view's own nav
        // bar for the search field. The modifier is iOS 18+, so guard with
        // `if #available` to keep iOS 16/17 builds working.
        .iOS18SidebarAdaptableTabStyle()
        // LIME-forward re-layout: brand green is the app-wide accent. Tinting
        // the root propagates to every `.accentColor`, `.borderedProminent`
        // button, Link, and `.tint`-based chevron in the settings tabs.
        .tint(SettingsTheme.accent)
        .environmentObject(navManager)
        .environmentObject(progressManager)
        .environmentObject(setupController)
        .environmentObject(manageImController)
        .environmentObject(manageRelatedController)
        .onAppear {
            Task {
                await setupController.seedRelatedIfNeeded()
                manageRelatedController.invalidate()
#if DEBUG
                await runUITestRestoreIfNeeded()
#endif
            }
            // Cold-launch deep link (URL arrived before view appeared).
            if let url = pendingLimeDeepLinkURL {
                pendingLimeDeepLinkURL = nil
                handleDeepLink(url)
            }
            if pendingLimeExternalImportURL != nil {
                navManager.selectTab(1)
            }
            triggerRootRelay()
        }
        .onReceive(NotificationCenter.default.publisher(for: .limeDeepLink)) { note in
            // Warm-launch deep link (app already running).
            if let url = note.object as? URL { handleDeepLink(url) }
        }
        .onReceive(NotificationCenter.default.publisher(for: .limeExternalImport)) { _ in
            // External imports need the IM manager/import screen so the user
            // chooses the destination IM instead of deriving it from filename.
            navManager.selectTab(1)
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            triggerRootRelay()
        }
        .onReceive(NotificationCenter.default.publisher(for: .limeTriggerRelay)) { _ in
            triggerRootRelay()
        }
        .onChange(of: rootRelayText) { _ in
            handleRootRelayTextChange()
        }
        .onReceive(NotificationCenter.default.publisher(for: .limeRelayPayloadReceived)) { _ in
#if DEBUG
            relayPayloadReceived = true
#endif
        }
        .overlay {
            rootRelayProbe
            if progressManager.isVisible {
                ZStack {
                    SettingsTheme.globalOverlayScrim.ignoresSafeArea()
                    VStack(spacing: SettingsMetrics.modalSpacing) {
                        ProgressView()
                            .progressViewStyle(.circular)
                        if !progressManager.status.isEmpty {
                            Text(progressManager.status)
                                .font(.caption)
                                .foregroundColor(.primary)
                        }
                    }
                    .padding(SettingsMetrics.modalPadding)
                    .background(
                        RoundedRectangle(cornerRadius: SettingsMetrics.globalModalCornerRadius)
                            .fill(SettingsTheme.overlayCardBackground)
                            .shadow(radius: SettingsMetrics.modalShadowRadius))
                }
            }
#if DEBUG
            relayPayloadReceivedStatusView
            uiTestRestoreStatusViews
#endif
        }
    }

    private var rootActiveThisSession: Bool {
        FAStateResolver.isActiveThisSession(faPingAt: rootFAPingAt,
                                            probeFiredAt: rootRelayFiredAt)
    }

    @ViewBuilder
    private var rootRelayProbe: some View {
        // UIKit UITextField (not SwiftUI TextField): the keyboard's documentContextProxy
        // does NOT see a SwiftUI TextField's programmatically-set text, so the relay token
        // was invisible to the keyboard. A real UITextField exposes its .text to the proxy.
        RelayProbeField(text: $rootRelayText, isFocused: $rootRelayFocused)
            .frame(width: SettingsMetrics.invisibleProbeSize,
                   height: SettingsMetrics.invisibleProbeSize)
            .opacity(SettingsMetrics.invisibleProbeOpacity)
            .accessibilityHidden(true)
    }

    private func triggerRootRelay() {
#if DEBUG
        if !didPrepareRelayOnlyPrefs {
            RelayPrefSync.prepareRelayOnlyIfNeeded(in: sharedDefaults)
            didPrepareRelayOnlyPrefs = true
        }
#endif
        guard !rootRelayPending else { return }
        let firedAt = Date().timeIntervalSince1970
        rootRelayPending = true
        rootRelayDidReceivePayload = false
        rootRelayFiredAt = firedAt
        rootRelayText = RelayToken.request
        DispatchQueue.main.async {
            rootRelayFocused = true
        }
        // Window covers keyboard cold-load + the round-trip; on a payload the relay
        // finishes immediately, so this is only the not-active timeout.
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
            if rootRelayPending && rootRelayFiredAt == firedAt && !rootActiveThisSession {
                finishRootRelay()
            }
        }
    }

    private func handleRootRelayTextChange() {
        guard let payload = decodeRelayPayload(rootRelayText) else { return }
        RelayPrefSync.apply(han: payload.han,
                            split: payload.split,
                            reverseLookupIM: payload.rlim,
                            reverseLookupValue: payload.rlval,
                            pts: payload.pts,
                            to: sharedDefaults)
        rootRelayDidReceivePayload = true
        let relayFiredAt = rootRelayFiredAt ?? payload.ts
        rootRelayFiredAt = FAStateResolver.isActiveThisSession(faPingAt: payload.ts,
                                                               probeFiredAt: relayFiredAt)
            ? relayFiredAt
            : payload.ts
        rootFAPingAt = payload.ts
#if DEBUG
        relayPayloadReceived = true
#endif
        NotificationCenter.default.post(name: .limeRelayPayloadReceived,
                                        object: nil,
                                        userInfo: ["faOn": payload.faOn,
                                                   "ts": payload.ts,
                                                   "firedAt": rootRelayFiredAt ?? payload.ts,
                                                   "source": "root"])
        finishRootRelay()
    }

    private func finishRootRelay() {
        let resolvedNotActive = rootRelayPending && !rootRelayDidReceivePayload
        rootRelayPending = false
        rootRelayFocused = false
        rootRelayText = ""
        if resolvedNotActive {
            NotificationCenter.default.post(name: .limeRelayResolvedNotActive, object: nil)
        }
    }

    // MARK: - Deep link

    private func handleDeepLink(_ url: URL) {
        guard url.scheme == "limeime" else { return }
        switch url.host {
        case "settings":
            navManager.selectTab(3)   // 喜好設定
        default:
            break
        }
    }

#if DEBUG
    // ponytail: launch-arg restore seam exercises the normal cold restore path after SwiftUI is ready.
    private func runUITestRestoreIfNeeded() async {
        guard !didRunUITestRestore,
              let fileName = UserDefaults.standard.string(forKey: "limeUITestRestoreFromDocuments"),
              !fileName.isEmpty
        else { return }
        didRunUITestRestore = true
        let url = FileManager.default.urls(for: .documentDirectory,
                                           in: .userDomainMask)[0]
            .appendingPathComponent(fileName)
        guard FileManager.default.fileExists(atPath: url.path) else {
            uiTestRestoreStatus = "restore_failed missing \(url.path)"
            return
        }
        let result = await setupController.restoreDB(from: url)
        switch result {
        case .success:
            manageImController.invalidate()
            manageRelatedController.invalidate()
            uiTestRestoreCounts = await setupController.restoredTableCountsForUITest()
            uiTestRestoreStatus = "restore_done \(url.path)"
        case .failure(let error):
            uiTestRestoreStatus = "restore_failed \(error.localizedDescription)"
        }
    }

    @ViewBuilder
    private var relayPayloadReceivedStatusView: some View {
        if relayPayloadReceived {
            Text("relay")
                .frame(width: 1, height: 1)
                .opacity(0.01)
                .accessibilityIdentifier("relayPayloadReceived")
        }
    }

    @ViewBuilder
    private var uiTestRestoreStatusViews: some View {
        if let status = uiTestRestoreStatus {
            Text(status)
                .frame(width: 1, height: 1)
                .opacity(0.01)
                .accessibilityIdentifier(status.hasPrefix("restore_done") ? "restore_done" : "restore_failed")
                .accessibilityLabel(status)
        }
        if let counts = uiTestRestoreCounts {
            Text(counts)
                .frame(width: 1, height: 1)
                .opacity(0.01)
                .accessibilityIdentifier("restore_table_counts")
                .accessibilityLabel(counts)
        }
    }
#endif

}

// MARK: - iOS 18 TabView adaptable-sidebar style helper

private extension View {
    /// Apply `.tabViewStyle(.sidebarAdaptable)` on iOS 18+, no-op on older
    /// systems. Keeps the iPad floating tab bar from hoisting `.searchable`
    /// fields into its pill (where they get clipped on iPad 11").
    @ViewBuilder
    func iOS18SidebarAdaptableTabStyle() -> some View {
        if #available(iOS 18.0, *) {
            self.tabViewStyle(.sidebarAdaptable)
        } else {
            self
        }
    }
}

// MARK: - Constrained detail layout (shared across pushed views)

/// Wraps a pushed detail view with a custom back chevron, static large title,
/// 560pt reading-width cap, and a hidden system nav bar. Every pushed
/// destination under the LimeSettings tabs uses this so the back chevron and
/// title sit at the left edge of the constrained content column (matching
/// the iPad 13" two-column rhythm) instead of floating at the iPad's screen
/// edge.
struct ConstrainedDetailLayout<Trailing: View>: ViewModifier {
    let title: String
    let trailing: () -> Trailing
    private let titleSectionHeight: CGFloat = SettingsMetrics.titleSectionHeight
    @Environment(\.dismiss) private var dismiss

    func body(content: Content) -> some View {
        VStack(spacing: 0) {
            HStack {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(.tint)
                }
                .accessibilityIdentifier("detail_back_button")
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)

            HStack(alignment: .center, spacing: 12) {
                Text(title)
                    .font(.largeTitle.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)

                trailing()
            }
                .frame(height: titleSectionHeight)
                .padding(.horizontal, 20)

            content
        }
        .frame(maxWidth: SettingsMetrics.contentMaxWidth)
        .frame(maxWidth: .infinity)
        .toolbar(.hidden, for: .navigationBar)
    }
}

extension View {
    /// Match SetupTabView's white page with gray grouped blocks for
    /// settings/list-style screens that use SwiftUI List or Form.
    func setupMatchedGroupedSurface() -> some View {
        self
            .scrollContentBackground(.hidden)
            .background(Color(.systemBackground))
    }

    /// Apply SetupTabView's gray block fill to rows inside a List/Form.
    func setupMatchedSectionBlock() -> some View {
        self.listRowBackground(Color(.secondarySystemBackground))
    }

    /// Apply the standard constrained-detail layout (chevron + static title
    /// + 560pt column + hidden system nav bar). No trailing toolbar items.
    func constrainedDetailLayout(_ title: String) -> some View {
        modifier(ConstrainedDetailLayout(title: title, trailing: { EmptyView() }))
    }

    /// Same as above, plus a trailing-aligned action button on the chevron
    /// row (e.g. the refresh button on the IM install list).
    func constrainedDetailLayout<Trailing: View>(
        _ title: String,
        @ViewBuilder trailing: @escaping () -> Trailing
    ) -> some View {
        modifier(ConstrainedDetailLayout(title: title, trailing: trailing))
    }
}
