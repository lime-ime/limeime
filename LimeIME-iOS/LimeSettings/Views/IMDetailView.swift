// IMDetailView.swift
// LimeIME-iOS
//
// IM detail drill-down showing metadata, keyboard picker, and table editor link.
// Spec §5.2.

import SwiftUI

// MARK: - IMDetailView

struct IMDetailView: View {

    private enum MetadataField: String, Identifiable {
        case name
        case version
        case endkey

        var id: String { rawValue }
        var label: String {
            switch self {
            case .name: return "名稱"
            case .version: return "版本"
            case .endkey: return "結束鍵"
            }
        }
        var title: String {
            switch self {
            case .name: return "編輯名稱"
            case .version: return "編輯版本"
            case .endkey: return "編輯結束鍵"
            }
        }
        var dbField: String {
            switch self {
            case .name, .version: return rawValue
            case .endkey: return "limeendkey"
            }
        }
    }

    let im: IMRow
    let onRefresh: (() -> Void)?
    /// Called by the parent (`IMListView`) after a successful remove. The
    /// parent owns the navigation selection and is responsible for dismissing
    /// the detail pane (clearing selection on iPad reverts the detail column
    /// to a placeholder; on iPhone it pops the stack).
    /// We intentionally do NOT call `dismiss()` ourselves: on iPad the
    /// `NavigationView` runs in column/split style and `dismiss()` /
    /// `presentationMode.dismiss()` cannot pop a detail-column view.
    let onDeleted: (() -> Void)?

    @EnvironmentObject private var manageImController: ManageImController
    @EnvironmentObject private var manageRelatedController: ManageRelatedController
    @EnvironmentObject private var setupController: SetupImController

    private let sharedUD = UserDefaults(suiteName: LIMEPreferenceManager.suiteName)

    // §13.3 — custom IM mapping toggles (only shown when tableNick == "custom")
    @AppStorage("accept_number_index", store: sharedDefaults) private var acceptNumberIndex: Bool = false
    @AppStorage("accept_symbol_index", store: sharedDefaults) private var acceptSymbolIndex: Bool = false
    @AppStorage("cj4_semicolon_key", store: sharedDefaults) private var cj4SemicolonKey: Bool = false

    // array10 phone-numpad auto-commit (only shown when tableNick == "array10")
    @AppStorage("auto_commit", store: sharedDefaults) private var autoCommit: Int = 0
    private let autoCommitOpts   = [0, 4, 5, 6, 7, 8, 9, 10]
    private let autoCommitLabels = ["無", "4碼", "5碼", "6碼", "7碼", "8碼", "9碼", "10碼"]

    // §5.2.2 — phonetic keyboard type (only shown when tableNick == "phonetic")
    @AppStorage("phonetic_keyboard_type", store: sharedDefaults) private var phoneticKeyboardType: String = "standard"
    private let phoneticOptions = ["standard", "et_41", "eten26", "eten26_symbol", "hsu", "hsu_symbol"]
    private let phoneticLabels  = ["標準", "倚天 41 鍵", "倚天 26 鍵 (英文)", "倚天 26 鍵 (符號)", "許氏 (英文)", "許氏 (符號)"]

    @State private var keyboardName: String = ""
    @State private var showRemoveAlert = false
    @State private var isRemoving = false
    @State private var showClearRelatedAlert = false
    @State private var showSharePicker = false
    @State private var isExporting = false
    @State private var shareURL: URL?
    @State private var showShareSheet = false
    @State private var displayName: String
    @State private var displayVersion: String = "—"
    @State private var displayEndkey: String = "-"
    @State private var editMetadataValue: String = ""
    @State private var editingMetadataField: MetadataField?
    @State private var metadataError: String?
    @State private var isSavingMetadata = false

    init(im: IMRow, onRefresh: (() -> Void)? = nil, onDeleted: (() -> Void)? = nil) {
        self.im = im
        self.onRefresh = onRefresh
        self.onDeleted = onDeleted
        _displayName = State(initialValue: im.label)
    }

    private var mappingVersion: String {
        let table = im.tableNick
        let server = DBServer.shared
        let version = server.getImConfig(table, "version")
        if !version.isEmpty { return version }

        let legacy = sharedUD?.string(forKey: table + "mapping_version") ?? ""
        if !legacy.isEmpty { return legacy }

        let source = server.getImConfig(table, "source")
        if !source.isEmpty { return source }

        let name = server.getImConfig(table, "name")
        return name.isEmpty ? "—" : name
    }

    @State private var totalRecord: String = "—"

