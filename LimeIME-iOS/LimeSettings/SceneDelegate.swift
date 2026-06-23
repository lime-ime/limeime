import UIKit

extension Notification.Name {
    static let limeDeepLink = Notification.Name("net.toload.limeime.deepLink")
    static let limeExternalImport = Notification.Name("net.toload.limeime.externalImport")
}

/// Stores a deep-link URL received before LimeSettingsView has appeared (cold launch).
@MainActor var pendingLimeDeepLinkURL: URL?

/// Stores an external import file copied into app-local temporary storage until
/// the user explicitly chooses the destination IM in the import UI.
@MainActor var pendingLimeExternalImportURL: URL?

class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let w = UIWindow(windowScene: windowScene)
        w.rootViewController = MainViewController()
        w.makeKeyAndVisible()
        window = w

        // Handle URLs passed at launch (e.g. via Files app)
        if let ctx = connectionOptions.urlContexts.first {
            handleURL(ctx.url)
        }
    }

    func scene(_ scene: UIScene, openURLContexts urlContexts: Set<UIOpenURLContext>) {
        if let ctx = urlContexts.first {
            handleURL(ctx.url)
        }
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        // Keyboard extensions cannot open URLs; they write a destination to the shared
        // App Group UserDefaults instead. Consume it here on every foreground activation.
        let suite = "group.net.toload.limeime"
        guard let defaults = UserDefaults(suiteName: suite),
              let destination = defaults.string(forKey: "pending_navigation") else { return }
        defaults.removeObject(forKey: "pending_navigation")
        defaults.synchronize()
        if destination == "settings",
           let url = URL(string: "limeime://settings") {
            handleURL(url)
        }
    }

    // MARK: - Private

    private func handleURL(_ url: URL) {
        if url.scheme == "limeime" {
            // Store for cold-launch (LimeSettingsView not yet on screen).
            pendingLimeDeepLinkURL = url
            // Notify for warm-launch (view already visible).
            NotificationCenter.default.post(name: .limeDeepLink, object: url)
            return
        }

        let accessing = url.startAccessingSecurityScopedResource()
        Task { @MainActor in
            defer { if accessing { url.stopAccessingSecurityScopedResource() } }
            await IntentHandler.shared.handle(url: url, view: nil)
        }
    }
}
