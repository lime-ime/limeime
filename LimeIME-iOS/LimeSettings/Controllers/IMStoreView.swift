import SwiftUI

// MARK: - Download state per variant

enum IMInstallState: Equatable {
    case notInstalled
    case downloading(progress: Double)   // 0.0 – 1.0
    case importing
    case installed
    case error(String)

    var isActive: Bool {
        if case .downloading = self { return true }
        if case .importing   = self { return true }
        return false
    }
}

// MARK: - Download manager (ObservableObject so views can subscribe)

@MainActor
final class IMDownloadManager: ObservableObject {

    @Published private(set) var states: [String: IMInstallState] = [:]

    // Persistent set of installed table names (read from lime.db on init)
    @Published private(set) var installedTables: Set<String> = []

    private var tasks: [String: URLSessionDownloadTask] = [:]
    private var progressObservers: [String: NSKeyValueObservation] = [:]
    private var restoreLearningFlags: [String: Bool] = [:]

    init() { refreshInstalledTables() }

    // MARK: - Public

    func state(for variant: IMVariant) -> IMInstallState {
        if let s = states[variant.id] { return s }
        return installedTables.contains(variant.tableName) ? .installed : .notInstalled
    }

    func install(_ variant: IMVariant, restoreLearning: Bool = false) {
        guard !state(for: variant).isActive else { return }
        restoreLearningFlags[variant.id] = restoreLearning
        states[variant.id] = .downloading(progress: 0)
        download(variant: variant)
    }

    func cancel(_ variant: IMVariant) {
        tasks[variant.id]?.cancel()
        tasks.removeValue(forKey: variant.id)
        restoreLearningFlags.removeValue(forKey: variant.id)
        states[variant.id] = .notInstalled
    }

    func refreshInstalledTables() {
        Task.detached(priority: .background) { [weak self] in
            let server = DBServer.shared
            // Use tableHasData() — all tables exist in bundled lime.db, only populated ones are installed
            let tables = IMCatalog.allVariants
                .map { $0.tableName }
                .filter { server.tableHasData($0) }
            let result = Set(tables)
            await MainActor.run { [weak self] in
                self?.installedTables = result
            }
        }
    }

    // MARK: - Private download pipeline

    private func download(variant: IMVariant) {
        let url = variant.downloadURL
        let variantID = variant.id

        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 300
        let session = URLSession(configuration: config)

        let task = session.downloadTask(with: url) { [weak self] tempURL, response, error in
            guard let self else { return }

            if let error {
                Task { await MainActor.run { self.states[variantID] = .error(error.localizedDescription) } }
                return
            }
            guard let tempURL else {
                Task { await MainActor.run { self.states[variantID] = .error("下載失敗") } }
                return
            }

            // Validate minimum file size (100 KB)
            let size = (try? tempURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            guard size > 100_000 else {
                Task { await MainActor.run { self.states[variantID] = .error("檔案過小，請稍後重試") } }
                return
            }

            Task { await MainActor.run {
                self.states[variantID] = .importing
                let restore = self.restoreLearningFlags[variant.id] ?? false
                self.importDownloaded(tempURL: tempURL, variant: variant, restoreLearning: restore)
            }}
        }

        // Observe download progress
        let obs = task.progress.observe(\.fractionCompleted, options: [.new]) { [weak self] progress, _ in
            guard let self else { return }
            Task { await MainActor.run {
                self.states[variantID] = .downloading(progress: progress.fractionCompleted)
            }}
        }
        progressObservers[variantID] = obs
        tasks[variantID] = task
        task.resume()
    }

    private func importDownloaded(tempURL: URL, variant: IMVariant, restoreLearning: Bool = false) {
        Task.detached(priority: .userInitiated) { [weak self] in
            guard let self else { return }
            let server = DBServer.shared

            do {
                try importDatabaseFile(server: server, url: tempURL, tableName: variant.tableName)

                if restoreLearning {
                    if let ss = server.makeSearchServer() {
                        let restored = ss.restoreUserRecords(variant.tableName)
                        if restored > 0 { ss.dropBackupTable(variant.tableName) }
                    }
                }

                // Register in im table so the keyboard can see it
                try server.registerIM(imName: variant.imName, tableName: variant.tableName,
                                      label: variant.label, keyboardId: variant.keyboardId)

                // Rebuild keyboard_state so the keyboard extension picks up the new IM
                LIMEPreferenceManager.shared.syncIMActivatedState(dbServer: server)

                await MainActor.run {
                    self.states[variant.id] = .installed
                    self.installedTables.insert(variant.tableName)
                    self.tasks.removeValue(forKey: variant.id)
                    self.progressObservers.removeValue(forKey: variant.id)
                    self.restoreLearningFlags.removeValue(forKey: variant.id)
                }
            } catch {
                await MainActor.run {
                    self.states[variant.id] = .error(error.localizedDescription)
                    self.restoreLearningFlags.removeValue(forKey: variant.id)
                }
            }
        }
    }
}

// MARK: - IM Store View

struct IMStoreView: View {
    @StateObject private var manager = IMDownloadManager()
    @State private var searchText = ""
    @State private var expandedFamilies: Set<String> = ["phonetic"]  // phonetic open by default

