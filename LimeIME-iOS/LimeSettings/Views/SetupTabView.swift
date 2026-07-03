// SetupTabView.swift
// LimeIME-iOS
//
// App Setup tab — keyboard activation guide, status detection, about.
// Spec §4.  Gboard-inspired layout: logo → status → step list → CTA → about.

import SwiftUI
import UIKit
import SafariServices
#if DEBUG
// FA-PROBE-I0 (temporary): fixture generation for import-timing probe.
// Selective import — a bare `import GRDB` collides with GroupBoxStyle.Configuration.
import class GRDB.DatabaseQueue
#endif

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

    // keyboardEnabled: checked via UITextInputMode.activeInputModes — this is the
    // same system API iOS uses to build the keyboard switcher.  It updates the
    // moment the user adds or removes a keyboard in Settings, no heartbeat needed.
    @State private var keyboardEnabled   = false
    // fullAccessEnabled: can only be known once the keyboard extension has run at
    // least once and written the value to the shared App Group.
    @State private var fullAccessEnabled = false

    @State private var pollTimer: Timer?

    // Invisible probe field: when the user taps it and types with the keyboard,
    // the extension writes keyboard_has_full_access to the App Group so the Full
    // Access state becomes accurate without leaving this screen.
    @State private var probeText: String = ""
    @FocusState private var probeFocused: Bool

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

                    // ── Status banner ─────────────────────────────────────
                    statusBanner
                        .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                    // ── Title ─────────────────────────────────────────────
                    Text("設定萊姆輸入法")
                        .font(.system(size: SettingsMetrics.setupTitleFontSize, weight: .bold))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                    // ── Step list ─────────────────────────────────────────
                    VStack(alignment: .leading, spacing: SettingsMetrics.setupListSpacing) {
                        SetupStepRow(text: "輕觸「鍵盤」") {
                            Image(systemName: "keyboard")
                                .font(.title3)
                                .foregroundColor(.accentColor)
                        }
                        SetupStepRow(text: "開啟萊姆輸入法") {
                            ToggleSwitchIcon()
                        }
                        SetupStepRow(text: "開啟「允許完整取用」") {
                            ToggleSwitchIcon()
                        }
                    }
                    .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                    // ── Explanatory note (hidden once Full Access is on) ──
                    if !fullAccessEnabled {
                        Text("萊姆輸入法僅需完整取用以啟用按鍵震動回饋。若不需要此功能，可不開啟。萊姆輸入法不會收集或傳送任何個人資料。")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)
                    }

                    // ── CTA button (full-width tonal — same legible style as the
                    //    資料庫 restore buttons; readable in dark mode). ─────────
                    Button {
                        openLimeKeyboardSettings()
                    } label: {
                        Text("前往設定")
                    }
                    .buttonStyle(LimeTonalButtonStyle())
                    .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)

                    // Hidden once fully enabled (green banner) — the hint only
                    // helps while the user is still hunting for the keyboard.
                    if detectionState != .fullyEnabled {
                        Text("若設定未直接顯示萊姆輸入法，請到「設定」>「Apps」>「萊姆輸入法」>「Keyboards」，開啟萊姆輸入法與允許完整取用。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, SettingsMetrics.pageHorizontalPadding)
                    }

                    // Invisible 1 × 1 probe — preserves heartbeat polling
                    // without showing a text field in the new layout.
                    TextField("", text: $probeText)
                        .focused($probeFocused)
                        .frame(width: SettingsMetrics.invisibleProbeSize,
                               height: SettingsMetrics.invisibleProbeSize)
                        .opacity(SettingsMetrics.invisibleProbeOpacity)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .accessibilityHidden(true)

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
                refreshStatus()
                refreshIMStatus()
                startPolling()
                // Auto-focus the invisible probe so the LimeIME extension's
                // viewWillAppear fires (if LimeIME is the active keyboard)
                // and writes fresh hasFullAccess to the App Group.
                triggerProbeIfNeeded()
                #if DEBUG
                faProbeSetup()   // FA-PROBE-I0 (temporary)
                #endif
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    refreshStatus()
                    refreshIMStatus()
                    startPolling()
                    // Re-trigger each time app comes to foreground — covers the
                    // common case: user grants Full Access in Settings, returns.
                    triggerProbeIfNeeded()
                } else if phase == .background {
                    stopPolling()
                }
            }
            .onChange(of: fullAccessEnabled) { enabled in
                // Once Full Access is confirmed, dismiss the keyboard.
                if enabled { probeFocused = false }
            }
            .onChange(of: probeText) { _ in
                refreshStatus()
                #if DEBUG
                // FA-PROBE-I0 (temporary): keyboard types its report into the probe
                // field; mirror it to the App Group so devicectl can read it out.
                if probeText.contains("[FAPROBE"),
                   let ag = FileManager.default.containerURL(
                        forSecurityApplicationGroupIdentifier: groupSuite) {
                    try? probeText.data(using: .utf8)!
                        .write(to: ag.appendingPathComponent("probe_report.txt"))
                }
                #endif
            }
            .onChange(of: manageImController.refreshToken) { _ in refreshIMStatus() }
            // scenePhase → .active is unreliable when SwiftUI is hosted in a
            // UIHostingController under a UIKit SceneDelegate, so the §4.3 IM
            // count could stay stale (showing 0) after returning from system
            // Settings. didBecomeActiveNotification fires reliably on every
            // foreground activation and is delivered on the main actor in the
            // view context, where the @EnvironmentObject and @State are valid.
            .onReceive(NotificationCenter.default.publisher(
                for: UIApplication.didBecomeActiveNotification)) { _ in
                refreshStatus()
                refreshIMStatus()
                startPolling()
                triggerProbeIfNeeded()
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
            // Full-bleed separator so the block reads as its own section,
            // matching the About footer divider. Spec §4.3.
            Divider()
                .padding(.horizontal, -SettingsMetrics.pageHorizontalPadding)

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
        .padding(.top, SettingsMetrics.aboutFooterTopPadding)
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
        case .disabled: return SettingsTheme.statusTintYellow
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

    // MARK: - Status banner

    // Status banner (§4.2): a filled status glyph + label in the state's deep
    // "ink" colour over a subtle status tint, matching the design StatusBanner.
    private var statusBanner: some View {
        Label(statusText, systemImage: statusSymbol)
            .font(.system(size: 15, weight: .semibold))
            .foregroundColor(statusInk)
            .padding(.vertical, SettingsMetrics.statusVerticalPadding)
            .padding(.horizontal, SettingsMetrics.statusHorizontalPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(statusTint)
            .clipShape(RoundedRectangle(cornerRadius: SettingsMetrics.groupedSectionCornerRadius))
    }

    private var statusText: String {
        switch detectionState {
        case .fullyEnabled:        return "萊姆輸入法已啟用"
        case .enabledNoFullAccess: return "鍵盤已啟用，但尚未允許完整取用"
        case .notEnabled:          return "尚未啟用萊姆輸入法鍵盤"
        }
    }

    private var statusSymbol: String {
        switch detectionState {
        case .fullyEnabled:        return "checkmark.circle.fill"
        case .enabledNoFullAccess: return "exclamationmark.triangle.fill"
        case .notEnabled:          return "xmark.circle.fill"
        }
    }

    private var statusInk: Color {
        switch detectionState {
        case .fullyEnabled:        return SettingsTheme.successInk
        case .enabledNoFullAccess: return SettingsTheme.warningInk
        case .notEnabled:          return SettingsTheme.dangerInk
        }
    }

    private var statusTint: Color {
        switch detectionState {
        case .fullyEnabled:        return SettingsTheme.statusTintGreen
        case .enabledNoFullAccess: return SettingsTheme.statusTintYellow
        case .notEnabled:          return SettingsTheme.statusTintRed
        }
    }

    // MARK: - Detection

    private enum DetectionState {
        case fullyEnabled, enabledNoFullAccess, notEnabled
    }

    private var detectionState: DetectionState {
        guard keyboardEnabled else { return .notEnabled }
        return fullAccessEnabled ? .fullyEnabled : .enabledNoFullAccess
    }

    private func refreshStatus() {
        // UITextInputMode.activeInputModes reflects the live system keyboard list.
        // The public API only exposes `primaryLanguage`, which is "zh-Hant" for
        // LimeIME — but also for Apple's built-in Traditional Chinese keyboards,
        // causing false positives.
        //
        // The correct way to identify a specific extension is via the private
        // `identifier` KVC key, which returns the extension's bundle ID
        // (e.g. "org.limeime.LimeKeyboard"). This pattern is used by
        // Gboard, SwiftKey and other third-party keyboards.
        // We guard with responds(to:) so if Apple ever removes this private
        // property the code degrades gracefully to false rather than crashing.
        // Method A — private `identifier` KVC on UITextInputMode.activeInputModes.
        let limeSelector = NSSelectorFromString("identifier")
        let viaInputModes = UITextInputMode.activeInputModes.contains { mode in
            guard mode.responds(to: limeSelector),
                  let id = mode.value(forKey: "identifier") as? String
            else { return false }
            return id.hasPrefix("org.limeime")
        }

        // Method B — the system "AppleKeyboards" list of enabled keyboard bundle IDs.
        // The private KVC in Method A can stop resolving on newer iOS (e.g. iOS 26),
        // which makes the banner stick on red even when the keyboard IS added. This
        // list reflects "added in Settings" directly; it lives in the global domain.
        let appleKeyboards: [String] =
            (UserDefaults.standard.array(forKey: "AppleKeyboards") as? [String] ?? [])
            + (UserDefaults(suiteName: ".GlobalPreferences")?.array(forKey: "AppleKeyboards") as? [String] ?? [])
        let viaAppleKeyboards = appleKeyboards.contains { $0.hasPrefix("org.limeime") }

        keyboardEnabled = viaInputModes || viaAppleKeyboards

        if keyboardEnabled {
            let suite = UserDefaults(suiteName: groupSuite)
            suite?.synchronize()  // force cross-process refresh
            // `bool(forKey:)` returns false for missing keys, which creates a
            // false-positive orange banner when the extension has never run yet
            // (e.g. keyboard just enabled + Full Access just granted).
            // Instead: treat MISSING key as "unknown → assume enabled".
            // The extension explicitly writes `false` when it finds Full Access
            // denied, so orange only appears when we KNOW it is actually denied.
            if let storedValue = suite?.object(forKey: "keyboard_has_full_access") as? Bool {
                fullAccessEnabled = storedValue   // definitive value from extension
            } else {
                fullAccessEnabled = true          // never ran yet — assume granted
            }
        } else {
            fullAccessEnabled = false
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

    /// Focuses the invisible probe field (with a short delay) when the keyboard
    /// is enabled but Full Access hasn't been confirmed yet.  Focusing any text
    /// field causes iOS to load whichever keyboard is currently active; if that
    /// is LimeIME, its UIInputViewController.viewWillAppear fires and writes a
    /// fresh `keyboard_has_full_access` value to the App Group — which the
    /// 1-second poll then picks up automatically.
    private func triggerProbeIfNeeded() {
        guard keyboardEnabled && !fullAccessEnabled else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            probeFocused = true
        }
    }

    #if DEBUG
    // FA-PROBE-I0 (temporary, removed at end of I0): writes the AG-read marker,
    // generates the 100k-row attach-import fixture, and posts a 1 Hz Darwin
    // doorbell while this tab is up. See docs/IOS_FA_REARCH_TASKS.md Task 0.1–0.3.
    private static var faProbeDarwinTimer: Timer?

    private func faProbeSetup() {
        guard let ag = FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: groupSuite) else { return }
        try? "probe \(Date().timeIntervalSince1970)".data(using: .utf8)!
            .write(to: ag.appendingPathComponent("probe_marker.txt"))
        let fixture = ag.appendingPathComponent("probe_fixture.limedb")
        if !FileManager.default.fileExists(atPath: fixture.path) {
            DispatchQueue.global(qos: .utility).async {
                try? Self.makeProbeFixture(at: fixture)
            }
        }
        Self.faProbeDarwinTimer?.invalidate()
        Self.faProbeDarwinTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            let name = CFNotificationName("org.limeime.tables.updated" as CFString)
            CFNotificationCenterPostNotification(
                CFNotificationCenterGetDarwinNotifyCenter(), name, nil, nil, true)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { probeFocused = true }
    }

    private static func makeProbeFixture(at url: URL) throws {
        let dq = try DatabaseQueue(path: url.path)
        try dq.write { db in
            try db.execute(sql: "CREATE TABLE IF NOT EXISTS probe_src(code TEXT, word TEXT, score INTEGER)")
            let count = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM probe_src") ?? 0
            guard count < 100_000 else { return }
            try db.execute(sql: "DELETE FROM probe_src")
            for i in 0..<100_000 {
                try db.execute(sql: "INSERT INTO probe_src VALUES (?,?,?)",
                               arguments: ["c\(i % 9999)", "字\(i)", i % 500])
            }
        }
    }
    #endif

    private func openLimeKeyboardSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
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
