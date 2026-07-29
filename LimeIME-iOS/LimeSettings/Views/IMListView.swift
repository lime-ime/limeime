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

// IMListView.swift
// LimeIME-iOS
//
// IM Manager tab — list of installed IMs with enable/disable and reorder.
// Spec §5.1.

import SwiftUI

// MARK: - IMRow

struct IMRow: Identifiable {
    let id: Int64
    let imName: String
    let label: String
    let tableNick: String
    let fullName: String
    var enabled: Bool
    var sortOrder: Int
    var keyboardId: String
}

// MARK: - IMListView

struct IMListView: View {

    @EnvironmentObject private var manageImController: ManageImController

    @State private var imList: [IMRow] = []
    @State private var isLoading = true
    @State private var errorMessage: String?

    // Selection-driven navigation. The parent owns the "currently shown
    // detail" identity. After a delete, IMDetailView calls back with
    // `clearSelection()` which sets this to nil — `NavigationSplitView`
    // reacts by reverting the detail column to the placeholder on iPad,
    // and by popping the pushed view on iPhone.
    //
    // We use NavigationSplitView (iOS 16+) instead of the deprecated
    // `NavigationView` + `NavigationLink(tag:selection:)` combo because
    // the latter has a long-standing SwiftUI bug: setting selection to nil
    // does NOT clear the iPad detail column, so a deleted IM's detail pane
    // would stay on screen. NavigationSplitView handles this correctly.
    private enum DetailSelection: Hashable {
        case im(Int64)
        case related
        case install
    }

    /// Stack-based navigation path. Each `DetailSelection` push lands the
    /// matching destination via `.navigationDestination(for: DetailSelection.self)`
    /// below. Replaces the previous `NavigationSplitView` + sidebar/detail
    /// columns; the IM tab now uses the same single-column constrained-width
    /// pattern as the 喜好設定 / 字根反查設定 flow for visual consistency on iPad.
    @State private var path: [DetailSelection] = []

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                // Static page title at the left edge of the 560pt content
                // column, matching PreferencesTabView / ReverseLookupSettingsView.
                Text("管理輸入法")
                    .font(.largeTitle.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 8)