    var filteredFamilies: [IMFamily] {
        if searchText.isEmpty { return IMCatalog.families }
        let q = searchText.lowercased()
        return IMCatalog.families.compactMap { family in
            let variants = family.variants.filter {
                $0.name.localizedCaseInsensitiveContains(q) ||
                family.chineseName.localizedCaseInsensitiveContains(q) ||
                family.englishName.localizedCaseInsensitiveContains(q)
            }
            guard !variants.isEmpty else { return nil }
            return IMFamily(id: family.id, chineseName: family.chineseName,
                            englishName: family.englishName, description: family.description,
                            systemIcon: family.systemIcon, variants: variants)
        }
    }

    var body: some View {
        NavigationView {
            List {
                // Installed summary banner
                if !manager.installedTables.isEmpty {
                    installedBanner
                }

                ForEach(filteredFamilies) { family in
                    Section {
                        if expandedFamilies.contains(family.id) {
                            ForEach(family.variants) { variant in
                                VariantRow(variant: variant, manager: manager)
                            }
                        }
                    } header: {
                        FamilyHeader(family: family, isExpanded: expandedFamilies.contains(family.id)) {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                if expandedFamilies.contains(family.id) {
                                    expandedFamilies.remove(family.id)
                                } else {
                                    expandedFamilies.insert(family.id)
                                }
                            }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .searchable(text: $searchText, prompt: "搜尋輸入法")
            .navigationTitle("輸入法商店")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { manager.refreshInstalledTables() } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
            .onAppear { expandedFamilies = Set(IMCatalog.families.map { $0.id }) }
        }
    }

    private var installedBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark.seal.fill")
                .foregroundColor(.green)
                .font(.title2)
            VStack(alignment: .leading, spacing: 2) {
                Text("已安裝 \(manager.installedTables.count) 個輸入法")
                    .font(.subheadline).bold()
                Text("點選「安裝」可更新或更換字集")
                    .font(.caption).foregroundColor(.secondary)
            }
            Spacer()
        }
        .padding(.vertical, 6)
    }
}

// MARK: - Family section header

struct FamilyHeader: View {
    let family: IMFamily
    let isExpanded: Bool
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            HStack(spacing: 10) {
                Image(systemName: family.systemIcon)
                    .frame(width: 28, height: 28)
                    .background(Color.accentColor.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                    .foregroundColor(.accentColor)

                VStack(alignment: .leading, spacing: 1) {
                    Text(family.chineseName + "  " + family.englishName)
                        .font(.headline)
                        .foregroundColor(.primary)
                    Text(family.description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Single variant row

struct VariantRow: View {
    let variant: IMVariant
    @ObservedObject var manager: IMDownloadManager
    var installOverride: ((IMVariant) -> Void)? = nil

    var state: IMInstallState { manager.state(for: variant) }

    var body: some View {
        HStack(spacing: 12) {
            // Variant info
            VStack(alignment: .leading, spacing: 3) {
                Text(variant.name)
                    .font(.body)
                HStack(spacing: 6) {
                    Text("\(variant.recordCount.formatted()) 字")
                    Text("·")
                    Text(variant.sizeString)
                }
                .font(.caption)
                .foregroundColor(.secondary)
            }

            Spacer()

            // Action button
            InstallButton(state: state) {
                if state.isActive {
                    manager.cancel(variant)
                } else if let override = installOverride {
                    override(variant)
                } else {
                    manager.install(variant)
                }
            }
        }
        .padding(.vertical, 4)
        // Error banner
        .overlay(alignment: .bottom) {
            if case .error(let msg) = state {
                Text(msg)
                    .font(.caption2)
                    .foregroundColor(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .offset(y: 14)
            }
        }
    }
}

// MARK: - Install button with states

struct InstallButton: View {
    let state: IMInstallState
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            switch state {
            case .notInstalled:
                Text("安裝").bold().frame(width: 64)

            case .downloading(let p):
                VStack(spacing: 2) {
                    ProgressView(value: p)
                        .frame(width: 60)
                        .tint(.blue)
                    Text("取消").font(.caption2).foregroundColor(.secondary)
                }

            case .importing:
                HStack(spacing: 4) {
                    ProgressView().scaleEffect(0.7)
                    Text("匯入中")
                }
                .frame(width: 80)

            case .installed:
                Label("已安裝", systemImage: "checkmark")
                    .font(.footnote)
                    .foregroundColor(.green)
                    .frame(width: 80)

            case .error:
                Text("重試").bold().frame(width: 64).foregroundColor(.red)
            }
        }
        .buttonStyle(.bordered)
        .disabled(state == .importing)
        .animation(.default, value: stateTag)
    }

    // Used only for animation triggering
    private var stateTag: Int {
        switch state {
        case .notInstalled:   return 0
        case .downloading:    return 1
        case .importing:      return 2
        case .installed:      return 3
        case .error:          return 4
        }
    }
}
