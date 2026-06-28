// SettingsTheme.swift
// LimeIME-iOS
//
// Semantic color roles for the settings app. Keep direct hardcoded colors
// here so views describe intent instead of choosing final colors inline.

import SwiftUI
import UIKit

enum SettingsTheme {
    // LIME-forward re-layout: brand green (lime-fruit app-icon deep green
    // #00833E) is promoted to the primary accent — filled buttons, links,
    // FAB, chevrons. Applied once via `.tint()` at the root TabView so every
    // `.accentColor` / `.borderedProminent` / Link inherits it. The native
    // systemBlue stays available as `accentBlue` for system-style affordances.
    // See docs/VISUAL_DESIGN.md (Color) and tokens/colors.css (--accent).
    static let accent = Color(red: 0x00 / 255, green: 0x83 / 255, blue: 0x3E / 255)
    static let accentPressed = Color(red: 0x00 / 255, green: 0x68 / 255, blue: 0x2F / 255)
    static let accentBlue = Color(uiColor: .systemBlue)

    static let destructive = Color(uiColor: .systemRed)
    static let success = Color(uiColor: .systemGreen)
    static let warning = Color(uiColor: .systemOrange)
    static let progressAccent = Color(uiColor: .systemBlue)

    // Status banner (§4.2) — the icon + label use the design's deeper "ink"
    // status colours over a subtle status tint, matching tokens/colors.css
    // (--success-ink / --warning-ink / --danger-ink and --status-tint-*).
    //
    // Inks and tints are ADAPTIVE: in dark mode the opaque pastel tints read far
    // too bright, so — like Android (values-night/colors.xml) — dark mode uses a
    // lighter ink and a translucent tint (≈15% of that ink) instead of a solid
    // pastel block; light mode keeps the deep ink over a ≈12% translucent tint.
    private static func adaptive(light: UIColor, dark: UIColor) -> Color {
        Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? dark : light })
    }
    // Deep light inks vs. Android's soft dark inks (#9CD67D / #FFB951 / #FFB4AB).
    private static let successInkLight = UIColor(red: 0x2E / 255, green: 0x7D / 255, blue: 0x32 / 255, alpha: 1)
    private static let warningInkLight = UIColor(red: 0xEF / 255, green: 0x6C / 255, blue: 0x00 / 255, alpha: 1)
    private static let dangerInkLight  = UIColor(red: 0xC6 / 255, green: 0x28 / 255, blue: 0x28 / 255, alpha: 1)
    private static let successInkDark  = UIColor(red: 0x9C / 255, green: 0xD6 / 255, blue: 0x7D / 255, alpha: 1)
    private static let warningInkDark  = UIColor(red: 0xFF / 255, green: 0xB9 / 255, blue: 0x51 / 255, alpha: 1)
    private static let dangerInkDark   = UIColor(red: 0xFF / 255, green: 0xB4 / 255, blue: 0xAB / 255, alpha: 1)

    static let successInk = adaptive(light: successInkLight, dark: successInkDark)
    static let warningInk = adaptive(light: warningInkLight, dark: warningInkDark)
    static let dangerInk  = adaptive(light: dangerInkLight,  dark: dangerInkDark)
    // Tint = translucent ink: 12% over the deep ink (light) / 15% over the soft
    // ink (dark) — matching Android's #1F (light) and #26 (dark) alpha.
    static let statusTintGreen  = adaptive(light: successInkLight.withAlphaComponent(0.12),
                                           dark:  successInkDark.withAlphaComponent(0.15))
    static let statusTintYellow = adaptive(light: warningInkLight.withAlphaComponent(0.12),
                                           dark:  warningInkDark.withAlphaComponent(0.15))
    static let statusTintRed    = adaptive(light: dangerInkLight.withAlphaComponent(0.12),
                                           dark:  dangerInkDark.withAlphaComponent(0.15))

    static let floatingActionForeground = Color(uiColor: .white)
    static let floatingActionBackground = accent
    static let floatingActionShadow = Color(uiColor: .black).opacity(0.3)

    static let overlayScrim = Color(uiColor: .black).opacity(0.3)
    static let globalOverlayScrim = Color(uiColor: .black).opacity(0.35)
    static let overlayCardBackground = Color(uiColor: .systemBackground)

    static let switchTrack = Color(uiColor: .systemGreen)
    static let switchThumb = Color(uiColor: .white)
    static let switchShadow = Color(uiColor: .black).opacity(0.18)

    // IM list-row leading badge. A single neutral grey circle carrying the
    // IM's representative character — accent colour is reserved for
    // interactive controls (switches, buttons, links). Spec §4.1 / §5.1.
    static let imBadgeBackground = Color(uiColor: .systemGray)
    static let imBadgeForeground = Color(uiColor: .white)

    /// Tonal button fill for a given accent. SwiftUI's `.bordered` fill is far too
    /// dark/low-contrast on a black background in dark mode (see DB 還原 button), so
    /// this uses a stronger tint in dark (28%) than light (14%) — like the Android
    /// `button_tint_primary` / `button_tint_error` selectors. Text/icon use the
    /// solid accent on top.
    static func tonalFill(_ base: Color) -> Color {
        let ui = UIColor(base)
        return Color(uiColor: UIColor { trait in
            ui.withAlphaComponent(trait.userInterfaceStyle == .dark ? 0.28 : 0.14)
        })
    }
}

/// A filled-tonal button (rounded, full-width-friendly) with a legible tint in both
/// light and dark. Used for the secondary/destructive actions on the 資料庫 and 設定
/// tabs in place of `.buttonStyle(.bordered)`, whose dark-mode fill is too faint.
struct LimeTonalButtonStyle: ButtonStyle {
    var tint: Color = SettingsTheme.accent
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 17, weight: .semibold))
            .foregroundColor(tint)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(SettingsTheme.tonalFill(tint))
            .clipShape(Capsule())   // round-edge to match the .borderedProminent backup button
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}
