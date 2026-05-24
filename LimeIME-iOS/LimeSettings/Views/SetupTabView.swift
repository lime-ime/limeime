// SetupTabView.swift
// LimeIME-iOS
//
// App Setup tab — keyboard activation guide, status detection, about.
// Spec §4.  Gboard-inspired layout: logo → status → step list → CTA → about.

import SwiftUI
import UIKit

// MARK: - FormSectionGroupBoxStyle

/// Makes a GroupBox look identical to a SwiftUI Form Section (grouped style):
/// white secondarySystemGroupedBackground fill, 10-pt corner radius, standard row padding.
struct FormSectionGroupBoxStyle: GroupBoxStyle {
    func makeBody(configuration: Configuration) -> some View {
        VStack(spacing: 0) {
            configuration.content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - ToggleSwitchIcon

/// Green ON-state toggle that matches the iOS Settings keyboard-enable toggle.
private struct ToggleSwitchIcon: View {
    var body: some View {
        ZStack(alignment: .trailing) {
            Capsule()
                .fill(Color.green)
                .frame(width: 30, height: 18)
            Circle()
                .fill(Color.white)
                .shadow(color: .black.opacity(0.18), radius: 1, x: 0, y: 1)
                .frame(width: 14, height: 14)
                .padding(.trailing, 2)
        }
    }
}

// MARK: - SetupStepRow

private struct SetupStepRow<Icon: View>: View {
    let text: String
    @ViewBuilder let icon: Icon

    var body: some View {
        HStack(spacing: 16) {
            icon
                .frame(width: 32, alignment: .center)
            Text(text)
                .font(.body)
            Spacer()
        }
    }
}

// MARK: - SetupTabView

struct SetupTabView: View {

    @Environment(\.scenePhase) private var scenePhase

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
    private let groupSuite   = "group.net.toload.limeime"
    private let githubURL        = URL(string: "https://github.com/lime-ime/limeime")!
    private let licenseURL       = URL(string: "https://github.com/lime-ime/limeime/blob/master/LICENSE.md")!

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {

                    // ── Brand block (logo + wordmark) ─────────────────────
                    VStack(spacing: 8) {
                        logoImage
                        Text("萊姆輸入法")
                            .font(.largeTitle).bold()
                    }
                    .padding(.top, 32)

                    // ── Status banner ─────────────────────────────────────
                    statusBanner
                        .padding(.horizontal, 24)

                    // ── Title ─────────────────────────────────────────────
                    Text("設定萊姆輸入法")
                        .font(.largeTitle).bold()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 24)

                    // ── Step list ─────────────────────────────────────────
                    VStack(alignment: .leading, spacing: 16) {
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
                    .padding(.horizontal, 24)

                    // ── Explanatory note ──────────────────────────────────
                    Text("萊姆輸入法僅需完整取用以啟用按鍵震動回饋。若不需要此功能，可不開啟。萊姆輸入法不會收集或傳送任何個人資料。")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)

                    // ── CTA button ────────────────────────────────────────
                    Button("前往設定") {
                        openLimeKeyboardSettings()
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .padding(.horizontal, 24)

                    // Invisible 1 × 1 probe — preserves heartbeat polling
                    // without showing a text field in the new layout.
                    TextField("", text: $probeText)
                        .focused($probeFocused)
                        .frame(width: 1, height: 1)
                        .opacity(0.01)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .accessibilityHidden(true)

                    // ── About section ─────────────────────────────────────
                    GroupBox {
                        LabeledContent("版本", value: appVersion())
                            .padding(.vertical, 11)
                        Divider()
                        HStack {
                            Text("授權")
                            Spacer()
                            Link("版權說明", destination: licenseURL)
                        }
                        .padding(.vertical, 11)
                        Divider()
                        Link("原始碼 (GitHub)", destination: githubURL)
                            .padding(.vertical, 11)
                    }
                    .groupBoxStyle(FormSectionGroupBoxStyle())
                    .padding(.horizontal, 24)
                    .padding(.bottom, 32)
                }
                // iPad / wide-screen reading-width cap: keeps the form column
                // at a comfortable width instead of stretching edge-to-edge in
                // iPad portrait and (especially) landscape. On iPhone this cap
                // never engages because the screen is narrower than 560pt.
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
            }
            .navigationBarHidden(true)
            .onAppear {
                refreshStatus()
                startPolling()
                // Auto-focus the invisible probe so the LimeIME extension's
                // viewWillAppear fires (if LimeIME is the active keyboard)
                // and writes fresh hasFullAccess to the App Group.
                triggerProbeIfNeeded()
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    refreshStatus()
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
            .onChange(of: probeText) { _ in refreshStatus() }
        }
    }

    // MARK: - Logo

    @ViewBuilder
    private var logoImage: some View {
        if let uiImage = appIconUIImage() {
            Image(uiImage: uiImage)
                .resizable()
                .scaledToFit()
                .frame(width: 80, height: 80)
                .clipShape(RoundedRectangle(cornerRadius: 18))
        } else {
            Image(systemName: "keyboard.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 60, height: 60)
                .padding(10)
                .foregroundColor(.accentColor)
                .background(Color(.quaternarySystemFill))
                .clipShape(RoundedRectangle(cornerRadius: 18))
        }
    }

    /// Returns the app's primary icon from the bundle.
    /// `UIImage(named: "AppIcon")` does not work for app-icon assets at runtime;
    /// reading the file name from `CFBundleIcons` is the correct approach.
    private func appIconUIImage() -> UIImage? {
        guard
            let icons   = Bundle.main.infoDictionary?["CFBundleIcons"]         as? [String: Any],
            let primary = icons["CFBundlePrimaryIcon"]                          as? [String: Any],
            let files   = primary["CFBundleIconFiles"]                          as? [String],
            let name    = files.last
        else { return nil }
        return UIImage(named: name)
    }

    // MARK: - Status banner

    private var statusBanner: some View {
        Group {
            switch detectionState {
            case .fullyEnabled:
                Label("萊姆輸入法已啟用",
                      systemImage: "checkmark.circle.fill")
                    .foregroundColor(.green)
            case .enabledNoFullAccess:
                Label("鍵盤已啟用，但尚未允許完整取用",
                      systemImage: "exclamationmark.triangle.fill")
                    .foregroundColor(.orange)
            case .notEnabled:
                Label("尚未啟用萊姆輸入法鍵盤",
                      systemImage: "xmark.circle.fill")
                    .foregroundColor(.red)
            }
        }
        .font(.subheadline)
        .padding(.vertical, 10)
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10))
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
        // (e.g. "net.toload.limeime.LimeKeyboard"). This pattern is used by
        // Gboard, SwiftKey and other third-party keyboards.
        // We guard with responds(to:) so if Apple ever removes this private
        // property the code degrades gracefully to false rather than crashing.
        let limeSelector = NSSelectorFromString("identifier")
        keyboardEnabled = UITextInputMode.activeInputModes.contains { mode in
            guard mode.responds(to: limeSelector),
                  let id = mode.value(forKey: "identifier") as? String
            else { return false }
            return id.hasPrefix("net.toload.limeime")
        }

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

    // MARK: - Navigation

    /// Opens the 萊姆輸入法 app settings page in the system Settings app.
    /// `openSettingsURLString` is the only Apple-guaranteed deep link; it always
    /// opens the settings page for the calling app (i.e. LimeIME Settings).
    /// From there the user taps "鍵盤" to reach the keyboard settings page where
    /// the enable toggle and Allow Full Access toggle live.
    /// `App-Prefs:` paths are intentionally avoided because `canOpenURL` returns
    /// true for any whitelisted scheme regardless of path, causing silent
    /// navigation to the wrong page.
    private func openLimeKeyboardSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    // MARK: - Version

    private func appVersion() -> String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—"
        return "\(v) (\(b))"
    }
}