    // Per-IM backup preference (dynamic key — can't use @AppStorage)
    private var backupOnDelete: Bool {
        get { UserDefaults.standard.object(forKey: "backup_on_delete_\(im.tableNick)") as? Bool ?? true }
        nonmutating set { UserDefaults.standard.set(newValue, forKey: "backup_on_delete_\(im.tableNick)") }
    }
    private var backupOnDeleteBinding: Binding<Bool> {
        Binding(get: { backupOnDelete }, set: { backupOnDelete = $0 })
    }

    var body: some View {
        List {
            Section(header: Text("輸入法資訊")) {
                if im.tableNick == "related" {
                    LabeledContent("名稱", value: displayName)
                } else {
                    Button {
                        beginMetadataEdit(.name)
                    } label: {
                        editableMetadataRow(label: "名稱", value: displayName)
                    }
                    .buttonStyle(.plain)

                    Button {
                        beginMetadataEdit(.version)
                    } label: {
                        editableMetadataRow(label: "版本", value: displayVersion)
                    }
                    .buttonStyle(.plain)

                    Button {
                        beginMetadataEdit(.endkey)
                    } label: {
                        editableMetadataRow(label: "結束鍵", value: displayEndkey)
                    }
                    .buttonStyle(.plain)
                }
                LabeledContent("筆數", value: totalRecord)
            }
            .setupMatchedSectionBlock()

            if im.tableNick != "related" {
                Section(header: Text("軟鍵盤配置")) {
                    NavigationLink(destination: KeyboardPickerView(im: im, onSave: onRefresh)) {
                        LabeledContent("鍵盤佈局", value: keyboardName.isEmpty ? "—" : keyboardName)
                    }
                }
                .setupMatchedSectionBlock()
            }

            // Phonetic keyboard type (§5.2.2 — moved here because the pref only
            // affects the phonetic IM).
            if im.tableNick == "phonetic" {
                Section(header: Text("注音鍵盤類型")) {
                    Picker("鍵盤類型", selection: $phoneticKeyboardType) {
                        ForEach(0..<phoneticOptions.count, id: \.self) { i in
                            Text(phoneticLabels[i]).tag(phoneticOptions[i])
                        }
                    }
                    .onChange(of: phoneticKeyboardType) { newType in
                        updatePhoneticKeyboard(type: newType)
                    }
                }
                .setupMatchedSectionBlock()
            }

            // Array10 phone-numpad auto-commit setting
            if im.tableNick == "array10" {
                Section(header: Text("電話鍵盤設定")) {
                    Picker(selection: $autoCommit) {
                        ForEach(0..<autoCommitOpts.count, id: \.self) { i in
                            Text(autoCommitLabels[i]).tag(autoCommitOpts[i])
                        }
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("自動上屏")
                            Text("輸入字根符合設定數則自動送出組字")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                }
                .setupMatchedSectionBlock()
            }

            // §13.3 — shown only for the user-built custom IM
            if im.tableNick == "custom" {
                Section(header: Text("字根對應設定")) {
                    Toggle(isOn: $acceptNumberIndex) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("數字字根對應")
                            Text("允許使用數字為輸入法字根")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                    Toggle(isOn: $acceptSymbolIndex) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("符號字根對應")
                            Text("允許使用符號為輸入法字根")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                }
                .setupMatchedSectionBlock()
            }

            if im.tableNick == "cj4" {
                Section(header: Text("倉頡鍵盤選項")) {
                    Toggle(isOn: $cj4SemicolonKey) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("顯示分號（；）鍵")
                            Text("在倉頡鍵盤加上分號（；）字根鍵，需自備含分號字根的倉頡碼表。")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                }
                .setupMatchedSectionBlock()
            }

            Section(header: Text(im.tableNick == "related" ? "關聯字庫" : "字根資料表")) {
                if im.tableNick == "related" {
                    NavigationLink(destination: RelatedListView(isEmbedded: true)) {
                        Label("瀏覽 / 編輯關聯字庫", systemImage: "text.bubble")
                    }
                } else {
                    NavigationLink(destination: RecordListView(tableName: im.tableNick,
                                                               imLabel: displayName)) {
                        Label("瀏覽 / 編輯資料表", systemImage: "tablecells")
                    }
                }
            }
            .setupMatchedSectionBlock()

            if im.tableNick == "related" {
                Section {
                    Button(role: .destructive) {
                        showClearRelatedAlert = true
                    } label: {
                        HStack {
                            Spacer()
                            Text("清除關聯字庫")
                            Spacer()
                        }
                    }
                }
                .setupMatchedSectionBlock()
            }

            if im.tableNick != "related" {
                Section(header: Text("選項")) {
                    Toggle("刪除時備份已學習記錄", isOn: backupOnDeleteBinding)
                }
                .setupMatchedSectionBlock()
            }

            if im.tableNick != "related" {
                // Full-width bordered destructive button at the bottom of the
                // detail screen (spec §5.2 — was a plain destructive list row).
                Section {
                    Button(role: .destructive) {
                        showRemoveAlert = true
                    } label: {
                        HStack {
                            Spacer()
                            if isRemoving {
                                ProgressView()
                            } else {
                                Text("移除輸入法")
                            }
                            Spacer()
                        }
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                    .tint(SettingsTheme.destructive)
                    .disabled(isRemoving)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }
                .setupMatchedSectionBlock()
            }
        }
        .setupMatchedGroupedSurface()
        .constrainedDetailLayout(displayName) {
            Button {
                showSharePicker = true
            } label: {
                Image(systemName: "square.and.arrow.up")
                    .font(.title2.weight(.semibold))
                    .frame(width: SettingsMetrics.detailToolbarButtonSize,
                           height: SettingsMetrics.detailToolbarButtonSize)
            }
            .disabled(isExporting)
        }
        .confirmationDialog("匯出格式", isPresented: $showSharePicker, titleVisibility: .visible) {
            if im.tableNick != "related" {
                Button(".lime（文字）") { exportAndShare(format: .txt) }
            }
            Button(".limedb（資料庫）") { exportAndShare(format: .db) }
            Button("取消", role: .cancel) {}
        }
        .sheet(isPresented: $showShareSheet, onDismiss: { shareURL = nil }) {
            if let url = shareURL {
                ShareSheet(activityItems: [url])
            }
        }
        .sheet(item: $editingMetadataField) { field in
            NavigationStack {
                Form {
                    Section(header: Text("輸入法資訊")) {
                        TextField(field.label, text: $editMetadataValue)
                    }
                    if let metadataError {
                        Section {
                            Text(metadataError)
                                .foregroundColor(SettingsTheme.destructive)
                        }
                    }
                }
                .navigationTitle(field.title)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") { editingMetadataField = nil }
                            .disabled(isSavingMetadata)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button {
                            saveMetadataField(field)
                        } label: {
                            if isSavingMetadata {
                                ProgressView()
                            } else {
                                Text("儲存")
                            }
                        }
                        .disabled(isSavingMetadata)
                    }
                }
            }
        }
        .overlay {
            if isExporting {
                ZStack {
                    SettingsTheme.overlayScrim.ignoresSafeArea()
                    ProgressView("匯出中…")
                        .padding(SettingsMetrics.modalPadding)
                        .background(RoundedRectangle(cornerRadius: SettingsMetrics.modalCornerRadius)
                            .fill(SettingsTheme.overlayCardBackground)
                            .shadow(radius: SettingsMetrics.modalShadowRadius))
                }
            }
        }
        .task {
            refreshMetadataFields()
            if im.tableNick == "related" {
                let n = await manageImController.countRelated()
                totalRecord = "\(n)"
            } else {
                async let keyboards = manageImController.loadKeyboards(forIM: im.tableNick)
                async let count = manageImController.countRecords(table: im.tableNick)
                let (kb, n) = await (keyboards, count)
                keyboardName = kb.keyboards.first(where: { $0.code == kb.selected })?.desc ?? kb.selected
                totalRecord = "\(n)"
            }
        }
        .alert("移除輸入法", isPresented: $showRemoveAlert) {
            Button("移除", role: .destructive) {
                performRemove()
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text(backupOnDelete
                ? "此操作將清除「\(displayName)」的所有對應資料。\n已學習記錄將先備份，可在重新匯入時還原。確定繼續？"
                : "此操作將清除「\(displayName)」的所有對應資料，無法還原。確定繼續？")
        }
        .alert("清除關聯字庫", isPresented: $showClearRelatedAlert) {
            Button("清除", role: .destructive) {
                Task {
                    _ = await manageRelatedController.clearRelated()
                    totalRecord = "0"
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("此操作將清除所有關聯字資料，無法還原。確定繼續？")
        }
    }

    // MARK: - Metadata helpers

    private func refreshMetadataFields() {
        let version = mappingVersion
        displayName = im.label
        displayVersion = version
        let endkey = DBServer.shared.getImConfig(im.tableNick, "limeendkey")
        displayEndkey = endkey.isEmpty ? "-" : endkey
        editMetadataValue = ""
    }

    @ViewBuilder
    private func editableMetadataRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .foregroundColor(.primary)
            Spacer()
            Text(value)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.trailing)
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundColor(.secondary)
        }
        .contentShape(Rectangle())
    }