                sidebar
            }
            // Same 560pt reading-width cap as the other tab roots.
            .frame(maxWidth: SettingsMetrics.contentMaxWidth)
            .frame(maxWidth: .infinity)
            // Tab root; no back navigation needed at this level. Pushed
            // destinations declare their own nav bar.
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: DetailSelection.self) { sel in
                destination(for: sel)
            }
            .onAppear {
                loadIMs()
                if pendingLimeExternalImportURL != nil { showInstallScreenForExternalImport() }
            }
            .onChange(of: manageImController.refreshToken) { _ in loadIMs() }
            .onReceive(NotificationCenter.default.publisher(for: .limeColdDatabaseAccessResumed)) { _ in
                loadIMs()
            }
            .onReceive(NotificationCenter.default.publisher(for: .limeExternalImport)) { _ in
                showInstallScreenForExternalImport()
            }
        }
    }

    // MARK: - Sidebar (master column)

    @ViewBuilder
    private var sidebar: some View {
        Group {
            if isLoading {
                ProgressView("載入中…")
            } else if let err = errorMessage {
                Text(err).foregroundColor(.secondary)
            } else {
                List {
                    Section(header: Text("已安裝的輸入法")) {
                        if imList.isEmpty {
                            IMEmptyState()
                                .frame(maxWidth: .infinity)
                                .listRowInsets(EdgeInsets())
                                .listRowBackground(Color.clear)
                        } else {
                            // NOTE: Use id-based bindings rather than `ForEach($imList)`.
                            // `ForEach($imList)` produces bindings that subscript the
                            // array by index. When the array mutates (onMove, refresh,
                            // delete) SwiftUI's UISwitch may still re-read a stale
                            // index in the same render cycle, crashing with
                            // "Fatal error: Index out of range" inside
                            // Binding<MutableCollection>.subscript.
                            ForEach(imList) { row in
                                let rowId = row.id
                                let enabledBinding = Binding<Bool>(
                                    get: {
                                        imList.first(where: { $0.id == rowId })?.enabled ?? false
                                    },
                                    set: { newVal in
                                        if let idx = imList.firstIndex(where: { $0.id == rowId }) {
                                            imList[idx].enabled = newVal
                                        }
                                        toggleIM(imName: row.imName, enabled: newVal)
                                    }
                                )
                                NavigationLink(value: DetailSelection.im(rowId)) {
                                    HStack(spacing: SettingsMetrics.imRowSpacing) {
                                        IMBadge(character: representativeCharacter(for: row))
                                            .opacity(row.enabled ? 1.0 : 0.5)
                                        Text(row.label)
                                            .font(.body)
                                            .opacity(row.enabled ? 1.0 : 0.5)
                                        Spacer()
                                        Toggle("", isOn: enabledBinding)
                                            .labelsHidden()
                                    }
                                }
                            }
                            // No drag-to-reorder: the installed list is not
                            // editable and has no Edit affordance (spec §5.1).
                        }
                    }
                    .setupMatchedSectionBlock()

                    Section(header: Text("關聯字庫")) {
                        NavigationLink(value: DetailSelection.related) {
                            // Single line, name only, with the grey tile + bubble
                            // glyph matching the IM-badge styling (spec §5.1).
                            HStack(spacing: SettingsMetrics.imRowSpacing) {
                                IMBadge(systemImage: "text.bubble")
                                Text("關聯字庫").font(.body)
                            }
                        }
                    }
                    .setupMatchedSectionBlock()
                }
                .overlay(alignment: .bottomTrailing) {
                    // The + FAB, nudged with a radar pulse + breath and a bobbing
                    // callout when no IM is installed (§5.1.1). Reduce-Motion safe.
                    InstallFAB(empty: imList.isEmpty) { path.append(.install) }
                        .padding([.bottom, .trailing], SettingsMetrics.floatingActionOuterPadding)
                }
                .listStyle(.insetGrouped)
                .setupMatchedGroupedSurface()
            }
        }
    }

    // MARK: - Push destinations

    @ViewBuilder
    private func destination(for sel: DetailSelection) -> some View {
        switch sel {
        case .im(let id):
            if let row = imList.first(where: { $0.id == id }) {
                IMDetailView(im: row, onRefresh: loadIMs, onDeleted: popToRoot)
            } else {
                placeholder
            }
        case .related:
            IMDetailView(
                im: IMRow(id: -1, imName: "related", label: "關聯字庫",
                          tableNick: "related", fullName: "",
                          enabled: true, sortOrder: 0, keyboardId: ""),
                onRefresh: nil,
                onDeleted: popToRoot)
        case .install:
            IMInstallView(onRefresh: loadIMs)
        }
    }

    private var placeholder: some View {
        Text("選擇一個輸入法")
            .font(.title3)
            .foregroundColor(.secondary)
    }

    /// Called by IMDetailView after a successful remove. Pops back to the
    /// IM list root so the deleted IM's detail pane no longer shows.
    private func popToRoot() {
        path.removeAll()
    }

    private func showInstallScreenForExternalImport() {
        if path.last != .install {
            path.removeAll()
            path.append(.install)
        }
    }

    // MARK: - Helpers

    private func loadIMs() {
        Task {
            let configs = await manageImController.loadIMList()
            let rows = configs.map { c in
                IMRow(id: c.id,
                      imName: c.imName,
                      label: c.label.isEmpty ? c.imName : c.label,
                      tableNick: c.tableNick,
                      fullName: c.fullName,
                      enabled: c.enabled,
                      sortOrder: c.sortOrder,
                      keyboardId: c.keyboardId)
            }
            imList = rows
            isLoading = false
            errorMessage = nil
        }
    }

    private func toggleIM(imName: String, enabled: Bool) {
        Task {
            await manageImController.setIMEnabled(imName: imName, enabled: enabled)
        }
    }

    /// The representative character shown in an IM row's grey badge. The rule is
    /// the **first character of the IM name**, with curated exceptions:
    ///   注音 → ㄅ (bopomofo symbol);
    ///   大易 → 易 (not 大);
    ///   倉頡-family (倉頡 / 四碼倉頡 / 倉頡五代 / 快倉) → 倉;
    ///   行列10 → 10 (not 行).
    /// Keyed by `tableNick`; unknown tables fall back to the first name character.
    /// Identical on Android. Spec §5.1.
    private func representativeCharacter(for row: IMRow) -> String {
        if let glyph = IMListView.representativeGlyphs[row.tableNick] { return glyph }
        return row.label.isEmpty ? "?" : String(row.label.prefix(1))
    }

    private static let representativeGlyphs: [String: String] = [
        "phonetic": "ㄅ",
        "cj": "倉",
        "cj4": "倉",     // 四碼倉頡
        "cj5": "倉",     // 倉頡五代
        "scj": "倉",     // 快倉
        "dayi": "易",    // 大易 → 易 (not 大)
        "array10": "10", // 行列10 → 10 (not 行)
    ]
}

// MARK: - IMBadge

/// Grey rounded-square tile carrying an IM's representative character (or, for
/// the 關聯字庫 row, a glyph). A single neutral badge — colour is reserved for
/// interactive controls. Spec §5.1.
private struct IMBadge: View {
    private enum Content {
        case character(String)
        case symbol(String)
    }
    private let content: Content

    init(character: String) { self.content = .character(character) }
    init(systemImage: String) { self.content = .symbol(systemImage) }

