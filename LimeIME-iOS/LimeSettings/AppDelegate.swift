import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        applyUITestKeyboardPrefsIfNeeded()
        return true
    }

    /// Test-only hook: when launched by the screenshot UITest with theme / IM launch
    /// arguments, write them into the real shared app-group defaults. The UITest runner
    /// itself cannot join the app group, so the host app (which is a group member) is the
    /// only process that can reliably seed the keyboard extension's preferences.
    ///
    /// Activated via XCUIApplication.launchArguments:
    ///   "-LimeUITestKeyboardTheme", "<0-6>"
    ///   "-LimeUITestKeyboardList",  "<im nick, e.g. phonetic>"
    private func applyUITestKeyboardPrefsIfNeeded() {
        let args = UserDefaults.standard
        guard args.object(forKey: "LimeUITestKeyboardTheme") != nil
            || args.object(forKey: "LimeUITestKeyboardList") != nil,
              let shared = UserDefaults(suiteName: LIMEPreferenceManager.suiteName)
        else { return }

        if args.object(forKey: "LimeUITestKeyboardTheme") != nil {
            shared.set(args.integer(forKey: "LimeUITestKeyboardTheme"), forKey: "keyboard_theme")
        }
        if let imNick = args.string(forKey: "LimeUITestKeyboardList"), !imNick.isEmpty {
            shared.set(imNick, forKey: "keyboard_list")
        }
        // Standard phonetic layout and a clean keyboard_state so the keyboard restores
        // the requested IM rather than a stale index map.
        shared.set("standard", forKey: "phonetic_keyboard_type")
        shared.set("", forKey: "keyboard_state")
        shared.set(true, forKey: "enable_emoji")
        shared.set(5, forKey: "enable_emoji_position")
        shared.synchronize()
    }

    // MARK: - UIScene lifecycle

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }
}