    private func beginMetadataEdit(_ field: MetadataField) {
        metadataError = nil
        switch field {
        case .name:
            editMetadataValue = displayName
        case .version:
            editMetadataValue = displayVersion == "—" ? "" : displayVersion
        case .endkey:
            editMetadataValue = displayEndkey == "-" ? "" : displayEndkey
        }
        editingMetadataField = field
    }

    private func saveMetadataField(_ field: MetadataField) {
        metadataError = nil
        isSavingMetadata = true
        Task {
            let result = await manageImController.updateIMMetadataField(tableNick: im.tableNick,
                                                                        field: field.dbField,
                                                                        value: editMetadataValue)
            await MainActor.run {
                isSavingMetadata = false
                switch result {
                case .success:
                    let trimmedValue = editMetadataValue.trimmingCharacters(in: .whitespacesAndNewlines)
                    if field == .name {
                        displayName = trimmedValue
                    } else if field == .version {
                        displayVersion = trimmedValue.isEmpty ? "—" : trimmedValue
                    } else {
                        displayEndkey = trimmedValue.isEmpty ? "-" : trimmedValue
                    }
                    editingMetadataField = nil
                    onRefresh?()
                case .failure(let error):
                    metadataError = error.localizedDescription
                }
            }
        }
    }

    // MARK: - Phonetic helper