    var body: some View {
        Group {
            switch content {
            case .character(let c):
                Text(c)
                    .font(.system(size: SettingsMetrics.imBadgeFontSize, weight: .medium))
            case .symbol(let name):
                Image(systemName: name)
                    .font(.system(size: SettingsMetrics.imBadgeFontSize, weight: .medium))
            }
        }
        .foregroundColor(SettingsTheme.imBadgeForeground)
        .frame(width: SettingsMetrics.imBadgeSize,
               height: SettingsMetrics.imBadgeSize)
        .background(SettingsTheme.imBadgeBackground,
                    in: RoundedRectangle(cornerRadius: SettingsMetrics.imBadgeCornerRadius))
    }
}

// MARK: - IMEmptyState (§5.1.1)

/// Centered placeholder shown in place of the IM rows when none is installed:
/// a brand-accent keyboard glyph on a quaternary-fill tile, a title, and one
/// line of guidance whose ＋ glyph is accent-tinted to tie it to the FAB.
private struct IMEmptyState: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "keyboard")
                .font(.system(size: 46))
                .foregroundColor(SettingsTheme.accent)
                .frame(width: 96, height: 96)
                .background(Color(.quaternarySystemFill),
                            in: RoundedRectangle(cornerRadius: 28))
            Text("尚未安裝任何輸入法")
                .font(.system(size: 20, weight: .semibold))
            (Text("點選右下角的 ")
                + Text("＋").foregroundColor(SettingsTheme.accent)
                + Text(" 下載或匯入輸入法表格，即可開始使用。"))
                .font(.system(size: 15))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 250)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
        .padding(.horizontal, 24)
    }
}

// MARK: - InstallFAB (§5.1.1)

/// The + floating action button. When `empty`, it draws the eye with three
/// coordinated, accent-coloured cues — two staggered radar rings, a periodic
/// "breath" scale, and a bobbing callout pill above it — all gated on
/// `accessibilityReduceMotion` so motion-sensitive users get a static fallback.
private struct InstallFAB: View {
    let empty: Bool
    let action: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var animate = false

    private var motion: Bool { empty && !reduceMotion }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            // Bobbing callout pill with a downward caret pointing at the FAB.
            if empty {
                calloutPill
                    .offset(y: -70)
                    .offset(y: motion && animate ? 4 : 0)
                    .animation(motion
                        ? .easeInOut(duration: 0.9).repeatForever(autoreverses: true)
                        : .default, value: animate)
            }

            ZStack {
                // Two staggered radar rings. Static parked ring under Reduce Motion.
                if empty {
                    ring(delay: 0)
                    ring(delay: 1.2)
                }
                button
            }
        }
        .onAppear { animate = true }
        .onChange(of: empty) { _ in animate = true }
    }

    private var button: some View {
        Button(action: action) {
            Image(systemName: "plus")
                .font(.title2.weight(.semibold))
                .foregroundStyle(SettingsTheme.floatingActionForeground)
                .padding(SettingsMetrics.floatingActionPadding)
                .background(SettingsTheme.floatingActionBackground, in: Circle())
                .shadow(color: SettingsTheme.floatingActionShadow,
                        radius: SettingsMetrics.floatingActionShadowRadius,
                        x: 0,
                        y: SettingsMetrics.floatingActionShadowY)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("im_install_fab")
        .scaleEffect(motion && animate ? 1.08 : 1.0)
        .animation(motion
            ? .easeInOut(duration: 1.2).repeatForever(autoreverses: true)
            : .default, value: animate)
    }

    @ViewBuilder
    private func ring(delay: Double) -> some View {
        if reduceMotion {
            // Reduced-motion fallback: a single static expanded ring.
            Circle()
                .fill(SettingsTheme.accent)
                .frame(width: 56, height: 56)
                .scaleEffect(1.9)
                .opacity(0.18)
        } else {
            Circle()
                .fill(SettingsTheme.accent)
                .frame(width: 56, height: 56)
                .scaleEffect(animate ? 2.6 : 1.0)
                .opacity(animate ? 0 : 0.45)
                .animation(.easeOut(duration: 2.4).repeatForever(autoreverses: false).delay(delay),
                           value: animate)
        }
    }

    private var calloutPill: some View {
        Text("安裝輸入法")
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(.white)
            .padding(.vertical, 9)
            .padding(.horizontal, 13)
            .background(SettingsTheme.accent, in: RoundedRectangle(cornerRadius: 11))
            .overlay(alignment: .bottomTrailing) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(SettingsTheme.accent)
                    .frame(width: 12, height: 12)
                    .rotationEffect(.degrees(45))
                    .offset(x: -16, y: 5)
            }
            .shadow(color: SettingsTheme.switchShadow, radius: 7, x: 0, y: 4)
            .fixedSize()
    }
}
