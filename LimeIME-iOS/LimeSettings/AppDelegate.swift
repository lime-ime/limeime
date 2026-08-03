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

import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        Self.removeLegacyV1Artifacts(in: FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: LIMEPreferenceManager.suiteName))
        applyUITestKeyboardPrefsIfNeeded()
        return true
    }

    static func removeLegacyV1Artifacts(in baseURL: URL?) {
        guard let baseURL else { return }
        let fm = FileManager.default
        [
            baseURL.appendingPathComponent("tables", isDirectory: true),
            baseURL.appendingPathComponent("restore.limedb"),
            baseURL.appendingPathComponent("restore.meta.json"),
        ].forEach { try? fm.removeItem(at: $0) }
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        let task = application.beginBackgroundTask(withName: "publish-editor-changes")
        DispatchQueue.global(qos: .utility).async {
            defer { application.endBackgroundTask(task) }
            do {
                try DBServer.shared.publishPendingEditorChanges()
            } catch {
                NSLog("AppDelegate: pending editor publication failed: %@", error.localizedDescription)
            }
        }
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
            shared.set(imNick, forKey: "active_im")
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
