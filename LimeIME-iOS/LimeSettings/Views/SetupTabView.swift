// SetupTabView.swift
// LimeIME-iOS
//
// App Setup tab — keyboard activation guide, status detection, about.
// Spec §4.  Gboard-inspired layout: logo → status → step list → CTA → about.

import SwiftUI
import UIKit
import SafariServices

// MARK: - FormSectionGroupBoxStyle

/// Makes a GroupBox look identical to a SwiftUI Form Section (grouped style):
/// white secondarySystemGroupedBackground fill, 10-pt corner radius, standard row padding.
struct FormSectionGroupBoxStyle: GroupBoxStyle {
    func makeBody(configuration: Configuration) -> some View {
        VStack(spacing: 0) {
            configuration.content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, SettingsMetrics.groupedSectionHorizontalPadding)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: SettingsMetrics.groupedSectionCornerRadius))
    }
}

// MARK: - ToggleSwitchIcon

/// Green ON-state toggle that matches the iOS Settings keyboard-enable toggle.
private struct ToggleSwitchIcon: View {
    var body: some View {
        ZStack(alignment: .trailing) {
            Capsule()
                .fill(SettingsTheme.switchTrack)
                .frame(width: SettingsMetrics.switchTrackWidth,
                       height: SettingsMetrics.switchTrackHeight)
            Circle()
                .fill(SettingsTheme.switchThumb)
                .shadow(color: SettingsTheme.switchShadow,
                        radius: SettingsMetrics.switchShadowRadius,
                        x: 0,
                        y: SettingsMetrics.switchShadowY)
                .frame(width: SettingsMetrics.switchThumbSize,
                       height: SettingsMetrics.switchThumbSize)
                .padding(.trailing, SettingsMetrics.switchThumbTrailingPadding)
        }
    }
}

// MARK: - SetupStepRow

private struct SetupStepRow<Icon: View>: View {
    let text: String
    @ViewBuilder let icon: Icon

    var body: some View {
        HStack(spacing: SettingsMetrics.setupStepSpacing) {
            icon
                .frame(width: SettingsMetrics.setupStepIconWidth, alignment: .center)
            Text(text)
                .font(.body)
            Spacer()
        }
    }
}

// MARK: - KeyboardSettingsPreviewRow

private struct KeyboardSettingsPreviewRow: View {
    let text: String
    var showsKeyboardIcon = false

    var body: some View {
        HStack(spacing: SettingsMetrics.setupStepSpacing) {
            if showsKeyboardIcon {
                Image(systemName: "keyboard")
                    .font(.body.weight(.medium))
                    .foregroundColor(.white)
                    .frame(width: SettingsMetrics.setupStepIconWidth,
                           height: SettingsMetrics.setupStepIconWidth)
                    .background(Color(uiColor: .systemGray3))
                    .clipShape(RoundedRectangle(cornerRadius: 7))
            }

            Text(text)
                .font(.body)

            Spacer()

            ToggleSwitchIcon()
        }
        .padding(.vertical, SettingsMetrics.rowVerticalPadding)
        .padding(.horizontal, SettingsMetrics.groupedSectionHorizontalPadding)
    }
}

// MARK: - LinkChip

/// One equal-width chip in the About footer: an icon over a brand-accent label.
/// `inApp` chips (使用手冊 / 版權說明) open the page IN-PLACE via an in-app Safari
/// sheet so the user stays in the app; external chips (原始碼) leave for Safari
/// and carry a small up-right arrow to signal that. Spec §4.1.
private struct LinkChip: View {
    let title: String
    let systemImage: String
    let destination: URL
    var inApp: Bool = false

    @State private var showInAppPage = false

