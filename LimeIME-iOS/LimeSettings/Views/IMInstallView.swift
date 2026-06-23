// IMInstallView.swift
// LimeIME-iOS
//
// IM install screen — local file import + cloud download.
// Spec §5.3.

import SwiftUI
import UniformTypeIdentifiers

// MARK: - IMInstallView

struct IMInstallView: View {

    let onRefresh: (() -> Void)?

    @EnvironmentObject private var setupController: SetupImController
    @EnvironmentObject private var manageRelatedController: ManageRelatedController

    @State private var showFilePicker = false
    @State private var pickerType: ImportType = .db
    @State private var pendingTableName: String = ""  // §13.3: fixed tableName for the pending import
    @State private var isImporting = false
    @State private var showsLocalImportOverlay = false
    @State private var statusMessage = ""

    // Cloud download state
    @StateObject private var downloadManager = IMDownloadManager()
    @State private var expandedFamilies: Set<String> = []
    @State private var searchText = ""
    @State private var relatedInstalled: Bool = false
    @State private var hasPendingExternalImport: Bool = false
    @State private var pendingExternalChoice: PendingExternalImportChoice?
    @State private var showExternalImportChoiceDialog = false

    enum ImportType { case db, txt, relatedDb }

    private struct PendingExternalImportChoice: Identifiable {
        let id = UUID()
        let tableName: String
        let requestedType: ImportType
    }

    init(onRefresh: (() -> Void)? = nil) {
        self.onRefresh = onRefresh
    }

    var filteredFamilies: [IMFamily] {
        if searchText.isEmpty { return IMCatalog.families }
        let q = searchText.lowercased()
        return IMCatalog.families.compactMap { family in
            let nameMatch = family.chineseName.localizedCaseInsensitiveContains(q) ||
                            family.englishName.localizedCaseInsensitiveContains(q)
            // Keep families with no variants if the family name matches (e.g. 自建)
            if family.variants.isEmpty {
                return nameMatch ? family : nil
            }
            let variants = family.variants.filter {
                $0.name.localizedCaseInsensitiveContains(q) || nameMatch
            }
            guard !variants.isEmpty else { return nil }
            return IMFamily(id: family.id, chineseName: family.chineseName,
                            englishName: family.englishName, description: family.description,
                            systemIcon: family.systemIcon, variants: variants)
        }
    }