    /// Mirrors Android's LIMEPreference.onSharedPreferenceChanged — maps the
    /// picker value to a `keyboard.code` and writes `im.keyboard` for phonetic
    /// so the keyboard extension's `refreshPhoneticKeyboardPrefs()` picks up the
    /// new layout on next onStartInput.
    ///
    /// Picker value → keyboard.code routing (matches Android LIMEPreference.java:209-235):
    ///   "standard"       → "phonetic"
    ///   "et_41" / "eten" → "phoneticet41"
    ///   "eten26"         → "limenum" or "lime" (number_row_in_english toggle)
    ///   "eten26_symbol"  → "et26"
    ///   "hsu"            → "limenum" or "lime"
    ///   "hsu_symbol"     → "hsu"
    private func updatePhoneticKeyboard(type: String) {
        Task {
            await Task.detached(priority: .userInitiated) {
                let server = DBServer.shared
                let numberRow = sharedDefaults.bool(forKey: "number_row_in_english")
                let targetCode: String
                switch type {
                case "standard":           targetCode = "phonetic"
                case "et_41", "eten":      targetCode = "phoneticet41"
                case "eten26":             targetCode = numberRow ? "limenum" : "lime"
                case "eten26_symbol":      targetCode = "et26"
                case "hsu":                targetCode = numberRow ? "limenum" : "lime"
                case "hsu_symbol":         targetCode = "hsu"
                default:                   targetCode = type
                }
                guard let kbList = server.getKeyboardConfigList(),
                      let kb = kbList.first(where: { $0.code == targetCode }) else { return }
                server.setImConfigKeyboard("phonetic", kb)
            }.value
            // Refresh the 鍵盤佈局 label after the DB write so the detail page
            // reflects the new layout immediately.
            let result = await manageImController.loadKeyboards(forIM: im.tableNick)
            await MainActor.run {
                keyboardName = result.keyboards.first(where: { $0.code == result.selected })?.desc ?? result.selected
            }
        }
    }

    // MARK: - Remove

    /// Triggered from the "移除" alert action. We must dismiss the detail
    /// pane through the parent (`onDeleted`) BEFORE awaiting the DB clear,
    /// because on iPad the pane lives in the split-view detail column and
    /// cannot be popped via `dismiss()` from inside this view. The parent
    /// owns the navigation selection and clearing it reverts the detail
    /// column (or pops the stack on iPhone).
    private func performRemove() {
        let backup = backupOnDelete
        let tableNick = im.tableNick
        // Persist the user's backup choice so IMInstallView can show the
        // restore toggle even when the actual backup table ends up empty
        // (e.g. user never built learned records → all rows had score=0).
        if backup {
            UserDefaults.standard.set(true, forKey: "user_backed_up_\(tableNick)")
        } else {
            UserDefaults.standard.removeObject(forKey: "user_backed_up_\(tableNick)")
        }
        // Hand control back to the parent first — it will clear the
        // selection that drives the NavigationLink, dismissing this pane.
        onDeleted?()
        Task {
            _ = await manageImController.clearTable(tableNick: tableNick, backupLearning: backup)
            onRefresh?()
        }
    }

    // MARK: - Export helpers

    private enum ExportFormat { case db, txt }

    private func exportAndShare(format: ExportFormat) {
        isExporting = true
        Task {
            let url: URL?
            if im.tableNick == "related" {
                url = await setupController.exportRelatedAsLimedb()
            } else if format == .db {
                url = await setupController.exportIMAsLimedb(tableNick: im.tableNick)
            } else {
                url = await setupController.exportIMAsText(tableNick: im.tableNick)
            }
            isExporting = false
            if let url {
                shareURL = url
                showShareSheet = true
            }
        }
    }
}