    var body: some View {
        Group {
            if inApp {
                // fullScreenCover (not .sheet) so the in-app browser fills the
                // screen on iPad too — a .sheet shows as a small centered card
                // there. SFSafariViewController carries its own Done button.
                Button { showInAppPage = true } label: { chipLabel(showArrow: false) }
                    .fullScreenCover(isPresented: $showInAppPage) {
                        SafariView(url: destination).ignoresSafeArea()
                    }
            } else {
                Link(destination: destination) { chipLabel(showArrow: true) }
            }
        }
        .tint(SettingsTheme.accent)
    }

    @ViewBuilder
    private func chipLabel(showArrow: Bool) -> some View {
        VStack(spacing: SettingsMetrics.aboutChipInnerSpacing) {
            Image(systemName: systemImage)
                .font(.title3)
            HStack(spacing: 3) {
                Text(title)
                    .font(.subheadline.weight(.medium))
                if showArrow {
                    Image(systemName: "arrow.up.right")
                        .font(.caption2)
                        .opacity(0.6)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, SettingsMetrics.aboutChipVerticalPadding)
        .padding(.horizontal, SettingsMetrics.aboutChipHorizontalPadding)
        .background(Color(.quaternarySystemFill),
                    in: RoundedRectangle(cornerRadius: SettingsMetrics.aboutChipCornerRadius))
    }
}

// MARK: - SafariView (in-app Safari)

/// Presents a URL in an in-app `SFSafariViewController` so the user stays within
/// the app (used by the 使用手冊 / 版權說明 About chips).
private struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

// MARK: - SetupTabView

struct SetupTabView: View {

    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var manageImController: ManageImController
    @EnvironmentObject private var navManager: NavigationManager

    // §4.3 — Installed-IM status. Mirrors the 輸入法 tab so a missing/disabled
    // IM surfaces on the first screen and the CTA routes straight to the fix.
    @State private var imCount = 0
    @State private var imAnyEnabled = false

    // keyboardEnabled: checked via the system AppleKeyboards bundle-ID list.
    // Active/current-keyboard status is a separate relay probe.
    @State private var keyboardEnabled   = false
    @State private var faState: FAState = .unknown
    @State private var faPingThisSession: Bool?
    @State private var hasFreshFAEvidence = false
    @State private var faPingObserver: FAPingObserver?
    @State private var faPingAt: TimeInterval?
    @State private var activeProbeFiredAt: TimeInterval?
    @State private var activeProbeMode: ActiveKeyboardProbeMode = .automatic
    @State private var activeProbePending = false
    @State private var activating = false

    @State private var pollTimer: Timer?

    // PrimaryLanguage from LimeKeyboard/Info.plist
    private let groupSuite   = LIMEPreferenceManager.suiteName
    private let githubURL        = URL(string: "https://github.com/lime-ime/limeime")!
    private let manualURL        = URL(string: "https://lime-ime.github.io/limeime/pages/index.html")!
    private let licenseURL       = URL(string: "https://lime-ime.github.io/limeime/pages/license.html")!

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: SettingsMetrics.pageHorizontalPadding) {

                    // ── Brand hero (logo beside wordmark, centered) ───────
                    HStack(spacing: SettingsMetrics.setupHeroSpacing) {
                        logoImage
                        Text("萊姆輸入法")
                            .font(.system(size: SettingsMetrics.setupWordmarkFontSize, weight: .bold))
                    }
                    .padding(.top, SettingsMetrics.setupHeroTopPadding)

                    // ── Title ─────────────────────────────────────────────
                    Text("設定萊姆輸入法")
                        .font(.system(size: SettingsMetrics.setupTitleFontSize, weight: .bold))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                    // ── Full Access status banner ─────────────────────────
                    fullAccessStatusBanner
                        .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                    if fullAccessBannerState != .fullyEnabled {
                        // ── Step list ─────────────────────────────────────
                        VStack(alignment: .leading, spacing: SettingsMetrics.setupListSpacing) {
                            SetupStepRow(text: "點「前往設定」後，輕觸「鍵盤」，開啓萊姆輸入法與允許完整取用（建議）") {
                                Image(systemName: "keyboard")
                                    .font(.title3)
                                    .foregroundColor(.accentColor)
                            }

                            VStack(spacing: 0) {
                                KeyboardSettingsPreviewRow(text: "萊姆輸入法")

                                Divider()
                                    .padding(.leading, SettingsMetrics.groupedSectionHorizontalPadding)

                                KeyboardSettingsPreviewRow(text: "允許完整取用",
                                                           showsKeyboardIcon: true)
                            }
                            .background(Color(UIColor.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: SettingsMetrics.groupedSectionCornerRadius))
                        }
                        .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                        // ── Explanatory note (hidden once Full Access is on) ─
                        Text("開啓完整取用以啓用備份資料庫、輸入法碼表編輯、按鍵震動回饋。不開啟也能正常輸入與安裝輸入法。")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                        // ── CTA button (full-width tonal — same legible style as the
                        //    資料庫 restore buttons; readable in dark mode). ─────
                        Button {
                            openLimeKeyboardSettings()
                        } label: {
                            Text("前往設定")
                        }
                        .buttonStyle(LimeTonalButtonStyle())
                        .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                        settingsGuidance
                    }

                    // ── Active-keyboard status + CTA — placed BELOW the keyboard
                    //    activation steps: once LIME is enabled, switch to it as
                    //    the current keyboard. Hidden until the keyboard is enabled.
                    activeKeyboardStatusBanner
                        .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                    // ── Installed-IM status (§4.3) ────────────────────────
                    imStatusSection
                        .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                    // ── About footer ──────────────────────────────────────
                    // Three equal-width link chips (使用手冊 / 版權說明 / 原始碼)
                    // above a one-line copyright banner. Replaces the old grouped
                    // list whose lone left-aligned GitHub row looked inconsistent.
                    VStack(spacing: SettingsMetrics.aboutFooterSpacing) {
                        // Full-bleed separator (extends past the 24pt page inset).
                        Divider()
                            .padding(.horizontal, -SettingsMetrics.pageHorizontalPadding)
                        HStack(spacing: SettingsMetrics.aboutChipSpacing) {
                            LinkChip(title: "使用手冊", systemImage: "book", destination: manualURL, inApp: true)
                            LinkChip(title: "版權說明", systemImage: "doc.text", destination: licenseURL, inApp: true)
                            LinkChip(title: "原始碼", systemImage: "chevron.left.forwardslash.chevron.right", destination: githubURL)
                        }
                        Text("© LIME 萊姆輸入法 \(copyrightLine())")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.top, SettingsMetrics.aboutCopyrightTopPadding)
                    }
                    .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)
                    .padding(.top, SettingsMetrics.aboutFooterTopPadding)
                    .padding(.bottom, SettingsMetrics.setupBottomPadding)
                }
                // iPad / wide-screen reading-width cap: keeps the form column
                // at a comfortable width instead of stretching edge-to-edge in
                // iPad portrait and (especially) landscape. On iPhone this cap
                // never engages because the screen is narrower than 560pt.
                .frame(maxWidth: SettingsMetrics.contentMaxWidth)
                .frame(maxWidth: .infinity)
            }
            .navigationBarHidden(true)
            .onAppear {
                ensureFAPingObserver()
                refreshStatus()
                refreshIMStatus()
                startPolling()
                triggerRootRelayIfNeeded()
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    refreshStatus()
                    refreshIMStatus()
                    startPolling()
                    triggerRootRelayIfNeeded()
                } else if phase == .background {
                    stopPolling()
                }
            }
            .onChange(of: hasFreshFAEvidence) { hasFreshEvidence in
                if hasFreshEvidence && activeThisSession { finishActiveProbe() }
            }
            .onChange(of: manageImController.refreshToken) { _ in refreshIMStatus() }
            .onReceive(NotificationCenter.default.publisher(for: .limeRelayPayloadReceived)) { note in
                handleRelayPayloadNotification(note)
            }
            .onReceive(NotificationCenter.default.publisher(for: .limeRelayResolvedNotActive)) { _ in
                activeProbePending = false
                activating = false
            }
            // scenePhase → .active is unreliable when SwiftUI is hosted in a
            // UIHostingController under a UIKit SceneDelegate, so the §4.3 IM
            // count could stay stale (showing 0) after returning from system
            // Settings. didBecomeActiveNotification fires reliably on every
            // foreground activation and is delivered on the main actor in the
            // view context, where the @EnvironmentObject and @State are valid.
            .onReceive(NotificationCenter.default.publisher(
                for: UIApplication.didBecomeActiveNotification)) { _ in
                ensureFAPingObserver()
                refreshStatus()
                refreshIMStatus()
                startPolling()
                triggerRootRelayIfNeeded()
            }
        }
    }

    // MARK: - Installed-IM status (§4.3)

    /// Three states derived from the installed IM list:
    ///   none     → no IM table installed     → CTA「安裝輸入法」 → IM tab (install)
    ///   disabled → ≥1 installed, all disabled → CTA「啟用輸入法」 → IM tab (list)
    ///   ok       → ≥1 installed & enabled     → no CTA
    private enum IMStatusState { case none, disabled, ok }

    private var imStatusState: IMStatusState {
        if imCount == 0 { return .none }
        return imAnyEnabled ? .ok : .disabled
    }

    @ViewBuilder
    private var imStatusSection: some View {
        VStack(spacing: SettingsMetrics.aboutFooterSpacing) {
            Label(imStatusText, systemImage: imStatusSymbol)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(imStatusInk)
                .padding(.vertical, SettingsMetrics.statusVerticalPadding)
                .padding(.horizontal, SettingsMetrics.statusHorizontalPadding)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(imStatusTint)
                .clipShape(RoundedRectangle(cornerRadius: SettingsMetrics.groupedSectionCornerRadius))

            if let cta = imStatusCTA {
                Button { navManager.selectTab(1) } label: { Text(cta) }
                    .buttonStyle(LimeTonalButtonStyle())
            }
        }
    }

    private var imStatusText: String {
        switch imStatusState {
        case .none:     return "尚未安裝任何輸入法"
        case .disabled: return "已安裝 \(imCount) 個輸入法，但全部停用"
        case .ok:       return "已安裝 \(imCount) 個輸入法"
        }
    }

    private var imStatusSymbol: String {
        switch imStatusState {
        case .none:     return "xmark.circle.fill"
        case .disabled: return "exclamationmark.triangle.fill"
        case .ok:       return "checkmark.circle.fill"
        }
    }

    private var imStatusInk: Color {
        switch imStatusState {
        case .none:     return SettingsTheme.dangerInk
        case .disabled: return SettingsTheme.warningInk
        case .ok:       return SettingsTheme.successInk
        }
    }

    private var imStatusTint: Color {
        switch imStatusState {
        case .none:     return SettingsTheme.statusTintRed
        case .disabled: return SettingsTheme.statusTintOrange
        case .ok:       return SettingsTheme.statusTintGreen
        }
    }

    private var imStatusCTA: String? {
        switch imStatusState {
        case .none:     return "安裝輸入法"
        case .disabled: return "啟用輸入法"
        case .ok:       return nil
        }
    }

    private func refreshIMStatus() {
        Task {
            let configs = await manageImController.loadIMList()
            imCount = configs.count
            imAnyEnabled = configs.contains { $0.enabled }
        }
    }

    // MARK: - Logo

    @ViewBuilder
    private var logoImage: some View {
        // Transparent-background brand logo (adapts to light/dark) — NOT the
        // white-background app icon. Source: Resources/Limeicon/Icon.png.
        if UIImage(named: "LimeLogo") != nil {
            Image("LimeLogo")
                .resizable()
                .scaledToFit()
                .frame(width: SettingsMetrics.setupLogoSize,
                       height: SettingsMetrics.setupLogoSize)
        } else {
            Image(systemName: "keyboard.fill")
                .resizable()
                .scaledToFit()
                .frame(width: SettingsMetrics.setupFallbackLogoSize,
                       height: SettingsMetrics.setupFallbackLogoSize)
                .padding(SettingsMetrics.setupFallbackLogoPadding)
                .foregroundColor(.accentColor)
                .background(Color(.quaternarySystemFill))
                .clipShape(RoundedRectangle(cornerRadius: SettingsMetrics.setupLogoCornerRadius))
        }
    }

    // MARK: - Status banners

    // Status banners (§4.2): Banner 1 is Full Access only; Banner 2 is the
    // active-keyboard axis and owns the switch-keyboard CTA.
    private var fullAccessStatusBanner: some View {
        statusBanner(text: fullAccessStatusText,
                     systemImage: fullAccessStatusSymbol,
                     ink: fullAccessStatusInk,
                     tint: fullAccessStatusTint)
    }

    @ViewBuilder
    private var activeKeyboardStatusBanner: some View {
        switch activeKeyboardBannerState {
        case .hidden:
            EmptyView()
        case .checking:
            statusBanner(text: "萊姆輸入法檢查中…",
                         systemImage: "hourglass",
                         ink: .secondary,
                         tint: Color(UIColor.secondarySystemBackground))
        case .notActive:
            statusBanner(text: "已啟用，但尚未切換萊姆輸入法",
                         systemImage: "xmark.circle.fill",
                         ink: SettingsTheme.dangerInk,
                         tint: SettingsTheme.statusTintRed) {
                Button { activateKeyboard() } label: { Text("選用萊姆輸入法") }
                    .buttonStyle(LimeTonalButtonStyle())

                Text("長按 🌐 選用萊姆輸入法")
                    .font(.footnote)
                    .foregroundColor(.secondary)

                if faState == .confirmedOn && !activeThisSession {
                    Text("啓用備份資料庫與輸入法碼表編輯需切換目前鍵盤為萊姆輸入法。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
            }
        case .active:
            statusBanner(text: "萊姆輸入法已啟用且為目前輸入法 ✓",
                         systemImage: "checkmark.circle.fill",
                         ink: SettingsTheme.successInk,
                         tint: SettingsTheme.statusTintGreen)
        }
    }

    private func statusBanner(text: String,
                              systemImage: String,
                              ink: Color,
                              tint: Color) -> some View {
        statusBanner(text: text, systemImage: systemImage, ink: ink, tint: tint) {
            EmptyView()
        }
    }

    private func statusBanner<Content: View>(text: String,
                                             systemImage: String,
                                             ink: Color,
                                             tint: Color,
                                             @ViewBuilder extra: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            // Only the status label carries the tinted background — any button or
            // notes below sit outside the colored card.
            Label(text, systemImage: systemImage)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, SettingsMetrics.statusVerticalPadding)
                .padding(.horizontal, SettingsMetrics.statusHorizontalPadding)
                .background(tint)
                .clipShape(RoundedRectangle(cornerRadius: SettingsMetrics.groupedSectionCornerRadius))

            extra()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var fullAccessStatusText: String {
        switch fullAccessBannerState {
        case .notEnabled:           return "尚未啟用萊姆輸入法鍵盤"
        case .enabledNoFullAccess:  return "萊姆輸入法已啓用，完整取用未開啓"
        case .activeNoFullAccess:   return "萊姆輸入法已啓用，完整取用未開啓"
        case .fullyEnabled:         return "萊姆輸入法已啓用、完整取用已開啓 ✓"
        }
    }

    private var fullAccessStatusSymbol: String {
        switch fullAccessBannerState {
        case .notEnabled:           return "xmark.circle.fill"
        case .enabledNoFullAccess:  return "info.circle.fill"
        case .activeNoFullAccess:   return "info.circle.fill"
        case .fullyEnabled:         return "checkmark.circle.fill"
        }
    }

    private var fullAccessStatusInk: Color {
        switch fullAccessBannerState {
        case .notEnabled:           return SettingsTheme.dangerInk
        case .enabledNoFullAccess:  return SettingsTheme.warningInk
        case .activeNoFullAccess:   return SettingsTheme.warningInk
        case .fullyEnabled:         return SettingsTheme.successInk
        }
    }

    private var fullAccessStatusTint: Color {
        switch fullAccessBannerState {
        case .notEnabled:           return SettingsTheme.statusTintRed
        case .enabledNoFullAccess:  return SettingsTheme.statusTintOrange
        case .activeNoFullAccess:   return SettingsTheme.statusTintOrange
        case .fullyEnabled:         return SettingsTheme.statusTintGreen
        }
    }

    @ViewBuilder
    private var settingsGuidance: some View {
        if fullAccessBannerState != .fullyEnabled {
            Text("若設定未直接顯示萊姆輸入法，請到「設定」>「Apps」>「萊姆輸入法」>「Keyboards」，開啟萊姆輸入法與允許完整取用。")
                .font(.footnote)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)
        }
    }

    // MARK: - Detection

    private var fullAccessBannerState: FullAccessBannerState {
        SetupDetection.fullAccessBannerState(keyboardEnabled: keyboardEnabled,
                                             faConfirmedOn: faState == .confirmedOn,
                                             activeThisSession: activeThisSession)
    }

    private var activeKeyboardBannerState: ActiveKeyboardBannerState {
        SetupDetection.activeKeyboardBannerState(keyboardEnabled: keyboardEnabled,
                                                activeThisSession: activeThisSession,
                                                probePending: activeProbePending)
    }

    private var activeThisSession: Bool {
        #if DEBUG
        if SetupDetection.forceNotActive() { return false }
        #endif
        return FAStateResolver.isActiveThisSession(faPingAt: faPingAt,
                                                   probeFiredAt: activeProbeFiredAt,
                                                   mode: activeProbeMode)
    }

    private func refreshStatus() {
        // The system "AppleKeyboards" list reflects keyboards added in Settings.
        // Avoid UITextInputMode private KVC here: an Objective-C exception on
        // foreground would terminate the app, and Section 2's relay owns active
        // keyboard detection anyway.
        let appleKeyboards: [String] =
            (UserDefaults.standard.array(forKey: "AppleKeyboards") as? [String] ?? [])
            + (UserDefaults(suiteName: ".GlobalPreferences")?.array(forKey: "AppleKeyboards") as? [String] ?? [])

        keyboardEnabled = SetupDetection.keyboardEnabled(
            appleKeyboards: appleKeyboards,
            forceEnabled: SetupDetection.forceKeyboardEnabled())

        if keyboardEnabled {
            let heartbeat = readKeyboardHeartbeat()
            faState = FAStateResolver.resolve(heartbeat: heartbeat,
                                              faPingThisSession: faPingThisSession,
                                              faPingAt: faPingAt)
            hasFreshFAEvidence = FAStateResolver.hasFreshEvidence(heartbeat: heartbeat,
                                                                  faPingThisSession: faPingThisSession)
            if activeThisSession {
                activeProbePending = false
            } else if let firedAt = activeProbeFiredAt,
                      Date().timeIntervalSince1970 - firedAt >= activeProbeMode.timeout {
                activeProbePending = false
            }
        } else {
            faState = .unknown
            hasFreshFAEvidence = false
            activeProbeFiredAt = nil
            activeProbePending = false
            activating = false
        }
    }

    private func readKeyboardHeartbeat() -> KeyboardHeartbeat? {
        guard let baseURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: groupSuite),
              let data = try? Data(contentsOf: SyncPaths.heartbeat(baseURL))
        else { return nil }
        return try? JSONDecoder().decode(KeyboardHeartbeat.self, from: data)
    }

    private func handleRelayPayloadNotification(_ note: Notification) {
        guard note.userInfo?["source"] as? String == "root",
              let faOn = note.userInfo?["faOn"] as? Bool,
              let ts = note.userInfo?["ts"] as? TimeInterval
        else { return }
        let firedAt = note.userInfo?["firedAt"] as? TimeInterval
        applyRelayPayload((proto: 1, faOn: faOn, ts: ts), firedAt: firedAt)
    }

    private func applyRelayPayload(_ payload: (proto: Int, faOn: Bool, ts: TimeInterval),
                                   firedAt: TimeInterval?) {
        let relayFiredAt = firedAt ?? payload.ts
        activeProbeFiredAt = FAStateResolver.isActiveThisSession(faPingAt: payload.ts,
                                                                 probeFiredAt: relayFiredAt,
                                                                 mode: activeProbeMode)
            ? relayFiredAt
            : payload.ts
        faPingThisSession = payload.faOn
        faPingAt = payload.ts
        activeProbePending = false
        activating = false
        refreshStatus()
        if activeThisSession {
            finishActiveProbe()
        }
    }

    private func ensureFAPingObserver() {
        guard faPingObserver == nil else { return }
        faPingObserver = FAPingObserver { hasFullAccess in
            let pingAt = Date().timeIntervalSince1970
            faPingThisSession = hasFullAccess
            faPingAt = pingAt
            if activating {
                activeProbeFiredAt = pingAt
            }
            refreshStatus()
            if activeThisSession {
                finishActiveProbe()
            }
        }
    }

    // Poll every 1 s while active so enabled state and Full Access both
    // reflect changes made in Settings without requiring a manual refresh.
    // (IM count is NOT refreshed here — it reads an @EnvironmentObject and writes
    // @State, which is only valid inside view-lifecycle closures, not a raw Timer.
    // It refreshes on foreground via .onReceive(didBecomeActive) instead.)
    private func startPolling() {
        guard pollTimer == nil else { return }
        pollTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            refreshStatus()
        }
    }

    private func stopPolling() {
        pollTimer?.invalidate()
        pollTimer = nil
    }

    private func triggerRootRelayIfNeeded() {
        // Re-detect on every foreground/appear even if we currently believe LIME is active —
        // the user may have switched to another keyboard while away. requestRootRelay bumps
        // activeProbeFiredAt, which invalidates the stale "active this session" match so the
        // ladder can fall back to enabled-but-not-active when LIME no longer responds.
        guard keyboardEnabled && !activeProbePending && !activating else { return }
        requestRootRelay(mode: .automatic)
    }

    private func requestRootRelay(mode: ActiveKeyboardProbeMode) {
        let firedAt = Date().timeIntervalSince1970
        activeProbeMode = mode
        activeProbePending = true
        activeProbeFiredAt = firedAt
        NotificationCenter.default.post(name: .limeTriggerRelay,
                                        object: nil,
                                        userInfo: mode.notificationUserInfo)
    }

    private func activateKeyboard() {
        activating = true
        requestRootRelay(mode: .manualSwitch)
    }

    private func finishActiveProbe() {
        activeProbePending = false
        activating = false
    }

    private func openLimeKeyboardSettings() {
        let plainURL = URL(string: UIApplication.openSettingsURLString)
        let firstURL = Bundle.main.bundleIdentifier
            .flatMap { URL(string: "\(UIApplication.openSettingsURLString)/\($0)") }
            ?? plainURL
        guard let firstURL else { return }
        UIApplication.shared.open(firstURL) { opened in
            if !opened, let plainURL {
                UIApplication.shared.open(plainURL)
            }
        }
    }

    // MARK: - Version

    private func appVersion() -> String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—"
        return "\(v) (\(b))"
    }

    /// "6.1.15 - 2026" — short version + current year, for the © footer banner.
    private func copyrightLine() -> String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"
        let year = Calendar.current.component(.year, from: Date())
        return "\(v) - \(year)"
    }
}