    var body: some View {
        List {
            // MARK: Search — inline TextField row.
            // iOS 18's floating tab bar hoists any `.searchable(...)` field
            // inside a TabView+NavigationStack into the tab pill, where it
            // gets clipped on iPad 11". Rendering the field as a regular
            // List row keeps the search local to this view and avoids the
            // hoisting entirely.
            Section {
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(.secondary)
                    TextField("搜尋輸入法", text: $searchText)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    if !searchText.isEmpty {
                        Button {
                            searchText = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .setupMatchedSectionBlock()

            // MARK: Status
            if !statusMessage.isEmpty {
                Section(header: Text("狀態")) {
                    Text(statusMessage)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                    if hasPendingExternalImport {
                        Button("取消外部檔案") {
                            clearPendingExternalImport()
                        }
                    }
                }
                .setupMatchedSectionBlock()
            }

            // MARK: Per-IM DisclosureGroups (§5.3 / §13.3)
            // Each group shows cloud variant rows (built-in IMs only) + local import buttons.
            // The 自建 group shows only local import buttons (no cloud variants).
            Section(header: Text("下載 / 匯入輸入法")) {
                ForEach(filteredFamilies) { family in
                    FamilyInstallGroup(
                        family: family,
                        isInstalled: downloadManager.installedTables.contains(family.id),
                        isExpanded: Binding(
                            get: { !downloadManager.installedTables.contains(family.id) && expandedFamilies.contains(family.id) },
                            set: { expanded in
                                guard !downloadManager.installedTables.contains(family.id) else { return }
                                if expanded { expandedFamilies.insert(family.id) }
                                else { expandedFamilies.remove(family.id) }
                            }
                        ),
                        downloadManager: downloadManager,
                        onImportDB: {
                            beginImport(for: family.id, requestedType: .db)
                        },
                        onImportTxt: {
                            beginImport(for: family.id, requestedType: .txt)
                        }
                    )
                }

                DisclosureGroup(
                    isExpanded: Binding(
                        get: { !relatedInstalled && expandedFamilies.contains("related") },
                        set: { expanded in
                            guard !relatedInstalled else { return }
                            if expanded { expandedFamilies.insert("related") }
                            else { expandedFamilies.remove("related") }
                        }
                    )
                ) {
                    Button {
                        beginImport(for: "related", requestedType: .relatedDb)
                    } label: {
                        Label("匯入 .limedb", systemImage: "archivebox")
                            .foregroundColor(.accentColor)
                    }
                } label: {
                    HStack {
                        Label("關聯字庫", systemImage: "text.bubble")
                        if relatedInstalled {
                            Spacer()
                            Text("已安裝")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
            .setupMatchedSectionBlock()
        }
        .listStyle(.insetGrouped)
        .setupMatchedGroupedSurface()
        // No manual refresh action: installed state is re-queried automatically
        // on appear and after every download/import (see refreshInstallStates /
        // downloadManager.installedTables). LIME_SETTINGS.md §5.3.
        .constrainedDetailLayout("下載 / 匯入輸入法")
        .onAppear {
            refreshInstallStates()
            prepareExternalImportIfNeeded()
        }
        .onReceive(NotificationCenter.default.publisher(for: .limeExternalImport)) { note in
            if let url = note.object as? URL { prepareExternalImport(url) }
        }
        .onChange(of: downloadManager.installedTables) { newTables in
            // Expand groups for tables that just became uninstalled
            for family in IMCatalog.families where !newTables.contains(family.id) {
                expandedFamilies.insert(family.id)
            }
        }
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: allowedTypes(),
            allowsMultipleSelection: false
        ) { result in
            handleFileImport(result: result)
        }
        .confirmationDialog(
            "已收到外部檔案",
            isPresented: $showExternalImportChoiceDialog,
            titleVisibility: .visible,
            presenting: pendingExternalChoice
        ) { choice in
            Button("匯入外部檔案") {
                usePendingExternalImport(choice)
            }
            Button("選擇其他檔案") {
                clearPendingExternalImport(message: nil)
                startManualFilePicker(for: choice.tableName, requestedType: choice.requestedType)
            }
            Button("取消", role: .cancel) {}
        } message: { _ in
            Text("要使用剛收到的外部檔案，還是改選其他檔案？")
        }
        .overlay {
            if showsLocalImportOverlay {
                ZStack {
                    SettingsTheme.overlayScrim.ignoresSafeArea()
                    ProgressView("匯入中…")
                        .padding(SettingsMetrics.modalPadding)
                        .background(RoundedRectangle(cornerRadius: SettingsMetrics.modalCornerRadius)
                            .fill(SettingsTheme.overlayCardBackground)
                            .shadow(radius: SettingsMetrics.modalShadowRadius))
                }
            }
        }
        .onChange(of: downloadManager.installedTables) { _ in
            onRefresh?()
        }
    }

    // MARK: - Helpers

    private func allowedTypes() -> [UTType] {
        switch pickerType {
        case .db, .relatedDb: return [UTType.item]
        case .txt:            return [UTType.plainText, .item]
        }
    }

    private func refreshInstallStates() {
        downloadManager.refreshInstalledTables()
        // All families start expanded; isExpanded binding getter collapses installed ones.
        expandedFamilies = Set(IMCatalog.families.map { $0.id }).union(["related"])
        Task.detached(priority: .background) {
            let hasData = DBServer.shared.tableHasData("related")
            await MainActor.run {
                relatedInstalled = hasData
                if hasData { expandedFamilies.remove("related") }
            }
        }
    }

    private func beginImport(for tableName: String, requestedType: ImportType) {
        if pendingLimeExternalImportURL != nil {
            pendingExternalChoice = PendingExternalImportChoice(tableName: tableName, requestedType: requestedType)
            showExternalImportChoiceDialog = true
            return
        }
        startManualFilePicker(for: tableName, requestedType: requestedType)
    }

    private func startManualFilePicker(for tableName: String, requestedType: ImportType) {
        pickerType = requestedType
        pendingTableName = tableName
        showFilePicker = true
    }

    private func usePendingExternalImport(_ choice: PendingExternalImportChoice) {
        pendingExternalChoice = nil
        showExternalImportChoiceDialog = false
        guard let externalURL = pendingLimeExternalImportURL else {
            startManualFilePicker(for: choice.tableName, requestedType: choice.requestedType)
            return
        }
        let ext = externalURL.pathExtension.lowercased()
        if choice.tableName == "related" && ext != "limedb" && ext != "zip" {
            statusMessage = "關聯字庫只支援匯入 .limedb 檔案。"
            hasPendingExternalImport = true
            return
        }
        pickerType = importType(for: externalURL, fallback: choice.requestedType)
        pendingTableName = choice.tableName
        pendingLimeExternalImportURL = nil
        hasPendingExternalImport = false
        handleSelectedImportURL(externalURL)
    }

    private func prepareExternalImportIfNeeded() {
        if let url = pendingLimeExternalImportURL { prepareExternalImport(url) }
    }

    private func prepareExternalImport(_ url: URL) {
        let ext = url.pathExtension.lowercased()
        guard ["limedb", "zip", "lime", "cin"].contains(ext) else { return }
        hasPendingExternalImport = true
        statusMessage = "已收到外部檔案，請選擇要匯入的輸入法。"
        showsLocalImportOverlay = false
    }

    private func clearPendingExternalImport(message: String? = "已取消外部檔案。") {
        if let url = pendingLimeExternalImportURL {
            try? FileManager.default.removeItem(at: url)
        }
        pendingLimeExternalImportURL = nil
        pendingExternalChoice = nil
        showExternalImportChoiceDialog = false
        hasPendingExternalImport = false
        if let message { statusMessage = message }
    }

    private func importType(for url: URL, fallback: ImportType) -> ImportType {
        let ext = url.pathExtension.lowercased()
        if ext == "limedb" || ext == "zip" {
            return fallback == .relatedDb ? .relatedDb : .db
        }
        return .txt
    }

    private func handleFileImport(result: Result<[URL], Error>) {
        guard case .success(let urls) = result, let url = urls.first else { return }
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }

        let importURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("\(UUID().uuidString)_\(url.lastPathComponent)")
        do {
            try? FileManager.default.removeItem(at: importURL)
            try FileManager.default.copyItem(at: url, to: importURL)
            handleSelectedImportURL(importURL)
        } catch {
            statusMessage = "匯入失敗：\(error.localizedDescription)"
            isImporting = false
            showsLocalImportOverlay = false
            pendingTableName = ""
        }
    }

    private func handleSelectedImportURL(_ importURL: URL) {
        isImporting = true
        showsLocalImportOverlay = pickerType == .relatedDb
        statusMessage = ""

        let ext = importURL.pathExtension.lowercased()
        // Use only the IM selected by the import button that launched the picker.
        // Source filenames are metadata only and must not decide the import target.
        guard !pendingTableName.isEmpty else {
            statusMessage = "請先選擇要匯入的輸入法，再選擇檔案。"
            isImporting = false
            showsLocalImportOverlay = false
            try? FileManager.default.removeItem(at: importURL)
            return
        }
        let tableName = pendingTableName
        let seedCustomAfter = (tableName == "custom")

        Task { @MainActor in
            defer {
                try? FileManager.default.removeItem(at: importURL)
                isImporting = false
                showsLocalImportOverlay = false
                pendingTableName = ""
            }
            if pickerType == .relatedDb {
                let server = DBServer.shared
                await Task.detached(priority: .userInitiated) {
                    server.importDbRelated(sourcedb: importURL)
                }.value
                statusMessage = "關聯字庫匯入完成"
                manageRelatedController.invalidate()
            } else if ext == "limedb" || ext == "zip" {
                let restoreLearning = UserDefaults.standard.object(
                    forKey: "restore_on_import_\(tableName)") as? Bool ?? true
                let r = await setupController.importDBFile(url: importURL, tableName: tableName,
                                                           restoreLearning: restoreLearning)
                switch r {
                case .success(let table):
                    if seedCustomAfter { try? DBServer.shared.seedCustomIM() }
                    statusMessage = "已成功匯入 \(table)"
                    downloadManager.refreshInstalledTables()
                    onRefresh?()
                case .failure(let error):
                    statusMessage = "匯入失敗：\(error.localizedDescription)"
                }
            } else {
                let restoreLearning = UserDefaults.standard.object(
                    forKey: "restore_on_import_\(tableName)") as? Bool ?? true
                let r = await setupController.importTxtFile(url: importURL, tableName: tableName,
                                                            restoreLearning: restoreLearning)
                switch r {
                case .success(let count):
                    if seedCustomAfter { try? DBServer.shared.seedCustomIM() }
                    statusMessage = "文字檔匯入完成，共 \(count) 筆"
                    downloadManager.refreshInstalledTables()
                    onRefresh?()
                case .failure(let error):
                    statusMessage = "匯入失敗：\(error.localizedDescription)"
                }
            }
        }
    }
}

// MARK: - FamilyInstallGroup

private struct FamilyInstallGroup: View {
    let family: IMFamily
    let isInstalled: Bool
    @Binding var isExpanded: Bool
    let downloadManager: IMDownloadManager
    let onImportDB: () -> Void
    let onImportTxt: () -> Void

    @State private var hasBackup: Bool = false

    private var restoreOnImport: Bool {
        get { UserDefaults.standard.object(forKey: "restore_on_import_\(family.id)") as? Bool ?? true }
        nonmutating set { UserDefaults.standard.set(newValue, forKey: "restore_on_import_\(family.id)") }
    }
    private var restoreOnImportBinding: Binding<Bool> {
        Binding(get: { restoreOnImport }, set: { restoreOnImport = $0 })
    }

    var body: some View {
        DisclosureGroup(isExpanded: $isExpanded) {
            if hasBackup {
                Toggle("還原已學習記錄", isOn: restoreOnImportBinding)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            ForEach(family.variants) { variant in
                VariantRow(variant: variant, manager: downloadManager, installOverride: { v in
                    downloadManager.install(v, restoreLearning: restoreOnImport)
                })
            }
            Button(action: onImportDB) {
                Label("匯入 .limedb", systemImage: "archivebox")
                    .foregroundColor(.accentColor)
            }
            Button(action: onImportTxt) {
                Label("匯入 .cin / .lime", systemImage: "doc.text")
                    .foregroundColor(.accentColor)
            }
        } label: {
            HStack(spacing: SettingsMetrics.imRowSpacing) {
                // Same grey rounded-square representative-character badge as the
                // IM-list page (ㄅ / 倉 / 速 / 易 / 行 / 拼 …).
                InstallBadge(character: installBadgeCharacter(for: family))
                Text(family.chineseName)
                if isInstalled {
                    Spacer()
                    Text("已安裝")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
        .task(id: isInstalled) {
            // Backup tables are keyed by the variant's `tableName` (set when the IM was
            // imported), which is NOT always equal to `family.id` — e.g. cangjie family
            // has variants with tableNames "cj", "cj5", "scj", "ecj". Check every
            // distinct tableName the family covers, plus family.id as fallback.
            //
            // Also honour the `user_backed_up_<tableNick>` flag set by IMDetailView
            // when the user removed the IM with backup enabled. checkBackupTable
            // returns false when the backup table has 0 rows (no learned records
            // existed at delete time, since backup only includes score>0). The
            // toggle should still appear so the user's intent is preserved.
            let candidates: Set<String> = Set(family.variants.map { $0.tableName }).union([family.id])
            let ud = UserDefaults.standard
            let userOpted = candidates.contains { ud.bool(forKey: "user_backed_up_\($0)") }
            let backup = await Task.detached(priority: .background) {
                let ss = DBServer.shared.makeSearchServer()
                return candidates.contains { ss?.checkBackupTable($0) ?? false }
            }.value
            hasBackup = backup || userOpted
            if hasBackup && !isInstalled {
                isExpanded = true
            }
        }
    }

    /// Representative character for the family badge — same rule as the IM-list
    /// page: first char of the name, except 注音→ㄅ, 大易→易,
    /// 倉頡-family (cj/cj4/cj5/scj)→倉, 行列10→10.
    private func installBadgeCharacter(for family: IMFamily) -> String {
        switch family.id {
        case "phonetic":              return "ㄅ"
        case "cj", "cj4", "cj5", "scj": return "倉"
        case "dayi":                  return "易"
        case "array10":               return "10"
        default:
            return family.chineseName.isEmpty ? "?" : String(family.chineseName.prefix(1))
        }
    }
}

// MARK: - InstallBadge

/// Grey rounded-square tile carrying a family's representative character on the
/// install page — identical styling to the IM-list `IMBadge`. Spec §5.1 / §5.3.
private struct InstallBadge: View {
    let character: String

    var body: some View {
        Text(character)
            .font(.system(size: SettingsMetrics.imBadgeFontSize, weight: .medium))
            .foregroundColor(SettingsTheme.imBadgeForeground)
            .frame(width: SettingsMetrics.imBadgeSize,
                   height: SettingsMetrics.imBadgeSize)
            .background(SettingsTheme.imBadgeBackground,
                        in: RoundedRectangle(cornerRadius: SettingsMetrics.imBadgeCornerRadius))
    }
}
