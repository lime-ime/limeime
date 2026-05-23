// LimeSettingsView.swift
// LimeIME-iOS
//
// Root 5-tab TabView. Hosted via UIHostingController from MainViewController.
// Replaces the old 4-tab version per spec §2.

import SwiftUI

// MARK: - Shared UserDefaults for @AppStorage

let sharedDefaults = UserDefaults(suiteName: "group.net.toload.limeime")!

// MARK: - LimeSettingsView (5-tab root)

struct LimeSettingsView: View {

    @StateObject private var navManager: NavigationManager
    @StateObject private var progressManager: ProgressManager
    @StateObject private var setupController: SetupImController
    @StateObject private var manageImController: ManageImController
    @StateObject private var manageRelatedController: ManageRelatedController

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
        .environmentObject(navManager)
        .environmentObject(progressManager)
        .environmentObject(setupController)
        .environmentObject(manageImController)
        .environmentObject(manageRelatedController)
        .onAppear {
            Task {
                await setupController.seedRelatedIfNeeded()
                manageRelatedController.invalidate()
            }
            // Cold-launch deep link (URL arrived before view appeared).
            if let url = pendingLimeDeepLinkURL {
                pendingLimeDeepLinkURL = nil
                handleDeepLink(url)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .limeDeepLink)) { note in
            // Warm-launch deep link (app already running).
            if let url = note.object as? URL { handleDeepLink(url) }
        }
        .overlay {
            if progressManager.isVisible {
                ZStack {
                    Color.black.opacity(0.35).ignoresSafeArea()
                    VStack(spacing: 12) {
                        ProgressView()
                            .progressViewStyle(.circular)
                            .tint(.white)
                        if !progressManager.status.isEmpty {
                            Text(progressManager.status)
                                .font(.caption)
                                .foregroundColor(.white)
                        }
                    }
                    .padding(24)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
                }
            }
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
    private let titleSectionHeight: CGFloat = 60
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
        .frame(maxWidth: 560)
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
