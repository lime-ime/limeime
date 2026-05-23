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
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
            // Tab root; no back navigation needed at this level. Pushed
            // destinations declare their own nav bar.
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: DetailSelection.self) { sel in
                destination(for: sel)
            }
            .onAppear { loadIMs() }
            .onChange(of: manageImController.refreshToken) { _ in loadIMs() }
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
                            Text("尚未匯入任何輸入法")
                                .foregroundColor(.secondary)
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
                                    HStack {
                                        Text(row.label)
                                            .font(.body)
                                            .opacity(row.enabled ? 1.0 : 0.5)
                                        Spacer()
                                        Toggle("", isOn: enabledBinding)
                                            .labelsHidden()
                                    }
                                }
                            }
                            .onMove(perform: moveIMs)
                        }
                    }
                    .setupMatchedSectionBlock()

                    Section(header: Text("關聯字庫")) {
                        NavigationLink(value: DetailSelection.related) {
                            Label("關聯字庫", systemImage: "text.bubble")
                        }
                    }
                    .setupMatchedSectionBlock()
                }
                .overlay(alignment: .bottomTrailing) {
                    Button {
                        path.append(.install)
                    } label: {
                        Image(systemName: "plus")
                            .font(.title2.weight(.semibold))
                            .foregroundStyle(.white)
                            .padding(16)
                            .background(Color.blue, in: Circle())
                            .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
                    }
                    .buttonStyle(.plain)
                    .padding([.bottom, .trailing], 20)
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

    private func moveIMs(from source: IndexSet, to dest: Int) {
        imList.move(fromOffsets: source, toOffset: dest)
        for (idx, row) in imList.enumerated() {
            let id = row.id
            Task {
                await manageImController.setIMSortOrder(id: id, sortOrder: idx)
            }
        }
    }
}
